package my.boxman;

import my.boxman.compat.HoloViewDialog;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 开发辅助：「关卡图像识别」界面的离屏快照。
 *
 * <p>{@code WindowSnapshotTool} 里的 {@code 07-myRecogView} 是在没有底图（{@code myMaps.edPict == null}）
 * 的情况下渲染的，只能看到一片黑 + 底行按钮，看不出四条边线指示灯、有效区域边框、
 * 格线、识别出的 XSB 字符与「关卡尺寸 / 箱子 / 目标点」三行提示。
 *
 * <p>这里合成一张「关卡截图」，跑一遍真实的识别流程（取样格 = 一个墙壁格），
 * 再把结果渲染出来。产物：
 * <ul>
 *   <li>{@code build/ui-snapshot/recog-view.png} —— 370×780 的窗口本体</li>
 *   <li>{@code build/ui-snapshot/recog-dialog.png} —— 「识别设置」（相似度 5~9）对话框</li>
 * </ul>
 */
public class RecogSnapshotTool {

    private static final int PIC = 1200;
    private static final int MARGIN = 50;      // 原版 initArena() 的四边留白
    private static final int COLS = 10;
    private static final int ROWS = 10;
    private static final int CELL = (PIC - MARGIN * 2) / COLS;   // 110

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/recog-home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();
    }

    /** 合成一张「关卡截图」：10×10 格，外圈墙壁、中间几个箱子/目标点/人。 */
    private static BufferedImage fakeLevelShot() {
        BufferedImage img = new BufferedImage(PIC, PIC, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x303030));
        g.fillRect(0, 0, PIC, PIC);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = MARGIN + c * CELL;
                int y = MARGIN + r * CELL;
                boolean wall = r == 0 || c == 0 || r == ROWS - 1 || c == COLS - 1;
                if (wall) {
                    // 墙壁：深灰底 + 浅灰内方块
                    g.setColor(new Color(0x707070));
                    g.fillRect(x, y, CELL, CELL);
                    g.setColor(new Color(0xB0B0B0));
                    g.fillRect(x + 12, y + 12, CELL - 24, CELL - 24);
                } else {
                    // 地板：浅灰底 + 深灰缝
                    g.setColor(new Color(0x505050));
                    g.fillRect(x, y, CELL, CELL);
                    g.setColor(new Color(0xD8D8D8));
                    g.fillRect(x + 2, y + 2, CELL - 4, CELL - 4);
                }
            }
        }

        // 目标点（白心圆）
        drawGoal(g, 3, 4);
        drawGoal(g, 5, 6);
        drawGoal(g, 7, 3);
        // 箱子（橙色方块）
        drawBox(g, 4, 4);
        drawBox(g, 5, 5);
        drawBox(g, 6, 3);
        // 仓管员（绿色圆）
        g.setColor(new Color(0x30A030));
        g.fillOval(MARGIN + 4 * CELL + CELL / 4, MARGIN + 7 * CELL + CELL / 4, CELL / 2, CELL / 2);
        g.dispose();
        return img;
    }

    private static void drawBox(Graphics2D g, int r, int c) {
        int x = MARGIN + c * CELL, y = MARGIN + r * CELL;
        g.setColor(new Color(0xC07020));
        g.fillRect(x + CELL / 6, y + CELL / 6, CELL * 2 / 3, CELL * 2 / 3);
        g.setColor(new Color(0x804000));
        g.drawRect(x + CELL / 6, y + CELL / 6, CELL * 2 / 3, CELL * 2 / 3);
    }

    private static void drawGoal(Graphics2D g, int r, int c) {
        int x = MARGIN + c * CELL, y = MARGIN + r * CELL;
        g.setColor(new Color(0x404040));
        int d = CELL / 3;
        g.fillOval(x + (CELL - d) / 2, y + (CELL - d) / 2, d, d);
    }

    @Test
    public void renderRecogWindows() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        myMaps.edPict = fakeLevelShot();

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();

        myRecogView win = new myRecogView();
        myRecogViewMap m = win.getMap();
        win.addNotify();
        win.validate();

        m.m_nCols = COLS;
        m.m_nRows = ROWS;
        m.setArena();

        // 先离屏画一遍，让 m_fLeft / m_fTop / m_fScale 有正确的值（它们只在 onDraw 后才是对的）
        BufferedImage warm = new BufferedImage(Math.max(1, m.getWidth()),
                Math.max(1, m.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D wg = warm.createGraphics();
        m.paint(wg);
        wg.dispose();

        // 取样格 = 左上角那个墙壁格，走一次真实的单击（会顺手触发 doAction 识别）
        // 注意：setColor(n) 是「再点一次就取消」的语义，所以这里只能调它，不能先手工设 m_nObj
        win.setColor(1);                    // 墙壁
        int cx = (int) ((MARGIN + CELL / 2) * m.getViewScale() + m.getViewLeft());
        int cy = (int) ((MARGIN + CELL / 2) * m.getViewScale() + m.getViewTop());
        m.clickForTest(cx, cy);

        BufferedImage img = render(win);
        ImageIO.write(img, "png", new File(outDir, "recog-view.png"));
        System.out.println("[RecogSnapshot] recog-view.png " + img.getWidth() + "x" + img.getHeight()
                + " 识别出 " + countHashes(win) + " 个墙壁格");
        win.dispose();

        // 「识别设置」对话框
        myRecogView host = new myRecogView();
        HoloViewDialog dlg = new HoloViewDialog(host, "识别设置", host.buildSimilarityContentForTest());
        dlg.addButton("确定", dlg::dispose);
        dlg.applyHoloSize();
        BufferedImage dimg = render(dlg);
        ImageIO.write(dimg, "png", new File(outDir, "recog-dialog.png"));
        System.out.println("[RecogSnapshot] recog-dialog.png " + dimg.getWidth() + "x" + dimg.getHeight());
        dlg.dispose();
        host.dispose();
    }

    private static int countHashes(myRecogView w) {
        int n = 0;
        char[][] a = w.getCellArray();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                if (a[r][c] == '#') n++;
            }
        }
        return n;
    }

    private static BufferedImage render(Window win) {
        Container pane = (win instanceof JFrame) ? ((JFrame) win).getContentPane()
                : ((JDialog) win).getContentPane();
        win.addNotify();
        win.validate();
        pane.validate();
        BufferedImage img = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pane.paint(g);
        g.dispose();
        return img;
    }
}
