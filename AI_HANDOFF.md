# the cutest girl —— 交接速查

> 当前版本 **5.0.0**（versionCode 46）｜最后更新 2026-11-25（正常包构建与验证通过）
> 给接手的 AI / 工程师：先读本页，再按需读 `docs/`。用户是代码小白，中文沟通，所有用户文案中文。

## v5.0.0 — 版本与提交范围（2026-11-25）

- **用户已批准**：升级至 `versionName=5.0.0` / `versionCode=46`，并 commit、推送 GitHub；普通 debug 产物由版本号动态命名为 `app/build/outputs/apk/debug/the_cutest_girl_v5.0.0_debug.apk`。
- **签名兼容**：沿用 4.3.1 固定证书 `b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547`，保留现有校验；下方旧签名迁移记录仍有效，不把提高版本号当成换签名兼容方案。
- **提交范围**：iOS 风格 UI、13 套图标、系统字体、文案精简、主题/草稿/滚动恢复一并交付；当前 UI 依赖工作区此前已有但尚未提交的 `BackupImporter`、`Partial` 同步状态及相关数据/同步测试 API，本次提交包含这些配套实现，不能描述为只提交了主题和界面文件。
- **5.0.0 验证**：Kotlin / AndroidTest 编译、19 项 JVM 单测、lint 0 错误 / 6 警告 / 2 信息及独立 `.verification` 模拟器完整设备回归 33 / 33 均通过。测试后已重建正常包 `com.thecutestgirl.app` / `5.0.0` / `46`，apksigner v2 校验成功，固定证书匹配。交付 `deliverables/the_cutest_girl_v5.0.0.apk`，文件 SHA-256 `b4d9e6ce46adbff0efa24152357679674ad59d275f37fedc740664a3ab01da9d`；详细证据见 `deliverables/release-v5.0.0.json`。Git 推送结果单独核验，不将本条当成已推送证明。
- **UI 验收边界**：41 项独立 UI 场景及原始截图是在版本号仍为 4.3.1（45）的同一功能代码上完成，详见 `deliverables/ui-refinement/validation.md`；这些场景没有全部在最终 5.0.0 APK 上重跑。19 项 JVM 与完整设备套件在 5.0.0 上另行执行，不混用两阶段结果。

## 开发记录 — 图标统一与界面文案精简（2026-11-25，并入 v5.0.0）

- **磨砂新增图标**：生成的磨砂「+」已接入 `ArtworkIcon` 的统一 `Add → R.drawable.art_add` 映射，新增操作沿用可访问描述；业务、餐次、设置与加号图标使用同套材质。原图、提示词和来源见 `output/imagegen/ios-icons/manifest.json`。原生 `image_gen` 未返回实际模型 ID，Image2 只能记录为用户请求的模型；relay 检查缺少 API 凭据，不能写成已核验模型。
- **文案精简**：移除主页标语、装饰性副标题、冗余操作教学、输入示例、重复日期/类型说明和次要尾标签；设置页删除昵称支持说明、电池/备份顶部教学段，云同步帮助保留为按需入口。真实作者、内容、字段名、日期、数量限制、状态、错误和必要确认后果均保留。
- **共享字段**：新增与编辑通过 `RecordTextField` 共用常驻 `label` 与 `BasicTextField`；后者设置 `contentDescription = label`，输入前后都能识别，热区至少 48dp。用户能辨认字段（UX），开发者维护单一组件（DX），智能体可用稳定语义定位和操作（AX），同时支持读屏。设置字段同样保留常驻名称和输入语义。
- **返回状态**：三个主页的 `expandedHeaderHeightPx` 改用 `rememberSaveable`，与 `rememberLazyListState` 一起恢复；返回已滚动页面时保留展开页头对应的留白。缺图卡片仅显示真实状态，有同步回调时显示「重试」并提供单一按钮语义；照片管理页无回调时传 `null`，不呈现无响应操作。
- **当前接口**：`PageHeader` 仅接收标题、折叠状态与设置/新增/回顶部等必要回调；`SettingsModule` 只保留 `key/title/icon` 与派生路由；`SettingsField` 保留字段名、值、输入配置和必要尾部操作。不要重新添加已删除的装饰或示例参数。
- **设置状态**：首页继续显示昵称、当前外观和真实同步状态，取消空闲时「已开启 / 未开启」及默认「去设置」尾标签；模块内部的权限操作、同步重试与备份确认保留。昵称保存/同步、云同步配置验证、备份恢复和电池权限回调语义不变。
- **字体与阶段验证**：所有文字继续使用 `FontFamily.Default` 跟随系统字体与缩放。该阶段使用版本 4.3.1（45）完成 Kotlin / AndroidTest 编译、19 项 JVM 单测与 41 项独立 UI 场景；lint 0 错误 / 6 警告 / 2 信息。浅深截图覆盖主页、表单、编辑、设置、确认与模拟同步失败；320dp / 200% 字体、键盘、草稿与滚动保持均有实测。分阶段构建范围、脚本误判修正与原始失败记录见 `deliverables/ui-refinement/validation.md`；当前 5.0.0 产物的验证按文首单独记录。

