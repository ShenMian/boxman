package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

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
        setSize(520, 520);
        setMinimumSize(new Dimension(450, 420));
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 标题
        JPanel pTitle = new JPanel(new BorderLayout(5, 5));
        pTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblTitle = new JLabel("标题:");
        lblTitle.setPreferredSize(new Dimension(50, 25));
        et_Title = new JTextField(mNode != null && mNode.Title != null ? mNode.Title : "");
        pTitle.add(lblTitle, BorderLayout.WEST);
        pTitle.add(et_Title, BorderLayout.CENTER);
        content.add(pTitle);
        content.add(Box.createVerticalStrut(8));

        // 作者
        JPanel pAuthor = new JPanel(new BorderLayout(5, 5));
        pAuthor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblAuthor = new JLabel("作者:");
        lblAuthor.setPreferredSize(new Dimension(50, 25));
        et_Author = new JTextField(mNode != null && mNode.Author != null ? mNode.Author : "");
        pAuthor.add(lblAuthor, BorderLayout.WEST);
        pAuthor.add(et_Author, BorderLayout.CENTER);
        content.add(pAuthor);
        content.add(Box.createVerticalStrut(8));

        // 说明 / 注释
        JPanel pComment = new JPanel(new BorderLayout(5, 5));
        JLabel lblComment = new JLabel("说明:");
        lblComment.setPreferredSize(new Dimension(50, 25));
        et_Comment = new JTextArea(mNode != null && mNode.Comment != null ? mNode.Comment : "");
        et_Comment.setLineWrap(true);
        et_Comment.setWrapStyleWord(true);
        et_Comment.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane spComment = new JScrollPane(et_Comment);
        spComment.setPreferredSize(new Dimension(400, 140));
        pComment.add(lblComment, BorderLayout.NORTH);
        pComment.add(spComment, BorderLayout.CENTER);
        content.add(pComment);
        content.add(Box.createVerticalStrut(8));

        // 关卡信息统计与缩略图
        JPanel pInfo = new JPanel(new BorderLayout(10, 5));
        String infoStr = mNode != null ? "尺寸: " + mNode.Rows + " 行 × " + mNode.Cols + " 列" : "";
        tv_Count = new JLabel(infoStr);
        pInfo.add(tv_Count, BorderLayout.NORTH);

        if (mNode != null && mNode.Map != null && !mNode.Map.equals("--")) {
            BufferedImage thumb = createPreview(mNode, 120);
            if (thumb != null) {
                lblThumbnail = new JLabel(new ImageIcon(thumb));
                lblThumbnail.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                JPanel pCenter = new JPanel(new FlowLayout(FlowLayout.CENTER));
                pCenter.add(lblThumbnail);
                pInfo.add(pCenter, BorderLayout.CENTER);
            }
        }
        content.add(pInfo);

        add(content, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("保存");
        JButton btnClose = new JButton("关闭");

        btnSave.addActionListener(e -> {
            saveInfo();
            dispose();
        });
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnSave);
        bottom.add(btnClose);
        add(bottom, BorderLayout.SOUTH);
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
