package team.kitemc.verifymc.proxy;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * VerifyMC Proxy Plugin for BungeeCord
 * Intercepts player login and verifies whitelist status via HTTP API
 */
public class VerifyMCProxy extends Plugin implements Listener {
    private ProxyConfig config;
    private ApiClient apiClient;
    private ProxyVersionCheckService versionCheckService;
    private ProxyResourceUpdater resourceUpdater;
    
    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = new ProxyConfig(getDataFolder(), getLogger());
        
        // Initialize API client
        apiClient = new ApiClient(config, getLogger());
        
        // Initialize version check service
        String version = getDescription().getVersion();
        versionCheckService = new ProxyVersionCheckService(version, getLogger(), config.isDebug());
        
        // Initialize resource updater
        resourceUpdater = new ProxyResourceUpdater(getDataFolder(), getLogger(), config.isDebug(), config, getClass());
        
        // Check and update resources
        resourceUpdater.checkAndUpdateResources();
        
        // Check for updates asynchronously
        checkForUpdates();
        
        // Register event listener
        getProxy().getPluginManager().registerListener(this, this);
        
        getLogger().info("[VerifyMC-Proxy] Plugin enabled!");
        getLogger().info("[VerifyMC-Proxy] Backend API: " + config.getBackendUrl());
    }
    
    /**
     * Check for plugin updates
     */
    private void checkForUpdates() {
        versionCheckService.checkForUpdatesAsync().thenAccept(result -> {
            if (result.isUpdateAvailable()) {
                getLogger().info("[VerifyMC-Proxy] A new version is available: " + result.getLatestVersion() + " (current: " + result.getCurrentVersion() + ")");
                getLogger().info("[VerifyMC-Proxy] Download: https://github.com/KiteMC/VerifyMC/releases");
            } else if (config.isDebug()) {
                getLogger().info("[VerifyMC-Proxy] You are running the latest version: " + result.getCurrentVersion());
            }
        }).exceptionally(e -> {
            if (config.isDebug()) {
                getLogger().warning("[VerifyMC-Proxy] Failed to check for updates: " + e.getMessage());
            }
            return null;
        });
    }
    
    @Override
    public void onDisable() {
        getLogger().info("[VerifyMC-Proxy] Plugin disabled!");
    }
    
    /**
     * Save default configuration file
     */
    private void saveDefaultConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    // Create default config manually
                    String defaultConfig = """
                        # VerifyMC Proxy Configuration
                        
                        # Backend server URL (where the main VerifyMC plugin is running)
                        backend_url: "http://localhost:8080"
                        
                        # API key for authentication (optional, set in main plugin config)
                        api_key: ""
                        
                        # Kick message for unregistered players
                        kick_message: "&c[ VerifyMC ]\\n&7Please visit &a{url} &7to register"
                        
                        # Registration URL to show in kick message
                        register_url: "https://yourdomain.com/"
                        
                        # Debug mode
                        debug: false
                        
                        # Request timeout in milliseconds
                        timeout: 5000
                        
                        # Cache settings
                        cache:
                          enabled: true
                          expire_seconds: 60
                        """;
                    Files.writeString(configFile.toPath(), defaultConfig);
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save default config", e);
            }
        }
    }
    
    /**
     * Handle player login event
     * Check if player is registered in VerifyMC backend
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        String playerName = event.getConnection().getName();
        
        if (config.isDebug()) {
            getLogger().info("[DEBUG] PreLogin check for: " + playerName);
        }
        
        try {
            // Check if player is approved
            ApiClient.WhitelistStatus status = apiClient.checkWhitelist(playerName);
            
            if (status == null || !status.isApproved()) {
                // Player not approved, cancel login
                String kickMessage = config.getKickMessage()
                    .replace("{url}", config.getRegisterUrl())
                    .replace("&", "§");
                
                event.setCancelled(true);
                event.setCancelReason(new TextComponent(kickMessage));
                
                if (config.isDebug()) {
                    getLogger().info("[DEBUG] Blocked player: " + playerName + " (not approved)");
                }
            } else {
                if (config.isDebug()) {
                    getLogger().info("[DEBUG] Allowed player: " + playerName + " (status: " + status.getStatus() + ")");
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to check whitelist for " + playerName, e);
            
            // On error, allow login by default (fail-open) to prevent lockout
            // Can be changed to fail-close if needed
        }
    }
}

