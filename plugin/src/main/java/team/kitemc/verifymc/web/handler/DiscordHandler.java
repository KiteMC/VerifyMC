package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.service.DiscordService;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles Discord linking, status, callback, and unlink endpoints.
 */
public class DiscordHandler implements HttpHandler {
    private final PluginContext ctx;
    private final Action action;

    public DiscordHandler(PluginContext ctx, Action action) {
        this.ctx = ctx;
        this.action = action;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (action) {
            case AUTH -> handleAuth(exchange);
            case CALLBACK -> handleCallback(exchange);
            case STATUS -> handleStatus(exchange);
            case UNLINK -> handleUnlink(exchange);
        }
    }

    private void handleAuth(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        QueryParams params = parseQuery(exchange);
        String username = params.get("username");
        String language = params.getOrDefault("language", "en");

        if (username == null || username.isBlank()) {
            sendMissingUsername(exchange, language);
            return;
        }

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("authUrl", ctx.getDiscordService().getAuthorizationUrl(username));
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        QueryParams params = parseQuery(exchange);
        String code = params.get("code");
        String state = params.get("state");
        String language = params.getOrDefault("language", "en");

        String html;
        if (code == null || state == null) {
            html = buildCallbackHtml(false, ctx.getMessage("discord.missing_code_state", language));
        } else {
            DiscordService.DiscordCallbackResult result = ctx.getDiscordService().handleCallback(code, state);
            boolean success = result.success;
            html = buildCallbackHtml(success,
                    success ? ctx.getMessage("discord.linked_successfully", language) : result.message);
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        QueryParams params = parseQuery(exchange);
        String username = params.get("username");
        String language = params.getOrDefault("language", "en");

        JSONObject resp = new JSONObject();
        if (username != null && !username.isBlank()) {
            boolean linked = ctx.getDiscordService().isLinked(username);
            resp.put("success", true);
            resp.put("linked", linked);
            if (linked) {
                var discordUser = ctx.getDiscordService().getLinkedUser(username);
                if (discordUser != null) {
                    resp.put("user", discordUser.toJson());
                }
            }
        } else {
            resp.put("success", false);
            resp.put("message", ctx.getMessage("error.missing_username", language));
        }
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void handleUnlink(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        String authenticatedUser = AdminAuthUtil.getAuthenticatedUser(exchange, ctx);
        if (authenticatedUser == null) return;

        JSONObject req = readJson(exchange);
        if (req == null) return;

        String targetUsername = req.optString("username", "");
        String language = req.optString("language", "en");

        if (targetUsername.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.missing_user_identifier", language)));
            return;
        }

        boolean isAdmin = ctx.getOpsManager().isOp(authenticatedUser);
        boolean isSelf = authenticatedUser.equalsIgnoreCase(targetUsername);

        if (!isSelf && !isAdmin) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.forbidden", language)), 403);
            return;
        }

        boolean ok = ctx.getDiscordService().unlinkUser(targetUsername);
        if (ok) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("discord.link_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("discord.link_failed", language)));
        }
    }

    private JSONObject readJson(HttpExchange exchange) throws IOException {
        try {
            return WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return null;
        }
    }

    private QueryParams parseQuery(HttpExchange exchange) {
        QueryParams params = new QueryParams();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return params;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private void sendMissingUsername(HttpExchange exchange, String language) throws IOException {
        JSONObject resp = new JSONObject()
                .put("success", false)
                .put("message", ctx.getMessage("error.missing_username", language));
        WebResponseHelper.sendJson(exchange, resp);
    }

    private String buildCallbackHtml(boolean success, String message) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Discord Link</title>
            <style>
                body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #36393f; color: #fff; }
                .container { text-align: center; padding: 40px; background: #2f3136; border-radius: 8px; }
                .success { color: #43b581; }
                .error { color: #f04747; }
            </style>
            </head>
            <body>
                <div class="container">
                    <h2 class="%s">%s</h2>
                    <p>%s</p>
                    <p>You can close this window now.</p>
                </div>
            </body>
            </html>
            """.formatted(
                success ? "success" : "error",
                success ? "✓ Success" : "✗ Error",
                message
            );
    }

    public enum Action {
        AUTH,
        CALLBACK,
        STATUS,
        UNLINK
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
