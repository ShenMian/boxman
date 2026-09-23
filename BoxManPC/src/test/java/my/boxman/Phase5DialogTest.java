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
        System.setProperty("java.awt.headless", "true");
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

    @Test
    public void testNewLevelDialog() {
        final Object[] levelData = new Object[4];
        NewLevelDialog dlg = new NewLevelDialog(null, (title, author, rows, cols) -> {
            levelData[0] = title;
            levelData[1] = author;
            levelData[2] = rows;
            levelData[3] = cols;
        });

        assertNotNull("NewLevelDialog should be created", dlg);
        dlg.tfTitle.setText("测试关卡");
        dlg.tfAuthor.setText("测试作者");
        dlg.spRows.setValue(20);
        dlg.spCols.setValue(18);

        dlg.btOK.doClick();
        assertEquals("Title should match", "测试关卡", levelData[0]);
        assertEquals("Author should match", "测试作者", levelData[1]);
        assertEquals("Rows should match", 20, levelData[2]);
        assertEquals("Cols should match", 18, levelData[3]);
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
    public void testExportDialog() {
        final String[] message = new String[1];
        ExportDialog dlg = new ExportDialog(null, msg -> message[0] = msg);

        assertNotNull("ExportDialog should be created", dlg);
        assertNotNull("modelSets should be populated", dlg.modelSets);
        assertNotNull("chkIncludeAns should exist", dlg.chkIncludeAns);
        assertTrue("chkIncludeAns should default to true", dlg.chkIncludeAns.isSelected());
    }

    @Test
    public void testUrlInputDialog() {
        UrlInputDialog dlg = new UrlInputDialog(null, content -> {});
        assertNotNull("UrlInputDialog should be created", dlg);
        assertNotNull("tfUrl should exist", dlg.tfUrl);
        assertNotNull("progressBar should exist", dlg.progressBar);
    }

    @Test
    public void testDelDialog() {
        final boolean[] flag = new boolean[1];
        DelDialog dlg = new DelDialog(null, "测试删除关卡", delAns -> flag[0] = delAns);

        assertNotNull("DelDialog should be created", dlg);
        assertTrue("chkDeleteSolutions should default to true", dlg.chkDeleteSolutions.isSelected());

        dlg.chkDeleteSolutions.setSelected(false);
        dlg.btDelete.doClick();
        assertFalse("Should confirm with delAns false", flag[0]);
    }

    @Test
    public void testGameViewMenuBar() {
        myGameView gv = new myGameView();
        JMenuBar mb = gv.getJMenuBar();
        assertNotNull("myGameView should have JMenuBar", mb);
        assertTrue("Menu bar should contain at least 4 menus", mb.getMenuCount() >= 4);

        boolean hasNav = false, hasAction = false, hasView = false;
        for (int i = 0; i < mb.getMenuCount(); i++) {
            JMenu menu = mb.getMenu(i);
            if (menu != null) {
                if ("导航".equals(menu.getText())) hasNav = true;
                if ("操作".equals(menu.getText())) hasAction = true;
                if ("视图".equals(menu.getText())) hasView = true;
            }
        }
        assertTrue("Must contain 导航 menu", hasNav);
        assertTrue("Must contain 操作 menu", hasAction);
        assertTrue("Must contain 视图 menu", hasView);
    }
}
