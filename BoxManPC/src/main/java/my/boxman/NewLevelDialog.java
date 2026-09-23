package my.boxman;

import javax.swing.*;
import java.awt.*;

/**
 * Create New Level Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's new_level_dialog.xml.
 */
public class NewLevelDialog extends JDialog {

    public interface NewLevelListener {
        void onNewLevel(String title, String author, int rows, int cols);
    }

    public JTextField tfTitle;
    public JTextField tfAuthor;
    public JSpinner spRows;
    public JSpinner spCols;
    public JButton btOK, btCancel;

    private NewLevelListener listener;

    public NewLevelDialog(Frame parent, NewLevelListener listener) {
        super(parent, "新建关卡", true);
        this.listener = listener;

        setSize(380, 260);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        form.add(new JLabel("关卡标题:"));
        tfTitle = new JTextField("新关卡");
        form.add(tfTitle);

        form.add(new JLabel("作者姓名:"));
        tfAuthor = new JTextField("PC作者");
        form.add(tfAuthor);

        form.add(new JLabel("初始行数 (3~50):"));
        spRows = new JSpinner(new SpinnerNumberModel(15, 3, 50, 1));
        form.add(spRows);

        form.add(new JLabel("初始列数 (3~50):"));
        spCols = new JSpinner(new SpinnerNumberModel(15, 3, 50, 1));
        form.add(spCols);

        add(form, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btOK = new JButton("确定");
        btOK.addActionListener(e -> {
            String title = tfTitle.getText().trim();
            String author = tfAuthor.getText().trim();
            int rows = (Integer) spRows.getValue();
            int cols = (Integer) spCols.getValue();

            if (listener != null) {
                listener.onNewLevel(title, author, rows, cols);
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
