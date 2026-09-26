package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloPopupMenu;
import my.boxman.compat.sqlite.Cursor;
import org.junit.After;
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

import static org.junit.Assert.*;

/**
 * 阶段 G ⑦：{@code BoxMan}（关卡集列表）的 10 项上下文菜单 ——
 * 原版 {@code BoxMan.java:1444-1465} 的 {@code onCreateContextMenu()} 与
 * {@code onContextItemSelected()}（1464-1712）。
 *
 * <p>覆盖四件事：
 * <ol>
 *   <li>菜单项的<b>标题与顺序</b>与原版逐字一致，且可见性矩阵正确
 *       （{@code 打开/导出.../清理状态记录.../删除答案.../详细...} 恒可见，
 *        其余 5 项只在扩展关卡组出现）；</li>
 *   <li>每个分支的<b>行为</b>（打开 / 清理状态 / 删除答案 / 重命名 / 删除 / 添加关卡 / 详细）；</li>
 *   <li>「导出...」走的是 {@code export2_dialog.xml} 那一套选项（含答案 / 答案含备注 /
 *       覆盖同名文档），而不是主菜单那套 {@code export_dialog3.xml}；</li>
 *   <li>「添加比赛关卡」的 {@code get_uil_dialog.xml} 两栏 + {@code url_Num} 解析 +
 *       {@code myJson()} 落库口径。</li>
 * </ol>
 *
 * <p>「点了会开窗」的动作方法都是模态框，真弹出来会把用例挂死 —— 所以统一把
 * {@link BoxManPC#dialogShower} 换成 {@link ShownDialog}（只记录、不显示），
 * 这样既能走完整条分支，又不会卡住（见 {@code TEST_NOTES.md} 的「模态框」一条）。
 */
