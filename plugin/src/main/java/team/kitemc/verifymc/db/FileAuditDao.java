package team.kitemc.verifymc.db;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

public class FileAuditDao {
    private final File file;
    private final List<Map<String, Object>> audits = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;

    public FileAuditDao(File dataFile, org.bukkit.plugin.Plugin plugin) {
        this.file = dataFile;
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        load();
    }

    // 兼容旧构造函数
    public FileAuditDao(File dataFile) {
        this.file = dataFile;
        this.plugin = null;
        this.debug = false;
        load();
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] FileAuditDao: " + msg);
    }

    public synchronized void load() {
        debugLog("Loading audits from: " + file.getAbsolutePath());
        if (!file.exists()) {
            debugLog("File does not exist, creating new audit database");
            return;
        }
        try (Reader reader = new FileReader(file)) {
            List<Map<String, Object>> loaded = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (loaded != null) {
                audits.addAll(loaded);
                debugLog("Loaded " + loaded.size() + " audits from database");
            } else {
                debugLog("No audits found in database");
            }
        } catch (Exception e) {
            debugLog("Error loading audits: " + e.getMessage());
        }
    }

    public synchronized void save() {
        debugLog("Saving " + audits.size() + " audits to: " + file.getAbsolutePath());
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(audits, writer);
            debugLog("Save successful");
        } catch (Exception e) {
            debugLog("Error saving audits: " + e.getMessage());
        }
    }

    public void insertAudit(Map<String, Object> audit) {
        debugLog("Inserting audit: " + audit);
        audits.add(audit);
        save();
        debugLog("Audit inserted successfully");
    }

    public List<Map<String, Object>> getPendingAudits() {
        debugLog("Getting pending audits");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> audit : audits) {
            if (Boolean.FALSE.equals(audit.get("verified"))) result.add(audit);
        }
        debugLog("Found " + result.size() + " pending audits");
        return result;
    }

    public boolean updateAuditStatus(String uuid, boolean verified) {
        debugLog("updateAuditStatus called: uuid=" + uuid + ", verified=" + verified);
        for (Map<String, Object> audit : audits) {
            if (uuid.equals(audit.get("uuid"))) {
                Boolean oldStatus = (Boolean) audit.get("verified");
                audit.put("verified", verified);
                save();
                debugLog("Audit status updated: " + uuid + " from " + oldStatus + " to " + verified);
                return true;
            }
        }
        debugLog("Audit not found: " + uuid);
        return false;
    }

    // 可扩展更多方法
} 