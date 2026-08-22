package team.kitemc.verifymc.proxy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Resource updater for VerifyMC Proxy Plugin
 * Handles automatic updates for config and i18n files
 */
public class ProxyResourceUpdater {
    private final File dataFolder;
    private final Logger logger;
    private final boolean debug;
    private final ProxyConfig config;
    private final Class<?> pluginClass;
    
    public ProxyResourceUpdater(File dataFolder, Logger logger, boolean debug, ProxyConfig config, Class<?> pluginClass) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.debug = debug;
        this.config = config;
        this.pluginClass = pluginClass;
    }
    
    private void debugLog(String msg) {
        if (debug) logger.info("[DEBUG] ProxyResourceUpdater: " + msg);
    }
    
    /**
     * Check and update all resources
     */
    public void checkAndUpdateResources() {
        debugLog("Checking for resource updates...");
        
        try {
            // Backup before update if enabled
            if (config.isBackupOnUpdate()) {
                createBackup();
            }
            
            // Check configuration file updates
            if (config.isAutoUpdateConfig()) {
                checkConfigUpdate();
            }
            
            // Check i18n file updates
            if (config.isAutoUpdateI18n()) {
                checkI18nUpdate();
            }
            
            debugLog("Resource update check completed");
        } catch (Exception e) {
            logger.warning("[VerifyMC-Proxy] Resource update check failed: " + e.getMessage());
            debugLog("Resource update check failed: " + e.getMessage());
        }
    }
    
    /**
     * Create backup of data folder
     */
    private void createBackup() {
        try {
            File backupDir = new File(dataFolder.getParentFile(), "VerifyMC-Proxy-Backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFile = new File(backupDir, "backup_" + timestamp);
            
            copyDirectory(dataFolder, backupFile);
            debugLog("Created backup at: " + backupFile.getAbsolutePath());
            
            // Clean old backups (keep last 5)
            cleanOldBackups(backupDir, 5);
        } catch (Exception e) {
            debugLog("Failed to create backup: " + e.getMessage());
        }
    }
    
    /**
     * Copy directory recursively
     */
    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    copyDirectory(new File(source, file), new File(target, file));
                }
            }
        } else {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Clean old backups, keeping only the specified number
     */
    private void cleanOldBackups(File backupDir, int keepCount) {
        File[] backups = backupDir.listFiles(file -> file.isDirectory() && file.getName().startsWith("backup_"));
        if (backups == null || backups.length <= keepCount) {
            return;
        }
        
        // Sort by modification time (oldest first)
        java.util.Arrays.sort(backups, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        
        // Delete oldest backups
        int deleteCount = backups.length - keepCount;
        for (int i = 0; i < deleteCount; i++) {
            deleteDirectory(backups[i]);
            debugLog("Deleted old backup: " + backups[i].getName());
        }
    }
    
    /**
     * Delete directory recursively
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
    
    /**
     * Check configuration file updates
     */
    private void checkConfigUpdate() {
        debugLog("Checking config.yml for updates...");
        
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            debugLog("Config file not found, will be created on first run");
            return;
        }
        
        try {
            String currentConfig = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            
            // Check if it contains new configuration keys
            String[] requiredKeys = {
                "auto_update_config",
                "auto_update_i18n",
                "backup_on_update",
                "language"
            };
            
            boolean needsUpdate = false;
            for (String key : requiredKeys) {
                if (!currentConfig.contains(key + ":")) {
                    needsUpdate = true;
                    debugLog("Config missing key: " + key);
                    break;
                }
            }
            
            if (needsUpdate) {
                // Backup current configuration
                File backupFile = new File(dataFolder, "config.yml.backup");
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debugLog("Backed up config to: " + backupFile.getAbsolutePath());
                
                // Merge new defaults into existing config
                mergeConfigDefaults(configFile);
                debugLog("Updated config.yml with new defaults");
            }
        } catch (Exception e) {
            debugLog("Error checking config update: " + e.getMessage());
        }
    }
    
    /**
     * Merge default configuration with existing configuration
     */
    private void mergeConfigDefaults(File configFile) {
        try {
            String existingConfig = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            
            // Add missing configuration sections at the end
            StringBuilder additions = new StringBuilder();
            
            if (!existingConfig.contains("auto_update_config:")) {
                additions.append("\n# Auto-update settings\n");
                additions.append("auto_update_config: true\n");
            }
            
            if (!existingConfig.contains("auto_update_i18n:")) {
                additions.append("auto_update_i18n: true\n");
            }
            
            if (!existingConfig.contains("backup_on_update:")) {
                additions.append("backup_on_update: true\n");
            }
            
            if (!existingConfig.contains("language:")) {
                additions.append("\n# Language setting (zh or en)\n");
                additions.append("language: zh\n");
            }
            
            if (additions.length() > 0) {
                String updatedConfig = existingConfig + additions.toString();
                Files.writeString(configFile.toPath(), updatedConfig, StandardCharsets.UTF_8);
                debugLog("Added missing config entries");
            }
        } catch (Exception e) {
            debugLog("Error merging config defaults: " + e.getMessage());
        }
    }
    
    /**
     * Check i18n file updates
     */
    private void checkI18nUpdate() {
        debugLog("Checking i18n files for updates...");
        
        File i18nDir = new File(dataFolder, "i18n");
        if (!i18nDir.exists()) {
            i18nDir.mkdirs();
        }
        
        // Ensure built-in zh and en files exist
        String[] builtinLanguages = {"zh", "en"};
        for (String lang : builtinLanguages) {
            File propFile = new File(i18nDir, "messages_" + lang + ".properties");
            if (!propFile.exists()) {
                createDefaultI18nFile(propFile, lang);
            } else {
                checkI18nFileUpdate(propFile, lang);
            }
        }
    }
    
    /**
     * Create default i18n file
     */
    private void createDefaultI18nFile(File propFile, String lang) {
        try {
            // Try to load from JAR
            InputStream in = pluginClass.getResourceAsStream("/i18n/messages_" + lang + ".properties");
            if (in != null) {
                Files.copy(in, propFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                in.close();
                debugLog("Created i18n file from JAR: " + propFile.getName());
            } else {
                // Create default content
                String content = getDefaultI18nContent(lang);
                Files.writeString(propFile.toPath(), content, StandardCharsets.UTF_8);
                debugLog("Created default i18n file: " + propFile.getName());
            }
        } catch (Exception e) {
            debugLog("Failed to create i18n file " + lang + ": " + e.getMessage());
        }
    }
    
    /**
     * Get default i18n content
     */
    private String getDefaultI18nContent(String lang) {
        if ("zh".equals(lang)) {
            return "# VerifyMC Proxy 中文语言文件\n" +
                   "proxy.enabled=VerifyMC Proxy 已启用\n" +
                   "proxy.disabled=VerifyMC Proxy 已禁用\n" +
                   "proxy.update_available=有新版本可用: {version}\n" +
                   "proxy.player_blocked=玩家 {player} 被阻止进入（未注册）\n" +
                   "proxy.player_allowed=玩家 {player} 已允许进入\n" +
                   "proxy.config_reloaded=配置已重新加载\n" +
                   "proxy.backup_created=备份已创建\n" +
                   "proxy.update_check_failed=版本检查失败\n";
        } else {
            return "# VerifyMC Proxy English Language File\n" +
                   "proxy.enabled=VerifyMC Proxy enabled\n" +
                   "proxy.disabled=VerifyMC Proxy disabled\n" +
                   "proxy.update_available=New version available: {version}\n" +
                   "proxy.player_blocked=Player {player} blocked (not registered)\n" +
                   "proxy.player_allowed=Player {player} allowed\n" +
                   "proxy.config_reloaded=Configuration reloaded\n" +
                   "proxy.backup_created=Backup created\n" +
                   "proxy.update_check_failed=Version check failed\n";
        }
    }
    
    /**
     * Check i18n file for updates
     */
    private void checkI18nFileUpdate(File propFile, String lang) {
        try {
            String currentContent = new String(Files.readAllBytes(propFile.toPath()), StandardCharsets.UTF_8);
            
            // Check if it contains new keys
            String[] requiredKeys = {
                "proxy.update_available",
                "proxy.backup_created"
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
                
                // Merge new keys
                mergeI18nDefaults(propFile, lang);
                debugLog("Updated i18n file: " + propFile.getName());
            }
        } catch (Exception e) {
            debugLog("Error checking i18n update: " + e.getMessage());
        }
    }
    
    /**
     * Merge default i18n keys with existing file
     */
    private void mergeI18nDefaults(File propFile, String lang) {
        try {
            Properties existing = new Properties();
            try (FileInputStream fis = new FileInputStream(propFile);
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                existing.load(reader);
            }
            
            Properties defaults = new Properties();
            defaults.load(new StringReader(getDefaultI18nContent(lang)));
            
            // Add missing keys
            boolean modified = false;
            for (String key : defaults.stringPropertyNames()) {
                if (!existing.containsKey(key)) {
                    existing.setProperty(key, defaults.getProperty(key));
                    modified = true;
                    debugLog("Added missing i18n key: " + key);
                }
            }
            
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(propFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    existing.store(writer, "VerifyMC Proxy Language File");
                }
            }
        } catch (Exception e) {
            debugLog("Error merging i18n defaults: " + e.getMessage());
        }
    }
}

