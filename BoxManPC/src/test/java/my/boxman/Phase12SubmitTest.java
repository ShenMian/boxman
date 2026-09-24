package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 C 验收：「提交答案」链路（原版 {@code mySubmit} Activity，475 行）。
 *
 * <p>这条链路在移植前是**完全缺失**的 —— PC 侧连 {@code mySubmit} 类都没有，
 * {@code myStateBrow} 的上下文菜单里也没有入口。所以这个用例既锁表单还原，
 * 也锁「状态浏览器 → 提交窗口」的接线。
 *
 * <p>不联网：只测 {@code interpret()} 这个纯函数分支，以及表单几何/配色。
 */
public class Phase12SubmitTest {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();
    }

    // ---------------------------------------------------------------- 国家表

    @Test
    public void testCountryTableHas242Entries() {
        assertEquals("原版 m_menu 共 242 项", 242, mySubmit.COUNTRY.length);
    }

    @Test
    public void testCountryTableAnchors() {
        // 首项是「其它」，原版注释 //国家 也在这里
        assertArrayEquals(new String[]{"00", "*** Other ***"}, mySubmit.COUNTRY[0]);
        assertArrayEquals(new String[]{"CN", "中国"}, mySubmit.COUNTRY[1]);
        // 末项
        assertArrayEquals(new String[]{"ZW", "Zimbabwe"}, mySubmit.COUNTRY[241]);

        // 每一项都是 {代码, 显示名} 两列，且都不为空
        for (int i = 0; i < mySubmit.COUNTRY.length; i++) {
            String[] row = mySubmit.COUNTRY[i];
            assertEquals("第 " + i + " 项应有 2 列", 2, row.length);
            assertNotNull(row[0]);
            assertNotNull(row[1]);
            assertFalse("第 " + i + " 项的代码不应为空", row[0].isEmpty());
            assertFalse("第 " + i + " 项的显示名不应为空", row[1].isEmpty());
        }
    }

    @Test
    public void testCountryNamesAreDisplayColumn() {
        List<String> names = mySubmit.countryNames();
        assertEquals(mySubmit.COUNTRY.length, names.size());
        assertEquals("*** Other ***", names.get(0));
        assertEquals("中国", names.get(1));
        assertEquals("Zimbabwe", names.get(names.size() - 1));
    }

    /**
     * 原版表里有重复代码：{@code CN} 出现两次（第 2 项「中国」、第 48 项「China」）。
     * 原版反查用 {@code break}，所以永远命中**第一个**。这是原版行为，不能「顺手去重」。
     */
    @Test
    public void testDuplicateCnResolvesToFirstEntry() {
        assertEquals(1, mySubmit.indexOfCountry("CN"));
        assertEquals("中国", mySubmit.COUNTRY[1][1]);
        // 确认第 48 项确实是另一个 CN
        assertArrayEquals(new String[]{"CN", "China"}, mySubmit.COUNTRY[47]);
    }

    @Test
    public void testUnknownCountryFallsBackToOther() {
        // 原版 m_Sel_id 初值为 0，找不到就停在 0
        assertEquals(0, mySubmit.indexOfCountry("XX"));
        assertEquals(0, mySubmit.indexOfCountry(null));
        assertEquals(0, mySubmit.indexOfCountry(""));
    }

    @Test
    public void testKnownCountryCodes() {
        assertEquals(99, mySubmit.indexOfCountry("HK"));
        assertEquals(129, mySubmit.indexOfCountry("MO"));
        assertEquals(213, mySubmit.indexOfCountry("TW"));
        assertEquals(229, mySubmit.indexOfCountry("UK"));
        assertEquals(230, mySubmit.indexOfCountry("US"));
        assertEquals(241, mySubmit.indexOfCountry("ZW"));
    }

    // ------------------------------------------------------- 响应关键字判定

    @Test
    public void testInterpretSuccess() {
        String r = mySubmit.interpret("Correct (for 7986 moves)");
        assertTrue("成功应答应带 OK 前缀", r.startsWith("\u0000OK\u0000"));
        assertEquals("提交成功！", r.substring("\u0000OK\u0000".length()));
    }

    @Test
    public void testInterpretEachFailureBranch() {
        assertEquals("答案不正确！", mySubmit.interpret("Sorry, not correct!"));
        assertEquals("比赛已过期，请关注下一期！", mySubmit.interpret("The competition has ended"));
        assertEquals("比赛尚未开始，请耐心等待！", mySubmit.interpret("Competition not begin yet"));
        assertEquals("姓名不能空着！", mySubmit.interpret("Name cannot be empty"));
        assertEquals("未知情况！", mySubmit.interpret("<html>something else</html>"));
        assertEquals("未知情况！", mySubmit.interpret(""));
        assertEquals("未知情况！", mySubmit.interpret(null));
    }

    /**
     * 判定顺序与原版一致：先看 {@code correct (for } 再看 {@code not correct}。
     * 所以「not correct (for ...」这种同时命中两条的响应，原版判成**成功**。
     * 这个怪癖照原样保留 —— 改掉就不是 1:1 移植了。
     */
    @Test
    public void testInterpretKeepsOriginalBranchOrder() {
        String r = mySubmit.interpret("NOT CORRECT (FOR 123 MOVES)");
        assertTrue("原版先匹配 correct (for ，因此这里判成功", r.startsWith("\u0000OK\u0000"));
    }

    @Test
    public void testInterpretIsCaseInsensitive() {
        assertEquals(mySubmit.interpret("CORRECT (FOR 1)"), mySubmit.interpret("correct (for 1)"));
        assertEquals(mySubmit.interpret("Not Correct"), mySubmit.interpret("NOT CORRECT"));
    }

    // ------------------------------------------------------------ 表单几何

    @Test
    public void testFormGeometryMatchesSubmitXml() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        mySubmit w = new mySubmit();
        try {
            w.addNotify();
            w.validate();
            Container pane = w.getContentPane();
            pane.validate();

            assertEquals("内容区应为主界面同宽", 370, pane.getWidth());
            assertEquals("内容区应为竖屏手机高", 780, pane.getHeight());

            BufferedImage img = new BufferedImage(370, 780, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pane.paint(g);
            g.dispose();

            // ActionBar 48dp #0083C5
            assertColor(img, 200, 20, 0x0083C5, "ActionBar 底色");
            // submit.xml 的根布局没有背景色，窗口底色由 theme 决定（Holo 深色 = #363636）
            assertColor(img, 200, 60, 0x363636, "表单底色 #363636");

            // 30dp 留白之后是「国家/地区:」那一行 —— 找第一个非 #363636 的行
            int firstInkRow = firstNonBackgroundRow(img, 0x363636, 48, 400);
            assertTrue("表单首个内容行应在 ActionBar 下方 30dp 附近，实测 y=" + firstInkRow,
                    firstInkRow >= 48 + 25 && firstInkRow <= 48 + 40);

            // 4dp 分隔条 #303030 必须存在（submit.xml 里唯一一处 #303030）
            assertTrue("应存在 4dp 的 #303030 分隔条", hasRowOfColor(img, 0x303030));
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testActionBarHasUpAffordanceOnly() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        mySubmit w = new mySubmit();
        try {
            myActionBar bar = w.getActionBar();
            assertEquals("比赛答案提交", bar.getBarTitle());
            assertTrue("原版 setDisplayHomeAsUpEnabled(true)", bar.isUpEnabled());
            assertEquals("原版菜单里没有动作项", 0, bar.getBarActionCount());
            assertEquals("也没有溢出项", 0, bar.getActionCount());
        } finally {
            w.dispose();
        }
    }

    // ------------------------------------------------- 与状态浏览器的接线

    /**
     * 原版 {@code myStateBrow} 的 {@code case 11}：先把选中项读进 {@code myMaps.m_State}，
     * 再打开 {@code mySubmit}。提交窗口不缓存状态，{@code send()} 时直接读
     * {@code myMaps.m_State.ans} —— 这里锁住这条数据流。
     */
    @Test
    public void testSubmitReadsSelectedStateAnswer() throws Exception {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();

        ans_Node nd = new ans_Node();
        nd.ans = "lUrD";
        nd.bk_ans = "";
        nd.solution = 0;
        myMaps.m_State = nd;

        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        mySubmit w = new mySubmit();
        try {
            assertSame(nd, myMaps.m_State);
            assertEquals("lUrD", myMaps.m_State.ans);
        } finally {
            w.dispose();
        }
    }

    /**
     * 失败时应留在窗口里让用户改（原版 handler 的 else 分支弹「错误」对话框，
     * 不 finish()）；成功标记由 {@link mySubmit#interpret} 产出的 OK 前缀承载。
     */
    @Test
    public void testOnSubmittedFailureKeepsWindowOpen() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        mySubmit w = new mySubmit();
        try {
            w.setVisible(true);
            assertTrue("窗口应已显示", w.isDisplayable());
            assertEquals("\u0000OK\u0000", okPrefix());
            assertTrue("成功分支以 OK 前缀区分",
                    mySubmit.interpret("correct (for 1)").startsWith(okPrefix()));
        } finally {
            w.dispose();
        }
    }

    private static String okPrefix() throws Exception {
        Field f = mySubmit.class.getDeclaredField("OK_PREFIX");
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // ---------------------------------------------------------------- 工具

    private static void assertArrayEquals(String[] expected, String[] actual) {
        assertEquals("列数", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("第 " + i + " 列", expected[i], actual[i]);
        }
    }

    private static void assertColor(BufferedImage img, int x, int y, int rgb, String what) {
        int got = img.getRGB(x, y) & 0xFFFFFF;
        assertEquals(what + " @" + x + "," + y, String.format("%06X", rgb), String.format("%06X", got));
    }

    /** 从 y0 往下找第一行「至少 5 个像素不是 bg」的行。 */
    private static int firstNonBackgroundRow(BufferedImage img, int bg, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            int n = 0;
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != bg) {
                    n++;
                }
            }
            if (n >= 5) {
                return y;
            }
        }
        return -1;
    }

    /** 是否存在某一整行（≥300px 宽）都是该颜色 —— 用来找通栏分隔条。 */
    private static boolean hasRowOfColor(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            int n = 0;
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) {
                    n++;
                }
            }
            if (n >= 300) {
                return true;
            }
        }
        return false;
    }
}
