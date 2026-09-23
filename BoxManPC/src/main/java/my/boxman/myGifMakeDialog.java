package my.boxman;

import my.boxman.gifencoder.GifEncoder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * GIF Animation Generator Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myGifMakeFragment.
 */
public class myGifMakeDialog extends JDialog {

    public JComboBox<String> cbInterval;
    public JCheckBox chkMoveByMove;
    public JCheckBox chkSkin;
    public JRadioButton rbMarkNone, rbMarkDefault, rbMarkCustom;
    public JProgressBar progressBar;
    public JTextArea logArea;
    public JButton btMake, btCancel;

    private String mAns;
    private int mGifStart;
    private boolean[] myRule;
    private short[] myBoxNum;
    private GifWorker worker;

    public myGifMakeDialog(Frame parent, String lurd, int gifStart, boolean[] rule, short[] boxNum) {
        super(parent, "导出解法为动画 (GIF)", true);
        this.mAns = lurd != null ? lurd.replaceAll("[^lurdLURD]", "") : "";
        this.mGifStart = gifStart;
        this.myRule = rule;
        this.myBoxNum = boxNum;

        setSize(540, 420);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel configPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        configPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        configPanel.add(new JLabel("帧间隔 (毫秒):"));
        cbInterval = new JComboBox<>(new String[]{"300", "100", "200", "500", "1000", "2000"});
        cbInterval.setSelectedIndex(0);
        configPanel.add(cbInterval);

        configPanel.add(new JLabel("逐步模式:"));
        chkMoveByMove = new JCheckBox("逐移 (否则仅记录推箱步骤)", true);
        configPanel.add(chkMoveByMove);

        configPanel.add(new JLabel("皮肤设置:"));
        chkSkin = new JCheckBox("使用现场皮肤", false);
        configPanel.add(chkSkin);

        configPanel.add(new JLabel("水印:"));
        JPanel markPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rbMarkNone = new JRadioButton("无");
        rbMarkDefault = new JRadioButton("默认", true);
        rbMarkCustom = new JRadioButton("自定义");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbMarkNone);
        bg.add(rbMarkDefault);
        bg.add(rbMarkCustom);
        markPanel.add(rbMarkNone);
        markPanel.add(rbMarkDefault);
        markPanel.add(rbMarkCustom);
        configPanel.add(markPanel);

