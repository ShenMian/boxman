package my.boxman.compat;

import javax.swing.*;
import java.awt.*;

/**
 * 原版
 * {@code new AlertDialog.Builder(ctx, AlertDialog.THEME_HOLO_DARK)
 *        .setTitle(t).setMessage(m).setPositiveButton("确定", null).setCancelable(false)}
 * 的等价物：Holo 外壳 + 标题 + 一段正文 + 一个「确定」按钮。
 *
 * <p>几何照 framework 的 {@code layout/alert_dialog_holo.xml}（1dp = 1px）：
 * 正文 {@code TextView#message} 是 {@code textAppearanceMedium}（16sp）、
 * {@code paddingStart/End=16dp}、{@code paddingTop/Bottom=8dp}，背景透明
 * （所以看到的是 9-patch 的 {@code #FF282828}）；所在 {@code contentPanel} 的
 * {@code minHeight=64dp} 由 {@link HoloAlertDialog} 统一处理。
 */
public class HoloMessageDialog extends HoloAlertDialog {

    /** {@code message} 的左右 padding */
    private static final int PAD_H = 16;
    /** {@code message} 的上下 padding */
    private static final int PAD_V = 8;
    /** 正文自动换行宽度（原版是 match_parent，Swing 的 html 标签需要一个显式宽度） */
    private static final int WRAP_WIDTH = 284;

    public HoloMessageDialog(Frame owner, String title, String message, String buttonText) {
        super(owner, title);
        setContentView(messageBody(message));
        addButton(buttonText, this::dispose);
    }

    /**
     * 原版 {@code AlertDialog} 的 {@code TextView#message} 区域。
     * 单按钮的 {@link HoloMessageDialog} 与双按钮的 {@link HoloConfirmDialog} 共用。
     */
    public static JComponent messageBody(String message) {
        JLabel text = new JLabel("<html><body style='width:" + WRAP_WIDTH + "px'>"
                + escape(message) + "</body></html>");
        text.setForeground(HoloContent.TEXT);      // bright_foreground_holo_dark
        text.setFont(HoloContent.font(Font.PLAIN));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(true);
        body.setBackground(PANEL_BG);              // 9-patch 填充色，message 本身透明
        body.setBorder(BorderFactory.createEmptyBorder(PAD_V, PAD_H, PAD_V, PAD_H));
        body.add(text, BorderLayout.CENTER);
        return body;
    }

    /** 正文是 HTML，转义掉会破坏标签的字符。 */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }
}
