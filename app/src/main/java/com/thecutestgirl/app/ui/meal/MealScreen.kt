package com.thecutestgirl.app.ui.meal

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thecutestgirl.app.data.MEAL_NAMES
import com.thecutestgirl.app.data.MealRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.AddRecordSheet
import com.thecutestgirl.app.ui.components.DateNavRow
import com.thecutestgirl.app.ui.components.FloatingTopBar
import com.thecutestgirl.app.ui.components.SheetDateRow
import com.thecutestgirl.app.ui.components.SheetPhotoPicker
import com.thecutestgirl.app.ui.components.SheetTextField
import com.thecutestgirl.app.ui.components.photoFrame
import com.thecutestgirl.app.ui.components.photoShape
import com.thecutestgirl.app.ui.components.LocalAppBottomInset
import com.thecutestgirl.app.ui.components.PageHeader
import com.thecutestgirl.app.ui.components.PartnerMark
import com.thecutestgirl.app.ui.components.UiSpringDamping
import com.thecutestgirl.app.ui.components.UiSpringStiffness
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.PhotoSyncPlaceholder
import com.thecutestgirl.app.ui.components.PhotoViewerDialog
import com.thecutestgirl.app.ui.components.toCnShort
import com.thecutestgirl.app.ui.components.toWeekday
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.time.LocalDate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import com.thecutestgirl.app.ui.components.dashedCard

/** 餐次图标：日出 / 太阳 / 月牙（与整体线性图标体系统一） */
fun mealIcon(mealType: Int): ImageVector = when (mealType) {
    0 -> Icons.Outlined.WbTwilight
    1 -> Icons.Outlined.WbSunny
    else -> Icons.Outlined.DarkMode
}

/**
 * 双色餐次图标：线性描边 + 钴蓝（secondary）柔底。
 * 三模块色彩分工：穿搭=梅紫、饮食=钴蓝、点滴=余烬橙。
 */
