package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 开发辅助：把主界面渲染成 PNG，用于与安卓原版截图逐像素比对。
 * 不指定窗口尺寸，直接用 BoxManPC 自身的手机竖屏默认尺寸，
 * 这样渲染结果与「原版截图 ÷ density」得到的 dp 画布一一对应。
 * 产物：desktop/build/ui-snapshot/main-window.png
 */
public class UiSnapshotTool {

    private static final int WIDTH = 370;
    private static final int HEIGHT = 780;

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
    }

    @Test
    public void renderMainWindow() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        BoxManPC app = new BoxManPC();
        app.addNotify();
        app.validate();

        Container pane = app.getContentPane();
        BufferedImage img = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pane.paint(g);
        g.dispose();

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();
        File out = new File(outDir, "main-window.png");
        ImageIO.write(img, "png", out);
        System.out.println("[UiSnapshot] " + out.getAbsolutePath() + " " + img.getWidth() + "x" + img.getHeight());

        app.dispose();
    }
}
