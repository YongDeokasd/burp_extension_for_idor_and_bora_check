package com.idorbola.checker.ui;

import com.idorbola.checker.model.ScanConfig;
import com.idorbola.checker.model.UserProfile;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class SettingsPanel extends JPanel {

    // Profile table
    private final DefaultTableModel profileTableModel;
    private final JTable profileTable;
    private final List<UserProfile> profiles = new ArrayList<>();

    // Replay settings
    private final JSpinner threadPoolSpinner;
    private final JSpinner rateLimitSpinner;
    private final JSpinner profileDelaySpinner;
    private final JSpinner timeoutSpinner;

    // Dedup
    private final JToggleButton dedupToggle;

    // Exclusion patterns
    private final JTextArea exclusionArea;

    // Static filter
    private final JPanel extTagPanel;
    private final JPanel prefixTagPanel;
    private final Set<String> excludedExtensions = new LinkedHashSet<>(Arrays.asList(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".mp4", ".mp3"));
    private final List<String> excludedPrefixes = new ArrayList<>(Arrays.asList(
            "/static/", "/assets/", "/resources/", "/_next/", "/dist/"));

    // Comparison settings
    private final JSpinner similaritySpinner;

    // Sidebar navigation
    private final List<JPanel> sections = new ArrayList<>();
    private final List<JButton> navButtons = new ArrayList<>();

    // Callback when profiles change (to update results panel dropdowns)
    private Consumer<List<UserProfile>> onProfilesChanged;

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        // Init spinners
        threadPoolSpinner    = Theme.spinner(10, 1, 100, 1);
        rateLimitSpinner     = Theme.spinner(20, 1, 500, 1);
        profileDelaySpinner  = Theme.spinner(0, 0, 60000, 100);
        timeoutSpinner       = Theme.spinner(10, 1, 300, 1);
        similaritySpinner    = Theme.spinner(80, 0, 100, 5);

        // Profile table
        String[] cols = {"Label", "Name", "Cookie Preview", "Headers"};
        profileTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        profileTable = Theme.table();
        profileTable.setModel(profileTableModel);
        profileTable.getColumnModel().getColumn(0).setMaxWidth(50);
        profileTable.getColumnModel().getColumn(3).setMaxWidth(80);

        // Dedup toggle
        dedupToggle = new JToggleButton("ON");
        dedupToggle.setSelected(true);
        dedupToggle.setBackground(Theme.ACCENT_DIM);
        dedupToggle.setForeground(Theme.TEXT_BRIGHT);
        dedupToggle.setFont(Theme.UI_SMALL);
        dedupToggle.setBorder(new LineBorder(Theme.ACCENT, 1));
        dedupToggle.setFocusPainted(false);
        dedupToggle.addActionListener(e -> dedupToggle.setText(dedupToggle.isSelected() ? "ON" : "OFF"));

        // Exclusion text area
        exclusionArea = Theme.textArea();
        exclusionArea.setRows(6);

        // Static resource tag panels
        extTagPanel    = Theme.panel(Theme.PANEL_ALT);
        prefixTagPanel = Theme.panel(Theme.PANEL_ALT);
        extTagPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        prefixTagPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));

        buildUI();
        rebuildExtTags();
        rebuildPrefixTags();
    }

    public void setOnProfilesChanged(Consumer<List<UserProfile>> cb) {
        this.onProfilesChanged = cb;
    }

    private void buildUI() {
        // Left sidebar
        JPanel sidebar = Theme.panel(new Color(0x202020));
        sidebar.setPreferredSize(new Dimension(160, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        String[] navLabels = {
                "User Profiles", "Replay Settings", "Dedup Settings",
                "Exclusion Endpoints", "Static Filter", "Comparison"
        };

        for (int i = 0; i < navLabels.length; i++) {
            JButton btn = new JButton(navLabels[i]);
            btn.setBackground(new Color(0x202020));
            btn.setForeground(Theme.TEXT_DIM);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setFont(Theme.UI_SMALL);
            btn.setBorder(new EmptyBorder(6, 12, 6, 8));
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            final int idx = i;
            btn.addActionListener(e -> showSection(idx));
            sidebar.add(btn);
            navButtons.add(btn);
        }
        sidebar.add(Box.createVerticalGlue());

        // Content area (card layout)
        JPanel content = Theme.panel(Theme.PANEL);
        content.setLayout(new CardLayout());

        sections.add(buildProfilesSection());
        sections.add(buildReplaySection());
        sections.add(buildDedupSection());
        sections.add(buildExclusionSection());
        sections.add(buildStaticSection());
        sections.add(buildComparisonSection());

        for (int i = 0; i < sections.size(); i++) {
            content.add(sections.get(i), String.valueOf(i));
        }

        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        showSection(0);
    }

    private void showSection(int idx) {
        for (int i = 0; i < navButtons.size(); i++) {
            JButton btn = navButtons.get(i);
            if (i == idx) {
                btn.setBackground(Theme.PANEL);
                btn.setForeground(Theme.ACCENT);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.ACCENT),
                        new EmptyBorder(6, 10, 6, 8)));
            } else {
                btn.setBackground(new Color(0x202020));
                btn.setForeground(Theme.TEXT_DIM);
                btn.setBorder(new EmptyBorder(6, 12, 6, 8));
            }
        }
        JPanel content = (JPanel) ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        ((CardLayout) content.getLayout()).show(content, String.valueOf(idx));
    }

    // ── Section builders ─────────────────────────────────────────────────────

    private JPanel buildProfilesSection() {
        JPanel p = sectionPanel("User Profiles");

        p.add(Theme.scrollPane(profileTable), BorderLayout.CENTER);

        JPanel btns = Theme.panel(Theme.PANEL);
        btns.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JButton add  = Theme.primaryButton("+ Add Profile");
        JButton edit = Theme.button("Edit");
        JButton del  = Theme.button("Delete");
        btns.add(add); btns.add(edit); btns.add(del);
        p.add(btns, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            UserProfile np = ProfileEditorDialog.show(this, null);
            if (np != null) {
                np.setLabel(UserProfile.nextLabel(profiles.size()));
                profiles.add(np);
                refreshProfileTable();
                fireProfilesChanged();
            }
        });
        edit.addActionListener(e -> {
            int row = profileTable.getSelectedRow();
            if (row < 0) return;
            UserProfile existing = profiles.get(row);
            UserProfile updated = ProfileEditorDialog.show(this, existing);
            if (updated != null) {
                updated.setLabel(existing.getLabel());
                profiles.set(row, updated);
                refreshProfileTable();
                fireProfilesChanged();
            }
        });
        del.addActionListener(e -> {
            int row = profileTable.getSelectedRow();
            if (row < 0) return;
            profiles.remove(row);
            // Re-assign labels
            for (int i = 0; i < profiles.size(); i++) {
                profiles.get(i).setLabel(UserProfile.nextLabel(i));
            }
            refreshProfileTable();
            fireProfilesChanged();
        });

        return p;
    }

    private JPanel buildReplaySection() {
        JPanel p = sectionPanel("Replay Settings");
        JPanel form = Theme.panel(Theme.PANEL);
        form.setLayout(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 12, 10, 12));

        addFormRow(form, "Thread Pool Size:", threadPoolSpinner, "", 0);
        addFormRow(form, "Rate Limit:", rateLimitSpinner, "req/s  (N profiles × rate = actual server req/s)", 1);
        addFormRow(form, "Profile Delay:", profileDelaySpinner, "ms between profiles", 2);
        addFormRow(form, "Timeout:", timeoutSpinner, "seconds", 3);

        p.add(form, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildDedupSection() {
        JPanel p = sectionPanel("Deduplication Settings");
        JPanel form = Theme.panel(Theme.PANEL);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel row = Theme.panel(Theme.PANEL);
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(Theme.label("Deduplication:"));
        row.add(dedupToggle);
        form.add(row);
        form.add(Box.createVerticalStrut(8));

        JLabel desc = Theme.dimLabel("<html>Fingerprint = SHA256(Method + NormalizedPath + QueryKeys + BodyKeys)<br>"
                + "When OFF, all URLs are tested individually.</html>");
        form.add(desc);

        p.add(form, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildExclusionSection() {
        JPanel p = sectionPanel("Exclusion Endpoints");
        JPanel inner = Theme.panel(Theme.PANEL);
        inner.setLayout(new BorderLayout(0, 6));
        inner.setBorder(new EmptyBorder(10, 12, 6, 12));

        inner.add(Theme.dimLabel(
                "<html>One pattern per line. Formats: /path/prefix/*, METHOD:/path, /exact/path<br>"
                + "Examples: /api/auth/logout &nbsp; POST:/api/user/delete &nbsp; DELETE:/api/admin/*</html>"),
                BorderLayout.NORTH);

        inner.add(Theme.scrollPane(exclusionArea), BorderLayout.CENTER);

        JPanel btns = Theme.panel(Theme.PANEL);
        btns.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton reset = Theme.button("Reset");
        btns.add(reset);
        reset.addActionListener(e -> exclusionArea.setText(""));
        inner.add(btns, BorderLayout.SOUTH);

        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStaticSection() {
        JPanel p = sectionPanel("Static Resource Filter");
        JPanel inner = Theme.panel(Theme.PANEL);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Extensions
        inner.add(Theme.dimLabel("Excluded Extensions:"));
        inner.add(Box.createVerticalStrut(4));

        JPanel extWrap = Theme.panel(Theme.PANEL_ALT);
        extWrap.setLayout(new BorderLayout());
        extWrap.setBorder(new LineBorder(Theme.BORDER, 1));
        extWrap.add(extTagPanel, BorderLayout.CENTER);

        JPanel extBtns = Theme.panel(Theme.PANEL);
        extBtns.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JTextField extField = Theme.textField(8);
        extField.setToolTipText(".ext");
        JButton addExt = Theme.button("Add");
        addExt.addActionListener(e -> {
            String ext = extField.getText().trim().toLowerCase();
            if (!ext.isEmpty()) {
                if (!ext.startsWith(".")) ext = "." + ext;
                excludedExtensions.add(ext);
                extField.setText("");
                rebuildExtTags();
            }
        });
        extBtns.add(extField); extBtns.add(addExt);

        inner.add(extWrap);
        inner.add(extBtns);
        inner.add(Box.createVerticalStrut(6));
        inner.add(Theme.dimLabel("Note: .pdf is NOT filtered — it's an authorization check target."));
        inner.add(Box.createVerticalStrut(10));

        // Path prefixes
        inner.add(Theme.dimLabel("Excluded Path Prefixes:"));
        inner.add(Box.createVerticalStrut(4));

        JPanel prefWrap = Theme.panel(Theme.PANEL_ALT);
        prefWrap.setLayout(new BorderLayout());
        prefWrap.setBorder(new LineBorder(Theme.BORDER, 1));
        prefWrap.add(prefixTagPanel, BorderLayout.CENTER);

        JPanel prefBtns = Theme.panel(Theme.PANEL);
        prefBtns.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JTextField prefField = Theme.textField(12);
        prefField.setToolTipText("/path/prefix/");
        JButton addPref = Theme.button("Add");
        addPref.addActionListener(e -> {
            String pref = prefField.getText().trim();
            if (!pref.isEmpty()) {
                if (!pref.startsWith("/")) pref = "/" + pref;
                excludedPrefixes.add(pref);
                prefField.setText("");
                rebuildPrefixTags();
            }
        });
        prefBtns.add(prefField); prefBtns.add(addPref);

        inner.add(prefWrap);
        inner.add(prefBtns);

        p.add(inner, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildComparisonSection() {
        JPanel p = sectionPanel("Comparison Settings");
        JPanel form = Theme.panel(Theme.PANEL);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel row = Theme.panel(Theme.PANEL);
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(Theme.label("Similarity Threshold:"));
        row.add(similaritySpinner);
        row.add(Theme.dimLabel("% (≥threshold → SIMILAR, ≥99.9% → SAME)"));
        form.add(row);
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.dimLabel("<html>Comparison target: response body only (headers excluded<br>"
                + "to avoid Date, X-Request-Time pollution).<br>"
                + "JSON: normalizes dynamic values. HTML: compares CSRF hidden inputs only.</html>"));

        p.add(form, BorderLayout.NORTH);
        return p;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JPanel sectionPanel(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(Theme.PANEL);
        JPanel header = Theme.panel(Theme.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(8, 12, 8, 12)));
        header.setLayout(new BorderLayout());
        header.add(Theme.accentLabel(title), BorderLayout.WEST);
        p.add(header, BorderLayout.NORTH);
        return p;
    }

    private void addFormRow(JPanel form, String labelText, JComponent field, String note, int row) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 10);
        form.add(Theme.dimLabel(labelText), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.anchor = GridBagConstraints.WEST;
        fc.insets = new Insets(4, 0, 4, 8);
        form.add(field, fc);

        if (!note.isEmpty()) {
            GridBagConstraints nc = new GridBagConstraints();
            nc.gridx = 2; nc.gridy = row;
            nc.anchor = GridBagConstraints.WEST;
            nc.weightx = 1.0;
            nc.insets = new Insets(4, 0, 4, 0);
            form.add(Theme.dimLabel(note), nc);
        }
    }

    private void rebuildExtTags() {
        rebuildTags(extTagPanel, excludedExtensions, excludedExtensions::remove, this::rebuildExtTags);
    }

    private void rebuildPrefixTags() {
        rebuildTags(prefixTagPanel, excludedPrefixes, excludedPrefixes::remove, this::rebuildPrefixTags);
    }

    private void rebuildTags(JPanel panel, Iterable<String> items, java.util.function.Consumer<String> remover, Runnable rebuilder) {
        panel.removeAll();
        for (String item : new ArrayList<>(items instanceof Set<?> s ? new ArrayList<>((Set<String>) s) : (List<String>) items)) {
            JPanel chip = Theme.panel(new Color(0x333333));
            chip.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            chip.setBorder(new LineBorder(new Color(0x4a4a4a), 1));
            JLabel lbl = Theme.label(item);
            lbl.setFont(Theme.MONO);
            JButton x = new JButton("×");
            x.setBackground(new Color(0x333333));
            x.setForeground(Theme.TEXT_DIM);
            x.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            x.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            x.setFocusPainted(false);
            x.setContentAreaFilled(false);
            x.addActionListener(e -> { remover.accept(item); rebuilder.run(); });
            chip.add(lbl); chip.add(x);
            panel.add(chip);
        }
        panel.revalidate();
        panel.repaint();
    }

    private void refreshProfileTable() {
        profileTableModel.setRowCount(0);
        for (UserProfile p : profiles) {
            profileTableModel.addRow(new Object[]{
                    p.getLabel(), p.getName(), p.getCookiePreview(),
                    p.getCustomHeaders().size() + " header(s)"
            });
        }
    }

    private void fireProfilesChanged() {
        if (onProfilesChanged != null) onProfilesChanged.accept(new ArrayList<>(profiles));
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public ScanConfig getConfig() {
        ScanConfig cfg = new ScanConfig();
        cfg.setProfiles(new ArrayList<>(profiles));
        cfg.setDeduplicationEnabled(dedupToggle.isSelected());
        cfg.setThreadPoolSize((Integer) threadPoolSpinner.getValue());
        cfg.setRateLimitPerSecond((Integer) rateLimitSpinner.getValue());
        cfg.setProfileDelayMs((Integer) profileDelaySpinner.getValue());
        cfg.setTimeoutSeconds((Integer) timeoutSpinner.getValue());

        List<String> patterns = new ArrayList<>();
        for (String line : exclusionArea.getText().split("\\n")) {
            if (!line.isBlank()) patterns.add(line.trim());
        }
        cfg.setExclusionPatterns(patterns);
        cfg.setExcludedExtensions(new LinkedHashSet<>(excludedExtensions));
        cfg.setExcludedPathPrefixes(new ArrayList<>(excludedPrefixes));
        cfg.setSimilarityThreshold((Integer) similaritySpinner.getValue());
        return cfg;
    }

    public void loadConfig(ScanConfig config) {
        profiles.clear();
        profiles.addAll(config.getProfiles());
        refreshProfileTable();

        dedupToggle.setSelected(config.isDeduplicationEnabled());
        dedupToggle.setText(config.isDeduplicationEnabled() ? "ON" : "OFF");
        threadPoolSpinner.setValue(config.getThreadPoolSize());
        rateLimitSpinner.setValue(config.getRateLimitPerSecond());
        profileDelaySpinner.setValue(config.getProfileDelayMs());
        timeoutSpinner.setValue(config.getTimeoutSeconds());

        exclusionArea.setText(String.join("\n", config.getExclusionPatterns()));
        excludedExtensions.clear();
        excludedExtensions.addAll(config.getExcludedExtensions());
        excludedPrefixes.clear();
        excludedPrefixes.addAll(config.getExcludedPathPrefixes());
        rebuildExtTags();
        rebuildPrefixTags();

        similaritySpinner.setValue(config.getSimilarityThreshold());
    }

    public List<UserProfile> getProfiles() { return Collections.unmodifiableList(profiles); }
}
