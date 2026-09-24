package my.boxman;

import my.boxman.compat.HoloPopupMenu;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Android Holo ActionBar 的 PC 等价实现，用于各窗口顶部的标题栏。
 *
 * 与原版资源的对应关系：
 *   res/values/style.xml  →  my_actionbar_style：android:background="#0083C5"
 *                            AcBar_titleStyle：  android:textColor="#FFFFFF"
 *   res/menu/*.xml        →  右侧溢出按钮（⋮）中承载的菜单项
 *
 * 视觉参数取自原版截图实测（截图 1260x2844，density≈3.4）：
 *   标题栏高 161px ≈ 48dp；标题左内边距 55px ≈ 16dp；
 *   标题字高 44px（列表 16sp 字高 52px）→ 标题约 14sp；
 *   溢出按钮为三个圆点，直径 17px ≈ 5dp，间距 26px ≈ 8dp，
 *   颜色 (128,193,225) 即白色 50% 叠加在 #0083C5 上。
 *
 * 第二屏（关卡网格 myGridView）追加实测（同一张截图）：
 *   ActionBar 左侧「返回」折角：ink 22x46px ≈ 6.5x13.5dp，中心 x=25px ≈ 7.4dp，
 *     颜色同为 (128,193,225)；
 *   ActionBar 标题左边界 x=58px ≈ 17dp（与无返回键时的 16dp 基本一致）；
 *   showAsAction="always" 的动作项（顶/底）为纯文字按钮，宽 56dp，
 *     字形 ink 高 37~39px ≈ 0.8em → 14sp 粗体，颜色实测平台值 (243,243,243)。
 */
public class myActionBar extends JPanel {

    /** Android ActionBar 标准高度 48dp */
    public static final int BAR_HEIGHT = 48;

    private static final Color BAR_BG = new Color(0x0083C5);          // style.xml
    private static final Color TITLE_FG = Color.WHITE;                // style.xml
    private static final Color DOT_FG = new Color(0x80C1E1);          // 白色 50% 叠加在 #0083C5 上

    private static final int TITLE_PADDING_LEFT = 16;
    private static final int TITLE_TEXT_SIZE = 14;
    private static final int DOT_SIZE = 5;
    private static final int DOT_GAP = 8;

    /** 原版 action_button_min_width */
    private static final int BAR_ACTION_WIDTH = 56;
    private static final int BAR_ACTION_TEXT_SIZE = 14;
    private static final Color BAR_ACTION_FG = new Color(0xF3F3F3);
    private static final Color BAR_ACTION_DISABLED = new Color(0x9FC7DE);
    private static final Color BAR_ACTION_HOVER = new Color(255, 255, 255, 40);

    /** 原版 setDisplayHomeAsUpEnabled(true) 的折角宽度 */
    private static final int UP_WIDTH = 17;
    private static final int UP_INK_WIDTH = 7;
    private static final int UP_INK_HEIGHT = 14;

    /**
     * 右侧留白与动作项/溢出按钮间距。
     * 实测（关卡网格界面截图，1dp=1px）：溢出圆点中心 x≈342，按钮宽 48 → 右边距 4；
     * 「底」中心 x≈285 → 按钮右边界 313，与溢出按钮左边界 318 之间留 6。
     */
    private static final int OVERFLOW_RIGHT_INSET = 4;
    private static final int BAR_ACTION_GAP = 6;

    private final JLabel titleLabel = new JLabel();
    private final JPopupMenu overflowMenu = HoloPopupMenu.create();
    private final OverflowButton overflowButton = new OverflowButton();
    private final JPanel barActionStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final UpIndicator upIndicator = new UpIndicator();
    private final JPanel eastPanel;
    private final JPanel overflowWrap;

    /**
     * 原版 ActionBar 只在溢出菜单**有内容**时才画 ⋮。
     * 实测「提交列表」截图（`menu/submit_list.xml` 只有一条 `showAsAction="always"` 的「刷新」）：
     * ActionBar 右侧只有一个动作项，ink 中心 x≈341.9dp，按钮 56dp 且**右边距为 0**（314..370）。
     * 而关卡网格界面有溢出项时，⋮ 是 48dp 且右边距 4dp。所以边距随 ⋮ 的可见性切换。
     */
    private void refreshOverflow() {
        boolean show = HoloPopupMenu.hasVisibleItems(overflowMenu);
        overflowButton.setVisible(show);
        overflowWrap.setBorder(new EmptyBorder(0, show ? BAR_ACTION_GAP : 0, 0, 0));
        eastPanel.setBorder(new EmptyBorder(0, 0, 0, show ? OVERFLOW_RIGHT_INSET : 0));
        revalidate();
        repaint();
    }

