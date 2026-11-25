# 架构说明（the cutest girl）

> 当前版本 5.0.0（versionCode 46）｜最后更新 2026-11-25；本版构建、签名核验与测试结果见第 6 节。

## 1. 总览

纯原生 Android 单模块应用，无后端、无 DI 框架：

- Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10.01，Material 3）
- Room 2.6.1（数据库 v6，schema 导出在 `app/schemas/`）
- WorkManager 2.9.1（后台同步）+ 前台轮询
- OkHttp 4.12（手写 WebDAV 客户端）
- Coil 2.7（照片）
- Haze 1.1.1（仅原版悬浮玻璃底部导航栏使用）

5.0.0 将全界面升级与其依赖的 ZIP 备份恢复、`Partial` 同步状态和相关回归支持一并交付。这些配套数据/同步实现此前已存在于工作区，本次提交包含完整功能代码。

包结构：

```
com.thecutestgirl.app/
├── MainActivity.kt        主题、通知落点、启动维护（草稿清理、同步排单）
├── data/                  Room 实体/DAO/迁移、照片、设置、备份导出与恢复
├── sync/                  WebDAV、同步引擎、状态/错误模型、WorkManager
└── ui/                    AppRoot + 页面 + 通用组件 + 主题
```

## 2. 数据层

### 表

| 表 | 说明 |
| --- | --- |
| `outfits` | 穿搭：date、photoPath、note、reply、时间戳、origin、dirty |
| `meals` | 三餐：date、mealType（0–2）、photoPath、note、reply… |
| `events` | 点滴/纪念日：type（0 日常 / 1 纪念日）、title、description、date、photoPath… |
| `tombstones` | 删除墓碑：id、updatedAt |

三个记录表都有 `Index("date")` 与 `Index("dirty")`。数据库版本 **6**，迁移 1→6 全部在 `AppDatabase.kt`，`MIGRATION_5_6` 建索引。

### 设置与身份

SharedPreferences 按「能不能进备份」拆文件：

| 文件 | 内容 | 系统备份 |
| --- | --- | --- |
| `identity.xml` | `device_id`、`my_name`、`propagated_name` | 包含 |
| `dav_secret.xml` | `webdav_pass`（Keystore AES-GCM 密文） | 排除 |
| `settings.xml` | `webdav_url`、`webdav_user`、同步状态/时间、本机 `theme_mode` | 包含 |
| `sync_state.xml` | 文件指纹、确认集、通知去重、dirsReady、维护时间 | 排除 |

旧版本的 `device_id/my_name/propagated_name/webdav_pass` 在读取时自动迁移到新文件，并从 `settings.xml` 删除。

`theme_mode` 由 `ThemeStore` 管理，仅为本机偏好，不进入记录同步或 ZIP 的 `identity.json`。

### 照片

`PhotoStore`（`data/PhotoStore.kt`）：

- 目录 `files/photos/`，数据库只存文件名
- 新选/新拍照片先落 `draft_` 草稿；保存时 `promoteDraft()` 晋级为 `img_`
- 启动时清理超过 24h 的草稿 + `cleanupOrphans()`（数据库不引用、非草稿、超过 24h）；同步维护轮复用同一个 GC
- 原子写入（`.tmp` → rename，失败复制兜底）；压缩长边 1920 / JPEG 85

### 备份

`BackupExporter.export()`：`wal_checkpoint(TRUNCATE)` 后打包 DB（含 wal/shm 兜底）+ 非草稿照片 + `manifest.json`（含 `davUrl/davUser`）+ `identity.json`（`deviceId/myName/propagatedName`）。不包含密码与同步账本。

`BackupImporter.prepare()` 在临时目录检查 ZIP 清单、数据库完整性、记录/照片数量和原身份，返回供界面确认的预览；`restore()` 恢复记录、墓碑、照片和原设备身份。只允许在未配置云同步且没有本机记录/墓碑的安装中恢复；界面确认数量和缺图信息后执行，恢复完成后重新配置同步。失败清理暂存照片并恢复原身份，不把界面预览当成已恢复。

