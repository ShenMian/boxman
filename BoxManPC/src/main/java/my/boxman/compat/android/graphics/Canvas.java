package my.boxman.compat.android.graphics;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public class Canvas {
    private Graphics2D g2d;
    private final Deque<AffineTransform> transformStack = new ArrayDeque<>();
    private final Deque<Paint> paintStack = new ArrayDeque<>();

    public Canvas() {
    }

    public Canvas(BufferedImage targetImage) {
        if (targetImage != null) {
            this.g2d = targetImage.createGraphics();
            this.g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    public void setGraphics(Graphics2D g) {
        this.g2d = g;
        transformStack.clear();
        paintStack.clear();
    }

    public Graphics2D getGraphics() {
        return g2d;
    }

    public int save() {
        if (g2d != null) {
            transformStack.push(g2d.getTransform());
        }
        return transformStack.size();
    }

    public void restore() {
        if (g2d != null && !transformStack.isEmpty()) {
            g2d.setTransform(transformStack.pop());
        }
    }

    public void setMatrix(Matrix matrix) {
        if (g2d != null && matrix != null) {
            g2d.setTransform(matrix.toAffineTransform());
        }
    }

    public void translate(float dx, float dy) {
        if (g2d != null) {
            g2d.translate(dx, dy);
        }
    }

    public void scale(float sx, float sy) {
        if (g2d != null) {
            g2d.scale(sx, sy);
        }
    }

    public void scale(float sx, float sy, float px, float py) {
        if (g2d != null) {
            g2d.translate(px, py);
            g2d.scale(sx, sy);
            g2d.translate(-px, -py);
        }
    }

    public void rotate(float degrees) {
        if (g2d != null) {
            g2d.rotate(Math.toRadians(degrees));
        }
    }

    public void rotate(float degrees, float px, float py) {
        if (g2d != null) {
            g2d.rotate(Math.toRadians(degrees), px, py);
        }
    }

    public void drawBitmap(BufferedImage bitmap, float left, float top, Paint paint) {
        if (g2d != null && bitmap != null) {
            g2d.drawImage(bitmap, (int) left, (int) top, null);
        }
    }

    public void drawBitmap(BufferedImage bitmap, Rect src, Rect dst, Paint paint) {
        if (g2d != null && bitmap != null && dst != null) {
            if (src == null) {
                g2d.drawImage(bitmap, dst.left, dst.top, dst.width(), dst.height(), null);
            } else {
                g2d.drawImage(bitmap,
                        dst.left, dst.top, dst.right, dst.bottom,
                        src.left, src.top, src.right, src.bottom,
                        null);
            }
        }
    }

    public void drawRect(Rect r, Paint paint) {
        if (g2d != null && r != null && paint != null) {
            paint.applyTo(g2d);
            if (paint.getStyle() == Paint.Style.STROKE) {
                g2d.drawRect(r.left, r.top, r.width(), r.height());
            } else {
                g2d.fillRect(r.left, r.top, r.width(), r.height());
            }
        }
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        if (g2d != null && paint != null) {
            paint.applyTo(g2d);
            int x = (int) Math.min(left, right);
            int y = (int) Math.min(top, bottom);
            int w = (int) Math.abs(right - left);
            int h = (int) Math.abs(bottom - top);
            if (paint.getStyle() == Paint.Style.STROKE) {
                g2d.drawRect(x, y, w, h);
            } else {
                g2d.fillRect(x, y, w, h);
            }
        }
    }

    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        if (g2d != null && paint != null) {
            paint.applyTo(g2d);
            g2d.drawLine((int) startX, (int) startY, (int) stopX, (int) stopY);
        }
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        if (g2d != null && paint != null) {
            paint.applyTo(g2d);
            int x = (int) (cx - radius);
            int y = (int) (cy - radius);
            int d = (int) (radius * 2);
            if (paint.getStyle() == Paint.Style.STROKE) {
                g2d.drawOval(x, y, d, d);
            } else {
                g2d.fillOval(x, y, d, d);
            }
        }
    }

    public void drawText(String text, float x, float y, Paint paint) {
        if (g2d != null && text != null && paint != null) {
            paint.applyTo(g2d);
            g2d.drawString(text, (int) x, (int) y);
        }
    }
}
