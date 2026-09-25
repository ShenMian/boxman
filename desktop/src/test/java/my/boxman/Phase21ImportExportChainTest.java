package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * 阶段 E：把「导入...」「导出...」两个主菜单项从 PC 自造的 {@code JFileChooser} /
 * {@code ExportDialog} 换回原版的入口链 ——
 * {@code BoxMan.sel_Set()}（{@code import_dialog3.xml}）+ {@code mySplitLevelsFragment}，
 * 以及 {@code BoxMan.sel_Set2()}（{@code export_dialog3.xml}）+ {@code myExportFragment}。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>入口链的「载体」对了（无裸 {@code JFileChooser}、菜单接到 {@code sel_Set/sel_Set2}）；</li>
 *   <li>两个对话框的初值与原版代码的有效初值一致（含 {@code m_All.setChecked(false)}、
 *       {@code m_XSB.setChecked(true)}、{@code m_ReWrite.setChecked(true)} 这几处
 *       「代码覆盖 XML」的怪癖）；</li>
 *   <li>解析/导出本体按原版口径工作（跳重复关卡集、只导入勾选项、覆盖/跳过同名文档、
 *       正负 id 约定）。
 * </ol>
 */
public class Phase21ImportExportChainTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 能通过 mapNormalize 的最小夹具（≥3×3 且箱子数 == 目标数）。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    private static mySQLite sql;

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase21").getAbsolutePath();
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();

        // mySplitLevelsFragment 建好关卡集后要把它塞进 mSets3（原版 expAdapter 的数据源）
        myMaps.mSets0 = sql.get_GroupList(0);
        myMaps.mSets1 = sql.get_GroupList(1);
        myMaps.mSets2 = sql.get_GroupList(2);
        myMaps.mSets3 = sql.get_GroupList(3);

        new File(myMaps.sRoot + "/导入/").mkdirs();
        new File(myMaps.sRoot + "/导出/").mkdirs();
    }

    @Before
    public void setUp() {
        myMaps.isXSB = true;
        myMaps.isLurd = false;
        myMaps.isComment = false;
        myMaps.m_Code = 0;
        myMaps.m_Nums = new int[]{0, 0, 0, 0};
        myMaps.m_Set_id = -1;
        myMaps.m_lstMaps.clear();
        myMaps.m_setName = null;
    }

    // ------------------------------------------------------------ 入口链载体

    @Test
    public void testImportEntryNoLongerUsesBareFileChooser() {
        String src = readSource("BoxManPC.java");
        assertFalse("「导入...」必须走原版 sel_Set()，不能再有裸 JFileChooser",
                src.contains("JFileChooser"));
        assertFalse("PC 自造的 chooseImportFile() 应已删除", src.contains("chooseImportFile"));
        assertTrue("菜单应接 sel_Set", src.contains("this::sel_Set"));
        assertTrue("菜单应接 sel_Set2", src.contains("this::sel_Set2"));
    }

    @Test
    public void testPcInventedExportAndSplitDialogsAreGone() {
        File dir = new File("src/main/java/my/boxman");
        assertFalse("ExportDialog 已被 sel_Set2 + myExportFragment 取代",
                new File(dir, "ExportDialog.java").exists());
        assertFalse("SplitDialog 已被 mySplitLevelsFragment 取代",
                new File(dir, "SplitDialog.java").exists());
    }

    @Test
    public void testOriginalImportExportFragmentsArePorted() {
        File dir = new File("src/main/java/my/boxman");
        assertTrue("mySplitLevelsFragment 应已移植",
                new File(dir, "mySplitLevelsFragment.java").exists());
        assertTrue("myExportFragment 应已移植",
                new File(dir, "myExportFragment.java").exists());
    }

    // ------------------------------------------------------------ 对话框初值

    @Test
    public void testImportDialogDefaultsMatchOriginalLayout() throws Exception {
        writeImportDoc("P21_Dlg.xsb", LEVEL + "\nTitle: D1\n");

        BoxManPC pc = new BoxManPC();
        HoloAlertDialog dlg = pc.buildImportDialog();
        assertNotNull("有文档时「导入」对话框应搭起来", dlg);

        // 原版代码：m_All.setChecked(false) 覆盖了 XML 的 checked="true"
        assertFalse("「全选」初值应为否", findCheckBox(dlg, "全选").isSelected());
        // 原版代码：m_XSB.setChecked(true)
        assertTrue("「XSB」初值应为是", findCheckBox(dlg, "XSB").isSelected());
        assertFalse("「Lurd」初值应为否", findCheckBox(dlg, "Lurd").isSelected());
        assertTrue("编码初值应为「自动」", findRadio(dlg, "自动").isSelected());
        assertFalse(findRadio(dlg, "GBK").isSelected());
        assertFalse(findRadio(dlg, "UTF-8").isSelected());

        // 原版 sel_Set() 把每个 item 都 setItemChecked(false)
        assertNotNull("sel_Set 应把列表挂到 myMaps.m_setName", myMaps.m_setName);
        assertEquals("列表初值应为全不选", 0, myMaps.m_setName.getSelectedIndices().length);
        assertEquals(0, myMaps.m_Code);

        dlg.dispose();
        pc.dispose();
    }

    @Test
    public void testExportDialogDefaultsMatchOriginalLayout() {
        ensureSetWithLevel("P21_ExpDlg");

        BoxManPC pc = new BoxManPC();
        HoloAlertDialog dlg = pc.buildExportDialog();
        assertNotNull("有关卡集时「导出」对话框应搭起来", dlg);

        assertTrue("「全选」初值应为是", findCheckBox(dlg, "全选").isSelected());
        assertTrue("「仅答案关卡」初值应为是（XML checked=true）",
                findCheckBox(dlg, "仅答案关卡").isSelected());
        assertFalse("「含答案」初值应为否", findCheckBox(dlg, "含答案").isSelected());
        assertFalse("「答案含备注」初值应为否", findCheckBox(dlg, "答案含备注").isSelected());
        // 原版代码：m_ReWrite.setChecked(true) 覆盖了 XML 的 checked="false"
        assertTrue("「覆盖同名文档」初值应为是", findCheckBox(dlg, "覆盖同名文档").isSelected());

        assertFalse("sel_Set2 开头会把 myMaps.isLurd 复位", myMaps.isLurd);
        // 原版全部 setItemChecked(k, true)
        assertNotNull(myMaps.m_setName);
        assertEquals(myMaps.m_setName.getModel().getSize(),
                myMaps.m_setName.getSelectedIndices().length);

        dlg.dispose();
        pc.dispose();
    }

    // ------------------------------------------------------------ 导入本体

    @Test
    public void testSplitLevelsFragmentImportsEveryCheckedDocument() throws Exception {
        deleteSet("P21_A");
        deleteSet("P21_B");
        writeImportDoc("P21_A.xsb", LEVEL + "\nTitle: A1\nAuthor: t\n");
        writeImportDoc("P21_B.xsb", LEVEL + "\nTitle: B1\n");

        ArrayList<String> files = new ArrayList<String>();
        files.add("P21_A.xsb");
        files.add("P21_B.xsb");
        myMaps.m_setName = selectAll(files);

        String inf = new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_FILE_LIST, files).runNow();

        long a = sql.find_Set("P21_A");
        long b = sql.find_Set("P21_B");
        assertTrue("P21_A 应已建集，inf=" + inf, a > 0);
        assertTrue("P21_B 应已建集，inf=" + inf, b > 0);
        assertTrue("统计里应有 2 个关卡，inf=" + inf, inf.contains("关卡数：2"));

        sql.get_Levels(a);
        assertEquals("P21_A 应有 1 个关卡", 1, myMaps.m_lstMaps.size());
        assertEquals("A1", myMaps.m_lstMaps.get(0).Title);
    }

    @Test
    public void testSplitLevelsFragmentSkipsAlreadyRegisteredSet() throws Exception {
        deleteSet("P21_Dup");
        writeImportDoc("P21_Dup.xsb", LEVEL + "\nTitle: D1\n");
        sql.add_T(3, "P21_Dup", "", "");   // 关卡集已登记

        ArrayList<String> files = new ArrayList<String>();
        files.add("P21_Dup.xsb");
        myMaps.m_setName = selectAll(files);

        String inf = new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_FILE_LIST, files).runNow();

        assertEquals("已登记的同名关卡集应整体跳过", "关卡集重复或无效！", inf);
    }

    @Test
    public void testSplitLevelsFragmentIgnoresUncheckedDocuments() throws Exception {
        deleteSet("P21_Skip");
        writeImportDoc("P21_Skip.xsb", LEVEL + "\nTitle: S1\n");

        ArrayList<String> files = new ArrayList<String>();
        files.add("P21_Skip.xsb");
        myMaps.m_setName = new JList<String>(files.toArray(new String[0]));
        myMaps.m_setName.clearSelection();

        String inf = new mySplitLevelsFragment(null, null,
                mySplitLevelsFragment.TYPE_FILE_LIST, files).runNow();

        assertEquals("关卡集重复或无效！", inf);
        assertEquals("未勾选的文档不应建集", -1, sql.find_Set("P21_Skip"));
    }

    @Test
    public void testSplitLevelsFragmentRejectsMissingArguments() {
        assertEquals("没有可解析的内容！",
                new mySplitLevelsFragment(null, null, -1, null).runNow());
    }

    // ------------------------------------------------------------ 导出本体

    @Test
    public void testExportFragmentWritesAndHonoursOverwriteFlag() throws Exception {
        long id = ensureSetWithLevel("P21_Exp");
        File out = new File(myMaps.sRoot + "/导出/P21_Exp.xsb");
        out.delete();

        String inf1 = new myExportFragment(null, null, false, false, false,
                new long[]{id}).runNow();
        assertTrue("首次导出应 OK，inf=" + inf1, inf1.contains("...OK"));
        assertTrue("导出文档应存在", out.exists());

        String inf2 = new myExportFragment(null, null, false, false, false,
                new long[]{id}).runNow();
        assertTrue("不允许覆盖时应跳过，inf=" + inf2, inf2.contains("...跳过"));

        String inf3 = new myExportFragment(null, null, false, false, true,
                new long[]{id}).runNow();
        assertTrue("允许覆盖时应写回，inf=" + inf3, inf3.contains("...覆盖"));
    }

    @Test
    public void testExportFragmentUsesTxtSuffixWhenLurdSelected() throws Exception {
        long id = ensureSetWithLevel("P21_Lurd");
        File out = new File(myMaps.sRoot + "/导出/P21_Lurd.txt");
        out.delete();

        String inf = new myExportFragment(null, null, false, true, false,
                new long[]{id}).runNow();

        assertTrue("勾了「含答案」应导出 .txt，inf=" + inf, inf.contains("P21_Lurd.txt"));
        assertTrue(out.exists());
    }

    @Test
    public void testExportFragmentMentionsOnlyAnswerDocument() throws Exception {
        long id = ensureSetWithLevel("P21_Ans");
        String inf = new myExportFragment(null, null, true, false, true,
                new long[]{id}).runNow();
        assertTrue("「仅答案关卡」应出现在统计里，inf=" + inf,
                inf.contains("仅有答案的关卡.xsb"));
    }

    @Test
    public void testExportFragmentSkipsNegativeSetIds() throws Exception {
        long id = ensureSetWithLevel("P21_Neg");
        File out = new File(myMaps.sRoot + "/导出/P21_Neg.xsb");
        out.delete();

        String inf = new myExportFragment(null, null, false, false, false,
                new long[]{-id}).runNow();

        assertTrue("负 id 表示未勾选，应计 0 个关卡集，inf=" + inf,
                inf.contains("共选择0个关卡集"));
        assertFalse("未勾选的关卡集不应产出文档", out.exists());
    }

    @Test
    public void testExportFragmentRejectsMissingSetList() {
        assertEquals("没有可导出的内容！",
                new myExportFragment(null, null, false, false, false, null).runNow());
    }

    // ------------------------------------------------------------ 辅助

    private static JList<String> selectAll(ArrayList<String> files) {
        JList<String> list = new JList<String>(files.toArray(new String[0]));
        list.setSelectionInterval(0, files.size() - 1);
        return list;
    }

    private static void writeImportDoc(String name, String body) throws Exception {
        File f = new File(myMaps.sRoot + "/导入/" + name);
        f.delete();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(body);
        }
    }

    private static void deleteSet(String title) {
        long id = sql.find_Set(title);
        if (id > 0) sql.del_T(id);
    }

    /** 建一个只含 1 个关卡的关卡集，返回它的 id。 */
    private static long ensureSetWithLevel(String title) {
        deleteSet(title);
        long id = sql.add_T(3, title, "P21 Author", "P21 Comment");
        assertTrue("关卡集应建成功", id > 0);
        long lid = sql.add_L(id, new mapNode(LEVEL, "P21 Level", "P21 Author", ""));
        assertTrue("关卡应入库", lid > 0);
        return id;
    }

    private static String readSource(String fileName) {
        for (String prefix : new String[]{"src/main/java/my/boxman/", "./", "../"}) {
            File f = new File(prefix + fileName);
            if (f.isFile()) {
                try {
                    byte[] buf = new byte[(int) f.length()];
                    try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        int n = 0;
                        while (n < buf.length) {
                            int r = in.read(buf, n, buf.length - n);
                            if (r < 0) break;
                            n += r;
                        }
                    }
                    return new String(buf, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // 落到下一个候选路径
                }
            }
        }
        fail("找不到源文件：" + fileName);
        return "";
    }

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JCheckBox && text.equals(((JCheckBox) c).getText())) {
                return (JCheckBox) c;
            }
            if (c instanceof Container) {
                JCheckBox found = findCheckBox((Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JRadioButton findRadio(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JRadioButton && text.equals(((JRadioButton) c).getText())) {
                return (JRadioButton) c;
            }
            if (c instanceof Container) {
                JRadioButton found = findRadio((Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
