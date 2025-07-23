package team.kitemc.verifymc;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.apache.commons.io.FileUtils;

public class ResourceManager {
    private final JavaPlugin plugin;
    private final boolean debug;

    public ResourceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] ResourceManager: " + msg);
    }

    private String getMessage(String key, String lang) {
        ResourceBundle messages = ResourceBundle.getBundle("i18n.messages", new Locale(lang));
        return messages.containsKey(key) ? messages.getString(key) : key;
    }

    private String getConfigLanguage() {
        return plugin.getConfig().getString("language", "zh");
    }

    private void backupPluginDataFolder() {
        boolean backupOnUpdate = plugin.getConfig().getBoolean("backup_on_update", true);
        if (!backupOnUpdate) {
            debugLog("Backup on update is disabled.");
            return;
        }

        File sourceDir = plugin.getDataFolder();
        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupDir = new File(sourceDir, "backup/" + timestamp);

        if (!backupDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create backup directory: " + backupDir.getAbsolutePath());
            return;
        }

        // Exclude the 'backup' directory itself to prevent recursion.
        FileFilter filter = file -> !file.getAbsolutePath().startsWith(new File(sourceDir, "backup").getAbsolutePath());

        try {
            FileUtils.copyDirectory(sourceDir, backupDir, filter);
            plugin.getLogger().info(getMessage("update.backup", getConfigLanguage()).replace("{path}", backupDir.getAbsolutePath()));
        } catch (IOException e) {
            plugin.getLogger().warning("Full backup failed: " + e.getMessage());
            debugLog("Full backup failed: " + e.toString());
        }
    }

    /**
     * 初始化所有资源目录和文件
     */
    public void initializeResources() {
        debugLog("Initializing resources...");
        
        // Backup first
        backupPluginDataFolder();
        
        try {
            // 创建基础目录
            createDirectories();

            // Save help files
            plugin.saveResource("config_help_en.yml", false);
            plugin.saveResource("config_help_zh.yml", false);
            
            // 更新配置文件
            upgradeConfigFile();
            
            // 更新i18n文件
            updateI18nFiles();
            
            // 更新email模板
            patchEmailTemplates();
            
            // 更新static文件
            updateStaticThemes();
            
            debugLog("Resource initialization completed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize resources: " + e.getMessage());
            debugLog("Resource initialization failed: " + e.getMessage());
        }
    }

    /**
     * 创建必要的目录结构
     */
    private void createDirectories() {
        debugLog("Creating directories...");
        
        String[] dirs = {
            "i18n",
            "email", 
            "static/default",
            "static/glassx",
            "data",
            "backup" // Added backup directory
        };
        
        for (String dir : dirs) {
            File directory = new File(plugin.getDataFolder(), dir);
            if (!directory.exists()) {
                directory.mkdirs();
                debugLog("Created directory: " + directory.getAbsolutePath());
            } else {
                debugLog("Directory already exists: " + directory.getAbsolutePath());
            }
        }
    }

    /**
     * 更新配置文件
     */
    private void upgradeConfigFile() {
        boolean autoUpdateConfig = plugin.getConfig().getBoolean("auto_update_config", false);
        if (!autoUpdateConfig) return;
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            boolean changed = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }
            if (changed) {
                currentConfig.save(configFile);
                plugin.getLogger().info(getMessage("update.config", getConfigLanguage()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Config upgrade failed: " + e.getMessage());
        }
    }

    /**
     * 更新i18n文件
     */
    private void updateI18nFiles() {
        boolean autoUpdateI18n = plugin.getConfig().getBoolean("auto_update_i18n", true);
        if (!autoUpdateI18n) return;
        debugLog("Updating i18n files...");
        File i18nDir = new File(plugin.getDataFolder(), "i18n");
        // 扫描所有messages_*.properties文件
        File[] existingFiles = i18nDir.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
        if (existingFiles != null) {
            for (File propFile : existingFiles) {
                String fileName = propFile.getName();
                String lang = fileName.substring("messages_".length(), fileName.length() - ".properties".length());
                debugLog("Found existing i18n file: " + fileName + " (language: " + lang + ")");
                updateI18nFileIfNeeded(propFile, lang);
            }
        }
        // 确保内置的zh和en文件存在
        String[] builtinLanguages = {"zh", "en"};
        for (String lang : builtinLanguages) {
            File propFile = new File(i18nDir, "messages_" + lang + ".properties");
            if (!propFile.exists()) {
                try (InputStream in = plugin.getResource("i18n/messages_" + lang + ".properties");
                     OutputStream out = new FileOutputStream(propFile)) {
                    if (in != null) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        debugLog("Created builtin i18n file: " + propFile.getName());
                    } else {
                        debugLog("Default i18n resource not found in JAR: i18n/messages_" + lang + ".properties");
                    }
                } catch (Exception e) {
                    debugLog("Failed to create builtin i18n file " + lang + ": " + e.getMessage());
                }
            }
        }
        // 备份并更新内置文件
        plugin.saveResource("i18n/messages_zh.properties", true);
        plugin.saveResource("i18n/messages_en.properties", true);
        plugin.getLogger().info(getMessage("update.i18n", getConfigLanguage()));
    }

    /**
     * 检查并更新i18n文件（如果需要）
     */
    private void updateI18nFileIfNeeded(File propFile, String lang) {
        try {
            debugLog("Checking i18n file: " + propFile.getName() + " (language: " + lang + ")");
            String currentContent = new String(Files.readAllBytes(propFile.toPath()), StandardCharsets.UTF_8);
            
            // 检查是否包含新版本的关键消息
            String[] requiredKeys = {
                "command.restart_starting",
                "command.restart_success", 
                "command.restart_failed",
                "admin.unban",
                "admin.unbanSuccess",
                "admin.unbanFailed",
                "admin.confirmUnban"
            };
            
            boolean needsUpdate = false;
            debugLog("Checking for missing i18n keys...");
            for (String key : requiredKeys) {
                if (!currentContent.contains(key + "=")) {
                    needsUpdate = true;
                    debugLog("i18n file missing key: " + key);
                } else {
                    debugLog("i18n file has key: " + key);
                }
            }
            
            if (needsUpdate) {
                debugLog("i18n file needs update, starting update process...");
                // 单独备份当前文件的逻辑已被移除，由启动时的全局备份替代。
                
                // 重新保存默认文件
                debugLog("Updating i18n file with new keys...");
                plugin.saveResource("i18n/messages_" + lang + ".properties", false);
                File tempFile = new File(plugin.getDataFolder(), "i18n/messages_" + lang + ".properties");
                if (tempFile.exists()) {
                    tempFile.renameTo(propFile);
                    debugLog("Updated i18n file: " + propFile.getName());
                }
            } else {
                debugLog("i18n file is up to date, no update needed");
            }
        } catch (Exception e) {
            debugLog("Error checking i18n update: " + e.getMessage());
        }
    }

    /**
     * 更新email模板
     */
    private void patchEmailTemplates() {
        boolean autoUpdateEmail = plugin.getConfig().getBoolean("auto_update_email", true);
        if (!autoUpdateEmail) return;
        debugLog("Updating email templates...");
        File emailDir = new File(plugin.getDataFolder(), "email");
        File[] htmlFiles = emailDir.listFiles((dir, name) -> name.endsWith(".html"));
        if (htmlFiles == null || htmlFiles.length == 0) {
            debugLog("No email templates found, auto-completing default templates...");
            String[] languages = {"zh", "en"};
            for (String lang : languages) {
                File templateFile = new File(emailDir, "verify_code_" + lang + ".html");
                if (!templateFile.exists()) {
                    try (InputStream in = plugin.getResource("email/verify_code_" + lang + ".html");
                         OutputStream out = new FileOutputStream(templateFile)) {
                        if (in != null) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                            debugLog("Created default email template: " + templateFile.getName());
                        } else {
                            debugLog("Default template not found in JAR: email/verify_code_" + lang + ".html");
                        }
                    } catch (Exception e) {
                        debugLog("Failed to create email template " + lang + ": " + e.getMessage());
                    }
                }
            }
        } else {
            debugLog("Email templates already exist, skipping auto-completion.");
        }
        // 备份并更新内置文件
        File zh = new File(emailDir, "verify_code_zh.html");
        File en = new File(emailDir, "verify_code_en.html");
        if (!zh.exists()) {
            plugin.saveResource("email/verify_code_zh.html", false);
            plugin.getLogger().info(getMessage("update.email", getConfigLanguage()));
        }
        if (!en.exists()) {
            plugin.saveResource("email/verify_code_en.html", false);
            plugin.getLogger().info(getMessage("update.email", getConfigLanguage()));
        }
    }

    /**
     * 更新static文件
     */
    private void updateStaticThemes() {
        boolean autoUpdateStatic = plugin.getConfig().getBoolean("auto_update_static", true);
        if (!autoUpdateStatic) return;
        debugLog("Updating static files...");
        
        File staticDir = new File(plugin.getDataFolder(), "static");
        String[] themes = {"default", "glassx"};
        
        for (String theme : themes) {
            File themeDir = new File(staticDir, theme);
            if (!themeDir.exists()) {
                themeDir.mkdirs();
                debugLog("Created theme directory: " + theme);
            } else {
                debugLog("Theme directory exists: " + theme);
            }
            
            // 检查主题目录是否为空
            File[] files = themeDir.listFiles();
            if (files == null || files.length == 0) {
                debugLog("Theme directory is empty, extracting files: " + theme);
                extractStaticFiles(themeDir, theme, true); // true=覆盖
            } else {
                debugLog("Theme directory has " + files.length + " files: " + theme);
            }
        }
        // 备份并更新内置文件
        File defaultTheme = new File(staticDir, "default");
        File glassxTheme = new File(staticDir, "glassx");
        extractStaticFiles(defaultTheme, "default", true); // true=覆盖
        extractStaticFiles(glassxTheme, "glassx", true);
        plugin.getLogger().info(getMessage("update.static", getConfigLanguage()));
    }

    /**
     * 从jar包中提取static文件
     */
    private void extractStaticFiles(File themeDir, String theme, boolean overwrite) {
        debugLog("Extracting static files for theme: " + theme);
        try {
            File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            debugLog("Opened jar file: " + jarFile.getAbsolutePath());
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                int extractedCount = 0;
                int totalEntries = 0;
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    totalEntries++;
                    if (entryName.startsWith("static/" + theme + "/") && !entry.isDirectory()) {
                        String relativePath = entryName.substring(("static/" + theme + "/").length());
                        File outFile = new File(themeDir, relativePath);
                        debugLog("Extracting: " + entryName + " -> " + outFile.getAbsolutePath());
                        outFile.getParentFile().mkdirs();
                        if (overwrite || !outFile.exists()) {
                            try (InputStream in = jar.getInputStream(entry);
                                 OutputStream out = new FileOutputStream(outFile)) {
                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, len);
                                }
                                extractedCount++;
                            }
                        } else {
                            debugLog("Skipping extraction for " + outFile.getAbsolutePath() + " (already exists)");
                        }
                    }
                }
                debugLog("Scanned " + totalEntries + " jar entries, extracted " + extractedCount + " files for theme: " + theme);
            }
        } catch (Exception e) {
            debugLog("Failed to extract static files for theme " + theme + ": " + e.getMessage());
        }
    }

    /**
     * 加载i18n资源包
     */
    public ResourceBundle[] loadI18nBundles() {
        debugLog("Loading i18n bundles...");
        
        ResourceBundle messagesZh = null;
        ResourceBundle messagesEn = null;
        
        try {
            File i18nDir = new File(plugin.getDataFolder(), "i18n");
            debugLog("i18n directory: " + i18nDir.getAbsolutePath());
            
            File zhProp = new File(i18nDir, "messages_zh.properties");
            File enProp = new File(i18nDir, "messages_en.properties");
            
            // 加载中文资源包
            if (zhProp.exists()) {
                debugLog("Loading external Chinese i18n bundle: " + zhProp.getAbsolutePath());
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(zhProp), StandardCharsets.UTF_8)) {
                    messagesZh = new PropertyResourceBundle(reader);
                    debugLog("Successfully loaded external Chinese i18n bundle");
                }
            } else {
                debugLog("External Chinese i18n bundle not found, loading internal bundle");
                messagesZh = ResourceBundle.getBundle("i18n.messages", Locale.CHINESE);
                debugLog("Loaded internal Chinese i18n bundle");
            }
            
            // 加载英文资源包
            if (enProp.exists()) {
                debugLog("Loading external English i18n bundle: " + enProp.getAbsolutePath());
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(enProp), StandardCharsets.UTF_8)) {
                    messagesEn = new PropertyResourceBundle(reader);
                    debugLog("Successfully loaded external English i18n bundle");
                }
            } else {
                debugLog("External English i18n bundle not found, loading internal bundle");
                messagesEn = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
                debugLog("Loaded internal English i18n bundle");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load i18n bundles: " + e.getMessage());
            debugLog("Failed to load i18n bundles: " + e.getMessage());
            
            // 使用内部资源包作为后备
            debugLog("Falling back to internal i18n bundles");
            messagesZh = ResourceBundle.getBundle("i18n.messages", Locale.CHINESE);
            messagesEn = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        }
        
        debugLog("i18n bundles loading completed");
        return new ResourceBundle[]{messagesZh, messagesEn};
    }

    /**
     * 获取主题的静态文件目录
     */
    public String getThemeStaticDir(String theme) {
        File themeDir = new File(plugin.getDataFolder(), "static/" + theme);
        return themeDir.getAbsolutePath();
    }

    /**
     * 检查主题是否存在
     */
    public boolean themeExists(String theme) {
        File themeDir = new File(plugin.getDataFolder(), "static/" + theme);
        return themeDir.exists() && themeDir.isDirectory();
    }
} 