## 开发记录 — iOS 风格全面 UI 升级（2026-11-24，并入 v5.0.0）

- **用户已批准方向**：克制原生 iOS 风格、浅灰白与近黑深灰两套表面、低饱和玫粉点缀；保留三主栏并全面重排。下方白粉玻璃、纸感、旧侧栏等条目是历史记录，不代表当前界面。
- **用户后续调整**：餐次选择弹层右侧的“记录/编辑”文字已移除，整行仍可点；所有文字使用 `FontFamily.Default` 跟随系统字体和字号缩放。
- **主题与材质**：正文 17sp、辅助 14sp、页面标题 32sp；日期与纪念日数字改系统字形的 tabular numbers。内容卡默认实色无投影，真实玻璃模糊仅保留底栏；`AppPalette` 承载浅深发丝线、玻璃与滑块材质，正文与业务表面走 `MaterialTheme.colorScheme`。
- **入口与设置**：三个主页均在右上角提供设置和新增；设置进入统一 `settings` 首页，原侧边抽屉移除。首页分组链接我的信息、外观、云同步、数据备份、后台不被冻结五个独立模块；不增加第四个底栏标签。
- **底栏**：悬浮胶囊增加中文标签，保留原有滑块、点按、长按拖动、触觉、形变与弹簧吸附，适配可用宽度与字体大小。
- **浅深切换**：`ThemeStore.mode` 提供 SYSTEM/LIGHT/DARK 三档，缺失或非法值默认跟随系统，已有合法值沿用；只存本机 `settings.xml/theme_mode`，不进记录同步或 ZIP 的 `identity.json`。`MainActivity` 保持稳定 Compose 根树、处理 `uiMode`，主题重组时同步窗口底色及系统栏图标；不覆盖真实系统资源配置、不重建 Activity 清除草稿。手动主题与机身相反时，OS 启动首帧仍可能跟随机身色。
- **表单与业务**：新增、编辑、日期选择、照片查看、同步状态和设置表单统一新样式，保留草稿确认、上传人/对方权限、照片原子写入与原有同步/备份语义。版本号和固定签名不变。
- **图标来源**：图标素材来自原生 `image_gen` 工具。用户指定 Image2，但工具没有暴露实际模型 ID；relay 路径检查时没有 API 凭据，不能声称已通过 relay 核验模型，也不能把原生工具产物标成已确认的 Image2。
- **阶段验证**：该 UI 功能阶段结果见 `deliverables/ui-refinement/validation.json`，不将下方历史版本结果作为证据。隔离包使用合成记录，恢复显示设置/主题并校验记录摘要；未访问真实云端，该阶段未重跑完整设备同步/备份回归。5.0.0 后续已完成的 33 项独立设备回归和产物验证见文首。

