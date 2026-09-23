package my.boxman.compat.android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;

    private int action;
    private float x;
    private float y;
    private float rawX;
    private float rawY;
    private int pointerCount = 1;

    public MotionEvent() {
    }

    public MotionEvent(int action, float x, float y, float rawX, float rawY) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.rawX = rawX;
        this.rawY = rawY;
    }

    public int getAction() {
        return action;
    }

    public int getActionMasked() {
        return action & 0xff;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getX(int index) {
        return x;
    }

    public float getY(int index) {
        return y;
    }

    public float getRawX() {
        return rawX;
    }

    public float getRawY() {
        return rawY;
    }

    public int getPointerCount() {
        return pointerCount;
    }
}
