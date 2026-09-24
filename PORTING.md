# 推箱快手 Android → PC (Java Swing) 移植计划

> **⚠️ 目录结构变更（2026-09-24）**
> 本计划书写于移植开始前，文中出现的 `BoxManPC/` 目录**现已更名为 `desktop/`**；
> 原版 Android 工程（原根目录的 `app/` 及根级 Gradle 文件）整体移入 `android/`。
> 仓库根目录现在只有：`desktop/`（移植产物）、`android/`（原版参考）、
> `README.md`、`PORTING.md`（本文）、`PORTING_AUDIT.md`。
> **文中其余内容（类名如 `BoxManPC.java`、`my.boxman.BoxManPC`、`BoxManPC.jar`）均未变，无需换算。**

## 项目概况与移植原则

将 **推箱快手**（Sokoban / BoxMan）从 Android 平台完整移植到 PC (Java SE + Swing)。全工程约 39,600 行 Java 源码、41 个 XML 布局、33 个 PNG 资源。

### 核心移植原则：**严格 1:1 原样等价移植**
1. **不修改 UI 布局**：严格复刻原版全部 41 个界面的布局结构与视觉排布（包括主页分类列表、游戏窗口顶部画布与底部 7 按钮工具栏、编辑器 8 按钮工具栏、各级对话框控件排布）。
2. **不改变原有具体功能**：保留原版全部业务逻辑、数据流与参数设置，不增删功能。
3. **不添加 PC 专属功能**：不引入 PC 专属的方向键推箱、快捷键体系或非原有的剪贴板操作，操作方式完全等价映射原版触屏交互（鼠标点击寻径/推箱、拖拽平移、滚轮缩放、底栏按钮点击）。
4. **最小修改**：语言保持纯 Java 不变，依靠轻量兼容桥使原业务代码以最小代价在 PC 端正常稳定运行。

## 代码分类总览

### 🟢 零修改直接复用（~10,200 行）

| 文件/包 | 行数 | 说明 |
|---------|------|------|
| `jsoko/` 全部 | ~8,100 | 求解器、死锁检测、下界估算，纯 Java 算法 |
| `myPathfinder.java` | 1,040 | BFS/A* 寻径、割点/图块，纯 Java |
| `IniFile.java` | 341 | INI 文件读写，纯 Java |
| `StringCompress.java` | 268 | 字符串压缩，纯 Java |
| `UnicodeInputStream.java` | ~90 | BOM 处理，纯 Java |
| `gifencoder/LzwEncoder.java` | ~300 | LZW 压缩算法，纯 Java |
| `gifencoder/NeuQuant.java` | ~400 | 颜色量化算法，纯 Java |

### 🟡 需适配但逻辑可大量保留（~5,500 行）

| 文件 | 行数 | 改动点 |
|------|------|--------|
| `mySQLite.java` | 1,920 | 仅改 import 和初始化方式，业务 SQL 不变 |
| `myMaps.java` | 1,641 | 拆分：纯数据部分保留，图形部分（~300 行）重写 |
| `myGameView.java` 中的游戏规则逻辑 | ~2,000 | 推/撤/重做/宏 等核心规则逻辑保留，仅改 UI 调用 |
| `gifencoder/GifEncoder.java` | 553 | 改 `Bitmap` → `BufferedImage`（~20 行） |

### 🔴 需要重写的 UI/平台层（~23,900 行）

所有 Activity/Fragment 的 `onCreate`/布局绑定、触摸事件、Android 对话框、菜单系统、Adapter、Handler 定时器等。

---

## 阶段 0：项目脚手架 (Day 1)

### 0.1 创建 Gradle Java SE 项目

```
desktop/          # 计划书原写作 BoxManPC/
├── build.gradle              # Java SE 项目
├── src/main/java/my/boxman/  # 源码（从 Android 项目复制）
├── src/main/resources/       # 资源文件
│   ├── assets/BoxMan.db      # SQLite 数据库
│   ├── drawable/             # PNG 资源（从 res/drawable/ 复制）
│   └── assets/flower.jpg, net.jpg
└── libs/                     # 外部 jar
```

**build.gradle**:
```groovy
plugins {
    id 'java'
    id 'application'
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass = 'my.boxman.BoxManPC'
}

dependencies {
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'
    implementation 'com.formdev:flatlaf:3.4'
    implementation 'com.googlecode.json-simple:json-simple:1.1.1'
    implementation 'com.miglayout:miglayout-swing:11.3'  // 简化布局
}
```

### 0.2 复制源码和资源

1. 将 `android/app/src/main/java/my/boxman/` 全部复制到 `desktop/src/main/java/my/boxman/`
2. 将 `android/app/src/main/assets/` 复制到 `desktop/src/main/resources/assets/`
3. 将 `android/app/src/main/res/drawable/*.png` 复制到 `desktop/src/main/resources/drawable/`
4. 删除 `service/MyService.java`（PC 端无需保活）

### 0.3 验收标准
- [ ] jsoko 包和 `myPathfinder.java`、`IniFile.java`、`StringCompress.java` 能直接编译通过

---

## 阶段 1：平台兼容层 (Day 2-4)

> **核心思路**：编写一层轻量兼容 API，使现有业务代码只需改 import 而无需改逻辑。

### 1.1 SQLite JDBC 兼容桥 (~250 行新代码)

创建 `my.boxman.compat.sqlite` 包，提供与 Android API 同名的包装类：

