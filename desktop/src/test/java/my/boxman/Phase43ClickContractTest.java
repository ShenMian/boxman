package my.boxman;

import my.boxman.compat.HoloTap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JPanel;
import java.awt.event.MouseEvent;
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
 * 收尾批次 —— <b>点击契约收口</b>：主源码里再也没有任何一个动作挂在
 * {@code MouseEvent.MOUSE_CLICKED} 上。
 *
 * <h3>为什么这是一整类缺陷，而不是「风格问题」</h3>
 * {@code MOUSE_CLICKED} 不是原始事件，是 Windows 原生层在 {@code AwtComponent::WmMouseUp}
 * 里<b>派生</b>的，受一个<b>无阈值</b>的开关控制：按住期间只要来了任何一次
 * {@code WM_MOUSEMOVE}（哪怕 1 像素），{@code m_mouseButtonClickAllowed} 就被清零，
 * 抬起时不再补发 CLICKED（完整推导见 {@code compat/HoloClick} 的类注释）。
 * 用户看到的是「有时点一次没反应，有时点几次才有反应」。
 *
 * <p>对双击更糟：两击里只要有一击被吞，{@code getClickCount()} 就永远到不了 2，
 * <b>双击功能彻底失效</b>。
 *
 * <h3>本用例锁什么</h3>
 * <ol>
 *   <li>主源码里 {@code mouseClicked} 只允许是<b>空实现</b>（{@code implements MouseListener}
 *       要求的方法占位），不能带任何动作 —— 一律改挂 {@code mouseReleased}。</li>
 *   <li>{@link HoloTap} 的双击判定：两次抬起落在系统双击间隔内且位移不超过 slop 才算双击，
 *       而且三连击不能算成两次双击。</li>
 *   <li>本轮改过的 7 处逐个钉住改法（源码扫描），防止回退。</li>
 * </ol>
 */
