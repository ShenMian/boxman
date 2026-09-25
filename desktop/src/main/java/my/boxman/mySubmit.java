package my.boxman;

import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.UiWindow;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 比赛答案提交（原版 {@code mySubmit} Activity，475 行）。
 *
 * <p>入口：{@code myStateBrow} 上下文菜单的「提交答案（sokoban.cn）」
 * （原版 {@code myStateBrow.java:407 case 11}）——会先把选中的状态读进
 * {@code myMaps.m_State}，再打开本窗口。
 *
 * <p>界面照 {@code res/layout/submit.xml} 逐块还原（1dp = 1px）：
 * <pre>
 *   30dp 留白
 *   国家/地区:  [220dp 下拉框]        （gravity=right, paddingRight 16dp）
 *   16dp 留白
 *   姓名:       [220dp 输入框]
 *   16dp 留白
 *   Email:      [220dp 输入框]
 *   30dp 留白
 *   4dp 分隔条 #303030
 *   16dp 留白
 *   [返回] 50dp [提交] 30dp           （gravity=right）
 *   16dp 留白
 * </pre>
 * 底色一律 {@code #363636}（{@link HoloContent#BAND}）。
 *
 * <p>提交协议（原版 {@code send()} 直译）：{@code POST {myMaps.uil}submit_result.php}，
 * body 为 {@code nickname/country/email/lurd} 四个参数，<b>强制 GBK 编码</b>
 * （服务器按 GB2312 接收）。响应按关键字判定：
 * {@code correct (for } / {@code not correct} / {@code competition has ended} /
 * {@code not begin yet} / {@code name cannot be empty}，其余为「未知情况」。
 *
 * <p>与原版一致：提交前不做本地校验（空姓名由服务器回 {@code name cannot be empty}），
 * 提交在后台线程进行，成功后跳到「比赛答案提交列表」并关闭本窗口。
 */
public class mySubmit extends JFrame {

    /** 国家/地区表：原版 {@code mySubmit.m_menu} 逐项直译（{code, 显示名}），共 242 项。 */
    static final String[][] COUNTRY = {
            //国家
            {"00", "*** Other ***"},
            {"CN", "中国"},
            {"AF", "Afghanistan"},
            {"AL", "Albania"},
            {"DZ", "Algeria"},
            {"AS", "American Samoa"},
            {"AD", "Andorra"},
            {"AO", "Angola"},
            {"AI", "Anguilla"},
            {"AQ", "Antarctica"},
            {"AG", "Antigua and Barbuda"},
            {"AR", "Argentina"},
            {"AM", "Armenia"},
            {"AW", "Aruba"},
            {"AU", "Australia"},
            {"AT", "Austria"},
            {"AZ", "Azerbaijan"},
            {"BS", "Bahamas"},
            {"BH", "Bahrain"},
            {"BD", "Bangladesh"},
            {"BB", "Barbados"},
            {"BY", "Belarus"},
            {"BE", "Belgium"},
            {"BZ", "Belize"},
            {"BJ", "Benin"},
            {"BM", "Bermuda"},
            {"BT", "Bhutan"},
            {"BO", "Bolivia"},
            {"BA", "Bosnia and Herzegovina"},
            {"BW", "Botswana"},
            {"BV", "Bouvet Island"},
            {"BR", "Brazil"},
            {"IO", "British Indian Ocean Territory"},
            {"VG", "British Virgin Islands"},
            {"BN", "Brunei Darussalam"},
            {"BG", "Bulgaria"},
            {"BF", "Burkina Faso"},
            {"MM", "Burma"},
            {"BI", "Burundi"},
            {"KH", "Cambodia"},
            {"CM", "Cameroon"},
            {"CA", "Canada"},
            {"CV", "Cape Verde"},
            {"KY", "Cayman Islands"},
            {"CF", "Central African Republic"},
            {"TD", "Chad"},
            {"CL", "Chile"},
            {"CN", "China"},
            {"CX", "Christmas Island"},
            {"CC", "Cocos (Keeling) Islands"},
            {"CO", "Colombia"},
            {"KM", "Comoros"},
            {"CD", "Congo, Democratic Republic of the"},
            {"CG", "Congo, Republic of the"},
            {"CK", "Cook Islands"},
            {"CR", "Costa Rica"},
            {"CI", "Cote d'Ivoire (Ivory Coast)"},
            {"HR", "Croatia"},
            {"CU", "Cuba"},
            {"CY", "Cyprus"},
            {"CZ", "Czech Republic"},
            {"DK", "Denmark"},
            {"DJ", "Djibouti"},
            {"DM", "Dominica"},
            {"DO", "Dominican Republic"},
            {"TP", "East Timor"},
            {"EC", "Ecuador"},
            {"EG", "Egypt"},
            {"SV", "El Salvador"},
            {"GQ", "Equatorial Guinea"},
            {"ER", "Eritrea"},
            {"EE", "Estonia"},
            {"ET", "Ethiopia"},
            {"FK", "Falkland Islands (Islas Malvinas)"},
            {"FO", "Faroe Islands"},
            {"FJ", "Fiji"},
            {"FI", "Finland"},
            {"FR", "France"},
            {"GF", "French Guiana"},
            {"PF", "French Polynesia"},
            {"GA", "Gabon"},
            {"GM", "Gambia"},
            {"GE", "Georgia"},
            {"DE", "Germany"},
            {"GH", "Ghana"},
            {"GI", "Gibraltar"},
            {"GR", "Greece"},
            {"GL", "Greenland"},
            {"GD", "Grenada"},
            {"GP", "Guadeloupe"},
            {"GU", "Guam"},
            {"GT", "Guatemala"},
            {"GG", "Guernsey"},
            {"GN", "Guinea"},
            {"GW", "Guinea-Bissau"},
            {"GY", "Guyana"},
            {"HT", "Haiti"},
            {"VA", "Holy See (Vatican City)"},
            {"HN", "Honduras"},
            {"HK", "Hong Kong"},
            {"HU", "Hungary"},
            {"IS", "Iceland"},
            {"IN", "India"},
            {"ID", "Indonesia"},
            {"IR", "Iran"},
            {"IQ", "Iraq"},
            {"IE", "Ireland"},
            {"IL", "Israel"},
            {"IT", "Italy"},
            {"JM", "Jamaica"},
            {"JP", "Japan"},
            {"JE", "Jersey"},
            {"JO", "Jordan"},
            {"KZ", "Kazakhstan"},
            {"KE", "Kenya"},
            {"KI", "Kiribati"},
            {"KP", "Korea, North"},
            {"KR", "Korea, South"},
            {"KW", "Kuwait"},
            {"KG", "Kyrgyzstan"},
            {"LA", "Laos"},
            {"LV", "Latvia"},
            {"LB", "Lebanon"},
            {"LS", "Lesotho"},
            {"LR", "Liberia"},
            {"LY", "Libya"},
            {"LI", "Liechtenstein"},
            {"LT", "Lithuania"},
            {"LU", "Luxembourg"},
            {"MO", "Macao"},
            {"MK", "Macedonia, The Former Yugoslav Republic of"},
            {"MG", "Madagascar"},
            {"MW", "Malawi"},
            {"MY", "Malaysia"},
            {"MV", "Maldives"},
            {"ML", "Mali"},
            {"MT", "Malta"},
            {"IM", "Man, Isle of"},
            {"MH", "Marshall Islands"},
            {"MQ", "Martinique"},
            {"MR", "Mauritania"},
            {"MU", "Mauritius"},
            {"YT", "Mayotte"},
            {"MX", "Mexico"},
            {"FM", "Micronesia, Federated States of"},
            {"MD", "Moldova"},
            {"MC", "Monaco"},
            {"MN", "Mongolia"},
            {"ME", "Montenegro"},
            {"MS", "Montserrat"},
            {"MA", "Morocco"},
            {"MZ", "Mozambique"},
            {"NA", "Namibia"},
            {"NR", "Nauru"},
            {"NP", "Nepal"},
            {"NL", "Netherlands"},
            {"AN", "Netherlands Antilles"},
            {"NC", "New Caledonia"},
            {"NZ", "New Zealand"},
            {"NI", "Nicaragua"},
            {"NE", "Niger"},
            {"NG", "Nigeria"},
            {"NU", "Niue"},
            {"NF", "Norfolk Island"},
            {"MP", "Northern Mariana Islands"},
            {"NO", "Norway"},
            {"OM", "Oman"},
            {"PK", "Pakistan"},
            {"PW", "Palau"},
            {"PS", "Palestinian Territory, Occupied"},
            {"PA", "Panama"},
            {"PG", "Papua New Guinea"},
            {"PY", "Paraguay"},
            {"PE", "Peru"},
            {"PH", "Philippines"},
            {"PN", "Pitcairn Islands"},
            {"PL", "Poland"},
            {"PT", "Portugal"},
            {"PR", "Puerto Rico"},
            {"QA", "Qatar"},
            {"RE", "Réunion"},
            {"RO", "Romania"},
            {"RU", "Russia"},
            {"RW", "Rwanda"},
            {"SH", "Saint Helena"},
            {"KN", "Saint Kitts and Nevis"},
            {"LC", "Saint Lucia"},
            {"PM", "Saint Pierre and Miquelon"},
            {"VC", "Saint Vincent and the Grenadines"},
            {"WS", "Samoa"},
            {"SM", "San Marino"},
            {"ST", "São Tomé and Príncipe"},
            {"SA", "Saudi Arabia"},
            {"SN", "Senegal"},
            {"RS", "Serbia"},
            {"SC", "Seychelles"},
            {"SL", "Sierra Leone"},
            {"SG", "Singapore"},
            {"SK", "Slovakia"},
            {"SI", "Slovenia"},
            {"SB", "Solomon Islands"},
            {"SO", "Somalia"},
            {"ZA", "South Africa"},
            {"GS", "South Georgia and the South Sandwich Islands"},
            {"ES", "Spain"},
            {"LK", "Sri Lanka"},
            {"SD", "Sudan"},
            {"SR", "Suriname"},
            {"SJ", "Svalbard"},
            {"SZ", "Swaziland"},
            {"SE", "Sweden"},
            {"CH", "Switzerland"},
            {"SY", "Syria"},
            {"TW", "Taiwan"},
            {"TJ", "Tajikistan"},
            {"TZ", "Tanzania"},
            {"TH", "Thailand"},
            {"TG", "Togo"},
            {"TK", "Tokelau"},
            {"TO", "Tonga"},
            {"TT", "Trinidad and Tobago"},
            {"TN", "Tunisia"},
            {"TR", "Turkey"},
            {"TM", "Turkmenistan"},
            {"TC", "Turks and Caicos Islands"},
            {"TV", "Tuvalu"},
            {"UG", "Uganda"},
            {"UA", "Ukraine"},
            {"AE", "United Arab Emirates"},
            {"UK", "United Kingdom"},
            {"US", "United States"},
            {"UY", "Uruguay"},
            {"UZ", "Uzbekistan"},
            {"VU", "Vanuatu"},
            {"VE", "Venezuela"},
            {"VN", "Vietnam"},
            {"VI", "Virgin Islands"},
            {"WF", "Wallis and Futuna"},
            {"EH", "Western Sahara"},
            {"YE", "Yemen"},
            {"ZM", "Zambia"},
            {"ZW", "Zimbabwe"}
    };

    /** 输入框 / 下拉框宽度（submit.xml 里写死的 220dp） */
    private static final int FIELD_WIDTH = 220;
    /** 表单行距右边缘的留白（submit.xml 的 paddingRight="16dp"） */
    private static final int ROW_RIGHT_PAD = 16;
    /** 按钮字号：原版 {@code android:textSize="10pt"} ≈ 13dp */
    private static final int BUTTON_TEXT_SIZE = 13;

    /** 原版 {@code m_Sel_id}：当前选中的国家/地区在 {@link #COUNTRY} 中的下标 */
    int m_Sel_id;

    private myActionBar actionBar;
    JComboBox<String> spCountry;
    JTextField tfId;
    JTextField tfEmail;
    JButton btOK;
    JButton btCancel;

    /** 提交是否正在进行（原版靠「提交在独立线程里跑」，这里用来防重复点击） */
    private boolean submitting;

    public mySubmit() {
        setTitle("比赛答案提交 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(HoloContent.BAND);

        // 原版：setTitle + setDisplayHomeAsUpEnabled(true) + setDisplayShowHomeEnabled(false)
        actionBar = new myActionBar();
        actionBar.setBarTitle("比赛答案提交");
        actionBar.setUpEnabled(true, this::dispose);
        add(actionBar, BorderLayout.NORTH);

        add(buildForm(), BorderLayout.CENTER);
    }

    /** 快照 / 测试用：取出标题栏（原版 {@code getActionBar()}）。 */
    myActionBar getActionBar() {
        return actionBar;
    }

    /** 照 submit.xml 自上而下拼装。 */
    private JComponent buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(HoloContent.BAND);

        form.add(HoloContent.band(HoloContent.BAND, 30));

        // 国家/地区
        spCountry = new JComboBox<>(countryNames().toArray(new String[0]));
        spCountry.setFont(new Font(Font.MONOSPACED, Font.PLAIN, HoloContent.TEXT_SIZE));  // typeface="monospace"
        spCountry.setBackground(HoloContent.FIELD_BG);
        spCountry.setForeground(HoloContent.TEXT);
        spCountry.setPreferredSize(new Dimension(FIELD_WIDTH, spCountry.getPreferredSize().height));
        spCountry.setMaximumSize(new Dimension(FIELD_WIDTH, spCountry.getPreferredSize().height));
        m_Sel_id = indexOfCountry(myMaps.country);
        spCountry.setSelectedIndex(m_Sel_id);
        form.add(rightRow(HoloContent.label("国家/地区:"), spCountry));

        form.add(HoloContent.band(HoloContent.BAND, 16));

        // 姓名
        tfId = HoloContent.field(FIELD_WIDTH, myMaps.nickname);
        form.add(rightRow(HoloContent.label("姓名:"), tfId));

        form.add(HoloContent.band(HoloContent.BAND, 16));

        // Email
        tfEmail = HoloContent.field(FIELD_WIDTH, myMaps.email);
        form.add(rightRow(HoloContent.label("Email:"), tfEmail));

        form.add(HoloContent.band(HoloContent.BAND, 30));
        form.add(HoloContent.band(new Color(0x30, 0x30, 0x30), 4));   // 4dp 分隔条
        form.add(HoloContent.band(HoloContent.BAND, 16));

        // 按钮行：返回 + 50dp + 提交 + 30dp，整体右对齐
        // 原版 submit.xml 的两个 <Button> 是主题默认的 Widget.Holo.Button
        // （textSize=10pt、paddingLeft=10dp、paddingTop/Bottom=5dp）
        btCancel = HoloButton.create("返回", BUTTON_TEXT_SIZE, BUTTON_PADDING);
        btCancel.addActionListener(e -> dispose());
        btOK = HoloButton.create("提交", BUTTON_TEXT_SIZE, BUTTON_PADDING);
        btOK.addActionListener(e -> onOk());

        JPanel btnRow = new JPanel();
        btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
        btnRow.setBackground(HoloContent.BAND);
        btnRow.setOpaque(true);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, ROW_RIGHT_PAD));
        btnRow.add(Box.createHorizontalGlue());
        btnRow.add(btCancel);
        btnRow.add(Box.createRigidArea(new Dimension(50, 0)));
        btnRow.add(btOK);
        btnRow.add(Box.createRigidArea(new Dimension(30, 0)));
        form.add(btnRow);

        form.add(HoloContent.band(HoloContent.BAND, 16));
        return form;
    }

    /**
     * 原版这些行都是 {@code gravity="right" + paddingRight="16dp"}，
     * 所以是「整体靠右」，而不是 {@link HoloContent#row} 那种居中。
     */
    private static JPanel rightRow(Component... children) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(HoloContent.BAND);
        p.setOpaque(true);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, ROW_RIGHT_PAD));
        p.add(Box.createHorizontalGlue());
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                p.add(Box.createRigidArea(new Dimension(4, 0)));   // TextView 的 padding 4dp
            }
            p.add(children[i]);
        }
        return p;
    }

    /**
     * 原版按钮：{@code textSize=10pt}、{@code paddingLeft=10dp}、{@code paddingTop/Bottom=5dp}。
     * 现在由 {@link HoloButton#create(String, int, Insets)} 一并处理（含 Holo 深色底图）。
     */
    static final Insets BUTTON_PADDING = new Insets(5, 10, 5, 10);

    /** 原版 {@code getData(m_menu)}：把国家表取成显示名列表。 */
    static List<String> countryNames() {
        List<String> list = new ArrayList<>();
        for (String[] row : COUNTRY) {
            list.add(row[1]);
        }
        return list;
    }

    /**
     * 原版 {@code onCreate} 里「按 {@code myMaps.country} 反查下拉框下标」的那段循环。
     *
     * <p>注意原版用 {@code break} 命中第一个匹配就停 —— 表里有重复代码
     * （{@code CN} 出现两次：第 2 项「中国」和第 48 项「China」），
     * 所以 {@code CN} 永远命中第 2 项。找不到时返回 0（{@code *** Other ***}），与原版初值一致。
     */
    static int indexOfCountry(String code) {
        for (int k = 0; k < COUNTRY.length; k++) {
            if (COUNTRY[k][0].equals(code)) {
                return k;
            }
        }
        return 0;
    }

    /** 原版 {@code bt_OK.setOnClickListener} 的逻辑。 */
    private void onOk() {
        if (submitting) {
            return;
        }
        myMaps.nickname = tfId.getText().trim();
        myMaps.email = tfEmail.getText().trim();
        myMaps.country = COUNTRY[spCountry.getSelectedIndex()][0];
        submitting = true;
        btOK.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return send();
            }

            @Override
            protected void done() {
                submitting = false;
                btOK.setEnabled(true);
                String message;
                boolean ok;
                try {
                    String raw = get();
                    ok = raw.startsWith(OK_PREFIX);
                    message = ok ? raw.substring(OK_PREFIX.length()) : raw;
                } catch (Exception e) {
                    ok = false;
                    message = "网络或读写错误！";
                }
                onSubmitted(ok, message);
            }
        }.execute();
    }

    /** 提交结果处理：成功 → 打开提交列表并关闭；失败 → 弹「错误」对话框。 */
    void onSubmitted(boolean ok, String message) {
        if (ok) {
            new mySubmitList().setVisible(true);
            dispose();
        } else {
            // 原版：AlertDialog(THEME_HOLO_DARK) title="错误"，setCancelable(false)
            new HoloMessageDialog(this, "错误", message, "确定").setVisible(true);
        }
    }

    /** 成功标记：{@link #send()} 用它把「成功」与「失败文案」区分开 */
    private static final String OK_PREFIX = "\u0000OK\u0000";

    /**
     * 原版 {@code send()}：POST 到 {@code {myMaps.uil}submit_result.php}。
     *
     * <p>返回值形如 {@link #OK_PREFIX}+文案（成功）或纯文案（失败）。
     */
    String send() {
        String result = "";
        HttpURLConnection urlConn = null;
        try {
            URL url = new URL(myMaps.uil + "submit_result.php");
            urlConn = (HttpURLConnection) url.openConnection();
            urlConn.setRequestMethod("POST");
            urlConn.setDoInput(true);
            urlConn.setDoOutput(true);
            urlConn.setUseCaches(false);
            urlConn.setInstanceFollowRedirects(true);
            urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String ans = myMaps.m_State == null ? "" : myMaps.m_State.ans;
            // 原版强制 GBK（服务器按 GB2312 接收）
            String param = "nickname=" + URLEncoder.encode(myMaps.nickname, "GBK")
                    + "&country=" + URLEncoder.encode(myMaps.country, "GBK")
                    + "&email=" + URLEncoder.encode(myMaps.email, "GBK")
                    + "&lurd=" + URLEncoder.encode(ans, "GBK");

            DataOutputStream out = new DataOutputStream(urlConn.getOutputStream());
            out.writeBytes(param);
            out.flush();
            out.close();

            int responseCode = urlConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader buffer = new BufferedReader(
                        new InputStreamReader(urlConn.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = buffer.readLine()) != null) {
                        sb.append(inputLine).append('\n');
                    }
                }
                result = sb.toString();
                return interpret(result);
            }
            return "网络错误，请与网站管理员或程序员联系！";
        } catch (IOException e) {
            return "网络或读写错误！";
        } finally {
            if (urlConn != null) {
                urlConn.disconnect();
            }
        }
    }

    /**
     * 把服务器响应按关键字翻译成提示文案（原版 {@code send()} 里的 if-else 链）。
     *
     * <p>抽成静态方法是为了能在不联网的情况下做单元测试。
     * 关键字判定**顺序与原版保持一致**：先 {@code correct (for }，再 {@code not correct}……
     * 顺序会改变结果（例如响应同时含 {@code not correct (for ...} 时原版会判成成功），
     * 这里不「顺手修正」，照原样保留。
     *
     * @return {@link #OK_PREFIX}+文案（成功），或纯文案（失败）
     */
    static String interpret(String result) {
        String lower = result == null ? "" : result.toLowerCase();
        if (lower.contains("correct (for ")) {
            return OK_PREFIX + "提交成功！";
        } else if (lower.contains("not correct")) {
            return "答案不正确！";
        } else if (lower.contains("competition has ended")) {
            return "比赛已过期，请关注下一期！";
        } else if (lower.contains("not begin yet")) {
            return "比赛尚未开始，请耐心等待！";
        } else if (lower.contains("name cannot be empty")) {
            return "姓名不能空着！";
        }
        return "未知情况！";
    }
}
