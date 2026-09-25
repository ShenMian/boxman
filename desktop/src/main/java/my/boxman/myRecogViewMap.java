package my.boxman;

import my.boxman.compat.android.graphics.Canvas;
import my.boxman.compat.android.graphics.Matrix;
import my.boxman.compat.android.graphics.Paint;
import my.boxman.compat.android.graphics.PointF;
import my.boxman.compat.android.graphics.Rect;
import my.boxman.compat.android.graphics.RectF;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * 「关卡图像识别」的关卡图 —— Android {@code myRecogViewMap} 的 1:1 移植。
 *
 * <p><b>改写前 PC 版只有 196 行</b>，只画了个「自己算出来的等分网格 + 写死的青色格线」，
 * 与原版完全不是一个东西。缺的部分：
 * <ol>
 *   <li><b>四条边线的指示灯</b>（{@code L_Rect/T_Rect/R_Rect/B_Rect} 四个圆）：
 *       左/上灯亮 = 调整左/上边线，右/下灯亮 = 调整右/下边线；长按指示灯会闪烁（{@code isLamp}）。</li>
 *   <li><b>可拖动的有效区域边框</b>（{@code m_nMapLeft/Top/Right/Bottom}）+ 拖动边线（{@code MoveSideLine}）。</li>
 *   <li><b>双击微调边线</b>（原版 {@code onDoubleTap}，分四个分支，各带 200px 的判据）。</li>
 *   <li><b>焦点格子</b>（{@code cur_Rect}）与四边「回」字形高亮（{@code mPoff}/{@code mPR}）。</li>
 *   <li><b>图像识别本体</b>：{@code findSubimages()} / {@code isSubimage()} /
 *       {@code getPixelDeviateWeightsArray()} / {@code calSimilarity()} /
 *       {@code maxColor()} —— 取样格 vs 全图逐格比对的汉明距离算法。</li>
 *   <li><b>识别结果的 XSB 字符</b>（按格子大小三档切换全角/替代字符）。</li>
 *   <li><b>底部两行提示</b>：关卡尺寸、箱子数 / 目标点数。</li>
 * </ol>
 *
 * <p><b>触屏 → 鼠标</b>的对应关系（与原版 {@code TouchListener} 一一对应）：
 * {@code onSingleTapUp} → 单击（含双击时的第一击，Android 亦如此）；
 * {@code onDoubleTap} → 双击；{@code onLongPress} → 按住 500ms 不动；
 * {@code MODE_DRAG} → 拖动（拖图片，或长按指示灯后拖边线）；{@code MODE_ZOOM} → 滚轮。
 */
