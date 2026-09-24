package my.boxman;

import javax.swing.*;
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
 */
public class myActionBar extends JPanel {

    /** Android ActionBar 标准高度 48dp */
    public static final int BAR_HEIGHT = 48;

    private static final Color BAR_BG = new Color(0x0083C5);          // style.xml
    private static final Color TITLE_FG = Color.WHITE;                // style.xml
    private static final Color DOT_FG = new Color(0x80C1E1);          // 白色 50% 叠加在 #0083C5 上
    private static final Color MENU_BG = new Color(0x333333);         // Holo 深色弹出菜单
    private static final Color MENU_FG = new Color(0xEEEEEE);
    private static final Color MENU_HOVER = new Color(0x0083C5);
    private static final Color MENU_DISABLED = new Color(0x777777);
    private static final Color MENU_SEPARATOR = new Color(0x555555);

    private static final int TITLE_PADDING_LEFT = 16;
    private static final int TITLE_TEXT_SIZE = 14;
    private static final int DOT_SIZE = 5;
    private static final int DOT_GAP = 8;
    private static final int MENU_TEXT_SIZE = 15;
    private static final int MENU_ROW_HEIGHT = 40;
    private static final int MENU_PADDING_LEFT = 16;
    private static final int MENU_MIN_WIDTH = 200;

    private final JLabel titleLabel = new JLabel();
    private final JPopupMenu overflowMenu = new JPopupMenu();
    private final OverflowButton overflowButton = new OverflowButton();

    public myActionBar() {
        setLayout(new BorderLayout());
        setBackground(BAR_BG);
        setOpaque(true);
        setPreferredSize(new Dimension(0, BAR_HEIGHT));
        setMinimumSize(new Dimension(0, BAR_HEIGHT));

        titleLabel.setForeground(TITLE_FG);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, TITLE_TEXT_SIZE));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, TITLE_PADDING_LEFT, 0, 0));

        overflowMenu.setBackground(MENU_BG);
        overflowMenu.setBorder(BorderFactory.createLineBorder(MENU_SEPARATOR));

        add(titleLabel, BorderLayout.CENTER);
        add(overflowButton, BorderLayout.EAST);
    }

    /** 设置标题（原版 ActionBar.setTitle） */
    public void setBarTitle(String title) {
        titleLabel.setText(title);
    }

    public String getBarTitle() {
        return titleLabel.getText();
    }

    /** 追加一个可用菜单项（原版 menu.xml 中的 <item>） */
    public void addAction(String title, Runnable action) {
        addAction(title, true, action);
    }

    /** 追加一个菜单项；enabled=false 时置灰（用于尚未移植的功能项） */
    public void addAction(String title, boolean enabled, Runnable action) {
        MenuRow row = new MenuRow(title, action);
        row.setEnabled(enabled);
        overflowMenu.add(row);
    }

    /** 追加一条分隔线 */
    public void addSeparator() {
        overflowMenu.add(new MenuSeparator());
    }

    /** 菜单项数量（不含分隔线） */
    public int getActionCount() {
        int n = 0;
        for (Component c : overflowMenu.getComponents()) {
            if (c instanceof MenuRow) n++;
        }
        return n;
    }

    /** 以编程方式展开溢出菜单 */
    public void showOverflow() {
        if (overflowMenu.getComponentCount() == 0) return;
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

    // ------------------------------------------------------------------ 菜单行

    private class MenuRow extends JComponent {
        private final String text;
        private final Runnable action;
        private boolean enabled = true;
        private boolean hover;

        MenuRow(String text, Runnable action) {
            this.text = text;
            this.action = action;
            setFont(new Font("Microsoft YaHei", Font.PLAIN, MENU_TEXT_SIZE));
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
                    if (!MenuRow.this.enabled) return;
                    overflowMenu.setVisible(false);
                    if (MenuRow.this.action != null) MenuRow.this.action.run();
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
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text) + MENU_PADDING_LEFT * 2;
            return new Dimension(Math.max(w, MENU_MIN_WIDTH), MENU_ROW_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(hover && enabled ? MENU_HOVER : MENU_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(enabled ? MENU_FG : MENU_DISABLED);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, MENU_PADDING_LEFT, baseline);
            g2.dispose();
        }
    }

    private static class MenuSeparator extends JComponent {
        MenuSeparator() {
            setBackground(MENU_SEPARATOR);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(MENU_MIN_WIDTH, 1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(MENU_SEPARATOR);
            g.fillRect(0, 0, getWidth(), getHeight());
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
            if (c instanceof MenuRow) titles.add(((MenuRow) c).text);
        }
        return titles;
    }
}
