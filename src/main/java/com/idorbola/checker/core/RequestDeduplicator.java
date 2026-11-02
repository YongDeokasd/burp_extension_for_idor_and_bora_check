package com.idorbola.checker.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.idorbola.checker.model.RequestGroup;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class RequestDeduplicator {

    private static final Pattern DIGIT_SEGMENT  = Pattern.compile("^\\d+$");
    private static final Pattern UUID_SEGMENT   = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Deduplicates requests by fingerprint. The first request with a given fingerprint
     * becomes the representative; subsequent matches are stored in its group.
     */
    public List<RequestGroup> deduplicate(List<HttpRequest> requests) {
        Map<String, RequestGroup> seen = new LinkedHashMap<>();
        for (HttpRequest req : requests) {
            String fp = fingerprint(req);
            RequestGroup group = seen.get(fp);
            if (group == null) {
                group = new RequestGroup(req, fp);
                seen.put(fp, group);
            } else {
                group.addGrouped(req);
            }
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Computes SHA-256 fingerprint for a request.
     * Fingerprint = SHA256(METHOD + "\n" + normalizedPath + "\n" + sortedQueryKeys + "\n" + sortedBodyKeys)
     * Original URL is NOT modified — normalization is only for fingerprinting.
     */
    public static String fingerprint(HttpRequest req) {
        String method = req.method().toUpperCase();
        String path = req.path();

        // Strip query string from path for normalization
        String pathOnly = path;
        String query = "";
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) {
            pathOnly = path.substring(0, qIdx);
            query    = path.substring(qIdx + 1);
        }

        String normalizedPath = normalizePath(pathOnly);
        String sortedQueryKeys = sortedKeys(parseQueryKeys(query));
        String sortedBodyKeys  = sortedKeys(parseBodyKeys(req));

        String raw = method + "\n" + normalizedPath + "\n" + sortedQueryKeys + "\n" + sortedBodyKeys;
        return sha256(raw);
    }

    /** Replace digit-only and UUID path segments with {id}. */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] segments = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (DIGIT_SEGMENT.matcher(seg).matches() || UUID_SEGMENT.matcher(seg).matches()) {
                seg = "{id}";
            }
            sb.append(seg);
            if (i < segments.length - 1) sb.append('/');
        }
        return sb.toString();
    }

    private static List<String> parseQueryKeys(String query) {
        List<String> keys = new ArrayList<>();
        if (query == null || query.isEmpty()) return keys;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            try { key = URLDecoder.decode(key, StandardCharsets.UTF_8); } catch (Exception ignored) {}
            if (!key.isEmpty()) keys.add(key.toLowerCase());
        }
        return keys;
    }

    private static List<String> parseBodyKeys(HttpRequest req) {
        List<String> keys = new ArrayList<>();
        String contentType = req.headerValue("Content-Type");
        if (contentType == null) return keys;
        String body = req.bodyToString();
        if (body == null || body.isEmpty()) return keys;

        if (contentType.contains("application/x-www-form-urlencoded")) {
            keys.addAll(parseQueryKeys(body));
        } else if (contentType.contains("application/json")) {
            keys.addAll(extractJsonKeys(body));
        }
        return keys;
    }

    /** Extract top-level JSON keys (shallow) for fingerprinting. */
    static List<String> extractJsonKeys(String json) {
        List<String> keys = new ArrayList<>();
        if (json == null || !json.trim().startsWith("{")) return keys;
        // Simple regex-free key extraction: look for "key":
        int i = 0;
        while (i < json.length()) {
            int start = json.indexOf('"', i);
            if (start < 0) break;
            int end = json.indexOf('"', start + 1);
            if (end < 0) break;
            String key = json.substring(start + 1, end);
            // Check if followed by ':'
            int colon = json.indexOf(':', end + 1);
            if (colon >= 0 && json.substring(end + 1, colon).isBlank()) {
                keys.add(key.toLowerCase());
            }
            i = end + 1;
        }
        return keys;
    }

    private static String sortedKeys(List<String> keys) {
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
