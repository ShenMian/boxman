package my.boxman;

import my.boxman.compat.HoloMessageDialog;
import my.boxman.compat.HoloProgressDialog;
import my.boxman.compat.UiWindow;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 原版「比赛答案提交列表」（{@code BoxMan.java:609} 的 {@code case R.id.menu_submit_list}）
 * 的 1:1 移植，对应原版 {@code mySubmitList} Activity（505 行）。
 *
 * <p><b>界面照搬 3 个布局</b>：
 * <ul>
 *   <li>{@code submit_main.xml}：整屏 {@code ExpandableListView}，背景 {@code #004040}，
 *       {@code listSelector="#ff0064aa"}</li>
 *   <li>{@code submit_groups.xml}：分组行，{@code #DDDDDD}、16sp、{@code paddingLeft=32dp}、
 *       上下 {@code margin 6dp}</li>
 *   <li>{@code submit_child.xml}：子项行，黑底 {@code #000000}，6 列 TextView（12sp {@code #BBBBBB}）</li>
 * </ul>
 *
 * <p><b>几何全部由原版截图实测校准</b>（1260×2844，density 3.4051，1dp = 1px）：
 * <pre>
 * ActionBar      48dp，#0083C5；返回折角 + 标题「提交列表」+ 右侧「刷新」（menu/submit_list.xml
 *                唯一的 item 是 showAsAction="always"，溢出菜单为空 → 原版**不画 ⋮**，
 *                动作项 56dp 且右边距 0：实测 ink 中心 x≈341.9dp）
 * 分组行         内容 33dp + 分隔线 1dp；chevron 12..24dp（12×6），文字落在 32dp
 * 子项行         内容 33dp + 分隔线 1dp；黑底
 *   列顺序（submit_child.xml 的视觉顺序）：
 *   序号 30dp 居中 11sp | 日期 wrap 居中 12sp | 姓名 130dp 左 12sp
 *   | 步数 60dp 左 12sp | 推动 60dp 左 12sp | 国籍 wrap 居中 12sp，各列 padding 4dp
 *   实测：序号中心 14.1dp、日期起 34.4、姓名起 78.4、步数起 208.2、推动起 267.8、国籍起 326.9
 * 最小步数 → #00FF00，最小推动 → #0000FF，其余 #BBBBBB
 * </pre>
 *
 * <p><b>数据来源</b>：{@code myMaps.uil + "api/competition/submission/"}，
 * 4 个 GET（主关 {@code /}、副关 {@code ?t=extra}、副关2 {@code ?t=extra2}、副关3 {@code ?t=extra3}），
 * 返回 {@code {"id":..,"label":..,"submission":[{"time","name","country","move","push"},..]}}。
 * 原版用 Apache HttpClient + json-simple；这里用 JDK 自带的 {@link HttpURLConnection} +
 * <b>同一个 json-simple</b>（{@code build.gradle} 里已有 {@code com.googlecode.json-simple:json-simple}，
 * 与原版 {@code libs/json-simple-1.1.jar} 同源），所以 {@link #myJson} 是原版解析代码的直译。
 */
public class mySubmitList extends JFrame {

    // ------------------------------------------------------------ 原版字段（同名同义）

    /** 主关提交列表 */
    final ArrayList<list_Node> mList1 = new ArrayList<list_Node>();
    /** 副关提交列表 */
    final ArrayList<list_Node> mList2 = new ArrayList<list_Node>();
    /** 副关2提交列表 */
    final ArrayList<list_Node> mList3 = new ArrayList<list_Node>();
    /** 副关3提交列表 */
    final ArrayList<list_Node> mList4 = new ArrayList<list_Node>();

    final String[] submit_groups = {"主关", "副关", "副关2", "副关3"};
    /** 参赛人数 */
    final int[] mTotal = {0, 0, 0, 0};

    int g_Pos;
    int c_Pos;
    int min_Moves, min_Pushs, min_Moves2, min_Pushs2, min_Moves3, min_Pushs3, min_Moves4, min_Pushs4;

    /** 原版 {@code private static String url = "api/competition/submission/";} */
    static final String URL = "api/competition/submission/";

    // ------------------------------------------------------------ 配色（取自原版资源 / 截图实测）

    /** {@code submit_main.xml} 的 Activity 背景 */
    private static final Color LIST_BG = new Color(0x00, 0x40, 0x40);
    /** {@code submit_child.xml} 子项内层 LinearLayout 的 {@code #FF000000} */
    private static final Color CHILD_BG = Color.BLACK;
    /** {@code submit_main.xml} 的 {@code android:listSelector="#ff0064aa"} */
    private static final Color SELECT_BG = new Color(0x00, 0x64, 0xAA);
    /** {@code submit_groups.xml} 的 {@code android:textColor="#ffdddddd"} */
    private static final Color GROUP_FG = new Color(0xDD, 0xDD, 0xDD);
    /** {@code submit_child.xml} 的 {@code android:textColor="#ffbbbbbb"} */
    private static final Color CHILD_FG = new Color(0xBB, 0xBB, 0xBB);
    /** 原版 {@code ts_child5.setTextColor(0xff00ff00)}：步数最少 */
    private static final Color MIN_MOVES_FG = new Color(0x00, 0xFF, 0x00);
    /** 原版 {@code ts_child6.setTextColor(0xff0000ff)}：推动最少 */
    private static final Color MIN_PUSHS_FG = new Color(0x00, 0x00, 0xFF);
    /** 列表分隔线：原版 2px 的 (37,93,94) 折算到 1dp，与主界面一致 */
    private static final Color DIVIDER_FG = new Color(0x17, 0x51, 0x51);
    /** 分组展开指示器颜色，与主界面一致 */
    private static final Color INDICATOR_FG = new Color(0xCC, 0xCC, 0xCC);

    // ------------------------------------------------------------ 几何（1dp = 1px）

    /** 行内容高度（实测分组行 110px / 子项行 112px @ density 3.405） */
    private static final int ROW_CONTENT_HEIGHT = 33;
    /** 行分隔线高度（实测 2px） */
    private static final int ROW_DIVIDER_HEIGHT = 1;
    private static final int ROW_HEIGHT = ROW_CONTENT_HEIGHT + ROW_DIVIDER_HEIGHT;

    /** {@code submit_groups.xml} 的 {@code android:paddingLeft="32dp"} */
    private static final int GROUP_PADDING_LEFT = 32;
    /** 分组指示器水平位置：实测 42px~82px ÷ 3.405 ≈ 12dp~24dp */
    private static final int INDICATOR_LEFT = 12;
    private static final int INDICATOR_WIDTH = 12;
    private static final int INDICATOR_HEIGHT = 6;

    private static final int GROUP_TEXT_SIZE = 16;
    private static final int CHILD_INDEX_TEXT_SIZE = 11;
    private static final int CHILD_TEXT_SIZE = 12;

    /** {@code submit_child.xml} 各列的 {@code android:padding="4dp"} */
    private static final int CHILD_PAD = 4;
    private static final int C_INDEX_W = 30;
    private static final int C_NAME_W = 130;
    private static final int C_MOVES_W = 60;
    private static final int C_PUSHS_W = 60;

    private static final Font GROUP_FONT = new Font("Microsoft YaHei", Font.PLAIN, GROUP_TEXT_SIZE);
    private static final Font CHILD_FONT = new Font("Microsoft YaHei", Font.PLAIN, CHILD_TEXT_SIZE);
    private static final Font CHILD_INDEX_FONT =
            new Font("Microsoft YaHei", Font.PLAIN, CHILD_INDEX_TEXT_SIZE);

    // ------------------------------------------------------------ 组件

    private myActionBar actionBar;
    private JTree tree;
    private JScrollPane scrollPane;
    private HoloProgressDialog dialog;
    /** 4 个分组节点，用于按「组号」展开（对应原版 {@code expandGroup}） */
    private DefaultMutableTreeNode[] groupNodes;

    public mySubmitList() {
        this(true);
    }

    /**
     * @param autoLoad {@code false} 时只建界面、不发网络请求（离屏快照与单元测试用，
     *                 数据由调用方通过 {@link #myJson} + {@link #applyResult} 灌入）
     */
    mySubmitList(boolean autoLoad) {
        setTitle("提交列表 - 推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();

        // ⚠️ 必须在 UI 全部装好之后再调（见 UiWindow 的说明）
        UiWindow.applyPhoneSize(this);

        // 放最后：reload() 会弹模态进度框，不能挡在定尺寸之前
        if (autoLoad) {
            reload();
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(LIST_BG);

        actionBar = new myActionBar();
        actionBar.setBarTitle("提交列表");
        actionBar.setUpEnabled(true, this::dispose);          // setDisplayHomeAsUpEnabled(true)
        actionBar.addBarAction("刷新", this::reload);          // submit_list.xml 的 showAsAction="always"
        add(actionBar, BorderLayout.NORTH);

        tree = new JTree(buildModel()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(DIVIDER_FG);
                for (int i = 0; i < getRowCount(); i++) {
                    Rectangle rowBounds = getRowBounds(i);
                    g2.fillRect(0, rowBounds.y + rowBounds.height - ROW_DIVIDER_HEIGHT,
                            getWidth(), ROW_DIVIDER_HEIGHT);
                }
                g2.dispose();
            }
        };
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        tree.setRowHeight(ROW_HEIGHT);
        tree.setBackground(LIST_BG);
        tree.setOpaque(true);
        tree.setCellRenderer(new RowRenderer());
        tree.setBorder(BorderFactory.createEmptyBorder());
        // 原版 ExpandableListView 单击分组条目即展开/折叠
        tree.setToggleClickCount(1);
        // 缩进完全由渲染器控制，不启用 JTree 自带的层级缩进
        tree.setUI(new BasicTreeUI() {
            @Override
            protected int getRowX(int row, int depth) {
                return 0;
            }

            /** 原版条目是 {@code match_parent}，选中色块通栏铺满整屏 */
            @Override
            protected void paintRow(Graphics g, Rectangle clipBounds, Insets insets,
                                    Rectangle bounds, TreePath path, int row,
                                    boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
                Rectangle fullRow = new Rectangle(0, bounds.y, tree.getWidth(), bounds.height);
                super.paintRow(g, clipBounds, insets, fullRow, path, row,
                        isExpanded, hasBeenExpanded, isLeaf);
            }
        });

        // 原版 onChildClick / setOnItemLongClickListener：只记住点击位置
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode)) {
                    return;
                }
                Object uo = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (uo instanceof ChildItem) {
                    ChildItem ci = (ChildItem) uo;
                    g_Pos = ci.groupPos;
                    c_Pos = ci.childPos;
                }
            }
        });

        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(LIST_BG);
        scrollPane.setBackground(LIST_BG);
        // 原版 ListView 不横向滚动
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        scrollPane.getVerticalScrollBar().setUI(new InvisibleScrollBarUI());
        // 原版静止时不画滚动条（只在滑动时淡入），宽度归零才不会压掉行背景
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        add(scrollPane, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------ 数据加载

    /** 原版 {@code onCreate} / 菜单「刷新」：清空后重新下载。 */
    void reload() {
        mList1.clear();
        mList2.clear();
        mList3.clear();
        mList4.clear();

        dialog = new HoloProgressDialog(this, "下载中...");   // 原版 ProgressDialog("下载中...")
        dialog.setOnCancel(dialog::dispose);                  // setCancelable(true)：只关框，不中断下载
        final HoloProgressDialog dlg = dialog;

        new SwingWorker<Msg, Void>() {
            @Override
            protected Msg doInBackground() {
                return download();
            }

            @Override
            protected void done() {
                Msg msg;
                try {
                    msg = get();
                } catch (Exception e) {
                    msg = new Msg();
                    msg.what = 0;
                    msg.obj = "网络错误：000";
                }
                dlg.dispose();                 // 原版 handler 第一句就是 dialog.dismiss()
                applyResult(msg);
            }
        }.execute();
        dlg.setVisible(true);
    }

    /** 原版 {@code mySubmitList.MyThread.run()}：4 个 GET 顺序执行，累积错误信息。 */
    private Msg download() {
        Msg msg = new Msg();
        mTotal[0] = 0;
        mTotal[1] = 0;
        String[] queries = {"", "?t=extra", "?t=extra2", "?t=extra3"};
        String[] errText = {
                "下载主关列表遇到网络错误：", "下载副关列表遇到网络错误：",
                "下载副关列表遇到网络错误：", "下载副关列表遇到网络错误："};
        try {
            for (int i = 0; i < queries.length; i++) {
                HttpResult r = httpGet(myMaps.uil + URL + queries[i]);
                if (r.code == 200) {
                    msg.what = 1;
                    String inf = myJson(r.body, i);
                    msg.obj = msg.obj.isEmpty() ? inf : msg.obj + "\n" + inf;
                } else {
                    msg.what = 0;
                    String inf = errText[i] + r.code;
                    msg.obj = msg.obj.isEmpty() ? inf : msg.obj + "\n" + inf;
                }
            }
        } catch (Exception e) {
            msg.what = 0;
            msg.obj = "网络错误：000";
        }
        return msg;
    }

    /** 原版 {@code handler.handleMessage()}：非空则展开有数据的分组，否则弹「错误」。 */
    void applyResult(Msg msg) {
        boolean any = !mList1.isEmpty() || !mList2.isEmpty() || !mList3.isEmpty() || !mList4.isEmpty();
        tree.setModel(buildModel());
        if (msg.what == 1 && any) {
            // 列表下载成功，改变一下窗口的标题
            updateMatchTitle();
            for (int i = 0; i < 4; i++) {
                if (!childList(i).isEmpty()) {
                    // 原版是 expandGroup(组号)；JTree 的 expandRow(行号) 会在前面的组展开后错位
                    tree.expandPath(new TreePath(new Object[]{tree.getModel().getRoot(), groupNodes[i]}));
                }
            }
        } else {
            new HoloMessageDialog(this, "错误", msg.obj, "确定").setVisible(true);
        }
    }

    /** 原版：比赛未过期时把标题改成「NNNN，M月D日结束」。 */
    private void updateMatchTitle() {
        if (myMaps.mMatchNo.isEmpty() || myMaps.mMatchDate2.isEmpty()) {
            return;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long d = f.parse(myMaps.mMatchDate2).getTime();
            if (d > System.currentTimeMillis()) {
                String[] arr = myMaps.mMatchDate2.split("-| |:");
                String month = arr[1].charAt(0) == '0' ? arr[1].substring(1) : arr[1];
                String day = arr[2].charAt(0) == '0' ? arr[2].substring(1) : arr[2];
                actionBar.setBarTitle(myMaps.mMatchNo.substring(1, 5) + "，" + month + "月" + day + "日结束");
            }
        } catch (java.text.ParseException ignored) {
            // 原版同样只是 printStackTrace
        }
    }

    private ArrayList<list_Node> childList(int groupPos) {
        switch (groupPos) {
            case 0: return mList1;
            case 1: return mList2;
            case 2: return mList3;
            default: return mList4;
        }
    }

    // ------------------------------------------------------------ JSON（原版 myJson 的直译）

    /**
     * 原版 {@code myJson(String str, int num)}：解析一组提交列表并统计最小步数/最小推动。
     * 返回错误提示串（成功时是初始值 "解析列表遇到错误！"，与原版一致——原版只在抛异常时才
     * 有意义地返回它，正常路径下调用方不会显示）。
     */
    String myJson(String str, int num) {
        String inf = "解析列表遇到错误！";
        ArrayList<list_Node> mList = childList(num);
        JSONParser parser = new JSONParser();
        try {
            mList.clear();

            JSONObject obj = (JSONObject) parser.parse(str);
            // 解析出列表
            str = obj.get("submission").toString();
            Object obj2 = JSONValue.parse(str);
            JSONArray array = (JSONArray) obj2;

            list_Node nd;
            int n1 = Integer.MAX_VALUE, n2 = Integer.MAX_VALUE;
            int n3 = Integer.MAX_VALUE, n4 = Integer.MAX_VALUE;
            if (num == 0) {
                min_Moves = -1;
                min_Pushs = -1;
            } else if (num == 1) {
                min_Moves2 = -1;
                min_Pushs2 = -1;
            } else if (num == 2) {
                min_Moves3 = -1;
                min_Pushs3 = -1;
            } else {
                min_Moves4 = -1;
                min_Pushs4 = -1;
            }

            for (int k = 0; k < array.size(); k++) {
                obj = (JSONObject) array.get(k);

                nd = new list_Node();
                nd.id = k + 1;
                nd.date = obj.get("time").toString();
                nd.name = obj.get("name").toString();
                nd.country = obj.get("country").toString();
                nd.moves = obj.get("move").toString();
                nd.pushs = obj.get("push").toString();

                if (n1 > Integer.valueOf(nd.moves)) {
                    n1 = Integer.valueOf(nd.moves);
                    n3 = Integer.valueOf(nd.pushs);
                    setMinMoves(num, k + 1);
                } else if (n1 == Integer.valueOf(nd.moves)) {
                    if (n3 > Integer.valueOf(nd.pushs)) {
                        n1 = Integer.valueOf(nd.moves);
                        n3 = Integer.valueOf(nd.pushs);
                        setMinMoves(num, k + 1);
                    }
                }

                if (n2 > Integer.valueOf(nd.pushs)) {
                    n2 = Integer.valueOf(nd.pushs);
                    n4 = Integer.valueOf(nd.moves);
                    setMinPushs(num, k + 1);
                } else if (n2 == Integer.valueOf(nd.pushs)) {
                    if (n4 > Integer.valueOf(nd.moves)) {
                        n2 = Integer.valueOf(nd.pushs);
                        n4 = Integer.valueOf(nd.moves);
                        setMinPushs(num, k + 1);
                    }
                }

                mList.add(nd);
            }

            // 统计参赛人数（按姓名去重，忽略大小写与首尾空白）
            Set<String> names = new LinkedHashSet<String>();
            for (int k = 0; k < mList.size(); k++) {
                names.add(mList.get(k).name.trim().toLowerCase());
            }
            mTotal[num] = names.size();

        } catch (Exception e) {
            // 原版是 catch (ParseException) + catch (Throwable)，都只 printStackTrace
        }
        return inf;
    }

    private void setMinMoves(int num, int idx) {
        if (num == 0) min_Moves = idx;
        else if (num == 1) min_Moves2 = idx;
        else if (num == 2) min_Moves3 = idx;
        else min_Moves4 = idx;
    }

    private void setMinPushs(int num, int idx) {
        if (num == 0) min_Pushs = idx;
        else if (num == 1) min_Pushs2 = idx;
        else if (num == 2) min_Pushs3 = idx;
        else min_Pushs4 = idx;
    }

    // ------------------------------------------------------------ HTTP

    /** GET 的结果：状态码 + 响应体（原版直接读 {@code httpResponse.getStatusLine().getStatusCode()}）。 */
    static class HttpResult {
        int code;
        String body = "";
    }

    /**
     * 原版 {@code DefaultHttpClient.execute(new HttpGet(url))} 的等价物。
     * 用 JDK 自带的 {@link HttpURLConnection}（Java 8 可用），不引第三方 HTTP 库。
     */
    static HttpResult httpGet(String url) {
        HttpResult out = new HttpResult();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            out.code = conn.getResponseCode();
            InputStream in = out.code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                in.close();
                out.body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            out.code = 0;
            out.body = e.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 模型

    /** 原版 {@code Message}：{@code what} + {@code obj} */
    static class Msg {
        int what;
        String obj = "";
    }

    /** 分组节点 */
    static class GroupItem {
        final int groupPos;

        GroupItem(int groupPos) {
            this.groupPos = groupPos;
        }
    }

    /** 子项节点 */
    static class ChildItem {
        final int groupPos;
        final int childPos;

        ChildItem(int groupPos, int childPos) {
            this.groupPos = groupPos;
            this.childPos = childPos;
        }
    }

    private DefaultTreeModel buildModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        groupNodes = new DefaultMutableTreeNode[submit_groups.length];
        for (int g = 0; g < submit_groups.length; g++) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(new GroupItem(g));
            ArrayList<list_Node> children = childList(g);
            for (int c = 0; c < children.size(); c++) {
                group.add(new DefaultMutableTreeNode(new ChildItem(g, c)));
            }
            root.add(group);
            groupNodes[g] = group;
        }
        return new DefaultTreeModel(root);
    }

    // ------------------------------------------------------------ 渲染

    private class RowRenderer implements TreeCellRenderer {
        private final JLabel groupRow = new JLabel();
        private final ChildRow childRow = new ChildRow();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean hasFocus) {
            Object uo = value instanceof DefaultMutableTreeNode
                    ? ((DefaultMutableTreeNode) value).getUserObject() : null;

            if (uo instanceof GroupItem) {
                int g = ((GroupItem) uo).groupPos;
                groupRow.setFont(GROUP_FONT);
                groupRow.setForeground(GROUP_FG);
                // 原版 submit_groups.xml 的条目自身没有背景 → 透明，
                // 所以 listSelector 能从下面透出来（AbsListView.drawSelectorOnTop 默认 false）
                groupRow.setOpaque(selected);
                groupRow.setBackground(SELECT_BG);
                groupRow.setText(submit_groups[g] + " 【" + mTotal[g] + "人参赛】");
                groupRow.setIcon(myActionBar.createIndicator(expanded, INDICATOR_FG,
                        INDICATOR_WIDTH, INDICATOR_HEIGHT));
                groupRow.setIconTextGap(GROUP_PADDING_LEFT - INDICATOR_LEFT - INDICATOR_WIDTH);
                groupRow.setBorder(BorderFactory.createEmptyBorder(0, INDICATOR_LEFT, 0, 0));
                return groupRow;
            }

            if (uo instanceof ChildItem) {
                ChildItem ci = (ChildItem) uo;
                ArrayList<list_Node> list = childList(ci.groupPos);
                // 子项行是不透明的黑底，会盖住 listSelector —— 与原版 drawSelectorOnTop=false 一致
                childRow.setOpaque(true);
                childRow.setBackground(CHILD_BG);
                if (ci.childPos < list.size()) {
                    list_Node nd = list.get(ci.childPos);
                    boolean minMove = minMoves(ci.groupPos) == nd.id;
                    boolean minPush = minPushs(ci.groupPos) == nd.id;
                    childRow.bind(String.valueOf(nd.id), nd.date.substring(5, 11), nd.name,
                            String.valueOf(nd.moves), String.valueOf(nd.pushs), nd.country,
                            minMove, minPush);
                }
                return childRow;
            }

            JLabel empty = new JLabel();
            empty.setOpaque(true);
            empty.setBackground(LIST_BG);
            return empty;
        }
    }

    private int minMoves(int g) {
        switch (g) {
            case 0: return min_Moves;
            case 1: return min_Moves2;
            case 2: return min_Moves3;
            default: return min_Moves4;
        }
    }

    private int minPushs(int g) {
        switch (g) {
            case 0: return min_Pushs;
            case 1: return min_Pushs2;
            case 2: return min_Pushs3;
            default: return min_Pushs4;
        }
    }

    /**
     * 子项行：照 {@code submit_child.xml} 的水平 LinearLayout 排 6 列。
     * 组件顺序 = 布局里的视觉顺序（child / child2 / child3 / child5 / child6 / child4）。
     */
    private static class ChildRow extends JPanel {
        private final JLabel index = label(CHILD_INDEX_FONT, SwingConstants.CENTER);
        private final JLabel date = label(CHILD_FONT, SwingConstants.CENTER);
        private final JLabel name = label(CHILD_FONT, SwingConstants.LEFT);
        private final JLabel moves = label(CHILD_FONT, SwingConstants.LEFT);
        private final JLabel pushs = label(CHILD_FONT, SwingConstants.LEFT);
        private final JLabel country = label(CHILD_FONT, SwingConstants.CENTER);

        ChildRow() {
            setLayout(new ChildLayout());
            add(index);
            add(date);
            add(name);
            add(moves);
            add(pushs);
            add(country);
        }

        private static JLabel label(Font f, int align) {
            JLabel l = new JLabel();
            l.setFont(f);
            l.setForeground(CHILD_FG);
            l.setHorizontalAlignment(align);
            l.setBorder(BorderFactory.createEmptyBorder(0, CHILD_PAD, 0, CHILD_PAD));
            return l;
        }

        void bind(String idx, String date, String name, String moves, String pushs, String country,
                  boolean minMove, boolean minPush) {
            this.index.setText(idx);
            this.date.setText(date);
            this.name.setText(name);
            this.moves.setText(moves);
            this.moves.setForeground(minMove ? MIN_MOVES_FG : CHILD_FG);
            this.pushs.setText(pushs);
            this.pushs.setForeground(minPush ? MIN_PUSHS_FG : CHILD_FG);
            this.country.setText(country);
        }

        /** 固定宽列取 XML 的 dp；{@code wrap_content} 列取文字宽 + 左右 padding。 */
        private static class ChildLayout implements LayoutManager {
            private static final int[] FIXED = {C_INDEX_W, 0, C_NAME_W, C_MOVES_W, C_PUSHS_W, 0};

            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                int w = 0;
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    w += columnWidth(parent, i);
                }
                return new Dimension(w, ROW_CONTENT_HEIGHT);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(Container parent) {
                int h = parent.getHeight();
                int x = 0;
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    int w = columnWidth(parent, i);
                    parent.getComponent(i).setBounds(x, 0, w, h);
                    x += w;
                }
            }

            private static int columnWidth(Container parent, int i) {
                // JLabel 的 preferredSize 已含 EmptyBorder 的左右 4dp
                return FIXED[i] > 0 ? FIXED[i] : parent.getComponent(i).getPreferredSize().width;
            }
        }
    }

    /** 原版静止时不画滚动条，且不占宽度。 */
    private static class InvisibleScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = LIST_BG;
            this.trackColor = LIST_BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(0, 0);
        }
    }

    // ------------------------------------------------------------ 测试辅助

    /** 供测试取树的展开行数等状态 */
    JTree getTree() {
        return tree;
    }

    myActionBar getActionBar() {
        return actionBar;
    }
}
