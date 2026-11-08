# 任务：给 TheCutestGirl 加玻璃拟态（Glassmorphism）底部导航栏

你是在 E:\project\AI_seession\zcode\TheCutestGirl 这个已有 Android 项目上继续开发的工程师。
本任务只改 UI 层（ui/ 包 + build 配置），**不要碰** data/、sync/、Room、WebDAV、SyncManager、WorkManager 等任何业务逻辑。

## 一、项目现状（已核实，不要重新猜测）

- 单模块 `:app`，Kotlin 2.0.21，AGP 8.7.3，compileSdk 35 / minSdk 26 / targetSdk 35，jvmTarget 17
- Compose BOM 2024.10.01（compose-ui 1.7.4），Material3，navigation-compose 2.8.3
- 所有依赖版本统一在 `gradle/libs.versions.toml`
- `MainActivity.kt` 已经调用 `enableEdgeToEdge()`
- `app/src/main/java/com/thecutestgirl/app/ui/AppRoot.kt`
  - `AppRoot.kt:60` 是 `Scaffold`，`bottomBar` 里放的是 Material3 `NavigationBar`（**不透明容器**）
  - `AppRoot.kt:91-94`：`NavHost(..., modifier = Modifier.padding(padding))` ← **本任务的核心问题所在**
  - 三个 tab：`outfit`「穿搭」/`meal`「饮食」/`event`「事件」，`showBottomBar` 在 settings / 各 Edit 页隐藏
- `OutfitScreen.kt:62`、`EventScreen.kt:76` 各自有内层 `Scaffold` + `ExtendedFloatingActionButton`，且已设 `contentWindowInsets = WindowInsets(0,0,0,0)`
- `MealScreen.kt:70` 没有 Scaffold，直接 `Column`
- 三个页面的列表都是 `LazyColumn`，底部只有 `Spacer(Modifier.height(72.dp))`（Outfit）或 20.dp（Meal）
- 设置页与各 Edit 页已有 `.navigationBarsPadding()`，并且有自己的 `TopAppBar`

## 二、先理解再动手（这是本任务最重要的一段）

玻璃效果 = 背景实时模糊。**当前布局里内容被 `padding` 挡在导航栏上方，导航栏背后只有纯色 background，模糊采样不到任何东西**，所以第一步不是加库，是把布局改成「内容能滚到导航栏底下」。

目标结构：

```
Scaffold(bottomBar = { GlassBottomBar() })   ← 玻璃栏仍然画在内容之上（Scaffold 先画 content 再画 bottomBar）
  └─ content: NavHost(modifier = Modifier.padding(top = 仅顶部))   ← 不再消费底部 padding
        └─ 各页面 LazyColumn 自己用 contentPadding.bottom 预留出导航栏高度
```

## 三、第一步：加依赖

在 `gradle/libs.versions.toml`：

```toml
[versions]
haze = "1.1.1"

[libraries]
haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
haze-materials = { group = "dev.chrisbanes.haze", name = "haze-materials", version.ref = "haze" }
```

`app/build.gradle.kts` 的 dependencies 里加：

```kotlin
implementation(libs.haze)
implementation(libs.haze.materials)
```

**版本必须是 1.1.1，理由（已验证）**：haze-android 1.1.1 的 pom 依赖 `kotlin-stdlib 2.0.21` + `androidx.compose.ui:ui-android 1.7.5`，与本项目完全对得上。更高版本（1.2+ / 2.x）需要更新的 Kotlin 或 Compose，会编译失败。Gradle 把 compose-ui 从 1.7.4 提到 1.7.5 是正常的，**不要回退**。

### 1.1.1 的正确 API（不要用错版本的名字）

1.1.1 只有两个 modifier：`Modifier.haze(...)` 和 `Modifier.hazeChild(...)`。
**不要**使用 `hazeBlur` / `HazeInput` / `hazeEffect` —— 那些是 2.x 才有的，写上去必然编译不过。

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeTint

val hazeState = remember { HazeState() }

// 被模糊的内容（放在包住 NavHost 的那个 Box 上，不要放进每个 LazyColumn）
Box(modifier = Modifier.haze(state = hazeState)) { NavHost(...) }

