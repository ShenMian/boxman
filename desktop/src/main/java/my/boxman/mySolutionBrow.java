package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import my.boxman.compat.UiWindow;
import my.boxman.compat.HoloPopupMenu;

/**
 * Similar Level Solution Browser for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's mySolutionBrow Activity.
 */
public class mySolutionBrow extends JDialog {

    public JList<state_Node> listSolutions;
    public DefaultListModel<state_Node> modelSolutions;

    public mySolutionBrow(Frame parent) {
        super(parent, "相似关卡答案 - 推箱快手", true);

        initUI();
        loadData();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        modelSolutions = new DefaultListModel<>();
        listSolutions = new JList<>(modelSolutions);
        listSolutions.setCellRenderer(new SolutionCellRenderer());
        listSolutions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    copySelectedSolution();
                }
            }
        });

        // 原版 mySolutionBrow.onCreateContextMenu() 只有一项（其余 3、4 两项被 <!-- --> 注释掉）：
        //     menu.add(0, 5, 0, "导出到剪切板: Lurd");
        // Android 上下文菜单与 ActionBar 溢出菜单共用 popup_menu_holo_dark 样式 → HoloPopupMenu。
        JPopupMenu popup = HoloPopupMenu.create();
        HoloPopupMenu.addItem(popup, "导出到剪切板: Lurd", this::copySelectedSolution);
        listSolutions.setComponentPopupMenu(popup);

        add(new JScrollPane(listSolutions), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btCopy = new JButton("复制到剪贴板");
        btCopy.addActionListener(e -> copySelectedSolution());
        JButton btClose = new JButton("关闭");
        btClose.addActionListener(e -> dispose());

        bottomBar.add(btCopy);
        bottomBar.add(btClose);
        add(bottomBar, BorderLayout.SOUTH);
    }

    public void loadData() {
        modelSolutions.clear();
        if (myMaps.mState2 != null) {
            for (state_Node nd : myMaps.mState2) {
                modelSolutions.addElement(nd);
            }
        }
    }

    private void copySelectedSolution() {
        state_Node nd = listSolutions.getSelectedValue();
        if (nd != null) {
            if (mySQLite.m_SQL != null) {
                mySQLite.m_SQL.load_State(nd.id);
            }
            String ans = myMaps.m_State != null ? myMaps.m_State.ans : nd.inf;
            if (ans != null && !ans.isEmpty()) {
                copyToClipboard(ans);
                JOptionPane.showMessageDialog(this, "答案已复制到剪贴板！");
            }
        }
    }

    private static class SolutionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof state_Node) {
                state_Node nd = (state_Node) value;
                setText(String.format("答案 %d: 步数=%d, 推数=%d, 时间=%s", index + 1, nd.moves, nd.pushs, nd.time != null ? nd.time : ""));
            }
            return this;
        }
    }

    private static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }
}
