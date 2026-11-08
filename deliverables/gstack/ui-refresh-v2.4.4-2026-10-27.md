# the cutest girl v2.4.4 —— 四项 UI 优化实现说明

**日期**：2026-10-27
**场景**：全流程交付（设计改造 → 代码实现 → 编译验证）
**参与成员**：设计师（视觉一致性审查）+ 排障手（运行时正确性审查）+ 主理人汇编
**版本**：v2.4.4（versionCode 13，从 v2.4.3 / versionCode 12 升上来）
**产物**：`app/build/outputs/apk/debug/the_cutest_girl_v2.4.4_debug.apk`（18.9 MB，包名 `com.thecutestgirl.app` 不变，可覆盖安装）

> **追加说明（同日稍晚）**：本文档定稿后，用户又追加了两项需求并已落地、包含在这份 APK 里——⑤ 穿搭/事件两页的 FAB 改为与底栏同一套玻璃的**圆形玻璃 + 号按钮**（`GlassCircleButton`）；⑥ 启动图标替换为用户提供的粉色日记本图（5 个密度 foreground PNG + 背景色 `#F85B88`，删除过时的 monochrome 白心向量）。详见文末「追加改动」一节。

---

## 📌 TL;DR

- 🟢 **四项全部落地，编译通过**，APK 已产出并校验版本号（aapt2：`versionCode='13' versionName='2.4.4'`）
- 穿搭模块从「按月铺平的照片流」改为「月份 → 日期条 → 按天分组」三层结构，并新增指定某天的上传页
- 三个模块标题加语义 emoji（👗 🍚 💗），尺寸/间距由统一常量控制
- 底栏重做为液态玻璃，支持长按拖动滑块连续滑动 + 液态形变 + 弹性吸附
- 图文割裂感用一张 `PhotoNoteCard` 解决，替换原来的 `PhotoBlock` + 独立输入框
- **数据层只加了一条 DAO 查询**，同步协议、实体、迁移、墓碑机制一行未动 → 回滚只需覆盖安装旧 APK，数据无损

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟢 Go（编译通过、APK 产出、无新依赖） |
| 改动文件 | 11 个（新增 1 个、重写 3 个、改造 7 个） |
| 数据层改动 | 1 条（新增 `OutfitDao.observeByDate`） |
| 新增依赖 | 0（全部用现有 Compose / Haze / Activity 能力） |
| 阻塞项 | 0 |
| 建议真机验证 | 底栏长按拖动手感（模拟器无法还原真实触感） |

---

## 1. 各成员核心结论

### 🎨 设计师（视觉一致性）
- 核心判断：四项改动都落在既有设计语言内，没有另起炉灶；emoji 只出现在标题位是克制且安全的。
- 关键建议：emoji 字号必须与标题成固定比例而非「随便调」；图文卡片要靠「照片贴顶铺满 + 顶角跟随卡片圆角 + 一条发丝分割线」三件事同时成立才算连贯，只调间距不够。

### 🔧 排障手（运行时正确性）
- 核心判断：手势层是本次最大风险点——`awaitEachGesture` 是 `@RestrictsSuspension` 作用域，里面不能调用外部 suspend 回调。
- 关键建议：拖动阶段改用 `mutableFloatStateOf` 直驱、在 `drawBehind` 里读值（只失效 draw 不触发重组），松手再交给 `Animatable` 做弹性动画；这条既解决编译限制，也顺带解决了掉帧。

### 主理人裁定
两项建议全部采纳并落地。拖动中实时切页保留（跟手感优先），但已在交接文档标注「低端机若卡顿可改为松手才切页」的降级开关位置。

---

## 2. 四项改动：方案 + 关键代码 + 涉及文件

### ① 穿搭改造为按天聚合的记录视图

**方案**：保留「月份」作为浏览范围（用户已习惯 v2.4.3 的月份弹窗直选），在下面加一条横向日期条负责「切换 / 选择日期」，列表本身改为按日期分组。FAB「记穿搭」把当前选中日期带进上传页；上传页内可改日期、展示该日已有照片（点图进单条编辑、右上角 ✕ 删除）、多选新照片。

