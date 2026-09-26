package my.boxman.compat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 原版 {@code res/values/style.xml} 的 {@code tab_style} —— 底栏「图标在上、文字在下」的
 * 标签按钮。原版的 {@code myGameView}（{@code main_bottom}）与 {@code myEditView}
 * （{@code edit_bottom}）用的是**同一份**样式，所以这里做成共用件。
 *
 * <pre>
 * &lt;style name="tab_style"&gt;
 *   &lt;item name="android:textSize"&gt;9.0dip&lt;/item&gt;
 *   &lt;item name="android:layout_margin"&gt;2.0dip&lt;/item&gt;
 *   &lt;item name="android:button"&gt;@null&lt;/item&gt;
 *   &lt;item name="android:layout_weight"&gt;1.0&lt;/item&gt;
 *   &lt;item name="android:gravity"&gt;center&lt;/item&gt;
 * &lt;/style&gt;
 * </pre>
 *
 * <p>原版用法：{@code <RadioGroup android:background="#ff778899" android:gravity="center_vertical">}
 * 里放若干 {@code <CheckBox style="@style/tab_style" android:drawableTop="@drawable/xxx" android:text="..."/>}，
 * 每个 CheckBox 都是 {@code layout_width="fill_parent" + layout_weight="1.0"}，即**精确等分整宽**。
 *
 * <p><b>底色与「按下」效果。</b>底栏通体是 RadioGroup 的 {@code #778899}；其中三个**开关**按钮
 * —— {@code cb_IM}（瞬移）、{@code cb_BK}（逆推）、{@code cb_Sel}（计数）—— 在选中时由原版代码
 * 显式改成更深的 {@code #445566}，这就是「按下去」的视觉效果：
 *
 * <pre>
 * // 原版 myGameView.java:1552 / 1561 / 1585 / 1632 等处
 * if (isChecked) buttonView.setBackgroundColor(0xff445566);
 * else           buttonView.setBackgroundColor(0xff778899);
 * </pre>
 *
 * 而 {@code cb_TR}（转置，每点一次换一转）、{@code cb_More}（更多，弹菜单）以及
 * {@code bt_UnDo} / {@code bt_ReDo}（后退 / 前进，靠 {@code setChecked(!isChecked())} 触发）
 * 都**没有**这一步，所以它们始终是 {@code #778899} —— 别「顺手统一」。
 */
public final class HoloTabBar {

    /** 底栏高度：原版 RadioGroup 实测 163px / density 3.4051 ≈ 48dp（两个界面一致）。 */
    public static final int BAR_HEIGHT = 48;
    /** 底栏图标：原版 {@code drawable/} 无密度后缀 = mdpi，PNG 为 32×32 → 32dp。 */
    public static final int ICON_SIZE = 32;
    /** 底栏文字：{@code tab_style} 的 {@code textSize="9.0dip"}。 */
    public static final int TEXT_SIZE = 9;
    /** {@code tab_style} 的 {@code layout_margin="2.0dip"}（顶）。 */
    private static final int TAB_MARGIN = 2;
    /** 9sp 文字的行高（Microsoft YaHei 实测 ≈ 11dp）。 */
    private static final int TEXT_LINE = 11;

    /** RadioGroup 的 {@code android:background="#ff778899"}。 */
    public static final Color BG = new Color(0x77, 0x88, 0x99);
    /**
     * 开关按钮**选中**时的底色：原版 {@code setBackgroundColor(0xff445566)}。
     * 比 {@link #BG} 更深，即用户看到的「按下去」效果。
     */
    public static final Color BG_CHECKED = new Color(0x44, 0x55, 0x66);
    /** 开关按钮**未选中**时的底色：原版 {@code setBackgroundColor(0xff778899)}，与 {@link #BG} 同值。 */
    public static final Color BG_UNCHECKED = BG;
    /** 文字色：{@code ?attr/textColorPrimaryDisableOnly} 的启用态 = 纯白。 */
    public static final Color FG = Color.WHITE;
    /**
     * 文字色：{@code ?attr/textColorPrimaryDisableOnly} 的禁用态 = {@code #80ffffff}。
     * 半透明白叠在 {@link #BG} 上 ≈ (187,196,204)，与原版截图实测一致。
     */
    public static final Color FG_DISABLED = new Color(0x80FFFFFF, true);

    private HoloTabBar() {
    }

    /** 取 {@code res/drawable/<name>.png} 并缩到 {@link #ICON_SIZE}（找不到返回 null）。 */
    public static Icon icon(String resName) {
        java.awt.image.BufferedImage img = ResourceLoader.getDrawable(resName);
        if (img == null) return null;
        Image scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /** 新建一个底栏容器（{@code #778899} 平底 + 1/N 等分列 + 固定高度）。 */
    public static JPanel bar() {
        JPanel bar = new JPanel(new TabBarLayout());
        bar.setBackground(BG);
        bar.setPreferredSize(new Dimension(0, BAR_HEIGHT));
        return bar;
    }

    /**
     * 底栏等分列布局。
     *
     * <p>原版每个 CheckBox 是 {@code layout_width="fill_parent" + layout_weight="1.0"}，
     * 即每格精确占 {@code 宽/n}（370/8 = 46.25dp），图标因此落在 23.1 / 69.4 / 115.6 … 这些位置上。
     *
     * <p>Swing 的 {@code GridLayout} 在总宽不能整除时会留边距（370 = 8×46 + 2，于是左右各留 1px、
     * 整体右移 1px）；{@code GridBagLayout} 又会被按钮的最小宽度撑开。所以这里直接按 1/n 切。
     */
    public static class TabBarLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, BAR_HEIGHT);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, BAR_HEIGHT);
        }

        @Override
        public void layoutContainer(Container parent) {
            int n = parent.getComponentCount();
            if (n == 0) return;
            int w = parent.getWidth();
            int h = parent.getHeight();
            for (int i = 0; i < n; i++) {
                int left = Math.round((float) w * i / n);
                int right = Math.round((float) w * (i + 1) / n);
                parent.getComponent(i).setBounds(left, 0, right - left, h);
            }
        }
    }

    /** 原版 {@code <CheckBox style="@style/tab_style">}：平底、无边框、图标在上文字在下。 */
    public static class TabButton extends JToggleButton {
        private ActionListener longClickListener = null;

        public TabButton(String text) {
            super(text);
            initButton();
        }

        public TabButton(String text, Icon icon) {
            super(text, icon);
            initButton();
        }

        private void initButton() {
            setFocusPainted(false);
            // tab_style 里 android:button="@null"，即没有勾选框图形，也没有按钮底板。
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(false);

            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setIconTextGap(0);
            setBorder(new EmptyBorder(TAB_MARGIN, 0,
                    BAR_HEIGHT - TAB_MARGIN - ICON_SIZE - TEXT_LINE, 0));
            setMargin(new Insets(0, 0, 0, 0));
            setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
            setBackground(BG);
            setForeground(FG);
        }

        @Override
        protected void paintComponent(Graphics g) {
            // 整体自绘，**不调 super.paintComponent**。
            // 原因有两个：
            //  1. Swing/FlatLaf 会给 JToggleButton 画渐变与边框，而原版是纯色平底；
            //  2. 更要紧的是**禁用态**：FlatLaf 会把图标和文字一起调灰，但原版两者都不灰 ——
            //     · 图标来自 {@code android:drawableTop}，是一张**没有 state list** 的 PNG，
            //       {@code setEnabled(false)} 根本不影响它（截图实测禁用项的图标峰值仍为
            //       (233,237,240)，与启用项同级；只有文字变暗）；
            //     · 文字色是 {@code ?attr/textColorPrimaryDisableOnly = #80ffffff}，
            //       禁用时半透明白叠在 #778899 上 ≈ (187,196,204)，启用时纯白。
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int y = TAB_MARGIN;
                Icon ic = getIcon();
                if (ic != null) {
                    ic.paintIcon(this, g2, (getWidth() - ic.getIconWidth()) / 2, y);
                    y += ic.getIconHeight();
                }

                String text = getText();
                if (text != null && !text.isEmpty()) {
                    g2.setFont(getFont());
                    g2.setColor(isEnabled() ? FG : FG_DISABLED);
                    FontMetrics fm = g2.getFontMetrics();
                    // 原版：CheckBox 高 48-2-2 = 44dp、padding 0，drawableTop 占 32dp，
                    // 文字排在剩下的 12dp 里并整体居中（TextView 的 compound drawable 布局）。
                    int textArea = BAR_HEIGHT - 2 * TAB_MARGIN - ICON_SIZE;
                    int baseline = y + (textArea + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, baseline);
                }
            } finally {
                g2.dispose();
            }
        }

        public boolean isChecked() {
            return isSelected();
        }

        public void setChecked(boolean b) {
            setSelected(b);
        }

        /**
         * 按选中态刷底色 —— 等价于原版开关按钮监听器里那两行
         * {@code setBackgroundColor(0xff445566)} / {@code setBackgroundColor(0xff778899)}。
         *
         * <p>⚠️ <b>不要</b>把这件事塞进 {@link #setChecked(boolean)}：原版只有
         * {@code cb_IM / cb_BK / cb_Sel} 三个开关会改底色，而 {@code bt_UnDo / bt_ReDo}
         * 是靠 {@code setChecked(!isChecked())} 触发动作的**瞬时**按钮，它们不该变色。
         * 由调用方显式调本方法，与原版逐行对应。
         */
        public void setCheckedBackground(boolean checked) {
            setBackgroundColor(checked ? BG_CHECKED : BG_UNCHECKED);
        }

        public void setTextColor(int argb) {
            setForeground(new Color(argb, true));
        }

        public void setBackgroundColor(int argb) {
            setBackground(new Color(argb, true));
        }

        /** 见 {@link #setCheckedBackground(boolean)}。 */
        public void setBackgroundColor(Color c) {
            setBackground(c);
        }

        public void setOnLongClickListener(ActionListener listener) {
            this.longClickListener = listener;
        }

        public void onKeyLongPress(int code, Object event) {
            if (longClickListener != null) {
                longClickListener.actionPerformed(
                        new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "longPress"));
            }
        }
    }
}
