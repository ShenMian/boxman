package my.boxman.compat;

import javax.swing.*;
import java.awt.*;

/**
 * 原版
 * {@code new AlertDialog.Builder(ctx, AlertDialog.THEME_HOLO_DARK)
 *        .setTitle(t).setView(new EditText(ctx))
 *        .setNegativeButton("取消", null)
 *        .setPositiveButton("确定", listener)}
 * 的等价物 —— 原版里这种「一个输入框 + 取消/确定」的框出现了很多次
 * （注释、文档名、URL、新建关卡集…），所以抽成一个通用类。
 *
 * <p>输入框照原版通用写法：{@code #242424} 底、16sp、{@code padding 4dp}、
 * 获得焦点时全选（原版是 {@code android:selectAllOnFocus="true"}）。
 */
public class HoloInputDialog extends HoloAlertDialog {

    /** 「确定」时的回调，参数是输入框里去掉首尾空白后的文本。 */
    public interface OnOk {
        void ok(String text);
    }

    public final JTextField field;

    public HoloInputDialog(Frame owner, String title, String initialText, int widthDp, OnOk onOk) {
        super(owner, title);

        field = HoloContent.field(widthDp, initialText);
        field.selectAll();

        setContentView(HoloContent.row(field));

        addButton("取消", this::dispose);
        JButton ok = addButton("确定", () -> {
            dispose();
            if (onOk != null) onOk.ok(field.getText().trim());
        });
        setDefaultButton(ok);
    }
}
