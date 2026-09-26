package my.boxman;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * 原版 9.99u3 修的「互动双推模式下数箱子不对」—— 逆推计数在 {@code m_Sets[13] == 1} 时
 * **不能只看逆推棋盘**。
 *
 * <p>互动双推下，逆推棋盘 {@code bk_cArray} 里的「目标」其实等于「正推棋盘 {@code m_cArray}
 * 上此刻摆着箱子的那些格子」（原版注释：{@code counting the goals means counting the boxes on
 * their current forward play position}）。所以目标数必须从 {@code m_cArray} 读，
 * 箱子数才从 {@code bk_cArray} 读。
 *
 * <p>测试用的棋盘（3×5，选区覆盖整块）：
 * <pre>
 *   正推 m_cArray : ---- / @$.-  ----     箱子在 (1,2)，目标在 (1,3)
 *   逆推 bk_cArray: ---- / @$$-  ----     箱子在 (1,2) 与 (1,3)
 * </pre>
 * 互动双推 → 箱子 2、目标 1、完成 1（(1,2) 上两副棋盘都有箱子）；
 * 普通逆推（旧算法，只看 {@code bk_cArray}）→ 箱子 2、目标 0、完成 0。
 * 两种模式**箱子数相同而目标/完成数不同**，正好能锁住「有没有走新分支」。
 */
public class Phase35DoublePushCountTest {

    private static final String LEVEL = "#####\n#@$.#\n#####";

    private myGameView win;
    private mapNode savedCur;
    private ArrayList<mapNode> savedList;
    private String savedRoot, savedPath;
    private int savedTrun;
    private final int[] savedSets = new int[40];

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase35";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();

        mySQLite.getInstance().openDataBase();
        myMaps.loadSkins();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedList = myMaps.m_lstMaps;
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        savedTrun = myMaps.m_nTrun;
        System.arraycopy(myMaps.m_Sets, 0, savedSets, 0, savedSets.length);

        myMaps.curMap = new mapNode(LEVEL, "双推计数测试关", "测试", "");
        myMaps.curMap.fileName = "phase35_test.XSB";
        myMaps.curMapNum = -4;
        myMaps.curMap.Level_id = 1;
        myMaps.m_nTrun = 0;
        myMaps.m_lstMaps = new ArrayList<mapNode>();
        myMaps.m_lstMaps.add(myMaps.curMap);
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.myStop();
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.m_lstMaps = savedList;
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
        myMaps.m_nTrun = savedTrun;
        System.arraycopy(savedSets, 0, myMaps.m_Sets, 0, savedSets.length);
    }

    // ---------------------------------------------------------------- 1. 互动双推

    /** {@code m_Sets[13] == 1}：目标数 / 完成数按**正推**棋盘上的箱子算。 */
    @Test
    public void inDoublePushModeReverseGoalsAreCountedOnTheForwardBoard() throws Exception {
        assertReverseCount(true, 2, 1, 1);
    }

    /** 普通逆推仍是原算法：只看 {@code bk_cArray}，与互动双推的结果不同。 */
    @Test
    public void normalReverseCountingStillReadsOnlyTheBackwardBoard() throws Exception {
        assertReverseCount(false, 2, 0, 0);
    }

    // ---------------------------------------------------------------- 工具

    private void assertReverseCount(boolean doublePush, int boxes, int goals, int onGoal) throws Exception {
        myGameView g = new myGameView();
        win = g;
        myGameViewMap map = g.mMap;

        myMaps.m_Sets[13] = doublePush ? 1 : 0;

        // 计数 + 逆推都打开（bt_Sel 的监听器会把 bk_selArray 清零，正好从空选区开始）
        g.bt_BK.setChecked(true);
        g.bt_Sel.setChecked(true);

        int rows = 3, cols = 5;
        char[][] fw = new char[rows][cols];
        char[][] bk = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(fw[r], '-');
            Arrays.fill(bk[r], '-');
        }
        fw[1][1] = '@';
        bk[1][1] = '@';
        fw[1][2] = '$';   // 正推：一个箱子
        fw[1][3] = '.';   // 正推：一个目标
        bk[1][2] = '$';   // 逆推：箱子（与正推箱子同位）
        bk[1][3] = '$';   // 逆推：箱子
        g.m_cArray = fw;
        g.bk_cArray = bk;
        g.bk_selArray = new byte[rows][cols];

        map.m_nRows = rows;
        map.m_nCols = cols;
        map.m_fLeft = 0;
        map.m_fTop = 0;
        map.m_fScale = 1;
        map.m_lGoto = false;
        map.m_lGoto2 = false;
        map.m_lParityBrightnessShade = false;
        g.m_bBusing = false;

        // selNode 的 r2 < 0 表示「还没定第一个角」，doACT 里那次 setPT 会把
        // [r1..r][c1..c] 整块置 1 —— 锚点放 (0,0)、点在右下角就是全盘选中
        map.selNode2.r1 = 0;
        map.selNode2.c1 = 0;
        map.selNode2.r2 = -1;
        map.selNode2.c2 = -1;

        doAct(map, (cols - 1) * 50 + 10, (rows - 1) * 50 + 10);

        assertEquals("逆推箱子数", boxes, map.m_Count[3]);
        assertEquals("逆推目标数", goals, map.m_Count[4]);
        assertEquals("逆推完成数", onGoal, map.m_Count[5]);
    }

    /** {@code doACT} 是 private，只有鼠标事件能进；这里直接反射调用，避免模拟真实点击。 */
    private static void doAct(myGameViewMap map, int x, int y) throws Exception {
        Method m = myGameViewMap.class.getDeclaredMethod("doACT", int.class, int.class, boolean.class);
        m.setAccessible(true);
        m.invoke(map, x, y, true);
    }
}
