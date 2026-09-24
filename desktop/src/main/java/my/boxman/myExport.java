package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import my.boxman.compat.UiWindow;

/**
 * Level Export Frame for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myExport Activity.
 *
 * <p><b>ActionBar</b>：原版 {@code setTitle("导出")} + {@code setDisplayHomeAsUpEnabled(true)}，
 * {@code res/menu/export.xml} 只有一项 {@code exp_gif_make}「GIF 导出」，且是
 * {@code showAsAction="always"} —— 所以它是 ActionBar 上的文字按钮，不是溢出项。
 *
 * <p><b>可见性</b>：原版 {@code onCreateOptionsMenu} 里
 * {@code if (my_Local == null) menu.setGroupVisible(0, false)} ——
 * 从**浏览界面**（`myGridView`）进来时 {@code LOCAL} 传 {@code null}，这一项隐藏；
 * 从**推关卡界面**（`myGameView`）进来时才可见。所以 {@code my_Local} 必须保持可为 {@code null}。
 */
public class myExport extends JFrame {

    public JTextArea et_Action;
    public JCheckBox cb_XSB;
    public JCheckBox cb_Lurd;
    public JCheckBox cb_File;
    public JCheckBox cb_Cur;
    public JCheckBox cb_Trun;
    public JButton bt_OK;
    private myActionBar actionBar;

    int m_Gif_Start;
    boolean is_ANS;
    /** 原版 {@code LOCAL}：{@code null} 表示「浏览界面的导出」（此时「GIF 导出」菜单项隐藏） */
    String my_Local;
    String my_Loca8;
    String my_XSB = "";
    String my_Lurd = "";
    String my_imPort_YASS = "";
    StringBuilder my_AND;

    boolean[] my_Rule;
    short[] my_BoxNum;

    public myExport(String xsb, String lurd, String local, String local8, boolean isAns, int gifStart, boolean[] rule, short[] boxNum, String importYass) {
        setTitle("导出 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        this.my_XSB = xsb != null ? xsb : "";
        this.my_Lurd = lurd != null ? lurd : "";
        this.my_Local = local;                      // 保持可为 null（见类注释）
        this.my_Loca8 = local8;
        this.is_ANS = isAns;
        this.m_Gif_Start = gifStart;
        this.my_Rule = rule;
        this.my_BoxNum = boxNum;
        this.my_imPort_YASS = importYass != null ? importYass : "";

        this.my_AND = new StringBuilder("\n");
        if (is_ANS && !my_Lurd.isEmpty()) {
            int len = my_Lurd.length();
            int p = 0;
            for (int k = 0; k < len; k++) {
                if ("LURD".indexOf(my_Lurd.charAt(k)) >= 0) p++;
            }
            String note;
            if (my_imPort_YASS.toLowerCase().contains("yass")) {
                note = "YASS " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            } else if (my_imPort_YASS.contains("导入")) {
                note = "导入 " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            } else {
                note = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            }
            my_AND.append("Solution (moves ").append(len).append(", pushes ").append(p);
            if (myMaps.m_Sets[30] == 1) {
                my_AND.append(", comment ").append(note);
            }
            my_AND.append("): \n");
        }

        initUI();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    public myExport() {
        this(myMaps.curMap != null ? myMaps.curMap.Map : "", "", null, "", false, 0, null, null, "");
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        // 原版：ActionBar.setTitle("导出") + setDisplayHomeAsUpEnabled(true)
        actionBar = new myActionBar();
        actionBar.setBarTitle("导出");
        actionBar.setUpEnabled(true, this::dispose);
        // export.xml 的 exp_gif_make 是 showAsAction="always" → ActionBar 上的文字按钮
        actionBar.addBarAction("GIF 导出", this::showGifDialog);
        // onCreateOptionsMenu：浏览界面的导出（LOCAL == null）时该项隐藏
        actionBar.setBarActionVisible("GIF 导出", my_Local != null);

        JPanel topOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        cb_XSB = new JCheckBox("关卡(XSB)", true);
        cb_Lurd = new JCheckBox("动作(Lurd)", !my_Lurd.isEmpty());
        cb_File = new JCheckBox("导出到文档", false);
        cb_Cur = new JCheckBox("现场地图", false);
        cb_Trun = new JCheckBox("旋转状态", false);
        cb_Trun.setEnabled(false);

        topOptions.add(cb_XSB);
        topOptions.add(cb_Lurd);
        topOptions.add(cb_File);
        if (my_Local != null) {
            topOptions.add(cb_Cur);
            topOptions.add(cb_Trun);
        }

        // ActionBar 与选项行都靠顶：BorderLayout 的 NORTH 只能放一个，所以套一层竖排容器
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        actionBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(actionBar);
        north.add(topOptions);
        add(north, BorderLayout.NORTH);

        et_Action = new JTextArea();
        et_Action.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        et_Action.setEditable(false);
        updateContent();

        add(new JScrollPane(et_Action), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bt_OK = new JButton("执行导出");
        JButton bt_Close = new JButton("关闭");
        bt_Close.addActionListener(e -> dispose());
        bottomBar.add(bt_OK);
        bottomBar.add(bt_Close);
        add(bottomBar, BorderLayout.SOUTH);

        // CheckBox Listeners
        cb_XSB.addActionListener(e -> updateContent());
        cb_Lurd.addActionListener(e -> updateContent());
        cb_Cur.addActionListener(e -> {
            cb_Trun.setEnabled(cb_Cur.isSelected());
            updateContent();
        });
        cb_Trun.addActionListener(e -> updateContent());

        bt_OK.addActionListener(e -> doExport());
    }

    private void updateContent() {
        if (cb_Cur != null && cb_Cur.isSelected()) {
            if (cb_Trun.isSelected() && my_Loca8 != null && !my_Loca8.isEmpty()) {
                et_Action.setText(my_Loca8);
            } else {
                et_Action.setText(my_Local == null ? "" : my_Local);
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (cb_XSB.isSelected() && !my_XSB.isEmpty()) {
            sb.append(my_XSB);
        }
        if (cb_Lurd.isSelected() && !my_Lurd.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(my_AND);
            }
            sb.append(my_Lurd);
        }
        et_Action.setText(sb.toString());
    }

    private void doExport() {
        String text = et_Action.getText();
        if (cb_File.isSelected()) {
            try {
                File dir = new File(myMaps.sRoot + myMaps.sPath + "导出/");
                if (!dir.exists()) dir.mkdirs();
                String ext = cb_Lurd.isSelected() ? ".txt" : ".xsb";
                int idx = (myMaps.m_lstMaps != null && myMaps.curMap != null) ? myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1 : 1;
                String fn = (myMaps.sFile != null ? myMaps.sFile : "Level") + "_" + idx + ext;
                File target = new File(dir, fn);

                try (FileOutputStream fos = new FileOutputStream(target)) {
                    fos.write(text.getBytes("UTF-8"));
                }
                JOptionPane.showMessageDialog(this, "导出成功: \n" + target.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出文件失败: " + ex.getMessage());
            }
        } else {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                JOptionPane.showMessageDialog(this, "已复制到剪贴板！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "剪贴板复制失败: " + ex.getMessage());
            }
        }
    }

    private void showGifDialog() {
        myGifMakeDialog dlg = new myGifMakeDialog(this, my_Lurd, m_Gif_Start, my_Rule, my_BoxNum);
        dlg.setVisible(true);
    }
}
