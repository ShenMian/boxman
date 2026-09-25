package my.boxman;

import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloConfirmDialog;
import my.boxman.compat.HoloPopupMenu;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 阶段 G ① —— 图片列表链路的移植验证：
 * {@link myFileExplorerActivity}（filelist.xml 上一级 / 完成）、
 * {@link myPicListViewAdapter}、{@link myPicListView}（piclist.xml 位置）、
 * 以及 {@code myGifMakeDialog} 的「制作」按钮（gif.xml）。
 *
 * <p>⚠️ 两个全局静态必须复位：{@code myMaps.myPathList}（原版是 5 个固定位置的数组，
 * 本类会改 [2]）和 {@code myMaps.m_Sets[36]}（当前图片位置下标）。
 * 否则同 JVM 里排在后面的用例类会看到脏值。
 */
public class Phase26PicListAndFileExplorerTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private static File root;
    private static String[] savedPathList;
    private static int savedSet36;
    private static List<String> savedFileList;

    private final List<Object> toDispose = new ArrayList<Object>();

    @BeforeClass
    public static void setUpClass() throws Exception {
        root = Files.createTempDirectory("boxman_phase26").toFile();
        myMaps.sRoot = root.getAbsolutePath();
        savedPathList = myMaps.myPathList.clone();
        savedSet36 = myMaps.m_Sets[36];
        savedFileList = new ArrayList<String>(myMaps.mFile_List);
    }

    @Before
    public void setUp() throws Exception {
        // 造一棵固定的目录树：aDir/ bDir/ m.png z.jpg note.txt
        deleteRecursively(new File(root, "aDir"));
        deleteRecursively(new File(root, "bDir"));
        for (String n : new String[] { "m.png", "small.png", "note.txt" }) {
            new File(root, n).delete();
        }
        assertTrue(new File(root, "aDir").mkdirs());
        assertTrue(new File(root, "bDir").mkdirs());
        writeBytes(new File(root, "m.png"), pngBytes(300, 400));
        writeBytes(new File(root, "small.png"), pngBytes(100, 100));   // 尺寸不过 200 的闸门
        writeBytes(new File(root, "note.txt"), "hello".getBytes("UTF-8"));

        myMaps.myPathList = new String[] { "", "/tencent/qq_images/", "/", "/", "/" };
        myMaps.m_Sets[36] = 0;
        myMaps.mFile_List.clear();
        myMaps.edPict = null;
    }

    @After
    public void tearDown() {
        for (Object o : toDispose) {
            if (o instanceof java.awt.Window) ((java.awt.Window) o).dispose();
            else if (o instanceof JDialog) ((JDialog) o).dispose();
        }
        myMaps.myPathList = savedPathList;
        myMaps.m_Sets[36] = savedSet36;
        myMaps.mFile_List.clear();
        myMaps.mFile_List.addAll(savedFileList);
        myMaps.edPict = null;
    }

    // ============================================================ myFileExplorerActivity

    @Test
    public void fileExplorerShowsTheTwoBarActionsInOriginalOrder() {
        myFileExplorerActivity a = explorer();
        assertEquals("标题应为「自定义位置」", "自定义位置", a.actionBar.getBarTitle());
        assertEquals(Arrays.asList("上一级", "完成"), a.actionBar.getBarActionTitles());
        assertTrue("左侧返回折角应开启", a.actionBar.isUpEnabled());
        assertFalse("两项都是 always，不该有 ⋮", a.actionBar.isOverflowVisible());
    }

    @Test
    public void fileExplorerListsDirectoriesFirstThenPictureFilesOnly() {
        myFileExplorerActivity a = explorer();
        assertEquals("应只列 目录 + jpg/bmp/png（note.txt 被过滤）",
                Arrays.asList("aDir", "bDir", "m.png", "small.png"), a.getDisplayNames());
    }

    @Test
    public void fileExplorerPathTextIsRelativeToRoot() {
        myFileExplorerActivity a = explorer();
        assertEquals("在根目录下路径栏应为空", "", a.getPathText());
    }

    @Test
    public void fileExplorerClickingADirectoryEntersIt() {
        myFileExplorerActivity a = explorer();
        a.onItemClick(0);                       // aDir（目录排在最前）
        assertEquals("应进入 aDir", canonical(new File(root, "aDir")), canonical(a.currentParent));
    }

    @Test
    public void fileExplorerClickingAFileDoesNothing() {
        myFileExplorerActivity a = explorer();
        File before = a.currentParent;
        a.onItemClick(2);                       // m.png
        assertSame("点文件不该改变当前目录", before, a.currentParent);
    }

    @Test
    public void fileExplorerParentStopsAtRoot() {
        myFileExplorerActivity a = explorer();
        a.myParent();
        assertEquals("已在 sRoot，上一级应原地不动", canonical(root), canonical(a.currentParent));
    }

    @Test
    public void fileExplorerParentGoesUpOneLevel() {
        myFileExplorerActivity a = explorer();
        a.onItemClick(0);                                     // 进 aDir
        assertEquals("路径栏应显示 /aDir", sep() + "aDir", a.getPathText());
        a.myParent();
        assertEquals("上一级应回到根", canonical(root), canonical(a.currentParent));
    }

    @Test
    public void fileExplorerOkWritesThePickedPathIntoMyPathList() {
        myFileExplorerActivity a = explorer();
        myMaps.m_Sets[36] = 2;
        a.onItemClick(0);                       // 进 aDir → 路径栏 = /aDir
        final String[] picked = { null };
        a = new myFileExplorerActivity(p -> picked[0] = p);
        toDispose.add(a);
        a.onItemClick(0);
        a.onOk();
        assertEquals("完成应把路径+'/' 写进 myPathList[m_Sets[36]]",
                sep() + "aDir" + "/", myMaps.myPathList[2]);
        assertEquals("回调应带回同一个值", myMaps.myPathList[2], picked[0]);
    }

    @Test
    public void fileExplorerBackKeyOpensExitDialogWhenPathIsEmpty() {
        myFileExplorerActivity a = explorer();
        final JDialog[] shown = { null };
        a.dialogShower = dlg -> shown[0] = dlg;
        a.onBack();
        assertNotNull("路径为空时 BACK 应弹「退出浏览」确认框", shown[0]);
        assertTrue("应是 Holo 确认框", shown[0] instanceof HoloConfirmDialog);
    }

    @Test
    public void fileExplorerBackKeyGoesUpWhenPathIsNotEmpty() {
        myFileExplorerActivity a = explorer();
        a.dialogShower = dlg -> fail("路径非空时 BACK 不该弹确认框");
        a.onItemClick(0);                       // 进 aDir
        a.onBack();
        assertEquals("路径非空时 BACK = 上一级", canonical(root), canonical(a.currentParent));
    }

    // ============================================================ myPicListViewAdapter

    @Test
    public void adapterCountAndFileNameComeFromFileList() {
        myMaps.mFile_List.add("m.png");
        myMaps.mFile_List.add("z.jpg");
        myPicListViewAdapter ad = new myPicListViewAdapter();
        assertEquals(2, ad.getCount());
        assertEquals("m.png", ad.getFileName(0));
        assertEquals("z.jpg", ad.getFileName(1));
    }

    @Test
    public void adapterFileIsRootPlusCurrentPathPlusName() {
        myMaps.mFile_List.add("m.png");
        myMaps.myPathList[myMaps.m_Sets[36]] = "/sub/";
        myPicListViewAdapter ad = new myPicListViewAdapter();
        assertEquals(new File(root, "sub/m.png").getAbsolutePath(),
                ad.getFile(0).getAbsolutePath());
    }

    @Test
    public void adapterThumbnailKeepsAspectRatio() {
        java.awt.image.BufferedImage src =
                new java.awt.image.BufferedImage(300, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.image.BufferedImage out =
                myPicListViewAdapter.scaleToFit(src, myPicListViewAdapter.THUMB_W, myPicListViewAdapter.THUMB_H);
        assertEquals(myPicListViewAdapter.THUMB_W, out.getWidth());
        assertEquals(myPicListViewAdapter.THUMB_H, out.getHeight());
        // 300x400 等比 → 103x137 上下居中，留 5px 透明边。
        // 原版是 createScaledBitmap 强制拉伸（会填满、无留白），这里故意不照抄。
        assertEquals("顶部应有留白（说明没被拉伸）", 0, out.getRGB(51, 0) >>> 24);
        assertEquals("中部应是图像本体", 255, out.getRGB(51, 73) >>> 24);
    }

    @Test
    public void adapterFallsBackToPlaceholderWhenImageIsBroken() {
        myMaps.mFile_List.add("note.txt");
        myPicListViewAdapter ad = new myPicListViewAdapter();
        java.awt.image.BufferedImage bmp = ad.getBitmap(0);
        assertEquals("解码失败应给 1x1 占位图（与原版一致）", 1, bmp.getWidth());
        assertEquals(1, bmp.getHeight());
    }

    // ============================================================ myPicListView

    @Test
    public void picListActionBarHasOnlyThePathAction() {
        myPicListView v = picList();
        assertEquals(myMaps.myPathList[myMaps.m_Sets[36]], v.actionBar.getBarTitle());
        assertEquals(Arrays.asList("位置"), v.actionBar.getBarActionTitles());
        assertTrue(v.actionBar.isUpEnabled());
        assertFalse("只有一项 always，不该有 ⋮", v.actionBar.isOverflowVisible());
    }

    @Test
    public void picListPathDialogHasFiveChoicesAndClampsInitialIndex() {
        myPicListView v = picList();
        myMaps.m_Sets[36] = 99;                 // 越界 → 原版夹到 0
        captureDialog(v);
        v.showPathDialog();
        HoloChoiceDialog d = v.getPathDialog();
        assertNotNull(d);
        // ⚠️ HoloAlertDialog 用自绘标题，不设 AWT 的 title —— 别拿 getTitle() 断言
        assertEquals(5, d.list.getModel().getSize());
        assertEquals("快手默认位置", d.list.getModel().getElementAt(0));
        assertEquals("QQ 图片接收文件夹", d.list.getModel().getElementAt(1));
        assertEquals(0, d.list.getSelectedIndex());
    }

    @Test
    public void picListPathDialogSelectingAnItemUpdatesSet36() {
        myPicListView v = picList();
        captureDialog(v);
        v.showPathDialog();
        v.getPathDialog().list.setSelectedIndex(3);
        assertEquals(3, myMaps.m_Sets[36]);
    }

    @Test
    public void picListModifyIsAllowedOnlyForCustomPositions() {
        myPicListView v = picList();
        captureDialog(v);

        // 「修改」只能从「图片位置」对话框里点，所以先开对话框
        myMaps.m_Sets[36] = 0;
        v.showPathDialog();
        v.onPathModify();
        assertToast("这个位置不能修改！");

        myMaps.m_Sets[36] = 1;
        v.showPathDialog();
        v.onPathModify();
        assertToast("这个位置不能修改！");

        myMaps.m_Sets[36] = 3;
        final myFileExplorerActivity[] shown = { null };
        v.explorerShower = w -> shown[0] = w;
        v.showPathDialog();
        v.onPathModify();
        assertNotNull("自定义位置 3 应打开文件浏览器", shown[0]);
        toDispose.add(shown[0]);
    }

    @Test
    public void picListOpenFallsBackToSlashWhenPathIsBlank() {
        myPicListView v = picList();
        captureDialog(v);
        myMaps.m_Sets[36] = 2;
        myMaps.myPathList[2] = "   ";
        v.showPathDialog();
        v.onPathOpen();
        assertEquals("空位置应补成 /", "/", myMaps.myPathList[2]);
    }

    @Test
    public void picListContextMenuHasLoadAndDelete() {
        myPicListView v = picList();
        JPopupMenu m = v.buildContextMenu();
        assertEquals(2, HoloPopupMenu.itemCount(m));
        assertEquals(Arrays.asList("加载", "删除"), titles(m));
    }

    @Test
    public void picListDeleteRemovesTheFileAndTheListEntry() {
        myMaps.edPicList(myMaps.sRoot + myMaps.myPathList[myMaps.m_Sets[36]]);
        assertTrue("应扫到两张图", myMaps.mFile_List.contains("m.png"));
        myPicListView v = picList();
        int idx = myMaps.mFile_List.indexOf("m.png");
        v.m_Num = idx;
        v.onContextItemSelected(1);
        assertFalse("条目应从列表移除", myMaps.mFile_List.contains("m.png"));
        assertFalse("文件应被删除", new File(root, "m.png").exists());
    }

    @Test
    public void picListOpenTooSmallImageShowsToastInsteadOfRecog() {
        myMaps.mFile_List.clear();
        myMaps.mFile_List.add("small.png");     // 100x100，过不了 >200 的闸门
        myPicListView v = picList();
        v.openPic(0);
        assertNotNull("图片应当被解出来", myMaps.edPict);
        assertToast("图片尺寸太小或不能打开！");
    }

    // ============================================================ 源码扫描锁

    @Test
    public void picListNoLongerUsesJFileChooser() throws Exception {
        // 只认「真的 new 了一个」；类注释里提到 JFileChooser 不算
        assertFalse("myPicListView 不该再有 JFileChooser",
                sourceContains("myPicListView.java", "new JFileChooser("));
    }

    @Test
    public void gifMakeDialogUsesTheOriginalButtonLabel() throws Exception {
        assertTrue("按钮文案应是「制作」不是「确定」",
                sourceContains("myGifMakeDialog.java", "addButton(\"制作\""));
    }

    @Test
    public void fileExplorerUsesHoloCarriers() throws Exception {
        String src = read("myFileExplorerActivity.java");
        assertTrue("应用 myActionBar", src.contains("new myActionBar()"));
        assertTrue("菜单项应走 addBarAction", src.contains("addBarAction(\"上一级\""));
        assertTrue("菜单项应走 addBarAction", src.contains("addBarAction(\"完成\""));
    }

    // ============================================================ 工具

    private myFileExplorerActivity explorer() {
        myFileExplorerActivity a = new myFileExplorerActivity();
        toDispose.add(a);
        return a;
    }

    private myPicListView picList() {
        myPicListView v = new myPicListView();
        toDispose.add(v);
        return v;
    }

    /** 把「弹模态框」换成「只记不弹」 */
    private static void captureDialog(myPicListView v) {
        v.dialogShower = dlg -> { };
    }

    private static List<String> titles(JPopupMenu menu) {
        List<String> out = new ArrayList<String>();
        for (java.awt.Component c : menu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) out.add(((HoloPopupMenu.Row) c).getText());
        }
        return out;
    }

    private static void assertToast(String expected) {
        MyToast.showToast(null, "哨兵", MyToast.LENGTH_SHORT);
        MyToast.showToast(null, expected, MyToast.LENGTH_SHORT);
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
        assertEquals(expected, MyToast.currentToastText());
        MyToast.dismiss();
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }

    private static String sep() {
        return File.separator;
    }

    private static void writeBytes(File f, byte[] data) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(data);
        out.close();
    }

    /** 生成一个 width×height 的合法 PNG */
    private static byte[] pngBytes(int w, int h) throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        f.delete();
    }

    private static String read(String name) throws Exception {
        File f = new File("src/main/java/my/boxman/" + name);
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    private static boolean sourceContains(String name, String needle) throws Exception {
        return read(name).contains(needle);
    }
}
