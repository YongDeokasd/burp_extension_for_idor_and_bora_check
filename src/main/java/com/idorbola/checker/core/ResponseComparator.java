package com.idorbola.checker.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseComparator {

    // Patterns for value normalization in JSON bodies
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern LARGE_NUMBER_PATTERN = Pattern.compile("\\b\\d{6,}\\b");

    // Per-request HTML values (CSRF tokens, nonces) that differ on every response
    private static final Pattern INPUT_TAG = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIDDEN_TYPE = Pattern.compile("\\btype\\s*=\\s*[\"']?hidden\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE_ATTR = Pattern.compile(
            "\\bvalue\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSRF_META = Pattern.compile(
            "<meta\\b[^>]*\\bname\\s*=\\s*[\"'][^\"']*(csrf|xsrf)[^\"']*[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR = Pattern.compile(
            "\\bcontent\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NONCE_ATTR = Pattern.compile(
            "\\bnonce\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);

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
        return compareText(normalizeHtml(a), normalizeHtml(b));
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

    /** Blank values that change on every response (hidden inputs, CSRF meta tags, nonces); keep the rest of the page. */
    static String normalizeHtml(String html) {
        String result = replaceInTags(html, INPUT_TAG, VALUE_ATTR, "value=\"__HIDDEN__\"", true);
        result = replaceInTags(result, CSRF_META, CONTENT_ATTR, "content=\"__CSRF__\"", false);
        return NONCE_ATTR.matcher(result).replaceAll("nonce=\"__NONCE__\"");
    }

    private static String replaceInTags(String html, Pattern tagPattern, Pattern attrPattern,
                                        String replacement, boolean hiddenInputsOnly) {
        Matcher tags = tagPattern.matcher(html);
        StringBuilder sb = new StringBuilder(html.length());
        while (tags.find()) {
            String tag = tags.group();
            if (!hiddenInputsOnly || HIDDEN_TYPE.matcher(tag).find()) {
                tag = attrPattern.matcher(tag).replaceAll(Matcher.quoteReplacement(replacement));
            }
            tags.appendReplacement(sb, Matcher.quoteReplacement(tag));
        }
        tags.appendTail(sb);
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
