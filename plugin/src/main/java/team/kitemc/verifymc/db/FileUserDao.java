package team.kitemc.verifymc.db;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

public class FileUserDao {
    private final File file;
    private final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;

    public FileUserDao(File dataFile, org.bukkit.plugin.Plugin plugin) {
        this.file = dataFile;
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        load();
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] FileUserDao: " + msg);
    }

    // 兼容旧构造函数
    public FileUserDao(File dataFile) {
        this.file = dataFile;
        this.plugin = null;
        this.debug = false;
        load();
    }

    public synchronized void load() {
        debugLog("Loading users from: " + file.getAbsolutePath());
        if (!file.exists()) {
            debugLog("File does not exist, creating new user database");
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Map<String, Map<String, Object>> loaded = gson.fromJson(reader, new TypeToken<Map<String, Map<String, Object>>>(){}.getType());
            if (loaded != null) {
                users.putAll(loaded);
                debugLog("Loaded " + loaded.size() + " users from database");
            } else {
                debugLog("No users found in database");
            }
        } catch (Exception e) {
            debugLog("Error loading users: " + e.getMessage());
        }
    }

    public synchronized void save() {
        debugLog("Saving " + users.size() + " users to: " + file.getAbsolutePath());
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(users, writer);
            debugLog("Save successful");
        } catch (Exception e) {
            debugLog("Error saving users: " + e.getMessage());
        }
    }

    public boolean registerUser(String uuid, String username, String email, String status) {
        debugLog("registerUser called: uuid=" + uuid + ", username=" + username + ", email=" + email + ", status=" + status);
        try {
            Map<String, Object> user = new HashMap<>();
            user.put("uuid", uuid);
            user.put("username", username);
            user.put("email", email);
            user.put("status", status);
            user.put("regTime", System.currentTimeMillis());
            debugLog("Adding user to map: " + user);
            users.put(uuid, user);
            save();
            debugLog("User registration successful");
            return true;
        } catch (Exception e) {
            debugLog("Exception in registerUser: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getPendingUsers() {
        debugLog("Getting pending users");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> user : users.values()) {
            if ("pending".equals(user.get("status"))) result.add(user);
        }
        debugLog("Found " + result.size() + " pending users");
        return result;
    }

    public boolean updateUserStatus(String uuid, String status) {
        debugLog("updateUserStatus called: uuid=" + uuid + ", status=" + status);
        Map<String, Object> user = users.get(uuid);
        if (user == null) {
            debugLog("User not found: " + uuid);
            return false;
        }
        String oldStatus = (String) user.get("status");
        user.put("status", status);
        save();
        debugLog("User status updated: " + uuid + " from " + oldStatus + " to " + status);
        return true;
    }

    public List<Map<String, Object>> getAllUsers() {
        debugLog("Getting all users, total: " + users.size());
        return new ArrayList<>(users.values());
    }

    public List<Map<String, Object>> getApprovedUsers() {
        debugLog("Getting approved users");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> user : users.values()) {
            if ("approved".equals(user.get("status"))) result.add(user);
        }
        debugLog("Found " + result.size() + " approved users");
        return result;
    }

    public Map<String, Object> getUserByUuid(String uuid) {
        debugLog("Getting user by UUID: " + uuid);
        Map<String, Object> user = users.get(uuid);
        if (user != null) {
            debugLog("User found: " + user.get("username"));
        } else {
            debugLog("User not found");
        }
        return user;
    }

    public Map<String, Object> getUserByUsername(String username) {
        debugLog("Getting user by username: " + username);
        for (Map<String, Object> user : users.values()) {
            if (user.get("username") != null && user.get("username").toString().equalsIgnoreCase(username)) {
                debugLog("User found: " + user.get("uuid"));
                return user;
            }
        }
        debugLog("User not found");
        return null;
    }

    public int countUsersByEmail(String email) {
        debugLog("Counting users by email: " + email);
        int count = 0;
        for (Map<String, Object> user : users.values()) {
            if (user.get("email") != null && user.get("email").toString().equalsIgnoreCase(email)) {
                count++;
            }
        }
        debugLog("Found " + count + " users with email: " + email);
        return count;
    }

    public boolean deleteUser(String uuid) {
        debugLog("deleteUser called: uuid=" + uuid);
        try {
            Map<String, Object> removed = users.remove(uuid);
            if (removed != null) {
                debugLog("User deleted: " + removed.get("username"));
                save();
                return true;
            } else {
                debugLog("User not found for deletion");
                return false;
            }
        } catch (Exception e) {
            debugLog("Exception in deleteUser: " + e.getMessage());
            return false;
        }
    }

    // 可扩展更多方法
} 