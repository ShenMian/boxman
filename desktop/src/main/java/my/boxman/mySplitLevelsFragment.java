package my.boxman;

import my.boxman.compat.HoloProgressDialog;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

/**
 * 原版 {@code mySplitLevelsFragment}（{@code DialogFragment} + {@code AsyncTask} + {@code ProgressDialog}）
 * 的等价物。导入时异步解析关卡 —— 大文档耗时剧增，所以原版把它放进 {@code AsyncTask}。
 *
 * <p>三条解析分支逐行照搬原版 {@code SplitTask.doInBackground()}：
 * <ul>
 *   <li>{@code myType == 0}：<b>剪切板导入</b>。把文本按行拆开（末尾人为补一个空行），
 *       走与文档解析相同的状态机。</li>
 *   <li>{@code myType == 1}：<b>单个关卡文档</b>。忽略关卡集信息，全部塞进
 *       {@code myMaps.m_Set_id} 指向的关卡集。</li>
 *   <li>{@code myType == 2}：<b>关卡集文档列表</b>。逐个文档解析；每个文档先按去扩展名的
 *       文档名 {@code find_Set()}，<b>已登记的关卡集直接跳过</b>，否则新建。</li>
 * </ul>
 *
 * <p>与 Android 的差异只在「调度」一层：{@code publishProgress()} → {@link SwingWorker#publish(Object)}，
 * {@code ProgressDialog} → {@link HoloProgressDialog}，{@code isCancelled()} → {@link #cancelled}。
 * 解析逻辑与统计口径完全一致。
 *
 * <p><b>可测性</b>：{@link #runNow()} 同步跑完整个解析、不开进度框，
 * 供单元测试与无界面场景使用（与 {@code myQueryFragment.runNow()} 同一套路）。
 */
public class mySplitLevelsFragment {

    /** 原版 {@code SplitStatusUpdate}：向上层回传导入统计文本。 */
    public interface SplitStatusUpdate {
        void onSplitDone(String inf);
    }

    /** 解析类别：0 -- 剪切板关卡；1 -- 文档关卡；2 -- 关卡集文档列表 */
    public static final int TYPE_CLIPBOARD = 0;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_FILE_LIST = 2;

    private final Frame owner;
    private final SplitStatusUpdate statusUpdate;
    private final int myType;
    private final ArrayList<String> myFiles;

    private HoloProgressDialog dialog;
    private SwingWorker<String, String> worker;
    private volatile boolean cancelled;

    public mySplitLevelsFragment(Frame owner, SplitStatusUpdate statusUpdate,
                                 int myType, ArrayList<String> myFiles) {
        this.owner = owner;
        this.statusUpdate = statusUpdate;
        this.myType = myType;
        this.myFiles = myFiles;
    }

    /** 原版 {@code mDialog.show(getFragmentManager(), TAG)}：弹进度框并开始解析。 */
    public void show() {
        dialog = new HoloProgressDialog(owner, "解析中...");
        dialog.setOnCancel(this::stopSplit);

        worker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                return split(this::publish);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty() && dialog != null && dialog.isVisible()) {
                    dialog.setMessage(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                String result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = "";
                }
                if (dialog != null) {
                    dialog.dispose();
                    dialog = null;
                }
                if (statusUpdate != null) {
                    statusUpdate.onSplitDone(result);
                }
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    /** 原版 {@code SplitTask.stopSplit()}：取消任务，并把「导入可能不完整」回调出去。 */
    public void stopSplit() {
        cancelled = true;
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            worker = null;
        }
        if (statusUpdate != null) {
            statusUpdate.onSplitDone("操作被中断，导入可能不完整！");
        }
    }

    /** 同步执行解析，不开进度框 —— 给测试与无界面场景用。 */
    public String runNow() {
        return split(text -> { });
    }

    // ------------------------------------------------------------------ 解析主体