## 5 分钟上手

- 项目：`E:\project\AI_seession\zcode\TheCutestGirl`（Windows）
- 构建：JDK 17（`gradle.properties` 的 `org.gradle.java.home` 是本机专用路径）
- 编译 + lint：

```bash
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

- 单测：`./gradlew.bat :app:testDebugUnitTest --offline`（5.0.0：19 / 19 通过）
- 设备回归：当前 33 项（含备份恢复），命令见 `docs/architecture.md` §6；跑完必须重出正式包（不要带 `-PverificationApp`）
- 正常包名 APK：`app/build/outputs/apk/debug/the_cutest_girl_v5.0.0_debug.apk`
- 关键入口：`MainActivity.kt`、`ui/AppRoot.kt`、`sync/SyncEngine.kt`、`ui/components/Glass.kt`

## 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/architecture.md` | 数据层、数据库 v6、同步协议/流程、业务规则、错误模型、验证命令 |
| `docs/design-system.md` | 当前 iOS 风格升级、浅深主题、材质、字阶、导航与组件 |
| `docs/changelog.md` | v1 → v5.0.0 版本摘要 |
| `README.md` | 功能、权限、构建、测试、项目结构 |

## 不可违反的约束

1. **只改 UI 时不要动同步/数据语义**；反之改同步必须跑当前 33 项设备回归（含备份恢复）。
2. `note` 归上传人、`reply` 归对方；删除必须「本地 + 墓碑」同事务；对方不能删除上传人的记录。
3. 云端目录固定：`thecutestgirl/records/<id>.json`、`records/T_<id>.json`、`photos/<name>.jpg`。
4. `origin == "local"` 只用于认领旧迁移记录，不能抢占对方 origin。
5. 修改 `WebDavClient` 等签名后，**必须重新 `assembleDebugAndroidTest`** 再跑设备回归（只编译 class 会装到旧测试包）。
6. 批量清理 import 不能按「符号出现次数」判断：`getValue/setValue` 是 `by` 委托隐式使用的，误删会导致全项目编译失败。
7. 按用户本轮批准的克制 iOS 风格升级：保留三主栏，三个主页右上角提供设置/新增，统一设置首页连接五个独立模块；保留顶栏滚动收缩、悬浮胶囊、滑块与长按拖动，并为底栏增加中文标签。支持默认跟随系统的浅/深三档；主题切换保留导航、滚动与草稿。内容表面实色，浅深材质统一通过主题 token 管理，详见 `docs/design-system.md`。
8. 不引入 Hilt/Koin、Retrofit、新 UI 库；新增依赖（包括测试依赖）先征得用户同意。
9. 版本号 / 打包由用户拍板；本轮已批准 `5.0.0` / `46`，后续不要擅自改 `versionCode/versionName`。
10. 照片写入必须原子化；新选照片先落 `draft_` 草稿，保存时 `promoteDraft`。
11. 所有用户可见文案中文，不用 emoji 当功能图标；页面、卡片和正文保持中性，低饱和玫粉用于主操作、选中态和少量纪念日重点，语义状态色按功能使用。图标需统一风格并保证浅深可辨；素材来源如实记录，未暴露的生成模型 ID 不作已核验声明。文案保持精简，但字段名称、真实作者/日期/状态、错误和必要确认不能删；字体使用 `FontFamily.Default` 跟随系统。

## 未发布 — 恢复旧界面与同步状态修复（2026-11-18）

