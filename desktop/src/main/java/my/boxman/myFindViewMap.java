package my.boxman;

import my.boxman.compat.ResourceLoader;
import my.boxman.compat.android.graphics.*;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * 「相似关卡对比」的关卡图 —— Android {@code myFindViewMap} 的 1:1 移植。
 *
 * <p>改写前 PC 版只有 225 行，缺了原版最核心的几件事：
 * <ol>
 *   <li><b>旋转</b>：原版 {@code onDraw} 按 {@code myMaps.m_nTrun} 把格子 (i,j) 映射成
 *       (r,c)（1 转时 {@code r = j, c = m_nRows-1-i}），并且 {@code initArena} 会把
 *       画布宽高对调。PC 版完全没有旋转，所以「转动关卡」菜单点了没反应。</li>
 *   <li><b>相似区域红框</b>：原版把四个角格子的 {@code rt} 拼成 {@code rt4}，
 *       再在「瘦关卡」状态下画一个 3px 红框；PC 版是自造的一个用 {@code mSelect} 直接算的框，
 *       既不跟旋转走，坐标口径也不对。</li>
 *   <li><b>背景</b>：原版铺 {@code myMaps.m_Sets[4]} 底色 + 平铺 {@code myMaps.bkPict}；
 *       PC 版写死 {@code 0xFF222222}，既不看背景色也不铺背景图。</li>
 *   <li><b>右上角两个按钮</b>（旋转 / 切换源关卡与相似关卡，各 120dp）+ 命中测试。</li>
 *   <li><b>三行关卡信息</b>（源关卡几转比对 / 相似度% / 点击位置游标）。</li>
 * </ol>
 */
