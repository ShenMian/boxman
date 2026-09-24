package my.boxman;

import my.boxman.compat.HoloProgressDialog;
import my.boxman.compat.sqlite.Cursor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * 原版 {@code myQueryFragment}（{@code DialogFragment} + {@code AsyncTask} + {@code ProgressDialog}）
 * 的等价物。查询逻辑逐行照搬原版的 {@code FindTask.doInBackground()}：
 *
 * <ol>
 *   <li><b>关卡表</b>：{@code G_Level}（若指定了关卡集则先建临时表 {@code id_T} 再按
 *       {@code P_id in (select P_id from id_T)} 过滤），逐条读 {@code L_thin_XSB}
 *       统计箱子数、按标题/作者/箱子/列/行做区间过滤。</li>
 *   <li><b>答案库</b>（{@code m_Ans} 且至少给了箱子/列/行之一时）：
 *       {@code G_State} 里 {@code G_Solution = 1 and P_Key_Num >= 0 and P_Key not in
 *       (select L_Key from G_Level)}，{@code group by P_Key}，<b>忽略标题与作者</b>。</li>
 * </ol>
 *
 * <p>原版用 {@code publishProgress()} 更新进度对话框文字；这里换成
 * {@link SwingWorker#publish(Object)} + {@link HoloProgressDialog}。
 * 取消语义也照搬：取消时把<b>已经查到的部分结果</b>回调出去（原版 {@code stopFind()}）。
 */
public class myQueryFragment {

    /** 原版 {@code myQueryFragment.FindStatusUpdate}：向上层回传查询结果。 */
    public interface FindStatusUpdate {
        void onQueryDone(ArrayList<mapNode> mlMaps);
    }

    private final long[] m_sets;
    private final boolean m_Ans;
    private final String mTitle;
    private final String mAuthor;
    private final int mBoxs, mCols, mRows, mBoxs2, mCols2, mRows2;

    private final Frame owner;
    private final FindStatusUpdate statusUpdate;

    private final ArrayList<mapNode> m_Map_List = new ArrayList<>();
    private HoloProgressDialog dialog;
    private SwingWorker<ArrayList<mapNode>, String> worker;
    private volatile boolean cancelled;

    public myQueryFragment(Frame owner, FindStatusUpdate statusUpdate,
                           long[] sets, boolean searchAnswerLib,
                           String title, String author,
                           int boxs, int cols, int rows,
                           int boxs2, int cols2, int rows2) {
        this.owner = owner;
        this.statusUpdate = statusUpdate;
        this.m_sets = sets;
        this.m_Ans = searchAnswerLib;
        // 原版在 onCreate 里就把标题/作者转成大写再比
        this.mTitle = title == null ? "" : title.toUpperCase();
        this.mAuthor = author == null ? "" : author.toUpperCase();
        this.mBoxs = boxs;
        this.mCols = cols;
        this.mRows = rows;
        this.mBoxs2 = boxs2;
        this.mCols2 = cols2;
        this.mRows2 = rows2;
    }

    /** 原版 {@code mDialog2.show(getFragmentManager(), ...)}：弹进度框并开始查询。 */
    public void show() {        dialog = new HoloProgressDialog(owner, "查询中...");
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
                    statusUpdate.onQueryDone(result);
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
     * 同步执行查询，不开进度框 —— 给测试与无界面场景用。
     * 逻辑与 {@link #show()} 完全相同，只是省掉 SwingWorker / ProgressDialog 那一层。
     */
    public ArrayList<mapNode> runNow() {
        return find(text -> { });
    }

    // ------------------------------------------------------------------ 查询主体

    private ArrayList<mapNode> find(java.util.function.Consumer<String> sink) {
        Cursor cursor = null;
        try {
            cursor = mySQLite.m_SQL.mSDB.rawQuery("PRAGMA synchronous=OFF", null);
            try {
                if (m_sets == null) {
                    cursor = mySQLite.m_SQL.mSDB.query("G_Level", null, null, null, null, null, null);
                } else {
                    mySQLite.m_SQL.new_tmp_Table2();  // 创建临时表，用于查询
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
                        return m_Map_List;
                    }

                    int boxCount = 0;
                    int rows2;
                    int cols2;
                    try {
                        long p_id = cursor.getLong(cursor.getColumnIndex("P_id"));
                        String title0 = cursor.getString(cursor.getColumnIndex("L_Title"));
                        String author0 = cursor.getString(cursor.getColumnIndex("L_Author"));
                        String map0 = cursor.getString(cursor.getColumnIndex("L_thin_XSB"));

                        n++;
                        if (mCount < 0) {
                            mCount = cursor.getCount();
                        }
                        sink.accept("查询中... " + n + "/" + mCount + "\n"
                                + myMaps.getSetTitle(p_id) + "\n" + title0 + "\n" + author0);

                        if ("无效关卡".equals(title0)) {
                            rows2 = 0;
                            cols2 = 0;
                            author0 = "";
                        } else {
                            String[] arr = map0.split("\r\n|\n\r|\n|\r|\\|");
                            rows2 = arr.length;
                            cols2 = arr[0].length();

                            // 检查 XSB 是否规整
                            if (rows2 < 3 || cols2 < 3) {
                                continue;
                            }
                            boolean flg = false;
                            for (int r = 1; r < rows2; r++) {
                                if (arr[r].length() != cols2) {
                                    flg = true;
                                    break;
                                }
                            }
                            if (flg) {
                                continue;
                            }

                            for (int r = 0; r < rows2; r++) {
                                for (int c = 0; c < cols2; c++) {
                                    char ch = arr[r].charAt(c);
                                    if (ch == '$' || ch == '*') {
                                        boxCount++;
                                    }
                                }
                            }
                        }

                        if (matches(title0, author0, boxCount, cols2, rows2)) {
                            m_Map_List.add(new mapNode(0,
                                    cursor.getLong(cursor.getColumnIndex("P_id")),
                                    cursor.getLong(cursor.getColumnIndex("L_id")),
                                    cursor.getInt(cursor.getColumnIndex("L_Solved")),
                                    cursor.getString(cursor.getColumnIndex("L_Content")),
                                    cursor.getString(cursor.getColumnIndex("L_Title")),
                                    cursor.getString(cursor.getColumnIndex("L_Author")),
                                    cursor.getString(cursor.getColumnIndex("L_Comment")),
                                    cursor.getLong(cursor.getColumnIndex("L_Key")),
                                    cursor.getInt(cursor.getColumnIndex("L_Solution")),
                                    cursor.getString(cursor.getColumnIndex("L_thin_XSB")),
                                    cursor.getInt(cursor.getColumnIndex("L_Locked"))));
                        }
                    } catch (Exception e) {
                        // 原版同样逐条吞掉异常继续
                    }
                }
            } catch (Exception e) {
                // 原版如此
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                mySQLite.m_SQL.del_tmp_Table2();
            }

            // ---------------- 答案库 ----------------
            cursor = null;
            try {
                // 若不搜索答案库，或没给尺寸条件，直接结束（原版用抛异常跳出）
                if (!m_Ans || (mBoxs <= 0 && mCols <= 0 && mRows <= 0)) {
                    return m_Map_List;
                }

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
                    n++;
                    if (mCount < 0) {
                        mCount = cursor.getCount();
                    }
                    sink.accept("查询中... " + n + "/" + mCount + "\n答案库"
                            + "\n查询答案库时，会忽略关卡的标题及作者信息。");

                    String map0;
                    String[] arr;
                    try {
                        map0 = cursor.getString(cursor.getColumnIndex("L_thin_XSB"));
                        arr = map0.split("\r\n|\n\r|\n|\r|\\|");
                    } catch (Exception e) {
                        continue;
                    }

                    int rows2 = arr.length;
                    int cols2 = arr[0].length();

                    if (rows2 < 3 || cols2 < 3) {
                        continue;
                    }
                    boolean flg = false;
                    for (int r = 1; r < rows2; r++) {
                        if (arr[r].length() != cols2) {
                            flg = true;
                            break;
                        }
                    }
                    if (flg) {
                        continue;
                    }

                    int boxCount = 0;
                    for (int r = 0; r < rows2; r++) {
                        for (int c = 0; c < cols2; c++) {
                            char ch = arr[r].charAt(c);
                            if (ch == '$' || ch == '*') {
                                boxCount++;
                            }
                        }
                    }

                    // 答案库不比对标题与作者
                    if (rangeMatch(boxCount, cols2, rows2)) {
                        m_Map_List.add(new mapNode(0,
                                -1,  // P_id
                                cursor.getLong(cursor.getColumnIndex("S_id")),
                                1,   // 是否有答案
                                cursor.getString(cursor.getColumnIndex("L_thin_XSB")),
                                "",
                                "",
                                "",
                                cursor.getLong(cursor.getColumnIndex("P_Key")),
                                cursor.getInt(cursor.getColumnIndex("P_Key_Num")),
                                cursor.getString(cursor.getColumnIndex("L_thin_XSB")),
                                0));
                    }
                }
            } catch (Exception e) {
                // 原版如此
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } finally {
            // nothing
        }
        return m_Map_List;
    }


    /** 原版 doInBackground 里关卡表那段的过滤条件（含标题/作者）。 */
    private boolean matches(String title0, String author0, int boxs, int cols, int rows) {
        return (mTitle.isEmpty() || title0.toUpperCase().indexOf(mTitle) >= 0)
                && (mAuthor.isEmpty() || author0.toUpperCase().indexOf(mAuthor) >= 0)
                && rangeMatch(boxs, cols, rows);
    }

    /**
     * 原版「最小值 / 最大值」三段的区间判定（箱子数 / 列数 / 行数共用同一套写法）：
     * <pre>
     * (min==0 &amp;&amp; max==0 || max==0 &amp;&amp; min&lt;=v) || (min==0 &amp;&amp; max&gt;=v) || (min&lt;=v &amp;&amp; max&gt;=v)
     * </pre>
     */
    private boolean rangeMatch(int boxs, int cols, int rows) {
        return inRange(mBoxs, mBoxs2, boxs)
                && inRange(mCols, mCols2, cols)
                && inRange(mRows, mRows2, rows);
    }

    private static boolean inRange(int min, int max, int value) {
        return (min == 0 && max == 0 || max == 0 && min <= value)
                || (min == 0 && max >= value)
                || (min <= value && max >= value);
    }
}