**涉及文件与组件**

| 文件 | 改动 |
|------|------|
| `data/Daos.kt` | 新增 `@Query("SELECT * FROM outfits WHERE date = :date ORDER BY createdAt DESC") fun observeByDate(date)` |
| `ui/outfit/OutfitViewModel.kt` | 新增 `OutfitDayGroup` 数据类；`OutfitViewModel` 加 `selectedDate` / `dayGroups` / `daysWithPhotos`；新增 `OutfitUploadViewModel` |
| `ui/outfit/OutfitScreen.kt` | 重写：三行头部 + 按天分组列表（`OutfitDaySection` / `OutfitThumb`） |
| `ui/outfit/OutfitUploadScreen.kt` | **新增**：`DatePickerRow` / `ExistingPhotosBlock` / `ExistingThumb` |
| `ui/components/Common.kt` | 新增 `DayStrip`（横向日期条） |
| `ui/AppRoot.kt` | 新增路由 `outfitUpload/{date}`，删除无参 `outfitEdit` |

**关键代码 —— 按天分组与选中日期**

```kotlin
// OutfitViewModel.kt
data class OutfitDayGroup(val date: LocalDate, val records: List<OutfitRecord>)

private val _selectedDate = MutableStateFlow(LocalDate.now())
val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

/** 当月记录按日期分组：日期新的在前，同一天内按创建时间新的在前 */
val dayGroups: StateFlow<List<OutfitDayGroup>> = outfits
    .map { list ->
        list.groupBy { it.date }
            .map { (date, records) ->
                OutfitDayGroup(LocalDate.parse(date), records.sortedByDescending { it.createdAt })
            }
            .sortedByDescending { it.date }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

private fun applyMonth(target: YearMonth) {
    _month.value = target
    // 选中日期要落回这个月：今天在当月就用今天，否则用该月 1 号
    if (YearMonth.from(_selectedDate.value) != target) {
        _selectedDate.value = if (YearMonth.now() == target) LocalDate.now() else target.atDay(1)
    }
}
```

**关键代码 —— 路由与上传入口**

```kotlin
// AppRoot.kt
composable("outfit") {
    OutfitScreen(
        // 上传入口带上当前选中的日期，照片统一归到那一天
        onAdd = { date -> navController.navigate("outfitUpload/$date") },
        onOpen = { id -> navController.navigate("outfitEdit/$id") },
        onOpenSettings = openSettings
    )
}
composable(
    route = "outfitUpload/{date}",
    arguments = listOf(navArgument("date") { type = NavType.StringType })
) { entry ->
    OutfitUploadScreen(
        dateArg = entry.arguments?.getString("date") ?: "",
        onOpen = { id -> navController.navigate("outfitEdit/$id") },
        onDone = { navController.popBackStack() }
    )
}
```

**关键代码 —— 横向日期条（DayStrip 核心片段）**

```kotlin
// Common.kt
@Composable
fun DayStrip(
    month: YearMonth,
    daysWithPhotos: Set<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
    val listState = rememberLazyListState()

    LaunchedEffect(month, selected) {
        val index = days.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val isToday = day == today
            Column(
                modifier = Modifier.width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .then(if (isToday && !isSelected) Modifier.border(1.dp, scheme.primary.copy(0.35f),
                              RoundedCornerShape(16.dp)) else Modifier)
                    .selectable(selected = isSelected, indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onSelect(day) }, role = Role.Tab)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                     style = MaterialTheme.typography.labelSmall,
                     color = if (isSelected) scheme.primary else scheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                // 有记录的日期：主色小圆点
                Box(Modifier.size(4.dp).clip(CircleShape)
                    .background(if (day in daysWithPhotos) scheme.primary.copy(0.7f) else Color.Transparent))
            }
        }
    }
}
```

