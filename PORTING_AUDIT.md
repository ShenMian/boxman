# PORTING.md 执行情况审计

审计时间：2026-09-24
**最近刷新：2026-09-25**（阶段 A~G 全部收口后重跑；见第 0 节与第 7 节）
审计对象：`PORTING.md`（移植计划书） vs `desktop/`（实际产物）
审计方式：类清单/行数对比、`res/menu/*.xml` 逐项匹配、`res/layout/*.xml` 控件核对、
`javac` 全量编译、全量测试运行、源码逐段对读。

---

## 0. 结论摘要

> **本节是「当前口径」，随每次收口刷新。** 首次审计（2026-09-24）的逐条结论保留在
> 第 1~7 节里，各节标题上的状态标记已就地改成现状。
> **本次刷新：2026-09-25，阶段 A~G 全部收口之后。**

### 0.1 逐阶段：审计时 → 现在

| 维度 | 审计时（2026-09-24） | 现在（2026-09-25） |
|------|----------------------|--------------------|
| 阶段 0 脚手架 | ✅ 忠实执行 | ✅ 不变 |
| 阶段 1 平台兼容层 | ✅ 执行，但**留了一个会静默毁库的健壮性 bug** | ✅ 缺陷已修（阶段 A） |
| 阶段 2 核心游戏视图 | ✅ 忠实执行（行数甚至超过原版） | ✅ 不变 |
| 阶段 3 主界面与导航 | ⚠️ `mySubmit` / `OpenState` 缺失 | ✅ 已回补（阶段 C） |
| 阶段 4 次要视图 | ⚠️ 窗口都在，但功能普遍缩水 30%~80% | ✅ 已回补（阶段 D-1 / D-2 + G ①③⑤），保留率升到 **46%~119%** |
| 阶段 5 对话框 | ✅ 执行，且**保真度高于计划书要求** | ✅ 不变 |
| 阶段 6 菜单系统 | ❌ **执行得最差**，84 项缺 28~47 项 | ✅ **81 项 / strict 缺 0 / loose 缺 0** |
| 阶段 7 测试与打包 | ⚠️ 测试 57 项全绿，**没有打包** | ✅ 39 类 / 412 用例全绿；jpackage 已补（阶段 F） |
| 四条移植原则 | ⚠️ 原则 1（布局）大致守住；原则 2/3 有明确违反 | ✅ 原则 2/3 的违反已逐条清掉（见第 2、4 节） |

### 0.2 总体判断

审计当时的判断是：**「骨架（兼容层 + 渲染 + 主流程）忠实度很高，血肉（菜单、次要视图细节、
网络写路径、Toast 反馈）大面积缺失，并且存在一处『凭空造数据』和一处『静默毁库』的实质缺陷。」**

**这四条现在都不成立了** —— 两处实质缺陷在阶段 A / D-2 修掉（见 2.1、2.3），
血肉部分按阶段 A~G 逐条回补。当前口径：

- **功能面**：`PORTING.md` 点名的映射项全部落地；菜单保真度 **81 / strict 缺 0 / loose 缺 0**。
- **结构面**：全项目**无 `JMenuBar`**、**无裸 `new JPopupMenu()` / `new JMenuItem()`**、
  **无 `JFileChooser`**、**无 `myActionBar.NO_OP` 占位项** —— 全部由源码扫描式测试锁死。
- **测试面**：**39 个用例类 / 412 个用例 / 0 失败**，`./gradlew --offline clean test` 全绿。

**仍然存在的偏差**（不掩盖，逐条留在文里）：

| 项 | 说明 |
|---|---|
| 窗口尺寸 | 15 个全屏窗口已是 370×780；**11 个 `AlertDialog` 等价物仍是 PC 尺寸（340~540 宽）**，待处理 |
| 窗口主题 | `myAbout` / `myAbout1` / `myAbout2` / `Help` / `myExport` 没套原版 `Theme.Holo` 的深色 `windowBackground`（纯黑）；`myGameView.showSetup2Dialog` 应改回 `HoloAlertDialog` + 多选列表（阶段 G ⑤ 新开条目） |
| 兼容性验证 | 只在 **Windows + JDK 26** 上验证过；macOS / Linux / JDK 8·17·21 未测（计划书 7.2） |
| 行数仍有差距 | `myExport` 46%、`myFindView` 77%、`myEditView` 79%、`myGameView` 82% —— 缩掉的部分已逐项核过，剩下的是 Android 样板（`findViewById` / `AlertDialog` / `Menu`），但**没有再做逐行复核** |
| 细节口径 | `myActGMView` 按钮行仍用 `4dp` strut 而非原版的 `layout_margin="2dp"`；`myFindView` 相似度闸门分母取源关卡面积、`ARGB_4444` 量化等「照抄原版怪癖」的地方，见各阶段小节 |

> 逐阶段的**证据与改法**在第 1~7 节；阶段 A~G 的收口记录在「阶段 A」…「阶段 G ⑤」各小节。

代码健康度（2026-09-25 实测）：

```
主源码 98 个 .java  → javac 零错误（0 error）
测试源 39 个 .java  → javac 零错误
测试运行            → 39 个用例类 / 412 个用例 全部通过
菜单保真度          → 81 项 / strict 缺 0 / loose 缺 0
```

---

## 1. 逐阶段核对

### 阶段 0：项目脚手架 — ✅

| 计划书要求 | 实际 | 判定 |
|-----------|------|------|
| 建 Java SE Gradle 工程 | `desktop/build.gradle` + `settings.gradle` | ✅ |
| 依赖 sqlite-jdbc / flatlaf / json-simple / miglayout | 4 项全在，另加 junit | ✅ |
| `mainClass = 'my.boxman.BoxManPC'` | 一致 | ✅ |
| `sourceCompatibility 1.8` | 一致 | ✅ |
| 复制 `res/drawable/*.png` | `resources/drawable/` **33 个**，原版 `res/drawable/` 也是 **33 个** | ✅ 1:1 |
| 复制 `assets/` | `resources/assets/` **3 个**，原版 `assets/` 也是 **3 个** | ✅ 1:1 |
| 删除 `service/MyService.java` | PC 侧无 `service/` 包 | ✅ |
| 验收：jsoko + 纯算法类编译通过 | 全量 javac 通过 | ✅ |

### 阶段 1：平台兼容层 — ✅ 功能到位，原 2 处实质缺陷已修（阶段 A，2026-09-24）

计划书点名的产物**全部落地**：

| 计划书 [NEW] | 实际文件 | 判定 |
|-------------|---------|------|
| `compat/sqlite/SQLiteDatabase.java` | 存在 | ✅ |
| `compat/sqlite/Cursor.java`（内存快照方案） | 存在，确实用了 `fromResultSet` 快照 | ✅ |
| `compat/sqlite/ContentValues.java` | 存在 | ✅ |
| `compat/graphics/PlatformGraphics.java` | 存在 | ✅ |
| `compat/ResourceLoader.java` | 存在 | ✅ |
| 剪贴板 → AWT Clipboard | `myMaps` 已替换 | ✅ |
| SharedPreferences → IniFile | 已替换 | ✅ |
| HttpClient → HttpURLConnection | `mySubmitList` 用 `HttpURLConnection` + json-simple | ✅ |
| GifEncoder `Bitmap`→`BufferedImage` | 已改 | ✅ |

额外产出（计划书未要求）：`compat/UiWindow`、`compat/HoloAlertDialog`、`compat/HoloContent`、
`compat/HoloMessageDialog`、`compat/HoloProgressDialog`、`compat/android/{graphics,view,widget}/*`。
这些是为「逐像素还原」服务的，属于超出计划的正确投入。

**阶段 1 验收标准**（`Phase1CompatTest`，6 项全绿）：
- SQLite 读写：`sets=7, levels=(1/2614)` ✅
- 剪贴板读写 ✅
- GifEncoder 输出 ✅
- `loadSkins()` 切图 `WallPic=50x50` ✅

#### ⚠️ 缺陷 1：`checkDataBase()` / `copyDataBase()` 会静默造出一个 0 字节数据库，且永久无法自愈

`mySQLite.java:40-100` 是原版逻辑的忠实直译，但**语义前提变了**：

```java
// mySQLite.java:84  checkDataBase()
checkDB = SQLiteDatabase.openDatabase(databaseFilename, null, SQLiteDatabase.OPEN_READONLY);
...
return checkDB == null ? false : true;   // 「能打开」就当成「库存在」
```

```java
// mySQLite.java:63-72  copyDataBase()
is = mySQLite.class.getResourceAsStream("/assets/" + dbName);
while (is != null && (count = is.read(buffer)) != -1) { os.write(...); }
// ↑ is == null 时不抛异常、不写数据，但 os 已经建了文件 → 留下 0 字节 BoxMan.db
```

失效链：
1. 资源不在 classpath（或任何导致 `getResourceAsStream` 返回 null 的情况）
   → `copyDataBase()` **静默写出一个 0 字节的 `BoxMan.db`**；
2. 0 字节的 sqlite 文件是**合法可打开**的 → `checkDataBase()` 返回 `true`
   → 以后**再也不会**重新复制；
3. 之后每一条 SQL 都抛 `no such table: G_Set`，且**无法通过重启自愈**。

实证（磁盘上留下的物证）：

```
build/test_boxman_phase7nullDataBase/BoxMan.db    size=0          tables=[]
build/test_boxman_phase4nullDataBase/BoxMan.db    size=0          tables=[]
build/test_boxman_phase4bnullDataBase/BoxMan.db   size=0          tables=[]
build/test_boxman_phase5nullDataBase/BoxMan.db    size=0          tables=[]
```

清理这 4 个目录后重跑 `Phase7SystemIntegrationTest`：**立刻 4/4 通过**（库正确复制为 1,180,672 字节）。
说明失败 100% 由「被 0 字节库污染且不自愈」造成，而非业务逻辑错误。

附带问题：`myMaps.java:50` 是 `static String sPath;`（**无初值 = null**）。
`Phase7SystemIntegrationTest.setUp()` 只设了 `sRoot` 没设 `sPath`，
于是路径拼成 `...test_boxman_phase7nullDataBase/`（目录名里带字面量 "null"）。
生产代码 `BoxManPC.java:118` 设了 `sPath = "/"`，所以线上路径是对的 —— 但这也意味着
**这个 bug 在开发机上永远不会暴露**（开发机 `~/.boxman/DataBase/BoxMan.db` 早就存在），
只有全新安装的用户才会踩到。

> **✅ 已修（阶段 A，2026-09-24）：** 上面两条缺陷都已收口 ——
> `copyDataBase()` 在 `getResourceAsStream` 返回 null 时**抛异常并删掉半成品文件**，
> `checkDataBase()` 增加「文件长度 > 0 且 `sqlite_master` 里有表」的判定（0 字节库自愈）；
> `myMaps.sPath` 已给默认值 `"/"`。回归锁：`Phase7SystemIntegrationTest`（清理污染目录后 4/4 绿）。
> 缺陷描述保留在此，供理解当初的失效链。

### 阶段 2：核心游戏视图 — ✅ 忠实

| 文件 | 原版行数 | PC 行数 | 判定 |
|------|---------|--------|------|
| `myGameViewMap.java` | 2,772 | **2,908** | ✅ 渲染管线完整搬过来了 |
| `myGameView.java` | 5,906 | 4,554 | ✅ 缩水部分主要是 Android UI/Handler 样板 |

`Phase2GameViewTest`、`Phase3GameViewSnapshotTest`（含 HiDPI 设备缩放不变量回归）、
`Phase3NavigationTest` 全绿。`myMaps.java` 1,640 → 1,493。

### 阶段 3：主界面与导航 — ✅ 已回补（阶段 C，2026-09-24）

| 计划书要求 | 实际 | 判定 |
|-----------|------|------|
| `BoxMan` → 主窗口单例 | `BoxManPC extends JFrame` | ✅ |
| `myFileExplorerActivity` → 文件选择 | ✅ **全项目已 0 处 `JFileChooser`**（阶段 G ①） | 前两处已在阶段 E 撤掉：`BoxManPC` 的「导入...」走原版 `sel_Set()`，`myGridView` 的文档导入走「导入/」目录列表。最后一处 `myPicListView.browseLocalImage()` 随阶段 G ① 的重写一起删掉 —— 原版根本没有系统文件选择器，「换目录」走的是 ActionBar「位置」→「图片位置」5 项单选 →「修改」→ `myFileExplorerActivity` |
| `mySubmitList` → 提交列表 | `mySubmitList.java`（754 行，比原版 505 行还厚） | ✅ |
| **`mySubmit` → `SubmitFrame`** | `mySubmit.java`（475 行，1:1 移植） | ✅ 阶段 C 已回补（审计时确为缺失，见下） |
| `Help` → `JEditorPane` | 实际用 `JTextArea`（见第 3 节，原版其实是 `TextView`） | ⚠️ 偏差但更接近原版 |

计划书 3.1 映射表里的类名（`GridViewFrame`/`PicListFrame`/`GameFrame`/`EditFrame`…）
**一个都没采用**，PC 全部保留原名（`myGridView`/`myPicListView`/`myGameView`…）。
对「严格 1:1 移植」来说这是**更好的选择**（便于与 Android 源码对照），但确实偏离了文档。

#### ✅ 已回补：`mySubmit`（比赛答案提交）整条链（2026-09-24）

> 下面是审计当时的状态，**现已修复**，见第 7 节「阶段 C」。

- 原版 `mySubmit.java` 475 行：国家下拉（200+ 项）、ID/邮箱输入、
  `POST {uil}submit_result.php`、成功跳 `mySubmitList`、失败弹「错误」。
- PC 侧全库 grep 无对应类，也无任何 `submit_result.php` 调用。
- 入口在 `myStateBrow` 的上下文菜单「提交答案（sokoban.cn）」（原版 `myStateBrow.java:407 case 11`）
  —— PC 的 `myStateBrow` 连这个菜单项都没有。
- 后果：**「比赛答案提交列表」只能看，不能交**。这正是计划书阶段 3.1 明确列出的映射项。

**同时暴露的第二处缺口（审计当时也漏了）**：`myGameView` 里原版有的 `OpenState()`
在 PC 侧**整个方法都不存在**，所以「打开状态」从来没生效过；
`myStateBrow.loadSelected()` 还把 `load_State()` 的返回值丢掉了。
两处均已修复。

### 阶段 4：次要视图 — ✅ 已回补（阶段 D-1 / D-2 + G ①③⑤，2026-09-25）

> 下表 PC 列已按 2026-09-25 的**磁盘实际行数**刷新（审计当时那版是回补前的旧值）。

| 文件 | 原版 | PC | 保留率 |
|------|------|-----|-------|
| `myEditView.java` | 2,407 | 1,904 | 79%（阶段 D-2 重写） |
| `myFindView.java` | 692 | 532 | 77%（阶段 D-2 重写） |
| `myRecogView.java` | 831 | 991 | 119%（阶段 D-2 重写） |
| `myRecogViewMap.java` | 1,047 | 1,171 | 112%（阶段 D-2 重写；原版把算法拆在匿名类里，PC 展开成具名方法） |
| `myStateBrow.java` | 1,018 | 1,165 | 114%（阶段 D-1 重写；同上） |
| `myActGMView.java` | 824 | 749 | 91%（阶段 D-2 重写；阶段 G ⑤ 换 HoloButton） |
| `myExport.java` | 481 | 223 | 46%（阶段 G ⑤ 订正按钮文案 + HoloButton） |
| `myPicListView.java` | 258 | 291 | 113%（阶段 G ① 重写） |
| `mySolutionBrow.java` | 304 | 270 | 89%（阶段 G ③ 整体重做） |
| `myGridView.java` | 1,810 | 1,773 | 98%（阶段 D-2 + G ②④ 补 14 项上下文菜单） |
| `myGameView.java` | 5,906 | 4,867 | 82%（阶段 D-3 / F / G ④） |
| `BoxManPC.java`（原版 `BoxMan.java`） | 2,006 | 1,921 | 96%（阶段 G ⑦ 补 10 项上下文菜单） |

（缩水本身可以理解 —— Android 的 `findViewById`/`AlertDialog`/`Menu` 样板占很大比重。
下面的逐项核对显示，**缩掉的不只是样板** —— 这正是阶段 D-2 重写 `myRecogView` 时
把行数从 180 拉到 991、`myRecogViewMap` 从 196 拉到 1,171 的原因。）

#### 4.7 外部求解器（YASS）— ✅ 已收口（阶段 F，2026-09-25）

> ⚠️ **计划书这里写错了**：它要求「通过 `ProcessBuilder` 调用本地可执行 YASS 求解器」。
> 回原版核实后确认 —— 原版**根本不用外部进程**，用的是 Android 的**跨应用 Intent**：
> `ComponentName("net.sourceforge.sokobanyasc.joriswit.yass", "yass.YASSActivity")`、
> `action = "nl.joriswit.sokosolver.SOLVE"`、`extra = "LEVEL"`，
> 由 `startActivityForResult(intent3, 1)` 发起，答案经 `onActivityResult` 的
> `extra "SOLUTION"` 回流。所以「全库 0 处 `ProcessBuilder`」**不是缺口**。
>
> PC 没有等价的跨应用机制，语义上等价于「真机未安装求解器」，因此落地方式为：
> **前面的真逻辑（自动保存当前状态、查重）照原版完整保留，最后一步抛出，
> 落到与真机相同的 catch 分支**（Toast「没有找到求解器！」）。
> 求解成功后的回流路径 `onSolverResult(String, boolean)` 也按原版 `onActivityResult` 保留。
>
> 已接的两处入口：`player.xml` 的「YASS求解」（`myGameView`）、
> `myEditViewMap.onLongPress` 长按仓管员素材（`myEditView`）。
> 原版被 `<!-- -->` 注释掉的「Solver求解」/「Festival求解」仍然不存在。

### 阶段 5：对话框重建 — ✅ 且优于计划书

计划书说简单对话框「直接用 `JOptionPane`」、中等对话框用 `MigLayout`。
实际实现走了另一条路：`compat/HoloAlertDialog` + `compat/HoloContent` 复刻 Android
`alert_dialog_holo.xml` 外壳（9-patch 内边距、`#FF282828` 填充、标题 22sp `#ff33b5e5`、
按钮栏 48dp 等）。**这比 `JOptionPane` 保真得多**，是对「逐像素还原」目标的正确理解。

已落地的对话框类（12 个）：
`ColorDialog`、`ExportDialog`、`FindDialog`、`GotoDialog`、`NewLevelDialog`、
`QueryDialog`、`RuleDialog`、`SplitDialog`、`UrlInputDialog`、`myGifMakeDialog`，
外加 `compat/Holo{Alert,Message,Progress}Dialog`。
`Phase5DialogTest` 8 项、`Phase8QueryTest` 5 项全绿。

> ⚠️ 其中 `DelDialog`（PC 自造的「删除确认 + 是否连同解答与状态一起删」）与
> `ExportDialog` / `SplitDialog` 一样是 PC 自造物 —— 原版 `myGridView` 的「删除」只是一句
> `setMessage` 的确认框（`myGridView.java:1484-1492`）。它已随阶段 G ④ 一并删除；
> 原版真正带「删除答案...」的是 `BoxMan` 的**关卡集**上下文菜单（`BoxMan.java:1450`）。
> **✅ 阶段 G ⑦ 已回补**：`BoxManPC` 的 10 项关卡集上下文菜单（含 `case 4「删除答案...」`
> → 异步删 `G_State` 里 `G_Solution = 1` 的记录 + 进度框「答案删除中...」）已 1:1 移植。

未建独立类但功能在别处的：`size_dialog`（并入 `myEditView.showResizeDialog()`）、
`recog_dialog`、`import_dialog/2/3`（并入 `BoxManPC.importLevelFile()`，见下）。

#### 阶段 5.5 Toast → 状态栏 — ✅ 已实装（2026-09-24）

> 下面是审计当时的状态，**现已修复**，见第 7 节「阶段 B」。
> 实际做法比计划书更进一步：不是「主窗口底部 `JLabel`」，而是真浮层提示条
> （`JWindow` + 圆角半透明 `JLabel`），因为原版 `Toast` 本来就是浮层。

实际 `MyToast.java` 全文只有 13 行：

```java
public static void showToast(Object context, String message, int duration) {
    System.out.println("[Toast] " + message);
}
```

而全项目**有 91 处调用**：

```
myGameView.java      69 处
myGameViewMap.java   11 处
BoxManPC.java         6 处
myMaps.java           3 处
myGridView.java       2 处
```

这 91 条面向用户的操作反馈（「关卡已保存！」「答案有重复！」「出错了，注释未能保存！」…）
在 PC 上**全部静默丢弃**。这是用户可感知度最高的缺口之一。

### 阶段 6：菜单系统 — ✅ 已收口（81 / strict 缺 0 / loose 缺 0，2026-09-25）

13 个 `res/menu/*.xml`、**81 个菜单项**。

> **修正（2026-09-24）**：原审计写「84 项」偏高 —— 其中 3 项是被 `<!-- -->` **注释掉**的
> `<item>`，原版里并不存在：`main.xml` 的「导入关卡集(文件夹)...」、`act_gm.xml` 的「YASS求解」、
> `player.xml` 的「Solver求解」。`menu_fidelity.py` 已改为先剥注释再取标题。
>
> 同时修掉该脚本的一个 **Windows 路径 bug**：`os.walk()` 产出反斜杠路径、查表用正斜杠，
> 导致 **strict 判定恒为假**（老数字「strict 缺 84/84」是假的）。路径归一化后下面的数字才成立。

逐项到 PC 源码中匹配（标题字符串必须出现在**该菜单所属 Activity 对应的 PC 类**里）：

| 窗口 | menu | 项数 | strict OK | 缺口 |
|------|------|-----|-----------|------|
| `myStateBrow` | `state.xml` | 4 | **4** | ✅ 阶段 D-1 已补齐 |
| `mySubmitList` | `submit_list.xml` | 1 | **1** | ✅ |
| `myGridView` | `levels.xml` | 13 | **13** | ✅ 阶段 D-2 已补齐（原先 `╋`/`清空列表`/`批量删除...` 三项置灰） |
| `BoxManPC` | `main.xml` | 10 | **10** | ✅ |
| `myActGMView` | `act_gm.xml` | 8 | **8** | ✅ 阶段 D-2 已补齐 |
| `myFindView` | `find.xml` | 8 | **8** | ✅ 阶段 D-2 已补齐 |
| `myRecogView` | `recog.xml` | 6 | **6** | ✅ 阶段 D-2 已补齐 |
| `myExport` | `export.xml` | 1 | **1** | ✅ |
| `myGameView` | `player.xml` | 13 | **13** | ✅ 阶段 D-3 补齐 12 项，阶段 F 补上最后的 `YASS求解`（新增 `导出...`/`导入...`/`打开状态...`/`操作说明`，并把 `关于` 改回 `myAbout2`） |
| `myEditView` | `edit.xml` | 13 | **13** | ✅ 阶段 D-2 已补齐 |
| `myPicListView` | `piclist.xml` | 1 | **1** | ✅ 阶段 G ① 重写后补上 ActionBar「位置」 |
| `myFileExplorerActivity` | `filelist.xml` | 2 | **2** | ✅ 阶段 G ① 新移植（224 → 331 行） |
| `(孤儿资源)` | `gif.xml` | 1 | **1** | ✅ 阶段 G ① —— ⚠️ 该菜单**从未被 inflate**（`grep -rn "R.menu.gif"` 零命中），真正的「制作」是 `myExport.java:423` 的 PositiveButton |
| | | **81** | **81** | **0** |

> **进展**：strict OK 从 **41 → 64 → 72 → 76 → 77 → 81**，`loose 真缺` 从 40 一路降到 **0**。
> 阶段 D-1/D-2/D-3/F/G 涉及的窗口（`myStateBrow` / `myActGMView` / `myFindView` / `myRecogView` /
> `myEditView` / `myGridView` / `myGameView` / `myPicListView` / `myFileExplorerActivity`）已全部补齐。
>
> ⚠️ **2026-09-25 阶段 E 修正：`loose 真缺` 由 4 改为 5。**
> 删掉 PC 自造的 `SplitDialog.java` 之后，`filelist.xml 完成` 从「`~`（他处出现过该字符串）」
> 变成 `MISS` —— 老数字 4 是**假阴性**：那个「完成」来自 `SplitDialog` 里
> `btCancel.setText("完成")`，与被测菜单毫无关系。`menu_fidelity.py` 的 loose 判定是
> 「全项目任何源码里出现过该标题字符串」，**任何无关的 PC 自造 UI 都可能掩盖真实缺口**。
> 判断 loose 缺口时要以 strict 列为准，并人工确认每个 `~` 的来源。

