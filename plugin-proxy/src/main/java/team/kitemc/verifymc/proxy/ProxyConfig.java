package team.kitemc.verifymc.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.yaml.snakeyaml.Yaml;
import java.util.Map;

/**
 * Configuration handler for VerifyMC Proxy
 */
public class ProxyConfig {
    private final File configFile;
    private Map<String, Object> config;
    
    // Configuration values
    private String backendUrl = "http://localhost:8080";
    private String apiKey = "";
    private String kickMessage = "&c[ VerifyMC ]\\n&7Please visit &a{url} &7to register";
    private String registerUrl = "https://yourdomain.com/";
    private boolean debug = false;
    private int timeout = 5000;
    private boolean cacheEnabled = true;
    private int cacheExpireSeconds = 60;
    private boolean autoUpdateConfig = true;
    private boolean autoUpdateI18n = true;
    private boolean backupOnUpdate = true;
    private String language = "zh";
    
    public ProxyConfig(File dataFolder) {
        this.configFile = new File(dataFolder, "config.yml");
        loadConfig();
    }
    
    /**
     * Load configuration from file
     */
    @SuppressWarnings("unchecked")
    public void loadConfig() {
        if (!configFile.exists()) {
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            config = yaml.load(fis);
            
            if (config != null) {
                backendUrl = getString("backend_url", backendUrl);
                apiKey = getString("api_key", apiKey);
                kickMessage = getString("kick_message", kickMessage);
                registerUrl = getString("register_url", registerUrl);
                debug = getBoolean("debug", debug);
                timeout = getInt("timeout", timeout);
                
                // Load cache settings
                Object cacheObj = config.get("cache");
                if (cacheObj instanceof Map) {
                    Map<String, Object> cacheMap = (Map<String, Object>) cacheObj;
                    cacheEnabled = cacheMap.get("enabled") != null && (Boolean) cacheMap.get("enabled");
                    Object expireObj = cacheMap.get("expire_seconds");
                    if (expireObj instanceof Number) {
                        cacheExpireSeconds = ((Number) expireObj).intValue();
                    }
                }
                
                // Load auto-update settings
                autoUpdateConfig = getBoolean("auto_update_config", autoUpdateConfig);
                autoUpdateI18n = getBoolean("auto_update_i18n", autoUpdateI18n);
                backupOnUpdate = getBoolean("backup_on_update", backupOnUpdate);
                language = getString("language", language);
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }
    
    /**
     * Get string value from config
     */
    private String getString(String key, String defaultValue) {
        if (config == null) return defaultValue;
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get boolean value from config
     */
    private boolean getBoolean(String key, boolean defaultValue) {
        if (config == null) return defaultValue;
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * Get integer value from config
     */
    private int getInt(String key, int defaultValue) {
        if (config == null) return defaultValue;
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    // Getters
    public String getBackendUrl() {
        return backendUrl;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public String getKickMessage() {
        return kickMessage;
    }
    
    public String getRegisterUrl() {
        return registerUrl;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    public int getCacheExpireSeconds() {
        return cacheExpireSeconds;
    }
    
    public boolean isAutoUpdateConfig() {
        return autoUpdateConfig;
    }
    
    public boolean isAutoUpdateI18n() {
        return autoUpdateI18n;
    }
    
    public boolean isBackupOnUpdate() {
        return backupOnUpdate;
    }
    
    public String getLanguage() {
        return language;
    }
}

