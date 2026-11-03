package com.idorbola.checker.ui;

import com.idorbola.checker.core.ScanEngine;
import com.idorbola.checker.model.*;
import com.idorbola.checker.util.CsvExporter;
import com.idorbola.checker.util.HttpText;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ResultsPanel extends JPanel {

    // Progress area
    private final JLabel progressLabel;
    private final JProgressBar progressBar;
    private final JLabel metaLabel;
    private final JButton pauseButton;
    private final JButton cancelButton;

    // Summary area
    private final JLabel totalLabel;
    private final JLabel sameLabel;
    private final JLabel similarLabel;
    private final JLabel diffLabel;
    private final JLabel pendingLabel;

    // Profile pair selector
    private final JComboBox<String> p1Combo;
    private final JComboBox<String> p2Combo;
    private final JLabel pairNoteLabel;

    // Filter bar
    private final JToggleButton filterAll;
    private final JToggleButton filterSame;
    private final JToggleButton filterSimilar;
    private final JToggleButton filterDiff;
    private final JComboBox<String> methodFilter;
    private final JComboBox<String> p1StatusFilter;
    private final JComboBox<String> p2StatusFilter;
    private final JLabel p1StatusLabel;
    private final JLabel p2StatusLabel;
    private final JTextField searchField;

    // Table
    private final ResultTableModel tableModel;
    private final JTable resultsTable;

    // Detail panel
    private final JTabbedPane detailTabs;
    private final MessageView p1Detail;
    private final MessageView p2Detail;
    private final JTextPane diffDetail;

    // State
    private ScanConfig scanConfig;
    private ScanEngine scanEngine;
    private List<UserProfile> profiles = List.of();
    private boolean suppressPairEvents = false;
    private final AtomicInteger doneCount   = new AtomicInteger(0);
    private final AtomicInteger errorCount  = new AtomicInteger(0);
    private final AtomicLong    scanStart   = new AtomicLong(0);
    private int skippedDuplicates = 0;
    private int scanTotal = 0;

    // Shared by every scan appended to this table; reset by Clear.
    private final ScanEngine.ScanContext scanContext =
            new ScanEngine.ScanContext(new AtomicInteger(0), ConcurrentHashMap.newKeySet());
    // Duplicates reported before their original row arrived (scan still in flight).
    private final Map<String, List<RequestGroup>> pendingMerges = new HashMap<>();
    private final ScanEngine.ScanListener scanListener = createScanListener();

    public ResultsPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        progressLabel = Theme.label("Idle");
        progressBar = new JProgressBar(0, 100);
        progressBar.setBackground(Theme.TRACK);
        progressBar.setForeground(Theme.ACCENT);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 5));
        metaLabel = Theme.dimLabel("");
        pauseButton  = Theme.button("Pause");
        cancelButton = Theme.button("Cancel");

        totalLabel   = boldCountLabel("0", Theme.TEXT_BRIGHT);
        sameLabel    = boldCountLabel("0", Theme.SAME);
        similarLabel = boldCountLabel("0", Theme.SIMILAR);
        diffLabel    = boldCountLabel("0", Theme.DIFF);
        pendingLabel = boldCountLabel("0", Theme.TEXT_DIM);

        p1Combo     = Theme.<String>comboBox();
        p2Combo     = Theme.<String>comboBox();
        pairNoteLabel = Theme.dimLabel("");

        filterAll     = filterToggle("All");
        filterSame    = filterToggle("SAME");
        filterSimilar = filterToggle("SIMILAR");
        filterDiff    = filterToggle("DIFFERENCE");
        filterAll.setSelected(true);
        methodFilter   = Theme.<String>comboBox();
        methodFilter.addItem(ResultTableModel.ANY);
        p1StatusFilter = Theme.<String>comboBox();
        p2StatusFilter = Theme.<String>comboBox();
        for (String s : ResultTableModel.STATUS_CLASSES) {
            p1StatusFilter.addItem(s);
            p2StatusFilter.addItem(s);
        }
        p1StatusLabel = Theme.dimLabel("P1 Status:");
        p2StatusLabel = Theme.dimLabel("P2 Status:");
        searchField = Theme.textField(18);
        searchField.setToolTipText("Filter by URL...");

        tableModel   = new ResultTableModel();
        resultsTable = Theme.table();
        resultsTable.setModel(tableModel);
        configureTableColumns();

        detailTabs = new JTabbedPane();
        detailTabs.setBackground(Theme.PANEL);
        detailTabs.setForeground(Theme.TEXT);
        p1Detail   = new MessageView();
        p2Detail   = new MessageView();
        diffDetail = detailPane();
        detailTabs.addTab("P1", p1Detail);
        detailTabs.addTab("P2", p2Detail);
        detailTabs.addTab("Diff View", Theme.scrollPane(diffDetail));

        buildUI();
        wireEvents();
    }

    private void buildUI() {
        JPanel top = Theme.panel(Theme.BG);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        top.add(buildProgressRow());
        top.add(sep());
        top.add(buildSummaryRow());
        top.add(sep());
        top.add(buildPairRow());
        top.add(sep());
        top.add(buildFilterRow());
        top.add(sep());

        add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                Theme.scrollPane(resultsTable), buildDetailPanel());
        split.setDividerLocation(280);
        split.setBackground(Theme.BG);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildProgressRow() {
        JPanel p = Theme.panel(Theme.BG);
        p.setLayout(new BorderLayout(6, 0));
        p.setBorder(new EmptyBorder(6, 10, 4, 10));

        p.add(progressLabel, BorderLayout.CENTER);

        JPanel btnRow = Theme.panel(Theme.BG);
        btnRow.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.add(pauseButton);
        btnRow.add(cancelButton);
        p.add(btnRow, BorderLayout.EAST);

        JPanel wrap = Theme.panel(Theme.BG);
        wrap.setLayout(new BorderLayout(0, 2));
        wrap.setBorder(new EmptyBorder(4, 10, 4, 10));
        wrap.add(p, BorderLayout.NORTH);
        wrap.add(progressBar, BorderLayout.CENTER);
        wrap.add(metaLabel, BorderLayout.SOUTH);
        return wrap;
    }

    private JPanel buildSummaryRow() {
        JPanel p = Theme.panel(Theme.PANEL_ALT);
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 6));
        p.setBorder(new EmptyBorder(2, 6, 2, 6));

        p.add(summaryItem("Total", totalLabel));
        p.add(vsep());
        p.add(summaryItem("SAME", sameLabel));
        p.add(summaryItem("SIMILAR", similarLabel));
        p.add(summaryItem("DIFFERENCE", diffLabel));
        p.add(vsep());
        p.add(summaryItem("Pending", pendingLabel));

        JButton clearBtn = Theme.button("Clear");
        clearBtn.setToolTipText("Remove all results. New scans append to the table until cleared.");
        clearBtn.addActionListener(e -> clearResults());
        JButton exportBtn = Theme.primaryButton("Export CSV");
        exportBtn.addActionListener(e -> exportCsv());
        JPanel right = Theme.panel(Theme.PANEL_ALT);
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.add(clearBtn);
        right.add(exportBtn);
        // Make the summary row fill width
        JPanel wrap = Theme.panel(Theme.PANEL_ALT);
        wrap.setLayout(new BorderLayout());
        wrap.add(p, BorderLayout.CENTER);
        wrap.add(right, BorderLayout.EAST);
        return wrap;
    }

    private JPanel buildPairRow() {
        JPanel p = Theme.panel(Theme.PANEL_ALT);
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        p.setBorder(new EmptyBorder(2, 6, 2, 6));

        p.add(Theme.dimLabel("Compare Profiles:"));
        p.add(p1Combo);
        JLabel arrow = Theme.label("↔");
        arrow.setForeground(Theme.TEXT_DIM);
        arrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        p.add(arrow);
        p.add(p2Combo);
        p.add(pairNoteLabel);
        return p;
    }

    private JPanel buildFilterRow() {
        JPanel p = Theme.panel(Theme.PANEL);
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 5));
        p.setBorder(new EmptyBorder(2, 6, 2, 6));

        p.add(filterAll);
        p.add(filterSame);
        p.add(filterSimilar);
        p.add(filterDiff);
        p.add(Box.createHorizontalStrut(10));
        p.add(Theme.dimLabel("Method:"));
        p.add(methodFilter);
        p.add(p1StatusLabel);
        p.add(p1StatusFilter);
        p.add(p2StatusLabel);
        p.add(p2StatusFilter);

        JPanel right = Theme.panel(Theme.PANEL);
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 5));
        right.add(Theme.dimLabel("Search:"));
        right.add(searchField);

        JPanel wrap = Theme.panel(Theme.PANEL);
        wrap.setLayout(new BorderLayout());
        wrap.add(p, BorderLayout.CENTER);
        wrap.add(right, BorderLayout.EAST);
        return wrap;
    }

    private JPanel buildDetailPanel() {
        JPanel dp = Theme.panel(Theme.BG);
        dp.setLayout(new BorderLayout());
        dp.setPreferredSize(new Dimension(0, 180));
        dp.add(detailTabs, BorderLayout.CENTER);
        return dp;
    }

    // ── Wiring ───────────────────────────────────────────────────────────────

    private void wireEvents() {
        pauseButton.addActionListener(e -> {
            if (scanEngine == null) return;
            if ("Pause".equals(pauseButton.getText())) {
                scanEngine.pause();
                pauseButton.setText("Resume");
            } else {
                scanEngine.resume();
                pauseButton.setText("Pause");
            }
        });
        cancelButton.addActionListener(e -> {
            if (scanEngine != null) scanEngine.cancel();
        });

        ActionListener pairListener = e -> onPairChanged();
        p1Combo.addActionListener(pairListener);
        p2Combo.addActionListener(pairListener);

        ButtonGroup fg = new ButtonGroup();
        fg.add(filterAll); fg.add(filterSame); fg.add(filterSimilar); fg.add(filterDiff);
        filterAll.addActionListener(e -> { tableModel.setFilter(null); updateFilterCounts(); });
        filterSame.addActionListener(e -> { tableModel.setFilter(Verdict.SAME); updateFilterCounts(); });
        filterSimilar.addActionListener(e -> { tableModel.setFilter(Verdict.SIMILAR); updateFilterCounts(); });
        filterDiff.addActionListener(e -> { tableModel.setFilter(Verdict.DIFFERENCE); updateFilterCounts(); });

        methodFilter.addActionListener(e -> tableModel.setMethodFilter((String) methodFilter.getSelectedItem()));
        ActionListener statusListener = e -> tableModel.setStatusFilters(
                (String) p1StatusFilter.getSelectedItem(), (String) p2StatusFilter.getSelectedItem());
        p1StatusFilter.addActionListener(statusListener);
        p2StatusFilter.addActionListener(statusListener);

        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                tableModel.setSearch(searchField.getText());
            }
        });

        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDetailPanel();
        });
    }

    private void onPairChanged() {
        if (suppressPairEvents) return;
        int i1 = p1Combo.getSelectedIndex();
        int i2 = p2Combo.getSelectedIndex();
        if (i1 < 0 || i2 < 0 || i1 >= profiles.size() || i2 >= profiles.size()) return;

        if (i1 == i2 && profiles.size() > 1) {
            p2Combo.setSelectedIndex((i1 + 1) % profiles.size());
            return;
        }

        UserProfile p1 = profiles.get(i1);
        UserProfile p2 = profiles.get(i2);
        tableModel.setProfilePair(p1.getId(), p2.getId());

        pairNoteLabel.setText("  Comparing " + p1.getName() + "(" + p1.getLabel() + ")"
                + " vs " + p2.getName() + "(" + p2.getLabel() + ")");

        // Update detail tab labels
        detailTabs.setTitleAt(0, p1.getLabel() + " — " + p1.getName());
        detailTabs.setTitleAt(1, p2.getLabel() + " — " + p2.getName());
        p1StatusLabel.setText(p1.getLabel() + " Status:");
        p2StatusLabel.setText(p2.getLabel() + " Status:");

        updateSummary();
        updateFilterCounts();
        updateDetailPanel();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void updateProfiles(List<UserProfile> newProfiles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateProfiles(newProfiles));
            return;
        }
        int prev1 = p1Combo.getSelectedIndex();
        int prev2 = p2Combo.getSelectedIndex();
        profiles = List.copyOf(newProfiles);

        suppressPairEvents = true;
        try {
            p1Combo.removeAllItems();
            p2Combo.removeAllItems();
            for (UserProfile p : profiles) {
                String label = p.getLabel() + " — " + p.getName();
                p1Combo.addItem(label);
                p2Combo.addItem(label);
            }
            if (!profiles.isEmpty()) {
                int n = profiles.size();
                int i1 = prev1 >= 0 && prev1 < n ? prev1 : 0;
                int i2 = prev2 >= 0 && prev2 < n && prev2 != i1 ? prev2 : (n > 1 ? (i1 + 1) % n : 0);
                p1Combo.setSelectedIndex(i1);
                p2Combo.setSelectedIndex(i2);
            }
        } finally {
            suppressPairEvents = false;
        }
        onPairChanged();
    }

    /** Must be called on the EDT before the scan engine starts emitting events. New results are appended. */
    public void prepareScan(ScanConfig config) {
        this.scanConfig = config;
        tableModel.setThreshold(config.getSimilarityThreshold());
        doneCount.set(0);
        errorCount.set(0);
        skippedDuplicates = 0;
        scanTotal = 0;
        scanStart.set(System.currentTimeMillis());
        progressBar.setValue(0);
        progressLabel.setText("Preparing scan…");
        metaLabel.setText("");
        pauseButton.setText("Pause");
        updateProfiles(config.getProfiles());
        updateSummary();
        updateFilterCounts();
    }

    /** Called by MainPanel after constructing ScanEngine with the real MontoyaApi. */
    public void setScanEngine(ScanEngine engine) {
        this.scanEngine = engine;
    }

    public ScanEngine.ScanContext getScanContext() {
        return scanContext;
    }

    /** Events must be delivered on the EDT. */
    public ScanEngine.ScanListener getScanListener() {
        return scanListener;
    }

    private void clearResults() {
        if (tableModel.getTotalCount() == 0) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove all " + tableModel.getTotalCount() + " results?"
                        + (scanEngine != null ? "\nAny running scan will be cancelled." : ""),
                "Clear Results", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) resetResults();
    }

    private void resetResults() {
        if (scanEngine != null) {
            scanEngine.cancel();
            scanEngine = null;
        }
        tableModel.clearResults();
        scanContext.rowCounter().set(0);
        scanContext.scannedFingerprints().clear();
        pendingMerges.clear();
        refreshMethodFilter();
        progressBar.setValue(0);
        progressLabel.setText("Idle");
        metaLabel.setText("");
        updateSummary();
        updateFilterCounts();
        updateDetailPanel();
    }

    private void refreshMethodFilter() {
        Object selected = methodFilter.getSelectedItem();
        java.util.SortedSet<String> methods = tableModel.getMethods();
        if (methodFilter.getItemCount() == methods.size() + 1) return;
        methodFilter.removeAllItems();
        methodFilter.addItem(ResultTableModel.ANY);
        for (String m : methods) methodFilter.addItem(m);
        methodFilter.setSelectedItem(selected != null && methods.contains(selected) ? selected : ResultTableModel.ANY);
    }

    private ScanEngine.ScanListener createScanListener() {
        return new ScanEngine.ScanListener() {
            @Override public void onResult(ScanResult result) {
                List<RequestGroup> merges = pendingMerges.remove(result.getGroup().getFingerprint());
                if (merges != null) merges.forEach(g -> mergeInto(result, g));
                tableModel.addResult(result);
                refreshMethodFilter();
                updateSummary();
                updateFilterCounts();
            }
            @Override public void onDuplicate(RequestGroup group) {
                skippedDuplicates++;
                ScanResult existing = tableModel.findByFingerprint(group.getFingerprint());
                if (existing != null) {
                    mergeInto(existing, group);
                    tableModel.refresh();
                } else {
                    pendingMerges.computeIfAbsent(group.getFingerprint(), k -> new java.util.ArrayList<>()).add(group);
                }
            }
            @Override public void onPlanned(int received, int excluded, int toScan) {
                scanTotal = toScan;
                progressLabel.setText("Queued " + toScan + " unique endpoint(s) from " + received
                        + " request(s) — excluded " + excluded + " (OPTIONS / static / rules), "
                        + skippedDuplicates + " already in results");
                if (toScan == 0) {
                    progressBar.setValue(100);
                    metaLabel.setText("Nothing new to scan.");
                }
            }
            @Override public void onProgress(int done, int total) {
                // Workers report from several threads, so updates can arrive out of order.
                if (done < doneCount.get()) return;
                doneCount.set(done);
                scanTotal = total;
                int pct = total > 0 ? (done * 100 / total) : 0;
                progressBar.setValue(pct);
                String profiles = scanConfig != null
                        ? scanConfig.getProfiles().stream()
                              .map(com.idorbola.checker.model.UserProfile::getLabel)
                              .reduce((a, b) -> a + "·" + b).orElse("")
                        : "";
                int totalHttp = total * (scanConfig != null ? scanConfig.getProfiles().size() : 1);
                progressLabel.setText("Scanning — " + done + " / " + total + " requests"
                        + (profiles.isEmpty() ? "" : "  |  Profile " + profiles + " simultaneous")
                        + " (total " + totalHttp + " HTTP requests)");
                long elapsed = System.currentTimeMillis() - scanStart.get();
                long eta = done > 0 ? (elapsed * (total - done) / done) : 0;
                metaLabel.setText("Done: " + done + "  |  Errors: " + errorCount.get()
                        + "  |  ETA: ~" + formatEta(eta) + "  |  " + pct + "%");
            }
            @Override public void onComplete() {
                progressBar.setValue(100);
                metaLabel.setText("Done: " + scanTotal + "  |  Errors: " + errorCount.get() + "  |  100%");
                progressLabel.setText("Scan complete — " + scanTotal + " endpoint(s) replayed"
                        + (skippedDuplicates > 0 ? ", " + skippedDuplicates + " already in results (merged into existing rows)" : "")
                        + ".");
                updateSummary();
                updateFilterCounts();
            }
            @Override public void onError(String msg) {
                errorCount.incrementAndGet();
                metaLabel.setText("Errors: " + errorCount.get() + " — last: " + msg);
            }
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void mergeInto(ScanResult target, RequestGroup duplicate) {
        target.getGroup().addGrouped(duplicate.getRepresentative());
        duplicate.getGrouped().forEach(target.getGroup()::addGrouped);
    }

    private void updateSummary() {
        int total   = tableModel.getTotalCount();
        int same    = tableModel.getVerdictCount(Verdict.SAME);
        int similar = tableModel.getVerdictCount(Verdict.SIMILAR);
        int diff    = tableModel.getVerdictCount(Verdict.DIFFERENCE);
        int pending = total - same - similar - diff;

        totalLabel.setText(String.valueOf(total));
        sameLabel.setText(String.valueOf(same));
        similarLabel.setText(String.valueOf(similar));
        diffLabel.setText(String.valueOf(diff));
        pendingLabel.setText(String.valueOf(Math.max(0, pending)));
    }

    private void updateFilterCounts() {
        int total   = tableModel.getTotalCount();
        int same    = tableModel.getVerdictCount(Verdict.SAME);
        int similar = tableModel.getVerdictCount(Verdict.SIMILAR);
        int diff    = tableModel.getVerdictCount(Verdict.DIFFERENCE);

        filterAll.setText("All " + total);
        filterSame.setText("SAME " + same);
        filterSimilar.setText("SIMILAR " + similar);
        filterDiff.setText("DIFFERENCE " + diff);
    }

    private void updateDetailPanel() {
        int row = resultsTable.getSelectedRow();
        ScanResult result = row >= 0 ? tableModel.getResultAt(row) : null;
        if (result == null) {
            p1Detail.setMessages("", "");
            p2Detail.setMessages("", "");
            diffDetail.setText("");
            return;
        }

        int i1 = p1Combo.getSelectedIndex();
        int i2 = p2Combo.getSelectedIndex();
        if (i1 < 0 || i1 >= profiles.size() || i2 < 0 || i2 >= profiles.size()) return;

        UserProfile p1 = profiles.get(i1);
        UserProfile p2 = profiles.get(i2);

        p1Detail.setMessages(requestText(result, p1), responseText(result, p1));
        p2Detail.setMessages(requestText(result, p2), responseText(result, p2));
        diffDetail.setText(buildDiff(result, p1, p2));
        diffDetail.setCaretPosition(0);
    }

    private static String requestText(ScanResult result, UserProfile profile) {
        return HttpText.message(result.getRequest(profile.getId()));
    }

    private static String responseText(ScanResult result, UserProfile profile) {
        burp.api.montoya.http.message.responses.HttpResponse resp =
                result.getResponses().get(profile.getId());
        return resp == null ? "(no response)" : HttpText.message(resp);
    }

    private String buildDiff(ScanResult result, UserProfile p1, UserProfile p2) {
        burp.api.montoya.http.message.responses.HttpResponse r1 = result.getResponses().get(p1.getId());
        burp.api.montoya.http.message.responses.HttpResponse r2 = result.getResponses().get(p2.getId());
        if (r1 == null || r2 == null) return "(responses not available)";

        String[] linesA = HttpText.body(r1).split("\n", -1);
        String[] linesB = HttpText.body(r2).split("\n", -1);

        Float sim = result.getSimilarity(p1.getId(), p2.getId());
        String header = "--- " + p1.getLabel() + " — " + p1.getName() + "\n"
                + "+++ " + p2.getLabel() + " — " + p2.getName() + "\n"
                + (sim != null ? String.format("Similarity: %.1f%%\n", sim * 100f) : "")
                + "\n";

        // Simple line-by-line diff (context diff, first 200 lines)
        StringBuilder sb = new StringBuilder(header);
        int maxLines = Math.min(Math.max(linesA.length, linesB.length), 200);
        for (int i = 0; i < maxLines; i++) {
            String a = i < linesA.length ? linesA[i] : "";
            String b = i < linesB.length ? linesB[i] : "";
            if (a.equals(b)) {
                sb.append("  ").append(a).append("\n");
            } else {
                if (!a.isEmpty()) sb.append("- ").append(a).append("\n");
                if (!b.isEmpty()) sb.append("+ ").append(b).append("\n");
            }
        }
        return sb.toString();
    }

    private void configureTableColumns() {
        TableColumnModel cm = resultsTable.getColumnModel();
        cm.getColumn(0).setMaxWidth(40);   // #
        cm.getColumn(1).setMaxWidth(60);   // Method
        cm.getColumn(3).setMaxWidth(55);   // Group
        cm.getColumn(4).setMaxWidth(70);   // P1 Status
        cm.getColumn(5).setMaxWidth(70);   // P2 Status
        cm.getColumn(6).setMaxWidth(100);  // Verdict
        cm.getColumn(7).setMaxWidth(110);  // Similarity

        resultsTable.setDefaultRenderer(Object.class, new ResultCellRenderer(tableModel));
        resultsTable.setDefaultRenderer(Integer.class, new ResultCellRenderer(tableModel));
        resultsTable.setDefaultRenderer(Verdict.class, new ResultCellRenderer(tableModel));
        resultsTable.setDefaultRenderer(Float.class, new ResultCellRenderer(tableModel));
    }

    private void exportCsv() {
        if (scanConfig == null) return;
        int i1 = p1Combo.getSelectedIndex();
        int i2 = p2Combo.getSelectedIndex();
        if (i1 < 0 || i2 < 0 || i1 >= profiles.size() || i2 >= profiles.size()) return;

        CsvExporter.exportToFile(this,
                tableModel,
                profiles,
                profiles.get(i1).getId(),
                profiles.get(i2).getId(),
                scanConfig.getSimilarityThreshold());
    }

    private static String formatEta(long ms) {
        if (ms <= 0) return "calculating…";
        long secs = ms / 1000;
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m " + (secs % 60) + "s";
    }

    private static JLabel boldCountLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        return l;
    }

    private static JPanel summaryItem(String labelText, JLabel value) {
        JPanel p = Theme.panel(Theme.PANEL_ALT);
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.add(Theme.dimLabel(labelText + ":"));
        p.add(value);
        return p;
    }

    private static JPanel vsep() {
        JPanel p = new JPanel();
        p.setBackground(Theme.PANEL_ALT);
        p.setPreferredSize(new Dimension(1, 14));
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        return p;
    }

    private static JPanel sep() {
        JPanel p = new JPanel();
        p.setBackground(Theme.BORDER);
        p.setPreferredSize(new Dimension(0, 1));
        return p;
    }

    private static JToggleButton filterToggle(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setBackground(Theme.PANEL);
        btn.setForeground(Theme.TEXT_DIM);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Theme.BORDER, 1),
                new EmptyBorder(2, 10, 2, 10)));
        btn.setFont(Theme.UI_SMALL);
        btn.setOpaque(true);
        // Selected style via ItemListener
        btn.addItemListener(e -> {
            boolean selected = e.getStateChange() == java.awt.event.ItemEvent.SELECTED;
            btn.setBackground(selected ? Theme.ROW_SEL : Theme.PANEL);
            btn.setForeground(selected ? Theme.TEXT_BRIGHT : Theme.TEXT_DIM);
        });
        return btn;
    }

    /** One profile's sent request and received response, with a Request/Response switch. */
    private static final class MessageView extends JPanel {
        private final JToggleButton requestBtn  = filterToggle("Request");
        private final JToggleButton responseBtn = filterToggle("Response");
        private final JTextPane text = detailPane();
        private String request  = "";
        private String response = "";

        MessageView() {
            super(new BorderLayout());
            setBackground(Theme.PANEL);

            ButtonGroup group = new ButtonGroup();
            group.add(requestBtn);
            group.add(responseBtn);
            responseBtn.setSelected(true);
            requestBtn.addActionListener(e -> refresh());
            responseBtn.addActionListener(e -> refresh());

            JPanel bar = Theme.panel(Theme.PANEL);
            bar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
            bar.add(requestBtn);
            bar.add(responseBtn);

            add(bar, BorderLayout.NORTH);
            add(Theme.scrollPane(text), BorderLayout.CENTER);
        }

        void setMessages(String request, String response) {
            this.request = request;
            this.response = response;
            refresh();
        }

        private void refresh() {
            text.setText(requestBtn.isSelected() ? request : response);
            text.setCaretPosition(0);
        }
    }

    private static JTextPane detailPane() {
        JTextPane tp = new JTextPane();
        tp.setBackground(Theme.INPUT_BG);
        tp.setForeground(Theme.TEXT);
        tp.setFont(Theme.MONO);
        tp.setEditable(false);
        tp.setBorder(new EmptyBorder(6, 8, 6, 8));
        return tp;
    }
}
