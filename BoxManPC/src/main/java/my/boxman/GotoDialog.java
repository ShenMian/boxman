package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * Goto Level Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}（原版 {@code AlertDialog} + {@code THEME_HOLO_DARK} 的等价物）。
 * 标题按原版 {@code myGridView.java:552} 的 {@code setTitle("跳至")} 取 <b>「跳至」</b>。
 *
 * <p>原版内容布局 {@code res/layout/goto_dialog.xml} 是
 * 「6dp {@code #363636} 条 → 居中一行（{@code TextView "输入位置: "} + 160dp 数字
 * {@code EditText}，底 {@code #242424}）→ 6dp 条」，按钮 取消 / 确定。
 * 这里保留 PC 版用 {@link JSpinner} 输入的做法（字段 {@link #spLevel} 被回归测试锁定），
 * 但把它收窄到原版输入框的 160dp 并放进同样的居中行里。
 */
public class GotoDialog extends HoloAlertDialog {

    public interface GotoLevelListener {
        void onGotoLevel(int targetLevelIndex);
    }

    public JSpinner spLevel;
    public JLabel lblRange;
    public JButton btOK, btCancel;

    private final int maxLevels;
    private final GotoLevelListener listener;

    public GotoDialog(Frame parent, int currentLevel, int totalLevels, GotoLevelListener listener) {
        super(parent, "跳至");
        this.maxLevels = Math.max(1, totalLevels);
        this.listener = listener;
        initUI(currentLevel);
    }

    private void initUI(int curLevel) {
        int initVal = Math.max(1, Math.min(curLevel, maxLevels));
        spLevel = new JSpinner(new SpinnerNumberModel(initVal, 1, maxLevels, 1));
        spLevel.setFont(HoloContent.font(Font.BOLD));
        spLevel.setBackground(HoloContent.FIELD_BG);
        spLevel.setForeground(HoloContent.TEXT);
        spLevel.setBorder(BorderFactory.createLineBorder(HoloContent.FIELD_BG));
        Dimension spinnerSize = new Dimension(160, 32);
        spLevel.setPreferredSize(spinnerSize);
        spLevel.setMinimumSize(spinnerSize);
        spLevel.setMaximumSize(spinnerSize);
        JComponent editor = spLevel.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(HoloContent.FIELD_BG);
            tf.setForeground(HoloContent.TEXT);
            tf.setCaretColor(HoloContent.TEXT);
            tf.setFont(HoloContent.font(Font.PLAIN));
            tf.setBorder(new javax.swing.border.EmptyBorder(
                    HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                    HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        }

        // 原版 goto_dialog.xml 的居中一行：「输入位置: 」+ 160dp 数字框
        lblRange = HoloContent.label("输入位置: ");
        setContentView(HoloContent.column(HoloContent.row(lblRange, spLevel)));

        // 原版按钮栏顺序：negative(取消) 在左、positive(确定) 在右
        btCancel = addButton("取消", this::dispose);
        btOK = addButton("确定", () -> {
            int target = (Integer) spLevel.getValue();
            if (listener != null) {
                listener.onGotoLevel(target - 1); // 0-based
            }
            dispose();
        });
        setDefaultButton(btOK);
    }
}
