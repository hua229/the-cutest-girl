# 移动端缺陷修复与体验优化方案（v2.6.1）

> 目标版本：**v2.6.1 / versionCode 24**
> 技术栈前提：Android 原生 + Jetpack Compose（`compose-bom 2024.10.01`，Compose UI 1.7.x）、Kotlin 2.0.21、
> Haze 1.1.1（磨砂/模糊库，**不可升级到 2.x**，2.x 在本项目编译失败）、低版本 minSdk 26 / targetSdk 35。
> 本文所有根因结论均基于本仓库实际代码与真机日志，非通用推测。

---

## 一、崩溃：备注输入闪退（P0，已修复并复现验证）

### 1.1 现象与复现路径
- 触发路径：穿搭 / 饮食 / 事件任一「记一笔」页 → 点卡片里折叠的「＋ 写点备注（可选）」→ **立即闪退**
- 影响面：`PhotoNoteCard` 是三个模块共用组件，所以三个模块都会崩。用户报的是「饮食记录」，实际是全局问题
- 复现概率：100%

### 1.2 根因（有堆栈，不是推测）
```
FATAL EXCEPTION: main
java.lang.IllegalStateException: FocusRequester is not initialized.
	at androidx.compose.ui.focus.FocusRequester.findFocusTargetNode$ui_release(FocusRequester.kt:254)
	at androidx.compose.ui.focus.FocusRequester.requestFocus(FocusRequester.kt:63)
	at com.thecutestgirl.app.ui.components.CommonKt.PhotoNoteCard_...$lambda$100(Common.kt:1150)
```

代码 `Common.kt:1148-1151`（v2.6.0）：

```kotlin
.clickable {
    noteExpanded = true
    focusRequester.requestFocus()   // ← 崩在这
}
```

`FocusRequester` 只挂在**展开分支**的 `TextField` 上。点折叠行时 `noteExpanded` 刚被置 true，
这一帧 `TextField` 还没组合、`FocusRequester` 没有目标节点，`requestFocus()` 直接抛异常。
Compose 官方文档把这条写在异常信息里：「Are you attempting to request focus during composition?」

### 1.3 修复思路（防御性 + 时序正确）
1. 点击回调**只置位意图**，不直接请求焦点：`noteExpanded = true; pendingFocus = true`
2. 在**展开分支内部**用 `LaunchedEffect(pendingFocus)`，等 `TextField` 上树后再 `requestFocus()`
3. 外层再包 `runCatching`，兜住极端时序（快速连点、页面销毁中）
4. 顺带修掉同源的第二个 bug：`onFocusChanged` 在 TextField 首次上树时会先回调一次 `isFocused = false`，
   原先的判断会立刻把 `noteExpanded` 打回 false —— **看起来像点了没反应**。改成「真正拿到过焦点、又失去焦点」才收起

### 1.4 验收标准
| 项 | 标准 |
|---|---|
| 点折叠行 | 展开成 3 行并弹出键盘，无崩溃 |
| `logcat -b crash` | `com.thecutestgirl.app` 0 条 FATAL |
| 快速连点 10 次 | 不崩、不闪、状态一致 |
| 输入后点空白处 | 有内容不回缩；无内容回缩到 41dp |
| 编辑页已有备注 | 自动展开且不抢键盘焦点 |

### 1.5 回归测试点
穿搭上传 / 穿搭编辑 / 饮食编辑 / 事件（日常点滴）编辑 / 多图（≥2 张）分支 / 旋转屏幕后状态保持。

---

## 二、顶部栏胶囊缩放掉帧（P1）

### 2.1 现状实现与瓶颈定位
当前动画（`Glass.kt` `GlassTopBar`）：

```kotlin
val barWidth by animateDpAsState(if (collapsed) 126.dp else expandedWidth, tween(300, FastOutSlowInEasing))
val barCorner by animateDpAsState(if (collapsed) 20.dp else 26.dp, tween(300))
val barElevation by animateDpAsState(if (collapsed) 4.dp else 8.dp, tween(240))
Column(Modifier.width(barWidth).glassMaterial(hazeState, cornerRadius = barCorner, elevation = barElevation) ...)
```

四个逐帧开销，按影响从大到小：

| # | 问题 | 机制 | 量级 |
|---|---|---|---|
| A | **Haze 模糊层每帧重建** | `hazeChild` 依赖节点尺寸；宽度每帧变化 → 模糊 RenderNode 每帧重新分配 + 重新采样背景（blurRadius 30dp） | 最大 |
| B | **文字每帧重新测量** | 宽度变化触发 `PageHeader` 整棵子树 relayout，26sp 中文标题 + `overflow = Ellipsis` 每帧重新断行 | 大 |
| C | **双布局动画叠加** | 内层还有 `animateContentSize()`（备注折叠/副标题收起），与外层宽度动画叠加 → 每帧两次 layout pass | 中 |
| D | **阴影每帧重绘** | `shadow(8.dp → 4.dp)` 走 GraphicsLayer，尺寸+高程同时变 → 每帧重算阴影 | 小 |

