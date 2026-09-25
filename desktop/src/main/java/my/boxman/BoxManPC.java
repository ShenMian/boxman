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
import java.io.File;
import java.util.ArrayList;
import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.UiWindow;

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
    /** {@code export_dialog3.xml} 里「覆盖同名文档」前的 80dp 占位。 */
    private static final int EXPORT_LEADING_GAP = 80;

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

        // 原版 onChildClick：单击关卡集条目即进入关卡网格浏览
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object last = path.getLastPathComponent();
                if (!(last instanceof DefaultMutableTreeNode)) return;
                Object userObject = ((DefaultMutableTreeNode) last).getUserObject();
                if (userObject instanceof SetItem) {
                    SetItem item = (SetItem) userObject;
                    openSet(item.id, item.title);
                }
            }
        });

        return tree;
    }

    private void openSet(long setId, String setTitle) {
        // 原版 browLevels(): 确保关卡集进入一次（点击太快可能进入两次）
        if (myMaps.curJi) return;
        myMaps.curJi = true;

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
        myMaps.edPicList(myMaps.sRoot + myMaps.myPathList[myMaps.m_Sets[36]]);
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
            return importLevelReader(reader, setTitle, silent);
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
                    new java.io.BufferedReader(new java.io.StringReader(text)), setTitle, silent);
        } catch (Throwable ex) {
            if (!silent) {
                JOptionPane.showMessageDialog(this, "导入失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
            return 0;
        }
    }

    /**
     * 导入的核心：按原版 {@code imPort_Sets()} 的口径逐行解析一个关卡文档
     * （XSB + Title/Author/Comment/Comment_end + Solution）。
     *
     * @param setTitle 目标关卡集名；不存在则新建
     * @return 成功导入的关卡数
     */
    private int importLevelReader(java.io.BufferedReader reader, String setTitle, boolean silent)
            throws Exception {
            long targetSetId = mySQLite.m_SQL.find_Set(setTitle);
            if (targetSetId <= 0) {
                targetSetId = mySQLite.m_SQL.add_T(3, setTitle, "", "");
            }
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
                JOptionPane.showMessageDialog(this, "成功导入关卡集: " + setTitle + "\n共导入 " + importedCount + " 个关卡", "导入成功", JOptionPane.INFORMATION_MESSAGE);
            }
            return importedCount;
    }

    // ------------------------------------------------------------ 刷新

    private void refreshTree() {
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
