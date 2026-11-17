package com.thecutestgirl.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import android.view.HapticFeedbackConstants
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.thecutestgirl.app.ui.theme.LocalAppPalette

/** 玻璃栏每个 tab 的数据 */
data class GlassTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
)

/**
 * 液态滑块静止时的尺寸。
 * 滑块回到「只包住图标」的尺寸 84×48dp，栏高 56dp——
 * 两者只差 8dp，视觉上是同一体量的两块玻璃，而不是「一块大板 + 一个小块」。
 */
private val SliderWidth = 84.dp
private val SliderHeight = 48.dp

/** 单格宽度：栏宽 = 单格宽 × tab 数，两个半圆端头正好落在首尾按钮外沿上 */
private val TabSlotWidth = 100.dp

/** 玻璃本体高度（视觉） */
private val BarGlassHeight = 56.dp

/** 可点区域高度：玻璃 56dp 不变，命中区上下各放 4dp（64dp）只放大热区 */
private val BarTouchHeight = 64.dp

/** 滑块最大拉伸比例（液态形变上限） */
private const val MAX_STRETCH = 0.30f
private const val MAX_SQUASH = 0.20f

/** 拖出边界后的橡皮筋跟随系数：拉得动，但越拉越沉 */
private const val RUBBER_BAND = 0.16f

/** 形变的速度敏感度：手指越快，滑块被拉得越长 */
private const val VELOCITY_FULL = 2400f

/**
 * 液态玻璃（liquid glass）底部导航栏——全 APP 唯一保留的玻璃材质（v3.0）。
 *
 * 视觉：
 * - 玻璃体：Haze 实时模糊背后滚过的内容 + 顶部镜面高光 + 底部内阴影 + 上亮下暗的折射描边
 * - 滑块：粉色糖浆质感（主色 16%~22%），随手指速度被「拉长变薄」，像一滴被拉扯的液体
 *
 * 交互：
 * - 点按：直接切 tab（无 ripple）
 * - 长按并拖动：滑块脱离吸附，跟随手指在 tab 之间连续滑动，松手弹性吸附
 */