public class Phase25BoxManContextMenuTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 能通过 mapNormalize 的最小夹具（≥3×3 且箱子数 == 目标数）。 */
    private static final String LEVEL = "#####\n#@$.#\n#####";

    /** 原版 {@code onCreateContextMenu()} 的 10 项，逐字照抄 {@code BoxMan.java:1447-1462}。 */
    private static final String[] ORIGINAL_ITEMS = {
            "打开", "导出...", "清理状态记录...", "删除答案...",
            "重命名...", "删除", "添加关卡(文档)...", "添加关卡(剪切板)...",
            "添加比赛关卡(sokoban.ws)", "详细..."};

    private static mySQLite sql;
    private BoxManPC app;

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("java.awt.headless", "false");
        myMaps.sRoot = new File("build/test_boxman_phase25").getAbsolutePath();
        myMaps.sPath = "/";
        myMaps.m_nWinWidth = 370;
        myMaps.m_nWinHeight = 780;

        sql = mySQLite.getInstance();
        sql.openDataBase();
        mySQLite.m_SQL = sql;
        myMaps.loadSkins();

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
        myMaps.curJi = false;
        myMaps.uil = "https://sokoban.cn/";
        myMaps.sFile = null;

        app = new BoxManPC();
        drainEdt();
    }

    @After
    public void tearDown() {
        // ⚠️ myMaps 是全局静态：m_Sets[0]/[1] 是「上次所在的关卡组」，curJi 是「正在进关卡集」的闸门。
        //    本类会刻意把它们改掉，必须还回去 —— 否则同 JVM 里排在后面的用例（如 Phase3 的
        //    「默认展开第 0 组」）会看到被污染的初值。
        myMaps.m_Sets[0] = 0;
        myMaps.m_Sets[1] = 0;
        myMaps.curJi = false;
        if (app != null) app.dispose();
        MyToast.dismiss();
    }

    // ------------------------------------------------------------ 菜单载体与标题

    @Test
    public void testContextItemTitlesMatchOriginal() {
        assertArrayEquals("上下文菜单的 10 项标题与顺序必须与原版逐字一致",
                ORIGINAL_ITEMS, BoxManPC.CONTEXT_ITEMS);
    }

    @Test
    public void testContextMenuHasTenItems() {
        app.setContextPosition(3, 0);
        JPopupMenu menu = app.buildContextMenu();
        assertNotNull(menu);
        assertEquals("原版 onCreateContextMenu 共 10 项", 10, HoloPopupMenu.itemCount(menu));
        // Android 的上下文菜单与 ActionBar 溢出菜单共用 popup_menu_holo_dark 样式 → 必须走 HoloPopupMenu
        assertSame("菜单项载体必须是 HoloPopupMenu.Row", HoloPopupMenu.class,
                menu.getComponent(0).getClass().getEnclosingClass());
    }

    @Test
    public void testExtendedGroupShowsAllTenItems() {
        app.setContextPosition(3, 0);
        JPopupMenu menu = app.buildContextMenu();
        for (String title : ORIGINAL_ITEMS) {
            assertTrue("扩展关卡组下「" + title + "」应可见", HoloPopupMenu.isVisible(menu, title));
        }
    }

    @Test
    public void testBuiltInGroupHidesTheFiveExtendedOnlyItems() {
        app.setContextPosition(0, 0);
        JPopupMenu menu = app.buildContextMenu();

        for (String title : new String[]{"打开", "导出...", "清理状态记录...", "删除答案...", "详细..."}) {
            assertTrue("内置关卡组下「" + title + "」应可见", HoloPopupMenu.isVisible(menu, title));
        }
        for (String title : new String[]{"重命名...", "删除", "添加关卡(文档)...",
                "添加关卡(剪切板)...", "添加比赛关卡(sokoban.ws)"}) {
            assertFalse("内置关卡组下「" + title + "」不应出现（原版 if (groupPos > 2)）",
                    HoloPopupMenu.isVisible(menu, title));
        }
    }

    @Test
    public void testSecondAndThirdGroupsAlsoHideTheExtendedOnlyItems() {
        for (int g = 0; g <= 2; g++) {
            app.setContextPosition(g, 0);
            JPopupMenu menu = app.buildContextMenu();
            assertFalse("第 " + g + " 组不该有「重命名...」", HoloPopupMenu.isVisible(menu, "重命名..."));
        }
    }

    @Test
    public void testContextMenuRemembersThePosition() {
        // 原版 onCreateContextMenu 末尾：myMaps.m_Sets[0] = groupPos; myMaps.m_Sets[1] = childPos;
        app.setContextPosition(2, 1);
        app.buildContextMenu();
        assertEquals("m_Sets[0] 应记住组别下标", 2, myMaps.m_Sets[0]);
        assertEquals("m_Sets[1] 应记住关卡集下标", 1, myMaps.m_Sets[1]);
    }

    // ------------------------------------------------------------ case 1「打开」

    @Test
    public void testLoadLevelsLoadsTheSetByPosition() {
        long id = ensureSet("P25_Load");
        int child = indexOfSet3(id);

        app.loadLevels(3, child);

        assertEquals("loadLevels 应把 sFile 设为关卡集名", "P25_Load", myMaps.sFile);
        assertEquals("get_Set 应把 m_Set_id 设成该关卡集", id, myMaps.m_Set_id);
        assertEquals("应装载到 1 个关卡", 1, myMaps.m_lstMaps.size());
        assertEquals("P25 Level", myMaps.m_lstMaps.get(0).Title);
    }

    @Test
    public void testLoadLevelsIgnoresInvalidPosition() {
        app.loadLevels(3, 9999);
        assertNull("越界的 childPos 不该改 sFile", myMaps.sFile);
    }

    @Test
    public void testBrowLevelsIsReentrancyGuarded() {
        // 原版 browLevels 开头：if (myMaps.curJi) return;
        ensureSet("P25_Reentry");
        myMaps.curJi = true;
        myMaps.sFile = "哨兵";

        app.browLevels(3, 0);

        assertEquals("curJi 为真时应直接返回", "哨兵", myMaps.sFile);
    }

    // ------------------------------------------------------------ case 3「清理状态记录...」

    @Test
    public void testClearSetStateRemovesSavedStates() {
        long id = ensureSet("P25_Clear");
        long levelId = lastLevelId(id);
        long key = lastLevelKey(id);
        sql.add_S(levelId, 0, 5, 2, 0, 0, 0, 0, "uulldd", "", key, 0, "", "");
        assertTrue("应已存入一条状态", countState(levelId, 0) >= 1);

        app.clearSetStateNow(id);

        assertEquals("clear_S 应清掉全部状态（G_Solution != 1）", 0, countState(levelId, 0));
        assertEquals("clear_S 不动答案（G_Solution = 1）", 0, countState(levelId, 1));
    }

    @Test
    public void testClearSetStateAsksForConfirmation() {
        long id = ensureSet("P25_ClearDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.clearSetState();

        assertTrue("原版用 AlertDialog「状态清理」确认，实际：" + shown,
                shown.is(HoloConfirmDialog.class));
        shown.dispose();
    }

    // ------------------------------------------------------------ case 4「删除答案...」

    @Test
    public void testDeleteSetAnswersResetsSolvedFlagButKeepsStates() {
        long id = ensureSet("P25_DelAns");
        long levelId = lastLevelId(id);
        long key = lastLevelKey(id);
        // 先制造一条状态，用来证明「删除答案」不会连状态一起删
        sql.add_S(levelId, 0, 5, 2, 0, 0, 0, 0, "uulldd", "", key, 0, "", "");
        sql.Set_L_Solved(key, 1, false);
        assertEquals("应登记为已通关", 1, sql.count_Sovled(id));

        app.deleteSetAnswersNow(id);

        assertEquals("del_T_Ans 应把已通关标记清零", 0, sql.count_Sovled(id));
        assertTrue("del_T_Ans 不该删状态（G_Solution = 0）", countState(levelId, 0) >= 1);
    }

    @Test
    public void testDeleteSetAnswersAsksForConfirmation() {
        long id = ensureSet("P25_DelAnsDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.deleteSetAnswers();

        assertTrue("原版用 AlertDialog「提醒」确认，实际：" + shown,
                shown.is(HoloConfirmDialog.class));
        shown.dispose();
    }

    // ------------------------------------------------------------ case 5「重命名...」

    @Test
    public void testRenameRejectsEmptyName() {
        long id = ensureSet("P25_RenameEmpty");
        set_Node nd = nodeOfSet3(id);

        assertFalse("空名不该通过", app.applyRename(nd, ""));
        assertEquals("库里名字不该变", "P25_RenameEmpty", titleOfSet(id));
    }

    @Test
    public void testRenameRejectsDuplicateName() {
        ensureSet("P25_RenameA");
        long b = ensureSet("P25_RenameB");
        set_Node nd = nodeOfSet3(b);

        assertFalse("与别的关卡集重名不该通过", app.applyRename(nd, "P25_RenameA"));
        assertEquals("库里名字不该变", "P25_RenameB", titleOfSet(b));
    }

    @Test
    public void testRenameAppliesAndRefreshesTheTree() {
        long id = ensureSet("P25_RenameOld");
        purgeSet("P25_RenameNew");   // 上一次跑留下的目标名，否则会被判成重名
        set_Node nd = nodeOfSet3(id);
        myMaps.m_Sets[0] = 3;   // 让 refreshTree 展开扩展组，子项才会出现在可见行里

        assertTrue("正常改名应成功", app.applyRename(nd, "P25_RenameNew"));

        assertEquals("库里应更新", "P25_RenameNew", titleOfSet(id));
        assertEquals("内存节点也应更新", "P25_RenameNew", nd.title);
        boolean listed = false;
        for (String row : app.getVisibleRowTexts()) {
            if (row.startsWith("P25_RenameNew ")) listed = true;
        }
        assertTrue("刷新后的列表应显示新名字", listed);
    }

    @Test
    public void testRenameOpensDialogPrefilledWithCurrentName() {
        long id = ensureSet("P25_RenameDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.reName();

        assertNotNull("「重命名...」应弹出输入框", shown.get());
        JTextField et = findTextField(shown.get());
        assertNotNull("应有一个输入框", et);
        assertEquals("应预填当前名称", "P25_RenameDlg", et.getText());
        assertEquals("原版 reName 会顺手把 sFile 设成该关卡集名", "P25_RenameDlg", myMaps.sFile);
        shown.dispose();
    }

    @Test
    public void testRenameIgnoresInvalidPosition() {
        app.setContextPosition(3, 9999);
        ShownDialog shown = captureShownDialog();
        app.reName();
        assertNull("无效位置不该弹框", shown.get());
    }

    // ------------------------------------------------------------ case 6「删除」

    @Test
    public void testDeleteSetRemovesItFromDbAndExtendedGroup() {
        long id = ensureSet("P25_Delete");
        set_Node nd = nodeOfSet3(id);
        assertTrue(myMaps.mSets3.contains(nd));

        app.deleteSetNow(nd);
        app.refreshTree();

        assertEquals("库里应查不到", -1, sql.find_Set("P25_Delete"));
        assertFalse("内存列表也应移除", myMaps.mSets3.contains(nd));
    }

    @Test
    public void testDeleteSetAsksForConfirmation() {
        long id = ensureSet("P25_DelDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.deleteSet();

        assertTrue("原版用 AlertDialog「提醒」确认，实际：" + shown,
                shown.is(HoloConfirmDialog.class));
        shown.dispose();
    }

    // ------------------------------------------------------------ case 2「导出...」

    @Test
    public void testExportOneSetBuildsExport2DialogWithOriginalDefaults() {
        long id = ensureSet("P25_Export");
        app.setContextPosition(3, indexOfSet3(id));

        HoloAlertDialog dlg = app.buildExportSetDialog();
        assertNotNull("「导出...」应能搭出对话框", dlg);

        // export2_dialog.xml 的 ex_lurd / ex_comment 都是 checked="false"；
        // 原版代码再补一句 m_ReWrite.setChecked(true)、m_LURD.setChecked(false)
        assertFalse("「含答案」初值应为否", findCheckBox(dlg, "含答案").isSelected());
        assertFalse("「答案含备注」初值应为否", findCheckBox(dlg, "答案含备注").isSelected());
        assertTrue("「覆盖同名文档」初值应为是", findCheckBox(dlg, "覆盖同名文档").isSelected());
        assertFalse("原版 case 2 开头会把 myMaps.isLurd 复位", myMaps.isLurd);
        assertEquals("sFile 应指向被点中的关卡集", "P25_Export", myMaps.sFile);

        dlg.dispose();
    }

    @Test
    public void testExportOneSetOpensTheDialog() {
        long id = ensureSet("P25_ExportDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.exportOneSet();

        assertNotNull("「导出...」应弹出 export2_dialog 那套选项框", shown.get());
        assertNotNull(findCheckBox(shown.get(), "含答案"));
        shown.dispose();
    }

    @Test
    public void testExportOneSetIgnoresInvalidPosition() {
        app.setContextPosition(3, 9999);
        assertNull("无效位置不该搭出对话框", app.buildExportSetDialog());
    }

    // ------------------------------------------------------------ case 7 / 8「添加关卡」

    @Test
    public void testAddLevelsFromDocTargetsTheChosenSet() throws Exception {
        long id = ensureSet("P25_AddDoc");
        writeImportDoc("P25_AddDoc.xsb", LEVEL + "\nTitle: D1\n");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.addLevelsFromDoc();

        assertEquals("原版 case 7：m_Set_id 应指向被点中的关卡集", id, myMaps.m_Set_id);
        assertTrue("应弹出「文档导入」框，实际：" + shown, shown.is(HoloChoiceDialog.class));

        // import_dialog.xml：cb_xsb / cb_lurd 的文案是 XSB / Lurd，
        // 且原版代码 m_XSB.setChecked(true) 覆盖 XML 的 checked="false"
        JDialog d = shown.get();
        assertTrue("「XSB」初值应为是", findCheckBox(d, "XSB").isSelected());
        assertFalse("「Lurd」初值应为否", findCheckBox(d, "Lurd").isSelected());
        assertTrue("编码初值应为「自动」", findRadio(d, "自动").isSelected());
        assertNotNull("应带「仅有一个关卡时，自动打开」",
                findCheckBox(d, "仅有一个关卡时，自动打开"));

        shown.dispose();
    }

    @Test
    public void testAddLevelsFromClipTargetsTheChosenSet() {
        long id = ensureSet("P25_AddClip");
        app.setContextPosition(3, indexOfSet3(id));
        captureShownDialog();   // read_Plate 可能真弹框，必须先换成「只记录」

        app.addLevelsFromClip();

        // 剪切板内容与环境有关（read_Plate 可能只弹 Toast），但落库目标必须先定好
        assertEquals("原版 case 8：m_Set_id 应指向被点中的关卡集", id, myMaps.m_Set_id);
    }

    @Test
    public void testClipImportOptionsHaveNoEncodingRow() {
        // import_dialog2.xml 只有「XSB / Lurd」+「仅有一个关卡时，自动打开」，
        // 没有编码单选（那是 import_dialog.xml 独有的）
        JComponent clip = app.buildImportOptions(false);
        assertNotNull("应能搭出剪切板那一套选项", clip);
        assertNull("剪切板导入没有编码单选", findRadio(clip, "自动"));
        assertNotNull("应带「XSB」", findCheckBox(clip, "XSB"));
        assertNotNull("应带「仅有一个关卡时，自动打开」",
                findCheckBox(clip, "仅有一个关卡时，自动打开"));
    }

    @Test
    public void testImportOptionsHonourTheLurdFlag() {
        myMaps.isLurd = true;
        JComponent clip = app.buildImportOptions(false);
        assertTrue("「Lurd」初值应取 myMaps.isLurd", findCheckBox(clip, "Lurd").isSelected());
    }

    @Test
    public void testImportOptionsForceXsbOnLikeTheOriginalCode() {
        // 原版 m_XSB.setChecked(true) 会触发监听器 → myMaps.isXSB 变 true
        myMaps.isXSB = false;
        app.buildImportOptions(false);
        assertTrue("m_XSB.setChecked(true) 应把 myMaps.isXSB 顶成 true", myMaps.isXSB);
    }

    @Test
    public void testDocImportDialogReturnsNullWhenNoDocuments() {
        File dir = new File(myMaps.sRoot + "/导入/");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        assertNull("「导入/」下没有文档时应只弹 Toast", app.buildDocImportDialog());
    }

    // ------------------------------------------------------------ case 9「添加比赛关卡」

    @Test
    public void testImportMatchLevelsOpensGetUilDialog() {
        long id = ensureSet("P25_MatchDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.importMatchLevels();

        assertTrue("原版弹 get_uil_dialog.xml（「导入比赛关卡」），实际：" + shown,
                shown.is(UrlInputDialog.class));
        UrlInputDialog u = (UrlInputDialog) shown.get();
        assertEquals("网站栏应预填 myMaps.uil", myMaps.uil, u.tfUil.getText());
        shown.dispose();
    }

    @Test
    public void testCompetitionEndpointMatchesOriginal() {
        String src = readSource("BoxManPC.java");
        assertTrue("原版 BoxMan.java:78 的 url 是 \"api/competition/\"",
                src.contains("\"api/competition/\""));
    }

    @Test
    public void testUrlNumParsing() {
        // 原版：int n = Integer.parseInt(num); url_Num = n > 0 ? "?id=" + n : "";
        assertEquals("?id=112", BoxManPC.computeUrlNum("112"));
        assertEquals("0 与负数都退化成空", "", BoxManPC.computeUrlNum("0"));
        assertEquals("", BoxManPC.computeUrlNum("-3"));
        assertEquals("解析失败也退化成空", "", BoxManPC.computeUrlNum("abc"));
        assertEquals("", BoxManPC.computeUrlNum(""));
        assertEquals("", BoxManPC.computeUrlNum(null));
    }

    @Test
    public void testUilNormalizationAppendsTrailingSlash() {
        assertEquals("https://sokoban.cn/", BoxManPC.normalizeUil("https://sokoban.cn"));
        assertEquals("已有斜杠不重复补", "https://sokoban.cn/", BoxManPC.normalizeUil("https://sokoban.cn/"));
        assertEquals("首尾空白要去掉", "https://x.cn/", BoxManPC.normalizeUil("  https://x.cn  "));
        assertEquals("空串退化成 \"/\"（原版这里会 charAt(-1) 崩）", "/", BoxManPC.normalizeUil(""));
        assertEquals("/", BoxManPC.normalizeUil(null));
    }

    @Test
    public void testSubmitCompetitionRecordsUrlNumUilAndTargetSet() {
        long id = ensureEmptySet("P25_Match");

        assertEquals("初始 url_Num 应为空", "", app.getUrlNum());

        app.prepareCompetition(id, "https://other.cn", "112");

        assertEquals("期号应解析成 ?id=", "?id=112", app.getUrlNum());
        assertEquals("网站末尾应补上 '/'", "https://other.cn/", myMaps.uil);
        assertEquals("目标关卡集应记为被点中的那个", id, myMaps.m_Set_id);
    }

    @Test
    public void testPrepareCompetitionLeavesUrlNumEmptyWhenNoNumber() {
        long id = ensureEmptySet("P25_MatchNoNum");
        app.prepareCompetition(id, "https://sokoban.cn", "");
        assertEquals("留空 = 最新一期", "", app.getUrlNum());
        assertEquals("https://sokoban.cn/", myMaps.uil);
    }

    @Test
    public void testParseCompetitionJsonAddsLevelsAndRecordsMatchInfo() {
        long id = ensureEmptySet("P25_MatchJson");
        myMaps.m_Set_id = id;

        String json = "{\"id\":112,\"begin\":\"2026-01-01\",\"end\":\"2026-01-07\","
                + "\"main\":{\"level\":\"#####\\n#@$.#\\n#####\",\"title\":\"M1\",\"author\":\"a\"},"
                + "\"extra\":{\"level\":\"#####\\n#@$.#\\n#####\",\"title\":\"M2\",\"author\":\"b\"}}";

        BoxManPC.CompetitionResult r = app.parseCompetitionJson(json);

        assertTrue("HTTP 200 时 what = 1 → 标题「比赛信息」", r.ok);
        assertTrue("提示语应报期号与起止日期，实际：" + r.message,
                r.message.startsWith("第112期比赛关卡加载成功！"));
        assertTrue(r.message.contains("开始：2026-01-01"));
        assertTrue(r.message.contains("结束：2026-01-07"));
        assertEquals("main + extra 共 2 个关卡", 2, myMaps.m_lstMaps.size());
        assertEquals("第112期比赛", myMaps.mMatchNo);
        assertEquals("2026-01-01", myMaps.mMatchDate1);
        assertEquals("2026-01-07", myMaps.mMatchDate2);
        assertEquals("关卡应落进目标关卡集", 2, sql.count_Level(id));
    }

    @Test
    public void testParseCompetitionJsonReportsNotStarted() {
        long id = ensureEmptySet("P25_MatchEmpty");
        myMaps.m_Set_id = id;

        String json = "{\"id\":113,\"begin\":\"2026-02-01\",\"end\":\"2026-02-07\"}";
        BoxManPC.CompetitionResult r = app.parseCompetitionJson(json);

        assertTrue(r.ok);
        assertTrue("没有 main/extra 时应报「尚未开赛」，实际：" + r.message,
                r.message.contains("第113期（尚未开赛）！"));
        assertEquals("不应往库里加关卡", 0, sql.count_Level(id));
    }

    @Test
    public void testParseCompetitionJsonFallsBackOnGarbage() {
        BoxManPC.CompetitionResult r = app.parseCompetitionJson("这不是 JSON");
        assertTrue("解析失败也回 what = 1（原版 myJson 只 catch ParseException）", r.ok);
        assertEquals("没找到关卡数据或比赛尚未开始！", r.message);
    }

    @Test
    public void testUrlInputDialogCarriesTheTwoFieldsFromGetUilDialog() {
        long id = ensureEmptySet("P25_UrlDlg");
        UrlInputDialog dlg = new UrlInputDialog(app, id, (setId, uil, num) -> { });
        try {
            assertEquals("网站栏应预填 myMaps.uil", myMaps.uil, dlg.tfUil.getText());
            assertEquals("期号栏初值应为空", "", dlg.tfNum.getText());
            assertEquals("期号栏应带 15sp 的 hint", UrlInputDialog.NUM_HINT, dlg.tfNum.getToolTipText());
            assertEquals(15, UrlInputDialog.HINT_TEXT_SIZE);

            // android:digits="0123456789"
            dlg.tfNum.setText("12ab3");
            assertEquals("非数字应被过滤", "123", dlg.tfNum.getText());
        } finally {
            dlg.dispose();
        }
    }

    @Test
    public void testUrlInputDialogSubmitCarriesSetId() {
        long id = ensureEmptySet("P25_UrlDlg2");
        final long[] got = new long[1];
        final String[] args = new String[2];
        UrlInputDialog dlg = new UrlInputDialog(app, id, (setId, uil, num) -> {
            got[0] = setId;
            args[0] = uil;
            args[1] = num;
        });
        try {
            // ⚠️ 原版 dialog_uil 也带 android:digits="0123456789"，URL 里只有数字能留下 —— 照抄的怪癖
            dlg.tfUil.setText("12345");
            dlg.tfNum.setText("7");
            dlg.fireSubmit();
            assertEquals("应回传对话框携带的关卡集 id", id, got[0]);
            assertEquals("12345", args[0]);
            assertEquals("7", args[1]);
        } finally {
            dlg.dispose();
        }
    }

    // ------------------------------------------------------------ case 10「详细...」

    @Test
    public void testSetAboutMessageIsSolvedOverTotal() {
        long id = ensureSet("P25_About");
        assertEquals("「详细...」的正文就是 已通关数/关卡总数", "0/1", app.setAboutMessage(id));
    }

    @Test
    public void testSetAboutLoadsSetInfoIntoMyMaps() {
        long id = ensureSet("P25_About2");
        app.setAboutMessage(id);
        assertEquals("get_Set 应把关卡集说明装进 J_Title", "P25_About2", myMaps.J_Title);
        assertEquals("作者", "P25 Author", myMaps.J_Author);
        assertEquals("说明", "P25 Comment", myMaps.J_Comment);
    }

    @Test
    public void testShowSetAboutOpensMyAbout1() {
        long id = ensureSet("P25_AboutDlg");
        app.setContextPosition(3, indexOfSet3(id));
        ShownDialog shown = captureShownDialog();

        app.showSetAbout();

        assertTrue("原版 case 10 进 myAbout1，实际：" + shown, shown.is(myAbout1.class));
        assertEquals("sFile 应指向被点中的关卡集", "P25_AboutDlg", myMaps.sFile);
        shown.dispose();
    }

    // ------------------------------------------------------------ 约定锁（源码扫描）

    @Test
    public void testBoxManPcUsesHoloPopupMenuOnly() {
        String src = readSource("BoxManPC.java");
        assertFalse("上下文菜单不能再用裸 Swing 菜单项", src.contains("new JMenuItem("));
        assertTrue("必须走 compat/HoloPopupMenu", src.contains("HoloPopupMenu.create()"));
    }

    @Test
    public void testNullActionButtonClosesTheDialog() {
        // 原版 AlertController.mButtonHandler：先 m.sendToTarget() 派发监听器，随后**无条件**
        // 发一条 MSG_DISMISS_DIALOG。所以关闭与 action 是否为 null 无关 ——
        // addButton(text, null) 是「点了就关」而不是死按钮，addButton(text, action) 也要关。
        //
        // ⚠️ 这条原先锁的是实现字面量 `if (action != null) { action.run(); } else { dispose(); }`，
        // 而那个 if/else 正是 bug：传了 action 的按钮**永远关不掉**（用户报的
        // 「点『是』不消失，只是一直跳转到下一个关卡」）。现在改成锁语义。
        String src = readSource("compat/HoloAlertDialog.java").replaceAll("\\s+", " ");
        assertFalse("不能再是 if/else：有 action 就只跑 action、不关框（那会让按钮永远关不掉）",
                src.contains("} else { dispose(); }"));
        assertTrue("addButton 的监听器必须无条件 dispose()（原版 MSG_DISMISS_DIALOG）",
                src.contains("finally { dispose(); }"));
    }

    @Test
    public void testGridViewImportOptionsUseTheImportDialogLabels() {
        // import_dialog.xml 的 cb_xsb / cb_lurd 文案是 XSB / Lurd（不是「关卡 / 答案」）
        String src = readSource("myGridView.java");
        assertTrue(src.contains("HoloContent.wrapCheck(\"XSB\""));
        assertTrue(src.contains("HoloContent.wrapCheck(\"Lurd\""));
        assertFalse("「关卡 / 答案」是 import_dialog3.xml 的文案，不该出现在这里",
                src.contains("check32(\"关卡\"") || src.contains("check32(\"答案\""));
    }

    @Test
    public void testGridViewImportsIntoTargetSetNotByName() {
        String src = readSource("myGridView.java");
        assertTrue("添加关卡(文档) 应按 m_Set_id 落库",
                src.contains("importLevelFileInto(f, mSetId, true)"));
        assertTrue("添加关卡(剪切板) 应按 m_Set_id 落库",
                src.contains("importLevelTextInto(text, mSetId, true)"));
    }

    // ------------------------------------------------------------ 辅助

    /** 「显示对话框」的替身：记录最近一次被弹出的对话框，而不真的显示。 */
    private static final class ShownDialog {
        JDialog last;

        JDialog get() {
            return last;
        }

        boolean is(Class<?> type) {
            return last != null && type.isInstance(last);
        }

        void dispose() {
            if (last != null) last.dispose();
        }

        @Override
        public String toString() {
            return String.valueOf(last);
        }
    }

    private ShownDialog captureShownDialog() {
        final ShownDialog holder = new ShownDialog();
        app.dialogShower = dlg -> holder.last = dlg;
        return holder;
    }

    private static void drainEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
    }

    /** 建一个空的扩展组关卡集，返回它的 id（并刷新主窗口列表）。 */
    private long ensureEmptySet(String title) {
        long old = sql.find_Set(title);
        if (old > 0) sql.del_T(old);
        long id = sql.add_T(3, title, "P25 Author", "P25 Comment");
        assertTrue("关卡集应建成功", id > 0);
        app.refreshTree();
        return id;
    }

    /** 建一个只含 1 个关卡的扩展组关卡集，返回它的 id（并刷新主窗口列表）。 */
    private long ensureSet(String title) {
        long id = ensureEmptySet(title);
        long lid = sql.add_L(id, new mapNode(LEVEL, "P25 Level", "P25 Author", ""));
        assertTrue("关卡应入库", lid > 0);
        app.refreshTree();
        return id;
    }

    /** 把一个关卡集连同它的关卡整个删掉（用于清掉上一次跑留下的残留）。 */
    private static void purgeSet(String title) {
        long id = sql.find_Set(title);
        if (id > 0) sql.del_T(id);
    }

    private static int indexOfSet3(long id) {
        for (int i = 0; i < myMaps.mSets3.size(); i++) {
            if (myMaps.mSets3.get(i).id == id) return i;
        }
        fail("mSets3 里找不到 id = " + id);
        return -1;
    }

    private static set_Node nodeOfSet3(long id) {
        return myMaps.mSets3.get(indexOfSet3(id));
    }

    private static String titleOfSet(long id) {
        Cursor c = sql.mSDB.rawQuery("SELECT T_Title FROM G_Set WHERE T_id = ?",
                new String[]{Long.toString(id)});
        try {
            return c.moveToNext() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    private static long lastLevelId(long setId) {
        sql.get_Levels(setId);
        assertFalse("关卡集里应有关卡", myMaps.m_lstMaps.isEmpty());
        return myMaps.m_lstMaps.get(myMaps.m_lstMaps.size() - 1).Level_id;
    }

    private static long lastLevelKey(long setId) {
        sql.get_Levels(setId);
        return myMaps.m_lstMaps.get(myMaps.m_lstMaps.size() - 1).key;
    }

    private static long countState(long levelId, int solution) {
        Cursor c = sql.mSDB.rawQuery(
                "SELECT COUNT(*) FROM G_State WHERE P_id = ? AND G_Solution = ?",
                new String[]{Long.toString(levelId), Integer.toString(solution)});
        try {
            return c.moveToNext() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private static void writeImportDoc(String name, String body) throws Exception {
        File f = new File(myMaps.sRoot + "/导入/" + name);
        f.delete();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(body);
        }
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

    private static JTextField findTextField(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField) return (JTextField) c;
            if (c instanceof Container) {
                JTextField found = findTextField((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
