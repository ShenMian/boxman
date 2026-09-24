package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 「比赛答案提交列表」（原版 {@code mySubmitList}）的回归锁。
 *
 * <p>数据用原版截图那一期的真实响应（截取片段），不依赖网络；
 * 只有 {@link #testHttpGetReportsFailure()} 会真的发一次注定失败的请求。
 */
public class Phase9SubmitTest {

    /** 主关：3 条，最小步数 = 第 3 条（7986），最小推动 = 第 3 条（1199） */
    private static final String MAIN = "{\"id\":219,\"label\":\"main\",\"submission\":["
            + "{\"time\":\"2026-09-18 22:17:56\",\"name\":\"油纸成伞\",\"country\":\"CN\",\"move\":16380,\"push\":1423},"
            + "{\"time\":\"2026-09-19 09:39:12\",\"name\":\"cjcjc\",\"country\":\"CN\",\"move\":20976,\"push\":2013},"
            + "{\"time\":\"2026-09-19 15:18:59\",\"name\":\"Liu Yi\",\"country\":\"CN\",\"move\":7986,\"push\":1199}]}";

    /** 副关：4 条，最小步数 = 第 4 条（1407），最小推动 = 第 3 条（243） */
    private static final String EXTRA = "{\"id\":219,\"label\":\"extra\",\"submission\":["
            + "{\"time\":\"2026-09-19 09:39:39\",\"name\":\"cjcjc\",\"country\":\"CN\",\"move\":2799,\"push\":369},"
            + "{\"time\":\"2026-09-19 12:56:07\",\"name\":\"油纸成伞\",\"country\":\"CN\",\"move\":1923,\"push\":263},"
            + "{\"time\":\"2026-09-19 15:18:27\",\"name\":\"Liu Yi\",\"country\":\"CN\",\"move\":1413,\"push\":243},"
            + "{\"time\":\"2026-09-22 11:29:37\",\"name\":\"zhouxh\",\"country\":\"CN\",\"move\":1407,\"push\":245}]}";

    /** 副关2：同一人提交两次（cjcjc 大小写不同）→ 参赛人数按姓名去重 */
    private static final String EXTRA2 = "{\"id\":219,\"label\":\"extra2\",\"submission\":["
            + "{\"time\":\"2026-09-18 19:36:46\",\"name\":\"zhouxh\",\"country\":\"CN\",\"move\":928,\"push\":187},"
            + "{\"time\":\"2026-09-18 21:50:02\",\"name\":\"Kevin29\",\"country\":\"CN\",\"move\":922,\"push\":179},"
            + "{\"time\":\"2026-09-19 09:39:56\",\"name\":\"CJCJC\",\"country\":\"CN\",\"move\":2203,\"push\":235},"
            + "{\"time\":\"2026-09-19 09:39:58\",\"name\":\" cjcjc \",\"country\":\"CN\",\"move\":2204,\"push\":236}]}";

    private static final String EXTRA3 = "{\"id\":219,\"label\":\"extra3\",\"submission\":["
            + "{\"time\":\"2026-09-18 13:49:35\",\"name\":\"XiBeiTianLang\",\"country\":\"CN\",\"move\":808,\"push\":137}]}";

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();
        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();
    }

    private static mySubmitList loaded() {
        mySubmitList w = new mySubmitList(false);
        w.myJson(MAIN, 0);
        w.myJson(EXTRA, 1);
        w.myJson(EXTRA2, 2);
        w.myJson(EXTRA3, 3);
        mySubmitList.Msg msg = new mySubmitList.Msg();
        msg.what = 1;
        w.applyResult(msg);
        return w;
    }

    @Test
    public void testParsesAllFourGroups() {
        mySubmitList w = loaded();
        try {
            assertEquals(3, w.mList1.size());
            assertEquals(4, w.mList2.size());
            assertEquals(4, w.mList3.size());
            assertEquals(1, w.mList4.size());

            list_Node nd = w.mList1.get(0);
            assertEquals(1, nd.id);                       // 原版 nd.id = k + 1
            assertEquals("2026-09-18 22:17:56", nd.date);
            assertEquals("油纸成伞", nd.name);
            assertEquals("CN", nd.country);
            assertEquals("16380", nd.moves);
            assertEquals("1423", nd.pushs);
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testMinMovesAndMinPushsIndices() {
        mySubmitList w = loaded();
        try {
            // 主关：7986/1199 同时是最小步数与最小推动
            assertEquals(3, w.min_Moves);
            assertEquals(3, w.min_Pushs);
            // 副关：最小步数 1407（第 4 条）、最小推动 243（第 3 条）
            assertEquals(4, w.min_Moves2);
            assertEquals(3, w.min_Pushs2);
            // 副关2：922/179 都在第 2 条
            assertEquals(2, w.min_Moves3);
            assertEquals(2, w.min_Pushs3);
            assertEquals(1, w.min_Moves4);
            assertEquals(1, w.min_Pushs4);
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testSubmitterCountDedupesNames() {
        mySubmitList w = loaded();
        try {
            assertEquals("主关 3 人", 3, w.mTotal[0]);
            assertEquals("副关 4 人", 4, w.mTotal[1]);
            // CJCJC / " cjcjc " 与 cjcjc 是同一个人（忽略大小写与首尾空白）
            assertEquals("副关2 应去重为 3 人", 3, w.mTotal[2]);
            assertEquals(1, w.mTotal[3]);
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testDateFormatIsSubstring5To11() {
        mySubmitList w = loaded();
        try {
            // 原版 getChildView 用 date.substring(5, 11)，即 "09-18 "（含尾随空格）
            assertEquals("09-18 ", w.mList1.get(0).date.substring(5, 11));
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testActionBarShowsReloadWithoutOverflow() {
        mySubmitList w = loaded();
        try {
            myActionBar bar = w.getActionBar();
            assertEquals("提交列表", bar.getBarTitle());
            assertEquals("menu/submit_list.xml 只有一条 showAsAction=always 的「刷新」",
                    1, bar.getBarActionCount());
            assertEquals("溢出菜单为空", 0, bar.getActionCount());
            assertFalse("原版溢出菜单为空时不画 ⋮", bar.isOverflowVisible());
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testGroupsWithDataAreExpanded() {
        mySubmitList w = loaded();
        try {
            // 4 个分组 + 3 + 4 + 4 + 1 个子项
            assertEquals(4 + 3 + 4 + 4 + 1, w.getTree().getRowCount());
        } finally {
            w.dispose();
        }
    }

    @Test
    public void testHttpGetReportsFailure() {
        // 127.0.0.1:1 必然拒绝连接 → 原版会走到 "网络错误：000" 分支
        mySubmitList.HttpResult r = mySubmitList.httpGet("http://127.0.0.1:1/");
        assertNotNull(r);
        assertEquals(0, r.code);
    }

    /**
     * 离屏渲染整屏，按像素断言关键几何与配色 —— 数值全部来自原版截图实测（1dp = 1px）。
     */
    @Test
    public void testRenderedGeometryMatchesOriginalScreenshot() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        mySubmitList w = loaded();
        try {
            w.addNotify();
            w.validate();
            java.awt.Container pane = w.getContentPane();
            pane.validate();
            assertEquals(370, pane.getWidth());
            assertEquals(780, pane.getHeight());

            BufferedImage img = new BufferedImage(370, 780, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pane.paint(g);
            g.dispose();

            // ActionBar 48dp #0083C5
            assertColor(img, 200, 20, 0x0083C5, "ActionBar 底色");
            // 第 1 个分组行内容 48..80，底色 #004040（submit_main.xml 的 Activity 背景）
            assertColor(img, 200, 60, 0x004040, "分组行底色");
            // 第 1 个子项行 82..114，底色 #000000（submit_child.xml）
            assertColor(img, 200, 95, 0x000000, "子项行底色");
            // 行分隔线 #175151
            assertColor(img, 200, 81, 0x175151, "行分隔线");

            // 子项行各列墨迹起点（相对内容左缘 dp），容差 4dp —— 残差来自字体宽度
            int[] expected = {12, 34, 79, 210, 270, 330};
            int[] actual = inkStarts(img, 83, 113, 6);
            assertEquals("子项行列数", expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertTrue("第 " + (i + 1) + " 列起点 " + actual[i] + "dp 应接近 " + expected[i] + "dp",
                        Math.abs(actual[i] - expected[i]) <= 4);
            }
        } finally {
            w.dispose();
        }
    }

    private static void assertColor(BufferedImage img, int x, int y, int rgb, String what) {
        int got = img.getRGB(x, y) & 0xFFFFFF;
        assertEquals(what + " @" + x + "," + y, String.format("%06X", rgb), String.format("%06X", got));
    }

    /** 在 y0..y1 行内找非黑底墨迹的连通段起点（合并间隔 < 12px 的字形）。 */
    private static int[] inkStarts(BufferedImage img, int y0, int y1, int limit) {
        java.util.List<Integer> starts = new java.util.ArrayList<Integer>();
        int lastEnd = -100;
        boolean in = false;
        int start = 0;
        for (int x = 0; x < img.getWidth(); x++) {
            boolean ink = false;
            for (int y = y0; y <= y1; y++) {
                int c = img.getRGB(x, y) & 0xFFFFFF;
                int r = (c >> 16) & 0xFF;
                int gg = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                if (Math.max(r, Math.max(gg, b)) > 40) {
                    ink = true;
                    break;
                }
            }
            if (ink && !in) {
                in = true;
                start = x;
            } else if (!ink && in) {
                in = false;
                if (start - lastEnd >= 12) {
                    starts.add(start);
                    lastEnd = x;
                } else {
                    lastEnd = x;
                }
            }
        }
        if (in && start - lastEnd >= 12) {
            starts.add(start);
        }
        int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = starts.get(i);
        }
        return out;
    }
}
