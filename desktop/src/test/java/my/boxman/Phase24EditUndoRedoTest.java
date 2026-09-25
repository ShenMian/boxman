package my.boxman;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.SwingUtilities;

import static org.junit.Assert.*;

/**
 * 阶段 G ⑥：核 {@code myEditView} 撤销栈的 {@code Act(...)} 语义。
 *
 * <p>覆盖两件事：
 * <ol>
 *   <li>{@code myUnDo()} / {@code myReDo()} 按 {@code nd.act} **分四类**还原现场
 *       （原版 `myEditView.java:1097-1209`）—— 单点/连续绘制、填充·剪切·粘贴·变换（含选区）、
 *       改变尺寸·标准化·剪切板导入·提交（含四至与变换矩阵）；</li>
 *   <li>{@code myRotate(int)} 的 5 个分支（原版 `myEditView.java:1622-1691`）——
 *       此前 PC 只实现了 3/4（水平/垂直翻转），**0/1/2 是空操作**；
 *       以及 `resetSize()` / `myRot90()` 这两段此前完全缺失的代码。</li>
 * </ol>
 *
 * <p>撤销/重做走 {@code bt_UnDo.doClick()} / {@code bt_ReDo.doClick()}，
 * 因为 {@code myUnDo}/{@code myReDo} 是 private —— 与原版一致。
 */
