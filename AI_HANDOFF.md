# the cutest girl —— 项目交接文档

> 最后更新：2026-11-08 ｜ 当前版本 **v4.1.0**（versionCode 40）  
> 本文档写给接手本项目的 AI 或工程师，包含全部关键上下文。用户是代码小白，中文沟通，所有面向用户的文案均为中文。
>
> **本次同步（2026-11-08）**：按 `OPTIMIZATION_PROMPT.md` 完成 **Batch 1（P0）**、**Batch 2 / 2b（P1-UI 全部）**、**Batch 3（P1-PERF 1–9）**、**Batch 4（P2-2/3/4）**、**Batch 5（P2-5/6）**，见下六节。

## Batch 5（P2-5 / P2-6，v4.1.0 未升版本）

| ID | 改动 |
|---|---|
| P2-5 | 三个实体加 `Index("date") + Index("dirty")`；DB **v5→v6** 迁移 `MIGRATION_5_6`（索引名 Room 规范的 `index_<table>_<column>`）；`exportSchema = true` + `ksp { room.schemaLocation }`，生成 `app/schemas/.../6.json`；云端 JSON 增加 `"schema": 1`；`parseSyncRecord` 增加校验（id 非空、mealType 0..2、eventType 0..1、事件标题非空、空 photoPath 视为无图），坏记录只跳过这一条 |
| P2-6 | 删除无效参数：`solidCard(elevation)`（10 处调用点已去掉）、`dashedCard(surfaceAlpha)`、`glassPanel/GlassCard(glow)`；删除无引用的 `replyVersionOf`、`EventViewModel.delete`、`SyncStatusKind.Conflict` 死分支；批量清理 102 个未使用 import |

### 验证
- 编译 + androidTest 编译 + `lintDebug`：0 errors / **6 warnings**；JVM 单测 4/4
- 设备回归 **26/26**（`.verification/tests-v4.1.0-p2-schema.txt`）
- 正式包真实 DB 升级：`PRAGMA user_version=6`，outfits/meals/events 数据保留（6/3/2 条），6 个索引全部存在，`logcat -b crash` 0 条
- **坑（重要）**：批量清理未使用 import 时不能只按「符号出现次数」判断——`getValue/setValue` 是 `by` 委托隐式使用的，会被误删导致全项目编译失败。已恢复并记录；以后清理 import 必须编译验证。

## Batch 4（P2-2/3/4，v4.1.0 未升版本）

| ID | 改动 |
|---|---|
| P2-2 | 新增 `sync/SyncStatusStore.kt`：`SyncStatus(phase/at/message/retryable)` StateFlow；`SyncRunner.runOnce` 在开始/成功/失败时更新，`SyncManager.startAfterConfigured` 用上次落盘结果 `prime()`；设置页与穿搭/饮食/点滴全部改为 `collectAsStateWithLifecycle`，不再解析 `lastSyncResult` 字符串；设置页显示「刚刚同步 / N 分钟前」；`SettingsStore.lastSyncResult` 仍照旧写入以兼容旧版本 |
| P2-3 | 新增 `sync/DavResult.kt`：`DavError { Auth, NotFound, Quota, Parse, Network }` + `DavResult<T>`；`WebDavClient` 所有方法改为返回 `DavResult`（401/403→Auth、404→NotFound、507→Quota、解析失败→Parse、IO→Network）；`SyncEngine.Result` 增加 `retryable/errorKind`，`SyncWorker` 只对可重试错误 `retry()`；设置页测试连接按错误类型显示「账号或应用密码不正确 / 服务器地址不对 / 云端空间不足 / 网络错误」；前台轮询遇到不可重试错误直接停止（P1-PERF-9 的认证停止补完） |
| P2-4 | 新增 `runCatchingCancellable`；`WebDavClient` 的 guard 与 `PhotoStore` 的 IO `runCatching` 全部改为取消异常原样抛出 |

### 验证
- 编译 + androidTest 编译 + `lintDebug`：0 errors / **6 warnings**；JVM 单测 4/4
- 设备回归 **26/26**（`.verification/tests-v4.1.0-p2-sync.txt`）
- 装机：设置页状态条显示「刚刚同步」，无崩溃
- **坑**：改 `WebDavClient` 签名后必须 `assembleDebugAndroidTest` 重新打包测试 APK，只 `compileDebugAndroidTestKotlin` 会装到旧测试包（本轮踩过一次，26 项全红）
- **未验证**：真机输入错误密码的完整 UI 流程；配额满/404 的真实服务器场景；设置页在后台同步完成瞬间的自动刷新（结构上已由 StateFlow 保证）

## Batch 3（P1-PERF 1–9，v4.1.0 未升版本）

| ID | 改动 |
|---|---|
| P1-PERF-1 | 删除穿搭/饮食/点滴三个页面的 `pageHaze` 与 `.haze(pageHaze)`（全仓无对应 hazeChild，纯开销）；底栏仍采样 AppRoot 的 `hazeState` |
| P1-PERF-2 | 顶栏只记录**展开态**高度，折叠动画期间不再改 LazyColumn 的 `contentPadding`；`onSizeChanged` 仅在 `!scrolled` 时更新 |
| P1-PERF-3 | 新增 `sync/HttpClientProvider.kt`：全进程共享 OkHttpClient（连接/读/写/整体超时 20/60/60/90s）；`WebDavClient` 不再每次 new |
| P1-PERF-4 | `PhotoStore.dir()` 缓存 application 目录（Batch 1 已顺手完成） |
| P1-PERF-5 | `SyncStateStore.saveFileSignatures/saveConfirmedFiles` 内容相同不写盘；`SettingsStore.saveSyncStatus` 状态文案不变时 `last_sync_at` 最多每分钟写一次，成功/失败切换立即写 |
| P1-PERF-6 | 大图瘦身/整目录扫描改为**最多 6 小时一次**（`last_maintenance_at`；首次同步/换端点会清掉该键立即跑）；照片补漏本来就只查 DB 引用的缺失文件 |
| P1-PERF-7 | 远端合并：网络阶段只下载 + 收集，全部结束后用一个 `db.withTransaction` 批量合并（`mergeRemoteLocked`），100 条记录 1 次事务 |
| P1-PERF-8 | 新增 `PhotoImage`：Coil 缓存 key 带 `name:lastModified`，瘦身覆盖同名文件后不会显示旧图；列表/编辑/抽屉/大图全部换用 |
| P1-PERF-9 | 前台轮询改 30 秒；连续失败指数退避 30→60→120→240→300 秒，成功立刻恢复；认证失败停止轮询依赖 P2-3 错误模型（未做） |
| P1-PERF-10 | 跳过：release/R8 需要用户确认，本轮不做 |

### 验证
- 编译 + androidTest 编译 + `lintDebug`：0 errors / **6 warnings**；JVM 单测 4/4
- 设备回归 **26/26**（`.verification/tests-v4.1.0-batch3.txt`）——批量合并/维护门控/共享客户端都在同步路径上，已覆盖
- 装机：三页截图正常，无本应用 crash；`logcat -b crash` 无记录
- **未验证**：真机 `gfxinfo` 帧率对比（模拟器不收集）、prefs 实际写入次数、10MB 图上传内存、6 小时维护窗口（未改时间实测）

## Batch 2b（剩余 P1-UI 6 项，v4.1.0 未升版本）

| ID | 改动 |
|---|---|
| P1-UI-6 | 新增 `AppTopBar`（quietTopAppBar + IconTitle + windowInsets=0，避免双倍状态栏留白）与 `DestructiveAction`（48dp、错误色、无 ripple）；OutfitEdit / MealEdit / EventEdit / OutfitUpload / Settings 全部换用；设置页「后台不被冻结」不再卡中卡（`SettingsActionRow(container=false)`） |
| P1-UI-7 | 饮食/点滴编辑页 `PhotoNoteCard` 传 `photoRatio = 3f/2f`，与列表卡一致；穿搭编辑/上传保持 4:5 |
| P1-UI-8 | 三个 tab 的 `FloatingTopBar` 都显示设置齿轮（长按标题快捷方式保留）；实测饮食/点滴都能一键进设置 |
| P1-UI-12 | 新增 `rememberToday()`：ON_RESUME 刷新 + 到下一个午夜自动刷新；穿搭/饮食/点滴/纪念日卡全部替换 `remember { LocalDate.now() }` |
| P1-UI-13 | `PhotoRemoveButton` 视觉 32dp、热区 48dp；上传页删除键此前已 48dp |
| P1-UI-14 | `PhotoViewerDialog` 支持 `photos + initialIndex`：HorizontalPager 左右滑、双指缩放（1–5x）、双击放大/还原、单击关闭、右上角关闭按钮；编辑页多图共用同一 viewer |

### 验证
- 编译 + androidTest 编译 + `lintDebug`：0 errors / **6 warnings**；JVM 单测 4/4
- 装机验证：
  - 饮食/点滴页都有设置齿轮，点击进入设置页
  - 设置页顶栏标题 y≈180（单层状态栏留白，不再双倍）
  - 饮食编辑页顶栏/保存/删除行显示正常；大图打开后「关闭」按钮可点
- 未验证：跨午夜自动刷新（未改系统时间实测）；大图双指/双击手势、左右滑（只验证了打开/关闭）；9 张照片横向滚动

## Batch 2（P1-UI 高优先 6 项，v4.1.0 未升版本）

| ID | 改动 |
|---|---|
| P1-UI-1 | 饮食页对方记录可点开写评论：`MealContentCard` 增加 `partner` 标志（保留左缘钴蓝/粉条），对方卡片 `clickable=true` 进编辑页；照片内部点击仍是看大图；备注/照片只读、评论可写由原 VM 保证 |
| P1-UI-2 | 通知点进「点滴」定位到记录：`PartnerNotifier` 增加 `EXTRA_RECORD_ID` → `MainActivity.NotificationTarget.recordId` → `AppRoot` → `EventScreen`；按 id 滚到对应 moment，找不到按日期回退，纪念日记录滚到纪念日分组；数据未加载时不提前消费 |
| P1-UI-3 | `AppRoot` 的 `pendingDate` 改为 `pendingTarget`（route/date/recordId）：只传给 route 匹配的页面，页面消费后回调清空；`jumpedDate/consumedTargetKey` 防重复 |
| P1-UI-4 | 抽屉完善：照片缩略图改 `LazyRow`；高度 0.62 屏高；去掉与 ModalBottomSheet 重复的 `navigationBarsPadding`；`hasUnsavedChanges` + 「放弃这次编辑？」确认；饮食抽屉日期行改为不可点信息行 |
| P1-UI-5 | `SoftCapsuleButton` 禁用态 alpha 0.38（照片到上限时明显变灰） |
| P1-UI-10 | Android 12+ 启动闪屏：`values-v31/themes.xml` 与 `values-night-v31/themes.xml`，背景 `#FAF7F8`（浅色品牌），冷启动实测首帧 `#FAF7F8`，无白闪 |

### 验证
- `compileDebugKotlin` + androidTest 编译 + `lintDebug`：0 errors / **6 warnings**
- JVM 单测 4/4；设备回归 **26/26**（`.verification/tests-v4.1.0-batch2.txt`；该轮在 EventScreen 滚动修正前跑，滚动修正只动 UI，未再复跑）
- 装机验证：
  - 通知 intent（meal/2026-10-29）→ 饮食页跳到 10月29日；再切穿搭页仍是 11 月（不串 tab）
  - 通知 intent（event/e-theirs）→ 点滴页目标卡片可见；内容不足一屏时无法滚到顶部（LazyColumn 到达滚动边界，属正常）
  - 对方午餐卡点开 → 编辑页「ta 的备注」只读、评论输入框可用、无删除按钮
  - 穿搭抽屉：输入备注后返回 → 弹「放弃这次编辑？」，确认后关闭并清理未保存照片
  - 冷启动首帧 `#FAF7F8`
- 未手动验证：9 张照片横向滚动（代码用 LazyRow，未做 9 张实选）；两页纪念日记录的滚动定位

## Batch 1（P0 正确性 / 数据安全，v4.1.0 未升版本）

### 已完成
| ID | 修复 | 主要文件 |
|---|---|---|
| P0-1 | 单图「换一张」真正替换；多选先按剩余名额截断再复制；被替换/超额的孤儿文件清理；新增纯函数 `nextPhotoList` + 4 个 JVM 单测 | `Common.kt`、`AddRecordSheet.kt`、`NextPhotoListTest.kt` |
| P0-2 | 只有上传人能删除；三个 EditViewModel 增加 `canDelete` 并在 `delete()` 里二次拦截；上传页删除键仅自己的照片显示；`deleteExisting` 按 origin 防御 | 3 个 EditViewModel、3 个编辑页、`OutfitUploadScreen.kt` |
| P0-3 | 删除记录 + 写墓碑放进 `AppDatabase.delete*WithTombstone()` 同一 Room 事务；照片删除移到事务之后 | `AppDatabase.kt` + 5 个删除调用点 |
| P0-4 | 「先验证、后提交」：`SyncEngine.verifyAndApplyDavConfig()` 用候选配置连一次（换端点还要建目录 + 真列 records/photos），成功才保存；失败不动旧配置/账本。换端点前弹确认框 | `SyncEngine.kt`、`SettingsScreen.kt` |
| P0-5 | 目录准备改为「MKCOL + listDir records/photos 全部成功」才算 ready；失败抛 `DirectorySetupException` 并在 catch 里把 `dirsReady=false`；`testConnection` 同时校验两个目录 | `SyncEngine.kt` |
| P0-6 | 照片原子写入（`.tmp` → rename，失败复制兜底）；`WebDavClient.downloadTo/uploadFrom` 流式传输；`saveFromUri` 先临时文件再压缩改名；`compressInPlace` 不再先删原图 | `PhotoStore.kt`、`WebDavClient.kt`、`SyncEngine.kt` |
| P0-7 | 即时同步 3 秒节流；周期任务默认 `KEEP`（改配置时 `force=true` 才 `UPDATE`）；`MainActivity` 只在 `savedInstanceState == null` 时排同步 | `SyncManager.kt`、`MainActivity.kt`、`SettingsScreen.kt` |

### 两个关键取舍（与任务书原文不同）
1. **P0-7 没有用 `enqueueUniqueWork(KEEP)`**：现有回归「immediate sync bypasses a previous failed request's retry delay」要求失败退避后点同步能立刻跑；KEEP 会挡住它。改为 **3 秒节流 + 普通 `enqueue`**，去重验收（连点 5 次只入队一次、旋转不重复）仍然满足。
2. **P0-1 的孤儿清理只删「本次新增里被丢弃的」文件**：编辑已有记录时，被替换的旧图仍被数据库引用，必须等保存成功由 ViewModel 删除；否则用户取消编辑会把原记录照片弄坏。纯函数 `nextPhotoList` 仍按任务书返回旧图孤儿，由调用方按场景决定是否立即删。

### 验证
- 编译 + `compileDebugAndroidTestKotlin` + `lintDebug`：0 errors / **6 warnings**（基线）
- JVM 单测：`testDebugUnitTest` 通过（`NextPhotoListTest` 4 项）
- 设备回归：**26/26 通过**（`.verification/tests-v4.1.0-batch1.txt`）
- 装机手动验证：
  - 对方点滴记录 → 编辑页**无**「删除这条点滴」
  - 自己午餐记录 → 编辑页**有**「删除这条记录」
  - 浅色界面/抽屉仍正常
- 说明：单图「换一张」的完整相册选图流程未做人工点选验证（逻辑由单测覆盖）；删除事务回滚未单独注入异常验证；P0-4 的「错误密码不覆盖旧配置」未做真机输入验证。

### 未做（留给后续批次）
P1-UI / P1-PERF / P2 全部未动。P1-PERF-4（`PhotoStore.dir()` 缓存）在 P0-6 顺手做了。

---

## 上一轮（v4.1.0）：浅色品牌体系（简洁白 + 粉红）

### 需求（用户原话）
用户以「品牌视觉系统重构专家」角色要求：把「深色 + 梅紫/钴蓝/余烬橙」整体切换为「简洁白 + 粉红」；取消深色方案；玻璃态仅保留底部导航栏，其余卡面统一为与底栏一致的玻璃外观。

### 色彩体系（新品牌 tokens）
| 角色 | 值 | 说明 |
|---|---|---|
| 页面底 | `#FAF7F8` | 简洁白，一丝暖粉 |
| 卡面 | 白 78% 玻璃（`Color.White.copy(0.78f)`） | 叠在页底上 |
| 边框 / 分割线 | `#14201A1D`（8% 深） | 1dp 半透 |
| 主文字 | `#201A1D` | 白底 ≈ 15.6:1 |
| 次文字 | `#625A5F` | ≈ 6.9:1 |
| 辅助 | `#7E757C` | ≈ 4.9:1 |
| **品牌粉** | `#C92661` | 主操作/选中，白底 ≈ 5.3:1；按钮白字 |
| 粉容器 | `#FCE4EC` / 软粉 `#FDF2F6` | 选中底 / 光晕 / 空态 |
| 成功 / 警告 / 失败 | `#2E7D5B` / `#9A5B00` / `#C62828` | 同步状态语义色 |

**原品牌色处置**：梅紫 → 淘汰（由品牌粉取代）；钴蓝 → 淘汰（信息/同步改用中性灰 + 语义色）；余烬橙 → 降级为 `#9A5B00` 的警告/冲突语义色；奶油白 → 保留为页底基因（`#FAF7F8`）；单一玫瑰 `#B25A79` → 升级为更明确的品牌粉 `#C92661`。

### 玻璃态范围（本轮明确）
- **真实背景模糊（Haze）只保留底部液态导航栏**。
- 其余所有卡面统一为「玻璃外观」：`glassPanel` = 78% 白 + 顶部内发光（白 55%→透明）+ 1dp 8% 深边框 + 柔和中性投影（8dp，ambient 6% / spot 10%）。没有真实模糊，因为卡片背后是纯色页底，模糊不可见。
- `solidCard` 已是 `glassPanel` 的别名，全站卡片（列表卡、编辑卡、设置分组、顶栏、抽屉）材质一致。

### 组件参数
- 主按钮：品牌粉实底 + 白字，48dp、圆角 8，按压 98%
- 次按钮：白 86% + 1dp 边框，40dp
- FAB/齿轮：白 86% 圆 + 投影 10dp + 边框
- 顶栏：玻璃面板 24 → 收起 16，白玻璃 + 投影
- 抽屉：55% 屏高、40% 黑遮罩（已验证合成色 `#969495`）、顶部圆角 24
- 输入：`#F5EFF2` 底 + 8dp 圆角 + 8% 边框
- 纪念日：数字等宽 Light + 品牌粉，左缘横向粉光 12%（用户确认过的形态，仅换色）
- 液态滑块：品牌粉 18% 糖浆 + 白色高光

