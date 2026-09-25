package my.boxman;

import my.boxman.compat.HoloPopupMenu;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 G 验收①：{@code myGridView} 的 <b>14 项上下文菜单</b>
 * （原版 {@code onCreateContextMenu()} + {@code ItemLongClickListener()} +
 * {@code onContextItemSelected()} 共约 612 行）。
 *
 * <p>改写前 PC 只有 7 项自造条目（{@code 推此关卡 / 编辑关卡... / 导出关卡... /
 * 复制 XSB 到剪贴板 / 关卡详细信息... / 删除此关卡...}），标题与原版全不对，
 * 且缺「迁出/复制到关卡集、移动到、前移、后移、查找相似关卡、连续选择至、反选」。
 *
 * <p>这里分两块测：
 * <ul>
 *   <li><b>菜单骨架</b>：14 项标题与顺序，以及按关卡集类型 / 多选状态的可见性矩阵。</li>
 *   <li><b>动作</b>：{@code swap / updateNO / 前移 / 后移 / 移动到 / 连续选择 / 反选 /
 *       删除 / 图标加锁} 这些不弹模态框的纯逻辑。</li>
 * </ul>
 * 「查找相似关卡」的引擎单独放在 {@link Phase23FindFragmentTest}。
 *
 * <p>用例不弹模态框（迁出/复制/移动到/连续选择/删除都是模态 {@code JDialog}），
 * 并用 {@code @Rule Timeout} 把「挂住」变成「失败」。
 */
