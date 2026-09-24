package my.boxman;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;

import my.boxman.compat.UiWindow;

/**
 * 原版 {@code AndroidManifest.xml} 中**所有 18 个 Activity 都是
 * {@code android:screenOrientation="portrait"}**，因此每个 PC 窗口都必须保持手机竖屏尺寸
 * （内容区 370×780，1dp = 1px），而不是 PC 习惯的横屏 800×600。
 *
 * <p>本测试把这条约定锁住：新增窗口若忘了调用 {@link UiWindow#applyPhoneSize}，
 * 或者布局的固有最小尺寸撑破了 370×780，这里会直接失败。
 */
public class WindowSizingTest {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman_winsize";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = UiWindow.PHONE_WIDTH;
        myMaps.m_nWinHeight = UiWindow.PHONE_HEIGHT;
        new File(myMaps.sRoot).mkdirs();

        mySQLite sql = mySQLite.getInstance();
        sql.openDataBase();
        myMaps.loadSkins();
    }

    private static void assertPhonePortrait(String name, Window window) {
        try {
            // ⚠️ 必须先 addNotify() + validate() 再读尺寸。
            // 只在构造完立刻读的话，JMenuBar 挖走的那 23px 还没体现出来
            // （pack() 之后内容区就是 370×780），测试会假通过 ——
            // 曾因此漏掉 myRecogView / myEditView / myFindView / myStateBrow /
            // myActGMView / myExport 六个窗口内容区实际只有 370×757 的问题。
            window.addNotify();
            window.validate();

            Container pane = window instanceof javax.swing.JFrame
                    ? ((javax.swing.JFrame) window).getContentPane()
                    : ((javax.swing.JDialog) window).getContentPane();
            int w = pane.getWidth();
            int h = pane.getHeight();
            Assert.assertEquals(name + " 内容区宽度应为 " + UiWindow.PHONE_WIDTH
                    + "（原版竖屏），实际 " + w + "x" + h, UiWindow.PHONE_WIDTH, w);
            Assert.assertEquals(name + " 内容区高度应为 " + UiWindow.PHONE_HEIGHT
                    + "（原版竖屏），实际 " + w + "x" + h + "；差值常见于 setJMenuBar 在"
                    + " applyPhoneSize 之后调用（菜单栏挖走 23px）", UiWindow.PHONE_HEIGHT, h);
            Assert.assertTrue(name + " 应为竖屏（高 > 宽），实际 " + w + "x" + h, h > w);
        } finally {
            window.dispose();
        }
    }

    @Test
    public void testMainWindowIsPhonePortrait() {
        BoxManPC app = new BoxManPC();
        assertPhonePortrait("BoxManPC", app);
    }

    @Test
    public void testGameAndGridWindowsArePhonePortrait() {
        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assert.assertFalse("应有至少一个关卡集", sets.isEmpty());
        long setId = sets.get(0).id;
        String setTitle = sets.get(0).title;

        assertPhonePortrait("myGameView", new myGameView());
        assertPhonePortrait("myGridView", new myGridView(setId, setTitle));
        assertPhonePortrait("myPicListView", new myPicListView());
    }

    @Test
    public void testEditorAndBrowseWindowsArePhonePortrait() {
        assertPhonePortrait("myEditView", new myEditView());
        assertPhonePortrait("myFindView", new myFindView());
        assertPhonePortrait("myRecogView", new myRecogView());
        assertPhonePortrait("myStateBrow", new myStateBrow());
        assertPhonePortrait("mySolutionBrow", new mySolutionBrow(null));
        assertPhonePortrait("myActGMView", new myActGMView(null, false));
    }

    @Test
    public void testHelpAndAboutWindowsArePhonePortrait() {
        assertPhonePortrait("Help", new Help(0));

        assertPhonePortrait("myAbout", new myAbout(null));

        ArrayList<set_Node> sets = mySQLite.m_SQL.get_GroupList(0);
        Assert.assertFalse("应有至少一个关卡集", sets.isEmpty());
        long setId = sets.get(0).id;

        assertPhonePortrait("myAbout1", new myAbout1(null, setId, "10/20"));

        mySQLite.m_SQL.get_Levels(setId);
        Assert.assertFalse("应有至少一关", myMaps.m_lstMaps.isEmpty());
        assertPhonePortrait("myAbout2", new myAbout2(null, myMaps.m_lstMaps.get(0)));

        // autoLoad=false：只建界面，不发网络请求
        assertPhonePortrait("mySubmitList", new mySubmitList(false));
        assertPhonePortrait("mySubmit", new mySubmit());
    }
}
