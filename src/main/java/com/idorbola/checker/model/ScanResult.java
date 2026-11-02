package com.idorbola.checker.model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.HashMap;
import java.util.Map;

public class ScanResult {

    private final int rowIndex;
    private final RequestGroup group;
    private final Map<String, HttpRequest> requests = new HashMap<>();
    private final Map<String, HttpResponse> responses = new HashMap<>();
    private final Map<String, Map<String, Float>> similarities = new HashMap<>();

    public ScanResult(int rowIndex, RequestGroup group) {
        this.rowIndex = rowIndex;
        this.group = group;
    }

    public void putRequest(String profileId, HttpRequest request) {
        requests.put(profileId, request);
    }

    public HttpRequest getRequest(String profileId) {
        HttpRequest sent = requests.get(profileId);
        return sent != null ? sent : group.getRepresentative();
    }

    public void putResponse(String profileId, HttpResponse response) {
        responses.put(profileId, response);
    }

    public void putSimilarity(String p1Id, String p2Id, float similarity) {
        similarities.computeIfAbsent(p1Id, k -> new HashMap<>()).put(p2Id, similarity);
        similarities.computeIfAbsent(p2Id, k -> new HashMap<>()).put(p1Id, similarity);
    }

    public Float getSimilarity(String p1Id, String p2Id) {
        Map<String, Float> inner = similarities.get(p1Id);
        if (inner == null) return null;
        return inner.get(p2Id);
    }

    public Verdict getVerdict(String p1Id, String p2Id, int threshold) {
        Float sim = getSimilarity(p1Id, p2Id);
        if (sim == null) {
            HttpResponse r1 = responses.get(p1Id);
            HttpResponse r2 = responses.get(p2Id);
            if (r1 == null || r2 == null) return Verdict.PENDING;
            return Verdict.PENDING;
        }
        float pct = sim * 100f;
        if (pct >= 99.9f) return Verdict.SAME;
        if (pct >= threshold) return Verdict.SIMILAR;
        return Verdict.DIFFERENCE;
    }

    public int getStatus(String profileId) {
        HttpResponse resp = responses.get(profileId);
        if (resp == null) return -1;
        return resp.statusCode();
    }

    public int getRowIndex() { return rowIndex; }
    public RequestGroup getGroup() { return group; }
    public Map<String, HttpResponse> getResponses() { return responses; }
    public Map<String, Map<String, Float>> getSimilarities() { return similarities; }
}
