# 变更历史（摘要）

> 详细交接记录见 git 历史；本文件只保留版本级摘要。

## v5.0.0（versionCode 46，2026-11-25）— iOS 风格全界面升级

- 统一浅灰白 / 近黑深灰表面与低饱和玫粉，内容卡改实色，玻璃模糊保留给悬浮胶囊导航；保留原有滑块、点按、长按拖动和触觉，并增加中文标签。
- 三个主页统一大标题及右上设置 / 新增入口；用分组设置首页连接我的信息、外观、云同步、数据备份、后台设置五个独立模块。默认跟随系统，支持手动浅色 / 深色。
- 接入 13 套统一磨砂图标，包括业务、三餐、设置与新增加号；原图和提示词随清单保留。原生图像工具未暴露模型 ID，因此不能将用户请求的 Image2 标为已核验模型。
- 字体使用 `FontFamily.Default` 跟随系统；精简标语、示例和重复说明，新增与编辑共用常驻字段名和稳定输入语义，保留作者、日期、错误、权限及确认后果。
- 保存三主页的展开页头高度与列表位置，设置往返和主题切换保留草稿与可见内容锚点；缺图状态和可用重试入口保持一致。
- 同时纳入工作区已有的 ZIP 备份恢复、`Partial` 同步状态及相关测试和夹具；当前界面依赖这些 API，本次提交覆盖完整功能代码。
- 沿用 4.3.1 固定证书，普通 debug 文件名为 `the_cutest_girl_v5.0.0_debug.apk`；不改变已完成固定签名迁移的安装身份。
- 5.0.0 验证：Kotlin / AndroidTest 编译、19 项 JVM 单测、独立模拟器完整设备回归 33 / 33 通过；lint 0 错误 / 6 警告 / 2 信息。正常包重新构建并通过 v2 签名和固定证书校验，产物证据见 `deliverables/release-v5.0.0.json`。
- UI 验收边界：41 项场景与原始截图在版本号升级前、同一功能代码的 4.3.1（45）构建上完成；分阶段结果单独保留，不写成在 5.0.0 APK 上重跑了全部 UI 场景。

## v4.3.1（versionCode 45）— 版本升级与签名校验

- 按用户要求升级版本号；保留穿搭顶部栏合并、文案精简和原版悬浮底栏。
- 定位安装冲突为新旧证书不同；原密钥未找回，经用户批准完成备份、换签名安装及数据恢复，并逐项核对记录、照片和身份。
- 新签名固定保存于本机 `.signing/`；普通 debug 打包校验证书，防止再次换签名。支持 `TCG_SIGNING_STORE_FILE` 或 `tcgSigningStoreFile`，签名文件禁止提交 Git。旧签名用户首次升级需先备份迁移，迁移后的安装可继续覆盖升级。

## 未发布 — 温柔手账 · 奶油纸感（`workbuddy` 分支）

- 全站从玻璃拟态迁移为不透明纸卡：浅色中性米纸 `#F3EDE2` / 卡面 `#FCF8F1`，深色暖黑纸 `#171412` / 卡面 `#221E1A`；品牌粉保持 `#C92661` / `#E8699C`，仅作印泥点缀
- 新增程序化纸纹（固定种子 + `drawWithCache`）、顶缘高光、左缘压痕、暖墨投影；删除 Haze 依赖与全部模糊采样，底栏改为 64dp 纸带 + 32dp 实心粉圆选中态
- 圆角统一 10 / 12 / 20，页面卡间距 20dp，纪念日数字 44→48sp；200% 字体下按最终 72–88sp 补偿并独占一行
- 组件六态补齐：hovered / pressed / selected / disabled / error / focused；按钮 98% 缩放、行类 6% 纸墨；主题级关闭 M3 ripple，新增 2dp 焦点框
- `SegmentedChoices` 选中块改中性抬升底 + 粉边，去除 18% 粉底；SoftCapsule 禁用改实色底 + 实色字；AddRecordSheet 增加 IME 高度重算和全屏表单兜底
- 同步协议、Room、删除语义、照片原子写均未改；版本号仍为 4.3.0
- 验证：JVM 12/12、compileDebugKotlin / compileDebugAndroidTestKotlin 通过、lint 0 错误 / 6 既有警告；真机视觉与性能走查待执行

## 未发布 — v4.3.0 之后的 UI 打磨（随下一个版本号一起发布）

