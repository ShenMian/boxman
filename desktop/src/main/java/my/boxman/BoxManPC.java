package my.boxman;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.HoloProgressDialog;
import my.boxman.compat.HoloViewDialog;
import my.boxman.compat.UiWindow;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * 主界面（原版 my.boxman.BoxMan Activity）。
 *
 * 布局严格对应原版资源：
 *   res/layout/main.xml      →  ExpandableListView 铺满整屏，背景 #004040，
 *                               listSelector #0064aa
 *   res/layout/v_groups.xml  →  一级（关卡组别）条目：16sp、#dddddd、paddingLeft 40dp
 *   res/layout/v_child.xml   →  二级（关卡集）条目：16sp、#bbbbbb、paddingLeft 40dp
 *   res/menu/main.xml        →  ActionBar 右侧溢出菜单的 10 个菜单项
 *   res/values/style.xml     →  ActionBar 背景 #0083C5、标题白色
 *
 * 原版 BoxMan.onCreate 中 setTitle("推箱快手 " + count_Level()) 与
 * actionBar.setDisplayShowHomeEnabled(false)，此处由 myActionBar 等价呈现。
 */
public class BoxManPC extends JFrame {

    // ------------------------------------------------------------ 视觉常量（取自原版资源）

    /** main.xml: android:background="#ff004040" */
    private static final Color LIST_BG = new Color(0x004040);
    /** v_groups.xml: android:textColor="#ffdddddd" */
    private static final Color GROUP_FG = new Color(0xDDDDDD);
    /** v_child.xml: android:textColor="#ffbbbbbb" */
    private static final Color CHILD_FG = new Color(0xBBBBBB);
    /** main.xml: android:listSelector="#ff0064aa" */
    private static final Color SELECT_BG = new Color(0x0064AA);
    /**
     * ExpandableListView 行分隔线。
     * 原版实测：2px 的 (37,93,94) 落在 #004040 上，折算到 1dp 宽度即 (23,81,81)。
     */
    private static final Color DIVIDER_FG = new Color(0x17, 0x51, 0x51);
    /** 组别展开指示器（原版 expander 图标颜色） */
    private static final Color INDICATOR_FG = new Color(0xCCCCCC);

    /** v_groups.xml / v_child.xml: android:textSize="16sp"，PC 端按 1sp = 1px 映射 */
    private static final int ITEM_TEXT_SIZE = 16;
    /** 行内容高度：原版 113px ÷ density 3.4 ≈ 33dp */
    private static final int ROW_CONTENT_HEIGHT = 33;
    /** 行分隔线高度：原版 2px ÷ density 3.4 ≈ 1px */
    private static final int ROW_DIVIDER_HEIGHT = 1;
    private static final int ROW_HEIGHT = ROW_CONTENT_HEIGHT + ROW_DIVIDER_HEIGHT;

    /** v_groups.xml / v_child.xml: android:paddingLeft="40dp" */
    private static final int ITEM_PADDING_LEFT = 40;
    /** 组别指示器水平位置：原版 42px~82px ÷ density 3.4 ≈ 12dp~24dp */
    private static final int INDICATOR_LEFT = 12;
    private static final int INDICATOR_WIDTH = 12;
    private static final int INDICATOR_HEIGHT = 6;
    /** 指示器与组别文字之间的间距，使组别文字与子项文字同落在 40dp 处 */
    private static final int INDICATOR_TEXT_GAP = ITEM_PADDING_LEFT - INDICATOR_LEFT - INDICATOR_WIDTH;

    private static final Font ITEM_FONT = new Font("Microsoft YaHei", Font.PLAIN, ITEM_TEXT_SIZE);

    // ------------------------------------------------------------ 窗口尺寸
    // 原版 AndroidManifest 中所有 Activity 均为 android:screenOrientation="portrait"（竖屏），
    // 应用可用区域 370dp x 780dp（见 compat/UiWindow），PC 端按 1dp = 1px 取 370x780。
    private static final int PHONE_CONTENT_WIDTH = UiWindow.PHONE_WIDTH;
    private static final int PHONE_CONTENT_HEIGHT = UiWindow.PHONE_HEIGHT;

    /** 原版 BoxMan.java: private String[] groups = {"入门关卡", "进阶关卡", "花样关卡", "关卡扩展"}; */
    private static final String[] GROUPS = {"入门关卡", "进阶关卡", "花样关卡", "关卡扩展"};

    // ------------------------------------------------------------ 组件

    private myActionBar actionBar;
    private JTree levelTree;
    private final Icon indicatorExpanded = myActionBar.createIndicator(true, INDICATOR_FG, INDICATOR_WIDTH, INDICATOR_HEIGHT);
    private final Icon indicatorCollapsed = myActionBar.createIndicator(false, INDICATOR_FG, INDICATOR_WIDTH, INDICATOR_HEIGHT);

    /** 原版 {@code BoxMan.mDialog}：批量导入的进度对话框（{@code mySplitLevelsFragment}）。 */
    private mySplitLevelsFragment mDialog;
    /** 原版 {@code BoxMan.mDialog3}：批量导出的进度对话框（{@code myExportFragment}）。 */
    private myExportFragment mDialog3;
    /** 原版 {@code BoxMan.andOpen}：导入后是否允许打开关卡（只有长按关卡集的导入会置 true）。 */
    private boolean andOpen = false;

    /** {@code import_dialog3.xml} / {@code export_dialog3.xml} 里 ListView 的高度。 */
    private static final int SET_LIST_HEIGHT = 190;
    /** {@code export_dialog3.xml} / {@code export2_dialog.xml} 里「覆盖同名文档」前的 80dp 占位。 */
    private static final int EXPORT_LEADING_GAP = 80;

    /** 原版 {@code BoxMan.java:78}: {@code private static String url = "api/competition/";} */
    private static final String COMPETITION_URL = "api/competition/";

    // ------------------------------------------------------------ 上下文菜单状态（原版 BoxMan 的字段）

    /** 原版 {@code BoxMan.groupPos}：长按条目所属的关卡组下标（0..3）。 */
    int groupPos = -1;
    /** 原版 {@code BoxMan.childPos}：长按条目在该组内的关卡集下标。 */
    int childPos = -1;

    /** 原版 {@code BoxMan.java:79}: {@code private static String url_Num;}（比赛期号参数）。 */
    String url_Num = "";

    /**
     * 「把对话框显示出来」这一步的出口 —— 默认就是模态的 {@code setVisible(true)}。
     *
     * <p>凡是「点了会开窗」的动作方法（{@link #sel_File()} / {@link #read_Plate()} /
     * {@link #exportOneSet()} / {@link #reName()} / {@link #clearSetState()} /
     * {@link #deleteSetAnswers()} / {@link #deleteSet()} / {@link #importMatchLevels()} /
     * {@link #showSetAbout()}）都经过它。测试把它换成「只记录、不显示」，
     * 就能直接驱动这些方法而不会卡在模态框上。
     */
    java.util.function.Consumer<JDialog> dialogShower = dlg -> dlg.setVisible(true);

