package my.boxman;

import my.boxman.compat.HoloContent;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.awt.Color;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 收尾批次 —— 「窗口主题」与 {@code myExport} 逐行复核后的行为还原。
 *
 * <h3>① 窗口主题</h3>
 * 原版 {@code Theme.Holo}（深色）的 {@code windowBackground} 是
 * {@code @color/background_holo_dark = #FF000000}（纯黑）。{@code help.xml} /
 * {@code about.xml} / {@code export_view.xml} 的根节点都**没有** {@code android:background}，
 * 所以整屏透出的就是纯黑 —— 而 PC 侧 {@code myAbout / myAbout1 / myAbout2 / Help /
 * myExport} 早期用的是 Swing 默认浅灰。本用例把「内容区底色 = 纯黑」锁住。
 *
 * <h3>② {@code myExport} 的行为</h3>
 * 原版 {@code myExport.java:131-265} 的初始状态与复选框互斥逻辑，PC 早期用自造的
 * {@code updateContent()} 简化掉了（行数保留率仅 46% 就是这么来的）。本用例按原版
 * 逐条钉住：初值、XSB/Lurd 互斥、{@code 导出正推现场} 的禁用矩阵、{@code is_ANS} 文案。
 */
public class Phase41ExportAndWindowThemeTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(90);

    private static final String MAP = "#####\n#@$.#\n#####";
    private static final String SCENE = "SCENE-LOCAL";
    private static final String SCENE8 = "SCENE-ROTATED";

    private static String savedRoot;
    private static String savedPath;
    private static boolean savedIsXsb;
    private static boolean savedIsLurd;

    private final List<Window> toDispose = new ArrayList<Window>();

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        File home = new File(System.getProperty("user.dir"), "build/test_boxman_phase41");
        //noinspection ResultOfMethodCallIgnored
        home.mkdirs();
        myMaps.sRoot = home.getAbsolutePath();
        myMaps.sPath = "/";

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Before
    public void saveGlobals() {
        savedIsXsb = myMaps.isXSB;
        savedIsLurd = myMaps.isLurd;
    }

    @After
    public void tearDown() {
        for (Window w : toDispose) {
            w.dispose();
        }
        toDispose.clear();
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
        myMaps.isXSB = savedIsXsb;
        myMaps.isLurd = savedIsLurd;
    }

    private <T extends Window> T track(T w) {
        toDispose.add(w);
        return w;
    }

    // ============================================================ 1. 窗口主题

    @Test
    public void aboutAndHelpWindowsUseTheHoloDarkWindowBackground() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        assertWindowBackground("myAbout", track(new myAbout(null)));
        assertWindowBackground("Help", track(new Help(0)));

        List<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        assertFalse("应有至少一个关卡集", sets.isEmpty());
        long setId = sets.get(0).id;
        assertWindowBackground("myAbout1", track(new myAbout1(null, setId, "10/20")));

        mySQLite.m_SQL.get_Levels(setId);
        assertFalse("应有至少一关", myMaps.m_lstMaps.isEmpty());
        assertWindowBackground("myAbout2", track(new myAbout2(null, myMaps.m_lstMaps.get(0))));
    }

    @Test
    public void exportWindowUsesTheHoloDarkWindowBackground() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myMaps.curMap = new mapNode(MAP, "t", "a", "c");
        assertWindowBackground("myExport", track(new myExport()));
    }

    private static void assertWindowBackground(String name, Window w) {
        Container pane = w instanceof javax.swing.JFrame
                ? ((javax.swing.JFrame) w).getContentPane()
                : ((javax.swing.JDialog) w).getContentPane();
        assertEquals(name + " 的内容区底色应是 Theme.Holo 的 windowBackground（纯黑）",
                HoloContent.WINDOW_BG.getRGB(), pane.getBackground().getRGB());
        assertEquals("WINDOW_BG 应等于 @color/background_holo_dark（#FF000000）",
                Color.BLACK.getRGB(), HoloContent.WINDOW_BG.getRGB());
    }

    // ============================================================ 2. myExport —— 推关卡界面

    @Test
    public void pushExportWithAnswerStartsOnLurdAndFollowsTheOriginalToggles() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        myExport exp = track(new myExport(MAP, "rR", SCENE, SCENE8, true, 0, null, null, "test"));

        // 原版 myExport.java:154-160：有答案 → 初值只显示 Lurd，XSB 不勾但可勾
        assertEquals("初值应只有 Lurd", "rR", exp.et_Action.getText());
        assertFalse("有答案时 XSB 默认不勾选", exp.cb_XSB.isSelected());
        assertTrue("XSB 应可勾", exp.cb_XSB.isEnabled());
        assertTrue("Lurd 默认勾选", exp.cb_Lurd.isSelected());
        assertTrue("推关卡界面应显示「导出正推现场」那一行",
                exp.cb_Cur.getParent().isVisible());

        // 勾上 XSB → XSB + Solution 头 + Lurd（原版 :182-187）
        exp.cb_XSB.doClick();
        assertTrue("勾上 XSB 后应含关卡图", exp.et_Action.getText().contains(MAP));
        assertTrue("勾上 XSB 后应含 Solution 头", exp.et_Action.getText().contains("Solution"));
        assertTrue("勾上 XSB 后应含答案", exp.et_Action.getText().contains("rR"));

        // 勾「导出正推现场」→ 显示现场，并禁用 XSB/Lurd（原版 :226-234）
        exp.cb_Cur.doClick();
        assertEquals("勾现场后应显示 LOCAL 现场", SCENE, exp.et_Action.getText());
        assertFalse("勾现场后 XSB 应禁用", exp.cb_XSB.isEnabled());
        assertFalse("有答案时 Lurd 也应禁用", exp.cb_Lurd.isEnabled());
        assertTrue("Trun 应可用", exp.cb_Trun.isEnabled());

        // 勾「保持现场旋转」→ 显示 LOCAL8（原版 :256-264）
        exp.cb_Trun.doClick();
        assertEquals("勾旋转后应显示 LOCAL8 现场", SCENE8, exp.et_Action.getText());

        // 取消现场 → 恢复 XSB/Lurd，且 is_ANS 用 "Solution: " 前缀（原版 :235-252）
        exp.cb_Cur.doClick();
        assertTrue("取消现场后 XSB 应恢复可用", exp.cb_XSB.isEnabled());
        assertTrue("取消现场后 Lurd 应恢复可用", exp.cb_Lurd.isEnabled());
        assertFalse("取消现场后 Trun 应禁用", exp.cb_Trun.isEnabled());
        assertEquals("答案模式应拼成 XSB + 换行 + \"Solution: \" + Lurd",
                MAP + "\nSolution: rR", exp.et_Action.getText());
    }

    @Test
    public void pushExportWithoutAnswerPinsXsbOn() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        myExport exp = track(new myExport(MAP, "", SCENE, SCENE8, false, 0, null, null, ""));

        assertEquals("没有答案时应显示关卡图", MAP, exp.et_Action.getText());
        assertTrue("没有答案时 XSB 应勾选", exp.cb_XSB.isSelected());
        assertFalse("没有答案时 Lurd 应禁用", exp.cb_Lurd.isEnabled());
        assertTrue("没有答案时 XSB 应可勾（推关卡界面）", exp.cb_XSB.isEnabled());
    }

    // ============================================================ 3. myExport —— 浏览界面

    @Test
    public void browseExportHidesTheSceneRowAndPinsXsb() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        // my_Local == null 即「浏览界面的导出」；原版 my_AND 此时恒为 "\n"
        myExport exp = track(new myExport(MAP, "rR", null, "", true, 0, null, null, ""));

        assertEquals("浏览界面的导出应拼 XSB + \"\\n\" + Lurd（不带 Solution 头）",
                MAP + "\n" + "rR", exp.et_Action.getText());
        assertTrue("浏览界面导出 XSB 应勾选", exp.cb_XSB.isSelected());
        assertFalse("浏览界面导出 XSB 应禁用", exp.cb_XSB.isEnabled());
        assertTrue("浏览界面导出 Lurd 应勾选", exp.cb_Lurd.isSelected());
        assertTrue("浏览界面导出 Lurd 应可勾", exp.cb_Lurd.isEnabled());
        assertFalse("浏览界面导出应隐藏「导出正推现场」行",
                exp.cb_Cur.getParent().isVisible());
    }

    @Test
    public void browseExportWithoutAnswerOnlyShowsTheMap() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        myExport exp = track(new myExport(MAP, "", null, "", false, 0, null, null, ""));

        assertEquals("无答案时只显示关卡图", MAP, exp.et_Action.getText());
        assertFalse("无答案时 Lurd 应禁用", exp.cb_Lurd.isEnabled());
        assertFalse("无答案时 Lurd 不应勾选", exp.cb_Lurd.isSelected());
    }

    // ============================================================ 4. 源码扫描（防回退）

    @Test
    public void setup2DialogKeepsItsLayoutButAdoptsTheHoloDarkColors() throws Exception {
        String dialog = methodBody(codeOf("myGameView.java"), "void showSetup2Dialog", "()");
        assertTrue("面板底色应取 Holo 深色对话框的 #282828",
                dialog.contains("HoloAlertDialog.PANEL_BG"));
        assertTrue("复选框应改用浅色文字（FlatLaf 的图标按前景色画）",
                dialog.contains("cb.setForeground(HoloContent.TEXT)"));
        assertTrue("「确定」应走 HoloButton", dialog.contains("new HoloButton(\"确定\")"));
        // 布局要求「不变」：仍是两列网格 + 单个确定按钮
        assertTrue("布局应保持两列网格", dialog.contains("new GridLayout(0, 2, 5, 5)"));
        assertEquals("仍应只有一个按钮", 1, countOccurrences(dialog, "new HoloButton("));
    }

    @Test
    public void actGmButtonRowRestoresTheOriginalMargins() throws Exception {
        String row = methodBody(codeOf("myActGMView.java"), "private JComponent buildContent", "()");
        assertTrue("行首/行尾应各留 2dp（原版 <Button> 的 layout_margin）",
                row.contains("Box.createHorizontalStrut(2)"));
        assertTrue("相邻按钮间距应为 8dp（margin2 + View4 + margin2）",
                row.contains("Box.createHorizontalStrut(8)"));
        assertTrue("cb_PreEdit 在 XML 里没有 margin，两侧应是 6dp",
                row.contains("Box.createHorizontalStrut(6)"));
        assertFalse("按钮行里不应再残留 4dp strut（那是间距偏小的旧值）",
                row.contains("Box.createHorizontalStrut(4)"));
    }

    @Test
    public void exportKeepsTheOriginalOverwritePromptAndClipboardFraming() throws Exception {
        String src = codeOf("myExport.java");
        assertFalse("自造的 updateContent() 应已删除（改为按原版的监听器逻辑）",
                src.contains("updateContent"));
        assertTrue("覆写前应弹「文档已存在，覆写吗？」",
                src.contains("文档已存在，覆写吗？"));
        assertTrue("覆写确认应走 HoloConfirmDialog", src.contains("HoloConfirmDialog"));
        assertTrue("剪切板导出应按原版前后各补一个换行",
                src.contains("saveClipper(\"\\n\" + text + \"\\n\")"));
        assertTrue("Solution 头只在推关卡界面（my_Local != null）拼",
                src.contains("my_Local != null && is_ANS"));
    }

    // ============================================================ 工具

    private static String codeOf(String name) throws IOException {
        File f = new File(System.getProperty("user.dir"),
                "src/main/java/my/boxman/" + name);
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    /** 取出某个方法的函数体（从签名后的第一个 `{` 配平到对应的 `}`）。 */
    private static String methodBody(String src, String name, String signatureTail) {
        int at = src.indexOf(" " + name + signatureTail);
        assertTrue("找不到方法 " + name + signatureTail, at >= 0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, i + 1);
            }
        }
        throw new AssertionError("方法 " + name + " 的大括号不配平");
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