public class myRecogViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    /** 父控件指针，以便使用父控件的功能（原版 {@code m_Recog}） */
    myRecogView m_Recog;
    /** 识别出来的物件（原版 {@code curPoints}） */
    ArrayList<Integer> curPoints = new ArrayList<Integer>();

    /** 第一触点，判断双击范围时使用（原版 {@code mClickPoint}） */
    private final PointF mClickPoint = new PointF();
    Paint myPaint = new Paint();
    int m_nArenaTop;                        // 舞台 Top 距屏幕顶的距离
    int m_nPicWidth, m_nPicHeight;          // 关卡图的像素尺寸
    int m_nMapTop, m_nMapLeft, m_nMapBottom, m_nMapRight;   // 图片有效区域的左、上、右、下
    float m_nWidth = -1;                    // 格子的尺寸 —— 默认为「未定」
    int m_nRows = 2;                        // 垂直方向的格子数
    int m_nCols = 2;                        // 水平方向的格子数
    boolean isLeftTop = true;               // 默认：左、上边的指示灯点亮
    int m_nObj = -1;                        // 选中的物件（XSB 元素）
    int cur_Row = -1, cur_Col;              // 点击的格子
    final Rect cur_Rect = new Rect();       // 点击的格子
    final RectF L_Rect = new RectF();       // 左边线指示灯
    final RectF T_Rect = new RectF();       // 上边线指示灯
    final RectF R_Rect = new RectF();       // 右边线指示灯
    final RectF B_Rect = new RectF();       // 下边线指示灯
    int m_Lamp = -1;                        // 长按的指示灯
    boolean isLamp = true;                  // 长按的指示灯，仓管员元素闪烁用

    final int[] m_SampleArray0 = new int[1024];  // 样本的比较数组
    int my_Color0, my_Color1;                    // 图片的颜色偏重
    int my_Grey0, my_Grey1;                      // 图片的灰度值

    public Matrix mMatrix = new Matrix();          // 图片原始变换矩阵
    public Matrix mCurrentMatrix = new Matrix();   // 当前变换矩阵
    private final Matrix mMapMatrix = new Matrix();// onDraw() 用的当前变换矩阵
    float m_fTop, m_fLeft, m_fScale, mScale;       // 关卡图的当前上边界、左边界、缩放倍数；原始缩放倍数
    public float mMaxScale = 32;                   // 最大缩放级别
    float[] values = new float[9];

    // 识别参数默认值
    int mSimilarity = 6;              // 默认相似度
    boolean isRecog = true;           // 是否为识别模式

    final char[] myXSB = {'-', '#', '$', '*', '.', '@', '+'};

    // onDraw() 的临时量（原版是类的字段，这里保持同形）
    private final Rect rt = new Rect();
    private int ss, cc;
    private char ch;
    private int mPoff, mPR;           // 焦点格子的外框宽度 / 内部边长

    // ---------------------------------------------------------------- 鼠标状态
    private int startX, startY;
    private boolean pressed, dragged;
    private Timer longPressTimer;
    /** 原版 GestureDetector 的长按超时（ViewConfiguration.getLongPressTimeout() = 500ms） */
    private static final int LONG_PRESS_MS = 500;

    public myRecogViewMap() {
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // 原版 onSizeChanged(w, h, old_w, old_h)
                if (getWidth() > 0 && getHeight() > 0) {
                    setArena();
                    repaint();
                }
            }
        });
        initView();
    }

    public void Init(myRecogView v) {
        m_Recog = v;
    }

    /** 原版 {@code initView()} */
    private void initView() {
        m_nArenaTop = 0;
    }

    // ================================================================ 舞台

    /** 计算原始变换矩阵（原版 {@code getInnerMatrix}） */
    private Matrix getInnerMatrix(Matrix matrix) {
        if (matrix == null) matrix = new Matrix();
        else matrix.reset();
        // 原图大小
        RectF tempSrc = new RectF(0, 0, m_nPicWidth, m_nPicHeight);
        // 控件大小
        RectF tempDst = new RectF(0, 0, getWidth(), getHeight() - m_nArenaTop);
        // 计算 fit center 矩阵
        matrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);
        return matrix;
    }

    /**
     * 舞台初始化（原版 {@code initArena()}）。
     * 原版在 {@code getWidth() == 0} 时挂 {@code OnPreDrawListener} 等布局完成后再 {@code setArena()}；
     * PC 侧由上面的 {@code componentResized} 承担同一职责。
     */
    public void initArena() {
        if (myMaps.edPict == null) {     // 原版这里会 NPE；PC 侧给个兜底，行为等价于「无底图」
            m_nPicWidth = 0;
            m_nPicHeight = 0;
            setArena();
            return;
        }
        // 计算关卡图的像素尺寸
        m_nPicWidth = myMaps.edPict.getWidth();
        m_nPicHeight = myMaps.edPict.getHeight();
        m_nWidth = -1;      // 格子的尺寸
        m_nRows = 2;        // 水平方向的格子数
        m_nCols = 2;
        m_nMapTop = 50;
        m_nMapLeft = 50;
        m_nMapBottom = m_nPicHeight - 50;
        m_nMapRight = m_nPicWidth - 50;
        setLampRects();

        if (getWidth() > 0) setArena();
    }

    /** 画指示器的四个区域（原版在 {@code initArena} 与 {@code onDraw} 里各算一次，口径相同） */
    private void setLampRects() {
        L_Rect.set(m_nMapLeft - 50, m_nMapTop + (m_nMapBottom - m_nMapTop) / 2 - 50,
                m_nMapLeft + 50, m_nMapTop + (m_nMapBottom - m_nMapTop) / 2 + 50);
        T_Rect.set(m_nMapLeft + (m_nMapRight - m_nMapLeft) / 2 - 50, m_nMapTop - 50,
                m_nMapLeft + (m_nMapRight - m_nMapLeft) / 2 + 50, m_nMapTop + 50);
        R_Rect.set(m_nMapRight - 50, m_nMapTop + (m_nMapBottom - m_nMapTop) / 2 - 50,
                m_nMapRight + 50, m_nMapTop + (m_nMapBottom - m_nMapTop) / 2 + 50);
        B_Rect.set(m_nMapLeft + (m_nMapRight - m_nMapLeft) / 2 - 50, m_nMapBottom - 50,
                m_nMapLeft + (m_nMapRight - m_nMapLeft) / 2 + 50, m_nMapBottom + 50);
    }

    /** 创建或重置舞台场地（原版 {@code setArena()}） */
    public void setArena() {
        mMatrix = getInnerMatrix(mMatrix);   // 计算原始变换矩阵
        mCurrentMatrix.set(mMatrix);         // 同时得到当前变换矩阵
        mMatrix.getValues(values);
        mScale = values[Matrix.MSCALE_X];    // 原始缩放倍数
        m_fTop = values[Matrix.MTRANS_Y];    // 当前上边界
        // ⚠️ 原版这里写的是 values[Matrix.MSCALE_X]（疑似笔误，应为 MTRANS_X）。
        // 但 onDraw() 每帧都会重算 m_fLeft，所以该笔误实际不产生影响；此处照抄原版。
        m_fLeft = values[Matrix.MSCALE_X];   // 当前左边界
        m_fScale = mScale;                   // 当前缩放倍数

        cur_Rect.top = -1;   // 开始时，没有焦点格子
        m_Lamp = -1;         // 开始时，没有长按的指示灯
        isRecog = true;      // 开始时，默认为识别模式

        repaint();
    }

    // ================================================================ 绘制

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

        myPaint.setARGB(255, 255, 255, 255);
        canvas.drawColor(0xFF000000);   // 原版 setBackgroundColor(Color.BLACK)

        canvas.save();

        // 计算缩放等参数
        mCurrentMatrix.getValues(values);
        values[Matrix.MTRANS_Y] += m_nArenaTop;
        mMapMatrix.setValues(values);
        m_fTop = values[Matrix.MTRANS_Y];
        m_fLeft = values[Matrix.MTRANS_X];
        m_fScale = values[Matrix.MSCALE_X];
        canvas.setMatrix(mMapMatrix);

        // 显示参考底图
        if (myMaps.edPict != null && m_nPicWidth > 0 && m_nPicHeight > 0) {
            canvas.drawBitmap(myMaps.edPict, null, new Rect(0, 0, m_nPicWidth, m_nPicHeight), myPaint);
        }

        myPaint.setStrokeWidth(1);      // 设置线宽
        // 画指示器
        setLampRects();
        myPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        if (isLeftTop) {   // 点亮左、上边线指示灯
            myPaint.setARGB(159, 255, 255, 255);
            canvas.drawOval(R_Rect, myPaint);
            canvas.drawOval(B_Rect, myPaint);
            if (isLamp) {
                myPaint.setARGB(159, 255, 0, 255);
            }
            canvas.drawOval(L_Rect, myPaint);
            canvas.drawOval(T_Rect, myPaint);
        } else {           // 点亮右、下边线指示灯
            myPaint.setARGB(159, 255, 255, 255);
            canvas.drawOval(L_Rect, myPaint);
            canvas.drawOval(T_Rect, myPaint);
            if (isLamp) {
                myPaint.setARGB(159, 255, 0, 255);
            }
            canvas.drawOval(R_Rect, myPaint);
            canvas.drawOval(B_Rect, myPaint);
        }
        myPaint.setStyle(Paint.Style.STROKE);
        myPaint.setARGB(159, 0, 0, 0);
        myPaint.setStrokeWidth(10);     // 设置线宽
        canvas.drawOval(L_Rect, myPaint);
        canvas.drawOval(T_Rect, myPaint);
        canvas.drawOval(R_Rect, myPaint);
        canvas.drawOval(B_Rect, myPaint);
        myPaint.setARGB(159, 255, 255, 255);
        myPaint.setStrokeWidth(5);      // 设置线宽
        canvas.drawOval(L_Rect, myPaint);
        canvas.drawOval(T_Rect, myPaint);
        canvas.drawOval(R_Rect, myPaint);
        canvas.drawOval(B_Rect, myPaint);

        myPaint.setStrokeWidth(1);      // 设置线宽
        myPaint.setStyle(Paint.Style.STROKE);

        // 画边框
        rt.set(m_nMapLeft + 1, m_nMapTop + 1, m_nMapRight + 1, m_nMapBottom + 1);
        myPaint.setARGB(255, 0, 0, 0);
        canvas.drawRect(rt, myPaint);
        rt.set(m_nMapLeft, m_nMapTop, m_nMapRight, m_nMapBottom);
        myPaint.setARGB(255, 255, 255, 255);
        canvas.drawRect(rt, myPaint);

        // 画格线
        if (m_nRows > 2 && m_nCols > 2) {
            m_nWidth = (float) (m_nMapRight - m_nMapLeft + 1) / m_nCols;
            m_nRows = (int) ((m_nMapBottom - m_nMapTop + 1 + m_nWidth / 2) / m_nWidth);

            float xx, yy;
            // 画黑色竖线
            for (int k = 1; k < m_nCols; k++) {
                xx = m_nMapLeft + k * m_nWidth;
                myPaint.setARGB(255, 0, 0, 0);
                canvas.drawLine((int) xx + 1, m_nMapTop + 1, (int) xx + 1, m_nMapBottom - 2, myPaint);
            }
            // 画黑色横线
            for (int k = 1; k < m_nRows; k++) {
                yy = m_nMapTop + k * m_nWidth;
                myPaint.setARGB(255, 0, 0, 0);
                canvas.drawLine(m_nMapLeft + 1, (int) yy + 1, m_nMapRight - 2, (int) yy + 1, myPaint);
            }
            // 画白色竖线
            for (int k = 1; k < m_nCols; k++) {
                xx = m_nMapLeft + k * m_nWidth;
                myPaint.setARGB(255, 255, 255, 255);
                canvas.drawLine((int) xx, m_nMapTop, (int) xx, m_nMapBottom - 1, myPaint);
            }
            // 画白色横线
            for (int k = 1; k < m_nRows; k++) {
                yy = m_nMapTop + k * m_nWidth;
                myPaint.setARGB(255, 255, 255, 255);
                canvas.drawLine(m_nMapLeft, (int) yy, m_nMapRight - 1, (int) yy, myPaint);
            }

            // 显示样本格子
            if (cur_Rect.top >= 0) {
                myPaint.setARGB(159, 255, 0, 255);
                myPaint.setStyle(Paint.Style.FILL);
                rt.set(cur_Rect.left + mPoff, cur_Rect.top + mPoff,
                        cur_Rect.left + mPoff + mPR, cur_Rect.top + mPoff + mPR);
                canvas.drawRect(cur_Rect.left, cur_Rect.top, rt.left, cur_Rect.bottom, myPaint);
                canvas.drawRect(rt.right, cur_Rect.top, cur_Rect.right, cur_Rect.bottom, myPaint);
                canvas.drawRect(rt.left, cur_Rect.top, rt.right, rt.top, myPaint);
                canvas.drawRect(rt.left, rt.bottom, rt.right, cur_Rect.bottom, myPaint);
                myPaint.setStyle(Paint.Style.STROKE);
            }

            // 根据格子的大小，调整字体的大小
            ss = sp2px(myMaps.ctxDealFile, 11);
            cc = 0;
            if (m_nWidth <= ss + 6) {
                ss /= 2;
                cc = 1;
            }
            if (m_nWidth <= ss / 2 + 15) {
                ss /= 5;
                cc = 2;
            }
            // 显示识别出来的 XSB
            if (m_Recog != null && m_Recog.m_cArray != null) {
                for (int i = 0; i < m_nRows; i++) {
                    for (int j = 0; j < m_nCols; j++) {
                        if (m_Recog.m_cArray[i][j] != '-') {
                            ch = m_Recog.m_cArray[i][j];
                            if (cc == 2) {  // 格子太小，用替代字符显示
                                switch (ch) {
                                    case '#': ch = '■'; break;
                                    case '$': ch = '▲'; break;
                                    case '*': ch = '◇'; break;
                                    case '@': ch = '↑'; break;
                                    case '+': ch = '＋'; break;
                                }
                            } else if (cc == 1) {
                                switch (ch) {
                                    case '#': ch = '■'; break;
                                    case '$': ch = '▲'; break;
                                    case '*': ch = '◇'; break;
                                    case '@': ch = '∏'; break;
                                    case '+': ch = '＋'; break;
                                }
                            } else {  // 格子可以正常显示 XSB 字符时，将其变成全角显示，这样定位准确
                                switch (ch) {
                                    case '#': ch = '＃'; break;
                                    case '$': ch = '＄'; break;
                                    case '*': ch = '*'; break;
                                    case '@': ch = '＠'; break;
                                    case '+': ch = '＋'; break;
                                }
                            }
                            showXSB(canvas, (int) (j * m_nWidth + m_nMapLeft),
                                    (int) (i * m_nWidth + m_nMapTop), ch, ss);
                        }
                    }
                }
            }
        }

        canvas.restore();

        // 提示关卡尺寸
        if (m_nRows > 2 && m_nCols > 2) {
            ss = sp2px(myMaps.ctxDealFile, 16);
            myPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            myPaint.setTextSize(ss);
            myPaint.setARGB(255, 0, 0, 0);
            myPaint.setStrokeWidth(5);
            drawTextFilled(canvas, "关卡尺寸: " + m_nCols + " × " + m_nRows, 10, ss / 2 * 3);
            myPaint.setARGB(255, 255, 255, 255);
            myPaint.setStrokeWidth(3);
            drawTextFilled(canvas, "关卡尺寸: " + m_nCols + " × " + m_nRows, 10, ss / 2 * 3);

            if (m_Recog != null) {
                m_Recog.myCount();

                // 提示箱子数和目标点数
                if (m_Recog.m_nBoxNum > 0 || m_Recog.DstNum > 0) {
                    ss = sp2px(myMaps.ctxDealFile, 16);
                    myPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    myPaint.setTextSize(ss);
                    myPaint.setARGB(255, 0, 0, 0);
                    myPaint.setStrokeWidth(5);
                    drawTextFilled(canvas, "箱子: " + m_Recog.m_nBoxNum + "  目标点: " + m_Recog.DstNum, 10, ss * 3);
                    myPaint.setARGB(255, 255, 255, 255);
                    myPaint.setStrokeWidth(3);
                    drawTextFilled(canvas, "箱子: " + m_Recog.m_nBoxNum + "  目标点: " + m_Recog.DstNum, 10, ss * 3);
                }
            }
        }
    }

    /**
     * 原版 {@code canvas.drawText()} 在 {@code Paint.Style.FILL_AND_STROKE} 下是
     * 「先描边、再填充」。Swing 的 {@code Graphics2D.drawString()} 只填充，
     * 所以这里把字形转成 {@link Shape} 后自己描边 —— 否则原版那种「白字黑边」的观感会丢掉。
     */
    private void drawTextFilled(Canvas canvas, String text, float x, float y) {
        Graphics2D g2d = canvas.getGraphics();
        if (g2d == null) return;
        Font f = myPaint.getFont();
        g2d.setFont(f);
        g2d.setColor(myPaint.getColor());
        g2d.setStroke(new BasicStroke(myPaint.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GlyphVector gv = f.createGlyphVector(g2d.getFontRenderContext(), text);
        Shape outline = gv.getOutline(x, y);
        Paint.Style st = myPaint.getStyle();
        if (st == Paint.Style.FILL || st == Paint.Style.FILL_AND_STROKE) g2d.fill(outline);
        if (st == Paint.Style.STROKE || st == Paint.Style.FILL_AND_STROKE) g2d.draw(outline);
    }

    /** 原版 {@code setPR()}：焦点格子的外框宽度与内部边长 */
    public void setPR() {
        if (m_nWidth < 20) {
            mPoff = (int) (m_nWidth / 4);
        } else {
            mPoff = (int) (m_nWidth / 5);
        }
        mPR = (int) (m_nWidth - mPoff * 2);
    }

    /** 显示 XSB（原版 {@code showXSB()}） */
    private void showXSB(Canvas canvas, int x, int y, char mXSB, int ss) {
        myPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        myPaint.setARGB(255, 255, 255, 255);
        if (mXSB == '.') {
            mXSB = 'o';
            myPaint.setTextSize(ss / 2);
            myPaint.setStrokeWidth(5);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 4, y + m_nWidth / 2 + ss / 6);
            myPaint.setARGB(255, 0, 0, 0);
            myPaint.setStrokeWidth(3);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 4, y + m_nWidth / 2 + ss / 6);
        } else if (mXSB == '*') {
            myPaint.setTextSize(ss * 3 / 2);
            myPaint.setStrokeWidth(5);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 3, y + m_nWidth / 3 + ss);
            myPaint.setARGB(255, 0, 0, 0);
            myPaint.setStrokeWidth(3);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 3, y + m_nWidth / 3 + ss);
        } else {
            myPaint.setTextSize(ss);
            myPaint.setStrokeWidth(5);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 2, y + m_nWidth / 2 + ss / 3);
            myPaint.setARGB(255, 0, 0, 0);
            myPaint.setStrokeWidth(3);
            drawTextFilled(canvas, "" + mXSB, x + m_nWidth / 2 - ss / 2, y + m_nWidth / 2 + ss / 3);
        }
    }

    // ================================================================ 图像识别

    /**
     * 分析图片，进行识别（原版 {@code findSubimages()}）。
     *
     * <p>取样格子（{@code cur_Rect}，四周各让出 3 像素）作为样本，
     * 与全图每一个格子做比对，返回「命中格子」的 {@code c << 16 | r} 列表。
     */
    public ArrayList<Integer> findSubimages() {
        ArrayList<Integer> locs = new ArrayList<Integer>();

        Rect rt0 = new Rect();  // 样本
        Rect rt1 = new Rect();  // 图片格子
        Rect rt2 = new Rect();  // 临时

        int ww = cur_Rect.right - cur_Rect.left - 6;  // 实际取样尺寸，即周边各让出 3 个像素
        if (ww <= 0 || myMaps.edPict == null) return locs;   // 原版此处会抛异常，PC 侧兜底成「不识别」

        BufferedImage img0 = new BufferedImage(ww, ww, myMaps.cfg);   // 样本图片
        BufferedImage img1 = new BufferedImage(ww, ww, myMaps.cfg);   // 格子图片
        Canvas cvs0 = new Canvas(img0);   // 样本画布
        Canvas cvs1 = new Canvas(img1);   // 格子画布

        rt2.set(0, 0, ww, ww);

        rt0.set(cur_Rect.left + 3, cur_Rect.top + 3, cur_Rect.left + 3 + ww, cur_Rect.top + 3 + ww);
        cvs0.drawBitmap(myMaps.edPict, rt0, rt2, myPaint);   // 样本图片
        my_Color0 = getPixelDeviateWeightsArray(m_SampleArray0, img0, 0);

        // 从「左上角」开始搜索子图
        float x;
        float y = m_nMapTop;
        for (int r = 0; r < m_nRows; r++) {
            x = m_nMapLeft;
            for (int c = 0; c < m_nCols; c++) {
                if (m_nObj == 0 || m_Recog.m_cArray[r][c] == '-') {   // 锁定之前识别出来的元素
                    if (r == cur_Row && c == cur_Col) {               // 样本格子
                        locs.add(c << 16 | r);
                    } else {
                        rt1.set((int) x + 3, (int) y + 3, (int) x + 3 + ww, (int) y + 3 + ww);
                        cvs1.drawBitmap(myMaps.edPict, rt1, rt2, myPaint);
                        if (isSubimage(img1)) {
                            locs.add(c << 16 | r);
                        }
                    }
                }
                x += m_nWidth;
            }
            y += m_nWidth;
        }
        return locs;
    }

    /** 与样本比较（原版 {@code isSubimage()}） */
    boolean isSubimage(BufferedImage img1) {

        int[] m_SampleArray1 = new int[1024];   // 格子的比较数组

        my_Color1 = getPixelDeviateWeightsArray(m_SampleArray1, img1, 1);

        double v = calSimilarity(m_SampleArray0, m_SampleArray1) * 10;

        return ((int) v >= mSimilarity && my_Color1 == my_Color0 && Math.abs(my_Grey0 - my_Grey1) < 5);
    }

    /** 取得最大颜色序号（原版 {@code maxColor()}） */
    private int maxColor(int r, int g, int b) {
        if (r - g > 10 && r - b > 10) return 1;         // R
        else if (g - r > 10 && g - b > 10) return 2;    // G
        else if (b - r > 10 && b - g > 10) return 3;    // B
        else if (r - b > 10) return 4;                  // RG
        else if (r - g > 10) return 5;                  // RB
        else if (g - b > 10) return 6;                  // BG
        else return 0;                                  // RGB
    }

    /**
     * 获取图片转换灰度图后的像素的比较数组（平均值的离差）。
     *
     * <p><b>与原版的两处量化口径必须对齐</b>：
     * <ol>
     *   <li>样本/格子位图在 Android 侧用的是 {@code myMaps.cfg = Bitmap.Config.ARGB_4444}，
     *       即每通道只有 4 bit。PC 侧 {@code myMaps.cfg} 是 {@code TYPE_INT_ARGB}（8 bit），
     *       所以这里把读出的像素按 {@code (v>>4)*17} 折回 4 bit —— 否则
     *       {@code |grey0-grey1| < 5} 这道闸门的判定会与原版不一致。</li>
     *   <li>缩略图用的是 {@code Bitmap.Config.RGB_565}，对应
     *       {@link BufferedImage#TYPE_USHORT_565_RGB}（Java 与 Skia 的 5→8 bit 展开规则一致）。</li>
     * </ol>
     */
    public int getPixelDeviateWeightsArray(int[] dest, BufferedImage img, int avGrey) {
        int width = img.getWidth();         // 获取位图的宽
        int height = img.getHeight();       // 获取位图的高
        int red, green, blue;
        int[] color = {0, 0, 0, 0, 0, 0, 0};
        long sumGrey = 0;

        int[] pixels = new int[width * height]; // 通过位图的大小创建像素点数组

        img.getRGB(0, 0, width, height, pixels, 0, width);
        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];

                // ARGB_4444 量化（每通道 4 bit → 8 bit 展开）
                red = quant4444((grey & 0x00FF0000) >> 16);
                green = quant4444((grey & 0x0000FF00) >> 8);
                blue = quant4444(grey & 0x000000FF);

                // 计算图块的颜色偏重
                color[maxColor(red, green, blue)]++;

                grey = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                sumGrey += grey;

                grey = alpha | (grey << 16) | (grey << 8) | grey;
                pixels[width * i + j] = grey;
            }
        }
        if (avGrey == 0) {
            my_Grey0 = (int) (sumGrey / (height * width));
        } else {
            my_Grey1 = (int) (sumGrey / (height * width));
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_565_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);

        // 缩放至 32x32 像素缩略图
        BufferedImage thumb = new BufferedImage(32, 32, BufferedImage.TYPE_USHORT_565_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, 32, 32, null);
        g.dispose();

        // 获取像素数组
        width = thumb.getWidth();
        height = thumb.getHeight();
        pixels = new int[width * height];
        thumb.getRGB(0, 0, width, height, pixels, 0, width);

        // 获取灰度图的平均像素颜色值
        long sumRed = 0;
        for (int i = 0; i < pixels.length; i++) {
            red = ((pixels[i] & 0x00FF0000) >> 16);
            sumRed += red;
        }
        int averageColor = (int) (sumRed / pixels.length);

        // 获取灰度图的像素比较数组（平均值的离差）
        for (int i = 0; i < pixels.length; i++) {
            dest[i] = ((pixels[i] & 0x00FF0000) >> 16) - averageColor > 0 ? 1 : 0;
        }

        int m = color[0], n = 0;
        for (int k = 1; k < 7; k++) {
            if (m < color[k]) {
                m = color[k];
                n = k;
            }
        }
        return n;
    }

    /** 把 8 bit 通道折回 Android {@code ARGB_4444} 的取值（{@code (v>>4)*17}） */
    private static int quant4444(int v) {
        return (v & 0xF0) | (v >> 4);
    }

    /** 通过汉明距离计算相似度（原版 {@code calSimilarity()}） */
    public double calSimilarity(int[] a, int[] b) {
        // 获取两个缩略图的平均像素比较数组的汉明距离（距离越大差异越大）
        int hammingDistance = 0;
        for (int i = 0; i < a.length; i++) {
            hammingDistance += a[i] == b[i] ? 0 : 1;
        }

        // 通过汉明距离计算相似度
        int length = 32 * 32;
        double similarity = (length - hammingDistance) / (double) length;

        // 使用指数曲线调整相似度结果
        return Math.pow(similarity, 2);
    }

    /** 原版 {@code sp2px(ctx, sp)}；桌面端约定 1sp = 1px */
    private int sp2px(Object context, float spValue) {
        return (int) (spValue + 0.5f);
    }

    // ================================================================ 交互

    @Override
    public void mousePressed(MouseEvent e) {
        startX = e.getX();
        startY = e.getY();
        mClickPoint.set(startX, startY);
        pressed = true;
        dragged = false;
        scheduleLongPress();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // 原版 ACTION_UP / ACTION_CANCEL
        pressed = false;
        cancelLongPress();
        m_Lamp = -1;          // 取消长按指示灯
        isLamp = true;
        if (m_Recog != null) m_Recog.actNum = 0;   // 取消定时器功能
        reSetMatrix();
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // 原版 GestureDetector：双击时，第一击仍会触发 onSingleTapUp，第二击触发 onDoubleTap。
        // AWT 的事件序列（click(1) 然后 click(2)）与之完全一致。
        if (e.getClickCount() == 2) {
            doDoubleTap(e.getX(), e.getY());
        } else {
            doSingleTap(e.getX(), e.getY());
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        dragged = true;
        cancelLongPress();
        if (m_Lamp < 0) {
            setDragMatrix(e.getX(), e.getY());
        } else {
            MoveSideLine(e.getX(), e.getY());
        }
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // 原版 MODE_ZOOM 用两指间距缩放；PC 用滚轮等价替代
        setZoomMatrix(e.getX(), e.getY(), e.getWheelRotation() < 0 ? 1.15f : 0.85f);
        repaint();
    }

    private void scheduleLongPress() {
        cancelLongPress();
        longPressTimer = new Timer(LONG_PRESS_MS, ev -> {
            if (pressed && !dragged) doLongPress(startX, startY);
        });
        longPressTimer.setRepeats(false);
        longPressTimer.start();
    }

    private void cancelLongPress() {
        if (longPressTimer != null) {
            longPressTimer.stop();
            longPressTimer = null;
        }
    }

    /** 原版 {@code onSingleTapUp} */
    public void doSingleTap(int screenX, int screenY) {
        if (m_fScale <= 0) return;

        float x = (screenX - m_fLeft) / m_fScale;
        float y = (screenY - m_fTop) / m_fScale;

        if (m_nObj < 0) {    // 识别模式
            // 切换边线指示灯
            if (L_Rect.contains(x, y) || T_Rect.contains(x, y)) {       // 左上边角
                isLeftTop = true;
                repaint();
            } else if (R_Rect.contains(x, y) || B_Rect.contains(x, y)) { // 右下边角
                isLeftTop = false;
                repaint();
            }
        }

        int xx = (int) x, yy = (int) y;

        // 有效区域之外
        if (yy < m_nMapTop || xx < m_nMapLeft || yy >= m_nMapBottom || xx >= m_nMapRight
                || m_nRows < 3 || m_nCols < 3) {
            cur_Rect.top = -1;   // 取消焦点格子
            return;
        }

        m_nWidth = (float) (m_nMapRight - m_nMapLeft + 1) / m_nCols;

        cur_Col = (int) ((x - m_nMapLeft + 1) / m_nWidth);
        cur_Row = (int) ((y - m_nMapTop + 1) / m_nWidth);

        // 消除因计算误差，对焦点格子的影响
        if (cur_Row >= m_nRows) {
            cur_Rect.top = -1;   // 取消焦点格子
            return;
        }

        xx = (int) (cur_Col * m_nWidth + m_nMapLeft);
        yy = (int) (cur_Row * m_nWidth + m_nMapTop);

        cur_Rect.set(xx, yy, (int) (xx + m_nWidth), (int) (yy + m_nWidth));   // 设置样本格子 —— 焦点区域

        setPR();        // 增强焦点格子的显示效果

        if (m_nObj >= 0 && m_Recog != null) {   // 选择了底行的 XSB 元素，然后单击格子
            m_Recog.myBackup();
            if (isRecog) {                      // 识别模式
                if (m_nObj == 5) {              // 若选中的物件是「仓管员」
                    clearOtherMan(cur_Row, cur_Col);
                    if (m_Recog.m_cArray[cur_Row][cur_Col] == '+'
                            || m_Recog.m_cArray[cur_Row][cur_Col] == '*'
                            || m_Recog.m_cArray[cur_Row][cur_Col] == '.') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '+';
                    } else {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '@';
                    }
                } else {
                    m_Recog.doAction();
                }
            } else {                            // 编辑模式
                if (m_nObj == 4) {              // 若选中的物件是「目标点」
                    if (m_Recog.m_cArray[cur_Row][cur_Col] == '@') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '+';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '+') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '@';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '$') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '*';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '*') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '$';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '.') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '-';
                    } else {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '.';
                    }
                } else if (m_nObj == 5) {       // 若选中的物件是「仓管员」
                    clearOtherMan(cur_Row, cur_Col);
                    if (m_Recog.m_cArray[cur_Row][cur_Col] == '@') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '-';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '+') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '.';
                    } else if (m_Recog.m_cArray[cur_Row][cur_Col] == '.') {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '+';
                    } else {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '@';
                    }
                } else {                        // 选中了其他物件
                    if (m_Recog.m_cArray[cur_Row][cur_Col] == myXSB[m_nObj]) {
                        m_Recog.m_cArray[cur_Row][cur_Col] = '-';
                    } else {
                        m_Recog.m_cArray[cur_Row][cur_Col] = myXSB[m_nObj];
                    }
                }
            }
        }
        repaint();
    }

    /** 原版内联的「仓管员唯一」清理逻辑（识别模式与编辑模式各抄一遍） */
    private void clearOtherMan(int keepRow, int keepCol) {
        for (int i = 0; i < m_Recog.m_cArray.length; i++) {
            for (int j = 0; j < m_Recog.m_cArray[0].length; j++) {
                if (i != keepRow || j != keepCol) {
                    if (m_Recog.m_cArray[i][j] == '@') {
                        m_Recog.m_cArray[i][j] = '-';
                    } else if (m_Recog.m_cArray[i][j] == '+') {
                        m_Recog.m_cArray[i][j] = '.';
                    }
                }
            }
        }
    }

    /** 原版 {@code onDoubleTap}：双击边界线附近，可以微调边界线位置 */
    public void doDoubleTap(int screenX, int screenY) {
        if (m_fScale <= 0) return;

        // 双击范围控制（原版比较的是 raw 坐标；GestureDetector 下两者恒等，故此处为恒真）
        if (Math.abs(screenX - mClickPoint.x) > 50f || Math.abs(screenY - mClickPoint.y) > 50f) {
            return;
        }

        float x = (screenX - m_fLeft) / m_fScale;
        float y = (screenY - m_fTop) / m_fScale;

        if (Math.abs(x - m_nMapLeft) < 200) {                    // 左边线左右
            if (Math.abs(y - m_nMapTop) > 200 && Math.abs(y - m_nMapBottom) > 200) {
                cur_Rect.top = -1;   // 取消焦点格子
                if (x < m_nMapLeft) {
                    if (m_nMapLeft > 1) m_nMapLeft--;
                } else if (x > m_nMapLeft) {
                    if (m_nMapLeft + 50 < m_nMapRight) m_nMapLeft++;
                }
                repaint();
            }
        } else if (Math.abs(x - m_nMapRight) < 200) {            // 右边线左右
            if (Math.abs(y - m_nMapTop) > 200 && Math.abs(y - m_nMapBottom) > 200) {
                cur_Rect.top = -1;   // 取消焦点格子
                if (x < m_nMapRight) {
                    if (m_nMapRight - 50 > m_nMapLeft) m_nMapRight--;
                } else if (x > m_nMapRight) {
                    if (m_nMapRight + 1 < m_nPicWidth - 1) m_nMapRight++;
                }
                repaint();
            }
        } else if (Math.abs(y - m_nMapTop) < 200) {              // 上边线上下
            if (Math.abs(x - m_nMapLeft) > 200 && Math.abs(x - m_nMapRight) > 200) {
                cur_Rect.top = -1;   // 取消焦点格子
                if (y < m_nMapTop) {
                    if (m_nMapTop > 1) m_nMapTop--;
                } else if (y > m_nMapTop) {
                    if (m_nMapTop + 50 < m_nMapBottom) m_nMapTop++;
                }
                repaint();
            }
        } else if (Math.abs(y - m_nMapBottom) < 200) {           // 下边线上下
            if (Math.abs(x - m_nMapLeft) > 200 && Math.abs(x - m_nMapRight) > 200) {
                cur_Rect.top = -1;   // 取消焦点格子
                if (y < m_nMapBottom) {
                    if (m_nMapBottom - 50 > m_nMapTop) m_nMapBottom--;
                } else if (y > m_nMapBottom) {
                    if (m_nMapBottom + 1 < m_nPicHeight - 1) m_nMapBottom++;
                }
                repaint();
            }
        }
    }

    /** 原版 {@code onLongPress}：长按指示灯 */
    public void doLongPress(int screenX, int screenY) {
        if (m_fScale <= 0) return;

        float x = (screenX - m_fLeft) / m_fScale;
        float y = (screenY - m_fTop) / m_fScale;

        if (L_Rect.contains(x, y)) {            // 左灯亮
            m_Lamp = 0;
            isLeftTop = true;
        } else if (T_Rect.contains(x, y)) {     // 上灯亮
            m_Lamp = 1;
            isLeftTop = true;
        } else if (R_Rect.contains(x, y)) {     // 右灯亮
            m_Lamp = 2;
            isLeftTop = false;
        } else if (B_Rect.contains(x, y)) {     // 下灯亮
            m_Lamp = 3;
            isLeftTop = false;
        } else {
            m_Lamp = -1;
        }

        if (m_Lamp >= 0 && m_Recog != null) {   // 长按了指示灯
            m_Recog.setColor(-1);               // 取消底行 XSB 元素高亮
            isLamp = false;                     // 仓管员闪烁 —— 是否亮起
            m_Recog.actNum = 5;                 // 定时器功能选择 —— 仓管员闪烁
            m_Recog.UpData(1);                  // 启动定时器
        }
    }

    /** 原版 {@code setDragMatrix} */
    private void setDragMatrix(int screenX, int screenY) {
        if (isZoomChanged()) {
            // 计算移动距离
            float dx = screenX - startX;
            float dy = screenY - startY;

            startX = screenX;
            startY = screenY;

            // 在当前基础上移动
            mCurrentMatrix.getValues(values);
            dy = checkDyBound(values, dy);
            dx = checkDxBound(values, dx);

            mCurrentMatrix.postTranslate(dx, dy);
        }
    }

    /** 图片是否在原始缩放基础上又进行了缩放？控制是否能够拖动（原版 {@code isZoomChanged}） */
    private boolean isZoomChanged() {
        mCurrentMatrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];  // 获取当前缩放级别
        return scale != mScale;                 // 与原始缩放级别做比较
    }

    /** 检验 dy，使图片边界尽量不离开屏幕边界（原版 {@code checkDyBound}） */
    private float checkDyBound(float[] values, float dy) {
        float height = getHeight() - m_nArenaTop;
        if (m_nPicHeight * values[Matrix.MSCALE_Y] < height) return 0;
        if (values[Matrix.MTRANS_Y] + dy > 0) {
            dy = -values[Matrix.MTRANS_Y];
        } else if (values[Matrix.MTRANS_Y] + dy < -(m_nPicHeight * values[Matrix.MSCALE_Y] - height)) {
            dy = -(m_nPicHeight * values[Matrix.MSCALE_Y] - height) - values[Matrix.MTRANS_Y];
        }
        return dy;
    }

    /** 检验 dx，使图片边界尽量不离开屏幕边界（原版 {@code checkDxBound}） */
    private float checkDxBound(float[] values, float dx) {
        float width = getWidth();
        if (m_nPicWidth * values[Matrix.MSCALE_X] < width) return 0;
        if (values[Matrix.MTRANS_X] + dx > 0) {
            dx = -values[Matrix.MTRANS_X];
        } else if (values[Matrix.MTRANS_X] + dx < -(m_nPicWidth * values[Matrix.MSCALE_X] - width)) {
            dx = -(m_nPicWidth * values[Matrix.MSCALE_X] - width) - values[Matrix.MTRANS_X];
        }
        return dx;
    }

    /** 设置缩放 Matrix（原版 {@code setZoomMatrix} 的双指缩放 → PC 滚轮） */
    private void setZoomMatrix(float px, float py, float rawScale) {
        mCurrentMatrix.getValues(values);
        float scale = checkMaxScale(rawScale, values);
        float[] center = getCenter(scale, values, px, py);
        mCurrentMatrix.postScale(scale, scale, center[0], center[1]);
    }

    /** 计算缩放的中心点，主要是控制图片边界尽量不离开屏幕边界（原版 {@code getCenter}） */
    private float[] getCenter(float scale, float[] values, float midX, float midY) {
        float cx = midX;
        float cy = midY;
        float height = getHeight() - m_nArenaTop;

        if (scale > 1) {  // 放大时，若图片边缘小于屏幕边缘，则以屏幕中心为缩放中心
            if (m_nPicWidth * scale * values[Matrix.MSCALE_X] < getWidth()) cx = getWidth() / 2;
            if (m_nPicHeight * scale * values[Matrix.MSCALE_Y] < height) cy = height / 2 + m_nArenaTop;
        } else {          // 缩小时，若图片边缘会离开屏幕边缘，则以屏幕边缘为缩放中心
            if (m_nPicWidth * scale * values[Matrix.MSCALE_X] < getWidth()) {
                cx = getWidth() / 2;
            } else {
                if ((cx - values[Matrix.MTRANS_X]) * scale < cx) cx = 0;
                if (((m_nPicWidth - cx) * values[Matrix.MSCALE_X] + values[Matrix.MTRANS_X]) * scale < getWidth())
                    cx = getWidth();
            }
            if (m_nPicHeight * scale * values[Matrix.MSCALE_Y] < height) {
                cy = height / 2 + m_nArenaTop;
            } else {
                if ((cy - values[Matrix.MTRANS_Y]) * scale < cy) cy = 0;
                if (((m_nPicHeight - cy) * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]) * scale < height)
                    cy = height;
            }
        }
        return new float[]{cx, cy};
    }

    /** 图片缩放倍数控制（原版 {@code checkMaxScale}） */
    private float checkMaxScale(float scale, float[] values) {
        if (mScale >= mMaxScale) {
            scale = mScale / values[Matrix.MSCALE_X];
        } else if (scale * values[Matrix.MSCALE_X] > mMaxScale) {          // 大于最大倍数限制时
            scale = mMaxScale / values[Matrix.MSCALE_X];
        } else if (scale * values[Matrix.MSCALE_X] < mScale * 0.9F) {      // 小于原始缩放倍数的 90% 时
            scale = mScale * 0.9F / values[Matrix.MSCALE_X];
        }
        return scale;
    }

    /** 重置 Matrix，在小于原始地图时恢复（原版 {@code reSetMatrix}） */
    private void reSetMatrix() {
        if (checkRest()) mCurrentMatrix.set(mMatrix);
    }

    /** 小于原始缩放级别时，则需要重置（原版 {@code checkRest}） */
    private boolean checkRest() {
        mCurrentMatrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];  // 获取当前缩放级别
        return scale < mScale;
    }

    /** 拖动边线（原版 {@code MoveSideLine}） */
    private void MoveSideLine(int screenX, int screenY) {
        // 计算移动距离
        int dx = (int) ((screenX - startX) / m_fScale);
        int dy = (int) ((screenY - startY) / m_fScale);

        startX = screenX;
        startY = screenY;

        switch (m_Lamp) {
            case 0:    // 左边线
                if (m_nMapLeft + dx > 0 && m_nMapLeft + dx + 50 < m_nMapRight) {
                    m_nMapLeft = m_nMapLeft + dx;
                }
                break;
            case 1:    // 上边线
                if (m_nMapTop + dy > 0 && m_nMapTop + dy + 50 < m_nMapBottom) {
                    m_nMapTop = m_nMapTop + dy;
                }
                break;
            case 2:    // 右边线
                if (m_nMapRight + dx < m_nPicWidth - 1 && m_nMapLeft + 50 < m_nMapRight + dx) {
                    m_nMapRight = m_nMapRight + dx;
                }
                break;
            case 3:    // 下边线
                if (m_nMapBottom + dy < m_nPicHeight - 1 && m_nMapTop + 50 < m_nMapBottom + dy) {
                    m_nMapBottom = m_nMapBottom + dy;
                }
                break;
        }
    }

    // ================================================================ 测试钩子

    /** 直接用像素坐标跑一遍「单击」逻辑（不经过 AWT 事件） */
    public void clickForTest(int x, int y) {
        doSingleTap(x, y);
    }

    /** 直接用像素坐标跑一遍「双击」逻辑 */
    public void doubleTapForTest(int x, int y) {
        mClickPoint.set(x, y);
        doDoubleTap(x, y);
    }

    /** 直接用像素坐标跑一遍「长按」逻辑 */
    public void longPressForTest(int x, int y) {
        doLongPress(x, y);
    }

    public int getMapLeft() { return m_nMapLeft; }

    public int getMapTop() { return m_nMapTop; }

    public int getMapRight() { return m_nMapRight; }

    public int getMapBottom() { return m_nMapBottom; }

    public int getLamp() { return m_Lamp; }

    public float getCellWidth() { return m_nWidth; }

    public int getCursorRow() { return cur_Row; }

    public int getCursorCol() { return cur_Col; }

    public Rect getFocusRect() { return cur_Rect; }

    public RectF getLeftLampRect() { return L_Rect; }

    public RectF getTopLampRect() { return T_Rect; }

    public RectF getRightLampRect() { return R_Rect; }

    public RectF getBottomLampRect() { return B_Rect; }

    /** 把屏幕坐标换算成图片坐标（测试用） */
    public float[] toImageSpace(int screenX, int screenY) {
        return new float[]{(screenX - m_fLeft) / m_fScale, (screenY - m_fTop) / m_fScale};
    }

    /** 当前变换的缩放倍数（原版 {@code m_fScale}，只在 onDraw 后有效） */
    public float getViewScale() {
        return m_fScale;
    }

    /** 当前变换的左边界（原版 {@code m_fLeft}，只在 onDraw 后有效） */
    public float getViewLeft() {
        return m_fLeft;
    }

    /** 当前变换的上边界（原版 {@code m_fTop}，只在 onDraw 后有效） */
    public float getViewTop() {
        return m_fTop;
    }

    /** 原始缩放倍数（原版 {@code mScale}） */
    public float getBaseScale() {
        return mScale;
    }

    /** 图片像素尺寸（原版 {@code m_nPicWidth} / {@code m_nPicHeight}） */
    public int getPicWidth() {
        return m_nPicWidth;
    }

    public int getPicHeight() {
        return m_nPicHeight;
    }

    /** 关掉长按定时器，避免测试之间残留（{@code @After} 里调用） */
    public void cancelPendingTimers() {
        cancelLongPress();
    }
}