> **注意**：`sqlite-jdbc` 的 `ResultSet` 仅支持 `TYPE_FORWARD_ONLY`，而原代码中有多处
> `cursor.getCount()` 调用（第 610、656、675、1127、1169 行等）。因此 `Cursor` 必须采用
> **内存快照**方案——在 `rawQuery` 执行时一次性将结果集读入 `List<Object[]>`，而非直接包装
> 活动的 `ResultSet`。数据库仅 1.17 MB，单次查询通常只有几十到几百条，内存开销可忽略。

#### [NEW] `compat/sqlite/SQLiteDatabase.java`

```java
package my.boxman.compat.sqlite;

import java.sql.*;
import java.util.*;

public class SQLiteDatabase {
    public static final int OPEN_READONLY = 1;
    private Connection conn;

    public static SQLiteDatabase openDatabase(String path, Object factory, int flags) {
        SQLiteDatabase db = new SQLiteDatabase();
        db.conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        return db;
    }

    public static SQLiteDatabase openOrCreateDatabase(String path, Object factory) {
        return openDatabase(path, null, 0);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        PreparedStatement ps = conn.prepareStatement(sql);
        if (selectionArgs != null) {
            for (int i = 0; i < selectionArgs.length; i++)
                ps.setString(i + 1, selectionArgs[i]);
        }
        ResultSet rs = ps.executeQuery();
        return Cursor.fromResultSet(rs); // 一次性读入内存，随后关闭 rs
    }

    public Cursor rawQuery(String sql, String[] selectionArgs, Object cancelSignal) {
        return rawQuery(sql, selectionArgs);
    }

    // Android SQLiteDatabase.query() —— mySQLite.java 中有 36 处调用
    public Cursor query(String table, String[] columns, String where,
                        String[] whereArgs, String groupBy, String having, String orderBy) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns == null ? "*" : String.join(",", columns));
        sql.append(" FROM ").append(table);
        if (where != null) sql.append(" WHERE ").append(where);
        if (groupBy != null) sql.append(" GROUP BY ").append(groupBy);
        if (having != null) sql.append(" HAVING ").append(having);
        if (orderBy != null) sql.append(" ORDER BY ").append(orderBy);
        return rawQuery(sql.toString(), whereArgs);
    }

    public void execSQL(String sql) { conn.createStatement().execute(sql); }

    public void execSQL(String sql, Object[] bindArgs) {
        PreparedStatement ps = conn.prepareStatement(sql);
        if (bindArgs != null) {
            for (int i = 0; i < bindArgs.length; i++)
                ps.setObject(i + 1, bindArgs[i]);
        }
        ps.execute();
    }

    public long insert(String table, String nullColumnHack, ContentValues values) { ... }
    public int delete(String table, String whereClause, String[] whereArgs) { ... }

    public void close() { conn.close(); }
}
```

#### [NEW] `compat/sqlite/Cursor.java`

```java
package my.boxman.compat.sqlite;

import java.sql.*;
import java.util.*;

/**
 * 基于内存快照的 Cursor 包装。
 * 在构造时一次性将 JDBC ResultSet 读入 List<Object[]>，
 * 从而支持 getCount() 和任意方向遍历，规避 sqlite-jdbc 的 TYPE_FORWARD_ONLY 限制。
 */
public class Cursor {
    private List<Object[]> rows = new ArrayList<>();
    private Map<String, Integer> colMap = new HashMap<>();
    private int colCount;
    private int currentIndex = -1;

    // 从 ResultSet 一次性读取全部数据，随后关闭 ResultSet
    public static Cursor fromResultSet(ResultSet rs) throws SQLException {
        Cursor c = new Cursor();
        ResultSetMetaData meta = rs.getMetaData();
        c.colCount = meta.getColumnCount();
        for (int i = 1; i <= c.colCount; i++)
            c.colMap.put(meta.getColumnName(i), i - 1); // Android Cursor 是 0-indexed
        while (rs.next()) {
            Object[] row = new Object[c.colCount];
            for (int i = 0; i < c.colCount; i++)
                row[i] = rs.getObject(i + 1);
            c.rows.add(row);
        }
        rs.close();
        return c;
    }

    public int getCount()             { return rows.size(); }
    public boolean moveToFirst()      { currentIndex = 0; return currentIndex < rows.size(); }
    public boolean moveToNext()       { currentIndex++; return currentIndex < rows.size(); }
    public int getInt(int col)        { return ((Number) rows.get(currentIndex)[col]).intValue(); }
    public long getLong(int col)      { return ((Number) rows.get(currentIndex)[col]).longValue(); }
    public String getString(int col)  { Object v = rows.get(currentIndex)[col]; return v == null ? null : v.toString(); }
    public int getColumnIndex(String name) { Integer i = colMap.get(name); return i != null ? i : -1; }
    public void close()               { rows.clear(); }
}
```

#### [NEW] `compat/sqlite/ContentValues.java`

```java
// 简单的 String→Object 映射，模拟 Android ContentValues
public class ContentValues extends LinkedHashMap<String, Object> {
    public void put(String key, String value) { super.put(key, value); }
    public void put(String key, Integer value) { super.put(key, value); }
    public void put(String key, Long value) { super.put(key, value); }
}
```

