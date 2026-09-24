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

/**
 * 开发辅助：把 {@code myGameView} 渲染成一张「有内容的」游戏截图。
 *
 * <p>为什么单独一个工具：{@link WindowSnapshotTool} 里的 {@code myGameView} 用的是
 * {@code myMaps.curMap} 的默认值（往往是第一个关卡，只有 1 个箱子），
 * 拿来当 README 的配图太寒酸。这里显式挑一个中等复杂度的关卡再渲染。
 *
 * <p>产物：{@code build/ui-snapshot/game-hero.png}（370×780 内容区）。
 */
public class GameViewSnapshotTool {

    /** 默认挑第一关集里第 9 关（Boxworld 9，9×11、6 个箱子）——尺寸适中，看得清箱子/目标/墙。 */
    private static final int DEFAULT_LEVEL_INDEX = 8;

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();
        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();
        myMaps.loadSkins();
    }

    @Test
    public void renderGameViewWithSelectedLevel() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        int index = Integer.getInteger("gameShot.levelIndex", DEFAULT_LEVEL_INDEX);

        java.util.ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assume.assumeFalse("关卡库为空", sets.isEmpty());
        mySQLite.m_SQL.get_Levels(sets.get(0).id);
        Assume.assumeTrue("关卡数不足 " + (index + 1), myMaps.m_lstMaps.size() > index);

        myMaps.curMap = myMaps.m_lstMaps.get(index);
        System.out.println("[GameViewSnapshot] 关卡 = " + myMaps.curMap.Title
                + " (" + myMaps.curMap.Rows + "x" + myMaps.curMap.Cols + ")");

        myGameView view = new myGameView();
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
            File out = new File(outDir, "game-hero.png");
            ImageIO.write(img, "png", out);
            System.out.println("[GameViewSnapshot] " + out.getAbsolutePath()
                    + " " + img.getWidth() + "x" + img.getHeight());
        } finally {
            view.dispose();
        }
    }
}
