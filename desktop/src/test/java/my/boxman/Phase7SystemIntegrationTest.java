package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Phase 7: End-to-End System Testing & Full Acceptance Verification.
 */
public class Phase7SystemIntegrationTest {

    private static mySQLite sql;

    @BeforeClass
    public static void setUp() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase7").getAbsolutePath();
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Test
    public void testGameEndToEndWorkflow() {
        // 1. Create a clean level set
        long setId = sql.find_Set("E2E_Test_Set");
        if (setId > 0) {
            sql.del_T(setId);
        }
        setId = sql.add_T(1, "E2E_Test_Set", "Tester", "E2E Comments");
        assertTrue("Level set should be created", setId > 0);

        // 2. Add a simple solvable level:
        // #####
        // #@$.#
        // #####
        String simpleMap = "#####\n#@$.#\n#####";
        mapNode node = new mapNode(simpleMap, "Simple Test", "Author", "Solvable");
        long levelId = sql.add_L(setId, node);
        assertTrue("Level should be inserted", levelId > 0);
        node.Level_id = levelId;

        // 3. Set current map and initialize myGameView
        myMaps.curMap = node;
        myGameView game = new myGameView();
        assertNotNull("myGameView should initialize", game);

        // 4. Verify initial state
        assertNotNull("cArray should be populated", game.m_cArray);
        assertEquals("Target count should be 1", 1, game.m_nGoals);
        assertEquals("Initially 0 goals completed", 0, game.m_nGoals_OK);

        // 5. Simulate pushing the box right (player at col 1 pushes box at col 2 into goal at col 3)
        // Simulate step: player moves right, pushes box
        game.m_cArray[1][1] = '-';
        game.m_cArray[1][2] = '@';
        game.m_cArray[1][3] = '*'; // Box on goal
        game.m_nGoals_OK = 1;
        game.m_iStep[0] = 1;

        // 6. Check clearance
        assertTrue("Level should be solved when all boxes on goals", game.myClearance());

        // 7. Save solution into database
        sql.inp_Ans(node, "R");
        sql.Set_L_Solved(levelId, 1, false);

        // 8. Verify solution is persisted
        sql.load_SolitionList(node.key);
        assertTrue("Answer list should contain the solution", myMaps.mState2.size() > 0);
    }

    @Test
    public void testEditorToGameWorkflow() {
        myEditView editor = new myEditView();
        drainEdt();
        assertNotNull("myEditView should be instantiated", editor);

        // Place elements in editor map
        int r = editor.mMap.m_nMapTop + 1;
        int c = editor.mMap.m_nMapLeft + 1;
        editor.m_cArray[r][c] = '@';
        editor.m_cArray[r][c + 1] = '$';
        editor.m_cArray[r][c + 2] = '.';

        // Verify element count calculation
        String boxesInfo = editor.getBoxs();
        assertNotNull("Boxes info should be generated", boxesInfo);
        assertTrue("Boxes info should reflect elements", boxesInfo.contains("箱:1") && boxesInfo.contains("标:1"));

        // Rotate map in editor
        editor.mMap.selNode.row = 1;
        editor.mMap.selNode.col = 1;
        editor.mMap.selNode2.row = 3;
        editor.mMap.selNode2.col = 3;
        editor.myRotate(3); // horizontal flip
        assertNotNull("Canvas should remain valid after rotation", editor.m_cArray);
    }

    @Test
    public void testLevelManagementLifecycle() throws Exception {
        // 1. Prepare level file
        File testDir = new File("build/test_boxman_phase7/temp");
        if (!testDir.exists()) testDir.mkdirs();
        File levelFile = new File(testDir, "test_lifecycle.xsb");

        String xsbContent = "Title: Lifecycle Level\nAuthor: Tester\n#####\n#@$.#\n#####\n";
        try (FileOutputStream fos = new FileOutputStream(levelFile)) {
            fos.write(xsbContent.getBytes("UTF-8"));
        }

        // 2. Import level file silently
        long existingSet = sql.find_Set("test_lifecycle");
        if (existingSet > 0) {
            sql.del_T(existingSet);
        }
        BoxManPC pc = new BoxManPC();
        pc.importLevelFile(levelFile, true);

        long setId = sql.find_Set("test_lifecycle");
        assertTrue("Imported set should exist in DB", setId > 0);

        // 3. Query level using QueryDialog logic
        sql.get_Levels(setId);
        assertTrue("Imported set should have levels", myMaps.m_lstMaps != null && myMaps.m_lstMaps.size() > 0);
        mapNode imported = myMaps.m_lstMaps.get(0);
        assertEquals("Title should match", "Lifecycle Level", imported.Title);

        // 4. Export logic —— 走原版的 myExportFragment（sel_Set2 的「确定」最终调用它）
        new File(myMaps.sRoot + myMaps.sPath + "导出/").mkdirs();
        File exported = new File(myMaps.sRoot + myMaps.sPath + "导出/test_lifecycle.xsb");
        exported.delete();   // 上一次跑留下的同名文档会让本次报「...跳过」
        myExportFragment exporter = new myExportFragment(null, null, false, false, true,
                new long[]{setId});
        String inf = exporter.runNow();
        assertTrue("Export should report OK, got: " + inf, inf.contains("...OK"));
        assertTrue("Exported document should exist", exported.exists());

        // 5. Delete level
        sql.del_L(imported.Level_id);
        sql.get_Levels(setId);
        assertEquals("Levels in set should be 0 after delete", 0, myMaps.m_lstMaps.size());

        // 6. Delete set
        sql.del_T(setId);
        assertEquals("Set should be deleted", -1, sql.find_Set("test_lifecycle"));
    }

    @Test
    public void testAdvancedFeaturesWorkflow() throws Exception {
        // 1. Action manager LURD mapping
        myActGMView actView = new myActGMView(null, false);
        actView.et_Action.setText("lluurrdd");
        actView.transformLURD("180");
        assertEquals("180 rotation should invert directions", "rrddlluu", actView.et_Action.getText());

        // 2. GIF animation export worker
        myMaps.curMap = new mapNode("#####\n#@$.#\n#####", "GIF Map", "Author", "");
        myGifMakeDialog gifDlg = new myGifMakeDialog(null, "rR", 0, null, null);
        assertNotNull("myGifMakeDialog created", gifDlg);

        CountDownLatch gifLatch = new CountDownLatch(1);
        myGifMakeDialog.GifWorker worker = gifDlg.new GifWorker() {
            @Override
            protected void done() {
                super.done();
                gifLatch.countDown();
            }
        };
        worker.execute();
        boolean gifDone = gifLatch.await(5, TimeUnit.SECONDS);
        assertTrue("GIF worker should complete in timeout", gifDone);

        // 3. Similar level comparison view
        myFindView findView = new myFindView();
        assertNotNull("myFindView initialized", findView);
        findView.myLevel();
        assertTrue("Toggled level should be source", findView.m_Level);

        // 4. Image recognition view
        myRecogView recogView = new myRecogView();
        assertNotNull("myRecogView initialized", recogView);
        assertNotNull("recog map initialized", recogView.getMap());
        assertNotNull("recog cell array initialized", recogView.getCellArray());
        recogView.dispose();
    }

    /**
     * 排空 EDT 队列 —— {@code myEditView} 构造器里的 {@code UiWindow.applyPhoneSize()} 会 pack()，
     * Swing 随后**异步**派发 componentResized → {@code myEditViewMap.setArena()}，
     * 它会重算 rtSize/rtF… 并把 selNode.row 置 -1。不排空的话这一下会随机落在断言中间。
     */
    private static void drainEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
    }
}
