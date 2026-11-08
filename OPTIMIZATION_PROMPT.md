# 任务：TheCutestGirl Android App 质量与体验优化（P0 / P1 / P2 全量实施）

## 0. 你的角色与目标

你是这个 Android 项目的资深工程师。项目是一个给一对情侣记录穿搭、三餐、点滴的私人 App（Jetpack Compose + Room + WorkManager + WebDAV 同步）。

你的任务不是“提建议”，而是**按下面的批次真正修改代码、补测试、跑验证、更新文档**。用户是代码小白，最终汇报必须用中文、说人话，讲清楚「改了什么 / 为什么 / 怎么验证 / 有没有风险」。

项目路径：
`E:\project\AI_seession\zcode\TheCutestGirl`

---

## 1. 项目事实（以此为准，不要相信旧文档）

- 当前 `app/build.gradle.kts`：`versionCode = 39`、`versionName = "4.0.0"`。
- 当前 UI 是 **v4.0 深色玻璃风格**，不是 `AI_HANDOFF.md` 里写的 v3.0.4 奶油玫瑰风。
- `AI_HANDOFF.md` 最后更新停在 v3.0.4，§3/§4 里大量组件名已不存在：
  - 已删除：`Accent.kt`、`EmojiTitle`、`GlassCapsuleButton`、`GlassCircleButton`、`glassPhotoFrame`、`moduleGlow`、`GlassTopBar`、`GlassTopPanel`、`glassStatic`。
  - 当前使用：`glassPanel`、`FloatingTopBar`、`SoftCapsuleButton`、`SolidCircleButton`、`photoShape`、`photoFrame`、`AddRecordSheet`、`quietTopAppBar`。
- 三个列表页（穿搭/饮食/点滴）已经切到 v4；编辑页、上传页、设置页仍是旧的 `TopAppBar + OutlinedButton` 语言。
- 当前主要文件（32 个 Kotlin 文件，约 1 万行）：

```
data/       Records.kt  Daos.kt  AppDatabase.kt  PhotoStore.kt  SettingsStore.kt
sync/       SyncEngine.kt  SyncManager.kt  SyncStateStore.kt  WebDavClient.kt
ui/         AppRoot.kt  MainActivity.kt
components/ Glass.kt  GlassBottomBar.kt  Common.kt  AddRecordSheet.kt
outfit/     OutfitScreen.kt  OutfitEditScreen.kt  OutfitUploadScreen.kt  OutfitViewModel.kt
meal/       MealScreen.kt  MealEditScreen.kt  MealViewModel.kt
event/      EventScreen.kt  EventEditScreen.kt  EventViewModel.kt
settings/   SettingsScreen.kt
theme/      Color.kt  Type.kt  Shape.kt  Theme.kt
```

---

## 2. 绝对约束（违反会导致返工或数据事故）

### 2.1 构建环境

```bash
cd /e/project/AI_seession/zcode/TheCutestGirl
export JAVA_HOME="E:/appdate/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="E:/appdate/gradle-home"
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

- 必须用 JDK 17，不要用 Android Studio 自带的 JDK 25。
- 项目目前**没有 git**。开始改动前建议先询问用户是否同意 `git init` 并提交一个基线；如果用户同意，再做。不要把 `tmp/`、`app/build/`、`.gradle/`、`.kotlin/` 提交进去。
- 不要动 `tmp/imagegen/` 里 vendored 的 Python 包，也不要把它们纳入任何构建。

### 2.2 数据安全

- **绝对不要**对正式包（`com.thecutestgirl.app`）做 `pm clear`、卸载、删数据库、删照片的测试。
- 同步回归必须用验证变体：
  ```bash
  ./gradlew.bat :app:assembleDebug -PverificationApp --offline -q
  ```
  验证包 applicationId 会变成 `com.thecutestgirl.app.verification`，测试服务器用 `tools/sync_test_server.py`。
- 改动 `Records.kt` 字段后，androidTest 必须加 `--rerun-tasks`，否则会报 `NoSuchMethodError`。
- 跑完回归必须用**不带** `-PverificationApp` 的命令重新出正式 APK，否则 `output-metadata.json` 的 applicationId 会停留在 `.verification`。
- 任何数据库迁移、照片清理、云端删除功能，先在本地/模拟器用测试数据验证，再让用户装机。

### 2.3 同步协议与业务规则（不能擅自改）

- 备注 `note` 永远归上传人写，评论 `reply` 永远归对方写；`noteDevice/noteName` 保留在 schema 里但不参与权限判定。
- 删除必须走「本地删除 + tombstones 表 + 上传墓碑」，不能让记录复活。
- 云端目录：`/thecutestgirl/records/<id>.json`、`/thecutestgirl/records/T_<id>.json`、`/thecutestgirl/photos/<name>.jpg`。
- `origin == "local"` 是旧迁移记录的特殊值；只有它才能被认领为本机，别把对方的 `origin` 抢走。
- 不要在没有迁移和回归测试的情况下改 JSON 字段语义、主键格式、墓碑规则。

### 2.4 UI 约束

- 当前是 v4.0 深色玻璃风格：背景 `#060607`、卡片 `#0D0D12`、主色梅紫 `#B9A0FF`、饮食钴蓝 `#7FA6FF`、点滴余烬橙 `#FFA47C`。
- 新代码必须复用现有组件，不要另起一套视觉语言：
  - 卡片：`Modifier.glassPanel(...)` / `Modifier.solidCard(...)`
  - 顶栏：`FloatingTopBar` / `quietTopAppBar`
  - 按钮：`PrimaryButton` / `SoftCapsuleButton` / `SolidCircleButton`
  - 输入：`PlainField` / `SheetTextField` / `SettingsField`
  - 抽屉：`AddRecordSheet` + `SheetDateRow` / `SheetPhotoPicker` / `SheetTextField` / `SheetSegmented`
- 8dp 网格；卡片圆角 16、按钮/输入 8、抽屉顶部 24；不要新增 12/18/20/26 之类的一次性圆角（现有 18 的调用点这轮顺手收敛）。
- 全部面向用户的文案保持中文；不要加 emoji（状态表达只用线性图标 + 语义色）。
- 不要为了“更好看”擅自恢复浅色主题或奶油玫瑰配色；主题方向见 P1-UI-11，先问用户。

### 2.5 版本号

- 用户没有明确指定新版本号之前，**不要**改 `versionCode/versionName`；保持 `39 / 4.0.0`。
- 如果用户要求打包，先问「这轮版本号用多少」。

### 2.6 代码风格

- 最小 diff：不要整文件重排、不要顺手改无关代码。
- 不要吞掉 `CancellationException`：`runCatching` 包网络/IO 时，必须重新抛出取消异常。
- 不要新增权限、不要新增后台服务、不要引入付费依赖。
- 新增依赖必须先说明用途、体积、兼容性，并得到用户同意。

---

## 3. 工作方法

1. 先跑一次基线：编译 + lint + 现有 26 项回归，记录结果。
2. 按批次实施，不要一次性全改。每批完成后：
   - 编译通过；
   - lint 没有新增 warning；
   - 相关回归通过；
   - 需要看界面的批次，模拟器截图；
   - 中文汇报，等用户确认后再进入下一批。
3. 每批开始前重新读相关文件，**行号可能已经变化，以函数名/文件路径为准**。
4. 优先修「用户能感知到的坏功能」和「会丢数据/坏数据」的问题，再做视觉打磨。
5. 不要在没有装机/截图证据时声称「界面已优化好」；没验证就明确写「未装机验证」。

---

# 第一批：P0 正确性 / 数据安全（必须最先做）

## P0-1 修复单图「换一张」不生效，并清理孤儿照片

**问题**
- `Common.kt` 的 `PhotoNoteCard` 里，相册单选和相机回调都是 `onPhotosChanged(currentPhotos + newName)`，不管 `maxPhotos` 是多少。
- 单图编辑页（OutfitEdit / MealEdit / EventEdit）用 `it.firstOrNull()` 取第一张，所以新选的照片被追加在第二位后又被丢弃，用户看到「换了一张」但保存还是旧图。
- 穿搭编辑页 `allowRemove = false`，用户连先删后换都做不到。
- 多选时 `saveFromUri` 已经把多余照片复制进私有目录，随后 `take(maxPhotos)` 丢弃，产生孤儿文件。

