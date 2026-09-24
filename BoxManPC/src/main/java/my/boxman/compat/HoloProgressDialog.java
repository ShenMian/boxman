package my.boxman.compat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 原版 {@code android.app.ProgressDialog}（{@code STYLE_SPINNER}）的等价物。
 *
 * <p>原版 {@code ProgressDialog} 本身就是一个 {@code AlertDialog}：{@code onCreate()} 里
 * {@code inflate(layout/progress_dialog.xml)} 后当作 {@code setView()} 的自定义视图，
 * 因此<b>外壳与 {@link HoloAlertDialog} 完全相同</b>，只是没有标题（{@code topPanel} 隐藏）
 * 也没有按钮（{@code buttonPanel} 隐藏）。
 *
 * <p>{@code layout/progress_dialog.xml} 的几何（1dp = 1px）：
 * 外层 {@code LinearLayout} 水平方向，{@code paddingStart/End = 8dp}、
 * {@code paddingTop/Bottom = 10dp}；{@code ProgressBar} 之后 {@code layout_marginEnd = 12dp}；
 * 消息 {@code TextView} 竖直居中。
 */
public class HoloProgressDialog extends HoloAlertDialog {

    /** {@code progress_dialog.xml} 的 {@code paddingStart/End} */
    private static final int BODY_PAD_H = 8;
    /** {@code progress_dialog.xml} 的 {@code paddingTop/Bottom} */
    private static final int BODY_PAD_V = 10;
    /** {@code ProgressBar} 的 {@code layout_marginEnd} */
    private static final int SPINNER_MARGIN = 12;
    /** Holo 大号不确定进度圈 {@code progress_large_holo} 的绘制尺寸 */
    private static final int SPINNER_SIZE = 32;

    private final JLabel message;
    private final Spinner spinner;
    private Runnable onCancel;

    public HoloProgressDialog(Frame owner, String text) {
        super(owner, "");

        spinner = new Spinner();

        message = new JLabel(text == null ? "" : text);
        message.setFont(HoloContent.font(Font.PLAIN));
        message.setForeground(HoloContent.TEXT);
        message.setVerticalAlignment(SwingConstants.CENTER);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.X_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(BODY_PAD_V, BODY_PAD_H, BODY_PAD_V, BODY_PAD_H));
        body.add(spinner);
        body.add(Box.createHorizontalStrut(SPINNER_MARGIN));
        body.add(message);

        setContentView(body);
    }

    /** 更新进度文字（原版 {@code ProgressDialog.setMessage()}）。 */
    public void setMessage(String text) {
        message.setText(text == null ? "" : text);
        message.revalidate();
    }

    /** 取消回调（原版 {@code onCancel()} → {@code mFindTask.stopFind()}）。 */
    public void setOnCancel(Runnable action) {
        this.onCancel = action;
    }

    /** 用户点了取消（原版 {@code setCancelable(true)}）。 */
    public void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    @Override
    public void dispose() {
        spinner.stop();
        super.dispose();
    }

    @Override
    public void setVisible(boolean visible) {
        // 注意：模态对话框的 super.setVisible(true) 会一直阻塞到 dispose，
        // 所以动画必须在显示之前启动。
        if (visible) {
            spinner.start();
        }
        super.setVisible(visible);
        if (!visible) {
            spinner.stop();
        }
    }

    /** Holo 不确定进度圈：12 段圆点绕圈，亮度随时间循环（{@code progress_large_holo} 的等价绘制）。 */
    private static final class Spinner extends JComponent {
        private static final int SEGMENTS = 12;
        private final Timer timer;
        private int phase;

        Spinner() {
            Dimension d = new Dimension(SPINNER_SIZE, SPINNER_SIZE);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setAlignmentY(Component.CENTER_ALIGNMENT);
            setOpaque(false);
            timer = new Timer(80, e -> {
                phase = (phase + 1) % SEGMENTS;
                repaint();
            });
        }

        void start() {
            if (!timer.isRunning()) {
                timer.start();
            }
        }

        void stop() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            double radius = size * 0.34;
            double dot = Math.max(2.0, size * 0.105);
            for (int i = 0; i < SEGMENTS; i++) {
                double angle = 2 * Math.PI * i / SEGMENTS - Math.PI / 2;
                int dist = (i - phase + SEGMENTS) % SEGMENTS;
                int alpha = 40 + 215 * (SEGMENTS - 1 - dist) / (SEGMENTS - 1);
                g2.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
                double x = cx + radius * Math.cos(angle) - dot / 2;
                double y = cy + radius * Math.sin(angle) - dot / 2;
                g2.fill(new java.awt.geom.Ellipse2D.Double(x, y, dot, dot));
            }
            g2.dispose();
        }
    }
}
