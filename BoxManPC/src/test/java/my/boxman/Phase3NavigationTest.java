package my.boxman;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

public class Phase3NavigationTest {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_phase3";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;
        new File(myMaps.sRoot).mkdirs();

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        myMaps.loadSkins();
    }

    @Test
    public void testBoxManPCAndTreeInitialization() {
        BoxManPC app = new BoxManPC();
        Assert.assertNotNull("BoxManPC should be initialized", app);
        Assert.assertNotNull("Menu bar should be present", app.getJMenuBar());
        Assert.assertTrue("Menu count should be >= 3", app.getJMenuBar().getMenuCount() >= 3);

        app.dispose();
    }

    @Test
    public void testMyGridViewLoadingAndCardGeneration() {
        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assert.assertNotNull(sets);
        Assert.assertFalse("Should have at least one set", sets.isEmpty());

        long setId = sets.get(0).id;
        String title = sets.get(0).title;

        myGridView grid = new myGridView(setId, title);
        Assert.assertNotNull(grid);
        Assert.assertNotNull(grid.getJMenuBar());
        Assert.assertNotNull(myMaps.m_lstMaps);
        Assert.assertFalse("Levels should be loaded", myMaps.m_lstMaps.isEmpty());

        // 验证第一关
        mapNode first = myMaps.m_lstMaps.get(0);
        Assert.assertNotNull(first.Map);
        Assert.assertTrue("Row count positive", first.Rows > 0);
        Assert.assertTrue("Col count positive", first.Cols > 0);

        grid.dispose();
    }

    @Test
    public void testHelpAndAboutWindows() {
        // 验证不同模式的 Help 窗口均能正确初始化并不抛出异常
        for (int mode = 0; mode <= 6; mode++) {
            Help help = new Help(mode);
            Assert.assertNotNull("Help window mode " + mode + " should be created", help);
            Assert.assertTrue("Help title should not be empty", help.getTitle() != null && !help.getTitle().isEmpty());
            help.dispose();
        }

        // 验证关于窗口
        myAbout about = new myAbout(null);
        Assert.assertNotNull("myAbout should be created", about);
        about.dispose();

        // 验证关于关卡集与关卡详细
        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        long setId = sets.get(0).id;
        myAbout1 about1 = new myAbout1(null, setId, "10/20");
        Assert.assertNotNull("myAbout1 should be created", about1);
        about1.dispose();

        mySQLite.m_SQL.get_Levels(setId);
        mapNode first = myMaps.m_lstMaps.get(0);
        myAbout2 about2 = new myAbout2(null, first);
        Assert.assertNotNull("myAbout2 should be created", about2);
        about2.dispose();
    }

    @Test
    public void testLevelFileImport() throws Exception {
        // 清理旧的 JUnitTestSet，保证测试幂等性
        long oldSetId = mySQLite.m_SQL.find_Set("JUnitTestSet");
        if (oldSetId > 0) {
            mySQLite.m_SQL.del_T(oldSetId);
        }

        // 构造一个简单的测试关卡文本文件
        File importDir = new File(myMaps.sRoot, "test_import");
        importDir.mkdirs();
        File testLevelFile = new File(importDir, "JUnitTestSet.txt");

        String levelContent = "Title: Test Set Level 1\n" +
                "Author: TestAuthor\n" +
                "Comment: TestComment\n" +
                "#####\n" +
                "#@$.#\n" +
                "#####\n" +
                "Solution: r\n";

        FileWriter writer = new FileWriter(testLevelFile);
        writer.write(levelContent);
        writer.close();

        BoxManPC app = new BoxManPC();
        int imported = app.importLevelFile(testLevelFile, true);
        Assert.assertTrue("Should import at least 1 level: imported=" + imported, imported >= 1);

        // 验证导入后的关卡集与关卡可被检索
        long newSetId = mySQLite.m_SQL.find_Set("JUnitTestSet");
        Assert.assertTrue("Imported set should exist in DB", newSetId > 0);

        mySQLite.m_SQL.get_Levels(newSetId);
        Assert.assertFalse("Imported levels list should not be empty", myMaps.m_lstMaps.isEmpty());
        mapNode importedMap = myMaps.m_lstMaps.get(0);
        Assert.assertEquals("Level title mismatch (Title=" + importedMap.Title + ", Author=" + importedMap.Author + ", Rows=" + importedMap.Rows + ", Cols=" + importedMap.Cols + ")",
                "Test Set Level 1", importedMap.Title);
        Assert.assertEquals("TestAuthor", importedMap.Author);

        app.dispose();
    }
}