> 结构性偏差：**已全部消除** —— 全项目不再有任何窗口使用 Swing 的 `JMenuBar`
> （`myEditView` 是最后一个，阶段 D-2 已删），也不再有任何 `myActionBar.NO_OP` 置灰占位项。
> 阶段 D-3 之后，**弹出菜单载体也统一了**：除 `myGameView.installMapPopupMenu()` 这一处
> PC 自造的多级右键菜单（阶段 G 待删）外，全项目不再有裸 `new JPopupMenu()` / `new JMenuItem()`。
> 该约定由 `Phase20MenuCarrierConventionTest` 用源码扫描锁住。
>
> ⚠️ 但要区分两种「没有 ActionBar」：`myGameView` 与 `myEditView` 原版就是
> `FEATURE_NO_TITLE` + `FLAG_FULLSCREEN`（**本来就没有 ActionBar**），它们的菜单在
> 底栏按钮弹出的选项菜单里，**不要**给它们加 `myActionBar`。
> 判据见第 3 节「PORTING.md 自身的错误」与 `android-ui-to-swing-fidelity` 技能。

**loose 真缺 —— 审计当时是 4 项，现已全部收口（0 项）**：

```
filelist.xml 上一级     ✅ 阶段 G ① 新移植 myFileExplorerActivity
filelist.xml 完成       ✅ 同上
gif.xml      制作       ✅ 阶段 G ① myGifMakeDialog 按钮文案改回「制作」
piclist.xml  位置       ✅ 阶段 G ① myPicListView 重写后补上 ActionBar「位置」
```

> ~~这 4 项**全部落在未移植的类/功能上**：`myFileExplorerActivity`（2 项）、
> `myGifMakeFragment`（1 项）、`myPicListViewAdapter`（1 项）。~~
> **✅ 阶段 G ① 已全部收口（2026-09-25），现在 strict / loose 均缺 0。**
> 也就是说 —— **已移植的窗口里已无菜单缺口**。
> （`player.xml YASS求解` 已在阶段 F 收口，2026-09-25。）


### 阶段 7：测试与打包 — ✅

**测试：做得扎实且全绿**（干净环境 + 资源目录在 classpath）。
审计当时的 12 个类 / 57 项是下面这张表：

```
Phase1CompatTest            OK (6 tests)
Phase2GameViewTest          OK (1 test)
Phase2GridTest              OK (4 tests)
Phase3GameViewSnapshotTest  OK (2 tests)   ← 含 HiDPI 设备缩放不变量
Phase3NavigationTest        OK (4 tests)
Phase4SecondaryViewTest     OK (5 tests)
Phase4BatchBTest            OK (6 tests)
Phase5DialogTest            OK (8 tests)
Phase7SystemIntegrationTest OK (4 tests)
Phase8QueryTest             OK (5 tests)
Phase9SubmitTest            OK (8 tests)
WindowSizingTest            OK (4 tests)
─────────────────────────────────────────
合计                        57 tests, 0 failures
```

**现状（2026-09-25）：39 个用例类 / 412 个用例 / 0 失败**，
`./gradlew --offline clean test` → `BUILD SUCCESSFUL`。
审计后新增的 27 个类逐项列在第 7 节的「当前测试基线」里。

**打包：已做（阶段 F，2026-09-25）。** `desktop/build.gradle` 新增 `packageApp` 任务
（`Exec` + `jpackage`，默认 `app-image`，可用 `-PjpackageType=msi|dmg|deb` 换安装包格式）。
实测产出的 `build/jpackage/BoxManPC/BoxManPC.exe` 能正常启动
（FlatLaf 原生库、sqlite-jdbc、Swing 界面全部就绪）。三个踩过的坑见第 7 节阶段 F。

**兼容性测试**（7.2 的 macOS / Linux / JDK 8/17/21）：未做（仅在 Windows + JDK 26 上验证）。
（当前开发机只有 JDK 26。）
> ✅ **构建链问题已修（阶段 A，2026-09-24）**：仓库根/`android/` 那份 Gradle wrapper 是 **6.5**，
> 在 JDK 26 下会以 `Unsupported class file major version 70` 失败；已在 `desktop/` 生成**独立 wrapper（9.7.1）**，
> `cd desktop && ./gradlew test` 可正常跑。**跨平台/跨 JDK 的兼容性验证本身仍未做**，见第 0 节「仍然存在的偏差」。

---

## 2. 逻辑被简化 / 硬编码（比行数缩水更严重）— ✅ 5 项全部已修（2026-09-25）

这几处不是「样板被删」，而是**业务行为被改**。
下面 5 条的**原始判定保留**（便于理解缺口性质），每条标题后已标出修复阶段：

### 2.1 `myEditView.doSubmit()` 凭空造作者名 — ✅ 2026-09-25 已修（见阶段 D-2）

```java
// PC（改前）: myEditView.java:559-568
mapNode nd = new mapNode(myMaps.curMap.Map, "编辑关卡", "PC作者", "");
long lvlId = mySQLite.m_SQL.add_L(myMaps.mSets3.get(0).id, nd);
```

原版 `myEditView.java` 的 `edit_complete`：
1. 弹「提交到」**单选列表**，列出全部关卡集 + 末尾一个「新建关卡集」；
2. 用户选定后先跑 `Normalize2(m_cArray)` 做关卡标准化；
3. 用 `myMaps.curMap` 的 **Title/Author/Comment** 落库。

PC 版：**不弹选择框**（写死 `mSets3.get(0)`）、**不标准化**、
**硬编码标题「编辑关卡」、作者「PC作者」**。这是「凭空造数据」，属于对原则 2 的实质违反。
→ **已按原版重写**：`onSubmit()` 弹「提交到」单选（含末尾自动追加的新关卡集、
选中后 `add_T(3,…)`），再跑 `Normalize2()`，最后用 `curMap` 真实的
Title/Author/Comment 落库。

### 2.2 `showResizeDialog()` 丢掉「扩充/消减」模式 — ✅ 2026-09-25 已修（见阶段 D-2）

原版 `size_dialog.xml` 有 `rbExtend`(扩充) / `rbCut`(消减) 单选，
代码里用 `mySign[0] = ±1` 决定 4 个 spinner 是加还是减，
并有 **8 条越界校验**（宽度/高度不能小于 3、不能超 `m_nMaxCol/m_nMaxRow`、
四侧空间是否足够），错误逐条列在「错误」对话框里。

PC 版（`myEditView.java:508-530`）：4 个 `JSpinner` + `JOptionPane`，
**只能扩充，不能消减**，**无任何校验**。
→ **已按原版重写**：`onResize()` 用 `HoloViewDialog` + 4 个 0~30 Spinner +
「扩充/消减」单选，校验抽成 `resizeError()`（8 条逐条照抄，含括号里的数字），
错误走 `HoloMessageDialog`「错误」。

### 2.3 `doNormalize()` 名不副实 — ✅ 2026-09-25 已修（见阶段 D-2）

原版 `edit_normalize`（关卡标准化）会重算最小包围盒并重写 `curMap.Map/Rows/Cols`。
PC 版（`myEditView.java:532-535`）只有：

```java
private void doNormalize() {
    mMap.mySelectAll();   // 只是全选
    mMap.repaint();
}
```

→ **已按原版重写**：`Normalize()`（四周补墙 → 可达性扩散 → 清非可达区 / 补墙 →
四至再外扩一圈 → 箱/目标数不对只 `MyToast`）与 `Normalize2()`（提交用，不补墙、
四至收缩、不对则弹「警告」返回 false）两个算法都已移植。

### 2.4 导入流程被窄化 — ✅ 2026-09-25 已修（阶段 E）

已做：
- 「导入(XSB 或 Lurd)」菜单项按原版重写（含 `LurdToXSB()` 逆推法），见阶段 D-2。
- 解析核心抽出 `BoxManPC.importLevelReader()`，并补上 `importLevelText()`
  —— **剪切板导入的入口回来了**。
- `myGridView` 的「╋ → 添加关卡(文档)... / 添加关卡(剪切板)...」两项菜单实装，
  选项面板（`关卡`/`答案` 复选 + `自动/GBK/UTF-8` 编码单选 + `仅一个关卡时自动打开`）
  按 `import_dialog.xml` 还原。
- **阶段 E**：`BoxManPC` 主菜单的「导入...」/「导出...」已换回原版入口链 ——
  `sel_Set()`（`import_dialog3.xml`）+ `mySplitLevelsFragment`，
  `sel_Set2()`（`export_dialog3.xml`）+ `myExportFragment`。裸 `JFileChooser` 已删除。

> 原判定（2026-09-25 之前）：`BoxManPC` 主菜单的「导入...」是一个裸 `JFileChooser`
> （`chooseImportFile()`），没走原版入口；批量导入（原版交给 `mySplitLevelsFragment`）也没做。
> 详见「阶段 E」小节。

### 2.5 图像识别与相似度搜索只剩外壳 — ✅ 2026-09-25 已修（见阶段 D-2）

> 原判定（2026-09-24 写 README 时回代码核实出来的）：
> 这两条在行数缩水表里只是「22% / 17%」，看起来像「实现得简略」；
回代码核实后确认是**功能本身没搬过来**，不是缩水：

| 功能 | 原版 | PC | 结论 |
|------|------|-----|------|
| 自动图像识别 | `myRecogViewMap`（1,047 行）做**逐像素样本匹配**：`getPixelDeviateWeightsArray()`、`Bitmap.getPixels()`、RGB_565 往返、样本/格子双图比对 | `myRecogViewMap` 196 行，无任何像素分析；`myRecogView` 只是个**手动 10×10 网格画板**（点「地板/墙壁/箱子/目标/标箱/人」逐格涂），再「发送到关卡编辑器」 | **未移植** |
| 关卡库级相似度搜索 | `myFindView`（692 行）对全库关卡打分排序，有 `mTrun`（最高相似度的 n 转）、`mSimilarity`（精准相似度阈值）、`mSelect`（相似区域方框） | `myFindView` 118 行；`mSimilarity` **声明了但从头到尾没被赋值**（死字段），只有源关卡/相似关卡的并排显示 | **未移植** |

> **→ 两条都已在阶段 D-2 补齐**：`myRecogView` + `myRecogViewMap`
> （831 + 1047 行原版，改前 183 + 196 行）与 `myFindView` + `myFindViewMap`
> （692 + 693 行，改前 120 + 225 行）都已按原版重写，见第 7 节。

> 教训：**行数缩水率不能直接当作「缺什么」的依据**。缩水可能来自 Android 样板
> （`findViewById`/`AlertDialog`/`Menu`）的消失，也可能来自功能整个没搬。
> 两者必须回代码分别核实 —— 前者无害，后者是缺口。写 README / 汇报前尤其要核。

---

## 3. PORTING.md 自身的错误（不是执行问题，但审计要说）

计划书有几处**对原版的描述不准确**，PC 的实现在这些点上反而比计划书更正确：

| 计划书原文 | 实际情况 | PC 的选择 |
|-----------|---------|----------|
| 「原 `Help` Activity 使用 `WebView` 显示 HTML」 | 原版 `Help.java:10,17,28` 用 `TextView` + `R.layout.help`，**没有 WebView** | 用 `JTextArea` → ✅ 更接近原版 |
| 阶段 5.1「简单对话框直接用 `JOptionPane`」 | —— | 改用 Holo 外壳 → ✅ 保真度更高 |
| 阶段 3.1 类名 `GridViewFrame`/`PicListFrame`/… | —— | 保留 `myGridView`/`myPicListView`/… → ✅ 便于 1:1 对照 |
| 「全工程约 39,600 行 Java 源码」 | 实测 `android/app/src/main/java` 约 3.4 万行 | 估算偏差 |

另外计划书**没有**为「逐像素还原」立规格（`1dp = 1px`、窗口统一 370×780 竖屏、
ActionBar `#0083C5`、列表背景 `#004040`…），
但实际工作大量投入在这一块，并产出了 `UiSnapshotTool` / `WindowSnapshotTool` /
`DialogSnapshotTool` 等比对工具。**这是计划书没写、但做对了的事。**

---

## 4. 反方向偏差（PC 自造 / 与原版不一致的交互）

「不增删功能」原则要求的是等价映射，以下几处是**交互方式被换掉**或**新增**：

| 位置 | 原版 | PC |
|------|------|-----|
| `myStateBrow` 排序 | **点分组标题循环切换**（`s_sort[my_Sort]` 拼在标题上） | ✅ 阶段 D-1 已改回右键分组标题循环切换，自造的「排序方式」菜单已删 |
| `myRecogView` 识别按钮标签 | `recog_view.xml` 是 `- # $ * . @` 符号 | ✅ 阶段 D-2 已改回符号（`new FlatButton("-", 29, 29, true)` 等 6 个） |
| `myRecogView` 菜单 | `？/悔/度/减/增/识别` | ✅ 阶段 D-2 已改回原版 6 项（全部 `showAsAction="always"`，无溢出菜单） |
| `myEditView` 菜单 | 无「保存」项（只有底栏 `bt_Save`） | ✅ 阶段 D-2 已删掉自造的菜单项，只剩底栏 `bt_Save` |
| `mySolutionBrow` | 上下文菜单 + 覆写对话框 | ✅ 阶段 G ③：改回 Activity + ActionBar + 「答案」组头；删自造按钮栏与 `JOptionPane` |

**✅ 2026-09-25：本节的 5 条已全部消除。** 由 `Phase20MenuCarrierConventionTest` /
`Phase16RecogViewTest` / `Phase27SolutionBrowTest` 等用例锁住，防止回退。
（阶段 G ⑤ 顺带删掉了 `myExport` 的「执行导出」这个自造文案，改回原版的「导出」。）

---

## 5. 建议的修复优先级

> 状态标记：✅ 已完成 ｜ ⬜ 未开始。完成项见第 7 节。

**P0 — 有实际危害**

1. ✅ **修 `copyDataBase()`**：`getResourceAsStream` 返回 null 时**抛异常并删除半成品文件**，
   不要留 0 字节库；`checkDataBase()` 增加「文件长度 > 0 且 `sqlite_master` 里有表」的判定，
   让被污染的库能自愈。
   （否则全新安装的用户会得到一个完全不能用的空库。）
2. ✅ **`myMaps.sPath` 给默认值**（如 `"/"`），消除 `...nullDataBase/` 这种路径。
3. ✅ **修构建链**：Gradle wrapper 6.5 与 JDK 26 不兼容，`./gradlew` 无法运行。
   已在 `desktop/` 生成独立 wrapper（9.7.1），`./gradlew test` 可跑。
4. ✅ **（审计漏项）修 `setJMenuBar` 时序**：6 个窗口内容区实际只有 370×757，少 23px。
   已统一把 `applyPhoneSize` 挪到构造器最后，并把回归锁改成 `validate()` 之后断言。

**P1 — 功能性缺口（计划书明确要求）**

5. ✅ 移植 `mySubmit`（475 行）→ 补齐「提交答案」链路，`myStateBrow` 加回菜单项。
   顺带补上漏掉的 `myGameView.OpenState()` 与 `myStateBrow.loadSelected()` 赋值。
6. ✅ 实现 `MyToast` 的浮层版本（**91 处调用**在等它）。
7. ✅ 接入 YASS 求解（阶段 F）。**方式与计划书不同**：原版走 Android **跨应用 Intent**
   （`nl.joriswit.sokosolver.SOLVE`），不是 `ProcessBuilder`；PC 等价于「真机未装求解器」，
   真逻辑保留、最后一步抛出落到同一个 catch 分支。详见阶段 F 与 4.7。

**P2 — 保真度回补**

8. ✅ **按第 6 节表格逐窗口补齐菜单项 —— 已 81 / strict 缺 0 / loose 缺 0。**
   - ✅ `myStateBrow`（阶段 D-1）：`state.xml` 4 项 + 上下文 12 项 + ExpandableListView 结构全部回补。
   - ✅ `myGridView`（阶段 D-2 + G ②④）：`levels.xml` 13 项 + 关卡项上下文菜单 **14 项** + 可见性矩阵。
   - ✅ `myRecogView`（阶段 D-2）：原版 6 项全部补齐。
   - ✅ 其余窗口自造的 `JMenuBar` 已全部换成 `myActionBar` + `res/menu/*.xml`（全项目 0 处 `JMenuBar`）。
   - ✅ 基础设施：抽出 `compat/HoloPopupMenu`（ActionBar 溢出菜单与上下文菜单共用同一套 Holo 渲染）。
   - ✅ 阶段 G ① 补上最后 4 项：`filelist.xml`（新移植 `myFileExplorerActivity`）、`piclist.xml`、`gif.xml`。
9. ✅ 修 `myEditView` 的三处简化（提交选择框 / 扩充-消减 / 标准化）—— 阶段 D-2，见 2.1~2.3。
10. ✅ 恢复导入的三种方式与编码选择 —— 阶段 E，见 2.4。
11. ✅ **补自动图像识别**（原版 `myRecogViewMap` 的逐像素样本匹配）—— 阶段 D-2；PC 现 1,171 行，见 2.5。
12. ✅ **补关卡库级相似度搜索**（原版 `myFindView` 的打分排序）—— 阶段 D-2 + G ②④
    （新增 `myFindFragment` 相似度引擎 + `FindDialog` 重写），见 2.5。

> **✅ 2026-09-25：P0~P2 全部完成，本节已无未开始项。**
> 阶段 G ⑤ 新开的一条（窗口主题 / 对话框载体）记在第 0 节的「仍然存在的偏差」里。

---

## 6. 附：审计可复现命令

> 目录已于 2026-09-24 重排：`BoxManPC/` → `desktop/`，原版 Android 工程（根级 Gradle 文件 + `app/`）
> → `android/`。下面的命令按**新目录**写。

```bash
# 1) 类清单对比
find android/app/src/main/java -name "*.java" | sort > /tmp/a.txt
find desktop/src/main/java     -name "*.java" | sort > /tmp/b.txt

# 2) 编译 + 跑全量测试（wrapper 已修好，不再需要手工拼 classpath）
cd desktop && ./gradlew --offline test
#    产物：build/test-results/test/TEST-*.xml、build/reports/tests/test/index.html

# 3) 菜单保真度（脚本见 .workbuddy-ai/scratch/menu_fidelity.py，从仓库根跑）
python .workbuddy-ai/scratch/menu_fidelity.py

# 4) 界面快照（离屏渲染到 desktop/build/ui-snapshot/）
cd desktop && ./gradlew --offline test \
  --tests 'my.boxman.WindowSnapshotTool' \
  --tests 'my.boxman.DialogSnapshotTool' \
  --tests 'my.boxman.UiSnapshotTool' \
  --tests 'my.boxman.GameViewSnapshotTool'
```

> 注意：审计当时跑测试前必须清掉 `build/test_boxman*` 与 `build/ui-snapshot/home`，
> 否则会被第 1 节所述的 0 字节库污染，出现「明明代码是对的却报 no such table」的假失败。
> **该问题已在阶段 A 修掉**（`checkDataBase()` 会识别并重建被污染的库），
> 现在无需再手工清理。
>
> 另外，上面的 `@<(find ...)` 进程替换在 Git Bash 下会报 `file not found: \proc\...\fd\63`，
> 改成先把文件清单写进 `/tmp/m.txt` 再 `javac @/tmp/m.txt` 更稳。

---

## 7. 修复进展（按阶段推进）

> 审计之后开始分阶段回补，每阶段以「代码 + 回归测试 + 快照」三件套收尾。
> 下表的「验证」列都是可复现的实际结果，不是计划。

### 阶段 A —— P0 健壮性（已完成）

| 修复 | 文件 | 验证 |
|------|------|------|
| `copyDataBase()` 不再静默造 0 字节库：资源流为 null 直接抛异常、写 `.tmp` 后校验大小再原子改名、`finally` 清理半成品 | `mySQLite.java` | `Phase10DatabaseRecoveryTest` 3/3 |
| `checkDataBase()` 拒绝 0 字节文件、要求 `sqlite_master` 里确有表（被污染的库可自愈） | `mySQLite.java` | 同上 |
| `myMaps.sPath` 默认 `"/"`，消除 `...nullDataBase/` 路径 | `myMaps.java` | 同上 |
| 启动失败时弹「关卡库出错，无法继续游戏！」并退出（对齐原版） | `BoxManPC.java` | 手工 |
| 独立 Gradle wrapper（9.7.1），修 JDK 26 下 `Unsupported class file major version 70` | `desktop/gradlew` | `./gradlew test` 可跑 |

### 阶段 B —— `MyToast` 实装（已完成）

原 PC 版是 13 行空壳（只 `System.out.println`），而全项目有 **91 处**调用 ——
所有用户反馈都被丢掉了。已改为真浮层提示条：`JWindow` + 圆角半透明 `JLabel`、
1500ms/3000ms 自动消失、同一时刻复用一条、`null` context 兜底、
在 `UiWindow.applyPhoneSize` 里统一挂 `attachTo(Window)` 跟随窗口移动。

验证：`Phase11ToastTest` 4/4；`ToastSnapshotTool` 离屏合成（**不用 `Robot` 截屏**，
避免把用户桌面截进去）。

### 阶段 C —— `mySubmit` 提交答案链路（已完成）

| 项 | 内容 |
|----|------|
| 新增 | `mySubmit.java`（571 行）：242 项国家表（与原版 `m_menu` **逐项一致**，脚本核对通过）、`submit.xml` 逐块还原（30dp 带 / 右对齐 220dp 字段 / 4dp `#303030` 分隔条 / 返回+提交）、`HttpURLConnection` POST `{uil}submit_result.php`、**强制 GBK** 编码 |
| 新增 | 关键字判定抽成 `static String interpret(String)`，便于离线单测；**保留原版的判定顺序怪癖**（`correct (for ` 先于 `not correct`，所以 `not correct (for ...` 会被判成成功） |
| 修 | `myStateBrow` 补上上下文菜单项「提交答案（sokoban.cn）」（原版 `case 11`），成功列表/失败「错误」对话框按原版分支 |
| 修 | ⚠️ **审计漏掉的一条**：PC 的 `myGameView` **完全没有 `OpenState()`** —— 「打开状态」功能从来没生效过。已按原版补齐，并用 `windowActivated` 等价替代 Activity 的 `onStart()` 触发 |
| 修 | `myStateBrow.loadSelected()` 把 `load_State()` 的返回值丢掉了，只置了标志位 → `myMaps.m_State` 一直是旧的。已改为真正赋值 |

验证：`Phase12SubmitTest` 14/14（国家表 242 项、`CN` 重复项命中第 1 个、关键字分支与顺序、
表单几何/配色、ActionBar 只有返回折角）；`WindowSnapshotTool` 新增 `16-mySubmit.png`。

### 阶段 C 附带发现并修复 —— `setJMenuBar` 在 `applyPhoneSize` 之后调用，内容区少了 23px

**这是审计时没看出来、靠跑回归才暴露的一个系统性缺陷。**

`UiWindow.applyPhoneSize()` 的做法是「把内容区首选尺寸设成 370×780 → `pack()`」。
但 `JMenuBar` 挂在 `JRootPane` 上、位于内容区**之外**，高度 **23px**。
所以只要构造器写成「先 `applyPhoneSize`，再 `setJMenuBar`」，
菜单栏就会从已经定好的内容区里挖走 23px —— 内容区静默变成 **370×757**。

更阴的是它的**显现时机**：`pack()` 之后立刻读还是 370×780，
要等窗口 `addNotify()/validate()` 之后才变成 757。
所以 `WindowSizingTest` 原来的写法（构造完直接读尺寸）**测不出来**，
只在个别运行里偶发失败 —— 一度被误判为「偶发」。

实测（`validate()` 前后对照）：

```
myRecogView   pre: 370×780   post: 370×757   menubar=370×23
myEditView    pre: 370×780   post: 370×757   menubar=370×23
myFindView    pre: 370×780   post: 370×757   menubar=370×23
myStateBrow   pre: 370×780   post: 370×757   menubar=370×23
```

受影响的 6 个窗口：`myRecogView` / `myEditView` / `myFindView` / `myStateBrow` /
`myActGMView` / `myExport`。
（`myStateBrow` 已于阶段 D-1 换成 `myActionBar`，不再是 370×757，剩 5 个。）

