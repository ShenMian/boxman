package my.boxman;

import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.UiWindow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「动作管理 / 导入」窗口 —— Android {@code myActGMView} Activity 的 1:1 移植。
 *
 * <p><b>ActionBar</b>：原版 {@code setDisplayHomeAsUpEnabled(true)} +
 * {@code setDisplayShowHomeEnabled(false)} + {@code setTitle("导入")}（原版 77~80 行）。
 * 菜单来自 {@code res/menu/act_gm.xml}，共 8 项：
 * <ul>
 *   <li>{@code act_recording}「录制动作」是 {@code showAsAction="always"} → ActionBar 上的文字按钮；
 *       {@code onCreateOptionsMenu} 里 {@code menu.getItem(0).setVisible(!is_BK)}
 *       —— <b>逆推界面（{@code is_BK}）时隐藏</b>。</li>
 *   <li>5 个动作变换项（标题带「（Lurd）」后缀，原版如此）、{@code act_micro_about}「“宏”功能说明」、
 *       {@code act_about}「“导入”说明」→ 溢出菜单。</li>
 * </ul>
 *
 * <p><b>布局</b>照 {@code res/layout/action_manage.xml}：竖向排列的
 * [415dp 高的动作编辑区] + [「执行: 」+ 从当前点 / 按关卡之旋转] + [加载 存入 清空 暂存 恢复 执行]。
 * 三个复选框都是 {@code style/CustomCheckboxTheme}（32dp 大号位图，见 {@link HoloContent#check32}）。
 */
public class myActGMView extends JDialog {

    // ---------------------------------------------------------------- 组件（与原版 action_manage.xml 一一对应）

    public JTextArea et_Action;
    public JCheckBox et_curPos;
    public JCheckBox et_curTrun;
    public JCheckBox et_PreEdit;

    public JButton btLoadAct, btSaveAct, btSave_t, btClear, btDO;

    private myActionBar actionBar;

    // ---------------------------------------------------------------- 状态

    private final Frame owner;
    /** 接收是否逆推界面 */
    private final boolean is_BK;
    /** 对话框中的出 item 选择前的记忆（原版 {@code m_nItemSelect}） */
    private int m_nItemSelect;
    /** 编辑框内容是否改动 */
    private boolean flg = false;

    private static final Pattern LURD_PATTERN = Pattern.compile("l|L|r|R|u|U|d|D");

    /** 原版「加载」对话框的 14 个选项 */
    private static final String[] LOAD_MENU = {
            "宏", "已做动作", "后续动作", "文档", "剪切板",
            "寄存器1", "寄存器2", "寄存器3", "寄存器4", "寄存器5",
            "寄存器6", "寄存器7", "寄存器8", "寄存器9"
    };

    /** 原版「保存到」对话框的 11 个选项 */
    private static final String[] SAVE_MENU = {
            "宏", "剪切板",
            "寄存器1", "寄存器2", "寄存器3", "寄存器4", "寄存器5",
            "寄存器6", "寄存器7", "寄存器8", "寄存器9"
    };

    public myActGMView(Frame parent, boolean isBK) {
        super(parent, "导入", true);
        this.owner = parent;
        this.is_BK = isBK;

        initUI();

        // ⚠️ 必须在 initUI() 之后再调，否则 ActionBar 会从内容区里挖走 48px（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(parent);

        onCreate();
    }

    // ================================================================ UI

    private void initUI() {
        setLayout(new BorderLayout());

        add(buildActionBar(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        // 「执行: 」那一行的复选框初始值 = action_manage.xml 的 android:checked
        // （cb_curPos=true、cb_curTrun=false、cb_PreEdit=false）
        et_curPos.setSelected(true);
        et_curTrun.setSelected(false);
        et_PreEdit.setSelected(false);
    }

    /** {@code res/menu/act_gm.xml} + 原版 {@code onCreateOptionsMenu} / {@code onOptionsItemSelected}。 */
    private myActionBar buildActionBar() {
        actionBar = new myActionBar();
        actionBar.setBarTitle("导入");
        actionBar.setUpEnabled(true, this::onHome);

        // act_recording「录制动作」：showAsAction="always" → ActionBar 文字按钮；
        // onCreateOptionsMenu 里 menu.getItem(0).setVisible(!is_BK)
        actionBar.addBarAction("录制动作", this::onRecording);
        actionBar.setBarActionVisible("录制动作", !is_BK);

        actionBar.addAction("左旋90度（Lurd）", () -> transformLURD("L90"));
        actionBar.addAction("右旋90度（Lurd）", () -> transformLURD("R90"));
        actionBar.addAction("右旋180度（Lurd）", () -> transformLURD("180"));
        actionBar.addAction("左右翻转（Lurd）", () -> transformLURD("LR"));
        actionBar.addAction("上下翻转（Lurd）", () -> transformLURD("UD"));
        actionBar.addAction("“宏”功能说明", () -> new Help(5).setVisible(true));
        actionBar.addAction("“导入”说明", () -> new Help(4).setVisible(true));

        return actionBar;
    }

    private JComponent buildContent() {
        // 原版主题是 android:Theme.Holo（深色）→ windowBackground = @color/background_holo_dark = #FF000000。
        // action_manage.xml 里只有中间那个装 EditText 的 ScrollView 显式写了 background="#363636"，
        // 它上下那两条 LinearLayout 都没有背景色 —— 所以编辑区之外应该是**纯黑**，不是 #363636。
        JPanel content = new JPanel();
        content.setOpaque(true);
        content.setBackground(Color.BLACK);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ---- 1) 动作编辑区：外层 ScrollView 高 415dp，EditText 用等宽字体 20sp、左右 padding 8dp
        et_Action = new JTextArea();
        et_Action.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
        et_Action.setBackground(HoloContent.BAND);
        et_Action.setForeground(Color.WHITE);
        et_Action.setCaretColor(Color.WHITE);
        et_Action.setLineWrap(true);
        et_Action.setWrapStyleWord(true);
        et_Action.setBorder(new EmptyBorder(0, 8, 0, 8));   // paddingLeft/Right 8dp
        et_Action.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { flg = true; }
            @Override public void removeUpdate(DocumentEvent e) { flg = true; }
            @Override public void changedUpdate(DocumentEvent e) { flg = true; }
        });

        JScrollPane spAct = new JScrollPane(et_Action);
        spAct.setBorder(BorderFactory.createEmptyBorder());
        spAct.setViewportBorder(BorderFactory.createEmptyBorder());
        spAct.getViewport().setBackground(HoloContent.BAND);
        Dimension d415 = new Dimension(0, 415);
        spAct.setPreferredSize(d415);
        spAct.setMinimumSize(d415);
        spAct.setMaximumSize(new Dimension(Integer.MAX_VALUE, 415));
        spAct.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(spAct);

        // ---- 2) 「执行: 」+ 两个复选框（layout_marginTop 4dp、marginLeft 4dp）
        JPanel rowExec = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rowExec.setOpaque(true);
        rowExec.setBackground(Color.BLACK);
        rowExec.setBorder(new EmptyBorder(4, 4, 0, 0));
        rowExec.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbExec = HoloContent.label("执行: ");
        lbExec.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        lbExec.setBorder(new EmptyBorder(4, 4, 4, 4));      // TextView padding 4dp
        rowExec.add(lbExec);

        et_curPos = bigCheck("从当前点", true, 18);
        et_curPos.addActionListener(e -> myMaps.m_ActionIsPos = et_curPos.isSelected());
        rowExec.add(et_curPos);

        et_curTrun = bigCheck("按关卡之旋转", false, 18);
        et_curTrun.addActionListener(e -> myMaps.m_ActionIsTrun = et_curTrun.isSelected());
        rowExec.add(et_curTrun);

        content.add(rowExec);

        // ---- 3) 按钮行：加载 | 存入 | 清空 | 暂存 | 恢复 | 执行（宽度 52/52/48/48/48/60dp）
        JPanel rowBtn = new JPanel();
        rowBtn.setOpaque(true);
        rowBtn.setBackground(Color.BLACK);
        rowBtn.setLayout(new BoxLayout(rowBtn, BoxLayout.X_AXIS));
        rowBtn.setBorder(new EmptyBorder(4, 0, 0, 0));
        rowBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        btLoadAct = makeButton("加载", 52);
        btSaveAct = makeButton("存入", 52);
        btClear = makeButton("清空", 48);
        btSave_t = makeButton("暂存", 48);
        btDO = makeButton("执行", 60);

        // et_PreEdit「恢\n复」：原版 text 里带换行，用 HTML 还原两行显示
        et_PreEdit = HoloContent.check32("<html>恢<br>复</html>", false, 48);
        et_PreEdit.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        et_PreEdit.setToolTipText("恢复");

        rowBtn.add(btLoadAct);
        rowBtn.add(Box.createHorizontalStrut(4));
        rowBtn.add(btSaveAct);
        rowBtn.add(Box.createHorizontalStrut(4));
        rowBtn.add(btClear);
        rowBtn.add(Box.createHorizontalStrut(4));
        rowBtn.add(btSave_t);
        rowBtn.add(Box.createHorizontalStrut(4));
        rowBtn.add(et_PreEdit);
        rowBtn.add(Box.createHorizontalStrut(4));
        rowBtn.add(btDO);

        content.add(rowBtn);

        wireListeners();

        // 顶层包一层 ScrollView（原版根布局就是 ScrollView）
        JScrollPane outer = new JScrollPane(content);
        outer.setBorder(BorderFactory.createEmptyBorder());
        outer.setViewportBorder(BorderFactory.createEmptyBorder());
        outer.getViewport().setBackground(Color.BLACK);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        return outer;
    }

    /**
     * 原版 {@code style/CustomCheckboxTheme} 的复选框，宽度按内容算（等价于 {@code wrap_content}）。
     * 别照抄 {@link HoloContent#check32} 的固定宽度 —— 18sp 的中文按固定宽度会被省略号截掉。
     */
    private static JCheckBox bigCheck(String text, boolean selected, int textSize) {
        return HoloContent.wrapCheck(text, selected, textSize);
    }

    /**
     * 原版 {@code action_manage.xml} 的 {@code <Button>}：主题默认的
     * {@code Widget.Holo.Button}（{@code btn_default_holo_dark}），
     * {@code textSize=14sp}、{@code padding=2dp}、{@code layout_width} 是确定值
     * （52/52/48/48/60dp）、{@code layout_height=wrap_content} → 被 {@code minHeight=48dp}
     * 顶到 **48dp**（此前 PC 写的是 36dp，是偏差）。
     */
    private static JButton makeButton(String text, int widthDp) {
        HoloButton b = HoloButton.create(text, 14, new Insets(2, 2, 2, 2));
        Dimension d = new Dimension(widthDp, HoloButton.MIN_HEIGHT);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        return b;
    }

    // ================================================================ onCreate / onResume

    /** 对应原版 {@code onCreate()} 里界面初始化之后的全部逻辑（原版 96~154 行）。 */
    private void onCreate() {
        // 动作编辑区
        et_PreEdit.setSelected(false);
        if (myMaps.isRecording) {                 // 录制模式且录制有效
            et_Action.setText(loadAct("act2"));
            saveAct("act2", "");                  // 录制模式仅仅借助该寄存器，所以，用后要清空
        }
        flg = false;                              // 编辑框内容是否改动
        myMaps.isRecording = false;               // 关闭录制模式
        myMaps.isMacroDebug = false;              // 单步宏

        // 是否从关卡的当前点执行动作
        myMaps.m_ActionIsPos = et_curPos.isSelected();
        // 是否按关卡的当前旋转状态执行动作
        myMaps.m_ActionIsTrun = et_curTrun.isSelected();

        // 是否加载上次编辑的动作
        et_PreEdit.addActionListener(e -> {
            if (et_PreEdit.isSelected()) {        // 加载上次编辑的动作
                et_Action.setText(loadAct("reg0"));
            } else {                              // 加载剪切板
                et_Action.setText(myMaps.loadLURD(myMaps.loadClipper(), is_BK ? -1 : 1));
            }
            flg = false;                          // 编辑框内容是否改动
        });

        // 原版 onResume()：非录制模式且为首次进入该关卡的导入功能
        // （原版有 500ms 延时，是安卓 10 取剪切板的限制，PC 不需要）
        if (!myMaps.isRecording && myMaps.m_MapChange) {
            et_Action.setText(myMaps.loadLURD(myMaps.loadClipper(), is_BK ? -1 : 1));  // 加载“剪切板”
            flg = false;
            myMaps.m_MapChange = false;           // 是否切换了关卡
        }
    }

    // ================================================================ 按钮

    private void wireListeners() {
        // 暂存
        btSave_t.addActionListener(e -> {
            if (nonBlank(et_Action.getText()).length() > 0) {
                new HoloConfirmDialog(owner, "", "临时保存一下，确认吗？", "取消", "确认",
                        () -> {
                            saveAct("reg0", et_Action.getText());   // 暂存当前编辑区内容
                            MyToast.showToast(this, "成功储存！", MyToast.LENGTH_SHORT);
                        }).setVisible(true);
            } else {
                MyToast.showToast(this, "没有东西可存啊！", MyToast.LENGTH_SHORT);
            }
        });

        // 加载
        btLoadAct.addActionListener(e -> {
            if (flg && nonBlank(et_Action.getText()).length() > 0) {   // 求解前，若编辑过则提示保存
                HoloConfirmDialog.askSave(owner, "内容有修改，是否暂存一下？",
                        this::myLoad,
                        () -> {
                            saveAct("reg0", et_Action.getText());  // 暂存当前编辑区内容
                            myLoad();
                        });
            } else {
                myLoad();
            }
        });

        // 存入（寄存）
        btSaveAct.addActionListener(e -> HoloChoiceDialog.clickToPick(owner, "保存到", SAVE_MENU,
                this::onSavePick).setVisible(true));

        // 清空动作
        btClear.addActionListener(e -> {
            et_Action.setText("");
            flg = false;                          // 编辑框内容是否改动
        });

        // 执行动作
        btDO.addActionListener(e -> doExecute());
    }

    /** 原版「保存到」单击分支（原版 224~262 行）。 */
    private void onSavePick(int which) {
        switch (which) {
            case 0:  // 保存：宏
                if (is_BK) {                      // 逆推
                    MyToast.showToast(this, "逆推不支持宏功能！", MyToast.LENGTH_SHORT);
                } else {
                    saveMacroFile();
                }
                break;
            case 1:  // 送入：剪切板
                myMaps.saveClipper(et_Action.getText());
                break;
            default: // 下面为送入各寄存器 reg1..reg9
                saveAct("reg" + (which - 1), et_Action.getText());
                break;
        }
    }

    /** 原版 {@code myLoad()}：标题「加载」的 14 项单选。 */
    private void myLoad() {
        HoloChoiceDialog.clickToPick(owner, "加载", LOAD_MENU, this::onLoadPick).setVisible(true);
    }

    /** 原版「加载」单击分支（原版 356~469 行）。 */
    private void onLoadPick(int which) {
        switch (which) {
            case 0:  // 读取：宏
                if (is_BK) {                      // 逆推
                    MyToast.showToast(this, "逆推不支持宏功能！", MyToast.LENGTH_SHORT);
                } else {
                    m_nItemSelect = -1;
                    myMaps.mMacroList();
                    if (myMaps.mFile_List.size() > 0) {
                        String[] items = myMaps.mFile_List.toArray(new String[0]);
                        HoloChoiceDialog.selectThenOk(owner, "读入：宏", null, items, sel -> {
                            m_nItemSelect = sel;
                            et_PreEdit.setSelected(false);
                            et_Action.setText(myMaps.readMacroFile(myMaps.mFile_List.get(sel)));
                            flg = false;
                        }).setVisible(true);
                    } else {
                        MyToast.showToast(this, "请将宏文档复制到“宏/”文件夹下", MyToast.LENGTH_SHORT);
                    }
                }
                break;
            case 1:  // 读取：已做动作
                et_PreEdit.setSelected(false);
                et_Action.setText(loadAct("act1"));
                flg = false;
                break;
            case 2:  // 读取：后续动作
                et_PreEdit.setSelected(false);
                et_Action.setText(loadAct("act2"));
                flg = false;
                break;
            case 3:  // 读取：文档
                m_nItemSelect = -1;
                myMaps.newSetList();
                if (myMaps.mFile_List.size() > 0) {
                    String[] items = myMaps.mFile_List.toArray(new String[0]);
                    HoloChoiceDialog.selectThenOk(owner, "文档：导入", null, items, sel -> {
                        et_PreEdit.setSelected(false);
                        et_Action.setText(readFile(myMaps.mFile_List.get(sel)));
                        flg = false;
                    }).setVisible(true);
                } else {
                    MyToast.showToast(this, "请将文档复制到“导入/”文件夹下", MyToast.LENGTH_SHORT);
                }
                break;
            case 4:  // 读取：剪切板（注意：原版这里**没有**过 loadLURD）
                et_PreEdit.setSelected(false);
                et_Action.setText(myMaps.loadClipper());
                flg = false;
                break;
            default: // 下面为读取各寄存器 reg1..reg9
                et_PreEdit.setSelected(false);
                et_Action.setText(loadAct("reg" + (which - 4)));
                flg = false;
                break;
        }
    }

    /** 原版 {@code btDO} 的点击逻辑（原版 280~332 行）。 */
    private void doExecute() {
        String actStr = et_Action.getText();

        // 判断 name 中是否包含字母
        if (is_BK && !myMaps.isLURD2(actStr)) {   // 逆推
            MyToast.showToast(this, "逆推请使用标准动作字符！", MyToast.LENGTH_SHORT);
            return;
        }

        myMaps.m_ActionIsRedy = true;
        // 对于非“宏”，自动加上忽略大小写命令
        if (myMaps.isLURD(actStr)) {
            myMaps.sAction = new String[]{"{" + actStr + "}~"};
        } else {
            myMaps.sAction = actStr.split("\n|\r|\n\r|\r\n|\\|");
        }

        // 提前去掉注释行和两端的空格及制表符，方便以后处理
        int len = myMaps.sAction.length;
        int w, w1, w2;
        String str;
        for (int k = 0; k < len; k++) {
            if (myMaps.sAction[k].trim().isEmpty()) {
                myMaps.sAction[k] = "";
                continue;
            } else {
                str = myMaps.qj2bj(myMaps.sAction[k]).trim();   // 全角转换成半角
            }
            w = str.indexOf('<');
            if (w >= 0) {
                w1 = str.indexOf(';');
                if (w1 >= 0 && w1 < w) {
                    w = w1;                                     // 若注释符号在行内块之前
                } else {
                    w2 = str.indexOf('>');
                    w = str.indexOf(';', w2);                   // 行内块后面的注释符号
                }
            } else {
                w = str.indexOf(';');
            }

            if (w > 0) {
                myMaps.sAction[k] = str.substring(0, w).replaceAll("[\t]", " ").trim();
            } else if (w == 0) {
                myMaps.sAction[k] = "";
            } else {
                myMaps.sAction[k] = str;
            }
        }

        if (flg && nonBlank(actStr).length() > 0) {
            showSavePrompt();
        } else {
            dispose();
        }
    }

    // ================================================================ ActionBar 动作

    /** 菜单「录制动作」（原版 798~802 行）。 */
    private void onRecording() {
        myMaps.isRecording = true;                // 开启录制模式
        if (flg && nonBlank(et_Action.getText()).length() > 0) {
            showSavePrompt();
        } else {
            dispose();
        }
    }

    /** 返回键 / {@code android.R.id.home}（原版 670~674 行）。 */
    private void onHome() {
        if (flg && nonBlank(et_Action.getText()).length() > 0) {
            showSavePrompt();
        } else {
            dispose();
        }
    }

    /** 原版那个常驻的 {@code isSaveDlg}：取消 / 否 / 是。 */
    private void showSavePrompt() {
        HoloConfirmDialog.askSave(owner, "内容有修改，是否暂存一下？",
                this::dispose,
                () -> {
                    saveAct("reg0", et_Action.getText());   // 暂存当前编辑区内容
                    dispose();
                });
    }

    /**
     * 动作变换（原版 698~797 行）。5 个 case 的字符映射逐一照抄，
     * 注意「左右翻转」保留 u/d 不变、「上下翻转」保留 l/r 不变。
     */
    public void transformLURD(String type) {
        String text = et_Action.getText();
        if (!myMaps.isLURD(text)) {
            MyToast.showToast(this, "仅支持规范的动作字符！", MyToast.LENGTH_SHORT);
            return;
        }

        Matcher m = LURD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String ch = m.group();
            String rep = ch;
            switch (type) {
                case "L90":   // 左旋90度
                    if ("l".equals(ch)) rep = "d";
                    else if ("L".equals(ch)) rep = "D";
                    else if ("u".equals(ch)) rep = "l";
                    else if ("U".equals(ch)) rep = "L";
                    else if ("r".equals(ch)) rep = "u";
                    else if ("R".equals(ch)) rep = "U";
                    else if ("d".equals(ch)) rep = "r";
                    else if ("D".equals(ch)) rep = "R";
                    break;
                case "R90":   // 右旋90度
                    if ("l".equals(ch)) rep = "u";
                    else if ("L".equals(ch)) rep = "U";
                    else if ("u".equals(ch)) rep = "r";
                    else if ("U".equals(ch)) rep = "R";
                    else if ("r".equals(ch)) rep = "d";
                    else if ("R".equals(ch)) rep = "D";
                    else if ("d".equals(ch)) rep = "l";
                    else if ("D".equals(ch)) rep = "L";
                    break;
                case "180":   // 右旋180度
                    if ("l".equals(ch)) rep = "r";
                    else if ("L".equals(ch)) rep = "R";
                    else if ("u".equals(ch)) rep = "d";
                    else if ("U".equals(ch)) rep = "D";
                    else if ("r".equals(ch)) rep = "l";
                    else if ("R".equals(ch)) rep = "L";
                    else if ("d".equals(ch)) rep = "u";
                    else if ("D".equals(ch)) rep = "U";
                    break;
                case "LR":    // 左右翻转
                    if ("l".equals(ch)) rep = "r";
                    else if ("L".equals(ch)) rep = "R";
                    else if ("r".equals(ch)) rep = "l";
                    else if ("R".equals(ch)) rep = "L";
                    break;
                case "UD":    // 上下翻转
                    if ("u".equals(ch)) rep = "d";
                    else if ("U".equals(ch)) rep = "D";
                    else if ("d".equals(ch)) rep = "u";
                    else if ("D".equals(ch)) rep = "U";
                    break;
                default:
                    break;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        et_Action.setText(sb.toString());
    }

    // ================================================================ 寄存器 / 文档

    /**
     * 读入寄存器。原版是 {@code SharedPreferences("BoxMan")}，
     * PC 侧沿用 {@code myGameView} 已有的 {@code [Action]} 段（{@code BoxMan.ini}）。
     */
    private String loadAct(String name) {
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            if (!f.exists()) return "";
            IniFile ini = new IniFile(f);
            Object val = ini.get("Action", name, "");
            return val != null ? val.toString() : "";
        } catch (Throwable ex) {
            return "";
        }
    }

    /** 保存到寄存器（原版 486~502 行）。 */
    private void saveAct(String name, String value) {
        if (myMaps.isRecording && value.isEmpty()) {   // 录制模式下的清理动作
            // 不提示
        } else if (!name.equals("reg0") && !myMaps.isLURD(value)) {
            MyToast.showToast(this, "遇到无效字符，保存失败!", MyToast.LENGTH_SHORT);
            return;
        }
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            IniFile ini = new IniFile(f);
            ini.set("Action", name, value);
            ini.save(f);
            if (myMaps.isRecording && value.isEmpty()) {   // 录制模式下的清理动作
                // 不提示
            } else {
                MyToast.showToast(this, "已存储！", MyToast.LENGTH_SHORT);
            }
            flg = false;                                  // 编辑框内容是否改动
        } catch (Exception e) {
            // 原版这里也是空 catch
        }
    }

    /** 从「导入/」文档读入 Lurd（原版 505~522 行）。 */
    private String readFile(String fn) {
        try {
            String my_Name = myMaps.sRoot + myMaps.sPath + "导入/" + fn;
            byte[] buf = new byte[(int) new File(my_Name).length()];
            try (FileInputStream fin = new FileInputStream(my_Name)) {
                int off = 0;
                while (off < buf.length) {
                    int n = fin.read(buf, off, buf.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            return myMaps.loadLURD(new String(buf, StandardCharsets.UTF_8), 0);
        } catch (Exception e) {
            MyToast.showToast(this, "文档中的数据无效！", MyToast.LENGTH_SHORT);
        }
        return "";
    }

    /** 保存“宏”到文档（原版 525~593 行）。 */
    private void saveMacroFile() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");   // 设置日期格式
        final String fn = "M_" + df.format(new Date()) + ".txt";

        // 原版 setView(new EditText)：底色 0xff444444
        final JTextField et = HoloContent.field(180, fn, new Color(0x44, 0x44, 0x44), HoloContent.TEXT);

        myMaps.mMacroList();
        myMaps.mFile_List.add(0, "自动名称");

        String[] items = myMaps.mFile_List.toArray(new String[0]);
        HoloChoiceDialog d = new HoloChoiceDialog(owner, "“宏”名称", HoloContent.row(et), items);
        d.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int which = d.list.getSelectedIndex();
            et.setText(which > 0 ? myMaps.mFile_List.get(which) : fn);
        });
        d.addButton("取消", d::dispose);
        JButton ok = d.addButton("确定", () -> {
            d.dispose();
            writeMacroFile(et.getText());
        });
        d.setDefaultButton(ok);
        d.setVisible(true);
    }

    /** 原版 {@code saveMacroFile()} 的「确定」分支。 */
    private void writeMacroFile(String rawName) {
        try {
            File targetDir = new File(myMaps.sRoot + myMaps.sPath + "宏/");
            myMaps.mFile_List.clear();
            if (!targetDir.exists()) targetDir.mkdirs();   // 创建"宏/"文件夹

            String str = rawName.trim();
            String prefix = str.substring(str.lastIndexOf(".") + 1);
            if (!prefix.equalsIgnoreCase("txt")) {
                str = str + ".txt";
            }
            final String my_Name = myMaps.sRoot + myMaps.sPath + "宏/" + str;

            File file = new File(my_Name);
            if (file.exists()) {
                new HoloConfirmDialog(owner, "", "文档已存在，覆写吗？\n宏/" + str,
                        "取消", "覆写", () -> writeMacroBytes(my_Name)).setVisible(true);
            } else {
                writeMacroBytes(my_Name);
            }
        } catch (Exception e) {
            MyToast.showToast(this, "写错误，保存失败！", MyToast.LENGTH_SHORT);
        }
    }

    /** 原版用 {@code getBytes()}（安卓默认 UTF-8），这里显式指定 UTF-8。 */
    private void writeMacroBytes(String my_Name) {
        try (FileOutputStream fout = new FileOutputStream(my_Name)) {
            fout.write(et_Action.getText().getBytes(StandardCharsets.UTF_8));
            fout.flush();
            MyToast.showToast(this, "保存成功！", MyToast.LENGTH_SHORT);
        } catch (Exception e) {
            MyToast.showToast(this, "写错误，保存失败！", MyToast.LENGTH_SHORT);
        }
    }

    // ================================================================ 工具

    /** 原版 {@code replaceAll("[ \n\r\t]", "")}。 */
    private static String nonBlank(String s) {
        return s == null ? "" : s.replaceAll("[ \n\r\t]", "");
    }

    // ================================================================ 测试钩子

    public myActionBar getActionBar() { return actionBar; }

    public boolean isBackward() { return is_BK; }

    public boolean isDirty() { return flg; }

    public void setDirty(boolean dirty) { this.flg = dirty; }

    /** 供测试直接触发菜单项。 */
    public void clickMenu(String title) {
        switch (title) {
            case "录制动作": onRecording(); break;
            case "左旋90度（Lurd）": transformLURD("L90"); break;
            case "右旋90度（Lurd）": transformLURD("R90"); break;
            case "右旋180度（Lurd）": transformLURD("180"); break;
            case "左右翻转（Lurd）": transformLURD("LR"); break;
            case "上下翻转（Lurd）": transformLURD("UD"); break;
            case "“宏”功能说明": new Help(5).setVisible(true); break;
            case "“导入”说明": new Help(4).setVisible(true); break;
            default: break;
        }
    }

    public String loadActForTest(String name) { return loadAct(name); }

    public void saveActForTest(String name, String value) { saveAct(name, value); }

    public void onLoadPickForTest(int which) { onLoadPick(which); }

    public void onSavePickForTest(int which) { onSavePick(which); }

    public void doExecuteForTest() { doExecute(); }

    public String readFileForTest(String fn) { return readFile(fn); }

    public void writeMacroForTest(String rawName) { writeMacroFile(rawName); }
}
