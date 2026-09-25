package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版「搜索相似关卡」对话框 —— 1:1 复刻 {@code res/layout/find_dialog.xml}
 * （1dp = 1px），发起自 {@link myGridView} 上下文菜单第 11 项。
 *
 * <pre>
 * 表头行 1  #334455，wrap_content（沿用 query_dialog 实测的 33dp）
 *   ├ 「关卡集：」TextView 100dp、16sp
 *   └ 右侧 gravity=right：答案库 100dp、全选 80dp（默认勾选）
 * ListView find_setname  190dp，#363636、padding 4dp、divider #000000 4px
 * 表头行 2  #334455
 *   ├ 「相似度：」TextView 80dp、16sp
 *   └ 右侧 gravity=right：排序 80dp、忽略箱子和人 145dp
 * ListView find_similarity 210dp，listSelector #0088aa
 * 按钮栏   取消（negative）左、开始（positive）右
 * </pre>
 *
 * <p>注意与 {@code query_dialog.xml} 的两处差异：<b>本布局没有 8dp 的 {@code #445566} 分隔条</b>，
 * 且第二段的两个复选是「排序 / 忽略箱子和人」而不是区间输入框。
 *
 * <p>点「开始」后的行为照搬原版 {@code myGridView.onContextItemSelected()} 的 {@code case 11}：
 * <b>至少选中一个关卡集 或 勾了「答案库」才会发起查找</b>，否则对话框不关、什么都不做。
 * 真正的查找在 {@link myFindFragment} 里跑，进度显示在 Holo 进度对话框中。
 *
 * <p><b>改写前</b>这个类是个 PC 自造的占位实现：一条「最低相似度滑杆 + 忽略箱子/仅当前关卡集
 * 两个复选框」，点「开始」后只拿 {@code |ΔRows| <= 2 && |ΔCols| <= 2} 在内存列表里瞎凑结果，
 * 既不查库也不做相似度计算，且<b>没有任何生产代码调用它</b>。这里按原版重建。
 */
public class FindDialog extends HoloAlertDialog {

    /** 点「开始」且校验通过后回调（原版把参数塞进 Bundle 交给 {@code myFindFragment}）。 */
    public interface FindRequestListener {
        void onFindRequest(long[] sets, int similarity, boolean ans, boolean sort, boolean ignoreBox);
    }

    // ---------------------------------------------------------------- 几何（1dp = 1px）

    /** 表头行高度（沿用 {@code query_dialog.xml} 的实测值 110px @ density 3.405） */
    private static final int HEADER_H = 33;
    /** 「关卡集：」 的 {@code android:layout_width} */
    private static final int SET_LABEL_W = 100;
    /** 「答案库」 的 {@code android:layout_width} */
    private static final int ANS_W = 100;
    /** 「全选」 的 {@code android:layout_width} */
    private static final int ALL_W = 80;
    /** {@code find_setname} 的 {@code android:layout_height} */
    private static final int SET_LIST_H = 190;
    /** 「相似度：」 的 {@code android:layout_width} */
    private static final int SIM_LABEL_W = 80;
    /** 「排序」 的 {@code android:layout_width} */
    private static final int SORT_W = 80;
    /** 「忽略箱子和人」 的 {@code android:layout_width} */
    private static final int IGNORE_W = 145;
    /** {@code find_similarity} 的 {@code android:layout_height} */
    private static final int SIM_LIST_H = 210;
    /** {@code ListView} 的 {@code android:padding} */
    private static final int LIST_PAD = 4;

    /**
     * 原版 {@code myGridView.onContextItemSelected()} {@code case 11} 里的 {@code m_menu4}。
     * 需与 {@code myFindView.myCompare()} 里的 {@code mLeast_Similarity} 保持一致。
     */
    static final String[] SIMILARITY = {"100", "95", "90", "85", "80", "75", "66", "50"};

    // ---------------------------------------------------------------- 公开控件

    public JCheckBox chkAns;
    public JCheckBox chkAll;
    public JCheckBox chkSort;
    public JCheckBox chkIgnoreBox;
    public JList<String> lstSets;
    public JList<String> lstSimilarity;
    public JButton btStart;
    public JButton btCancel;

    private final FindRequestListener listener;

    /** 关卡集 id，与 {@link #lstSets} 的项一一对应（原版的 {@code mySets}）。 */
    private final ArrayList<Long> mySets = new ArrayList<>();
    private String[] setNames = new String[0];

    public FindDialog(Frame parent, FindRequestListener listener) {
        super(parent, "搜索相似关卡");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        // ---------------- 表头行 1：关卡集： | 答案库 | 全选 ----------------
        JPanel header1 = new JPanel(new BorderLayout());
        header1.setBackground(HoloContent.HEAD);
        header1.setOpaque(true);
        header1.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension hd = new Dimension(0, HEADER_H);
        header1.setPreferredSize(hd);
        header1.setMinimumSize(hd);
        header1.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_H));

        header1.add(HoloContent.label("关卡集：", SET_LABEL_W, SwingConstants.LEFT), BorderLayout.WEST);

        chkAns = HoloContent.check32("答案库", false, ANS_W);
        chkAll = HoloContent.check32("全选", true, ALL_W);

        JPanel right1 = new JPanel();
        right1.setLayout(new BoxLayout(right1, BoxLayout.X_AXIS));
        right1.setOpaque(false);
        right1.add(Box.createHorizontalGlue());   // 原版内层 LinearLayout 的 gravity="right"
        right1.add(chkAns);
        right1.add(chkAll);
        header1.add(right1, BorderLayout.CENTER);

        // ---------------- 关卡集列表 ----------------
        setNames = buildSetNames();
        lstSets = HoloContent.itemList(setNames);
        lstSets.setBorder(new EmptyBorder(LIST_PAD, LIST_PAD, LIST_PAD, LIST_PAD));
        lstSets.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if (setNames.length > 0) {
            lstSets.setSelectionInterval(0, setNames.length - 1);   // 原版进入时全部勾选
        }
        installToggleSelection(lstSets);
        // 原版把 find_setname 赋给了静态的 myMaps.m_setName
        myMaps.m_setName = lstSets;

        JScrollPane setScroll = HoloContent.scroll(lstSets);
        setScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        HoloContent.darkScrollBar(setScroll);
        Dimension sd = new Dimension(0, SET_LIST_H);
        setScroll.setPreferredSize(sd);
        setScroll.setMinimumSize(sd);
        setScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, SET_LIST_H));

        // ---------------- 表头行 2：相似度： | 排序 | 忽略箱子和人 ----------------
        JPanel header2 = new JPanel(new BorderLayout());
        header2.setBackground(HoloContent.HEAD);
        header2.setOpaque(true);
        header2.setAlignmentX(Component.LEFT_ALIGNMENT);
        header2.setPreferredSize(new Dimension(0, HEADER_H));
        header2.setMinimumSize(new Dimension(0, HEADER_H));
        header2.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_H));

        header2.add(HoloContent.label("相似度：", SIM_LABEL_W, SwingConstants.LEFT), BorderLayout.WEST);

        chkSort = HoloContent.check32("排序", false, SORT_W);
        chkIgnoreBox = HoloContent.check32("忽略箱子和人", false, IGNORE_W);

        JPanel right2 = new JPanel();
        right2.setLayout(new BoxLayout(right2, BoxLayout.X_AXIS));
        right2.setOpaque(false);
        right2.add(Box.createHorizontalGlue());
        right2.add(chkSort);
        right2.add(chkIgnoreBox);
        header2.add(right2, BorderLayout.CENTER);

        // ---------------- 相似度列表（单选） ----------------
        lstSimilarity = HoloContent.itemList(SIMILARITY);
        lstSimilarity.setBorder(new EmptyBorder(LIST_PAD, LIST_PAD, LIST_PAD, LIST_PAD));
        lstSimilarity.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (myMaps.m_Sets[26] < 0 || myMaps.m_Sets[26] > 7) myMaps.m_Sets[26] = 0;
        lstSimilarity.setSelectedIndex(myMaps.m_Sets[26]);   // 原版 setItemChecked(m_Sets[26], true)
        lstSimilarity.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lstSimilarity.getSelectedIndex() >= 0) {
                myMaps.m_Sets[26] = lstSimilarity.getSelectedIndex();
            }
        });

        JScrollPane simScroll = HoloContent.scroll(lstSimilarity);
        simScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        HoloContent.darkScrollBar(simScroll);
        Dimension md = new Dimension(0, SIM_LIST_H);
        simScroll.setPreferredSize(md);
        simScroll.setMinimumSize(md);
        simScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, SIM_LIST_H));

        // ---------------- 内容列 ----------------
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(header1);
        column.add(setScroll);
        column.add(header2);
        column.add(simScroll);

        setContentView(column);

        // ---------------- 按钮 ----------------
        btCancel = addButton("取消", this::dispose);
        btStart = addButton("开始", this::doStart);
        setDefaultButton(btStart);

        // 「全选」勾选时同步列表（原版 m_All.setOnCheckedChangeListener）
        chkAll.addActionListener(e -> {
            if (setNames.length == 0) {
                return;
            }
            if (chkAll.isSelected()) {
                lstSets.setSelectionInterval(0, setNames.length - 1);
            } else {
                lstSets.clearSelection();
            }
        });
    }

    // ---------------------------------------------------------------- 组装

    /** 关卡集名称（原版 {@code myMaps.getData(mySets)} 会同时填好 id 列表）。 */
    private String[] buildSetNames() {
        List<String> names = myMaps.getData(mySets);
        return names.toArray(new String[0]);
    }

    /**
     * 原版 {@code ListView} 的 {@code CHOICE_MODE_MULTIPLE} + {@code OnItemClickListener}：
     * <b>每次点击是「切换」该项的选中状态</b>，而不是把选择替换成这一项。
     * Swing 的 {@code JList} 默认是替换语义，所以这里摘掉 UI 自带的鼠标监听，自己实现切换。
     */
    private static void installToggleSelection(final JList<String> list) {
        for (java.awt.event.MouseListener ml : list.getMouseListeners()) {
            list.removeMouseListener(ml);
        }
        for (java.awt.event.MouseMotionListener ml : list.getMouseMotionListeners()) {
            list.removeMouseMotionListener(ml);
        }
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || index >= list.getModel().getSize()) {
                    return;
                }
                Rectangle bounds = list.getCellBounds(index, index);
                if (bounds != null && !bounds.contains(e.getPoint())) {
                    return;
                }
                if (list.isSelectedIndex(index)) {
                    list.removeSelectionInterval(index, index);
                } else {
                    list.addSelectionInterval(index, index);
                }
            }
        });
    }

    // ---------------------------------------------------------------- 发起查找

    /** 原版 {@code setPositiveButton("开始", ...)} 的 onClick。 */
    private void doStart() {
        int[] selected = lstSets.getSelectedIndices();
        int len = selected.length;

        // 原版：既没选关卡集也没勾答案库 → 什么都不做（对话框也不关）
        if (len == 0 && !chkAns.isSelected()) {
            return;
        }

        long[] sets = null;
        if (len != mySets.size()) {   // 全选时数组为 null（原版语义）
            sets = new long[len];
            for (int i = 0; i < len; i++) {
                sets[i] = mySets.get(selected[i]);
            }
        }

        int simIdx = lstSimilarity.getSelectedIndex();
        if (simIdx < 0) simIdx = 0;
        int similarity = Integer.parseInt(SIMILARITY[simIdx]);

        dispose();
        if (listener != null) {
            listener.onFindRequest(sets, similarity,
                    chkAns.isSelected(), chkSort.isSelected(), chkIgnoreBox.isSelected());
        }
    }
}