已做的两件事：

1. **把所有 17 个窗口的 `applyPhoneSize` 统一挪到构造器最后**（UI 全部装好之后），
   并在 `UiWindow` 的类注释里把这条写成硬性约定。
   `myGameView` 是唯一例外：它必须放在 `initUI()` 之后、`initGame()` **之前**
   （`initGame()` 依赖地图控件已有实际尺寸）。
2. **把回归锁改成 `addNotify()` + `validate()` 之后再断言**，
   这样「构造完看着是 780、实际是 757」再也藏不住。
   `WindowSizingTest` 同时补上了新增的 `mySubmit`。

修完实测：

```
myRecogView   pre: 370×780   post: 370×780   menubar=370×23   outer=384×840
```

> ⚠️ **遗留（转阶段 D）**：这 6 个窗口用的是 **PC 自造的 `JMenuBar`**，
> 而原版是 ActionBar + `res/menu/*.xml`（`myRecogView` 用 `recog.xml`，
> 且原版 `myRecogView.java:60` 明确有 `getActionBar()` + `setTitle("")`）。
> 也就是说现在窗口比手机屏**多出 23px 的菜单栏**，且菜单项是自造文案
> （如「发送到关卡编辑器 (myEditView)」）。阶段 D 要把它们换成
> `myActionBar` + 逐项还原 `res/menu/*.xml`，届时这 23px 会自然消失。

### 测试基线（阶段 C 之后）

```
23 个用例类 / 100 个测试用例  全部通过   （审计时：12 个类 / 57 个用例）
./gradlew --offline test   BUILD SUCCESSFUL
```

新增测试类：`Phase10DatabaseRecoveryTest`(3)、`Phase11ToastTest`(4)、`Phase12SubmitTest`(14)、
`Phase13StateBrowTest`(15)。
新增快照工具：`GameViewSnapshotTool`（挑中等复杂度关卡渲染游戏界面，供 README 配图）；
`WindowSnapshotTool` 增加 `16-mySubmit`；`WindowSizingTest` 断言方式加固并补上 `mySubmit`。

### 阶段 D-1 —— `myStateBrow` 全量回补（已完成）

`myStateBrow` 从 **228 行**（原版 1018 行，缩水 4.5×）重写到 **约 780 行**。

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| 列表结构 | 自造 `JTabbedPane` + 两个 `JList` | `JTree` 等价 `ExpandableListView`：2 个分组 + **两行子项**（描述 / 时间） |
| ActionBar | 自造 `JMenuBar`「排序方式」 | `myActionBar` + `state.xml` 4 项（含 checkable 的「导出答案的注释信息」） |
| 上下文菜单 | 4 项 | **12 项**，顺序/文案/按 `g_Pos` 的可见性规则与 `onCreateContextMenu` 完全一致 |
| 排序 | 菜单里点 | 右键分组标题**循环**切换（原版长按），分组标题显示 `答案  【移动优先】` |
| 打开 | 双击 | **单击**（原版 `onChildClick` 单击即 `load_State` + `finish`） |
| 导出 | 无 | `myExport` / `myExport2` / `myExport3` / `myExport4` + `saveAnsToFile` / `writeStateFile` |
| 注释 / 删除 / 删除全部 / 提交 / GIF | 部分 | 全部按原版分支实现（含 `isCanDeleteAns` 保护、YASS/导入答案只读） |

新增基础设施（原版 ActionBar 溢出菜单与上下文菜单共用同一套 Holo 弹出菜单样式）：

- `compat/HoloPopupMenu` —— 从 `myActionBar` 里抽出的 Holo 深色弹出菜单；`myActionBar` 改为复用它，
  行为/外观不变（`getActionTitles()` / `isActionChecked()` 等公开 API 保持原样）。
- `compat/HoloConfirmDialog` —— 原版 `setNegativeButton("取消") + setPositiveButton("确定")` 的双按钮框。
- `compat/HoloInputDialog` —— 原版「一个 EditText + 取消/确定」的通用输入框。

新增测试：`Phase13StateBrowTest`（15 个用例）锁住分组结构、4 项菜单、12 项上下文菜单的
逐项可见性、排序循环、Lurd/XSB 导出文本、`writeStateFile` 的文件头与 Comment 段、单击即打开。
新增快照工具：`StateBrowSnapshotTool`（灌合成数据渲染 `build/ui-snapshot/state-brow.png`，
因为 `WindowSnapshotTool` 里的 `08-myStateBrow` 是空数据，看不出两行子项与排序后缀）。

顺带修掉 `menu_fidelity.py` 的两个缺陷（见阶段 6 的修正框）：注释项误计、Windows 路径反斜杠
导致 strict 判定恒为假。

### 阶段 D-2 —— `myActGMView` / `myFindView` 全量回补（已完成）

两个窗口的差距都不止在菜单上，所以一次把菜单 + 对话框 + 逻辑补齐。

#### `myActGMView`（「动作管理 / 导入」）：原版 824 行 / 改前 247 行

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| ActionBar | 自造 `JMenuBar`「动作变换」（5 项，标题缺「（Lurd）」后缀） | `myActionBar`：标题「导入」+ 返回键 + `act_gm.xml` 全 8 项 |
| 「录制动作」 | 无 | ActionBar 文字按钮（`showAsAction="always"`），`setVisible(!is_BK)` |
| 「加载」对话框 | 自造 3 项 | 原版 **14 项**（宏 / 已做动作 / 后续动作 / 文档 / 剪切板 / 寄存器1~9） |
| 「保存到」对话框 | 自造 3 项 | 原版 **11 项**（宏 / 剪切板 / 寄存器1~9） |
| 「宏」名称框 | 无 | 输入框 + 已有宏列表 + 覆写确认（`saveMacroFile()`） |
| 暂存 / 执行前确认框 | 无 | 原版「内容有修改，是否暂存一下？」三按钮（取消/否/是） |
| 寄存器 | 无 | `reg0`~`reg9`，走 `BoxMan.ini` 的 `[Action]` 段（与 `myGameView` 同源） |
| 文档读入 | 无 | `readFile()` 读「导入/」下的文档并过 `loadLURD` |
| 录制模式 | 无 | `isRecording` + `act2` 寄存器（用后清空） |
| 执行前的注释剥离 | 无 | `<...>;` / 行尾 `;` / 整行 `;` 三种，逐行照抄 |
| 「“宏”功能说明」「“导入”说明」 | 无 | `Help(5)` / `Help(4)` |

#### `myFindView` + `myFindViewMap`（「相似关卡对比」）：原版 692 + 693 行 / 改前 120 + 225 行

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| ActionBar | 自造 `JMenuBar`「关卡操作」（3 项，标题全不对） | `myActionBar`：标题 `myMaps.sFile` + 返回键 + `find.xml` 全 8 项 |
| 「上一关」「下一关」 | 无 | ActionBar 文字按钮（`showAsAction="always"`），同关卡集内前后翻 |
| **8 转相似度比对** | 无 | `getLevel_inf()` 的 8 次旋转 + `myCompare()` 精确相似度 + 相似区域坐标 |
| 位置信息 | 无 | `m_Set_Pos1` / `m_Set_Pos2`（关卡集名 + 序号；创编关卡 / 自由关卡分支） |
| 「解关答案...」 | 无 | `load_SolitionList` + 按时间倒排 + `mySolutionBrow` |
| 「关于...」「操作说明」 | 无 | `myAbout2` / `Help(3)` |
| 「关卡全貌」 | 无 | checkable，勾选状态跟着地图视图走 |
| 旋转渲染 | 完全没做 | `onDraw` 按 `myMaps.m_nTrun` 映射格子，`initArena` 对调画布宽高 |
| 相似区域红框 | 自造（不跟旋转、坐标口径错） | 由四个角格子拼 `rt4`，瘦关卡时画 3px 红框 |
| 背景 | 写死 `0xFF222222` | `myMaps.m_Sets[4]` 底色 + 平铺 `myMaps.bkPict` |
| 右上角两个按钮 | 无 | 120dp「旋转」/「切换源关卡与相似关卡」+ 命中测试 |
| 三行关卡信息 | 无（自造了底部状态栏） | 源关卡几转比对 / 相似度% / 点击位置游标 |
| 墙图拼接 | 单一 `rtKW` | `getWall()` 按 `m_nTrun` 查方向表选图 |
| 双击 | 切换源/相似关卡（**错**） | 切换「关卡全貌」（原版 `onDoubleTap`） |
| 默认显示 | 源关卡（**错**） | 相似关卡（原版 `m_Level = false`） |

**新增基础设施**

- `compat/HoloChoiceDialog` —— 原版 `setSingleChoiceItems(...)` 的等价物，两副面孔：
  `clickToPick`（单击即生效 + 只有「取消」）与 `selectThenOk`（先选中再「确定」）。
- `compat/HoloConfirmDialog` 增加**三按钮**变体（取消/否/是）与 `askSave(...)` 静态方法。

**新增测试**

- `Phase14ActGMTest`（28 个用例）：菜单 8 项与顺序、5 张字符映射表、
  注释剥离的三种形态、寄存器读写与非法字符校验、录制模式清空 `act2`、
  「导入/」文档读入、「宏/」文档写出（自动补 `.txt`）、`onCreate` 的复选框/静态量同步。
- `Phase15FindViewTest`（20 个用例）：菜单 8 项与顺序、`关卡全貌` 可勾选、
  8 转相似度选中 1 转、相似区域坐标、`myCompare` 的阈值闸门与同形 100%、
  源/相似 × 全貌/标准化的四套数组切换、`initArena` 的宽高对调、
  按钮命中区域与点击分发、双击切「全貌」、游标命名、ESC 关闭、翻页键受开关项 15 控制。

**顺带修掉的既有偏差**：`Phase4BatchBTest` / `Phase7SystemIntegrationTest` 里断言的是
PC 自造的 `switchLevel()/rotateLevel()/toggleViewMode()/setLevels()` 与「默认显示源关卡」，
已改为按原版语义断言。

**当时的偏差（✅ 均已在阶段 G ②④ 收口）**

- 原版的入口是 `myGridView` 的「查找相似关卡」→ `myFindFragment`（相似度搜索对话框）→
  由它把源关卡写进 `myMaps.oldMap`，再启动 `myFindView`。
  **✅ 阶段 G ②④ 已移植 `myFindFragment`**（相似度引擎）并重写 `FindDialog`，
  入口链回到原版：`myGridView` 上下文菜单「查找相似关卡」→ `FindDialog` → 写 `oldMap` → `myFindView`。
- ~~`myGameView` 的「工具」菜单（关卡编辑器 / 相似关卡对比 / 关卡图像识别）在
  `res/menu/player.xml` 里不存在，是 PC 自造项。~~
  **✅ 阶段 G ④ 已删除**：`myGameView.installMapPopupMenu()` 整份 PC 自造右键菜单（导航/操作/视图/工具/帮助
  五组）连同其「工具」组一并移除；原先靠它兜底的入口都已有原版路径
  （关卡编辑器 → `myGridView` 上下文菜单；相似关卡对比 → `myGridView`「查找相似关卡」→ `FindDialog`；
  图像识别 → `BoxMan` 菜单「图像识别」→ `myPicListView` → `myRecogView`）。
- ~~`myActGMView` / `myFindView` 里的普通按钮仍是 Swing 默认外观，
  与原版 Holo 深色按钮（深灰底 + 浅边框）不一致。这是全项目性的问题，
  待一个「统一按钮样式」的阶段一并处理。~~
  **✅ 阶段 G ⑤ 已处理**：`myActGMView` 的 5 个按钮换成 `compat/HoloButton`。
  ⚠️ 原审计写的 `myFindView` 有误 —— 原版 `find_view.xml` 与 PC `myFindView` 里
  **都没有任何按钮**（已核实）。

#### `myRecogView` + `myRecogViewMap`（「关卡图像识别」）：原版 831 + 1047 行 / 改前 183 + 196 行

这是 D-2 里**缩水最严重**的一对文件：改前只画了个空壳（能打开、能放几个按钮），
识别算法、指示灯、边线拖拽、双击分支、相似度对话框**一行都没有**。

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| ActionBar | 自造 `JMenuBar`（标题/项都不对） | `myActionBar`：标题 `""` + 返回键 + `recog.xml` 全 6 项 |
| 6 项菜单 | 无 | `？`/`悔`/`度`/`减`/`增`/`识别` —— **全部**是 `showAsAction="always"`，所以都是 ActionBar 文字按钮，**没有溢出菜单**（`isOverflowVisible()` 为 false） |
| 「识别」按钮的自改名 | 无 | 原版 `myMenu.getItem(5).setTitle()`：识别中↔编辑中互改自己的标题 → `myActionBar.setBarActionTitle()` |
| 底部元素栏 | 自造等分 `GridLayout` | 按 `recog_view.xml` **绝对定位**逐个累加 x：地板@2、墙壁@33、箱子@66、标箱@99、目标@132、人@165、←@206、→@245、↑@284、↓@323（右缘 358 ≤ 370）；元素钮 29×29、方向钮 35×29、栏高 33、底色 `#FF778899` |
| 元素选中态 | 无 | 由**按钮自身背景色**表达（识别中 `0x9f0000ff` / 编辑中 `0x9fff3300` / 未选 `0xff334455`） |
| 边线调整 | 无 | 「减」「增」+ `UpData(1..4)` 每步移动 3px 的连续调整；方向钮长按可**取消**连续调整（原版 `return false` 的语义） |
| `悔`（撤销上次识别） | 无 | `m_cArray` ↔ `m_cBkArray` 互换（原版 `myRestore()`） |
| 相似关卡识别 | 无 | `findSubimages()` / `isSubimage()` / `getPixelDeviateWeightsArray()` / `calSimilarity()` 逐行照抄 |
| **ARGB_4444 量化** | 无 | PC 用 8-bit `TYPE_INT_ARGB`，原版是 `ARGB_4444`；补 `quant4444(v)` 折回 4-bit，否则 `\|grey0-grey1\| < 5` 的闸门行为不一致 |
| 「识别设置」对话框 | 无（只有个空方法） | `recog_dialog.xml` 复刻：上下 2dp 分隔线 + `#363636` 带 + 居中的「相似度:」+ 五个自绘方格（5~9），带初值高亮 |
| 退出确认框 | 直接关窗 | `HoloAlertDialog`「请选择:」三按钮（取消/退出/进入编辑），且仅在 `行数≥3 && 列数≥3` 时弹 |
| 「进入编辑」交接 | 无 | `prepareEditHandoff()`：写 `myMaps.sFile="创编关卡"`、`curMap.fileName="Recog_时间戳.XSB"`、`curMapNum=-4/-5`、灌 `edPict*/edRows/edCols` |
| 4 个边线指示灯 | 无 | `L_Rect/T_Rect/R_Rect/B_Rect` 半径 50 的圆（`Canvas.drawOval`）；长按灯 → `isLamp` 闪烁定时器 |
| 有效区边线拖拽 | 无 | `MoveSideLine`，四边命中测试 |
| 双击 | 无 | `doDoubleTap` 的 4 个分支（200px 判定） |
| 焦点格「回」字高亮 | 无 | `cur_Rect` + `setPR()` 的 `mPoff`/`mPR` 四个矩形 |
| 网格线 | 无 | 原版 `getWall` 口径：**先画偏移 1px 的黑线，再画白线** |
| XSB 字符三档字号 | 无 | 全角字符 / 替代字符 `■▲◇∏＋` 两档 |
| 底部两行信息 | 无 | 「关卡尺寸: C × R」「箱子: N  目标点: M」，走 `drawTextFilled()`（先描边再填充，因为 Swing 的 `drawString` 只做 FILL，原版是 `FILL_AND_STROKE`） |

**忠实保留的原版瑕疵**（按 1:1 原则，不「顺手修好」）

- `getXSB()` 的 `<=` 越界：循环写成 `for (i = 0; i <= m_nRows; i++)`，产出的是
  **行数+1 × 列数+1** 的字符阵。原版如此，PC 照抄。
- `setArena()` 里 `m_fLeft = values[Matrix.MSCALE_X]` 是原版的笔误（应是 `MSCALE_X` 之外
  的另一个值），因为 `onDraw` 会重算所以无害，PC 照抄。

**新增基础设施**

- `compat/HoloViewDialog` —— `AlertDialog.Builder.setView(v)` 的等价物
  （Holo 外壳 + 自定义内容 + 一个「确定」），「识别设置」用它。
- `compat/HoloAlertDialog.create(Frame, String)` —— 构造函数是 `protected`，
  同包外没法直接 `new`，补一个静态工厂（不改可见性，副作用最小）。
- `compat/HoloChoiceDialog.selectThenOk(...)` 增加**带初始下标**的重载
  （原版 `setSingleChoiceItems(items, checkedItem, listener)` 的三参形式）。
- `compat/android/graphics/Canvas.drawOval(RectF, Paint)` +
  `compat/android/graphics/RectF.contains(float, float)` —— 指示灯命中测试要用。
- `myActionBar` 增加 `setBarActionTitle(String, String)` / `getBarActionTitles()` /
  `fireBarAction(String)`；`BarAction.text` 由 `final` 改为可变（「识别」要改自己的标题）。

**新增测试**

- `Phase16RecogViewTest`（39 个用例）：6 项菜单与顺序 + 无溢出菜单；「识别」自改名；
  `悔` 的数组互换；`减`/`增` 的前置条件与取消连续调整；底部栏几何与各按钮 x 坐标；
  选中态三色；边线移动步长 3；备份/还原；`removeXSB` 四种字符模式；`clearXSB`；`myCount`；
  `getXSB` 的 +1/+1；`initArena` 默认值；4 个指示灯矩形；单击放置/切换（墙/复合目标/玩家唯一/
  点外部清焦点）；双击左边线；长按灯（actNum=5）；单击灯切 `isTopLeft`；
  `findSubimages` 全命中 + 已识别格锁定；`doAction` 填充/跳过；`calSimilarity`（1.0/0.0/0.25）；
  `getPixelDeviateWeightsArray` 灰色偏置 0；相似度对话框 5 格 + 初值高亮 + 点击回写模型；
  `prepareEditHandoff` 的正常(-4) 与小图(-5) 两条分支；过小关卡直接关窗。
- `RecogSnapshotTool` —— 离屏合成一张 10×10 关卡图后渲染 `recog-view.png`(370×780) 与
  `recog-dialog.png`(352×215)。**必须单独开工具**：`WindowSnapshotTool` 的
  `07-myRecogView` 没有 `myMaps.edPict`，画出来是空的。

**顺带修掉的既有偏差**：`Phase4BatchBTest` / `Phase7SystemIntegrationTest` 里断言的
`myRecogView` 用的是改前的自造 API，已改为按原版语义断言（6 项菜单、按钮宽度 29/35）。

#### `myEditView`（「关卡编辑器」）：原版 2407 行 / 改前 629 行

**先纠正一个方向性错误**：审计原先把 `myEditView` 和 `myGameView` 一起列为
「要换成 `myActionBar`」——**`myEditView` 不该加 ActionBar**。
原版 `onCreate()` 里是 `requestWindowFeature(Window.FEATURE_NO_TITLE)` +
`FLAG_FULLSCREEN`（`myEditView.java:79-81`），**根本没有 ActionBar**，
13 项菜单由底栏「更多」按钮的 `openOptionsMenu()` 弹出（`:362`、`:459`）。
`myGameView` 早已按这个形态改好了，`myEditView` 是最后一个还在用 `JMenuBar` 的窗口。

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| 菜单载体 | 自造 `JMenuBar`（`文件`/`编辑`/`帮助` 三组共 10 项，标题与 `edit.xml` 全不对） | 删掉；底栏「更多」→ `HoloPopupMenu`，`edit.xml` **全 13 项**且顺序一致 |
| 内容区高度 | 被 `JMenuBar` 挖走 23px | 恢复 370×780 |
| `标尺...` | 无 | `rule_dialog`：标尺字体灰度 SeekBar + 两个色样 + 5 个元素的 multi-choice（`m_Sets[21]/[22]`） |
| `设置...` | 无 | 2 项 multi-choice：`YASC绘制习惯`（`m_Sets[19]`）/ `系统导航键`（`m_Sets[16]`），确定后落 ini |
| `区块另存为...` | 无 | `myBlockSave()`：无选区→「请选择一个区块！」；选区太小→「区块太小！」；否则写 `~Block_时间戳.XSB` 并挂进关卡列表 |
| `关卡资料` | 无 | `myMaps.curMap.Map == null` →「做好关卡保存后才可以哦！」，否则开 `myAbout2` |
| `导入(XSB 或 Lurd)` | 只走 `loadXSB`，且没有确认框 | 剪切板内容先给一个可编辑的确认框；`isLURD` → `LurdToXSB()`，否则 `loadXSB()` |
| `LurdToXSB`（逆推法） | 无 | 照抄：从答案**尾部往前**扫，仓管员初始位置预设画布正中，新访问格先记 `'_'`，撞墙/越界/推不动即 `return false` |
| `修改尺寸...` | 4 个 0~20 的 Spinner，无模式、无校验 | 4 个 0~30 Spinner + **「扩充 / 消减」单选**（`mySign = ±1`）+ **8 条越界校验**逐条照抄（含括号里的数字） |
| `提交` | 硬编码 `"PC作者"`、不弹关卡集选择框 | 先按需存盘 → 「提交到」单选（`mSets3` + 末尾自动加「新关卡集」，选中则 `add_T(3,…)`）→ `Normalize2()` → `add_L()` |
| `试推` | `doSave()` 后直接开窗口 | 先按需存盘 → `mapNode` 校验 `L_CRC_Num != -1`，否则「关卡尚不规范！」 |
| `关卡标准化` | 只调 `mySelectAll()`（名不副实） | `Normalize()`：四周补墙 → 可达性扩散 → 非可达区清成 `'-'`、邻可达则补 `'#'` → 四至**再外扩一圈**；箱/目标数不对只 `MyToast` |
| `Normalize2()` | 无 | 提交用：不补墙、四至**收缩**、箱/目标数不对弹「警告」并返回 false |
| `清空地图` | `JOptionPane` | `HoloConfirmDialog`（取消/确定），清完后 `剪切`/`复制` 一并置灰 |
| `导出(XSB)` | 只复制纯 XSB + `JOptionPane` | 有选区只导选区，否则导有效部分；带 `Title/Author/Comment/Comment_end` 头 + 可编辑的「剪切板」确认框 |
| `离开` | `JOptionPane` | `MyToast`「离开！」+ `exitDlg`（原版 `onCreate` 里建好的「有修改未保存，坚持退出吗？」） |
| 「更多」长按 | 无 | = 原版 `onLongClick`：弹「离开！」后 `finish()` 或 `exitDlg.show()`，且 `return true` 吞掉随后的 click |

**新增测试**

- `Phase17EditViewTest`（16 个用例）：无 `JMenuBar`；13 项菜单的顺序/标题/可见性
  （并反向断言自造的「从剪贴板导入 XSB」「导出 XSB 到剪贴板」已消失）；
  `resizeError` 的通过与「宽度<3」「超宽」「左侧空间不足」三条；
  `clearMap` 的清屏 + 剪切/复制置灰 + 撤销栈；无选区时 `myBlockSave` 不写文件；
  `LurdToXSB` 的成功重建与撞边界返回 false；`Normalize2` 成功收四至；
  `getXSB` 收掉外围空行；`saveFile` 写出文件头并回写 Rows/Cols；`exitDlg` 已建。

> ⚠️ 写这批用例时的一个约束：**确认框 / 剪切板框 / 设置框都是模态 `JDialog`**，
> 用例里弹出来会挂死 EDT。所以只测「纯逻辑 + 已拆出来的动作方法」
> （为此把 `clearMap()` 从确认框回调里拆成独立方法），
> 并给用例类加 `@Rule Timeout.seconds(30)` 兜底。

#### `myGridView` 最后 3 个置灰项（阶段 D-2 收尾）

`buildActionBar()` 里这三项原先都写成 `addAction(..., false, myActionBar.NO_OP)`
（标题在、位置对，但点不动）。原版 `BoxMan` **没有 `onPrepareOptionsMenu`**，
`levels.xml` 的 13 项全部常驻可用 —— 所以置灰是 PC 侧的偏差。

