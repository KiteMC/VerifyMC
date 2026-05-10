package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Map;

public interface UserDao {
    boolean registerUser(String username, String email, String status);

    boolean registerUser(String username, String email, String status,
                         Integer questionnaireScore, Boolean questionnairePassed,
                         String questionnaireReviewSummary, Long questionnaireScoredAt);

    boolean registerUser(String username, String email, String status, String password);

    boolean registerUser(String username, String email, String status, String password,
                         Integer questionnaireScore, Boolean questionnairePassed,
                         String questionnaireReviewSummary, Long questionnaireScoredAt);

    boolean updateUserStatus(String username, String status);

    /**
     * Updates a user's password using a plaintext password input.
     * Implementations are responsible for hashing before persistence.
     */
    boolean updateUserPassword(String username, String plainPassword);

    boolean updateUserEmail(String username, String email);

    List<Map<String, Object>> getAllUsers();

    List<Map<String, Object>> getUsersWithPagination(int page, int pageSize);

    int getTotalUserCount();

    List<Map<String, Object>> getUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery);

    int getTotalUserCountWithSearch(String searchQuery);

    /**
     * Returns count of non-pending users (approved, banned, rejected).
     * Despite the name, this includes all processed users, not just "approved".
     */
    int getApprovedUserCount();

    int getApprovedUserCountWithSearch(String searchQuery);

    List<Map<String, Object>> getApprovedUsersWithPagination(int page, int pageSize);

    List<Map<String, Object>> getApprovedUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery);

    Map<String, Object> getUserByUsername(String username);

    Map<String, Object> getUserByUsernameExact(String username);

    default Map<String, Object> getUserByUsername(String username, boolean caseSensitive) {
        return caseSensitive ? getUserByUsernameExact(username) : getUserByUsername(username);
    }

    default String resolveStoredUsername(String username, boolean caseSensitive) {
        Map<String, Object> user = getUserByUsername(username, caseSensitive);
        if (user == null) {
            return null;
        }
        Object storedUsername = user.get("username");
        return storedUsername == null ? null : String.valueOf(storedUsername);
    }

    default boolean updateUserStatus(String username, String status, String operator, boolean caseSensitive) {
        String storedUsername = resolveStoredUsername(username, caseSensitive);
        return storedUsername != null && updateUserStatus(storedUsername, status, operator);
    }

    default boolean updateUserPassword(String username, String plainPassword, boolean caseSensitive) {
        String storedUsername = resolveStoredUsername(username, caseSensitive);
        return storedUsername != null && updateUserPassword(storedUsername, plainPassword);
    }

    default boolean updateUserEmail(String username, String email, boolean caseSensitive) {
        String storedUsername = resolveStoredUsername(username, caseSensitive);
        return storedUsername != null && updateUserEmail(storedUsername, email);
    }

    default boolean deleteUser(String username, boolean caseSensitive) {
        String storedUsername = resolveStoredUsername(username, caseSensitive);
        return storedUsername != null && deleteUser(storedUsername);
    }

    default boolean updateUserDiscordId(String username, String discordId, boolean caseSensitive) {
        String storedUsername = resolveStoredUsername(username, caseSensitive);
        return storedUsername != null && updateUserDiscordId(storedUsername, discordId);
    }

    Map<String, Object> getUserByEmail(String email);

    boolean deleteUser(String username);

    void save();

    int countUsersByEmail(String email);

    List<Map<String, Object>> getPendingUsers();

    boolean updateUserDiscordId(String username, String discordId);

    Map<String, Object> getUserByDiscordId(String discordId);

    boolean isDiscordIdLinked(String discordId);

    default boolean updateUserStatus(String username, String status, String operator) {
        return updateUserStatus(username, status);
    }

    default List<Map<String, Object>> getUsers(int page, int size, String search, String status) {
        if (status == null || status.isEmpty() || "all".equalsIgnoreCase(status)) {
            return getUsersWithPaginationAndSearch(page, size, search);
        }
        if ("approved".equalsIgnoreCase(status)) {
            return getApprovedUsersWithPaginationAndSearch(page, size, search);
        }
        return getUsersByStatus(status, page, size, search);
    }

    default int getTotalUsers(String search, String status) {
        if (status == null || status.isEmpty() || "all".equalsIgnoreCase(status)) {
            return getTotalUserCountWithSearch(search);
        }
        if ("approved".equalsIgnoreCase(status)) {
            return getApprovedUserCountWithSearch(search);
        }
        return getTotalUsersByStatus(status, search);
    }

    default boolean banUser(String username) {
        return updateUserStatus(username, "banned");
    }

    default boolean unbanUser(String username) {
        return updateUserStatus(username, "approved");
    }

    default List<Map<String, Object>> getUsersByStatus(String status) {
        List<Map<String, Object>> allUsers = getAllUsers();
        return allUsers.stream()
            .filter(u -> status.equalsIgnoreCase((String) u.get("status")))
            .collect(java.util.stream.Collectors.toList());
    }

    default List<Map<String, Object>> getUsersByStatus(String status, int page, int size, String search) {
        if (page < 1 || size <= 0) {
            return java.util.Collections.emptyList();
        }

        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        List<Map<String, Object>> filtered = getAllUsers().stream()
            .filter(u -> status.equalsIgnoreCase(String.valueOf(u.get("status"))))
            .filter(u -> matchesSearch(u, normalizedSearch))
            .sorted((a, b) -> Long.compare(getRegTimeAsLong(b.get("regTime")), getRegTimeAsLong(a.get("regTime"))))
            .collect(java.util.stream.Collectors.toList());

        int from = (page - 1) * size;
        if (from >= filtered.size()) {
            return java.util.Collections.emptyList();
        }
        int to = Math.min(from + size, filtered.size());
        return filtered.subList(from, to);
    }

    default int getTotalUsersByStatus(String status, String search) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        return (int) getAllUsers().stream()
            .filter(u -> status.equalsIgnoreCase(String.valueOf(u.get("status"))))
            .filter(u -> matchesSearch(u, normalizedSearch))
            .count();
    }

    default boolean matchesSearch(Map<String, Object> user, String normalizedSearch) {
        if (normalizedSearch == null || normalizedSearch.isEmpty()) {
            return true;
        }
        String username = String.valueOf(user.getOrDefault("username", "")).toLowerCase();
        String email = String.valueOf(user.getOrDefault("email", "")).toLowerCase();
        return username.contains(normalizedSearch) || email.contains(normalizedSearch);
    }

    default long getRegTimeAsLong(Object regTime) {
        if (regTime instanceof Number) {
            return ((Number) regTime).longValue();
        }
        if (regTime == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(regTime));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    default boolean updatePassword(String username, String plainPassword) {
        return updateUserPassword(username, plainPassword);
    }

    /**
     * Closes any resources held by this DAO (e.g., database connections).
     * Default implementation does nothing.
     */
    default void close() {
        // Default: no-op
    }
}
