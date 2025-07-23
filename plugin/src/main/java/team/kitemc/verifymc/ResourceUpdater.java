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

public class ResourceUpdater {
    private final JavaPlugin plugin;
    private final boolean debug;

    public ResourceUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] ResourceUpdater: " + msg);
    }

    /**
     * 检查并更新所有资源文件
     */
    public void checkAndUpdateResources() {
        debugLog("Checking for resource updates...");
        
        try {
            // 检查配置文件更新
            checkConfigUpdate();
            
            // 检查i18n文件更新
            checkI18nUpdate();
            
            // 检查email模板更新
            checkEmailUpdate();
            
            // 检查static文件更新
            checkStaticUpdate();
            
            debugLog("Resource update check completed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check resource updates: " + e.getMessage());
            debugLog("Resource update check failed: " + e.getMessage());
        }
    }

    /**
     * 检查配置文件更新
     */
    private void checkConfigUpdate() {
        debugLog("Checking config.yml for updates...");
        
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            debugLog("Created new config.yml");
            return;
        }

        try {
            String currentConfig = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            
            // 检查是否包含新版本的关键配置项
            String[] requiredKeys = {
                "frontend.theme",
                "frontend.announcement",
                "frontend.logo_url"
            };
            
            boolean needsUpdate = false;
            for (String key : requiredKeys) {
                if (!currentConfig.contains(key)) {
                    needsUpdate = true;
                    debugLog("Config missing key: " + key);
                    break;
                }
            }
            
            if (needsUpdate) {
                // 备份当前配置
                File backupFile = new File(plugin.getDataFolder(), "config.yml.backup");
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debugLog("Backed up config to: " + backupFile.getAbsolutePath());
                
                // 重新保存默认配置
                plugin.saveDefaultConfig();
                debugLog("Updated config.yml with new defaults");
            }
        } catch (Exception e) {
            debugLog("Error checking config update: " + e.getMessage());
        }
    }

    /**
     * 检查i18n文件更新
     */
    private void checkI18nUpdate() {
        debugLog("Checking i18n files for updates...");
        File i18nDir = new File(plugin.getDataFolder(), "i18n");
        // 扫描所有messages_*.properties文件
        File[] existingFiles = i18nDir.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
        if (existingFiles != null) {
            for (File propFile : existingFiles) {
                String fileName = propFile.getName();
                String lang = fileName.substring("messages_".length(), fileName.length() - ".properties".length());
                debugLog("Found existing i18n file: " + fileName + " (language: " + lang + ")");
                checkI18nFileUpdate(propFile, lang);
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
    }

    /**
     * 创建i18n文件
     */
    private void createI18nFile(String lang) {
        try {
            plugin.saveResource("i18n/messages_" + lang + ".properties", false);
            File tempFile = new File(plugin.getDataFolder(), "i18n/messages_" + lang + ".properties");
            if (tempFile.exists()) {
                File targetFile = new File(plugin.getDataFolder(), "i18n/messages_" + lang + ".properties");
                tempFile.renameTo(targetFile);
                debugLog("Created i18n file: " + targetFile.getName());
            }
        } catch (Exception e) {
            debugLog("Failed to create i18n file " + lang + ": " + e.getMessage());
        }
    }

    /**
     * 检查i18n文件更新
     */
    private void checkI18nFileUpdate(File propFile, String lang) {
        try {
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
            for (String key : requiredKeys) {
                if (!currentContent.contains(key + "=")) {
                    needsUpdate = true;
                    debugLog("i18n file missing key: " + key);
                    break;
                }
            }
            
            if (needsUpdate) {
                // 备份当前文件
                File backupFile = new File(propFile.getParentFile(), propFile.getName() + ".backup");
                Files.copy(propFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debugLog("Backed up i18n file: " + backupFile.getName());
                
                // 重新保存默认文件
                createI18nFile(lang);
                debugLog("Updated i18n file: " + propFile.getName());
            }
        } catch (Exception e) {
            debugLog("Error checking i18n update: " + e.getMessage());
        }
    }

    /**
     * 检查email模板更新
     */
    private void checkEmailUpdate() {
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
    }

    /**
     * 创建email模板
     */
    private void createEmailTemplate(String lang) {
        try {
            plugin.saveResource("email/verify_code_" + lang + ".html", false);
            File tempFile = new File(plugin.getDataFolder(), "email/verify_code_" + lang + ".html");
            if (tempFile.exists()) {
                File targetFile = new File(plugin.getDataFolder(), "email/verify_code_" + lang + ".html");
                tempFile.renameTo(targetFile);
                debugLog("Created email template: " + targetFile.getName());
            }
        } catch (Exception e) {
            debugLog("Failed to create email template " + lang + ": " + e.getMessage());
        }
    }

    /**
     * 检查static文件更新
     */
    private void checkStaticUpdate() {
        debugLog("Checking static files for updates...");
        
        File staticDir = new File(plugin.getDataFolder(), "static");
        String[] themes = {"default", "glassx"};
        
        for (String theme : themes) {
            File themeDir = new File(staticDir, theme);
            if (!themeDir.exists()) {
                themeDir.mkdirs();
                debugLog("Created theme directory: " + theme);
            }
            
            // 检查主题目录是否为空或需要更新
            if (shouldUpdateStaticFiles(themeDir, theme)) {
                extractStaticFiles(themeDir, theme);
            }
        }
    }

    /**
     * 检查是否需要更新static文件
     */
    private boolean shouldUpdateStaticFiles(File themeDir, String theme) {
        File[] files = themeDir.listFiles();
        if (files == null || files.length == 0) {
            debugLog("Theme directory is empty: " + theme);
            return true;
        }
        
        // 检查关键文件是否存在
        String[] keyFiles = {"index.html", "assets/", "css/", "js/"};
        for (String keyFile : keyFiles) {
            File checkFile = new File(themeDir, keyFile);
            if (!checkFile.exists()) {
                debugLog("Missing key file in theme " + theme + ": " + keyFile);
                return true;
            }
        }
        
        return false;
    }

    /**
     * 从jar包中提取static文件
     */
    private void extractStaticFiles(File themeDir, String theme) {
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
                        try (InputStream in = jar.getInputStream(entry);
                             OutputStream out = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                            extractedCount++;
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
     * 获取资源版本信息
     */
    public String getResourceVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * 检查是否有资源更新
     */
    public boolean hasResourceUpdates() {
        // 这里可以实现更复杂的版本检查逻辑
        // 目前简单返回false，表示没有更新
        return false;
    }
} 