**关键代码 —— 保存时每张照片一条记录，统一归到所选日期**

```kotlin
// OutfitUploadViewModel
fun save(onDone: () -> Unit) {
    val photos = newPhotos
    if (photos.isEmpty()) { onDone(); return }
    viewModelScope.launch {
        val day = _date.value
        val base = System.currentTimeMillis()
        photos.forEachIndexed { index, path ->
            dao.insert(OutfitRecord(
                id = UUID.randomUUID().toString(),
                date = day.toString(),
                photoPath = path,
                note = note.trim(),
                createdAt = base + index,   // 递增，保证组内顺序稳定
                updatedAt = base + index,
                origin = myDevice, originName = myName, dirty = true
            ))
        }
        newPhotos = emptyList(); note = ""
        SyncManager.enqueueNow(getApplication())
        onDone()
    }
}
```

---

### ② 标题前加语义 emoji

**方案**：emoji 集中在 `ModuleEmoji` 定义，由 `EmojiTitle` 统一渲染，字号/间距走常量，保证跨模块一致。主标题 26sp 配 22sp emoji，TopAppBar 标题 16sp 配 18sp emoji，间距统一 8dp。

**涉及文件**

| 文件 | 改动 |
|------|------|
| `ui/components/Common.kt` | 新增 `ModuleEmoji` / `PageEmojiSize` / `BarEmojiSize` / `PageEmojiGap` / `EmojiTitle`；`PageHeader` 增加 `emoji` 参数 |
| `ui/outfit/OutfitScreen.kt` | `PageHeader(emoji = ModuleEmoji.OUTFIT)` 👗 |
| `ui/meal/MealScreen.kt` | `PageHeader(emoji = ModuleEmoji.MEAL)` 🍚 |
| `ui/event/EventScreen.kt` | `PageHeader(emoji = ModuleEmoji.EVENT)` 💗 |
| `ui/outfit/OutfitEditScreen.kt` / `OutfitUploadScreen.kt` | TopAppBar 用 `EmojiTitle(👗)` |
| `ui/event/EventEditScreen.kt` | TopAppBar 用 `EmojiTitle(💗)` |
| `ui/settings/SettingsScreen.kt` | TopAppBar 用 `EmojiTitle(⚙️)` |
| `ui/meal/MealEditScreen.kt` | **不改**：标题已有餐次 Duotone 图标（日出/太阳/月牙），再叠 emoji 会撞语义 |

**关键代码**

```kotlin
// Common.kt
val PageEmojiSize: TextUnit = 22.sp   // 主标题 26sp → emoji 22sp
val BarEmojiSize: TextUnit = 18.sp    // TopAppBar 标题 16sp → emoji 18sp
val PageEmojiGap: Dp = 8.dp

object ModuleEmoji {
    const val OUTFIT = "\uD83D\uDC57"  // 👗
    const val MEAL = "\uD83C\uDF5A"    // 🍚
    const val EVENT = "\uD83D\uDC97"   // 💗
    const val SETTINGS = "⚙️"
}

@Composable
fun EmojiTitle(
    emoji: String, text: String,
    style: ComposeTextStyle = MaterialTheme.typography.headlineMedium,
    emojiSize: TextUnit = PageEmojiSize, emojiGap: Dp = PageEmojiGap
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = emojiSize, lineHeight = emojiSize, color = Color.Unspecified)
        Spacer(Modifier.width(emojiGap))
        Text(text, style = style, color = Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

> ⚠️ **约定变更**：`AI_HANDOFF.md` 里 v2.4.3 定的「UI 禁用 emoji」已作废——emoji 仅用于各模块标题这一处；状态表达（`SyncStatusBar` / `PhotoSyncPlaceholder`）仍然只用图标 + 语义色。

---

### ③ 液态玻璃底栏 + 长按拖动滑块

**方案**：玻璃体在原有 Haze 模糊之上再画「顶部镜面高光 + 底部内阴影 + 上亮下暗四段折射描边」；滑块改为在 `drawBehind` 里手绘，可随手指速度被拉长变薄、圆角变尖。手势用自定义 `detectLongPressDrag`：长按成立后才接管拖动，拖动中消费事件（让子控件 clickable 取消，避免拖完多切一次），松手弹性吸附。

**涉及文件**：`ui/components/GlassBottomBar.kt`（整个重写）

**关键代码 —— 状态划分（这是能编译 + 不掉帧的关键）**

```kotlin
// 拖动阶段：直接跟手，用 State 直驱（在 draw 里读，不触发重组）
var dragPos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
var dragSquish by remember { mutableFloatStateOf(0f) }
// 吸附 / 切 tab 阶段：走弹性动画
val settlePos = remember { Animatable(selectedIndex.toFloat()) }
val settleSquish = remember { Animatable(0f) }
var dragging by remember { mutableStateOf(false) }

