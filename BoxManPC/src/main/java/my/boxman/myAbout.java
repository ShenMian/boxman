package my.boxman;

import javax.swing.*;
import java.awt.*;
import my.boxman.compat.UiWindow;

public class myAbout extends JDialog {

    public myAbout(Frame owner) {
        super(owner, "关于", true);
        initUI();
    }

    private void initUI() {
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JTextArea tv_help = new JTextArea();
        tv_help.setEditable(false);
        tv_help.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        tv_help.setLineWrap(true);
        tv_help.setWrapStyleWord(true);
        tv_help.setMargin(new Insets(10, 15, 10, 15));

        String s = "《推箱快手》PC版\n\n" +
                "  版本：9.99u~ (PC Swing移植版)\n\n" +
                "  作者：愉翁    QQ：252804303\n\n" +
                "  策划：anian、愉翁\n\n" +
                "  网站：sokoban.cn\n\n" +
                "  QQ群：92017135\n\n" +
                "  特别感谢：anian老师和杨超教授及众多热心箱友，anian老师在游戏设计的全过程中，" +
                "给出了大量的指导性意见，并进行了繁重的开发期测试，尤其在“割点”寻径及“穿越”寻径等算法方面，" +
                "更是得到了两位老师不遗余力的支持和帮助，许多算法都是直接移植于杨超教授的“SokoPlayer HTML5”。" +
                "另一方面，anian老师也为游戏提供了几乎全部的关卡集原始档案，还有少数关卡选自“http://sokoban.cn/”，" +
                "在游戏的公测期间，也收到了众多箱友的宝贵建议，这些建议包含了便捷操作、界面调整、功能增减以及错误修复等诸多方面。" +
                "可以说，离开了anian老师和杨超教授的倾心指导以及众位箱友热心支持，本游戏不会这么顺利地完成编写。" +
                "还有，从网上淘到“衣旧”网友编写的一个“ini文件工具类”，用在了系统配置的存取；" +
                "“闭口对角”死锁的检测代码和 XSB 的图像自动识别代码，移植于德国 Matthias Meger 大师的 JSoko；" +
                "以及 Kevin Weiner, FM Software 的 GIF 合成代码。" +
                "特此鸣谢！同时，也祝各位箱友都能很快的晋升到推箱子群中的快手之列！";

        tv_help.setText(s);
        tv_help.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(tv_help);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("确定");
        btnClose.addActionListener(e -> dispose());
        bottomPanel.add(btnClose);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}
