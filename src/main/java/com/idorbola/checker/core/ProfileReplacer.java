package com.idorbola.checker.core;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.idorbola.checker.model.UserProfile;

import java.util.Map;

public class ProfileReplacer {

    /**
     * Returns a copy of the request with the profile's Cookie header and custom headers applied.
     * Profile values win on conflict.
     */
    public HttpRequest apply(HttpRequest original, UserProfile profile) {
        HttpRequest req = original;

        // Replace Cookie header with profile's cookie (if non-empty)
        String cookie = profile.getCookieHeader();
        if (cookie != null && !cookie.isBlank()) {
            if (req.hasHeader("Cookie")) {
                req = req.withUpdatedHeader("Cookie", cookie);
            } else {
                req = req.withAddedHeader("Cookie", cookie);
            }
        }

        // Apply custom headers (add or replace)
        for (Map.Entry<String, String> entry : profile.getCustomHeaders().entrySet()) {
            String name  = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.isBlank()) continue;
            if (req.hasHeader(name)) {
                req = req.withUpdatedHeader(name, value);
            } else {
                req = req.withAddedHeader(name, value);
            }
        }

        return req;
    }
}
