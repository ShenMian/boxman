package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * Delete Level Confirmation Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题取原版确认类对话框的 {@code setTitle("确认")}
 * （{@code myGridView.java:660}）。
 *
 * <p><b>注意：本对话框与原版 {@code res/layout/del_dialog.xml} 并不是同一个东西。</b>
 * 原版 {@code del_dialog} 是「批量删除范围」——标题 {@code "删除范围: 1 -- N"}，
 * 内容是两个 100dp 数字框加一个 60dp 的「 -- 」（{@code myGridView.java:682}）。
 * PC 版这个「删除确认 + 是否连同解答与状态一起删」的勾选框在原版里没有对应布局，
 * 是移植时新增的；此处保留其功能，仅统一 Holo 外观，是否改回原版的「删除范围」形式待定。
 */
public class DelDialog extends HoloAlertDialog {

    public interface DeleteConfirmListener {
        void onConfirmed(boolean deleteSolutionsAndStates);
    }

    public JLabel lblMessage;
    public JCheckBox chkDeleteSolutions;
    public JButton btDelete, btCancel;

    private final DeleteConfirmListener listener;

    public DelDialog(Frame parent, String levelTitle, DeleteConfirmListener listener) {
        super(parent, "确认");
        this.listener = listener;
        initUI(levelTitle);
    }

    private void initUI(String levelTitle) {
        String name = (levelTitle != null && !levelTitle.isEmpty()) ? levelTitle : "当前关卡";

        lblMessage = HoloContent.label("<html><body style='width:280px'>确定要删除关卡【"
                + name + "】吗？</body></html>");
        chkDeleteSolutions = HoloContent.check("同时删除该关卡的所有解答与保存状态", true);

        setContentView(HoloContent.column(
                HoloContent.row(lblMessage),
                HoloContent.row(chkDeleteSolutions)));

        btCancel = addButton("取消", this::dispose);
        btDelete = addButton("删除", () -> {
            if (listener != null) {
                listener.onConfirmed(chkDeleteSolutions.isSelected());
            }
            dispose();
        });
        setDefaultButton(btDelete);
    }
}
