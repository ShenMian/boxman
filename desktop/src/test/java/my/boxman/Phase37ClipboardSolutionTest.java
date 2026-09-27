package my.boxman;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 H 修正②：剪切板导入要能把 {@code Solution(...)} 后面的答案一起带进来。
 *
 * <p>用户样本（sokoban 站点上常见的写法）形如：
 * <pre>
 * Title: Trifle 27 var
 * Author: Guenther
 * Solution(pushes 603, moves 1868, inlines 185, changes 103, sessions 181):
 * lurdLURD…（一长串答案）
 * </pre>
 * 现象是「关卡进来了，答案没进来」。
 *
 * <p>根因：{@code mySplitLevelsFragment} 的剪切板分支在「遇到第一行 XSB」时无条件把待存答案
 * 清空（原版如此）。当答案写在关卡<b>之前</b>时，这一清就把还没挂到任何关卡上的答案丢了 ——
 * 后面真正存关卡时 {@code sSolution} 已经是空的。关卡在答案之前的顺序不受影响，
 * 这就是「有时能导进来」的原因。
 */
public class Phase37ClipboardSolutionTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /**
     * 三条夹具：@ 在箱子左边、目标在箱子右边，答案都是一步「右推」。
     * 形状刻意做得不同 —— 答案表按关卡 CRC 查重，同一个关卡在同一库里导入两次会算「重复答案」。
     */
    private static final String MAP1 = "#####\n#@$.#\n#####";
    private static final String MAP2 = "#####\n#@$.#\n#   #\n#####";
    private static final String MAP3 = "######\n#@$. #\n#    #\n######";
    private static final String ANS = "R";

    /** 用户样本那种答案头（带 pushes/moves/inlines/changes/sessions，冒号在最后）。 */
    private static final String SOLUTION_HEAD =
            "Solution(pushes 1, moves 1, inlines 1, changes 1, sessions 1):";

    private static mySQLite sql;

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase37").getAbsolutePath();
        myMaps.sPath = "/";

        // 每次跑用例都从一份全新的关卡库开始：答案表是按关卡 CRC 查重的（del_T 只删关卡、
        // 保留答案），留着旧库的话第二次跑就会命中「重复答案」，断言就不稳了。
        File db = new File(myMaps.sRoot + "/DataBase/BoxMan.db");
        if (db.exists()) db.delete();

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    @Before
    public void setUp() {
        myMaps.isXSB = true;
        myMaps.isLurd = true;          // 「Lurd」勾选：要导入答案
        myMaps.isComment = false;
        myMaps.m_Nums = new int[]{0, 0, 0, 0};
    }

    // ------------------------------------------------------------ 用例

    /** 关卡在答案<b>之前</b>（先 XSB 再 Title/Author/Solution）—— 原本就正常，别改坏。 */
    @Test
    public void solutionFollowingTheMapIsImported() {
        importClipboard("P37_After", MAP1 + "\n"
                + "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n");

        assertAnswerImported();
    }

    /** 关卡在答案<b>之后</b>（先 Title/Author/Solution 再 XSB）—— 用户样本就是这个顺序。 */
    @Test
    public void solutionPrecedingTheMapIsImported() {
        importClipboard("P37_Before", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n"
                + MAP2 + "\n");

        assertAnswerImported();
    }

    /** 答案跟在答案头同一行上（站点导出也有这种写法）。 */
    @Test
    public void solutionOnTheHeaderLineIsImported() {
        importClipboard("P37_Inline", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + ANS + "\n"
                + MAP3 + "\n");

        assertAnswerImported();
    }

    /** 「Lurd」没勾时按原版口径不导答案（选项语义，不是缺陷）。 */
    @Test
    public void answerIsSkippedWhenLurdIsUnchecked() {
        myMaps.isLurd = false;
        importClipboard("P37_NoLurd", MAP1 + "\n"
                + "Title: Trifle 27 var\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n");

        assertEquals("关卡照样要导入", 1, myMaps.m_Nums[2]);
        assertEquals("勾了才导答案：这里应一个都没解析到", 0, myMaps.m_Nums[0]);
    }

    // ------------------------------------------------------------ 辅助

    private static void importClipboard(String setTitle, String text) {
        long old = sql.find_Set(setTitle);
        if (old > 0) sql.del_T(old);
        myMaps.m_Set_id = sql.add_T(3, setTitle, "", "");

        ArrayList<String> files = new ArrayList<String>();
        files.add(text);
        new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_CLIPBOARD, files).runNow();

        assertEquals("应导入 1 个关卡", 1, myMaps.m_Nums[2]);
        assertTrue("关卡集应建成功", myMaps.m_Set_id > 0);
    }

    /**
     * {@code m_Nums[0]} = 解析到的答案数，{@code m_Nums[1]} = 重复/无效/超长被拒的答案数
     * （原版统计口径，见 {@code add_S}）。
     */
    private static void assertAnswerImported() {
        assertEquals("答案应入库（m_Nums[0] 是解析到的答案数）", 1, myMaps.m_Nums[0]);
        assertEquals("答案不应被判为重复/无效", 0, myMaps.m_Nums[1]);
    }
}
