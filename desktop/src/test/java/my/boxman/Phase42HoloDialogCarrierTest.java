package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloInputDialog;
import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.UiWindow;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 收尾批次 —— <b>对话框载体</b>全部换成原版 {@code AlertDialog(THEME_HOLO_DARK)} 的等价物。
 *
 * <h3>背景：审计里那条「11 个 AlertDialog 等价物仍是 PC 尺寸（340~540 宽）」是过期的</h3>
 * 那句话出自 {@code compat/HoloAlertDialog} 的类注释，描述的是<b>该类诞生之前</b>的状态
 * （一堆「浅色 Swing 对话框 + 各自 setSize(340~540)」）。现在所有 AlertDialog 等价物都走
 * {@link HoloAlertDialog}，宽度统一是 {@code 370dp × 95% = 352dp}。本用例把这条钉死。
 *
 * <p>真正还残留的「PC 尺寸 + 浅色」载体是裸 {@code JOptionPane} 的调用点
 * （{@code myGameView} 12 处 / {@code BoxManPC} 8 处 / {@code myGridView} 2 处）。
 * 它们已被逐一替换成 Holo 载体，本用例同时锁住「一个都不许回来」。
 *
 * <h3>为什么模态性也要锁</h3>
 * 原版 {@code AlertDialog.show()} <b>不阻塞</b>。PC 早期用模态 {@code JOptionPane} 顶替，
 * 在 1ms 定时器驱动的动画回调里会开嵌套事件循环 → 定时器重入 → 层层嵌套 → 卡死。
 * 所以动画链上的框必须走 {@code showNonModal}。
 */
