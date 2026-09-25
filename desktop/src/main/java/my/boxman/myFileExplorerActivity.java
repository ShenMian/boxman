package my.boxman;

import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.UiWindow;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件浏览器 —— 原版 {@code myFileExplorerActivity.java}（224 行）的 PC 等价物。
 *
 * <p>布局对应 {@code res/layout/line_list.xml}：顶部一行「当前位置: 」+ 路径
 * （{@code R.id.tv_file_path}），下面是 {@code R.id.filelist} 列表；行布局对应
 * {@code res/layout/line.xml}：图标 + 文件名，左右 padding 10dp、上下 5dp。
 *
 * <p>菜单 {@code res/menu/filelist.xml} 的两项都是 {@code showAsAction="always"}，
 * 所以走 {@code myActionBar.addBarAction}（走溢出菜单就错了）；左侧返回折角来自
 * 原版 {@code setDisplayHomeAsUpEnabled(true)}，点它 = {@code android.R.id.home} → finish。
 *
 * <p>⚠️ 两处**必要的 PC 适配**（原版代码直接照抄在 Windows 上会失效）：
 * <ol>
 *   <li>{@code myMaps.sRoot} 用的是正斜杠（{@code user.home + "/.boxman"}），而
 *       {@code File.getCanonicalPath()} 在 Windows 上返回反斜杠。原版的
 *       {@code canonical.equals(sRoot)} 与 {@code canonical.replace(sRoot, "")}
 *       都会全部落空 → 表现为「上一级能一路退到盘符」和「路径栏永远显示绝对路径」。
 *       这里改成**两边都取 canonical** 再比较 / 去前缀，语义与原版一致。</li>
 *   <li>原版 {@code root.listFiles()} 返回 null 时会 NPE，这里兜成空数组。</li>
 * </ol>
 */
public class myFileExplorerActivity extends JFrame {

    /** 原版 {@code setResult(999)}；PC 没有 {@code startActivityForResult}，用回调等价。 */
    public interface OnPathPicked {
        void onPicked(String path);
    }

    public static final int RESULT_CODE = 999;

    /** 列表里只认这三种后缀（原版 {@code inflateListView} 的判断） */
    static final String[] PIC_SUFFIX = { "jpg", "bmp", "png" };

    public myActionBar actionBar;
    public JLabel tvPath;                  // R.id.tv_file_path
    public JList<File> listView;           // R.id.filelist

    /** 记录当前父文件夹 */
    public File currentParent;
    /** 记录当前路径下的所有文件（**未过滤**，原版就是这个语义） */
    public File[] currentFiles = new File[0];
    /** 屏幕上真正画出来的条目：目录 + 图片文件（原版 SimpleAdapter 的数据源） */
    private final List<File> displayFiles = new ArrayList<File>();

    private OnPathPicked onPathPicked;

    /** 测试缝：把「弹模态框」换成「只记不弹」（见 TEST_NOTES 的 dialogShower 一节） */
    Consumer<javax.swing.JDialog> dialogShower = dlg -> dlg.setVisible(true);
    private HoloConfirmDialog exitDlg;

    public myFileExplorerActivity() {
        this(null);
    }

