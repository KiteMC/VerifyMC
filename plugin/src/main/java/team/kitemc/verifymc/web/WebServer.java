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
import team.kitemc.verifymc.db.FileUserDao;
import team.kitemc.verifymc.db.FileAuditDao;
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
    private final FileUserDao userDao;
    private final FileAuditDao auditDao;
    private final ReviewWebSocketServer wsServer;
    private final ResourceBundle messagesZh;
    private final ResourceBundle messagesEn;
    private final boolean debug;
    
    // 认证相关
    private final ConcurrentHashMap<String, Long> validTokens = new ConcurrentHashMap<>();
    private final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final long TOKEN_EXPIRY_TIME = 3600000; // 1小时

    // 默认主流邮箱域名白名单
    private static final java.util.List<String> DEFAULT_EMAIL_DOMAIN_WHITELIST = Arrays.asList(
        "gmail.com", "qq.com", "163.com", "126.com", "outlook.com", "hotmail.com", "yahoo.com",
        "sina.com", "aliyun.com", "foxmail.com", "icloud.com", "yeah.net", "live.com", "mail.com",
        "protonmail.com", "zoho.com"
    );

    public WebServer(int port, String staticDir, Plugin plugin, VerifyCodeService codeService, MailService mailService, FileUserDao userDao, FileAuditDao auditDao, ReviewWebSocketServer wsServer, ResourceBundle messagesZh, ResourceBundle messagesEn) {
        this.port = port;
        this.staticDir = staticDir;
        this.plugin = plugin;
        this.codeService = codeService;
        this.mailService = mailService;
        this.userDao = userDao;
        this.auditDao = auditDao;
        this.wsServer = wsServer;
        this.messagesZh = messagesZh;
        this.messagesEn = messagesEn;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] " + msg);
    }

    // 认证验证方法
    private boolean isAuthenticated(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return validateToken(token);
    }
    
    // Token验证
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
    
    // 生成安全Token
    private String generateSecureToken() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String random = String.valueOf(Math.random());
            String secret = plugin.getConfig().getString("admin.password", "default_secret");
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String combined = timestamp + random + secret;
            byte[] hash = md.digest(combined.getBytes());
            String token = Base64.getEncoder().encodeToString(hash);
            
            // 存储token和过期时间
            validTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_TIME);
            
            return token;
        } catch (NoSuchAlgorithmException e) {
            // 降级到简单token
            String token = "admin_token_" + System.currentTimeMillis() + "_" + Math.random();
            validTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_TIME);
            return token;
        }
    }
    
    // 输入验证方法
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    private boolean isValidUUID(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }
    
    private boolean isValidUsername(String username) {
        return ((team.kitemc.verifymc.VerifyMC)plugin).isValidUsername(username);
    }
    private boolean isUsernameCaseConflict(String username) {
        return ((team.kitemc.verifymc.VerifyMC)plugin).isUsernameCaseConflict(username);
    }
    
    // 清理过期token的定时任务
    private void startTokenCleanupTask() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // 每5分钟清理一次
                    long currentTime = System.currentTimeMillis();
                    validTokens.entrySet().removeIf(entry -> entry.getValue() < currentTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    // 获取邮箱域名白名单
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

    // 获取是否启用邮箱域名白名单
    private boolean isEmailDomainWhitelistEnabled() {
        try {
            return plugin.getConfig().getBoolean("enable_email_domain_whitelist", true);
        } catch (Exception ignored) {}
        return true;
    }
    // 获取是否启用邮箱别名限制
    private boolean isEmailAliasLimitEnabled() {
        try {
            return plugin.getConfig().getBoolean("enable_email_alias_limit", false);
        } catch (Exception ignored) {}
        return false;
    }

    // 工具方法：在 JSON 响应中添加版权字段
    private JSONObject withCopyright(JSONObject obj) {
        obj.put("copyright", "Powered by VerifyMC (GPLv3)");
        return obj;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 启动token清理任务
        startTokenCleanupTask();
        
        // 静态资源
        server.createContext("/", new StaticHandler(staticDir));
        
        // API 示例
        server.createContext("/api/ping", exchange -> {
            String resp = "{\"msg\":\"pong\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp.getBytes());
            os.close();
        });
        
        // /api/config 配置接口
        server.createContext("/api/config", exchange -> {
            JSONObject resp = new JSONObject();
            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
            // login 配置
            JSONObject login = new JSONObject();
            login.put("enable_email", config.getStringList("auth_methods").contains("email"));
            login.put("email_smtp", config.getString("smtp.host", ""));
            // admin 配置
            JSONObject admin = new JSONObject();
            // frontend 配置
            JSONObject frontend = new JSONObject();
            frontend.put("theme", config.getString("frontend.theme", "default"));
            frontend.put("logo_url", config.getString("frontend.logo_url", ""));
            frontend.put("announcement", config.getString("frontend.announcement", ""));
            frontend.put("web_server_prefix", config.getString("web_server_prefix", "[VerifyMC]"));
            frontend.put("current_theme", config.getString("frontend.theme", "default"));
            resp.put("login", login);
            resp.put("admin", admin);
            resp.put("frontend", frontend);
            sendJson(exchange, resp);
        });
        
        // /api/reload-config 重载配置接口 - 需要认证
        server.createContext("/api/reload-config", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // 验证认证
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
                // 更新静态文件目录以支持主题切换
                String theme = plugin.getConfig().getString("frontend.theme", "default");
                
                // 使用ResourceManager检查主题
                team.kitemc.verifymc.ResourceManager resourceManager = new team.kitemc.verifymc.ResourceManager((org.bukkit.plugin.java.JavaPlugin) plugin);
                if (!resourceManager.themeExists(theme)) {
                    resp.put("success", false);
                    resp.put("message", "Theme not found: " + theme + ". Available themes: default, glassx");
                    sendJson(exchange, resp);
                    return;
                }
                
                String newStaticDir = resourceManager.getThemeStaticDir(theme);
                
                // 更新静态文件目录
                debugLog("Switching theme from " + this.staticDir + " to " + newStaticDir);
                this.staticDir = newStaticDir;
                
                // 重新创建静态文件处理器
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
        
        // /api/send_code 发送验证码接口
        server.createContext("/api/send_code", exchange -> {
            debugLog("/api/send_code called");
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, 0); exchange.close(); return; }
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String email = req.optString("email", "").trim().toLowerCase();
            String language = req.optString("language", "zh");

            // 邮箱别名限制
            if (isEmailAliasLimitEnabled() && email.contains("+")) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.alias_not_allowed", language));
                sendJson(exchange, resp);
                return;
            }

            // 邮箱域名白名单
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
            
            // 验证邮箱格式
            if (!isValidEmail(email)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("email.invalid_format", language));
                sendJson(exchange, resp);
                return;
            }
            
            String code = codeService.generateCode(email);
            debugLog("generateCode for email: " + email + ", code: " + code);
            boolean sent = mailService.sendCode(email, getMsg("email.subject", language), code);
            JSONObject resp = new JSONObject();
            resp.put("success", sent);
            resp.put("msg", sent ? getMsg("email.sent", language) : getMsg("email.failed", language));
            sendJson(exchange, resp);
        });
        
        // /api/register 注册接口
        server.createContext("/api/register", exchange -> {
            debugLog("/api/register called");
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, 0); exchange.close(); return; }
            JSONObject req = new JSONObject(new String(exchange.getRequestBody().readAllBytes()));
            String email = req.optString("email", "").trim().toLowerCase();
            String code = req.optString("code");
            String uuid = req.optString("uuid");
            String username = req.optString("username");
            String language = req.optString("language", "zh");
            debugLog("register params: email=" + email + ", code=" + code + ", uuid=" + uuid + ", username=" + username);

            // 邮箱别名限制
            if (isEmailAliasLimitEnabled() && email.contains("+")) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("register.alias_not_allowed", language));
                sendJson(exchange, resp);
                return;
            }

            // 邮箱域名白名单
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
            // 用户名唯一性检查
            if (userDao.getUserByUsername(username) != null) {
                debugLog("Username already exists: " + username);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", "Username already exists");
                sendJson(exchange, resp);
                return;
            }
            // 注册前校验用户名规则和大小写冲突
            if (!isValidUsername(username)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("msg", getMsg("username.invalid", language));
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
            // 邮箱注册数限制
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
            // 输入验证
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
                boolean ok = userDao.registerUser(uuid, username, email, status);
                debugLog("registerUser result: " + ok);
                if (ok) {
                    // 注册成功，自动添加白名单
                    debugLog("Execute: whitelist add " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + username);
                    });
                }
                if (!ok) {
                    plugin.getLogger().warning("[VerifyMC] Registration failed: userDao.registerUser returned false, uuid=" + uuid + ", username=" + username + ", email=" + email);
                }
                resp.put("success", ok);
                resp.put("msg", ok ? getMsg("register.success", language) : getMsg("register.failed", language));
            }
            sendJson(exchange, resp);
        });
        
        // 管理员登录
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
        
        // 获取待审核用户列表 - 需要认证
        server.createContext("/api/pending-list", exchange -> {
            // 验证认证
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
                // 从数据库获取待审核用户列表
                List<Map<String, Object>> users = userDao.getPendingUsers();
                // 确保每个用户都包含 email 和 regTime 字段
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
        
        // 统一审核用户接口 - 需要认证
        server.createContext("/api/review", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // 验证认证
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
            
            // 输入验证
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
                String status = "approve".equals(action) ? "approved" : "rejected";
                boolean success = userDao.updateUserStatus(uuid, status);
                resp.put("success", success);
                resp.put("msg", success ? 
                    ("approve".equals(action) ? getMsg("review.approve_success", language) : getMsg("review.reject_success", language)) :
                    getMsg("review.failed", language));
                
                // WebSocket推送
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
        
        // 获取所有用户 - 需要认证
        server.createContext("/api/all-users", exchange -> {
            // 验证认证
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
                // 只返回非pending用户
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
        
        // 删除用户 - 需要认证
        server.createContext("/api/delete-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // 验证认证
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
            
            // 输入验证
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // 获取用户信息用于白名单操作
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
                    // 从白名单中移除
                    debugLog("Execute: whitelist remove " + username);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + username);
                    });
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
        
        // 封禁用户 - 需要认证
        server.createContext("/api/ban-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // 验证认证
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
            
            // 输入验证
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // 获取用户信息用于白名单操作
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
                    // 从白名单中移除
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
        
        // 解封用户 - 需要认证
        server.createContext("/api/unban-user", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { 
                exchange.sendResponseHeaders(405, 0); 
                exchange.close(); 
                return; 
            }
            
            // 验证认证
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
            
            // 输入验证
            if (!isValidUUID(uuid)) {
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("message", "Invalid UUID format");
                sendJson(exchange, resp);
                return;
            }
            
            JSONObject resp = new JSONObject();
            try {
                // 获取用户信息用于白名单操作
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
                    // 重新添加到白名单
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
        
        // 获取用户状态
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
                // 验证UUID格式
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
        
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // 静态资源处理器
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
                    // 只要不是 /api/ 路径，全部兜底返回 index.html
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
                    // 兜底 404.html
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