package com.idorbola.checker.core;

import java.util.regex.Pattern;

public class ResponseComparator {

    // Patterns for value normalization in JSON bodies
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern LARGE_NUMBER_PATTERN = Pattern.compile("\\b\\d{6,}\\b");

    // Pattern for hidden input values in HTML (CSRF tokens)
    private static final Pattern HIDDEN_INPUT_PATTERN = Pattern.compile(
            "<input[^>]+type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE_ATTR_PATTERN = Pattern.compile(
            "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    /**
     * Compare two response bodies and return a similarity score [0.0, 1.0].
     */
    public float compare(String bodyA, String bodyB, String contentType) {
        if (bodyA == null) bodyA = "";
        if (bodyB == null) bodyB = "";

        if (bodyA.isEmpty() && bodyB.isEmpty()) return 1.0f;
        if (bodyA.isEmpty() || bodyB.isEmpty()) return 0.0f;

        String ct = contentType != null ? contentType.toLowerCase() : "";

        if (ct.contains("application/json")) {
            return compareJson(bodyA, bodyB);
        } else if (ct.contains("text/html")) {
            return compareHtml(bodyA, bodyB);
        } else {
            return compareText(bodyA, bodyB);
        }
    }

    private float compareJson(String a, String b) {
        String normA = normalizeJsonValues(a);
        String normB = normalizeJsonValues(b);
        return levenshteinRatio(normA, normB);
    }

    private float compareHtml(String a, String b) {
        String csrfA = extractHiddenValues(a);
        String csrfB = extractHiddenValues(b);
        // If no hidden inputs found, fall back to text comparison
        if (csrfA.isEmpty() && csrfB.isEmpty()) {
            return compareText(a, b);
        }
        return levenshteinRatio(csrfA, csrfB);
    }

    private float compareText(String a, String b) {
        String normA = normalizeWhitespace(a);
        String normB = normalizeWhitespace(b);
        return levenshteinRatio(normA, normB);
    }

    /** Replace dynamic values (UUIDs, timestamps, large numbers) with placeholders. */
    static String normalizeJsonValues(String json) {
        String result = json;
        result = UUID_PATTERN.matcher(result).replaceAll("__UUID__");
        result = ISO_DATE_PATTERN.matcher(result).replaceAll("__TIMESTAMP__");
        result = LARGE_NUMBER_PATTERN.matcher(result).replaceAll("__NUM__");
        return result;
    }

    /** Extract all hidden input values concatenated (for CSRF comparison). */
    static String extractHiddenValues(String html) {
        StringBuilder sb = new StringBuilder();
        var matcher = HIDDEN_INPUT_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            var valueMatcher = VALUE_ATTR_PATTERN.matcher(tag);
            if (valueMatcher.find()) {
                sb.append(valueMatcher.group(1)).append("|");
            }
        }
        return sb.toString();
    }

    private static String normalizeWhitespace(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Computes normalized Levenshtein similarity: 1.0 - distance / max(len(a), len(b)).
     * Uses capped strings to avoid O(n²) on very large bodies.
     */
    static float levenshteinRatio(String a, String b) {
        // Cap at 4000 chars to keep it performant
        final int MAX = 4000;
        if (a.length() > MAX) a = a.substring(0, MAX);
        if (b.length() > MAX) b = b.substring(0, MAX);

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0f;

        int dist = levenshteinDistance(a, b);
        return 1.0f - (float) dist / maxLen;
    }

    static int levenshteinDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) prev[j] = j;

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
                }
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }
}
