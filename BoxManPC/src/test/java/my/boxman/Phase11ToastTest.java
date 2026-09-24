package my.boxman;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

/**
 * 阶段 5.5 验收：Toast → PC 提示条。
 *
 * <p>原版 {@code MyToast} 是个真 Toast（浮层、1.5s/3s 自动消失、同一时刻只有一个）。
 * 移植前 PC 版是 13 行空壳（只 {@code System.out.println}），而全项目有 91 处调用
 * ——等于所有用户反馈都被丢掉了。这个用例把行为锁住。
 */
public class Phase11ToastTest {

    private JFrame frame;

    @After
    public void tearDown() throws Exception {
        MyToast.dismiss();
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
            frame = null;
        }
    }

    private JFrame showFrame() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("toast-test");
            frame.setSize(370, 780);
            frame.setLocation(200, 120);
            frame.setVisible(true);
        });
        return frame;
    }

    @Test
    public void testShortToastShowsAndAutoHides() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        JFrame f = showFrame();

        SwingUtilities.invokeAndWait(() -> MyToast.showToast(f, "关卡已保存！", MyToast.LENGTH_SHORT));

        Assert.assertTrue("提示条应立即可见", MyToast.isToastShowing());
        Assert.assertEquals("关卡已保存！", MyToast.currentToastText());

        // LENGTH_SHORT = 1500ms，留一点余量
        Thread.sleep(2200);
        Assert.assertFalse("短提示应在 1.5 秒后自动消失", MyToast.isToastShowing());

        System.out.println("[Phase11] 短提示显示 → 自动消失 通过");
    }

    @Test
    public void testRepeatedToastReusesSingleWindow() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        JFrame f = showFrame();

        SwingUtilities.invokeAndWait(() -> {
            MyToast.showToast(f, "第一条", MyToast.LENGTH_LONG);
            MyToast.showToast(f, "第二条", MyToast.LENGTH_LONG);
            MyToast.showToast(f, "第三条", MyToast.LENGTH_LONG);
        });

        // 原版语义：复用同一个 Toast，只是换文字 —— 不应该叠出三个
        Assert.assertEquals("连续调用应复用同一个提示条，只换文字", "第三条", MyToast.currentToastText());
        Assert.assertTrue(MyToast.isToastShowing());

        Thread.sleep(1200);
        Assert.assertTrue("后一次调用应重置计时，1.2 秒时仍可见（LENGTH_LONG=3s）", MyToast.isToastShowing());

        Thread.sleep(2200);
        Assert.assertFalse("长提示应在 3 秒后消失", MyToast.isToastShowing());

        System.out.println("[Phase11] 连续提示复用同一条 通过");
    }

    @Test
    public void testNullContextFallsBackToActiveWindow() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        showFrame();

        // 模拟原版遗留的 myMaps.ctxDealFile（PC 上为 null）
        SwingUtilities.invokeAndWait(() -> MyToast.showToast(null, "空 context 也要能弹", MyToast.LENGTH_SHORT));

        Assert.assertTrue("context 为 null 时应退化为当前活动窗口，提示条仍可见",
                MyToast.isToastShowing());
        Assert.assertEquals("空 context 也要能弹", MyToast.currentToastText());

        System.out.println("[Phase11] null context 兜底 通过");
    }

    @Test
    public void testToastDoesNotDisturbWindowLayout() throws Exception {
        Assume.assumeFalse("需要图形环境", GraphicsEnvironment.isHeadless());
        JFrame f = showFrame();

        SwingUtilities.invokeAndWait(() -> f.getContentPane().setLayout(new BorderLayout()));
        Dimension before = f.getContentPane().getSize();
        int componentCount = f.getContentPane().getComponentCount();

        SwingUtilities.invokeAndWait(() -> MyToast.showToast(f, "不应该影响布局", MyToast.LENGTH_SHORT));

        Assert.assertEquals("提示条不得往窗口里加组件", componentCount,
                f.getContentPane().getComponentCount());
        Assert.assertEquals("提示条不得改变窗口尺寸", before, f.getContentPane().getSize());

        System.out.println("[Phase11] 提示条不干扰布局 通过（内容区组件数=" + componentCount + "）");
    }
}
