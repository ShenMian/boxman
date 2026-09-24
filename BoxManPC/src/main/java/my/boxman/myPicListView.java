package my.boxman;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import my.boxman.compat.UiWindow;

/**
 * Image Browser Grid View for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myPicListView Activity.
 */
public class myPicListView extends JFrame {

    public JPanel gridPanel;
    public JScrollPane scrollPane;
    public List<File> imageFiles = new ArrayList<>();

    public myPicListView() {
        setTitle("图片列表 - 推箱快手");
        UiWindow.applyPhoneSize(this);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        loadImagesFromDefaultDir();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(new GridLayout(0, 4, 8, 8));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scrollPane = new JScrollPane(gridPanel);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton btBrowse = new JButton("选择本地文件...");
        btBrowse.addActionListener(e -> browseLocalImage());
        JButton btClose = new JButton("关闭");
        btClose.addActionListener(e -> dispose());

        bottomBar.add(btBrowse);
        bottomBar.add(btClose);
        add(bottomBar, BorderLayout.SOUTH);
    }

    public void loadImagesFromDefaultDir() {
        gridPanel.removeAll();
        imageFiles.clear();

        File dir = new File(myMaps.sRoot + myMaps.sPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
            });
            if (files != null) {
                for (File f : files) {
                    imageFiles.add(f);
                    gridPanel.add(createImageCard(f));
                }
            }
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel createImageCard(File file) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        card.setPreferredSize(new Dimension(140, 140));

        JLabel imgLabel = new JLabel("加载中...", SwingConstants.CENTER);
        card.add(imgLabel, BorderLayout.CENTER);

        JLabel nameLabel = new JLabel(file.getName(), SwingConstants.CENTER);
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        card.add(nameLabel, BorderLayout.SOUTH);

        // Async thumbnail loading
        SwingUtilities.invokeLater(() -> {
            try {
                BufferedImage original = ImageIO.read(file);
                if (original != null) {
                    Image scaled = original.getScaledInstance(120, 100, Image.SCALE_SMOOTH);
                    imgLabel.setIcon(new ImageIcon(scaled));
                    imgLabel.setText("");
                }
            } catch (Exception ignored) {
                imgLabel.setText("损坏图片");
            }
        });

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openInRecog(file);
                }
            }
        });

        return card;
    }

    private void openInRecog(File file) {
        try {
            myMaps.edPict = ImageIO.read(file);
            myRecogView recog = new myRecogView();
            recog.setVisible(true);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "打开图片失败: " + ex.getMessage());
        }
    }

    private void browseLocalImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件 (*.png, *.jpg, *.jpeg, *.bmp)", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openInRecog(chooser.getSelectedFile());
        }
    }
}
