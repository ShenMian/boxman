package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发辅助：把所有对话框各渲染成 PNG，并拼成一张总览图，用于检查它们是否还在用 PC 尺寸
 * （原版是 Holo AlertDialog，竖屏下窗口宽度至少是屏幕的 95%）。
 *
 * <p>产物：{@code build/ui-snapshot/dialogs-overview.png}
 */
public class DialogSnapshotTool {

    private static final int W = my.boxman.compat.UiWindow.PHONE_WIDTH;
    private static final int H = my.boxman.compat.UiWindow.PHONE_HEIGHT;

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
        // 「关卡查询」对话框要列出全部关卡集，快照里也得先装填
        myMaps.mSets0 = mySQLite.m_SQL.get_GroupList(0);
        myMaps.mSets1 = mySQLite.m_SQL.get_GroupList(1);
        myMaps.mSets2 = mySQLite.m_SQL.get_GroupList(2);
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);
        mySQLite.m_SQL.get_Levels(1);
        if (myMaps.m_lstMaps != null && !myMaps.m_lstMaps.isEmpty()) {
            myMaps.curMap = myMaps.m_lstMaps.get(0);
        }
    }

    @Test
    public void renderAllDialogs() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        Map<String, JDialog> dialogs = new LinkedHashMap<>();
        dialogs.put("d01-Goto", new GotoDialog(null, 5, 100, i -> {}));
        dialogs.put("d02-Del", new DelDialog(null, "测试删除关卡", b -> {}));
        dialogs.put("d03-NewLevel", new NewLevelDialog(null, (t, a, r, c) -> {}));
        dialogs.put("d04-Rule", new RuleDialog(null, (c, m) -> {}));
        dialogs.put("d05-Color", new ColorDialog(null, Color.BLUE, c -> {}));
        dialogs.put("d06-Find", new FindDialog(null, (list, sim, ignoreBox) -> {}));
        dialogs.put("d07-Export", new ExportDialog(null, s -> {}));
        dialogs.put("d08-UrlInput", new UrlInputDialog(null, s -> {}));
        dialogs.put("d09-Query", new QueryDialog(null, r -> {}));
        dialogs.put("d10-Split", new SplitDialog(null, 0, java.util.Collections.emptyList(), s -> {}));
        dialogs.put("d11-GifMake", new myGifMakeDialog(null, "", 0, new boolean[8], new short[4]));

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();

        int sw = 200, sh = (int) (H * (sw / (float) W));
        int cols = 6, pad = 8, head = 20;
        int rows = (dialogs.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * (sw + pad) + pad, rows * (sh + pad + head) + pad,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(new Color(0x20, 0x20, 0x20));
        sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        sg.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        sg.setColor(Color.WHITE);

        int i = 0;
        for (Map.Entry<String, JDialog> e : dialogs.entrySet()) {
            JDialog d = e.getValue();
            try {
                BufferedImage img = render(d);
                ImageIO.write(img, "png", new File(outDir, e.getKey() + ".png"));
                int cx = pad + (i % cols) * (sw + pad);
                int cy = pad + (i / cols) * (sh + pad + head);
                String info = e.getKey() + "  " + d.getWidth() + "x" + d.getHeight()
                        + (d.getWidth() > W || d.getHeight() > H ? "  ⚠超屏" : "");
                sg.drawString(info, cx, cy + 14);
                sg.drawImage(img, cx, cy + head, sw, sh, null);
                sg.setColor(new Color(0x60, 0x60, 0x60));
                sg.drawRect(cx, cy + head, sw - 1, sh - 1);
                sg.setColor(Color.WHITE);
                System.out.println("[DialogSnapshot] " + info);
            } catch (Throwable t) {
                System.out.println("[DialogSnapshot] " + e.getKey() + " 渲染失败: " + t);
            } finally {
                d.dispose();
            }
            i++;
        }
        sg.dispose();
        File sheetFile = new File(outDir, "dialogs-overview.png");
        ImageIO.write(sheet, "png", sheetFile);
        System.out.println("[DialogSnapshot] 总览: " + sheetFile.getAbsolutePath()
                + " " + sheet.getWidth() + "x" + sheet.getHeight());
    }

    private static BufferedImage render(JDialog d) {
        if (d instanceof my.boxman.compat.HoloAlertDialog) {
            ((my.boxman.compat.HoloAlertDialog) d).applyHoloSize();
        }
        d.addNotify();
        d.validate();
        Container pane = d.getContentPane();
        forceLayout(pane);

        // 画在 370×780 的手机画布中央，底色近似 Activity 被 dim 之后的观感，
        // 这样面板外的 9dp 软投影才看得见，也能一眼看出是否超屏。
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x1A, 0x1A, 0x1A));
        g.fillRect(0, 0, W, H);
        int dw = Math.max(1, pane.getWidth());
        int dh = Math.max(1, pane.getHeight());
        g.translate((W - dw) / 2, (H - dh) / 2);
        pane.paint(g);
        g.dispose();
        return img;
    }

    /** 离屏渲染必须自上而下跑一遍 doLayout()：非 EDT 上 revalidate() 的延迟校验不会发生。 */
    private static void forceLayout(Container c) {
        c.doLayout();
        for (Component ch : c.getComponents()) {
            if (ch instanceof Container) forceLayout((Container) ch);
        }
    }
}
