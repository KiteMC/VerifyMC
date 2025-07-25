package team.kitemc.verifymc.db;

import java.io.*;
import java.util.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

public class FileAuditDao implements AuditDao {
    private final File file;
    private final List<Map<String, Object>> audits = new ArrayList<>();
    private final Gson gson = new Gson();

    public FileAuditDao(File dataFile) {
        this.file = dataFile;
        load();
    }

    public synchronized void load() {
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            List<Map<String, Object>> loaded = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (loaded != null) audits.addAll(loaded);
        } catch (Exception ignored) {}
    }

    @Override
    public synchronized void save() {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(audits, writer);
        } catch (Exception ignored) {}
    }

    @Override
    public void addAudit(Map<String, Object> audit) {
        audits.add(audit);
        save();
    }

    @Override
    public List<Map<String, Object>> getAllAudits() {
        return new ArrayList<>(audits);
    }
} 