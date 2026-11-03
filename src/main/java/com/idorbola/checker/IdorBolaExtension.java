package com.idorbola.checker;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.idorbola.checker.ui.MainPanel;

public class IdorBolaExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("IDOR/BOLA Checker");

        MainPanel mainPanel = new MainPanel(api);

        api.userInterface().registerSuiteTab("IDOR/BOLA", mainPanel);

        api.userInterface().registerContextMenuItemsProvider(
                new SitemapContextMenu(api, mainPanel));

        try {
            HistoryHotKey.register(api, mainPanel);
        } catch (LinkageError e) {
            api.logging().logToOutput("Proxy history hotkey needs Burp Suite with Montoya API 2025.12 or later; "
                    + "use right-click > IDOR/BOLA Checker instead.");
        } catch (RuntimeException e) {
            api.logging().logToError("Proxy history hotkey registration failed: " + e.getMessage());
        }

        api.logging().logToOutput("IDOR/BOLA Checker loaded.");
    }
}