public class myFindViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    public int m_iR = -1, m_iC = -1;  // 单击的节点坐标
    public myFindView m_Find;         // 父控件指针，以便使用父控件的功能

    Paint myPaint = new Paint();
    public char[][] m_cArray;

    /** 可以触发“旋转”的区域 */
    private final Rect m_rTrun = new Rect();
    /** 可以触发“切换源关卡与相似关卡”的区域 */
    private final Rect m_rLevel = new Rect();
    private BufferedImage bitTrun;
    private BufferedImage bitLevel;

    /** 是否显示关卡全貌（原版这个字段在 View 上，`myFindView` 读的是它） */
    public boolean m_Level_All = false;

    int w_bkPic, h_bkPic, w_bkNum, h_bkNum;  // （舞台用）背景图片的宽、高；及其平铺时的横、纵个数
    int m_nPicWidth, m_nPicHeight, m_nRows, m_nCols;  // 关卡尺寸
    int m_PicWidth = 50;                    // 素材尺寸，即关卡图每个格子的像素尺寸
    public Matrix mMatrix = new Matrix();          // 图片原始变换矩阵
    public Matrix mCurrentMatrix = new Matrix();   // 当前变换矩阵
    private final Matrix mMapMatrix = new Matrix();// 当前变换矩阵
    float m_fTop, m_fLeft, m_fScale, mScale;       // 关卡图的当前上边界、左边界、缩放倍数；原始缩放倍数
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
    /** 相似区域的外接矩形（由四个角格子拼出来，原版 {@code rt4}） */
    private final Rect rt4 = new Rect();

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
        initView();
    }

    /** 原版 {@code initView()}：两个 120dp 按钮的区域与位图。 */
    private void initView() {
        int myWitth = 120;
        int winW = myMaps.m_nWinWidth > 0 ? myMaps.m_nWinWidth : 370;
        // 旋转：右上角往左第二个 120dp
        m_rTrun.set(winW - myWitth * 2, 20, winW - myWitth, myWitth + 20);
        // 切换源关卡与相似关卡：最右上角
        m_rLevel.set(m_rTrun.right, m_rTrun.top, winW, m_rTrun.bottom);

        bitTrun = scaledDrawable("trbtn", myWitth);
        bitLevel = scaledDrawable("cb_pressed", myWitth);
    }

    private static BufferedImage scaledDrawable(String name, int size) {
        BufferedImage src = ResourceLoader.getDrawable(name);
        if (src == null) return null;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
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

    /**
     * 原版 {@code initArena()}：<b>{@code myMaps.m_nTrun} 为奇数时画布宽高要对调</b>
     * （1 转 = 顺时针 90°，行列互换）。PC 版漏了这个，所以旋转后画面会被压扁。
     */
    public void initArena() {
        if (myMaps.m_nTrun % 2 == 0) {
            m_nPicWidth = m_PicWidth * m_nCols;
            m_nPicHeight = m_PicWidth * m_nRows;
        } else {
            m_nPicWidth = m_PicWidth * m_nRows;
            m_nPicHeight = m_PicWidth * m_nCols;
        }
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

        // 计算（舞台用）背景图片的尺寸，以及平铺时的水平、垂直数量
        if (myMaps.bkPict != null) {
            w_bkPic = myMaps.bkPict.getWidth();
            h_bkPic = myMaps.bkPict.getHeight();
            if (w_bkPic > 0) w_bkNum = getWidth() / w_bkPic + 1;
            if (h_bkPic > 0) h_bkNum = getHeight() / h_bkPic + 1;
        }
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
        canvas.drawColor(myMaps.m_Sets[4]);   // 设置背景色

        if (myMaps.bkPict != null) {
            for (int i = 0; i <= w_bkNum; i++) {
                for (int j = 0; j <= h_bkNum; j++) {
                    canvas.drawBitmap(myMaps.bkPict, w_bkPic * i, h_bkPic * j, null);
                }
            }
        }

        if (m_cArray == null) return;

        canvas.save();
        mCurrentMatrix.getValues(values);
        mMapMatrix.setValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = values[Matrix.MSCALE_X];
        canvas.setMatrix(mMapMatrix);

        // 相似区域红框的初值：故意做成「空框」，靠四个角格子拼出来
        rt4.set(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (int i = 0; i < m_nRows; i++) {
            for (int j = 0; j < m_nCols; j++) {
                int r, c;
                switch (myMaps.m_nTrun) {
                    case 1:
                        rt.left = m_PicWidth * (m_nRows - 1 - i);
                        rt.top = m_PicWidth * j;
                        r = j;
                        c = m_nRows - 1 - i;
                        break;
                    default:
                        rt.left = m_PicWidth * j;
                        rt.top = m_PicWidth * i;
                        r = i;
                        c = j;
                }
                rt.right = rt.left + m_PicWidth;
                rt.bottom = rt.top + m_PicWidth;

                // 相似区域：把四个角格子的 rt 拼成 rt4
                if (myMaps.m_nTrun == 0) {
                    if (m_Find != null && m_Find.m_Level) {
                        if (r == m_Find.mSelect[0][0] && c == m_Find.mSelect[0][1]) {
                            rt4.left = rt.left;
                            rt4.top = rt.top;
                        } else if (r == m_Find.mSelect[0][2] && c == m_Find.mSelect[0][3]) {
                            rt4.right = rt.right;
                            rt4.bottom = rt.bottom;
                        }
                    } else if (m_Find != null) {
                        if (r == m_Find.mSelect[1][0] && c == m_Find.mSelect[1][1]) {
                            rt4.left = rt.left;
                            rt4.top = rt.top;
                        } else if (r == m_Find.mSelect[1][2] && c == m_Find.mSelect[1][3]) {
                            rt4.right = rt.right;
                            rt4.bottom = rt.bottom;
                        }
                    }
                } else {  // 旋转
                    if (m_Find != null && m_Find.m_Level) {
                        if (r == m_Find.mSelect[0][1] && c == m_nRows - 1 - m_Find.mSelect[0][0]) {
                            rt4.right = rt.right;
                            rt4.top = rt.top;
                        } else if (r == m_Find.mSelect[0][3] && c == m_nRows - 1 - m_Find.mSelect[0][2]) {
                            rt4.left = rt.left;
                            rt4.bottom = rt.bottom;
                        }
                    } else if (m_Find != null) {
                        if (r == m_Find.mSelect[1][1] && c == m_nRows - 1 - m_Find.mSelect[1][0]) {
                            rt4.right = rt.right;
                            rt4.top = rt.top;
                        } else if (r == m_Find.mSelect[1][3] && c == m_nRows - 1 - m_Find.mSelect[1][2]) {
                            rt4.left = rt.left;
                            rt4.bottom = rt.bottom;
                        }
                    }
                }

                char ch = m_cArray[i][j];

                if (myMaps.skinBit == null) continue;

                myPaint.setARGB(255, 0, 0, 0);
                // 第一、二层显示————地板、逆推水印
                if (ch != '#') {
                    canvas.drawBitmap(myMaps.skinBit, rtKF, rt, myPaint);
                }
                // 第三层显示————目标点
                if (ch == '.' || ch == '*' || ch == '+') {
                    canvas.drawBitmap(myMaps.skinBit, rtKD, rt, myPaint);
                }
                // 第四层显示————箱子或人
                switch (ch) {
                    case '#':   // 墙壁属于第一层显示
                        Rect w = getWall(rtKW, i, j);
                        canvas.drawBitmap(myMaps.skinBit, w, rt, myPaint);
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
                    default:
                        break;
                }
            }
        }

        // 瘦关卡时，画出相似区域框
        if (!m_Level_All && rt4.left <= rt4.right && rt4.top <= rt4.bottom) {
            myPaint.setARGB(255, 255, 0, 0);
            myPaint.setStyle(Paint.Style.STROKE);
            myPaint.setStrokeWidth(3);
            canvas.drawRect(rt4, myPaint);
        }

        canvas.restore();

        // 画按钮
        myPaint.setARGB(255, 0, 0, 0);
        if (bitTrun != null) canvas.drawBitmap(bitTrun, m_rTrun.left, m_rTrun.top, null);
        if (bitLevel != null) canvas.drawBitmap(bitLevel, m_rLevel.left, m_rLevel.top, null);

        // 关卡信息（原版 sp2px(ctx,16)；桌面端 1sp = 1px）
        float ss = 16;
        myPaint.setTextSize(ss);
        myPaint.setStyle(Paint.Style.FILL);
        myPaint.setStrokeWidth(1);

        if (m_Find != null && m_Find.m_Level) {
            myPaint.setARGB(255, 255, 255, 255);
            canvas.drawText("源关卡 " + m_Find.mTrun + " 转后的比对图", 0, ss, myPaint);
            canvas.drawText(m_Find.m_Set_Pos1 == null ? "" : m_Find.m_Set_Pos1, 0, ss * 2, myPaint);
        } else {
            myPaint.setARGB(255, 0, 255, 255);
            boolean solved = myMaps.curMap != null && myMaps.curMap.Solved;
            int sim = m_Find == null ? 0 : m_Find.mSimilarity;
            canvas.drawText("相似关卡" + (solved ? "【有解】，" : "，") + "相似度：" + sim + "%", 0, ss, myPaint);
            canvas.drawText(m_Find == null || m_Find.m_Set_Pos2 == null ? "" : m_Find.m_Set_Pos2,
                    0, ss * 2, myPaint);
        }
        canvas.drawText("点击位置：" + mGetCur(m_iR, m_iC), 0, ss * 3, myPaint);
    }

    /**
     * 计算使用哪个“墙”图。规则与 {@code myEditViewMap.getWall} 同一张图集，
     * 但多了一张按 {@code myMaps.m_nTrun} 查的方向表 —— 因为调用方传进来的是
     * **旋转后的视图坐标 (i,j)**，方向表负责把它折算回原图的方向位。
     */
    private Rect getWall(Rect rt, int r, int c) {
        if (myMaps.isSkin_200 == 0) {
            rt.set(0, 0, 50, 50);
            return rt;
        }

        int bz = 0;
        int[][] dir = {
                {1, 2, 4, 8, 3, 7, 11},    // 0 转
                {2, 4, 8, 1, 6, 7, 14},    // 1
                {4, 8, 1, 2, 12, 13, 14},  // 2
                {8, 1, 2, 4, 9, 13, 11},   // 3
                {4, 2, 1, 8, 6, 7, 14},    // 4
                {8, 4, 2, 1, 12, 13, 14},  // 5
                {1, 8, 4, 2, 9, 13, 11},   // 6
                {2, 1, 8, 4, 3, 7, 11}     // 7
        };

        // 看看哪个方向上有“墙”
        if (c > 0 && c - 1 < m_cArray[r].length && m_cArray[r][c - 1] == '#') bz |= dir[myMaps.m_nTrun][0];          // 左
        if (r > 0 && c < m_cArray[r - 1].length && m_cArray[r - 1][c] == '#') bz |= dir[myMaps.m_nTrun][1];          // 上
        if (c + 1 < m_cArray[r].length && m_cArray[r][c + 1] == '#') bz |= dir[myMaps.m_nTrun][2];                   // 右
        if (r + 1 < m_cArray.length && c < m_cArray[r + 1].length && m_cArray[r + 1][c] == '#') bz |= dir[myMaps.m_nTrun][3];  // 下

        switch (bz) {
            case 1:  rt.set(150, 0, 200, 50); break;      // 仅左
            case 2:  rt.set(0, 150, 50, 200); break;      // 仅上
            case 3:  rt.set(150, 150, 200, 200); break;   // 左、上
            case 4:  rt.set(50, 0, 100, 50); break;       // 仅右
            case 5:  rt.set(100, 0, 150, 50); break;      // 左、右
            case 6:  rt.set(50, 150, 100, 200); break;    // 右、上
            case 7:  rt.set(100, 150, 150, 200); break;   // 左、上、右
            case 8:  rt.set(0, 50, 50, 100); break;       // 仅下
            case 9:  rt.set(150, 50, 200, 100); break;    // 左、下
            case 10: rt.set(0, 100, 50, 150); break;      // 上、下
            case 11: rt.set(150, 100, 200, 150); break;   // 左、上、下
            case 12: rt.set(50, 50, 100, 100); break;     // 右、下
            case 13: rt.set(100, 50, 150, 100); break;    // 左、右、下
            case 14: rt.set(50, 100, 100, 150); break;    // 上、右、下
            case 15: rt.set(100, 100, 150, 150); break;   // 四方向全有
            default: rt.set(0, 0, 50, 50);                // 四方向全无
        }

        return rt;
    }

    /** 游标计算（原版 {@code mGetCur}）。 */
    public String mGetCur(int r, int c) {
        if (r < 0 || c < 0) return "";

        StringBuilder s = new StringBuilder();
        int k = c / 26 + 64;
        if (k > 64) s.append((char) (byte) k);
        s.append((char) ((byte) (c % 26 + 65))).append(String.valueOf(1 + r));
        return s.toString();
    }

    /** 计算击位置 —— 更新游标（原版 {@code doACT}）。 */
    private void doACT(int i, int j) {
        if (i < m_fLeft || j < m_fTop) {  // 界外
            m_iC = -1;
            m_iR = -1;
        } else {
            switch (myMaps.m_nTrun) {
                case 1:
                    m_iR = m_cArray.length - 1 - ((int) ((i - m_fLeft) / m_fScale)) / m_PicWidth;
                    m_iC = ((int) ((j - m_fTop) / m_fScale)) / m_PicWidth;
                    break;
                default:
                    m_iC = ((int) ((i - m_fLeft) / m_fScale)) / m_PicWidth;
                    m_iR = ((int) ((j - m_fTop) / m_fScale)) / m_PicWidth;
            }
        }
        if (m_iR < 0 || m_iR >= m_nRows || m_iC < 0 || m_iC >= m_nCols) {
            m_iC = -1;
            m_iR = -1;
            return;
        }
        repaint();
    }

    // ================================================================ 交互

    @Override
    public void mousePressed(MouseEvent e) {
        startX = e.getX();
        startY = e.getY();
    }

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {
        if (m_Find == null) return;

        // 原版 onSingleTapUp：先看是不是点在那两个按钮上
        if (m_rTrun.contains(e.getX(), e.getY())) {
            m_Find.myTrun();
            return;
        }
        if (m_rLevel.contains(e.getX(), e.getY())) {
            m_Find.myLevel();
            return;
        }

        if (e.getClickCount() == 2) {
            // 原版 onDoubleTap：切换「关卡全貌」（不是切换源/相似关卡！）
            m_Find.toggleLevelAll();
            initArena();
            repaint();
        } else {
            doACT(e.getX(), e.getY());
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

    // ================================================================ 测试钩子

    /** 「旋转」按钮的命中区域（原版 {@code m_rTrun}）。 */
    public Rect getTrunRect() { return m_rTrun; }

    /** 「切换源关卡与相似关卡」按钮的命中区域（原版 {@code m_rLevel}）。 */
    public Rect getLevelRect() { return m_rLevel; }

    public int getPicWidth() { return m_nPicWidth; }

    public int getPicHeight() { return m_nPicHeight; }

    /** 直接跑一遍命中测试（不经过 AWT 事件，便于用例断言）。 */
    public void clickForTest(int x, int y, int clickCount) {
        if (m_Find == null) return;
        if (m_rTrun.contains(x, y)) {
            m_Find.myTrun();
            return;
        }
        if (m_rLevel.contains(x, y)) {
            m_Find.myLevel();
            return;
        }
        if (clickCount == 2) {
            m_Find.toggleLevelAll();
            initArena();
        } else {
            doACT(x, y);
        }
    }

    public int getCursorRow() { return m_iR; }

    public int getCursorCol() { return m_iC; }
}