### 必须记住的坑
- **强制浅色**：`TheCutestGirlTheme` 忽略系统深色；`values/themes.xml` 与 `values-night` 都固定浅色 + `windowBackground=#FAF7F8`；`enableEdgeToEdge` 用 `SystemBarStyle.light`（深色状态栏图标）。用户明确要求「取消深色方案」，不要加回深色分支。
- **粉红使用比例**：只用于主操作、选中态、FAB 图标、纪念日数字与左侧光；不得给卡片背景、正文、大面积区域上粉。文字用 `#C92661`（5.3:1），浅粉底上用 `#7A1038`（`PinkInk`）。
- **玻璃卡是「外观」不是「模糊」**：别给普通卡片挂 Haze（性能开销且背后是纯色，看不出效果）；真实模糊只在底栏。
- 原深色 token 名字保留了别名（`Void/Panel/Plum/...`）映射到浅色，避免历史引用断裂；新代码直接用语义色。

### 验证
- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` + `lintDebug`：0 errors / 6 warnings（基线）
- 模拟器 testgirl 实测：浅色五个页面 + 两个抽屉截图在 `.verification/screens/v410/`；系统切深色后 App 仍保持浅色（`08-system-dark-app-light.png`，采样 #FAF7F8）
- APK `the_cutest_girl_v4.1.0_debug.apk`

---

## 上一轮（v4.0.0）：全站深色玻璃 UI（严格按「去AI味4步」规范）

### 需求（用户原话）
「这版严格适配你的完整技术栈……只把视频4步写成UI规范，不引入外部库或DI……只改 UI 质感，不动同步/数据逻辑。」用户拍板：**全站深色化、全部页面、新增记录用底部抽屉（编辑保留全屏）、系统无衬线字体**。

### 技术边界（已遵守）
- 单模块 Kotlin + Compose Material3；无 Hilt/Koin、无外部 UI 库
- Room / Entity / DAO / ViewModel / Flow **未改**；抽屉写入复用现有 `MealEditViewModel` / `EventEditViewModel` / `OutfitUploadViewModel`
- WorkManager / OkHttp / WebDAV / SyncEngine **未改**；同步 UI 只读 `SettingsStore.lastSyncAt/lastSyncResult`
- 图标全部 Compose Material Icons（Outlined），16/20/24dp；禁止 emoji 当功能图标

### 设计 tokens（改 UI 前先读）
```
背景 #060607 · 卡片底 #0D0D12 + 7% 白玻璃 · 边框 1dp 10% 白
梅紫 #B9A0FF（主操作/穿搭）· 钴蓝 #7FA6FF（信息/同步/饮食）· 余烬橙 #FFA47C（点滴/纪念日/警告）
成功 #6FD39A · 失败 #FF6B6B
圆角：控件 8 / 卡片 16 / 容器·抽屉 24（Shape.kt）
按钮：主按钮全宽 48dp；小控件高 40dp
字号：页面标题 30 Medium · 正文 16 Regular · 辅助 14 · 角标 12
数字：FontFamily.Monospace；纪念日大数字 44sp Light（BigNumeralStyle，Type.kt）
间距：4/8/16/24/32/48 网格；页面左右 24、卡内 16、卡间距 16、模块间距 24
```
材质：`Modifier.glassPanel()`（Glass.kt）= 实底 + 7% 白玻璃 + 背景之后的内发光 + 1dp 半透边框；**不叠投影**（深色下靠明度差 + 边框）。真实背景模糊只保留给底部液态导航栏（Haze）。

### 组件（全部可复用，均在 `ui/components/`）
- `GlassCard` / `glassPanel`：唯一卡片材质（solidCard 兼容旧调用，内部同实现）
- `PrimaryButton`：48dp 全宽主操作，按压 scale 98%
- `SoftCapsuleButton`：40dp 次操作（选照片 / 回到今天 / 重试）
- `SolidCircleButton`：FAB / 齿轮
- `SyncStatusBar`：**空闲 / 同步中 / 成功 / 冲突 / 失败** 五态（见 Common.kt），失败带原因 + 重试
- `AddRecordSheet` + `SheetTextField` / `SheetDateRow` / `SheetSegmented` / `SheetPhotoPicker`（AddRecordSheet.kt，含 @Preview 开/合两态）

### 新增记录抽屉（v4.0 交互）
- 穿搭：FAB 或每一天的「+」→ `AddRecordSheet`（日期 + 最多 9 张 + 备注）；已有照片通过「管理这一天的 N 张照片」仍可进原上传页
- 饮食：未记录餐次的虚线卡 → 抽屉（照片 + 备注）；已有记录仍进全屏编辑页
- 点滴：FAB → 抽屉（日常/纪念日分段 + 标题 + 日期 + 照片 + 描述）
- 半屏 55%、遮罩 40% 黑、顶部圆角 24、关掉后表单复位；**取消时删除未保存的照片文件**，避免孤儿文件
- 编辑已有记录仍是全屏页（PhotoNoteCard 同步了同一套容器参数）

### 必须记住的坑
- **主题强制深色**：`TheCutestGirlTheme` 忽略系统浅色；`values/themes.xml` 也固定深色 + `windowBackground=#060607`，`enableEdgeToEdge` 用 `SystemBarStyle.dark`。别再把亮色分支加回来。
- **同步冲突（Conflict）组件已就绪但未接线**：SyncEngine 目前没有冲突信号，设置页不会伪造；等以后同步层真能报冲突再传给 `SyncStatusBar(conflictCount=…)`，三按钮策略（保留本地/保留云端/合并）属于同步逻辑，不在 UI 层实现。
- **抽屉复用编辑 ViewModel**：打开时手动复位字段；不要给这些 VM 加新方法，避免碰「只改 UI」的边界。
- **1.6dp 图标描边无法字面实现**：Material Icons 是字形不是 stroke；统一用 Outlined + 16/20/24，即为规范允许的等效落地。
- **Geist 未引入**：规范允许「无则系统无衬线」，当前用系统 Sans + Monospace 数字。

### 验证
- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` + `lintDebug`：0 errors / 6 warnings（基线）
- 模拟器 testgirl 装机实测：五个页面 + 两个抽屉截图在 `.verification/screens/v400/`
- APK `app/build/outputs/apk/debug/the_cutest_girl_v4.0.0_debug.apk`

---

## 上一轮（v3.0.4）：图文容器质感统一 + 光影定稿
- 用户看过径向柔光（v3.0.2/3.0.3）后明确要求「换成原来左缘一条横向渐变」。已恢复 v3.0 之前的画法：`Brush.horizontalGradient(主色 12% → 透明, endX = 170dp)`，数字坐在光里，卡片右侧仍是干净白面。
- **以后别再改这层光**：`alpha 0.12 / endX 170dp / 左缘横向` 是用户确认过的最终形态。
- `versionCode = 38` / `versionName = "3.0.4"`。

### v3.0.3 补丁：纪念日光影再压淡一档（用户要求「更淡一点」）
- 径向柔光 alpha：`0.16 → 0.10`，中间停靠点 `0.07 → 0.04`（0.45→0.50 位置），半径 `150 → 160dp`（更大更散，观感更接近"一层空气光"而不是光斑）。
- 其余不变；`versionCode = 37` / `versionName = "3.0.3"`。

### v3.0.2 补丁：纪念日卡光影回归（用户明确要求）
- **背景**：v3.0.0 按 unslop「去除彩色色块/渐变 wash」规则删掉了纪念日卡左缘的渐变光；用户装机后指出「之前纪念日天数是有光影感觉的，现在改没了」。这是用户记得并喜欢的细节，恢复。
- **做法**：`ui/event/EventScreen.kt` 的 `AnniversaryCard` 用 `.drawBehind` 画一圈**径向柔光**（主色 16% → 45% 处 7% → 透明），圆心 `(86,58)dp`、半径 `150dp`，只罩住「第 N 天」数字区域；数字本身仍是纯主色 SemiBold。
- **约束**：这是全 app **唯一**允许的彩色光（属于首页 "moment" 元素）；以后不要再给其他卡片加同类 wash。若有人再拿 unslop 规则来删它，先看本条。
- `versionCode = 36` / `versionName = "3.0.2"`。

## 本轮改动（v3.0.1）：图文容器质感统一（饮食 / 点滴 / 编辑页）

### 需求（用户原话）
「用方案一，同步给编辑页的 PhotoNoteCard」——即按《图文容器视觉质感优化方案》的「通用容器规范」落地，并让编辑页的 `PhotoNoteCard` 使用同一套参数。

### 参数规范（以后新建图文容器照此执行）
```
卡 18 · 图 8 · 内缩 12 · 图文距 12 · 文字 15/23 · 辅助 12/17 · 标题 16/24
比例 3:2 · 真图无框 · 占位 1dp 40% · 投影 6dp(6%/10%) · 发丝线 6%（深色 10%）
评论线 2dp primary40% 等高（IntrinsicSize.Min） · 深色无投影
```

### 做法
| 位置 | 改动 |
|---|---|
| `ui/components/Glass.kt` | `solidCard`：投影默认 8→6dp、ambient/spot 10%/14%→6%/10%；卡片发丝线 `outline 55%`→`onSurface 6%`（深色 10%）——全局生效，设置页/输入卡一起变柔和。新增 `Modifier.photoShape()`（真图只裁圆角、无框）；`photoFrame` 改为「占位/同步失败专用」（1dp `outline 40%`），默认圆角 8 |
| `ui/meal/MealScreen.kt` | `MealContentCard`：归属行内边距 16/10→12/12；图片内缩 14→12、固定高 190→`aspectRatio(3:2)`、圆角 14→8、真图改 `photoShape`（无框）、占位同步失败同比例；备注内边距 16/12→12/12；评论线改 `height(IntrinsicSize.Min)` + `fillMaxHeight`，颜色 50%→40%，内边距 12/14 |
| `ui/event/EventScreen.kt` | `MomentCard`：图片内缩 14→12、高 200→`aspectRatio(3:2)`、圆角 8、真图无框；文字块 16/12→12/0/14（无图时补 12 顶部）；描述 14sp→15sp（与饮食备注同档）；日期与评论间距 6→8；评论线同上 |
| `ui/components/Common.kt` | `PhotoNoteCard`（编辑页）：`cardCorner` 20→18，单图/多图圆角 12/14→8，与列表卡同轴同参 |
| `app/build.gradle.kts` | `versionCode = 35` / `versionName = "3.0.1"` |

### 两个需要记住的坑
- **`photoShape` 与 `photoFrame` 是两个语义**：真图用前者（无框），只有占位/同步失败状态用后者。以后新增图片位别再给真图加描边。
- **评论竖线依赖 `Row(Modifier.height(IntrinsicSize.Min))`**：去掉这行，`fillMaxHeight()` 的竖线会塌成 0 高。改布局时别把 `height(IntrinsicSize.Min)` 删掉。

### 验证
- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` + `lintDebug`：0 errors / 6 warnings（基线）
- 参数已抽查：图片内缩/圆角/比例/文字内边距/评论线在「饮食」「点滴」「PhotoNoteCard」三处一致
- ⚠️ 未做装机截图验证（本轮无模拟器操作），观感需装机确认

---

## 上一轮（v3.0.0）：UI 全站 unslop 优化（按 unslop 技能流程）

### 需求（用户原话）
「我想要对这个项目 app 的 ui 进行全方面优化升级」（并明确拍板四个决定：emoji 全换线性图标；玻璃只留底部液态导航栏；三模块强调色收敛成单一玫瑰；一次改完所有页）。

### 设计决定
- **方向**：从现有品牌推导——产品是「一对情侣记录她穿搭/三餐/小事的私人日记」，性格「温柔、安静、一点点甜」，世界「毛毡玩偶与手账剪贴簿」，焦点时刻「纪念日大数字 + 照片本身」，签名细节「毛毡贴纸（空态合照）+ 日期条记录圆点」。
- **判定**：轻中度 slop → polish 模式，保留奶油底 + 玫瑰 + 字重体系，不换新风格。

### 做法
| 位置 | 改动 |
|---|---|
| `ui/theme/Accent.kt` | **整文件删除**：ModuleKind / moduleAccent / moduleAccentSoft / moduleGlowColor 全移除，强调色收敛成单一 `colorScheme.primary` |
| `ui/components/Glass.kt` | 重写：`glassMaterial`（仅底栏用）投影改中性暖褐；新增 `neutralShadowTint`；删除 `glassTints / GlassBlurRadiusNested / drawGlassEdge / glassStatic / GlassTopPanel / moduleGlow / 旧 GlassTopBar / 旧 GlassCircleButton / 旧 GlassCapsuleButton`；`glassPhotoFrame`→`photoFrame`（安静描边框）；`glassTopAppBar`→`quietTopAppBar`；`GlassTopBar`→`FloatingTopBar`（实色 solidCard 面板 + 收起胶囊，去掉 haze，动画从 Animatable+冻结模糊简化成 animateDpAsState）；`GlassCircleButton`→`SolidCircleButton`（实色圆钮）；`GlassCapsuleButton`→`SoftCapsuleButton`（白面 + 发丝描边）；新增统一弹簧常量 `UiSpringDamping=0.65f / UiSpringStiffness=400f`（液态滑块物理弹簧除外） |
| `ui/components/GlassBottomBar.kt` | 只留 GlassTab + 液态玻璃底栏 + 滑块绘制；移除 GlassCircleButton（迁到 Glass.kt）；`bottom=6dp`→`4dp` |
| `ui/components/Common.kt` | `EmojiTitle`→`IconTitle`（图标 + 标题行）；删除 `ModuleEmoji / ButtonEmoji / PageEmojiSize / BarEmojiSize`，新增 `PageIconSize=22dp / BarIconSize=18dp`；PageHeader 参数 `emoji: String?`→`icon: ImageVector?`；`TextNoteBlock.leadingEmoji`→`leadingIcon`（💬→`Icons.Outlined.ChatBubbleOutline`）；照片操作行 emoji 前缀换成 PhotoLibrary/PhotoCamera 图标；GlassCapsuleButton→SoftCapsuleButton（6 处）；「今天」静态胶囊徽标→安静主色小字；月份弹窗格子与日期条 chip 圆角 50/14→12；ActionRow 间距 10→8 |
| `ui/outfit/OutfitScreen.kt` 等三个主页 | 删除页顶 `moduleGlow` 彩光层；标题 emoji→导航同款线图标（穿搭 Checkroom / 饮食 Restaurant / 点滴 FavoriteBorder）；列表节奏统一 `spacedBy(16.dp)`（原 18/18/12）；照片网格 10→8；FAB 改 SolidCircleButton；缩略图「💗昵称」「💬评论」胶囊→半径 10 的安静 chip，💬→评论图标 |
| `ui/meal/MealScreen.kt` | `MealDuotoneIcon` 圆底暖橙→主色玫瑰 12%/20%；对方记录左侧强调竖条→主色；`glassPhotoFrame`→`photoFrame` |
| `ui/event/EventScreen.kt` | 纪念日卡删除左缘强调色渐变 wash（数字本身是唯一亮色焦点）；分组竖条→主色；同理 photoFrame / 列表节奏 |
| `ui/settings/… + 三个编辑页 + 上传页` | `⚙️`→`Icons.Outlined.Settings`；各编辑页标题 emoji→对应线图标（`quietTopAppBar` + `IconTitle`）；设置页间距 14→16 |
| 文案 | 删除唯一感叹号：饮食编辑页占位「超满足！」→「超满足」；通知标题「💗 $who…」→「$who 发来了新内容」；删除未使用的 `MEAL_EMOJIS` |
| `app/build.gradle.kts` | `versionCode = 34` / `versionName = "3.0.0"` |

### 需要记得的后续坑
- **玻璃如今只有底栏一处**：`haze` 采样源（各页 `.haze(pageHaze)`）仍保留，因为底栏需要模糊内容，删了底栏会变实心灰块。别再给别的组件挂 `glassMaterial`。
- **动效只有一条弹簧**（`UiSpringDamping/Stiffness`，在 Glass.kt 顶部，`internal`）：新组件按压/折叠回弹一律用它；**唯一例外**是底栏液态滑块的三个弹簧（位置 0.58/420、形变 0.32/420、吸附 0.45/380），那是「液体」手感本身，别顺手统一。
- **标题图标与导航同一套**：以后加第四个 tab，标题图标要复用 `Icons.Outlined.*` 的对应图标，不要再回退 emoji。
- `FloatingTopBar` 收窄动画里「只测一次、溢出裁切」的 `Layout` 技巧保留着：宽度动画每帧变时字幕/日期条不需要重排，去掉会有掉帧风险。

