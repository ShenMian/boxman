package my.boxman.compat;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * 原版布局里 {@code <Button>} 的等价物 —— Holo 深色平台默认按钮
 * （{@code @drawable/btn_default_holo_dark}）。
 *
 * <p>原版 18 个 Activity 的 {@code AppBaseTheme} 父主题是 {@code android:Theme.Holo}
 * （深色），所以布局里凡是没有显式写 {@code android:background} 的 {@code <Button>}
 * 都渲染成 {@code Widget.Holo.Button}：
 *
 * <pre>{@code
 * <style name="Widget.Holo.Button" parent="Widget.Button">
 *     <item name="background">@drawable/btn_default_holo_dark</item>
 *     <item name="textAppearance">?attr/textAppearanceMedium</item>
 *     <item name="textColor">@color/primary_text_holo_dark</item>   <!-- #FFF3F3F3 -->
 *     <item name="minHeight">48dip</item>
 *     <item name="minWidth">64dip</item>
 * </style>
 * }</pre>
 *
 * <p>它是一个 9-patch 状态图（{@code values/drawable/btn_default_holo_dark.xml}）：
 * 正常 / 按下 / 聚焦 / 禁用四种位图，**没有 hover 态**（触摸端没有鼠标悬停）。
 * PC 端按 1dp = 1px 把 xxhdpi 位图（80×98）的像素除以 density 3.4 折算后自绘，
 * 保留 alpha 让 Swing 按 SrcOver 合成 —— 与原版「半透明底叠在窗口背景上」的语义一致。
 *
 * <p><b>为什么要自绘</b>：{@code JButton.setBackground()} 会被 FlatLaf 主题覆盖
 * （同 {@code myRecogView} 的元素按钮，见 {@code RENDER_NOTES.md}），所以
 * {@code paintComponent} 里直接画，并且**不调用 {@code super.paintComponent}**
 * —— 文字也自己画，免得 FlatLaf / {@code BasicButtonUI} 对禁用态前景色各有一套处理。
 */
public class HoloButton extends JButton {

    // ---------------------------------------------------------------- 配色（实测自 AOSP drawable-xxhdpi 的 9-patch）

    /** 正常态：1px 外描边（9-patch 的左右边框 + 上下沿） */
    public static final Color EDGE = new Color(32, 32, 32, 191);
    /** 正常态：顶面高光棱（9-patch 里最亮的一道，位于上缘下方约 4dp） */
    public static final Color TOP_HIGHLIGHT = new Color(82, 87, 91, 199);
    /** 正常态：主体（半透明深灰，叠在窗口黑底上约为 {@code #1E2327}） */
    public static final Color BODY = new Color(41, 47, 52, 189);
    /** 正常态：底缘压暗（9-patch 底部两行 {@code 1E1F1F} / {@code 1D1D1D} 的合并） */
    public static final Color BOTTOM = new Color(29, 29, 29, 213);

    /** 按下态：顶面（{@code F9F9F9} @ 145/255） */
    public static final Color PRESSED_TOP = new Color(249, 249, 249, 145);
    /** 按下态：主体（{@code F0F0F0} @ 89/255，整体变亮成中灰） */
    public static final Color PRESSED_BODY = new Color(240, 240, 240, 89);
    /** 按下态：外描边（{@code D0D0D0} @ 97/255） */
    public static final Color PRESSED_EDGE = new Color(208, 208, 208, 97);
    /** 按下态：底缘（{@code 7A7A7A} @ 131/255） */
    public static final Color PRESSED_BOTTOM = new Color(122, 122, 122, 131);

    /** 禁用态：顶面（{@code 969696} @ 128/255） */
    public static final Color DISABLED_TOP = new Color(150, 150, 150, 128);
    /** 禁用态：主体（{@code 999999} @ 39/255） */
    public static final Color DISABLED_BODY = new Color(153, 153, 153, 39);
    /** 禁用态：外描边（{@code 747474} @ 128/255） */
    public static final Color DISABLED_EDGE = new Color(116, 116, 116, 128);
    /** 禁用态：底缘（{@code 6C6C6C} @ 128/255） */
    public static final Color DISABLED_BOTTOM = new Color(108, 108, 108, 128);

    /** 文字色 {@code bright_foreground_holo_dark} */
    public static final Color TEXT = new Color(0xF3, 0xF3, 0xF3);
    /** 禁用文字色 {@code bright_foreground_disabled_holo_dark} */
    public static final Color TEXT_DISABLED = new Color(0x4C, 0x4C, 0x4C);

    // ---------------------------------------------------------------- 几何（9-patch 实测像素 ÷ density 3.4）

    /**
     * 9-patch 四周的**透明外缘**：按钮图形并不铺满 View 边界，而是内缩一圈，
     * 所以相邻按钮之间看起来比「边界间距」更宽。实测左右各 11px、上 12px、下 8px，
     * 除以 density 3.4 后取整。
     */
    public static final int INSET_X = 3;
    /** 上侧透明外缘（12px / 3.4 ≈ 3.5 → 4） */
    public static final int INSET_TOP = 4;
    /** 下侧透明外缘（8px / 3.4 ≈ 2.4 → 2） */
    public static final int INSET_BOTTOM = 2;

    /** {@code Widget.Holo.Button} 的 {@code minHeight} */
    public static final int MIN_HEIGHT = 48;
    /** {@code Widget.Holo.Button} 的 {@code minWidth} */
    public static final int MIN_WIDTH = 64;

    /** 原版布局里 {@code <Button>} 的默认 {@code android:textSize}（各布局多写 14sp） */
    public static final int TEXT_SIZE = 14;

    // ---------------------------------------------------------------- 构造

