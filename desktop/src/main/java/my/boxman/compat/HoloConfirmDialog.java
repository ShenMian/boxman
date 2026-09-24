package my.boxman.compat;

import javax.swing.*;
import java.awt.*;

/**
 * 原版
 * {@code new AlertDialog.Builder(ctx, AlertDialog.THEME_HOLO_DARK)
 *        .setMessage(m).setCancelable(false)
 *        .setNegativeButton("取消", null)
 *        .setPositiveButton("确定", listener)}
 * 的等价物：Holo 外壳 + 一段正文 + 「取消 / 确定」两个按钮。
 *
 * <p>标题通常传 {@code ""} —— 原版这种确认框大多不设标题，此时
 * {@link HoloAlertDialog} 不会画顶部的标题栏与蓝线，只剩正文 + 按钮栏，
 * 与 framework 的 {@code alert_dialog_holo.xml} 在无标题时一致。
 *
 * <p>按钮顺序沿用原版：{@code button2}（取消）在左、{@code button1}（确定）在最右，
 * 两个按钮 {@code layout_weight="1"}` 等分铺满按钮栏。
 */
public class HoloConfirmDialog extends HoloAlertDialog {

    public HoloConfirmDialog(Frame owner, String title, String message,
                             String negativeText, String positiveText, Runnable onPositive) {
        super(owner, title);
        setContentView(HoloMessageDialog.messageBody(message));
        addButton(negativeText, this::dispose);
        JButton ok = addButton(positiveText, () -> {
            dispose();
            if (onPositive != null) onPositive.run();
        });
        setDefaultButton(ok);
    }

    /** 常用的「取消 / 确定」组合。 */
    public static void show(Frame owner, String message, Runnable onPositive) {
        new HoloConfirmDialog(owner, "", message, "取消", "确定", onPositive).setVisible(true);
    }
}
