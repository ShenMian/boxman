package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.HoloViewDialog;
import my.boxman.compat.android.graphics.Matrix;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import my.boxman.compat.UiWindow;

/**
 * Level Editor for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myEditView Activity.
 */
public class myEditView extends JFrame {

    public myEditViewMap mMap;

    // Bottom action toggle buttons
    public JToggleButton bt_UnDo;
    public JToggleButton bt_ReDo;
    public JToggleButton bt_Cut;
    public JToggleButton bt_Copy;
    public JToggleButton bt_Paste;
    public JToggleButton bt_Tru;
    public JToggleButton bt_Save;
    public JToggleButton bt_More;

    public char[][] m_cArray, m_cSelArray;
    public int selRows, selCols, selRows2, selCols2;
    int mWhich = -1;      // 「提交到」对话框里选中的关卡集下标
    int m_nItemSelect;

    LinkedList<ActNode> m_UnDoList = new LinkedList<>();
    LinkedList<ActNode> m_ReDoList = new LinkedList<>();
    ActNode ndAct;
    mapNode old_Map;

    /** 退出确认框（原版 onCreate 里建好的 exitDlg）。 */
    private HoloConfirmDialog exitDlg;
    /** 最近一次 {@link #openOptionsMenu()} 建出的菜单，供测试取用。 */
    private JPopupMenu optionsMenu;
    /** 「更多」按钮本次按下是否已按长按处理掉（原版 onLongClick 返回 true → 吞掉 click）。 */
    private boolean moreLongPressed;
    private javax.swing.Timer moreLongPressTimer;

    public static class ActNode {
        char ch;
        int row, col;
        int left, top, right, bottom;
        char[][] map;
        selNode sel1, sel2;
        Matrix mMtx, mCurMtx;
        int act;

        public ActNode(char[][] m, int l, int r, int t, int b, int m_row, int m_col,
                       Matrix mtx0, Matrix mtx1, selNode s1, selNode s2) {
            this.left = l;
            this.right = r;
            this.top = t;
            this.bottom = b;
            this.row = m_row;
            this.col = m_col;
            if (s1 != null) {
                this.sel1 = new selNode();
                this.sel1.row = s1.row;
                this.sel1.col = s1.col;
            }
            if (s2 != null) {
                this.sel2 = new selNode();
                this.sel2.row = s2.row;
                this.sel2.col = s2.col;
            }
            if (mtx0 != null) {
                this.mMtx = new Matrix();
                this.mMtx.set(mtx0);
            }
            if (mtx1 != null) {
                this.mCurMtx = new Matrix();
                this.mCurMtx.set(mtx1);
            }
            if (m != null) {
                this.map = new char[b - t + 1][r - l + 1];
                for (int i = t; i <= b; i++) {
                    for (int j = l; j <= r; j++) {
                        this.map[i - t][j - l] = m[i][j];
                    }
                }
            }
        }

        public void Act(int a) {
            this.act = a;
        }

        public void setMap(char[][] target) {
            if (map != null && target != null) {
                for (int i = top; i <= bottom; i++) {
                    for (int j = left; j <= right; j++) {
                        target[i][j] = map[i - top][j - left];
                    }
                }
            }
        }
    }

