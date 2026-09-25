package my.boxman;

import my.boxman.compat.android.graphics.Canvas;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-2 验收：原版「关卡图像识别」的两个类 ——
 * {@code myRecogView}（831 行）与 {@code myRecogViewMap}（1047 行）的还原。
 *
 * <p>改写前 PC 版分别是 183 行与 196 行：自造了一条「识别操作」菜单（原版没有），
 * 底行 10 个按钮等分铺满，没有长按清理菜单、没有连续调整定时器、
 * 没有「请选择:」退出框，关卡图也没有四条边线指示灯、可拖动边线与识别算法。
 *
 * <p>用例不联网、不弹窗：识别用的底图是一张内存里生成的纯色图，
 * 这样「样本格 vs 全图」的比对结果完全确定（同色 → 必然全部命中）。
 */
public class Phase16RecogViewTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    private static final int PIC = 1200;      // 底图边长
    private static final int CELL = 367;      // 3 列时每格宽度： (1150-50+1)/3 = 367.0

    private myRecogView win;
    private BufferedImage savedEdPict;
    private mapNode savedCurMap;
    private String savedSFile;
    private int savedCurMapNum;
    private int savedEdLeft, savedEdTop, savedEdRight, savedEdBottom, savedEdRows, savedEdCols;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/recog-home";
        myMaps.sPath = "/";
        new java.io.File(myMaps.sRoot).mkdirs();
    }

    @Before
    public void setUp() {
        savedEdPict = myMaps.edPict;
        savedCurMap = myMaps.curMap;
        savedSFile = myMaps.sFile;
        savedCurMapNum = myMaps.curMapNum;
        savedEdLeft = myMaps.edPictLeft;
        savedEdTop = myMaps.edPictTop;
        savedEdRight = myMaps.edPictRight;
        savedEdBottom = myMaps.edPictBottom;
        savedEdRows = myMaps.edRows;
        savedEdCols = myMaps.edCols;
    }

    @After
    public void tearDown() {
        if (win != null) {
            if (win.getMap() != null) win.getMap().cancelPendingTimers();
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.edPict = savedEdPict;
        myMaps.curMap = savedCurMap;
        myMaps.sFile = savedSFile;
        myMaps.curMapNum = savedCurMapNum;
        myMaps.edPictLeft = savedEdLeft;
        myMaps.edPictTop = savedEdTop;
        myMaps.edPictRight = savedEdRight;
        myMaps.edPictBottom = savedEdBottom;
        myMaps.edRows = savedEdRows;
        myMaps.edCols = savedEdCols;
    }

    // ---------------------------------------------------------------- 夹具

    /** 一张纯色底图 —— 所有格子长得一模一样，识别结果可完全预测。 */
    private static BufferedImage uniformPic() {
        BufferedImage img = new BufferedImage(PIC, PIC, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x808080));
        g.fillRect(0, 0, PIC, PIC);
        g.dispose();
        return img;
    }

    /** 打开窗口并把地图视图摆成 3×3 网格（原版默认是 2×2，那会让单击直接返回）。 */
    private myRecogView openView() {
        myMaps.edPict = uniformPic();
        win = new myRecogView();
        myRecogViewMap m = win.getMap();
        if (m.getWidth() == 0 || m.getHeight() == 0) {
            m.setSize(370, 699);
        }
        m.m_nCols = 3;
        m.m_nRows = 3;
        m.setArena();
        render(m);
        return win;
    }

    /** 走一遍真实的绘制路径 —— {@code m_fLeft}/{@code m_fTop}/{@code m_fScale} 只在 onDraw 后才是对的。 */
    private static void render(myRecogViewMap m) {
        BufferedImage img = new BufferedImage(Math.max(1, m.getWidth()),
                Math.max(1, m.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Canvas c = new Canvas();
        c.setGraphics(g);
        m.onDraw(c);
        g.dispose();
    }

    private static int toScreen(double imgCoord, myRecogViewMap m) {
        return (int) (imgCoord * m.getViewScale() + m.getViewLeft());
    }

    private static int toScreenY(double imgCoord, myRecogViewMap m) {
        return (int) (imgCoord * m.getViewScale() + m.getViewTop());
    }

    /** 格子 (r,c) 中心点的屏幕坐标 */
    private static int cellX(int c, myRecogViewMap m) {
        return toScreen(m.getMapLeft() + (c + 0.5) * CELL, m);
    }

    private static int cellY(int r, myRecogViewMap m) {
        return toScreenY(m.getMapTop() + (r + 0.5) * CELL, m);
    }

    private static void clickCell(myRecogViewMap m, int r, int c) {
        m.clickForTest(cellX(c, m), cellY(r, m));
    }

    // ================================================================ ActionBar / 菜单

    @Test
    public void testActionBarMatchesRecogXml() {
        myRecogView w = openView();
        // 原版 setTitle("")
        assertEquals("", w.getActionBar().getBarTitle());
        // 原版 setDisplayHomeAsUpEnabled(true)
        assertTrue(w.getActionBar().isUpEnabled());
        // recog.xml 的 6 项全是 showAsAction="always"
        assertEquals(6, w.getActionBar().getBarActionCount());
        assertEquals(Arrays.asList("？", "悔", "度", "减", "增", "识别"),
                w.getActionBar().getBarActionTitles());
        // 没有 never 项 → 溢出菜单为空 → 原版不画 ⋮
        assertEquals(0, w.getActionBar().getActionCount());
        assertFalse(w.getActionBar().isOverflowVisible());
    }

    @Test
    public void testToggleRecogRewritesItsOwnTitle() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        assertTrue("原版 setArena() 里 isRecog = true", m.isRecog);

        w.clickBarAction("识别");
        assertFalse(m.isRecog);
        assertTrue("原版 mt.setTitle(\"编辑\")", w.getActionBar().getBarActionTitles().contains("编辑"));
        assertFalse(w.getActionBar().getBarActionTitles().contains("识别"));

        w.clickBarAction("编辑");
        assertTrue(m.isRecog);
        assertTrue(w.getActionBar().getBarActionTitles().contains("识别"));
    }

    @Test
    public void testRestoreMenuUndoesLastRecognition() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = 1;                       // 墙壁
        w.getCellArray()[0][0] = '#';       // 假装识别出了一个墙
        w.myBackup();                       // 备份当前（含 '#')
        w.getCellArray()[1][1] = '#';       // 又识别出一个

        w.clickBarAction("悔");
        // myRestore() 是「交换」语义：交换之后当前图变成备份图，备份图变成交换前的当前图。
        // 备份是在 0,0='#' / 1,1='-' 时做的，所以换完之后 0,0 仍是 '#'、1,1 回到 '-'。
        assertEquals('-', w.getCellArray()[1][1]);
        assertEquals('#', w.getCellArray()[0][0]);
        assertEquals('#', w.getBackupArray()[0][0]);
        assertEquals('#', w.getBackupArray()[1][1]);
    }

    @Test
    public void testShrinkAndExtendGridCount() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();

        // 原版下限：m_nRows > 3 && m_nCols > 3 才允许继续减
        m.m_nRows = 4;
        m.m_nCols = 4;
        w.clickBarAction("减");
        assertEquals(3, m.m_nCols);

        m.m_nRows = 4;
        m.m_nCols = 3;
        w.clickBarAction("减");
        assertEquals("m_nCols > 3 不成立 → 不减", 3, m.m_nCols);

        // 「增」的前置条件：(int)m_nWidth > 8
        m.m_nRows = 3;
        m.m_nCols = 3;
        m.m_nMapLeft = 50;
        m.m_nMapRight = 1150;
        w.clickBarAction("增");
        assertEquals(4, m.m_nCols);

        // 格子细到 8px 以下时拒绝继续增
        m.m_nCols = 200;                       // m_nWidth = 1101/200 = 5.5 → (int) = 5
        w.clickBarAction("增");
        assertEquals("(int)m_nWidth <= 8 → 不增", 200, m.m_nCols);
    }

    // ================================================================ 底行按钮

    @Test
    public void testBottomBarButtonsMatchLayout() {
        myRecogView w = openView();
        // 原版 recog_view.xml：6 个 29dp 元素按钮 + 8dp 占位 + 4 个 35dp 方向按钮
        assertEquals(29, w.bt_Floor.getPreferredSize().width);
        assertEquals(29, w.bt_Player.getPreferredSize().width);
        assertEquals(35, w.bt_Left.getPreferredSize().width);
        assertEquals(35, w.bt_Down.getPreferredSize().width);
        assertEquals(29, w.bt_Floor.getPreferredSize().height);

        // 依 XML 累计的 x 坐标：地板 2，墙壁 33，箱子 66，标箱 99，目标 132，人 165，
        // 8dp 占位后 ← 206、→ 245、↑ 284、↓ 323
        assertEquals(2, w.bt_Floor.getX());
        assertEquals(33, w.bt_Wall.getX());
        assertEquals(66, w.bt_Box.getX());
        assertEquals(99, w.bt_BoxGoal.getX());
        assertEquals(132, w.bt_Goal.getX());
        assertEquals(165, w.bt_Player.getX());
        assertEquals(206, w.bt_Left.getX());
        assertEquals(245, w.bt_Right.getX());
        assertEquals(284, w.bt_Up.getX());
        assertEquals(323, w.bt_Down.getX());
        assertEquals("最后一个按钮右边界 358 <= 370", 358, w.bt_Down.getX() + 35);
    }

    @Test
    public void testElementButtonHighlightFollowsMode() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        int base = 0xff334455;
        int recogHL = 0x9f0000ff;
        int editHL = 0x9fff3300;

        // 初始全部是底色
        assertEquals(new Color(base, true), w.bt_Floor.getColor());

        w.setColor(1);                                   // 选墙壁
        assertEquals(1, m.m_nObj);
        assertEquals(new Color(recogHL, true), w.bt_Wall.getColor());
        assertEquals(new Color(base, true), w.bt_Floor.getColor());

        w.setColor(1);                                   // 再点一次 = 取消
        assertEquals(-1, m.m_nObj);
        assertEquals(new Color(base, true), w.bt_Wall.getColor());

        // 编辑模式用另一种高亮色
        w.toggleRecogForTest();                          // → isRecog = false
        assertFalse(m.isRecog);
        w.setColor(2);
        assertEquals(new Color(editHL, true), w.bt_Box.getColor());

        // setColor(-1)：取消底行高亮（原版 recog_complete 的分支）
        w.setColor(-1);
        assertEquals(-1, m.m_nObj);
        assertEquals(new Color(base, true), w.bt_Box.getColor());
    }

    // ================================================================ 定时器 / 边线

    @Test
    public void testShiftEdgeLeftTopVersusRightBottom() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.isLeftTop = true;
        int left0 = m.getMapLeft();

        w.shiftEdgeForTest(1);            // ←
        assertEquals(left0 - 1, m.getMapLeft());

        m.isLeftTop = false;              // 改成「右、下灯亮」→ ← 变成收右边界
        int right0 = m.getMapRight();
        w.shiftEdgeForTest(1);
        assertEquals(right0 - 1, m.getMapRight());
    }

    @Test
    public void testShiftEdgeCancelsContinuousAdjust() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.isLeftTop = true;
        int left0 = m.getMapLeft();

        w.actNum = 1;                     // 假装正在连续调整（长按 ← 的效果）
        w.shiftEdgeForTest(1);            // 原版：actNum > 0 时只取消，不动边线
        assertEquals(0, w.getActNum());
        assertEquals(left0, m.getMapLeft());

        w.shiftEdgeForTest(1);            // 取消之后再点 → 正常微调
        assertEquals(left0 - 1, m.getMapLeft());
    }

    @Test
    public void testUpDataMovesEdgeByThree() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.isLeftTop = true;
        m.m_nMapLeft = 100;

        w.actNum = 1;
        w.UpData(1);
        assertEquals(97, m.getMapLeft());

        w.actNum = 2;
        w.UpData(1);
        assertEquals(100, m.getMapLeft());

        // UpData(p != 1) 直接返回（原版守卫）—— 先停掉定时器循环再验
        w.actNum = 0;
        w.UpData(0);
        assertEquals(100, m.getMapLeft());
    }

    // ================================================================ 识别结果维护

    @Test
    public void testBackupAndRestoreSwapArrays() {
        myRecogView w = openView();
        char[][] cur = w.getCellArray();
        char[][] bk = w.getBackupArray();
        cur[3][4] = '#';
        bk[3][4] = '-';

        w.myBackup();
        assertEquals('#', bk[3][4]);

        cur[3][4] = '$';
        w.myRestore();                    // 交换：当前变成备份的 '#'
        assertEquals('#', cur[3][4]);
        assertEquals('$', bk[3][4]);
    }

    @Test
    public void testRemoveXsbWallClearsEveryHash() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '#';
        a[1][1] = '#';
        a[2][2] = '$';

        w.removeXSB('#', true);
        assertEquals('-', a[0][0]);
        assertEquals('-', a[1][1]);
        assertEquals("不该动箱子", '$', a[2][2]);
    }

    @Test
    public void testRemoveXsbBoxPureVersusAll() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '$';
        a[0][1] = '*';                    // 目标点上的箱子
        a[0][2] = '.';

        w.removeXSB('$', false);          // 只清「纯箱子」
        assertEquals('-', a[0][0]);
        assertEquals("纯箱子模式下不动目标点箱子", '*', a[0][1]);

        a[0][0] = '$';
        w.removeXSB('$', true);           // 清所有箱子
        assertEquals('-', a[0][0]);
        assertEquals("目标点箱子退化成目标点", '.', a[0][1]);
    }

    @Test
    public void testRemoveXsbBoxGoalTwoModes() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '*';

        w.removeXSB('*', false);          // 「保留目标点清理箱子」
        assertEquals('.', a[0][0]);

        a[0][0] = '*';
        w.removeXSB('*', true);           // 「清理目标点箱子」
        assertEquals('-', a[0][0]);
    }

    @Test
    public void testRemoveXsbGoalPureVersusAll() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '.';
        a[0][1] = '*';
        a[0][2] = '+';

        w.removeXSB('.', false);          // 只清「纯目标点」
        assertEquals('-', a[0][0]);
        assertEquals("* 是箱子+目标点，不属于「纯目标点」", '*', a[0][1]);
        assertEquals("+ 是人+目标点，不属于「纯目标点」", '+', a[0][2]);

        a[0][0] = '.';
        w.removeXSB('.', true);           // 清所有目标点
        assertEquals('-', a[0][0]);
        assertEquals('$', a[0][1]);
        assertEquals('@', a[0][2]);
    }

    @Test
    public void testClearXsbEmptiesEverything() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        for (int i = 0; i < 4; i++) a[i][i] = '#';
        w.clearXSB();
        for (int i = 0; i < 4; i++) assertEquals('-', a[i][i]);
    }

    @Test
    public void testMyCountBoxesAndGoals() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '$';
        a[0][1] = '*';   // 既是箱子也是目标点
        a[0][2] = '.';
        a[1][0] = '+';   // 目标点
        w.myCount();
        assertEquals(2, w.getBoxNum());
        assertEquals(3, w.getDstNum());
    }

    @Test
    public void testGetXsbIsRowsPlusOneByColsPlusOne() {
        myRecogView w = openView();
        char[][] a = w.getCellArray();
        a[0][0] = '#';
        a[2][2] = '$';
        String s = w.getXSB();
        assertNotNull(s);
        String[] lines = s.split("\n");
        // 原版两个循环都是 <=，所以是 (m_nRows+1) 行 × (m_nCols+1) 列
        assertEquals(4, lines.length);
        assertEquals(4, lines[0].length());
        assertEquals('#', lines[0].charAt(0));
        assertEquals('$', lines[2].charAt(2));
    }

    @Test
    public void testGetXsbReturnsNullForTinyGrid() {
        myRecogView w = openView();
        w.getMap().m_nCols = 2;          // < 3
        assertNull(w.getXSB());
    }

    // ================================================================ 地图视图几何

    @Test
    public void testInitArenaDefaults() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        assertEquals(PIC, m.getPicWidth());
        assertEquals(PIC, m.getPicHeight());

        // 原版 initArena()：四边各让出 50px，格子数回到 2×2、格子尺寸回到「未定」
        myRecogViewMap fresh = new myRecogViewMap();
        fresh.setSize(370, 699);
        fresh.Init(w);
        fresh.initArena();
        assertEquals(50, fresh.getMapLeft());
        assertEquals(50, fresh.getMapTop());
        assertEquals(1150, fresh.getMapRight());
        assertEquals(1150, fresh.getMapBottom());
        assertEquals(2, fresh.m_nCols);
        assertEquals(2, fresh.m_nRows);
        assertEquals(-1f, fresh.getCellWidth(), 0.001f);
        fresh.cancelPendingTimers();
    }

    @Test
    public void testLampRectsSitOnTheFourEdges() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        // 原版：指示灯是以边线中点为中心、半径 50 的圆
        assertEquals(0f, m.getLeftLampRect().left, 0.001f);
        assertEquals(0f, m.getTopLampRect().top, 0.001f);
        assertEquals(1200f, m.getRightLampRect().right, 0.001f);
        assertEquals(1200f, m.getBottomLampRect().bottom, 0.001f);
        // 左边线灯中心 y = m_nMapTop + (bottom-top)/2 = 50 + 550 = 600
        assertEquals(550f, m.getLeftLampRect().top, 0.001f);
        assertEquals(650f, m.getLeftLampRect().bottom, 0.001f);
    }

    @Test
    public void testSingleTapPlacesAndTogglesWallInEditMode() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.isRecog = false;               // 编辑模式才走「直接放元素」的分支
        m.m_nObj = 1;                    // 墙壁

        clickCell(m, 1, 2);
        assertEquals(1, m.getCursorRow());
        assertEquals(2, m.getCursorCol());
        assertEquals('#', w.getCellArray()[1][2]);

        clickCell(m, 1, 2);              // 再点一次 → 取消
        assertEquals('-', w.getCellArray()[1][2]);
    }

    @Test
    public void testSingleTapGoalTogglesCompoundElements() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        char[][] a = w.getCellArray();
        m.isRecog = false;
        m.m_nObj = 4;                    // 目标点

        a[0][0] = '@';  clickCell(m, 0, 0);  assertEquals('+', a[0][0]);
        clickCell(m, 0, 0);                  assertEquals('@', a[0][0]);
        a[0][0] = '$';  clickCell(m, 0, 0);  assertEquals('*', a[0][0]);
        clickCell(m, 0, 0);                  assertEquals('$', a[0][0]);
        a[0][0] = '.';  clickCell(m, 0, 0);  assertEquals('-', a[0][0]);
        clickCell(m, 0, 0);                  assertEquals('.', a[0][0]);
    }

    @Test
    public void testSingleTapPlayerIsUnique() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        char[][] a = w.getCellArray();
        m.isRecog = false;
        m.m_nObj = 5;                    // 仓管员

        a[0][0] = '@';
        clickCell(m, 1, 1);
        assertEquals("旧的仓管员要被清掉", '-', a[0][0]);
        assertEquals('@', a[1][1]);

        // 站在目标点上 → '+'
        a[1][1] = '.';
        clickCell(m, 1, 1);
        assertEquals('+', a[1][1]);
    }

    @Test
    public void testSingleTapOutsideValidAreaClearsFocus() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = 1;
        clickCell(m, 1, 1);
        assertTrue(m.getFocusRect().top >= 0);

        // 点在图外（左上角）
        m.clickForTest(0, 0);
        assertEquals(-1, m.getFocusRect().top);
    }

    @Test
    public void testDoubleTapNudgesLeftEdge() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        int left0 = m.getMapLeft();
        // 双击左边线左侧一点点 → 左边线左移 1（原版 onDoubleTap 的第一分支）。
        // 原版要求 y 同时离上、下边线 > 200px，所以取边线中点（y=600）。
        int x = toScreen(left0 - 5, m);
        int y = toScreenY(600, m);
        m.doubleTapForTest(x, y);
        assertEquals(left0 - 1, m.getMapLeft());
    }

    @Test
    public void testLongPressOnLampStartsBlink() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        // 左边线指示灯的中心
        float cx = (m.getLeftLampRect().left + m.getLeftLampRect().right) / 2;
        float cy = (m.getLeftLampRect().top + m.getLeftLampRect().bottom) / 2;
        m.longPressForTest(toScreen(cx, m), toScreenY(cy, m));

        assertEquals(0, m.getLamp());
        assertTrue("左灯亮 → isLeftTop = true", m.isLeftTop);
        assertEquals("原版 actNum = 5（仓管员闪烁）", 5, w.getActNum());
        // 原版先 isLamp = false，紧接着 UpData(1) 的 case 5 又取反一次 → 结果是 true
        assertTrue(m.isLamp);
        m.cancelPendingTimers();
        w.actNum = 0;
    }

    @Test
    public void testSingleTapOnLampTogglesWhichEdgesAreActive() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = -1;                   // 识别模式下单击指示灯才切换
        m.isLeftTop = true;
        float cx = (m.getRightLampRect().left + m.getRightLampRect().right) / 2;
        float cy = (m.getRightLampRect().top + m.getRightLampRect().bottom) / 2;
        m.clickForTest(toScreen(cx, m), toScreenY(cy, m));
        assertFalse(m.isLeftTop);
    }

    // ================================================================ 识别算法

    @Test
    public void testFindSubimagesHitsEveryCellForUniformPicture() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        // 编辑模式：单击只放元素，不会顺手触发一次识别，这样才能干净地单测 findSubimages
        m.isRecog = false;
        m.m_nObj = 0;                    // 地板：原版 m_nObj == 0 时不做「已识别格子」锁定
        clickCell(m, 0, 0);              // 建立样本格（cur_Rect）
        assertTrue(m.getFocusRect().top >= 0);

        m.curPoints = m.findSubimages();
        // 纯色底图上每个格子都与样本一致 → 3×3 全部命中
        assertEquals(9, m.curPoints.size());
        // 样本格自己也一定在结果里（原版 locs.add(c << 16 | r)）
        assertTrue(m.curPoints.contains(0 << 16 | 0));
    }

    @Test
    public void testFindSubimagesLocksAlreadyRecognisedCells() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = 1;                    // 墙壁
        clickCell(m, 0, 0);              // 识别模式 → 顺手把 3×3 全填成 '#'
        assertEquals('#', w.getCellArray()[2][2]);

        // 清掉样本格与 (2,2)，让它们重新参与比对：
        // 其余 7 格都已经是 '#'，被「锁定之前识别出来的元素」这条规则排除掉。
        w.getCellArray()[0][0] = '-';
        w.getCellArray()[2][2] = '-';
        m.curPoints = m.findSubimages();
        assertEquals(2, m.curPoints.size());
        assertTrue(m.curPoints.contains(0 << 16 | 0));
        assertTrue(m.curPoints.contains(2 << 16 | 2));
    }

    @Test
    public void testDoActionFillsAllMatchingCells() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = 1;
        clickCell(m, 0, 0);
        w.doAction();

        char[][] a = w.getCellArray();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                assertEquals("#(" + r + "," + c + ")", '#', a[r][c]);
            }
        }
    }

    @Test
    public void testDoActionSkipsAlreadyRecognisedCells() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.m_nObj = 2;                    // 箱子
        clickCell(m, 0, 0);
        w.getCellArray()[1][1] = '#';    // 已经被识别过的格子
        w.doAction();
        assertEquals("m_nObj != 0 时，非 '-' 的格子要锁定", '#', w.getCellArray()[1][1]);
        assertEquals('$', w.getCellArray()[2][2]);
    }

    @Test
    public void testCalSimilarityIsOneForIdenticalArrays() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        int[] a = new int[1024];
        int[] b = new int[1024];
        assertEquals(1.0, m.calSimilarity(a, b), 1e-9);

        for (int i = 0; i < 1024; i++) b[i] = 1;
        assertEquals(0.0, m.calSimilarity(a, b), 1e-9);

        // 一半不同 → (0.5)^2
        for (int i = 0; i < 512; i++) b[i] = 0;
        assertEquals(0.25, m.calSimilarity(a, b), 1e-9);
    }

    @Test
    public void testDeviateWeightsAndColourBias() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        int[] dest = new int[1024];
        BufferedImage grey = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = grey.createGraphics();
        g.setColor(new Color(0x808080));
        g.fillRect(0, 0, 64, 64);
        g.dispose();

        int bias = m.getPixelDeviateWeightsArray(dest, grey, 0);
        assertEquals("灰阶图 RGB 三通道差 < 10 → maxColor 返回 0", 0, bias);
        // 纯色图 → 每个像素都等于均值 → 离差全 0
        for (int i = 0; i < 1024; i++) assertEquals(0, dest[i]);
    }

    // ================================================================ 相似度设置框

    @Test
    public void testSimilarityDialogContentHasFiveBoxes() {
        myRecogView w = openView();
        JComponent content = w.buildSimilarityContentForTest();
        List<myRecogView.SimilarityBox> boxes = new ArrayList<>();
        collectBoxes(content, boxes);
        assertEquals(5, boxes.size());
        assertEquals("5", boxes.get(0).getText());
        assertEquals("9", boxes.get(4).getText());
        assertEquals(30, boxes.get(0).getPreferredSize().width);
        // 初始高亮跟着 mMap.mSimilarity（原版 setBT_Color(my_RBS[mSimilarity-5], true)）
        assertEquals(6, w.getMap().mSimilarity);
        assertTrue(boxes.get(1).isSelected());
        assertFalse(boxes.get(0).isSelected());
    }

    @Test
    public void testSimilarityBoxClickUpdatesModel() {
        myRecogView w = openView();
        myRecogViewMap m = w.getMap();
        m.mSimilarity = 6;
        JComponent content = w.buildSimilarityContentForTest();
        List<myRecogView.SimilarityBox> boxes = new ArrayList<>();
        collectBoxes(content, boxes);

        // 点「8」→ mMap.mSimilarity = 8，且只有它高亮（原版 RadioGroup 的 OnCheckedChangeListener）
        clickListener(boxes.get(3));
        assertEquals(8, m.mSimilarity);
        assertTrue(boxes.get(3).isSelected());
        assertFalse(boxes.get(0).isSelected());
        assertFalse(boxes.get(1).isSelected());
    }

    // ================================================================ 「进入编辑」的状态准备

    @Test
    public void testPrepareEditHandoffForNormalGrid() {
        myRecogView w = openView();
        myMaps.curMap = new mapNode("-", "旧关卡", "", "");
        w.getCellArray()[0][0] = '#';

        w.prepareEditHandoff();

        assertEquals("原版 myMaps.sFile = \"创编关卡\"", "创编关卡", myMaps.sFile);
        assertEquals(-4, myMaps.curMapNum);      // 正常尺寸 → -4
        assertTrue(myMaps.curMap.fileName.startsWith("Recog_"));
        assertTrue(myMaps.curMap.fileName.endsWith(".XSB"));
        assertEquals(3, myMaps.edRows);
        assertEquals(3, myMaps.edCols);
        assertEquals(50, myMaps.edPictLeft);
        assertEquals(50, myMaps.edPictTop);
        assertEquals(1150, myMaps.edPictRight);
        // edPictBottom = m_nMapTop + m_nRows * m_nWidth = 50 + 3*367 = 1151
        assertEquals(1151, myMaps.edPictBottom);
    }

    @Test
    public void testPrepareEditHandoffForTinyGrid() {
        myRecogView w = openView();
        mapNode placeholder = new mapNode("#####\n#@$.#\n#####", "旧", "", "");
        myMaps.curMap = placeholder;
        w.getMap().m_nCols = 2;                  // < 3 → getXSB() 返回 null

        w.prepareEditHandoff();

        assertEquals(-5, myMaps.curMapNum);
        assertNull("原版 level == null 时把 curMap.Map 置空", myMaps.curMap.Map);
        assertSame(placeholder, myMaps.curMap);
    }

    // ================================================================ 退出框

    @Test
    public void testBackDisposesWhenGridTooSmall() {
        myRecogView w = openView();
        w.getMap().m_nCols = 2;
        assertTrue("原版 m_nCols < 3 时 finish()", w.isDisplayable());
        w.backForTest();
        // dispose 后 isDisplayable() 变 false
        assertFalse(w.isDisplayable());
    }

    @Test
    public void testExitDialogExistsAndIsNotShownEagerly() {
        myRecogView w = openView();
        assertNotNull(w.getExitDialog());
        assertFalse("原版 create() 而非 show()，随用随弹", w.getExitDialog().isVisible());
    }

    // ================================================================ 工具

    /** 在组件树里收集所有相似度方块 */
    private static void collectBoxes(Component root, List<myRecogView.SimilarityBox> out) {
        if (root instanceof myRecogView.SimilarityBox) out.add((myRecogView.SimilarityBox) root);
        if (root instanceof Container) {
            for (Component c : ((Container) root).getComponents()) {
                collectBoxes(c, out);
            }
        }
    }

    /** 直接把 MOUSE_CLICKED 交给组件上注册的监听器（等价于点它一下，且不依赖组件是否已显示） */
    private static void clickListener(Component c) {
        MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, 5, 5, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : c.getMouseListeners()) {
            l.mouseClicked(e);
        }
    }
}
