package my.boxman.compat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 原版 {@code AlertDialog.Builder.setSingleChoiceItems(...)} 的等价物。
 *
 * <p>原版这种框有两副面孔，这里都覆盖：
 * <ul>
 *   <li>{@link #clickToPick}：{@code .setSingleChoiceItems(items, -1, onClick)}
 *       + {@code .setPositiveButton("取消", null)}，且 {@code onClick} 末尾立刻
 *       {@code dialog.dismiss()} —— <b>单击列表项即生效</b>（「加载」「保存到」用这种）。</li>
 *   <li>{@link #selectThenOk}：{@code .setNegativeButton("取消", null)}
 *       + {@code .setPositiveButton("确定", ...)} —— <b>先选中、再点确定</b>
 *       （「读入：宏」「文档：导入」用这种）。</li>
 * </ul>
 *
 * <p>列表本体用 {@link HoloContent#itemList}（{@code #363636} 底、白字、
 * {@code #0088aa} 选中、项间 1px 黑分隔线），与原版 {@code ListView} 一致。
 */
public class HoloChoiceDialog extends HoloAlertDialog {

    /** 「选中」回调，参数是列表下标（原版那个 {@code which}）。 */
    public interface OnPick {
        void pick(int index);
    }

    /** 列表最大可见高度：原版 ListView 受窗口高度约束，超长要滚动。 */
    private static final int MAX_LIST_HEIGHT = 300;
    /** 列表与对话框内容区上下各留 8dp（原版 customPanel 的 margin）。 */
    private static final int LIST_PAD = 8;

    public final JList<String> list;

    /**
     * @param owner  所属窗口
     * @param title  对话框标题（原版 {@code setTitle}），可为 {@code ""}
     * @param extra  列表上方的附加视图（原版 {@code setView}，如「宏」名称的输入框），可为 {@code null}
     * @param items  单选项文字
     */
    public HoloChoiceDialog(Frame owner, String title, JComponent extra, String[] items) {
        super(owner, title);

        list = HoloContent.itemList(items);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        if (extra != null) {
            extra.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(extra);
            body.add(Box.createVerticalStrut(LIST_PAD));
        }

        JScrollPane sp = HoloContent.scroll(list);
        HoloContent.darkScrollBar(sp);
        int full = items.length * (HoloContent.LIST_ITEM_HEIGHT + HoloContent.LIST_DIVIDER_HEIGHT) + 2;
        int h = Math.min(full, MAX_LIST_HEIGHT);
        Dimension d = new Dimension(0, h);
        sp.setPreferredSize(d);
        sp.setMinimumSize(d);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(sp);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(LIST_PAD, 0, LIST_PAD, 0));
        wrap.add(body, BorderLayout.CENTER);
        setContentView(wrap);
    }

    /** 当前选中项下标，未选中为 {@code -1}（等价于原版的 {@code m_nItemSelect}）。 */
    public int getSelectedIndex() {
        return list.getSelectedIndex();
    }

    /** 单击列表项即生效（原版 onClick 里直接 {@code dismiss()}）。只有「取消」一个按钮。 */
    public static HoloChoiceDialog clickToPick(Frame owner, String title, String[] items, OnPick onPick) {
        HoloChoiceDialog d = new HoloChoiceDialog(owner, title, null, items);
        d.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = d.list.getSelectedIndex();
            if (i < 0) return;
            // 在监听里直接 dispose 会打断 ListUI 的后续处理，挪到事件队列尾部
            SwingUtilities.invokeLater(() -> {
                d.dispose();
                if (onPick != null) onPick.pick(i);
            });
        });
        d.addButton("取消", d::dispose);
        return d;
    }

    /** 先选中、再点「确定」生效；未选中时点「确定」什么都不做（原版判 {@code m_nItemSelect > -1}）。 */
    public static HoloChoiceDialog selectThenOk(Frame owner, String title, JComponent extra,
                                                String[] items, OnPick onPick) {
        return selectThenOk(owner, title, extra, items, -1, onPick);
    }

    /**
     * 同上，但可以预选一项 —— 对应原版
     * {@code .setSingleChoiceItems(items, <初始下标>, listener)}（「图像识别」的
     * 「清理箱子」等选项框就是这种，默认选中第 0 项）。
     *
     * @param initialIndex 预选下标，{@code <0} 表示不预选
     */
    public static HoloChoiceDialog selectThenOk(Frame owner, String title, JComponent extra,
                                                String[] items, int initialIndex, OnPick onPick) {
        HoloChoiceDialog d = new HoloChoiceDialog(owner, title, extra, items);
        if (initialIndex >= 0 && initialIndex < items.length) {
            d.list.setSelectedIndex(initialIndex);
            d.list.ensureIndexIsVisible(initialIndex);
        }
        d.addButton("取消", d::dispose);
        JButton ok = d.addButton("确定", () -> {
            int i = d.list.getSelectedIndex();
            d.dispose();
            if (i >= 0 && onPick != null) onPick.pick(i);
        });
        d.setDefaultButton(ok);
        return d;
    }
}
