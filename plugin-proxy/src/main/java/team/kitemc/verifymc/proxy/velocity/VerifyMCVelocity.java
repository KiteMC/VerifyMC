package team.kitemc.verifymc.proxy.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import team.kitemc.verifymc.proxy.ApiClient;
import team.kitemc.verifymc.proxy.ProxyConfig;
import team.kitemc.verifymc.proxy.ProxyResourceUpdater;
import team.kitemc.verifymc.proxy.ProxyVersionCheckService;

/**
 * VerifyMC Proxy Plugin for Velocity
 * Intercepts player login and verifies whitelist status via HTTP API
 */
@Plugin(
    id = "verifymc-proxy",
    name = "VerifyMC-Proxy",
    version = "1.7.3",
    description = "VerifyMC proxy plugin for Velocity",
    authors = {"KiteMC"}
)
@SuppressWarnings("unused")
public class VerifyMCVelocity {
    private final ProxyServer server; // Reserved for future use (e.g., getting online players)
    private final Logger logger;
    private final Path dataDirectory;

    private ProxyConfig config;
    private ApiClient apiClient;
    private ProxyVersionCheckService versionCheckService;
    private ProxyResourceUpdater resourceUpdater;

    // Wrapper to adapt SLF4J Logger to java.util.logging.Logger
    private java.util.logging.Logger julLogger;

    @Inject
    public VerifyMCVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        // Create a JUL logger wrapper
        this.julLogger = new java.util.logging.Logger("VerifyMC-Proxy", null) {
            @Override
            public void info(String msg) {
                logger.info(msg);
            }

            @Override
            public void warning(String msg) {
                logger.warn(msg);
            }
        };
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Create data directory
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                logger.error("Failed to create data directory", e);
            }
        }

        // Save default config
        saveDefaultConfig();

        // Load configuration
        config = new ProxyConfig(dataDirectory.toFile());

        // Initialize API client
        apiClient = new ApiClient(config, julLogger);

        // Initialize version check service
        String version = "1.7.3"; // From @Plugin annotation
        versionCheckService = new ProxyVersionCheckService(version, julLogger, config.isDebug());

        // Initialize resource updater
        resourceUpdater = new ProxyResourceUpdater(dataDirectory.toFile(), julLogger, config.isDebug(), config, getClass());

        // Check and update resources
        resourceUpdater.checkAndUpdateResources();

        // Check for updates asynchronously
        checkForUpdates();

        logger.info("[VerifyMC-Proxy] Plugin enabled!");
        logger.info("[VerifyMC-Proxy] Backend API: " + config.getBackendUrl());
    }

    /**
     * Check for plugin updates
     */
    private void checkForUpdates() {
        versionCheckService.checkForUpdatesAsync().thenAccept(result -> {
            if (result.isUpdateAvailable()) {
                logger.info("[VerifyMC-Proxy] A new version is available: " + result.getLatestVersion() + " (current: " + result.getCurrentVersion() + ")");
                logger.info("[VerifyMC-Proxy] Download: https://github.com/KiteMC/VerifyMC/releases");
            } else if (config.isDebug()) {
                logger.info("[VerifyMC-Proxy] You are running the latest version: " + result.getCurrentVersion());
            }
        }).exceptionally(e -> {
            if (config.isDebug()) {
                logger.warn("[VerifyMC-Proxy] Failed to check for updates: " + e.getMessage());
            }
            return null;
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("[VerifyMC-Proxy] Plugin disabled!");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String playerName = event.getUsername();

        if (config.isDebug()) {
            logger.info("[DEBUG] PreLogin check for: " + playerName);
        }

        try {
            // Check if player is approved
            ApiClient.WhitelistStatus status = apiClient.checkWhitelist(playerName);

            if (status == null || !status.isApproved()) {
                // Player not approved, cancel login
                String kickMessage = config.getKickMessage()
                    .replace("{url}", config.getRegisterUrl());

                Component kickComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(kickMessage);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickComponent));

                if (config.isDebug()) {
                    String reason = (status == null) ? "lookup failed" : "not approved";
                    logger.info("[DEBUG] Blocked player: {} ({})", playerName, reason);
                }
            } else {
                if (config.isDebug()) {
                    logger.info("[DEBUG] Allowed player: " + playerName + " (status: " + status.getStatus() + ")");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check whitelist for " + playerName, e);
            String kickMessage = config.getKickMessage()
                .replace("{url}", config.getRegisterUrl());
            Component kickComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(kickMessage);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickComponent));
        }
    }

    /**
     * Save default configuration file
     */
    private void saveDefaultConfig() {
        Path configPath = dataDirectory.resolve("config.yml");

        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configPath);
                } else {
                    // Create default config manually
                    String defaultConfig = """
                        # VerifyMC Proxy Configuration
                        # This plugin works with both BungeeCord and Velocity

                        # Backend server URL (where the main VerifyMC plugin is running)
                        backend_url: "http://localhost:8080"

                        # API key for authentication (optional)
                        api_key: ""

                        # Kick message for unregistered players
                        kick_message: "&c[ VerifyMC ]\\n&7Please visit &a{url} &7to register"

                        # Registration URL to show in kick message
                        register_url: "https://yourdomain.com/"

                        # Language setting (zh or en)
                        language: zh

                        # Debug mode
                        debug: false

                        # Request timeout in milliseconds
                        timeout: 5000

                        # Cache settings
                        cache:
                          enabled: true
                          expire_seconds: 60

                        # Auto-update settings
                        auto_update_config: true
                        auto_update_i18n: true
                        backup_on_update: true
                        """;
                    Files.writeString(configPath, defaultConfig);
                }
            } catch (IOException e) {
                logger.error("Failed to save default config", e);
            }
        }
    }
}
