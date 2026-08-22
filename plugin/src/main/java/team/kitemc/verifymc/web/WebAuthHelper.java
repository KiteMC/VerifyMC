package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import team.kitemc.verifymc.core.I18nManager;

public class WebAuthHelper {
    private static final long TOKEN_EXPIRY_TIME = 3600000;
    private final Plugin plugin;
    private final I18nManager i18nManager;
    private final ConcurrentHashMap<String, TokenInfo> validTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile Thread cleanupThread;

    private static class TokenInfo {
        final long expiryTime;
        final String username;

        TokenInfo(long expiryTime, String username) {
            this.expiryTime = expiryTime;
            this.username = username;
        }
    }

    public WebAuthHelper(Plugin plugin, I18nManager i18nManager) {
        this.plugin = plugin;
        this.i18nManager = Objects.requireNonNull(i18nManager, "i18nManager cannot be null");
    }

    public boolean isAuthenticated(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return validateToken(token);
    }

    public String generateSecureToken(String username) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(randomBytes);
            md.update(Long.toString(System.currentTimeMillis()).getBytes());
            byte[] hash = md.digest();
            String token = Base64.getEncoder().encodeToString(hash);
            validTokens.put(token, new TokenInfo(System.currentTimeMillis() + TOKEN_EXPIRY_TIME, username));
            return token;
        } catch (NoSuchAlgorithmException e) {
            String token = Base64.getEncoder().encodeToString(randomBytes);
            validTokens.put(token, new TokenInfo(System.currentTimeMillis() + TOKEN_EXPIRY_TIME, username));
            return token;
        }
    }

    public String generateSecureToken() {
        return generateSecureToken("system");
    }

    public String generateToken(String username) {
        return generateSecureToken(username);
    }

    public String generateToken() {
        return generateSecureToken("system");
    }

    public void startTokenCleanupTask() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(300000);
                    long currentTime = System.currentTimeMillis();
                    validTokens.entrySet().removeIf(entry -> entry.getValue().expiryTime < currentTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.setName("VerifyMC-TokenCleanup");
        t.start();
        this.cleanupThread = t;
    }

    public void stopTokenCleanupTask() {
        Thread t = this.cleanupThread;
        if (t != null) {
            t.interrupt();
        }
    }

    public boolean validateToken(String token) {
        TokenInfo tokenInfo = validTokens.get(token);
        if (tokenInfo == null) {
            return false;
        }
        if (System.currentTimeMillis() > tokenInfo.expiryTime) {
            validTokens.remove(token);
            return false;
        }
        return true;
    }

    public String getUsername(String token) {
        TokenInfo tokenInfo = validTokens.get(token);
        if (tokenInfo == null || System.currentTimeMillis() > tokenInfo.expiryTime) {
            return null;
        }
        return tokenInfo.username;
    }

    /**
     * Check if a token is valid (alias for validateToken)
     * @param token Token string to validate
     * @return true if token is valid
     */
    public boolean isValidToken(String token) {
        return validateToken(token);
    }

    /**
     * Authenticates an HTTP request by validating the Bearer token.
     * Sends appropriate 401 error responses if authentication fails.
     *
     * @param exchange The HTTP exchange containing the request headers
     * @return The authenticated username, or null if authentication failed
     * @throws IOException If an I/O error occurs while sending the response
     */
    public String authenticateRequest(HttpExchange exchange) throws IOException {
        String language = getAcceptLanguage(exchange);
        
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendAuthError(exchange, "auth.authentication_required", language);
            return null;
        }
        
        String token = authHeader.substring(7);
        TokenInfo tokenInfo = validTokens.get(token);
        
        if (tokenInfo == null || System.currentTimeMillis() > tokenInfo.expiryTime) {
            if (tokenInfo != null) {
                validTokens.remove(token);
            }
            sendAuthError(exchange, "auth.invalid_token", language);
            return null;
        }
        
        return tokenInfo.username;
    }

    private void sendAuthError(HttpExchange exchange, String messageKey, String language) throws IOException {
        JSONObject error = new JSONObject();
        error.put("success", false);
        error.put("message", i18nManager.getMessage(messageKey, language));
        WebResponseHelper.sendJson(exchange, error, 401);
    }

    private String getAcceptLanguage(HttpExchange exchange) {
        String acceptLanguage = exchange.getRequestHeaders().getFirst("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            String lang = acceptLanguage.split(",")[0].split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (lang.startsWith("zh")) {
                return "zh";
            }
            if (lang.startsWith("en")) {
                return "en";
            }
        }
        return "zh";
    }
}
