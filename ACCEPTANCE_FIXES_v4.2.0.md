# v4.2.0 验收未通过项与修复方案

> 验收日期：2026-11-08  
> 验收版本：`4.2.0` / `versionCode 41`  
> 验收提交：`8868ea7`（工作区干净）  
> 验收结论：**有条件通过**。自动化与 29 项设备回归全部通过，但有 2 个必须修的问题、4 个建议修的问题，另有 2 个产品决策需要确认。

---

## 0. 本次验收实际跑过的证据

| 项目 | 结果 |
|---|---|
| `compileDebugKotlin` + `compileDebugAndroidTestKotlin` | ✅ 通过 |
| `lintDebug` | ✅ 0 errors / 6 warnings（基线 6 条） |
| `testDebugUnitTest` | ✅ 7/7 |
| `assembleDebug` + `assembleDebugAndroidTest -PverificationApp --rerun-tasks` | ✅ 通过 |
| 设备回归 `SyncRegressionRunner` | ✅ 29/29，证据：`.verification/tests-acceptance-v4.2.0.txt` |
| 正式 APK | ✅ `the_cutest_girl_v4.2.0_debug.apk`，applicationId 已恢复 `com.thecutestgirl.app` |
| 模拟器冒烟 | ✅ 穿搭 / 饮食 / 点滴 / 设置 / 新增抽屉可打开，`logcat -b crash` 无本应用 FATAL |

编译期还有 2 条 warning（不阻塞，但建议顺手消掉）：
- `app/src/main/java/com/thecutestgirl/app/ui/SavedStateValues.kt:84-86`：`ArrayList<String>` unchecked cast。
- `app/src/main/java/com/thecutestgirl/app/ui/settings/SettingsScreen.kt:67,176`：`androidx.compose.ui.platform.LocalLifecycleOwner` 已废弃。

---

## 1. 未通过 / 部分通过清单

| 编号 | 项目 | 状态 | 优先级 | 主要位置 |
|---|---|---|---|---|
| FIX-01 | 新增记录抽屉半透明，底层内容穿透，文字可读性差 | ❌ 未通过 | **P0** | `AddRecordSheet.kt:100,144`、`Glass.kt:121` |
| FIX-02 | 备份/恢复后 `device_id` 丢失，用户变成「对方」，自己的记录只读 | ❌ 未通过 | **P0** | `SettingsStore.kt:20,121,128`、`backup_rules.xml:4-5`、`data_extraction_rules.xml:5-6,9-10`、`BackupExporter.kt:21` |
| FIX-03 | 同端点下云端目录被删，`dirsReady` 不重置，同步永久失败 | ⚠️ 部分通过 | P1 | `SyncEngine.kt:244,585,597` |
| FIX-04 | 对方编辑页标题 / 日期仍可点、可改，但保存时被静默丢弃 | ⚠️ 部分通过 | P1 | `EventEditScreen.kt:132,188`、`OutfitEditScreen.kt:126` |
| FIX-05 | 本地照片孤儿 GC 只在同步开启时运行，未同步用户会积累 `raw_/cam_/img_` | ⚠️ 部分通过 | P1 | `SyncEngine.kt:483-518`、`MainActivity.kt:44-47` |
| FIX-06 | 浅色卡片与页面底对比度偏低（约 1.03:1），卡片主要靠阴影区分 | ⚠️ 设计待打磨 | P2 | `Color.kt:9`、`Glass.kt:121` |
| FIX-07 | 一些小问题：食模块左侧强调条颜色、合并记录 N+1 查询、截图证据串页、`enqueueNow` 非唯一任务 | ⚠️ 小问题 | P2 | 见 §2.7 |

> 说明：P0-7「同步任务去重」没有按原方案使用 `enqueueUniqueWork`，但改为「3 秒节流 + 进程级 Mutex + 只在首次 onCreate 触发」，实际场景已覆盖，**按可接受取舍处理**，不列入阻塞项。

---

## 2. 逐项修复方案

### FIX-01 新增记录抽屉半透明、内容穿透（P0，必须修）

**现象**

当前 v4.2.0 实拍 `tmp/acc-sheet.png` 与 `.verification/screens/v410/b2-sheet.png` 都能看到：
- 抽屉后面的「他」「照片还在路上…」、底部导航图标直接透到抽屉上；
- 抽屉整体发灰，标题、表单文字对比度下降；
- 抽屉和背景没有明确边界，看起来像一层没加载完的蒙层。