#### [MODIFY] `mySQLite.java`
- 将 `import android.database.*` → `import my.boxman.compat.sqlite.*`
- 将 `import android.content.Context` 相关的初始化改为直接读取文件路径
- `copyDataBase()` 改为从 classpath 资源复制：`getClass().getResourceAsStream("/assets/BoxMan.db")`
- **其余 1,800+ 行的 SQL 业务逻辑完全不变**

### 1.2 图形抽象层 (~200 行新代码)

创建 `my.boxman.compat.graphics` 包，提供类型别名和工具方法：

#### [NEW] `compat/graphics/PlatformGraphics.java`

```java
package my.boxman.compat.graphics;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 提供 Android graphics API 到 AWT 的映射工具。
 * 不是完整包装——只封装高频操作，其余在调用点直接改写。
 */
public class PlatformGraphics {
    // Bitmap → BufferedImage 工厂
    public static BufferedImage createBitmap(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    // BitmapFactory.decodeFile → ImageIO.read
    public static BufferedImage decodeFile(String path) {
        return ImageIO.read(new File(path));
    }

    // BitmapFactory.decodeResource → ClassLoader resource
    public static BufferedImage decodeResource(String name) {
        return ImageIO.read(PlatformGraphics.class.getResourceAsStream("/drawable/" + name + ".png"));
    }

    // Android Rect → AWT Rectangle 互转
    public static Rectangle toRect(int l, int t, int r, int b) {
        return new Rectangle(l, t, r - l, b - t);
    }
}
```

### 1.3 资源加载器 (~80 行新代码)

#### [NEW] `compat/ResourceLoader.java`

替代 Android 的 `Resources` 和 `R.drawable.*`：

```java
public class ResourceLoader {
    private static Map<String, BufferedImage> cache = new HashMap<>();

    // 替代 res.getDrawable(R.drawable.xxx)
    public static BufferedImage getDrawable(String name) {
        return cache.computeIfAbsent(name, n ->
            ImageIO.read(ResourceLoader.class.getResourceAsStream("/drawable/" + n + ".png"))
        );
    }
}
```

### 1.4 剪贴板兼容 (~15 行)

在 `myMaps.java` 中替换两处剪贴板调用：

```java
// loadClipper(): Android ClipboardManager → AWT Clipboard
static String loadClipper() {
    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
    return (String) cb.getData(DataFlavor.stringFlavor);
}

// saveClipper(): 同理
static void saveClipper(String data) {
    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
    cb.setContents(new StringSelection(data), null);
}
```

### 1.5 SharedPreferences → Properties/IniFile (~30 行)

在 `myGameView` 和 `myActGMView` 中共 5 处 `SharedPreferences` 调用，统一改为使用项目已有的 `IniFile.java`：

```java
// 原: SharedPreferences sp = getSharedPreferences("BoxMan", MODE_PRIVATE);
// 改: IniFile ini = new IniFile(dataDir + "/BoxMan.ini");
```

### 1.6 Apache HttpClient → HttpURLConnection (~40 行)

`BoxMan.java` 和 `mySubmitList.java` 中共 5 处 `HttpGet` 调用，改为 `java.net.HttpURLConnection`（项目中 `mySubmit.java` 已在使用此方案，保持一致）。

### 1.7 GifEncoder 适配 (~20 行)

#### [MODIFY] `gifencoder/GifEncoder.java`
- `import android.graphics.Bitmap` → `import java.awt.image.BufferedImage`
- `import android.graphics.Color` → `import java.awt.Color`
- `image.getPixels(pixelInts, ...)` → `image.getRGB(0, 0, w, h, pixelInts, 0, w)`
- 像素通道顺序：Android 是 ARGB packed int，AWT `BufferedImage.TYPE_INT_ARGB` 也是 ARGB，**格式一致无需调整**

### 阶段 1 验收标准
- [ ] `mySQLite.java` 能通过兼容层正常读写 `BoxMan.db`（写单元测试验证）
- [ ] `myMaps.loadSkins()` 能用 `BufferedImage` 正确切割皮肤精灵图
- [ ] 剪贴板读写正常
- [ ] `GifEncoder` 能接收 `BufferedImage` 并输出 GIF 文件

---

## 阶段 2：核心游戏视图 (Day 5-9)

> 这是移植的**核心战场**——将 `myGameViewMap`（2,773 行）和 `myGameView`（5,907 行）移植到 Swing。

### 2.1 myGameViewMap → JPanel (~2,773 行改造)

#### 类声明与基础框架

```java
// 原: public class myGameViewMap extends View
// 改:
public class myGameViewMap extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    // 原 Android 字段中的类型替换:
    // Bitmap → BufferedImage
    // Paint → (直接用 Graphics2D 属性)
    // Matrix → AffineTransform
    // Rect → Rectangle
    // PointF → Point2D.Float
    // Drawable → BufferedImage

    public myGameViewMap() {
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }
}
```

#### 渲染管线改造