    public myEditView() {
        setTitle("关卡编辑器 - 推箱快手");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExitRequest();
            }
        });

        initUI();

        // ⚠️ 必须最后调：内容区要锁成 370×780 竖屏。见 UiWindow 的说明。
        UiWindow.applyPhoneSize(this);
    }

    private void initUI() {
        mMap = new myEditViewMap();
        mMap.Init(this);

        m_cSelArray = new char[myMaps.m_nMaxRow][myMaps.m_nMaxCol];
        m_cArray = new char[myMaps.m_nMaxRow * 2][myMaps.m_nMaxCol * 2];
        for (int i = 0; i < myMaps.m_nMaxRow * 2; i++) {
            Arrays.fill(m_cArray[i], '-');
        }

        old_Map = myMaps.curMap;
        myMaps.isSaveBlock = false;
        initMap();

        buildExitDialog();

        setLayout(new BorderLayout());
        add(mMap, BorderLayout.CENTER);

        JPanel bottomBar = createBottomBar();
        add(bottomBar, BorderLayout.SOUTH);

        // 原版 myEditView 是 FEATURE_NO_TITLE + FLAG_FULLSCREEN：既没有 ActionBar 也没有菜单栏，
        // 13 项菜单全在底栏「更多」按钮弹出的选项菜单里（res/menu/edit.xml，见 openOptionsMenu()）。
        // 所以这里不安装 JMenuBar —— 那会多出一行、并挖走内容区 23px。
    }

    /**
     * 原版 {@code onCreate()} 里建好的退出确认框：「有修改未保存，坚持退出吗？」（否 / 是）。
     * 「离开」菜单项与「更多」按钮的长按都会用到它。
     */
    private void buildExitDialog() {
        exitDlg = new HoloConfirmDialog(this, "提醒",
                "有修改未保存，坚持退出吗？", "否", "是", this::dispose);
    }

    private JPanel createBottomBar() {
        JPanel bar = new JPanel(new GridLayout(1, 8, 4, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        bt_UnDo = new JToggleButton("撤销");
        bt_UnDo.setEnabled(false);
        bt_UnDo.addActionListener(e -> {
            bt_UnDo.setSelected(false);
            if (!m_UnDoList.isEmpty()) myUnDo();
        });

        bt_ReDo = new JToggleButton("重做");
        bt_ReDo.setEnabled(false);
        bt_ReDo.addActionListener(e -> {
            bt_ReDo.setSelected(false);
            if (!m_ReDoList.isEmpty()) myReDo();
        });

        bt_Cut = new JToggleButton("剪切");
        bt_Cut.setEnabled(false);
        bt_Cut.addActionListener(e -> {
            bt_Cut.setSelected(false);
            doCut();
        });

        bt_Copy = new JToggleButton("复制");
        bt_Copy.setEnabled(false);
        bt_Copy.addActionListener(e -> {
            bt_Copy.setSelected(false);
            doCopy();
        });

        bt_Paste = new JToggleButton("粘贴");
        bt_Paste.setEnabled(true);
        bt_Paste.addActionListener(e -> {
            bt_Paste.setSelected(false);
            doPaste();
        });

        bt_Tru = new JToggleButton("变换");
        bt_Tru.addActionListener(e -> {
            bt_Tru.setSelected(false);
            showTransformMenu();
        });

        bt_Save = new JToggleButton("保存");
        bt_Save.setEnabled(false);
        bt_Save.addActionListener(e -> {
            bt_Save.setSelected(false);
            doSave();
        });

        bt_More = new JToggleButton("更多");
        bt_More.addActionListener(e -> {
            bt_More.setSelected(false);
            if (!moreLongPressed) openOptionsMenu();   // 长按已经处理过了，别再弹菜单
            moreLongPressed = false;
        });
        installMoreLongPress();

        bar.add(bt_UnDo);
        bar.add(bt_ReDo);
        bar.add(bt_Cut);
        bar.add(bt_Copy);
        bar.add(bt_Paste);
        bar.add(bt_Tru);
        bar.add(bt_Save);
        bar.add(bt_More);

        return bar;
    }

    /**
     * 「更多」按钮的长按 = 原版 {@code bt_More.setOnLongClickListener}：
     * 弹「离开！」提示，然后 {@code if (!bt_Save.isEnabled()) finish(); else exitDlg.show();}，
     * 并且 {@code return true} —— 长按后**吞掉**随后的 click（否则菜单会跟着弹出来）。
     *
     * <p>长按阈值用 Android 的 {@code ViewConfiguration.getLongPressTimeout()} = 500ms。
     */
    private void installMoreLongPress() {
        moreLongPressTimer = new javax.swing.Timer(500, e -> {
            moreLongPressTimer.stop();
            moreLongPressed = true;
            MyToast.showToast(this, "离开！", MyToast.LENGTH_SHORT);
            if (!bt_Save.isEnabled()) dispose();
            else if (exitDlg != null) exitDlg.setVisible(true);
        });
        moreLongPressTimer.setRepeats(false);

        bt_More.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                moreLongPressed = false;
                moreLongPressTimer.start();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                moreLongPressTimer.stop();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                moreLongPressTimer.stop();
            }
        });
    }

    /**
     * 原版 {@code openOptionsMenu()}，由底栏「更多」按钮触发。
     *
     * <p>菜单项与顺序严格按 {@code res/menu/edit.xml} 全 13 项。原版这些项都没写
     * {@code showAsAction}，所以全在溢出／选项菜单里 —— 这里用 {@link HoloPopupMenu}
     * （与 ActionBar 溢出菜单同一套 {@code popup_menu_holo_dark} 样式）呈现。
     */
    public void openOptionsMenu() {
        JPopupMenu menu = HoloPopupMenu.create();

        HoloPopupMenu.addItem(menu, "提交", this::onSubmit);
        HoloPopupMenu.addItem(menu, "试推", this::onPlayTest);
        HoloPopupMenu.addItem(menu, "标尺...", this::onRule);
        HoloPopupMenu.addItem(menu, "设置...", this::onSetup);
        HoloPopupMenu.addItem(menu, "清空地图", this::onClear);
        HoloPopupMenu.addItem(menu, "区块另存为...", this::onBlockSave);
        HoloPopupMenu.addItem(menu, "导出(XSB)", this::myExport);
        HoloPopupMenu.addItem(menu, "导入(XSB 或 Lurd)", this::myImport);
        HoloPopupMenu.addItem(menu, "修改尺寸...", this::onResize);
        HoloPopupMenu.addItem(menu, "关卡标准化", this::onNormalize);
        HoloPopupMenu.addItem(menu, "关卡资料", this::onInf);
        HoloPopupMenu.addItem(menu, "操作说明", () -> new Help(2).setVisible(true));
        HoloPopupMenu.addItem(menu, "离开", this::onExit);

        optionsMenu = menu;
        if (bt_More.isShowing()) {
            menu.show(bt_More, 0, -menu.getPreferredSize().height);
        }
    }

    /** 供测试调用：不弹窗，只把菜单建出来。 */
    public JPopupMenu optionsMenuForTest() {
        if (optionsMenu == null) openOptionsMenu();
        return optionsMenu;
    }

    private void initMap() {
        mMap.m_nMapLeft = myMaps.m_nMaxCol;
        mMap.m_nMapTop = myMaps.m_nMaxRow;
        int rows = (myMaps.curMap != null && myMaps.curMap.Rows > 0) ? myMaps.curMap.Rows : 15;
        int cols = (myMaps.curMap != null && myMaps.curMap.Cols > 0) ? myMaps.curMap.Cols : 10;
        mMap.m_nMapRight = mMap.m_nMapLeft + cols - 1;
        mMap.m_nMapBottom = mMap.m_nMapTop + rows - 1;

        loadLevel();
        mMap.initArena();

        m_UnDoList.clear();
        m_ReDoList.clear();
    }

    private void loadLevel() {
        if (myMaps.curMap != null && myMaps.curMap.Map != null) {
            String[] arr = myMaps.curMap.Map.split("\r\n|\n\r|\n|\r|\\|");
            for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
                int r = i - mMap.m_nMapTop;
                for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                    int c = j - mMap.m_nMapLeft;
                    if (r < arr.length && c < arr[r].length() && isOK(arr[r].charAt(c))) {
                        m_cArray[i][j] = arr[r].charAt(c);
                    } else {
                        m_cArray[i][j] = '-';
                    }
                }
            }
        } else {
            for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
                for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                    m_cArray[i][j] = '-';
                }
            }
        }
    }

    public boolean isOK(char c) {
        return c == '#' || c == '.' || c == '$' || c == '*' || c == '@' || c == '+';
    }

    public String getBoxs() {
        int boxes = 0, goals = 0;
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                char ch = m_cArray[i][j];
                if (ch == '$' || ch == '*') boxes++;
                if (ch == '.' || ch == '*' || ch == '+') goals++;
            }
        }
        return " [箱:" + boxes + " 标:" + goals + "]";
    }

    public void DoAct(int act) {
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                mMap.m_iR, mMap.m_iC, null, null, null, null);
        ndAct.Act(act);
        m_UnDoList.offer(ndAct);
        bt_UnDo.setEnabled(true);
        m_ReDoList.clear();
        bt_ReDo.setEnabled(false);
        bt_Save.setEnabled(true);
    }

    private void myUnDo() {
        if (m_UnDoList.isEmpty()) return;
        ActNode nd = m_UnDoList.pollLast();
        ActNode nd2 = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                nd.row, nd.col, null, null, null, null);
        nd2.Act(nd.act);

        nd.setMap(m_cArray);
        m_ReDoList.offer(nd2);
        bt_ReDo.setEnabled(true);
        bt_UnDo.setEnabled(!m_UnDoList.isEmpty());
        bt_Save.setEnabled(true);
        mMap.repaint();
    }

    private void myReDo() {
        if (m_ReDoList.isEmpty()) return;
        ActNode nd = m_ReDoList.pollLast();
        ActNode nd2 = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                nd.row, nd.col, null, null, null, null);
        nd2.Act(nd.act);

        nd.setMap(m_cArray);
        m_UnDoList.offer(nd2);
        bt_UnDo.setEnabled(true);
        bt_ReDo.setEnabled(!m_ReDoList.isEmpty());
        bt_Save.setEnabled(true);
        mMap.repaint();
    }

    private void doCut() {
        if (mMap.selNode.row < 0) return;
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, null, null, mMap.selNode, mMap.selNode2);
        ndAct.Act(1);

        StringBuilder str = new StringBuilder();
        selRows = mMap.selNode2.row - mMap.selNode.row + 1;
        selCols = mMap.selNode2.col - mMap.selNode.col + 1;
        for (int i = 0; i < selRows; i++) {
            for (int j = 0; j < selCols; j++) {
                char ch = m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft];
                str.append(isOK(ch) ? ch : '-');
                m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft] = '-';
            }
            if (i < selRows - 1) str.append('\n');
        }
        saveClipper(str.toString());

        m_UnDoList.offer(ndAct);
        bt_UnDo.setEnabled(true);
        m_ReDoList.clear();
        bt_ReDo.setEnabled(false);
        bt_Save.setEnabled(true);
        mMap.repaint();
    }

    private void doCopy() {
        if (mMap.selNode.row < 0) return;
        StringBuilder str = new StringBuilder();
        selRows = mMap.selNode2.row - mMap.selNode.row + 1;
        selCols = mMap.selNode2.col - mMap.selNode.col + 1;
        for (int i = 0; i < selRows; i++) {
            for (int j = 0; j < selCols; j++) {
                char ch = m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft];
                str.append(isOK(ch) ? ch : '-');
            }
            if (i < selRows - 1) str.append('\n');
        }
        saveClipper(str.toString());
    }

    private void doPaste() {
        if (mMap.selNode.row < 0) return;
        String str = loadClipper();
        if (str == null || str.trim().isEmpty()) return;

        String[] lines = str.split("\r\n|\n\r|\n|\r|\\|");
        selRows2 = lines.length;
        selCols2 = 0;
        for (String l : lines) if (l.length() > selCols2) selCols2 = l.length();

        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, null, null, mMap.selNode, mMap.selNode2);
        ndAct.Act(2);

        for (int i = 0; i < selRows2; i++) {
            for (int j = 0; j < selCols2; j++) {
                char ch = (j < lines[i].length()) ? lines[i].charAt(j) : '-';
                int r = mMap.selNode.row + i + mMap.m_nMapTop;
                int c = mMap.selNode.col + j + mMap.m_nMapLeft;
                if (r <= mMap.m_nMapBottom && c <= mMap.m_nMapRight) {
                    m_cArray[r][c] = isOK(ch) ? ch : '-';
                }
            }
        }

        m_UnDoList.offer(ndAct);
        bt_UnDo.setEnabled(true);
        m_ReDoList.clear();
        bt_ReDo.setEnabled(false);
        bt_Save.setEnabled(true);
        mMap.repaint();
    }

    private void showTransformMenu() {
        if (mMap.selNode.row < 0) mMap.mySelectAll();
        String[] opts = {"180度旋转", "90度顺时针", "90度逆时针", "水平翻转", "垂直翻转"};
        int choice = JOptionPane.showOptionDialog(this, "选择变换操作", "变换",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
        if (choice >= 0) {
            myRotate(choice);
        }
    }

    public void myRotate(int which) {
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, null, null, mMap.selNode, mMap.selNode2);
        ndAct.Act(90);

        selRows = mMap.selNode2.row - mMap.selNode.row + 1;
        selCols = mMap.selNode2.col - mMap.selNode.col + 1;
        char[][] temp = new char[selRows][selCols];
        for (int i = 0; i < selRows; i++) {
            for (int j = 0; j < selCols; j++) {
                temp[i][j] = m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft];
            }
        }

        if (which == 3) {  // 水平翻转
            for (int i = 0; i < selRows; i++) {
                for (int j = 0; j < selCols; j++) {
                    m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft] = temp[i][selCols - 1 - j];
                }
            }
        } else if (which == 4) {  // 垂直翻转
            for (int i = 0; i < selRows; i++) {
                for (int j = 0; j < selCols; j++) {
                    m_cArray[mMap.selNode.row + i + mMap.m_nMapTop][mMap.selNode.col + j + mMap.m_nMapLeft] = temp[selRows - 1 - i][j];
                }
            }
        }

        m_UnDoList.offer(ndAct);
        bt_UnDo.setEnabled(true);
        bt_Save.setEnabled(true);
        mMap.repaint();
    }

    public void My_ReSize(int side) {
        switch (side) {
            case 0: if (mMap.m_nMapLeft > 1) mMap.m_nMapLeft--; break;
            case 1: if (mMap.m_nMapTop > 1) mMap.m_nMapTop--; break;
            case 2: if (mMap.m_nMapRight < myMaps.m_nMaxCol * 2 - 2) mMap.m_nMapRight++; break;
            case 3: if (mMap.m_nMapBottom < myMaps.m_nMaxRow * 2 - 2) mMap.m_nMapBottom++; break;
        }
        mMap.initArena();
        mMap.repaint();
    }

    /** {@code edit_resize}「修改尺寸...」：四个 Spinner(0~30) + 「扩充 / 消减」单选组。 */
    private void onResize() {
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, mMap.mMatrix, mMap.mCurrentMatrix, null, null);
        ndAct.Act(4);

        JSpinner spLeft = HoloContent.spinner(56, 0, 0, 30);
        JSpinner spRight = HoloContent.spinner(56, 0, 0, 30);
        JSpinner spTop = HoloContent.spinner(56, 0, 0, 30);
        JSpinner spBottom = HoloContent.spinner(56, 0, 0, 30);

        ButtonGroup group = new ButtonGroup();
        JRadioButton rbExtend = HoloContent.radio("扩充", true);    // mySign = +1
        JRadioButton rbShrink = HoloContent.radio("消减", false);   // mySign = -1
        group.add(rbExtend);
        group.add(rbShrink);

        JComponent body = HoloContent.column(
                HoloContent.row(HoloContent.label("左", 32, SwingConstants.LEFT), spLeft,
                        HoloContent.label("右", 32, SwingConstants.LEFT), spRight),
                HoloContent.row(HoloContent.label("上", 32, SwingConstants.LEFT), spTop,
                        HoloContent.label("下", 32, SwingConstants.LEFT), spBottom),
                HoloContent.row(rbExtend, rbShrink));

        HoloViewDialog dlg = new HoloViewDialog(this,
                "当前尺寸：" + (mMap.m_nMapRight - mMap.m_nMapLeft + 1)
                        + " × " + (mMap.m_nMapBottom - mMap.m_nMapTop + 1), body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            int sign = rbExtend.isSelected() ? 1 : -1;
            int newLeft = sign * (Integer) spLeft.getValue();
            int newRight = sign * (Integer) spRight.getValue();
            int newTop = sign * (Integer) spTop.getValue();
            int newBottom = sign * (Integer) spBottom.getValue();

            String err = resizeError(newLeft, newRight, newTop, newBottom);
            if (err != null) {
                dlg.dispose();
                new HoloMessageDialog(this, "错误", err, "确定").setVisible(true);
                return;
            }

            // 新扩出来的格子一律置为 '-'
            for (int i = mMap.m_nMapTop - newTop; i <= mMap.m_nMapBottom + newBottom; i++) {
                for (int j = mMap.m_nMapLeft - newLeft; j <= mMap.m_nMapRight + newRight; j++) {
                    if (i < mMap.m_nMapTop || i > mMap.m_nMapBottom
                            || j < mMap.m_nMapLeft || j > mMap.m_nMapRight) {
                        m_cArray[i][j] = '-';
                    }
                }
            }
            mMap.m_nMapLeft -= newLeft;
            mMap.m_nMapTop -= newTop;
            mMap.m_nMapRight += newRight;
            mMap.m_nMapBottom += newBottom;
            mMap.initArena();

            pushUndo();
            mMap.repaint();
            dlg.dispose();
        });
        dlg.setVisible(true);
    }

    /**
     * 原版「修改尺寸」确定后的 8 条越界校验，逐条照抄（含文案与括号里的数字）。
     *
     * @return {@code null} 表示通过；否则返回拼好的错误文案（多行）
     */
    String resizeError(int newLeft, int newRight, int newTop, int newBottom) {
        int newCols = newLeft + newRight + mMap.m_nMapRight - mMap.m_nMapLeft + 1;
        int newRows = newTop + newBottom + mMap.m_nMapBottom - mMap.m_nMapTop + 1;

        StringBuilder str = new StringBuilder();
        if (newCols < 3) str.append("关卡宽度不能小于3\n");
        if (newRows < 3) str.append("关卡高度不能小于3\n");
        if (newCols > myMaps.m_nMaxCol) str.append("关卡超宽（").append(myMaps.m_nMaxCol).append("）\n");
        if (newRows > myMaps.m_nMaxRow) str.append("关卡超高（").append(myMaps.m_nMaxRow).append("）\n");
        if (newLeft > mMap.m_nMapLeft - 1)
            str.append("左侧空间不足（").append(mMap.m_nMapLeft - 1).append("）\n");
        if (newTop > mMap.m_nMapTop - 1)
            str.append("顶部空间不足（").append(mMap.m_nMapTop - 1).append("）\n");
        if (newRight + mMap.m_nMapRight >= myMaps.m_nMaxCol * 2 - 1)
            str.append("右侧空间不足（").append(myMaps.m_nMaxCol * 2 - 1 - mMap.m_nMapRight).append("）\n");
        if (newBottom + mMap.m_nMapBottom >= myMaps.m_nMaxRow * 2 - 1)
            str.append("底部空间不足（").append(myMaps.m_nMaxRow * 2 - 1 - mMap.m_nMapBottom).append("）\n");

        return str.length() > 0 ? str.toString().trim() : null;
    }

    /** {@code edit_normalize}「关卡标准化」。 */
    void onNormalize() {
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, mMap.mMatrix, mMap.mCurrentMatrix, null, null);
        ndAct.Act(9);

        Normalize(m_cArray);
        mMap.initArena();

        pushUndo();
        mMap.repaint();
    }

    /** {@code edit_clear}「清空地图」：确认框是「取消 / 确定」（原版 取消/确定）。 */
    private void onClear() {
        new HoloConfirmDialog(this, "确认", "地图将被清空，确定吗？",
                "取消", "确定", this::clearMap).setVisible(true);
    }

    /** 「清空地图」确认后的实际动作（原版确认框里的 {@code onClick}）。 */
    void clearMap() {
        ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                -1, -1, null, null, null, null);
        ndAct.Act(6);

        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                m_cArray[i][j] = '-';
            }
        }

        mMap.selNode.row = -1;      // 避免显示选择区域块
        mMap.isFistClick = true;    // 准备记录第一坐标点
        pushUndo();
        bt_Cut.setEnabled(false);
        bt_Copy.setEnabled(false);
        mMap.repaint();
    }

    /**
     * 把 {@link #ndAct} 压进撤销栈（原版每个菜单项收尾都重复这一段）。
     * 注意「清空地图」后剪切/复制也要置灰，所以那两项由调用方单独处理。
     */
    private void pushUndo() {
        m_UnDoList.offer(ndAct);
        bt_UnDo.setEnabled(true);
        m_ReDoList.clear();
        bt_ReDo.setEnabled(false);
        bt_Save.setEnabled(true);
    }

    /** 原版 {@code bt_Save} 的 {@code onCheckedChanged}：存盘到「创编关卡/」下。 */
    private boolean doSave() {
        return saveFile(myMaps.curMap != null ? myMaps.curMap.fileName : "");
    }

    /**
     * 原版 {@code saveFile(my_Name)}：把当前画布写成 XSB 文档，并回写
     * {@code myMaps.curMap} 的 Map/Rows/Cols。
     */
    boolean saveFile(String my_Name) {
        try {
            File dir = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/");
            if (!dir.exists() && !dir.mkdirs()) return false;
            try (FileOutputStream fout = new FileOutputStream(
                    myMaps.sRoot + myMaps.sPath + "创编关卡/" + my_Name)) {

                StringBuilder str = new StringBuilder();
                for (int r = mMap.m_nMapTop; r <= mMap.m_nMapBottom; r++) {
                    for (int c = mMap.m_nMapLeft; c <= mMap.m_nMapRight; c++) {
                        str.append(m_cArray[r][c]);
                    }
                    if (r < mMap.m_nMapBottom) str.append('\n');
                }

                myMaps.curMap.Map = str.toString();
                myMaps.curMap.Rows = mMap.m_nMapBottom - mMap.m_nMapTop + 1;
                myMaps.curMap.Cols = mMap.m_nMapRight - mMap.m_nMapLeft + 1;

                str.append("\nTitle: ").append(myMaps.curMap.Title);
                str.append("\nAuthor: ").append(myMaps.curMap.Author);
                str.append("\nComment:");
                str.append('\n').append(myMaps.curMap.Comment);
                int len = myMaps.curMap.Comment.length() - 1;
                if (len >= 0 && myMaps.curMap.Comment.charAt(len) != '\n'
                        && myMaps.curMap.Comment.charAt(len) != '\r') {
                    str.append('\n');
                }
                str.append("Comment_end:");

                fout.write(str.toString().getBytes());
                fout.flush();
            }

            if (myMaps.curMapNum < -1) {   // 新建关卡或创编为新关卡
                myMaps.m_lstMaps.add(myMaps.curMap);
                myMaps.curMapNum = myMaps.m_lstMaps.size() - 1;
            } else {                       // 编辑关卡
                myMaps.m_lstMaps.set(myMaps.curMapNum, myMaps.curMap);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** {@code edit_complete}「提交」：先按需存盘，再弹「提交到」关卡集单选框。 */
    private void onSubmit() {
        if (bt_Save.isEnabled()) {   // 提交前，若需保存
            if (saveFile(myMaps.curMap.fileName)) {
                MyToast.showToast(this, "关卡已保存！", MyToast.LENGTH_LONG);
                bt_Save.setEnabled(false);
            } else {
                MyToast.showToast(this, "出错了，关卡未保存！", MyToast.LENGTH_LONG);
            }
        }

        final String[] myList = new String[myMaps.mSets3.size() + 1];
        for (int k = 0; k < myMaps.mSets3.size(); k++) {
            myList[k] = myMaps.mSets3.get(k).title;
        }
        myList[myList.length - 1] = myMaps.getNewSetName();   // 末尾自动加一个新的关卡集

        if (mWhich < 0 || mWhich >= myList.length) mWhich = 0;

        HoloChoiceDialog.selectThenOk(this, "提交到", null, myList, mWhich, which -> {
            mWhich = which;
            // 选中的是最后一个「新建关卡集」
            if (mWhich == myList.length - 1) {
                try {
                    long newId = mySQLite.m_SQL.add_T(3, myList[myList.length - 1], "", "");
                    set_Node nd = new set_Node();
                    nd.id = newId;
                    nd.title = myList[myList.length - 1];
                    myMaps.mSets3.add(nd);
                } catch (Exception e) {
                    MyToast.showToast(this, "新关卡集创建失败: " + myList[myList.length - 1],
                            MyToast.LENGTH_SHORT);
                    return;
                }
            }

            ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                    -1, -1, mMap.mMatrix, mMap.mCurrentMatrix, null, null);
            ndAct.Act(8);

            if (mWhich >= 0 && Normalize2(m_cArray)) {
                mMap.initArena();
                // Normalize2() 里已经重新定义了 myMaps.curMap 的 Rows、Cols、Map
                mapNode nd = new mapNode(myMaps.curMap.Map, myMaps.curMap.Title,
                        myMaps.curMap.Author, myMaps.curMap.Comment);
                long lvlId = mySQLite.m_SQL.add_L(myMaps.mSets3.get(mWhich).id, nd);
                if (lvlId > 0) {
                    MyToast.showToast(this, "提交成功！", MyToast.LENGTH_SHORT);
                    m_UnDoList.offer(ndAct);
                    bt_UnDo.setEnabled(true);
                    m_ReDoList.clear();
                    bt_ReDo.setEnabled(false);
                } else {
                    MyToast.showToast(this, "出错了，提交失败！", MyToast.LENGTH_SHORT);
                }
            }
        }).setVisible(true);
    }

    /** {@code edit_play}「试推」：先按需存盘，再用 {@code mapNode} 校验后打开游戏界面。 */
    private void onPlayTest() {
        if (bt_Save.isEnabled()) {   // 试推前，若需保存
            if (saveFile(myMaps.curMap.fileName)) {
                MyToast.showToast(this, "关卡已保存！", MyToast.LENGTH_LONG);
                bt_Save.setEnabled(false);
            } else {
                MyToast.showToast(this, "出错了，关卡未保存！", MyToast.LENGTH_LONG);
            }
        }
        old_Map = myMaps.curMap;
        // saveFile() 中已经重新定义了 myMaps.curMap 的 Rows、Cols、Map
        try {
            mapNode nd = new mapNode(myMaps.curMap.Map, myMaps.curMap.Title,
                    myMaps.curMap.Author, myMaps.curMap.Comment);
            if (nd.L_CRC_Num != -1) {
                myMaps.curMap = nd;
                myMaps.curMap.fileName = old_Map.fileName;
                myMaps.curMap.Level_id = -1;
                new myGameView().setVisible(true);
            } else {
                MyToast.showToast(this, "关卡尚不规范！", MyToast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            MyToast.showToast(this, "请检查关卡是否规范！", MyToast.LENGTH_SHORT);
        }
    }

    /** {@code edit_block_save}「区块另存为...」。 */
    void onBlockSave() {
        myBlockSave(m_cArray);
    }

    /** 原版 {@code myBlockSave(char[][])}：把当前选区另存为一个 XSB 文档并挂进关卡列表。 */
    void myBlockSave(char[][] m_Level) {
        if (mMap.selNode.row < 0) {                     // 没有选区
            MyToast.showToast(this, "请选择一个区块！", MyToast.LENGTH_LONG);
            return;
        }

        int r1 = mMap.selNode.row;
        int r2 = mMap.selNode2.row;
        int c1 = mMap.selNode.col;
        int c2 = mMap.selNode2.col;

        if (r2 - r1 < 1 || c2 - c1 < 1) {
            MyToast.showToast(this, "区块太小！", MyToast.LENGTH_LONG);
            return;
        }

        try {
            StringBuilder str = new StringBuilder();
            for (int i = r1; i <= r2; i++) {
                for (int j = c1; j <= c2; j++) {
                    char ch = m_Level[i + mMap.m_nMapTop][j + mMap.m_nMapLeft];
                    str.append(isOK(ch) ? ch : '-');
                }
                str.append('\n');
            }

            SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String fn = "~Block_" + df.format(new Date());
            str.append("Title: ").append(fn);           // 给块一个标题

            File dir = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed");
            try (FileOutputStream fout = new FileOutputStream(
                    myMaps.sRoot + myMaps.sPath + "创编关卡/" + fn + ".XSB")) {
                fout.write(str.toString().getBytes());
                fout.flush();
            }

            mapNode nd = new mapNode(c1 + mMap.m_nMapLeft, c2 + mMap.m_nMapLeft,
                    r1 + mMap.m_nMapTop, r2 + mMap.m_nMapTop, m_Level);
            nd.Title = fn;
            nd.fileName = fn + ".XSB";
            myMaps.m_lstMaps.add(nd);
            myMaps.isSaveBlock = true;

            new HoloMessageDialog(this, "成功", "标题(Title):\n" + fn, "确定").setVisible(true);
        } catch (Exception e) {
            new HoloMessageDialog(this, "错误", "写错误，保存失败！", "确定").setVisible(true);
        }
    }

    /** {@code edit_inf}「关卡资料」：打开 myAbout2 编辑标题/作者/说明。 */
    private void onInf() {
        if (myMaps.curMap == null || myMaps.curMap.Map == null) {
            MyToast.showToast(this, "做好关卡保存后才可以哦！", MyToast.LENGTH_SHORT);
        } else {
            old_Map = myMaps.curMap;
            new myAbout2(this, myMaps.curMap).setVisible(true);
        }
    }

    /** {@code edit_exit}「离开」。 */
    private void onExit() {
        MyToast.showToast(this, "离开！", MyToast.LENGTH_SHORT);
        if (!bt_Save.isEnabled()) dispose();
        else if (exitDlg != null) exitDlg.setVisible(true);   // 做过编辑修改，提示保存
    }

    /** {@code edit_setup}「设置...」：YASC 绘制习惯 / 系统导航键 两个开关。 */
    private void onSetup() {
        String[] items = {"YASC绘制习惯", "系统导航键"};
        boolean[] checked = {myMaps.m_Sets[19] == 1, myMaps.m_Sets[16] == 1};

        JCheckBox[] boxes = new JCheckBox[items.length];
        for (int i = 0; i < items.length; i++) {
            boxes[i] = HoloContent.check32(items[i], checked[i], 288);
        }
        JComponent body = HoloContent.column(boxes);

        HoloViewDialog dlg = new HoloViewDialog(this, "设置", body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            myMaps.m_Sets[19] = boxes[0].isSelected() ? 1 : 0;   // YASC绘制习惯
            myMaps.m_Sets[16] = boxes[1].isSelected() ? 1 : 0;   // 系统导航键（PC 无系统导航键，只保存开关）
            saveEditSets();
            mMap.repaint();
            dlg.dispose();
        });
        dlg.setVisible(true);
    }

    /** {@code edit_rule}「标尺...」：标尺字体灰度 + 显示哪些元素。 */
    private void onRule() {
        int[] grey = {myMaps.m_Sets[21] & 0x000000ff};
        JSlider slider = HoloContent.slider(grey[0], 220);
        JLabel sample1 = HoloContent.label("标尺示例 1234", 140, SwingConstants.LEFT);
        JLabel sample2 = HoloContent.label("标尺示例 5678", 140, SwingConstants.LEFT);
        Color c0 = new Color((grey[0] << 16) | (grey[0] << 8) | grey[0]);
        sample1.setForeground(c0);
        sample2.setForeground(c0);
        slider.addChangeListener(e -> {
            grey[0] = slider.getValue();
            Color c = new Color((grey[0] << 16) | (grey[0] << 8) | grey[0]);
            sample1.setForeground(c);
            sample2.setForeground(c);
        });

        String[] items = {"墙壁", "地板", "目标", "箱子", "仓管员"};
        int[] mask = {1, 2, 4, 8, 16};
        JCheckBox[] boxes = new JCheckBox[items.length];
        for (int i = 0; i < items.length; i++) {
            boxes[i] = HoloContent.check32(items[i], (myMaps.m_Sets[22] & mask[i]) > 0, 288);
        }

        java.util.List<JComponent> rows = new ArrayList<>();
        rows.add(HoloContent.row(sample1, sample2));
        rows.add(sliderWrap(slider));
        for (JCheckBox b : boxes) rows.add(b);
        JComponent body = HoloContent.column(rows.toArray(new JComponent[0]));

        HoloViewDialog dlg = new HoloViewDialog(this, "显示标尺的元素", body);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            for (int i = 0; i < items.length; i++) {
                if (boxes[i].isSelected()) myMaps.m_Sets[22] |= mask[i];
                else myMaps.m_Sets[22] &= ~mask[i];
            }
            myMaps.m_Sets[21] = (grey[0] << 16) | (grey[0] << 8) | grey[0] | 0xff000000;
            mMap.repaint();
            dlg.dispose();
        });
        dlg.setVisible(true);
    }

    private static JComponent sliderWrap(JSlider slider) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(true);
        p.setBackground(HoloContent.BAND);
        p.add(slider);
        return p;
    }

    /** {@code edit_export}「导出(XSB)」：全部或选区，带 Title/Author/Comment 头。 */
    private void myExport() {
        StringBuilder str = new StringBuilder("\n");

        if (mMap.selNode.row < 0 || mMap.selNode == mMap.selNode2) {
            str.append(getXSB());                    // 计算地图有效部分
        } else {                                     // 仅导出选区部分
            for (int i = mMap.selNode.row; i <= mMap.selNode2.row; i++) {
                for (int j = mMap.selNode.col; j <= mMap.selNode2.col; j++) {
                    char ch = m_cArray[i + mMap.m_nMapTop][j + mMap.m_nMapLeft];
                    str.append(isOK(ch) ? ch : '-');
                }
                str.append('\n');
            }
        }
        str.append("Title: ").append(myMaps.curMap.Title).append('\n');
        str.append("Author: ").append(myMaps.curMap.Author).append('\n');
        str.append("Comment:").append('\n').append(myMaps.curMap.Comment).append('\n');
        str.append("Comment_end:\n");

        JTextArea ta = new JTextArea(str.toString());
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        ta.setBackground(HoloContent.FIELD_BG);
        ta.setForeground(HoloContent.TEXT);
        ta.setCaretColor(HoloContent.TEXT);
        ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(320, 220));
        HoloContent.darkScrollBar(sp);

        HoloViewDialog dlg = new HoloViewDialog(this, "剪切板", sp);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            saveClipper(ta.getText());
            dlg.dispose();
        });
        dlg.setVisible(true);
    }

    /** {@code edit_import}「导入(XSB 或 Lurd)」：从剪切板，先给一个可编辑的确认框。 */
    private void myImport() {
        String str = loadClipper();
        if (str == null || str.trim().isEmpty()) {
            MyToast.showToast(this, "没有数据可导入！", MyToast.LENGTH_SHORT);
            return;
        }

        JTextArea ta = new JTextArea(str);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        ta.setBackground(HoloContent.FIELD_BG);
        ta.setForeground(HoloContent.TEXT);
        ta.setCaretColor(HoloContent.TEXT);
        ta.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(320, 220));
        HoloContent.darkScrollBar(sp);

        HoloViewDialog dlg = new HoloViewDialog(this, "剪切板", sp);
        dlg.addButton("取消", dlg::dispose);
        dlg.addButton("确定", () -> {
            dlg.dispose();
            applyImport(ta.getText());
        });
        dlg.setVisible(true);
    }

    /**
     * 导入的实际动作：Lurd 走 {@link #LurdToXSB(String)}，否则走 {@code myMaps.loadXSB}。
     * 两条路成功后都要压一个 act=7 的撤销节点并 {@code initMap()}。
     */
    void applyImport(String text) {
        boolean ok;
        String fn = myMaps.curMap != null ? myMaps.curMap.fileName : "";
        try {
            if (myMaps.isLURD(text)) {          // 导入的是答案
                ok = LurdToXSB(text);
            } else {                            // 导入 XSB
                ok = myMaps.loadXSB(text);
            }
            if (!ok) throw new Exception();

            ndAct = new ActNode(m_cArray, mMap.m_nMapLeft, mMap.m_nMapRight, mMap.m_nMapTop, mMap.m_nMapBottom,
                    -1, -1, mMap.mMatrix, mMap.mCurrentMatrix, null, null);
            ndAct.Act(7);

            initMap();
            if (myMaps.curMap != null) myMaps.curMap.fileName = fn;
            pushUndo();
            mMap.repaint();
        } catch (Exception e) {
            MyToast.showToast(this, "导入失败，请检查数据格式！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code LurdToXSB(String)}：用**逆推法**从一段答案反推出关卡初态。
     *
     * <p>答案串从尾往头扫，仓管员初始位置预设在画布正中；新访问到的格子先记为
     * {@code '_'}（假定为墙外地板），再视情况改成 {@code '-'}。任何矛盾（撞墙、
     * 越界、推不动）都直接 {@code return false}。
     *
     * @return 成功则已把结果写进 {@code m_cArray} 并调整好四至，返回 {@code true}
     */
    boolean LurdToXSB(String mStr) {
        char[][] m_tLevel = new char[myMaps.m_nMaxRow * 2][myMaps.m_nMaxCol * 2];
        for (int i = 0; i < myMaps.m_nMaxRow * 2; i++) {
            Arrays.fill(m_tLevel[i], '_');       // 暂时假定全部为墙外地板
        }

        int row = myMaps.m_nMaxRow, col = myMaps.m_nMaxCol;   // 预设仓管员初始位置
        m_tLevel[row][col] = '-';

        int top = row, bottom = row, left = col, right = col; // 关卡四至
        int row2, col2, box_row, box_col;

        for (int k = mStr.length() - 1; k >= 0; k--) {
            switch (mStr.charAt(k)) {
                case 'r':
                    col2 = col - 1;
                    if (col2 < 1 || m_tLevel[row][col2] == '$' || m_tLevel[row][col2] == '*') return false;
                    if (m_tLevel[row][col2] == '_') m_tLevel[row][col2] = '-';
                    col = col2;
                    if (left > col) left = col;
                    break;
                case 'd':
                    row2 = row - 1;
                    if (row2 < 1 || m_tLevel[row2][col] == '$' || m_tLevel[row2][col] == '*') return false;
                    if (m_tLevel[row2][col] == '_') m_tLevel[row2][col] = '-';
                    row = row2;
                    if (top > row) top = row;
                    break;
                case 'l':
                    col2 = col + 1;
                    if (col2 >= myMaps.m_nMaxCol * 2 || m_tLevel[row][col2] == '$'
                            || m_tLevel[row][col2] == '*') return false;
                    if (m_tLevel[row][col2] == '_') m_tLevel[row][col2] = '-';
                    col = col2;
                    if (right < col) right = col;
                    break;
                case 'u':
                    row2 = row + 1;
                    if (row2 >= myMaps.m_nMaxRow * 2 || m_tLevel[row2][col] == '$'
                            || m_tLevel[row2][col] == '*') return false;
                    if (m_tLevel[row2][col] == '_') m_tLevel[row2][col] = '-';
                    row = row2;
                    if (bottom < row) bottom = row;
                    break;
                case 'R':
                    col2 = col - 1;
                    box_col = col + 1;
                    if (col2 < 1 || box_col >= myMaps.m_nMaxCol * 2
                            || m_tLevel[row][col2] == '$' || m_tLevel[row][col2] == '*'
                            || m_tLevel[row][box_col] == '-') return false;
                    if (m_tLevel[row][col2] == '_') m_tLevel[row][col2] = '-';
                    if (m_tLevel[row][box_col] == '_' || m_tLevel[row][box_col] == '*') {
                        m_tLevel[row][box_col] = '.';
                    } else if (m_tLevel[row][box_col] == '$') {
                        m_tLevel[row][box_col] = '-';
                    }
                    m_tLevel[row][col] = (m_tLevel[row][col] == '.') ? '*' : '$';
                    col = col2;
                    if (left > col) left = col;
                    if (right < box_col) right = box_col;
                    break;
                case 'D':
                    row2 = row - 1;
                    box_row = row + 1;
                    if (row2 < 1 || box_row >= myMaps.m_nMaxRow * 2
                            || m_tLevel[row2][col] == '$' || m_tLevel[row2][col] == '*'
                            || m_tLevel[box_row][col] == '-') return false;
                    if (m_tLevel[row2][col] == '_') m_tLevel[row2][col] = '-';
                    if (m_tLevel[box_row][col] == '_' || m_tLevel[box_row][col] == '*') {
                        m_tLevel[box_row][col] = '.';
                    } else if (m_tLevel[box_row][col] == '$') {
                        m_tLevel[box_row][col] = '-';
                    }
                    m_tLevel[row][col] = (m_tLevel[row][col] == '.') ? '*' : '$';
                    row = row2;
                    if (top > row) top = row;
                    if (bottom < box_row) bottom = box_row;
                    break;
                case 'L':
                    col2 = col + 1;
                    box_col = col - 1;
                    if (col2 >= myMaps.m_nMaxCol * 2 || box_col < 1
                            || m_tLevel[row][col2] == '$' || m_tLevel[row][col2] == '*'
                            || m_tLevel[row][box_col] == '-') return false;
                    if (m_tLevel[row][col2] == '_') m_tLevel[row][col2] = '-';
                    if (m_tLevel[row][box_col] == '_' || m_tLevel[row][box_col] == '*') {
                        m_tLevel[row][box_col] = '.';
                    } else if (m_tLevel[row][box_col] == '$') {
                        m_tLevel[row][box_col] = '-';
                    }
                    m_tLevel[row][col] = (m_tLevel[row][col] == '.') ? '*' : '$';
                    col = col2;
                    if (right < col) right = col;
                    if (left > box_col) left = box_col;
                    break;
                case 'U':
                    row2 = row + 1;
                    box_row = row - 1;
                    if (row2 >= myMaps.m_nMaxRow * 2 || box_row < 1
                            || m_tLevel[row2][col] == '$' || m_tLevel[row2][col] == '*'
                            || m_tLevel[box_row][col] == '-') return false;
                    if (m_tLevel[row2][col] == '_') m_tLevel[row2][col] = '-';
                    if (m_tLevel[box_row][col] == '_' || m_tLevel[box_row][col] == '*') {
                        m_tLevel[box_row][col] = '.';
                    } else if (m_tLevel[box_row][col] == '$') {
                        m_tLevel[box_row][col] = '-';
                    }
                    m_tLevel[row][col] = (m_tLevel[row][col] == '.') ? '*' : '$';
                    row = row2;
                    if (bottom < row) bottom = row;
                    if (top > box_row) top = box_row;
                    break;
                default:
                    break;   // 非动作字符（换行、空格等）忽略
            }
        }

        // 仓管员所在位置补 '@' / '+'
        m_tLevel[row][col] = (m_tLevel[row][col] == '.') ? '+' : '@';

        // 把结果写回画布（原版是整体替换 m_cArray 的四至范围）
        for (int i = top; i <= bottom; i++) {
            for (int j = left; j <= right; j++) {
                char ch = m_tLevel[i][j];
                m_cArray[i][j] = (ch == '_') ? '-' : ch;
            }
        }
        mMap.m_nMapTop = top;
        mMap.m_nMapBottom = bottom;
        mMap.m_nMapLeft = left;
        mMap.m_nMapRight = right;
        return true;
    }

    /** 测试钩子：{@link #getXSB()}。 */
    String getXSBForTest() {
        return getXSB();
    }

    /** 测试钩子：取 {@link #exitDlg}。 */
    HoloConfirmDialog getExitDialogForTest() {
        return exitDlg;
    }

    /** 测试钩子：取「更多」按钮（长按 = 离开 挂在这上面）。 */
    JToggleButton getMoreButtonForTest() {
        return bt_More;
    }

    /** 原版 {@code getXSB()}：导出「有效部分」（把外围的空地收掉）。 */
    private String getXSB() {
        int r1 = mMap.m_nMapTop, r2 = mMap.m_nMapBottom, c1 = mMap.m_nMapLeft, c2 = mMap.m_nMapRight;
        boolean flg;

        // 上边界
        for (r1 = mMap.m_nMapTop; r1 <= mMap.m_nMapBottom; r1++) {
            flg = false;
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                if (isOK(m_cArray[r1][j])) { flg = true; break; }
            }
            if (flg) break;
        }
        // 下边界
        for (r2 = mMap.m_nMapBottom; r2 >= r1; r2--) {
            flg = false;
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                if (isOK(m_cArray[r2][j])) { flg = true; break; }
            }
            if (flg) break;
        }
        // 左边界
        for (c1 = mMap.m_nMapLeft; c1 <= mMap.m_nMapRight; c1++) {
            flg = false;
            for (int i = r1; i <= r2; i++) {
                if (isOK(m_cArray[i][c1])) { flg = true; break; }
            }
            if (flg) break;
        }
        // 右边界
        for (c2 = mMap.m_nMapRight; c2 >= c1; c2--) {
            flg = false;
            for (int i = r1; i <= r2; i++) {
                if (isOK(m_cArray[i][c2])) { flg = true; break; }
            }
            if (flg) break;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = r1; i <= r2; i++) {
            for (int j = c1; j <= c2; j++) {
                sb.append(isOK(m_cArray[i][j]) ? m_cArray[i][j] : '-');
            }
            if (i < r2) sb.append('\n');
        }
        return sb.toString();
    }

    /** 原版窗口关闭（返回键）走的是同一套「离开」逻辑。 */
    private void onExitRequest() {
        onExit();
    }

    /**
     * 「设置...」与「标尺...」的落盘。原版是 {@code BoxMan.saveSets()} 一次写整张 ini，
     * 这里只写这两个菜单项真正改到的 4 个键（段名与键名照抄 {@code BoxMan.saveSets()}）。
     */
    static void saveEditSets() {
        IniFile file = new IniFile();
        file.set("操作", "显示系统虚拟按键", myMaps.m_Sets[16]);
        file.set("编辑", "关卡编辑中，图中标尺的字体颜色", myMaps.m_Sets[21]);
        file.set("编辑", "关卡编辑中，携带标尺的元素", myMaps.m_Sets[22]);
        file.set("编辑", "是否采用YASC绘制习惯", myMaps.m_Sets[19]);
    }

    // ------------------------------------------------------------------
    // 标准化：Normalize2()（提交时用，返回成功与否）/ Normalize()（菜单项，只提示不返回）
    // ------------------------------------------------------------------

    private static final int[] NR = {-1, 1, 0, 0, -1, 1, -1, 1};  // 前四个是可移动方向，后面是四个临角
    private static final int[] NC = {0, 0, 1, -1, -1, -1, 1, 1};

    /** 找到唯一的仓管员，返回 {row, col}；不是恰好一个则返回 {@code null}。 */
    private int[] findKeeper(char[][] mLevel) {
        int mr = -1, mc = -1, nRen = 0;
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                if (mLevel[i][j] == '@' || mLevel[i][j] == '+') {
                    nRen++;
                    mr = i;
                    mc = j;
                }
            }
        }
        return nRen == 1 ? new int[]{mr, mc} : null;
    }

    /**
     * 原版 {@code Normalize2(char[][])}：「提交」前的精准标准化。
     *
     * <p>从仓管员出发做一次可达性扩散（只走非 {@code '#'}），据此：
     * ① 统计可达区内的箱子 / 目标点，两者必须都 ≥1 且相等，否则弹「箱子或目标数不正确！」并返回 false；
     * ② 把非可达区里的 {@code '$'}{@code '*'} 变 {@code '#'}、其余清成 {@code '-'}，
     *    但若八邻域里有可达格就补成 {@code '#'}（补墙）；
     * ③ 收缩四至，并把结果写回 {@code myMaps.curMap} 的 Map/Rows/Cols。
     */
    boolean Normalize2(char[][] mLevel) {
        int[] keeper = findKeeper(mLevel);
        if (keeper == null) {
            new HoloMessageDialog(this, "错误", "仓管员数目不正确！", "确定").setVisible(true);
            return false;
        }
        int mr = keeper[0], mc = keeper[1];

        int rows = mMap.m_nMapBottom - mMap.m_nMapTop + 1;
        int cols = mMap.m_nMapRight - mMap.m_nMapLeft + 1;
        boolean[][] mark = new boolean[rows][cols];

        int nBox = 0, nDst = 0, r1 = mr, c1 = mc, r2 = mr, c2 = mc;
        LinkedList<Integer> queue = new LinkedList<>();
        queue.offer(mr << 16 | mc);
        mark[mr - mMap.m_nMapTop][mc - mMap.m_nMapLeft] = true;

        while (!queue.isEmpty()) {
            int f = queue.poll();
            mr = f >>> 16;
            mc = f & 0x0000FFFF;

            switch (mLevel[mr][mc]) {
                case '$': nBox++; break;
                case '*': nBox++; nDst++; break;
                case '.': nDst++; break;
                case '+': nDst++; break;
                default: break;
            }

            for (int k = 0; k < 4; k++) {
                int r = mr + NR[k];
                int c = mc + NC[k];
                if (r < mMap.m_nMapTop || r > mMap.m_nMapBottom
                        || c < mMap.m_nMapLeft || c > mMap.m_nMapRight
                        || mark[r - mMap.m_nMapTop][c - mMap.m_nMapLeft]
                        || mLevel[r][c] == '#') continue;

                if (r1 > r) r1 = r;
                if (c1 > c) c1 = c;
                if (r2 < r) r2 = r;
                if (c2 < c) c2 = c;

                queue.offer(r << 16 | c);
                mark[r - mMap.m_nMapTop][c - mMap.m_nMapLeft] = true;
            }
        }

        if (nBox != nDst || nBox < 1 || nDst < 1) {
            new HoloMessageDialog(this, "警告",
                    "箱子或目标数不正确！\n箱子数：" + nBox + "，目标数：" + nDst, "确定").setVisible(true);
            return false;
        }

        // 补充必要的墙壁并去掉关卡图界外的无效元素
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                if (mark[i - mMap.m_nMapTop][j - mMap.m_nMapLeft]) continue;

                if (mLevel[i][j] == '#' || mLevel[i][j] == '$' || mLevel[i][j] == '*') {
                    if (mLevel[i][j] == '$' || mLevel[i][j] == '*') mLevel[i][j] = '#';
                    if (i < r1) r1 = i;
                    if (j < c1) c1 = j;
                    if (i > r2) r2 = i;
                    if (j > c2) c2 = j;
                } else {
                    mLevel[i][j] = '-';
                    for (int k = 0; k < 8; k++) {
                        int r4 = i + NR[k];
                        int c4 = j + NC[k];
                        if (r4 < mMap.m_nMapTop || r4 > mMap.m_nMapBottom
                                || c4 < mMap.m_nMapLeft || c4 > mMap.m_nMapRight) continue;
                        if (mark[r4 - mMap.m_nMapTop][c4 - mMap.m_nMapLeft]) {
                            mLevel[i][j] = '#';
                            if (i < r1) r1 = i;
                            if (j < c1) c1 = j;
                            if (i > r2) r2 = i;
                            if (j > c2) c2 = j;
                            break;
                        }
                    }
                }
            }
        }

        mMap.m_nMapBottom = r2;
        mMap.m_nMapRight = c2;
        mMap.m_nMapTop = r1;
        mMap.m_nMapLeft = c1;

        StringBuilder str = new StringBuilder();
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                str.append(isOK(mLevel[i][j]) ? mLevel[i][j] : '-');
            }
            if (i < mMap.m_nMapBottom) str.append('\n');
        }
        myMaps.curMap.Map = str.toString();
        myMaps.curMap.Rows = mMap.m_nMapBottom - mMap.m_nMapTop + 1;
        myMaps.curMap.Cols = mMap.m_nMapRight - mMap.m_nMapLeft + 1;

        return true;
    }

    /**
     * 原版 {@code Normalize(char[][])}：「关卡标准化」菜单项。
     *
     * <p>与 {@link #Normalize2(char[][])} 的差别：
     * ① 先向四周**各扩一圈墙**再扩散（所以框住的是「补墙后」的范围）；
     * ② 四至向外多留一圈（{@code r1-1 / r2+1 / c1-1 / c2+1}）；
     * ③ 箱子/目标数不对只是 {@code MyToast} 提示，照样收尾（不返回失败）。
     */
    void Normalize(char[][] mLevel) {
        int[] keeper = findKeeper(mLevel);
        if (keeper == null) {
            new HoloMessageDialog(this, "错误", "仓管员数目不正确！", "确定").setVisible(true);
            return;
        }
        int mr = keeper[0], mc = keeper[1];

        // 标准化预处理：四周各补一圈墙（前提是还没到最大尺寸）
        if (mMap.m_nMapTop > 0 && mMap.m_nMapBottom - mMap.m_nMapTop + 1 < myMaps.m_nMaxRow) {
            mMap.m_nMapTop--;
            for (int k = mMap.m_nMapLeft; k <= mMap.m_nMapRight; k++) mLevel[mMap.m_nMapTop][k] = '#';
        }
        if (mMap.m_nMapBottom < myMaps.m_nMaxRow * 2 - 1
                && mMap.m_nMapBottom - mMap.m_nMapTop + 1 < myMaps.m_nMaxRow) {
            mMap.m_nMapBottom++;
            for (int k = mMap.m_nMapLeft; k <= mMap.m_nMapRight; k++) mLevel[mMap.m_nMapBottom][k] = '#';
        }
        if (mMap.m_nMapLeft > 0 && mMap.m_nMapRight - mMap.m_nMapLeft + 1 < myMaps.m_nMaxCol) {
            mMap.m_nMapLeft--;
            for (int k = mMap.m_nMapTop; k <= mMap.m_nMapBottom; k++) mLevel[k][mMap.m_nMapLeft] = '#';
        }
        if (mMap.m_nMapRight < myMaps.m_nMaxCol * 2 - 1
                && mMap.m_nMapRight - mMap.m_nMapLeft + 1 < myMaps.m_nMaxCol) {
            mMap.m_nMapRight++;
            for (int k = mMap.m_nMapTop; k <= mMap.m_nMapBottom; k++) mLevel[k][mMap.m_nMapRight] = '#';
        }

        int rows = mMap.m_nMapBottom - mMap.m_nMapTop + 1;
        int cols = mMap.m_nMapRight - mMap.m_nMapLeft + 1;
        boolean[][] mark = new boolean[rows][cols];

        int r1 = mr, c1 = mc, r2 = mr, c2 = mc;
        LinkedList<Integer> queue = new LinkedList<>();
        queue.offer(mr << 16 | mc);
        mark[mr - mMap.m_nMapTop][mc - mMap.m_nMapLeft] = true;

        while (!queue.isEmpty()) {
            int f = queue.poll();
            mr = f >>> 16;
            mc = f & 0x0000FFFF;

            for (int k = 0; k < 4; k++) {
                int r = mr + NR[k];
                int c = mc + NC[k];
                if (r < mMap.m_nMapTop || c < mMap.m_nMapLeft
                        || r > mMap.m_nMapBottom || c > mMap.m_nMapRight
                        || mark[r - mMap.m_nMapTop][c - mMap.m_nMapLeft]
                        || mLevel[r][c] == '#') continue;

                if (r1 > r) r1 = r;
                if (c1 > c) c1 = c;
                if (r2 < r) r2 = r;
                if (c2 < c) c2 = c;

                queue.offer(r << 16 | c);
                mark[r - mMap.m_nMapTop][c - mMap.m_nMapLeft] = true;
            }
        }

        // 补充必要的墙壁并去掉关卡图界外的所有元素
        int nBox = 0, nDst = 0;
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                if (mark[i - mMap.m_nMapTop][j - mMap.m_nMapLeft]) {
                    switch (mLevel[i][j]) {
                        case '$': nBox++; break;
                        case '*': nBox++; nDst++; break;
                        case '.': nDst++; break;
                        case '+': nDst++; break;
                        default: break;
                    }
                } else {
                    mLevel[i][j] = '-';
                    for (int k = 0; k < 8; k++) {
                        int r = i + NR[k];
                        int c = j + NC[k];
                        if (r < mMap.m_nMapTop || c < mMap.m_nMapLeft
                                || r > mMap.m_nMapBottom || c > mMap.m_nMapRight) continue;
                        if (mark[r - mMap.m_nMapTop][c - mMap.m_nMapLeft]) {
                            mLevel[i][j] = '#';
                            break;
                        }
                    }
                }
            }
        }

        // 修正关卡四至（比可达区再多留一圈）
        if (r1 > 0 && mMap.m_nMapBottom - mMap.m_nMapTop + 1 < myMaps.m_nMaxRow) {
            mMap.m_nMapTop = r1 - 1;
        }
        if (r2 < myMaps.m_nMaxRow * 2 - 1 && mMap.m_nMapBottom - mMap.m_nMapTop + 1 < myMaps.m_nMaxRow) {
            mMap.m_nMapBottom = r2 + 1;
        }
        if (c1 > 0 && mMap.m_nMapRight - mMap.m_nMapLeft + 1 < myMaps.m_nMaxCol) {
            mMap.m_nMapLeft = c1 - 1;
        }
        if (c2 < myMaps.m_nMaxCol * 2 - 1 && mMap.m_nMapRight - mMap.m_nMapLeft + 1 < myMaps.m_nMaxCol) {
            mMap.m_nMapRight = c2 + 1;
        }

        if (nBox != nDst || nBox < 1 || nDst < 1) {
            MyToast.showToast(this, "箱子或目标数不正确！\n箱子：" + nBox + "，目标：" + nDst,
                    MyToast.LENGTH_LONG);
        }
    }

    private static void saveClipper(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }

    private static String loadClipper() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
            return "";
        }
    }
}
