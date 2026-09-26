package my.boxman;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * BUG 回归 —— 「通关后跳下一个未解关卡，可能直接弹出死锁警告」。
 *
 * <p><b>根因：</b>关卡装载时启动的后台任务 {@code AsyncCountBoxsTask} 算的是**上一关**的
 * 逆推死锁数据；切关时 {@code initMap()} 虽然会 {@code cancel(true)}，但
 * {@code SwingWorker.cancel()} 在 {@code doInBackground()} <b>已经跑完</b>之后是空操作
 * （返回 {@code false}，{@code isCancelled()} 保持 {@code false}）。而「通关→跳下一关」正好
 * 发生在上一关刚算完的瞬间，于是旧任务的 {@code done()} 照常执行：
 *
 * <ol>
 *   <li>把旧关卡的 {@code mark14/15/16}、{@code mArray9} 写进视图，覆盖掉新关卡的死锁数据
 *       （随后 {@code myLock()} 就会拿旧数据误报死锁）；</li>
 *   <li>弹出旧关卡的「这是一个无解的关卡！」——此时屏幕上已经是新关卡了。</li>
 * </ol>
 *
 * <p><b>修法（对齐原版）：</b>原版把提醒放在 {@code onProgressUpdate()} 里，由
 * {@code doInBackground()} 中途的 {@code publishProgress()} 触发，所以提醒在**本关还在屏幕上**
 * 时就发出去了，不会漂移到下一关；并且提醒后立刻 {@code m_bNoSolution = false}。
 * 端口这边改成 {@code publish()} / {@code process()}，并给 {@code process()} / {@code done()}
 * 都加上「本任务是否仍是当前关卡的任务」这道闸门（{@link my.boxman.myGameView.AsyncCountBoxsTask#isCurrent()}）。
 *
 * <p>另外顺手修掉一个同类隐患：地图尺寸原先是在 {@code doInBackground()} 里读
 * {@code myMaps.curMap.Rows/Cols}，后台线程真正起跑时关卡可能已经切走 —— 会拿**旧行数**去读
 * **新地图**。原版是 {@code execute(rows, cols)}，在 UI 线程捕获；现在挪进构造器（同样在 EDT 上）。
 */
public class Phase30StaleTaskGuardTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 一个合法的小关卡：3 行 × 5 列，1 个仓管员、1 箱 1 目标。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    private myGameView win;
    private mapNode savedCur;
    private String savedRoot, savedPath;
    private int savedTrun;

    /** 收集被「弹」出来的对话框（走 {@code dialogShower} 缝，只记不弹，否则模态框会挂死用例）。 */
    private final List<JDialog> shown = new ArrayList<JDialog>();

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase30";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();

        mySQLite.getInstance().openDataBase();
        myMaps.loadSkins();
    }

    @Before
    public void setUp() throws Exception {
        savedCur = myMaps.curMap;
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        savedTrun = myMaps.m_nTrun;

        // ⚠️ 必须先给 curMap，否则 myGameView 的 initGame() 会因 curMap == null 直接 return，
        //    m_cArray / m_cArray0 全是 null，构造 AsyncCountBoxsTask 时就取不到尺寸。
        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase30_test.XSB";
        myMaps.curMapNum = -4;
        myMaps.m_nTrun = 0;

        win = new myGameView();
        win.dialogShower = dlg -> shown.add(dlg);

        // 构造器里 initGame() → initMap() 已经起了一个任务，先等它落定，
        // 免得它在本用例的断言中间往 mark15/mark16 上写东西。
        awaitTask(win.mTask);
    }

    @After
    public void tearDown() {
        for (JDialog d : shown) {
            d.dispose();
        }
        shown.clear();

        if (win != null) {
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
        myMaps.m_nTrun = savedTrun;
    }

    // ---------------------------------------------------------------- 闸门本体

    /** 切关（{@code initMap()} 换了 {@code mTask}）之后，旧任务必须自认「不是当前任务」。 */
    @Test
    public void testSupersededTaskIsNoLongerCurrent() {
        myGameView.AsyncCountBoxsTask old = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = old;
        assertTrue("刚挂上去的任务就是当前任务", old.isCurrent());

        myGameView.AsyncCountBoxsTask next = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = next;   // 模拟 initMap() 里 mTask = new AsyncCountBoxsTask(this)

        assertFalse("切关后旧任务必须不再是当前任务", old.isCurrent());
        assertTrue("新任务应当是当前任务", next.isCurrent());
    }

    /** 切关后旧任务不得再弹「这是一个无解的关卡！」——这就是用户看到的那个 bug。 */
    @Test
    public void testSupersededTaskDoesNotWarn() {
        myGameView.AsyncCountBoxsTask old = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = old;
        old.m_bNoSolution = true;   // 旧关卡确实无解

        win.mTask = new myGameView.AsyncCountBoxsTask(win);   // 用户通关，跳到下一关
        old.process(Collections.<Void>emptyList());

        assertTrue("旧关卡的「无解」提醒不能弹在新关卡上", shown.isEmpty());
    }

    /** 正向对照：当前任务仍要提醒，且提醒后立刻复位（否则会重复弹）。 */
    @Test
    public void testCurrentTaskStillWarns() {
        myGameView.AsyncCountBoxsTask cur = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = cur;
        cur.m_bNoSolution = true;

        cur.process(Collections.<Void>emptyList());

        assertEquals("当前关卡确实无解时必须提醒", 1, shown.size());
        assertFalse("提醒后必须复位 m_bNoSolution，避免重复提醒", cur.m_bNoSolution);
    }

    /** 同一个任务提醒过一次之后，再 process 不应重复弹。 */
    @Test
    public void testWarnHappensOnlyOnce() {
        myGameView.AsyncCountBoxsTask cur = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = cur;
        cur.m_bNoSolution = true;

        cur.process(Collections.<Void>emptyList());
        cur.process(Collections.<Void>emptyList());

        assertEquals("同一任务只提醒一次", 1, shown.size());
    }

    // ---------------------------------------------------------------- 数据不被旧关卡覆盖

    /** 旧任务的 {@code done()} 不得把新关卡的 {@code mark14} / {@code mArray9} 覆盖掉。 */
    @Test
    public void testSupersededTaskDoesNotOverwriteLockData() throws Exception {
        myGameView.AsyncCountBoxsTask old = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = old;
        old.execute();
        old.get();     // 旧关卡算完了（必须真的跑过，否则直接调 done() 里的 get() 会永久阻塞）

        // 用户通关 → 跳下一关：initMap() 换上新任务（其构造器会清空这四个字段）
        myGameView.AsyncCountBoxsTask next = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = next;

        // 新任务算出来的结果（这里用替身代表）
        short[][] fresh14 = new short[1][1];
        char[][] fresh9 = new char[1][1];
        win.mark14 = fresh14;
        win.mArray9 = fresh9;

        old.done();   // 旧任务的 done() 这时才被 EDT 排到

        assertSame("旧任务不得覆盖新关卡的 mark14", fresh14, win.mark14);
        assertSame("旧任务不得覆盖新关卡的 mArray9", fresh9, win.mArray9);
    }

    /** 当前任务正常算完后，结果照常写回 —— 闸门不能把正常路径也挡掉。 */
    @Test
    public void testCurrentTaskStillPublishesResult() throws Exception {
        myGameView.AsyncCountBoxsTask task = new myGameView.AsyncCountBoxsTask(win);
        win.mTask = task;
        task.execute();

        final short[][] expected14 = task.get();   // 等后台算完
        assertNotNull("正常关卡应当算出逆推区域数据", expected14);

        // ⚠️ done() 是 worker 线程排到 EDT 上的，可能晚于 get() 返回，所以要等它落定。
        assertTrue("当前任务的结果应当写回 mark14", awaitUntil(() -> win.mark14 == expected14, 2000));
        assertSame("当前任务的地图应当写回 mArray9", task.mArray, win.mArray9);
    }

    // ---------------------------------------------------------------- 尺寸捕获时机

    /** 尺寸必须在构造器（EDT）里捕获，而不是在后台线程里读 {@code myMaps.curMap}。 */
    @Test
    public void testLevelSizeIsCapturedOnConstruction() {
        myGameView.AsyncCountBoxsTask task = new myGameView.AsyncCountBoxsTask(win);
        assertEquals("应当捕获到本关的行数", 3, task.m_Rows);
        assertEquals("应当捕获到本关的列数", 5, task.m_Cols);

        // 后台线程起跑前关卡被切走（换成另一尺寸：4 行 × 6 列）
        myMaps.curMap = new mapNode("######\n#....#\n#@$..#\n######", "换关", "测试", "");

        assertEquals("切关后旧任务仍应持有构造时捕获的行数", 3, task.m_Rows);
        assertEquals("切关后旧任务仍应持有构造时捕获的列数", 5, task.m_Cols);
    }

    /** 构造任务时会清掉上一轮的死锁数据、复位「无解」标志（原版 {@code onPreExecute()}）。 */
    @Test
    public void testConstructionResetsPreviousRoundData() {
        win.mark14 = new short[1][1];
        win.mark15 = new boolean[1][1];
        win.mark16 = new boolean[1][1];
        win.mArray9 = new char[1][1];

        myGameView.AsyncCountBoxsTask task = new myGameView.AsyncCountBoxsTask(win);

        org.junit.Assert.assertNull("构造新任务时应清掉上一轮的 mark14", win.mark14);
        org.junit.Assert.assertNull("构造新任务时应清掉上一轮的 mark15", win.mark15);
        org.junit.Assert.assertNull("构造新任务时应清掉上一轮的 mark16", win.mark16);
        org.junit.Assert.assertNull("构造新任务时应清掉上一轮的 mArray9", win.mArray9);
        assertFalse("新任务的「无解」标志应当是 false", task.m_bNoSolution);
    }

    // ---------------------------------------------------------------- 辅助

    /** 等一个任务跑完（{@code execute()} 过的任务 get() 会返回；没起过的不碰）。 */
    private static void awaitTask(myGameView.AsyncCountBoxsTask task) {
        if (task == null) return;
        try {
            task.get();
        } catch (Exception ignored) {
            // 任务内部出错也算「落定」
        }
        drainEdt();
    }

    /** 排空 EDT。 */
    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * 边排空 EDT 边等条件成立。
     *
     * <p>不能只 {@code drainEdt()} 一次就断言：{@code SwingWorker} 的 {@code done()} 是
     * worker 线程 {@code invokeLater} 上来的，而 {@code get()} 在 {@code FutureTask} 置位后
     * 就可能返回 —— 两者有先后竞争，{@code done()} 完全可能落在断言之后。
     */
    private static boolean awaitUntil(java.util.function.BooleanSupplier cond, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            drainEdt();
            if (cond.getAsBoolean()) return true;
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return cond.getAsBoolean();
    }
}
