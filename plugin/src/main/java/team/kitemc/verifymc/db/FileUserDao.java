package team.kitemc.verifymc.db;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import team.kitemc.verifymc.util.PasswordUtil;

public class FileUserDao implements UserDao {
    private final File file;
    private final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;
    private volatile boolean dirty = false;
    private volatile boolean running = true;

    public FileUserDao(File dataFile, org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);

        // 验证文件路径在插件数据目录内，防止路径遍历攻击
        try {
            File dataFolder = plugin.getDataFolder();
            String canonicalPath = dataFile.getCanonicalPath();
            String expectedPath = dataFolder.getCanonicalPath();
            if (!canonicalPath.startsWith(expectedPath)) {
                throw new SecurityException("File path must be within plugin data folder: " + canonicalPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate file path", e);
        }

        this.file = dataFile;
        load();
        startFlushThread();
    }

    private void startFlushThread() {
        Thread flushThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    if (dirty) {
                        save();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        flushThread.setDaemon(true);
        flushThread.setName("FileUserDao-Flush");
        flushThread.start();
    }

    /**
     * Mark data as dirty; will be flushed by background thread within 5s.
     */
    private void saveLater() {
        dirty = true;
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) plugin.getLogger().info("[DEBUG] FileUserDao: " + msg);
    }

    private boolean isUsernameCaseSensitive() {
        return plugin != null && plugin.getConfig().getBoolean("username_case_sensitive", false);
    }

    private String keyForUsername(String username) {
        if (username == null) {
            return "";
        }
        return isUsernameCaseSensitive() ? username : username.toLowerCase(Locale.ROOT);
    }

    private String storageKeyForUser(Map<String, Object> user, String fallbackKey) {
        Object username = user != null ? user.get("username") : null;
        if (username != null) {
            return keyForUsername(username.toString());
        }
        return isUsernameCaseSensitive() ? fallbackKey : fallbackKey.toLowerCase(Locale.ROOT);
    }

    @Override
    public long getRegTimeAsLong(Object regTimeValue) {
        if (regTimeValue == null) {
            return 0L;
        }
        if (regTimeValue instanceof Long) {
            return (Long) regTimeValue;
        }
        if (regTimeValue instanceof Double) {
            return ((Double) regTimeValue).longValue();
        }
        if (regTimeValue instanceof Number) {
            return ((Number) regTimeValue).longValue();
        }
        try {
            return Long.parseLong(regTimeValue.toString());
        } catch (NumberFormatException e) {
            debugLog("Failed to convert regTime to Long: " + regTimeValue + ", error: " + e.getMessage());
            return 0L;
        }
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
                boolean hasUpgraded = false;
                for (Map.Entry<String, Map<String, Object>> entry : loaded.entrySet()) {
                    Map<String, Object> user = entry.getValue();
                    if (user != null) {
                        if (!user.containsKey("password")) {
                            user.put("password", null);
                            hasUpgraded = true;
                            debugLog("Added missing password field for user: " + user.get("username"));
                        }

                        if (!user.containsKey("regTime")) {
                            user.put("regTime", System.currentTimeMillis());
                            hasUpgraded = true;
                            debugLog("Added missing regTime field for user: " + user.get("username"));
                        }

                        if (!user.containsKey("discordId") && !user.containsKey("discord_id")) {
                            user.put("discordId", null);
                            hasUpgraded = true;
                            debugLog("Added missing discordId field for user: " + user.get("username"));
                        }
                        if (user.containsKey("discord_id") && !user.containsKey("discordId")) {
                            user.put("discordId", user.remove("discord_id"));
                            hasUpgraded = true;
                        }

                        if (!user.containsKey("questionnaireScore") && !user.containsKey("questionnaire_score")) {
                            user.put("questionnaireScore", null);
                            hasUpgraded = true;
                        }
                        if (user.containsKey("questionnaire_score") && !user.containsKey("questionnaireScore")) {
                            user.put("questionnaireScore", user.remove("questionnaire_score"));
                            hasUpgraded = true;
                        }
                        if (!user.containsKey("questionnairePassed") && !user.containsKey("questionnaire_passed")) {
                            user.put("questionnairePassed", null);
                            hasUpgraded = true;
                        }
                        if (user.containsKey("questionnaire_passed") && !user.containsKey("questionnairePassed")) {
                            user.put("questionnairePassed", user.remove("questionnaire_passed"));
                            hasUpgraded = true;
                        }
                        if (!user.containsKey("questionnaireReviewSummary") && !user.containsKey("questionnaire_review_summary")) {
                            user.put("questionnaireReviewSummary", null);
                            hasUpgraded = true;
                        }
                        if (user.containsKey("questionnaire_review_summary") && !user.containsKey("questionnaireReviewSummary")) {
                            user.put("questionnaireReviewSummary", user.remove("questionnaire_review_summary"));
                            hasUpgraded = true;
                        }
                        if (!user.containsKey("questionnaireScoredAt") && !user.containsKey("questionnaire_scored_at")) {
                            user.put("questionnaireScoredAt", null);
                            hasUpgraded = true;
                        }
                        if (user.containsKey("questionnaire_scored_at") && !user.containsKey("questionnaireScoredAt")) {
                            user.put("questionnaireScoredAt", user.remove("questionnaire_scored_at"));
                            hasUpgraded = true;
                        }
                    }
                }

                users.clear();
                for (Map.Entry<String, Map<String, Object>> entry : loaded.entrySet()) {
                    Map<String, Object> user = entry.getValue();
                    if (user != null) {
                        users.put(storageKeyForUser(user, entry.getKey()), user);
                    }
                }
                debugLog("Loaded " + loaded.size() + " users from database");

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

    public synchronized void rebuildUsernameIndex() {
        Map<String, Map<String, Object>> reindexed = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : users.entrySet()) {
            Map<String, Object> user = entry.getValue();
            if (user != null) {
                reindexed.put(storageKeyForUser(user, entry.getKey()), user);
            }
        }
        users.clear();
        users.putAll(reindexed);
        debugLog("Rebuilt username index for case_sensitive=" + isUsernameCaseSensitive());
    }

    @Override
    public synchronized void save() {
        debugLog("Saving " + users.size() + " users to: " + file.getAbsolutePath());

        // Use temporary file for atomic write operation
        File tempFile = new File(file.getAbsolutePath() + ".tmp");

        try (Writer writer = new FileWriter(tempFile)) {
            gson.toJson(users, writer);
            writer.flush();

            // Atomic rename: tempFile -> target file
            if (!tempFile.renameTo(file)) {
                // If rename fails (e.g., cross-filesystem), try copy and delete
                debugLog("Atomic rename failed, falling back to copy");
                try (java.io.InputStream in = new FileInputStream(tempFile);
                     java.io.OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                if (!tempFile.delete()) {
                    debugLog("Warning: failed to delete temporary file: " + tempFile.getAbsolutePath());
                }
            }

            debugLog("Save successful");
        } catch (Exception e) {
            debugLog("Error saving users: " + e.getMessage());
            // Clean up temp file if it exists
            if (tempFile.exists() && !tempFile.delete()) {
                debugLog("Warning: failed to delete temporary file after error: " + tempFile.getAbsolutePath());
            }
        }
    }


    private void applyQuestionnaireAuditFields(Map<String, Object> user, Integer questionnaireScore, Boolean questionnairePassed,
                                               String questionnaireReviewSummary, Long questionnaireScoredAt) {
        user.put("questionnaireScore", questionnaireScore);
        user.put("questionnairePassed", questionnairePassed);
        user.put("questionnaireReviewSummary", questionnaireReviewSummary);
        user.put("questionnaireScoredAt", questionnaireScoredAt);
    }

    @Override
    public boolean registerUser(String username, String email, String status) {
        return registerUser(username, email, status, null, null, null, null);
    }

    @Override
    public boolean registerUser(String username, String email, String status,
                                Integer questionnaireScore, Boolean questionnairePassed,
                                String questionnaireReviewSummary, Long questionnaireScoredAt) {
        debugLog("registerUser called: username=" + username + ", email=" + email + ", status=" + status);
        try {
            String key = keyForUsername(username);
            if (users.containsKey(key)) {
                debugLog("User already exists with username: " + username + ", skipping registration");
                return false;
            }

            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("email", email);
            user.put("status", status);
            user.put("regTime", System.currentTimeMillis());
            applyQuestionnaireAuditFields(user, questionnaireScore, questionnairePassed, questionnaireReviewSummary, questionnaireScoredAt);
            debugLog("Adding user to map: " + user);
            users.put(key, user);
            saveLater();
            debugLog("User registration successful");
            return true;
        } catch (Exception e) {
            debugLog("Exception in registerUser: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean registerUser(String username, String email, String status, String password) {
        return registerUser(username, email, status, password, null, null, null, null);
    }

    @Override
    public boolean registerUser(String username, String email, String status, String password,
                                Integer questionnaireScore, Boolean questionnairePassed,
                                String questionnaireReviewSummary, Long questionnaireScoredAt) {
        debugLog("registerUser with password called: username=" + username + ", email=" + email + ", status=" + status);
        try {
            String key = keyForUsername(username);
            if (users.containsKey(key)) {
                debugLog("User already exists with username: " + username + ", skipping registration");
                return false;
            }

            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("email", email);
            user.put("status", status);
            user.put("password", PasswordUtil.hash(password));
            user.put("regTime", System.currentTimeMillis());
            applyQuestionnaireAuditFields(user, questionnaireScore, questionnairePassed, questionnaireReviewSummary, questionnaireScoredAt);
            debugLog("Adding user with password to map: username=" + username);
            users.put(key, user);
            saveLater();
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
    public boolean updateUserStatus(String username, String status) {
        debugLog("updateUserStatus called: username=" + username + ", status=" + status);
        String key = keyForUsername(username);
        Map<String, Object> user = users.get(key);

        if (user == null) {
            debugLog("User not found: " + username);
            return false;
        }
        String oldStatus = (String) user.get("status");
        user.put("status", status);
        saveLater();
        debugLog("User status updated: " + username + " from " + oldStatus + " to " + status);
        return true;
    }

    @Override
    public boolean updateUserPassword(String username, String plainPassword) {
        debugLog("updateUserPassword called: username=" + username);
        String key = keyForUsername(username);
        Map<String, Object> user = users.get(key);

        if (user == null) {
            debugLog("User not found: " + username);
            return false;
        }

        user.put("password", PasswordUtil.hash(plainPassword));
        saveLater();
        debugLog("User password updated: " + user.get("username"));
        return true;
    }

    @Override
    public boolean updateUserEmail(String username, String email) {
        debugLog("updateUserEmail called: username=" + username);
        String key = keyForUsername(username);
        Map<String, Object> user = users.get(key);

        if (user == null) {
            debugLog("User not found: " + username);
            return false;
        }

        user.put("email", email);
        saveLater();
        debugLog("User email updated: " + user.get("username"));
        return true;
    }

    @Override
    public Map<String, Object> getUserByUsername(String username) {
        debugLog("Getting user by username: " + username);
        String key = username.toLowerCase(Locale.ROOT);
        Map<String, Object> user = users.get(key);
        if (user == null) {
            for (Map<String, Object> candidate : users.values()) {
                if (candidate.get("username") != null && candidate.get("username").toString().equalsIgnoreCase(username)) {
                    user = candidate;
                    break;
                }
            }
        }
        if (user != null) {
            debugLog("User found: " + user.get("username"));
        } else {
            debugLog("User not found");
        }
        return user;
    }

    @Override
    public Map<String, Object> getUserByUsernameExact(String username) {
        debugLog("Getting user by username (exact match): " + username);
        for (Map<String, Object> user : users.values()) {
            if (user.get("username") != null && user.get("username").toString().equals(username)) {
                debugLog("User found: " + user.get("username"));
                return user;
            }
        }
        debugLog("User not found");
        return null;
    }

    @Override
    public Map<String, Object> getUserByEmail(String email) {
        debugLog("Getting user by email: " + email);
        if (email == null || email.isEmpty()) {
            return null;
        }
        for (Map<String, Object> user : users.values()) {
            Object userEmail = user.get("email");
            if (userEmail != null && userEmail.toString().equalsIgnoreCase(email)) {
                debugLog("User found by email: " + user.get("username"));
                return user;
            }
        }
        debugLog("User not found by email");
        return null;
    }

    @Override
    public boolean deleteUser(String username) {
        debugLog("deleteUser called: username=" + username);
        try {
            String key = keyForUsername(username);
            Map<String, Object> removed = users.remove(key);

            if (removed != null) {
                debugLog("User deleted: " + removed.get("username"));
                saveLater();
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

        allUsers.sort((a, b) -> {
            Long timeA = getRegTimeAsLong(a.get("regTime"));
            Long timeB = getRegTimeAsLong(b.get("regTime"));
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

        filteredUsers.sort((a, b) -> {
            Long timeA = getRegTimeAsLong(a.get("regTime"));
            Long timeB = getRegTimeAsLong(b.get("regTime"));
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

    @Override
    public int getApprovedUserCount() {
        debugLog("Getting approved user count (excluding pending)");
        int count = 0;
        for (Map<String, Object> user : users.values()) {
            String status = user.get("status") != null ? user.get("status").toString() : "";
            if (!"pending".equalsIgnoreCase(status)) {
                count++;
            }
        }
        debugLog("Approved user count: " + count);
        return count;
    }

    @Override
    public int getApprovedUserCountWithSearch(String searchQuery) {
        debugLog("Getting approved user count with search: query=" + searchQuery);
        int count = 0;
        String query = searchQuery != null ? searchQuery.toLowerCase().trim() : "";

        for (Map<String, Object> user : users.values()) {
            String status = user.get("status") != null ? user.get("status").toString() : "";
            if (!"pending".equalsIgnoreCase(status)) {
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
        }

        debugLog("Approved user count with search '" + searchQuery + "': " + count);
        return count;
    }

    @Override
    public List<Map<String, Object>> getApprovedUsersWithPagination(int page, int pageSize) {
        debugLog("Getting approved users with pagination: page=" + page + ", pageSize=" + pageSize);
        List<Map<String, Object>> approvedUsers = new ArrayList<>();

        for (Map<String, Object> user : users.values()) {
            String status = user.get("status") != null ? user.get("status").toString() : "";
            if (!"pending".equalsIgnoreCase(status)) {
                approvedUsers.add(user);
            }
        }

        approvedUsers.sort((a, b) -> {
            Long timeA = getRegTimeAsLong(a.get("regTime"));
            Long timeB = getRegTimeAsLong(b.get("regTime"));
            if (timeA == null) timeA = 0L;
            if (timeB == null) timeB = 0L;
            return timeB.compareTo(timeA);
        });

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, approvedUsers.size());

        if (startIndex >= approvedUsers.size()) {
            debugLog("Page " + page + " is out of range for approved users, returning empty list");
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = approvedUsers.subList(startIndex, endIndex);
        debugLog("Returning " + result.size() + " approved users for page " + page);
        return result;
    }

    @Override
    public List<Map<String, Object>> getApprovedUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery) {
        debugLog("Getting approved users with pagination and search: page=" + page + ", pageSize=" + pageSize + ", query=" + searchQuery);
        List<Map<String, Object>> filteredUsers = new ArrayList<>();

        String query = searchQuery != null ? searchQuery.toLowerCase().trim() : "";
        for (Map<String, Object> user : users.values()) {
            String status = user.get("status") != null ? user.get("status").toString() : "";
            if (!"pending".equalsIgnoreCase(status)) {
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
        }

        filteredUsers.sort((a, b) -> {
            Long timeA = getRegTimeAsLong(a.get("regTime"));
            Long timeB = getRegTimeAsLong(b.get("regTime"));
            if (timeA == null) timeA = 0L;
            if (timeB == null) timeB = 0L;
            return timeB.compareTo(timeA);
        });

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredUsers.size());

        if (startIndex >= filteredUsers.size()) {
            debugLog("Page " + page + " is out of range for approved users search results, returning empty list");
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = filteredUsers.subList(startIndex, endIndex);
        debugLog("Returning " + result.size() + " approved users for page " + page + " with search query: " + searchQuery);
        return result;
    }

    @Override
    public boolean updateUserDiscordId(String username, String discordId) {
        debugLog("updateUserDiscordId called: username=" + username + ", discordId=" + discordId);
        String key = keyForUsername(username);
        Map<String, Object> user = users.get(key);

        if (user == null) {
            debugLog("User not found: " + username);
            return false;
        }

        user.put("discordId", discordId);
        saveLater();
        debugLog("User Discord ID updated: " + user.get("username") + " -> " + discordId);
        return true;
    }

    @Override
    public Map<String, Object> getUserByDiscordId(String discordId) {
        debugLog("Getting user by Discord ID: " + discordId);
        for (Map<String, Object> user : users.values()) {
            Object userDiscordId = user.get("discordId");
            if (userDiscordId == null) {
                userDiscordId = user.get("discord_id");
            }
            if (userDiscordId != null && userDiscordId.toString().equals(discordId)) {
                debugLog("User found: " + user.get("username"));
                return user;
            }
        }
        debugLog("User not found with Discord ID: " + discordId);
        return null;
    }

    @Override
    public boolean isDiscordIdLinked(String discordId) {
        debugLog("Checking if Discord ID is linked: " + discordId);
        return getUserByDiscordId(discordId) != null;
    }

    @Override
    public void close() {
        running = false;
        // Flush any remaining dirty data on close
        if (dirty) {
            save();
        }
        debugLog("FileUserDao closed");
    }
}
