package com.thecutestgirl.app.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.ui.theme.DateNumberStyle
import com.thecutestgirl.app.ui.theme.EmptyTitleStyle
import com.thecutestgirl.app.ui.theme.SuccessGreen
import com.thecutestgirl.app.ui.theme.TextMuted
import com.thecutestgirl.app.ui.theme.WarningAmber
import dev.chrisbanes.haze.HazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.thecutestgirl.app.R

// ---------- 日期格式化 ----------
private val CN_FULL = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val CN_SHORT = DateTimeFormatter.ofPattern("M月d日")

fun LocalDate.toCnFull(): String = format(CN_FULL)
fun LocalDate.toCnShort(): String = format(CN_SHORT)
fun LocalDate.toWeekday(): String = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)

/**
 * 计算新的照片列表，并返回「需要删除的孤儿文件」。
 *
 * - 单图（maxPhotos == 1）：新选的图替换旧图；旧图进入孤儿列表（是否真删由调用方决定，
 *   编辑已有记录时旧图仍被数据库引用，要等保存成功后才由 ViewModel 删除）。
 * - 多图：保留前 maxPhotos 张，超出部分进入孤儿列表（这些是刚从相册复制进来的多余文件）。
 */
internal fun nextPhotoList(
    current: List<String>,
    added: List<String>,
    maxPhotos: Int
): Pair<List<String>, List<String>> {
    require(maxPhotos > 0) { "maxPhotos must be > 0" }
    if (added.isEmpty()) return current to emptyList()
    return if (maxPhotos == 1) {
        val result = added.takeLast(1)
        val replaced = current.filter { it !in result }
        result to replaced
    } else {
        val merged = current + added
        merged.take(maxPhotos) to merged.drop(maxPhotos)
    }
}

/** 统一顶栏：quietTopAppBar + IconTitle；windowInsets=0（根 Scaffold 已处理状态栏）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = Modifier.quietTopAppBar(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
            IconTitle(
                icon = icon,
                text = title,
                style = MaterialTheme.typography.titleMedium,
                iconSize = BarIconSize
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = actions
    )
}

/** 破坏性操作行：48dp、错误色文字、无系统 ripple（只在允许删除时显示）。 */
@Composable
fun DestructiveAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.error
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * 「今天」：跨午夜自动刷新，回到前台也会刷新。
 * 不要再用 `remember { LocalDate.now() }`（App 挂一夜后会停在昨天）。
 */
@Composable
fun rememberToday(): LocalDate {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(today) {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val waitMs = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)
        delay(waitMs)
        today = LocalDate.now()
    }
    return today
}