| Android API | Swing 等价物 | 改动量 |
|-------------|-------------|--------|
| `onDraw(Canvas canvas)` | `paintComponent(Graphics g)` | 方法签名改一行 |
| `canvas.drawBitmap(bmp, srcRect, dstRect, paint)` | `g2d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null)` | 逐处替换，约 60 处 |
| `canvas.drawText(text, x, y, paint)` | `g2d.drawString(text, x, y)` | ~20 处 |
| `canvas.drawLine(...)` | `g2d.drawLine(...)` | ~10 处 |
| `canvas.drawRect(...)` | `g2d.drawRect(...)` / `g2d.fillRect(...)` | ~15 处 |
| `canvas.drawCircle(...)` | `g2d.drawOval(...)` | ~5 处 |
| `canvas.save()` / `canvas.restore()` | `g2d.getTransform()` / `g2d.setTransform(saved)` | ~5 处 |
| `canvas.setMatrix(matrix)` | `g2d.setTransform(affineTransform)` | ~3 处 |
| `paint.setARGB(a,r,g,b)` | `g2d.setColor(new Color(r,g,b,a))` | ~15 处 |
| `paint.setTextSize(size)` | `g2d.setFont(font.deriveFont(size))` | ~10 处 |
| `invalidate()` | `repaint()` | 全局替换，约 40 处 |
| `setBackgroundColor(color)` | `setBackground(new Color(color))` | ~2 处 |

#### 输入事件与视口适配改造

**触摸 → 鼠标 1:1 等价映射**（不增加任何非原版键盘快捷键，完全保留原版操作习惯）：

| Android 触摸操作 | PC 鼠标操作 | 对应原版功能 |
|-----------------|------------|-------------|
| `MotionEvent.ACTION_DOWN` | `mousePressed` | 记录触点坐标 |
| `MotionEvent.ACTION_UP` | `mouseReleased` | 触点抬起，触发移动/推箱 |
| `MotionEvent.ACTION_MOVE` | `mouseDragged` | 视野平移拖拽 |
| 双指捏合缩放 (`ACTION_POINTER_DOWN`) | `mouseWheelMoved` (滚轮) | 视野放大/缩小 |
| `GestureDetector.onSingleTapConfirmed` | 鼠标左键单击 | 自动寻径移动仓管员 / 推动箱子 |
| `GestureDetector.onLongPress` | 鼠标长按 / 右键单击 | 触发单元格统计或长按快捷操作 |
| `GestureDetector.onDoubleTap` | 鼠标双击 | 触发双击操作 |
| 底栏 CheckBox 点击 | 鼠标左键点击 | 触发 Undo/Redo/IM/BK/Sel/TR/More |

#### 视口几何自适应 (原 `onSizeChanged` 映射)
原工程在 `myGameViewMap.java` 第 2658 行通过 `onSizeChanged` 调用 `setArena()` 重算舞台居中与尺寸。在 Swing 中通过组件大小监听器等价触发：

```java
addComponentListener(new ComponentAdapter() {
    @Override
    public void componentResized(ComponentEvent e) {
        setArena();  // 触发原版自带的舞台视口重算，保证棋盘居中与变换矩阵正常
        repaint();
    }
});
```

#### 字体大小计算函数 `sp2px` 兼容
原工程使用 `sp2px(Context, float)` 计算标尺和字号。在 PC 端直接映射为：
```java
private int sp2px(Object context, float spValue) {
    // FlatLaf 现代主题已原生接管系统级 HiDPI 缩放，直接返回标准字号即可
    return (int) spValue;
}
```

#### 缩放/平移矩阵

```java
// 原: Matrix mCurrentMatrix = new Matrix();
// 改: AffineTransform mCurrentTransform = new AffineTransform();

// 原: mCurrentMatrix.postTranslate(dx, dy);
// 改: mCurrentTransform.translate(dx, dy);

// 原: mCurrentMatrix.postScale(scale, scale, mid.x, mid.y);
// 改: mCurrentTransform.translate(mid.x, mid.y);
//     mCurrentTransform.scale(scale, scale);
//     mCurrentTransform.translate(-mid.x, -mid.y);

// 原: mCurrentMatrix.getValues(values); float sx = values[Matrix.MSCALE_X];
// 改: double sx = mCurrentTransform.getScaleX();
```

### 2.2 myGameView → JFrame + 游戏逻辑 (~5,907 行)

#### 窗口结构

```java
public class myGameView extends JFrame {
    myGameViewMap mMap;
    JPanel bottomBar;       // 底部按钮栏
    JToggleButton bt_UnDo, bt_ReDo, bt_More, bt_IM, bt_Sel, bt_TR, bt_BK;
    JMenuBar menuBar;

    // 游戏逻辑字段（全部保留不变）
    LinkedList<Byte> m_lstMovUnDo, m_lstMovReDo;
    // ... (所有非 UI 字段原封不动)

    public myGameView() {
        setTitle("推箱快手");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(myMaps.m_nWinWidth, myMaps.m_nWinHeight);

        // 构建布局 (对应 game_view.xml)
        mMap = new myGameViewMap();
        mMap.m_Game = this;

        bottomBar = createBottomBar();

        setLayout(new BorderLayout());
        add(mMap, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);

        // 构建菜单
        menuBar = createMenuBar();
        setJMenuBar(menuBar);

        // 初始化游戏状态（原 onCreate 中的逻辑）
        initGame();
    }
}
```

#### 定时器迁移

| 原 Android Handler | 改为 Swing Timer | 说明 |
|--------------------|-----------------|------|
| `RefreshHandler1` (1 秒周期) | `new Timer(1000, e -> { ... })` | 背景时钟/自动保存 |
| `RefreshHandler2` (可变速度) | `new Timer(m_iSleep[speed], e -> { ... })` | 移动动画播放 |
| `RefreshHandler3` | `new Timer(delay, e -> { ... })` | 演示/回放 |
| `RefreshHandler4` | `new Timer(delay, e -> { ... })` | 宏单步执行 |

所有 Timer 回调自动在 EDT (Event Dispatch Thread) 上执行，天然线程安全，无需额外同步。

