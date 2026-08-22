package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Returns the current plugin version and checks for updates.
 */
public class VersionHandler implements HttpHandler {
    private final PluginContext ctx;

    public VersionHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("currentVersion", ctx.getPlugin().getDescription().getVersion());

        if (ctx.getVersionCheckService() != null) {
            // The initial check runs asynchronously during plugin startup. Trigger
            // a retry when the first browser request arrives before it completes.
            if (ctx.getVersionCheckService().getLatestVersion() == null) {
                try {
                    ctx.getVersionCheckService().checkForUpdatesAsync().get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Return the current version even when GitHub is unavailable.
                }
            }
            JSONObject info = ctx.getVersionCheckService().getVersionInfo();
            if (info != null) {
                resp.put("latestVersion", info.optString("latestVersion", ""));
                resp.put("updateAvailable", info.optBoolean("updateAvailable", false));
                resp.put("releasesUrl", info.optString("releasesUrl", ""));
            }
        }

        WebResponseHelper.sendJson(exchange, resp);
    }
}
