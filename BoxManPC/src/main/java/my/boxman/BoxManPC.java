package my.boxman;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
        SwingUtilities.invokeLater(() -> {
            myGridView grid = new myGridView(setId, setTitle);
            grid.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    refreshTree();
                }
            });
            grid.setVisible(true);
        });
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
        JMenuItem itemHelp = new JMenuItem("推箱快手说明");
        itemHelp.addActionListener(e -> new Help(0).setVisible(true));
        JMenuItem itemAbout = new JMenuItem("关于推箱快手");
        itemAbout.addActionListener(e -> showAboutDialog());
        helpMenu.add(itemHelp);
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
            importLevelFile(sel);
        }
    }

    public int importLevelFile(File file) {
        return importLevelFile(file, false);
    }

    public int importLevelFile(File file, boolean silent) {
        try {
            String fileName = file.getName();
            int dotIdx = fileName.lastIndexOf('.');
            String setTitle = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

            long targetSetId = mySQLite.m_SQL.find_Set(setTitle);
            if (targetSetId <= 0) {
                targetSetId = mySQLite.m_SQL.add_T(3, setTitle, "", "");
            }
            if (targetSetId <= 0) {
                if (!silent) JOptionPane.showMessageDialog(this, "创建关卡集失败！", "错误", JOptionPane.ERROR_MESSAGE);
                return 0;
            }

            String encode = myMaps.getTxtEncode(new java.io.FileInputStream(file));
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), encode));

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
        } catch (Throwable ex) {
            if (!silent) JOptionPane.showMessageDialog(this, "导入关卡文件失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return 0;
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
        new myAbout(this).setVisible(true);
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
