package my.boxman;

import my.boxman.compat.HoloProgressDialog;
import my.boxman.compat.sqlite.Cursor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * 「查找相似关卡」引擎 —— 原版 {@code myFindFragment}
 * （{@code DialogFragment} + {@code AsyncTask} + {@code ProgressDialog}）的 1:1 移植。
 *
 * <p>由 {@link myGridView} 的上下文菜单第 11 项「查找相似关卡」发起。搜索主体逐行照搬原版
 * {@code FindTask.doInBackground()}：
 *
 * <ol>
 *   <li><b>源关卡的 8 次旋转</b>：把 {@code myMaps.oldMap.Map0}（标准化 XSB）按
 *       0/1/2/3/4/5/6/7 转展开成 {@code m_cAry0}（偶数转，Rows×Cols）与
 *       {@code m_cAry1}（奇数转，Cols×Rows）。注意 {@code '@'/'_'/' ' → '-'}、
 *       {@code '+' → '.'}，<b>箱子和目标点保留</b>（源关卡这一步不归一化箱子）。</li>
 *   <li><b>关卡表</b>：{@code G_Level}（若指定了关卡集则先建临时表 {@code id_T} 再按
 *       {@code P_id in (select P_id from id_T)} 过滤），逐条读 {@code L_thin_XSB}，
 *       归一化后与源关卡的 8 个旋转各比一次 {@link #myCompare}，取最高相似率。</li>
 *   <li><b>答案库</b>（{@code m_Ans} 时）：{@code G_State} 里
 *       {@code G_Solution = 1 and P_Key_Num >= 0 and P_Key not in (select L_Key from G_Level)}，
 *       {@code group by P_Key}。</li>
 * </ol>
 *
 * <p>{@code m_Sort} 决定两条完全不同的代码路径（原版就是两段几乎重复的代码，这里保留）：
 * <ul>
 *   <li>{@code true}：8 个旋转各跑一遍完整的重叠区「晃动」扫描，把<b>最大相同格子数</b>
 *       折算成相似率存进 {@code mapNode.Num}，最后按 {@code Num} 降序排序。</li>
 *   <li>{@code false}：只要任一旋转的相同格子数达到 {@code mLeast} 就立即收录，
 *       相似率记为 0，不排序。</li>
 * </ul>
 *
 * <p>原版用 {@code publishProgress()} 更新进度对话框文字；这里换成
 * {@link SwingWorker#publish(Object)} + {@link HoloProgressDialog}（与
 * {@link myQueryFragment} 同一套写法）。取消语义也照搬：取消时把<b>已查到的部分结果</b>
 * 回调出去（原版 {@code stopFind()}）。
 */
public class myFindFragment {

    /** 原版 {@code myFindFragment.FindStatusUpdate}：向上层回传查找结果。 */
    public interface FindStatusUpdate {
        void onFindDone(ArrayList<mapNode> mlMaps);
    }

    private final Frame owner;
    private final FindStatusUpdate statusUpdate;

    private final int mSimilarity0;   // 相似度（百分数）
    private final long[] m_sets;      // 关卡集 id 数组；null 表示全选
    private final boolean m_Ans;      // 是否搜索答案库
    private final boolean m_Sort;     // 搜索后是否按相似度排序
    private final boolean m_IgnoreBox;// 比较时忽略箱子和人

    private ArrayList<mapNode> m_Map_List;
    private HoloProgressDialog dialog;
    private SwingWorker<ArrayList<mapNode>, String> worker;
    private volatile boolean cancelled;

    public myFindFragment(Frame owner, FindStatusUpdate statusUpdate,
                          long[] sets, int similarity, boolean ans, boolean sort, boolean ignoreBox) {
        this.owner = owner;
        this.statusUpdate = statusUpdate;
        this.m_sets = sets;
        this.mSimilarity0 = similarity;
        this.m_Ans = ans;
        this.m_Sort = sort;
        this.m_IgnoreBox = ignoreBox;
    }

    /** 原版 {@code mDialog.show(getFragmentManager(), ...)}：弹进度框并开始查找。 */
    public void show() {
        dialog = new HoloProgressDialog(owner, "查找中...");
        dialog.setOnCancel(this::stopFind);
        worker = new SwingWorker<ArrayList<mapNode>, String>() {
            @Override
            protected ArrayList<mapNode> doInBackground() {
                // SwingWorker.publish 是 protected，只能在子类里取方法引用
                return find(this::publish);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty() && dialog != null && dialog.isVisible()) {
                    dialog.setMessage(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                ArrayList<mapNode> result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = m_Map_List;
                }
                if (dialog != null) {
                    dialog.dispose();
                    dialog = null;
                }
                if (statusUpdate != null) {
                    statusUpdate.onFindDone(result);
                }
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    /** 原版 {@code FindTask.stopFind()}：取消任务，并把已有结果回调出去。 */
    public void stopFind() {
        cancelled = true;
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
    }

    /**
     * 同步执行查找，不开进度框 —— 给测试与无界面场景用。
     * 逻辑与 {@link #show()} 完全相同，只是省掉 SwingWorker / ProgressDialog 那一层。
     */
    public ArrayList<mapNode> runNow() {
        return find(text -> { });
    }

    // ------------------------------------------------------------------ 查找主体

    private ArrayList<mapNode> find(java.util.function.Consumer<String> sink) {
        m_Map_List = new ArrayList<>();

        char[][][] m_cAry0;
        char[][][] m_cAry1;
        int Rows, Cols, mLeast;

        try {
            // 源关卡
            String[] Arr = myMaps.oldMap.Map0.split("\r\n|\n\r|\n|\r|\\|");
            Rows = Arr.length;
            Cols = Arr[0].length();

            // 检查 XSB 是否规整
            if (Rows < 3 || Cols < 3) {
                return m_Map_List;
            }
            for (int r = 1; r < Rows; r++) {
                if (Arr[r].length() != Cols) {
                    return m_Map_List;
                }
            }

            // 源关卡的 8 次旋转
            m_cAry0 = new char[4][Rows][Cols];
            m_cAry1 = new char[4][Cols][Rows];
            char ch;
            for (int r = 0; r < Rows; r++) {
                for (int c = 0; c < Cols; c++) {
                    ch = Arr[r].charAt(c);
                    if (ch == '@' || ch == '_' || ch == ' ') ch = '-';
                    else if (ch == '+') ch = '.';
                    m_cAry0[0][r][c] = ch;                   // 0 转
                    m_cAry0[1][Rows - 1 - r][Cols - 1 - c] = ch;  // 2 转
                    m_cAry0[2][r][Cols - 1 - c] = ch;        // 4 转
                    m_cAry0[3][Rows - 1 - r][c] = ch;        // 6 转
                    m_cAry1[0][c][Rows - 1 - r] = ch;        // 1 转
                    m_cAry1[1][Cols - 1 - c][r] = ch;        // 3 转
                    m_cAry1[2][Cols - 1 - c][Rows - 1 - r] = ch;  // 5 转
                    m_cAry1[3][c][r] = ch;                   // 7 转
                }
            }

            // 达到相似要求的最少格子数
            int mTotal = Rows * Cols;  // 源关卡的格子总数
            mLeast = (mSimilarity0 == 100 ? mTotal
                    : (int) Math.floor((double) mTotal * mSimilarity0 / 100));
        } catch (Exception e) {
            return m_Map_List;
        }

        // ---------------------------- 搜索关卡表中的相似关卡
        Cursor cursor = null;
        try {
            cursor = mySQLite.m_SQL.mSDB.rawQuery("PRAGMA synchronous=OFF", null);
            try {
                if (m_sets == null) {
                    cursor = mySQLite.m_SQL.mSDB.rawQuery("select * from G_Level", null);
                } else {
                    mySQLite.m_SQL.new_tmp_Table2();  // 创建临时表，用于相似查找和查询
                    for (long setId : m_sets) {
                        mySQLite.m_SQL.add_Set_ID(setId);
                    }
                    cursor = mySQLite.m_SQL.mSDB.rawQuery(
                            "select * from G_Level where P_id in (select P_id from id_T)", null);
                }

                long n = 0;
                long mCount = -1;
                while (cursor.moveToNext()) {
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    String[] Arr2;
                    try {
                        long p_id = cursor.getLong(cursor.getColumnIndex("P_id"));
                        long l_id2 = cursor.getLong(cursor.getColumnIndex("L_id"));
                        String Title0 = cursor.getString(cursor.getColumnIndex("L_Title"));
                        String Author0 = cursor.getString(cursor.getColumnIndex("L_Author"));
                        String Map0 = cursor.getString(cursor.getColumnIndex("L_thin_XSB"));  // 标准化关卡

                        n++;  // 搜索进度
                        if (mCount < 0) mCount = cursor.getCount();
                        sink.accept("查找中... " + n + "/" + mCount + '\n'
                                + myMaps.getSetTitle(p_id) + '\n' + Title0 + '\n' + Author0);

                        if (myMaps.oldMap.Level_id == l_id2) continue;

                        Arr2 = Map0.split("\r\n|\n\r|\n|\r|\\|");
                    } catch (Exception e) {
                        continue;
                    }

                    int Rows2 = Arr2.length;
                    int Cols2 = Arr2[0].length();

                    // 检查 XSB 是否规整
                    if (Rows2 < 3 || Cols2 < 3) continue;
                    boolean flg = false;
                    for (int r = 1; r < Rows2; r++) {
                        if (Arr2[r].length() != Cols2) {
                            flg = true;
                            break;
                        }
                    }
                    if (flg) continue;

                    // DB 中的关卡
                    char[][] m_cAry2 = normalize(Arr2, Rows2, Cols2);
                    compareAndCollect(cursor, m_cAry0, m_cAry1, Rows, Cols, m_cAry2, Rows2, Cols2, mLeast, false);
                }
            } catch (Exception e) {
                // 原版如此
            } finally {
                if (cursor != null) cursor.close();
                mySQLite.m_SQL.del_tmp_Table2();  // 清理暂存关卡集 ID 临时表
            }

            if (!m_Ans) {
                return m_Map_List;  // 若不搜索答案库，结束任务
            }

            // ---------------------------- 搜索答案表中的相似关卡
            cursor = null;
            try {
                long n = 0;
                long mCount = -1;
                cursor = mySQLite.m_SQL.mSDB.rawQuery(
                        "select P_Key, P_Key_Num, L_thin_XSB, S_id from G_State"
                                + " where G_Solution = 1 and P_Key_Num >= 0"
                                + " and P_Key not in (select L_Key from G_Level) group by P_Key", null);
                while (cursor.moveToNext()) {
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    n++;  // 搜索进度
                    if (mCount < 0) mCount = cursor.getCount();
                    sink.accept("查找中... " + n + "/" + mCount + "\n答案库");

                    String[] Arr2;
                    try {
                        String Map0 = cursor.getString(cursor.getColumnIndex("L_thin_XSB"));  // 标准化关卡
                        Arr2 = Map0.split("\r\n|\n\r|\n|\r|\\|");
                    } catch (Exception e) {
                        continue;
                    }

                    int Rows2 = Arr2.length;
                    int Cols2 = Arr2[0].length();

                    if (Rows2 < 3 || Cols2 < 3) continue;
                    boolean flg = false;
                    for (int r = 1; r < Rows2; r++) {
                        if (Arr2[r].length() != Cols2) {
                            flg = true;
                            break;
                        }
                    }
                    if (flg) continue;

                    char[][] m_cAry2 = normalize(Arr2, Rows2, Cols2);
                    compareAndCollect(cursor, m_cAry0, m_cAry1, Rows, Cols, m_cAry2, Rows2, Cols2, mLeast, true);
                }
            } catch (Exception e) {
                // 原版如此
            } finally {
                if (cursor != null) cursor.close();
            }
        } finally {
            // nothing
        }

        if (m_Sort) {  // 若结果需要排序
            Collections.sort(m_Map_List, new Comparator<mapNode>() {
                @Override
                public int compare(mapNode nd1, mapNode nd2) {
                    if (nd1.Num > nd2.Num) return -1;
                    else if (nd1.Num < nd2.Num) return 1;
                    else return 0;
                }
            });
        }
        return m_Map_List;
    }

    /** 把 XSB 的每一行归一化成 char[][]（{@code '@'/'_'/' ' → '-'}、{@code '+' → '.'}）。 */
    private static char[][] normalize(String[] arr, int rows, int cols) {
        char[][] m = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = arr[r].charAt(c);
                if (ch == '@' || ch == '_' || ch == ' ') ch = '-';
                else if (ch == '+') ch = '.';
                m[r][c] = ch;
            }
        }
        return m;
    }

    /**
     * 对一条候选关卡跑源关卡的 8 个旋转，达到要求就按 {@code m_Sort} 的分支收录。
     *
     * @param fromAnswerLib {@code true} 表示这条来自答案表（{@code G_State}），
     *                      收录时 {@code P_id = -1}、{@code L_id} 取 {@code S_id}、标题/作者/说明为空
     */
    private void compareAndCollect(Cursor cursor, char[][][] m_cAry0, char[][][] m_cAry1,
                                   int Rows, int Cols, char[][] m_cAry2,
                                   int Rows2, int Cols2, int mLeast, boolean fromAnswerLib) {
        if (m_Sort) {
            int mSimilarity1 = 0, mSimilarity2;

            mSimilarity2 = myCompare(mLeast, m_cAry0[0], Rows, Cols, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry0[1], Rows, Cols, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry0[2], Rows, Cols, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry0[3], Rows, Cols, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry1[0], Cols, Rows, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry1[1], Cols, Rows, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry1[2], Cols, Rows, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;
            mSimilarity2 = myCompare(mLeast, m_cAry1[3], Cols, Rows, m_cAry2, Rows2, Cols2);
            if (mSimilarity2 > mSimilarity1) mSimilarity1 = mSimilarity2;

            if (mSimilarity1 > 0) {  // 说明相似率达到要求
                m_Map_List.add(nodeOf(cursor, fromAnswerLib, mSimilarity1));
            }
        } else {
            if (myCompare(mLeast, m_cAry0[0], Rows, Cols, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry0[1], Rows, Cols, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry0[2], Rows, Cols, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry0[3], Rows, Cols, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry1[0], Cols, Rows, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry1[1], Cols, Rows, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry1[2], Cols, Rows, m_cAry2, Rows2, Cols2) > 0
                    || myCompare(mLeast, m_cAry1[3], Cols, Rows, m_cAry2, Rows2, Cols2) > 0) {
                m_Map_List.add(nodeOf(cursor, fromAnswerLib, 0));
            }
        }
    }

    /** 按来源（关卡表 / 答案表）从游标当前行造一个 {@link mapNode}。 */
    private static mapNode nodeOf(Cursor cursor, boolean fromAnswerLib, int num) {
        if (fromAnswerLib) {
            return new mapNode(num,
                    -1,  // P_id
                    cursor.getLong(cursor.getColumnIndex("S_id")),
                    1,   // 是否有答案
                    cursor.getString(cursor.getColumnIndex("L_thin_XSB")),  // 关卡 XSB
                    "",
                    "",
                    "",
                    cursor.getLong(cursor.getColumnIndex("P_Key")),
                    cursor.getInt(cursor.getColumnIndex("P_Key_Num")),  // 第几转
                    cursor.getString(cursor.getColumnIndex("L_thin_XSB")),  // 标准化关卡
                    0);  // 是否加锁图标
        }
        return new mapNode(num,
                cursor.getLong(cursor.getColumnIndex("P_id")),
                cursor.getLong(cursor.getColumnIndex("L_id")),
                cursor.getInt(cursor.getColumnIndex("L_Solved")),
                cursor.getString(cursor.getColumnIndex("L_Content")),  // 关卡 XSB
                cursor.getString(cursor.getColumnIndex("L_Title")),
                cursor.getString(cursor.getColumnIndex("L_Author")),
                cursor.getString(cursor.getColumnIndex("L_Comment")),
                cursor.getLong(cursor.getColumnIndex("L_Key")),
                cursor.getInt(cursor.getColumnIndex("L_Solution")),  // 第几转
                cursor.getString(cursor.getColumnIndex("L_thin_XSB")),  // 标准化关卡
                cursor.getInt(cursor.getColumnIndex("L_Locked")));  // 是否加锁图标
    }

    /**
     * 计数两个关卡的相同格子数（原版 {@code myCompare}）：把小的那个在大的那个上面
     * 「晃动」，找出重叠区里相同格子总数的<b>最大值</b>。
     *
     * <p>四个分支按「谁大谁小」分成 4 段几乎一样的代码（行大列大 / 行大列小 / 行小列大 /
     * 行小列小），原版就是如此，这里保留同样的形状。
     *
     * @param mLeast 相似率要求的最少相同格子数
     * @return {@code m_Sort} 时返回相似率百分数（{@code floor(m*100/(Rows1*Cols1))}，
     *         未达 {@code mLeast} 则 0）；否则「达标即返回 1，否则 0」
     */
    int myCompare(int mLeast, char[][] mAry1, int Rows1, int Cols1,
                  char[][] mAry2, int Rows2, int Cols2) {
        int m, m2, n, n2, dR1, dC1, dR2, dC2;
        char ch1, ch2;

        if (Rows1 < Rows2) m = Rows1;
        else m = Rows2;
        if (Cols1 < Cols2) n = Cols1;
        else n = Cols2;

        // 参加对比的格子数不够，不需比对
        if (m * n < mLeast) return 0;

        dR1 = Rows1 - Rows2;
        dC1 = Cols1 - Cols2;
        dR2 = Rows2 - Rows1;
        dC2 = Cols2 - Cols1;

        m2 = Rows1 * Cols1 - mLeast;  // 允许的最大不同的格子数
        m = 0;  // 记录相同的格子总数的最大值
        if (Rows1 < Rows2) {
            if (Cols1 < Cols2) {
                for (int i = 0; i <= dR2; i++) {
                    for (int j = 0; j <= dC2; j++) {
                        n = 0;
                        n2 = 0;
                        for (int r = 0; r < Rows1; r++) {
                            for (int c = 0; c < Cols1; c++) {
                                ch1 = mAry1[r][c];
                                ch2 = mAry2[r + i][c + j];
                                if (m_IgnoreBox) {
                                    ch1 = ignoreBox(ch1);
                                    ch2 = ignoreBox(ch2);
                                }
                                if (ch1 == ch2) n++;
                                else n2++;

                                if (m_Sort) {
                                    if (m < n) m = n;
                                } else {
                                    if (n >= mLeast) return 1;
                                }
                                if (n2 > m2) break;
                            }
                            if (n2 > m2) break;
                        }
                    }
                }
            } else {
                for (int i = 0; i <= dR2; i++) {
                    for (int j = 0; j <= dC1; j++) {
                        n = 0;
                        n2 = 0;
                        for (int r = 0; r < Rows1; r++) {
                            for (int c = 0; c < Cols2; c++) {
                                ch1 = mAry1[r][c + j];
                                ch2 = mAry2[r + i][c];
                                if (m_IgnoreBox) {
                                    ch1 = ignoreBox(ch1);
                                    ch2 = ignoreBox(ch2);
                                }
                                if (ch1 == ch2) n++;
                                else n2++;

                                if (m_Sort) {
                                    if (m < n) m = n;
                                } else {
                                    if (n >= mLeast) return 1;
                                }
                                if (n2 > m2) break;
                            }
                            if (n2 > m2) break;
                        }
                    }
                }
            }
        } else {
            if (Cols1 < Cols2) {
                for (int i = 0; i <= dR1; i++) {
                    for (int j = 0; j <= dC2; j++) {
                        n = 0;
                        n2 = 0;
                        for (int r = 0; r < Rows2; r++) {
                            for (int c = 0; c < Cols1; c++) {
                                ch1 = mAry1[r + i][c];
                                ch2 = mAry2[r][c + j];
                                if (m_IgnoreBox) {
                                    ch1 = ignoreBox(ch1);
                                    ch2 = ignoreBox(ch2);
                                }
                                if (ch1 == ch2) n++;
                                else n2++;

                                if (m_Sort) {
                                    if (m < n) m = n;
                                } else {
                                    if (n >= mLeast) return 1;
                                }
                                if (n2 > m2) break;
                            }
                            if (n2 > m2) break;
                        }
                    }
                }
            } else {
                for (int i = 0; i <= dR1; i++) {
                    for (int j = 0; j <= dC1; j++) {
                        n = 0;
                        n2 = 0;
                        for (int r = 0; r < Rows2; r++) {
                            for (int c = 0; c < Cols2; c++) {
                                ch1 = mAry1[r + i][c + j];
                                ch2 = mAry2[r][c];
                                if (m_IgnoreBox) {
                                    ch1 = ignoreBox(ch1);
                                    ch2 = ignoreBox(ch2);
                                }
                                if (ch1 == ch2) n++;
                                else n2++;

                                if (m_Sort) {
                                    if (m < n) m = n;
                                } else {
                                    if (n >= mLeast) return 1;
                                }
                                if (n2 > m2) break;
                            }
                            if (n2 > m2) break;
                        }
                    }
                }
            }
        }
        if (m_Sort && m >= mLeast) {
            return (int) Math.floor((double) m * 100 / (Rows1 * Cols1));
        } else {
            return 0;
        }
    }

    /** 「忽略箱子和人」：{@code '$'/'@' → '-'}、{@code '*'/'+' → '.'}（原版四处内联写法）。 */
    private static char ignoreBox(char ch) {
        if (ch == '$' || ch == '@') return '-';
        if (ch == '*' || ch == '+') return '.';
        return ch;
    }
}
