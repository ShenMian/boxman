package my.boxman;

import my.boxman.compat.ResourceLoader;
import my.boxman.jsoko.DiagonalLock;
import my.boxman.jsoko.FreezeLock;
import my.boxman.jsoko.IntStack;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import my.boxman.compat.UiWindow;
import my.boxman.compat.HoloAlertDialog;
import my.boxman.compat.HoloContent;
import my.boxman.compat.HoloPopupMenu;

public class myGameView extends JFrame {

    /**
     * 底栏按钮 = 原版 {@code res/values/style.xml} 的 {@code tab_style}。
     *
     * <p>{@code main_bottom} 与 {@code myEditView} 的 {@code edit_bottom} 用的是同一份样式，
     * 实现已提到 {@link my.boxman.compat.HoloTabBar.TabButton}；这里只留一个名字。
     */
    public static class GameButton extends my.boxman.compat.HoloTabBar.TabButton {
        public GameButton(String text) {
            super(text);
        }

        public GameButton(String text, Icon icon) {
            super(text, icon);
        }
    }

    AsyncCountBoxsTask mTask;
    public RunMicroTask mMicroTask;

    javax.swing.Timer mClockTimer;  // 背景时间定时器
    javax.swing.Timer myTimer1;
    javax.swing.Timer myTimer2;
    javax.swing.Timer myTimer3;
    javax.swing.Timer myTimer4;

    public myGameViewMap mMap;  // 地图
    public myPathfinder mPathfinder;  // 探路者

    // 底行按钮
    public GameButton bt_UnDo = null;
    public GameButton bt_ReDo = null;
    public GameButton bt_More = null;
    public GameButton bt_IM = null;
    public GameButton bt_Sel = null;
    public GameButton bt_TR = null;
    public GameButton bt_BK = null;

    JPanel main_bottom; // 底部栏面板

    /** 「更多」按钮弹出的选项菜单（原版 {@code res/menu/player.xml}），见 {@link #openOptionsMenu()}。 */
    private JPopupMenu optionsMenu;

    // 正推，目标数、完成数、仓管员初始位置
    public int m_nGoals;
    public int m_nGoals_OK;
    public int m_nRow, m_nCol;
    // 逆推，目标数、完成数、仓管员初始位置
    public int m_nGoals_2;
    public int m_nGoals_OK_2;
    public int m_nRow2;
    public int m_nCol2;
    public int m_nRow0;  // 逆推时，记录仓管员初始占位，周转用
    public int m_nCol0;
    public int m_iR9, m_iC9, m_iR10, m_iC10; // 正逆推时，箱子停止的位置，判断死锁移动时使用
    public int b_nRow = -1, b_nCol = -1, oldDir; // 动画中的箱子
    public final int m_iSleep[] = {50, 17, 10, 5, 2}; // 移动速度
    public final String m_sSleep[] = {"最快", "较快", "中速", "较慢", "最慢"}; // 移动速度

    public LinkedList<Byte> m_lstMovedHistory = new LinkedList<Byte>();  // 临时栈，记录当前点前的动作，MacroDebug 会用的到

    public LinkedList<Byte> m_lstMovUnDo;  // unDo 栈
    public LinkedList<Byte> m_lstMovReDo;  // reDo 栈

    public LinkedList<Byte> m_lstMovUnDo2;  // 逆推 unDo 栈
    public LinkedList<Byte> m_lstMovReDo2;  // 逆推 reDo 栈

    public char[][] mArray9;  // 检查死锁用的临时地图（空地图），正推根据目标数识别死锁时，也会用到
    public myPathfinder mPF;  // 检查死锁用的探路者，正推根据目标数识别死锁时，也会用到
    public boolean[][] mark15, mark16, mark7, mark8, mark11, mark12, mark44;  // 15、为逆推死点；16、为正推网锁点；7、为正推箱子初位；8、为正推初态地板；11、为哪个箱子能动或未动过；12、为正推未被使用过的地板；44、是否显示标尺
    public short[][] mark14;  // 14、为逆推可推的点（其它为推死的点）
    public byte[][] mark41;  // 网锁标志

    public int m_Gif_Start = 0;  // 导出 GIF 的起点
    public int m_nStep, m_nLastSteps = -1;  // 执行"推"、"移"的步数
    public boolean m_bYanshi, m_bYanshi2;  // 是否演示
    public String m_imPort_YASS;  // 是否做过动作“导入”或“YASS”过动作
    public int m_iStep[] = new int[4];    // 记录"推"、"移"的步数
    public boolean m_bACT_ERROR;  // 执行动作时是否遇到错误
    public boolean m_bACT_IgnoreCase;  // 执行动作时是否忽略大小写
    public boolean m_bMoved;  // 有新动作
    public boolean m_bBusing;  // 忙中
    public boolean m_bPush;  // 推
    public boolean m_bNetLock;  // 网锁

    // 四邻：左、右、上、下
    final byte[] dr4 = {0, 0, -1, 1};
    final byte[] dc4 = {-1, 1, 0, 0};

    public short[][] m_iBoxNum;  // 迷宫箱子编号（人为）
    public short[][] m_iBoxNum2;  // 迷宫箱子自动（固定）编号
    public char[][] m_cArray0;  // 迷宫初态
    public char[][] m_cArray;  // 迷宫
    public char[][] bk_cArray;  // 逆推迷宫

    public char[][] ls_bk_cArray;  // 导入时用的临时逆推迷宫

    public byte[][] m_selArray;  // 迷宫选择
    public byte[][] bk_selArray;  // 逆推迷宫选择

    public DiagonalLock closedDiagonalLock;
    public FreezeLock freezeDeadlock;
    public byte[][] m_Freeze;  // 检查冻结时使用的临时数字

    public int m_nRow3;  // 逆推过关时用，记录仓管员正推地图之初始占位
    public int m_nCol3;
    public int m_nRow4;  // 导入用，记录仓管员逆推地图之初始占位
    public int m_nCol4;
    public int m_nItemSelect;  // 对话框中的出item选择前的记忆

    int MyMINUTE;   // 记忆时钟的分钟数，用于更新背景时间
    int m_nMacro_Row, m_nMacro_Col;  // “宏”功能中，用于记忆仓管员坐标

    public myGameView() {
        setTitle("推箱快手");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }

