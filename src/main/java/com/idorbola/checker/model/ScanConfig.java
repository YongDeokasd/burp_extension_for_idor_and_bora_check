package com.idorbola.checker.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScanConfig {

    private List<UserProfile> profiles = new ArrayList<>();
    private boolean deduplicationEnabled = true;
    private int threadPoolSize = 10;
    private int rateLimitPerSecond = 20;
    private int profileDelayMs = 0;
    private int timeoutSeconds = 10;
    private List<String> exclusionPatterns = new ArrayList<>();
    private Set<String> excludedExtensions = new HashSet<>(Arrays.asList(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map", ".mp4",
            ".mp3", ".webm", ".avi", ".mov"
    ));
    private List<String> excludedPathPrefixes = new ArrayList<>(Arrays.asList(
            "/static/", "/assets/", "/resources/", "/_next/", "/dist/"
    ));
    private int similarityThreshold = 80;

    public ScanConfig() {}

    /**
     * Returns true if this method+path combination should be excluded from scanning.
     * Pattern formats: "/path/prefix/*", "METHOD:/path", "/exact/path"
     */
    public boolean isExcluded(String method, String path) {
        for (String pattern : exclusionPatterns) {
            if (pattern == null || pattern.isBlank()) continue;
            String p = pattern.trim();
            // Method-specific pattern: "POST:/api/user/delete"
            if (p.contains(":/")) {
                int colon = p.indexOf(":/");
                String patMethod = p.substring(0, colon).toUpperCase();
                String patPath   = p.substring(colon + 1);
                if (!patMethod.equals(method.toUpperCase())) continue;
                if (matchesPattern(path, patPath)) return true;
            } else {
                if (matchesPattern(path, p)) return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix);
        }
        return path.equals(pattern);
    }

    /**
     * Returns true if the path points to a static resource that should be filtered.
     * Note: .pdf is intentionally NOT filtered (authorization check target).
     */
    public boolean isStaticResource(String path) {
        if (path == null) return false;
        // Check excluded path prefixes
        for (String prefix : excludedPathPrefixes) {
            if (path.startsWith(prefix)) return true;
        }
        // Check excluded extensions
        String lower = path.toLowerCase();
        int qmark = lower.indexOf('?');
        String pathOnly = qmark >= 0 ? lower.substring(0, qmark) : lower;
        for (String ext : excludedExtensions) {
            if (pathOnly.endsWith(ext)) return true;
        }
        return false;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public List<UserProfile> getProfiles() { return profiles; }
    public void setProfiles(List<UserProfile> profiles) {
        this.profiles = profiles;
        // Assign labels
        for (int i = 0; i < profiles.size(); i++) {
            profiles.get(i).setLabel(UserProfile.nextLabel(i));
        }
    }

    public boolean isDeduplicationEnabled() { return deduplicationEnabled; }
    public void setDeduplicationEnabled(boolean deduplicationEnabled) {
        this.deduplicationEnabled = deduplicationEnabled;
    }

    public int getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = Math.max(1, threadPoolSize); }

    public int getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(int rateLimitPerSecond) {
        this.rateLimitPerSecond = Math.max(1, rateLimitPerSecond);
    }

    public int getProfileDelayMs() { return profileDelayMs; }
    public void setProfileDelayMs(int profileDelayMs) { this.profileDelayMs = Math.max(0, profileDelayMs); }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = Math.max(1, timeoutSeconds); }

    public List<String> getExclusionPatterns() { return exclusionPatterns; }
    public void setExclusionPatterns(List<String> exclusionPatterns) {
        this.exclusionPatterns = exclusionPatterns;
    }

    public Set<String> getExcludedExtensions() { return excludedExtensions; }
    public void setExcludedExtensions(Set<String> excludedExtensions) {
        this.excludedExtensions = excludedExtensions;
    }

    public List<String> getExcludedPathPrefixes() { return excludedPathPrefixes; }
    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes;
    }

    public int getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(int similarityThreshold) {
        this.similarityThreshold = Math.max(0, Math.min(100, similarityThreshold));
    }
}
