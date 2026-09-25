package my.boxman;

import my.boxman.compat.HoloPopupMenu;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-2 验收：原版「关卡编辑器」Activity（{@code myEditView}，2407 行）的还原。
 *
 * <p>改写前 PC 版只有 629 行，装了一条自造的 {@code JMenuBar}
 * （{@code 文件 / 编辑 / 帮助} 三组共 10 项，标题与 {@code res/menu/edit.xml} 全不对），
 * 而原版是 {@code FEATURE_NO_TITLE} + {@code FLAG_FULLSCREEN} —— <b>根本没有 ActionBar</b>，
 * 13 项全在底栏「更多」按钮弹出的选项菜单里。
 * {@code JMenuBar} 还顺带挖走了内容区 23px，与「370×780 竖屏」不符。
 *
 * <p>本次同时补齐了 4 个此前完全不存在的菜单项
 * （{@code 标尺...} / {@code 设置...} / {@code 区块另存为...} / {@code 关卡资料}），
 * 并把 {@code 修改尺寸...}（含「扩充/消减」与 8 条越界校验）、
 * {@code 导入(XSB 或 Lurd)}（含 {@code LurdToXSB} 逆推）、
 * {@code 提交} / {@code 试推} / {@code 关卡标准化} 按原版逻辑重写。
 *
 * <p>后又补了 {@code myEditViewMap.onLongPress} 的两段分支
 * （长按顶部「仓管员」素材 → {@code Normalize2} + 自动求解；长按素材 → 填充/勾边），
 * 以及它们依赖的 {@code myEditView.DoAct(0)}（原版会弹「请选择：填充 / 勾边」模态框，
 * 模态部分留在 {@code DoAct(0)} 里，实际填充/勾边拆成 {@code fillSelection()} /
 * {@code outlineSelection()} 供用例直接调）。
 *
 * <p><b>用例不许弹模态框</b>：确认框、剪切板框、设置框都是模态 {@code JDialog}，
 * 弹出来会挂死 EDT。所以只测「纯逻辑 + 已拆出来的动作方法」，
 * 并用 {@code @Rule Timeout} 兜底，把「挂住」变成「失败」。
 */
