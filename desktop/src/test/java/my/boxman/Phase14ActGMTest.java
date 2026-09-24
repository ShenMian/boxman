package my.boxman;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 D-2 验收：原版「动作管理 / 导入」Activity（{@code myActGMView}，824 行）的还原。
 *
 * <p>改写前 PC 版只有 247 行，差距有三块：
 * <ol>
 *   <li><b>菜单</b>：自造的 {@code JMenuBar}「动作变换」只有 5 项，标题少了「（Lurd）」后缀，
 *       {@code act_gm.xml} 的「录制动作 / “宏”功能说明 / “导入”说明」3 项**全缺**，
 *       ActionBar 的标题「导入」和返回键也没有。</li>
 *   <li><b>对话框</b>：「加载」原版 14 项、PC 只有 3 项；「保存到」原版 11 项、PC 只有 3 项；
 *       「宏」名称框、覆写确认框、暂存确认框全缺。</li>
 *   <li><b>逻辑</b>：录制模式（{@code isRecording} + act2 寄存器）、寄存器 reg0~reg9、
 *       「导入/」文档读入、「宏/」文档写出、{@code onResume} 的剪切板加载、执行前的注释剥离，全缺。</li>
 * </ol>
 *
 * <p>本用例把菜单项与顺序、5 张字符映射表、注释剥离规则、寄存器/文档读写全部锁住。
 */
public class Phase14ActGMTest {

    /** 兜底：任何一条用例若弹出了模态框都会挂住 EDT，这里把它变成「失败」而不是「卡死」。 */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    private static File home;

    private myActGMView win;
    private String savedRoot, savedPath;
    private boolean savedRecording, savedMacroDebug, savedMapChange;
    private boolean savedActionIsPos, savedActionIsTrun;
    private String[] savedAction;
    private boolean savedActionIsRedy;
    private List<String> savedFileList;

