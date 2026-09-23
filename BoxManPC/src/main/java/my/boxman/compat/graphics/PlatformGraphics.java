package my.boxman.compat.graphics;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class PlatformGraphics {
    public static BufferedImage createBitmap(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage decodeFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            return ImageIO.read(f);
        } catch (Exception e) {
            return null;
        }
    }

    public static BufferedImage decodeResource(String name) {
        try {
            String path = "/drawable/" + (name.endsWith(".png") ? name : name + ".png");
            InputStream is = PlatformGraphics.class.getResourceAsStream(path);
            if (is == null) {
                path = "/drawable/" + name;
                is = PlatformGraphics.class.getResourceAsStream(path);
            }
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Rectangle toRect(int l, int t, int r, int b) {
        return new Rectangle(l, t, Math.max(0, r - l), Math.max(0, b - t));
    }
}