// ---------- 空状态提示 ----------
/**
 * 空状态（v2.5.3 重做）：默认用「模块色柔光 + 毛毡玩偶」当插画，而不是一枚线性图标。
 *
 * 为什么用启动图标那张图：APP 的图标本身就是毛毡玩偶合照（tools/make_launcher_icon.py 产出），
 * 空态是用户停留最久、屏幕最空的一屏，正好把它放大复用——既统一了视觉资产，又不必额外画插画。
 * 圆角方形 + 一圈柔光，看起来像「一张贴纸」，比纯线性图标有温度。
 *
 * @param accent 模块强调色，用于柔光与图标 tint；默认取主色
 * @param showFeltIllustration 传 false 时退回「圆底线性图标」的轻量形态
 */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.FavoriteBorder,
    title: String? = null,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    showFeltIllustration: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    // 兼容旧调用：只传一段文字时按换行符切成主文案 + 引导语，主文案吃重字重
    val (resolvedTitle, resolvedHint) = when {
        title != null -> title to hint
        else -> text.split('\n', limit = 2).let {
            it.first() to (hint ?: it.getOrNull(1))
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showFeltIllustration) {
            // 毛毡玩偶贴纸：外圈柔光光环 + 圆角方形裁切（尺寸保持 8dp 网格）
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            resolvedTitle,
            color = scheme.onBackground,
            style = EmptyTitleStyle,
            textAlign = TextAlign.Center
        )
        if (!resolvedHint.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                resolvedHint,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

// ---------- 「来自对方」标记：描边心形 + 昵称，弱化为辅助信息 ----------
@Composable
fun PartnerMark(name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name.ifBlank { "来自对方" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- 标题图标：全 APP 统一的尺寸与间距 ----------
/** 主标题（headlineMedium 30sp）前的图标 24dp */
val PageIconSize: Dp = 24.dp
/** TopAppBar 标题（titleMedium 16sp）前的图标 20dp */
val BarIconSize: Dp = 20.dp
/** 图标与标题文字的固定间距（8dp 网格） */
val PageIconGap: Dp = 8.dp

/**
 * 标题行：线图标 + 标题文字（v3.0 由 EmojiTitle 更换而来）。
 * 三个模块的标题图标与底部导航栏同一套（Checkroom / Restaurant / FavoriteBorder），
 * 全 APP 只有一套图标语言。
 */
@Composable
fun IconTitle(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    style: ComposeTextStyle = MaterialTheme.typography.headlineMedium,
    iconSize: Dp = PageIconSize,
    iconGap: Dp = PageIconGap,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    // 收起态传 false：不参与断行计算，宽度不够直接裁切
    softWrap: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(iconGap))
        Text(
            text,
            style = style,
            color = Color.Unspecified,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 页面头部：三页完全一致。这行不放任何会条件出现/消失的控件。
 *
 * v3.0：自身不画背景（由外层 [FloatingTopBar] 的实色卡面承载），避免两层底色叠加；
 * 标题图标由 emoji 换成与底部导航同套的线性图标。
 * 设置齿轮移出标题行（由 [FloatingTopBar] 的独立圆形按钮承载，
 * 避免与日期栏的 ◀ ▶ 挤在一起误触）。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    collapsed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                // 收起成胶囊时右侧留白与左侧对齐，标题不会挤在圆角上
                end = if (collapsed) 16.dp else 8.dp,
                top = 8.dp,
                bottom = 8.dp
            )
            // v2.6：长按标题是设置的「隐藏入口」——饮食/事件页去掉齿轮后，
            // 仍能就地进设置，不必切回穿搭页
            // 点击 = 收起态胶囊回列表顶部；长按 = 进设置（饮食/事件页唯一的设置入口）。
            // 两者必须用同一个手势识别器，拆成两个会让子节点先消费、外层收不到。
            .then(
                if (onClick != null || onLongPress != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongPress?.invoke() }
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (icon == null) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
            } else {
                IconTitle(
                    icon = icon,
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    softWrap = !collapsed
                )
            }
            // 滚动后收起副标题：高度与透明度一起动画，由 AnimatedVisibility 自己算出高度差
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically(
                    animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 日期/月份切换条：◀ [label] ▶，中间区域可点（打开日历或月份弹窗）。
 * [subLabel] 是 [label] 下方的小字（「回到今天」「回到本月」可点 / 「今天」静态信息）；
 * 可点时是 [SoftCapsuleButton]，小尺寸 + 描边 + 按压反馈，不抢主视觉。
 * [subLabelClickable] 为 false 时它是静态徽标（同样做成胶囊，只是不可点），点它落到 [onLabelClick]。
 */
@Composable
fun DateNavRow(
    label: String,
    onLabelClick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevContentDescription: String,
    nextContentDescription: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    subLabelClickable: Boolean = false,
    onSubLabelClick: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v4.0：可点区 40→48dp（8dp 网格）
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = prevContentDescription, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLabelClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = DateNumberStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subLabel.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                if (subLabelClickable) {
                    SoftCapsuleButton(
                        onClick = onSubLabelClick,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            subLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // 静态徽标：v3.0 从带底胶囊退成一行安静的主色小字——
                    // 「今天」只是信息不是操作，不该占一个按钮的视觉重量
                    Text(
                        subLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = nextContentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 横向日期条：一个月里的每一天一个 chip，可横向滚动切换、点击选中。
 * - 选中：玫瑰色底 + 主色文字
 * - 今天：未选中时给一圈主色描边
 * - 有记录：数字下方一个主色小圆点（一眼看出哪天记过）
 * 选中项变化时自动把它滚到可视区。
 */
@Composable
fun DayStrip(
    month: YearMonth,
    daysWithPhotos: Set<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val days = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
    val listState = rememberLazyListState()

    LaunchedEffect(month, selected) {
        val index = days.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val isToday = day == today
            val hasPhoto = day in daysWithPhotos
            val textColor = when {
                isSelected -> scheme.primary
                isToday -> scheme.primary
                else -> scheme.onSurface
            }
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) scheme.primary.copy(alpha = 0.18f) else Color.Transparent
                    )
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = scheme.primary.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else Modifier
                    )
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(day) },
                        role = Role.Tab
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${day.dayOfMonth}",
                    style = DateNumberStyle,
                    color = textColor
                )
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasPhoto) scheme.primary.copy(alpha = 0.7f) else Color.Transparent
                        )
                )
            }
        }
    }
}