    @BeforeClass
    public static void setUpClass() throws Exception {
        home = new File(System.getProperty("user.dir"), "build/ui-snapshot/actgm-home");
        deleteRecursively(home);
        new File(home, "宏").mkdirs();
        new File(home, "导入").mkdirs();
        // 「导入/」下放一个带 YASS 风格头的文档，验证 readFile 真的过了 loadLURD
        try (FileOutputStream fos = new FileOutputStream(new File(home, "导入/lurd.txt"))) {
            fos.write("Solution (moves 4): lUrD".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 每次开跑都从干净目录开始：否则「宏文件已存在」会弹出模态覆写框，把测试挂死。 */
    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteRecursively(k);
        }
        f.delete();
    }

    @Before
    public void setUp() {
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        savedRecording = myMaps.isRecording;
        savedMacroDebug = myMaps.isMacroDebug;
        savedMapChange = myMaps.m_MapChange;
        savedActionIsPos = myMaps.m_ActionIsPos;
        savedActionIsTrun = myMaps.m_ActionIsTrun;
        savedAction = myMaps.sAction;
        savedActionIsRedy = myMaps.m_ActionIsRedy;
        savedFileList = new ArrayList<String>(myMaps.mFile_List);

        myMaps.sRoot = home.getAbsolutePath();
        myMaps.sPath = "/";
        myMaps.isRecording = false;
        myMaps.m_MapChange = false;      // 别让 onResume 逻辑去读真剪切板
        myMaps.isMacroDebug = true;      // 构造器应把它清掉
    }

    @After
    public void tearDown() {
        MyToast.dismiss();
        if (win != null) {
            win.dispose();
            win = null;
        }
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
        myMaps.isRecording = savedRecording;
        myMaps.isMacroDebug = savedMacroDebug;
        myMaps.m_MapChange = savedMapChange;
        myMaps.m_ActionIsPos = savedActionIsPos;
        myMaps.m_ActionIsTrun = savedActionIsTrun;
        myMaps.sAction = savedAction;
        myMaps.m_ActionIsRedy = savedActionIsRedy;
        myMaps.mFile_List.clear();
        myMaps.mFile_List.addAll(savedFileList);
    }

    private myActGMView open(boolean isBK) {
        win = new myActGMView(null, isBK);
        return win;
    }

    /**
     * 排空 EDT。{@link MyToast#showToast} 在非 EDT 线程上会 {@code invokeLater}，
     * 所以触发后必须先把队列跑完，{@link MyToast#currentToastText()} 才是刚弹的那条。
     */
    private static void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void assertToast(String expected) throws Exception {
        drainEdt();
        assertEquals(expected, MyToast.currentToastText());
        MyToast.dismiss();
    }

    /**
     * 把编辑区文字直接塞进去（{@code setText} 会触发 DocumentListener → {@code flg = true}），
     * 再清掉「已改动」标记，避免 {@code doExecute()} 末尾弹出模态的「是否暂存」框把用例挂死。
     */
    private void setActionQuietly(myActGMView w, String text) {
        w.et_Action.setText(text);
        w.setDirty(false);
    }

    // ---------------------------------------------------------------- ActionBar

    @Test
    public void testActionBarTitleAndUp() {
        myActGMView w = open(false);
        assertEquals("导入", w.getActionBar().getBarTitle());
        assertTrue("原版 setDisplayHomeAsUpEnabled(true)", w.getActionBar().isUpEnabled());
    }

    @Test
    public void testRecordingIsABarActionHiddenWhenBackward() {
        // 原版 act_recording 是 showAsAction="always" → ActionBar 文字按钮
        assertEquals(1, open(false).getActionBar().getBarActionCount());
        assertTrue(open(false).getActionBar().isBarActionVisible("录制动作"));
        // onCreateOptionsMenu：if (is_BK) menu.getItem(0).setVisible(false)
        assertFalse(open(true).getActionBar().isBarActionVisible("录制动作"));
    }

    @Test
    public void testOverflowMenuMatchesActGmXml() {
        // res/menu/act_gm.xml 去掉 showAsAction 的那一项后，溢出项顺序：
        // 左旋90度 / 右旋90度 / 右旋180度 / 左右翻转 / 上下翻转 / “宏”功能说明 / “导入”说明
        assertEquals(java.util.Arrays.asList(
                "左旋90度（Lurd）",
                "右旋90度（Lurd）",
                "右旋180度（Lurd）",
                "左右翻转（Lurd）",
                "上下翻转（Lurd）",
                "“宏”功能说明",
                "“导入”说明"),
                open(false).getActionBar().getActionTitles());
        // 有溢出项 → 画 ⋮
        assertTrue(open(false).getActionBar().isOverflowVisible());
        // 逆推时溢出项一个不少（原版只隐藏 getItem(0)）
        assertEquals(7, open(true).getActionBar().getActionTitles().size());
    }

    // ---------------------------------------------------------------- 5 张字符映射表

    @Test
    public void testTransformL90() {
        // l→d u→l r→u d→r（原版 703~710 行）
        myActGMView w = open(false);
        w.et_Action.setText("lurdlurdlurdLURDLURD");
        w.transformLURD("L90");
        assertEquals("dlurdlurdlurDLURDLUR", w.et_Action.getText());
    }

    @Test
    public void testTransformR90() {
        // l→u u→r r→d d→l（原版 723~730 行）
        myActGMView w = open(false);
        w.et_Action.setText("lurdLURD");
        w.transformLURD("R90");
        assertEquals("urdlURDL", w.et_Action.getText());
    }

    @Test
    public void testTransform180() {
        // l↔r、u↔d（原版 743~750 行）
        myActGMView w = open(false);
        w.et_Action.setText("lurdLURD");
        w.transformLURD("180");
        assertEquals("rdluRDLU", w.et_Action.getText());
    }

    @Test
    public void testTransformLeftRightKeepsUpDown() {
        // 左右翻转：l<->r，u/d 不变（原版 765~770 行）
        myActGMView w = open(false);
        w.et_Action.setText("lurdLURD");
        w.transformLURD("LR");
        assertEquals("ruldRULD".toLowerCase(), w.et_Action.getText().toLowerCase());
        assertEquals("r", w.et_Action.getText().substring(0, 1));
        assertEquals("u", w.et_Action.getText().substring(1, 2));
        assertEquals("d", w.et_Action.getText().substring(3, 4));
    }

    @Test
    public void testTransformUpDownKeepsLeftRight() {
        // 上下翻转：u<->d，l/r 不变（原版 783~790 行）
        myActGMView w = open(false);
        w.et_Action.setText("lurdLURD");
        w.transformLURD("UD");
        assertEquals("ldruLDRU", w.et_Action.getText());
    }

    @Test
    public void testTransformRejectsNonLurd() throws Exception {
        myActGMView w = open(false);
        w.et_Action.setText("lurd;comment");
        w.transformLURD("L90");
        assertEquals("非规范动作串应原样保留", "lurd;comment", w.et_Action.getText());
        assertToast("仅支持规范的动作字符！");
    }

    @Test
    public void testMenuClickDispatchesToTransform() {
        myActGMView w = open(false);
        w.et_Action.setText("lurd");
        w.clickMenu("上下翻转（Lurd）");
        assertEquals("ldru", w.et_Action.getText());
    }

    // ---------------------------------------------------------------- 执行（注释剥离）

    @Test
    public void testExecuteStripsCommentAfterLurd() {
        myActGMView w = open(false);
        setActionQuietly(w, "lurd; 这是注释");
        w.doExecuteForTest();
        assertArrayEquals(new String[]{"lurd"}, myMaps.sAction);
        assertTrue(myMaps.m_ActionIsRedy);
    }

    @Test
    public void testExecuteStripsInlineBlockThenComment() {
        // 原版：先找 '<'，若有 '>' 再找其后的 ';'（行内块后面的注释符号）
        myActGMView w = open(false);
        setActionQuietly(w, "<lurd>; 注释");
        w.doExecuteForTest();
        assertArrayEquals(new String[]{"<lurd>"}, myMaps.sAction);
    }

    @Test
    public void testExecuteWholeLineCommentBecomesEmpty() {
        myActGMView w = open(false);
        setActionQuietly(w, "; 整行注释");
        w.doExecuteForTest();
        assertArrayEquals(new String[]{""}, myMaps.sAction);
    }

    @Test
    public void testExecuteMultiLineSplitsAndTabsBecomeSpaces() {
        myActGMView w = open(false);
        setActionQuietly(w, "lurd\n\n\turdl; tail");
        w.doExecuteForTest();
        assertArrayEquals(new String[]{"lurd", "", "urdl"}, myMaps.sAction);
    }

    @Test
    public void testExecuteWrapsPlainLurdWithIgnoreCaseBraces() {
        // 原版：对于非“宏”，自动加上忽略大小写命令 {"..."}~
        myActGMView w = open(false);
        setActionQuietly(w, "lurd");
        w.doExecuteForTest();
        assertArrayEquals(new String[]{"{lurd}~"}, myMaps.sAction);
    }

    @Test
    public void testExecuteRejectsNonLurdInBackwardMode() throws Exception {
        // 逆推只接受「标准动作字符」（isLURD2：可含 [ ] 数字 , -），字母注释不算
        myActGMView w = open(true);
        setActionQuietly(w, "lurd abc");
        w.doExecuteForTest();
        assertToast("逆推请使用标准动作字符！");
        assertFalse("被拒时不应置 m_ActionIsRedy", myMaps.m_ActionIsRedy);
    }

    // ---------------------------------------------------------------- 寄存器

    @Test
    public void testRegisterRoundTrip() {
        myActGMView w = open(false);
        w.saveActForTest("reg1", "lurdLURD");
        assertEquals("lurdLURD", w.loadActForTest("reg1"));
    }

    @Test
    public void testRegisterRejectsInvalidCharsExceptReg0() throws Exception {
        myActGMView w = open(false);
        // reg0 不校验（原版 !name.equals("reg0") 才校验）
        w.saveActForTest("reg0", "随便什么 都可以");
        assertEquals("随便什么 都可以", w.loadActForTest("reg0"));
        // reg1..reg9 校验：非法字符保存失败
        w.saveActForTest("reg2", "lurd;注释");
        assertEquals("", w.loadActForTest("reg2"));
        assertToast("遇到无效字符，保存失败!");
    }

    @Test
    public void testRecordingModeClearsAct2Silently() {
        myActGMView w = open(false);
        w.saveActForTest("reg3", "lurd");
        myMaps.isRecording = true;
        w.saveActForTest("act2", "");          // 录制模式下的清理动作：不弹提示
        assertEquals("", w.loadActForTest("act2"));
        myMaps.isRecording = false;
    }

    // ---------------------------------------------------------------- 文档读写

    @Test
    public void testReadFileFromImportDir() {
        myActGMView w = open(false);
        // 「导入/lurd.txt」内容是 "Solution (moves 4): lUrD"
        // readFile 会把整份文档交给 myMaps.loadLURD(.., 0)，解析出第一个动作段
        assertEquals("lUrD", w.readFileForTest("lurd.txt"));
    }

    @Test
    public void testReadMissingFileToasts() throws Exception {
        myActGMView w = open(false);
        assertEquals("", w.readFileForTest("no_such_file.txt"));
        assertToast("文档中的数据无效！");
    }

    @Test
    public void testWriteMacroAppendsTxtSuffix() throws Exception {
        File target = new File(home, "宏/myMacro.txt");
        target.delete();                     // 存在就会弹模态覆写框，先删掉
        myActGMView w = open(false);
        w.et_Action.setText("lurdLURD");
        w.writeMacroForTest("myMacro");      // 没有扩展名 → 自动补 .txt
        assertTrue("应写到 宏/myMacro.txt", target.isFile());
        assertEquals("lurdLURD", new String(java.nio.file.Files.readAllBytes(target.toPath()),
                StandardCharsets.UTF_8));
    }

    @Test
    public void testWriteMacroKeepsExistingTxtSuffix() {
        File target = new File(home, "宏/keep.txt");
        target.delete();
        myActGMView w = open(false);
        w.et_Action.setText("lurd");
        w.writeMacroForTest("keep.txt");
        assertTrue(target.isFile());
        assertFalse("不该出现 keep.txt.txt", new File(home, "宏/keep.txt.txt").exists());
    }

    // ---------------------------------------------------------------- 布局 / onCreate

    @Test
    public void testLayoutMatchesActionManageXml() {
        myActGMView w = open(false);
        // 动作编辑区：ScrollView 高 415dp、EditText 用等宽字体
        assertEquals(415, w.et_Action.getParent().getParent().getPreferredSize().height);
        assertEquals("Monospaced", w.et_Action.getFont().getFamily());
        // 三个复选框的初始勾选状态 = action_manage.xml 的 android:checked
        assertTrue("cb_curPos android:checked=\"true\"", w.et_curPos.isSelected());
        assertFalse("cb_curTrun android:checked=\"false\"", w.et_curTrun.isSelected());
        assertFalse("cb_PreEdit android:checked=\"false\"", w.et_PreEdit.isSelected());
        // 6 个按钮
        assertEquals("加载", w.btLoadAct.getText());
        assertEquals("存入", w.btSaveAct.getText());
        assertEquals("清空", w.btClear.getText());
        assertEquals("暂存", w.btSave_t.getText());
        assertEquals("执行", w.btDO.getText());
    }

    @Test
    public void testOnCreateClearsRecordingAndMacroDebugFlags() {
        myMaps.isRecording = true;
        myMaps.isMacroDebug = true;
        myActGMView w = open(false);
        // 原版 onCreate：myMaps.isRecording = false; myMaps.isMacroDebug = false;
        assertFalse(myMaps.isRecording);
        assertFalse(myMaps.isMacroDebug);
        assertFalse("构造完不应处于「已改动」状态", w.isDirty());
    }

    @Test
    public void testOnCreatePullsAct2RegisterWhenRecording() {
        // 先在非录制状态下写一个 act2 寄存器
        myActGMView seed = open(false);
        seed.saveActForTest("act2", "urdl");
        seed.dispose();

        myMaps.isRecording = true;
        myActGMView w = open(false);
        assertEquals("录制模式应把 act2 灌进编辑区", "urdl", w.et_Action.getText());
        assertEquals("用后要清空", "", w.loadActForTest("act2"));
    }

    @Test
    public void testOnCreateSyncsExecuteCheckboxesToStatics() {
        myActGMView w = open(false);
        // 原版 onCreate：myMaps.m_ActionIsPos = et_curPos.isChecked()（XML 默认 true）
        assertTrue(myMaps.m_ActionIsPos);
        assertFalse(myMaps.m_ActionIsTrun);
    }

    @Test
    public void testClickingExecuteCheckboxUpdatesStatics() {
        myActGMView w = open(false);
        w.et_curTrun.setSelected(true);
        w.et_curTrun.doClick();
        // doClick 会翻转 → false
        assertFalse(myMaps.m_ActionIsTrun);
        w.et_curPos.doClick();
        assertFalse(myMaps.m_ActionIsPos);
    }
}
