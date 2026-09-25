package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发辅助：把所有顶层窗口各渲染成 PNG，并拼成一张「总览图」，用于人工检查
 * 各窗口在手机竖屏尺寸（370×780）下有没有被压扁、裁切。
 *
 * <p>产物：{@code build/ui-snapshot/} 下的各窗口 PNG，以及 {@code windows-overview.png}。
 */
public class WindowSnapshotTool {

    private static final int W = my.boxman.compat.UiWindow.PHONE_WIDTH;
    private static final int H = my.boxman.compat.UiWindow.PHONE_HEIGHT;
    private static final int SCALE = 40;   // 总览图缩放百分比

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
    public void renderAllWindows() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        long setId = sets.get(0).id;
        String setTitle = sets.get(0).title;
        mySQLite.m_SQL.get_Levels(setId);
        mapNode firstLevel = myMaps.m_lstMaps.get(0);

        Map<String, Window> windows = new LinkedHashMap<>();
        windows.put("01-BoxManPC", new BoxManPC());
        windows.put("02-myGameView", new myGameView());
        windows.put("03-myGridView", new myGridView(setId, setTitle));
        windows.put("04-myPicListView", new myPicListView());
        windows.put("05-myEditView", new myEditView());
        windows.put("06-myFindView", new myFindView());
        windows.put("07-myRecogView", new myRecogView());
        windows.put("08-myStateBrow", new myStateBrow());
        windows.put("09-mySolutionBrow", new mySolutionBrow(null));
        windows.put("10-myActGMView", new myActGMView(null, false));
        windows.put("11-Help", new Help(0));
        windows.put("12-myAbout", new myAbout(null));
        windows.put("13-myAbout1", new myAbout1(null, setId, "10/20"));
        windows.put("14-myAbout2", new myAbout2(null, firstLevel));
        windows.put("15-mySubmitList", new mySubmitList(false));
        windows.put("16-mySubmit", new mySubmit());
        windows.put("17-myExport", new myExport());

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();

        int sw = W * SCALE / 100;
        int sh = H * SCALE / 100;
        int cols = 5;
        int rows = (windows.size() + cols - 1) / cols;
        int pad = 8;
        int head = 20;
        BufferedImage sheet = new BufferedImage(
                cols * (sw + pad) + pad, rows * (sh + pad + head) + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(new java.awt.Color(0x20, 0x20, 0x20));
        sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        sg.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 12));
        sg.setColor(java.awt.Color.WHITE);

        int i = 0;
        for (Map.Entry<String, Window> e : windows.entrySet()) {
            Window win = e.getValue();
            try {
                BufferedImage img = render(win);
                ImageIO.write(img, "png", new File(outDir, e.getKey() + ".png"));

                int cx = pad + (i % cols) * (sw + pad);
                int cy = pad + (i / cols) * (sh + pad + head);
                sg.drawString(e.getKey(), cx, cy + 14);
                sg.drawImage(img, cx, cy + head, sw, sh, null);
                sg.setColor(new java.awt.Color(0x60, 0x60, 0x60));
                sg.drawRect(cx, cy + head, sw - 1, sh - 1);
                sg.setColor(java.awt.Color.WHITE);
                System.out.println("[WindowSnapshot] " + e.getKey() + " " + img.getWidth() + "x" + img.getHeight());
            } catch (Throwable t) {
                System.out.println("[WindowSnapshot] " + e.getKey() + " 渲染失败: " + t);
            } finally {
                win.dispose();
            }
            i++;
        }
        sg.dispose();

        File sheetFile = new File(outDir, "windows-overview.png");
        ImageIO.write(sheet, "png", sheetFile);
        System.out.println("[WindowSnapshot] 总览: " + sheetFile.getAbsolutePath()
                + " " + sheet.getWidth() + "x" + sheet.getHeight());
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
