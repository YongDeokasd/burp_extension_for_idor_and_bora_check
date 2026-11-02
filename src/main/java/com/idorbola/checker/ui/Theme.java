package com.idorbola.checker.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;

public class Theme {

    public static final Color BG          = new Color(0x1e1e1e);
    public static final Color PANEL       = new Color(0x252525);
    public static final Color PANEL_ALT   = new Color(0x2c2c2c);
    public static final Color BORDER      = new Color(0x3a3a3a);
    public static final Color TEXT        = new Color(0xd4d4d4);
    public static final Color TEXT_DIM    = new Color(0x888888);
    public static final Color TEXT_BRIGHT = new Color(0xf0f0f0);
    public static final Color ACCENT      = new Color(0xe0721e);
    public static final Color ACCENT_DIM  = new Color(0xa0500e);
    public static final Color SAME        = new Color(0xc0392b);
    public static final Color SAME_BG     = new Color(0x26, 0x0a, 0x08);
    public static final Color SIMILAR     = new Color(0xe67e22);
    public static final Color SIMILAR_BG  = new Color(0x28, 0x1a, 0x08);
    public static final Color DIFF        = new Color(0x27ae60);
    public static final Color DIFF_BG     = new Color(0x08, 0x20, 0x10);
    public static final Color ROW_SEL     = new Color(0x1e3a5c);
    public static final Color STATUS_OK   = new Color(0x7af5a0);
    public static final Color STATUS_ERR  = new Color(0xf57a7a);
    public static final Color STATUS_RDR  = new Color(0xf5d07a);

    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    public static final Font UI   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font UI_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font UI_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    public static void apply(JComponent c) {
        c.setBackground(BG);
        c.setForeground(TEXT);
        c.setFont(UI);
    }

    public static void applyPanel(JComponent c) {
        c.setBackground(PANEL);
        c.setForeground(TEXT);
        c.setFont(UI);
    }

    public static JButton button(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(0x333333));
        btn.setForeground(TEXT);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(BORDER, 1));
        btn.setFont(UI_SMALL);
        btn.setOpaque(true);
        return btn;
    }

    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT_DIM);
        btn.setForeground(new Color(0xf0d090));
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(ACCENT, 1));
        btn.setFont(UI_SMALL);
        btn.setOpaque(true);
        return btn;
    }

    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBackground(BG);
        sp.setBorder(new LineBorder(BORDER, 1));
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setBackground(PANEL);
        sp.getHorizontalScrollBar().setBackground(PANEL);
        return sp;
    }

    public static JTable table() {
        JTable t = new JTable();
        t.setBackground(PANEL);
        t.setForeground(TEXT);
        t.setFont(UI_SMALL);
        t.setGridColor(new Color(0x2a2a2a));
        t.setSelectionBackground(ROW_SEL);
        t.setSelectionForeground(TEXT_BRIGHT);
        t.setRowHeight(22);
        t.setShowGrid(true);
        t.setIntercellSpacing(new Dimension(0, 1));
        JTableHeader header = t.getTableHeader();
        header.setBackground(new Color(0x1e1e1e));
        header.setForeground(TEXT_DIM);
        header.setFont(UI_SMALL);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return t;
    }

    @SuppressWarnings("unchecked")
    public static <T> JComboBox<T> comboBox() {
        JComboBox<T> cb = new JComboBox<>();
        cb.setBackground(PANEL_ALT);
        cb.setForeground(TEXT);
        cb.setFont(UI_SMALL);
        cb.setBorder(new LineBorder(BORDER, 1));
        return cb;
    }

    public static JTextField textField(int cols) {
        JTextField tf = new JTextField(cols);
        tf.setBackground(new Color(0x1a1a1a));
        tf.setForeground(TEXT);
        tf.setFont(MONO);
        tf.setCaretColor(TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1),
                new EmptyBorder(2, 6, 2, 6)));
        return tf;
    }

    public static JTextArea textArea() {
        JTextArea ta = new JTextArea();
        ta.setBackground(new Color(0x1a1a1a));
        ta.setForeground(TEXT);
        ta.setFont(MONO);
        ta.setCaretColor(TEXT);
        ta.setBorder(new EmptyBorder(4, 6, 4, 6));
        return ta;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(UI_SMALL);
        return l;
    }

    public static JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DIM);
        l.setFont(UI_SMALL);
        return l;
    }

    public static JLabel accentLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(ACCENT);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        return l;
    }

    public static JPanel panel(Color bg) {
        JPanel p = new JPanel();
        p.setBackground(bg);
        return p;
    }

    public static Border matteBorder(int top, int right, int bottom, int left) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, BORDER);
    }

    public static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        sp.setBackground(new Color(0x1a1a1a));
        sp.setForeground(TEXT);
        sp.setFont(MONO);
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(new Color(0x1a1a1a));
            de.getTextField().setForeground(TEXT);
            de.getTextField().setFont(MONO);
            de.getTextField().setCaretColor(TEXT);
        }
        return sp;
    }

    public static Color statusColor(int code) {
        if (code < 0) return TEXT_DIM;
        if (code < 300) return STATUS_OK;
        if (code < 400) return STATUS_RDR;
        return STATUS_ERR;
    }

    public static Color verdictColor(com.idorbola.checker.model.Verdict v) {
        if (v == null) return TEXT_DIM;
        return switch (v) {
            case SAME       -> SAME;
            case SIMILAR    -> SIMILAR;
            case DIFFERENCE -> DIFF;
            default         -> TEXT_DIM;
        };
    }

    public static Color verdictBg(com.idorbola.checker.model.Verdict v) {
        if (v == null) return BG;
        return switch (v) {
            case SAME       -> SAME_BG;
            case SIMILAR    -> SIMILAR_BG;
            case DIFFERENCE -> DIFF_BG;
            default         -> BG;
        };
    }
}
