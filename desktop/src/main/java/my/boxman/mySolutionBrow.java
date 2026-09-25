package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.UiWindow;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * 答案浏览 —— 原版 {@code mySolutionBrow.java}（304 行）的 PC 等价物。
 *
 * <p>原版是一个 **Activity**：{@code setContentView(R.layout.s_main)}
 * （整屏 {@code #ff004040} 底 + 一个 {@code ExpandableListView}），
 * 标题「相似关卡」，ActionBar 开返回折角、**没有** {@code onCreateOptionsMenu}
 * （仓库里不存在 {@code res/menu/solution*.xml}）。
 *
 * <p>列表是**两级可展开列表**：一个组「答案」（{@code s_groups}，paddingLeft 32dp、
 * 16sp、{@code #ffdddddd}）+ 若干子项（{@code s_child}：主行 {@code inf} 16sp
 * {@code #ffbbbbbb} paddingLeft 20dp，次行 {@code time} 12sp {@code #ff888888}
 * **右对齐** paddingRight 20dp）。{@code onCreate} 末尾 {@code expandGroup(0)}。
 *
 * <p>上下文菜单只有 **1 项**「导出到剪切板: Lurd」（其余 case 3 / case 4 在原版里
 * 被整段注释掉）。选中后 {@code load_State(m_Sel_id)} → 弹「剪切板：Lurd」对话框，
 * 里面是一个**可编辑**的文本框预填答案，点「确定」才 {@code myMaps.saveClipper(...)}。
 *
 * <p>⚠️ 这次重写删掉了 PC 自造的底部按钮栏（「复制到剪贴板」/「关闭」）与
 * {@code JOptionPane}；并且**不再直接写剪切板** —— 原版一定要先过那个可编辑对话框。
 *
 * <p>⚠️ 原版 {@code writeStateFile()} / {@code myExport2()} / {@code mDlg}（覆写确认框）
 * **在原版里就不可达**：它们只被注释掉的 case 3 / case 4 调用，所以这里不移植。
 */
public class mySolutionBrow extends JFrame {

    /** 原版 {@code String[] s_groups = { "答案" }} */
    public static final String GROUP_TITLE = "答案";
    /** 原版 {@code onCreateContextMenu} 唯一一项 */
    public static final String CONTEXT_ITEM = "导出到剪切板: Lurd";

    /** {@code s_main.xml} 的 {@code android:background="#ff004040"} */
    static final Color SOL_BG = new Color(0x00, 0x40, 0x40);
    /** {@code s_main.xml} 的 {@code android:listSelector="#ff0064aa"} */
    static final Color SOL_SELECTED = new Color(0x00, 0x64, 0xAA);
    static final Color GROUP_FG = new Color(0xDD, 0xDD, 0xDD);
    static final Color CHILD_FG = new Color(0xBB, 0xBB, 0xBB);
    static final Color CHILD_TIME_FG = new Color(0x88, 0x88, 0x88);

    public myActionBar actionBar;
    /** 第 0 项是组头 {@link #GROUP_TITLE}，之后依次是 {@code myMaps.mState2} 的条目 */
    public JList<Object> listSolutions;
    public DefaultListModel<Object> modelSolutions;

    /** 原版 {@code c_Pos}：当前子项下标，-1 表示没选 */
    public int c_Pos = -1;
    /** 原版 {@code m_Sel_id}：当前子项的答案 id */
    public long m_Sel_id = -1;

    /** 测试缝：把「弹模态框」换成「只记不弹」 */
    Consumer<JDialog> dialogShower = dlg -> dlg.setVisible(true);
    private JPopupMenu contextMenu;
    private HoloAlertDialog clipDlg;
    private JTextArea clipArea;

    public mySolutionBrow(Frame parent) {
        setTitle("相似关卡 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        loadData();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
        if (parent != null) setLocationRelativeTo(parent);
    }

    // ================================================================ 界面

    private void initUI() {
        setLayout(new BorderLayout());

        actionBar = new myActionBar();
        actionBar.setBarTitle("相似关卡");
        actionBar.setUpEnabled(true, this::dispose);     // android.R.id.home → finish()
        add(actionBar, BorderLayout.NORTH);

        modelSolutions = new DefaultListModel<Object>();
        listSolutions = new JList<Object>(modelSolutions);
        listSolutions.setCellRenderer(new SolutionCellRenderer());
        listSolutions.setBackground(SOL_BG);
        listSolutions.setForeground(CHILD_FG);
        listSolutions.setSelectionBackground(SOL_SELECTED);
        listSolutions.setSelectionForeground(GROUP_FG);
        listSolutions.setOpaque(true);

        listSolutions.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 长按（PC：右键）要先定好 c_Pos / m_Sel_id，弹菜单 Swing 自己会做
                onRowAt(listSolutions.locationToIndex(e.getPoint()));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) return;
                // 原版 onChildClick：只认子项，组头点不出东西
                onRowAt(listSolutions.locationToIndex(e.getPoint()));
            }
        });

        contextMenu = buildContextMenu();
        listSolutions.setComponentPopupMenu(contextMenu);

        JScrollPane sp = HoloContent.scroll(listSolutions);
        HoloContent.darkScrollBar(sp);
        sp.getViewport().setBackground(SOL_BG);
        add(sp, BorderLayout.CENTER);

        getContentPane().setBackground(SOL_BG);
        setBackground(SOL_BG);
    }

    /**
     * 原版 {@code onChildClick} / {@code onItemLongClick} 的公共部分：
     * 记住子项位置与答案 id。组头（下标 0）不算。
     */
    void onRowAt(int index) {
        if (index <= 0) {                   // 0 = 组头「答案」，-1 = 空白处
            c_Pos = -1;
            return;
        }
        c_Pos = index - 1;                  // 减掉组头那一行
        if (c_Pos >= 0 && c_Pos < myMaps.mState2.size()) {
            m_Sel_id = myMaps.mState2.get(c_Pos).id;
        }
    }

    /** 原版 {@code expandGroup(0)} + 适配器的数据源 */
    public void loadData() {
        modelSolutions.clear();
        modelSolutions.addElement(GROUP_TITLE);                 // 组头，恒展开
        if (myMaps.mState2 != null) {
            for (state_Node nd : myMaps.mState2) modelSolutions.addElement(nd);
        }
    }

    // ================================================================ 上下文菜单

    JPopupMenu buildContextMenu() {
        JPopupMenu menu = HoloPopupMenu.create();
        HoloPopupMenu.addItem(menu, CONTEXT_ITEM, () -> onContextItemSelected(5));
        return menu;
    }

    /** 原版 {@code onContextItemSelected}：只有 case 5，且 {@code c_Pos >= 0} 才做事 */
    boolean onContextItemSelected(int itemId) {
        if (c_Pos < 0) return true;
        if (itemId != 5) return true;
        if (mySQLite.m_SQL != null) {
            myMaps.m_State = mySQLite.m_SQL.load_State(m_Sel_id);
        }
        showClipboardDialog();
        return true;
    }

    /** 原版 {@code myExport()}：标题「剪切板：Lurd」+ 可编辑文本框预填答案 */
    void showClipboardDialog() {
        String ans = myMaps.m_State != null && myMaps.m_State.ans != null ? myMaps.m_State.ans : "";
        clipArea = new JTextArea(ans);
        clipArea.setEditable(true);
        clipArea.setLineWrap(true);
        clipArea.setWrapStyleWord(true);
        clipArea.setFont(HoloContent.font(Font.PLAIN));
        clipArea.setBackground(HoloContent.FIELD_BG);
        clipArea.setForeground(HoloContent.TEXT);
        clipArea.setCaretColor(HoloContent.TEXT);
        clipArea.setBorder(new EmptyBorder(HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));

        JScrollPane sp = HoloContent.scroll(clipArea);
        sp.setPreferredSize(new Dimension(300, 140));

        clipDlg = HoloAlertDialog.create(this, "剪切板：Lurd");
        clipDlg.setContentView(HoloContent.column(sp));
        clipDlg.addButton("取消", null);                                    // setNegativeButton("取消", null)
        clipDlg.addButton("确定", () -> {                                    // setPositiveButton("确定")
            myMaps.saveClipper(clipArea.getText());
            clipDlg.dispose();
        });
        dialogShower.accept(clipDlg);
    }

    // ================================================================ 渲染

    /** 组头 {@code s_groups.xml} / 子项 {@code s_child.xml} */
    private static class SolutionCellRenderer extends JPanel implements ListCellRenderer<Object> {
        private final JLabel lbGroup = new JLabel();
        private final JLabel lbInf = new JLabel();
        private final JLabel lbTime = new JLabel();

        SolutionCellRenderer() {
            setLayout(new BorderLayout());
            lbGroup.setFont(HoloContent.font(Font.PLAIN));
            lbGroup.setForeground(GROUP_FG);
            lbGroup.setBorder(new EmptyBorder(6, 32, 6, 0));    // paddingLeft 32dp + margin 6dp

            lbInf.setFont(HoloContent.font(Font.PLAIN));
            lbInf.setForeground(CHILD_FG);
            lbTime.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));   // textSize 12sp
            lbTime.setForeground(CHILD_TIME_FG);
            lbTime.setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            removeAll();
            setBackground(isSelected ? SOL_SELECTED : SOL_BG);
            setBorder(BorderFactory.createEmptyBorder());
            if (value instanceof state_Node) {
                state_Node nd = (state_Node) value;
                lbInf.setText(nd.inf == null ? "" : nd.inf);
                lbTime.setText(nd.time == null ? "" : nd.time);
                lbInf.setBorder(new EmptyBorder(6, 20, 0, 0));         // paddingLeft 20dp
                lbTime.setBorder(new EmptyBorder(0, 0, 6, 20));        // paddingRight 20dp
                add(lbInf, BorderLayout.NORTH);
                add(lbTime, BorderLayout.SOUTH);
                setPreferredSize(null);
            } else {
                lbGroup.setText(value == null ? "" : String.valueOf(value));
                add(lbGroup, BorderLayout.CENTER);
            }
            return this;
        }
    }

    // ================================================================ 测试缝

    public JPopupMenu getContextMenu() {
        return contextMenu;
    }

    public HoloAlertDialog getClipboardDialog() {
        return clipDlg;
    }

    public JTextArea getClipArea() {
        return clipArea;
    }
}
