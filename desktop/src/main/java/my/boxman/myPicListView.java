package my.boxman;

import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.UiWindow;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.Consumer;

/**
 * 图片列表 —— 原版 {@code myPicListView.java}（258 行）的 PC 等价物。
 *
 * <p>布局 {@code res/layout/my_piclist_view.xml}：一个 {@code GridView}
 * （{@code numColumns="auto_fit"}、{@code columnWidth="350px"}、
 * {@code verticalSpacing="10dip"}、{@code horizontalSpacing="5px"}）铺满全屏，
 * 背景 {@code #ff000000}。350px 按 density≈3.4 折算 ≈ 103dp，
 * 内容区 370dp → <b>3 列</b>。
 *
 * <p>菜单 {@code res/menu/piclist.xml} 只有一项 {@code showAsAction="always"}「位置」，
 * 所以走 {@code addBarAction}（且按原版规则，此时 ActionBar 右侧不画 ⋮）。
 *
 * <p>上下文菜单是 {@code onCreateContextMenu} 的「加载 / 删除」两项；
 * PC 把 Android 的**长按**映射到**右键**。
 *
 * <p>⚠️ 这次重写删掉了 PC 自造的底部按钮栏（「选择本地文件...」/「关闭」）与
 * {@code JFileChooser} —— 原版的「换目录」入口是 ActionBar「位置」→「图片位置」对话框
 * →「修改」→ {@link myFileExplorerActivity}，没有系统文件选择器。
 */
public class myPicListView extends JFrame {

    /** 原版 {@code GridView} 列数（见类注释） */
    public static final int COLUMNS = 3;

    public myActionBar actionBar;
    public JPanel gridPanel;
    public JScrollPane scrollPane;
    public myPicListViewAdapter adapter;

    /** 长按（PC：右键）的位置，上下文菜单用 */
    public int m_Num = -1;
    /** 原版「防止点击过快」的 2000ms 闸门 */
    public long currentTime = 0;

    /** 测试缝：把「弹模态框」换成「只记不弹」 */
    Consumer<JDialog> dialogShower = dlg -> dlg.setVisible(true);
    private HoloChoiceDialog pathDialog;
    private JPopupMenu contextMenu;
    /** 测试缝：「修改」要起的 {@link myFileExplorerActivity}（默认真的 new 一个） */
    Consumer<myFileExplorerActivity> explorerShower = w -> w.setVisible(true);

