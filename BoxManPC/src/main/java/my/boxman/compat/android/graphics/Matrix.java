package my.boxman.compat.android.graphics;

import java.awt.geom.AffineTransform;

public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    public enum ScaleToFit {
        FILL,
        START,
        CENTER,
        END
    }

    private final float[] values = new float[9];

    public Matrix() {
        reset();
    }

    public Matrix(Matrix src) {
        set(src);
    }

    public void reset() {
        values[MSCALE_X] = 1.0f;
        values[MSKEW_X] = 0.0f;
        values[MTRANS_X] = 0.0f;
        values[MSKEW_Y] = 0.0f;
        values[MSCALE_Y] = 1.0f;
        values[MTRANS_Y] = 0.0f;
        values[MPERSP_0] = 0.0f;
        values[MPERSP_1] = 0.0f;
        values[MPERSP_2] = 1.0f;
    }

    public void set(Matrix src) {
        if (src != null) {
            System.arraycopy(src.values, 0, this.values, 0, 9);
        }
    }

    public void getValues(float[] values) {
        if (values != null && values.length >= 9) {
            System.arraycopy(this.values, 0, values, 0, 9);
        }
    }

    public void setValues(float[] values) {
        if (values != null && values.length >= 9) {
            System.arraycopy(values, 0, this.values, 0, 9);
        }
    }

    public void postTranslate(float dx, float dy) {
        values[MTRANS_X] += dx;
        values[MTRANS_Y] += dy;
    }

    public void postScale(float sx, float sy, float px, float py) {
        postTranslate(-px, -py);
        values[MSCALE_X] *= sx;
        values[MSKEW_X] *= sx;
        values[MTRANS_X] *= sx;
        values[MSKEW_Y] *= sy;
        values[MSCALE_Y] *= sy;
        values[MTRANS_Y] *= sy;
        postTranslate(px, py);
    }

    public void postScale(float sx, float sy) {
        values[MSCALE_X] *= sx;
        values[MSKEW_X] *= sx;
        values[MTRANS_X] *= sx;
        values[MSKEW_Y] *= sy;
        values[MSCALE_Y] *= sy;
        values[MTRANS_Y] *= sy;
    }

    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (src == null || dst == null || src.width() <= 0 || src.height() <= 0) {
            reset();
            return false;
        }

        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();

        if (stf == ScaleToFit.CENTER || stf == ScaleToFit.START || stf == ScaleToFit.END) {
            sx = sy = Math.min(sx, sy);
        }

        float tx = dst.left - src.left * sx;
        float ty = dst.top - src.top * sy;

        if (stf == ScaleToFit.CENTER) {
            float diffW = dst.width() - src.width() * sx;
            float diffH = dst.height() - src.height() * sy;
            tx += diffW / 2.0f;
            ty += diffH / 2.0f;
        } else if (stf == ScaleToFit.END) {
            tx += dst.width() - src.width() * sx;
            ty += dst.height() - src.height() * sy;
        }

        reset();
        values[MSCALE_X] = sx;
        values[MSCALE_Y] = sy;
        values[MTRANS_X] = tx;
        values[MTRANS_Y] = ty;
        return true;
    }

    public AffineTransform toAffineTransform() {
        return new AffineTransform(
                values[MSCALE_X],
                values[MSKEW_Y],
                values[MSKEW_X],
                values[MSCALE_Y],
                values[MTRANS_X],
                values[MTRANS_Y]
        );
    }
}
