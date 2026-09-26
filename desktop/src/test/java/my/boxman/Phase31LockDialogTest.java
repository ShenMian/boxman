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
 * BUG 回归 —— 「死锁警告弹窗，选『否』直接就卡死」。
 *
 * <p><b>根因：</b>「死锁移动」提示框原先是用阻塞式
 * {@code JOptionPane.showConfirmDialog(this, …)} 弹的，而它弹出来的位置在
 * {@code UpData1()} / {@code UpData3()} 的<b>末尾</b> —— 那两个方法是由
 * {@code myTimer1 / myTimer3}（1ms 一次性定时器）驱动的动画循环：
 *
 * <ol>
 *   <li>定时器触发 {@code UpData1()}，动画推进到一格结束，判定
 *       {@code myLock(m_iR9, m_iC9)} 为真；</li>
 *   <li>模态框在 EDT 上开一个<b>嵌套事件循环</b>（{@code JOptionPane.showConfirmDialog}
 *       会一直阻塞到用户点按钮）；</li>
 *   <li>嵌套循环里那个 1ms 定时器照旧触发 → {@code UpData1()} 重入 → 动画跑完一步
 *       → 又满足死锁条件 → 在<b>上一层模态框内部</b>再弹一层；</li>
 *   <li>栈无限加深，界面表现为「点了按钮就卡死」。</li>
 * </ol>
 *
 * <p><b>修法（对齐原版）：</b>原版 {@code myGameView.java:1315-1325} 把 {@code lockDlg}
 * 一次性建好（{@code setTitle("死锁移动")} + {@code setNegativeButton("继续", null)} +
 * {@code setPositiveButton("撤销移动", → bt_UnDo 取反)}），之后只用
 * {@code setMessage()} + {@code show()} —— 而 Android 的 {@code AlertDialog.show()}
 * <b>不阻塞</b>，并且 {@code Dialog.show()} 在已显示时会 {@code if (mShowing) return;} 早退。
 * 端口现在改成 {@link HoloAlertDialog#createNonModal}，两个语义都照搬。
 *
 * <p>本用例同时锁住顺带补上的一项保真：原版 {@code myLock()} / {@code myLock2()}
 * （{@code myGameView.java:2390-2419}）会把**具体原因**写进正文
 * （{@code （点位不足）} / {@code （僵位冻结）} / {@code （闭锁对角）} / {@code （网位互锁）}）。
 */
public class Phase31LockDialogTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 一个合法的小关卡：3 行 × 5 列，1 个仓管员、1 箱 1 目标。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    /** 原版 {@code dlg4} 的正文骨架，四条原因都拼在它后面。 */
    private static final String ASK = "这一步造成关卡死锁，继续吗？";

    private myGameView win;
    private mapNode savedCur;
    private String savedRoot, savedPath;
    private int savedTrun;

    /** 收集被「弹」出来的对话框（走 {@code dialogShower} 缝，只记不弹，否则模态框会挂死用例）。 */
    private final List<JDialog> shown = new ArrayList<JDialog>();

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase31";
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
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        savedTrun = myMaps.m_nTrun;

        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase31_test.XSB";
        myMaps.curMapNum = -4;
        myMaps.m_nTrun = 0;

        win = new myGameView();
        win.dialogShower = dlg -> shown.add(dlg);
        shown.clear();
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

    // ---------------------------------------------------------------- 1. 卡死的根因：模态

    /** 这就是「选『否』直接卡死」的根因 —— 提示框绝不能是模态的。 */
    @Test
    public void lockDialogMustNotBeModal() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        assertNotNull("initUI() 里就该把 lockDlg 建好（原版在挂 bt_UnDo 监听之前建）", win.lockDlg);
        assertFalse("「死锁移动」提示框必须非模态：它在 1ms 动画定时器里弹，"
                + "模态框会在 EDT 上开嵌套事件循环 → 定时器重入 → 层层嵌套直到卡死", win.lockDlg.isModal());
    }

    /** 兼容层的新工厂本身也要锁住：{@code create} 仍是模态，{@code createNonModal} 不是。 */
    @Test
    public void nonModalFactoryProducesNonModalDialog() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        HoloAlertDialog modal = HoloAlertDialog.create(null, "死锁移动");
        HoloAlertDialog nonModal = HoloAlertDialog.createNonModal(null, "死锁移动");
        try {
            assertTrue("create() 保持原有语义（模态）", modal.isModal());
            assertFalse("createNonModal() 才是原版 AlertDialog 的真实语义", nonModal.isModal());
            assertTrue("标题应当照原版 setTitle(\"死锁移动\") 画在面板里",
                    textsOf(nonModal).contains("死锁移动"));
        } finally {
            modal.dispose();
            nonModal.dispose();
        }
    }

    // ---------------------------------------------------------------- 2. 与原版逐项对齐

    /** 原版 {@code dlg4}：标题「死锁移动」，按钮「继续」在左、「撤销移动」在最右。 */
    @Test
    public void lockDialogMatchesTheOriginalBuilder() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        List<JButton> buttons = buttonsOf(win.lockDlg);

        assertTrue("标题应照原版 setTitle(\"死锁移动\")", textsOf(win.lockDlg).contains("死锁移动"));
        assertEquals("原版只有一个 negative + 一个 positive", 2, buttons.size());
        assertEquals("button2 是 negative", "继续", buttons.get(0).getText());
        assertEquals("button1 是 positive（在最右）", "撤销移动", buttons.get(1).getText());
        assertSame("原版把 positive 设为默认焦点按钮",
                buttons.get(1), win.lockDlg.getRootPane().getDefaultButton());
    }

    /** 原版 {@code setPositiveButton("撤销移动", → bt_UnDo.setChecked(!isChecked()))}。 */
    @Test
    public void undoMoveButtonTogglesTheUndoButton() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        JButton undoMove = buttonsOf(win.lockDlg).get(1);

        boolean before = win.bt_UnDo.isChecked();
        undoMove.doClick();

        assertEquals("「撤销移动」必须把 bt_UnDo 取反（原版唯一的动作）",
                !before, win.bt_UnDo.isChecked());
    }

    /** 原版 AlertDialog 的按钮无条件先 dismiss —— 非模态框不关掉就会一直挡在屏幕上。 */
    @Test
    public void bothButtonsDismissTheDialog() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        for (int i = 0; i < 2; i++) {
            win.lockDlg.setVisible(true);
            assertTrue("前置：提示框应当显示出来了", win.lockDlg.isVisible());
            JButton b = buttonsOf(win.lockDlg).get(i);
            b.doClick();
            assertFalse("点「" + b.getText() + "」后应当关掉（原版按钮无条件先 dismiss）",
                    win.lockDlg.isVisible());
        }
    }

    // ---------------------------------------------------------------- 3. 正文里的具体原因

    /** 原版 {@code myLock()} / {@code myLock2()} 会把死锁原因写进正文。 */
    @Test
    public void deadlockReasonsAreWrittenIntoTheBody() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        for (String reason : new String[]{"（点位不足）", "（僵位冻结）", "（闭锁对角）", "（网位互锁）"}) {
            String message = ASK + "\n" + reason;
            HoloAlertDialog probe = HoloAlertDialog.create(null, "死锁移动");
            try {
                probe.setMessage(message);
                assertTrue("正文应写入 " + reason + "，实际：" + textsOf(probe),
                        textsOf(probe).contains(message));
            } finally {
                probe.dispose();
            }
        }
    }

    /** {@code setMessage()} 必须能更新**同一个**已建好的框（原版是 {@code lockDlg.setMessage(...)}）。 */
    @Test
    public void setMessageUpdatesTheSameDialog() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        HoloAlertDialog dlg = HoloAlertDialog.create(null, "死锁移动");
        try {
            dlg.setMessage(ASK + "\n（点位不足）");
            assertTrue("第一次应写入", textsOf(dlg).contains(ASK + "\n（点位不足）"));
            dlg.setMessage(ASK + "\n（网位互锁）");
            assertTrue("第二次应覆盖", textsOf(dlg).contains(ASK + "\n（网位互锁）"));
            assertFalse("旧正文不应残留", textsOf(dlg).contains(ASK + "\n（点位不足）"));
        } finally {
            dlg.dispose();
        }
    }

    // ---------------------------------------------------------------- 4. 只建一次、已显示就不重复弹

    /** 原版只在初始化时 {@code create()} 一次，动画每走一步都调的是同一个框的 {@code show()}。 */
    @Test
    public void theDialogIsBuiltOnceAndReused() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        HoloAlertDialog first = win.lockDlg;

        win.showLockDlg();
        win.showLockDlg();

        assertSame("不得每次弹都新建一个框", first, win.lockDlg);
        assertEquals("缝里收到的次数", 2, shown.size());
        assertSame("两次弹的应当是同一个实例", shown.get(0), shown.get(1));
    }

    /** 原版 {@code Dialog.show()} 在已显示时 {@code if (mShowing) return;} —— 动画每走一步都会调它。 */
    @Test
    public void alreadyShowingDialogIsNotShownAgain() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        win.lockDlg.setVisible(true);   // 非模态，立刻返回；模拟「框已在屏幕上」
        try {
            assertTrue("前置：提示框确实在显示", win.lockDlg.isVisible());
            win.showLockDlg();
            assertTrue("已显示时再调 show() 必须直接早退（否则动画每走一步都会重复弹）",
                    shown.isEmpty());
        } finally {
            win.lockDlg.dispose();
        }
    }

    /** 提示框走的是 {@code dialogShower} 测试缝（不是内联的 JOptionPane），否则用例会挂死。 */
    @Test
    public void showGoesThroughTheTestSeam() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        win.showLockDlg();

        assertEquals(1, shown.size());
        assertSame("缝里收到的应当是 lockDlg 本体", win.lockDlg, shown.get(0));
        assertFalse("绝不能是模态的 JOptionPane 对话框", shown.get(0).isModal());
    }

    // ---------------------------------------------------------------- 5. 源码扫描

    /** 两个死锁判定点都不许再出现阻塞式确认框。 */
    @Test
    public void deadlockSitesNoLongerUseABlockingConfirmDialog() throws Exception {
        String src = codeOf("myGameView.java");

        assertFalse("死锁提示不能再走 JOptionPane（模态框 + 定时器重入 = 卡死）",
                src.contains("JOptionPane.showConfirmDialog(this, \"这一步造成关卡死锁"));
        assertEquals("正推、逆推两个判定点都应当调 showLockDlg()", 2, countOf(src, "showLockDlg();"));
    }

    /** 四条原因必须真的落在 {@code myLock} / {@code myLock2} 里（原版就是这么写的）。 */
    @Test
    public void allFourReasonsArePresentInTheLockHelpers() throws Exception {
        String src = codeOf("myGameView.java");
        for (String reason : new String[]{"（点位不足）", "（僵位冻结）", "（闭锁对角）", "（网位互锁）"}) {
            assertTrue("myLock/myLock2 里应写入原因 " + reason, src.contains(reason));
        }
    }

    /** 提示框必须是「建一次、之后只 setMessage + show」—— 不能在动画循环里 new。 */
    @Test
    public void lockDialogIsConstructedOnlyOnce() throws Exception {
        String src = codeOf("myGameView.java");
        assertEquals("lockDlg 只应在 setupButtonEvents() 里构造一次",
                1, countOf(src, "lockDlg = HoloAlertDialog.createNonModal("));
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 提示框正文用同一套 Holo 组件渲染，这里直接扫组件树里的文字。
     *
     * <p>正文是 HTML（{@code <html><body style='width:284px'>…<br>…</body></html>}，
     * 见 {@code HoloMessageDialog.messageBody}），这里还原成纯文本再比。
     */
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

    /** 按钮栏里的按钮（跳过按钮之间的 1px 分隔线）。 */
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
        // 先剥注释：注释里提到 JOptionPane / showLockDlg 会误报
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
