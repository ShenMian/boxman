package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Level Query Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code BoxMan.java:527} 的
 * {@code setTitle("关卡查询")} 取 <b>「关卡查询」</b>，按钮 取消 / 确定。
 *
 * <p>PC 版的表单项（标题/作者/行数/列数/箱子数/已解）比原版 {@code query_dialog.xml}
 * 的字段多，功能是原版的超集；这里保留其功能与公开字段，只统一到 Holo 深色外观。
 */
public class QueryDialog extends HoloAlertDialog {

    public interface QueryResultListener {
        void onQueryDone(List<mapNode> results);
    }

    public JTextField tfTitle;
    public JTextField tfAuthor;
    public JSpinner spRowsMin, spRowsMax;
    public JSpinner spColsMin, spColsMax;
    public JSpinner spBoxesMin, spBoxesMax;
    public JCheckBox chkSolvedOnly;
    public JButton btSearch, btCancel;

    private final QueryResultListener listener;

    public QueryDialog(Frame parent, QueryResultListener listener) {
        super(parent, "关卡查询");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        tfTitle = HoloContent.field(160, "");
        tfAuthor = HoloContent.field(160, "");

        spRowsMin = HoloContent.spinner(72, 0, 0, 100);
        spRowsMax = HoloContent.spinner(72, 100, 0, 100);
        spColsMin = HoloContent.spinner(72, 0, 0, 100);
        spColsMax = HoloContent.spinner(72, 100, 0, 100);
        spBoxesMin = HoloContent.spinner(72, 0, 0, 100);
        spBoxesMax = HoloContent.spinner(72, 100, 0, 100);

        chkSolvedOnly = HoloContent.check("仅查询已解关卡", false);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("标题:"), tfTitle),
                HoloContent.row(HoloContent.label("作者:"), tfAuthor),
                HoloContent.row(HoloContent.label("行数:"),
                        HoloContent.pair(spRowsMin, HoloContent.label(" ~ "), spRowsMax)),
                HoloContent.row(HoloContent.label("列数:"),
                        HoloContent.pair(spColsMin, HoloContent.label(" ~ "), spColsMax)),
                HoloContent.row(HoloContent.label("箱子:"),
                        HoloContent.pair(spBoxesMin, HoloContent.label(" ~ "), spBoxesMax)),
                HoloContent.row(chkSolvedOnly)));

        btCancel = addButton("取消", this::dispose);
        btSearch = addButton("确定", this::doSearch);
        setDefaultButton(btSearch);
    }

    private void doSearch() {
        String titleKey = tfTitle.getText().trim().toLowerCase();
        String authorKey = tfAuthor.getText().trim().toLowerCase();
        int rMin = (Integer) spRowsMin.getValue();
        int rMax = (Integer) spRowsMax.getValue();
        int cMin = (Integer) spColsMin.getValue();
        int cMax = (Integer) spColsMax.getValue();
        boolean solvedOnly = chkSolvedOnly.isSelected();

        List<mapNode> matches = new ArrayList<>();
        if (myMaps.m_lstMaps != null) {
            for (mapNode node : myMaps.m_lstMaps) {
                if (!titleKey.isEmpty()
                        && (node.Title == null || !node.Title.toLowerCase().contains(titleKey))) {
                    continue;
                }
                if (!authorKey.isEmpty()
                        && (node.Author == null || !node.Author.toLowerCase().contains(authorKey))) {
                    continue;
                }
                if (node.Rows < rMin || node.Rows > rMax) continue;
                if (node.Cols < cMin || node.Cols > cMax) continue;
                if (solvedOnly && !node.Solved) continue;

                matches.add(node);
            }
        }

        if (listener != null) {
            listener.onQueryDone(matches);
        }
        dispose();
    }
}
