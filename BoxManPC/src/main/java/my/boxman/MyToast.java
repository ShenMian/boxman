package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * 提示条（Toast）的 PC 等价物。
 *
 * <p>原版 {@code MyToast} 包的是 {@code android.widget.Toast}：一个**浮在内容之上的**
 * 半透明圆角提示条，水平居中、靠近屏幕底部，1.5 秒（{@code LENGTH_SHORT}）或
 * 3 秒（{@code LENGTH_LONG}）后自动消失，并且**同一时刻只有一个**——
 * 连续调用时是「换文字 + 重置计时」，不是叠一堆出来。
 *
 * <p>移植约定（与 PORTING.md 5.5 的差异，见 {@code PORTING_AUDIT.md}）：
 * 计划书写的是「在主窗口底部放一个 {@code JLabel}，3 秒后清除文字」。但各窗口的
 * {@code BorderLayout.SOUTH} 已经被原版底栏占用（如游戏界面的 7 按钮工具栏），
 * 真的往里塞一个状态栏会**顶掉原版布局**，破坏「不改布局」原则。
 * 因此这里按原版语义实现为**覆盖在窗口之上的浮动条**（{@link JWindow}），
 * 同样是一个「底部居中的 JLabel」，但不参与任何容器的布局计算。
 *
 * <p>调用方传进来的 context 沿用了原版的 {@code Context} 位置，PC 侧实际是：
 * 组件/窗口（{@code this}、{@code m_Game}、{@code view}）、
 * 或者 Android 遗留的 {@code myMaps.ctxDealFile}（PC 上为 {@code null}）。
 * 因此这里对 context 做了容错：解析不出窗口时退化为「当前活动窗口」。
 */
public class MyToast {

    /** 与原版一致：短提示 */
    public static final int LENGTH_SHORT = 0;
    /** 与原版一致：长提示 */
    public static final int LENGTH_LONG = 1;

    /** 原版 {@code MyToast.showToast} 里的实际毫秒数 */
    private static final int SHORT_MS = 1500;
    private static final int LONG_MS = 3000;

    /** 距窗口底边的留白（dp）。原版 Toast 的 {@code BOTTOM} gravity 默认带一段边距 */
    private static final int BOTTOM_MARGIN = 64;
    /** 提示条文字左右内边距 */
    private static final int PAD_H = 12;
    /** 提示条文字上下内边距 */
    private static final int PAD_V = 8;
    /** 圆角半径 */
    private static final int ARC = 8;

    private static JWindow window;
    private static ToastLabel label;
    private static Timer hideTimer;
    private static Window owner;

    private MyToast() {
    }

    /**
     * 弹出一个默认的提示，在一定时间内消失。
     *
     * @param context  上下文（组件/窗口；PC 上可能是 null）
     * @param message  提示字符串
     * @param duration {@link #LENGTH_SHORT}（约 1.5 秒）或 {@link #LENGTH_LONG}（约 3 秒）
     */
    public static void showToast(Object context, String message, int duration) {
        if (message == null || message.isEmpty()) {
            return;
        }
        final int ms = duration == LENGTH_LONG ? LONG_MS : SHORT_MS;
        final Object ctx = context;

        if (SwingUtilities.isEventDispatchThread()) {
            show(ctx, message, ms);
        } else {
            SwingUtilities.invokeLater(() -> show(ctx, message, ms));
        }
    }