    /** 最后一次构建出的 10 项上下文菜单，供测试取用。 */
    private JPopupMenu contextMenuForTest;
    /** 最后一次「文档导入」搭好的对话框，供测试取用。 */
    private HoloChoiceDialog docImportDialogForTest;
    /** 最后一次「剪切板导入」用的文本框，供测试取用。 */
    private JTextArea clipAreaForTest;
    /** 最后一次「导出...」搭好的对话框，供测试取用。 */
    private HoloAlertDialog exportSetDialogForTest;

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Throwable ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            try {
                BoxManPC app = new BoxManPC();
                app.setVisible(true);
            } catch (Throwable t) {
                // 原版 BoxMan.onCreate 在 openDataBase() 失败时会
                // MyToast + finish + System.exit(0)。PC 端同样不允许「带着坏库继续跑」——
                // 那会退化成满屏 no such table。这里把原因明确弹给用户。
                t.printStackTrace();
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                JOptionPane.showMessageDialog(null,
                        "关卡库出错，无法继续游戏！\n\n" + msg,
                        "推箱快手", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    public BoxManPC() {
        // 原版 AndroidManifest: android:label="@string/app_name" → "推箱快手"
        setTitle("推箱快手");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initAppEnvironment();
        initUI();
        // 原版 AndroidManifest: 主 Activity 为 portrait，窗口保持手机竖屏尺寸
        UiWindow.applyPhoneSize(this);
    }

    private void initAppEnvironment() {
        if (myMaps.sRoot == null) {
            myMaps.sRoot = System.getProperty("user.home") + "/.boxman";
        }
        myMaps.sPath = "/";
        // 原版：myMaps.m_nWinWidth/Height = 设备屏幕尺寸；PC 端取主窗口的竖屏内容尺寸
        myMaps.m_nWinWidth = PHONE_CONTENT_WIDTH;
        myMaps.m_nWinHeight = PHONE_CONTENT_HEIGHT;

        new File(myMaps.sRoot).mkdirs();
        // 原版 BoxMan.java:192-203 在启动时把 7 个工作目录一次建好
        // （超长答案/、导入/、导出/、创编关卡/、关卡图/、宏/；背景/ 由皮肤复制流程负责）
        for (String dir : new String[]{"超长答案", "导入", "导出", "创编关卡", "关卡图", "宏"}) {
            new File(myMaps.sRoot + myMaps.sPath + dir + "/").mkdirs();
        }

        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();

        loadAllSets();
        myMaps.loadSkins();
    }

    private void loadAllSets() {
        myMaps.mSets0 = mySQLite.m_SQL.get_GroupList(0);
        myMaps.mSets1 = mySQLite.m_SQL.get_GroupList(1);
        myMaps.mSets2 = mySQLite.m_SQL.get_GroupList(2);
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);

        // 原版：扩展关卡组为空时自动创建一个“新关卡集”
        if (myMaps.mSets3.size() == 0) {
            try {
                long new_ID = mySQLite.m_SQL.add_T(3, "新关卡集", "", "");
                set_Node nd = new set_Node();
                nd.id = new_ID;
                nd.title = "新关卡集";
                myMaps.mSets3.add(nd);
            } catch (Exception ignored) {
            }
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 顶部 ActionBar（原版 getActionBar()）
        actionBar = new myActionBar();
        buildOverflowMenu();
        updateActionBarTitle();
        add(actionBar, BorderLayout.NORTH);

        // 关卡分类列表（原版 ExpandableListView，铺满整屏）
        // 换选时整行重绘：BasicTreeUI.getRepaintPathBounds() 只有在打开这个开关时，
        // 才会把重绘区域从「节点宽度」扩到整行（bounds.x=0, width=tree.getWidth()）；
        // 否则换选后旧高亮右侧会残留上一次的色块。全工程只有这一棵 JTree。
        UIManager.put("Tree.repaintWholeRow", Boolean.TRUE);
        levelTree = createLevelTree();
        expandRememberedGroup();

        JScrollPane scrollPane = new JScrollPane(levelTree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(LIST_BG);
        scrollPane.getViewport().setBackground(LIST_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        scrollPane.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void updateActionBarTitle() {
        // 原版 BoxMan.onCreate / onStart: setTitle("推箱快手 " + mySQLite.m_SQL.count_Level())
        if (actionBar != null && mySQLite.m_SQL != null) {
            actionBar.setBarTitle("推箱快手 " + mySQLite.m_SQL.count_Level());
        }
    }

    // ------------------------------------------------------------ 列表

    @SuppressWarnings("unchecked")
    private DefaultTreeModel buildTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        ArrayList<set_Node>[] sets = new ArrayList[]{myMaps.mSets0, myMaps.mSets1, myMaps.mSets2, myMaps.mSets3};

        for (int i = 0; i < GROUPS.length; i++) {
            ArrayList<set_Node> list = sets[i] != null ? sets[i] : new ArrayList<set_Node>();
            // 原版 getGroupView: groups[groupPosition] + " 【" + size + "】"
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(GROUPS[i], list.size()));
            for (set_Node node : list) {
                long total = mySQLite.m_SQL.count_Level(node.id);
                long solved = mySQLite.m_SQL.count_Sovled(node.id);
                groupNode.add(new DefaultMutableTreeNode(new SetItem(node.id, node.title, solved, total)));
            }
            root.add(groupNode);
        }
        return new DefaultTreeModel(root);
    }

    /** 原版 onCreate: expView.expandGroup(myMaps.m_Sets[0])，即只展开上次所在的组别 */
    private void expandRememberedGroup() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) levelTree.getModel().getRoot();
        int remembered = myMaps.m_Sets[0];
        if (remembered < 0 || remembered >= root.getChildCount()) remembered = 0;
        levelTree.expandPath(new TreePath(((DefaultMutableTreeNode) root.getChildAt(remembered)).getPath()));
    }

    private JTree createLevelTree() {
        // 行分隔线由 paintComponent 统一绘制（原版 ExpandableListView 的 divider，通栏 1px）
        JTree tree = new JTree(buildTreeModel()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(DIVIDER_FG);
                for (int i = 0; i < getRowCount(); i++) {
                    Rectangle rowBounds = getRowBounds(i);
                    g2.fillRect(0, rowBounds.y + rowBounds.height - ROW_DIVIDER_HEIGHT, getWidth(), ROW_DIVIDER_HEIGHT);
                }
                g2.dispose();
            }
        };
        // 原版没有额外的根节点，4 个关卡组即为顶层条目
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        tree.setRowHeight(ROW_HEIGHT);
        tree.setBackground(LIST_BG);
        tree.setOpaque(true);
        tree.setFont(ITEM_FONT);
        tree.setCellRenderer(new RowRenderer());
        tree.setBorder(BorderFactory.createEmptyBorder());
        // 原版 ExpandableListView 单击组别条目即展开/折叠
        tree.setToggleClickCount(1);
        // 缩进完全由渲染器控制（与原版 paddingLeft 一致），不启用 JTree 自带的层级缩进
        tree.setUI(new BasicTreeUI() {
            @Override
            protected int getRowX(int row, int depth) {
                return 0;
            }

            /**
             * 原版 ExpandableListView 的条目是 {@code match_parent}，选中色块**通栏铺满整屏**
             * （实测原版截图里选中行 x 从 0 一直到 1259，即整屏宽）。
             *
             * <p>而 JTree 默认只把「节点自身宽度」的矩形交给渲染器：{@code paintRow} 收到的
             * {@code bounds} 来自私有的 {@code getPathBounds(path, insets, buffer)} →
             * {@code TreeState.getBounds()}，实测同一棵树里逐行不同（组别行 150、子项行 195 / 193），
             * 于是选中高亮只有文字那么宽。这里把交给渲染器的行矩形撑到树的整宽。
             */
            @Override
            protected void paintRow(Graphics g, Rectangle clipBounds, Insets insets,
                                    Rectangle bounds, TreePath path, int row,
                                    boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
                Rectangle fullRow = new Rectangle(0, bounds.y, tree.getWidth(), bounds.height);
                super.paintRow(g, clipBounds, insets, fullRow, path, row,
                        isExpanded, hasBeenExpanded, isLeaf);
            }
        });

        // 原版 onChildClick：单击关卡集条目即进入关卡网格浏览。
        // 原版 OnItemLongClickListener + registerForContextMenu：长按条目弹 10 项上下文菜单
        // （组别行 childPos < 0，原版不弹）。
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object last = path.getLastPathComponent();
                if (!(last instanceof DefaultMutableTreeNode)) return;
                Object userObject = ((DefaultMutableTreeNode) last).getUserObject();

                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e, path);
                    return;
                }
                if (userObject instanceof SetItem) {
                    int[] pos = positionOf(path);
                    if (pos != null) browLevels(pos[0], pos[1]);
                }
            }
        });

        return tree;
    }

    /**
     * 由树路径反查原版的 {@code groupPos} / {@code childPos}（关卡组下标 / 组内关卡集下标）。
     *
     * @return {@code {groupPos, childPos}}；组别行与根节点返回 {@code null}
     *         （原版此时 {@code childPos < 0}，不弹上下文菜单）
     */
    private static int[] positionOf(TreePath path) {
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) last;
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) node.getParent();
        if (groupNode == null) return null;                       // 根节点
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) groupNode.getParent();
        if (root == null) return null;                            // 组别行本身
        return new int[]{root.getIndex(groupNode), groupNode.getIndex(node)};
    }

    /**
     * 原版 {@code browLevels(groupPos, childPos)}：装载该关卡集并进入关卡网格浏览。
     *
     * <p>单击条目（{@code onChildClick}）与上下文菜单的「打开」都走这里。
     */
    void browLevels(int groupPos, int childPos) {
        //确保关卡集进入一次 （点击太快，可能进入两次）
        if (myMaps.curJi) return;
        myMaps.curJi = true;

        //记住点击的位置，下次打开游戏时定位到此
        myMaps.m_Sets[0] = groupPos;
        myMaps.m_Sets[1] = childPos;

        //加载关卡
        loadLevels(groupPos, childPos);

        if (myMaps.m_lstMaps.size() < 1) {
            MyToast.showToast(this, "未找到关卡！", MyToast.LENGTH_SHORT);
            myMaps.curJi = false;
            return;
        }

        final long setId = myMaps.m_Set_id;
        final String setTitle = myMaps.sFile;
        SwingUtilities.invokeLater(() -> {
            myGridView grid = new myGridView(setId, setTitle);
            grid.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    myMaps.curJi = false;
                    refreshTree();
                }
            });
            grid.setVisible(true);
        });
    }

    /**
     * 原版 {@code loadLevels(groupPosition, childPosition)}：按 {@code groupPos/childPos}
     * 取出关卡集 id 与名称，再从库里装载关卡集信息与关卡列表。
     *
     * <p>{@code get_Set()} 内部会把 {@code myMaps.m_Set_id} 设成该关卡集，所以这里不重复赋值。
     */
    void loadLevels(int groupPos, int childPos) {
        set_Node nd = setOf(groupPos, childPos);
        if (nd == null) return;
        myMaps.sFile = nd.title;
        mySQLite.m_SQL.get_Set(nd.id);
        mySQLite.m_SQL.get_Levels(nd.id);
    }

    // ------------------------------------------------------------ 上下文菜单（原版 onCreateContextMenu）

    /**
     * 原版 {@code onCreateContextMenu()} 的 10 项标题，顺序一致
     * （下标 {@code i} 对应菜单项 id {@code i + 1}）。
     */
    static final String[] CONTEXT_ITEMS = {
            "打开", "导出...", "清理状态记录...", "删除答案...",
            "重命名...", "删除", "添加关卡(文档)...", "添加关卡(剪切板)...",
            "添加比赛关卡(sokoban.ws)", "详细..."};

    /** 原版 {@code loadLevels()} 里那个 {@code switch (groupPosition)} 的等价物。 */
    private static ArrayList<set_Node> groupList(int groupPos) {
        switch (groupPos) {
            case 0:  return myMaps.mSets0;
            case 1:  return myMaps.mSets1;
            case 2:  return myMaps.mSets2;
            case 3:  return myMaps.mSets3;
            default: return null;
        }
    }

    /** 原版反复出现的「按 {@code groupPos/childPos} 取关卡集」的 switch。 */
    private static set_Node setOf(int groupPos, int childPos) {
        ArrayList<set_Node> list = groupList(groupPos);
        if (list == null || childPos < 0 || childPos >= list.size()) return null;
        return list.get(childPos);
    }

    /**
     * 原版 {@code OnItemLongClickListener} + {@code onCreateContextMenu()}：
     * 长按关卡集条目弹出 10 项上下文菜单（组别行不弹）。
     */
    private void showContextMenu(MouseEvent e, TreePath path) {
        int[] pos = positionOf(path);
        if (pos == null) return;                 // 组别行：原版 childPos < 0，不弹
        groupPos = pos[0];
        childPos = pos[1];
        JPopupMenu menu = buildContextMenu();
        contextMenuForTest = menu;
        menu.show(levelTree, e.getX(), e.getY());
    }

    /**
     * 构建上下文菜单 —— 原版 {@code onCreateContextMenu()}。
     *
     * <p>可见性：{@code 打开 / 导出... / 清理状态记录... / 删除答案... / 详细...} 恒可见；
     * 中间的 {@code 重命名... / 删除 / 添加关卡(文档)... / 添加关卡(剪切板)... /
     * 添加比赛关卡(sokoban.ws)} 只在<b>扩展关卡组</b>（原版 {@code if (groupPos > 2)}）下出现。
     *
     * <p>副作用照抄原版：{@code onCreateContextMenu()} 末尾会把 {@code myMaps.m_Sets[0]}/{@code [1]}
     * 写成当前下标（下次启动定位到此）。
     */
    JPopupMenu buildContextMenu() {
        myMaps.m_Sets[0] = groupPos;
        myMaps.m_Sets[1] = childPos;

        boolean ext = groupPos > 2;              //扩展组
        boolean[] vis = new boolean[CONTEXT_ITEMS.length];
        vis[0] = vis[1] = vis[2] = vis[3] = true;
        vis[4] = vis[5] = vis[6] = vis[7] = vis[8] = ext;
        vis[9] = true;

        JPopupMenu menu = HoloPopupMenu.create();
        for (int i = 0; i < CONTEXT_ITEMS.length; i++) {
            final int itemId = i + 1;
            HoloPopupMenu.Row row = HoloPopupMenu.addItem(menu, CONTEXT_ITEMS[i],
                    () -> onContextItemSelected(itemId));
            row.setVisible(vis[i]);
        }
        menu.revalidate();
        menu.repaint();
        return menu;
    }

    /**
     * 原版 {@code onContextItemSelected(MenuItem)} 的等价物：按菜单项 id（1..10）执行动作。
     * 拆成独立方法以便测试直接调用（不必真弹菜单）。
     *
     * @return 恒为 {@code true}（原版如此）
     */
    boolean onContextItemSelected(int itemId) {
        switch (itemId) {
            case 1:   // 打开
                browLevels(groupPos, childPos);
                break;
            case 2:   // 导出...
                exportOneSet();
                break;
            case 3:   // 清理状态记录...
                clearSetState();
                break;
            case 4:   // 删除答案...
                deleteSetAnswers();
                break;
            case 5:   // 重命名...（仅扩展组）
                reName();
                break;
            case 6:   // 删除（仅扩展组）
                deleteSet();
                break;
            case 7:   // 添加关卡(文档)...（仅扩展组）
                addLevelsFromDoc();
                break;
            case 8:   // 添加关卡(剪切板)...（仅扩展组）
                addLevelsFromClip();
                break;
            case 9:   // 添加比赛关卡(sokoban.ws)（仅扩展组）
                importMatchLevels();
                break;
            case 10:  // 详细...（== 关卡集的「关于...」）
                showSetAbout();
                break;
            default:
                break;
        }
        return true;
    }

    // ------------------------------------------------------------ case 2「导出...」

    /**
     * 原版 case 2「导出...」：单关卡集导出。
     *
     * <p>选项框是 {@code res/layout/export2_dialog.xml}：
     * 6dp 色条 → {@code #363636} 行（{@code 选项：} + 16dp + 含答案 + 10dp + 答案含备注）
     * → 6dp 色条 → 80dp 占位 + 覆盖同名文档 → 6dp 色条。
     */
    void exportOneSet() {
        HoloAlertDialog dlg = buildExportSetDialog();
        if (dlg != null) dialogShower.accept(dlg);
    }

    /**
     * 只把「导出...」对话框搭好、不显示 —— 供测试检查初值与组件树。
     *
     * @return 搭好的对话框；{@code childPos} 无效时返回 {@code null}
     */
    HoloAlertDialog buildExportSetDialog() {
        final set_Node nd = setOf(groupPos, childPos);
        if (nd == null) return null;

        myMaps.isLurd = false;
        myMaps.sFile = nd.title;

        final JCheckBox cbLurd = HoloContent.wrapCheck("含答案", false);
        final JCheckBox cbComment = HoloContent.wrapCheck("答案含备注", false);
        cbLurd.addActionListener(e -> {
            myMaps.isLurd = cbLurd.isSelected();
            cbComment.setSelected(cbLurd.isSelected());
        });
        // ⚠️ 原版这个监听器有个 else：勾选时只联动 m_LURD、**不写** myMaps.isComment，
        //    只有取消勾选才写。照抄不改（和 sel_Set2() 里那个「总是写」的版本不同）。
        cbComment.addActionListener(e -> {
            if (cbComment.isSelected()) cbLurd.setSelected(true);
            else myMaps.isComment = cbComment.isSelected();   //是否导出答案的备注信息
        });

        final JCheckBox cbReWrite = HoloContent.wrapCheck("覆盖同名文档", true);   // m_ReWrite.setChecked(true)

        JComponent content = HoloContent.column(
                HoloContent.band(HoloContent.BAND, 6),
                HoloContent.row(HoloContent.BAND, 0,
                        HoloContent.label("选项："), Box.createHorizontalStrut(16), cbLurd,
                        Box.createHorizontalStrut(10), cbComment),
                HoloContent.band(HoloContent.BAND, 6),
                HoloContent.row(HoloContent.BAND, 0,
                        Box.createHorizontalStrut(EXPORT_LEADING_GAP), cbReWrite),
                HoloContent.band(HoloContent.BAND, 6));

        HoloAlertDialog dlg = HoloAlertDialog.create(this, "导出");
        dlg.setContentView(content);
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            exPort_Sets(new long[]{nd.id}, false, myMaps.isLurd, cbReWrite.isSelected());
        });
        exportSetDialogForTest = dlg;
        return dlg;
    }

    // ------------------------------------------------------------ case 3「清理状态记录...」

    /** 原版 case 3「清理状态记录...」：确认后 {@code clear_S(m_id)}。 */
    void clearSetState() {
        final set_Node nd = setOf(groupPos, childPos);
        if (nd == null) return;
        dialogShower.accept(new HoloConfirmDialog(this, "状态清理",
                "本集关卡保存的全部状态将被清理，确认吗？",
                "取消", "确定", () -> clearSetStateNow(nd.id)));
    }

    /** case 3 确认后的动作（拆出来便于测试，不弹框）。 */
    void clearSetStateNow(long setId) {
        mySQLite.m_SQL.clear_S(setId);
        MyToast.showToast(this, "清理完毕！", MyToast.LENGTH_SHORT);
    }

    // ------------------------------------------------------------ case 4「删除答案...」

    /** 原版 case 4「删除答案...」：确认后异步删除本集全部答案（进度框「答案删除中...」）。 */
    void deleteSetAnswers() {
        final set_Node nd = setOf(groupPos, childPos);
        if (nd == null) return;
        dialogShower.accept(new HoloConfirmDialog(this, "提醒",
                "本集关卡的答案将全部删除，\n请做好备份！\n确定要删除答案吗？",
                "取消", "确定", () -> runWithProgress("答案删除中...", () -> {
                    deleteSetAnswersNow(nd.id);
                    return null;
                })));
    }

    /** case 4 确认后的动作（拆出来便于测试，不弹框）。 */
    void deleteSetAnswersNow(long setId) {
        mySQLite.m_SQL.del_T_Ans(setId);
    }

    // ------------------------------------------------------------ case 5「重命名...」

    /** 原版 {@code reName()}「重命名...」：预填当前名称的输入框（标题「重命名」）。 */
    void reName() {
        final set_Node nd = setOf(3, childPos);
        if (nd == null) return;
        myMaps.sFile = nd.title;

        final HoloAlertDialog dlg = HoloAlertDialog.create(this, "重命名");
        final JTextField et = HoloContent.field(240, nd.title);
        et.selectAll();
        dlg.setContentView(HoloContent.row(et));
        dlg.addButton("取消", null);
        // 原版「确定」是 setPositiveButton：监听器里**不** dismiss，靠 AlertDialog 自动关 ——
        // 也就是「校验失败也照关，只弹 Toast」。所以这里传 action 即可，关框交给 addButton。
        JButton ok = dlg.addButton("确定", () -> applyRename(nd, et.getText().trim()));
        dlg.setDefaultButton(ok);
        // ⚠️ 原版 Enter 走的**不是** positive 按钮，而是 setOnKeyListener：
        //     if (成功) { …; di.dismiss(); return true; }   // 失败则 return false → 留在原地让用户改
        // 所以这里不能复用 ok.doClick()（那会无条件关框），必须自己判成功才关。
        et.addActionListener(e -> {
            if (applyRename(nd, et.getText().trim())) dlg.dispose();
        });
        dialogShower.accept(dlg);
    }

    /**
     * 原版 {@code reName()} 的校验与落库部分：空名与重名都只弹 Toast 并让输入框留在原地。
     *
     * @return {@code true} = 改名成功（原版此时 {@code dismiss()} 对话框）
     */
    boolean applyRename(set_Node nd, String input) {
        if (nd == null) return false;
        if (input == null || input.isEmpty()) {
            MyToast.showToast(this, "名称不能为空！\n" + input, MyToast.LENGTH_SHORT);
            return false;
        }
        if (mySQLite.m_SQL.find_Set(input, nd.id) > 0) {
            MyToast.showToast(this, "此名称已经存在！\n" + input, MyToast.LENGTH_SHORT);
            return false;
        }
        mySQLite.m_SQL.set_T_T(nd.id, input);
        nd.title = input;
        refreshTree();
        return true;
    }

    // ------------------------------------------------------------ case 6「删除」

    /**
     * 原版 case 6「删除」：确认后异步删除整个关卡集（进度框「删除中...」）。
     *
     * <p>确认框正文里的括号照抄原版：开头是全角 {@code （}、结尾是半角 {@code )}。
     */
    void deleteSet() {
        final set_Node nd = setOf(3, childPos);
        if (nd == null) return;
        dialogShower.accept(new HoloConfirmDialog(this, "提醒",
                "删除关卡集，确定吗？\n（" + nd.title + ")",
                "取消", "确定", () -> runWithProgress("删除中...", () -> {
                    deleteSetNow(nd);
                    return null;
                })));
    }

    /** case 6 确认后的动作（原版 {@code deleteThread.run()} 的主体）。 */
    void deleteSetNow(set_Node nd) {
        if (nd == null) return;
        mySQLite.m_SQL.del_T(nd.id);
        myMaps.mSets3.remove(nd);
    }

    // ------------------------------------------------------------ case 7 / 8「添加关卡」

    /** 原版 case 7「添加关卡(文档)...」：{@code m_Set_id = mSets3.get(childPos).id;} 然后 {@code sel_File();} */
    void addLevelsFromDoc() {
        set_Node nd = setOf(3, childPos);
        if (nd == null) return;
        myMaps.m_Set_id = nd.id;
        sel_File();
    }

    /** 原版 case 8「添加关卡(剪切板)...」：{@code m_Set_id = mSets3.get(childPos).id;} 然后 {@code read_Plate();} */
    void addLevelsFromClip() {
        set_Node nd = setOf(3, childPos);
        if (nd == null) return;
        myMaps.m_Set_id = nd.id;
        read_Plate();
    }

    // ------------------------------------------------------------ case 9「添加比赛关卡(sokoban.ws)」

    /** 原版 case 9：{@code get_uil_dialog.xml} 的「网站 / 期号」两栏 → 下载比赛关卡。 */
    void importMatchLevels() {
        final set_Node nd = setOf(3, childPos);
        if (nd == null) return;
        dialogShower.accept(new UrlInputDialog(this, nd.id, this::submitCompetition));
    }

    /**
     * 原版 case 9「确定」/ 回车 里的那段：
     * 解析期号 → {@code url_Num}、规整 {@code myMaps.uil}（末尾补 {@code '/'}）、
     * 记下目标关卡集 → 开下载。
     */
    void submitCompetition(long setId, String uil, String numText) {
        prepareCompetition(setId, uil, numText);
        startCompetitionDownload();
    }

    /**
     * {@link #submitCompetition} 里「不开下载」的那一半 —— 只把三个状态量摆好，
     * 拆出来是为了让测试不必真的发请求 / 弹进度框。
     */
    void prepareCompetition(long setId, String uil, String numText) {
        url_Num = computeUrlNum(numText);
        myMaps.uil = normalizeUil(uil);
        myMaps.m_Set_id = setId;
    }

    /** 原版：{@code int n = Integer.parseInt(num); url_Num = n > 0 ? "?id=" + n : "";}（解析失败也取 ""）。 */
    static String computeUrlNum(String numText) {
        try {
            int n = Integer.parseInt(numText == null ? "" : numText.trim());
            return n > 0 ? "?id=" + n : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 原版：{@code myMaps.uil = uil.trim(); 末尾不是 '/' 就补一个}。
     *
     * <p>原版对空串会 {@code charAt(-1)} 抛异常；PC 端把空串也补成 {@code "/"}，
     * 之后 URL 解析失败会落到「网络错误：000」，不会崩。
     */
    static String normalizeUil(String uil) {
        String s = uil == null ? "" : uil.trim();
        if (s.isEmpty() || s.charAt(s.length() - 1) != '/') s = s + '/';
        return s;
    }

    /**
     * 原版 {@code BoxMan.MyThread} + {@code handler}：
     * {@code ProgressDialog("下载中...")} → 后台下载 → 关框 + 刷新列表 + 结果框。
     */
    void startCompetitionDownload() {
        final HoloProgressDialog pd = new HoloProgressDialog(this, "下载中...");
        SwingWorker<CompetitionResult, Void> worker = new SwingWorker<CompetitionResult, Void>() {
            @Override
            protected CompetitionResult doInBackground() {
                return fetchCompetition();
            }

            @Override
            protected void done() {
                CompetitionResult r;
                try {
                    r = get();
                } catch (Exception e) {
                    r = new CompetitionResult(false, "网络错误：000");
                }
                pd.dispose();
                onCompetitionImported(r);
            }
        };
        worker.execute();
        pd.setVisible(true);
    }

    /** 原版 {@code handler}：{@code notifyDataSetChanged()} + 标题刷新 + 结果框（「比赛信息」/「错误」）。 */
    void onCompetitionImported(CompetitionResult result) {
        refreshTree();
        new HoloMessageDialog(this, result.ok ? "比赛信息" : "错误", result.message, "确定").setVisible(true);
    }

    /**
     * 原版 {@code MyThread.run()}：{@code GET myMaps.uil + url + url_Num}。
     * HTTP 200 → 解析 JSON（{@code what = 1}）；其它状态码 → {@code "网络错误：" + code}；
     * 异常 → {@code "网络错误：000"}（都是 {@code what = 0}）。
     *
     * <p>源级别是 Java 8，只能用 {@link HttpURLConnection}（{@code java.net.http} 是 11+）。
     */
    CompetitionResult fetchCompetition() {
        try {
            URL u = new URL(myMaps.uil + COMPETITION_URL + url_Num);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code == 200) {
                return parseCompetitionJson(readAll(conn.getInputStream()));
            }
            return new CompetitionResult(false, "网络错误：" + code);
        } catch (Exception e) {
            return new CompetitionResult(false, "网络错误：000");
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** 原版 {@code Message.what} 的等价物：{@code ok == true} → 标题「比赛信息」，否则「错误」。 */
    static final class CompetitionResult {
        final boolean ok;
        final String message;

        CompetitionResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /**
     * 原版 {@code BoxMan.myJson(String)}：解析比赛关卡（json-simple）并把关卡加进
     * {@code myMaps.m_Set_id} 指向的关卡集，同时记下期号与起止日期。
     *
     * <p>与 Android 版唯一的差别：原版只 {@code catch (ParseException)}，
     * 而 {@code obj.get("id")} 取不到时会是 {@code null} → NPE。
     * PC 端一并吞掉，统一退化成默认提示语（正常接口下不可见）。
     */
    CompetitionResult parseCompetitionJson(String lvls) {
        String inf = "没找到关卡数据或比赛尚未开始！";

        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(lvls);

            // 解析string
            String m_no = obj.get("id").toString();
            String m_begin = obj.get("begin").toString();
            String m_end = obj.get("end").toString();

            myMaps.m_lstMaps.clear();  //关卡列表

            // 解析json中的关卡
            String[] keys = {"main", "extra", "extra2", "extra3"};
            for (String key : keys) {
                JSONObject level = (JSONObject) obj.get(key);
                if (level != null) {
                    myMaps.m_lstMaps.add(new mapNode(level.get("level").toString(),
                            level.get("title").toString(),
                            level.get("author").toString(), ""));
                }
            }

            if (myMaps.m_lstMaps.size() > 0) {
                inf = "第" + m_no + "期比赛关卡加载成功！\n开始：" + m_begin + "\n结束：" + m_end;
                for (int k = 0; k < myMaps.m_lstMaps.size(); k++) {
                    mySQLite.m_SQL.add_L(myMaps.m_Set_id, myMaps.m_lstMaps.get(k));   //添加的关卡库，P_id = myMaps.m_Set_id
                }
                myMaps.mMatchNo = "第" + m_no + "期比赛";
                myMaps.mMatchDate1 = m_begin;
                myMaps.mMatchDate2 = m_end;
            } else {
                inf = "第" + m_no + "期（尚未开赛）！\n开始：" + m_begin + "\n结束：" + m_end;
            }
        } catch (Exception e) {
            // 原版是 catch (ParseException) —— 见方法注释
        }

        return new CompetitionResult(true, inf);
    }

    // ------------------------------------------------------------ case 10「详细...」

    /**
     * 原版 case 10「详细...」：等价于该关卡集的「关于...」——
     * {@code get_Set(m_id)} 取标题/作者/说明，正文是「已通关数 / 关卡总数」。
     */
    void showSetAbout() {
        set_Node nd = setOf(groupPos, childPos);
        if (nd == null) return;
        myMaps.sFile = nd.title;
        dialogShower.accept(new myAbout1(this, nd.id, setAboutMessage(nd.id)));
    }

    /** case 10 里那句 {@code count_Sovled(m_id) + "/" + count_Level(m_id)}（拆出来便于测试）。 */
    String setAboutMessage(long setId) {
        mySQLite.m_SQL.get_Set(setId);   // 顺带把 J_Title / J_Author / J_Comment 装进 myMaps
        return mySQLite.m_SQL.count_Sovled(setId) + "/" + mySQLite.m_SQL.count_Level(setId);
    }

    // ------------------------------------------------------------ 导入：原版 BoxMan.sel_File / read_Plate

    /**
     * 原版 {@code BoxMan.sel_File()}「文档导入」：列出「导入/」下的关卡集文档（单选），
     * 选项是 {@code res/layout/import_dialog.xml} 的「XSB / Lurd」复选 +
     * 编码单选（自动/GBK/UTF-8）+「仅有一个关卡时，自动打开」。
     *
     * <p>导入本身走 {@link #imPort_Sets(ArrayList, int)}，落库目标是 {@code myMaps.m_Set_id}
     * （与原版 {@code mySplitLevelsFragment} 的 {@code myType == 1} 分支一致）。
     */
    void sel_File() {
        HoloChoiceDialog dlg = buildDocImportDialog();
        if (dlg != null) dialogShower.accept(dlg);
    }

    /**
     * 只把「文档导入」对话框搭好、不显示。
     *
     * @return 搭好的对话框；「导入/」下没有文档时返回 {@code null}（原版此时只弹 Toast）
     */
    HoloChoiceDialog buildDocImportDialog() {
        myMaps.newSetList();

        if (myMaps.mFile_List.size() <= 0) {
            MyToast.showToast(this, "没找到关卡集文档。", MyToast.LENGTH_SHORT);
            return null;
        }

        String[] items = myMaps.mFile_List.toArray(new String[0]);
        HoloChoiceDialog dlg = HoloChoiceDialog.selectThenOk(this, "文档导入",
                buildImportOptions(true), items, -1, which -> {
                    if (which < 0) return;                       // 原版判 m_nItemSelect >= 0
                    String setName = myMaps.mFile_List.get(which);   //选择的文档
                    myMaps.mFile_List.clear();
                    myMaps.mFile_List.add(setName);
                    imPort_Sets(myMaps.mFile_List, mySplitLevelsFragment.TYPE_FILE);  //导入文档关卡
                });
        docImportDialogForTest = dlg;
        return dlg;
    }

    /**
     * 原版 {@code BoxMan.read_Plate()}「剪切板导入」：剪切板内容放进可编辑文本框
     * （{@code import_dialog2.xml} 的 {@code im_plate}），确定后导入 {@code myMaps.m_Set_id}。
     */
    void read_Plate() {
        HoloViewDialog dlg = buildClipImportDialog();
        if (dlg != null) dialogShower.accept(dlg);
    }

    /**
     * 只把「剪切板导入」对话框搭好、不显示。
     *
     * @return 搭好的对话框；剪切板里没有关卡数据时返回 {@code null}（原版此时只弹 Toast）
     */
    HoloViewDialog buildClipImportDialog() {
        String str = myMaps.loadClipper();
        if (str == null || str.isEmpty()) {
            MyToast.showToast(this, "剪切板中没有找到关卡数据！", MyToast.LENGTH_SHORT);
            return null;
        }

        final JTextArea et = new JTextArea(str);
        et.setFont(new Font(Font.MONOSPACED, Font.PLAIN, HoloContent.TEXT_SIZE));
        et.setBackground(HoloContent.FIELD_BG);
        et.setForeground(HoloContent.TEXT);
        et.setCaretColor(HoloContent.TEXT);
        et.setBorder(new EmptyBorder(HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        JScrollPane sp = new JScrollPane(et);
        sp.setPreferredSize(new Dimension(300, 180));
        HoloContent.darkScrollBar(sp);
        clipAreaForTest = et;

        myMaps.isLurd = false;

        HoloViewDialog dlg = new HoloViewDialog(this, "剪切板导入",
                HoloContent.column(buildImportOptions(false), sp));
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            myMaps.mFile_List.clear();
            myMaps.mFile_List.add(et.getText());
            imPort_Sets(myMaps.mFile_List, mySplitLevelsFragment.TYPE_CLIPBOARD);  //导入剪切板关卡
        });
        return dlg;
    }

    /**
     * 原版 {@code import_dialog.xml} / {@code import_dialog2.xml} 共用的那几个开关：
     * 「XSB / Lurd」复选（互相兜底）+（可选的）编码单选 +「仅有一个关卡时，自动打开」。
     *
     * <p>初值照抄原版代码而非 XML：{@code m_XSB.setChecked(true)} 覆盖 XML 的
     * {@code checked="false"}（会触发监听器，所以 {@code myMaps.isXSB} 与 {@code andOpen}
     * 一起变 true）；{@code m_Open} 取 {@code myMaps.m_Sets[31]}；编码单选默认「自动」。
     */
    JComponent buildImportOptions(boolean withEncoding) {
        final JCheckBox cbXsb = HoloContent.wrapCheck("XSB", true);
        final JCheckBox cbLurd = HoloContent.wrapCheck("Lurd", myMaps.isLurd);
        cbXsb.addActionListener(e -> {
            myMaps.isXSB = cbXsb.isSelected();
            andOpen = cbXsb.isSelected();
            if (!cbXsb.isSelected() && !cbLurd.isSelected()) cbLurd.setSelected(true);
        });
        cbLurd.addActionListener(e -> {
            myMaps.isLurd = cbLurd.isSelected();
            if (!cbLurd.isSelected() && !cbXsb.isSelected()) cbXsb.setSelected(true);
        });

        final JCheckBox cbOpen = HoloContent.wrapCheck("仅有一个关卡时，自动打开", myMaps.m_Sets[31] == 1);
        cbOpen.addActionListener(e -> myMaps.m_Sets[31] = cbOpen.isSelected() ? 1 : 0);

        // 原版：m_XSB.setChecked(true) 会触发上面那个监听器
        myMaps.isXSB = true;
        andOpen = true;

        JPanel optRow = HoloContent.row(HoloContent.BAND, 0,
                Box.createHorizontalStrut(12), HoloContent.label("导入选项："),
                Box.createHorizontalStrut(16), cbXsb,
                Box.createHorizontalStrut(10), cbLurd);
        JPanel openRow = HoloContent.row(HoloContent.BAND, 0,
                Box.createHorizontalStrut(56), cbOpen);   // paddingLeft 56dp

        if (!withEncoding) {
            return HoloContent.column(optRow, HoloContent.band(HoloContent.BAND, 6), openRow);
        }

        myMaps.m_Code = 0;   // 原版：myMaps.m_Code = 0;
        ButtonGroup g = new ButtonGroup();
        JRadioButton rbAuto = HoloContent.radio("自动", true);
        JRadioButton rbGbk = HoloContent.radio("GBK", false);
        JRadioButton rbUtf8 = HoloContent.radio("UTF-8", false);
        g.add(rbAuto);
        g.add(rbGbk);
        g.add(rbUtf8);
        rbAuto.addActionListener(e -> myMaps.m_Code = 0);
        rbGbk.addActionListener(e -> myMaps.m_Code = 1);
        rbUtf8.addActionListener(e -> myMaps.m_Code = 2);

        JPanel codeRow = HoloContent.row(HoloContent.BAND, 0,
                Box.createHorizontalStrut(32), rbAuto,
                Box.createHorizontalStrut(16), rbGbk,
                Box.createHorizontalStrut(16), rbUtf8);   // 12dp paddingLeft + 20dp 占位

        return HoloContent.column(
                optRow,
                HoloContent.band(HoloContent.BAND, 6),
                codeRow,
                HoloContent.band(HoloContent.BAND, 6),
                openRow,
                HoloContent.band(HoloContent.BAND, 12));
    }

    /**
     * 原版「{@code ProgressDialog} + {@code new Thread(...)} + {@code Handler}」三件套的等价物：
     * 模态进度框 + {@link SwingWorker}，任务结束后关框并刷新列表。
     */
    private void runWithProgress(String message, Callable<Void> task) {
        final HoloProgressDialog pd = new HoloProgressDialog(this, message);
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                pd.dispose();
                refreshTree();
            }
        };
        worker.execute();
        pd.setVisible(true);
    }

    // ------------------------------------------------------------ 测试辅助（上下文菜单）

    /** 供测试读取最近一次构建的 10 项上下文菜单。 */
    JPopupMenu getContextMenuForTest() {
        return contextMenuForTest;
    }

    /** 供测试直接指定上下文菜单对应的条目位置（原版 {@code groupPos} / {@code childPos}）。 */
    void setContextPosition(int groupPos, int childPos) {
        this.groupPos = groupPos;
        this.childPos = childPos;
    }

    /** 供测试读取最近一次「文档导入」搭好的对话框。 */
    HoloChoiceDialog getDocImportDialogForTest() {
        return docImportDialogForTest;
    }

    /** 供测试读取最近一次「剪切板导入」的文本框。 */
    JTextArea getClipAreaForTest() {
        return clipAreaForTest;
    }

    /** 供测试读取最近一次「导出...」搭好的对话框。 */
    HoloAlertDialog getExportSetDialogForTest() {
        return exportSetDialogForTest;
    }

    /** 供测试读取原版 {@code url_Num}。 */
    String getUrlNum() {
        return url_Num;
    }

    // ------------------------------------------------------------ 渲染

    private class RowRenderer extends JLabel implements TreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object userObject = value instanceof DefaultMutableTreeNode
                    ? ((DefaultMutableTreeNode) value).getUserObject() : null;

            setOpaque(true);
            setFont(ITEM_FONT);
            setBackground(selected ? SELECT_BG : LIST_BG);
            setHorizontalAlignment(SwingConstants.LEFT);

            if (userObject instanceof GroupItem) {
                GroupItem group = (GroupItem) userObject;
                setText(group.title + " 【" + group.count + "】");
                setForeground(GROUP_FG);
                setIcon(expanded ? indicatorExpanded : indicatorCollapsed);
                setIconTextGap(INDICATOR_TEXT_GAP);
                setBorder(new EmptyBorder(0, INDICATOR_LEFT, 0, 0));
            } else if (userObject instanceof SetItem) {
                SetItem item = (SetItem) userObject;
                setText(item.title + " （" + item.solved + "/" + item.total + "）");
                setForeground(CHILD_FG);
                setIcon(null);
                setBorder(new EmptyBorder(0, ITEM_PADDING_LEFT, 0, 0));
            } else {
                setText("");
                setForeground(CHILD_FG);
                setIcon(null);
                setBorder(new EmptyBorder(0, 0, 0, 0));
            }
            return this;
        }
    }

    /** 深色细滚动条，贴近原版 ExpandableListView 的滚动指示 */
    private static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(0x00, 0x60, 0x60);
            this.trackColor = LIST_BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    // ------------------------------------------------------------ 溢出菜单（原版 res/menu/main.xml）

    private void buildOverflowMenu() {
        // 原版 res/menu/main.xml：menu_set → sel_Set()、menu_exp_ans → sel_Set2()
        actionBar.addAction("导入...", this::sel_Set);
        actionBar.addAction("导出...", this::sel_Set2);
        actionBar.addAction("最近推过的关卡", this::openRecentLevels);
        actionBar.addAction("关卡查询", this::showQueryDialog);
        actionBar.addAction("新建关卡集...", this::createNewSet);
        actionBar.addAction("创编关卡★", this::openEditor);
        actionBar.addAction("图像识别", this::openRecognition);
        actionBar.addAction("比赛答案提交列表", () -> new mySubmitList().setVisible(true));
        actionBar.addAction("帮助", () -> new Help(0).setVisible(true));
        actionBar.addAction("关于", this::showAboutDialog);
    }

    /**
     * 原版 {@code menu_query}：弹「关卡查询」对话框，查询完成后把结果当作一个虚拟关卡集
     * 进入关卡网格浏览（对应原版 {@code BoxMan.onQueryDone()}）。
     */
    private void showQueryDialog() {
        new QueryDialog(this, this::onQueryDone).setVisible(true);
    }

    /** 原版 {@code BoxMan.onQueryDone(ArrayList&lt;mapNode&gt;)}。 */
    private void onQueryDone(java.util.List<mapNode> results) {
        if (results == null || results.isEmpty()) {
            MyToast.showToast(this, "没有找到！", MyToast.LENGTH_SHORT);
            return;
        }
        if (myMaps.curJi) {
            return;
        }
        myMaps.curJi = true;

        myMaps.curMap = null;
        myMaps.m_lstMaps.clear();
        myMaps.m_Sets[0] = 0;
        myMaps.m_Sets[1] = 0;
        myMaps.m_Set_id = -1;
        myMaps.sFile = "关卡查询";
        myMaps.m_lstMaps.addAll(results);
        myMaps.J_Title = myMaps.sFile;
        myMaps.J_Author = "";
        myMaps.J_Comment = "";

        myGridView grid = new myGridView(-1, myMaps.sFile);
        grid.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                myMaps.curJi = false;
            }
        });
        grid.setVisible(true);
    }

    /** 原版 menu_recent：mySQLite.get_Recent() 后进入关卡网格浏览 */
    private void openRecentLevels() {
        if (myMaps.curJi) return;
        myMaps.curJi = true;

        mySQLite.m_SQL.get_Recent();
        if (myMaps.m_lstMaps.size() < 1) {
            myMaps.curJi = false;
            MyToast.showToast(this, "未找到关卡！", MyToast.LENGTH_SHORT);
            return;
        }

        myMaps.m_Sets[0] = 0;
        myMaps.m_Sets[1] = 0;
        myMaps.m_Set_id = -1;
        myMaps.sFile = "最近推过的关卡";
        myMaps.J_Title = myMaps.sFile;
        myMaps.J_Author = "";
        myMaps.J_Comment = "";

        myGridView grid = new myGridView(-1, myMaps.sFile);
        grid.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                myMaps.curJi = false;
            }
        });
        grid.setVisible(true);
    }

    /** 原版 menu_NewSet：新增关卡集 */
    private void createNewSet() {
        String input = JOptionPane.showInputDialog(this, "关卡集名称", myMaps.getNewSetName());
        if (input == null) return;
        input = input.trim();
        if (input.isEmpty()) {
            MyToast.showToast(this, "无效的名称！" + input, MyToast.LENGTH_SHORT);
            return;
        }
        if (myMaps.mSets3.size() > 0 && mySQLite.m_SQL.find_Set(input, myMaps.mSets3.get(0).id) > 0) {
            MyToast.showToast(this, "此名称已经存在！\n" + input, MyToast.LENGTH_SHORT);
            return;
        }
        try {
            long new_ID = mySQLite.m_SQL.add_T(3, input, "", "");
            set_Node nd = new set_Node();
            nd.id = new_ID;
            nd.title = input;
            myMaps.mSets3.add(nd);
            refreshTree();
            MyToast.showToast(this, "新关卡集创建成功！\n" + input, MyToast.LENGTH_SHORT);
        } catch (Exception e) {
            MyToast.showToast(this, "未知原因，创建失败！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 menu_builder：创编关卡 */
    private void openEditor() {
        if (myMaps.curJi) return;
        myMaps.curJi = true;
        myMaps.read_DirBuilder();

        myGridView grid = new myGridView(-1, myMaps.sFile);
        grid.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                myMaps.curJi = false;
            }
        });
        grid.setVisible(true);
    }

    /** 原版 menu_recog：图像识别 */
    private void openRecognition() {
        // 原版 BoxMan.java:617 —— 进入列表前先按当前位置刷一次截图列表
        myMaps.edPicList(myMaps.picDir());
        new myPicListView().setVisible(true);
    }

    // ------------------------------------------------------------ 导入 / 导出（原版 sel_Set / sel_Set2）

    /**
     * 原版 {@code BoxMan.sel_Set()}：列出「导入/」目录下的关卡集文档，多选后批量导入。
     *
     * <p>布局对应 {@code res/layout/import_dialog3.xml}：
     * 6dp 色条 → {@code #334455} 分组条（{@code 关卡集：} + 右对齐「全选」）→ 190dp 多选列表
     * → 8dp {@code #445566} 色条 → {@code 选项：} + XSB/Lurd → 6dp 色条
     * → 编码单选（自动/GBK/UTF-8，{@code paddingLeft 12dp}）→ 12dp 色条。
     *
     * <p>原版代码里 {@code m_All.setChecked(false)} 覆盖了 XML 的 {@code checked="true"}，
     * 而 {@code m_XSB.setChecked(true)} 覆盖了 XML 的 {@code checked="false"} ——
     * 所以有效初值是「全选 = 否、XSB = 是、Lurd = 否、自动 = 是」。
     */
    void sel_Set() {
        HoloAlertDialog dlg = buildImportDialog();
        if (dlg != null) {
            dlg.setVisible(true);
        }
    }

    /**
     * 只把「导入」对话框搭好、不显示 —— 与原版 {@code sel_Set()} 的差异仅在最后那一次
     * {@code create().show()}。拆出来是为了让测试能直接检查组件树与初值。
     *
     * @return 搭好的对话框；若「导入/」下没有可导入的文档则返回 {@code null}（原版此时只弹 Toast）
     */
    HoloAlertDialog buildImportDialog() {
        myMaps.newSetList();

        if (myMaps.mFile_List.size() <= 0) {
            MyToast.showToast(this, "没找到关卡集文档。", MyToast.LENGTH_SHORT);
            return null;
        }

        // 原版 m_setName = findViewById(R.id.im_sets)，CHOICE_MODE_MULTIPLE + 全部 setItemChecked(false)
        JList<String> sets = createSetList(myMaps.mFile_List.toArray(new String[0]));
        sets.clearSelection();
        myMaps.m_setName = sets;

        final JCheckBox cbAll = HoloContent.check32("全选", false, 80);
        cbAll.addActionListener(e -> {
            if (cbAll.isSelected()) sets.setSelectionInterval(0, sets.getModel().getSize() - 1);
            else sets.clearSelection();
        });

        final JCheckBox cbXsb = HoloContent.wrapCheck("XSB", myMaps.isXSB);
        final JCheckBox cbLurd = HoloContent.wrapCheck("Lurd", myMaps.isLurd);
        cbXsb.addActionListener(e -> {
            myMaps.isXSB = cbXsb.isSelected();
            if (!cbXsb.isSelected() && !cbLurd.isSelected()) cbLurd.setSelected(true);
        });
        cbLurd.addActionListener(e -> {
            myMaps.isLurd = cbLurd.isSelected();
            if (!cbLurd.isSelected() && !cbXsb.isSelected()) cbXsb.setSelected(true);
        });

        myMaps.m_Code = 0;   // 原版：myMaps.m_Code = 0;
        ButtonGroup codeGroup = new ButtonGroup();
        JRadioButton rbAuto = HoloContent.radio("自动", true);
        JRadioButton rbGbk = HoloContent.radio("GBK", false);
        JRadioButton rbUtf8 = HoloContent.radio("UTF-8", false);
        codeGroup.add(rbAuto);
        codeGroup.add(rbGbk);
        codeGroup.add(rbUtf8);
        rbAuto.addActionListener(e -> myMaps.m_Code = 0);
        rbGbk.addActionListener(e -> myMaps.m_Code = 1);
        rbUtf8.addActionListener(e -> myMaps.m_Code = 2);

        JPanel optionRow = HoloContent.row(HoloContent.BAND, 0,
                HoloContent.label("选项："), Box.createHorizontalStrut(16), cbXsb,
                Box.createHorizontalStrut(10), cbLurd);

        JPanel codeRow = new JPanel();
        codeRow.setLayout(new BoxLayout(codeRow, BoxLayout.X_AXIS));
        codeRow.setBackground(HoloContent.BAND);
        codeRow.setOpaque(true);
        codeRow.setBorder(new EmptyBorder(0, 12, 0, 0));   // paddingLeft 12dp
        codeRow.add(Box.createHorizontalStrut(20));
        codeRow.add(rbAuto);
        codeRow.add(Box.createHorizontalStrut(16));
        codeRow.add(rbGbk);
        codeRow.add(Box.createHorizontalStrut(16));
        codeRow.add(rbUtf8);

        JComponent content = HoloContent.column(
                HoloContent.band(HoloContent.BAND, 6),
                HoloContent.headRow("关卡集：", 100, cbAll),
                wrapSetList(sets),
                HoloContent.band(new Color(0x44, 0x55, 0x66), 8),
                optionRow,
                HoloContent.band(HoloContent.BAND, 6),
                codeRow,
                HoloContent.band(HoloContent.BAND, 12));

        andOpen = false;   // 原版：andOpen = false;

        HoloAlertDialog dlg = HoloAlertDialog.create(this, "导入");
        dlg.setContentView(content);
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            imPort_Sets(myMaps.mFile_List, mySplitLevelsFragment.TYPE_FILE_LIST);   // 导入关卡集
            refreshTree();                                                          // expAdapter.notifyDataSetChanged()
        });
        return dlg;
    }

    /**
     * 原版 {@code BoxMan.sel_Set2()}：列出全部关卡集，多选后批量导出到「导出/」目录。
     *
     * <p>布局对应 {@code res/layout/export_dialog3.xml}：
     * 6dp 色条 → {@code #334455} 分组条（{@code 关卡集：} + 右对齐「仅答案关卡」「全选」）
     * → 190dp 多选列表 → 8dp {@code #445566} 色条 → {@code 选项：} + 含答案/答案含备注
     * → 6dp 色条 → 80dp 占位 + 覆盖同名文档 → 6dp 色条。
     *
     * <p>⚠️ 原版有个自带的怪癖，这里照抄不改：{@code ex_ans}「仅答案关卡」的初值来自 XML
     * （{@code checked="true"}），而它的 {@code OnCheckedChangeListener} 才会把状态写进
     * {@code myMaps.isXSB}。若上一次导入把 XSB 取消勾选过，{@code myMaps.isXSB} 就是 false，
     * 此时打开导出对话框、不再动这个框，「仅答案关卡」虽然显示勾选却不会被导出。
     */
    void sel_Set2() {
        HoloAlertDialog dlg = buildExportDialog();
        if (dlg != null) {
            dlg.setVisible(true);
        }
    }

    /**
     * 只把「导出」对话框搭好、不显示 —— 与 {@link #buildImportDialog()} 同一套路，
     * 供测试检查初值与组件树。
     *
     * @return 搭好的对话框；若库里一个关卡集都没有则返回 {@code null}
     */
    HoloAlertDialog buildExportDialog() {
        myMaps.isLurd = false;   // 原版：myMaps.isLurd = false;

        ArrayList<Long> ids = new ArrayList<Long>();
        java.util.List<String> titles = myMaps.getData(ids);
        if (titles.isEmpty()) {
            MyToast.showToast(this, "没找到关卡集。", MyToast.LENGTH_SHORT);
            return null;
        }

        // 原版 m_setName = findViewById(R.id.ex_sets)，全部 setItemChecked(k, true)
        JList<String> sets = createSetList(titles.toArray(new String[0]));
        sets.setSelectionInterval(0, titles.size() - 1);
        myMaps.m_setName = sets;

        final JCheckBox cbAll = HoloContent.check32("全选", true, 80);
        cbAll.addActionListener(e -> {
            if (cbAll.isSelected()) sets.setSelectionInterval(0, sets.getModel().getSize() - 1);
            else sets.clearSelection();
        });

        final JCheckBox cbAns = HoloContent.wrapCheck("仅答案关卡", true);
        cbAns.addActionListener(e -> myMaps.isXSB = cbAns.isSelected());

        final JCheckBox cbComment = HoloContent.wrapCheck("答案含备注", false);
        final JCheckBox cbLurd = HoloContent.wrapCheck("含答案", false);
        cbLurd.addActionListener(e -> {
            myMaps.isLurd = cbLurd.isSelected();
            cbComment.setSelected(cbLurd.isSelected());
        });
        cbComment.addActionListener(e -> {
            if (cbComment.isSelected()) cbLurd.setSelected(true);
            myMaps.isComment = cbComment.isSelected();   // 是否导出答案的备注信息
        });

        final JCheckBox cbReWrite = HoloContent.wrapCheck("覆盖同名文档", true);   // 原版 m_ReWrite.setChecked(true)

        JPanel optionRow = HoloContent.row(HoloContent.BAND, 0,
                HoloContent.label("选项："), Box.createHorizontalStrut(10), cbLurd,
                Box.createHorizontalStrut(16), cbComment);

        JPanel rewriteRow = HoloContent.row(HoloContent.BAND, 0,
                Box.createHorizontalStrut(EXPORT_LEADING_GAP), cbReWrite);

        JComponent content = HoloContent.column(
                HoloContent.band(HoloContent.BAND, 6),
                HoloContent.headRow("关卡集：", 100, cbAns, cbAll),
                wrapSetList(sets),
                HoloContent.band(new Color(0x44, 0x55, 0x66), 8),
                optionRow,
                HoloContent.band(HoloContent.BAND, 6),
                rewriteRow,
                HoloContent.band(HoloContent.BAND, 6));

        HoloAlertDialog dlg = HoloAlertDialog.create(this, "导出");
        dlg.setContentView(content);
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            // 原版：被勾选的关卡集取正 id，未勾选的取负 id，交给 exPort_Sets 按符号过滤
            long[] setsArr = new long[ids.size()];
            for (int k = 0; k < setsArr.length; k++) {
                setsArr[k] = sets.isSelectedIndex(k) ? ids.get(k) : -ids.get(k);
            }
            dlg.dispose();
            exPort_Sets(setsArr, myMaps.isXSB, myMaps.isLurd, cbReWrite.isSelected());
        });
        return dlg;
    }

    /** 原版 {@code imPort_Sets(ArrayList&lt;String&gt;, int)}：异步导入。 */
    void imPort_Sets(ArrayList<String> filelist, int act) {
        if (mDialog == null) {
            mDialog = new mySplitLevelsFragment(this, this::onSplitDone, act, filelist);
            mDialog.show();
            mDialog = null;
        }
    }

    /** 原版 {@code exPort_Sets(long[], boolean, boolean, boolean)}：异步导出。 */
    void exPort_Sets(long[] sets, boolean andAns, boolean isLurd, boolean isReWrite) {
        if (mDialog3 == null) {
            mDialog3 = new myExportFragment(this, this::onExportDone, andAns, isLurd, isReWrite, sets);
            mDialog3.show();
            mDialog3 = null;
        }
    }

    /** 原版 {@code BoxMan.onSplitDone(String)}：导入结束后的刷新与统计提示。 */
    public void onSplitDone(String inf) {
        refreshTree();
        updateActionBarTitle();

        // 导入统计提示
        if (andOpen && myMaps.m_Sets[31] == 1 && myMaps.m_Nums[2] == 1) {
            // 导入后打开（长按关卡集的导入，仅导入了一个有效的关卡时）
            mySQLite.m_SQL.get_Set(myMaps.m_Set_id);
            mySQLite.m_SQL.get_Last_Level(myMaps.m_Set_id);   // 取得刚刚添加的关卡到"关卡列表"

            if (0 == myMaps.m_Nums[3]) {   // 关卡有效时
                if (0 < myMaps.m_Nums[1]) {
                    MyToast.showToast(this, "重复或无效的答案未导入！", MyToast.LENGTH_SHORT);
                }
                myMaps.iskinChange = false;
                myMaps.sFile = "关卡导入";
                myMaps.curMap = myMaps.m_lstMaps.get(0);
                new myGameView().setVisible(true);
            } else {
                new HoloMessageDialog(this, "信息", inf, "确定").setVisible(true);
            }
            andOpen = false;
        } else {   // 常规的导入（一般为导入关卡集）
            if (1 == myMaps.m_Nums[2] && 0 == myMaps.m_Nums[3]
                    && (0 == myMaps.m_Nums[0] || 0 < myMaps.m_Nums[0] && 0 == myMaps.m_Nums[1])) {
                // 成功导入一个有效关卡且答案也没有差错时，简单提示即可
                MyToast.showToast(this, "导入成功！", MyToast.LENGTH_SHORT);
            } else {
                new HoloMessageDialog(this, "信息：", inf, "确定").setVisible(true);
            }
        }
    }

    /** 原版 {@code BoxMan.onExportDone(String)}。 */
    public void onExportDone(String inf) {
        new HoloMessageDialog(this, "已存入“导出/”文件夹", inf, "确定").setVisible(true);
    }

    /**
     * 原版 {@code ListView}（{@code CHOICE_MODE_MULTIPLE}）的等价物：
     * {@link HoloContent#itemList} 给的是原版「选中 {@code #0088aa} / 未选中 {@code #363636}」
     * 的观感，这里再加上 {@code android:padding="4dp"} 与多选模式。
     */
    private static JList<String> createSetList(String[] items) {
        JList<String> list = HoloContent.itemList(items);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(HoloContent.BAND);
        list.setBorder(new EmptyBorder(HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        return list;
    }

    /** 给关卡集列表套上固定 190dp 高的滚动面板（原版 {@code android:layout_height="190dp"}）。 */
    private static JScrollPane wrapSetList(JList<String> list) {
        JScrollPane sp = HoloContent.scroll(list);
        HoloContent.darkScrollBar(sp);
        Dimension d = new Dimension(0, SET_LIST_HEIGHT);
        sp.setPreferredSize(d);
        sp.setMinimumSize(d);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, SET_LIST_HEIGHT));
        return sp;
    }

    public int importLevelFile(File file) {
        return importLevelFile(file, false);
    }

    public int importLevelFile(File file, boolean silent) {
        String fileName = file.getName();
        int dotIdx = fileName.lastIndexOf('.');
        String setTitle = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        try {
            String encode = myMaps.getTxtEncode(new java.io.FileInputStream(file));
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), encode));
            return importLevelReader(reader, resolveSetId(setTitle), silent);
        } catch (Throwable ex) {
            if (!silent) {
                JOptionPane.showMessageDialog(this, "导入关卡文件失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            return 0;
        }
    }

    /**
     * 导入到<b>指定</b>关卡集 —— 原版 {@code imPort_Sets()} 的落库目标始终是
     * {@code myMaps.m_Set_id}，而不是「按文档名找/建同名关卡集」。
     *
     * <p>{@code myGridView} 的「添加关卡(文档)...」以及 {@code BoxMan} 上下文菜单的
     * case 7 都走这条。
     */
    public int importLevelFileInto(File file, long setId, boolean silent) {
        try {
            String encode = myMaps.getTxtEncode(new java.io.FileInputStream(file));
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), encode));
            return importLevelReader(reader, setId, silent);
        } catch (Throwable ex) {
            if (!silent) {
                JOptionPane.showMessageDialog(this, "导入关卡文件失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            return 0;
        }
    }

    /**
     * 从一段文本导入 —— 原版「剪切板导入」走的就是这条路，
     * PC 侧原先把入口整个砍掉了（见 PORTING_AUDIT 2.4）。
     */
    public int importLevelText(String text, String setTitle, boolean silent) {
        try {
            return importLevelReader(
                    new java.io.BufferedReader(new java.io.StringReader(text)),
                    resolveSetId(setTitle), silent);
        } catch (Throwable ex) {
            if (!silent) {
                JOptionPane.showMessageDialog(this, "导入失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            return 0;
        }
    }

    /** 同 {@link #importLevelText(String, String, boolean)}，但落到指定的关卡集。 */
    public int importLevelTextInto(String text, long setId, boolean silent) {
        try {
            return importLevelReader(
                    new java.io.BufferedReader(new java.io.StringReader(text)), setId, silent);
        } catch (Throwable ex) {
            if (!silent) {
                JOptionPane.showMessageDialog(this, "导入失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            return 0;
        }
    }

    /** 按「关卡集名」找集，找不到就在扩展组新建一个（原版 {@code imPort_Sets()} 的文档分支）。 */
    private long resolveSetId(String setTitle) {
        long targetSetId = mySQLite.m_SQL.find_Set(setTitle);
        if (targetSetId <= 0) {
            targetSetId = mySQLite.m_SQL.add_T(3, setTitle, "", "");
        }
        return targetSetId;
    }

    /**
     * 导入的核心：按原版 {@code imPort_Sets()} 的口径逐行解析一个关卡文档
     * （XSB + Title/Author/Comment/Comment_end + Solution）。
     *
     * @param targetSetId 目标关卡集 id（原版就是 {@code myMaps.m_Set_id}）
     * @return 成功导入的关卡数
     */
    private int importLevelReader(java.io.BufferedReader reader, long targetSetId, boolean silent)
            throws Exception {
            if (targetSetId <= 0) {
                if (!silent) JOptionPane.showMessageDialog(this, "创建关卡集失败！", "错误", JOptionPane.ERROR_MESSAGE);
                return 0;
            }

            StringBuilder g_Map = new StringBuilder();      //关卡地图
            StringBuilder g_Title = new StringBuilder();    //标题
            StringBuilder g_Author = new StringBuilder();   //作者
            StringBuilder g_Comment = new StringBuilder();  //"注释"
            StringBuilder sSolution = new StringBuilder();  //答案
            mapNode nd = null;
            long id;

            boolean flg = false;   //是否 XSB
            byte flg2 = 0;  //是否 Comment
            boolean flg3 = false;  //是否答案
            byte flg4 = 0;  //是否开始了 Title
            byte flg5 = 0;  //是否开始了 author
            int importedCount = 0;
            int num0 = 0;

            String line;
            while (true) {
                line = reader.readLine();
                if (line == null || myMaps.isXSB(line)) {
                    if (!flg || line == null) {
                        if (line == null && g_Map.length() <= 0) break;
                        num0++;
                        if (num0 > 1) {
                            if (nd == null) {
                                nd = new mapNode(g_Map.toString(), g_Title.toString(), g_Author.toString(), g_Comment.toString());
                            }
                            if (!nd.Title.equals("无效关卡") || nd.Cols != 2 || nd.Rows != 1) {
                                id = mySQLite.m_SQL.add_L(targetSetId, nd);
                                if (id > 0) importedCount++;
                            }
                        }
                        if (sSolution.length() > 0) {
                            mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                        }
                        if (line == null) break;

                        if (num0 > 1) {
                            g_Map = new StringBuilder();
                            g_Title = new StringBuilder();
                            g_Author = new StringBuilder();
                            g_Comment = new StringBuilder();
                            sSolution = new StringBuilder();
                            flg3 = false;
                            flg2 = 0;
                            flg4 = 0;
                            flg5 = 0;
                            nd = null;
                        }
                        flg = true;
                    }
                    if (g_Map.length() > 0) g_Map.append('\n');
                    g_Map.append(line);
                } else if (flg2 == 0 && line.trim().toLowerCase().startsWith("title:") && flg4++ == 0) {
                    g_Title.append(line.substring(line.indexOf(":") + 1).trim());
                    flg = false;
                    flg3 = false;
                } else if (flg2 == 0 && line.trim().toLowerCase().startsWith("author:") && flg5++ == 0) {
                    g_Author.append(line.substring(line.indexOf(":") + 1).trim());
                    flg = false;
                    flg3 = false;
                } else if (line.trim().toLowerCase().startsWith("solution")) {
                    if (sSolution.length() > 0) {
                        if (nd == null) {
                            nd = new mapNode(g_Map.toString(), g_Title.toString(), g_Author.toString(), g_Comment.toString());
                        }
                        mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                        sSolution = new StringBuilder();
                    }
                    if (line.indexOf(":") >= 0) {
                        sSolution.append(line.substring(line.indexOf(":") + 1).trim());
                    } else if (line.indexOf(")") >= 0) {
                        sSolution.append(line.substring(line.indexOf(")") + 1).trim());
                    }
                    if (flg2 > 0) flg2++;
                    flg = false;
                    flg3 = true;
                } else if (line.trim().toLowerCase().startsWith("comment-end:") ||
                        line.trim().toLowerCase().startsWith("comment_end:")) {
                    if (flg2 > 0) flg2++;
                } else if (line.trim().toLowerCase().startsWith("comment:") && flg2++ == 0) {
                    flg3 = false;
                    flg = false;
                    line = line.substring(line.indexOf(":") + 1).trim();
                    if (!line.isEmpty()) g_Comment.append(line);
                } else if (flg2 != 1 && (line.indexOf(';') == 0 || line.matches("\\s*"))) {
                    flg = false;
                } else if (flg2 == 1) {
                    if (!g_Comment.toString().isEmpty()) g_Comment.append('\n');
                    g_Comment.append(line);
                } else if (flg3) {
                    sSolution.append(line);
                } else {
                    flg = false;
                }
            }
            reader.close();

            refreshTree();
            if (!silent) {
                String setTitle = myMaps.sFile != null ? myMaps.sFile : String.valueOf(targetSetId);
                JOptionPane.showMessageDialog(this, "成功导入关卡集: " + setTitle + "\n共导入 " + importedCount + " 个关卡", "导入成功", JOptionPane.INFORMATION_MESSAGE);
            }
            return importedCount;
    }

    // ------------------------------------------------------------ 刷新

    /**
     * 原版 {@code expAdapter.notifyDataSetChanged()} + {@code setTitle(...)} 的合并实现：
     * 重新读库、重建树、回到记住的组别、刷新标题。
     *
     * <p>包内可见是为了让测试能在直接调用「不弹框」的动作方法后确认列表已同步。
     */
    void refreshTree() {
        loadAllSets();
        // 原版 notifyDataSetChanged() 后重新展开 myMaps.m_Sets[0] 所在的组别
        levelTree.setModel(buildTreeModel());
        expandRememberedGroup();
        updateActionBarTitle();
        revalidate();
        repaint();
    }

    private void showAboutDialog() {
        new myAbout(this).setVisible(true);
    }

    // ------------------------------------------------------------ 列表条目数据

    /** 一级条目：关卡组别（原版 groups[groupPosition] + " 【" + size + "】"） */
    private static class GroupItem {
        final String title;
        final int count;

        GroupItem(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    /** 二级条目：关卡集（原版 title + " （" + solved + "/" + total + "）"） */
    private static class SetItem {
        final long id;
        final String title;
        final long solved;
        final long total;

        SetItem(long id, String title, long solved, long total) {
            this.id = id;
            this.title = title;
            this.solved = solved;
            this.total = total;
        }
    }

    // ------------------------------------------------------------ 测试辅助

    /** 供测试读取当前列表的文本行（与原版 getGroupView / getChildView 的拼接规则一致） */
    public java.util.List<String> getVisibleRowTexts() {
        java.util.List<String> rows = new ArrayList<String>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) levelTree.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) root.getChildAt(i);
            GroupItem group = (GroupItem) groupNode.getUserObject();
            rows.add(group.title + " 【" + group.count + "】");
            if (!levelTree.isExpanded(new TreePath(groupNode.getPath()))) continue;
            for (int j = 0; j < groupNode.getChildCount(); j++) {
                SetItem item = (SetItem) ((DefaultMutableTreeNode) groupNode.getChildAt(j)).getUserObject();
                rows.add(item.title + " （" + item.solved + "/" + item.total + "）");
            }
        }
        return rows;
    }

    public myActionBar getActionBar() {
        return actionBar;
    }

    public JTree getLevelTree() {
        return levelTree;
    }

    /** 供测试断言背景色（对应 main.xml 的 #004040） */
    public Color getListBackground() {
        return LIST_BG;
    }
}
