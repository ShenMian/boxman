package my.boxman;

import my.boxman.compat.HoloPopupMenu;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D 验收：原版「关卡状态」Activity（{@code myStateBrow}，1018 行）的菜单与列表还原。
 *
 * <p>改写前 PC 版只有 228 行：自造了 {@code JTabbedPane} + 两个 {@code JList}，
 * 菜单栏只有一条自造的「排序方式」，{@code res/menu/state.xml} 的 4 项**全缺**，
 * 原版 12 项上下文菜单只剩 4 项。这里把「分组结构 / 菜单项 / 排序 / 导出文本」全部锁住。
 *
 * <p>不联网、不建库：直接用合成数据灌 {@code myMaps.mState1/mState2}。
 */
public class Phase13StateBrowTest {

    private mapNode savedCurMap;
    private List<state_Node> savedState1;
    private List<state_Node> savedState2;
    private ans_Node savedState;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();
        // writeStateFile 本身不建目录（原版由 saveAnsToFile 先 mkdirs）
        new File(myMaps.sRoot + myMaps.sPath + "导出/").mkdirs();
        new File(myMaps.sRoot + myMaps.sPath + "款/").mkdirs();
    }

    @Before
    public void setUp() {
        savedCurMap = myMaps.curMap;
        savedState = myMaps.m_State;
        savedState1 = new ArrayList<state_Node>(myMaps.mState1);
        savedState2 = new ArrayList<state_Node>(myMaps.mState2);
        // 构造器里的 loadData() 只在 curMap != null 时才查库；置空以保证用的是合成数据
        myMaps.curMap = null;
        myMaps.m_State = null;
        myMaps.mState1.clear();
        myMaps.mState2.clear();
    }

    @After
    public void tearDown() {
        myMaps.curMap = savedCurMap;
        myMaps.m_State = savedState;
        myMaps.mState1.clear();
        myMaps.mState1.addAll(savedState1);
        myMaps.mState2.clear();
        myMaps.mState2.addAll(savedState2);
    }

    private static state_Node node(long id, int moves, int pushs, String inf, String time) {
        state_Node nd = new state_Node();
        nd.id = id;
        nd.pid = 1;
        nd.pkey = 1;
        nd.moves = moves;
        nd.pushs = pushs;
        nd.inf = inf;
        nd.time = time;
        return nd;
    }

    private static List<String> visibleTitles(JPopupMenu menu) {
        List<String> titles = new ArrayList<String>();
        for (Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row && c.isVisible()) {
                titles.add(((HoloPopupMenu.Row) c).getText());
            }
        }
        return titles;
    }

    private static List<String> allTitles(JPopupMenu menu) {
        List<String> titles = new ArrayList<String>();
        for (Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) {
                titles.add(((HoloPopupMenu.Row) c).getText());
            }
        }
        return titles;
    }

    private static DefaultMutableTreeNode groupNode(JTree tree, int g) {
        return (DefaultMutableTreeNode) tree.getModel().getChild(tree.getModel().getRoot(), g);
    }

    private static String groupLabel(myStateBrow w, int g) {
        JTree tree = w.getTree();
        DefaultMutableTreeNode gn = groupNode(tree, g);
        Component c = tree.getCellRenderer().getTreeCellRendererComponent(
                tree, gn, false, true, false, 0, false);
        return ((JLabel) c).getText();
    }

    private static Component childComponent(myStateBrow w, int g, int c) {
        JTree tree = w.getTree();
        DefaultMutableTreeNode cn = (DefaultMutableTreeNode) groupNode(tree, g).getChildAt(c);
        return tree.getCellRenderer().getTreeCellRendererComponent(
                tree, cn, false, false, true, 1, false);
    }

    // ---------------------------------------------------------------- ActionBar / state.xml

    @Test
    public void testActionBarRestoresStateXmlItems() {
        myStateBrow w = new myStateBrow();
        myActionBar bar = w.getActionBar();

        assertEquals("原版 setTitle(\"关卡状态\")", "关卡状态", bar.getBarTitle());
        assertTrue("原版 setDisplayHomeAsUpEnabled(true)", bar.isUpEnabled());
        assertEquals("state.xml 共 4 项", 4, bar.getActionCount());
        assertEquals(Arrays.asList(
                        "导出全部答案到文档",
                        "导出全部答案到剪切板",
                        "导出答案的注释信息",
                        "比赛答案提交列表"),
                bar.getActionTitles());
        // state.xml 里没有任何 showAsAction="always"，所以 4 项全在溢出菜单里
        assertEquals(0, bar.getBarActionCount());
        assertTrue(bar.isOverflowVisible());
        w.dispose();
    }

    @Test
    public void testExportCommentCheckboxFollowsSets30() {
        myMaps.m_Sets[30] = 1;
        myStateBrow w = new myStateBrow();
        assertTrue("m_Sets[30]==1 时 onCreateOptionsMenu 应勾上",
                w.getActionBar().isActionChecked("导出答案的注释信息"));

        w.toggleExportComment();
        assertEquals(0, myMaps.m_Sets[30]);
        assertFalse(w.getActionBar().isActionChecked("导出答案的注释信息"));

        w.toggleExportComment();
        assertEquals(1, myMaps.m_Sets[30]);
        assertTrue(w.getActionBar().isActionChecked("导出答案的注释信息"));

        myMaps.m_Sets[30] = 0;
        w.dispose();
    }

    // ---------------------------------------------------------------- 分组结构

    @Test
    public void testTreeHasTwoGroupsAndSortSuffix() {
        myMaps.mState1.add(node(1, 10, 3, "移动: 10, 推动: 3", "2026-09-01 10:00:00"));
        myMaps.mState2.add(node(2, 8, 2, "移动: 8, 推动: 2", "[YASS]2026-09-02 10:00:00"));

        myStateBrow w = new myStateBrow();
        JTree tree = w.getTree();

        assertFalse("原版 s_main 里没有根节点行", tree.isRootVisible());
        assertEquals(2, tree.getModel().getChildCount(tree.getModel().getRoot()));

        // 原版 getGroupView：只有「答案」组拼上排序方式
        assertEquals("状态", groupLabel(w, 0));
        assertEquals("答案  【移动优先】", groupLabel(w, 1));

        // onCreate 末尾：有数据的分组默认展开
        assertTrue(tree.isExpanded(new javax.swing.tree.TreePath(new Object[]{
                tree.getModel().getRoot(), groupNode(tree, 0)})));
        assertTrue(tree.isExpanded(new javax.swing.tree.TreePath(new Object[]{
                tree.getModel().getRoot(), groupNode(tree, 1)})));

        w.dispose();
    }

    @Test
    public void testChildRowIsTwoLinesTallerThanGroupRow() {
        myMaps.mState1.add(node(1, 10, 3, "移动: 10, 推动: 3", "2026-09-01 10:00:00"));

        myStateBrow w = new myStateBrow();
        JTree tree = w.getTree();

        Component group = tree.getCellRenderer().getTreeCellRendererComponent(
                tree, groupNode(tree, 0), false, true, false, 0, false);
        Component child = childComponent(w, 0, 0);

        FontMetrics fm16 = child.getFontMetrics(new Font("Microsoft YaHei", Font.PLAIN, 16));
        FontMetrics fm12 = child.getFontMetrics(new Font("Microsoft YaHei", Font.PLAIN, 12));

        // s_child.xml：两行 TextView 各带上下 6dp 外边距
        int expected = (6 + fm16.getHeight() + 6) + (6 + fm12.getHeight() + 6);
        assertEquals("子项行 = 描述行 + 时间行（各含 6dp 上下边距）",
                expected, child.getPreferredSize().height);
        assertTrue("子项行应比分组行高", child.getPreferredSize().height > group.getPreferredSize().height);
        assertEquals("子项行是两行 TextView", 2, ((Container) child).getComponentCount());

        // 分组行 = 6 + 16sp 字高 + 6
        assertEquals(6 + fm16.getHeight() + 6, group.getPreferredSize().height);

        w.dispose();
    }

    // ---------------------------------------------------------------- 上下文菜单

    @Test
    public void testContextMenuHasAllTwelveItemsInOriginalOrder() {
        myMaps.mState1.add(node(1, 10, 3, "s", "t"));
        myStateBrow w = new myStateBrow();
        w.setSelection(0, 0);

        JPopupMenu menu = w.buildContextMenu();
        assertEquals("原版 onCreateContextMenu 共 add 12 项", 12, allTitles(menu).size());
        assertEquals(Arrays.asList(myStateBrow.CTX_ALL), allTitles(menu));

        w.dispose();
    }

    @Test
    public void testContextMenuVisibilityOnStateGroup() {
        myMaps.mState1.add(node(1, 10, 3, "s", "t"));
        myMaps.mState2.add(node(2, 8, 2, "a", "t"));
        myStateBrow w = new myStateBrow();

        w.setSelection(0, 0);
        List<String> visible = visibleTitles(w.buildContextMenu());
        assertEquals(Arrays.asList(myStateBrow.CTX_STATE), visible);
        // g_Pos==0 时不该出现 YASS优化 / 提交答案 / 制作 GIF
        assertFalse(visible.contains("YASS优化"));
        assertFalse(visible.contains("提交答案（sokoban.cn）"));
        assertFalse(visible.contains("制作 GIF 演示动画"));
        // 而「删除全部状态」只在状态页出现
        assertTrue(visible.contains("删除全部状态"));

        w.dispose();
    }

    @Test
    public void testContextMenuVisibilityOnAnswerGroup() {
        myMaps.mState1.add(node(1, 10, 3, "s", "t"));
        myMaps.mState2.add(node(2, 8, 2, "a", "t"));
        myStateBrow w = new myStateBrow();

        w.setSelection(1, 0);
        List<String> visible = visibleTitles(w.buildContextMenu());
        assertEquals(Arrays.asList(myStateBrow.CTX_ANSWER), visible);
        assertTrue(visible.contains("YASS优化"));
        assertTrue(visible.contains("提交答案（sokoban.cn）"));
        assertTrue(visible.contains("制作 GIF 演示动画"));
        assertFalse("「删除全部状态」只在状态页", visible.contains("删除全部状态"));
        assertFalse("「正推/逆推 Lurd」只在状态页", visible.contains("导出到剪切板: 正推 Lurd"));
        assertFalse(visible.contains("导出到剪切板: 逆推 Lurd"));

        w.dispose();
    }

    @Test
    public void testContextMenuSelectionCarriesStateId() {
        myMaps.mState1.add(node(11, 10, 3, "s", "t"));
        myMaps.mState2.add(node(22, 8, 2, "a", "t"));
        myStateBrow w = new myStateBrow();

        w.setSelection(0, 0);
        assertEquals(11, w.getSelectedId());
        w.setSelection(1, 0);
        assertEquals(22, w.getSelectedId());

        w.dispose();
    }

    // ---------------------------------------------------------------- 长按分组标题 → 循环排序

    @Test
    public void testLongPressOnAnswerGroupCyclesSortOrder() {
        myMaps.mState2.add(node(2, 8, 20, "推动多", ""));
        myMaps.mState2.add(node(3, 20, 2, "移动多", ""));

        myStateBrow w = new myStateBrow();
        assertEquals(0, w.getSortIndex());
        assertTrue(groupLabel(w, 1).contains("移动优先"));

        w.cycleSortForTest();
        assertEquals(1, w.getSortIndex());
        assertTrue(groupLabel(w, 1).contains("推动优先"));
        // 推动优先：pushs=2 的排前面
        assertEquals(3, myMaps.mState2.get(0).id);

        w.cycleSortForTest();
        assertEquals(2, w.getSortIndex());
        assertTrue(groupLabel(w, 1).contains("时间优先"));

        w.cycleSortForTest();
        assertEquals("循环回移动优先", 0, w.getSortIndex());
        assertTrue(groupLabel(w, 1).contains("移动优先"));
        // 移动优先：moves=8 的排前面
        assertEquals(2, myMaps.mState2.get(0).id);

        w.dispose();
    }

    // ---------------------------------------------------------------- 导出文本

    private static ans_Node ans(int solution, String ans, String bk, int r, int c, String time) {
        ans_Node st = new ans_Node();
        st.solution = solution;
        st.ans = ans;
        st.bk_ans = bk;
        st.r = r;
        st.c = c;
        st.time = time;
        return st;
    }

    @Test
    public void testForwardLurdKeepsOnlyLurdCharacters() {
        myStateBrow w = new myStateBrow();
        myMaps.m_State = ans(1, "ddrrUUl", "", 3, 2, "");
        assertEquals("ddrrUUl", w.buildForwardLurd());
        // 原版 replaceAll("[^lurdLURD]", "")
        myMaps.m_State = ans(1, "[K]dd rr\nUUl", "", 3, 2, "");
        assertEquals("ddrrUUl", w.buildForwardLurd());
        w.dispose();
    }

    @Test
    public void testBackwardLurdPrefixesPusherCoordinate() {
        myStateBrow w = new myStateBrow();
        // 原版：先列后行，且坐标 +1
        myMaps.m_State = ans(0, "", "lluu", 3, 2, "");
        assertEquals("[3,4]lluu", w.buildBackwardLurd());

        // bk_ans 为空时原版不输出坐标前缀
        myMaps.m_State = ans(0, "", "", 3, 2, "");
        assertEquals("", w.buildBackwardLurd());
        w.dispose();
    }

    @Test
    public void testLurdCombinesForwardAndBackward() {
        myStateBrow w = new myStateBrow();
        myMaps.m_State = ans(0, "ddrr", "lluu", 3, 2, "");
        // 原版 myExport：正推 + "\n[列,行]" + 逆推
        assertEquals("ddrr\n[3,4]lluu", w.buildLurd());

        myMaps.m_State = ans(1, "ddrr", "", 3, 2, "");
        assertEquals("ddrr", w.buildLurd());
        w.dispose();
    }

    // ---------------------------------------------------------------- 导出到文档

    @Test
    public void testWriteStateFileHeaderAndAnswerLine() throws Exception {
        myMaps.curMap = new mapNode("#####\n#@$.#\n#####", "测试关卡", "作者甲", "");
        myMaps.m_Sets[30] = 0;

        myStateBrow w = new myStateBrow();
        assertTrue("写文件应成功", w.writeStateFile("unit-test-export.txt", 999));

        File f = new File(myMaps.sRoot + myMaps.sPath + "导出/unit-test-export.txt");
        assertTrue("导出文件应存在", f.exists());
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        assertTrue("应先写关卡 XSB", content.startsWith("#####\n#@$.#\n#####"));
        assertTrue("应写 Title", content.contains("\nTitle: 测试关卡"));
        assertTrue("应写 Author", content.contains("\nAuthor: 作者甲"));
        assertFalse("Comment 为空时不应写 Comment 段", content.contains("Comment:"));

        f.delete();
        w.dispose();
    }

    @Test
    public void testWriteStateFileWritesCommentBlock() throws Exception {
        // 注意：mapNode(m,t,a,c) 会跑 mapNormalize，不足 3×3 会被判为「无效关卡」
        // 并把 t/a/c 塞进 Comment，所以这里必须用一张合法地图。
        myMaps.curMap = new mapNode("#####\n#@$.#\n#####", "T", "A", "一段说明");

        myStateBrow w = new myStateBrow();
        assertTrue(w.writeStateFile("unit-test-export2.txt", 999));

        File f = new File(myMaps.sRoot + myMaps.sPath + "导出/unit-test-export2.txt");
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("\nComment:\n一段说明\nComment-End:"));

        f.delete();
        w.dispose();
    }

    // ---------------------------------------------------------------- 单击子项 = 打开

    @Test
    public void testSingleClickOnChildLoadsStateAndCloses() {
        myMaps.mState1.add(node(7, 10, 3, "s", "t"));
        myMaps.m_StateIsRedy = false;

        myStateBrow w = new myStateBrow();
        w.setSelection(0, 0);
        assertNotNull(w.getTree());
        w.openSelectedForTest();

        assertTrue("原版 set_State() 置 m_StateIsRedy = true", myMaps.m_StateIsRedy);
        assertFalse("原版 set_State() 里 finish()", w.isDisplayable());

        myMaps.m_StateIsRedy = false;
    }
}