    public HoloButton(String text) {
        super(text);
        setUI(new BasicButtonUI());                 // 只借它的 getPreferredSize，绘制全部自绘
        setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
        setForeground(TEXT);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setFocusPainted(false);
        setMargin(new Insets(0, 0, 0, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** 等价于原版布局里的 {@code android:textSize="Nsp"}。 */
    public static HoloButton create(String text, int textSize) {
        HoloButton b = new HoloButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.PLAIN, textSize));
        return b;
    }

    /**
     * 原版 {@code android:paddingLeft / paddingTop / paddingBottom} 的等价物。
     * 文字在这个内边距**之内**居中（与 Android 的 {@code View} 内容区一致）。
     */
    public static HoloButton create(String text, int textSize, Insets contentPadding) {
        HoloButton b = create(text, textSize);
        b.setBorder(new EmptyBorder(contentPadding.top, contentPadding.left,
                contentPadding.bottom, contentPadding.right));
        return b;
    }

    // ---------------------------------------------------------------- 尺寸

    /**
     * {@code Widget.Holo.Button} 的 {@code minWidth} / {@code minHeight} 只在
     * {@code wrap_content}（{@code MeasureSpec.AT_MOST}）时生效；
     * 布局写了确定值（{@code layout_width="52dp"}）时走 {@code MeasureSpec.EXACTLY}，
     * **不受下限约束**。Swing 侧的对应判据就是「调用方有没有显式
     * {@code setPreferredSize(...)}」。
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (isPreferredSizeSet()) {
            return d;
        }
        return new Dimension(Math.max(d.width, MIN_WIDTH), Math.max(d.height, MIN_HEIGHT));
    }

    /**
     * {@code wrap_content} 的等价语义：不显式给 min/max 时，两者都退化成首选尺寸，
     * 这样放在 {@code BoxLayout} 里不会被拉长（Android 的 {@code wrap_content} 也是这样）。
     * 调用方显式 {@code setMinimumSize} / {@code setMaximumSize} 时仍以调用方为准。
     */
    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintNinePatch(g2, getWidth(), getHeight());
            paintLabel(g2);
        } finally {
            g2.dispose();
        }
    }

    /** 9-patch 的等价几何：透明外缘 → 圆角底 → 顶棱 → 底缘 → 1px 描边。 */
    private void paintNinePatch(Graphics2D g2, int w, int h) {
        int x0 = INSET_X;
        int x1 = w - INSET_X;
        int y0 = INSET_TOP;
        int y1 = h - INSET_BOTTOM;
        if (x1 - x0 < 4 || y1 - y0 < 4) {
            return;                                  // 太小了，原版也会退化成空图
        }
        int ww = x1 - x0;
        int hh = y1 - y0;

        Color edge, top, body, bottom;
        if (!isEnabled()) {
            edge = DISABLED_EDGE;
            top = DISABLED_TOP;
            body = DISABLED_BODY;
            bottom = DISABLED_BOTTOM;
        } else if (getModel().isPressed()) {
            edge = PRESSED_EDGE;
            top = PRESSED_TOP;
            body = PRESSED_BODY;
            bottom = PRESSED_BOTTOM;
        } else {
            edge = EDGE;
            top = TOP_HIGHLIGHT;
            body = BODY;
            bottom = BOTTOM;
        }

        // 9-patch 的圆角只有 3px（≈1dp），用 2px 的弧近似
        g2.setColor(body);
        g2.fillRoundRect(x0, y0, ww, hh, 2, 2);
        g2.setColor(top);
        g2.fillRect(x0 + 1, y0 + 1, ww - 2, 1);
        g2.setColor(bottom);
        g2.fillRect(x0 + 1, y1 - 2, ww - 2, 1);
        g2.setColor(edge);
        g2.drawRoundRect(x0, y0, ww - 1, hh - 1, 2, 2);
    }

    /**
     * 文字自己画：在内容区（去掉 {@code border} 内边距后的矩形）里水平、垂直居中。
     *
     * <p>⚠️ 颜色由 Holo 调色板**固定**（正常 {@link #TEXT} / 禁用 {@link #TEXT_DISABLED}），
     * 与 Android 的 {@code Widget.Holo.Button} 把 {@code textColor} 写死在 style 里一致 ——
     * 改 {@code setForeground(...)} 不会生效，要换色请改常量或另写子类。
     */
    private void paintLabel(Graphics2D g2) {
        String[] lines = labelLines();
        if (lines.length == 0) {
            return;
        }
        Insets in = getInsets();
        int bx = in.left;
        int by = in.top;
        int bw = getWidth() - in.left - in.right;
        int bh = getHeight() - in.top - in.bottom;

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        g2.setColor(isEnabled() ? TEXT : TEXT_DISABLED);

        FontMetrics fm = g2.getFontMetrics();
        int blockH = lines.length * fm.getHeight();
        int y = by + (bh - blockH) / 2 + fm.getAscent();
        for (String line : lines) {
            g2.drawString(line, bx + (bw - fm.stringWidth(line)) / 2, y);
            y += fm.getHeight();
        }
    }

    /**
     * 把按钮文字拆成逐行。原版有几处 {@code <Button>} 的文字里带 {@code \n}
     * （如 {@code action_manage.xml} 的「恢\n复」复选框旁的按钮），PC 侧用
     * {@code <html>…<br>…</html>} 表达，这里统一还原成纯文本行。
     */
    public String[] labelLines() {
        String t = getText();
        if (t == null || t.isEmpty()) {
            return new String[0];
        }
        String s = t;
        if (s.regionMatches(true, 0, "<html>", 0, 6)) {
            s = s.substring(6);
            int end = s.toLowerCase().lastIndexOf("</html>");
            if (end >= 0) {
                s = s.substring(0, end);
            }
            s = s.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        }
        return s.split("\n", -1);
    }
}
