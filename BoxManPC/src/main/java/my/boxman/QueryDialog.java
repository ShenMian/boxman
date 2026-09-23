package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Level Query Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's query_dialog & myQueryFragment.
 */
public class QueryDialog extends JDialog {

    public interface QueryResultListener {
        void onQueryDone(List<mapNode> results);
    }

    public JTextField tfTitle;
    public JTextField tfAuthor;
    public JSpinner spRowsMin, spRowsMax;
    public JSpinner spColsMin, spColsMax;
    public JSpinner spBoxesMin, spBoxesMax;
    public JCheckBox chkSolvedOnly;
    public JButton btSearch, btCancel;

    private QueryResultListener listener;

    public QueryDialog(Frame parent, QueryResultListener listener) {
        super(parent, "高级关卡查询 - 推箱快手", true);
        this.listener = listener;

        setSize(480, 360);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        form.add(new JLabel("关卡标题 (包含):"));
        tfTitle = new JTextField();
        form.add(tfTitle);

        form.add(new JLabel("关卡作者 (包含):"));
        tfAuthor = new JTextField();
        form.add(tfAuthor);

        form.add(new JLabel("行数范围 (最小 ~ 最大):"));
        JPanel pRows = new JPanel(new GridLayout(1, 2, 4, 0));
        spRowsMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spRowsMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        pRows.add(spRowsMin);
        pRows.add(spRowsMax);
        form.add(pRows);

        form.add(new JLabel("列数范围 (最小 ~ 最大):"));
        JPanel pCols = new JPanel(new GridLayout(1, 2, 4, 0));
        spColsMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spColsMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        pCols.add(spColsMin);
        pCols.add(spColsMax);
        form.add(pCols);

        form.add(new JLabel("箱子数范围 (最小 ~ 最大):"));
        JPanel pBoxes = new JPanel(new GridLayout(1, 2, 4, 0));
        spBoxesMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spBoxesMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        pBoxes.add(spBoxesMin);
        pBoxes.add(spBoxesMax);
        form.add(pBoxes);

        form.add(new JLabel("解题状态:"));
        chkSolvedOnly = new JCheckBox("仅查询已解关卡", false);
        form.add(chkSolvedOnly);

        add(form, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btSearch = new JButton("查询");
        btSearch.addActionListener(e -> doSearch());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());
        bottomBar.add(btSearch);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void doSearch() {
        String titleKey = tfTitle.getText().trim().toLowerCase();
        String authorKey = tfAuthor.getText().trim().toLowerCase();
        int rMin = (Integer) spRowsMin.getValue();
        int rMax = (Integer) spRowsMax.getValue();
        int cMin = (Integer) spColsMin.getValue();
        int cMax = (Integer) spColsMax.getValue();
        int bMin = (Integer) spBoxesMin.getValue();
        int bMax = (Integer) spBoxesMax.getValue();
        boolean solvedOnly = chkSolvedOnly.isSelected();

        List<mapNode> matches = new ArrayList<>();
        if (myMaps.m_lstMaps != null) {
            for (mapNode node : myMaps.m_lstMaps) {
                if (!titleKey.isEmpty() && (node.Title == null || !node.Title.toLowerCase().contains(titleKey))) {
                    continue;
                }
                if (!authorKey.isEmpty() && (node.Author == null || !node.Author.toLowerCase().contains(authorKey))) {
                    continue;
                }
                if (node.Rows < rMin || node.Rows > rMax) continue;
                if (node.Cols < cMin || node.Cols > cMax) continue;
                if (solvedOnly && !node.Solved) continue;

                matches.add(node);
            }
        }

        if (listener != null) {
            listener.onQueryDone(matches);
        }
        dispose();
    }
}
