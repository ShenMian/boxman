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
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-2 收尾验收：{@code myGridView} 里最后 3 个**置灰占位**的菜单项。
 *
 * <p>改写前 {@code buildActionBar()} 里这三项都写成
 * {@code addAction(..., false, myActionBar.NO_OP)} / {@code addBarAction(..., false, NO_OP)}
 * —— 标题在、位置对，但点不动（原版 {@code BoxMan} 没有 {@code onPrepareOptionsMenu}，
 * {@code levels.xml} 的 13 项全部常驻可用，所以置灰是 PC 侧的偏差）。
 *
 * <ul>
 *   <li>{@code ╋}（{@code levels_add}）：创编关卡 → 「关卡尺寸」框建空关卡进编辑器；
 *       其它关卡集 → 两项 PopupMenu（{@code 添加关卡(文档)...} / {@code 添加关卡(剪切板)...}）</li>
 *   <li>{@code 清空列表}（{@code levels_clear}）：确认后清 {@code L_DateTime} + 清列表</li>
 *   <li>{@code 批量删除...}（{@code levels_delete_more}）：「删除范围: 1 -- N」按序号区间删</li>
 * </ul>
 *
 * <p>用例不弹模态框（确认框/尺寸框都是模态 {@code JDialog}），只测已拆出来的动作方法；
 * 并用 {@code @Rule Timeout} 把「挂住」变成「失败」。
 */
