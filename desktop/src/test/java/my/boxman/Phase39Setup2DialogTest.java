package my.boxman;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「开关选项」对话框（{@code myGameView.showSetup2Dialog}）的条目清单与原版对齐。
 *
 * <p>原版在 {@code myGameView.java:3687-3800}（{@code player_Setup2}）用
 * {@code AlertDialog.THEME_HOLO_DARK + setMultiChoiceItems(20 项)} 列出全部开关，
 * 其中 3 项是 Android 平台专有、PC 端不移植：
 *
 * <ul>
 *   <li>系统导航键（{@code m_Sets[16]}）—— PC 没有系统虚拟导航栏；</li>
 *   <li>禁用全屏（{@code m_Sets[20]}）—— PC 窗口没有全屏概念；</li>
 *   <li>音量键选择关卡（{@code m_Sets[15]}）—— PC 没有音量键。</li>
 * </ul>
 *
 * <p>其余各项要一项不少、文案照抄原版，本用例把这份清单按顺序锁死。
 *
 * <p>⚠️ 一个容易踩的坑，正是本次修的东西：原版第 11 项的 UI 文案是「<b>定位双推</b>」，
 * 而「禁用逆推目标点」只是原版代码里的<b>行内注释</b>（{@code myGameView.java:3721} 的
 * {@code //禁用逆推目标点}）兼 <b>INI 键名</b>（{@code BoxMan.java:828/898}）。
 * 端口把注释当成了菜单文字。
 */
public class Phase39Setup2DialogTest {

    /**
     * 「开关选项」应列出的条目，顺序照原版（已剔除 3 项 Android 专有）。
     *
     * <p>原版顺序见 {@code myGameView.java:3688-3709}，本表即其删掉
     * 系统导航键 / 禁用全屏 / 音量键选择关卡 后的结果。
     */
    private static final String[] EXPECTED_OPTIONS = {
            "双击箱子编号",
            "自动箱子编号",
            "标尺",
            "标尺不随关卡旋转",
            "区分奇偶地板格",
            "死锁嗅探",
            "可达提示",
            "仓管员转向动画",
            "长按点位提示关联网",
            "自动加载最新状态",
            "定位双推",
            "允许穿越",
            "单步进退",
            "进度条",
            "自动爬阶梯",
            "显示当前时间提示",
    };

    /** 全部条目一项不少、顺序一致。 */
    @Test
    public void listsEveryPortedOptionInTheOriginalOrder() throws Exception {
        assertEquals("「开关选项」的条目清单必须与原版（去掉 3 项 Android 专有后）一致",
                Arrays.asList(EXPECTED_OPTIONS).toString(),
                checkboxLabels(setup2DialogSource()).toString());
    }

    /** 第 11 项用的是原版 UI 文案「定位双推」，不是那条行内注释。 */
    @Test
    public void reversePushToggleUsesTheOriginalLabel() throws Exception {
        String dialog = setup2DialogSource();
        assertTrue("原版菜单项文字是「定位双推」（android myGameView.java:3699）",
                dialog.contains("new JCheckBox(\"定位双推\""));
        assertFalse("「禁用逆推目标点」是原版的行内注释，不是 UI 文案，不该出现在对话框里",
                dialog.contains("new JCheckBox(\"禁用逆推目标点\""));
    }

    /** 「定位双推」仍绑在原版那一位 {@code m_Sets[32]} 上。 */
    @Test
    public void reversePushToggleStillBindsToSet32() throws Exception {
        String dialog = setup2DialogSource();
        assertTrue("「定位双推」应读 m_Sets[32]",
                dialog.contains("new JCheckBox(\"定位双推\", myMaps.m_Sets[32] == 1)"));
        assertTrue("保存时应写回 m_Sets[32]",
                dialog.contains("myMaps.m_Sets[32] = chk10.isSelected() ? 1 : 0;"));
    }

    /**
     * INI 键名<b>不能</b>跟着 UI 文案一起改。
     *
     * <p>原版 {@code BoxMan.java:828 / 898} 存取的键就是「禁用逆推目标点」，
     * 改了就读不回原版（以及 PC 端此前）存下的设置。
     */
    @Test
    public void iniKeyKeepsTheOriginalName() throws Exception {
        assertTrue("INI 键必须与原版 BoxMan.java 一致，不能跟着 UI 文案改名",
                codeOf("myGameView.java")
                        .contains("file.set(\"操作\", \"禁用逆推目标点\", myMaps.m_Sets[32]);"));
    }

    /** 3 项 Android 专有开关不移植，且不应误入对话框。 */
    @Test
    public void androidOnlyOptionsAreNotPorted() throws Exception {
        List<String> labels = checkboxLabels(setup2DialogSource());
        for (String androidOnly : new String[]{"系统导航键", "禁用全屏", "音量键选择关卡"}) {
            assertFalse("「" + androidOnly + "」是 Android 平台专有，PC 端不移植",
                    labels.contains(androidOnly));
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 只取 {@code showSetup2Dialog()} 的方法体，避免别处出现同名文字时误判。 */
    private static String setup2DialogSource() throws IOException {
        return methodBody(codeOf("myGameView.java"), "void showSetup2Dialog", "()");
    }

    /** 按出现顺序取出源码里所有 {@code new JCheckBox("…")} 的文案。 */
    private static List<String> checkboxLabels(String src) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("new JCheckBox\\(\"([^\"]*)\"").matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 取出某个方法的函数体（从签名后的第一个 `{` 配平到对应的 `}`）。 */
    private static String methodBody(String src, String name, String signatureTail) {
        int at = src.indexOf(" " + name + signatureTail);
        assertTrue("找不到方法 " + name + signatureTail, at >= 0);
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

    private static String codeOf(String name) throws IOException {
        File f = new File("src/main/java/my/boxman/" + name);
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        // 先剥注释：注释里提到「禁用逆推目标点」会误报
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
