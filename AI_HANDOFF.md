# the cutest girl —— 交接速查

> 当前版本 **4.3.0**（versionCode 44）｜最后更新 2026-11-17
> 给接手的 AI / 工程师：先读本页，再按需读 `docs/`。用户是代码小白，中文沟通，所有用户文案中文。

## 5 分钟上手

- 项目：`E:\project\AI_seession\zcode\TheCutestGirl`（Windows）
- 构建：JDK 17（`gradle.properties` 的 `org.gradle.java.home` 是本机专用路径）
- 编译 + lint：

```bash
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

- 单测：`./gradlew.bat :app:testDebugUnitTest --offline`（12 项）
- 设备回归：32 项，命令见 `docs/architecture.md` §6；跑完必须重出正式包（不要带 `-PverificationApp`）
- 正式 APK：`app/build/outputs/apk/debug/the_cutest_girl_v<版本>_debug.apk`
- 关键入口：`MainActivity.kt`、`ui/AppRoot.kt`、`sync/SyncEngine.kt`、`ui/components/Glass.kt`

## 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/architecture.md` | 数据层、数据库 v6、同步协议/流程、业务规则、错误模型、验证命令 |
| `docs/design-system.md` | 奶油纸感双主题 tokens、纸纹材质、字阶、形状、六态组件与降级策略 |
| `docs/changelog.md` | v1 → v4.3.0 版本摘要 |
| `README.md` | 功能、权限、构建、测试、项目结构 |

## 不可违反的约束

1. **只改 UI 时不要动同步/数据语义**；反之改同步必须跑 32 项设备回归。
2. `note` 归上传人、`reply` 归对方；删除必须「本地 + 墓碑」同事务；对方不能删除上传人的记录。
3. 云端目录固定：`thecutestgirl/records/<id>.json`、`records/T_<id>.json`、`photos/<name>.jpg`。
4. `origin == "local"` 只用于认领旧迁移记录，不能抢占对方 origin。
5. 修改 `WebDavClient` 等签名后，**必须重新 `assembleDebugAndroidTest`** 再跑设备回归（只编译 class 会装到旧测试包）。
6. 批量清理 import 不能按「符号出现次数」判断：`getValue/setValue` 是 `by` 委托隐式使用的，误删会导致全项目编译失败。
7. 主题双档并存（奶油纸感）：浅色页面底 `#F3EDE2` / 卡面 `#FCF8F1`；深色页面底 `#171412` / 卡面 `#221E1A`；品牌粉保持 `#C92661` / `#E8699C`。材质值一律走 `LocalAppPalette`，**不许**在组件里写 `if (isDark)`，也不许塞写死的 `Color.White/Black`（压在照片上的两处浮层除外，要带注释）。**底部导航由用户于 2026-11-17 明确要求保持原版悬浮玻璃胶囊及滑动动画**，仅此处保留 Haze 1.1.1 与根采样源；其他页面继续纸卡，不能把底栏再改成纸质横条。
8. 不引入 Hilt/Koin、Retrofit、新 UI 库；新增依赖（包括测试依赖）先征得用户同意。
9. 版本号 / 打包由用户拍板；不要擅自改 `versionCode/versionName`。
10. 照片写入必须原子化；新选照片先落 `draft_` 草稿，保存时 `promoteDraft`。
11. 所有用户可见文案中文、无 emoji 当功能图标；粉色仅出现在 6 个允许位（主按钮、选中态、FAB、纪念日数字、左缘光、≤20dp 标签底），全屏面积 ≤8%、同屏 ≤3 位。

## 未发布 — 全局文案精简与原版底栏保留（2026-11-17）

- 三首页删除装饰性副标题，空态删除操作教学，新增抽屉删除重复日期 / 类型说明；日期字段、输入标签、作者、真实内容、数量限制、错误与删除确认保留。
- 菜单删除分组小标题及每项功能说明，保留模块名与当前值；昵称 / 外观页去掉说明段落，云同步帮助改为按需弹窗，状态和时间合并为一行。
- 用户指出底栏变化后，核对发现工作区此前的纸带改版被一并打包；已恢复仓库原有 `GlassBottomBar` 的悬浮胶囊、图标、滑块弹簧和长按拖动实现，仅材质颜色沿用当前主题 token。不要再次跟随页面文案调整底栏。
- 验证：常规包名 APK 构建成功，Kotlin / AndroidTest 编译通过，JVM **12/12**，lint **0 错误 / 6 条既有警告**。模拟器检查三首页 / 新增表单、五个设置页、同步帮助开合、主题切换、320dp / 200% 字体，以及原版底栏点按和长按拖动；`logcat -b crash` 无崩溃。证据在 `.verification/screens/minimal-copy/`。

## 未发布 — 穿搭顶部栏合并（2026-11-17）