| 项 | 改写前 | 改写后 |
|----|--------|--------|
| `╋`（`levels_add`） | 置灰 | 两条分支：`sFile == "创编关卡"` → 「关卡尺寸」框（默认 10 列 × 15 行）→ `mapNode(rows, cols, null, "NewLevel_时间戳.XSB", "", "")` + `curMapNum = -2` → 进编辑器；其它关卡集 → 两项 PopupMenu（`添加关卡(文档)...` / `添加关卡(剪切板)...`） |
| `清空列表`（`levels_clear`） | 置灰 | 确认框 → `Clear_L_DateTime()` + `m_lstMaps.clear()` + 刷新 |
| `批量删除...`（`levels_delete_more`） | 置灰 | 「删除范围: 1 -- N」两个数字框 → **从后往前**删序号区间；创编关卡删磁盘 `.XSB`，其它关卡集走 `del_L()` |

**顺带修掉的既有偏差**

- `NewLevelDialog` 多出「关卡标题 / 作者姓名」两个输入框，并把作者默认成 `"PC作者"`
  —— 原版 `new_level_dialog.xml` 只有「列 × 行」两个数字框，作者默认串也正好踩中
  审计 2.1 记的「凭空造作者名」。已删掉这两个字段，监听器签名收成
  `onNewLevel(int rows, int cols)`。
- `BoxManPC.importLevelFile(File, boolean)` 原本把解析循环和文件读取揉在一个方法里。
  已抽出 `importLevelReader(BufferedReader, setTitle, silent)` 核心，
  并新增 `importLevelText(text, setTitle, silent)` —— 原版的「剪切板导入」就是这条路，
  PC 侧原先把入口整个砍掉了（见 2.4）。`myGridView` 的两项导入菜单复用它。
- `myActionBar` 新增 `isBarActionEnabled(String)` / `isActionEnabled(String)`，
  供测试断言「不再是置灰占位」。

**新增测试**：`Phase18GridViewMenuTest`（9 个用例）：三项均已可点、非创编关卡时
「╋」弹出两项且标题与原版一致、`clearList()` 清空列表、`deleteRange()` 的闭区间语义 /
从后往前不越界 / 创编关卡真的删掉磁盘文件、导入选项面板的构建。

**顺带修掉的测试**：`Phase5DialogTest.testNewLevelDialog` 与 `DialogSnapshotTool`
按新的 `NewLevelDialog` 签名改写（原用例在断言那两个被删掉的标题/作者输入框）。

> **全项目已无 `myActionBar.NO_OP` 置灰占位项。**

### 阶段 D-3 —— 弹出菜单载体收口 + `myGameView` 选项菜单（已完成，2026-09-25）

**问题**：`myGameView` 是 `FEATURE_NO_TITLE` + `FLAG_FULLSCREEN`，**没有 ActionBar**，
它的 13 项菜单全在 `res/menu/player.xml` 里、由底栏「更多」按钮的 `openOptionsMenu()` 弹出。
PC 侧改前用的是裸 `JPopupMenu + JMenuItem`：样式与 ActionBar 溢出菜单
（`popup_menu_holo_dark`：深灰 `#333333`、行高 40dp、最小宽 200dp）不一致，
而且只实现了 **8 / 13** 项。

#### `myGameView.openOptionsMenu()`

| 项 | 改前 | 改后 |
|----|------|------|
| 载体 | 裸 `JPopupMenu` + `JMenuItem` | `HoloPopupMenu`（`popup_menu_holo_dark` 等价物） |
| 条目 | 8 项（`设置...`/`开关选项...`/`重新开始`/`退至首`/`进至尾`/`保存状态`/`关于`/`退出`） | **12 项**，顺序严格按 `player.xml` |
| `导出...` | ❌ 无 | ✅ 移植 `player_EX`：打包「关卡初态 XSB + 正推现场 + 正推现场-旋转 + Lurd + 标尺 + 箱子编号」→ `myExport(xsb,lurd,local,local8,isAns,gifStart,rule,boxNum,importYass)`（PC 侧构造器签名早已对齐原版 Bundle，直接可用） |
| `导入...` | ❌ 无 | ✅ 移植 `player_IN`：`setACT(false)` + 清 `m_ActionIsRedy` + 记录制起始点 → `myActGMView` |
| `打开状态...` | ❌ 无 | ✅ 移植 `player_load` + `myOpenState()`：含「宏调试中」的「关闭调试」对话框（`HoloAlertDialog` + `HoloContent.check`），然后 `load_StateList` → 修正 `Solved` → 按时间倒序排 `mState1` → 开 `myStateBrow` |
| `操作说明` | ❌ 无 | ✅ `new Help(1)`（原版 Bundle 传 `m_Num = 1`） |
| `关于` | 自造 `JOptionPane`（「推箱快手 (BoxMan) PC版」） | ✅ 改为原版的 `myAbout2`（关卡描述） |
| `YASS求解` | ❌ 无 | ✅ 阶段 F 已接（原版那一步是**跨应用 Intent**，PC 无等价机制 → 真逻辑照留、最后一步落到「没有找到求解器！」分支） |

> `player.xml` 里 `player_Festival_Solver`「Solver求解」整段被 `<!-- -->` 注释掉，**原版不存在**，不补。

**顺带修正**：原版 `player_IN` 会把 `LOCAL = myMaps.getLocale(m_cArray)` 塞进 Bundle，
但 `myActGMView` 里接收那一行是**注释掉的**（`myActGMView.java:74`）—— 所以 PC 不传是对的。

#### 弹出菜单载体统一（`HoloPopupMenu`）

| 位置 | 改前 | 改后 |
|------|------|------|
| `myGameView.openOptionsMenu()` | 裸 `JPopupMenu` | `HoloPopupMenu` |
| `myGridView.LevelCard.showContextMenu()` | 裸 `JPopupMenu` + 7 个 `JMenuItem` | `HoloPopupMenu` + **原版 14 项**（阶段 G ②） |
| `mySolutionBrow` 列表上下文菜单 | 裸 `JPopupMenu`，标题「复制答案 (LURD)」 | `HoloPopupMenu`，标题改回原版的 **`导出到剪切板: Lurd`** |

阶段 G ④ 删掉 `myGameView.installMapPopupMenu()` 之后，全项目**已无任何裸 Swing 菜单树**
（`Phase20MenuCarrierConventionTest` 的白名单已清空，改为「全项目 0 处 `new JMenuItem(`」）。

#### ⚠️ 新发现的偏差：`menu_fidelity.py` 看不到「上下文菜单」

`menu_fidelity.py` 只扫 `res/menu/*.xml`（`onCreateOptionsMenu` 的菜单），
**扫不到 `onCreateContextMenu()` 注册的上下文菜单**。实测原版有 3 处：

| 位置 | 原版条目 | PC 现状 |
|------|---------|---------|
| `myGridView` 关卡项长按 | **14 项**：打开 / 改编为新关卡 / 图标加锁 / 迁出关卡至... / 复制关卡到... / 导出... / 移动到... / 前移 / 后移 / 删除 / 查找相似关卡 / 连续选择至... / 反选 / 详细... | ✅ 阶段 G ② 已对齐（含逐项可见性矩阵） |
| `mySolutionBrow` | 1 项：导出到剪切板: Lurd（另两项被注释） | ✅ 阶段 D-3 已对齐标题 |
| `myGameView` / `myGameViewMap` | **没有上下文菜单** | ✅ 阶段 G ④ 已删除 PC 自造的那 5 组多级右键菜单 |

原版 `myGridView.onContextItemSelected()` 的 14 个分支合计约 **612 行**
（`myGridView.java:1079-1690`），是 PC `myGridView` 比原版（1810 行）少的那部分主体。
阶段 G ② 已补齐：`onContextItemSelected(int)` + `editAsNewLevel()` + `migrateToSet()` /
`applyMigrate()` + `exportCurrent()` + `onMoveTo()` / `moveTo()` + `onShift()` +
`prepareDeleteSelection()` / `deleteSelected()` + `onFindSimilar()` / `startFind()` /
`prepareFindSource()` / `onFindDone()` + `onSelectRange()` / `selectRange()` +
`swap()` / `updateNO()` / `recycleBitmapCaches()`，以及新类
`myFindFragment`（相似度搜索引擎）与重写的 `FindDialog`（`find_dialog.xml`）。


---

## 阶段 E —— 导入/导出入口链恢复（已完成，2026-09-25）

### 改前

| 入口 | 原版 | PC（改前） |
|------|------|-----------|
| 主菜单「导入...」 | `menu_set` → `myMaps.m_Set_id = -1; sel_Set();` | 裸 `JFileChooser` → `importLevelFile(File)`（`chooseImportFile()`） |
| 主菜单「导出...」 | `menu_exp_ans` → `sel_Set2();` | PC 自造的 `ExportDialog`（`HoloAlertDialog` 子类，含 `JProgressBar` + 日志区） |
| 批量导入执行体 | `mySplitLevelsFragment`（`DialogFragment` + `AsyncTask` + `ProgressDialog`） | ❌ 未移植（`SplitDialog` 是个带「确定」按钮的空壳） |
| 批量导出执行体 | `myExportFragment`（同上） | ❌ 未移植（逻辑塞在 `ExportDialog` 里） |

### 改后

| 新增/修改 | 内容 |
|-----------|------|
| `mySplitLevelsFragment.java`（新） | 逐行照搬原版 `SplitTask.doInBackground()` 的 **3 条分支**：`myType==0` 剪切板 / `==1` 单文档 / `==2` 关卡集文档列表。`publishProgress()` → `SwingWorker.publish()`，`ProgressDialog` → `compat/HoloProgressDialog`，`isCancelled()` → `volatile cancelled`。另加 `runNow()` 供测试同步跑。 |
| `myExportFragment.java`（新） | 照搬 `ExportTask.doInBackground()` + `exportSet()`（含 `Comment-End:` 拼装、`get_Ans()` 取答案、`expAnsLevel()` 走「仅有答案的关卡」）。 |
| `BoxManPC.sel_Set()` | `import_dialog3.xml` 的等价物：6dp 色条 → `#334455` 分组条（`关卡集：` 100dp + 右对齐「全选」80dp）→ 190dp 多选列表 → 8dp `#445566` → `选项：` + XSB/Lurd（互为兜底）→ 6dp → 编码单选（`paddingLeft 12dp`）→ 12dp。 |
| `BoxManPC.sel_Set2()` | `export_dialog3.xml` 的等价物：分组条右侧是「仅答案关卡」+「全选」；选项行「含答案」+「答案含备注」；末行 80dp 占位 +「覆盖同名文档」。 |
| `BoxManPC.onSplitDone()` / `onExportDone()` | 照搬原版：刷新列表 + `updateActionBarTitle()`；`m_Nums[2]==1 && m_Nums[3]==0` 时只弹 Toast「导入成功！」，否则 `HoloMessageDialog`；导出固定弹标题「已存入“导出/”文件夹」。 |
| 启动目录 | 补上原版 `BoxMan.java:192-203` 的 7 个目录（`超长答案/`、`导入/`、`导出/`、`创编关卡/`、`关卡图/`、`宏/`）。**这是 `myExportFragment` 不自己 `mkdirs()` 的前提**。 |
| 删除 | `SplitDialog.java`（PC 自造）、`ExportDialog.java`（PC 自造），共约 400 行。 |

### 两处「代码覆盖 XML」的初值（照抄，别「修好」）

| 控件 | XML | 原版代码 | 有效初值 |
|------|-----|---------|---------|
| `im_all`「全选」 | `checked="true"` | `m_All.setChecked(false)` | **否** |
| `im_xsb`「XSB」 | `checked="false"` | `m_XSB.setChecked(true)` | **是** |
| `ex_rewrite3`「覆盖同名文档」 | `checked="false"` | `m_ReWrite.setChecked(true)` | **是** |

> ⚠️ 还有一个**原版自带的怪癖**，PC 侧照抄未改：`ex_ans`「仅答案关卡」的初值来自 XML
> （`checked="true"`），但只有它的 `OnCheckedChangeListener` 会把状态写进 `myMaps.isXSB`。
> 若上一次导入把 XSB 取消勾选过，`myMaps.isXSB` 就是 `false`，此时打开导出对话框、
> 不再动这个框，「仅答案关卡」虽然显示勾选却不会被导出。

### 两处为可测性做的拆分

`sel_Set()` / `sel_Set2()` 各拆成「搭对话框」+「显示」两半：
`buildImportDialog()` / `buildExportDialog()` 返回未显示的 `HoloAlertDialog`，
`sel_Set()` / `sel_Set2()` 只负责 `.setVisible(true)`。
这样 `Phase21ImportExportChainTest` 能直接遍历组件树核对初值
（否则模态对话框会把用例挂死，见 MEMORY 的「写 Swing 测试的固定坑」）。

### 覆盖

`Phase21ImportExportChainTest`（16 个用例）：入口链载体（无 `JFileChooser`、菜单接对方法）、
两个对话框的初值、`mySplitLevelsFragment` 的 3 条分支（全勾选导入 / 跳重复关卡集 /
跳未勾选）、`myExportFragment` 的写盘 / `...跳过` / `...覆盖` / `.txt` 后缀 /
「仅答案关卡」/ 负 id 过滤。快照 `d12-Import.png` / `d13-Export.png` 已加入
`DialogSnapshotTool`。

### 当前测试基线

```
39 个用例类 / 412 个测试用例  全部通过
（阶段 E 后：32 / 242 → 阶段 F 后：32 / 251 → 阶段 G ②④ 后：34 / 291
  → 阶段 G ⑥ 后：35 / 307 → 阶段 G ⑦ 后：36 / 354 → 阶段 G ① 后：37 / 379
  → 阶段 G ③ 后：38 / 392 → 阶段 G ⑤ 后：39 / 412）
./gradlew --offline clean test   BUILD SUCCESSFUL
```

新增测试类：`Phase14ActGMTest`(28)、`Phase15FindViewTest`(20)、`Phase16RecogViewTest`(39)、
`Phase17EditViewTest`(23)、`Phase18GridViewMenuTest`(9)、
`Phase19GameViewOptionsMenuTest`(13)、`Phase20MenuCarrierConventionTest`(5)、
`Phase21ImportExportChainTest`(16，阶段 E)、
`Phase22GridViewContextMenuTest`(24，阶段 G ②)、
`Phase23FindFragmentTest`(12，阶段 G ②)、
`Phase24EditUndoRedoTest`(16，阶段 G ⑥)、
`Phase25BoxManContextMenuTest`(47，阶段 G ⑦)、
`Phase26PicListAndFileExplorerTest`(25，阶段 G ①)、
`Phase27SolutionBrowTest`(14，阶段 G ③)、
`Phase28HoloButtonTest`(20，阶段 G ⑤)。
新增快照工具 `RecogSnapshotTool`；`DialogSnapshotTool` 增加 `d12-Import` / `d13-Export`；
`WindowSnapshotTool` 增加 `17-myExport`（此前漏了这个窗口）。
`Phase4BatchBTest` / `Phase7SystemIntegrationTest` 的 `myFindView`、`myRecogView` 断言
均已按原版语义改写；阶段 E 删掉 `SplitDialog` / `ExportDialog` 后，
`Phase4SecondaryViewTest.testSplitDialogWorker` 与 `Phase5DialogTest.testExportDialog`
改为断言新移植的 `mySplitLevelsFragment` / `myExportFragment`。
阶段 F 给 `Phase17EditViewTest` 补了 6 条（长按求解闸门、长按尺寸区、填充/勾边），
给 `Phase19GameViewOptionsMenuTest` 补了 2 条（正推/逆推下的 `YASS求解`）。
阶段 G ④ 删掉 `DelDialog` 后，`Phase5DialogTest.testDelDialog` 改为断言该类已不存在；
`testGameViewHasNoMenuBar` 改名为 `testGameViewHasNoMenuBarAndNoMapPopupMenu`，
新增「地图上没有右键菜单」的断言。

**菜单保真度**：`strict OK 41 → 76 → 77 → 81`、`loose 真缺 12 → 5 → 4 → 0`。
**阶段 G ① 把最后 4 项全收口了**：`filelist.xml 上一级`/`完成`（新移植
`myFileExplorerActivity`）、`gif.xml 制作`（`myGifMakeDialog` 的按钮文案由「确定」改回
「制作」）、`piclist.xml 位置`（`myPicListView` 重写后加上 ActionBar「位置」）。
现在 **81 / strict 缺 0 / loose 缺 0**。
阶段 G ②④ 与阶段 G ⑦ **不改变**这两个数字 —— 脚本只扫 `res/menu/*.xml`，
扫不到 `onCreateContextMenu()` 的上下文菜单（见上）。
⚠️ 同时修了脚本自身的映射表：`filelist.xml` 此前指向 `BoxManPC.java`（因为 PC 侧
没有那个类），现改指向 `myFileExplorerActivity.java`；`gif.xml` 标注为「孤儿资源」。

> ⚠️ 注意 `menu_fidelity.py` **测不出「置灰」** —— `myGridView` 那三项的标题一直都在，
> 脚本从阶段 6 起就把它们算成 OK。所以「标题齐」之后还要单独查一遍
> `addAction(title, false, NO_OP)` 这种置灰写法（见阶段 6 的第 5 条）。
> 同理它也**测不出上下文菜单**（见阶段 D-3 末尾）。
>
> ⚠️ 也注意它的 **loose 判定过于宽松**（「全项目任何源码里出现过该标题字符串」）：
> 阶段 E 删掉 PC 自造的 `SplitDialog` 后，`filelist.xml 完成` 才暴露出是真缺 ——
> 老数字「loose 真缺 4」是那个对话框里 `btCancel.setText("完成")` 造成的**假阴性**。
> **任何无关的 PC 自造 UI 都可能掩盖真实缺口**，判断时以 strict 列为准。



### 后续阶段

- **阶段 D-2：✅ 已完成**（`myStateBrow` / `myActGMView` / `myFindView` / `myRecogView` /
  `myEditView` / `myGridView` 六个窗口全部收口，全项目已无 `JMenuBar`、无置灰占位项）。
- **阶段 D-3：✅ 已完成**（弹出菜单载体全部统一到 `HoloPopupMenu`；
  `myGameView.openOptionsMenu()` 从 8 项补到 12 项）。
- **阶段 E：✅ 已完成（2026-09-25）**
  - 已随 D-2 修掉：`doSubmit()` 的硬编码 `"PC作者"`、`showResizeDialog()` 的「扩充/消减」
    与 8 条校验、`doNormalize()` 名不副实、`NewLevelDialog` 的 PC 专属标题/作者框。
  - 本轮修掉：`BoxManPC` 主菜单「导入...」/「导出...」走回原版入口链
    （`sel_Set()` / `sel_Set2()` + `mySplitLevelsFragment` / `myExportFragment`），
    删掉裸 `JFileChooser` 与 PC 自造的 `SplitDialog` / `ExportDialog`；详见「阶段 E」小节。
- **阶段 F：✅ 已完成（2026-09-25）**
  - **YASS 求解**：先纠正了本审计原先的误判（原版用的是**跨应用 Intent**，不是 `ProcessBuilder`）。
    已接 `player.xml` 的「YASS求解」与 `myEditViewMap.onLongPress` 的长按仓管员素材两条入口；
    `mySolution()` 里「自动保存当前状态」那段真逻辑照原版保留，最后一步抛出 →
    落到与真机未装求解器相同的 catch 分支；回流接口 `onSolverResult(String, boolean)` 按原版保留。
  - **jpackage 打包**：`build.gradle` 新增 `packageApp`，实测产出可运行的 `BoxManPC.exe`。
    详见「阶段 F」小节。
- **阶段 G（收口用）**：
  1. ✅ **已完成（2026-09-25）**：补 `myFileExplorerActivity`（`filelist.xml` 的
     「上一级」/「完成」，224 → 331 行）/ `myPicListViewAdapter`（183 → 111 行），
     并**重写** `myPicListView`（原版 258 行；PC 此前是自造的 `sRoot+sPath` +
     底部按钮栏 + `JFileChooser`，现改为 ActionBar「位置」+ 3 列 GridView +
     上下文菜单「加载/删除」+「图片位置」5 项单选框）。
     顺带把 `myGifMakeDialog` 的确认按钮由「确定」改回原版的**「制作」**
     （`myExport.java:423` 的 PositiveButton；`res/menu/gif.xml` 是从未 inflate 的
     孤儿资源）。**至此菜单保真度 81 / strict 缺 0 / loose 缺 0。** 详见「阶段 G ①」小节。
  2. ✅ **已完成（2026-09-25）**：`myGridView` 的 14 项上下文菜单 + `myFindFragment`
     相似度搜索引擎（详见「阶段 G ②④」小节）；
  3. ✅ **已完成（2026-09-25）**：**`mySolutionBrow` 整体重做** —— 原版是
     `ExpandableListView` + ActionBar 的 Activity（304 行），PC 此前是自造的**模态
     `JDialog`** + 底部按钮栏（107 行）；现改为 `JFrame` + `myActionBar` +
     「答案」组头 + 子项列表（248 行），并删掉 `JOptionPane` 与自造按钮。
     详见「阶段 G ③」小节。
  4. ✅ **已完成（2026-09-25）**：删除 `myGameView.installMapPopupMenu()`
     （原版 `myGameView` / `myGameViewMap` 无任何上下文菜单）+ 顺带删掉 PC 自造的 `DelDialog`；
  5. ✅ **已完成（2026-09-25）**：统一普通按钮的 Holo 深色样式 —— 新增
     `compat/HoloButton`（按 AOSP `btn_default_holo_dark` 9-patch 实测像素自绘：
     正常 / 按下 / 禁用三态 + 透明外缘 + 顶棱 + 暗底缘 + 1px 描边），
     替换 `myActGMView`（5 个）/ `mySubmit`（2 个）/ `myExport`（1 个 + 自造「关闭」），
     顺带订正 `myActGMView` 按钮高度 36dp → 原版的 **48dp**、
     `myExport` 按钮文案「执行导出」→ 原版的 **「导出」**。详见「阶段 G ⑤」小节。
  6. ✅ **已完成（2026-09-25）**：核 `myEditView` 撤销栈的 `Act(...)` 语义 ——
     `myUnDo()`/`myReDo()` 补上按 `act` 分四类的现场还原；顺带发现并补齐
     `myRotate()` 缺失的 180°/顺 90°/逆 90° 三个分支（此前是**空操作**）
     以及完全缺失的 `myRot90()` / `resetSize()`。详见「阶段 G ⑥」小节。
  7. ✅ **已完成（2026-09-25）**：`BoxMan`（关卡集列表）的 10 项上下文菜单 ——
     原版 `BoxMan.java:1445-1465` 的 `onCreateContextMenu()` / `onContextItemSelected()`
     （打开 / 导出... / 清理状态记录... / 删除答案... / 扩展组另加 重命名... / 删除 /
     添加关卡(文档)... / 添加关卡(剪切板)... / 添加比赛关卡(sokoban.ws) / 详细...）。
     顺带补齐了整条入口链缺的件：`BoxManPC.sel_File()` / `read_Plate()`、
     `export2_dialog.xml` 的导出选项框、`reName()`、删除答案 / 删除关卡集的线程、
     `get_uil_dialog.xml` 的 `UrlInputDialog`（重写）+ 比赛关卡下载（`url` / `url_Num` /
     `MyThread` / `myJson` / `handler`）。详见「阶段 G ⑦」小节。

---

## 阶段 F —— YASS 求解 + jpackage 打包（已完成，2026-09-25）

### 1. 先纠正本审计自己的误判

原审计第 4.7 节与「后续阶段」都写着「YASS 求解器接入（`ProcessBuilder`）」，并把
「全库 0 处 `ProcessBuilder`」当成缺口证据。**回原版核实后这是错的**：

```java
// android/app/src/main/java/my/boxman/myGameView.java:4339-4352
Intent intent3 = new Intent(Intent.ACTION_MAIN);
intent3.addCategory(Intent.CATEGORY_LAUNCHER);
ComponentName name = new ComponentName(
        "net.sourceforge.sokobanyasc.joriswit.yass", "yass.YASSActivity");
intent3.setComponent(name);
intent3.setAction("nl.joriswit.sokosolver.SOLVE");
intent3.putExtra("LEVEL", myMaps.getLocale(m_cArray));
startActivityForResult(intent3, 1);
```

是 Android 的**跨应用 Intent**（唤起第三方 YASS App），**不是外部进程**。
`myEditViewMap.mySolution()`（`myEditViewMap.java:93-107`）是同一段代码的副本。

