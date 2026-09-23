package my.boxman;

import my.boxman.compat.android.graphics.*;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Level Image Recognition Map View for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myRecogViewMap.
 */
public class myRecogViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    public myRecogView m_Recog;

    public int m_nRows = 10;
    public int m_nCols = 10;
    public float m_nWidth = 40;  // 格子像素宽度
    public int m_nObj = -1;      // 当前选中的物件类型
    public int cur_Row = -1, cur_Col = -1;

    public Matrix mMatrix = new Matrix();
    public Matrix mCurrentMatrix = new Matrix();
    private Matrix mMapMatrix = new Matrix();
    float m_fTop, m_fLeft, m_fScale, mScale;
    float[] values = new float[9];

    private int startX, startY;

    public myRecogViewMap() {
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() > 0 && getHeight() > 0) {
                    setArena();
                    repaint();
                }
            }
        });
    }

    public void Init(myRecogView v) {
        this.m_Recog = v;
    }

    public void setArena() {
        mMatrix.reset();
        int w = getWidth() > 0 ? getWidth() : 800;
        int h = getHeight() > 0 ? getHeight() : 600;
        int picW = (int) (m_nCols * m_nWidth);
        int picH = (int) (m_nRows * m_nWidth);

        RectF src = new RectF(0, 0, Math.max(10, picW), Math.max(10, picH));
        RectF dst = new RectF(0, 0, w, h);
        mMatrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);

        mCurrentMatrix.set(mMatrix);
        mMatrix.getValues(values);
        mScale = values[Matrix.MSCALE_X];
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = mScale;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Canvas canvas = new Canvas();
        canvas.setGraphics(g2d);
        onDraw(canvas);
        g2d.dispose();
    }

    public void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF333333);

        canvas.save();
        mCurrentMatrix.getValues(values);
        mMapMatrix.setValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = values[Matrix.MSCALE_X];
        canvas.setMatrix(mMapMatrix);

        // 1. Draw source recognition image if available
        if (myMaps.edPict != null) {
            Paint imgPaint = new Paint();
            canvas.drawBitmap(myMaps.edPict, 0, 0, imgPaint);
        }

        // 2. Draw Grid Lines
        Paint gridPaint = new Paint();
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setARGB(180, 0, 255, 255);

        for (int r = 0; r <= m_nRows; r++) {
            float y = r * m_nWidth;
            canvas.drawLine(0, y, m_nCols * m_nWidth, y, gridPaint);
        }
        for (int c = 0; c <= m_nCols; c++) {
            float x = c * m_nWidth;
            canvas.drawLine(x, 0, x, m_nRows * m_nWidth, gridPaint);
        }

        // 3. Draw recognized/placed elements
        if (m_Recog != null && m_Recog.m_cArray != null) {
            Paint textPaint = new Paint();
            textPaint.setTextSize(Math.max(12, m_nWidth * 0.6f));
            textPaint.setARGB(255, 255, 255, 0);

            for (int r = 0; r < m_nRows; r++) {
                for (int c = 0; c < m_nCols; c++) {
                    char ch = (r < m_Recog.m_cArray.length && c < m_Recog.m_cArray[r].length) ? m_Recog.m_cArray[r][c] : '-';
                    if (ch != '-' && ch != ' ' && ch != '\0') {
                        canvas.drawText(String.valueOf(ch), c * m_nWidth + m_nWidth * 0.25f, (r + 1) * m_nWidth - m_nWidth * 0.25f, textPaint);
                    }
                }
            }
        }

        // 4. Highlight clicked/current cell
        if (cur_Row >= 0 && cur_Col >= 0 && cur_Row < m_nRows && cur_Col < m_nCols) {
            Paint highlightPaint = new Paint();
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setARGB(255, 255, 0, 0);
            canvas.drawRect(cur_Col * m_nWidth, cur_Row * m_nWidth,
                    (cur_Col + 1) * m_nWidth, (cur_Row + 1) * m_nWidth, highlightPaint);
        }

        canvas.restore();
    }

    private void doClick(int screenX, int screenY) {
        if (m_fScale <= 0) m_fScale = 1.0f;
        int col = (int) ((screenX - m_fLeft) / m_fScale / m_nWidth);
        int row = (int) ((screenY - m_fTop) / m_fScale / m_nWidth);

        if (row >= 0 && row < m_nRows && col >= 0 && col < m_nCols) {
            cur_Row = row;
            cur_Col = col;
            if (m_Recog != null) {
                m_Recog.onCellClicked(row, col);
            }
            repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        startX = e.getX();
        startY = e.getY();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (Math.abs(e.getX() - startX) < 5 && Math.abs(e.getY() - startY) < 5) {
            doClick(e.getX(), e.getY());
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        float dx = e.getX() - startX;
        float dy = e.getY() - startY;
        startX = e.getX();
        startY = e.getY();
        mCurrentMatrix.postTranslate(dx, dy);
        repaint();
    }

    @Override public void mouseMoved(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        float factor = e.getWheelRotation() < 0 ? 1.15f : 0.85f;
        mCurrentMatrix.postScale(factor, factor, e.getX(), e.getY());
        repaint();
    }
}