#### AsyncTask → SwingWorker 迁移

```java
// 原:
private class AsyncCountBoxsTask extends AsyncTask<Integer, Void, short[][]> {
    protected short[][] doInBackground(Integer... params) { /* 后台计算 */ }
    protected void onPostExecute(short[][] result) { /* 更新 UI */ }
}

// 改:
private class CountBoxsWorker extends SwingWorker<short[][], Void> {
    protected short[][] doInBackground() { /* 后台计算（逻辑完全一样）*/ }
    protected void done() {
        short[][] result = get();
        /* 更新 UI */
    }
}
```

需迁移的 7 个 AsyncTask：

| 原 AsyncTask | 所在文件 | 迁移为 SwingWorker |
|-------------|---------|-------------------|
| `AsyncCountBoxsTask` | myGameView | `CountBoxsWorker` |
| `RunMicroTask` | myGameView | `MacroWorker` |
| `AsyncGifMakeTask` | myGifMakeFragment | `GifMakeWorker` |
| `AsyncLoadImageTask` | myGridViewAdapter | `ImageLoadWorker` |
| `AsyncLoadImageTask` | myPicListViewAdapter | `ImageLoadWorker` |
| `SplitTask` | mySplitLevelsFragment | `SplitWorker` |
| `FindTask` | myFindFragment / myQueryFragment | `FindWorker` |

### 2.3 myMaps.java 图形部分改造

`loadSkins()` 方法（~220 行）需将所有 `Bitmap` 操作改为 `BufferedImage`：

```java
// 原:
FloorPic = Bitmap.createBitmap(50, 50, myMaps.cfg);
Canvas cvsTmp = new Canvas(FloorPic);
Rect rt_ = new Rect(0, 0, 50, 50);
cvsTmp.drawBitmap(myMaps.skinBit, rt_, rt, myPaint);

// 改:
FloorPic = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
Graphics2D g = FloorPic.createGraphics();
g.drawImage(skinBit, 0, 0, 50, 50, srcX, srcY, srcX+50, srcY+50, null);
g.dispose();
```

`loadBKPic()` 方法（~35 行）：
- `BitmapFactory.decodeFile()` → `ImageIO.read(new File(...))`
- `BitmapFactory.Options.inSampleSize` 降采样 → `BufferedImage` 创建后用 `getScaledInstance()` 或 `AffineTransformOp` 缩放

### 阶段 2 验收标准
- [ ] 能打开游戏窗口，显示一个关卡地图（正确渲染地板/墙壁/箱子/人物）
- [ ] 鼠标点击能触发人物移动（寻径行走）与推箱（与原版触屏操作行为 1:1 等价）
- [ ] 底栏 7 个按钮（Undo/Redo/IM/BK/Sel/TR/More）点击响应正常
- [ ] 鼠标滚轮缩放、拖拽平移视野正常
- [ ] 窗口尺寸改变时画布自动居中自适应（`setArena` 正常响应）
- [ ] 完成一个关卡后有通关提示

---

## 阶段 3：主界面与导航框架 (Day 10-12)

### 3.1 Activity 导航图 → Swing 窗口映射

项目共 16 个 Activity 和 5 个 Fragment。映射策略：

| Android 组件 | Swing 映射 | 理由 |
|-------------|-----------|------|
| `BoxMan` (主 Activity) | `BoxManPC extends JFrame` (主窗口，单例) | 程序入口 |
| `myGridView` | `GridViewFrame extends JFrame` | 关卡网格浏览 |
| `myPicListView` | `PicListFrame extends JFrame` | 关卡列表浏览 |
| `myGameView` | `GameFrame extends JFrame` | 游戏主界面 |
| `myEditView` | `EditFrame extends JFrame` | 关卡编辑器 |
| `myFindView` | `FindFrame extends JFrame` | 相似关卡查找 |
| `myRecogView` | `RecogFrame extends JFrame` | 关卡图像识别 |
| `myExport` | `ExportFrame extends JFrame` | 导出界面 |
| `myActGMView` | `ActionManageFrame extends JFrame` | 动作管理 |
| `myStateBrow` | `StateBrowseFrame extends JFrame` | 状态浏览 |
| `mySolutionBrow` | `SolutionBrowseFrame extends JFrame` | 答案浏览 |
| `mySubmitList` | `SubmitListFrame extends JFrame` | 提交列表 |
| `mySubmit` | `SubmitFrame extends JFrame` | 提交界面 |
| `Help` | `HelpFrame extends JFrame` | 帮助 (WebView→JEditorPane) |
| `myAbout/1/2` | `JDialog` (模态) | 关于页面 |
| `myFileExplorerActivity` | `JFileChooser`（**直接使用系统组件**） | 文件选择器 |

**导航流实现**：

```java
// 原 Android: startActivity(new Intent(this, myGridView.class));
// 改:
GridViewFrame frame = new GridViewFrame(setId);
frame.setVisible(true);

// 原 Android: startActivityForResult(intent, REQUEST_CODE);
//              → onActivityResult(requestCode, resultCode, data)
// 改: 利用窗口关闭监听 + 回调
frame.addWindowListener(new WindowAdapter() {
    public void windowClosed(WindowEvent e) {
        // 直接从 myMaps 静态字段读取结果（原项目已大量使用此模式）
        refreshList();
    }
});
```

**关键发现**：原项目的 Activity 间数据传递主要通过 `myMaps` 的静态字段而非 Intent extras。这**简化了移植**——不需要模拟 Intent/Bundle 系统。

