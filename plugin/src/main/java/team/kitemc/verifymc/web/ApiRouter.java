package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpServer;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.web.handler.*;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Central API router that maps HTTP paths to modular handler classes.
 * <p>
 * Replaces the 1271-line {@code WebServer.start()} method which defined
 * all routes inline as anonymous HttpHandler classes.
 * <p>
 * Each endpoint group is now a dedicated handler class, making the
 * routing topology visible at a glance and each handler independently testable.
 */
public class ApiRouter {
    private final PluginContext ctx;
    private final ConcurrentHashMap<String, RegistrationProcessingHandler.QuestionnaireSubmissionRecord> questionnaireSubmissionStore;

    public ApiRouter(PluginContext ctx) {
        this.ctx = ctx;
        this.questionnaireSubmissionStore = new ConcurrentHashMap<>();
    }

    /**
     * Register all API routes on the given HttpServer.
     */
    public void registerRoutes(HttpServer server) {
        // --- Configuration endpoint ---
        server.createContext("/api/config", new ConfigHandler(ctx));

        // --- Captcha endpoints ---
        server.createContext("/api/captcha/generate", new CaptchaHandler(ctx));
        server.createContext("/api/captcha", new CaptchaHandler(ctx));

        // --- Email verification ---
        server.createContext("/api/verify/send", new VerifyCodeHandler(ctx));

        // --- Questionnaire endpoints ---
        server.createContext("/api/questionnaire/config", new QuestionnaireConfigHandler(ctx));
        server.createContext("/api/questionnaire/submit", new QuestionnaireSubmitHandler(ctx, questionnaireSubmissionStore));

        // --- Registration ---
        server.createContext("/api/register", new RegistrationProcessingHandler(
                ctx.getPlugin(),
                ctx.getVerifyCodeService(),
                ctx.getUserDao(),
                ctx.getAuthmeService(),
                ctx.getCaptchaService(),
                ctx.getQuestionnaireService(),
                ctx.getDiscordService(),
                ctx.getRegistrationApplicationService(),
                questionnaireSubmissionStore,
                () -> ctx.getConfigManager().getEmailDomainWhitelist(),
                (key, lang) -> ctx.getMessage(key, lang),
                (username, platform) -> resolveUsernameRegex(username, platform),
                (username, platform) -> isValidUsername(username, platform),
                (username) -> hasUsernameCaseConflict(username),
                () -> ctx.getConfigManager().isUsernameCaseSensitive(),
                (username, platform) -> normalizeUsername(username, platform),
                (email) -> isValidEmail(email),
                (msg) -> ctx.debugLog(msg)
        ));

        // --- Review status check ---
        server.createContext("/api/review/status", new ReviewStatusHandler(ctx));

        // --- Login endpoints ---
        server.createContext("/api/login", new LoginHandler(ctx, false));
        server.createContext("/api/admin/login", new LoginHandler(ctx, true));

        // --- Admin endpoints ---
        server.createContext("/api/admin/verify", new AdminVerifyHandler(ctx));
        server.createContext("/api/admin/users", new AdminUserHandler(ctx, AdminUserHandler.Action.LIST));
        server.createContext("/api/admin/user/approve", new AdminUserHandler(ctx, AdminUserHandler.Action.APPROVE));
        server.createContext("/api/admin/user/reject", new AdminUserHandler(ctx, AdminUserHandler.Action.REJECT));
        server.createContext("/api/admin/user/delete", new AdminUserHandler(ctx, AdminUserHandler.Action.DELETE));
        server.createContext("/api/admin/user/ban", new AdminUserHandler(ctx, AdminUserHandler.Action.BAN));
        server.createContext("/api/admin/user/unban", new AdminUserHandler(ctx, AdminUserHandler.Action.UNBAN));
        server.createContext("/api/admin/user/password", new AdminUserHandler(ctx, AdminUserHandler.Action.PASSWORD));
        server.createContext("/api/admin/audits", new AdminAuditHandler(ctx));
        server.createContext("/api/admin/sync", new AdminSyncHandler(ctx));

        // --- Discord endpoints ---
        server.createContext("/api/discord/auth", new DiscordHandler(ctx, DiscordHandler.Action.AUTH));
        server.createContext("/api/discord/callback", new DiscordHandler(ctx, DiscordHandler.Action.CALLBACK));
        server.createContext("/api/discord/status", new DiscordHandler(ctx, DiscordHandler.Action.STATUS));
        server.createContext("/api/discord/unlink", new DiscordHandler(ctx, DiscordHandler.Action.UNLINK));

        // --- Version check ---
        server.createContext("/api/version", new VersionHandler(ctx));

        // --- User status query ---
        server.createContext("/api/user/status", new UserProfileHandler(ctx, UserProfileHandler.Action.STATUS));

        // --- Server status ---
        server.createContext("/api/server/status", new ServerStatusHandler(ctx));

        // --- Downloads ---
        server.createContext("/api/downloads", new DownloadsHandler(ctx));

        // --- User profile management ---
        server.createContext("/api/user/update", new UserProfileHandler(ctx, UserProfileHandler.Action.UPDATE));
        server.createContext("/api/user/password", new UserProfileHandler(ctx, UserProfileHandler.Action.PASSWORD));

        // --- Static files (front-end) ---
        server.createContext("/", new StaticFileHandler(ctx));
    }

    // --- Utility methods used by route wiring (delegated from WebServer) ---

    private boolean isValidUsername(String username, String platform) {
        if (username == null || username.trim().isEmpty()) return false;
        String regex = resolveUsernameRegex(username, platform);
        return username.matches(regex);
    }

    private String resolveUsernameRegex(String username, String platform) {
        if ("bedrock".equalsIgnoreCase(platform) && ctx.getConfigManager().isBedrockEnabled()) {
            return ctx.getConfigManager().getBedrockUsernameRegex();
        }
        return ctx.getConfigManager().getUsernameRegex();
    }

    private boolean hasUsernameCaseConflict(String username) {
        // If username is case-sensitive, no conflict check needed
        if (ctx.getConfigManager().isUsernameCaseSensitive()) {
            return false;
        }
        // If username is case-insensitive, check for case conflicts
        var existing = ctx.getUserDao().getUserByUsername(username, false);
        if (existing == null) return false;
        String storedName = (String) existing.get("username");
        return storedName != null && !storedName.equals(username) && storedName.equalsIgnoreCase(username);
    }

    private String normalizeUsername(String username, String platform) {
        if (username == null) return "";
        String trimmed = username.trim();
        if ("bedrock".equalsIgnoreCase(platform) && ctx.getConfigManager().isBedrockEnabled()) {
            String prefix = ctx.getConfigManager().getBedrockPrefix();
            if (!trimmed.startsWith(prefix)) {
                trimmed = prefix + trimmed;
            }
        }
        return trimmed;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }
}