**实施**
1. 在 `Common.kt` 新增纯函数（便于单测）：
   ```kotlin
   internal fun nextPhotoList(
       current: List<String>,
       added: List<String>,
       maxPhotos: Int
   ): Pair<List<String>, List<String>> {
       // 返回 (结果列表, 需要删除的孤儿)
   }
   ```
   规则：
   - `maxPhotos == 1`：结果 = `added.takeLast(1)`，原来的照片全部进孤儿列表。
   - 多图：结果 = `(current + added).take(maxPhotos)`，超出的进孤儿列表。
2. `PhotoNoteCard` 的 `pickSingle`、`pickMultiple`、相机回调全部走这个函数；孤儿文件用 `PhotoStore.delete` 删除（在 IO 线程）。
3. `pickMultiple` 回调里先限制数量再复制，或复制后删多余；不要留下 `img_*.jpg` 孤儿。
4. `SheetPhotoPicker`（`AddRecordSheet.kt`）同样处理：`onAdd(saved.take(maxPhotos - photos.size))` 之前/之后删除多余文件。
5. 穿搭编辑页继续 `allowRemove = false` 没问题，但「换一张」必须真正替换。

**验收**
- 单图编辑页：选新图 → 卡片只显示新图 → 保存后数据库和列表都是新图。
- 相机「拍一张」同样替换。
- 多选 10 张、上限 9 张：只保留 9 张，`files/photos/` 里没有多出来的孤儿。
- 补一个纯函数单元测试覆盖 `maxPhotos=1` 和多图两种分支。

---

## P0-2 禁止对方删除上传人的记录

**问题**
- `OutfitEditScreen.kt`、`EventEditScreen.kt` 只要 `id != null` 就显示删除按钮；`OutfitUploadScreen.kt` 可删除当天任意照片；三个 `delete()` 都没有检查 `amUploader`。
- 结果：对方打开的记录可以删掉上传人的记录，并上传墓碑，双方数据都消失。

**实施**
1. 三个 EditViewModel 增加：
   ```kotlin
   val canDelete: Boolean get() = amUploader
   ```
2. `OutfitEditScreen`、`MealEditScreen`、`EventEditScreen` 的删除按钮改为 `if (id != null && vm.canDelete)`。
3. `OutfitUploadScreen.ExistingThumb` 的删除键只对 `record.origin == myDeviceId`（或 `origin` 为空/local）显示；对方的照片只读。
4. 如果产品希望「对方也能删」，不要直接传墓碑；应先问用户，设计「只从我这删 / 双方都删」两个选项。本轮默认**不允许**。
5. 保存路径里也加一道防御：`delete()` 里如果 `!canDelete` 直接返回（UI 是第一道门，VM 是第二道门）。

**验收**
- 用对方 origin 的记录打开编辑页：没有删除按钮。
- 直接调用 `delete()`（测试里）不会删记录、不会写墓碑。
- 自己的记录删除行为不变。

---

## P0-3 删除记录 + 写墓碑放进同一个 Room 事务

**问题**
- 多个 ViewModel 都是先 `dao.delete(record)`，再 `tombstoneDao.insert(...)`。
- 两步之间进程被杀/异常，会「本地记录没了、墓碑没写」，下一轮同步云端记录重新下载，记录复活。

**实施**
1. 在 `AppDatabase` 或新的 `RecordRepository` 里提供：
   ```kotlin
   suspend fun deleteOutfitWithTombstone(record: OutfitRecord) = db.withTransaction {
       outfitDao.delete(record)
       tombstoneDao.insert(Tombstone(record.id, System.currentTimeMillis()))
   }
   ```
   meal/event 同理。
2. 替换以下调用点：
   - `OutfitEditViewModel.delete`
   - `MealEditViewModel.delete`
   - `EventEditViewModel.delete`
   - `OutfitUploadViewModel.deleteExisting`
   - `EventViewModel.delete`
3. 照片删除放在事务**之后**（文件删除不参与数据库事务）；照片删除失败只记录日志，不影响记录删除。
4. 删除成功后照常 `SyncManager.enqueueNow`。

**验收**
- 删除后立即查库：记录不存在、墓碑存在。
- 模拟在两步之间抛异常（测试里）：事务回滚，记录和墓碑都不变。
- 对方同步后记录不复活。

---

## P0-4 换云盘配置：先测试成功，再保存

**问题**
- `SettingsScreen.startTest()` 先调用 `SyncEngine.applyDavConfig(...)`，它内部会 `saveDavConfig`，端点变化时还会 `resetForNewEndpoint + clearFirstSyncDone`。
- 用户地址/账号/密码打错一个字符，点「测试并开启同步」，旧配置已经被覆盖，同步账本也已经重置；退回设置页后同步就坏了。

**实施**
1. 新增「先验证、后提交」的入口，例如：
   ```kotlin
   sealed class DavTestResult {
       data class Ok(val changed: Boolean) : DavTestResult()
       data class Failure(val message: String) : DavTestResult()
   }

   suspend fun verifyAndApplyDavConfig(
       context: Context, url: String, user: String, pass: String
   ): DavTestResult
   ```
   流程：
   - 归一化用 `SettingsStore` 同一套规则（`url.trim().ifBlank { DEFAULT_DAV_URL }`、`user.trim()`、`pass` 不 trim）。
   - 用**临时** `WebDavClient` 对候选配置 `testConnection()`。
   - 如果端点变化（`davConfigChanged`）：对候选配置执行 MKCOL 三层目录，并实际 `listDir("records")` + `listDir("photos")`；都成功才继续。
   - 全部成功后才 `saveDavConfig`，然后重置账本、清 `first_sync_done`、`setDirsReady(true)`。
   - 失败直接返回 `Failure`，不动旧配置、不动账本。
2. `SettingsScreen.startTest()` 改用新入口；成功后再 `ensurePeriodic + enqueueNow + 请求通知权限`。
3. 端点变化时先弹确认框：「换服务器会让本机重新全量拉取一次，但不会删除本地数据。继续？」
4. 测试失败时在状态条显示可操作原因（账号密码错误 / 目录建不出来 / 网络不通），不要只显示「失败」。

**验收**
- 输入错误密码点测试：旧配置仍在，旧账本仍在，同步还能继续用。
- 输入新地址且目录可建：测试成功后新配置生效、账本重置、目录存在。
- 新地址目录建不出来：返回失败，不保存、不重置。

---

## P0-5 修复 `dirsReady` 提前置 true / 失败不重置

**问题**
- `SyncEngine.kt` 开头三次 `dav.mkcol(...)` 的返回值被忽略，随后直接 `SyncStateStore.setDirsReady(context, true)`。
- 注释说「任何一次同步失败都会把标记清掉（见 catch）」，但 `catch` 里没有 `setDirsReady(false)`。
- 新云盘如果 records/photos 没建出来，会永远报「读不到云端记录目录」，并且以后再也不 MKCOL。
- `testConnection(prepareDirectories = true)` 只验证 records，不验证 photos。

**实施**
1. 目录准备改成：
   ```kotlin
   if (!SyncStateStore.dirsReady(context)) {
       val ok = dav.mkcol("thecutestgirl") &&
                dav.mkcol("thecutestgirl/records") &&
                dav.mkcol("thecutestgirl/photos") &&
                dav.listDir("thecutestgirl/records") != null &&
                dav.listDir("thecutestgirl/photos") != null
       SyncStateStore.setDirsReady(context, ok)
       if (!ok) throw IOException("云端目录准备失败，请检查账号、配额或网络")
   }
   ```
   `mkcol` 返回 405（已存在）仍算成功，保留现有逻辑。
2. `catch` 里只在「目录准备失败」时 `setDirsReady(false)`；不要把所有网络失败都重置，否则每轮都多 3 次 MKCOL。可以用自定义异常 `DirectorySetupException` 区分。
3. `testConnection(prepareDirectories = true)` 同样检查 records 和 photos 两个目录，全部成功才 `setDirsReady(true)`。
4. 保留「`syncOnceLocked` 里 `if (!dirsReady)` 是主路径」的设计，不要删。

**验收**
- 新端点没有 `thecutestgirl` 目录：第一次同步自动建出两个子目录，并成功 listDir。
- 模拟 records 建成功、photos 失败：`dirsReady` 为 false，下一轮重试，不会永久失败。
- 目录正常时，每轮不再重复 MKCOL。

