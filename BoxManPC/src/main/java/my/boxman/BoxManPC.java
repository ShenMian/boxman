package my.boxman;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;

public class BoxManPC extends JFrame {
    private JTree levelTree;
    private JLabel statusLabel;

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Throwable ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            BoxManPC app = new BoxManPC();
            app.setVisible(true);
        });
    }

    public BoxManPC() {
        setTitle("推箱快手 - PC版");
        setSize(800, 600);
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initAppEnvironment();
        initUI();
    }

    private void initAppEnvironment() {
        if (myMaps.sRoot == null) {
            myMaps.sRoot = System.getProperty("user.home") + "/.boxman";
        }
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = getWidth();
        myMaps.m_nWinHeight = getHeight();

        new File(myMaps.sRoot).mkdirs();

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
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 关卡分类树
        levelTree = createLevelTree();
        JScrollPane scrollPane = new JScrollPane(levelTree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(scrollPane, BorderLayout.CENTER);

        // 底部状态栏
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        String progress = mySQLite.m_SQL.count_Level();
        statusLabel = new JLabel("关卡完成统计: " + progress + "    |    双击关卡集进入网格/关卡浏览");
        statusPanel.add(statusLabel, BorderLayout.WEST);
        add(statusPanel, BorderLayout.SOUTH);

        // 菜单栏
        setJMenuBar(createMenuBar());
    }

    private JTree createLevelTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("推箱快手关卡库");
        String[] groupNames = {"1. 入门关卡", "2. 进阶关卡", "3. 花样关卡", "4. 关卡扩展"};
        @SuppressWarnings("unchecked")
        ArrayList<set_Node>[] sets = new ArrayList[]{
                myMaps.mSets0, myMaps.mSets1, myMaps.mSets2, myMaps.mSets3
        };

        for (int i = 0; i < 4; i++) {
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupNames[i]);
            ArrayList<set_Node> list = sets[i];
            if (list != null) {
                for (set_Node node : list) {
                    groupNode.add(new DefaultMutableTreeNode(new SetItem(node.id, node.title)));
                }
            }
            root.add(groupNode);
        }

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        tree.setRowHeight(24);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof SetItem) {
                            SetItem item = (SetItem) node.getUserObject();
                            openSet(item.id, item.title);
                        }
                    }
                }
            }
        });

        // 默认展开所有一级组
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

        return tree;
    }

    private void openSet(long setId, String setTitle) {
        System.out.println("打开关卡集: id=" + setId + ", title=" + setTitle);
        JOptionPane.showMessageDialog(this, "已选择关卡集: " + setTitle + " (ID: " + setId + ")\n进入关卡网格/游戏窗口准备中...", "关卡集", JOptionPane.INFORMATION_MESSAGE);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("文件(F)");
        JMenuItem itemImport = new JMenuItem("导入关卡文件...");
        itemImport.addActionListener(e -> chooseImportFile());
        JMenuItem itemExit = new JMenuItem("退出");
        itemExit.addActionListener(e -> System.exit(0));
        fileMenu.add(itemImport);
        fileMenu.addSeparator();
        fileMenu.add(itemExit);

        JMenu viewMenu = new JMenu("视图(V)");
        JMenuItem itemRefresh = new JMenuItem("刷新关卡列表");
        itemRefresh.addActionListener(e -> refreshTree());
        viewMenu.add(itemRefresh);

        JMenu helpMenu = new JMenu("帮助(H)");
        JMenuItem itemAbout = new JMenuItem("关于推箱快手");
        itemAbout.addActionListener(e -> showAboutDialog());
        helpMenu.add(itemAbout);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void chooseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择关卡文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("关卡文件 (*.txt;*.sok;*.xsb)", "txt", "sok", "xsb"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File sel = chooser.getSelectedFile();
            JOptionPane.showMessageDialog(this, "已选择导入文件: " + sel.getAbsolutePath(), "关卡导入", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshTree() {
        loadAllSets();
        levelTree.setModel(new DefaultTreeModel((javax.swing.tree.TreeNode) createLevelTree().getModel().getRoot()));
        statusLabel.setText("关卡完成统计: " + mySQLite.m_SQL.count_Level() + "    |    双击关卡集进入网格/关卡浏览");
        revalidate();
        repaint();
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "推箱快手 (BoxMan) PC版\n严格 1:1 原样等价移植\nJava SE + Swing + FlatLaf",
                "关于", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class SetItem {
        final long id;
        final String title;

        SetItem(long id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
