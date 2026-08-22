package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;

/**
 * Verifies an admin token is still valid.
 * Extracted from WebServer.start() — the "/api/admin/verify" context.
 */
public class AdminVerifyHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminVerifyHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "zh")), 400);
            return;
        }
        String token = req.optString("token", "");
        String language = req.optString("language", "zh");

        if (ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("login.token_valid", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("login.token_invalid", language)));
        }
    }
}
