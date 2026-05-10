package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.OpsManager;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.service.AuthmeService;
import team.kitemc.verifymc.util.PasswordUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebAuthHelper;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoginHandler implements HttpHandler {
    private static final int MAX_ATTEMPTS_PER_IP = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    private final PluginContext ctx;
    private final boolean isAdminLogin;
    private final ConcurrentHashMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    private static class LoginAttempt {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    public LoginHandler(PluginContext ctx) {
        this(ctx, false);
    }

    public LoginHandler(PluginContext ctx, boolean isAdminLogin) {
        this.ctx = ctx;
        this.isAdminLogin = isAdminLogin;
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        LoginAttempt attempt = loginAttempts.compute(ip, (k, v) -> {
            if (v == null || (now - v.windowStart) > RATE_LIMIT_WINDOW_MS) {
                LoginAttempt fresh = new LoginAttempt();
                fresh.windowStart = now;
                fresh.count.set(1);
                return fresh;
            }
            v.count.incrementAndGet();
            return v;
        });
        return attempt.count.get() > MAX_ATTEMPTS_PER_IP;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (isRateLimited(clientIp)) {
            ctx.getPlugin().getLogger().warning("[Security] Login rate limit exceeded for IP: " + clientIp);
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    "Too many login attempts. Please try again later."), 429);
            return;
        }

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }
        String username = req.optString("username", "");
        String password = req.optString("password", "");
        String language = req.optString("language", "en");

        OpsManager opsManager = ctx.getOpsManager();
        if (isAdminLogin && (opsManager == null || opsManager.getOps().isEmpty())) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.system_error", language)));
            return;
        }

        UserDao userDao = ctx.getUserDao();
        Map<String, Object> user = userDao.getUserByUsername(username, ctx.isUsernameCaseSensitive());
        if (user == null) {
            user = userDao.getUserByEmail(username);
        }

        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        String actualUsername = (String) user.get("username");
        if (actualUsername == null || actualUsername.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.user_not_found", language)));
            return;
        }

        if (isAdminLogin) {
            if (!opsManager.isOp(actualUsername)) {
                ctx.getPlugin().getLogger().warning("[Security] Non-op login attempt for user: " + actualUsername);
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("login.not_authorized", language)));
                return;
            }
        }

        String storedPassword = (String) user.get("password");
        AuthmeService authmeService = ctx.getAuthmeService();
        boolean passwordValid = false;

        if (authmeService != null && authmeService.isAuthmeEnabled() && authmeService.hasAuthmeUser(actualUsername)) {
            String authmePassword = authmeService.getAuthmePassword(actualUsername);
            if (authmePassword != null && !authmePassword.isEmpty()) {
                passwordValid = PasswordUtil.verify(password, authmePassword);
            }
        }

        if (!passwordValid && storedPassword != null && !storedPassword.isEmpty()) {
            passwordValid = PasswordUtil.verify(password, storedPassword);
        }

        if (!passwordValid) {
            ctx.getPlugin().getLogger().warning("[Security] Failed login attempt - User: " + actualUsername + ", IP: " + clientIp + ", Reason: Invalid password");
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.failed", language)));
            return;
        }

        if (storedPassword != null && PasswordUtil.needsMigration(storedPassword)) {
            migratePassword(actualUsername, password, storedPassword);
        }

        WebAuthHelper webAuthHelper = ctx.getWebAuthHelper();
        String token = webAuthHelper.generateToken(actualUsername);
        boolean isAdmin = opsManager != null && opsManager.isOp(actualUsername);
        JSONObject resp = ApiResponseFactory.success(ctx.getMessage("login.success", language));
        resp.put("token", token);
        resp.put("username", actualUsername);
        resp.put("isAdmin", isAdmin);
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void migratePassword(String username, String plainPassword, String oldStoredPassword) {
        try {
            String migrationType = PasswordUtil.isPlaintext(oldStoredPassword) ? "plaintext" : "unsalted-sha256";
            boolean success = ctx.getUserDao().updateUserPassword(username, plainPassword);

            if (success) {
                ctx.getPlugin().getLogger().info("[VerifyMC] Password migration successful - User: " + username +
                        ", From: " + migrationType + ", To: salted-sha256");

                if (ctx.getAuditDao() != null) {
                    ctx.getAuditDao().addAudit(new AuditRecord(
                            "password_migration", "system", username,
                            "Migrated from " + migrationType + " to salted-sha256",
                            System.currentTimeMillis()));
                }
            } else {
                ctx.getPlugin().getLogger().warning("[VerifyMC] Password migration failed - User: " + username);
            }
        } catch (Exception e) {
            ctx.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                    "[VerifyMC] Password migration error - User: " + username, e);
        }
    }
}
