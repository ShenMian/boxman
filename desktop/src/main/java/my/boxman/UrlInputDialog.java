package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * 原版 {@code res/layout/get_uil_dialog.xml} —— 上下文菜单 case 9
 * 「添加比赛关卡(sokoban.ws)」弹出的「网站 / 期号」两栏输入框，标题「导入比赛关卡」。
 *
 * <p>布局（1dp = 1px）：
 * 6dp {@code #363636} 色条 → 右对齐行（{@code 网站: } + 220dp 输入框，初值 {@code myMaps.uil}）
 * → 6dp 色条 → 右对齐行（{@code 期号: } + 220dp 数字输入框，hint「默认最新一期的比赛」15sp）
 * → 6dp 色条。
 *
 * <p>原版两个输入框都带 {@code android:digits="0123456789"}（{@code dialog_uil} 连 URL
 * 也限制成纯数字，这是原版的怪癖，照抄不改）。
 *
 * <p>原版 {@code setOnKeyListener} 里「期号」框回车与「确定」等价；PC 端用
 * {@code JTextField.addActionListener} 表达。
 *
 * <p>⚠️ 这里只负责「收集两个输入」，真正的 {@code url_Num} 解析、{@code myMaps.uil} 规整、
 * 目标关卡集记录与下载都在 {@link BoxManPC#submitCompetition(long, String, String)} 里 ——
 * 与「对话框只出参数、Activity 出逻辑」的原版分工一致。
 */
public class UrlInputDialog extends HoloAlertDialog {

    /** 原版「确定」按钮 / 回车 里那段逻辑的出口。 */
    public interface OnSubmit {
        void submit(long setId, String uil, String numText);
    }

    /** {@code dialog_uil}：网站（初值 {@code myMaps.uil}）。 */
    public final JTextField tfUil;
    /** {@code dialog_num2}：期号（留空 = 最新一期）。 */
    public final JTextField tfNum;

    /** 期号框的 hint 文案与字号：原版 {@code new AbsoluteSizeSpan(15, true)}。 */
    static final String NUM_HINT = "默认最新一期的比赛";
    static final int HINT_TEXT_SIZE = 15;
    /** hint 的淡色（原版 hint 用的是 {@code textColorHint}，Holo 深色下是灰） */
    private static final Color HINT_FG = new Color(0x77, 0x77, 0x77);
    /** 两个输入框的宽度：{@code android:layout_width="220dp"}。 */
    private static final int FIELD_WIDTH = 220;
    /** 该行是 {@code gravity="right" + paddingRight 16dp}。 */
    private static final int ROW_PADDING_RIGHT = 16;

    private final long setId;
    private final OnSubmit onSubmit;

    public UrlInputDialog(Frame owner, long setId, OnSubmit onSubmit) {
        super(owner, "导入比赛关卡");
        this.setId = setId;
        this.onSubmit = onSubmit;

        tfUil = numberField(FIELD_WIDTH, myMaps.uil, null);
        tfNum = numberField(FIELD_WIDTH, "", NUM_HINT);

        setContentView(HoloContent.column(
                HoloContent.band(HoloContent.BAND, 6),
                rightAligned("网站: ", tfUil),
                HoloContent.band(HoloContent.BAND, 6),
                rightAligned("期号: ", tfNum),
                HoloContent.band(HoloContent.BAND, 6)));

        addButton("取消", null);
        JButton ok = addButton("确定", this::fireSubmit);
        setDefaultButton(ok);
        // 原版 setOnKeyListener 里 KEYCODE_ENTER 走的是同一段逻辑
        tfUil.addActionListener(e -> ok.doClick());
        tfNum.addActionListener(e -> ok.doClick());
    }

    /** 原版那一行是 {@code gravity="right" + paddingRight 16dp}，标签与输入框整体靠右。 */
    private static JComponent rightAligned(String labelText, JComponent field) {
        JPanel p = HoloContent.row(HoloContent.BAND, 0,
                HoloContent.label(labelText), field);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, ROW_PADDING_RIGHT));
        return p;
    }

    /**
     * 原版 {@code dialog_uil} / {@code dialog_num2}：{@code #242424} 底、16sp、
     * {@code padding 4dp}、{@code selectAllOnFocus}、{@code digits="0123456789"}。
     */
    private static JTextField numberField(int widthDp, String text, String hint) {
        HintField tf = new HintField(hint);
        tf.setText(text);
        tf.setFont(HoloContent.font(Font.PLAIN));
        tf.setBackground(HoloContent.FIELD_BG);
        tf.setForeground(HoloContent.TEXT);
        tf.setCaretColor(HoloContent.TEXT);
        tf.setBorder(new EmptyBorder(HoloContent.FIELD_PAD, HoloContent.FIELD_PAD,
                HoloContent.FIELD_PAD, HoloContent.FIELD_PAD));
        tf.setSelectionColor(Color.BLACK);                 // textColorHighlight="#000000"
        tf.setSelectedTextColor(HoloContent.TEXT);
        Dimension d = new Dimension(widthDp, tf.getPreferredSize().height);
        tf.setPreferredSize(d);
        tf.setMinimumSize(d);
        tf.setMaximumSize(d);
        tf.addFocusListener(new FocusAdapter() {           // selectAllOnFocus="true"
            @Override
            public void focusGained(FocusEvent e) {
                tf.selectAll();
            }
        });
        // 除了画出来的淡色 hint，再挂一份 toolTip（可访问性 + 便于测试断言）
        if (hint != null && !hint.isEmpty()) tf.setToolTipText(hint);
        ((AbstractDocument) tf.getDocument()).setDocumentFilter(new DigitsOnly());
        return tf;
    }

    /**
     * 空文本时绘制淡色 hint 的输入框 —— Swing 没有 {@code android:hint} 的等价物，
     * 而原版给「期号」框挂了 15sp 的 {@code AbsoluteSizeSpan} 作提示。
     */
    private static final class HintField extends JTextField {
        private final String hint;

        HintField(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hint == null || hint.isEmpty() || !getText().isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, HINT_TEXT_SIZE));
            g2.setColor(HINT_FG);
            Insets in = getInsets();
            g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
            g2.dispose();
        }
    }

    /** 只接受数字（原版 {@code NumberKeyListener} 的等价物）。 */
    private static final class DigitsOnly extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            fb.insertString(offset, digits(text), attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
                throws BadLocationException {
            fb.replace(offset, length, digits(text), attr);
        }

        private static String digits(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') sb.append(c);
            }
            return sb.toString();
        }
    }

    /** 原版「确定」按钮 / 回车的动作：把两个输入交出去后关闭自己。 */
    void fireSubmit() {
        String uil = tfUil.getText();
        String num = tfNum.getText();
        dispose();
        if (onSubmit != null) onSubmit.submit(setId, uil, num);
    }
}