---

## P0-6 照片写入原子化 + 流式下载/上传

**问题**
- `SyncEngine` 下载照片直接 `FileOutputStream(最终文件).write(bytes)`；写到一半被杀/磁盘满会留下半个文件。
- 后续同步只判断 `exists()`，会把半个文件当成「已下载」，这张照片永远坏。
- `getBytes`/`putBytes(local.readBytes())` 会把整张图读进内存，历史大图可能 OOM。
- `PhotoStore.saveFromUri` 直接写最终文件，失败会留部分文件。
- `PhotoStore.compressInPlace` 里 `file.delete(); tmp.renameTo(file)` 没有检查 rename 是否成功，极端情况下原图被删、tmp 没改名。

**实施**
1. `PhotoStore` 新增：
   ```kotlin
   fun writeAtomically(target: File, bytes: ByteArray): Boolean
   fun copyAtomically(target: File, input: InputStream): Boolean
   ```
   流程：写 `target.name + ".tmp"` → `flush`/`fd.sync`（可选）→ 校验长度 > 0 → `renameTo`；失败时 `copyTo(overwrite = true)` 兜底；最后删 tmp。
2. `SyncEngine` 下载记录照片、补漏照片、瘦身覆盖本地照片全部改走原子写入。
3. `WebDavClient` 增加流式方法：
   ```kotlin
   fun downloadTo(path: String, target: File): Boolean
   fun uploadFrom(path: String, file: File, contentType: String): Boolean
   ```
   内部用 `resp.body.byteStream().copyTo(out)` / `file.inputStream().toRequestBody(...)`，不要 `bytes()` / `readBytes()`。
4. 记录 JSON 仍然可以 `getString`（很小），照片必须流式。
5. `PhotoStore.saveFromUri` 改成先复制到临时文件，再压缩，再原子改名为最终文件；失败时删临时文件。
6. `compressInPlace` 的 rename 失败兜底：`if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }`，不要先删原图。

**验收**
- 下载中断（测试里让服务器提前断开）不会在 `files/photos/` 留下目标文件；下一轮会重新下载。
- 上传/下载一张 10MB 测试图不 OOM。
- 压缩失败时原图不丢。

---

## P0-7 同步任务去重 + 降低启动时的重复入队

**问题**
- `SyncManager.enqueueNow` 只 `addTag(ONCE_NAME)`，没有 `enqueueUniqueWork`。
- `MainActivity.onCreate` 每次旋转屏幕/重建 Activity 都调用 `startAfterConfigured`，会排入多个 OneTimeWork。
- `ensurePeriodic` 每次启动都 `UPDATE`，可能重置下一次周期任务的执行时间（不同 WorkManager 版本行为有差异）。

**实施**
1. `enqueueNow` 改成：
   ```kotlin
   private var lastEnqueueAt = 0L
   fun enqueueNow(context: Context) {
       if (!SettingsStore.isSyncConfigured(context)) return
       val now = SystemClock.elapsedRealtime()
       if (now - lastEnqueueAt < 3_000L) return
       lastEnqueueAt = now
       val request = OneTimeWorkRequestBuilder<SyncWorker>()
           .setConstraints(...)
           .build()
       WorkManager.getInstance(context)
           .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
   }
   ```
   注意：`ONCE_NAME` 现在同时用作 tag 和 unique name，确认不会和其他任务冲突。
2. `ensurePeriodic` 不要在每次启动都 UPDATE。两种做法二选一：
   - 简单：用 `ExistingPeriodicWorkPolicy.KEEP`，只有首次注册；用户改配置/换云盘时显式 `UPDATE`。
   - 更稳：先 `getWorkInfosForUniqueWork(PERIODIC_NAME).get()`，为空才 enqueue；配置变化时用 UPDATE。
3. `MainActivity` 只在 `savedInstanceState == null`（真正首次创建）时调用 `startAfterConfigured`，旋转不要反复入队。
4. `SyncWorker` 的失败处理见 P2-3：认证/404 不应该无限 `retry()`。

**验收**
- 旋转屏幕 5 次，`adb shell dumpsys jobscheduler`/WorkManager 里只有一个待执行的一次性同步。
- 连续点「立即同步」5 次，3 秒内只入队一次。
- 周期任务不会因为每次打开 App 被无限推迟。

---

# 第二批：P1 UI / 交互

## P1-UI-1 饮食页「对方的记录」可点开写评论

**问题**
- `MealScreen.kt` 里对方记录 `clickable = false`，`onOpenMeal` 只对 `mine` 调用；饮食的评论功能实际上不可用。

**实施**
1. `MealSection` 增加 `onOpenTheirs: (MealRecord) -> Unit`，对方卡片也 `clickable = true`。
2. `MealScreen` 打开编辑页：
   ```kotlin
   onOpenMeal(date, mealType, record.id)
   ```
3. `MealEditScreen` / `MealEditViewModel` 已经能按 `amUploader` 区分权限：备注/照片只读、只允许写评论；确认这条路径可用。
4. 对方的照片仍应能点开大图；卡片点击区域和照片点击区域不要互相打架（照片点击优先看大图，卡片其余区域进编辑页，或整卡进编辑页、照片内部再处理）。

**验收**
- 点对方的三餐卡片能进入编辑页；备注/照片只读；评论可写；保存后只写 `reply`，不碰 `note/photo/date`。
- 点自己的卡片行为不变。

---

## P1-UI-2 通知点进「点滴」时定位到对应记录

**问题**
- `AppRoot.kt` 只把 `date` 传给 Outfit/Meal；`EventScreen` 没有 `initialDate/initialRecordId`。
- `PartnerItem` 有 `recordId`，但通知只带了 route/date。

**实施**
1. `PartnerNotifier` 增加 `EXTRA_RECORD_ID`，把 `target.recordId` 放进 Intent。
2. `MainActivity.NotificationTarget` 增加 `recordId`，`toNotificationTarget()` 读取。
3. `AppRoot` 把 target 传给对应页面：
   - Outfit/Meal：date 足够；
   - Event：传 `initialDate` + `initialRecordId`。
4. `EventScreen`：
   - 新增 `initialDate: LocalDate?`、`initialRecordId: String?`；
   - `LazyListState` 在数据加载后 `scrollToItem` 到匹配 `id` 的 moment；如果找不到，退回到该日期附近；
   - 用 `rememberSaveable` 记住「已消费的 recordId」，避免切 tab 回来重复滚。
5. 通知里如果有多条不同类型的新内容，仍然定位到最后一条；摘要保留。

**验收**
- 对方新增一条点滴 → 通知 → 点击 → 打开点滴 tab 并滚到那条卡片。
- 穿搭/三餐通知仍然定位到正确日期。

---

## P1-UI-3 `pendingDate` 用完即清，避免串到别的 tab

**问题**
- `AppRoot.kt` 的 `pendingDate` 在通知消费后没有清空；之后切到另一个 tab，那个页面会拿到上一次通知的日期并跳过去。

**实施**
1. 把 `pendingDate: String?` 改成 `pendingTarget: NotificationTarget?`（含 route/date/recordId）。
2. 每个列表页接收 `initialDate` 时只在该页 route 等于 target.route 时传；消费后调用 `onNotificationConsumed()` 清空。
3. 或者更简单：`pendingTarget` 只在 `currentRoute == route` 时传给该页面，并在 `LaunchedEffect(target)` 里滚动完成后清空。
4. 用 `rememberSaveable` 记录已消费的 `route+date+recordId`，避免旋转后重复跳。

**验收**
- 收到三餐通知并点开，停在对应日期；再切到穿搭页，穿搭页停在今天/用户上次的日期，不会被拽到三餐那天。
- 连续来两条不同 tab 的通知，第二次点击仍能跳。

---

## P1-UI-4 新增记录抽屉：照片不溢出、键盘可用、误关有确认

**问题**
- `AddRecordSheet.kt` 的 `SheetPhotoPicker` 用一个普通 `Row` 平铺所有缩略图，9 张会横向溢出。
- `AddRecordSheetContent` 固定 `fillMaxHeight(0.55f)`，键盘弹出后内容区很小。
- 下滑关闭会静默丢弃已写文字和已复制照片。

