package my.boxman;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-2 验收：原版「相似关卡对比」Activity（{@code myFindView}，692 行）的还原。
 *
 * <p>改写前 PC 版只有 120 行，自造了一条「关卡操作」菜单（3 项，标题全不对），
 * 缺 {@code res/menu/find.xml} 的 8 项、缺 {@code getLevel_inf()} 的
 * <b>8 转相似度比对</b>、缺 {@code myCompare()} 的精确相似度算法、
 * 缺 {@code m_Set_Pos1/m_Set_Pos2} 位置信息，{@code myFindViewMap} 也没有旋转渲染。
 *
 * <p>用例不联网：直接在内存里造两个关卡，走 {@code loadForTest} 注入。
 * 相似度阈值走默认的 {@code myMaps.m_Sets[26] = 0}（= 100%，即只认「完全同形」），
 * 这样「源关卡 = 相似关卡转 90°」的那一转会被唯一选中。
 */
public class Phase15FindViewTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 源关卡 3 行 × 5 列 */
    private static final String SRC = "#####\n#@$.#\n#####";
    /** 源关卡顺时针转 90°（1 转）后的样子 —— 也就是「相似关卡」 */
    private static final String SRC_R90 = "###\n#@#\n#$#\n#.#\n###";

    private myFindView win;
    private mapNode savedCur, savedOld;
    private int savedTrun, savedSets26;
    private ArrayList<mapNode> savedLst;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/find-home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();
        // getLevel_inf() 会查关卡集名称 / 序号，必须有库
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedOld = myMaps.oldMap;
        savedTrun = myMaps.m_nTrun;
        savedSets26 = myMaps.m_Sets[26];
        savedLst = new ArrayList<mapNode>(myMaps.m_lstMaps);

        myMaps.curMap = null;   // 构造器据此跳过 getLevel_inf()
        myMaps.oldMap = null;
        myMaps.m_nTrun = 0;
        myMaps.m_Sets[26] = 0;  // 只认 100% 同形
    }

    @After
    public void tearDown() {
        MyToast.dismiss();
        if (win != null) {
            win.dispose();
            win = null;
        }
        myMaps.curMap = savedCur;
        myMaps.oldMap = savedOld;
        myMaps.m_nTrun = savedTrun;
        myMaps.m_Sets[26] = savedSets26;
        myMaps.m_lstMaps.clear();
        myMaps.m_lstMaps.addAll(savedLst);
    }

    /** 源关卡 = {@link #SRC}，相似关卡 = 它顺时针转 90°。 */
    private myFindView openRotatedPair() {
        mapNode src = new mapNode(-1L, -1L, 3, 5, SRC, "源", "", "", SRC);
        mapNode sim = new mapNode(-2L, -1L, 5, 3, SRC_R90, "似", "", "", SRC_R90);
        win = new myFindView();
        win.loadForTest(src, sim);
        return win;
    }

    // ---------------------------------------------------------------- ActionBar / 菜单

    @Test
    public void testActionBarTitleAndBarActions() {
        myFindView w = openRotatedPair();
        // 原版 setTitle(myMaps.sFile)
        myMaps.sFile = "测试关卡集";
        myFindView w2 = new myFindView();
        assertEquals("测试关卡集", w2.getActionBar().getBarTitle());
        w2.dispose();
        myMaps.sFile = null;

        assertTrue("原版 setDisplayHomeAsUpEnabled(true)", w.getActionBar().isUpEnabled());
        // find.xml 的 find_pre / find_next 是 showAsAction="always" → 两个 ActionBar 文字按钮
        assertEquals(2, w.getActionBar().getBarActionCount());
    }

    @Test
    public void testOverflowMenuMatchesFindXml() {
        // 去掉两个 always 项后，find.xml 剩下的 6 项按原顺序进溢出菜单
        assertEquals(Arrays.asList(
                "转动关卡",
                "似源切换",
                "解关答案...",
                "关卡全貌",
                "关于...",
                "操作说明"),
                openRotatedPair().getActionBar().getActionTitles());
        assertTrue(openRotatedPair().getActionBar().isOverflowVisible());
    }

    @Test
    public void testLevelAllMenuItemIsCheckableAndFollowsMapView() {
        myFindView w = openRotatedPair();
        // onCreateOptionsMenu：myMenu.getItem(5).setChecked(mMap.m_Level_All)
        assertFalse(w.getActionBar().isActionChecked("关卡全貌"));
        assertFalse(w.mMap.m_Level_All);

        w.toggleLevelAll();
        assertTrue(w.mMap.m_Level_All);
        assertTrue("菜单勾选要跟着地图视图走", w.getActionBar().isActionChecked("关卡全貌"));

        w.toggleLevelAll();
        assertFalse(w.mMap.m_Level_All);
        assertFalse(w.getActionBar().isActionChecked("关卡全貌"));
    }

    @Test
    public void testDefaultViewIsSimilarLevelNotSource() {
        // 原版 getLevel_inf() 末尾：m_Level = false;  // 默认显示相似关卡
        assertFalse(openRotatedPair().isSourceLevel());
    }

    // ---------------------------------------------------------------- 8 转相似度

    @Test
    public void testSimilarityPicksTheQuarterTurn() {
        myFindView w = openRotatedPair();
        // 相似关卡正是源关卡顺时针 90°（原版的「1 转」）
        assertEquals("1 转应给出 100% 相似度", 100, w.mSimilarity);
        assertEquals("最高相似度出现在 1 转", 1, w.mTrun);
    }

    @Test
    public void testSimilarityRegionIsTheWholeOverlap() {
        myFindView w = openRotatedPair();
        // 两个关卡同尺寸同形 → 相似区域就是整幅图（行 0..4、列 0..2）
        assertArrayEquals(new int[]{0, 0, 4, 2}, w.mSelect[0]);
        assertArrayEquals(new int[]{0, 0, 4, 2}, w.mSelect[1]);
    }

    @Test
    public void testSetPositionsComeFromLevelSource() {
        myFindView w = openRotatedPair();
        // P_id < 0 → 原版把源关卡当「创编关卡」报，相似关卡当「自由关卡」报
        assertNotNull(w.m_Set_Pos1);
        assertTrue(w.m_Set_Pos1.startsWith("关卡集：创编关卡"));
        assertEquals("关卡集：无，自由关卡", w.m_Set_Pos2);
    }

    @Test
    public void testCompareThresholdGateUsesSourceArea() {
        myFindView w = openRotatedPair();
        // 原版「参加对比的格子不够数，不需比较」的闸门：
        //   ceil(min(R1,R2) * min(C1,C2) * 100 / (源关卡面积))  与  mLeast_Similarity[m_Sets[26]] 比
        // 源 5×5、相似 3×5 → 重叠 15 格 / 源 25 格 = 60%
        char[][] src5x5 = {"#####".toCharArray(), "#---#".toCharArray(), "#-$-#".toCharArray(),
                "#-@-#".toCharArray(), "#####".toCharArray()};
        char[][] sim3x5 = {"#####".toCharArray(), "#-$-#".toCharArray(), "#####".toCharArray()};
        int[][] sel = new int[2][4];

        myMaps.m_Sets[26] = 0;   // 阈值档位 0 → 100%
        assertEquals("60% < 100% → 直接判 0，不进比对循环",
                0, w.myCompare(src5x5, 5, 5, sim3x5, 3, 5, sel));

        myMaps.m_Sets[26] = 7;   // 阈值档位 7 → 50%
        assertTrue("60% >= 50% → 放行，进入比对循环",
                w.myCompare(src5x5, 5, 5, sim3x5, 3, 5, sel) > 0);
    }

    @Test
    public void testCompareIdenticalIsHundred() {
        myFindView w = openRotatedPair();
        char[][] a = {"###".toCharArray(), "#-#".toCharArray(), "#$#".toCharArray(),
                "#.#".toCharArray(), "###".toCharArray()};
        int[][] sel = new int[2][4];
        assertEquals(100, w.myCompare(a, 5, 3, a, 5, 3, sel));
        assertArrayEquals(new int[]{0, 0, 4, 2}, sel[0]);
        assertArrayEquals(new int[]{0, 0, 4, 2}, sel[1]);
    }

    // ---------------------------------------------------------------- 视图切换

    @Test
    public void testMyLevelSwapsBetweenSourceAndSimilarArrays() {
        myFindView w = openRotatedPair();
        // 默认：相似关卡 + 标准化 → m_cArray4
        assertSame(w.m_cArray4, w.mMap.m_cArray);

        w.myLevel();                       // → 源关卡
        assertTrue(w.isSourceLevel());
        assertSame(w.m_cArray3, w.mMap.m_cArray);
        assertEquals(w.Rows3, w.mMap.m_nRows);
        assertEquals(w.Cols3, w.mMap.m_nCols);

        w.myLevel();                       // → 相似关卡
        assertFalse(w.isSourceLevel());
        assertSame(w.m_cArray4, w.mMap.m_cArray);
    }

    @Test
    public void testToggleLevelAllSwapsBetweenFullAndThinArrays() {
        myFindView w = openRotatedPair();
        w.myLevel();                       // 切到源关卡，便于区分
        w.toggleLevelAll();                // 关卡全貌
        assertSame(w.m_cArray1, w.mMap.m_cArray);
        assertEquals(w.Rows1, w.mMap.m_nRows);
        assertEquals(w.Cols1, w.mMap.m_nCols);

        w.toggleLevelAll();                // 标准化
        assertSame(w.m_cArray3, w.mMap.m_cArray);
    }

    @Test
    public void testMyTrunTogglesAndSwapsArenaSize() {
        myFindView w = openRotatedPair();
        assertEquals(0, myMaps.m_nTrun);
        int pw = w.mMap.getPicWidth();
        int ph = w.mMap.getPicHeight();
        assertEquals(50 * w.mMap.m_nCols, pw);
        assertEquals(50 * w.mMap.m_nRows, ph);

        w.myTrun();
        assertEquals(1, myMaps.m_nTrun);
        // 原版 initArena()：m_nTrun 为奇数时画布宽高对调
        assertEquals(ph, w.mMap.getPicWidth());
        assertEquals(pw, w.mMap.getPicHeight());

        w.myTrun();
        assertEquals(0, myMaps.m_nTrun);
        assertEquals(pw, w.mMap.getPicWidth());
        assertEquals(ph, w.mMap.getPicHeight());
    }

    // ---------------------------------------------------------------- 地图视图

    @Test
    public void testMapViewButtonRectsSitInTopRightCorner() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        int winW = myMaps.m_nWinWidth > 0 ? myMaps.m_nWinWidth : 370;
        // 两个 120dp 按钮并排贴在右上角（原版 initView）
        assertEquals(winW, m.getLevelRect().right);
        assertEquals(m.getLevelRect().left, m.getTrunRect().right);
        assertEquals(120, m.getTrunRect().width());
        assertEquals(120, m.getLevelRect().width());
        assertEquals(20, m.getTrunRect().top);
        assertEquals(140, m.getTrunRect().bottom);
    }

    @Test
    public void testClickingTrunButtonRotatesLevel() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        int cx = (m.getTrunRect().left + m.getTrunRect().right) / 2;
        int cy = (m.getTrunRect().top + m.getTrunRect().bottom) / 2;
        m.clickForTest(cx, cy, 1);
        assertEquals("点「旋转」按钮应走 myTrun()", 1, myMaps.m_nTrun);
    }

    @Test
    public void testClickingLevelButtonSwitchesSourceAndSimilar() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        int cx = (m.getLevelRect().left + m.getLevelRect().right) / 2;
        int cy = (m.getLevelRect().top + m.getLevelRect().bottom) / 2;
        m.clickForTest(cx, cy, 1);
        assertTrue("点「切换」按钮应走 myLevel()", w.isSourceLevel());
    }

    @Test
    public void testDoubleClickOutsideButtonsTogglesLevelAll() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        // 左上角远处（避开右上角两个按钮）
        m.clickForTest(5, 400, 2);
        assertTrue("原版 onDoubleTap 切换的是「关卡全貌」，不是源/相似", m.m_Level_All);
        assertTrue(w.getActionBar().isActionChecked("关卡全貌"));
    }

    @Test
    public void testGetCurCursorNaming() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        assertEquals("A1", m.mGetCur(0, 0));
        assertEquals("A5", m.mGetCur(4, 0));
        assertEquals("D3", m.mGetCur(2, 3));
        assertEquals("", m.mGetCur(-1, 0));
        assertEquals("", m.mGetCur(0, -1));
    }

    @Test
    public void testSetMapArrayKeepsRowsColsAndArenaInSync() {
        myFindView w = openRotatedPair();
        myFindViewMap m = w.mMap;
        char[][] a = {"###".toCharArray(), "#-#".toCharArray(), "###".toCharArray()};
        m.setMapArray(a, 3, 3);
        assertSame(a, m.m_cArray);
        assertEquals(150, m.getPicWidth());
        assertEquals(150, m.getPicHeight());
    }

    // ---------------------------------------------------------------- 键盘

    @Test
    public void testEscapeClosesWindow() {
        myFindView w = openRotatedPair();
        assertTrue(w.isDisplayable());
        w.handleKeyDown(java.awt.event.KeyEvent.VK_ESCAPE);
        assertFalse("BACK / ESC 应关闭窗口", w.isDisplayable());
    }

    @Test
    public void testPageKeysMoveBetweenLevelsOnlyWhenOptionOn() {
        mapNode src = new mapNode(-1L, -1L, 3, 5, SRC, "源", "", "", SRC);
        mapNode l0 = new mapNode(-2L, -1L, 5, 3, SRC_R90, "似0", "", "", SRC_R90);
        mapNode l1 = new mapNode(-3L, -1L, 5, 3, SRC_R90, "似1", "", "", SRC_R90);

        win = new myFindView();
        win.loadForTest(src, l0);
        myMaps.m_lstMaps.clear();
        myMaps.m_lstMaps.add(l0);
        myMaps.m_lstMaps.add(l1);

        int saved15 = myMaps.m_Sets[15];
        try {
            myMaps.m_Sets[15] = 0;
            win.handleKeyDown(java.awt.event.KeyEvent.VK_PAGE_DOWN);
            assertSame("开关关闭时翻页键不生效", l0, myMaps.curMap);

            myMaps.m_Sets[15] = 1;
            win.handleKeyDown(java.awt.event.KeyEvent.VK_PAGE_DOWN);
            assertSame("开关打开时翻到下一关", l1, myMaps.curMap);
        } finally {
            myMaps.m_Sets[15] = saved15;
        }
    }
}
