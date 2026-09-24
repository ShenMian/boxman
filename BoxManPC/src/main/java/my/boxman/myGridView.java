package my.boxman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
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

    public myGridView(long setId, String setTitle) {
        this.mSetId = setId;
        myMaps.m_Set_id = setId;
        myMaps.sFile = setTitle != null ? setTitle : "关卡列表";

        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initAppEnvironment();
        initUI();
        loadData();
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

        actionBar.addBarAction(A_ADD, false, myActionBar.NO_OP);       // 尚未移植
        actionBar.addBarAction(A_TOP, () -> scrollToPosition(true));
        actionBar.addBarAction(A_BOTTOM, () -> scrollToPosition(false));

        actionBar.addAction(A_GOTO, this::doGoto);
        actionBar.addAction(A_SELECT, () -> toggleSelectMode(!myMaps.isSelect));
        actionBar.addAction(A_SHOWTITLE, () -> toggleShowTitle(myMaps.m_Sets[2] != 1));
        actionBar.addAction(A_SHOWDUP, () -> toggleShowDup(myMaps.m_Sets[12] != 1));
        actionBar.addAction(A_FIRST_UNSOLVED, this::openFirstUnsolved);
        actionBar.addAction(A_RECENT, this::openRecentLevel);
        actionBar.addAction(A_CLEAR, false, myActionBar.NO_OP);        // 尚未移植
        actionBar.addAction(A_COLCOUNT, this::chooseColumnCount);
        actionBar.addAction(A_DELETE_MORE, false, myActionBar.NO_OP);  // 尚未移植
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
            JPopupMenu menu = new JPopupMenu();
            JMenuItem miOpen = new JMenuItem("推此关卡");
            miOpen.addActionListener(act -> openGame(node, index));
            menu.add(miOpen);

            JMenuItem miEdit = new JMenuItem("编辑关卡...");
            miEdit.addActionListener(act -> {
                myMaps.curMap = node;
                myEditView ev = new myEditView();
                ev.setVisible(true);
            });
            menu.add(miEdit);

            JMenuItem miExport = new JMenuItem("导出关卡...");
            miExport.addActionListener(act -> {
                myMaps.curMap = node;
                myExport exp = new myExport();
                exp.setVisible(true);
            });
            menu.add(miExport);

            JMenuItem miCopyXsb = new JMenuItem("复制 XSB 到剪贴板");
            miCopyXsb.addActionListener(act -> {
                if (node.Map != null) {
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(node.Map), null);
                        MyToast.showToast(myGridView.this, "已复制第 " + (index + 1) + " 关 XSB 数据！", MyToast.LENGTH_SHORT);
                    } catch (Exception ignored) {
                    }
                }
            });
            menu.add(miCopyXsb);

            menu.addSeparator();

            JMenuItem miDetail = new JMenuItem("关卡详细信息...");
            miDetail.addActionListener(act -> new myAbout2(myGridView.this, node).setVisible(true));
            menu.add(miDetail);

            JMenuItem miDelete = new JMenuItem("删除此关卡...");
            miDelete.addActionListener(act -> {
                DelDialog dlg = new DelDialog(null, node.Title, delAns -> {
                    if (mySQLite.m_SQL != null && node.Level_id > 0) {
                        mySQLite.m_SQL.del_L(node.Level_id);
                        if (delAns) {
                            mySQLite.m_SQL.del_S_ALL(node.Level_id);
                        }
                        myMaps.m_lstMaps.remove(node);
                        refreshGrid();
                    }
                });
                dlg.setVisible(true);
            });
            menu.add(miDelete);

            menu.show(this, e.getX(), e.getY());
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
}