### 2. 改了什么

| 文件 | 改动 |
|------|------|
| `myGameView.java` | `openOptionsMenu()` 打开 `YASS求解` 项 → `onYassSolver()`；新增 `onYassSolver()` / `mySolution(int)` / `onSolverResult(String,boolean)` |
| `myEditViewMap.java` | 新增 `mySolution()`；`onLongPress()` 补回原版的 `rtM` 前置判断与「长按素材填充/勾边」分支 |
| `myEditView.java` | `DoAct(0)` 从「只记一笔 undo」的桩，补成原版的「弹『请选择：填充/勾边』→ 实际填充/勾边」；填充与勾边拆成 `fillSelection()` / `outlineSelection()` 便于测试 |
| `build.gradle` | 新增 `packageApp`（`Exec` + `jpackage`） |
| `src/main/resources/drawable/icon.ico` | 新增（由原版 `drawable-xhdpi/icon.png` 96×96 下采样出 16/24/32/48/64/96 六帧） |

**PC 上的落地方式**：原版最后一步是跨应用 Intent，PC 没有等价机制 —— 语义上等价于
「真机未安装求解器」，所以**直接抛出**，落到与真机相同的 `catch` 分支：

```java
} catch (Exception e) {
    MyToast.showToast(this, "没有找到求解器！", MyToast.LENGTH_SHORT);
}
```

`mySolution()` 里 **前面那段「自动保存当前状态（自动查重）」是真逻辑，照原版逐行保留**
（`count_S` 查重 → 按 `[YASS]`/`[导入]`/时间戳改写 `m_imPort_YASS` → `add_S(...)` 14 参 →
`m_bMoved = false` → Toast「状态已保存！」）。

求解成功后的回流按原版 `onActivityResult(requestCode == 1)` 保留为
`onSolverResult(String solution, boolean ok)`（成功 → `formatPath(solution, false)` +
Toast「答案已经载！」+ `m_imPort_YASS = "[YASS]"`）。
`myEditView` 侧原版**没有** `onActivityResult`，所以编辑器这条链到「没有找到求解器！」为止 ——
原版就是这样（求解结果会被静默丢弃），PC 不多做。

### 3. jpackage 的三个实测坑（JDK 26 / Windows）

```bash
cd desktop && ./gradlew --offline packageApp
# → build/jpackage/BoxManPC/{BoxManPC.exe, app/, runtime/}
# 换格式：-PjpackageType=msi|dmg|deb
```

1. **`--name` 必须是 ASCII**。jpackage 生成的原生 launcher 用 ANSI API 定位自带 runtime：
   安装目录名含中文时启动直接报
   `Error: could not find java.dll / Could not find Java SE Runtime Environment`。
   实测对照：`ASCII 名 + ASCII 目录` → 正常；`ASCII 名 + 中文目录` → 报错；
   `中文名 + 中文目录` → 报错。所以应用目录名用 `BoxManPC`，
   用户可见的**窗口标题仍是「推箱快手」**（`BoxManPC.setTitle`，不受影响），
   安装包元数据里用 `--description '推箱快手 PC 版'` / `--vendor 'sokoban.cn'` 保留原版身份。
2. **必须走 `@argfile`**。中文参数直接从 shell 传会被 Windows 控制台代码页转成 `????`，
   jpackage 报 `Invalid Application name: ????`；`@argfile` 由 jpackage 自己按 UTF-8 读，绕开 shell。
3. **`@argfile` 是按空白切词的**（不是「一行一个参数」）。含空格的值要自己加引号，
   否则报 `Found 2 non-option arguments on the command line.`。

另外：`--icon` 在 Windows 上**只认 `.ico`**，给 `.png` 会打一行警告然后退回默认图标。

产出的 runtime 已含 `java.sql` / `java.desktop` / `jdk.unsupported`（sqlite-jdbc 的
`sun.misc.Unsafe` 依赖）等模块，不需要额外 `--add-modules`。已实测启动成功
（FlatLaf 原生库加载、Swing 界面起来、`timeout` 到点被杀 → exit 124）。

---

## 阶段 G ②④ —— `myGridView` 14 项上下文菜单 + 删除 PC 自造的菜单树（已完成，2026-09-25）

### 1. 为什么 `menu_fidelity.py` 一直没报这一条

它只扫 `res/menu/*.xml`（即 `onCreateOptionsMenu` 的菜单），**扫不到
`onCreateContextMenu()` 注册的上下文菜单**。原版 `myGridView` 的关卡项长按走的是后者，
14 个分支合计约 612 行（`myGridView.java:1079-1690`）—— 这是 PC `myGridView`
比原版少的那部分主体，也是「阶段 D-3 之后仍存在的最大单块缺口」。

### 2. 原版菜单的完整形状（含可见性矩阵）

`onCreateContextMenu()` 固定 14 项，标题与顺序如下；
`ItemLongClickListener()` 随后按**关卡集类型 + 多选状态**逐项设可见性：

| # | 标题 | 最近推过 | 创编关卡 | 关卡查询 | 相似关卡 | 扩展组 | 内置组 |
|---|------|:--:|:--:|:--:|:--:|:--:|:--:|
| 1 | 打开 | ● | | ● | ● | ● | ● |
| 2 | 改编为新关卡（创编关卡下改叫 **编辑**） | ● | ● | ● | ● | ● | ● |
| 3 | 图标加锁（已加锁时改叫 **图标解锁**） | | | | | ● | |
| 4 | 迁出关卡至... | | | ● | ● | ● | ● |
| 5 | 复制关卡到... | | | ● | ● | ● | ● |
| 6 | 导出... | ● | | ● | ● | ● | ● |
| 7 | 移动到... | | | | | ● | |
| 8 | 前移 | | | | | ● | |
| 9 | 后移 | | | | | ● | |
| 10 | 删除 | | ● | | | ● | |
| 11 | 查找相似关卡 | ● | ● | ● | | ● | ● |
| 12 | 连续选择至... | | | | | | |
| 13 | 反选 | | | | | | |
| 14 | 详细... | ● | ● | ● | ● | ● | ● |

**多选模式下还有一次覆盖**（在原表之后无条件执行）：
第 2/6/11 项**强制隐藏**，第 12/13 项显示；
非多选时反过来 —— 第 2 项显示，第 6 项在「创编关卡」下、第 11 项在「相似关卡」下保持隐藏。

> ⚠️ 踩坑：一开始按「多选分支会把第 2 项重新打开」写测试，跑出来才发现反了 ——
> 多选分支**无条件先关掉**第 2 项。原版代码里这两个分支的先后顺序是有意义的。

### 3. 改了什么

| 文件 | 改动 |
|------|------|
| `myGridView.java` | `LevelCard.showContextMenu()` 从 7 项自造条目换成 `buildContextMenu()`（14 项 + 可见性矩阵）；新增 `onContextItemSelected(int)` 及 13 个动作方法：`editAsNewLevel()`、`migrateToSet()`/`prepareMigrateSelection()`/`applyMigrate()`、`exportCurrent()`、`onMoveTo()`/`moveTo()`、`onShift(int)`、`prepareDeleteSelection()`/`deleteSelected()`、`onFindSimilar()`/`startFind()`/`prepareFindSource()`、`onFindDone()`、`onSelectRange()`/`selectRange()`；新增工具 `swap(List,int,int)`、`updateNO()`、`recycleBitmapCaches(int,int)` |
| `myFindFragment.java` | **新增**。原版 618 行 `DialogFragment + AsyncTask` 的等价物：`FindTask.doInBackground()` 的关卡表 / 答案库两段 + `myCompare()` 精确相似度算法逐行照搬；`show()` 走 `SwingWorker` + `HoloProgressDialog`，另有同步的 `runNow()` 供测试 |
| `FindDialog.java` | **重写**。原 PC 版是个占位实现（相似度滑杆 + 用 `|ΔRows|<=2 && |ΔCols|<=2` 在内存列表里瞎凑结果，既不查库也不算相似度，**且没有任何生产代码调用它**）；现按 `find_dialog.xml` 1:1 重建（表头行 1 + 190dp 关卡集多选列表 + 表头行 2 + 210dp 相似度单选列表） |
| `myGameView.java` | 删除 `installMapPopupMenu()`（约 176 行）及其构造器调用 |
| `DelDialog.java` | **删除**。PC 自造的「删除确认 + 是否连同解答与状态一起删」对话框，原版没有 |
| `Phase20MenuCarrierConventionTest` | 白名单清空 —— 改为断言全项目 **0 处** `new JMenuItem(` |

### 4. `myFindFragment` 的两个模式（原版就是两段重复代码，这里保留）

`m_Sort` 决定走哪条：

- **排序模式**：源关卡的 8 个旋转各跑一遍完整的重叠区「晃动」扫描，取**最大相同格子数**，
  折算成相似率存进 `mapNode.Num`，最后按 `Num` 降序 `Collections.sort`。
- **非排序模式**：任一旋转的相同格子数达到 `mLeast` 就立即收录（`myCompare` 直接 `return 1`），
  相似率记 0，不排序。

`mLeast = (相似度==100 ? 源关卡格子总数 : floor(总数 * 相似度 / 100))`；
`myCompare` 开头还有一道闸门：`min(R1,R2) * min(C1,C2) < mLeast` 时直接 `return 0`。

### 5. 删除 `installMapPopupMenu()` 之前先逐项确认入口

删之前把那份 5 组菜单的每一项都追到了原版入口，确认**没有功能变不可达**：

| 自造菜单项 | 原版入口 | PC 现状 |
|-----------|---------|--------|
| 重新开始 | `player.xml player_ReStart` | ✅ `openOptionsMenu()` → `onReStart()` |
| 跳转到... | ✗ 原版无此项（原版是「退至首 / 进至尾」） | 纯 PC 便利项，删掉不损失原版功能 |
| 返回关卡列表 | ActionBar home / BACK | ✅ `handleExit()` |
| 后退一步 / 前进一步 | 底栏 `bt_UnDo` / `bt_ReDo` | ✅ 底栏按钮 |
| 保存当前状态 | `player.xml player_SaveState` | ✅ `onSaveState()` |
| 动作管理 | `player.xml player_IN`「导入...」 | ✅ `onImport()` |
| 关卡状态与答案 | `player.xml player_load`「打开状态...」 | ✅ `onOpenState2()` |
| 背景颜色 / 标尺与坐标 | 设置 → 更换背景 / 开关选项 | ✅ `showSetup1Dialog()` / `showSetup2Dialog()` |
| 导出关卡... | `player.xml player_Export` | ✅ `onExport()` |
| 导出为动画 (GIF)... | `myExport` 的 `exp_gif_make` / `myStateBrow` | ✅ 两处都在 |
| 关卡编辑器 | `myGridView` 上下文菜单「改编为新关卡 / 编辑」 | ✅ 阶段 G ② 刚接上 |
| 相似关卡对比 | `myGridView` 上下文菜单「查找相似关卡」→ `myFindView` | ✅ 阶段 G ② 刚接上 |
| 关卡图像识别 | `BoxMan` 菜单「图像识别」→ `myPicListView` → `myRecogView` | ✅ `BoxManPC.openRecognition()` |
| 游戏玩法说明 / 关于 | `player.xml player_help` / `player_about` | ✅ `Help(1)` / `myAbout2` |

### 6. 测试

新增两个用例类，共 36 条：

- `Phase22GridViewContextMenuTest`（24 条）：14 项标题与顺序、6 种关卡集类型下的可见性矩阵、
  多选覆盖、扩展组加锁改名、`swap` 的「让位」语义、`moveTo` / `onShift(±1)` 的边界、
  `updateNO` 回写 `L_NO`、`selectRange` 的越界钳制与双向、`deleteSelected`（含创编关卡删盘上文档）、
  图标加锁（含 `P_id < 0` 跳过）、`prepareFindSource` 的两条分支、`onFindDone` 的两条分支。
- `Phase23FindFragmentTest`（12 条）：`myCompare` 的恒等/闸门/忽略箱子/非排序两态；
  `runNow()` 的查库命中、跳过源关卡自身、关卡集过滤、降序排列、异常地图、
  以及临时表 `id_T` 用完即删。

**当前基线：34 个用例类 / 291 个测试用例，全部通过。**

---

## 阶段 G ⑥ —— `myEditView` 撤销栈的 `Act(...)` 语义 + 补齐 `myRotate`/`myRot90`/`resetSize`（已完成，2026-09-25）

### 1. 原计划只打算「核一遍」，结果核出两个真缺口

任务原文是「核 `myEditView` 撤销栈的 `Act(1/2/4/5/8/9/90)` 语义」。
对着原版 `myEditView.java` 逐行比完之后发现，问题不止「语义要核」：

| # | 缺口 | 严重度 |
|---|---|---|
| A | `myUnDo()` / `myReDo()` **完全没有按 `act` 分类**，只有一条「整片回写」路径 | 中（撤销/重做会丢选区、丢四至、丢变换矩阵） |
| B | `myRotate(int)` 只实现了 `3`/`4`（水平/垂直翻转），**`0`/`1`/`2`（180度 / 顺 90 度 / 逆 90 度）是空操作** | **高（三个菜单项点了没反应）** |
| C | `myRot90(boolean)`、`resetSize()` **整段不存在** | 高（B 的依赖） |
| D | 「变换」对话框用的是 PC 自造的 `JOptionPane.showOptionDialog`，且**选项文字与原版不一致** | 低（保真度） |

### 2. A —— 按 `act` 分四类还原现场

原版 `myUnDo()`（`myEditView.java:1097-1152`）与 `myReDo()`（`1154-1209`）
把同一段还原逻辑各写了一遍，**两处逐字相同**，分类如下：

| `act` | 动作 | 还原方式 |
|---|---|---|
| `3` | 单点绘制 | **只回写被点的那一个格子**（`nd.ch`），隐藏选区（`selNode.row = -1`） |
| `6` | 连续绘制 | 整片回写，隐藏选区 |
| `0/1/2/5/90` | 填充·剪切·粘贴·变换 | 整片回写 + 还原选区（**`90` 度时选区行列对调**） |
| `4/9/8/7` | 改变尺寸·标准化·剪切板导入·提交 | 整片回写 + 还原四至 + 还原变换矩阵 + `initArena3()` |

函数末尾**无条件** `mMap.isFistClick = true`。

PC 侧改法：把这段抽成 `restoreByAct(ActNode nd)`，返回一份**同分类**的反向快照。
撤销/重做只在末尾「压哪条栈、刷哪个按钮」上不同，所以共用它 —— 行为等价，只是去掉了原版的重复。

### 3. ⚠️ 两个必须记住的坑

#### 3.1 `ActNode.sel1/sel2` 必须是**引用**，不能深拷贝

原版 `ActNode` 构造器写的是 `sel1 = s1; sel2 = s2;`（**引用**），
而 `map = m` 也是引用（靠 `getMap()` 序列化成字符串当快照）。

PC 侧原本把 `sel1/sel2` 深拷贝成新 `selNode`。看着更安全，**但会把 90 度旋转的撤销/重做算反**：

`act == 90` 分支的公式是

```java
selRows = nd.sel2.col - nd.sel1.col + 1;   // ← 行列对调
selCols = nd.sel2.row - nd.sel1.row + 1;
```

它之所以「对调」是对的，是因为 `writeRot90()` 在旋转完成后**已经改过 `mMap.selNode2`**，
而 `nd.sel2` 是**引用**，读到的就是「旋转后」的角点 —— 对调之后才换得回旋转前的 行×列。

- 引用语义（原版）：undo 读「旋转后」的值 → 对调 → 正确；redo 同理 → 正确。
- 拷贝语义（PC 原状）：undo 读「旋转前」的值 → 再对调 → **2×3 与 3×2 互串**。

所以 PC 的 `ActNode` 已改为**与原文一致地存引用**（地图仍走深拷贝 —— 那才是等价的：
原版的 `getMap()` 序列化本身就把地图复制了一份）。

> 反过来说：**如果哪天有人把选区改回深拷贝，`restoreByAct()` 里那两行「对调」必须同步去掉。**
> 这条已经写进代码注释。

#### 3.2 `act == 3` 只回写一个格子 —— 这是**原版怪癖，照抄**

`myEditViewMap` 用 `@` 素材绘制时，会把全图其它 `@`/`+` 一并抹掉（「仓管员只能留存 1 位」）。
但 `act == 3`（单点绘制）的 undo **只回写被点的那个格子** —— 被抹掉的其它仓管员**不会**回来。

整片回写能「修好」这个现象，但那就不是 1:1 了。所以 PC 的 `ActNode.Act(int)`
也照原版对 `act == 3` 单独抓一个字符（`ch`），并新增 `restoreSingle(char[][])` 只回写那一格。
`Phase24EditUndoRedoTest.testUndoAct3RestoresOnlyTheClickedCell` 专门断言
「相邻格子**不**被还原」。

### 4. B/C/D —— 变换的三个缺失分支

原版 `myRotate(int n)`（`myEditView.java:1622-1691`）五个分支：

| `n` | 动作 | 入栈时机 / `act` |
|---|---|---|
| 0 | 180 度（原地对换循环 + 奇数行补一次左右对折） | 立即入栈，`act = 5` |
| 1 | 顺 90 度 → `myRot90(true)` | **`myRot90` 内部**在扩尺寸+询问之后入栈，`act = 90` |
| 2 | 逆 90 度 → `myRot90(false)` | 同上，`act = 90` |
| 3 | 水平翻转 | 立即入栈，`act = 5` |
| 4 | 垂直翻转 | 立即入栈，`act = 5` |

原版那句 `if (n < 1 || n > 2)` 的注释「1、2 情况特殊，需在调整四至且询问后，才可进入 undo 栈」
说的就是这个区别 —— **`act = 5` 与 `act = 90` 两条路**。

`myRot90()` 的流程（`myEditView.java:1299-1397`）：
算新尺寸 → `resetSize()` 扩四至 → 检查新区域是否压到已绘制内容 →
有则弹「部分原有绘制将被覆盖，确定吗？」（取消/确定）→ 写盘 → 更新 `selNode2`/`selRows`/`selCols` → 入栈。

`resetSize()`（`myEditView.java:1693-1708`）按 `selRows2/selCols2` 扩 `m_nMapBottom`/`m_nMapRight`，
超 `myMaps.m_nMaxRow/m_nMaxCol * 2 - 1` 时**夹取**，最后 `initArena2()`。

PC 侧新增：`myRot90(boolean)`、`writeRot90(boolean)`（原版在「覆盖提醒」的两个分支里各抄了一遍，这里合一）、
`resetSize()`（包私有，供测试）；`myRotate(int)` 改为五分支全量；
`showTransformMenu()` 改为 `HoloChoiceDialog.selectThenOk(...)`，选项文字照原版
`{"180度","90度(顺时针)","90度(逆时针)","水平翻转","垂直翻转"}`，默认第 1 项（`mWhich` 与原版一样
**与「提交到」共用**）。

顺带：`myEditView.java` 里最后一处 `JOptionPane` 就此消失（`Phase24` 有一条源码扫描断言钉住）。

### 5. ⚠️ 测试环境的坑：`pack()` 引发的异步 `componentResized`

`myEditView` 构造器最后会调 `UiWindow.applyPhoneSize()`，它要 `pack()` 才能定尺寸。
`pack()` 之后 Swing 会**异步**派发一次 `componentResized` →
`myEditViewMap.setArena()` → 它既**重算 `rtSize`/`rtF`…**，又**把 `selNode.row` 置 -1**。

踩到的现象：两条 `assertEquals` 之间 `selNode.row` 从 `1` 变成 `-1`，
于是 `restoreByAct()` 里 `nd.sel1.row >= 0` 的闸门不成立，选区还原分支被整段跳过 ——
「撤销后选区行数回到 2」失败成 `3`。

修法：构造完窗口后先排空 EDT（`SwingUtilities.invokeAndWait(() -> {})` 两次），
让这一下异步事件落到断言之前。已加到 `Phase17EditViewTest`（它本来就有这个 flake ——
`testLongPressOnSizeAreaSwitchesToSelectMode` 会间歇性失败）、`Phase4SecondaryViewTest`、
`Phase7SystemIntegrationTest`、`Phase24EditUndoRedoTest`。

### 6. 测试

新增 `Phase24EditUndoRedoTest`（16 条）：

- **`act` 分类**：`act 3` 只还原一格（含「相邻格不被还原」的怪癖断言）、`act 3` 的 redo、
  `act 6` 整片还原、`act 5` 整片还原且选区不动、`act 90` 选区行列对调、
  `act 4` 还原四至与 `mCurrentMatrix`、`act 4` 的 redo 回压撤销栈。
- **`myRotate` 五分支**：180 / 顺 90 / 逆 90 / 水平 / 垂直（用 2×3 的
  `# $ .` / `@ - *` 图案逐个核对结果矩阵），外加 `act` 取值（`5` vs `90`）、
  90 度后 `selRows/selCols` 对调，以及 90 度旋转的 undo→redo 往返。
- **`resetSize`**：装得下时不动、装不下时扩边界、超上限时夹取。
- **约定**：`myEditView.java` 源码里不得再出现 `JOptionPane`。

**当前基线：35 个用例类 / 307 个测试用例，全部通过。**
菜单保真度 `81 / strict 缺 4 / loose 缺 4` **不变** —— 本阶段不涉及菜单。

---

## 阶段 G ⑦ —— `BoxMan`（关卡集列表）的 10 项上下文菜单（已完成，2026-09-25）

### 1. 缺口

原版 `BoxMan.java:1444-1465` 的 `onCreateContextMenu()` + `onContextItemSelected()`
（1464-1712）是一整条**关卡集**维度的入口链，PC 侧此前**整条缺失**：`BoxManPC` 的
`levelTree` 只有左键单击进关卡集，没有任何右键/长按菜单，10 个动作一个都没有。

回原版逐行核对后，这条链还拖着 **6 个尚未移植的件**：

| 缺的件 | 原版出处 | 用在哪一项 |
| --- | --- | --- |
| `BoxMan.sel_File()` / `read_Plate()` | `BoxMan.java:1233-1330` | 添加关卡(文档) / 添加关卡(剪切板) |
| `export2_dialog.xml` 的选项框 | `BoxMan.java:1487-1510` | 导出... |
| `reName()` | `BoxMan.java:1819-1876` | 重命名... |
| `deleteThread` / `delete2Thread` | `BoxMan.java:1882-1955` | 删除 / 删除答案... |
| `get_uil_dialog.xml` 的载体 | `BoxMan.java:1633-1712` | 添加比赛关卡(sokoban.ws) |
| `MyThread` / `myJson` / `handler` | `BoxMan.java:1737-1805` | 同上 |

`myMaps.m_setName` 并不是「只声明未用」——`buildImportDialog()` / `buildExportDialog()`
早就在用它（`import_dialog3.xml` / `export_dialog3.xml` 的关卡集列表）。真正的缺口是
**菜单本身 + 上面 6 个件**。

### 2. 菜单本体

`BoxManPC` 新增：

- `CONTEXT_ITEMS`（10 项标题，逐字照抄）+ `buildContextMenu()` + `onContextItemSelected(int)`；
- `groupPos` / `childPos`（原版同名字段）+ `setContextPosition()`（测试用）；
- `positionOf(TreePath)`：由树路径反查 `{groupPos, childPos}`，**组别行返回 `null`**
  （原版 `if (childPos >= 0) expView.showContextMenu();`）；
- `createLevelTree()` 的 `MouseAdapter.mouseClicked` 加右键分支 → `showContextMenu(e, path)`。

可见性矩阵（`i` = `CONTEXT_ITEMS` 下标，菜单 id = `i + 1`）：

| 下标 | 标题 | 内置组（`groupPos <= 2`） | 扩展组（`groupPos > 2`） |
| --- | --- | --- | --- |
| 0 | 打开 | ✅ | ✅ |
| 1 | 导出... | ✅ | ✅ |
| 2 | 清理状态记录... | ✅ | ✅ |
| 3 | 删除答案... | ✅ | ✅ |
| 4 | 重命名... | ❌ | ✅ |
| 5 | 删除 | ❌ | ✅ |
| 6 | 添加关卡(文档)... | ❌ | ✅ |
| 7 | 添加关卡(剪切板)... | ❌ | ✅ |
| 8 | 添加比赛关卡(sokoban.ws) | ❌ | ✅ |
| 9 | 详细... | ✅ | ✅ |