### 验证
- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` + `lintDebug`：**0 errors / 6 warnings**（与基线一致）
- 三个主页 + 设置 + 四个编辑/上传页全部人工过了一遍代码路径：无残留 emoji（全仓 grep），无残留 `moduleAccent/moduleGlow/GlassCapsuleButton` 等旧符号引用
- ⚠️ **未做装机截图验证**（本轮无模拟器操作），界面观感需要装到手机上过一眼，重点看：顶栏收窄动画是否顺滑、日期条选中态、饮食页虚线卡与新主色是否协调

---

## 上一轮（v2.9.2）：换云盘时自动重置同步账本

### 需求（用户原话）
先问「如果想换云存储照片等数据的地方只要把服务器地址一改账号一改就可以了吗？那之前的上传的照片等数据换完服务器地址之后手机 app 上面还有吗」，
听完解释后拍板：「把这个小改动加上」——即「在『测试并开启同步』里检测到地址/账号变化时，自动把 `dirsReady`、文件指纹、已确认集合、首次同步标记清掉，并对新服务器重新发 MKCOL」。

### 换云盘的三个坑（改动前真实存在）
1. **`testConnection()` 只对新账号根目录发 PROPFIND Depth 0**（`sync/WebDavClient.kt:38`）——根目录必然存在，所以「测试成功」是个假信号；真正同步时 `listDir("thecutestgirl/records")` 才 404 → `throw IOException("读不到云端记录目录，请检查账号或网络")`（`SyncEngine.kt:170-171`）。
2. **本机 `dirsReady` 仍是 true**（`sync/SyncStateStore.kt:111`），`SyncEngine.kt:128-133` 的三次 MKCOL 被整段跳过 —— 新云盘上根本没人去建 `thecutestgirl/{records,photos}`。
3. **`sync_state` 增量账本是本机记的**，换服务器不会自己清：`file_sig` / `file_confirmed` / `notified_ids` / `dirs_ready`（`SyncStateStore.kt:29-32`）+ `settings` 里的 `first_sync_done`（`SettingsStore.kt:64`）。跨云盘沿用会出三类错：新旧云盘恰好同名同时间同长度的记录被误跳过、`dirsReady` 跳过建目录、`notified_ids` 串台导致该弹的通知不弹。

### 做法
| 位置 | 改动 |
|---|---|
| `data/SettingsStore.kt` | 新增 `davConfigChanged(context,url,user,pass): Boolean`——归一化必须与 `saveDavConfig` 完全一致（`url.trim().ifBlank{DEFAULT_DAV_URL}` / `user.trim()` / `pass` 不 trim），否则「前后带空格重存」会被误判成换端点；比较基准是「已生效配置」，不需要另存一份。密码也一起比（换密码 = 重新授权，顺手重置代价极小） |
| `data/SettingsStore.kt` | 新增 `clearFirstSyncDone(context)`（`edit().remove("first_sync_done")`）——换云盘后新服务器上的历史要当「安静的全量拉取」，否则 `firstTime=false` 会把对方所有老记录逐条弹通知 |
| `sync/SyncStateStore.kt` | 新增 `resetForNewEndpoint(context)`：清掉 `file_sig`/`file_confirmed`/`notified_ids`/`dirs_ready` 四个键。用 **`commit()` 同步落盘**并加 `@Suppress("ApplySharedPref")`——紧随其后就是 `enqueueNow`，`apply()` 会和 Worker 抢先后；不动本地 Room 库与 `files/photos/` |
| `sync/SyncEngine.kt` | companion 新增 `fun applyDavConfig(context,url,user,pass): Boolean`：`davConfigChanged` → `saveDavConfig` → 若变了则 `resetForNewEndpoint` + `clearFirstSyncDone` → 返回 changed。把「写配置」和「判断是否换端点」收在一个入口，设置页不再自己 `saveDavConfig` |
| `sync/SyncEngine.kt` | `testConnection(prepareDirectories: Boolean = false)`：原来只测根目录；当 `changed=true` 时顺手 `mkcol` 三层目录、再 `listDir("thecutestgirl/records")` 真验一次，成功才 `setDirsReady(true)`。这样「手动去新云盘建文件夹」这步也省了，且测试按钮不再报假的「成功」 |
| `ui/settings/SettingsScreen.kt` | 「测试并开启同步」的 `onClick` 改为 `val changed = SyncEngine.applyDavConfig(...)` → `SyncEngine(context).testConnection(prepareDirectories = changed)`，成功才 `ensurePeriodic` + `enqueueNow` + 请求通知权限 |
| `app/build.gradle.kts` | `versionCode = 33` / `versionName = "2.9.2"` |

### 两个必须记住的坑
- **`syncOnceLocked()` 里的 `if (!dirsReady) { 三次 mkcol }` 仍然是主路径**：把 `dirsReady` 清成 false 就等价于「下一轮重新发 MKCOL」。`testConnection(prepareDirectories=true)` 只是让设置页当场把目录建好、不用等第一次后台同步，两条路径都要留。
- **换云盘 ≠ 搬历史**：`markClean` 把老记录标成「已同步」、`dirty=0`，`SyncEngine.kt:320-344` 只传 dirty 记录，所以新云盘的 `records/` 是空的（装机实测 `0` 个文件），本地库 6+2+2 条与 2 张照片原样保留。要让对方也能看到历史，仍得手动把旧云盘的整个 `thecutestgirl/` 文件夹复制到新云盘同一级。改动只解决「换完能连、能建目录、不误跳过、不狂弹通知」，不负责重传历史。

### 验证
- `compileDebugKotlin` + `compileDebugAndroidTestKotlin` + `lintDebug`：**0 errors / 6 warnings**（`resetForNewEndpoint` 的 `commit()` 会触发 `ApplySharedPref`，已用 `@Suppress` 压掉，保持 6 条基线）
- 回归 **24 → 26 项，26/26 通过**（证据 `.verification/tests-v292.txt`），新增两条：`endpointSwitchResetsLedger()`（换随机 URL、故意不建 `thecutestgirl`，调 `applyDavConfig` 后断言五个状态全清、目录被自动建出、新记录能同步）与 `sameEndpointKeepsLedger()`（相同配置 / 前后带空格重存都断言 `!changed` 且账本还在）
- 装机实测：设置页把地址从 `/notify-test/` 改成 `/switch-a/`（新账号根已存在、`thecutestgirl` 不存在）→ 点「测试并开启同步」→ `.verification/dav/switch-a/thecutestgirl/{records,photos}` 自动建出；`sync_state.xml` 从「5 条指纹 + dirs_ready=true + notified_ids + file_confirmed」变成「空指纹 + 空确认集 + dirs_ready=true」、`notified_ids` 清空；`webdav_url` 已切到新端点；本应用无挂出通知（首次静默）、`logcat -b crash` 0 条。截图 `.verification/screens/v292-settings-initial.png` / `v292-settings-switched.png`
- APK `app/build/outputs/apk/debug/the_cutest_girl_v2.9.2_debug.apk`（19,355,069 字节，versionCode 33，applicationId `com.thecutestgirl.app`）

---

## 上一轮（v2.9.1）：修「去设置」点击无效

### 需求（用户原话）
「设置点击后台不被冻结的去设置按钮点击无效」

### 根因（两条，都在 Manifest / 跳转判定，不在 UI 布局）
1. **主因**：`app/src/main/AndroidManifest.xml` 缺 `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`。
   AOSP 的 `com.android.settings/.fuelgauge.RequestIgnoreBatteryOptimizations.onCreate()` 会检查调用方有没有声明这个权限，
   没有就直接 `finish()`。logcat 里能看到
   `START u0 {act=android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dat=package: cmp=...} ... result code=0`，
   但 2 秒后 `topResumedActivity` 仍是本 App —— 用户看到的就是「点了没反应」。
2. **次因**：原来用 `runCatching { startActivity }.isSuccess` 判断成败，可是「有 resolver 但对方 Activity 立刻 finish」
   **不会抛异常**，所以回退链永远走不到，连第二级白名单列表都出不来。
3. 附带风险：Android 11+ 的包可见性过滤会让 `resolveActivity` 查不到系统设置页 → 回退断在第一步。

### 做法
| 位置 | 改动 |
|---|---|
| `AndroidManifest.xml` | 补 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限（`tools:ignore="BatteryLife"`，两个人的私人 App 不上架 Play）；`<manifest>` 加 `xmlns:tools` |
| `AndroidManifest.xml` | 加 `<queries>` 三个 intent：`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`+`package`、`IGNORE_BATTERY_OPTIMIZATION_SETTINGS`、`APPLICATION_DETAILS_SETTINGS`+`package`（该 intent 带 `tools:ignore="BatteryLife"`，否则 Lint 报第 7 条 warning） |
| `ui/settings/SettingsScreen.kt` | `SettingsActionRow` 的 `onClick` 收敛到 `context.openBatteryOptimizationSettings()`；新增 `private fun Context.openBatteryOptimizationSettings()`，内部 `launch(intent)` 先 `resolveActivity(...) != null` 再 `startActivity(...FLAG_ACTIVITY_NEW_TASK)`，全程 `runCatching{}.getOrDefault(false)` |
| 同上 | **三级回退**：直接弹确认框（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，带 `@Suppress("BatteryLife")`）→ 系统白名单列表（`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`）→ 应用详情页（`ACTION_APPLICATION_DETAILS_SETTINGS`）→ 全失败 `Toast`；先判 `isIgnoringBatteryOptimizations()` 已在白名单就直接返回 |
| `app/build.gradle.kts` | `versionCode = 32` / `versionName = "2.9.1"` |

### 两个必须记住的坑
- **`tools:ignore` 要同时打在权限声明和 `<queries>` 里那个 intent 上**，只打权限那处 Lint 仍会报 `BatteryLife`（指向 queries 的 action 行）。
- **补权限后 `dumpsys package` 要能看到 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: granted=true`**，否则说明没生效。

### 验证
- `compileDebugKotlin` + `lintDebug`：**0 errors / 6 warnings**（回落一档，BatteryLife 两处都压掉了）
- 装机实测：点「去设置」→ `topResumedActivity=com.android.settings/.fuelgauge.RequestIgnoreBatteryOptimizations`，屏幕弹出「Allow the cutest girl to ignore battery optimizations?」确认框（`.verification/screens/v291-battery-dialog.png`）；点 ALLOW 后 `dumpsys deviceidle whitelist` 出现 `user,com.thecutestgirl.app,10148`，返回设置页显示「已加入白名单」（`v291-settings-whitelisted.png`）
- 24/24 回归通过（`.verification/tests-v291.txt`）；`logcat -b crash` 本应用 0 条
- APK `app/build/outputs/apk/debug/the_cutest_girl_v2.9.1_debug.apk`（19,355,057 字节）

---

## 上一轮（v2.9.0）：同步提速 + 通知真正能收到

### 需求（用户原话）
「我觉得在软件中上传内容之后更新的太慢了，有时候对方要好几分钟才能看到，而且不打开软件的话基本不会收到系统通知，能优化一下吗？」

### 三条根因（都不是"代码写错了"，是设计没做）
1. **每轮同步全量重下**：`SyncEngine` 阶段 1 对云端 records 目录下**每一个** JSON 都发 GET，不看有没有变。
   记录攒到几百条就是几百次往返，对方刚传的那条排在这些往返后面。
   `DavEntry.lastModified` 解析了却从未使用（`grep lastModified` 只命中 `WebDavClient` 自己）。
2. **photos 目录被 PROPFIND 两次**（阶段 1 一次、阶段 4 瘦身又一次），外加开头 3 次 MKCOL 无条件发送。
3. **后台被系统限流**：`PeriodicWorkRequestBuilder(15, MINUTES)` 是 WorkManager 的**最小周期**，
   Doze / 厂商省电会把实际间隔拉长到几十分钟甚至更久；没有前台服务、没有开机自启。
   通知渠道是 `IMPORTANCE_DEFAULT`——只在通知栏躺着，不弹横幅，用户不主动下拉就永远看不见。

### 做法
| 位置 | 改动 |
|---|---|
| 新增 `sync/SyncStateStore.kt` | SharedPreferences `sync_state`，存「文件名 → `服务器时间:字节数`」指纹、已确认集合、已通知 id、目录已建标记 |
| `SyncEngine` 阶段 1 | 指纹 + 已确认 + 本地有且不 dirty → **跳过，不发 GET**；未跳过时记下指纹，同一指纹第二次见到才确认稳定 |
| `SyncEngine` 开头 | MKCOL 包进 `if (!SyncStateStore.dirsReady(context))`，三个目录只建一次 |
| `SyncEngine` 阶段 4 | 删掉第二次 `listDir("photos")`，复用阶段 1 那份 |
| `SyncManager` | `ExistingPeriodicWorkPolicy.KEEP` → `UPDATE`；新增 `SyncRunner.runOnce`（WorkManager 与前台轮询共用）；新增 `BootReceiver`（开机/应用更新后重挂周期任务）；新增 `SyncForegroundPoller`（**前台每 20 秒**） |
| `PartnerNotifier` | 渠道 `IMPORTANCE_HIGH` + 震动 + `PRIORITY_HIGH`/`CATEGORY_MESSAGE`；按 `id@updatedAt` 去重；点通知直达对应 tab + 日期 |
| `MainActivity` | 读 `EXTRA_ROUTE`/`EXTRA_DATE` 传给 `AppRoot`；`onNewIntent` 也接（App 在前台时不重建 Activity） |
| `AppRoot` | `LaunchedEffect(notificationTarget)` 切 tab + 传日期；`repeatOnLifecycle(RESUMED)` 里跑前台轮询 |
| `SettingsScreen` | 新增「后台不被冻结」分组，一键跳 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，`ON_RESUME` 重读状态 |

### 两个必须记住的坑
1. **通知渠道 id 必须换新**（`partner_updates` → `partner_updates_v2`）。
   Android 的规矩：渠道建好后**重要级别就钉死了**，再传更高的 IMPORTANCE 系统直接忽略。
   老用户手机上旧渠道早以 DEFAULT 建好，只改 `IMPORTANCE` 常量等于什么都没改。旧渠道顺手 `deleteNotificationChannel`。
2. **点通知跳日期必须同时改月份**。`OutfitViewModel.jumpToDate(date)` = `_month` + `_selectedDate` 一起落。
   只调 `setDate` 的话，列表是按月订阅的（`observeByMonth`），十月的记录根本不在查询结果里，
   点完通知看到的还是当月——本轮实测复现过。
   另外守卫要存**日期字符串**而不是布尔值：App 开着时连来两条通知，布尔守卫会让第二次点击毫无反应。

### 验证
- `./gradlew :app:assembleDebug :app:lintDebug --offline` 通过，**lint 0 errors / 7 warnings**（新增 1 条 `BatteryLife` 警告，是白名单入口的已知代价）
- 回归从 22 项增到 **24 项**，`24/24 tests passed`（证据 `.verification/tests-v290.txt`）
  - 新增 `incremental sync stops re-downloading untouched records`：造 5 条 → 首轮下 5 → 三轮后 0 → 只改 1 条 → 只下 1 → 再回 0
  - 新增 `an unconfirmed file signature is still downloaded`：清空 `file_confirmed` 后必须重新发 GET（靠测试服务器的 `get-log` 计数，不能用 `Result.downloaded`——那个值只在"真的合并了新内容"时才加一）
- 前台轮询实测：往云端塞一条新记录 → **22 秒**后出现在本地库里
- 通知实测：`dumpsys notification` 显示 `importance=4 channel=partner_updates_v2`、标题「💗 他 发来了新内容」；点通知跳到 **2026年10月** 且选中 15 日
- 截图：`.verification/screens/v290-notify4.png`（通知横幅）、`v290-jump-oct.png`（跳转后停在 10 月 15 日）、`v290-settings-bottom.png`（设置页新分组）

### 仍做不到的（已写进设置页文案，别再承诺）
- 后台**最快就是 15 分钟**，这是 Android 的硬限制，加前台服务也换不来秒级推送
- 真正秒级要 FCM / 自建推送服务，需要云服务和实名，用户已否决过付费方案
- 各厂商 ROM 的省电策略差异极大，白名单能改善但不能保证

## 本轮改动（v2.8.3）：撤销「备注作者判权限」—— 回到「备注归上传人，评论归对方」

### 需求（用户原话大意）
「不是我的意思是我之前上传的备注被误归到对方的备注了，不是让你改成对方的备注我也能修改，改回去。
还改成备注区改为两人都可发送：上传照片时有一个备注区供上传人写入，再设置一个评论区供对方评论。
上传人备注区仅供上传人写入，评论区仅供对方评论写入。」

### 为什么 v2.8.2 错了
v2.8.2 按 `noteDevice` 判「谁写的谁能改」，本意是修「对方传的照片 + 我写的备注被锁成只读」。
但这条规则一旦成立，**评论者就能改上传人的字**——恰好把用户最在意的那句话的归属权搞反了。
用户要的不是「谁写谁能改」，而是**两个区各归一个人**：备注区永远归上传人，评论区永远归对方。
两人各写各的，谁也改不了对方那句，这才是不会打架的模型。

### 做法：删掉 `canEditNote`，只留 `amUploader`
| 位置 | 现在 |
|---|---|
| `noteEditable` | `vm.amUploader`（我是上传人才能写备注） |
| `replyEditable` | `!vm.amUploader`（我不是上传人才能写评论） |
| `noteAuthorName` | `if (amUploader) "" else editingOriginName.ifBlank { "ta" }`（我上传时不贴标签） |
| `replyAuthorName` | `originalReplyName`（评论作者昵称，空则不贴标签） |
| `canSave` | 两分支：上传人看照片/备注是否完整，对方只看 `reply != originalReply` |
| `save()` | `if (!amUploader && latest != null)` 只写评论，一个字都不碰上传人的照片和备注 |

- `data/Records.kt` 的 `noteDevice` / `noteName` **保留在 schema 里**（不为撤销再加一次迁移），
  但不再参与权限判定，也不再被 ViewModel 当作编辑依据。
- `ui/components/Common.kt` 的 `PhotoNoteCard` / `CommentField` KDoc 重写为
  「`noteEditable` = 我是不是上传人」「`replyEditable` = 我是不是对方」。

### 回归（仍是 22 项，换掉 2 项）
删掉 `noteAuthorIsNotTheUploader` / `legacyNoteStaysEditable`，换成：
1. `partner cannot edit the uploader's note from the editor`（`uploaderNoteIsReadOnly`）——
   用真实 `OutfitEditViewModel` 加载对方记录，**程序性**把 `vm.note` 改成「我想偷偷改他的备注」，
   save 后断言 `result.note` 仍是原值、`result.reply` 变成我写的那句、`result.origin` 不变。
   这条同时锁 UI 与保存路径两道门。
2. `note block is labelled with the uploader's nickname`（`noteLabelFollowsUploader`）——
   对方记录 `noteAuthorName == "Bob"`；自己的记录 `noteAuthorName.isBlank()`。

### 验证
`./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --offline` 通过；
22/22 instrumented 回归通过（证据 `.verification/tests-v283.txt`）；
`grep -rn canEditNote app/src/` 无残留。
截图：`.verification/screens/v283-edit-partner.png`（对方记录：只读「他 的备注」+ 可写评论区）、
`v283-edit-mine.png`（自己的记录：备注直接可写、不贴标签 + 只读「他 的评论」）、
`v283-outfit.png` / `v283-outfit2.png`（列表页 💬 评论胶囊与 ♡ 来源胶囊）。

## 本轮改动（v2.8.2）：备注作者单独存 —— 「对方传的照片 + 我写的备注」

### 需求
v2.8.1 用「记录是不是我上传的」决定备注能不能改。结果：**对方上传的照片、我写的备注，升级后变成只读，还贴上了对方的昵称**。

### 根因
`origin` 是**照片上传人**，不是**备注作者**。v2.7 及以前只有一个 `note` 字段、两人共写，
「A 传照片 + B 写备注」这种组合在历史数据里到处都是。拿 `origin` 判权限，等于把备注判给了照片的主人。

### 做法：作者单独两个字段
| 字段 | 含义 | 用途 |
|---|---|---|
| `noteDevice` | 写这段备注的设备 ID | **判权限**：空 / 等于本机 → 能改 |
| `noteName` | 写这段备注时的昵称 | **贴标签**：`X 的备注` |

- `noteDevice.isBlank()` = v2.8.1 之前的老数据 → **谁都能改、不贴标签**（`legacyNoteStaysEditable` 用例锁死这条）。
- 合并时署名跟着内容走：`noteFromRemote` 为真才采纳远端作者，否则保留本地（`mergeFields`）。
- **`needsUpload` 必须覆盖「内容一样但本地多带了署名」**：否则作者信息永远只活在本机，换台手机又变回「ta 的备注」
  （`noteAuthorIsNotTheUploader` 第二轮同步断言的就是这条）。

