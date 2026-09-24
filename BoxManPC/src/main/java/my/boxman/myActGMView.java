package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import my.boxman.compat.UiWindow;

/**
 * Action Manager for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myActGMView Activity.
 */
public class myActGMView extends JDialog {

    public JTextArea et_Action;
    public JCheckBox et_curPos;
    public JCheckBox et_curTrun;
    public JCheckBox et_PreEdit;

    public JButton btLoadAct, btSaveAct, btSave_t, btClear, btDO;

    private boolean is_BK;
    private boolean flg = false;
    private static String reg0 = "";

    public myActGMView(Frame parent, boolean isBK) {
        super(parent, "动作管理", true);
        this.is_BK = isBK;

        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        // Top checkboxes
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        et_curPos = new JCheckBox("当前点执行", myMaps.m_ActionIsPos);
        et_curPos.addActionListener(e -> myMaps.m_ActionIsPos = et_curPos.isSelected());

        et_curTrun = new JCheckBox("当前旋转执行", myMaps.m_ActionIsTrun);
        et_curTrun.addActionListener(e -> myMaps.m_ActionIsTrun = et_curTrun.isSelected());

        et_PreEdit = new JCheckBox("加载上次编辑", false);
        et_PreEdit.addActionListener(e -> {
            if (et_PreEdit.isSelected()) {
                et_Action.setText(reg0);
            } else {
                et_Action.setText(myMaps.loadLURD(loadClipper(), is_BK ? -1 : 1));
            }
            flg = false;
        });

        topPanel.add(et_curPos);
        topPanel.add(et_curTrun);
        topPanel.add(et_PreEdit);
        add(topPanel, BorderLayout.NORTH);

        // Center text area
        et_Action = new JTextArea();
        et_Action.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        et_Action.setLineWrap(true);
        et_Action.setWrapStyleWord(true);
        et_Action.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { flg = true; }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { flg = true; }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { flg = true; }
        });

        // Default fill from clipboard
        String clip = loadClipper();
        if (clip != null && !clip.isEmpty()) {
            et_Action.setText(myMaps.loadLURD(clip, is_BK ? -1 : 1));
        }

        add(new JScrollPane(et_Action), BorderLayout.CENTER);

        // Bottom button bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        btLoadAct = new JButton("加载");
        btLoadAct.addActionListener(e -> doLoad());

        btSaveAct = new JButton("保存");
        btSaveAct.addActionListener(e -> doSave());

        btSave_t = new JButton("暂存");
        btSave_t.addActionListener(e -> {
            reg0 = et_Action.getText();
            JOptionPane.showMessageDialog(this, "成功暂存！");
        });

        btClear = new JButton("清空");
        btClear.addActionListener(e -> {
            et_Action.setText("");
            flg = false;
        });

        btDO = new JButton("执行");
        btDO.addActionListener(e -> doExecute());

        bottomBar.add(btLoadAct);
        bottomBar.add(btSaveAct);
        bottomBar.add(btSave_t);
        bottomBar.add(btClear);
        bottomBar.add(btDO);

        add(bottomBar, BorderLayout.SOUTH);

        // Menu bar for transforms
        setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu mTransform = new JMenu("动作变换");

        JMenuItem miL90 = new JMenuItem("左旋90度");
        miL90.addActionListener(e -> transformLURD("L90"));
        JMenuItem miR90 = new JMenuItem("右旋90度");
        miR90.addActionListener(e -> transformLURD("R90"));
        JMenuItem mi180 = new JMenuItem("旋转180度");
        mi180.addActionListener(e -> transformLURD("180"));
        JMenuItem miLR = new JMenuItem("左右翻转");
        miLR.addActionListener(e -> transformLURD("LR"));
        JMenuItem miUD = new JMenuItem("上下翻转");
        miUD.addActionListener(e -> transformLURD("UD"));

        mTransform.add(miL90);
        mTransform.add(miR90);
        mTransform.add(mi180);
        mTransform.add(miLR);
        mTransform.add(miUD);
        mb.add(mTransform);

        return mb;
    }

    public void transformLURD(String type) {
        String text = et_Action.getText();
        if (!myMaps.isLURD(text)) {
            JOptionPane.showMessageDialog(this, "仅支持规范的动作字符！");
            return;
        }

        Pattern p = Pattern.compile("l|L|r|R|u|U|d|D");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String ch = m.group();
            String rep = ch;
            switch (type) {
                case "L90":
                    if ("l".equals(ch)) rep = "d"; else if ("L".equals(ch)) rep = "D";
                    else if ("u".equals(ch)) rep = "l"; else if ("U".equals(ch)) rep = "L";
                    else if ("r".equals(ch)) rep = "u"; else if ("R".equals(ch)) rep = "U";
                    else if ("d".equals(ch)) rep = "r"; else if ("D".equals(ch)) rep = "R";
                    break;
                case "R90":
                    if ("l".equals(ch)) rep = "u"; else if ("L".equals(ch)) rep = "U";
                    else if ("u".equals(ch)) rep = "r"; else if ("U".equals(ch)) rep = "R";
                    else if ("r".equals(ch)) rep = "d"; else if ("R".equals(ch)) rep = "D";
                    else if ("d".equals(ch)) rep = "l"; else if ("D".equals(ch)) rep = "L";
                    break;
                case "180":
                    if ("l".equals(ch)) rep = "r"; else if ("L".equals(ch)) rep = "R";
                    else if ("u".equals(ch)) rep = "d"; else if ("U".equals(ch)) rep = "D";
                    else if ("r".equals(ch)) rep = "l"; else if ("R".equals(ch)) rep = "L";
                    else if ("d".equals(ch)) rep = "u"; else if ("D".equals(ch)) rep = "U";
                    break;
                case "LR":
                    if ("l".equals(ch)) rep = "r"; else if ("L".equals(ch)) rep = "R";
                    else if ("r".equals(ch)) rep = "l"; else if ("R".equals(ch)) rep = "L";
                    break;
                case "UD":
                    if ("u".equals(ch)) rep = "d"; else if ("U".equals(ch)) rep = "D";
                    else if ("d".equals(ch)) rep = "u"; else if ("D".equals(ch)) rep = "U";
                    break;
            }
            m.appendReplacement(sb, rep);
        }
        m.appendTail(sb);
        et_Action.setText(sb.toString());
    }

    private void doLoad() {
        String[] opts = {"剪切板", "暂存内容", "宏文件..."};
        int choice = JOptionPane.showOptionDialog(this, "选择加载来源", "加载动作",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (choice == 0) {
            et_Action.setText(myMaps.loadLURD(loadClipper(), is_BK ? -1 : 1));
        } else if (choice == 1) {
            et_Action.setText(reg0);
        }
    }

    private void doSave() {
        String[] opts = {"剪切板", "暂存寄存器", "保存到宏文件..."};
        int choice = JOptionPane.showOptionDialog(this, "选择保存目的地", "保存动作",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (choice == 0) {
            saveClipper(et_Action.getText());
            JOptionPane.showMessageDialog(this, "已复制到剪贴板！");
        } else if (choice == 1) {
            reg0 = et_Action.getText();
            JOptionPane.showMessageDialog(this, "已暂存！");
        }
    }

    private void doExecute() {
        String actStr = et_Action.getText().trim();
        if (is_BK && !myMaps.isLURD2(actStr)) {
            JOptionPane.showMessageDialog(this, "逆推请使用标准动作字符！");
            return;
        }

        myMaps.m_ActionIsRedy = true;
        if (myMaps.isLURD(actStr)) {
            myMaps.sAction = new String[]{"{" + actStr + "}~"};
        } else {
            myMaps.sAction = actStr.split("\n|\r|\n\r|\r\n|\\|");
        }
        dispose();
    }

    private static String loadClipper() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void saveClipper(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }
}
