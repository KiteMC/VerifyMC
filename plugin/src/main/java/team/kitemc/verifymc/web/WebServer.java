package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.db.AuditDao;
import team.kitemc.verifymc.service.AuthmeService;
import team.kitemc.verifymc.service.CaptchaService;
import team.kitemc.verifymc.service.QuestionnaireService;
import team.kitemc.verifymc.service.DiscordService;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ResourceBundle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

public class WebServer {
    private HttpServer server;
    private final int port;
    private String staticDir;
    private final Plugin plugin;
    private final VerifyCodeService codeService;
    private final MailService mailService;
    private final UserDao userDao;
    private final AuditDao auditDao;
    private final AuthmeService authmeService;
    private final CaptchaService captchaService;
    private final QuestionnaireService questionnaireService;
    private final DiscordService discordService;
    private final ReviewWebSocketServer wsServer;
    private final ResourceBundle messages;
    private final boolean debug;
    private final boolean trustProxy;
    private final HashMap<String, ResourceBundle> languageCache = new HashMap<>();
    
    // Authentication related
    private final ConcurrentHashMap<String, Long> validTokens = new ConcurrentHashMap<>();
    private final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final long TOKEN_EXPIRY_TIME = 3600000; // 1 hour
    private static final long QUESTIONNAIRE_SUBMISSION_TTL_MS = 10 * 60 * 1000; // 10 minutes
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5 minutes
    // Questionnaire token lifecycle: keep records until successful registration consumes them,
    // otherwise let them expire naturally and be reclaimed by cleanupExpiredQuestionnaireSubmissions.
    private final ConcurrentHashMap<String, QuestionnaireSubmissionRecord> questionnaireSubmissionStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowRateLimitRecord> questionnaireRateLimitStore = new ConcurrentHashMap<>();
    private Thread cleanupThread;

    // Default mainstream email domain whitelist
    private static final java.util.List<String> DEFAULT_EMAIL_DOMAIN_WHITELIST = Arrays.asList(
        "gmail.com", "qq.com", "163.com", "126.com", "outlook.com", "hotmail.com", "yahoo.com",
        "sina.com", "aliyun.com", "foxmail.com", "icloud.com", "yeah.net", "live.com", "mail.com",
        "protonmail.com", "zoho.com"
    );

    public WebServer(int port, String staticDir, Plugin plugin, VerifyCodeService codeService, MailService mailService, UserDao userDao, AuditDao auditDao, AuthmeService authmeService, CaptchaService captchaService, QuestionnaireService questionnaireService, DiscordService discordService, ReviewWebSocketServer wsServer, ResourceBundle messages) {
        this.port = port;
        this.staticDir = staticDir;
        this.plugin = plugin;
        this.codeService = codeService;
        this.mailService = mailService;
        this.userDao = userDao;
        this.auditDao = auditDao;
        this.authmeService = authmeService;
        this.captchaService = captchaService;
        this.questionnaireService = questionnaireService;
        this.discordService = discordService;
        this.wsServer = wsServer;
        this.messages = messages;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.trustProxy = plugin.getConfig().contains("web.trust_proxy")
            ? plugin.getConfig().getBoolean("web.trust_proxy", false)
            : plugin.getConfig().getBoolean("security.trust_proxy", false);
    }


    private static class QuestionnaireSubmissionRecord {
        private final boolean passed;
        private final int score;
        private final int passScore;
        private final JSONArray details;
        private final boolean manualReviewRequired;
        private final JSONObject answers;
        private final long submittedAt;
        private final long expiresAt;

        private QuestionnaireSubmissionRecord(boolean passed, int score, int passScore, JSONArray details, boolean manualReviewRequired, JSONObject answers, long submittedAt, long expiresAt) {
            this.passed = passed;
            this.score = score;
            this.passScore = passScore;
            this.details = details;
            this.manualReviewRequired = manualReviewRequired;
            this.answers = answers;
            this.submittedAt = submittedAt;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }



    private static class WindowRateLimitRecord {
        private final int count;
        private final long windowStart;

        private WindowRateLimitRecord(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }

        private int count() {
            return count;
        }

        private long windowStart() {
            return windowStart;
        }
    }

    private static class RateLimitDecision {
        private final boolean allowed;
        private final long retryAfterMs;

        private RateLimitDecision(boolean allowed, long retryAfterMs) {
            this.allowed = allowed;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private RateLimitDecision checkQuestionnaireRateLimit(String key, int limit, long windowMs) {
        if (key == null || key.isBlank() || limit <= 0 || windowMs <= 0) {
            return new RateLimitDecision(true, 0L);
        }
        long now = System.currentTimeMillis();
        questionnaireRateLimitStore.computeIfPresent(key, (k, old) -> now - old.windowStart() >= windowMs ? null : old);
        WindowRateLimitRecord rec = questionnaireRateLimitStore.compute(key, (k, old) -> {
            if (old == null || now - old.windowStart() >= windowMs) {
                return new WindowRateLimitRecord(1, now);
            }
            return new WindowRateLimitRecord(old.count() + 1, old.windowStart());
        });
        if (rec.count() > limit) {
            long retryAfterMs = windowMs - (now - rec.windowStart());
            return new RateLimitDecision(false, Math.max(1L, retryAfterMs));
        }
        return new RateLimitDecision(true, 0L);
    }

    private String getClientIp(HttpExchange exchange) {
        if (trustProxy) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String firstForwardedIp = forwarded.split(",")[0].trim();
                if (!firstForwardedIp.isEmpty()) {
                    return firstForwardedIp;
                }
            }
        }
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "**@" + domain;
        }
        return local.substring(0, 1) + "***" + local.substring(local.length() - 1) + "@" + domain;
    }

