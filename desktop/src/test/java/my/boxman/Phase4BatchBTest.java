package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class Phase4BatchBTest {

    @BeforeClass
    public static void setUp() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase4b").getAbsolutePath();
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Test
    public void testExportFrame() {
        myExport exp = new myExport("#####\n#@$.#\n#####", "rR", "#####\n#@$.#\n#####", "", true, 0, null, null, "test");
        assertNotNull("myExport frame should be created", exp);
        assertNotNull("et_Action text area should be initialized", exp.et_Action);
        assertTrue("Content should contain XSB", exp.et_Action.getText().contains("#####"));
        assertTrue("Content should contain solution", exp.et_Action.getText().contains("Solution"));

        exp.cb_Lurd.setSelected(false);
        assertNotNull(exp.et_Action.getText());
    }

    @Test
    public void testGifMakeDialog() {
        myGifMakeDialog gifDlg = new myGifMakeDialog(null, "rrddlluu", 0, null, null);
        assertNotNull("myGifMakeDialog should be instantiated", gifDlg);
        assertNotNull("cbInterval should exist", gifDlg.cbInterval);
        assertNotNull("chkMoveByMove should exist", gifDlg.chkMoveByMove);
        assertTrue("chkMoveByMove should be default checked", gifDlg.chkMoveByMove.isSelected());
    }

    @Test
    public void testFindViewAndMap() {
        myFindView findView = new myFindView();
        assertNotNull("myFindView should be created", findView);
        assertNotNull("mMap canvas should be initialized", findView.mMap);

        char[][] src = new char[][]{
                {'#', '#', '#'},
                {'#', '@', '#'},
                {'#', '#', '#'}
        };
        char[][] sim = new char[][]{
                {'#', '#', '#', '#'},
                {'#', '@', '$', '#'},
                {'#', '#', '.', '#'},
                {'#', '#', '#', '#'}
        };

        findView.setLevels(src, 3, 3, sim, 4, 4);
        assertTrue("Default should show source level", findView.m_Level);

        findView.switchLevel();
        assertFalse("Should toggle to similar level", findView.m_Level);

        findView.rotateLevel();
        assertEquals("Should rotate to 1", 1, findView.mTrun);

        findView.toggleViewMode();
        assertTrue("Should toggle to full view mode", findView.m_Level_All);
    }

    @Test
    public void testRecogViewAndMap() {
        myRecogView recogView = new myRecogView();
        assertNotNull("myRecogView should be created", recogView);
        assertNotNull("mMap should be initialized", recogView.mMap);
        assertNotNull("m_cArray should be allocated", recogView.m_cArray);

        // Click a cell and verify assignment
        recogView.selectedObj = 1; // Wall '#'
        recogView.onCellClicked(2, 2);
        assertEquals("Cell (2,2) should be wall", '#', recogView.m_cArray[2][2]);

        recogView.selectedObj = 2; // Box '$'
        recogView.onCellClicked(2, 3);
        assertEquals("Cell (2,3) should be box", '$', recogView.m_cArray[2][3]);
    }

    @Test
    public void testPicListView() {
        myPicListView picList = new myPicListView();
        assertNotNull("myPicListView should be created", picList);
        assertNotNull("gridPanel should be initialized", picList.gridPanel);
        assertNotNull("scrollPane should be initialized", picList.scrollPane);
    }

    @Test
    public void testQueryAndFindDialogs() {
        final boolean[] queryDone = {false};
        QueryDialog qDlg = new QueryDialog(null, results -> queryDone[0] = true);
        assertNotNull("QueryDialog should be created", qDlg);
        assertNotNull("tfTitle should exist", qDlg.tfTitle);
        assertNotNull("tfBoxes1 should exist", qDlg.tfBoxes1);
        assertNotNull("关卡集列表 should exist", qDlg.lstSets);

        final boolean[] findDone = {false};
        FindDialog fDlg = new FindDialog(null, (results, sim, ignoreBox) -> findDone[0] = true);
        assertNotNull("FindDialog should be created", fDlg);
        assertNotNull("sliderSimilarity should exist", fDlg.sliderSimilarity);
        assertEquals("Default similarity should be 80%", 80, fDlg.sliderSimilarity.getValue());
    }
}
