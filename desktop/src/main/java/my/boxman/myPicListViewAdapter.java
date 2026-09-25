package my.boxman;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片列表适配器 —— 原版 {@code myPicListViewAdapter.java}（183 行）的 PC 等价物。
 *
 * <p>原版是 {@code BaseAdapter} + {@code AsyncTask} 异步解码 + {@code SparseArray}
 * 缩略图缓存；PC 没有 {@code AsyncTask}/{@code Bitmap.recycle()}，改成
 * 「按需解码 + {@code Map} 缓存 + 丢弃引用交给 GC」。
 *
 * <p>缩略图尺寸：原版 {@code mWidth=350, mHeight=500}（**px**），
 * 按本项目 density≈3.4 折算 ≈ <b>103 × 147 dp</b>（1dp=1px）。
 * ⚠️ 原版是 {@code createScaledBitmap(w, h, false)} **强制拉伸**（不保比例），
 * PC 这里改成**等比缩放后居中**：拉伸会把关卡截图压扁，与原版
 * {@code ImageView.ScaleType.CENTER_INSIDE} 的最终观感不一致，故不照抄拉伸。
 */
public class myPicListViewAdapter {

    /** 350px / 500px 按 density 3.4 折算 */
    public static final int THUMB_W = 103;
    public static final int THUMB_H = 147;

    private final Map<Integer, BufferedImage> cache = new HashMap<Integer, BufferedImage>();

    /** 原版 {@code getCount()} */
    public int getCount() {
        return myMaps.mFile_List.size();
    }

    /** 原版 {@code getView} 里的 {@code mFile_List.get(position)} */
    public String getFileName(int position) {
        return myMaps.mFile_List.get(position);
    }

    /** 当前位置下的完整文件对象（原版 {@code sRoot + myPathList[m_Sets[36]] + 文件名}） */
    public File getFile(int position) {
        return getFileOf(getFileName(position));
    }

    /** 按文件名取文件对象（{@code loadEDPic} 用的是文件名而不是下标） */
    public File getFileOf(String name) {
        return new File(myMaps.picDir() + name);
    }

    /**
     * 原版 {@code getBitmapFromUrl(key)}：有缓存直接取，没有才生成缩略图。
     * 失败时返回 1×1 占位图 —— 与原版 {@code Bitmap.createBitmap(1,1,RGB_565)} 一致。
     */
    public BufferedImage getBitmap(int key) {
        BufferedImage bmp = cache.get(key);
        if (bmp == null) {
            bmp = getThumbnail(getFileName(key));
            if (bmp == null) bmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            cache.put(key, bmp);
        }
        return bmp;
    }

    /** 原版 {@code getThumbnail(pathName, width, height)} */
    public BufferedImage getThumbnail(String name) {
        try {
            BufferedImage src = ImageIO.read(new File(myMaps.picDir() + name));
            if (src == null) return null;
            return scaleToFit(src, THUMB_W, THUMB_H);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 原版 {@code recycleBitmapCaches(from, to)} —— PC 没有 {@code recycle()}，
     * 直接丢引用让 GC 回收。
     */
    public void clearCache(int fromPosition, int toPosition) {
        for (int i = fromPosition; i < toPosition; i++) cache.remove(i);
    }

    public void clearCache() {
        cache.clear();
    }

    /** 等比缩放并居中放进 {@code w×h} 的画布（不拉伸，见类注释） */
    static BufferedImage scaleToFit(BufferedImage src, int w, int h) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.min((double) w / sw, (double) h / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getScaledInstance(dw, dh, Image.SCALE_SMOOTH), (w - dw) / 2, (h - dh) / 2, null);
        g.dispose();
        return out;
    }
}