    private String hashToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "hash_error";
        }
    }

    private void logQuestionnaireCall(String event, String ip, String uuid, String email, String requestId, JSONObject extra) {
        JSONObject log = new JSONObject();
        log.put("event", event);
        log.put("requestId", requestId);
        log.put("ip", ip);
        log.put("uuid", uuid != null ? uuid : "");
        log.put("email", maskEmail(email));
        if (extra != null) {
            log.put("extra", extra);
        }
        plugin.getLogger().info("[VerifyMC] questionnaire_call=" + log.toString());
    }

    private String buildQuestionnaireReviewSummary(JSONArray details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail == null) {
                continue;
            }
            String type = detail.optString("type", "");
            if (!"text".equalsIgnoreCase(type)) {
                continue;
            }
            int questionId = detail.optInt("question_id", -1);
            int score = detail.optInt("score", 0);
            int maxScore = detail.optInt("max_score", 0);
            String reason = detail.optString("reason", "").trim();
            if (reason.isEmpty()) {
                reason = "N/A";
            }
            parts.add("Q" + questionId + "(" + score + "/" + maxScore + "): " + reason);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" | ", parts);
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] " + msg);
    }

    /**
     * Authentication verification method
     * @param exchange HTTP exchange
     * @return true if authenticated
     */
    private boolean isAuthenticated(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return validateToken(token);
    }
    
    /**
     * Token validation
     * @param token Token to validate
     * @return true if token is valid
     */
    private boolean validateToken(String token) {
        Long expiryTime = validTokens.get(token);
        if (expiryTime == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiryTime) {
            validTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Read API key used by proxy-to-backend endpoints.
     * Priority: proxy.api_key -> security.api_key.
     */
    private String getConfiguredProxyApiKey() {
        String key = plugin.getConfig().getString("proxy.api_key", "");
        if (key == null || key.isBlank()) {
            key = plugin.getConfig().getString("security.api_key", "");
        }
        return key == null ? "" : key.trim();
    }

    /**
     * Unified API key validation for proxy-facing endpoints.
     *
     * Supported request formats:
     * - X-API-Key: <key>
     * - Authorization: Bearer <key>
     *
     * If API key is not configured in the main plugin, validation is treated as disabled.
     */
    private boolean isApiKeyValid(HttpExchange exchange) {
        String configuredApiKey = getConfiguredProxyApiKey();
        if (configuredApiKey.isBlank()) {
            return true;
        }

        String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (requestKey == null || requestKey.isBlank()) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                requestKey = authHeader.substring(7);
            }
        }

        if (requestKey == null || requestKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
            configuredApiKey.getBytes(StandardCharsets.UTF_8),
            requestKey.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * Generate secure token
     * @return Generated secure token
     */
    private String generateSecureToken() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String random = String.valueOf(Math.random());
            String secret = plugin.getConfig().getString("admin.password", "default_secret");
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String combined = timestamp + random + secret;
            byte[] hash = md.digest(combined.getBytes());
            String token = Base64.getEncoder().encodeToString(hash);
            
            // Store token and expiry time
            validTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_TIME);
            
            return token;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple token
            String token = "admin_token_" + System.currentTimeMillis() + "_" + Math.random();
            validTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_TIME);
            return token;
        }
    }
    
    /**
     * Input validation methods
     */
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    private boolean isValidUUID(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }
    
    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$");
        return username.matches(regex);
    }

    private String normalizeBedrockUsername(String username) {
        if (username == null) {
            return "";
        }
        String normalized = username.trim();
        String prefix = plugin.getConfig().getString("bedrock.prefix", ".");
        if (prefix == null || prefix.isEmpty()) {
            return normalized;
        }
        return normalized.startsWith(prefix) ? normalized : prefix + normalized;
    }

    private boolean isValidBedrockUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("bedrock.username_regex", "^\\.[a-zA-Z0-9_\\s]{3,16}$");
        return username.matches(regex);
    }
    private boolean isUsernameCaseConflict(String username) {
        return ((team.kitemc.verifymc.VerifyMC)plugin).isUsernameCaseConflict(username);
    }
    
    /**
     * Scheduled task to clean up expired tokens
     */
    private void startTokenCleanupTask() {
        if (cleanupThread != null && cleanupThread.isAlive()) {
            return;
        }
        cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                    long currentTime = System.currentTimeMillis();
                    validTokens.entrySet().removeIf(entry -> entry.getValue() < currentTime);
                    cleanupExpiredQuestionnaireSubmissions(currentTime);
                    cleanupStaleQuestionnaireRateLimits(currentTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "VerifyMC-Web-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void stopCleanupTask() {
        if (cleanupThread != null) {
            cleanupThread.interrupt();
            cleanupThread = null;
        }
    }

    private void cleanupExpiredQuestionnaireSubmissions(long currentTime) {
        // Records are retained for retry after non-questionnaire failures and removed here on natural expiry.
        questionnaireSubmissionStore.entrySet().removeIf(entry -> {
            QuestionnaireSubmissionRecord record = entry.getValue();
            return record == null || record.expiresAt <= currentTime || record.isExpired();
        });
    }

    private void cleanupStaleQuestionnaireRateLimits(long currentTime) {
        long windowMs = plugin.getConfig().getLong("questionnaire.rate_limit.window_ms", 300000L);
        if (windowMs <= 0) {
            questionnaireRateLimitStore.clear();
            return;
        }
        questionnaireRateLimitStore.entrySet().removeIf(entry -> {
            WindowRateLimitRecord record = entry.getValue();
            return record == null || currentTime - record.windowStart() >= windowMs;
        });
    }

    /**
     * Get email domain whitelist
     * @return List of whitelisted email domains
     */
    private java.util.List<String> getEmailDomainWhitelist() {
        java.util.List<String> list = null;
        try {
            list = plugin.getConfig().getStringList("email_domain_whitelist");
        } catch (Exception ignored) {}
        if (list == null || list.isEmpty()) {
            return DEFAULT_EMAIL_DOMAIN_WHITELIST;
        }
        return list;
    }

    /**
     * Check if email domain whitelist is enabled
     * @return true if email domain whitelist is enabled
     */
    private boolean isEmailDomainWhitelistEnabled() {
        try {
            return plugin.getConfig().getBoolean("enable_email_domain_whitelist", true);
        } catch (Exception ignored) {}
        return true;
    }
    /**
     * Check if email alias limit is enabled
     * @return true if email alias limit is enabled
     */
    private boolean isEmailAliasLimitEnabled() {
        try {
            return plugin.getConfig().getBoolean("enable_email_alias_limit", false);
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Utility method: Add copyright field to JSON response
     * @param obj JSON object to add copyright to
     * @return JSON object with copyright
     */
    private JSONObject withCopyright(JSONObject obj) {
        obj.put("copyright", "Powered by VerifyMC (GPLv3)");
        return obj;
    }


    private boolean isSupportedQuestionType(String type) {
        return "single_choice".equals(type) || "multiple_choice".equals(type) || "text".equals(type);
    }

    private void validateAnswer(JSONObject questionDef, String answerType, List<Integer> selectedOptionIds, String textAnswer, int questionId) {
        boolean required = questionDef.optBoolean("required", false);
        JSONObject input = questionDef.optJSONObject("input");
        int minSelections = input != null ? input.optInt("min_selections", 0) : 0;
        int maxSelections = input != null ? input.optInt("max_selections", Integer.MAX_VALUE) : Integer.MAX_VALUE;
        int minLength = input != null ? input.optInt("min_length", 0) : 0;
        int maxLength = input != null ? input.optInt("max_length", Integer.MAX_VALUE) : Integer.MAX_VALUE;

        if ("single_choice".equals(answerType) || "multiple_choice".equals(answerType)) {
            JSONArray options = questionDef.optJSONArray("options");
            int optionCount = options != null ? options.length() : 0;
            if (required && selectedOptionIds.isEmpty()) {
                throw new IllegalArgumentException("Question " + questionId + " is required");
            }
            if (selectedOptionIds.size() < minSelections || selectedOptionIds.size() > maxSelections) {
                throw new IllegalArgumentException("Invalid selection count for question: " + questionId);
            }
            for (Integer optionId : selectedOptionIds) {
                if (optionId == null || optionId < 0 || optionId >= optionCount) {
                    throw new IllegalArgumentException("Invalid option id for question: " + questionId);
                }
            }
        } else if ("text".equals(answerType)) {
            String normalized = textAnswer != null ? textAnswer.trim() : "";
            if (required && normalized.isEmpty()) {
                throw new IllegalArgumentException("Question " + questionId + " is required");
            }
            if (!normalized.isEmpty() && (normalized.length() < minLength || normalized.length() > maxLength)) {
                throw new IllegalArgumentException("Invalid text length for question: " + questionId);
            }
        } else {
            throw new IllegalArgumentException("Unsupported question type: " + answerType);
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Start token cleanup task
        startTokenCleanupTask();
        
        // Static resources
        server.createContext("/", new StaticHandler(staticDir));
        
        // API examples
        server.createContext("/api/ping", exchange -> {
            String resp = "{\"msg\":\"pong\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] data = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            OutputStream os = exchange.getResponseBody();
            os.write(data);
            os.close();
        });
        
        // /api/config configuration interface
        server.createContext("/api/config", exchange -> {
            JSONObject resp = new JSONObject();
            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
            // login configuration
            JSONObject login = new JSONObject();
            login.put("enable_email", config.getStringList("auth_methods").contains("email"));
            login.put("email_smtp", config.getString("smtp.host", ""));
            // admin configuration
            JSONObject admin = new JSONObject();
            // frontend configuration
            JSONObject frontend = new JSONObject();
            frontend.put("theme", config.getString("frontend.theme", "default"));
            frontend.put("logo_url", config.getString("frontend.logo_url", ""));
            frontend.put("announcement", config.getString("frontend.announcement", ""));
            frontend.put("web_server_prefix", config.getString("web_server_prefix", "[VerifyMC]"));
            frontend.put("current_theme", config.getString("frontend.theme", "default"));
            // authme configuration
            JSONObject authme = new JSONObject();
            authme.put("enabled", config.getBoolean("authme.enabled", false));
            authme.put("require_password", config.getBoolean("authme.require_password", false));
            authme.put("auto_register", config.getBoolean("authme.auto_register", false));
            authme.put("auto_unregister", config.getBoolean("authme.auto_unregister", false));
            authme.put("password_regex", config.getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$"));
            // Username regex pattern
            frontend.put("username_regex", config.getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$"));
            
            // Captcha configuration
            JSONObject captcha = new JSONObject();
            java.util.List<String> authMethods = config.getStringList("auth_methods");
            debugLog("auth_methods from config: " + authMethods);
            debugLog("captcha enabled: " + authMethods.contains("captcha"));
            captcha.put("enabled", authMethods.contains("captcha"));
            captcha.put("email_enabled", authMethods.contains("email"));
            captcha.put("type", config.getString("captcha.type", "math"));
            
            // Bedrock player configuration
            JSONObject bedrock = new JSONObject();
            bedrock.put("enabled", config.getBoolean("bedrock.enabled", false));
            bedrock.put("prefix", config.getString("bedrock.prefix", "."));
            bedrock.put("username_regex", config.getString("bedrock.username_regex", "^\\.[a-zA-Z0-9_\\s]{3,16}$"));
            
            // Questionnaire configuration
            JSONObject questionnaire = new JSONObject();
            questionnaire.put("enabled", questionnaireService.isEnabled());
            questionnaire.put("pass_score", questionnaireService.getPassScore());
            questionnaire.put("auto_approve_on_pass", questionnaireService.isAutoApproveOnPass());
            questionnaire.put("require_pass_before_register", config.getBoolean("questionnaire.require_pass_before_register", false));
            
            // Discord configuration
            JSONObject discord = new JSONObject();
            discord.put("enabled", discordService.isEnabled());
            discord.put("required", discordService.isRequired());
            
            resp.put("login", login);
            resp.put("admin", admin);
            resp.put("frontend", frontend);
            resp.put("authme", authme);
            resp.put("captcha", captcha);
            resp.put("bedrock", bedrock);
            resp.put("questionnaire", questionnaire);
            resp.put("discord", discord);
            sendJson(exchange, resp);
        });
        
        // /api/check-whitelist - Check if a player is on the whitelist (for proxy plugins)
        server.createContext("/api/check-whitelist", exchange -> {
            debugLog("/api/check-whitelist called");
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }

            if (!isApiKeyValid(exchange)) {
                debugLog("/api/check-whitelist unauthorized request");
                JSONObject unauthorized = new JSONObject();
                unauthorized.put("success", false);
                unauthorized.put("msg", "Invalid API key");
                byte[] data = unauthorized.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(401, data.length);
                exchange.getResponseBody().write(data);
                exchange.close();
                return;
            }
            
            String query = exchange.getRequestURI().getQuery();
            String username = null;
            if (query != null && query.contains("username=")) {
                username = query.split("username=")[1].split("&")[0];
                try {
                    username = java.net.URLDecoder.decode(username, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    // Keep original value
                }
            }
            
            JSONObject resp = new JSONObject();
            
            if (username == null || username.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("msg", "Username parameter is required");
                sendJson(exchange, resp);
                return;
            }
            
            // Look up user in database
            java.util.Map<String, Object> user = userDao.getUserByUsername(username);
            
            if (user != null) {
                resp.put("success", true);
                resp.put("found", true);
                resp.put("username", user.get("username"));
                resp.put("status", user.get("status"));
                resp.put("email", user.get("email"));
                debugLog("Whitelist check for " + username + ": found, status=" + user.get("status"));
            } else {
                resp.put("success", true);
                resp.put("found", false);
                resp.put("status", "not_registered");
                debugLog("Whitelist check for " + username + ": not found");
            }
            
            sendJson(exchange, resp);
        });
        
        // /api/discord/auth - Generate Discord OAuth2 authorization URL
        server.createContext("/api/discord/auth", exchange -> {
            debugLog("/api/discord/auth called");
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String username = req.optString("username", "");
            
            JSONObject resp = new JSONObject();
            
            if (!discordService.isEnabled()) {
                resp.put("success", false);
                resp.put("msg", "Discord integration is not enabled");
                sendJson(exchange, resp);
                return;
            }
            
            if (username.isEmpty()) {
                resp.put("success", false);
                resp.put("msg", "Username is required");
                sendJson(exchange, resp);
                return;
            }
            
            String authUrl = discordService.generateAuthUrl(username);
            if (authUrl != null) {
                resp.put("success", true);
                resp.put("auth_url", authUrl);
            } else {
                resp.put("success", false);
                resp.put("msg", "Failed to generate auth URL");
            }
            
            sendJson(exchange, resp);
        });
        
        // /api/discord/callback - Handle Discord OAuth2 callback
        server.createContext("/api/discord/callback", exchange -> {
            debugLog("/api/discord/callback called");
            
            String query = exchange.getRequestURI().getQuery();
            String code = null;
            String state = null;
            
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2) {
                        if ("code".equals(keyValue[0])) {
                            code = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        } else if ("state".equals(keyValue[0])) {
                            state = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            
            // Check Accept header to determine response format
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            boolean wantsHtml = accept == null || accept.contains("text/html");
            
            if (code == null || state == null) {
                if (wantsHtml) {
                    sendDiscordCallbackHtml(exchange, false, "Missing code or state parameter", null);
                } else {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    resp.put("msg", "Missing code or state parameter");
                    sendJson(exchange, resp);
                }
                return;
            }
            
            DiscordService.DiscordCallbackResult result = discordService.handleCallback(code, state);
            
            if (wantsHtml) {
                String discordUsername = result.user != null ? 
                    (result.user.globalName != null ? result.user.globalName : result.user.username) : null;
                sendDiscordCallbackHtml(exchange, result.success, result.message, discordUsername);
            } else {
                sendJson(exchange, result.toJson());
            }
        });
        
        // /api/discord/status - Check if user has linked Discord
        server.createContext("/api/discord/status", exchange -> {
            debugLog("/api/discord/status called");
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            String query = exchange.getRequestURI().getQuery();
            String username = null;
            if (query != null && query.contains("username=")) {
                username = query.split("username=")[1].split("&")[0];
                try {
                    username = java.net.URLDecoder.decode(username, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    // Keep original value
                }
            }
            
            JSONObject resp = new JSONObject();
            
            if (username == null || username.isEmpty()) {
                resp.put("success", false);
                resp.put("msg", "Username is required");
                sendJson(exchange, resp);
                return;
            }
            
            resp.put("success", true);
            resp.put("linked", discordService.isLinked(username));
            
            if (discordService.isLinked(username)) {
                DiscordService.DiscordUser user = discordService.getLinkedUser(username);
                if (user != null) {
                    resp.put("user", user.toJson());
                }
            }
            
            sendJson(exchange, resp);
        });
        
        // /api/reload-config reload configuration interface - requires authentication
        server.createContext("/api/reload-config", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                plugin.reloadConfig();
                // Update static file directory to support theme switching
                String theme = plugin.getConfig().getString("frontend.theme", "default");
                
                // Use ResourceManager to check theme
                team.kitemc.verifymc.ResourceManager resourceManager = new team.kitemc.verifymc.ResourceManager((org.bukkit.plugin.java.JavaPlugin) plugin);
                if (!resourceManager.themeExists(theme)) {
                    resp.put("success", false);
                    resp.put("message", "Theme not found: " + theme + ". Available themes: default, glassx");
                    sendJson(exchange, resp);
                    return;
                }
                
                String newStaticDir = resourceManager.getThemeStaticDir(theme);
                
                // Update static file directory
                debugLog("Switching theme from " + this.staticDir + " to " + newStaticDir);
                this.staticDir = newStaticDir;
                
                // Recreate static file handler
                server.removeContext("/");
                server.createContext("/", new StaticHandler(staticDir));
                debugLog("Static handler updated for theme: " + theme);
                
                resp.put("success", true);
                resp.put("message", "Configuration reloaded successfully. Theme switched to: " + theme);
            } catch (Exception e) {
                resp.put("success", false);
                resp.put("message", "Failed to reload configuration: " + e.getMessage());
            }
            sendJson(exchange, resp);
        });
        
        // /api/captcha - Generate captcha image for verification
        server.createContext("/api/captcha", exchange -> {
            debugLog("/api/captcha called");
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject resp = new JSONObject();
            try {
                CaptchaService.CaptchaResult result = captchaService.generateCaptcha();
                resp.put("success", true);
                resp.put("token", result.getToken());
                resp.put("image", result.getImageBase64());
            } catch (Exception e) {
                debugLog("Captcha generation failed: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", "Failed to generate captcha");
            }
            sendJson(exchange, resp);
        });
        
        // /api/questionnaire - Get questionnaire questions
        server.createContext("/api/questionnaire", exchange -> {
            debugLog("/api/questionnaire called");
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            String query = exchange.getRequestURI().getQuery();
            String language = "en";
            if (query != null && query.contains("language=")) {
                language = query.split("language=")[1].split("&")[0];
            }
            
            JSONObject resp = new JSONObject();
            try {
                JSONObject questionnaire = questionnaireService.getQuestionnaire(language);
                resp.put("success", true);
                resp.put("data", questionnaire);
            } catch (Exception e) {
                debugLog("Failed to get questionnaire: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", "Failed to get questionnaire");
            }
            sendJson(exchange, resp);
        });
        
        // /api/submit-questionnaire - Submit questionnaire answers
        server.createContext("/api/submit-questionnaire", exchange -> {
            debugLog("/api/submit-questionnaire called");
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String language = req.optString("language", "en");
            String requestId = UUID.randomUUID().toString();
            String clientIp = getClientIp(exchange);
            String requestUuid = req.optString("uuid", "").trim().toLowerCase();
            String requestEmail = req.optString("email", "").trim().toLowerCase();

            int ipLimit = plugin.getConfig().getInt("questionnaire.rate_limit.ip.max", 20);
            int uuidLimit = plugin.getConfig().getInt("questionnaire.rate_limit.uuid.max", 8);
            int emailLimit = plugin.getConfig().getInt("questionnaire.rate_limit.email.max", 6);
            long windowMs = plugin.getConfig().getLong("questionnaire.rate_limit.window_ms", 300000L);

            RateLimitDecision ipDecision = checkQuestionnaireRateLimit("q:ip:" + clientIp, ipLimit, windowMs);
            RateLimitDecision uuidDecision = checkQuestionnaireRateLimit("q:uuid:" + requestUuid, uuidLimit, windowMs);
            RateLimitDecision emailDecision = checkQuestionnaireRateLimit("q:email:" + requestEmail, emailLimit, windowMs);

            if (!ipDecision.allowed || !uuidDecision.allowed || !emailDecision.allowed) {
                long retryAfterMs = Math.max(ipDecision.retryAfterMs, Math.max(uuidDecision.retryAfterMs, emailDecision.retryAfterMs));
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", "Too many questionnaire submissions, please retry later.");
                resp.put("retry_after_ms", retryAfterMs);
                JSONObject extra = new JSONObject();
                extra.put("retryAfterMs", retryAfterMs);
                logQuestionnaireCall("rate_limited", clientIp, requestUuid, requestEmail, requestId, extra);
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // Parse answers from request
                JSONObject answersJson = req.optJSONObject("answers");
                if (answersJson == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("questionnaire.answers_required", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                JSONObject questionnaire = questionnaireService.getQuestionnaire(language);
                JSONArray questionDefs = questionnaire.optJSONArray("questions");
                if (questionDefs == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("questionnaire.answers_required", language));
                    sendJson(exchange, resp);
                    return;
                }

                Map<Integer, JSONObject> questionDefMap = new HashMap<>();
                for (int i = 0; i < questionDefs.length(); i++) {
                    JSONObject questionDef = questionDefs.optJSONObject(i);
                    if (questionDef != null) {
                        questionDefMap.put(questionDef.optInt("id", -1), questionDef);
                    }
                }

                // Convert answers to Map<Integer, AnswerObject>
                Map<Integer, QuestionnaireService.QuestionAnswer> answers = new HashMap<>();
                for (String key : answersJson.keySet()) {
                    int questionId = Integer.parseInt(key);
                    JSONObject questionDef = questionDefMap.get(questionId);
                    if (questionDef == null) {
                        throw new IllegalArgumentException("Invalid question id: " + questionId);
                    }

                    Object rawAnswer = answersJson.get(key);
                    if (!(rawAnswer instanceof JSONObject)) {
                        throw new IllegalArgumentException("Invalid answer object for question: " + questionId);
                    }
                    JSONObject answerObj = (JSONObject) rawAnswer;

                    String answerType = answerObj.optString("type", "").trim();
                    String questionType = questionDef.optString("type", "single_choice");
                    if (answerType.isEmpty()) {
                        answerType = questionType;
                    }
                    if (!answerType.equals(questionType)) {
                        throw new IllegalArgumentException("Illegal answer type for question: " + questionId);
                    }
                    if (!isSupportedQuestionType(answerType)) {
                        throw new IllegalArgumentException("Unsupported question type: " + answerType);
                    }

                    JSONArray selectedArray = answerObj.optJSONArray("selectedOptionIds");
                    List<Integer> selectedOptionIds = new java.util.ArrayList<>();
                    if (selectedArray != null) {
                        for (int i = 0; i < selectedArray.length(); i++) {
                            selectedOptionIds.add(selectedArray.getInt(i));
                        }
                    }

                    String textAnswer = answerObj.optString("textAnswer", "");
                    validateAnswer(questionDef, answerType, selectedOptionIds, textAnswer, questionId);
                    answers.put(questionId, new QuestionnaireService.QuestionAnswer(answerType, selectedOptionIds, textAnswer));
                }

                // Evaluate answers
                QuestionnaireService.QuestionnaireResult result = questionnaireService.evaluateAnswers(answers);
                JSONObject resultJson = result.toJson();
                JSONArray details = resultJson.optJSONArray("details");
                boolean manualReviewRequired = resultJson.optBoolean("manual_review_required", false);

                long submittedAt = System.currentTimeMillis();
                long expiresAt = submittedAt + QUESTIONNAIRE_SUBMISSION_TTL_MS;
                String questionnaireToken = UUID.randomUUID().toString();
                questionnaireSubmissionStore.put(questionnaireToken, new QuestionnaireSubmissionRecord(
                    result.isPassed(),
                    result.getScore(),
                    result.getPassScore(),
                    details,
                    manualReviewRequired,
                    answersJson,
                    submittedAt,
                    expiresAt
                ));

                resp.put("success", true);
                resp.put("passed", result.isPassed());
                resp.put("score", result.getScore());
                resp.put("pass_score", result.getPassScore());
                resp.put("details", details);
                resp.put("manual_review_required", manualReviewRequired);
                resp.put("token", questionnaireToken);
                resp.put("submitted_at", submittedAt);
                resp.put("expires_at", expiresAt);
                resp.put("msg", result.isPassed() ? 
                    getMsg("questionnaire.passed", language).replace("{score}", String.valueOf(result.getScore())) : 
                    getMsg("questionnaire.failed", language).replace("{score}", String.valueOf(result.getScore())).replace("{pass_score}", String.valueOf(result.getPassScore())));

                JSONObject extra = new JSONObject();
                extra.put("passed", result.isPassed());
                extra.put("score", result.getScore());
                extra.put("manualReview", manualReviewRequired);
                extra.put("questionCount", answers.size());
                logQuestionnaireCall("submitted", clientIp, requestUuid, requestEmail, requestId, extra);
                
            } catch (Exception e) {
                debugLog("Failed to submit questionnaire requestId=" + requestId + ": " + e.getMessage());
                JSONObject extra = new JSONObject();
                extra.put("error", e.getClass().getSimpleName());
                logQuestionnaireCall("submit_failed", clientIp, requestUuid, requestEmail, requestId, extra);
                resp.put("success", false);
                resp.put("msg", "Failed to submit questionnaire: " + e.getMessage());
            }
            sendJson(exchange, resp);
        });
        

        // /api/send_code send verification code interface with rate limiting and authentication
        server.createContext("/api/send_code", exchange -> {
            debugLog("/api/send_code called");
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String email = req.optString("email", "").trim().toLowerCase();
            String language = req.optString("language", "en");

            // Basic input validation - Email format check
            if (!isValidEmail(email)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("email.invalid_format", language));
                sendJson(exchange, resp);
                return;
            }

            // Rate limiting check - Prevent spam and abuse
            if (!codeService.canSendCode(email)) {
                long remainingSeconds = codeService.getRemainingCooldownSeconds(email);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("email.rate_limited", language).replace("{seconds}", String.valueOf(remainingSeconds)));
                resp.put("remaining_seconds", remainingSeconds);
                debugLog("Rate limit exceeded for email: " + email + ", remaining: " + remainingSeconds + "s");
                sendJson(exchange, resp);
                return;
            }

            // Email alias restriction check
            if (isEmailAliasLimitEnabled() && email.contains("+")) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.alias_not_allowed", language));
                sendJson(exchange, resp);
                return;
            }

            // Email domain whitelist check
            if (isEmailDomainWhitelistEnabled()) {
                String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
                java.util.List<String> allowedDomains = getEmailDomainWhitelist();
                if (!allowedDomains.contains(domain)) {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    resp.put("msg", getMsg("register.domain_not_allowed", language));
                    sendJson(exchange, resp);
                    return;
                }
            }
            
            // Generate verification code and send email
            String code = codeService.generateCode(email);
            debugLog("Generated verification code for email: " + maskEmail(email) + ", codeHash=" + hashToken(code));
            
            // Get email subject from config.yml, fallback to default if not set
            String emailSubject = plugin.getConfig().getString("email_subject", "VerifyMC Verification Code");
            boolean sent = mailService.sendCode(email, emailSubject, code, language);
            JSONObject resp = new JSONObject();
            resp.put("success", sent);
            resp.put("msg", sent ? getMsg("email.sent", language) : getMsg("email.failed", language));
            
            if (sent) {
                debugLog("Verification code sent successfully to: " + email);
            } else {
                debugLog("Failed to send verification code to: " + email);
            }
            
            sendJson(exchange, resp);
        });
        
        // /api/register registration interface
        server.createContext("/api/register", exchange -> {
            debugLog("/api/register called");
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, 0); exchange.close(); return; }
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String email = req.optString("email", "").trim().toLowerCase();
            String code = req.optString("code");
            String uuid = req.optString("uuid");
            String username = req.optString("username");
            String platform = req.optString("platform", "java").trim().toLowerCase();
            String password = req.optString("password", ""); // New password parameter
            String captchaToken = req.optString("captchaToken", "");
            String captchaAnswer = req.optString("captchaAnswer", "");
            String language = req.optString("language", "en");
            JSONObject questionnaire = req.optJSONObject("questionnaire");
            debugLog("register params: email=" + maskEmail(email) + ", codeHash=" + hashToken(code) + ", uuid=" + uuid + ", username=" + username + ", hasPassword=" + !password.isEmpty() + ", hasCaptcha=" + !captchaToken.isEmpty());

            // Check if password is required
            if (authmeService.isAuthmeEnabled() && authmeService.isPasswordRequired()) {
                if (password == null || password.trim().isEmpty()) {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    resp.put("msg", getMsg("register.password_required", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                // Validate password format
                if (!authmeService.isValidPassword(password)) {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    String passwordRegex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$");
                    resp.put("msg", getMsg("register.invalid_password", language).replace("{regex}", passwordRegex));
                    sendJson(exchange, resp);
                    return;
                }
            }

            // Email alias restriction
            if (isEmailAliasLimitEnabled() && email.contains("+")) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.alias_not_allowed", language));
                sendJson(exchange, resp);
                return;
            }

            // Email domain whitelist
            if (isEmailDomainWhitelistEnabled()) {
                String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
                java.util.List<String> allowedDomains = getEmailDomainWhitelist();
                if (!allowedDomains.contains(domain)) {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    resp.put("msg", getMsg("register.domain_not_allowed", language));
                    sendJson(exchange, resp);
                    return;
                }
            }
            boolean bedrockEnabled = plugin.getConfig().getBoolean("bedrock.enabled", false);
            boolean useBedrockUsernameRule = bedrockEnabled && "bedrock".equals(platform);
            if (useBedrockUsernameRule) {
                username = normalizeBedrockUsername(username);
            } else if (username != null) {
                username = username.trim();
            }

            // Username uniqueness check
            if (userDao.getUserByUsername(username) != null) {
                debugLog("Username already exists: " + username);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", "Username already exists");
                sendJson(exchange, resp);
                return;
            }
            // Pre-registration username rule validation and case conflict check
            if (useBedrockUsernameRule ? !isValidBedrockUsername(username) : !isValidUsername(username)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                String usernameRegex = useBedrockUsernameRule
                    ? plugin.getConfig().getString("bedrock.username_regex", "^\\.[a-zA-Z0-9_\\s]{3,16}$")
                    : plugin.getConfig().getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$");
                resp.put("msg", getMsg("username.invalid", language).replace("{regex}", usernameRegex));
                sendJson(exchange, resp);
                return;
            }
            if (isUsernameCaseConflict(username)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("username.case_conflict", language));
                sendJson(exchange, resp);
                return;
            }
            // Email registration count limit
            int maxAccounts = plugin.getConfig().getInt("max_accounts_per_email", 2);
            int emailCount = userDao.countUsersByEmail(email);
            if (emailCount >= maxAccounts) {
                debugLog("Email registration limit reached: " + email + ", count=" + emailCount);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.email_limit", language));
                sendJson(exchange, resp);
                return;
            }
            // Input validation
            if (!isValidEmail(email)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.invalid_email", language));
                sendJson(exchange, resp);
                return;
            }
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.invalid_uuid", language));
                sendJson(exchange, resp);
                return;
            }
            if (username == null || username.trim().isEmpty()) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.invalid_username", language));
                sendJson(exchange, resp);
                return;
            }

            QuestionnaireSubmissionRecord questionnaireSubmissionRecord = null;
            String questionnaireSubmissionToken = null;
            boolean questionnaireEnabled = questionnaireService.isEnabled();
            boolean requireQuestionnairePass = plugin.getConfig().getBoolean("questionnaire.require_pass_before_register", false) && questionnaireEnabled;
            boolean hasQuestionnairePayload = questionnaireEnabled && questionnaire != null && questionnaire.length() > 0;
            if (requireQuestionnairePass || hasQuestionnairePayload) {
                JSONObject questionnaireResp = new JSONObject();
                if (questionnaire == null) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_required", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                String questionnaireToken = questionnaire.optString("token", "");
                long submittedAt = questionnaire.optLong("submitted_at", 0L);
                long expiresAt = questionnaire.optLong("expires_at", 0L);
                JSONObject answers = questionnaire.optJSONObject("answers");

                if (questionnaireToken.isEmpty() || answers == null) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_required", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                cleanupExpiredQuestionnaireSubmissions(System.currentTimeMillis());
                QuestionnaireSubmissionRecord record = questionnaireSubmissionStore.get(questionnaireToken);
                if (record == null) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_missing", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                if (requireQuestionnairePass && !record.passed && !record.manualReviewRequired) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_required", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                if (record.isExpired() || System.currentTimeMillis() > expiresAt || submittedAt <= 0 || expiresAt <= submittedAt) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_expired", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                if (!record.answers.similar(answers) || record.submittedAt != submittedAt || record.expiresAt != expiresAt) {
                    questionnaireResp.put("success", false);
                    questionnaireResp.put("msg", getMsg("register.questionnaire_invalid", language));
                    sendJson(exchange, questionnaireResp);
                    return;
                }

                questionnaireSubmissionRecord = record;
                questionnaireSubmissionToken = questionnaireToken;
            }

            JSONObject resp = new JSONObject();
            
            // Determine verification method based on auth_methods config
            java.util.List<String> authMethods = plugin.getConfig().getStringList("auth_methods");
            boolean useCaptcha = authMethods.contains("captcha");
            boolean useEmail = authMethods.contains("email");
            boolean verificationPassed = false;
            
            // Captcha verification
            if (useCaptcha) {
                if (captchaToken.isEmpty() || captchaAnswer.isEmpty()) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("captcha.required", language));
                    sendJson(exchange, resp);
                    return;
                }
                if (!captchaService.validateCaptcha(captchaToken, captchaAnswer)) {
                    debugLog("Captcha validation failed: token=" + captchaToken);
                    resp.put("success", false);
                    resp.put("msg", getMsg("captcha.invalid", language));
                    sendJson(exchange, resp);
                    return;
                }
                debugLog("Captcha validation passed");
                verificationPassed = true;
            }
            
            // Email verification (if email method is enabled or no captcha)
            if (useEmail || !useCaptcha) {
            if (!codeService.checkCode(email, code)) {
                debugLog("Verification code check failed: email=" + maskEmail(email) + ", codeHash=" + hashToken(code));
                plugin.getLogger().warning("[VerifyMC] Registration failed: wrong code, email=" + maskEmail(email) + ", codeHash=" + hashToken(code));
                resp.put("success", false); 
                resp.put("msg", getMsg("verify.wrong_code", language));
                    sendJson(exchange, resp);
                    return;
                }
                debugLog("Email verification code passed");
                verificationPassed = true;
            }
            
            if (verificationPassed) {
                debugLog("Verification passed, checking Discord requirement");
                
                // Check Discord binding if required
                if (discordService.isRequired()) {
                    if (!discordService.isLinked(username)) {
                        debugLog("Discord binding required but user not linked: " + username);
                        resp.put("success", false);
                        resp.put("msg", getMsg("discord.required", language));
                        resp.put("discord_required", true);
                        sendJson(exchange, resp);
                        return;
                    }
                    debugLog("Discord binding verified for: " + username);
                }
                
                debugLog("All checks passed, registering user");
                QuestionnaireSubmissionRecord submissionRecord = questionnaireSubmissionRecord;
                boolean questionnairePassed = submissionRecord != null && submissionRecord.passed;
                boolean manualReviewRequired = submissionRecord != null && submissionRecord.manualReviewRequired;
                boolean questionnaireAutoApprove = questionnairePassed && questionnaireService.isEnabled() && questionnaireService.isAutoApproveOnPass();
                boolean registerAutoApprove = plugin.getConfig().getBoolean("register.auto_approve", false);
                boolean autoApprove = !manualReviewRequired && (questionnaireAutoApprove || registerAutoApprove);
                String status = autoApprove ? "approved" : "pending";

                Integer questionnaireScore = submissionRecord != null ? submissionRecord.score : null;
                Boolean questionnairePassedValue = submissionRecord != null ? submissionRecord.passed : null;
                String questionnaireReviewSummary = submissionRecord != null ? buildQuestionnaireReviewSummary(submissionRecord.details) : null;
                Long questionnaireScoredAt = submissionRecord != null ? submissionRecord.submittedAt : null;
                boolean ok;

                // Choose registration method based on whether password is provided
                if (password != null && !password.trim().isEmpty()) {
                    ok = userDao.registerUser(uuid, username, email, status, password, questionnaireScore, questionnairePassedValue, questionnaireReviewSummary, questionnaireScoredAt);
                } else {
                    ok = userDao.registerUser(uuid, username, email, status, questionnaireScore, questionnairePassedValue, questionnaireReviewSummary, questionnaireScoredAt);
                }
                
                debugLog("registerUser result: " + ok);
                if (ok) {
                    if (submissionRecord != null && questionnaireSubmissionToken != null) {
                        boolean questionnaireConsumed = questionnaireSubmissionStore.remove(questionnaireSubmissionToken, submissionRecord);
                        if (!questionnaireConsumed) {
                            plugin.getLogger().warning("[VerifyMC] Registration duplicated or replayed: questionnaire token already consumed, tokenHash=" + hashToken(questionnaireSubmissionToken));
                            resp.put("success", false);
                            resp.put("msg", getMsg("register.questionnaire_already_used", language));
                            sendJson(exchange, resp);
                            return;
                        }
                    }

                    // Registration successful, automatically add to whitelist
                    debugLog("Execute: whitelist add " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + username);
                    });
                    
                    // If Authme integration is enabled and auto registration is enabled, register to Authme
                    if (authmeService.isAuthmeEnabled() && authmeService.isAutoRegisterEnabled() && 
                        password != null && !password.trim().isEmpty()) {
                        authmeService.registerToAuthme(username, password);
                    }
                }
                if (!ok) {
                    plugin.getLogger().warning("[VerifyMC] Registration failed: userDao.registerUser returned false, uuid=" + uuid + ", username=" + username + ", email=" + email);
                }
                resp.put("success", ok);
                if (ok) {
                    if (questionnaireAutoApprove) {
                        resp.put("msg", getMsg("register.questionnaire_auto_approved", language));
                    } else if (questionnairePassed) {
                        resp.put("msg", getMsg("register.questionnaire_pending_review", language));
                    } else {
                        resp.put("msg", getMsg("register.success", language));
                    }
                } else {
                    resp.put("msg", getMsg("register.failed", language));
                }
            }
            sendJson(exchange, resp);
        });
        
        // Admin login
        server.createContext("/api/admin-login", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String password = req.optString("password");
            String language = req.optString("language", "en");
            
            String adminPassword = plugin.getConfig().getString("admin.password", "");
            JSONObject resp = new JSONObject();
            
            if (password.equals(adminPassword)) {
                String token = generateSecureToken();
                resp.put("success", true);
                resp.put("token", token);
                resp.put("message", getMsg("admin.login_success", language));
            } else {
                resp.put("success", false);
                resp.put("message", getMsg("admin.login_failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Admin token verification
        server.createContext("/api/admin-verify", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject resp = new JSONObject();
            
            if (isAuthenticated(exchange)) {
                resp.put("success", true);
                resp.put("message", "Token is valid");
            } else {
                resp.put("success", false);
                resp.put("message", "Invalid or expired token");
            }
            
            sendJson(exchange, resp);
        });
        
        // Get pending users list - requires authentication
        server.createContext("/api/pending-list", exchange -> {
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            String query = exchange.getRequestURI().getQuery();
            String language = "en";
            if (query != null && query.contains("language=")) {
                language = query.split("language=")[1].split("&")[0];
            }
            JSONObject resp = new JSONObject();
            try {
                // Get pending users list from database
                List<Map<String, Object>> users = userDao.getPendingUsers();
                // Ensure each user contains email and regTime fields
                for (Map<String, Object> user : users) {
                    if (!user.containsKey("regTime")) user.put("regTime", null);
                    if (!user.containsKey("email")) user.put("email", "");
                    if (!user.containsKey("questionnaire_score")) user.put("questionnaire_score", null);
                    if (!user.containsKey("questionnaire_review_summary")) user.put("questionnaire_review_summary", null);
                }
                resp.put("success", true);
                resp.put("users", users);
            } catch (Exception e) {
                resp.put("success", false);
                resp.put("message", getMsg("admin.load_failed", language));
            }
            sendJson(exchange, resp);
        });
        
        // Unified user review interface - requires authentication
        server.createContext("/api/review", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String uuid = req.optString("uuid");
            String action = req.optString("action");
            String language = req.optString("language", "en");
            
            // Input validation
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            if (!"approve".equals(action) && !"reject".equals(action)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid action");
                sendJson(exchange, resp);
                return;
            }
            
            String reason = req.optString("reason", "");
            
            JSONObject resp = new JSONObject();
            try {
                // Get user information
                Map<String, Object> user = userDao.getUserByUuid(uuid);
                if (user == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.user_not_found", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                String username = (String) user.get("username");
                String password = (String) user.get("password");
                String userEmail = (String) user.get("email");
                
                String status = "approve".equals(action) ? "approved" : "rejected";
                boolean success = userDao.updateUserStatus(uuid, status);
                
                if (success && "approve".equals(action) && username != null) {
                    // Review approved, add to whitelist
                    debugLog("Execute: whitelist add " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + username);
                    });
                    
                    // If Authme integration is enabled and auto registration is enabled, and password exists, register to Authme
                    if (authmeService.isAuthmeEnabled() && authmeService.isAutoRegisterEnabled() && 
                        password != null && !password.trim().isEmpty()) {
                        authmeService.registerToAuthme(username, password);
                    }
                }
                
                // Send review result notification to user (async)
                if (success && userEmail != null && !userEmail.trim().isEmpty()) {
                    final String finalUsername = username;
                    final String finalEmail = userEmail;
                    final boolean approved = "approve".equals(action);
                    final String finalReason = reason;
                    final String finalLanguage = language;
                    new Thread(() -> {
                        try {
                            mailService.sendReviewResultNotification(finalEmail, finalUsername, approved, finalReason, finalLanguage);
                        } catch (Exception e) {
                            debugLog("Failed to send review result notification: " + e.getMessage());
                        }
                    }).start();
                }
                
                resp.put("success", success);
                resp.put("msg", success ? 
                    ("approve".equals(action) ? getMsg("review.approve_success", language) : getMsg("review.reject_success", language)) :
                    getMsg("review.failed", language));
                
                // WebSocket push
                if (success) {
                    JSONObject wsMsg = new JSONObject();
                    wsMsg.put("type", action);
                    wsMsg.put("uuid", uuid);
                    wsMsg.put("msg", resp.getString("msg"));
                    wsServer.broadcastMessage(wsMsg.toString());
                }
            } catch (Exception e) {
                resp.put("success", false);
                resp.put("message", getMsg("review.failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Get all users - requires authentication
        server.createContext("/api/all-users", exchange -> {
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }

            String language = "en";
            JSONObject resp = new JSONObject();
            try {
                // Only return non-pending users
                List<Map<String, Object>> users = userDao.getAllUsers();
                List<Map<String, Object>> filtered = new java.util.ArrayList<>();
                for (Map<String, Object> user : users) {
                    if (!"pending".equalsIgnoreCase(String.valueOf(user.get("status")))) {
                        if (!user.containsKey("regTime")) user.put("regTime", null);
                        if (!user.containsKey("email")) user.put("email", "");
                        filtered.add(user);
                    }
                }
                resp.put("success", true);
                resp.put("users", filtered);
            } catch (Exception e) {
                resp.put("success", false);
                resp.put("message", getMsg("admin.load_failed", language));
            }
            sendJson(exchange, resp);
        });
        
        // Get users with pagination - requires authentication
        server.createContext("/api/users-paginated", exchange -> {
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String language = "en";
            int page = 1;
            int pageSize = 10;
            String searchQuery = "";
            
            // Parse query parameters
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2) {
                        String key = keyValue[0];
                        String value = keyValue[1];
                        try {
                            switch (key) {
                                case "page":
                                    page = Math.max(1, Integer.parseInt(value));
                                    break;
                                case "pageSize":
                                    pageSize = Math.max(1, Math.min(100, Integer.parseInt(value))); // Limit max page size to 100
                                    break;
                                case "search":
                                    searchQuery = java.net.URLDecoder.decode(value, "UTF-8");
                                    break;
                                case "language":
                                    language = value;
                                    break;
                            }
                        } catch (Exception e) {
                            debugLog("Error parsing query parameter: " + param + ", error: " + e.getMessage());
                        }
                    }
                }
            }
            
            debugLog("Paginated users request: page=" + page + ", pageSize=" + pageSize + ", search=" + searchQuery);
            
            JSONObject resp = new JSONObject();
            try {
                List<Map<String, Object>> users;
                int totalCount;
                
                // Get approved users with pagination and optional search
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    users = userDao.getApprovedUsersWithPaginationAndSearch(page, pageSize, searchQuery);
                    totalCount = userDao.getApprovedUserCountWithSearch(searchQuery);
                } else {
                    users = userDao.getApprovedUsersWithPagination(page, pageSize);
                    totalCount = userDao.getApprovedUserCount();
                }
                
                // Ensure required fields exist (no need to filter pending users as they're already excluded)
                for (Map<String, Object> user : users) {
                    if (!user.containsKey("regTime")) user.put("regTime", null);
                    if (!user.containsKey("email")) user.put("email", "");
                    if (!user.containsKey("questionnaire_score")) user.put("questionnaire_score", null);
                    if (!user.containsKey("questionnaire_review_summary")) user.put("questionnaire_review_summary", null);
                }
                
                // Calculate pagination info
                int totalPages = (int) Math.ceil((double) totalCount / pageSize);
                boolean hasNext = page < totalPages;
                boolean hasPrev = page > 1;
                
                resp.put("success", true);
                resp.put("users", users);
                resp.put("pagination", new JSONObject()
                    .put("currentPage", page)
                    .put("pageSize", pageSize)
                    .put("totalCount", totalCount)
                    .put("totalPages", totalPages)
                    .put("hasNext", hasNext)
                    .put("hasPrev", hasPrev)
                );
                
                debugLog("Returning " + users.size() + " approved users for page " + page + "/" + totalPages);
                
            } catch (Exception e) {
                debugLog("Error in paginated users API: " + e.getMessage());
                resp.put("success", false);
                resp.put("message", getMsg("admin.load_failed", language));
            }
            sendJson(exchange, resp);
        });
        
        // Delete user - requires authentication
        server.createContext("/api/delete-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "en");
            
            // Input validation
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // Get user information for whitelist operations
                Map<String, Object> user = userDao.getUserByUuid(uuid);
                if (user == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.user_not_found", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                String username = (String) user.get("username");
                boolean success = userDao.deleteUser(uuid);
                
                if (success && username != null) {
                    // Remove from whitelist
                    debugLog("Execute: whitelist remove " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + username);
                    });
                    
                    // If Authme integration is enabled and auto unregister is configured, unregister user from Authme
                    if (authmeService.isAuthmeEnabled() && authmeService.isAutoUnregisterEnabled()) {
                        authmeService.unregisterFromAuthme(username);
                    }
                }
                
                resp.put("success", success);
                resp.put("msg", success ? getMsg("admin.delete_success", language) : getMsg("admin.delete_failed", language));
            } catch (Exception e) {
                debugLog("Delete user error: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", getMsg("admin.delete_failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Ban user - requires authentication
        server.createContext("/api/ban-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "en");
            
            // Input validation
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // Get user information for whitelist operations
                Map<String, Object> user = userDao.getUserByUuid(uuid);
                if (user == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.user_not_found", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                String username = (String) user.get("username");
                boolean success = userDao.updateUserStatus(uuid, "banned");
                
                if (success && username != null) {
                    // Remove from whitelist
                    debugLog("Execute: whitelist remove " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + username);
                    });
                }
                
                resp.put("success", success);
                resp.put("msg", success ? getMsg("admin.ban_success", language) : getMsg("admin.ban_failed", language));
            } catch (Exception e) {
                debugLog("Ban user error: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", getMsg("admin.ban_failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Unban user - requires authentication
        server.createContext("/api/unban-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "en");
            
            // Input validation
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // Get user information for whitelist operations
                Map<String, Object> user = userDao.getUserByUuid(uuid);
                if (user == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.user_not_found", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                String username = (String) user.get("username");
                boolean success = userDao.updateUserStatus(uuid, "approved");
                
                if (success && username != null) {
                    // Re-add to whitelist
                    debugLog("Execute: whitelist add " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + username);
                    });
                }
                
                resp.put("success", success);
                resp.put("msg", success ? getMsg("admin.unban_success", language) : getMsg("admin.unban_failed", language));
            } catch (Exception e) {
                debugLog("Unban user error: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", getMsg("admin.unban_failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Change user password
        server.createContext("/api/change-password", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String uuid = req.optString("uuid");
            String username = req.optString("username");
            String newPassword = req.optString("newPassword");
            String language = req.optString("language", "en");
            
            JSONObject resp = new JSONObject();
            
            // Validate input
            if ((uuid == null || uuid.trim().isEmpty()) && (username == null || username.trim().isEmpty())) {
                resp.put("success", false);
                resp.put("msg", getMsg("admin.missing_user_identifier", language));
                sendJson(exchange, resp);
                return;
            }
            
            if (newPassword == null || newPassword.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("msg", getMsg("admin.password_required", language));
                sendJson(exchange, resp);
                return;
            }
            
            // Validate password format
            if (!authmeService.isValidPassword(newPassword)) {
                resp.put("success", false);
                String passwordRegex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$");
                resp.put("msg", getMsg("admin.invalid_password", language).replace("{regex}", passwordRegex));
                sendJson(exchange, resp);
                return;
            }
            
            try {
                // Find user
                Map<String, Object> user = null;
                if (uuid != null && !uuid.trim().isEmpty()) {
                    user = userDao.getUserByUuid(uuid);
                } else if (username != null && !username.trim().isEmpty()) {
                    user = userDao.getUserByUsername(username);
                }
                
                if (user == null) {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.user_not_found", language));
                    sendJson(exchange, resp);
                    return;
                }
                
                String targetUsername = (String) user.get("username");
                String targetUuid = (String) user.get("uuid");
                
                // Update password
                boolean success = userDao.updateUserPassword(targetUuid, newPassword);
                
                if (success) {
                    // If Authme integration is enabled, synchronize Authme password update
                    if (authmeService.isAuthmeEnabled()) {
                        authmeService.changePasswordInAuthme(targetUsername, newPassword);
                    }
                    
                    resp.put("success", true);
                    resp.put("msg", getMsg("admin.password_change_success", language));
                } else {
                    resp.put("success", false);
                    resp.put("msg", getMsg("admin.password_change_failed", language));
                }
            } catch (Exception e) {
                debugLog("Change password error: " + e.getMessage());
                resp.put("success", false);
                resp.put("msg", getMsg("admin.password_change_failed", language));
            }
            
            sendJson(exchange, resp);
        });
        
        // Get user status
        server.createContext("/api/user-status", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            String query = exchange.getRequestURI().getQuery();
            String uuid = null;
            if (query != null && query.contains("uuid=")) {
                uuid = query.split("uuid=")[1].split("&")[0];
            }
            
            JSONObject resp = new JSONObject();
            if (uuid != null) {
                // Validate UUID format
                if (!isValidUUID(uuid)) {
                    resp.put("success", false);
                    resp.put("message", "Invalid UUID format");
                    sendJson(exchange, resp);
                    return;
                }
                
                try {
                    Map<String, Object> user = userDao.getUserByUuid(uuid);
                    if (user != null) {
                        resp.put("success", true);
                        JSONObject data = new JSONObject();
                        data.put("status", user.get("status"));
                        data.put("reason", user.get("reason"));
                        resp.put("data", data);
                    } else {
                        resp.put("success", false);
                        resp.put("message", "User not found");
                    }
                } catch (Exception e) {
                    resp.put("success", false);
                    resp.put("message", "Failed to get user status");
                }
            } else {
                resp.put("success", false);
                resp.put("message", "UUID parameter required");
            }
            sendJson(exchange, resp);
        });
        
        // Version check API - requires authentication
        server.createContext("/api/version-check", exchange -> {
            // Verify authentication
            if (!isAuthenticated(exchange)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Authentication required");
                sendJson(exchange, resp);
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            try {
                // Get version check service from main plugin
                team.kitemc.verifymc.VerifyMC mainPlugin = (team.kitemc.verifymc.VerifyMC) plugin;
                team.kitemc.verifymc.service.VersionCheckService versionService = mainPlugin.getVersionCheckService();
                
                if (versionService != null) {
                    // Perform async version check
                    versionService.checkForUpdatesAsync().thenAccept(result -> {
                        try {
                            sendJson(exchange, result.toJson());
                        } catch (Exception e) {
                            debugLog("Error sending version check response: " + e.getMessage());
                        }
                    }).exceptionally(throwable -> {
                        try {
                            JSONObject errorResp = new JSONObject();
                            errorResp.put("success", false);
                            errorResp.put("error", "Version check failed: " + throwable.getMessage());
                            sendJson(exchange, errorResp);
                        } catch (Exception e) {
                            debugLog("Error sending version check error response: " + e.getMessage());
                        }
                        return null;
                    });
                } else {
                    JSONObject resp = new JSONObject();
                    resp.put("success", false);
                    resp.put("error", "Version check service not available");
                    sendJson(exchange, resp);
                }
            } catch (Exception e) {
                debugLog("Version check API error: " + e.getMessage());
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("error", "Internal server error");
                sendJson(exchange, resp);
            }
        });
        
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        stopCleanupTask();
        if (server != null) server.stop(0);
    }

    // Static resource handler
    static class StaticHandler implements HttpHandler {
        private final String baseDir;
        public StaticHandler(String baseDir) {
            this.baseDir = baseDir;
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = URLDecoder.decode(exchange.getRequestURI().getPath(), "UTF-8");
            if (uri.equals("/")) uri = "/index.html";
            Path file = Paths.get(baseDir, uri);
            try {
                if (!file.normalize().startsWith(Paths.get(baseDir).normalize()) || !Files.exists(file) || Files.isDirectory(file)) {
                    // For all non-/api/ paths, fallback to index.html
                    if (!uri.startsWith("/api/")) {
                        Path index = Paths.get(baseDir, "index.html");
                        if (Files.exists(index)) {
                            byte[] data = Files.readAllBytes(index);
                            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                            exchange.sendResponseHeaders(200, data.length);
                            exchange.getResponseBody().write(data);
                            exchange.close();
                            return;
                        }
                    }
                    // Fallback to 404.html
                    Path notFound = Paths.get(baseDir, "404.html");
                    if (Files.exists(notFound)) {
                        byte[] data = Files.readAllBytes(notFound);
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                        exchange.sendResponseHeaders(404, data.length);
                        exchange.getResponseBody().write(data);
                        exchange.close();
                    } else {
                        exchange.sendResponseHeaders(404, 0);
                        exchange.getResponseBody().close();
                    }
                    return;
                }
                String mime = Files.probeContentType(file);
                if (mime == null) {
                    // Try to determine MIME type from file extension
                    String fileName = file.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                        mime = "text/html";
                    } else if (fileName.endsWith(".css")) {
                        mime = "text/css";
                    } else if (fileName.endsWith(".js")) {
                        mime = "application/javascript";
                    } else if (fileName.endsWith(".json")) {
                        mime = "application/json";
                    } else if (fileName.endsWith(".svg")) {
                        mime = "image/svg+xml";
                    } else if (fileName.endsWith(".xml")) {
                        mime = "text/xml";
                    } else {
                        mime = "application/octet-stream";
                    }
                }
                
                // Add charset=utf-8 for text-based content types
                String contentType = mime;
                if (mime.startsWith("text/") || 
                    mime.equals("application/javascript") || 
                    mime.equals("application/json") ||
                    mime.equals("application/xml") ||
                    mime.equals("text/xml") ||
                    mime.equals("image/svg+xml")) {
                    contentType = mime + "; charset=utf-8";
                }
                
                exchange.getResponseHeaders().add("Content-Type", contentType);
                byte[] data = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, data.length);
                OutputStream os = exchange.getResponseBody();
                os.write(data);
                os.close();
            } catch (Exception e) {
                Path errPage = Paths.get(baseDir, "500.html");
                if (Files.exists(errPage)) {
                    byte[] data = Files.readAllBytes(errPage);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(500, data.length);
                    exchange.getResponseBody().write(data);
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.getResponseBody().close();
                }
            }
        }
    }

    private void sendJson(HttpExchange exchange, JSONObject resp) throws IOException {
        JSONObject withCopy = withCopyright(resp);
        byte[] data = withCopy.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
    
    /**
     * Send Discord OAuth callback result as an HTML page
     */
    private void sendDiscordCallbackHtml(HttpExchange exchange, boolean success, String message, String discordUsername) throws IOException {
        String serverName = plugin.getConfig().getString("web_server_prefix", "Server");
        String statusIcon = success ? "✓" : "✗";
        String statusColor = success ? "#4ade80" : "#f87171";
        String statusText = success ? "Success" : "Failed";
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>Discord Link - ").append(escapeHtml(serverName)).append("</title>");
        html.append("<style>");
        html.append("*{margin:0;padding:0;box-sizing:border-box}");
        html.append("body{min-height:100vh;display:flex;align-items:center;justify-content:center;");
        html.append("background:linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%);");
        html.append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#fff}");
        html.append(".card{background:rgba(255,255,255,0.1);backdrop-filter:blur(10px);");
        html.append("border:1px solid rgba(255,255,255,0.2);border-radius:16px;padding:40px;");
        html.append("text-align:center;max-width:400px;width:90%;box-shadow:0 8px 32px rgba(0,0,0,0.3)}");
        html.append(".icon{font-size:64px;margin-bottom:20px;color:").append(statusColor).append("}");
        html.append(".title{font-size:24px;font-weight:600;margin-bottom:8px;color:").append(statusColor).append("}");
        html.append(".message{color:rgba(255,255,255,0.8);margin-bottom:20px;line-height:1.5}");
        html.append(".discord-user{background:rgba(88,101,242,0.3);border:1px solid rgba(88,101,242,0.5);");
        html.append("border-radius:8px;padding:12px 20px;margin-bottom:20px;display:inline-flex;align-items:center;gap:8px}");
        html.append(".discord-icon{width:24px;height:24px}");
        html.append(".btn{display:inline-block;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);");
        html.append("color:#fff;text-decoration:none;padding:12px 32px;border-radius:8px;font-weight:500;");
        html.append("transition:transform 0.2s,box-shadow 0.2s}");
        html.append(".btn:hover{transform:translateY(-2px);box-shadow:0 4px 20px rgba(102,126,234,0.4)}");
        html.append("</style></head><body>");
        html.append("<div class=\"card\">");
        html.append("<div class=\"icon\">").append(statusIcon).append("</div>");
        html.append("<div class=\"title\">").append(statusText).append("</div>");
        html.append("<div class=\"message\">").append(escapeHtml(message)).append("</div>");
        
        if (success && discordUsername != null) {
            html.append("<div class=\"discord-user\">");
            html.append("<svg class=\"discord-icon\" viewBox=\"0 0 24 24\" fill=\"#5865F2\">");
            html.append("<path d=\"M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03z\"/>");
            html.append("</svg>");
            html.append("<span>").append(escapeHtml(discordUsername)).append("</span>");
            html.append("</div>");
        }
        
        html.append("<a href=\"/\" class=\"btn\">Return to Registration</a>");
        html.append("</div></body></html>");
        
        byte[] data = html.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String getMsg(String key, String language) {
        // Get or load language resource bundle
        ResourceBundle bundle = getLanguageBundle(language);
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getString(key);
        }
        return key;
    }
    
    /**
     * Get language resource bundle, load and cache if not exists
     * @param language Language code
     * @return ResourceBundle for the language
     */
    private ResourceBundle getLanguageBundle(String language) {
        // Check cache first
        if (languageCache.containsKey(language)) {
            return languageCache.get(language);
        }
        
        // Load language file
        try {
            File i18nDir = new File(plugin.getDataFolder(), "i18n");
            File propFile = new File(i18nDir, "messages_" + language + ".properties");
            
            if (propFile.exists()) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(propFile), java.nio.charset.StandardCharsets.UTF_8)) {
                    ResourceBundle bundle = new java.util.PropertyResourceBundle(reader);
                    languageCache.put(language, bundle);
                    return bundle;
                }
            } else {
                // Try to load from JAR
                try {
                    ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", new java.util.Locale(language));
                    languageCache.put(language, bundle);
                    return bundle;
                } catch (Exception e) {
                    // Fallback to default messages
                    if (!languageCache.containsKey("default")) {
                        languageCache.put("default", messages);
                    }
                    return messages;
                }
            }
        } catch (Exception e) {
            debugLog("Failed to load language bundle for: " + language + ", error: " + e.getMessage());
            return messages; // Fallback to default
        }
    }
} 