## 3. 同步

### 云端目录

```
/thecutestgirl/records/<id>.json      记录
/thecutestgirl/records/T_<id>.json    删除墓碑
/thecutestgirl/photos/<name>.jpg      照片
```

### 组件

| 文件 | 职责 |
| --- | --- |
| `WebDavClient.kt` | PROPFIND/GET/PUT/MKCOL/DELETE；流式上传下载；返回 `DavResult` |
| `DavResult.kt` | `DavError`（Auth/NotFound/Quota/Parse/Network）+ `DavResult` |
| `SyncEngine.kt` | 单轮同步：目录准备 → 远端合并 → 上传 → 墓碑 → 维护/GC |
| `SyncManager.kt` | WorkManager 调度、前台轮询、通知 |
| `SyncStateStore.kt` | 增量账本（文件指纹/确认集/通知去重/dirsReady/维护时间） |
| `SyncStatusStore.kt` | `StateFlow<SyncStatus>`，UI 收集，不再解析字符串 |

### 单轮流程

1. 目录准备（只做一次：MKCOL 三层 + listDir records/photos）。若同端点下目录被删（listDir 404），抛 `CloudDirectoryMissingException` 并清 `dirsReady`，下一轮自动重建；单条记录 GET 404 只跳过该条
2. 旧迁移 id 修复（`L…` → `M-设备-…`）
3. 远端：PROPFIND records/photos → 墓碑优先 → 指纹跳过未变文件 → 下载 JSON（网络阶段）
4. **批量合并**：一个 Room 事务里 `mergeRemoteLocked`（字段级合并 note/reply）
5. 昵称传播、照片补漏（只查 DB 引用缺失）
6. 上传 dirty 记录 + 照片（流式）
7. 上传墓碑并删除云端记录文件
8. 维护（最多 6h 一次）：历史大图瘦身、本地孤儿照片 GC（与启动时共用 `cleanupOrphans`，>24h）
9. 云端照片 GC（每天一次，保守：记录解析失败则跳过；只删两边都不引用且 >7 天）
10. 写状态/账本；失败按 `retryable` 决定 WorkManager 是否重试

### 业务规则（不可违反）

- **note 归上传人写，reply 归对方写**；字段级时间戳合并，互不覆盖
- 删除必须「本地删除 + 墓碑 + 上传墓碑」；对方不能删除上传人的记录（UI 与 ViewModel 双拦截）
- 删除记录 + 写墓碑必须在同一 Room 事务
- `origin == "local"` 是旧迁移值，只有它能被认领为本机
- 删除/换照片后的云端照片只走保守 GC，不做即时远程删除（避免误删）

### 错误与重试

- `SyncPhase` 包含 `Idle / Running / Success / Partial / Failure`；`SyncStatus` 通过 `pending` 与 `skipped` 区分待重传和异常跳过。单轮虽成功但仍有待处理项时标为 `Partial`，不显示为完整成功；重启时从持久摘要恢复这部分状态。
- 401/403 → Auth；404 → NotFound；507 → Quota；解析失败 → Parse；其余 → Network
- Auth/Quota/Parse：不重试（状态条显示原因）；Network：指数退避
- 目录 404（records/photos listDir）：清 `dirsReady`，下一轮重建；单条记录 404：跳过，不重置
- 前台轮询 30s，失败退避到 5 分钟；不可重试错误停止轮询（回到前台/改配置后恢复）

## 4. UI 层