            /**
             * 原版 {@code myGameView.onStart()}：从「关卡状态与答案」窗口回来时，
             * 如果那边置了 {@code m_StateIsRedy}，就把选中的状态/答案载入进来。
             * PC 端用「窗口重新获得焦点」等价替代 Activity 的 onStart()。
             */
            @Override
            public void windowActivated(WindowEvent e) {
                if (myMaps.m_StateIsRedy) {
                    OpenState();  //处理打开的状态
                }
            }
        });

        initUI();

        // ⚠️ 必须在 UI 装好之后、initGame() 之前调：initGame() 里按地图控件的实际尺寸
        // 装载关卡，窗口没 pack 过的话地图尺寸为 0，舞台区就画不出来。
        UiWindow.applyPhoneSize(this);

        initTimers();
        initGame();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        mMap = new myGameViewMap();
        mMap.Init(this);
        add(mMap, BorderLayout.CENTER);

        main_bottom = createBottomBar();
        add(main_bottom, BorderLayout.SOUTH);

        // 原版 myGameView 是 FEATURE_NO_TITLE + FLAG_FULLSCREEN：既没有 ActionBar 也没有菜单栏，
        // 全部菜单项都在底栏「更多」按钮弹出的选项菜单里（对应 res/menu/player.xml，见 openOptionsMenu()）。
        //
        // ⚠️ 这里曾经还挂过一份 PC 自造的「地图右键菜单」（installMapPopupMenu，导航/操作/视图/工具/帮助
        // 五组）。原版 myGameView / myGameViewMap 里没有任何 registerForContextMenu /
        // onCreateContextMenu / setOnLongClickListener，地图上没有右键菜单 —— 已整体删除（阶段 G ④）。
        // 原先靠它兜底的入口现在都有原版路径了：
        //   关卡编辑器   → myGridView 上下文菜单「改编为新关卡 / 编辑」
        //   相似关卡对比 → myGridView 上下文菜单「查找相似关卡」→ FindDialog → myFindView
        //   图像识别     → BoxMan 菜单「图像识别」→ myPicListView → myRecogView
        //   动作管理     → player_IN「导入...」
        //   关卡状态     → player_load「打开状态...」
        //   背景颜色/标尺/导出/GIF/重新开始/帮助/关于 → 「设置...」/「开关选项...」/player.xml 各项
    }

    private Icon getScaledIcon(String resName) {
        return my.boxman.compat.HoloTabBar.icon(resName);
    }

    private JPanel createBottomBar() {
        JPanel bar = my.boxman.compat.HoloTabBar.bar();

        bt_UnDo = new GameButton("后退", getScaledIcon("undobtn"));
        bt_ReDo = new GameButton("前进", getScaledIcon("redobtn"));
        bt_IM   = new GameButton("瞬移", getScaledIcon("imbtn"));
        bt_BK   = new GameButton("逆推", getScaledIcon("bkbtn"));
        bt_Sel  = new GameButton("计数", getScaledIcon("selbtn"));
        bt_TR   = new GameButton("转置", getScaledIcon("trbtn"));
        bt_More = new GameButton("更多", getScaledIcon("chbtn"));

        setupButtonEvents();

        bar.add(bt_UnDo);
        bar.add(bt_ReDo);
        bar.add(bt_IM);
        bar.add(bt_BK);
        bar.add(bt_Sel);
        bar.add(bt_TR);
        bar.add(bt_More);

        return bar;
    }

    private void attachLongPress(GameButton btn, Runnable longPressAction) {
        btn.setOnLongClickListener(e -> longPressAction.run());

        final javax.swing.Timer timer = new javax.swing.Timer(500, null);
        timer.setRepeats(false);

        btn.addMouseListener(new MouseAdapter() {
            private boolean isLongPressTriggered = false;

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    longPressAction.run();
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isLongPressTriggered = false;
                    for (ActionListener al : timer.getActionListeners()) {
                        timer.removeActionListener(al);
                    }
                    timer.addActionListener(ev -> {
                        isLongPressTriggered = true;
                        longPressAction.run();
                    });
                    timer.start();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (timer.isRunning()) {
                    timer.stop();
                }
            }
        });
    }

    private void setupButtonEvents() {
        // 后退
        bt_UnDo.addItemListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bNetLock = false;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            m_bACT_ERROR = false;
            mMap.invalidate();
            if (m_bBusing) return;

            if (bt_BK.isChecked()) {
                if (m_lstMovUnDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    if (myMaps.m_Sets[23] == 1) m_nStep = 1;
                    else m_nStep = getStep(m_lstMovUnDo2);
                    UpData4(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    levelReset(false);
                    if (myMaps.m_ActionIsPos) {
                        Iterator<Byte> myItr = m_lstMovedHistory.iterator();
                        m_lstMovReDo.clear();
                        m_lstMovUnDo.clear();
                        while (myItr.hasNext()) {
                            m_lstMovReDo.offer(myItr.next());
                            m_nStep = 1;
                            reDo1();
                        }
                        mMap.invalidate();
                    }
                    mMap.curMoves = 0;

                    m_nMacro_Row = m_nRow;
                    m_nMacro_Col = m_nCol;
                    int len = mMap.myMacro.size();
                    if (len > 1) {
                        mMap.myMacro.remove(len - 1);
                        for (int k = 0; k < len - 2; k++) {
                            myDo_Block(mMap.myMacro.get(k), mMap.myMacro.get(k), true, false);
                        }
                        m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (m_lstMovUnDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        if (myMaps.m_Sets[23] == 1) m_nStep = 1;
                        else {
                            if (m_nLastSteps >= 0 && m_nLastSteps <= m_lstMovUnDo.size()) m_nStep = m_lstMovUnDo.size() - m_nLastSteps;
                            else m_nStep = getStep2(m_lstMovUnDo);
                        }
                        m_nLastSteps = -1;
                        UpData2(1);
                    }
                }
            }
            m_bBusing = false;
        });

        attachLongPress(bt_UnDo, () -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bNetLock = false;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            m_bACT_ERROR = false;
            mMap.d_Moves = mMap.m_PicWidth;
            mMap.invalidate();

            if (bt_BK.isChecked()) {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(true);
            } else {
                MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
                levelReset(false);

                if (myMaps.m_ActionIsPos && myMaps.isMacroDebug) {
                    Iterator<Byte> myItr = m_lstMovedHistory.iterator();
                    m_lstMovReDo.clear();
                    m_lstMovUnDo.clear();
                    while (myItr.hasNext()) {
                        m_lstMovReDo.offer(myItr.next());
                        m_nStep = 1;
                        reDo1();
                    }
                }
                mMap.myMacro.clear();
                mMap.myMacro.add(0);
                mMap.myMacroInf = "";
            }
            mMap.curMoves = 0;
            m_bBusing = false;
            mMap.invalidate();
        });

        // 前进
        bt_ReDo.addItemListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bNetLock = false;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            m_bACT_ERROR = false;
            mMap.invalidate();
            if (m_bBusing) return;

            if (bt_BK.isChecked()) {
                if (m_lstMovReDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    if (myMaps.m_Sets[23] == 1) m_nStep = 1;
                    else m_nStep = getStep2(m_lstMovReDo2);
                    UpData3(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    if (mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                        myDo_Block(mMap.myMacro.get(mMap.myMacro.size() - 1), mMap.myMacro.get(mMap.myMacro.size() - 1), false, false);
                        m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (m_lstMovReDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        if (myMaps.m_Sets[23] == 1) m_nStep = 1;
                        else m_nStep = getStep(m_lstMovReDo);
                        mMap.Box_Row0 = -1;
                        m_nLastSteps = m_lstMovUnDo.size();
                        UpData1(1);
                    }
                }
            }
            m_bBusing = false;
        });

        attachLongPress(bt_ReDo, () -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bNetLock = false;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            mMap.m_lChangeBK = false;
            m_bACT_ERROR = false;
            mMap.invalidate();

            if (bt_BK.isChecked()) {
                if (m_lstMovReDo2.isEmpty()) {
                    MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                } else {
                    MyToast.showToast(this, "进至尾！", MyToast.LENGTH_SHORT);
                    if (m_lstMovUnDo2.isEmpty()) goHome();
                    m_nStep = m_lstMovReDo2.size();
                    m_bYanshi = false;
                    m_bYanshi2 = true;
                    mMap.curMoves = 0;
                    UpData3(1);
                }
            } else {
                if (myMaps.isMacroDebug) {
                    if (mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                        myDo_Block(mMap.myMacro.get(mMap.myMacro.size() - 1), myMaps.sAction.length - 1, false, false);
                        m_lstMovReDo.clear();
                        mMap.invalidate();
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                } else {
                    if (m_lstMovReDo.isEmpty()) {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    } else {
                        MyToast.showToast(this, "正向演示！", MyToast.LENGTH_SHORT);
                        m_nStep = m_lstMovReDo.size();
                        mMap.Box_Row0 = -1;
                        m_bYanshi = true;
                        m_bYanshi2 = false;
                        mMap.curMoves = 0;
                        m_nLastSteps = -1;
                        UpData1(1);
                    }
                }
            }
            m_bBusing = false;
        });

        // 瞬移
        bt_IM.setChecked(myMaps.m_Sets[6] == 1);
        bt_IM.addItemListener(e -> {
            mMap.d_Moves = mMap.m_PicWidth;
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            if (bt_IM.isChecked()) {
                myMaps.m_Sets[6] = 1;
            } else {
                myMaps.m_Sets[6] = 0;
            }
        });

        // 逆推
        bt_BK.setChecked(false);
        bt_BK.addItemListener(e -> {
            StopMicro();
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            myMaps.isRecording = false;
            mMap.d_Moves = mMap.m_PicWidth;
            m_bBusing = false;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            m_bACT_ERROR = false;
            if (bt_BK.isChecked()) {
                if (m_nRow2 < 0 || m_nCol2 < 0) {
                    mMap.m_iR = m_nRow2;
                    mMap.m_iC = m_nCol2;
                    MyToast.showToast(this, "需要给出仓管员的位置！", MyToast.LENGTH_SHORT);
                }
            } else {
                mMap.m_iR = m_nRow;
                mMap.m_iC = m_nCol;
            }
            m_bNetLock = false;
            mMap.invalidate();
        });

        // 计数
        bt_Sel.setChecked(false);
        bt_Sel.addItemListener(e -> {
            StopMicro();
            mMap.d_Moves = mMap.m_PicWidth;
            m_nStep = 0;
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bYanshi = false;
            m_bYanshi2 = false;
            m_bBusing = false;
            mMap.m_lGoto = false;
            mMap.m_lGoto2 = false;
            mMap.m_lParityBrightnessShade = false;

            mMap.m_Count[0] = 0;
            mMap.m_Count[1] = 0;
            mMap.m_Count[2] = 0;
            mMap.m_Count[3] = 0;
            mMap.m_Count[4] = 0;
            mMap.m_Count[5] = 0;
            if (m_cArray != null) {
                for (int i = 0; i < m_cArray.length; i++) {
                    for (int j = 0; j < m_cArray[0].length; j++) {
                        m_selArray[i][j] = 0;
                        bk_selArray[i][j] = 0;
                    }
                }
            }

            if (bt_Sel.isChecked()) {
                mMap.m_boxCanMove = false;
                mMap.m_boxNoMoved = false;
                mMap.m_boxCanMove2 = false;
                mMap.m_boxNoUsed = false;
            }
            m_bNetLock = false;
            mMap.m_lChangeBK = false;
            mMap.invalidate();
        });

        // 转置
        bt_TR.setChecked(false);
        bt_TR.setText(myMaps.m_nTrun + " 转");
        bt_TR.addItemListener(e -> {
            StopMicro();
            myMaps.m_nTrun = (myMaps.m_nTrun + 1) % 8;
            if (myMaps.curMap != null) myMaps.curMap.Trun = myMaps.m_nTrun;
            bt_TR.setText(myMaps.m_nTrun + " 转");
            mMap.initArena();
            mMap.invalidate();
            if (myMaps.m_nTrun == 0) {
                MyToast.showToast(this, "第 0 转", MyToast.LENGTH_SHORT);
            }
        });
        attachLongPress(bt_TR, () -> {
            StopMicro();
            myMaps.m_nTrun = 0;
            if (myMaps.curMap != null) myMaps.curMap.Trun = myMaps.m_nTrun;
            bt_TR.setText(myMaps.m_nTrun + " 转");
            mMap.initArena();
            mMap.invalidate();
            MyToast.showToast(this, "第 0 转", MyToast.LENGTH_SHORT);
        });

        // 更多
        bt_More.addActionListener(e -> {
            StopMicro();
            bt_Sel.setChecked(false);
            if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
            m_bNetLock = false;
            mMap.m_lChangeBK = false;
            m_bACT_ERROR = false;
            mMap.invalidate();
            mMap.d_Moves = mMap.m_PicWidth;
            m_nStep = 0;
            m_bYanshi = false;
            m_bYanshi2 = false;
            m_bBusing = false;

            openOptionsMenu();
        });
        attachLongPress(bt_More, () -> {
            MyToast.showToast(this, "可以离开了！", MyToast.LENGTH_SHORT);
            handleExit();
        });
    }

    private void handleExit() {
        StopMicro();
        bt_Sel.setChecked(false);
        if (mMap.m_lShowAnsInf) mMap.m_lShowAnsInf = false;
        m_bNetLock = false;
        mMap.m_lChangeBK = false;
        m_bACT_ERROR = false;
        mMap.d_Moves = mMap.m_PicWidth;
        m_nStep = 0;
        m_bYanshi = false;
        m_bYanshi2 = false;
        m_bBusing = false;
        mMap.invalidate();

        if (m_bMoved && ((m_lstMovUnDo != null && m_lstMovUnDo.size() > 0) || (m_lstMovUnDo2 != null && m_lstMovUnDo2.size() > 0))) {
            int ret = JOptionPane.showConfirmDialog(this, "有状态未保存，坚持退出吗？", "退出", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION) {
                myStop();
                dispose();
            }
        } else {
            myStop();
            dispose();
        }
    }

    private void initTimers() {
        MyMINUTE = 0;
        mClockTimer = new javax.swing.Timer(3000, e -> {
            if (myMaps.m_Sets[25] == 1) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                if (MyMINUTE != cal.get(Calendar.MINUTE)) {
                    MyMINUTE = cal.get(Calendar.MINUTE);
                    if (mMap != null) mMap.repaint();
                }
            }
        });
        mClockTimer.start();

        myTimer1 = new javax.swing.Timer(1, e -> UpData1(1));
        myTimer1.setRepeats(false);

        myTimer2 = new javax.swing.Timer(1, e -> UpData2(1));
        myTimer2.setRepeats(false);

        myTimer3 = new javax.swing.Timer(1, e -> UpData3(1));
        myTimer3.setRepeats(false);

        myTimer4 = new javax.swing.Timer(1, e -> UpData4(1));
        myTimer4.setRepeats(false);
    }

    private void sleepTimer(javax.swing.Timer timer, int ms) {
        if (timer.isRunning()) timer.stop();
        timer.setInitialDelay(Math.max(1, ms));
        timer.start();
    }

    private void initGame() {
        skinList();
        bkPicList();

        if (myMaps.curMap == null) {
            if (myMaps.m_lstMaps != null && !myMaps.m_lstMaps.isEmpty()) {
                myMaps.curMap = myMaps.m_lstMaps.get(0);
            }
        }

        if (myMaps.curMap != null) {
            myMaps.m_nTrun = myMaps.curMap.Trun;
        }
        myMaps.isHengping = false;
        m_iR9 = -1;
        m_iC9 = -1;
        m_iR10 = -1;
        m_iC10 = -1;

        initMap();
        m_bNetLock = false;

        closedDiagonalLock = new DiagonalLock(this);
        freezeDeadlock = new FreezeLock(this);
    }

    // 旋转方向计算
    private int getRotate(int mDir1, int mDir2) {
        int mRT = 0;
        if (b_nRow < 0 || (!bt_BK.isChecked() && myMaps.m_Sets[18] > 0)) {
            switch (mDir1) {
                case 1:
                    switch (mDir2) {
                        case 2: mRT = -2080; break;
                        case 3: mRT = -10170; break;
                        case 4: mRT = -1080; break;
                    }
                    break;
                case 2:
                    switch (mDir2) {
                        case 1: mRT = -1080; break;
                        case 3: mRT = -2080; break;
                        case 4: mRT = -10170; break;
                    }
                    break;
                case 3:
                    switch (mDir2) {
                        case 1: mRT = -10170; break;
                        case 2: mRT = -1080; break;
                        case 4: mRT = -2080; break;
                    }
                    break;
                case 4:
                    switch (mDir2) {
                        case 1: mRT = -2080; break;
                        case 2: mRT = -10170; break;
                        case 3: mRT = -1080; break;
                    }
                    break;
            }
        } else {
            switch (mDir1) {
                case 1:
                    switch (mDir2) {
                        case 4: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 1: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 2: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                    }
                    break;
                case 2:
                    switch (mDir2) {
                        case 3: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                        case 1: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 2: if (!bt_BK.isChecked()) mRT = -10170; break;
                    }
                    break;
                case 3:
                    switch (mDir2) {
                        case 3: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 4: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                        case 2: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                    }
                    break;
                case 4:
                    switch (mDir2) {
                        case 3: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -1080 : -2080; break;
                        case 4: if (!bt_BK.isChecked()) mRT = -10170; break;
                        case 1: mRT = (bt_BK.isChecked() && myMaps.m_Sets[18] < 0) ? -2080 : -1080; break;
                    }
                    break;
            }
        }
        return mRT;
    }

    int[] YanshiSpeed = {0, 100, 200, 500, 1000};

    // redo -- 正推
    public void UpData1(int i) {
        if ((i != 1 || m_nStep <= 0 || m_lstMovReDo.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance()) {
                if (m_bMoved) {
                    if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                        saveAns(1);
                        int opt = JOptionPane.showConfirmDialog(this, "恭喜过关！\n是否自动打开下一个未解关卡？", "恭喜过关", JOptionPane.YES_NO_OPTION);
                        if (opt == JOptionPane.YES_OPTION) {
                            int k = myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1;
                            int len = myMaps.m_lstMaps.size();
                            while (k < len && myMaps.m_lstMaps.get(k).Solved) {
                                k++;
                            }
                            if (k < len) {
                                myMaps.curMap = myMaps.m_lstMaps.get(k);
                                myMaps.m_nTrun = myMaps.curMap.Trun;
                                bt_BK.setChecked(false);
                                bt_TR.setChecked(false);
                                bt_TR.setText(myMaps.m_nTrun + " 转");
                                initMap();
                                ls_bk_cArray = bk_cArray;
                            } else {
                                MyToast.showToast(this, "后面没有未解关卡！", MyToast.LENGTH_SHORT);
                            }
                        }
                    } else {
                        MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
                        saveAns(0);
                    }
                } else MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            m_bBusing = false;
            m_bYanshi = false;
            m_bYanshi2 = false;
            return;
        }

        if (bt_IM.isChecked()) {
            if (m_bYanshi) {
                do {
                    reDo1();
                } while (m_nStep > 0 && (m_bPush && m_lstMovReDo.getLast() == m_Dir || !m_bPush && m_lstMovReDo.getLast() < 5));
                mMap.invalidate();

                if (m_nStep > 0) {
                    sleepTimer(myTimer1, YanshiSpeed[myMaps.m_Sets[10]]);
                    m_bPush = false;
                }
            } else {
                while (m_nStep > 0) reDo1();
                mMap.invalidate();
            }
            if (myClearance()) {
                m_bYanshi = false;
                if (m_bMoved) {
                    if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                        saveAns(1);
                        int opt = JOptionPane.showConfirmDialog(this, "恭喜过关！\n是否自动打开下一个未解关卡？", "恭喜过关", JOptionPane.YES_NO_OPTION);
                        if (opt == JOptionPane.YES_OPTION) {
                            int k = myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1;
                            int len = myMaps.m_lstMaps.size();
                            while (k < len && myMaps.m_lstMaps.get(k).Solved) {
                                k++;
                            }
                            if (k < len) {
                                myMaps.curMap = myMaps.m_lstMaps.get(k);
                                myMaps.m_nTrun = myMaps.curMap.Trun;
                                bt_BK.setChecked(false);
                                bt_TR.setChecked(false);
                                bt_TR.setText(myMaps.m_nTrun + " 转");
                                initMap();
                                ls_bk_cArray = bk_cArray;
                            } else {
                                MyToast.showToast(this, "后面没有未解关卡！", MyToast.LENGTH_SHORT);
                            }
                        }
                    } else {
                        MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
                        saveAns(0);
                    }
                } else MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                m_bYanshi = false;
                if (m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                b_nRow = -1;
                b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                if (m_bYanshi && myMaps.m_Sets[28] == 1) {
                    do {
                        reDo1();
                    } while (m_nStep > 0 && !m_bPush);
                    m_bPush = false;
                } else {
                    reDo1();
                }
                myMaps.m_Sets[18] = 1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3) {
                    mMap.d_Moves = 0;
                } else {
                    if (!m_bACT_ERROR) {
                        mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
                    }
                }
            }
            mMap.invalidate();
            sleepTimer(myTimer1, 1);
        }

        // 正推死锁判断
        if (m_nStep == 0 && myMaps.m_Sets[11] == 1 && mMap.d_Moves >= mMap.m_PicWidth && myLock(m_iR9, m_iC9)) {
            int ret = JOptionPane.showConfirmDialog(this, "这一步造成关卡死锁，继续吗？", "死锁移动", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.NO_OPTION) {
                bt_UnDo.setChecked(!bt_UnDo.isChecked());
            }
        }
    }

    // undo -- 正推
    public void UpData2(int i) {
        if ((i != 1 || m_nStep <= 0 || m_lstMovUnDo.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance())
                MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            m_bBusing = false;
            m_bYanshi2 = false;
            return;
        }

        if (bt_IM.isChecked()) {
            while (m_nStep > 0) unDo1();
            mMap.invalidate();
            if (myClearance()) {
                m_bYanshi2 = false;
                MyToast.showToast(this, "正推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                m_bYanshi2 = false;
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                b_nRow = -1;
                b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                unDo1();
                myMaps.m_Sets[18] = -1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3)
                    mMap.d_Moves = 0;
                else mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
            }
            mMap.invalidate();
            sleepTimer(myTimer2, 1);
        }
    }

    // redo -- 逆推
    public void UpData3(int i) {
        if ((i != 1 || m_nStep <= 0 || m_lstMovReDo2.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance2()) {
                if (m_bMoved) {
                    zhengniHE2();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            m_bBusing = false;
            return;
        }

        if (bt_IM.isChecked()) {
            if (m_bYanshi2) {
                do {
                    reDo2();
                } while (m_nStep > 0 && (m_bPush && m_lstMovReDo2.getLast() == m_Dir || !m_bPush && m_lstMovReDo2.getLast() < 5));
                mMap.invalidate();
                if (m_nStep > 0) {
                    sleepTimer(myTimer3, YanshiSpeed[myMaps.m_Sets[10]]);
                    m_bPush = false;
                }
            } else {
                while (m_nStep > 0) reDo2();
                mMap.invalidate();
            }
            if (myClearance2()) {
                if (m_bMoved) {
                    zhengniHE2();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            } else if (myMeet()) {
                if (m_bMoved) {
                    zhengniHE();
                    saveAns2();
                    if (myMaps.curMap != null && myMaps.curMap.Level_id < 0)
                        MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
                } else MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            }
            m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                b_nRow = -1;
                b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                reDo2();
                myMaps.m_Sets[18] = 1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3) {
                    mMap.d_Moves = 0;
                } else {
                    if (!m_bACT_ERROR) {
                        mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
                    }
                }
            }
            mMap.invalidate();
            sleepTimer(myTimer3, 1);
        }

        // 逆推死锁判断
        if (m_nStep == 0 && myMaps.m_Sets[11] == 1 && mMap.d_Moves >= mMap.m_PicWidth && myLock2(m_iR10, m_iC10)) {
            int ret = JOptionPane.showConfirmDialog(this, "这一步造成关卡死锁，继续吗？", "死锁移动", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.NO_OPTION) {
                bt_UnDo.setChecked(!bt_UnDo.isChecked());
            }
        }
    }

    // undo -- 逆推
    public void UpData4(int i) {
        if ((i != 1 || m_nStep <= 0 || m_lstMovUnDo2.isEmpty()) && mMap.d_Moves >= mMap.m_PicWidth) {
            if (myClearance2())
                MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            m_bBusing = false;
            return;
        }

        if (bt_IM.isChecked()) {
            while (m_nStep > 0) unDo2();
            mMap.invalidate();
            if (myClearance2())
                MyToast.showToast(this, "逆推通关！", MyToast.LENGTH_SHORT);
            else if (myMeet())
                MyToast.showToast(this, "正逆相合！", MyToast.LENGTH_SHORT);
            m_bBusing = false;
        } else {
            if (mMap.d_Moves < mMap.m_PicWidth) {
                if (mMap.d_Moves >= 0) mMap.d_Moves += m_iSleep[myMaps.m_Sets[10]];
                else mMap.d_Moves += (myMaps.m_Sets[10] > 3 ? 10 : 30);
                if (mMap.d_Moves >= -10000 && mMap.d_Moves < -2080 || mMap.d_Moves >= -2000 && mMap.d_Moves < -1080 || mMap.d_Moves >= -1000 && mMap.d_Moves < 0)
                    mMap.d_Moves = 0;
            } else {
                b_nRow = -1;
                b_nCol = -1;
                oldDir = myMaps.m_Sets[5];
                unDo2();
                myMaps.m_Sets[18] = -1;
                if (myMaps.isSimpleSkin || myMaps.isSkin_200 == 0 || myMaps.m_Sets[27] == 0 || myMaps.m_Sets[10] < 3)
                    mMap.d_Moves = 0;
                else mMap.d_Moves = getRotate(oldDir, myMaps.m_Sets[14]);
            }
            mMap.invalidate();
            sleepTimer(myTimer4, 1);
        }
    }

    int i, j, i2, j2;
    byte m_Dir;
    int[] dr_reDo1 = {0, 0, -1, 0, 1, 0, -2, 0, 2};
    int[] dc_reDo1 = {0, -1, 0, 1, 0, -2, 0, 2, 0};

    public void reDo1() {
        if (mMap.m_lGoto) {
            int len = m_lstMovUnDo.size();
            int len2 = m_lstMovUnDo.size() + m_lstMovReDo.size();
            if (len2 > 0) {
                mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            } else mMap.curMoves = 0;
        }
        if (m_lstMovReDo.isEmpty()) {
            m_bBusing = false;
            m_nStep = 0;
            return;
        }
        m_bBusing = true;
        m_iR9 = -1;
        m_Dir = m_lstMovReDo.pollLast();

        if (m_Dir < 1 || m_Dir > 8) {
            m_bBusing = false;
            m_lstMovReDo.clear();
            m_nStep = 0;
            m_bACT_ERROR = true;
            return;
        }
        m_bPush = (m_Dir > 4);
        if (mMap.m_bBoxTo) mMap.m_bBoxTo = false;
        if (mMap.m_bManTo) mMap.m_bManTo = false;
        if (mMap.m_boxCanMove) mMap.m_boxCanMove = false;
        if (mMap.m_boxNoMoved) mMap.m_boxNoMoved = false;
        if (mMap.m_boxNoUsed) mMap.m_boxNoUsed = false;
        if (mMap.m_boxCanCome) mMap.m_boxCanCome = false;

        if (m_Dir < 5) {
            myMaps.m_Sets[14] = m_Dir;
            myMaps.m_Sets[5] = m_Dir;
            i = m_nRow + dr_reDo1[m_Dir];
            j = m_nCol + dc_reDo1[m_Dir];
            i2 = -1;
            j2 = -1;
        } else {
            myMaps.m_Sets[14] = m_Dir - 4;
            myMaps.m_Sets[5] = m_Dir - 4;
            i = m_nRow + dr_reDo1[m_Dir - 4];
            j = m_nCol + dc_reDo1[m_Dir - 4];
            i2 = m_nRow + dr_reDo1[m_Dir];
            j2 = m_nCol + dc_reDo1[m_Dir];
        }

        if (m_bACT_IgnoreCase) {
            if (m_Dir > 4) {
                if (i >= 0 && j >= 0 && i < m_cArray.length && j < m_cArray[0].length &&
                        (m_cArray[i][j] == '-' || m_cArray[i][j] == '.')) {
                    m_Dir = (byte) (m_Dir - 4);
                    i = m_nRow + dr_reDo1[m_Dir];
                    j = m_nCol + dc_reDo1[m_Dir];
                    i2 = -1;
                    j2 = -1;
                }
            } else {
                if (i >= 0 && j >= 0 && i < m_cArray.length && j < m_cArray[0].length &&
                        (m_cArray[i][j] == '$' || m_cArray[i][j] == '*')) {
                    m_Dir = (byte) (m_Dir + 4);
                    i = m_nRow + dr_reDo1[m_Dir - 4];
                    j = m_nCol + dc_reDo1[m_Dir - 4];
                    i2 = m_nRow + dr_reDo1[m_Dir];
                    j2 = m_nCol + dc_reDo1[m_Dir];
                }
            }
        }

        if (m_Dir > 4) {
            if (i < 0 || j < 0 || i2 < 0 || j2 < 0 ||
                    i >= m_cArray.length || j >= m_cArray[0].length || i2 >= m_cArray.length || j2 >= m_cArray[0].length ||
                    m_cArray[i2][j2] != '-' && m_cArray[i2][j2] != '.' ||
                    m_cArray[i][j] != '$' && m_cArray[i][j] != '*') {
                m_lstMovReDo.clear();
                m_bBusing = false;
                m_nStep = 0;
                m_bACT_ERROR = true;
                return;
            }
            if (mMap.Box_Row0 < 0) {
                mMap.Box_Row0 = i;
                mMap.Box_Col0 = j;
            }
            if (m_cArray[i2][j2] == '-')
                m_cArray[i2][j2] = '$';
            else {
                m_cArray[i2][j2] = '*';
                m_nGoals_OK++;
            }
            m_iBoxNum[i2][j2] = m_iBoxNum[i][j];
            m_iBoxNum[i][j] = -1;
            m_iBoxNum2[i2][j2] = m_iBoxNum2[i][j];
            m_iBoxNum2[i][j] = -1;

            if (m_cArray[i][j] == '$')
                m_cArray[i][j] = '@';
            else {
                m_cArray[i][j] = '+';
                m_nGoals_OK--;
            }
            if (m_cArray[m_nRow][m_nCol] == '@')
                m_cArray[m_nRow][m_nCol] = '-';
            else
                m_cArray[m_nRow][m_nCol] = '.';

            m_nRow = i;
            m_nCol = j;
            m_iR9 = i2;
            m_iC9 = j2;
            b_nRow = i2;
            b_nCol = j2;

            m_lstMovUnDo.offer(m_Dir);
            m_iStep[0]++;
        } else {
            if (i < 0 || j < 0 || i >= m_cArray.length || j >= m_cArray[0].length ||
                    m_cArray[i][j] != '-' && m_cArray[i][j] != '.') {
                m_lstMovReDo.clear();
                m_bBusing = false;
                m_nStep = 0;
                m_bACT_ERROR = true;
                return;
            }
            if (m_cArray[i][j] == '-')
                m_cArray[i][j] = '@';
            else {
                m_cArray[i][j] = '+';
            }
            if (m_cArray[m_nRow][m_nCol] == '@')
                m_cArray[m_nRow][m_nCol] = '-';
            else
                m_cArray[m_nRow][m_nCol] = '.';

            m_nRow = i;
            m_nCol = j;

            m_lstMovUnDo.offer(m_Dir);
        }

        m_iStep[1]++;
        m_nStep--;
        mMap.m_iR = m_nRow;
        mMap.m_iC = m_nCol;
    }

    byte[] dr_unDo1 = {0, 0, 1, 0, -1, 0, -1, 0, 1};
    byte[] dc_unDo1 = {0, 1, 0, -1, 0, -1, 0, 1, 0};

    public void unDo1() {
        if (mMap.m_lGoto) {
            int len = m_lstMovUnDo.size();
            int len2 = m_lstMovUnDo.size() + m_lstMovReDo.size();
            if (len2 > 0) {
                mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            } else mMap.curMoves = 0;
        }
        if (m_lstMovUnDo.isEmpty()) {
            m_bBusing = false;
            return;
        }
        m_bBusing = true;
        m_Dir = m_lstMovUnDo.pollLast();

        m_bPush = (m_Dir > 4);
        if (mMap.m_bBoxTo) mMap.m_bBoxTo = false;
        if (mMap.m_bManTo) mMap.m_bManTo = false;
        if (mMap.m_boxCanMove) mMap.m_boxCanMove = false;
        if (mMap.m_boxNoMoved) mMap.m_boxNoMoved = false;
        if (mMap.m_boxNoUsed) mMap.m_boxNoUsed = false;
        if (mMap.m_boxCanCome) mMap.m_boxCanCome = false;

        switch (m_Dir) {
            case 1:
                myMaps.m_Sets[14] = 3;
                myMaps.m_Sets[5] = 3;
                break;
            case 2:
                myMaps.m_Sets[14] = 4;
                myMaps.m_Sets[5] = 4;
                break;
            case 3:
                myMaps.m_Sets[14] = 1;
                myMaps.m_Sets[5] = 1;
                break;
            case 4:
                myMaps.m_Sets[14] = 2;
                myMaps.m_Sets[5] = 2;
                break;
            case 5:
                myMaps.m_Sets[14] = 3;
                myMaps.m_Sets[5] = 1;
                break;
            case 6:
                myMaps.m_Sets[14] = 4;
                myMaps.m_Sets[5] = 2;
                break;
            case 7:
                myMaps.m_Sets[14] = 1;
                myMaps.m_Sets[5] = 3;
                break;
            case 8:
                myMaps.m_Sets[14] = 2;
                myMaps.m_Sets[5] = 4;
                break;
        }
        if (m_Dir < 5) {
            i = m_nRow + dr_unDo1[m_Dir];
            j = m_nCol + dc_unDo1[m_Dir];
            i2 = -1;
            j2 = -1;
        } else {
            i = m_nRow + dr_unDo1[m_Dir - 4];
            j = m_nCol + dc_unDo1[m_Dir - 4];
            i2 = m_nRow + dr_unDo1[m_Dir];
            j2 = m_nCol + dc_unDo1[m_Dir];
        }

        if (m_Dir > 4) {
            if ((m_cArray[i][j] == '-' || m_cArray[i][j] == '.') &&
                    (m_cArray[i2][j2] == '$' || m_cArray[i2][j2] == '*')) {
                if (m_cArray[i][j] == '-')
                    m_cArray[i][j] = '@';
                else {
                    m_cArray[i][j] = '+';
                }
                if (m_cArray[m_nRow][m_nCol] == '@')
                    m_cArray[m_nRow][m_nCol] = '$';
                else {
                    m_cArray[m_nRow][m_nCol] = '*';
                    m_nGoals_OK++;
                }
                if (m_cArray[i2][j2] == '$')
                    m_cArray[i2][j2] = '-';
                else {
                    m_cArray[i2][j2] = '.';
                    m_nGoals_OK--;
                }
                m_iBoxNum[m_nRow][m_nCol] = m_iBoxNum[i2][j2];
                m_iBoxNum[i2][j2] = -1;
                m_iBoxNum2[m_nRow][m_nCol] = m_iBoxNum2[i2][j2];
                m_iBoxNum2[i2][j2] = -1;

                b_nRow = m_nRow;
                b_nCol = m_nCol;
                m_nRow = i;
                m_nCol = j;

                m_lstMovReDo.offer(m_Dir);
                m_iStep[0]--;
            }
        } else {
            if (m_cArray[i][j] == '-' || m_cArray[i][j] == '.') {
                if (m_cArray[i][j] == '-')
                    m_cArray[i][j] = '@';
                else {
                    m_cArray[i][j] = '+';
                }
                if (m_cArray[m_nRow][m_nCol] == '@')
                    m_cArray[m_nRow][m_nCol] = '-';
                else
                    m_cArray[m_nRow][m_nCol] = '.';

                m_nRow = i;
                m_nCol = j;

                m_lstMovReDo.offer(m_Dir);
            }
        }

        m_iStep[1]--;
        if (m_iStep[1] < m_Gif_Start) m_Gif_Start = 0;
        m_nStep--;
        mMap.m_iR = m_nRow;
        mMap.m_iC = m_nCol;
    }

    int[] dr_reDo2 = {0, 0, -1, 0, 1, 0, 1, 0, -1};
    int[] dc_reDo2 = {0, -1, 0, 1, 0, 1, 0, -1, 0};

    public void reDo2() {
        if (mMap.m_lGoto2) {
            int len = m_lstMovUnDo2.size();
            int len2 = m_lstMovUnDo2.size() + m_lstMovReDo2.size();
            if (len2 > 0) {
                mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            } else mMap.curMoves2 = 0;
        }
        if (m_lstMovReDo2.isEmpty()) {
            m_bBusing = false;
            m_nStep = 0;
            return;
        }
        m_bBusing = true;
        m_iR10 = -1;
        m_Dir = m_lstMovReDo2.pollLast();

        if (m_Dir < 1 || m_Dir > 8) {
            m_bBusing = false;
            m_lstMovReDo2.clear();
            m_nStep = 0;
            m_bACT_ERROR = true;
            return;
        }
        m_bPush = (m_Dir > 4);
        if (mMap.m_bBoxTo2) mMap.m_bBoxTo2 = false;
        if (mMap.m_bManTo2) mMap.m_bManTo2 = false;
        if (mMap.m_boxCanMove2) mMap.m_boxCanMove2 = false;
        if (mMap.m_boxCanCome2) mMap.m_boxCanCome2 = false;

        switch (m_Dir) {
            case 1:
                myMaps.m_Sets[14] = 1;
                myMaps.m_Sets[5] = 1;
                break;
            case 2:
                myMaps.m_Sets[14] = 2;
                myMaps.m_Sets[5] = 2;
                break;
            case 3:
                myMaps.m_Sets[14] = 3;
                myMaps.m_Sets[5] = 3;
                break;
            case 4:
                myMaps.m_Sets[14] = 4;
                myMaps.m_Sets[5] = 4;
                break;
            case 5:
                myMaps.m_Sets[14] = 1;
                myMaps.m_Sets[5] = 3;
                break;
            case 6:
                myMaps.m_Sets[14] = 2;
                myMaps.m_Sets[5] = 4;
                break;
            case 7:
                myMaps.m_Sets[14] = 3;
                myMaps.m_Sets[5] = 1;
                break;
            case 8:
                myMaps.m_Sets[14] = 4;
                myMaps.m_Sets[5] = 2;
                break;
        }
        if (m_Dir < 5) {
            i = m_nRow2 + dr_reDo2[m_Dir];
            j = m_nCol2 + dc_reDo2[m_Dir];
            i2 = -1;
            j2 = -1;
        } else {
            i = m_nRow2 + dr_reDo2[m_Dir - 4];
            j = m_nCol2 + dc_reDo2[m_Dir - 4];
            i2 = m_nRow2 + dr_reDo2[m_Dir];
            j2 = m_nCol2 + dc_reDo2[m_Dir];
        }

        if (m_bACT_IgnoreCase) {
            if (m_Dir > 4) {
                if (i >= 0 && j >= 0 && i < bk_cArray.length && j < bk_cArray[0].length &&
                        (bk_cArray[i2][j2] == '-' || bk_cArray[i2][j2] == '.')) {
                    m_Dir = (byte) (m_Dir - 4);
                    i = m_nRow2 + dr_reDo2[m_Dir];
                    j = m_nCol2 + dc_reDo2[m_Dir];
                    i2 = -1;
                    j2 = -1;
                }
            } else {
                if (i >= 0 && j >= 0 && i < bk_cArray.length && j < bk_cArray[0].length &&
                        (bk_cArray[m_nRow2 + dr_reDo2[m_Dir]][m_nCol2 + dc_reDo2[m_Dir]] == '$' || bk_cArray[m_nRow2 + dr_reDo2[m_Dir]][m_nCol2 + dc_reDo2[m_Dir]] == '*')) {
                    m_Dir = (byte) (m_Dir + 4);
                    i = m_nRow2 + dr_reDo2[m_Dir - 4];
                    j = m_nCol2 + dc_reDo2[m_Dir - 4];
                    i2 = m_nRow2 + dr_reDo2[m_Dir];
                    j2 = m_nCol2 + dc_reDo2[m_Dir];
                }
            }
        }

        if (m_Dir > 4) {
            if (i < 0 || j < 0 || i2 < 0 || j2 < 0 ||
                    i >= bk_cArray.length || j >= bk_cArray[0].length || i2 >= bk_cArray.length || j2 >= bk_cArray[0].length ||
                    bk_cArray[i][j] != '-' && bk_cArray[i][j] != '.' ||
                    bk_cArray[i2][j2] != '$' && bk_cArray[i2][j2] != '*') {
                m_lstMovReDo2.clear();
                m_bBusing = false;
                m_nStep = 0;
                m_bACT_ERROR = true;
                return;
            }
            if (bk_cArray[i][j] == '-')
                bk_cArray[i][j] = '@';
            else {
                bk_cArray[i][j] = '+';
            }
            if (bk_cArray[m_nRow2][m_nCol2] == '@')
                bk_cArray[m_nRow2][m_nCol2] = '$';
            else {
                bk_cArray[m_nRow2][m_nCol2] = '*';
                m_nGoals_OK_2++;
            }
            if (bk_cArray[i2][j2] == '$')
                bk_cArray[i2][j2] = '-';
            else {
                bk_cArray[i2][j2] = '.';
                m_nGoals_OK_2--;
            }

            m_iR10 = m_nRow2;
            m_iC10 = m_nCol2;
            b_nRow = m_nRow2;
            b_nCol = m_nCol2;
            m_nRow2 = i;
            m_nCol2 = j;

            m_lstMovUnDo2.offer(m_Dir);
            m_iStep[2]++;
        } else {
            if (i < 0 || j < 0 || i >= bk_cArray.length || j >= bk_cArray[0].length ||
                    bk_cArray[i][j] != '-' && bk_cArray[i][j] != '.') {
                m_lstMovReDo2.clear();
                m_bBusing = false;
                m_nStep = 0;
                m_bACT_ERROR = true;
                return;
            }
            if (bk_cArray[i][j] == '-')
                bk_cArray[i][j] = '@';
            else {
                bk_cArray[i][j] = '+';
            }
            if (bk_cArray[m_nRow2][m_nCol2] == '@')
                bk_cArray[m_nRow2][m_nCol2] = '-';
            else
                bk_cArray[m_nRow2][m_nCol2] = '.';

            m_nRow2 = i;
            m_nCol2 = j;

            m_lstMovUnDo2.offer(m_Dir);
        }

        m_iStep[3]++;
        m_nStep--;
        mMap.m_iR = m_nRow2;
        mMap.m_iC = m_nCol2;
    }

    int[] dr_unDo2 = {0, 0, 1, 0, -1, 0, 2, 0, -2};
    int[] dc_unDo2 = {0, 1, 0, -1, 0, 2, 0, -2, 0};

    public void unDo2() {
        if (mMap.m_lGoto2) {
            int len = m_lstMovUnDo2.size();
            int len2 = m_lstMovUnDo2.size() + m_lstMovReDo2.size();
            if (len2 > 0) {
                mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
            } else mMap.curMoves2 = 0;
        }
        if (m_lstMovUnDo2.isEmpty()) {
            m_bBusing = false;
            return;
        }
        m_bBusing = true;
        m_Dir = m_lstMovUnDo2.pollLast();

        m_bPush = (m_Dir > 4);
        if (mMap.m_bBoxTo2) mMap.m_bBoxTo2 = false;
        if (mMap.m_bManTo2) mMap.m_bManTo2 = false;
        if (mMap.m_boxCanMove2) mMap.m_boxCanMove2 = false;
        if (mMap.m_boxCanCome2) mMap.m_boxCanCome2 = false;

        switch (m_Dir) {
            case 1:
            case 5:
                myMaps.m_Sets[14] = 3;
                myMaps.m_Sets[5] = 3;
                break;
            case 2:
            case 6:
                myMaps.m_Sets[14] = 4;
                myMaps.m_Sets[5] = 4;
                break;
            case 3:
            case 7:
                myMaps.m_Sets[14] = 1;
                myMaps.m_Sets[5] = 1;
                break;
            case 4:
            case 8:
                myMaps.m_Sets[14] = 2;
                myMaps.m_Sets[5] = 2;
                break;
        }
        if (m_Dir < 5) {
            i = m_nRow2 + dr_unDo2[m_Dir];
            j = m_nCol2 + dc_unDo2[m_Dir];
            i2 = -1;
            j2 = -1;
        } else {
            i = m_nRow2 + dr_unDo2[m_Dir - 4];
            j = m_nCol2 + dc_unDo2[m_Dir - 4];
            i2 = m_nRow2 + dr_unDo2[m_Dir];
            j2 = m_nCol2 + dc_unDo2[m_Dir];
        }

        if (m_Dir > 4) {
            if ((bk_cArray[i2][j2] == '-' || bk_cArray[i2][j2] == '.') &&
                    (bk_cArray[i][j] == '$' || bk_cArray[i][j] == '*')) {
                if (bk_cArray[i2][j2] == '-')
                    bk_cArray[i2][j2] = '$';
                else {
                    bk_cArray[i2][j2] = '*';
                    m_nGoals_OK_2++;
                }
                if (bk_cArray[i][j] == '$')
                    bk_cArray[i][j] = '@';
                else {
                    bk_cArray[i][j] = '+';
                    m_nGoals_OK_2--;
                }
                if (bk_cArray[m_nRow2][m_nCol2] == '@')
                    bk_cArray[m_nRow2][m_nCol2] = '-';
                else
                    bk_cArray[m_nRow2][m_nCol2] = '.';

                m_nRow2 = i;
                m_nCol2 = j;
                b_nRow = i2;
                b_nCol = j2;

                m_lstMovReDo2.offer(m_Dir);
                m_iStep[2]--;
            }
        } else {
            if (bk_cArray[i][j] == '-' || bk_cArray[i][j] == '.') {
                if (bk_cArray[i][j] == '-')
                    bk_cArray[i][j] = '@';
                else {
                    bk_cArray[i][j] = '+';
                }
                if (bk_cArray[m_nRow2][m_nCol2] == '@')
                    bk_cArray[m_nRow2][m_nCol2] = '-';
                else
                    bk_cArray[m_nRow2][m_nCol2] = '.';

                m_nRow2 = i;
                m_nCol2 = j;

                m_lstMovReDo2.offer(m_Dir);
            }
        }

        m_iStep[3]--;
        m_nStep--;
        mMap.m_iR = m_nRow2;
        mMap.m_iC = m_nCol2;
    }

    private void goHome() {
        if (m_nRow2 != m_nRow0 || m_nCol2 != m_nCol0) {
            if (m_nRow2 > -1) {
                if (bk_cArray[m_nRow2][m_nCol2] == '@')
                    bk_cArray[m_nRow2][m_nCol2] = '-';
                else
                    bk_cArray[m_nRow2][m_nCol2] = '.';
            }

            if (bk_cArray[m_nRow0][m_nCol0] == '-')
                bk_cArray[m_nRow0][m_nCol0] = '@';
            else
                bk_cArray[m_nRow0][m_nCol0] = '+';

            m_nRow2 = m_nRow0;
            m_nCol2 = m_nCol0;

            mMap.invalidate();
        }
    }

    private void myPre(int n) {
        bt_BK.setChecked(false);
        bt_Sel.setChecked(false);
        myMaps.curMap = myMaps.m_lstMaps.get(n);
        myMaps.m_nTrun = myMaps.curMap.Trun;
        bt_TR.setText(myMaps.m_nTrun + " 转");
        initMap();
        ls_bk_cArray = bk_cArray;
    }

    private void myNext(int n) {
        bt_BK.setChecked(false);
        bt_Sel.setChecked(false);
        myMaps.curMap = myMaps.m_lstMaps.get(n);
        myMaps.m_nTrun = myMaps.curMap.Trun;
        bt_TR.setText(myMaps.m_nTrun + " 转");
        initMap();
        ls_bk_cArray = bk_cArray;
    }

    private void levelReset(boolean flg) {
        if (flg) {
            m_nStep = m_lstMovUnDo2.size();
            while (m_nStep > 0) unDo2();

            mMap.m_bBoxTo2 = false;
            mMap.m_bManTo2 = false;
            mMap.m_boxCanMove2 = false;
            mMap.m_boxCanCome2 = false;

            mMap.m_iR = m_nRow2;
            mMap.m_iC = m_nCol2;
        } else {
            m_nStep = m_lstMovUnDo.size();
            while (m_nStep > 0) unDo1();

            mMap.m_bBoxTo = false;
            mMap.m_bManTo = false;
            mMap.m_boxCanMove = false;
            mMap.m_boxNoMoved = false;
            mMap.m_boxNoUsed = false;
            mMap.m_boxCanCome = false;

            mMap.m_iR = m_nRow;
            mMap.m_iC = m_nCol;
        }

        m_nStep = 0;
        m_bBusing = false;
        m_bMoved = false;
        myMaps.m_StateIsRedy = false;
        myMaps.m_Sets[14] = 2;
        myMaps.m_Sets[5] = 2;
    }

    private void mBoxNum(char[][] level, int mr, int mc) {
        int nRows = level.length;
        int nCols = level[0].length;

        boolean[][] Mark = new boolean[nRows][nCols];
        int F, mr2, mc2;

        int[] dr = {-1, 1, 0, 0, -1, 1, -1, 1};
        int[] dc = {0, 0, 1, -1, -1, -1, 1, 1};

        Queue<Integer> P = new LinkedList<Integer>();
        P.offer(mr << 16 | mc);
        Mark[mr][mc] = true;
        while (!P.isEmpty()) {
            F = P.poll();
            mr = F >>> 16;
            mc = F & 0x0000FFFF;
            for (int k = 0; k < 4; k++) {
                mr2 = mr + dr[k];
                mc2 = mc + dc[k];
                if (mr2 < 0 || mr2 >= nRows || mc2 < 0 || mc2 >= nCols ||
                        Mark[mr2][mc2] || level[mr2][mc2] == '_' || level[mr2][mc2] == '#')
                    continue;

                P.add(mr2 << 16 | mc2);
                Mark[mr2][mc2] = true;
            }
        }

        short box_Num = 0;
        m_nGoals = 0;
        m_nGoals_OK = 0;
        m_nGoals_2 = 0;
        m_nGoals_OK_2 = 0;
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                switch (level[i][j]) {
                    case '$':
                        if (Mark[i][j]) {
                            box_Num++;
                            m_iBoxNum2[i][j] = box_Num;
                        }
                        m_nGoals_2++;
                        break;
                    case '*':
                        if (Mark[i][j]) {
                            box_Num++;
                            m_iBoxNum2[i][j] = box_Num;
                        }
                        m_nGoals_OK++;
                        m_nGoals++;
                        m_nGoals_OK_2++;
                        m_nGoals_2++;
                        break;
                    case '.':
                    case '+':
                        m_nGoals++;
                        break;
                }
            }
        }
    }

    private void newGame() {
        m_bBusing = true;
        m_nRow = -1;
        m_nCol = -1;
        m_nRow2 = -1;
        m_nCol2 = -1;
        m_nRow0 = -1;
        m_nCol0 = -1;
        m_nRow4 = -1;
        m_nCol4 = -1;

        if (m_lstMovUnDo != null) m_lstMovUnDo.clear();
        if (m_lstMovReDo != null) m_lstMovReDo.clear();
        if (m_lstMovUnDo2 != null) m_lstMovUnDo2.clear();
        if (m_lstMovReDo2 != null) m_lstMovReDo2.clear();
        m_lstMovUnDo = new LinkedList<Byte>();
        m_lstMovReDo = new LinkedList<Byte>();
        m_lstMovUnDo2 = new LinkedList<Byte>();
        m_lstMovReDo2 = new LinkedList<Byte>();

        try {
            if (myMaps.curMap == null) return;
            m_iBoxNum = new short[myMaps.curMap.Rows][myMaps.curMap.Cols];
            m_iBoxNum2 = new short[myMaps.curMap.Rows][myMaps.curMap.Cols];
            m_Freeze = new byte[myMaps.curMap.Rows][myMaps.curMap.Cols];
            m_cArray = new char[myMaps.curMap.Rows][myMaps.curMap.Cols];
            m_cArray0 = new char[myMaps.curMap.Rows][myMaps.curMap.Cols];
            bk_cArray = new char[myMaps.curMap.Rows][myMaps.curMap.Cols];
            m_selArray = new byte[myMaps.curMap.Rows][myMaps.curMap.Cols];
            bk_selArray = new byte[myMaps.curMap.Rows][myMaps.curMap.Cols];
            ls_bk_cArray = bk_cArray;

            mark7 = new boolean[myMaps.curMap.Rows][myMaps.curMap.Cols];
            mark8 = new boolean[myMaps.curMap.Rows][myMaps.curMap.Cols];
            mark11 = new boolean[myMaps.curMap.Rows][myMaps.curMap.Cols];
            mark12 = new boolean[myMaps.curMap.Rows][myMaps.curMap.Cols];
            mark44 = new boolean[myMaps.curMap.Rows][myMaps.curMap.Cols];
            mark41 = new byte[myMaps.curMap.Rows][myMaps.curMap.Cols];

            String[] Arr = myMaps.curMap.Map.split("\r\n|\n\r|\n|\r|\\|");
            char ch;
            for (int i = 0; i < myMaps.curMap.Rows; i++) {
                for (int j = 0; j < myMaps.curMap.Cols; j++) {
                    m_iBoxNum[i][j] = -1;
                    m_iBoxNum2[i][j] = -1;
                    ch = Arr[i].charAt(j);
                    switch (ch) {
                        case '#':
                        case '_':
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = ch;
                            break;
                        case '-':
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = ch;
                            mark8[i][j] = true;
                            break;
                        case '*':
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = ch;
                            mark7[i][j] = true;
                            break;
                        case '+':
                            m_nRow = i;
                            m_nCol = j;
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = '$';
                            break;
                        case '.':
                            m_nGoals++;
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = '$';
                            mark8[i][j] = true;
                            break;
                        case '$':
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = '.';
                            mark7[i][j] = true;
                            break;
                        case '@':
                            m_nRow = i;
                            m_nCol = j;
                            m_cArray[i][j] = ch;
                            m_cArray0[i][j] = ch;
                            bk_cArray[i][j] = '-';
                    }
                }
            }

            mark14 = null;
            mark15 = null;
            mark16 = null;
            mArray9 = null;

            mBoxNum(m_cArray, m_nRow, m_nCol);
            mPathfinder = new myPathfinder(myMaps.curMap.Rows, myMaps.curMap.Cols);

            m_iStep[0] = 0;
            m_iStep[1] = 0;
            m_Gif_Start = 0;
            m_iStep[2] = 0;
            m_iStep[3] = 0;
            m_nStep = 0;

            mMap.m_iR = m_nRow;
            mMap.m_iC = m_nCol;
            m_nRow3 = m_nRow;
            m_nCol3 = m_nCol;

            mMap.m_bBoxTo = false;
            mMap.m_bBoxTo2 = false;
            mMap.m_bManTo = false;
            mMap.m_bManTo2 = false;
            mMap.m_boxCanMove = false;
            mMap.m_boxCanMove2 = false;
            mMap.m_boxNoMoved = false;
            mMap.m_boxNoUsed = false;
            mMap.m_boxCanCome = false;
            mMap.m_boxCanCome2 = false;
            mMap.m_lGoto = false;
            mMap.m_lGoto2 = false;
            mMap.m_lParityBrightnessShade = false;
            myMaps.m_bBiaochi = false;
            myMaps.m_bBianhao = false;

            m_bBusing = false;
            m_bMoved = false;
            m_bYanshi = false;
            m_bYanshi2 = false;
            myMaps.m_StateIsRedy = false;
            m_imPort_YASS = "";
            myMaps.m_Sets[14] = 2;
            myMaps.m_Sets[5] = 2;
            m_nLastSteps = -1;
        } catch (Throwable ex) {
            myStop();
            if (myMaps.curMap != null) {
                myMaps.curMap.Title = "无效关卡";
                myMaps.curMap.Map = "--";
                myMaps.curMap.Rows = 1;
                myMaps.curMap.Cols = 2;
            }
        }
    }

    public void initMap() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
        StopMicro();
        newGame();
        if (myMaps.curMap == null) return;

        mMap.m_lChangeBK = false;
        myMaps.isRecording = false;
        mMap.d_Moves = mMap.m_PicWidth;
        myMaps.m_Sets[13] = 0;
        levelReset(false);

        mMap.initArena();

        if (mySQLite.m_SQL != null) {
            mySQLite.m_SQL.Set_L_DateTime(myMaps.curMap.Level_id);
            mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        }

        if (myMaps.mState2.size() > 0) {
            myMaps.m_State = mySQLite.m_SQL.load_State(myMaps.mState2.get(0).id);
            if (myMaps.m_State.ans.length() > 0) formatPath(myMaps.m_State.ans, false);
        } else if (myMaps.m_Sets[37] == 1 && myMaps.mState1.size() > 0) {
            Collections.sort(myMaps.mState1, new Comparator<state_Node>() {
                @Override
                public int compare(state_Node o1, state_Node o2) {
                    return o2.time.compareTo(o1.time);
                }
            });
            myMaps.m_State = mySQLite.m_SQL.load_State(myMaps.mState1.get(0).id);
            m_nLastSteps = -1;
            int len = myMaps.m_State.ans.length();
            if (len > 0) {
                formatPath(myMaps.m_State.ans, false);
                if (myMaps.m_State.time.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]";
                } else if (myMaps.m_State.time.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]";
                } else {
                    m_imPort_YASS = "";
                }
                len = m_lstMovReDo.size();
                for (int k = 0; k < len; k++) reDo1();
                m_bBusing = false;
            }

            len = myMaps.m_State.bk_ans.length();
            if (len > 0) {
                try {
                    m_nRow0 = myMaps.m_State.r;
                    m_nCol0 = myMaps.m_State.c;
                    m_nRow2 = m_nRow0;
                    m_nCol2 = m_nCol0;
                    bk_cArray[m_nRow2][m_nCol2] = (bk_cArray[m_nRow2][m_nCol2] == '-' ? '@' : '+');
                    formatPath(myMaps.m_State.bk_ans, true);
                    len = m_lstMovReDo2.size();
                    for (int k = 0; k < len; k++) reDo2();
                    m_bBusing = false;
                } catch (ArrayIndexOutOfBoundsException ex) {
                    m_nRow0 = -1;
                    m_nCol0 = -1;
                    m_nRow2 = m_nRow0;
                    m_nCol2 = m_nCol0;
                    m_lstMovReDo2.clear();
                }
            }
        }

        mTask = new AsyncCountBoxsTask(this);
        mTask.execute();

        myMaps.m_MapChange = true;
        m_bACT_ERROR = false;
        myMaps.isMacroDebug = false;
        mMap.m_lShowAnsInf = false;
    }

    /**
     * 处理「打开状态」——原版 {@code myGameView.OpenState()}（由 {@code onStart()} 里
     * {@code if (myMaps.m_StateIsRedy) OpenState();} 触发）。
     *
     * <p>入口在 {@code myStateBrow} 的上下文菜单「打开」：它把选中的状态读进
     * {@code myMaps.m_State} 并置 {@code m_StateIsRedy = true}，然后关闭自己，
     * 回到已经开着的游戏窗口。原版靠 Activity 的 {@code onStart()} 感知「回来了」；
     * PC 端没有 Activity 生命周期，改用窗口重新获得焦点（{@code windowActivated}）等价触发，
     * 见构造器里安装的 {@link WindowAdapter}。
     *
     * <p>注意 {@code solution == 0} 表示「答案」（停在开始位置），
     * 否则是「状态」（停在结束位置）——两者处理不同，不要合并。
     */
    private void OpenState() {
        m_nLastSteps = -1;
        myMaps.m_StateIsRedy = false;
        try {
            levelReset(false);  //正推复位
            myMaps.m_Sets[13] = 0;  //求解后，关闭“互动双推”模式

            int len = myMaps.m_State.ans.length();
            if (len > 0) {
                formatPath(myMaps.m_State.ans, false);
                if (myMaps.m_State.time.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]";
                } else if (myMaps.m_State.time.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]";
                } else {
                    m_imPort_YASS = "";
                }
                if (myMaps.m_State.solution == 0) {  //答案，停在开始位置；状态，停在结束位置
                    len = m_lstMovReDo.size();
                    for (int k = 0; k < len; k++) reDo1();
                    m_bBusing = false;
                } else
                    MyToast.showToast(this, "答案加载成功！", MyToast.LENGTH_SHORT);
            }

            len = myMaps.m_State.bk_ans.length();
            if (len > 0) {
                try {
                    levelReset(true);  //逆推复位
                    try {
                        if (bk_cArray[m_nRow2][m_nCol2] == '@') bk_cArray[m_nRow2][m_nCol2] = '-';
                        else if (bk_cArray[m_nRow2][m_nCol2] == '+') bk_cArray[m_nRow2][m_nCol2] = '.';
                    } catch (ArrayIndexOutOfBoundsException ex) { }
                    m_nRow0 = myMaps.m_State.r;
                    m_nCol0 = myMaps.m_State.c;
                    m_nRow2 = m_nRow0;
                    m_nCol2 = m_nCol0;
                    bk_cArray[m_nRow2][m_nCol2] = (bk_cArray[m_nRow2][m_nCol2] == '-' ? '@' : '+');
                    formatPath(myMaps.m_State.bk_ans, true);
                    len = m_lstMovReDo2.size();
                    for (int k = 0; k < len; k++) reDo2();
                    m_bBusing = false;
                } catch (ArrayIndexOutOfBoundsException ex) {
                    m_nRow0 = -1;
                    m_nCol0 = -1;
                    m_nRow2 = m_nRow0;
                    m_nCol2 = m_nCol0;
                    m_lstMovReDo2.clear();
                }
            }
            bt_BK.setChecked(false);  //强制回到正推界面
            mMap.repaint();           //原版 mMap.invalidate()
        } catch (Throwable ex) {
        }
    }

    private boolean isVisited(byte[][] m_Mrk, int mR, int mC) {
        try {
            return m_Mrk[mR][mC] > 0;
        } catch (Throwable ex) {
            return true;
        }
    }

    private boolean isWall_Box(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '#' || m_Arr[mR][mC] == '_' || m_Arr[mR][mC] == '$' || m_Arr[mR][mC] == '*')
                return true;
        } catch (Throwable ex) {
            return true;
        }
        return false;
    }

    private boolean isWall(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '#' || m_Arr[mR][mC] == '_') return true;
        } catch (Throwable ex) {
            return true;
        }
        return false;
    }

    private boolean isBox(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '$') return true;
        } catch (Throwable ex) { }
        return false;
    }

    private boolean isBox_Goal(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '*') return true;
        } catch (Throwable ex) { }
        return false;
    }

    private boolean isFloor2(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '-' || m_Arr[mR][mC] == '.' || m_Arr[mR][mC] == '@' || m_Arr[mR][mC] == '+')
                return true;
        } catch (Throwable ex) { }
        return false;
    }

    private boolean isFloor1(char[][] m_Arr, int mR, int mC) {
        try {
            if (m_Arr[mR][mC] == '-' || m_Arr[mR][mC] == '@') return true;
        } catch (Throwable ex) { }
        return false;
    }

    private boolean isLock_Count(char[][] m_Arr, short[][] m_mark, int mR, int mC, int mRow, int mCol) {
        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                mPathfinder.mark1[i][j] = false;
            }
        }

        mArray9[mR][mC] = '$';
        mPF.boxReachable(true, mR, mC, mRow, mCol);
        mArray9[mR][mC] = '-';

        boolean is_ALL_OK = true;
        int n = 0, n2 = 0;
        int i1, j1;
        int p = 0, tail = 0;
        mPathfinder.pt[0] = mRow << 16 | mCol;
        mPathfinder.mark1[mPathfinder.pt[0] >>> 16][mPathfinder.pt[0] & 0x0000ffff] = true;
        for (; p <= tail; ) {
            for (int k = 0; 4 > k; k++) {
                try {
                    i1 = (mPathfinder.pt[p] >>> 16) + dr4[k];
                    j1 = (mPathfinder.pt[p] & 0x0000ffff) + dc4[k];
                    if (!mPF.mark4[i1][j1]) continue;
                    if (m_mark[mR][mC] == m_mark[i1][j1] && !mPathfinder.mark1[i1][j1]) {
                        tail++;
                        mPathfinder.pt[tail] = i1 << 16 | j1;
                        mPathfinder.mark1[i1][j1] = true;

                        if (!mark15[i1][j1] && (m_Arr[i1][j1] == '$' || m_Arr[i1][j1] == '*'))
                            n++;
                        if (mark15[i1][j1] && m_Arr[i1][j1] == '.') n--;
                        if (is_ALL_OK && m_Arr[i1][j1] == '$') is_ALL_OK = false;
                    }
                    if (mark15[i1][j1] && !mPathfinder.mark1[i1][j1] && m_Arr[i1][j1] == '.')
                        n2++;
                } catch (Throwable ex) { }
            }
            p++;
        }

        if (is_ALL_OK) return false;
        return (n > m_mark[mR][mC] + n2);
    }

    private boolean isLock_Goal(char[][] m_Arr, int bRow, int bCol, int mRow, int mCol) {
        mArray9[bRow][bCol] = '$';
        mPF.boxReachable(false, bRow, bCol, mRow, mCol);

        int n1 = 0, n2 = 0;
        for (int r = 0; r < myMaps.curMap.Rows; r++) {
            for (int c = 0; c < myMaps.curMap.Cols; c++) {
                if (mPF.mark3[r][c]) {
                    if (m_Arr[r][c] == '$') n1++;
                    if (m_Arr[r][c] == '.' || m_Arr[r][c] == '+') n2++;
                }
            }
        }
        mArray9[bRow][bCol] = '-';
        return n1 > n2;
    }

    private boolean isLock_Net2(char[][] m_Arr, int b_new_Row, int b_new_Col) {
        int mRows = m_Arr.length, mCols = m_Arr[0].length;

        for (int i = 0; i < mRows; i++) {
            for (int j = 0; j < mCols; j++) {
                mark41[i][j] = 0;
            }
        }

        Queue<Integer> Q = new LinkedList<Integer>();
        int P, r = b_new_Row, c = b_new_Col, r1, c1, r2, c2;

        Q.offer(r << 16 | c);
        mark41[r][c] = 1;
        boolean flg = false;

        while (!Q.isEmpty()) {
            P = Q.poll();
            r = P >>> 16;
            c = P & 0x0000ffff;

            flg |= (!isBox_Goal(m_Arr, r, c));

            for (int k = 0; k < 4; k++) {
                r1 = r + dr4[k];
                c1 = c + dc4[k];
                r2 = r + dr4[k] * 2;
                c2 = c + dc4[k] * 2;

                if (isVisited(mark41, r2, c2) || isWall(m_Arr, r2, c2) || isWall(m_Arr, r1, c1)) {
                    continue;
                } else if (isFloor2(m_Arr, r2, c2)) {
                    return false;
                } else {
                    Q.offer(r2 << 16 | c2);
                    mark41[r2][c2] = 1;
                }
            }
        }
        return flg;
    }

    public void m_Net_Inf(char[][] m_Arr, int b_Row, int b_Col) {
        int mRows = m_Arr.length, mCols = m_Arr[0].length;
        int P, r = b_Row, c = b_Col, r1, c1, r2, c2;

        for (int i = 0; i < mRows; i++) {
            for (int j = 0; j < mCols; j++) {
                mark41[i][j] = 0;
            }
        }

        Queue<Integer> Q = new LinkedList<Integer>();
        Q.offer(r << 16 | c);
        mark41[r][c] = 1;

        while (!Q.isEmpty()) {
            P = Q.poll();
            r = P >>> 16;
            c = P & 0x0000ffff;

            for (int k = 0; k < 4; k++) {
                r1 = r + dr4[k];
                c1 = c + dc4[k];
                r2 = r + dr4[k] * 2;
                c2 = c + dc4[k] * 2;

                if (isVisited(mark41, r2, c2) || isWall(m_Arr, r2, c2) || isWall(m_Arr, r1, c1)) {
                    continue;
                } else if (isFloor1(m_Arr, r2, c2) || isBox(m_Arr, r2, c2)) {
                    mark41[r2][c2] = 2;
                } else {
                    Q.offer(r2 << 16 | c2);
                    mark41[r2][c2] = 1;
                }
            }
        }
    }

    private boolean myLock(int bRow, int bCol) {
        if (m_iR9 > -1) {
            if (mArray9 != null && isLock_Goal(m_cArray, bRow, bCol, m_nRow, m_nCol)) {
                return true;
            } else if (freezeDeadlock != null && freezeDeadlock.isDeadlock(bRow, bCol)) {
                return true;
            } else if (closedDiagonalLock != null && closedDiagonalLock.isDeadlock(bRow * myMaps.curMap.Cols + bCol)) {
                return true;
            }
        }
        return false;
    }

    private boolean myLock2(int bRow, int bCol) {
        if (m_iR10 > -1 && mark14 != null) {
            if (mArray9 != null && isLock_Count(bk_cArray, mark14, bRow, bCol, m_nRow2, m_nCol2)) {
                return true;
            } else if (isLock_Net2(bk_cArray, bRow, bCol)) {
                return true;
            }
        }
        return false;
    }

    private void mySetProgressBar() {
        m_nLastSteps = -1;
        if (bt_BK.isChecked()) {
            int len = m_lstMovUnDo2.size();
            int len2 = m_lstMovUnDo2.size() + m_lstMovReDo2.size();
            if (len2 > 0) {
                mMap.curMoves2 = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
                mMap.m_lGoto2 = true;
            } else {
                MyToast.showToast(this, "尚无动作可用！", MyToast.LENGTH_SHORT);
            }
        } else {
            int len = m_lstMovUnDo.size();
            int len2 = m_lstMovUnDo.size() + m_lstMovReDo.size();
            if (len2 > 0) {
                mMap.curMoves = (int) (((double) len / len2) * (mMap.stRight - mMap.stLeft));
                mMap.m_lGoto = true;
            } else {
                MyToast.showToast(this, "尚无动作可用！", MyToast.LENGTH_SHORT);
            }
        }
        mMap.invalidate();
    }

    private void skinList() {
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "皮肤/");
        myMaps.mFile_List1.clear();
        myMaps.mFile_List1.add("默认皮肤");
        if (!targetDir.exists()) targetDir.mkdirs();
        else {
            String[] filelist = targetDir.list();
            if (filelist != null) {
                Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
                for (String s : filelist) {
                    int dot = s.lastIndexOf('.');
                    if (dot > -1 && dot < s.length()) {
                        String prefix = s.substring(dot + 1);
                        if (prefix.equalsIgnoreCase("png"))
                            myMaps.mFile_List1.add(s);
                    }
                }
            }
        }
    }

    private void bkPicList() {
        File targetDir = new File(myMaps.sRoot + myMaps.sPath + "背景/");
        myMaps.mFile_List2.clear();
        myMaps.mFile_List2.add("使用背景色");
        if (!targetDir.exists()) targetDir.mkdirs();
        else {
            String[] filelist = targetDir.list();
            if (filelist != null) {
                Arrays.sort(filelist, String.CASE_INSENSITIVE_ORDER);
                for (String s : filelist) {
                    int dot = s.lastIndexOf('.');
                    if (dot > -1 && dot < s.length()) {
                        String prefix = s.substring(dot + 1);
                        if (prefix.equalsIgnoreCase("jpg") || prefix.equalsIgnoreCase("bmp") || prefix.equalsIgnoreCase("png"))
                            myMaps.mFile_List2.add(s);
                    }
                }
            }
        }
    }

    public boolean myClearance() {
        return (myMaps.m_Sets[13] == 0 || m_iStep[2] <= 0) && m_nGoals_OK == m_nGoals && m_iStep[0] > 0;
    }

    public boolean myClearance2() {
        return (myMaps.m_Sets[13] == 0 || m_iStep[0] <= 0) && m_nGoals_OK_2 == m_nGoals_2 && m_iStep[2] > 0 &&
                mPathfinder.manTo2(true, bk_cArray, -1, -1, m_nRow2, m_nCol2, m_nRow3, m_nCol3);
    }

    public boolean myMeet() {
        boolean flg = true;
        if (m_iStep[2] < 1 || m_bYanshi || m_bYanshi2)
            return false;

        for (int i = 0; i < m_cArray.length; i++) {
            for (int j = 0; j < m_cArray[0].length; j++) {
                if ((m_cArray[i][j] == '$' || m_cArray[i][j] == '*') && bk_cArray[i][j] != '$' && bk_cArray[i][j] != '*') {
                    return false;
                }
            }
        }
        if (m_nRow2 != m_nRow || m_nCol2 != m_nCol) {
            if (bt_BK.isChecked())
                flg = mPathfinder.manTo2(true, bk_cArray, -1, -1, m_nRow2, m_nCol2, m_nRow, m_nCol);
            else
                flg = mPathfinder.manTo2(false, m_cArray, -1, -1, m_nRow, m_nCol, m_nRow2, m_nCol2);
        }
        if (flg) {
            m_nStep = 0;
            if (bt_BK.isChecked()) {
                mMap.m_iR = m_nRow2;
                mMap.m_iC = m_nCol2;
            } else {
                mMap.m_iR = m_nRow;
                mMap.m_iC = m_nCol;
            }
            myMaps.m_Sets[13] = 0;
        }
        return flg;
    }

    public void DoEvent(int act) {
        mMap.m_bBoxTo = false;
        mMap.m_bBoxTo2 = false;
        mMap.m_bManTo = false;
        mMap.m_bManTo2 = false;
        mMap.m_boxCanMove = false;
        mMap.m_boxCanMove2 = false;
        m_bNetLock = false;
        mMap.invalidate();
        switch (act) {
            case 0:
                if (myMaps.m_Sets[13] == 0) {
                    myMaps.m_Sets[13] = 1;
                } else {
                    myMaps.m_Sets[13] = 0;
                }
                mMap.invalidate();
                break;
            case 1:
                mMap.Box_Row0 = -1;
                m_nLastSteps = m_lstMovUnDo.size();
                UpData1(1);
                break;
            case 2:
                UpData3(1);
                break;
            case 3:
                bt_UnDo.onKeyLongPress(0, null);
                break;
            case 4:
                bt_ReDo.onKeyLongPress(0, null);
                break;
            case 5:
                bt_UnDo.setChecked(!bt_UnDo.isChecked());
                break;
            case 6:
                bt_ReDo.setChecked(!bt_ReDo.isChecked());
                break;
            case 7:
                StopMicro();
                if (myMaps.m_lstMaps != null) {
                    int n1 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
                    if (n1 > 0) {
                        if (m_bMoved) {
                            int r = JOptionPane.showConfirmDialog(this, "有状态未保存，坚持更换吗？", "更换关卡", JOptionPane.YES_NO_OPTION);
                            if (r == JOptionPane.YES_OPTION) myPre(n1 - 1);
                        } else myPre(n1 - 1);
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                }
                break;
            case 8:
                StopMicro();
                if (myMaps.m_lstMaps != null) {
                    int n2 = myMaps.m_lstMaps.indexOf(myMaps.curMap);
                    if (n2 >= 0 && n2 + 1 < myMaps.m_lstMaps.size()) {
                        if (m_bMoved) {
                            int r = JOptionPane.showConfirmDialog(this, "有状态未保存，坚持更换吗？", "更换关卡", JOptionPane.YES_NO_OPTION);
                            if (r == JOptionPane.YES_OPTION) myNext(n2 + 1);
                        } else myNext(n2 + 1);
                    } else {
                        MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                    }
                }
                break;
            case 9:
                setACT(true);
                myMaps.m_ActionIsRedy = false;
                if (bt_BK.isChecked())
                    myMaps.m_nRecording_Bggin2 = m_lstMovUnDo2.size();
                else
                    myMaps.m_nRecording_Bggin = m_lstMovUnDo.size();
                MyToast.showToast(this, "已送入剪切板与导入缓存", MyToast.LENGTH_SHORT);
                break;
            case 10:
                if (myMaps.isMacroDebug) {
                    int r = JOptionPane.showConfirmDialog(this, "关卡回到该“宏”打开前的状态？", "关闭调试", JOptionPane.YES_NO_OPTION);
                    if (r == JOptionPane.YES_OPTION) {
                        levelReset(false);
                    }
                    myMaps.isMacroDebug = false;
                    mMap.invalidate();
                } else {
                    StopMicro();
                    mLoad_Do_Macro();
                }
                break;
            case 11:
                if (myMaps.isMacroDebug) {
                    MyToast.showToast(this, "“宏”调试时，不支持此功能！", MyToast.LENGTH_SHORT);
                } else {
                    if (bt_BK.isChecked()) {
                        if (mMap.m_lGoto2) mMap.m_lGoto2 = false;
                        else mySetProgressBar();
                    } else {
                        if (mMap.m_lGoto) mMap.m_lGoto = false;
                        else mySetProgressBar();
                    }
                }
                break;
            case 12:
                if (myMaps.m_Sets[38] == 1) {
                    myMaps.m_Sets[38] = 0;
                    MyToast.showToast(this, "奇偶格模式 - 关", MyToast.LENGTH_SHORT);
                } else {
                    myMaps.m_Sets[38] = 1;
                    MyToast.showToast(this, "奇偶格模式 - 开", MyToast.LENGTH_SHORT);
                }
                break;
        }
    }

    private void setACT(boolean flg) {
        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        try {
            char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
            Byte t;
            Iterator<Byte> myItr;
            if (bt_BK.isChecked()) {
                if (!m_lstMovUnDo2.isEmpty()) {
                    myItr = m_lstMovUnDo2.iterator();
                    while (myItr.hasNext()) {
                        t = myItr.next();
                        s1.append(Move[t - 1]);
                    }
                }
                if (myMaps.isRecording && flg) {
                    if (!m_lstMovUnDo2.isEmpty() && myMaps.m_nRecording_Bggin2 < m_lstMovUnDo2.size()) {
                        myItr = m_lstMovUnDo2.iterator();
                        for (int k = 0; k < myMaps.m_nRecording_Bggin2; k++) {
                            if (myItr.hasNext()) myItr.next();
                            else break;
                        }
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                } else {
                    if (!m_lstMovReDo2.isEmpty()) {
                        myItr = m_lstMovReDo2.descendingIterator();
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                }
            } else {
                if (!m_lstMovUnDo.isEmpty()) {
                    myItr = m_lstMovUnDo.iterator();
                    while (myItr.hasNext()) {
                        t = myItr.next();
                        s1.append(Move[t - 1]);
                    }
                }
                if (myMaps.isRecording && flg) {
                    if (!m_lstMovUnDo.isEmpty() && myMaps.m_nRecording_Bggin < m_lstMovUnDo.size()) {
                        myItr = m_lstMovUnDo.iterator();
                        for (int k = 0; k < myMaps.m_nRecording_Bggin; k++) {
                            if (myItr.hasNext()) myItr.next();
                            else break;
                        }
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                } else {
                    if (!m_lstMovReDo.isEmpty()) {
                        myItr = m_lstMovReDo.descendingIterator();
                        while (myItr.hasNext()) {
                            t = myItr.next();
                            s2.append(Move[t - 1]);
                        }
                    }
                }
            }
            saveAct("act1", s1.toString());
            saveAct("act2", s2.toString());
            myMaps.saveClipper(s1.toString() + (s2.length() > 0 ? "\n" + s2.toString() : ""));
        } catch (Throwable ex) { }
    }

    private int getStep(LinkedList<Byte> m_lstMove) {
        if (m_nGoals == 1) return m_lstMove.size();
        int len = m_lstMove.size();
        int[] boxRC = {1000, 1000};
        int i = 0, j = 0;
        byte mDir;
        int n = 0, k = 0;
        boolean flg = false;
        Iterator<Byte> descItr = m_lstMove.descendingIterator();
        while (descItr.hasNext()) {
            mDir = descItr.next();
            k++;
            switch (mDir) {
                case 1: j--; break;
                case 2: i--; break;
                case 3: j++; break;
                case 4: i++; break;
                case 5:
                    j--;
                    if (boxRC[0] != i || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j - 1;
                    break;
                case 6:
                    i--;
                    if (boxRC[0] != i || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i - 1;
                    boxRC[1] = j;
                    break;
                case 7:
                    j++;
                    if (boxRC[0] != i || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j + 1;
                    break;
                case 8:
                    i++;
                    if (boxRC[0] != i || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i + 1;
                    boxRC[1] = j;
                    break;
            }
        }
        if (flg) return n;
        return len;
    }

    private int getStep2(LinkedList<Byte> m_lstMove) {
        if (m_nGoals == 1) return m_lstMove.size();
        int len = m_lstMove.size();
        int[] boxRC = {1000, 1000};
        int i = 0, j = 0;
        byte mDir;
        int n = 0, k = 0;
        boolean flg = false;
        Iterator<Byte> descItr = m_lstMove.descendingIterator();
        while (descItr.hasNext()) {
            mDir = descItr.next();
            k++;
            switch (mDir) {
                case 1: j--; break;
                case 2: i--; break;
                case 3: j++; break;
                case 4: i++; break;
                case 5:
                    if (boxRC[0] != i || boxRC[1] != j + 1) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j;
                    j--;
                    break;
                case 6:
                    if (boxRC[0] != i + 1 || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j;
                    i--;
                    break;
                case 7:
                    if (boxRC[0] != i || boxRC[1] != j - 1) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j;
                    j++;
                    break;
                case 8:
                    if (boxRC[0] != i - 1 || boxRC[1] != j) {
                        if (flg) return n;
                        flg = true;
                    }
                    n = k;
                    boxRC[0] = i;
                    boxRC[1] = j;
                    i++;
                    break;
            }
        }
        if (flg) return n;
        return len;
    }

    public void zhengniHE2() {
        if (m_nRow2 != m_nRow3 || m_nCol2 != m_nCol3)
            FindPath(m_nRow3, m_nCol3, true);

        int len = m_lstMovReDo2.size();
        for (int k = 0; k < len; k++) reDo2();
        m_bBusing = false;

        byte t;
        int s = 0;
        len = m_lstMovUnDo2.size();
        for (int k = 0; k < len; k++) {
            t = m_lstMovUnDo2.get(k);
            if (t < 5) s++;
            else break;
        }

        len = m_lstMovUnDo.size();
        for (int k = 0; k < len; k++) unDo1();
        m_lstMovReDo.clear();
        m_lstMovUnDo.clear();
        m_iStep[0] = 0;
        m_iStep[1] = 0;

        Byte mDir;
        Iterator<Byte> myItr = m_lstMovUnDo2.iterator();
        for (int k = 0; k < s; k++) {
            if (myItr.hasNext()) myItr.next();
            else break;
        }
        while (myItr.hasNext()) {
            mDir = myItr.next();
            switch (mDir) {
                case -1:
                case 1: m_lstMovReDo.offer((byte) 3); break;
                case -2:
                case 2: m_lstMovReDo.offer((byte) 4); break;
                case -3:
                case 3: m_lstMovReDo.offer((byte) 1); break;
                case -4:
                case 4: m_lstMovReDo.offer((byte) 2); break;
                case -5:
                case 5: m_lstMovReDo.offer((byte) 7); break;
                case -6:
                case 6: m_lstMovReDo.offer((byte) 8); break;
                case -7:
                case 7: m_lstMovReDo.offer((byte) 5); break;
                case -8:
                case 8: m_lstMovReDo.offer((byte) 6); break;
            }
        }
        mMap.invalidate();
    }

    public void zhengniHE() {
        if (m_nRow2 != m_nRow || m_nCol2 != m_nCol) {
            FindPath(m_nRow2, m_nCol2, false);
        } else {
            m_lstMovReDo.clear();
        }

        byte t;
        int s = 0;
        int len = m_lstMovUnDo2.size();
        for (int k = 0; k < len; k++) {
            t = m_lstMovUnDo2.get(k);
            if (t < 5) {
                s++;
            } else break;
        }

        Byte mDir;
        len = m_lstMovUnDo2.size();
        Iterator<Byte> myItr = m_lstMovUnDo2.descendingIterator();
        while (myItr.hasNext()) {
            mDir = myItr.next();
            if (--len < s) break;
            switch (mDir) {
                case -1:
                case 1: m_lstMovReDo.offerFirst((byte) 3); break;
                case -2:
                case 2: m_lstMovReDo.offerFirst((byte) 4); break;
                case -3:
                case 3: m_lstMovReDo.offerFirst((byte) 1); break;
                case -4:
                case 4: m_lstMovReDo.offerFirst((byte) 2); break;
                case -5:
                case 5: m_lstMovReDo.offerFirst((byte) 7); break;
                case -6:
                case 6: m_lstMovReDo.offerFirst((byte) 8); break;
                case -7:
                case 7: m_lstMovReDo.offerFirst((byte) 5); break;
                case -8:
                case 8: m_lstMovReDo.offerFirst((byte) 6); break;
            }
        }
        mMap.invalidate();
    }

    private void saveToFile(final String str, String fn) {
        try {
            FileOutputStream fout = new FileOutputStream(myMaps.sRoot + myMaps.sPath + fn);
            fout.write(str.getBytes());
            fout.flush();
            fout.close();
            MyToast.showToast(this, "保存成功！", MyToast.LENGTH_SHORT);
        } catch (Exception e) {
            MyToast.showToast(this, "出错了，保存失败！", MyToast.LENGTH_SHORT);
        }
    }

    public void saveAns(int m_Solution) {
        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        byte t;
        Iterator<Byte> myItr;

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        if (!m_lstMovUnDo.isEmpty()) {
            myItr = m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }

        if (m_Solution == 0 && !m_lstMovUnDo2.isEmpty()) {
            myItr = m_lstMovUnDo2.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s2.append(Move[t - 1]);
            }
        }

        if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
            m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
            m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else {
            m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }

        if (m_Solution == 0 && (m_lstMovUnDo.size() > myMaps.m_nMaxSteps || m_lstMovUnDo2.size() > myMaps.m_nMaxSteps) || m_Solution == 1 && m_lstMovUnDo.size() > myMaps.m_nMaxSteps) {
            final StringBuilder str = new StringBuilder();
            str.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title).append("\nAuthor: ").append(myMaps.curMap.Author);
            str.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:\n");

            if (m_Solution == 0) {
                str.append(s1).append("\n[").append(m_nRow0).append(", ").append(m_nCol0).append("]").append(s2);
            } else {
                str.append("Solution (moves ").append(m_iStep[1]).append(", pushes ").append(m_iStep[0]).append(": ");
                str.append(m_imPort_YASS);
                str.append(s1);
            }

            final String my_Name = "导入/" + myMaps.sFile + "_" + (myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1);
            File file;
            int n = 1;
            file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            while (file.exists()) {
                n++;
                file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            }
            final int finalN = n;
            int ret = JOptionPane.showConfirmDialog(this, "答案或状态太长，保存到文档！\n" + my_Name + "(" + n + ").txt", "注意", JOptionPane.OK_CANCEL_OPTION);
            if (ret == JOptionPane.OK_OPTION) {
                saveToFile(str.toString(), my_Name + "(" + finalN + ").txt");
            }
        } else {
            long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                    m_Solution,
                    m_iStep[1],
                    m_iStep[0],
                    m_Solution == 0 ? m_iStep[3] : 0,
                    m_Solution == 0 ? m_iStep[2] : 0,
                    m_Solution == 0 ? m_nRow0 : -1,
                    m_Solution == 0 ? m_nCol0 : -1,
                    s1.toString(),
                    s2.toString(),
                    myMaps.curMap.key,
                    m_Solution == 0 ? -1 : myMaps.curMap.L_CRC_Num,
                    m_Solution == 0 ? "" : myMaps.curMap.Map0,
                    m_imPort_YASS);

            m_bMoved = false;
            if (hh > 0) {
                if (m_Solution == 1) {
                    myMaps.curMap.Solved |= (m_Solution == 1);
                    MyToast.showToast(this, "答案已保存！", MyToast.LENGTH_LONG);
                    state_Node ans = new state_Node();
                    ans.id = hh;
                    ans.pid = myMaps.curMap.Level_id;
                    ans.pkey = myMaps.curMap.key;
                    ans.moves = m_iStep[1];
                    ans.pushs = m_iStep[0];
                    ans.inf = "移动: " + m_iStep[1] + ", 推动: " + m_iStep[0];
                    ans.time = m_imPort_YASS;
                    myMaps.mState2.add(ans);
                    mMap.invalidate();
                } else {
                    MyToast.showToast(this, "状态已保存！", MyToast.LENGTH_LONG);
                }
            } else if (hh == 0) {
                if (m_Solution == 1) {
                    myMaps.curMap.Solved |= (m_Solution == 1);
                    MyToast.showToast(this, "答案有重复，未再保存！\n移动：" + (m_iStep[1] + m_lstMovReDo.size()) + "，推动：" + (m_iStep[0] + m_iStep[2]), MyToast.LENGTH_LONG);
                } else {
                    MyToast.showToast(this, "有重复，已重排！", MyToast.LENGTH_LONG);
                }
            } else {
                myMaps.curMap.Solved |= (m_Solution == 1);
                MyToast.showToast(this, "出错了，保存失败！", MyToast.LENGTH_LONG);
            }
        }
    }

    public void saveAns2() {
        StringBuilder s1 = new StringBuilder();
        byte t;
        Iterator<Byte> myItr;

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        if (!m_lstMovUnDo.isEmpty()) {
            myItr = m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }
        if (!m_lstMovReDo.isEmpty()) {
            myItr = m_lstMovReDo.descendingIterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s1.append(Move[t - 1]);
            }
        }

        if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
            m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
            m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        } else {
            m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }

        if (m_lstMovUnDo.size() + m_lstMovReDo.size() > myMaps.m_nMaxSteps) {
            final StringBuilder str = new StringBuilder();
            str.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title).append("\nAuthor: ").append(myMaps.curMap.Author);
            str.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:\n");
            str.append("Solution (moves ").append(m_iStep[1] + m_lstMovReDo.size()).append(", pushes ").append(m_iStep[0] + m_iStep[2]).append(": ");
            str.append(m_imPort_YASS);
            str.append(s1);

            File targetDir = new File(myMaps.sRoot + myMaps.sPath + "超长答案/");
            if (!targetDir.exists()) targetDir.mkdirs();

            final String my_Name = "超长答案/" + myMaps.sFile + "_" + (myMaps.m_lstMaps.indexOf(myMaps.curMap) + 1);
            File file;
            int n = 1;
            file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            while (file.exists()) {
                n++;
                file = new File(myMaps.sRoot + myMaps.sPath + my_Name + "(" + n + ").txt");
            }
            final int finalN = n;
            int ret = JOptionPane.showConfirmDialog(this, "答案太长，保存到文档！\n" + my_Name + "(" + n + ").txt", "注意", JOptionPane.OK_CANCEL_OPTION);
            if (ret == JOptionPane.OK_OPTION) {
                saveToFile(str.toString(), my_Name + "(" + finalN + ").txt");
            }
        } else {
            long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                    myMaps.curMap.Level_id > 0 ? 1 : 0,
                    m_iStep[1] + m_lstMovReDo.size(),
                    m_iStep[0] + m_iStep[2],
                    0,
                    0,
                    -1,
                    -1,
                    s1.toString(),
                    "",
                    myMaps.curMap.key,
                    myMaps.curMap.L_CRC_Num,
                    myMaps.curMap.Map0,
                    m_imPort_YASS);

            myMaps.curMap.Solved = true;
            m_bMoved = false;
            if (myMaps.curMap.Level_id > 0) {
                if (hh > 0) {
                    state_Node ans = new state_Node();
                    ans.id = hh;
                    ans.pid = myMaps.curMap.Level_id;
                    ans.pkey = myMaps.curMap.key;
                    ans.inf = "移动: " + m_iStep[1] + ", 推动: " + m_iStep[0];
                    ans.time = m_imPort_YASS;
                    myMaps.mState2.add(ans);
                    JOptionPane.showMessageDialog(this, "答案成功保存！\n(可用进退键观看通关演示)", "正逆相合或通关！", JOptionPane.INFORMATION_MESSAGE);
                } else if (hh == 0) {
                    JOptionPane.showMessageDialog(this, "答案有重复！\n移动：" + (m_iStep[1] + m_lstMovReDo.size()) + "，推动：" + (m_iStep[0] + m_iStep[2]) + "，\n本次未做保存！\n(可用进退键观看通关演示)", "正逆相合或通关！", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "DB写错误，答案未能保存，请利用剪切板手动保存到其它地方！\n(可用进退键观看通关演示)", "正逆相合或通关！", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    /**
     * 原版 {@code myGameView} 的选项菜单 —— {@code res/menu/player.xml} 的 13 项，
     * 由底栏「更多」按钮 {@code openOptionsMenu()} 弹出。
     *
     * <p>原版 {@code myGameView} 是 {@code FEATURE_NO_TITLE} + {@code FLAG_FULLSCREEN}，
     * **没有 ActionBar**，所以这里不用 {@code myActionBar}；菜单外壳与 ActionBar 溢出菜单
     * 共用同一套 {@code popup_menu_holo_dark} 样式，因此走 {@link HoloPopupMenu}。
     *
     * <p>条目顺序严格按 {@code player.xml}：设置… / 开关选项… / 重新开始 / 退至首 / 进至尾 /
     * 导出… / 导入… / YASS求解 / 打开状态… / 保存状态 / 关于 / 操作说明 / 退出。
     * （{@code player.xml} 里「Solver求解」整段被 {@code <!-- -->} 注释掉，原版不存在。）
     * {@code YASS求解} 已接（阶段 F）：原版那一步是跨应用 Intent，PC 无等价机制，
     * 等价于真机未装求解器 —— 见 {@link #onYassSolver()}。
     */
    public void openOptionsMenu() {
        JPopupMenu menu = HoloPopupMenu.create();

        HoloPopupMenu.addItem(menu, "设置...", this::showSetup1Dialog);
        HoloPopupMenu.addItem(menu, "开关选项...", this::showSetup2Dialog);
        HoloPopupMenu.addItem(menu, "重新开始", this::onReStart);
        HoloPopupMenu.addItem(menu, "退至首", this::onHome);
        HoloPopupMenu.addItem(menu, "进至尾", this::onEnd);
        HoloPopupMenu.addItem(menu, "导出...", this::onExport);
        HoloPopupMenu.addItem(menu, "导入...", this::onImport);
        HoloPopupMenu.addItem(menu, "YASS求解", this::onYassSolver);
        HoloPopupMenu.addItem(menu, "打开状态...", this::onOpenState);
        HoloPopupMenu.addItem(menu, "保存状态", this::onSaveState);
        // 原版 player_about → myAbout2（关卡描述），不是 myAbout。
        HoloPopupMenu.addItem(menu, "关于", () -> new myAbout2(this, myMaps.curMap).setVisible(true));
        // 原版 player_help：Bundle 传 m_Num = 1。
        HoloPopupMenu.addItem(menu, "操作说明", () -> new Help(1).setVisible(true));
        HoloPopupMenu.addItem(menu, "退出", this::handleExit);

        optionsMenu = menu;
        if (bt_More.isShowing()) menu.show(bt_More, 0, -menu.getPreferredSize().height);
    }

    /** 原版 {@code R.id.player_ReStart}「重新开始」。 */
    void onReStart() {
        int ret = JOptionPane.showConfirmDialog(this, "重新开始，确定吗？", "重新开始", JOptionPane.YES_NO_OPTION);
        if (ret != JOptionPane.YES_OPTION) return;
        mMap.d_Moves = mMap.m_PicWidth;
        if (bt_BK.isChecked()) {
            MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
            levelReset(true);
        } else {
            MyToast.showToast(this, "重新开始！", MyToast.LENGTH_SHORT);
            levelReset(false);
            if (myMaps.isMacroDebug) {
                mMap.myMacro.clear();
                mMap.myMacro.add(0);
            }
        }
        mMap.curMoves = 0;
        mMap.invalidate();
    }

    /** 原版 {@code R.id.player_Home}「退至首」。 */
    void onHome() {
        m_bYanshi = false;
        m_bYanshi2 = false;
        if (bt_BK.isChecked()) {
            if (m_lstMovUnDo2.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                m_bBusing = false;
            } else {
                m_nStep = m_lstMovUnDo2.size();
                UpData4(1);
            }
        } else {
            if (m_lstMovUnDo.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                m_bBusing = false;
            } else {
                m_nStep = m_lstMovUnDo.size();
                UpData2(1);
            }
        }
    }

    /** 原版 {@code R.id.player_End}「进至尾」。 */
    void onEnd() {
        m_bYanshi = false;
        m_bYanshi2 = false;
        if (bt_BK.isChecked()) {
            if (m_lstMovReDo2.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                m_bBusing = false;
            } else {
                if (m_lstMovUnDo2.isEmpty()) goHome();
                m_nStep = m_lstMovReDo2.size();
                UpData3(1);
            }
        } else {
            if (m_lstMovReDo.isEmpty()) {
                MyToast.showToast(this, "没有了！", MyToast.LENGTH_SHORT);
                m_bBusing = false;
            } else {
                m_nStep = m_lstMovReDo.size();
                mMap.Box_Row0 = -1;
                m_nLastSteps = -1;
                UpData1(1);
            }
        }
    }

    /** 原版 {@code R.id.player_save}「保存状态」。 */
    void onSaveState() {
        if (m_lstMovUnDo.size() > 0 || m_lstMovUnDo2.size() > 0) {
            if ((myMaps.m_Sets[13] == 0 || m_iStep[2] <= 0) && m_nGoals_OK == m_nGoals
                    && m_iStep[0] > 0 && myMaps.curMap.Level_id > 0)
                saveAns(1);
            else
                saveAns(0);
        } else {
            MyToast.showToast(this, "没什么可保存的！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code R.id.player_Yass_Solver}「YASS求解」。
     * 逆推时没有此功能（原版 {@code mySolution()} 只接正推现场）。
     */
    void onYassSolver() {
        if (!bt_BK.isChecked()) {
            mySolution(0);   // YASS求解
        } else {
            MyToast.showToast(this, "逆推时，无此功能！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code myGameView.mySolution(int XYZ)}：先把当前状态自动存一次（自动查重），
     * 再 {@code startActivityForResult()} 把 {@code LEVEL} 交给第三方「YASS」求解器。
     *
     * <p><b>PC 上的落地方式</b>：原版这最后一步是 Android 的<b>跨应用 Intent</b> ——
     * {@code ComponentName("net.sourceforge.sokobanyasc.joriswit.yass", "yass.YASSActivity")}、
     * {@code action = "nl.joriswit.sokosolver.SOLVE"}、{@code extra = "LEVEL"}。
     * PC 没有等价的跨应用机制，等价于「设备上没装求解器」，所以这里直接抛出、
     * 落到与真机相同的 catch 分支：Toast「没有找到求解器！」。
     * <b>前面那段「自动保存状态」是真逻辑，照原版完整保留。</b>
     *
     * <p>求解成功后答案回流的路径见 {@link #onSolverResult(String, boolean)}。
     *
     * @param XYZ 0 = YASS 求解；1 = Festival 求解（原版该分支已被注释掉，PC 不接）
     */
    void mySolution(int XYZ) {
        try {
            // 拼接正逆推动作，进行查重和保存
            char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
            StringBuilder s1 = new StringBuilder();
            StringBuilder s2 = new StringBuilder();
            if (!m_lstMovUnDo.isEmpty()) {
                for (Byte t : m_lstMovUnDo) {
                    s1.append(Move[t - 1]);
                }
            }
            if (!m_lstMovUnDo2.isEmpty()) {
                s2.append("[").append(m_nRow0).append(", ").append(m_nCol0).append("]");
                for (Byte t : m_lstMovUnDo2) {
                    s2.append(Move[t - 1]);
                }
            }

            // 自动保存一下当前状态（自动查重），避免 yass 闪退造成丢失
            if ((m_iStep[1] > 0 || m_iStep[3] > 0)
                    && mySQLite.m_SQL.count_S(myMaps.curMap.Level_id, m_iStep[1], m_iStep[0],
                    m_iStep[3], m_iStep[2], m_nRow0, m_nCol0,
                    myMaps.getCRC32(s1.toString() + s2.toString())) <= 0) {
                if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("yass") >= 0) {
                    m_imPort_YASS = "[YASS]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                } else if (m_imPort_YASS != null && m_imPort_YASS.toLowerCase().indexOf("导入") >= 0) {
                    m_imPort_YASS = "[导入]" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                } else {
                    m_imPort_YASS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                }

                long hh = mySQLite.m_SQL.add_S(myMaps.curMap.Level_id,
                        3,
                        m_iStep[1],
                        m_iStep[0],
                        m_iStep[3],
                        m_iStep[2],
                        m_nRow0,
                        m_nCol0,
                        s1.toString(),
                        s2.toString(),
                        myMaps.curMap.key,
                        -1,
                        "",
                        m_imPort_YASS);

                m_bMoved = false;
                if (hh > 0) {
                    MyToast.showToast(this, "状态已保存！", MyToast.LENGTH_SHORT);
                }
            }

            // 原版：new Intent(ACTION_MAIN) + addCategory(CATEGORY_LAUNCHER)
            //       + setComponent(YASS 的 Activity) + setAction("nl.joriswit.sokosolver.SOLVE")
            //       + putExtra("LEVEL", myMaps.getLocale(m_cArray))
            //       + startActivityForResult(intent3, 1)
            // PC 无跨应用 Intent → 恒抛，等价于真机未安装 YASS。
            throw new UnsupportedOperationException(
                    "PC 上没有 YASS 求解器（原版是跨应用 Intent，非外部进程）");
        } catch (Exception e) {
            MyToast.showToast(this, "没有找到求解器！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code myGameView.onActivityResult(requestCode == 1, ...)} 的等价物：
     * 求解器把答案放在 {@code extra "SOLUTION"} 里回传。
     *
     * <p>PC 上目前没有求解器会回调到这里，但这条回路按原版语义保留：
     * 成功 → {@code formatPath(solution, false)} + Toast「答案已经载！」+ {@code m_imPort_YASS = "[YASS]"}；
     * 失败 → Toast「未能完成求解！」。
     *
     * @param solution 求解器回传的动作串（{@code RESULT_OK} 时才有意义）
     * @param ok       {@code resultCode == RESULT_OK}
     */
    void onSolverResult(String solution, boolean ok) {
        if (ok) {
            formatPath(solution, false);
            MyToast.showToast(this, "答案已经载！", MyToast.LENGTH_SHORT);
            m_imPort_YASS = "[YASS]";
        } else {
            MyToast.showToast(this, "未能完成求解！", MyToast.LENGTH_SHORT);
        }
    }

    /**
     * 原版 {@code R.id.player_IN}「导入」：进入 {@link myActGMView}（动作管理）录入动作。
     * 原版还把 {@code LOCAL}（{@code myMaps.getLocale(m_cArray)}）塞进 Bundle，
     * 但 {@code myActGMView} 里接收那行是**注释掉的** —— 所以不用传。
     */
    void onImport() {
        prepareImport();
        new myActGMView(this, bt_BK.isChecked()).setVisible(true);
    }

    /** 「导入」在开窗之前的簿记（原版 {@code player_IN} 分支的前半段）。 */
    void prepareImport() {
        setACT(false);                                  // 非录制模式
        myMaps.m_ActionIsRedy = false;
        if (bt_BK.isChecked())
            myMaps.m_nRecording_Bggin2 = m_lstMovUnDo2.size();  // 逆推录制起始点
        else
            myMaps.m_nRecording_Bggin = m_lstMovUnDo.size();    // 正推录制起始点
    }

    /**
     * 原版 {@code R.id.player_load}「打开状态」。
     *
     * <p>「宏」调试中时，先弹一个「关闭调试」对话框问是否把关卡退回宏打开前的状态，
     * 确认后才继续 {@link #onOpenState2()}（原版 {@code setCancelable(false)}，取消不清调试态）。
     */
    void onOpenState() {
        if (!myMaps.isMacroDebug) {
            onOpenState2();
            return;
        }
        final JCheckBox isBack = HoloContent.check("关卡回到该“宏”打开前的状态", true);
        HoloAlertDialog dlg = HoloAlertDialog.create(this, "关闭调试");
        dlg.setContentView(isBack);
        dlg.addButton("取消", null);
        dlg.addButton("确定", () -> {
            if (isBack.isSelected()) levelReset(false);   // 回到宏打开前的关卡状态（正推复位）
            myMaps.isMacroDebug = false;
            mMap.invalidate();
            onOpenState2();
        });
        dlg.setVisible(true);
    }

    /** 原版 {@code myGameView.myOpenState()}：载入状态列表 → 修正解关标记 → 打开 {@link myStateBrow}。 */
    void onOpenState2() {
        loadStateList();
        myMaps.m_StateIsRedy = false;
        myStateBrow.my_Sort = 0;   // 每次，默认移动优先排序答案
        new myStateBrow().setVisible(true);
    }

    /**
     * 状态列表排序：按保存时间**倒序**（最后保存的在最前面）。
     * 原版 {@code myGameView.myOpenState()} 里的匿名 {@code Comparator}。
     */
    static final Comparator<state_Node> STATE_TIME_DESC = new Comparator<state_Node>() {
        public int compare(state_Node o1, state_Node o2) {
            return o2.time.compareTo(o1.time);
        }
    };

    /** {@link #onOpenState2()} 里「读数据」的部分（与开窗分开，便于测试）。 */
    void loadStateList() {
        mySQLite.m_SQL.load_StateList(myMaps.curMap.Level_id, myMaps.curMap.key);
        // 仅修正本关卡是否解关（按关卡 id）；试推时（Num <= 0）不做修正
        if (myMaps.curMap.Num > 0) {
            myMaps.curMap.Solved = (myMaps.mState2.size() > 0);   // 修正关卡预览图之是否有答案
            mySQLite.m_SQL.Set_L_Solved(myMaps.curMap.Level_id, myMaps.curMap.Solved ? 1 : 0, true);
        }
        // 状态按保存时间排序，最后保存的在最前面
        Collections.sort(myMaps.mState1, STATE_TIME_DESC);
    }

    /** 原版 {@code player_EX} 打包给 {@link myExport} 的数据（与开窗分开，便于测试）。 */
    static class ExportData {
        String xsb;        // 关卡初态
        String lurd;       // Lurd 动作
        String local;      // 关卡正推现场
        String local8;     // 关卡正推现场 -- 旋转
        boolean isAns;     // 是否答案（正推已达答案）
        int gifStart;
        boolean[] rule;    // 需要显示标尺的格子
        short[] boxNum;    // 迷宫箱子编号（人为）
        String importYass;
    }

    /**
     * 原版 {@code R.id.player_EX}「导出」：把当前关卡的
     * 初态（XSB）/ 正推现场 / 正推现场-旋转 / Lurd 动作，连同标尺与箱子编号打包给 {@link myExport}。
     */
    void onExport() {
        ExportData d = buildExportData();
        new myExport(d.xsb, d.lurd, d.local, d.local8, d.isAns, d.gifStart, d.rule, d.boxNum, d.importYass)
                .setVisible(true);
    }

    /** 构造 {@link #onExport()} 要传给 {@link myExport} 的全部数据。 */
    ExportData buildExportData() {
        ExportData d = new ExportData();
        StringBuilder s_XSB = new StringBuilder();   // 关卡初态
        StringBuilder s_XSB1 = new StringBuilder();  // 关卡正推现场
        StringBuilder s_XSB8 = new StringBuilder();  // 关卡正推现场 -- 旋转
        StringBuilder s_Lurd = new StringBuilder();  // Lurd
        boolean isANS = (m_nGoals_OK == m_nGoals);

        // 关卡初态
        s_XSB.append(myMaps.curMap.Map).append("\nTitle: ").append(myMaps.curMap.Title)
                .append("\nAuthor: ").append(myMaps.curMap.Author);
        if (!myMaps.curMap.Comment.trim().isEmpty()) {
            s_XSB.append("\nComment:\n").append(myMaps.curMap.Comment).append("\nComment-End:");
        }

        // 关卡正推现场
        char ch;
        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                ch = m_cArray[i][j];  // 正推迷宫
                if (myMaps.m_Sets[13] == 1) {  // “互动双推”模式
                    if (bk_cArray[i][j] == '$' || bk_cArray[i][j] == '*') {  // 只有逆推地图中的箱子将成为正推的目标点位
                        switch (ch) {
                            case '-': ch = '.'; break;
                            case '$': ch = '*'; break;
                            case '@': ch = '+';
                        }
                    } else {
                        switch (ch) {
                            case '.': ch = '-'; break;
                            case '*': ch = '$'; break;
                            case '+': ch = '@';
                        }
                    }
                }
                s_XSB1.append(ch);
            }
            if (i < myMaps.curMap.Rows - 1) s_XSB1.append('\n');
        }

        // 关卡正推现场 -- 旋转（m_nTrun 为偶数时行优先，奇数时列优先）
        char ch2;
        if (myMaps.m_nTrun % 2 == 0) {
            for (int i = 0; i < myMaps.curMap.Rows; i++) {
                for (int j = 0; j < myMaps.curMap.Cols; j++) {
                    switch (myMaps.m_nTrun) {
                        case 0: ch = m_cArray[i][j];
                                ch2 = bk_cArray[i][j]; break;
                        case 2: ch = m_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j];
                                ch2 = bk_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j]; break;
                        case 4: ch = m_cArray[i][myMaps.curMap.Cols - 1 - j];
                                ch2 = bk_cArray[i][myMaps.curMap.Cols - 1 - j]; break;
                        case 6: ch = m_cArray[myMaps.curMap.Rows - 1 - i][j];
                                ch2 = bk_cArray[myMaps.curMap.Rows - 1 - i][j]; break;
                        default: ch = '_'; ch2 = '_'; break;
                    }
                    s_XSB8.append(dualPush(ch, ch2));
                }
                if (i < myMaps.curMap.Rows - 1) s_XSB8.append('\n');
            }
        } else {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                for (int i = 0; i < myMaps.curMap.Rows; i++) {
                    switch (myMaps.m_nTrun) {
                        case 1: ch = m_cArray[myMaps.curMap.Rows - 1 - i][j];
                                ch2 = bk_cArray[myMaps.curMap.Rows - 1 - i][j]; break;
                        case 3: ch = m_cArray[i][myMaps.curMap.Cols - 1 - j];
                                ch2 = bk_cArray[i][myMaps.curMap.Cols - 1 - j]; break;
                        case 5: ch = m_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j];
                                ch2 = bk_cArray[myMaps.curMap.Rows - 1 - i][myMaps.curMap.Cols - 1 - j]; break;
                        case 7: ch = m_cArray[i][j];
                                ch2 = bk_cArray[i][j]; break;
                        default: ch = '_'; ch2 = '_'; break;
                    }
                    s_XSB8.append(dualPush(ch, ch2));
                }
                if (j < myMaps.curMap.Cols - 1) s_XSB8.append('\n');
            }
        }

        char[] Move = {'l', 'u', 'r', 'd', 'L', 'U', 'R', 'D'};
        byte t;
        Iterator<Byte> myItr;

        // 解关答案
        if (!m_lstMovUnDo.isEmpty()) {
            myItr = m_lstMovUnDo.iterator();
            while (myItr.hasNext()) {
                t = myItr.next();
                s_Lurd.append(Move[t - 1]);
            }
        }
        if (!isANS) {  // 若正推已经是答案，则不再导出逆推动作
            if (!m_lstMovUnDo2.isEmpty()) {
                if (s_Lurd.length() > 0) s_Lurd.append('\n');
                s_Lurd.append('[').append(m_nCol0 + 1).append(',').append(m_nRow0 + 1).append(']');  // （x, y）-- 先列后行
                myItr = m_lstMovUnDo2.iterator();
                while (myItr.hasNext()) {
                    t = myItr.next();
                    s_Lurd.append(Move[t - 1]);
                }
            }
        }

        boolean[] my_Rule = new boolean[myMaps.curMap.Cols * myMaps.curMap.Rows];
        short[] my_BoxNum = new short[m_nGoals];  // 按箱子数定义，记录“自动箱子编号”，以方便转换“人工箱子编号”
        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                my_Rule[myMaps.curMap.Cols * i + j] = mark44[i][j];
                if (m_iBoxNum2[i][j] > 0 && (m_cArray[i][j] == '$' || m_cArray[i][j] == '*')) {
                    // 自动箱子编号与人工箱子编号建立关联
                    my_BoxNum[m_iBoxNum2[i][j] - 1] = myMaps.m_bBianhao ? m_iBoxNum2[i][j] : m_iBoxNum[i][j];
                }
            }
        }

        d.xsb = s_XSB.toString();
        d.lurd = s_Lurd.toString();
        d.local = s_XSB1.toString();
        d.local8 = s_XSB8.toString();
        d.isAns = isANS;
        d.gifStart = m_Gif_Start;
        d.rule = my_Rule;
        d.boxNum = my_BoxNum;
        d.importYass = m_imPort_YASS;
        return d;
    }

    /** 「互动双推」（{@code myMaps.m_Sets[13] == 1}）时把正推格按逆推格是否为箱子做点/箱/人互换。 */
    private static char dualPush(char ch, char ch2) {
        if (myMaps.m_Sets[13] != 1) return ch;
        if (ch2 == '$' || ch2 == '*') {
            switch (ch) {
                case '-': return '.';
                case '$': return '*';
                case '@': return '+';
            }
        } else {
            switch (ch) {
                case '.': return '-';
                case '*': return '$';
                case '+': return '@';
            }
        }
        return ch;
    }

    // ================================================================ 测试钩子

    /** 供测试读取「更多」选项菜单（调用 {@link #openOptionsMenu()} 之后才有值）。 */
    JPopupMenu optionsMenuForTest() {
        return optionsMenu;
    }

    /** 供测试读取选项菜单的条目文字（不含分隔线）。 */
    java.util.List<String> optionsMenuTitlesForTest() {
        java.util.List<String> titles = new java.util.ArrayList<String>();
        if (optionsMenu == null) return titles;
        for (Component c : optionsMenu.getComponents()) {
            if (c instanceof HoloPopupMenu.Row) titles.add(((HoloPopupMenu.Row) c).getText());
        }
        return titles;
    }

    private void showSetup1Dialog() {
        String[] opts = {"速度设置", "更换皮肤", "更换背景", "奇偶格位明暗度"};
        String sel = (String) JOptionPane.showInputDialog(this, "请选择设置项:", "设置", JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (sel == null) return;

        if (sel.equals("速度设置")) {
            String sp = (String) JOptionPane.showInputDialog(this, "选择移动速度:", "移动速度", JOptionPane.PLAIN_MESSAGE, null, m_sSleep, m_sSleep[myMaps.m_Sets[10]]);
            if (sp != null) {
                for (int i = 0; i < m_sSleep.length; i++) {
                    if (m_sSleep[i].equals(sp)) {
                        myMaps.m_Sets[10] = i;
                        break;
                    }
                }
                saveSets();
            }
        } else if (sel.equals("更换皮肤")) {
            skinList();
            if (!myMaps.mFile_List1.isEmpty()) {
                String[] skins = myMaps.mFile_List1.toArray(new String[0]);
                String skin = (String) JOptionPane.showInputDialog(this, "选择皮肤:", "皮肤", JOptionPane.PLAIN_MESSAGE, null, skins, myMaps.skin_File);
                if (skin != null) {
                    myMaps.skin_File = skin;
                    myMaps.loadSkins();
                    mMap.invalidate();
                    saveSets();
                }
            }
        } else if (sel.equals("更换背景")) {
            bkPicList();
            if (!myMaps.mFile_List2.isEmpty()) {
                String[] bks = myMaps.mFile_List2.toArray(new String[0]);
                String bk = (String) JOptionPane.showInputDialog(this, "选择背景:", "背景图片", JOptionPane.PLAIN_MESSAGE, null, bks, myMaps.bk_Pic);
                if (bk != null) {
                    myMaps.bk_Pic = bk;
                    myMaps.loadBKPic();
                    if (bk.equals("使用背景色")) {
                        setColorBK();
                    } else if (myMaps.bkPict != null) {
                        mMap.w_bkPic = myMaps.bkPict.getWidth();
                        mMap.h_bkPic = myMaps.bkPict.getHeight();
                        mMap.w_bkNum = myMaps.m_nWinWidth / mMap.w_bkPic + 1;
                        mMap.h_bkNum = myMaps.m_nWinHeight / mMap.h_bkPic + 1;
                    }
                    mMap.invalidate();
                    saveSets();
                }
            }
        } else if (sel.equals("奇偶格位明暗度")) {
            mMap.m_lParityBrightnessShade = true;
            mMap.invalidate();
        }
    }

    private void showSetup2Dialog() {
        JDialog dlg = new JDialog(this, "开关选项", true);
        dlg.setLayout(new BorderLayout());

        JPanel pnl = new JPanel(new GridLayout(0, 2, 5, 5));
        pnl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JCheckBox chk0 = new JCheckBox("双击箱子编号", myMaps.m_Sets[41] == 1);
        JCheckBox chk1 = new JCheckBox("自动箱子编号", myMaps.m_bBianhao);
        JCheckBox chk2 = new JCheckBox("标尺", myMaps.m_bBiaochi);
        JCheckBox chk3 = new JCheckBox("标尺不随关卡旋转", myMaps.m_Sets[9] == 1);
        JCheckBox chk4 = new JCheckBox("区分奇偶地板格", myMaps.m_Sets[38] == 1);
        JCheckBox chk5 = new JCheckBox("死锁嗅探", myMaps.m_Sets[11] == 1);
        JCheckBox chk6 = new JCheckBox("可达提示", myMaps.m_Sets[8] == 1);
        JCheckBox chk7 = new JCheckBox("仓管员转向动画", myMaps.m_Sets[27] == 1);
        JCheckBox chk8 = new JCheckBox("长按点位提示关联网", myMaps.m_Sets[3] == 1);
        JCheckBox chk9 = new JCheckBox("自动加载最新状态", myMaps.m_Sets[37] == 1);
        JCheckBox chk10 = new JCheckBox("禁用逆推目标点", myMaps.m_Sets[32] == 1);
        JCheckBox chk11 = new JCheckBox("允许穿越", myMaps.m_Sets[17] == 1);
        JCheckBox chk12 = new JCheckBox("单步进退", myMaps.m_Sets[23] == 1);
        JCheckBox chk13 = new JCheckBox("进度条", (!bt_BK.isChecked() && mMap.m_lGoto) || (bt_BK.isChecked() && mMap.m_lGoto2));
        JCheckBox chk14 = new JCheckBox("自动爬阶梯", myMaps.m_Sets[29] == 1);
        JCheckBox chk15 = new JCheckBox("显示当前时间提示", myMaps.m_Sets[25] == 1);

        pnl.add(chk0); pnl.add(chk1);
        pnl.add(chk2); pnl.add(chk3);
        pnl.add(chk4); pnl.add(chk5);
        pnl.add(chk6); pnl.add(chk7);
        pnl.add(chk8); pnl.add(chk9);
        pnl.add(chk10); pnl.add(chk11);
        pnl.add(chk12); pnl.add(chk13);
        pnl.add(chk14); pnl.add(chk15);

        JPanel btnPnl = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("确定");
        btnOk.addActionListener(e -> {
            myMaps.m_Sets[41] = chk0.isSelected() ? 1 : 0;
            myMaps.m_bBianhao = chk1.isSelected();
            myMaps.m_bBiaochi = chk2.isSelected();
            myMaps.m_Sets[9] = chk3.isSelected() ? 1 : 0;
            myMaps.m_Sets[38] = chk4.isSelected() ? 1 : 0;
            myMaps.m_Sets[11] = chk5.isSelected() ? 1 : 0;
            myMaps.m_Sets[8] = chk6.isSelected() ? 1 : 0;
            myMaps.m_Sets[27] = chk7.isSelected() ? 1 : 0;
            myMaps.m_Sets[3] = chk8.isSelected() ? 1 : 0;
            myMaps.m_Sets[37] = chk9.isSelected() ? 1 : 0;
            myMaps.m_Sets[32] = chk10.isSelected() ? 1 : 0;
            myMaps.m_Sets[17] = chk11.isSelected() ? 1 : 0;
            myMaps.m_Sets[23] = chk12.isSelected() ? 1 : 0;
            if (chk13.isSelected()) {
                if (bt_BK.isChecked()) mMap.m_lGoto2 = true;
                else mMap.m_lGoto = true;
            } else {
                if (bt_BK.isChecked()) mMap.m_lGoto2 = false;
                else mMap.m_lGoto = false;
            }
            myMaps.m_Sets[29] = chk14.isSelected() ? 1 : 0;
            myMaps.m_Sets[25] = chk15.isSelected() ? 1 : 0;

            saveSets();
            mMap.invalidate();
            dlg.dispose();
        });
        btnPnl.add(btnOk);

        dlg.add(pnl, BorderLayout.CENTER);
        dlg.add(btnPnl, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    public void setColorBK() {
        Color c = JColorChooser.showDialog(this, "选择背景色", new Color(myMaps.m_Sets[4], true));
        if (c != null) {
            myMaps.m_Sets[4] = c.getRGB();
            myMaps.bk_Pic = "使用背景色";
            mMap.invalidate();
            saveSets();
        }
    }

    public void setMenuVisible() {
        if (main_bottom.isVisible()) {
            main_bottom.setVisible(false);
            mMap.m_nArenaTop = 0;
        } else {
            main_bottom.setVisible(true);
            mMap.m_nArenaTop = getHeight() * 27 / 640;
        }
        revalidate();
        mMap.invalidate();
    }

    public void boxNoMoved() {
        if (bt_BK.isChecked()) return;

        int row = m_nRow3;
        int col = m_nCol3;
        mMap.m_boxNoMoved = true;
        mMap.m_boxNoUsed = true;
        int dir = 0;

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (mark7[i][j]) mark11[i][j] = false;
                else mark11[i][j] = true;
                if (mark8[i][j]) mark12[i][j] = false;
                else mark12[i][j] = true;
            }
        }

        byte mDir;
        int t = -1;
        Iterator<Byte> myItr = m_lstMovUnDo.iterator();
        while (myItr.hasNext()) {
            mDir = myItr.next();
            switch (mDir) {
                case 1:
                case 5:
                    col--;
                    t = 0;
                    break;
                case 2:
                case 6:
                    row--;
                    t = 2;
                    break;
                case 3:
                case 7:
                    col++;
                    t = 1;
                    break;
                case 4:
                case 8:
                    row++;
                    t = 3;
                    break;
            }

            if (mDir > 4 && t >= 0 && !mark11[row - dr4[t] * dir][col - dc4[t] * dir]) {
                mark11[row - dr4[t] * dir][col - dc4[t] * dir] = true;
                mark12[row - dr4[t] * dir][col - dc4[t] * dir] = true;
            }
            mark12[row][col] = true;
        }
    }

    public void boxCanCome(int row, int col) {
        boolean[][] mark, mark9;
        char[][] level;
        int nRow, nCol;

        if (bt_BK.isChecked()) {
            level = bk_cArray;
            mark = mPathfinder.mark4;
            mark9 = mark12;
            mMap.m_boxCanCome2 = true;
            nRow = m_nRow2;
            nCol = m_nCol2;
        } else {
            level = m_cArray;
            mark = mPathfinder.mark3;
            mark9 = mark11;
            mMap.m_boxCanCome = true;
            nRow = m_nRow;
            nCol = m_nCol;
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                mark9[i][j] = false;
            }
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (level[i][j] == '$' || level[i][j] == '*') {
                    mPathfinder.FindBlock(bt_BK.isChecked(), level, i, j);
                    mPathfinder.boxReachable(bt_BK.isChecked(), i, j, nRow, nCol);
                    mark9[i][j] = mark[row][col];
                }
            }
        }
        mark9[row][col] = true;
    }

    public void boxCanMove() {
        char[][] level;
        boolean[][] mark, mark9;
        boolean flg = bt_BK.isChecked();

        if (flg) {
            level = bk_cArray;
            mark = mPathfinder.mark2;
            mark9 = mark12;
            mMap.m_boxCanMove2 = true;
        } else {
            level = m_cArray;
            mark = mPathfinder.mark1;
            mark9 = mark11;
            mMap.m_boxCanMove = true;
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                mark9[i][j] = false;
            }
        }

        for (int i = 0; i < myMaps.curMap.Rows; i++) {
            for (int j = 0; j < myMaps.curMap.Cols; j++) {
                if (level[i][j] == '$' || level[i][j] == '*') {
                    for (int k = 0; 4 > k; k++) {
                        try {
                            if (flg) {
                                if (mark[i + dr4[k]][j + dc4[k]] && (level[i + dr4[k] * 2][j + dc4[k] * 2] == '-' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '.' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '@' || level[i + dr4[k] * 2][j + dc4[k] * 2] == '+')) {
                                    mark9[i][j] = true;
                                    break;
                                }
                            } else {
                                if (mark[i + dr4[k]][j + dc4[k]] && (level[i - dr4[k]][j - dc4[k]] == '-' || level[i - dr4[k]][j - dc4[k]] == '.' || level[i - dr4[k]][j - dc4[k]] == '@' || level[i - dr4[k]][j - dc4[k]] == '+')) {
                                    mark9[i][j] = true;
                                    break;
                                }
                            }
                        } catch (Throwable ex) { }
                    }
                }
            }
        }
    }

    public boolean FindPath(int k, int l, boolean flg) {
        LinkedList<Byte> path;
        LinkedList<Byte> pathByteList;

        if (flg) {
            path = mPathfinder.manTo(true, bk_cArray, m_nRow2, m_nCol2, k, l);
            pathByteList = m_lstMovReDo2;
        } else {
            path = mPathfinder.manTo(false, m_cArray, m_nRow, m_nCol, k, l);
            pathByteList = m_lstMovReDo;
        }

        if (!path.isEmpty()) {
            pathByteList.clear();
            while (!path.isEmpty()) {
                pathByteList.offer(path.removeFirst());
            }
            return true;
        } else return false;
    }

    public void formatPath(String strPath, boolean flg) {
        int Len = strPath.length();
        if (Len > 0) {
            LinkedList<Byte> pathByteList = flg ? m_lstMovReDo2 : m_lstMovReDo;
            pathByteList.clear();
            for (int t = 0; t < Len; t++) {
                switch (strPath.charAt(t)) {
                    case 'l': pathByteList.offerFirst((byte)1); break;
                    case 'u': pathByteList.offerFirst((byte)2); break;
                    case 'r': pathByteList.offerFirst((byte)3); break;
                    case 'd': pathByteList.offerFirst((byte)4); break;
                    case 'L': pathByteList.offerFirst((byte)5); break;
                    case 'U': pathByteList.offerFirst((byte)6); break;
                    case 'R': pathByteList.offerFirst((byte)7); break;
                    case 'D': pathByteList.offerFirst((byte)8); break;
                }
            }
        }
    }

    public void StopMicro() {
        if (mMicroTask != null) {
            mMicroTask.cancel(true);
            mMicroTask = null;
        }
    }

    public void myStop() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
        StopMicro();
        if (mClockTimer != null) {
            mClockTimer.stop();
            mClockTimer = null;
        }
        if (myTimer1 != null) { myTimer1.stop(); myTimer1 = null; }
        if (myTimer2 != null) { myTimer2.stop(); myTimer2 = null; }
        if (myTimer3 != null) { myTimer3.stop(); myTimer3 = null; }
        if (myTimer4 != null) { myTimer4.stop(); myTimer4 = null; }
        if (mMap != null) mMap.d_Moves = mMap.m_PicWidth;
    }

    public void saveSets() {
        IniFile file = new IniFile();
        file.set("常规", "当前关卡集组别", myMaps.m_Sets[0]);
        file.set("常规", "当前关卡集", myMaps.m_Sets[1]);
        file.set("常规", "预览时是否显示关卡标题", myMaps.m_Sets[2]);
        file.set("常规", "是否标识出重复关卡", myMaps.m_Sets[12]);
        file.set("常规", "浏览时每行的图标数", myMaps.m_Sets[33]);
        file.set("常规", "浏览时每行的图标默认数", myMaps.m_Sets[34]);

        file.set("界面", "背景色", myMaps.m_Sets[4]);
        file.set("界面", "皮肤", myMaps.skin_File);
        file.set("界面", "背景图片", myMaps.bk_Pic);
        file.set("界面", "背景时间", myMaps.m_Sets[25]);

        file.set("速度", "瞬移状态", myMaps.m_Sets[6]);
        file.set("速度", "移动速度", myMaps.m_Sets[10]);

        file.set("操作", "是否提示死锁", myMaps.m_Sets[11]);
        file.set("操作", "长按点位提示关联网", myMaps.m_Sets[3]);
        file.set("操作", "显示可达提示", myMaps.m_Sets[8]);
        file.set("操作", "仓管员转向动画", myMaps.m_Sets[27]);
        file.set("操作", "禁用全屏", myMaps.m_Sets[20]);
        file.set("操作", "演示时仅推动", myMaps.m_Sets[28]);
        file.set("操作", "标尺是否同步旋转", myMaps.m_Sets[9]);
        file.set("操作", "是否允许音量键选择关卡", myMaps.m_Sets[15]);
        file.set("操作", "显示系统虚拟按键", myMaps.m_Sets[16]);
        file.set("操作", "是否允许穿越", myMaps.m_Sets[17]);
        file.set("操作", "自动爬阶梯", myMaps.m_Sets[29]);
        file.set("操作", "导出答案的注释信息", myMaps.m_Sets[30]);
        file.set("操作", "自动打开导入关卡", myMaps.m_Sets[31]);
        file.set("操作", "禁用逆推目标点", myMaps.m_Sets[32]);
        file.set("操作", "区分奇偶地板格", myMaps.m_Sets[38]);
        file.set("操作", "偶格位明暗度", myMaps.m_Sets[39]);
        file.set("操作", "奇格位明暗度", myMaps.m_Sets[40]);
        file.set("操作", "双击箱子编号", myMaps.m_Sets[41]);

        file.set("编辑", "关卡编辑中，图中标尺的字体颜色", myMaps.m_Sets[21]);
        file.set("编辑", "关卡编辑中，携带标尺的元素", myMaps.m_Sets[22]);
        file.set("编辑", "是否采用YASC绘制习惯", myMaps.m_Sets[19]);
        file.set("识别", "图像识别", myMaps.m_Sets[36]);

        file.save(new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini"));
    }

    private void saveAct(String name, String value) {
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            IniFile ini = new IniFile(f);
            ini.set("Action", name, value);
            ini.save(f);
        } catch (Throwable ex) { }
    }

    private String loadAct(String name) {
        try {
            File f = new File(myMaps.sRoot + myMaps.sPath + "BoxMan.ini");
            if (!f.exists()) return "";
            IniFile ini = new IniFile(f);
            Object val = ini.get("Action", name, "");
            return val != null ? val.toString() : "";
        } catch (Throwable ex) {
            return "";
        }
    }

    private void mLoad_Do_Macro() {
        if (bt_BK.isChecked()) {
            MyToast.showToast(this, "逆推不支持宏功能！", MyToast.LENGTH_SHORT);
        } else {
            m_nItemSelect = -1;
            mMap.m_lGoto = false;
            mMap.m_lParityBrightnessShade = false;
            myMaps.mMacroList();
            if (myMaps.mFile_List.size() > 0) {
                String[] list = myMaps.mFile_List.toArray(new String[0]);
                String sel = (String) JOptionPane.showInputDialog(this, "选择：宏", "宏", JOptionPane.PLAIN_MESSAGE, null, list, list[0]);
                if (sel != null) {
                    myMaps.isMacroDebug = false;
                    myMaps.sAction = myMaps.readMacroFile(sel).split("\n|\r|\n\r|\r\n|\\|");

                    int len = myMaps.sAction.length;
                    int w, w1, w2;
                    String str;
                    for (int k = 0; k < len; k++) {
                        if (myMaps.sAction[k].trim().isEmpty()) {
                            myMaps.sAction[k] = "";
                            continue;
                        } else {
                            str = myMaps.qj2bj(myMaps.sAction[k]).trim();
                        }
                        w = str.indexOf('<');
                        if (w >= 0) {
                            w1 = str.indexOf(';');
                            if (w1 >= 0 && w1 < w) w = w1;
                            else {
                                w2 = str.indexOf('>');
                                w = str.indexOf(';', w2);
                            }
                        } else {
                            w = str.indexOf(';');
                        }
                        if (w > 0) {
                            myMaps.sAction[k] = str.substring(0, w).replaceAll("[\t]", " ").trim();
                        } else if (w == 0) {
                            myMaps.sAction[k] = "";
                        } else {
                            myMaps.sAction[k] = str;
                        }
                    }

                    if (!myMaps.sAction[0].isEmpty() && myMaps.sAction[0].charAt(0) == '=') {
                        myMaps.m_ActionIsPos = false;
                    } else {
                        myMaps.m_ActionIsPos = true;
                    }
                    if (myMaps.m_ActionIsPos) {
                        m_lstMovedHistory.clear();
                        Iterator<Byte> myItr = m_lstMovUnDo.descendingIterator();
                        while (myItr.hasNext()) {
                            m_lstMovedHistory.offer(myItr.next());
                        }
                    }
                    if (!myMaps.m_ActionIsPos) {
                        levelReset(false);
                    }

                    m_nMacro_Row = m_nRow;
                    m_nMacro_Col = m_nCol;
                    mMap.myMacro.clear();
                    mMap.myMacro.add(0);
                    myMaps.isMacroDebug = false;
                    StopMicro();
                    mMicroTask = new RunMicroTask(this);
                    mMicroTask.execute(0, myMaps.sAction.length - 1);
                    m_bBusing = false;
                    mMap.invalidate();
                    if (!m_lstMovReDo.isEmpty()) {
                        m_lstMovReDo.clear();
                    }
                }
            } else {
                MyToast.showToast(this, "没有可读取的“宏”文档！", MyToast.LENGTH_SHORT);
            }
        }
    }

    String[][] DIR = {
            {"l", "u", "r", "d", "L", "U", "R", "D"},
            {"d", "l", "u", "r", "D", "L", "U", "R"},
            {"r", "d", "l", "u", "R", "D", "L", "U"},
            {"u", "r", "d", "l", "U", "R", "D", "L"},
            {"r", "u", "l", "d", "R", "U", "L", "D"},
            {"d", "r", "u", "l", "D", "R", "U", "L"},
            {"l", "d", "r", "u", "L", "D", "R", "U"},
            {"u", "l", "d", "r", "U", "L", "D", "R"}};

    private void doACT(String myACT) {
        if (myMaps.m_ActionIsTrun) {
            Pattern p = Pattern.compile("l|L|r|R|u|U|d|D");
            Matcher m = p.matcher(myACT);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                if (m.group().equals("l")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][0]);
                else if (m.group().equals("u")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][1]);
                else if (m.group().equals("r")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][2]);
                else if (m.group().equals("d")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][3]);
                else if (m.group().equals("L")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][4]);
                else if (m.group().equals("U")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][5]);
                else if (m.group().equals("R")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][6]);
                else if (m.group().equals("D")) m.appendReplacement(sb, DIR[myMaps.m_nTrun][7]);
            }
            m.appendTail(sb);
            formatPath(sb.toString(), bt_BK.isChecked());
        } else {
            formatPath(myACT, bt_BK.isChecked());
        }
        int len;
        m_bACT_ERROR = false;
        if (bt_BK.isChecked()) {
            len = m_lstMovReDo2.size();
            if (len > 0) {
                for (int k = 0; k < len; k++) reDo2();
            }
        } else {
            len = m_lstMovReDo.size();
            if (len > 0) {
                for (int k = 0; k < len; k++) reDo1();
            }
        }
    }

    private char my_Get_Position(String str, int cs) {
        int m = 0, n = 0;
        char chr = '!';
        boolean inLine = cs < 0;

        if (inLine) {
            if (str.charAt(0) == '@') cs = 1;
            else if (str.charAt(0) == '+') cs = 2;
            else cs = 0;
        }

        boolean bMoves = str.indexOf('~') < 0;

        if (str.indexOf(',') < 0) {
            if (cs > 0) {
                chr = ' ';
            } else {
                try {
                    m = Integer.valueOf(str.replaceAll("[^0-9]", " ").trim()) - 1;
                    str = str.replaceAll("[^a-zA-Z]", " ").toLowerCase(Locale.getDefault()).trim();
                    if (str.isEmpty() || str.length() > 2) {
                        chr = ' ';
                    } else {
                        if (str.length() == 1) {
                            n = (int) str.charAt(0) - 'a';
                        } else {
                            int i = (int) str.charAt(0) - 'a';
                            int j = (int) str.charAt(1) - 'a';
                            n = (i + 1) * 26 + j;
                        }
                    }
                } catch (Throwable ex) {
                    chr = ' ';
                }
            }
        } else {
            try {
                String[] arr = str.replaceAll("[@~\\+\\[\\]]", " ").split(",");
                m = Integer.valueOf(arr[1].trim());
                n = Integer.valueOf(arr[0].trim());
            } catch (Throwable ex) {
                chr = ' ';
            }
        }

        if (chr != ' ') {
            int r2, c2;
            switch (cs) {
                case 1:
                    r2 = m_nRow + m;
                    c2 = m_nCol + n;
                    break;
                case 2:
                    r2 = m_nMacro_Row + m;
                    c2 = m_nMacro_Col + n;
                    break;
                default:
                    r2 = m;
                    c2 = n;
            }
            if (r2 < 0 || c2 < 0 || r2 >= myMaps.curMap.Rows || c2 >= myMaps.curMap.Cols) {
                chr = ' ';
            } else {
                if (!inLine) {
                    m_nMacro_Row = r2;
                    m_nMacro_Col = c2;
                    mMap.myMacroInf = "  >>>  [ " + mMap.mGetCur(r2, c2) + " ]";
                    if (bMoves) {
                        if (FindPath(m_nMacro_Row, m_nMacro_Col, false)) {
                            int len = m_lstMovReDo.size();
                            for (int k = 0; k < len; k++) {
                                reDo1();
                            }
                        }
                    }
                } else {
                    chr = m_cArray[r2][c2];
                    mMap.myMacroInf = "  >>>  [ " + mMap.mGetCur(r2, c2) + " ] = " + chr;
                }
            }
        }
        return chr;
    }

    private int my_Get_Num(String str, int def) {
        try {
            return Integer.valueOf(str.trim());
        } catch (Throwable ex) {
            return def;
        }
    }

    boolean isLooping = false;
    int loopBegin = -1, loopEnd = -1;

    private int myDo_Loop(int nLine) {
        int n = my_Get_Num(myMaps.sAction[nLine].replaceAll("[\\*]", " "), -1);
        if (n < 0) return nLine + 1;

        int k = nLine + 1;
        while (k < myMaps.sAction.length) {
            if (myMaps.sAction[k].isEmpty()) {
                k++;
                continue;
            }
            if (myMaps.sAction[k].charAt(0) == '*') {
                int n2 = my_Get_Num(myMaps.sAction[k].replaceAll("[\\*]", " "), -1);
                if (n2 < 0) {
                    k++;
                }
                break;
            }
            k++;
        }
        isLooping = true;
        loopBegin = nLine + 1;
        loopEnd = k - 1;
        for (int s = 0; s < n; s++) {
            myDo_Block(loopBegin, loopEnd, false, true);
            if (!isLooping) break;
        }
        if (isLooping) isLooping = false;
        loopBegin = -1;
        loopEnd = -1;
        return k;
    }

    private int myGet_GoTo(String mStr) {
        int k, n;
        char ch;
        String str = mStr.replaceAll("%", " ").trim();

        if (loopBegin >= 0 && str.equals("*")) {
            isLooping = false;
            return -2;
        }

        n = my_Get_Num(str, -1);
        if (n < 0) return -1;

        int len;
        if (loopBegin >= 0) {
            k = loopBegin;
            len = loopEnd;
        } else {
            k = 0;
            len = myMaps.sAction.length;
        }
        while (k < len) {
            if (myMaps.sAction[k].isEmpty()) {
                k++;
                continue;
            }
            ch = myMaps.sAction[k].charAt(0);
            if (ch == ':') {
                if (n == my_Get_Num(myMaps.sAction[k].replaceAll(":", " "), -1)) {
                    return k + 1;
                }
            }
            k++;
        }
        return -1;
    }

    private void myDo_Block(int mBegin, int mEnd, boolean isUnDo, boolean isLoop) {
        int n;
        char ch;

        int step = mBegin;
        while (step <= mEnd && step < myMaps.sAction.length) {
            mMap.myMacroInf = "";
            if (myMaps.sAction[step].isEmpty()) {
                step++;
                if (!isUnDo && !isLoop && mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                    mMap.myMacro.add(step);
                }
                continue;
            }

            ch = myMaps.sAction[step].charAt(0);
            m_bBusing = false;
            if (ch == '*') {
                step = myDo_Loop(step);
            } else {
                n = myDo_Line(myMaps.sAction[step], isUnDo, isLoop);
                if (n == -1) {
                    step++;
                } else if (n < -1) {
                    return;
                } else {
                    step = n;
                }
            }

            if (!bt_BK.isChecked()) {
                if (!isUnDo && !isLoop && mMap.myMacro.get(mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                    mMap.myMacro.add(step);
                }
                if (!isUnDo && !isLoop && myMaps.isMacroDebug) break;
            }
        }
    }

    private int myDo_Line(String myACT, boolean isUnDo, boolean isLoop) {
        if (myACT.isEmpty()) return -1;

        String str;
        String[] myArr;
        int n, m;
        char ch;

        ch = myACT.charAt(0);
        m_bACT_IgnoreCase = false;
        m_bACT_ERROR = false;
        if (ch == '{') {
            try {
                if (myACT.indexOf('=') >= 0) {
                    myArr = myACT.replaceAll("[\\{\\}]", " ").split("=");
                    if (myArr.length > 1) {
                        str = myArr[0].trim();
                        n = my_Get_Num(str, 0);
                        str = myArr[1].trim();
                        if (n > 9 && n < 100 && myMaps.isLURD(str)) {
                            saveAct("reg" + n, str);
                        }
                    }
                } else {
                    m_bACT_IgnoreCase = (myACT.indexOf('~') >= 0);
                    myArr = myACT.replaceAll("~", " ").split("\\{|\\}");
                    if (myArr.length > 1) {
                        str = myArr[1].trim();
                        n = my_Get_Num(str, 0);
                        if (n > 0 && n < 100) {
                            str = loadAct("reg" + n);
                        }
                    } else {
                        str = "";
                    }
                    if (myArr.length > 2) {
                        n = my_Get_Num(myArr[2].trim(), 1);
                        if (n == 0) {
                            n = Integer.MAX_VALUE;
                        }
                        if (n > 1) {
                            for (int k = 0; k < n; k++) {
                                doACT(str);
                                if (m_bACT_ERROR) break;
                            }
                        } else {
                            doACT(str);
                        }
                    } else {
                        doACT(str);
                    }
                }
            } catch (Throwable ex) { }
        } else if (ch == '?') {
            myArr = myACT.split("\\?|:|\\/");
            if (myArr.length == 3 || myArr.length == 4) {
                String[] myArr2 = myArr[1].split("=");
                if (myArr2.length == 2) {
                    char ch2 = my_Get_Position(myArr2[0].trim(), -1);
                    mMap.myMacroInf = mMap.myMacroInf + " ▲ " + myACT;
                    if (myArr2[1].trim().indexOf(ch2) >= 0) {
                        return myDo_Line(myArr[2].trim(), isUnDo, isLoop);
                    } else {
                        if (myArr.length == 4) {
                            return myDo_Line(myArr[3].trim(), isUnDo, isLoop);
                        }
                    }
                }
            }
        } else if (ch == '(') {
            try {
                myArr = myACT.split("\\(|\\)");
                n = my_Get_Num(myArr[1].trim(), -1);
                for (int k = 0; k < n; k++) unDo1();
            } catch (Throwable ex) { }
        } else if (ch == '@') {
            my_Get_Position(myACT, 1);
        } else if (ch == '+') {
            my_Get_Position(myACT, 2);
        } else if (ch == '[') {
            my_Get_Position(myACT, 0);
        } else if (ch == '%') {
            return myGet_GoTo(myACT);
        } else if (ch == '^') {
            if (!isUnDo && !isLoop) {
                myMaps.isMacroDebug = true;
            }
        } else if (ch == '<') {
            n = myACT.indexOf('>');
            if (n >= 0) {
                m = my_Get_Num(myACT.substring(n).replaceAll(">~", " ").trim(), 1);
                myArr = myACT.substring(0, n).replaceAll("<", " ").split(";");
            } else {
                m = 1;
                myArr = myACT.replaceAll("<", " ").split(";");
            }
            int len = myArr.length;
            for (int t = 0; t < m; t++) {
                for (int k = 0; k < len; k++) {
                    n = myDo_Line(myArr[k].trim(), isUnDo, isLoop);
                    if (n >= 0) return n;
                }
            }
        } else if (ch == ':') {
        } else {
            doACT(myACT);
        }
        return -1;
    }

    public static class AsyncCountBoxsTask extends SwingWorker<short[][], Void> {
        boolean m_bNoSolution = false;
        boolean[][] mk15, mk16;
        short[][] mk14;
        byte[][] mk0;
        char[][] mArray;
        char[][] mArray0;
        byte[][] freezeBoxs;
        IntStack intStack;
        int m_Rows, m_Cols;

        private final WeakReference<myGameView> mViewReference;

        public AsyncCountBoxsTask(myGameView mView) {
            super();
            mViewReference = new WeakReference<myGameView>(mView);
        }

        @Override
        protected short[][] doInBackground() {
            myGameView view = mViewReference.get();
            if (view == null || myMaps.curMap == null) return null;

            try {
                m_Rows = myMaps.curMap.Rows;
                m_Cols = myMaps.curMap.Cols;

                int mRow = view.m_nRow;
                int mCol = view.m_nCol;

                if (isCancelled()) return null;

                mk0  = new byte[m_Rows][m_Cols];
                mk15 = new boolean[m_Rows][m_Cols];
                mk16 = new boolean[m_Rows][m_Cols];
                mk14 = new short[m_Rows][m_Cols];
                freezeBoxs = new byte[m_Rows][m_Cols];
                intStack = new IntStack(200);

                mArray = new char[m_Rows][m_Cols];
                mArray0 = new char[m_Rows][m_Cols];

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        mArray0[i][j] = view.m_cArray0[i][j];
                        if (mArray0[i][j] == '#' || mArray0[i][j] == '_') mArray[i][j] = '#';
                        else mArray[i][j] = '-';
                    }
                }

                setBlock4();

                int pos, r, c;
                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if ((mArray0[i][j] == '$' || mArray0[i][j] == '*') && freezeBoxs[i][j] == 0) {
                            if (isZ_Freeze(i, j)) {
                                while (!intStack.isEmpty()) {
                                    pos = intStack.remove();
                                    r = pos >>> 16;
                                    c = pos & 0x0000ffff;
                                    freezeBoxs[r][c] = 2;
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (isBox2(i, j) && freezeBoxs[i][j] > 0) {
                            if (mArray0[i][j] == '$' && !m_bNoSolution) m_bNoSolution = true;
                            mk15[i][j] = true;
                            mArray0[i][j] = '#';
                            mArray[i][j] = '#';
                        }
                    }
                }

                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (mArray0[i][j] == '*' && mArray[i][j] != '#' && checkNet(mArray0, i, j)) {
                            mk15[i][j] = true;
                            mk16[i][j] = true;
                            mArray0[i][j] = '#';
                            mArray[i][j] = '#';
                        }
                    }
                }

                view.mark15 = mk15;
                view.mark16 = mk16;

                view.mPF = new myPathfinder(m_Rows, m_Cols);
                view.mPF.FindBlock(false, mArray, mRow, mCol);
                for (int i = 0; i < m_Rows; i++) {
                    for (int j = 0; j < m_Cols; j++) {
                        if (isCancelled()) return null;
                        if (mArray0[i][j] == '$' || mArray0[i][j] == '*') {
                            mArray[i][j] = '$';
                            view.mPF.boxReachable(false, i, j, mRow, mCol);

                            for (r = 0; r < m_Rows; r++) {
                                for (c = 0; c < m_Cols; c++) {
                                    if (isCancelled()) return null;
                                    if (view.mPF.mark3[r][c]) mk14[r][c]++;
                                }
                            }
                            mArray[i][j] = '-';
                        }
                    }
                }
            } catch (Throwable ex) {
                return null;
            }
            return mk14;
        }

        @Override
        protected void done() {
            myGameView view = mViewReference.get();
            if (view == null) return;
            if (isCancelled()) {
                view.mark14 = null;
                view.mark15 = null;
                view.mark16 = null;
                view.mArray9 = null;
                return;
            }
            try {
                short[][] mk = get();
                view.mark14 = mk;
                view.mArray9 = mArray;
            } catch (Throwable ignored) { }

            if (m_bNoSolution) {
                JOptionPane.showMessageDialog(view, "这是一个无解的关卡！", "提醒", JOptionPane.WARNING_MESSAGE);
            }
            view.bt_More.setTextColor(0xffffffff);
        }

        private boolean isWall(int row, int col) {
            return row < 0 || col < 0 || row >= m_Rows || col >= m_Cols ||
                    mArray0[row][col] == '#' || mArray0[row][col] == '_' ||
                    freezeBoxs[row][col] == 4;
        }

        private boolean isPass(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols) return false;
            return mArray0[row][col] == '-' || mArray0[row][col] == '.' ||
                    mArray0[row][col] == '@' || mArray0[row][col] == '+';
        }

        private boolean isBoxOrWall(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols ||
                    mArray0[row][col] == '#' || mArray0[row][col] == '_' ||
                    mArray0[row][col] == '$' || mArray0[row][col] == '*') return true;
            return false;
        }

        private boolean isBox2(int row, int col) {
            if (row < 0 || col < 0 || row >= m_Rows || col >= m_Cols) return false;
            return mArray0[row][col] == '$' || mArray0[row][col] == '*';
        }

        private void setBlock4() {
            for (int i = 0; i < m_Rows; i++) {
                for (int j = 0; j < m_Cols; j++) {
                    if (isBoxOrWall(i, j) && isBoxOrWall(i, j - 1) && isBoxOrWall(i - 1, j) && isBoxOrWall(i - 1, j - 1)) {
                        if (isBox2(i, j)) freezeBoxs[i][j] = 4;
                        if (isBox2(i, j - 1)) freezeBoxs[i][j - 1] = 4;
                        if (isBox2(i - 1, j)) freezeBoxs[i - 1][j] = 4;
                        if (isBox2(i - 1, j - 1)) freezeBoxs[i - 1][j - 1] = 4;
                    } else freezeBoxs[i][j] = 0;
                }
            }
        }

        private final int[][] dA = {
                {0, -1, -1, 0}, {0, -1, 1, 0}, {0, 1, -1, 0}, {0, 1, 1, 0}
        };
        private final int[][] dB = {
                {1, 0, 0, 1}, {-1, 0, 0, 1}, {1, 0, 0, -1}, {-1, 0, 0, -1}
        };

        private boolean isZ_Freeze(int row, int col) {
            intStack.clear();
            if (isZ(row, col, dA[0]) && isZ(row, col, dB[0])) return true;
            intStack.clear();
            if (isZ(row, col, dA[1]) && isZ(row, col, dB[1])) return true;
            intStack.clear();
            if (isZ(row, col, dA[2]) && isZ(row, col, dB[2])) return true;
            intStack.clear();
            if (isZ(row, col, dA[3]) && isZ(row, col, dB[3])) return true;
            return false;
        }

        private boolean isZ(int row, int col, int[] dDir) {
            int r = row, c = col;
            boolean flg = true;
            while (true) {
                intStack.add(r << 16 | c);
                if (flg) { r += dDir[0]; c += dDir[1]; }
                else { r += dDir[2]; c += dDir[3]; }

                if (isPass(r, c)) break;
                if (isWall(r, c)) return true;
                flg = !flg;
            }
            return false;
        }

        private boolean checkNet(char[][] m_Arr, int b_new_Row, int b_new_Col) {
            int mRows = m_Arr.length, mCols = m_Arr[0].length;
            for (int i = 0; i < mRows; i++) {
                for (int j = 0; j < mCols; j++) {
                    mk0[i][j] = 0;
                }
            }

            Queue<Integer> Q = new LinkedList<Integer>();
            Queue<Integer> Q2 = new LinkedList<Integer>();

            int P, r = b_new_Row, c = b_new_Col, r1, c1, r2, c2;
            Q.offer(r << 16 | c);
            mk0[r][c] = 1;

            while (!Q.isEmpty()) {
                P = Q.poll();
                r = P >>> 16;
                c = P & 0x0000ffff;

                for (int k = 0; k < 4; k++) {
                    r1 = r + dr4(k);
                    c1 = c + dc4(k);
                    r2 = r + dr4(k) * 2;
                    c2 = c + dc4(k) * 2;

                    if (isVisited(mk0, r2, c2) || isWall(r2, c2) || isWall(r1, c1)) {
                        continue;
                    } else if (m_Arr[r2][c2] == '-' || m_Arr[r2][c2] == '.' || m_Arr[r2][c2] == '@' || m_Arr[r2][c2] == '+' || m_Arr[r2][c2] == '$') {
                        return false;
                    } else {
                        Q.offer(r2 << 16 | c2);
                        Q2.offer(r2 << 16 | c2);
                        mk0[r2][c2] = 1;
                    }
                }
            }

            while (!Q2.isEmpty()) {
                P = Q2.poll();
                r = P >>> 16;
                c = P & 0x0000ffff;
                mk15[r][c] = true;
                mk16[r][c] = true;
                m_Arr[r][c] = '#';
                mArray[r][c] = '#';
            }
            return true;
        }

        private int dr4(int k) {
            int[] d = {0, 0, -1, 1};
            return d[k];
        }

        private int dc4(int k) {
            int[] d = {-1, 1, 0, 0};
            return d[k];
        }

        private boolean isVisited(byte[][] m_Mrk, int mR, int mC) {
            return mR >= 0 && mC >= 0 && mR < m_Rows && mC < m_Cols && m_Mrk[mR][mC] > 0;
        }
    }

    public static class RunMicroTask extends SwingWorker<Void, Void> {
        private final WeakReference<myGameView> mViewReference2;
        private long myTime, myTime0;
        private final int[] mySpeed = {0, 200, 300, 500, 1000};
        private final String[] msg = {"", "正推通关！", "正逆相合！", "逆推通关！"};
        private int type = 0;
        private int mBegin, mEnd;

        public RunMicroTask(myGameView mView) {
            super();
            mViewReference2 = new WeakReference<myGameView>(mView);
        }

        public void execute(int begin, int end) {
            this.mBegin = begin;
            this.mEnd = end;
            myTime0 = System.currentTimeMillis();
            myTime = myTime0;
            type = 0;
            super.execute();
        }

        @Override
        protected Void doInBackground() {
            myGameView view = mViewReference2.get();
            if (view == null) return null;

            try {
                int n;
                char ch;
                int step = mBegin;
                while (step <= mEnd && step < myMaps.sAction.length) {
                    if (isCancelled()) return null;

                    view.mMap.myMacroInf = "";
                    if (myMaps.sAction[step].isEmpty()) {
                        step++;
                        if (view.mMap.myMacro.get(view.mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                            view.mMap.myMacro.add(step);
                        }
                        continue;
                    }

                    ch = myMaps.sAction[step].charAt(0);
                    view.m_bBusing = false;
                    if (ch == '*') {
                        step = view.myDo_Loop(step);
                    } else {
                        n = view.myDo_Line(myMaps.sAction[step], false, false);
                        if (n == -1) {
                            step++;
                        } else if (n < -1) {
                            return null;
                        } else {
                            step = n;
                        }
                    }

                    if (!view.bt_BK.isChecked()) {
                        if (view.mMap.myMacro.get(view.mMap.myMacro.size() - 1) < myMaps.sAction.length) {
                            view.mMap.myMacro.add(step);
                        }
                        if (myMaps.isMacroDebug) break;
                    }
                    if (System.currentTimeMillis() - myTime > mySpeed[myMaps.m_Sets[10]]) {
                        myTime = System.currentTimeMillis();
                        publish();
                    }
                }
                if (myMaps.curMap != null && myMaps.curMap.Level_id > 0) {
                    if (view.bt_BK.isChecked()) {
                        if (view.myClearance2()) {
                            view.zhengniHE2();
                            view.saveAns2();
                            type = 3;
                        } else if (view.myMeet()) {
                            view.zhengniHE();
                            view.saveAns2();
                            type = 2;
                        }
                    } else {
                        if (view.myClearance()) {
                            view.saveAns(1);
                            type = 1;
                        } else if (view.myMeet()) {
                            view.zhengniHE();
                            view.saveAns2();
                            type = 2;
                        }
                    }
                }
            } catch (Throwable ex) { }
            return null;
        }

        @Override
        protected void process(java.util.List<Void> chunks) {
            myGameView view = mViewReference2.get();
            if (view != null) view.mMap.invalidate();
        }

        @Override
        protected void done() {
            myGameView view = mViewReference2.get();
            if (view != null) {
                view.mMap.invalidate();
                view.m_bBusing = false;
                myMaps.m_ActionIsRedy = false;
                MyToast.showToast(view, msg[type] + "耗时 " + ((myTime - myTime0) / 1000) + " 秒", MyToast.LENGTH_SHORT);
                if (!view.m_lstMovReDo.isEmpty()) {
                    view.m_lstMovReDo.clear();
                }
                view.StopMicro();
            }
        }
    }
}