public class Phase42HoloDialogCarrierTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(90);

    /** 原版 {@code values/dimens.xml}: {@code dialog_min_width_minor = 95%}（竖屏） */
    private static final int HOLO_DIALOG_WIDTH = 352;

    // ============================================================ 1. 尺寸：352dp

    /**
     * 所有 Holo 载体都必须是 {@code 370dp × 95% = 352dp}。
     *
     * <p>这条直接对应审计 §0.2「窗口尺寸」那一行 —— 原版对话框宽度来自
     * {@code Theme.Holo.Dialog.BaseAlert} 的 {@code windowMinWidthMinor}，
     * 370dp 竖屏屏 → 352dp。
     */
    @Test
    public void everyHoloDialogCarrierIs352dpWide() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        assertEquals("原版 dialog_min_width_minor 就是 95% → 370 × 95% = 352",
                HOLO_DIALOG_WIDTH, Math.round(UiWindow.PHONE_WIDTH * 95f / 100f));

        List<HoloAlertDialog> carriers = new ArrayList<HoloAlertDialog>();
        carriers.add(HoloAlertDialog.create(null, "标题"));
        carriers.add(new HoloMessageDialog(null, "标题", "正文", "确定"));
        carriers.add(new HoloConfirmDialog(null, "标题", "正文", "取消", "确定", null));
        carriers.add(new HoloChoiceDialog(null, "标题", null, new String[]{"甲", "乙"}));
        carriers.add(new HoloInputDialog(null, "标题", "初值", 288, null));
        carriers.add(new GotoDialog(null, 5, 100, null));

        for (HoloAlertDialog d : carriers) {
            try {
                d.applyHoloSize();
                assertEquals(d.getClass().getSimpleName() + " 的宽度应是 352dp（370dp 的 95%），"
                                + "而不是审计里那句已过期的「340~540 的 PC 尺寸」",
                        HOLO_DIALOG_WIDTH, d.getWidth());
            } finally {
                d.dispose();
            }
        }
    }

    // ============================================================ 2. 一个 JOptionPane 都不许剩

    /**
     * {@code myGameView} / {@code BoxManPC} / {@code myGridView} 里不许再有 {@code JOptionPane}
     * 调用点（注释不算 —— 注释里会提到「删掉了 JOptionPane」这类字样，所以先剥注释）。
     */
    @Test
    public void noPcNativeDialogCarriersRemain() throws Exception {
        for (String name : new String[]{"myGameView.java", "BoxManPC.java", "myGridView.java"}) {
            String src = codeOf(name);
            assertFalse(name + " 里不许再出现 JOptionPane 调用点 —— 全部应走 compat 的 Holo 载体。"
                            + "实际残留：" + firstLineWith(src, "JOptionPane"),
                    src.contains("JOptionPane."));
        }
    }

    // ============================================================ 3. 非模态工厂

    /**
     * 动画链上的框走 {@code showNonModal}：出来的框<b>不阻塞</b>（{@code setVisible} 立刻返回）
     * 且 {@code isModal() == false} —— 这正是原版 {@code AlertDialog.show()} 的语义。
     */
    @Test
    public void nonModalFactoriesDoNotBlockAndReportNonModal() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        HoloAlertDialog confirm = HoloConfirmDialog.showNonModal(
                null, "注意", "答案或状态太长，保存到文档！", "取消", "确定", null);
        try {
            assertFalse("showNonModal 出来的确认框必须是非模态的", confirm.isModal());
            assertTrue("非模态 setVisible 应立刻返回，且框已在显示", confirm.isVisible());
        } finally {
            confirm.dispose();
        }

        HoloAlertDialog msg = HoloMessageDialog.showNonModal(
                null, "正逆相合或通关！", "答案成功保存！\n(可用进退键观看通关演示)", "确定");
        try {
            assertFalse("showNonModal 出来的提示框必须是非模态的", msg.isModal());
            assertTrue("非模态 setVisible 应立刻返回，且框已在显示", msg.isVisible());
        } finally {
            msg.dispose();
        }
    }

    /** 对照组：按钮事件里弹的框仍是模态的（与 PC 既有用法一致，不阻塞风险不存在）。 */
    @Test
    public void thePlainConstructorStaysModal() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        HoloConfirmDialog d = new HoloConfirmDialog(
                null, "", "重新开始，确定吗？", "取消", "确定", null);
        try {
            assertTrue("按钮触发的框仍应是模态的", d.isModal());
        } finally {
            d.dispose();
        }
    }

    // ============================================================ 4. 动画链的调用图

    /**
     * 动画回调<b>及其被调用方</b>里都不许出现阻塞式对话框。
     *
     * <p>{@code Phase33LevelCompleteDialogTest.animationCallbacksContainNoBlockingDialog}
     * 只扫了 {@code UpData1..4} 的方法体本身，而真正的弹出点在 {@code saveAns()} /
     * {@code saveAns2()} 这两个<b>被调方</b>里 —— 那条用例锁不住它们。这里把整条调用链补上。
     */
    @Test
    public void animationCallbacksAndTheirCalleesContainNoBlockingDialog() throws Exception {
        String src = codeOf("myGameView.java");
        for (String m : new String[]{"UpData1", "UpData2", "UpData3", "UpData4",
                                     "saveAns", "saveAns2", "zhengniHE", "DoEvent"}) {
            String body = methodBody(src, m);
            assertFalse(m + "() 在动画/事件链上，不能出现阻塞式 JOptionPane"
                            + "（模态框在 EDT 上开嵌套事件循环，定时器重入 → 层层嵌套 → 卡死）",
                    body.contains("JOptionPane"));
        }
        assertTrue("saveAns() 的「答案或状态太长」应走非模态工厂",
                methodBody(src, "saveAns").contains("HoloConfirmDialog.showNonModal"));
        assertTrue("saveAns2() 的「答案太长」应走非模态工厂",
                methodBody(src, "saveAns2").contains("HoloConfirmDialog.showNonModal"));
        assertTrue("saveAns2() 的三条「正逆相合或通关！」应走非模态提示框",
                methodBody(src, "saveAns2").contains("HoloMessageDialog.showNonModal"));
    }

    // ============================================================ 5. DoEvent(10)「关闭调试」

    /**
     * 原版 {@code myGameView.java:2625} 是一个<b>默认勾选</b>的复选框 + 单个「确定」，
     * PC 早期退化成了「是 / 否」确认框 —— 勾/不勾都要关框，与「是 / 否」语义完全不同。
     */
    @Test
    public void closeMacroDebugDialogIsACheckBoxWithASingleOk() throws Exception {
        String src = codeOf("myGameView.java");
        String body = methodBody(src, "showCloseMacroDebugDialog");

        assertTrue("应是非模态的（原版 .create().show() 不阻塞）",
                body.contains("HoloAlertDialog.createNonModal(this, \"关闭调试\")"));
        assertTrue("内容应是复选框（原版 setView(isBack)）", body.contains("new JCheckBox("));
        assertTrue("复选框默认勾选（原版 isBack.setChecked(true)）", body.contains("setSelected(true)"));
        assertTrue("勾选框文字照原版", body.contains("关卡回到该“宏”打开前的状态"));
        assertEquals("原版只有「确定」一个按钮", 1, countOccurrences(body, "addButton("));
        assertTrue("确定后应按勾选状态复位关卡", body.contains("if (isBack.isSelected()) levelReset(false)"));
        assertFalse("不能再是「是 / 否」确认框", body.contains("YES_NO_OPTION"));

        assertTrue("DoEvent(10) 应调用这个新方法",
                methodBody(src, "DoEvent").contains("showCloseMacroDebugDialog()"));
    }

    // ============================================================ 6. 「设置」菜单

    /**
     * 原版 {@code myGameView.java:3554} 是 {@code setSingleChoiceItems} 的<b>菜单式</b>单选框：
     * 单击分派到子对话框、父框不动，按钮栏只有一个「返回」。
     */
    @Test
    public void setup1MenuIsASingleChoiceListWithAReturnButton() throws Exception {
        String src = codeOf("myGameView.java");
        String body = methodBody(src, "showSetup1Dialog");

        assertTrue("应是 HoloChoiceDialog 的标题「设置」",
                body.contains("new HoloChoiceDialog(this, \"设置\", null, menu2)"));
        assertTrue("四项文案照原版",
                body.contains("\"速度设置\", \"更换皮肤\", \"更换背景\", \"奇偶格位明暗度\""));
        assertTrue("按钮栏只有「返回」", body.contains("dlg.addButton(\"返回\""));
        assertTrue("「返回」才保存设置（原版 BoxMan.saveSets()）", body.contains("saveSets()"));
        assertTrue("「奇偶格位明暗度」分支自己关框（原版 dialog.dismiss()）",
                body.contains("dlg.dispose()"));

        // 三个子框
        assertTrue("速度子框应存在", src.contains(" void showSpeedDialog("));
        assertTrue("皮肤子框应存在", src.contains(" void showSkinDialog("));
        assertTrue("背景子框应存在", src.contains(" void showBkPicDialog("));

        String speed = methodBody(src, "showSpeedDialog");
        assertTrue("速度框初始下标应是 m_Sets[10]",
                speed.contains("setSelectedIndex(myMaps.m_Sets[10])"));
        assertTrue("速度框应写回 m_Sets[10]", speed.contains("myMaps.m_Sets[10] = i"));
        assertFalse("原版速度框没有「取消」按钮（onClick 里就写回，不回滚）",
                speed.contains("\"取消\""));

        assertTrue("皮肤框「取消」应回滚到打开时那一项",
                methodBody(src, "showSkinDialog").contains("myMaps.mFile_List1.get(init)"));
        assertTrue("背景框「取消」应回滚到打开时那一项",
                methodBody(src, "showBkPicDialog").contains("myMaps.mFile_List2.get(init)"));
        assertFalse("背景框「取消」分支不应调 setColorBK()（原版的怪癖，照搬）",
                methodBody(src, "showBkPicDialog").contains("init == 0) setColorBK"));
    }

    // ============================================================ 7. 「选择：宏」

    /**
     * 原版 {@code myGameView.java:4575}：{@code setSingleChoiceItems(list, -1, …)} + 取消/确定，
     * 未选中就点「确定」什么都不做（判 {@code m_nItemSelect > -1}）—— 正是
     * {@link HoloChoiceDialog#selectThenOk} 的语义。PC 早期用的是下拉输入框，默认还预选了第一项。
     */
    @Test
    public void macroPickerUsesSelectThenOkWithNoPreselection() throws Exception {
        String body = methodBody(codeOf("myGameView.java"), "mLoad_Do_Macro");
        assertTrue("应走 selectThenOk 且初始不预选（-1）",
                body.contains("HoloChoiceDialog.selectThenOk(this, \"选择：宏\", null, list, -1"));
        assertTrue("应保留原版 m_nItemSelect 哨兵", body.contains("m_nItemSelect = idx"));
        assertTrue("应按 m_nItemSelect > -1 判空", body.contains("if (m_nItemSelect > -1)"));
        assertTrue("宏名应按下标取，而不是按文字",
                body.contains("myMaps.readMacroFile(myMaps.mFile_List.get(m_nItemSelect))"));
        assertFalse("不应再是下拉输入框", body.contains("showInputDialog"));
    }

    // ============================================================ 8. myGridView 的两处

    /**
     * 原版 {@code myGridView.java:729} 是 11 项单选框（「自动 / 1 个 / … / 10 个」），
     * 单击即生效 + 单个「取消」；PC 早期让用户敲数字（0~10）。
     */
    @Test
    public void gridColumnCountUsesTheOriginalElevenItemChoiceList() throws Exception {
        String body = methodBody(codeOf("myGridView.java"), "chooseColumnCount");
        assertTrue("应是 clickToPick + 原版标题",
                body.contains("HoloChoiceDialog.clickToPick(this, \"设置每行的图标个数\", menu, myMaps.m_Sets[33]"));
        assertTrue("首项应是「自动」", body.contains("\"自动\", \"1 个\""));
        assertTrue("末项应是「10 个」", body.contains("\"10 个\""));
        assertTrue("冻结自动列数的判据应是 m_Sets[34] == 0（原版口径）",
                body.contains("myMaps.m_Sets[34] == 0"));
        assertFalse("不应再让用户敲数字", body.contains("showInputDialog"));
    }

    /**
     * 原版 {@code myGridView.java:517}「定位...」是 {@code goto_dialog.xml} 的
     * 「输入位置: 」+ 160dp 数字框 + 取消/确定，{@link GotoDialog} 就是它的等价物。
     */
    @Test
    public void gridGotoUsesGotoDialog() throws Exception {
        String src = codeOf("myGridView.java");
        String body = methodBody(src, "doGoto");
        assertTrue("应改用 GotoDialog（该类的布局就是 goto_dialog.xml）",
                body.contains("new GotoDialog(this, 1, myMaps.m_lstMaps.size()"));
        assertFalse("不应再是输入框", body.contains("showInputDialog"));
        assertTrue("GotoDialog 不再是死代码：myGridView 必须引用它",
                src.contains("new GotoDialog("));
    }

    // ============================================================ 9. BoxManPC 的两处

    /** 原版 {@code BoxMan.java:430}：「关卡集名称」+ 输入框 + 取消/确定 → {@code HoloInputDialog}。 */
    @Test
    public void newSetPromptUsesHoloInputDialog() throws Exception {
        String body = methodBody(codeOf("BoxManPC.java"), "createNewSet");
        assertTrue("应走 HoloInputDialog 且预填 getNewSetName()",
                body.contains("new HoloInputDialog(this, \"关卡集名称\", myMaps.getNewSetName()"));
        assertFalse("不应再是输入框", body.contains("showInputDialog"));
        assertTrue("创建逻辑应整体挪进回调", body.contains("mySQLite.m_SQL.add_T(3, input, \"\", \"\")"));
    }

    /** 导入成功 / 失败 / 建集失败一律走 Holo 提示框，且启动期的致命错误也有 Holo 外壳。 */
    @Test
    public void boxManImportDialogsUseHoloMessageDialog() throws Exception {
        String src = codeOf("BoxManPC.java");
        assertEquals("四处导入失败 + 一处建集失败 = 五个「错误」提示框",
                5, countOccurrences(src, "new HoloMessageDialog(this, \"错误\""));
        assertTrue("导入统计框也应是 Holo 提示框",
                src.contains("new HoloMessageDialog(this, \"导入成功\""));
        assertTrue("启动期「关卡库出错」应给 Holo 外壳（owner 传 null）",
                src.contains("new HoloMessageDialog(null, \"推箱快手\""));
        assertTrue("该框必须是模态的，否则 System.exit 会在用户看清之前就把进程干掉",
                src.contains("System.exit(1)"));
    }

    // ============================================================ 10. 「重新开始」

    /** 原版 {@code myGameView.java:3462}：逻辑整个写在 OnClickListener 里。 */
    @Test
    public void restartConfirmMovesTheResetIntoTheCallback() throws Exception {
        String body = methodBody(codeOf("myGameView.java"), "onReStart");
        assertTrue("应走 HoloConfirmDialog（无标题，原版就没写 setTitle）",
                body.contains("new HoloConfirmDialog(this, \"\", \"重新开始，确定吗？\""));
        assertTrue("复位逻辑应挪进回调", body.contains("levelReset(false)"));
        assertTrue("逆推分支也应在回调里", body.contains("levelReset(true)"));
        assertFalse("不应再用阻塞式确认框", body.contains("showConfirmDialog"));
    }

    // ============================================================ 11. clickToPick 的预选时序

    /**
     * {@code clickToPick} 的预选必须在挂监听器<b>之前</b>完成。
     *
     * <p>否则 {@code setSelectedIndex} 自己就会触发「选中」回调，而
     * {@code clickToPick} 的回调会 {@code dispose()} —— 框会在用户还没看见时就自己关掉。
     * 原版没有这个问题：{@code setSingleChoiceItems} 的初始下标只用于绘制单选钮，不回调。
     */
    @Test
    public void clickToPickPreselectsBeforeWiringTheListener() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());

        final int[] fired = {-1};
        HoloChoiceDialog d = HoloChoiceDialog.clickToPick(null, "设置每行的图标个数",
                new String[]{"自动", "1 个", "2 个"}, 2, i -> fired[0] = i);
        try {
            assertEquals("应预选初始下标", 2, d.getSelectedIndex());
            // 把事件队列里已排队的 runnable 跑掉：若预选发生在挂监听器之后，
            // clickToPick 的 invokeLater 会把「选中」回调排进来
            try {
                SwingUtilities.invokeAndWait(() -> { });
            } catch (Exception e) {
                throw new AssertionError("排空事件队列失败", e);
            }
            assertEquals("构造期间的预选不能触发回调，否则框会被提前关掉", -1, fired[0]);
        } finally {
            d.dispose();
        }
    }

    /** 三参数的 {@code clickToPick} 应委托给带初始下标的重载（默认不预选）。 */
    @Test
    public void clickToPickKeepsTheOldThreeArgOverload() throws Exception {
        String src = codeOf("compat/HoloChoiceDialog.java");
        assertTrue("应有带 initialIndex 的重载",
                src.contains("String[] items,\n                                               int initialIndex, OnPick onPick"));
        assertTrue("三参数版应委托给四参数版，默认不预选",
                src.contains("return clickToPick(owner, title, items, -1, onPick);"));
    }

    // ============================================================ 工具

    private static String codeOf(String relPath) throws IOException {
        File f = new File(System.getProperty("user.dir"),
                "src/main/java/my/boxman/" + relPath);
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    /**
     * 取出某个方法的函数体。定位用 {@code " void " + name + "("} ——
     * 调用点永远不会被 {@code void } 前缀，所以不会误命中 {@code saveAns(1)} 这类调用。
     */
    private static String methodBody(String src, String name) {
        String needle = " void " + name + "(";
        int at = src.indexOf(needle);
        assertTrue("找不到方法 " + name, at >= 0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(open, i + 1);
            }
        }
        throw new AssertionError("方法 " + name + " 的大括号不配平");
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private static String firstLineWith(String src, String needle) {
        int at = src.indexOf(needle);
        if (at < 0) return "";
        int start = src.lastIndexOf('\n', at) + 1;
        int end = src.indexOf('\n', at);
        return src.substring(start, end < 0 ? src.length() : end).trim();
    }
}
