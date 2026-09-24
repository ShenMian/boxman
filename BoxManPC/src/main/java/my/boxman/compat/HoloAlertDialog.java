package my.boxman.compat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 原版 Android 的 {@code AlertDialog}（{@code Theme.Holo} 深色主题）等价物。
 *
 * <p>原版全部对话框都是 {@code AlertDialog.Builder(context, THEME_HOLO_DARK).setView(自定义布局)}，
 * 外壳统一是框架的 {@code alert_dialog_holo.xml}。PC 端原来用的是一批「浅色 Swing 对话框 +
 * 各自 setSize(340~540)」，既不是 Holo 外观，宽度还超过了 370dp 的手机屏。本类按
 * Android framework 资源 1:1 复刻那个外壳（**1dp = 1px**）。
 *
 * <h3>尺寸来源（全部取自 Android SDK 的 framework 资源，非臆测）</h3>
 * <ul>
 *   <li>窗口宽度：竖屏取 <b>95%</b> —— {@code values/dimens.xml}
 *       {@code dialog_min_width_minor = 95%}，由 {@code Theme.Holo.Dialog.BaseAlert} 的
 *       {@code windowMinWidthMinor} 引用（注释原文：the screen is portrait）。
 *       370dp 屏 → 352dp。</li>
 *   <li>9-patch {@code drawable-xhdpi/dialog_full_holo_dark.9.png}（194×82，density 2.0）实测：
 *       填充 {@code #FF282828}、圆角 ≈2dp、顶边 1px 高光 {@code #FF4B4B4B}；
 *       <b>内容内边距 16px = 8dp</b>（bottom/right 标记 runs 17..176 / 17..64），
 *       填充起点在 18px = 9dp，即投影带占外侧 9dp。</li>
 *   <li>{@code DecorView.updateColorViews()} 会把 windowBackground 的 padding 作为
 *       DecorView 的 padding（{@code DecorView.java} 实测），因此
 *       <b>9-patch 的 8dp 与 {@code parentPanel} 的 8dp 外边距会叠加</b>
 *       → 内容区相对窗口左右各内缩 <b>16dp</b>。</li>
 *   <li>{@code layout/alert_dialog_holo.xml}：{@code parentPanel} 左右外边距 8dp、
 *       {@code title_template} minHeight 64dp（{@code alert_dialog_title_height}）
 *       且左右内边距 16dp、{@code titleDivider} 高 2dp、
 *       {@code contentPanel}/{@code customPanel} minHeight 64dp、
 *       {@code buttonPanel} minHeight 48dp（{@code alert_dialog_button_bar_height}）。</li>
 *   <li>标题：{@code DialogWindowTitle.Holo → TextAppearance.Holo.DialogWindowTitle}
 *       = 22sp + {@code @color/holo_blue_light} {@code #ff33b5e5}。</li>
 *   <li>标题下 2dp 蓝线：{@code AlertController} 在有标题且有自定义面板时把
 *       {@code titleDivider} 置为 VISIBLE。</li>
 *   <li>按钮栏上方 1px 分隔线：{@code dividerHorizontal = ?attr/listDivider =
 *       drawable-xhdpi/list_divider_holo_dark.9.png}，实测内部 2×2 像素均为
 *       {@code (255,255,255,38)} = {@code #26FFFFFF}。</li>
 *   <li>按钮：{@code buttonBarStyle = Holo.ButtonBar.AlertDialog}（background 为 null、
 *       dividerPadding 0dp）+ {@code buttonBarButtonStyle = ?attr/borderlessButtonStyle =
 *       Widget.Holo.Button.Borderless}（左右 padding 4dp，继承
 *       {@code Widget.Holo.Button} 的 minHeight 48dip / minWidth 64dip），
 *       布局里每个按钮显式 {@code android:textSize="14sp"}、{@code layout_weight="1"}，
 *       文字色为 {@code primary_text_holo_dark}（白）。</li>
 * </ul>
 *
 * <h3>按钮栏几何（照 {@code LinearLayout.measureHorizontal} 的算法复刻）</h3>
 * <p>按钮 {@code layout_width="wrap_content"} + {@code layout_weight="1"}，在宽度已确定的
 * {@code buttonPanel} 里，Android 给每个按钮的宽度是
 * {@code 自然宽度 + share}，{@code share} 由剩余空间按权重均分并逐个子项递减。
 * 结果：<b>按钮等分铺满整条按钮栏</b>（两个按钮各占一半），而不是靠右挤在一起。
 * {@code layout_gravity="start/center_horizontal/end"} 在水平 {@code LinearLayout} 里
 * 只取竖直分量（{@code layoutHorizontal} 用 {@code Gravity.VERTICAL_GRAVITY_MASK} 取掩码），
 * 水平分量是**无效**的，所以不必按它去靠左/靠右摆。
 */
public class HoloAlertDialog extends JDialog {

    // ---------------------------------------------------------------- 尺寸常量（1dp = 1px）

    /** {@code dialog_min_width_minor = 95%}：竖屏下对话框窗口至少占屏宽的 95% */
    private static final int WIDTH_PERCENT = 95;

    /** 9-patch 声明的内容内边距（实测 16px @ density 2.0） */
    private static final int PATCH_PADDING = 8;
    /** 9-patch 外侧的非拉伸投影带（填充起点实测 18px @ density 2.0） */
    private static final int SHADOW = 9;
    /** 面板圆角（9-patch 实测） */
    private static final int RADIUS = 2;

    /** {@code parentPanel} 的 layout_marginStart/End */
    private static final int PARENT_MARGIN = 8;
    /** {@code alert_dialog_title_height} */
    private static final int TITLE_HEIGHT = 64;
    /** {@code title_template} 的 layout_marginStart/End */
    private static final int TITLE_PADDING = 16;
    /** {@code titleDivider} 的 layout_height */
    private static final int DIVIDER_HEIGHT = 2;
    /** {@code contentPanel} / {@code customPanel} 的 minHeight */
    private static final int CONTENT_MIN_HEIGHT = 64;
    /** {@code alert_dialog_button_bar_height} */
    private static final int BUTTON_BAR_HEIGHT = 48;
    /** {@code Widget.Holo.Button} 的 minWidth */
    private static final int BUTTON_MIN_WIDTH = 64;
    /** {@code Widget.Holo.Button.Borderless} 的 paddingStart/End */
    private static final int BUTTON_PADDING = 4;

    /** {@code TextAppearance.Holo.DialogWindowTitle} */
    private static final int TITLE_TEXT_SIZE = 22;
    /** 布局里按钮显式指定的 textSize */
    private static final int BUTTON_TEXT_SIZE = 14;

    /** 内容区相对窗口左右各内缩的量 = 9-patch padding + {@code parentPanel} 外边距 */
    public static final int CONTENT_INSET = PATCH_PADDING + PARENT_MARGIN;

    // ---------------------------------------------------------------- 颜色常量

    /** {@code dialog_full_holo_dark.9.png} 实测填充色 */
    public static final Color PANEL_BG = new Color(0x28, 0x28, 0x28);
    /** 面板顶边的 1px 高光（9-patch 实测 (75,75,75)） */
    private static final Color PANEL_TOP_EDGE = new Color(0x4B, 0x4B, 0x4B);
    /** {@code color/holo_blue_light} */
    public static final Color HOLO_BLUE_LIGHT = new Color(0x33, 0xB5, 0xE5);
    /** {@code color/primary_text_holo_dark} */
    public static final Color BUTTON_TEXT = Color.WHITE;
    /** {@code list_divider_holo_dark} 实测：白 15% */
    private static final Color BAR_DIVIDER = new Color(255, 255, 255, 38);
    /** 拿不到逐像素透明窗口时的退路底色（近似 Activity 被 dim 之后的观感） */
    private static final Color FALLBACK_BACKDROP = new Color(0x14, 0x14, 0x14);

    // ---------------------------------------------------------------- 组件

    /** 放自定义内容（对应 {@code customPanel}）的容器，子类往这里塞视图 */
    protected final JPanel contentPanel;
    /** 对应 {@code parentPanel}：再左右各内缩 8dp */
    private final JPanel parentPanel;
    private final JPanel buttonBar;
    private final String dialogTitle;

    private JButton defaultButton;
    private boolean sized;

    protected HoloAlertDialog(Frame owner, String title) {
        super(owner, true);
        this.dialogTitle = title == null ? "" : title;

        // 原版 AlertDialog 没有系统标题栏，标题画在面板内部
        setUndecorated(true);
        applyTranslucentBackground();

        contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BorderLayout());

        buttonBar = new JPanel();
        buttonBar.setOpaque(false);
        buttonBar.setLayout(new BoxLayout(buttonBar, BoxLayout.X_AXIS));
        // buttonPanel 的 showDividers="beginning"，dividerPadding 0dp → 顶边通栏 1px
        buttonBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BAR_DIVIDER));
        buttonBar.setMinimumSize(new Dimension(0, BUTTON_BAR_HEIGHT));
        buttonBar.setPreferredSize(new Dimension(0, BUTTON_BAR_HEIGHT));
        buttonBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_BAR_HEIGHT));

        parentPanel = new JPanel();
        parentPanel.setOpaque(false);
        parentPanel.setLayout(new BorderLayout());
        parentPanel.setBorder(new EmptyBorder(0, PARENT_MARGIN, 0, PARENT_MARGIN));
        if (!dialogTitle.isEmpty()) {
            parentPanel.add(buildTopPanel(), BorderLayout.NORTH);
        }
        parentPanel.add(contentPanel, BorderLayout.CENTER);
        parentPanel.add(buttonBar, BorderLayout.SOUTH);

        // DecorView 会把 windowBackground 的 padding 作为自身 padding
        JPanel root = new PanelBackground();
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(PATCH_PADDING, PATCH_PADDING, PATCH_PADDING, PATCH_PADDING));
        root.add(parentPanel, BorderLayout.CENTER);
        setContentPane(root);

        installEscapeToClose();
    }

    /** 逐像素透明窗口才能让 9dp 软投影与背后内容混合；不支持时退化为不透明底色。 */
    private void applyTranslucentBackground() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            if (device.isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                setBackground(new Color(0, 0, 0, 0));
                return;
            }
        } catch (Throwable ignored) {
            // 无图形环境 / 不支持 → 走退路
        }
        setBackground(FALLBACK_BACKDROP);
    }

    /** {@code topPanel}：{@code title_template}（minHeight 64dp）+ {@code titleDivider}（2dp 蓝线）。 */
    private JComponent buildTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(dialogTitle);
        title.setFont(new Font("Microsoft YaHei", Font.PLAIN, TITLE_TEXT_SIZE));
        title.setForeground(HOLO_BLUE_LIGHT);
        title.setBorder(new EmptyBorder(0, TITLE_PADDING, 0, TITLE_PADDING));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMinimumSize(new Dimension(0, TITLE_HEIGHT));
        title.setPreferredSize(new Dimension(0, TITLE_HEIGHT));
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, TITLE_HEIGHT));
        topPanel.add(title);

        JPanel divider = new JPanel();
        divider.setBackground(HOLO_BLUE_LIGHT);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setPreferredSize(new Dimension(0, DIVIDER_HEIGHT));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, DIVIDER_HEIGHT));
        topPanel.add(divider);
        return topPanel;
    }

    /** PC 上对话框没有系统标题栏，留一个 ESC 退出口，避免无按钮对话框把用户困住 */
    private void installEscapeToClose() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    /** 放入自定义内容（等价于原版的 {@code AlertDialog.setView()}）。 */
    public void setContentView(JComponent view) {
        contentPanel.removeAll();
        if (view != null) {
            view.setOpaque(false);
            contentPanel.add(view, BorderLayout.CENTER);
        }
        contentPanel.setPreferredSize(null);
        // customPanel 的 minHeight
        if (contentPanel.getPreferredSize().height < CONTENT_MIN_HEIGHT) {
            contentPanel.setPreferredSize(new Dimension(0, CONTENT_MIN_HEIGHT));
        }
    }

    /**
     * 追加一个按钮。原版按钮栏顺序是 {@code button2 / button3 / button1}（{@code button1} 在最右），
     * 每个按钮 {@code layout_weight="1"}，因此按钮**等分铺满**整条按钮栏。
     *
     * @param text   按钮文字
     * @param action 点击后的动作（可为 null）
     */
    public JButton addButton(String text, final Runnable action) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                if (getModel().isRollover() || getModel().isPressed()) {
                    // ?attr/selectableItemBackground（Holo 深色）的等价观感
                    g2.setColor(new Color(255, 255, 255, getModel().isPressed() ? 40 : 24));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setForeground(BUTTON_TEXT);
        b.setFont(new Font("Microsoft YaHei", Font.PLAIN, BUTTON_TEXT_SIZE));
        b.setBorder(new EmptyBorder(0, BUTTON_PADDING, 0, BUTTON_PADDING));
        b.setMinimumSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_BAR_HEIGHT));
        b.setPreferredSize(new Dimension(BUTTON_MIN_WIDTH, BUTTON_BAR_HEIGHT));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_BAR_HEIGHT));
        b.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        buttonBar.add(b);
        return b;
    }

    /**
     * 指定默认按钮。原版 {@code AlertDialog} 会 {@code requestFocusForDefaultButton()}，
     * positive 按钮（布局里的 {@code button1}，位于最右）是默认按钮，这里由调用方显式指定。
     */
    public void setDefaultButton(JButton button) {
        defaultButton = button;
        getRootPane().setDefaultButton(button);
    }

    /** 竖屏下对话框窗口宽度 = 屏宽 × 95%（{@code dialog_min_width_minor}）。 */
    private static int windowWidth() {
        return Math.round(UiWindow.PHONE_WIDTH * WIDTH_PERCENT / 100f);
    }

    /** 按钮栏可用宽度 = 窗口宽度 − 两侧内容内缩。 */
    private static int buttonBarWidth() {
        return windowWidth() - 2 * CONTENT_INSET;
    }

    /**
     * 按 {@code LinearLayout.measureHorizontal} 的权重算法给按钮定宽：
     * {@code 自然宽度 + share}，{@code share} 把剩余空间按权重均分并逐个子项递减。
     * 结果就是按钮等分铺满按钮栏（文字长短略有差异时按自然宽度做补偿）。
     */
    private void distributeButtonWidths() {
        java.util.List<JButton> buttons = new java.util.ArrayList<>();
        int naturalSum = 0;
        for (Component c : buttonBar.getComponents()) {
            if (c instanceof JButton) {
                JButton b = (JButton) c;
                buttons.add(b);
                naturalSum += Math.max(BUTTON_MIN_WIDTH, b.getPreferredSize().width);
            }
        }
        int n = buttons.size();
        if (n == 0) {
            return;
        }

        int barWidth = buttonBarWidth();
        int remainingExcess = barWidth - naturalSum;
        int remainingWeight = n;
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            JButton b = buttons.get(i);
            int natural = Math.max(BUTTON_MIN_WIDTH, b.getPreferredSize().width);
            int share = remainingWeight > 0
                    ? (int) (1f * remainingExcess / remainingWeight) : 0;
            remainingExcess -= share;
            remainingWeight -= 1;
            int w = Math.max(0, natural + share);
            if (i == n - 1) {
                // 整数截断的零头归最后一个按钮，保证铺满
                w = Math.max(0, barWidth - assigned);
            }
            assigned += w;
            b.setPreferredSize(new Dimension(w, BUTTON_BAR_HEIGHT));
            b.setMaximumSize(new Dimension(w, BUTTON_BAR_HEIGHT));
        }
    }

    /**
     * 显示前按原版语义定尺寸：宽度固定为屏宽的 95%，高度 wrap_content（但不超过手机内容区高度）。
     *
     * <p>覆写 {@code setVisible} 是为了让现有的 {@code new XxxDialog(...).setVisible(true)} 调用点
     * 不用改动 —— 尺寸在真正显示的那一刻才确定，此时内容的首选尺寸已经算好了。
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible && !sized) {
            applyHoloSize();
            sized = true;
        }
        super.setVisible(visible);
    }

    /** 供调用方在 {@code setVisible} 之外主动定尺寸（例如先 pack 再量）。 */
    public void applyHoloSize() {
        distributeButtonWidths();
        pack();
        int w = windowWidth();
        // 高度 wrap_content，但不能超出手机内容区（原版对话框同样受屏幕高度约束）
        int maxH = UiWindow.PHONE_HEIGHT - 2 * SHADOW;
        int h = Math.min(getHeight(), maxH);
        setSize(w, h);
        setLocationRelativeTo(getOwner());
    }

    /** 面板背景：{@code dialog_full_holo_dark} 9-patch 的等价绘制。 */
    private static class PanelBackground extends JPanel {
        PanelBackground() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // 软投影：由外向内逐圈加深（9-patch 里是黑色 alpha 0→87 的渐变）
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i < SHADOW; i++) {
                int alpha = Math.round(87f * i / (SHADOW - 1));
                g2.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, alpha))));
                g2.drawRoundRect(i, i, w - 1 - 2 * i, h - 1 - 2 * i, RADIUS * 2, RADIUS * 2);
            }

            // 面板本体
            g2.setColor(PANEL_BG);
            g2.fillRoundRect(SHADOW, SHADOW, w - 2 * SHADOW, h - 2 * SHADOW,
                    RADIUS * 2, RADIUS * 2);

            // 顶边 1px 高光
            g2.setColor(PANEL_TOP_EDGE);
            g2.drawLine(SHADOW + RADIUS, SHADOW, w - SHADOW - RADIUS - 1, SHADOW);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
