package my.boxman;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 H 修正①：单击<b>组别</b>行（入门关卡 / 进阶关卡 / 花样关卡 / 关卡扩展）只能
 * 展开或折叠，<b>不能</b>顺带打开某个关卡集。
 *
 * <p>原版 Android 的 {@code ExpandableListView}：单击组别条目就是纯粹的
 * {@code expandGroup / collapseGroup}，只有 {@code onChildClick} 才会进入关卡集。
 * PC 侧把列表落成 {@code JTree} 之后多了一条原版没有的行为 —— {@code JTree} 展开时<b>默认会滚动</b>
 * （{@code JTree.scrollsOnExpand} 默认 {@code true} → {@code toggleExpandState()} 调
 * {@code ensureRowsAreVisible(row, row + 可见子节点数)}，尽量把子项滚进视口）。
 * 于是「按下 → 展开并滚动 → {@code mouseClicked} 还按同一坐标取行」之间列表已经错位，
 * 鼠标底下变成了某个<b>子项</b>，{@code browLevels()} 被触发 —— 表现就是
 * 「只点了一下『关卡扩展』，却自动打开了 Boxxle_all 之类的关卡集」。
 * 关卡集越多、组别行越靠底部，错位越明显，所以「有时正常，有时不正常」。
 *
 * <p>修法是两条一起上：
 * <ol>
 *   <li>关掉 {@code scrollsOnExpand}（原版本来就不滚）—— 用例
 *       {@link #treeDoesNotScrollOnExpand}；</li>
 *   <li>打开关卡集前认「按下时那一行的条目」，列表怎么滚都不认错行 —— 用例
 *       {@link #listShiftingBetweenPressAndClickDoesNotOpenASet}。</li>
 * </ol>
 */
public class Phase36GroupClickTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 组别下标：0 入门 / 1 进阶 / 2 花样 / 3 关卡扩展。 */
    private static final int GROUP_EXT = 3;

    /** 给「关卡扩展」组塞够多的关卡集 —— 集合一多，展开才要滚动，原缺陷才复现得出来。 */
    private static final int EXTRA_SETS = 24;

    private static mySQLite sql;

    /** 记录 {@code browLevels()}（进入关卡集）是否被调用过。 */
    private static final class Spy extends BoxManPC {
        int opened = -1;   // 被打开的 childPos；-1 表示没进过关卡集

        @Override
        void browLevels(int groupPos, int childPos) {
            opened = childPos;
        }
    }

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase36").getAbsolutePath();
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();

        for (int i = 0; i < EXTRA_SETS; i++) {
            String title = "P36_S" + (i < 10 ? "0" : "") + i;
            if (sql.find_Set(title) <= 0) sql.add_T(GROUP_EXT, title, "", "");
        }
    }

    @After
    public void tearDown() {
        // 全局静态：组别记忆与「正在进入关卡集」标志都要还回去（见 TEST_NOTES.md）
        myMaps.m_Sets[0] = 0;
        myMaps.m_Sets[1] = 0;
        myMaps.curJi = false;
    }

    // ------------------------------------------------------------ 用例

    @Test
    public void clickingAGroupRowOnlyExpandsIt() {
        Spy app = new Spy();
        try {
            layout(app);
            JTree tree = app.getLevelTree();

            int row = groupRow(tree, GROUP_EXT);
            assertTrue("应找得到「关卡扩展」组别行", row >= 0);
            assertFalse("用例起点：该组应是折叠的", tree.isExpanded(row));

            clickRow(tree, row);

            assertTrue("单击组别应展开它", tree.isExpanded(row));
            assertTrue("单击组别不应进入关卡集，实际进了 childPos=" + app.opened,
                    app.opened < 0);
        } finally {
            app.dispose();
        }
    }

    /**
     * 列表在「按下」与「单击」之间被滚动过（原缺陷的成因），此时该坐标已经换成某个子项 ——
     * 也不能打开它。
     */
    @Test
    public void listShiftingBetweenPressAndClickDoesNotOpenASet() {
        Spy app = new Spy();
        try {
            layout(app);
            JTree tree = app.getLevelTree();

            // 先展开前三组，让内容长到能滚动
            for (int g = 0; g < GROUP_EXT; g++) tree.expandRow(groupRow(tree, g));
            app.validate();      // 让树的高度真的长出来，否则视口会被夹住、滚不动
            int row = groupRow(tree, GROUP_EXT);
            assertFalse("用例起点：该组应是折叠的", tree.isExpanded(row));

            JViewport vp = (JViewport) tree.getParent();
            assertNotNull("树应挂在 JViewport 里", vp);

            // 鼠标停的位置用**视口坐标**记（真实鼠标就是屏幕/视口位置），
            // 派发时再按当前 viewPosition 换算成树坐标 —— 列表一滚，同一位置就换了一行。
            Rectangle r = tree.getRowBounds(row);
            Point at = new Point(r.x + 20 - vp.getViewPosition().x,
                    r.y + r.height / 2 - vp.getViewPosition().y);
            long when = System.currentTimeMillis();

            // 按下落在组别行
            tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_PRESSED, when, treeAt(vp, at)));
            assertTrue("按下应让组别展开", tree.isExpanded(row));

            // 模拟 JTree 展开时的自动滚动：列表上移后，鼠标底下换成了某个子项
            vp.setViewPosition(new Point(0, vp.getViewPosition().y + 300));
            TreePath now = tree.getPathForLocation(treeAt(vp, at).x, treeAt(vp, at).y);
            assertTrue("列表滚动后，鼠标底下应已换成别的行",
                    now != null && !new TreePath(pathOf(tree, row)).equals(now));

            tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_RELEASED, when, treeAt(vp, at)));
            tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_CLICKED, when, treeAt(vp, at)));

            assertTrue("列表错位后也不该打开关卡集，实际进了 childPos=" + app.opened,
                    app.opened < 0);
        } finally {
            app.dispose();
        }
    }

    @Test
    public void clickingAChildRowStillOpensTheSet() {
        Spy app = new Spy();
        try {
            layout(app);
            JTree tree = app.getLevelTree();

            int groupRow = groupRow(tree, GROUP_EXT);
            tree.expandRow(groupRow);
            int childRow = groupRow + 1;      // 该组的第一个关卡集
            clickRow(tree, childRow);

            assertTrue("单击关卡集条目仍应进入该关卡集", app.opened >= 0);
        } finally {
            app.dispose();
        }
    }

    /** 根因：展开组别时不允许再自动滚动（原版 ExpandableListView 不滚）。 */
    @Test
    public void treeDoesNotScrollOnExpand() {
        Spy app = new Spy();
        try {
            layout(app);
            JTree tree = app.getLevelTree();
            assertFalse("展开时不允许再自动滚动（那会让鼠标底下的行错位）",
                    tree.getScrollsOnExpand());
        } finally {
            app.dispose();
        }
    }

    // ------------------------------------------------------------ 辅助

    /** 让窗口的几何真正生效（{@code getRowBounds / getPathForLocation} 都要用）。 */
    private static void layout(BoxManPC app) {
        app.pack();
        app.validate();
    }

    /** 组别（第 {@code index} 个）当前所在的行号；找不到返回 -1。 */
    private static int groupRow(JTree tree, int index) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        assertNotNull(root);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) root.getChildAt(index);
        assertNotNull(groupNode);
        return tree.getRowForPath(new TreePath(groupNode.getPath()));
    }

    /** 第 {@code row} 行的路径（节点数组）。 */
    private static Object[] pathOf(JTree tree, int row) {
        return tree.getPathForRow(row).getPath();
    }

    /** 视口坐标 → 树坐标（视口滚动就是靠把视图挪到负坐标实现的）。 */
    private static Point treeAt(JViewport vp, Point at) {
        Point view = vp.getViewPosition();
        return new Point(at.x + view.x, at.y + view.y);
    }

    /** 在指定行的中心派发一次完整的「按下 → 抬起 → 单击」。 */
    private static void clickRow(JTree tree, int row) {
        Rectangle r = tree.getRowBounds(row);
        assertTrue("行 " + row + " 应有矩形区域", r != null);
        Point p = new Point(r.x + 20, r.y + r.height / 2);

        long when = System.currentTimeMillis();
        tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_PRESSED, when, p));
        tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_RELEASED, when, p));
        tree.dispatchEvent(mouse(tree, MouseEvent.MOUSE_CLICKED, when, p));
    }

    private static MouseEvent mouse(JTree tree, int id, long when, Point p) {
        return new MouseEvent(tree, id, when, InputEvent.BUTTON1_DOWN_MASK,
                p.x, p.y, 1, false, MouseEvent.BUTTON1);
    }
}