// 玻璃表面
Box(
    modifier = Modifier.hazeChild(state = hazeState) {
        blurRadius = 24.dp          // 默认 20.dp
        noiseFactor = 0.08f         // 默认 0.15f，太大显脏
        backgroundColor = MaterialTheme.colorScheme.surface
        tints = listOf(
            HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)),
            HazeTint(Color.White.copy(alpha = 0.10f))   // 深色模式下换成 0.06f
        )
    }
) { /* 导航栏内容 */ }
```

`HazeChildScope` 可用属性：`blurRadius` / `noiseFactor` / `tints` / `backgroundColor` / `alpha` / `mask` / `progressive` / `inputScale` / `style`。
如需 `HazeMaterials.thin()`，要加 `@OptIn(ExperimentalHazeMaterialsApi::class)`（拿不准就先手写 `HazeTint`，别引实验 API）。
若某个 API 名字编译不过，以 1.1.1 官方文档为准：https://chrisbanes.github.io/haze/1.1.1/

## 四、第二步：布局改造（必做，否则玻璃看不见）

1. `AppRoot.kt`：
   - `Scaffold` 保留，`bottomBar = { GlassBottomBar(...) }`，把 Material3 `NavigationBar` 整个换掉
   - content lambda 内改成只消费顶部内边距：
     ```kotlin
     val bottomInset = padding.calculateBottomPadding()   // = 玻璃栏高度 + 系统导航条高度
     CompositionLocalProvider(LocalAppBottomInset provides bottomInset) {
         NavHost(
             navController = navController,
             startDestination = "outfit",
             modifier = Modifier.padding(top = padding.calculateTopPadding())
         ) { ... }
     }
     ```
   - 新建 `ui/components/LocalAppBottomInset.kt`：
     ```kotlin
     val LocalAppBottomInset = staticCompositionLocalOf { 0.dp }
     ```
2. 三个 tab 页的滚动容器，底部内边距要加上 `LocalAppBottomInset.current + 16.dp`：
   - `OutfitScreen.kt:121` 的 `LazyColumn`：`contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp, bottom = LocalAppBottomInset.current + 16.dp)`，同时删掉末尾那个 `Spacer(Modifier.height(72.dp))`
   - `EventScreen.kt:117` 的 `LazyColumn`：同上
   - `MealScreen.kt:138` 的 `LazyColumn`：同上，删掉末尾 `Spacer(20.dp)`
3. `OutfitScreen.kt:66`、`EventScreen.kt:80` 的 `ExtendedFloatingActionButton`：加 `modifier = Modifier.padding(bottom = LocalAppBottomInset.current)`，否则 FAB 会压在玻璃栏上
4. 检查内层 Scaffold（Outfit/Event/各 Edit 页）的 `contentWindowInsets`，避免状态栏高度被叠加两次（外层已给顶部内边距）。有叠加就统一设 `WindowInsets(0,0,0,0)`。
5. 确认 `showBottomBar == false` 的页面（settings / 三个 Edit 页）底部内边距仍然正确：它们自己已有 `.navigationBarsPadding()`，`LocalAppBottomInset` 在这些页面应为 0 或不生效 —— 请实测确认没有被压到底。

## 五、第三步：玻璃导航栏的具体规格

新建 `ui/components/GlassBottomBar.kt`。**不要**直接用 Material3 `NavigationBar`（它自带不透明 containerColor）。

结构：外层 `Box`（留白）→ 内层 `Row`（胶囊玻璃体）。

| 项 | 值 |
|---|---|
| 外边距 | 水平 16.dp，底部 10.dp，再 `.navigationBarsPadding()` |
| 高度 | 64.dp |
| 形状 | `RoundedCornerShape(26.dp)` |
| 模糊 | `blurRadius = 24.dp`，`noiseFactor = 0.08f` |
| 色调 | surface 60% + 白色 10%（深色模式：surface 55% + 白色 6%） |
| 描边 | 1.dp，用 `Modifier.drawBehind` 画 `Brush.linearGradient`：浅色 White 0.55 → White 0.08；深色 White 0.18 → White 0.04 |
| 阴影 | `Modifier.shadow(16.dp, shape, clip = false, ambientColor = primary.copy(alpha=0.20f), spotColor = primary.copy(alpha=0.28f))` |

**注意 draw 顺序**：`shadow` → `clip(shape)` → `hazeChild{...}` → `drawBehind`(描边)。`hazeChild` 必须在 `clip` 之后，否则模糊会溢出圆角。

每个 tab item（3 个，等宽 `weight(1f)`）：

- 未选中：`Icons.Outlined.*`，颜色 `onSurfaceVariant.copy(alpha = 0.72f)`，不显示文字或文字 11.sp
- 选中：`Icons.Filled.*`，颜色 `primary`，文字 11.sp `FontWeight.Medium`
- 选中背景：一个胶囊指示器，`Box(Modifier.height(40.dp).width(72.dp).clip(RoundedCornerShape(20.dp)).background(primary.copy(alpha = 0.14f)))`，用 `animateDpAsState` + `spring(dampingRatio = 0.75f, stiffness = 400f)` 平移
- 图标缩放：选中 `1.0f`，未选中 `0.92f`，`animateFloatAsState` + `spring(stiffness = Spring.StiffnessMediumLow)`
- 触控区不小于 48.dp（`Modifier.height(48.dp).weight(1f)` + `clickable(indication = null)` 自定义 ripple 或 `selectable`）
- 无障碍：`Modifier.selectable(selected, onClick, role = Role.Tab)` + `contentDescription = tab.label`（中文）
- 三个 tab 的图标沿用现有映射：穿搭 `Checkroom`、饮食 `Restaurant`、事件 `FavoriteBorder/Favorite`

性能：
- `blurEnabled = true`
- 可选 `inputScale = HazeInputScale.Auto`（若 1.1.1 有），能明显降开销
- 不要在 `LazyColumn` 的 item 上再加 `hazeChild`

## 六、第四步（可选，做完主体再考虑）

1. 顶部：让列表滚到状态栏下方，顶部叠一层从 background 到透明的 24.dp 渐变 scrim，保证标题可读
2. `ExtendedFloatingActionButton` 也套一层 `hazeChild`，与导航栏同一 `hazeState`
3. 卡片（`OutfitCard` / `MealCard` 等 `RoundedCornerShape(20.dp)`）加 1.dp 顶部高光描边，提升质感

## 七、硬约束（违反了算失败）

- 不改任何 `data/`、`sync/` 下的文件
- 不改导航路由、不新增页面、不改 tab 顺序和中文文案
- 不改主题色（`ui/theme/Color.kt` 的配色保持原样）
- 深色模式必须一起适配，不能只在浅色下好看
- 最低支持 API 26，Android 12 以下的降级表现（Haze 自动退化为 tint scrim）必须可接受，不能白屏或错位
- 所有代码注释用中文，风格与现有文件一致

## 八、验收标准（请逐条自检并在最后汇报）

1. `./gradlew :app:assembleDebug` 编译通过，0 warning 中的未使用 import
2. 三个 tab 页滑动时，列表内容确实从玻璃栏**下方**穿过，能看到模糊后的照片/卡片
3. 玻璃栏的圆角、描边、阴影在浅色和深色下都正确，没有黑边、没有模糊溢出
4. FAB 不被玻璃栏遮挡，且与玻璃栏之间留有空隙
5. 进入 settings / 三个 Edit 页时玻璃栏消失，底部留白正常，内容不被系统导航条压住
6. 快速滑动列表无掉帧（用 Android Studio Profiler 粗看，或在真机上目测不卡顿）
7. 选中态切换动画平滑，无跳变

## 九、如果 Haze 1.1.1 解析或编译失败（回退方案）

不要硬顶，改用**零依赖的伪玻璃**，视觉上 90% 接近：

- 容器：`surface.copy(alpha = 0.72f)` 半透明底 + `clip(RoundedCornerShape(26.dp))`
- 描边：同上的渐变描边
- 噪声：`Modifier.drawBehind` 里画一层 3% 透明度的静态噪点（可用 `Modifier.blur` 前的近似方案，或直接省略）
- 顶部内阴影：`Brush.verticalGradient(White 0.06f → Transparent)` 叠一层

其它布局改造（第四节）保持不变 —— 那部分无论用不用 Haze 都必须做。

## 十、汇报格式

完成后请说明：

1. 改了哪些文件，每个文件改了什么（一句话）
2. 最终玻璃栏的参数取值（模糊半径 / 色调 / 圆角 / 高度）
3. 编译与自检结果
4. 是否有遗留问题或建议的下一步