### 3.2 主窗口 BoxManPC

```java
public class BoxManPC extends JFrame {
    JTree levelTree;  // 替代 ExpandableListView

    public static void main(String[] args) {
        // 设置 FlatLaf 主题
        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            BoxManPC app = new BoxManPC();
            app.setVisible(true);
        });
    }

    public BoxManPC() {
        setTitle("推箱快手");
        setSize(800, 600);  // PC 合理默认尺寸
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 初始化（原 BoxMan.onCreate 逻辑）
        myMaps.sRoot = System.getProperty("user.home") + "/.boxman";
        myMaps.m_nWinWidth = 800;
        myMaps.m_nWinHeight = 600;

        mySQLite.m_SQL = mySQLite.getInstance();
        mySQLite.m_SQL.openDB();

        myMaps.mSets0 = mySQLite.m_SQL.getSetList(0);
        // ... 加载 4 个分类

        myMaps.loadSkins();

        // 构建关卡树
        levelTree = createLevelTree();
        add(new JScrollPane(levelTree), BorderLayout.CENTER);

        // 构建菜单栏
        setJMenuBar(createMenuBar());
    }

    private JTree createLevelTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("关卡");
        String[] groups = {"入门关卡", "进阶关卡", "花样关卡", "关卡扩展"};
        ArrayList[] sets = {myMaps.mSets0, myMaps.mSets1, myMaps.mSets2, myMaps.mSets3};

        for (int i = 0; i < 4; i++) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(groups[i]);
            for (Object s : sets[i]) {
                group.add(new DefaultMutableTreeNode(((mapSetNode)s).title));
            }
            root.add(group);
        }

        JTree tree = new JTree(root);
        tree.addTreeSelectionListener(e -> { /* 双击打开关卡集 */ });
        return tree;
    }
}
```

### 3.3 文件选择器简化

原 `myFileExplorerActivity`（自定义文件浏览器）在 PC 端直接替换为：

```java
JFileChooser chooser = new JFileChooser();
chooser.setFileFilter(new FileNameExtensionFilter("关卡文件", "txt", "sok", "xsb"));
if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
    String path = chooser.getSelectedFile().getAbsolutePath();
    // 执行导入逻辑
}
```

### 3.4 Help 页面

原 `Help` Activity 使用 `WebView` 显示 HTML 帮助文档。PC 端用 `JEditorPane`：

```java
JEditorPane helpPane = new JEditorPane();
helpPane.setContentType("text/html");
helpPane.setEditable(false);
helpPane.setPage(getClass().getResource("/help/index.html"));
```

### 阶段 3 验收标准
- [ ] 主窗口显示四级关卡树，双击可展开/折叠
- [ ] 点击关卡集可打开关卡网格窗口
- [ ] 从关卡网格可进入游戏窗口
- [ ] 窗口间导航正常（打开/关闭/返回刷新）
- [ ] 文件选择器可选择 .txt/.sok 文件导入

---

## 阶段 4：次要视图移植 (Day 13-17)

### 4.1 关卡网格视图 myGridView

| Android 组件 | Swing 替代 |
|-------------|-----------|
| `GridView` + `myGridViewAdapter` | `JPanel` with `GridLayout` + 自绘缩略图 cell |
| `AsyncLoadImageTask` (异步缩略图) | `SwingWorker` 后台加载 |
| Context Menu (长按) | `JPopupMenu` (右键) |
| `onCreateOptionsMenu` | `JMenuBar` |

### 4.2 关卡编辑器 myEditView + myEditViewMap

结构与 `myGameView` 高度类似——编辑画布是 `myEditViewMap extends View`（改为 `JPanel`），底部工具栏是一排 `CheckBox`（改为 `JToggleButton`）。

编辑器特有的改动：
- 工具选择（墙壁/地板/箱子/目标/人物）用 `ButtonGroup` 实现互斥选择
- 鼠标绘制：`mouseDragged` 时连续放置所选元素
- 尺寸调整对话框 (`size_dialog.xml`) → `JDialog` + `JSpinner`

### 4.3 关卡识别 myRecogView + myRecogViewMap

- 画布部分与其他 ViewMap 同理
- 10 个按钮（地板/墙壁/箱子/目标/人 + 四方向箭头）→ `JButton` 面板

### 4.4 动作管理 myActGMView

- `ListView` + 12 个操作按钮 → `JList` + `JToolBar`

### 4.5 状态/答案浏览 myStateBrow / mySolutionBrow

- `ExpandableListView` → `JTree` 或 `JTable`（与主界面同理）

### 4.6 导出界面 myExport

- 复用已移植的 `myGameViewMap` 做预览
- GIF 参数设置对话框 (`gif_set_dialog.xml`) → `JDialog`

### 4.7 外部求解器 (YASS) 接口等价维持
原版在 `myGameView` 和 `myStateBrow` 中点击“YASS求解”是发 Android Intent 广播唤起外部求解器 `nl.joriswit.sokosolver.SOLVE`。
在 PC 平台为维持该已有功能正常运行（避免 Intent 缺失抛出未捕获异常）：
- 通过 Java `ProcessBuilder` 调用本地可执行的 YASS 求解器（如 `./tools/yass.exe`），输入临时关卡文本并读取标准输出结果；
- 保持原版求解完成后的回调与界面响应完全一致。

