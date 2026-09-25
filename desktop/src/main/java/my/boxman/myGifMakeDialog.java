package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;
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
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code myExport.java:409} 的
 * {@code setTitle("帧间隔")} 取 <b>「帧间隔」</b>，按钮按 {@code myExport.java:422-423} 取
 * <b>取消 / 制作</b>（不是「确定」）。
 *
 * <p>内容按原版 {@code res/layout/gif_set_dialog.xml} 的分组还原：
 * 「12dp {@code #363636} 条 → 一行『其它：』+ 两个复选框 → 12dp 条
 * → 一行『水印：』+ 无/默认/自定义 单选 → 12dp 条」，另外原版用
 * {@code setSingleChoiceItems({"自动","100",...})} 选帧间隔，这里用下拉框等价实现。
 *
 * <p>已知差异：原版第一个复选框是「仅关键帧」（勾选 = 只记关键帧），
 * PC 版是「逐移」（勾选 = 每步都记），语义相反，故保留 PC 文案不强行改字。
 */
public class myGifMakeDialog extends HoloAlertDialog {

    public JComboBox<String> cbInterval;
    public JCheckBox chkMoveByMove;
    public JCheckBox chkSkin;
    public JRadioButton rbMarkNone, rbMarkDefault, rbMarkCustom;
    public JProgressBar progressBar;
    public JTextArea logArea;
    public JButton btMake, btCancel;

    private final String mAns;
    private final int mGifStart;
    private final boolean[] myRule;
    private final short[] myBoxNum;
    private GifWorker worker;

    public myGifMakeDialog(Frame parent, String lurd, int gifStart, boolean[] rule, short[] boxNum) {
        super(parent, "帧间隔");
        this.mAns = lurd != null ? lurd.replaceAll("[^lurdLURD]", "") : "";
        this.mGifStart = gifStart;
        this.myRule = rule;
        this.myBoxNum = boxNum;
        initUI();
    }

    private void initUI() {
        // 原版帧间隔是一个单选列表 {"自动","100","200","300","500","1000","2000"}
        cbInterval = new JComboBox<>(new String[]{"300", "100", "200", "500", "1000", "2000"});
        cbInterval.setSelectedIndex(0);
        cbInterval.setFont(HoloContent.font(Font.PLAIN));
        cbInterval.setBackground(HoloContent.FIELD_BG);
        cbInterval.setForeground(HoloContent.TEXT);
        cbInterval.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? HoloContent.LIST_SELECTED : HoloContent.FIELD_BG);
                c.setForeground(HoloContent.TEXT);
                setFont(HoloContent.font(Font.PLAIN));
                return c;
            }
        });
        Dimension comboSize = new Dimension(120, 28);
        cbInterval.setPreferredSize(comboSize);
        cbInterval.setMinimumSize(comboSize);
        cbInterval.setMaximumSize(comboSize);

        chkMoveByMove = HoloContent.check("逐移 (否则仅记录推箱步骤)", true);
        chkSkin = HoloContent.check("现场皮肤", false);

        rbMarkNone = HoloContent.radio("无", false);
        rbMarkDefault = HoloContent.radio("默认", true);
        rbMarkCustom = HoloContent.radio("自定义", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbMarkNone);
        bg.add(rbMarkDefault);
        bg.add(rbMarkCustom);

        JPanel radios = new JPanel();
        radios.setLayout(new BoxLayout(radios, BoxLayout.X_AXIS));
        radios.setOpaque(false);
        radios.add(rbMarkNone);
        radios.add(rbMarkDefault);
        radios.add(rbMarkCustom);

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
        Dimension logSize = new Dimension(288, 160);
        logScroll.setPreferredSize(logSize);
        logScroll.setMinimumSize(logSize);
        logScroll.setMaximumSize(logSize);
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        setContentView(HoloContent.column(
                HoloContent.band(HoloContent.BAND, 12),
                HoloContent.row(HoloContent.label("其它："), chkMoveByMove,
                        HoloContent.gap(0), chkSkin),
                HoloContent.band(HoloContent.BAND, 12),
                HoloContent.row(HoloContent.label("水印："), radios),
                HoloContent.band(HoloContent.BAND, 12),
                HoloContent.row(HoloContent.label("帧间隔:"), cbInterval),
                HoloContent.row(progressBar),
                HoloContent.gap(6),
                logScroll));

        btCancel = addButton("取消", () -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(true);
            }
            dispose();
        });
        // 原版 myExport.java:423 —— PositiveButton 的文案是「制作」不是「确定」
        // （res/menu/gif.xml 那一项「制作」在原版里从未被 inflate，是孤儿资源；
        //   真正的「制作」是这个对话框的按钮）
        btMake = addButton("制作", this::startMake);
        setDefaultButton(btMake);
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
