package team.kitemc.verifymc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.web.WebServer;
import java.io.File;
import team.kitemc.verifymc.web.ReviewWebSocketServer;
import team.kitemc.verifymc.db.FileUserDao;
import team.kitemc.verifymc.db.FileAuditDao;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.service.VerifyCodeService;
import java.util.Properties;
import java.util.MissingResourceException;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.db.AuditDao;
import team.kitemc.verifymc.db.MysqlAuditDao;
import team.kitemc.verifymc.db.MysqlUserDao;
import team.kitemc.verifymc.service.AuthmeService;

import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class VerifyMC extends JavaPlugin implements Listener {
    private ResourceBundle messagesZh;
    private ResourceBundle messagesEn;
    private WebServer webServer;
    private ReviewWebSocketServer wsServer;
    // 字段类型改为接口
    private UserDao userDao;
    private AuditDao auditDao;
    private VerifyCodeService codeService;
    private MailService mailService;
    private AuthmeService authmeService;
    private ResourceManager resourceManager;
    private String whitelistMode;
    private boolean whitelistJsonSync;
    private String webRegisterUrl;
    private String webServerPrefix;
    private Path whitelistJsonPath;
    private long lastWhitelistJsonModified = 0;
    public boolean debug = false;

    public void debugLog(String msg) {
        if (debug) getLogger().info("[DEBUG] " + msg);
    }

    private String getConfigLanguage() {
        String lang = getConfig().getString("language", "zh");
        return ("en".equalsIgnoreCase(lang)) ? "en" : "zh";
    }

    private String getMessage(String key) {
        return getMessage(key, getConfigLanguage());
    }

    private String getMessage(String key, String language) {
        if ("en".equals(language)) {
            return messagesEn != null && messagesEn.containsKey(key) ? messagesEn.getString(key) : key;
        } else {
            return messagesZh != null && messagesZh.containsKey(key) ? messagesZh.getString(key) : key;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        whitelistMode = config.getString("whitelist_mode", "bukkit");
        whitelistJsonSync = config.getBoolean("whitelist_json_sync", true);
        webRegisterUrl = config.getString("web_register_url", "https://yourdomain.com/");
        webServerPrefix = config.getString("web_server_prefix", "[VerifyMC]");
        whitelistJsonPath = Paths.get(getServer().getWorldContainer().getAbsolutePath(), "whitelist.json");
        debug = config.getBoolean("debug", false);
        // 初始化资源管理器
        resourceManager = new ResourceManager(this);
        resourceManager.initializeResources();
        // 检查资源更新
        ResourceUpdater resourceUpdater = new ResourceUpdater(this);
        resourceUpdater.checkAndUpdateResources();
        // 加载i18n资源包
        ResourceBundle[] bundles = resourceManager.loadI18nBundles();
        messagesZh = bundles[0];
        messagesEn = bundles[1];
        // 初始化服务
        codeService = new VerifyCodeService(this);
        mailService = new MailService(this, this::getMessage);
        authmeService = new AuthmeService(this);
        String storageType = getConfig().getString("storage.type", "data");
        String lang = getConfig().getString("language", "zh");
        ResourceBundle messages;
        try {
            messages = ResourceBundle.getBundle("i18n/messages_" + lang);
        } catch (MissingResourceException e) {
            messages = ResourceBundle.getBundle("i18n/messages_en");
            getLogger().warning("No messages_" + lang + ".properties found, fallback to English.");
        }

        if ("mysql".equalsIgnoreCase(storageType)) {
            Properties mysqlConfig = new Properties();
            mysqlConfig.setProperty("host", getConfig().getString("storage.mysql.host"));
            mysqlConfig.setProperty("port", String.valueOf(getConfig().getInt("storage.mysql.port")));
            mysqlConfig.setProperty("database", getConfig().getString("storage.mysql.database"));
            mysqlConfig.setProperty("user", getConfig().getString("storage.mysql.user"));
            mysqlConfig.setProperty("password", getConfig().getString("storage.mysql.password"));
            try {
                userDao = new MysqlUserDao(mysqlConfig, messages, this);
                auditDao = new MysqlAuditDao(mysqlConfig);
                getLogger().info(messages.getString("storage.mysql.enabled"));
            } catch (Exception e) {
                getLogger().severe(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            File userFile = new File(getDataFolder(), "data/users.json");
            File auditFile = new File(getDataFolder(), "data/audits.json");
            userFile.getParentFile().mkdirs();
            auditFile.getParentFile().mkdirs();
            userDao = new FileUserDao(userFile, this);
            auditDao = new FileAuditDao(auditFile);
            getLogger().info(messages.getString("storage.file.enabled"));
        }
        autoMigrateIfNeeded(messages);
        // 初始化文件存储
        // 删除后续 userDao/auditDao 的重复赋值
        // 启动WebSocket服务（必须先于webServer）
        int port = config.getInt("web_port", 8080);
        int wsPort = config.getInt("ws_port", port + 1);
        wsServer = new ReviewWebSocketServer(wsPort, this);
        try {
            wsServer.start();
            getLogger().info(getMessage("websocket.start_success") + ": " + wsPort);
        } catch (Exception e) {
            getLogger().warning(getMessage("websocket.start_failed") + ": " + e.getMessage());
        }
        // 启动Web服务
        String theme = config.getString("frontend.theme", "default");
        String staticDir = resourceManager.getThemeStaticDir(theme);
        webServer = new WebServer(port, staticDir, this, codeService, mailService, userDao, auditDao, authmeService, wsServer, messagesZh, messagesEn);
        try {
            webServer.start();
            getLogger().info(getMessage("web.start_success") + ": " + port);
        } catch (Exception e) {
            getLogger().warning(getMessage("web.start_failed") + ": " + e.getMessage());
        }
        boolean autoSync = getConfig().getBoolean("auto_sync_whitelist", true);
        boolean autoCleanup = getConfig().getBoolean("auto_cleanup_whitelist", true);
        if ("bukkit".equalsIgnoreCase(whitelistMode) && autoSync) {
            syncWhitelistToServer();
        } else if (!"bukkit".equalsIgnoreCase(whitelistMode) && autoCleanup) {
            cleanupServerWhitelist();
        }
        if ("plugin".equalsIgnoreCase(whitelistMode)) {
            getServer().getPluginManager().registerEvents(this, this);
        } else if ("bukkit".equalsIgnoreCase(whitelistMode) && whitelistJsonSync) {
            startWhitelistJsonWatcher();
        }
        // 兼容性检测与提示
        String serverName = getServer().getName().toLowerCase();
        getLogger().info("[VerifyMC] Supported server cores: Bukkit, Spigot, Paper, Purpur, Folia, Velocity, Canvas, Waterfall (MC 1.12 - 1.21.8)");
        if (serverName.contains("folia")) {
            getLogger().info("[VerifyMC] Detected Folia: Async compatibility is experimental, most features should work.");
        } else if (serverName.contains("purpur")) {
            getLogger().info("[VerifyMC] Detected Purpur: Fully supported.");
        } else if (serverName.contains("paper")) {
            getLogger().info("[VerifyMC] Detected Paper: Fully supported.");
        } else if (serverName.contains("spigot")) {
            getLogger().info("[VerifyMC] Detected Spigot: Fully supported.");
        } else if (serverName.contains("bukkit")) {
            getLogger().info("[VerifyMC] Detected Bukkit: Fully supported.");
        } else if (serverName.contains("velocity")) {
            getLogger().info("[VerifyMC] Detected Velocity proxy: Please ensure plugin is installed on backend servers. Some features may not work on proxy directly.");
        } else if (serverName.contains("waterfall")) {
            getLogger().info("[VerifyMC] Detected Waterfall proxy: Please ensure plugin is installed on backend servers. Some features may not work on proxy directly.");
        } else if (serverName.contains("canvas")) {
            getLogger().info("[VerifyMC] Detected Canvas: Experimental support. Please report issues if any.");
        } else {
            getLogger().info("[VerifyMC] Unknown server type, attempting to run in compatibility mode.");
        }
        getLogger().info(getMessage("plugin.enabled"));
        int pluginId = 26637;
        Metrics metrics = new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
        if (wsServer != null) {
            try {
                wsServer.stop();
            } catch (InterruptedException e) {
                String msg = getMessage("websocket.stop_interrupted") + ": " + e.getMessage();
                getLogger().warning(msg);
                Thread.currentThread().interrupt();
            }
        }
        // 插件关闭时保存数据
        if (userDao != null) userDao.save();
        if (auditDao != null) auditDao.save();
        if ("bukkit".equalsIgnoreCase(whitelistMode) && whitelistJsonSync) {
            syncPluginToWhitelistJson();
        }
        getLogger().info(getMessage("plugin.disabled"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("vmc")) return false;
        String language = getConfigLanguage();
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;
        // 控制台和玩家都可执行
        if (args.length == 0) {
            showHelp(sender, language);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
                showHelp(sender, language);
                break;
            case "reload":
                if (isPlayer && !player.hasPermission("verifymc.admin")) {
                    player.sendMessage("§c" + getMessage("command.no_permission", language));
                    return true;
                }
                reloadPlugin(sender, language);
                break;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage("§e" + getMessage("command.add_usage", language));
                    return true;
                }
                String addName = args[1];
                String addEmail = args[2];
                if (isPlayer) {
                    if (!player.hasPermission("verifymc.admin")) {
                        player.sendMessage("§c" + getMessage("command.no_permission", language));
                        return true;
                    }
                    addWhitelist(player, addName, addEmail, language);
                } else {
                    addWhitelist(sender, addName, addEmail, language);
                }
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("§e" + getMessage("command.remove_usage", language));
                    return true;
                }
                String removeName = args[1];
                if (isPlayer) {
                    if (!player.hasPermission("verifymc.admin")) {
                        player.sendMessage("§c" + getMessage("command.no_permission", language));
                        return true;
                    }
                    removeWhitelist(player, removeName, language);
                } else {
                    removeWhitelist(sender, removeName, language);
                }
                break;
            case "port":
                showPort(sender, language);
                break;
            default:
                showHelp(sender, language);
                break;
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("vmc")) return null;
        java.util.List<String> subCommands = java.util.Arrays.asList("help", "reload", "add", "remove", "port");
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String cmd : subCommands) {
                if (cmd.startsWith(prefix)) result.add(cmd);
            }
            return result;
        }
        return java.util.Collections.emptyList();
    }

    // 支持控制台和玩家
    private void showHelp(CommandSender sender, String language) {
        sender.sendMessage("§6=== VerifyMC " + getMessage("command.help.title", language) + " ===\n");
        sender.sendMessage("§e/vmc help §7- " + getMessage("command.help.help", language) + "\n");
        sender.sendMessage("§e/vmc port §7- " + getMessage("command.help.port", language) + "\n");
        sender.sendMessage("§e/vmc reload §7- " + getMessage("command.help.reload", language) + "\n");
        sender.sendMessage("§e/vmc add <" + getMessage("command.help.player", language) + "> §7- " + getMessage("command.help.add", language) + "\n");
        sender.sendMessage("§e/vmc remove <" + getMessage("command.help.player", language) + "> §7- " + getMessage("command.help.remove", language) + "\n");
    }

    private void reloadPlugin(CommandSender sender, String language) {
        try {
            // 检查theme是否变更
            String oldTheme = getConfig().getString("frontend.theme", "default");
            File configFile = new File(getDataFolder(), "config.yml");
            String configContent = new String(java.nio.file.Files.readAllBytes(configFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String newTheme = oldTheme;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("frontend.theme\\s*:\\s*(\\w+)").matcher(configContent);
            if (m.find()) newTheme = m.group(1);
            boolean themeChanged = !oldTheme.equals(newTheme);
            sender.sendMessage("§a" + getMessage("command.restart_starting", language));
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    Bukkit.getPluginManager().disablePlugin(this);
                    Bukkit.getPluginManager().enablePlugin(this);
                    sender.sendMessage("§a" + getMessage("command.restart_success", language));
                    if (themeChanged) {
                        sender.sendMessage("§e" + getMessage("command.theme_reload_hint", language));
                    }
                } catch (Exception e) {
                    sender.sendMessage("§c" + getMessage("command.restart_failed", language) + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            sender.sendMessage("§c" + getMessage("command.restart_failed", language) + ": " + e.getMessage());
        }
    }

    // 新增: 支持邮箱的addWhitelist
    private void addWhitelist(CommandSender sender, String targetName, String email, String language) {
        addWhitelist(sender, targetName, email, null, language);
    }
    
    /**
     * 添加用户到白名单（支持密码）
     * @param sender 命令发送者
     * @param targetName 目标用户名
     * @param email 邮箱
     * @param password 密码（可选）
     * @param language 语言
     */
    private void addWhitelist(CommandSender sender, String targetName, String email, String password, String language) {
        try {
            // 验证密码格式（如果提供了密码）
            if (password != null && !password.isEmpty() && authmeService.isAuthmeEnabled()) {
                if (!authmeService.isValidPassword(password)) {
                    String passwordRegex = getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$");
                    sender.sendMessage("§c" + getMessage("command.invalid_password", language).replace("{regex}", passwordRegex));
                    return;
                }
            }
            
            // 设置玩家为白名单
            Bukkit.getOfflinePlayer(targetName).setWhitelisted(true);
            String uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();
            Map<String, Object> user = userDao.getUserByUuid(uuid);
            boolean ok;
            
            if (user != null) {
                // 用户已存在，更新状态为approved
                ok = userDao.updateUserStatus(uuid, "approved");
                // 如果提供了密码，更新密码
                if (password != null && !password.isEmpty()) {
                    userDao.updateUserPassword(uuid, password);
                    // 如果启用了Authme集成，注册到Authme
                    if (authmeService.isAuthmeEnabled()) {
                        authmeService.registerToAuthme(targetName, password);
                        sender.sendMessage("§a已将用户 " + targetName + " 注册到Authme");
                    }
                }
            } else {
                // 用户不存在，注册新用户（状态为approved）
                if (password != null && !password.isEmpty()) {
                    ok = userDao.registerUser(uuid, targetName, email, "approved", password);
                    // 如果启用了Authme集成，注册到Authme
                    if (authmeService.isAuthmeEnabled()) {
                        authmeService.registerToAuthme(targetName, password);
                        sender.sendMessage("§a已将用户 " + targetName + " 注册到Authme");
                    }
                } else {
                    ok = userDao.registerUser(uuid, targetName, email, "approved");
                }
            }
            
            userDao.save();
            
            // 立即同步到whitelist.json（如果启用）
            if ("bukkit".equalsIgnoreCase(whitelistMode) && whitelistJsonSync) {
                syncPluginToWhitelistJson();
            }
            
            // 同步到服务器白名单
            syncWhitelistToServer();
            
            // WebSocket通知
            if (wsServer != null) {
                wsServer.broadcastMessage("{\"type\":\"user_update\"}");
            }
            
            if (ok) {
                sender.sendMessage("§a" + getMessage("command.add_success", language).replace("{player}", targetName));
            } else {
                sender.sendMessage("§c" + getMessage("command.add_failed", language));
            }
        } catch (Exception e) {
            sender.sendMessage("§c" + getMessage("command.add_failed", language) + ": " + e.getMessage());
        }
    }
    
    // 修改: removeWhitelist时同步删除userDao
    private void removeWhitelist(CommandSender sender, String targetName, String language) {
        try {
            Bukkit.getOfflinePlayer(targetName).setWhitelisted(false);
            // 优先用用户名查找用户
            Map<String, Object> user = userDao.getUserByUsername(targetName);
            String uuid = null;
            if (user != null && user.get("uuid") != null) {
                uuid = user.get("uuid").toString();
            } else {
                // 兼容老数据或uuid算法差异
                uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();
            }
            
            // 如果启用了Authme集成且配置了自动注销，从Authme注销用户
            if (authmeService.isAuthmeEnabled() && authmeService.isAutoUnregisterEnabled()) {
                authmeService.unregisterFromAuthme(targetName);
            }
            
            boolean ok = userDao.deleteUser(uuid);
            userDao.save();
            if (ok) {
            sender.sendMessage("§a" + getMessage("command.remove_success", language).replace("{player}", targetName));
                if (wsServer != null) wsServer.broadcastMessage("{\"type\":\"user_update\"}");
            } else {
                sender.sendMessage("§c" + getMessage("command.remove_failed", language));
            }
        } catch (Exception e) {
            sender.sendMessage("§c" + getMessage("command.remove_failed", language) + ": " + e.getMessage());
        }
    }

    private void showPort(CommandSender sender, String language) {
        int port = getConfig().getInt("web_port", 8080);
        sender.sendMessage("§a" + getMessage("command.port_info", language).replace("{port}", String.valueOf(port)));
    }

    // plugin模式下拦截未注册玩家
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!"plugin".equalsIgnoreCase(whitelistMode)) return;
        Player player = event.getPlayer();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        java.util.List<String> bypassIps = getConfig().getStringList("whitelist_bypass_ips");
        if (bypassIps.contains(ip)) return; // 免验证
        // 改为检测玩家名（id）
        Map<String, Object> user = userDao != null ? userDao.getAllUsers().stream()
            .filter(u -> player.getName().equalsIgnoreCase((String)u.get("username")) && "approved".equals(u.get("status")))
            .findFirst().orElse(null) : null;
        if (user == null) {
            String language = getConfigLanguage();
            String url = webRegisterUrl;
            String msg;
            if ("en".equals(language)) {
                msg = "§c[ VerifyMC ]\n§7Please visit §a" + url + " §7to register";
            } else {
                msg = "§c[ VerifyMC ]\n§7请访问 §a" + url + " §7进行注册";
            }
            player.kickPlayer(msg);
        }
    }

    // bukkit模式下监听 whitelist.json 变更
    private void startWhitelistJsonWatcher() {
        if (whitelistJsonPath == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (Files.exists(whitelistJsonPath)) {
                        long modified = Files.getLastModifiedTime(whitelistJsonPath).toMillis();
                        if (modified != lastWhitelistJsonModified) {
                            lastWhitelistJsonModified = modified;
                            syncWhitelistJsonToPlugin();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskTimerAsynchronously(this, 40L, 100L); // 每5秒检测一次
    }

    // 将 whitelist.json 同步到插件 data
    private void syncWhitelistJsonToPlugin() {
        try {
            List<String> lines = Files.readAllLines(whitelistJsonPath);
            String json = String.join("\n", lines);
            List<Map<String, Object>> list = new Gson().fromJson(json, List.class);
            for (Map<String, Object> entry : list) {
                String uuid = (String) entry.get("uuid");
                if (uuid != null) {
                    Map<String, Object> user = userDao.getAllUsers().stream().filter(u -> uuid.equals(u.get("uuid"))).findFirst().orElse(null);
                    if (user != null) {
                        Object whitelisted = entry.get("whitelisted");
                        // Only update status if user is currently pending and whitelist.json shows approved
                        // This prevents overriding manually set statuses
                        String currentStatus = (String) user.get("status");
                        if ("pending".equals(currentStatus) && Boolean.TRUE.equals(whitelisted)) {
                            user.put("status", "approved");
                        } else if (!"approved".equals(currentStatus) && !"banned".equals(currentStatus) && !Boolean.TRUE.equals(whitelisted)) {
                            // Only set to pending if not already explicitly set
                            user.put("status", "pending");
                        }
                    }
                }
            }
            userDao.save();
        } catch (Exception ignored) {}
    }

    // 插件 data 变更时同步到 whitelist.json
    private void syncPluginToWhitelistJson() {
        if (!"bukkit".equalsIgnoreCase(whitelistMode)) return;
        try {
            List<Map<String, Object>> users = userDao.getAllUsers();
            List<Map<String, Object>> wl = new java.util.ArrayList<>();
            for (Map<String, Object> user : users) {
                if ("approved".equals(user.get("status"))) {
                    Map<String, Object> entry = new java.util.HashMap<>();
                    entry.put("uuid", user.get("uuid"));
                    entry.put("name", user.get("username"));
                    entry.put("whitelisted", true);
                    wl.add(entry);
                }
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(wl);
            Files.write(whitelistJsonPath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static final String USERNAME_REGEX_KEY = "username_regex";
    private static final String USERNAME_CASE_SENSITIVE_KEY = "username_case_sensitive";
    private static final String USERNAME_INVALID_KEY = "username.invalid";
    private static final String USERNAME_CASE_CONFLICT_KEY = "username.case_conflict";

    public boolean isValidUsername(String username) {
        String regex = getConfig().getString(USERNAME_REGEX_KEY, "^[a-zA-Z0-9_-]{3,16}$");
        return username != null && username.matches(regex);
    }
    public boolean isUsernameCaseConflict(String username) {
        boolean caseSensitive = getConfig().getBoolean(USERNAME_CASE_SENSITIVE_KEY, false);
        if (caseSensitive) return false;
        for (Map<String, Object> user : userDao.getAllUsers()) {
            String exist = (String) user.get("username");
            if (exist != null && exist.equalsIgnoreCase(username) && !exist.equals(username)) return true;
        }
        return false;
    }

    private void syncWhitelistToServer() {
        for (Map<String, Object> user : userDao.getAllUsers()) {
            String name = (String) user.get("username");
            String status = (String) user.get("status");
            if ("approved".equals(status)) {
                Bukkit.getOfflinePlayer(name).setWhitelisted(true);
            } else if ("banned".equals(status)) {
                Bukkit.getOfflinePlayer(name).setWhitelisted(false);
            }
        }
    }
    private void cleanupServerWhitelist() {
        for (org.bukkit.OfflinePlayer p : Bukkit.getWhitelistedPlayers()) {
            Map<String, Object> user = userDao.getUserByUsername(p.getName());
            if (user != null && "approved".equals(user.get("status"))) {
                p.setWhitelisted(false);
            }
        }
    }

    public void autoMigrateIfNeeded(ResourceBundle messages) {
        boolean autoMigrateOnSwitch = getConfig().getBoolean("storage.auto_migrate_on_switch", false);
        String storageType = getConfig().getString("storage.type", "data");
        if (autoMigrateOnSwitch) {
            if ("mysql".equalsIgnoreCase(storageType) && userDao instanceof MysqlUserDao) {
                // data -> mysql
                List<Map<String, Object>> fileUsers = new FileUserDao(new File(getDataFolder(), "data/users.json"), this).getAllUsers();
                List<Map<String, Object>> mysqlUsers = userDao.getAllUsers();
                if (!fileUsers.equals(mysqlUsers)) {
                    for (Map<String, Object> user : fileUsers) {
                        userDao.registerUser(
                            (String) user.get("uuid"),
                            (String) user.get("username"),
                            (String) user.get("email"),
                            (String) user.get("status")
                        );
                    }
                    getLogger().info(messages.getString("storage.migrate.success"));
                }
            } else if ("data".equalsIgnoreCase(storageType) && userDao instanceof FileUserDao) {
                // mysql -> data
                try {
                    List<Map<String, Object>> mysqlUsers = new MysqlUserDao(getMysqlConfig(), messages, this).getAllUsers();
                    List<Map<String, Object>> fileUsers = userDao.getAllUsers();
                    if (!mysqlUsers.equals(fileUsers)) {
                        for (Map<String, Object> user : mysqlUsers) {
                            userDao.registerUser(
                                (String) user.get("uuid"),
                                (String) user.get("username"),
                                (String) user.get("email"),
                                (String) user.get("status")
                            );
                        }
                        getLogger().info(messages.getString("storage.migrate.success"));
                    }
                } catch (Exception e) {
                    getLogger().severe(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
                }
            }
        }
    }
    private Properties getMysqlConfig() {
        Properties mysqlConfig = new Properties();
        mysqlConfig.setProperty("host", getConfig().getString("storage.mysql.host"));
        mysqlConfig.setProperty("port", String.valueOf(getConfig().getInt("storage.mysql.port")));
        mysqlConfig.setProperty("database", getConfig().getString("storage.mysql.database"));
        mysqlConfig.setProperty("user", getConfig().getString("storage.mysql.user"));
        mysqlConfig.setProperty("password", getConfig().getString("storage.mysql.password"));
        return mysqlConfig;
    }
} 