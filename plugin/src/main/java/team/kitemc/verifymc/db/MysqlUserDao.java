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
            // Create users table (if not exists)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(32) NOT NULL," +
                    "email VARCHAR(64)," +
                    "status VARCHAR(16)," +
                    "password VARCHAR(255)," +
                    "regTime BIGINT)");
            
            // Compatibility handling: Check and add missing fields
            try {
                stmt.executeQuery("SELECT password FROM users LIMIT 1");
                debugLog("Password column already exists in users table");
            } catch (SQLException e) {
                // password field doesn't exist, add it
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN password VARCHAR(255)");
                debugLog("Added password column to users table");
            }
            
            // Check if regTime field exists
            try {
                stmt.executeQuery("SELECT regTime FROM users LIMIT 1");
                debugLog("regTime column already exists in users table");
            } catch (SQLException e) {
                // regTime field doesn't exist, add it
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN regTime BIGINT");
                debugLog("Added regTime column to users table");
                
                // Set default regTime value for existing records
                stmt.executeUpdate("UPDATE users SET regTime = " + System.currentTimeMillis() + " WHERE regTime IS NULL");
                debugLog("Updated existing records with default regTime value");
            }
            
            // Check and ensure indexes exist
            try {
                stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'idx_username'");
                debugLog("Username index already exists");
            } catch (SQLException e) {
                // Add username index to improve query performance
                stmt.executeUpdate("CREATE INDEX idx_username ON users(username)");
                debugLog("Added username index to users table");
            }
            
            try {
                stmt.executeQuery("SHOW INDEX FROM users WHERE Key_name = 'idx_email'");
                debugLog("Email index already exists");
            } catch (SQLException e) {
                // Add email index to improve query performance
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
        // First check if user already exists
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
        // First check if user already exists
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
        // MySQL implementation can be empty or just log message
        debugLog("MySQL storage: save() called (no-op)");
    }
    
    @Override
    public List<Map<String, Object>> getUsersWithPagination(int page, int pageSize) {
        debugLog("Getting users with pagination: page=" + page + ", pageSize=" + pageSize);
        List<Map<String, Object>> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        
        String sql = "SELECT * FROM users ORDER BY regTime DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        } catch (SQLException e) {
            debugLog("Error getting users with pagination: " + e.getMessage());
        }
        
        debugLog("Returning " + result.size() + " users for page " + page);
        return result;
    }
    
    @Override
    public int getTotalUserCount() {
        debugLog("Getting total user count");
        int count = 0;
        String sql = "SELECT COUNT(*) FROM users";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            debugLog("Error getting total user count: " + e.getMessage());
        }
        
        debugLog("Total user count: " + count);
        return count;
    }
    
    @Override
    public List<Map<String, Object>> getUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery) {
        debugLog("Getting users with pagination and search: page=" + page + ", pageSize=" + pageSize + ", query=" + searchQuery);
        List<Map<String, Object>> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        
        String sql;
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            sql = "SELECT * FROM users ORDER BY regTime DESC LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT * FROM users WHERE LOWER(username) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?) ORDER BY regTime DESC LIMIT ? OFFSET ?";
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
            } else {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
                ps.setInt(3, pageSize);
                ps.setInt(4, offset);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        } catch (SQLException e) {
            debugLog("Error getting users with pagination and search: " + e.getMessage());
        }
        
        debugLog("Returning " + result.size() + " users for page " + page + " with search query: " + searchQuery);
        return result;
    }
    
    @Override
    public int getTotalUserCountWithSearch(String searchQuery) {
        debugLog("Getting total user count with search: query=" + searchQuery);
        int count = 0;
        
        String sql;
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            sql = "SELECT COUNT(*) FROM users";
        } else {
            sql = "SELECT COUNT(*) FROM users WHERE LOWER(username) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?)";
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            debugLog("Error getting total user count with search: " + e.getMessage());
        }
        
        debugLog("Total user count with search '" + searchQuery + "': " + count);
        return count;
    }
    
    @Override
    public int getApprovedUserCount() {
        debugLog("Getting approved user count (excluding pending)");
        int count = 0;
        String sql = "SELECT COUNT(*) FROM users WHERE status != 'pending'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            debugLog("Error getting approved user count: " + e.getMessage());
        }
        
        debugLog("Approved user count: " + count);
        return count;
    }
    
    @Override
    public int getApprovedUserCountWithSearch(String searchQuery) {
        debugLog("Getting approved user count with search: query=" + searchQuery);
        int count = 0;
        
        String sql;
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            sql = "SELECT COUNT(*) FROM users WHERE status != 'pending'";
        } else {
            sql = "SELECT COUNT(*) FROM users WHERE status != 'pending' AND (LOWER(username) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?))";
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            debugLog("Error getting approved user count with search: " + e.getMessage());
        }
        
        debugLog("Approved user count with search '" + searchQuery + "': " + count);
        return count;
    }
    
    @Override
    public List<Map<String, Object>> getApprovedUsersWithPagination(int page, int pageSize) {
        debugLog("Getting approved users with pagination: page=" + page + ", pageSize=" + pageSize);
        List<Map<String, Object>> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        
        String sql = "SELECT * FROM users WHERE status != 'pending' ORDER BY regTime DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        } catch (SQLException e) {
            debugLog("Error getting approved users with pagination: " + e.getMessage());
        }
        
        debugLog("Returning " + result.size() + " approved users for page " + page);
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getApprovedUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery) {
        debugLog("Getting approved users with pagination and search: page=" + page + ", pageSize=" + pageSize + ", query=" + searchQuery);
        List<Map<String, Object>> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        
        String sql;
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            sql = "SELECT * FROM users WHERE status != 'pending' ORDER BY regTime DESC LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT * FROM users WHERE status != 'pending' AND (LOWER(username) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?)) ORDER BY regTime DESC LIMIT ? OFFSET ?";
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
            } else {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
                ps.setInt(3, pageSize);
                ps.setInt(4, offset);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        } catch (SQLException e) {
            debugLog("Error getting approved users with pagination and search: " + e.getMessage());
        }
        
        debugLog("Returning " + result.size() + " approved users for page " + page + " with search query: " + searchQuery);
        return result;
    }
} 