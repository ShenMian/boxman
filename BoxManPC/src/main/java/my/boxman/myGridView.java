package my.boxman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class myGridView extends JFrame {

    private final long mSetId;
    private JLabel mTitleView;
    private JCheckBox my_SelectAll;
    private JPanel gridContainer;
    private JScrollPane scrollPane;

    private int mCols = 5;
    private int thumbWidth = 100;
    private int thumbHeight = 100;

    private static final ConcurrentHashMap<String, BufferedImage> thumbCache = new ConcurrentHashMap<>();
    private final ArrayList<LevelCard> cardList = new ArrayList<>();

    private JMenuItem miSelectMode;
    private JMenuItem miShowTitle;
    private JMenuItem miShowDup;

    public myGridView(long setId, String setTitle) {
        this.mSetId = setId;
        myMaps.m_Set_id = setId;
        myMaps.sFile = setTitle != null ? setTitle : "关卡列表";

        setTitle(myMaps.sFile);
        setSize(850, 650);
        setMinimumSize(new Dimension(600, 450));
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
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 顶栏：全选与关卡集标题 (my_grid_view.xml)
        JPanel topBar = new JPanel(new BorderLayout(5, 5));
        topBar.setBackground(new Color(0x55, 0x55, 0x55));
        topBar.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.setOpaque(false);

        my_SelectAll = new JCheckBox("全选");
        my_SelectAll.setForeground(Color.WHITE);
        my_SelectAll.setOpaque(false);
        my_SelectAll.setVisible(false);
        my_SelectAll.addActionListener(e -> {
            boolean sel = my_SelectAll.isSelected();
            if (myMaps.m_lstMaps != null) {
                for (mapNode nd : myMaps.m_lstMaps) {
                    nd.Select = sel;
                }
            }
            for (LevelCard card : cardList) {
                card.updateSelection();
            }
        });
        leftPanel.add(my_SelectAll);

        mTitleView = new JLabel(myMaps.sFile);
        mTitleView.setForeground(Color.WHITE);
        mTitleView.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        leftPanel.add(mTitleView);

        topBar.add(leftPanel, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);

        // 中间网格容器
        gridContainer = new JPanel();
        gridContainer.setBackground(Color.BLACK);
        updateGridLayout();

        scrollPane = new JScrollPane(gridContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calcColumns();
            }
        });
        add(scrollPane, BorderLayout.CENTER);

        // 顶部菜单栏 (levels.xml)
        setJMenuBar(createMenuBar());
    }

    private void updateGridLayout() {
        if (myMaps.m_Sets[2] == 0) {
            // 图标网格模式
            gridContainer.setLayout(new GridLayout(0, Math.max(1, mCols), 10, 10));
            gridContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        } else {
            // 列表带标题模式
            gridContainer.setLayout(new GridLayout(0, 1, 0, 4));
            gridContainer.setBorder(new EmptyBorder(5, 5, 5, 5));
        }
    }

    private void calcColumns() {
        if (myMaps.m_Sets[2] == 1) {
            updateGridLayout();
            gridContainer.revalidate();
            return;
        }

        int width = scrollPane.getViewport().getWidth();
        if (width <= 0) width = getWidth() - 40;

        int userSetCols = myMaps.m_Sets[33];
        if (userSetCols >= 1 && userSetCols <= 10) {
            mCols = userSetCols;
        } else {
            mCols = Math.max(2, width / (thumbWidth + 20));
        }

        updateGridLayout();
        gridContainer.revalidate();
    }

    public void loadData() {
        if (mSetId >= 0 && mySQLite.m_SQL != null) {
            mySQLite.m_SQL.get_Levels(mSetId);
        }
        refreshGrid();
    }

    public void refreshGrid() {
        gridContainer.removeAll();
        cardList.clear();

        if (myMaps.m_lstMaps == null || myMaps.m_lstMaps.isEmpty()) {
            JLabel emptyLbl = new JLabel("此关卡集中没有关卡", SwingConstants.CENTER);
            emptyLbl.setForeground(Color.GRAY);
            emptyLbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
            gridContainer.setLayout(new BorderLayout());
            gridContainer.add(emptyLbl, BorderLayout.CENTER);
            gridContainer.revalidate();
            gridContainer.repaint();
            return;
        }

        int count = myMaps.m_lstMaps.size();
        int solvedCount = 0;
        for (int i = 0; i < count; i++) {
            mapNode nd = myMaps.m_lstMaps.get(i);
            if (nd.Solved) solvedCount++;
            LevelCard card = new LevelCard(nd, i);
            cardList.add(card);
            gridContainer.add(card);
        }

        mTitleView.setText(myMaps.sFile + " (" + solvedCount + "/" + count + ")");
        calcColumns();
        gridContainer.revalidate();
        gridContainer.repaint();
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        // 顶
        JMenuItem itemTop = new JMenuItem("顶");
        itemTop.addActionListener(e -> scrollToPosition(true));
        bar.add(itemTop);

        // 底
        JMenuItem itemBottom = new JMenuItem("底");
        itemBottom.addActionListener(e -> scrollToPosition(false));
        bar.add(itemBottom);

        // 定位
        JMenuItem itemGoto = new JMenuItem("定位...");
        itemGoto.addActionListener(e -> doGoto());
        bar.add(itemGoto);

        // 选项菜单
        JMenu viewMenu = new JMenu("视图(V)");

        miSelectMode = new JCheckBoxMenuItem("多选模式", myMaps.isSelect);
        miSelectMode.addActionListener(e -> {
            myMaps.isSelect = miSelectMode.isSelected();
            my_SelectAll.setVisible(myMaps.isSelect);
            if (!myMaps.isSelect) {
                my_SelectAll.setSelected(false);
                for (mapNode nd : myMaps.m_lstMaps) nd.Select = false;
            }
            for (LevelCard c : cardList) c.updateSelection();
            revalidate();
            repaint();
        });
        viewMenu.add(miSelectMode);

        miShowTitle = new JCheckBoxMenuItem("显示标题", myMaps.m_Sets[2] == 1);
        miShowTitle.addActionListener(e -> {
            myMaps.m_Sets[2] = miShowTitle.isSelected() ? 1 : 0;
            updateGridLayout();
            for (LevelCard c : cardList) c.updateLayoutMode();
            gridContainer.revalidate();
            gridContainer.repaint();
        });
        viewMenu.add(miShowTitle);

        miShowDup = new JCheckBoxMenuItem("标识重复关卡", myMaps.m_Sets[12] == 1);
        miShowDup.addActionListener(e -> {
            myMaps.m_Sets[12] = miShowDup.isSelected() ? 1 : 0;
            thumbCache.clear();
            for (LevelCard c : cardList) c.requestThumbnail();
        });
        viewMenu.add(miShowDup);

        viewMenu.addSeparator();

        JMenuItem itemFirstUnsolved = new JMenuItem("打开首个未解关卡");
        itemFirstUnsolved.addActionListener(e -> openFirstUnsolved());
        viewMenu.add(itemFirstUnsolved);

        JMenuItem itemRecent = new JMenuItem("打开上次推的关卡");
        itemRecent.addActionListener(e -> openRecentLevel());
        viewMenu.add(itemRecent);

        JMenuItem itemColCount = new JMenuItem("每行图标个数...");
        itemColCount.addActionListener(e -> chooseColumnCount());
        viewMenu.add(itemColCount);

        bar.add(viewMenu);

        // 帮助与关于
        JMenu helpMenu = new JMenu("帮助(H)");
        JMenuItem itemHelp = new JMenuItem("操作说明");
        itemHelp.addActionListener(e -> new Help(1).setVisible(true));
        JMenuItem itemAbout = new JMenuItem("关卡集关于");
        itemAbout.addActionListener(e -> {
            String msg = (mySQLite.m_SQL != null && mSetId > 0)
                    ? mySQLite.m_SQL.count_Sovled(mSetId) + "/" + mySQLite.m_SQL.count_Level(mSetId)
                    : "";
            new myAbout1(this, mSetId, msg).setVisible(true);
        });
        helpMenu.add(itemHelp);
        helpMenu.add(itemAbout);
        bar.add(helpMenu);

        return bar;
    }

    private void scrollToPosition(boolean toTop) {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vbar = scrollPane.getVerticalScrollBar();
            vbar.setValue(toTop ? vbar.getMinimum() : vbar.getMaximum());
        });
    }

    private void doGoto() {
        if (myMaps.m_lstMaps == null || myMaps.m_lstMaps.isEmpty()) return;
        String input = JOptionPane.showInputDialog(this, "请输入要跳转的关卡序号 (1 - " + myMaps.m_lstMaps.size() + "):", "定位", JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.trim().isEmpty()) {
            try {
                int num = Integer.parseInt(input.trim());
                if (num >= 1 && num <= myMaps.m_lstMaps.size()) {
                    LevelCard target = cardList.get(num - 1);
                    Rectangle bounds = target.getBounds();
                    scrollPane.getViewport().scrollRectToVisible(bounds);
                }
            } catch (NumberFormatException ignored) {}
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
        JOptionPane.showMessageDialog(this, "关卡已经全部解开！", "提示", JOptionPane.INFORMATION_MESSAGE);
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
                    myMaps.m_Sets[33] = cols;
                    calcColumns();
                }
            } catch (NumberFormatException ignored) {}
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

    private class LevelCard extends JPanel {
        private final mapNode node;
        private final int index;
        private JLabel lblThumb;
        private JLabel lblText;
        private JLabel lblText2;
        private JCheckBox chkSelect;

        public LevelCard(mapNode nd, int idx) {
            this.node = nd;
            this.index = idx;
            initCard();
        }

        private void initCard() {
            setOpaque(true);
            setBackground(new Color(0x22, 0x22, 0x22));
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
            if (myMaps.m_Sets[2] == 0) {
                // 网格模式
                setLayout(new BorderLayout(2, 2));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(node.Select ? Color.CYAN : Color.DARK_GRAY, node.Select ? 2 : 1),
                        BorderFactory.createEmptyBorder(4, 4, 4, 4)
                ));

                lblThumb = new JLabel("", SwingConstants.CENTER);
                lblThumb.setPreferredSize(new Dimension(thumbWidth, thumbHeight));
                add(lblThumb, BorderLayout.CENTER);

                lblText = new JLabel(String.valueOf(index + 1), SwingConstants.CENTER);
                lblText.setForeground(node.Solved ? Color.GREEN : Color.WHITE);
                lblText.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                add(lblText, BorderLayout.SOUTH);
            } else {
                // 列表模式
                setLayout(new BorderLayout(8, 4));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(node.Select ? Color.CYAN : Color.DARK_GRAY, 1),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));

                lblThumb = new JLabel("", SwingConstants.CENTER);
                lblThumb.setPreferredSize(new Dimension(50, 50));
                add(lblThumb, BorderLayout.WEST);

                String titleText = (index + 1) + ". " + (node.Title != null ? node.Title : "") +
                        (node.Author != null && !node.Author.isEmpty() ? "  [" + node.Author + "]" : "") +
                        (node.Solved ? "  (已通关)" : "");
                lblText2 = new JLabel(titleText);
                lblText2.setForeground(node.Solved ? Color.GREEN : Color.WHITE);
                lblText2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                add(lblText2, BorderLayout.CENTER);
            }
            revalidate();
            repaint();
        }

        public void updateSelection() {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(node.Select ? Color.CYAN : Color.DARK_GRAY, node.Select ? 2 : 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));
            repaint();
        }

        public void requestThumbnail() {
            String cacheKey = node.key + "_" + node.Level_id + "_" + (myMaps.m_Sets[2] == 1 ? "sm" : "lg");
            BufferedImage cached = thumbCache.get(cacheKey);
            if (cached != null) {
                lblThumb.setIcon(new ImageIcon(cached));
                return;
            }

            int sz = (myMaps.m_Sets[2] == 1) ? 50 : thumbWidth;
            SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() {
                    return generateThumbnail(node, sz);
                }

                @Override
                protected void done() {
                    try {
                        BufferedImage img = get();
                        if (img != null) {
                            thumbCache.put(cacheKey, img);
                            lblThumb.setIcon(new ImageIcon(img));
                        }
                    } catch (Throwable ignored) {}
                }
            };
            worker.execute();
        }

        private void showContextMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem miOpen = new JMenuItem("打开");
            miOpen.addActionListener(act -> openGame(node, index));
            menu.add(miOpen);

            JMenuItem miDetail = new JMenuItem("详细信息...");
            miDetail.addActionListener(act -> new myAbout2(myGridView.this, node).setVisible(true));
            menu.add(miDetail);

            menu.show(this, e.getX(), e.getY());
        }
    }

    private static BufferedImage generateThumbnail(mapNode nd, int targetSize) {
        try {
            if (nd.Map == null || nd.Map.equals("--")) {
                BufferedImage def = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = def.createGraphics();
                g.setColor(new Color(40, 40, 40));
                g.fillRect(0, 0, targetSize, targetSize);
                g.setColor(Color.GRAY);
                g.drawRect(0, 0, targetSize - 1, targetSize - 1);
                g.drawString("无效", targetSize / 3, targetSize / 2);
                g.dispose();
                return def;
            }

            String[] lines = nd.Map.split("\r\n|\n\r|\n|\\|");
            int rows = nd.Rows;
            int cols = nd.Cols;
            if (rows <= 0 || cols <= 0) return null;

            int tileSize = 16;
            BufferedImage full = new BufferedImage(cols * tileSize, rows * tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = full.createGraphics();

            for (int r = 0; r < rows; r++) {
                if (r >= lines.length) break;
                for (int c = 0; c < cols; c++) {
                    if (c >= lines[r].length()) break;
                    char ch = lines[r].charAt(c);
                    int x = c * tileSize;
                    int y = r * tileSize;
                    drawTile(g, ch, x, y, tileSize);
                }
            }
            g.dispose();

            double scale = Math.min((double) targetSize / full.getWidth(), (double) targetSize / full.getHeight());
            int sw = Math.max(1, (int) (full.getWidth() * scale));
            int sh = Math.max(1, (int) (full.getHeight() * scale));

            BufferedImage thumb = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = thumb.createGraphics();
            tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int dx = (targetSize - sw) / 2;
            int dy = (targetSize - sh) / 2;
            tg.drawImage(full, dx, dy, sw, sh, null);

            // 如果已通关，绘制通关锁标记或勾标记
            if (nd.Solved) {
                tg.setColor(new Color(0, 200, 0, 180));
                tg.fillOval(targetSize - 18, 2, 16, 16);
                tg.setColor(Color.WHITE);
                tg.setFont(new Font("Dialog", Font.BOLD, 12));
                tg.drawString("✓", targetSize - 15, 14);
            }

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
                else { g.setColor(new Color(0x8B, 0x45, 0x13)); g.fillRect(x, y, size, size); }
                break;
            case '-':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                else { g.setColor(new Color(0xD2, 0xB4, 0x8C)); g.fillRect(x, y, size, size); }
                break;
            case '.':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                else { g.setColor(Color.BLUE); g.fillOval(x + size / 4, y + size / 4, size / 2, size / 2); }
                break;
            case '$':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxPic != null) g.drawImage(myMaps.BoxPic, x, y, size, size, null);
                else { g.setColor(Color.ORANGE); g.fillRect(x + 2, y + 2, size - 4, size - 4); }
                break;
            case '*':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxGoalPic != null) g.drawImage(myMaps.BoxGoalPic, x, y, size, size, null);
                else { g.setColor(Color.RED); g.fillRect(x + 2, y + 2, size - 4, size - 4); }
                break;
            case '@':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.ManPic_d != null) g.drawImage(myMaps.ManPic_d, x, y, size, size, null);
                else { g.setColor(Color.YELLOW); g.fillOval(x + 2, y + 2, size - 4, size - 4); }
                break;
            case '+':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                if (myMaps.ManGoalPic_d != null) g.drawImage(myMaps.ManGoalPic_d, x, y, size, size, null);
                break;
        }
    }
}
