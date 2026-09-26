package my.boxman;

import my.boxman.compat.HoloTabBar;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 底栏三个**开关**按钮的选中态配色 —— 「瞬移 / 逆推 / 计数」。
 *
 * <p>原版 {@code myGameView} 里它们是 {@code <CheckBox style="@style/tab_style">}，
 * {@code tab_style} 的 {@code android:button="@null"} 去掉了勾选框，所以选中与否全靠**底色**表达：
 * 原版在监听器里显式改背景色（{@code myGameView.java:1552 / 1561 / 1585 / 1632} 等）：
 *
 * <pre>
 * if (isChecked) buttonView.setBackgroundColor(0xff445566);   // 深色 = 「按下」
 * else           buttonView.setBackgroundColor(0xff778899);   // 与底栏同色 = 「弹起」
 * </pre>
 *
 * <p>端口原先漏了这一步（底栏一直是平的），用户看到的就是「开关没有两种状态」。
 *
 * <p>⚠️ 另外几个标签**不**变色，别顺手统一：
 * {@code bt_UnDo}/{@code bt_ReDo}（后退/前进，靠 {@code setChecked(!isChecked())} 触发动作的
 * 瞬时按钮）、{@code bt_TR}（转置，每点一次换一转）、{@code bt_More}（更多，弹菜单）——
 * 原版都没给它们 {@code setBackgroundColor}。
 */
public class Phase34BottomBarToggleTest {

    private static final String LEVEL = "#####\n#@$.#\n#####";

    private myGameView win;
    private mapNode savedCur;
    private ArrayList<mapNode> savedList;
    private String savedRoot, savedPath;
    private int savedTrun;
    private final int[] savedSets = new int[40];

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase34";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();

        mySQLite.getInstance().openDataBase();
        myMaps.loadSkins();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedList = myMaps.m_lstMaps;
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        savedTrun = myMaps.m_nTrun;
        System.arraycopy(myMaps.m_Sets, 0, savedSets, 0, savedSets.length);

