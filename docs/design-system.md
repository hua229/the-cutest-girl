# 设计系统（v4.1 浅色品牌）

> 品牌方向：简洁白 + 粉红。真实背景模糊只保留底部导航栏；其余卡面共享同一套玻璃**外观**。

## 1. 色彩

| 角色 | 值 | 用途 |
| --- | --- | --- |
| 页面底 | `#FAF7F8` | 带一丝暖粉的简洁白 |
| 卡面 | 白 78% | 玻璃外观填充 |
| 边框/分割线 | `#14201A1D`（8% 深） | 1dp 半透 |
| 主文字 | `#201A1D` | 约 15.6:1 |
| 次文字 | `#625A5F` | 约 6.9:1 |
| 辅助 | `#7E757C` | 约 4.9:1 |
| 品牌粉 | `#C92661` | 主操作/选中；白底约 5.3:1 |
| 粉容器 | `#FCE4EC` | 选中底/标签 |
| 软粉 | `#FDF2F6` | 光晕/空态底 |
| 成功/警告/失败 | `#2E7D5B` / `#9A5B00` / `#C62828` | 仅同步语义 |

粉红只允许出现在：主按钮、选中态、FAB 图标、纪念日数字与左缘光、少量标签底。正文、卡片底、大段背景保持中性。

## 2. 字体

- 系统无衬线（不引入字体文件）
- 页面标题 30sp Medium；正文 16sp Regular；辅助 14sp；角标 12sp
- 数字用 Monospace：日期栏 16sp；纪念日大数字 44sp Light（`BigNumeralStyle`）
- 禁止 700+ 字重、斜体、小字号负字距

## 3. 形状与间距

- 圆角：控件 8 / 卡片 16 / 容器·抽屉 24
- 按钮：主按钮全宽 48dp；次按钮 40dp；FAB 56dp
- 间距：4/8/16/24/32/48 网格；页面左右 24、卡内 16、卡间距 16
- 图标：Material Outlined，16/20/24dp，一档一尺寸；禁止 emoji 当功能图标

## 4. 组件

| 组件 | 规范 |
| --- | --- |
| `glassPanel` / `GlassCard` | 78% 白 + 顶部内发光（白 55%→透明）+ 1dp 8% 边框 + 8dp 中性投影；无 `glow/elevation` 参数 |
| `solidCard` | `glassPanel` 别名，圆角 16、固定 8dp 投影 |
| `AppTopBar` | `quietTopAppBar` + `IconTitle`，`windowInsets=0`（根 Scaffold 已处理状态栏） |
| `FloatingTopBar` | 玻璃面板 24dp → 收起 16dp；宽度动画 300ms；可带设置齿轮 |
| `PrimaryButton` | 品牌粉底 + 白字、48dp、圆角 8、按压 98% |
| `SoftCapsuleButton` | 白 86% + 8% 边框、40dp、禁用 alpha 0.38 |
| `SolidCircleButton` | 白 86% 圆 + 投影 10dp + 边框、按压 98% |
| `DestructiveAction` | 48dp、错误色、1dp 错误色 35% 边框、无 ripple |
| `AddRecordSheet` | 62% 屏高、40% 黑遮罩、顶部圆角 24、放弃确认；字段 `SheetTextField/SheetDateRow/SheetSegmented/SheetPhotoPicker` |
| `SyncStatusBar` | 空闲/同步中/成功/失败；失败带原因 + 重试 |
| `PhotoImage` | Coil 缓存 key = `name:lastModified`，瘦身后不显示旧图 |
| `PhotoViewerDialog` | HorizontalPager + 双指缩放 1–5x + 双击 + 单击关闭 + 48dp 关闭按钮 |

## 5. 材质与动效

- 卡片层次：投影 + 白玻璃 + 顶部内发光 + 1dp 半透边框；深色不再使用（全站浅色）
- 真实 Haze 只用于底部液态导航栏（白 72% + 白高光 10%，模糊 32dp，噪点 0.06）
- 全站一条弹簧：`UiSpringDamping=0.72 / UiSpringStiffness=420`（约 300ms）；液态滑块物理弹簧例外
- 页面切换：轻微位移 + 淡入（300ms/220ms）；按压 scale 98%，不做弹跳、不做循环动画

## 6. 相关文件

- `ui/theme/Color.kt`、`Type.kt`、`Shape.kt`、`Theme.kt`
- `ui/components/Glass.kt`（材质/按钮/顶栏）、`Common.kt`（卡片/日期条/状态条/大图）、`AddRecordSheet.kt`