.drawBehind {
    drawLiquidGlassBar(
        isDark = isDark,
        tabCount = currentTabs.size,
        sliderPos = if (dragging) dragPos else settlePos.value,
        squish   = if (dragging) dragSquish else settleSquish.value,
        primary  = scheme.primary
    )
}
```

**关键代码 —— 手势**

```kotlin
@OptIn(ExperimentalFoundationApi::class)
private suspend fun PointerInputScope.detectLongPressDrag(
    onLongPress: () -> Unit,
    onDrag: (x: Float, velocityX: Float) -> Unit,
    onRelease: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop

        // 长按超时前抬起或移动 → 直接返回，交给子控件的 clickable 走「点按切 tab」
        val settled = withTimeoutOrNull(timeout) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (dx * dx + dy * dy > slop * slop) break
            }
            true
        }
        if (settled != null) return@awaitEachGesture

        onLongPress()
        var lastX = down.position.x
        var lastT = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L)
            val velocityX = (change.position.x - lastX) / dt * 1000f
            lastX = change.position.x; lastT = change.uptimeMillis
            change.consume()                       // 让子控件 clickable 取消，防重复切 tab
            onDrag(change.position.x, velocityX)
        }
        onRelease()
    }
}
```

**关键代码 —— 跟手形变 + 橡皮筋 + 弹性吸附**

```kotlin
onDrag = { x, velocityX ->
    val itemWidth = size.width.toFloat() / currentTabs.size
    val raw = (x - itemWidth / 2f) / itemWidth
    val clamped = raw.coerceIn(0f, currentLastIndex.toFloat())
    val overshoot = raw - clamped

    val byVelocity = (abs(velocityX) / VELOCITY_FULL).coerceIn(0f, 1f)   // 越快拉得越长
    val byEdge = (abs(overshoot) / 0.8f).coerceIn(0f, 1f) * 0.85f        // 拖出边界由橡皮筋接管

    dragPos = clamped + overshoot * RUBBER_BAND      // 0.16：拉得动但越拉越沉
    dragSquish = maxOf(byVelocity, byEdge).coerceIn(0f, 1f)

    val nearest = clamped.roundToInt().coerceIn(0, currentLastIndex)
    if (nearest != currentSelectedIndex) currentOnSelect(currentTabs[nearest].route)  // 跟手切页
},
onRelease = {
    val target = dragPos.roundToInt().coerceIn(0, currentLastIndex)
    if (target != currentSelectedIndex) currentOnSelect(currentTabs[target].route)
    scope.launch {
        settlePos.snapTo(dragPos)          // 先把动画起点对齐手指松开的位置，避免跳变
        settleSquish.snapTo(dragSquish)
        dragging = false
        launch { settleSquish.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 420f)) }
        settlePos.animateTo(target.toFloat(), spring(dampingRatio = 0.45f, stiffness = 380f))
    }
}
```

**关键代码 —— 液态滑块绘制**

```kotlin
val w = baseW * (1f + MAX_STRETCH * squish)   // 横 +30%
val h = baseH * (1f - MAX_SQUASH * squish)    // 纵 -20%
val r = (h / 2f) * (1f - 0.22f * squish)      // 圆角随拉伸变尖，像被拉扯的液滴
drawRoundRect(color = primary.copy(alpha = 0.08f),   // 玻璃上的投影
    topLeft = Offset(left - 2.dp.toPx(), top + 2.dp.toPx()),
    size = Size(w + 4.dp.toPx(), h), cornerRadius = CornerRadius(r, r))
