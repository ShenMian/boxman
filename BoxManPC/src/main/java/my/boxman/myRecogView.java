package my.boxman;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import my.boxman.compat.UiWindow;

/**
 * Level Image Recognition Frame for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myRecogView Activity.
 */
public class myRecogView extends JFrame {

    public myRecogViewMap mMap;
    public char[][] m_cArray;
    public int m_nRows = 10, m_nCols = 10;
    public int selectedObj = 1; // 默认选中墙壁

    public JButton bt_Floor, bt_Wall, bt_Box, bt_Goal, bt_BoxGoal, bt_Player;
    public JButton bt_Left, bt_Right, bt_Up, bt_Down;

    public myRecogView() {
        setTitle("图像识别 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initMapData();
        initUI();

        // ⚠️ 必须在 initUI()（内含 setJMenuBar）之后再调：JMenuBar 挂在 rootPane 上、
        // 位于内容区**之外**，会从内容区里挖走 23px。先 pack 再 setJMenuBar 的话，
        // 内容区就只剩 370×757，而不是竖屏约定的 370×780。见 UiWindow 的说明。
        UiWindow.applyPhoneSize(this);
    }

    private void initMapData() {
        m_cArray = new char[m_nRows][m_nCols];
        for (int r = 0; r < m_nRows; r++) {
            Arrays.fill(m_cArray[r], '-');
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        mMap = new myRecogViewMap();
        mMap.Init(this);
        mMap.m_nRows = m_nRows;
        mMap.m_nCols = m_nCols;
        add(mMap, BorderLayout.CENTER);

        JPanel bottomBar = createBottomBar();
        add(bottomBar, BorderLayout.SOUTH);

        setJMenuBar(createMenuBar());
    }

    private JPanel createBottomBar() {
        JPanel bar = new JPanel(new GridLayout(1, 10, 4, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        bt_Floor = new JButton("地板");
        bt_Floor.addActionListener(e -> setObj(0));
        bt_Wall = new JButton("墙壁");
        bt_Wall.addActionListener(e -> setObj(1));
        bt_Box = new JButton("箱子");
        bt_Box.addActionListener(e -> setObj(2));
        bt_Goal = new JButton("目标");
        bt_Goal.addActionListener(e -> setObj(3));
        bt_BoxGoal = new JButton("标箱");
        bt_BoxGoal.addActionListener(e -> setObj(4));
        bt_Player = new JButton("人");
        bt_Player.addActionListener(e -> setObj(5));

        bt_Left = new JButton("←");
        bt_Left.addActionListener(e -> shiftGrid(-1, 0));
        bt_Right = new JButton("→");
        bt_Right.addActionListener(e -> shiftGrid(1, 0));
        bt_Up = new JButton("↑");
        bt_Up.addActionListener(e -> shiftGrid(0, -1));
        bt_Down = new JButton("↓");
        bt_Down.addActionListener(e -> shiftGrid(0, 1));

        bar.add(bt_Floor);
        bar.add(bt_Wall);
        bar.add(bt_Box);
        bar.add(bt_Goal);
        bar.add(bt_BoxGoal);
        bar.add(bt_Player);
        bar.add(bt_Left);
        bar.add(bt_Right);
        bar.add(bt_Up);
        bar.add(bt_Down);

        return bar;
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu mFile = new JMenu("识别操作");

        JMenuItem miLoadImg = new JMenuItem("加载图片文件...");
        miLoadImg.addActionListener(e -> chooseImage());
        JMenuItem miToEditor = new JMenuItem("发送到关卡编辑器 (myEditView)");
        miToEditor.addActionListener(e -> sendToEditor());
        JMenuItem miClear = new JMenuItem("清空所有识别元素");
        miClear.addActionListener(e -> clearMap());

        mFile.add(miLoadImg);
        mFile.add(miToEditor);
        mFile.addSeparator();
        mFile.add(miClear);

        mb.add(mFile);
        return mb;
    }

    private void setObj(int obj) {
        this.selectedObj = obj;
        if (mMap.cur_Row >= 0 && mMap.cur_Col >= 0) {
            onCellClicked(mMap.cur_Row, mMap.cur_Col);
            mMap.repaint();
        }
    }

    public void onCellClicked(int r, int c) {
        if (r < 0 || r >= m_nRows || c < 0 || c >= m_nCols) return;
        char ch = '-';
        switch (selectedObj) {
            case 0: ch = '-'; break;
            case 1: ch = '#'; break;
            case 2: ch = '$'; break;
            case 3: ch = '.'; break;
            case 4: ch = '*'; break;
            case 5: ch = '@'; break;
        }
        m_cArray[r][c] = ch;
    }

    private void shiftGrid(int dc, int dr) {
        if (mMap.cur_Col + dc >= 0 && mMap.cur_Col + dc < m_nCols) mMap.cur_Col += dc;
        if (mMap.cur_Row + dr >= 0 && mMap.cur_Row + dr < m_nRows) mMap.cur_Row += dr;
        mMap.repaint();
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件 (*.png, *.jpg, *.jpeg, *.bmp)", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                myMaps.edPict = ImageIO.read(chooser.getSelectedFile());
                mMap.setArena();
                mMap.repaint();
                JOptionPane.showMessageDialog(this, "成功加载图片: " + chooser.getSelectedFile().getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "加载图片失败: " + ex.getMessage());
            }
        }
    }

    private void clearMap() {
        for (int r = 0; r < m_nRows; r++) {
            Arrays.fill(m_cArray[r], '-');
        }
        mMap.repaint();
    }

    private void sendToEditor() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m_nRows; r++) {
            for (int c = 0; c < m_nCols; c++) {
                sb.append(m_cArray[r][c]);
            }
            sb.append('\n');
        }
        myMaps.curMap = new mapNode(sb.toString(), "识别关卡", "图像识别", "");
        myEditView edit = new myEditView();
        edit.setVisible(true);
        dispose();
    }
}