@Composable
fun MealDuotoneIcon(mealType: Int, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * 1.6f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )
        Icon(
            imageVector = mealIcon(mealType),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * v2.5.3：顶栏高度改实测（原因同穿搭页）。原来按字号手算的 140dp，只要动一次
 * PageHeader 的内边距或副标题字号就会失效，表现为「早餐」被玻璃卡压住。
 */

/**
 * 饮食页（v2.4.5 顶栏折叠 + 玻璃化）：每个餐次下分开显示两个人的记录——
 * 「我的」随时可记录/编辑，「对方的」自动从云端同步过来、只查看。
 * 两个人各记各的，互不覆盖。
 */
@Composable
/**
 * @param initialDate 从通知点进来时要落到的日期；null = 用默认（今天）
 */
fun MealScreen(
    onOpenMeal: (LocalDate, Int, String?) -> Unit,
    onOpenSettings: () -> Unit,
    initialDate: LocalDate? = null
) {
    val vm: MealViewModel = viewModel()
    // 只在**首次**组合时按通知日期定位一次。
    // 用 rememberSaveable 记住「已经跳过了」：切走再切回来时 NavHost 会重建这个 composable，
    // 没有这个守卫的话每次回来都会被拽回通知里那天，用户手动翻的日期就白翻了。
    // 同 OutfitScreen：存日期而不是布尔，第二条通知才跳得动
    var jumpedDate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDate) {
        val target = initialDate?.toString() ?: return@LaunchedEffect
        if (jumpedDate != target) {
            vm.setDate(initialDate)
            jumpedDate = target
        }
    }
    val date by vm.date.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val myDeviceId = SettingsStore.deviceId(context)
    // 上次同步失败时，照片占位显示「同步不了」而不是「还在路上」
    val syncFailed = SettingsStore.lastSyncResult(context).startsWith("同步失败")
    val retrySync = {
        // 没填坚果云账号时不能承诺"已经开始同步"，否则点了什么也不会发生
        if (SettingsStore.isSyncConfigured(context)) {
            SyncManager.enqueueNow(context)
            Toast.makeText(context, "已开始同步，照片稍后出现", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "还没开启同步，去设置里填坚果云账号", Toast.LENGTH_SHORT).show()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<String?>(null) }
    if (showDatePicker) {
        AppDatePickerDialog(
            initial = date,
            onPick = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    viewerPhoto?.let { name ->
        PhotoViewerDialog(context = context, name = name, onDismiss = { viewerPhoto = null })
    }

    // 列表滚动状态：驱动顶栏折叠（只有"是否滚动过"这个布尔值参与重组）
    val listState = rememberLazyListState()
    val topBarScope = rememberCoroutineScope()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
        }
    }
    val pageHaze = remember { HazeState() }

    // 新增记录抽屉：统一走 AddRecordSheet，写入复用现有 MealEditViewModel
    val addVm: MealEditViewModel = viewModel()
    var addMealType by remember { mutableStateOf<Int?>(null) }

    // 顶栏实测高度 → 内容起点
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topArea = with(density) { headerHeightPx.toDp() } + 10.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // ---------- 内容区（模糊采样源，只服务底部液态玻璃导航栏）----------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(pageHaze)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    // 顶部让出实测顶栏高度，首餐卡片不会藏在顶栏底下
                    top = topArea,
                    // 底部留出玻璃导航栏的高度，内容能滚到玻璃底下而不被永久遮住
                    bottom = LocalAppBottomInset.current + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(3) { mealType ->
                    val mine = meals.firstOrNull { it.mealType == mealType && it.origin == myDeviceId }
                    val theirs = meals.filter { it.mealType == mealType && it.origin != myDeviceId }
                    MealSection(
                        mealType = mealType,
                        mine = mine,
                        theirs = theirs,
                        syncFailed = syncFailed,
                        onRetry = retrySync,
                        onEditMine = { onOpenMeal(date, mealType, mine?.id) },
                        onAddMine = { addMealType = mealType },
                        onViewPhoto = { name -> viewerPhoto = name }
                    )
                }
            }
        }

        // ---------- 悬浮顶栏：滚动后收起日期行与副标题 ----------
        FloatingTopBar(
            collapsed = scrolled,
            onOpenSettings = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height }
        ) {
            PageHeader(
                title = "饮食",
                subtitle = "好好吃饭，天天开心",
                icon = Icons.Outlined.Restaurant,
                collapsed = scrolled,
                // 收起态点一下回列表顶部；长按标题进设置
                onClick = if (scrolled) ({
                    topBarScope.launch { listState.animateScrollToItem(0) }
                }) else null,
                onLongPress = onOpenSettings
            )
            AnimatedVisibility(
                visible = !scrolled,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(
                    animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
                    shrinkTowards = Alignment.Top
                )
            ) {
                val isToday = date == LocalDate.now()
                DateNavRow(
                    label = "${date.toCnShort()} ${date.toWeekday()}",
                    // 今天时是静态胶囊徽标；不是今天时变成可点的「回到今天」胶囊按钮
                    subLabel = if (isToday) "今天" else "回到今天",
                    subLabelClickable = !isToday,
                    onSubLabelClick = { vm.setDate(LocalDate.now()) },
                    onLabelClick = { showDatePicker = true },
                    onPrev = vm::previousDay,
                    onNext = vm::nextDay,
                    prevContentDescription = "前一天",
                    nextContentDescription = "后一天"
                )
            }
        }
    }

    // ---------- 新增记录抽屉（半屏） ----------
    addMealType?.let { mealType ->
        LaunchedEffect(mealType) {
            // 打开时复位表单（同一个 ViewModel 复用，不新增数据逻辑）
            addVm.note = ""
            addVm.photoPath = null
            addVm.reply = ""
        }
        val dismissSheet = {
            addVm.photoPath?.let { PhotoStore.delete(context, it) }
            addVm.note = ""
            addVm.photoPath = null
            addMealType = null
        }
        AddRecordSheet(
            title = "记录${MEAL_NAMES[mealType]}",
            subtitle = "${date.toCnShort()} ${date.toWeekday()}",
            onDismiss = dismissSheet,
            primaryEnabled = addVm.canSave,
            onPrimary = { addVm.save(date, mealType) { addMealType = null } }
        ) {
            SheetDateRow(
                label = "日期",
                value = "${date.toCnShort()} ${date.toWeekday()}",
                onClick = {}
            )
            SheetPhotoPicker(
                photos = listOfNotNull(addVm.photoPath),
                maxPhotos = 1,
                onAdd = { addVm.photoPath = it.firstOrNull() },
                onRemove = {
                    addVm.photoPath = null
                    PhotoStore.delete(context, it)
                }
            )
            SheetTextField(
                value = addVm.note,
                onValueChange = { addVm.note = it },
                placeholder = "这一餐吃了什么，比如：楼下那家豆浆油条",
                singleLine = false,
                minLines = 3
            )
        }
    }
}

