package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * Create New Level Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code myGridView.java:455} 的
 * {@code setTitle("关卡尺寸")} 取 <b>「关卡尺寸」</b>，按钮 取消 / 确定。
 *
 * <p>原版 {@code res/layout/new_level_dialog.xml} 只有「列 × 行」两个 100dp 数字框
 * （居中一行，底 {@code #363636}，输入框 {@code #242424}，16sp）。PC 版额外加了
 * 关卡标题 / 作者姓名两个输入框（原版没有），这里<b>保留</b>以免改变功能，
 * 仅统一到 Holo 深色配色并把输入框排成原版那样的居中行。
 */
public class NewLevelDialog extends HoloAlertDialog {

    public interface NewLevelListener {
        void onNewLevel(String title, String author, int rows, int cols);
    }

    public JTextField tfTitle;
    public JTextField tfAuthor;
    public JSpinner spRows;
    public JSpinner spCols;
    public JButton btOK, btCancel;

    private final NewLevelListener listener;

    public NewLevelDialog(Frame parent, NewLevelListener listener) {
        super(parent, "关卡尺寸");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        tfTitle = HoloContent.field(160, "新关卡");
        tfAuthor = HoloContent.field(160, "PC作者");
        spRows = HoloContent.spinner(80, 15, 3, 50);
        spCols = HoloContent.spinner(80, 15, 3, 50);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("关卡标题:"), tfTitle),
                HoloContent.row(HoloContent.label("作者姓名:"), tfAuthor),
                HoloContent.row(HoloContent.label("初始列数 (3~50):"), spCols),
                HoloContent.row(HoloContent.label("初始行数 (3~50):"), spRows)));

        btCancel = addButton("取消", this::dispose);
        btOK = addButton("确定", () -> {
            if (listener != null) {
                listener.onNewLevel(tfTitle.getText().trim(), tfAuthor.getText().trim(),
                        (Integer) spRows.getValue(), (Integer) spCols.getValue());
            }
            dispose();
        });
        setDefaultButton(btOK);
    }
}
