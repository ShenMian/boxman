package my.boxman;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 H 修正③：「关卡扩展」里的关卡集要按<b>快手手机版</b>的规则排序。
 *
 * <p>现象：入门关卡 / 进阶关卡 / 花样关卡 三组本来就是按名称排好的（内置库的插入顺序就是
 * 名称序），只有「关卡扩展」乱 —— 因为这一组是用户导入 / 新建出来的，库里是什么顺序就显示
 * 什么顺序。
 *
 * <p>原版 {@code BoxMan.onCreate()} 只对扩展组排了一次：
 * <pre>
 * Collections.sort(myMaps.mSets3, new MyComparator());
 * </pre>
 * PC 侧漏了这一句，本用例把它补上并锁住。{@code MyComparator} 的实际规则（注意和「直觉」
 * 略有出入，见 {@link #digitsSortBeforeUnderscoreBeforeLetters}）：
 * <ol>
 *   <li>首字符<b>不是</b>汉字的一律排在汉字之前；</li>
 *   <li>两边都不是汉字 → {@code compareToIgnoreCase}，大小写不敏感，ASCII 上表现为
 *       <b>数字 &lt; 下划线 &lt; 字母</b>（{@code '0'=0x30 &lt; '_'=0x5F}，而字母会先折成大写、
 *       折不动再折小写，所以下划线落在数字与字母之间）；</li>
 *   <li>两边都是汉字 → 用 GB2312 字节序比较，等价于<b>拼音</b>序（GB2312 一级字库按拼音排列）。</li>
 * </ol>
 */
public class Phase38ExtensionSetSortTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 组别下标：0 入门 / 1 进阶 / 2 花样 / 3 关卡扩展。 */
    private static final int GROUP_BEGINNER = 0;
    private static final int GROUP_EXT = 3;

    /** 乱序塞进扩展组的一组标题，覆盖「数字 / 下划线 / 字母 / 汉字」四类。 */
    private static final List<String> SHUFFLED = Arrays.asList(
            "Zone26", "阿凡提", "696", "Sasquatch_II", "_test", "Boxxle_all",
            "包青天", "1a", "新关卡集", "我的关卡集");

    /** 原版 MyComparator 排出来的顺序（也就是手机版的顺序）。 */
    private static final List<String> EXPECTED = Arrays.asList(
            "1a", "696", "_test", "Boxxle_all", "Sasquatch_II", "Zone26",
            "阿凡提", "包青天", "我的关卡集", "新关卡集");

    private static mySQLite sql;

    @BeforeClass
    public static void setUpClass() {
        // 每次都从内置库重来一份，用例的输入才可控
        File db = new File("build/test_boxman_phase38/DataBase/BoxMan.db");
        if (db.exists()) db.delete();

        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase38").getAbsolutePath();
        myMaps.sPath = "/";

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();
    }

    // ------------------------------------------------------------ 比较器本体

    private static BoxManPC.MyComparator cmp() {
        return new BoxManPC.MyComparator();
    }

    private static set_Node node(String title) {
        set_Node nd = new set_Node();
        nd.title = title;
        return nd;
    }

    @Test
    public void nonChineseSortsBeforeChinese() {
        assertTrue("非汉字开头的一律排在汉字之前",
                cmp().compare(node("Zzz"), node("阿凡提")) < 0);
        assertTrue("汉字开头的一律排在非汉字之后",
                cmp().compare(node("阿凡提"), node("Zzz")) > 0);
    }

    @Test
    public void asciiComparisonIgnoresCase() {
        assertEquals("只有大小写不同应视为相同",
                0, cmp().compare(node("abc"), node("ABC")));
        assertTrue(cmp().compare(node("abc"), node("ABD")) < 0);
        assertEquals("汉字之外的比较不看大小写",
                0, cmp().compare(node("Dragon"), node("dragon")));
    }

    /**
     * 锁住「下划线落在数字与字母之间」—— 这是 {@code compareToIgnoreCase} 的真实行为，
     * 和「下划线排在最前」的直觉不一样，容易在重构时被改坏。
     */
    @Test
    public void digitsSortBeforeUnderscoreBeforeLetters() {
        assertTrue("数字 < 下划线", cmp().compare(node("1a"), node("_test")) < 0);
        assertTrue("下划线 < 字母", cmp().compare(node("_test"), node("abc")) < 0);
        assertTrue("字母 < 下划线", cmp().compare(node("Zzz"), node("_test")) > 0);
    }

    /** 汉字按 GB2312 字节序 = 拼音序：a &lt; b &lt; w &lt; x。 */
    @Test
    public void chineseSortsByPinyin() {
        assertTrue("阿(a) < 包(b)", cmp().compare(node("阿凡提"), node("包青天")) < 0);
        assertTrue("包(b) < 我(w)", cmp().compare(node("包青天"), node("我的关卡集")) < 0);
        assertTrue("我(w) < 新(x)", cmp().compare(node("我的关卡集"), node("新关卡集")) < 0);
    }

    // ------------------------------------------------------------ 列表排序

    /** 走真实启动路径（{@code BoxManPC()} → {@code initAppEnvironment()} → 排序）。 */
    @Test
    public void extensionGroupIsSortedLikeThePhoneVersion() {
        clearExtensionGroup();
        for (String title : SHUFFLED) sql.add_T(GROUP_EXT, title, "", "");

        BoxManPC app = new BoxManPC();
        try {
            assertEquals("「关卡扩展」应按手机版规则排好", EXPECTED, titles(myMaps.mSets3));
        } finally {
            app.dispose();
        }
    }

    /**
     * 原版只排扩展组，入门 / 进阶 / 花样三组保持库里的顺序 —— 这里用一个「排起来会跑到最前、
     * 但入库最晚」的标题来验证：没被排过才会留在末尾。
     */
    @Test
    public void builtInGroupsKeepDatabaseOrder() {
        final String title = "AAA_P38";     // 按名称排会跑到最前，按入库顺序会留在最后
        if (sql.find_Set(title) <= 0) sql.add_T(GROUP_BEGINNER, title, "", "");

        BoxManPC app = new BoxManPC();
        try {
            List<String> beginner = titles(myMaps.mSets0);
            assertEquals("入门关卡组不应被排序（原版只排扩展组）",
                    title, beginner.get(beginner.size() - 1));
        } finally {
            app.dispose();
        }
    }

    // ------------------------------------------------------------ 辅助

    /** 把「关卡扩展」里现有的集合（含自动补建的「新关卡集」）清空。 */
    private static void clearExtensionGroup() {
        for (set_Node nd : sql.get_GroupList(GROUP_EXT)) sql.del_T(nd.id);
        assertTrue("扩展组应已清空", sql.get_GroupList(GROUP_EXT).isEmpty());
    }

    private static List<String> titles(List<set_Node> list) {
        List<String> out = new ArrayList<String>();
        for (set_Node nd : list) out.add(nd.title);
        return out;
    }
}
