package com.thecutestgirl.app.ui.outfit

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.OutfitRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.AddRecordSheet
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.AppMonthPickerDialog
import com.thecutestgirl.app.ui.components.DateNavRow
import com.thecutestgirl.app.ui.components.DayStrip
import com.thecutestgirl.app.ui.components.EmptyHint
import com.thecutestgirl.app.ui.components.FabNavSpacing
import com.thecutestgirl.app.ui.components.FloatingTopBar
import com.thecutestgirl.app.ui.components.LocalAppBottomInset
import com.thecutestgirl.app.ui.components.PageHeader
import com.thecutestgirl.app.ui.components.PaperIconButton
import com.thecutestgirl.app.ui.components.PhotoImage
import com.thecutestgirl.app.ui.components.PhotoSyncPlaceholder
import com.thecutestgirl.app.ui.components.rememberToday
import com.thecutestgirl.app.ui.components.SheetDateRow
import com.thecutestgirl.app.ui.components.SheetPhotoPicker
import com.thecutestgirl.app.ui.components.SheetTextField
import com.thecutestgirl.app.ui.components.SoftCapsuleButton
import com.thecutestgirl.app.ui.components.SolidCircleButton
import com.thecutestgirl.app.ui.components.pressTint
import com.thecutestgirl.app.ui.components.UiSpringDamping
import com.thecutestgirl.app.ui.components.UiSpringStiffness
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.toCnShort
import com.thecutestgirl.app.ui.components.toWeekday
import com.thecutestgirl.app.ui.theme.LocalAppPalette
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.outlined.ChatBubbleOutline

/**
 * v2.5.3：顶栏高度不再写死常量（原来 196dp 是按字号和 padding 手算的，改一次字号就得重算一次，
 * 而且算错就是首条内容被玻璃盖住）。改成用 onSizeChanged 实测顶栏高度，内容起点 = 实测 + 10dp。
 * 顶栏折叠时高度变小，留白自动跟着收，不会留下一条空档。
 */


/**
 * 穿搭页（v2.4.4 建立，v2.4.5 顶栏折叠 + 玻璃化）：按**天**聚合的记录视图。
 * - 悬浮玻璃顶栏：月份行 + 日期条，滚动后整块收起，只留标题行
 * - 列表以「日期」为分组单位，每天一组，组内是这一天的全部照片（两列 4:5）
 * - 「记穿搭」把当前选中日期带进上传页，新照片统一归到那一天
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * @param initialDate 从通知点进来时要落到的日期；null = 用默认（今天）
 */
