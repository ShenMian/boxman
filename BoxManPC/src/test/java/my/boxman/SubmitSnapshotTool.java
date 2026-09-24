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
 * 开发辅助：把「比赛答案提交列表」窗口渲染成 PNG，用于与原版截图逐像素比对。
 *
 * <p>数据走真实接口（{@code myMaps.uil + api/competition/submission/}），
 * 但**不经过窗口的 {@code reload()}**（那会弹模态进度框），而是直接取回 JSON 再灌进去，
 * 这样离屏渲染是确定的。
 *
 * <p>产物：{@code build/ui-snapshot/submit-list-live.png}
 * （刻意不叫 {@code 15-mySubmitList.png}——那是 {@link WindowSnapshotTool} 的总览图产物，
 * 那边渲染的是没数据的空窗口，两者会互相覆盖）
 */
public class SubmitSnapshotTool {

    private static final int W = my.boxman.compat.UiWindow.PHONE_WIDTH;
    private static final int H = my.boxman.compat.UiWindow.PHONE_HEIGHT;
    private static final String[] QUERY = {"", "?t=extra", "?t=extra2", "?t=extra3"};

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = W;
        myMaps.m_nWinHeight = H;
        new File(myMaps.sRoot).mkdirs();
        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();
        myMaps.loadSkins();
    }

    @Test
    public void renderSubmitList() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        String[] jsons = new String[4];
        for (int i = 0; i < 4; i++) {
            mySubmitList.HttpResult r = mySubmitList.httpGet(myMaps.uil + mySubmitList.URL + QUERY[i]);
            System.out.println("[SubmitSnapshot] GET " + QUERY[i] + " -> " + r.code
                    + "  " + r.body.length() + " bytes");
            Assume.assumeTrue("接口不可用，跳过离屏比对", r.code == 200);
            jsons[i] = r.body;
        }

        mySubmitList win = new mySubmitList(false);
        try {
            for (int i = 0; i < 4; i++) {
                win.myJson(jsons[i], i);
            }
            mySubmitList.Msg msg = new mySubmitList.Msg();
            msg.what = 1;
            win.applyResult(msg);

            win.addNotify();
            win.validate();
            Container pane = win.getContentPane();
            pane.validate();
            System.out.println("[SubmitSnapshot] 内容区 " + pane.getWidth() + "x" + pane.getHeight()
                    + "  树行数=" + win.getTree().getRowCount());

            BufferedImage img = new BufferedImage(pane.getWidth(), pane.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pane.paint(g);
            g.dispose();

            File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
            outDir.mkdirs();
            File out = new File(outDir, "submit-list-live.png");
            ImageIO.write(img, "png", out);
            System.out.println("[SubmitSnapshot] " + out.getAbsolutePath()
                    + "  " + img.getWidth() + "x" + img.getHeight());
        } finally {
            win.dispose();
        }
    }
}
