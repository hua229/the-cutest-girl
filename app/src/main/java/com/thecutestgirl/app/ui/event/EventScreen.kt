package com.thecutestgirl.app.ui.event

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.EVENT_TYPE_ANNIVERSARY
import com.thecutestgirl.app.data.EVENT_TYPE_MOMENT
import com.thecutestgirl.app.data.EventRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.parseRecordDate
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.AddRecordSheet
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.EmptyHint
import com.thecutestgirl.app.ui.components.FabNavSpacing
import com.thecutestgirl.app.ui.components.FloatingTopBar
import com.thecutestgirl.app.ui.components.SheetDateRow
import com.thecutestgirl.app.ui.components.SheetPhotoPicker
import com.thecutestgirl.app.ui.components.SheetSegmented
import com.thecutestgirl.app.ui.components.SheetTextField
import com.thecutestgirl.app.ui.components.SolidCircleButton
import com.thecutestgirl.app.ui.components.photoFrame
import com.thecutestgirl.app.ui.components.photoShape
import com.thecutestgirl.app.ui.components.LocalAppBottomInset
import com.thecutestgirl.app.ui.components.PageHeader
import com.thecutestgirl.app.ui.components.PartnerMark
import com.thecutestgirl.app.ui.components.PhotoImage
import com.thecutestgirl.app.ui.components.rememberToday
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.PhotoSyncPlaceholder
import com.thecutestgirl.app.ui.components.toCnFull
import com.thecutestgirl.app.ui.components.toCnShort
import com.thecutestgirl.app.ui.components.toWeekday
import com.thecutestgirl.app.ui.theme.BigNumeralStyle
import dev.chrisbanes.haze.haze
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

/** 展开态玻璃顶栏占的高度（面板竖向 6dp×2 + 标题行 56 + 副标题 16 ≈ 78，取 84 略留余量） */
// v2.5.3：顶栏高度改实测（原因同穿搭/饮食页），不再按字号手算

