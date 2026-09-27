package my.boxman;

import my.boxman.compat.sqlite.Cursor;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
 * <p>这条链上有<b>两个</b>独立的坑，都会让答案悄无声息地消失：
 * <ol>
 *   <li><b>顺序</b>：剪切板分支在「遇到第一行 XSB」时无条件把待存答案清空（原版如此）。
 *       答案写在关卡<b>之前</b>时，这一清就把还没挂到任何关卡上的答案丢了。</li>
 *   <li><b>Level_id</b>：{@code add_L()} 返回新关卡的 {@code L_id}，但导入器从不把它回填到
 *       {@code mapNode} 上；而 {@code mapNode(4 参字符串)} 构造器把 {@code Level_id} 设成 0。
 *       于是 {@code inp_Ans()} → {@code isAnsOK_and_Case()} → {@code isLevelOK()} 一律否掉，
 *       答案被丢弃，<b>而 {@code myMaps.m_Nums[0]}（解析到的答案数）照样 +1</b> ——
 *       用户看到的就是「答案数 1 / 导入数 0」。</li>
 * </ol>
 *
 * <p>所以本类的断言一律<b>直接查库</b>（{@code G_State} 里必须真有一行答案），
 * 不只信统计数字 —— 统计口径在坑 2 里是会骗人的。
 */
