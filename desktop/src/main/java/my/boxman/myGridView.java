package my.boxman;

import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.HoloViewDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import my.boxman.compat.UiWindow;

/**
 * 关卡网格界面 —— 对应原版 my.boxman.myGridView + res/layout/my_grid_view.xml。
 *
 * 结构（自上而下，与原版布局一一对应）：
 *   1) ActionBar（style.xml #0083C5，48dp）
 *        · 返回折角   ← actionBar.setDisplayHomeAsUpEnabled(true)
 *        · 标题       ← actionBar.setTitle(...)，由 my_SetTitle() 决定内容
 *        · 动作项     ← res/menu/levels.xml 中 showAsAction="always" 的 ╋ / 顶 / 底
 *        · 溢出菜单   ← levels.xml 其余项，可见性由 setMenu() 决定
 *   2) 关卡集标题条（my_grid_view.xml 顶部 LinearLayout，#ff555555）
 *        · 8dp 占位 + m_gridTitleView（14sp，padding 4dp）→ 文本左边界实测 ≈ 12dp
 *        · 仅当 sFile 不属于 {创编关卡, 关卡查询, 相似关卡, 最近推过的关卡} 时可见
 *   3) GridView（#ff000000）
 *        · columnWidth="60dip"、numColumns="auto_fit"、stretchMode="columnWidth"
 *        · verticalSpacing="10dip"、horizontalSpacing="5px"
 *        · 370dp 宽下自动得到 6 列，列宽 = (可用宽 - 5*间距) / 6 ≈ 60dp
 *
 * 单元（my_grid_view_item.xml）：
 *   竖向 LinearLayout = ImageView(60dip, layout_margin=5px) + TextView(gravity=center, 14sp)
 *   网格模式下 ImageView 的宽高被改写为 mGridView.getColumnWidth()（与列同宽）。
 *   文字背景：已通关 #ff004700（绿）、未通关 #ff151515、多选选中 #ffa06300（黄）。
 *
 * 实测换算（原版截图 1260x2844，density = 3.4；桌面端约定 1dp = 1px）：
 *   标题条高 89px ≈ 26dp → 取 27px；行距 310px ≈ 91dp；
 *   单元高 = 2 + 60 + 2 + 18 = 82，加 verticalSpacing 10 → 92（实测 91）。
 */
public class myGridView extends JFrame {

    // ---------------------------------------------------------------- 颜色（原版资源实测）
    private static final Color GRID_BG = new Color(0x000000);          // #ff000000
    private static final Color HEADER_BG = new Color(0x555555);        // #ff555555
    private static final Color HEADER_FG = new Color(0xBFBFBF);        // 标题条文字实测平台值 191
    private static final Color ITEM_FG = new Color(0xBFBFBF);          // 单元文字实测平台值 191
    private static final Color ITEM_BG = new Color(0x151515);          // 未通关
    private static final Color ITEM_BG_SOLVED = new Color(0x004700);   // 已通关（绿）
    private static final Color ITEM_BG_SELECT = new Color(0xA06300);   // 多选选中（黄）

    // ---------------------------------------------------------------- 尺寸（原版 dp/px 资源）
    private static final int COLUMN_WIDTH_DP = 60;   // android:columnWidth="60dip"
    private static final int V_SPACING = 10;         // android:verticalSpacing="10dip"
    private static final int H_SPACING = 2;          // android:horizontalSpacing="5px" ≈ 1.47dp
    private static final int IMAGE_MARGIN = 2;       // android:layout_margin="5px" ≈ 1.47dp
    private static final int TEXT_ROW_HEIGHT = 18;   // 14sp 文本行高实测 62px ≈ 18.2dp
    private static final int HEADER_HEIGHT = 27;     // 实测 89px ≈ 26.2dp
    private static final int HEADER_SPACER_WIDTH = 8;  // my_grid_view.xml 中的 8dp 占位 TextView
    private static final int HEADER_TITLE_PADDING = 4; // m_gridTitleView android:padding="4dp"
    private static final int TEXT_SIZE = 14;         // android:textSize="14sp"

    private static final String[] SPECIAL_TITLES = {"创编关卡", "关卡查询", "相似关卡", "最近推过的关卡"};

    // levels.xml 顺序：0 ╋ / 1 顶 / 2 底 / 3 定位… / 4 多选模式 / 5 显示标题 / 6 标识重复关卡
    //                7 打开首个未解关卡 / 8 打开上次推的关卡 / 9 清空列表 / 10 每行图标个数…
    //                11 批量删除… / 12 关于
    private static final String A_ADD = "╋";
    private static final String A_TOP = "顶";
    private static final String A_BOTTOM = "底";
    private static final String A_GOTO = "定位...";
    private static final String A_SELECT = "多选模式";
    private static final String A_SHOWTITLE = "显示标题";
    private static final String A_SHOWDUP = "标识重复关卡";
    private static final String A_FIRST_UNSOLVED = "打开首个未解关卡";
    private static final String A_RECENT = "打开上次推的关卡";
    private static final String A_CLEAR = "清空列表";
    private static final String A_COLCOUNT = "每行图标个数...";
    private static final String A_DELETE_MORE = "批量删除...";
    private static final String A_ABOUT = "关于";

    private final long mSetId;

    private myActionBar actionBar;
    private JPanel headerBar;
    private JLabel mTitleView;
    private JCheckBox my_SelectAll;
    private JPanel gridContainer;
    private JScrollPane scrollPane;
    private GridLayout gridLayout;

    private int mCols = 6;
    private int cellWidth = COLUMN_WIDTH_DP;

    private static final ConcurrentHashMap<String, BufferedImage> thumbCache = new ConcurrentHashMap<>();
    private final ArrayList<LevelCard> cardList = new ArrayList<>();

    /** 「╋」在非「创编关卡」下弹出的两项菜单，供测试取用。 */
    private JPopupMenu addMenuForTest;

    /** 上下文菜单最近一次打开时的「关卡序号」（原版 {@code m_Num}，长按/右键时赋值）。 */
    int m_Num;

    /** 上下文菜单里单选项的临时下标（原版 {@code mWhich}，迁出/复制关卡集时用）。 */
    int mWhich;

    /** 最后一次构建出的 14 项上下文菜单，供测试取用。 */
    private JPopupMenu contextMenuForTest;