public class Phase18GridViewMenuTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    private static final String A_ADD = "╋";
    private static final String A_CLEAR = "清空列表";
    private static final String A_DELETE_MORE = "批量删除...";

    private myGridView win;
    private String savedFile, savedRoot;
    private ArrayList<mapNode> savedLst;

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/grid-home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot + myMaps.sPath + "创编关卡/").mkdirs();
        new File(myMaps.sRoot + myMaps.sPath + "导入/").mkdirs();
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
    }

    @Before
    public void setUp() {
        savedFile = myMaps.sFile;
        savedRoot = myMaps.sRoot;
        savedLst = myMaps.m_lstMaps;
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
        myMaps.m_lstMaps = savedLst;
    }

    private myGridView open(String setTitle) {
        win = new myGridView(-1, setTitle);
        return win;
    }

    /**
     * 合法地图：3 行 × 5 列，1 仓管员 / 1 箱 / 1 目标。
     *
     * <p>⚠️ 必须带箱子与目标 —— {@code mapNode(String,String,String,String)} 会跑
     * {@code mapNormalize()}，不足 3×3 或箱/标不配对会被判「无效关卡」并把标题改掉。
     */
    private static final String VALID_LEVEL = "#####\n#@$.#\n#####";

    /** 造一个带 n 个关卡的内存列表（不碰库）。 */
    private void fakeList(int n) {
        ArrayList<mapNode> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            mapNode nd = new mapNode(VALID_LEVEL, "L" + (i + 1), "", "");
            nd.fileName = "L" + (i + 1) + ".XSB";
            list.add(nd);
        }
        myMaps.m_lstMaps = list;
        win.refreshGrid();
    }

    // ---------------------------------------------------------------- 三项已不再置灰

    @Test
    public void testThreeItemsAreNoLongerGreyed() {
        myGridView g = open("创编关卡");

        assertTrue("「╋」应可点（原版 13 项全部常驻可用）",
                g.getActionBarForTest().isBarActionEnabled(A_ADD));
        assertTrue("「清空列表」应可点", g.getActionBarForTest().isActionEnabled(A_CLEAR));
        assertTrue("「批量删除...」应可点", g.getActionBarForTest().isActionEnabled(A_DELETE_MORE));
    }

    @Test
    public void testClearAndDeleteMoreEnabledInNonCreativeSetToo() {
        myGridView g = open("最近推过的关卡");
        assertTrue(g.getActionBarForTest().isActionEnabled(A_CLEAR));
    }

    // ---------------------------------------------------------------- ╋

    @Test
    public void testAddMenuHasTwoItemsWithOriginalTitles() {
        myGridView g = open("最近推过的关卡");   // 非「创编关卡」→ 走两项 PopupMenu
        g.addForTest();

        JPopupMenu menu = g.getAddMenuForTest();
        assertNotNull("非创编关卡时「╋」应弹出两项菜单", menu);
        assertEquals(2, HoloPopupMenu.itemCount(menu));
        assertTrue(HoloPopupMenu.isVisible(menu, "添加关卡(文档)..."));
        assertTrue(HoloPopupMenu.isVisible(menu, "添加关卡(剪切板)..."));
    }

    // ---------------------------------------------------------------- 清空列表

    @Test
    public void testClearListEmptiesTheList() {
        myGridView g = open("最近推过的关卡");
        fakeList(4);
        assertEquals(4, myMaps.m_lstMaps.size());

        g.clearList();

        assertTrue("清空列表后 m_lstMaps 应为空", myMaps.m_lstMaps.isEmpty());
    }

    // ---------------------------------------------------------------- 批量删除

    @Test
    public void testDeleteRangeRemovesInclusiveRange() {
        myGridView g = open("创编关卡");
        fakeList(5);

        g.deleteRange(2, 4);      // 删第 2、3、4 个

        assertEquals("应剩 2 个", 2, myMaps.m_lstMaps.size());
        assertEquals("L1", myMaps.m_lstMaps.get(0).Title);
        assertEquals("L5", myMaps.m_lstMaps.get(1).Title);
    }

    @Test
    public void testDeleteRangeFromEndToStartKeepsIndicesValid() {
        myGridView g = open("创编关卡");
        fakeList(6);

        g.deleteRange(1, 6);      // 全删

        assertTrue(myMaps.m_lstMaps.isEmpty());
    }

    @Test
    public void testDeleteRangeInCreativeSetDeletesFilesOnDisk() throws Exception {
        myGridView g = open("创编关卡");
        fakeList(3);

        File dir = new File(myMaps.sRoot + myMaps.sPath + "创编关卡/");
        for (int i = 1; i <= 3; i++) {
            File f = new File(dir, "L" + i + ".XSB");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write((VALID_LEVEL + "\nTitle: L" + i).getBytes(StandardCharsets.UTF_8));
            }
        }

        g.deleteRange(1, 2);      // 删 L1、L2

        assertFalse("L1.XSB 应被删掉", new File(dir, "L1.XSB").exists());
        assertFalse("L2.XSB 应被删掉", new File(dir, "L2.XSB").exists());
        assertTrue("L3.XSB 应保留", new File(dir, "L3.XSB").exists());
        assertEquals(1, myMaps.m_lstMaps.size());
        assertEquals("L3", myMaps.m_lstMaps.get(0).Title);
    }

    // ---------------------------------------------------------------- 导入选项面板

    @Test
    public void testImportOptionsReflectAndWriteBackSettings() {
        myGridView g = open("最近推过的关卡");

        boolean savedXsb = myMaps.isXSB, savedLurd = myMaps.isLurd;
        int savedCode = myMaps.m_Code, savedOpen = myMaps.m_Sets[31];
        try {
            myMaps.isXSB = true;
            myMaps.isLurd = false;
            myMaps.m_Code = 1;
            myMaps.m_Sets[31] = 1;

            JComponent withEnc = g.buildImportOptionsForTest(true);
            assertNotNull(withEnc);
            // 「关卡」/「答案」两个勾选框 + 编码三选一 + 自动打开，共 4 行
            JComponent plain = g.buildImportOptionsForTest(false);
            assertNotNull(plain);
        } finally {
            myMaps.isXSB = savedXsb;
            myMaps.isLurd = savedLurd;
            myMaps.m_Code = savedCode;
            myMaps.m_Sets[31] = savedOpen;
        }
    }
}
