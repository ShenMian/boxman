package my.boxman;

import my.boxman.compat.HoloPopupMenu;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 阶段 G ③ —— {@link mySolutionBrow} 整体重做的验证。
 *
 * <p>原版是一个带 ActionBar 的 Activity + {@code ExpandableListView}（组「答案」+ 子项），
 * PC 此前是自造的模态 {@code JDialog} + 底部按钮栏 + {@code JOptionPane}，
 * 现已按原版重写。
 *
 * <p>⚠️ {@code myMaps.mState2} / {@code myMaps.m_State} 都是全局的，改过要在
 * {@code @After} 复位。
 */
public class Phase27SolutionBrowTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private static List<state_Node> savedState2;
    private static ans_Node savedState;

    private final List<java.awt.Window> toDispose = new ArrayList<java.awt.Window>();

    @Before
    public void setUp() {
        if (savedState2 == null) {
            savedState2 = new ArrayList<state_Node>(myMaps.mState2);
            savedState = myMaps.m_State;
        }
        myMaps.mState2.clear();
        myMaps.mState2.add(node(11L, "步数 10 / 推数 3", "2026-01-01 10:00"));
        myMaps.mState2.add(node(22L, "步数 20 / 推数 5", "2026-01-02 11:00"));
    }

    @After
    public void tearDown() {
        for (java.awt.Window w : toDispose) w.dispose();
        myMaps.mState2.clear();
        myMaps.mState2.addAll(savedState2);
        myMaps.m_State = savedState;
    }

    // ============================================================ 窗口外壳

    @Test
    public void windowIsAnActivityWithActionBarTitledSimilarLevels() {
        mySolutionBrow w = win();
        assertEquals("标题应为「相似关卡」", "相似关卡", w.actionBar.getBarTitle());
        assertTrue("应开返回折角", w.actionBar.isUpEnabled());
        assertFalse("原版没有 res/menu/solution*.xml，不该有 ⋮",
                w.actionBar.isOverflowVisible());
        assertTrue("原版是 Activity，PC 应是 JFrame", w instanceof javax.swing.JFrame);
    }

    // ============================================================ 列表结构

    @Test
    public void modelStartsWithTheGroupHeaderThenTheAnswers() {
        mySolutionBrow w = win();
        assertEquals("第 0 项应是组头", "答案", w.modelSolutions.getElementAt(0));
        assertEquals("答案", mySolutionBrow.GROUP_TITLE);
        assertEquals("组头 + 2 条答案", 3, w.modelSolutions.getSize());
        assertSame(myMaps.mState2.get(0), w.modelSolutions.getElementAt(1));
        assertSame(myMaps.mState2.get(1), w.modelSolutions.getElementAt(2));
    }

    @Test
    public void clickingAChildRemembersPositionAndId() {
        mySolutionBrow w = win();
        w.onRowAt(2);
        assertEquals("子项下标要减掉组头那一行", 1, w.c_Pos);
        assertEquals(22L, w.m_Sel_id);
    }

    @Test
    public void clickingTheGroupHeaderSelectsNothing() {
        mySolutionBrow w = win();
        w.onRowAt(0);
        assertEquals(-1, w.c_Pos);

        w.onRowAt(-1);                     // 空白处
        assertEquals(-1, w.c_Pos);
    }

    // ============================================================ 上下文菜单

    @Test
    public void contextMenuHasExactlyOneItemWithTheOriginalTitle() {
        mySolutionBrow w = win();
        JPopupMenu m = w.getContextMenu();
        assertNotNull(m);
        assertEquals(1, HoloPopupMenu.itemCount(m));
        List<String> titles = new ArrayList<String>();
        for (Component c : m.getComponents()) {
            assertTrue("上下文菜单项应走 HoloPopupMenu", c instanceof HoloPopupMenu.Row);
            titles.add(((HoloPopupMenu.Row) c).getText());
        }
        assertEquals("[导出到剪切板: Lurd]", titles.toString());
    }

    @Test
    public void contextItemDoesNothingWhenNoChildIsSelected() {
        mySolutionBrow w = win();
        w.dialogShower = dlg -> fail("没选子项时不该弹对话框");
        w.c_Pos = -1;
        w.onContextItemSelected(5);
        assertNull(w.getClipboardDialog());
    }

    @Test
    public void contextItemOpensTheClipboardDialog() {
        mySolutionBrow w = win();
        final JDialog[] shown = { null };
        w.dialogShower = dlg -> shown[0] = dlg;
        w.onRowAt(1);
        w.onContextItemSelected(5);
        assertNotNull("选中子项后应弹「剪切板：Lurd」对话框", shown[0]);
        assertNotNull("对话框里应有可编辑文本框", w.getClipArea());
        assertTrue("文本框要可编辑（原版是 EditText）", w.getClipArea().isEditable());
    }

    // ============================================================ 剪切板对话框

    @Test
    public void clipboardDialogPrefillsTheAnswer() {
        mySolutionBrow w = win();
        w.dialogShower = dlg -> { };
        myMaps.m_State = answer("rrddlluu");
        w.showClipboardDialog();
        assertEquals("rrddlluu", w.getClipArea().getText());
    }

    @Test
    public void clipboardDialogOkWritesTheEditedTextToTheClipboard() {
        mySolutionBrow w = win();
        w.dialogShower = dlg -> { };
        myMaps.m_State = answer("rrdd");
        w.showClipboardDialog();
        w.getClipArea().setText("llddrr");          // 用户改过再点确定

        AbstractButton ok = findButton(w.getClipboardDialog(), "确定");
        assertNotNull("应有「确定」按钮", ok);
        ok.doClick();

        assertEquals("确定后应把文本框内容写进剪切板", "llddrr", myMaps.loadClipper());
    }

    @Test
    public void clipboardDialogHandlesNullAnswerGracefully() {
        mySolutionBrow w = win();
        w.dialogShower = dlg -> { };
        myMaps.m_State = null;
        w.showClipboardDialog();
        assertEquals("", w.getClipArea().getText());
    }

    // ============================================================ 源码扫描锁

    @Test
    public void noJOptionPaneAnywhereInSolutionBrow() throws Exception {
        assertFalse("不该再有 JOptionPane",
                codeOf("mySolutionBrow.java").contains("JOptionPane"));
    }

    @Test
    public void noSelfInventedBottomBar() throws Exception {
        String src = codeOf("mySolutionBrow.java");
        assertFalse("删掉了自造的「复制到剪贴板」按钮", src.contains("复制到剪贴板"));
        assertFalse("删掉了自造的「关闭」按钮", src.contains("\"关闭\""));
    }

    @Test
    public void usesHoloCarriers() throws Exception {
        String src = codeOf("mySolutionBrow.java");
        assertTrue("应走 HoloPopupMenu", src.contains("HoloPopupMenu.create()"));
        assertTrue("应走 HoloAlertDialog", src.contains("HoloAlertDialog.create"));
        assertTrue("应走 myActionBar", src.contains("new myActionBar()"));
    }

    // ============================================================ 工具

    private mySolutionBrow win() {
        mySolutionBrow w = new mySolutionBrow(null);
        toDispose.add(w);
        return w;
    }

    private static state_Node node(long id, String inf, String time) {
        state_Node nd = new state_Node();
        nd.id = id;
        nd.inf = inf;
        nd.time = time;
        return nd;
    }

    private static ans_Node answer(String ans) {
        ans_Node a = new ans_Node();
        a.ans = ans;
        return a;
    }

    private static AbstractButton findButton(Container root, String text) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText())) {
                return (AbstractButton) c;
            }
            if (c instanceof Container) {
                AbstractButton hit = findButton((Container) c, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /**
     * 源码扫描**必须先剥注释** —— 类注释里会提到「删掉了 JOptionPane」这类字样，
     * 不剥就会命中（见 TEST_NOTES 的「源码扫描式约定锁」）。
     */
    private static String codeOf(String name) throws Exception {
        String src = sourceOf(name);
        src = src.replaceAll("(?s)/\\*.*?\\*/", "");
        src = src.replaceAll("//[^\\n]*", "");
        return src;
    }

    private static String sourceOf(String name) throws Exception {
        java.io.File f = new java.io.File("src/main/java/my/boxman/" + name);
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }
}