- 菜单入口移入 `PageHeader(onOpenMenu=...)`，与标题、月份、日期条共用 `FloatingTopBar` 外框；月份与日期条使用完整卡面宽度。
- 保留 300ms 宽度收缩、副标题与日期区淡出 / 高度过渡；收起态菜单独立打开设置、标题点击回顶部。`collapsedWidth` 可配置，穿搭页随字体缩放并限制在可用宽度内。
- 验证：编译与 AndroidTest 编译通过，JVM **12/12**，lint **0 错误 / 6 条既有警告**；独立 `.topbarqa` 包实测菜单、回顶部、月份弹窗、前后月、日期选择、深色及 320dp / 200% 字体。截图在 `.verification/screens/merged-topbar/`。

## v4.3.0（2026-11-09）：深色模式 + 外观设置页

用户明确要求：加深色模式，默认跟随系统，设置里给独立开关。设计在动手前已逐条确认（入口形态 = 独立「外观」页三选一；配色深度 = 定制品牌深色板；版本号一并批准）。

- **新增文件**：`ui/theme/ThemeStore.kt`（`ThemeMode` 枚举 + `StateFlow`）、`ui/theme/AppPalette.kt`（材质 token 与浅/深两份实例）、`ui/theme/WindowChrome.kt`（`Configuration.isSystemNight()`、`windowBackgroundArgb()`）、`ui/settings/AppearanceSettingsScreen.kt`、`app/src/test/.../ui/theme/ThemeModeTest.kt`
- **色板**：`Theme.kt` 的 `darkTheme` 参数以前是**写死忽略**的，现在真正选 `DarkColors`。两套 `colorScheme` token 一一对应，加槽位必须两边都加，否则深色会掉回 M3 默认值
- **palette 的边界**：只装 M3 没槽位的材质色（发丝边框、内发光、玻璃表面、底栏着色、遮罩、投影开关）。业务语义色仍走 `colorScheme`，别往 palette 加
- **16 处写死白的分流**：页面表面全部改走 palette；`PhotoViewerDialog` 的 96% 黑底白图标、`AddRecordSheet` 缩略图上的 24dp 移除键**故意不翻**（永远压在照片上），各带注释——下次别当遗漏修掉
- **不重建 Activity**：换档走 `ThemeStore.mode` → `setContent` 重组；`MainActivity.applyWindowChrome` 在 `SideEffect` 里重设 `enableEdgeToEdge`（深底必须白图标）和 `window` 底色。`enableEdgeToEdge` 可重复调用
- **`values-night` 只跟随机身 uiMode**，认不出手动档位：手动深色 + 机身浅色时，系统启动窗仍可能约一帧浅底。要彻底消除得上 `applyOverrideConfiguration`/AppCompat 重建，代价是换档丢表单与滚动位置，已明确不做
- **数据边界**：`theme_mode` 存 `settings.xml`，是本机偏好，不参与云同步、不进备份 zip 的 `identity.json`
- **约束 7 已改写**（原来写死「主题固定浅色」），改主题相关代码前先读它

验证：`:app:assembleDebug` 成功；JVM **12/12**（新增 `ThemeModeTest` 5 项）；`:app:compileDebugAndroidTestKotlin` 通过。模拟器 `testgirl`（1080×2400）覆盖安装实测，截图 `.verification/screens/v430/`：浅色首页无回归（`02`）、跟随系统切机身深色即时变深且不重启（`03`）、深色抽屉与外观行尾「跟随系统」（`04`）、外观页深色（`05`/`09`）、深色点滴页大数字与左缘光（`07`）、手动固定浅色而机身仍深色 → 界面翻浅且状态栏图标同步翻深（`10`）、手动固定深色而机身切浅 → 保持深色（`11`）、`force-stop` 后冷启动仍是深色且 `settings.xml` 里 `theme_mode=dark`（`12`）、深色记录抽屉与饮食虚线占位卡（`13`/`14`）。`logcat -b crash` 无本应用 FATAL。设备回归 32 项未跑（本轮只动 UI 与主题，不碰同步/数据语义）。

## 未发布 — 温柔手账 · 奶油纸感（`workbuddy` 分支，版本号未改）

- **主题**：`Color.kt` / `AppPalette.kt` 换成米纸与暖黑纸两套；纸卡 100% 不透明，1dp 实色边、顶缘高光、左缘压痕、浅色暖影 / 深色无影。`Theme.kt` 提供 `LocalDegradationLevel`，主题级 `LocalIndication` 改为空 Node 工厂，全站关闭 M3 ripple。
- **共享组件**：`glassPanel` / `GlassCard` 名称保留但已改纸卡；新增 `paperTexture()`、`paperBackground()`、`focusRing()`、`PaperIconButton()`。`pressTint` 现在同时处理 hovered / pressed / focused / disabled；按钮 98% 缩放、行类 6% 纸墨，二者不混用。
- **底栏**：删除 Haze 依赖与采样源，`GlassBottomBar` 重写为 64dp 纸带、Ø32 实心粉圆选中态、震动点击，去掉长按液态拖拽。
- **页面**：三主页、上传/编辑页、五个设置页、抽屉、Sheet 全部换纸感；统一 10/12/20 圆角、20dp 页面卡间距、48dp 最小热区；纪念日数字 48sp，200% 字体按最终 72–88sp 补偿并独占一行。
- **表单**：新增/编辑输入焦点 2dp 粉边；Sheet 按 IME 可用高度重算，不足 240dp 切全屏表单态。`SegmentedChoices` 选中块改为中性抬升底 + 粉边，不再铺 18% 粉底。
- **验证**：JVM **12/12**；`compileDebugKotlin`、`compileDebugAndroidTestKotlin`、`assembleDebug` 通过；lint **0 错误 / 6 警告**（均为既有启动图标 monochrome 提示）。debug APK 已生成；尚未在真机完成深浅 / 320dp / 200% 字体 / IME / overdraw 走查，未改版本号。`LocalDegradationLevel` 已接入 L1/L2/L3/L5/L7 的确定性装饰降级，运行时性能自动触发尚未接入。

