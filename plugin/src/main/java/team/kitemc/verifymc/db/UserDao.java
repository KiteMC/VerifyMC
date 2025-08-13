package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Map;

public interface UserDao {
    /**
     * Register user (without password)
     * @param uuid User UUID
     * @param username Username
     * @param email Email address
     * @param status User status
     * @return true if registration successful
     */
    boolean registerUser(String uuid, String username, String email, String status);
    
    /**
     * Register user (with password)
     * @param uuid User UUID
     * @param username Username
     * @param email Email address
     * @param status User status
     * @param password User password
     * @return true if registration successful
     */
    boolean registerUser(String uuid, String username, String email, String status, String password);
    
    /**
     * Update user status
     * @param uuidOrName User UUID or username
     * @param status New status
     * @return true if update successful
     */
    boolean updateUserStatus(String uuidOrName, String status);
    
    /**
     * Update user password
     * @param uuidOrName User UUID or username
     * @param password New password
     * @return true if update successful
     */
    boolean updateUserPassword(String uuidOrName, String password);
    
    /**
     * Get all users
     * @return List of all users
     */
    List<Map<String, Object>> getAllUsers();
    
    /**
     * Get users with pagination
     * @param page Page number (starting from 1)
     * @param pageSize Number of users per page
     * @return List of users for the specified page
     */
    List<Map<String, Object>> getUsersWithPagination(int page, int pageSize);
    
    /**
     * Get total count of all users
     * @return Total number of users
     */
    int getTotalUserCount();
    
    /**
     * Get users with pagination and search
     * @param page Page number (starting from 1)
     * @param pageSize Number of users per page
     * @param searchQuery Search query for username or email
     * @return List of users matching the search criteria
     */
    List<Map<String, Object>> getUsersWithPaginationAndSearch(int page, int pageSize, String searchQuery);
    
    /**
     * Get total count of users matching search criteria
     * @param searchQuery Search query for username or email
     * @return Total number of users matching the search
     */
    int getTotalUserCountWithSearch(String searchQuery);
    
    /**
     * Get user by UUID
     * @param uuid User UUID
     * @return User data map
     */
    Map<String, Object> getUserByUuid(String uuid);
    
    /**
     * Get user by username
     * @param username Username
     * @return User data map
     */
    Map<String, Object> getUserByUsername(String username);
    
    /**
     * Delete user
     * @param uuidOrName User UUID or username
     * @return true if deletion successful
     */
    boolean deleteUser(String uuidOrName);
    
    /**
     * Save data
     */
    void save();
    
    /**
     * Count users by email
     * @param email Email address
     * @return Number of users with this email
     */
    int countUsersByEmail(String email);
    
    /**
     * Get pending users list
     * @return List of pending users
     */
    List<Map<String, Object>> getPendingUsers();
} 