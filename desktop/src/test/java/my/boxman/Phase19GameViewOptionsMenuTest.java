package my.boxman;

import my.boxman.compat.HoloPopupMenu;

import org.junit.After;
import org.junit.Before;
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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-3 验收：原版推关卡界面（{@code myGameView}）底栏「更多」按钮弹出的选项菜单。
 *
 * <p>原版 {@code myGameView} 是 {@code FEATURE_NO_TITLE} + {@code FLAG_FULLSCREEN} ——
 * <b>根本没有 ActionBar</b>，13 项全在 {@code res/menu/player.xml} 里，
 * 由 {@code bt_More} 的 {@code openOptionsMenu()} 弹出。
 *
 * <p>改写前 PC 版 {@code openOptionsMenu()} 用的是裸 {@code JPopupMenu + JMenuItem}：
 * ① 样式与 ActionBar 溢出菜单（{@code popup_menu_holo_dark}）不一致；
 * ② 只有 8 项，缺 {@code 导出...} / {@code 导入...} / {@code YASS求解} / {@code 打开状态...} / {@code 操作说明}；
 * ③ {@code 关于} 弹的是自造的 {@code JOptionPane}，而原版是 {@code myAbout2}（关卡描述）。
 *
 * <p>本次把菜单换成 {@link HoloPopupMenu}，并回补除 {@code YASS求解}（需外部求解器，阶段 F）
 * 之外的全部条目，顺序严格按 {@code player.xml}。
 *
 * <p><b>用例不许弹模态框</b>：{@code onReStart()} / {@code onImport()} / {@code onOpenState2()} /
 * {@code onExport()} 都会开窗（{@code myActGMView} 还是模态），所以只测「已拆出来的纯逻辑」，
 * 并用 {@code @Rule Timeout} 把「挂住」变成「失败」。
 */
