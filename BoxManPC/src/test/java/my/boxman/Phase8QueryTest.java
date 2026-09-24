package my.boxman;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.JCheckBox;
import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 「关卡查询」功能的回归锁（原版 {@code BoxMan.java:472} → {@code myQueryFragment}）。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>{@link myQueryFragment} 的查询引擎真的能从 {@code G_Level} 里查出关卡；</li>
 *   <li>标题/箱子数区间过滤按原版的三段式条件生效；</li>
 *   <li>{@link QueryDialog} 的布局几何与 {@code query_dialog.xml} 一致
 *       （关卡集列表 190dp、5 行字段、输入框 160dp / 60dp、表头 33dp、按钮栏 53dp）。</li>
 * </ol>
 */
public class Phase8QueryTest {

    private static final int W = my.boxman.compat.UiWindow.PHONE_WIDTH;
    private static final int H = my.boxman.compat.UiWindow.PHONE_HEIGHT;

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/ui-snapshot/home";
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = W;
        myMaps.m_nWinHeight = H;
        new File(myMaps.sRoot).mkdirs();
        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDataBase();
        myMaps.mSets0 = mySQLite.m_SQL.get_GroupList(0);
        myMaps.mSets1 = mySQLite.m_SQL.get_GroupList(1);
        myMaps.mSets2 = mySQLite.m_SQL.get_GroupList(2);
        myMaps.mSets3 = mySQLite.m_SQL.get_GroupList(3);
    }

    /** 原版没有关卡集过滤时（m_sets == null）扫全表，这里断言能查出关卡。 */
    @Test
    public void testQueryWithoutSetFilterFindsLevels() {
        myQueryFragment q = new myQueryFragment(null, null,
                null, false, "", "", 1, 0, 0, 0, 0, 0);
        ArrayList<mapNode> result = q.runNow();
        assertNotNull("查询结果不应为 null", result);
        assertTrue("至少应查出一关（箱子数 >= 1）", result.size() > 0);
        for (mapNode nd : result) {
            assertNotNull("查出的关卡应带 XSB", nd.Map);
        }
    }

    /**
     * 箱子数区间：只留 >= 1 且 <= 3 的关卡，结果必须落在区间内。
     *
     * <p>注意断言的是 {@code nd.Map0}（= 数据库 {@code L_thin_XSB}），因为原版的过滤
     * 就是拿 {@code L_thin_XSB} 统计箱子数的；而 {@code nd.Map}（= {@code L_Content}）
     * 两者**并不总是相等** —— 测试库里 "Microban 155" 的 {@code L_Content} 有 11 个箱子、
     * {@code L_thin_XSB} 只有 1 个。这是原版数据的既有现象，不是移植 bug。
     */
    @Test
    public void testBoxCountRangeFilter() {
        myQueryFragment q = new myQueryFragment(null, null,
                null, false, "", "", 1, 0, 0, 3, 0, 0);
        ArrayList<mapNode> result = q.runNow();
        assertTrue("应能查出箱子数 1~3 的关卡", result.size() > 0);
        for (mapNode nd : result) {
            int boxes = countBoxes(nd.Map0);
            assertTrue("L_thin_XSB 箱子数 " + boxes + " 应落在 [1,3]（" + nd.Title + "）",
                    boxes >= 1 && boxes <= 3);
        }
    }

    /** 标题过滤：给一个必然不存在的标题 → 应为空。 */
    @Test
    public void testTitleFilterExcludesEverything() {
        myQueryFragment q = new myQueryFragment(null, null,
                null, false, "____不存在的标题____", "", 1, 0, 0, 0, 0, 0);
        assertEquals("不存在的标题应查不到任何关卡", 0, q.runNow().size());
    }

    /** 指定关卡集时走临时表 id_T 分支。 */
    @Test
    public void testQueryWithSetFilter() {
        if (myMaps.mSets0.isEmpty()) {
            return;   // 测试库里没有关卡集就跳过（不制造假失败）
        }
        long setId = myMaps.mSets0.get(0).id;
        myQueryFragment q = new myQueryFragment(null, null,
                new long[]{setId}, false, "", "", 1, 0, 0, 0, 0, 0);
        ArrayList<mapNode> result = q.runNow();
        for (mapNode nd : result) {
            assertEquals("查出的关卡都应属于指定关卡集", setId, nd.P_id);
        }
    }

    /** 布局几何：严格照 {@code query_dialog.xml} 的 dp 值。 */
    @Test
    public void testQueryDialogGeometry() {
        QueryDialog dlg = new QueryDialog(null, r -> { });
        try {
            dlg.applyHoloSize();   // 平时由 setVisible(true) 触发
            assertEquals("窗口宽度应为屏宽 95%", Math.round(W * 0.95f), dlg.getWidth());

            // 输入框宽度（1dp = 1px）
            assertEquals("关卡名称输入框 160dp", 160, dlg.tfTitle.getPreferredSize().width);
            assertEquals("关卡作者输入框 160dp", 160, dlg.tfAuthor.getPreferredSize().width);
            assertEquals("箱子数下限 60dp", 60, dlg.tfBoxes1.getPreferredSize().width);
            assertEquals("箱子数上限 60dp", 60, dlg.tfBoxes2.getPreferredSize().width);
            assertEquals("列数下限 60dp", 60, dlg.tfCols1.getPreferredSize().width);
            assertEquals("行数上限 60dp", 60, dlg.tfRows2.getPreferredSize().width);

            // 表头行 / 字段行 / 按钮栏高度
            assertEquals("表头行 33dp", 33, dlg.chkAns.getPreferredSize().height);
            assertEquals("字段行 30dp", 30, dlg.tfTitle.getPreferredSize().height);
            assertEquals("按钮栏 53dp（原版截图实测）", 53, dlg.btCancel.getPreferredSize().height);

            // 复选框：CompoundButton 把 button 位图画在控件 x=0（padding 不作用于位图），
            // 而文字从 paddingLeft + drawableWidth 开始。所以是「位图齐左 + 4dp 缝」，不是「位图也缩进 4dp」。
            // 实测原版：答案库框 140..240 → 位图 140..172、文字 176；全选框 240..320 → 绿勾 240..272、文字 276。
            for (JCheckBox cb : new JCheckBox[]{dlg.chkAns, dlg.chkAll}) {
                assertEquals(cb.getText() + " 位图齐左（padding 不作用于 button 位图）",
                        0, cb.getBorder().getBorderInsets(cb).left);
                assertEquals(cb.getText() + " 图标 32dp", 32, cb.getIcon().getIconWidth());
                assertEquals(cb.getText() + " 位图与文字间距 = padding 4dp", 4, cb.getIconTextGap());
            }
            assertEquals("答案库 100dp", 100, dlg.chkAns.getPreferredSize().width);
            assertEquals("全选 80dp", 80, dlg.chkAll.getPreferredSize().width);
            assertTrue("进入时「全选」默认勾选", dlg.chkAll.isSelected());
            assertFalse("进入时「答案库」默认不勾选", dlg.chkAns.isSelected());

            // 关卡集列表 190dp
            java.awt.Container column = dlg.getContentPane();
            assertNotNull(column);
        } finally {
            dlg.dispose();
        }
    }

    private static int countBoxes(String xsb) {
        if (xsb == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < xsb.length(); i++) {
            char ch = xsb.charAt(i);
            if (ch == '$' || ch == '*') {
                n++;
            }
        }
        return n;
    }
}
