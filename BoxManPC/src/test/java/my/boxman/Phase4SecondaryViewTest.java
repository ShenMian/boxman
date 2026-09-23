package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class Phase4SecondaryViewTest {

    @BeforeClass
    public static void setUp() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase4").getAbsolutePath();
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Test
    public void testEditViewMapAndEditViewInit() {
        myEditView editView = new myEditView();
        assertNotNull("myEditView frame should be instantiated", editView);
        assertNotNull("mMap canvas should be non-null", editView.mMap);

        // Verify bottom buttons exist
        assertNotNull("bt_UnDo should be initialized", editView.bt_UnDo);
        assertNotNull("bt_ReDo should be initialized", editView.bt_ReDo);
        assertNotNull("bt_Cut should be initialized", editView.bt_Cut);
        assertNotNull("bt_Copy should be initialized", editView.bt_Copy);
        assertNotNull("bt_Paste should be initialized", editView.bt_Paste);
        assertNotNull("bt_Tru should be initialized", editView.bt_Tru);
        assertNotNull("bt_Save should be initialized", editView.bt_Save);
        assertNotNull("bt_More should be initialized", editView.bt_More);

        assertFalse("UnDo should be initially disabled", editView.bt_UnDo.isEnabled());
        assertFalse("ReDo should be initially disabled", editView.bt_ReDo.isEnabled());
    }

    @Test
    public void testEditViewCanvasDrawingAndTransform() {
        myEditView editView = new myEditView();
        assertNotNull("m_cArray should be allocated", editView.m_cArray);

        // Simulate drawing a wall element
        int row = editView.mMap.m_nMapTop + 2;
        int col = editView.mMap.m_nMapLeft + 2;
        editView.mMap.m_iR = 2;
        editView.mMap.m_iC = 2;

        editView.DoAct(3); // Single point draw
        editView.m_cArray[row][col] = '#';
        assertTrue("bt_UnDo should be enabled after drawing", editView.bt_UnDo.isEnabled());

        // Test rotate transform
        editView.mMap.selNode.row = 1;
        editView.mMap.selNode.col = 1;
        editView.mMap.selNode2.row = 3;
        editView.mMap.selNode2.col = 3;
        editView.myRotate(3); // Horizontal flip

        assertNotNull("Box count calculation should succeed", editView.getBoxs());
    }

    @Test
    public void testActGMViewInitAndTransform() {
        myActGMView actView = new myActGMView(null, false);
        assertNotNull("et_Action textarea should be initialized", actView.et_Action);
        assertNotNull("Buttons should be initialized", actView.btDO);

        // Test LURD transformation: L90 (l->d, u->l, r->u, d->r)
        actView.et_Action.setText("lluurrdd");
        actView.transformLURD("L90");
        assertEquals("L90 transform should correctly map directions", "ddlluurr", actView.et_Action.getText());

        // Test R90
        actView.et_Action.setText("ddlluurr");
        actView.transformLURD("R90");
        assertEquals("R90 transform should return to original directions", "lluurrdd", actView.et_Action.getText());
    }

    @Test
    public void testStateBrowAndSolutionBrow() {
        myStateBrow stateBrow = new myStateBrow();
        assertNotNull("TabbedPane should exist", stateBrow.tabPane);
        assertEquals("Should have 2 tabs", 2, stateBrow.tabPane.getTabCount());
        assertNotNull("listStates should exist", stateBrow.listStates);
        assertNotNull("listAnswers should exist", stateBrow.listAnswers);

        mySolutionBrow solBrow = new mySolutionBrow(null);
        assertNotNull("listSolutions should exist", solBrow.listSolutions);
    }

    @Test
    public void testSplitDialogWorker() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] resultHolder = new String[1];

        SplitDialog dlg = new SplitDialog(null, 0, Collections.emptyList(), result -> {
            resultHolder[0] = result;
            latch.countDown();
        });

        assertNotNull("SplitDialog should be created", dlg);
        assertNotNull("ProgressBar should be created", dlg.progressBar);

        // Execute worker directly in background
        dlg.startImport();
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Import worker should complete within timeout", completed);
        assertNotNull("Result message should be non-null", resultHolder[0]);
        assertTrue("Result message should indicate success", resultHolder[0].contains("导入成功"));
    }
}
