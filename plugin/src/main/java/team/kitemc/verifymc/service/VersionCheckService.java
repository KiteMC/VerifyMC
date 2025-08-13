package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version check service for VerifyMC plugin
 * Checks for updates from GitHub repository
 */
public class VersionCheckService {
    private static final String GITHUB_POM_URL = "https://raw.githubusercontent.com/KiteMC/VerifyMC/refs/heads/master/plugin/pom.xml";
    private static final String GITHUB_RELEASES_URL = "https://github.com/KiteMC/VerifyMC/releases";
    private static final Pattern VERSION_PATTERN = Pattern.compile("<version>([^<]+)</version>");
    private static final int TIMEOUT_MS = 10000; // 10 seconds timeout
    
    private final Plugin plugin;
    private final boolean debug;
    private String currentVersion;
    private String latestVersion;
    private boolean updateAvailable = false;
    private long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 3600000; // 1 hour in milliseconds
    
    public VersionCheckService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.currentVersion = plugin.getDescription().getVersion();
        debugLog("VersionCheckService initialized with current version: " + currentVersion);
    }
    
    private void debugLog(String message) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] VersionCheckService: " + message);
        }
    }
    
    /**
     * Check for updates asynchronously
     * @return CompletableFuture with update check result
     */
    public CompletableFuture<UpdateCheckResult> checkForUpdatesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                debugLog("Starting version check...");
                
                // Check if we need to perform the check (rate limiting)
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCheckTime < CHECK_INTERVAL && latestVersion != null) {
                    debugLog("Using cached version check result");
                    return new UpdateCheckResult(true, currentVersion, latestVersion, updateAvailable, null);
                }
                
                // Fetch latest version from GitHub
                String fetchedVersion = fetchLatestVersionFromGitHub();
                if (fetchedVersion == null) {
                    debugLog("Failed to fetch latest version");
                    return new UpdateCheckResult(false, currentVersion, null, false, "Failed to fetch version information");
                }
                
                // Update cached values
                latestVersion = fetchedVersion;
                lastCheckTime = currentTime;
                updateAvailable = isNewerVersion(fetchedVersion, currentVersion);
                
                debugLog("Version check completed. Current: " + currentVersion + ", Latest: " + latestVersion + ", Update available: " + updateAvailable);
                
                return new UpdateCheckResult(true, currentVersion, latestVersion, updateAvailable, null);
                
            } catch (Exception e) {
                debugLog("Error during version check: " + e.getMessage());
                return new UpdateCheckResult(false, currentVersion, null, false, e.getMessage());
            }
        });
    }
    
    /**
     * Fetch latest version from GitHub pom.xml
     * @return Latest version string or null if failed
     */
    private String fetchLatestVersionFromGitHub() {
        try {
            debugLog("Fetching version from: " + GITHUB_POM_URL);
            
            URL url = new URL(GITHUB_POM_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "VerifyMC-Plugin/" + currentVersion);
            
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
            
            // Parse version from pom.xml content
            String pomContent = content.toString();
            Matcher matcher = VERSION_PATTERN.matcher(pomContent);
            
            // Find the first version tag (should be the project version)
            if (matcher.find()) {
                String version = matcher.group(1).trim();
                debugLog("Found version in pom.xml: " + version);
                return version;
            } else {
                debugLog("No version found in pom.xml content");
                return null;
            }
            
        } catch (Exception e) {
            debugLog("Exception while fetching version: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Compare two version strings to determine if the first is newer
     * @param version1 Version to check (potentially newer)
     * @param version2 Current version
     * @return true if version1 is newer than version2
     */
    private boolean isNewerVersion(String version1, String version2) {
        try {
            debugLog("Comparing versions: " + version1 + " vs " + version2);
            
            // Remove any non-numeric prefixes (like 'v')
            String v1 = version1.replaceAll("^[vV]", "");
            String v2 = version2.replaceAll("^[vV]", "");
            
            // Split versions by dots
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            
            // Compare each part
            int maxLength = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLength; i++) {
                int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
                int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
                
                if (num1 > num2) {
                    debugLog("Version " + version1 + " is newer than " + version2);
                    return true;
                } else if (num1 < num2) {
                    debugLog("Version " + version1 + " is older than " + version2);
                    return false;
                }
            }
            
            debugLog("Versions are equal");
            return false;
            
        } catch (Exception e) {
            debugLog("Error comparing versions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse version part to integer, handling non-numeric suffixes
     * @param part Version part string
     * @return Numeric value
     */
    private int parseVersionPart(String part) {
        try {
            // Extract numeric part only
            String numericPart = part.replaceAll("[^0-9].*", "");
            return numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Get current version
     * @return Current plugin version
     */
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    /**
     * Get latest version (cached)
     * @return Latest version or null if not checked yet
     */
    public String getLatestVersion() {
        return latestVersion;
    }
    
    /**
     * Check if update is available (cached)
     * @return true if update is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
    
    /**
     * Get GitHub releases URL
     * @return URL to GitHub releases page
     */
    public String getReleasesUrl() {
        return GITHUB_RELEASES_URL;
    }
    
    /**
     * Get version check result as JSON
     * @return JSON object with version information
     */
    public JSONObject getVersionInfoJson() {
        JSONObject json = new JSONObject();
        json.put("currentVersion", currentVersion);
        json.put("latestVersion", latestVersion);
        json.put("updateAvailable", updateAvailable);
        json.put("releasesUrl", GITHUB_RELEASES_URL);
        json.put("lastCheckTime", lastCheckTime);
        return json;
    }
    
    /**
     * Result class for update check operations
     */
    public static class UpdateCheckResult {
        private final boolean success;
        private final String currentVersion;
        private final String latestVersion;
        private final boolean updateAvailable;
        private final String errorMessage;
        
        public UpdateCheckResult(boolean success, String currentVersion, String latestVersion, 
                               boolean updateAvailable, String errorMessage) {
            this.success = success;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public String getCurrentVersion() { return currentVersion; }
        public String getLatestVersion() { return latestVersion; }
        public boolean isUpdateAvailable() { return updateAvailable; }
        public String getErrorMessage() { return errorMessage; }
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("success", success);
            json.put("currentVersion", currentVersion);
            json.put("latestVersion", latestVersion);
            json.put("updateAvailable", updateAvailable);
            json.put("releasesUrl", GITHUB_RELEASES_URL);
            if (errorMessage != null) {
                json.put("error", errorMessage);
            }
            return json;
        }
    }
}