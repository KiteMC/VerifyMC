package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import org.json.JSONArray;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.util.PluginScheduler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord OAuth2 integration service
 * Handles Discord account linking and guild membership verification
 */
public class DiscordService {
    private final Plugin plugin;
    private final boolean debug;
    private PluginScheduler.ScheduledTask cleanupTask;
    private UserDao userDao;
    private boolean usernameCaseSensitive;
    
    // OAuth2 configuration
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String guildId;
    private boolean required;
    
    // State tokens for OAuth2 flow (state -> StateData)
    private final Map<String, StateData> stateTokens = new ConcurrentHashMap<>();
    
    // Token cache (username -> DiscordToken) - temporary cache for active sessions
    private final Map<String, TokenData> tokenCache = new ConcurrentHashMap<>();
    
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    private static final String DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize";
    private static final String DISCORD_OAUTH_TOKEN = DISCORD_API_BASE + "/oauth2/token";
    
    // State token expiry: 10 minutes
    private static final long STATE_EXPIRY_MS = 600000;
    // Token cache expiry: 1 hour
    private static final long TOKEN_CACHE_EXPIRY_MS = 3600000;
    // Cleanup interval: 5 minutes
    private static final long CLEANUP_INTERVAL_TICKS = 6000;
    
    public DiscordService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        loadConfig();
        startCleanupTask();
    }
    
    /**
     * Set the UserDao for persistent storage
     * @param userDao UserDao instance
     */
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
        debugLog("UserDao set for Discord service");
    }
    
    /**
     * Start periodic cleanup task for expired state tokens and token cache
     */
    private void startCleanupTask() {
        cleanupTask = PluginScheduler.runAsyncTimer(
                plugin,
                this::cleanupExpiredTokens,
                CLEANUP_INTERVAL_TICKS,
                CLEANUP_INTERVAL_TICKS
        );
        debugLog("Started cleanup task for expired tokens");
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }
    
    /**
     * Cleanup expired state tokens and token cache
     */
    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int statesCleaned = 0;
        int tokensCleaned = 0;
        
        // Cleanup expired state tokens
        Iterator<Map.Entry<String, StateData>> stateIterator = stateTokens.entrySet().iterator();
        while (stateIterator.hasNext()) {
            Map.Entry<String, StateData> entry = stateIterator.next();
            if (entry.getValue().isExpired()) {
                stateIterator.remove();
                statesCleaned++;
            }
        }
        
        // Cleanup expired token cache
        Iterator<Map.Entry<String, TokenData>> tokenIterator = tokenCache.entrySet().iterator();
        while (tokenIterator.hasNext()) {
            Map.Entry<String, TokenData> entry = tokenIterator.next();
            if (now - entry.getValue().cacheTime > TOKEN_CACHE_EXPIRY_MS) {
                tokenIterator.remove();
                tokensCleaned++;
            }
        }
        
        if (statesCleaned > 0 || tokensCleaned > 0) {
            debugLog("Cleanup completed: " + statesCleaned + " state tokens, " + tokensCleaned + " cached tokens removed");
        }
    }
    
    /**
     * Load Discord configuration from plugin config
     */
    public void loadConfig() {
        clientId = plugin.getConfig().getString("discord.client_id", "");
        clientSecret = plugin.getConfig().getString("discord.client_secret", "");
        redirectUri = plugin.getConfig().getString("discord.redirect_uri", "");
        guildId = plugin.getConfig().getString("discord.guild_id", "");
        required = plugin.getConfig().getBoolean("discord.required", false);
        usernameCaseSensitive = plugin.getConfig().getBoolean("username_case_sensitive", false);
        
        debugLog("Discord config loaded: clientId=" + (clientId.isEmpty() ? "not set" : "***") + 
                 ", guildId=" + (guildId.isEmpty() ? "not set" : guildId));
    }
    
    /**
     * Check if Discord integration is enabled
     * @return true if Discord integration is properly configured and enabled
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false) &&
               !clientId.isEmpty() && !clientSecret.isEmpty();
    }
    
    /**
     * Check if Discord binding is required for registration
     * @return true if Discord binding is required
     */
    public boolean isRequired() {
        return isEnabled() && required;
    }
    
    /**
     * Generate OAuth2 authorization URL
     * @param username The username to link
     * @return Authorization URL
     */
    public String generateAuthUrl(String username) {
        if (!isEnabled()) {
            return null;
        }
        
        // Generate state token
        String state = generateState();
        stateTokens.put(state, new StateData(username, System.currentTimeMillis()));
        
        // Build authorization URL
        StringBuilder url = new StringBuilder(DISCORD_OAUTH_AUTHORIZE);
        url.append("?client_id=").append(clientId);
        url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        url.append("&response_type=code");
        url.append("&scope=identify%20guilds");
        url.append("&state=").append(state);
        
        debugLog("Generated auth URL for " + username + ": " + url);
        
        return url.toString();
    }
    
    /**
     * Get OAuth2 authorization URL (alias for generateAuthUrl)
     * @param username The username to link
     * @return Authorization URL
     */
    public String getAuthorizationUrl(String username) {
        return generateAuthUrl(username);
    }
    
    /**
     * Handle OAuth2 callback
     * @param code Authorization code from Discord
     * @param state State token
     * @return DiscordCallbackResult
     */
    public DiscordCallbackResult handleCallback(String code, String state) {
        // Validate state
        StateData stateData = stateTokens.remove(state);
        if (stateData == null || stateData.isExpired()) {
            debugLog("Invalid or expired state token: " + state);
            return new DiscordCallbackResult(false, "Invalid or expired state", null, null);
        }
        
        String username = stateData.username;
        
        try {
            // Exchange code for access token
            DiscordToken token = exchangeCodeForToken(code);
            if (token == null) {
                return new DiscordCallbackResult(false, "Failed to exchange code for token", username, null);
            }
            
            // Get user info
            DiscordUser user = getUserInfo(token.accessToken);
            if (user == null) {
                return new DiscordCallbackResult(false, "Failed to get user info", username, null);
            }
            
            // Check if this Discord account is already linked to another user
            if (userDao != null && userDao.isDiscordIdLinked(user.id)) {
                Map<String, Object> existingUser = userDao.getUserByDiscordId(user.id);
                if (existingUser != null) {
                    String existingUsername = (String) existingUser.get("username");
                    boolean sameUsername = usernameCaseSensitive
                            ? existingUsername.equals(username)
                            : existingUsername.equalsIgnoreCase(username);
                    if (!sameUsername) {
                        debugLog("Discord account already linked to: " + existingUsername);
                        return new DiscordCallbackResult(false, "This Discord account is already linked to another user", username, user);
                    }
                }
            }
            
            // Check guild membership if required
            if (!guildId.isEmpty()) {
                boolean inGuild = checkGuildMembership(token.accessToken, guildId);
                if (!inGuild) {
                    debugLog("User " + user.username + " is not in guild " + guildId);
                    return new DiscordCallbackResult(false, "You must be a member of the Discord server", username, user);
                }
            }
            
            // Store token in cache
            tokenCache.put(username.toLowerCase(), new TokenData(token, System.currentTimeMillis()));
            
            // Persist Discord ID to database
            if (userDao != null) {
                boolean updated = userDao.updateUserDiscordId(username, user.id, usernameCaseSensitive);
                if (updated) {
                    debugLog("Persisted Discord ID to database for " + username + ": " + user.id);
                } else {
                    debugLog("Warning: Failed to persist Discord ID to database for " + username);
                }
            }
            
            debugLog("Successfully linked Discord for " + username + ": " + user.username + "#" + user.discriminator);
            
            return new DiscordCallbackResult(true, "Discord account linked successfully", username, user);
            
        } catch (Exception e) {
            debugLog("OAuth callback error: " + e.getMessage());
            return new DiscordCallbackResult(false, "OAuth error: " + e.getMessage(), username, null);
        }
    }
    
    /**
     * Exchange authorization code for access token
     */
    private DiscordToken exchangeCodeForToken(String code) throws Exception {
        URL url = new URL(DISCORD_OAUTH_TOKEN);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        String body = "client_id=" + clientId +
                     "&client_secret=" + clientSecret +
                     "&grant_type=authorization_code" +
                     "&code=" + code +
                     "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        
        if (conn.getResponseCode() != 200) {
            debugLog("Token exchange failed with status: " + conn.getResponseCode());
            return null;
        }
        
        String response = readResponse(conn);
        JSONObject json = new JSONObject(response);
        
        return new DiscordToken(
            json.getString("access_token"),
            json.getString("refresh_token"),
            System.currentTimeMillis() + json.getLong("expires_in") * 1000
        );
    }
    
    /**
     * Refresh an expired access token
     * @param refreshToken The refresh token
     * @return New DiscordToken or null if failed
     */
    private DiscordToken refreshAccessToken(String refreshToken) {
        try {
            URL url = new URL(DISCORD_OAUTH_TOKEN);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            String body = "client_id=" + clientId +
                         "&client_secret=" + clientSecret +
                         "&grant_type=refresh_token" +
                         "&refresh_token=" + refreshToken;
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() != 200) {
                debugLog("Token refresh failed with status: " + conn.getResponseCode());
                return null;
            }
            
            String response = readResponse(conn);
            JSONObject json = new JSONObject(response);
            
            return new DiscordToken(
                json.getString("access_token"),
                json.getString("refresh_token"),
                System.currentTimeMillis() + json.getLong("expires_in") * 1000
            );
        } catch (Exception e) {
            debugLog("Token refresh error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get a valid access token for a user, refreshing if necessary
     * @param username The username
     * @return Valid access token or null
     */
    private String getValidAccessToken(String username) {
        TokenData tokenData = tokenCache.get(username.toLowerCase());
        if (tokenData == null) {
            return null;
        }
        
        DiscordToken token = tokenData.token;
        
        // Check if token is expired or about to expire (within 5 minutes)
        if (System.currentTimeMillis() >= token.expiresAt - 300000) {
            debugLog("Token expired or expiring soon for " + username + ", refreshing...");
            DiscordToken newToken = refreshAccessToken(token.refreshToken);
            if (newToken != null) {
                tokenCache.put(username.toLowerCase(), new TokenData(newToken, System.currentTimeMillis()));
                debugLog("Token refreshed successfully for " + username);
                return newToken.accessToken;
            } else {
                debugLog("Token refresh failed for " + username);
                tokenCache.remove(username.toLowerCase());
                return null;
            }
        }
        
        return token.accessToken;
    }
    
    /**
     * Get user info from Discord API
     */
    private DiscordUser getUserInfo(String accessToken) throws Exception {
        URL url = new URL(DISCORD_API_BASE + "/users/@me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        if (conn.getResponseCode() != 200) {
            debugLog("Get user info failed with status: " + conn.getResponseCode());
            return null;
        }
        
        String response = readResponse(conn);
        JSONObject json = new JSONObject(response);
        
        return new DiscordUser(
            json.getString("id"),
            json.getString("username"),
            json.optString("discriminator", "0"),
            json.optString("avatar", null),
            json.optString("global_name", null)
        );
    }
    
    /**
     * Check if user is a member of the specified guild
     */
    private boolean checkGuildMembership(String accessToken, String guildId) throws Exception {
        URL url = new URL(DISCORD_API_BASE + "/users/@me/guilds");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        if (conn.getResponseCode() != 200) {
            debugLog("Get guilds failed with status: " + conn.getResponseCode());
            return false;
        }
        
        String response = readResponse(conn);
        JSONArray guilds = new JSONArray(response);
        
        for (int i = 0; i < guilds.length(); i++) {
            JSONObject guild = guilds.getJSONObject(i);
            if (guildId.equals(guild.getString("id"))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a user has linked their Discord account (from database)
     * @param username The username to check
     * @return true if linked
     */
    public boolean isLinked(String username) {
        // First check database
        if (userDao != null) {
            Map<String, Object> user = userDao.getUserByUsername(username, usernameCaseSensitive);
            if (user != null) {
                Object discordId = user.get("discordId");
                if (discordId == null) {
                    discordId = user.get("discord_id");
                }
                if (discordId != null && !discordId.toString().isEmpty()) {
                    return true;
                }
            }
        }
        
        // Fallback to cache (for users who just linked but haven't registered yet)
        return tokenCache.containsKey(username.toLowerCase());
    }
    
    /**
     * Get Discord ID for a linked user
     * @param username The username
     * @return Discord ID or null
     */
    public String getLinkedDiscordId(String username) {
        if (userDao != null) {
            Map<String, Object> user = userDao.getUserByUsername(username, usernameCaseSensitive);
            if (user != null) {
                Object discordId = user.get("discordId");
                if (discordId == null) {
                    discordId = user.get("discord_id");
                }
                if (discordId != null && !discordId.toString().isEmpty()) {
                    return discordId.toString();
                }
            }
        }
        return null;
    }
    
    /**
     * Get Discord user info for a linked user
     * @param username The username
     * @return DiscordUser or null
     */
    public DiscordUser getLinkedUser(String username) {
        // Try to get from cache with valid token
        String accessToken = getValidAccessToken(username);
        if (accessToken != null) {
            try {
                return getUserInfo(accessToken);
            } catch (Exception e) {
                debugLog("Failed to get linked user info from API: " + e.getMessage());
            }
        }
        
        // If no valid token, return basic info from database
        String discordId = getLinkedDiscordId(username);
        if (discordId != null) {
            return new DiscordUser(discordId, null, "0", null, null);
        }
        
        return null;
    }
    
    /**
     * Unlink Discord account from a user
     * @param username The username
     * @return true if successful
     */
    public boolean unlinkDiscord(String username) {
        tokenCache.remove(username.toLowerCase());
        
        if (userDao != null) {
            return userDao.updateUserDiscordId(username, null, usernameCaseSensitive);
        }
        
        return true;
    }
    
    /**
     * Unlink Discord account from a user (alias for unlinkDiscord)
     * @param username The username
     * @return true if successful
     */
    public boolean unlinkUser(String username) {
        return unlinkDiscord(username);
    }
    
    /**
     * Read HTTP response
     */
    private String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        )) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    /**
     * Generate random state token
     */
    private String generateState() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] DiscordService: " + msg);
        }
    }
    
    /**
     * State data for OAuth2 flow
     */
    private static class StateData {
        final String username;
        final long timestamp;
        
        StateData(String username, long timestamp) {
            this.username = username;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > STATE_EXPIRY_MS;
        }
    }
    
    /**
     * Token data with cache timestamp
     */
    private static class TokenData {
        final DiscordToken token;
        final long cacheTime;
        
        TokenData(DiscordToken token, long cacheTime) {
            this.token = token;
            this.cacheTime = cacheTime;
        }
    }
    
    /**
     * Discord OAuth2 token
     */
    public static class DiscordToken {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAt;
        
        public DiscordToken(String accessToken, String refreshToken, long expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
    }
    
    /**
     * Discord user info
     */
    public static class DiscordUser {
        public final String id;
        public final String username;
        public final String discriminator;
        public final String avatar;
        public final String globalName;
        
        public DiscordUser(String id, String username, String discriminator, String avatar, String globalName) {
            this.id = id;
            this.username = username;
            this.discriminator = discriminator;
            this.avatar = avatar;
            this.globalName = globalName;
        }
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("id", id);
            if (username != null) json.put("username", username);
            json.put("discriminator", discriminator);
            if (avatar != null) json.put("avatar", avatar);
            if (globalName != null) json.put("globalName", globalName);
            return json;
        }
    }
    
    /**
     * OAuth2 callback result
     */
    public static class DiscordCallbackResult {
        public final boolean success;
        public final String message;
        public final String username;
        public final DiscordUser user;
        
        public DiscordCallbackResult(boolean success, String message, String username, DiscordUser user) {
            this.success = success;
            this.message = message;
            this.username = username;
            this.user = user;
        }
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("success", success);
            json.put("message", message);
            if (username != null) json.put("username", username);
            if (user != null) json.put("user", user.toJson());
            return json;
        }
    }
}
