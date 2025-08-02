package team.kitemc.verifymc.db;

import java.util.List;
import java.util.Map;

public interface UserDao {
    /**
     * 注册用户（不包含密码）
     */
    boolean registerUser(String uuid, String username, String email, String status);
    
    /**
     * 注册用户（包含密码）
     */
    boolean registerUser(String uuid, String username, String email, String status, String password);
    
    /**
     * 更新用户状态
     */
    boolean updateUserStatus(String uuidOrName, String status);
    
    /**
     * 更新用户密码
     */
    boolean updateUserPassword(String uuidOrName, String password);
    
    /**
     * 获取所有用户
     */
    List<Map<String, Object>> getAllUsers();
    
    /**
     * 根据UUID获取用户
     */
    Map<String, Object> getUserByUuid(String uuid);
    
    /**
     * 根据用户名获取用户
     */
    Map<String, Object> getUserByUsername(String username);
    
    /**
     * 删除用户
     */
    boolean deleteUser(String uuidOrName);
    
    /**
     * 保存数据
     */
    void save();
    
    /**
     * 统计指定邮箱的用户数量
     */
    int countUsersByEmail(String email);
    
    /**
     * 获取待审核用户列表
     */
    List<Map<String, Object>> getPendingUsers();
} 