public class Phase19GameViewOptionsMenuTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    /** {@code res/menu/player.xml} 里**未被注释**的 13 项，顺序即原版 XML 顺序。 */
    private static final String[] PLAYER_XML = {
            "设置...", "开关选项...", "重新开始", "退至首", "进至尾",
            "导出...", "导入...", "YASS求解", "打开状态...", "保存状态",
            "关于", "操作说明", "退出",
    };

    /** 阶段 F 才能补的一项（外部 YASS 求解器未接入）。 */
    private static final String NOT_YET_PORTED = "YASS求解";

    /** 一个合法的小关卡：3 行 × 5 列，1 个仓管员、1 箱 1 目标。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    private myGameView win;
    private mapNode savedCur;
    private int savedTrun;
    private boolean savedActionRedy;
    private int savedRecBegin;

    @BeforeClass
    public static void setUpClass() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase19";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = my.boxman.compat.UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = my.boxman.compat.UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        myMaps.loadSkins();
    }

    @Before
    public void setUp() {
        savedCur = myMaps.curMap;
        savedTrun = myMaps.m_nTrun;
        savedActionRedy = myMaps.m_ActionIsRedy;
        savedRecBegin = myMaps.m_nRecording_Bggin;

        // ⚠️ 必须先给 curMap，否则 myGameView 的 initGame() 会因 curMap == null 直接 return，
        //    m_cArray / bk_cArray / mark44 全是 null，导出相关的用例会 NPE。
        myMaps.curMap = new mapNode(LEVEL, "测试关", "测试", "");
        myMaps.curMap.fileName = "phase19_test.XSB";
        myMaps.curMapNum = -4;
        myMaps.m_nTrun = 0;

        win = new myGameView();
        win.openOptionsMenu();   // 底栏「更多」按钮点一下的效果
    }

    @After
    public void tearDown() {
        if (win != null) {
            win.setVisible(false);
            win.dispose();
            win = null;
        }
        MyToast.dismiss();
        myMaps.curMap = savedCur;
        myMaps.m_nTrun = savedTrun;
        myMaps.m_ActionIsRedy = savedActionRedy;
        myMaps.m_nRecording_Bggin = savedRecBegin;
    }

    // ---------------------------------------------------------------- 菜单（D-3 本体）

    @Test
    public void testNoJMenuBarInstalled() {
        assertNull("原版是 FEATURE_NO_TITLE，PC 不应再装 JMenuBar", win.getJMenuBar());
    }

    @Test
    public void testOptionsMenuIsHoloStyled() {
        JPopupMenu menu = win.optionsMenuForTest();
        assertNotNull("openOptionsMenu() 应产出菜单", menu);
        for (Component c : menu.getComponents()) {
            assertFalse("菜单项不应再是 Swing JMenuItem: " + c.getClass().getName(),
                    c instanceof JMenuItem);
            assertTrue("菜单项应是 HoloPopupMenu.Row: " + c.getClass().getName(),
                    c instanceof HoloPopupMenu.Row);
        }
    }

    @Test
    public void testOptionsMenuCoversEveryPlayerXmlItemExceptYass() {
        List<String> xml = readPlayerXmlTitles();
        assertEquals("player.xml 未被注释的项应为 13", PLAYER_XML.length, xml.size());
        assertEquals("player.xml 解析结果与常量不符（XML 改了？）",
                Arrays.asList(PLAYER_XML).toString(), xml.toString());

        List<String> pc = win.optionsMenuTitlesForTest();
        List<String> missing = new ArrayList<>();
        for (String t : xml) {
            if (!pc.contains(t)) missing.add(t);
        }
        assertEquals("阶段 D-3 之后，player.xml 只应剩 YASS求解 未接（阶段 F）",
                "[" + NOT_YET_PORTED + "]", missing.toString());
    }

    @Test
    public void testOptionsMenuOrderFollowsPlayerXml() {
        List<String> expected = new ArrayList<>(readPlayerXmlTitles());
        expected.remove(NOT_YET_PORTED);   // 阶段 F 补
        assertEquals("菜单顺序必须与 player.xml 一致（去掉未接的 YASS求解）",
                expected.toString(), win.optionsMenuTitlesForTest().toString());
    }

    @Test
    public void testEveryMenuItemIsVisibleAndEnabled() {
        JPopupMenu menu = win.optionsMenuForTest();
        for (String t : win.optionsMenuTitlesForTest()) {
            assertTrue("菜单项应可见: " + t, HoloPopupMenu.isVisible(menu, t));
            assertTrue("菜单项应可点: " + t, HoloPopupMenu.isEnabled(menu, t));
        }
        assertEquals("不应再有条目被置灰", 0, countDisabled(menu));
    }

    @Test
    public void testNoInventedPcOnlyItems() {
        List<String> pc = win.optionsMenuTitlesForTest();
        for (String t : pc) {
            assertTrue("菜单里不该有 player.xml 之外的项: " + t,
                    Arrays.asList(PLAYER_XML).contains(t));
        }
    }

    // ---------------------------------------------------------------- 各条目的动作

    @Test
    public void testPrepareImportClearsActionReadyAndSetsRecordingBegin() {
        win.m_lstMovUnDo.add((byte) 1);
        win.m_lstMovUnDo.add((byte) 4);
        myMaps.m_ActionIsRedy = true;
        myMaps.m_nRecording_Bggin = -1;

        win.prepareImport();

        assertFalse("「导入」应把 m_ActionIsRedy 清掉", myMaps.m_ActionIsRedy);
        assertEquals("正推录制起始点应等于当前 undo 栈深度",
                win.m_lstMovUnDo.size(), myMaps.m_nRecording_Bggin);
    }

    @Test
    public void testStateListSortsByTimeDescending() {
        myMaps.mState1.clear();
        for (String t : new String[]{"2026-01-01 00:00:00", "2026-09-25 12:00:00", "2025-12-31 23:59:59"}) {
            state_Node n = new state_Node();
            n.time = t;
            myMaps.mState1.add(n);
        }
        java.util.Collections.sort(myMaps.mState1, myGameView.STATE_TIME_DESC);

        List<String> got = new ArrayList<>();
        for (state_Node n : myMaps.mState1) got.add(n.time);
        assertEquals("状态列表应按保存时间倒序（最后保存的在最前）",
                "[2026-09-25 12:00:00, 2026-01-01 00:00:00, 2025-12-31 23:59:59]",
                got.toString());
    }

    @Test
    public void testBuildExportDataPackagesInitialState() {
        myGameView.ExportData d = win.buildExportData();

        assertNotNull(d.xsb);
        assertTrue("关卡初态应带 Title 头", d.xsb.contains("\nTitle: "));
        assertTrue("关卡初态应带 Author 头", d.xsb.contains("\nAuthor: "));
        assertTrue("关卡初态应以 curMap.Map 开头",
                d.xsb.startsWith(myMaps.curMap.Map));

        assertNotNull(d.local);
        assertEquals("正推现场的行数应等于关卡行数",
                myMaps.curMap.Rows, d.local.split("\n", -1).length);
        assertEquals("旋转现场的行数应等于关卡列数（m_nTrun == 0 时为偶数支路）",
                myMaps.curMap.Rows, d.local8.split("\n", -1).length);

        assertEquals("标尺数组长度应为 行×列",
                myMaps.curMap.Rows * myMaps.curMap.Cols, d.rule.length);
        assertEquals("箱子编号数组长度应为目标数", win.m_nGoals, d.boxNum.length);
        assertEquals("是否答案 == 目标完成", win.m_nGoals_OK == win.m_nGoals, d.isAns);
        assertEquals("GIF 起点应带过去", win.m_Gif_Start, d.gifStart);
        assertEquals("「导入/YASS」标记应带过去", win.m_imPort_YASS, d.importYass);
    }

    @Test
    public void testBuildExportDataEmitsLurdForForwardMoves() {
        win.m_lstMovUnDo.clear();
        // 1=l 2=u 3=r 4=d 5=L 6=U 7=R 8=D
        win.m_lstMovUnDo.add((byte) 1);
        win.m_lstMovUnDo.add((byte) 4);
        win.m_lstMovUnDo.add((byte) 3);
        win.m_lstMovUnDo.add((byte) 6);

        assertEquals("正推动作应逐字节译成 lurd 字母", "ldrU", win.buildExportData().lurd);
    }

    @Test
    public void testBuildExportDataAppendsBackwardSectionWhenNotSolved() {
        win.m_lstMovUnDo.clear();
        win.m_lstMovUnDo2.clear();
        win.m_nGoals_OK = 0;            // 强制 isANS == false
        win.m_nGoals = Math.max(1, win.m_nGoals);
        win.m_nCol0 = 2;
        win.m_nRow0 = 5;
        win.m_lstMovUnDo.add((byte) 1);            // l
        win.m_lstMovUnDo2.add((byte) 3);           // r

        String lurd = win.buildExportData().lurd;
        assertEquals("未解关时应追加逆推段：[列+1,行+1] + 动作（先列后行）",
                "l\n[3,6]r", lurd);
    }

    // ---------------------------------------------------------------- 辅助

    private static int countDisabled(JPopupMenu menu) {
        int n = 0;
        for (Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row && !c.isEnabled()) n++;
        }
        return n;
    }

    /** 读原版 {@code res/menu/player.xml}，剥掉 {@code <!-- -->} 后按出现顺序取 {@code android:title}。 */
    private static List<String> readPlayerXmlTitles() {
        File f = locate("android/app/src/main/res/menu/player.xml");
        assertNotNull("找不到原版 player.xml，工作目录可能是 " + new File(".").getAbsolutePath(), f);
        try {
            String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            xml = xml.replaceAll("(?s)<!--.*?-->", "");     // 剥注释（原版注释掉「Solver求解」）
            Matcher m = Pattern.compile("android:title\\s*=\\s*\"([^\"]*)\"").matcher(xml);
            List<String> titles = new ArrayList<>();
            while (m.find()) titles.add(m.group(1));
            return titles;
        } catch (Exception e) {
            throw new AssertionError("读 player.xml 失败: " + e, e);
        }
    }

    private static File locate(String rel) {
        for (String prefix : new String[]{"../", "", "./"}) {
            File f = new File(prefix + rel);
            if (f.isFile()) return f;
        }
        return null;
    }
}