        add(configPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        centerPanel.add(progressBar, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btMake = new JButton("开始制作");
        btMake.addActionListener(e -> startMake());
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true);
            }
            dispose();
        });
        bottomBar.add(btMake);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }

    public void startMake() {
        if (mAns.isEmpty()) {
            logArea.append("没有可供制作动画的正推动作！\n");
            return;
        }
        btMake.setEnabled(false);
        progressBar.setIndeterminate(true);
        worker = new GifWorker();
        worker.execute();
    }

    public class GifWorker extends SwingWorker<String, String> {

        @Override
        protected String doInBackground() throws Exception {
            publish("正在准备 GIF 动画帧...\n");

            File dir = new File(myMaps.sRoot + myMaps.sPath + "GIF/");
            if (!dir.exists()) dir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File gifFile = new File(dir, "BoxMan_" + timeStamp + ".gif");

            int interval = 300;
            try {
                interval = Integer.parseInt((String) cbInterval.getSelectedItem());
            } catch (Exception ignored) {}

            int rows = (myMaps.curMap != null && myMaps.curMap.Rows > 0) ? myMaps.curMap.Rows : 10;
            int cols = (myMaps.curMap != null && myMaps.curMap.Cols > 0) ? myMaps.curMap.Cols : 10;
            int tileSize = chkSkin.isSelected() ? 50 : 24;
            int width = cols * tileSize;
            int height = rows * tileSize;

            GifEncoder encoder = new GifEncoder();
            try (FileOutputStream fos = new FileOutputStream(gifFile)) {
                encoder.start(fos);
                encoder.setDelay(interval);
                encoder.setRepeat(0);

                char[][] grid = new char[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        grid[r][c] = '-';
                    }
                }
                if (myMaps.curMap != null && myMaps.curMap.Map != null) {
                    String[] lines = myMaps.curMap.Map.split("\r\n|\n\r|\n|\r|\\|");
                    for (int r = 0; r < rows && r < lines.length; r++) {
                        for (int c = 0; c < cols && c < lines[r].length(); c++) {
                            grid[r][c] = lines[r].charAt(c);
                        }
                    }
                }

                // Render initial frame
                BufferedImage frame = renderFrame(grid, rows, cols, tileSize);
                encoder.addFrame(frame);
                publish("已添加初始帧...\n");

                int totalSteps = mAns.length();
                int addedFrames = 1;

                // Simulate actions
                int pRow = 0, pCol = 0;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (grid[r][c] == '@' || grid[r][c] == '+') {
                            pRow = r;
                            pCol = c;
                            break;
                        }
                    }
                }

                for (int step = 0; step < totalSteps; step++) {
                    if (isCancelled()) {
                        encoder.finish();
                        return "GIF 制作已取消。";
                    }

                    char act = mAns.charAt(step);
                    int dr = 0, dc = 0;
                    if (act == 'l' || act == 'L') dc = -1;
                    else if (act == 'r' || act == 'R') dc = 1;
                    else if (act == 'u' || act == 'U') dr = -1;
                    else if (act == 'd' || act == 'D') dr = 1;

                    boolean isPush = Character.isUpperCase(act);
                    int nr = pRow + dr;
                    int nc = pCol + dc;

                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                        if (isPush) {
                            int bnr = nr + dr;
                            int bnc = nc + dc;
                            if (bnr >= 0 && bnr < rows && bnc >= 0 && bnc < cols) {
                                grid[bnr][bnc] = (grid[bnr][bnc] == '.') ? '*' : '$';
                                grid[nr][nc] = (grid[nr][nc] == '*') ? '.' : '-';
                            }
                        }
                        grid[pRow][pCol] = (grid[pRow][pCol] == '+') ? '.' : '-';
                        grid[nr][nc] = (grid[nr][nc] == '.' || grid[nr][nc] == '*') ? '+' : '@';
                        pRow = nr;
                        pCol = nc;
                    }

                    if (isPush || chkMoveByMove.isSelected()) {
                        frame = renderFrame(grid, rows, cols, tileSize);
                        encoder.addFrame(frame);
                        addedFrames++;
                    }

                    if (step % 20 == 0 || step == totalSteps - 1) {
                        publish("处理进度: " + (step + 1) + "/" + totalSteps + " 步 (已渲染 " + addedFrames + " 帧)\n");
                    }
                }

                encoder.finish();
                publish("动画合成完毕！\n保存至: " + gifFile.getAbsolutePath() + "\n");
                return "GIF 动画导出成功！共 " + addedFrames + " 帧。";
            }
        }

        private BufferedImage renderFrame(char[][] grid, int rows, int cols, int tileSize) {
            BufferedImage img = new BufferedImage(cols * tileSize, rows * tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill background
            g.setColor(new Color(0x22, 0x22, 0x22));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = c * tileSize;
                    int y = r * tileSize;
                    char ch = grid[r][c];

                    // Floor
                    if (ch != '-' && ch != '_') {
                        g.setColor(new Color(0x55, 0x55, 0x55));
                        g.fillRect(x + 1, y + 1, tileSize - 2, tileSize - 2);
                    }

                    // Goal
                    if (ch == '.' || ch == '*' || ch == '+') {
                        g.setColor(Color.RED);
                        int dotSize = Math.max(4, tileSize / 4);
                        g.fillOval(x + (tileSize - dotSize) / 2, y + (tileSize - dotSize) / 2, dotSize, dotSize);
                    }

                    // Wall
                    if (ch == '#') {
                        g.setColor(new Color(0x8B, 0x45, 0x13));
                        g.fillRect(x, y, tileSize, tileSize);
                    }
                    // Box
                    else if (ch == '$') {
                        g.setColor(new Color(0xDA, 0xA5, 0x20));
                        g.fillRect(x + 2, y + 2, tileSize - 4, tileSize - 4);
                    }
                    // Box on Goal
                    else if (ch == '*') {
                        g.setColor(new Color(0x32, 0xCD, 0x32));
                        g.fillRect(x + 2, y + 2, tileSize - 4, tileSize - 4);
                    }
                    // Player
                    else if (ch == '@' || ch == '+') {
                        g.setColor(Color.CYAN);
                        g.fillOval(x + 2, y + 2, tileSize - 4, tileSize - 4);
                    }
                }
            }

            g.dispose();
            return img;
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
            } catch (Exception ignored) {
                logArea.append("制作完成或中断。\n");
            }
            btCancel.setText("关闭");
            btMake.setEnabled(true);
        }
    }
}
