package my.boxman.compat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Android Holo 深色弹出菜单（{@code Widget.Holo.PopupMenu} / {@code ContextMenu}）的 PC 等价物。
 *
 * <p>原版里 **ActionBar 溢出菜单**和**长按上下文菜单**用的是同一套弹出菜单样式
 * （framework 的 {@code popup_menu_holo_dark} 9-patch + {@code list_selector_holo_dark}），
 * 所以这里抽成一个共用类，{@code myActionBar} 的 ⋮ 菜单与各窗口的右键上下文菜单都走它。
 *
 * <p>取值来源：
 * <ul>
 *   <li>底板 {@code #333333}（framework {@code popup_menu_holo_dark.9.png} 填充色）</li>
 *   <li>文字 {@code #EEEEEE}、禁用 {@code #777777}、选中 {@code #0083C5}（Holo 蓝，与 ActionBar 同色）</li>
 *   <li>行高 40dp、左内边距 16dp、勾选标记占位 22dp、最小宽 200dp</li>
 * </ul>
 *
 * <p>注意 {@link Row#getPreferredSize()} 在**不可见时返回 0×0**：原版用
 * {@code MenuItem.setVisible(false)} 隐藏条目时，弹出菜单会重新测量、不占位；
 * Swing 的 {@code JPopupMenu} 会为不可见的子组件保留空间，必须靠这个覆写抹掉。
 */
public final class HoloPopupMenu {

    /** 底板 {@code #333333} */
    public static final Color BG = new Color(0x333333);
    /** 文字 {@code #EEEEEE} */
    public static final Color FG = new Color(0xEEEEEE);
    /** 悬停 / 选中底色，Holo 蓝 */
    public static final Color HOVER = new Color(0x0083C5);
    /** 禁用文字 {@code #777777} */
    public static final Color DISABLED = new Color(0x777777);
    /** 分隔线 {@code #555555} */
    public static final Color SEPARATOR = new Color(0x555555);

    public static final int TEXT_SIZE = 15;
    public static final int ROW_HEIGHT = 40;
    public static final int PADDING_LEFT = 16;
    /** 勾选标记（✓）占位宽度 */
    public static final int CHECK_WIDTH = 22;
    public static final int MIN_WIDTH = 200;

    private HoloPopupMenu() {
    }

    /** 建一个空的 Holo 深色弹出菜单。 */
    public static JPopupMenu create() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG);
        menu.setBorder(BorderFactory.createLineBorder(SEPARATOR));
        return menu;
    }

    /**
     * 追加一个菜单项。
     *
     * @param action 点击后的动作；{@code null} 表示只做占位（配合 {@link Row#setEnabled} 置灰）
     * @return 新建的行，便于调用方设置启用/勾选态
     */
    public static Row addItem(JPopupMenu menu, String title, Runnable action) {
        Row row = new Row(menu, title, action);
        menu.add(row);
        return row;
    }

    /** 追加一条分隔线。 */
    public static void addSeparator(JPopupMenu menu) {
        menu.add(new Separator());
    }

    /**
     * 按标题设置可见性（原版 {@code MenuItem.setVisible}）。
     * 会同步触发重新布局，让隐藏项不占位。
     */
    public static void setVisible(JPopupMenu menu, String title, boolean visible) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) {
                c.setVisible(visible);
            }
        }
        menu.revalidate();
        menu.repaint();
    }

    /** 按标题设置启用态（原版 {@code MenuItem.setEnabled}）。 */
    public static void setEnabled(JPopupMenu menu, String title, boolean enabled) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) {
                ((Row) c).setEnabled(enabled);
            }
        }
    }

    /** 按标题设置勾选态（原版 {@code MenuItem.setChecked}，对应 {@code android:checkable="true"}）。 */
    public static void setChecked(JPopupMenu menu, String title, boolean checked) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) {
                ((Row) c).setChecked(checked);
            }
        }
    }

    /** 该标题的条目当前是否可见。 */
    public static boolean isVisible(JPopupMenu menu, String title) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) return c.isVisible();
        }
        return false;
    }

    /** 该标题的条目当前是否勾选。 */
    public static boolean isChecked(JPopupMenu menu, String title) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) return ((Row) c).checked;
        }
        return false;
    }

    /** 该标题的条目当前是否启用。 */
    public static boolean isEnabled(JPopupMenu menu, String title) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && ((Row) c).text.equals(title)) return ((Row) c).enabled;
        }
        return false;
    }

    /** 是否存在可见条目 —— 原版据此决定要不要画 ⋮ / 弹菜单。 */
    public static boolean hasVisibleItems(JPopupMenu menu) {
        for (Component c : menu.getComponents()) {
            if (c instanceof Row && c.isVisible()) return true;
        }
        return false;
    }

    /** 菜单项数量（不含分隔线）。 */
    public static int itemCount(JPopupMenu menu) {
        int n = 0;
        for (Component c : menu.getComponents()) {
            if (c instanceof Row) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ 菜单行

    /** 一行菜单项。原版是 {@code list_selector_holo_dark} 上的一个 TextView。 */
    public static class Row extends JComponent {
        final String text;
        final Runnable action;
        private final JPopupMenu owner;
        boolean enabled = true;
        boolean checked;
        private boolean hover;

        Row(JPopupMenu owner, String text, Runnable action) {
            this.owner = owner;
            this.text = text;
            this.action = action;
            setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!enabled) return;
                    if (owner != null) owner.setVisible(false);
                    if (action != null) action.run();
                }
            });
        }

        public String getText() {
            return text;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
            repaint();
        }

        public boolean isChecked() {
            return checked;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Dimension getPreferredSize() {
            // 原版 setVisible(false) 的条目在弹出菜单里不占位
            if (!isVisible()) return new Dimension(0, 0);
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text) + PADDING_LEFT * 2 + CHECK_WIDTH;
            return new Dimension(Math.max(w, MIN_WIDTH), ROW_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(hover && enabled ? HOVER : BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(enabled ? FG : DISABLED);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            if (checked) {
                // 原版 Holo 菜单勾选标记（✓）
                g2.drawString("\u2713", PADDING_LEFT, baseline);
            }
            g2.drawString(text, PADDING_LEFT + CHECK_WIDTH, baseline);
            g2.dispose();
        }
    }

    /** 一条分隔线。 */
    public static class Separator extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(MIN_WIDTH, 1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(SEPARATOR);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
