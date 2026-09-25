package my.boxman;

import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloChoiceDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloViewDialog;
import my.boxman.compat.UiWindow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 「关卡图像识别」窗口 —— Android {@code myRecogView} Activity 的 1:1 移植。
 *
 * <p><b>ActionBar</b>：原版 {@code setTitle("")}（空标题）+ 返回键。
 * 菜单来自 {@code res/menu/recog.xml}，<b>6 项全部 {@code showAsAction="always"}</b>，
 * 所以它们是 ActionBar 上的文字按钮而不是溢出项：
 * <table border="1">
 *   <tr><th>id</th><th>标题</th><th>动作</th></tr>
 *   <tr><td>recog_about</td><td>？</td><td>{@code Help(6)}「图片识别说明」</td></tr>
 *   <tr><td>recog_restore</td><td>悔</td><td>{@code myRestore()} 撤销本次识别</td></tr>
 *   <tr><td>recog_similarity</td><td>度</td><td>「识别设置」相似度 5~9</td></tr>
 *   <tr><td>recog_shrink</td><td>减</td><td>减少格子数</td></tr>
 *   <tr><td>recog_extend</td><td>增</td><td>增加格子数</td></tr>
 *   <tr><td>recog_complete</td><td>识别</td><td>识别↔编辑切换（会改写自身标题）</td></tr>
 * </table>
 *
 * <p><b>改写前 PC 版只有 183 行</b>，自造了一条「识别操作」菜单（3 项，原版根本没有），
 * 底行是 10 个 {@code JButton} 等分（原版是 29dp/35dp 固定尺寸 + 2dp 外边距），
 * 而且缺了：底部 6 个元素按钮的<b>长按清理菜单</b>、←→↑↓ 的<b>连续调整定时器</b>、
 * 「请选择:」退出框、{@code getXSB()} / {@code removeXSB()} / {@code myBackup()} 等逻辑。
 * 关卡图的绘制与识别算法见 {@link myRecogViewMap}。
 */
public class myRecogView extends JFrame {

    public myRecogViewMap mMap;

    char[][] m_cArray, bk_cArray;   // 地图，备份地图
    int m_nBoxNum, DstNum;

    public int actNum = 0;          // 定时器功能选择
    int selNum = -1;                // 当前的选择条目
    boolean isInAction = false;     // 是否正在识别

    FlatButton bt_Floor, bt_Wall, bt_Box, bt_BoxGoal, bt_Goal, bt_Player;
    FlatButton bt_Left, bt_Right, bt_Up, bt_Down;

    private myActionBar actionBar;
    private HoloAlertDialog exitDlg;
    private Timer mTimer;

    /** 底行按钮的底色（{@code recog_view.xml} 里 10 个按钮都写死这个色） */
    private static final int BT_BG = 0xff334455;
    /** 识别模式 / 编辑模式的元素高亮色（原版 {@code setColor}） */
    private static final int HL_RECOG = 0x9f0000ff;
    private static final int HL_EDIT = 0x9fff3300;
    /** {@code recog_dialog.xml} 上下两条 2dp 分隔条的颜色 */
    private static final Color SEP_BLUE = new Color(0x18759E);

