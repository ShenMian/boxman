package my.boxman;

import javax.swing.*;
import java.awt.*;
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

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 标题
        JPanel pTitle = new JPanel(new BorderLayout(5, 5));
        pTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblTitle = new JLabel("标题:");
        lblTitle.setPreferredSize(new Dimension(50, 25));
        et_Title = new JTextField(myMaps.J_Title != null ? myMaps.J_Title : "");
        pTitle.add(lblTitle, BorderLayout.WEST);
        pTitle.add(et_Title, BorderLayout.CENTER);
        content.add(pTitle);
        content.add(Box.createVerticalStrut(8));

        // 作者
        JPanel pAuthor = new JPanel(new BorderLayout(5, 5));
        pAuthor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel lblAuthor = new JLabel("作者:");
        lblAuthor.setPreferredSize(new Dimension(50, 25));
        et_Author = new JTextField(myMaps.J_Author != null ? myMaps.J_Author : "");
        pAuthor.add(lblAuthor, BorderLayout.WEST);
        pAuthor.add(et_Author, BorderLayout.CENTER);
        content.add(pAuthor);
        content.add(Box.createVerticalStrut(8));

        // 说明 / 注释
        JPanel pComment = new JPanel(new BorderLayout(5, 5));
        JLabel lblComment = new JLabel("说明:");
        lblComment.setPreferredSize(new Dimension(50, 25));
        et_Comment = new JTextArea(myMaps.J_Comment != null ? myMaps.J_Comment : "");
        et_Comment.setLineWrap(true);
        et_Comment.setWrapStyleWord(true);
        et_Comment.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane spComment = new JScrollPane(et_Comment);
        spComment.setPreferredSize(new Dimension(400, 180));
        pComment.add(lblComment, BorderLayout.NORTH);
        pComment.add(spComment, BorderLayout.CENTER);
        content.add(pComment);
        content.add(Box.createVerticalStrut(8));

        // 关卡数量统计
        tv_Count = new JLabel("关卡数量：" + (message != null ? message : ""));
        tv_Count.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(tv_Count);

        add(content, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("保存");
        JButton btnCancel = new JButton("取消");

        btnSave.addActionListener(e -> {
            saveInfo();
            dispose();
        });
        btnCancel.addActionListener(e -> dispose());

        bottom.add(btnSave);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

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