    private static void show(Object context, String message, int ms) {
        // 原版语义：先撤掉上一次的自动隐藏，再复用同一个 Toast（换文字而不是叠新的）
        if (hideTimer != null) {
            hideTimer.stop();
            hideTimer = null;
        }

        Window target = resolveOwner(context);
        if (target != null) {
            owner = target;
        }

        if (window == null) {
            window = new JWindow();
            window.setFocusableWindowState(false);   // 不抢焦点
            window.setAlwaysOnTop(true);
            label = new ToastLabel();
            window.setContentPane(label);
            window.setBackground(new Color(0, 0, 0, 0));
        }
        label.setText(message);
        label.setFont(TOAST_FONT);
        window.pack();

        position(target);

        if (!window.isVisible()) {
            window.setVisible(true);
        }

        hideTimer = new Timer(ms, e -> hideNow());
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    private static void hideNow() {
        if (hideTimer != null) {
            hideTimer.stop();
            hideTimer = null;
        }
        if (window != null) {
            window.setVisible(false);
        }
    }

    /** 把提示条摆到目标窗口底边居中；窗口不可用时退化为屏幕底边居中。 */
    private static void position(Window target) {
        if (window == null) return;
        Rectangle area = contentAreaOnScreen(target);
        Point p = computeToastLocation(area, window.getSize());
        window.setLocation(p.x, p.y);
    }

    /** 目标窗口内容区在屏幕上的位置；窗口不可用时返回整个屏幕。 */
    private static Rectangle contentAreaOnScreen(Window target) {
        if (target != null && target.isShowing()) {
            // 用内容区（去掉标题栏/边框）定位，避免受系统装饰影响
            Container content = target instanceof RootPaneContainer
                    ? ((RootPaneContainer) target).getContentPane() : target;
            Rectangle b = content.getBounds();
            Point p = content.getLocationOnScreen();
            return new Rectangle(p.x, p.y, b.width, b.height);
        }
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    /**
     * 计算提示条在内容区里的落点：水平居中、底边上留 {@link #BOTTOM_MARGIN}。
     * 抽出来是为了让离屏渲染/快照工具能算出与真机上完全一致的坐标。
     */
    public static Point computeToastLocation(Rectangle contentArea, Dimension toastSize) {
        int x = contentArea.x + (contentArea.width - toastSize.width) / 2;
        int y = contentArea.y + contentArea.height - toastSize.height - BOTTOM_MARGIN;
        if (y < contentArea.y) {
            y = contentArea.y;
        }
        return new Point(x, y);
    }

    /**
     * 从 context 解析出「最近的窗口」。
     * context 可能是 Component、Window、null（Android 遗留的 {@code ctxDealFile}），
     * 也可能是个跟 UI 无关的对象，所以每一层都要容错。
     */
    private static Window resolveOwner(Object context) {
        if (context instanceof Window) {
            return (Window) context;
        }
        if (context instanceof Component) {
            Window w = SwingUtilities.getWindowAncestor((Component) context);
            if (w != null) {
                return w;
            }
        }
        // 兜底：当前活动窗口 → 最后已知窗口 → 任意可见窗口
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.isActive()) {
                return w;
            }
        }
        if (owner != null && owner.isShowing()) {
            return owner;
        }
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                return w;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 测试/调试用

    /** 提示条当前是否可见。 */
    public static boolean isToastShowing() {
        return window != null && window.isVisible();
    }

    /** 提示条当前文字；未创建时为 {@code null}。 */
    public static String currentToastText() {
        return label == null ? null : label.getText();
    }

    /** 立刻收起提示条（测试收尾用）。 */
    public static void dismiss() {
        if (SwingUtilities.isEventDispatchThread()) {
            hideNow();
        } else {
            SwingUtilities.invokeLater(MyToast::hideNow);
        }
    }

    /** 提示条字体（与真机一致） */
    private static final Font TOAST_FONT = new Font("Microsoft YaHei", Font.PLAIN, 14);

    /**
     * 造一个「跟真机一样」的提示条组件并完成排版。
     * 供快照工具做<b>离屏</b>渲染用——不要为了看观感去 {@code Robot} 截真实屏幕，
     * 那会把用户桌面上的其它窗口一起拍进来。
     */
    static JComponent createToastLabel(String message) {
        ToastLabel l = new ToastLabel();
        l.setText(message);
        l.setFont(TOAST_FONT);
        l.setSize(l.getPreferredSize());
        return l;
    }

    /**
     * 提示条本体：半透明黑底 + 白字 + 圆角，对应原版 Holo Toast 的外观。
     */
    private static class ToastLabel extends JLabel {

        ToastLabel() {
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(PAD_V, PAD_H, PAD_V, PAD_H));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 40), d.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 0xCC));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * 窗口移动/缩放时把提示条跟过去（原版 Toast 跟随 Activity 窗口）。
     * 由 {@link #attachTo(Window)} 安装，避免在每个窗口里各写一遍。
     */
    public static void attachTo(Window w) {
        if (w == null) return;
        w.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                if (isToastShowing() && w == owner) {
                    position(w);
                }
            }

            @Override
            public void componentResized(ComponentEvent e) {
                if (isToastShowing() && w == owner) {
                    position(w);
                }
            }
        });
    }
}
