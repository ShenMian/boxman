package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Similar Level Comparison View for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myFindView Activity.
 */
public class myFindView extends JFrame {

    public myFindViewMap mMap;
    public JLabel statusLabel;

    public char[][] m_cArray1;  // 源关卡 -- 全貌
    public char[][] m_cArray2;  // 相似关卡 -- 全貌
    public char[][] m_cArray3;  // 源关卡 -- 标准化
    public char[][] m_cArray4;  // 相似关卡 -- 标准化

    public boolean m_Level = true;  // true: 源关卡, false: 相似关卡
    public boolean m_Level_All = false; // 是否全貌

    public int Rows1, Cols1, Rows2, Cols2, Rows3, Cols3, Rows4, Cols4;
    public int mTrun = 0;
    public int mSimilarity = 0;
    public int[][] mSelect = new int[2][4];

    public myFindView() {
        setTitle("相似关卡对比 - 推箱快手");
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        mMap = new myFindViewMap();
        mMap.Init(this);
        add(mMap, BorderLayout.CENTER);

        statusLabel = new JLabel(" 当前: 源关卡 | 双击画布或使用菜单切换源/相似关卡");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu mNav = new JMenu("关卡操作");
        JMenuItem miSwitch = new JMenuItem("切换源关卡 / 相似关卡");
        miSwitch.addActionListener(e -> switchLevel());
        JMenuItem miRotate = new JMenuItem("旋转关卡 (顺时针90°)");
        miRotate.addActionListener(e -> rotateLevel());
        JMenuItem miToggleAll = new JMenuItem("切换全貌 / 标准化视图");
        miToggleAll.addActionListener(e -> toggleViewMode());

        mNav.add(miSwitch);
        mNav.add(miRotate);
        mNav.add(miToggleAll);

        mb.add(mNav);
        return mb;
    }

    public void switchLevel() {
        m_Level = !m_Level;
        updateDisplay();
    }

    public void rotateLevel() {
        mTrun = (mTrun + 1) % 4;
        updateDisplay();
    }

    public void toggleViewMode() {
        m_Level_All = !m_Level_All;
        updateDisplay();
    }

    public void setLevels(char[][] src, int r1, int c1, char[][] sim, int r2, int c2) {
        this.m_cArray1 = src;
        this.Rows1 = r1;
        this.Cols1 = c1;
        this.m_cArray2 = sim;
        this.Rows2 = r2;
        this.Cols2 = c2;
        updateDisplay();
    }

    private void updateDisplay() {
        char[][] current;
        int r, c;
        if (m_Level) {
            current = (m_Level_All && m_cArray1 != null) ? m_cArray1 : (m_cArray3 != null ? m_cArray3 : m_cArray1);
            r = (m_Level_All && m_cArray1 != null) ? Rows1 : (Rows3 > 0 ? Rows3 : Rows1);
            c = (m_Level_All && m_cArray1 != null) ? Cols1 : (Cols3 > 0 ? Cols3 : Cols1);
            statusLabel.setText(" 当前显示: 【源关卡】 (尺寸: " + c + "×" + r + ")");
        } else {
            current = (m_Level_All && m_cArray2 != null) ? m_cArray2 : (m_cArray4 != null ? m_cArray4 : m_cArray2);
            r = (m_Level_All && m_cArray2 != null) ? Rows2 : (Rows4 > 0 ? Rows4 : Rows2);
            c = (m_Level_All && m_cArray2 != null) ? Cols2 : (Cols4 > 0 ? Cols4 : Cols2);
            statusLabel.setText(" 当前显示: 【相似关卡】 (尺寸: " + c + "×" + r + ")");
        }

        if (current != null) {
            mMap.setMapArray(current, r, c);
        }
        mMap.repaint();
    }
}
