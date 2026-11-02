package com.idorbola.checker.model;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

public class RequestGroup {

    private final HttpRequest representative;
    private final List<HttpRequest> grouped;
    private final String fingerprint;

    public RequestGroup(HttpRequest representative, String fingerprint) {
        this.representative = representative;
        this.fingerprint = fingerprint;
        this.grouped = new ArrayList<>();
    }

    public void addGrouped(HttpRequest request) {
        grouped.add(request);
    }

    public HttpRequest getRepresentative() { return representative; }
    public List<HttpRequest> getGrouped() { return grouped; }
    public String getFingerprint() { return fingerprint; }

    /** Total number of requests in this group (representative + grouped duplicates). */
    public int size() { return 1 + grouped.size(); }
}