### 2.2 优化方案（三条，按收益排序）

**方案 1（首选）：动画期间冻结模糊层**
- 增加 `isAnimating` 状态（`animateDpAsState` 用 `Animatable` + `isRunning`，或 `snapshotFlow { barWidth }` 判断连续变化）
- 动画期间 `blurRadius = 0.dp`（或 `HazeStyle(blurRadius = 0.dp)`），只保留半透明底色 + 描边；动画结束（~300ms）后再开模糊
- 人眼在运动中对模糊不敏感，静止时才有对比 —— 观感几乎无损，收益最大
- 关键参数：`blurRadius` 300ms 内切 `30dp ↔ 0dp`，用 `tween(120)` 淡入淡出避免突变

**方案 2：把「宽度动画」换成「两态交叉淡入 + 固定尺寸」**
- 展开态、收起态各是一套**固定尺寸**布局，用 `Crossfade`/`AnimatedContent` + `SizeTransform(clip = false)` 过渡
- 每帧只做 alpha 与 scale，不做文字 relayout；渲染管线只剩合成
- 代价：从「连续挤压」变成「收缩淡出/淡入」，动效性格略变。若用户偏好连续挤压感则保留方案 1+3

**方案 3：降低每帧布局成本（无论选哪种都要做）**
- `PageHeader` 标题在收起态改用 `maxLines = 1, softWrap = false, overflow = Clip`：不做断行计算，宽度不足直接裁切（收起态本来就只有两个汉字）
- 去掉内层 `animateContentSize()`，改为显式 `animateDpAsState` 高度目标（一次测量、一次动画），避免双 layout pass
- 收起态 `AnimatedVisibility` 的 `expandVertically/shrinkVertically` 改为 `fadeIn + scaleY`（纯绘制，不参与测量）
- `Modifier.graphicsLayer { }` 显式给顶栏一个合成层，减少过度绘制时的重绘范围
- 阴影只在**动画结束后**跟随 elevation，动画期间固定 6dp

### 2.3 量化目标与测量方法
| 指标 | 目标 |
|---|---|
| 掉帧率（单帧 > 16.6ms 的占比） | ≤ 1%（60Hz）/ ≤ 5%（90~120Hz 高刷） |
| 动画期间单帧 CPU 主线程耗时 P90 | ≤ 8ms |
| Haze RenderNode 重建次数 / 一次动画 | 0（动画期间关闭） |
| 连续 5 次收缩+展开的 janky frames | ≤ 2 |

测量命令（README 已记录 adb 坑）：
```bash
export MSYS_NO_PATHCONV=1
adb shell dumpsys gfxinfo com.thecutestgirl.app reset
# 连续 5 次上滑收起 + 点击胶囊展开
adb shell dumpsys gfxinfo com.thecutestgirl.app framestats > tmp/frames.txt
# 解析 FrameCompleted - IntendedVsync，统计 > 16.6ms 的帧数与 P90
```
> 基线数据需在有内容可滚动的 fixture 下采集（先灌测试数据，见 AI_HANDOFF「造 seed 数据」一节）。

### 2.4 风险与回归
- 关闭模糊会让动画期间顶栏「变实」，需确认与页面底色的对比不刺眼（浅色/深色各看一次）
- 去掉 `animateContentSize` 后，备注折叠的过渡改为固定高度动画，需确认展开时键盘弹起不闪烁
- 回归：三页滚动收起/展开、点胶囊回顶部、长按标题进设置、深浅色切换、列表首项不被玻璃遮挡

---

## 三、底部导航栏改「粉色液态玻璃磨砂」（P2）

### 3.1 现状
`GlassBottomBar` 现为**中性灰玻璃**：`glassMaterial(blurRadius = 22.dp, noise = 0.06f, tintScale = 0.72f)`，
tint 取 `surface`，选中块 `onSurface 7.5%`，图标 `onSurface`。用户要的是**粉色液态玻璃磨砂**。

### 3.2 参数方案（颜色 / 模糊 / 透明度 / 层次）

**底色与染色**
| 参数 | 浅色 | 深色 |
|---|---|---|
| `backgroundColor`（模糊后叠加） | `surface #FFFFFF` @ 62% | `surface #262019` @ 58% |
| tint 主色 | `primary #B25A79` @ **22%** | `primaryDark #E8A9BF` @ **18%** |
| tint 高光 | `White` @ 10% | `White` @ 6% |
| `blurRadius` | **26.dp**（比现在 22dp 更「磨」） | 26.dp |
| `noiseFactor` | **0.09f**（颗粒感，磨砂的关键） | 0.09f |

