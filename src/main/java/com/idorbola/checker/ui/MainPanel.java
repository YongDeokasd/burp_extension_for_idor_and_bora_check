package com.idorbola.checker.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.Gson;
import com.idorbola.checker.core.ScanEngine;
import com.idorbola.checker.model.ScanConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class MainPanel extends JPanel {

    private final MontoyaApi api;
    private final SettingsPanel settingsPanel;
    private final ResultsPanel  resultsPanel;
    private final JTabbedPane   tabs;

    private static final String SETTINGS_KEY = "idor_bola_config";

    public MainPanel(MontoyaApi api) {
        this.api = api;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        settingsPanel = new SettingsPanel();
        resultsPanel  = new ResultsPanel();

        tabs = new JTabbedPane();
        tabs.setBackground(Theme.PANEL);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(Theme.UI);

        tabs.addTab("Settings", wrapTab(settingsPanel));
        tabs.addTab("Results",  wrapTab(resultsPanel));

        add(tabs, BorderLayout.CENTER);

        // When profiles change in settings, update results panel dropdowns
        settingsPanel.setOnProfilesChanged(profiles -> {
            resultsPanel.updateProfiles(profiles);
            saveConfig();
        });

        loadConfig();
    }

    /**
     * Called by the context menu when the user selects requests to scan.
     * Switches to the Results tab and starts the scan.
     */
    public void onScanRequested(List<HttpRequest> requests) {
        SwingUtilities.invokeLater(() -> {
            ScanConfig config = settingsPanel.getConfig();

            if (config.getProfiles().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No profiles configured. Please add at least one user profile in Settings.",
                        "IDOR/BOLA Checker", JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }

            saveConfig();
            tabs.setSelectedIndex(1);
            resultsPanel.prepareScan(config);

            ScanEngine.ScanListener ui = resultsPanel.getScanListener();
            ScanEngine engine = new ScanEngine(api, config, resultsPanel.getScanContext(), new ScanEngine.ScanListener() {
                @Override public void onResult(com.idorbola.checker.model.ScanResult r) {
                    SwingUtilities.invokeLater(() -> ui.onResult(r));
                }
                @Override public void onProgress(int done, int total) {
                    SwingUtilities.invokeLater(() -> ui.onProgress(done, total));
                }
                @Override public void onComplete() {
                    SwingUtilities.invokeLater(ui::onComplete);
                }
                @Override public void onError(String msg) {
                    SwingUtilities.invokeLater(() -> ui.onError(msg));
                }
                @Override public void onPlanned(int received, int excluded, int toScan) {
                    SwingUtilities.invokeLater(() -> ui.onPlanned(received, excluded, toScan));
                }
                @Override public void onDuplicate(com.idorbola.checker.model.RequestGroup group) {
                    SwingUtilities.invokeLater(() -> ui.onDuplicate(group));
                }
            });

            resultsPanel.setScanEngine(engine);
            engine.startScan(requests);
        });
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void saveConfig() {
        try {
            ScanConfig config = settingsPanel.getConfig();
            String json = new Gson().toJson(config);
            api.persistence().extensionData().setString(SETTINGS_KEY, json);
        } catch (Exception e) {
            api.logging().logToError("Failed to save config: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            String json = api.persistence().extensionData().getString(SETTINGS_KEY);
            if (json != null && !json.isBlank()) {
                ScanConfig config = new Gson().fromJson(json, ScanConfig.class);
                settingsPanel.loadConfig(config);
                resultsPanel.updateProfiles(config.getProfiles());
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to load config: " + e.getMessage());
        }
    }

    private JPanel wrapTab(JComponent component) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG);
        p.setBorder(new EmptyBorder(0, 0, 0, 0));
        p.add(component, BorderLayout.CENTER);
        return p;
    }
}
