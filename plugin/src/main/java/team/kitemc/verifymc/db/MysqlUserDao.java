package team.kitemc.verifymc.db;

import java.sql.*;
import java.util.*;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.ConcurrentHashMap;

public class MysqlUserDao implements UserDao {
    private final Connection conn;
    private final ResourceBundle messages;
    private final boolean debug;
    private final Plugin plugin;

    public MysqlUserDao(Properties mysqlConfig, ResourceBundle messages, Plugin plugin) throws SQLException {
        this.messages = messages;
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        String url = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") + "?useSSL=false&characterEncoding=utf8";
        conn = DriverManager.getConnection(url, mysqlConfig.getProperty("user"), mysqlConfig.getProperty("password"));
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(32) NOT NULL," +
                    "email VARCHAR(64)," +
                    "status VARCHAR(16)," +
                    "regTime BIGINT)");
        }
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] MysqlUserDao: " + msg);
    }

    @Override
    public boolean registerUser(String uuid, String username, String email, String status) {
        String sql = "INSERT INTO users (uuid, username, email, status, regTime) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE username=?, email=?, status=?, regTime=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, status);
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, username);
            ps.setString(7, email);
            ps.setString(8, status);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
            debugLog("User registered: " + username);
            return true;
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean updateUserStatus(String uuidOrName, String status) {
        String sql = "UPDATE users SET status=? WHERE uuid=? OR username=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, uuidOrName);
            ps.setString(3, uuidOrName);
            int rows = ps.executeUpdate();
            debugLog("User status updated: " + uuidOrName + " to " + status);
            return rows > 0;
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getAllUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("uuid", rs.getString("uuid"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("status", rs.getString("status"));
                user.put("regTime", rs.getLong("regTime"));
                result.add(user);
            }
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getPendingUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE status='pending'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("uuid", rs.getString("uuid"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("status", rs.getString("status"));
                user.put("regTime", rs.getLong("regTime"));
                result.add(user);
            }
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
        }
        return result;
    }

    @Override
    public Map<String, Object> getUserByUuid(String uuid) {
        String sql = "SELECT * FROM users WHERE uuid=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("uuid", rs.getString("uuid"));
                    user.put("username", rs.getString("username"));
                    user.put("email", rs.getString("email"));
                    user.put("status", rs.getString("status"));
                    user.put("regTime", rs.getLong("regTime"));
                    return user;
                }
            }
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
        }
        return null;
    }

    @Override
    public Map<String, Object> getUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE LOWER(username)=LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("uuid", rs.getString("uuid"));
                    user.put("username", rs.getString("username"));
                    user.put("email", rs.getString("email"));
                    user.put("status", rs.getString("status"));
                    user.put("regTime", rs.getLong("regTime"));
                    return user;
                }
            }
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
        }
        return null;
    }

    @Override
    public boolean deleteUser(String uuidOrName) {
        String sql = "DELETE FROM users WHERE uuid=? OR username=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidOrName);
            ps.setString(2, uuidOrName);
            int rows = ps.executeUpdate();
            debugLog("User deleted: " + uuidOrName);
            return rows > 0;
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
            return false;
        }
    }

    @Override
    public int countUsersByEmail(String email) {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(email)=LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
        }
        return count;
    }

    @Override
    public void save() {
        // MySQL 实现可为空或仅日志提示
        debugLog("MySQL storage: save() called (no-op)");
    }
} 