public class Phase37ClipboardSolutionTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(120);

    /**
     * 夹具。**形状必须互不相同**：答案表按「关卡 CRC + 答案 CRC」查重，而 CRC 是算在
     * 标准化后的关卡上的 —— 外面补几行空白/地板会在标准化时被裁掉，两个看起来不一样的
     * 地图可能撞成同一个 key，那样第二次导入的答案会被算成「重复」而不入库
     * （{@code m_Nums[1]++}）。所以这里每个夹具都用真正不同的墙壁结构。
     *
     * <p>人 {@code @} 在箱子 {@code $} 左边、目标 {@code .} 在箱子右边，答案都是一步「右推」。
     */
    private static final String MAP1 = "#####\n#@$.#\n#####";
    private static final String MAP2 = "#######\n#@$.#  #\n#     #\n#######";
    private static final String MAP3 = "######\n#@$.##\n#    #\n######";
    private static final String MAP4 = "#####\n#  @$.#\n#     #\n#####";
    private static final String MAP5 = "#######\n#@$.# #\n#     #\n#     #\n#######";
    private static final String MAP6 = "########\n#@$.#  #\n#      #\n########";
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
        myMaps.mState1.clear();
        myMaps.mState2.clear();
    }

    // ------------------------------------------------------------ 用例

    /** 关卡在答案<b>之前</b>（先 XSB 再 Title/Author/Solution）—— 原本就正常，别改坏。 */
    @Test
    public void solutionFollowingTheMapIsImported() {
        long setId = importClipboard("P37_After", MAP1 + "\n"
                + "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n");

        assertAnswerInDatabase(setId, "R");
    }

    /** 关卡在答案<b>之后</b>（先 Title/Author/Solution 再 XSB）—— 用户样本就是这个顺序。 */
    @Test
    public void solutionPrecedingTheMapIsImported() {
        long setId = importClipboard("P37_Before", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n"
                + MAP2 + "\n");

        assertAnswerInDatabase(setId, "R");
    }

    /** 答案跟在答案头同一行上（站点导出也有这种写法）。 */
    @Test
    public void solutionOnTheHeaderLineIsImported() {
        long setId = importClipboard("P37_Inline", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + ANS + "\n"
                + MAP3 + "\n");

        assertAnswerInDatabase(setId, "R");
    }

    /**
     * 小写 {@code r} 也要能被认下来 —— {@code isAnsOK_and_Case()} 会把答案统一修正成
     * 「推」用大写、单纯移动用小写，一步右推应当存成 {@code "R"}。
     */
    @Test
    public void lowercaseAnswerIsAcceptedAndNormalised() {
        long setId = importClipboard("P37_Lower", MAP4 + "\n"
                + "Title: Trifle 27 var\n"
                + SOLUTION_HEAD + "\n"
                + "r\n");

        assertAnswerInDatabase(setId, "R");
    }

    /**
     * 答案折成多行（站点导出常见）也要能拼起来 —— 答案头后面先跟一段，
     * 下一行再续上一段。两条答案行都会被拼进同一个 {@code sSolution}。
     */
    @Test
    public void wrappedAnswerLinesAreJoined() {
        long setId = importClipboard("P37_Wrapped", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + "R\n"
                + "\n"
                + MAP5 + "\n");

        assertAnswerInDatabase(setId, "R");
    }

    /**
     * 真·端到端：导入完成后把关卡列表重读一遍，再走 {@code load_StateList()} ——
     * 这就是用户点进关卡后「这道题有没有答案」那条链。
     */
    @Test
    public void answerIsVisibleThroughTheProductionReadPath() {
        long setId = importClipboard("P37_EndToEnd", "Title: Trifle 27 var\n"
                + "Author: Guenther\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n"
                + MAP6 + "\n");

        // 重新加载关卡列表（G_Level）
        sql.get_Levels(setId);
        assertEquals("应读回 1 个关卡", 1, myMaps.m_lstMaps.size());

        mapNode nd = myMaps.m_lstMaps.get(0);
        assertTrue("关卡在库里的 L_id 必须 > 0，否则答案挂不上去", nd.Level_id > 0);

        // 按关卡读状态/答案列表（G_State）
        sql.load_StateList(nd.Level_id, nd.key);

        assertEquals("这道题应当能读出 1 条答案", 1, myMaps.mState2.size());
        assertEquals("这条答案应当挂在本关卡上", nd.Level_id, myMaps.mState2.get(0).pid);
        assertEquals("答案的关卡 CRC 应与关卡一致", nd.key, myMaps.mState2.get(0).pkey);
        assertEquals("移动 1 步", 1, myMaps.mState2.get(0).moves);
        assertEquals("推动 1 次", 1, myMaps.mState2.get(0).pushs);
        assertEquals("关卡应被标记为已解", 1, (int) sql.count_Sovled(setId));
    }

    /** 「Lurd」没勾时按原版口径不导答案（选项语义，不是缺陷）。 */
    @Test
    public void answerIsSkippedWhenLurdIsUnchecked() {
        myMaps.isLurd = false;
        long setId = importClipboard("P37_NoLurd", MAP1 + "\n"
                + "Title: Trifle 27 var\n"
                + SOLUTION_HEAD + "\n"
                + ANS + "\n");

        assertEquals("关卡照样要导入", 1, myMaps.m_Nums[2]);
        assertEquals("勾了才导答案：这里应一个都没解析到", 0, myMaps.m_Nums[0]);

        sql.get_Levels(setId);
        assertEquals("库里不该有任何答案", 0,
                sql.count_S(0L, myMaps.m_lstMaps.get(0).key, 1));
    }

    /**
     * 答案没写「Solution」头、只是孤零零一行动作时，不该被当成答案硬塞进去。
     * 这里关卡照常导入，但答案数是 0 —— 只有 {@code Solution:} 才会让状态机进入答案态。
     */
    @Test
    public void strayActionLineWithoutHeaderIsNotAnAnswer() {
        long setId = importClipboard("P37_Stray", MAP1 + "\n"
                + "Title: Trifle 27 var\n"
                + ANS + "\n");

        assertEquals("没有答案头，就不该记到「解析到的答案数」", 0, myMaps.m_Nums[0]);
        sql.get_Levels(setId);
        assertEquals("库里不该有任何答案", 0,
                sql.count_S(0L, myMaps.m_lstMaps.get(0).key, 1));
    }

    // ------------------------------------------------------------ 辅助

    /** 导入一段剪切板文本，返回所属关卡集 id。 */
    private static long importClipboard(String setTitle, String text) {
        long old = sql.find_Set(setTitle);
        if (old > 0) sql.del_T(old);
        myMaps.m_Set_id = sql.add_T(3, setTitle, "", "");
        assertTrue("关卡集应建成功", myMaps.m_Set_id > 0);

        ArrayList<String> files = new ArrayList<String>();
        files.add(text);
        new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_CLIPBOARD, files).runNow();

        assertEquals("应导入 1 个关卡", 1, myMaps.m_Nums[2]);
        return myMaps.m_Set_id;
    }

    /**
     * 直接查 {@code G_State}：答案必须真落库。
     *
     * <p>顺手把 {@code m_Nums} 的口径也检查一遍 —— 但重点在库里的那一行。
     * 只断言 {@code m_Nums[0] == 1} 是不够的：{@code add_S} 在「答案被验证链否掉」之前
     * 就已经把计数加过了，统计数字会掩盖真实的失败。
     *
     * <p>⚠️ {@code count_S(long id, long key, int Solution)} 的形参顺序容易记反：
     * 第 1 个是 <b>id</b>，第 2 个才是 <b>key</b>。数答案（{@code Solution == 1}）时
     * 用的是 <b>key</b>（关卡 CRC），所以 id 位要显式传 0，写成 {@code count_S(0L, key, 1)}。
     * 传反了不会报错，只会静默查不到行。
     */
    private static void assertAnswerInDatabase(long setId, String expectedAns) {
        assertEquals("库中关卡数", 1, sql.count_Level(setId));

        sql.get_Levels(setId);
        assertEquals("应读回 1 个关卡", 1, myMaps.m_lstMaps.size());
        mapNode nd = myMaps.m_lstMaps.get(0);

        long ansCount = sql.count_S(0L, nd.key, 1);
        assertEquals("G_State 里必须真的多出一行答案（按关卡 CRC 计数）", 1, ansCount);

        assertEquals("解析到的答案数", 1, myMaps.m_Nums[0]);
        assertEquals("答案不应被判为重复/无效", 0, myMaps.m_Nums[1]);

        // 内容层面的核对：走生产读路径把答案取出来，比对规范化后的动作串
        myMaps.mState1.clear();
        myMaps.mState2.clear();
        sql.load_StateList(nd.Level_id, nd.key);
        assertEquals("应读出 1 条答案", 1, myMaps.mState2.size());
        assertEquals("答案应挂在本关卡上", nd.Level_id, myMaps.mState2.get(0).pid);

        String raw = rawAnswerOf(nd.key);
        assertNotNull("应能取到答案原文", raw);
        assertEquals("答案应被修正为「推」的大小写形式", expectedAns, raw);
        assertEquals("保存的移动步数", 1, myMaps.mState2.get(0).moves);
        assertEquals("保存的推动次数", 1, myMaps.mState2.get(0).pushs);
    }

    /** 从 {@code G_State} 里取出该关卡 CRC 对应的答案原文。 */
    private static String rawAnswerOf(long key) {
        Cursor c = sql.mSDB.query("G_State", null,
                "P_Key = ? AND G_Solution = 1", new String[]{Long.toString(key)}, null, null, null);
        try {
            if (c.moveToNext()) return c.getString(c.getColumnIndex("G_Ans"));
        } finally {
            c.close();
        }
        return null;
    }
}
