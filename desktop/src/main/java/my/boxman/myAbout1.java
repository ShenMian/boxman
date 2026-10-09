package my.boxman;

import javax.swing.*;
import java.awt.*;
import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloContent;
import my.boxman.compat.UiWindow;

public class myAbout1 extends JDialog {

    private final long m_id;
    private JTextField et_Title;
    private JTextField et_Author;
    private JTextArea et_Comment;
    private JLabel tv_Count;
    private boolean isModified = false;

    public myAbout1(Frame owner, long setId, String message) {
        super(owner, (myMaps.sFile != null ? myMaps.sFile : "关卡集") + " - 关于", true);
        this.m_id = setId;
        initUI(message);
    }

    private void initUI(String message) {
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
        et_Title = HoloContent.darkField(myMaps.J_Title != null ? myMaps.J_Title : "");
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
        et_Author = HoloContent.darkField(myMaps.J_Author != null ? myMaps.J_Author : "");
        pAuthor.add(lblAuthor, BorderLayout.WEST);
        pAuthor.add(et_Author, BorderLayout.CENTER);
        content.add(pAuthor);
        content.add(Box.createVerticalStrut(8));

        // 说明 / 注释
        JPanel pComment = new JPanel(new BorderLayout(5, 5));
        pComment.setOpaque(false);
        JLabel lblComment = HoloContent.label("说明:");
        lblComment.setPreferredSize(new Dimension(50, 25));
        et_Comment = HoloContent.textArea(myMaps.J_Comment != null ? myMaps.J_Comment : "", 13, true);
        JScrollPane spComment = HoloContent.windowScroll(et_Comment);
        spComment.setPreferredSize(new Dimension(400, 180));
        pComment.add(lblComment, BorderLayout.NORTH);
        pComment.add(spComment, BorderLayout.CENTER);
        content.add(pComment);
        content.add(Box.createVerticalStrut(8));

        // 关卡数量统计
        tv_Count = HoloContent.label("关卡数量：" + (message != null ? message : ""));
        tv_Count.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(tv_Count);

        add(content, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(HoloContent.WINDOW_BG);
        bottom.setOpaque(true);
        JButton btnSave = new HoloButton("保存");
        JButton btnCancel = new HoloButton("取消");

        btnSave.addActionListener(e -> {
            saveInfo();
            dispose();
        });
        btnCancel.addActionListener(e -> dispose());

        bottom.add(btnSave);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

        // 原版 Theme.Holo 深色 windowBackground（纯黑）——见 HoloContent.WINDOW_BG
        HoloContent.applyWindowBackground(this);

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(getOwner());
    }

    private void saveInfo() {
        if (mySQLite.m_SQL != null && m_id > 0) {
            String title = et_Title.getText().trim();
            String author = et_Author.getText().trim();
            String comment = et_Comment.getText().trim();
            mySQLite.m_SQL.Update_T_Inf(m_id, title, author, comment);
            myMaps.J_Title = title;
            myMaps.J_Author = author;
            myMaps.J_Comment = comment;
        }
    }
}
