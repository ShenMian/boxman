package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Web Level URL Input and Download Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code BoxMan.java:1645} 的
 * {@code setTitle("导入比赛关卡")} 取 <b>「导入比赛关卡」</b>，按钮 取消 / 确定
 * （PC 原来写的是「下载导入」）。
 *
 * <p>原版 {@code res/layout/get_uil_dialog.xml} 是一个 URL 输入框；PC 版额外加了一条
 * 下载进度条，属于新增功能，这里保留并统一到 Holo 深色配色。
 */
public class UrlInputDialog extends HoloAlertDialog {

    public interface UrlDownloadListener {
        void onDownloaded(String content);
    }

    public JTextField tfUrl;
    public JButton btDownload, btCancel;
    public JProgressBar progressBar;

    private final UrlDownloadListener listener;

    public UrlInputDialog(Frame parent, UrlDownloadListener listener) {
        super(parent, "导入比赛关卡");
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        tfUrl = HoloContent.field(288, "http://");

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");
        progressBar.setFont(HoloContent.font(Font.PLAIN));
        progressBar.setBackground(HoloContent.FIELD_BG);
        progressBar.setForeground(HoloContent.HOLO_BLUE);
        progressBar.setBorderPainted(false);
        Dimension barSize = new Dimension(288, 24);
        progressBar.setPreferredSize(barSize);
        progressBar.setMinimumSize(barSize);
        progressBar.setMaximumSize(barSize);

        setContentView(HoloContent.column(
                HoloContent.row(HoloContent.label("URL:")),
                HoloContent.row(tfUrl),
                HoloContent.row(progressBar)));

        btCancel = addButton("取消", this::dispose);
        btDownload = addButton("确定", this::startDownload);
        setDefaultButton(btDownload);
    }

    public void startDownload() {
        String urlStr = tfUrl.getText().trim();
        if (urlStr.isEmpty() || urlStr.equals("http://") || urlStr.equals("https://")) {
            JOptionPane.showMessageDialog(this, "请输入有效的网络 URL！");
            return;
        }

        btDownload.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在下载...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                try {
                    String result = get();
                    progressBar.setValue(100);
                    progressBar.setString("下载成功");
                    if (listener != null) {
                        listener.onDownloaded(result);
                    }
                    dispose();
                } catch (Exception ex) {
                    progressBar.setString("下载失败");
                    JOptionPane.showMessageDialog(UrlInputDialog.this, "下载失败: " + ex.getMessage());
                    btDownload.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
