package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * 原版 {@code myGridView} 的「关卡尺寸」对话框
 * （{@code myGridView.java:446-481}，布局 {@code res/layout/new_level_dialog.xml}）。
 *
 * <p>标题 {@code setTitle("关卡尺寸")}，按钮 取消 / 确定；
 * 内容只有<b>「列数 × 行数」两个数字框</b>（居中一行，底 {@code #363636}，
 * 输入框 {@code #242424}，16sp），默认 10 列 × 15 行。
 *
 * <p>⚠️ 这里曾经多出「关卡标题 / 作者姓名」两个输入框，并把作者默认成 {@code "PC作者"} ——
 * 那是 PC 自造项（原版没有），也正好踩中审计 2.1 记的「凭空造作者名」。
 * 现已删掉：原版的标题就是自动生成的 {@code NewLevel_时间戳}，作者为空串。
 */
public class NewLevelDialog extends HoloAlertDialog {

    public interface NewLevelListener {
        void onNewLevel(int rows, int cols);
    }

    /** 原版 {@code new_level_dialog.xml} 的两个数字框默认值 */
    private static final int DEFAULT_COLS = 10;
    private static final int DEFAULT_ROWS = 15;

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
        // 原版 input21(列) / input22(行) 的初值就是 "10" / "15"
        spCols = HoloContent.spinner(80, DEFAULT_COLS, 3, 100);
        spRows = HoloContent.spinner(80, DEFAULT_ROWS, 3, 100);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("列数:", 64, SwingConstants.LEFT), spCols),
                HoloContent.row(HoloContent.label("行数:", 64, SwingConstants.LEFT), spRows)));

        btCancel = addButton("取消", this::dispose);
        btOK = addButton("确定", () -> {
            int rows = (Integer) spRows.getValue();
            int cols = (Integer) spCols.getValue();
            // 原版：越界就退回默认值（不是拒绝）
            if (rows < 3 || rows > 100) rows = DEFAULT_ROWS;
            if (cols < 3 || cols > 100) cols = DEFAULT_COLS;
            dispose();
            if (listener != null) listener.onNewLevel(rows, cols);
        });
        setDefaultButton(btOK);
    }
}
