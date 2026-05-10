package team.kitemc.verifymc;

import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.core.ConfigManager;
import team.kitemc.verifymc.core.OpsManager;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.*;
import team.kitemc.verifymc.listener.PlayerLoginListener;
import team.kitemc.verifymc.command.VmcCommandExecutor;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.registration.RegistrationOutcomeResolver;
import team.kitemc.verifymc.service.*;
import team.kitemc.verifymc.util.PluginScheduler;
import team.kitemc.verifymc.web.ReviewWebSocketServer;
import team.kitemc.verifymc.web.WebAuthHelper;
import team.kitemc.verifymc.web.WebServer;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * VerifyMC plugin entrypoint — refactored from the 878-line god class
 * into a clean initialization orchestrator.
 * <p>
 * Responsibilities:
 * - Initialize the {@link PluginContext} service container
 * - Wire up all services, data access, and web layer
 * - Register event listeners and command executors
 * - Manage lifecycle (enable/disable)
 */
public class VerifyMC extends JavaPlugin {
    private PluginContext context;
    private WebServer webServer;
    private ReviewWebSocketServer wsServer;
    private Metrics metrics;
    private PluginScheduler.ScheduledTask authmeSyncTask;

    @Override
    public void onEnable() {
        Logger log = getLogger();

        // --- Core infrastructure ---
        context = new PluginContext(this);
        context.getResourceManager().init();
        context.getConfigManager().reloadConfig();
        context.getI18nManager().init(context.getConfigManager().getLanguage());

        // --- Data access layer ---
        initDataLayer(log);

        // --- Services ---
        initServices(log);

        // --- Web layer ---
        initWebLayer(log);

        // --- Event listeners ---
        getServer().getPluginManager().registerEvents(
                new PlayerLoginListener(context), this);

        // --- Commands ---
        var vmcCommand = getCommand("vmc");
        if (vmcCommand != null) {
            VmcCommandExecutor executor = new VmcCommandExecutor(context);
            vmcCommand.setExecutor(executor);
            vmcCommand.setTabCompleter(executor);
        }

        // --- Metrics ---
        try {
            metrics = new Metrics(this, 21854);
        } catch (Exception e) {
            log.warning("[VerifyMC] Metrics init failed: " + e.getMessage());
        }

        // --- Version check ---
        if (context.getVersionCheckService() != null) {
            context.getVersionCheckService().checkAsync();
        }

        log.info("[VerifyMC] Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        Logger log = getLogger();

        // Stop web server
        if (webServer != null) {
            webServer.stop();
        }

        // Stop WebSocket server
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (Exception e) {
                log.warning("[VerifyMC] WebSocket server stop error: " + e.getMessage());
            }
        }

        // Stop service cleanup threads
        if (authmeSyncTask != null) {
            authmeSyncTask.cancel();
            authmeSyncTask = null;
        }
        if (context != null) {
            if (context.getWebAuthHelper() != null) {
                context.getWebAuthHelper().stopTokenCleanupTask();
            }
            if (context.getVerifyCodeService() != null) {
                context.getVerifyCodeService().stop();
            }
            if (context.getCaptchaService() != null) {
                context.getCaptchaService().stop();
            }
            if (context.getDiscordService() != null) {
                context.getDiscordService().stop();
            }
        }

        // Save and close data access layer
        if (context != null) {
            if (context.getUserDao() != null) {
                context.getUserDao().save();
                context.getUserDao().close();
            }
            if (context.getAuditDao() != null) {
                context.getAuditDao().save();
                context.getAuditDao().close();
            }
        }

        // Shutdown metrics
        if (metrics != null) {
            metrics.shutdown();
        }

        log.info("[VerifyMC] Plugin disabled.");
    }

    private void initDataLayer(Logger log) {
        ConfigManager config = context.getConfigManager();
        String storageType = config.getStorageType();

        try {
            if ("mysql".equalsIgnoreCase(storageType)) {
                var props = config.getMysqlProperties();
                context.setUserDao(new MysqlUserDao(props, context.getI18nManager().getResourceBundle(), this));
                context.setAuditDao(new MysqlAuditDao(props, this));
                log.info("[VerifyMC] Using MySQL storage.");
            } else {
                File dataDir = getDataFolder();
                context.setUserDao(new FileUserDao(new File(dataDir, "users.json"), this));
                context.setAuditDao(new FileAuditDao(new File(dataDir, "audits.json")));
                log.info("[VerifyMC] Using file storage.");
            }
        } catch (SQLException e) {
            log.severe("[VerifyMC] Database initialization failed: " + e.getMessage());
            log.info("[VerifyMC] Falling back to file storage.");
            File dataDir = getDataFolder();
            context.setUserDao(new FileUserDao(new File(dataDir, "users.json"), this));
            context.setAuditDao(new FileAuditDao(new File(dataDir, "audits.json")));
        }
    }

    private void initServices(Logger log) {
        ConfigManager config = context.getConfigManager();

        context.setOpsManager(new OpsManager(this));

        context.getResourceManager().setI18nManager(context.getI18nManager());

        // Mail service
        context.setMailService(new MailService(this, context.getResourceManager()));

        // Verify code service
        context.setVerifyCodeService(new VerifyCodeService(this));

        // AuthMe service
        AuthmeService authmeService = new AuthmeService(this);
        authmeService.setUserDao(context.getUserDao());
        context.setAuthmeService(authmeService);

        // Sync AuthMe data on startup if enabled
        if (authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUsers();
            log.info("[VerifyMC] AuthMe sync completed on startup.");

            // Schedule periodic sync
            int syncInterval = config.getAuthmeSyncInterval();
            if (syncInterval > 0) {
                authmeSyncTask = PluginScheduler.runAsyncTimer(
                        this,
                        authmeService::syncApprovedUsers,
                        syncInterval * 20L,
                        syncInterval * 20L
                );
                log.info("[VerifyMC] AuthMe periodic sync scheduled every " + syncInterval + " seconds.");
            }
        }

        // Captcha service
        context.setCaptchaService(new CaptchaService(this));

        // Questionnaire service
        context.setQuestionnaireService(new QuestionnaireService(this));

        // Discord service
        DiscordService discordService = new DiscordService(this);
        discordService.setUserDao(context.getUserDao());
        context.setDiscordService(discordService);

        // Version check service
        context.setVersionCheckService(new VersionCheckService(this));

        // Registration application service
        context.setRegistrationApplicationService(new RegistrationApplicationService());

        // Review application service
        context.setReviewApplicationService(new ReviewApplicationService());

        // Questionnaire application service
        context.setQuestionnaireApplicationService(new QuestionnaireApplicationService());

        // --- Web auth ---
        WebAuthHelper webAuthHelper = new WebAuthHelper(this, context.getI18nManager());
        webAuthHelper.startTokenCleanupTask();
        context.setWebAuthHelper(webAuthHelper);
    }

    private void initWebLayer(Logger log) {
        // WebSocket server for review notifications
        int wsPort = context.getConfigManager().getWsPort();
        try {
            wsServer = new ReviewWebSocketServer(wsPort, context);
            wsServer.start();
            context.setWsServer(wsServer);
            log.info("[VerifyMC] WebSocket server started on port " + wsPort);
        } catch (Exception e) {
            log.warning("[VerifyMC] WebSocket server failed to start: " + e.getMessage());
        }

        // HTTP server
        webServer = new WebServer(context);
        webServer.start();
    }

    /**
     * Access the plugin context from external code.
     */
    public PluginContext getContext() {
        return context;
    }
}