## 未发布 — v4.3.0 之后的 UI 打磨（版本号待用户拍板）

评审产出的三批小改动，全部只碰 UI：

- **对比度**：占位符/空态改 `palette.mutedText`，supporting 去掉 alpha，页面底上的粉字改新 token `palette.accentText`（浅 `PinkInk` / 深 `PinkDark`）；卡面粉字仍用 `primary`。规则在 design-system §1b，别再用 `copy(alpha=…)` 压文字颜色
- **按压反馈**：`Glass.kt` 的 `Modifier.pressTint`（6% 底，圆角跟该行裁形，破坏性行传语义色），接到设置行/操作行/分段/日期格/页头收起态/破坏性行；**不要**给行类加系统 ripple 或整行缩放
- **护栏**：`ComponentPreviews.kt` 深浅成对 Preview；`SheetSegmented` 已改名 `SegmentedChoices`（改名落进工作区的时间点早于本轮编辑，引用已核对一致）
- 截图 `15-light-meal-accent` / `16-light-sync-placeholder` / `17-light-row-pressed`（按压中的 6% 底）/ `18-dark-*`；模拟器中途出过一次 System UI ANR 弹窗（模拟器自身问题，与本 App 无关，`logcat -b crash` 干净）

## v4.2.2（2026-11-08）：侧边菜单 + 四个独立设置页

- **单一入口**：删除穿搭页齿轮、饮食/点滴「长按标题进设置」；现在只在**穿搭页左上角**保留一个菜单按钮（`FloatingTopBar(onOpenMenu=...)`），饮食/点滴没有按钮，边缘滑动也只允许在穿搭页触发。菜单位于左侧，只放设置，不混入「穿搭 / 饮食 / 点滴」模块入口。
- **四个独立页面**：`SettingsModule` 枚举提供四个模块及路由，分别是 `settings/profile`、`settings/sync`、`settings/backup`、`settings/battery`。每个模块有独立顶栏、独立页面和返回栈，不再共用一张可滚动设置页。
- **全局抽屉**：`ui/settings/SettingsMenu.kt` 的 `SettingsMenuDrawer`，用 Material3 `ModalNavigationDrawer`。宽度 `min(78% 屏宽, 340dp)`，右侧圆角 24，表面完全不透明，40% 黑遮罩；遮罩点击 / 右滑 / 返回键均可关闭，行高 ≥56dp。
- **展开动画**：抽屉自身滑入之外，内容按 `drawerState.targetValue` 做 240ms 淡入 + 18dp 右移（`FastOutSlowInEasing`），与滑入同向叠加，避免内容突然出现。
- **文案精简**：删除顶栏「设置」下方的说明文字和抽屉底部说明文字；四个菜单行保留标题 + 一句功能说明。
- **设置页拆分**：公共卡片、输入行、操作行和电池优化跳转抽到 `SettingsCommon.kt`；昵称防抖同步、同步测试流程、备份导出、白名单状态刷新均原样保留。
- **版本**：`versionName 4.2.2` / `versionCode 43`。
- **不改数据/同步**：仅 UI、导航与版本号变化，未触碰同步协议、Room 结构或删除语义。

验证：`:app:assembleDebug` 成功；JVM 7/7；lint 0 错误 / 6 警告（基线）；模拟器覆盖安装验证菜单、四个独立页面、返回栈与昵称/同步状态展示，`logcat -b crash` 无本应用 FATAL。截图：`.verification/screens/v422/`。

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
- `.verification/screens/v430/`（侧边设置菜单：默认/抽屉/分组定位/遮罩/边缘滑动/返回键/小屏）
- `.verification/screens/v422/`（四个独立设置页、菜单文案精简、返回栈）
- `.verification/screens/v422b/`（只有穿搭页左上角一个菜单入口；饮食/点滴无按钮、无边缘滑动）
- `.verification/screens/v421/`（抽屉空态/有内容/键盘/小屏、列表浅色与系统深色、对方/自己编辑页、通知落点、大图缩放）
- `.verification/screens/v410/`（浅色品牌截图）
- `app/schemas/com.thecutestgirl.app.data.AppDatabase/6.json`
