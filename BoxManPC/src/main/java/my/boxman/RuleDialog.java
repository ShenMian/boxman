package my.boxman;

import javax.swing.*;
import java.awt.*;

/**
 * Grid Ruler Settings Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's rule_dialog.xml.
 */
public class RuleDialog extends JDialog {

    public interface RuleChangeListener {
        void onRuleChanged(int fontColor, int elementsBitmask);
    }

    public JSlider colorSlider;
    public JLabel lblPreview1, lblPreview2;
    public JCheckBox chkWall, chkFloor, chkGoal, chkBox, chkPlayer;
    public JButton btOK, btCancel;

    private RuleChangeListener listener;

    public RuleDialog(Frame parent, RuleChangeListener listener) {
        super(parent, "设置标尺与坐标", true);
        this.listener = listener;

        setSize(420, 320);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Slider for gray level
        int curGray = myMaps.m_Sets[21] & 0xFF;
        JPanel pSlider = new JPanel(new BorderLayout(8, 0));
        pSlider.add(new JLabel("标尺字体灰度:"), BorderLayout.WEST);
        colorSlider = new JSlider(0, 255, curGray);
        pSlider.add(colorSlider, BorderLayout.CENTER);
        content.add(pSlider);

        // Preview labels
        JPanel pPreview = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 6));
        lblPreview1 = new JLabel("标尺示例: A1");
        lblPreview2 = new JLabel("[ 1, 1 ]");
        updatePreviewColor(curGray);
        pPreview.add(lblPreview1);
        pPreview.add(lblPreview2);
        content.add(pPreview);

        colorSlider.addChangeListener(e -> updatePreviewColor(colorSlider.getValue()));

        content.add(Box.createVerticalStrut(10));
        content.add(new JLabel("在以下元素上显示标尺/坐标:"));

        int mask = myMaps.m_Sets[22];
        chkWall = new JCheckBox("墙壁", (mask & 1) > 0);
        chkFloor = new JCheckBox("地板", (mask & 2) > 0);
        chkGoal = new JCheckBox("目标", (mask & 4) > 0);
        chkBox = new JCheckBox("箱子", (mask & 8) > 0);
        chkPlayer = new JCheckBox("仓管员", (mask & 16) > 0);

        JPanel pChecks = new JPanel(new GridLayout(3, 2, 4, 4));
        pChecks.add(chkWall);
        pChecks.add(chkFloor);
        pChecks.add(chkGoal);
        pChecks.add(chkBox);
        pChecks.add(chkPlayer);
        content.add(pChecks);

        add(content, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btOK = new JButton("确定");
        btOK.addActionListener(e -> applyAndClose());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());
        bottomBar.add(btOK);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updatePreviewColor(int gray) {
        Color c = new Color(gray, gray, gray);
        lblPreview1.setForeground(c);
        lblPreview2.setForeground(c);
        lblPreview1.repaint();
        lblPreview2.repaint();
    }

    private void applyAndClose() {
        int gray = colorSlider.getValue();
        int fontColor = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;

        int mask = 0;
        if (chkWall.isSelected()) mask |= 1;
        if (chkFloor.isSelected()) mask |= 2;
        if (chkGoal.isSelected()) mask |= 4;
        if (chkBox.isSelected()) mask |= 8;
        if (chkPlayer.isSelected()) mask |= 16;

        myMaps.m_Sets[21] = fontColor;
        myMaps.m_Sets[22] = mask;

        if (listener != null) {
            listener.onRuleChanged(fontColor, mask);
        }
        dispose();
    }
}
