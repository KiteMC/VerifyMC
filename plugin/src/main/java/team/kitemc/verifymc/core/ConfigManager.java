package team.kitemc.verifymc.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Type-safe configuration access, eliminating scattered getConfig().getString(...)
 * calls with magic strings throughout the codebase.
 */
public class ConfigManager {
    private final JavaPlugin plugin;

    private static final List<String> DEFAULT_EMAIL_DOMAIN_WHITELIST = Arrays.asList(
        "gmail.com", "qq.com", "163.com", "126.com", "outlook.com", "hotmail.com", "yahoo.com",
        "sina.com", "aliyun.com", "foxmail.com", "icloud.com", "yeah.net", "live.com", "mail.com",
        "protonmail.com", "zoho.com"
    );

    private static final Set<String> VALID_STORAGE_TYPES = new HashSet<>(Arrays.asList("file", "mysql"));
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        validateConfig();
    }

    /**
     * Validates configuration values and logs warnings for invalid settings.
     */
    private void validateConfig() {
        // Validate web port
        int webPort = getWebPort();
        if (webPort < MIN_PORT || webPort > MAX_PORT) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid web_port: {0}. Must be between {1} and {2}. Using default 8080.",
                new Object[]{webPort, MIN_PORT, MAX_PORT});
        }

        // Validate WebSocket port
        int wsPort = getWsPort();
        if (wsPort < MIN_PORT || wsPort > MAX_PORT) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid ws_port: {0}. Must be between {1} and {2}. Using default 8081.",
                new Object[]{wsPort, MIN_PORT, MAX_PORT});
        }

        // Validate storage type
        String storageType = getStorageType();
        if (!VALID_STORAGE_TYPES.contains(storageType.toLowerCase())) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid storage type: {0}. Must be one of: {1}. Using default ''file''.",
                new Object[]{storageType, String.join(", ", VALID_STORAGE_TYPES)});
        }

        // Validate MySQL port if using MySQL storage
        if ("mysql".equalsIgnoreCase(storageType)) {
            int mysqlPort = getConfig().getInt("mysql.port", 3306);
            if (mysqlPort < MIN_PORT || mysqlPort > MAX_PORT) {
                plugin.getLogger().log(Level.WARNING,
                    "Invalid mysql.port: {0}. Must be between {1} and {2}. Using default 3306.",
                    new Object[]{mysqlPort, MIN_PORT, MAX_PORT});
            }
        }

        plugin.getLogger().log(Level.INFO, "Configuration validated successfully");
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }

    // --- General config ---
    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }

    public String getStorageType() {
        return getConfig().getString("storage", "file");
    }

    public String getLanguage() {
        return getConfig().getString("language", "zh");
    }

    // --- Web server ---
    public int getWebPort() {
        int port = getConfig().getInt("web_port", 8080);
        return (port >= MIN_PORT && port <= MAX_PORT) ? port : 8080;
    }

    public int getWsPort() {
        int port = getConfig().getInt("ws_port", 8081);
        return (port >= MIN_PORT && port <= MAX_PORT) ? port : 8081;
    }

    public String getWebServerPrefix() {
        return getConfig().getString("web_server_prefix", "[VerifyMC]");
    }

    // --- Frontend ---
    public String getTheme() {
        return getConfig().getString("frontend.theme", "default");
    }

    public String getLogoUrl() {
        return getConfig().getString("frontend.logo_url", "");
    }

    public String getAnnouncement() {
        return getConfig().getString("frontend.announcement", "");
    }

    public String getUsernameRegex() {
        return getConfig().getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$");
    }

    public boolean isUsernameCaseSensitive() {
        return getConfig().getBoolean("username_case_sensitive", false);
    }

    // --- Auth methods ---
    public List<String> getAuthMethods() {
        return getConfig().getStringList("auth_methods");
    }

    public boolean isEmailAuthEnabled() {
        return getAuthMethods().contains("email");
    }

    public boolean isCaptchaAuthEnabled() {
        return getAuthMethods().contains("captcha");
    }

    // --- Email ---
    public String getEmailSubject() {
        return getConfig().getString("email_subject", "VerifyMC Verification Code");
    }

    public boolean isEmailDomainWhitelistEnabled() {
        return getConfig().getBoolean("enable_email_domain_whitelist", true);
    }

    public List<String> getEmailDomainWhitelist() {
        List<String> list = null;
        try {
            list = getConfig().getStringList("email_domain_whitelist");
        } catch (Exception ignored) {}
        if (list == null || list.isEmpty()) {
            return DEFAULT_EMAIL_DOMAIN_WHITELIST;
        }
        return list;
    }

    public boolean isEmailAliasLimitEnabled() {
        return getConfig().getBoolean("enable_email_alias_limit", false);
    }

    public int getMaxAccountsPerEmail() {
        return getConfig().getInt("max_accounts_per_email", 2);
    }

    // --- Whitelist ---
    public String getWhitelistMode() {
        return getConfig().getString("whitelist_mode", "bukkit");
    }

    // --- Auto Update Resources ---
    public boolean isAutoUpdateResources() {
        return getConfig().getBoolean("auto_update_resources", true);
    }

    // --- AuthMe ---
    public boolean isAuthmeEnabled() {
        return getConfig().getBoolean("authme.enabled", false);
    }

    public boolean isAuthmePasswordRequired() {
        return getConfig().getBoolean("authme.require_password", false);
    }

    public String getAuthmePasswordRegex() {
        return getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{8,26}$");
    }

    public int getAuthmeSyncInterval() {
        return getConfig().getInt("authme.database.sync_interval_seconds", 30);
    }

    // --- Captcha ---
    public String getCaptchaType() {
        return getConfig().getString("captcha.type", "math");
    }

    // --- Bedrock ---
    public boolean isBedrockEnabled() {
        return getConfig().getBoolean("bedrock.enabled", false);
    }

    public String getBedrockPrefix() {
        return getConfig().getString("bedrock.prefix", ".");
    }

    public String getBedrockUsernameRegex() {
        return getConfig().getString("bedrock.username_regex", "^\\\\.[a-zA-Z0-9_\\\\s]{3,16}$");
    }

    // --- Registration ---
    public boolean isAutoApprove() {
        return getConfig().getBoolean("register.auto_approve", false);
    }

    // --- Questionnaire rate limit ---
    public int getQuestionnaireRateLimitIpMax() {
        return getConfig().getInt("questionnaire.rate_limit.ip.max", 20);
    }

    public int getQuestionnaireRateLimitUuidMax() {
        return getConfig().getInt("questionnaire.rate_limit.uuid.max", 8);
    }

    public int getQuestionnaireRateLimitEmailMax() {
        return getConfig().getInt("questionnaire.rate_limit.email.max", 6);
    }

    public long getQuestionnaireRateLimitWindowMs() {
        return getConfig().getLong("questionnaire.rate_limit.window_ms", 300000L);
    }

    // --- MySQL config ---
    public java.util.Properties getMysqlProperties() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("host", getConfig().getString("mysql.host", "localhost"));
        props.setProperty("port", String.valueOf(getConfig().getInt("mysql.port", 3306)));
        props.setProperty("database", getConfig().getString("mysql.database", "verifymc"));
        props.setProperty("user", getConfig().getString("mysql.user", "root"));
        props.setProperty("password", getConfig().getString("mysql.password", ""));
        props.setProperty("useSSL", String.valueOf(getMysqlUseSSL()));
        props.setProperty("allowPublicKeyRetrieval", String.valueOf(getMysqlAllowPublicKeyRetrieval()));
        return props;
    }

    /**
     * Get MySQL SSL setting. Default is true for security.
     */
    public boolean getMysqlUseSSL() {
        return getConfig().getBoolean("mysql.useSSL", true);
    }

    /**
     * Get MySQL allowPublicKeyRetrieval setting. Default is false for security.
     * Enable this if you need to connect to MySQL 8.0+ with default authentication.
     */
    public boolean getMysqlAllowPublicKeyRetrieval() {
        return getConfig().getBoolean("mysql.allowPublicKeyRetrieval", false);
    }

    public java.util.List<java.util.Map<String, Object>> getDownloadResources() {
        java.util.List<java.util.Map<String, Object>> resources = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("downloads");
        if (section == null) {
            return resources;
        }
        for (String key : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection resourceSection = section.getConfigurationSection(key);
            if (resourceSection != null) {
                java.util.Map<String, Object> resource = new java.util.HashMap<>();
                resource.put("id", key);
                resource.put("name", resourceSection.getString("name", key));
                resource.put("description", resourceSection.getString("description", ""));
                resource.put("version", resourceSection.getString("version", ""));
                resource.put("size", resourceSection.getString("size", ""));
                resource.put("url", resourceSection.getString("url", ""));
                resource.put("icon", resourceSection.getString("icon", "package"));
                resources.add(resource);
            }
        }
        return resources;
    }
}