- **对比度**：占位符与空态文字从「alpha 压过的 onSurfaceVariant」（浅色 ≈2.6:1）改为 `palette.mutedText`（浅 4.9 / 深 6.5）；supporting 小字去掉 0.85 alpha；落在页面底/半透表面上的粉色文字新增 `palette.accentText`（浅 `PinkInk` ≈8.9:1 / 深 `PinkDark` ≈5.6:1），卡面上的粉色文字仍用 `primary`。规则写进 design-system §1b
- **按压反馈**：新增 `Modifier.pressTint`（按压期间 6% 主色底，破坏性操作传自己的语义色），接到 7 处原本「点下去毫无反应」的可点面：设置菜单行、设置操作行、分段控件、日期格、页头收起态、破坏性操作行。底栏仍用震动 + 液态滑块
- **护栏**：新增 `ComponentPreviews.kt`，玻璃卡/主按钮/分段控件各出浅+深一对 Preview；`SheetSegmented` 改名 `SegmentedChoices`（它早就不只属于抽屉）
- **穿搭页两处真机反馈修复**：① 左上角设置菜单入口去掉白圆 chip，改成融进页面底的无边框图标按钮（48dp 热区 + 圆形 `pressTint` 按压底）；② 列表顶部让位原来只记展开态顶栏高度，顶栏收起后与首个日期组之间空出一整段页面底，改为展开/收起各记一次实测高度并按 `scrolled` 用同一条弹簧补间切换
- 未改同步协议、数据结构与权限；版本号待用户拍板后再 bump

## v4.3.0（versionCode 44）— 深色模式

- 主题从「全站浅色」改为**浅 + 深双主题**：`Theme.kt` 补一份与浅色 token 一一对应的 `darkColorScheme`；深色是暖黑（页面底 `#141113`、卡面 `#211C20`）+ 提亮品牌粉 `#E8699C`，不是把浅色取反
- 新增 `ui/theme/AppPalette.kt`：M3 `colorScheme` 没槽位的材质色（发丝边框、卡面内发光、玻璃表面、底栏 Haze 着色、遮罩、投影开关）收进一份 palette，组件经 `LocalAppPalette` 读取，不在组件里判断深浅
- 共享组件层 16 处写死 `Color.White/Black` 分流：页面表面改走 palette（`glassPanel` 内发光、`dashedCard`、`SolidCircleButton`、`SoftCapsuleButton`、底栏滑块高光/描边与模糊着色、照片移除键），**压在真实照片上的浮层保持深底白字不翻**（看大图、缩略图移除键），各带注释
- 深色不打投影（`shadowAmbient/SpotAlpha=0`）：暖黑底上黑投影看不见只会糊边，层次由明度差 + 1dp 边框 + 7% 内发光承担
- 档位：`ThemeMode` = 跟随系统（默认）/ 浅色 / 深色，存 `settings.xml` 的 `theme_mode`；本机偏好，不进云同步、不进备份 `identity.json`
- 生效链路不重建 Activity：`ThemeStore`（StateFlow）+ `isSystemInDarkTheme()` → `resolve()` → 色板；`MainActivity.applyWindowChrome` 同步翻 `enableEdgeToEdge` 系统栏图标与 `window` 底色
- `values-night(-v31)` 原本还继承 `Material.Light` 且 `windowLightStatusBar=true`、底色 `#FAF7F8`，改为真深色（`#141113` + 白状态栏图标），冷启动不再闪白
- 新增第 5 个独立设置页「外观」（`settings/appearance`）：三选一分段控件复用 `SegmentedChoices`（当时还叫 `SheetSegmented`，同轮打磨里改名），点选即存即生效；抽屉给它单独一组，行尾显示当前档位，手动固定时染成主色
- 已知限制：`values-night` 只跟随机身 uiMode，手动固定深色而机身是浅色时，系统启动窗仍可能有约一帧浅底（Compose 起来后即正确）
- JVM 单测 5 项：`ThemeModeTest` 覆盖 key 往返、未知/缺失值回落 `System`、`resolve` 四组组合
- 未改同步协议、数据结构与权限

## v4.2.2（versionCode 43）— 设置入口收敛与独立设置页

- 设置入口收敛为**唯一一处**：只保留穿搭页左上角菜单按钮；移除穿搭齿轮、饮食/点滴「长按标题进设置」和这两页的边缘滑动；菜单本身固定在左侧
- 四个模块拆成四个**独立页面**：我的信息（`settings/profile`）、云同步（`settings/sync`）、数据备份（`settings/backup`）、后台不被冻结（`settings/battery`）；菜单点击直达，不再共用一张设置页
- 抽屉：屏宽 78%（上限 340dp）、右侧圆角 24、表面完全不透明、40% 黑遮罩；遮罩点击 / 右滑 / 返回键关闭；行高 ≥56dp
- 展开动画：内容按抽屉目标状态做 240ms 淡入 + 18dp 右移（`FastOutSlowInEasing`），与滑入同向叠加
- 菜单文案精简：删除「设置」下方说明与抽屉底部说明；菜单行只保留标题和一句功能说明
- 公共设置控件抽到 `SettingsCommon.kt`；昵称同步、WebDAV 测试、备份导出、白名单刷新逻辑不变
- 仅 UI/导航与版本号改动，未改同步/数据语义

