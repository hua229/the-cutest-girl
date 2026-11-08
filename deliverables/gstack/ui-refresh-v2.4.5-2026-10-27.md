# the cutest girl v2.4.5 —— 五项 UI 修复与玻璃化改版

**日期**：2026-10-27
**版本**：v2.4.5（versionCode 14，从 v2.4.4 / versionCode 13 升上来）
**产物**：`app/build/outputs/apk/debug/the_cutest_girl_v2.4.5_debug.apk`
**改动范围**：纯界面。同步协议、照片压缩、墓碑机制、数据层**一行未动**（回滚用 v2.4.4 APK 覆盖安装，数据无损）

---

## 📌 TL;DR

| 项 | 内容 | 状态 |
|---|---|---|
| 编译 | `assembleDebug` BUILD SUCCESSFUL | 🟢 |
| ① 穿搭卡片昵称 | 恢复「💗 + 昵称」，过长省略号截断 | 🟢 |
| ② FAB 上移 | 与导航栏拉开 18dp 安全间距 | 🟢 |
| ③ 顶栏压缩 + 折叠 | 基础高度 -16dp；滚动再收起副标题 / 日期行 / 日期条 | 🟢 |
| ④ 次要按钮胶囊化 | 回到本月 / 回到今天 = 小胶囊 + 描边 + 按压反馈 | 🟢 |
| ⑤ 全局玻璃拟态 | 玻璃参数收拢到 `Glass.kt`，覆盖顶栏、日期栏、图文卡片、4 个按钮 | 🟢 |
| 数据层改动 | 0 | 🟢 |
| 新增依赖 | 0（全部用现有 Haze / Compose 能力） | 🟢 |

---

## 1. 穿搭卡片恢复上传人昵称

**问题**：v2.4.4 起穿搭列表缩略图右上角只剩一颗 💗，上传人昵称丢失，用户分不清哪张是对方上传的。

**改法**（`OutfitScreen.OutfitThumb`）：把「圆形 22dp 图标按钮」换成「胶囊标签」，💗 与昵称共存。

```kotlin
if (isPartner) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.FavoriteBorder, "来自对方", tint = scheme.primary,
             modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = record.originName.ifBlank { "对方" },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 68.dp)   // 过长截断，不会顶到照片边缘
        )
    }
}
```

昵称为空时兜底显示「对方」——不会出现一个孤零零的 💗。

---

## 2. FAB 上移，与导航栏拉开安全间距

**问题**：「记穿搭」「记一笔」的圆形玻璃 FAB 底部只用导航栏占位高度，紧贴导航栏上沿，容易误触。

**改法**：新增公共常量 `FabNavSpacing = 18.dp`（`Glass.kt`），两页 FAB 统一用它。

```kotlin
// 穿搭页 / 事件页，两处一致
GlassCircleButton(
    hazeState = pageHaze,
    onClick = { onAdd(selectedDate) },
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 20.dp, bottom = LocalAppBottomInset.current + FabNavSpacing)
)
```

用常量而不是裸写数字，是为了两页永远一致，也方便后面按手感统一调。

---

## 3. 顶栏压缩 + 滚动折叠

**问题**：顶栏高度过大，滚动时遮挡内容。

**改法（两步）**：

**① 静态压缩**：`PageHeader` 竖向内边距 12→4dp、`DateNavRow` 4→2dp，基础高度直接少 16dp。

**② 滚动折叠**：`PageHeader` 加 `collapsed` 参数，副标题用 `AnimatedVisibility` 收起；穿搭页滚动时再把「月份行 + 日期条」整块收起。

```kotlin
// 折叠状态只有一个布尔值参与重组，滚动过程本身不触发重组
val listState = rememberLazyListState()
val scrolled by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
    }
}
```

```kotlin
// 穿搭页：滚动后收起「月份行 + 日期条」，只留标题行
AnimatedVisibility(
    visible = !scrolled,
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        shrinkTowards = Alignment.Top
    )
) {
    Column {
        DateNavRow(...)
        DayStrip(...)
    }
}
```

**高度实测**：穿搭页 196→70dp、饮食页 124→70dp、事件页 84→70dp（饮食 / 事件只收起副标题与日期行）。