**实施**
1. 缩略图改 `LazyRow`（或 `FlowRow`），每张 72dp，间距 8dp；数量多时可横向滚动。
2. 选择照片时先算剩余名额，再复制；多余 URI 不要复制，或复制后立刻删除。
3. 抽屉高度改成：
   ```kotlin
   Modifier
       .fillMaxWidth()
       .heightIn(max = 0.85f * screenHeight)  // 用 LocalConfiguration 或 BoxWithConstraints
   ```
   内容区用 `verticalScroll`；不要同时叠加 `imePadding` 和 `navigationBarsPadding` 造成双倍留白，按实际测试调。
4. 新增 `unsavedChanges: Boolean` 参数；`onDismissRequest` 时如果为 true，先弹 `AlertDialog`「放弃这次编辑？」，确认后才 `onDismiss`。三个调用点（Outfit/Meal/Event）传入各自的 dirty 判断。
5. `MealScreen` 的 `SheetDateRow(onClick = {})` 是假可点：要么让它打开日期选择并真正改变 `date`，要么改成不可点的信息行。默认改成不可点（本轮不扩展「跨日期记一餐」）。

**验收**
- 选 9 张照片，缩略图可以横向滚动，不超出屏幕。
- 键盘弹出时仍能看到输入框和保存按钮。
- 写了内容后下滑关闭，会弹确认；确认后照片文件被删除，无孤儿。
- 饮食抽屉的日期行不再假装可点。

---

## P1-UI-5 `SoftCapsuleButton` 禁用态可见

**问题**
- `Glass.kt` 的 `SoftCapsuleButton` 在 `enabled = false` 时只禁用点击，颜色/透明度不变，看起来仍可点。
- `SheetPhotoPicker` 到达上限后两个按钮尤其明显。

**实施**
1. 在按钮的 `Row` 上加：
   ```kotlin
   .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
   ```
2. 保留 `clickable(enabled = enabled)`；不要给禁用态加按压反馈。
3. 检查 `PrimaryButton` 已有禁用色，不用改。

**验收**
- 照片达到上限时「加照片/拍一张」明显变灰，点击无反应。
- 其他禁用按钮（设置页「已加入白名单」）也一致。

---

## P1-UI-6 编辑页 / 上传页 / 设置页切到 v4 语言

**问题**
- 这四个页面仍用 `TopAppBar`、`OutlinedButton`、旧间距，与三个列表页不一致。
- 根 `AppRoot` 已经给内容加了顶部状态栏 inset；内层 `TopAppBar` 默认还会再加一次，可能造成双倍留白。

**实施**
1. 新建一个统一顶栏组件（放在 `Common.kt` 或 `Glass.kt`）：
   ```kotlin
   @Composable
   fun AppTopBar(
       title: String,
       icon: ImageVector,
       onBack: () -> Unit,
       actions: @Composable RowScope.() -> Unit = {}
   )
   ```
   视觉用 `quietTopAppBar()` + `IconTitle`，`windowInsets = WindowInsets(0)`（因为根已经处理状态栏）。
2. 替换 OutfitEdit / MealEdit / EventEdit / OutfitUpload / Settings 的 `TopAppBar`。
3. 删除按钮从 `OutlinedButton` 改为统一的 destructive 行/胶囊：
   - 不要系统 ripple；
   - 错误色文字；
   - 48dp 高；
   - 只在 `canDelete` 时显示。
4. 编辑页「保存」可以保留顶栏文字按钮，但颜色/字重和 `PrimaryButton` 一致；或者改成底部全宽 `PrimaryButton`，与抽屉一致。二选一，不要两种混用。
5. 设置页去掉「卡中卡」：`SettingsActionRow` 在 `SettingsSection` 内部时不要再调 `solidCard`；可以给它加 `container: Boolean = true` 参数，组内传 false。
6. 统一输入：
   - 编辑页继续用 `PlainField` / `PhotoNoteCard`；
   - 设置页继续用 `SettingsField`；
   - 抽屉用 `SheetTextField`；
   - 如果时间允许，抽一个 `AppTextField`，本轮至少保证三处视觉一致（无外框、8dp 圆角、主色光标、14sp 标签）。

**验收**
- 四个页面与列表页同一套顶栏、按钮、输入视觉。
- 状态栏没有双倍空白（装机截图确认）。
- 设置页不再出现卡片套卡片。
- 删除按钮只在允许时出现。

---

## P1-UI-7 饮食 / 点滴编辑页图片比例与列表一致

**问题**
- `PhotoNoteCard` 默认 `photoRatio = 4f / 5f`，但列表页饮食/点滴是 3:2；编辑页没有显式传 3:2。

**实施**
- `MealEditScreen.kt`、`EventEditScreen.kt` 的 `PhotoNoteCard` 传 `photoRatio = 3f / 2f`。
- 穿搭编辑/上传保持 4:5。
- 检查 `PhotoNoteCard` 多图网格是否受 `photoRatio` 影响；如果多图也应 4:5，就不要把默认值改掉，只在单图调用点传。

**验收**
- 饮食/点滴编辑页照片比例与列表卡一致，不出现「列表 3:2、编辑 4:5」的裁切跳变。

---

## P1-UI-8 设置入口可发现

**问题**
- 齿轮只在穿搭页；饮食/点滴只能长按标题进设置，用户很难发现。

**实施**
- 先问用户是否接受「三页都放齿轮」。如果用户没有意见，默认：
  - 三个列表页的 `FloatingTopBar` 都传 `onOpenSettings`；
  - 保留长按标题作为快捷方式。
- 如果用户坚持「只留穿搭页齿轮」，至少在饮食/点滴空态或首次启动加一句提示：「长按标题可打开设置」。

**验收**
- 从任意 tab 都能在 1 次点击内进设置（或至少有明确提示）。

---

## P1-UI-9 设置页去嵌套卡片

**问题**
- `SettingsSection` 是 `solidCard(18)`，里面的 `SettingsActionRow` 又是 `solidCard(16)`；「后台不被冻结」分组出现卡中卡。

**实施**
- `SettingsActionRow` 增加 `container: Boolean = true`；在 `SettingsSection` 内传 `false`，只保留整行点击和文字/图标。
- 组内用 `SettingsDivider()` 分隔，不要再叠卡片。
- 独立的「立即同步」行可以保留卡片，或包进自己的 `SettingsSection`。

**验收**
- 设置页每一组只有一层卡片；截图确认。

---

## P1-UI-10 深色启动闪屏

**问题**
- 当前没有 `values-v31/themes.xml`；Android 12+ 启动时可能先用白色系统背景，再进深色 App。仓库里 `.verification/screens/v400/01-outfit.png` 看起来就是白底启动页。

**实施**
1. 新增 `app/src/main/res/values-v31/themes.xml`：
   ```xml
   <resources>
       <style name="Theme.TheCutestGirl" parent="android:Theme.Material.NoActionBar">
           <item name="android:windowSplashScreenBackground">#060607</item>
           <item name="android:windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
           <item name="android:windowLightStatusBar">false</item>
           <item name="android:windowBackground">#060607</item>
       </style>
   </resources>
   ```
2. 检查 `values/themes.xml` 与 `values-night/themes.xml` 的 `windowBackground` 也是 `#060607`。
3. 装机冷启动截图，确认没有白闪；注意 Android 12 启动图标会被系统裁切，选合适的图标资源。

**验收**
- 冷启动第一帧是深色，不是白底。
- 状态栏图标为浅色。

---

## P1-UI-11 主题方向确认（决策门）

**问题**
- `Theme.kt` 固定 `DarkColors`，`darkTheme` 参数完全没用；README 却写「自动适配深色模式」，旧交接文档写的是奶油玫瑰浅色。

**实施**
- 先问用户：
  - A. 确认 v4.0 只做深色：删除无用的 `darkTheme` 参数，更新 README 和交接文档；
  - B. 恢复自动浅/深色：补一套浅色 `ColorScheme`，用 `isSystemInDarkTheme()`，并逐页检查玻璃/内发光/文字对比度。
- 在用户拍板前不要擅自改。默认按 A 处理（删参数 + 更新文档），因为当前代码和截图都是深色。

**验收**
- 文档与代码一致；没有「README 说自动、实际强制深色」的矛盾。

---

## P1-UI-12 跨午夜自动刷新日期

