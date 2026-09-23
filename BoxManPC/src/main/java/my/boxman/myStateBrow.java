package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.Collections;
import java.util.Comparator;

/**
 * Level State and Solution Browser for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myStateBrow Activity.
 */
public class myStateBrow extends JFrame {

    public JTabbedPane tabPane;
    public JList<state_Node> listStates;
    public JList<state_Node> listAnswers;
    public DefaultListModel<state_Node> modelStates;
    public DefaultListModel<state_Node> modelAnswers;

    public static int my_Sort = 0;  // 0: moves, 1: pushes, 2: time

    public static class SortComparator implements Comparator<state_Node> {
        @Override
        public int compare(state_Node o1, state_Node o2) {
            if (my_Sort == 0) {
                if (o1.moves != o2.moves) return Integer.compare(o1.moves, o2.moves);
                return Integer.compare(o1.pushs, o2.pushs);
            } else if (my_Sort == 1) {
                if (o1.pushs != o2.pushs) return Integer.compare(o1.pushs, o2.pushs);
                return Integer.compare(o1.moves, o2.moves);
            } else {
                return (o1.time != null && o2.time != null) ? o1.time.compareTo(o2.time) : 0;
            }
        }
    }

    public myStateBrow() {
        setTitle("关卡状态与答案 - 推箱快手");
        setSize(550, 450);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        loadData();
    }

    private void initUI() {
        tabPane = new JTabbedPane();

        modelStates = new DefaultListModel<>();
        listStates = new JList<>(modelStates);
        listStates.setCellRenderer(new StateCellRenderer());
        listStates.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelected(listStates.getSelectedValue());
                }
            }
        });
        setupContextMenu(listStates, true);

        modelAnswers = new DefaultListModel<>();
        listAnswers = new JList<>(modelAnswers);
        listAnswers.setCellRenderer(new StateCellRenderer());
        listAnswers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelected(listAnswers.getSelectedValue());
                }
            }
        });
        setupContextMenu(listAnswers, false);

        tabPane.addTab("状态", new JScrollPane(listStates));
        tabPane.addTab("答案", new JScrollPane(listAnswers));

        add(tabPane, BorderLayout.CENTER);

        // Menu bar
        setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu mSort = new JMenu("排序方式");

        JMenuItem miSortMoves = new JMenuItem("移动优先");
        miSortMoves.addActionListener(e -> { my_Sort = 0; sortAnswers(); });
        JMenuItem miSortPushes = new JMenuItem("推动优先");
        miSortPushes.addActionListener(e -> { my_Sort = 1; sortAnswers(); });
        JMenuItem miSortTime = new JMenuItem("时间优先");
        miSortTime.addActionListener(e -> { my_Sort = 2; sortAnswers(); });

        mSort.add(miSortMoves);
        mSort.add(miSortPushes);
        mSort.add(miSortTime);
        mb.add(mSort);

        return mb;
    }

    private void setupContextMenu(JList<state_Node> list, boolean isState) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem miOpen = new JMenuItem("载入");
        miOpen.addActionListener(e -> loadSelected(list.getSelectedValue()));

        JMenuItem miCopyLurd = new JMenuItem("复制 LURD 到剪贴板");
        miCopyLurd.addActionListener(e -> {
            state_Node nd = list.getSelectedValue();
            if (nd != null && nd.inf != null) {
                copyToClipboard(nd.inf);
                JOptionPane.showMessageDialog(this, "动作已复制到剪贴板！");
            }
        });

        JMenuItem miDelete = new JMenuItem("删除");
        miDelete.addActionListener(e -> {
            state_Node nd = list.getSelectedValue();
            if (nd != null && mySQLite.m_SQL != null) {
                mySQLite.m_SQL.del_S(nd.id);
                loadData();
            }
        });

        popup.add(miOpen);
        popup.add(miCopyLurd);
        popup.addSeparator();
        popup.add(miDelete);

        if (isState) {
            JMenuItem miDelAll = new JMenuItem("删除全部状态");
            miDelAll.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(this, "确定删除该关卡的所有保存状态吗？", "确认", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION && mySQLite.m_SQL != null && myMaps.curMap != null) {
                    mySQLite.m_SQL.del_S_ALL(myMaps.curMap.Level_id);
                    loadData();
                }
            });
            popup.add(miDelAll);
        }

        list.setComponentPopupMenu(popup);
    }

    public void loadData() {
        modelStates.clear();
        modelAnswers.clear();

        if (mySQLite.m_SQL != null && myMaps.curMap != null) {
            mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        }

        if (myMaps.mState1 != null) {
            for (state_Node nd : myMaps.mState1) {
                modelStates.addElement(nd);
            }
        }
        if (myMaps.mState2 != null) {
            sortAnswers();
        }
    }

    private void sortAnswers() {
        modelAnswers.clear();
        if (myMaps.mState2 != null) {
            Collections.sort(myMaps.mState2, new SortComparator());
            for (state_Node nd : myMaps.mState2) {
                modelAnswers.addElement(nd);
            }
        }
    }

    private void loadSelected(state_Node nd) {
        if (nd != null && mySQLite.m_SQL != null) {
            mySQLite.m_SQL.load_State(nd.id);
            myMaps.m_StateIsRedy = true;
            dispose();
        }
    }

    private static class StateCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof state_Node) {
                state_Node nd = (state_Node) value;
                setText(String.format("步数: %d  推数: %d  时间: %s", nd.moves, nd.pushs, nd.time != null ? nd.time : ""));
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
