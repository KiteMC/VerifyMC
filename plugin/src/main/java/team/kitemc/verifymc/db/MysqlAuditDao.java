package team.kitemc.verifymc.db;

import java.sql.*;
import java.util.*;

public class MysqlAuditDao implements AuditDao {
    private final Connection conn;

    public MysqlAuditDao(Properties mysqlConfig) throws SQLException {
        String url = "jdbc:mysql://" + mysqlConfig.getProperty("host") + ":" +
                mysqlConfig.getProperty("port") + "/" +
                mysqlConfig.getProperty("database") + "?useSSL=false&characterEncoding=utf8";
        conn = DriverManager.getConnection(url, mysqlConfig.getProperty("user"), mysqlConfig.getProperty("password"));
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS audits (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "action VARCHAR(32)," +
                    "operator VARCHAR(32)," +
                    "target VARCHAR(32)," +
                    "detail TEXT," +
                    "timestamp BIGINT)");
        }
    }

    @Override
    public void addAudit(Map<String, Object> audit) {
        String sql = "INSERT INTO audits (action, operator, target, detail, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, (String)audit.get("action"));
            ps.setString(2, (String)audit.get("operator"));
            ps.setString(3, (String)audit.get("target"));
            ps.setString(4, (String)audit.get("detail"));
            ps.setLong(5, (Long)audit.get("timestamp"));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public List<Map<String, Object>> getAllAudits() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM audits";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> audit = new HashMap<>();
                audit.put("id", rs.getInt("id"));
                audit.put("action", rs.getString("action"));
                audit.put("operator", rs.getString("operator"));
                audit.put("target", rs.getString("target"));
                audit.put("detail", rs.getString("detail"));
                audit.put("timestamp", rs.getLong("timestamp"));
                result.add(audit);
            }
        } catch (SQLException ignored) {}
        return result;
    }

    @Override
    public void save() {
        // MySQL storage: save() called (no-op)
    }
} 