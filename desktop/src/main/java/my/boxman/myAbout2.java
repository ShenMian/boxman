package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloContent;
import my.boxman.compat.UiWindow;

public class myAbout2 extends JDialog {

    private final mapNode mNode;
    private JTextField et_Title;
    private JTextField et_Author;
    private JTextArea et_Comment;
    private JLabel tv_Count;
    private JLabel lblThumbnail;

    public myAbout2(Frame owner, mapNode node) {
        super(owner, (node != null && node.Title != null ? node.Title : "关卡") + " - 详细", true);
        this.mNode = node;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // 原版 about.xml 没有 android:background → 透出 Theme.Holo 的 windowBackground（纯黑）
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(HoloContent.WINDOW_BG);
        content.setOpaque(true);
        content.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 标题
        JPanel pTitle = new JPanel(new BorderLayout(5, 5));
        pTitle.setOpaque(false);
        pTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblTitle = HoloContent.label("标题:");
        lblTitle.setPreferredSize(new Dimension(50, 25));
        et_Title = HoloContent.darkField(mNode != null && mNode.Title != null ? mNode.Title : "");
        pTitle.add(lblTitle, BorderLayout.WEST);
        pTitle.add(et_Title, BorderLayout.CENTER);
        content.add(pTitle);
        content.add(Box.createVerticalStrut(8));

        // 作者
        JPanel pAuthor = new JPanel(new BorderLayout(5, 5));
        pAuthor.setOpaque(false);
        pAuthor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblAuthor = HoloContent.label("作者:");
        lblAuthor.setPreferredSize(new Dimension(50, 25));
        et_Author = HoloContent.darkField(mNode != null && mNode.Author != null ? mNode.Author : "");
        pAuthor.add(lblAuthor, BorderLayout.WEST);
        pAuthor.add(et_Author, BorderLayout.CENTER);
        content.add(pAuthor);
        content.add(Box.createVerticalStrut(8));

        // 说明 / 注释
        JPanel pComment = new JPanel(new BorderLayout(5, 5));
        pComment.setOpaque(false);
        JLabel lblComment = HoloContent.label("说明:");
        lblComment.setPreferredSize(new Dimension(50, 25));
        et_Comment = HoloContent.textArea(mNode != null && mNode.Comment != null ? mNode.Comment : "", 13, true);
        JScrollPane spComment = HoloContent.windowScroll(et_Comment);
        spComment.setPreferredSize(new Dimension(400, 140));
        pComment.add(lblComment, BorderLayout.NORTH);
        pComment.add(spComment, BorderLayout.CENTER);
        content.add(pComment);
        content.add(Box.createVerticalStrut(8));

        // 关卡信息统计与缩略图
        JPanel pInfo = new JPanel(new BorderLayout(10, 5));
        pInfo.setOpaque(false);
        String infoStr = mNode != null ? "尺寸: " + mNode.Rows + " 行 × " + mNode.Cols + " 列" : "";
        tv_Count = HoloContent.label(infoStr);
        pInfo.add(tv_Count, BorderLayout.NORTH);

        if (mNode != null && mNode.Map != null && !mNode.Map.equals("--")) {
            BufferedImage thumb = createPreview(mNode, 120);
            if (thumb != null) {
                lblThumbnail = new JLabel(new ImageIcon(thumb));
                lblThumbnail.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                JPanel pCenter = new JPanel(new FlowLayout(FlowLayout.CENTER));
                pCenter.setOpaque(false);
                pCenter.add(lblThumbnail);
                pInfo.add(pCenter, BorderLayout.CENTER);
            }
        }
        content.add(pInfo);

        add(content, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(HoloContent.WINDOW_BG);
        bottom.setOpaque(true);
        JButton btnSave = new HoloButton("保存");
        JButton btnClose = new HoloButton("关闭");

        btnSave.addActionListener(e -> {
            saveInfo();
            dispose();
        });
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnSave);
        bottom.add(btnClose);
        add(bottom, BorderLayout.SOUTH);

        // 原版 Theme.Holo 深色 windowBackground（纯黑）——见 HoloContent.WINDOW_BG
        HoloContent.applyWindowBackground(this);

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(getOwner());
    }

    private void saveInfo() {
        if (mNode != null) {
            mNode.Title = et_Title.getText().trim();
            mNode.Author = et_Author.getText().trim();
            mNode.Comment = et_Comment.getText().trim();
            if (mySQLite.m_SQL != null && mNode.Level_id > 0) {
                mySQLite.m_SQL.Update_L_inf(mNode.Level_id, mNode.Title, mNode.Author, mNode.Comment);
            }
        }
    }

    private BufferedImage createPreview(mapNode nd, int targetSize) {
        try {
            String[] arr = nd.Map.split("\r\n|\n\r|\n|\\|");
            int rows = nd.Rows;
            int cols = nd.Cols;
            if (rows <= 0 || cols <= 0) return null;

            int tileSize = 20;
            BufferedImage full = new BufferedImage(cols * tileSize, rows * tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = full.createGraphics();

            for (int r = 0; r < rows; r++) {
                if (r >= arr.length) break;
                for (int c = 0; c < cols; c++) {
                    if (c >= arr[r].length()) break;
                    char ch = arr[r].charAt(c);
                    int x = c * tileSize;
                    int y = r * tileSize;
                    drawTile(g, ch, x, y, tileSize);
                }
            }
            g.dispose();

            double scale = Math.min((double) targetSize / full.getWidth(), (double) targetSize / full.getHeight());
            int sw = Math.max(1, (int) (full.getWidth() * scale));
            int sh = Math.max(1, (int) (full.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.drawImage(full, 0, 0, sw, sh, null);
            sg.dispose();
            return scaled;
        } catch (Throwable t) {
            return null;
        }
    }

    private void drawTile(Graphics2D g, char ch, int x, int y, int size) {
        switch (ch) {
            case '#':
                if (myMaps.WallPic != null) g.drawImage(myMaps.WallPic, x, y, size, size, null);
                else { g.setColor(Color.DARK_GRAY); g.fillRect(x, y, size, size); }
                break;
            case '-':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                break;
            case '.':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                break;
            case '$':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxPic != null) g.drawImage(myMaps.BoxPic, x, y, size, size, null);
                break;
            case '*':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.BoxGoalPic != null) g.drawImage(myMaps.BoxGoalPic, x, y, size, size, null);
                break;
            case '@':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.ManPic_d != null) g.drawImage(myMaps.ManPic_d, x, y, size, size, null);
                break;
            case '+':
                if (myMaps.FloorPic != null) g.drawImage(myMaps.FloorPic, x, y, size, size, null);
                if (myMaps.GoalPic != null) g.drawImage(myMaps.GoalPic, x, y, size, size, null);
                if (myMaps.ManGoalPic_d != null) g.drawImage(myMaps.ManGoalPic_d, x, y, size, size, null);
                break;
        }
    }
}
