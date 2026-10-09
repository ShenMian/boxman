package my.boxman.compat;

import javax.swing.JComponent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * 给任意 {@link JComponent} 装上「按下在控件内 → 抬起还在控件内 → 才算点击」这一
 * {@code JButton} 契约。自绘的可点组件（ActionBar 动作项、弹出菜单行、关卡单元…）都必须用它，
 * <b>不要</b>自己写 {@code MouseAdapter.mouseClicked}。
 *
 * <h2>为什么不能用 {@code mouseClicked}</h2>
 *
 * <p>{@code MOUSE_CLICKED} <b>不是</b>原始事件，是 Windows 原生层在
 * {@code AwtComponent::WmMouseUp} 里<b>派生</b>出来的，而且受一个<b>无阈值</b>的开关控制
 * （{@code awt_Component.cpp}）：
 *
 * <pre>
 * // WmMouseDown —— 允许本按钮产生 click
 * m_mouseButtonClickAllowed |= GetButtonMK(button);
 *
 * // WmMouseMove —— 只要按住期间来了任何一次 WM_MOUSEMOVE（哪怕 1 像素）
 * SendMouseEvent(java_awt_event_MouseEvent_MOUSE_DRAGGED, ...);
 * m_mouseButtonClickAllowed = 0;          // ← 这一次点击就此作废
 *
 * // WmMouseUp —— 位还在才补发 CLICK
 * if ((m_mouseButtonClickAllowed & GetButtonMK(button)) != 0) {
 *     SendMouseEvent(java_awt_event_MouseEvent_MOUSE_CLICKED, ...);
 * }
 * </pre>
 *
 * <p>注意这里<b>没有</b> {@code SM_CXDRAG} 之类的容差 —— 不是「拖了 4px 才算拖」，
 * 是「动一下就作废」。所以挂在 {@code mouseClicked} 上的自绘控件会表现为
 * <b>「有时点一次就中、有时点几次才有反应」</b>，且只在本程序里复现，很容易被误判成鼠标坏了。
 * Java 侧根本不生成 click：{@code WComponentPeer.handleJavaMouseEvent} 只处理
 * {@code MOUSE_PRESSED}（为了要焦点）。
 *
 * <p>Swing 自己也从不依赖它：{@code BasicButtonListener.mouseClicked()} 是个<b>空方法</b>，
 * {@code JButton} 的动作在 {@code mouseReleased()} 里经 {@code model.setPressed(false)} 触发。
 *
 * <p>而且 {@code mouseReleased} <b>更忠实于原版</b>：Android 的
 * {@code View.OnClickListener} 与 ActionBar 菜单项（{@code onOptionsItemSelected}）
 * 都在 {@code ACTION_UP} 触发，对应 {@code mouseReleased} 而不是 {@code mouseClicked}。
 *
 * <h2>契约细节</h2>
 *
 * <p>按下时记下「是否落在控件内」，抬起时要求<b>仍然</b>落在控件内才触发。
 * 这样「从别处拖进来松手」不会误触发，而「按下后手抖了几下再松手」照常触发。
 *
 * <p>刻意<b>不</b>在 {@code mouseExited} 里取消：AWT 有时会在弹出窗口/其它窗口盖住光标时
 * 发出莫名的 {@code mouseExited}，用它取消反而会让点击更不可靠。抬起时用
 * {@code contains()} 判一次就够了。
 *
 * <p>回调收到的是 {@code MOUSE_RELEASED} 那个事件（坐标、{@code getClickCount()}、
 * {@code SwingUtilities.isRightMouseButton()} 在它上面都照常可用），所以需要区分左右键、
 * 或需要坐标来弹上下文菜单的调用方可以直接用。
 */
public final class HoloClick {

    private HoloClick() {
    }

    /** 不关心事件内容的简写。 */
    public static void install(JComponent c, Runnable action) {
        install(c, e -> {
            if (action != null) action.run();
        });
    }

    /**
     * 装上一套鼠标监听：按下在 {@code c} 内、抬起仍在 {@code c} 内时，用
     * {@code MOUSE_RELEASED} 事件回调 {@code onClick}。
     */
    public static void install(JComponent c, Consumer<MouseEvent> onClick) {
        c.addMouseListener(new MouseAdapter() {
            /** 本次按下是否落在控件内（按下时算一次，抬起后清零）。 */
            private boolean armed;

            @Override
            public void mousePressed(MouseEvent e) {
                armed = c.contains(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = armed && c.contains(e.getX(), e.getY());
                armed = false;
                if (fire && onClick != null) onClick.accept(e);
            }
        });
    }
}
