package my.boxman;

import my.boxman.compat.UiWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Comparator;

/**
 * 「相似关卡对比」窗口 —— Android {@code myFindView} Activity 的 1:1 移植。
 *
 * <p><b>ActionBar</b>：{@code setTitle(myMaps.sFile)}（关卡集名）+ 返回键。
 * 菜单来自 {@code res/menu/find.xml}，8 项：
 * <ul>
 *   <li>{@code find_pre}「上一关」、{@code find_next}「下一关」都是
 *       {@code showAsAction="always"} → ActionBar 上的文字按钮。</li>
 *   <li>溢出 6 项：转动关卡 / 似源切换 / 解关答案... / 关卡全貌(可勾选) / 关于... / 操作说明。</li>
 * </ul>
 *
 * <p><b>改写前 PC 版只有 120 行</b>，自造了一条「关卡操作」菜单（3 项，标题全不对），
 * 缺 {@code getLevel_inf()} 的**8 转相似度比对**、缺 {@code myCompare()} 的精确相似度算法、
 * 缺 {@code m_Set_Pos1/m_Set_Pos2} 位置信息、缺「上一关 / 下一关 / 解关答案 / 关于」。
 * 这里把控制器补齐；关卡图的绘制与交互见 {@link myFindViewMap}。
 */
public class myFindView extends JFrame {

    public myFindViewMap mMap;  // 地图

    public char[][] m_cArray1;  // 源关卡 -- 全貌
    public char[][] m_cArray2;  // 相似关卡 -- 全貌
    public char[][] m_cArray3;  // 源关卡 -- 标准化
    public char[][] m_cArray4;  // 相似关卡 -- 标准化

    /** 当前显示的关卡是否为源关卡（原版默认 false = 相似关卡） */
    public boolean m_Level = false;

    String m_Set_Pos1;  // 源关卡所属关卡集位置信息
    String m_Set_Pos2;  // 相似关卡所属关卡集位置信息
    public int Rows1, Cols1, Rows2, Cols2, Rows3, Cols3, Rows4, Cols4;  // 关卡原貌及标准化关卡的原始尺寸

    public int mTrun;        // 源关卡 n 转相似度最高
    public int mSimilarity;  // 精准相似度设定
    public int[][] mSelect = new int[2][4];  // 相似区域，用于绘制关卡图中的相似区域方框

    private myActionBar actionBar;

    public myFindView() {
        setTitle(myMaps.sFile != null ? myMaps.sFile : "");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);

        // 原版进入本界面前，myGridView 一定会先把源关卡塞进 myMaps.oldMap
        // （myGridView.java:1614~1618）。PC 侧目前还没有那条路径，这里兜底成「自己跟自己比」，
        // 否则 oldMap 为 null 会直接 NPE。
        if (myMaps.oldMap == null) {
            myMaps.oldMap = myMaps.curMap;
        }

        if (myMaps.curMap != null) {
            getLevel_inf();   // 取得两个关卡，及其位置信息
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 原版：setTitle(myMaps.sFile) + setDisplayHomeAsUpEnabled(true) + setDisplayShowHomeEnabled(false)
        actionBar = new myActionBar();
        actionBar.setBarTitle(myMaps.sFile != null ? myMaps.sFile : "");
        actionBar.setUpEnabled(true, this::dispose);
        // find.xml 的 find_pre / find_next 是 showAsAction="always"
        actionBar.addBarAction("上一关", () -> moveLevel(-1));
        actionBar.addBarAction("下一关", () -> moveLevel(1));
        // 溢出 6 项
        actionBar.addAction("转动关卡", this::myTrun);
        actionBar.addAction("似源切换", this::myLevel);
        actionBar.addAction("解关答案...", this::showSolution);
        actionBar.addAction("关卡全貌", this::toggleLevelAll);
        actionBar.addAction("关于...", () -> new myAbout2(this, myMaps.curMap).setVisible(true));
        actionBar.addAction("操作说明", () -> new Help(3).setVisible(true));
        actionBar.setActionChecked("关卡全貌", false);

        add(actionBar, BorderLayout.NORTH);

        mMap = new myFindViewMap();
        mMap.Init(this);
        add(mMap, BorderLayout.CENTER);
    }

