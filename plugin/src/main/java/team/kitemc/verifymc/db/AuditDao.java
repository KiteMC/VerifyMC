package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Map;

public interface AuditDao {
    void addAudit(Map<String, Object> audit);
    List<Map<String, Object>> getAllAudits();
    void save();
} 