drawRoundRect(color = primary.copy(alpha = 0.18f), topLeft = Offset(left, top),
    size = Size(w, h), cornerRadius = CornerRadius(r, r))
```

---

### ④ 消除图文割裂感

**方案**：把「照片选择区」和「文字输入栏」装进同一张卡片。四件事同时成立才算连贯：① 照片贴到卡片顶边铺满、顶部两圆角跟随卡片 20dp 圆角；② 照片区与输入区之间只放一条 1dp / 28% 发丝分割线（左右留 12dp），连而不糊；③ 输入框去掉外框与底色，由卡片充当容器；④ 空态（两个按钮）与有图态卡片轮廓不变，不跳变。

**涉及文件**

| 文件 | 改动 |
|------|------|
| `ui/components/Common.kt` | 新增 `PhotoNoteCard` + `PhotoRemoveButton`，**删除** `PhotoBlock` |
| `ui/outfit/OutfitEditScreen.kt` | PhotoBlock + 独立 OutlinedTextField → `PhotoNoteCard(allowRemove = false)` |
| `ui/meal/MealEditScreen.kt` | 同上 |
| `ui/event/EventEditScreen.kt` | 日常点滴用 `PhotoNoteCard`；纪念日无照片，仍用独立输入框 |
| `ui/outfit/OutfitUploadScreen.kt` | 用 `maxPhotos = 9` 承载「多图 + 备注」 |

**关键代码**

```kotlin
@Composable
fun PhotoNoteCard(
    photos: List<String>, onPhotosChanged: (List<String>) -> Unit,
    note: String, onNoteChange: (String) -> Unit, notePlaceholder: String,
    allowRemove: Boolean = true, maxPhotos: Int = 1,
    photoRatio: Float = 4f / 5f, cardCorner: Dp = 20.dp
) {
    val shape = RoundedCornerShape(cardCorner)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(scheme.surface, shape)
            .border(1.dp, scheme.outline.copy(alpha = 0.4f), shape)   // 与列表卡片同一语言
    ) {
        when {
            photos.isEmpty() -> { /* 相册 / 拍照 两个按钮，卡片轮廓不变 */ }
            photos.size == 1 -> {
                // 贴顶铺满，顶部两角跟随卡片圆角 → 像卡片自带的一层，不是塞进去的方块
                Box(Modifier.fillMaxWidth().aspectRatio(photoRatio)
                    .clip(RoundedCornerShape(topStart = cardCorner, topEnd = cardCorner))
                    .clickable { viewerPhoto = photos.first() }) { /* AsyncImage + 移除键 */ }
            }
            else -> { /* 两列网格，格内 4:5，整体留 8dp 内边距 */ }
        }

        // 发丝分割线：把照片区和输入区分开，但不切断卡片
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp)
            .background(scheme.outline.copy(alpha = 0.28f)))

        TextField(
            value = note, onValueChange = onNoteChange,
            placeholder = { Text(notePlaceholder, style = MaterialTheme.typography.bodyMedium,
                                 color = scheme.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(), minLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                cursorColor = scheme.primary
            )
        )
    }
}
```

---

### ⑤ 版本号

`app/build.gradle.kts`：`versionCode = 12 → 13`，`versionName = "2.4.3" → "2.4.4"`。
APK 名自动生成为 `the_cutest_girl_v2.4.4_debug.apk`（已用 aapt2 校验）。

---

## 3. 交叉审查发现与修复（编译通过后追加）

编译通过不等于能跑。审查阶段翻源码核对后，发现并修掉了一个**编译期抓不到、一进编辑页就崩**的问题：

**🔴 `PickMultipleVisualMedia(maxItems)` 有 `require(maxItems > 1)`**
（`androidx.activity` 1.9.3 `ActivityResultContracts.kt:872`）

`PhotoNoteCard` 原来无条件构造 `PickMultipleVisualMedia(maxPhotos)`，而穿搭单条编辑页 / 饮食页 / 事件页都是 `maxPhotos = 1` → 构造时直接抛 `IllegalArgumentException("Max items must be higher than 1")`，**组合阶段就崩，任何编辑页都打不开**。

```kotlin
// 修复：永远构造一个合法的多选合约，实际是否走它由 launchGallery 决定
val pickMultiple = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxPhotos.coerceAtLeast(2))
) { ... }