### 改动清单
- `data/Records.kt`：三实体加 `noteDevice` / `noteName`；`toJson()` / `parseSyncRecord` 同步
- `data/AppDatabase.kt`：version 4→5，`MIGRATION_4_5` 三表各加两列（DEFAULT ''）
- `data/Daos.kt`：`setNote(id, note, name, device, now)`
- `sync/SyncEngine.kt`：`MergedFields` 加两字段；`mergeFields` 加 4 个参数；`noteAuthorStale` / `replyAuthorStale` 计入 `needsUpload`
- 三个 ViewModel：`canEditNote` / `noteAuthorName` / `replyAuthorName`；`canSave` 三分支（上传人 / 可改备注 / 只能写评论）；
  保存时**没改过的字段保持原时间戳**，否则等于用旧内容抢新版本
- `ui/components/Common.kt`：`PhotoNoteCard` 加 `noteAuthorName` / `replyAuthorName`，标签 `"$it 的备注"` / `"$it 的评论"`
- `ui/event/EventEditScreen.kt`：纪念日分支 `canEditNote` 决定 `PlainField` 还是 `ReadOnlyNote`
- 回归 20 → **22 项**（新增 `noteAuthorIsNotTheUploader`、`legacyNoteStaysEditable`）

### 验证
`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline` 通过；
22/22 instrumented 回归通过（证据 `.verification/tests-v282.txt`）；
截图 `.verification/screens/v282-partner-note.png`（对方传的照片 + 他的备注显示为只读「他的备注」，我的评论「nice」可编辑）。

## 上一轮改动（v2.8.1）：记录下的「评论」—— 两个人可以同时写字而不互相覆盖

### 需求
之前一条记录只有**一个** `note` 字段，两个人都能改。谁后保存谁覆盖，另一方那句就没了；
界面上也分不清「这句是他写的还是我写的」。

### 做法：把「写字的位置」拆成两个字段
| 字段 | 谁能写 | 谁只读 | 时间戳 |
|---|---|---|---|
| `note` / `description`（备注） | **上传人** | 对方 | `noteUpdatedAt` |
| `reply`（评论） | **对方** | 上传人 | `replyUpdatedAt` |

- 每个字段**只有一个写者**，所以按字段比时间就能无损合并（`SyncEngine.mergeFields`）。
  记录级 `updatedAt` 只留给「整条只有一个写者」的字段（照片 / 日期 / 标题 / 昵称）。
- `replyName` 单独存评论作者昵称。**不能拿 `originName` 顶替**——`originName` 是上传人，
  自己上传的记录收到评论时会显示成「我的评论」（v2.8.0 的真实 bug，v2.8.1 修）。
- 数据库 v2→v3 加 `reply` / `noteUpdatedAt` / `replyUpdatedAt`，v3→v4 加 `replyName`，
  全部 `ALTER TABLE ADD COLUMN + DEFAULT`，老记录 `noteUpdatedAt = 0` 读取时回退到
  `updatedAt`（`noteVersionOf`），升级后第一轮同步不会把老备注当成「刚改过」。

### 界面
- `PhotoNoteCard` 多出 `noteEditable` / `reply` / `onReplyChange` / `replyEditable` / `photoEditable`。
  上传人看到「备注可写 + ta 的评论只读」；对方看到「ta 的备注只读 + 评论区可写」，
  且**不能换掉上传人的照片**。
- 备注区收成 41dp 单行入口，点开才铺三行（`TextNoteBlock`，`animateContentSize`）。
- 列表页：饮食/点滴卡片在正文下方用「左侧主色细线 + 小标签 + 正文」显示评论；
  穿搭缩略图只在左下角挂一个 💬 气泡（4:5 格子里塞两行字会糊住照片主体）。
- 纪念日没有 `PhotoNoteCard`，用 `ReadOnlyNote` + `CommentField` 两块独立卡片。

### 回归
`SyncRegressionRunner` 从 17 项增到 **20 项**，新增：
1. `uploader note and partner reply both survive one sync round`
2. `partner reply alone does not touch the uploader's note`（含 `replyName` 断言）
3. `reply written during an upload is not lost`
全部通过（`.verification/tests-v281.txt`）。既有 14 项里的时钟偏差用例改为「对方写的是评论」，
并额外断言「对方不得覆盖上传人的备注」。

## 本轮改动（v2.7.0）：顶栏动画真正解耦布局 + 「事件」模块更名「点滴」

### 1. 顶栏胶囊动画：A/B/C/E 四项（`Glass.kt` 的 `GlassTopBar`）
| 项 | 做法 | 为什么 |
|---|---|---|
| A | `glassMaterial(..., enabled = false)` 时**整条 Haze 管线不挂**，改铺 `disabledBackground` 静态色 | 原来只把 `blurRadius` 设 0，离屏层/采样/合成照样跑 |
| B | `liveBlur = if (isAnimating) 0.dp else GlassBlurRadius`（去掉 `tween(120)`） | blur 30→0 的那 120ms 里它仍非 0 且在变，RenderNode 照样每帧重建 |
| C | 用自定义 `Layout`：内容**只按展开宽度测量一次**，布局宽度每帧跟 `barWidth` 变，溢出由 `.clip()` 裁掉 | 原来 `.width(barWidth)` 把约束传给子树 → 标题 Text、副标题、`DateNavRow`、`DayStrip` 的 LazyRow 每帧全重排 |
| E | `Row` 的 `verticalAlignment` 恒为 `Top` | 原来在 `collapsed` 翻转瞬间切 `CenterVertically`，而那时胶囊还是满高 → 标题先跳一下 |

⚠️ **踩坑（必读）**：C 一开始想用 `Modifier.requiredWidth(expandedWidth)`，编译通过但**运行不对**——胶囊里标题被压成 5px 宽（`uiautomator dump` 看到 text 节点 bounds 只有 5px）。
换自定义 `Layout` 后才对：
```kotlin
Layout(
    content = { Column(Modifier.padding(bottom = 2.dp), content = content) },
    modifier = Modifier.width(barWidth).glassMaterial(...)
) { measurables, constraints ->
    val placeable = measurables.first().measure(Constraints.fixedWidth(expandedWidth.roundToPx()))
    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
}
```
**教训**：`requiredWidth` 在「父节点用 `Modifier.width()` 固定了尺寸」时行为不可预期，别指望它做「测量与布局解耦」；要确定性就用 `Layout` 自己写 measure/place。

### 2. 144Hz 适配结论
Compose 的 `Animatable` / `tween` 是**时长驱动 + vsync 对齐**，300ms 动画在 144Hz 上就是 ~43 帧，不需要为刷新率改任何代码。
真正的门槛是每帧预算从 16.7ms 缩到 **6.94ms**，所以 A/B/C 不是可选优化，是跑满 144Hz 的前提。
真机复核（模拟器上 `dumpsys gfxinfo` 恒为 `Total frames rendered: 0`，软件渲染不收集）：
```
adb shell dumpsys gfxinfo com.thecutestgirl.app framestats   # 解析 FrameCompleted - IntendedVsync
```

### 3. 「事件」模块更名「点滴」（仅用户可见文案，未动表名/类名/路由）
- `AppRoot.kt` tab label、`EventScreen.kt` 页标题与照片 contentDescription
- `EventEditScreen.kt`：「记一条点滴」「编辑点滴」「删除这条点滴」
- `Records.kt` `typeLabel` = "点滴"（通知摘要里的「穿搭 ×2 · 点滴 ×1」）
- `SyncManager.kt` 通知频道描述、`SettingsScreen.kt` 云同步说明文案

## 本轮改动（v2.6.2）：顶栏胶囊掉帧优化 + 底栏粉色液态玻璃磨砂

两件事一起做的：一是把顶栏收缩动画的掉帧压下来，二是把底栏从「中性灰白」改成「粉色液态玻璃磨砂」。
**没动业务逻辑、同步协议、数据结构**（回滚用 v2.6.1 的 APK 覆盖安装，数据无损）。

### 1. 掉帧：动画期间把模糊冻到 0（`Glass.kt` 的 `GlassTopBar`）
- **根因**：`hazeChild` 的模糊层尺寸依赖节点尺寸。胶囊宽度每帧都在变 → RenderNode 每帧重建 + 每帧重新采样 30dp 的背景 → 这是最大的一笔开销；
  其次是 26sp 中文标题每帧重新断行、`animateContentSize` 与外层宽度动画叠加导致每帧两次 layout、阴影每帧重绘。
- **做法**：
  1. `animateDpAsState` → `Animatable(initialValue = …, typeConverter = Dp.VectorConverter)`，用它暴露的 `isRunning` 判断「正在动」
  2. `val liveBlur by animateDpAsState(if (isAnimating) 0.dp else GlassBlurRadius, tween(120))`
  3. 阴影也锁：`val shadowElevation = if (isAnimating) 6.dp else barElevation`
  4. 收起态标题传 `softWrap = false`（`EmojiTitle` 新增参数），不参与断行计算
- ⚠️ 踩坑：`Animatable(if (…) A else B)` 不写 `typeConverter` 会被推断成 `Animatable<Float>`，随后 `.width(barWidth)` 直接编译不过
- ⚠️ 量化受限：本机模拟器 `dumpsys gfxinfo` 恒为 `Total frames rendered: 0`（软件渲染不收集 HWUI 统计），
  真实机型复核方法见 `docs/perf-ui-spec-v2.6.1.md`：`dumpsys gfxinfo <pkg> framestats` 解析 `FrameCompleted - IntendedVsync > 16.67ms` 的比例

### 2. 底栏粉色液态玻璃磨砂（`GlassBottomBar.kt` + `pinkGlassTints`）
- `Glass.kt` 新增 `internal fun pinkGlassTints(isDark, surface, primary)`：浅色 `surface@62% + primary@22% + White@10%`，深色 `surface@58% + primary@18% + White@6%`
- ⚠️ 关键：粉色**不直接铺**。直接用 primary 打底会和奶油底 `#F1EBE5` 打架，下拉穿透的内容也会整体发红；三层叠出来才是奶粉调而不是芭比粉
- `glassMaterial` 新增 `tints: List<HazeTint>? = null`，传了就完全替代默认 surface 着色（顶栏与卡片不传，仍走 `tintScale`）
- 模糊 26dp、噪点 **0.09f**（磨砂的关键是颗粒，不是单纯模糊）
- 选中块：`primary@16%`（深色 22%）糖浆 + 白色顶部高光 `32%`（深色 18%）+ 白色描边 `30%`（深色 22%）
- 图标：选中 `scheme.primary`、未选中 `scheme.onSurfaceVariant` —— 粉色只在选中那一格出现，三格同时上色会糊成一片

## 本轮 UI 改版（v2.5.3 / v2.5.4）：B 档结构 + C 档视觉资产 + 底部导航栏重做

在 v2.5.2 的 A 档（明暗分层/实体卡/空态/排版/设置页）之上继续做 B 档、C 档，并补上 A 档当时跳过的第 4 条模块配色。
**仍未动业务逻辑、同步协议、数据结构**（回滚用 v2.5.2 的 APK 覆盖安装，数据无损）。

### 1. 模块配色（新增 `ui/theme/Accent.kt`）
- `enum class ModuleKind { Outfit, Meal, Event }` + `moduleAccent()` / `moduleAccentSoft()` / `moduleGlowColor()`
- 穿搭玫瑰 `#B25A79`（深色 `#E8A9BF`）、饮食暖橙 `#C07545`（深色 `#E9B58C`）、事件玫红 `#C4527A`（深色 `#EFA1BC`）
- ⚠️ 约束：三个色相都落在粉→橙段、饱和度接近，**只用在大面积小、信息密度高的位置**（空态柔光、分组标题短竖条、餐次图标底、纪念日大卡数字）；
  大块背景与主按钮一律仍用 `colorScheme.primary`，否则三个页面会散成三个 App

### 2. C 档：毛毡空态插画（`EmptyHint` 升级）
- 签名扩为 `EmptyHint(text, modifier, icon, title, hint, action, accent, showFeltIllustration = true)`
- 默认用 **112dp 柔光圆 + 88dp 圆角方形 `R.mipmap.ic_launcher_foreground`**：直接复用启动图标那张毛毡玩偶合照，
  既统一了视觉资产，又不必额外画插画；`showFeltIllustration = false` 时退回原来的圆底线性图标
- 三页均传各自的 `accent`

### 3. C 档：模块色柔光（`Modifier.moduleGlow`）
- `ui/components/Glass.kt` 新增 `moduleGlow(color, height = 260.dp)`：顶部竖向渐变（强调色 10% → 透明）
- 必须放在 `.haze(pageHaze)` 的**子树内**（作为内容区第一层），否则玻璃没有东西可模糊
- 副作用是好的：首屏背后不再是一片纯色，悬浮顶栏终于有「玻璃」味道

### 4. B 档：顶栏高度改实测（三个页面统一）
- 删掉 `OutfitTopArea = 196.dp` / `MealTopArea = 140.dp` / `EventTopArea = 108.dp` 三个手算常量
- 改为 `var headerHeightPx by remember { mutableIntStateOf(0) }` + `Modifier.onSizeChanged { headerHeightPx = it.height }`，
  内容起点 `topArea = headerHeightPx.toDp() + 10.dp`
- 理由：常量是按字号和 padding 手算的，改一次字号就得重算，算错就是首条内容被玻璃盖住；实测后折叠动画期间留白连续跟随
- 顺带压缩 `DateNavRow`（`IconButton` 44→**40dp**、去掉行内多余 padding）与 `DayStrip`（chip 48→44dp、圆角 16→14dp、竖向 8→6dp）

### 5. B 档：饮食三卡差异化（`MealScreen.kt`）
三种重量级，一眼分清归属：
- **未记录** = `Modifier.dashedCard(cornerRadius = 16.dp)`（`Glass.kt` 新增：`surface` 80% 底 + 虚线 `PathEffect.dashPathEffect(5dp, 4dp)` 描边）
  + 主色 `+ 记午餐` 文案 + 右端 `我的`
- **已记录（我）** = `solidCard` 照片大卡，整卡可点进编辑
- **对方** = 同 `solidCard`，但 `drawBehind` 在左缘画一条 3.5dp 强调色竖条（圆角交给 `solidCard` 的 clip 切）
- `MealDuotoneIcon` 底色从 primary → `moduleAccentSoft(ModuleKind.Meal)`

### 6. B 档：纪念日横向大卡（`EventScreen.kt`）
- 从 164dp 窄竖卡改成 **292dp 横向大卡**：左「第 / 1258 / 天」（44sp SemiBold，强调色），右「名称 + 从 X 开始 + 备注」
- 左缘叠一层 `Brush.horizontalGradient(强调色 12% → 透明)`，卡片有方向感
- 选横向的理由：窄卡里「第/数字/天」三行挤在 164dp 里，数字一大就贴边；横卡能让数字当主角，同时一屏露出下一张卡边缘，明示可滑
- `SectionLabel` 前加 3dp 强调色短竖条（13dp 高、2dp 圆角）

### 7. B 档：转场与列表动画
- `AppRoot.kt` 的 `NavHost` 加 SharedAxis 简化版四段转场：
  `enter = slideInHorizontally(300ms, initialOffsetX = { it / 8 }) + fadeIn(220ms)`、
  `exit = fadeOut(140ms) + scaleOut(0.98f, 200ms)`、`popEnter = fadeIn(200ms) + scaleIn(0.98f, 240ms)`、`popExit = slideOutHorizontally(240ms, { it / 8 }) + fadeOut(180ms)`
- 列表项加 `Modifier.animateItem()`（穿搭 `OutfitDaySection`、事件 `MomentCard`）：同步把对方记录合并进来时淡入 + 让位，不再硬跳

### 8. 底部导航栏重做（v2.5.5，对齐酷安）
用户先反馈「那个玻璃框大小跟按钮一样」，随后给了酷安的底栏截图作参照。

**第一次改（v2.5.4，方向错了）**：只把栏加高到 72dp、滑块缩到 62×54，配色仍是粉色胶囊。
问题在于酷安那块选中块**不是彩色小胶囊**，而是一块**比图标大得多、中性灰白、半透明的大圆角块**；
而且它的图标和文字**全是深色**，选中只靠「块」表达，不靠颜色。

**最终做法（v2.5.5）**：
- 栏高 64 → **76dp**，`cornerRadius` 30 → **38dp**，接近半高，端头是完整半圆
- 玻璃上再铺一层 `scheme.surface` 45% 的纱（`drawBehind` 里画在 haze 之上、内容之下）：
  让底栏从「能看清底下内容的透片」变成「近白实体胶囊」，这是酷安最显著的特征
- 滑块 68×42 → **88×56dp**：占栏高 74%、宽度盖住图标 + 文字，上下各留 10dp
- **配色中性化**：`body = onSurface 7.5%`（深色 `White 10%`）、`highlight = White 22%`、`edge = onSurface 5%`；
  不再用粉色，粉色品牌色只留在 FAB 上，导航栏安静下来，层级反而更清楚
- **图标与文字全走 `onSurface`**：选中 100% + `FontWeight.SemiBold`，未选中 68% + `Medium`；
  图标仍保持 Outlined / Filled 双态（形状差异是第二重提示）
- 图标 22 → **23dp**，标签 10.5sp；tab 内容为 `Column(Icon, 3dp, Text)`
- 液态拖动（长按脱离吸附、速度拉长变薄、橡皮筋、松手 spring 吸附）**逻辑完全保留**，只是作用在更大的块上
- 视觉坑：滑块离栏端太近时会被 38dp 圆角裁掉一个角，看起来「块偏左」；所以宽度收到 88dp（左端留 16dp）后才正常

### 9. 底部导航栏第三轮：图标化 + 与滑块等大 + 更通透（v2.5.6）

用户随后以一份「液态玻璃导航栏 UI 优化专家」的规则集收敛了要求，三条硬性约束推翻了 v2.5.5 的方向：

1. **尺寸一致**：导航栏整体大小必须与滑动按钮块**基本一致**
2. **透明度优先**：玻璃背景要比原来**更透明**
3. **纯图标导航**：不保留任何文字标签

**落地（`ui/components/GlassBottomBar.kt`）**：
- 滑块 `88×56` → **`68×46dp`**（只包住图标）；栏高 `76` → **`56dp`**、`cornerRadius` `38` → **`28dp`**
  → 栏与滑块只差 10dp（上下各 5dp），是「同一体量的两块玻璃」，不再是「一块大板 + 一个小块」
