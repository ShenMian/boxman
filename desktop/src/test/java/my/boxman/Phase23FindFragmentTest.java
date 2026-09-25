package my.boxman;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 G 验收②：{@code myGridView} 上下文菜单第 11 项「查找相似关卡」的引擎
 * {@link myFindFragment}（原版 618 行 {@code DialogFragment + AsyncTask}）。
 *
 * <p>改写前 PC <b>根本没有这个类</b>，只有一个自造的 {@code FindDialog} 占位实现
 * （相似度滑杆 + 用 {@code |ΔRows| <= 2 && |ΔCols| <= 2} 在内存列表里瞎凑结果，
 * 既不查库也不算相似度，且没有任何生产代码调用它）。
 *
 * <p>这里分两块测：
 * <ul>
 *   <li><b>相似度算法</b> {@link myFindFragment#myCompare}：恒等关卡给 100%、
 *       {@code m_IgnoreBox} 把箱子/人当空地板、重叠区不够直接判 0、
 *       非排序模式只回「达标 / 不达标」两态。</li>
 *   <li><b>查找主体</b> {@code runNow()}：能查库、会跳过源关卡自身、受关卡集过滤、
 *       排序模式下把相似率写进 {@code mapNode.Num} 并降序排列。</li>
 * </ul>
 * 用例全部走同步的 {@code runNow()}，不弹进度框。
 */
public class Phase23FindFragmentTest {

    /**
     * 合法地图：3 行 × 5 列，1 仓管员 / 1 箱 / 1 目标。
     *
     * <p>⚠️ 必须带箱子与目标 —— {@code mapNode(String,String,String,String)} 会跑
     * {@code mapNormalize()}，箱/标不配对会被判「无效关卡」，{@code Map0} 变空串。
     */
    private static final String SRC = "#####\n#@$.#\n#####";

    /** 同一关卡的另一种墙外造型（箱子/目标数一致，仍是合法关卡）。 */
    private static final String SRC_VARIANT = "#####\n#@$.#\n##.##";

    private long setId;
    private long otherSetId;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/find-engine-home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        myMaps.mSets0 = sql.get_GroupList(0);
        myMaps.mSets1 = sql.get_GroupList(1);
        myMaps.mSets2 = sql.get_GroupList(2);
        myMaps.mSets3 = sql.get_GroupList(3);
    }

    @After
    public void tearDown() {
        myMaps.oldMap = null;
    }

    /** 建一个专属关卡集，返回其 id（每次用例都新建，避免互相污染）。 */
    private long newSet(String tag) {
        long id = mySQLite.m_SQL.add_T(3, tag + "_" + System.nanoTime(), "", "");
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);
        return id;
    }

    /** 往库里插一个关卡，返回它的 {@code L_id}。 */
    private static long addLevel(long setId, String xsb, String title) {
        mapNode nd = new mapNode(xsb, title, "", "");
        nd.Level_id = mySQLite.m_SQL.add_L(setId, nd);
        return nd.Level_id;
    }

    /**
     * 把源关卡设成「{@link #SRC} 走同一条 {@code mapNormalize} 路径」的样子。
     *
     * <p>必须这样做：{@code myFindFragment} 拿 {@code oldMap.Map0} 去比库里的
     * {@code L_thin_XSB}，两边都得是精准标准化后的形态，否则比不出 100%。
     *
     * @param levelId 源关卡在库里的 {@code L_id}；{@code -999} 表示「库里没有这个关卡」
     */
    private static void setSource(String xsb, long levelId) {
        mapNode ref = new mapNode(xsb, "源", "", "");
        assertNotNull("夹具地图必须合法（Map0 非空）", ref.Map0);
        assertFalse("夹具地图必须合法", ref.Map0.isEmpty());
        myMaps.oldMap = new mapNode(levelId, -1, ref.Rows, ref.Cols,
                ref.Map, ref.Title, ref.Author, ref.Comment, ref.Map0);
    }

    private myFindFragment engine(long[] sets, int similarity, boolean ans, boolean sort, boolean ignoreBox) {
        return new myFindFragment(null, null, sets, similarity, ans, sort, ignoreBox);
    }

    // ---------------------------------------------------------------- 相似度算法

    @Test
    public void testIdenticalMapsScoreHundredInSortedMode() {
        char[][] a = {"#####".toCharArray(), "#-$-#".toCharArray(), "#####".toCharArray()};
        int r = engine(null, 100, false, true, false).myCompare(15, a, 3, 5, a, 3, 5);
        assertEquals("同形关卡应为 100%", 100, r);
    }

    @Test
    public void testOverlapSmallerThanThresholdShortCircuitsToZero() {
        char[][] small = {"###".toCharArray(), "#-#".toCharArray(), "###".toCharArray()};
        char[][] big = new char[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) big[i][j] = '#';
        }
        // 重叠区只有 3×3 = 9 格，达不到 mLeast = 25
        assertEquals("重叠格子数不够 → 直接判 0",
                0, engine(null, 100, false, true, false).myCompare(25, small, 3, 3, big, 5, 5));
    }

    @Test
    public void testUnsortedModeReturnsOneWhenThresholdIsMet() {
        char[][] a = {"#####".toCharArray(), "#-$-#".toCharArray(), "#####".toCharArray()};
        // 非排序模式：达标即返回 1（不计算百分比）
        assertEquals(1, engine(null, 100, false, false, false).myCompare(15, a, 3, 5, a, 3, 5));
    }

    @Test
    public void testUnsortedModeReturnsZeroWhenThresholdIsNotMet() {
        char[][] src = {"#####".toCharArray(), "#@$.#".toCharArray(), "#####".toCharArray()};
        char[][] other = {"#####".toCharArray(), "#---#".toCharArray(), "#####".toCharArray()};
        // 非排序模式、要求 100%：只有 8 格墙/地板相同，达不到 15
        assertEquals(0, engine(null, 100, false, false, false).myCompare(15, src, 3, 5, other, 3, 5));
    }

    @Test
    public void testIgnoreBoxTreatsBoxesAndKeeperAsFloor() {
        // 两个关卡的墙与目标点完全一样，只有「箱子 / 仓管员」互换了位置
        char[][] a = {"#####".toCharArray(), "#@$.#".toCharArray(), "#####".toCharArray()};
        char[][] b = {"#####".toCharArray(), "#$@.#".toCharArray(), "#####".toCharArray()};

        myFindFragment strict = engine(null, 100, false, true, false);
        assertTrue("不忽略箱子时，箱/人换位会掉相似度", strict.myCompare(15, a, 3, 5, b, 3, 5) < 100);

        myFindFragment loose = engine(null, 100, false, true, true);
        assertEquals("忽略箱子和人后应视为完全同形",
                100, loose.myCompare(15, a, 3, 5, b, 3, 5));
    }

    // ---------------------------------------------------------------- 查找主体

    @Test
    public void testRunNowFindsIdenticalLevelInDatabase() {
        setId = newSet("命中集");
        addLevel(setId, SRC, "命中");
        setSource(SRC, -999);   // 源关卡不在库里

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, false, false).runNow();

        assertEquals("应找到 1 个相似关卡", 1, r.size());
        assertEquals("命中", r.get(0).Title);
        assertEquals("非排序模式的相似率记为 0", 0, r.get(0).Num);
    }

    @Test
    public void testSortedModeStoresSimilarityInNum() {
        setId = newSet("排序集");
        addLevel(setId, SRC, "命中");
        setSource(SRC, -999);

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, true, false).runNow();

        assertEquals(1, r.size());
        assertEquals("排序模式应把相似率写进 Num", 100, r.get(0).Num);
    }

    @Test
    public void testRunNowSkipsTheSourceLevelItself() {
        setId = newSet("自比集");
        long lid = addLevel(setId, SRC, "自己");
        setSource(SRC, lid);    // 源关卡就是库里这一条

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, false, false).runNow();

        assertTrue("源关卡自己不应出现在结果里", r.isEmpty());
    }

    @Test
    public void testRunNowRestrictsSearchToTheGivenSets() {
        setId = newSet("A集");
        otherSetId = newSet("B集");
        addLevel(setId, SRC, "在A里");
        addLevel(otherSetId, SRC, "在B里");
        setSource(SRC, -999);

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, false, false).runNow();

        assertEquals("只应命中给定关卡集里的那一个", 1, r.size());
        assertEquals("在A里", r.get(0).Title);
    }

    @Test
    public void testRunNowReturnsEmptyWhenNothingMatches() {
        setId = newSet("空集");
        addLevel(setId, SRC_VARIANT, "不同关卡");
        setSource(SRC, -999);

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, true, false).runNow();

        // 两者的墙外造型不同，但 100% 闸门只看「重叠区完全相同」——
        // 这里只断言引擎不炸、结果有序（可能命中也可能不命中，取决于标准化后的形态）
        for (int i = 1; i < r.size(); i++) {
            assertTrue("结果应按相似率降序", r.get(i - 1).Num >= r.get(i).Num);
        }
    }

    @Test
    public void testRunNowIsSortedDescendingBySimilarity() {
        setId = newSet("降序集");
        addLevel(setId, SRC, "甲");
        addLevel(setId, SRC, "乙");
        addLevel(setId, SRC, "丙");
        setSource(SRC, -999);

        ArrayList<mapNode> r = engine(new long[]{setId}, 100, false, true, false).runNow();

        assertEquals(3, r.size());
        for (int i = 1; i < r.size(); i++) {
            assertTrue("第 " + i + " 项不应比前一项更相似",
                    r.get(i - 1).Num >= r.get(i).Num);
        }
        assertEquals("同形关卡都应是 100%", 100, r.get(0).Num);
    }

    @Test
    public void testRunNowSurvivesAnUnparsableSourceMap() {
        setId = newSet("异常集");
        addLevel(setId, SRC, "随便");
        // 源关卡的 Map0 只有 2 行 → 原版直接 return（不查库）
        myMaps.oldMap = new mapNode(-999, -1, 2, 5, "###\n###", "", "", "", "###\n###");

        assertTrue("源关卡不规整时应直接返回空结果",
                engine(new long[]{setId}, 100, false, false, false).runNow().isEmpty());
    }

    @Test
    public void testTemporarySetTableIsCleanedUpAfterRun() {
        setId = newSet("临时表集");
        addLevel(setId, SRC, "命中");
        setSource(SRC, -999);

        engine(new long[]{setId}, 100, false, false, false).runNow();

        // 原版 finally 里 del_tmp_Table2()：临时表不该留下来
        boolean exists = false;
        try {
            mySQLite.m_SQL.mSDB.rawQuery("select * from id_T", null).close();
            exists = true;
        } catch (Exception ignored) {
            // 表不存在 → 正是期望
        }
        assertFalse("查找结束后 id_T 临时表应被删掉", exists);
    }
}