public class Phase17EditViewTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** {@code res/menu/edit.xml} 全 13 项，顺序即原版 XML 里的顺序。 */
    private static final String[] EDIT_MENU = {
            "提交", "试推", "标尺...", "设置...", "清空地图", "区块另存为...",
            "导出(XSB)", "导入(XSB 或 Lurd)", "修改尺寸...", "关卡标准化",
            "关卡资料", "操作说明", "离开",
    };

    /** 一个合法的小关卡：3 行 × 5 列，1 个仓管员、1 箱 1 目标。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    private myEditView win;
    private mapNode savedCur;
    private String savedRoot, savedPath;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/edit-home";
        myMaps.sPath = "/";
        // ⚠️ 必须显式给窗口尺寸：myEditViewMap.initView() 用它算顶部素材条的矩形
        //    （rtF/rtW/rtD/rtB/rtM/rtSize）。默认 0 时 obj_Width 会被夹到 30，
        //    而 rtSize 的右边界 = m_nWinWidth - 5 会变成 -5 → 整个矩形退化，长按用例全假过。
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot + myMaps.sPath + "创编关卡/").mkdirs();
        new File(myMaps.sRoot + myMaps.sPath + "宏/").mkdirs();
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;

        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase17_test.XSB";
        myMaps.curMapNum = -4;

        win = new myEditView();
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
    }

    // ---------------------------------------------------------------- 菜单（D-2 本体）

    @Test
    public void testNoJMenuBarInstalled() {
        assertNull("原版是 FEATURE_NO_TITLE，PC 不应再装 JMenuBar", win.getJMenuBar());
    }

    @Test
    public void testOptionsMenuHasAll13ItemsInEditXmlOrder() {
        JPopupMenu menu = win.optionsMenuForTest();
        assertEquals(13, HoloPopupMenu.itemCount(menu));

        java.util.List<String> got = new ArrayList<>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) got.add(((HoloPopupMenu.Row) c).getText());
        }
        assertEquals(Arrays.asList(EDIT_MENU).toString(), got.toString());
    }

    @Test
    public void testEveryMenuItemIsVisible() {
        JPopupMenu menu = win.optionsMenuForTest();
        for (String t : EDIT_MENU) {
            assertTrue("菜单项应可见: " + t, HoloPopupMenu.isVisible(menu, t));
            assertTrue("菜单项应可点: " + t, HoloPopupMenu.isEnabled(menu, t));
        }
    }

    @Test
    public void testMenuTitlesMatchEditXmlExactly() {
        // 原版标题里有全角/半角括号与省略号，任何「顺手改标题」都会在这里被抓住
        JPopupMenu menu = win.optionsMenuForTest();
        assertTrue(HoloPopupMenu.isVisible(menu, "导出(XSB)"));
        assertTrue(HoloPopupMenu.isVisible(menu, "导入(XSB 或 Lurd)"));
        assertTrue(HoloPopupMenu.isVisible(menu, "区块另存为..."));
        assertFalse("不应该再有自造的「从剪贴板导入 XSB」",
                HoloPopupMenu.isVisible(menu, "从剪贴板导入 XSB"));
        assertFalse("不应该再有自造的「导出 XSB 到剪贴板」",
                HoloPopupMenu.isVisible(menu, "导出 XSB 到剪贴板"));
    }

    // ---------------------------------------------------------------- 修改尺寸...（8 条校验）

    @Test
    public void testResizeErrorAcceptsInRangeValues() {
        assertNull("合法尺寸不应报错", win.resizeError(1, 1, 1, 1));
    }

    @Test
    public void testResizeErrorRejectsTooSmall() {
        // 当前关卡是 3 行 × 5 列；左右各消减 2 → 宽度只剩 1
        String err = win.resizeError(-2, -2, 0, 0);
        assertNotNull("宽度只剩 1 时应报错", err);
        assertTrue(err.contains("关卡宽度不能小于3"));
    }

    @Test
    public void testResizeErrorRejectsTooLarge() {
        String err = win.resizeError(myMaps.m_nMaxCol, myMaps.m_nMaxCol, 0, 0);
        assertNotNull(err);
        assertTrue(err.contains("关卡超宽"));
    }

    @Test
    public void testResizeErrorRejectsInsufficientSpaceOnSides() {
        String err = win.resizeError(1, 1, 1, 1);
        assertNull(err);

        // 左侧空间：m_nMapLeft 现在是 m_nMaxCol，减 1 后允许的最大 newLeft = m_nMapLeft - 1
        int tooBig = win.mMap.m_nMapLeft + 1;
        String e2 = win.resizeError(tooBig, 0, 0, 0);
        assertNotNull(e2);
        assertTrue("应报左侧空间不足，实际: " + e2, e2.contains("左侧空间不足"));
    }

    // ---------------------------------------------------------------- 清空地图

    @Test
    public void testClearMapWipesAndDisablesCutCopy() {
        win.m_cArray[win.mMap.m_nMapTop][win.mMap.m_nMapLeft] = '#';
        win.bt_Cut.setEnabled(true);
        win.bt_Copy.setEnabled(true);

        win.clearMap();

        for (int i = win.mMap.m_nMapTop; i <= win.mMap.m_nMapBottom; i++) {
            for (int j = win.mMap.m_nMapLeft; j <= win.mMap.m_nMapRight; j++) {
                assertEquals("清空后应全是 '-'", '-', win.m_cArray[i][j]);
            }
        }
        assertEquals(-1, win.mMap.selNode.row);
        assertFalse(win.bt_Cut.isEnabled());
        assertFalse(win.bt_Copy.isEnabled());
        assertTrue(win.bt_UnDo.isEnabled());
        assertTrue(win.bt_Save.isEnabled());
    }

    // ---------------------------------------------------------------- 区块另存为...

    @Test
    public void testBlockSaveWithoutSelectionOnlyToasts() {
        File dir = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/");
        int before = countBlocks(dir);

        win.mMap.selNode.row = -1;      // 没有选区
        win.myBlockSave(win.m_cArray);
        drainEdt();

        assertEquals("没有选区时只提示、不写文件", before, countBlocks(dir));
    }

    private static int countBlocks(File dir) {
        File[] fs = dir.listFiles((d, n) -> n.startsWith("~Block_"));
        return fs == null ? 0 : fs.length;
    }

    // ---------------------------------------------------------------- 导入（Lurd 逆推）

    @Test
    public void testLurdToXSBReconstructsInitialState() {
        // 一条能走通的答案：向右推一格（'R'）。逆推后应得到 "@$." 形状的初态
        assertTrue(win.LurdToXSB("R"));

        int top = win.mMap.m_nMapTop, left = win.mMap.m_nMapLeft;
        int bottom = win.mMap.m_nMapBottom, right = win.mMap.m_nMapRight;
        StringBuilder sb = new StringBuilder();
        for (int i = top; i <= bottom; i++) {
            for (int j = left; j <= right; j++) sb.append(win.m_cArray[i][j]);
            sb.append('\n');
        }
        String s = sb.toString();
        assertTrue("应含仓管员: " + s, s.indexOf('@') >= 0 || s.indexOf('+') >= 0);
        assertTrue("应含箱子: " + s, s.indexOf('$') >= 0 || s.indexOf('*') >= 0);
        assertTrue("应含目标点: " + s, s.indexOf('.') >= 0 || s.indexOf('*') >= 0);
    }

    @Test
    public void testLurdToXSBRejectsImpossibleMove() {
        // 一直往下走，迟早撞到画布下边界（m_nMaxRow*2）→ 矛盾，返回 false
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < myMaps.m_nMaxRow * 2; i++) sb.append('u');
        assertFalse(win.LurdToXSB(sb.toString()));
    }

    // ---------------------------------------------------------------- 标准化

    @Test
    public void testNormalize2ShrinksToReachableArea() {
        assertTrue("合法关卡应标准化成功", win.Normalize2(win.m_cArray));

        String map = myMaps.curMap.Map;
        assertNotNull(map);
        assertEquals(win.mMap.m_nMapBottom - win.mMap.m_nMapTop + 1, myMaps.curMap.Rows);
        assertEquals(win.mMap.m_nMapRight - win.mMap.m_nMapLeft + 1, myMaps.curMap.Cols);
    }

    // ---------------------------------------------------------------- 导出文本

    @Test
    public void testGetXSBSkipsEmptyOuterRing() {
        // 在中间放一个箱子，外围全空 → 有效部分不应含外围的空行/空列
        int r = (win.mMap.m_nMapTop + win.mMap.m_nMapBottom) / 2;
        int c = (win.mMap.m_nMapLeft + win.mMap.m_nMapRight) / 2;
        win.m_cArray[r][c] = '$';

        String xsb = win.getXSBForTest();
        assertNotNull(xsb);
        assertTrue("有效部分里应有箱子: " + xsb, xsb.indexOf('$') >= 0);
        // 关卡是 15 行 × 10 列，收掉外围后行数应明显小于 15
        assertTrue("应收掉外围空行: " + xsb, xsb.split("\n").length < 15);
    }

    // ---------------------------------------------------------------- 存盘

    @Test
    public void testSaveFileWritesXsbAndUpdatesCurMap() throws Exception {
        ArrayList<mapNode> savedLst = myMaps.m_lstMaps;
        myMaps.m_lstMaps = new ArrayList<>();
        try {
            assertTrue(win.saveFile("phase17_test.XSB"));
        } finally {
            myMaps.m_lstMaps = savedLst;
        }

        File f = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/phase17_test.XSB");
        assertTrue("应写出文档: " + f.getAbsolutePath(), f.exists());

        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Title: 测试关"));
        assertTrue(text.contains("Author: 测试"));
        assertTrue(text.contains("Comment_end:"));

        assertEquals(win.mMap.m_nMapBottom - win.mMap.m_nMapTop + 1, myMaps.curMap.Rows);
        assertEquals(win.mMap.m_nMapRight - win.mMap.m_nMapLeft + 1, myMaps.curMap.Cols);
    }

    // ---------------------------------------------------------------- 退出框

    @Test
    public void testExitDialogIsBuilt() {
        assertNotNull("原版 onCreate 里就建好了 exitDlg", win.getExitDialogForTest());
    }

    // ---------------------------------------------------------------- 长按（onLongPress）

    /**
     * 原版 {@code myEditViewMap.onLongPress}：长按顶部「仓管员」素材 → 先 {@code Normalize2}，
     * 成功才自动求解。PC 无跨应用 YASS 求解器，所以落到「没有找到求解器！」。
     *
     * <p>改写前 PC 的 {@code onLongPress} 只有 {@code y < m_nArenaTop} 与 {@code else} 两个分支，
     * 既没有 {@code rtM} 这个前置判断，也没有「长按素材填充选区」那一段。
     */
    @Test
    public void testLongPressOnWorkerSwatchTriesAutoSolve() {
        assertTrue("前置：合法关卡应能标准化", win.Normalize2(win.m_cArray));

        longPress(win.mMap, centerX(win.mMap.rtM), centerY(win.mMap.rtM));
        drainEdt();

        assertEquals("PC 无 YASS 求解器 → 与真机未安装同一条提示",
                "没有找到求解器！", MyToast.currentToastText());
    }

    /**
     * {@code Normalize2} 失败时不应发起求解。
     *
     * <p>⚠️ 不能真造一个非法地图去跑 {@code Normalize2}：它的两条失败路径都会弹
     * <b>模态</b> {@code HoloMessageDialog}（「仓管员数目不正确！」/「箱子或目标数不正确！」），
     * 用例会直接挂死。所以这里用一个「{@code Normalize2} 恒失败」的编辑器替身来验闸门。
     */
    @Test
    public void testLongPressOnWorkerSwatchSkipsSolveWhenNormalizeFails() {
        myEditView failing = new myEditView() {
            @Override
            boolean Normalize2(char[][] mLevel) {
                return false;
            }
        };
        try {
            String sentinel = showSentinel(failing);
            longPress(failing.mMap, centerX(failing.mMap.rtM), centerY(failing.mMap.rtM));
            drainEdt();

            assertEquals("标准化失败时不该弹求解提示", sentinel, MyToast.currentToastText());
        } finally {
            failing.dispose();
        }
    }

    /** 长按地板素材（不是仓管员）不应触发求解。 */
    @Test
    public void testLongPressOnFloorSwatchDoesNotSolve() {
        win.mMap.mMod = myEditViewMap.MOD_SELECT;
        win.mMap.selNode.row = -1;   // 没有选区 → 填充分支也不动

        String sentinel = showSentinel(win);
        longPress(win.mMap, centerX(win.mMap.rtF), centerY(win.mMap.rtF));
        drainEdt();

        assertEquals("长按地板素材不该触发求解", sentinel, MyToast.currentToastText());
    }

    /** 长按右上角「尺寸」区域：切到选择模式并选中全部素材。 */
    @Test
    public void testLongPressOnSizeAreaSwitchesToSelectMode() {
        win.mMap.mMod = myEditViewMap.MOD_EDIT;
        win.mMap.selNode.row = -1;

        longPress(win.mMap, centerX(win.mMap.rtSize), centerY(win.mMap.rtSize));
        drainEdt();

        assertEquals("应切到选择模式", myEditViewMap.MOD_SELECT, win.mMap.mMod);
        assertTrue("应已选中全部素材（selNode 不再是 -1）", win.mMap.selNode.row >= 0);
    }

    // ---------------------------------------------------------------- 填充 / 勾边（DoAct(0)）

    /** {@code DoAct(0)} 的「填充」分支：选区整块刷成当前素材。 */
    @Test
    public void testFillSelectionPaintsWholeSelection() {
        select(0, 0, 1, 1);
        win.mMap.cur_Obj = 1;          // 素材--墙壁 '#'
        win.fillSelection();

        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                assertEquals("选区内 (" + i + "," + j + ") 应被刷成墙壁",
                        '#', win.m_cArray[win.mMap.m_nMapTop + i][win.mMap.m_nMapLeft + j]);
            }
        }
    }

    /** {@code DoAct(0)} 的「勾边」分支：只画四条边，中间不动。 */
    @Test
    public void testOutlineSelectionPaintsOnlyTheBorder() {
        select(0, 0, 2, 2);
        int top = win.mMap.m_nMapTop, left = win.mMap.m_nMapLeft;

        // 先把 3×3 全刷成地板，方便分辨「边」与「中间」
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) win.m_cArray[top + i][left + j] = '-';
        }

        win.mMap.cur_Obj = 1;          // 素材--墙壁 '#'
        win.outlineSelection();

        assertEquals("上边应被画", '#', win.m_cArray[top][left + 1]);
        assertEquals("下边应被画", '#', win.m_cArray[top + 2][left + 1]);
        assertEquals("左边应被画", '#', win.m_cArray[top + 1][left]);
        assertEquals("右边应被画", '#', win.m_cArray[top + 1][left + 2]);
        assertEquals("四个角也应被画", '#', win.m_cArray[top][left]);
        assertEquals("中间不该被动", '-', win.m_cArray[top + 1][left + 1]);
    }

    /** 勾边的「箱子落到目标上」规则：{@code $} 画到 {@code .} 上要变 {@code *}。 */
    @Test
    public void testOutlineSelectionMergesBoxOntoGoal() {
        select(0, 0, 2, 2);
        int top = win.mMap.m_nMapTop, left = win.mMap.m_nMapLeft;
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) win.m_cArray[top + i][left + j] = '-';
        }
        win.m_cArray[top][left + 1] = '.';   // 上边正中先放个目标

        win.mMap.cur_Obj = 3;                // 素材--箱子 '$'
        win.outlineSelection();

        assertEquals("箱子画到目标上应变 '*'", '*', win.m_cArray[top][left + 1]);
        assertEquals("纯空地上应直接是箱子", '$', win.m_cArray[top + 2][left + 1]);
    }

    // ---------------------------------------------------------------- 辅助

    /** 在 {@code myEditViewMap} 上模拟一次右键长按（PC 把 Android 的长按映射到右键）。 */
    private static void longPress(myEditViewMap map, int x, int y) {
        map.mousePressed(new java.awt.event.MouseEvent(map,
                java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                x, y, 1, true, java.awt.event.MouseEvent.BUTTON3));
    }

    private static int centerX(my.boxman.compat.android.graphics.Rect r) {
        return (r.left + r.right) / 2;
    }

    private static int centerY(my.boxman.compat.android.graphics.Rect r) {
        return (r.top + r.bottom) / 2;
    }

    /** 设置选区（坐标相对关卡左上角，与 {@code myEditViewMap.selNode} 语义一致）。 */
    private void select(int r1, int c1, int r2, int c2) {
        win.mMap.mMod = myEditViewMap.MOD_SELECT;
        win.mMap.selNode.row = r1;
        win.mMap.selNode.col = c1;
        win.mMap.selNode2.row = r2;
        win.mMap.selNode2.col = c2;
    }

    /**
     * 先弹一条哨兵提示再排空 EDT，用于「不该有新提示」的断言。
     * （{@code MyToast.currentToastText()} 收起后仍保留旧文字，不能靠 null 判断。）
     */
    private static String showSentinel(java.awt.Component ctx) {
        MyToast.showToast(ctx, "哨兵", MyToast.LENGTH_SHORT);
        drainEdt();
        return MyToast.currentToastText();
    }

    /** 排空 EDT —— {@code MyToast.showToast} 在非 EDT 线程上是 {@code invokeLater}。 */
    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // ignore
        }
    }
}
