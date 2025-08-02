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
            // 创建用户表（如果不存在）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(32) NOT NULL," +
                    "email VARCHAR(64)," +
                    "status VARCHAR(16)," +
                    "password VARCHAR(255)," +
                    "regTime BIGINT)");
            
            // 兼容性处理：检查并添加缺失的字段
            try {
                stmt.executeQuery("SELECT password FROM users LIMIT 1");
                debugLog("Password column already exists in users table");
            } catch (SQLException e) {
                // password字段不存在，添加它
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN password VARCHAR(255)");
                debugLog("Added password column to users table");
            }
            
            // 检查regTime字段是否存在
            try {
                stmt.executeQuery("SELECT regTime FROM users LIMIT 1");
                debugLog("regTime column already exists in users table");
            } catch (SQLException e) {
                // regTime字段不存在，添加它
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN regTime BIGINT");
                debugLog("Added regTime column to users table");
                
                // 为现有记录设置默认的regTime值
                stmt.executeUpdate("UPDATE users SET regTime = " + System.currentTimeMillis() + " WHERE regTime IS NULL");
                debugLog("Updated existing records with default regTime value");
            }
            
            // 检查并确保索引存在
            try {
                stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'idx_username'");
                debugLog("Username index already exists");
            } catch (SQLException e) {
                // 添加username索引以提高查询性能
                stmt.executeUpdate("CREATE INDEX idx_username ON users(username)");
                debugLog("Added username index to users table");
            }
            
            try {
                stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'idx_email'");
                debugLog("Email index already exists");
            } catch (SQLException e) {
                // 添加email索引以提高查询性能
                stmt.executeUpdate("CREATE INDEX idx_email ON users(email)");
                debugLog("Added email index to users table");
            }
        }
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] MysqlUserDao: " + msg);
    }

    @Override
    public boolean registerUser(String uuid, String username, String email, String status) {
        // 先检查用户是否已存在
        String checkSql = "SELECT uuid FROM users WHERE uuid = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setString(1, uuid);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                debugLog("User already exists with UUID: " + uuid + ", skipping registration");
                return false;
            }
        } catch (SQLException e) {
            debugLog("Error checking existing user: " + e.getMessage());
            return false;
        }
        
        String sql = "INSERT INTO users (uuid, username, email, status, regTime) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, status);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            debugLog("User registered: " + username);
            return true;
        } catch (SQLException e) {
            debugLog(messages.getString("storage.migrate.fail").replace("{0}", e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean registerUser(String uuid, String username, String email, String status, String password) {
        // 先检查用户是否已存在
        String checkSql = "SELECT uuid FROM users WHERE uuid = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setString(1, uuid);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                debugLog("User already exists with UUID: " + uuid + ", skipping registration");
                return false;
            }
        } catch (SQLException e) {
            debugLog("Error checking existing user: " + e.getMessage());
            return false;
        }
        
        String sql = "INSERT INTO users (uuid, username, email, status, password, regTime) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, status);
            ps.setString(5, password);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            debugLog("User registered with password: " + username);
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
    public boolean updateUserPassword(String uuidOrName, String password) {
        String sql = "UPDATE users SET password=? WHERE uuid=? OR username=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, password);
            ps.setString(2, uuidOrName);
            ps.setString(3, uuidOrName);
            int rows = ps.executeUpdate();
            debugLog("User password updated: " + uuidOrName);
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
                user.put("password", rs.getString("password"));
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
                user.put("password", rs.getString("password"));
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
                    user.put("password", rs.getString("password"));
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
                    user.put("password", rs.getString("password"));
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