public class Phase24EditUndoRedoTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 测试用的舒适四至（离数组边界很远，避免 initArena 之类把尺寸夹掉）。 */
    private static final int T = 2, L = 2, B = 20, R = 20;

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase24").getAbsolutePath();
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;
        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Before
    public void setUp() {
        // myEditView 的构造器里 initMap() 会读 myMaps.curMap（null 时走默认 9x9 之类），
        // 这里先给一个合法关卡，保证初始化路径和真实使用一致。
        myMaps.curMap = new mapNode("#####\n#@$.#\n#####", "L1", "A", "");
        assertNotNull("夹具地图必须合法", myMaps.curMap.Map0);
    }

    /** 造一个干净的编辑器：四至锁到 {@link #T}/{@link #L}/{@link #B}/{@link #R}，区域全填 '-'。 */
    private myEditView freshEditor() {
        myEditView ed = new myEditView();
        // ⚠️ 构造器里的 UiWindow.applyPhoneSize() 会 pack()，Swing 随后会**异步**派发一次
        //    componentResized → myEditViewMap.setArena() → selNode.row = -1。
        //    不排空 EDT 的话，这一下会随机落在断言中间，把刚设好的选区清掉
        //    （踩过：两条 assertEquals 之间 selNode.row 从 1 变成 -1，导致 undo 的选区还原分支被跳过）。
        drainEdt();
        ed.mMap.m_nMapTop = T;
        ed.mMap.m_nMapBottom = B;
        ed.mMap.m_nMapLeft = L;
        ed.mMap.m_nMapRight = R;
        for (int r = T; r <= B; r++) {
            for (int c = L; c <= R; c++) {
                ed.m_cArray[r][c] = '-';
            }
        }
        return ed;
    }

    /** 排空 EDT 队列 —— 让 pack() 引发的 componentResized 之类异步事件先落地。 */
    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
    }

    /** 把选区设成「相对坐标」的 (r1,c1)-(r2,c2)。 */
    private static void select(myEditView ed, int r1, int c1, int r2, int c2) {
        ed.mMap.selNode.row = r1;
        ed.mMap.selNode.col = c1;
        ed.mMap.selNode2.row = r2;
        ed.mMap.selNode2.col = c2;
    }

    // ==================================================================
    // 一、myUnDo / myReDo 的 act 分类
    // ==================================================================

    /**
     * {@code act == 3}（单点绘制）**只回写一个格子**。
     *
     * <p>这是原版刻意为之：`myEditViewMap` 用 {@code @} 素材绘制时会顺手抹掉全图其它
     * {@code @}/{@code +}（「仓管员只能留存 1 位」），但单点绘制的 undo 并不把那些仓管员找回来。
     * 所以这里特意断言「相邻格子**不**被还原」—— 整片回写能「修好」这个现象，但那就不是 1:1 了。
     */
    @Test
    public void testUndoAct3RestoresOnlyTheClickedCell() {
        myEditView ed = freshEditor();
        int r = T + 3, c = L + 4;
        ed.m_cArray[r][c] = '#';
        ed.m_cArray[r][c + 1] = '$';           // 相邻格子，不在 undo 范围内
        ed.mMap.m_iR = 3;
        ed.mMap.m_iC = 4;

        ed.DoAct(3);                            // 快照：此时 (r,c)='#'
        assertEquals("快照应记成 act 3", 3, ed.m_UnDoList.peekLast().act);

        // 模拟绘制：被点格 + 相邻格都变了
        ed.m_cArray[r][c] = '.';
        ed.m_cArray[r][c + 1] = '*';

        ed.bt_UnDo.doClick();
        assertEquals("被点格应还原", '#', ed.m_cArray[r][c]);
        assertEquals("单点绘制只回写一个格子，相邻格不该被还原", '*', ed.m_cArray[r][c + 1]);
        assertEquals("单点绘制 undo 后应隐藏选区", -1, ed.mMap.selNode.row);
        assertTrue("undo 末尾无条件置 isFistClick = true", ed.mMap.isFistClick);
    }

    @Test
    public void testRedoAct3WritesTheCellBack() {
        myEditView ed = freshEditor();
        int r = T + 3, c = L + 4;
        ed.m_cArray[r][c] = '#';
        ed.mMap.m_iR = 3;
        ed.mMap.m_iC = 4;
        ed.DoAct(3);
        ed.m_cArray[r][c] = '.';

        ed.bt_UnDo.doClick();
        assertEquals('#', ed.m_cArray[r][c]);
        assertTrue("撤销后重做按钮应可用", ed.bt_ReDo.isEnabled());

        ed.bt_ReDo.doClick();
        assertEquals("重做应把绘制结果写回", '.', ed.m_cArray[r][c]);
        assertTrue("重做后撤销按钮应可用", ed.bt_UnDo.isEnabled());
    }

    /** {@code act == 6}（连续绘制）**整片回写**。 */
    @Test
    public void testUndoAct6RestoresWholeRegion() {
        myEditView ed = freshEditor();
        int r = T + 3, c = L + 4;
        ed.m_cArray[r][c] = '#';
        ed.m_cArray[r + 1][c] = '$';
        ed.mMap.m_iR = 3;
        ed.mMap.m_iC = 4;

        ed.DoAct(6);
        assertEquals("快照应记成 act 6", 6, ed.m_UnDoList.peekLast().act);

        ed.m_cArray[r][c] = '.';
        ed.m_cArray[r + 1][c] = '*';

        ed.bt_UnDo.doClick();
        assertEquals("连续绘制 undo 应整片回写", '#', ed.m_cArray[r][c]);
        assertEquals("连续绘制 undo 应整片回写（相邻格也要回来）", '$', ed.m_cArray[r + 1][c]);
        assertEquals("连续绘制 undo 后应隐藏选区", -1, ed.mMap.selNode.row);
    }

    /**
     * {@code act ∈ {0,1,2,5}}：整片回写地图，选区**不动**。
     *
     * <p>⚠️ 这里没有「选区被还原」这回事 —— {@code ActNode.sel1/sel2} 与原版一样是
     * **引用**（指向 {@code mMap.selNode}/{@code selNode2} 本体），所以那个还原块
     * 对 act 5 来说只是把「当前值」重算一遍，等于空操作。真正起作用的是地图回写。
     */
    @Test
    public void testUndoAct5RestoresMapAndLeavesSelectionAlone() {
        myEditView ed = freshEditor();
        int r = T + 3, c = L + 4;
        ed.m_cArray[r][c] = '#';
        ed.m_cArray[r][c + 1] = '$';
        select(ed, 3, 4, 5, 8);

        myEditView.ActNode nd = new myEditView.ActNode(ed.m_cArray, L, R, T, B,
                -1, -1, null, null, ed.mMap.selNode, ed.mMap.selNode2);
        nd.Act(5);
        ed.m_UnDoList.offer(nd);
        ed.bt_UnDo.setEnabled(true);

        ed.m_cArray[r][c] = '.';
        ed.m_cArray[r][c + 1] = '*';

        ed.bt_UnDo.doClick();

        assertEquals("act 5 应整片回写地图", '#', ed.m_cArray[r][c]);
        assertEquals("act 5 应整片回写地图", '$', ed.m_cArray[r][c + 1]);
        assertEquals("act 5 不改变选区", 3, ed.mMap.selNode.row);
        assertEquals("act 5 不改变选区", 4, ed.mMap.selNode.col);
    }

    /**
     * {@code act == 90}：还原选区时**行列对调**（原版 `myEditView.java:1118-1120`）。
     *
     * <p>时序必须照 {@link myEditView#writeRot90(boolean)} 来：先按**旋转前**的选区建快照
     * （此时 selNode2 还是旋转前的角点），再把选区改成**旋转后**的尺寸 ——
     * 因为快照存的是引用，undo 读到的 sel2 已经是旋转后的值，对调之后才换得回来。
     */
    @Test
    public void testUndoAct90SwapsSelectionDims() {
        myEditView ed = freshEditor();
        select(ed, 3, 4, 5, 8);                // 旋转前：Δrow=2, Δcol=4 → 3 行 5 列

        myEditView.ActNode nd = new myEditView.ActNode(ed.m_cArray, L, R, T, B,
                -1, -1, null, null, ed.mMap.selNode, ed.mMap.selNode2);
        nd.Act(90);
        ed.m_UnDoList.offer(nd);
        ed.bt_UnDo.setEnabled(true);

        // 模拟旋转完成：行列对调 → 5 行 3 列，selNode2 = (3+5-1, 4+3-1) = (7,6)
        select(ed, 3, 4, 7, 6);
        ed.bt_UnDo.doClick();

        assertEquals("90 度：selRows = Δcol+1 = 3", 3, ed.selRows);
        assertEquals("90 度：selCols = Δrow+1 = 5", 5, ed.selCols);
        assertEquals(3, ed.mMap.selNode.row);
        assertEquals(4, ed.mMap.selNode.col);
        assertEquals("selNode2.row = selNode.row + selRows - 1", 5, ed.mMap.selNode2.row);
        assertEquals("selNode2.col = selNode.col + selCols - 1", 8, ed.mMap.selNode2.col);
    }

    /**
     * {@code act ∈ {4,9,8,7}}：还原**四至**与变换矩阵（原版 `myEditView.java:1130-1143`）。
     *
     * <p>注意 `initArena3()` 里 `mMatrix = getInnerMatrix(mMatrix)` 会把 mMatrix **整个重算**，
     * 所以 `mMatrix` 的还原其实会被覆盖掉（原版也是这个顺序，照抄）；
     * 真正生效的是 `mCurrentMatrix` —— `initArena3()` 用它取 `m_fTop`/`m_fLeft`。
     */
    @Test
    public void testUndoAct4RestoresBoundsAndCurrentMatrix() {
        myEditView ed = freshEditor();

        myEditView.ActNode nd = new myEditView.ActNode(ed.m_cArray, L, R, T, B,
                -1, -1, ed.mMap.mMatrix, ed.mMap.mCurrentMatrix, null, null);
        nd.Act(4);
        ed.m_UnDoList.offer(nd);
        ed.bt_UnDo.setEnabled(true);

        // 改四至 + 改 mCurrentMatrix（模拟「修改尺寸」把画布挪了）
        ed.mMap.m_nMapTop = T + 5;
        ed.mMap.m_nMapBottom = B + 5;
        ed.mMap.m_nMapLeft = L + 5;
        ed.mMap.m_nMapRight = R + 5;
        float[] bumped = new float[9];
        ed.mMap.mCurrentMatrix.getValues(bumped);
        bumped[my.boxman.compat.android.graphics.Matrix.MTRANS_X] = 77f;
        ed.mMap.mCurrentMatrix.setValues(bumped);

        ed.bt_UnDo.doClick();

        assertEquals("四至 top 应还原", T, ed.mMap.m_nMapTop);
        assertEquals("四至 bottom 应还原", B, ed.mMap.m_nMapBottom);
        assertEquals("四至 left 应还原", L, ed.mMap.m_nMapLeft);
        assertEquals("四至 right 应还原", R, ed.mMap.m_nMapRight);

        float[] after = new float[9];
        ed.mMap.mCurrentMatrix.getValues(after);
        assertEquals("mCurrentMatrix 应还原（原版会 set(nd.mCurMtx)）",
                0f, after[my.boxman.compat.android.graphics.Matrix.MTRANS_X], 0.0001f);
    }

    @Test
    public void testRedoAct4PushesBackToUndoStack() {
        myEditView ed = freshEditor();
        myEditView.ActNode nd = new myEditView.ActNode(ed.m_cArray, L, R, T, B,
                -1, -1, ed.mMap.mMatrix, ed.mMap.mCurrentMatrix, null, null);
        nd.Act(4);
        ed.m_UnDoList.offer(nd);
        ed.bt_UnDo.setEnabled(true);

        ed.mMap.m_nMapTop = T + 5;
        ed.bt_UnDo.doClick();
        assertEquals(T, ed.mMap.m_nMapTop);
        assertEquals("撤销后重做栈应有一条", 1, ed.m_ReDoList.size());

        ed.bt_ReDo.doClick();
        assertEquals("重做应把新四至写回", T + 5, ed.mMap.m_nMapTop);
        assertEquals("重做后撤销栈应重新有一条", 1, ed.m_UnDoList.size());
    }

    // ==================================================================
    // 二、myRotate 的 5 个分支（原版 myEditView.java:1622-1691）
    // ==================================================================

    /**
     * 铺一个 2x3 的选区：
     * <pre>
     *   # $ .
     *   @ - *
     * </pre>
     * 选区的相对坐标 (1,1)-(2,3)，即绝对 (3,3)-(4,5)。
     */
    private myEditView editorWithPattern() {
        myEditView ed = freshEditor();
        ed.m_cArray[T + 1][L + 1] = '#';
        ed.m_cArray[T + 1][L + 2] = '$';
        ed.m_cArray[T + 1][L + 3] = '.';
        ed.m_cArray[T + 2][L + 1] = '@';
        ed.m_cArray[T + 2][L + 2] = '-';
        ed.m_cArray[T + 2][L + 3] = '*';
        select(ed, 1, 1, 2, 3);
        return ed;
    }

    private static String row(myEditView ed, int r, int c1, int c2) {
        StringBuilder sb = new StringBuilder();
        for (int c = c1; c <= c2; c++) sb.append(ed.m_cArray[r][c]);
        return sb.toString();
    }

    @Test
    public void testRotate180() {
        myEditView ed = editorWithPattern();
        ed.myRotate(0);

        assertEquals("*-@", row(ed, T + 1, L + 1, L + 3));
        assertEquals(".$#", row(ed, T + 2, L + 1, L + 3));
        assertEquals("180 度走 act 5（四至不变）", 5, ed.m_UnDoList.peekLast().act);
    }

    @Test
    public void testRotateClockwise90() {
        myEditView ed = editorWithPattern();
        ed.myRotate(1);

        // 顺时针 90 度后是 3 行 2 列
        assertEquals("@#", row(ed, T + 1, L + 1, L + 2));
        assertEquals("-$", row(ed, T + 2, L + 1, L + 2));
        assertEquals("*.", row(ed, T + 3, L + 1, L + 2));
        assertEquals("90 度走 act 90", 90, ed.m_UnDoList.peekLast().act);
        assertEquals("旋转后选区行数 = 原列数", 3, ed.selRows);
        assertEquals("旋转后选区列数 = 原行数", 2, ed.selCols);
        assertEquals("selNode2.row 应跟着更新", 1 + 3 - 1, ed.mMap.selNode2.row);
        assertEquals("selNode2.col 应跟着更新", 1 + 2 - 1, ed.mMap.selNode2.col);
    }

    @Test
    public void testRotateCounterClockwise90() {
        myEditView ed = editorWithPattern();
        ed.myRotate(2);

        assertEquals(".*", row(ed, T + 1, L + 1, L + 2));
        assertEquals("$-", row(ed, T + 2, L + 1, L + 2));
        assertEquals("#@", row(ed, T + 3, L + 1, L + 2));
        assertEquals("逆 90 度同样走 act 90", 90, ed.m_UnDoList.peekLast().act);
    }

    @Test
    public void testRotateHorizontalFlip() {
        myEditView ed = editorWithPattern();
        ed.myRotate(3);

        assertEquals(".$#", row(ed, T + 1, L + 1, L + 3));
        assertEquals("*-@", row(ed, T + 2, L + 1, L + 3));
        assertEquals(5, ed.m_UnDoList.peekLast().act);
    }

    @Test
    public void testRotateVerticalFlip() {
        myEditView ed = editorWithPattern();
        ed.myRotate(4);

        assertEquals("@-*", row(ed, T + 1, L + 1, L + 3));
        assertEquals("#$.", row(ed, T + 2, L + 1, L + 3));
        assertEquals(5, ed.m_UnDoList.peekLast().act);
    }

    /** 变换是可撤销的：90 度旋转后撤销应把选区尺寸也还原（`act == 90` 的对调分支），重做再换回去。 */
    @Test
    public void testUndoAfter90RotationRestoresOriginalDims() {
        myEditView ed = editorWithPattern();
        ed.myRotate(1);
        assertEquals(3, ed.selRows);
        assertEquals(2, ed.selCols);

        ed.bt_UnDo.doClick();
        assertEquals("撤销后选区行数回到 2", 2, ed.selRows);
        assertEquals("撤销后选区列数回到 3", 3, ed.selCols);
        assertEquals("地图内容应还原", "#$.", row(ed, T + 1, L + 1, L + 3));
        assertEquals("地图内容应还原", "@-*", row(ed, T + 2, L + 1, L + 3));

        ed.bt_ReDo.doClick();
        assertEquals("重做后选区行数回到 3", 3, ed.selRows);
        assertEquals("重做后选区列数回到 2", 2, ed.selCols);
        assertEquals("重做后地图内容应再次旋转", "@#", row(ed, T + 1, L + 1, L + 2));
        assertEquals("重做后地图内容应再次旋转", "*.", row(ed, T + 3, L + 1, L + 2));
    }

    // ==================================================================
    // 三、resetSize（原版 myEditView.java:1693-1708）
    // ==================================================================

    @Test
    public void testResetSizeExpandsBounds() {
        myEditView ed = freshEditor();
        ed.mMap.m_nMapTop = T;
        ed.mMap.m_nMapBottom = T + 2;           // 只有 3 行
        ed.mMap.m_nMapLeft = L;
        ed.mMap.m_nMapRight = L + 2;
        select(ed, 1, 1, 1, 1);
        ed.selRows2 = 5;
        ed.selCols2 = 5;

        ed.resetSize();

        assertEquals("下边界应扩到 selNode.row + selRows2 + top - 1", T + 1 + 5 - 1, ed.mMap.m_nMapBottom);
        assertEquals("右边界应扩到 selNode.col + selCols2 + left - 1", L + 1 + 5 - 1, ed.mMap.m_nMapRight);
        assertEquals("尺寸没超上限，不该夹取", 5, ed.selRows2);
        assertEquals(5, ed.selCols2);
    }

    @Test
    public void testResetSizeClampsToMaxAndLeavesBoundsWhenTheyFit() {
        myEditView ed = freshEditor();
        select(ed, 1, 1, 1, 1);

        // ① 装得下 → 什么都不动
        ed.selRows2 = 3;
        ed.selCols2 = 3;
        ed.resetSize();
        assertEquals(B, ed.mMap.m_nMapBottom);
        assertEquals(R, ed.mMap.m_nMapRight);

        // ② 装不下且超上限 → 夹到 m_nMaxRow*2-1
        ed.selRows2 = 500;
        ed.selCols2 = 500;
        ed.resetSize();
        assertEquals("应夹到 myMaps.m_nMaxRow*2-1 - top - selNode.row",
                myMaps.m_nMaxRow * 2 - 1 - T - 1, ed.selRows2);
        assertEquals(myMaps.m_nMaxCol * 2 - 1 - L - 1, ed.selCols2);
        assertEquals("下边界 = top + selNode.row + selRows2 - 1",
                T + 1 + ed.selRows2 - 1, ed.mMap.m_nMapBottom);
    }

    // ==================================================================
    // 四、约定：变换菜单不再用 PC 自造的 JOptionPane
    // ==================================================================

    @Test
    public void testEditViewHasNoJOptionPane() throws Exception {
        File src = new File("src/main/java/my/boxman/myEditView.java");
        assertTrue("源码文件应存在: " + src.getAbsolutePath(), src.isFile());
        String text = new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8);
        assertFalse("myEditView 不该再用 PC 自造的 JOptionPane（应走 compat 的 Holo 对话框）",
                text.contains("JOptionPane"));
    }
}