public class Phase43ClickContractTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 本轮改过的 7 处所在文件。 */
    private static final String[] TOUCHED = {
            "myStateBrow.java",
            "mySolutionBrow.java",
            "myRecogView.java",
            "myRecogViewMap.java",
            "myFindViewMap.java",
            "myEditViewMap.java",
            "BoxManPC.java",
    };

    // ============================================================ 1. 全局：动作不许挂 mouseClicked

    /**
     * 主源码里每一个 {@code mouseClicked} 覆写都必须是<b>空方法体</b>。
     *
     * <p>用「把方法体里除空白和大括号以外的字符全删掉，看还剩不剩东西」来判 ——
     * 比匹配具体语句稳，任何形式的动作都会被抓住。
     */
    @Test
    public void everyMouseClickedOverrideInMainSourcesIsEmpty() throws Exception {
        List<String> offenders = new ArrayList<String>();
        File dir = new File(System.getProperty("user.dir"), "src/main/java/my/boxman");
        for (File f : allJavaFiles(dir)) {
            String src = stripComments(read(f));
            int at = src.indexOf("mouseClicked(MouseEvent");
            while (at >= 0) {
                int open = src.indexOf('{', at);
                assertTrue("mouseClicked 后面应有方法体：" + f.getName(), open > 0);
                int depth = 0;
                int end = -1;
                for (int i = open; i < src.length(); i++) {
                    char c = src.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            end = i;
                            break;
                        }
                    }
                }
                assertTrue("mouseClicked 的大括号不配平：" + f.getName(), end > 0);
                String body = src.substring(open, end + 1).replaceAll("[\\s{}]", "");
                if (!body.isEmpty()) {
                    offenders.add(f.getName() + " → " + body);
                }
                at = src.indexOf("mouseClicked(MouseEvent", end);
            }
        }
        assertTrue("主源码里不该再有「带动作的 mouseClicked」。MOUSE_CLICKED 会被 Windows 原生层"
                        + "因按住期间的任何一次 WM_MOUSEMOVE 吞掉（见 compat/HoloClick），"
                        + "动作一律要挂 mouseReleased。实际残留：\n  "
                        + String.join("\n  ", offenders),
                offenders.isEmpty());
    }

    /** 顺带钉住：本轮改过的 7 个文件里，{@code mouseClicked} 要么没有、要么是空实现。 */
    @Test
    public void theSevenTouchedFilesHaveNoActiveMouseClicked() throws Exception {
        for (String name : TOUCHED) {
            String src = stripComments(codeOf(name));
            int at = src.indexOf("mouseClicked(MouseEvent");
            while (at >= 0) {
                int open = src.indexOf('{', at);
                int close = src.indexOf('}', open);
                assertEquals(name + " 的 mouseClicked 应是空实现（见 HoloClick 的类注释）",
                        "", src.substring(open + 1, close).trim());
                at = src.indexOf("mouseClicked(MouseEvent", close);
            }
        }
    }

    // ============================================================ 2. HoloTap 的双击判定

    @Test
    public void holoTapFiresOnTwoReleasesWithinTheInterval() {
        HoloTap tap = new HoloTap();
        assertFalse("第一击不该算双击", tap.isDoubleTap(evt(1000, 100, 100)));
        assertTrue("第二击在间隔内、几乎没移动 → 应算双击",
                tap.isDoubleTap(evt(1000 + HoloTap.interval(), 103, 101)));
    }

    @Test
    public void holoTapDoesNotFireWhenTooSlow() {
        HoloTap tap = new HoloTap();
        tap.isDoubleTap(evt(1000, 100, 100));
        assertFalse("超过系统双击间隔 → 不算双击",
                tap.isDoubleTap(evt(1000 + HoloTap.interval() + 50, 100, 100)));
    }

    @Test
    public void holoTapDoesNotFireWhenTooFar() {
        HoloTap tap = new HoloTap();
        tap.isDoubleTap(evt(1000, 100, 100));
        assertFalse("位移超过 slop → 不算双击（这是拖动，不是双击）",
                tap.isDoubleTap(evt(1100, 100 + HoloTap.SLOP + 5, 100)));
    }

    /** 三连击只能算「一次双击 + 一次新的第一击」，不能算成两次双击。 */
    @Test
    public void holoTapDoesNotTreatThreeTapsAsTwoDoubleTaps() {
        HoloTap tap = new HoloTap();
        assertFalse(tap.isDoubleTap(evt(1000, 10, 10)));
        assertTrue(tap.isDoubleTap(evt(1100, 10, 10)));
        assertFalse("双击已被消费，第三击应重新开始计数",
                tap.isDoubleTap(evt(1200, 10, 10)));
    }

    /** {@code reset()} 之后，紧跟着的那次抬起不能跟前面接成双击。 */
    @Test
    public void holoTapResetBreaksThePair() {
        HoloTap tap = new HoloTap();
        tap.isDoubleTap(evt(1000, 10, 10));
        tap.reset();
        assertFalse("reset 后应重新开始计数", tap.isDoubleTap(evt(1100, 10, 10)));
    }

    /** slop 取 Android {@code ViewConfiguration} 的量级；间隔取系统设置。 */
    @Test
    public void holoTapConstantsAreSane() {
        assertEquals("SLOP 应对应 Android 的 doubleTapSlop 量级", 24, HoloTap.SLOP);
        int interval = HoloTap.interval();
        assertTrue("系统双击间隔应落在合理区间（拿不到时退化 500），实际 " + interval,
                interval >= 100 && interval <= 2000);
    }

    // ============================================================ 3. 逐处改法（源码扫描）

    /** {@code myStateBrow} 的树：按下记路径、抬起才动作（并守住「还是按下那一行」）。 */
    @Test
    public void stateBrowTreeUsesTheReleaseContract() throws Exception {
        String src = stripComments(codeOf("myStateBrow.java"));
        String pressed = methodBody(src, "void mousePressed", "(");
        assertTrue("按下时应记住「按的是哪一行」", pressed.contains("pressedPath = armed ? tree.getSelectionPath()"));
        assertTrue("按下时应记 armed（按下在内 → 抬起仍在内）", pressed.contains("armed = tree.contains("));

        String released = methodBody(src, "void mouseReleased", "(");
        assertTrue("抬起时应确认鼠标底下还是按下那一行", released.contains("pressed.equals(at)"));
        assertTrue("抬起时才载入状态", released.contains("openSelected()"));
        assertTrue("抬起时也要 armed", released.contains("armed"));
    }

    /** {@code mySolutionBrow}：记行只需要 {@code mousePressed}（原版 onChildClick 也只记行）。 */
    @Test
    public void solutionBrowRemembersTheRowOnPressOnly() throws Exception {
        String src = stripComments(codeOf("mySolutionBrow.java"));
        assertFalse("不该再有 mouseClicked 覆写", src.contains("mouseClicked"));
        String pressed = methodBody(src, "void mousePressed", "(");
        assertTrue("按下时应记住行号", pressed.contains("onRowAt(listSolutions.locationToIndex(e.getPoint()))"));
    }

    /** {@code FlatButton}：动作挂抬起，并去掉 suppressClick 这个中间状态。 */
    @Test
    public void recogViewFlatButtonFiresOnRelease() throws Exception {
        String src = stripComments(codeOf("myRecogView.java"));
        assertFalse("suppressClick 这个中间状态应已删除", src.contains("suppressClick"));
        String released = methodBody(src, "void mouseReleased", "(");
        assertTrue("抬起时应判「按下在内且抬起仍在内」", released.contains("contains(e.getX(), e.getY())"));
        assertTrue("抬起时才触发 onClick", released.contains("onClick.run()"));
        assertTrue("长按被消费时不应再触发 click", released.contains("longFired && consumeLongClick"));
    }

    /** 三个地图面板：点击/双击一律在 {@code mouseReleased} 里分派，且用 HoloTap 判双击。 */
    @Test
    public void theThreeMapPanelsDispatchTapsOnRelease() throws Exception {
        String edit = stripComments(codeOf("myEditViewMap.java"));
        assertTrue("myEditViewMap 抬起时应自己判双击", edit.contains("tap.isDoubleTap(e)"));
        assertTrue("myEditViewMap 应有 doDoubleTap 落点", edit.contains("void doDoubleTap("));

        String recog = stripComments(codeOf("myRecogViewMap.java"));
        assertTrue("myRecogViewMap 抬起时应自己判双击", recog.contains("tap.isDoubleTap(e)"));
        assertTrue("myRecogViewMap 抬起时应分派单击", recog.contains("doSingleTap(e.getX(), e.getY())"));
        assertTrue("myRecogViewMap 要用按下位置判 slop（startX 在拖动里会被改写）",
                recog.contains("pressX = startX"));

        String find = stripComments(codeOf("myFindViewMap.java"));
        assertTrue("myFindViewMap 抬起时应自己判双击", find.contains("tap.isDoubleTap(e)"));
        assertTrue("myFindViewMap 要用按下位置判 slop（startX 在拖动里会被改写）",
                find.contains("pressX = startX"));
        assertTrue("myFindViewMap 的两个热区仍在抬起时判", find.contains("m_rTrun.contains("));
    }

    /** {@code BoxManPC} 的关卡树：抬起才打开，且保留 Phase36 那条「按下那一行」兜底。 */
    @Test
    public void boxManTreeKeepsThePressedPathGuard() throws Exception {
        String src = stripComments(codeOf("BoxManPC.java"));
        String released = methodBody(src, "void mouseReleased", "(");
        assertTrue("抬起时应确认「还是按下那一行」", released.contains("path.equals(pressed)"));
        assertTrue("抬起时才打开关卡集", released.contains("browLevels("));
        assertTrue("右键菜单也挪到抬起（isPopupTrigger 在 Windows 上就是抬起的）",
                released.contains("e.isPopupTrigger()"));
        String pressed = methodBody(src, "void mousePressed", "(");
        assertTrue("按下时应记路径", pressed.contains("pressedPath = armed ? tree.getSelectionPath()"));
    }

    // ============================================================ 工具

    private static MouseEvent evt(long when, int x, int y) {
        return new MouseEvent(new JPanel(), MouseEvent.MOUSE_RELEASED, when,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static String codeOf(String name) throws IOException {
        return read(new File(System.getProperty("user.dir"),
                "src/main/java/my/boxman/" + name));
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static List<File> allJavaFiles(File dir) {
        List<File> out = new ArrayList<File>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (f.isDirectory()) out.addAll(allJavaFiles(f));
            else if (f.getName().endsWith(".java")) out.add(f);
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
}