- **去掉文字**：tab 内容从 `Column(Icon, Spacer, Text)` 改成单个居中 `Icon(24.dp)`；
  `contentDescription = tab.label` 保留，无障碍不丢
- **选中态三重表达**（没有文字也不会分不清当前页）：填充图标（`iconSelected`）+ `onSurface` 不透明色 + 背后的滑动块；
  未选中 `onSurface 62%`
- **更透明**：删掉 v2.5.5 那层 45% 白纱；`glassMaterial(..., blurRadius = 22.dp, noise = 0.06f, tintScale = 0.72f)`
  （原 30dp / 0.10f）。为此给 `Glass.kt` 新增 `tintScale` 参数：
  `internal fun glassTints(isDark, surface, tintScale = 1f)`、`Modifier.glassMaterial(..., tintScale = 1f)`——
  **只有底栏打 0.72，顶栏与卡片仍 1f**，避免全站一起发虚
- 液态拖动（长按脱离吸附、速度拉长变薄、橡皮筋、松手 spring 吸附）逻辑完全保留

> 三轮的教训值得记下：v2.5.4 把「玻璃框大小跟按钮一样」理解成「栏太小、滑块太大」，方向正好相反；
> v2.5.5 抓对了酷安的「中性大块」特征，但把栏做得比块大太多；到 v2.5.6 才按「栏≈块 + 图标化 + 更透」收敛。
> **用户给的参考图要先读「什么是它最显著的那一处」，不要先改数值。**

### 10. 底部导航栏第四轮：栏宽贴合按钮 + 热区放大（v2.5.7）

用户圈出底栏右端的空白，要求「横向宽度刚好跟按钮视觉重合」，并同意把可点区域放大。

- **栏宽不再铺满屏幕**：`fillMaxWidth() - 16dp*2` → `wrapContentWidth`，宽度 = **`TabSlotWidth(100dp) × tab 数`** = 300dp
  → 两个半圆端头正好落在首尾按钮的外沿；滑块 84dp 居中于 100dp 单格，与栏端只差 8dp，
  首/尾 tab 选中时滑块的外沿和栏的外沿几乎重合（这正是用户圈出来的那处）
- **滑块** `68×46` → **`84×48dp`**：栏变窄后若滑块不变，单格内留白会重新变大
- **热区放大 8dp**：玻璃仍是 56dp（视觉不变），但把可点层从玻璃里**拆出来做兄弟节点**——
  `Box(宽 300dp × 高 64dp)` 承载 `pointerInput` 与三个 `selectable`，玻璃 `Box` 只负责 `glassMaterial + drawBehind`
  → 触摸高度 56 → **64dp**（每个 tab 100×64dp），且不影响液态滑块绘制层级
  （图标层与玻璃都以 `Alignment.Center` 对齐，图标视觉位置完全没动）
- 结构变化要点：`Row` 从玻璃 Box 的**内容**变成**兄弟**，否则热区撑不出玻璃；
  `drawLiquidSlider` 仍绑在玻璃 Box 的 `drawBehind` 上，用玻璃自身尺寸算单格宽（`size.width / tabCount`）
- 液态拖动整套手势逻辑原样保留，只是 `pointerInput` 挂到了新的 64dp 容器上

## 本轮修复（v2.6.1）：备注输入闪退（P0）

v2.6.0 引入的回归：点 `PhotoNoteCard` 折叠备注行「＋ 写点备注（可选）」100% 闪退，三模块共用组件所以全中。

- 堆栈：`java.lang.IllegalStateException: FocusRequester is not initialized` at `Common.kt:1150`
- 根因：`FocusRequester` 只挂在**展开分支**的 `TextField` 上；点折叠行那一帧 `TextField` 还没组合，
  `requestFocus()` 没有目标节点 → 抛异常
- 修复：点击只置 `pendingFocus`，在展开分支内用 `LaunchedEffect(pendingFocus)` 等 `TextField` 上树后再 `requestFocus()`，
  外层加 `runCatching` 兜底
- 顺带修掉同源第二个 bug：`onFocusChanged` 在 TextField 首次上树时会先回调一次 `isFocused = false`，
  原判断立刻把 `noteExpanded` 打回 false → 表现成「点了没反应」。改成 `wasFocused` 守卫：
  真正拿到过焦点、又失去焦点时才收起
- 验证：点折叠行展开 3 行并弹键盘，`logcat -b crash` 0 条，截图 `.verification/screens/v261-note-focus.png`
- 换色 / 掉帧两项的方案见 `docs/perf-ui-spec-v2.6.1.md`（第二、三节），尚未实施

## 本轮 UI 优化（v2.6.0）：顶部栏收缩胶囊 / 设置入口收敛 / 图文卡间距规范

依据 `docs/ui-optimization-spec-v2.6.md` 的三条规范落地。**没有改动业务逻辑、同步协议、数据结构**。

### 1. 顶部栏滚动收缩成左上角胶囊（`Glass.kt` 重写）
- 删除 `GlassTopBarWithSettings`，新增 `GlassTopBar(hazeState, modifier, collapsed, onOpenSettings, content)`
- 旧实现的病灶：只收起副标题和日期条，面板仍 `weight(1f)` 撑满整行 → 滚到底是一条几乎全宽的白横条
- **`Modifier.weight(1f)` 不支持宽度动画**，改成 `BoxWithConstraints` 拿可用宽度 → `animateDpAsState` 在固定宽度上跑
  （`CollapsedBarWidth = 126.dp` ↔ `maxWidth - GearSlot`），右侧用 `Spacer(weight(1f))` 吃剩余，齿轮才不会被动画中的面板挤出屏幕
- 三条动画：宽度 `tween(300, FastOutSlowInEasing)`、圆角 `26dp ↔ 20dp` 同曲线、elevation `8dp ↔ 4dp` `tween(240)`
- ⚠️ `CollapsedBarWidth` 一开始设 112dp，实测标题被压成「穿…」，改 126dp 才够（emoji 22 + 间距 8 + 两个汉字 52 + 左右内边距 28 ≈ 110，留余量）

### 2. 点击 / 长按手势：只能有一个识别器
- `PageHeader` 新增 `onClick` 参数，与既有 `onLongPress` 一起走**同一个 `combinedClickable`**（需要 `@OptIn(ExperimentalFoundationApi::class)`）
- 踩过的坑：一开始把长按写成 `Modifier.pointerInput { detectTapGestures(onLongPress = …) }` 放在 PageHeader 上，
  外层 `GlassTopBar` 再套一层 `clickable` 处理「点胶囊回顶部」——**子节点先消费手势，外层永远收不到点击**，点了没反应
- 现在：`onClick`（仅收起态非 null，回列表顶部并展开）+ `onLongPress`（进设置）都在 PageHeader 里
- 三页都传 `onClick = if (scrolled) ({ topBarScope.launch { listState.animateScrollToItem(0) } }) else null`

### 3. 设置入口收敛
- 齿轮只在穿搭页（`onOpenSettings` 非 null 才渲染）；饮食 / 事件页传 `null`
- 隐藏入口：**长按任意页的标题行进设置**（`onLongPress = onOpenSettings`），两页仍能就地进设置
- `PageHeader` 右内边距 `6dp` → 收起态 `14dp`（展开态保持 6dp，让日期行的 ▶ 贴右）

### 4. 图文卡（`PhotoNoteCard`）间距与备注折叠
| 位置 | v2.5.x | v2.6.0 |
|---|---|---|
| 卡片内边距 | 0 / 8 / 16 三套混用 | 统一 **12dp** 基线 |
| 单图 | 贴边铺满、顶部两角跟随卡片圆角 | **12dp 缩进 + 12dp 圆角**，不再贴边 |
| 多图 | `padding(8dp)`，格间 8dp | `padding(12dp)`，格间保持 8dp |
| 操作行 | `padding(h12, v10)` | `padding(h12, v12)` |
| 分割线 | 恒显示，`padding(h16)` | **仅 `note.isNotEmpty()` 时显示**，`padding(h12)` |
| 备注区 | `minLines = 3` 恒占 **101dp** 空白 | 空备注折叠成 **41dp** 单行「＋ 写点备注（可选）」，点开才铺开 3 行 |

- 备注折叠实现：`noteExpanded` 状态 + `animateContentSize()`；展开时 `focusRequester.requestFocus()` 直接弹键盘；
  `onFocusChanged` 里「失焦且内容为空」自动收回；`LaunchedEffect(note.isNotEmpty())` 保证编辑页载入已有备注时自动展开
- 折叠态提示文案取 `notePlaceholder.substringBefore('，')`，「比如：……」那半句留给展开态

### 验证
- `assembleDebug` 通过；`lintDebug` **0 errors / 6 warnings**（无新增类型）；`logcat -b crash` 本应用 0 条
- 模拟器逐项核对：滚动后收缩成左上胶囊 ✅ / 点胶囊回顶部并展开 ✅ / 饮食·事件页无齿轮 ✅ / 长按标题进设置 ✅ /
  上传页备注折叠 41dp ✅ / 单图 12dp 缩进 ✅
- 截图：`.verification/screens/v260-*.png`

## 本轮 UI 改版（v2.5.2）：A 档视觉重做 —— 明暗分层 / 实体卡片 / 空态 / 排版 / 设置页

用户反馈「UI 界面看起来不像样」。实测原因是**层次几乎没有**：页面底 `#FAF6F2` vs 卡面 `#FFFDFA` 只有 **1.06:1** 对比度
（人眼要 ≥1.2:1 才分得清两个面），卡片靠 1dp 40% 描边撑形体，看起来像「浮在纸上的铅笔框」。
本轮只做视觉与排版，**未动业务逻辑、同步协议、数据结构**（回滚用 v2.5.1 的 APK 覆盖安装，数据无损）。

1. **配色拉开明度差**（`ui/theme/Color.kt`）
   - 浅色：背景 `#FAF6F2` → **`#F1EBE5`**（奶油燕麦）、卡面 `#FFFDFA` → **`#FFFFFF`**、描边 `#C9BEB8` → `#D2C8C1`
   - 深色：背景 `#171310` → `#15110F`、卡面 `#1D1916` → **`#262019`**、描边 `#59504B` → `#4A423D`
   - 结果：浅色背景/卡面对比 **1.06:1 → 1.18:1**，深色 1.16:1，卡片终于「浮起来」了
2. **新增 `Modifier.solidCard()`**（`ui/components/Glass.kt`，紧跟 `glassPhotoFrame` 之后）
   - 双层柔和投影（`shadow(elevation, ambientColor = onSurface 10%, spotColor = onSurface 14%)`）+ 白底 + 1dp 55% 发丝描边
   - 投影色取暖褐（`onSurface`）而不是纯黑，避免奶油底上发灰；深色模式切纯黑
   - ⚠️ 刻意**不用** `hazeChild`：所有调用点都在 `.haze()` 采样源内部，Haze 硬性要求 hazeChild 不能是 haze 源子孙
   - 全站卡片从这个修饰符出：`OutfitThumb`(16dp/6dp) / `MealContentCard`(18dp/8dp) / `MomentCard`(18dp) / `AnniversaryCard`(18dp)
     / `PhotoNoteCard`(20dp/6dp) / 编辑页日期行(14dp/4dp) / `SyncStatusBar`(14dp/4dp) / 设置页分组(18dp/6dp)
3. **空态重做**（`ui/components/Common.kt` 的 `EmptyHint`）
   - 新签名 `EmptyHint(text, modifier, icon, title, hint, action)`，`text.split('
', limit = 2)` 兼容旧调用
   - 视觉：64dp 主色 12% 圆底 + 28dp 主色图标 + **18sp/27sp Medium 主文案**（新增 `EmptyTitleStyle`）+ 13sp 引导行 + 可选按钮
   - 穿搭空态：`title="这个月还没有穿搭记录"`；事件空态：`title="还没有点滴记录"`
4. **排版重排**（`ui/theme/Type.kt` 全量重写 + `Common.kt` 三处换 style）
   - 新增 `titleLarge 20sp/28sp`、`titleSmall 14sp/20sp`、`bodySmall 13sp/19sp`
   - 中文行高统一 ~1.5 倍（`bodyLarge` 15sp 行高 22→**23**，`bodyMedium` 14sp 20→**21**）
   - `PageHeader` 副标题 `labelMedium`(12sp) → **`titleSmall`**(14sp Medium)；`DateNavRow` 主标签与 `DayStrip` 数字 → 新增 **`DateNumberStyle`**(17sp Medium/24sp)
   - 顶栏变高后同步下移内容起点：`MealTopArea` 124→**140dp**、`EventTopArea` 84→**108dp**
5. **设置页换皮**（`ui/settings/SettingsScreen.kt` 全量重写，265→约 380 行）
   - 丢掉 4 个 `OutlinedTextField`（自带方框 + 浮动 label，与全站玻璃胶囊语言冲突），改「分组实体卡 + 发丝线 + 无框输入」
   - 新增三个私有组件：`SettingsSection`（小标题 + 说明 + solidCard）、`SettingsDivider`（左缩进 16dp 的 1dp 线条）、
     `SettingsField`（12sp 灰标签 + `BasicTextField` + 15sp 值 + 可选说明；光标 `SolidColor(primary)`，占位符 60% 辅助色）、
     `SettingsActionRow`（无 ripple 的次要操作行，禁用时 38% 透明度）
   - 主 CTA「测试并开启同步」从 `GlassCapsuleButton`（玻璃胶囊）换成**实心主色 `Button`**：原先主/次按钮重量级几乎一样，看不出该点哪个
   - 业务逻辑（昵称 700ms 防抖传播、通知权限申请、`statusKind` 五态、坚果云说明文案）全部保留
6. **编辑页输入框统一**（`ui/components/Common.kt` 新增 `PlainField`）
   - `EventEditScreen` 的标题/备注两个 `OutlinedTextField` → `PlainField`（实体卡 + 无框输入 + 无浮动 label）
   - `OutfitUploadScreen` / `OutfitEditScreen` / `EventEditScreen` 的日期行 `Surface + 描边` → `solidCard`
7. **顶栏 scrim 改成背景色**（`Glass.kt` 的 `glassTopAppBar`）
   - 原来 `surface`(纯白) 0.86 压在奶油底上会拉出一条比页面更亮的灰白横带；改用 `background` 0.92 做 scrim，
     滚上去的内容仍被盖住，但没有「颜色台阶」，只留 1dp 30% 发丝线
8. **顺手修掉两处图片问题**
   - `OutfitUploadScreen.ExistingThumb` 补照片存在性判断（缺图时给 `PhotoSyncPlaceholder`，不再白框）
   - 饮食「未记录」单行入口去掉描边、改 `surfaceVariant` 底 + 35% 描边，与已记录的白卡区分

**验证**：`assembleDebug` 通过；API 35 模拟器装 `the_cutest_girl_v2.5.2_debug.apk` 逐页截图
（`.verification/screens/v252-*.png`：outfit / outfit-filled / meal / meal-filled / event / event-filled / settings / outfit-upload / event-edit）；
`lintDebug` **0 errors / 6 warnings**（无新增类型）；`adb logcat -b crash` 仅模拟器蓝牙栈残留，无本应用 FATAL。
测试数据用的是临时造的两张渐变图 + seed 数据库（已随测试覆盖，正式数据不受影响）。

## 本轮修复（v2.5.1）：审查后落地的三处数据可靠性修复

代码审查后按「只改三件事」实施，只动数据与同步逻辑，**未动 UI 布局、同步协议格式、照片压缩参数**（回滚用 v2.5.0 的 APK 覆盖安装，数据无损）。

1. **饮食编辑补上版本递增**（`ui/meal/MealViewModel.kt` 的 `MealEditViewModel.save`）
   - 原来是 `val now = System.currentTimeMillis()`，如果手机时钟比对方慢，保存出来的 `updatedAt` 会小于它正在编辑的那条记录，
     下一轮 `mergeRemote` 的 `localUpdatedAt >= record.updatedAt` 判断就把这次修改当成旧内容丢掉——备注静默消失，且**只在饮食模块**出现（穿搭/事件早就有这个保护）
   - 现改为 `val now = maxOf(System.currentTimeMillis(), (latest?.updatedAt ?: 0L) + 1)`，与另外两个编辑页一致；
     顺带把 `origin` / `originName` 的内联三元抽成 `owner` 变量，逻辑与 `OutfitEditViewModel` 对齐
2. **云端脏日期不再能让页面崩**（`data/Records.kt` + 四个下游）
   - 新增 `fun parseRecordDate(text: String): LocalDate?` 和 `JSONObject.validDate()`；`parseSyncRecord` 三个分支都要求日期能被解析，
     否则返回 null（该条云端记录被当作无法识别而跳过，不再入库）
   - 下游兜底：`OutfitViewModel.dayGroups` 改 `mapNotNull`（非法日期不进分组）、`OutfitEditViewModel.load` / `EventEditViewModel.load` 用
     `parseRecordDate(...) ?: LocalDate.now()` 回填、`EventScreen` 的纪念日/点滴过滤掉非法日期并让卡片 `remember` 里兜底
   - 触发场景：另一台手机或旧版本写坏的 `"2026-13-45"` 这类日期进库后，列表分组与编辑页回填都会在 `LocalDate.parse` 上抛异常，
     而且是**每次进页面都崩**，用户只能卸载重装
3. **单条上传失败不再锁死整轮同步**（`sync/SyncEngine.kt`）
   - 阶段 1 下载：`parseSyncRecord` 解析失败或 id 不匹配时 `skipped++; continue`（原来直接抛异常中止整轮）
   - 阶段 2 上传：`uploadRecord(...)` 改为返回 `Boolean`，记录 PUT 失败或照片 PUT 失败都只记进 `uploadErrors` 并 `continue`，
     其它记录照常上传、照常 `markClean`；照片 PUT 成功后把 `photoName` 加进 `remotePhotoNames`（同一张图不会被子记录重复 PUT）
   - `Result` 新增 `pending: Int`（本轮没传上去、仍 dirty 的条数）和 `skipped: Int`（跳过的云端坏记录数）；
     `ok` 仍为 true（单条失败不算整轮失败），所以对方的新内容不会因为一条毒记录永远收不到
   - 同步结果文案：`同步成功（瘦身 N 张历史大图），M 条待重传，跳过 K 条异常数据`（M/K 为 0 时不出现）

**回归验证**：`tools/sync_test_server.py` 的 `do_PUT` 新增 `reject-` 前缀拒收钩子（返回 507）；`SyncRegressionRunner` 从 14 项扩到 **17 项**，
新增 `mealClockSkew` / `malformedRemoteRecord` / `rejectedUploadIsIsolated`。API 35 模拟器 + 本地 WebDAV 实跑 **17/17 passed**，
证据 `.verification/tests-after-3fixes.txt`。构建 `assembleDebug` 通过，crash 缓冲无本应用 FATAL。

