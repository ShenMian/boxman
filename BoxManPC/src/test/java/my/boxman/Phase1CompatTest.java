package my.boxman;

import my.boxman.gifencoder.GifEncoder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

public class Phase1CompatTest {

    @BeforeClass
    public static void setUp() {
        myMaps.sRoot = System.getProperty("user.dir") + "/build/test_boxman";
        myMaps.sPath = "/";
        new File(myMaps.sRoot).mkdirs();
    }

    @Test
    public void testSQLiteCompat() {
        mySQLite sql = mySQLite.getInstance();
        Assert.assertNotNull("mySQLite instance should not be null", sql);
        boolean opened = sql.openDataBase();
        Assert.assertTrue("Database should open successfully", opened);

        ArrayList<set_Node> sets0 = sql.get_GroupList(0);
        Assert.assertNotNull("Level sets 0 should not be null", sets0);
        Assert.assertTrue("Level sets 0 should have entries", sets0.size() > 0);

        String levelCount = sql.count_Level();
        Assert.assertNotNull("Level count string should not be null", levelCount);
        Assert.assertTrue("Level count should have format", levelCount.contains("/"));
        System.out.println("SQLite verification passed: sets=" + sets0.size() + ", levels=" + levelCount);
    }

    @Test
    public void testLoadSkins() {
        myMaps.loadSkins();
        Assert.assertNotNull("skinBit should be loaded", myMaps.skinBit);
        Assert.assertNotNull("WallPic should be sliced", myMaps.WallPic);
        Assert.assertNotNull("FloorPic should be sliced", myMaps.FloorPic);
        Assert.assertNotNull("BoxPic should be sliced", myMaps.BoxPic);
        Assert.assertNotNull("ManPic_u should be sliced", myMaps.ManPic_u);
        Assert.assertEquals(50, myMaps.WallPic.getWidth());
        Assert.assertEquals(50, myMaps.WallPic.getHeight());
        System.out.println("Skins verification passed: WallPic=" + myMaps.WallPic.getWidth() + "x" + myMaps.WallPic.getHeight());
    }

    @Test
    public void testClipboard() {
        String testStr = "BoxManTestString_" + System.currentTimeMillis();
        myMaps.saveClipper(testStr);
        String retrieved = myMaps.loadClipper();
        Assert.assertEquals("Clipboard text should match", testStr, retrieved);
        System.out.println("Clipboard verification passed: " + retrieved);
    }

    @Test
    public void testGifEncoder() {
        BufferedImage frame1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = frame1.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 100, 100);
        g1.dispose();

        BufferedImage frame2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = frame2.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 100, 100);
        g2.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        encoder.start(baos);
        encoder.setDelay(200);
        encoder.addFrame(frame1);
        encoder.addFrame(frame2);
        encoder.finish();

        byte[] gifBytes = baos.toByteArray();
        Assert.assertTrue("GIF bytes should not be empty", gifBytes.length > 0);
        String header = new String(gifBytes, 0, 6);
        Assert.assertEquals("GIF89a", header);
        System.out.println("GifEncoder verification passed: bytes=" + gifBytes.length);
    }
}
