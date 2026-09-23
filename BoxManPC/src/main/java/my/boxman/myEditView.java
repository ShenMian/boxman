package my.boxman;

import my.boxman.compat.android.graphics.Matrix;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.util.*;

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
    int mWhich = -1;
    int m_nItemSelect;

    LinkedList<ActNode> m_UnDoList = new LinkedList<>();
    LinkedList<ActNode> m_ReDoList = new LinkedList<>();
    ActNode ndAct;
    mapNode old_Map;

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
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExitRequest();
            }
        });

        initUI();
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
        initMap();

        setLayout(new BorderLayout());
        add(mMap, BorderLayout.CENTER);

        JPanel bottomBar = createBottomBar();
        add(bottomBar, BorderLayout.SOUTH);

        setJMenuBar(createMenuBar());
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
            showMorePopup();
        });

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

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu mFile = new JMenu("文件");
        JMenuItem miSave = new JMenuItem("保存");
        miSave.addActionListener(e -> doSave());
        JMenuItem miSubmit = new JMenuItem("提交到关卡集...");
        miSubmit.addActionListener(e -> doSubmit());
        JMenuItem miPlay = new JMenuItem("试推关卡");
        miPlay.addActionListener(e -> doPlayTest());
        JMenuItem miExit = new JMenuItem("退出");
        miExit.addActionListener(e -> onExitRequest());
        mFile.add(miSave);
        mFile.add(miSubmit);
        mFile.add(miPlay);
        mFile.addSeparator();
        mFile.add(miExit);

        JMenu mEdit = new JMenu("编辑");
        JMenuItem miImport = new JMenuItem("从剪贴板导入 XSB");
        miImport.addActionListener(e -> myImport());
        JMenuItem miExport = new JMenuItem("导出 XSB 到剪贴板");
        miExport.addActionListener(e -> myExport());
        JMenuItem miNorm = new JMenuItem("关卡标准化");
        miNorm.addActionListener(e -> doNormalize());
        JMenuItem miResize = new JMenuItem("修改关卡尺寸...");
        miResize.addActionListener(e -> showResizeDialog());
        JMenuItem miClear = new JMenuItem("清空地图");
        miClear.addActionListener(e -> doClear());
        mEdit.add(miImport);
        mEdit.add(miExport);
        mEdit.addSeparator();
        mEdit.add(miNorm);
        mEdit.add(miResize);
        mEdit.add(miClear);

        JMenu mHelp = new JMenu("帮助");
        JMenuItem miHelpDoc = new JMenuItem("操作说明");
        miHelpDoc.addActionListener(e -> {
            Help h = new Help(2);
            h.setVisible(true);
        });
        mHelp.add(miHelpDoc);

        mb.add(mFile);
        mb.add(mEdit);
        mb.add(mHelp);

        return mb;
    }

    private void showMorePopup() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem mi1 = new JMenuItem("从剪贴板导入");
        mi1.addActionListener(e -> myImport());
        JMenuItem mi2 = new JMenuItem("导出到剪贴板");
        mi2.addActionListener(e -> myExport());
        JMenuItem mi3 = new JMenuItem("标准化");
        mi3.addActionListener(e -> doNormalize());
        JMenuItem mi4 = new JMenuItem("调整尺寸");
        mi4.addActionListener(e -> showResizeDialog());
        JMenuItem mi5 = new JMenuItem("试推");
        mi5.addActionListener(e -> doPlayTest());
        JMenuItem mi6 = new JMenuItem("提交");
        mi6.addActionListener(e -> doSubmit());

        popup.add(mi1);
        popup.add(mi2);
        popup.add(mi3);
        popup.add(mi4);
        popup.add(mi5);
        popup.add(mi6);
        popup.show(bt_More, 0, -popup.getPreferredSize().height);
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

    private void showResizeDialog() {
        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        JSpinner spLeft = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
        JSpinner spRight = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
        JSpinner spTop = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
        JSpinner spBottom = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));

        p.add(new JLabel("向左扩展:")); p.add(spLeft);
        p.add(new JLabel("向右扩展:")); p.add(spRight);
        p.add(new JLabel("向上扩展:")); p.add(spTop);
        p.add(new JLabel("向下扩展:")); p.add(spBottom);

        int res = JOptionPane.showConfirmDialog(this, p, "修改关卡尺寸", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            mMap.m_nMapLeft -= (int) spLeft.getValue();
            mMap.m_nMapRight += (int) spRight.getValue();
            mMap.m_nMapTop -= (int) spTop.getValue();
            mMap.m_nMapBottom += (int) spBottom.getValue();
            mMap.initArena();
            mMap.repaint();
            bt_Save.setEnabled(true);
        }
    }

    private void doNormalize() {
        mMap.mySelectAll();
        mMap.repaint();
    }

    private void doClear() {
        int res = JOptionPane.showConfirmDialog(this, "确定清空地图吗？", "清空", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
                Arrays.fill(m_cArray[i], '-');
            }
            mMap.selNode.row = -1;
            bt_Save.setEnabled(true);
            mMap.repaint();
        }
    }

    private void doSave() {
        String xsb = getXSB();
        if (myMaps.curMap != null) {
            myMaps.curMap.Map = xsb;
            myMaps.curMap.Rows = mMap.m_nMapBottom - mMap.m_nMapTop + 1;
            myMaps.curMap.Cols = mMap.m_nMapRight - mMap.m_nMapLeft + 1;
        }
        bt_Save.setEnabled(false);
    }

    private void doSubmit() {
        doSave();
        if (mySQLite.m_SQL != null && myMaps.mSets3 != null && !myMaps.mSets3.isEmpty()) {
            mapNode nd = new mapNode(myMaps.curMap.Map, "编辑关卡", "PC作者", "");
            long lvlId = mySQLite.m_SQL.add_L(myMaps.mSets3.get(0).id, nd);
            if (lvlId > 0) {
                JOptionPane.showMessageDialog(this, "提交成功！");
            }
        }
    }

    private void doPlayTest() {
        doSave();
        myGameView game = new myGameView();
        game.setVisible(true);
    }

    private String getXSB() {
        StringBuilder sb = new StringBuilder();
        for (int i = mMap.m_nMapTop; i <= mMap.m_nMapBottom; i++) {
            for (int j = mMap.m_nMapLeft; j <= mMap.m_nMapRight; j++) {
                char ch = m_cArray[i][j];
                sb.append(isOK(ch) ? ch : '-');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void myExport() {
        saveClipper(getXSB());
        JOptionPane.showMessageDialog(this, "关卡 XSB 已复制到剪贴板！");
    }

    private void myImport() {
        String str = loadClipper();
        if (str != null && !str.trim().isEmpty()) {
            myMaps.loadXSB(str);
            initMap();
            mMap.repaint();
        }
    }

    private void onExitRequest() {
        if (bt_Save.isEnabled()) {
            int res = JOptionPane.showConfirmDialog(this, "有修改未保存，确定退出吗？", "提示", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                dispose();
            }
        } else {
            dispose();
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
