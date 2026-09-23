package my.boxman;

import javax.swing.*;
import java.awt.*;

/**
 * Goto Level Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's goto_dialog.xml.
 */
public class GotoDialog extends JDialog {

    public interface GotoLevelListener {
        void onGotoLevel(int targetLevelIndex);
    }

    public JSpinner spLevel;
    public JLabel lblRange;
    public JButton btOK, btCancel;

    private int maxLevels;
    private GotoLevelListener listener;

    public GotoDialog(Frame parent, int currentLevel, int totalLevels, GotoLevelListener listener) {
        super(parent, "跳转到指定关卡", true);
        this.maxLevels = Math.max(1, totalLevels);
        this.listener = listener;

        setSize(340, 180);
        setLocationRelativeTo(parent);
        initUI(currentLevel);
    }

    private void initUI(int curLevel) {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(2, 1, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        lblRange = new JLabel("请输入关卡序号 (范围: 1 ~ " + maxLevels + "):");
        form.add(lblRange);

        int initVal = Math.max(1, Math.min(curLevel, maxLevels));
        spLevel = new JSpinner(new SpinnerNumberModel(initVal, 1, maxLevels, 1));
        spLevel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        form.add(spLevel);

        add(form, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btOK = new JButton("跳转");
        btOK.addActionListener(e -> {
            int target = (Integer) spLevel.getValue();
            if (listener != null) {
                listener.onGotoLevel(target - 1); // 0-based
            }
            dispose();
        });
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());

        bottomBar.add(btOK);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }
}
