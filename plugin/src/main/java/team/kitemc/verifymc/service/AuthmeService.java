package team.kitemc.verifymc.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.util.PluginScheduler;
import team.kitemc.verifymc.util.PasswordUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class AuthmeService {
    private final Plugin plugin;
    private final boolean debug;
    private UserDao userDao;

    public AuthmeService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    public boolean isAuthmeEnabled() {
        return plugin.getConfig().getBoolean("authme.enabled", false);
    }

    public boolean isPasswordRequired() {
        return plugin.getConfig().getBoolean("authme.require_password", false);
    }

    public String getAuthmePassword(String username) {
        if (!isAuthmeEnabled() || username == null || username.isEmpty()) {
            return null;
        }
        String sql = "SELECT " + passwordColumn() + " FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            debugLog("Failed to get AuthMe password for " + username + ": " + e.getMessage());
        }
        return null;
    }

    public boolean hasAuthmeUser(String username) {
        if (!isAuthmeEnabled() || username == null || username.isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            debugLog("Failed to check AuthMe user " + username + ": " + e.getMessage());
        }
        return false;
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{8,26}$");
        return Pattern.matches(regex, password);
    }

    public boolean registerToAuthme(String username, String password) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping registration");
            return false;
        }

        return upsertAuthmeUser(username, password);
    }

    public boolean unregisterFromAuthme(String username) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping unregistration");
            return false;
        }

        return deleteAuthmeUser(username);
    }

    public boolean changePasswordInAuthme(String username, String newPassword) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping password change");
            return false;
        }

        return updateAuthmePassword(username, newPassword);
    }

    public boolean changePassword(String username, String newPassword) {
        return changePasswordInAuthme(username, newPassword);
    }

    public String encodePasswordForStorage(String plainOrEncodedPassword) {
        return buildStoredPassword(plainOrEncodedPassword);
    }

    public void syncApprovedUsers() {
        if (!isAuthmeEnabled() || userDao == null) {
            return;
        }
        try {
            boolean usernameCaseSensitive = plugin.getConfig().getBoolean("username_case_sensitive", false);
            List<Map<String, Object>> localUsers = userDao.getAllUsers();
            Map<String, Map<String, Object>> localByLowerName = new HashMap<>();
            Map<String, Map<String, Object>> localByName = new HashMap<>();
            for (Map<String, Object> u : localUsers) {
                String username = (String) u.get("username");
                if (username != null) {
                    localByLowerName.put(username.toLowerCase(), u);
                    localByName.put(username, u);
                }
            }

            Map<String, AuthmeProfile> authmeProfilesByName = listAuthmeProfiles();
            Map<String, String> authmeByLowerName = new HashMap<>();
            for (String name : authmeProfilesByName.keySet()) {
                authmeByLowerName.put(name.toLowerCase(), name);
            }

            for (Map<String, Object> local : localUsers) {
                String status = (String) local.get("status");
                String username = (String) local.get("username");
                String password = (String) local.get("password");
                String email = (String) local.get("email");
                if (username == null || !"approved".equals(status)) {
                    continue;
                }
                String authName = usernameCaseSensitive ? username : authmeByLowerName.get(username.toLowerCase());
                if (usernameCaseSensitive && !authmeProfilesByName.containsKey(authName)) {
                    authName = null;
                }
                if (authName == null && password != null && !password.trim().isEmpty()) {
                    upsertAuthmeUser(username, password);
                    continue;
                }

                if (authName != null) {
                    AuthmeProfile profile = authmeProfilesByName.get(authName);
                    String authPassword = profile != null ? profile.password : null;
                    String authEmail = profile != null ? profile.email : null;

                    if (password != null && !password.trim().isEmpty() && (authPassword == null || authPassword.trim().isEmpty())) {
                        updateAuthmePassword(authName, password);
                    }

                    if (email != null && !email.trim().isEmpty() && (authEmail == null || authEmail.trim().isEmpty() || !email.equalsIgnoreCase(authEmail))) {
                        updateAuthmeEmail(authName, email);
                    }
                }
            }

            for (Map.Entry<String, AuthmeProfile> entry : authmeProfilesByName.entrySet()) {
                String authName = entry.getKey();
                AuthmeProfile profile = entry.getValue();
                String authPassword = profile != null ? profile.password : null;
                String authEmail = profile != null ? profile.email : null;
                Map<String, Object> local = usernameCaseSensitive
                        ? localByName.get(authName)
                        : localByLowerName.get(authName.toLowerCase());
                if (local == null) {
                    String localEmail = authEmail != null ? authEmail : "";
                    if (authPassword != null && !authPassword.trim().isEmpty()) {
                        userDao.registerUser(authName, localEmail, "approved", authPassword);
                    } else {
                        userDao.registerUser(authName, localEmail, "approved");
                    }
                    PluginScheduler.runGlobal(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + authName));
                    continue;
                }
                String status = (String) local.get("status");
                if (!"approved".equals(status) && !"banned".equals(status)) {
                    userDao.updateUserStatus(authName, "approved");
                    PluginScheduler.runGlobal(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + authName));
                }

                if (authPassword != null && !authPassword.trim().isEmpty()) {
                    String localPassword = (String) local.get("password");
                    if (localPassword == null || localPassword.trim().isEmpty() || !authPassword.equals(localPassword)) {
                        userDao.updateUserPassword(authName, authPassword);
                    }
                }

                if (authEmail != null && !authEmail.trim().isEmpty()) {
                    String localEmail = (String) local.get("email");
                    if (localEmail == null || localEmail.trim().isEmpty() || !authEmail.equalsIgnoreCase(localEmail)) {
                        userDao.updateUserEmail(authName, authEmail);
                    }
                }
            }
            userDao.save();
        } catch (Exception e) {
            debugLog("Failed syncApprovedUsers: " + e.getMessage());
        }
    }

    private Connection getAuthmeConnection() throws Exception {
        String type = plugin.getConfig().getString("authme.database.type", "sqlite").toLowerCase();
        if ("sqlite".equals(type)) {
            Class.forName("org.sqlite.JDBC");
            String path = plugin.getConfig().getString("authme.database.sqlite.path", "plugins/AuthMe/authme.db");
            return DriverManager.getConnection("jdbc:sqlite:" + path);
        }

        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = plugin.getConfig().getString("authme.database.mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("authme.database.mysql.port", 3306);
        String database = plugin.getConfig().getString("authme.database.mysql.database", "authme");
        String user = plugin.getConfig().getString("authme.database.mysql.user", "root");
        String password = plugin.getConfig().getString("authme.database.mysql.password", "");
        boolean useSSL = plugin.getConfig().getBoolean("authme.database.mysql.useSSL", true);
        boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("authme.database.mysql.allowPublicKeyRetrieval", false);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL +
                "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval +
                "&characterEncoding=utf8" +
                "&connectTimeout=5000" +
                "&socketTimeout=10000";
        return DriverManager.getConnection(url, user, password);
    }

    private static final java.util.regex.Pattern SAFE_SQL_IDENTIFIER = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    private String tableName() {
        String name = plugin.getConfig().getString("authme.database.table", "authme");
        if (!SAFE_SQL_IDENTIFIER.matcher(name).matches()) {
            debugLog("Unsafe table name in config: " + name + ", falling back to 'authme'");
            return "authme";
        }
        return name;
    }

    private String column(String key, String def) {
        String col = plugin.getConfig().getString("authme.database.columns." + key, def);
        if (col != null && !col.isEmpty() && !SAFE_SQL_IDENTIFIER.matcher(col).matches()) {
            debugLog("Unsafe column name in config: " + col + ", falling back to '" + def + "'");
            return def;
        }
        return col;
    }

    private String nameColumn() {
        return column("mySQLColumnName", "username");
    }

    private String passwordColumn() {
        return column("mySQLColumnPassword", "password");
    }

    private String saltColumn() {
        return column("mySQLColumnSalt", "");
    }

    private boolean hasSaltColumn() {
        String saltCol = saltColumn();
        return saltCol != null && !saltCol.trim().isEmpty();
    }

    private static final class AuthmeProfile {
        private final String password;
        private final String email;

        private AuthmeProfile(String password, String email) {
            this.password = password;
            this.email = email;
        }
    }

    private Map<String, AuthmeProfile> listAuthmeProfiles() throws Exception {
        Map<String, AuthmeProfile> result = new HashMap<>();
        String sql = "SELECT " + nameColumn() + ", " + passwordColumn() + ", " + column("mySQLColumnEmail", "email") + " FROM " + tableName();
        try (Connection conn = getAuthmeConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String username = rs.getString(1);
                if (username != null) {
                    result.put(username, new AuthmeProfile(rs.getString(2), rs.getString(3)));
                }
            }
        }
        return result;
    }

    private boolean upsertAuthmeUser(String username, String password) {
        String nameCol = nameColumn();
        String passCol = passwordColumn();
        String realNameCol = column("mySQLRealName", "realname");
        String regDateCol = column("mySQLColumnRegisterDate", "regdate");
        String lastLoginCol = column("mySQLColumnLastLogin", "lastlogin");
        String ipCol = column("mySQLColumnIp", "ip");
        String regIpCol = column("mySQLColumnRegisterIp", "regip");
        String loggedCol = column("mySQLColumnLogged", "isLogged");
        String hasSessionCol = column("mySQLColumnHasSession", "hasSession");
        String xCol = column("mySQLlastlocX", "x");
        String yCol = column("mySQLlastlocY", "y");
        String zCol = column("mySQLlastlocZ", "z");
        String worldCol = column("mySQLlastlocWorld", "world");
        String yawCol = column("mySQLlastlocYaw", "yaw");
        String pitchCol = column("mySQLlastlocPitch", "pitch");
        String emailCol = column("mySQLColumnEmail", "email");
        String saltCol = saltColumn();

        String selectSql = "SELECT " + nameCol + " FROM " + tableName() + " WHERE " + nameCol + " = ?";

        StringBuilder updateSql = new StringBuilder("UPDATE " + tableName() + " SET " + passCol + " = ?, "
            + realNameCol + " = ?, " + regDateCol + " = ?, " + lastLoginCol + " = ?, "
            + ipCol + " = ?, " + regIpCol + " = ?, " + loggedCol + " = 0, " + hasSessionCol + " = 0");
        if (hasSaltColumn()) {
            updateSql.append(", ").append(saltCol).append(" = ?");
        }
        updateSql.append(" WHERE ").append(nameCol).append(" = ?");

        StringBuilder insertColumns = new StringBuilder(nameCol + ", " + realNameCol + ", " + passCol + ", "
            + regDateCol + ", " + lastLoginCol + ", " + ipCol + ", " + regIpCol + ", " + loggedCol
            + ", " + hasSessionCol + ", " + xCol + ", " + yCol + ", " + zCol + ", " + worldCol
            + ", " + yawCol + ", " + pitchCol + ", " + emailCol);
        StringBuilder insertValues = new StringBuilder("?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, 0, 0, ?");
        if (hasSaltColumn()) {
            insertColumns.append(", ").append(saltCol);
            insertValues.append(", ?");
        }
        String insertSql = "INSERT INTO " + tableName() + " (" + insertColumns + ") VALUES (" + insertValues + ")";

        long now = System.currentTimeMillis() / 1000;
        String loopback = "127.0.0.1";
        String storedPassword = buildStoredPassword(password);

        try (Connection conn = getAuthmeConnection();
             PreparedStatement select = conn.prepareStatement(selectSql)) {
            select.setString(1, username);
            boolean exists;
            try (ResultSet rs = select.executeQuery()) {
                exists = rs.next();
            }

            if (exists) {
                try (PreparedStatement update = conn.prepareStatement(updateSql.toString())) {
                    int idx = 1;
                    update.setString(idx++, storedPassword);
                    update.setString(idx++, username);
                    update.setLong(idx++, now);
                    update.setLong(idx++, now);
                    update.setString(idx++, loopback);
                    update.setString(idx++, loopback);
                    if (hasSaltColumn()) {
                        update.setString(idx++, "");
                    }
                    update.setString(idx, username);
                    return update.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    int idx = 1;
                    insert.setString(idx++, username);
                    insert.setString(idx++, username);
                    insert.setString(idx++, storedPassword);
                    insert.setLong(idx++, now);
                    insert.setLong(idx++, now);
                    insert.setString(idx++, loopback);
                    insert.setString(idx++, loopback);
                    insert.setString(idx++, "world");
                    insert.setString(idx++, "");
                    if (hasSaltColumn()) {
                        insert.setString(idx++, "");
                    }
                    return insert.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            debugLog("Failed to upsert AuthMe user " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean deleteAuthmeUser(String username) {
        String sql = "DELETE FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            debugLog("Failed to delete AuthMe user " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean updateAuthmeEmail(String username, String email) {
        String sql = "UPDATE " + tableName() + " SET " + column("mySQLColumnEmail", "email") + " = ? WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            debugLog("Failed to update AuthMe email " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean updateAuthmePassword(String username, String newPassword) {
        String passCol = passwordColumn();
        String nameCol = nameColumn();
        String sql;
        if (hasSaltColumn()) {
            sql = "UPDATE " + tableName() + " SET " + passCol + " = ?, " + saltColumn() + " = ? WHERE " + nameCol + " = ?";
        } else {
            sql = "UPDATE " + tableName() + " SET " + passCol + " = ? WHERE " + nameCol + " = ?";
        }
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildStoredPassword(newPassword));
            if (hasSaltColumn()) {
                ps.setString(2, "");
                ps.setString(3, username);
            } else {
                ps.setString(2, username);
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            debugLog("Failed to update AuthMe password " + username + ": " + e.getMessage());
            return false;
        }
    }

    private String buildStoredPassword(String plainPassword) {
        if (plainPassword == null) {
            return null;
        }
        if (PasswordUtil.isHashed(plainPassword)) {
            return plainPassword;
        }
        return PasswordUtil.hash(plainPassword);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AuthmeService: " + msg);
        }
    }
}
