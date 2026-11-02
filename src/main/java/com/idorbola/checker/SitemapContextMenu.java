package com.idorbola.checker;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.idorbola.checker.ui.MainPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SitemapContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi api;
    private final MainPanel  mainPanel;

    public SitemapContextMenu(MontoyaApi api, MainPanel mainPanel) {
        this.api       = api;
        this.mainPanel = mainPanel;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();

        // Collect requests from all selected items across supported tools
        List<HttpRequest> requests = new ArrayList<>();

        // Message editor context (single request)
        event.messageEditorRequestResponse().ifPresent(me ->
                requests.add(me.requestResponse().request()));

        // Multi-select context (Proxy/Target sitemap, Repeater, etc.)
        if (!event.selectedRequestResponses().isEmpty()) {
            for (var rr : event.selectedRequestResponses()) {
                requests.add(rr.request());
            }
        }

        if (requests.isEmpty()) return items;

        JMenuItem scanItem = new JMenuItem("IDOR/BOLA Checker — Scan " + requests.size() + " request(s)");
        scanItem.setFont(UIManager.getFont("MenuItem.font"));
        final List<HttpRequest> snapshot = List.copyOf(requests);
        scanItem.addActionListener(e -> SwingUtilities.invokeLater(() ->
                mainPanel.onScanRequested(snapshot)));
        items.add(scanItem);

        return items;
    }
}
