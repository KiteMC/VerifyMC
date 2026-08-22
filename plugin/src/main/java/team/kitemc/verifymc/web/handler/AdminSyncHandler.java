package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.logging.Level;

public class AdminSyncHandler implements HttpHandler {
    private final PluginContext ctx;

    public AdminSyncHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        // Require admin privileges
        if (AdminAuthUtil.requireAdmin(exchange, ctx) == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "zh")), 400);
            return;
        }
        String language = req.optString("language", "zh");

        if (ctx.getAuthmeService() == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("authme.service_unavailable", language)), 500);
            return;
        }

        if (!ctx.getAuthmeService().isAuthmeEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("authme.not_enabled", language)), 400);
            return;
        }

        try {
            ctx.getAuthmeService().syncApprovedUsers();
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("authme.sync_success", language)));
        } catch (Exception e) {
            ctx.getPlugin().getLogger().log(Level.WARNING, "AuthMe sync failed", e);
            String errorMsg = ctx.getMessage("authme.sync_failed", language).replace("{error}", e.getMessage());
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(errorMsg), 500);
        }
    }
}
