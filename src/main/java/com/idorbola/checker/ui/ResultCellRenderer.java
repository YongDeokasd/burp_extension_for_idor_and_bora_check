package com.idorbola.checker.ui;

import com.idorbola.checker.model.Verdict;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom cell renderer that applies Burp dark theme styling to the results table.
 */
public class ResultCellRenderer extends DefaultTableCellRenderer {

    private final ResultTableModel model;

    public ResultCellRenderer(ResultTableModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        setBackground(isSelected ? Theme.ROW_SEL : Theme.PANEL);
        setForeground(isSelected ? Theme.TEXT_BRIGHT : Theme.TEXT);
        setFont(Theme.UI_SMALL);
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        // Column-specific rendering
        String colName = table.getColumnName(column);
        switch (colName) {
            case "Method" -> renderMethod(value);
            case "URL"    -> renderUrl(value);
            case "Group"  -> renderGroup(value);
            case "P1 Status", "P2 Status" -> renderStatus(value, isSelected);
            case "Verdict"  -> renderVerdict(value, isSelected);
            case "Similarity" -> renderSimilarity(table, value, row, isSelected);
        }

        return this;
    }

    private void renderMethod(Object value) {
        String method = value != null ? value.toString() : "";
        setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        setHorizontalAlignment(CENTER);
        Color bg, fg;
        switch (method) {
            case "GET"    -> { bg = new Color(0x1a3a5c); fg = new Color(0x7ab8f5); }
            case "POST"   -> { bg = new Color(0x2a3a1a); fg = new Color(0xa8d878); }
            case "PUT"    -> { bg = new Color(0x3a2a1a); fg = new Color(0xf5c07a); }
            case "DELETE" -> { bg = new Color(0x3a1a1a); fg = new Color(0xf57a7a); }
            default       -> { bg = new Color(0x2a2a3a); fg = new Color(0xd4d4d4); }
        }
        setBackground(bg);
        setForeground(fg);
        setText(method);
    }

    private void renderUrl(Object value) {
        if (value == null) return;
        String url = value.toString();
        // Show as plain monospace — host is embedded in the full path
        setFont(Theme.MONO);
        setForeground(Theme.TEXT);
        setHorizontalAlignment(LEFT);
    }

    private void renderGroup(Object value) {
        setHorizontalAlignment(CENTER);
        if (value == null) {
            setForeground(new Color(0x444444));
            setText("—");
        } else {
            int size = (Integer) value;
            if (size > 1) {
                setForeground(new Color(0x8a8aff));
                setText(String.valueOf(size));
            } else {
                setForeground(new Color(0x444444));
                setText("—");
            }
        }
    }

    private void renderStatus(Object value, boolean isSelected) {
        setHorizontalAlignment(CENTER);
        setFont(Theme.MONO);
        if (value == null || (value instanceof Integer i && i < 0)) {
            setForeground(Theme.TEXT_DIM);
            setText("—");
        } else {
            int code = (Integer) value;
            setForeground(isSelected ? Theme.TEXT_BRIGHT : Theme.statusColor(code));
            setText(String.valueOf(code));
        }
    }

    private void renderVerdict(Object value, boolean isSelected) {
        setHorizontalAlignment(CENTER);
        if (value instanceof Verdict v) {
            setForeground(Theme.verdictColor(v));
            if (!isSelected) setBackground(Theme.verdictBg(v));
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            setText(v.name());
        } else {
            setForeground(Theme.TEXT_DIM);
            setText("—");
        }
    }

    private void renderSimilarity(JTable table, Object value, int row, boolean isSelected) {
        setHorizontalAlignment(RIGHT);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        if (value instanceof Float f) {
            float pct = f * 100f;
            Verdict v = model.getResultAt(row) != null
                    ? (Verdict) table.getValueAt(row, table.getColumnCount() - 2)
                    : null;
            Color c = v != null ? Theme.verdictColor(v) : Theme.TEXT_DIM;
            setForeground(isSelected ? Theme.TEXT_BRIGHT : c);
            setText(String.format("%.1f%%", pct));
        } else {
            setForeground(Theme.TEXT_DIM);
            setText("—");
        }
    }
}