- `AppRoot`：Scaffold + NavHost +「穿搭 / 饮食 / 点滴」悬浮胶囊导航；内容使用实色主题表面，根层提供底栏 Haze 采样源。通知落点 `pendingTarget`（route/date/recordId）交给目标页消费后清空。
- 统一设置首页：三个主页右上角设置按钮均进入 `settings`，取代旧侧边抽屉；设置首页分组显示昵称、外观和有效同步/权限状态，返回后恢复原主栏。
- 五个设置模块：`settings/profile`、`settings/appearance`、`settings/sync`、`settings/backup`、`settings/battery`，各有独立页面和返回栈；`SettingsModule` 只提供 `key/title/icon` 与派生路由。
- 主题：`ThemeStore.mode` 暴露 SYSTEM/LIGHT/DARK 三档 `StateFlow`，默认跟随系统；Material `ColorScheme` 管理业务色与表面，`AppPalette` 管理发丝线、玻璃和滑块材质。`MainActivity` 稳定保留根导航、处理 `uiMode` 并更新系统栏及窗口底色。手动主题与系统相反时，OS 启动首帧仍可能使用系统色。
- 主页：`FloatingTopBar` + `PageHeader` + LazyColumn；共用大标题、右上设置/新增与收起态点击回顶部。穿搭保留月份/日期条，饮食保留日期与三餐，点滴展示纪念日与日常。展开页头高度用 `rememberSaveable` 与列表位置一起恢复，避免设置返回折叠列表时顶部留白归零。
- 底栏：保留滑块、点按切换、长按拖动、触觉与弹簧形变；图标下显示中文标签，按可用宽度与字体大小调整。真实模糊只用于导航，内容卡默认实色无投影。
- 图标与字体：`ArtworkIcon` 统一映射 13 套磨砂素材（包括 `Add`），未覆盖符号回退原生图标；原图与来源清单见 `output/imagegen/ios-icons/`，实际模型 ID 未暴露。全部文字使用 `FontFamily.Default` 跟随系统。
- 新增记录：统一 `AddRecordSheet`（按 IME 后可用区域计算约 86%、最大 720dp，内容可滚动、保存区固定、离开放弃确认）；编辑已有记录走全屏页。`SheetTextField` 与 `PlainField` 复用 `RecordTextField`，常驻字段名与 `BasicTextField.contentDescription` 保持一致；设置字段同样保留命名和至少 48dp 热区。
- 文案与权限：删除标语、示例和重复说明，保留真实作者、字段名、日期、数量、错误和必要确认；对方记录的标题/日期只读，保存只写 `reply`。表单继续用 `SavedStateHandle` 与 Compose 状态双写（`ui/SavedStateValues.kt`），云同步配置草稿放在页面 ViewModel 内存中。
- 同步状态：设置首页和云同步页面收集 `SyncStatusStore.status`，列表页据此显示照片同步失败与重试；`Partial`、失败和保存/恢复忙碌态不因精简文案而合并。

## 5. 约束

- 不引入 Hilt/Koin、Retrofit、新 UI 库；新增依赖需用户同意
- 不改同步协议/字段语义/墓碑规则，除非有迁移与回归
- 所有文案中文、无 emoji 当功能图标；品牌方向见 `docs/design-system.md`
- 改动同步/删除/解析后必须跑当前 33 项设备回归（含备份恢复）

## 6. 验证命令

```bash
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
./gradlew.bat :app:testDebugUnitTest --offline

python tools/sync_test_server.py --port 18765 --root .verification/dav
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline
adb install -r -t app/build/outputs/apk/debug/the_cutest_girl_v5.0.0_debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
```

跑完回归后必须用不带 `-PverificationApp` 的命令重新出正式包。

普通 debug 文件名随 `versionName` 和变体生成；5.0.0（46）沿用 4.3.1 固定签名，证书 SHA-256 为 `b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547`。5.0.0 已完成 Kotlin / AndroidTest 编译、19 项 JVM 单测、lint（0 错误 / 6 警告 / 2 信息）与独立 `.verification` 模拟器完整设备同步/备份回归 33 / 33，测试后重建正常包并通过 apksigner v2 及固定证书校验。交付产物为 `deliverables/the_cutest_girl_v5.0.0.apk`，证据见 `deliverables/release-v5.0.0.json`。

41 项 UI 场景与原始截图是在升级版本号前、同一功能代码的 4.3.1（45）构建上完成，详见 `deliverables/ui-refinement/validation.md`。这批 UI 场景没有全部在最终 5.0.0 APK 上重跑，不能与本版 33 项设备同步/备份回归混为同一套检查。
