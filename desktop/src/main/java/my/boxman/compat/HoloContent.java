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

    // ---------------------------------------------------------------- 原版 CustomCheckboxTheme

    /** {@code query_dialog} 表头行高度：实测 110px @ density 3.405 ≈ 32.3dp */
    public static final int CHECK_ROW_HEIGHT = 33;
    /** {@code cb_normal.png} / {@code cb_pressed.png} 的位图尺寸（mdpi，即 32dp×33dp） */
    private static final int CHECK_ICON = 32;

    /**
     * 原版 {@code style/CustomCheckboxTheme} 的复选框：{@code android:button=@drawable/checkbox_style}，
     * 选择器指向 {@code cb_pressed}（已选，绿勾）/ {@code cb_normal}（未选，空框），
     * 位图 32×33 @ mdpi → <b>32dp 见方</b>，比 Holo 默认的 16dp 方框大一倍。
     *
     * <p><b>图标与文字的相对位置照 {@code CompoundButton} 的语义来</b>（别凭直觉写成「图标也缩进 padding」）：
     * {@code onDraw()} 里 {@code final int left = isLayoutRtl() ? getWidth() - drawableWidth : 0;}
     * —— 按钮位图是画在控件 <b>x=0</b> 处的，{@code android:padding} <b>不作用于位图</b>；
     * 而 {@code getCompoundPaddingLeft()} 返回 {@code paddingLeft + drawableWidth}，
     * 文字才从那里开始。所以 {@code padding=4dp} 的效果是
     * <b>位图 0..32、文字 36..</b>（中间 4dp 的缝），不是「位图 4..36」。
     * 实测原版截图印证：答案库框 140..240 → 位图 140..172、文字 176；全选框 240..320 →
     * 绿勾 240..272、文字 276。故此处 {@code left=0} + {@code iconTextGap=FIELD_PAD}。
     *
     * <p>另外高度不计入控件高度（位图由 {@code onDraw} 竖直居中绘制，可略微溢出），
     * 所以控件高 ≈ 文字行高 + 8dp padding，这里按实测固定为 {@link #CHECK_ROW_HEIGHT}。
     */
    public static JCheckBox check32(String text, boolean selected, int widthDp) {
        JCheckBox c = new JCheckBox(text, selected);
        c.setFont(font(Font.PLAIN));
        c.setForeground(TEXT);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setBorder(new EmptyBorder(FIELD_PAD, 0, FIELD_PAD, FIELD_PAD));
        c.setIcon(checkboxIcon(false));
        c.setSelectedIcon(checkboxIcon(true));
        c.setIconTextGap(FIELD_PAD);
        Dimension d = new Dimension(widthDp, CHECK_ROW_HEIGHT);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        return c;
    }

    /**
     * {@code CustomCheckboxTheme} 的复选框，宽度按内容算 —— 等价于原版布局里的
     * {@code android:layout_width="wrap_content"}。
     *
     * <p><b>别拿 {@link #check32} 的固定宽度凑合</b>：宽度不足时 Swing 会把文字截成省略号，
     * 而原版是「32dp 位图 + 4dp iconTextGap + 文字 + 4dp 右 padding」自然撑开。
     */
    public static JCheckBox wrapCheck(String text, boolean selected) {
        return wrapCheck(text, selected, TEXT_SIZE);
    }

    /** 同上，可指定字号（原版 {@code myActGMView} 那一排用的是 18sp）。 */
    public static JCheckBox wrapCheck(String text, boolean selected, int textSize) {
        Font f = new Font("Microsoft YaHei", Font.PLAIN, textSize);
        JCheckBox probe = new JCheckBox();
        // 位图 32dp + iconTextGap 4dp + 文字 + 右侧 padding 4dp
        int w = CHECK_ICON + FIELD_PAD + probe.getFontMetrics(f).stringWidth(text) + FIELD_PAD;
        JCheckBox c = check32(text, selected, w);
        c.setFont(f);
        return c;
    }

    /** {@code cb_normal} / {@code cb_pressed} 位图 → 32dp 图标。 */
    private static Icon checkboxIcon(boolean checked) {
        java.awt.image.BufferedImage src =
                ResourceLoader.getDrawable(checked ? "cb_pressed" : "cb_normal");
        if (src == null) {
            return null;
        }
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                CHECK_ICON, CHECK_ICON, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, CHECK_ICON, CHECK_ICON, null);
        g.dispose();
        return new ImageIcon(out);
    }

    // ---------------------------------------------------------------- 原版 ListView 等价物

    /** 列表项高度：实测 73px @ density 3.405 ≈ 21.4dp */
    public static final int LIST_ITEM_HEIGHT = 21;
    /** 列表分隔线：原版 {@code android:dividerHeight="4px"}（设备像素）≈ 1dp */
    public static final int LIST_DIVIDER_HEIGHT = 1;

    /**
     * 原版 {@code ListView} 的等价物：每项一个 {@code TextView}，
     * 选中底色 {@code #0088aa}、未选中 {@code #363636}，项间 {@code #000000} 分隔线。
     */
    public static JList<String> itemList(String[] items) {
        JList<String> l = new JList<>(items);
        l.setFont(font(Font.PLAIN));
        l.setBackground(BAND);
        l.setForeground(TEXT);
        l.setOpaque(true);
        l.setSelectionBackground(LIST_SELECTED);
        l.setSelectionForeground(TEXT);
        l.setFixedCellHeight(LIST_ITEM_HEIGHT + LIST_DIVIDER_HEIGHT);
        l.setCellRenderer(new ItemRenderer());
        return l;
    }

    /** 单项渲染：底色 + 底部 1px 黑线（原版 ListView 的 divider）。 */
    private static final class ItemRenderer extends JLabel implements ListCellRenderer<String> {
        ItemRenderer() {
            setOpaque(true);
            setFont(HoloContent.font(Font.PLAIN));
            setBorder(new EmptyBorder(0, 0, LIST_DIVIDER_HEIGHT, 0));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value);
            setForeground(TEXT);
            setBackground(isSelected ? LIST_SELECTED : BAND);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, getHeight() - LIST_DIVIDER_HEIGHT, getWidth(), LIST_DIVIDER_HEIGHT);
        }
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
     * 深色细滚动条：原版 Android 的滚动条是半透明细条，这里用 {@code #006060} 的等价观感，
     * 与主界面的 {@code DarkScrollBarUI} 保持一致。
     */
    public static void darkScrollBar(JScrollPane sp) {
        JScrollBar bar = sp.getVerticalScrollBar();
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(0x00, 0x60, 0x60);
                this.trackColor = BAND;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                Dimension zero = new Dimension(0, 0);
                b.setPreferredSize(zero);
                b.setMinimumSize(zero);
                b.setMaximumSize(zero);
                return b;
            }
        });
        bar.setPreferredSize(new Dimension(6, 0));
        bar.setOpaque(true);
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
