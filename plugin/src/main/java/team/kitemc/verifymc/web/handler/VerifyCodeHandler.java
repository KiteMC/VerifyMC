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
 * Sends an email verification code.
 * Extracted from WebServer.start() — the "/api/verify/send" context.
 */
public class VerifyCodeHandler implements HttpHandler {
    private final PluginContext ctx;

    public VerifyCodeHandler(PluginContext ctx) {
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
        String email = req.optString("email", "").trim().toLowerCase();
        String language = req.optString("language", "zh");

        if (email.isEmpty() || !email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.invalid_format", language)));
            return;
        }

        // Check email domain whitelist
        if (ctx.getConfigManager().isEmailDomainWhitelistEnabled()) {
            String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
            if (!ctx.getConfigManager().getEmailDomainWhitelist().contains(domain)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("register.domain_not_allowed", language)));
                return;
            }
        }

        // Check rate limit
        if (!ctx.getVerifyCodeService().canSendCode(email)) {
            long remainingSeconds = ctx.getVerifyCodeService().getRemainingCooldownSeconds(email);
            JSONObject response = ApiResponseFactory.failure(
                    ctx.getMessage("email.rate_limited", language)
                            .replace("{seconds}", String.valueOf(remainingSeconds)));
            response.put("remainingSeconds", remainingSeconds);
            WebResponseHelper.sendJson(exchange, response);
            return;
        }

        // Generate and send code
        String code = ctx.getVerifyCodeService().generateCode(email);
        boolean sent = ctx.getMailService().sendVerifyCodeWithDefaultSubject(email, code, language);

        if (sent) {
            // Get remaining cooldown seconds for next send
            long remainingSeconds = ctx.getVerifyCodeService().getRemainingCooldownSeconds(email);
            JSONObject response = ApiResponseFactory.success(ctx.getMessage("email.sent", language));
            response.put("remainingSeconds", remainingSeconds);
            WebResponseHelper.sendJson(exchange, response);
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("email.failed", language)));
        }
    }
}
