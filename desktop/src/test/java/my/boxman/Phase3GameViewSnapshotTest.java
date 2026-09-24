package my.boxman;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 开发辅助：把「游戏界面」({@code myGameView}) 渲染成 PNG，用于与安卓原版截图逐像素比对。
 *
 * <p>原版参考截图 {@code screenshot_20260921_224332_my.boxman.jpg} 对应
 * BoxWorld 组第 3 关（Boxworld 3，信息栏显示「关卡 3」「游标 C4」），
 * 因此这里固定加载同一关，渲染尺寸取 PC 手机竖屏内容区 370×780。
 *
 * <p>产物：{@code desktop/build/ui-snapshot/phase3-game.png}
 */
public class Phase3GameViewSnapshotTest {

    private static final int WIDTH = 370;
    private static final int HEIGHT = 780;
    /** 原版截图：BoxWorld 组的关卡集 id */
    private static final long BOXWORLD_SET_ID = 1;
    /** 原版截图：列表中的第 3 关（0 基下标 2） */
    private static final int BOXWORLD_3_INDEX = 2;

    /** 舞台背景灰：原版与 PC 一致，都是 {@code (127,127,127)} */
    private static final int ARENA_GREY = 127;
    /** HiDPI 设备缩放：Windows 150% 时 Swing 给组件 Graphics2D 叠加的变换 */
    private static final float DEVICE_SCALE = 1.5f;
    /** 底栏高度（dp），量舞台包围盒时要跳过 */
    private static final int BOTTOM_BAR = 48;

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = WIDTH;
        myMaps.m_nWinHeight = HEIGHT;
        new File(myMaps.sRoot).mkdirs();
        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();
        myMaps.loadSkins();
        mySQLite.m_SQL.get_Levels(BOXWORLD_SET_ID);
    }

    @Test
    public void testGameViewChromeMatchesOriginal() throws Exception {
        Assert.assertNotNull("BoxWorld 关卡集应能加载", myMaps.m_lstMaps);
        Assert.assertTrue("BoxWorld 至少有 3 关", myMaps.m_lstMaps.size() > BOXWORLD_3_INDEX);

        myMaps.curMap = myMaps.m_lstMaps.get(BOXWORLD_3_INDEX);
        myMaps.m_nTrun = myMaps.curMap.Trun;

        myGameView game = new myGameView();

        // 原版 myGameView 是 FEATURE_NO_TITLE + FLAG_FULLSCREEN，没有 ActionBar、也没有菜单栏
        Assert.assertNull("myGameView 不应有 JMenuBar（原版无菜单栏）", game.getJMenuBar());

        render(game, "phase3-game.png");

        // 舞台几何：原版 1260×2705 内容区里，关卡占地图区高度的 36.1%、水平铺满并被裁切。
        // PC 370×780（地图区 370×700）下应得 scale ≈ 0.74、关卡 ≈ 370×259 且垂直居中。
        Assert.assertEquals("信息栏高度应为 32dp", 32, game.mMap.m_nArenaTop);
        Assert.assertEquals("舞台缩放应为 0.74", 0.74, game.mMap.m_fScale, 0.01);

        game.myStop();
        game.dispose();
    }

    /**
     * 舞台几何在「真实屏幕」上必须与离屏渲染一致。
     *
     * <p>回归：HiDPI 屏幕（Windows 150%）上 Swing 会给组件的 {@code Graphics2D} 叠加
     * 1.5 的设备变换，而 {@code Canvas.setMatrix()} 曾经直接 {@code setTransform()}
     * 把它整个覆盖掉，导致真实屏幕上舞台只有 1/1.5 大、且不再垂直居中。
     * 离屏渲染走的是 BufferedImage（基础矩阵为单位阵），所以这个 bug 在快照里永远看不出来 ——
     * 这个用例因此特意按设备缩放再渲染一遍。
     */
    @Test
    public void testArenaGeometryInvariantUnderDeviceScale() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        myMaps.curMap = myMaps.m_lstMaps.get(BOXWORLD_3_INDEX);
        myMaps.m_nTrun = myMaps.curMap.Trun;

        myGameView game = new myGameView();
        Rectangle logical = greyBounds(paint(game, 1f), 1f);
        Rectangle device = greyBounds(paint(game, DEVICE_SCALE), DEVICE_SCALE);
        game.myStop();
        game.dispose();

        System.out.println("[Phase3] arena logical=" + logical + "  device÷" + DEVICE_SCALE + "=" + device);

        Assert.assertEquals("设备缩放下舞台宽度应一致", logical.width, device.width, 2);
        Assert.assertEquals("设备缩放下舞台高度应一致", logical.height, device.height, 2);
        Assert.assertEquals("设备缩放下舞台顶边应一致", logical.y, device.y, 2);
    }

    /** 离屏渲染：必须 addNotify() → validate() → 递归 doLayout()，否则子组件尺寸仍是 0。 */
    static void render(JFrame frame, String fileName) throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        BufferedImage img = paint(frame, 1f);

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();
        File out = new File(outDir, fileName);
        ImageIO.write(img, "png", out);
        System.out.println("[Phase3] " + out.getAbsolutePath() + " " + img.getWidth() + "x" + img.getHeight());
    }

    /**
     * 把内容区按给定的设备缩放画进一张图。
     *
     * @param deviceScale {@code 1} 表示离屏（Graphics2D 是单位阵），{@code 1.5} 表示 HiDPI 真实屏幕
     */
    private static BufferedImage paint(JFrame frame, float deviceScale) {
        frame.addNotify();
        frame.validate();
        forceLayout(frame.getContentPane());

        Container pane = frame.getContentPane();
        BufferedImage img = new BufferedImage(
                Math.round(pane.getWidth() * deviceScale),
                Math.round(pane.getHeight() * deviceScale),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(deviceScale, deviceScale);  // 复现真实屏幕上的设备变换
        pane.paint(g);
        g.dispose();
        return img;
    }

    /**
     * 量出舞台背景灰的包围盒，再换算回逻辑坐标。
     *
     * <p>从 110dp 起扫：上面是信息栏（32dp）与时钟（白字黑描边，抗锯齿边缘也会产生
     * {@code (127,127,127)}，不排除会把包围盒顶边拉到时钟上）。
     */
    private static Rectangle greyBounds(BufferedImage img, float deviceScale) {
        int top = Math.round(110 * deviceScale);
        int bottom = img.getHeight() - Math.round(BOTTOM_BAR * deviceScale);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = top; y < bottom; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (Math.abs(r - ARENA_GREY) <= 8 && Math.abs(g - ARENA_GREY) <= 8 && Math.abs(b - ARENA_GREY) <= 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        Assert.assertTrue("应能在舞台区找到关卡灰色背景", maxX > minX && maxY > minY);
        return new Rectangle(Math.round(minX / deviceScale), Math.round(minY / deviceScale),
                Math.round((maxX - minX + 1) / deviceScale), Math.round((maxY - minY + 1) / deviceScale));
    }

    /**
     * 离屏渲染的关键：{@code revalidate()} 是延迟执行的，在非 EDT 上永远不会真正跑布局，
     * 因此这里递归调用 {@code doLayout()} 把每一层都强制排版一次。
     */
    static void forceLayout(Container c) {
        c.doLayout();
        for (Component ch : c.getComponents()) {
            if (ch instanceof Container) forceLayout((Container) ch);
        }
    }
}
