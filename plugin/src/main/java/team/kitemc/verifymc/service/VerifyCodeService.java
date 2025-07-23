package team.kitemc.verifymc.service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class VerifyCodeService {
    private final ConcurrentHashMap<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final long expireMillis = 5 * 60 * 1000; // 5分钟
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;

    public VerifyCodeService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    // 兼容旧构造函数
    public VerifyCodeService() {
        this.plugin = null;
        this.debug = false;
    }

    private void debugLog(String msg) {
        if (debug) plugin.getLogger().info("[DEBUG] VerifyCodeService: " + msg);
    }

    public String generateCode(String key) {
        debugLog("generateCode called for key: " + key);
        String code = String.format("%06d", new Random().nextInt(1000000));
        long expireTime = System.currentTimeMillis() + expireMillis;
        codeMap.put(key, new CodeEntry(code, expireTime));
        debugLog("Generated code: " + code + " for key: " + key + ", expires at: " + expireTime);
        return code;
    }

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