## 本轮 UI 修复与改版（v2.5.0）：新图标 / 按钮 emoji / 设置入口独立 / 图片容器玻璃化

只改界面与图标资源，**未动同步协议、照片压缩、墓碑机制**（要回滚就用 v2.4.5 的 APK 覆盖安装，数据无损）。

1. **启动图标换成毛毡玩偶合照**（`tools/make_launcher_icon.py` 已改为新图逻辑）
   - 新图是满幅照片无白边：中心 8% 内缩取景 → 中心裁方 → LANCZOS 缩放到 5 密度 foreground PNG（108/162/216/324/432）
   - `ic_launcher_background` 粉色 `#F85B88` → 暖沙色 `#D1BD96`（取画面上缘天空/树叶平均色）
   - 圆形裁切实测两张脸完整保留在安全区内；闪屏页即显示新图标
2. **详情页「拍一张」「换一张」加 emoji**（`Common.kt` 的 `PhotoNoteCard` 有照片时的操作行）
   - 🖼️ 换一张/再加、📷 拍一张；新私有件 `ButtonEmoji`（13sp，比 labelLarge 小一档 + 固定行高 + 6dp 间距，与空态按钮的图标间距一致）
   - 空态「选照片/拍一张」按钮原本就有 Material 图标，未重复加 emoji
3. **设置入口独立 + 顶栏左边距**（`PageHeader` + 新增 `GlassTopBarWithSettings`）
   - 齿轮从 `PageHeader` 标题行移除（每页只剩这一个入口）；改为顶栏面板**右侧独立的 44dp 圆形玻璃按钮**（与 FAB 同套玻璃材质），不与日期栏 ◀ ▶ 挤在一起
   - `GlassTopPanel` 新增 `outerPadding` 参数（默认不变；收进 Row 时传 0，外边距由 Row 统一承担）
   - 标题行左侧安全边距 4→14dp，emoji + 文字整体右移，不再贴面板边
4. **饮食/事件图片容器统一磨砂玻璃**（新增 `Glass.kt` 的 `Modifier.glassPhotoFrame()`）
   - 已上传照片展示框 + 同步占位框：内缩 14dp + 14dp 圆角 + surface 55% 半透明底 + 玻璃折射描边（`drawGlassEdge`），无阴影（嵌在卡片里）
   - ⚠️ 这些容器在 `.haze()` 采样源**内部**，按 Haze 约束不能用 hazeChild 真模糊，走静态玻璃（同编辑页图文卡片的 `glassStatic` 思路）


## 本轮 UI 修复与改版（v2.4.5）：卡片昵称 / FAB 间距 / 顶栏折叠 / 次要按钮 / 全局玻璃

只改界面，**未动同步协议、照片压缩、墓碑机制**（要回滚就用 v2.4.4 的 APK 覆盖安装，数据无损）。

1. **穿搭卡片恢复上传人昵称**（`OutfitScreen.OutfitThumb`）
   - 之前右上角只有一颗 💗，昵称丢了；现在改成「💗 + 昵称」胶囊（surface 82% 底 + 50dp 圆角）
   - 昵称用 `widthIn(max = 68.dp)` + `maxLines = 1` + `TextOverflow.Ellipsis` 截断，过长不会顶到照片边缘；昵称为空时兜底显示「对方」
2. **FAB 上移，与导航栏拉开安全间距**
   - 穿搭页「记穿搭」、事件页「记一笔」的圆形玻璃 FAB：`bottom = LocalAppBottomInset.current + FabNavSpacing`
   - `FabNavSpacing = 18.dp`（`Glass.kt` 公共常量）。原来紧贴导航栏上沿，容易误触
3. **顶栏压缩 + 滚动折叠**（三个列表页统一）
   - 高度压缩：`PageHeader` 竖向内边距 12→4dp、`DateNavRow` 4→2dp，光这一项就让顶栏矮了 16dp
   - **滚动折叠**：`PageHeader` 新增 `collapsed` 参数，副标题用 `AnimatedVisibility(fadeOut + shrinkVertically)` 收起；
     穿搭页再往下滚会把「月份行 + 日期条」整块收起（穿搭 196→70dp，饮食 124→70dp，事件 84→70dp）
   - 折叠状态只有一个布尔值参与重组：`derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 16 }`，滚动过程不触发重组
4. **「回到本月」/「回到今天」改为胶囊按钮**（`DateNavRow`）
   - 用 `GlassCapsuleButton`：半透明底 + 主色描边 + 50dp 全圆角，按压整体轻缩 0.94 弹簧回弹，内边距 10/3dp，labelSmall 11sp
   - 静态「今天」徽标也做成同款胶囊（只是不可点），避免可点 / 不可点两态形状不统一
5. **全局玻璃拟态**（新增 `ui/components/Glass.kt`，玻璃参数全部收拢到一处）
   - 顶栏 / 日期栏：三个列表页的头部改 `GlassTopPanel`——悬浮玻璃面板，左右 16dp 与底部导航栏对称，
     内容从它下面滚过并被实时模糊（列表 `.haze()`，面板 `hazeChild`）
   - 编辑页图文卡片：四个编辑页（穿搭单条 / 穿搭上传 / 饮食 / 事件）的 `PhotoNoteCard` 走 `glassMaterial`，
     照片与备注文字都是卡片上的内容（不会被模糊，只有卡片底被模糊）
   - 4 个新增按钮：「从相册选择 / 拍一张 / 再加 / 移除」改用 `GlassCapsuleButton`
   - 编辑页 / 设置页顶栏：`glassTopAppBar` = 半透明底 + 底部 1dp 发丝线 + 4dp 柔和阴影
   - ⚠️ **刻意不套模糊的两处**（别自作主张加）：① 卡片内的 4 个按钮——卡片本身就是玻璃，它背后是纯色页面背景，
     再模糊一次只会得到同样的纯色，白搭一遍模糊开销；② 编辑页顶栏——它不与内容重叠，背后同样没有可模糊的东西

**有意未做**：通知 deeplink 直达某天、Design Token 全量归位、日期条按最早记录动态取年份范围、底栏文字标签（保持现有图标-only 语言）。

## 本轮 UI 优化（v2.4.4）

只改界面与穿搭页交互，**未动同步协议、照片压缩、墓碑机制**（要回滚就用 v2.4.3 的 APK 覆盖安装，数据无损）。

1. **穿搭改为按天聚合的记录视图**（`OutfitScreen` / `OutfitViewModel` / 新增 `OutfitUploadScreen`）
   - 浏览范围仍是月份（`DateNavRow`），下面新增 `DayStrip` 横向日期条：可滚动切换、点击选中某天，有记录的日期下方带主色小圆点
   - 列表以「日期」为分组单位，每天一组（`OutfitDayGroup`），组内是这一天的全部照片（两列 4:5 网格），日期头可点选中、右侧「+」直接进这一天的上传页
   - 「记穿搭」FAB 把当前选中日期带进 `outfitUpload/{date}`；上传页可改归属日期、展示该日已有照片（点图进单条编辑、右上角 ✕ 删除）、多选新照片，保存时每张照片一条记录统一归到该日期
   - 数据层只加了一条 `OutfitDao.observeByDate(date)`，实体 / 同步 / 迁移一行未动
2. **标题 emoji**（`Common.kt` 新增 `EmojiTitle` / `ModuleEmoji` / `PageEmojiSize` 等常量）
   - 穿搭 👗 / 饮食 🍚 / 事件 💗 / 设置 ⚙️；主标题 22sp（标题 26sp）、TopAppBar 标题 18sp（标题 16sp），间距统一 8dp
   - ⚠️ **v2.4.3「UI 禁用 emoji」的约定到此作废**：emoji 仅用于各模块标题这一个位置，状态表达（`SyncStatusBar` / `PhotoSyncPlaceholder`）仍然只用图标 + 语义色，不用 emoji
3. **液态玻璃底栏 + 长按拖拽**（`GlassBottomBar.kt` 重写）
   - 视觉：blurRadius 24→30dp、新增顶部镜面高光 + 底部内阴影 + 上亮下暗多段折射描边；滑块会随手指速度被拉长变薄（横 +30% / 纵 -20%），圆角同步变尖
   - 交互：点按仍切 tab；**长按后拖动**，滑块脱离吸附连续跟手，两端有橡皮筋阻尼（`RUBBER_BAND = 0.16`），松手以 spring(0.45, 380) 弹性吸附 + spring(0.32, 420) 形变回弹；拖动中消费事件，避免子控件 clickable 再触发一次切 tab
   - 滑块位置/形变用 `Animatable`，在 `drawBehind` 里读值绘制，**不走重组**，拖动不掉帧
4. **图文一体卡片**（`Common.kt` 新增 `PhotoNoteCard`，替换 `PhotoBlock`）
   - 照片贴卡片顶边铺满、顶部两圆角跟随卡片；照片区与输入区之间只放一条 1dp / 28% 发丝分割线（左右 16dp，与 TextField 内容内边距对齐）；输入框去掉外框与底色（透明 indicator + container），由卡片充当容器
   - 穿搭/饮食/事件三个编辑页统一改用；穿搭上传页用它承载「多图 + 备注」（`maxPhotos = 9`）
5. **FAB 圆形玻璃按钮**（`GlassBottomBar.kt` 新增 `GlassCircleButton`，v2.4.4 后半轮补做）
   - 穿搭页「记穿搭」、事件页「记一笔」的 FAB 从 ExtendedFAB 改为 **52dp 正圆玻璃按钮**：与底栏完全同一套玻璃材质（同一组 blurRadius 30dp / noise 0.10 / tints / 高光 / 内阴影 / 折射描边 / 10dp 阴影），容器换成正圆
   - 点按反馈 = 整体轻缩 0.90 + 弹簧回弹；图标用 `Icons.Outlined.Add` 28dp 主色
   - 每页自己的 `fabHaze = remember { HazeState() }` 作模糊采样源，页面内容 `.haze(fabHaze)`，按钮 `hazeChild` 悬浮其上；位置 `BottomEnd` + `end=20dp, bottom=LocalAppBottomInset.current`
6. **启动图标替换**（用户提供粉色日记本图）
   - `tools/make_launcher_icon.py`：裁掉原图白边 → 居中补正方形 → LANCZOS 缩放到 5 个密度（108/162/216/324/432px）写为 `mipmap-*/ic_launcher_foreground.png`
   - `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`：background = `@color/ic_launcher_background`，foreground = 新 PNG；**monochrome 已删**（旧白心向量与新图标不一致）
   - `values/colors.xml`：`ic_launcher_background = #F85B88`（从图标粉色采样）

**有意未做**：通知 deeplink 直达某天、Design Token 全量归位、日期条按最早记录动态取年份范围、底栏文字标签（保持现有图标-only 语言）。

## 本轮修复与验证（v2.4.2）

- 修复了此前未实际解决的同步问题：创建者会接收对方修改的穿搭/事件备注；不再按 `origin == 本机` 跳过，也不把服务器 `Last-Modified` 与手机时间混着比较。现在下载 JSON，按 `updatedAt` 判断版本。
- 昵称更新移到下载合并之后，每轮校正自己记录中的旧昵称，避免改名时覆盖尚未接收的备注。设置页停顿输入或退出时自动请求同步。
- 纪念日编辑器过去有备注框但保存时清空描述；现在保存并同步备注，纪念日卡片也显示备注预览。日常点滴、穿搭继续通过原有编辑页查看完整内容。
- 上传完成只清除对应版本的 `dirty`，上传期间的新保存留待下一次同步。穿搭/事件编辑时间至少为当前记录版本 + 1，兼容两台手机的时钟偏差；保存时保留最新创建者身份。
- 同步引擎用进程级 Mutex 串行执行；即时任务独立入队，不取消正在上传的任务，也不被旧失败任务的重试等待阻塞。取消异常继续向上传播。
- 兼容现有 Room v2 数据和 WebDAV JSON，不需要清空数据。两台手机都应覆盖升级；仍然使用整个记录的最后写入优先规则，同时离线改同一条记录不会自动合并两段文本。
- 验证方法见 `docs/sync-verification.md`。真实 Room/WebDAV/编辑器/WorkManager 回归测试 14 项通过；修复前的核心 10 项有 6 项失败，额外并发、时钟、失败重试问题均先复现后修复。

## 本轮 UI 优化（v2.4.3）

只改界面，**未动数据层、同步逻辑、玻璃底栏**（要回滚就用旧 APK 覆盖安装，数据无损）。改动前备份在 `E:\project\AI_seession\TheCutestGirl_backup_20261027_v2.4.2`（项目无 git，这是唯一回滚点）。

1. **三页头部统一为两行制**（新增 `PageHeader` / `DateNavRow` / `AppMonthPickerDialog`，都在 `ui/components/Common.kt`）
   - 第 1 行：标题 headlineMedium + 副标题 labelMedium + 右上角设置 IconButton，水平 20dp / 垂直 12dp，**三页完全一致，且不放任何条件显示的控件**——这是"切 tab 不跳变"的关键
   - 第 2 行：◀ 日期/月份 ▶，中间区域可点（饮食 → 现有 `AppDatePickerDialog`；穿搭 → 新的月份选择弹窗，年份范围 = 今年往前 10 年）
   - 穿搭月份可点直选，解决"回看要连点 N 次箭头"；「回到今天 / 回到本月」从标题行挪进日期条下方小字（主色），标题行因此永久静止
   - 事件页**只有第 1 行**：时间线不需要日期过滤，不强行统一
   - `OutfitViewModel` 新增 `setMonth(y, m)` / `toCurrentMonth()`；Room / DAO / 同步逻辑一行未动
2. **照片占位两态 + 点击重试**（`PhotoSyncPlaceholder`）：替换三处复制粘贴的「照片同步中…」，该文案已全项目清零
   - 同步中：CloudSync +「照片还在路上… / 同步完成后会自动出现」
   - 同步不了：CloudOff +「现在同步不了 / 点一下重新同步」；判定 = `SettingsStore.lastSyncResult` 以「同步失败」开头
   - 占位**永远可点击** → 已配置账号时 `SyncManager.enqueueNow` + Toast「已开始同步，照片稍后出现」；未配置时只 Toast「还没开启同步，去设置里填坚果云账号」（不做空承诺）
   - 尺寸沿用原位：穿搭 4:5、饮食 90dp、事件 110dp
3. **设置页状态清晰化**（`SyncStatusBar` + `SyncStatusKind`）：删除 ✅/❌ emoji，改图标 + 语义色五态（Idle / Testing / Success / Failure / Queued）；「上次同步：M月d日 HH:mm · 成功/失败」从页面底部 12sp 灰字提升为状态条副行
   - 顺带修了一个真 bug：原来的连接测试结果变量赋值了 4 处，但**代码里从未把它显示出来**——以前点「测试并开启同步」，成功失败在界面上毫无反馈
   - 已知局限：后台同步完成后状态条不会自动刷新，要等下次进页面或点按钮（修它需给同步结果加 Flow，属数据层，本轮未做）

**有意未做**：通知点击直达对应页面（deeplink）、Design Token 体系 / 字号圆角全量归位 / 组件去重、中文文案抽 strings.xml、顶部渐变 scrim。（FAB 玻璃化已在 v2.4.4 落地，见上文 `GlassCircleButton`）

## 1. 项目是什么

给一对情侣用的 Android 记录 APP「the cutest girl」，三大板块（底部玻璃拟态导航）：

| 模块 | 功能                                             |
| -- | ---------------------------------------------- |
| 穿搭 | 按月浏览每日穿搭照片（4:5 竖版照片为主体），照片+日期+备注               |
| 饮食 | 按天查看，早/午/晚三餐；**每人每餐独立记录**（我的可编辑 + 对方的只查看），互不覆盖 |
| 点滴 | 纪念日（自动算第 N 天/还有 N 天，数字为视觉焦点）+ 日常点滴图文时间线        |

> 「点滴」在 v2.7.0 之前叫「事件」，路由仍是 `event`、实体仍是 `EventRecord`，只改了展示名。

核心特性：**两人通过坚果云 WebDAV 双向同步**，一方上传另一方 ≤15 分钟内收到系统通知（💗xx 发来了新内容）；照片保存时自动压缩（1920px JPEG85 + EXIF 转正）。

**双区文字（v2.8.0 起，v2.8.3 定稿）**：每条记录除了上传人的备注，还有一块给对方写的评论区——
备注只有上传人能写、评论只有对方能写，两个人各写各的，谁也不会覆盖谁。

数据全部本地 Room + 坚果云私有目录 `/thecutestgirl/`，无自建服务器，免费额度（上传 1GB/月、下载 3GB/月，按自然月刷新）两人用量约 60MB/月绰绰有余。

## 2. 构建环境（全部在 E 盘，用户硬性要求）

```
项目路径    E:\project\AI_seession\zcode\TheCutestGirl
Android Studio  E:\appdate\Android_studio   ⚠️ 自带 JBR 是 JDK 25，与 Gradle 8.9 不兼容，禁止用于构建
构建 JDK 17     E:\appdate\jdk-17.0.20.1+1  （gradle.properties 里 org.gradle.java.home 已锁定）
Gradle 8.9      E:\appdate\gradle-8.9       （项目有 wrapper，distributionUrl 指向腾讯镜像）
GRADLE_USER_HOME  E:\appdate\gradle-home    （已 setx 用户级环境变量）
Android SDK     E:\appdate\Android\Sdk      （build-tools 34.0.0+35.0.0、platforms/android-35、platform-tools、emulator、系统镜像）
AVD 目录        E:\appdate\android-avd      （已 setx ANDROID_AVD_HOME；AVD 名 testgirl = pixel_6 / API35 / default x86_64）
```

**构建命令**（Git Bash）：

```bash
cd /e/project/AI_seession/zcode/TheCutestGirl
export JAVA_HOME="E:/appdate/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="E:/appdate/gradle-home"
./gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/the_cutest_girl_v<版本>_debug.apk
```

**依赖版本锁定注意**：Haze 必须保持 **1.1.1**（2.x 需要更新 Kotlin/Compose，会编译失败；API 只有 `Modifier.haze(state)` / `Modifier.hazeChild(state){...}`，没有 hazeEffect/HazeInput）。Kotlin 2.0.21 / Compose BOM 2024.10.01 / AGP 8.7.3 / Room 2.6.1。

### 网络（中国大陆环境，坑最多的一节）

