package my.boxman.compat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * 原版对话框内容区的公共骨架与配色。
 *
 * <p>原版 16 个 {@code *_dialog.xml} 反复使用同一套写法，实测统计（全部 dialog 布局里出现的颜色）：
 * {@code #363636} 出现 101 次（内容底色）、{@code #242424} 16 次（输入框底）、
 * {@code #334455} 5 次（分组条）、{@code #ffffff}/{@code #000000}/{@code #808080} 若干。
 * 布局骨架几乎都是「6dp 色条 + 居中一行 + 6dp 色条」。
 *
 * <p>文字色未在布局里指定，取主题默认：{@code Theme.Holo}（深色）的
 * {@code primary_text_holo_dark → bright_foreground_holo_dark → background_holo_light}
 * = <b>{@code #FFF3F3F3}</b>；禁用态 {@code bright_foreground_disabled_holo_dark = #FF4C4C4C}。
 * 输入框 16sp、padding 4dp、{@code selectAllOnFocus="true"}。
 */
public final class HoloContent {

    // ---------------------------------------------------------------- 颜色（取自原版布局与 framework 资源）

    /** 内容区底色，原版出现最多（101 次） */
    public static final Color BAND = new Color(0x36, 0x36, 0x36);
    /** 分组条底色（{@code find_dialog} 等用） */
    public static final Color HEAD = new Color(0x33, 0x44, 0x55);
    /** 输入框底色 */
    public static final Color FIELD_BG = new Color(0x24, 0x24, 0x24);
    /** 正文文字色 {@code bright_foreground_holo_dark} */
    public static final Color TEXT = new Color(0xF3, 0xF3, 0xF3);
    /** 禁用文字色 {@code bright_foreground_disabled_holo_dark} */
    public static final Color TEXT_DISABLED = new Color(0x4C, 0x4C, 0x4C);
    /** 列表选中色（{@code find_dialog} 的 listSelector） */
    public static final Color LIST_SELECTED = new Color(0x00, 0x88, 0xAA);
    /** Holo 蓝，用于滑杆进度 */
    public static final Color HOLO_BLUE = new Color(0x33, 0xB5, 0xE5);
    /** 滑杆轨道 */
    public static final Color SLIDER_TRACK = new Color(0x5A, 0x5A, 0x5A);

    // ---------------------------------------------------------------- 尺寸

    /** 原版正文/输入框统一 16sp */
    public static final int TEXT_SIZE = 16;
    /** 输入框 padding 4dp */
    public static final int FIELD_PAD = 4;
    /** 「6dp 色条」的上下留白 */
    public static final int BAND_PAD = 6;

    private HoloContent() {
    }

    /** 原版正文统一字体。 */
    public static Font font(int style) {
        return new Font("Microsoft YaHei", style, TEXT_SIZE);
    }

    /** 纯色通栏条（对应布局里的 {@code <View android:background=... android:layout_height="Ndp"/>}）。 */
    public static JComponent band(Color color, int heightDp) {
        JPanel p = new JPanel();
        p.setBackground(color);
        p.setOpaque(true);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = new Dimension(0, heightDp);
        p.setMinimumSize(d);
        p.setPreferredSize(d);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, heightDp));
        return p;
    }

    /** 6dp 的 {@code #363636} 色条。 */
    public static JComponent band() {
        return band(BAND, BAND_PAD);
    }

    /** 透明弹性间隔（对应 {@code <View android:layout_height="20dp"/>} 这类空行）。 */
    public static JComponent gap(int heightDp) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = new Dimension(0, heightDp);
        p.setMinimumSize(d);
        p.setPreferredSize(d);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, heightDp));
        return p;
    }

    /**
     * 原版最常见的骨架：{@code #363636} 底 + 上下 6dp 留白 + 水平居中一行。
     * 对应布局里「View 6dp → LinearLayout(gravity=center, background=#363636) → View 6dp」。
     */
    public static JPanel row(Component... children) {
        return row(BAND, children);
    }

    /** 同上，可指定底色。 */
    public static JPanel row(Color bg, Component... children) {
        return row(bg, BAND_PAD, children);
    }

    /** 同上，可指定底色与上下留白（原版有些行没有 6dp 留白，例如 {@code color_dialog} 的红/绿/蓝行）。 */
    public static JPanel row(Color bg, int vPad, Component... children) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(bg);
        p.setOpaque(true);
        p.setBorder(new EmptyBorder(vPad, 0, vPad, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createHorizontalGlue());
        for (Component c : children) {
            p.add(c);
        }
        p.add(Box.createHorizontalGlue());
        return p;
    }

    /** 无上下留白的行（原版 {@code color_dialog} 的三条颜色行）。 */
    public static JPanel tightRow(Component... children) {
        return row(BAND, 0, children);
    }

    /**
     * 分组条：左标签（固定宽度、16sp）+ 右侧内容，底色 {@code #334455}。
     * 对应 {@code find_dialog} 里「TextView 100dp + LinearLayout(gravity=right)」那一行。
     */
    public static JPanel headRow(String labelText, int labelWidthDp, Component... right) {
        JLabel left = label(labelText, labelWidthDp, SwingConstants.LEFT);

        JPanel rightBox = new JPanel();
        rightBox.setLayout(new BoxLayout(rightBox, BoxLayout.X_AXIS));
        rightBox.setOpaque(false);
        rightBox.add(Box.createHorizontalGlue());
        for (Component c : right) {
            rightBox.add(c);
        }

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(HEAD);
        p.setOpaque(true);
        p.setBorder(new EmptyBorder(BAND_PAD, FIELD_PAD, BAND_PAD, FIELD_PAD));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(left, BorderLayout.WEST);
        p.add(rightBox, BorderLayout.CENTER);
        return p;
    }

    /** 16sp 正文标签（白 {@code #F3F3F3}）。 */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.PLAIN));
        l.setForeground(TEXT);
        return l;
    }

    /** 16sp 标签，固定宽度（对应布局里的 {@code android:layout_width="100dp"}）。 */
    public static JLabel label(String text, int widthDp, int horizontalAlign) {
        JLabel l = label(text);
        l.setHorizontalAlignment(horizontalAlign);
        l.setPreferredSize(new Dimension(widthDp, l.getPreferredSize().height));
        l.setMinimumSize(new Dimension(widthDp, l.getPreferredSize().height));
        l.setMaximumSize(new Dimension(widthDp, l.getPreferredSize().height));
        return l;
    }

    /**
     * 输入框：{@code #242424} 底、16sp、padding 4dp、{@code selectAllOnFocus}。
     *
     * @param widthDp 固定宽度（原版用 84/100/160dp）
     */
    public static JTextField field(int widthDp, String text) {
        return field(widthDp, text, FIELD_BG, TEXT);
    }

    /** 输入框，可指定底色与文字色（例如 {@code rule_dialog} 的「颜色示例」框）。 */
    public static JTextField field(int widthDp, String text, Color bg, Color fg) {
        JTextField tf = new JTextField(text);
        tf.setFont(font(Font.PLAIN));
        tf.setBackground(bg);
        tf.setForeground(fg);
        tf.setCaretColor(fg);
        tf.setBorder(new EmptyBorder(FIELD_PAD, FIELD_PAD, FIELD_PAD, FIELD_PAD));
        // 原版 textColorHighlight="#000000"
        tf.setSelectionColor(Color.BLACK);
        tf.setSelectedTextColor(fg);
        Dimension d = new Dimension(widthDp, tf.getPreferredSize().height);
        tf.setPreferredSize(d);
        tf.setMinimumSize(d);
        tf.setMaximumSize(d);
        // 对应 android:selectAllOnFocus="true"
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                tf.selectAll();
            }
        });
        return tf;
    }

    /** 数字输入框（对应 {@code android:digits="0123456789"}）。 */
    public static JTextField numberField(int widthDp, String text) {
        JTextField tf = field(widthDp, text);
        ((javax.swing.text.AbstractDocument) tf.getDocument())
                .setDocumentFilter(new DigitsOnlyFilter());
        return tf;
    }

    /** 深色主题复选框。 */
    public static JCheckBox check(String text, boolean selected) {
        JCheckBox c = new JCheckBox(text, selected);
        c.setFont(font(Font.PLAIN));
        c.setForeground(TEXT);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setBorder(new EmptyBorder(FIELD_PAD, FIELD_PAD, FIELD_PAD, FIELD_PAD));
        return c;
    }

    /** 深色主题单选按钮。 */
    public static JRadioButton radio(String text, boolean selected) {
        JRadioButton r = new JRadioButton(text, selected);
        r.setFont(font(Font.PLAIN));
        r.setForeground(TEXT);
        r.setOpaque(false);
        r.setFocusPainted(false);
        r.setBorder(new EmptyBorder(FIELD_PAD, FIELD_PAD, FIELD_PAD, FIELD_PAD));
        return r;
    }

    /** 深色主题按钮（对话框内部用的普通按钮，非 HoloAlertDialog 底栏按钮）。 */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(font(Font.PLAIN));
        b.setForeground(TEXT);
        b.setBackground(FIELD_BG);
        b.setFocusPainted(false);
        return b;
    }

    /** 深色配色的微调框（原版这些位置是数字 {@code EditText}）。 */
    public static JSpinner spinner(int widthDp, int value, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        sp.setFont(font(Font.PLAIN));
        sp.setBackground(FIELD_BG);
        sp.setForeground(TEXT);
        sp.setBorder(BorderFactory.createLineBorder(FIELD_BG));
        Dimension d = new Dimension(widthDp, 32);
        sp.setPreferredSize(d);
        sp.setMinimumSize(d);
        sp.setMaximumSize(d);
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(FIELD_BG);
            tf.setForeground(TEXT);
            tf.setCaretColor(TEXT);
            tf.setFont(font(Font.PLAIN));
            tf.setBorder(new EmptyBorder(FIELD_PAD, FIELD_PAD, FIELD_PAD, FIELD_PAD));
        }
        return sp;
    }

    /** 把一行里的若干控件放进固定宽度的水平盒（用于「最小 ~ 最大」这类成对输入）。 */
    public static JPanel pair(JComponent... children) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(4));
            }
            p.add(children[i]);
        }
        return p;
    }

    /** 深色主题列表：{@code #363636} 底、白字、黑 1px 分隔线、{@code #0088aa} 选中。 */
    public static <E> JList<E> list(E[] items) {
        JList<E> l = new JList<>(items);
        l.setFont(font(Font.PLAIN));
        l.setBackground(BAND);
        l.setForeground(TEXT);
        l.setSelectionBackground(LIST_SELECTED);
        l.setSelectionForeground(TEXT);
        l.setFixedCellHeight(l.getFontMetrics(l.getFont()).getHeight() + 2 * FIELD_PAD);
        l.setBorder(new EmptyBorder(FIELD_PAD, FIELD_PAD, FIELD_PAD, FIELD_PAD));
        return l;
    }

    /** 无边框深色滚动面板（列表外层）。 */
    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BAND);
        sp.setBackground(BAND);
        sp.setOpaque(true);
        sp.getVerticalScrollBar().setUnitIncrement(TEXT_SIZE);
        return sp;
    }

    /**
     * Holo SeekBar 等价物：细轨道 + 蓝色已选段 + 圆形滑块。
     * 对应原版 {@code rule_dialog} / {@code color_dialog} 里的 {@code SeekBar}（{@code max="255"}）。
     */
    public static JSlider slider(int value, int widthDp) {
        JSlider s = new JSlider(0, 255, value) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = getHeight() / 2;
                int inset = 12;
                int x0 = inset;
                int x1 = getWidth() - inset;
                double ratio = (getMaximum() > getMinimum())
                        ? (getValue() - getMinimum()) / (double) (getMaximum() - getMinimum())
                        : 0;
                int thumbX = (int) Math.round(x0 + ratio * (x1 - x0));

                g2.setColor(SLIDER_TRACK);
                g2.fillRoundRect(x0, cy - 1, x1 - x0, 2, 2, 2);
                g2.setColor(HOLO_BLUE);
                g2.fillRoundRect(x0, cy - 1, Math.max(0, thumbX - x0), 2, 2, 2);
                // scrubber_control_holo：蓝色外圈 + 白色内圈
                g2.setColor(HOLO_BLUE);
                g2.fillOval(thumbX - 10, cy - 10, 20, 20);
                g2.setColor(Color.WHITE);
                g2.fillOval(thumbX - 4, cy - 4, 8, 8);
                g2.dispose();
            }
        };
        s.setOpaque(false);
        s.setFocusable(false);
        Dimension d = new Dimension(widthDp, 32);
        s.setPreferredSize(d);
        s.setMinimumSize(d);
        s.setMaximumSize(d);
        return s;
    }

    /** 垂直堆叠（左对齐），用于拼装内容区。 */
    public static JPanel column(JComponent... rows) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        for (JComponent r : rows) {
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(r);
        }
        return p;
    }

    /** 只允许数字的输入过滤（对应 {@code android:digits="0123456789"}）。 */
    private static final class DigitsOnlyFilter extends javax.swing.text.DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text,
                                 javax.swing.text.AttributeSet attr) {
            if (text != null && text.matches("\\d*")) {
                try {
                    fb.insertString(offset, text, attr);
                } catch (javax.swing.text.BadLocationException ignored) {
                    // 位置非法，忽略
                }
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text,
                            javax.swing.text.AttributeSet attr) {
            if (text == null || text.matches("\\d*")) {
                try {
                    fb.replace(offset, length, text, attr);
                } catch (javax.swing.text.BadLocationException ignored) {
                    // 位置非法，忽略
                }
            }
        }
    }
}
