package team.kitemc.verifymc.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.regex.Pattern;

/**
 * AuthMe integration service class
 * Responsible for interacting with AuthMe plugin, including password verification, user registration, unregistration, etc.
 */
public class AuthmeService {
    private final Plugin plugin;
    private final boolean debug;

    public AuthmeService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * Check if AuthMe is enabled
     * @return true if AuthMe is enabled
     */
    public boolean isAuthmeEnabled() {
        return plugin.getConfig().getBoolean("authme.enabled", false);
    }

    /**
     * Check if password is required
     * @return true if password is required
     */
    public boolean isPasswordRequired() {
        return plugin.getConfig().getBoolean("authme.require_password", false);
    }

    /**
     * Check if auto registration is enabled
     * @return true if auto registration is enabled
     */
    public boolean isAutoRegisterEnabled() {
        return plugin.getConfig().getBoolean("authme.auto_register", false);
    }

    /**
     * Check if auto unregistration is enabled
     * @return true if auto unregistration is enabled
     */
    public boolean isAutoUnregisterEnabled() {
        return plugin.getConfig().getBoolean("authme.auto_unregister", false);
    }

    /**
     * Validate if password matches regex pattern
     * @param password Password to validate
     * @return true if password is valid
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{3,16}$");
        return Pattern.matches(regex, password);
    }

    /**
     * Register user to AuthMe (executed in main thread)
     * @param username Username to register
     * @param password Password for registration
     * @return true if registration successful
     */
    public boolean registerToAuthme(String username, String password) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping registration");
            return false;
        }
        
        debugLog("Registering user to AuthMe: " + username);
        
        // Ensure execution in main thread
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("register " + username + " " + password);
        } else {
            // If in async thread, use sync task to execute in main thread
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
     * Unregister user from AuthMe (executed in main thread)
     * @param username Username to unregister
     * @return true if unregistration successful
     */
    public boolean unregisterFromAuthme(String username) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping unregistration");
            return false;
        }
        
        debugLog("Unregistering user from AuthMe: " + username);
        
        // Ensure execution in main thread
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("purgeplayer " + username);
        } else {
            // If in async thread, use sync task to execute in main thread
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
     * Change user password in AuthMe (executed in main thread)
     * @param username Username to change password for
     * @param newPassword New password
     * @return true if password change successful
     */
    public boolean changePasswordInAuthme(String username, String newPassword) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping password change");
            return false;
        }
        
        debugLog("Changing password in AuthMe: " + username);
        
        // Ensure execution in main thread
        if (Bukkit.isPrimaryThread()) {
            return executeAuthmeCommand("password " + username + " " + newPassword);
        } else {
            // If in async thread, use sync task to execute in main thread
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
     * Execute AuthMe command
     * @param command Command to execute
     * @return true if command executed successfully
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
     * Debug logging
     * @param msg Message to log
     */
    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AuthmeService: " + msg);
        }
    }
} 