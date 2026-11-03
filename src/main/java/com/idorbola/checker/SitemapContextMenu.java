package com.idorbola.checker;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.sitemap.SiteMapFilter;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import com.idorbola.checker.ui.MainPanel;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Right-click menu. selectedRequestResponses() only returns the items of the clicked node(s),
 * not the branch below them, so whole-domain scans query the site map by URL prefix instead.
 */
public class SitemapContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi api;
    private final MainPanel  mainPanel;

    public SitemapContextMenu(MontoyaApi api, MainPanel mainPanel) {
        this.api       = api;
        this.mainPanel = mainPanel;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequest> selected = new ArrayList<>();
        event.messageEditorRequestResponse().ifPresent(me -> selected.add(me.requestResponse().request()));
        for (HttpRequestResponse rr : event.selectedRequestResponses()) {
            if (rr.request() != null) selected.add(rr.request());
        }

        List<Component> items = new ArrayList<>();
        if (!selected.isEmpty()) {
            JMenuItem scanSelected = new JMenuItem("IDOR/BOLA Checker — Scan selected (" + selected.size() + ")");
            List<HttpRequest> snapshot = List.copyOf(selected);
            scanSelected.addActionListener(e -> mainPanel.onScanRequested(snapshot));
            items.add(scanSelected);
        }

        if (event.isFrom(InvocationType.SITE_MAP_TREE, InvocationType.SITE_MAP_TABLE)) {
            if (!selected.isEmpty()) {
                for (String origin : origins(selected)) {
                    items.add(hostItem("IDOR/BOLA Checker — Scan all site map requests for " + origin, origin));
                }
            } else {
                // Folder nodes without a request of their own give no selection: offer every host instead.
                List<HttpRequest> all = new ArrayList<>();
                for (HttpRequestResponse rr : api.siteMap().requestResponses()) {
                    if (rr.request() != null) all.add(rr.request());
                }
                Set<String> hosts = origins(all);
                if (!hosts.isEmpty()) {
                    JMenu menu = new JMenu("IDOR/BOLA Checker — Scan all site map requests for host");
                    for (String origin : hosts) menu.add(hostItem(origin, origin));
                    items.add(menu);
                }
            }
        }
        return items;
    }

    private JMenuItem hostItem(String text, String origin) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> scanSiteMapPrefix(origin + "/"));
        return item;
    }

    private void scanSiteMapPrefix(String prefix) {
        Thread t = new Thread(() -> {
            List<HttpRequest> requests = new ArrayList<>();
            for (HttpRequestResponse rr : api.siteMap().requestResponses(SiteMapFilter.prefixFilter(prefix))) {
                if (rr.request() != null) requests.add(rr.request());
            }
            api.logging().logToOutput("IDOR/BOLA Checker: " + requests.size() + " site map request(s) under " + prefix);
            if (requests.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainPanel,
                        "No requests found in the site map under " + prefix,
                        "IDOR/BOLA Checker", JOptionPane.INFORMATION_MESSAGE));
                return;
            }
            mainPanel.onScanRequested(requests);
        }, "idor-bola-sitemap-collect");
        t.setDaemon(true);
        t.start();
    }

    /** scheme://host[:port] of each selected request, in the same form Burp uses for site map URLs. */
    private static Set<String> origins(List<HttpRequest> requests) {
        Set<String> origins = new LinkedHashSet<>();
        for (HttpRequest req : requests) {
            try {
                URI uri = URI.create(req.url());
                if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                    origins.add(uri.getScheme() + "://" + uri.getRawAuthority());
                }
            } catch (IllegalArgumentException ignored) {
                // Unparseable URL: still scannable via "Scan selected".
            }
        }
        return origins;
    }
}
