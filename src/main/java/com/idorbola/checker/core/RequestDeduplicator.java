package com.idorbola.checker.core;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.idorbola.checker.model.DedupLevel;
import com.idorbola.checker.model.RequestGroup;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups requests so only one representative per group is replayed. What counts as "the same"
 * depends on the {@link DedupLevel}. Normalization is used only for the fingerprint; the original
 * request is replayed unchanged.
 */
public class RequestDeduplicator {

    private static final Pattern UUID_SEG    = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_SEG     = Pattern.compile("^[0-9a-f]{16,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_SEG = Pattern.compile("^v\\d+(\\.\\d+)*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTENSION   = Pattern.compile("^(.+)(\\.[a-z0-9]{1,5})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARRAY_INDEX = Pattern.compile("\\[\\d*]");
    private static final Pattern MULTIPART_NAME = Pattern.compile("name=\"([^\"]*)\"");
    private static final Pattern GRAPHQL_OP  = Pattern.compile("\\b(query|mutation|subscription)\\s+(\\w+)");

    public List<RequestGroup> deduplicate(List<HttpRequest> requests, DedupLevel level) {
        Map<String, RequestGroup> seen = new LinkedHashMap<>();
        for (HttpRequest req : requests) {
            String fp = fingerprint(req, level);
            RequestGroup group = seen.get(fp);
            if (group == null) {
                seen.put(fp, new RequestGroup(req, fp));
            } else {
                group.addGrouped(req);
            }
        }
        return new ArrayList<>(seen.values());
    }

    /** OFF uses the LOW (exact URL) fingerprint so each request still has a stable identity. */
    public static String fingerprint(HttpRequest req, DedupLevel level) {
        String method = req.method().toUpperCase(Locale.ROOT);
        String origin = origin(req.httpService());
        String fullPath = req.path() == null ? "/" : req.path();
        int fragment = fullPath.indexOf('#');
        if (fragment >= 0) fullPath = fullPath.substring(0, fragment);

        if (level == DedupLevel.OFF || level == DedupLevel.LOW) {
            return sha256(method + "\n" + origin + "\n" + fullPath);
        }

        String path  = fullPath;
        String query = "";
        int qIdx = fullPath.indexOf('?');
        if (qIdx >= 0) {
            query = fullPath.substring(qIdx + 1);
            path  = fullPath.substring(0, qIdx);
        }
        BodyShape body = bodyShape(req);

        if (level == DedupLevel.HIGH) {
            String graphql = String.join(",", body.keys.stream().filter(k -> k.startsWith("graphql:")).toList());
            return sha256(method + "\n" + origin + "\n" + normalizePath(path, true) + "\n" + graphql);
        }

        return sha256(method
                + "\n" + origin
                + "\n" + normalizePath(path, false)
                + "\n" + String.join(",", formKeys(query))
                + "\n" + body.kind
                + "\n" + String.join(",", body.keys));
    }

    // ── Path ────────────────────────────────────────────────────────────────

    static String normalizePath(String path, boolean loose) {
        if (path == null || path.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            sb.append('/').append(normalizeSegment(decode(seg), loose));
        }
        return sb.length() == 0 ? "/" : sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSegment(String seg, boolean loose) {
        String name = seg;
        String ext  = "";
        Matcher m = EXTENSION.matcher(seg);
        if (m.matches() && m.group(2).chars().anyMatch(Character::isLetter)) {
            name = m.group(1);
            ext  = m.group(2);
        }
        return isIdSegment(name, loose) ? "{id}" + ext : seg;
    }

    /**
     * Heuristic: does this path segment look like an object identifier rather than a route name?
     * loose (HIGH level): any segment with a digit counts, except API versions such as v2.
     */
    static boolean isIdSegment(String seg, boolean loose) {
        if (seg.isEmpty()) return false;
        if (seg.indexOf('@') > 0) return true;
        if (VERSION_SEG.matcher(seg).matches()) return false;

        int digits = 0;
        for (int i = 0; i < seg.length(); i++) {
            if (Character.isDigit(seg.charAt(i))) digits++;
        }
        if (loose && digits > 0) return true;
        if (digits == seg.length()) return true;
        if (UUID_SEG.matcher(seg).matches()) return true;
        if (digits > 0 && HEX_SEG.matcher(seg).matches()) return true;
        if (digits >= 3) return true;
        return digits > 0 && seg.length() >= 16;
    }

    // ── Query / body keys ───────────────────────────────────────────────────

    private static SortedSet<String> formKeys(String encoded) {
        SortedSet<String> keys = new TreeSet<>();
        if (encoded == null || encoded.isEmpty()) return keys;
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq >= 0 ? pair.substring(0, eq) : pair).trim();
            if (!key.isEmpty()) keys.add(ARRAY_INDEX.matcher(key.toLowerCase(Locale.ROOT)).replaceAll("[]"));
        }
        return keys;
    }

    private record BodyShape(String kind, SortedSet<String> keys) {}

    private static BodyShape bodyShape(HttpRequest req) {
        String body = req.bodyToString();
        if (body == null || body.isBlank()) return new BodyShape("none", new TreeSet<>());

        String ct = Optional.ofNullable(req.headerValue("Content-Type")).orElse("").toLowerCase(Locale.ROOT);
        String trimmed = body.trim();

        if (ct.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            SortedSet<String> keys = jsonKeys(trimmed);
            if (keys != null) return new BodyShape("json", keys);
        }
        if (ct.contains("x-www-form-urlencoded")) return new BodyShape("form", formKeys(body));
        if (ct.contains("multipart/form-data")) {
            SortedSet<String> keys = new TreeSet<>();
            Matcher m = MULTIPART_NAME.matcher(body);
            while (m.find()) keys.add(m.group(1).toLowerCase(Locale.ROOT));
            return new BodyShape("multipart", keys);
        }
        return new BodyShape(ct.isEmpty() ? "raw" : ct.split(";")[0].trim(), new TreeSet<>());
    }

    /** Key paths of a JSON body (nested keys, arrays collapsed to "[]"); null when not valid JSON. */
    static SortedSet<String> jsonKeys(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return null;
        }
        SortedSet<String> keys = new TreeSet<>();
        collectJsonKeys(root, "", keys);

        // GraphQL: every operation shares /graphql and {query, variables}; the operation is the endpoint.
        if (root.isJsonObject()) {
            String op = graphqlOperation(root.getAsJsonObject());
            if (op != null) keys.add("graphql:" + op);
        }
        return keys;
    }

    private static void collectJsonKeys(JsonElement el, String prefix, Set<String> out) {
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                out.add(key.toLowerCase(Locale.ROOT));
                collectJsonKeys(e.getValue(), key, out);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                collectJsonKeys(item, prefix + "[]", out);
            }
        }
    }

    private static String graphqlOperation(JsonObject obj) {
        JsonElement opName = obj.get("operationName");
        if (opName != null && opName.isJsonPrimitive()) return opName.getAsString();
        JsonElement query = obj.get("query");
        if (query == null || !query.isJsonPrimitive()) return null;
        String q = query.getAsString();
        Matcher m = GRAPHQL_OP.matcher(q);
        if (m.find()) return m.group(1) + " " + m.group(2);
        return q.replaceAll("\\s+", " ").trim();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String origin(HttpService service) {
        if (service == null) return "";
        return (service.secure() ? "https://" : "http://")
                + service.host().toLowerCase(Locale.ROOT) + ":" + service.port();
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
