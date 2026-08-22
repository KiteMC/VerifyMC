package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.util.PluginScheduler;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles admin user listing and user lifecycle actions.
 */
public class AdminUserHandler implements HttpHandler {
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_.\\-\\s]{1,32}$");

    private final PluginContext ctx;
    private final Action action;

    public AdminUserHandler(PluginContext ctx, Action action) {
        this.ctx = ctx;
        this.action = action;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (action) {
            case LIST -> handleList(exchange);
            case APPROVE -> handleApprove(exchange);
            case REJECT -> handleReject(exchange);
            case DELETE -> handleDelete(exchange);
            case BAN -> handleBan(exchange);
            case UNBAN -> handleUnban(exchange);
            case PASSWORD -> handlePassword(exchange);
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;
        if (AdminAuthUtil.requireAdmin(exchange, ctx) == null) return;

        QueryParams params = parseQuery(exchange);
        int page = parseInt(params.get("page"), 1);
        int size = parseInt(params.get("size"), 20);
        String search = params.get("search");
        String status = params.get("status");

        List<Map<String, Object>> users = ctx.getUserDao().getUsers(page, size, search, status);
        int total = ctx.getUserDao().getTotalUsers(search, status);
        int totalPages = (int) Math.ceil((double) total / size);

        JSONArray usersArray = new JSONArray();
        for (Map<String, Object> user : users) {
            Map<String, Object> safeUser = new HashMap<>(user);
            safeUser.remove("password");
            usersArray.put(new JSONObject(safeUser));
        }

        JSONObject pagination = new JSONObject();
        pagination.put("currentPage", page);
        pagination.put("pageSize", size);
        pagination.put("totalCount", total);
        pagination.put("totalPages", totalPages);
        pagination.put("hasNext", page < totalPages);
        pagination.put("hasPrev", page > 1);

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("users", usersArray);
        resp.put("pagination", pagination);
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void handleApprove(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, true);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "review.failed", request.language());
            return;
        }

        boolean ok = ctx.getUserDao().updateUserStatus(target, "approved", request.operator());
        if (ok) {
            addToWhitelist(target);
            registerWithAuthmeIfPossible(target);
            sendReviewResult(target, true, "");
            addAudit("approve", request.operator(), target, "");
            broadcast("user_approved", target);
            sendSuccess(exchange, "review.approve_success", request.language());
        } else {
            sendFailure(exchange, "review.failed", request.language());
        }
    }

    private void handleReject(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, false);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "review.failed", request.language());
            return;
        }

        String reason = request.body().optString("reason", "");
        boolean ok = ctx.getUserDao().updateUserStatus(target, "rejected", request.operator());
        if (ok) {
            sendReviewResult(target, false, reason);
            addAudit("reject", request.operator(), target, reason);
            broadcast("user_rejected", target);
            sendSuccess(exchange, "review.reject_success", request.language());
        } else {
            sendFailure(exchange, "review.failed", request.language());
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, true);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "admin.delete_failed", request.language());
            return;
        }

        boolean ok = ctx.getUserDao().deleteUser(target);
        if (ok) {
            removeFromWhitelist(target);
            unregisterFromAuthmeIfPossible(target);
            addAudit("delete", request.operator(), target, "");
            sendSuccess(exchange, "admin.delete_success", request.language());
        } else {
            sendFailure(exchange, "admin.delete_failed", request.language());
        }
    }

    private void handleBan(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, true);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "admin.ban_failed", request.language());
            return;
        }

        String reason = request.body().optString("reason", "");
        boolean ok = ctx.getUserDao().banUser(target);
        if (ok) {
            removeFromWhitelist(target);
            unregisterFromAuthmeIfPossible(target);
            addAudit("ban", request.operator(), target, reason);
            sendSuccess(exchange, "admin.ban_success", request.language());
        } else {
            sendFailure(exchange, "admin.ban_failed", request.language());
        }
    }

    private void handleUnban(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, false);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "admin.unban_failed", request.language());
            return;
        }

        boolean ok = ctx.getUserDao().unbanUser(target);
        if (ok) {
            addToWhitelist(target);
            registerWithAuthmeIfPossible(target);
            addAudit("unban", request.operator(), target, "");
            sendSuccess(exchange, "admin.unban_success", request.language());
        } else {
            sendFailure(exchange, "admin.unban_failed", request.language());
        }
    }

    private void handlePassword(HttpExchange exchange) throws IOException {
        AdminRequest request = readAdminRequest(exchange, false);
        if (request == null) return;

        String target = resolveTarget(request);
        if (target == null) {
            sendFailure(exchange, "admin.password_change_failed", request.language());
            return;
        }

        String password = request.body().optString("password", "");
        if (password.isBlank()) {
            sendFailure(exchange, "admin.missing_user_identifier", request.language());
            return;
        }

        boolean ok = ctx.getUserDao().updatePassword(target, password);
        if (ok && ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
            ctx.getAuthmeService().changePassword(target, password);
        }

        if (ok) {
            addAudit("password_change", request.operator(), target, "");
            sendSuccess(exchange, "admin.password_change_success", request.language());
        } else {
            sendFailure(exchange, "admin.password_change_failed", request.language());
        }
    }

    private AdminRequest readAdminRequest(HttpExchange exchange, boolean validateUsername) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return null;

        String operator = AdminAuthUtil.requireAdmin(exchange, ctx);
        if (operator == null) return null;

        JSONObject body = readJson(exchange);
        if (body == null) return null;

        String target = body.optString("username", body.optString("uuid", ""));
        String language = body.optString("language", "zh");

        if (target.isBlank()) {
            sendFailure(exchange, "admin.missing_user_identifier", language);
            return null;
        }

        if (validateUsername && !VALID_USERNAME.matcher(target).matches()) {
            sendFailure(exchange, "admin.invalid_username", language);
            return null;
        }

        return new AdminRequest(operator, target, language, body);
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

    private String resolveTarget(AdminRequest request) {
        return ctx.resolveStoredUsername(request.target());
    }

    private QueryParams parseQuery(HttpExchange exchange) {
        QueryParams params = new QueryParams();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return params;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length != 2) continue;
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void addToWhitelist(String username) {
        PluginScheduler.runGlobal(ctx.getPlugin(), () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + username));
    }

    private void removeFromWhitelist(String username) {
        PluginScheduler.runGlobal(ctx.getPlugin(), () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + username));
    }

    private void registerWithAuthmeIfPossible(String username) {
        if (ctx.getAuthmeService() == null || !ctx.getAuthmeService().isAuthmeEnabled()) return;

        var user = ctx.getUserByConfiguredUsername(username);
        if (user == null) return;

        String storedPassword = (String) user.get("password");
        if (storedPassword != null && !storedPassword.isEmpty()) {
            ctx.getAuthmeService().registerToAuthme(username, storedPassword);
        }
    }

    private void unregisterFromAuthmeIfPossible(String username) {
        if (ctx.getAuthmeService() != null && ctx.getAuthmeService().isAuthmeEnabled()) {
            ctx.getAuthmeService().unregisterFromAuthme(username);
        }
    }

    private void sendReviewResult(String username, boolean approved, String reason) {
        var user = ctx.getUserByConfiguredUsername(username);
        if (user == null) return;

        String email = (String) user.get("email");
        if (email != null && !email.isEmpty()) {
            ctx.getMailService().sendReviewResult(email, username, approved, reason,
                    ctx.getConfigManager().getLanguage());
        }
    }

    private void addAudit(String action, String operator, String target, String detail) {
        ctx.getAuditDao().addAudit(new AuditRecord(action, operator, target, detail, System.currentTimeMillis()));
    }

    private void broadcast(String type, String username) {
        if (ctx.getWsServer() != null) {
            ctx.getWsServer().broadcastMessage(new JSONObject()
                    .put("type", type)
                    .put("username", username)
                    .toString());
        }
    }

    private void sendSuccess(HttpExchange exchange, String messageKey, String language) throws IOException {
        WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(ctx.getMessage(messageKey, language)));
    }

    private void sendFailure(HttpExchange exchange, String messageKey, String language) throws IOException {
        WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(ctx.getMessage(messageKey, language)));
    }

    public enum Action {
        LIST,
        APPROVE,
        REJECT,
        DELETE,
        BAN,
        UNBAN,
        PASSWORD
    }

    private record AdminRequest(String operator, String target, String language, JSONObject body) {
    }

    private static final class QueryParams {
        private final Map<String, String> values = new HashMap<>();

        private String get(String key) {
            return values.get(key);
        }

        private void put(String key, String value) {
            values.put(key, value);
        }
    }
}
