package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch Export Level Sets Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code BoxMan.java:1193} 的
 * {@code setTitle("导出")} 取 <b>「导出」</b>，按钮 取消 / 确定。
 *
 * <p>原版用 {@code export2_dialog.xml} / {@code export_dialog3.xml} 选择关卡集与导出选项；
 * PC 版在此之上加了进度条与日志区，属于实现差异，这里保留其功能与公开字段，
 * 只统一到 Holo 深色外观。
 */
public class ExportDialog extends HoloAlertDialog {

    public interface ExportCallback {
        void onExportComplete(String message);
    }

    public JList<String> listSets;
    public DefaultListModel<String> modelSets;
    public JCheckBox chkIncludeAns;
    public JCheckBox chkOverwrite;
    public JProgressBar progressBar;
    public JTextArea logArea;
    public JButton btStart, btCancel;

    private final List<set_Node> availableSets = new ArrayList<>();
    private ExportWorker worker;
    private final ExportCallback callback;

    public ExportDialog(Frame parent, ExportCallback callback) {
        super(parent, "导出");
        this.callback = callback;
        initUI();
        loadSets();
    }

    private void initUI() {
        modelSets = new DefaultListModel<>();
        listSets = new JList<>(modelSets);
        listSets.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        listSets.setFont(HoloContent.font(Font.PLAIN));
        listSets.setBackground(HoloContent.BAND);
        listSets.setForeground(HoloContent.TEXT);
        listSets.setSelectionBackground(HoloContent.LIST_SELECTED);
        listSets.setSelectionForeground(HoloContent.TEXT);
        listSets.setBorder(new javax.swing.border.EmptyBorder(
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));

        JScrollPane listScroll = HoloContent.scroll(listSets);
        Dimension listSize = new Dimension(288, 150);
        listScroll.setPreferredSize(listSize);
        listScroll.setMinimumSize(listSize);
        listScroll.setMaximumSize(listSize);
        listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        chkIncludeAns = HoloContent.check("包含关卡解答 (Solution)", true);
        chkOverwrite = HoloContent.check("覆盖已存在同名文档", true);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(HoloContent.font(Font.PLAIN));
        progressBar.setBackground(HoloContent.FIELD_BG);
        progressBar.setForeground(HoloContent.HOLO_BLUE);
        progressBar.setBorderPainted(false);
        Dimension barSize = new Dimension(288, 24);
        progressBar.setPreferredSize(barSize);
        progressBar.setMinimumSize(barSize);
        progressBar.setMaximumSize(barSize);

        logArea = new JTextArea(6, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(HoloContent.FIELD_BG);
        logArea.setForeground(HoloContent.TEXT);
        logArea.setCaretColor(HoloContent.TEXT);
        logArea.setBorder(new javax.swing.border.EmptyBorder(
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        JScrollPane logScroll = HoloContent.scroll(logArea);
        logScroll.getViewport().setBackground(HoloContent.FIELD_BG);
        Dimension logSize = new Dimension(288, 110);
        logScroll.setPreferredSize(logSize);
        logScroll.setMinimumSize(logSize);
        logScroll.setMaximumSize(logSize);
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("请选择要导出的关卡集 (可按 Ctrl/Shift 多选):")),
                listScroll,
                HoloContent.row(chkIncludeAns),
                HoloContent.row(chkOverwrite),
                HoloContent.row(progressBar),
                logScroll));

        btCancel = addButton("取消", () -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true);
            }
            dispose();
        });
        btStart = addButton("确定", this::startExport);
        setDefaultButton(btStart);
    }

    private void loadSets() {
        modelSets.clear();
        availableSets.clear();
        if (mySQLite.m_SQL != null) {
            // Load from mSets0 ~ mSets3
            ArrayList[] all = {myMaps.mSets0, myMaps.mSets1, myMaps.mSets2, myMaps.mSets3};
            for (ArrayList arr : all) {
                if (arr != null) {
                    for (Object obj : arr) {
                        if (obj instanceof set_Node) {
                            set_Node sn = (set_Node) obj;
                            availableSets.add(sn);
                            modelSets.addElement(sn.title);
                        }
                    }
                }
            }
        }
        if (modelSets.size() > 0) {
            listSets.setSelectedIndex(0);
        }
    }

    public void startExport() {
        int[] indices = listSets.getSelectedIndices();
        if (indices.length == 0) {
            logArea.append("请先选择至少一个要导出的关卡集！\n");
            return;
        }

        btStart.setEnabled(false);
        progressBar.setIndeterminate(true);
        worker = new ExportWorker(indices);
        worker.execute();
    }

    public class ExportWorker extends SwingWorker<String, String> {
        private int[] selectedIndices;

        public ExportWorker(int[] indices) {
            this.selectedIndices = indices;
        }

        @Override
        protected String doInBackground() throws Exception {
            publish("开始批量导出任务...\n");

            File dir = new File(myMaps.sRoot + myMaps.sPath + "导出/");
            if (!dir.exists()) dir.mkdirs();

            int totalSets = selectedIndices.length;
            int count = 0;

            for (int idx : selectedIndices) {
                if (isCancelled()) break;
                set_Node sn = availableSets.get(idx);
                publish("正在导出关卡集: " + sn.title + " ...\n");

                String ext = chkIncludeAns.isSelected() ? ".txt" : ".xsb";
                File target = new File(dir, sn.title + ext);

                if (target.exists() && !chkOverwrite.isSelected()) {
                    publish("文件已存在且未勾选覆盖，跳过: " + target.getName() + "\n");
                    continue;
                }

                // Query and write levels
                StringBuilder sb = new StringBuilder();
                sb.append("Title: ").append(sn.title).append("\n\n");

                if (mySQLite.m_SQL != null) {
                    mySQLite.m_SQL.get_Levels(sn.id);
                    if (myMaps.m_lstMaps != null) {
                        for (int i = 0; i < myMaps.m_lstMaps.size(); i++) {
                            mapNode node = myMaps.m_lstMaps.get(i);
                            sb.append("; Level ").append(i + 1).append("\n");
                            if (node.Title != null && !node.Title.isEmpty()) {
                                sb.append("Title: ").append(node.Title).append("\n");
                            }
                            if (node.Author != null && !node.Author.isEmpty()) {
                                sb.append("Author: ").append(node.Author).append("\n");
                            }
                            if (node.Map != null) {
                                sb.append(node.Map).append("\n\n");
                            }
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(target)) {
                    fos.write(sb.toString().getBytes("UTF-8"));
                }

                count++;
                publish("成功导出: " + target.getName() + "\n");
            }

            return "批量导出完成，共成功导出 " + count + " 个关卡集。";
        }

        @Override
        protected void process(List<String> chunks) {
            for (String s : chunks) {
                logArea.append(s);
            }
        }

        @Override
        protected void done() {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            try {
                String msg = get();
                logArea.append(msg + "\n");
                if (callback != null) {
                    callback.onExportComplete(msg);
                }
            } catch (Exception ignored) {
                logArea.append("导出中断或发生异常。\n");
            }
            btCancel.setText("完成");
        }
    }
}
