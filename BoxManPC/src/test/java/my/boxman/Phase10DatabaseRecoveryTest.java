package my.boxman;

import my.boxman.compat.sqlite.Cursor;
import my.boxman.compat.sqlite.SQLiteDatabase;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 回归：关卡库初始化必须能自愈，且不能留下「看着像库、其实没表」的半成品。
 *
 * <p>背景（2026-09-24 审计发现的真 bug）：
 * 旧 {@code mySQLite.copyDataBase()} 写成
 * {@code while (is != null && (count = is.read(buffer)) != -1)}，
 * 当 {@code /assets/BoxMan.db} 不在 classpath 时 {@code is == null}，
 * 循环一次都不执行，但 {@code FileOutputStream} 已经把文件建出来了
 * → 磁盘上留下一个 <b>0 字节的 BoxMan.db</b>。
 *
 * <p>而 0 字节的 sqlite 文件是「合法可打开」的，旧 {@code checkDataBase()}
 * 只看「能否打开」就返回 true → 之后再也不会走 {@code copyDataBase()}，
 * 所有 SQL 永久报 {@code no such table: G_Set} 且<b>无法自愈</b>。
 * 磁盘上留下的物证：{@code build/{test_boxman_phase4null,phase4bnull,phase5null,phase7null}DataBase/BoxMan.db}
 * 全是 0 字节 / 0 张表。
 *
 * <p>修复后：
 * <ol>
 *   <li>{@code copyDataBase()} 资源缺失时抛 {@link IllegalStateException}，
 *       并清掉临时文件，绝不留下半成品；</li>
 *   <li>{@code checkDataBase()} 增加「文件非空」+「{@code sqlite_master} 里有表」两条判定，
 *       被污染的库会被判为不存在并重新复制。</li>
 * </ol>
 */
public class Phase10DatabaseRecoveryTest {

    /** 复制资源后应有的量级（原版 assets/BoxMan.db 约 1.15 MB） */
    private static final long MIN_VALID_SIZE = 100_000L;

    private static String savedRoot;
    private static String savedPath;

    @BeforeClass
    public static void setUp() {
        savedRoot = myMaps.sRoot;
        savedPath = myMaps.sPath;
    }

    @AfterClass
    public static void tearDown() {
        myMaps.sRoot = savedRoot;
        myMaps.sPath = savedPath;
    }

    /** 把 sRoot 指到一个干净的临时目录（只动 build/ 下的构建产物） */
    private static File freshRoot(String name) {
        File root = new File(System.getProperty("user.dir"), "build/test_boxman_" + name);
        deleteRecursively(root);
        Assert.assertTrue("应能创建临时根目录：" + root, root.mkdirs());
        myMaps.sRoot = root.getAbsolutePath();
        return root;
    }

    /** 0 字节 BoxMan.db 是「上次复制中断」的半成品，必须被判为无效并重新复制。 */
    @Test
    public void testZeroByteDatabaseIsRebuilt() throws IOException {
        File root = freshRoot("dbrecovery");
        File dir = new File(root, "DataBase");
        Assert.assertTrue(dir.mkdirs());
        File db = new File(dir, "BoxMan.db");

        try (FileOutputStream os = new FileOutputStream(db)) {
            // 故意留一个 0 字节文件
        }
        Assert.assertEquals("前置条件：库应为 0 字节", 0, db.length());

        mySQLite sql = mySQLite.getInstance();
        Assert.assertNotNull(sql);

        Assert.assertTrue("被污染的 0 字节库应被替换成有效库，实际长度=" + db.length(),
                db.length() >= MIN_VALID_SIZE);
        Assert.assertTrue("替换后的库应真的能查出表", countTables(db) > 0);

        System.out.println("[Phase10] 0 字节库自愈通过，重建后 " + db.length() + " 字节 / "
                + countTables(db) + " 张表");
    }

    /** {@code myMaps.sPath == null} 时不能拼出 {@code ...nullDataBase/} 这种带字面量 null 的路径。 */
    @Test
    public void testNullSPathDoesNotLeakIntoPath() {
        File root = freshRoot("dbnullpath");
        myMaps.sPath = null;

        mySQLite.getInstance();

        File expected = new File(root, "DataBase/BoxMan.db");
        Assert.assertTrue("数据库应落在 <root>/DataBase/，实际不存在：" + expected, expected.isFile());
        Assert.assertTrue("库内容应有效", expected.length() >= MIN_VALID_SIZE);

        File leaked = new File(root.getParentFile(), root.getName() + "nullDataBase");
        Assert.assertFalse("不应产生带字面量 null 的目录：" + leaked, leaked.exists());

        System.out.println("[Phase10] sPath=null 路径解析通过：" + expected);
    }

    /** 有效库不应被反复覆盖（自愈只发生在库无效时）。 */
    @Test
    public void testValidDatabaseIsNotOverwritten() {
        File root = freshRoot("dbkeep");
        mySQLite.getInstance();

        File db = new File(root, "DataBase/BoxMan.db");
        long firstLength = db.length();
        long firstModified = db.lastModified();
        Assert.assertTrue("前置条件：库应有效", firstLength >= MIN_VALID_SIZE);

        mySQLite.getInstance();

        Assert.assertEquals("有效库不应被重新复制", firstLength, db.length());
        Assert.assertEquals("有效库不应被重写", firstModified, db.lastModified());

        System.out.println("[Phase10] 有效库保持不动通过");
    }

    private static int countTables(File db) {
        SQLiteDatabase sdb = null;
        Cursor c = null;
        try {
            sdb = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = sdb.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table'", null);
            return (c != null && c.moveToFirst()) ? c.getInt(0) : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.close();
            if (sdb != null) sdb.close();
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File ch : children) deleteRecursively(ch);
        }
        if (!f.delete()) f.deleteOnExit();
    }
}
