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
 * 原版「关卡查询」对话框（{@code BoxMan.java:472} 的 {@code case R.id.menu_query}）。
 *
 * <p><b>1:1 复刻 {@code res/layout/query_dialog.xml}</b>（1dp = 1px），几何全部由原版截图实测校准
 * （截图 1260px 宽 = 370dp，density 3.405）：
 *
 * <pre>
 * 表头行   #334455，实测 110px ≈ 33dp
 *   ├ 「关卡集：」  TextView 100dp、16sp、无 padding（文字墨迹起点 x=89，面板边缘 x=86）
 *   └ 右侧 match_parent + gravity=right：答案库 100dp、全选 80dp（实测起点 x=559 → 138.9dp ✓）
 * 关卡集   ListView 190dp，#363636 底、padding 4dp、每项 73px ≈ 21dp、dividerHeight=4px ≈ 1dp
 *         每项是 TextView，选中 #0088aa / 未选中 #363636
 * 分隔条   View 8dp #445566
 * 5 行字段 每行 101px ≈ 30dp，行间 6dp #363636
 *   ├ 关卡名称 / 关卡作者：wrap_content 标签 + 160dp 输入框（实测 540px = 158.6dp ✓）
 *   └ 箱子个数 / 关卡列数 / 关卡行数：标签 + 60dp + 「 -- 」+ 60dp（实测 203px = 59.6dp ✓）
 * 按钮栏   取消（negative）左、确定（positive）右，等分铺满
 * </pre>
 *
 * <p>输入框全部 {@code #242424}、16sp、padding 4dp、{@code selectAllOnFocus}；
 * 数字框限 {@code android:digits="0123456789"}。
 *
 * <p>点「确定」后的行为照搬原版：<b>必须至少填一个条件，且（选中了至少一个关卡集 或 勾了「答案库」）
 * 才会发起查询</b>；查询在 {@link myQueryFragment} 里跑，进度显示在 Holo 进度对话框中。
 */
public class QueryDialog extends HoloAlertDialog {

    /** 查询完成后回调（原版走 {@code myQueryFragment.FindStatusUpdate}）。 */
    public interface QueryResultListener {
        void onQueryDone(List<mapNode> results);
    }

    // ---------------------------------------------------------------- 几何（1dp = 1px）

    /** 表头行高度（原版截图实测 110px @ density 3.405） */
    private static final int HEADER_H = 33;
    /** 「关卡集：」 的 {@code android:layout_width} */
    private static final int SET_LABEL_W = 100;
    /** 「答案库」 的 {@code android:layout_width} */
    private static final int ANS_W = 100;
    /** 「全选」 的 {@code android:layout_width} */
    private static final int ALL_W = 80;
    /** {@code ListView} 的 {@code android:layout_height} */
    private static final int LIST_H = 190;
    /** {@code ListView} 的 {@code android:padding} */
    private static final int LIST_PAD = 4;
    /** 8dp 的 {@code #445566} 分隔条 */
    private static final int SEP_H = 8;
    /** 字段行高（原版截图实测 101px ≈ 30dp） */
    private static final int ROW_H = 30;
    /** 字段行之间的 6dp {@code #363636} 间隔 */
    private static final int ROW_GAP = 6;
    /** 「关卡名称」「关卡作者」输入框宽度 */
    private static final int WIDE_FIELD = 160;
    /** 「箱子个数」等数值输入框宽度 */
    private static final int NARROW_FIELD = 60;

    /** {@code query_dialog.xml} 里 {@code <View>} 分隔条的颜色 */
    private static final Color SEPARATOR = new Color(0x44, 0x55, 0x66);

    // ---------------------------------------------------------------- 公开控件

    public JTextField tfTitle;
    public JTextField tfAuthor;
    public JTextField tfBoxes1;
    public JTextField tfBoxes2;
    public JTextField tfCols1;
    public JTextField tfCols2;
    public JTextField tfRows1;
    public JTextField tfRows2;
    public JCheckBox chkAns;
    public JCheckBox chkAll;
    public JList<String> lstSets;
    public JButton btSearch;
    public JButton btCancel;

    private final QueryResultListener listener;

    /** 关卡集 id，与 {@link #lstSets} 的项一一对应（原版的 {@code mySets}）。 */
    private final ArrayList<Long> mySets = new ArrayList<>();
    private String[] setNames = new String[0];

    public QueryDialog(Frame parent, QueryResultListener listener) {
        super(parent, "关卡查询");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        // ---------------- 表头行：关卡集： | 答案库 | 全选 ----------------
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HoloContent.HEAD);
        header.setOpaque(true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension hd = new Dimension(0, HEADER_H);
        header.setPreferredSize(hd);
        header.setMinimumSize(hd);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_H));

        JLabel setLabel = HoloContent.label("关卡集：", SET_LABEL_W, SwingConstants.LEFT);
        header.add(setLabel, BorderLayout.WEST);

        chkAns = HoloContent.check32("答案库", false, ANS_W);
        chkAll = HoloContent.check32("全选", true, ALL_W);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.setOpaque(false);
        right.add(Box.createHorizontalGlue());   // 原版内层 LinearLayout 的 gravity="right"
        right.add(chkAns);
        right.add(chkAll);
        header.add(right, BorderLayout.CENTER);

        // ---------------- 关卡集列表 ----------------
        setNames = buildSetNames();
        lstSets = HoloContent.itemList(setNames);
        lstSets.setBorder(new EmptyBorder(LIST_PAD, LIST_PAD, LIST_PAD, LIST_PAD));
        lstSets.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if (setNames.length > 0) {
            lstSets.setSelectionInterval(0, setNames.length - 1);   // 原版进入时全部勾选
        }
        installToggleSelection(lstSets);

        JScrollPane scroll = HoloContent.scroll(lstSets);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        HoloContent.darkScrollBar(scroll);
        Dimension ld = new Dimension(0, LIST_H);
        scroll.setPreferredSize(ld);
        scroll.setMinimumSize(ld);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, LIST_H));

        // ---------------- 内容列 ----------------
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(header);
        column.add(scroll);
        column.add(HoloContent.band(SEPARATOR, SEP_H));

        tfTitle = field(WIDE_FIELD, false);
        tfAuthor = field(WIDE_FIELD, false);
        tfBoxes1 = field(NARROW_FIELD, true);
        tfBoxes2 = field(NARROW_FIELD, true);
        tfCols1 = field(NARROW_FIELD, true);
        tfCols2 = field(NARROW_FIELD, true);
        tfRows1 = field(NARROW_FIELD, true);
        tfRows2 = field(NARROW_FIELD, true);

        column.add(rowOf(new Component[]{label("关卡名称: "), tfTitle}));
        column.add(HoloContent.band(HoloContent.BAND, ROW_GAP));
        column.add(rowOf(new Component[]{label("关卡作者: "), tfAuthor}));
        column.add(HoloContent.band(HoloContent.BAND, ROW_GAP));
        column.add(rangeRow("箱子个数: ", tfBoxes1, tfBoxes2));
        column.add(HoloContent.band(HoloContent.BAND, ROW_GAP));
        column.add(rangeRow("关卡列数: ", tfCols1, tfCols2));
        column.add(HoloContent.band(HoloContent.BAND, ROW_GAP));
        column.add(rangeRow("关卡行数: ", tfRows1, tfRows2));
        column.add(HoloContent.band(HoloContent.BAND, ROW_GAP));

        setContentView(column);

        // ---------------- 按钮 ----------------
        btCancel = addButton("取消", this::dispose);
        btSearch = addButton("确定", this::doSearch);
        setDefaultButton(btSearch);

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

    /** 16sp 标签，带 4dp padding（原版 TextView 的 {@code android:padding="4dp"}）。 */
    private static JLabel label(String text) {
        JLabel l = HoloContent.label(text);
        l.setBorder(new EmptyBorder(HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        return l;
    }

    /** 固定宽度、固定行高的输入框。 */
    private static JTextField field(int widthDp, boolean digitsOnly) {
        JTextField tf = digitsOnly ? HoloContent.numberField(widthDp, "")
                : HoloContent.field(widthDp, "");
        Dimension d = new Dimension(widthDp, ROW_H);
        tf.setPreferredSize(d);
        tf.setMinimumSize(d);
        tf.setMaximumSize(d);
        return tf;
    }

    /** 「标签 + 60dp + 「 -- 」 + 60dp」那种区间行。 */
    private static JPanel rangeRow(String text, JTextField from, JTextField to) {
        return rowOf(new Component[]{label(text), from, label(" -- "), to});
    }

    /** 一行字段（固定行高 30dp、水平居中、#363636 底）。 */
    private static JPanel rowOf(Component[] children) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(HoloContent.BAND);
        p.setOpaque(true);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = new Dimension(0, ROW_H);
        p.setPreferredSize(d);
        p.setMinimumSize(d);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));
        p.add(Box.createHorizontalGlue());   // 原版 android:gravity="center"
        for (Component c : children) {
            p.add(c);
        }
        p.add(Box.createHorizontalGlue());
        return p;
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

    // ---------------------------------------------------------------- 查询

    /** 原版 {@code setPositiveButton("确定", ...)} 的 onClick。 */
    private void doSearch() {
        String title = tfTitle.getText().trim();
        String author = tfAuthor.getText().trim();
        int boxs = parseInt(tfBoxes1.getText());
        int cols = parseInt(tfCols1.getText());
        int rows = parseInt(tfRows1.getText());
        int boxs2 = parseInt(tfBoxes2.getText());
        int cols2 = parseInt(tfCols2.getText());
        int rows2 = parseInt(tfRows2.getText());

        // 原版：一个条件都没填 → 什么都不做（对话框也不关）
        if (title.isEmpty() && author.isEmpty()
                && boxs <= 0 && cols <= 0 && rows <= 0
                && boxs2 <= 0 && cols2 <= 0 && rows2 <= 0) {
            return;
        }

        // 原版 mySets 用正负号记选中态（点击时 set(position, -value)），这里等价地用
        // JList 的选中项；「全选」时传 null 表示不过滤关卡集。
        int[] selected = lstSets.getSelectedIndices();
        int len = selected.length;
        // 原版：既没选关卡集也没勾答案库 → 什么都不做
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
        dispose();

        final long[] m_sets = sets;
        final int fBoxs = boxs;
        final int fCols = cols;
        final int fRows = rows;
        final int fBoxs2 = boxs2;
        final int fCols2 = cols2;
        final int fRows2 = rows2;
        final boolean ans = chkAns.isSelected();

        SwingUtilities.invokeLater(() -> {
            myQueryFragment query = new myQueryFragment(
                    getOwner() instanceof Frame ? (Frame) getOwner() : null,
                    listener == null ? null : listener::onQueryDone,
                    m_sets, ans, title, author,
                    fBoxs, fCols, fRows, fBoxs2, fCols2, fRows2);
            query.show();
        });
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
