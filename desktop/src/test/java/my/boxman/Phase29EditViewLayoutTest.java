package my.boxman;

import my.boxman.compat.HoloTabBar;
import my.boxman.compat.UiWindow;
import my.boxman.compat.android.graphics.Rect;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 H —— 「关卡编辑器」（{@code myEditView} / {@code myEditViewMap}）的**版面还原**。
 *
 * <p>对着原版截图 {@code screenshot_20260925_125127_my.boxman.jpg}
 * （1260×2844、density 3.4051）量出来的两处结构性偏差，这次一起修掉：
 *
 * <ol>
 *   <li><b>顶行信息栏高 39dp，应为 30dp。</b>原版把这段几何写在**设备像素**里 ——
 *       {@code BoxMan.onCreate()} 里 {@code m_nWinWidth = metric.widthPixels}（= 1260），
 *       于是 {@code obj_Width = min(1260/10, m_PicWidth*2=100) = 100 设备像素}，
 *       {@code m_nArenaTop = 102 设备像素 = 29.96dp}。PC 端的 {@code m_nWinWidth} 是 dp（370），
 *       直接 {@code /10} 得 37 → 顶栏 39dp，而且那个 100 的上限永远够不着。
 *       现在按 {@code myMaps.DENSITY} 先把屏幕宽折回设备像素，再把 2/1/10/5 这些裸像素常量折成 dp。</li>
 *   <li><b>底栏 34dp 且只有文字、没有图标。</b>原版 {@code edit_view.xml} 的 {@code edit_bottom}
 *       是 {@code RadioGroup(background=#ff778899)} + 8 个
 *       {@code <CheckBox style="@style/tab_style" android:drawableTop="...">}，
 *       每个 {@code layout_weight="1.0"}（精确 1/8 等分 = 46.25dp），实测高 163px = 47.87dp。
 *       PC 侧原先是 {@code GridLayout(1,8,4,4)} + 纯文字 {@code JToggleButton}（34dp）。
 *       现在统一走 {@link HoloTabBar}（与 {@code myGameView} 的 {@code main_bottom} 同一份
 *       {@code tab_style}）。</li>
 * </ol>
 *
 * <p>另外顺带对齐了两个禁用态细节（原版截图实测）：
 * 禁用项的**图标不灰**（{@code drawableTop} 的 PNG 没有 state list），只有**文字**变
 * {@code #80ffffff}（≈ 叠在 {@code #778899} 上的 (187,196,204)）；以及「粘贴」进入时
 * {@code setEnabled(!loadClipper().equals(""))}，空剪切板下是灰的。
 */
public class Phase29EditViewLayoutTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** 一个合法的小关卡：3 行 × 5 列，1 个仓管员、1 箱 1 目标。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    /** 原版 {@code tab_style} 的 {@code textSize="9.0dip"}。 */
    private static final int TAB_TEXT_SIZE = 9;

    private myEditView win;
    private mapNode savedCur;
    private String savedRoot, savedPath;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/edit-home";
        myMaps.sPath = "/";
        // myEditViewMap.initView() 用 m_nWinWidth 算顶栏矩形，必须显式给。
        myMaps.m_nWinWidth = UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot + myMaps.sPath + "创编关卡/").mkdirs();
        new File(myMaps.sRoot + myMaps.sPath + "宏/").mkdirs();
        mySQLite.getInstance().openDataBase();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;

        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase29_test.XSB";
        myMaps.curMapNum = -4;

        win = new myEditView();
        // 构造器里的 applyPhoneSize() 会 pack()，Swing 随后异步派发 componentResized →
        // setArena()。不排空 EDT，这一下会随机落在断言中间。
        drainEdt();
        win.validate();
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
    }

    // ---------------------------------------------------------------- 顶行信息栏

    @Test
    public void testTopBarHeightIsThirtyDp() {
        assertEquals("原版 m_nArenaTop = 102 设备像素 = 29.96dp", 30, win.mMap.m_nArenaTop);
    }

    @Test
    public void testTopBarMaterialWidthIsTwentyNineDp() {
        assertEquals("原版 obj_Width = min(1260/10, 100) = 100 设备像素 = 29.36dp → 29",
                29, win.mMap.obj_Width);
        assertEquals("原版 obj_Width（设备像素）应仍是 100，字号由它算",
                100, win.mMap.obj_WidthDev);
    }

    /**
     * 5 个素材槽：地板 / 墙 / 目标 / 箱子 / 仓管员。
     * 原版（折算成 dp）：[0.29, 29.66] [30.25, 58.74] [60.21, 89.57] [90.18, 119.53] [120.14, 149.50]。
     */
    @Test
    public void testTopBarMaterialSlotsMatchOriginal() {
        assertRect("地板", win.mMap.rtF, 0, 0, 29, 29);
        assertRect("墙", win.mMap.rtW, 30, 0, 58, 29);
        assertRect("目标", win.mMap.rtD, 60, 0, 89, 29);
        assertRect("箱子", win.mMap.rtB, 90, 0, 119, 29);
        assertRect("仓管员", win.mMap.rtM, 120, 0, 149, 29);
    }

    /**
     * ⚠️ 原版 {@code rtW.set(obj_Width+2, 1, obj_Width+obj_Width, obj_Width+1)} 少加了一个间隔，
     * 墙面素材因此只有 98 设备像素宽（其它 4 个是 100）。截图实测 floor = 1..100、wall = 102..199，
     * 确认是真·98px。按项目约定**照抄**，别「顺手修好」。
     */
    @Test
    public void testWallSlotKeepsOriginalNarrowerWidth() {
        int wall = win.mMap.rtW.right - win.mMap.rtW.left;
        int floor = win.mMap.rtF.right - win.mMap.rtF.left;
        assertEquals("墙面素材比其它 4 个窄 2 设备像素（原版笔误，照抄）", floor - 1, wall);
    }

    @Test
    public void testSizeBoxStartsWhereOriginalDoes() {
        // 原版 rtSize.left = rtM.right + 10 设备像素 = 508 + 10 = 518px = 152.11dp
        assertEquals("关卡尺寸框左缘", 152, win.mMap.rtSize.left);
        assertEquals("关卡尺寸框右缘 = 宽 - 5 设备像素", 369, win.mMap.rtSize.right);
        assertEquals("关卡尺寸框上缘 = 5 设备像素 ≈ 1dp", 1, win.mMap.rtSize.top);
        assertEquals("关卡尺寸框下缘 = 顶栏高 - 5 设备像素", 29, win.mMap.rtSize.bottom);
    }

    /** 字号 = 原版 {@code obj_Width * 2/5}（设备像素）= 40px = 11.75dp → 12dp。 */
    @Test
    public void testSizeBoxTextSizeIsTwelveDp() {
        assertEquals(12, Math.round(win.mMap.obj_WidthDev * 2 / 5f / myMaps.DENSITY));
    }

    // ---------------------------------------------------------------- 底栏

    @Test
    public void testBottomBarHasEightTabs() {
        JPanel bar = bottomBar();
        assertNotNull("底栏应是 contentPane 的 SOUTH", bar);
        assertEquals("原版 edit_bottom 有 8 个 CheckBox", 8, bar.getComponentCount());
    }

    @Test
    public void testBottomBarHeightIsFortyEightDp() {
        assertEquals("原版实测 163px / 3.4051 = 47.87dp", HoloTabBar.BAR_HEIGHT,
                bottomBar().getPreferredSize().height);
        assertEquals("底栏必须真的占 48px 高", 48, bottomBar().getHeight());
    }

    @Test
    public void testBottomBarIsFlatTabBg() {
        assertEquals("RadioGroup 的 android:background=#ff778899",
                HoloTabBar.BG.getRGB(), bottomBar().getBackground().getRGB());
    }

    /** 原版每个 CheckBox 是 {@code layout_weight="1.0"} → 精确 1/8 等分，格子 46.25dp。 */
    @Test
    public void testBottomBarTabsSplitExactlyIntoEighths() {
        JPanel bar = bottomBar();
        int w = bar.getWidth();
        assertEquals("底栏应铺满内容区宽", UiWindow.PHONE_WIDTH, w);
        for (int i = 0; i < 8; i++) {
            Component c = bar.getComponent(i);
            int left = Math.round((float) w * i / 8);
            int right = Math.round((float) w * (i + 1) / 8);
            assertEquals("第 " + i + " 格左缘", left, c.getX());
            assertEquals("第 " + i + " 格宽", right - left, c.getWidth());
            assertEquals("第 " + i + " 格高", 48, c.getHeight());
        }
    }

    @Test
    public void testBottomBarTabsCarryOriginalDrawablesAndNineSpText() {
        String[] labels = {"撤销", "重做", "剪切", "复制", "粘贴", "变换", "保存", "更多"};
        JPanel bar = bottomBar();
        for (int i = 0; i < labels.length; i++) {
            JToggleButton b = (JToggleButton) bar.getComponent(i);
            assertEquals("第 " + i + " 格文字", labels[i], b.getText());
            assertNotNull(labels[i] + " 应有 drawableTop 图标", b.getIcon());
            assertEquals(labels[i] + " 图标 = 原版 drawable 32×32 mdpi → 32dp",
                    HoloTabBar.ICON_SIZE, b.getIcon().getIconWidth());
            assertEquals(labels[i] + " 文字 9dip", TAB_TEXT_SIZE, b.getFont().getSize());
        }
    }

    /**
     * 原版禁用项：图标**不灰**（{@code drawableTop} 的 PNG 没有 state list），只有文字变
     * {@code #80ffffff}。PC 自绘必须照做 —— 不能走 FlatLaf 那套「图标文字一起调灰」。
     */
    @Test
    public void testDisabledTabKeepsBrightIconAndHalfWhiteText() {
        JPanel bar = bottomBar();
        JToggleButton undo = (JToggleButton) bar.getComponent(0);
        assertTrue("进入编辑器时「撤销」是禁用的", !undo.isEnabled());

        BufferedImage img = new BufferedImage(bar.getWidth(), 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        bar.paint(g);
        g.dispose();

        // 图标区（原版 32dp 图标、上边距 2dp）：禁用态的图标峰值仍应亮
        // （原版截图实测禁用项图标峰值 (233,237,240)，与启用项同级）
        int iconMax = 0;
        for (int y = 2; y < 34; y++) {
            for (int x = undo.getX(); x < undo.getX() + undo.getWidth(); x++) {
                iconMax = Math.max(iconMax, img.getRGB(x, y) & 0xFF);
            }
        }
        assertTrue("禁用项的图标不该被调灰（实测最亮通道 " + iconMax + "）", iconMax > 200);

        // 文字区（图标下方）：禁用文字 = #80ffffff 叠在 #778899 上 ≈ (187,196,204)。
        // 只统计字形像素 —— 底栏底色的 B 通道正好是 153，用 >160 把它排除掉。
        int textMax = 0;
        for (int y = 34; y < 48; y++) {
            for (int x = undo.getX(); x < undo.getX() + undo.getWidth(); x++) {
                int v = img.getRGB(x, y) & 0xFF;
                if (v > 160) textMax = Math.max(textMax, v);
            }
        }
        assertTrue("应能找到禁用文字像素", textMax > 0);
        assertTrue("禁用文字应比纯白暗（实测峰值 " + textMax + "）", textMax < 235);
        assertTrue("禁用文字不该是 FlatLaf 的 #999999（实测峰值 " + textMax + "）", textMax > 175);
    }

    /** 反证：启用项的图标与文字都是亮的。 */
    @Test
    public void testEnabledTabHasWhiteText() {
        JPanel bar = bottomBar();
        JToggleButton more = (JToggleButton) bar.getComponent(7);
        assertTrue("「更多」是启用的", more.isEnabled());

        BufferedImage img = new BufferedImage(bar.getWidth(), 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        bar.paint(g);
        g.dispose();

        int textMax = 0;
        for (int y = 34; y < 48; y++) {
            for (int x = more.getX(); x < more.getX() + more.getWidth(); x++) {
                int v = img.getRGB(x, y) & 0xFF;
                if (v > 160) textMax = Math.max(textMax, v);
            }
        }
        assertEquals("启用项文字是纯白", 255, textMax);
    }

    /** 原版：{@code bt_Paste.setEnabled(!myMaps.loadClipper().equals(""))}。 */
    @Test
    public void testPasteEnabledFollowsClipper() {
        boolean clipperHasText = !myMaps.loadClipper().equals("");
        assertEquals("「粘贴」的可用态应跟随剪切板内容", clipperHasText, win.bt_Paste.isEnabled());
    }

    /** 原版「变换」「更多」常驻可用。 */
    @Test
    public void testTransformAndMoreAreAlwaysEnabled() {
        assertTrue("「变换」常驻可用", win.bt_Tru.isEnabled());
        assertTrue("「更多」常驻可用", win.bt_More.isEnabled());
    }

    // ---------------------------------------------------------------- 整屏渲染

    /** 顶栏 30px 高、底栏 48px 高，中间是地图；底栏底色必须是 #778899。 */
    @Test
    public void testRenderedBandsMatchOriginal() {
        BufferedImage img = new BufferedImage(UiWindow.PHONE_WIDTH, UiWindow.PHONE_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        win.getContentPane().paint(g);
        g.dispose();

        int barBg = HoloTabBar.BG.getRGB() & 0xFFFFFF;
        // x=29 / x=147 落在顶栏 5 个素材槽之间，露出 RadioGroup 底色；
        // x=2 落在底栏第一格图标的左侧。
        assertEquals("顶栏底色 #778899（y=0）", barBg, img.getRGB(29, 0) & 0xFFFFFF);
        assertEquals("顶栏底色 #778899（y=29）", barBg, img.getRGB(29, 29) & 0xFFFFFF);
        assertEquals("y=30 起是地图的黑色背景", 0x000000, img.getRGB(5, 30) & 0xFFFFFF);
        assertEquals("底栏底色 #778899（y=732）", barBg, img.getRGB(2, 732) & 0xFFFFFF);
        assertEquals("底栏底色 #778899（y=779）", barBg, img.getRGB(2, 779) & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertRect(String what, Rect r, int left, int top, int right, int bottom) {
        assertEquals(what + " left", left, r.left);
        assertEquals(what + " top", top, r.top);
        assertEquals(what + " right", right, r.right);
        assertEquals(what + " bottom", bottom, r.bottom);
    }

    private JPanel bottomBar() {
        Component south = ((BorderLayout) win.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.SOUTH);
        return (JPanel) south;
    }

    /** 排空 EDT —— {@code MyToast.showToast} 在非 EDT 线程上是 {@code invokeLater}。 */
    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // ignore
        }
    }
}
