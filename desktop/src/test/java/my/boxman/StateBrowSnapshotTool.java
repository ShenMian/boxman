package my.boxman;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发辅助：把 {@code myStateBrow} 渲染成一张「有内容的」截图。
 *
 * <p>为什么单独一个工具：{@link WindowSnapshotTool} 里的 {@code 08-myStateBrow} 是空数据，
 * 两个分组都是 0 个子项，看不出「两行子项（描述 + 时间）」和分组标题上的排序后缀。
 * 这里灌入合成数据再渲染，用于肉眼核对 {@code s_groups.xml} / {@code s_child.xml} 的还原。
 *
 * <p>产物：{@code build/ui-snapshot/state-brow.png}（370×780 内容区）。
 */
public class StateBrowSnapshotTool {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();
    }

    private static state_Node node(long id, int moves, int pushs, String inf, String time) {
        state_Node nd = new state_Node();
        nd.id = id;
        nd.pid = 1;
        nd.pkey = 1;
        nd.moves = moves;
        nd.pushs = pushs;
        nd.inf = inf;
        nd.time = time;
        return nd;
    }

    @Test
    public void renderStateBrowWithSampleData() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        // 构造器只在 curMap != null 时查库；置空以使用下面的合成数据
        myMaps.curMap = null;
        List<state_Node> backup1 = new ArrayList<state_Node>(myMaps.mState1);
        List<state_Node> backup2 = new ArrayList<state_Node>(myMaps.mState2);
        myMaps.mState1.clear();
        myMaps.mState2.clear();

        myMaps.mState1.add(node(1, 87, 23, "移动: 87, 推动: 23", "2026-09-20 21:14:03"));
        myMaps.mState1.add(node(2, 64, 18, "移动: 64, 推动: 18", "2026-09-21 09:02:47"));
        myMaps.mState2.add(node(3, 42, 11, "移动: 42, 推动: 11", "[YASS]2026-09-22 18:30:11"));
        myMaps.mState2.add(node(4, 40, 13, "移动: 40, 推动: 13", "2026-09-23 08:11:52"));
        myMaps.mState2.add(node(5, 38, 10, "移动: 38, 推动: 10", "导入: Boxworld 9"));

        myStateBrow view = new myStateBrow();
        try {
            view.addNotify();
            view.validate();
            Container pane = view.getContentPane();
            pane.validate();

            BufferedImage img = new BufferedImage(pane.getWidth(), pane.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pane.paint(g);
            g.dispose();

            File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
            outDir.mkdirs();
            File out = new File(outDir, "state-brow.png");
            ImageIO.write(img, "png", out);
            System.out.println("[StateBrowSnapshot] " + out.getAbsolutePath()
                    + " " + img.getWidth() + "x" + img.getHeight()
                    + "  rows=" + view.getTree().getRowCount());
        } finally {
            view.dispose();
            myMaps.mState1.clear();
            myMaps.mState1.addAll(backup1);
            myMaps.mState2.clear();
            myMaps.mState2.addAll(backup2);
        }
    }
}
