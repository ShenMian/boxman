package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloInputDialog;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.UiWindow;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 原版「关卡状态」Activity（{@code myStateBrow}，1018 行）的 1:1 移植。
 *
 * <p><b>界面照搬 3 个布局</b>：
 * <ul>
 *   <li>{@code s_main.xml}：整屏 {@code ExpandableListView}，Activity 背景 {@code #ff004040}，
 *       {@code listSelector="#ff0064aa"}</li>
 *   <li>{@code s_groups.xml}：分组行，{@code #ffdddddd}、16sp、{@code paddingLeft=32dp}、
 *       上下 {@code margin 6dp}</li>
 *   <li>{@code s_child.xml}：子项行，**两行** TextView 竖排 ——
 *       第一行 {@code s_expChild}（{@code #ffbbbbbb}、16sp、{@code paddingLeft=20dp}）显示状态/答案描述，
 *       第二行 {@code s_expChild2}（{@code #ff888888}、12sp、{@code paddingRight=20dp}、右对齐）显示时间/注释</li>
 * </ul>
 * 两个布局都没有 {@code android:background}，所以条目是**透明**的 ——
 * 与 {@code drawSelectorOnTop=false} 配合，选中高亮能从下面透出来。
 *
 * <p><b>ActionBar</b>：{@code setTitle("关卡状态")} + {@code setDisplayHomeAsUpEnabled(true)}，
 * 溢出菜单逐项还原 {@code res/menu/state.xml} 的 4 项
 * （其中「导出答案的注释信息」是 {@code android:checkable="true"}，勾选态映射 {@code myMaps.m_Sets[30]}）。
 *
 * <p><b>上下文菜单</b>：原版 {@code onCreateContextMenu} 一次性 {@code add} 12 项再按
 * {@code g_Pos} 打开其中一部分，这里照做 —— 条目顺序、文案、可见性规则完全一致。
 *
 * <p><b>手势映射</b>（PC 无长按）：
 * <ul>
 *   <li>原版「单击子项」→ 载入状态并结束窗口。PC 保持**单击**，不是双击。</li>
 *   <li>原版「长按子项」→ 弹上下文菜单。PC 用**右键**。</li>
 *   <li>原版「长按『答案』分组标题」（且答案多于 1 个）→ 循环切换排序方式。PC 用**右键点分组标题**。</li>
 * </ul>
 */
public class myStateBrow extends JFrame {

    // ------------------------------------------------------------ 原版字段（同名同义）

    final String[] s_groups = {"状态", "答案"};
    final String[] s_sort = {"  【移动优先】", "  【推动优先】", "  【时间优先】"};
    /** 答案列表的默认排序 —— 移动优先 */
    static int my_Sort = 0;
    final Comparator<state_Node> comp = new SortComparator();

    int g_Pos;
    int c_Pos;
    long m_Sel_id;

    /** 水印 */
    int m_Gif_Mark = 1;
    /** 制作 GIF 动画相关参数：帧间隔（毫秒） */
    int m_Gif_Interval = 300;
    /** 帧方式：逐推 / 逐移 */
    boolean m_Gif_Type = true;
    boolean m_Gif_Skin = false;

    // ------------------------------------------------------------ 配色（取自原版资源）

    /** {@code s_main.xml} 的 Activity 背景 {@code #ff004040} */
    private static final Color LIST_BG = new Color(0x00, 0x40, 0x40);
    /** {@code s_main.xml} 的 {@code android:listSelector="#ff0064aa"} */
    private static final Color SELECT_BG = new Color(0x00, 0x64, 0xAA);
    /** {@code s_groups.xml} 的 {@code android:textColor="#ffdddddd"} */
    private static final Color GROUP_FG = new Color(0xDD, 0xDD, 0xDD);
    /** {@code s_child.xml} 的 {@code s_expChild} {@code #ffbbbbbb} */
    private static final Color CHILD_FG = new Color(0xBB, 0xBB, 0xBB);
    /** {@code s_child.xml} 的 {@code s_expChild2} {@code #ff888888} */
    private static final Color CHILD2_FG = new Color(0x88, 0x88, 0x88);
    /** 列表分隔线：原版 2px 的 (37,93,94) 折算到 1dp，与主界面一致 */
    private static final Color DIVIDER_FG = new Color(0x17, 0x51, 0x51);
    /** 分组展开指示器颜色，与主界面一致 */
    private static final Color INDICATOR_FG = new Color(0xCC, 0xCC, 0xCC);

    // ------------------------------------------------------------ 几何（1dp = 1px）

    /** 行分隔线高度（原版 2px） */
    private static final int ROW_DIVIDER_HEIGHT = 1;
    /** {@code s_groups.xml} / {@code s_child.xml} 的 {@code layout_marginTop/Bottom="6dp"} */
    private static final int ROW_MARGIN = 6;
    /** {@code s_groups.xml} 的 {@code android:paddingLeft="32dp"} */
    private static final int GROUP_PADDING_LEFT = 32;
    /** {@code s_child.xml} 的 {@code android:paddingLeft="20dp"} */
    private static final int CHILD_PADDING_LEFT = 20;
    /** {@code s_child.xml} 的 {@code android:paddingRight="20dp"} */
    private static final int CHILD_PADDING_RIGHT = 20;
    /** 分组指示器水平位置：实测 42px~82px ÷ 3.405 ≈ 12dp~24dp */
    private static final int INDICATOR_LEFT = 12;
    private static final int INDICATOR_WIDTH = 12;
    private static final int INDICATOR_HEIGHT = 6;

    private static final int GROUP_TEXT_SIZE = 16;
    private static final int CHILD_TEXT_SIZE = 16;
    private static final int CHILD2_TEXT_SIZE = 12;

    private static final Font GROUP_FONT = new Font("Microsoft YaHei", Font.PLAIN, GROUP_TEXT_SIZE);
    private static final Font CHILD_FONT = new Font("Microsoft YaHei", Font.PLAIN, CHILD_TEXT_SIZE);
    private static final Font CHILD2_FONT = new Font("Microsoft YaHei", Font.PLAIN, CHILD2_TEXT_SIZE);

    /** 上下文菜单 12 项（原版 {@code onCreateContextMenu} 的顺序与文案） */
    static final String[] CTX_ALL = {
            "打开",
            "YASS优化",
            "导出到文档: XSB+Lurd",
            "导出到剪切板: XSB+Lurd",
            "导出到剪切板: Lurd",
            "导出到剪切板: 正推 Lurd",
            "导出到剪切板: 逆推 Lurd",
            "注释标签",
            "删除",
            "删除全部状态",
            "提交答案（sokoban.cn）",
            "制作 GIF 演示动画",
    };

    /** 原版 {@code g_Pos == 0}（状态页）时可见的项 */
    static final String[] CTX_STATE = {
            "打开", "导出到文档: XSB+Lurd", "导出到剪切板: XSB+Lurd", "导出到剪切板: Lurd",
            "导出到剪切板: 正推 Lurd", "导出到剪切板: 逆推 Lurd", "注释标签", "删除", "删除全部状态",
    };

    /** 原版 {@code g_Pos == 1}（答案页）时可见的项 */
    static final String[] CTX_ANSWER = {
            "打开", "YASS优化", "导出到文档: XSB+Lurd", "导出到剪切板: XSB+Lurd", "导出到剪切板: Lurd",
            "注释标签", "删除", "提交答案（sokoban.cn）", "制作 GIF 演示动画",
    };

    // ------------------------------------------------------------ 组件

    private myActionBar actionBar;
    private JTree tree;
    /** 2 个分组节点，用于按「组号」展开（对应原版 {@code expandGroup}） */
    private DefaultMutableTreeNode[] groupNodes;

    public myStateBrow() {
        setTitle("关卡状态 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        loadData();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(LIST_BG);

        // ---- ActionBar：标题「关卡状态」+ 返回折角 + state.xml 的 4 项
        actionBar = new myActionBar();
        actionBar.setBarTitle("关卡状态");                     // setTitle("关卡状态")
        actionBar.setUpEnabled(true, this::dispose);           // setDisplayHomeAsUpEnabled(true)
        actionBar.addAction("导出全部答案到文档", () -> saveAnsToFile(-1));
        actionBar.addAction("导出全部答案到剪切板", () -> previewAndCopy("剪切板：XSB+Lurd", buildXsbLurd(-1)));
        actionBar.addAction("导出答案的注释信息", this::toggleExportComment);
        actionBar.addAction("比赛答案提交列表", () -> new mySubmitList().setVisible(true));
        // onCreateOptionsMenu：勾选态来自 myMaps.m_Sets[30]
        actionBar.setActionChecked("导出答案的注释信息", myMaps.m_Sets[30] == 1);
        add(actionBar, BorderLayout.NORTH);

        // ---- ExpandableListView 的 JTree 等价物
        tree = new JTree(buildModel()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(DIVIDER_FG);
                for (int i = 0; i < getRowCount(); i++) {
                    Rectangle rowBounds = getRowBounds(i);
                    g2.fillRect(0, rowBounds.y + rowBounds.height - ROW_DIVIDER_HEIGHT,
                            getWidth(), ROW_DIVIDER_HEIGHT);
                }
                g2.dispose();
            }
        };
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        // 0 = 行高由渲染器的 preferredSize 决定（分组行一行、子项行两行）
        tree.setRowHeight(0);
        tree.setBackground(LIST_BG);
        tree.setOpaque(true);
        tree.setCellRenderer(new RowRenderer());
        tree.setBorder(BorderFactory.createEmptyBorder());
        // 原版 ExpandableListView 单击分组标题即展开/折叠
        tree.setToggleClickCount(1);
        // 缩进完全由渲染器控制，不启用 JTree 自带的层级缩进
        tree.setUI(new BasicTreeUI() {
            @Override
            protected int getRowX(int row, int depth) {
                return 0;
            }

            /** 原版条目是 {@code match_parent}，选中色块通栏铺满整屏 */
            @Override
            protected void paintRow(Graphics g, Rectangle clipBounds, Insets insets,
                                    Rectangle bounds, TreePath path, int row,
                                    boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
                Rectangle fullRow = new Rectangle(0, bounds.y, tree.getWidth(), bounds.height);
                super.paintRow(g, clipBounds, insets, fullRow, path, row,
                        isExpanded, hasBeenExpanded, isLeaf);
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onPopup(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // 原版 onChildClick：单击子项 = 载入该状态/答案并结束
                if (e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger()) return;
                Object uo = userObjectAt(e.getX(), e.getY());
                if (uo instanceof ChildItem) {
                    ChildItem ci = (ChildItem) uo;
                    g_Pos = ci.groupPos;
                    c_Pos = ci.childPos;
                    m_Sel_id = idAt(ci.groupPos, ci.childPos);
                    openSelected();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(LIST_BG);
        scrollPane.setBackground(LIST_BG);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUI(new InvisibleScrollBarUI());
        // 原版静止时不画滚动条，宽度归零才不会压掉行背景
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        add(scrollPane, BorderLayout.CENTER);

        // onCreate 末尾：有数据的分组默认展开
        expandNonEmptyGroups();
    }

    // ------------------------------------------------------------ 数据

    /** 原版 {@code onCreate}：先按 CRC 取回状态与答案列表，再排序答案。 */
    public void loadData() {
        if (mySQLite.m_SQL != null && myMaps.curMap != null) {
            mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        }
        sortAnswers();
        rebuildTree();
        expandNonEmptyGroups();
    }

    /** 原版：{@code Collections.sort(myMaps.mState2, comp)} 后刷新适配器。 */
    private void sortAnswers() {
        if (myMaps.mState2 != null) {
            Collections.sort(myMaps.mState2, comp);
        }
    }

    /** 原版「长按『答案』分组标题」：移动优先 → 推动优先 → 时间优先 → 移动优先。 */
    void cycleSort() {
        my_Sort = (my_Sort + 1) % 3;
        sortAnswers();
        rebuildTree();
    }

    private DefaultTreeModel buildModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        groupNodes = new DefaultMutableTreeNode[s_groups.length];
        for (int g = 0; g < s_groups.length; g++) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(new GroupItem(g));
            int n = childList(g).size();
            for (int c = 0; c < n; c++) {
                group.add(new DefaultMutableTreeNode(new ChildItem(g, c)));
            }
            root.add(group);
            groupNodes[g] = group;
        }
        return new DefaultTreeModel(root);
    }

    /** 重建模型并保留原有的展开状态（对应原版 {@code notifyDataSetInvalidated()}）。 */
    private void rebuildTree() {
        if (groupNodes == null) {
            tree.setModel(buildModel());
            return;
        }
        boolean[] expanded = new boolean[s_groups.length];
        TreePath root = new TreePath(tree.getModel().getRoot());
        for (int g = 0; g < s_groups.length; g++) {
            expanded[g] = tree.isExpanded(root.pathByAddingChild(groupNodes[g]));
        }
        tree.setModel(buildModel());
        for (int g = 0; g < s_groups.length; g++) {
            if (expanded[g] && !groupNodes[g].isLeaf()) expandGroup(g);
        }
    }

    private void expandNonEmptyGroups() {
        for (int g = 0; g < s_groups.length; g++) {
            if (!childList(g).isEmpty()) expandGroup(g);
        }
    }

    /**
     * 原版是 {@code s_expView.expandGroup(组号)}；JTree 的 {@code expandRow(行号)} 会在
     * 前面的组展开后错位，所以必须按路径展开。
     */
    private void expandGroup(int g) {
        if (g < 0 || g >= groupNodes.length) return;
        tree.expandPath(new TreePath(new Object[]{tree.getModel().getRoot(), groupNodes[g]}));
    }

    private static List<state_Node> childList(int groupPos) {
        return groupPos == 0 ? myMaps.mState1 : myMaps.mState2;
    }

    private static long idAt(int groupPos, int childPos) {
        List<state_Node> list = childList(groupPos);
        return (childPos >= 0 && childPos < list.size()) ? list.get(childPos).id : -1;
    }

    private Object userObjectAt(int x, int y) {
        TreePath path = tree.getPathForLocation(x, y);
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode)) {
            return null;
        }
        return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    // ------------------------------------------------------------ 排序比较器

    /**
     * 原版 {@code SortComparator}。时间优先那一支照抄了原版「取时间字符串里 '-' 前 4 位」
     * 的写法，包括 {@code n1 < 0} 时误用 {@code b.time} 的那处 —— 保持行为一致。
     */
    static class SortComparator implements Comparator<state_Node> {
        @Override
        public int compare(state_Node a, state_Node b) {
            if (my_Sort == 0) {
                if (a.moves == b.moves) return a.pushs - b.pushs;
                return a.moves - b.moves;
            } else if (my_Sort == 1) {
                if (a.pushs == b.pushs) return a.moves - b.moves;
                return a.pushs - b.pushs;
            } else {
                String at = s(a.time);
                String bt = s(b.time);
                int n1 = at.indexOf('-');
                int n2 = bt.indexOf('-');
                String t1 = (n1 >= 0) ? at.substring(Math.max(0, n1 - 4)) : bt;
                String t2 = (n2 >= 0) ? bt.substring(Math.max(0, n2 - 4)) : bt;
                if (t1.equals(t2)) {
                    if (a.moves == b.moves) return a.pushs - b.pushs;
                    return a.moves - b.moves;
                }
                return t1.compareTo(t2);
            }
        }
    }

    // ------------------------------------------------------------ 渲染

    private class RowRenderer implements TreeCellRenderer {
        private final JLabel groupRow = new JLabel();
        private final ChildRow childRow = new ChildRow();
        private final JLabel emptyRow = new JLabel();

        RowRenderer() {
            emptyRow.setOpaque(true);
            emptyRow.setBackground(LIST_BG);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean hasFocus) {
            Object uo = value instanceof DefaultMutableTreeNode
                    ? ((DefaultMutableTreeNode) value).getUserObject() : null;

            if (uo instanceof GroupItem) {
                int g = ((GroupItem) uo).groupPos;
                groupRow.setFont(GROUP_FONT);
                groupRow.setForeground(GROUP_FG);
                // 原版 s_groups.xml 的 TextView 没有背景 → 透明，选中高亮从下面透出来
                groupRow.setOpaque(selected);
                groupRow.setBackground(SELECT_BG);
                // 原版 getGroupView：只有「答案」组拼上当前排序方式
                groupRow.setText(g == 1 ? s_groups[g] + s_sort[my_Sort] : s_groups[g]);
                groupRow.setIcon(myActionBar.createIndicator(expanded, INDICATOR_FG,
                        INDICATOR_WIDTH, INDICATOR_HEIGHT));
                groupRow.setIconTextGap(GROUP_PADDING_LEFT - INDICATOR_LEFT - INDICATOR_WIDTH);
                groupRow.setBorder(BorderFactory.createEmptyBorder(
                        ROW_MARGIN, INDICATOR_LEFT, ROW_MARGIN, 0));
                return groupRow;
            }

            if (uo instanceof ChildItem) {
                ChildItem ci = (ChildItem) uo;
                List<state_Node> list = childList(ci.groupPos);
                // 子项行同样透明（s_child.xml 无背景），选中色由 listSelector 提供
                childRow.setOpaque(selected);
                childRow.setBackground(SELECT_BG);
                if (ci.childPos < list.size()) {
                    state_Node nd = list.get(ci.childPos);
                    childRow.bind(s(nd.inf), s(nd.time));
                } else {
                    childRow.bind("", "");
                }
                return childRow;
            }

            return emptyRow;
        }
    }

    /**
     * 子项行：照 {@code s_child.xml} 竖排两行 TextView ——
     * 第一行描述（左内边距 20dp），第二行时间（右内边距 20dp、右对齐）。
     * 两行各有上下 6dp 外边距。
     */
    private static class ChildRow extends JPanel {
        private final JLabel inf = new JLabel();
        private final JLabel time = new JLabel();

        ChildRow() {
            setLayout(new StackLayout());
            inf.setFont(CHILD_FONT);
            inf.setForeground(CHILD_FG);
            inf.setBorder(BorderFactory.createEmptyBorder(
                    ROW_MARGIN, CHILD_PADDING_LEFT, ROW_MARGIN, 0));
            time.setFont(CHILD2_FONT);
            time.setForeground(CHILD2_FG);
            time.setHorizontalAlignment(SwingConstants.RIGHT);
            time.setBorder(BorderFactory.createEmptyBorder(
                    ROW_MARGIN, 0, ROW_MARGIN, CHILD_PADDING_RIGHT));
            add(inf);
            add(time);
        }

        void bind(String infText, String timeText) {
            inf.setText(infText);
            time.setText(timeText);
        }

        /** 按 preferredSize 依次竖排，并把每个子组件拉满行宽（第二行才可能右对齐）。 */
        private static class StackLayout implements LayoutManager {
            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                int w = 0;
                int h = 0;
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    Dimension d = parent.getComponent(i).getPreferredSize();
                    h += d.height;
                    w = Math.max(w, d.width);
                }
                return new Dimension(w, h);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(Container parent) {
                int y = 0;
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    Component c = parent.getComponent(i);
                    int h = c.getPreferredSize().height;
                    c.setBounds(0, y, parent.getWidth(), h);
                    y += h;
                }
            }
        }
    }

    /** 原版静止时不画滚动条，且不占宽度。 */
    private static class InvisibleScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = LIST_BG;
            this.trackColor = LIST_BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(0, 0);
        }
    }

    // ------------------------------------------------------------ 树节点载体

    private static class GroupItem {
        final int groupPos;

        GroupItem(int groupPos) {
            this.groupPos = groupPos;
        }
    }

    private static class ChildItem {
        final int groupPos;
        final int childPos;

        ChildItem(int groupPos, int childPos) {
            this.groupPos = groupPos;
            this.childPos = childPos;
        }
    }

    // ------------------------------------------------------------ 交互

    /** 原版 {@code onItemLongClick} 的右键等价物。 */
    private void onPopup(MouseEvent e) {
        Object uo = userObjectAt(e.getX(), e.getY());
        if (uo instanceof ChildItem) {
            ChildItem ci = (ChildItem) uo;
            g_Pos = ci.groupPos;
            c_Pos = ci.childPos;
            m_Sel_id = idAt(ci.groupPos, ci.childPos);
            showContextMenu(e);
        } else if (uo instanceof GroupItem) {
            GroupItem gi = (GroupItem) uo;
            g_Pos = gi.groupPos;
            c_Pos = -1;
            // 原版：长按「答案」分组标题，且答案多于 1 个 → 循环切换排序方式
            if (gi.groupPos == 1 && myMaps.mState2.size() > 1) {
                cycleSort();
            }
        }
    }

    private void showContextMenu(MouseEvent e) {
        buildContextMenu().show(tree, e.getX(), e.getY());
    }

    /**
     * 按原版 {@code onCreateContextMenu} 构造上下文菜单：
     * 先 add 全部 12 项，再全部 {@code setVisible(false)}，最后按 {@code g_Pos} 打开对应项。
     */
    JPopupMenu buildContextMenu() {
        JPopupMenu menu = HoloPopupMenu.create();
        HoloPopupMenu.addItem(menu, CTX_ALL[0], this::ctxOpen);
        HoloPopupMenu.addItem(menu, CTX_ALL[1], this::ctxYassOptimize);
        HoloPopupMenu.addItem(menu, CTX_ALL[2], () -> {
            loadSel();
            saveAnsToFile(m_Sel_id);
        });
        HoloPopupMenu.addItem(menu, CTX_ALL[3], () -> {
            loadSel();
            previewAndCopy("剪切板：XSB+Lurd", buildXsbLurd(m_Sel_id));
        });
        HoloPopupMenu.addItem(menu, CTX_ALL[4], () -> {
            loadSel();
            previewAndCopy("剪切板：Lurd", buildLurd());
        });
        HoloPopupMenu.addItem(menu, CTX_ALL[5], () -> {
            loadSel();
            previewAndCopy("剪切板：正推 Lurd", buildForwardLurd());
        });
        HoloPopupMenu.addItem(menu, CTX_ALL[6], () -> {
            loadSel();
            previewAndCopy("剪切板：逆推 Lurd", buildBackwardLurd());
        });
        HoloPopupMenu.addItem(menu, CTX_ALL[7], this::ctxComment);
        HoloPopupMenu.addItem(menu, CTX_ALL[8], this::ctxDelete);
        HoloPopupMenu.addItem(menu, CTX_ALL[9], this::ctxDeleteAll);
        HoloPopupMenu.addItem(menu, CTX_ALL[10], this::ctxSubmit);
        HoloPopupMenu.addItem(menu, CTX_ALL[11], this::ctxGifMake);

        for (String title : CTX_ALL) {
            HoloPopupMenu.setVisible(menu, title, false);
        }
        for (String title : (g_Pos == 0 ? CTX_STATE : CTX_ANSWER)) {
            HoloPopupMenu.setVisible(menu, title, true);
        }
        return menu;
    }

    /** 原版 case 1（打开）：载入状态并结束窗口。 */
    private void ctxOpen() {
        openSelected();
    }

    private void openSelected() {
        if (m_Sel_id <= 0) return;
        // 原版无条件 load_State + finish；这里只在库可用时读，其余情况仍然走 set_State()
        if (mySQLite.m_SQL != null) {
            myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        }
        set_State();
    }

    private void set_State() {
        myMaps.m_StateIsRedy = true;
        dispose();
    }

    /** 原版 case 2（YASS 优化）：PC 端求解器尚未接入，走原版「找不到求解器」分支。 */
    private void ctxYassOptimize() {
        if (mySQLite.m_SQL == null) return;
        myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        if (s(myMaps.m_State.ans).length() > 0) {
            MyToast.showToast(this, "没有找到求解器！", MyToast.LENGTH_SHORT);
        } else {
            MyToast.showToast(this, "答案是空的！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 case 8（注释标签）。 */
    private void ctxComment() {
        List<state_Node> list = childList(g_Pos);
        if (c_Pos < 0 || c_Pos >= list.size()) return;
        final state_Node nd = list.get(c_Pos);
        String t = s(nd.time);
        String lower = t.toLowerCase();
        // 原版：YASS 优化结果与导入的答案不允许改注释
        if (lower.contains("yass") || lower.contains("导入")) {
            MyToast.showToast(this, "只读！", MyToast.LENGTH_SHORT);
            return;
        }
        new HoloInputDialog(this, "注释", t, 288, inf -> {
            nd.time = inf;
            try {
                if (mySQLite.m_SQL != null) mySQLite.m_SQL.Update_A_inf(m_Sel_id, inf);
            } catch (Exception e) {
                MyToast.showToast(this, "出错了，注释未能保存！", MyToast.LENGTH_SHORT);
            }
            tree.repaint();
        }).setVisible(true);
    }

    /** 原版 case 9（删除）。 */
    private void ctxDelete() {
        new HoloConfirmDialog(this, "", "删除此状态或答案，确认吗？", "取消", "确定", () -> {
            if (mySQLite.m_SQL == null) return;
            // 第一个内置关卡至少需保留 1 个答案
            if (mySQLite.m_SQL.isCanDeleteAns(m_Sel_id)) {
                MyToast.showToast(this, "第一个内置关卡，至少需保留 1 个答案！", MyToast.LENGTH_SHORT);
                return;
            }
            mySQLite.m_SQL.del_S(m_Sel_id);
            List<state_Node> list = childList(g_Pos);
            if (c_Pos >= 0 && c_Pos < list.size()) list.remove(c_Pos);
            if (myMaps.curMap != null) {
                myMaps.curMap.Solved = !myMaps.mState2.isEmpty();
                // 若此 CRC 关卡的答案个数为 0，则设置涉及到的全部关卡为无答案
                if (g_Pos == 1 && !myMaps.curMap.Solved) {
                    mySQLite.m_SQL.Set_L_Solved(myMaps.curMap.key, 0, false);
                }
            }
            rebuildTree();
        }).setVisible(true);
    }

    /** 原版 case 10（删除全部状态）。 */
    private void ctxDeleteAll() {
        new HoloConfirmDialog(this, "", "删除所有的状态，确定吗？", "取消", "确定", () -> {
            if (mySQLite.m_SQL == null) return;
            if (c_Pos < 0 || c_Pos >= myMaps.mState1.size()) return;
            mySQLite.m_SQL.del_S_ALL(myMaps.mState1.get(c_Pos).pid);
            myMaps.mState1.clear();
            rebuildTree();
        }).setVisible(true);
    }

    /** 原版 case 11（提交答案）。 */
    private void ctxSubmit() {
        if (mySQLite.m_SQL == null) return;
        myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        new mySubmit().setVisible(true);
    }

    /** 原版 case 12（制作 GIF 演示动画）。 */
    private void ctxGifMake() {
        if (mySQLite.m_SQL == null) return;
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "GIF/");
        if (!targetDir.exists()) targetDir.mkdirs();   // 创建自定义 GIF 文件夹

        myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        String ans = s(myMaps.m_State.ans).replaceAll("[^lurdLURD]", "");

        myGifMakeDialog dlg = new myGifMakeDialog(this, ans, 0, null, null);
        // 原版把水印/帧间隔/帧方式/皮肤记在 Activity 字段里，下次打开沿用
        dlg.cbInterval.setSelectedItem(String.valueOf(m_Gif_Interval));
        dlg.chkMoveByMove.setSelected(m_Gif_Type);
        dlg.chkSkin.setSelected(m_Gif_Skin);
        dlg.rbMarkNone.setSelected(m_Gif_Mark == 0);
        dlg.rbMarkDefault.setSelected(m_Gif_Mark == 1);
        dlg.rbMarkCustom.setSelected(m_Gif_Mark == 2);
        dlg.setVisible(true);

        try {
            m_Gif_Interval = Integer.parseInt(String.valueOf(dlg.cbInterval.getSelectedItem()));
        } catch (Exception ignored) {
        }
        m_Gif_Type = dlg.chkMoveByMove.isSelected();
        m_Gif_Skin = dlg.chkSkin.isSelected();
        m_Gif_Mark = dlg.rbMarkNone.isSelected() ? 0 : (dlg.rbMarkCustom.isSelected() ? 2 : 1);
    }

    private void loadSel() {
        if (mySQLite.m_SQL != null) {
            myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        }
    }

    /** 原版 {@code onOptionsItemSelected} 的 {@code st_ex_ans_comment}：翻转 {@code myMaps.m_Sets[30]}。 */
    void toggleExportComment() {
        myMaps.m_Sets[30] = (myMaps.m_Sets[30] == 1) ? 0 : 1;
        actionBar.setActionChecked("导出答案的注释信息", myMaps.m_Sets[30] == 1);
    }

    // ------------------------------------------------------------ 导出

    /** 原版 {@code myExport2}：XSB + Lurd。 */
    String buildXsbLurd(long myID) {
        StringBuilder str = new StringBuilder();
        if (myMaps.curMap != null) {
            str.append(s(myMaps.curMap.Map));
        }
        if (myID < 0) {   // 导出全部答案
            for (state_Node nd : new ArrayList<state_Node>(myMaps.mState2)) {
                ans_Node st = (mySQLite.m_SQL != null) ? mySQLite.m_SQL.load_State(nd.id) : null;
                if (st == null) continue;
                String strAns = s(st.ans).replaceAll("[^lurdLURD]", "");
                int len = strAns.length();
                if (len > 0) {
                    str.append('\n');
                    if (st.solution == 1) {
                        int p = 0;
                        for (int k = 0; k < len; k++) {
                            if ("LURD".indexOf(strAns.charAt(k)) >= 0) p++;
                        }
                        str.append("Solution (moves ").append(len).append(", pushes ").append(p);
                        if (myMaps.m_Sets[30] == 1) {   // 是否导出答案备注
                            str.append(", comment ").append(s(st.time));
                        }
                        str.append("): \n");
                    }
                    str.append(strAns);
                }
            }
        } else {          // 导出单个状态或答案
            ans_Node st = (mySQLite.m_SQL != null) ? mySQLite.m_SQL.load_State(myID) : null;
            if (st != null) {
                String strAns = s(st.ans).replaceAll("[^lurdLURD]", "");
                int len = strAns.length();
                if (len > 0) {
                    str.append('\n');
                    if (st.solution == 1) {
                        int p = 0;
                        for (int k = 0; k < len; k++) {
                            if ("LURD".indexOf(strAns.charAt(k)) >= 0) p++;
                        }
                        str.append("Solution (moves ").append(len).append(", pushes ").append(p);
                        if (myMaps.m_Sets[30] == 1) {
                            str.append(", comment ").append(s(st.time));
                        }
                        str.append("): \n");
                    }
                    str.append(strAns);
                }
                if (st.solution != 1) {   // 若不是答案，还要加上逆推动作
                    len = s(st.bk_ans).length();
                    if (len > 0) {        // 逆推仓管员坐标，（x，y）-- 先列后行
                        str.append('\n');
                        str.append('[');
                        str.append(st.c + 1);
                        str.append(',');
                        str.append(st.r + 1);
                        str.append(']');
                        str.append(s(st.bk_ans).replaceAll("[^lurdLURD]", ""));
                    }
                }
            }
        }
        return str.toString();
    }

    /** 原版 {@code myExport}：Lurd（正推 + 逆推）。 */
    String buildLurd() {
        StringBuilder str = new StringBuilder();
        ans_Node st = myMaps.m_State;
        if (st == null) return "";
        int len = s(st.ans).length();
        if (len > 0) {
            str.append(s(st.ans).replaceAll("[^lurdLURD]", ""));
        }
        len = s(st.bk_ans).length();
        if (len > 0) {
            str.append("\n[");
            str.append(st.c + 1);
            str.append(',');
            str.append(st.r + 1);
            str.append(']');
            str.append(s(st.bk_ans).replaceAll("[^lurdLURD]", ""));
        }
        return str.toString();
    }

    /** 原版 {@code myExport3}：正推 Lurd。 */
    String buildForwardLurd() {
        ans_Node st = myMaps.m_State;
        if (st == null) return "";
        int len = s(st.ans).length();
        return len > 0 ? s(st.ans).replaceAll("[^lurdLURD]", "") : "";
    }

    /** 原版 {@code myExport4}：逆推 Lurd。 */
    String buildBackwardLurd() {
        ans_Node st = myMaps.m_State;
        if (st == null) return "";
        int len = s(st.bk_ans).length();
        if (len == 0) return "";
        StringBuilder str = new StringBuilder();
        str.append('[');
        str.append(st.c + 1);
        str.append(',');
        str.append(st.r + 1);
        str.append(']');
        str.append(s(st.bk_ans).replaceAll("[^lurdLURD]", ""));
        return str.toString();
    }

    /** 原版那种「只读多行预览 + 取消/确定」的剪切板确认框。 */
    private void previewAndCopy(String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, HoloContent.TEXT_SIZE));
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(HoloContent.FIELD_BG);
        area.setForeground(HoloContent.TEXT);
        area.setCaretColor(HoloContent.TEXT);
        JScrollPane sp = HoloContent.scroll(area);
        sp.getViewport().setBackground(HoloContent.FIELD_BG);
        sp.setPreferredSize(new Dimension(288, 160));
        new PreviewDialog(this, title, sp, area.getText()).setVisible(true);
    }

    /** 「剪切板：xxx」预览框（原版是 setView(EditText) + 取消/确定）。 */
    private static class PreviewDialog extends HoloAlertDialog {
        PreviewDialog(Frame owner, String title, JComponent view, final String text) {
            super(owner, title);
            setContentView(view);
            addButton("取消", this::dispose);
            JButton ok = addButton("确定", () -> {
                dispose();
                myMaps.saveClipper(text);
            });
            setDefaultButton(ok);
        }
    }

    /** 原版 {@code saveAnsToFile}：先问文档名，再写文件。 */
    private void saveAnsToFile(final long myID) {
        final String fn = new StringBuilder(s(myMaps.sFile))
                .append("_")
                .append(myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1)
                .append(".txt").toString();

        mTxtList();
        List<String> names = new ArrayList<String>();
        names.add("自动名称");
        names.addAll(myMaps.mFile_List);

        new FileNameDialog(this, fn, names, myID).setVisible(true);
    }

    /** 取得 {@code 推箱快手/<sPath>/款/} 下的 {@code .txt} 文档列表。 */
    private void mTxtList() {
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "款/");
        myMaps.mFile_List.clear();
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        } else {
            String[] filelist = targetDir.list();
            if (filelist != null) {
                Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
                for (String f : filelist) {
                    int dot = f.lastIndexOf('.');
                    if (dot > -1 && dot < f.length()
                            && f.substring(dot + 1).equalsIgnoreCase("txt")) {
                        myMaps.mFile_List.add(f);
                    }
                }
            }
        }
    }

    /** 原版「文档名」对话框：一个输入框 + 一份已有文档的单选列表。 */
    private class FileNameDialog extends HoloAlertDialog {
        private final JTextField et;

        FileNameDialog(Frame owner, final String autoName, List<String> existing, final long myID) {
            super(owner, "文档名");
            et = HoloContent.field(288, autoName);
            et.setBackground(new Color(0x44, 0x44, 0x44));   // 原版 et.setBackgroundColor(0xff444444)

            final String[] names = existing.toArray(new String[existing.size()]);
            JList<String> list = HoloContent.itemList(names);
            // 原版 setSingleChoiceItems(list, -1, ...)：选第 0 项（「自动名称」）回到自动名
            list.addListSelectionListener(ev -> {
                if (ev.getValueIsAdjusting()) return;
                int which = list.getSelectedIndex();
                et.setText(which > 0 ? names[which] : autoName);
            });
            JScrollPane sp = HoloContent.scroll(list);
            sp.setPreferredSize(new Dimension(288, 120));

            setContentView(HoloContent.column(HoloContent.row(et), sp));

            addButton("取消", this::dispose);
            JButton ok = addButton("确定", () -> {
                dispose();
                writeNamed(et.getText(), myID);
            });
            setDefaultButton(ok);
        }
    }

    /** 校验/补全 {@code .txt} 后缀、必要时确认覆写，然后落盘。 */
    private void writeNamed(String raw, long myID) {
        try {
            File targetDir = new File(myMaps.sRoot + myMaps.sPath + "导出/");
            myMaps.mFile_List.clear();
            if (!targetDir.exists()) targetDir.mkdirs();   // 创建"导出/"文件夹

            String str = (raw == null) ? "" : raw.trim();
            if (str.isEmpty()) {
                MyToast.showToast(this, "出错了，导出失败！", MyToast.LENGTH_SHORT);
                return;
            }
            String prefix = str.substring(str.lastIndexOf('.') + 1);
            if (!prefix.equalsIgnoreCase("txt")) {
                str = str + ".txt";
            }
            final String my_Name = str;
            File file = new File(myMaps.sRoot + myMaps.sPath + "导出/" + my_Name);
            if (file.exists()) {
                new HoloConfirmDialog(this, "", "文档已存在，覆写吗？\n导出/" + my_Name,
                        "取消", "覆写", () -> exportToast(writeStateFile(my_Name, myID)))
                        .setVisible(true);
            } else {
                exportToast(writeStateFile(my_Name, myID));
            }
        } catch (Exception e) {
            MyToast.showToast(this, "出错了，导出失败！", MyToast.LENGTH_SHORT);
        }
    }

    private void exportToast(boolean ok) {
        MyToast.showToast(this, ok ? "导出成功！" : "出错了，导出失败！", MyToast.LENGTH_SHORT);
    }

    /**
     * 原版 {@code writeStateFile}：写出关卡 XSB + {@code Title/Author/Comment} + 答案行。
     * 原版用平台默认编码（安卓 = UTF-8），这里显式指定 UTF-8 以产出同样的字节。
     */
    boolean writeStateFile(String my_Name, long myID) {
        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(myMaps.sRoot + myMaps.sPath + "导出/" + my_Name);

            fout.write(s(myMaps.curMap.Map).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            sb.append("\nTitle: ").append(s(myMaps.curMap.Title))
                    .append("\nAuthor: ").append(s(myMaps.curMap.Author));
            if (!s(myMaps.curMap.Comment).trim().isEmpty()) {
                sb.append("\nComment:\n").append(s(myMaps.curMap.Comment)).append("\nComment-End:");
            }
            fout.write(sb.toString().getBytes(StandardCharsets.UTF_8));

            if (myID < 0) {   // 导出全部答案
                for (state_Node nd : new ArrayList<state_Node>(myMaps.mState2)) {
                    ans_Node st = (mySQLite.m_SQL != null) ? mySQLite.m_SQL.load_State(nd.id) : null;
                    if (st == null) continue;
                    String strAns = s(st.ans).replaceAll("[^lurdLURD]", "");
                    int len = strAns.length();
                    if (len > 0) {
                        fout.write('\n');
                        int p = 0;
                        for (int k = 0; k < len; k++) {
                            if ("LURD".indexOf(strAns.charAt(k)) >= 0) p++;
                        }
                        fout.write(("Solution (moves " + len + ", pushes " + p
                                + (myMaps.m_Sets[30] == 1 ? s(st.time) : "") + "): \n")
                                .getBytes(StandardCharsets.UTF_8));
                        fout.write(strAns.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {          // 导出单个状态或答案
                ans_Node st = (mySQLite.m_SQL != null) ? mySQLite.m_SQL.load_State(myID) : null;
                if (st != null) {
                    String strAns = s(st.ans).replaceAll("[^lurdLURD]", "");
                    int len = strAns.length();
                    if (len > 0) {
                        fout.write('\n');
                        if (st.solution == 1) {   // 若是答案
                            int p = 0;
                            for (int k = 0; k < len; k++) {
                                if ("LURD".indexOf(strAns.charAt(k)) >= 0) p++;
                            }
                            fout.write(("Solution (moves " + len + ", pushes " + p
                                    + (myMaps.m_Sets[30] == 1 ? s(st.time) : "") + "): \n")
                                    .getBytes(StandardCharsets.UTF_8));
                        }
                        fout.write(strAns.getBytes(StandardCharsets.UTF_8));
                    }

                    if (st.solution != 1) {   // 若不是答案，还要加上逆推动作
                        len = s(st.bk_ans).length();
                        if (len > 0) {        // 逆推仓管员坐标，（x，y）-- 先列后行
                            fout.write('\n');
                            fout.write('[');
                            fout.write(Integer.toString(st.c + 1).getBytes(StandardCharsets.UTF_8));
                            fout.write(',');
                            fout.write(Integer.toString(st.r + 1).getBytes(StandardCharsets.UTF_8));
                            fout.write(']');
                            String bk = s(st.bk_ans);
                            for (int k = 0; k < len; k++) {
                                char t = bk.charAt(k);
                                if ("lurdLURD".indexOf(t) >= 0) fout.write(t);
                            }
                        }
                    }
                }
            }
            fout.flush();
        } catch (Exception e) {
            return false;
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (Exception ignored) {
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 小工具

    /** 原版多处直接 {@code .length()}，这里统一兜 null，避免脏数据把 EDT 打崩。 */
    private static String s(String v) {
        return v == null ? "" : v;
    }

    // ------------------------------------------------------------ 测试辅助

    JTree getTree() {
        return tree;
    }

    myActionBar getActionBar() {
        return actionBar;
    }

    int getSortIndex() {
        return my_Sort;
    }

    void setSortIndex(int sort) {
        my_Sort = sort;
    }

    void setSelection(int groupPos, int childPos) {
        g_Pos = groupPos;
        c_Pos = childPos;
        m_Sel_id = idAt(groupPos, childPos);
    }

    void setSelectionId(long id) {
        m_Sel_id = id;
    }

    long getSelectedId() {
        return m_Sel_id;
    }

    int getGroupPos() {
        return g_Pos;
    }

    int getChildPos() {
        return c_Pos;
    }

    /** 原版单击子项的行为，供测试直接触发。 */
    void openSelectedForTest() {
        openSelected();
    }

    /** 原版长按「答案」分组标题的行为，供测试直接触发。 */
    void cycleSortForTest() {
        cycleSort();
    }

    void refreshForTest() {
        rebuildTree();
    }
}