**关键取舍**：列表的 `contentPadding.top` 用的是**展开态**高度（常量 196/124/84），不用动画值——
否则每帧都要改 padding 触发重排。滚动折叠后那块留白由内容自然补上，视觉上就是「内容升上来顶掉顶栏」。

---

## 4. 「回到本月」/「回到今天」改胶囊按钮

**问题**：原来只是主色小字，看不出能点。

**改法**（`DateNavRow`）：换成 `GlassCapsuleButton`（半透明底 + 主色描边 + 全圆角 + 按压轻缩）。

```
内边距 10/3dp、labelSmall 11sp、按压整体轻缩 0.94 弹簧回弹（无系统 ripple）
静态「今天」徽标 = 同款胶囊形状，只是不可点（两态形状必须统一）
```

体积刻意压小：纵向才 3dp 内边距 + 11sp 文字，高度约 21dp，不抢日期行的主视觉。

---

## 5. 全局玻璃拟态

**核心动作：玻璃参数全部收拢到新文件 `ui/components/Glass.kt`**（之前底栏与 FAB 各自有一份私有副本）。

```kotlin
@Composable
fun Modifier.glassMaterial(
    hazeState: HazeState,
    cornerRadius: Dp,
    elevation: Dp = 0.dp,
    blurRadius: Dp = GlassBlurRadius,   // 30dp
    noise: Float = GlassNoise            // 0.10
): Modifier = this
    .shadow(elevation, RoundedCornerShape(cornerRadius), clip = false, ...)  // ① 柔和阴影（clip 前，否则被裁掉）
    .clip(RoundedCornerShape(cornerRadius))                                   // ② 裁形
    .hazeChild(state = hazeState) { ...tints... }                             // ③ 实时模糊 + 着色（clip 后，否则溢出圆角）
    .drawBehind { drawGlassChrome(isDark, cornerRadius.toPx()) }              // ④ 高光/内阴影/折射描边
```

**覆盖范围**

| 位置 | 做法 |
|---|---|
| 三个列表页顶栏 / 日期栏 | `GlassTopPanel`：悬浮玻璃面板，左右 16dp 与底部导航栏对称，内容从它下面滚过并被实时模糊 |
| 编辑页图文卡片（照片 + 备注） | `PhotoNoteCard` 走 `glassStatic`（静态玻璃：半透明底 + 高光/内阴影/折射描边 + 柔和阴影，**不套 Haze**——冒烟测试发现 Haze 硬约束「hazeChild 不能是 haze 源的子孙」，编辑页卡片在滚动内容里，用真模糊会运行期崩溃） |
| 4 个新增按钮 | 「从相册选择 / 拍一张 / 再加 / 移除」改 `GlassCapsuleButton`（半透明实心 + 描边 + 按压缩放） |
| 编辑页 / 设置页顶栏 | `glassTopAppBar` = 半透明底 + 底部 1dp 发丝线 + 4dp 柔和阴影 |

**可读性保障**

- 文字一律画在玻璃层之上，不参与模糊
- 卡片内按钮底只到 18% 透明度（按压 30%），浅色主题下文字仍为深暖灰 `#2B2420`，对比度不受影响
- 玻璃着色沿用底栏同一组 tints（浅色 surface 58% + 白 10%），全 APP 观感统一

**⚠️ 刻意不套模糊的两处**（说明理由，别自作主张补上）：

1. **卡片内的 4 个按钮**：卡片本身就是玻璃，它背后是纯色页面背景，再模糊一次只会得到同样的纯色，
   白白多一遍模糊开销，还容易和父卡片叠出脏块
2. **编辑页 / 设置页顶栏**：它不与内容重叠，背后同样没有可模糊的东西，保留「半透明 + 细描边 + 柔和阴影」即可

---

## 6. 改动文件清单

