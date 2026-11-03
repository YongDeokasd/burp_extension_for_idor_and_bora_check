package com.idorbola.checker.model;

/** How aggressively requests are merged before replay. Higher levels replay fewer requests. */
public enum DedupLevel {
    OFF("Off",
            "Every request is replayed individually. Nothing is merged."),
    LOW("1 — Low",
            "Merges only requests with the same method and exactly the same URL "
            + "(scheme, host, port, path and query string). Request bodies are ignored."),
    MEDIUM("2 — Medium (recommended)",
            "Merges requests to the same endpoint shape: ID-like path segments (numbers, UUIDs, hashes, "
            + "codes such as INV-8842, emails) count as one, and the query/body parameter names must match. "
            + "Parameter values may differ."),
    HIGH("3 — High",
            "Merges by path template only: any path segment containing a digit counts as an ID, and query/body "
            + "parameters are ignored. GraphQL operations stay separate. Fewest requests, but may skip variants.");

    private final String label;
    private final String description;

    DedupLevel(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
