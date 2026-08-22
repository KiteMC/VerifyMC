package team.kitemc.verifymc.proxy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Version check service for VerifyMC Proxy Plugin
 * Checks for updates from GitHub repository
 */
public class ProxyVersionCheckService {
    private static final String GITHUB_RELEASES_API_URL = "https://api.github.com/repos/KiteMC/VerifyMC/releases/latest";
    private static final int TIMEOUT_MS = 10000;
    
    private final String currentVersion;
    private final Logger logger;
    private final boolean debug;
    
    private String latestVersion;
    private long lastCheckTime = 0;
    private static final long CHECK_COOLDOWN = 3600000; // 1 hour
    
    public ProxyVersionCheckService(String currentVersion, Logger logger, boolean debug) {
        this.currentVersion = currentVersion;
        this.logger = logger;
        this.debug = debug;
    }
    
    private void debugLog(String msg) {
        if (debug) logger.info("[DEBUG] ProxyVersionCheckService: " + msg);
    }
    
    /**
     * Check for updates asynchronously
     * @return CompletableFuture with update check result
     */
    public CompletableFuture<UpdateCheckResult> checkForUpdatesAsync() {
        return CompletableFuture.supplyAsync(this::checkForUpdates);
    }
    
    /**
     * Check for updates synchronously
     * @return Update check result
     */
    public UpdateCheckResult checkForUpdates() {
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime < CHECK_COOLDOWN && latestVersion != null) {
            debugLog("Using cached version check result (cooldown active)");
            return new UpdateCheckResult(currentVersion, latestVersion, isNewerVersion(latestVersion, currentVersion));
        }
        
        debugLog("Checking for updates...");
        
        String fetchedVersion = fetchLatestVersionFromGitHub();
        if (fetchedVersion != null) {
            latestVersion = fetchedVersion;
            lastCheckTime = currentTime;
            
            boolean updateAvailable = isNewerVersion(fetchedVersion, currentVersion);
            debugLog("Version check complete: current=" + currentVersion + ", latest=" + fetchedVersion + ", updateAvailable=" + updateAvailable);
            
            return new UpdateCheckResult(currentVersion, fetchedVersion, updateAvailable);
        }
        
        debugLog("Failed to fetch latest version");
        return new UpdateCheckResult(currentVersion, null, false);
    }
    
    /**
     * Fetch latest official version from GitHub. The latest endpoint excludes
     * drafts and prereleases by definition.
     * @return Latest version string or null if failed
     */
    private String fetchLatestVersionFromGitHub() {
        try {
            debugLog("Fetching latest official release from: " + GITHUB_RELEASES_API_URL);
            
            URL url = new URL(GITHUB_RELEASES_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "VerifyMC-Proxy/" + currentVersion);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                debugLog("HTTP request failed with response code: " + responseCode);
                return null;
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            JsonObject release = JsonParser.parseString(content.toString()).getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) return null;
            if (release.has("prerelease") && release.get("prerelease").getAsBoolean()) return null;

            String tagName = release.has("tag_name") ? release.get("tag_name").getAsString().trim() : "";
            if (tagName.isEmpty()) return null;
            String version = tagName.replaceFirst("^[vV]", "");
            debugLog("Found latest official release: " + version);
            return version;
            
        } catch (Exception e) {
            debugLog("Exception while fetching version: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Compare two version strings
     * @param newVersion The potentially newer version
     * @param oldVersion The current version
     * @return true if newVersion is newer than oldVersion
     */
    private boolean isNewerVersion(String newVersion, String oldVersion) {
        if (newVersion == null || oldVersion == null) {
            return false;
        }
        
        try {
            String[] newParts = newVersion.split("[.-]");
            String[] oldParts = oldVersion.split("[.-]");
            
            int maxLength = Math.max(newParts.length, oldParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int newPart = 0;
                int oldPart = 0;
                
                if (i < newParts.length) {
                    try {
                        newPart = Integer.parseInt(newParts[i].replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException e) {
                        // Non-numeric part, compare as string
                        if (i < oldParts.length) {
                            int comparison = newParts[i].compareTo(oldParts[i]);
                            if (comparison != 0) return comparison > 0;
                        }
                        continue;
                    }
                }
                
                if (i < oldParts.length) {
                    try {
                        oldPart = Integer.parseInt(oldParts[i].replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                
                if (newPart > oldPart) return true;
                if (newPart < oldPart) return false;
            }
            
            return false;
        } catch (Exception e) {
            debugLog("Error comparing versions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current plugin version
     * @return Current version string
     */
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    /**
     * Get latest version (from cache)
     * @return Latest version string or null if not checked
     */
    public String getLatestVersion() {
        return latestVersion;
    }
    
    /**
     * Update check result
     */
    public static class UpdateCheckResult {
        private final String currentVersion;
        private final String latestVersion;
        private final boolean updateAvailable;
        
        public UpdateCheckResult(String currentVersion, String latestVersion, boolean updateAvailable) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
        }
        
        public String getCurrentVersion() {
            return currentVersion;
        }
        
        public String getLatestVersion() {
            return latestVersion;
        }
        
        public boolean isUpdateAvailable() {
            return updateAvailable;
        }
    }
}

