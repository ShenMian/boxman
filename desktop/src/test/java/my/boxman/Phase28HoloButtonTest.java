package my.boxman;

import my.boxman.compat.HoloButton;
import my.boxman.compat.HoloContent;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 G ⑤ —— 普通按钮统一成 Holo 深色样式（{@link HoloButton}）的验证。
 *
 * <p>原版布局里凡是没有显式 {@code android:background} 的 {@code <Button>} 都走
 * {@code Theme.Holo} 的 {@code Widget.Holo.Button} → {@code btn_default_holo_dark}
 * （深灰半透明底 + 浅色顶棱 + 暗底缘 + {@code minWidth 64dp / minHeight 48dp}）。
 * PC 此前是 FlatLaf 默认外观，本用例把配色、9-patch 几何、三态渲染、尺寸语义
 * 以及「哪些窗口落地了」全部锁住。
 *
 * <p>⚠️ 所有像素断言都在**离屏**渲染上做（{@code BufferedImage}），不碰真实屏幕。
 */
public class Phase28HoloButtonTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    /** 渲染按钮用的面板底色，取 {@code HoloContent.BAND}（原版对话框/选项行最常用的 #363636） */
    private static final Color PANEL = HoloContent.BAND;

    private static String savedRoot;
    private static String savedPath;

    private final List<Window> toDispose = new ArrayList<Window>();

    @BeforeClass
    public static void setUpClass() throws IOException {
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
        File home = new File(System.getProperty("user.dir"), "build/ui-snapshot/g5-home");
        deleteRecursively(home);
        //noinspection ResultOfMethodCallIgnored
        home.mkdirs();
        myMaps.sRoot = home.getAbsolutePath();
        myMaps.sPath = "/";
    }

    @After
    public void tearDown() {
        for (Window w : toDispose) {
            w.dispose();
        }
        toDispose.clear();
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
    }

    // ============================================================ 1. 配色与 AOSP 实测值

    @Test
    public void colorsMatchTheAospNinePatches() {
        assertColor("正常态主体", new Color(41, 47, 52, 189), HoloButton.BODY);
        assertColor("正常态顶棱", new Color(82, 87, 91, 199), HoloButton.TOP_HIGHLIGHT);
        assertColor("正常态描边", new Color(32, 32, 32, 191), HoloButton.EDGE);
        assertColor("正常态底缘", new Color(29, 29, 29, 213), HoloButton.BOTTOM);

        assertColor("按下态主体", new Color(240, 240, 240, 89), HoloButton.PRESSED_BODY);
        assertColor("按下态顶棱", new Color(249, 249, 249, 145), HoloButton.PRESSED_TOP);
        assertColor("按下态描边", new Color(208, 208, 208, 97), HoloButton.PRESSED_EDGE);
        assertColor("按下态底缘", new Color(122, 122, 122, 131), HoloButton.PRESSED_BOTTOM);

        assertColor("禁用态主体", new Color(153, 153, 153, 39), HoloButton.DISABLED_BODY);
        assertColor("禁用态顶棱", new Color(150, 150, 150, 128), HoloButton.DISABLED_TOP);
        assertColor("禁用态描边", new Color(116, 116, 116, 128), HoloButton.DISABLED_EDGE);
        assertColor("禁用态底缘", new Color(108, 108, 108, 128), HoloButton.DISABLED_BOTTOM);
    }

    @Test
    public void textColorsMatchPrimaryTextHoloDark() {
        // primary_text_holo_dark → bright_foreground_holo_dark = #FFF3F3F3
        assertColor("正常文字色", new Color(0xF3, 0xF3, 0xF3), HoloButton.TEXT);
        // bright_foreground_disabled_holo_dark = #FF4C4C4C
        assertColor("禁用文字色", new Color(0x4C, 0x4C, 0x4C), HoloButton.TEXT_DISABLED);
    }

    @Test
    public void minSizeMatchesWidgetHoloButton() {
        assertEquals("Widget.Holo.Button 的 minWidth 是 64dip", 64, HoloButton.MIN_WIDTH);
        assertEquals("Widget.Holo.Button 的 minHeight 是 48dip", 48, HoloButton.MIN_HEIGHT);
    }

    // ============================================================ 2. 9-patch 几何

    @Test
    public void graphicIsInsetByTheTransparentNinePatchMargin() {
        BufferedImage img = render(new HoloButton("加载"), 52, 48);
        int x = 26;
        for (int y = 0; y < HoloButton.INSET_TOP; y++) {
            assertEquals("上缘第 " + y + " 行应是 9-patch 的透明外缘", PANEL.getRGB(), img.getRGB(x, y));
        }
        for (int y = 48 - HoloButton.INSET_BOTTOM; y < 48; y++) {
            assertEquals("下缘第 " + y + " 行应是 9-patch 的透明外缘", PANEL.getRGB(), img.getRGB(x, y));
        }
        int y = 24;
        for (int cx = 0; cx < HoloButton.INSET_X; cx++) {
            assertEquals("左缘第 " + cx + " 列应是 9-patch 的透明外缘", PANEL.getRGB(), img.getRGB(cx, y));
        }
        for (int cx = 52 - HoloButton.INSET_X; cx < 52; cx++) {
            assertEquals("右缘第 " + cx + " 列应是 9-patch 的透明外缘", PANEL.getRGB(), img.getRGB(cx, y));
        }
    }

    // ============================================================ 3. 三态渲染

    @Test
    public void normalStateDrawsBevelOverTheBody() {
        BufferedImage img = render(new HoloButton("加载"), 52, 48);
        int x = HoloButton.INSET_X + 3;                 // 避开中间的文字墨迹
        int yEdge = HoloButton.INSET_TOP;
        int yTop = HoloButton.INSET_TOP + 1;
        int yBody = 24;
        int yBottom = 48 - HoloButton.INSET_BOTTOM - 2;
        int yEdge2 = 48 - HoloButton.INSET_BOTTOM - 1;

        assertNear("描边行 = 描边色叠在主体色上", over(HoloButton.EDGE, over(HoloButton.BODY, PANEL)),
                img.getRGB(x, yEdge));
        assertNear("顶棱行 = 顶棱色叠在主体色上", over(HoloButton.TOP_HIGHLIGHT, over(HoloButton.BODY, PANEL)),
                img.getRGB(x, yTop));
        assertNear("主体行 = 主体色叠在面板上", over(HoloButton.BODY, PANEL), img.getRGB(x, yBody));
        assertNear("底缘行 = 底缘色叠在主体色上", over(HoloButton.BOTTOM, over(HoloButton.BODY, PANEL)),
                img.getRGB(x, yBottom));
        assertNear("下描边行 = 描边色叠在主体色上", over(HoloButton.EDGE, over(HoloButton.BODY, PANEL)),
                img.getRGB(x, yEdge2));

        assertTrue("顶棱应比主体亮", brightness(img.getRGB(x, yTop)) > brightness(img.getRGB(x, yBody)));
        assertTrue("底缘应比主体暗", brightness(img.getRGB(x, yBottom)) < brightness(img.getRGB(x, yBody)));
        assertNotSame("主体不能等于面板色（图形确实画出来了）", PANEL.getRGB(), img.getRGB(x, yBody));
    }

    @Test
    public void bodyIsLighterThanTheBlackWindowBackground() {
        // 原版 AppBaseTheme 的 windowBackground 是纯黑（action_manage.xml 的按钮行就在黑底上），
        // Holo 深色按钮的半透明底叠在黑底上应比黑底亮 —— 这才看得出是个按钮。
        HoloButton b = new HoloButton("加载");
        BufferedImage img = render(b, 52, 48, Color.BLACK);
        int x = HoloButton.INSET_X + 3;
        assertNear("黑底上的主体色", over(HoloButton.BODY, Color.BLACK), img.getRGB(x, 24));
        assertTrue("主体应比黑底亮", brightness(img.getRGB(x, 24)) > brightness(Color.BLACK.getRGB()));
    }

    @Test
    public void pressedStateLightsTheButtonUp() {
        int x = HoloButton.INSET_X + 3;
        int body = render(new HoloButton("加载"), 52, 48).getRGB(x, 24);

        HoloButton pressed = new HoloButton("存入");
        pressed.getModel().setPressed(true);
        pressed.getModel().setArmed(true);
        BufferedImage img = render(pressed, 52, 48);

        assertNear("按下态主体 = 按下主体色叠在面板上", over(HoloButton.PRESSED_BODY, PANEL), img.getRGB(x, 24));
        assertTrue("按下态应比正常态亮（Holo 深色按钮按下变中灰）",
                brightness(img.getRGB(x, 24)) > brightness(body));
    }

    @Test
    public void disabledStateUsesTheDisabledNinePatch() {
        int x = HoloButton.INSET_X + 3;
        int normalBody = render(new HoloButton("加载"), 52, 48).getRGB(x, 24);

        HoloButton disabled = new HoloButton("清空");
        disabled.setEnabled(false);
        BufferedImage img = render(disabled, 52, 48);

        assertNear("禁用态主体 = 禁用主体色叠在面板上", over(HoloButton.DISABLED_BODY, PANEL), img.getRGB(x, 24));
        assertNotSame("禁用态主体不能和正常态一样", normalBody, img.getRGB(x, 24));
        // ⚠️ 别断言「禁用态比正常态暗」：禁用主体是浅灰 @15% alpha，叠在 #363636 上反而比
        // 正常态（深灰 @74%）亮；只有叠在黑底上才更暗。两态的差别看合成色本身。
        assertNear("黑底上禁用态才更暗", over(HoloButton.DISABLED_BODY, Color.BLACK),
                render(disabled, 52, 48, Color.BLACK).getRGB(x, 24));

        // 文字：把最亮的墨迹像素取出来比
        assertTrue("禁用态文字应比正常态暗",
                brightestInk(img) < brightestInk(render(new HoloButton("清空"), 52, 48)));
    }

    @Test
    public void textIsDrawnCentredInsideTheGraphic() {
        BufferedImage withText = render(new HoloButton("加载"), 52, 48);
        BufferedImage blank = render(new HoloButton(""), 52, 48);
        assertEquals("空文字的按钮图形里不应有任何墨迹", 0, inkCount(blank));

        int ink = inkCount(withText);
        assertTrue("有文字时应画出墨迹（自己画字，不走 UI 委托）", ink > 0);

        // 墨迹的水平重心应接近图形中心
        int x0 = HoloButton.INSET_X;
        int x1 = 52 - HoloButton.INSET_X;
        long sum = 0;
        int n = 0;
        for (int y = 0; y < 48; y++) {
            for (int cx = x0; cx < x1; cx++) {
                if (isInk(withText, cx, y)) {
                    sum += cx;
                    n++;
                }
            }
        }
        double center = sum / (double) n;
        assertTrue("墨迹重心应接近图形中心（实测 " + center + "）", Math.abs(center - (x0 + x1) / 2.0) <= 1.0);
    }

    @Test
    public void htmlTextIsSplitIntoLines() {
        HoloButton two = new HoloButton("<html>恢<br>复</html>");
        assertEquals("HTML 的 <br> 应还原成两行", 2, two.labelLines().length);
        assertEquals("恢", two.labelLines()[0]);
        assertEquals("复", two.labelLines()[1]);

        HoloButton one = new HoloButton("加载");
        assertEquals(1, one.labelLines().length);
        assertEquals("加载", one.labelLines()[0]);
    }

    // ============================================================ 4. 尺寸语义

    @Test
    public void wrapContentIsClampedToMinSize() {
        HoloButton b = new HoloButton("导");
        assertFalse("没有显式设过首选尺寸", b.isPreferredSizeSet());
        assertEquals("wrap_content 的宽度下限是 64dp", HoloButton.MIN_WIDTH, b.getPreferredSize().width);
        assertEquals("wrap_content 的高度下限是 48dp", HoloButton.MIN_HEIGHT, b.getPreferredSize().height);
    }

    @Test
    public void explicitLayoutSizeIsNotClamped() {
        // 原版 action_manage.xml 的 layout_width="52dp" 是确定值（MeasureSpec.EXACTLY），
        // 不受 minWidth 约束 —— 所以 52 < 64 也必须原样保留。
        HoloButton b = new HoloButton("加载");
        b.setPreferredSize(new java.awt.Dimension(52, 48));
        assertEquals(52, b.getPreferredSize().width);
        assertEquals(48, b.getPreferredSize().height);
    }

    @Test
    public void factoryAppliesTextSizeAndPadding() {
        HoloButton b = HoloButton.create("返回", 13, new java.awt.Insets(5, 10, 5, 10));
        assertEquals(13, b.getFont().getSize());
        assertEquals(10, b.getInsets().left);
        assertEquals(5, b.getInsets().top);
        assertNotSame("工厂应返回 HoloButton 本体", JButton.class, b.getClass());
    }

    // ============================================================ 5. 落地站点

    @Test
    public void actGmButtonsAreHoloButtonsWithOriginalSizes() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myActGMView win = track(new myActGMView(null, false));

        assertHoloButton("加载", win.btLoadAct, 52);
        assertHoloButton("存入", win.btSaveAct, 52);
        assertHoloButton("清空", win.btClear, 48);
        assertHoloButton("暂存", win.btSave_t, 48);
        assertHoloButton("执行", win.btDO, 60);
    }

    @Test
    public void submitButtonsAreHoloButtons() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        mySubmit win = track(new mySubmit());
        assertTrue("「返回」应是 HoloButton", win.btCancel instanceof HoloButton);
        assertTrue("「提交」应是 HoloButton", win.btOK instanceof HoloButton);
        assertEquals("返回", win.btCancel.getText());
        assertEquals("提交", win.btOK.getText());
    }

    @Test
    public void exportButtonIsHoloAndCarriesTheOriginalLabel() {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        myMaps.curMap = new mapNode("#####\n#@$.#\n#####", "t", "a", "c");
        myExport win = track(new myExport());

        assertTrue("bt_ex_OK 应是 HoloButton", win.bt_OK instanceof HoloButton);
        // 原版 export_view.xml 的 android:text 就是「导出」（PC 曾写成「执行导出」）
        assertEquals("导出", win.bt_OK.getText());

        Container bar = win.bt_OK.getParent();
        assertNotNull("按钮应挂在底栏上", bar);
        assertEquals("底栏底色应取原版那一行的 #363636，Holo 半透明底才合成得对",
                HoloContent.BAND.getRGB(), bar.getBackground().getRGB());
    }

    // ============================================================ 6. 没被误改的地方

    @Test
    public void alertDialogBarButtonsStayBorderless() {
        // AlertDialog 的底栏按钮是 Widget.Holo.Button.Borderless（selectableItemBackground），
        // 不是 btn_default_holo_dark —— 不能被「统一」成 HoloButton。
        my.boxman.compat.HoloAlertDialog dlg =
                my.boxman.compat.HoloAlertDialog.create(null, "提示");
        JButton b = dlg.addButton("取消", null);
        assertFalse("AlertDialog 底栏按钮不应是 HoloButton", b instanceof HoloButton);
        assertFalse("AlertDialog 底栏按钮不画边框", b.isBorderPainted());
        dlg.dispose();
    }

    @Test
    public void holoContentButtonDelegatesToHoloButton() {
        assertTrue("HoloContent.button 应返回 HoloButton", HoloContent.button("确定") instanceof HoloButton);
    }

    // ============================================================ 7. 源码扫描

    @Test
    public void plainButtonsAreGoneFromTheThreeDarkWindows() throws Exception {
        for (String name : new String[]{"myActGMView.java", "mySubmit.java", "myExport.java"}) {
            String src = codeOf(name);
            assertFalse(name + " 里不应再有裸 new JButton(（普通按钮要走 HoloButton）",
                    src.contains("new JButton("));
        }
    }

    @Test
    public void holoButtonIsTheOnlySelfDrawnPlainButton() throws Exception {
        String src = codeOf("HoloButton.java");
        assertTrue("必须自绘（JButton.setBackground 会被 FlatLaf 覆盖）",
                src.contains("protected void paintComponent(Graphics g)"));
        assertTrue("自绘后不应再调 super.paintComponent", !src.contains("super.paintComponent"));
    }

    // ============================================================ 工具

    private static void assertColor(String what, Color expected, Color actual) {
        assertEquals(what + "（ARGB）", expected.getRGB(), actual.getRGB());
    }

    private static void assertNear(String what, Color expected, int actualRgb) {
        Color a = new Color(actualRgb);
        assertTrue(what + "：期望 " + hex(expected) + "，实际 " + hex(a),
                Math.abs(a.getRed() - expected.getRed()) <= 3
                        && Math.abs(a.getGreen() - expected.getGreen()) <= 3
                        && Math.abs(a.getBlue() - expected.getBlue()) <= 3);
    }

    /** SrcOver：把带 alpha 的 {@code fg} 叠到不透明的 {@code bg} 上。 */
    private static Color over(Color fg, Color bg) {
        double a = fg.getAlpha() / 255.0;
        return new Color(
                (int) Math.round(fg.getRed() * a + bg.getRed() * (1 - a)),
                (int) Math.round(fg.getGreen() * a + bg.getGreen() * (1 - a)),
                (int) Math.round(fg.getBlue() * a + bg.getBlue() * (1 - a)));
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** 离屏渲染：面板底色固定 {@link #PANEL}，不碰真实屏幕。 */
    private static BufferedImage render(HoloButton b, int w, int h) {
        return render(b, w, h, PANEL);
    }

    private static BufferedImage render(HoloButton b, int w, int h, Color bg) {
        b.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        b.paint(g);
        g.dispose();
        return img;
    }

    /** 图形内部与面板色不同的像素数（= 墨迹）。 */
    private static int inkCount(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (isInk(img, x, y)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean isInk(BufferedImage img, int x, int y) {
        int x0 = HoloButton.INSET_X;
        int x1 = img.getWidth() - HoloButton.INSET_X;
        int y0 = HoloButton.INSET_TOP;
        int y1 = img.getHeight() - HoloButton.INSET_BOTTOM;
        if (x < x0 || x >= x1 || y < y0 || y >= y1) {
            return false;                                   // 只统计图形内部
        }
        return brightness(img.getRGB(x, y)) > brightness(over(HoloButton.BODY, PANEL).getRGB()) + 120;
    }

    private static int brightestInk(BufferedImage img) {
        int best = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                best = Math.max(best, brightness(img.getRGB(x, y)));
            }
        }
        return best;
    }

    private static void assertHoloButton(String text, JButton b, int width) {
        assertTrue("「" + text + "」应是 HoloButton", b instanceof HoloButton);
        assertEquals("「" + text + "」文字", text, b.getText());
        assertEquals("「" + text + "」宽度取自 action_manage.xml", width, b.getPreferredSize().width);
        assertEquals("「" + text + "」高度被 minHeight=48dp 顶到 48", HoloButton.MIN_HEIGHT,
                b.getPreferredSize().height);
    }

    private <T extends Window> T track(T w) {
        toDispose.add(w);
        return w;
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String sourceOf(String name) throws IOException {
        File f = new File("src/main/java/my/boxman/" + name);
        if (!f.isFile()) {
            f = new File("src/main/java/my/boxman/compat/" + name);   // compat 包下的 Holo* 组件
        }
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** 源码扫描前先剥掉注释 —— 类注释里提到过 `new JButton(` / `JOptionPane` 会误报。 */
    private static String codeOf(String name) throws IOException {
        return sourceOf(name)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "");
    }
}
