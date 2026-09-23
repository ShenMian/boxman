package my.boxman;

import javax.swing.*;
import java.awt.*;

/**
 * RGB Color Picker Dialog for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's color_dialog.xml.
 */
public class ColorDialog extends JDialog {

    public interface ColorSelectListener {
        void onColorSelected(Color color);
    }

    public JSlider rSlider, gSlider, bSlider;
    public JLabel rVal, gVal, bVal;
    public JPanel previewPanel;
    public JButton btOK, btCancel;

    private Color initialColor;
    private Color selectedColor;
    private ColorSelectListener listener;

    public ColorDialog(Frame parent, Color initColor, ColorSelectListener listener) {
        super(parent, "选择背景颜色", true);
        this.initialColor = initColor != null ? initColor : Color.BLACK;
        this.selectedColor = this.initialColor;
        this.listener = listener;

        setSize(420, 280);
        setLocationRelativeTo(parent);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        JPanel sliderPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        sliderPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        // Red
        JPanel pR = new JPanel(new BorderLayout(6, 0));
        pR.add(new JLabel("R:"), BorderLayout.WEST);
        rSlider = new JSlider(0, 255, initialColor.getRed());
        rVal = new JLabel(String.valueOf(initialColor.getRed()), SwingConstants.CENTER);
        rVal.setPreferredSize(new Dimension(35, 20));
        rSlider.addChangeListener(e -> {
            rVal.setText(String.valueOf(rSlider.getValue()));
            updatePreview();
        });
        pR.add(rSlider, BorderLayout.CENTER);
        pR.add(rVal, BorderLayout.EAST);

        // Green
        JPanel pG = new JPanel(new BorderLayout(6, 0));
        pG.add(new JLabel("G:"), BorderLayout.WEST);
        gSlider = new JSlider(0, 255, initialColor.getGreen());
        gVal = new JLabel(String.valueOf(initialColor.getGreen()), SwingConstants.CENTER);
        gVal.setPreferredSize(new Dimension(35, 20));
        gSlider.addChangeListener(e -> {
            gVal.setText(String.valueOf(gSlider.getValue()));
            updatePreview();
        });
        pG.add(gSlider, BorderLayout.CENTER);
        pG.add(gVal, BorderLayout.EAST);

        // Blue
        JPanel pB = new JPanel(new BorderLayout(6, 0));
        pB.add(new JLabel("B:"), BorderLayout.WEST);
        bSlider = new JSlider(0, 255, initialColor.getBlue());
        bVal = new JLabel(String.valueOf(initialColor.getBlue()), SwingConstants.CENTER);
        bVal.setPreferredSize(new Dimension(35, 20));
        bSlider.addChangeListener(e -> {
            bVal.setText(String.valueOf(bSlider.getValue()));
            updatePreview();
        });
        pB.add(bSlider, BorderLayout.CENTER);
        pB.add(bVal, BorderLayout.EAST);

        sliderPanel.add(pR);
        sliderPanel.add(pG);
        sliderPanel.add(pB);
        add(sliderPanel, BorderLayout.NORTH);

        // Preview
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        centerPanel.add(new JLabel("颜色预览:"), BorderLayout.NORTH);
        previewPanel = new JPanel();
        previewPanel.setBackground(initialColor);
        previewPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        previewPanel.setPreferredSize(new Dimension(100, 50));
        centerPanel.add(previewPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Buttons
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btOK = new JButton("确定");
        btOK.addActionListener(e -> {
            selectedColor = new Color(rSlider.getValue(), gSlider.getValue(), bSlider.getValue());
            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            dispose();
        });
        btCancel = new JButton("取消");
        btCancel.addActionListener(e -> dispose());
        bottomBar.add(btOK);
        bottomBar.add(btCancel);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updatePreview() {
        previewPanel.setBackground(new Color(rSlider.getValue(), gSlider.getValue(), bSlider.getValue()));
        previewPanel.repaint();
    }

    public Color getSelectedColor() {
        return selectedColor;
    }
}