    public myRecogView() {
        setTitle("图像识别 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        initMap();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);
    }

    // ================================================================ 界面

    private void initUI() {
        setLayout(new BorderLayout());

        // 原版：setTitle("") + setDisplayHomeAsUpEnabled(true) + setDisplayShowHomeEnabled(false)
        actionBar = new myActionBar();
        actionBar.setBarTitle("");
        actionBar.setUpEnabled(true, this::onBack);
        // recog.xml 的 6 项全是 showAsAction="always"
        actionBar.addBarAction("？", () -> new Help(6).setVisible(true));
        actionBar.addBarAction("悔", this::onRestore);
        actionBar.addBarAction("度", this::onSimilarity);
        actionBar.addBarAction("减", this::onShrink);
        actionBar.addBarAction("增", this::onExtend);
        actionBar.addBarAction("识别", this::onToggleRecog);
        add(actionBar, BorderLayout.NORTH);

        mMap = new myRecogViewMap();
        mMap.Init(this);
        add(mMap, BorderLayout.CENTER);

        add(createBottomBar(), BorderLayout.SOUTH);

        // 原版 onCreate 末尾用 dlg0 建好的「请选择:」框（create() 而非 show()，随用随弹）
        exitDlg = HoloAlertDialog.create(this, "请选择:");
        exitDlg.addButton("取消", exitDlg::dispose);
        exitDlg.addButton("退出", () -> {
            exitDlg.dispose();
            dispose();
        });
        exitDlg.addButton("进入编辑", () -> {
            exitDlg.dispose();
            enterEdit();
        });
    }

    /**
     * 底行按钮栏（原版 {@code recog_view.xml} 的 {@code LinearLayout#recog_bottom}）。
     *
     * <p>原版尺寸（1dp = 1px）：
     * <pre>
     * 地板/墙壁/箱子/标箱/目标/人  29x29，margin 2（地板只写了 marginTop+marginLeft）
     * 中间一个 8dp 的 TextView 占位
     * ←/→/↑/↓                    35x29，margin 2
     * </pre>
     * 合计 360dp，背景 {@code #ff778899}，高 29+2+2 = 33dp。
     * 因为宽度是「固定值 + 不等距外边距」，不是等分，所以这里用绝对定位而不是 LayoutManager
     * —— 等分底栏在本项目里踩过坑（见 memory：GridLayout 会给 370/7 的格子整体偏移 +3px）。
     */
    private JPanel createBottomBar() {
        JPanel bar = new JPanel(null);
        bar.setBackground(new Color(0xFF778899));
        bar.setOpaque(true);
        Dimension d = new Dimension(0, 33);
        bar.setPreferredSize(d);
        bar.setMinimumSize(d);

        bt_Floor = new FlatButton("-", 29, 29, true);
        bt_Floor.addClick(() -> setColor(0));
        bt_Floor.addLongClick(() -> onElementLongClick(0, 1,
                new String[]{"清空地图"}, 0, i -> {
                    clearXSB();
                    mMap.repaint();
                }));

        bt_Wall = new FlatButton("#", 29, 29, true);
        bt_Wall.addClick(() -> setColor(1));
        bt_Wall.addLongClick(() -> onElementLongClick(1, 1,
                new String[]{"清理墙壁"}, 0, i -> {
                    removeXSB('#', true);
                    mMap.repaint();
                }));

        bt_Box = new FlatButton("$", 29, 29, true);
        bt_Box.addClick(() -> setColor(2));
        bt_Box.addLongClick(() -> onElementLongClick(2, 2,
                new String[]{"清理纯箱子", "清理所有箱子"}, 0, i -> {
                    removeXSB('$', selNum == 1);
                    mMap.repaint();
                }));

        bt_BoxGoal = new FlatButton("*", 29, 29, true);
        bt_BoxGoal.addClick(() -> setColor(3));
        bt_BoxGoal.addLongClick(() -> onElementLongClick(3, 2,
                new String[]{"清理目标点箱子", "保留目标点清理箱子"}, 0, i -> {
                    removeXSB('*', selNum == 0);
                    mMap.repaint();
                }));

        bt_Goal = new FlatButton(".", 29, 29, true);
        bt_Goal.addClick(() -> setColor(4));
        bt_Goal.addLongClick(() -> onElementLongClick(4, 2,
                new String[]{"清理纯目标点", "清理所有目标点"}, 0, i -> {
                    removeXSB('.', selNum == 1);
                    mMap.repaint();
                }));

        bt_Player = new FlatButton("@", 29, 29, true);
        bt_Player.addClick(() -> setColor(5));

        bt_Left = new FlatButton("←", 35, 29, false);
        bt_Left.addClick(() -> shiftEdge(1));
        bt_Left.addLongClick(() -> startTimer(1));

        bt_Right = new FlatButton("→", 35, 29, false);
        bt_Right.addClick(() -> shiftEdge(2));
        bt_Right.addLongClick(() -> startTimer(2));

        bt_Up = new FlatButton("↑", 35, 29, false);
        bt_Up.addClick(() -> shiftEdge(3));
        bt_Up.addLongClick(() -> startTimer(3));

        bt_Down = new FlatButton("↓", 35, 29, false);
        bt_Down.addClick(() -> shiftEdge(4));
        bt_Down.addLongClick(() -> startTimer(4));

        // 依 recog_view.xml 逐个子项累计 x（1dp = 1px）
        int x = 0;
        x += 2;                        // bt_floor: marginLeft 2（没有 marginRight）
        put(bar, bt_Floor, x, 2);      x += 29;
        x += 2;                        // bt_wall: margin 2
        put(bar, bt_Wall, x, 2);       x += 29 + 2;
        x += 2;
        put(bar, bt_Box, x, 2);        x += 29 + 2;
        x += 2;
        put(bar, bt_BoxGoal, x, 2);    x += 29 + 2;
        x += 2;
        put(bar, bt_Goal, x, 2);       x += 29 + 2;
        x += 2;
        put(bar, bt_Player, x, 2);     x += 29 + 2;
        x += 8;                        // 中间那个 8dp 的 TextView
        x += 2;
        put(bar, bt_Left, x, 2);       x += 35 + 2;
        x += 2;
        put(bar, bt_Right, x, 2);      x += 35 + 2;
        x += 2;
        put(bar, bt_Up, x, 2);         x += 35 + 2;
        x += 2;
        put(bar, bt_Down, x, 2);

        return bar;
    }

    private static void put(JPanel bar, JComponent c, int x, int y) {
        c.setBounds(x, y, c.getPreferredSize().width, c.getPreferredSize().height);
        bar.add(c);
    }

    // ================================================================ 菜单动作

    /** 原版 {@code android.R.id.home} 与 {@code KEYCODE_BACK} 的共用逻辑 */
    private void onBack() {
        if (mMap.m_nCols < 3 || mMap.m_nRows < 3) {
            dispose();
        } else {
            exitDlg.setVisible(true);   // 有过识别动作，提示保存
        }
    }

    /** {@code recog_restore}「悔」 */
    private void onRestore() {
        myRestore();
        mMap.repaint();
    }

    /** {@code recog_shrink}「减」：减少格子数 */
    private void onShrink() {
        mMap.cur_Rect.top = -1;   // 取消焦点框
        if (mMap.m_nRows > 3 && mMap.m_nCols > 3) {
            mMap.m_nCols--;
            mMap.repaint();
            mMap.m_nWidth = (float) (mMap.m_nMapRight - mMap.m_nMapLeft + 1) / mMap.m_nCols;
            mMap.m_nRows = (int) ((mMap.m_nMapBottom - mMap.m_nMapTop + 1) / mMap.m_nWidth);
        }
    }

    /** {@code recog_extend}「增」：增加格子数 */
    private void onExtend() {
        mMap.cur_Rect.top = -1;   // 取消焦点框
        mMap.m_nWidth = (float) (mMap.m_nMapRight - mMap.m_nMapLeft + 1) / mMap.m_nCols;
        if (mMap.m_nCols < 100 && (int) mMap.m_nWidth > 8) {
            mMap.m_nCols++;
            mMap.repaint();
            mMap.m_nWidth = (float) (mMap.m_nMapRight - mMap.m_nMapLeft + 1) / mMap.m_nCols;
            mMap.m_nRows = (int) ((mMap.m_nMapBottom - mMap.m_nMapTop + 1) / mMap.m_nWidth);
        }
    }

    /**
     * {@code recog_complete}「识别」：切换识别 / 编辑模式，
     * 并把<b>自己的标题</b>在「识别」与「编辑」之间来回改（原版 {@code mt.setTitle}）。
     */
    private void onToggleRecog() {
        setColor(-1);   // 取消底行 XSB 元素高亮
        if ("识别".equals(currentToggleTitle())) {
            actionBar.setBarActionTitle("识别", "编辑");
            mMap.isRecog = false;
        } else {
            actionBar.setBarActionTitle("编辑", "识别");
            mMap.isRecog = true;
        }
        mMap.repaint();
    }

    /** 第 6 个动作项当前叫什么（原版用 {@code mt.getTitle()} 判分支） */
    private String currentToggleTitle() {
        java.util.List<String> titles = actionBar.getBarActionTitles();
        return titles.isEmpty() ? "" : titles.get(titles.size() - 1);
    }

    /** {@code recog_similarity}「度」：识别设置（相似度 5~9） */
    private void onSimilarity() {
        HoloViewDialog.show(this, "识别设置", buildSimilarityContent(), "确定");
    }

    // ================================================================ 相似度设置内容

    /** {@code res/layout/recog_dialog.xml}：上下各一条 2dp 蓝条，中间一行「相似度: 5 6 7 8 9」。 */
    private JComponent buildSimilarityContent() {
        final SimilarityBox[] boxes = new SimilarityBox[5];
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(HoloContent.BAND);
        row.setOpaque(true);
        // 原版这一行的 LinearLayout 是 android:gravity="center"，
        // 对 horizontal 的 LinearLayout 来说就是「整块内容水平居中」（layoutHorizontal 的
        // majorGravity 分支取 CENTER_HORIZONTAL），两侧用 glue 等价实现。
        row.add(Box.createHorizontalGlue());

        JLabel lab = HoloContent.label("相似度:");
        lab.setBorder(new EmptyBorder(4, 4, 4, 4));
        Dimension ld = new Dimension(70, lab.getPreferredSize().height);
        lab.setPreferredSize(ld);
        lab.setMinimumSize(ld);
        lab.setMaximumSize(ld);
        lab.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(lab);

        row.add(Box.createRigidArea(new Dimension(8, 0)));

        int boxH = textRowHeight();
        for (int k = 0; k < 5; k++) {
            final int value = 5 + k;
            SimilarityBox b = new SimilarityBox(String.valueOf(value), 30, boxH,
                    () -> mMap.mSimilarity = value);
            boxes[k] = b;
            row.add(Box.createRigidArea(new Dimension(8, 0)));   // android:layout_marginLeft="8dp"
            row.add(b);
        }
        row.add(Box.createHorizontalGlue());

        // 初始高亮：setBT_Color(my_RBS[mMap.mSimilarity-5], true)
        int idx = Math.max(0, Math.min(4, mMap.mSimilarity - 5));
        boxes[idx].setSelected(true);
        // RadioGroup 的 OnCheckedChangeListener：先把 5 个都刷成未选中，再点亮新选中的
        for (SimilarityBox b : boxes) {
            b.setOnPicked(() -> {
                for (SimilarityBox o : boxes) o.setSelected(false);
                b.setSelected(true);
            });
        }

        return HoloContent.column(
                HoloContent.band(SEP_BLUE, 2),
                HoloContent.band(HoloContent.BAND, 10),
                row,
                HoloContent.band(HoloContent.BAND, 8),
                HoloContent.band(SEP_BLUE, 2));
    }

    /** 16sp 一行的自然高度（TextView 的 {@code wrap_content}） */
    private static int textRowHeight() {
        JLabel probe = new JLabel("5");
        probe.setFont(HoloContent.font(Font.PLAIN));
        return probe.getPreferredSize().height;
    }

    /**
     * {@code recog_dialog.xml} 里的一个相似度方块：
     * {@code RadioButton} + {@code android:button="@null"}，所以它就是一个
     * 30dp 宽的纯色方块（未选中黑底白字，选中白底黑字）。
     */
    static class SimilarityBox extends JComponent {
        private final String text;
        private boolean selected;
        private Runnable onPicked;

        SimilarityBox(String text, int width, int height, Runnable onClick) {
            this.text = text;
            Dimension d = new Dimension(width, height);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setAlignmentY(Component.CENTER_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) onClick.run();
                    if (onPicked != null) onPicked.run();
                    repaint();
                }
            });
        }

        void setOnPicked(Runnable r) {
            this.onPicked = r;
        }

        void setSelected(boolean s) {
            selected = s;
            repaint();
        }

        boolean isSelected() {
            return selected;
        }

        String getText() {
            return text;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            // setBT_Color：选中 → 白底黑字；未选中 → 黑底白字
            g2.setColor(selected ? Color.WHITE : Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(selected ? Color.BLACK : Color.WHITE);
            g2.setFont(HoloContent.font(Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2.drawString(text, (getWidth() - tw) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ================================================================ 底行按钮

    /** 元素按钮的长按「选项」框：先选中、再点「确定」 */
    private void onElementLongClick(int obj, int itemCount, String[] menu, int initial,
                                    HoloChoiceDialog.OnPick onOk) {
        mMap.m_nObj = -1;
        setColor(obj);
        HoloChoiceDialog.selectThenOk(this, "选项", null, menu, initial, i -> {
            selNum = i;
            onOk.pick(i);
        }).setVisible(true);
    }

    /** ←→↑↓ 的单击：微调边线（原版 {@code setOnClickListener}） */
    private void shiftEdge(int which) {
        if (actNum > 0) {    // 取消连续调整
            actNum = 0;      // 取消定时器功能
            return;
        }
        mMap.cur_Rect.top = -1;   // 取消焦点格子
        switch (which) {
            case 1:   // 左
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapLeft > 1) {
                        mMap.m_nMapLeft--;
                        mMap.repaint();
                    }
                } else {
                    if (mMap.m_nMapRight - 50 > mMap.m_nMapLeft) {
                        mMap.m_nMapRight--;
                        mMap.repaint();
                    }
                }
                break;
            case 2:   // 右
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapLeft + 50 < mMap.m_nMapRight) {
                        mMap.m_nMapLeft++;
                        mMap.repaint();
                    }
                } else {
                    if (mMap.m_nMapRight + 1 < mMap.m_nPicWidth - 1) {
                        mMap.m_nMapRight++;
                        mMap.repaint();
                    }
                }
                break;
            case 3:   // 上
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapTop > 1) {
                        mMap.m_nMapTop--;
                        mMap.repaint();
                    }
                } else {
                    if (mMap.m_nMapBottom - 50 > mMap.m_nMapTop) {
                        mMap.m_nMapBottom--;
                        mMap.repaint();
                    }
                }
                break;
            case 4:   // 下
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapTop + 50 < mMap.m_nMapBottom) {
                        mMap.m_nMapTop++;
                        mMap.repaint();
                    }
                } else {
                    if (mMap.m_nMapBottom + 1 < mMap.m_nPicHeight - 1) {
                        mMap.m_nMapBottom++;
                        mMap.repaint();
                    }
                }
                break;
        }
    }

    /** ←→↑↓ 的长按：启动连续调整定时器 */
    private void startTimer(int which) {
        mMap.cur_Rect.top = -1;   // 取消焦点框
        actNum = which;           // 定时器功能选择
        UpData(1);                // 启动定时器
    }

    /** 统一定时器处理（原版 {@code UpData(int p)}） */
    public void UpData(int p) {
        if (p != 1) {
            return;
        }

        switch (actNum) {
            case 1:                       // 长按「左」，微调左右边线
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapLeft > 3) {
                        mMap.m_nMapLeft -= 3;
                    } else {
                        mMap.m_nMapLeft = 0;
                    }
                } else {
                    if (mMap.m_nMapRight - 100 > mMap.m_nMapLeft) {
                        mMap.m_nMapRight -= 3;
                    }
                }
                mMap.repaint();
                sleep(20);
                break;
            case 2:                       // 长按「右」，微调左右边线
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapLeft + 100 < mMap.m_nMapRight) {
                        mMap.m_nMapLeft += 3;
                    }
                } else {
                    mMap.m_nMapRight += 3;
                    if (mMap.m_nMapRight > mMap.m_nPicWidth - 1) {
                        mMap.m_nMapRight = mMap.m_nPicWidth - 1;
                    }
                }
                mMap.repaint();
                sleep(20);
                break;
            case 3:                       // 长按「上」，微调上下边线
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapTop > 3) {
                        mMap.m_nMapTop -= 3;
                    } else {
                        mMap.m_nMapTop = 0;
                    }
                } else {
                    if (mMap.m_nMapBottom - 100 > mMap.m_nMapTop) {
                        mMap.m_nMapBottom -= 3;
                    }
                }
                mMap.repaint();
                sleep(20);
                break;
            case 4:                       // 长按「下」，微调上下边线
                if (mMap.isLeftTop) {
                    if (mMap.m_nMapTop + 100 < mMap.m_nMapBottom) {
                        mMap.m_nMapTop += 3;
                    }
                } else {
                    mMap.m_nMapBottom += 3;
                    if (mMap.m_nMapBottom > mMap.m_nPicHeight - 1) {
                        mMap.m_nMapBottom = mMap.m_nPicHeight - 1;
                    }
                }
                mMap.repaint();
                sleep(20);
                break;
            case 5:                       // 长按指示灯时，闪烁
                mMap.isLamp = !mMap.isLamp;
                mMap.repaint();
                sleep(500);
                break;
            default:
                break;
        }
    }

    /** 原版 {@code MyHandler.sleep(int)}：{@code removeMessages(0)} + {@code sendMessageDelayed(1, m)} */
    private void sleep(int ms) {
        stopTimer();
        mTimer = new Timer(ms, e -> UpData(1));
        mTimer.setRepeats(false);
        mTimer.start();
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.stop();
            mTimer = null;
        }
    }

    // ================================================================ 业务逻辑

    /** 自动识别（原版 {@code doAction()}） */
    public void doAction() {
        if (isInAction || mMap.cur_Rect.top < 0) return;

        isInAction = true;     // 识别中标记

        mMap.curPoints = mMap.findSubimages();

        // 应用识别结果
        for (int k = 0; k < mMap.curPoints.size(); k++) {
            setXSB(mMap.curPoints.get(k) & 0xffff, mMap.curPoints.get(k) >>> 16,
                    mMap.myXSB[mMap.m_nObj]);
        }

        isInAction = false;    // 识别结束

        mMap.repaint();
    }

    /** 还原到上一次识别出的地图（原版 {@code myRestore()}） */
    public void myRestore() {
        char ch;
        for (int i = 0; i < myMaps.m_nMaxRow; i++) {
            for (int j = 0; j < myMaps.m_nMaxRow; j++) {
                ch = m_cArray[i][j];
                m_cArray[i][j] = bk_cArray[i][j];
                bk_cArray[i][j] = ch;
            }
        }
    }

    /** 备份一次识别出的地图，以备撤销使用（只允许撤销一次）（原版 {@code myBackup()}） */
    public void myBackup() {
        for (int i = 0; i < myMaps.m_nMaxRow; i++) {
            for (int j = 0; j < myMaps.m_nMaxRow; j++) {
                bk_cArray[i][j] = m_cArray[i][j];
            }
        }
    }

    /** 计数箱子和目标点（原版 {@code myCount()}） */
    public void myCount() {
        m_nBoxNum = 0;
        DstNum = 0;
        for (int i = 0; i < mMap.m_nRows; i++) {
            for (int j = 0; j < mMap.m_nCols; j++) {
                if (m_cArray[i][j] == '$') {
                    m_nBoxNum++;
                } else if (m_cArray[i][j] == '*') {
                    m_nBoxNum++;
                    DstNum++;
                } else if (m_cArray[i][j] == '.' || m_cArray[i][j] == '+') {
                    DstNum++;
                }
            }
        }
    }

    /**
     * 将识别出来的 XSB 转换为字符串（原版 {@code getXSB()}）。
     * <b>注意</b>：原版两个循环都是 {@code <=}（写成 {@code m_nRows+1} 行 × {@code m_nCols+1} 列），
     * 这里照抄 —— 多出来的那一行/列基本都是 {@code -}，后续 {@code mapNormalize} 会裁掉。
     */
    String getXSB() {

        StringBuilder str = new StringBuilder();

        if (mMap.m_nRows < 3 || mMap.m_nCols < 3) {
            return null;
        }

        for (int i = 0; i <= mMap.m_nRows; i++) {
            for (int j = 0; j <= mMap.m_nCols; j++) {
                str.append(m_cArray[i][j]);
            }
            str.append('\n');
        }

        return str.toString();
    }

    /** 置 XSB（原版 {@code setXSB()}） */
    private void setXSB(int r, int c, char ch) {
        if (c < 0 || r < 0 || c >= myMaps.m_nMaxCol || r >= myMaps.m_nMaxRow) {
            return;
        }
        m_cArray[r][c] = ch;
    }

    /** 清除特定的 XSB 元素（原版 {@code removeXSB()}） */
    void removeXSB(char ch, boolean isAll) {
        myBackup();
        for (int i = 0; i < myMaps.m_nMaxRow; i++) {
            for (int j = 0; j < myMaps.m_nMaxRow; j++) {
                if (ch == '.') {
                    if (m_cArray[i][j] == '.') m_cArray[i][j] = '-';
                    // 考虑是否「所有目标点」选项
                    if (isAll) {
                        if (m_cArray[i][j] == '*') {
                            m_cArray[i][j] = '$';
                        } else if (m_cArray[i][j] == '+') {
                            m_cArray[i][j] = '@';
                        }
                    }
                } else if (ch == '$') {
                    if (m_cArray[i][j] == '$') m_cArray[i][j] = '-';
                    // 考虑是否「所有箱子」选项
                    if (isAll && m_cArray[i][j] == '*') m_cArray[i][j] = '.';
                } else if (ch == '*') {
                    // 考虑是否「保留目标点」选项
                    if (isAll) {
                        if (m_cArray[i][j] == '*') m_cArray[i][j] = '-';
                    } else {
                        if (m_cArray[i][j] == '*') m_cArray[i][j] = '.';
                    }
                } else {
                    if (m_cArray[i][j] == ch) m_cArray[i][j] = '-';
                }
            }
        }
    }

    /** 清除所有的 XSB 元素（原版 {@code clearXSB()}） */
    void clearXSB() {
        myBackup();
        for (int i = 0; i < myMaps.m_nMaxRow; i++) {
            for (int j = 0; j < myMaps.m_nMaxRow; j++) {
                if (m_cArray[i][j] != '-') {
                    m_cArray[i][j] = '-';
                }
            }
        }
    }

    /** 元素按钮的状态颜色（原版 {@code setColor()}） */
    public void setColor(int n) {
        setBtnColor(bt_Floor, BT_BG);
        setBtnColor(bt_Wall, BT_BG);
        setBtnColor(bt_Box, BT_BG);
        setBtnColor(bt_BoxGoal, BT_BG);
        setBtnColor(bt_Goal, BT_BG);
        setBtnColor(bt_Player, BT_BG);

        int m_Color;
        if (mMap.isRecog) {  // 识别模式的高亮颜色
            m_Color = HL_RECOG;
        } else {             // 编辑模式的高亮颜色
            m_Color = HL_EDIT;
        }

        if (mMap.m_nObj == n) {
            mMap.m_nObj = -1;
        } else {
            mMap.m_nObj = n;
            switch (n) {
                case 0: setBtnColor(bt_Floor, m_Color); break;
                case 1: setBtnColor(bt_Wall, m_Color); break;
                case 2: setBtnColor(bt_Box, m_Color); break;
                case 3: setBtnColor(bt_BoxGoal, m_Color); break;
                case 4: setBtnColor(bt_Goal, m_Color); break;
                case 5: setBtnColor(bt_Player, m_Color); break;
                default: break;
            }
        }
    }

    private static void setBtnColor(FlatButton b, int argb) {
        if (b != null) b.setColor(new Color(argb, true));
    }

    /** 地图数组初始化（原版 {@code initMap()}） */
    private void initMap() {
        // 默认尺寸：100 * 100
        m_cArray = new char[myMaps.m_nMaxRow][myMaps.m_nMaxCol];
        bk_cArray = new char[myMaps.m_nMaxRow][myMaps.m_nMaxCol];
        for (int i = 0; i < myMaps.m_nMaxRow; i++) {
            for (int j = 0; j < myMaps.m_nMaxCol; j++) {
                m_cArray[i][j] = '-';
                bk_cArray[i][j] = '-';
            }
        }
        mMap.initArena();  // 舞台初始化
    }

    // ================================================================ 「进入编辑」

    /** 原版 exitDlg 的「进入编辑」按钮：跳转到关卡编辑器 */
    void enterEdit() {
        prepareEditHandoff();

        myEditView edit = new myEditView();
        edit.setVisible(true);
        dispose();
    }

    /**
     * 「进入编辑」的状态准备（不含窗口跳转，便于测试）。
     * 原版把识别结果转成 {@code mapNode} 塞进 {@code myMaps.curMap}，
     * 并把截图的四至与行列数记进 {@code myMaps.edPict*} / {@code edRows} / {@code edCols}，
     * 供 {@code myEditViewMap} 画参照底图用。
     */
    void prepareEditHandoff() {
        // 若关卡标题不空，则以关卡标题为本命名新关卡标题；否则以关卡集名及其序号为本命名
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        final String newTitle = "Recog_" + df.format(new Date());   // new Date() 获取当前系统时间
        final String level = getXSB();

        if (level == null) {
            if (myMaps.curMap != null) myMaps.curMap.Map = null;
        } else {
            myMaps.curMap = new mapNode(mMap.m_nRows, mMap.m_nCols, level.split("\n"), newTitle, "", "");
        }
        if (myMaps.curMap == null) {     // 原版此处必然非 null；PC 侧兜底，避免 NPE
            myMaps.curMap = new mapNode("-", newTitle, "", "");
        }
        myMaps.sFile = "创编关卡";        // 取得当前关卡文档名
        myMaps.curMap.fileName = newTitle + ".XSB";
        if (mMap.m_nRows < 3 || mMap.m_nCols < 3) {
            myMaps.curMapNum = -5;       // 识别关卡编辑
        } else {
            myMaps.curMapNum = -4;       // 识别关卡编辑
        }
        // 计算截图尺寸
        myMaps.edPictLeft = mMap.m_nMapLeft;
        myMaps.edPictTop = mMap.m_nMapTop;
        myMaps.edPictRight = mMap.m_nMapRight;
        myMaps.edPictBottom = (int) (mMap.m_nMapTop + mMap.m_nRows * mMap.m_nWidth);
        myMaps.edRows = mMap.m_nRows;
        myMaps.edCols = mMap.m_nCols;
    }

    // ================================================================ 底行按钮组件

    /**
     * 原版 {@code <Button android:background="#ff334455" android:textSize="14sp"/>} 的等价物：
     * <b>纯色底 + 居中文字</b>。
     *
     * <p>为什么不直接用 {@code JButton}：FlatLaf 会接管 JButton 的外观，
     * {@code setBackground} 并不总能覆盖主题底色；而本界面按钮的底色<b>本身就是语义</b>
     * （高亮当前选中的 XSB 元素），必须完全可控，所以自绘。
     * （顺带也解决了 29dp 小按钮下 FlatLaf 默认 margin 把文字挤成「...」的问题。）
     *
     * <p>长按语义照抄 Android：{@code setOnLongClickListener} 返回 {@code true} 时，
     * 长按之后抬起<b>不再触发</b> click（地板/墙壁/箱子/标箱/目标）；返回 {@code false}
     * 时 click 仍会触发（←→↑↓，正好用来「松开即停止连续调整」）。
     */
    static class FlatButton extends JComponent {
        private static final Color TEXT = new Color(0xFFF3F3F3);   // primary_text_holo_dark
        private static final int LONG_PRESS_MS = 500;              // ViewConfiguration

        private final String text;
        private final boolean consumeLongClick;
        private Color bg = new Color(0xFF334455);
        private Runnable onClick;
        private Runnable onLongClick;
        private Timer longPressTimer;
        private boolean pressed, longFired, suppressClick;

        FlatButton(String text, int w, int h, boolean consumeLongClick) {
            this.text = text;
            this.consumeLongClick = consumeLongClick;
            Dimension d = new Dimension(w, h);
            setPreferredSize(d);
            setMinimumSize(d);
            setSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    longFired = false;
                    cancelLongPress();
                    longPressTimer = new Timer(LONG_PRESS_MS, ev -> {
                        if (pressed) {
                            longFired = true;
                            if (onLongClick != null) onLongClick.run();
                        }
                    });
                    longPressTimer.setRepeats(false);
                    longPressTimer.start();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    cancelLongPress();
                    if (longFired && consumeLongClick) suppressClick = true;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (suppressClick) {
                        suppressClick = false;
                        return;
                    }
                    if (onClick != null) onClick.run();
                }
            });
        }

        private void cancelLongPress() {
            if (longPressTimer != null) {
                longPressTimer.stop();
                longPressTimer = null;
            }
        }

        void addClick(Runnable r) {
            this.onClick = r;
        }

        void addLongClick(Runnable r) {
            this.onLongClick = r;
        }

        void setColor(Color c) {
            this.bg = c;
            repaint();
        }

        Color getColor() {
            return bg;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(TEXT);
            g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2.drawString(text, (getWidth() - tw) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ================================================================ 测试钩子

    public myActionBar getActionBar() {
        return actionBar;
    }

    public myRecogViewMap getMap() {
        return mMap;
    }

    public char[][] getCellArray() {
        return m_cArray;
    }

    public char[][] getBackupArray() {
        return bk_cArray;
    }

    public int getBoxNum() {
        return m_nBoxNum;
    }

    public int getDstNum() {
        return DstNum;
    }

    public HoloAlertDialog getExitDialog() {
        return exitDlg;
    }

    /** 触发 ActionBar 上某个文字按钮（测试用；走的是真实的事件分发路径） */
    public void clickBarAction(String title) {
        if (!actionBar.fireBarAction(title)) {
            throw new IllegalArgumentException("no such bar action: " + title);
        }
    }

    public void shiftEdgeForTest(int which) {
        shiftEdge(which);
    }

    public void toggleRecogForTest() {
        onToggleRecog();
    }

    public void backForTest() {
        onBack();
    }

    public void similarityDialogForTest() {
        onSimilarity();
    }

    /** 只构造「识别设置」的内容区（不弹窗），供测试检查那 5 个相似度方块 */
    public JComponent buildSimilarityContentForTest() {
        return buildSimilarityContent();
    }

    /** 原版 {@code UpData} 里 {@code actNum} 的当前值（测试用） */
    public int getActNum() {
        return actNum;
    }
}