    public myFileExplorerActivity(OnPathPicked onPathPicked) {
        setTitle("自定义位置 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.onPathPicked = onPathPicked;

        initUI();
        initRoot();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    // ================================================================ 界面

    private void initUI() {
        setLayout(new BorderLayout());

        actionBar = new myActionBar();
        actionBar.setBarTitle("自定义位置");                 // 原版 setTitle("自定义位置")
        actionBar.setUpEnabled(true, this::finish);        // android.R.id.home
        actionBar.addBarAction("上一级", this::myParent);  // R.id.filelist_parent
        actionBar.addBarAction("完成", this::onOk);        // R.id.filelist_ok
        add(actionBar, BorderLayout.NORTH);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBackground(Color.BLACK);
        top.setOpaque(true);
        top.setBorder(new EmptyBorder(4, 4, 4, 4));       // TextView 的 layout_margin=4dp
        top.add(HoloContent.label("当前位置: "));
        tvPath = HoloContent.label("");
        top.add(tvPath);
        top.add(Box.createHorizontalGlue());

        listView = new JList<File>();
        listView.setModel(new FileListModel());
        listView.setCellRenderer(new FileCellRenderer());
        listView.setBackground(Color.BLACK);
        listView.setForeground(HoloContent.TEXT);
        listView.setSelectionBackground(HoloContent.LIST_SELECTED);
        listView.setSelectionForeground(HoloContent.TEXT);
        listView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int position = listView.locationToIndex(e.getPoint());
                onItemClick(position);
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.BLACK);
        content.add(top, BorderLayout.NORTH);
        content.add(HoloContent.scroll(listView), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        // 原版 BACK 键：路径为空 → 弹「退出浏览」确认框；否则 → 上一级
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        getRootPane().getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onBack();
            }
        });
    }

    // ================================================================ 数据

    private void initRoot() {
        File root;
        if (myMaps.myPathList[2] != null && !myMaps.myPathList[2].isEmpty()) {
            File targetDir = new File(myMaps.sRoot + myMaps.myPathList[2]);
            root = targetDir.exists() ? targetDir : new File(myMaps.sRoot);
        } else {
            root = new File(myMaps.sRoot);
        }

        if (root.exists()) {
            currentParent = root;
            currentFiles = listFiles(root);
            inflateListView(currentFiles);
        } else {
            MyToast.showToast(this, "系统错误，无法执行该操作！", MyToast.LENGTH_SHORT);
            finish();
        }
    }

    /** 列表项单击（原版 {@code onItemClick}） */
    void onItemClick(int position) {
        if (position < 0 || position >= currentFiles.length) return;
        // ⚠️ 原版用的是**未过滤**的 currentFiles 下标，而屏幕上画的是过滤后的列表。
        // 目录排在最前且连续，所以点目录永远正确；点文件原本就没反应，
        // 于是这个错位只在「目录里混着非图片文件」时表现为「点图片没反应」。
        // 原版怪癖，照抄。
        File f = currentFiles[position];
        if (f.isFile()) return;                       // 用户单击了文件，直接返回
        File[] tmp = listFiles(f);
        if (tmp != null) {
            currentParent = f;
            currentFiles = tmp;
            inflateListView(currentFiles);
        }
    }

    /** 原版 {@code inflateListView}：目录优先 + 按名（小写）排序，文件只留 jpg/bmp/png */
    void inflateListView(File[] files) {
        if (files == null) files = new File[0];

        List<File> sorted = new ArrayList<File>(Arrays.asList(files));
        Collections.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) return -1;
                if (o1.isFile() && o2.isDirectory()) return 1;
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        });
        currentFiles = sorted.toArray(new File[0]);

        displayFiles.clear();
        for (File f : currentFiles) {
            if (f.isDirectory()) {
                displayFiles.add(f);
                continue;
            }
            String fn = f.getName();
            int dot = fn.lastIndexOf('.');
            // ⚠️ 原版的判断是 (dot > -1) && (dot < fn.length()) —— 没有扩展名的文件
            // 会被**无条件**列出来（不参与后缀过滤）。照抄。
            if (dot > -1 && dot < fn.length()) {
                String prefix = fn.substring(dot + 1);
                boolean isPic = false;
                for (String s : PIC_SUFFIX) {
                    if (prefix.equalsIgnoreCase(s)) { isPic = true; break; }
                }
                if (!isPic) continue;
            }
            displayFiles.add(f);
        }

        ((FileListModel) listView.getModel()).fire();
        updatePathText();
    }

    /** 原版 {@code textView.setText(currentParent.getCanonicalPath().replace(myMaps.sRoot, ""))} */
    void updatePathText() {
        String cp = canonical(currentParent);
        String root = canonical(new File(myMaps.sRoot));
        tvPath.setText(cp.startsWith(root) ? cp.substring(root.length()) : cp);
    }

    // ================================================================ 动作

    /** 原版 {@code R.id.filelist_ok}：保存自定义位置后 setResult(999) + finish */
    void onOk() {
        String str = tvPath.getText();
        // ⚠️ 原版把 if (!str.isEmpty()) 注释掉了 —— 空路径也会写成 "/"。照抄。
        String saved = str + '/';
        myMaps.myPathList[myMaps.m_Sets[36]] = saved;
        if (onPathPicked != null) onPathPicked.onPicked(saved);
        finish();
    }

    /** 原版 {@code R.id.filelist_parent} / BACK 键的非空分支 */
    void myParent() {
        if (currentParent == null) return;
        if (!canonical(currentParent).equals(canonical(new File(myMaps.sRoot)))) {
            File parent = currentParent.getParentFile();
            if (parent == null) return;               // 已经到盘符，原版这里会 NPE
            currentParent = parent;
            currentFiles = listFiles(parent);
            inflateListView(currentFiles);
        }
    }

    /** 原版 BACK 键 */
    void onBack() {
        String str = tvPath.getText();
        if (str.isEmpty()) showExitDialog();
        else myParent();
    }

    /** 原版 {@code dlg0}：提醒 / 退出浏览，确定吗？ / 取消 / 确定（setCancelable(false)） */
    void showExitDialog() {
        exitDlg = new HoloConfirmDialog(this, "提醒", "退出浏览，确定吗？", "取消", "确定", this::finish);
        dialogShower.accept(exitDlg);
    }

    /** 原版 {@code this.finish()} */
    public void finish() {
        dispose();
    }

    // ================================================================ 工具

    /** {@code listFiles()} 的 null 兜底（原版会 NPE） */
    private static File[] listFiles(File dir) {
        File[] fs = dir.listFiles();
        return fs == null ? new File[0] : fs;
    }

    /**
     * 取 canonical 路径；失败时退回绝对路径。
     * 见类注释的「必要 PC 适配 ①」。
     */
    static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    // ================================================================ 列表模型与渲染

    private class FileListModel extends AbstractListModel<File> {
        @Override
        public int getSize() {
            return displayFiles.size();
        }

        @Override
        public File getElementAt(int index) {
            return displayFiles.get(index);
        }

        void fire() {
            fireContentsChanged(this, 0, getSize());
        }
    }

    /** 行布局对应 {@code res/layout/line.xml}：图标 + 文件名 */
    private static class FileCellRenderer extends JPanel implements ListCellRenderer<File> {
        private final JLabel lbIcon = new JLabel();
        private final JLabel lbName = new JLabel();

        FileCellRenderer() {
            setLayout(new BorderLayout(10, 0));       // TextView 的 paddingLeft=10dp
            setBorder(new EmptyBorder(5, 10, 5, 10)); // ImageView/TextView 的 padding
            lbIcon.setPreferredSize(new Dimension(24, 24));
            lbName.setFont(HoloContent.font(Font.PLAIN));
            add(lbIcon, BorderLayout.WEST);
            add(lbName, BorderLayout.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends File> list, File value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            lbIcon.setIcon(value.isDirectory() ? folderIcon() : fileIcon());
            lbName.setText(value.getName());
            setBackground(isSelected ? HoloContent.LIST_SELECTED : Color.BLACK);
            lbName.setForeground(HoloContent.TEXT);
            return this;
        }
    }

    private static ImageIcon folderIconCache;
    private static ImageIcon fileIconCache;

    /** {@code R.drawable.folder} */
    static ImageIcon folderIcon() {
        if (folderIconCache == null) {
            folderIconCache = new ImageIcon(myFileExplorerActivity.class.getResource("/drawable/folder.png"));
        }
        return folderIconCache;
    }

    /** {@code R.drawable.file} */
    static ImageIcon fileIcon() {
        if (fileIconCache == null) {
            fileIconCache = new ImageIcon(myFileExplorerActivity.class.getResource("/drawable/file.png"));
        }
        return fileIconCache;
    }

    // ================================================================ 测试缝

    /** 供测试读取当前显示的条目名 */
    public List<String> getDisplayNames() {
        List<String> out = new ArrayList<String>();
        for (File f : displayFiles) out.add(f.getName());
        return out;
    }

    public String getPathText() {
        return tvPath.getText();
    }

    public HoloConfirmDialog getExitDialog() {
        return exitDlg;
    }
}
