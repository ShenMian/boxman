package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static org.junit.Assert.*;

public class Phase5DialogTest {

    @BeforeClass
    public static void setUp() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase5").getAbsolutePath();
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Test
    public void testColorDialog() {
        final Color[] selected = new Color[1];
        ColorDialog dlg = new ColorDialog(null, Color.BLUE, color -> selected[0] = color);
        assertNotNull("ColorDialog should be created", dlg);
        assertNotNull("rSlider should exist", dlg.rSlider);
        assertNotNull("previewPanel should exist", dlg.previewPanel);

        dlg.rSlider.setValue(120);
        dlg.gSlider.setValue(60);
        dlg.bSlider.setValue(200);

        dlg.btOK.doClick();
        assertNotNull("Selected color should be returned", selected[0]);
        assertEquals("Red value should be 120", 120, selected[0].getRed());
        assertEquals("Green value should be 60", 60, selected[0].getGreen());
        assertEquals("Blue value should be 200", 200, selected[0].getBlue());
    }

    @Test
    public void testRuleDialog() {
        final int[] result = new int[2];
        RuleDialog dlg = new RuleDialog(null, (color, mask) -> {
            result[0] = color;
            result[1] = mask;
        });

        assertNotNull("RuleDialog should be created", dlg);
        assertNotNull("colorSlider should exist", dlg.colorSlider);
        assertNotNull("chkWall should exist", dlg.chkWall);

        dlg.colorSlider.setValue(150);
        dlg.chkWall.setSelected(true);
        dlg.chkFloor.setSelected(false);
        dlg.chkGoal.setSelected(true);
        dlg.chkBox.setSelected(false);
        dlg.chkPlayer.setSelected(true);

        dlg.btOK.doClick();
        assertEquals("Mask should be 1 + 4 + 16 = 21", 21, result[1]);
    }

    /**
     * 「关卡尺寸」框（原版 {@code new_level_dialog.xml}）：只有「列 × 行」两个数字框，
     * 默认 10 列 × 15 行。越界（&lt;3 或 &gt;100）退回默认值而不是拒绝。
     */
    @Test
    public void testNewLevelDialog() {
        final int[] got = new int[2];
        NewLevelDialog dlg = new NewLevelDialog(null, (rows, cols) -> {
            got[0] = rows;
            got[1] = cols;
        });

        assertNotNull("NewLevelDialog should be created", dlg);
        assertEquals("默认列数应为 10", 10, dlg.spCols.getValue());
        assertEquals("默认行数应为 15", 15, dlg.spRows.getValue());

        dlg.spRows.setValue(20);
        dlg.spCols.setValue(18);
        dlg.btOK.doClick();
        assertEquals("Rows should match", 20, got[0]);
        assertEquals("Cols should match", 18, got[1]);
    }

    @Test
    public void testGotoDialog() {
        final int[] target = new int[]{-1};
        GotoDialog dlg = new GotoDialog(null, 5, 20, idx -> target[0] = idx);

        assertNotNull("GotoDialog should be created", dlg);
        dlg.spLevel.setValue(8);
        dlg.btOK.doClick();

        assertEquals("Target level index should be 7 (0-based)", 7, target[0]);
    }

    @Test
    public void testExportFragmentRejectsMissingSetList() {
        // 原版 myExportFragment.doInBackground 的第一道闸门：mySets == null
        myExportFragment f = new myExportFragment(null, null, false, false, false, null);
        assertEquals("没有可导出的内容！", f.runNow());
    }

    @Test
    public void testUrlInputDialog() {
        UrlInputDialog dlg = new UrlInputDialog(null, content -> {});
        assertNotNull("UrlInputDialog should be created", dlg);
        assertNotNull("tfUrl should exist", dlg.tfUrl);
        assertNotNull("progressBar should exist", dlg.progressBar);
    }

    @Test
    public void testDelDialogRemoved() {
        // PC 自造的「删除确认 + 是否连同解答与状态一起删」对话框（DelDialog）已删除：
        // 原版 myGridView 的「删除」只是一句 setMessage 的确认框（myGridView.java:1484-1492），
        // 没有这个勾选框；而原版真正带「删除答案...」的是 BoxMan 的**关卡集**上下文菜单
        // （BoxMan.java:1450），属于另一条尚未移植的入口链。
        assertFalse("DelDialog 不应再存在", new File("src/main/java/my/boxman/DelDialog.java").exists());
    }

    @Test
    public void testGameViewHasNoMenuBarAndNoMapPopupMenu() {
        // 原版 myGameView 是 FEATURE_NO_TITLE + FLAG_FULLSCREEN：既没有 ActionBar 也没有菜单栏，
        // 全部菜单项都在底栏「更多」按钮弹出的选项菜单里（res/menu/player.xml）。
        // 阶段 G ④ 还把 PC 自造的「地图右键菜单」整体删掉了 —— 原版 myGameView /
        // myGameViewMap 里没有任何 registerForContextMenu / setOnLongClickListener。
        myGameView gv = new myGameView();
        assertNull("myGameView 不应有 JMenuBar（原版无菜单栏）", gv.getJMenuBar());
        assertNull("地图上不应有右键菜单（原版没有上下文菜单）",
                gv.mMap.getComponentPopupMenu());

        // 功能入口没丢：全都落在「更多」按钮弹出的选项菜单里
        gv.openOptionsMenu();
        java.util.List<String> titles = gv.optionsMenuTitlesForTest();
        assertTrue("选项菜单应有 player.xml 的 13 项", titles.size() >= 13);
        assertTrue(titles.contains("重新开始"));
        assertTrue(titles.contains("导出..."));
        assertTrue(titles.contains("导入..."));
        assertTrue(titles.contains("打开状态..."));

        gv.myStop();
        gv.dispose();
    }
}
