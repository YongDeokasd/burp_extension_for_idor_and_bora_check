package com.idorbola.checker.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;

public class Theme {

    public static final Color BG          = Color.WHITE;
    public static final Color PANEL       = Color.WHITE;
    public static final Color PANEL_ALT   = new Color(0xf5f5f5);
    public static final Color SIDEBAR     = new Color(0xf7f7f7);
    public static final Color INPUT_BG    = Color.WHITE;
    public static final Color BUTTON_BG   = new Color(0xf0f0f0);
    public static final Color CHIP_BG     = new Color(0xeeeeee);
    public static final Color TRACK       = new Color(0xe0e0e0);
    public static final Color BORDER      = new Color(0xd0d0d0);
    public static final Color TEXT        = new Color(0x1e1e1e);
    public static final Color TEXT_DIM    = new Color(0x666666);
    public static final Color TEXT_MUTED  = new Color(0xaaaaaa);
    public static final Color TEXT_BRIGHT = Color.BLACK;
    public static final Color ACCENT      = new Color(0xe0721e);
    public static final Color ACCENT_DIM  = new Color(0xfbe3cf);
    public static final Color GROUP       = new Color(0x4a4adf);
    public static final Color SAME        = new Color(0xc0392b);
    public static final Color SAME_BG     = new Color(0xfdecea);
    public static final Color SIMILAR     = new Color(0xc0650f);
    public static final Color SIMILAR_BG  = new Color(0xfdf2e6);
    public static final Color DIFF        = new Color(0x1e8449);
    public static final Color DIFF_BG     = new Color(0xe9f7ef);
    public static final Color ROW_SEL     = new Color(0xcfe3fa);
    public static final Color STATUS_OK   = new Color(0x1e8449);
    public static final Color STATUS_ERR  = new Color(0xc0392b);
    public static final Color STATUS_RDR  = new Color(0x9a7d0a);

    public static final Font MONO       = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    public static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font MONO_BOLD  = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font UI         = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font UI_BOLD    = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font UI_SMALL   = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font BADGE      = new Font(Font.SANS_SERIF, Font.BOLD, 12);

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
        btn.setBackground(BUTTON_BG);
        btn.setForeground(TEXT);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(BORDER, 1));
        btn.setFont(UI_SMALL);
        btn.setOpaque(true);
        return btn;
    }

    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
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
        t.setGridColor(new Color(0xeeeeee));
        t.setSelectionBackground(ROW_SEL);
        t.setSelectionForeground(TEXT_BRIGHT);
        t.setRowHeight(26);
        t.setShowGrid(true);
        t.setIntercellSpacing(new Dimension(0, 1));
        JTableHeader header = t.getTableHeader();
        header.setBackground(PANEL_ALT);
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
        tf.setBackground(INPUT_BG);
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
        ta.setBackground(INPUT_BG);
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
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
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
        sp.setBackground(INPUT_BG);
        sp.setForeground(TEXT);
        sp.setFont(MONO);
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(INPUT_BG);
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