**根因**

- `AddRecordSheet.kt:100`：`ModalBottomSheet(containerColor = Color.Transparent)`。
- `AddRecordSheet.kt:144`：内容固定 `fillMaxHeight(0.62f)`。
- `Glass.kt:121`：`glassPanel` 统一用 `background(Color.White.copy(alpha = 0.78f))`，22% 的底层内容会透出来。

**修改方案**

1. 给 `glassPanel` 增加一个表面不透明度参数，默认保持 0.78，不影响列表卡片：
   ```kotlin
   @Composable
   fun Modifier.glassPanel(
       cornerRadius: Dp = 16.dp,
       drawHairline: Boolean = true,
       elevation: Dp = 8.dp,
       surfaceAlpha: Float = 0.78f
   ): Modifier = glassPanel(
       shape = RoundedCornerShape(cornerRadius),
       drawHairline = drawHairline,
       elevation = elevation,
       surfaceAlpha = surfaceAlpha
   )
   ```
   内部把 `Color.White.copy(alpha = 0.78f)` 改成：
   ```kotlin
   .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
   ```
2. `AddRecordSheetContent` 改用不透明表面：
   ```kotlin
   .glassPanel(shape = topOnly, surfaceAlpha = 0.98f)
   ```
   或者直接 `.clip(topOnly).background(MaterialTheme.colorScheme.surface)`，再保留顶部内发光和边框。
3. `FloatingTopBar` 同样建议用 `surfaceAlpha = 0.94f~0.96f`，避免列表内容从顶栏下面滚过时“透字”。
4. 抽屉高度不要硬编码 `0.62f`：
   ```kotlin
   BoxWithConstraints {
       val maxSheetHeight = maxHeight * 0.85f
       Column(
           modifier = Modifier
               .fillMaxWidth()
               .heightIn(min = 360.dp, max = maxSheetHeight)
               ...
       )
   }
   ```
   内容区继续用 `verticalScroll`，并实测键盘弹出时输入框和保存按钮仍可见；注意不要同时叠加 `imePadding()` 和 `navigationBarsPadding()` 造成双倍留白。

**验收标准**

- 抽屉下方有内容时，截图里看不到底层文字/图标透过抽屉。
- 抽屉标题、正文与卡片背景对比清晰。
- 键盘弹出时能滚动到「保存」按钮；小屏（约 640dp 高）不裁切。
- 列表页卡片、顶栏观感不因 `surfaceAlpha` 默认值改变而明显变化。

**回归**

- 截图：穿搭/饮食/点滴三个抽屉各一张，含「空态 + 有内容 + 键盘弹出」。
- `compileDebugKotlin` + `lintDebug`。
- 不需要设备同步回归；但需 `logcat -b crash` 无 FATAL。

---

### FIX-02 备份/恢复后设备身份丢失，用户变成「对方」（P0，必须修）

**现象**

- 用户换机/系统备份恢复/未来导入备份后，历史记录还在，但用户对**自己的全部记录**都变成只读：
  - 备注不能编辑；
  - 照片不能更换；
  - 删除按钮消失；
  - 只能写评论。
- 根因不是数据损坏，而是「设备身份」没有被备份。

**根因**

- `SettingsStore.kt:20`：`FILE = "settings"`。
- `SettingsStore.kt:121`：`deviceId` 存在 `settings.xml`。
- `SettingsStore.kt:128`：`myName` 也存在 `settings.xml`。
- `backup_rules.xml:4-5` 与 `data_extraction_rules.xml:5-6,9-10` 把整个 `settings.xml` 排除。
- `BackupExporter.kt` 只导出 DB + 照片，不导出 `device_id` / `my_name`。
- 三个 ViewModel 的 `amUploader`（`OutfitViewModel.kt:148`、`MealViewModel.kt:81`、`EventViewModel.kt:66`）依赖 `editingOrigin == myDeviceId`；恢复后 `myDeviceId` 重新生成，所有记录都变成「对方」。

**修改方案**

1. 把「身份」和「密码」拆成两个 `SharedPreferences` 文件：
   - `identity.xml`：`device_id`、`my_name`、`propagated_name`（可备份）；
   - `dav_secret.xml`：`webdav_pass`（Keystore 密文，不备份）；
   - `settings.xml`：`webdav_url`、`webdav_user`、同步时间等（可备份）；
   - `sync_state.xml`：同步账本（不备份，继续排除）。
