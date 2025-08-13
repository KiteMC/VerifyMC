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
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import java.util.ResourceBundle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Arrays;

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
    private final ReviewWebSocketServer wsServer;
    private final ResourceBundle messagesZh;
    private final ResourceBundle messagesEn;
    private final boolean debug;
    
    // Authentication related
    private final ConcurrentHashMap<String, Long> validTokens = new ConcurrentHashMap<>();
    private final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final long TOKEN_EXPIRY_TIME = 3600000; // 1 hour

    // Default mainstream email domain whitelist
    private static final java.util.List<String> DEFAULT_EMAIL_DOMAIN_WHITELIST = Arrays.asList(
        "gmail.com", "qq.com", "163.com", "126.com", "outlook.com", "hotmail.com", "yahoo.com",
        "sina.com", "aliyun.com", "foxmail.com", "icloud.com", "yeah.net", "live.com", "mail.com",
        "protonmail.com", "zoho.com"
    );

    public WebServer(int port, String staticDir, Plugin plugin, VerifyCodeService codeService, MailService mailService, UserDao userDao, AuditDao auditDao, AuthmeService authmeService, ReviewWebSocketServer wsServer, ResourceBundle messagesZh, ResourceBundle messagesEn) {
        this.port = port;
        this.staticDir = staticDir;
        this.plugin = plugin;
        this.codeService = codeService;
        this.mailService = mailService;
        this.userDao = userDao;
        this.auditDao = auditDao;
        this.authmeService = authmeService;
        this.wsServer = wsServer;
        this.messagesZh = messagesZh;
        this.messagesEn = messagesEn;
        this.debug = plugin.getConfig().getBoolean("debug", false);
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
    private boolean isUsernameCaseConflict(String username) {
        return ((team.kitemc.verifymc.VerifyMC)plugin).isUsernameCaseConflict(username);
    }
    
    /**
     * Scheduled task to clean up expired tokens
     */
    private void startTokenCleanupTask() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // Clean up every 5 minutes
                    long currentTime = System.currentTimeMillis();
                    validTokens.entrySet().removeIf(entry -> entry.getValue() < currentTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
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
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp.getBytes());
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
            
            resp.put("login", login);
            resp.put("admin", admin);
            resp.put("frontend", frontend);
            resp.put("authme", authme);
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
        
        // /api/send_code send verification code interface with rate limiting and authentication
        server.createContext("/api/send_code", exchange -> {
            debugLog("/api/send_code called");
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String email = req.optString("email", "").trim().toLowerCase();
            String language = req.optString("language", "zh");

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
            debugLog("Generated verification code for email: " + email + ", code: " + code);
            
            boolean sent = mailService.sendCode(email, getMsg("email.subject", language), code);
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
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String email = req.optString("email", "").trim().toLowerCase();
            String code = req.optString("code");
            String uuid = req.optString("uuid");
            String username = req.optString("username");
            String password = req.optString("password", ""); // New password parameter
            String language = req.optString("language", "zh");
            debugLog("register params: email=" + email + ", code=" + code + ", uuid=" + uuid + ", username=" + username + ", hasPassword=" + !password.isEmpty());

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
            if (!isValidUsername(username)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                String usernameRegex = plugin.getConfig().getString("username_regex", "^[a-zA-Z0-9_-]{3,16}$");
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
            JSONObject resp = new JSONObject();
            if (!codeService.checkCode(email, code)) {
                debugLog("Verification code check failed: email=" + email + ", code=" + code);
                plugin.getLogger().warning("[VerifyMC] Registration failed: wrong code, email=" + email + ", code=" + code);
                resp.put("success", false); 
                resp.put("msg", getMsg("verify.wrong_code", language));
            } else {
                debugLog("Verification code passed, registering user");
                boolean autoApprove = plugin.getConfig().getBoolean("register.auto_approve", false);
                String status = autoApprove ? "approved" : "pending";
                boolean ok;
                
                // Choose registration method based on whether password is provided
                if (password != null && !password.trim().isEmpty()) {
                    ok = userDao.registerUser(uuid, username, email, status, password);
                } else {
                    ok = userDao.registerUser(uuid, username, email, status);
                }
                
                debugLog("registerUser result: " + ok);
                if (ok) {
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
                resp.put("msg", ok ? getMsg("register.success", language) : getMsg("register.failed", language));
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
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String password = req.optString("password");
            String language = req.optString("language", "zh");
            
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
            String language = "zh";
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
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String uuid = req.optString("uuid");
            String action = req.optString("action");
            String language = req.optString("language", "zh");
            
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

            String language = "zh";
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
            String language = "zh";
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
                
                // Get users with pagination and optional search
                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    users = userDao.getUsersWithPaginationAndSearch(page, pageSize, searchQuery);
                    totalCount = userDao.getTotalUserCountWithSearch(searchQuery);
                } else {
                    users = userDao.getUsersWithPagination(page, pageSize);
                    totalCount = userDao.getTotalUserCount();
                }
                
                // Filter out pending users and ensure required fields exist
                List<Map<String, Object>> filtered = new java.util.ArrayList<>();
                for (Map<String, Object> user : users) {
                    if (!"pending".equalsIgnoreCase(String.valueOf(user.get("status")))) {
                        if (!user.containsKey("regTime")) user.put("regTime", null);
                        if (!user.containsKey("email")) user.put("email", "");
                        filtered.add(user);
                    }
                }
                
                // Calculate pagination info
                int totalPages = (int) Math.ceil((double) totalCount / pageSize);
                boolean hasNext = page < totalPages;
                boolean hasPrev = page > 1;
                
                resp.put("success", true);
                resp.put("users", filtered);
                resp.put("pagination", new JSONObject()
                    .put("currentPage", page)
                    .put("pageSize", pageSize)
                    .put("totalCount", totalCount)
                    .put("totalPages", totalPages)
                    .put("hasNext", hasNext)
                    .put("hasPrev", hasPrev)
                );
                
                debugLog("Returning " + filtered.size() + " users for page " + page + "/" + totalPages);
                
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
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "zh");
            
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
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "zh");
            
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
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String uuid = req.optString("uuid");
            String language = req.optString("language", "zh");
            
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
            
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String uuid = req.optString("uuid");
            String username = req.optString("username");
            String newPassword = req.optString("newPassword");
            String language = req.optString("language", "zh");
            
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
                if (mime == null) mime = "application/octet-stream";
                exchange.getResponseHeaders().add("Content-Type", mime);
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
        byte[] data = withCopy.toString().getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
    private String getMsg(String key, String language) {
        if ("en".equals(language)) {
            return messagesEn.containsKey(key) ? messagesEn.getString(key) : key;
        } else {
            return messagesZh.containsKey(key) ? messagesZh.getString(key) : key;
        }
    }
} 