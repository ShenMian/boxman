package my.boxman.compat;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * 把顶层窗口调整成原版 Android 的手机竖屏尺寸。
 *
 * <p>{@code AndroidManifest.xml} 里**所有 18 个 Activity 都是
 * {@code android:screenOrientation="portrait"}**，所以每一个 PC 窗口都应保持竖屏，
 * 而不是 PC 习惯的横屏 800×600。
 *
 * <p>尺寸取法：原版截图 1260×2844 px、density ≈ 3.4；扣掉状态栏（y 0~139）与导航栏
 * （y 2790~2843）后，应用可用区域约 **370dp × 780dp**。PC 端按 1dp = 1px 直接取 370×780。
 * 三个 dp 锚点互相印证：ActionBar 蓝条 162px / 48dp ≈ 3.375、{@code paddingLeft="40dp"}
 * 的文字左边界 138px ≈ 3.4、行距 115.4px / 34dp ≈ 3.39。
 *
 * <p>用法：把原来的 {@code setSize(800, 600)} 换成 {@code UiWindow.applyPhoneSize(this)}。
 */
public final class UiWindow {

    /** 原版应用可用区宽度（1dp = 1px） */
    public static final int PHONE_WIDTH = 370;
    /** 原版应用可用区高度（1dp = 1px） */
    public static final int PHONE_HEIGHT = 780;
    /** 窗口最小尺寸，避免被用户拖到不可用 */
    public static final int MIN_WIDTH = 300;
    public static final int MIN_HEIGHT = 400;

    private UiWindow() {
    }

    /** 默认手机内容区 370×780 */
    public static void applyPhoneSize(Window window) {
        applyPhoneSize(window, PHONE_WIDTH, PHONE_HEIGHT);
    }

    /**
     * 按给定的「内容区」尺寸套用手机竖屏比例：设置内容区首选尺寸 → {@code pack()} →
     * 屏幕放不下时按桌面可用区域收缩 → 居中。
     */
    public static void applyPhoneSize(Window window, int contentWidth, int contentHeight) {
        Container pane = contentPaneOf(window);
        if (pane != null) {
            pane.setPreferredSize(new Dimension(contentWidth, contentHeight));
        }
        window.pack();

        // 最小尺寸必须在 pack() 之后再设，否则会把 pack 的结果顶大
        window.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));

        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.min(window.getWidth(), Math.max(MIN_WIDTH, usable.width - 16));
        int h = Math.min(window.getHeight(), Math.max(MIN_HEIGHT, usable.height - 16));
        if (w != window.getWidth() || h != window.getHeight()) {
            window.setSize(w, h);
        }
        window.setLocationRelativeTo(null);
    }

    private static Container contentPaneOf(Window window) {
        if (window instanceof JFrame) {
            return ((JFrame) window).getContentPane();
        }
        if (window instanceof JDialog) {
            return ((JDialog) window).getContentPane();
        }
        return null;
    }
}
