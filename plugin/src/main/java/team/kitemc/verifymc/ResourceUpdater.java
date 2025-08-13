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
     * Check and update all resource files
     */
    public void checkAndUpdateResources() {
        debugLog("Checking for resource updates...");
        
        try {
            // Check configuration file updates
            checkConfigUpdate();
            
            // Check i18n file updates
            checkI18nUpdate();
            
            // Check email template updates
            checkEmailUpdate();
            
            // Check static file updates
            checkStaticUpdate();
            
            debugLog("Resource update check completed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check resource updates: " + e.getMessage());
            debugLog("Resource update check failed: " + e.getMessage());
        }
    }

    /**
     * Check configuration file updates
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
            
            // Check if it contains new version key configuration items
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
                // Backup current configuration
                File backupFile = new File(plugin.getDataFolder(), "config.yml.backup");
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debugLog("Backed up config to: " + backupFile.getAbsolutePath());
                
                // Re-save default configuration
                plugin.saveDefaultConfig();
                debugLog("Updated config.yml with new defaults");
            }
        } catch (Exception e) {
            debugLog("Error checking config update: " + e.getMessage());
        }
    }

    /**
     * Check i18n file updates
     */
    private void checkI18nUpdate() {
        debugLog("Checking i18n files for updates...");
        File i18nDir = new File(plugin.getDataFolder(), "i18n");
        // Scan all messages_*.properties files
        File[] existingFiles = i18nDir.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
        if (existingFiles != null) {
            for (File propFile : existingFiles) {
                String fileName = propFile.getName();
                String lang = fileName.substring("messages_".length(), fileName.length() - ".properties".length());
                debugLog("Found existing i18n file: " + fileName + " (language: " + lang + ")");
                checkI18nFileUpdate(propFile, lang);
            }
        }
        // Ensure built-in zh and en files exist
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
     * Create i18n file
     * @param lang Language code
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
     * Check i18n file update
     * @param propFile Properties file to check
     * @param lang Language code
     */
    private void checkI18nFileUpdate(File propFile, String lang) {
        try {
            String currentContent = new String(Files.readAllBytes(propFile.toPath()), StandardCharsets.UTF_8);
            
            // Check if it contains new version key messages
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
                // Backup current file
                File backupFile = new File(propFile.getParentFile(), propFile.getName() + ".backup");
                Files.copy(propFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debugLog("Backed up i18n file: " + backupFile.getName());
                
                // Re-save default file
                createI18nFile(lang);
                debugLog("Updated i18n file: " + propFile.getName());
            }
        } catch (Exception e) {
            debugLog("Error checking i18n update: " + e.getMessage());
        }
    }

    /**
     * Check email template updates
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
     * Create email template
     * @param lang Language code
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
     * Check static file updates
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
            
            // Check if theme directory is empty or needs update
            if (shouldUpdateStaticFiles(themeDir, theme)) {
                extractStaticFiles(themeDir, theme);
            }
        }
    }

    /**
     * Check if static files need to be updated
     * @param themeDir Theme directory
     * @param theme Theme name
     * @return true if update is needed
     */
    private boolean shouldUpdateStaticFiles(File themeDir, String theme) {
        File[] files = themeDir.listFiles();
        if (files == null || files.length == 0) {
            debugLog("Theme directory is empty: " + theme);
            return true;
        }
        
        // Check if key files exist
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
     * Extract static files from jar package
     * @param themeDir Theme directory
     * @param theme Theme name
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
     * Get resource version information
     * @return Resource version
     */
    public String getResourceVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Check if there are resource updates
     * @return true if updates are available
     */
    public boolean hasResourceUpdates() {
        // More complex version checking logic can be implemented here
        // Currently simply returns false, indicating no updates
        return false;
    }
} 