package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Web Level URL Input and Download Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's get_uil_dialog.xml.
 */
public class UrlInputDialog extends JDialog {

    public interface UrlDownloadListener {
        void onDownloaded(String content);
    }

    public JTextField tfUrl;
    public JButton btDownload, btCancel;
    public JProgressBar progressBar;

    private UrlDownloadListener listener;

    public UrlInputDialog(Frame parent, UrlDownloadListener listener) {
        super(parent, "从网络导入关卡", true);
        this.listener = listener;

        setSize(460, 200);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(2, 1, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        form.add(new JLabel("请输入关卡文件的网络 URL 地址 (HTTP / HTTPS):"));
        tfUrl = new JTextField("http://");
        form.add(tfUrl);
        add(form, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        centerPanel.add(progressBar, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btDownload = new JButton("下载导入");
        btDownload.addActionListener(e -> startDownload());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());
        bottomBar.add(btDownload);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
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
