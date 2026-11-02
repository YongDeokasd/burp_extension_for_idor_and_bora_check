package com.idorbola.checker.ui;

import com.idorbola.checker.model.UserProfile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileEditorDialog extends JDialog {

    private final JTextField nameField;
    private final JTextArea cookieArea;
    private final DefaultTableModel headerTableModel;

    private UserProfile result = null;

    private ProfileEditorDialog(Frame parent, UserProfile existing) {
        super(parent, existing == null ? "Add Profile" : "Edit Profile", true);

        nameField = Theme.textField(20);
        cookieArea = Theme.textArea();
        cookieArea.setRows(3);
        cookieArea.setLineWrap(true);

        String[] cols = {"Header Name", "Value"};
        headerTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };

        if (existing != null) {
            nameField.setText(existing.getName());
            cookieArea.setText(existing.getCookieHeader());
            for (Map.Entry<String, String> e : existing.getCustomHeaders().entrySet()) {
                headerTableModel.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        }

        buildUI();
        pack();
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel content = Theme.panel(Theme.BG);
        content.setLayout(new BorderLayout(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        // Form area
        JPanel form = Theme.panel(Theme.BG);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // Name row
        JPanel nameRow = row("Profile Name:", nameField);
        form.add(nameRow);
        form.add(Box.createVerticalStrut(8));

        // Cookie area
        form.add(Theme.dimLabel("Cookie Header (raw):"));
        form.add(Box.createVerticalStrut(4));
        JScrollPane cookieScroll = Theme.scrollPane(cookieArea);
        cookieScroll.setPreferredSize(new Dimension(0, 70));
        form.add(cookieScroll);
        form.add(Box.createVerticalStrut(10));

        // Custom headers table
        form.add(Theme.dimLabel("Custom Headers:"));
        form.add(Box.createVerticalStrut(4));

        JTable headerTable = Theme.table();
        headerTable.setModel(headerTableModel);
        JScrollPane tableScroll = Theme.scrollPane(headerTable);
        tableScroll.setPreferredSize(new Dimension(0, 120));
        form.add(tableScroll);
        form.add(Box.createVerticalStrut(4));

        JPanel headerBtns = Theme.panel(Theme.BG);
        headerBtns.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addRow = Theme.button("+ Add Header");
        JButton delRow = Theme.button("Delete");
        headerBtns.add(addRow);
        headerBtns.add(delRow);
        form.add(headerBtns);

        addRow.addActionListener(e -> headerTableModel.addRow(new Object[]{"", ""}));
        delRow.addActionListener(e -> {
            int row = headerTable.getSelectedRow();
            if (row >= 0) headerTableModel.removeRow(row);
        });

        content.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = Theme.panel(Theme.BG);
        btnPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton ok     = Theme.primaryButton("OK");
        JButton cancel = Theme.button("Cancel");
        btnPanel.add(cancel);
        btnPanel.add(ok);
        content.add(btnPanel, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Profile name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            result = new UserProfile();
            result.setName(name);
            result.setCookieHeader(cookieArea.getText().trim());
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 0; i < headerTableModel.getRowCount(); i++) {
                Object k = headerTableModel.getValueAt(i, 0);
                Object v = headerTableModel.getValueAt(i, 1);
                if (k != null && !k.toString().isBlank()) {
                    headers.put(k.toString().trim(), v != null ? v.toString() : "");
                }
            }
            result.setCustomHeaders(headers);
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        setContentPane(content);
        getContentPane().setBackground(Theme.BG);
    }

    private JPanel row(String labelText, JComponent field) {
        JPanel p = Theme.panel(Theme.BG);
        p.setLayout(new BorderLayout(8, 0));
        JLabel lbl = Theme.dimLabel(labelText);
        lbl.setPreferredSize(new Dimension(140, 24));
        p.add(lbl, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    /**
     * Show the dialog and return the profile, or null if cancelled.
     * Pass existing=null to create a new profile.
     */
    public static UserProfile show(Component parent, UserProfile existing) {
        Frame frame = parent instanceof Frame f ? f
                : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
        ProfileEditorDialog dlg = new ProfileEditorDialog(frame, existing);
        dlg.setVisible(true);
        UserProfile r = dlg.result;
        if (r != null && existing != null) {
            r.setId(existing.getId());
            r.setLabel(existing.getLabel());
        }
        return r;
    }
}