fun OutfitScreen(
    onAdd: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
    onOpenMenu: () -> Unit,
    initialDate: LocalDate? = null,
    onInitialTargetConsumed: () -> Unit = {}
) {
    val vm: OutfitViewModel = viewModel()
    // 只在**首次**组合时按通知日期定位一次。
    // 用 rememberSaveable 记住「已经跳过了」：切走再切回来时 NavHost 会重建这个 composable，
    // 没有这个守卫的话每次回来都会被拽回通知里那天，用户手动翻的日期就白翻了。
    // 记住「上次按哪个日期跳过」，而不是一个布尔值：App 开着的时候可能连着来第二条通知，
    // 布尔守卫会让第二次点击毫无反应。存日期字符串就同时满足「不重复跳」和「换了日期要再跳」。
    var jumpedDate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDate) {
        val target = initialDate?.toString() ?: return@LaunchedEffect
        if (jumpedDate != target) {
            // 必须 jumpToDate 而不是 setDate：列表按月订阅，只改选中日期的话
            // 别的月份的记录压根不在查询结果里，用户点完通知还是看到当月。
            vm.jumpToDate(initialDate)
            jumpedDate = target
            onInitialTargetConsumed()
        }
    }
    val month by vm.month.collectAsStateWithLifecycle()
    val dayGroups by vm.dayGroups.collectAsStateWithLifecycle()
    val daysWithPhotos by vm.daysWithPhotos.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()

    // 上次同步失败时，照片占位显示「同步不了」而不是「还在路上」
    val context = LocalContext.current
    val myDeviceId = SettingsStore.deviceId(context)
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()
    val syncFailed = syncStatus.phase == SyncPhase.Failure
    val retrySync = {
        // 没填坚果云账号时不能承诺"已经开始同步"，否则点了什么也不会发生
        if (SettingsStore.isSyncConfigured(context)) {
            SyncManager.enqueueNow(context)
            Toast.makeText(context, "已开始同步，照片稍后出现", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "还没开启同步，去设置里填坚果云账号", Toast.LENGTH_SHORT).show()
        }
    }

    val today = rememberToday()

    // 新增记录抽屉：统一 AddRecordSheet，写入复用现有 OutfitUploadViewModel
    val addVm: OutfitUploadViewModel = viewModel()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSheetDatePicker by remember { mutableStateOf(false) }
    var sheetInitialDate by remember { mutableStateOf(selectedDate) }
    val addDate by addVm.date.collectAsStateWithLifecycle()

    LaunchedEffect(showAddSheet) {
        if (showAddSheet) {
            // 打开时复位表单并落到触发入口的日期
            addVm.setDate(sheetInitialDate)
            addVm.newPhotos = emptyList()
            addVm.note = ""
        }
    }
    if (showSheetDatePicker) {
        AppDatePickerDialog(
            initial = addDate,
            onPick = { addVm.setDate(it); showSheetDatePicker = false },
            onDismiss = { showSheetDatePicker = false }
        )
    }
    val addDayExistingCount = dayGroups
        .firstOrNull { it.date == addDate }?.records?.size ?: 0

    var showMonthPicker by remember { mutableStateOf(false) }
    if (showMonthPicker) {
        val now = YearMonth.now()
        AppMonthPickerDialog(
            initial = month,
            minYear = now.year - 10,
            maxYear = now.year,
            onPick = { vm.setMonth(it.year, it.monthValue); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }

    // 列表滚动状态：用来驱动顶栏折叠（只有"是否滚动过"这一个布尔值参与重组，不会每帧重组）
    val listState = rememberLazyListState()
    val topBarScope = rememberCoroutineScope()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
        }
    }
    // 顶栏 / FAB 共用的模糊采样源已移除：底栏的采样源在 AppRoot，页面内再挂一份是纯开销

    // 顶栏两种形态各记一次实测高度：列表顶部让位跟着「当前形态」走。
    // v4.3.0 之前只记展开态，收起后列表仍让着展开态的高度，顶栏和首个日期组之间
    // 会空出一大截页面底（真机截图反馈）；改成按 scrolled 切换目标高度并用同一条
    // 弹簧补间，折叠动画期间列表让位跟着一起收，稳态不再留空。
    var expandedHeaderHeightPx by remember { mutableIntStateOf(0) }
    var collapsedHeaderHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val targetTopArea by animateDpAsState(
        targetValue = with(density) {
            (if (scrolled && collapsedHeaderHeightPx > 0) collapsedHeaderHeightPx
             else expandedHeaderHeightPx).toDp()
        } + 10.dp,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "outfitTopArea"
    )
    val topArea = targetTopArea

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---------- 内容区（模糊采样源，只服务底部液态玻璃导航栏）----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (dayGroups.isEmpty()) {
                    EmptyHint(
                        title = "这个月还没有穿搭记录",
                        icon = Icons.Outlined.Checkroom,
                        modifier = Modifier.padding(top = topArea)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            // 顶部让出实测的顶栏高度，首条内容不会藏在玻璃底下
                            top = topArea,
                            // 底部留出玻璃导航栏的高度，最后一张卡片能完整滚出玻璃区域
                            bottom = LocalAppBottomInset.current + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = dayGroups, key = { it.date.toString() }) { group ->
                            OutfitDaySection(
                                // 同步合并进来的新一天淡入 + 让位，而不是整屏硬跳
                                modifier = Modifier.animateItem(),
                                group = group,
                                isSelected = group.date == selectedDate,
                                syncFailed = syncFailed,
                                myDeviceId = myDeviceId,
                                onSelectDay = { vm.setDate(group.date) },
                                onAddThisDay = { sheetInitialDate = group.date; vm.setDate(group.date); showAddSheet = true },
                                onRetry = retrySync,
                                onOpen = onOpen
                            )
                        }
                    }
                }
            }

            // ---------- 悬浮顶栏：内容从它下面滚过；左上角是唯一的设置菜单入口 ----------
            FloatingTopBar(
                collapsed = scrolled,
                // 菜单随标题一起收进同一张卡；大字体下为标题保留足够宽度。
                collapsedWidth = 56.dp + 136.dp * density.fontScale.coerceAtLeast(1f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged {
                        if (scrolled) collapsedHeaderHeightPx = it.height
                        else expandedHeaderHeightPx = it.height
                    }
            ) {
                PageHeader(
                    title = "穿搭",
                    icon = Icons.Outlined.Checkroom,
                    collapsed = scrolled,
                    onOpenMenu = onOpenMenu,
                    // 收起态点标题回列表顶部，左侧菜单仍独立打开设置。
                    onClick = if (scrolled) ({
                        topBarScope.launch { listState.animateScrollToItem(0) }
                    }) else null
                )
                // 滚动后把「月份行 + 日期条」整块收起，只留标题行
                AnimatedVisibility(
                    visible = !scrolled,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
                        shrinkTowards = Alignment.Top
                    )
                ) {
                    Column {
                        val isCurrentMonth = month == YearMonth.from(today)
                        DateNavRow(
                            label = "${month.year}年${month.monthValue}月",
                            subLabel = if (isCurrentMonth) null else "回到本月",
                            subLabelClickable = !isCurrentMonth,
                            onSubLabelClick = { vm.toCurrentMonth() },
                            onLabelClick = { showMonthPicker = true },
                            onPrev = vm::previousMonth,
                            onNext = vm::nextMonth,
                            prevContentDescription = "上个月",
                            nextContentDescription = "下个月"
                        )
                        // 切换 / 选择日期：横向日期条，有记录的日期带小圆点
                        DayStrip(
                            month = month,
                            daysWithPhotos = daysWithPhotos,
                            selected = selectedDate,
                            today = today,
                            onSelect = vm::setDate,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            // 实心圆形 + 号按钮：底部在导航栏占位之上再抬 16dp，避免紧贴导航栏误触
            SolidCircleButton(
                onClick = { sheetInitialDate = selectedDate; showAddSheet = true },
                contentDescription = "记穿搭",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = LocalAppBottomInset.current + FabNavSpacing
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "记穿搭",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ---------- 新增记录抽屉（半屏） ----------
    if (showAddSheet) {
        val dismissSheet = {
            addVm.newPhotos.forEach { addVm.removeNew(it) }
            addVm.note = ""
            showAddSheet = false
        }
        AddRecordSheet(
            title = "记穿搭",
            onDismiss = dismissSheet,
            primaryEnabled = addVm.canSave,
            hasUnsavedChanges = addVm.newPhotos.isNotEmpty() || addVm.note.isNotBlank(),
            onPrimary = { addVm.save { showAddSheet = false } }
        ) {
            SheetDateRow(
                label = "归到",
                value = "${addDate.toCnShort()} ${addDate.toWeekday()}",
                onClick = { showSheetDatePicker = true }
            )
            SheetPhotoPicker(
                photos = addVm.newPhotos,
                maxPhotos = 9,
                onAdd = { addVm.newPhotos = (addVm.newPhotos + it).take(9) },
                onRemove = { addVm.removeNew(it) }
            )
            SheetTextField(
                value = addVm.note,
                onValueChange = { addVm.note = it },
                placeholder = "备注（可选）",
                singleLine = false,
                minLines = 3
            )
            if (addDayExistingCount > 0) {
                SoftCapsuleButton(
                    onClick = {
                        showAddSheet = false
                        onAdd(addDate)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "管理这一天的 $addDayExistingCount 张照片",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalContentColor.current
                    )
                }
            }
        }
    }
}

/** 一天的分组：日期头（可选中 / 可加照片）+ 这一天的照片网格 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutfitDaySection(
    modifier: Modifier = Modifier,
    group: OutfitDayGroup,
    isSelected: Boolean,
    syncFailed: Boolean,
    myDeviceId: String,
    onSelectDay: () -> Unit,
    onAddThisDay: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dayHeaderPress = remember { MutableInteractionSource() }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .pressTint(dayHeaderPress, cornerRadius = 10.dp)
                    .clickable(
                        interactionSource = dayHeaderPress,
                        indication = null,
                        onClick = onSelectDay
                    )
                    .semantics { selected = isSelected }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    "${group.date.toCnShort()} ${group.date.toWeekday()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) LocalAppPalette.current.accentText else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            PaperIconButton(onClick = onAddThisDay) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加这一天的穿搭",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 照片网格：两列，每格 4:5，与穿搭的视觉身份一致
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.records.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { record ->
                        OutfitThumb(
                            record = record,
                            syncFailed = syncFailed,
                            isPartner = record.origin.isNotBlank() && record.origin != myDeviceId,
                            onRetry = onRetry,
                            onClick = { onOpen(record.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 单张穿搭缩略图：4:5 竖版，对方的记录右上角挂一颗小爱心做来源标记 */
@Composable
private fun OutfitThumb(
    record: OutfitRecord,
    syncFailed: Boolean,
    isPartner: Boolean,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val thumbPress = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .solidCard(cornerRadius = 12.dp)
            .pressTint(thumbPress, cornerRadius = 12.dp)
            .clickable(
                interactionSource = thumbPress,
                indication = null,
                onClick = onClick
            )
    ) {
        val photoFile = PhotoStore.file(context, record.photoPath)
        if (photoFile.exists()) {
            PhotoImage(
                file = photoFile,
                contentDescription = "穿搭照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PhotoSyncPlaceholder(
                failed = syncFailed,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isPartner) {
            // 分享标记：❤ 图标 + 上传人昵称，昵称过长用省略号截断（限宽 68dp），不会把照片主体糊住
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevatedSurface)
                    .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = "来自对方",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = record.originName.ifBlank { "对方" },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 68.dp)
                )
            }
        }
        // 对方的评论（v2.8.0）：缩略图上只给一个评论气泡图标，不铺正文——
        // 4:5 的格子塞两行字会把照片主体糊住，进详情页看全文
        if (record.reply.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevatedSurface)
                    .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "评论",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = record.reply,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
        }
    }
}