2. `SettingsStore` 增加兼容迁移：
   - 读取 `deviceId/myName` 时先读 `identity.xml`，没有则回退读旧 `settings.xml`，并写回 `identity.xml`；
   - 读取 `webdavPass` 时先读 `dav_secret.xml`，没有则回退旧 `settings.xml`，并写回 `dav_secret.xml`；
   - 保存时全部写新文件；旧 key 可保留一段时间，确认无回滚需求后再删。
3. 备份规则改成只排除密码与账本：
   ```xml
   <!-- backup_rules.xml / data_extraction_rules.xml -->
   <exclude domain="sharedpref" path="dav_secret.xml" />
   <exclude domain="sharedpref" path="sync_state.xml" />
   ```
   不要再排除 `settings.xml` 和 `identity.xml`。
4. `BackupExporter`：
   - zip 里增加 `identity.json`：
     ```json
     { "deviceId": "...", "myName": "..." }
     ```
   - 仍不导出 `webdav_pass`；
   - 导出时把 `webdav_url/webdav_user` 也写进 `manifest.json`，方便以后导入时提示用户重新填密码；
   - 如果导入功能下一轮才做，至少先把 `identity.json` 的格式定下来。
5. 未来做导入时：
   - 先校验 manifest；
   - 恢复 DB、照片、`identity.json`；
   - 让用户重新输入应用密码（Keystore key 不跨设备）；
   - 重启进程。

**验收标准**

- 模拟恢复流程：备份 → 清空 App 数据（模拟器）→ 恢复 DB + 照片 + identity → 启动。
- 打开自己的历史记录：备注可编辑、照片可换、删除按钮可见。
- 对方的记录仍只读，权限不被放大。
- 系统备份规则不再包含 `dav_secret.xml`；`dav_secret.xml` 里没有明文密码。

**回归**

- 新增设备回归：`restoredIdentityKeepsUploader`（手动设置 `device_id` 后加载记录，断言 `amUploader == true` / `canDelete == true`）。
- 现有 29 项必须全过。
- 真实设备/模拟器截图：恢复前后设置页、自己记录编辑页。

---

### FIX-03 同端点下云端目录被删，`dirsReady` 不重置（P1，建议修）

**现象**

- 用户手动删除云端 `thecutestgirl/` 目录，或换了一个会重置目录的服务商；
- `dirsReady` 仍然是 true，下一轮同步跳过 MKCOL；
- `listDir("thecutestgirl/records")` 返回 404，同步一直失败；
- 只有换端点或清 App 数据才能恢复。

**根因**

- `SyncEngine.kt:244` 读取 records 目录；404 会抛 `DavException(NotFound)`。
- `SyncEngine.kt:585` 的 `catch (e: DavException)` 只上报错误，不重置 `dirsReady`。
- 只有 `SyncEngine.kt:597` 的 `DirectorySetupException` 会 `setDirsReady(false)`。

**修改方案**

1. 新增一个更精确的异常，或复用 `DirectorySetupException`：
   ```kotlin
   private class CloudDirectoryMissingException(message: String) : IOException(message)
   ```
2. `listDir("thecutestgirl/records")` / `listDir("thecutestgirl/photos")` 返回 `DavResult.Err(DavError.NotFound)` 时抛 `CloudDirectoryMissingException`。
3. `catch (CloudDirectoryMissingException)` 里：
   ```kotlin
   SyncStateStore.setDirsReady(context, false)
   ```
   返回 `ok = false, retryable = true`，下一轮重新 MKCOL。
4. 注意不要把所有 `NotFound` 都当目录问题：
   - 单条记录 GET 404 只代表这条记录被删了，不应该重置 `dirsReady`；
   - 只对 `listDir(records/photos)` 的 404 使用新异常。
5. `testConnection(prepareDirectories = false)` 可以选择在每次测试时也 `listDir(records)` 一次，提前发现目录缺失；但这会增加一次请求，按需启用。

**验收标准**

- 设 `dirsReady = true`，手动删除测试服务器上的 `thecutestgirl/records`；
- 下一轮同步失败，但 `dirsReady` 变为 false；
- 再下一轮自动 MKCOL + listDir，恢复成功。
- 单条记录 404 不会误触发 MKCOL。

