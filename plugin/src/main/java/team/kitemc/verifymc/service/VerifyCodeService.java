package team.kitemc.verifymc.service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class VerifyCodeService {
    private final ConcurrentHashMap<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rateLimitMap = new ConcurrentHashMap<>(); // Rate limiting for email sending
    private final long expireMillis = 5 * 60 * 1000; // 5分钟
    private final long rateLimitMillis = 60 * 1000; // 60秒频率限制
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;

    public VerifyCodeService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        startCleanupTask();
    }

    /**
     * Compatible with old constructor
     */
    public VerifyCodeService() {
        this.plugin = null;
        this.debug = false;
        startCleanupTask();
    }
    
    /**
     * Start cleanup task to remove expired entries
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // Clean up every 5 minutes
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    debugLog("Cleanup task interrupted");
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        debugLog("Cleanup task started");
    }
    
    /**
     * Clean up expired code entries and rate limit entries
     */
    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up expired verification codes
        codeMap.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expire < currentTime;
            if (expired) {
                debugLog("Removed expired code for key: " + entry.getKey());
            }
            return expired;
        });
        
        // Clean up expired rate limit entries
        rateLimitMap.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > rateLimitMillis;
            if (expired) {
                debugLog("Removed expired rate limit for email: " + entry.getKey());
            }
            return expired;
        });
        
        debugLog("Cleanup completed. Active codes: " + codeMap.size() + ", Active rate limits: " + rateLimitMap.size());
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] VerifyCodeService: " + msg);
    }

    /**
     * Check if email is within rate limit for sending verification codes
     * @param email Email to check
     * @return true if email can send code (not rate limited)
     */
    public boolean canSendCode(String email) {
        debugLog("canSendCode called for email: " + email);
        Long lastSentTime = rateLimitMap.get(email);
        if (lastSentTime == null) {
            debugLog("No previous send record for email: " + email);
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastSent = currentTime - lastSentTime;
        boolean canSend = timeSinceLastSent >= rateLimitMillis;
        
        debugLog("Email: " + email + ", last sent: " + lastSentTime + ", time since: " + timeSinceLastSent + "ms, can send: " + canSend);
        
        // Clean up expired rate limit entries
        if (canSend) {
            rateLimitMap.remove(email);
        }
        
        return canSend;
    }
    
    /**
     * Get remaining time in seconds before next code can be sent
     * @param email Email to check
     * @return remaining seconds, 0 if can send immediately
     */
    public long getRemainingCooldownSeconds(String email) {
        Long lastSentTime = rateLimitMap.get(email);
        if (lastSentTime == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastSent = currentTime - lastSentTime;
        long remainingMillis = rateLimitMillis - timeSinceLastSent;
        
        return remainingMillis > 0 ? (remainingMillis / 1000) + 1 : 0;
    }

    public String generateCode(String key) {
        debugLog("generateCode called for key: " + key);
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expireTime = System.currentTimeMillis() + expireMillis;
        long currentTime = System.currentTimeMillis();
        
        // Record the time when code was generated for rate limiting
        rateLimitMap.put(key, currentTime);
        codeMap.put(key, new CodeEntry(code, expireTime));
        
        debugLog("Generated code: " + code + " for key: " + key + ", expires at: " + expireTime + ", rate limit recorded at: " + currentTime);
        return code;
    }

    /**
     * Check verification code for given key
     * @param key Key to check
     * @param code Code to verify
     * @return true if code is valid
     */
    public boolean checkCode(String key, String code) {
        debugLog("checkCode called: key=" + key + ", code=" + code);
        CodeEntry entry = codeMap.get(key);
        if (entry == null) {
            debugLog("No code found for key: " + key);
            return false;
        }
        if (entry.expire < System.currentTimeMillis()) {
            debugLog("Code expired for key: " + key + ", expired at: " + entry.expire);
            codeMap.remove(key);
            return false;
        }
        boolean ok = entry.code.equals(code);
        debugLog("Code verification result: " + ok + " (expected: " + entry.code + ", provided: " + code + ")");
        if (ok) {
            debugLog("Removing used code for key: " + key);
            codeMap.remove(key);
        }
        return ok;
    }

    static class CodeEntry {
        String code;
        long expire;
        CodeEntry(String code, long expire) {
            this.code = code;
            this.expire = expire;
        }
    }
} 