package team.kitemc.verifymc.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.regex.Pattern;

/**
 * AuthMe集成服务类
 * 负责与AuthMe插件交互，包括密码验证、用户注册、注销等功能
 */
public class AuthmeService {
    private final Plugin plugin;
    private final boolean debug;

    public AuthmeService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * 检查AuthMe是否启用
     */
    public boolean isAuthmeEnabled() {
        return plugin.getConfig().getBoolean("authme.enabled", false);
    }

    /**
     * 检查是否要求密码
     */
    public boolean isPasswordRequired() {
        return plugin.getConfig().getBoolean("authme.require_password", false);
    }

    /**
     * 检查是否启用自动注册
     */
    public boolean isAutoRegisterEnabled() {
        return plugin.getConfig().getBoolean("authme.auto_register", false);
    }

    /**
     * 检查是否启用自动注销
     */
    public boolean isAutoUnregisterEnabled() {
        return plugin.getConfig().getBoolean("authme.auto_unregister", false);
    }

    /**
     * 验证密码是否符合正则表达式
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("authme.password_regex", "^.{6,}$");
        return Pattern.matches(regex, password);
    }

    /**
     * 注册用户到AuthMe（在主线程中执行）
     */
    public boolean registerToAuthme(String username, String password) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping registration");
            return false;
        }
        
        debugLog("Registering user to AuthMe: " + username);
        
        // 确保在主线程中执行
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("register " + username + " " + password);
        } else {
            // 如果在异步线程中，使用同步任务在主线程执行
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> 
                    executeAuthmeCommand("register " + username + " " + password)
                ).get();
            } catch (Exception e) {
                debugLog("Failed to register user to AuthMe: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * 从AuthMe注销用户（在主线程中执行）
     */
    public boolean unregisterFromAuthme(String username) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping unregistration");
            return false;
        }
        
        debugLog("Unregistering user from AuthMe: " + username);
        
        // 确保在主线程中执行
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("purgeplayer " + username);
        } else {
            // 如果在异步线程中，使用同步任务在主线程执行
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> 
                    executeAuthmeCommand("purgeplayer " + username)
                ).get();
            } catch (Exception e) {
                debugLog("Failed to unregister user from AuthMe: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * 修改AuthMe中的用户密码（在主线程中执行）
     */
    public boolean changePasswordInAuthme(String username, String newPassword) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping password change");
            return false;
        }
        
        debugLog("Changing password in AuthMe: " + username);
        
        // 确保在主线程中执行
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("password " + username + " " + newPassword);
        } else {
            // 如果在异步线程中，使用同步任务在主线程执行
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> 
                    executeAuthmeCommand("password " + username + " " + newPassword)
                ).get();
            } catch (Exception e) {
                debugLog("Failed to change password in AuthMe: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * 执行AuthMe命令
     */
    private boolean executeAuthmeCommand(String command) {
        try {
            debugLog("Executing AuthMe command: " + command);
            boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "authme " + command);
            debugLog("AuthMe command result: " + result);
            return result;
        } catch (Exception e) {
            debugLog("Exception executing AuthMe command: " + e.getMessage());
            return false;
        }
    }

    /**
     * 调试日志
     */
    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AuthmeService: " + msg);
        }
    }
} 