**问题**
- 多处 `remember { LocalDate.now() }`（OutfitScreen、EventScreen 的 AnniversaryCard 等），App 挂一夜后「今天」「第 N 天」还是昨天的。

**实施**
1. 新增：
   ```kotlin
   @Composable
   fun rememberToday(): LocalDate
   ```
   实现：`produceState(initialValue = LocalDate.now())`，监听 `Lifecycle.Event.ON_RESUME` 刷新；同时计算到下一个午夜的 `delay`，到点再刷新（不要每分钟轮询）。
2. 替换 OutfitScreen、MealScreen、EventScreen、AnniversaryCard 里的 `remember { LocalDate.now() }` / 直接 `LocalDate.now()`。
3. `isCurrentMonth` 用 `YearMonth.from(today)` 计算。

**验收**
- 把模拟器时间调到 23:59，等过午夜，页面「今天/第 N 天」自动更新；从后台回前台也会刷新。

---

## P1-UI-13 最小触摸热区 48dp

**问题**
- 照片右上角删除键 32dp，`OutfitUploadScreen` 的删除键也是 32dp，低于 Android 建议的 48dp。

**实施**
- 视觉保持 32dp，外层用 48dp `Box` 承载 clickable；或使用 `Modifier.minimumInteractiveComponentSize()`。
- 检查 `PhotoRemoveButton`、`ExistingThumb` 的删除键、大图关闭等。

**验收**
- TalkBack/手指点击删除键更容易命中；视觉尺寸不变。

---

## P1-UI-14 大图支持缩放 / 左右滑

**问题**
- `PhotoViewerDialog` 只显示单张 `ContentScale.Fit`，不能缩放、不能切下一张。

**实施**
1. 增加 `photos: List<String>` + `initialIndex` 参数；保留单图调用的重载。
2. 用 `HorizontalPager` 支持左右滑。
3. 用 `detectTransformGestures` / `graphicsLayer` 实现双指缩放 + 双击放大；单击关闭。
4. 更新 `PhotoNoteCard` 的 viewer 调用，传入全部照片；Meal/Event 单图调用保持简单。

**验收**
- 多图卡片点开可以左右滑；双指能放大缩小；单击退出。
- 单图场景行为不变。

---

# 第三批：P1 性能

## P1-PERF-1 删除三个主页无用的 `pageHaze`

**问题**
- `OutfitScreen.kt`、`MealScreen.kt`、`EventScreen.kt` 各自 `remember { HazeState() }` 并 `.haze(pageHaze)`，但全仓没有对应的 `hazeChild(pageHaze)`。
- 底栏用的是 `AppRoot` 的 `hazeState`；这三个是纯开销/死代码。

**实施**
- 删除三个文件里的 `val pageHaze = remember { HazeState() }` 和 `.haze(pageHaze)`。
- 保留 `AppRoot` 的 `hazeState` + `GlassBottomBar` 的 `hazeChild`。
- 检查删除后底栏模糊仍然正常（内容区仍由 AppRoot 的 `.haze(hazeState)` 提供采样）。

**验收**
- 底栏毛玻璃不变；三个页面没有多余的 HazeState；滚动帧率不下降。

---

## P1-PERF-2 顶栏折叠不要每帧改 `contentPadding`

**问题**
- 三个主页用 `onSizeChanged` 把顶栏高度写进 `headerHeightPx`，折叠动画期间高度每帧变化，`topArea` 跟着变，LazyColumn 的 `contentPadding` 每帧变，触发整表重新布局。

**实施**
1. 只记录**展开态**高度：
   ```kotlin
   var expandedHeaderHeightPx by remember { mutableIntStateOf(0) }
   val topArea = with(density) { expandedHeaderHeightPx.toDp() } + 10.dp
   ```
   `onSizeChanged` 里只在 `!scrolled` 时更新；折叠过程中不再改 `topArea`。
2. 折叠后顶栏覆盖在内容上方；因为用户已经滚动，固定顶部 padding 不会让首条内容被永久遮住。滚回顶部时 `scrolled` 变 false，顶栏展开，留白正确。
3. 如果产品要求「折叠时内容跟着上移」的视觉效果，用 `graphicsLayer { translationY = ... }` 做视觉位移，不要改布局约束。
4. 三个页面统一改。

**验收**
- 顶栏折叠动画期间，`dumpsys gfxinfo` 的掉帧明显减少（真机验证；模拟器可能不收集）。
- 内容不被折叠顶栏永久遮挡。

---

## P1-PERF-3 共享 OkHttpClient

**问题**
- `WebDavClient` 每次构造都 new 一个 `OkHttpClient`；前台每 20 秒同步一次，连接池和线程池无法复用，TLS 握手和线程创建开销大。

**实施**
1. 新建 `sync/HttpClientProvider.kt`：
   ```kotlin
   internal object HttpClientProvider {
       val client: OkHttpClient by lazy {
           OkHttpClient.Builder()
               .connectTimeout(20, TimeUnit.SECONDS)
               .readTimeout(60, TimeUnit.SECONDS)
               .writeTimeout(60, TimeUnit.SECONDS)
               .callTimeout(90, TimeUnit.SECONDS)
               .build()
       }
   }
   ```
2. `WebDavClient` 改用这个单例；认证仍是每请求 `Authorization` header。
3. 增加 `callTimeout`，避免一次挂起的请求把同步 Mutex 占住 3 分钟。

**验收**
- 连续多轮同步，进程里只有一个 OkHttp 连接池/线程池。
- 断网时单轮同步能在 90 秒内失败，不会无限挂起。

---

## P1-PERF-4 缓存 `PhotoStore.dir()`

**问题**
- `PhotoStore.dir()` 每次调用都 `mkdirs()`；`PhotoStore.file()` 在 Composable 里被反复调用，产生大量文件系统调用。

**实施**
- 缓存 application context 对应的目录：
  ```kotlin
  @Volatile private var cachedDir: File? = null
  fun dir(context: Context): File =
      cachedDir ?: synchronized(this) {
          cachedDir ?: File(context.applicationContext.filesDir, DIR_NAME)
              .apply { mkdirs() }
              .also { cachedDir = it }
      }
  ```
- 注意不要缓存 Activity context。

**验收**
- 列表滚动时不再每帧 `mkdirs`；照片仍能正常保存/读取。

---

## P1-PERF-5 SharedPreferences 变更才写

**问题**
- 每轮同步都写 `file_sig`、`file_confirmed`、`last_sync_at`、`last_sync_result`，即使内容没变；前台 20 秒一轮会造成不必要的磁盘写入。

**实施**
1. `SyncStateStore` 增加内存缓存/上次序列化字符串：
   - `saveFileSignatures` 先和上次 JSON 比较，相同就不 `apply()`；
   - `saveConfirmedFiles` 同理。
2. `SettingsStore.saveSyncStatus`：
   - 状态文本没变时，`last_sync_at` 最多每分钟写一次；
   - 失败/成功切换时必须立即写。
3. 不要把 `lastSyncResult` 当成 UI 的唯一数据源；后续 P2-2 会换成 `StateFlow`。

**验收**
- 无变化的同步轮次不再写 prefs（可用 `adb shell dumpsys`/日志确认）。

---

## P1-PERF-6 大图瘦身 / 照片补漏降频

**问题**
- `SyncEngine` 每轮都 `PhotoStore.dir().listFiles()` 扫描所有照片做瘦身；前台 20 秒一轮，几百张照片时 IO 很重。
- 照片补漏每轮也检查所有本地记录的照片。

**实施**
1. 在 `SyncStateStore` 加 `last_maintenance_at`：
   - 瘦身（>1MB 扫描）每 6 小时或首次同步后跑一次；
   - 照片补漏可以保留每轮，但先查「本地记录引用的照片文件缺失」的列表，只对缺失项发请求；不要 `listFiles()`。
2. 首次同步/换端点后强制跑一次维护。
3. 瘦身失败不影响主同步结果。

**验收**
- 正常无变化轮次不再扫描整个照片目录。
- 历史大图仍会在维护轮次被压缩。

---

## P1-PERF-7 合并记录批量事务

**问题**
- `SyncEngine.mergeRemote` 每条远端记录开一次 `db.withTransaction`；记录多时 fsync 次数多。