载体照旧走 `compat/HoloPopupMenu`（Android 的上下文菜单与 ActionBar 溢出菜单是同一套
`popup_menu_holo_dark` 样式）。`buildContextMenu()` 末尾照抄原版的副作用：
`myMaps.m_Sets[0] = groupPos; myMaps.m_Sets[1] = childPos;`。

### 3. 逐项实现

| id | 项 | PC 实现 | 要点 |
| --- | --- | --- | --- |
| 1 | 打开 | `browLevels(g, c)` | 把原来的 `openSet(setId,title)` **改写成原版口径**：`curJi` 闸门 → 记 `m_Sets[0/1]` → `loadLevels()` → `m_lstMaps` 为空则 Toast「未找到关卡！」并放行 `curJi` → 开 `myGridView`。单击条目走的是同一个方法（原版 `onChildClick → browLevels`）。 |
| 2 | 导出... | `exportOneSet()` / `buildExportSetDialog()` | 单关卡集导出，选项框是 `export2_dialog.xml`（**不是**主菜单的 `export_dialog3.xml`）：6dp 色条 → 「选项：」+16dp+含答案+10dp+答案含备注 → 6dp 色条 → 80dp 占位 + 覆盖同名文档 → 6dp 色条。初值「含答案=否 / 答案含备注=否 / 覆盖同名文档=是」。⚠️ 照抄原版那个 `else` 怪癖：`ex_comment` 勾上时只联动 `ex_lurd`、**不写** `myMaps.isComment`，只有取消勾选才写（和 `sel_Set2()` 里「总是写」的版本不同）。 |
| 3 | 清理状态记录... | `clearSetState()` → `clearSetStateNow(id)` | `AlertDialog("状态清理", "本集关卡保存的全部状态将被清理，确认吗？")` → `clear_S(id)` + Toast「清理完毕！」。 |
| 4 | 删除答案... | `deleteSetAnswers()` → `deleteSetAnswersNow(id)` | `AlertDialog("提醒", "本集关卡的答案将全部删除，\n请做好备份！\n确定要删除答案吗？")` → `ProgressDialog("答案删除中...")` + `delete2Thread` → `del_T_Ans(id)`。 |
| 5 | 重命名... | `reName()` / `applyRename(nd, input)` | 输入框预填当前名。空名 → Toast「名称不能为空！」并**留在原地**；重名 → Toast「此名称已经存在！」并留在原地；否则 `set_T_T()` + 改内存节点 + 刷新。回车与「确定」等价（原版 `setOnKeyListener` 的 `KEYCODE_ENTER`）。 |
| 6 | 删除 | `deleteSet()` → `deleteSetNow(nd)` | `AlertDialog("提醒", "删除关卡集，确定吗？\n（" + title + ")")` —— 括号照抄原版（全角 `（` + 半角 `)`）→ `ProgressDialog("删除中...")` + `deleteThread` → `del_T(id)` + `mSets3.remove`。 |
| 7 | 添加关卡(文档)... | `addLevelsFromDoc()` → `sel_File()` | `m_Set_id = mSets3.get(childPos).id` 后走 `import_dialog.xml`（XSB/Lurd + 编码单选 + 仅一个关卡时自动打开）→ `imPort_Sets(list, TYPE_FILE)`。 |
| 8 | 添加关卡(剪切板)... | `addLevelsFromClip()` → `read_Plate()` | 同上，`im_plate` 文本框 + `TYPE_CLIPBOARD`。 |
| 9 | 添加比赛关卡(sokoban.ws) | `importMatchLevels()` → `UrlInputDialog` → `submitCompetition()` → `startCompetitionDownload()` | 见 §4。 |
| 10 | 详细... | `showSetAbout()` / `setAboutMessage(id)` | `get_Set(id)` + `count_Sovled(id) + "/" + count_Level(id)` → `myAbout1`。 |

### 4. 「添加比赛关卡」这一项

原版是 `get_uil_dialog.xml`（「网站: 」+「期号: 」两栏，**两个框都带
`android:digits="0123456789"`** —— 连 URL 框也只收数字，这是原版怪癖，照抄不改）
→ `MyThread` 里 `HttpGet(myMaps.uil + "api/competition/" + url_Num)`
→ `myJson()` 解析 → `handler` 弹「比赛信息」/「错误」。

PC 侧原来的 `UrlInputDialog` 是**自造件**（单个 `URL:` 框 + 进度条，`res/layout` 里没有对应
文件），且生产代码里根本没接线（只有快照工具与 `Phase5DialogTest` 用）。本次**按原版重写**：

- `UrlInputDialog(Frame owner, long setId, OnSubmit onSubmit)` —— 标题「导入比赛关卡」，
  两个 `numberField`（220dp、`#242424`、16sp、`selectAllOnFocus`），期号框带
  15sp 的 hint「默认最新一期的比赛」（Swing 没有 `android:hint`，用空文本时自绘淡色文字
  的 `HintField` 表达，同时挂一份 `toolTipText`）。
- `BoxManPC.COMPETITION_URL = "api/competition/"`（原版 `url` 字段）。
- `computeUrlNum(num)`：`n > 0 ? "?id=" + n : ""`，解析失败也取 `""`。
- `normalizeUil(uil)`：trim + 末尾补 `'/'`。⚠️ 原版对空串会 `charAt(-1)` 抛
  `StringIndexOutOfBoundsException`；PC 端把空串补成 `"/"`，之后 URL 解析失败会落到
  「网络错误：000」，不崩。
- `prepareCompetition(setId, uil, numText)`：`url_Num` / `myMaps.uil` / `myMaps.m_Set_id`
  三件事（拆出来便于测试，不发请求）。
- `startCompetitionDownload()`：`ProgressDialog("下载中...")` + `SwingWorker` →
  `fetchCompetition()`（`HttpURLConnection`，源级别 Java 8 用不了 `java.net.http`）。
  HTTP 200 → `parseCompetitionJson()`；其它状态码 → `"网络错误：" + code`；异常 →
  `"网络错误：000"`。`done()` 里关框 + `refreshTree()` + 结果框
  （`ok ? "比赛信息" : "错误"`，即原版 `Message.what == 1 / 0`）。
- `parseCompetitionJson(lvls)` = 原版 `myJson()`：`id`/`begin`/`end` + `main`/`extra`/
  `extra2`/`extra3` 四个关卡位 → `add_L(myMaps.m_Set_id, nd)`，并记
  `mMatchNo = "第N期比赛"` / `mMatchDate1` / `mMatchDate2`。
  ⚠️ 原版只 `catch (ParseException)`，而 `obj.get("id")` 取不到时是 `null` → NPE；
  PC 端一并吞掉（统一退化成默认提示语，正常接口下不可见）。

### 5. 顺带修掉的三个既有问题

1. **`addButton(text, null)` 是个点不动的死按钮**。原版 `AlertDialog` 的按钮在
   `onClick()` 里**无条件先 `dismiss()`**，监听器只是可选的附加动作 —— 所以
   `.setNegativeButton("取消", null)` 是「点了就关」。`HoloAlertDialog.addButton`
   原先在 `action == null` 时什么都不做，`BoxManPC` 的两处「取消」和
   `myGameView` 的一处「取消」全是死按钮。已改成 null 分支兜底 `dispose()`。
2. **`myGridView.buildImportOptions()` 的文案错了**：`import_dialog.xml` 里
   `cb_xsb` / `cb_lurd` 写的是 **「XSB」/「Lurd」**（「关卡 / 答案」是
   `import_dialog3.xml` 的 `im_xsb` / `im_lurd`，属于另一套）。原先写成了「关卡 / 答案」，
   且用 `check32(..., 96)` 固定宽度（XML 是 `wrap_content`）→ 已改为
   `HoloContent.wrapCheck("XSB"/"Lurd")`。「仅有一个关卡时，自动打开」同理（原文案
   漏了顿号、宽度写死 288）。
3. **「添加关卡」落库目标错了**：原版 `imPort_Sets()` 落的是 `myMaps.m_Set_id`；
   `myGridView.importDocFile()` 原先走「按文档名 → `find_Set`，没有就在扩展组新建同名集」，
   会把关卡导到一个**新建的集**里而不是当前集。已新增
   `BoxManPC.importLevelFileInto(File, long, boolean)` /
   `importLevelTextInto(String, long, boolean)`，`myGridView` 改用 `mSetId` 落库。
   （导入本体仍是 PC 已有的直接解析路径，不是原版的 `mySplitLevelsFragment` ——
   这是一处**保留的 PC 简化**：`BoxManPC.sel_File()`/`read_Plate()` 走的是原版的
   `imPort_Sets` + `mySplitLevelsFragment`（带进度框与统计），`myGridView` 那两个则
   用直接解析 + 自己的 `afterImport()` 刷新与 Toast。）

### 6. 测试接缝：`dialogShower`

这 10 项里有 9 项「点了就开窗」，而且开的都是**模态**框 —— 直接调用会把用例挂死
（这正是 `TEST_NOTES.md` 里记着的坑）。为此 `BoxManPC` 加了一个字段：

```java
/** 「把对话框显示出来」这一步的出口 —— 默认就是模态的 setVisible(true)。 */
java.util.function.Consumer<JDialog> dialogShower = dlg -> dlg.setVisible(true);
```

所有「会开窗」的动作方法都经过它；测试把它换成「只记录、不显示」，就能走完整条分支而不卡住：

```java
private ShownDialog captureShownDialog() {
    final ShownDialog holder = new ShownDialog();
    app.dialogShower = dlg -> holder.last = dlg;
    return holder;
}
```

配合「把纯逻辑拆出来」的老办法（`clearSetStateNow` / `deleteSetAnswersNow` /
`deleteSetNow` / `applyRename` / `prepareCompetition` / `parseCompetitionJson` /
`setAboutMessage` / `loadLevels`），10 个分支全部可测。

### 7. 测试

新增 `Phase25BoxManContextMenuTest`（**47 条**）：

- **菜单本身**：10 项标题与顺序逐字一致、`itemCount == 10`、载体是 `HoloPopupMenu.Row`、
  扩展组 10 项全可见、内置组（0/1/2 三组）各隐藏那 5 项、`m_Sets[0]/[1]` 记住位置；
- **case 1**：`loadLevels` 按位置装集（`sFile` / `m_Set_id` / `m_lstMaps`）、越界不改
  `sFile`、`browLevels` 的 `curJi` 重入闸门；
- **case 2**：`export2_dialog` 三个复选框的初值（否/否/是）、`isLurd` 复位、`sFile` 指向被点中的集、
  越界不搭框、「导出...」真能弹出该框；
- **case 3/4**：`clear_S` 清掉状态但不动答案、`del_T_Ans` 把 `L_Solved` 清零但不动状态、
  两者都先弹确认框（`HoloConfirmDialog`）；
- **case 5**：空名 / 重名被拒且库里不变、正常改名落库 + 内存节点 + 列表刷新、
  弹框预填当前名、越界不弹框；
- **case 6**：`del_T` + 从 `mSets3` 移除、先弹确认框；
- **case 7/8**：`m_Set_id` 指向被点中的集、弹出 `HoloChoiceDialog`、
  `import_dialog.xml` 的 XSB/Lurd/自动 初值、`import_dialog2.xml` 没有编码单选、
  `m_XSB.setChecked(true)` 顶起 `myMaps.isXSB`、无文档时返回 `null`；
- **case 9**：弹出 `UrlInputDialog`、`api/competition/`、`computeUrlNum` 六种输入、
  `normalizeUil` 五种输入、`prepareCompetition` 三个状态量、
  `parseCompetitionJson` 的「加载成功」/「尚未开赛」/「垃圾输入」三条路径
  （含 `mMatchNo` / `mMatchDate1/2` 与落库关卡数）、两个输入框的 digits 过滤与 15sp hint、
  `fireSubmit` 回传 `setId`；
- **case 10**：`0/1` 正文、`get_Set` 装进 `J_Title/J_Author/J_Comment`、弹出 `myAbout1`；
- **约定锁**（源码扫描）：`BoxManPC` 不得出现 `new JMenuItem(`、必须 `HoloPopupMenu.create()`、
  `HoloAlertDialog.addButton` 的 null 分支兜底 `dispose()`、
  `myGridView` 用 `wrapCheck("XSB"/"Lurd")` 且不再有 `check32("关卡"/"答案")`、
  `myGridView` 按 `m_SetId` 落库。

⚠️ 用例里踩到并已记入 `TEST_NOTES.md` 的两个坑：

- **`myMaps.m_Sets[0]` 是全局静态**（「上次所在的关卡组」）。本类会刻意改它（右键菜单会
  写入 `m_Sets[0]`，改名用例为了让扩展组展开也设成 3），`@After` 里**必须还回 0**，
  否则同 JVM 里排在后面的 `Phase3NavigationTest`（断言默认展开第 0 组）会失败。
- **`dialog_uil` 也是 digits-only**：原版把 URL 输入框也限成纯数字，测试不能拿
  `"https://x.cn"` 当输入（会被过滤成空串），要用 `"12345"`。

### 8. 与 Android 的差异（全部是保留的 PC 简化）

| 处 | 原版 | PC |
| --- | --- | --- |
| 菜单触发 | `OnItemLongClickListener`（长按） | 右键（桌面惯例；`myGridView` 的 14 项菜单已是右键） |
| 异步 | `Thread` + `Handler` | `SwingWorker` + `done()` |
| 进度框 | `android.app.ProgressDialog` | `compat/HoloProgressDialog` |
| HTTP | `HttpClient` / `HttpGet` | `HttpURLConnection`（源级别 Java 8） |
| 比赛关卡解析 | `json-simple` | `json-simple`（依赖同源，可直译） |
| `myJson` 异常 | 只 `catch (ParseException)` | 一并吞掉 `null` 字段造成的 NPE |

**当前基线：36 个用例类 / 354 个测试用例，全部通过。**
菜单保真度 `81 / strict 缺 4 / loose 缺 4` **不变** —— `menu_fidelity.py` 只扫
`res/menu/*.xml`，扫不到 `onCreateContextMenu()`（见本文档前面的说明）。

---

## 阶段 G ① —— 图片列表链路：`myFileExplorerActivity` / `myPicListViewAdapter` /
## `myPicListView` + `gif.xml`「制作」（已完成，2026-09-25）

### 1. 这一项原本被记成「最零散」，实际体量与判断

审计里写的是「补三个未移植类」，动手前先量了一遍行数，发现之前的估计有两处错：

| 类 | 原版 | PC（移植前） | 说明 |
|---|---|---|---|
| `myFileExplorerActivity` | 224 | — | 真·未移植，本次新写 |
| `myPicListViewAdapter` | 183 | — | 真·未移植，本次新写 |
| `myPicListView` | 258 | 132（**自造件**） | 不是「没移植」而是「移植歪了」，本次重写 |
| `myGifMakeFragment` | 1471 | — | **早已被 `myGifMakeDialog`(347) + `gifencoder/`(3 个类) 覆盖**，不需要再移植 |

⚠️ 两个纠正：
- **`myGifMakeFragment` 不是缺口。** 它是个 `DialogFragment`，`onCreateDialog()`
  返回的是一个 `ProgressDialog`（「合成中...」），GIF 编码器在 PC 侧已拆到
  `gifencoder/` 包。原版 1471 行里绝大部分是 `AsyncGifMakeTask` 的逐帧绘制。
- **`res/menu/gif.xml` 是孤儿资源**：全仓库 `grep "R.menu.gif"` **零命中**，
  原版从来没有 inflate 过它。真正的「制作」是 `myExport.java:423` 那个
  「帧间隔」对话框的 `setPositiveButton("制作", ...)`。所以 `gif.xml` 那一项
  在 PC 侧的落点是 `myGifMakeDialog` 的按钮文案 —— 此前写成「确定」。

### 2. `myFileExplorerActivity`（新写，331 行）

| 原版 | PC |
|---|---|
| `setContentView(R.layout.line_list)` | 顶行「当前位置: 」+ 路径 label，下面 `JList` |
| `setTitle("自定义位置")` | `actionBar.setBarTitle("自定义位置")` |
| `setDisplayHomeAsUpEnabled(true)` | `setUpEnabled(true, this::finish)` |
| `filelist.xml` 两项（`always`） | `addBarAction("上一级")` / `addBarAction("完成")` |
| `onKeyDown(BACK)` | ESC：`getRootPane()` 的 input/action map |
| `setResult(999)` | `OnPathPicked` 回调（PC 没有 `startActivityForResult`） |

- 列表：**目录优先 + 按名小写排序**，文件只留 `jpg/bmp/png`；
  ⚠️ 原版判断是 `(dot > -1) && (dot < fn.length())` —— **没有扩展名的文件会被
  无条件列出**（不参与后缀过滤），照抄。
- 根目录：`myPathList[2]` 非空且存在 → 它，否则 `sRoot`；不存在 → Toast
  「系统错误，无法执行该操作！」+ `finish()`。
- BACK：路径栏为空 → 弹「提醒 / 退出浏览，确定吗？（取消/确定）」；否则 → 上一级。
- `myParent()`：`canonical != sRoot` 才上移。
- **完成**：`myPathList[m_Sets[36]] = 路径 + '/'`（原版把 `if (!str.isEmpty())`
  注释掉了，空路径也会写成 `"/"`，照抄）+ 回调 + `finish()`。

### 3. 两处**必要的 PC 适配**（照抄会在 Windows 上失效）

1. **路径分隔符**：`myMaps.sRoot` 是 `user.home + "/.boxman"`（正斜杠、无尾斜杠），
   `File.getCanonicalPath()` 在 Windows 上返回反斜杠 → 原版的
   `canonical.equals(sRoot)` 与 `canonical.replace(sRoot, "")` **都会全部落空**，
   表现为「上一级能一路退到盘符」「路径栏永远显示绝对路径」。
   改成**两边都取 canonical** 再比较 / 去前缀。
2. **`sRoot` 无尾斜杠 + `myPathList[0] == ""`**：原版 `sRoot` 是 `"/推箱快手/"`
   （自带尾斜杠），PC 的不是 → `sRoot + ""` 会拼出 `.../.boxman` + `m.png`
   = `.../.boxmanm.png`。新增 **`myMaps.picDir()`** 统一处理，已接到
   `myPicListViewAdapter` / `myPicListView.reloadList()` / `BoxManPC.openRecognition()`。
   （这个缺陷在移植前就存在，只是没被触发过。）

### 4. `myPicListView` 重写（132 → 289 行）

删掉 PC 自造的底部按钮栏（「选择本地文件...」/「关闭」）与 `JFileChooser`
（**至此全项目 0 处 `JFileChooser`**），改为：

| 原版 | PC |
|---|---|
| `GridView` `numColumns="auto_fit"` `columnWidth="350px"` | 折算 **3 列**（350px ÷ density 3.4 ≈ 103dp；370 ÷ 103 ≈ 3），缩略图框 103×147 |
| `verticalSpacing="10dip"` / `horizontalSpacing="5px"` | `GridLayout(0, 3, 1, 10)` |
| `setTitle(myPathList[m_Sets[36]])` | `actionBar.setBarTitle(...)` |
| `piclist.xml` 位置（`always`） | `addBarAction("位置", ...)` |
| `onCreateContextMenu` 加载 / 删除 | `HoloPopupMenu`，**右键**触发（PC 的长按映射） |
| 「图片位置」5 项单选 + 修改/打开/取消 | `HoloChoiceDialog` + `addButton("取消"/"修改"/"打开")` |
| `onActivityResult(999)` | `onPathPicked(String)` |

- 「修改」闸门：`m_Sets[36]` 在 **2..4** 才开文件浏览器，否则 Toast「这个位置不能修改！」
- 「打开」：位置为空 → 补成 `"/"`；异常 → Toast「错误的位置！」
- 单击缩略图：`loadEDPic` → 宽高都 > 200 才开 `myRecogView`，否则 Toast
  「图片尺寸太小或不能打开！」；另有 2000ms 防连点闸门。

### 5. `myPicListViewAdapter`（新写，111 行）

原版是 `BaseAdapter` + `AsyncTask` 异步解码 + `SparseArray` 缓存；PC 改成
「按需解码 + `Map` 缓存 + 丢引用交给 GC」（没有 `Bitmap.recycle()`）。
⚠️ 原版 `getThumbnail` 用 `createScaledBitmap(350, 500, false)` **强制拉伸**不保比例，
PC 改成**等比缩放后居中** —— 拉伸会把关卡截图压扁，与原版 `ImageView` 的
`CENTER_INSIDE` 最终观感不一致，故不照抄拉伸。失败时返回 1×1 占位图（与原版一致）。

### 6. `myGifMakeDialog`：确定 → 制作

原版 `myExport.java:409-423`：标题「帧间隔」，`setNegativeButton("取消", null)`
+ `setPositiveButton("制作", ...)`。PC 此前写「确定」，已改回「制作」。
（`res/menu/gif.xml` 标注为孤儿资源，`menu_fidelity.py` 的映射表也已同步。）

### 7. 测试

新增 `Phase26PicListAndFileExplorerTest`（**25 条**）：菜单标题/顺序/无 ⋮、
目录优先 + 图片后缀过滤、进目录、点文件无反应、上一级到根停住、完成写回
`myPathList` + 回调、BACK 两条分支、适配器路径拼接与等比缩放与 1×1 占位、
「位置」对话框 5 项与下标夹取、「修改」闸门三种情况、「打开」空路径补 `/`、
上下文菜单标题与真删除文件、小图 Toast 闸门、源码扫描锁（无 `new JFileChooser(`、
`addButton("制作"`、`myActionBar` + `addBarAction`）。

两个新踩的测试坑：
- **`HoloAlertDialog` 不设 AWT 的 `title`**（标题是自绘的）→ `getTitle()` 返回
  空串，别拿它断言标题；改用 `instanceof` 判定对话框类型。
- **源码扫描要写精确一点**：类注释里提到 `JFileChooser` 也会被 `contains` 命中，
  断言要写成 `new JFileChooser(`。

### 8. Android ↔ PC 差异表

| 差异 | 处理 |
|---|---|
| `startActivityForResult` / `setResult(999)` | `OnPathPicked` 回调 |
| `AsyncTask` 解码缩略图 | 按需同步解码 + `Map` 缓存 |
| `Bitmap.recycle()` 防 OOM | 丢引用交给 GC |
| `GridView` 的 `auto_fit` / `columnWidth` | 折算成 3 列 × 103×147 |
| `sRoot` 尾斜杠 | `myMaps.picDir()` 兜底 |
| Windows 反斜杠 | 两边都取 canonical |

**当前基线：37 个用例类 / 379 个测试用例，全部通过。**
**菜单保真度：81 / strict 缺 0 / loose 缺 0 —— 阶段 G ① 把最后 4 项全部收口。**

---

## 阶段 G ③ —— `mySolutionBrow` 整体重做（已完成，2026-09-25）

### 1. 原来的差距有多大

原版是 **Activity + ActionBar + `ExpandableListView`**（304 行）；PC 此前是
**自造的模态 `JDialog` + 底部按钮栏 + `JOptionPane`**（107 行）。差距不只是行数：

| 维度 | 原版 | PC（重写前） | 重写后 |
|---|---|---|---|
| 载体 | Activity（`JFrame`） | 模态 `JDialog` | ✅ `JFrame` |
| 标题栏 | ActionBar「相似关卡」+ 返回折角 | 无 | ✅ `myActionBar` |
| 列表 | 两级：组「答案」+ 子项 | 平铺 `JList` | ✅ 第 0 项是组头 |
| 行内容 | 主行 `inf` + 次行 `time`（右对齐） | 「答案 N: 步数=…推数=…」 | ✅ `inf` / `time` |
| 背景 | `#ff004040` | 默认 | ✅ `#004040` |
| 选中色 | `listSelector #ff0064aa` | 默认 | ✅ `#0064aa` |
| 导出路径 | 弹「剪切板：Lurd」对话框（**可编辑**）→ 确定才写 | 直接写剪切板 + `JOptionPane` | ✅ 走对话框 |
| 底部按钮栏 | 无 | 「复制到剪贴板」/「关闭」 | ✅ 删掉 |

### 2. 列表结构怎么落的

`ExpandableListView` 在 PC 上没有等价控件，落法是 `JList<Object>`：
第 0 项放组头字符串「答案」，之后依次是 `myMaps.mState2` 的条目；
渲染器按类型分两支（组头 = `s_groups` 样式；子项 = `s_child` 的主行 + 次行）。
`onCreate` 末尾的 `expandGroup(0)` = 组头恒在场。