/**
 * 事件页（v2.4.5）：纪念日天数用字号与留白突出，日常点滴保持清晰的日期与内容关系。
 * 顶栏为悬浮玻璃面板，滚动后收起副标题，内容从它下面滚过。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenMenu: () -> Unit,
    initialDate: LocalDate? = null,
    initialRecordId: String? = null,
    onInitialTargetConsumed: () -> Unit = {}
) {
    val vm: EventViewModel = viewModel()
    val events by vm.events.collectAsStateWithLifecycle()
    // 上次同步失败时，照片占位显示「同步不了」而不是「还在路上」
    val context = LocalContext.current
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

    // 日期非法的记录直接挡在列表之外：它进不了日历，也不该让整页崩在 LocalDate.parse 上
    val anniversaries = remember(events) {
        events.filter { it.type == EVENT_TYPE_ANNIVERSARY && parseRecordDate(it.date) != null }
    }
    val moments = remember(events) {
        events.filter { it.type == EVENT_TYPE_MOMENT && parseRecordDate(it.date) != null }
            .sortedWith(
                compareByDescending<EventRecord> { it.date }
                    .thenByDescending { it.createdAt }
            )
    }

    // 列表滚动状态：驱动顶栏折叠（只有"是否滚动过"这个布尔值参与重组）
    val listState = rememberLazyListState()
    val topBarScope = rememberCoroutineScope()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
        }
    }
    // 顶栏 / FAB 共用的模糊采样源已移除：底栏的采样源在 AppRoot，页面内再挂一份是纯开销

    // 新增记录抽屉：统一 AddRecordSheet，写入复用现有 EventEditViewModel
    val addVm: EventEditViewModel = viewModel()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSheetDatePicker by remember { mutableStateOf(false) }

    if (showSheetDatePicker) {
        AppDatePickerDialog(
            initial = addVm.date,
            onPick = { addVm.date = it; showSheetDatePicker = false },
            onDismiss = { showSheetDatePicker = false }
        )
    }

    // 只记录展开态高度：折叠动画期间不再改 contentPadding
    var expandedHeaderHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topArea = with(density) { expandedHeaderHeightPx.toDp() } + 10.dp

    // 通知落点：滚动到对应点滴；找不到就退回该日期附近。消费一次后清空。
    var consumedTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialRecordId, initialDate, moments, anniversaries.size) {
        if (initialRecordId == null && initialDate == null) return@LaunchedEffect
        val key = "${initialRecordId.orEmpty()}|${initialDate?.toString().orEmpty()}"
        if (consumedTargetKey == key) return@LaunchedEffect
        // 数据还没从 Room 回来：先不消费，等 moments/anniversaries 到达后重跑
        if (moments.isEmpty() && anniversaries.isEmpty()) return@LaunchedEffect
        val byId = initialRecordId?.let { id -> moments.indexOfFirst { it.id == id } } ?: -1
        val byDate = if (byId < 0 && initialDate != null) {
            moments.indexOfFirst { record ->
                runCatching { LocalDate.parse(record.date) }
                    .getOrNull()
                    ?.let { !it.isAfter(initialDate) } == true
            }
        } else -1
        val indexInMoments = if (byId >= 0) byId else byDate
        if (indexInMoments >= 0) {
            val momentsStart = (if (anniversaries.isNotEmpty()) 2 else 0) + 1
            listState.scrollToItem(momentsStart + indexInMoments)
        } else if (initialRecordId != null && anniversaries.any { it.id == initialRecordId }) {
            // 目标是纪念日：滚到纪念日分组
            listState.scrollToItem(0)
        }
        consumedTargetKey = key
        onInitialTargetConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        // 顶部让出实测顶栏高度，首张卡片不会被它挡住
                        top = topArea,
                        // 底部留出玻璃导航栏的高度，最后一张卡片能完整滚出玻璃区域
                        bottom = LocalAppBottomInset.current + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (anniversaries.isNotEmpty()) {
                        item {
                            SectionLabel("纪念日")
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(anniversaries, key = { it.id }) { record ->
                                    AnniversaryCard(record, onClick = { onOpen(record.id) })
                                }
                            }
                        }
                    }

                    item {
                        SectionLabel("日常点滴")
                    }

                    if (moments.isEmpty()) {
                        item {
                            EmptyHint(
                                text = "",
                                title = "还没有点滴记录",
                                hint = "点右下角「记一笔」写下今天",
                                icon = Icons.Outlined.FavoriteBorder,
                                accent = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        items(moments, key = { it.id }) { record ->
                            MomentCard(
                                record,
                                syncFailed = syncFailed,
                                onRetry = retrySync,
                                onClick = { onOpen(record.id) },
                                // 同步把对方的记录合并进来时，卡片淡入 + 让位，而不是硬跳
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            // ---------- 悬浮顶栏（事件是时间线，只有标题行） ----------
            FloatingTopBar(
                collapsed = scrolled,
                onOpenMenu = onOpenMenu,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { if (!scrolled) expandedHeaderHeightPx = it.height }
            ) {
                PageHeader(
                    title = "点滴",
                    subtitle = "值得记住的每一件小事",
                    icon = Icons.Outlined.FavoriteBorder,
                    collapsed = scrolled,
                    // 收起态点一下回列表顶部；设置入口统一在右侧菜单里
                    onClick = if (scrolled) ({
                        topBarScope.launch { listState.animateScrollToItem(0) }
                    }) else null
                )
            }

            // 实心圆形 + 号按钮：与穿搭页完全一致（样式/尺寸/热区）；
            // 底部在导航栏占位之上再抬 16dp，避免紧贴导航栏误触
            SolidCircleButton(
                onClick = { showAddSheet = true },
                contentDescription = "记一笔",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = LocalAppBottomInset.current + FabNavSpacing
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "记一笔",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ---------- 新增记录抽屉（半屏） ----------
    if (showAddSheet) {
        LaunchedEffect(Unit) {
            // 打开时复位表单（复用 EventEditViewModel，不新增数据逻辑）
            addVm.type = EVENT_TYPE_MOMENT
            addVm.title = ""
            addVm.description = ""
            addVm.photoPath = null
            addVm.date = LocalDate.now()
            addVm.reply = ""
        }
        val dismissSheet = {
            addVm.photoPath?.let { PhotoStore.delete(context, it) }
            addVm.photoPath = null
            addVm.title = ""
            addVm.description = ""
            showAddSheet = false
        }
        val isMoment = addVm.type == EVENT_TYPE_MOMENT
        AddRecordSheet(
            title = if (isMoment) "记一条点滴" else "记一个纪念日",
            subtitle = if (isMoment) "日常点滴 · 可配一张照片" else "纪念日 · 自动计算天数",
            onDismiss = dismissSheet,
            primaryEnabled = addVm.canSave,
            hasUnsavedChanges = addVm.title.isNotBlank() ||
                addVm.description.isNotBlank() || addVm.photoPath != null,
            onPrimary = { addVm.save { showAddSheet = false } }
        ) {
            SheetSegmented(
                options = listOf("日常点滴", "纪念日"),
                selectedIndex = if (isMoment) 0 else 1,
                onSelect = {
                    addVm.type = if (it == 0) EVENT_TYPE_MOMENT else EVENT_TYPE_ANNIVERSARY
                }
            )
            SheetTextField(
                value = addVm.title,
                onValueChange = { addVm.title = it },
                placeholder = if (isMoment) "比如：一起去看了日落" else "比如：在一起"
            )
            SheetDateRow(
                label = if (isMoment) "发生日期" else "开始日期",
                value = "${addVm.date.toCnFull()} ${addVm.date.toWeekday()}",
                onClick = { showSheetDatePicker = true }
            )
            if (isMoment) {
                SheetPhotoPicker(
                    photos = listOfNotNull(addVm.photoPath),
                    maxPhotos = 1,
                    onAdd = { addVm.photoPath = it.firstOrNull() },
                    onRemove = {
                        addVm.photoPath = null
                        PhotoStore.delete(context, it)
                    }
                )
            }
            SheetTextField(
                value = addVm.description,
                onValueChange = { addVm.description = it },
                placeholder = "写点什么（可选）",
                singleLine = false,
                minLines = 3
            )
        }
    }
}

/** 分组标题：左侧一条 4dp 余烬橙短竖条 + 小号文字，给分组一个视觉锚点 */
@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 纪念日卡片：左边一个大数字（唯一重字重），右边名称 + 起始日 + 描述。
 * v3.0：去掉左缘的强调色渐变 wash——数字本身就是这张卡的亮色焦点，
 * 卡面回归纯白，颜色只住在数字里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnniversaryCard(record: EventRecord, onClick: () -> Unit) {
    val today = rememberToday()
    val start = remember(record.date) { parseRecordDate(record.date) ?: today }
    val isFuture = start.isAfter(today)
    val days = remember(record.date) {
        if (isFuture) {
            ChronoUnit.DAYS.between(today, start).toInt()
        } else {
            ChronoUnit.DAYS.between(start, today).toInt() + 1
        }
    }
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .width(288.dp)
            .heightIn(min = 112.dp)
            .solidCard(cornerRadius = 16.dp)
            // v4.0：保留用户确认过的左缘横向渐变光（余烬橙 12% → 透明），
            // 数字坐在光里，右侧文字留在干净的玻璃面上。
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                        endX = 176.dp.toPx()
                    )
                )
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：天数（全页唯一的等宽大数字，字重 300）
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    if (isFuture) "还有" else "第",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$days",
                        style = BigNumeralStyle,
                        color = accent
                    )
                    Text(
                        " 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            // 右：名称 + 起始日 + 描述
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isFuture) "${start.toCnShort()} 见" else "从 ${start.toCnShort()} 开始",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.description.isNotBlank()) {
                    Text(
                        record.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 纪念日卡窄，评论只用一行主色小字，不铺竖线
                if (record.reply.isNotBlank()) {
                    Text(
                        record.reply,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 日常点滴卡片：照片 + 标题 + 描述 + 日期，层级清晰 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MomentCard(
    record: EventRecord,
    syncFailed: Boolean,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val day = remember(record.date) { parseRecordDate(record.date) ?: LocalDate.now() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 18.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            if (record.photoPath != null) {
                val photoFile = PhotoStore.file(context, record.photoPath!!)
                // 图文容器规范 v4.0：图片内缩 8、比例 3:2、圆角 8、真图无框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 8.dp, bottom = 16.dp)
                ) {
                    if (photoFile.exists()) {
                        PhotoImage(
                            file = photoFile,
                            contentDescription = "点滴照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 2f)
                                .photoShape(8.dp)
                        )
                    } else {
                        PhotoSyncPlaceholder(
                            failed = syncFailed,
                            onRetry = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 2f)
                                .photoFrame(8.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    // 有照片时图文间距由图片块的 bottom 16 承担；没有照片时自己补顶部留白
                    top = if (record.photoPath != null) 8.dp else 16.dp,
                    bottom = 16.dp
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (record.origin.isNotBlank() &&
                        record.origin != com.thecutestgirl.app.data.SettingsStore.deviceId(context)
                    ) {
                        Spacer(Modifier.width(8.dp))
                        PartnerMark(name = record.originName)
                    }
                }
                if (record.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        record.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 对方的评论（v2.8.0）：与上传人的描述分属两个字段，视觉上也分开——
                // 缩进 + 左侧一条主色细线 + 小标签，一眼看出是「另一个人说的话」
                if (record.reply.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                // 署名用 replyName（见 MealScreen 里的说明）
                                if (record.replyName.isNotBlank()) "${record.replyName} 的评论" else "ta 的评论",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                record.reply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${day.toCnFull()} ${day.toWeekday()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