val launchGallery: () -> Unit = {
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    if (maxPhotos > 1) {
        // createIntent 里还有一条 require(maxItems <= getPickImagesMaxLimit())，
        // 系统选择器上限更小时退回单选，避免崩在这句话上
        val launched = runCatching { pickMultiple.launch(request) }.isSuccess
        if (!launched) pickSingle.launch(request)
    } else {
        pickSingle.launch(request)
    }
}
```

**🟡 旋转屏幕会把用户已改的日期打回初始值**

`OutfitUploadScreen` 里 `LaunchedEffect(dateArg) { vm.ensureDate(dateArg) }` 在配置变更（旋转）后会重跑，把用户手动改过的归属日期覆盖掉。已加 `dateInitialized` 幂等标志，只首次进入时初始化。

**🟡 分割线与文字起始位置差 4dp**

M3 `TextField` 内容左右内边距是 16dp，而卡片里的发丝分割线原来是左右 12dp，文字和线没对齐。分割线改为 16dp，与文字成一条线。

**已核对确认无问题的点**

- `rememberLauncherForActivityResult` 内部用 `rememberUpdatedState(onResult)`，回调用的永远是最新的 lambda → 追加照片不会用旧列表覆盖
- `waitForUpOrCancellation` 会检查 Final pass 的消费标记 → 拖动中 `change.consume()` 能让子控件 clickable 正确取消，不会"拖完又多切一次 tab"
- `DayStrip` 的 `animateScrollToItem` 有 `index >= 0` 保护，切月时不会越界
- `save()` 里 `createdAt/updatedAt = base + index` 逐条递增，与同步的「updatedAt 大者胜」不冲突
- `deleteExisting` 走的是与 `OutfitEditViewModel.delete` 完全一致的「删库 + 墓碑 + 删文件 + 触发同步」路径

---

## 4. 编译踩坑记录（下轮别再踩）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | `Unresolved reference 'LocalHapticFeedback'` | Compose 1.7.5 **没有**这个 API（1.9 才加） | 改用 `LocalView.current.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` |
| 2 | `Unresolved reference 'withTimeoutOrNull'` | 它是 `AwaitPointerEventScope` 的**成员方法**，不是顶层扩展 | **删掉 import**，直接在 `awaitEachGesture` 块内调用 |
| 3 | `Expression 'longPressTimeoutMillis' cannot be invoked as a function` | 它是 `Long` 属性不是方法 | 去掉 `()` |
| 4 | `Restricted suspending functions can invoke ... only on their restricted coroutine scope` | `awaitEachGesture` 是 `@RestrictsSuspension`，块内不能调用外部 suspend lambda | 拖动阶段改 `mutableFloatStateOf` 直驱，松手用 `scope.launch { Animatable... }` |
| 5 | `Platform declaration clash: setNewPhotos` | `var newPhotos` 的属性 setter 与同名 `fun setNewPhotos` JVM 签名撞车 | 删掉函数，直接用属性赋值 |
| 6 | `Argument type mismatch: Function0<Any> but Function0<Unit> expected` + 一串 `@Composable invocations can only happen` | `launchGallery` 里 `runCatching{}.onFailure{}` 返回 `Result`，把 lambda 推断成 `() -> Any`，传给 `Button(onClick:)` 失败并连带动摇了后续推断 | 显式声明 `val launchGallery: () -> Unit`，用 `isSuccess` 而不是返回 `Result` |

---

## ✅ 行动清单

| # | 行动 | 负责方 | 紧急度 |
|---|------|--------|--------|
| 1 | 真机安装 v2.4.4，实测底栏长按拖动手感（跟手度 / 形变幅度 / 吸附回弹） | 用户 | P0 |
| 2 | 穿搭页实测：切月 → 点日期条 → FAB「记穿搭」→ 多选照片 → 保存，确认照片归到所选日期 | 用户 | P0 |
| 3 | 上传页实测：删除已有照片后对方手机能同步到墓碑删除 | 用户 | P1 |
| 4 | 若拖动时切页在低端机卡顿，改 `onDrag` 里不调 `onSelect`、只在 `onRelease` 切页 | 工程 | P2 |
| 5 | 上传页暂存照片目前只存内存，进程被杀会丢；需要时再落盘 | 工程 | P2 |

---

## ⚠️ 待完善 / 已知局限

- **未做模拟器实测**（当前无可用 AVD）：本轮只做了编译 + APK 产物校验，UI 真机观感与手势手感需用户装机确认
- 底栏拖动时实时切页，低端机上可能掉帧 → 降级方案见行动清单 #4
- 上传页的新照片只暂存内存，进程被杀会丢失（已保存的已有照片不受影响）
- 穿搭日期条固定展示整月，未按最早记录收敛范围
- 有意未做：通知 deeplink 直达某天、Design Token 全量归位、底栏文字标签（保持图标-only 语言）

---

## 追加改动（同日，四项定稿后用户追加，已包含在同一 APK）

### ⑤ 穿搭 / 事件 FAB 改圆形玻璃按钮

**诉求**：两页右下角的 ExtendedFAB（圆角矩形 + 文字）风格突兀，改成与液态玻璃底栏完全同一套材质的圆形「+」按钮。

**方案**：在 `GlassBottomBar.kt` 里把玻璃参数抽成两处共用的私有常量/函数（`GlassBlurRadius = 30.dp`、`GlassNoise = 0.10f`、`glassTints()`、`drawGlassChrome()`），新增 `GlassCircleButton`——52dp 正圆容器，复用同一组模糊/着色/高光/内阴影/折射描边/阴影，只把形状从胶囊换正圆。点按反馈为整体轻缩 0.90 + 弹簧回弹（无 ripple）。每页自建 `remember { HazeState() }` 作模糊采样源，页面内容 `.haze(fabHaze)`，按钮 `hazeChild` 悬浮其上，位置 BottomEnd（end 20dp / bottom = 底栏占位高度）。

**涉及文件**：`ui/components/GlassBottomBar.kt`（抽公共玻璃参数 + 新增 `GlassCircleButton`）、`ui/outfit/OutfitScreen.kt`、`ui/event/EventScreen.kt`（ExtendedFAB → GlassCircleButton）。

### ⑥ 启动图标替换

**诉求**：用户提供了新的粉色日记本图标图，替换默认安卓机器人图标。

**做法**：`tools/make_launcher_icon.py` 自动裁掉原图四周白边 → 居中补正方形 → LANCZOS 缩放到 5 个密度（mdpi 108 / hdpi 162 / xhdpi 216 / xxhdpi 324 / xxxhdpi 432 px）写入 `mipmap-*/ic_launcher_foreground.png`；`mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 的 foreground 指向新 PNG、background 用 `@color/ic_launcher_background`；`values/colors.xml` 采样图标粉色定为 `#F85B88`；**删除 monochrome 层**（旧的白心向量与新图标视觉不一致）。

---

## 📚 成员产出索引

- 设计师（视觉一致性审查）：独立审查后回传主理人
- 排障手（运行时正确性审查）：独立审查后回传主理人
- 主理人：代码实现 + 编译验证 + 本文档汇编

---

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
