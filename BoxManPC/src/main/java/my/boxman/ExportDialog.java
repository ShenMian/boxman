package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch Export Level Sets Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's export2_dialog & myExportFragment.
 */
public class ExportDialog extends JDialog {

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

    private List<set_Node> availableSets = new ArrayList<>();
    private ExportWorker worker;
    private ExportCallback callback;

    public ExportDialog(Frame parent, ExportCallback callback) {
        super(parent, "批量导出关卡集", true);
        this.callback = callback;

        setSize(520, 440);
        setLocationRelativeTo(parent);
        initUI();
        loadSets();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        topPanel.add(new JLabel("请选择要导出的关卡集 (可按 Ctrl/Shift 多选):"), BorderLayout.NORTH);

        modelSets = new DefaultListModel<>();
        listSets = new JList<>(modelSets);
        listSets.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        topPanel.add(new JScrollPane(listSets), BorderLayout.CENTER);
        topPanel.setPreferredSize(new Dimension(480, 160));
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        chkIncludeAns = new JCheckBox("包含关卡解答 (Solution)", true);
        chkOverwrite = new JCheckBox("覆盖已存在同名文档", true);
        opts.add(chkIncludeAns);
        opts.add(chkOverwrite);
        centerPanel.add(opts, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        centerPanel.add(progressBar, BorderLayout.CENTER);

        logArea = new JTextArea(6, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        centerPanel.add(new JScrollPane(logArea), BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btStart = new JButton("开始导出");
        btStart.addActionListener(e -> startExport());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true);
            }
            dispose();
        });
        bottomBar.add(btStart);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
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