**实施**
1. 阶段 1 的网络循环只负责「下载 + 解析 + 收集 `SyncRecord`」，不要边下边写库。
2. 下载全部完成后，在一个 `db.withTransaction { ... }` 里逐条合并；`mergeOutfit/mergeMeal/mergeEvent` 改成不自己开事务的内部函数。
3. 注意：事务里不能再做网络请求；所有 `dav.getString/getBytes` 必须在事务外完成。
4. 保持「事务内重新读本地版本」的语义，避免覆盖下载期间刚保存的内容。

**验收**
- 100 条远端记录合并只产生 1 次事务；行为与现有回归一致。

---

## P1-PERF-8 Coil 缓存 key 带上文件修改时间

**问题**
- 瘦身阶段会原地覆盖同名照片；Coil 的 memory/disk cache 可能继续显示旧图。

**实施**
- 所有 `AsyncImage(model = photoFile)` 改成 `ImageRequest`：
  ```kotlin
  ImageRequest.Builder(context)
      .data(photoFile)
      .memoryCacheKey("${photoFile.name}:${photoFile.lastModified()}")
      .diskCacheKey("${photoFile.name}:${photoFile.lastModified()}")
      .build()
  ```
- 至少覆盖列表缩略图和编辑页照片；可以抽一个 `AppAsyncImage` 组件统一处理。

**验收**
- 瘦身后重新进入页面显示压缩后的新图，不是旧缓存。

---

## P1-PERF-9 前台轮询自适应 + 认证失败停止

**问题**
- `SyncForegroundPoller` 固定 20 秒；账号密码错误时会每 20 秒重试，耗电且无意义。

**实施**
1. 正常间隔改 30 秒（或保留 20 秒，但加失败退避）。
2. 连续失败时指数退避：30s → 60s → 120s，上限 5 分钟。
3. 如果 `SyncStatus` 是 `Auth`/`NotFound`（见 P2-3），停止轮询，直到设置页保存新配置后重新启动。
4. 网络恢复后自然继续。

**验收**
- 密码错误时不再每 20 秒打服务器；改对密码后恢复轮询。

---

## P1-PERF-10 Release 构建优化（可选，需用户确认）

**问题**
- `release { isMinifyEnabled = false }`；debug APK 约 19MB。

**实施**
- 如果用户以后要发 release：开 `isMinifyEnabled = true`、`shrinkResources = true`，补 Room/OkHttp/Coil/Haze 的 keep 规则，验证 release 包能正常启动和同步。
- 如果只装 debug，本轮可不做，避免引入混淆问题。

**验收**
- release APK 能安装、启动、同步；体积明显下降。

---

# 第四批：P2 项目接口 / 架构

> 这一批改动大，建议单独一轮、单独分支/提交，先补测试再动。

## P2-1 抽 Repository，统一三个编辑 ViewModel

**问题**
- Outfit/Meal/Event 三个实体共享 12 个字段（note/reply/timestamps/origin/dirty），三个 EditViewModel 的 load/save/delete/权限判断几乎逐行相同；v2.8.x 的评论权限 bug 就是三处各改一遍。

**实施**
1. 新增 `data/RecordCommon.kt`：
   ```kotlin
   data class RecordCommon(
       val note: String = "",
       val noteDevice: String = "",
       val noteName: String = "",
       val reply: String = "",
       val replyName: String = "",
       val noteUpdatedAt: Long = 0L,
       val replyUpdatedAt: Long = 0L,
       val createdAt: Long = 0L,
       val updatedAt: Long = 0L,
       val origin: String = "",
       val originName: String = "",
       val dirty: Boolean = true
   )
   ```
   三个实体用 `@Embedded` 嵌入；列名保持不变，DB 版本可以不升（如果 Room 生成的 schema 完全一致；先跑 `exportSchema` 对比）。
2. 新增 `data/RecordRepository.kt`：
   - `loadOutfit(id)`, `loadMeal(id)`, `loadEvent(id)`
   - `saveNote(...)`, `saveReply(...)`
   - `deleteWithTombstone(...)`
   - `canEditNote(record, myDevice)`, `canDelete(record, myDevice)`
3. 三个 EditViewModel 只保留各自的表单字段（date、mealType、type、title、photoPath），保存/删除走 Repository。
4. 先写测试锁住现有行为，再重构；不要一次性改所有调用点。

**验收**
- 三个模块的备注/评论/删除/权限回归全部通过。
- 新增一种记录类型时不需要再复制一遍 save/delete 权限逻辑。

---

## P2-2 结构化同步状态 `StateFlow<SyncStatus>`

**问题**
- UI 用 `SettingsStore.lastSyncResult(context).startsWith("同步失败")` 解析字符串；后台同步完成后设置页不刷新。

**实施**
1. 新增：
   ```kotlin
   enum class SyncPhase { Idle, Running, Success, Failure }
   data class SyncStatus(
       val phase: SyncPhase,
       val at: Long = 0L,
       val message: String = "",
       val retryable: Boolean = true
   )
   object SyncStatusStore {
       val status: StateFlow<SyncStatus>
       fun update(...)
   }
   ```
2. `SyncRunner.runOnce` / `SyncEngine` 在开始、成功、失败时更新；`SyncWorker` 和前台轮询共用。
3. `SettingsScreen`、`OutfitScreen`、`MealScreen`、`EventScreen` 的 `syncFailed` 改成 `collectAsStateWithLifecycle()`。
4. 保留 `SettingsStore.lastSyncResult` 兼容旧版本，但 UI 不再解析它；下个大版本可删。
5. 状态条支持「刚刚同步 / 3 分钟前」相对时间。

**验收**
- 后台同步完成后，设置页状态条自动刷新，不需要重新进页面。
- 失败原因能显示「账号密码错误 / 网络 / 配额」等可操作信息。

---

## P2-3 WebDAV 错误模型 + 可重试标记

**问题**
- `WebDavClient` 全部返回 `Boolean?` / `null`；401、404、507、超时无法区分；`SyncWorker` 对所有失败都 `Result.retry()`，密码错误会无限重试。

**实施**
1. 新增：
   ```kotlin
   sealed class DavError {
       object Auth : DavError()
       object NotFound : DavError()
       object Quota : DavError()
       data class Network(val message: String) : DavError()
       object Parse : DavError()
   }
   sealed class DavResult<out T> {
       data class Ok<T>(val value: T) : DavResult<T>()
       data class Err(val error: DavError) : DavResult<Nothing>()
   }
   ```
2. `WebDavClient` 的 `listDir/getString/getBytes/putBytes/delete/testConnection` 返回 `DavResult`；HTTP 401/403 → Auth，404 → NotFound，507 → Quota，IO → Network。
3. `SyncEngine.Result` 增加 `retryable: Boolean` / `errorKind: DavError?`。
4. `SyncWorker`：`retryable=true` 才 `Result.retry()`，Auth/NotFound/Quota 返回 `Result.failure()` 或 `success()`（让状态条显示原因）。
5. 设置页根据 `DavError` 显示不同文案。

**验收**
- 密码错误：不再无限重试；设置页提示「账号或应用密码不对」。
- 网络断开：继续退避重试。
- 配额满：提示「云端空间不足」。

---

## P2-4 修复 `runCatching` 吞掉 `CancellationException`

**问题**
- `WebDavClient`、`PhotoStore` 的 `runCatching` 会把协程取消当普通失败，导致已取消的同步继续跑。

**实施**
1. 新增扩展：
   ```kotlin
   internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
       try { Result.success(block()) }
       catch (e: CancellationException) { throw e }
       catch (e: Exception) { Result.failure(e) }
   ```
2. 替换网络/IO 路径里的 `runCatching`；纯同步的、不涉及协程的工具函数可以保留。
3. `PhotoStore.compressInPlace` 不是 suspend，但如果从 suspend 上下文调用，也建议用同一套。

**验收**
- 取消同步（比如退出 App）后，网络请求不再继续发。

---

## P2-5 Room 索引 + schema 导出 + JSON schema 版本

**实施**
1. 三个实体加索引：
   ```kotlin
   @Entity(tableName = "outfits", indices = [Index("date"), Index("dirty")])
   ```
   meals/events 同理。
