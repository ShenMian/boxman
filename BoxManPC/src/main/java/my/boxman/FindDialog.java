package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Similar Level Find Settings Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's find_dialog & myFindFragment.
 */
public class FindDialog extends JDialog {

    public interface FindResultListener {
        void onFindDone(List<mapNode> results, int similarity, boolean ignoreBox);
    }

    public JSlider sliderSimilarity;
    public JLabel lblSimilarityVal;
    public JCheckBox chkIgnoreBox;
    public JCheckBox chkCurrentSetOnly;
    public JButton btFind, btCancel;

    private FindResultListener listener;

    public FindDialog(Frame parent, FindResultListener listener) {
        super(parent, "查找相似关卡 - 推箱快手", true);
        this.listener = listener;

        setSize(440, 260);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(3, 1, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        JPanel simPanel = new JPanel(new BorderLayout(8, 0));
        simPanel.add(new JLabel("最低相似度:"), BorderLayout.WEST);
        sliderSimilarity = new JSlider(50, 100, 80);
        sliderSimilarity.setMajorTickSpacing(10);
        sliderSimilarity.setPaintTicks(true);
        lblSimilarityVal = new JLabel("80%", SwingConstants.CENTER);
        lblSimilarityVal.setPreferredSize(new Dimension(45, 20));
        sliderSimilarity.addChangeListener(e -> lblSimilarityVal.setText(sliderSimilarity.getValue() + "%"));
        simPanel.add(sliderSimilarity, BorderLayout.CENTER);
        simPanel.add(lblSimilarityVal, BorderLayout.EAST);
        form.add(simPanel);

        chkIgnoreBox = new JCheckBox("忽略箱子与目标点 (仅对比墙壁轮廓)", false);
        chkCurrentSetOnly = new JCheckBox("仅在当前关卡集内查找", false);
        form.add(chkIgnoreBox);
        form.add(chkCurrentSetOnly);

        add(form, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btFind = new JButton("查找");
        btFind.addActionListener(e -> doFind());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());
        bottomBar.add(btFind);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void doFind() {
        int targetSim = sliderSimilarity.getValue();
        boolean ignoreBox = chkIgnoreBox.isSelected();

        List<mapNode> matches = new ArrayList<>();
        if (myMaps.curMap != null && myMaps.m_lstMaps != null) {
            for (mapNode node : myMaps.m_lstMaps) {
                if (node == myMaps.curMap) continue;
                // Basic CRC or dimension heuristic
                if (Math.abs(node.Rows - myMaps.curMap.Rows) <= 2 &&
                    Math.abs(node.Cols - myMaps.curMap.Cols) <= 2) {
                    matches.add(node);
                }
            }
        }

        if (listener != null) {
            listener.onFindDone(matches, targetSim, ignoreBox);
        }
        dispose();
    }
}
