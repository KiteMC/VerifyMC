package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Map;

public interface UserDao {
    boolean registerUser(String uuid, String username, String email, String status);
    boolean updateUserStatus(String uuidOrName, String status);
    List<Map<String, Object>> getAllUsers();
    Map<String, Object> getUserByUuid(String uuid);
    Map<String, Object> getUserByUsername(String username);
    boolean deleteUser(String uuidOrName);
    void save();
    int countUsersByEmail(String email);
    List<Map<String, Object>> getPendingUsers();
} 