**回归**

- 新增设备回归：`missingCloudFolderIsReprepared`。
- 现有 29 项全过。

---

### FIX-04 对方编辑页标题 / 日期仍可点、可改（P1，建议修）

**现象**

- 对方打开一条记录写评论时：
  - `EventEditScreen` 的标题 `PlainField` 仍可编辑；
  - `EventEditScreen` / `OutfitEditScreen` 的日期行仍显示「修改」并可打开日期选择器；
- 保存时非上传人只走 `setReply`，这些修改被静默丢弃，用户会以为保存了。

**根因**

- `EventEditScreen.kt:132` 标题不区分 `amUploader`；
- `EventEditScreen.kt:188` 日期行始终 `clickable`；
- `OutfitEditScreen.kt:126` 日期行始终 `clickable`；
- 三个 ViewModel 的非上传人保存分支只写 `reply`。

**修改方案**

1. `EventEditScreen`：
   - 标题区改成：
     ```kotlin
     if (vm.amUploader || id == null) {
         PlainField(...) // 可编辑
     } else {
         ReadOnlyNote(label = "标题", value = vm.title)
     }
     ```
   - 日期行：
     ```kotlin
     .clickable(enabled = vm.amUploader) { if (vm.amUploader) showDatePicker = true }
     ```
     非上传人隐藏右侧「修改」，只显示日期。
   - 非上传人的类型选择本来已隐藏，保持。
2. `OutfitEditScreen`：
   - 日期行同样 `clickable(enabled = vm.amUploader)`，非上传人隐藏「修改」。
3. `MealEditScreen` 没有日期行；确认备注 / 照片已经是只读即可。
4. 非上传人的保存按钮仍只要求 `reply != originalReply`；不要因为只读字段不可编辑而改变 `canSave`。

**验收标准**

- 对方打开穿搭/点滴记录：标题、日期只读，没有「修改」；评论可写；保存后只更新 `reply`。
- 自己打开记录：标题、日期、照片、备注都可编辑，行为不变。
- 删除按钮仍只对上传人显示。

**回归**

- 现有 `partner cannot delete`、`partner cannot edit the uploader's note` 继续通过。
- 新增 UI 截图：对方记录编辑页 / 自己记录编辑页。

---

### FIX-05 本地照片孤儿 GC 只在同步开启时运行（P1，建议修）

**现象**

- 没开同步的用户：
  - 取消编辑、进程被杀、相机取消等场景可能留下 `raw_*.jpg`、`cam_*.jpg`、`img_*.jpg`；
  - `PhotoStore.cleanupOldDrafts` 只清理 `draft_*`；
  - 本地 GC 在 `SyncEngine.kt:483-518` 的 `maintenanceDue` 里，而 `SyncEngine` 只有配置同步后才会运行；
  - 结果：未同步用户的照片目录可能长期堆积孤儿文件。

**修改方案**

1. 新增 `PhotoGc`（或 `PhotoStore.cleanupOrphans`）：
   ```kotlin
   suspend fun cleanupOrphans(context: Context) = withContext(Dispatchers.IO) {
       val referenced = AppDatabase.getInstance(context)
           .let { db ->
               db.outfitDao().getAllPhotoPaths() +
               db.mealDao().getAllPhotoPaths() +
               db.eventDao().getAllPhotoPaths()
           }.toHashSet()
       val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
       PhotoStore.dir(context).listFiles { f ->
           f.isFile &&
               !f.name.startsWith("draft_") &&
               f.name !in referenced &&
               f.lastModified() < cutoff
       }?.forEach { it.delete() }
   }
   ```
2. `MainActivity.onCreate` 在 IO 线程调用一次（与 `cleanupOldDrafts` 并列），不依赖同步是否开启。
3. `SyncEngine` 的维护轮继续保留，作为同步用户的兜底；两边共用同一个 `PhotoGc`。
4. 注意：
   - 不要删除 `draft_`（由 `cleanupOldDrafts` 按 24h 处理）；
   - 不要删除正在被数据库引用的照片；
   - 如果担心刚复制但还没写库的照片，可把 cutoff 设为 24h 或 48h。

**验收标准**

- 在 `files/photos/` 放一个 25 小时前的 `raw_x.jpg` 孤儿，启动 App 后被删除。
- 数据库引用的照片、`draft_` 草稿不被删除。
- 未配置同步的模拟器也能触发清理。