## v4.2.1（versionCode 42）— v4.2.0 验收修复

- FIX-01 抽屉完全不透明、约 82% 屏高、小屏最小 360dp；顶栏 0.96
- FIX-02 身份与密码拆分（`identity.xml` / `dav_secret.xml` / `settings.xml`）；旧数据自动迁移；备份 zip 增加 `identity.json`；系统备份只排除密码与同步账本
- FIX-03 云端目录被删：`listDir` 404 清 `dirsReady`，下一轮自动 MKCOL；单条记录 404 只跳过
- FIX-04 对方编辑页标题/日期只读，隐藏「修改」
- FIX-05 本地孤儿照片 GC 随 App 启动执行（不要求开启同步）
- FIX-06 页面底压深 `#EFE8EB` + 卡片 92% 白，对比度 ≥1.18:1
- FIX-07 饮食左缘注释统一、墓碑 N+1 查询消除、编译 warning 清零、证据截图补齐
- 设备回归 29 → **32 项**（身份迁移、备份恢复身份、目录重建）

## v4.2.0（versionCode 41）— 工程收尾

- 文档拆分：`docs/architecture.md`、`docs/design-system.md`、`docs/changelog.md`；`AI_HANDOFF.md` 收敛为交接速查
- 测试与构建说明更新（JVM 7 项、设备回归 29 项、JDK 17 可移植说明）
- 本版本不改变同步协议与数据结构，仅为 v4.1.0 质量修复的正式打包版本

## v4.1.0（versionCode 40）— 浅色品牌 + 质量修复

- 全站切到「简洁白 + 粉红」浅色品牌；玻璃只保留底栏真实模糊，其余卡面统一玻璃外观
- Batch 1（P0）：单图换图/孤儿清理、对方不能删除、删除+墓碑同事务、同步配置先测后存、dirsReady 修复、照片原子写入/流式传输、同步任务去重
- Batch 2/2b（P1-UI）：饮食评论、通知定位、抽屉完善、禁用态、浅色闪屏、统一顶栏/删除行、编辑图 3:2、设置入口、跨午夜刷新、48dp 热区、大图缩放/左右滑
- Batch 3（P1-PERF）：删无用 Haze、顶栏折叠稳定、共享 OkHttp、prefs 写去重、维护限频、批量合并事务、Coil 缓存 key、轮询退避
- Batch 4（P2）：结构化同步状态 StateFlow、WebDAV 错误模型 + 可重试标记、取消异常
- Batch 5（P2）：数据库索引 + v6 迁移、schema 导出、JSON schema/字段校验、死参数清理
- Batch 6（P2）：SavedStateHandle 表单、草稿照片、本地/云端孤儿照片 GC
- Batch 7（P2）：Keystore 加密应用密码、备份规则、zip 导出
- Batch 8（P2）：设备回归 29 项、README 重写、通知/无障碍/密码开关打磨

## v4.0.0（versionCode 39）— 深色玻璃

- 按「去AI味4步」全站深色：`#060607` + 梅紫/钴蓝/余烬橙、玻璃面板、AddRecordSheet、8dp 网格
- 后续被 v4.1.0 的浅色品牌方向取代（保留代码历史）

## v3.0.x（versionCode 34–38）— unslop 与图文容器

- v3.0.0：全站去 emoji、单色强调、玻璃收敛到底栏、8dp/图标/文案统一
- v3.0.1：图文容器规范（卡 18/缩 12/圆角 8、3:2、评论等高线）
- v3.0.2–3.0.4：纪念日光影往返，最终定为左缘横向渐变

## v2.9.x（versionCode 29–33）— 同步可靠性

- v2.9.0 同步提速 + 通知可达（前台 20s、增量指纹、通知去重/直达）
- v2.9.1 修「去设置」点击无效（权限 + 三级回退）
- v2.9.2 换云盘自动重置同步账本

## v2.8.x — 评论与权限

- note / reply 双字段拆分，各自作者与时间戳，字段级合并
- 评论作者单独记录（replyName），修复「我的评论」显示错误

## v2.4.x–v2.6.x — 液态玻璃与页面体系

- 底部液态玻璃导航（拖拽形变/弹簧吸附）
- 悬浮玻璃顶栏折叠、日期条、模块色、玻璃 FAB
- 用户明确保留的细节：纪念日左缘横向光影

## v1 → v2

- 主键从自增数字迁移为 UUID 字符串（旧记录加 `L` 前缀）
- 引入同步字段（updatedAt/origin/dirty）与墓碑表
