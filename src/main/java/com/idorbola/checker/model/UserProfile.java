package com.idorbola.checker.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class UserProfile {

    private String id;
    private String name;
    private String cookieHeader;
    private Map<String, String> customHeaders;

    public UserProfile() {
        this.id = UUID.randomUUID().toString();
        this.customHeaders = new LinkedHashMap<>();
    }

    public UserProfile(String name, String cookieHeader) {
        this();
        this.name = name;
        this.cookieHeader = cookieHeader;
    }

    /** Returns "A" for index 0, "B" for 1, etc. Supports up to 26 profiles. */
    public static String nextLabel(int index) {
        if (index < 0 || index > 25) return String.valueOf(index + 1);
        return String.valueOf((char) ('A' + index));
    }

    /** Returns the single-letter label based on the profile's position in the list.
     *  Set via setLabel() after construction when list position is known. */
    private String label;

    public String getLabel() {
        return label != null ? label : "?";
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCookieHeader() { return cookieHeader != null ? cookieHeader : ""; }
    public void setCookieHeader(String cookieHeader) { this.cookieHeader = cookieHeader; }

    public Map<String, String> getCustomHeaders() {
        if (customHeaders == null) customHeaders = new LinkedHashMap<>();
        return customHeaders;
    }
    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    /** Preview of cookie for display (first 40 chars). */
    public String getCookiePreview() {
        String c = getCookieHeader();
        if (c.length() <= 40) return c;
        return c.substring(0, 37) + "...";
    }

    @Override
    public String toString() {
        return getLabel() + " — " + name;
    }
}