2. DB 版本 5 → 6；新增 `MIGRATION_5_6`：
   ```sql
   CREATE INDEX IF NOT EXISTS index_outfits_date ON outfits(date);
   CREATE INDEX IF NOT EXISTS index_outfits_dirty ON outfits(dirty);
   ...
   ```
   注意 Room 期望的索引名是 `index_<table>_<column>`；先用 `exportSchema` 生成 schema 对照。
3. 开 `exportSchema = true`，在 `app/build.gradle.kts` 配 `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`；把 `schemas/` 纳入版本管理（如果用户同意 git）。
4. 云端 JSON 增加 `"schema": 1`；`parseSyncRecord` 忽略未知字段，未来不兼容变更时按 schema 分流。
5. `parseSyncRecord` 增加校验：
   - `mealType in 0..2`
   - `eventType in 0..1`
   - `photoPath` 非空才当作有效
   - `id` 非空
   不合格返回 null，同步里 `skipped++`。

**验收**
- 从 v5 数据库升级到 v6 不丢数据；26 项回归通过。
- 坏 JSON 只跳过那一条，不会让整轮失败。

---

## P2-6 清理死参数 / 死代码 / 未使用 import

**实施**
- 删除或改造：
  - `solidCard(elevation=...)`：参数没有实际作用；要么删参数并改 17 个调用点，要么在 KDoc 里明确「保留兼容，不生效」并停止新增调用；
  - `dashedCard(surfaceAlpha=...)` 未使用；
  - `glassPanel(glow=...)` 没有非透明调用；
  - `replyVersionOf` 无引用；
  - `EventViewModel.delete` 无引用（如果 P0-2/P0-3 后仍无引用就删）；
  - `SyncStatusKind.Conflict` 分支：要么实现冲突 UI，要么删掉；
  - 未使用 import（OutfitScreen 的 `BorderStroke/Surface`、MealEditScreen 的 `OutlinedTextField` 等）。
- 用 `./gradlew :app:lintDebug` + IDE 的 Optimize Imports 辅助，但不要整文件重排。

**验收**
- 编译/lint 通过；没有新增 warning；代码里不再出现「看起来能配、实际不生效」的参数。

---

## P2-7 表单状态用 `SavedStateHandle` + 草稿照片

**实施**
1. 三个 EditViewModel 的 `date/type/title/note/reply/photoPath` 改为 `SavedStateHandle` 保存；进程被杀后能恢复。
2. `AddRecordSheet` 的新选照片：先写到 `files/photos/drafts/` 或用 `draft_` 前缀；保存时移动到正式目录，取消时删除。
3. App 启动时清理超过 24 小时的 `draft_*` 文件。
4. 上传页的 `newPhotos` 也走草稿目录，解决「进程被杀暂存照片丢失」。

**验收**
- 在抽屉里选好照片、写一半文字，开发者选项「不保留活动」/杀进程后回来，内容还在（或至少不产生孤儿）。
- 取消编辑后草稿照片被删除。

---

## P2-8 照片垃圾回收（本地 + 云端）

**问题**
- 删除记录只删本地照片；替换照片只删本地旧图；云端 `/photos/` 永远留孤儿，最终吃满坚果云配额。

**实施**
1. 本地 GC：
   - 同步/启动后列出 `files/photos/`；
   - 与三张表所有 `photoPath` 求差集；
   - 只删除「不在引用中且 mtime > 24 小时」的文件（避开草稿/正在编辑的照片）。
2. 云端 GC：
   - 删除记录时，如果本地没有任何记录再引用这个 `photoPath`，在墓碑上传成功后 DELETE 云端照片；
   - 换图时同理，旧图如果在本地/已知远端都没有引用，再删；
   - 保守策略：只删「当前设备确认无引用且记录文件已从云端删除」的照片；不确定就留给下一轮。
3. 可选：低频全量 GC（每天一次）列出云端照片，拉取所有远端记录 JSON 求引用集合，删除超过 7 天的孤儿；这一步开销大，必须限频。
4. 所有删除操作先备份/记录日志，避免误删。

**验收**
- 删除一条带照片的记录后，云端照片最终被清理；对方仍能正常同步其他记录。
- 本地照片目录不会无限增长。

---

## P2-9 密码加密 + 备份规则

**实施**
1. `webdav_pass` 不再明文存 SharedPreferences：
   - 方案 A：引入 `androidx.security:security-crypto` 的 `EncryptedSharedPreferences`，迁移现有 `settings` prefs；
   - 方案 B：用 Android Keystore 生成 AES Key，手动加密 `webdav_pass`，其余 key 仍用普通 prefs。
   - 无论哪种，迁移时必须兼容旧明文值：首次读取时解密失败/读到明文，重新加密写回。
2. `AndroidManifest.xml`：
   - 保持 `allowBackup="true"`（用户需要备份）也可以，但加 `android:dataExtractionRules`（API 31+）和 `android:fullBackupContent`（旧版本），排除 `shared_prefs/settings.xml` 和 `shared_prefs/sync_state.xml`；
   - 或者直接 `allowBackup="false"`，把备份需求交给 P2-10 的导出功能。
3. 不记录密码到日志；崩溃上报里不要带配置。

**验收**
- 重启后同步仍能用；旧配置能平滑迁移。
- 系统备份不包含明文密码。

---

## P2-10 导出 / 导入备份

**实施**
1. 设置页新增「导出备份」：
   - `ActivityResultContracts.CreateDocument("application/zip")`；
   - 打包 `databases/the_cutest_girl.db`（先 `PRAGMA wal_checkpoint(TRUNCATE)`，连同 `-wal`/`-shm` 处理）+ `files/photos/` + 一个 `manifest.json`（版本、时间、记录数）；
   - 不打包 `webdav_pass`。
2. 导入：
   - `OpenDocument` 选择 zip；
   - 校验 manifest；
   - 关闭 Room、备份当前数据、替换数据库和照片、重启进程；
   - 高风险操作，必须有二次确认和「导入前自动备份」。
3. 第一版可以先只做「导出」，导入下一轮再做。

**验收**
- 导出的 zip 能在电脑上解压看到 db 和照片；导入后记录/照片完整。
- 卸载重装后能靠备份恢复。

---

## P2-11 单元测试

**实施**
1. 新增 `app/src/test/java/...`：
   - `parseRecordDate` / `parseSyncRecord` 合法/非法日期、非法 mealType/eventType；
   - `mergeFields` 的备注/评论字段级合并；
   - `signatureOf`；
   - `SettingsStore.davConfigChanged` 的归一化；
   - `nextPhotoList`（P0-1）。
2. 把 `mergeFields` 抽成 internal 纯对象/函数，便于 JVM 测试。
3. 现有 `SyncRegressionRunner` 26 项保留；新增用例覆盖：
   - 对方不能删除上传人记录；
   - 删除事务回滚；
   - 单图替换；
   - `dirsReady` 目录准备失败重试；
   - 相机/相册孤儿清理。
4. 测试命令写进 README/交接文档。

**验收**
- `./gradlew :app:testDebugUnitTest` 通过；26 项设备回归仍通过。

---

## P2-12 构建可移植性

**实施**
- `gradle.properties` 的 `org.gradle.java.home=E:/appdate/jdk-17.0.20.1+1` 是机器绝对路径。不要直接删掉导致用户构建失败；改成：
  - 在 README/交接文档明确「本机专用」；
  - 或提供 `gradle-local.properties` 示例 + `.gitignore`；
  - 或去掉该行，要求使用 `JAVA_HOME`。
- 选一种，并验证用户当前机器仍能构建。

**验收**
- 换一台机器按 README 能构建；用户当前机器也不坏。

---

## P2-13 文案 / 无障碍 / 通知打磨（可选，时间允许再做）

1. 用户可见文案抽 `strings.xml`，至少覆盖通知、设置页、错误提示、删除确认。
2. 自定义控件补语义：
   - `SheetSegmented` 用 `selectable(role = Role.Tab)` / `stateDescription`；
   - 月份格子、日期条、底栏 tab 补 `Role`；
   - 图片补有意义的 `contentDescription`（穿搭/餐食/点滴）。
3. 设置页密码框加「显示/隐藏」切换。
4. 通知：
   - `BigTextStyle` 显示完整摘要；
   - 多条新内容考虑 `InboxStyle` / 按记录分组；
   - 发送通知成功后再写 `notified_ids`（现在先写后发，失败会漏提醒）。
