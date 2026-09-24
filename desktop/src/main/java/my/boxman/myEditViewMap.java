package my.boxman;

import my.boxman.compat.android.graphics.*;
import my.boxman.compat.android.view.MotionEvent;

import javax.swing.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Level Editor Canvas for BoxMan PC (Swing Port).
 * Replicates Android myEditViewMap 1:1 using JPanel and compat graphics.
 */
public class myEditViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    public static final int MOD_EDIT   = 0;  // 编辑模式
    public static final int MOD_SELECT = 1;  // 选择模式
    public int mMod = MOD_SELECT;            // 当前模式

    public int m_iR, m_iC;  // 节点坐标
    public boolean isFistClick = true;  // 指示下一次点击是否为第一次选择点击

    int m_iR0, m_iC0;  // 上次的节点坐标
    boolean isSize = true;  // 指示右上角显示尺寸还是游标
    boolean isDrawing = false;  // 是否正在连续绘制
    boolean isShowBkPict = true;  // 是否显示背景图片

    myEditView m_Edit;  // 父控件

    Paint myPaint = new Paint();
    public selNode selNode = new selNode(), selNode2 = new selNode(); // 选择区域对角点

    public int m_nArenaTop;  // 舞台 Top 距屏幕顶的距离
    int m_nPicWidth, m_nPicHeight;  // 关卡图的像素尺寸
    public int m_nMapLeft, m_nMapRight, m_nMapTop, m_nMapBottom;  // 关卡四至
    int m_PicWidth = 50;  // 素材尺寸
    public Matrix mMatrix = new Matrix();  // 原始变换矩阵
    public Matrix mCurrentMatrix = new Matrix();  // 当前变换矩阵
    private Matrix mMapMatrix = new Matrix();  // onDraw 变换矩阵
    float m_fTop, m_fLeft, m_fScale, mScale;  // 当前上边界、左边界、缩放倍数
    public float mMaxScale = 5;  // 最大缩放级别
    float[] values = new float[9];

    Rect rtKW   = new Rect();  // 皮肤中，墙
    Rect rtKF   = new Rect();  // 皮肤中，地板
    Rect rtKD   = new Rect();  // 皮肤中，目标
    Rect rtKB   = new Rect();  // 皮肤中，箱子
    Rect rtKBD  = new Rect();  // 皮肤中，目标上的箱子
    Rect rtKM   = new Rect();  // 皮肤中，人
    Rect rtKMD  = new Rect();  // 皮肤中，目标上的人
    Rect rtKSel = new Rect();  // 皮肤中，选择框

    Rect rtTop  = new Rect();  // 顶行信息栏
    Rect rtF    = new Rect();  // 地板
    Rect rtW    = new Rect();  // 墙
    Rect rtD    = new Rect();  // 目标
    Rect rtB    = new Rect();  // 箱子
    Rect rtM    = new Rect();  // 人
    Rect rtSize = new Rect();  // 关卡尺寸

    int cur_Obj = 0, obj_Width;  // 当前素材、顶部素材矩形宽
    char[] m_Objs = {'-', '#', '.', '$', '@'};

    private final TouchListener touchListener;

    public myEditViewMap() {
        setFocusable(true);
        touchListener = new TouchListener();
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
        initView();
    }

    public void Init(myEditView v) {
        this.m_Edit = v;
    }

    private void initView() {
        mMod = MOD_SELECT;
        selNode.row = -1;

        obj_Width = myMaps.m_nWinWidth / 10;
        if (obj_Width > m_PicWidth * 2) obj_Width = m_PicWidth * 2;
        if (obj_Width < 30) obj_Width = 30;
        m_nArenaTop = obj_Width + 2;

        rtTop.set(0, 0, myMaps.m_nWinWidth, m_nArenaTop);
        rtF.set(1, 1, 1 + obj_Width, obj_Width + 1);
        rtW.set(obj_Width + 2, 1, (obj_Width + 2) + obj_Width, obj_Width + 1);
        rtD.set((obj_Width + 2) * 2, 1, (obj_Width + 2) * 2 + obj_Width, obj_Width + 1);
        rtB.set((obj_Width + 2) * 3, 1, (obj_Width + 2) * 3 + obj_Width, obj_Width + 1);
        rtM.set((obj_Width + 2) * 4, 1, (obj_Width + 2) * 4 + obj_Width, obj_Width + 1);
        rtSize.set(rtM.right + 10, 5, myMaps.m_nWinWidth - 5, m_nArenaTop - 5);

        rtKW.set(0, 0, 50, 50);
        rtKF.set(0, 50 + myMaps.isSkin_200, 50, 100 + myMaps.isSkin_200);
        rtKD.set(0, 100 + myMaps.isSkin_200, 50, 150 + myMaps.isSkin_200);
        rtKB.set(50, 50 + myMaps.isSkin_200, 100, 100 + myMaps.isSkin_200);
        rtKBD.set(50, 100 + myMaps.isSkin_200, 100, 150 + myMaps.isSkin_200);
        rtKM.set(100, 50 + myMaps.isSkin_200, 150, 100 + myMaps.isSkin_200);
        rtKMD.set(100, 100 + myMaps.isSkin_200, 150, 150 + myMaps.isSkin_200);
        rtKSel.set(100, myMaps.isSkin_200, 150, 50 + myMaps.isSkin_200);
    }

    public void mySelectAll() {
        if (m_Edit == null || m_Edit.m_cArray == null) return;
        boolean flg = false;
        for (int i = m_nMapTop; i <= m_nMapBottom; i++) {
            for (int j = m_nMapLeft; j <= m_nMapRight; j++) {
                if (m_Edit.isOK(m_Edit.m_cArray[i][j])) {
                    selNode.row = i;
                    selNode2.row = selNode.row;
                    flg = true;
                    break;
                }
            }
            if (flg) break;
        }
        if (flg) {
            for (int i = m_nMapBottom; i >= selNode.row; i--) {
                for (int j = m_nMapLeft; j <= m_nMapRight; j++) {
                    if (m_Edit.isOK(m_Edit.m_cArray[i][j])) {
                        selNode2.row = i;
                        flg = true;
                        break;
                    }
                }
                if (flg) break;
            }
            flg = false;
            for (int j = m_nMapLeft; j <= m_nMapRight; j++) {
                for (int i = selNode.row; i <= selNode2.row; i++) {
                    if (m_Edit.isOK(m_Edit.m_cArray[i][j])) {
                        selNode.col = j;
                        selNode2.col = selNode.col;
                        flg = true;
                        break;
                    }
                }
                if (flg) break;
            }
            flg = false;
            for (int j = m_nMapRight; j >= selNode.col; j--) {
                for (int i = selNode.row; i <= selNode2.row; i++) {
                    if (m_Edit.isOK(m_Edit.m_cArray[i][j])) {
                        selNode2.col = j;
                        flg = true;
                        break;
                    }
                }
                if (flg) break;
            }
        } else {
            selNode.row = m_nMapTop;
            selNode.col = m_nMapLeft;
            selNode2.row = m_nMapBottom;
            selNode2.col = m_nMapRight;
        }
        selNode.row -= m_nMapTop;
        selNode2.row -= m_nMapTop;
        selNode.col -= m_nMapLeft;
        selNode2.col -= m_nMapLeft;
        m_Edit.selRows = selNode2.row - selNode.row + 1;
        m_Edit.selCols = selNode2.col - selNode.col + 1;
        isFistClick = true;
        mMod = MOD_SELECT;
        if (m_Edit.bt_Cut != null) m_Edit.bt_Cut.setEnabled(true);
        if (m_Edit.bt_Copy != null) m_Edit.bt_Copy.setEnabled(true);
    }

    private Matrix getInnerMatrix(Matrix matrix) {
        if (matrix == null) matrix = new Matrix();
        else matrix.reset();
        int w = getWidth() > 0 ? getWidth() : myMaps.m_nWinWidth;
        int h = getHeight() > 0 ? getHeight() : myMaps.m_nWinHeight;
        RectF tempSrc = new RectF(0, 0, m_nPicWidth > 0 ? m_nPicWidth : 100, m_nPicHeight > 0 ? m_nPicHeight : 100);
        RectF tempDst = new RectF(0, 0, w, Math.max(1, h - m_nArenaTop));
        matrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);
        return matrix;
    }

    public void initArena() {
        m_nPicWidth = m_PicWidth * (m_nMapRight - m_nMapLeft + 1);
        m_nPicHeight = m_PicWidth * (m_nMapBottom - m_nMapTop + 1);
        setArena();
    }

    public void initArena2() {
        m_nPicWidth = m_PicWidth * (m_nMapRight - m_nMapLeft + 1);
        m_nPicHeight = m_PicWidth * (m_nMapBottom - m_nMapTop + 1);
        mMatrix = getInnerMatrix(mMatrix);
        mCurrentMatrix.set(mMatrix);
        mMatrix.getValues(values);
        mScale = values[Matrix.MSCALE_X];
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = mScale;
        repaint();
    }

    public void initArena3() {
        m_nPicWidth = m_PicWidth * (m_nMapRight - m_nMapLeft + 1);
        m_nPicHeight = m_PicWidth * (m_nMapBottom - m_nMapTop + 1);
        mMatrix = getInnerMatrix(mMatrix);
        mMatrix.getValues(values);
        mScale = values[Matrix.MSCALE_X];
        mCurrentMatrix.getValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = mScale;
    }

    public void setArena() {
        mMatrix = getInnerMatrix(mMatrix);
        mCurrentMatrix.set(mMatrix);
        mMatrix.getValues(values);
        mScale = values[Matrix.MSCALE_X];
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = mScale;
        mMod = MOD_SELECT;
        selNode.row = -1;
        isFistClick = true;
        repaint();
    }

    private Rect getWall(Rect rt, int r, int c) {
        if (myMaps.isSkin_200 == 0) {
            rt.set(0, 0, 50, 50);
            return rt;
        }
        int bz = 0;
        if (m_Edit != null && m_Edit.m_cArray != null) {
            if (c > m_nMapLeft && m_Edit.m_cArray[r][c - 1] == '#') bz |= 1;
            if (r > m_nMapTop && m_Edit.m_cArray[r - 1][c] == '#') bz |= 2;
            if (c < m_nMapRight && m_Edit.m_cArray[r][c + 1] == '#') bz |= 4;
            if (r < m_nMapBottom && m_Edit.m_cArray[r + 1][c] == '#') bz |= 8;
        }
        switch (bz) {
            case 1:  rt.set(150, 0, 200, 50); break;
            case 2:  rt.set(0, 150, 50, 200); break;
            case 3:  rt.set(150, 150, 200, 200); break;
            case 4:  rt.set(50, 0, 100, 50); break;
            case 5:  rt.set(100, 0, 150, 50); break;
            case 6:  rt.set(50, 150, 100, 200); break;
            case 7:  rt.set(100, 150, 150, 200); break;
            case 8:  rt.set(0, 50, 50, 100); break;
            case 9:  rt.set(150, 50, 200, 100); break;
            case 10: rt.set(0, 100, 50, 150); break;
            case 11: rt.set(150, 100, 200, 150); break;
            case 12: rt.set(50, 50, 100, 100); break;
            case 13: rt.set(100, 50, 150, 100); break;
            case 14: rt.set(50, 100, 100, 150); break;
            case 15: rt.set(100, 100, 150, 150); break;
            default: rt.set(0, 0, 50, 50);
        }
        return rt;
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

    Rect rt = new Rect();
    Rect rt0 = new Rect();

    public void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);  // 黑色背景

        canvas.save();

        mCurrentMatrix.getValues(values);
        values[Matrix.MTRANS_Y] += m_nArenaTop;
        mMapMatrix.setValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = values[Matrix.MSCALE_X];
        canvas.setMatrix(mMapMatrix);

        if (m_Edit != null && m_Edit.m_cArray != null) {
            for (int r = m_nMapTop; r <= m_nMapBottom; r++) {
                for (int c = m_nMapLeft; c <= m_nMapRight; c++) {
                    int i = r - m_nMapTop;
                    int j = c - m_nMapLeft;
                    rt.set(m_PicWidth * j, m_PicWidth * i, m_PicWidth * j + m_PicWidth, m_PicWidth * i + m_PicWidth);
                    char ch = m_Edit.m_cArray[r][c];

                    myPaint.setARGB(255, 0, 0, 0);

                    // 1. 地板
                    if (myMaps.skinBit != null) {
                        canvas.drawBitmap(myMaps.skinBit, rtKF, rt, myPaint);

                        // 2. 目标点
                        if (ch == '.' || ch == '*' || ch == '+') {
                            canvas.drawBitmap(myMaps.skinBit, rtKD, rt, myPaint);
                        }

                        // 3. 物件
                        switch (ch) {
                            case '#':
                                rtKW = getWall(rtKW, r, c);
                                canvas.drawBitmap(myMaps.skinBit, rtKW, rt, myPaint);
                                break;
                            case '$':
                                canvas.drawBitmap(myMaps.skinBit, rtKB, rt, myPaint);
                                break;
                            case '*':
                                canvas.drawBitmap(myMaps.skinBit, rtKBD, rt, myPaint);
                                break;
                            case '@':
                                canvas.drawBitmap(myMaps.skinBit, rtKM, rt, myPaint);
                                break;
                            case '+':
                                canvas.drawBitmap(myMaps.skinBit, rtKMD, rt, myPaint);
                                break;
                        }
                    }
                }
            }
        }

        // 选区显示
        if (mMod == MOD_SELECT && selNode.row >= 0) {
            myPaint.setARGB(100, 200, 0, 200);
            myPaint.setStyle(Paint.Style.FILL);
            rt.set(m_PicWidth * selNode.col, m_PicWidth * selNode.row,
                    m_PicWidth * selNode2.col + m_PicWidth, m_PicWidth * selNode2.row + m_PicWidth);
            canvas.drawRect(rt, myPaint);
            if (!isFistClick && myMaps.skinBit != null) {
                canvas.drawBitmap(myMaps.skinBit, rtKSel, rt, myPaint);
            }
        }

        canvas.restore();

        // 顶行信息栏
        if (m_nArenaTop > 0) {
            int w = getWidth() > 0 ? getWidth() : myMaps.m_nWinWidth;
            rtTop.set(0, 0, w, m_nArenaTop);
            rtSize.set(rtM.right + 10, 5, w - 5, m_nArenaTop - 5);

            myPaint.setStyle(Paint.Style.FILL);
            myPaint.setARGB(255, 119, 136, 153);
            canvas.drawRect(rtTop, myPaint);

            if (myMaps.skinBit != null) {
                myPaint.setARGB(255, 0, 0, 0);
                canvas.drawBitmap(myMaps.skinBit, rtKF, rtF, myPaint);
                canvas.drawBitmap(myMaps.skinBit, rtKW, rtW, myPaint);
                canvas.drawBitmap(myMaps.skinBit, rtKD, rtD, myPaint);
                canvas.drawBitmap(myMaps.skinBit, rtKB, rtB, myPaint);
                canvas.drawBitmap(myMaps.skinBit, rtKM, rtM, myPaint);

                if (mMod == MOD_EDIT) {
                    rt.set((obj_Width + 2) * cur_Obj, 1, (obj_Width + 2) * cur_Obj + obj_Width, obj_Width + 1);
                    canvas.drawBitmap(myMaps.skinBit, rtKSel, rt, myPaint);
                }
            }

            if (mMod == MOD_SELECT) myPaint.setARGB(127, 0, 0, 0);
            else myPaint.setARGB(191, 0, 63, 0);

            canvas.drawRect(rtSize, myPaint);
            myPaint.setARGB(255, 255, 255, 255);
            myPaint.setTextSize(Math.max(12, obj_Width * 2 / 5));

            String mStr;
            if (isSize) {
                String boxs = m_Edit != null ? m_Edit.getBoxs() : "";
                mStr = (m_nMapRight - m_nMapLeft + 1) + "列" + (m_nMapBottom - m_nMapTop + 1) + "行" + boxs;
            } else {
                if (m_iR < 0 || m_iC < 0 || m_iR > (m_nMapBottom - m_nMapTop) || m_iC > (m_nMapRight - m_nMapLeft)) {
                    mStr = " ";
                } else {
                    mStr = mGetCur(m_iR, m_iC);
                }
            }

            myPaint.getTextBounds(mStr, 0, mStr.length(), rt);
            canvas.drawText(mStr, rtSize.left + (rtSize.width() - rt.width()) / 2,
                    rtSize.top + (rtSize.height() + rt.height()) / 2, myPaint);
        }
    }

    private String mGetCur(int r, int c) {
        StringBuilder s = new StringBuilder();
        int k = c / 26 + 64;
        if (k > 64) s.append((char) (byte) k);
        s.append((char) ((byte) (c % 26 + 65))).append(1 + r);
        s.append(" [ ").append(c + 1).append(", ").append(r + 1).append(" ]");
        return s.toString();
    }

    public void doACT(int i, int j, boolean bSContinuous, boolean flg) {
        if (j < m_nArenaTop) return;
        if (m_fScale <= 0) m_fScale = 1.0f;
        if (i < m_fLeft || j < m_fTop) {
            m_iC = -1;
            m_iR = -1;
        } else {
            m_iC = ((int) ((i - m_fLeft) / m_fScale)) / m_PicWidth;
            m_iR = ((int) ((j - m_fTop) / m_fScale)) / m_PicWidth;
        }
        if (flg || m_iR < 0 || m_iR > m_nMapBottom - m_nMapTop || m_iC < 0 || m_iC > m_nMapRight - m_nMapLeft) {
            return;
        }

        if (mMod == MOD_SELECT) {
            if (isFistClick) {
                selNode.row = m_iR;
                selNode.col = m_iC;
                selNode2.row = m_iR;
                selNode2.col = m_iC;
                isFistClick = false;
            } else {
                if (m_iR < selNode.row) selNode.row = m_iR;
                else selNode2.row = m_iR;
                if (m_iC < selNode.col) selNode.col = m_iC;
                else selNode2.col = m_iC;
                isFistClick = true;
            }
            if (m_Edit != null) {
                m_Edit.selRows = selNode2.row - selNode.row + 1;
                m_Edit.selCols = selNode2.col - selNode.col + 1;
                if (m_Edit.bt_Cut != null) m_Edit.bt_Cut.setEnabled(true);
                if (m_Edit.bt_Copy != null) m_Edit.bt_Copy.setEnabled(true);
            }
        } else if (mMod == MOD_EDIT && m_Edit != null) {
            if (!isDrawing) {
                if (bSContinuous) m_Edit.DoAct(6);
                else m_Edit.DoAct(3);
            }
            isDrawing = true;
            if (!bSContinuous || m_iR != m_iR0 || m_iC != m_iC0) {
                char target = m_Objs[cur_Obj];
                switch (target) {
                    case '#':
                        if (bSContinuous) {
                            m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '#';
                        } else {
                            if (m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] == '#')
                                m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '-';
                            else m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '#';
                        }
                        break;
                    case '.':
                        char cur = m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft];
                        if (cur == '$') m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '*';
                        else if (cur == '@') m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '+';
                        else if (cur == '*') { if (!bSContinuous) m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '$'; }
                        else if (cur == '+') { if (!bSContinuous) m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '@'; }
                        else if (cur == '.') { if (!bSContinuous) m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '-'; }
                        else m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '.';
                        break;
                    case '$':
                        cur = m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft];
                        if (cur == '.' || cur == '+') m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '*';
                        else if (cur == '*') { if (!bSContinuous) m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '.'; }
                        else if (cur == '$') { if (!bSContinuous) m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '-'; }
                        else m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '$';
                        break;
                    case '@':
                        for (int r = m_nMapTop; r <= m_nMapBottom; r++) {
                            for (int c = m_nMapLeft; c <= m_nMapRight; c++) {
                                if (m_Edit.m_cArray[r][c] == '+') m_Edit.m_cArray[r][c] = '.';
                                else if (m_Edit.m_cArray[r][c] == '@') m_Edit.m_cArray[r][c] = '-';
                            }
                        }
                        cur = m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft];
                        if (cur == '.' || cur == '*') m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '+';
                        else m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = '@';
                        break;
                    default:
                        m_Edit.m_cArray[m_iR + m_nMapTop][m_iC + m_nMapLeft] = target;
                        break;
                }
                m_iR0 = m_iR;
                m_iC0 = m_iC;
            }
        }
        repaint();
    }

    // Touch and Gesture Listener
    private class TouchListener {
        private static final int MODE_NONE = 0;
        private static final int MODE_DRAG = 1;
        private static final int MODE_DRAW = 2;
        private int mMode = MODE_NONE;
        private int startX, startY;

        public void onMouseDown(int x, int y, int button) {
            startX = x;
            startY = y;
            isDrawing = false;

            if (button == MouseEvent.BUTTON3) {
                // 右键长按逻辑
                onLongPress(x, y);
                return;
            }

            if (y < m_nArenaTop) {
                // 点击顶部信息栏
                if (rtF.contains(x, y)) { mMod = MOD_EDIT; cur_Obj = 0; }
                else if (rtW.contains(x, y)) { mMod = MOD_EDIT; cur_Obj = 1; }
                else if (rtD.contains(x, y)) { mMod = MOD_EDIT; cur_Obj = 2; }
                else if (rtB.contains(x, y)) { mMod = MOD_EDIT; cur_Obj = 3; }
                else if (rtM.contains(x, y)) { mMod = MOD_EDIT; cur_Obj = 4; }
                else if (rtSize.contains(x, y)) {
                    if (mMod != MOD_SELECT) {
                        mMod = MOD_SELECT;
                        selNode.row = -1;
                        isFistClick = true;
                    } else {
                        mMod = MOD_EDIT;
                    }
                    if (m_Edit != null && (mMod == MOD_EDIT || selNode.row < 0)) {
                        selNode.row = -1;
                        isFistClick = true;
                        if (m_Edit.bt_Cut != null) m_Edit.bt_Cut.setEnabled(false);
                        if (m_Edit.bt_Copy != null) m_Edit.bt_Copy.setEnabled(false);
                    }
                }
                repaint();
            } else {
                if (mMod == MOD_EDIT) {
                    mMode = MODE_DRAW;
                    doACT(x, y, false, false);
                } else {
                    mMode = MODE_DRAG;
                }
            }
        }

        public void onMouseDrag(int x, int y) {
            if (mMode == MODE_DRAG) {
                float dx = x - startX;
                float dy = y - startY;
                startX = x;
                startY = y;
                mCurrentMatrix.postTranslate(dx, dy);
                repaint();
            } else if (mMode == MODE_DRAW) {
                doACT(x, y, true, false);
            }
        }

        public void onMouseUp(int x, int y, int button) {
            if (mMode == MODE_DRAG && Math.abs(x - startX) < 5 && Math.abs(y - startY) < 5) {
                // 单击选择模式
                doACT(x, y, false, false);
            }
            mMode = MODE_NONE;
            isDrawing = false;
            isSize = true;
            if (m_Edit != null) m_Edit.getBoxs();
            repaint();
        }

        public void onLongPress(int x, int y) {
            if (y < m_nArenaTop) {
                if (rtSize.contains(x, y)) {
                    mMod = MOD_SELECT;
                    mySelectAll();
                }
            } else {
                isSize = false;
                doACT(x, y, false, true);
            }
            repaint();
        }

        public void applyZoom(float factor, int cx, int cy) {
            mCurrentMatrix.postScale(factor, factor, cx, cy);
            repaint();
        }
    }

    // MouseListener implementation
    @Override public void mousePressed(MouseEvent e) { touchListener.onMouseDown(e.getX(), e.getY(), e.getButton()); }
    @Override public void mouseReleased(MouseEvent e) { touchListener.onMouseUp(e.getX(), e.getY(), e.getButton()); }
    @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && mMod == MOD_SELECT && m_Edit != null) {
            if (m_iR > 0 && m_iR < m_nMapBottom - m_nMapTop) {
                if (m_iC == 0) m_Edit.My_ReSize(0);
                else if (m_iC == m_nMapRight - m_nMapLeft) m_Edit.My_ReSize(2);
            } else if (m_iC > 0 && m_iC < m_nMapRight - m_nMapLeft) {
                if (m_iR == 0) m_Edit.My_ReSize(1);
                else if (m_iR == m_nMapBottom - m_nMapTop) m_Edit.My_ReSize(3);
            }
        }
    }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // MouseMotionListener implementation
    @Override public void mouseDragged(MouseEvent e) { touchListener.onMouseDrag(e.getX(), e.getY()); }
    @Override public void mouseMoved(MouseEvent e) {}

    // MouseWheelListener implementation
    @Override public void mouseWheelMoved(MouseWheelEvent e) {
        float factor = e.getWheelRotation() < 0 ? 1.15f : 0.85f;
        touchListener.applyZoom(factor, e.getX(), e.getY());
    }
}