    /** 原版 {@code find_level_all} 的可勾选状态跟着地图视图走。 */
    private void syncLevelAllMenu() {
        if (actionBar != null) actionBar.setActionChecked("关卡全貌", mMap.m_Level_All);
    }

    // ================================================================ 关卡资料

    /** 取得关卡相关资料（原版 {@code getLevel_inf()}）。 */
    private void getLevel_inf() {
        try {
            // 重新计算比较精确的相似度  ****************
            String[] Arr = myMaps.oldMap.Map0.split("\r\n|\n\r|\n|\r|\\|");
            Rows1 = Arr.length;
            Cols1 = Arr[0].length();

            // 源关卡的 8 次旋转
            char[][][] m_cAry0 = new char[4][Rows1][Cols1];
            char[][][] m_cAry1 = new char[4][Cols1][Rows1];
            char ch;
            for (int r = 0; r < Rows1; r++) {
                for (int c = 0; c < Cols1; c++) {
                    ch = Arr[r].charAt(c);
                    if (ch == '_' || ch == ' ') ch = '-';
                    m_cAry0[0][r][c] = ch;                   // 0 转
                    m_cAry1[0][c][Rows1 - 1 - r] = ch;       // 1 转
                    m_cAry0[1][Rows1 - 1 - r][Cols1 - 1 - c] = ch;  // 2 转
                    m_cAry1[1][Cols1 - 1 - c][r] = ch;       // 3 转
                    m_cAry0[2][r][Cols1 - 1 - c] = ch;       // 4 转
                    m_cAry1[2][Cols1 - 1 - c][Rows1 - 1 - r] = ch;  // 5 转
                    m_cAry0[3][Rows1 - 1 - r][c] = ch;       // 6 转
                    m_cAry1[3][c][r] = ch;                   // 7 转
                }
            }

            // 相似关卡的标准化 XSB  ********************
            Arr = myMaps.curMap.Map0.split("\r\n|\n\r|\n|\r|\\|");
            Rows2 = Arr.length;
            Cols2 = Arr[0].length();

            // 相似关卡的 0 转
            char[][] m_cAry2 = new char[Rows2][Cols2];
            for (int r = 0; r < Rows2; r++) {
                for (int c = 0; c < Cols2; c++) {
                    ch = Arr[r].charAt(c);
                    if (ch == '@' || ch == '_' || ch == ' ') ch = '-';
                    else if (ch == '+') ch = '.';

                    m_cAry2[r][c] = ch;
                }
            }

            // 计算两个关卡的精确相似度及相似区域  ********************
            mSimilarity = 0;
            int[][] sel = new int[2][4];  // 记录两个关卡的相似区域

            // 原版是 8 段几乎一样的代码，转序依次是 0,2,4,6,1,3,5,7（偶数转用 m_cAry0、奇数转用 m_cAry1）
            int[] trunOrder = {0, 2, 4, 6, 1, 3, 5, 7};
            for (int k = 0; k < trunOrder.length; k++) {
                int t = trunOrder[k];
                boolean odd = (t % 2 == 1);
                char[][] src = odd ? m_cAry1[t / 2] : m_cAry0[t / 2];
                int r1 = odd ? Cols1 : Rows1;
                int c1 = odd ? Rows1 : Cols1;

                int sim = myCompare(src, r1, c1, m_cAry2, Rows2, Cols2, sel);
                if (mSimilarity < sim) {
                    mTrun = t;
                    mSimilarity = sim;
                    // 相似区域的坐标调整到 0 转坐标
                    for (int i = 0; i < 2; i++) {
                        System.arraycopy(sel[i], 0, mSelect[i], 0, 4);
                    }
                }
            }

            // 源关卡  ******************
            if (myMaps.oldMap.P_id >= 0) {  // 创编关卡，不需要记录打开时间
                mySQLite.m_SQL.Set_L_DateTime(myMaps.oldMap.Level_id);  // 记录源关卡打开时间
            }

            if (myMaps.oldMap.P_id < 0) {
                m_Set_Pos1 = "关卡集：创编关卡，序号：" + myMaps.oldMap.Num;  // 源关卡所属关卡集位置信息
            } else {
                String m_Set_Name1 = myMaps.getSetTitle(myMaps.oldMap.P_id);  // 源关卡所属关卡集
                int m_Level_Num1 = mySQLite.m_SQL.get_Level_Num(myMaps.oldMap.P_id, myMaps.oldMap.Level_id);  // 源关卡在所属关卡集中的序号
                m_Set_Pos1 = "关卡集：" + m_Set_Name1 + "，序号：" + m_Level_Num1;
            }

            // 源关卡全貌
            Arr = myMaps.oldMap.Map.split("\r\n|\n\r|\n|\r|\\|");
            Rows1 = Arr.length;
            Cols1 = Arr[0].length();

            if (mTrun % 2 == 0) m_cArray1 = new char[Rows1][Cols1];
            else m_cArray1 = new char[Cols1][Rows1];
            for (int r = 0; r < Rows1; r++) {
                for (int c = 0; c < Cols1; c++) {
                    switch (mTrun) {
                        case 0: m_cArray1[r][c] = Arr[r].charAt(c); break;
                        case 1: m_cArray1[c][Rows1 - 1 - r] = Arr[r].charAt(c); break;
                        case 2: m_cArray1[Rows1 - 1 - r][Cols1 - 1 - c] = Arr[r].charAt(c); break;
                        case 3: m_cArray1[Cols1 - 1 - c][r] = Arr[r].charAt(c); break;
                        case 4: m_cArray1[r][Cols1 - 1 - c] = Arr[r].charAt(c); break;
                        case 5: m_cArray1[Cols1 - 1 - c][Rows1 - 1 - r] = Arr[r].charAt(c); break;
                        case 6: m_cArray1[Rows1 - 1 - r][c] = Arr[r].charAt(c); break;
                        case 7: m_cArray1[c][r] = Arr[r].charAt(c); break;
                        default: break;
                    }
                }
            }
            if (mTrun % 2 == 1) {
                int n = Rows1;
                Rows1 = Cols1;
                Cols1 = n;
            }

            // 原关卡标准化
            switch (mTrun) {
                case 0: m_cArray3 = m_cAry0[0]; break;
                case 1: m_cArray3 = m_cAry1[0]; break;
                case 2: m_cArray3 = m_cAry0[1]; break;
                case 3: m_cArray3 = m_cAry1[1]; break;
                case 4: m_cArray3 = m_cAry0[2]; break;
                case 5: m_cArray3 = m_cAry1[2]; break;
                case 6: m_cArray3 = m_cAry0[3]; break;
                case 7: m_cArray3 = m_cAry1[3]; break;
                default: break;
            }
            Rows3 = m_cArray3.length;
            Cols3 = m_cArray3[0].length;

            // 相似关卡  *******************
            if (myMaps.oldMap.P_id >= 0) {  // 答案表中的关卡，不需要记录打开时间
                mySQLite.m_SQL.Set_L_DateTime(myMaps.curMap.Level_id);  // 记录相似关卡打开时间
            }

            String m_Set_Name2 = mySQLite.m_SQL.getSetName(myMaps.curMap.P_id);  // 相似关卡所属关卡集
            if (myMaps.curMap.P_id >= 0) {
                int m_Level_Num2 = mySQLite.m_SQL.get_Level_Num(myMaps.curMap.P_id, myMaps.curMap.Level_id);  // 相似关卡在所属关卡集中的序号
                m_Set_Pos2 = "关卡集：" + m_Set_Name2 + "，序号：" + m_Level_Num2;
            } else {  // 答案表中的关卡，没有序号
                m_Set_Pos2 = "关卡集：无，自由关卡";
            }

            // 相似关卡全貌
            Arr = myMaps.curMap.Map.split("\r\n|\n\r|\n|\r|\\|");
            Rows2 = Arr.length;
            Cols2 = Arr[0].length();

            m_cArray2 = new char[Rows2][Cols2];
            for (int r = 0; r < Rows2; r++) {
                for (int c = 0; c < Cols2; c++) {
                    m_cArray2[r][c] = Arr[r].charAt(c);
                }
            }

            // 标准化 XSB -- 可看到相似区域
            Arr = myMaps.curMap.Map0.split("\r\n|\n\r|\n|\r|\\|");
            Rows4 = Arr.length;
            Cols4 = Arr[0].length();

            m_cArray4 = new char[Rows4][Cols4];
            for (int r = 0; r < Rows4; r++) {
                for (int c = 0; c < Cols4; c++) {
                    m_cArray4[r][c] = Arr[r].charAt(c);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            dispose();
            MyToast.showToast(this, "关卡数据不完整！", MyToast.LENGTH_SHORT);
        } catch (Exception e) {
            dispose();
            MyToast.showToast(this, "关卡数据不完整！", MyToast.LENGTH_SHORT);
        }

        mMap.m_Level_All = false;
        syncLevelAllMenu();

        myMaps.m_nTrun = 0;
        m_Level = false;  // 默认显示相似关卡

        applyView();      // 把当前（源/相似 × 全貌/标准化）的数组挂到地图视图上
        mMap.initArena(); // 舞台初始化
    }

    /** 原版 {@code getLevel_inf()} 末尾那段「按 m_Level × m_Level_All 选数组」的逻辑。 */
    private void applyView() {
        if (mMap.m_Level_All) {  // 关卡全貌
            if (m_Level) {
                mMap.m_cArray = m_cArray1;  // 源关卡 -- 全貌
                mMap.m_nRows = Rows1;
                mMap.m_nCols = Cols1;
            } else {
                mMap.m_cArray = m_cArray2;  // 相似关卡 -- 全貌
                mMap.m_nRows = Rows2;
                mMap.m_nCols = Cols2;
            }
        } else {   // 瘦关卡
            if (m_Level) {
                mMap.m_cArray = m_cArray3;  // 源关卡 -- 瘦
                mMap.m_nRows = Rows3;
                mMap.m_nCols = Cols3;
            } else {
                mMap.m_cArray = m_cArray4;  // 相似关卡 -- 瘦
                mMap.m_nRows = Rows4;
                mMap.m_nCols = Cols4;
            }
        }
    }

    /**
     * 计算精准相似度（原版 {@code myCompare}）。逐行照抄，包括四处几乎一样的
     * 「Rows1 与 Rows2 谁大 / Cols1 与 Cols2 谁大」的分支与 {@code Sel} 的写入口径。
     */
    int myCompare(char[][] mAry1, int Rows1, int Cols1, char[][] mAry2, int Rows2, int Cols2, int[][] Sel) {
        int m, n, dR1, dC1, dR2, dC2;
        int[] mLeast_Similarity = {100, 95, 90, 85, 80, 75, 66, 50};  // 需参照：myGridView.onContextItemSelected() 中的定义

        if (Rows1 < Rows2) m = Rows1;
        else m = Rows2;
        if (Cols1 < Cols2) n = Cols1;
        else n = Cols2;

        // 参加对比的格子不够数，不需比较
        if ((int) Math.ceil((double) m * n * 100 / (Rows1 * Cols1)) < mLeast_Similarity[myMaps.m_Sets[26]]) return 0;

        dR1 = Rows1 - Rows2;
        dC1 = Cols1 - Cols2;
        dR2 = Rows2 - Rows1;
        dC2 = Cols2 - Cols1;

        m = 0;  // 记录相同的格子总数的最大值
        char ch;
        if (Rows1 < Rows2) {
            if (Cols1 < Cols2) {
                for (int i = 0; i <= dR2; i++) {
                    for (int j = 0; j <= dC2; j++) {
                        n = 0;
                        for (int r = 0; r < Rows1; r++) {
                            for (int c = 0; c < Cols1; c++) {
                                ch = mAry1[r][c];
                                if (ch == '@') ch = '-';
                                else if (ch == '+') ch = '.';
                                if (ch == mAry2[r + i][c + j]) n++;
                            }
                        }
                        if (m < n) {
                            m = n;
                            Sel[0][0] = 0;
                            Sel[0][1] = 0;
                            Sel[0][2] = Rows1 - 1;
                            Sel[0][3] = Cols1 - 1;
                            Sel[1][0] = i;
                            Sel[1][1] = j;
                            Sel[1][2] = i + Rows1 - 1;
                            Sel[1][3] = j + Cols1 - 1;
                        }
                    }
                }
            } else {
                for (int i = 0; i <= dR2; i++) {
                    for (int j = 0; j <= dC1; j++) {
                        n = 0;
                        for (int r = 0; r < Rows1; r++) {
                            for (int c = 0; c < Cols2; c++) {
                                ch = mAry1[r][c + j];
                                if (ch == '@') ch = '-';
                                else if (ch == '+') ch = '.';
                                if (ch == mAry2[r + i][c]) n++;
                            }
                        }
                        if (m < n) {
                            m = n;
                            Sel[0][0] = 0;
                            Sel[0][1] = j;
                            Sel[0][2] = Rows1 - 1;
                            Sel[0][3] = j + Cols2 - 1;
                            Sel[1][0] = i;
                            Sel[1][1] = 0;
                            Sel[1][2] = i + Rows1 - 1;
                            Sel[1][3] = Cols2 - 1;
                        }
                    }
                }
            }
        } else {
            if (Cols1 < Cols2) {
                for (int i = 0; i <= dR1; i++) {
                    for (int j = 0; j <= dC2; j++) {
                        n = 0;
                        for (int r = 0; r < Rows2; r++) {
                            for (int c = 0; c < Cols1; c++) {
                                ch = mAry1[r + i][c];
                                if (ch == '@') ch = '-';
                                else if (ch == '+') ch = '.';
                                if (ch == mAry2[r][c + j]) n++;
                            }
                        }
                        if (m < n) {
                            m = n;
                            Sel[0][0] = i;
                            Sel[0][1] = 0;
                            Sel[0][2] = i + Rows2 - 1;
                            Sel[0][3] = Cols1 - 1;
                            Sel[1][0] = 0;
                            Sel[1][1] = j;
                            Sel[1][2] = Rows2 - 1;
                            Sel[1][3] = j + Cols1 - 1;
                        }
                    }
                }
            } else {
                for (int i = 0; i <= dR1; i++) {
                    for (int j = 0; j <= dC1; j++) {
                        n = 0;
                        for (int r = 0; r < Rows2; r++) {
                            for (int c = 0; c < Cols2; c++) {
                                ch = mAry1[r + i][c + j];
                                if (ch == '@') ch = '-';
                                else if (ch == '+') ch = '.';
                                if (ch == mAry2[r][c]) n++;
                            }
                        }
                        if (m < n) {
                            m = n;
                            Sel[0][0] = i;
                            Sel[0][1] = j;
                            Sel[0][2] = i + Rows2 - 1;
                            Sel[0][3] = j + Cols2 - 1;
                            Sel[1][0] = 0;
                            Sel[1][1] = 0;
                            Sel[1][2] = Rows2 - 1;
                            Sel[1][3] = Cols2 - 1;
                        }
                    }
                }
            }
        }

        return (int) Math.floor((double) m * 100 / (Rows1 * Cols1));
    }

    // ================================================================ 菜单动作

    /** 原版 {@code find_pre} / {@code find_next}：在同关卡集里前后翻。 */
    private void moveLevel(int delta) {
        if (myMaps.m_lstMaps == null || myMaps.curMap == null) return;
        int n = myMaps.m_lstMaps.indexOf(myMaps.curMap);
        int t = n + delta;
        if (delta < 0) {
            if (n > 0) myLoadLevel(t);
        } else {
            if (n >= 0 && n + 1 < myMaps.m_lstMaps.size()) myLoadLevel(t);
        }
    }

    /** 原版 {@code find_solution}：切到相似关卡后看它的答案。 */
    private void showSolution() {
        if (!m_Level && myMaps.curMap != null && myMaps.curMap.Solved) {  // 相似关卡，且有答案
            mySQLite.m_SQL.load_SolitionList(myMaps.curMap.key);            // 加载答案列表
            myMaps.curMap.Solved = (myMaps.mState2.size() > 0);             // 修正相似关卡预览图之是否有答案

            Collections.sort(myMaps.mState1, new Comparator<state_Node>() {
                @Override
                public int compare(state_Node o1, state_Node o2) {
                    return o2.time.compareTo(o1.time);
                }
            });
            myMaps.m_StateIsRedy = false;
            new mySolutionBrow(this).setVisible(true);
        } else {
            if (myMaps.curMap != null && myMaps.curMap.Solved) {
                MyToast.showToast(this, "请先切换到相似关卡！", MyToast.LENGTH_SHORT);
            } else {
                MyToast.showToast(this, "相似关卡也没有答案！", MyToast.LENGTH_SHORT);
            }
        }
    }

    /** 原版 {@code find_level_all}：切换「关卡全貌 / 标准化」，并同步菜单勾选。 */
    public void toggleLevelAll() {
        mMap.m_Level_All = !mMap.m_Level_All;
        syncLevelAllMenu();
        applyView();
        mMap.initArena();
        mMap.repaint();
    }

    /** 原版 {@code myLevel()}：切换源关卡与相似关卡。 */
    public void myLevel() {
        m_Level = !m_Level;
        applyView();
        // 舞台初始化
        mMap.initArena();
        mMap.repaint();
    }

    /** 原版 {@code myTrun()}：0 转 ↔ 1 转。 */
    public void myTrun() {
        myMaps.m_nTrun = (myMaps.m_nTrun + 1) % 2;
        mMap.initArena();
        mMap.repaint();
    }

    /** 原版 {@code myLoadLevel(int)}：加载新的相似关卡。 */
    private void myLoadLevel(int n) {
        myMaps.curMap = null;
        myMaps.curMap = myMaps.m_lstMaps.get(n);
        getLevel_inf();  // 取得两个关卡，及其位置信息
    }

    // ================================================================ 键盘

    /** 原版 {@code onKeyDown}：BACK 关闭；开关项 15 打开时音量键前后翻关。 */
    public void handleKeyDown(int keyCode) {
        if (keyCode == KeyEvent.VK_ESCAPE) {
            dispose();
        } else if (myMaps.m_Sets[15] == 1 && keyCode == KeyEvent.VK_PAGE_UP) {
            moveLevel(-1);
        } else if (myMaps.m_Sets[15] == 1 && keyCode == KeyEvent.VK_PAGE_DOWN) {
            moveLevel(1);
        }
    }

    // ================================================================ 测试钩子

    public myActionBar getActionBar() { return actionBar; }

    public boolean isSourceLevel() { return m_Level; }

    /** 供测试注入两个关卡（不碰数据库、不弹提示）。 */
    public void loadForTest(mapNode source, mapNode similar) {
        myMaps.oldMap = source;
        myMaps.curMap = similar;
        getLevel_inf();
    }
}
