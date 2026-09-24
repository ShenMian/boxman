package my.boxman;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;

public class Phase2GameViewTest {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;
        new File(myMaps.sRoot).mkdirs();

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        myMaps.loadSkins();
    }

    @Test
    public void testGameViewInitializationAndPlay() throws Exception {
        // 加载入门关卡的第一组
        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assert.assertNotNull(sets);
        Assert.assertFalse("Should have at least one set", sets.isEmpty());

        long setId = sets.get(0).id;
        mySQLite.m_SQL.get_Levels(setId);
        Assert.assertNotNull(myMaps.m_lstMaps);
        Assert.assertFalse("Should have levels in set", myMaps.m_lstMaps.isEmpty());

        myMaps.curMap = myMaps.m_lstMaps.get(0);
        myMaps.m_nTrun = myMaps.curMap.Trun;

        // 初始化 myGameView 窗口及地图
        myGameView game = new myGameView();
        Assert.assertNotNull(game.mMap);
        Assert.assertNotNull(game.bt_UnDo);
        Assert.assertNotNull(game.bt_ReDo);
        Assert.assertNotNull(game.bt_IM);
        Assert.assertNotNull(game.bt_BK);
        Assert.assertNotNull(game.bt_Sel);
        Assert.assertNotNull(game.bt_TR);
        Assert.assertNotNull(game.bt_More);

        // 验证初始状态
        Assert.assertTrue("Row should be positive", game.m_nRow >= 0);
        Assert.assertTrue("Col should be positive", game.m_nCol >= 0);
        Assert.assertTrue("Goals should be > 0", game.m_nGoals > 0);
        Assert.assertNotNull("m_cArray should be initialized", game.m_cArray);

        // 模拟寻径与推箱操作验证
        // 查找人旁边可达的地板格子测试寻径
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        boolean pathFound = false;
        for (int k = 0; k < 4; k++) {
            int nr = game.m_nRow + dr[k];
            int nc = game.m_nCol + dc[k];
            if (nr >= 0 && nr < myMaps.curMap.Rows && nc >= 0 && nc < myMaps.curMap.Cols) {
                if (game.m_cArray[nr][nc] == '-' || game.m_cArray[nr][nc] == '.') {
                    pathFound = game.FindPath(nr, nc, false);
                    if (pathFound) {
                        break;
                    }
                }
            }
        }
        Assert.assertTrue("Should find path to at least one neighbor floor", pathFound);
        Assert.assertFalse("m_lstMovReDo should not be empty after finding path", game.m_lstMovReDo.isEmpty());

        // 测试底栏 7 按钮方法调用与切换
        game.bt_IM.setChecked(true);
        Assert.assertTrue(game.bt_IM.isChecked());
        game.bt_IM.setChecked(false);
        Assert.assertFalse(game.bt_IM.isChecked());

        // 测试转置按钮点击切换
        int trunBefore = myMaps.m_nTrun;
        game.bt_TR.doClick();
        Assert.assertEquals((trunBefore + 1) % 8, myMaps.m_nTrun);

        // 测试舞台自适应
        game.mMap.setBounds(0, 0, 800, 500);
        game.mMap.setArena();
        Assert.assertTrue("m_nPicWidth should be > 0", game.mMap.m_nPicWidth > 0);
        Assert.assertTrue("m_nPicHeight should be > 0", game.mMap.m_nPicHeight > 0);

        game.myStop();
        game.dispose();
        System.out.println("Phase 2 GameView initialization and basic gameplay verified successfully!");
    }
}
