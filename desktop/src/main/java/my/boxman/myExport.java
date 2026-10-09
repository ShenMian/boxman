package my.boxman;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
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
 *
 * <p><b>版面</b>：严格按 {@code res/layout/export_view.xml} 复刻（根 LinearLayout 无
 * {@code android:background} → 透出主题的纯黑 windowBackground）：
 * <pre>
 *   EditText ex_edit_text       350dp 高、bg #242424、等宽、不可编辑/不可聚焦
 *   View                        6dp  #363636
 *   LinearLayout #363636        「导出选项：」+ 16dp + XSB + 16dp + Lurd
 *   View                        6dp  #363636
 *   LinearLayout ex_is_grid     一行 cb_Cur + 30dp + cb_Trun（无背景 → 纯黑）
 *   LinearLayout #363636        cb_File + 80dp + bt_ex_OK（layout_marginTop 8dp）
 * </pre>
 * 原版**没有**底部「关闭」按钮 —— 返回走 ActionBar 的返回折角。
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
    /** 原版 {@code ex_is_grid}：浏览界面的导出时整体 {@code GONE} */
    private JPanel isGridRow;

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
        // ⚠️ 原版只在「推关卡界面的导出」（my_Local != null）里拼 Solution 头；
        //    浏览界面的导出（my_Local == null）my_AND 始终就是 "\n"。
        //    PC 早期把它写成了无条件拼接，浏览界面会凭空多出 "Solution (...):" 一行。
        if (my_Local != null && is_ANS && !my_Lurd.isEmpty()) {
            int len = my_Lurd.length();
            int p = 0;
            for (int k = 0; k < len; k++) {
                if ("LURD".indexOf(my_Lurd.charAt(k)) >= 0) p++;
            }
            String note;
            if (my_imPort_YASS.toLowerCase().contains("yass")) {
                note = "YASS " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            } else if (my_imPort_YASS.toLowerCase().contains("导入")) {
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
        initState();        // 原版 onCreate 里的初始状态（必须在监听器挂上之前跑）

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    public myExport() {
        this(myMaps.curMap != null ? myMaps.curMap.Map : "", "", null, "", false, 0, null, null, "");
    }

    // ================================================================ 版面

    private void initUI() {
        // 原版 export_view.xml 根布局没有 android:background → Theme.Holo 的纯黑 windowBackground
        HoloContent.applyWindowBackground(this);
        setLayout(new BorderLayout());

        // 原版：ActionBar.setTitle("导出") + setDisplayHomeAsUpEnabled(true)
        actionBar = new myActionBar();
        actionBar.setBarTitle("导出");
        actionBar.setUpEnabled(true, this::dispose);
        // export.xml 的 exp_gif_make 是 showAsAction="always" → ActionBar 上的文字按钮
        actionBar.addBarAction("GIF 导出", this::showGifDialog);
        // onCreateOptionsMenu：浏览界面的导出（LOCAL == null）时该项隐藏
        actionBar.setBarActionVisible("GIF 导出", my_Local != null);
        add(actionBar, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(HoloContent.WINDOW_BG);
        content.setOpaque(true);

        // ---- 1) ex_edit_text：350dp 高、#242424、等宽、不可编辑（原版 setFocusable(false)）
        et_Action = new JTextArea();
        et_Action.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        et_Action.setBackground(HoloContent.FIELD_BG);
        et_Action.setForeground(HoloContent.TEXT);
        et_Action.setCaretColor(HoloContent.TEXT);
        et_Action.setEditable(false);
        et_Action.setFocusable(false);
        et_Action.setLineWrap(true);
        et_Action.setWrapStyleWord(true);
        et_Action.setBorder(new javax.swing.border.EmptyBorder(
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));

        JScrollPane spAction = new JScrollPane(et_Action);
        spAction.setBorder(BorderFactory.createEmptyBorder());
        spAction.setViewportBorder(BorderFactory.createEmptyBorder());
        spAction.getViewport().setBackground(HoloContent.FIELD_BG);
        spAction.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d350 = new Dimension(0, 350);
        spAction.setPreferredSize(d350);
        spAction.setMinimumSize(d350);
        spAction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 350));
        content.add(spAction);

        // ---- 2) 6dp #363636
        content.add(HoloContent.band(HoloContent.BAND, 6));

        // ---- 3) 「导出选项：」+ XSB + Lurd
        cb_XSB = HoloContent.wrapCheck("XSB", false);
        cb_Lurd = HoloContent.wrapCheck("Lurd", false);
        content.add(HoloContent.row(HoloContent.BAND, 4,
                HoloContent.label("导出选项："),
                Box.createHorizontalStrut(16), cb_XSB,
                Box.createHorizontalStrut(16), cb_Lurd));

        // ---- 4) 6dp #363636
        content.add(HoloContent.band(HoloContent.BAND, 6));

        // ---- 5) ex_is_grid：无背景（纯黑），marginTop 8dp
        cb_Cur = HoloContent.wrapCheck("导出正推现场", false);
        cb_Trun = HoloContent.wrapCheck("保持现场旋转", false);
        content.add(Box.createVerticalStrut(8));
        isGridRow = HoloContent.row(HoloContent.WINDOW_BG, 4,
                cb_Cur, Box.createHorizontalStrut(30), cb_Trun);
        content.add(isGridRow);

        // ---- 6) cb_File + 80dp + bt_ex_OK（#363636、marginTop 8dp）
        cb_File = HoloContent.wrapCheck("导出到文档", false);
        // 原版 export_view.xml 的 bt_ex_OK：文字是「导出」，主题默认 Widget.Holo.Button → HoloButton
        bt_OK = new HoloButton("导出");
        content.add(Box.createVerticalStrut(8));
        content.add(HoloContent.row(HoloContent.BAND, 4,
                cb_File, Box.createHorizontalStrut(80), bt_OK));

        // 原版根布局是 wrap_content，内容不占满整屏 —— 用 NORTH 包一层，避免被拉高
        JPanel contentWrap = new JPanel(new BorderLayout());
        contentWrap.setBackground(HoloContent.WINDOW_BG);
        contentWrap.setOpaque(true);
        contentWrap.add(content, BorderLayout.NORTH);
        add(contentWrap, BorderLayout.CENTER);

        bt_OK.addActionListener(e -> doExport());
    }

    // ================================================================ 初始状态（原版 myExport.java:127-170）

    private void initState() {
        cb_File.setSelected(false);
        cb_Cur.setSelected(false);
        cb_Trun.setEnabled(false);

        if (my_Local == null) {  // 浏览界面的导出
            if (my_Lurd.isEmpty()) {  // 无答案
                et_Action.setText(my_XSB);
                myMaps.isLurd = false;
                cb_Lurd.setSelected(false);
                cb_Lurd.setEnabled(false);
            } else {  // 有答案
                et_Action.setText(my_XSB + my_AND + my_Lurd);
                myMaps.isLurd = true;
                cb_Lurd.setSelected(true);
                cb_Lurd.setEnabled(true);
            }
            myMaps.isXSB = true;
            cb_XSB.setSelected(true);
            cb_XSB.setEnabled(false);
        } else {  // 推关卡界面的导出
            cb_XSB.setEnabled(true);
            if (my_Lurd.isEmpty()) {
                et_Action.setText(my_XSB);
                myMaps.isLurd = false;
                cb_Lurd.setEnabled(false);
                myMaps.isXSB = true;
                cb_XSB.setSelected(true);
            } else {
                et_Action.setText(my_Lurd);
                myMaps.isXSB = false;
                cb_Lurd.setEnabled(true);
                myMaps.isLurd = true;
                cb_Lurd.setSelected(true);
            }
        }

        // ex_is_grid：浏览界面的导出时整体 GONE
        isGridRow.setVisible(my_Local != null);

        // ⚠️ 监听器必须在初始状态**之后**挂 —— 原版就是这么排的
        //    （Android 的 setChecked 会触发监听器，Swing 的 setSelected 不会；
        //      先设状态再挂监听，两边行为才一致）
        wireListeners();
    }

    private void wireListeners() {
        cb_XSB.addActionListener(e -> onXsbToggled());
        cb_Lurd.addActionListener(e -> onLurdToggled());
        cb_Cur.addActionListener(e -> onCurToggled());
        cb_Trun.addActionListener(e -> onTrunToggled());
    }

    /** 原版 {@code cb_XSB.setOnCheckedChangeListener}。 */
    private void onXsbToggled() {
        boolean isChecked = cb_XSB.isSelected();
        if (my_Lurd.isEmpty()) {
            cb_XSB.setSelected(true);   // 没有答案时 XSB 恒选
            return;
        }
        myMaps.isXSB = isChecked;
        if (!isChecked && !cb_Lurd.isSelected()) {
            // 原版这里 cb_Lurd.setChecked(true) 会连带触发 Lurd 监听器；
            // Swing 的 setSelected 不触发，所以手动把 isLurd 与文案补上
            cb_Lurd.setSelected(true);
            myMaps.isLurd = true;
        }
        if (myMaps.isXSB) {
            if (myMaps.isLurd && !my_Lurd.isEmpty()) {
                et_Action.setText(my_XSB + my_AND + my_Lurd);
            } else {
                et_Action.setText(my_XSB);
            }
        } else {
            et_Action.setText(my_Lurd);
        }
    }

    /** 原版 {@code cb_Lurd.setOnCheckedChangeListener}。 */
    private void onLurdToggled() {
        boolean isChecked = cb_Lurd.isSelected();
        if (my_Local == null) {  // 浏览界面的导出，能够到这里，肯定是有答案的
            myMaps.isLurd = isChecked;
            if (myMaps.isLurd) {
                et_Action.setText(my_XSB + my_AND + my_Lurd);
            } else {
                et_Action.setText(my_XSB);
            }
            return;
        }
        myMaps.isLurd = isChecked;
        if (!isChecked && !cb_XSB.isSelected()) {
            cb_XSB.setSelected(true);
            myMaps.isXSB = true;
        }
        if (myMaps.isXSB) {
            if (myMaps.isLurd && !my_Lurd.isEmpty()) {
                et_Action.setText(my_XSB + my_AND + my_Lurd);
            } else {
                et_Action.setText(my_XSB);
            }
        } else {
            et_Action.setText(my_Lurd);
        }
    }

    /** 原版 {@code cb_Cur.setOnCheckedChangeListener}。 */
    private void onCurToggled() {
        if (cb_Cur.isSelected()) {
            cb_Trun.setEnabled(true);
            cb_XSB.setEnabled(false);
            if (!my_Lurd.isEmpty()) cb_Lurd.setEnabled(false);
            if (cb_Trun.isSelected()) {
                et_Action.setText(my_Loca8);
            } else {
                et_Action.setText(my_Local);
            }
        } else {
            cb_Trun.setEnabled(false);
            cb_XSB.setEnabled(true);
            if (!my_Lurd.isEmpty()) cb_Lurd.setEnabled(true);
            if (myMaps.isXSB) {
                if (myMaps.isLurd && !my_Lurd.isEmpty()) {
                    if (is_ANS) {  // 若是答案
                        et_Action.setText(my_XSB + "\nSolution: " + my_Lurd);
                    } else {
                        et_Action.setText(my_XSB + "\n" + my_Lurd);
                    }
                } else {
                    et_Action.setText(my_XSB);
                }
            } else {
                et_Action.setText(my_Lurd);
            }
        }
    }

    /** 原版 {@code cb_Trun.setOnCheckedChangeListener}。 */
    private void onTrunToggled() {
        et_Action.setText(cb_Trun.isSelected() ? my_Loca8 : my_Local);
    }

    // ================================================================ 导出（原版 myExport.java:268-320）

    private void doExport() {
        String text = et_Action.getText();
        if (cb_File.isSelected()) {  // 导出到文档
            // 按关卡集和关卡序号生成文档名
            final String my_Name = new StringBuilder("导出/").append(myMaps.sFile).append("_")
                    .append(myMaps.m_lstMaps != null && myMaps.curMap != null
                            ? myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1 : 1)
                    .append(cb_Lurd.isSelected() ? ".txt" : ".xsb").toString();
            File file = new File(myMaps.sRoot + myMaps.sPath + my_Name);
            if (file.exists()) {
                // 原版：AlertDialog「警告 / 文档已存在，覆写吗？」+ 取消 / 覆写
                new HoloConfirmDialog(this, "警告", "文档已存在，覆写吗？\n" + my_Name,
                        "取消", "覆写", () -> saveFile(text, my_Name)).setVisible(true);
            } else {
                saveFile(text, my_Name);
            }
        } else {  // 导出到剪切板
            myMaps.saveClipper("\n" + text + "\n");
            dispose();
        }
    }

    /** 导出状态到文档（原版 {@code saveFile}）。 */
    private void saveFile(String str, String fn) {
        try {
            File dir = new File(myMaps.sRoot + myMaps.sPath + "导出/");
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fout = new FileOutputStream(myMaps.sRoot + myMaps.sPath + fn)) {
                // 原版是 str.getBytes()（平台默认编码）；PC 端统一走 UTF-8，与导入链一致
                fout.write(str.getBytes("UTF-8"));
            }
            // 原版：AlertDialog「成功」+ 单个「确定」，点了才 finish()
            HoloAlertDialog dlg = HoloAlertDialog.create(this, "成功");
            dlg.setContentView(HoloMessageDialog.messageBody(fn));
            dlg.addButton("确定", this::dispose);
            dlg.setVisible(true);
        } catch (Exception e) {
            MyToast.showToast(this, "出错了，导出失败！", MyToast.LENGTH_SHORT);
            dispose();
        }
    }

    // ================================================================ GIF 导出（原版 myExport.java:349-449）

    private void showGifDialog() {
        // 原版：先截出「正推动作」，没有正推动作直接提示返回
        String act;
        int n = my_Lurd.indexOf('\n');
        if (n >= 0) {
            act = my_Lurd.substring(0, n);
        } else {
            n = my_Lurd.indexOf('[');
            act = n >= 0 ? my_Lurd.substring(0, n) : my_Lurd;
        }
        if (act.length() <= 0 || act.length() < m_Gif_Start) {
            MyToast.showToast(this, "没有制作动画的正推动作！", MyToast.LENGTH_SHORT);
            return;
        }
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "GIF/");
        if (!targetDir.exists()) targetDir.mkdirs();  // 创建自定义 GIF 文件夹

        myGifMakeDialog dlg = new myGifMakeDialog(this, my_Lurd, m_Gif_Start, my_Rule, my_BoxNum);
        dlg.setVisible(true);
    }
}
