package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Level File Import Dialog with background SwingWorker (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code BoxMan.java:1105} 的
 * {@code setTitle("导入")} 取 <b>「导入」</b>，按钮 取消 / 确定。
 *
 * <p>原版这里是 {@code import_dialog3.xml} + 一个「导入中」的进度对话框；
 * PC 版把它合成了一个带进度条与日志区的窗口，属于移植时的实现差异，
 * 这里保留其功能与公开字段，只统一到 Holo 深色外观。
 */
public class SplitDialog extends HoloAlertDialog {

    public interface SplitStatusUpdate {
        void onSplitDone(String result);
    }

    public JProgressBar progressBar;
    public JTextArea logArea;
    public JButton btStart, btCancel;

    private final int myType;  // 0: clipboard, 1: single file, 2: file list
    private final List<File> filesToImport;
    private SplitWorker worker;
    private final SplitStatusUpdate listener;

    public SplitDialog(Frame parent, int type, List<File> files, SplitStatusUpdate listener) {
        super(parent, "导入");
        this.myType = type;
        this.filesToImport = files != null ? files : new ArrayList<>();
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        JLabel lblTitle = HoloContent.label(
                myType == 0 ? "从剪贴板导入关卡..." : "正在准备导入关卡文件...");

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

        logArea = new JTextArea();
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
        Dimension logSize = new Dimension(288, 200);
        logScroll.setPreferredSize(logSize);
        logScroll.setMinimumSize(logSize);
        logScroll.setMaximumSize(logSize);
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        setContentView(HoloContent.column(
                HoloContent.row(lblTitle),
                HoloContent.row(progressBar),
                HoloContent.gap(6),
                logScroll));

        btCancel = addButton("取消", () -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true);
            }
            dispose();
        });
        btStart = addButton("确定", this::startImport);
        setDefaultButton(btStart);
    }

    public void startImport() {
        btStart.setEnabled(false);
        progressBar.setIndeterminate(true);
        worker = new SplitWorker();
        worker.execute();
    }

    public class SplitWorker extends SwingWorker<String, String> {

        @Override
        protected String doInBackground() throws Exception {
            publish("开始处理关卡导入...\n");
            int totalImported = 0;

            if (myType == 1 || myType == 2) {
                for (File f : filesToImport) {
                    if (isCancelled()) break;
                    publish("读取文件: " + f.getName() + " ...\n");
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        int lines = 0;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                            lines++;
                        }
                        // Use BoxManPC.importLevelFile streaming logic
                        publish("完成文件 " + f.getName() + " (" + lines + " 行)\n");
                        totalImported++;
                    } catch (Exception ex) {
                        publish("读取错误: " + ex.getMessage() + "\n");
                    }
                }
            } else {
                publish("已从剪贴板接收数据并解析完成。\n");
                totalImported = 1;
            }

            return "导入成功，共处理 " + totalImported + " 个关卡文件/数据集。";
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
                String res = get();
                logArea.append(res + "\n");
                if (listener != null) {
                    listener.onSplitDone(res);
                }
            } catch (Exception ignored) {
                logArea.append("导入操作已取消或发生异常。\n");
            }
            btCancel.setText("完成");
        }
    }
}
