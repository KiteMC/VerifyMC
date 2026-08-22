package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.util.PasswordUtil;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles user-facing profile status, email update, and password update endpoints.
 */
public class UserProfileHandler implements HttpHandler {
    private final PluginContext ctx;
    private final Action action;

    public UserProfileHandler(PluginContext ctx, Action action) {
        this.ctx = ctx;
        this.action = action;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (action) {
            case STATUS -> handleStatus(exchange);
            case UPDATE -> handleUpdate(exchange);
            case PASSWORD -> handlePassword(exchange);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        QueryParams params = parseQuery(exchange);
        String username = params.get("username");
        String language = params.getOrDefault("language", "zh");

        JSONObject resp = new JSONObject();
        if (username != null && !username.isBlank()) {
            Map<String, Object> user = ctx.getUserByConfiguredUsername(username);
            if (user != null) {
                resp.put("success", true);
                JSONObject data = new JSONObject();
                data.put("status", user.getOrDefault("status", "unknown"));
                data.put("registered", true);
                data.put("username", user.getOrDefault("username", ""));
                resp.put("data", data);
            } else {
                resp.put("success", true);
                JSONObject data = new JSONObject();
                data.put("registered", false);
                resp.put("data", data);
            }
        } else {
            resp.put("success", false);
            resp.put("message", ctx.getMessage("error.missing_username", language));
        }
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        UserRequest request = readUserRequest(exchange);
        if (request == null) return;

        String newEmail = request.body().optString("email", "").trim().toLowerCase();
        String code = request.body().optString("code", "").trim();

        if (newEmail.isBlank() || !isValidEmail(newEmail)) {
            sendFailure(exchange, "register.invalid_email", request.language());
            return;
        }

        Map<String, Object> user = ctx.getUserByConfiguredUsername(request.username());
        if (user == null) {
            sendFailure(exchange, "error.user_not_found", request.language());
            return;
        }

        String currentEmail = (String) user.get("email");
        if (newEmail.equalsIgnoreCase(currentEmail)) {
            sendSuccess(exchange, "user.update_success", request.language());
            return;
        }

        if (!isEmailDomainAllowed(newEmail)) {
            sendFailure(exchange, "register.domain_not_allowed", request.language());
            return;
        }

        int maxAccounts = ctx.getConfigManager().getMaxAccountsPerEmail();
        int emailCount = ctx.getUserDao().countUsersByEmail(newEmail);
        if (emailCount >= maxAccounts) {
            sendFailure(exchange, "register.email_limit", request.language());
            return;
        }

        if (code.isEmpty() || !ctx.getVerifyCodeService().checkCode(newEmail, code)) {
            sendFailure(exchange, "verify.wrong_code", request.language());
            return;
        }

        boolean updated = ctx.getUserDao().updateUserEmail(request.username(), newEmail, ctx.isUsernameCaseSensitive());
        if (updated) {
            addAudit("email_update", request.username(), request.username(), "Email updated to: " + newEmail);
            sendSuccess(exchange, "user.update_success", request.language());
        } else {
            sendFailure(exchange, "user.update_failed", request.language());
        }
    }

    private void handlePassword(HttpExchange exchange) throws IOException {
        UserRequest request = readUserRequest(exchange);
        if (request == null) return;

        String currentPassword = request.body().optString("currentPassword", "");
        String newPassword = request.body().optString("newPassword", "");

        if (currentPassword.isBlank() || newPassword.isBlank()) {
            sendFailure(exchange, "admin.password_required", request.language());
            return;
        }

        String passwordRegex = ctx.getConfigManager().getAuthmePasswordRegex();
        if (!newPassword.matches(passwordRegex)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.invalid_password", request.language()).replace("{regex}", passwordRegex)));
            return;
        }

        Map<String, Object> user = ctx.getUserByConfiguredUsername(request.username());
        if (user == null) {
            sendFailure(exchange, "error.user_not_found", request.language());
            return;
        }

        String storedPassword = (String) user.get("password");
        if (storedPassword == null || storedPassword.isBlank()) {
            sendFailure(exchange, "user.password_not_set", request.language());
            return;
        }

        if (!PasswordUtil.verify(currentPassword, storedPassword)) {
            sendFailure(exchange, "user.current_password_incorrect", request.language());
            return;
        }

        String actualUsername = String.valueOf(user.get("username"));
        boolean updated = ctx.getUserDao().updateUserPassword(actualUsername, newPassword, true);
        if (updated) {
            changeAuthmePasswordIfPossible(actualUsername, newPassword);
            addAudit("password_change", actualUsername, actualUsername, "User changed own password");
            sendSuccess(exchange, "admin.password_change_success", request.language());
        } else {
            sendFailure(exchange, "admin.password_change_failed", request.language());
        }
    }

    private UserRequest readUserRequest(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return null;

        String username = AdminAuthUtil.getAuthenticatedUser(exchange, ctx);
        if (username == null) return null;

        JSONObject body = readJson(exchange);
        if (body == null) return null;

        return new UserRequest(username, body.optString("language", "zh"), body);
    }

    private JSONObject readJson(HttpExchange exchange) throws IOException {
        try {
            return WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "zh")), 400);
            return null;
        }
    }

    private QueryParams parseQuery(HttpExchange exchange) {
        QueryParams params = new QueryParams();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return params;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length != 2) continue;
            if ("username".equals(kv[0])) {
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }

    private boolean isEmailDomainAllowed(String email) {
        if (!ctx.getConfigManager().isEmailDomainWhitelistEnabled()) return true;

        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
        List<String> whitelist = ctx.getConfigManager().getEmailDomainWhitelist();
        return whitelist.contains(domain);
    }

    private void changeAuthmePasswordIfPossible(String username, String password) {
        if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
            ctx.getAuthmeService().changePassword(username, password);
        }
    }

    private void addAudit(String action, String operator, String target, String detail) {
        ctx.getAuditDao().addAudit(new AuditRecord(action, operator, target, detail, System.currentTimeMillis()));
    }

    private void sendSuccess(HttpExchange exchange, String messageKey, String language) throws IOException {
        WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(ctx.getMessage(messageKey, language)));
    }

    private void sendFailure(HttpExchange exchange, String messageKey, String language) throws IOException {
        WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(ctx.getMessage(messageKey, language)));
    }

    public enum Action {
        STATUS,
        UPDATE,
        PASSWORD
    }

    private record UserRequest(String username, String language, JSONObject body) {
    }

    private static final class QueryParams {
        private final Map<String, String> values = new HashMap<>();

        private String get(String key) {
            return values.get(key);
        }

        private String getOrDefault(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        private void put(String key, String value) {
            values.put(key, value);
        }
    }
}