    public myPicListView() {
        setTitle("图片列表 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        adapter = new myPicListViewAdapter();

        initUI();
        // 原版 onCreate 末尾：recycleBitmapCaches(0, size) + adapter.notifyDataSetChanged()
        adapter.clearCache();
        rebuildGrid();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    private void initUI() {
        setLayout(new BorderLayout());

        actionBar = new myActionBar();
        // 原版 setTitle(myMaps.myPathList[myMaps.m_Sets[36]])
        actionBar.setBarTitle(myMaps.myPathList[myMaps.m_Sets[36]]);
        actionBar.setUpEnabled(true, this::dispose);
        actionBar.addBarAction("位置", this::showPathDialog);   // R.id.pic_path
        add(actionBar, BorderLayout.NORTH);

        gridPanel = new JPanel(new GridLayout(0, COLUMNS, 1, 10));   // hgap 1dp, vgap 10dp
        gridPanel.setBackground(Color.BLACK);
        gridPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        gridPanel.setOpaque(true);

        scrollPane = HoloContent.scroll(gridPanel);
        HoloContent.darkScrollBar(scrollPane);
        scrollPane.getViewport().setBackground(Color.BLACK);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ================================================================ 网格

    void rebuildGrid() {
        gridPanel.removeAll();
        for (int i = 0; i < adapter.getCount(); i++) gridPanel.add(createCard(i));
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    /** 行布局对应 {@code res/layout/my_piclist_view_item.xml}：缩略图 + 文件名 */
    private JPanel createCard(final int position) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.BLACK);
        card.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));   // ImageView 的 layout_margin=5px

        JLabel lbImg = new JLabel(new ImageIcon(adapter.getBitmap(position)), SwingConstants.CENTER);
        lbImg.setPreferredSize(new Dimension(myPicListViewAdapter.THUMB_W, myPicListViewAdapter.THUMB_H));
        lbImg.setMinimumSize(lbImg.getPreferredSize());
        lbImg.setMaximumSize(lbImg.getPreferredSize());
        lbImg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lbName = new JLabel(adapter.getFileName(position), SwingConstants.CENTER);
        lbName.setFont(HoloContent.font(java.awt.Font.PLAIN));
        lbName.setForeground(HoloContent.TEXT);
        lbName.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(lbImg);
        card.add(lbName);

        // 原版 onItemClick（左键）；PC 的长按=右键→上下文菜单
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    m_Num = position;
                    showContextMenu(e);
                    return;
                }
                onItemClick(position);
            }
        });
        return card;
    }

    /** 原版 {@code ItemClickListener.onItemClick} */
    void onItemClick(int position) {
        if (System.currentTimeMillis() - currentTime < 2000) return;   // 防止点击过快
        currentTime = System.currentTimeMillis();
        openPic(position);
    }

    // ================================================================ 上下文菜单

    JPopupMenu buildContextMenu() {
        JPopupMenu menu = HoloPopupMenu.create();
        HoloPopupMenu.addItem(menu, "加载", () -> onContextItemSelected(0));
        HoloPopupMenu.addItem(menu, "删除", () -> onContextItemSelected(1));
        return menu;
    }

    void showContextMenu(MouseEvent e) {
        contextMenu = buildContextMenu();
        contextMenu.show((Component) e.getSource(), e.getX(), e.getY());
    }

    /** 原版 {@code onContextItemSelected}：0 加载图片 / 1 删除图片 */
    boolean onContextItemSelected(int itemId) {
        if (itemId == 0) {
            openPic(m_Num);
        } else if (itemId == 1) {
            File file = adapter.getFile(m_Num);
            if (file.exists() && file.isFile()) file.delete();
            myMaps.mFile_List.remove(m_Num);
            adapter.clearCache();
            rebuildGrid();
        }
        return true;
    }

    // ================================================================ 打开图片

    /** 原版 {@code loadEDPic} + 尺寸闸门（>200 才进识别，否则 Toast） */
    void openPic(int position) {
        loadEDPic(adapter.getFileName(position));
        java.awt.image.BufferedImage p = myMaps.edPict;
        if (p != null && p.getHeight() > 200 && p.getWidth() > 200) {
            new myRecogView().setVisible(true);
        } else {
            MyToast.showToast(this, "图片尺寸太小或不能打开！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 {@code loadEDPic}：解码 {@code sRoot + myPathList[m_Sets[36]] + fn} */
    void loadEDPic(String fn) {
        myMaps.edPict = null;
        try {
            myMaps.edPict = javax.imageio.ImageIO.read(adapter.getFileOf(fn));
        } catch (Exception e) {
            myMaps.edPict = null;
            MyToast.showToast(this, "图片加载失败！", MyToast.LENGTH_SHORT);
        }
    }

    // ================================================================ ActionBar「位置」

    /** 原版 {@code R.id.pic_path}：5 项单选 + 修改 / 打开 / 取消 */
    void showPathDialog() {
        String[] m_menu = new String[] {
                "快手默认位置",
                "QQ 图片接收文件夹",
                myMaps.myPathList[2],
                myMaps.myPathList[3],
                myMaps.myPathList[4]
        };
        int m = myMaps.m_Sets[36];
        if (m < 0 || m > 4) m = 0;

        pathDialog = new HoloChoiceDialog(this, "图片位置", null, m_menu);
        pathDialog.list.setSelectedIndex(m);
        pathDialog.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = pathDialog.list.getSelectedIndex();
            if (i >= 0) myMaps.m_Sets[36] = i;      // 原版 onClick: myMaps.m_Sets[36] = which
        });

        pathDialog.addButton("取消", null);                       // setNegativeButton("取消", null)
        pathDialog.addButton("修改", this::onPathModify);         // setNeutralButton("修改")
        JButton btOpen = pathDialog.addButton("打开", this::onPathOpen);   // setPositiveButton("打开")
        pathDialog.setDefaultButton(btOpen);

        dialogShower.accept(pathDialog);
    }

    /** 原版「修改」：只有自定义位置（2..4）能改 */
    void onPathModify() {
        int m = myMaps.m_Sets[36];
        if (m > 1 && m < 5) {
            pathDialog.dispose();
            myFileExplorerActivity explorer = new myFileExplorerActivity(this::onPathPicked);
            explorerShower.accept(explorer);
        } else {
            MyToast.showToast(this, "这个位置不能修改！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版「打开」 */
    void onPathOpen() {
        String path = myMaps.myPathList[myMaps.m_Sets[36]];
        if (path == null || path.trim().isEmpty()) {
            path = "/";
            myMaps.myPathList[myMaps.m_Sets[36]] = path;
        }
        try {
            pathDialog.dispose();
            reloadList();
        } catch (Exception e) {
            MyToast.showToast(this, "错误的位置！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版 {@code onActivityResult(999)} —— 文件浏览器选完回来 */
    void onPathPicked(String path) {
        reloadList();
    }

    /** 原版那三行：recycleBitmapCaches → edPicList → setTitle → notifyDataSetChanged */
    void reloadList() {
        adapter.clearCache();
        myMaps.edPicList(myMaps.picDir());
        actionBar.setBarTitle(myMaps.myPathList[myMaps.m_Sets[36]]);
        rebuildGrid();
    }

    // ================================================================ 测试缝

    public HoloChoiceDialog getPathDialog() {
        return pathDialog;
    }

    public JPopupMenu getContextMenu() {
        return contextMenu;
    }
}
