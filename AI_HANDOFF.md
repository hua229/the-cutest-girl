# the cutest girl —— 交接速查

> 当前版本 **4.2.1**（versionCode 42）｜最后更新 2026-11-08  
> 给接手的 AI / 工程师：先读本页，再按需读 `docs/`。用户是代码小白，中文沟通，所有用户文案中文。

## 5 分钟上手

- 项目：`E:\project\AI_seession\zcode\TheCutestGirl`（Windows）
- 构建：JDK 17（`gradle.properties` 的 `org.gradle.java.home` 是本机专用路径）
- 编译 + lint：

```bash
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

- 单测：`./gradlew.bat :app:testDebugUnitTest --offline`（7 项）
- 设备回归：32 项，命令见 `docs/architecture.md` §6；跑完必须重出正式包（不要带 `-PverificationApp`）
- 正式 APK：`app/build/outputs/apk/debug/the_cutest_girl_v<版本>_debug.apk`
- 关键入口：`MainActivity.kt`、`ui/AppRoot.kt`、`sync/SyncEngine.kt`、`ui/components/Glass.kt`

## 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/architecture.md` | 数据层、数据库 v6、同步协议/流程、业务规则、错误模型、验证命令 |
| `docs/design-system.md` | v4.1/4.2 浅色品牌 tokens（含对比度修订）、字体、形状、组件、动效规范 |
| `docs/changelog.md` | v1 → v4.2.1 版本摘要 |
| `README.md` | 功能、权限、构建、测试、项目结构 |

## 不可违反的约束

1. **只改 UI 时不要动同步/数据语义**；反之改同步必须跑 32 项设备回归。
2. `note` 归上传人、`reply` 归对方；删除必须「本地 + 墓碑」同事务；对方不能删除上传人的记录。
3. 云端目录固定：`thecutestgirl/records/<id>.json`、`records/T_<id>.json`、`photos/<name>.jpg`。
4. `origin == "local"` 只用于认领旧迁移记录，不能抢占对方 origin。
5. 修改 `WebDavClient` 等签名后，**必须重新 `assembleDebugAndroidTest`** 再跑设备回归（只编译 class 会装到旧测试包）。
6. 批量清理 import 不能按「符号出现次数」判断：`getValue/setValue` 是 `by` 委托隐式使用的，误删会导致全项目编译失败。
7. 主题固定浅色（简洁白 + 粉红）；不要恢复深色/奶油玫瑰；真实 Haze 只允许底栏。
8. 不引入 Hilt/Koin、Retrofit、新 UI 库；新增依赖（包括测试依赖）先征得用户同意。
9. 版本号 / 打包由用户拍板；不要擅自改 `versionCode/versionName`。
10. 照片写入必须原子化；新选照片先落 `draft_` 草稿，保存时 `promoteDraft`。
11. 所有用户可见文案中文、无 emoji 当功能图标；粉色只用于操作与焦点。

## 最近一轮（2026-11-08）：v4.2.1 验收修复

按 `ACCEPTANCE_FIXES_v4.2.0.md` 完成 FIX-01 ~ FIX-07：

- **FIX-01** 抽屉：约 82% 屏高（最小 360dp）、表面完全不透明（保留顶部高光/边框）；顶栏 0.96；内容区可滚动并随键盘避让。
- **FIX-02** 身份/密码拆分：`identity.xml`（device_id/my_name/propagated_name，可备份）、`dav_secret.xml`（密码，排除备份）、`settings.xml`（url/user/同步状态）；旧键读取时迁移并从 settings 删除；备份 zip 增加 `identity.json`（camelCase）与 manifest 的 `davUrl/davUser`。
- **FIX-03** 云端目录 404：`CloudDirectoryMissingException` 只用于 `listDir(records/photos)`，清 `dirsReady` 后下一轮自动重建；单条记录 GET 404 只跳过该条。
- **FIX-04** 对方编辑页：点滴标题改 `ReadOnlyNote`；穿搭/点滴日期行 `clickable(enabled = amUploader)` 并隐藏「修改」。
- **FIX-05** `PhotoStore.cleanupOrphans()`：启动即清理「数据库不引用 + 非 draft_ + 超过 24h」，同步维护轮共用。
- **FIX-06** 对比度：`CanvasLight #EFE8EB` + `glassPanel` 默认 `surfaceAlpha=0.92`（目标 ≥1.18:1）。
- **FIX-07** 饮食左缘注释与主色统一；墓碑 id 在批量合并前查一次；`SavedStateValues` 加 `@Suppress`；设置页改用 `androidx.lifecycle.compose.LocalLifecycleOwner`。

验证：lint 0 错误 / 6 警告；JVM 7/7；设备回归 **32/32**（`.verification/tests-v4.2.1-fixes.txt`）。

## 上一轮（2026-11-08）

按 `OPTIMIZATION_PROMPT.md` 完成 P0 7 项、P1-UI 14 项、P1-PERF 9 项、P2（除导入备份/文案抽 strings/Repository 重构外的全部）：
换图与孤儿清理、删除权限与事务、同步先测后存、目录/照片原子写、任务去重、批量合并、状态/错误模型、索引 v6、草稿与照片 GC、密码加密/备份导出。
未完成：导入备份、文案抽 `strings.xml`、`RecordRepository` 重构（P2-1）、release/R8。

## 验证证据

- `.verification/tests-v4.2.1-final.txt`（32/32）
- `.verification/screens/v421/`（抽屉空态/有内容/键盘/小屏、列表浅色与系统深色、对方/自己编辑页、通知落点、大图缩放）
- `.verification/screens/v410/`（浅色品牌截图）
- `app/schemas/com.thecutestgirl.app.data.AppDatabase/6.json`