点击 / 长按取到的下标要**减掉组头那一行**才是原版的 `c_Pos`；
组头（下标 0）与空白处（-1）都置 `c_Pos = -1`，与原版
`onItemLongClick` 里 `if (c_Pos != -1)` 的闸门一致。

### 3. 上下文菜单与导出

原版 `onCreateContextMenu` **只有 1 项**「导出到剪切板: Lurd」（case 3 / case 4
在原版里被整段注释掉）。选中后 `myMaps.m_State = load_State(m_Sel_id)`，
再弹标题为「剪切板：Lurd」的对话框，里面是一个 `EditText` 预填 `m_State.ans`，
**取消 / 确定**，只有点「确定」才 `myMaps.saveClipper(...)`。
PC 用 `HoloAlertDialog` + 可编辑 `JTextArea` 还原（不再直接写剪切板）。

### 4. 三处在原版里就不可达的代码，不移植

`writeStateFile()`、`myExport2()`（导出 XSB+Lurd）、`mDlg`（「文档已存在，覆写吗？」）
**在原版里只被注释掉的 case 3 / case 4 调用**，`mDlg.show()` 同样在注释块里。
按「先确认原版是不是注释掉了」的纪律，这三处**不移植**（移植了也是死代码）。
审计原先把它们记成「覆写对话框」差距，现已订正。

### 5. 测试

新增 `Phase27SolutionBrowTest`（**14 条**）：ActionBar 标题 / 折角 / 无 ⋮、
模型结构（组头 + 2 条）、子项下标换算与 `m_Sel_id`、组头与空白处不选中、
上下文菜单只有 1 项且走 `HoloPopupMenu`、未选中时不弹框、选中后弹框且文本框可编辑、
预填答案、改完点「确定」写进剪切板（`myMaps.loadClipper()` 回读）、
`m_State == null` 不炸、源码扫描（无 `JOptionPane`、无自造按钮栏、走 Holo 载体）。

⚠️ 踩了自己刚写进 `TEST_NOTES` 的那个坑：**源码扫描命中了类注释里提到的
`JOptionPane`**。修法是扫描前先剥注释：
```java
src = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
```

### 6. Android ↔ PC 差异表

| 差异 | 处理 |
|---|---|
| `ExpandableListView` 两级列表 | `JList<Object>`：第 0 项当组头，恒展开 |
| 平铺下标 vs 子项下标 | `c_Pos = index - 1` |
| `EditText` 预填答案 | 可编辑 `JTextArea` |
| `saveClipper` | `myMaps.saveClipper`（已存在） |
| 原版不可达的三处 | 不移植（见 §4） |

**当前基线：38 个用例类 / 392 个测试用例，全部通过。**

---

## 阶段 G ⑤ —— 普通按钮统一成 Holo 深色样式（已完成，2026-09-25）

### 1. 原版的按钮到底是什么样

`AppBaseTheme` 的父主题是 `android:Theme.Holo`（**深色**），所以布局里凡是没有显式
`android:background` 的 `<Button>` 都走 framework 的 `Widget.Holo.Button`：

```xml
<style name="Widget.Holo.Button" parent="Widget.Button">
    <item name="background">@drawable/btn_default_holo_dark</item>
    <item name="textAppearance">?attr/textAppearanceMedium</item>
    <item name="textColor">@color/primary_text_holo_dark</item>   <!-- #FFF3F3F3 -->
    <item name="minHeight">48dip</item>
    <item name="minWidth">64dip</item>
</style>
```

`btn_default_holo_dark` 是一个 **9-patch 状态图**，只有四态：
`normal / pressed / focused / disabled`（+ `disabled_focused`）——
**没有 hover 态**（触摸端没有鼠标悬停）。

⚠️ 别把 `res/drawable/shape_btn.xml`（`#44ffffff` + 1dp 圆角）当成按钮底图 ——
`grep -rln "shape_btn" res/layout/` 是 **0 命中**，它是孤儿资源。
`values/style.xml` 里也**没有任何按钮 style**。

### 2. 只有 4 个布局里有 `<Button>`

`grep -ln "<Button" res/layout/*.xml` → `action_manage.xml` / `export_view.xml` /
`recog_view.xml` / `submit.xml`。其中：

| 布局 | 按钮 | PC 对应 |
|---|---|---|
| `action_manage.xml` | 加载 / 存入 / 清空 / 暂存 / 执行（52/52/48/48/60dp × `wrap_content`，`textSize 14sp`、`padding 2dp`、`margin 2dp`） | `myActGMView.makeButton`（5 个） |
| `export_view.xml` | `bt_ex_OK`「**导出**」（100dp × `wrap_content`） | `myExport.bt_OK` |
| `submit.xml` | 返回 / 提交（`wrap_content`，`textSize 10pt`、`paddingLeft 10dp`、`paddingTop/Bottom 5dp`） | `mySubmit.btCancel / btOK` |
| `recog_view.xml` | 9 个元素钮，**每个都显式写了 `android:background="#ff334455"`** | 不适用（不是平台按钮，PC 早已自绘，见阶段 D-2） |

`help.xml` / `about.xml` **一个 `<Button>` 都没有** —— 原版这两个 Activity 只能按 BACK 退出。
所以 PC 的 `myAbout` / `myAbout1` / `myAbout2` / `Help` 底栏按钮是 **PC 自造**的
（而且这几个窗口没有套原版深色主题），不在本阶段范围，见 §5。

### 3. 自绘：`compat/HoloButton`

⚠️ **必须自绘**：`JButton.setBackground()` 会被 FlatLaf 覆盖（同 `myRecogView` 的元素钮，
见 `RENDER_NOTES.md`）。而且 `paintComponent` 里**不调 `super.paintComponent`** ——
文字也自己画，免得 FlatLaf / `BasicButtonUI` 对禁用态前景色各有一套处理
（`BasicButtonUI.paintText` 的禁用分支用的是 `getBackground().brighter()/darker()`）。

配色全部实测自 `drawable-xxhdpi/btn_default_{normal,pressed,disabled}_holo_dark.9.png`
（80×98，density 3.4），**保留 alpha** 让 Swing 按 SrcOver 合成 —— 与原版
「半透明底叠在窗口背景上」的语义一致：

| 角色 | normal | pressed | disabled |
|---|---|---|---|
| 主体 | `(41,47,52,189)` | `(240,240,240,89)` | `(153,153,153,39)` |
| 顶棱 | `(82,87,91,199)` | `(249,249,249,145)` | `(150,150,150,128)` |
| 外描边 | `(32,32,32,191)` | `(208,208,208,97)` | `(116,116,116,128)` |
| 底缘 | `(29,29,29,213)` | `(122,122,122,131)` | `(108,108,108,128)` |
| 文字 | `#F3F3F3` | `#F3F3F3` | `#4C4C4C` |

几何：9-patch 的**透明外缘**（左右 11px、上 12px、下 8px ÷ 3.4 ≈ 3 / 4 / 2dp）
必须保留 —— 按钮图形不铺满 View 边界，所以相邻按钮看起来比「边界间距」更宽。

尺寸语义：`minWidth 64dp` / `minHeight 48dp` **只在 `wrap_content` 时生效**；
布局写了确定值（`layout_width="52dp"`）时走 `MeasureSpec.EXACTLY`，不受下限约束。
Swing 侧的对应判据就是「调用方有没有显式 `setPreferredSize(...)`」，
所以 `HoloButton.getPreferredSize()` 先判 `isPreferredSizeSet()`。

### 4. 顺带订正的两个偏差

| 项 | 改前 | 改后（= 原版） |
|---|---|---|
| `myActGMView` 按钮高度 | 36dp | **48dp**（`Widget.Holo.Button` 的 `minHeight`） |
| `myExport` 按钮文案 | 「执行导出」 | **「导出」**（`export_view.xml` 的 `android:text`） |

`myExport` 的底栏底色也一并取成 `#363636` —— 原版 `bt_ex_OK` 内嵌在
「导出到文档」那一行里，该行就是 `android:background="#363636"`；
Holo 按钮的半透明底必须有正确的合成背景才不会发灰。

### 5. 刻意没改的地方（都不是「普通按钮」）

| 位置 | 为什么不动 |
|---|---|
| `HoloAlertDialog.addButton` | `AlertDialog` 的底栏按钮是 `Widget.Holo.Button.Borderless`（`selectableItemBackground`），**本来就无边框**，只有按压/聚焦的白色水波 —— 与 `btn_default_holo_dark` 是两回事。已加测试锁住它不会被「统一」掉 |
| `recog_view.xml` 的 9 个元素钮 | 显式 `#ff334455`，不是平台按钮；PC 早已自绘（阶段 D-2） |
| `myAbout` / `myAbout1` / `myAbout2` / `Help` 的底栏按钮 | 原版 `help.xml` / `about.xml` **没有按钮**，PC 自造；且这几个窗口**没套原版深色主题**（黑底），单把按钮改成 Holo 反而更不协调 |
| `myGameView.showSetup2Dialog` 的「确定」 | 原版这里是 `AlertDialog.setMultiChoiceItems()`，PC 自造成一个浅色 `JDialog`。整个对话框都要重做，不在本阶段 |

> **🆕 新开条目（留给后续阶段）**：`myAbout` / `myAbout1` / `myAbout2` / `Help` /
> `myExport` 这几个窗口没有套原版 `Theme.Holo` 的深色 `windowBackground`（纯黑），
> 连带 `myGameView.showSetup2Dialog` 应改回 `HoloAlertDialog` + 多选列表。
> 这是一条**窗口主题 / 对话框载体**的线，比按钮样式大。

### 6. 测试

新增 `Phase28HoloButtonTest`（**20 条**）：12 个配色常量与 AOSP 实测 ARGB 逐一对齐、
文字色（`#F3F3F3` / `#4C4C4C`）、`minWidth/minHeight`、9-patch 透明外缘（四边逐行逐列）、
正常态五段结构（描边/顶棱/主体/底缘/描边）的**合成色**断言、
黑底上主体比底亮、按下态变亮、禁用态换图、文字自己画且墨迹重心居中、
`<html>…<br>…</html>` 拆行、`wrap_content` 被下限钳制 / 显式尺寸不被钳制、
工厂方法的 `textSize` 与 `padding`、`myActGMView` 五个按钮（含 52/52/48/48/60 × 48）、
`mySubmit` 两个按钮、`myExport` 的按钮文案与底栏底色、
`HoloAlertDialog` 底栏按钮**不是** `HoloButton`、`HoloContent.button` 委托、
以及三个窗口的源码扫描（不再有裸 `new JButton(`）。

⚠️ 写测试时踩到的两个坑：
1. **`Component.paint(g)` 不会把坐标平移到自己原点** —— 直接 `b.setBounds(8,8,…); b.paint(g)`
   会把按钮画在 (0,0)。要么 `paintAll`，要么每个组件渲染进自己的 `BufferedImage`。
2. **别断言「禁用态比正常态暗」**：禁用主体是浅灰 `@15%` alpha，叠在 `#363636` 上
   反而比正常态（深灰 `@74%`）**亮**；只有叠在黑底上才更暗。差别要看合成色本身。

### 7. Android ↔ PC 差异表

| 差异 | 处理 |
|---|---|
| 9-patch 位图（80×98 @3.4x） | 自绘等价几何；`px ÷ 3.4` 取整成 dp，1dp = 1px |
| `state_focused` / `state_window_focused` | **不实现** —— 原版靠 d-pad/键盘聚焦，PC 上无对应语义；Swing 焦点框也已关掉（`setFocusPainted(false)`） |
| hover 态 | 原版**没有**，PC 也不加（不引入 PC 专属交互） |
| `layout_margin="2dp"` | PC 侧仍是 `Box.createHorizontalStrut(4)`，**未还原 2dp margin** —— 间距偏小，属「布局」条目 |
| `minWidth/minHeight` | 只在未显式 `setPreferredSize` 时生效（对应 `AT_MOST` vs `EXACTLY`） |

**当前基线：40 个用例类 / 428 个测试用例，全部通过**（本行写于阶段 G ⑤，已由阶段 H 更新）。

---

## 阶段 H —— 「关卡编辑器」版面还原：顶行信息栏 + 底栏（已完成，2026-09-25）

原版参照：`screenshot_20260925_125127_my.boxman.jpg`（1260×2844，density 3.4051）。
该截图是 **`FLAG_FULLSCREEN` + `hideSystemUI()` 的沉浸态**：应用内容 = y 139..2843，
即 **370 × 794.3dp**（不是普通界面那种扣掉导航栏的 780dp，见文末「已知残差」）。

按 1dp = 1px 量出来的两处**结构性**偏差，都改掉了。

### 1. 顶行信息栏高 39dp，应为 30dp

**根因：原版这段几何写在「设备像素」里，PC 侧写成了 dp。**

```java
// 原版 BoxMan.onCreate()：m_nWinWidth = metric.widthPixels  ← 设备像素（1260）
obj_Width = myMaps.m_nWinWidth / 10;                      // 126
if (obj_Width > m_PicWidth*2) obj_Width = m_PicWidth*2;   // 夹到 100 设备像素
m_nArenaTop = obj_Width + 2;                              // 102 设备像素 = 29.96dp
```

PC 端 `myMaps.m_nWinWidth` 是 **dp**（370），直接 `/10` 得 37 —— 而 `m_PicWidth*2 = 100`
这个上限**永远够不着**，于是顶栏变成 39dp（比原版高 9dp），5 个素材槽也各宽了 7.6dp。
PC 侧还额外加了一条 `if (obj_Width < 30) obj_Width = 30;`（原版没有）。

改法（`myEditViewMap.initView()`）：把屏幕宽先折回设备像素再套原式，
并把 `+2 / 1 / +10 / 5` 这些裸像素常量一起折成 dp。

```java
int screenDev = Math.round(myMaps.m_nWinWidth * myMaps.DENSITY);   // 370 × 3.4051 = 1260
obj_WidthDev = Math.min(screenDev / 10, m_PicWidth * 2);           // 100（设备像素）
obj_Width    = dp(obj_WidthDev);                                   // 29dp
m_nArenaTop  = obj_Width + dp(2);                                  // 30dp
```

`myMaps.DENSITY = 3.4051f` 是本轮新增的常量（原版 `m_nWinWidth` 是设备像素、
PC 是 dp，凡「原版写死像素」的地方都要用它）。

**实测对照**（原版折算成 dp / PC 快照）：

| 项 | 原版 | 改前 PC | 改后 PC |
|---|---|---|---|
| 顶栏高 | 29.96dp | 39 | **30** |
| 地板槽 | [0.29, 29.66] | [1, 38] | **[0, 29]** |
| 墙槽 | [29.95, 58.44]（**98px 宽**） | [39, 76]（100px 宽） | **[30, 58]**（98px 宽） |
| 目标槽 | [60.21, 89.57] | [78, 115] | **[60, 89]** |
| 箱子槽 | [90.18, 119.53] | [117, 154] | **[90, 119]** |
| 仓管员槽 | [120.14, 149.50] | [156, 193] | **[120, 149]** |
| 尺寸框左缘 | 152.11 | 203 | **152** |
| 字号 | 40 设备像素 = 11.75dp | 14 | **12** |

> ⚠️ **原版 `rtW` 的笔误照抄了。** 原版写的是
> `rtW.set(obj_Width+2, 1, obj_Width+obj_Width, obj_Width+1)` —— 右边界少加了一个间隔，
> 墙面素材只有 **98 设备像素**宽（其它 4 个是 100）。截图实测 floor = 1..100、wall = 102..199，
> 证实是真·98px。PC 侧原先「顺手修好」成了 100px，本轮**改回照抄**。
> 判据见 `MEMORY.md` 的硬禁忌：先看原版是不是笔误/注释掉，再决定改不改。

### 2. 底栏 34dp 且只有文字、没有图标

原版 `edit_view.xml` 的 `edit_bottom`：

```xml
<RadioGroup android:background="#ff778899" android:gravity="center_vertical">
    <CheckBox android:id="@+id/bt_UnDo2" style="@style/tab_style"
              android:layout_marginTop="2.0dp" android:drawableTop="@drawable/unbit2" android:text="撤销"/>
    … 共 8 个（撤销/重做/剪切/复制/粘贴/变换/保存/更多）
</RadioGroup>
```

`tab_style` = `textSize 9dip` + `layout_margin 2dip` + `button="@null"` + `layout_weight 1.0`
→ **精确 1/8 等分**（370/8 = 46.25dp），实测底栏高 163px = **47.87dp**。

PC 侧原先是 `GridLayout(1, 8, 4, 4)` + `EmptyBorder(4,4,4,4)` + **纯文字** `JToggleButton`
（无图标、高 34dp、格子被 4px 间隙与边距挤窄）。`GridLayout` 正是 `RENDER_NOTES.md` 里
明令不要用来做 1/N 等分底栏的那个。

改法：把 `myGameView`（`main_bottom`）里已经验证过的那套 `tab_style` 实现提成共用件
**`compat/HoloTabBar`**（`TabButton` + `TabBarLayout` + 常量），两个界面共用
—— 原版本来就是同一份 `@style/tab_style`。

**顺带对齐的三个细节**（都在原版截图上量过）：

| 细节 | 原版实测 | 改前 PC | 改后 PC |
|---|---|---|---|
| 禁用项的**图标** | **不灰**，峰值 (233,237,240) | 被 FlatLaf 调灰 | 不灰 |
| 禁用项的**文字** | `#80ffffff` 叠在 `#778899` ≈ (187,196,204) | (153,153,153) | **(187,196,204)** |
| 启用项文字 | 纯白 | 纯白 | 纯白 |
| 「粘贴」初始态 | `setEnabled(!myMaps.loadClipper().equals(""))` → 空剪切板下**禁用** | 恒 `true` | 跟随剪切板 |

- **图标不灰**的原因：`android:drawableTop` 是一张**没有 state list** 的 PNG，
  `setEnabled(false)` 不影响它；只有文字色 `?attr/textColorPrimaryDisableOnly`
  是 ColorStateList。所以 `TabButton.paintComponent` 改成**整体自绘**
  （与 `compat/HoloButton` 同一套路，**不调 `super.paintComponent`**）。
- **文字基线**按原版模型放：CheckBox 高 `48-2-2 = 44dp`、padding 0、`drawableTop` 占 32dp，
  文字排在剩下的 12dp 里居中 →
  `baseline = 图标下沿 + (12 + ascent - descent) / 2`。
  实测文字墨迹 rel 3..44 vs 原版 3.23..43.75。

### 3. 实测对照（整屏）

| 项 | 原版 | PC |
|---|---|---|
| 顶栏高 | 29.96dp | 30 |
| 底栏高 | 47.87dp | 48 |
| 底栏 8 格宽 | 46.25dp | 46.25（`round(370*i/8)`） |
| 底栏图标 | 30.54dp 墨迹（32dp 位图） | 31 墨迹（32dp 位图） |
| 网格上缘偏移 | 80.71dp（理论 80.73） | 166.0（理论 166.0） |
| 网格单元 | 37.0dp | 37 |

**新增测试**：`Phase29EditViewLayoutTest`（16 个用例）—— 顶栏高/素材槽 5 个矩形/尺寸框四边/
墙槽 98px 笔误/字号；底栏 8 格、48dp、平底 `#778899`、精确 1/8 切列、图标 32dp、
文字 9dip、禁用态「图标亮 + 文字半白」、启用态纯白、粘贴跟随剪切板；
以及整屏渲染的 5 个色带锚点。

### 4. 已知残差（未改，留给用户判断）

| 残差 | 量级 | 说明 |
|---|---|---|
| **内容区高 780dp vs 原版 794.3dp** | **1.8%** | 原版 `myEditView` / `myGameView` 是沉浸态（`hideSystemUI()`），内容区 = 794.3dp；PC 全局约定是 370×780（`UiWindow` 的注释里扣掉了导航栏，那对沉浸态界面是多余的）。**没改** —— 改它要动 `WindowSizingTest` 与 15 个窗口的既有约定。表现：地图区 702dp vs 716.5dp，网格**垂直**居中位置差 7.2dp（网格尺寸不受影响，因为 10 列宽先卡住缩放）。 |
| 尺寸框文字基线 | ~2dp | 由 `getTextBounds()` 的 `rt.height()` 决定，而 PC 的串 `8列8行 [箱:4 标:4]` 含 `[` `]` 下降部、原版串 `10列15行 B-0 G-0` 不含 → 串相关，不是版面错。 |
| 底栏文字基线 | ~0.5dp | Microsoft YaHei 与 Noto Sans CJK 的 ascent/descent 不同，属字体度量差异（`RENDER_NOTES.md`：不复刻字体瑕疵）。 |

**当前基线：41 个用例类 / 436 个测试用例，全部通过。**

---

## BUG 修复 —— 「通关后跳下一个未解关卡，可能直接弹出死锁警告」（2026-09-26）

**用户报告**：完成关卡后跳转到下一个未解的关卡，可能直接弹出死锁警告。

### 1. 定位

「死锁警告」= `myGameView` 里唯一的警告框 `JOptionPane`「这是一个无解的关卡！」，
原先挂在后台任务 `AsyncCountBoxsTask.done()` 的末尾。

跳关链路：`UpData1()`（`myGameView.java:749`）→ `myClearance()` 判定通关 →
`JOptionPane.showConfirmDialog("恭喜过关！是否自动打开下一个未解关卡？")` →
`myMaps.curMap = m_lstMaps.get(k)` → `initMap()`（`:768`）。

`initMap()` 开头**有**取消逻辑（`:1911`）：

```java
if (mTask != null) { mTask.cancel(true); mTask = null; }
```

所以问题不在「忘了取消」，而在 **`SwingWorker.cancel(true)` 在 `doInBackground()` 已经跑完之后
是空操作**（返回 `false`，`isCancelled()` 保持 `false`）。而「通关 → 跳关」正好发生在
上一关刚算完的瞬间，于是旧任务的 `done()` 照常执行，造成两个后果：

1. **弹错提醒**：旧关卡的「这是一个无解的关卡！」弹在了新关卡上；
2. **污染数据**：`done()` 把旧关卡的 `mark14/15/16`、`mArray9` 写进视图，覆盖掉新关卡的死锁
   数据 —— 随后 `myLock()`（`:2255`）就会拿旧数据误判死锁。

### 2. 与原版的差异（这才是根因）

原版把提醒放在 **`onProgressUpdate()`**，由 `doInBackground()` 中途的 `publishProgress()`
（原版 `myGameView.java:5559`）触发 —— 提醒在**本关还显示在屏幕上时**就发出去了，
不会漂移到下一关；并且提醒后立刻 `m_bNoSolution = false`（原版 `:5601`）。

端口这边把提醒挪到了 `done()` 末尾，等于**推迟了一整轮**，正好落在跳关之后。
另有一处同类隐患：地图尺寸原先在 `doInBackground()` 里读 `myMaps.curMap.Rows/Cols`，
而后台线程真正起跑时关卡可能已经切走 —— 会拿**旧行数**去读**新地图**。
原版是 `execute(myMaps.curMap.Rows, myMaps.curMap.Cols)`，尺寸在 UI 线程捕获。

### 3. 修法

| # | 改动 | 对应原版 |
|---|---|---|
| ① | `doInBackground()` 开头改 `publish()` | `publishProgress()`（`:5559`） |
| ② | 新增 `process()` 弹提醒 + 复位 `m_bNoSolution` | `onProgressUpdate()`（`:5595`） |
| ③ | 新增 `isCurrent()` 闸门，`process()` / `done()` 都要先过 | 无对应（端口补的，见下） |
| ④ | 尺寸改到**构造器**（EDT 上）捕获 | `execute(rows, cols)` |
| ⑤ | 构造器里清 `mark14/15/16`、`mArray9`、复位标志、`bt_More` 染红 `0xffcc0000` | `onPreExecute()`（`:5467`） |

③ 是端口必须补的：原版的 `onProgressUpdate` / `onPostExecute` 也会被旧任务送达，
但它没有身份判据；端口这边 `done()` 要写共享字段，加一道
`view.mTask == this` 的闸门才能既防弹错提醒、又防污染数据。

### 4. 顺带补上的保真项

