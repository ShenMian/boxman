package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * BUG 回归 —— 「点了提示框的按钮，界面直接卡死」的**真正元凶**。
 *
 * <p>Phase31 修掉了「死锁移动」提示框的模态问题，但用户的症状依旧。真正的元凶是同一批里的
 * 另外四个框 —— 原版 {@code myGameView.java:1243-1325} 在 {@code onCreate} 里一次性建好
 * 5 个提示框（{@code AotoNextDlg} / {@code exitDlg} / {@code exitDlg2} / {@code exitDlg3} /
 * {@code lockDlg}），之后只用 {@code show()}；端口却把它们改成了内联的阻塞式
 * {@code JOptionPane.showConfirmDialog}。
 *
 * <p>其中 <b>{@code AotoNextDlg} 是在 {@code UpData1()} 里弹的，而 {@code UpData1()} 正是
 * {@code myTimer1}（1ms 一次性定时器）的回调</b>（原版 {@code myGameView.java:345 / 385}）。
 * 于是：
 *
 * <ol>
 *   <li>定时器触发 {@code UpData1()}，动画走完最后一步，{@code myClearance()} 为真；</li>
 *   <li>模态框在 EDT 上开一个<b>嵌套事件循环</b>，等用户点「是 / 否」；</li>
 *   <li>那个 1ms 定时器在嵌套循环里照旧触发 → {@code UpData1()} 重入 → 又走早退分支
 *       → 又 {@code showConfirmDialog} → 在<b>上一层模态框内部</b>再弹一层；</li>
 *   <li>栈无限加深，界面表现为「点了按钮就卡死」，而且由于模态框锁住宿主窗口，
 *       底下的「撤销 / 前进」按钮全都点不动 —— 也就是用户说的「点击撤销不会撤销」。</li>
 * </ol>
 *
 * <p>修法：照原版把 4 个框都改成 {@link HoloAlertDialog#createNonModal} 预建一次 +
 * {@code showDialog()}（等价于 Android 不阻塞、且已显示就 {@code if (mShowing) return;} 的
 * {@code Dialog.show()}）。
 */
public class Phase33LevelCompleteDialogTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 1 箱 1 目标，箱子紧挨仓管员右侧：往右一推就通关。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    /** 原版 {@code UpData1()} 里推箱子向右的编码（{@code 'R'} = 7 = 推右）。 */
    private static final byte PUSH_RIGHT = 7;

    private myGameView win;
    private mapNode savedCur;
    private ArrayList<mapNode> savedList;
    private String savedRoot, savedPath;
    private int savedTrun;

    private final List<JDialog> shown = new ArrayList<JDialog>();

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase33";
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

        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase33_test.XSB";
        myMaps.curMapNum = -4;
        // 「恭喜过关！」框只在常规关卡（Level_id > 0）时才弹
        myMaps.curMap.Level_id = 1;
        myMaps.m_nTrun = 0;
        myMaps.m_lstMaps = new ArrayList<mapNode>();
        myMaps.m_lstMaps.add(myMaps.curMap);
        myMaps.m_Sets[13] = 0;   // 不区分正逆推，myClearance() 只看目标数

        win = new myGameView();
        win.dialogShower = dlg -> {
            shown.add(dlg);
            dlg.setVisible(true);        // 非模态，立刻返回；这样 isVisible() 才为真
        };
        shown.clear();
    }

    @After
    public void tearDown() {
        for (JDialog d : shown) {
            d.dispose();
        }
        shown.clear();

        if (win != null) {
            // ⚠️ 必须先 myStop() 停掉 myTimer1..4 / mClockTimer 再 dispose()。
            // dispose() 不会停 Swing Timer，而前几条用例会直接调 UpData1(1)/DoEvent(1)
            // 把一次性定时器排上队；漏掉这一步就会让「上一支视图的定时器」在后续用例里
            // 继续回调，污染 m_lstMovReDo 之类的全局状态（踩过一次，表现为
            // 「凭空出现一次 reDo1 dir=7」）。
            win.myStop();
            for (HoloAlertDialog d : new HoloAlertDialog[]{win.AotoNextDlg, win.exitDlg,
                    win.exitDlg2, win.exitDlg3, win.lockDlg}) {
                if (d != null) d.dispose();
            }
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
    }

    // ---------------------------------------------------------------- 1. 卡死的根因

    /** 原版在 {@code onCreate} 里一次性建好的 5 个框，端口一个都不能少，且都不能是模态的。 */
    @Test
    public void allPreCreatedDialogsExistAndAreNonModal() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        assertNotNull("原版 dlg（myGameView.java:1243-1268）", win.AotoNextDlg);
        assertNotNull("原版 dlg0（myGameView.java:1270-1279）", win.exitDlg);
        assertNotNull("原版 dlg1（myGameView.java:1282-1296）", win.exitDlg2);
        assertNotNull("原版 dlg2（myGameView.java:1299-1313）", win.exitDlg3);
        assertNotNull("原版 dlg4（myGameView.java:1315-1325）", win.lockDlg);

        for (HoloAlertDialog dlg : new HoloAlertDialog[]{win.AotoNextDlg, win.exitDlg,
                win.exitDlg2, win.exitDlg3, win.lockDlg}) {
            assertFalse("「" + dlg.getTitle() + "」必须非模态："
                            + "AotoNextDlg 是在 1ms 定时器的回调 UpData1() 里弹的，模态框会开嵌套"
                            + "事件循环 → 定时器重入 → 层层嵌套直到卡死；其余几个也会在动画循环"
                            + "还在跑时被键盘快捷键触发",
                    dlg.isModal());
        }
    }

    // ---------------------------------------------------------------- 2. 与原版逐项对齐

    /** 原版 {@code dlg}：标题「恭喜过关！」，正文「是否自动打开下一个未解关卡？」，否 / 是。 */
    @Test
    public void autoNextDialogMatchesTheOriginalBuilder() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        List<JButton> buttons = buttonsOf(win.AotoNextDlg);

        assertTrue("标题应照原版 setTitle(\"恭喜过关！\")",
                textsOf(win.AotoNextDlg).contains("恭喜过关！"));
        assertTrue("正文应照原版 setMessage(\"是否自动打开下一个未解关卡？\")",
                textsOf(win.AotoNextDlg).contains("是否自动打开下一个未解关卡？"));
        assertEquals("原版只有一个 negative + 一个 positive", 2, buttons.size());
        assertEquals("button2 是 negative", "否", buttons.get(0).getText());
        assertEquals("button1 是 positive（在最右）", "是", buttons.get(1).getText());
        assertSame("原版把 positive 设为默认焦点按钮",
                buttons.get(1), win.AotoNextDlg.getRootPane().getDefaultButton());
    }

    /** 原版 {@code dlg0}：标题「退出」，正文「有状态未保存，坚持退出吗？」。 */
    @Test
    public void exitDialogMatchesTheOriginalBuilder() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        assertDialog(win.exitDlg, "退出", "有状态未保存，坚持退出吗？");
    }

    /** 原版 {@code dlg1} / {@code dlg2}：标题都是「更换关卡」，正文「有状态未保存，坚持更换吗？」。 */
    @Test
    public void replaceLevelDialogsMatchTheOriginalBuilder() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        assertDialog(win.exitDlg2, "更换关卡", "有状态未保存，坚持更换吗？");
        assertDialog(win.exitDlg3, "更换关卡", "有状态未保存，坚持更换吗？");
    }

    private static void assertDialog(HoloAlertDialog dlg, String title, String message) {
        List<JButton> buttons = buttonsOf(dlg);
        assertTrue("标题应照原版 setTitle(\"" + title + "\")", textsOf(dlg).contains(title));
        assertTrue("正文应照原版 setMessage(\"" + message + "\")", textsOf(dlg).contains(message));
        assertEquals("原版只有一个 negative + 一个 positive", 2, buttons.size());
        assertEquals("button2 是 negative", "否", buttons.get(0).getText());
        assertEquals("button1 是 positive（在最右）", "是", buttons.get(1).getText());
    }

    // ---------------------------------------------------------------- 3. 核心回归：不再层层嵌套

    /**
     * 通关后动画每多走一拍，{@code UpData1()} 都会再调一次 {@code show()}；
     * 原版靠 {@code Dialog.mShowing} 早退，所以屏幕上永远只有<b>一个</b>框。
     *
     * <p>端口原来是阻塞式 {@code JOptionPane}，这条会直接挂住 EDT（30s 超时）。
     */
    @Test
    public void completingALevelShowsTheDialogExactlyOnce() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        win.m_bMoved = true;
        win.mMap.d_Moves = win.mMap.m_PicWidth;   // 已停稳，下一拍就会真正执行动作
        win.m_lstMovReDo.offer(PUSH_RIGHT);
        win.m_nStep = 1;

        // 一直推进动画，直到「恭喜过关！」框弹出来
        int tick = 0;
        while (shown.isEmpty() && tick < 200) {
            win.UpData1(1);
            tick++;
        }

        assertEquals("通关后应当弹出「恭喜过关！」框", 1, shown.size());
        assertSame("弹的应当是预建的 AotoNextDlg", win.AotoNextDlg, shown.get(0));
        assertTrue("前置：框确实在显示", win.AotoNextDlg.isVisible());
        assertTrue("前置：关卡确实已通关", win.myClearance());

        // 原版语义：动画每多走一拍都再调一次 show()，但已显示时必须早退
        for (int k = 0; k < 30; k++) {
            win.UpData1(1);
        }
        assertEquals("已显示时再调 show() 必须直接早退 —— 否则会一层层叠出无数个框（就是「卡死」）",
                1, shown.size());
        assertSame("始终是同一个实例", win.AotoNextDlg, shown.get(0));
    }

    /** {@code showDialog()} 就是原版 {@code Dialog.show()}：已显示就早退，空引用直接忽略。 */
    @Test
    public void showDialogRespectsTheShowingEarlyReturn() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        win.showDialog(null);
        assertTrue("空引用应当被忽略（原版不会有这种情况，但端口是懒建的）", shown.isEmpty());

        win.showDialog(win.AotoNextDlg);
        assertEquals("首次应当弹出来", 1, shown.size());

        win.showDialog(win.AotoNextDlg);
        assertEquals("已显示时再调必须早退", 1, shown.size());
    }

    /** 每个框都必须在 {@code setupButtonEvents()} 里只构造一次 —— 不能在动画循环里 new。 */
    @Test
    public void everyDialogIsConstructedExactlyOnce() throws Exception {
        String src = codeOf("myGameView.java");
        assertEquals(1, countOf(src, "AotoNextDlg = HoloAlertDialog.createNonModal("));
        assertEquals(1, countOf(src, "exitDlg = HoloAlertDialog.createNonModal("));
        assertEquals(1, countOf(src, "exitDlg2 = HoloAlertDialog.createNonModal("));
        assertEquals(1, countOf(src, "exitDlg3 = HoloAlertDialog.createNonModal("));
        assertEquals(1, countOf(src, "lockDlg = HoloAlertDialog.createNonModal("));
    }

    // ---------------------------------------------------------------- 4. 源码扫描：动画回调里不许有阻塞框

    /**
     * 四个动画回调（{@code myTimer1..4} 的 ActionListener 目标）里不许再出现 {@code JOptionPane}。
     * 这是「卡死」这一类 bug 的唯一充分条件，用源码扫描永久锁住。
     */
    @Test
    public void animationCallbacksContainNoBlockingDialog() throws Exception {
        String src = codeOf("myGameView.java");
        for (String method : new String[]{"UpData1", "UpData2", "UpData3", "UpData4"}) {
            String body = methodBody(src, method);
            assertFalse(method + "() 是定时器回调，里面不能有阻塞式 JOptionPane"
                            + "（模态框会在 EDT 上开嵌套事件循环，定时器重入 → 层层嵌套 → 卡死）。"
                            + "实际内容：" + body,
                    body.contains("JOptionPane"));
        }
    }

    /** 那四个框原来的阻塞式确认框都不许再回来。 */
    @Test
    public void theFourBlockingConfirmDialogsAreGone() throws Exception {
        String src = codeOf("myGameView.java");
        assertFalse("「恭喜过关！」不能再走 JOptionPane",
                src.contains("JOptionPane.showConfirmDialog(this, \"恭喜过关！"));
        assertFalse("「退出」不能再走 JOptionPane",
                src.contains("JOptionPane.showConfirmDialog(this, \"有状态未保存，坚持退出吗？"));
        assertFalse("「更换关卡」不能再走 JOptionPane",
                src.contains("JOptionPane.showConfirmDialog(this, \"有状态未保存，坚持更换吗？"));
    }

    /** 四个调用点都要改成 {@code showDialog(...)}。 */
    @Test
    public void theFourCallSitesGoThroughShowDialog() throws Exception {
        String src = codeOf("myGameView.java");
        assertEquals("UpData1() 的两个通关点", 2, countOf(src, "showDialog(AotoNextDlg);"));
        assertEquals(1, countOf(src, "showDialog(exitDlg);"));
        assertEquals(1, countOf(src, "showDialog(exitDlg2);"));
        assertEquals(1, countOf(src, "showDialog(exitDlg3);"));
    }

    // ---------------------------------------------------------------- 4b. 端到端：真实定时器

    /** 2 箱 2 目标；往左推两次就把箱子推进 (1,1) 死角 —— 该箱子可达范围内一个目标点都没有。 */
    private static final String DEADLOCK_LEVEL =
            "#######\n" +
            "#  $@ #\n" +
            "#     #\n" +
            "#   $ #\n" +
            "#.   .#\n" +
            "#######";

    /**
     * 端到端复现用户报的那条路径：<b>走一步 → 弹死锁提示 → 点「撤销移动」</b>。
     *
     * <p>与前几条不同，这条**不手动推进动画**：动作队列摆好之后完全交给真实的
     * {@code myTimer1}（1 ms 一次性定时器）去跑，{@code dialogShower} 也用默认实现
     * （真的把非模态框显示出来）。因此它同时验证三件事：
     *
     * <ol>
     *   <li>动画循环会自己把 {@code m_bBusing}（忙中）复位 —— 原版靠下一拍走早退分支，
     *       否则 {@code bt_UnDo} 的监听器会在 {@code if (m_bBusing) return;} 处永远早退，
     *       表现就是「点击撤销不会撤销」；</li>
     *   <li>点「撤销移动」之后撤销动画真的把箱子退回去；</li>
     *   <li>整个过程结束后 <b>EDT 仍然响应</b> —— 也就是没有卡死。</li>
     * </ol>
     */
    @Test
    public void realTimerFlowDeadlockThenUndoRestoresTheBoard() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        // 换关卡要重建视图（@Before 里那个是按「一推通关」的关卡建的）
        win.setVisible(false);
        win.dispose();

        myMaps.curMap = new mapNode(DEADLOCK_LEVEL, "死锁测试关", "测试", "");
        myMaps.curMap.fileName = "phase33_deadlock.XSB";
        myMaps.curMapNum = -4;
        myMaps.m_Sets[11] = 1;    // 死锁嗅探开
        myMaps.m_Sets[10] = 4;    // 最快速度
        myMaps.m_Sets[6] = 0;     // 关掉「瞬移」

        final myGameView g = new myGameView();
        win = g;                                   // 交给 @After 收尾
        g.dialogShower = dlg -> dlg.setVisible(true);   // 真实：非模态，立刻返回

        final String before = new String(g.m_cArray[1]);

        // 等价于在棋盘上往左划一下：排队两次「推箱向左」（'L' = 5）
        SwingUtilities.invokeAndWait(() -> {
            g.bt_IM.setChecked(false);
            g.mMap.d_Moves = g.mMap.m_PicWidth;    // 已停稳
            g.m_lstMovReDo.offer((byte) 5);
            g.m_lstMovReDo.offer((byte) 5);
            g.m_nStep = 2;
            g.DoEvent(1);
        });

        assertTrue("动画循环应当自己跑完并弹出「死锁移动」提示（真实 1ms 定时器）",
                awaitUntil(() -> g.lockDlg.isVisible(), 10000));

        // 提示框弹出之后动画循环还剩最后一拍：那一拍会走 UpData1() 的早退分支并复位 m_bBusing。
        // ⚠️ 这一拍**必须**真的到达 —— 否则 bt_UnDo 监听器会在 `if (m_bBusing) return;` 处
        // 永远早退，表现就是用户报的「点击撤销不会撤销」。见
        // rearmingFromInsideTheCallbackAlwaysDeliversTheNextTick()。
        assertTrue("动画循环停下来后必须把 m_bBusing 复位 —— 否则 bt_UnDo 会永远早退，"
                        + "表现就是「点击撤销不会撤销」（busy=" + g.m_bBusing
                        + " nStep=" + g.m_nStep + "）",
                awaitUntil(() -> !g.m_bBusing, 10000));
        assertTrue("EDT 必须保持响应（没卡死）", edtRespondsWithin(3000));

        // 点「撤销移动」—— 与用户操作完全同一条路径
        SwingUtilities.invokeAndWait(() -> buttonsOf(g.lockDlg).get(1).doClick());

        assertFalse("点「撤销移动」后提示框必须消失（原版按钮无条件 dismiss）",
                g.lockDlg.isVisible());
        assertTrue("点「撤销移动」后应当真的开始撤销（m_lstMovUnDo 缩短）",
                awaitUntil(() -> g.m_lstMovUnDo.size() < 2, 10000));
        assertTrue("撤销动画应当把箱子退回原位（row1 恢复成 " + before + "），实际："
                        + new String(g.m_cArray[1]),
                awaitUntil(() -> before.equals(new String(g.m_cArray[1])), 10000));
        assertTrue("撤销之后 EDT 仍要响应", edtRespondsWithin(3000));
        assertTrue("撤销动画跑完后忙标志应当复位（否则下一次撤销又会被早退掉）",
                awaitUntil(() -> !g.m_bBusing, 10000));
    }

    /**
     * 用户报的<b>完整路径</b>：打通一关 → 点「恭喜过关！」的「是」跳到下一个未解关卡 →
     * 在新关卡走一步死锁 → 弹「死锁移动」→ 点「撤销移动」必须真的撤销。
     *
     * <p>这条覆盖了前面几条各自只盖了一半的东西：{@code AotoNextDlg} 的「是」会走
     * {@code initMap()} 换关卡（{@code levelReset()} 里会把 {@code m_bBusing} 复位，
     * 所以换关本身是干净的），真正的坑在新关卡自己那一轮动画循环上。
     */
    @Test
    public void completingALevelThenJumpingToTheNextOneKeepsUndoWorking() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        mapNode lv1 = new mapNode(LEVEL, "第1关", "测试", "");
        lv1.fileName = "phase33_lv1.XSB";
        lv1.Level_id = 1;
        mapNode lv2 = new mapNode(DEADLOCK_LEVEL, "第2关", "测试", "");
        lv2.fileName = "phase33_lv2.XSB";
        lv2.Level_id = 2;
        myMaps.m_lstMaps = new ArrayList<mapNode>();
        myMaps.m_lstMaps.add(lv1);
        myMaps.m_lstMaps.add(lv2);
        myMaps.curMap = lv1;
        myMaps.m_nTrun = 0;
        myMaps.m_Sets[11] = 1;    // 死锁嗅探开
        myMaps.m_Sets[10] = 4;    // 最快速度
        myMaps.m_Sets[6] = 0;     // 关掉「瞬移」

        win.setVisible(false);
        win.dispose();

        final myGameView g = new myGameView();
        win = g;
        g.dialogShower = dlg -> dlg.setVisible(true);

        // ① 交给真实定时器把第 1 关推通（往右一推即通关）
        SwingUtilities.invokeAndWait(() -> {
            g.bt_IM.setChecked(false);
            g.mMap.d_Moves = g.mMap.m_PicWidth;
            g.m_bMoved = true;
            g.m_lstMovReDo.offer(PUSH_RIGHT);
            g.m_nStep = 1;
            g.DoEvent(1);
        });

        assertTrue("通关后应弹出「恭喜过关！」",
                awaitUntil(() -> g.AotoNextDlg.isVisible(), 10000));
        assertTrue("通关后动画必须停稳（m_bBusing 复位）",
                awaitUntil(() -> !g.m_bBusing, 10000));

        // ② 点「是」→ 跳到下一个未解关卡
        SwingUtilities.invokeAndWait(() -> buttonsOf(g.AotoNextDlg).get(1).doClick());

        assertSame("应当切到第 2 关", lv2, myMaps.curMap);
        assertFalse("⚠️ 点「是」之后这个框必须消失 —— 否则它会一直挂在那儿，"
                        + "每点一次就往后跳一个关卡（用户报的就是这个）",
                g.AotoNextDlg.isVisible());
        assertFalse("刚换完关卡不该弹「死锁移动」", g.lockDlg.isVisible());
        assertFalse("换关之后忙标志必须已复位 —— 否则新关卡上一按撤销就早退", g.m_bBusing);

        // ③ 在新关卡走两步死锁，弹提示 → 必须停稳 → 点「撤销移动」要真的撤销
        final String before = new String(g.m_cArray[1]);
        SwingUtilities.invokeAndWait(() -> {
            g.bt_IM.setChecked(false);
            g.mMap.d_Moves = g.mMap.m_PicWidth;
            g.m_lstMovReDo.offer((byte) 5);
            g.m_lstMovReDo.offer((byte) 5);
            g.m_nStep = 2;
            g.DoEvent(1);
        });

        assertTrue("第 2 关走到死锁应当弹提示", awaitUntil(() -> g.lockDlg.isVisible(), 10000));
        assertTrue("提示弹出后动画必须停稳 —— 否则就是用户报的「点撤销没反应」",
                awaitUntil(() -> !g.m_bBusing, 10000));

        SwingUtilities.invokeAndWait(() -> buttonsOf(g.lockDlg).get(1).doClick());

        assertTrue("点「撤销移动」必须真的把箱子退回原位，实际："
                        + new String(g.m_cArray[1]),
                awaitUntil(() -> before.equals(new String(g.m_cArray[1])), 10000));
        assertTrue("全程结束后 EDT 仍要响应", edtRespondsWithin(3000));
    }

    // ---------------------------------------------------------------- 4c. coalesce 竞态

    /**
     * 回归锁 —— <b>在定时器自己的回调里重排下一拍时，那一拍必须真的到达</b>。
     *
     * <p>这条是本 BUG 的**根因**用例，与上面那条端到端用例互补：那条依赖真实窗口（headless
     * 下会被 {@code Assume} 跳过），这条用 {@code dialogShower} 里一次 {@code Thread.sleep}
     * 精确模拟「弹框把 EDT 拖住几十毫秒」，因此**任何环境都跑**。
     *
     * <p>原版 {@code RefreshHandler1.sleep(ms)} 是
     * {@code removeMessages(0); sendMessageDelayed(obtainMessage(1), m);} —— Looper 对每个
     * message 都投递一次，不存在合并，所以每一拍必然到达。而 {@code javax.swing.Timer} 默认
     * {@code coalesce == true}：{@code post()} 写成
     * {@code if (notify.compareAndSet(false, true) || !coalesce) invokeLater(doPostEvent);}，
     * 而 {@code notify} 只在回调<b>返回之后</b>才被 {@code cancelEvent()} 清掉。于是：
     *
     * <ol>
     *   <li>回调执行中 → {@code notify == true}；</li>
     *   <li>{@code sleepTimer()} 里 {@code isRunning()} 为 false（TimerQueue 线程在
     *       {@code post()} 之前就已把 {@code delayedTimer} 置空）→ 不走 {@code stop()}；</li>
     *   <li>TimerQueue 线程 1 ms 后 {@code post()} → {@code compareAndSet(false,true)} 失败，
     *       且 coalesce 为 true → <b>不投递</b>，这一拍被丢掉；</li>
     *   <li>回调返回 → {@code cancelEvent()} → {@code notify = false}。</li>
     * </ol>
     *
     * <p>结果：定时器既不在队列里、{@code notify} 也是 false，<b>再也不会响</b>。第 ③ 步只要
     * 落在第 ④ 步之前（EDT 在回调里停留超过 1 ms）就必然丢拍 —— 弹「死锁移动」提示框正好
     * 制造这个条件。修法是 {@code initTimers()} 里对 4 个一次性定时器
     * {@code setCoalesce(false)}，让 {@code post()} 走 {@code || !coalesce} 分支。
     */
    @Test
    public void rearmingFromInsideTheCallbackAlwaysDeliversTheNextTick() throws Exception {
        win.setVisible(false);
        win.dispose();

        myMaps.curMap = new mapNode(DEADLOCK_LEVEL, "死锁测试关", "测试", "");
        myMaps.curMap.fileName = "phase33_coalesce.XSB";
        myMaps.curMapNum = -4;
        myMaps.m_Sets[11] = 1;
        myMaps.m_Sets[10] = 4;
        myMaps.m_Sets[6] = 0;

        final myGameView g = new myGameView();
        win = g;

        // 「弹框」改成把 EDT 拖住 20ms —— 精确复刻真实弹框期间的 EDT 停留，且不依赖图形环境。
        g.dialogShower = dlg -> {
            shown.add(dlg);
            try {
                Thread.sleep(20);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        };

        SwingUtilities.invokeAndWait(() -> {
            g.bt_IM.setChecked(false);
            g.mMap.d_Moves = g.mMap.m_PicWidth;
            g.m_lstMovReDo.offer((byte) 5);
            g.m_lstMovReDo.offer((byte) 5);
            g.m_nStep = 2;
            g.DoEvent(1);
        });

        assertTrue("提示框应当弹出（说明动画确实跑到了死锁判定那一拍）",
                awaitUntil(() -> !shown.isEmpty(), 10000));
        assertTrue("弹框把 EDT 拖住之后，下一拍仍必须到达并把 m_bBusing 复位 —— "
                        + "否则 bt_UnDo 永远早退（busy=" + g.m_bBusing
                        + " nStep=" + g.m_nStep + "）",
                awaitUntil(() -> !g.m_bBusing, 10000));
        assertTrue("EDT 仍要响应", edtRespondsWithin(3000));
    }

    // ---------------------------------------------------------------- 4d. 按钮必须关框

    /**
     * 回归锁 —— 原版 {@code AlertDialog} 的按钮**无条件**关闭对话框。
     *
     * <p>原版 {@code AlertController.mButtonHandler}：先 {@code m.sendToTarget()} 派发监听器，
     * 随后**无条件**再发一条 {@code MSG_DISMISS_DIALOG}。所以「传了 action」只是
     * 「关闭前额外做的事」，**不影响关闭本身**。
     *
     * <p>端口原先写成 {@code if (action != null) { action.run(); } else { dispose(); }} ——
     * 于是凡传了 action 的按钮**全都关不掉**。用户看到的就是：
     * 点「恭喜过关！」的「是」，框不消失，每点一次就往后跳一个关卡。
     */
    @Test
    public void clickingAButtonWithAnActionAlsoClosesTheDialog() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        HoloAlertDialog dlg = HoloAlertDialog.createNonModal(win, "按钮语义");
        try {
            dlg.setMessage("点「是」应当既执行动作、又关闭本框");
            final boolean[] ran = {false};
            dlg.addButton("否", null);
            dlg.addButton("是", () -> ran[0] = true);
            dlg.setVisible(true);
            assertTrue("前置：框已在显示", dlg.isVisible());

            buttonsOf(dlg).get(1).doClick();

            assertTrue("action 必须被执行", ran[0]);
            assertFalse("传了 action 的按钮也必须关闭对话框（原版无条件 dismiss）",
                    dlg.isVisible());

            // 反向：null 分支也不能变成死按钮
            HoloAlertDialog dlg2 = HoloAlertDialog.createNonModal(win, "按钮语义");
            try {
                dlg2.setMessage("点「取消」应当直接关闭");
                dlg2.addButton("取消", null);
                dlg2.setVisible(true);
                buttonsOf(dlg2).get(0).doClick();
                assertFalse("addButton(text, null) 是「点了就关」，不是死按钮", dlg2.isVisible());
            } finally {
                dlg2.dispose();
            }
        } finally {
            dlg.dispose();
        }
    }

    /** 4 个一次性动画定时器都必须关掉 coalesce —— 这是上面那条用例的静态对偶。 */
    @Test
    public void theFourAnimationTimersDisableCoalescing() throws Exception {        String src = codeOf("myGameView.java");
        // 传 "void initTimers" 而不是 "initTimers"：methodBody 是 indexOf(" " + name + tail)，
        // 只给 "initTimers" 会先命中 initUI() 里的调用点 `initTimers();`。
        String body = methodBody(src, "void initTimers", "()");
        for (int n = 1; n <= 4; n++) {
            assertTrue("myTimer" + n + " 必须 setCoalesce(false)："
                            + "javax.swing.Timer 默认合并投递，会让「回调里重排下一拍」静默丢拍",
                    body.contains("myTimer" + n + ".setCoalesce(false);"));
        }
        assertEquals("不应误伤 mClockTimer（它是重复定时器，不在此竞态路径上）",
                4, countOf(body, "setCoalesce(false)"));
    }

    /** 在 {@code budgetMs} 内把一件事交回 EDT 执行 —— 超时即说明 EDT 被卡住了。 */
    private static boolean edtRespondsWithin(long budgetMs) throws InterruptedException {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(latch::countDown);
        return latch.await(budgetMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** 轮询等待条件成立（从测试线程轮询，让真实定时器在 EDT 上照常跑）。 */
    private static boolean awaitUntil(java.util.function.BooleanSupplier cond, long budgetMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + budgetMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return cond.getAsBoolean();
    }

    // ---------------------------------------------------------------- 5. 无解提醒（原版 onProgressUpdate）
    /**
     * 原版 {@code onProgressUpdate()}（{@code myGameView.java:5598-5600}）用的是
     * {@code Builder(...).setTitle("提醒").setMessage("这是一个无解的关卡！")
     * .setPositiveButton("确定", null).create().show()}。
     *
     * <p>端口原来是裸的 {@code new JOptionPane(…).createDialog(…)} —— 既模态（{@code process()}
     * 是 EDT 回调，模态框会开嵌套事件循环，后续 {@code process()} 会在里面重入再弹一层），
     * 按钮还是英文 "OK"。这条把它锁回原版形态。
     */
    @Test
    public void noSolutionWarningMatchesTheOriginalBuilder() throws Exception {
        String src = codeOf("myGameView.java");

        assertFalse("「这是一个无解的关卡！」不能再是裸 JOptionPane（模态 + 英文 OK 按钮）",
                src.contains("new JOptionPane("));
        assertFalse("不能再用 JOptionPane.createDialog", src.contains("createDialog("));
        assertTrue("应当照原版用非模态的 HoloAlertDialog",
                src.contains("HoloAlertDialog.createNonModal(view, \"提醒\")"));
        assertTrue("正文应照原版", src.contains("\"这是一个无解的关卡！\""));
        assertTrue("按钮应照原版 setPositiveButton(\"确定\", null)",
                src.contains("dlg.addButton(\"确定\", null)"));
    }

    /** 原版这条提醒只弹一次（提醒完立刻复位 {@code m_bNoSolution}）。 */
    @Test
    public void noSolutionWarningFiresOnlyOncePerTask() throws Exception {
        String src = codeOf("myGameView.java");
        String body = methodBody(src, "process", "(java.util.List<Void> chunks)");
        assertTrue("正文里应先复位标志再弹框", body.indexOf("m_bNoSolution = false") < body.indexOf("dialogShower"));
        assertEquals("一个 process() 里只应弹一次", 1, countOf(body, "dialogShower.accept"));
    }

    // ---------------------------------------------------------------- 工具

    /** 取出 {@code UpDataN(int i)} 的函数体。 */
    private static String methodBody(String src, String name) {
        return methodBody(src, name, "(int i)");
    }

    /** 取出某个方法的函数体（从签名后的第一个 `{` 配平到对应的 `}`）。 */
    private static String methodBody(String src, String name, String signatureTail) {
        int at = src.indexOf(" " + name + signatureTail);
        assertTrue("找不到方法 " + name + signatureTail, at >= 0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, i + 1);
            }
        }
        throw new AssertionError("方法 " + name + " 的大括号不配平");
    }

    private static List<String> textsOf(Container root) {
        List<String> raw = new ArrayList<String>();
        collectTexts(root, raw);
        List<String> out = new ArrayList<String>();
        for (String t : raw) {
            out.add(plain(t));
        }
        return out;
    }

    private static String plain(String text) {
        return text.replace("<html>", "")
                .replaceAll("<body[^>]*>", "")
                .replace("</body></html>", "")
                .replace("<br>", "\n")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static void collectTexts(Container c, List<String> out) {
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel) {
                String t = ((JLabel) child).getText();
                if (t != null && !t.isEmpty()) {
                    out.add(t);
                }
            }
            if (child instanceof Container) {
                collectTexts((Container) child, out);
            }
        }
    }

    private static List<JButton> buttonsOf(Container root) {
        List<JButton> out = new ArrayList<JButton>();
        collectButtons(root, out);
        return out;
    }

    private static void collectButtons(Container c, List<JButton> out) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton) {
                out.add((JButton) child);
            }
            if (child instanceof Container) {
                collectButtons((Container) child, out);
            }
        }
    }

    private static int countOf(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static String codeOf(String name) throws IOException {
        File f = new File("src/main/java/my/boxman/" + name);
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        // 先剥注释：注释里提到 JOptionPane 会误报
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