---

### FIX-06 浅色卡片与页面底对比度偏低（P2，设计打磨）

**现象**

- 页面底 `#FAF7F8`，卡片 78% 白；有效卡片色接近 `#FDFCFC`；
- 卡片与背景的亮度差只有约 1.03:1，主要靠 8dp 阴影和 1dp 边框区分；
- 截图里头部卡、列表卡、设置卡都偏“平”，在低亮度屏幕上可能更明显。

**修改方案**

- 如果确定走浅色品牌，二选一：
  1. 把页面底压深：`CanvasLight` 从 `#FAF7F8` 调到 `#F1EBE5` 或 `#F2ECEF`，卡片保持白/78% 白；
  2. 把卡片填充提高到 0.92~0.96，背景保持 `#FAF7F8`。
- 目标：页面底与卡片有效颜色对比 ≥ 1.18:1（这是 v2.5.2 交接文档里验证过的经验值）。
- 改完同步更新 `docs/design-system.md` 的色板与对比度说明。

**验收标准**

- 在浅色系统、深色系统（App 仍浅色）下各截一张列表页；
- 卡片边界不需要刻意找也能看出来；
- 抽屉/顶栏不透明修复后，与卡片层次一致。

---

### FIX-07 小问题（P2，可与其他修复一起做）

1. **食模块左侧强调条颜色与注释不一致**
   - `MealScreen.kt:442`：`val accent = MaterialTheme.colorScheme.primary`；
   - 上一行注释写「钴蓝」，浅色主题下 `secondary` 是 MutedRose；
   - 二选一：改用 `secondary`，或把注释改成「主色/粉色」。不要留下“注释说一套、代码做一套”。

2. **批量合并里的 N+1 查询**
   - `SyncEngine.kt:629` 每条远端记录都 `tombstoneDao.getAll()`；
   - 建议在事务外查一次墓碑 id 集合，传进 `mergeRemoteLocked(record, tombstoneIds)`；
   - 100 条记录从 100 次查询降到 1 次。

3. **编译 warning**
   - `SavedStateValues.kt:84-86`：`ArrayList<String>` 的 unchecked cast；
     ```kotlin
     @Suppress("UNCHECKED_CAST")
     private fun <T> SavedStateHandle.getList(key: String): List<T> =
         (get<Any?>(key) as? ArrayList<T>) ?: emptyList()
     ```
     或保留现有逻辑但加 `@Suppress("UNCHECKED_CAST")`。
   - `SettingsScreen.kt:67,176`：改用 `androidx.lifecycle.compose.LocalLifecycleOwner`。

4. **截图证据串页**
   - `.verification/screens/v410/b3-meal.png` 的内容看起来是穿搭页（标题「穿搭」、底部选中穿搭），不能作为饮食页证据；
   - 我补拍了 `tmp/acc-meal4.png`，饮食页本身正常；
   - 修复轮请补：饮食页、编辑页、设置页、对方记录编辑页、通知跳转、抽屉键盘态、大图缩放态。

5. **`enqueueNow` 仍是普通 `enqueue`**
   - `SyncManager.kt:75-90` 用 3 秒节流 + 进程级 Mutex，未使用 `enqueueUniqueWork`；
   - 实际场景已覆盖，属于可接受取舍；
   - 如果后续出现“保存很频繁导致任务排队”，可改成 `enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)` 或加更长的 debounce；
   - 本轮不阻塞。

6. **项目体积**
   - `tmp/` 仍有约 55MB（已 gitignore）；
   - 如果不再需要 `tmp/imagegen`，移到项目外或删除；至少确认它不参与构建。

---

## 3. 需要产品/用户确认的事项

### 3.1 主题方向：现在是浅色，不是上一轮的深色

- `Theme.kt:12,42,47` 固定 `LightColors`，不再跟随系统深色；
- `values/themes.xml`、`values-v31/themes.xml`、`docs/design-system.md` 都已改成「简洁白 + 粉红」；
- `.verification/screens/v410/08-system-dark-app-light.png` 也验证了「系统深色时 App 仍浅色」。

如果这是用户本轮明确要求的，**通过**；如果不是，需要回滚到 v4.0 深色方案，改动范围包括：
- `ui/theme/Theme.kt`、`Color.kt`；
- `values/themes.xml`、`values-night/themes.xml`、`values-v31/themes.xml`、`values-night-v31/themes.xml`；
- `docs/design-system.md`、`AI_HANDOFF.md`、README；
- 所有浅色截图。

