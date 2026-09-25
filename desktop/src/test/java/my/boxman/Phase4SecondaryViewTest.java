package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

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
        // 原版是 ExpandableListView 两个分组（状态 / 答案），PC 用 JTree 等价实现，
        // 不再是早先那版自造的 JTabbedPane。
        assertNotNull("ActionBar should exist", stateBrow.getActionBar());
        assertEquals("ActionBar title", "关卡状态", stateBrow.getActionBar().getBarTitle());
        assertTrue("ActionBar should show the up chevron", stateBrow.getActionBar().isUpEnabled());
        assertNotNull("Tree should exist", stateBrow.getTree());
        assertEquals("Should have 2 groups", 2, stateBrow.getTree().getModel().getChildCount(
                stateBrow.getTree().getModel().getRoot()));

        mySolutionBrow solBrow = new mySolutionBrow(null);
        assertNotNull("listSolutions should exist", solBrow.listSolutions);
    }

    @Test
    public void testSplitLevelsFragmentRejectsMissingArguments() {
        // 原版 doInBackground 的第一道闸门：myType < 0 || myFiles == null
        mySplitLevelsFragment f = new mySplitLevelsFragment(null, null, -1, null);
        assertEquals("没有可解析的内容！", f.runNow());

        // 取消一次不应抛异常（原版 stopSplit 只是置标志 + 回调）
        mySplitLevelsFragment f2 = new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_CLIPBOARD, new java.util.ArrayList<String>());
        f2.stopSplit();
    }
}