- `bt_More` 在任务进行中染红 `0xffcc0000`、结束时恢复 `0xffffffff` —— 原版
  `onPreExecute()` / `onPostExecute()` 都有，端口原先只在 `done()` 里恢复了白色，
  **红的这一半是缺的**。
- 给 `myGameView` 加了 `dialogShower` 测试缝（沿用阶段 G ⑦ 立的规矩，
  见 `TEST_NOTES.md`），否则这条提醒的模态框会把用例挂死。

### 5. 回归测试

**新增 `Phase30StaleTaskGuardTest`（8 个用例）**：切关后旧任务不再是当前任务；
旧任务不弹提醒；当前任务仍弹且只弹一次；旧任务的 `done()` 不覆盖新关卡的
`mark14`/`mArray9`；当前任务正常算完后照常写回；尺寸在构造时捕获、切关后不漂移；
构造任务时清空上一轮数据。

**⚠️ 已实测「撤掉闸门会失败」**：临时去掉 `isCurrent()` 后，
`testSupersededTaskDoesNotWarn` 与 `testSupersededTaskDoesNotOverwriteLockData`
两条如期失败（8 完成 / 2 失败），恢复后全绿 —— 即这两条真的锁住了这个 bug。

---

## BUG 修复 —— 「死锁警告弹窗，选『否』直接就卡死」（2026-09-26）

### 1. 现象

在关卡里走一步造成死锁时，会弹出「死锁移动 / 这一步造成关卡死锁，继续吗？」，
点「否」（撤销移动）之后**整个界面卡死**。

### 2. 根因：模态框 + 1ms 定时器 = 嵌套事件循环无限加深

提示框原先写成了阻塞式的：

```java
// UpData1() 末尾（旧代码）
if (m_nStep == 0 && myMaps.m_Sets[11] == 1 && mMap.d_Moves >= mMap.m_PicWidth && myLock(m_iR9, m_iC9)) {
    int ret = JOptionPane.showConfirmDialog(this, "这一步造成关卡死锁，继续吗？", "死锁移动", JOptionPane.YES_NO_OPTION);
    if (ret == JOptionPane.NO_OPTION) bt_UnDo.setChecked(!bt_UnDo.isChecked());
}
```

而 `UpData1()` / `UpData3()` 是 **`myTimer1` / `myTimer3`（1ms 一次性定时器）驱动的动画循环**
（`initTimers()`，`desktop/…/myGameView.java:645-668`）：

1. 定时器触发 `UpData1()`，动画推进到一格结束，末尾判定 `myLock(...)` 为真；
2. `JOptionPane.showConfirmDialog` 在 EDT 上开一个**嵌套事件循环**，一直阻塞到用户点按钮；
3. 那个 1ms 定时器在嵌套循环里**照旧触发** → `UpData1()` 重入 → 动画跑完一步
   → 又满足死锁条件 → 在**上一层模态框内部**再弹一层；
4. 层层嵌套、栈不断加深 —— 用户看到的就是「点了按钮就卡死」。

### 3. 与原版的差异

原版 `myGameView.java:1315-1325` 是**建一次、之后只 `setMessage()` + `show()`**：

```java
Builder dlg4 = new Builder(this);
dlg4.setTitle("死锁移动").setMessage("这一步造成关卡死锁，继续吗？")
    .setCancelable(false).setNegativeButton("继续", null)
    .setPositiveButton("撤销移动", (a, b) -> bt_UnDo.setChecked(!bt_UnDo.isChecked()));
lockDlg = dlg4.create();
```

调用点只有 `lockDlg.show()`（原版 `:435` / `:561`）。关键在两点：

| 原版语义 | 说明 |
|---|---|
| `AlertDialog.show()` **不阻塞** | 立刻返回，事件循环照常跑；用户点按钮时走监听器回调 |
| `Dialog.show()` 已显示时**早退** | Android `Dialog.show()` 开头 `if (mShowing) { …; return; }` —— 动画每走一步都调一次 `show()` 也只是把同一个框保持在屏幕上 |

端口把这两条**都丢了**：改成了模态框（丢 ①），而且每次判定为真都新建一个
`JOptionPane`（丢 ②）。所以模态 + 定时器重入 → 卡死。

### 4. 修法

| # | 改动 | 对应原版 |
|---|---|---|
| ① | `compat/HoloAlertDialog` 新增 `createNonModal(Frame, String)`（原 `create` 仍是模态，不动） | `AlertDialog` 的真实语义 |
| ② | `compat/HoloAlertDialog` 新增 `setMessage(String)`：换正文、清 `sized` 标记，已显示则就地重新测量 | `AlertController.setMessage()` + `wrap_content` 重排 |
| ③ | `myGameView` 加字段 `lockDlg`，在 `setupButtonEvents()` 开头**建一次** | `dlg4.create()`（`:1325`） |
| ④ | 按钮「继续」（negative，`null` → 只关）+「撤销移动」（positive → `dispose()` 再 `bt_UnDo` 取反），并把 positive 设为默认焦点按钮 | `setNegativeButton` / `setPositiveButton` / `requestFocusForDefaultButton` |
| ⑤ | 新增 `showLockDlg()`：`isLockDlgVisible()` 为真则早退，否则走 `dialogShower` 缝 | `Dialog.show()` 的 `mShowing` 早退 |
| ⑥ | `UpData1()` / `UpData3()` 两处判定点改调 `showLockDlg()` | `lockDlg.show()`（`:435` / `:561`） |

### 5. 顺带补上的保真项

原版 `myLock()` / `myLock2()`（`myGameView.java:2390-2419`）会按**具体原因**改正文，
端口原先只弹一句干巴巴的「这一步造成关卡死锁，继续吗？」—— 现已补齐：

| 检测器 | 正文 |
|---|---|
| `isLock_Goal`（正推） | `这一步造成关卡死锁，继续吗？\n（点位不足）` |
| `freezeDeadlock.isDeadlock` | `…\n（僵位冻结）` |
| `closedDiagonalLock.isDeadlock` | `…\n（闭锁对角）` |
| `isLock_Count`（逆推） | `…\n（点位不足）` |
| `isLock_Net2`（逆推「网」型） | `…\n（网位互锁）` |

### 6. 回归测试

**新增 `Phase31LockDialogTest`（13 个用例）**：提示框必须非模态；`createNonModal` 与
`create` 的模态语义各自锁住；标题/按钮文字/按钮顺序/默认焦点按钮与原版 `dlg4` 一致；
「撤销移动」取反 `bt_UnDo`；两个按钮都无条件先关框；四条原因都写进正文且 `setMessage`
能覆盖同一个框；提示框只建一次、已显示就不重复弹、走 `dialogShower` 缝；
源码扫描确认两个判定点不再有 `JOptionPane` 且都调 `showLockDlg()`。

**⚠️ 已实测「把 `createNonModal` 换回 `create` 会失败」**：5 条如期失败
（`lockDialogMustNotBeModal`、`showGoesThroughTheTestSeam`、`lockDialogIsConstructedOnlyOnce`，
以及两条直接**因为模态框真的把 EDT 阻塞住而超时**的 —— `alreadyShowingDialogIsNotShownAgain`、
`bothButtonsDismissTheDialog`），恢复后全绿。

### 7. ⚠️ 同类隐患（**已由 Phase33 处理，见下一节**）

原版 **全部 5 个 AlertDialog 都是「建一次 + 非阻塞 `show()`」**
（`AotoNextDlg` 恭喜过关、`exitDlg` 退出、`exitDlg2` / `exitDlg3` 更换关卡、`lockDlg` 死锁移动，
原版 `:1243-1325` 连续 `create()`），端口这边当时仍然全是阻塞式 `JOptionPane`：

| 位置 | 触发源 | 风险 |
|---|---|---|
| `:782` / `:840` 恭喜过关 | `UpData1()`（`myTimer1`） | **中** —— 仅当「瞬移 + 演示」同时开着时 `sleepTimer(myTimer1,…)` 才会留一个待触发的定时器 |
| `:634` 退出 | `windowClosing` | 低（不是定时器驱动） |
| `:2480` / `:2494` 更换关卡、`:2513` 关闭调试、`:3092` 重新开始 | 按钮 / 菜单事件 | 低 |
| `:2911` / `:3012` / `:3043-3047` 保存文档提示 | `saveAns` / `saveAns2` | 低 |

死锁提示之所以是唯一必卡的一条，是因为它**每次判定为真都在动画循环里**；
其余几个要么不在定时器里，要么需要「瞬移 + 演示」同时开。

> **后续（2026-09-26）**：上面这 5 个框已在 Phase33 里全部改成「建一次 + 非模态 + 回调」
> （`AotoNextDlg` / `exitDlg` / `exitDlg2` / `exitDlg3` 四个 + `lockDlg` 原有），
> 连带把 `AsyncCountBoxsTask.process()` 的「无解提醒」也换回原版的非模态 Holo 框。
> 但**只改非模态还不够** —— 还有第二个独立的根因，见下一节。
> 仍未对齐的阻塞 `JOptionPane`：`:2586` 关闭调试、`:2984` / `:3085` 保存文档、
> `:3165` 重新开始、`:3621`+ 设置项选择、`:4046` 宏选择。

## BUG 修复 —— 「点击撤销不会撤销，直接卡住」（2026-09-26，真正的根因）

### 1. 现象

用户在上一轮（非模态化）修复后回报：**问题依然存在，点击撤销不会撤销，直接卡住。**

### 2. 定位过程

`Phase33LevelCompleteDialogTest.realTimerFlowDeadlockThenUndoRestoresTheBoard`
端到端复现（真实 1 ms 定时器 + 真弹框）后，抓到的冻结态是：

```
busy=true nStep=0 dMoves=50/50 t1run=false edtFree=true unDo=[5,5] reDo=[]
```

`m_bBusing` 永远停在 `true`，而 `bt_UnDo` 的监听器第 **396** 行是
`if (m_bBusing) return;` —— 所以「点撤销没反应」。EDT 其实是**响应**的（`edtFree=true`），
不是真卡死，是动画循环**静默停摆**、忙标志再也没人复位。

决定性二分（同一关卡、同一动作队列，只改 `dialogShower`）：

| 变体 | 定时器响了几次 | 结束时 `m_bBusing` |
|---|---|---|
| A：`dialogShower` 只记录、不显示 | 60 | **false** ✅ |
| B：`dialogShower` 真的 `setVisible(true)` | **59** | **true** ❌ |

**只差一次响铃** —— 弹框让动画循环丢了最后一拍。

### 3. 根因：`javax.swing.Timer` 的 coalesce 竞态

原版 `RefreshHandler1..4.sleep(ms)`（`myGameView.java:168-171` 等）是：

```java
public void sleep(int m) {
    removeMessages(0);                        // 撤掉尚未投递的那一拍
    sendMessageDelayed(obtainMessage(1), m);  // 排新的一拍
}
```

Looper 对每个 message 都投递一次，**不存在合并**，所以每一拍必然到达。
而 `javax.swing.Timer` 默认 `coalesce == true`，JDK 源码里：

```java
// Timer.post()
if (notify.compareAndSet(false, true) || !coalesce) { SwingUtilities.invokeLater(doPostEvent); }
// Timer.DoPostEvent.run()
if (notify.get()) { fireActionPerformed(...); if (coalesce) cancelEvent(); }   // notify 在这里才清
```

`notify` 只在**回调返回之后**才被清掉。于是「在定时器自己的回调里重排下一拍」
（正是 `sleepTimer` 的用法）会踩中这个竞态：

1. 回调执行中 → `notify == true`；
2. `sleepTimer` 里 `isRunning()` 为 **false** —— 因为 `TimerQueueThread.run()` 在
   `timer.post()` **之前**就已经 `timer.delayedTimer = null`，所以 `containsTimer()` 为假
   → **不走 `stop()`** → `notify` 仍是 `true`；
3. TimerQueue 线程 1 ms 后 `post()` → `compareAndSet(false,true)` **失败**，且 `coalesce`
   为 `true` → **不投递** → 这一拍被丢掉；
4. 回调返回 → `cancelEvent()` → `notify = false`。

结果：定时器既不在队列里、`notify` 也是 `false`，**再也不会响**。
第 ③ 步只要落在第 ④ 步之前（EDT 在回调里停留超过 1 ms）就必然丢拍 ——
而 `showLockDlg()` 正好紧跟在同一拍的 `sleepTimer()` 之后，`setVisible()` 建窗/定尺寸要几十
毫秒，EDT 一直停在回调里，所以**弹框必然丢拍**。这就是变体 B 少响一次的原因。

### 4. 修法

`initTimers()` 里对 4 个一次性动画定时器关掉 coalesce：

```java
myTimer1.setCoalesce(false);   // myTimer2/3/4 同
```

关掉之后 `post()` 走 `|| !coalesce` 分支，**每次都投递** —— 与 `sendMessageDelayed` 等价。
`sleepTimer` 本体不动（`stop()` 对应 `removeMessages(0)`，`start()` 对应 `sendMessageDelayed()`）。

验证：变体 A / B 都变成 `fires=60 busy=false`；正常播放的拍数与修复前一致（不加速、不重复）。

### 5. 同类排查（全项目）

| 定时器 | 是否「在回调里重排自己」 | 结论 |
|---|---|---|
| `myGameView.myTimer1..4` | **是**（`sleepTimer`） | 本次已修 |
| `myGameView.mClockTimer`（3000 ms 重复） | 否（重复定时器，由 TimerQueue 自己重排） | 无需改 |
| `myGameView:261` 500 ms 长按 | 否（由 `mousePressed` 启动） | 无需改 |
| `myEditView:293` / `myGameViewMap:334` / `myRecogView:862` / `myRecogViewMap:733` 长按 | 否 | 无需改 |
| `myRecogView.mTimer` | 否（`start()` 一次，回调里只 `Thread.sleep`） | 无需改 |
| `MyToast.hideTimer` / `HoloProgressDialog:106` | 否 | 无需改 |

### 6. 回归测试（`Phase33LevelCompleteDialogTest`，5 条新增/强化）

- `rearmingFromInsideTheCallbackAlwaysDeliversTheNextTick` —— **根因用例**，用
  `dialogShower` 里一次 `Thread.sleep(20)` 精确模拟「弹框把 EDT 拖住」，**不依赖图形环境**，
  任何机器都跑；
- `realTimerFlowDeadlockThenUndoRestoresTheBoard` —— 端到端：走一步 → 弹死锁提示 →
  点「撤销移动」→ 箱子必须退回原位；
- `completingALevelThenJumpingToTheNextOneKeepsUndoWorking` —— 用户报的**完整路径**：
  通关 → 点「是」跳下一关 → 新关卡走一步死锁 → 弹提示 → 点「撤销移动」必须真撤销；
- `theFourAnimationTimersDisableCoalescing` —— 静态对偶，锁死 4 个 `setCoalesce(false)`
  且不误伤 `mClockTimer`；
- `@After` 补 `win.myStop()` —— 先停 `myTimer1..4` / `mClockTimer` 再 `dispose()`。
  `dispose()` 不会停 Swing Timer，漏掉这一步会让「上一支视图的定时器」在后续用例里继续回调
  （踩过一次，表现为「凭空出现一次 `reDo1 dir=7`」）。

**验证**：把 4 行 `setCoalesce(false)` 临时注掉 → 上面 4 条里**恰好 4 条失败**（含端到端那条），
恢复后全绿。这条链是可信的。

### 7. 基线

**当前基线：43 个用例类 / 466 个测试用例，全部通过**（含新增 4 条 coalesce 相关用例）。
`clean fatJar` = **16,034,869** 字节。

## BUG 修复 —— 「点『是』不消失，只是一直跳转到下一个关卡」（2026-09-26）

### 1. 现象

用户回报：完成关卡后弹出「恭喜过关！／是否自动打开下一个未解关卡？」，点「是」之后
**对话框不消失**，而且每点一次就往后跳一个关卡。

### 2. 根因：`HoloAlertDialog.addButton` 把「无条件关闭」写成了 if/else

原版 `AlertController.mButtonHandler` 是：

```java
if (m != null) { m.sendToTarget(); }                                   // 先派发监听器
mHandler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG, mDialog)      // 再**无条件**关闭
        .sendToTarget();
```

也就是说 **`AlertDialog` 的按钮点了就关，与有没有监听器无关**；监听器只是「关闭前额外做的事」。

而端口的 `addButton` 写成了：

```java
if (action != null) { action.run(); } else { dispose(); }   // ❌ 有 action 就不关
```

→ **凡传了 `action` 的按钮全都关不掉**。`AotoNextDlg` 的「是」正好是
「跑动作（跳下一关）但不关框」，于是框永远挂在屏幕上、每点一次再跳一关。

### 3. 为什么一直没被发现

全项目 **55 处 `addButton` 调用点**里，绝大多数都在 `action` 里**手写了一遍 `dlg.dispose()`**
—— 那正是在绕开这个 bug（`lockDlg` 的「撤销移动」、`myGridView` / `myEditView` /
`myRecogView` / `HoloChoiceDialog` 等都是），所以它们看起来正常。
只有 5 处没写，就全中招：

| 位置 | 按钮 | 后果 |
|---|---|---|
| `myGameView.java:311` | `AotoNextDlg`「是」 | **用户报的这个** |
| `myGameView.java:345` | `exitDlg2`「是」 | 换关卡后框不消失 |
| `myGameView.java:359` | `exitDlg3`「是」 | 同上 |
| `myGameView.java:3434` | 「关闭调试」「确定」 | 框不消失，压住了后面弹出的状态列表 |
| `myGameView.java:335` | `exitDlg`「是」 | 里面的 `dispose()` 是 `myGameView.this.dispose()`（视图），不是框 —— 但反正要退出了 |

### 4. 修法（根因）

`HoloAlertDialog.addButton` 的监听器改成「先跑 action，**finally 里无条件 dispose**」，
与原版 `mButtonHandler` 的顺序一致：

```java
try {
    if (action != null) action.run();
} finally {
    dispose();
}
```

各调用点里手写的 `dlg.dispose()` 从此**冗余但无害**，按最小修改原则**不删**。

### 5. 唯一需要特殊处理的一处：`BoxManPC.reName()`

原版「重命名」有**两条**路径，行为不同：

| 路径 | 原版写法 | 关闭时机 |
|---|---|---|
| 「确定」按钮 | `setPositiveButton("确定", listener)`，监听器里**不** dismiss | **无条件关**（靠 AlertDialog 自动关） |
| 回车 | `setOnKeyListener`，成功才 `di.dismiss(); return true;` | **只在成功时关**，失败留在原地让用户改 |

端口原先把两条都接到同一个「成功才 dispose」的 lambda 上。改为：按钮只传
`applyRename(...)`（交给 `addButton` 自动关，✓ 对齐「确定」），回车单独判成功才 `dispose()`
（✓ 对齐 `setOnKeyListener`）。

### 6. 回归测试

- `Phase33.clickingAButtonWithAnActionAlsoClosesTheDialog` —— **行为锁**：传了 action 的按钮
  必须既执行 action 又关框；`addButton(text, null)` 不能是死按钮；
- `Phase33.completingALevelThenJumpingToTheNextOneKeepsUndoWorking` 加断言 ——
  点「是」之后 `AotoNextDlg.isVisible()` 必须为假（直接编码用户那句话）；
- `Phase33.realTimerFlowDeadlockThenUndoRestoresTheBoard` 加断言 ——
  点「撤销移动」后 `lockDlg` 必须消失；
- `Phase25.testNullActionButtonClosesTheDialog` **改写**：原先它锁的是实现字面量
  `if (action != null) { action.run(); } else { dispose(); }` —— 那正是 bug 本身，
  等于把缺陷固化成「约定」。现在改成锁语义（不得出现 `} else { dispose(); }`、
  必须有 `finally { dispose(); }`）。

**验证**：把 `addButton` 临时还原成 if/else → 恰好上面 3 条失败；恢复后全绿。

### 7. 基线

**43 个用例类 / 466 个测试用例，0 失败 0 错误 0 跳过**；`clean fatJar` = **16,034,869** 字节。



## UI 还原 —— 底栏「瞬移 / 逆推 / 计数」的按下态（2026-09-26）

### 1. 现象

用户回报：游玩界面底部的「瞬移 / 逆推 / 计数」应当有**两种状态**，启用时按钮呈「按下」效果，
再点一次恢复。

### 2. 定位

原版 `game_view.xml` 的 `main_bottom` 是
`<RadioGroup android:background="#ff778899">` 里放 8 个
`<CheckBox style="@style/tab_style" android:drawableTop="@drawable/xxx">`。
`tab_style` 设了 `android:button="@null"`（去掉勾选框），所以**选中与否只能靠底色表达** ——
而原版确实在监听器里显式改底色（`myGameView.java`）：

```java
// 瞬移 cb_IM —— :1551-1554（初始态按上次的开关状态刷）+ :1561/:1564
bt_IM.setChecked(myMaps.m_Sets[6] == 1);
if (myMaps.m_Sets[6] == 1) bt_IM.setBackgroundColor(0xff445566);
else                       bt_IM.setBackgroundColor(0xff778899);
...
if (isChecked) { buttonView.setBackgroundColor(0xff445566); myMaps.m_Sets[6] = 1; }
else           { buttonView.setBackgroundColor(0xff778899); myMaps.m_Sets[6] = 0; }
// 逆推 cb_BK —— :1585/:1592   计数 cb_Sel —— :1632/:1638，同样两行
```

端口漏了这一步，底栏一直是平的 —— 与 `HoloTabBar` 类注释里「勾选态**没有任何视觉反馈**」
那句（**错的**）互为因果。

### 3. 只有这三个按钮变色

| 控件 | 原版是否 `setBackgroundColor` | 说明 |
|---|---|---|
| `cb_IM` 瞬移 / `cb_BK` 逆推 / `cb_Sel` 计数 | **是** | 真开关，两态 |
| `bt_UnDo` 后退 / `bt_ReDo` 前进 | 否 | 靠 `setChecked(!isChecked())` 触发动作的**瞬时**按钮 |
| `cb_TR` 转置 | 否 | 每点一次换一转（另配长按归零） |
| `cb_More` 更多 | 否 | 弹选项菜单 |

`myEditView` 的 8 个标签（撤销/重做/剪切/复制/粘贴/变换/保存/更多）也全是瞬时按钮，
`myEditView.java` 里 `setBackgroundColor` 出现 **0 次** —— 那边不用改。

### 4. 修法

`compat/HoloTabBar` 新增两个常量与一个方法，逐行对应原版那两行：

```java
public static final Color BG_CHECKED   = new Color(0x44, 0x55, 0x66);  // 0xff445566
public static final Color BG_UNCHECKED = BG;                          // 0xff778899
// TabButton
public void setCheckedBackground(boolean checked) {
    setBackgroundColor(checked ? BG_CHECKED : BG_UNCHECKED);
}
```

`myGameView.setupButtonEvents()` 里 7 处调用（瞬移初始 1 + 三个监听器各 2 个分支），
位置与原版 `if (isChecked) { … } else { … }` 的分支一一对应。

⚠️ **不要**把刷底色塞进 `TabButton.setChecked()`：`bt_UnDo` / `bt_ReDo` 是靠
`setChecked(!isChecked())` 触发动作的瞬时按钮，塞进去会让它们闪一下深色（原版不会）。

同时订正 `HoloTabBar` 类注释里「勾选态没有任何视觉反馈」的错误结论。

### 5. 回归测试（新增 `Phase34BottomBarToggleTest`，7 条）

- `theThreeTogglesStartUncheckedAndFlat` —— 初始都是 `#778899`；
- `checkingAToggleDarkensItsBackgroundAndUncheckingRestoresIt` —— 选中 → `#445566`，取消 → `#778899`；
- `theTwoStatesAreVisuallyDistinct` —— 两个色值真的不同，且分别等于 `#445566` / `#778899`；
- `imRestoresItsPressedLookFromTheSavedSetting` —— `m_Sets[6]==1` 时进游戏直接是按下态；
- `theInstantaneousTabsNeverChangeTheirBackground` —— 后退/前进/转置/更多选中也不变色；
- `theThreeToggleListenersRefreshTheirBackground` / `setCheckedDoesNotSecretlyChangeTheBackground`
  —— 源码约定锁。

**验证**：注掉 7 处 `setCheckedBackground(...)` → 恰好 3 条失败；恢复后全绿。

### 6. 基线

**44 个用例类 / 473 个测试用例，0 失败 0 错误 0 跳过**；`clean fatJar` = **16,035,107** 字节。