- `dl.google.com` 被 DNS 污染。用 DoH（`https://doh.pub/dns-query?name=dl.google.com`）解析出国内 CDN IP（如 120.253.253.33），然后 `curl --resolve dl.google.com:443:<IP>` 直连
- SDK 包优先腾讯镜像 `https://mirrors.cloud.tencent.com/AndroidSDK/`（**没有系统镜像**，镜像在 `dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip`）
- Maven 依赖走阿里云镜像（settings.gradle.kts 已配置，别删）
- 本地 CONNECT 代理脚本 `%LOCALAPPDATA%\Temp\gproxy.py`（8888 端口，仅放行 dl.google.com，供 sdkmanager 用：`SDKMANAGER_OPTS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=8888"`）
- **模拟器必须执行**：`adb shell settings put global captive_portal_mode 0` 然后 `svc data disable && svc data enable`，否则系统认为"无有效网络"，WorkManager 的 CONNECTIVITY 约束永远不满足、同步永不执行

## 3. 架构与关键文件

```
app/src/main/java/com/thecutestgirl/app/
├── MainActivity.kt            # 入口：enableEdgeToEdge + 启动即触发同步
├── data/                      # ⚠️ 动这里要极度谨慎，涉及同步协议
│   ├── Records.kt             # 三个实体（UUID 主键 + origin/originName/updatedAt/dirty）
│   │                          #   + 双区文字：note/noteDevice/noteName/noteUpdatedAt（上传人备注）
│   │                          #                 reply/replyName/replyUpdatedAt（对方评论）
│   │                          #   + Tombstone + SyncRecord(JSON 序列化/反序列化) + parseRecordDate()
│   ├── Daos.kt                # 含 getDirty/markClean/setOrigin/getLegacy/deleteLegacyByCreatedAt/
│   │                          #   updateOriginName（昵称传播）/ setNote(id,note,name,device,now) /
│   │                          #   setReply(id,reply,name,now) 等同步专用查询
│   │                          #   ⚠️ events 的备注列名是 description，不是 note
│   ├── AppDatabase.kt         # version=5，四条迁移全在：
│   │                          #   MIGRATION_1_2（v1 自增 Long id → "L"+id 字符串，无损）
│   │                          #   MIGRATION_2_3（+ reply/noteUpdatedAt/replyUpdatedAt）
│   │                          #   MIGRATION_3_4（+ replyName）
│   │                          #   MIGRATION_4_5（+ noteDevice/noteName）
│   ├── PhotoStore.kt          # 照片存 files/photos/；保存时压缩（1920px/JPEG85/EXIF转正）
│   └── SettingsStore.kt       # WebDAV 配置（saveDavConfig / davConfigChanged）、deviceId、myName、
│                              #   propagatedName、firstSyncDone / clearFirstSyncDone、同步状态
├── sync/
│   ├── WebDavClient.kt        # OkHttp；PROPFIND/GET/PUT/DELETE/MKCOL
│   │                          #   ⚠️ XmlPullParser 必须开 FEATURE_PROCESS_NAMESPACES，否则解析为空
│   ├── SyncEngine.kt          # 同步主流程（见下）；companion 里有 applyDavConfig（换云盘入口）
│   │                          #   与 testConnection(prepareDirectories)，见 v2.9.2 节
│   ├── SyncStateStore.kt      # 增量账本（file_sig/file_confirmed/notified_ids/dirs_ready）
│   │                          #   + resetForNewEndpoint()：换云盘时清账本（commit 同步落盘）
│   └── SyncManager.kt         # 15分钟周期 + 即时任务（SyncWorker）+ PartnerNotifier 通知
└── ui/
    ├── AppRoot.kt             # Scaffold + GlassBottomBar + haze 采样源 Box + LocalAppBottomInset
    │                          #   + 四段转场（enter/exit/popEnter/popExit，见 §4）
    ├── theme/                 # Color(奶油燕麦系)/Type(字号行高表)/Accent(模块配色)/Theme
    ├── components/
    │   ├── Glass.kt           # 玻璃材质总控：glassMaterial / pinkGlassTints / GlassTopBar /
    │   │                      #   GlassCapsuleButton / glassTopAppBar / solidCard / dashedCard /
    │   │                      #   moduleGlow / glassStatic / glassPhotoFrame（改观感只改这里）
    │   ├── GlassBottomBar.kt  # 液态玻璃栏 + GlassCircleButton(圆形玻璃 FAB)
    │   ├── Common.kt          # EmojiTitle/PageHeader/DateNavRow/DayStrip/EmptyHint/PartnerMark/
    │   │                      #   PhotoNoteCard(双区)/TextNoteBlock/ReadOnlyNote/CommentField/
    │   │                      #   PlainField/SyncStatusBar/PhotoSyncPlaceholder/日期与删除对话框/大图
    │   └── LocalAppBottomInset.kt
    ├── outfit/                # OutfitScreen(按天聚合) + OutfitUploadScreen(指定某天上传) +
    │                          #   OutfitEditScreen(单条编辑) + OutfitViewModel(含 OutfitUploadViewModel)
    ├── meal/ event/ settings/ # 三组页面（Screen + EditScreen + ViewModel）
```

> 源码共 30 个 Kotlin 文件（data 5 / sync 3 / ui 20 / MainActivity 1，ui 内含 theme 4 与 components 4）。
> 组件调用点计数（判断「改它会影响几处」用，已排除声明与 KDoc）：
> `solidCard` 17、`GlassCapsuleButton` 7、`glassMaterial` 7、`EmojiTitle` 7、`PageHeader` 6、`GlassTopBar` 5、
> `glassTopAppBar` 5、`PhotoSyncPlaceholder` 5、`glassPhotoFrame` 4、`DateNavRow` 4、`moduleGlow` 3、`PlainField` 3、
> `TextNoteBlock` 3、`PartnerMark` 3、`DayStrip` 3、`EmptyHint` 3、`GlassCircleButton` 3、`CommentField` 2、
> `ReadOnlyNote` 2、`SyncStatusBar` 2、`dashedCard` 1；**`glassStatic` 与 `GlassTopPanel` 已无调用点（可删）**

### SyncEngine 同步阶段（顺序敏感，改前必读）

```
0    旧迁移 id 撞车修复：origin=="local"或本机的 "L*" 记录改名 "M-<设备指纹8位>-<编号>" 重传，
     删云端旧 L 文件（不传墓碑）；收到 M- 记录时按 同类型+同createdAt 去重删本地 L 副本
1    下载：先收集远端+本地 pending 墓碑 id（命中跳过，防删除复活）；读取 JSON 的 updatedAt；
     在 Room 事务内重新读取本地版本和墓碑，只有远端更新才合并（无论记录属于谁）；下载失败停止本轮
1.5  昵称传播：合并后校正本机名下的 originName，置 dirty 并递增版本，成功后记 propagatedName
1.6  照片补漏：本地记录引用的照片文件缺失且云端有 → 补下载（自愈）
2    上传：dirty 记录 PUT；照片不在云端则传；仅上传版本仍相同时清 dirty；仅 origin=="local" 的才 setOrigin 认领为本机
     ⚠️ 只认领 "local"，认领对方的会把归属抢走（修过的 bug）
     v2.5.1 起**逐条隔离**：单条 PUT 失败只记进 uploadErrors 并 continue，不中断整轮；
     照片 PUT 失败也算该条失败（否则记录标干净了、对方永远等不到图）；
     照片传成功后加入 remotePhotoNames 去重，同一张图不会被多条记录重复 PUT；
     Result 带 pending（仍 dirty 待重传数）与 skipped（云端坏记录跳过数）两个计数
3    墓碑上传（PUT T_<id>.json）+ DELETE 云端对应记录文件；不传墓碑会复活、传了会误删对方
4    大图瘦身：本地 >1MB 的照片——云端明显更小则拉压缩版覆盖本地，否则本地压缩后覆盖云端
```

### 业务规则（改功能前必读）

- **编辑保留归属**：三个 EditViewModel 持有 editingOrigin/originName，编辑别人记录只改内容不改归属（备注双方可见、💗标记不消失）；新建记录才写入本机身份
- **双区文字权限（v2.8.3 定稿，别再改）**：`amUploader = editingId == null || origin 为空 || origin=="local" || origin==本机`
  - **备注区 note：只有上传人可写，对方只读**（`noteEditable = vm.amUploader`）
  - **评论区 reply：只有对方可写，上传人只读**（`replyEditable = !vm.amUploader`）
  - 两人各写各的区，谁也不能改对方那句。`noteDevice`/`noteName` 两列留在 schema 里但**不参与权限判定**
  - 非上传人保存时走 `dao.setReply(...)` 直接返回，一个字都不碰上传人的照片和备注
  - 备注标签 = `noteAuthorName`（我是上传人时为空，不贴标签；否则显示对方昵称 + 「的备注」）；评论标签 = `replyAuthorName`
- **饮食双人**：按 `origin == 本机deviceId` 拆「我的」（可编辑）/「对方的」（只查看+点图全屏），一餐两人各自记录
- 删除 = 本地删 + tombstones 表 → 同步上传墓碑；对方应用墓碑删本地记录+照片
- 通知：仅下载到「对方 origin 的新记录」且非首次同步时发（防配对轰炸）；Android 13+ 需 POST_NOTIFICATIONS（设置页开启同步时请求）
- 同步触发点：保存记录后、修改昵称后、APP 打开时、15 分钟周期（WorkManager 最小周期）

## 4. 设计系统（v2.8.3「奶油底 + 纯白卡 + 奶粉玻璃」）

> 本节 v2.8.3 全量重写。v2.3.2~v2.6.2 期间做过 A 档（明暗分层/实体卡/空态/排版/设置页换皮）、
> B 档（结构）、C 档（视觉资产），下面写的是**当前代码实况**，不是计划。

### 4.1 配色（`ui/theme/Color.kt`）
| 语义 | 浅色 | 深色 |
|---|---|---|
| primary | `#B25A79` 玫瑰 | `#E8A9BF` |
| background | **`#F1EBE5` 奶油燕麦**（v2.5.2 前是 #FAF6F2） | **`#15110F`** |
| surface | **`#FFFFFF` 纯白**（v2.5.2 前是 #FFFDFA） | **`#262019`** |
| onSurface / onBackground | `#2B2420` | `#EDE4DF` |
| onSurfaceVariant | `#6E625C` | `#BFB3AD` |
| surfaceVariant | `#F8F2EC` | `#2F2823` |
| outline | **`#D2C8C1`**（v2.5.2 前是 #C9BEB8） | **`#4A423D`** |

- **背景 / 卡面明度差必须 ≥1.18:1**。v2.5.1 时是 1.06:1，人眼要 ≥1.2:1 才分得清两个面，
  所以当时的卡片看起来「像浮在纸上的铅笔框」。这是整轮 UI 改版的起点，**改配色先量这个比值**。
- 模块主色在 `ui/theme/Accent.kt`（v2.5.3 加）：`ModuleKind` + `moduleAccent()` / `moduleAccentSoft()`（alpha 深 0.22 / 浅 0.12）/ `moduleGlowColor()`（alpha 深 0.16 / 浅 0.10）
  - 穿搭 `#B25A79` / 深 `#E8A9BF`；饮食 `#C07545` 暖橙 / 深 `#E9B58C`；点滴 `#C4527A` 玫红 / 深 `#EFA1BC`
  - 只在各自模块的图标底、空态光环、模块发光带出现，**不要三页同时上色**

### 4.2 排版（`ui/theme/Type.kt`）
| 样式 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| headlineMedium | 26 / 34 | Medium | 页面主标题 |
| titleLarge | 20 / 28 | Medium | （v2.5.2 新增，备用） |
| titleMedium | 16 / 24 | Medium | 分组标题、餐次、TopAppBar 标题 |
| titleSmall | 14 / 20 | Medium | 页面副标题（v2.5.2 新增，原为 labelMedium） |
| bodyLarge | 15 / 23 | Normal | 卡片正文、输入框 |
| bodyMedium | 14 / 21 | Normal | 次要正文、评论正文 |
| bodySmall | 13 / 19 | Normal | 设置页说明文字（v2.5.2 新增） |
| labelLarge | 14 / 20 | Medium | 按钮、小标题 |
| labelMedium | 12 / 17 | Normal | 标签、日期头辅助 |
| labelSmall | 11 / 15 | Normal | 最次级说明、图片角标 |

另有两个独立 TextStyle：`DateNumberStyle`（17sp Medium / 24sp，`DateNavRow` 主标签与 `DayStrip` 日期数字）、
`EmptyTitleStyle`（18sp Medium / 27sp，空态主文案）。
**中文行高统一按 1.5 倍字号**（v2.5.2 前 bodyLarge 是 15/22，只有 1.47，密）。页面里别再手写 Bold。

### 4.3 三个卡片基元（`ui/components/Glass.kt`）
| 基元 | 参数 | 视觉 | 用在哪 |
|---|---|---|---|
| `Modifier.solidCard(cornerRadius=18.dp, elevation=8.dp, drawHairline=true)` | 纯白底 + 双层柔和投影（ambient 10% / spot 14%，色取暖褐 `onSurface` 而非纯黑）+ 1dp outline 55% 发丝线 | 主卡片 | **17 处**：列表卡片、缩略图、设置页分组、日期行、状态条 |
| `Modifier.dashedCard(cornerRadius=16.dp)` | 虚线描边、无投影 | 未记录占位 | 1 处：`MealScreen.kt:307` 饮食「未记录」入口 |
| `Modifier.moduleGlow(color, height=260.dp)` | 顶部模块色柔光渐变 | 页面顶部氛围 | 3 处：三个列表页 |

- ⚠️ `solidCard` **刻意不用 hazeChild**：所有调用点都在 `.haze()` 采样源内部，会踩下面的 Haze 硬约束。
  投影承担层次感，模糊只留给顶栏/底栏/FAB。
- 深色模式 `shadowTint` 用纯黑（`isSystemInDarkTheme()` 分支），暖褐投在暗底上会发灰。

### 4.4 玻璃材质
- `glassMaterial(hazeState, cornerRadius, elevation=0.dp, blurRadius=GlassBlurRadius, noise=GlassNoise, tintScale=1f, tints=null, enabled=true, disabledBackground=Unspecified)`
  - 内部顺序固定：柔和阴影 → 裁形 → Haze 模糊+着色 → 高光/内阴影/折射描边（`drawGlassChrome` 三笔，小控件用 `drawGlassEdge`）
  - `tints` 传了就完全替代默认 surface 着色；`enabled=false` 时**整条 Haze 管线都不挂**（不建离屏层/不采样/不模糊），改铺 `disabledBackground`
  - **改玻璃观感只改 `Glass.kt` 这一个文件**，别在组件里另写一套
- 公共常量：`GlassBlurRadius=30.dp`、`GlassNoise=0.10f`、`GlassBlurRadiusNested=16.dp`、`FabNavSpacing=18.dp`
- `pinkGlassTints(isDark, surface, primary)`（v2.6.2）：浅色 `surface@62% + primary@22% + White@10%`，深色 `0.58/0.18/0.06`
  - ⚠️ 粉色**不直接铺**：直接 primary 打底会和奶油底 `#F1EBE5` 打架、穿透内容整体发红；三层叠出来才是奶粉调
- ⚠️ **Haze 硬约束（冒烟实测崩溃过）**：`hazeChild` 不能是 `haze` 源的子孙节点，否则运行期抛
  `IllegalArgumentException: ... can not be descendants of each other`。
  编辑页/上传页内容区**不要包 `.haze()`**；列表页的顶栏与 FAB 能用真模糊，因为它们是内容区的兄弟节点。

### 4.5 顶栏（v2.6.0 → v2.7.0）
- `GlassTopBar(hazeState, modifier, collapsed, onOpenSettings, content)`（`Glass.kt:277`），**旧的 `GlassTopBarWithSettings` 已删除**
- 常量：`CollapsedBarWidth=126.dp`（112dp 会把「穿搭」压成「穿…」）、`CollapsedBarCorner=20.dp`、`GearSlot=54.dp`；展开态圆角 26dp
- 宽度动画：`Animatable(initialValue=…, typeConverter = Dp.VectorConverter)` + `tween(300, FastOutSlowInEasing)`
  - ⚠️ 不写 `typeConverter` 会被推断成 `Animatable<Float>`，`.width()` 直接编译不过
- 动画期间 `glassMaterial(enabled = !isAnimating, disabledBackground = flatGlass)`：模糊与阴影都锁死，静止后切回
- **测量与布局解耦（v2.7.0）**：自定义 `Layout`，内容按 `Constraints.fixedWidth(expandedWidth.roundToPx())` 测，外层按 `constraints.maxWidth` 布局
  - ⚠️ 先试的 `Modifier.requiredWidth(expandedWidth)` 编译通过但运行不对：胶囊里标题被压成 5px（`uiautomator dump` 证实）
- 手势：`onClick`（收起态回列表顶部并展开）+ `onLongPress`（进设置）**必须在 `PageHeader` 的同一个 `combinedClickable` 里**，
  拆成两层会被子节点先消费，外层永远收不到
- 设置入口：齿轮只在穿搭页渲染；饮食/点滴页靠长按标题进设置
- 三个列表页的顶部留白已改成**实测**：`var headerHeightPx by remember { mutableIntStateOf(0) }` + `onSizeChanged`
  （`OutfitScreen.kt:155`、`MealScreen.kt:177`、`EventScreen.kt:138`），`TopArea` 常量全项目已无残留

### 4.6 底栏（`GlassBottomBar.kt`，v2.5.4 → v2.6.2）
| 常量 | 值 |
|---|---|
| `TabSlotWidth` | **100.dp**（定宽，不是 weight） |
| `BarGlassHeight` | **56.dp** |
| `BarTouchHeight` | **64.dp**（热区比玻璃高 8dp） |
| `SliderWidth` / `SliderHeight` | **84 × 48 dp** |
| `MAX_STRETCH` / `MAX_SQUASH` | 0.30 / 0.20 |
| `RUBBER_BAND` / `VELOCITY_FULL` | 0.16 / 2400 |

- **结构（v2.5.7 重排）**：`Row` 从玻璃 Box 的**内容**改成它的**兄弟** ——
  `Box { 64dp 热区(pointerInput) / 56dp 玻璃(glassMaterial + drawLiquidSlider) / Row{ TabSlotWidth 的 tab } }`。
  热区大于玻璃、视觉不变、命中区变大；栏宽随 tab 数自适应 = `TabSlotWidth × tabCount`
- 外观：`blurRadius = 26.dp`、`noise = 0.09f`、`tints = pinkGlassTints(...)`（**不再走 tintScale**）
  - 磨砂的关键是颗粒（noise），不是单纯加大模糊
  - 选中块：`primary@16%`（深 22%）糖浆 + 白色顶部高光 `32%`/`18%` + 白色描边 `30%`/`22%`；投影层 alpha 0.55