> 粉色不要直接铺，用 `primary @22%` 叠在白玻璃上，得到的是「奶粉」而不是「芭比粉」——
> 直接 `primary @60%` 会和页面奶油底 `#F1EBE5` 打架，且下拉时穿透的内容会变色。

**选中块（液态高光的承担者）**
| 参数 | 浅色 | 深色 |
|---|---|---|
| 填充 | `primary.copy(alpha = 0.16f)` | `primaryDark.copy(alpha = 0.22f)` |
| 顶部高光 | `White.copy(alpha = 0.32f)` 竖向渐变，覆盖块高上 45% | `White.copy(alpha = 0.18f)` |
| 边缘描边 | `White.copy(alpha = 0.30f)` 1dp | `White.copy(alpha = 0.22f)` 1dp |
| 投影 | `primary.copy(alpha = 0.20f)`，`blur 12dp`，Y 偏移 4dp | 同色 @0.28 |

**图标**
- 选中：`primary`（深色 `primaryDark`），填充态图标
- 未选中：`onSurfaceVariant`
- 尺寸 24dp 不变（v2.5.6 已定）

**层次顺序**（自下而上，与现有实现一致，只换色）
```
模糊背景 + 奶粉 tint → 选中块填充 → 选中块顶部高光 → 高光描边 → 图标/（已无文字）
```

### 3.3 与整体设计语言的一致性
- 主色仍取 `colorScheme.primary`，不引入新色值 → 换主题时自动跟随
- 底栏是页面里唯一允许大面积粉的部位；上方的实体卡、顶栏玻璃仍走中性色，避免"全身粉"
- 与顶栏玻璃的关系：顶栏是「白玻璃」，底栏是「粉玻璃」，形成上下呼应而不是两个独立贴片

### 3.4 机型与系统版本适配
| 情况 | 处理 |
|---|---|
| Android 12+（API 31+） | Haze 走 `RenderEffect` GPU 模糊，参数照上表 |
| Android 8~11（API 26~30） | 无硬件模糊，Haze 自动降级为半透明底色；此时把 `backgroundColor` 提到 82% 保证可读性 |
| 低端机 / 省电模式 | 检测 `PowerManager.isPowerSaveMode`，`blurRadius` 降为 0 + 提高底色不透明度 |
| 深色模式 | 用 `primaryDark` 的低透明度版本，**不要**直接用浅色 `primary`，否则发白发灰 |
| 高刷屏 | 底栏静止不重绘；拖动滑块时只动画 `drawBehind` 里的位置，不触发测量 |

### 3.5 量化与验收
| 项 | 标准 |
|---|---|
| 未选中图标对比度 | ≥ 4.5:1（对模糊后底色） |
| 选中块与底栏本体可分辨度 | ≥ 1.25:1 |
| 拖动滑块帧率 | ≥ 55fps（60Hz 机型），无掉帧尖峰 > 32ms |
| 深色模式 | 底栏不发灰，图标可读 |

### 3.6 风险与回归
- 粉色 tint 会削弱「内容从底栏后穿过」时的辨识度 → 需要给底栏加 1dp 顶部发丝线（`White @20%`）
- `noiseFactor 0.09` 在低 DPI 屏可能出现噪点块状 → 需要在 320dpi 机型上目视确认
- 回归：三页切换滑块动画、长按拖动换页、热区点击、深浅色切换、底栏与 FAB 不重叠

---

## 四、实施顺序与交付清单

| 顺序 | 项 | 状态 | 交付物 |
|---|---|---|---|
| 1 | 备注输入闪退 | **已完成并验证** | `Common.kt` 修复、`logcat -b crash` 0 条、截图 `.verification/screens/v261-note-focus.png` |
| 2 | 胶囊缩放掉帧 | 待实施（方案 1+3 优先，方案 2 备选） | 动画期间关闭模糊 + 消除双 layout pass；`framestats` 前后对比 |
| 3 | 底部导航栏粉色磨砂 | 待实施 | `GlassBottomBar` 换色 + 参数调整；浅 / 深色截图各一张 |

### 全量回归清单（三项改完统一过一遍）
1. 穿搭：新增（单图 / 多图 / 无图）、编辑、删除、按天聚合、日期条切换
2. 饮食：三餐记录、对方记录左色条、未记录虚线入口
3. 事件：日常点滴增删改、纪念日天数、横向大卡滑动
4. 顶部栏：滚动收起 / 点胶囊回顶 / 长按进设置（三页）
5. 底部栏：三页切换、滑块拖动、热区边缘点击
6. 设置：昵称修改、WebDAV 配置、同步状态条
7. 同步：`-PverificationApp` 跑 17 项 instrumented 回归
8. 深浅色各截一轮图，`logcat -b crash` 必须为空
