package team.kitemc.verifymc.core;

import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.db.AuditDao;
import team.kitemc.verifymc.db.FileUserDao;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.service.*;
import team.kitemc.verifymc.web.ReviewWebSocketServer;
import team.kitemc.verifymc.web.WebAuthHelper;

import java.util.Map;

/**
 * Central service container that holds references to all services.
 * Replaces the 13-parameter constructor of WebServer and the scattered
 * service fields in VerifyMC.
 */
public class PluginContext {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final I18nManager i18nManager;
    private final ResourceManager resourceManager;
    private OpsManager opsManager;

    // Data access
    private UserDao userDao;
    private AuditDao auditDao;

    // Services
    private MailService mailService;
    private VerifyCodeService verifyCodeService;
    private AuthmeService authmeService;
    private CaptchaService captchaService;
    private QuestionnaireService questionnaireService;
    private DiscordService discordService;
    private VersionCheckService versionCheckService;

    // Application services
    private RegistrationApplicationService registrationApplicationService;
    private ReviewApplicationService reviewApplicationService;
    private QuestionnaireApplicationService questionnaireApplicationService;

    // Web layer
    private ReviewWebSocketServer wsServer;
    private WebAuthHelper webAuthHelper;

    public PluginContext(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        this.i18nManager = new I18nManager(plugin);
        this.resourceManager = new ResourceManager(plugin);
        this.resourceManager.setConfigManager(configManager);
    }

    // --- Getters ---
    public JavaPlugin getPlugin() { return plugin; }
    public ConfigManager getConfigManager() { return configManager; }
    public I18nManager getI18nManager() { return i18nManager; }
    public ResourceManager getResourceManager() { return resourceManager; }
    public OpsManager getOpsManager() { return opsManager; }

    public UserDao getUserDao() { return userDao; }
    public AuditDao getAuditDao() { return auditDao; }
    public MailService getMailService() { return mailService; }
    public VerifyCodeService getVerifyCodeService() { return verifyCodeService; }
    public AuthmeService getAuthmeService() { return authmeService; }
    public CaptchaService getCaptchaService() { return captchaService; }
    public QuestionnaireService getQuestionnaireService() { return questionnaireService; }
    public DiscordService getDiscordService() { return discordService; }
    public VersionCheckService getVersionCheckService() { return versionCheckService; }
    public RegistrationApplicationService getRegistrationApplicationService() { return registrationApplicationService; }
    public ReviewApplicationService getReviewApplicationService() { return reviewApplicationService; }
    public QuestionnaireApplicationService getQuestionnaireApplicationService() { return questionnaireApplicationService; }
    public ReviewWebSocketServer getWsServer() { return wsServer; }
    public WebAuthHelper getWebAuthHelper() { return webAuthHelper; }

    // --- Setters (for initialization phase) ---
    public void setUserDao(UserDao userDao) { this.userDao = userDao; }
    public void setAuditDao(AuditDao auditDao) { this.auditDao = auditDao; }
    public void setMailService(MailService mailService) { this.mailService = mailService; }
    public void setVerifyCodeService(VerifyCodeService verifyCodeService) { this.verifyCodeService = verifyCodeService; }
    public void setAuthmeService(AuthmeService authmeService) { this.authmeService = authmeService; }
    public void setCaptchaService(CaptchaService captchaService) { this.captchaService = captchaService; }
    public void setQuestionnaireService(QuestionnaireService questionnaireService) { this.questionnaireService = questionnaireService; }
    public void setDiscordService(DiscordService discordService) { this.discordService = discordService; }
    public void setVersionCheckService(VersionCheckService versionCheckService) { this.versionCheckService = versionCheckService; }
    public void setRegistrationApplicationService(RegistrationApplicationService svc) { this.registrationApplicationService = svc; }
    public void setReviewApplicationService(ReviewApplicationService svc) { this.reviewApplicationService = svc; }
    public void setQuestionnaireApplicationService(QuestionnaireApplicationService svc) { this.questionnaireApplicationService = svc; }
    public void setWsServer(ReviewWebSocketServer wsServer) { this.wsServer = wsServer; }
    public void setWebAuthHelper(WebAuthHelper webAuthHelper) { this.webAuthHelper = webAuthHelper; }
    public void setOpsManager(OpsManager opsManager) { this.opsManager = opsManager; }

    public boolean isDebug() {
        return configManager.isDebug();
    }

    public void debugLog(String msg) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    /**
     * Get i18n message by key and language.
     */
    public String getMessage(String key, String language) {
        return i18nManager.getMessage(key, language);
    }

    public boolean isUsernameCaseSensitive() {
        return configManager.isUsernameCaseSensitive();
    }

    public Map<String, Object> getUserByConfiguredUsername(String username) {
        return userDao.getUserByUsername(username, isUsernameCaseSensitive());
    }

    public String resolveStoredUsername(String username) {
        return userDao.resolveStoredUsername(username, isUsernameCaseSensitive());
    }

    public void reloadRuntimeConfigDependents() {
        if (userDao instanceof FileUserDao fileUserDao) {
            fileUserDao.rebuildUsernameIndex();
        }
        if (discordService != null) {
            discordService.loadConfig();
        }
        if (questionnaireService != null) {
            questionnaireService.reloadConfig();
        }
    }
}