public class Phase22GridViewContextMenuTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /**
     * 合法地图：3 行 × 5 列，1 仓管员 / 1 箱 / 1 目标。
     *
     * <p>⚠️ 必须带箱子与目标 —— {@code mapNode(String,String,String,String)} 会跑
     * {@code mapNormalize()}，箱/标不配对会被判「无效关卡」并把标题改掉。
     */
    private static final String VALID_LEVEL = "#####\n#@$.#\n#####";

    private myGridView win;
    private String savedFile, savedRoot;
    private mapNode savedCur, savedOld;
    private ArrayList<mapNode> savedLst;
    private boolean savedSelect;
    private int savedSet0;

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/ctx-home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;
        new File(myMaps.sRoot).mkdirs();
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        // getSetTitle() 要查关卡集名
        myMaps.mSets0 = sql.get_GroupList(0);
        myMaps.mSets1 = sql.get_GroupList(1);
        myMaps.mSets2 = sql.get_GroupList(2);
        myMaps.mSets3 = sql.get_GroupList(3);
    }

    @Before
    public void setUp() {
        savedFile = myMaps.sFile;
        savedRoot = myMaps.sRoot;
        savedCur = myMaps.curMap;
        savedOld = myMaps.oldMap;
        savedLst = myMaps.m_lstMaps;
        savedSelect = myMaps.isSelect;
        savedSet0 = myMaps.m_Sets[0];
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.sFile = savedFile;
        myMaps.sRoot = savedRoot;
        myMaps.curMap = savedCur;
        myMaps.oldMap = savedOld;
        myMaps.m_lstMaps = savedLst;
        myMaps.isSelect = savedSelect;
        myMaps.m_Sets[0] = savedSet0;
    }

    private myGridView open(String setTitle) {
        win = new myGridView(-1, setTitle);
        return win;
    }

    /** 造一个带 n 个关卡的<b>内存</b>列表（不碰库）。 */
    private void fakeList(int n) {
        ArrayList<mapNode> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            mapNode nd = new mapNode(VALID_LEVEL, "L" + (i + 1), "", "");
            nd.Level_id = i + 1;
            nd.fileName = "L" + (i + 1) + ".XSB";
            list.add(nd);
        }
        myMaps.m_lstMaps = list;
        win.refreshGrid();
    }

    private static List<String> allTitles(JPopupMenu menu) {
        List<String> out = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) out.add(((HoloPopupMenu.Row) c).getText());
        }
        return out;
    }

    private static List<String> visibleTitles(JPopupMenu menu) {
        List<String> out = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row && c.isVisible()) {
                out.add(((HoloPopupMenu.Row) c).getText());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 菜单骨架

    @Test
    public void testContextMenuHasTheFourteenOriginalItemsInOrder() {
        myGridView g = open("最近推过的关卡");
        fakeList(3);
        g.m_Num = 0;

        JPopupMenu menu = g.buildContextMenu();
        assertEquals("原版 onCreateContextMenu() 共 14 项", 14, HoloPopupMenu.itemCount(menu));
        assertEquals(Arrays.asList(
                "打开", "改编为新关卡", "图标加锁", "迁出关卡至...", "复制关卡到...", "导出...",
                "移动到...", "前移", "后移", "删除", "查找相似关卡", "连续选择至...", "反选", "详细..."),
                allTitles(menu));
    }

    @Test
    public void testVisibilityForRecentLevelsSet() {
        myGridView g = open("最近推过的关卡");
        fakeList(3);
        g.m_Num = 0;
        // 原版：0 打开 / 1 改编为新关卡 / 5 导出... / 10 查找相似关卡 / 13 详细...
        assertEquals(Arrays.asList("打开", "改编为新关卡", "导出...", "查找相似关卡", "详细..."),
                visibleTitles(g.buildContextMenu()));
    }

    @Test
    public void testVisibilityForCreativeSetRenamesSecondItemToEdit() {
        myGridView g = open("创编关卡");
        fakeList(3);
        g.m_Num = 0;
        // 原版：创编关卡下第 2 项标题改成「编辑」，可见 1/9/10/13
        assertEquals(Arrays.asList("编辑", "删除", "查找相似关卡", "详细..."),
                visibleTitles(g.buildContextMenu()));
    }

    @Test
    public void testVisibilityForSimilarSetHidesFindItem() {
        myGridView g = open("相似关卡");
        fakeList(3);
        g.m_Num = 0;
        // 原版「相似关卡」下 10 查找相似关卡 保持隐藏
        assertEquals(Arrays.asList("打开", "改编为新关卡", "复制关卡到...", "导出...", "详细..."),
                visibleTitles(g.buildContextMenu()));
    }

    @Test
    public void testVisibilityForExtendedSetShowsAllExceptSelectRangeAndInvert() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 0;
        myMaps.m_Sets[0] = 3;   // 扩展关卡组

        assertEquals(Arrays.asList(
                "打开", "改编为新关卡", "图标加锁", "迁出关卡至...", "复制关卡到...", "导出...",
                "移动到...", "前移", "后移", "删除", "查找相似关卡", "详细..."),
                visibleTitles(g.buildContextMenu()));
    }

    @Test
    public void testExtendedSetLockedLevelSwitchesThirdTitleToUnlock() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 1;
        myMaps.m_Sets[0] = 3;
        myMaps.m_lstMaps.get(1).Lock = true;

        assertTrue("已加锁的关卡，第 3 项应变成「图标解锁」",
                visibleTitles(g.buildContextMenu()).contains("图标解锁"));
        assertFalse(visibleTitles(g.buildContextMenu()).contains("图标加锁"));
    }

    @Test
    public void testMultiSelectModeSwapsItemsForRangeSelectAndInvert() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 0;
        myMaps.m_Sets[0] = 3;
        myMaps.isSelect = true;

        List<String> vis = visibleTitles(g.buildContextMenu());
        assertTrue("多选模式应显示「连续选择至...」", vis.contains("连续选择至..."));
        assertTrue("多选模式应显示「反选」", vis.contains("反选"));
        assertFalse("多选模式应隐藏「改编为新关卡」", vis.contains("改编为新关卡"));
        assertFalse("多选模式应隐藏「导出...」", vis.contains("导出..."));
        assertFalse("多选模式应隐藏「查找相似关卡」", vis.contains("查找相似关卡"));
    }

    @Test
    public void testMultiSelectRevealsRangeItemsInCreativeSetToo() {
        myGridView g = open("创编关卡");
        fakeList(3);
        g.m_Num = 0;
        myMaps.isSelect = true;

        List<String> vis = visibleTitles(g.buildContextMenu());
        // 原版多选分支在关卡集分支之后跑：它会**关掉**第 2 项（改编为新关卡/编辑）
        // 与第 11 项（查找相似关卡），再打开第 12/13 项
        assertFalse("多选模式下「编辑」也应隐藏", vis.contains("编辑"));
        assertFalse(vis.contains("查找相似关卡"));
        assertTrue(vis.contains("连续选择至..."));
        assertTrue(vis.contains("反选"));
        assertTrue("「删除」不受多选分支影响", vis.contains("删除"));
    }

    // ---------------------------------------------------------------- 纯逻辑动作

    @Test
    public void testSwapShiftsElementsRatherThanExchangingThem() {
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        myGridView.swap(list, 0, 2);           // A 向后挪到下标 2
        assertEquals(Arrays.asList("B", "C", "A", "D"), list);

        myGridView.swap(list, 3, 1);           // D 向前挪到下标 1
        assertEquals(Arrays.asList("B", "D", "C", "A"), list);
    }

    @Test
    public void testMoveToReordersListAndIsNoOpWhenTargetIsCurrent() {
        myGridView g = open("测试扩展集");
        fakeList(4);
        g.m_Num = 0;                            // 把 L1 移到第 3 位
        g.moveTo(3);

        assertEquals("L2", myMaps.m_lstMaps.get(0).Title);
        assertEquals("L3", myMaps.m_lstMaps.get(1).Title);
        assertEquals("L1", myMaps.m_lstMaps.get(2).Title);
        assertEquals("L4", myMaps.m_lstMaps.get(3).Title);

        g.m_Num = 0;
        g.moveTo(1);                            // 位置没变 → 什么都不做
        assertEquals("L2", myMaps.m_lstMaps.get(0).Title);
    }

    @Test
    public void testShiftForwardMovesLevelUpByOne() {
        myGridView g = open("测试扩展集");
        fakeList(4);
        g.m_Num = 2;                            // L3 → 前移一位
        g.onShift(-1);

        assertEquals("L1", myMaps.m_lstMaps.get(0).Title);
        assertEquals("L3", myMaps.m_lstMaps.get(1).Title);
        assertEquals("L2", myMaps.m_lstMaps.get(2).Title);
        assertEquals("L4", myMaps.m_lstMaps.get(3).Title);
    }

    @Test
    public void testShiftForwardAtFirstPositionIsNoOp() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 0;
        g.onShift(-1);
        assertEquals("L1", myMaps.m_lstMaps.get(0).Title);
    }

    @Test
    public void testShiftBackwardMovesLevelDownByOne() {
        myGridView g = open("测试扩展集");
        fakeList(4);
        g.m_Num = 1;                            // L2 → 后移一位
        g.onShift(1);

        assertEquals("L1", myMaps.m_lstMaps.get(0).Title);
        assertEquals("L3", myMaps.m_lstMaps.get(1).Title);
        assertEquals("L2", myMaps.m_lstMaps.get(2).Title);
        assertEquals("L4", myMaps.m_lstMaps.get(3).Title);
    }

    @Test
    public void testShiftBackwardAtLastPositionIsNoOp() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 2;
        g.onShift(1);
        assertEquals("L3", myMaps.m_lstMaps.get(2).Title);
    }

    @Test
    public void testUpdateNoWritesSequentialNumbersBackToDatabase() {
        myGridView g = open("测试扩展集");
        long setId = mySQLite.m_SQL.add_T(3, "序号回写集_" + System.nanoTime(), "", "");
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);

        ArrayList<mapNode> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mapNode nd = new mapNode(VALID_LEVEL, "N" + (i + 1), "", "");
            nd.Level_id = mySQLite.m_SQL.add_L(setId, nd);
            list.add(nd);
        }
        myMaps.m_lstMaps = list;
        g.refreshGrid();

        g.updateNO();

        assertEquals("第 1 个关卡的 L_NO 应为 1",
                1, mySQLite.m_SQL.get_Level_Num(setId, list.get(0).Level_id));
        assertEquals("第 3 个关卡的 L_NO 应为 3",
                3, mySQLite.m_SQL.get_Level_Num(setId, list.get(2).Level_id));
    }

    @Test
    public void testInvertSelectionFlipsEveryFlag() {
        myGridView g = open("测试扩展集");
        fakeList(4);
        myMaps.m_lstMaps.get(0).Select = true;
        myMaps.m_lstMaps.get(3).Select = true;

        g.onContextItemSelected(13);            // 反选

        assertFalse(myMaps.m_lstMaps.get(0).Select);
        assertTrue(myMaps.m_lstMaps.get(1).Select);
        assertTrue(myMaps.m_lstMaps.get(2).Select);
        assertFalse(myMaps.m_lstMaps.get(3).Select);
    }

    @Test
    public void testSelectRangeSelectsInclusiveInterval() {
        myGridView g = open("测试扩展集");
        fakeList(6);
        g.m_Num = 1;                            // 从第 2 号关卡连选至第 5 号
        g.selectRange(5);

        assertFalse(myMaps.m_lstMaps.get(0).Select);
        for (int i = 1; i <= 4; i++) {
            assertTrue("第 " + (i + 1) + " 个应被选中", myMaps.m_lstMaps.get(i).Select);
        }
        assertFalse(myMaps.m_lstMaps.get(5).Select);
    }

    @Test
    public void testSelectRangeBackwardsAlsoWorks() {
        myGridView g = open("测试扩展集");
        fakeList(6);
        g.m_Num = 3;                            // 从第 4 号向前连选至第 2 号
        g.selectRange(2);

        assertFalse(myMaps.m_lstMaps.get(0).Select);
        assertTrue(myMaps.m_lstMaps.get(1).Select);
        assertTrue(myMaps.m_lstMaps.get(2).Select);
        assertTrue(myMaps.m_lstMaps.get(3).Select);
        assertFalse(myMaps.m_lstMaps.get(4).Select);
    }

    @Test
    public void testSelectRangeClampsToTheEnd() {
        myGridView g = open("测试扩展集");
        fakeList(3);
        g.m_Num = 0;
        g.selectRange(999);                     // 越界 → 钳到末位
        for (mapNode nd : myMaps.m_lstMaps) {
            assertTrue("应全部选中", nd.Select);
        }
    }

    @Test
    public void testDeleteSelectedRemovesTheChosenLevel() {
        myGridView g = open("测试扩展集");
        fakeList(4);
        g.m_Num = 1;                            // 单关卡删除 → L2
        g.prepareDeleteSelection();
        g.deleteSelected();

        assertEquals(3, myMaps.m_lstMaps.size());
        assertEquals("L1", myMaps.m_lstMaps.get(0).Title);
        assertEquals("L3", myMaps.m_lstMaps.get(1).Title);
        assertEquals("L4", myMaps.m_lstMaps.get(2).Title);
    }

    @Test
    public void testDeleteSelectedInCreativeSetRemovesFilesFromDisk() throws Exception {
        myGridView g = open("创编关卡");
        fakeList(3);

        File dir = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/");
        dir.mkdirs();
        for (int i = 1; i <= 3; i++) {
            java.io.FileOutputStream out =
                    new java.io.FileOutputStream(new File(dir, "L" + i + ".XSB"));
            out.write((VALID_LEVEL + "\nTitle: L" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.close();
        }

        g.m_Num = 0;
        g.prepareDeleteSelection();
        g.deleteSelected();

        assertFalse("L1.XSB 应被删掉", new File(dir, "L1.XSB").exists());
        assertTrue("L2.XSB 应保留", new File(dir, "L2.XSB").exists());
        assertEquals(2, myMaps.m_lstMaps.size());
    }

    @Test
    public void testLockToggleUpdatesDatabase() {
        myGridView g = open("测试扩展集");
        long setId = mySQLite.m_SQL.add_T(3, "加锁集_" + System.nanoTime(), "", "");
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);

        ArrayList<mapNode> list = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            mapNode nd = new mapNode(VALID_LEVEL, "K" + (i + 1), "", "");
            nd.P_id = setId;                 // 库里的关卡 P_id 就是所属关卡集
            nd.Level_id = mySQLite.m_SQL.add_L(setId, nd);
            list.add(nd);
        }
        myMaps.m_lstMaps = list;
        g.refreshGrid();

        long targetId = list.get(0).Level_id;
        g.m_Num = 0;
        assertFalse(list.get(0).Lock);
        g.onContextItemSelected(3);             // 图标加锁
        assertTrue("内存里的 Lock 应变成 true", list.get(0).Lock);

        // 库里 L_Locked 也应跟着写进去：重新读一遍关卡列表，按 L_id 找回来
        mySQLite.m_SQL.get_Levels(setId);
        mapNode reloaded = null;
        for (mapNode nd : myMaps.m_lstMaps) {
            if (nd.Level_id == targetId) reloaded = nd;
        }
        assertNotNull("重新加载后应能找到那个关卡", reloaded);
        assertTrue("库里的 L_Locked 应为 1", reloaded.Lock);
    }

    @Test
    public void testLockToggleIsSkippedForAnswerLibraryLevels() {
        myGridView g = open("相似关卡");
        fakeList(2);
        myMaps.m_lstMaps.get(0).P_id = -1;      // 答案表里的关卡
        g.m_Num = 0;
        g.onContextItemSelected(3);
        assertFalse("P_id < 0 的关卡不加锁", myMaps.m_lstMaps.get(0).Lock);
    }

    // ---------------------------------------------------------------- 查找相似关卡的准备

    @Test
    public void testPrepareFindSourceCopiesCurrentLevelFromDatabaseSet() {
        myGridView g = open("测试扩展集");
        fakeList(2);
        g.m_Num = 1;
        myMaps.m_lstMaps.get(1).Level_id = 77;
        myMaps.m_lstMaps.get(1).P_id = 5;

        g.prepareFindSource();

        assertSame("源关卡就是当前关卡", myMaps.m_lstMaps.get(1), myMaps.curMap);
        assertNotNull(myMaps.oldMap);
        assertEquals("库里的关卡应原样带上 Level_id", 77, myMaps.oldMap.Level_id);
        assertEquals(5, myMaps.oldMap.P_id);
        assertNotNull("Map0 必须非空（查找引擎读它）", myMaps.oldMap.Map0);
    }

    @Test
    public void testPrepareFindSourceRebuildsFreeLevelForCreativeSet() {
        myGridView g = open("创编关卡");
        fakeList(2);
        g.m_Num = 0;

        g.prepareFindSource();

        assertNotNull(myMaps.oldMap);
        assertEquals("自由关卡的 P_id 应为 -1", -1, myMaps.oldMap.P_id);
        assertEquals("Num 应记成序号（1 基）", 1, myMaps.oldMap.Num);
        assertEquals("Map0 直接等于 Map", myMaps.oldMap.Map, myMaps.oldMap.Map0);
    }

    @Test
    public void testOnFindDoneReplacesListWithSimilarLevels() {
        myGridView g = open("测试扩展集");
        fakeList(2);

        ArrayList<mapNode> found = new ArrayList<>();
        found.add(new mapNode(0, 9, 42, 0, VALID_LEVEL, "命中", "", "", 0, 0, VALID_LEVEL, 0));

        g.onFindDone(found);

        assertEquals("相似关卡", myMaps.sFile);
        assertEquals(1, myMaps.m_lstMaps.size());
        assertEquals("命中", myMaps.m_lstMaps.get(0).Title);
        assertEquals("m_Set_id 应重置为 -1", -1, myMaps.m_Set_id);
    }

    @Test
    public void testOnFindDoneWithEmptyResultKeepsListAndToasts() throws Exception {
        myGridView g = open("测试扩展集");
        fakeList(2);

        g.onFindDone(new ArrayList<>());

        // MyToast.showToast 在非 EDT 线程上是 invokeLater，先把 EDT 排空再断言
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals("没有结果时列表不变", 2, myMaps.m_lstMaps.size());
        assertEquals("没有发现相似的关卡！", MyToast.currentToastText());
    }
}
