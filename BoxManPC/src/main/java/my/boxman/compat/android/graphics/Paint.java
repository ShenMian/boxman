package my.boxman.compat.android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class Paint {
    public enum Style {
        FILL,
        STROKE,
        FILL_AND_STROKE
    }

    private Color color = Color.BLACK;
    private Style style = Style.FILL;
    private float strokeWidth = 1.0f;
    private Font font = new Font("Microsoft YaHei", Font.PLAIN, 12);

    private static final Graphics2D MEASURE_G2D;

    static {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        MEASURE_G2D = img.createGraphics();
    }

    public Paint() {
    }

    public void setARGB(int a, int r, int g, int b) {
        color = new Color(clamp(r), clamp(g), clamp(b), clamp(a));
    }

    public void setColor(int argb) {
        color = new Color(argb, true);
    }

    public void setColor(Color c) {
        if (c != null) this.color = c;
    }

    public Color getColor() {
        return color;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public Style getStyle() {
        return style;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void setTextSize(float textSize) {
        int size = Math.max(1, (int) textSize);
        font = font.deriveFont((float) size);
    }

    public Font getFont() {
        return font;
    }

    public void getTextBounds(String text, int start, int end, Rect bounds) {
        if (text == null || start >= end || bounds == null) return;
        String sub = text.substring(start, end);
        FontMetrics fm;
        synchronized (MEASURE_G2D) {
            MEASURE_G2D.setFont(font);
            fm = MEASURE_G2D.getFontMetrics();
        }
        int w = fm.stringWidth(sub);
        int h = fm.getAscent();
        bounds.set(0, 0, w, h);
    }

    public void applyTo(Graphics2D g2d) {
        if (g2d == null) return;
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(strokeWidth));
        g2d.setFont(font);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
