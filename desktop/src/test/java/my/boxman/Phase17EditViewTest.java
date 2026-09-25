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

    // ---------------------------------------------------------------- 辅助

    /** 排空 EDT —— {@code MyToast.showToast} 在非 EDT 线程上是 {@code invokeLater}。 */
    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // ignore
        }
    }
}
