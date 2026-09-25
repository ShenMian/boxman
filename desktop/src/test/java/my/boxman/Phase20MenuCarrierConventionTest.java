package my.boxman;

import my.boxman.compat.HoloPopupMenu;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 菜单载体约定回归锁（阶段 D-3 收尾）。
 *
 * <p>原版所有菜单都出自同一套 Android framework 样式：
 * ActionBar 溢出菜单、{@code onCreateOptionsMenu} 的菜单、以及
 * {@code onCreateContextMenu} 的上下文菜单 —— 三者渲染出来都是
 * {@code popup_menu_holo_dark}（深灰底 {@code #333333}、行高 40dp、最小宽 200dp）。
 * 因此 PC 侧**任何**弹出菜单都必须走 {@link HoloPopupMenu}，
 * 否则会与 ActionBar 的 ⋮ 菜单样式不一致。
 *
 * <p>本测试用源码扫描把这条约定锁住：以后谁再写 {@code new JMenuItem(...)}
 * 或 {@code new JPopupMenu()} 就会在这里失败。
 *
 * <p>阶段 G ④ 删掉 {@code myGameView.installMapPopupMenu()}（PC 自造的多级右键菜单，
 * 原版 {@code myGameView} 根本没有上下文菜单）之后，这条约定已经<b>没有白名单</b>了。
 */
public class Phase20MenuCarrierConventionTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    private static final String SRC = "src/main/java/my/boxman";
    private static final String COMPAT = SRC + "/compat";

    private static List<File> mainSources;

    @BeforeClass
    public static void setUpClass() {
        mainSources = new ArrayList<>();
        File root = locate("src/main/java/my/boxman");
        assertNotNull("找不到主源码目录，工作目录是 " + new File(".").getAbsolutePath(), root);
        collect(root, mainSources);
        assertTrue("主源码应有几十个文件", mainSources.size() > 30);
    }

    @Test
    public void testNoBareJMenuItemAnywhere() {
        assertEquals("弹出菜单必须走 HoloPopupMenu，全项目不应再有 new JMenuItem(",
                "[]", scan("new JMenuItem(").toString());
    }

    @Test
    public void testNoBareJPopupMenuOutsideCompat() {
        for (String h : scan("new JPopupMenu()")) {
            assertTrue("弹出菜单必须走 HoloPopupMenu.create()，别直接 new JPopupMenu()：" + h,
                    h.startsWith("compat/HoloPopupMenu.java:"));
        }
    }

    @Test
    public void testNoSwingJMenuBarAnywhere() {
        List<String> hits = scan("setJMenuBar(");
        assertEquals("原版没有一处 Swing 菜单栏；JMenuBar 还会从内容区挖走 23px",
                "[]", hits.toString());
    }

    @Test
    public void testNoGreyedOutPlaceholderItems() {
        List<String> hits = scan("myActionBar.NO_OP");
        assertEquals("不应再有置灰占位项（原版没有任何置灰菜单项）", "[]", hits.toString());
    }

    @Test
    public void testSolutionBrowContextMenuMatchesOriginalTitle() {
        mySolutionBrow win = null;
        try {
            win = new mySolutionBrow(null);
            JPopupMenu popup = win.listSolutions.getComponentPopupMenu();
            assertNotNull("答案浏览列表应有上下文菜单", popup);

            List<String> titles = new ArrayList<>();
            for (Component c : popup.getComponents()) {
                assertTrue("上下文菜单项应走 HoloPopupMenu", c instanceof HoloPopupMenu.Row);
                titles.add(((HoloPopupMenu.Row) c).getText());
            }
            // 原版 onCreateContextMenu 只有这一项（另外两项被 <!-- --> 注释掉）
            assertEquals("[导出到剪切板: Lurd]", titles.toString());
        } finally {
            if (win != null) {
                win.setVisible(false);
                win.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- 辅助

    /** 扫描主源码里含 {@code needle} 的行，返回 {@code 相对路径:行号}。 */
    private static List<String> scan(String needle) {
        List<String> hits = new ArrayList<>();
        for (File f : mainSources) {
            String rel = f.getPath().replace('\\', '/');
            int idx = rel.indexOf("my/boxman/");
            if (idx >= 0) rel = rel.substring(idx + "my/boxman/".length());
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*") || line.trim().startsWith("//")) continue;  // 跳过注释
                    if (line.contains(needle)) hits.add(rel + ":" + (i + 1));
                }
            } catch (Exception e) {
                throw new AssertionError("读源码失败: " + f, e);
            }
        }
        return hits;
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    private static File locate(String rel) {
        for (String prefix : new String[]{"", "./", "../desktop/"}) {
            File f = new File(prefix + rel);
            if (f.isDirectory()) return f;
        }
        return null;
    }
}