@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    tabs: List<GlassTab>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 选中块：品牌粉 18% 的玻璃糖浆 + 白色顶部高光
    val pillBody = scheme.primary.copy(alpha = 0.18f)
    val pillHighlight = palette.pillHighlight.copy(alpha = 0.55f)
    val pillEdge = palette.pillHighlight.copy(alpha = 0.65f)

    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val lastIndex = (tabs.size - 1).coerceAtLeast(0)

    // 拖动阶段：滑块位置和形变直接跟手，用 State 直驱（在 draw 里读，不触发重组）
    var dragPos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragSquish by remember { mutableFloatStateOf(0f) }
    // 吸附 / 切 tab 阶段：走弹性动画
    val settlePos = remember { Animatable(selectedIndex.toFloat()) }
    val settleSquish = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    val currentTabs by rememberUpdatedState(tabs)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentLastIndex by rememberUpdatedState(lastIndex)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    // 外部切换 tab（点按 / 返回）时滑过去；拖动期间不打断手指
    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            settlePos.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            // 可点区：宽 = 单格宽 × tab 数、高 = 64dp（比玻璃高 8dp）
            modifier = Modifier
                .width(TabSlotWidth * currentTabs.size)
                .height(BarTouchHeight)
                .pointerInput(tabs.size, currentTabs.size) {
                    detectLongPressDrag(
                        onLongPress = {
                            dragPos = settlePos.value
                            dragSquish = settleSquish.value
                            dragging = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        onDrag = { x, velocityX ->
                            val itemWidth = size.width.toFloat() / currentTabs.size
                            val raw = (x - itemWidth / 2f) / itemWidth
                            val clamped = raw.coerceIn(0f, currentLastIndex.toFloat())
                            val overshoot = raw - clamped

                            val byVelocity = (abs(velocityX) / VELOCITY_FULL).coerceIn(0f, 1f)
                            val byEdge = (abs(overshoot) / 0.8f).coerceIn(0f, 1f) * 0.85f

                            dragPos = clamped + overshoot * RUBBER_BAND
                            dragSquish = maxOf(byVelocity, byEdge).coerceIn(0f, 1f)

                            // 跟手切页：划过哪个 tab 就实时切到哪个
                            val nearest = clamped.roundToInt().coerceIn(0, currentLastIndex)
                            if (nearest != currentSelectedIndex) {
                                currentOnSelect(currentTabs[nearest].route)
                            }
                        },
                        onRelease = {
                            val target = dragPos.roundToInt().coerceIn(0, currentLastIndex)
                            if (target != currentSelectedIndex) {
                                currentOnSelect(currentTabs[target].route)
                            }
                            scope.launch {
                                settlePos.snapTo(dragPos)
                                settleSquish.snapTo(dragSquish)
                                dragging = false
                                launch {
                                    settleSquish.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.32f, stiffness = 420f)
                                    )
                                }
                                settlePos.animateTo(
                                    targetValue = target.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f)
                                )
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(BarGlassHeight)
                    .glassMaterial(
                        hazeState = hazeState,
                        cornerRadius = 28.dp,
                        blurRadius = GlassBlurRadius,
                        noise = GlassNoise,
                        tints = lightGlassTints(scheme.surface)
                    )
                    .drawBehind {
                        drawLiquidSlider(
                            tabCount = currentTabs.size,
                            sliderPos = if (dragging) dragPos else settlePos.value,
                            squish = if (dragging) dragSquish else settleSquish.value,
                            body = pillBody,
                            highlight = pillHighlight,
                            edge = pillEdge
                        )
                    }
            )
            // 图标层与热区：与玻璃并列，这样热区能高于玻璃 8dp，且不挡滑块绘制
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = tab.route == currentRoute
                    val target = if (selected) 1f else 0.95f
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .width(TabSlotWidth)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(tab.route) },
                                role = Role.Tab
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.iconSelected else tab.icon,
                            contentDescription = tab.label,
                            // 选中用主色、未选中用中性灰：粉色只在选中的那一格出现
                            tint = if (selected) scheme.primary
                            else scheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(target)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 长按 → 连续拖动手势：长按成立后每个移动事件都回调 [onDrag]（带 x 与横向速度），
 * 松手回调 [onRelease]；未长按则交还子控件的 clickable 走「点按切 tab」。
 */
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
            val now = change.uptimeMillis
            val dt = (now - lastT).coerceAtLeast(1L)
            val velocityX = (change.position.x - lastX) / dt * 1000f
            lastX = change.position.x
            lastT = now
            change.consume()
            onDrag(change.position.x, velocityX)
        }
        onRelease()
    }
}

/**
 * 液态滑块绘制：只画滑块本体（玻璃体由 [glassMaterial] 负责），
 * 集中在 draw 阶段做，避免每帧重组。
 */
private fun DrawScope.drawLiquidSlider(
    tabCount: Int,
    sliderPos: Float,
    squish: Float,
    body: Color,
    highlight: Color,
    edge: Color
) {
    if (tabCount <= 0) return
    val itemWidth = size.width / tabCount
    val centerX = itemWidth * (sliderPos + 0.5f)

    val baseW = SliderWidth.toPx()
    val baseH = SliderHeight.toPx()
    // 拉长变薄：横向 +30%，纵向 -20%（squish = 1 时到上限）
    val w = baseW * (1f + MAX_STRETCH * squish)
    val h = baseH * (1f - MAX_SQUASH * squish)
    val left = centerX - w / 2f
    val top = (size.height - h) / 2f
    // 圆角随拉伸变尖，像被拉扯的液滴
    val r = (h / 2f) * (1f - 0.22f * squish)

    // 一层更淡更大的底，充当滑块在玻璃上的投影
    drawRoundRect(
        color = body.copy(alpha = body.alpha * 0.55f),
        topLeft = Offset(left - 2.dp.toPx(), top + 2.dp.toPx()),
        size = Size(w + 4.dp.toPx(), h),
        cornerRadius = CornerRadius(r, r)
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r)
    )
    // 顶部一层极淡的高光，让粉色块看起来是糖浆而不是平涂
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(highlight, Color.Transparent),
            start = Offset(left, top),
            end = Offset(left, top + h * 0.75f)
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r)
    )
    drawRoundRect(
        color = edge,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx())
    )
}
