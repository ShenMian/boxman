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

        // 原版 myExport.java:146-160：推关卡界面的导出 + 有答案时，初值**只**显示 Lurd，
        // XSB 默认不勾选（用户勾上 XSB 才会拼成 "XSB + Solution 头 + Lurd"）。
        // PC 早期用自造的 updateContent() 无条件拼 XSB+答案，是行为偏差。
        assertEquals("初值应只有 Lurd", "rR", exp.et_Action.getText());
        assertFalse("推关卡界面且有答案时 XSB 默认不勾选", exp.cb_XSB.isSelected());
        assertTrue("Lurd 默认勾选", exp.cb_Lurd.isSelected());
        assertTrue("推关卡界面应显示「导出正推现场」那一行", exp.cb_Cur.isVisible());

        // 勾上 XSB → 拼出 XSB + Solution 头 + Lurd
        exp.cb_XSB.doClick();
        String both = exp.et_Action.getText();
        assertTrue("勾上 XSB 后应含关卡图", both.contains("#####"));
        assertTrue("勾上 XSB 后应含 Solution 头", both.contains("Solution"));
        assertTrue("勾上 XSB 后应含答案", both.contains("rR"));
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

        // 阶段 D-2 起 myFindView 按原版语义工作：默认显示「相似关卡」（m_Level = false），
        // 关卡数据走 myMaps.oldMap / curMap 注入（loadForTest 内部即 getLevel_inf()）。
        mapNode src = new mapNode(-1L, -1L, 3, 5, "#####\n#@$.#\n#####", "源", "", "",
                "#####\n#@$.#\n#####");
        mapNode sim = new mapNode(-2L, -1L, 5, 3, "###\n#@#\n#$#\n#.#\n###", "似", "", "",
                "###\n#@#\n#$#\n#.#\n###");
        findView.loadForTest(src, sim);

        assertFalse("原版默认显示相似关卡", findView.m_Level);
        assertEquals("相似关卡正是源关卡转 90°", 100, findView.mSimilarity);

        findView.myLevel();
        assertTrue("myLevel() 切到源关卡", findView.m_Level);

        findView.myTrun();
        assertEquals("myTrun() 在 0/1 转之间循环", 1, myMaps.m_nTrun);

        findView.toggleLevelAll();
        assertTrue("toggleLevelAll() 切到关卡全貌", findView.mMap.m_Level_All);
    }

    @Test
    public void testRecogViewAndMap() {
        myRecogView recogView = new myRecogView();
        assertNotNull("myRecogView should be created", recogView);
        assertNotNull("mMap should be initialized", recogView.mMap);
        assertNotNull("m_cArray should be allocated", recogView.getCellArray());
        // 原版 res/menu/recog.xml 的 6 项全是 showAsAction="always"
        assertEquals(6, recogView.getActionBar().getBarActionCount());
        // 底行按钮按 recog_view.xml 固定尺寸（29dp 元素按钮 / 35dp 方向按钮）
        assertEquals(29, recogView.bt_Floor.getPreferredSize().width);
        assertEquals(35, recogView.bt_Left.getPreferredSize().width);
        recogView.dispose();
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
        FindDialog fDlg = new FindDialog(null, (sets, sim, ans, sort, ignoreBox) -> findDone[0] = true);
        assertNotNull("FindDialog should be created", fDlg);
        assertNotNull("lstSets should exist", fDlg.lstSets);
        assertNotNull("lstSimilarity should exist", fDlg.lstSimilarity);
        assertNotNull("chkAns should exist", fDlg.chkAns);
        assertNotNull("chkSort should exist", fDlg.chkSort);
        assertNotNull("chkIgnoreBox should exist", fDlg.chkIgnoreBox);
        assertTrue("全选 should default to checked", fDlg.chkAll.isSelected());
        // find_dialog.xml 的相似度列表：100/95/90/85/80/75/66/50
        assertEquals("相似度应有 8 档", 8, fDlg.lstSimilarity.getModel().getSize());
        assertEquals("100", fDlg.lstSimilarity.getModel().getElementAt(0));
        assertEquals("50", fDlg.lstSimilarity.getModel().getElementAt(7));
    }
}
