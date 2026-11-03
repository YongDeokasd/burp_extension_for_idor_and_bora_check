package com.idorbola.checker.util;

import com.idorbola.checker.model.ScanResult;
import com.idorbola.checker.model.UserProfile;
import com.idorbola.checker.model.Verdict;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvExporter {

    public static void export(File file,
                              List<ScanResult> results,
                              List<UserProfile> profiles,
                              String p1Id,
                              String p2Id,
                              int threshold) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write('﻿'); // BOM so Excel opens Korean text as UTF-8
            // Header
            StringBuilder header = new StringBuilder("#,Method,URL,Group,Fingerprint");
            for (UserProfile p : profiles) {
                header.append(",").append(escape(p.getLabel() + " Status"));
            }
            header.append(",Verdict (").append(labelOf(profiles, p1Id))
                  .append(" vs ").append(labelOf(profiles, p2Id)).append(")")
                  .append(",Similarity");
            w.write(header.toString());
            w.newLine();

            // Rows
            for (ScanResult r : results) {
                StringBuilder row = new StringBuilder();
                row.append(r.getRowIndex() + 1).append(",");
                row.append(escape(r.getGroup().getRepresentative().method())).append(",");
                row.append(escape(fullUrl(r))).append(",");
                row.append(r.getGroup().size() > 1 ? r.getGroup().size() : "").append(",");
                row.append(escape(r.getGroup().getFingerprint()));
                for (UserProfile p : profiles) {
                    int code = r.getStatus(p.getId());
                    row.append(",").append(code >= 0 ? code : "");
                }
                Verdict v = (p1Id != null && p2Id != null)
                        ? r.getVerdict(p1Id, p2Id, threshold)
                        : Verdict.PENDING;
                Float sim = (p1Id != null && p2Id != null)
                        ? r.getSimilarity(p1Id, p2Id)
                        : null;
                row.append(",").append(v.name());
                row.append(",").append(sim != null ? String.format("%.1f%%", sim * 100f) : "");
                w.write(row.toString());
                w.newLine();
            }
        }
    }

    private static String labelOf(List<UserProfile> profiles, String id) {
        if (id == null) return "?";
        return profiles.stream()
                .filter(p -> p.getId().equals(id))
                .map(UserProfile::getLabel)
                .findFirst().orElse("?");
    }

    private static String fullUrl(ScanResult r) {
        try {
            var req = r.getGroup().getRepresentative();
            var svc = req.httpService();
            return (svc.secure() ? "https" : "http") + "://" + svc.host()
                    + (svc.port() == 80 || svc.port() == 443 ? "" : ":" + svc.port())
                    + HttpText.displayPath(req.path());
        } catch (Exception e) {
            return HttpText.displayPath(r.getGroup().getRepresentative().path());
        }
    }

    /**
     * Opens a file-save dialog and exports all rows in {@code model} to a CSV file.
     */
    public static void exportToFile(Component parent,
                                    com.idorbola.checker.ui.ResultTableModel model,
                                    List<UserProfile> profiles,
                                    String p1Id, String p2Id, int threshold) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Results as CSV");
        chooser.setSelectedFile(new File("idor-bola-results.csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File dest = chooser.getSelectedFile();

        List<ScanResult> rows = new java.util.ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            ScanResult r = model.getResultAt(i);
            if (r != null) rows.add(r);
        }

        try {
            export(dest, rows, profiles, p1Id, p2Id, threshold);
            JOptionPane.showMessageDialog(parent,
                    "Exported " + rows.size() + " rows to:\n" + dest.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
