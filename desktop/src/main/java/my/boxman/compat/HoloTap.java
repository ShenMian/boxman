package my.boxman.compat;

import java.awt.Toolkit;
import java.awt.event.MouseEvent;

/**
 * 双击判定 —— 原版 {@code GestureDetector.onDoubleTap} 的等价物。
 *
 * <h2>为什么不能用 {@code getClickCount() == 2}</h2>
 *
 * <p>{@code getClickCount()} 只在 {@code MOUSE_CLICKED} 上才有意义，而
 * {@code MOUSE_CLICKED} <b>不是</b>原始事件：它是 Windows 原生层在
 * {@code AwtComponent::WmMouseUp} 里派生出来的，受一个<b>无阈值</b>的开关控制 ——
 * 按住期间只要来了<b>任何一次</b> {@code WM_MOUSEMOVE}（哪怕 1 像素），
 * {@code m_mouseButtonClickAllowed} 就被清零，抬起时不再补发 CLICKED
 * （完整推导见 {@link HoloClick} 的类注释）。
 *
 * <p>后果对单击是「有时点一次没反应」，对双击更糟：两击里只要<b>有一击</b>被吞掉，
 * {@code getClickCount()} 就永远到不了 2，<b>双击功能彻底失效</b>
 * （{@code myEditViewMap} 的「双击边界缩放」、{@code myRecogViewMap} /
 * {@code myFindViewMap} 的「双击切换全貌」都是这一类）。
 *
 * <h2>做法</h2>
 *
 * <p>改成在 {@code mouseReleased} 上自己数：两次抬起的时间差不超过系统双击间隔
 * （{@code awt.multiClickInterval}，退化值 500ms），且位移不超过 {@link #SLOP}。
 * 这两条正好对应 Android {@code GestureDetector} 判双击用的
 * {@code DOUBLE_TAP_TIMEOUT} 与 {@code doubleTapSlop}。
 *
 * <p>用法（把 {@code mouseClicked} 里那段双击分支搬进 {@code mouseReleased}）：
 * <pre>
 * private final HoloTap tap = new HoloTap();
 *
 * public void mouseReleased(MouseEvent e) {
 *     if (tap.isDoubleTap(e)) {
 *         doDoubleTap(e.getX(), e.getY());
 *     } else {
 *         doSingleTap(e.getX(), e.getY());   // 第一击照样触发（原版 onSingleTapUp 也是如此）
 *     }
 * }
 * </pre>
 */
public final class HoloTap {

    /** 双击允许的最大位移（px）。对应 Android {@code ViewConfiguration#getScaledDoubleTapSlop()}。 */
    public static final int SLOP = 24;

    /** 系统双击间隔取不到时的退化值（毫秒）。 */
    public static final int DEFAULT_INTERVAL = 500;

    /** 上一次抬起的时刻；{@code 0} 表示「还没有过第一击」。 */
    private long lastTime;
    private int lastX, lastY;

    /** 系统双击间隔（毫秒）—— 就是控制面板里那个「双击速度」。 */
    public static int interval() {
        try {
            Object v = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
            if (v instanceof Integer && (Integer) v > 0) {
                return (Integer) v;
            }
        } catch (Throwable ignored) {
            // 无图形环境 / 该平台不提供 → 走退化值
        }
        return DEFAULT_INTERVAL;
    }

    /** 清掉「第一击」，避免把两次互不相关的点击接成一次双击。 */
    public void reset() {
        lastTime = 0;
    }

    /**
     * 在一次 {@code mouseReleased} 上推进状态。
     *
     * @return {@code true} 表示这一次抬起构成了一次双击。此时状态会被清掉，
     *         所以「三连击」只会算成一次双击 + 一次新的第一击，不会算成两次双击。
     */
    public boolean isDoubleTap(MouseEvent e) {
        long dt = e.getWhen() - lastTime;
        int dx = e.getX() - lastX;
        int dy = e.getY() - lastY;
        boolean second = lastTime > 0 && dt <= interval()
                && dx * dx + dy * dy <= SLOP * SLOP;
        if (second) {
            reset();
            return true;
        }
        lastTime = e.getWhen();
        lastX = e.getX();
        lastY = e.getY();
        return false;
    }
}