### 阶段 4 验收标准
- [ ] 关卡网格能显示缩略图，支持翻页、搜索、排序
- [ ] 关卡编辑器能新建/修改/保存关卡
- [ ] 导出功能能输出 XSB 文本、PNG 图片、GIF 动画
- [ ] 动作管理能回放、编辑动作序列
- [ ] 图像识别功能按原版流程正常加载图片并识别关卡
- [ ] 外部求解器（若配置）能正常回传解题结果，无异常中断

---

## 阶段 5：对话框重建 (Day 18-20)

41 个 XML 布局中，约 20 个是对话框。按复杂度分批处理：

### 5.1 简单对话框（直接用 JOptionPane）

| 原 XML | 替代方案 |
|--------|---------|
| `goto_dialog.xml` | `JOptionPane.showInputDialog("跳转到第几关:")` |
| `del_dialog.xml` | `JOptionPane.showConfirmDialog(...)` + CheckBox panel |
| `new_level_dialog.xml` | `JOptionPane` + 自定义 panel (3 个 JTextField) |

### 5.2 中等对话框（自定义 JDialog + MigLayout）

| 原 XML | Swing 实现 |
|--------|-----------|
| `import_dialog.xml` / `import_dialog2.xml` / `import_dialog3.xml` | `ImportDialog extends JDialog` |
| `export2_dialog.xml` / `export_dialog3.xml` | `ExportSettingsDialog extends JDialog` |
| `find_dialog.xml` | `FindSettingsDialog extends JDialog` |
| `rule_dialog.xml` | `RuleDialog extends JDialog` |
| `gif_set_dialog.xml` | `GifSettingsDialog extends JDialog` |
| `get_uil_dialog.xml` | `UrlInputDialog extends JDialog` |

使用 MigLayout 简化布局代码，示例：

```java
// 替代 color_dialog.xml (RGB 颜色选择器)
JDialog dlg = new JDialog(parent, "选择颜色", true);
JPanel p = new JPanel(new MigLayout("wrap 2", "[right]10[200]"));
JSlider rSlider = new JSlider(0, 255);
JSlider gSlider = new JSlider(0, 255);
JSlider bSlider = new JSlider(0, 255);
JPanel preview = new JPanel();

p.add(new JLabel("R:"));  p.add(rSlider);
p.add(new JLabel("G:"));  p.add(gSlider);
p.add(new JLabel("B:"));  p.add(bSlider);
p.add(new JLabel("预览:")); p.add(preview, "h 50!");
```

### 5.3 复杂对话框

`query_dialog.xml`（~20 个控件，高级关卡查询）是最复杂的对话框，需要独立实现：

```java
public class QueryDialog extends JDialog {
    // 尺寸范围 (行/列 min-max)
    JSpinner rowMin, rowMax, colMin, colMax;
    // 箱子数范围
    JSpinner boxMin, boxMax;
    // 解题状态
    JCheckBox solved, unsolved;
    // 排序方式
    JComboBox<String> sortBy;
    // ... 约 20 个控件
}
```

### 5.4 Fragment → JDialog 迁移

| 原 Fragment | 改为 |
|------------|------|
| `mySplitLevelsFragment` | `SplitDialog extends JDialog` + `SwingWorker` |
| `myQueryFragment` | `QueryDialog extends JDialog` + `SwingWorker` |
| `myExportFragment` | `ExportDialog extends JDialog` + `SwingWorker` |
| `myFindFragment` | `FindDialog extends JDialog` + `SwingWorker` |
| `myGifMakeFragment` | `GifMakeDialog extends JDialog` + `SwingWorker` |

这些 Fragment 原本都包含 `AsyncTask` + `ProgressDialog`，迁移为 `SwingWorker` + `JProgressBar` / `ProgressMonitor`。

### 5.5 Toast → 状态栏消息

```java
// [NEW] 简单的 Toast 替代
public class MyToast {
    // 原: MyToast.showToast(context, "消息", Toast.LENGTH_SHORT);
    // 改: 在主窗口底部 JLabel 显示 3 秒后清除
    public static void showToast(Component parent, String message, int duration) {
        // 找到最近的 JFrame，更新底部状态栏 JLabel
        // 用 Timer 在 3 秒后清除文字
    }
}
```

### 阶段 5 验收标准
- [ ] 所有导入/导出对话框能正常弹出并提交
- [ ] 颜色选择器能实时预览
- [ ] 高级查询对话框能正确构建查询条件
- [ ] 后台任务（拆分/查找/GIF生成）有进度条显示且可取消

---

## 阶段 6：菜单系统重建 (Day 18-20, 与阶段 5 并行)

原项目 12 个 Activity 有独立的菜单，大部分在代码中用 `menu.add()` 动态构建。

### 6.1 myGameView 菜单（最复杂，约 40 项）

