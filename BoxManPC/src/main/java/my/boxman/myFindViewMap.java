package my.boxman;

import my.boxman.compat.android.graphics.*;
import my.boxman.compat.android.view.MotionEvent;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.*;

/**
 * Similar Level Comparison Map View for BoxMan PC (Swing Port).
 * 1:1 functional equivalent of Android's myFindViewMap.
 */
public class myFindViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    public int m_iR = -1, m_iC = -1;
    public myFindView m_Find;

    Paint myPaint = new Paint();
    public char[][] m_cArray;

    public boolean m_Level_All = false;  // 是否显示全貌

    int m_nPicWidth, m_nPicHeight, m_nRows, m_nCols;
    int m_PicWidth = 50;
    public Matrix mMatrix = new Matrix();
    public Matrix mCurrentMatrix = new Matrix();
    private Matrix mMapMatrix = new Matrix();
    float m_fTop, m_fLeft, m_fScale, mScale;
    public float mMaxScale = 5;
    float[] values = new float[9];

    private Rect rtKW = new Rect(0, 0, 50, 50);
    private Rect rtKF = new Rect(0, 50, 50, 100);
    private Rect rtKD = new Rect(0, 100, 50, 150);
    private Rect rtKB = new Rect(50, 50, 100, 100);
    private Rect rtKBD = new Rect(50, 100, 100, 150);
    private Rect rtKM = new Rect(100, 50, 150, 100);
    private Rect rtKMD = new Rect(100, 100, 150, 150);
    private Rect rt = new Rect();

    private int startX, startY;

    public myFindViewMap() {
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() > 0 && getHeight() > 0 && m_cArray != null) {
                    setArena();
                    repaint();
                }
            }
        });
        initSkin();
    }

    public void Init(myFindView v) {
        this.m_Find = v;
    }

    private void initSkin() {
        int off = myMaps.isSkin_200;
        rtKW.set(0, 0, 50, 50);
        rtKF.set(0, 50 + off, 50, 100 + off);
        rtKD.set(0, 100 + off, 50, 150 + off);
        rtKB.set(50, 50 + off, 100, 100 + off);
        rtKBD.set(50, 100 + off, 100, 150 + off);
        rtKM.set(100, 50 + off, 150, 100 + off);
        rtKMD.set(100, 100 + off, 150, 150 + off);
    }

    public void setMapArray(char[][] map, int rows, int cols) {
        this.m_cArray = map;
        this.m_nRows = rows;
        this.m_nCols = cols;
        initArena();
    }

    private Matrix getInnerMatrix(Matrix matrix) {
        if (matrix == null) matrix = new Matrix();
        else matrix.reset();
        int w = getWidth() > 0 ? getWidth() : myMaps.m_nWinWidth;
        int h = getHeight() > 0 ? getHeight() : myMaps.m_nWinHeight;
        RectF tempSrc = new RectF(0, 0, m_nPicWidth > 0 ? m_nPicWidth : 100, m_nPicHeight > 0 ? m_nPicHeight : 100);
        RectF tempDst = new RectF(0, 0, w, h);
        matrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);
        return matrix;
    }

    public void initArena() {
        m_nPicWidth = m_PicWidth * m_nCols;
        m_nPicHeight = m_PicWidth * m_nRows;
        setArena();
    }

    public void setArena() {
        mMatrix = getInnerMatrix(mMatrix);
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
        canvas.drawColor(0xFF222222);

        if (m_cArray == null) return;

        canvas.save();
        mCurrentMatrix.getValues(values);
        mMapMatrix.setValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = values[Matrix.MSCALE_X];
        canvas.setMatrix(mMapMatrix);

        myPaint.setARGB(255, 0, 0, 0);

        for (int r = 0; r < m_nRows; r++) {
            for (int c = 0; c < m_nCols; c++) {
                rt.set(m_PicWidth * c, m_PicWidth * r, m_PicWidth * c + m_PicWidth, m_PicWidth * r + m_PicWidth);
                char ch = (r < m_cArray.length && c < m_cArray[r].length) ? m_cArray[r][c] : '-';

                if (myMaps.skinBit != null) {
                    canvas.drawBitmap(myMaps.skinBit, rtKF, rt, myPaint);

                    if (ch == '.' || ch == '*' || ch == '+') {
                        canvas.drawBitmap(myMaps.skinBit, rtKD, rt, myPaint);
                    }

                    switch (ch) {
                        case '#': canvas.drawBitmap(myMaps.skinBit, rtKW, rt, myPaint); break;
                        case '$': canvas.drawBitmap(myMaps.skinBit, rtKB, rt, myPaint); break;
                        case '*': canvas.drawBitmap(myMaps.skinBit, rtKBD, rt, myPaint); break;
                        case '@': canvas.drawBitmap(myMaps.skinBit, rtKM, rt, myPaint); break;
                        case '+': canvas.drawBitmap(myMaps.skinBit, rtKMD, rt, myPaint); break;
                    }
                }
            }
        }

        // Draw selection frame for similar region
        if (m_Find != null && m_Find.mSelect != null) {
            int idx = m_Find.m_Level ? 0 : 1;
            int top = m_Find.mSelect[idx][0];
            int left = m_Find.mSelect[idx][1];
            int bottom = m_Find.mSelect[idx][2];
            int right = m_Find.mSelect[idx][3];

            if (top >= 0 && left >= 0 && bottom >= top && right >= left) {
                Paint selPaint = new Paint();
                selPaint.setStyle(Paint.Style.STROKE);
                selPaint.setARGB(200, 255, 0, 0);
                canvas.drawRect(left * m_PicWidth, top * m_PicWidth,
                        (right + 1) * m_PicWidth, (bottom + 1) * m_PicWidth, selPaint);
            }
        }

        canvas.restore();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        startX = e.getX();
        startY = e.getY();
    }

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && m_Find != null) {
            m_Find.switchLevel();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        float dx = e.getX() - startX;
        float dy = e.getY() - startY;
        startX = e.getX();
        startY = e.getY();
        mCurrentMatrix.postTranslate(dx, dy);
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        float factor = e.getWheelRotation() < 0 ? 1.15f : 0.85f;
        mCurrentMatrix.postScale(factor, factor, e.getX(), e.getY());
        repaint();
    }
}