    private String split(java.util.function.Consumer<String> sink) {
        long exitTime = System.currentTimeMillis();

        if (myType < 0 || myFiles == null) return "没有可解析的内容！";

        sink.accept("解析中...");

        int[] num = {0, 0, 0};   // 解析出来的关卡数、无效关卡数、忽略（跳过）的关卡数
        myMaps.m_Nums[0] = 0;    // 解析出来的答案数、无效答案数
        myMaps.m_Nums[1] = 0;

        if (myType == 2) {  // 关卡集列表
            StringBuilder g_Map = new StringBuilder();      // 关卡地图
            StringBuilder g_Title = new StringBuilder();    // 标题
            StringBuilder g_Author = new StringBuilder();   // 作者
            StringBuilder g_Comment = new StringBuilder();  // "注释"
            StringBuilder sSolution = new StringBuilder();  // 答案
            mapNode nd;
            long id;
            String my_Name;
            File file;
            InputStreamReader read;
            BufferedReader bufferedReader;

            boolean flg = false;    // 是否开始了 XSB
            byte flg2 = 0;          // 是否开始了 Comment
            boolean flg3 = false;   // 是否答案
            byte flg4 = 0;          // 是否开始了 Title
            byte flg5 = 0;          // 是否开始了 author
            boolean newSet = false; // 关卡集解析尚未开始

            String line;
            for (int i = 0; i < myFiles.size(); i++) {
                if (isCancelled()) return "";
                if (isItemChecked(i)) {
                    try {
                        my_Name = new StringBuilder(myMaps.sRoot).append(myMaps.sPath)
                                .append("导入/").append(myFiles.get(i)).toString();
                        // 若包含导入关卡选项，查看是否有重复关卡集
                        if (myMaps.isXSB) {
                            myMaps.J_Title = myFiles.get(i).substring(0, myFiles.get(i).lastIndexOf("."));  // 去掉扩展名
                            long new_Set_id = mySQLite.m_SQL.find_Set(myMaps.J_Title);
                            if (new_Set_id > 0) continue;   // 关卡集已经登记，跳过
                            else {                          // 创建关卡集
                                myMaps.m_Set_id = mySQLite.m_SQL.add_T(3, myMaps.J_Title, "", "");

                                if (myMaps.m_Set_id > 0) {  // 关卡集创建成功，更新界面中的关卡集列表
                                    set_Node nd2 = new set_Node();
                                    nd2.id = myMaps.m_Set_id;
                                    nd2.title = myMaps.J_Title;
                                    myMaps.mSets3.add(nd2);
                                } else continue;            // 关卡集创建失败，跳过
                            }
                        }
                        sink.accept("检查文档编码...\n" + myFiles.get(i));
                        file = new File(my_Name);
                        read = new InputStreamReader(new FileInputStream(file),
                                myMaps.getTxtEncode(new FileInputStream(file)));   // 考虑到编码格式
                        bufferedReader = new BufferedReader(read);
                        nd = null;
                        while (true) {
                            if (isCancelled()) return "";

                            sink.accept("解析中...\n" + myFiles.get(i) + "\n" + (num[0] - num[2]));

                            line = bufferedReader.readLine();
                            if (line == null || myMaps.isXSB(line)) {   // 匹配 XSB 行
                                if (!flg || line == null) {             // XSB 块刚开始，或到文档尾
                                    if (line == null && g_Map.length() <= 0) break;  // 到文档尾，且没有解析到 XSB
                                    num[0]++;   // 到文档尾时，会虚增一个数
                                    if (newSet) {   // 当遇到第一个关卡的 XSB 后，说明新的关卡集解析已经开始
                                        if (nd == null)
                                            nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                                    g_Author.toString(), g_Comment.toString());  // 关卡节点
                                        if (myMaps.isXSB) {
                                            if (nd.Title.equals("无效关卡") && nd.Cols == 2 && nd.Rows == 1) {
                                                id = -1;
                                                num[2]++;
                                            } else {
                                                id = mySQLite.m_SQL.add_L(myMaps.m_Set_id, nd);
                                            }
                                            if (nd.L_CRC_Num < 0 || id <= 0) num[1]++;
                                        }
                                    } else {    // 第一个关卡的 XSB 之前，先保存关卡集的作者、说明等信息
                                        if (myMaps.isXSB) {
                                            mySQLite.m_SQL.Update_T_Inf(myMaps.m_Set_id, myMaps.J_Title,
                                                    g_Author.toString(), g_Comment.toString());
                                        }
                                    }
                                    if (sSolution.length() > 0) {   // 有答案尚未保存
                                        mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                                    }
                                    if (line == null) {   // 到文档尾，此关卡集解析结束
                                        newSet = false;
                                        num[0]--;         // 将虚增的关卡数调整一下
                                        break;
                                    }

                                    newSet = true;  // 一个关卡已经开始解析
                                    g_Map = new StringBuilder();
                                    g_Title = new StringBuilder();
                                    g_Author = new StringBuilder();
                                    g_Comment = new StringBuilder();
                                    sSolution = new StringBuilder();
                                    flg3 = false;
                                    flg2 = 0;   // 强制"注释"块结束，预防"注释"块没写"comment-end:"的情况
                                    flg4 = 0;   // 强制"标题"可以重新开始
                                    flg5 = 0;   // 强制"作者"可以重新开始
                                    flg = true; // 准备读入关卡 XSB
                                    nd = null;
                                }
                                if (g_Map.length() > 0) g_Map.append('\n');
                                g_Map.append(line);
                            } else
                            if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("title:")
                                    && flg4++ == 0) {   // 匹配 Title，标题
                                g_Title.append(line.substring(line.indexOf(":") + 1).trim());
                                flg = false;    // 结束关卡 XSB 的解析
                                flg3 = false;
                            } else
                            if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("author:")
                                    && flg5++ == 0) {   // 匹配 Author，作者
                                g_Author.append(line.substring(line.indexOf(":") + 1).trim());
                                flg = false;    // 结束关卡 XSB 的解析
                                flg3 = false;
                            } else
                            if (myMaps.isLurd && line.trim().toLowerCase(Locale.getDefault())
                                    .startsWith("solution")) {  // 匹配 Solution，答案
                                if (sSolution.length() > 0) {   // 有答案尚未保存
                                    if (nd == null)
                                        nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                                g_Author.toString(), g_Comment.toString());
                                    mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                                    sSolution = new StringBuilder();
                                }
                                if (line.indexOf(":") >= 0) {
                                    sSolution.append(line.substring(line.indexOf(":") + 1).trim());
                                } else {
                                    sSolution.append(line.substring(line.indexOf(")") + 1).trim());
                                }
                                if (flg2 > 0) flg2++;
                                flg = false;
                                flg3 = true;    // 开始答案行
                            } else
                            if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment-end:")
                                    || line.trim().toLowerCase(Locale.getDefault())
                                    .startsWith("comment_end:")) {  // 匹配 Comment-end，"注释"块结束
                                if (flg2 > 0) flg2++;
                            } else
                            if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment:")
                                    && flg2++ == 0) {   // 匹配 Comment，"注释"块开始
                                flg3 = false;
                                flg = false;    // 结束关卡 XSB 的解析
                                line = line.substring(line.indexOf(":") + 1).trim();
                                if (!line.equals("")) g_Comment.append(line);
                            } else
                            if (flg2 != 1 && (line.indexOf(';') == 0 || line.matches("\\s*"))) {
                                flg = false;    // 结束关卡 XSB 的解析
                            } else
                            if (flg2 == 1) {    // "注释"块
                                if (!g_Comment.toString().isEmpty()) g_Comment.append('\n');
                                g_Comment.append(line);
                            } else
                            if (flg3) {         // 答案行
                                sSolution.append(line);
                            } else {
                                flg = false;    // 结束关卡 XSB 的解析
                            }
                        }   // end the while

                        read.close();
                    } catch (Exception e) {
                        // 对于不规范的文档，直接跳过
                    }
                }
            }   // end for
        } else if (myType == 1) {   // 关卡文档（忽略关卡集方面的信息）
            try {
                sink.accept("检查文档编码...\n" + myFiles.get(0));
                String my_Name = new StringBuilder(myMaps.sRoot).append(myMaps.sPath)
                        .append("导入/").append(myFiles.get(0)).toString();
                File file = new File(my_Name);
                InputStreamReader read = new InputStreamReader(new FileInputStream(file),
                        myMaps.getTxtEncode(new FileInputStream(file)));
                BufferedReader bufferedReader = new BufferedReader(read);

                StringBuilder g_Map = new StringBuilder();
                StringBuilder g_Title = new StringBuilder();
                StringBuilder g_Author = new StringBuilder();
                StringBuilder g_Comment = new StringBuilder();
                StringBuilder sSolution = new StringBuilder();
                mapNode nd = null;
                long id;

                boolean flg = false;
                byte flg2 = 0;
                boolean flg3 = false;
                byte flg4 = 0;
                byte flg5 = 0;

                String line;
                while (true) {
                    if (isCancelled()) return "";

                    sink.accept("解析中...\n" + myFiles.get(0) + "\n" + (num[0] - num[2]));

                    line = bufferedReader.readLine();
                    if (line == null || myMaps.isXSB(line)) {
                        if (!flg || line == null) {
                            if (line == null && g_Map.length() <= 0) break;
                            num[0]++;
                            if (num[0] > 1) {
                                if (nd == null)
                                    nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                            g_Author.toString(), g_Comment.toString());
                                if (myMaps.isXSB) {
                                    if (nd.Title.equals("无效关卡") && nd.Cols == 2 && nd.Rows == 1) {
                                        id = -1;
                                        num[2]++;
                                    } else {
                                        id = mySQLite.m_SQL.add_L(myMaps.m_Set_id, nd);
                                    }
                                    if (nd.L_CRC_Num < 0 || id <= 0) num[1]++;
                                }
                            }
                            if (sSolution.length() > 0) {
                                mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                            }
                            if (line == null) break;

                            g_Map = new StringBuilder();
                            g_Title = new StringBuilder();
                            g_Author = new StringBuilder();
                            g_Comment = new StringBuilder();
                            sSolution = new StringBuilder();
                            flg3 = false;
                            flg2 = 0;
                            flg4 = 0;
                            flg5 = 0;
                            flg = true;
                            nd = null;
                        }
                        if (g_Map.length() > 0) g_Map.append('\n');
                        g_Map.append(line);
                    } else
                    if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("title:")
                            && flg4++ == 0) {
                        g_Title.append(line.substring(line.indexOf(":") + 1).trim());
                        flg = false;
                        flg3 = false;
                    } else
                    if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("author:")
                            && flg5++ == 0) {
                        g_Author.append(line.substring(line.indexOf(":") + 1).trim());
                        flg = false;
                        flg3 = false;
                    } else
                    if (myMaps.isLurd && line.trim().toLowerCase(Locale.getDefault())
                            .startsWith("solution")) {
                        if (sSolution.length() > 0) {
                            if (nd == null)
                                nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                        g_Author.toString(), g_Comment.toString());
                            mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                            sSolution = new StringBuilder();
                        }
                        if (line.indexOf(":") >= 0) {
                            sSolution.append(line.substring(line.indexOf(":") + 1).trim());
                        } else {
                            sSolution.append(line.substring(line.indexOf(")") + 1).trim());
                        }
                        if (flg2 > 0) flg2++;
                        flg = false;
                        flg3 = true;
                    } else
                    if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment-end:")
                            || line.trim().toLowerCase(Locale.getDefault()).startsWith("comment_end:")) {
                        if (flg2 > 0) flg2++;
                    } else
                    if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment:")
                            && flg2++ == 0) {
                        flg3 = false;
                        flg = false;
                        line = line.substring(line.indexOf(":") + 1).trim();
                        if (!line.equals("")) g_Comment.append(line);
                    } else
                    if (flg2 != 1 && (line.indexOf(';') == 0 || line.matches("\\s*"))) {
                        flg = false;
                    } else
                    if (flg2 == 1) {
                        if (!g_Comment.toString().isEmpty()) g_Comment.append('\n');
                        g_Comment.append(line);
                    } else
                    if (flg3) {
                        sSolution.append(line);
                    } else {
                        flg = false;
                    }
                }   // end the while

                read.close();
            } catch (Exception e) {
            }
            if (num[0] > 0) num[0]--;   // 修正统计数字
        } else {    // 剪切板导入
            try {
                // 将剪切板的内容按行拆解（为便于解析，人为加一个空行）
                String[] Arr = (myFiles.get(0) + "\n\n;").split("\r\n|\n\r|\n|\r|\\|");
                StringBuilder g_Map = new StringBuilder();
                StringBuilder g_Title = new StringBuilder();
                StringBuilder g_Author = new StringBuilder();
                StringBuilder g_Comment = new StringBuilder();
                StringBuilder sSolution = new StringBuilder();
                mapNode nd = null;
                long id;

                boolean flg = false;
                byte flg2 = 0;
                boolean flg3 = false;
                byte flg4 = 0;
                byte flg5 = 0;

                String line;
                int k = 0;
                while (k < Arr.length) {
                    if (isCancelled()) return "";

                    sink.accept("解析中...\n剪切板\n" + (num[0] - num[2]));

                    line = Arr[k++];
                    if (myMaps.isXSB(line) || k == Arr.length - 1) {
                        if (!flg || k == Arr.length - 1) {
                            num[0]++;
                            if (num[0] > 1) {
                                if (nd == null)
                                    nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                            g_Author.toString(), g_Comment.toString());
                                if (myMaps.isXSB) {
                                    if (nd.Title.equals("无效关卡") && nd.Cols == 2 && nd.Rows == 1) {
                                        id = -1;
                                        num[2]++;
                                    } else {
                                        id = mySQLite.m_SQL.add_L(myMaps.m_Set_id, nd);
                                    }
                                    if (nd.L_CRC_Num < 0 || id <= 0) num[1]++;
                                }
                            }
                            if (sSolution.length() > 0) {
                                mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                            }
                            g_Map = new StringBuilder();
                            g_Title = new StringBuilder();
                            g_Author = new StringBuilder();
                            g_Comment = new StringBuilder();
                            sSolution = new StringBuilder();
                            flg3 = false;
                            flg2 = 0;
                            flg4 = 0;
                            flg5 = 0;
                            flg = true;
                            nd = null;
                        }
                        if (g_Map.length() > 0) g_Map.append('\n');
                        g_Map.append(line);
                    } else
                    if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("title:")
                            && flg4++ == 0) {
                        g_Title.append(line.substring(line.indexOf(":") + 1).trim());
                        flg = false;
                        flg3 = false;
                    } else
                    if (flg2 == 0 && line.trim().toLowerCase(Locale.getDefault()).startsWith("author:")
                            && flg5++ == 0) {
                        g_Author.append(line.substring(line.indexOf(":") + 1).trim());
                        flg = false;
                        flg3 = false;
                    } else
                    if (myMaps.isLurd && line.trim().toLowerCase(Locale.getDefault())
                            .startsWith("solution")) {
                        if (sSolution.length() > 0) {
                            if (nd == null)
                                nd = new mapNode(g_Map.toString(), g_Title.toString(),
                                        g_Author.toString(), g_Comment.toString());
                            mySQLite.m_SQL.inp_Ans(nd, sSolution.toString());
                            sSolution = new StringBuilder();
                        }
                        if (line.indexOf(":") >= 0) {
                            sSolution.append(line.substring(line.indexOf(":") + 1).trim());
                        } else {
                            sSolution.append(line.substring(line.indexOf(")") + 1).trim());
                        }
                        if (flg2 > 0) flg2++;
                        flg = false;
                        flg3 = true;
                    } else
                    if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment-end:")
                            || line.trim().toLowerCase(Locale.getDefault()).startsWith("comment_end:")) {
                        if (flg2 > 0) flg2++;
                    } else
                    if (line.trim().toLowerCase(Locale.getDefault()).startsWith("comment:")
                            && flg2++ == 0) {
                        flg3 = false;
                        flg = false;
                        line = line.substring(line.indexOf(":") + 1).trim();
                        if (!line.equals("")) g_Comment.append(line);
                    } else
                    if (flg2 != 1 && (line.indexOf(';') == 0 || line.matches("\\s*"))) {
                        flg = false;
                    } else
                    if (flg2 == 1) {
                        if (!g_Comment.toString().isEmpty()) g_Comment.append('\n');
                        g_Comment.append(line);
                    } else
                    if (flg3) {
                        sSolution.append(line);
                    } else {
                        flg = false;
                    }
                }   // end the while

            } catch (Exception e) {
            }
            if (num[0] > 0) num[0]--;   // 修正统计数字
        }

        // 导入统计
        StringBuilder str = new StringBuilder();

        if (num[0] == 0) {
            str.append("关卡集重复或无效！");
        } else {
            str.append("解析耗时：").append((System.currentTimeMillis() - exitTime) / 1000).append(" 秒\n");
            str.append("关卡数：").append(num[0] - num[2]).append("\n无效关卡数：").append(num[1] - num[2]);
            myMaps.m_Nums[2] = num[0];
            myMaps.m_Nums[3] = num[1];
            if (myMaps.isLurd) {
                str.append("\n\n答案数：").append(myMaps.m_Nums[0])
                        .append("\n导入数：").append(myMaps.m_Nums[0] - myMaps.m_Nums[1]);
                if (myMaps.m_Nums[1] > 0) {
                    str.append("\n(无效重复超长答案不导入)");
                }
            }
        }
        return str.toString();
    }

    private boolean isCancelled() {
        return cancelled || Thread.currentThread().isInterrupted();
    }

    /**
     * 原版 {@code myMaps.m_setName.isItemChecked(i)}。
     *
     * <p>原版 {@code m_setName} 是 {@code CHOICE_MODE_MULTIPLE} 的 {@code ListView}，
     * PC 侧同一个字段就是那个 {@link JList}（{@code MULTIPLE_INTERVAL_SELECTION}），
     * 因此「第 i 项是否被勾选」等价于「第 i 项是否被选中」。
     */
    private boolean isItemChecked(int i) {
        JList<?> list = myMaps.m_setName;
        return list != null && i >= 0 && i < list.getModel().getSize() && list.isSelectedIndex(i);
    }
}
