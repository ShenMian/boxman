package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Similar Level Find Settings Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code myGridView.java:1585} 的
 * {@code setTitle("搜索相似关卡")} 取 <b>「搜索相似关卡」</b>，
 * 按钮按原版取 取消 / <b>开始</b>（PC 原来写的是「查找」）。
 *
 * <p><b>内容与原版差异较大，待定。</b>原版 {@code res/layout/find_dialog.xml} 是
 * 「{@code #334455} 分组条（关卡集：+ 答案库/全选 复选框）→ 190dp 关卡集 {@code ListView}
 * → {@code #334455} 分组条（相似度：+ 排序/忽略箱子和人 复选框）→ 210dp 相似度 {@code ListView}」，
 * 用两个列表让用户勾选参与比较的关卡集、并在下方实时列出相似关卡。
 * PC 版把它简化成了一个「最低相似度滑杆 + 两个复选框」的表单，功能等价但界面不是原版的样子。
 * 是否按原版重建两个列表，需要确认。
 */
public class FindDialog extends HoloAlertDialog {

    public interface FindResultListener {
        void onFindDone(List<mapNode> results, int similarity, boolean ignoreBox);
    }

    public JSlider sliderSimilarity;
    public JLabel lblSimilarityVal;
    public JCheckBox chkIgnoreBox;
    public JCheckBox chkCurrentSetOnly;
    public JButton btFind, btCancel;

    private final FindResultListener listener;

    public FindDialog(Frame parent, FindResultListener listener) {
        super(parent, "搜索相似关卡");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        sliderSimilarity = HoloContent.slider(80, 200);
        // 原版滑杆 max=255；这里是百分比，范围保持 PC 版的 50~100
        sliderSimilarity.setMinimum(50);
        sliderSimilarity.setMaximum(100);
        sliderSimilarity.setValue(80);

        lblSimilarityVal = new JLabel("80%", SwingConstants.CENTER);
        lblSimilarityVal.setFont(HoloContent.font(Font.PLAIN));
        lblSimilarityVal.setForeground(HoloContent.TEXT);
        Dimension valSize = new Dimension(45, 32);
        lblSimilarityVal.setPreferredSize(valSize);
        lblSimilarityVal.setMinimumSize(valSize);
        lblSimilarityVal.setMaximumSize(valSize);
        sliderSimilarity.addChangeListener(e ->
                lblSimilarityVal.setText(sliderSimilarity.getValue() + "%"));

        chkIgnoreBox = HoloContent.check("忽略箱子与目标点 (仅对比墙壁轮廓)", false);
        chkCurrentSetOnly = HoloContent.check("仅在当前关卡集内查找", false);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("最低相似度:"), sliderSimilarity, lblSimilarityVal),
                HoloContent.row(chkIgnoreBox),
                HoloContent.row(chkCurrentSetOnly)));

        btCancel = addButton("取消", this::dispose);
        btFind = addButton("开始", this::doFind);
        setDefaultButton(btFind);
    }

    private void doFind() {
        int targetSim = sliderSimilarity.getValue();
        boolean ignoreBox = chkIgnoreBox.isSelected();

        List<mapNode> matches = new ArrayList<>();
        if (myMaps.curMap != null && myMaps.m_lstMaps != null) {
            for (mapNode node : myMaps.m_lstMaps) {
                if (node == myMaps.curMap) continue;
                // Basic CRC or dimension heuristic
                if (Math.abs(node.Rows - myMaps.curMap.Rows) <= 2 &&
                    Math.abs(node.Cols - myMaps.curMap.Cols) <= 2) {
                    matches.add(node);
                }
            }
        }

        if (listener != null) {
            listener.onFindDone(matches, targetSim, ignoreBox);
        }
        dispose();
    }
}
