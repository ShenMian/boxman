package my.boxman;

import my.boxman.compat.HoloClick;
import my.boxman.compat.HoloPopupMenu;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 自绘可点组件的**点击契约**：动作必须挂 {@code mouseReleased}，不能挂 {@code mouseClicked}。
 *
 * <h2>现象</h2>
 * 关卡网格顶栏的「╋ / 顶 / 底」有时候点一次没反应、要点几次才有反应；同一个鼠标在别的
 * 程序里完全正常。
 *
 * <h2>根因</h2>
 * {@code MOUSE_CLICKED} <b>不是</b>原始事件，是 Windows 原生层在
 * {@code AwtComponent::WmMouseUp} 里<b>派生</b>的，受一个<b>无阈值</b>的开关控制：
 * {@code WmMouseMove} 只要在按住期间收到任何一次 {@code WM_MOUSEMOVE}（哪怕 1 像素）就
 * {@code m_mouseButtonClickAllowed = 0}，抬起时便不再补发 {@code MOUSE_CLICKED}。
 * 于是「手抖 1 像素 = 这一次点击被吞掉」。Java 侧不生成 click
 * （{@code WComponentPeer.handleJavaMouseEvent} 只处理 {@code MOUSE_PRESSED}），
 * Swing 自己也不依赖它（{@code BasicButtonListener.mouseClicked()} 是空方法，
 * {@code JButton} 的动作在 {@code mouseReleased()} 里触发）。
 *
 * <h2>本用例怎么测</h2>
 * 关键在于：<b>刻意不派发 {@code MOUSE_CLICKED}</b>，只派发
 * {@code MOUSE_PRESSED → [MOUSE_DRAGGED] → MOUSE_RELEASED} —— 这正是真实系统在手抖时
 * 交给组件的事件序列。旧的 {@code mouseClicked} 写法在这个序列下<b>什么都不会发生</b>，
 * 所以下面每一条都是会红的用例；把 {@code HoloClick.install(...)} 改回
 * {@code MouseAdapter.mouseClicked} 就能复现（见 {@code HoloClick} 的类注释）。
 *
 * <p>⚠️ {@code contains()} 依赖真实几何：组件没被布局过时尺寸是 0×0，判定一律为假。
 * 所以派发前必须先 {@code setSize(getPreferredSize())}（本项目 Swing 用例的固定坑）。
 *
 * <h2>光验「动作触发了」还不够</h2>
 * 用户看到的是「点了没反应」，所以最后一条用例
 * （{@link #barActionTopAndBottomActuallyScrollTheGrid}）不看回调、只看
 * {@code myGridView} 的<b>真实滚动值</b>，并按用户描述的<b>连续轮换</b>点三轮
 * 「底 → 顶 → 底 → 顶 → 底 → 顶」。改回 {@code mouseClicked} 时它报的是
 * {@code expected:<178> but was:<0>} —— 和真机现象一字不差。
 *
 * <p>⚠️ 写这条用例时踩过的坑：{@code dispatchEvent} 的事件坐标是<b>收件组件自身</b>坐标系，
 * 不是父容器的。用父容器坐标去派发，{@code contains()} 一律为假，会把自己骗成
 * 「事件送到了但滚动没生效」。
 */
public class Phase40ReliableClickTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private static final String A_TOP = "顶";
    private static final String A_BOTTOM = "底";

    /**
     * 合法地图：3 行 × 5 列，1 仓管员 / 1 箱 / 1 目标。
     *
     * <p>⚠️ 必须带箱子与目标 —— {@code mapNode(String,String,String,String)} 会跑
     * {@code mapNormalize()}，箱/标不配对会被判「无效关卡」并把标题改掉。
     */
    private static final String VALID_LEVEL = "#####\n#@$.#\n#####";

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/click-home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;
        new File(myMaps.sRoot + myMaps.sPath + "创编关卡/").mkdirs();
        new File(myMaps.sRoot + myMaps.sPath + "导入/").mkdirs();
        mySQLite.getInstance().openDataBase();
    }

    // ---------------------------------------------------------------- HoloClick 契约

    @Test
    public void plainComponentFiresOnPressAndReleaseWithoutAnyClickEvent() {
        JPanel c = sized(new JPanel(), 100, 40);
        boolean[] fired = {false};
        HoloClick.install(c, () -> fired[0] = true);

        clickWithoutClickEvent(c, 10, 10, false);

        assertTrue("按下+抬起都落在组件内，即使没有 MOUSE_CLICKED 也必须触发", fired[0]);
    }

    @Test
    public void plainComponentSurvivesJitterBetweenPressAndRelease() {
        JPanel c = sized(new JPanel(), 100, 40);
        boolean[] fired = {false};
        HoloClick.install(c, () -> fired[0] = true);

        clickWithoutClickEvent(c, 10, 10, true);   // 按住期间手抖 2px

        assertTrue("手抖正是系统吞掉 MOUSE_CLICKED 的场景，动作必须照常触发", fired[0]);
    }

    @Test
    public void plainComponentIgnoresAPressThatStartedOutside() {
        JPanel c = sized(new JPanel(), 100, 40);
        boolean[] fired = {false};
        HoloClick.install(c, () -> fired[0] = true);

        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_PRESSED, 500, 500, 1));
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_RELEASED, 10, 10, 1));

        assertFalse("从别处拖进来松手不该触发（等价于 JButton 的 armed 判定）", fired[0]);
    }

    @Test
    public void plainComponentIgnoresAReleaseThatEndedOutside() {
        JPanel c = sized(new JPanel(), 100, 40);
        boolean[] fired = {false};
        HoloClick.install(c, () -> fired[0] = true);

        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_PRESSED, 10, 10, 1));
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_RELEASED, 500, 500, 1));

        assertFalse("按下后拖出组件再松手不该触发", fired[0]);
    }

    // ---------------------------------------------------------------- ActionBar 动作项

    @Test
    public void barActionFiresOnPressAndReleaseWithoutAClickEvent() {
        myActionBar bar = new myActionBar();
        boolean[] fired = {false};
        bar.addBarAction(A_TOP, () -> fired[0] = true);

        JComponent btn = bar.getBarActionComponentForTest(A_TOP);
        assertNotNull("应能取到「顶」这个动作项", btn);
        sized(btn, btn.getPreferredSize().width, btn.getPreferredSize().height);

        clickWithoutClickEvent(btn, 28, 24, false);

        assertTrue("「顶」必须按下+抬起就触发（这正是用户报的那三个按钮之一）", fired[0]);
    }

    @Test
    public void barActionSurvivesJitterBetweenPressAndRelease() {
        myActionBar bar = new myActionBar();
        boolean[] fired = {false};
        bar.addBarAction(A_TOP, () -> fired[0] = true);

        JComponent btn = bar.getBarActionComponentForTest(A_TOP);
        sized(btn, btn.getPreferredSize().width, btn.getPreferredSize().height);

        clickWithoutClickEvent(btn, 28, 24, true);

        assertTrue("手抖时也必须触发", fired[0]);
    }

    @Test
    public void disabledBarActionDoesNotFire() {
        myActionBar bar = new myActionBar();
        boolean[] fired = {false};
        bar.addBarAction(A_TOP, false, () -> fired[0] = true);

        JComponent btn = bar.getBarActionComponentForTest(A_TOP);
        sized(btn, btn.getPreferredSize().width, btn.getPreferredSize().height);

        clickWithoutClickEvent(btn, 28, 24, false);

        assertFalse("置灰的动作项不该被触发", fired[0]);
    }

    // ---------------------------------------------------------------- 「顶 / 底」真的会滚动

    /**
     * 用户报的是「点了没反应」—— 光验证「动作被触发」还不够，必须验证
     * <b>网格真的滚动了</b>，而且专挑用户描述的<b>连续轮换</b>场景：
     * 点「底」→ 点「顶」→ 再点「底」→ 再点「顶」…… 每一轮都必须精确落到两端。
     *
     * <p>旧的 {@code mouseClicked} 写法在这条用例下是整条红。
     */
    @Test
    public void barActionTopAndBottomActuallyScrollTheGrid() throws Exception {
        ArrayList<mapNode> savedList = myMaps.m_lstMaps;
        boolean savedSelect = myMaps.isSelect;
        myGridView g = newGridWithManyLevels(60);
        try {
            int max = g.getScrollMaxForTest();
            assertTrue("内容必须高过视口，否则这条用例测不出东西（scrollMax=" + max + "）", max > 0);

            for (int round = 0; round < 3; round++) {
                clickBarAction(g, A_BOTTOM);
                assertEquals("第 " + round + " 轮：点「底」应滚到底", max, g.getScrollValueForTest());

                clickBarAction(g, A_TOP);
                assertEquals("第 " + round + " 轮：点「顶」应滚回顶", 0, g.getScrollValueForTest());
            }
        } finally {
            myMaps.m_lstMaps = savedList;
            myMaps.isSelect = savedSelect;
            // 每个单元都会起一个缩略图 SwingWorker —— 等它们收工再 dispose，
            // 免得给后面的用例留下一堆后台线程 / EDT 任务（全量跑时会互相干扰）。
            g.awaitThumbnails(20000);
            SwingUtilities.invokeAndWait(g::dispose);
        }
    }

    // ---------------------------------------------------------------- 弹出菜单行

    @Test
    public void popupMenuRowFiresOnPressAndReleaseWithoutAClickEvent() {
        JPopupMenu menu = HoloPopupMenu.create();
        boolean[] fired = {false};
        HoloPopupMenu.Row row = HoloPopupMenu.addItem(menu, "定位...", () -> fired[0] = true);
        sized(row, row.getPreferredSize().width, row.getPreferredSize().height);

        clickWithoutClickEvent(row, 5, 5, false);

        assertTrue("溢出菜单 / 右键上下文菜单的条目也必须按下+抬起就触发", fired[0]);
    }

    // ---------------------------------------------------------------- 源码兜底

    /**
     * 本次修过的 6 个文件都必须走 {@link HoloClick}。
     * 用源码扫描兜住「以后又有人顺手写回 {@code mouseClicked}」。
     */
    @Test
    public void everyFixedWidgetUsesHoloClick() throws Exception {
        String[] files = {
                "my/boxman/myActionBar.java",
                "my/boxman/compat/HoloPopupMenu.java",
                "my/boxman/myGridView.java",
                "my/boxman/myPicListView.java",
                "my/boxman/myFileExplorerActivity.java",
                "my/boxman/myRecogView.java",
        };
        for (String f : files) {
            assertTrue(f + " 应改用 HoloClick（mouseReleased 契约）",
                    codeOf(f).contains("HoloClick.install"));
        }
    }

    /**
     * 这 5 个文件里已经<b>没有</b>任何 {@code mouseClicked} 覆写了。
     * （{@code myRecogView} 还有一处用于「点击 vs 长按」判定，故不在此列。）
     */
    @Test
    public void noWidgetInTheseFilesOverridesMouseClickedAlone() throws Exception {
        String[] files = {
                "my/boxman/myActionBar.java",
                "my/boxman/compat/HoloPopupMenu.java",
                "my/boxman/myGridView.java",
                "my/boxman/myPicListView.java",
                "my/boxman/myFileExplorerActivity.java",
        };
        for (String f : files) {
            assertFalse(f + " 不该再有 mouseClicked 覆写（注释已剥掉，所以这里只可能是真代码）",
                    codeOf(f).contains("mouseClicked"));
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 建一个关卡多到能滚动的 {@code myGridView}（用完请自行 {@code dispose()}）。
     *
     * <p>用临时数据根目录，不碰用户真实的 {@code ~/.boxman}。
     */
    private static myGridView newGridWithManyLevels(int count) throws Exception {
        myMaps.m_Sets[2] = 0;      // 网格模式（不是「显示标题」的列表模式）
        myMaps.m_Sets[33] = 0;     // 每行图标个数 = 自动
        myMaps.m_Sets[35] = 60;    // 缩略图边长（原版由 60dip 推得）

        myGridView g = new myGridView(-1, "最近推过的关卡");

        ArrayList<mapNode> list = new ArrayList<mapNode>();
        for (int i = 0; i < count; i++) {
            mapNode nd = new mapNode(VALID_LEVEL, "L" + i, "", "");
            nd.fileName = "L" + i + ".XSB";
            list.add(nd);
        }
        myMaps.m_lstMaps = list;
        SwingUtilities.invokeAndWait(g::refreshGrid);
        SwingUtilities.invokeAndWait(g::validate);
        return g;
    }

    /**
     * 对顶栏动作项做一次「没有 {@code MOUSE_CLICKED} 的点击」，并等 EDT 把延迟的滚动跑完。
     *
     * <p>⚠️ 坐标必须用组件<b>自身</b>坐标系：{@code dispatchEvent} 收到的事件坐标就是
     * 「收件组件」坐标系，不是父容器的。换算错一步 {@code contains()} 就一律为假，
     * 动作根本不会触发 —— 上一轮的临时诊断脚本正是栽在这里，把「按钮没响应」错判成了
     * 「滚动没生效」。
     */
    private static void clickBarAction(myGridView g, String title) throws Exception {
        JComponent btn = g.getActionBar().getBarActionComponentForTest(title);
        assertNotNull("应能取到顶栏动作项「" + title + "」", btn);
        clickWithoutClickEvent(btn, btn.getWidth() / 2, btn.getHeight() / 2, false);
        // scrollToPosition 走 invokeLater，先把它跑掉再读滚动值
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    /** 派发一次「没有 MOUSE_CLICKED 的点击」：按下 →（可选手抖）→ 抬起。 */
    private static void clickWithoutClickEvent(Component c, int x, int y, boolean jitter) {
        int upX = jitter ? x + 2 : x;
        int upY = jitter ? y + 1 : y;
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_PRESSED, x, y, 1));
        if (jitter) {
            c.dispatchEvent(mouse(c, MouseEvent.MOUSE_DRAGGED, upX, upY, 0));
        }
        c.dispatchEvent(mouse(c, MouseEvent.MOUSE_RELEASED, upX, upY, 1));
    }

    private static MouseEvent mouse(Component c, int id, int x, int y, int clickCount) {
        return new MouseEvent(c, id, System.currentTimeMillis(), 0, x, y, clickCount, false,
                MouseEvent.BUTTON1);
    }

    /** 组件没被布局过时尺寸是 0×0，{@code contains()} 一律为假 —— 先给个真实几何。 */
    private static <T extends JComponent> T sized(T c, int w, int h) {
        c.setSize(Math.max(1, w), Math.max(1, h));
        return c;
    }

    /** 读源码并剥掉注释（注释里提到 {@code mouseClicked} 会误报）。 */
    private static String codeOf(String relPath) throws IOException {
        File f = new File("src/main/java/" + relPath);
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
