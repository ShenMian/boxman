package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;

import javax.swing.*;
import java.awt.*;

/**
 * RGB Color Picker Dialog for BoxMan PC (Swing Port).
 *
 * <p>外壳用 {@link HoloAlertDialog}。标题按原版 {@code myGameView.java:4257} 的
 * {@code setTitle("设置背景色:")} 取 <b>「设置背景色:」</b>，按钮 取消 / 确定。
 *
 * <p>内容按原版 {@code res/layout/color_dialog.xml} 还原：
 * 「6dp {@code #363636} 条 → 20dp 空行 → 居中的 108dp 黑框套 104dp 白框套 100dp 色块
 * → 20dp 空行 → 6dp 条 → 红/绿/蓝三行（{@code TextView} + 256dp {@code SeekBar}，行间 20dp）
 * → 20dp 空行」。
 *
 * <p>原版没有数值读数，这里也<b>不放</b>（否则 256dp 的滑杆宽度会被挤掉）；
 * 为兼容既有调用点仍保留 {@link #rVal}/{@link #gVal}/{@link #bVal} 三个字段并继续更新其文本。
 */
public class ColorDialog extends HoloAlertDialog {

    public interface ColorSelectListener {
        void onColorSelected(Color color);
    }

    public JSlider rSlider, gSlider, bSlider;
    public JLabel rVal, gVal, bVal;
    public JPanel previewPanel;
    public JButton btOK, btCancel;

    private final ColorSelectListener listener;
    private Color selectedColor;

    public ColorDialog(Frame parent, Color initColor, ColorSelectListener listener) {
        super(parent, "设置背景色:");
        Color init = initColor != null ? initColor : Color.BLACK;
        this.selectedColor = init;
        this.listener = listener;
        initUI(init);
    }

    private void initUI(Color init) {
        rVal = valueLabel(init.getRed());
        gVal = valueLabel(init.getGreen());
        bVal = valueLabel(init.getBlue());

        rSlider = HoloContent.slider(init.getRed(), 256);
        gSlider = HoloContent.slider(init.getGreen(), 256);
        bSlider = HoloContent.slider(init.getBlue(), 256);
        rSlider.addChangeListener(e -> {
            rVal.setText(String.valueOf(rSlider.getValue()));
            updatePreview();
        });
        gSlider.addChangeListener(e -> {
            gVal.setText(String.valueOf(gSlider.getValue()));
            updatePreview();
        });
        bSlider.addChangeListener(e -> {
            bVal.setText(String.valueOf(bSlider.getValue()));
            updatePreview();
        });

        // 108dp 黑框 → 104dp 白框 → 100dp 色块（原版 dialog_bk_color）
        previewPanel = new JPanel();
        previewPanel.setBackground(init);
        previewPanel.setPreferredSize(new Dimension(100, 100));

        JPanel white = new JPanel(new GridBagLayout());
        white.setBackground(Color.WHITE);
        white.setPreferredSize(new Dimension(104, 104));
        white.add(previewPanel);

        JPanel black = new JPanel(new GridBagLayout());
        black.setBackground(Color.BLACK);
        black.setPreferredSize(new Dimension(108, 108));
        black.add(white);

        JPanel swatchRow = new JPanel(new GridBagLayout());
        swatchRow.setOpaque(false);
        swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        swatchRow.add(black);

        setContentView(HoloContent.column(
                HoloContent.band(),
                HoloContent.gap(20),
                swatchRow,
                HoloContent.gap(20),
                HoloContent.band(),
                HoloContent.tightRow(HoloContent.label("红："), rSlider),
                HoloContent.gap(20),
                HoloContent.tightRow(HoloContent.label("绿："), gSlider),
                HoloContent.gap(20),
                HoloContent.tightRow(HoloContent.label("蓝："), bSlider),
                HoloContent.gap(20)));

        btCancel = addButton("取消", this::dispose);
        btOK = addButton("确定", () -> {
            selectedColor = new Color(rSlider.getValue(), gSlider.getValue(), bSlider.getValue());
            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            dispose();
        });
        setDefaultButton(btOK);
    }

    private static JLabel valueLabel(int value) {
        JLabel l = new JLabel(String.valueOf(value), SwingConstants.CENTER);
        l.setFont(HoloContent.font(Font.PLAIN));
        l.setForeground(HoloContent.TEXT);
        return l;
    }

    private void updatePreview() {
        previewPanel.setBackground(new Color(
                rSlider.getValue(), gSlider.getValue(), bSlider.getValue()));
        previewPanel.repaint();
    }

    public Color getSelectedColor() {
        return selectedColor;
    }
}