```java
private JMenuBar createMenuBar() {
    JMenuBar bar = new JMenuBar();

    // 导航菜单
    JMenu navMenu = new JMenu("导航");
    navMenu.add(createItem("下一关", KeyEvent.VK_RIGHT, this::nextLevel));
    navMenu.add(createItem("上一关", KeyEvent.VK_LEFT, this::prevLevel));
    navMenu.add(createItem("重新开始", KeyEvent.VK_R, this::restart));
    navMenu.addSeparator();
    navMenu.add(createItem("跳转到...", KeyEvent.VK_G, this::gotoLevel));

    // 显示菜单
    JMenu displayMenu = new JMenu("显示");
    JCheckBoxMenuItem showBoxReach = new JCheckBoxMenuItem("箱子可达");
    displayMenu.add(showBoxReach);
    // ... 8+ 显示选项

    // 工具菜单
    JMenu toolMenu = new JMenu("工具");
    toolMenu.add(createItem("录制", this::toggleRecord));
    toolMenu.add(createItem("宏操作", this::runMacro));

    // 操作菜单
    JMenu actionMenu = new JMenu("操作");
    actionMenu.add(createItem("撤销 (Ctrl+Z)", this::undo));
    actionMenu.add(createItem("重做 (Ctrl+Y)", this::redo));

    bar.add(navMenu);
    bar.add(displayMenu);
    bar.add(toolMenu);
    bar.add(actionMenu);
    return bar;
}
```

### 6.2 其他窗口菜单

每个窗口的菜单项都有对应的 `onOptionsItemSelected` 处理逻辑，**这些逻辑代码可以直接复用**，只需将调用点从 `case menuItemId:` 改为 `ActionListener` lambda。

### 6.3 右键上下文菜单

原 Android 的 `ContextMenu`（长按触发）→ Swing 的 `JPopupMenu`（右键触发）：

```java
// myGridView 中的关卡右键菜单
JPopupMenu popup = new JPopupMenu();
popup.add(createItem("编辑关卡", this::editLevel));
popup.add(createItem("删除关卡", this::deleteLevel));
popup.add(createItem("复制关卡", this::copyLevel));
popup.add(createItem("移动关卡", this::moveLevel));

mGridPanel.setComponentPopupMenu(popup);
```

---

## 阶段 7：测试与验收 (Day 21-23)

### 7.1 功能验证清单

#### 核心游戏流程
- [ ] 打开应用 → 显示关卡树 → 选择关卡集 → 选择关卡 → 进入游戏
- [ ] 鼠标点击移动人物与推箱（自动寻径/推箱，与原版触屏操作完全一致）
- [ ] 底栏 7 按钮点击与功能触发正常（Undo/Redo/IM/BK/Sel/TR/More）
- [ ] 推箱逻辑正确（不能推入墙壁、不能推两个箱子）
- [ ] Undo/Redo 正常
- [ ] 通关检测 + 自动保存答案
- [ ] 正推/逆推模式切换
- [ ] 死锁检测标记正确显示

#### 关卡管理
- [ ] 导入关卡文件（XSB/SOK/TXT 格式）
- [ ] 从剪贴板导入关卡
- [ ] 导出为文本/图片/GIF
- [ ] 关卡集的增删改查
- [ ] 关卡排序、搜索、跳转

#### 编辑器
- [ ] 新建空白关卡
- [ ] 放置/擦除元素
- [ ] 旋转、翻转、缩放关卡
- [ ] 测试游玩（从编辑器直接进入游戏）
- [ ] 保存编辑结果

#### 高级功能
- [ ] GIF 动画导出（正确渲染每帧）
- [ ] 宏脚本执行
- [ ] 相似关卡查找
- [ ] 答案/状态的保存与恢复
- [ ] 关卡图像识别

### 7.2 兼容性测试
- [ ] Windows 10/11
- [ ] macOS (如有条件)
- [ ] Linux (如有条件)
- [ ] JDK 8 / JDK 17 / JDK 21 编译与运行

### 7.3 打包分发

```groovy
// 使用 jpackage (JDK 14+) 或 launch4j 打包为原生安装包
tasks.register('packageApp', Exec) {
    commandLine 'jpackage',
        '--input', 'build/libs',
        '--main-jar', 'BoxManPC.jar',
        '--main-class', 'my.boxman.BoxManPC',
        '--name', '推箱快手',
        '--icon', 'src/main/resources/drawable/icon.png',
        '--type', 'msi'  // Windows: msi, macOS: dmg, Linux: deb
}
```

---

## 工作量估算

| 阶段 | 天数 | 核心工作 |
|------|------|---------|
| 0: 项目脚手架 | 1 | 建工程、复制文件、确认纯算法编译 |
| 1: 平台兼容层 | 3 | SQLite 桥、图形抽象、资源加载、剪贴板、SharedPreferences |
| 2: 核心游戏视图 | 5 | myGameViewMap 渲染+输入、myGameView 窗口+逻辑、定时器、AsyncTask |
| 3: 主界面与导航 | 3 | 主窗口、窗口导航框架、文件选择器 |
| 4: 次要视图 | 5 | 编辑器、网格视图、识别、动作管理、状态/答案浏览、导出 |
| 5: 对话框 | 3 | ~20 个对话框重建、Fragment→JDialog、Toast 替代 |
| 6: 菜单系统 | (与 5 并行) | 12 个窗口的菜单 + 右键菜单 |
| 7: 测试与打包 | 3 | 全功能验证、兼容性测试、打包分发 |
| **合计** | **~23 天** | 单人全职，保守估计 |

> [!WARNING]
> 实际执行中最大的风险在于**阶段 2 的渲染管线调试**。`myGameViewMap.onDraw()` 有约 1,800 行绘制逻辑，涉及大量坐标计算、矩阵变换、条件绘制。这部分需要逐步移植、频繁运行验证，可能超出预估时间。

> [!TIP]
> **优先级建议**：如果时间有限，可以先完成阶段 0-3（约 12 天），此时已经有一个可玩的推箱子游戏。阶段 4-6 的功能可以后续逐步补充。