- **纯图标导航（v2.5.6）**：图标 24dp 居中、**无文字标签**；选中用 `scheme.primary`、未选中 `scheme.onSurfaceVariant`，
  选中态由「填充图标 + 主色 + 背后滑动块」三重表达
- 拖动：`mutableFloatStateOf` 直驱 + `drawBehind` 读值（只失效 draw，不重组）；松手 `spring(0.45,380)` 吸附 + `spring(0.32,420)` 形变回弹
  - ⚠️ `awaitEachGesture` 是 `@RestrictsSuspension`，里面**不能调用外部 suspend lambda**
- 无 ripple（`indication = null`），反馈由液态滑块承担

### 4.7 其它组件规范
- `Modifier.glassTopAppBar()`：scrim 已从 `surface@0.86` 改为 **`background@0.92`**（纯白压在奶油底上会拉出更亮的灰白横带），底部发丝线 `outline@0.30`；**刻意不模糊**
- `Modifier.glassPhotoFrame(cornerRadius=14.dp)`：列表页照片框 / 同步占位框（半透明底 + 折射描边，无阴影）；⚠️ 在 haze 源内部，不能真模糊
- `Modifier.glassStatic(cornerRadius, elevation=6.dp, surfaceAlpha=0.72f)`：静态玻璃（无 Haze）。v2.8.3 起主卡片已改用 `solidCard`，**现在已无调用点**
- `GlassCapsuleButton(onClick, modifier, enabled, hazeState=null, cornerRadius=50.dp, contentPadding=12/5dp, content)`：次要操作统一用它
- 圆形玻璃 FAB `GlassCircleButton(diameter=52.dp)`：穿搭「记穿搭」/ 点滴「记一笔」，与底栏同套玻璃参数，点按缩放 0.90
- `EmptyHint(text, modifier, icon, title, hint, action, accent, showFeltIllustration=true)`：v2.5.3 升级成 **112dp 柔光圆 + 88dp 圆角方形 `R.mipmap.ic_launcher_foreground` 毛毡玩偶**（与启动图标同一只），`title` 为空时回退按 `\n` 拆旧文案；`showFeltIllustration=false` 退回「64dp 圆底 + 28dp 线性图标」轻量形态
- `PageHeader` / `DateNavRow` / `DayStrip`：`DateNavRow` 左右 `IconButton` 已 44→**40dp**，`DayStrip` chip 48→**44dp**
- `PhotoNoteCard(...)`（`Common.kt:875`）承载「照片 + 备注区 + 评论区」双区，参数（**顺序即当前签名**）：
  `photos / onPhotosChanged / note / onNoteChange / notePlaceholder / modifier / allowRemove / maxPhotos / photoRatio / cardCorner / noteEditable / noteAuthorName / reply / onReplyChange / replyEditable / replyAuthorName / replyPlaceholder / photoEditable`
  - `onReplyChange = null` 表示不显示评论区（列表缩略场景）；`replyEditable = false` 时评论区只读
  - 配套四个文字组件：`TextNoteBlock`（内部，可编辑时 41dp 单行 ↔ 三行折叠）、`ReadOnlyNote`、`CommentField`、`PlainField`（设置页/点滴标题用的无框输入）
  - 单图 **12dp 缩进 + 12dp 圆角**（不贴边），多图 `padding(12dp)`、格间 8dp
- 转场（`AppRoot.kt:109-123`）：enter `slideInHorizontally(300)+fadeIn(220)`、exit `scaleOut(0.98,200)+fadeOut(140)`、
  popEnter `fadeIn(200)+scaleIn(0.98,240)`、popExit `slideOutHorizontally(240)+fadeOut(180)`；列表项用 `Modifier.animateItem()`
- 底栏 tab 文案：穿搭 / 饮食 / **点滴**（v2.7.0 从「事件」改名，路由仍是 `event`）
- 标题 emoji：穿搭 👗 / 饮食 🍚 / 点滴 💗 / 设置 ⚙️（`ModuleEmoji`）；**状态表达（`SyncStatusBar` / `PhotoSyncPlaceholder`）只用图标 + 语义色，不用 emoji**
- 图标全部 Material Outlined（有 material-icons-extended 依赖，别引 Phosphor）
- 布局：内容延伸到玻璃栏底下（`LocalAppBottomInset` 供各页 LazyColumn 底部留白 +16dp）；穿搭照片 4:5
- 穿搭页结构：`PageHeader` + 月份 `DateNavRow` + `DayStrip` 日期条 + 按天分组的两列 4:5 网格

## 5. 测试与验证方法（交付前必做）

### 5.1 自动化回归（v2.9.2 起 26 条，首选）
```bash
export JAVA_HOME="E:/appdate/jdk-17.0.20.1+1"
# 1) 起本地 WebDAV 夹具（模拟器走 10.0.2.2:18765）
(nohup python tools/sync_test_server.py --port 18765 --root .verification/dav > tmp/dav.log 2>&1 &)
curl -X PROPFIND http://127.0.0.1:18765/    # 返回 207 即就绪
# 2) 构建 verification 变体（applicationId 会变成 .verification）
./gradlew :app:assembleDebug -PverificationApp --offline -q
adb install -r -t app/build/outputs/apk/debug/*.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 3) 跑（SyncRegressionRunner 是自定义 Instrumentation，不是 JUnit）
adb shell am instrument -w -r \
  com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
# 证据存 .verification/tests-v<版本>.txt
```
- 安全闸：`SyncRegressionRunner.onStart` 里 `check(targetContext.packageName.endsWith(".verification"))`，**不带 `-PverificationApp` 的包根本跑不起来**，防止破坏性 fixture 打到真机数据
- ⚠️ **改了 `Records.kt` 字段后必须 `--rerun-tasks`**，否则 androidTest APK 不重编，运行期报 `NoSuchMethodError: No direct method <init>(...OutfitRecord...)`
- ⚠️ **跑完必须用不带 `-PverificationApp` 的 `assembleDebug` 重出正式包**，否则 `output-metadata.json` 的 applicationId 还是 `.verification`
- 用例覆盖：三条记录类型的往返、昵称刷新、改名保留备注、远端更新/本地更新/无变化、删除与墓碑、纪念日编辑器、上传中编辑、三个时钟偏差、失败重试不阻塞新请求、备注与评论共存、评论不覆盖备注、并发上传、云端坏数据跳过、上传拒收隔离、对方不能改上传人备注、备注标签跟上传人、增量指纹未确认仍下载、无变化不重复下载、**换端点重置账本并自动建目录**、**同一端点（含前后空格）重存保留账本**（后四条是 v2.9.0 / v2.9.2 新增）

### 5.2 手动 UI 核对（1080×2400 模拟器，AVD 名 testgirl）
- Git Bash 里 adb shell 带 Unix 路径必须 `export MSYS_NO_PATHCONV=1`（否则 `adb push` 报 `remote secure_mkdirs() failed`）
- 模拟器掉线拉起：`(nohup emulator -avd testgirl -no-snapshot-save -no-boot-anim > tmp/emulator.log 2>&1 &)`
  ⚠️ 别写 `export X && ( … &)`，`&` 只作用于最后一段，export 不会进后台子 shell；要用 `;` + `( … &)`
- 底栏 tab 中心：`y≈2192`，三格 x ≈ `206 / 540 / 874`（v2.5.7 之后栏高变了，老坐标 y≈2300 已失效）
- 编辑页「保存」在 **(993,340)**；穿搭页 FAB ≈ (900,2100)
- 切深色 `adb shell cmd uimode night yes` **会重启 Activity**，截图前必须重新 `am start` 并等 **12~14s**
- 截图前等 6s 以上，转场没结束会截到黑屏；出现「页面上叠着别的页卡片」的错帧是模拟器快照恢复的后端残留，`pm clear` 或重进即消失（不是代码问题）
- `adb shell input text` 不支持中文，测试数据用 ASCII

### 5.3 造测试数据（seed 数据库）
1. `adb exec-out run-as com.thecutestgirl.app cat databases/the_cutest_girl.db > tmp/x.db`
   **必须连 `-wal` 一起拉**，否则最新数据不在主库里
2. 本地 python sqlite3 改完 → `PRAGMA wal_checkpoint(TRUNCATE)` → 删本地 `.db-wal` / `.db-shm`
3. `export MSYS_NO_PATHCONV=1` 后 `adb push` 到 `/data/local/tmp/`，再 `run-as cp` 回 `databases/` 并 `rm -f` 设备上的 wal/shm
4. 照片：先 `run-as mkdir -p files/photos`，再 `cp` 成**具体文件名**（写成目录会报 `Is a directory`）
5. `force-stop` 重启 → 等 14s（外部 sqlite3 插数据不触发 Room 失效通知，必须重启才刷新）
- ⚠️ `adb push a && adb push b && adb shell cp…` 这种链里 b 失败会让 cp 整段不执行，**数据库看起来没更新却无报错**，分开执行更稳
- ⚠️ `pm clear` 会清掉 `device_id` 与 WebDAV 配置（`shared_prefs/settings.xml` 里的 `device_id`，`my_name` 可能不存在 → 昵称回退「我」）

### 5.4 交付标准
构建通过 + 26/26 回归通过 + `lintDebug` 无新增 + 模拟器截图确认 + `adb logcat -d -b crash | grep FATAL` 为空。
截图统一存 `.verification/screens/v<版本>-*.png`。

## 6. 已修过的关键 Bug（防复发，也是排障经验）

| Bug          | 根因与修法                                                                                                                                      |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| 饮食页点击闪退      | Navigation 路径参数带 defaultValue 仍是必填；改为 query 参数 `mealEdit/{date}/{mealType}?id={id}`                                                        |
| 云端目录列表永远为空   | XmlPullParser 未开命名空间，`<D:response>` 匹配不上；必须 `setFeature(FEATURE_PROCESS_NAMESPACES, true)`                                                 |
| 删除的记录复活      | 墓碑优先级不足；远端墓碑 + 本地 pending 墓碑都要跳过记录文件；上传墓碑后顺手 DELETE 云端记录文件                                                                                 |
| 对方记录"消失"     | v1→v2 迁移 id "L1/L2" 两台手机+三张表撞车，LWW 互相覆盖；M- 前缀改名修复（只改自己 origin 的）。⚠️ 升级前已被覆盖/墓碑误删的数据不可恢复，唯一补救 = 坚果云网页版回收站恢复 `thecutestgirl/records/L*.json` |
| 对方照片看不到      | 照片 GET 一次失败后永不重试；加「照片补漏」阶段每次同步自愈                                                                                                           |
| 进上传页即崩溃（v2.4.5 冒烟） | 编辑页把整页内容包成 `.haze()` 采样源，内部 PhotoNoteCard 又用了 `hazeChild`——Haze 禁止二者互为子孙；修法：卡片改用 `glassStatic`（无模糊静态玻璃），编辑页内容区不再包 haze 源 |
| 编辑对方记录抢归属    | VM save 覆盖 origin + 引擎认领条件过宽；两处都修（认领仅 origin=="local"）                                                                                     |
| 历史大图不瘦身      | v1 旧照片原样在云端；同步末尾 >1MB 自动压缩并覆盖云端/本地                                                                                                         |
| 改昵称旧记录不更新    | 昵称是记录内快照；同步时检测 propagatedName≠myName 批量刷新本机名下记录                                                                                            |
| 多选照片一进编辑页就闪退（v2.4.4 修） | `ActivityResultContracts.PickMultipleVisualMedia(maxItems)` 构造里有 `require(maxItems > 1)`。单图场景传 1 会在**组合阶段**抛异常（编译不报错，只有运行时崩）。修法：合约用 `maxPhotos.coerceAtLeast(2)` 构造，是否使用由 launch 分支决定；另外 `createIntent` 里还有一条 `require(maxItems <= getPickImagesMaxLimit())`，launch 要包 `runCatching` 并退回单选 |
| 按钮文字截断（"拍一"） | 三按钮一行放不下；去掉图标只留文字                                                                                                                          |
| 切换 tab 出现方框  | selectable 默认 ripple；indication=null                                                                                                       |
| 备注区点一下就弹回（v2.6.0） | 折叠态点击回调里直接 `focusRequester.requestFocus()`，此时 TextField 还没上树 → 抛 `IllegalStateException: FocusRequester is not initialized`（三个模块共用组件，全站闪退）。修法：`pendingFocus = true` → `LaunchedEffect(pendingFocus)` 里 `runCatching { requestFocus() }`；另外 `onFocusChanged` 首次上树会先回调一次「未聚焦」，需 `wasFocused` 守卫，否则点一下瞬间弹回 |
| 顶栏胶囊动画里标题被压成 5px（v2.7.0） | 用 `Modifier.requiredWidth(expandedWidth)` 做「测量与布局解耦」编译通过但运行不对（父节点已用 `Modifier.width()` 固定尺寸时行为不可预期）。修法：自定义 `Layout`，内容按 `Constraints.fixedWidth(expandedWidth.roundToPx())` 测、外层按 `constraints.maxWidth` 布局 |
| 评论署名显示成我自己的昵称（v2.8.1） | 评论标签错误地复用了 `originName`（照片上传人昵称）。修法：实体加 `replyName`，`mergeFields` 里「署名跟着评论内容走」（`replyFromRemote ? remoteReplyName : localReplyName`） |
| 对方上传的照片 + 我写的备注被锁成只读（v2.8.2→v2.8.3） | v2.8.2 按 `noteDevice`（备注作者）判「谁写的谁能改」，等于让评论者改上传人的字。**已撤销**，规则收敛为一条：备注归上传人、评论归对方（见 §3 业务规则） |
| 换服务器后「测试成功」但同步必 404（v2.9.2） | `testConnection()` 只 PROPFIND 账号根（必存在 → 假成功），本机 `dirsReady` 仍 true 跳过 MKCOL，新云盘没 `thecutestgirl/records` → `listDir` 404。修法：`applyDavConfig` 比对地址/账号，变了就 `resetForNewEndpoint` + `clearFirstSyncDone`，`testConnection(prepareDirectories=true)` 当场建三层目录并真 listDir 一次 |
| 「去设置」点了没反应（v2.9.1） | Manifest 缺 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限 → 系统设置 Activity 立刻 `finish()`；`runCatching{startActivity}.isSuccess` 判不了这种「有 resolver 却秒退」，回退链走不到。补权限 + `<queries>` + 三级回退 |

## 7. 已知限制与待办候选

- **后台同步延迟上限 15 分钟**（WorkManager 最小周期，Android 硬限制）。App 开着时每 20 秒轮询一次，保存/打开 APP 时即时同步；真正秒级推送要 FCM + 实名，用户已否决付费方案
- 撞车修复、昵称传播等机制都要求**两台手机同时升级**才收敛
- v2.3.1 之前被覆盖丢失的记录：只能靠坚果云回收站/文件历史找回（已告知用户，用户暂搁置）
- 旧时代（v2.4 前）"被对方编辑过的记录"归属是最后编辑者，历史数据形状如此，无法追溯
- 未做的候选功能：导出备份、顶部渐变 scrim、真实时推送（需云服务+实名，用户已否决过付费方案）
- **换云盘仍需手动搬历史**（v2.9.2 只做「连得上、自动建目录、账本不串台」）：老记录 `dirty=0` 不会被重传，新云盘 `records/` 是空的。要让对方看到历史、或换机后能拉回老照片，必须先在两台手机上各自同步完，再把旧云盘整个 `thecutestgirl/`（含 `records`/`photos`）复制到新云盘同一级，然后两台都改配置点「测试并开启同步」。本地数据（Room 库 + `files/photos/`）改配置时不会被动过
- **下轮候选（已排优先级）**：① 照片选择区三按钮重设计（现在是历史截断 bug 的妥协修法）② 「我的 / 对方」记录视觉区分强化 ③ 通知按记录聚合（现在同一条记录连改两次会弹两条）
- ~~通知点击直达对应 tab + 日期~~ **v2.9.0 已完成**
- **设计系统专项轮候选**（用户零感知，攒一起做）：Design Token 体系、剩余硬编码字号归位、圆角梯度统一、重复组件抽取、中文文案抽 strings.xml
- 已知小瑕疵（v2.4.3）：设置页状态条在后台同步完成后不自动刷新（需数据层加 Flow）；月份弹窗年份范围固定为「今年往前 10 年」，未按最早记录动态取值
- 已知小瑕疵（v2.4.4）：穿搭日期条同样固定展示整月（未按最早记录收敛范围）；上传页的暂存新照片在进程被杀后会丢失（未落盘）；底栏拖动时实时切页，低端机上若出现卡顿可改为"松手才切页"
- **v2.8.x 新增候选**（评论功能带出来的）：
  - 评论没有「已读」标记，对方写了新评论只能靠同步通知里的「三餐 ×1」这种笼统摘要感知，不知道是哪条
  - ~~通知没有按记录 id 去重~~ **v2.9.0 已修**：按 `id@updatedAt` 去重，同一条记录被对方改第二次仍会提醒
  - `noteDevice` / `noteName` 两列现在只写不读（v2.8.3 撤销了按作者判权限），未来要么用于「谁写的」展示，要么在下一次 schema 变更时清掉
  - 编辑页「保存」按钮的 `enabled` 依赖 `canSave` 快照，非上传人只改评论时能正确亮起，但上传人只改照片不改备注时 `noteUpdatedAt` 不会刷新（这是有意为之，避免用旧内容抢版本）
- **UI 侧遗留**：`Modifier.glassStatic` 与 `GlassTopPanel` 已**无任何调用点**（`GlassTopBar` 内部自己调 `glassMaterial`，没走 `GlassTopPanel`），下一轮可以删；`Glass.kt:232` 与 `Common.kt:308/310` 的 KDoc 仍提到已删除的 `GlassTopBarWithSettings`，顺手一起改

## 8. 用户偏好与协作方式

- 一切占存储的东西放 **E 盘**；C 盘不占
- 中文、说人话、给小白能懂的交付说明（改了什么/为什么/怎么装）
- **版本号由用户逐轮指定**（历史：1.0→2.0→2.1→2.2→2.3→2.3.1→2.3.2→2.3.3→2.4→2.4.1→…→2.5.1→2.5.2→2.5.3→2.5.4→2.5.5→2.5.6→2.5.7→2.6.0→2.6.1→2.6.2→2.7.0→2.8.0→2.8.1→2.8.2→2.8.3→2.9.0→2.9.1→2.9.2），改版前先升 versionCode/versionName
- 交付物 = APK（`the_cutest_girl_v<版本>_debug.apk`），两台手机覆盖安装（debug 签名一致，数据保留）
- 用户会用手机截图反馈问题；配图用视觉模型分析后再动手
- 坚果云网页版入口叫「账号安全」（旧称安全选项），应用密码在那里面生成
