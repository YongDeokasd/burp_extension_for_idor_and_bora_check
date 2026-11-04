package com.idorbola.checker;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import com.idorbola.checker.ui.MainPanel;

import java.util.List;
import java.util.Objects;

/** Proxy > HTTP history: the hotkey sends the selected rows to the IDOR/BOLA scan (same as "Scan selected"). */
final class HistoryHotKey {

    static final String KEY = "Ctrl+Shift+C";

    private HistoryHotKey() {}

    /** Requires Burp with Montoya 2025.12+; callers must catch LinkageError for older versions. */
    static void register(MontoyaApi api, MainPanel mainPanel) {
        Registration registration = api.userInterface().registerHotKeyHandler(
                HotKeyContext.PROXY_HTTP_HISTORY, KEY, event -> {
                    List<HttpRequest> requests = event.selectedRequestResponses().stream()
                            .map(HttpRequestResponse::request)
                            .filter(Objects::nonNull)
                            .toList();
                    if (!requests.isEmpty()) mainPanel.onScanRequested(requests);
                });
        api.logging().logToOutput(registration.isRegistered()
                ? "Proxy history hotkey " + KEY + " → IDOR/BOLA scan registered."
                : "Proxy history hotkey " + KEY + " could not be registered (key may be reserved by Burp).");
    }
}