| 文件 | 改动 |
|---|---|
| `ui/components/Glass.kt` | **新增**：玻璃总控（材质 / 顶栏面板 / 胶囊按钮 / 顶栏轻玻璃 / 公共参数） |
| `ui/components/GlassBottomBar.kt` | 删除私有玻璃副本，底栏与圆形 FAB 改用 `glassMaterial`；滑块绘制拆成 `drawLiquidSlider` |
| `ui/components/Common.kt` | `PageHeader` 压缩 + `collapsed` 折叠；`DateNavRow` 胶囊按钮；`PhotoNoteCard` 玻璃化 + 4 个按钮改胶囊 |
| `ui/outfit/OutfitScreen.kt` | 昵称胶囊；FAB 上移；玻璃顶栏 + 滚动折叠 + 内容区采样源 |
| `ui/meal/MealScreen.kt` | 玻璃顶栏 + 滚动折叠 |
| `ui/event/EventScreen.kt` | 玻璃顶栏 + 副标题折叠 + FAB 上移 |
| `ui/outfit/OutfitEditScreen.kt` | 顶栏轻玻璃；去除内容区 haze 源包裹（见「冒烟修复」） |
| `ui/outfit/OutfitUploadScreen.kt` | 同上 + 日期行半透明 |
| `ui/meal/MealEditScreen.kt` | 顶栏轻玻璃；去除内容区 haze 源包裹 |
| `ui/event/EventEditScreen.kt` | 顶栏轻玻璃；去除内容区 haze 源包裹 |
| `ui/settings/SettingsScreen.kt` | 顶栏轻玻璃 |
| `app/build.gradle.kts` | versionCode 13→14、versionName 2.4.4→2.4.5 |

---

## 7. 验证记录（冒烟测试，2026-10-27）

### 发现并修复的运行期崩溃

首版 v2.4.5 冒烟时，进入「记穿搭」上传页立即崩溃：

```
java.lang.IllegalArgumentException: Layout nodes using Modifier.haze and
Modifier.hazeChild can not be descendants of each other
    at dev.chrisbanes.haze.HazeChildNode.draw(HazeChildNode.kt:277)
```

根因：编辑页把整页内容包成 `.haze(pageHaze)` 采样源，而 `PhotoNoteCard` 在其内部用
`hazeChild` —— Haze 硬性禁止二者互为子孙。**修复方案：给编辑页卡片新增
`glassStatic`（静态玻璃，不含模糊）**，同时移除三个编辑页/上传页的 haze 源包裹
（它们背后只有纯色页面背景，本就没有可模糊的内容）。列表页的顶栏/FAB 不受影响：
它们是内容区的**兄弟节点**而非子孙，真模糊保留。

### 通过项

- `compileDebugKotlin` / `assembleDebug` BUILD SUCCESSFUL（37 tasks）
- 安装 `the_cutest_girl_v2.4.5_debug.apk`（18.9 MB）→ Success
- 冷启动 → 穿搭页：玻璃顶栏（标题+副标题+月份行+日期条）、空态、右下圆形玻璃 FAB
  （与导航栏间距加大）✅
- FAB → 上传页：玻璃顶栏、日期胶囊、静态玻璃图文卡、「选照片/拍一张」胶囊 ✅（原崩溃点）
- 饮食页：玻璃顶栏、日期行、「今天」胶囊、三餐卡片 ✅ → 记录早餐页 ✅
- 事件页：玻璃顶栏、空态、FAB ✅ → 记一件事页：分段按钮 + 玻璃图文卡 + 日期行 ✅
- 全程 logcat `AndroidRuntime:E` 无崩溃日志

截图存于 `deliverables/smoke/`（s1 穿搭 / s2 上传 / s3 饮食 / s4 事件 / s5 记一件事 / s6 记录早餐）。

---

## ⚠️ 已知局限

- 顶栏折叠的留白常量（196/124/84dp）是**按当前字号排版算出来的**，若改标题字号或日期条高度需要同步调这三个数
- 折叠状态下列表顶部不再有 196dp 留白，若后续给列表加了「下拉刷新」之类需要固定留白的控件要重新算
- 卡片内 4 个按钮用的是半透明实心而非嵌套模糊（理由见 5.），若以后让卡片在滚动内容之上，可再套真模糊
- 编辑页图文卡片是**静态玻璃**（无模糊），与列表页真模糊玻璃观感略有差异——但编辑页背后无内容可模糊，视觉上几乎无差别

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