    public myActionBar() {
        setLayout(new BorderLayout());
        setBackground(BAR_BG);
        setOpaque(true);
        setPreferredSize(new Dimension(0, BAR_HEIGHT));
        setMinimumSize(new Dimension(0, BAR_HEIGHT));

        titleLabel.setForeground(TITLE_FG);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, TITLE_TEXT_SIZE));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, TITLE_PADDING_LEFT, 0, 0));

        barActionStrip.setOpaque(false);

        // EAST 侧：动作项（顶/底…）在左，溢出按钮在最右
        eastPanel = new JPanel(new BorderLayout());
        eastPanel.setOpaque(false);
        eastPanel.add(barActionStrip, BorderLayout.CENTER);

        overflowWrap = new JPanel(new BorderLayout());
        overflowWrap.setOpaque(false);
        overflowWrap.add(overflowButton, BorderLayout.CENTER);
        eastPanel.add(overflowWrap, BorderLayout.EAST);

        add(titleLabel, BorderLayout.CENTER);
        add(eastPanel, BorderLayout.EAST);
        refreshOverflow();
        // WEST 侧：返回折角（默认隐藏，原版由 setDisplayHomeAsUpEnabled 控制）
        upIndicator.setVisible(false);
        add(upIndicator, BorderLayout.WEST);
    }

    /** 设置标题（原版 ActionBar.setTitle） */
    public void setBarTitle(String title) {
        titleLabel.setText(title);
    }

    public String getBarTitle() {
        return titleLabel.getText();
    }

    /**
     * 原版 ActionBar.setDisplayHomeAsUpEnabled(true)：显示左侧返回折角。
     * 折角占用 17dp，此时标题左内边距归零，保证标题绝对左边界仍在 17dp。
     */
    public void setUpEnabled(boolean enabled, Runnable action) {
        upIndicator.setAction(enabled ? action : null);
        upIndicator.setVisible(enabled);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, enabled ? 0 : TITLE_PADDING_LEFT, 0, 0));
        revalidate();
        repaint();
    }

    /** 原版 {@code ActionBar.setDisplayHomeAsUpEnabled} 的状态查询（快照/测试用）。 */
    public boolean isUpEnabled() {
        return upIndicator.isVisible();
    }

    /** 追加一个可用菜单项（原版 menu.xml 中的 <item>） */
    public void addAction(String title, Runnable action) {
        addAction(title, true, action);
    }

    /** 追加一个菜单项；enabled=false 时置灰（用于尚未移植的功能项） */
    public void addAction(String title, boolean enabled, Runnable action) {
        HoloPopupMenu.Row row = HoloPopupMenu.addItem(overflowMenu, title, action);
        row.setEnabled(enabled);
        refreshOverflow();
    }

    /** 追加一个 showAsAction="always" 的动作项（原版 ActionBar 上的纯文字按钮） */
    public void addBarAction(String title, Runnable action) {
        addBarAction(title, true, action);
    }

    public void addBarAction(String title, boolean enabled, Runnable action) {
        BarAction btn = new BarAction(title, action);
        btn.setEnabled(enabled);
        barActionStrip.add(btn);
    }

    /** 按标题显示/隐藏溢出菜单项（原版 setMenu 中的 setVisible） */
    public void setActionVisible(String title, boolean visible) {
        HoloPopupMenu.setVisible(overflowMenu, title, visible);
        refreshOverflow();
    }

    /** 按标题显示/隐藏 ActionBar 上的动作项 */
    public void setBarActionVisible(String title, boolean visible) {
        for (Component c : barActionStrip.getComponents()) {
            if (c instanceof BarAction && ((BarAction) c).text.equals(title)) {
                c.setVisible(visible);
            }
        }
        barActionStrip.revalidate();
        barActionStrip.repaint();
    }

    /** 按标题设置菜单项的勾选态（原版 android:checkable="true"） */
    public void setActionChecked(String title, boolean checked) {
        HoloPopupMenu.setChecked(overflowMenu, title, checked);
    }

    /** 该溢出菜单项当前是否可见（供自检/测试） */
    public boolean isActionVisible(String title) {
        return HoloPopupMenu.isVisible(overflowMenu, title);
    }

    /** 该溢出菜单项是否处于勾选态（供自检/测试） */
    public boolean isActionChecked(String title) {
        return HoloPopupMenu.isChecked(overflowMenu, title);
    }

    /** 该 ActionBar 动作项当前是否可见（供自检/测试） */
    public boolean isBarActionVisible(String title) {
        for (Component c : barActionStrip.getComponents()) {
            if (c instanceof BarAction && ((BarAction) c).text.equals(title)) return c.isVisible();
        }
        return false;
    }

    /** ActionBar 上动作项的数量（含隐藏项） */
    public int getBarActionCount() {
        return barActionStrip.getComponentCount();
    }

    /**
     * 溢出按钮（⋮）当前是否可见。原版只在溢出菜单有内容时才画它
     * —— 例如「提交列表」的 {@code menu/submit_list.xml} 只有一条 {@code showAsAction="always"}，
     * 所以右侧只有一个动作项，没有 ⋮。
     */
    public boolean isOverflowVisible() {
        return overflowButton.isVisible();
    }

    /** 追加一条分隔线 */
    public void addSeparator() {
        HoloPopupMenu.addSeparator(overflowMenu);
        refreshOverflow();
    }

    /** 菜单项数量（不含分隔线） */
    public int getActionCount() {
        return HoloPopupMenu.itemCount(overflowMenu);
    }

    /** 以编程方式展开溢出菜单 */
    public void showOverflow() {
        if (!HoloPopupMenu.hasVisibleItems(overflowMenu)) return;
        if (!overflowButton.isShowing()) return;   // JPopupMenu 要求 invoker 已显示
        int x = overflowButton.getWidth() - overflowMenu.getPreferredSize().width;
        if (x < 0) x = 0;
        overflowMenu.show(overflowButton, x, overflowButton.getHeight());
    }

    // ------------------------------------------------------------------ 溢出按钮

    private class OverflowButton extends JComponent {
        private boolean hover;

        OverflowButton() {
            setPreferredSize(new Dimension(BAR_HEIGHT, BAR_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("更多选项");
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
                    showOverflow();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(DOT_FG);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            for (int i = -1; i <= 1; i++) {
                g2.fillOval(cx - DOT_SIZE / 2, cy + i * DOT_GAP - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ 返回折角

    /**
     * 原版 ic_ab_back_holo_light：ActionBar 左侧的「<」折角。
     * 实测 ink 22x46px ≈ 6.5x13.5dp，中心 x=25px ≈ 7.4dp。
     */
    private class UpIndicator extends JComponent {
        private Runnable action;
        private boolean hover;

        UpIndicator() {
            setPreferredSize(new Dimension(UP_WIDTH, BAR_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("返回");
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
                    if (action != null) action.run();
                }
            });
        }

        void setAction(Runnable action) {
            this.action = action;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(DOT_FG);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int half = UP_INK_WIDTH / 2;
            int halfH = UP_INK_HEIGHT / 2;
            g2.drawLine(cx + half, cy - halfH, cx - half, cy);
            g2.drawLine(cx - half, cy, cx + half, cy + halfH);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ ActionBar 动作项

    /** 原版 showAsAction="always"：ActionBar 上的纯文字按钮，宽 action_button_min_width=56dp */
    private class BarAction extends JComponent {
        private final String text;
        private final Runnable action;
        private boolean enabled = true;
        private boolean hover;

        BarAction(String text, Runnable action) {
            this.text = text;
            this.action = action;
            setFont(new Font("Microsoft YaHei", Font.BOLD, BAR_ACTION_TEXT_SIZE));
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
                    if (enabled && BarAction.this.action != null) BarAction.this.action.run();
                }
            });
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
            return new Dimension(BAR_ACTION_WIDTH, BAR_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (hover && enabled) {
                g2.setColor(BAR_ACTION_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(enabled ? BAR_ACTION_FG : BAR_ACTION_DISABLED);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, (getWidth() - tw) / 2, baseline);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ 静态工具

    /** 生成一个「组别展开指示器」图标（原版 expander 图标：粗折线 ^ / v） */
    public static Icon createIndicator(boolean expanded, Color color, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
        int cx = width / 2;
        if (expanded) {
            g2.drawLine(1, height - 1, cx, 1);
            g2.drawLine(cx, 1, width - 1, height - 1);
        } else {
            g2.drawLine(1, 0, cx, height - 2);
            g2.drawLine(cx, height - 2, width - 1, 0);
        }
        g2.dispose();
        return new ImageIcon(img);
    }

    /** 供外部复用的空实现，避免调用方判空 */
    public static final Runnable NO_OP = new Runnable() {
        @Override
        public void run() {
        }
    };

    /** 收集菜单项标题，便于测试与自检 */
    public List<String> getActionTitles() {
        List<String> titles = new ArrayList<String>();
        for (Component c : overflowMenu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) titles.add(((HoloPopupMenu.Row) c).getText());
        }
        return titles;
    }

    /** 暴露底层弹出菜单，便于需要同样 Holo 样式的上下文菜单复用同一套渲染 */
    public JPopupMenu getOverflowMenu() {
        return overflowMenu;
    }
}