### 3.2 版本号

- 当前 `app/build.gradle.kts:16-17` 是 `versionCode 41 / 4.2.0`；
- 如果这不是用户指定的版本号，打包前改；否则 `AI_HANDOFF.md`、README、docs/changelog 一起同步。

---

## 4. 已通过项（不需要再改，供对照）

| 类别 | 已通过 |
|---|---|
| P0 | 单图换图/孤儿清理、删除权限、删除+墓碑事务、换云盘先测后存、`dirsReady` 主路径、照片原子写/流式传输 |
| P1-UI | 饮食对方记录可编辑评论、通知带 recordId、`pendingTarget` 按 route 分发、抽屉 LazyRow/放弃确认、禁用态、统一顶栏、编辑图 3:2、三页齿轮、设置页去嵌套、跨午夜刷新、48dp 热区、大图缩放/左右滑 |
| P1-PERF | 删除 `pageHaze`、顶栏折叠稳定、共享 OkHttp、`PhotoStore` 目录缓存、prefs 写去重、维护限频、批量合并、Coil 缓存 key、轮询退避 |
| P2 | `SyncStatus` Flow、WebDAV 错误模型/可重试、取消异常、Room v6 索引/schema、JSON 校验、死参数清理、SavedState/草稿、密码加密、备份导出、29 项设备回归、文档拆分、git 基线 |

未完成但已在文档中明确标注、不阻塞本轮验收：
- `RecordRepository` 重构（P2-1）；
- 导入备份（P2-10 只做导出）；
- 文案抽 `strings.xml`（P2-13 部分）；
- release/R8（P1-PERF-10 可选）。

---

## 5. 修复顺序与回归清单

### 建议顺序

1. **FIX-01 抽屉不透明 + 高度/键盘**（用户感知最强，改动小）。
2. **FIX-02 备份身份拆分**（数据安全，影响恢复场景）。
3. **FIX-03 `dirsReady` NotFound**（同步可靠性）。
4. **FIX-04 对方编辑页只读**（避免用户误以为保存成功）。
5. **FIX-05 本地 GC**、**FIX-06 对比度**、**FIX-07 小问题**。
6. 重新截图、跑 29 项回归、重出正式 APK。

### 每轮必须跑的命令

```bash
export JAVA_HOME="E:/appdate/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="E:/appdate/gradle-home"

./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
./gradlew.bat :app:testDebugUnitTest --offline
```

设备回归：

```bash
# 1) 起本地 WebDAV 夹具（如果没起）
python tools/sync_test_server.py --port 18765 --root .verification/dav

# 2) 构建验证变体（从干净状态重编，避免 androidTest 用旧包）
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline --rerun-tasks

# 3) 安装 + 跑
adb install -r -t app/build/outputs/apk/debug/the_cutest_girl_v4.2.0_debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner

# 4) 跑完必须重出正式包（不带 -PverificationApp）
./gradlew.bat :app:assembleDebug --offline
# 检查 output-metadata.json 的 applicationId == com.thecutestgirl.app
```

### 修复轮 Definition of Done

- [ ] `compileDebugKotlin` / `compileDebugAndroidTestKotlin` 0 errors。
- [ ] `lintDebug` 不超过 6 条 warning，且没有新增类型。
- [ ] JVM 单测 7/7 通过；建议新增 FIX-03/FIX-02 的纯逻辑/设备测试。
- [ ] 设备回归 **29/29** 通过（新增用例后数量增加）。
- [ ] `logcat -d -b crash | grep com.thecutestgirl` 为空。
- [ ] 正式 APK `applicationId = com.thecutestgirl.app`、`versionCode/Name` 正确。
- [ ] 抽屉、编辑页、设置页、通知跳转、大图缩放、键盘态截图齐全。
- [ ] `AI_HANDOFF.md` / `docs/changelog.md` / README 记录本轮修复。
- [ ] 如果主题方向有变，所有文档与截图同步更新。

---

## 6. 一句话总结

**核心功能和同步回归都过了；现在不能直接装机的原因只有两个——抽屉半透明影响可读性、备份恢复会丢失设备身份导致自己的记录变只读。修完这两项，再补 `dirsReady` 和对方编辑页只读，就可以打 4.2.1 交付。**