        myMaps.curMap = new mapNode(LEVEL, "底栏测试关", "测试", "");
        myMaps.curMap.fileName = "phase34_test.XSB";
        myMaps.curMapNum = -4;
        myMaps.curMap.Level_id = 1;
        myMaps.m_nTrun = 0;
        myMaps.m_lstMaps = new ArrayList<mapNode>();
        myMaps.m_lstMaps.add(myMaps.curMap);
        myMaps.m_Sets[6] = 0;    // 瞬移：默认关
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.myStop();
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.m_lstMaps = savedList;
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
        myMaps.m_nTrun = savedTrun;
        System.arraycopy(savedSets, 0, myMaps.m_Sets, 0, savedSets.length);
    }

    private myGameView view() {
        win = new myGameView();
        return win;
    }

    // ---------------------------------------------------------------- 1. 三个开关

    /** 初始都是「弹起」：底色与底栏同色 {@code #778899}。 */
    @Test
    public void theThreeTogglesStartUncheckedAndFlat() {
        myGameView g = view();

        for (myGameView.GameButton b : new myGameView.GameButton[]{g.bt_IM, g.bt_BK, g.bt_Sel}) {
            assertFalse(b.getText() + " 初始应当是未选中", b.isChecked());
            assertEquals(b.getText() + " 未选中时底色应为 #778899（与底栏同色）",
                    HoloTabBar.BG_UNCHECKED, b.getBackground());
        }
    }

    /** 选中 → 底色变深（「按下」）；再点一次 → 恢复。 */
    @Test
    public void checkingAToggleDarkensItsBackgroundAndUncheckingRestoresIt() {
        myGameView g = view();

        for (myGameView.GameButton b : new myGameView.GameButton[]{g.bt_IM, g.bt_BK, g.bt_Sel}) {
            String n = b.getText();

            b.setChecked(true);
            assertTrue(n + " 应当已选中", b.isChecked());
            assertEquals(n + " 选中时底色应为 #445566（「按下」效果）",
                    HoloTabBar.BG_CHECKED, b.getBackground());

            b.setChecked(false);
            assertFalse(n + " 再点一次应当恢复未选中", b.isChecked());
            assertEquals(n + " 恢复后底色应回到 #778899",
                    HoloTabBar.BG_UNCHECKED, b.getBackground());
        }
    }

    /** 两个底色必须真的不同 —— 否则「两种状态」在视觉上不存在。 */
    @Test
    public void theTwoStatesAreVisuallyDistinct() {
        assertNotEquals("选中/未选中底色不能相同", HoloTabBar.BG_CHECKED, HoloTabBar.BG_UNCHECKED);
        assertEquals("未选中底色应等于底栏底色", HoloTabBar.BG, HoloTabBar.BG_UNCHECKED);
        assertEquals("选中底色应照原版 #445566",
                new java.awt.Color(0x44, 0x55, 0x66), HoloTabBar.BG_CHECKED);
        assertEquals("未选中底色应照原版 #778899",
                new java.awt.Color(0x77, 0x88, 0x99), HoloTabBar.BG_UNCHECKED);
    }

    /** 「瞬移」是持久开关：上次开着的话，下次进游戏要直接是「按下」态。 */
    @Test
    public void imRestoresItsPressedLookFromTheSavedSetting() {
        myMaps.m_Sets[6] = 1;
        myGameView g = view();

        assertTrue("m_Sets[6]==1 时「瞬移」应当已选中", g.bt_IM.isChecked());
        assertEquals("并且底色应当已经是「按下」的 #445566",
                HoloTabBar.BG_CHECKED, g.bt_IM.getBackground());
        assertEquals("m_Sets[6] 不该被构造过程改掉", 1, myMaps.m_Sets[6]);
    }

    // ---------------------------------------------------------------- 2. 其余标签不变色

    /**
     * 瞬时/动作类标签**不该**有「按下」态。特别是 {@code bt_UnDo} / {@code bt_ReDo}：
     * 原版用 {@code setChecked(!isChecked())} 触发动作，若跟着变色就会闪一下深色。
     */
    @Test
    public void theInstantaneousTabsNeverChangeTheirBackground() {
        myGameView g = view();

        for (myGameView.GameButton b : new myGameView.GameButton[]{
                g.bt_UnDo, g.bt_ReDo, g.bt_TR, g.bt_More}) {
            String n = b.getText();
            assertEquals(n + " 初始底色应为 #778899", HoloTabBar.BG_UNCHECKED, b.getBackground());

            b.setChecked(true);
            assertEquals(n + " 是瞬时/动作标签，选中也不该变色",
                    HoloTabBar.BG_UNCHECKED, b.getBackground());

            b.setChecked(false);
            assertEquals(n + " 恢复后仍应是 #778899",
                    HoloTabBar.BG_UNCHECKED, b.getBackground());
        }
    }

    // ---------------------------------------------------------------- 3. 源码约定锁

    /** 三个开关的监听器都要照原版显式刷底色；且不能把这件事塞进 {@code setChecked}。 */
    @Test
    public void theThreeToggleListenersRefreshTheirBackground() throws Exception {
        String src = new java.lang.String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                        "src/main/java/my/boxman/myGameView.java")),
                java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");

        assertEquals("瞬移：初始 1 处 + 监听器 2 个分支", 3, countOf(src, "bt_IM.setCheckedBackground("));
        assertEquals("逆推：监听器 2 个分支", 2, countOf(src, "bt_BK.setCheckedBackground("));
        assertEquals("计数：监听器 2 个分支", 2, countOf(src, "bt_Sel.setCheckedBackground("));
        assertEquals("转置不该有「按下」态", 0, countOf(src, "bt_TR.setCheckedBackground("));
        assertEquals("更多不该有「按下」态", 0, countOf(src, "bt_More.setCheckedBackground("));
        assertEquals("后退/前进是靠 setChecked 触发动作的瞬时按钮，不该变色",
                0, countOf(src, "bt_UnDo.setCheckedBackground(")
                        + countOf(src, "bt_ReDo.setCheckedBackground("));
    }

    /**
     * ⚠️ 别把「刷底色」塞进 {@code TabButton.setChecked()} —— 那样
     * {@code bt_UnDo} / {@code bt_ReDo} 也会跟着变色（原版不会）。
     */
    @Test
    public void setCheckedDoesNotSecretlyChangeTheBackground() throws Exception {
        String src = new java.lang.String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                        "src/main/java/my/boxman/compat/HoloTabBar.java")),
                java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");

        int at = src.indexOf("public void setChecked(boolean b)");
        assertTrue("找不到 setChecked(boolean)", at >= 0);
        int open = src.indexOf('{', at);
        int close = src.indexOf('}', open);
        String body = src.substring(open, close + 1);
        assertFalse("setChecked 只能改选中态，不能顺带刷底色 —— 否则 bt_UnDo / bt_ReDo "
                        + "（靠 setChecked 触发动作的瞬时按钮）会跟着变色，原版不会。实际：" + body,
                body.contains("Background"));
    }

    private static int countOf(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
