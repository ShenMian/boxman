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
import java.util.ArrayList;

/**
 * 第二阶段（关卡网格界面 myGridView）与原版截图的还原度自检。
 *
 * 输出：
 *   build/ui-snapshot/phase2-grid.png        当前实现渲染图（1dp = 1px，370x780 内容区）
 *   build/ui-snapshot/phase2-grid-bands.txt  关键色带/尺寸实测值
 */
public class Phase2GridTest {

    private static boolean ready;

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        if (myMaps.sRoot == null) {
            myMaps.sRoot = System.getProperty("user.home") + "/.boxman";
        }
        if (mySQLite.m_SQL == null) {
            mySQLite.m_SQL = mySQLite.getInstance();
            mySQLite.m_SQL.openDataBase();
        }
        myMaps.loadSkins();
        ready = mySQLite.m_SQL != null;
    }

    private static BufferedImage render(Window win) {
        Container pane = ((JFrame) win).getContentPane();
        win.addNotify();
        win.validate();
        pane.validate();
        forceLayout(pane);
        BufferedImage img = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        pane.paint(g);
        g.dispose();
        return img;
    }

    /**
     * 离屏渲染不经过 EDT/RepaintManager，revalidate() 的延迟校验不会发生，
     * 因此这里自上而下强制跑一遍 doLayout，保证快照反映真实布局。
     */
    private static void forceLayout(Container c) {
        c.doLayout();
        for (Component ch : c.getComponents()) {
            if (ch instanceof Container) forceLayout((Container) ch);
        }
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Test
    public void testGridLayoutMatchesOriginal() throws Exception {
        Assume.assumeTrue("需要本地关卡库", ready);

        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assert.assertFalse("应至少有一个关卡集", sets.isEmpty());
        long setId = sets.get(0).id;
        String title = sets.get(0).title;

        myMaps.m_Sets[2] = 0;   // 网格模式（原版默认）
        myMaps.m_Sets[33] = 0;  // 自动列数
        myMaps.isSelect = false;

        myGridView grid = new myGridView(setId, title);
        grid.setVisible(false);
        myMaps.m_Sets[2] = 0;
        grid.revalidate();
        Assert.assertTrue("缩略图应能在超时前加载完成", grid.awaitThumbnails(30000));
        BufferedImage img = render(grid);

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();
        ImageIO.write(img, "png", new File(outDir, "phase2-grid.png"));

        StringBuilder sb = new StringBuilder();
        sb.append("渲染尺寸: ").append(img.getWidth()).append('x').append(img.getHeight()).append('\n');

        // ---- ActionBar 色带
        int barBottom = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            Color c = new Color(img.getRGB(4, y));
            if (c.getRed() == 0x00 && c.getGreen() == 0x83 && c.getBlue() == 0xC5) barBottom = y;
        }
        sb.append("ActionBar 底部 y = ").append(barBottom).append("（原版 48dp）\n");
        sb.append("ActionBar 底色 = ").append(hex(new Color(img.getRGB(4, barBottom / 2)))).append('\n');

        // ---- 标题条
        int headerTop = barBottom + 1;
        Color headerColor = new Color(img.getRGB(4, headerTop + 5));
        int headerBottom = headerTop;
        for (int y = headerTop; y < img.getHeight(); y++) {
            if (new Color(img.getRGB(4, y)).equals(headerColor)) headerBottom = y;
            else break;
        }
        sb.append("标题条 y = ").append(headerTop).append("..").append(headerBottom)
                .append("  高 = ").append(headerBottom - headerTop + 1).append("（原版 89px≈26dp）\n");
        sb.append("标题条底色 = ").append(hex(headerColor)).append("（原版 #555555）\n");

        // ---- 网格几何
        int gridTop = headerBottom + 1;
        sb.append("网格起始 y = ").append(gridTop).append('\n');
        sb.append("列数 = ").append(grid.getColumnCount()).append("（原版 6）\n");
        sb.append("列宽 = ").append(grid.getCellWidth()).append("（原版 205px/3.4 ≈ 60dp）\n");
        sb.append("卡片数 = ").append(grid.getCardCount()).append('\n');

        // ---- 单元格：文字条背景色（第 1 行）
        // 找到第一行编号文字条的 y
        int textBandY = -1;
        for (int y = gridTop; y < img.getHeight(); y++) {
            Color c = new Color(img.getRGB(4, y));
            if (c.getRed() == 0x15 && c.getGreen() == 0x15 && c.getBlue() == 0x15
                    || (c.getGreen() > 0x30 && c.getRed() < 0x20 && c.getBlue() < 0x20)) {
                textBandY = y;
                break;
            }
        }
        sb.append("首个编号条 y = ").append(textBandY).append('\n');
        if (textBandY > 0) {
            int bandBottom = textBandY;
            Color bandColor = new Color(img.getRGB(4, textBandY));
            for (int y = textBandY; y < img.getHeight(); y++) {
                if (new Color(img.getRGB(4, y)).equals(bandColor)) bandBottom = y;
                else break;
            }
            sb.append("编号条高 = ").append(bandBottom - textBandY + 1).append("（原版 62px≈18dp）\n");
            sb.append("编号条底色 = ").append(hex(bandColor)).append("（原版 #151515 / #004700）\n");
        }

        // ---- 水平列边界（扫描编号条一行）
        if (textBandY > 0) {
            int y = textBandY + 3;
            sb.append("第 ").append(y).append(" 行水平色带：\n");
            int prev = -1;
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (rgb != prev) {
                    sb.append("   x=").append(x).append(" -> ").append(hex(new Color(rgb))).append('\n');
                    prev = rgb;
                }
            }
        }

        File bandFile = new File(outDir, "phase2-grid-bands.txt");
        java.nio.file.Files.write(bandFile.toPath(), sb.toString().getBytes("UTF-8"));
        System.out.println(sb);
        System.out.println("[Phase2Grid] 渲染图: " + new File(outDir, "phase2-grid.png").getAbsolutePath());

        Assert.assertTrue("网格应为 6 列", grid.getColumnCount() == 6);
        grid.dispose();
    }

    @Test
    public void testListModeRenders() throws Exception {
        Assume.assumeTrue("需要本地关卡库", ready);

        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        long setId = sets.get(0).id;
        String title = sets.get(0).title;

        myMaps.m_Sets[2] = 1;   // 显示标题（ListView 模式）
        myMaps.m_Sets[33] = 0;
        myMaps.isSelect = false;

        myGridView grid = new myGridView(setId, title);
        grid.setVisible(false);
        myMaps.m_Sets[2] = 1;
        grid.revalidate();
        Assert.assertTrue(grid.awaitThumbnails(30000));
        BufferedImage img = render(grid);

        File outDir = new File(System.getProperty("user.dir"), "build/ui-snapshot");
        outDir.mkdirs();
        ImageIO.write(img, "png", new File(outDir, "phase2-list.png"));

        Assert.assertEquals("列表模式应只有 1 列", 1, grid.getColumnCount());
        Assert.assertTrue("列表模式应有关卡卡片", grid.getCardCount() > 0);
        System.out.println("[Phase2Grid] 列表模式渲染图: "
                + new File(outDir, "phase2-list.png").getAbsolutePath());
        grid.dispose();
    }

    @Test
    public void testSelectModeTogglesSelectAll() {
        Assume.assumeTrue("需要本地关卡库", ready);
        myMaps.m_Sets[2] = 0;
        myMaps.m_Sets[33] = 0;
        myMaps.isSelect = false;

        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        myGridView grid = new myGridView(sets.get(0).id, sets.get(0).title);
        Assert.assertFalse("默认不显示全选勾选框", grid.isSelectAllVisible());

        grid.setSelectModeForTest(true);
        Assert.assertTrue("多选模式应显示全选勾选框", grid.isSelectAllVisible());
        Assert.assertTrue("多选模式菜单项应勾选", grid.getActionBar().isActionChecked("多选模式"));

        grid.setSelectModeForTest(false);
        Assert.assertFalse("退出多选模式应隐藏全选勾选框", grid.isSelectAllVisible());
        grid.dispose();
    }

    @Test
    public void testHeaderHiddenForSpecialSets() {
        Assume.assumeTrue("需要本地关卡库", ready);
        myMaps.m_Sets[2] = 0;
        myGridView grid = new myGridView(-1, "创编关卡");
        Assert.assertFalse("创编关卡应隐藏关卡集标题条", grid.isHeaderVisible());
        Assert.assertEquals("创编关卡", grid.getActionBar().getBarTitle());
        Assert.assertTrue("创编关卡应显示 ╋ 动作项", grid.getActionBar().isBarActionVisible("╋"));
        Assert.assertTrue("创编关卡应显示 批量删除...", grid.getActionBar().isActionVisible("批量删除..."));
        Assert.assertFalse("创编关卡不应显示 关于", grid.getActionBar().isActionVisible("关于"));
        grid.dispose();

        myMaps.m_Sets[2] = 0;
        myGridView grid2 = new myGridView(0, "BoxWorld");
        Assert.assertTrue("内置关卡组应显示标题条", grid2.isHeaderVisible());
        Assert.assertTrue("内置关卡组应显示 关于", grid2.getActionBar().isActionVisible("关于"));
        Assert.assertFalse("内置关卡组不应显示 批量删除...", grid2.getActionBar().isActionVisible("批量删除..."));
        Assert.assertFalse("内置关卡组不应显示 ╋", grid2.getActionBar().isBarActionVisible("╋"));
        Assert.assertTrue("应显示 顶", grid2.getActionBar().isBarActionVisible("顶"));
        Assert.assertTrue("应显示 底", grid2.getActionBar().isBarActionVisible("底"));
        grid2.dispose();
    }
}