5. 去掉残留的「感叹号」/不一致标点（用户在意文案语气）。

**验收**
- TalkBack 能读出主要控件；通知内容完整；密码可切换可见。

---

# 第五批：P2 工程与交付

## P2-ENG-1 git 基线

- 征得用户同意后：
  ```bash
  git init
  # .gitignore 排除：.gradle/ .kotlin/ app/build/ build/ tmp/ .idea/ local.properties
  # .verification/ 可选：测试证据保留，大截图可忽略
  git add .
  git commit -m "chore: baseline before v4.x quality pass"
  ```
- 之后每批一个 commit，信息写清楚批次和任务 ID。
- 如果用户不同意 git，至少把当前代码复制一份到项目外的备份目录。

## P2-ENG-2 清理项目体积

- `tmp/` 54MB，里面有 vendored Python 包；把 `tmp/imagegen` 移到项目外或加 `.gitignore`。
- 根目录的 `output/`、`deliverables/`、`.workbuddy/`、`.opencode/` 评估是否还需要；至少让它们不参与构建。
- 不要删 `tools/sync_test_server.py`、`.verification` 里的测试证据。

## P2-ENG-3 文档重构

1. 修正 `README.md`：
   - 删除「不需要联网、不需要账号、不要任何权限」；
   - 写清楚：两台手机用坚果云 WebDAV 同步；需要 INTERNET/POST_NOTIFICATIONS/RECEIVE_BOOT_COMPLETED；数据本地 + 云端私有目录；
   - 写清楚构建命令、APK 路径、测试命令。
2. 拆 `AI_HANDOFF.md`：
   - `docs/architecture.md`：数据层、同步协议、业务规则；
   - `docs/design-system.md`：v4 色板、字号、圆角、组件；
   - `docs/changelog.md`：历史版本；
   - `AI_HANDOFF.md` 只保留「当前版本 + 5 分钟上手 + 不可违反的约束」。
3. 更新所有旧组件名：`Accent.kt`/`EmojiTitle`/`GlassCapsuleButton`/`GlassCircleButton`/`glassPhotoFrame`/`moduleGlow`/`GlassTopBar` 已不存在。
4. 在设置页加「关于」：`versionName/versionCode`、同步端点、照片数量/体积。

## P2-ENG-4 版本与打包

- 用户指定版本号后再改 `versionCode/versionName`。
- 正式包：
  ```bash
  ./gradlew.bat :app:assembleDebug --offline
  # 产物：app/build/outputs/apk/debug/the_cutest_girl_v<版本>_debug.apk
  ```
- 提醒用户两台手机覆盖安装（debug 签名一致，数据保留）。
- 如果做 release：单独验证 R8、签名、安装、同步。

---

# 4. 验证矩阵（每批至少跑对应部分）

## 4.1 编译 / 静态检查

```bash
export JAVA_HOME="E:/appdate/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="E:/appdate/gradle-home"
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

要求：
- 0 errors；
- lint 不新增 warning（基线 6 条）；
- 改了 `Records.kt` 后加 `--rerun-tasks` 重跑 androidTest 编译。

## 4.2 同步回归（26 项）

```bash
# 1) 起本地 WebDAV 夹具
(nohup python tools/sync_test_server.py --port 18765 --root .verification/dav > tmp/dav.log 2>&1 &)
curl -X PROPFIND http://127.0.0.1:18765/

# 2) 构建验证变体
./gradlew.bat :app:assembleDebug -PverificationApp --offline -q

# 3) 安装（先装正式测试机/模拟器，不要装到用户日常手机）
adb install -r -t app/build/outputs/apk/debug/*.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 4) 跑自定义 Instrumentation
adb shell am instrument -w -r \
  com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner

# 5) 证据存 .verification/tests-v<版本>.txt
```

要求：
- 现有 26 项全过；新增用例全过。
- 跑完必须用**不带** `-PverificationApp` 的命令重出正式包。

## 4.3 手动 UI 清单（截图存 `.verification/screens/v<版本>-*.png`）

至少覆盖：
1. 深色冷启动第一帧（不是白屏）。
2. 穿搭列表 / 饮食列表 / 点滴列表。
3. 新增记录抽屉：空态、选了 9 张照片、键盘弹出、写了一半下滑关闭的确认。
4. 饮食对方记录 → 编辑页 → 只读备注/照片 + 可写评论。
5. 穿搭编辑页「换一张」前后。
6. 通知点进点滴 → 滚动到对应记录。
7. 设置页：测试连接成功/失败、去电池优化、关于。
8. 深色/浅色系统切换（如果保留了浅色主题）。
9. 编辑页状态栏没有双倍留白。

## 4.4 崩溃 / 数据检查

```bash
adb logcat -d -b crash | grep -i "com.thecutestgirl" || true
adb shell dumpsys package com.thecutestgirl.app | grep versionName
```

要求：
- 本应用 0 条 FATAL。
- 数据库升级后记录数、照片数不变。
- 删除/替换照片后没有孤儿（可写脚本对比 DB 引用和 `files/photos/`）。

## 4.5 性能检查（真机优先）

- 顶栏折叠动画：真机 `dumpsys gfxinfo com.thecutestgirl.app framestats`，看 `FrameCompleted - IntendedVsync > 16.67ms` 比例；模拟器可能不收集。
- 同步轮次：无变化时只有少量 PROPFIND；不再每轮扫描照片目录。
- 内存：大图下载/上传不 OOM。

---

# 5. 汇报格式

每批完成后用中文汇报，包含：

1. **完成的任务 ID** 和一句话说明。
2. **改了哪些文件**（路径 + 关键函数）。
3. **怎么验证的**：命令、测试项数、截图路径、崩溃检查结果。
4. **没做/没验证的**：明确写出来，不要含糊。
5. **风险 / 需要用户决定的事**：版本号、主题方向、是否允许对方删除、是否引入新依赖。
6. **装机说明**：APK 路径、覆盖安装注意事项。

不要只贴代码；用户是小白，要能看懂「这次解决了什么」。

---

# 6. 停止并询问用户的情况

遇到以下情况先停下来问，不要擅自做：

1. 要改主题方向（深色 only vs 自动浅/深）。
2. 要改数据库 schema / 同步协议 / 墓碑规则（除非按 P2-5 的迁移方案）。
3. 要删除云端或本地用户数据（GC/导入除外，需明确确认）。
4. 要新增依赖（security-crypto、splashscreen、paging、zip 库等）。
5. 要改版本号 / 打包正式包。
6. 要重构 Repository / `@Embedded` 这种大范围改动，且可能影响交付时间。
7. 构建环境、模拟器、测试服务器不可用，无法验证。

---

# 7. 推荐执行顺序（按批次交付）

| 批次 | 内容 | 建议 |
|---|---|---|
| Batch 0 | 基线编译 + lint + 26 项回归；询问是否 git init/备份 | 不做功能改动 |
| Batch 1 | P0-1 ~ P0-7 | 必须最先做，单独提交/验证 |
| Batch 2 | P1-UI-1 ~ P1-UI-14 | 先做用户感知强的：饮食评论、通知定位、抽屉、启动闪屏 |
| Batch 3 | P1-PERF-1 ~ P1-PERF-9 | 用真机/模拟器对比前后 |
| Batch 4 | P2-1 ~ P2-6 | 架构/接口，单独一轮；先补测试 |
| Batch 5 | P2-7 ~ P2-13 | 数据安全、备份、测试、文档 |
| Batch 6 | P2-ENG-1 ~ P2-ENG-4 | git、清理、文档重构、打包 |

每批完成后等用户确认，再进入下一批。不要一次性把 P0~P2 全改完再交付——这个项目的数据/同步逻辑经不起大爆炸式改动。

---

# 8. 开始前请先做的事

1. 读一遍当前代码，尤其是：
   - `SyncEngine.kt`、`SyncManager.kt`、`WebDavClient.kt`
   - `PhotoStore.kt`、`Common.kt`（`PhotoNoteCard`）
   - `OutfitEditScreen.kt`、`MealEditScreen.kt`、`EventEditScreen.kt`
   - `AppRoot.kt`、`SettingsScreen.kt`
2. 跑基线编译 + lint + 回归。
3. 告诉用户你准备从 Batch 1 的哪几项开始、预计改哪些文件、有什么风险。
4. 得到用户确认后再动手。
