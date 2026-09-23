package my.boxman;

import javax.swing.*;
import java.awt.*;

/**
 * Delete Level Confirmation Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's del_dialog.xml.
 */
public class DelDialog extends JDialog {

    public interface DeleteConfirmListener {
        void onConfirmed(boolean deleteSolutionsAndStates);
    }

    public JLabel lblMessage;
    public JCheckBox chkDeleteSolutions;
    public JButton btDelete, btCancel;

    private DeleteConfirmListener listener;

    public DelDialog(Frame parent, String levelTitle, DeleteConfirmListener listener) {
        super(parent, "删除关卡确认", true);
        this.listener = listener;

        setSize(380, 190);
        setLocationRelativeTo(parent);
        initUI(levelTitle);
    }

    private void initUI(String levelTitle) {
        setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel(new GridLayout(2, 1, 6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));

        lblMessage = new JLabel("确定要删除关卡【" + (levelTitle != null ? levelTitle : "当前关卡") + "】吗？");
        lblMessage.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        content.add(lblMessage);

        chkDeleteSolutions = new JCheckBox("同时删除该关卡的所有解答与保存状态", true);
        content.add(chkDeleteSolutions);

        add(content, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btDelete = new JButton("删除");
        btDelete.setForeground(Color.RED);
        btDelete.addActionListener(e -> {
            if (listener != null) {
                listener.onConfirmed(chkDeleteSolutions.isSelected());
            }
            dispose();
        });
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());

        bottomBar.add(btDelete);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }
}
