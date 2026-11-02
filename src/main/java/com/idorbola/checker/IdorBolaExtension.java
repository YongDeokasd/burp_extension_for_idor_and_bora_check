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

        api.logging().logToOutput("IDOR/BOLA Checker loaded.");
    }
}