- 恢复 b4fd0ef 白粉玻璃界面，保留合并顶栏和底部玻璃导航。WorkBuddy 增加的深色与外观设置已随界面回退。
- 连接测试、同步异常及协程取消增加状态兜底；立即同步不再在任务排队前标为运行。实际手机的卡住原因尚未核实，不能把模拟器回归当成真实云同步成功。
- 验证：单测 9/9，AndroidTest 编译通过，lint 0 错误 / 6 警告；隔离模拟器 WebDAV 回归 32/32。模拟器未通过网络验证时立即重试测试会超时，网络恢复后通过。
- 2026-11-18 真机已同签名覆盖安装 4.3.1；更新前后当前数据一致。经用户明确同意恢复旧备份并逐项校验 20 条穿搭、76 条饮食、10 条点滴、86 张关联照片和原设备身份；当前另外 2 张照片已在手机 Download 和电脑单独备份。真实坚果云同步于 12:43 显示成功，首次同步完成；同步后 20 条穿搭、77 条饮食、10 条点滴，旧记录均存在，3 条回复有云端更新，86 张原照片哈希一致。不得提交本机签名或个人备份。

## v4.3.1 — 版本升级与签名冲突排查（2026-11-17）

- 用户手机安装报签名冲突；模拟器已安装旧版的证书 SHA-256 为 `dcdc21df5edc6bb08523157a6dae4b517b53dd16214c33aa484bcf82fda8968e`，此前交付 APK / 当前用户目录调试密钥为 `b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547`，两者不匹配。
- 当前用户目录调试密钥创建于 2026-11-17；用户确认旧包来自同机 WorkBuddy。已检索常用构建位置、WorkBuddy 文件历史 / 快照 / blob 与回收站路径元数据，未找到原密钥。
- 用户明确批准备份后迁移：备份数据库、照片、身份及旧 APK，在电脑与手机下载目录保存两份，并先在独立模拟器包演练恢复。授权卸载后安装新签名的 4.3.1，恢复并逐项验证记录内容哈希、照片哈希、身份一致。Android Keystore 中的云同步密码需用户重新填写；旧签名的其他设备也需要先备份迁移，不能承诺直接覆盖。
- `versionName=4.3.1` / `versionCode=45`；普通 debug 打包增加 `verifyDebugUpgradeSigning`，固定证书为 `b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547`。密钥在本机 `.signing/the-cutest-girl-debug.keystore`，也可用 `TCG_SIGNING_STORE_FILE` 或 `-PtcgSigningStoreFile=...` 指定，禁止提交 Git；独立包名验证构建跳过兼容性检查。
- 验证：普通包构建 / AndroidTest 编译通过，JVM **12/12**，lint **0 错误 / 6 条既有警告**；签名校验可拦截不匹配证书。手机迁移验证及个人备份在被忽略的 `.verification/signing-v431/`，不上传 GitHub。

## 未发布 — 全局文案精简与原版底栏保留（2026-11-17）

- 三首页删除装饰性副标题，空态删除操作教学，新增抽屉删除重复日期 / 类型说明；日期字段、输入标签、作者、真实内容、数量限制、错误与删除确认保留。
- 菜单删除分组小标题及每项功能说明，保留模块名与当前值；昵称 / 外观页去掉说明段落，云同步帮助改为按需弹窗，状态和时间合并为一行。
- 用户指出底栏变化后，核对发现工作区此前的纸带改版被一并打包；已恢复仓库原有 `GlassBottomBar` 的悬浮胶囊、图标、滑块弹簧和长按拖动实现，仅材质颜色沿用当前主题 token。不要再次跟随页面文案调整底栏。
- 验证：常规包名 APK 构建成功，Kotlin / AndroidTest 编译通过，JVM **12/12**，lint **0 错误 / 6 条既有警告**。模拟器检查三首页 / 新增表单、五个设置页、同步帮助开合、主题切换、320dp / 200% 字体，以及原版底栏点按和长按拖动；`logcat -b crash` 无崩溃。证据在 `.verification/screens/minimal-copy/`。

## 未发布 — 穿搭顶部栏合并（2026-11-17）

- 当时的菜单入口移入共享页头，与标题、月份、日期条共用 `FloatingTopBar` 外框；月份与日期条使用完整卡面宽度。当前入口与参数以文首 2026-11-25 条目及设计系统为准。
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
