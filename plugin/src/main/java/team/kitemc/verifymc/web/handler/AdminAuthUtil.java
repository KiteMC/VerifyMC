package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;

/**
 * Shared authentication utility for admin handlers.
 * Extracts the Bearer token from the Authorization header and validates it.
 * Provides both token validation and admin permission checking.
 */
public final class AdminAuthUtil {
    private AdminAuthUtil() {}

    /**
     * Validates the Authorization header. Returns true if the token is valid.
     * Sends a 401 response and returns false if not.
     */
    public static boolean requireAuth(HttpExchange exchange, PluginContext ctx) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return false;
        }
        return true;
    }

    /**
     * Validates the Authorization header and checks if the user is an admin (OP).
     * This method performs the following checks:
     * 1. Validates the Bearer token from Authorization header
     * 2. Retrieves the username associated with the token
     * 3. Verifies the user has admin (OP) privileges
     *
     * @param exchange The HTTP exchange containing the request
     * @param ctx The plugin context for accessing services
     * @return The username of the authenticated admin, or null if validation fails
     * @throws IOException If an I/O error occurs while sending the response
     */
    public static String requireAdmin(HttpExchange exchange, PluginContext ctx) throws IOException {
        // Step 1: Extract and validate token
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // Check if token exists and is valid
        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }

        // Step 2: Get username from token
        String username = ctx.getWebAuthHelper().getUsername(token);
        if (username == null) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }

        // Step 3: Check if user is an admin (OP)
        if (!ctx.getOpsManager().isOp(username)) {
            // Get language from Accept-Language header
            String acceptLanguage = exchange.getRequestHeaders().getFirst("Accept-Language");
            String language = (acceptLanguage != null && acceptLanguage.startsWith("en")) ? "en" : "zh";

            // Record audit log for unauthorized admin access attempt
            ctx.getAuditDao().addAudit(new AuditRecord(
                    "admin_access_denied",
                    username,
                    exchange.getRequestURI().getPath(),
                    "Non-admin user attempted to access admin endpoint",
                    System.currentTimeMillis()
            ));

            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("admin.forbidden", language)), 403);
            return null;
        }

        return username;
    }

    /**
     * Extracts the username from a valid token without admin check.
     * Useful for endpoints that need the username but don't require admin privileges.
     *
     * @param exchange The HTTP exchange containing the request
     * @param ctx The plugin context for accessing services
     * @return The username if token is valid, null otherwise
     * @throws IOException If an I/O error occurs while sending the response
     */
    public static String getAuthenticatedUser(HttpExchange exchange, PluginContext ctx) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure("Unauthorized"), 401);
            return null;
        }

        return ctx.getWebAuthHelper().getUsername(token);
    }

    /**
     * Attempts to extract the username from a valid token without sending error response.
     * Useful for endpoints that provide different data based on authentication status.
     *
     * @param exchange The HTTP exchange containing the request
     * @param ctx The plugin context for accessing services
     * @return The username if token is valid, null otherwise (no error response sent)
     */
    public static String getAuthenticatedUserQuietly(HttpExchange exchange, PluginContext ctx) {
        try {
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (token == null || !ctx.getWebAuthHelper().isValidToken(token)) {
                return null;
            }

            return ctx.getWebAuthHelper().getUsername(token);
        } catch (Exception e) {
            return null;
        }
    }
}
