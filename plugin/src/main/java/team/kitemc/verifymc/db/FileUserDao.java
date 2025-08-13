package team.kitemc.verifymc.db;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

public class FileUserDao implements UserDao {
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

    public FileUserDao(File dataFile) {
        this.file = dataFile;
        this.plugin = null;
        this.debug = false;
        load();
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) plugin.getLogger().info("[DEBUG] FileUserDao: " + msg);
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
                // Compatibility handling: Upgrade old version data format
                boolean hasUpgraded = false;
                for (Map.Entry<String, Map<String, Object>> entry : loaded.entrySet()) {
                    Map<String, Object> user = entry.getValue();
                    if (user != null) {
                        // Check and add missing fields
                        if (!user.containsKey("password")) {
                            user.put("password", null);
                            hasUpgraded = true;
                            debugLog("Added missing password field for user: " + user.get("username"));
                        }
                        
                        // Ensure all required fields exist
                        if (!user.containsKey("uuid")) {
                            user.put("uuid", entry.getKey());
                            hasUpgraded = true;
                            debugLog("Added missing uuid field for user: " + user.get("username"));
                        }
                        
                        if (!user.containsKey("regTime")) {
                            user.put("regTime", System.currentTimeMillis());
                            hasUpgraded = true;
                            debugLog("Added missing regTime field for user: " + user.get("username"));
                        }
                    }
                }
                
                users.putAll(loaded);
                debugLog("Loaded " + loaded.size() + " users from database");
                
                // If data upgrade occurred, save immediately
                if (hasUpgraded) {
                    debugLog("Data format upgraded, saving updated data");
                    save();
                }
            } else {
                debugLog("No users found in database");
            }
        } catch (Exception e) {
            debugLog("Error loading users: " + e.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        debugLog("Saving " + users.size() + " users to: " + file.getAbsolutePath());
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(users, writer);
            debugLog("Save successful");
        } catch (Exception e) {
            debugLog("Error saving users: " + e.getMessage());
        }
    }

    @Override
    public boolean registerUser(String uuid, String username, String email, String status) {
        debugLog("registerUser called: uuid=" + uuid + ", username=" + username + ", email=" + email + ", status=" + status);
        try {
            // Check if user already exists
            if (users.containsKey(uuid)) {
                debugLog("User already exists with UUID: " + uuid + ", skipping registration");
                return false;
            }
            
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

    @Override
    public boolean registerUser(String uuid, String username, String email, String status, String password) {
        debugLog("registerUser with password called: uuid=" + uuid + ", username=" + username + ", email=" + email + ", status=" + status);
        try {
            // Check if user already exists
            if (users.containsKey(uuid)) {
                debugLog("User already exists with UUID: " + uuid + ", skipping registration");
                return false;
            }
            
            Map<String, Object> user = new HashMap<>();
            user.put("uuid", uuid);
            user.put("username", username);
            user.put("email", email);
            user.put("status", status);
            user.put("password", password);
            user.put("regTime", System.currentTimeMillis());
            debugLog("Adding user with password to map: " + user);
            users.put(uuid, user);
            save();
            debugLog("User registration with password successful");
            return true;
        } catch (Exception e) {
            debugLog("Exception in registerUser with password: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getAllUsers() {
        debugLog("Getting all users, total: " + users.size());
        return new ArrayList<>(users.values());
    }

    @Override
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

    @Override
    public boolean updateUserPassword(String uuidOrName, String password) {
        debugLog("updateUserPassword called: uuidOrName=" + uuidOrName);
        Map<String, Object> user = null;
        
        // First try to find as UUID
        user = users.get(uuidOrName);
        
        // If not found, try to find as username
        if (user == null) {
            for (Map<String, Object> u : users.values()) {
                if (u.get("username") != null && u.get("username").toString().equalsIgnoreCase(uuidOrName)) {
                    user = u;
                    break;
                }
            }
        }
        
        if (user == null) {
            debugLog("User not found: " + uuidOrName);
            return false;
        }
        
        user.put("password", password);
        save();
        debugLog("User password updated: " + user.get("username"));
        return true;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public List<Map<String, Object>> getPendingUsers() {
        debugLog("Getting pending users");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> user : users.values()) {
            if ("pending".equals(user.get("status"))) result.add(user);
        }
        debugLog("Found " + result.size() + " pending users");
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getUsersWithPagination(int page, int pageSize) {
        debugLog("Getting users with pagination: page=" + page + ", pageSize=" + pageSize);
        List<Map<String, Object>> allUsers = new ArrayList<>(users.values());
        
        // Sort by registration time (newest first)
        allUsers.sort((a, b) -> {
            Long timeA = (Long) a.get("regTime");
            Long timeB = (Long) b.get("regTime");
            if (timeA == null) timeA = 0L;
            if (timeB == null) timeB = 0L;
            return timeB.compareTo(timeA);
        });
        
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allUsers.size());
        
        if (startIndex >= allUsers.size()) {
            debugLog("Page " + page + " is out of range, returning empty list");
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> result = allUsers.subList(startIndex, endIndex);
        debugLog("Returning " + result.size() + " users for page " + page);
        return result;
    }
    
    @Override
    public int getTotalUserCount() {
        int count = users.size();
        debugLog("Total user count: " + count);
        return count;
    }
    
    @Override
    public List<Map<String, Object>> getUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery) {
        debugLog("Getting users with pagination and search: page=" + page + ", pageSize=" + pageSize + ", query=" + searchQuery);
        List<Map<String, Object>> filteredUsers = new ArrayList<>();
        
        // Filter users based on search query
        String query = searchQuery != null ? searchQuery.toLowerCase().trim() : "";
        for (Map<String, Object> user : users.values()) {
            if (query.isEmpty()) {
                filteredUsers.add(user);
            } else {
                String username = user.get("username") != null ? user.get("username").toString().toLowerCase() : "";
                String email = user.get("email") != null ? user.get("email").toString().toLowerCase() : "";
                if (username.contains(query) || email.contains(query)) {
                    filteredUsers.add(user);
                }
            }
        }
        
        // Sort by registration time (newest first)
        filteredUsers.sort((a, b) -> {
            Long timeA = (Long) a.get("regTime");
            Long timeB = (Long) b.get("regTime");
            if (timeA == null) timeA = 0L;
            if (timeB == null) timeB = 0L;
            return timeB.compareTo(timeA);
        });
        
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredUsers.size());
        
        if (startIndex >= filteredUsers.size()) {
            debugLog("Page " + page + " is out of range for search results, returning empty list");
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> result = filteredUsers.subList(startIndex, endIndex);
        debugLog("Returning " + result.size() + " users for page " + page + " with search query: " + searchQuery);
        return result;
    }
    
    @Override
    public int getTotalUserCountWithSearch(String searchQuery) {
        debugLog("Getting total user count with search: query=" + searchQuery);
        int count = 0;
        String query = searchQuery != null ? searchQuery.toLowerCase().trim() : "";
        
        for (Map<String, Object> user : users.values()) {
            if (query.isEmpty()) {
                count++;
            } else {
                String username = user.get("username") != null ? user.get("username").toString().toLowerCase() : "";
                String email = user.get("email") != null ? user.get("email").toString().toLowerCase() : "";
                if (username.contains(query) || email.contains(query)) {
                    count++;
                }
            }
        }
        
        debugLog("Total user count with search '" + searchQuery + "': " + count);
        return count;
    }
} 