// ---------- 月份选择弹窗：年份切换 + 3×4 月份网格 ----------
@Composable
fun AppMonthPickerDialog(
    initial: YearMonth,
    minYear: Int,
    maxYear: Int,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.monthValue) }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (year > minYear) year-- },
                        enabled = year > minYear
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一年")
                    }
                    Text(
                        "${year}年",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (year < maxYear) year++ },
                        enabled = year < maxYear
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下一年")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (col in 0..2) {
                                val m = row * 3 + col + 1
                                val selected = m == month
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (selected) {
                                                Modifier.background(scheme.primary.copy(alpha = 0.18f))
                                            } else Modifier
                                        )
                                        .clickable { month = m }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${m}月",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) scheme.primary else scheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(YearMonth.of(year, month)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------- 日历选日期弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state)
    }
}

// ---------- 删除确认弹窗 ----------
@Composable
fun ConfirmDeleteDialog(visible: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除", style = MaterialTheme.typography.titleMedium) },
        text = { Text("删除后就找不回来了，确定要删除吗？", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 同步状态（规范 v4.0）：空闲 / 同步中 / 成功 / 冲突 / 失败。
 * UI 只展示状态，不重写同步逻辑。
 */
enum class SyncStatusKind { Idle, Syncing, Success, Conflict, Failure }

/**
 * 同步状态条：图标 + 场景化文案 + 可选原因 + 失败重试。
 * - 空闲：灰色小图标「还没同步」
 * - 同步中：环形进度 +「同步中…」（调用方同时禁用触发按钮）
 * - 成功：绿色对勾 +「刚刚同步」+ 上次时间
 * - 冲突：橙色警告 +「N条冲突，点击合并」
 * - 失败：红色 +「同步失败」+ 简短原因 + 重试按钮
 */
@Composable
fun SyncStatusBar(
    kind: SyncStatusKind,
    subText: String = "",
    reason: String? = null,
    conflictCount: Int = 0,
    onConflictClick: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val title = when (kind) {
        SyncStatusKind.Idle -> "还没同步"
        SyncStatusKind.Syncing -> "同步中…"
        SyncStatusKind.Success -> "刚刚同步"
        SyncStatusKind.Conflict -> "${conflictCount}条冲突，点击合并"
        SyncStatusKind.Failure -> "同步失败"
    }
    val iconTint = when (kind) {
        SyncStatusKind.Success -> SuccessGreen
        SyncStatusKind.Failure -> scheme.error
        SyncStatusKind.Conflict -> WarningAmber
        SyncStatusKind.Idle -> TextMuted
        SyncStatusKind.Syncing -> scheme.secondary
    }
    val titleColor = when (kind) {
        SyncStatusKind.Failure -> scheme.error
        SyncStatusKind.Conflict -> WarningAmber
        else -> scheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp)
            .then(
                if (kind == SyncStatusKind.Conflict) Modifier.clickable(onClick = onConflictClick)
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kind == SyncStatusKind.Syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = scheme.secondary
            )
        } else {
            Icon(
                imageVector = when (kind) {
                    SyncStatusKind.Success -> Icons.Outlined.CheckCircleOutline
                    SyncStatusKind.Failure -> Icons.Outlined.CloudOff
                    SyncStatusKind.Conflict -> Icons.Outlined.Warning
                    else -> Icons.Outlined.CloudQueue
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = reason?.takeIf { it.isNotBlank() } ?: subText.takeIf { it.isNotBlank() }
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (kind == SyncStatusKind.Failure && onRetry != null) {
            Spacer(Modifier.width(8.dp))
            SoftCapsuleButton(onClick = onRetry) {
                Text(
                    "重试",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.error
                )
            }
        }
    }
}

/**
 * 照片占位：照片文件还没同步到本地时显示，只有「还在路上」和「同步不了」两态。
 * 高度/比例由调用方通过 [modifier] 决定（穿搭 4:5、饮食 90dp、事件 110dp），组件内部不写死。
 * 整块可点击 → 触发 [onRetry]（调用方自己去 enqueueNow + Toast）。
 */
@Composable
fun PhotoSyncPlaceholder(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(scheme.surfaceVariant)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (failed) Icons.Outlined.CloudOff else Icons.Outlined.CloudSync,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (failed) "现在同步不了" else "照片还在路上…",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (failed) "点一下重新同步" else "同步完成后会自动出现",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

// ---------- 全屏看大图（支持左右滑 / 双指缩放 / 双击放大，单击关闭）----------
@Composable
fun PhotoViewerDialog(
    context: Context,
    photos: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val safeIndex = initialIndex.coerceIn(0, photos.lastIndex)
        val pagerState = rememberPagerState(
            initialPage = safeIndex,
            pageCount = { photos.size }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomablePhoto(
                    file = PhotoStore.file(context, photos[page]),
                    onTap = onDismiss
                )
            }
            if (photos.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** 兼容单图调用 */
@Composable
fun PhotoViewerDialog(context: Context, name: String, onDismiss: () -> Unit) {
    PhotoViewerDialog(context = context, photos = listOf(name), initialIndex = 0, onDismiss = onDismiss)
}

@Composable
private fun ZoomablePhoto(file: File, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/** 照片右上角的小圆底删除键：视觉 32dp，热区 48dp（Android 最小触摸目标）。 */
@Composable
private fun PhotoRemoveButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.88f))
                .border(1.dp, scheme.onSurface.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "移除照片",
                tint = scheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 图文一体卡片（v2.4.4 建立，v2.4.5 玻璃化；v2.8.0 拆出「备注 / 评论」双区）。
 *
 * 层次做法：
 * 1. 卡片本身 = 玻璃面板（[solidCard]：深色底 + 7% 白玻璃 + 1dp 半透边框 + 内发光）
 * 2. 照片 8dp 内缩 + 8dp 圆角（16−8 的嵌套关系），与卡片留白基线一致
 * 3. 各区之间只放一条 1dp 发丝分割线，连而不糊
 * 4. 输入区去掉外框与底色，由卡片充当容器
 *
 * ## 备注区 / 评论区分离
 * 一条记录上有两个「写字的位置」，各有唯一写者：
 * - **备注（note）**：只有上传人能写，对方只读
 * - **评论（reply）**：只有对方能写，上传人只读
 *
 * 两个人各写各的区，谁也不能改对方那句。[SyncEngine] 里按字段时间戳做合并
 * （noteUpdatedAt / replyUpdatedAt），两条线互不覆盖。
 *
 * @param noteEditable 我是不是上传人；false 时备注只读
 * @param noteAuthorName 备注区标签（对方看时是上传人昵称）；空 = 我就是上传人，不贴标签
 * @param onReplyChange 传 null 表示这张卡不显示评论区（列表页缩略场景）
 * @param replyEditable 我是不是对方（评论写者）；false 时评论区只读
 * @param replyAuthorName 评论作者昵称；空表示还没有评论
 * @param photoEditable false 时照片区只保留查看，不出现换图/拍照按钮
 */
@Composable
fun PhotoNoteCard(
    photos: List<String>,
    onPhotosChanged: (List<String>) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    notePlaceholder: String,
    modifier: Modifier = Modifier,
    allowRemove: Boolean = true,
    maxPhotos: Int = 1,
    photoRatio: Float = 4f / 5f,
    cardCorner: Dp = 16.dp,
    /** 我是不是上传人；false 时备注只读 */
    noteEditable: Boolean = true,
    /** 备注区标签；空 = 我就是上传人，不贴标签 */
    noteAuthorName: String = "",
    /** 对方写的评论 */
    reply: String = "",
    /** null = 不显示评论区（列表缩略场景） */
    onReplyChange: ((String) -> Unit)? = null,
    /** 我是不是对方；false 时评论区只读 */
    replyEditable: Boolean = false,
    /** 评论作者昵称；空表示还没有评论 */
    replyAuthorName: String = "",
    replyPlaceholder: String = "回复一句…",
    /** false 时照片区只保留查看 */
    photoEditable: Boolean = true
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var cameraTarget by remember { mutableStateOf<File?>(null) }
    var viewerPhoto by remember { mutableStateOf<String?>(null) }
    // 结果回调是在外部线程回来的，用 rememberUpdatedState 保证追加的是最新的照片列表
    val currentPhotos by rememberUpdatedState(photos)

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraTarget
        if (ok && file != null && file.exists() && file.length() > 0) {
            // 拍出来的原图很大，压缩后再使用
            scope.launch {
                withContext(Dispatchers.IO) { PhotoStore.compressInPlace(file) }
                val (next, orphans) = nextPhotoList(currentPhotos, listOf(file.name), maxPhotos)
                onPhotosChanged(next)
                // 只清理本次新增里被丢弃的；被替换的旧图仍被数据库引用，
                // 保存成功后才由 ViewModel 删除（否则取消编辑会把原记录弄坏）
                withContext(Dispatchers.IO) {
                    orphans.filter { it == file.name }.forEach { PhotoStore.delete(context, it) }
                }
            }
        } else {
            file?.delete()
        }
    }
    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                PhotoStore.saveFromUri(context, uri)?.let { name ->
                    val (next, orphans) = nextPhotoList(currentPhotos, listOf(name), maxPhotos)
                    onPhotosChanged(next)
                    withContext(Dispatchers.IO) {
                        orphans.filter { it == name }.forEach { PhotoStore.delete(context, it) }
                    }
                }
            }
        }
    }
    // 注意：PickMultipleVisualMedia 的构造里有 require(maxItems > 1)，
    // 单图场景（maxPhotos == 1）传 1 会在组合阶段直接抛 IllegalArgumentException。
    // 这里永远构造一个合法的多选合约，实际是否走它由 launchGallery 决定。
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxPhotos.coerceAtLeast(2))
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                // 先按剩余名额截断，再复制进私有目录，避免「复制了又丢弃」产生孤儿文件
                val remaining = (maxPhotos - currentPhotos.size).coerceAtLeast(1)
                val saved = uris.take(remaining).mapNotNull { PhotoStore.saveFromUri(context, it) }
                if (saved.isNotEmpty()) {
                    val (next, orphans) = nextPhotoList(currentPhotos, saved, maxPhotos)
                    onPhotosChanged(next)
                    withContext(Dispatchers.IO) { orphans.forEach { PhotoStore.delete(context, it) } }
                }
            }
        }
    }
    val launchCamera = {
        val (file, uri) = PhotoStore.newCameraTarget(context)
        cameraTarget = file
        takePicture.launch(uri)
    }
    val launchGallery: () -> Unit = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (maxPhotos > 1) {
            // 系统照片选择器可能对可选数量另有限制（createIntent 里还有一条 require），
            // 越界时退回单选，避免直接崩在这句话上
            val launched = runCatching { pickMultiple.launch(request) }.isSuccess
            if (!launched) pickSingle.launch(request)
        } else {
            pickSingle.launch(request)
        }
    }

    if (viewerPhoto != null) {
        val index = photos.indexOf(viewerPhoto).coerceAtLeast(0)
        PhotoViewerDialog(
            context = context,
            photos = photos,
            initialIndex = index,
            onDismiss = { viewerPhoto = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = cardCorner, elevation = 6.dp)
    ) {
        // ---------- 照片区 ----------
        when {
            photos.isEmpty() && !photoEditable -> Unit

            photos.isEmpty() -> {
                Row(
                    // v4.0：卡片内边距统一 16dp 基线
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftCapsuleButton(
                        onClick = launchGallery,
                        modifier = Modifier.weight(1f),
                        cornerRadius = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary, contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (maxPhotos > 1) "选照片" else "从相册选择",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SoftCapsuleButton(
                        onClick = launchCamera,
                        modifier = Modifier.weight(1f),
                        cornerRadius = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera, contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "拍一张",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            photos.size == 1 -> {
                // v4.0：单图 16dp 缩进 + 8dp 圆角（与卡片 16 形成嵌套关系）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .aspectRatio(photoRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewerPhoto = photos.first() }
                ) {
                    AsyncImage(
                        model = PhotoStore.file(context, photos.first()),
                        contentDescription = "照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (allowRemove && photoEditable) {
                        PhotoRemoveButton(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            onClick = { onPhotosChanged(emptyList()) }
                        )
                    }
                }
            }

            else -> {
                // 多图：两列网格，每格 4:5，整体与卡片留 16dp 内边距
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { name ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(photoRatio)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewerPhoto = name }
                                ) {
                                    AsyncImage(
                                        model = PhotoStore.file(context, name),
                                        contentDescription = "照片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (allowRemove && photoEditable) {
                                        PhotoRemoveButton(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                            onClick = { onPhotosChanged(photos - name) }
                                        )
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ---------- 照片操作行（只在有照片且可编辑时出现，卡片轮廓不变） ----------
        if (photos.isNotEmpty() && photoEditable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoftCapsuleButton(
                    onClick = launchGallery,
                    modifier = Modifier.weight(1f),
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary, contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (maxPhotos > 1) "再加" else "换一张",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SoftCapsuleButton(
                    onClick = launchCamera,
                    modifier = Modifier.weight(1f),
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera, contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "拍一张",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (allowRemove && photos.size == 1) {
                    SoftCapsuleButton(
                        onClick = { onPhotosChanged(emptyList()) },
                        modifier = Modifier.weight(1f),
                        cornerRadius = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            "移除",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ---------- 备注区（上传人写）+ 评论区（对方写） ----------
        // 两个区各有一条发丝线，但只在「这一区真的会有内容」时才画：
        // 空备注 + 空评论却画两条线，等于把卡片切成三块空白，纯噪声。
        val hasNoteBlock = noteEditable || note.isNotEmpty()
        val hasReplyBlock = onReplyChange != null && (replyEditable || reply.isNotEmpty())

        if (hasNoteBlock) {
            if (note.isNotEmpty()) {
                Hairline(scheme)
            }
            TextNoteBlock(
                label = noteAuthorName.takeIf { it.isNotBlank() }?.let { "$it 的备注" },
                value = note,
                onValueChange = onNoteChange,
                placeholder = notePlaceholder,
                editable = noteEditable,
                accent = scheme.onSurfaceVariant
            )
        }

        if (hasReplyBlock) {
            Hairline(scheme)
            TextNoteBlock(
                label = replyAuthorName.takeIf { it.isNotBlank() }?.let { "$it 的评论" }
                    ?: if (replyEditable) "写点评论" else "ta 的评论",
                value = reply,
                onValueChange = { onReplyChange?.invoke(it) },
                placeholder = replyPlaceholder,
                editable = replyEditable,
                accent = scheme.primary,
                leadingIcon = Icons.Outlined.ChatBubbleOutline
            )
        }
    }
}

/** 卡片内的 1dp 发丝分割线，左右 16dp 与内容基线对齐 */
@Composable
private fun Hairline(scheme: ColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

/**
 * 单个文字区块：可编辑时是「折叠成一行 → 点开铺三行」的输入框，
 * 只读时是一段普通文字（带可选小标签）。
 *
 * 折叠逻辑的坑（v2.6.0 真实闪退，务必保留注释）：
 * 不能在收起态的点击回调里直接 `requestFocus()` —— 那一刻 TextField 还没上树，
 * FocusRequester 没有目标节点，会抛 IllegalStateException: FocusRequester is not initialized。
 * 必须「记下待聚焦意图 → 等 TextField 组合完成后再聚焦」。
 * 另外 `onFocusChanged` 在 TextField 首次上树时会先回调一次「未聚焦」，
 * 不加 wasFocused 守卫的话点一下会瞬间弹回去，看起来像没反应。
 */
@Composable
private fun TextNoteBlock(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    editable: Boolean,
    accent: Color,
    leadingIcon: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(value.isNotEmpty()) }
    var pendingFocus by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value.isNotEmpty()) {
        if (value.isNotEmpty()) expanded = true
    }
    // "写点备注（可选），比如：..." -> 收起态只显示逗号前半句，示例留给展开态
    val shortHint = remember(placeholder) {
        placeholder.substringBefore('，').substringBefore(',')
    }

    Column(modifier = Modifier.animateContentSize()) {
        // ---------- 只读：一段文字，不上输入框 ----------
        if (!editable) {
            if (value.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (label != null) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface
                    )
                }
            }
            return@Column
        }

        // ---------- 可编辑：折叠 / 展开 ----------
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable {
                        expanded = true
                        pendingFocus = true
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon ?: Icons.Outlined.Add,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            LaunchedEffect(pendingFocus) {
                if (pendingFocus) {
                    runCatching { focusRequester.requestFocus() }
                    pendingFocus = false
                }
            }
            Column {
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                }
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            // 失焦且没写东西就收回去，回到 41dp 的紧凑态
                            if (state.isFocused) {
                                wasFocused = true
                            } else if (wasFocused && value.isEmpty()) {
                                expanded = false
                                wasFocused = false
                            }
                        },
                    minLines = 3,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        cursorColor = scheme.primary
                    )
                )
            }
        }
    }
}

/**
 * 只读文字块（v2.8.0）：显示对方写的备注/评论，不带任何输入控件。
 * 用在「纪念日」这种没有 [PhotoNoteCard] 的编辑页上。
 */
@Composable
fun ReadOnlyNote(
    label: String?,
    value: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp, elevation = 4.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface
        )
    }
}

/**
 * 评论区（v2.8.0）：一条记录下「对方」写的话。
 *
 * 与上传人的备注区是**两个不同的字段**（[com.thecutestgirl.app.data.MealRecord.reply]），
 * 各自带时间戳，同步时按字段合并，两人同时写字不会互相覆盖。
 *
 * @param editable 我是不是「对方」。上传人视角传 false，只看不写。
 */
@Composable
fun CommentField(
    value: String,
    onValueChange: (String) -> Unit,
    editable: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "说点什么…"
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp, elevation = 4.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            if (editable) "写点评论" else "ta 的评论",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (editable) {
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                value.ifBlank { "还没有评论" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) scheme.onSurfaceVariant.copy(alpha = 0.6f)
                else scheme.onSurface
            )
        }
    }
}

/**
 * 无框输入卡（v2.5.2 新增）：一个实体卡片 + 可选 12sp 灰标签 + 无描边输入。
 *
 * 存在的理由：编辑页原来用 Material3 的 [OutlinedTextField]，自带一圈方框 + 浮动 label，
 * 和全站「实体卡 + 发丝线 + 无框输入」的语言对不上，一进编辑页就像换了个 App。
 * 这里把「框」交给卡片本身，输入区不再画任何描边，只保留 1dp 主色光标。
 *
 * @param label 卡片内的小标签（如「标题」），null 表示不要标签
 * @param minLines 最小行数，>1 时自动多行（备注类）
 */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp, elevation = 4.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        Box {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
