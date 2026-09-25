package my.boxman;

import my.boxman.compat.HoloProgressDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 原版 {@code myExportFragment}（{@code DialogFragment} + {@code AsyncTask} + {@code ProgressDialog}）
 * 的等价物。逐行照搬原版 {@code ExportTask.doInBackground()} 与 {@code exportSet()}。
 *
 * <p>导出目标目录固定为 {@code <sRoot><sPath>导出/}，文档名 {@code 关卡集名 + (.txt | .xsb)}；
 * 「仅答案关卡」另存为 {@code 仅有答案的关卡 + 后缀}（走 {@link mySQLite#expAnsLevel()}）。
 *
 * <p>取消语义照搬：{@code stopExport()} 取消任务并把 {@code mInf + "\n...Break！"} 回调出去。
 *
 * <p><b>可测性</b>：{@link #runNow()} 同步跑完、不开进度框。
 */
public class myExportFragment {

    /** 原版 {@code ExportStatusUpdate}：向上层回传导出统计文本。 */
    public interface ExportStatusUpdate {
        void onExportDone(String inf);
    }

    private final Frame owner;
    private final ExportStatusUpdate statusUpdate;
    private final boolean myAns;        // 是否导出仅答案关卡
    private final boolean myLurd;       // 是否导出答案
    private final boolean myReWrite;    // 遇到重复文档是否覆盖
    private final long[] mySets;        // 关卡集 id 列表

    private HoloProgressDialog dialog;
    private SwingWorker<String, String> worker;
    private volatile boolean cancelled;

    private StringBuilder mInf;
    private String my_Name;

    public myExportFragment(Frame owner, ExportStatusUpdate statusUpdate,
                            boolean myAns, boolean myLurd, boolean myReWrite, long[] mySets) {
        this.owner = owner;
        this.statusUpdate = statusUpdate;
        this.myAns = myAns;
        this.myLurd = myLurd;
        this.myReWrite = myReWrite;
        this.mySets = mySets;
    }

    /** 原版 {@code mDialog3.show(getFragmentManager(), TAG)}：弹进度框并开始导出。 */
    public void show() {
        dialog = new HoloProgressDialog(owner, "导出...");
        dialog.setOnCancel(this::stopExport);

        worker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                return export(this::publish);
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
                    result = mInf == null ? "" : mInf.toString();
                }
                if (dialog != null) {
                    dialog.dispose();
                    dialog = null;
                }
                if (statusUpdate != null) {
                    statusUpdate.onExportDone(result);
                }
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    /** 原版 {@code ExportTask.stopExport()}。 */
    public void stopExport() {
        cancelled = true;
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            worker = null;
        }
        if (statusUpdate != null) {
            statusUpdate.onExportDone(mInf == null ? "" : mInf + "\n...Break！");
        }
    }

    /** 同步执行导出，不开进度框 —— 给测试与无界面场景用。 */
    public String runNow() {
        return export(text -> { });
    }

    // ------------------------------------------------------------------ 导出主体

    private String export(java.util.function.Consumer<String> sink) {
        myMaps.m_lstMaps.clear();   // 关卡列表

        if (mySets == null) return "没有可导出的内容！";

        int set_Count = 0;
        for (int k = 0; k < mySets.length; k++) {   // 计数选中的关卡集个数
            if (mySets[k] > 0) set_Count++;
        }
        if (myAns) set_Count++;
        mInf = new StringBuilder("共选择").append(set_Count).append("个关卡集:");

        for (int k = 0; k < mySets.length; k++) {   // 导出选中的关卡集

            if (mySets[k] > 0) {
                mySQLite.m_SQL.get_Set(mySets[k]);
                mySQLite.m_SQL.get_Levels(mySets[k]);

                if (myMaps.m_lstMaps.size() > 0) {
                    myMaps.sFile = myMaps.J_Title;
                    my_Name = myMaps.sFile + (myLurd ? ".txt" : ".xsb");   // 导出文档名，不含路径
                    sink.accept("导出...\n" + my_Name);
                    if (isCancelled()) return mInf.append("...Break！").toString();

                    File file = new File(myMaps.sRoot + myMaps.sPath + "导出/" + my_Name);
                    if (!file.exists() || myReWrite) {   // 若没有同名文档或允许覆盖
                        exportSet(my_Name, file.exists());
                    } else {                             // 若不允许覆盖同名文档，则跳过
                        mInf.append('\n').append(my_Name).append("...跳过");
                    }
                }
            }
        }

        if (myAns) {   // 导出仅有答案的关卡
            my_Name = "仅有答案的关卡" + (myLurd ? ".txt" : ".xsb");
            sink.accept("导出...\n" + my_Name);
            File file = new File(myMaps.sRoot + myMaps.sPath + "导出/" + my_Name);
            mInf.append('\n').append(my_Name);
            if (!file.exists() || myReWrite) {
                if (isCancelled()) return mInf.append("...Break！").toString();
                if (mySQLite.m_SQL.expAnsLevel()) {
                    if (file.exists()) mInf.append("...覆盖");
                    else mInf.append("...OK");
                } else {
                    mInf.append("...Error");
                }
            } else {
                mInf.append("...跳过");
            }
        }

        return mInf.toString();
    }

    /** 原版 {@code exportSet(String my_Name, boolean flg)}：把一个关卡集写成文档。 */
    private void exportSet(String my_Name, boolean flg) {
        try {
            final StringBuilder str = new StringBuilder();

            if (myMaps.J_Author != null && !myMaps.J_Author.trim().isEmpty()) {
                str.append("Author: ").append(myMaps.J_Author).append('\n');
            }
            if (myMaps.J_Comment != null && !myMaps.J_Comment.trim().isEmpty()) {
                str.append("Comment:\n").append(myMaps.J_Comment).append("\nComment-End:\n");
            }

            for (int k = 0; k < myMaps.m_lstMaps.size(); k++) {
                str.append("\n;Level ").append(k + 1).append('\n');
                if (myMaps.m_lstMaps.get(k).Title != null
                        && !myMaps.m_lstMaps.get(k).Title.equals("无效关卡")) {
                    str.append(myMaps.m_lstMaps.get(k).Map).append('\n');
                }
                if (myMaps.m_lstMaps.get(k).Title != null
                        && !myMaps.m_lstMaps.get(k).Title.trim().isEmpty()) {
                    if (myMaps.m_lstMaps.get(k).Title.equals("无效关卡")) {
                        if (myMaps.m_lstMaps.get(k).Comment != null
                                && !myMaps.m_lstMaps.get(k).Comment.trim().isEmpty()) {
                            str.append(myMaps.m_lstMaps.get(k).Comment).append('\n');
                        }
                        continue;
                    }
                    str.append("Title: ").append(myMaps.m_lstMaps.get(k).Title).append('\n');
                }
                if (myMaps.m_lstMaps.get(k).Author != null
                        && !myMaps.m_lstMaps.get(k).Author.trim().isEmpty()) {
                    str.append("Author: ").append(myMaps.m_lstMaps.get(k).Author).append('\n');
                }
                if (myMaps.m_lstMaps.get(k).Comment != null
                        && !myMaps.m_lstMaps.get(k).Comment.trim().isEmpty()) {
                    str.append("Comment:\n").append(myMaps.m_lstMaps.get(k).Comment);
                    if (myMaps.m_lstMaps.get(k).Comment
                            .charAt(myMaps.m_lstMaps.get(k).Comment.length() - 1) != '\n') {
                        str.append('\n');
                    }
                    str.append("Comment-End:\n");
                }
                if (myLurd) {   // 导出答案
                    myMaps.curMap = myMaps.m_lstMaps.get(k);
                    str.append(mySQLite.m_SQL.get_Ans(myMaps.curMap.key));
                }
            }

            // 「导出/」目录由启动时的目录初始化创建（原版 BoxMan.java:195-197），此处不再兜底
            FileOutputStream fout = new FileOutputStream(
                    myMaps.sRoot + myMaps.sPath + "导出/" + my_Name);
            fout.write(str.toString().getBytes());
            fout.flush();
            fout.close();
            mInf.append('\n').append(my_Name);
            if (flg) mInf.append("...覆盖");
            else mInf.append("...OK");
        } catch (Exception e) {
            mInf.append('\n').append(my_Name).append("...Error");
        }
    }

    private boolean isCancelled() {
        return cancelled || Thread.currentThread().isInterrupted();
    }
}
