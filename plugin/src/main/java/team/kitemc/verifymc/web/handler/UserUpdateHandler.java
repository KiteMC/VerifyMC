package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class UserUpdateHandler implements HttpHandler {
    private final PluginContext ctx;

    public UserUpdateHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        String username = AdminAuthUtil.getAuthenticatedUser(exchange, ctx);
        if (username == null) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "en")), 400);
            return;
        }

        String language = req.optString("language", "en");
        String newEmail = req.optString("email", "").trim().toLowerCase();
        String code = req.optString("code", "").trim();

        if (newEmail.isBlank()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("register.invalid_email", language)));
            return;
        }

        if (!isValidEmail(newEmail)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("register.invalid_email", language)));
            return;
        }

        Map<String, Object> user = ctx.getUserDao().getUserByUsername(username);
        if (user == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.user_not_found", language)));
            return;
        }

        String currentEmail = (String) user.get("email");
        if (newEmail.equalsIgnoreCase(currentEmail)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("user.update_success", language)));
            return;
        }

        if (ctx.getConfigManager().isEmailDomainWhitelistEnabled()) {
            String domain = newEmail.contains("@") ? newEmail.substring(newEmail.indexOf('@') + 1) : "";
            List<String> whitelist = ctx.getConfigManager().getEmailDomainWhitelist();
            if (!whitelist.contains(domain)) {
                WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                        ctx.getMessage("register.domain_not_allowed", language)));
                return;
            }
        }

        int maxAccounts = ctx.getConfigManager().getMaxAccountsPerEmail();
        int emailCount = ctx.getUserDao().countUsersByEmail(newEmail);
        if (emailCount >= maxAccounts) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("register.email_limit", language)));
            return;
        }

        if (code.isEmpty() || !ctx.getVerifyCodeService().checkCode(newEmail, code)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("verify.wrong_code", language)));
            return;
        }

        boolean updated = ctx.getUserDao().updateUserEmail(username, newEmail);
        
        if (updated) {
            ctx.getAuditDao().addAudit(new AuditRecord(
                "email_update", username, username, 
                "Email updated to: " + newEmail, System.currentTimeMillis()
            ));
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.success(
                    ctx.getMessage("user.update_success", language)));
        } else {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("user.update_failed", language)));
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }
}