    public myGridView(long setId, String setTitle) {
        this.mSetId = setId;
        myMaps.m_Set_id = setId;
        myMaps.sFile = setTitle != null ? setTitle : "关卡列表";

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initAppEnvironment();
        initUI();
        loadData();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    private void initAppEnvironment() {
        if (myMaps.sRoot == null) {
            myMaps.sRoot = System.getProperty("user.home") + "/.boxman";
        }
        if (mySQLite.m_SQL == null) {
            mySQLite.m_SQL = mySQLite.getInstance();
            mySQLite.m_SQL.openDataBase();
        }
        myMaps.loadSkins();
        // 原版在首次 getView 时由 my_grid_view_item.xml 的 60dip 得到 m_Sets[35]（缩略图边长）
        if (myMaps.m_Sets[35] <= 0) myMaps.m_Sets[35] = COLUMN_WIDTH_DP;
    }

    // ------------------------------------------------------------------ 界面搭建

    private void initUI() {
        setLayout(new BorderLayout());

        // ---- 1) ActionBar（原版 getActionBar()）
        actionBar = new myActionBar();
        buildActionBar();
        add(actionBar, BorderLayout.NORTH);

        // ---- 2) 关卡集标题条 + 3) GridView，纵向叠放
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(GRID_BG);

        headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(HEADER_BG);
        headerBar.setOpaque(true);
        headerBar.setPreferredSize(new Dimension(0, HEADER_HEIGHT));

        // my_grid_view.xml 顶部横向 LinearLayout：m_select_all(默认 gone) + 8dp 占位 + m_gridTitleView
        JPanel headerWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerWest.setOpaque(false);

        my_SelectAll = new JCheckBox("全选");
        my_SelectAll.setOpaque(false);
        my_SelectAll.setForeground(HEADER_FG);
        my_SelectAll.setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
        my_SelectAll.setVisible(false);
        my_SelectAll.addActionListener(e -> {
            boolean sel = my_SelectAll.isSelected();
            if (myMaps.m_lstMaps != null) {
                for (mapNode nd : myMaps.m_lstMaps) nd.Select = sel;
            }
            for (LevelCard card : cardList) card.updateSelection();
        });
        headerWest.add(my_SelectAll);

        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(HEADER_SPACER_WIDTH, 0));
        headerWest.add(spacer);
        headerBar.add(headerWest, BorderLayout.WEST);

        mTitleView = new JLabel(myMaps.sFile);
        mTitleView.setForeground(HEADER_FG);
        mTitleView.setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
        mTitleView.setBorder(new EmptyBorder(0, HEADER_TITLE_PADDING, 0, 0));
        headerBar.add(mTitleView, BorderLayout.CENTER);

        body.add(headerBar, BorderLayout.NORTH);

        gridContainer = new JPanel();
        gridContainer.setBackground(GRID_BG);
        gridLayout = new GridLayout(0, mCols, H_SPACING, V_SPACING);
        gridContainer.setLayout(gridLayout);

        scrollPane = new JScrollPane(gridContainer);
        scrollPane.setBorder(null);
        scrollPane.setBackground(GRID_BG);
        scrollPane.getViewport().setBackground(GRID_BG);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // 原版 Android 静止时不绘制滚动条，这里同样隐藏，仅保留滚轮滚动
        scrollPane.getVerticalScrollBar().setUI(new HiddenScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calcColumns();
            }
        });
        body.add(scrollPane, BorderLayout.CENTER);

        add(body, BorderLayout.CENTER);

        my_SetTitle();
    }

    /** 构建 ActionBar：标题、返回折角、showAsAction 动作项、溢出菜单（原版 res/menu/levels.xml） */
    private void buildActionBar() {
        actionBar.setUpEnabled(true, this::finish);
        actionBar.setBarTitle(myMaps.sFile);

        actionBar.addBarAction(A_ADD, this::onAdd);
        actionBar.addBarAction(A_TOP, () -> scrollToPosition(true));
        actionBar.addBarAction(A_BOTTOM, () -> scrollToPosition(false));

        actionBar.addAction(A_GOTO, this::doGoto);
        actionBar.addAction(A_SELECT, () -> toggleSelectMode(!myMaps.isSelect));
        actionBar.addAction(A_SHOWTITLE, () -> toggleShowTitle(myMaps.m_Sets[2] != 1));
        actionBar.addAction(A_SHOWDUP, () -> toggleShowDup(myMaps.m_Sets[12] != 1));
        actionBar.addAction(A_FIRST_UNSOLVED, this::openFirstUnsolved);
        actionBar.addAction(A_RECENT, this::openRecentLevel);
        actionBar.addAction(A_CLEAR, this::onClearList);
        actionBar.addAction(A_COLCOUNT, this::chooseColumnCount);
        actionBar.addAction(A_DELETE_MORE, this::onDeleteMore);
        actionBar.addAction(A_ABOUT, this::showSetAbout);

        applyMenu();
    }

    /** 原版 myGridView.setMenu()：按当前关卡集类型决定各菜单项的可见性 */
    private void applyMenu() {
        for (String t : new String[]{A_ADD, A_TOP, A_BOTTOM}) {
            actionBar.setBarActionVisible(t, false);
        }
        for (String t : new String[]{A_GOTO, A_SELECT, A_SHOWTITLE, A_SHOWDUP, A_FIRST_UNSOLVED,
                A_RECENT, A_CLEAR, A_COLCOUNT, A_DELETE_MORE, A_ABOUT}) {
            actionBar.setActionVisible(t, false);
        }

        String f = myMaps.sFile;
        if (f.equals("最近推过的关卡")) {
            showBar(A_TOP, A_BOTTOM);
            show(A_GOTO, A_SHOWTITLE, A_SHOWDUP, A_FIRST_UNSOLVED, A_CLEAR, A_COLCOUNT);
        } else if (f.equals("创编关卡")) {
            showBar(A_ADD, A_TOP, A_BOTTOM);
            show(A_GOTO, A_SELECT, A_SHOWTITLE, A_COLCOUNT, A_DELETE_MORE);
        } else if (f.equals("关卡查询")) {
            showBar(A_TOP, A_BOTTOM);
            show(A_GOTO, A_SELECT, A_SHOWTITLE, A_SHOWDUP, A_FIRST_UNSOLVED, A_COLCOUNT);
        } else if (f.equals("相似关卡")) {
            showBar(A_TOP, A_BOTTOM);
            show(A_GOTO, A_SELECT, A_SHOWTITLE, A_SHOWDUP, A_COLCOUNT);
        } else if (myMaps.m_Sets[0] == 3) {          // 扩展关卡组
            showBar(A_ADD, A_TOP, A_BOTTOM);
            show(A_GOTO, A_SELECT, A_SHOWTITLE, A_SHOWDUP, A_FIRST_UNSOLVED, A_RECENT,
                    A_COLCOUNT, A_DELETE_MORE, A_ABOUT);
        } else {                                     // 内置关卡组
            showBar(A_TOP, A_BOTTOM);
            show(A_GOTO, A_SELECT, A_SHOWTITLE, A_SHOWDUP, A_FIRST_UNSOLVED, A_RECENT,
                    A_COLCOUNT, A_ABOUT);
        }

        actionBar.setActionChecked(A_SELECT, myMaps.isSelect);
        actionBar.setActionChecked(A_SHOWTITLE, myMaps.m_Sets[2] == 1);
        actionBar.setActionChecked(A_SHOWDUP, myMaps.m_Sets[12] == 1);
    }

    private void showBar(String... titles) {
        for (String t : titles) actionBar.setBarActionVisible(t, true);
    }

    private void show(String... titles) {
        for (String t : titles) actionBar.setActionVisible(t, true);
    }

    /** 原版 myGridView.my_SetTitle()：标题条与 ActionBar 标题的分工 */
    private void my_SetTitle() {
        String f = myMaps.sFile;
        boolean special = false;
        for (String s : SPECIAL_TITLES) {
            if (s.equals(f)) {
                special = true;
                break;
            }
        }
        if (special) {
            headerBar.setVisible(false);
            setBarTitle(f);
        } else {
            headerBar.setVisible(true);
            mTitleView.setText(f);
            int total = myMaps.m_lstMaps == null ? 0 : myMaps.m_lstMaps.size();
            long solved = mySQLite.m_SQL != null ? mySQLite.m_SQL.count_Sovled(myMaps.m_Set_id) : 0;
            setBarTitle(solved + "/" + total);
        }
    }

    /** 原版 ActionBar.setTitle(...)：ActionBar 即窗口标题栏，故同步窗口标题 */
    private void setBarTitle(String title) {
        actionBar.setBarTitle(title);
        setTitle(title);
    }

    /** 原版 ActionBar 的返回键（android.R.id.home）→ this.finish() */
    private void finish() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    // ------------------------------------------------------------------ 网格布局

    private void updateGridLayout() {
        if (myMaps.m_Sets[2] == 1) {
            // 显示标题（ListView 模式）
            gridLayout = new GridLayout(0, 1, 0, V_SPACING);
        } else {
            gridLayout = new GridLayout(0, Math.max(1, mCols), H_SPACING, V_SPACING);
        }
        gridContainer.setLayout(gridLayout);
    }

    private void calcColumns() {
        if (myMaps.m_Sets[2] == 1) {
            updateGridLayout();
            gridContainer.revalidate();
            return;
        }

        int width = scrollPane.getViewport().getWidth();
        if (width <= 0) width = UiWindow.PHONE_WIDTH;

        int userCols = myMaps.m_Sets[33];
        if (userCols >= 1 && userCols <= 10) {
            mCols = userCols;
        } else {
            // 原版 GridView auto_fit：(可用宽 + hSpacing) / (columnWidth + hSpacing)
            mCols = Math.max(1, (width + H_SPACING) / (COLUMN_WIDTH_DP + H_SPACING));
        }
        cellWidth = Math.max(1, (width - (mCols - 1) * H_SPACING) / mCols);

        updateGridLayout();
        for (LevelCard c : cardList) c.updateLayoutMode();
        gridContainer.revalidate();
    }

    // ------------------------------------------------------------------ 数据

    public void loadData() {
        if (mSetId >= 0 && mySQLite.m_SQL != null) {
            mySQLite.m_SQL.get_Levels(mSetId);
        }
        refreshGrid();
    }

    public void refreshGrid() {
        gridContainer.removeAll();
        cardList.clear();

        if (myMaps.m_lstMaps != null) {
            for (int i = 0; i < myMaps.m_lstMaps.size(); i++) {
                LevelCard card = new LevelCard(myMaps.m_lstMaps.get(i), i);
                cardList.add(card);
                gridContainer.add(card);
            }
        }

        my_SetTitle();
        calcColumns();
        gridContainer.revalidate();
        gridContainer.repaint();
    }

    // ------------------------------------------------------------------ 菜单动作

    private void scrollToPosition(boolean toTop) {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vbar = scrollPane.getVerticalScrollBar();
            vbar.setValue(toTop ? vbar.getMinimum() : vbar.getMaximum());
        });
    }

    private void toggleSelectMode(boolean on) {
        myMaps.isSelect = on;
        my_SelectAll.setSelected(false);
        if (!on && myMaps.m_lstMaps != null) {
            for (mapNode nd : myMaps.m_lstMaps) nd.Select = false;
        }
        my_SelectAll.setVisible(on);
        applyMenu();
        for (LevelCard c : cardList) c.updateSelection();
        headerBar.revalidate();
        headerBar.repaint();
        revalidate();
        repaint();
    }

    private void toggleShowTitle(boolean on) {
        myMaps.m_Sets[2] = on ? 1 : 0;
        applyMenu();
        for (LevelCard c : cardList) c.updateLayoutMode();
        calcColumns();
        gridContainer.revalidate();
        gridContainer.repaint();
    }

    private void toggleShowDup(boolean on) {
        myMaps.m_Sets[12] = on ? 1 : 0;
        applyMenu();
        thumbCache.clear();
        for (LevelCard c : cardList) c.requestThumbnail();
    }

    private void showSetAbout() {
        String msg = (mySQLite.m_SQL != null && mSetId > 0)
                ? mySQLite.m_SQL.count_Sovled(mSetId) + "/" + mySQLite.m_SQL.count_Level(mSetId)
                : "";
        new myAbout1(this, mSetId, msg).setVisible(true);
    }

    private void doGoto() {
        if (myMaps.m_lstMaps == null || myMaps.m_lstMaps.isEmpty()) return;
        String input = JOptionPane.showInputDialog(this, "请输入要跳转的关卡序号 (1 - " + myMaps.m_lstMaps.size() + "):", "定位", JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.trim().isEmpty()) {
            try {
                int num = Integer.parseInt(input.trim());
                if (num >= 1 && num <= myMaps.m_lstMaps.size()) {
                    Rectangle bounds = cardList.get(num - 1).getBounds();
                    scrollPane.getViewport().scrollRectToVisible(bounds);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void openFirstUnsolved() {
        if (myMaps.m_lstMaps == null) return;
        for (int i = 0; i < myMaps.m_lstMaps.size(); i++) {
            mapNode nd = myMaps.m_lstMaps.get(i);
            if (!nd.Solved) {
                openGame(nd, i);
                return;
            }
        }
        MyToast.showToast(this, "关卡已经全部解开！", MyToast.LENGTH_SHORT);
    }

    private void openRecentLevel() {
        if (myMaps.m_lstMaps == null || myMaps.m_lstMaps.isEmpty()) return;
        long recentId = mySQLite.m_SQL.get_Recent(mSetId);
        int targetIdx = 0;
        if (recentId > 0) {
            for (int i = 0; i < myMaps.m_lstMaps.size(); i++) {
                if (myMaps.m_lstMaps.get(i).Level_id == recentId) {
                    targetIdx = i;
                    break;
                }
            }
        }
        openGame(myMaps.m_lstMaps.get(targetIdx), targetIdx);
    }

    private void chooseColumnCount() {
        String input = JOptionPane.showInputDialog(this, "输入每行图标个数 (1-10，0表示自动):", myMaps.m_Sets[33]);
        if (input != null) {
            try {
                int cols = Integer.parseInt(input.trim());
                if (cols >= 0 && cols <= 10) {
                    if (myMaps.m_Sets[33] == 0) myMaps.m_Sets[34] = mCols;
                    myMaps.m_Sets[33] = cols;
                    calcColumns();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- ╋ / 清空列表 / 批量删除

    /**
     * 原版 {@code levels_add}「╋」：创编新的关卡，或往当前关卡集里添加关卡。
     *
     * <p>两条分支（{@code myGridView.java:441-502}）：
     * <ul>
     *   <li>{@code sFile == "创编关卡"} → 弹「关卡尺寸」框，建一个空关卡后直接进编辑器
     *       （{@code curMapNum = -2} 表示「新建关卡」状态，此时列表里还没有图标）</li>
     *   <li>其它关卡集 → 弹一个两项的 PopupMenu：{@code 添加关卡(文档)...} / {@code 添加关卡(剪切板)...}</li>
     * </ul>
     */
    void onAdd() {
        if ("创编关卡".equals(myMaps.sFile)) {
            SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
            final String fn = "NewLevel_" + df.format(new Date()) + ".XSB";   // new Date() 取当前系统时间

            NewLevelDialog dlg = new NewLevelDialog(this, (rows, cols) -> {
                myMaps.curMap = new mapNode(rows, cols, null, fn, "", "");
                myMaps.curMap.fileName = fn;
                myMaps.curMapNum = -2;   // 「新建关卡」状态，此时列表中没有关卡图标
                new myEditView().setVisible(true);
            });
            dlg.setVisible(true);
        } else {
            // 原版用 PopupMenu 锚在 mTitleView 上
            JPopupMenu menu = HoloPopupMenu.create();
            HoloPopupMenu.addItem(menu, "添加关卡(文档)...", this::sel_File);
            HoloPopupMenu.addItem(menu, "添加关卡(剪切板)...", this::read_Plate);
            addMenuForTest = menu;
            if (mTitleView != null && mTitleView.isShowing()) {
                menu.show(mTitleView, 0, mTitleView.getHeight());
            }
        }
    }

    /**
     * 原版 {@code sel_File()}「添加关卡(文档)...」：列出「导入/」目录下的关卡文档，
     * 让用户选一个导入到当前关卡集。
     *
     * <p>原版还带 {@code import_dialog.xml} 的「关卡/答案」复选、编码单选
     * （自动/GBK/UTF-8）与「仅一个关卡时自动打开」。PC 侧这里先按原版口径把
     * {@code myMaps.isXSB / isLurd / m_Code / m_Sets[31]} 读进来并回写，
     * 导入本身复用 {@link BoxManPC#importLevelFile}。
     */
    void sel_File() {
        myMaps.newSetList();
        if (myMaps.mFile_List.isEmpty()) {
            MyToast.showToast(this, "没找到关卡文档。", MyToast.LENGTH_SHORT);
            return;
        }

        String[] items = myMaps.mFile_List.toArray(new String[0]);
        JComponent extra = buildImportOptions(true);

        HoloChoiceDialog.selectThenOk(this, "文档导入", extra, items, -1, which -> {
            String setName = myMaps.mFile_List.get(which);
            File f = new File(myMaps.sRoot + myMaps.sPath + "导入/" + setName);
            importDocFile(f, setName);
        }).setVisible(true);
    }

    /**
     * 原版 {@code read_Plate()}「添加关卡(剪切板)...」：剪切板内容先放进一个可编辑的
     * 文本框（标题「剪切板导入」），确定后按「关卡/答案」复选导入。
     */
    void read_Plate() {
        String str = myMaps.loadClipper();
        if (str == null || str.isEmpty()) {
            MyToast.showToast(this, "剪切板中没有找到关卡数据！", MyToast.LENGTH_SHORT);
            return;
        }

        JTextArea ta = new JTextArea(str);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        ta.setBackground(HoloContent.FIELD_BG);
        ta.setForeground(HoloContent.TEXT);
        ta.setCaretColor(HoloContent.TEXT);
        ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(300, 180));
        HoloContent.darkScrollBar(sp);

        JComponent extra = HoloContent.column(buildImportOptions(false), sp);

        HoloViewDialog dlg = new HoloViewDialog(this, "剪切板导入", extra);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            importClipText(ta.getText());
        });
        dlg.setVisible(true);
    }

    /**
     * {@code import_dialog.xml} / {@code import_dialog2.xml} 共用的那几个开关。
     *
     * <p>文案照 {@code import_dialog.xml}：{@code cb_xsb} 是 <b>「XSB」</b>、{@code cb_lurd} 是
     * <b>「Lurd」</b>（不是「关卡 / 答案」——那是 {@code import_dialog3.xml} 的 {@code im_xsb}/
     * {@code im_lurd}，属于 {@link BoxManPC#buildImportDialog()} 那一套）。两者都是
     * {@code wrap_content}，所以用 {@link HoloContent#wrapCheck}。
     */
    private JComponent buildImportOptions(boolean withEncoding) {
        JCheckBox cbXsb = HoloContent.wrapCheck("XSB", true);
        JCheckBox cbLurd = HoloContent.wrapCheck("Lurd", myMaps.isLurd);
        // 原版是两个 CheckBox 互相兜底：都不选时自动把另一个勾上
        cbXsb.addActionListener(e -> {
            myMaps.isXSB = cbXsb.isSelected();
            if (!cbXsb.isSelected() && !cbLurd.isSelected()) cbLurd.setSelected(true);
        });
        cbLurd.addActionListener(e -> {
            myMaps.isLurd = cbLurd.isSelected();
            if (!cbLurd.isSelected() && !cbXsb.isSelected()) cbXsb.setSelected(true);
        });

        JCheckBox cbOpen = HoloContent.wrapCheck("仅有一个关卡时，自动打开", myMaps.m_Sets[31] == 1);
        cbOpen.addActionListener(e -> myMaps.m_Sets[31] = cbOpen.isSelected() ? 1 : 0);

        JPanel row1 = HoloContent.row(cbXsb, cbLurd);
        if (!withEncoding) {
            return HoloContent.column(row1, cbOpen);
        }

        ButtonGroup g = new ButtonGroup();
        JRadioButton rbAuto = HoloContent.radio("自动", myMaps.m_Code == 0);
        JRadioButton rbGbk = HoloContent.radio("GBK", myMaps.m_Code == 1);
        JRadioButton rbUtf8 = HoloContent.radio("UTF-8", myMaps.m_Code == 2);
        g.add(rbAuto);
        g.add(rbGbk);
        g.add(rbUtf8);
        rbAuto.addActionListener(e -> myMaps.m_Code = 0);
        rbGbk.addActionListener(e -> myMaps.m_Code = 1);
        rbUtf8.addActionListener(e -> myMaps.m_Code = 2);

        return HoloContent.column(row1, HoloContent.row(rbAuto, rbGbk, rbUtf8), cbOpen);
    }

    /**
     * 导入一个「导入/」目录下的文档。
     *
     * <p>落库目标是<b>当前关卡集</b>（{@code myMaps.m_Set_id}）—— 原版
     * {@code imPort_Sets(..., 1)} 进 {@code mySplitLevelsFragment} 后就是
     * {@code add_L(myMaps.m_Set_id, nd)}。PC 侧原先按「文档名 → 关卡集名」找/建集，
     * 会误在扩展组里新建一个集，已改回按 id 落库。
     */
    void importDocFile(File f, String fileName) {
        int dot = fileName.lastIndexOf('.');
        String setTitle = dot > 0 ? fileName.substring(0, dot) : fileName;

        // PC 侧没有独立的导入 Activity，复用 BoxManPC 的解析器
        BoxManPC importer = findBoxManPC();
        if (importer != null) {
            importer.importLevelFileInto(f, mSetId, true);
        } else {
            new BoxManPC().importLevelFileInto(f, mSetId, true);
        }
        afterImport(setTitle);
    }

    /** 导入剪切板文本（原版也是导进当前关卡集）。 */
    void importClipText(String text) {
        BoxManPC importer = findBoxManPC();
        int n = (importer != null)
                ? importer.importLevelTextInto(text, mSetId, true)
                : new BoxManPC().importLevelTextInto(text, mSetId, true);
        if (n > 0) afterImport(myMaps.sFile);
    }

    /** 导入完成后的列表刷新（原版 {@code onSplitDone()} 的收尾部分）。 */
    private void afterImport(String setTitle) {
        if (mSetId >= 0 && mySQLite.m_SQL != null) {
            mySQLite.m_SQL.get_Levels(mSetId);
        }
        refreshGrid();
        my_SelectAll.setSelected(false);
        scrollToPosition(false);   // 定位到新增的关卡
        MyToast.showToast(this, "导入成功！", MyToast.LENGTH_SHORT);
    }

    /** 找出已打开的 {@link BoxManPC} 主窗口；找不到就返回 {@code null}（测试里就是这样）。 */
    private BoxManPC findBoxManPC() {
        for (Window w : Window.getWindows()) {
            if (w instanceof BoxManPC && w.isDisplayable()) return (BoxManPC) w;
        }
        return null;
    }

    /**
     * 原版 {@code levels_clear}「清空列表」：确认后把「最近推过的关卡」的时间戳清掉，
     * 并清空当前列表。
     */
    private void onClearList() {
        new HoloConfirmDialog(this, "确认", "清空列表，确定吗？", "取消", "确定",
                this::clearList).setVisible(true);
    }

    /** 「清空列表」确认后的动作（原版确认框里的 {@code onClick}）。 */
    void clearList() {
        if (mySQLite.m_SQL != null) mySQLite.m_SQL.Clear_L_DateTime();
        myMaps.m_lstMaps.clear();
        refreshGrid();
    }

    /**
     * 原版 {@code levels_delete_more}「批量删除...」：弹「删除范围: 1 -- N」，
     * 输入起止序号后按序号区间删除。
     *
     * <p>「创编关卡」删的是磁盘上的 {@code .XSB} 文档；其它关卡集删的是库里的关卡
     * （{@code del_L}）。<b>从后往前删</b>，否则下标会错位。
     */
    void onDeleteMore() {
        if (myMaps.m_lstMaps.isEmpty()) return;

        JSpinner spFrom = HoloContent.spinner(72, 1, 1, myMaps.m_lstMaps.size());
        JSpinner spTo = HoloContent.spinner(72, 0, 0, myMaps.m_lstMaps.size());
        JComponent body = HoloContent.column(
                HoloContent.row(HoloContent.label("开始:", 48, SwingConstants.LEFT), spFrom),
                HoloContent.row(HoloContent.label("结束:", 48, SwingConstants.LEFT), spTo));

        HoloViewDialog dlg = new HoloViewDialog(this,
                "删除范围: 1 -- " + myMaps.m_lstMaps.size(), body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            int m = (Integer) spFrom.getValue();
            int n = (Integer) spTo.getValue();
            dlg.dispose();
            deleteRange(m, n);
        });
        dlg.setVisible(true);
    }

    /** 「批量删除...」确认后的动作：删除序号 {@code [m, n]} 闭区间内的关卡。 */
    void deleteRange(int m, int n) {
        myMaps.curMap = null;

        boolean creative = "创编关卡".equals(myMaps.sFile);
        int len = myMaps.m_lstMaps.size();
        for (int k = n; k >= m; k--) {          // 从后往前，避免下标错位
            if (k > len) continue;
            try {
                if (creative) {
                    File file = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/"
                            + myMaps.m_lstMaps.get(k - 1).fileName);
                    if (file.exists() && file.isFile()) file.delete();
                } else {
                    mySQLite.m_SQL.del_L(myMaps.m_lstMaps.get(k - 1).Level_id);
                }
                myMaps.m_lstMaps.remove(k - 1);
            } catch (Exception ignored) {
                // 原版这里也是空 catch
            }
        }

        setSelectAll();
        refreshGrid();
    }

    private void openGame(mapNode node, int index) {
        if (node.Title != null && node.Title.equals("无效关卡")) {
            new myAbout2(this, node).setVisible(true);
            return;
        }

        myMaps.iskinChange = false;
        myMaps.curMap = node;
        myMaps.curMapNum = index;
        myMaps.m_nTrun = node.Trun;

        myGameView game = new myGameView();
        game.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                loadData();
            }
        });
        game.setVisible(true);
    }

    // ------------------------------------------------------------------ 单元

    private class LevelCard extends JPanel {
        private final mapNode node;
        private final int index;
        private JLabel lblThumb;
        private JLabel lblText;
        private JLabel lblText2;

        LevelCard(mapNode nd, int idx) {
            this.node = nd;
            this.index = idx;
            setOpaque(true);
            setBackground(GRID_BG);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            updateLayoutMode();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showContextMenu(e);
                    } else if (e.getClickCount() == 1) {
                        if (myMaps.isSelect) {
                            node.Select = !node.Select;
                            updateSelection();
                            setSelectAll();
                        } else {
                            openGame(node, index);
                        }
                    }
                }
            });

            requestThumbnail();
        }

        public void updateLayoutMode() {
            removeAll();
            int imgSize = cellWidth;
            if (myMaps.m_Sets[2] == 0) {
                // 网格模式：ImageView(与列同宽) 在上，编号文字条在下
                setLayout(new BorderLayout());
                setBorder(new EmptyBorder(IMAGE_MARGIN, 0, IMAGE_MARGIN, 0));

                lblThumb = new JLabel("", SwingConstants.CENTER);
                lblThumb.setOpaque(true);
                lblThumb.setBackground(GRID_BG);
                lblThumb.setPreferredSize(new Dimension(imgSize, imgSize));
                add(lblThumb, BorderLayout.CENTER);

                lblText = new JLabel(String.valueOf(index + 1), SwingConstants.CENTER);
                lblText.setOpaque(true);
                lblText.setForeground(ITEM_FG);
                lblText.setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
                lblText.setPreferredSize(new Dimension(0, TEXT_ROW_HEIGHT));
                add(lblText, BorderLayout.SOUTH);
                lblText2 = null;
            } else {
                // 显示标题模式：左侧缩略图(60dip) + 右侧三行文字
                setLayout(new BorderLayout());
                setBorder(new EmptyBorder(IMAGE_MARGIN, 0, IMAGE_MARGIN, 0));

                lblThumb = new JLabel("", SwingConstants.CENTER);
                lblThumb.setOpaque(true);
                lblThumb.setBackground(GRID_BG);
                lblThumb.setPreferredSize(new Dimension(imgSize, imgSize));
                JPanel left = new JPanel(new BorderLayout());
                left.setOpaque(false);
                left.setBorder(new EmptyBorder(0, IMAGE_MARGIN, 0, IMAGE_MARGIN));
                left.add(lblThumb, BorderLayout.NORTH);
                add(left, BorderLayout.WEST);

                lblText2 = new JLabel();
                lblText2.setOpaque(true);
                lblText2.setForeground(ITEM_FG);
                lblText2.setFont(new Font("Microsoft YaHei", Font.PLAIN, TEXT_SIZE));
                lblText2.setVerticalAlignment(SwingConstants.TOP);
                lblText2.setText("<html>序号：" + (index + 1)
                        + "<br>标题：" + escape(node.Title)
                        + "<br>作者：" + escape(node.Author) + "</html>");
                add(lblText2, BorderLayout.CENTER);
                lblText = null;
            }
            updateSelection();
            requestThumbnail();
            revalidate();
            repaint();
        }

        /** 原版 getView 中的背景色：已通关 #004700 / 未通关 #151515 / 选中 #A06300 */
        public void updateSelection() {
            Color bg;
            if (myMaps.isSelect && node.Select) {
                bg = ITEM_BG_SELECT;
            } else if (node.Solved) {
                bg = ITEM_BG_SOLVED;
            } else {
                bg = ITEM_BG;
            }
            if (lblText != null) lblText.setBackground(bg);
            if (lblText2 != null) lblText2.setBackground(bg);
            repaint();
        }

        public void requestThumbnail() {
            if (lblThumb == null) return;
            int size = (myMaps.m_Sets[2] == 1) ? Math.max(1, myMaps.m_Sets[35]) : cellWidth;
            String cacheKey = node.key + "_" + node.Level_id + "_" + size;
            BufferedImage cached = thumbCache.get(cacheKey);
            if (cached != null) {
                lblThumb.setIcon(new ImageIcon(cached));
                return;
            }

            SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() {
                    return generateThumbnail(node, size);
                }

                @Override
                protected void done() {
                    try {
                        BufferedImage img = get();
                        if (img != null) {
                            thumbCache.put(cacheKey, img);
                            lblThumb.setIcon(new ImageIcon(img));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            worker.execute();
        }

        private void showContextMenu(MouseEvent e) {
            // 原版是 registerForContextMenu + onCreateContextMenu + ItemLongClickListener ——
            // Android 上下文菜单与 ActionBar 溢出菜单共用 popup_menu_holo_dark 样式，
            // 所以必须走 HoloPopupMenu（见 Phase20MenuCarrierConventionTest）。
            m_Num = index;
            JPopupMenu menu = buildContextMenu();
            contextMenuForTest = menu;
            menu.show(this, e.getX(), e.getY());
        }
    }

    // ---------------------------------------------------------------- 上下文菜单（14 项）

    /** 原版 {@code onCreateContextMenu()} 的 14 项标题，顺序一致。 */
    static final String[] CONTEXT_ITEMS = {
            "打开", "改编为新关卡", "图标加锁", "迁出关卡至...", "复制关卡到...", "导出...",
            "移动到...", "前移", "后移", "删除", "查找相似关卡", "连续选择至...", "反选", "详细..."};

    /**
     * 构建上下文菜单 —— 原版 {@code onCreateContextMenu()}（14 项）+ 原版
     * {@code ItemLongClickListener}（按关卡集类型与多选状态逐项设可见性）的合并实现。
     *
     * <p>可见性矩阵（{@code vis[0..13]} 依次对应 {@link #CONTEXT_ITEMS}）：
     * <pre>
     * 最近推过的关卡 0,1,5,10,13 ｜ 创编关卡 1,9,10,13 ｜ 关卡查询 / 相似关卡 / 内置组 0,1,4,5,10,13
     * 扩展关卡组（m_Sets[0]==3） 0..10 + 13
     * 多选模式下：1/5/10 强制隐藏，11/12 显示；否则 1 显示，11/12 隐藏，
     *             且 5（导出）在「创编关卡」下、10（查找相似关卡）在「相似关卡」下保持隐藏
     * </pre>
     * 另外两处标题改写：创编关卡下第 2 项变「编辑」；扩展关卡组下关卡已加锁时第 3 项变「图标解锁」。
     */
    JPopupMenu buildContextMenu() {
        boolean[] vis = new boolean[CONTEXT_ITEMS.length];
        String[] titles = CONTEXT_ITEMS.clone();
        String f = myMaps.sFile;

        if ("创编关卡".equals(f)) {
            titles[1] = "编辑";
        } else if (myMaps.m_Sets[0] == 3 && myMaps.m_lstMaps != null
                && m_Num >= 0 && m_Num < myMaps.m_lstMaps.size()
                && myMaps.m_lstMaps.get(m_Num).Lock) {
            titles[2] = "图标解锁";
        }

        if ("最近推过的关卡".equals(f)) {
            vis[0] = vis[1] = vis[5] = vis[10] = vis[13] = true;
        } else if ("创编关卡".equals(f)) {
            vis[1] = vis[9] = vis[10] = vis[13] = true;
        } else if ("关卡查询".equals(f)) {
            vis[0] = vis[1] = vis[4] = vis[5] = vis[10] = vis[13] = true;
        } else if ("相似关卡".equals(f)) {
            vis[0] = vis[1] = vis[4] = vis[5] = vis[13] = true;
        } else if (myMaps.m_Sets[0] == 3) {          // 扩展关卡组
            for (int i = 0; i <= 10; i++) vis[i] = true;
            vis[13] = true;
        } else {                                     // 内置关卡组
            vis[0] = vis[1] = vis[4] = vis[5] = vis[10] = vis[13] = true;
        }

        if (myMaps.isSelect) {                       // 多选模式
            vis[1] = false;                          // 改编为新关卡（或编辑）
            vis[5] = false;                          // 导出...
            vis[10] = false;                         // 查找相似关卡
            vis[11] = true;                          // 连续选择...
            vis[12] = true;                          // 反选
        } else {
            vis[1] = true;
            if (!"创编关卡".equals(f)) vis[5] = true;
            if (!"相似关卡".equals(f)) vis[10] = true;
            vis[11] = false;
            vis[12] = false;
        }

        JPopupMenu menu = HoloPopupMenu.create();
        for (int i = 0; i < titles.length; i++) {
            final int itemId = i + 1;
            HoloPopupMenu.Row row = HoloPopupMenu.addItem(menu, titles[i],
                    () -> onContextItemSelected(itemId));
            row.setVisible(vis[i]);
        }
        menu.revalidate();
        menu.repaint();
        return menu;
    }

    /**
     * 原版 {@code onContextItemSelected(MenuItem)} 的等价物：按菜单项 id（1..14）执行动作。
     * 用 {@link #m_Num} 定位关卡，拆成独立方法以便测试直接调用（不必真弹菜单）。
     *
     * @return 恒为 {@code true}（原版如此）
     */
    boolean onContextItemSelected(int itemId) {
        switch (itemId) {
            case 1:   // 打开
                if ("相似关卡".equals(myMaps.sFile)) {
                    myMaps.iskinChange = false;
                    myMaps.curMap = myMaps.m_lstMaps.get(m_Num);
                    new myFindView().setVisible(true);
                } else {
                    // 原版这里直接 startActivity(myGameView)；PC 的 openGame() 额外带上了
                    // 「无效关卡 → 显示备注」的保护（对应原版 ItemClickListener 的同名分支）
                    openGame(myMaps.m_lstMaps.get(m_Num), m_Num);
                }
                break;

            case 2:   // 改编为新关卡 / 编辑
                editAsNewLevel();
                break;

            case 3:   // 图标加锁 / 图标解锁
                if (myMaps.m_lstMaps.get(m_Num).P_id < 0) break;   // 相似查找中答案表的关卡
                mapNode lockNd = myMaps.m_lstMaps.get(m_Num);
                lockNd.Lock = !lockNd.Lock;
                mySQLite.m_SQL.Update_L_Lock(lockNd.Level_id, lockNd.Lock ? 1 : 0);
                refreshGrid();
                break;

            case 4:   // 迁出关卡至...
                migrateToSet(true);
                break;

            case 5:   // 复制关卡到...
                migrateToSet(false);
                break;

            case 6:   // 导出...
                exportCurrent();
                break;

            case 7:   // 移动到...
                onMoveTo();
                break;

            case 8:   // 前移
                onShift(-1);
                break;

            case 9:   // 后移
                onShift(1);
                break;

            case 10:  // 删除
                onDeleteOne();
                break;

            case 11:  // 查找相似关卡
                onFindSimilar();
                break;

            case 12:  // 连续选择至...
                onSelectRange();
                break;

            case 13:  // 反选
                for (mapNode nd : myMaps.m_lstMaps) {
                    nd.Select = !nd.Select;
                }
                setSelectAll();   // 设置全选开关状态
                refreshGrid();
                break;

            case 14:  // 详细...
                myMaps.iskinChange = false;
                myMaps.curMap = myMaps.m_lstMaps.get(m_Num);
                new myAbout2(this, myMaps.curMap).setVisible(true);
                break;

            default:
                break;
        }
        return true;
    }

    /**
     * 原版 {@code case 2}「改编为新关卡 / 编辑」。
     *
     * <p>{@code nd.Level_id > 0} 表示这是库里已有的关卡 → 「改编为新关卡」：换一个新文档名、
     * 标题以原标题为本（空标题则用「关卡集名_序号」），{@code curMapNum = -3}；
     * 否则（创编关卡）→「编辑」，{@code curMapNum = m_Num}。
     */
    void editAsNewLevel() {
        mapNode nd = myMaps.m_lstMaps.get(m_Num);
        boolean flg9 = true;   // 是否有效关卡

        if (nd.Level_id > 0) {
            // 为关卡生成关卡文档名称（含有原关卡集及其关卡序号信息）
            SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String fn = "NewLevel_" + df.format(new Date()) + ".XSB";

            // 若关卡标题不空，则以关卡标题为本；否则以关卡集名及其序号为本
            String newTitle = nd.Title.trim().isEmpty()
                    ? myMaps.sFile + "_" + (m_Num + 1)
                    : nd.Title.trim();

            recycleBitmapCaches(0, myMaps.m_lstMaps.size());
            myMaps.read_DirBuilder();   // 重新加载「创编关卡」文件夹中的关卡列表
            applyMenu();                // 原版 setMenu(MyMenu)：调整菜单项

            myMaps.curMapNum = -3;      // 「改编为新关卡」状态，列表中已有关卡图标
            nd.fileName = fn;           // 关卡保存时的文档名称
            if ("--".equals(nd.Map)) {  // 无效关卡
                myMaps.loadXSB(nd.Comment);
                flg9 = false;
            }
            nd.Title = newTitle;
            nd.Comment = "";
        } else {
            myMaps.curMapNum = m_Num;   // 「继续编辑」状态
        }

        if (flg9) {
            myMaps.curMap = new mapNode(nd.Rows, nd.Cols,
                    nd.Map.split("\r\n|\n\r|\n|\r|\\|"), nd.Title, nd.Author, nd.Comment);
        }
        myMaps.curMap.fileName = nd.fileName;   // 取得当前关卡文档名
        new myEditView().setVisible(true);
    }

    /**
     * 原版 {@code case 4}/{@code case 5}「迁出关卡至... / 复制关卡到...」的合并实现：
     * 弹一个「关卡集列表 + 末尾自动追加一个新建关卡集」的单选对话框。
     *
     * @param move {@code true} = 迁出（源关卡集里删掉）；{@code false} = 复制（源关卡集保留）
     */
    void migrateToSet(boolean move) {
        String title = prepareMigrateSelection(move);

        String[] items = new String[myMaps.mSets3.size() + 1];
        for (int k = 0; k < myMaps.mSets3.size(); k++) {
            items[k] = myMaps.mSets3.get(k).title;
        }
        items[items.length - 1] = myMaps.getNewSetName();   // 末尾，自动加上一个新的关卡集

        mWhich = 0;
        HoloChoiceDialog.selectThenOk(this, title, null, items, 0, which -> {
            mWhich = which;
            applyMigrate(move, items);
        }).setVisible(true);
    }

    /**
     * 「迁出 / 复制」前的选择收集 —— 原版 {@code case 4}/{@code case 5} 里算
     * {@code str}/{@code str2} 的那一段（会填好 {@code myMaps.mArray}）。
     *
     * @return 对话框标题
     */
    String prepareMigrateSelection(boolean move) {
        myMaps.mArray.clear();
        myMaps.curMap = null;

        String title;
        if (myMaps.isSelect && myMaps.m_lstMaps.get(m_Num).Select) {   // 多关卡
            for (int k = 0; k < myMaps.m_lstMaps.size(); k++) {
                if (myMaps.m_lstMaps.get(k).Select) {
                    myMaps.mArray.add(k);
                }
            }
            title = "共 " + myMaps.mArray.size() + " 个关卡" + (move ? "迁至" : "复制到");
        } else {                                                       // 单关卡
            myMaps.mArray.add(m_Num);
            title = (m_Num + 1) + " 号关卡" + (move ? "迁至" : "复制到");
        }
        return title;
    }

    /** 「迁出/复制」对话框点「确定」后的动作（原版那个 {@code onClick}）。 */
    void applyMigrate(boolean move, String[] items) {
        // 如果选中的是最后一个「新建关卡集」
        if (mWhich == items.length - 1) {
            try {
                long newId = mySQLite.m_SQL.add_T(3, items[items.length - 1], "", "");
                set_Node nd = new set_Node();
                nd.id = newId;
                nd.title = items[items.length - 1];
                myMaps.mSets3.add(nd);
            } catch (Exception e) {
                MyToast.showToast(this, "新关卡集创建失败: " + items[items.length - 1], MyToast.LENGTH_SHORT);
                return;
            }
        }
        if (mWhich < 0) return;

        long sId = myMaps.mSets3.get(mWhich).id;
        long sId2 = mySQLite.m_SQL.get_Level_Set_id(myMaps.m_lstMaps.get(m_Num).Level_id);
        if (sId == sId2) {
            MyToast.showToast(this, "请选择新的关卡集！", MyToast.LENGTH_SHORT);
            return;
        }

        try {
            int len = myMaps.m_lstMaps.size();
            for (int k = 0; k < myMaps.mArray.size(); k++) {
                int idx = myMaps.mArray.get(k);
                if (move) idx -= k;   // 迁出时前面的元素已被移除，下标要跟着前移
                long newId = mySQLite.m_SQL.add_L(sId, myMaps.m_lstMaps.get(idx));
                // 将关卡的状态带过去
                mySQLite.m_SQL.Update_A_Lid(myMaps.m_lstMaps.get(idx).Level_id, newId);
                if (move) {
                    mySQLite.m_SQL.del_L(myMaps.m_lstMaps.get(idx).Level_id);
                    myMaps.m_lstMaps.remove(idx);
                }
            }
            if (move) {
                recycleBitmapCaches(0, len);
                setSelectAll();   // 设置全选开关状态
                refreshGrid();
                MyToast.showToast(this, "迁出成功！", MyToast.LENGTH_SHORT);
            } else {
                MyToast.showToast(this, "复制成功！", MyToast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            MyToast.showToast(this, move ? "出错了，迁出失败！" : "出错了，复制失败！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 {@code case 6}「导出...」：组装关卡初态 + 答案后开 {@link myExport}。 */
    void exportCurrent() {
        StringBuilder sXSB = new StringBuilder();    // 关卡初态
        StringBuilder sLurd = new StringBuilder();   // Lurd

        myMaps.curMap = myMaps.m_lstMaps.get(m_Num);
        if ("无效关卡".equals(myMaps.curMap.Title)) {
            sXSB.append(myMaps.curMap.Comment);
        } else {
            sXSB.append(myMaps.curMap.Map)
                    .append("\nTitle: ").append(myMaps.curMap.Title)
                    .append("\nAuthor: ").append(myMaps.curMap.Author);
            if (!myMaps.curMap.Comment.trim().isEmpty()) {
                sXSB.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:");
            }
            myMaps.isComment = false;   // 答案备注信息
            if (myMaps.curMap.Solved) { // 导出答案
                myMaps.isComment = true;
                sLurd.append(mySQLite.m_SQL.get_Ans(myMaps.curMap.key));
            }
        }

        // 原版 Bundle：m_XSB / LOCAL=null（表示浏览界面的导出）/ m_Lurd / is_ANS=true
        new myExport(sXSB.toString(), sLurd.toString(), null, null, true, 0, null, null, null)
                .setVisible(true);
    }

    /** 原版 {@code case 7}「移动到...」：输入目标序号，把当前关卡移过去。 */
    void onMoveTo() {
        myMaps.curMap = null;
        int total = myMaps.m_lstMaps.size();

        JSpinner spTo = HoloContent.spinner(72, m_Num + 1, 1, Math.max(1, total));
        // 原版 goto_dialog.xml 的 setMessage(...)：显示在自定义视图之上
        JLabel msg = HoloContent.label("<html>移动范围：1 -- " + total
                + "<br>（大于 " + total + " 时则移到尾部）</html>");
        JComponent body = HoloContent.column(msg,
                HoloContent.row(HoloContent.label("移动到: ", 56, SwingConstants.LEFT), spTo));

        HoloViewDialog dlg = new HoloViewDialog(this, "将 " + (m_Num + 1) + " 号关卡移到", body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            int n = (Integer) spTo.getValue();
            dlg.dispose();
            moveTo(n);
        });
        dlg.setVisible(true);
    }

    /** 「移动到...」的实际移动（原版 {@code onClick} 与 ENTER 键处理共用的一段）。 */
    void moveTo(int n) {
        int total = myMaps.m_lstMaps.size();
        if (n > total) n = total;                 // 大于总数则移到尾部
        if (n <= 0 || n - 1 == m_Num) return;     // 位置没变，什么都不做

        try {
            swap(myMaps.m_lstMaps, m_Num, n - 1);
            if (n - 1 < m_Num) {
                recycleBitmapCaches(n - 1, m_Num + 1);
            } else {
                recycleBitmapCaches(m_Num, n + 1);
            }
            updateNO();   // 关卡顺序改变
            refreshGrid();
        } catch (Exception e) {
            MyToast.showToast(this, "出错了，移动失败！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code case 8}/{@code case 9}「前移 / 后移」的合并实现。
     *
     * @param dir {@code -1} = 前移，{@code +1} = 后移
     */
    void onShift(int dir) {
        myMaps.curMap = null;
        myMaps.mArray.clear();
        boolean flg = true;   // 被选中的关卡是否集中在一起

        if (myMaps.isSelect && myMaps.m_lstMaps.get(m_Num).Select) {
            if (dir < 0) {    // 前移：从小到大遍历
                for (int k = 0; k < myMaps.m_lstMaps.size(); k++) {
                    if (myMaps.m_lstMaps.get(k).Select) {
                        if (flg && !myMaps.mArray.isEmpty()
                                && myMaps.mArray.get(myMaps.mArray.size() - 1) + 1 < k) {
                            flg = false;   // 分散不集中
                        }
                        myMaps.mArray.add(k);
                    }
                }
            } else {          // 后移：从大到小遍历
                for (int k = myMaps.m_lstMaps.size() - 1; k >= 0; k--) {
                    if (myMaps.m_lstMaps.get(k).Select) {
                        if (flg && !myMaps.mArray.isEmpty()
                                && myMaps.mArray.get(myMaps.mArray.size() - 1) - 1 > k) {
                            flg = false;   // 分散不集中
                        }
                        myMaps.mArray.add(k);
                    }
                }
            }
        } else {
            myMaps.mArray.add(m_Num);   // 单关卡时 flg 默认为集中状态
        }

        int toNum, p;
        if (!flg) {   // 关卡多且分散：先让出第一个位置
            toNum = myMaps.mArray.get(0) - dir;
            p = 1;
        } else {      // 单个关卡或已经集中在一起
            toNum = myMaps.mArray.get(0) + dir;
            p = 0;
        }

        // 有空位可移动
        if (dir < 0 ? toNum >= 0 : toNum < myMaps.m_lstMaps.size()) {
            for (int k = p; k < myMaps.mArray.size(); k++) {
                swap(myMaps.m_lstMaps, myMaps.mArray.get(k), toNum);
                toNum -= dir;
            }
            updateNO();   // 关卡顺序改变
            if (dir < 0) {
                recycleBitmapCaches(myMaps.mArray.get(0) - 1,
                        myMaps.mArray.get(myMaps.mArray.size() - 1) + 1);
            } else {
                recycleBitmapCaches(myMaps.mArray.get(myMaps.mArray.size() - 1),
                        myMaps.mArray.get(0) + 2);
            }
            refreshGrid();
        }
    }

    /** 原版 {@code case 10}「删除」：确认后删单个或全部选中的关卡。 */
    void onDeleteOne() {
        HoloConfirmDialog.show(this, prepareDeleteSelection(), this::deleteSelected);
    }

    /**
     * 「删除」前的选择收集 —— 原版 {@code case 10} 里算 {@code str3} 的那一段。
     * 拆出来是为了让用例不必弹确认框也能验证选择逻辑。
     *
     * @return 确认框的提示文字
     */
    String prepareDeleteSelection() {
        myMaps.mArray.clear();
        String msg;
        if (myMaps.isSelect && myMaps.m_lstMaps.get(m_Num).Select) {
            for (int k = myMaps.m_lstMaps.size() - 1; k >= 0; k--) {
                if (myMaps.m_lstMaps.get(k).Select) {
                    myMaps.mArray.add(k);
                }
            }
            msg = "共有 " + myMaps.mArray.size() + " 个关卡将被删除，\n确认吗？";
        } else {
            myMaps.mArray.add(m_Num);
            msg = (m_Num + 1) + " 号关卡将被删除，确认吗？";
        }
        return msg;
    }

    /** 「删除」确认后的动作（原版那个 {@code onClick}）。 */
    void deleteSelected() {
        myMaps.curMap = null;
        int len = myMaps.m_lstMaps.size();
        for (int k = 0; k < myMaps.mArray.size(); k++) {
            int idx = myMaps.mArray.get(k);
            if ("创编关卡".equals(myMaps.sFile)) {
                File file = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/"
                        + myMaps.m_lstMaps.get(idx).fileName);
                if (file.exists() && file.isFile()) file.delete();
            } else {
                mySQLite.m_SQL.del_L(myMaps.m_lstMaps.get(idx).Level_id);
            }
            myMaps.m_lstMaps.remove(idx);
        }
        recycleBitmapCaches(0, len);
        setSelectAll();   // 设置全选开关状态
        refreshGrid();
    }

    /** 原版 {@code case 11}「查找相似关卡」：弹 {@link FindDialog}。 */
    void onFindSimilar() {
        new FindDialog(this, this::startFind).setVisible(true);
    }

    /**
     * {@link FindDialog} 点「开始」后的动作 —— 原版那段
     * {@code if (mDialog == null) { ... myMaps.oldMap = ...; mDialog.show(...) }}。
     *
     * <p>进入前必须先把<b>源关卡</b>塞进 {@code myMaps.oldMap}（{@link myFindView} 与
     * {@link myFindFragment} 都读它）。
     */
    void startFind(long[] sets, int similarity, boolean ans, boolean sort, boolean ignoreBox) {
        prepareFindSource();
        applyMenu();   // 原版 setMenu(MyMenu)
        new myFindFragment(this, this::onFindDone, sets, similarity, ans, sort, ignoreBox).show();
    }

    /**
     * 进入查找前先把<b>源关卡</b>塞进 {@code myMaps.oldMap}
     * （{@link myFindView} 与 {@link myFindFragment} 都读它）。
     *
     * <p>「创编关卡」下源关卡不是库里的关卡，所以要按自由关卡重新造一个，
     * 并把 {@code Map0} 直接等于 {@code Map}、{@code Num} 记成序号。
     */
    void prepareFindSource() {
        myMaps.curMap = myMaps.m_lstMaps.get(m_Num);

        if ("创编关卡".equals(myMaps.sFile)) {
            myMaps.oldMap = new mapNode(0, -1, myMaps.curMap.Rows, myMaps.curMap.Cols,
                    myMaps.curMap.Map, myMaps.curMap.Title, myMaps.curMap.Author,
                    myMaps.curMap.Comment, myMaps.curMap.Map);
            myMaps.oldMap.Map0 = myMaps.oldMap.Map;
            myMaps.oldMap.Num = m_Num + 1;   // 关卡序号
        } else {
            myMaps.oldMap = new mapNode(myMaps.curMap.Level_id, myMaps.curMap.P_id,
                    myMaps.curMap.Rows, myMaps.curMap.Cols, myMaps.curMap.Map,
                    myMaps.curMap.Title, myMaps.curMap.Author, myMaps.curMap.Comment,
                    myMaps.curMap.Map0);
        }
    }

    /** 原版 {@code myGridView.onFindDone()}：把查找结果换成当前列表。 */
    public void onFindDone(java.util.ArrayList<mapNode> mlMaps) {
        if (mlMaps != null && !mlMaps.isEmpty()) {
            int n = myMaps.m_lstMaps.size();
            myMaps.curMap = null;
            myMaps.m_lstMaps.clear();
            recycleBitmapCaches(0, n);
            myMaps.sFile = "相似关卡";
            applyMenu();   // 原版 setMenu(MyMenu)：调整菜单项
            myMaps.m_Set_id = -1;
            myMaps.J_Title = myMaps.sFile;
            myMaps.J_Author = "";
            myMaps.J_Comment = "";
            myMaps.m_lstMaps = mlMaps;
            my_SetTitle();
            refreshGrid();
            scrollToPosition(true);   // 原版 mGridView.setSelection(0)
        } else {
            MyToast.showToast(this, "没有发现相似的关卡！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 {@code case 12}「连续选择至...」：输入序号，把 [m_Num+1, n] 区间全选。 */
    void onSelectRange() {
        int total = myMaps.m_lstMaps.size();
        JSpinner spTo = HoloContent.spinner(72, m_Num + 1, 1, Math.max(1, total));
        JLabel msg = HoloContent.label("<html>从第 " + (m_Num + 1)
                + " 号关卡，向前或向后连选至：</html>");
        JComponent body = HoloContent.column(msg,
                HoloContent.row(HoloContent.label("连选至: ", 56, SwingConstants.LEFT), spTo));

        HoloViewDialog dlg = new HoloViewDialog(this, "连续选择", body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            int n = (Integer) spTo.getValue();
            dlg.dispose();
            selectRange(n);
        });
        dlg.setVisible(true);
    }

    /** 「连续选择至...」的实际动作（原版 {@code onClick} 里那段，含越界钳制与 Toast）。 */
    void selectRange(int n) {
        try {
            if (n < 1) n = 1;
            else if (n > myMaps.m_lstMaps.size()) n = myMaps.m_lstMaps.size();

            int from = m_Num + 1, to = m_Num + 1;
            if (n < from) {
                from = n;
            } else if (n > to) {
                to = n;
            }
            for (int k = from; k <= to; k++) {
                myMaps.m_lstMaps.get(k - 1).Select = true;
            }
            setSelectAll();   // 设置全选开关状态
            refreshGrid();
        } catch (Throwable ex) {
            MyToast.showToast(this, "关卡序号不正确！", MyToast.LENGTH_SHORT);
        }
    }

    // ---------------------------------------------------------------- 上下文菜单用的小工具

    /**
     * 原版 {@code myGridView.swap(List, int, int)}：把 {@code oldPosition} 处的元素
     * 一路交换到 {@code newPosition}（其余元素依次让位，不是单纯互换）。
     */
    static <T> void swap(java.util.List<T> list, int oldPosition, int newPosition) {
        if (list == null) {
            throw new IllegalStateException("The list can not be empty...");
        }
        if (oldPosition < newPosition) {        // 向前移动，前面的元素需要向后移动
            for (int i = oldPosition; i < newPosition; i++) {
                Collections.swap(list, i, i + 1);
            }
        }
        if (oldPosition > newPosition) {        // 向后移动，后面的元素需要向前移动
            for (int i = oldPosition; i > newPosition; i--) {
                Collections.swap(list, i, i - 1);
            }
        }
    }

    /** 原版 {@code myGridView.updateNO()}：关卡顺序改变后把序号写回数据库。 */
    void updateNO() {
        int len = myMaps.m_lstMaps.size();
        for (int k = 0; k < len; k++) {
            mySQLite.m_SQL.Set_L_NO(myMaps.m_lstMaps.get(k).Level_id, k + 1);
        }
    }

    /**
     * 原版 {@code myGridView.recycleBitmapCaches(int, int)}：释放 {@code [from, to)}
     * 区间内关卡缩略图的缓存（Android 侧是 {@code Bitmap.recycle()} 防 OOM）。
     *
     * <p>PC 的缩略图缓存以「关卡 key + Level_id + 尺寸」为键、不按下标存放，所以这里按
     * <b>该区间内关卡对应的缓存键</b> 来删，语义一致。
     */
    void recycleBitmapCaches(int fromPosition, int toPosition) {
        if (myMaps.m_lstMaps == null) return;
        for (int i = Math.max(0, fromPosition); i < toPosition && i < myMaps.m_lstMaps.size(); i++) {
            mapNode nd = myMaps.m_lstMaps.get(i);
            String prefix = nd.key + "_" + nd.Level_id + "_";
            thumbCache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** 原版 setSelectAll()：全部选中时勾上「全选」 */
    private void setSelectAll() {
        if (my_SelectAll == null || myMaps.m_lstMaps == null) return;
        boolean flg = !myMaps.m_lstMaps.isEmpty();
        for (mapNode nd : myMaps.m_lstMaps) {
            if (!nd.Select) {
                flg = false;
                break;
            }
        }
        my_SelectAll.setSelected(flg);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ 缩略图

    /**
     * 生成关卡缩略图。原版 myGridViewAdapter.getBitmapFromUrl：
     * 每个格子 25px 画进 Bitmap，再交给 ImageView 以 fitCenter 缩放到列宽。
     * 这里同样按 fitCenter（保持比例、居中）渲染到 size x size 的画布。
     */
    private static BufferedImage generateThumbnail(mapNode nd, int size) {
        try {
            BufferedImage thumb = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            if (nd.Map == null || nd.Map.equals("--")) {
                return thumb;
            }

            String[] lines = nd.Map.split("\r\n|\n\r|\n|\\|");
            int rows = nd.Rows;
            int cols = nd.Cols;
            if (rows <= 0 || cols <= 0) return thumb;

            // 源分辨率取显示尺寸的约 2 倍，降采样时细节损失最小
            int tileSize = Math.max(4, (size * 2) / Math.max(cols, rows));
            BufferedImage full = new BufferedImage(cols * tileSize, rows * tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = full.createGraphics();

            for (int r = 0; r < rows; r++) {
                if (r >= lines.length) break;
                for (int c = 0; c < cols; c++) {
                    if (c >= lines[r].length()) break;
                    drawTile(g, lines[r].charAt(c), c * tileSize, r * tileSize, tileSize);
                }
            }
            g.dispose();

            double scale = Math.min((double) size / full.getWidth(), (double) size / full.getHeight());
            int sw = Math.max(1, (int) Math.round(full.getWidth() * scale));
            int sh = Math.max(1, (int) Math.round(full.getHeight() * scale));

            Graphics2D tg = thumb.createGraphics();
            tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            tg.drawImage(full, (size - sw) / 2, (size - sh) / 2, sw, sh, null);
            tg.dispose();
            return thumb;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void drawTile(Graphics2D g, char ch, int x, int y, int size) {
        switch (ch) {
            case '#':
                if (myMaps.WallPic != null) g.drawImage(myMaps.WallPic, x, y, size, size, null);
                break;
            case '-':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                break;
            case '.':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                break;
            case '$':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxPic != null) g.drawImage(myMaps.BoxPic, x, y, size, size, null);
                break;
            case '*':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxGoalPic != null) g.drawImage(myMaps.BoxGoalPic, x, y, size, size, null);
                break;
            case '@':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.ManPic_d != null) g.drawImage(myMaps.ManPic_d, x, y, size, size, null);
                break;
            case '+':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                if (myMaps.ManGoalPic_d != null) g.drawImage(myMaps.ManGoalPic_d, x, y, size, size, null);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ 自检接口

    /** 供测试与自检访问 ActionBar（原版 getActionBar()） */
    public myActionBar getActionBar() {
        return actionBar;
    }

    /** 当前网格列数 */
    public int getColumnCount() {
        return myMaps.m_Sets[2] == 1 ? 1 : mCols;
    }

    /** 当前单元边长（列宽） */
    public int getCellWidth() {
        return cellWidth;
    }

    /** 当前关卡卡片数量 */
    public int getCardCount() {
        return cardList.size();
    }

    /** 关卡集标题条是否可见（原版 m_gridTitleView.getVisibility()） */
    public boolean isHeaderVisible() {
        return headerBar.isVisible();
    }

    /** 标题条上的「全选」勾选框是否可见（原版 m_select_all，仅多选模式可见） */
    public boolean isSelectAllVisible() {
        return my_SelectAll.isVisible();
    }

    /** 供测试/自检：切换多选模式（等同点击溢出菜单的「多选模式」） */
    public void setSelectModeForTest(boolean on) {
        toggleSelectMode(on);
    }

    /**
     * 供测试/自检：等待所有单元缩略图异步加载完成。
     * 离屏快照若不等待，会拍到尚未回填图标的空白单元。
     */
    public boolean awaitThumbnails(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ok = {true};
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (LevelCard c : cardList) {
                        if (c.lblThumb == null || c.lblThumb.getIcon() == null) {
                            ok[0] = false;
                            return;
                        }
                    }
                });
            } catch (Exception e) {
                return false;
            }
            if (ok[0]) return true;
            Thread.sleep(20);
        }
        return false;
    }

    // ------------------------------------------------------------------ 隐藏滚动条

    /** 原版 Android 静止时不显示滚动条；这里保持 0 宽且不绘制，仅保留滚轮滚动能力 */
    private static class HiddenScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
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
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        }
    }

    // ---------------------------------------------------------------- 测试钩子

    /** 供测试读取 ActionBar 的菜单状态（可见性/启用态/勾选态）。 */
    myActionBar getActionBarForTest() {
        return actionBar;
    }

    /** 供测试读取「╋」在非「创编关卡」下弹出的两项菜单。 */
    JPopupMenu getAddMenuForTest() {
        return addMenuForTest;
    }

    /** 供测试触发「╋」而不真的弹窗。 */
    void addForTest() {
        onAdd();
    }

    /** 供测试构建导入选项面板（{@code import_dialog.xml} 的等价物）。 */
    JComponent buildImportOptionsForTest(boolean withEncoding) {
        return buildImportOptions(withEncoding);
    }
}
