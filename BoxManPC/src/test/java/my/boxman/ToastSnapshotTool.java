package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 开发辅助：把「主界面 + 提示条」渲染成 PNG，用于肉眼确认 Toast 的观感与落点。
 *
 * <p><b>不要用 {@link Robot} 截真实屏幕</b>——那会把用户桌面上的其它窗口（邮件、聊天记录…）
 * 一起拍进产物里，既泄漏隐私又经常被别的窗口挡住看不到本体。
 * 这里改成<b>离屏合成</b>：主窗口内容区照常 {@code paint()}，
 * 提示条单独渲染，再按 {@link MyToast#computeToastLocation} 算出的坐标贴上去。
 * 坐标算法与真机共用同一份代码，所以位置是可信的。
 *
 * <p>产物：{@code BoxManPC/build/ui-snapshot/toast.png}
 */
public class ToastSnapshotTool {

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
    public void renderToastOverMainWindow() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        BoxManPC app = new BoxManPC();
        app.addNotify();
        app.validate();

        Container pane = app.getContentPane();
        int w = pane.getWidth();
        int h = pane.getHeight();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pane.paint(g);

        // 提示条：与真机同一个组件、同一个坐标算法
        String message = "关卡已保存！";
        JComponent toast = MyToast.createToastLabel(message);
        Dimension ts = toast.getSize();
        Point at = MyToast.computeToastLocation(new Rectangle(0, 0, w, h), ts);
        g.translate(at.x, at.y);
        toast.paint(g);
        g.translate(-at.x, -at.y);
        g.dispose();

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();
        File out = new File(outDir, "toast.png");
        ImageIO.write(img, "png", out);
        System.out.println("[ToastSnapshot] " + out.getAbsolutePath() + " " + w + "x" + h
                + " toast=" + ts.width + "x" + ts.height + " @" + at.x + "," + at.y);

        app.dispose();
    }
}
