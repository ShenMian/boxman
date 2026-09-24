package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * Grid Ruler Settings Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code myEditView.java:848} 的
 * {@code setTitle("显示标尺的元素")} 取 <b>「显示标尺的元素」</b>，按钮 取消 / 确定。
 *
 * <p>内容按原版 {@code res/layout/rule_dialog.xml} 还原：
 * 「6dp {@code #363636} 条 → 居中一行（{@code TextView "字体颜色: "} + 两个 84dp 的
 * {@code EditText "颜色示例"}，左白底右黑底、文字色随滑块变化）→ 6dp 条 → 20dp 空行
 * → 256dp {@code SeekBar}（max 255）→ 20dp 空行」。
 * 原版用 {@code setMultiChoiceItems({"墙壁","地板","目标","箱子","仓管员"})} 列出元素，
 * 这里用 5 个深色复选框竖排等价实现。
 */
public class RuleDialog extends HoloAlertDialog {

    public interface RuleChangeListener {
        void onRuleChanged(int fontColor, int elementsBitmask);
    }

    public JSlider colorSlider;
    public JLabel lblPreview1, lblPreview2;
    public JCheckBox chkWall, chkFloor, chkGoal, chkBox, chkPlayer;
    public JButton btOK, btCancel;

    private final RuleChangeListener listener;

    public RuleDialog(Frame parent, RuleChangeListener listener) {
        super(parent, "显示标尺的元素");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        int curGray = myMaps.m_Sets[21] & 0xFF;

        // 原版两个「颜色示例」框：84dp，左白底、右黑底，文字色 = 当前灰度
        lblPreview1 = previewBox(Color.WHITE);
        lblPreview2 = previewBox(Color.BLACK);

        colorSlider = HoloContent.slider(curGray, 256);
        colorSlider.addChangeListener(e -> updatePreviewColor(colorSlider.getValue()));
        updatePreviewColor(curGray);

        int mask = myMaps.m_Sets[22];
        chkWall = HoloContent.check("墙壁", (mask & 1) > 0);
        chkFloor = HoloContent.check("地板", (mask & 2) > 0);
        chkGoal = HoloContent.check("目标", (mask & 4) > 0);
        chkBox = HoloContent.check("箱子", (mask & 8) > 0);
        chkPlayer = HoloContent.check("仓管员", (mask & 16) > 0);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("字体颜色: "), lblPreview1,
                        HoloContent.gap(0), lblPreview2),
                HoloContent.band(),
                HoloContent.gap(20),
                HoloContent.row(colorSlider),
                HoloContent.gap(20),
                HoloContent.row(chkWall),
                HoloContent.row(chkFloor),
                HoloContent.row(chkGoal),
                HoloContent.row(chkBox),
                HoloContent.row(chkPlayer)));

        btCancel = addButton("取消", this::dispose);
        btOK = addButton("确定", this::applyAndClose);
        setDefaultButton(btOK);
    }

    /** 原版 {@code dialog_rule_color1/2}：固定底色 + 随滑块变化的文字色。 */
    private static JLabel previewBox(Color bg) {
        JLabel l = new JLabel("颜色示例", SwingConstants.CENTER);
        l.setFont(HoloContent.font(Font.PLAIN));
        l.setOpaque(true);
        l.setBackground(bg);
        Dimension d = new Dimension(84, 32);
        l.setPreferredSize(d);
        l.setMinimumSize(d);
        l.setMaximumSize(d);
        return l;
    }

    private void updatePreviewColor(int gray) {
        Color c = new Color(gray, gray, gray);
        lblPreview1.setForeground(c);
        lblPreview2.setForeground(c);
    }

    private void applyAndClose() {
        int gray = colorSlider.getValue();
        int fontColor = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;

        int mask = 0;
        if (chkWall.isSelected()) mask |= 1;
        if (chkFloor.isSelected()) mask |= 2;
        if (chkGoal.isSelected()) mask |= 4;
        if (chkBox.isSelected()) mask |= 8;
        if (chkPlayer.isSelected()) mask |= 16;

        myMaps.m_Sets[21] = fontColor;
        myMaps.m_Sets[22] = mask;

        if (listener != null) {
            listener.onRuleChanged(fontColor, mask);
        }
        dispose();
    }
}
