package my.boxman.compat;

import javax.swing.*;
import java.awt.*;

/**
 * 原版
 * {@code new AlertDialog.Builder(ctx, AlertDialog.THEME_HOLO_DARK).setView(v).setTitle(t)
 *        .setPositiveButton("确定", null).create().show()}
 * 的等价物：Holo 外壳 + 一段**自定义内容**（{@code setView}）。
 *
 * <p>与 {@link HoloMessageDialog}（一段正文）和 {@link HoloChoiceDialog}（一个列表）不同，
 * 这类对话框的内容完全由布局文件决定，例如「图像识别」的「识别设置」
 * （{@code res/layout/recog_dialog.xml}：相似度 5~9 的一排方块按钮）。
 */
public class HoloViewDialog extends HoloAlertDialog {

    public HoloViewDialog(Frame owner, String title, JComponent view) {
        super(owner, title);
        setContentView(view);
    }

    /** 常用形态：只有一个「确定」按钮，点掉即关。 */
    public static HoloViewDialog show(Frame owner, String title, JComponent view, String positiveText) {
        HoloViewDialog d = new HoloViewDialog(owner, title, view);
        d.addButton(positiveText, d::dispose);
        d.setVisible(true);
        return d;
    }
}
