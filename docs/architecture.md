# 架构说明（the cutest girl）

> 当前版本 4.2.0（versionCode 41）｜最后更新 2026-11-08

## 1. 总览

纯原生 Android 单模块应用，无后端、无 DI 框架：

- Kotlin 2.0.21 + Jetpack Compose（BOM 2024.10.01，Material 3）
- Room 2.6.1（数据库 v6，schema 导出在 `app/schemas/`）
- WorkManager 2.9.1（后台同步）+ 前台轮询
- OkHttp 4.12（手写 WebDAV 客户端）
- Coil 2.7（照片）
- Haze 1.1.1（仅底部导航栏的真实背景模糊）

包结构：

```
com.thecutestgirl.app/
├── MainActivity.kt        主题、通知落点、启动维护（草稿清理、同步排单）
├── data/                  Room 实体/DAO/迁移、照片、设置、备份导出
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

### 照片

`PhotoStore`（`data/PhotoStore.kt`）：

- 目录 `files/photos/`，数据库只存文件名
- 新选/新拍照片先落 `draft_` 草稿；保存时 `promoteDraft()` 晋级为 `img_`
- 启动清理超过 24h 的草稿；同步维护轮做本地孤儿 GC
- 原子写入（`.tmp` → rename，失败复制兜底）；压缩长边 1920 / JPEG 85

### 备份

`BackupExporter.export()`：`wal_checkpoint(TRUNCATE)` 后打包 DB（含 wal/shm 兜底）+ 非草稿照片 + `manifest.json`，不包含密码与同步账本。

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

1. 目录准备（只做一次：MKCOL 三层 + listDir records/photos）
2. 旧迁移 id 修复（`L…` → `M-设备-…`）
3. 远端：PROPFIND records/photos → 墓碑优先 → 指纹跳过未变文件 → 下载 JSON（网络阶段）
4. **批量合并**：一个 Room 事务里 `mergeRemoteLocked`（字段级合并 note/reply）
5. 昵称传播、照片补漏（只查 DB 引用缺失）
6. 上传 dirty 记录 + 照片（流式）
7. 上传墓碑并删除云端记录文件
8. 维护（最多 6h 一次）：历史大图瘦身、本地孤儿照片 GC（>24h）
9. 云端照片 GC（每天一次，保守：记录解析失败则跳过；只删两边都不引用且 >7 天）
10. 写状态/账本；失败按 `retryable` 决定 WorkManager 是否重试

### 业务规则（不可违反）

- **note 归上传人写，reply 归对方写**；字段级时间戳合并，互不覆盖
- 删除必须「本地删除 + 墓碑 + 上传墓碑」；对方不能删除上传人的记录（UI 与 ViewModel 双拦截）
- 删除记录 + 写墓碑必须在同一 Room 事务
- `origin == "local"` 是旧迁移值，只有它能被认领为本机
- 删除/换照片后的云端照片只走保守 GC，不做即时远程删除（避免误删）

### 错误与重试

- 401/403 → Auth；404 → NotFound；507 → Quota；解析失败 → Parse；其余 → Network
- Auth/NotFound/Quota/Parse：不重试（状态条显示原因）；Network：指数退避
- 前台轮询 30s，失败退避到 5 分钟；不可重试错误停止轮询（回到前台/改配置后恢复）

## 4. UI 层

- `AppRoot`：底部液态导航 + NavHost；持有底栏 Haze 采样源；通知落点 `pendingTarget`（route/date/recordId），页面消费后清空
- 列表页：`FloatingTopBar`（展开高度只记录一次）+ LazyColumn + 页面专属内容
- 新增记录：统一 `AddRecordSheet`（半屏、40% 遮罩、放弃确认）；编辑已有记录仍走全屏页
- 表单：`SavedStateHandle` + Compose 状态双写（`ui/SavedStateValues.kt`）
- 同步状态：设置页/三个列表页收集 `SyncStatusStore.status`

## 5. 约束

- 不引入 Hilt/Koin、Retrofit、新 UI 库；新增依赖需用户同意
- 不改同步协议/字段语义/墓碑规则，除非有迁移与回归
- 所有文案中文、无 emoji 当功能图标；品牌方向见 `docs/design-system.md`
- 改动同步/删除/解析后必须跑设备回归 29 项

## 6. 验证命令

```bash
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
./gradlew.bat :app:testDebugUnitTest --offline

python tools/sync_test_server.py --port 18765 --root .verification/dav
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline
adb install -r -t app/build/outputs/apk/debug/the_cutest_girl_v4.2.0_debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
```

跑完回归后必须用不带 `-PverificationApp` 的命令重新出正式包。