/** 一个餐次的完整区块：餐次标题 + 我的记录 + 对方的记录 */
@Composable
private fun MealSection(
    mealType: Int,
    mine: MealRecord?,
    theirs: List<MealRecord>,
    syncFailed: Boolean,
    onRetry: () -> Unit,
    onEditMine: () -> Unit,
    onAddMine: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    Column {
        // 餐次标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            MealDuotoneIcon(mealType = mealType, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(MEAL_NAMES[mealType], style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))

        // ---- 我的 ----
        if (mine != null) {
            MealContentCard(
                ownerLabel = "我的",
                record = mine,
                clickable = true,
                syncFailed = syncFailed,
                onRetry = onRetry,
                onClick = onEditMine,
                onViewPhoto = onViewPhoto
            )
        } else {
            // 未记录：虚线轻量卡（v2.5.3）。三态用三种重量级区分：
            //   未记录 = 虚线空槽（最轻，暗示「这里可以填」）
            //   已记录 = 实体白卡 + 照片（最重）
            //   对方   = 白卡但左侧一条强调色竖条（同重量级，但归属一眼可辨）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedCard(cornerRadius = 16.dp)
                    .clickable(onClick = onAddMine)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "记${MEAL_NAMES[mealType]}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "我的",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // ---- 对方的（同步来的，只查看）----
        theirs.forEach { record ->
            Spacer(Modifier.height(8.dp))
            MealContentCard(
                ownerLabel = null,
                record = record,
                clickable = false,
                syncFailed = syncFailed,
                onRetry = onRetry,
                onClick = {},
                onViewPhoto = onViewPhoto,
                partnerName = record.originName
            )
        }
    }
}

/**
 * 记录内容卡片：小标签（我的 / 对方昵称）+ 照片 + 文字。
 * 我的卡片整卡可点进编辑；对方的卡片点照片看大图。
 */
@Composable
private fun MealContentCard(
    ownerLabel: String?,
    record: MealRecord,
    clickable: Boolean,
    syncFailed: Boolean,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    onViewPhoto: (String) -> Unit,
    partnerName: String = ""
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp)
            // 对方的记录：卡片左缘压一条 4dp 钴蓝竖条（裁剪后只露内侧圆角内的部分），
            // 不额外占布局宽度，也不改变卡片圆角。
            .drawBehind {
                if (!clickable) {
                    drawRect(
                        color = accent,
                        topLeft = Offset(0f, 0f),
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
            }
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ownerLabel != null) {
                    Text(
                        ownerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PartnerMark(name = partnerName)
                }
            }
            if (record.photoPath != null) {
                val photoFile = PhotoStore.file(context, record.photoPath!!)
                // 图文容器规范 v4.0：图片内缩 8、比例 3:2、圆角 8、真图无框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 16.dp)
                ) {
                    if (photoFile.exists()) {
                        AsyncImage(
                            model = photoFile,
                            contentDescription = "餐食照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 2f)
                                .photoShape(8.dp)
                                .clickable { onViewPhoto(record.photoPath!!) }
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
            if (record.note.isNotBlank()) {
                Text(
                    record.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 对方的评论（v2.8.0）：与上传人的备注分属两个字段，视觉上也分开——
            // 缩进 + 左侧一条主色细线 + 小标签，一眼能看出这是「另一个人说的话」
            if (record.reply.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .height(IntrinsicSize.Min),
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
                            // 署名必须用 replyName：originName 是「上传人」，
                            // 拿它给评论署名，自己上传的记录收到评论就会显示成「我的评论」
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
        }
    }
}
