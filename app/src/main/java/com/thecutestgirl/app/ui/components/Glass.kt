package com.thecutestgirl.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thecutestgirl.app.ui.theme.BorderHairline
import com.thecutestgirl.app.ui.theme.GlassTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

// ---------------- 全局 UI 节奏（v4.0：8dp 网格）----------------

/** 全站唯一弹簧：按压 / 折叠 / 徽标统一这一条（约 300ms 到位） */
internal const val UiSpringDamping = 0.72f
internal const val UiSpringStiffness = 420f

/** FAB 与底部导航栏之间的安全间距（8 的倍数） */
val FabNavSpacing = 16.dp

/** 玻璃材质统一参数（底部液态导航栏） */
internal val GlassBlurRadius = 32.dp
internal const val GlassNoise = 0.06f

/** 深色主题下投影不参与层次：边界由「表面明度差 + 1dp 半透边框 + 内发光」承担 */

// ---------------- 玻璃面板：卡片 / 容器的唯一材质 ----------------

/**
 * 玻璃面板（v4.1 浅色版，全站唯一的卡面材质）：
 * 1. 柔和中性投影（浅色下允许小投影，8dp）
 * 2. 78% 白玻璃填充
 * 3. 背景之后画「顶部内发光」，accent 光晕只落在左上角
 * 4. 1dp 8% 深半透边框
 *
 * 真实背景模糊只属于底部导航栏；其余卡面共用这套玻璃「外观」。
 */
@Composable
fun Modifier.glassPanel(
    cornerRadius: Dp = 16.dp,
    drawHairline: Boolean = true,
    elevation: Dp = 8.dp,
    surfaceAlpha: Float = 0.92f
): Modifier = glassPanel(
    shape = RoundedCornerShape(cornerRadius),
    drawHairline = drawHairline,
    elevation = elevation,
    surfaceAlpha = surfaceAlpha
)

/** glassPanel 的任意 shape 版本（抽屉用顶部圆角 24、底部齐边）。 */
@Composable
fun Modifier.glassPanel(
    shape: androidx.compose.ui.graphics.Shape,
    drawHairline: Boolean = true,
    elevation: Dp = 8.dp,
    surfaceAlpha: Float = 0.92f
): Modifier {
    val shadowTint = MaterialTheme.colorScheme.onSurface
    return this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowTint.copy(alpha = 0.06f),
                    spotColor = shadowTint.copy(alpha = 0.10f)
                )
            } else Modifier
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
        .drawBehind {
            // 顶部内发光（背景之后绘制）
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.35f
                )
            )
        }
        .then(
            if (drawHairline) Modifier.border(1.dp, BorderHairline, shape) else Modifier
        )
}

/** 可复用的玻璃卡片容器（规范要求的 GlassCard）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.glassPanel(cornerRadius = cornerRadius), content = content)
}

/**
 * 兼容旧调用：所有卡片统一走 [glassPanel]，固定 8dp 柔和投影（不提供 elevation 参数，
 * 避免出现「看起来能配、实际不影响视觉」的无效参数）。
 */
@Composable
fun Modifier.solidCard(
    cornerRadius: Dp = 16.dp,
    drawHairline: Boolean = true
): Modifier = glassPanel(
    cornerRadius = cornerRadius,
    drawHairline = drawHairline
)

/** 虚线卡：给「还空着、等你填」的位置（未记录的餐次）。 */
@Composable
fun Modifier.dashedCard(
    cornerRadius: Dp = 16.dp,
    dash: Dp = 8.dp,
    gap: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = 0.72f))
        .drawBehind {
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(dash.toPx(), gap.toPx()), 0f
                    )
                )
            )
        }
}

// ---------------- 照片 ----------------

/** 真实照片：只裁 8dp 圆角（卡片 16 − 缩进 8）。 */
@Composable
fun Modifier.photoShape(cornerRadius: Dp = 8.dp): Modifier =
    clip(RoundedCornerShape(cornerRadius))

/** 占位 / 同步失败照片：8dp 圆角 + 1dp 半透边框。 */
@Composable
fun Modifier.photoFrame(cornerRadius: Dp = 8.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .border(1.dp, BorderHairline, shape)
}

// ---------------- 顶栏 ----------------

/** 编辑页 / 设置页顶栏：页面底 scrim + 1dp 半透分隔线（无模糊、无阴影）。 */
@Composable
fun Modifier.quietTopAppBar(): Modifier {
    val background = MaterialTheme.colorScheme.background
    return this.drawBehind {
        drawRect(color = background.copy(alpha = 0.94f))
        drawLine(
            color = BorderHairline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private val CollapsedBarWidth = 136.dp
private val CollapsedBarCorner = 16.dp
private val MenuSlot = 64.dp

/**
 * 顶部悬浮面板（v4.0）：玻璃面板 24dp 圆角 + 可选菜单按钮；
 * 滚动后收成左上角 16dp 圆角的小胶囊。宽度动画 300ms，内容只测量一次。
 *
 * v4.2.2：右侧齿轮改为「设置菜单」按钮。三个列表页共用这一个入口，
 * 点开全局侧边菜单，不再出现「每页一个齿轮 / 长按标题」的分裂入口。
 */
@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onOpenMenu: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val menuSpace = if (onOpenMenu != null) MenuSlot else 0.dp
        val expandedWidth = (maxWidth - menuSpace).coerceAtLeast(CollapsedBarWidth)
        val barWidth by animateDpAsState(
            targetValue = if (collapsed) CollapsedBarWidth else expandedWidth,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "topBarWidth"
        )
        val barCorner by animateDpAsState(
            targetValue = if (collapsed) CollapsedBarCorner else 24.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "topBarCorner"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .glassPanel(cornerRadius = barCorner, surfaceAlpha = 0.96f)
            ) {
                Layout(
                    content = {
                        Column(
                            modifier = Modifier.padding(bottom = 4.dp),
                            content = content
                        )
                    }
                ) { measurables, constraints ->
                    val placeable = measurables.first()
                        .measure(Constraints.fixedWidth(expandedWidth.roundToPx()))
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (onOpenMenu != null) {
                SolidCircleButton(
                    onClick = onOpenMenu,
                    diameter = 48.dp,
                    contentDescription = "打开设置菜单",
                    modifier = Modifier.padding(top = if (collapsed) 0.dp else 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "打开设置菜单",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ---------------- 按钮 ----------------

/**
 * 主操作按钮（规范：全宽 48dp、小圆角 8、按压 scale 98%）。
 * 不用系统 ripple 方框，反馈由轻缩 + 颜色承担。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "primaryPressScale"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.32f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f)
        )
    }
}

/**
 * 实心圆形按钮：FAB / 齿轮。白色玻璃圆 + 柔和投影 + 1dp 半透边框；
 * 按压 scale 98%。
 */
@Composable
fun SolidCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "solidFabScale"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = if (pressed) 4.dp else 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = scheme.onSurface.copy(alpha = 0.06f),
                spotColor = scheme.onSurface.copy(alpha = 0.10f)
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.86f))
            .border(1.dp, BorderHairline, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 次要胶囊按钮（回到本月 / 选照片 / 拍一张）：玻璃底 + 半透边框，
 * 高 40dp、8dp 圆角，按压 scale 98%。
 */
@Composable
fun SoftCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "softButtonScale"
    )

    Row(
        modifier = modifier
            .height(40.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                // 禁用态明显变灰（否则只禁用点击、看起来仍可点）
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.86f))
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .border(1.dp, BorderHairline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                role = Role.Button
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ---------------- 液态玻璃底栏材质（全 app 唯一保留的背景模糊）----------------

/** 浅色玻璃着色：白 72% + 10% 白高光。粉色只存在于滑块本身。 */
internal fun lightGlassTints(surface: Color): List<HazeTint> = listOf(
    HazeTint(Color.White.copy(alpha = 0.72f)),
    HazeTint(Color.White.copy(alpha = 0.10f))
)

/** 玻璃边缘：顶部白色高光 + 1dp 8% 深边框。 */
internal fun DrawScope.drawGlassChrome(cornerRadius: Float) {
    val corner = CornerRadius(cornerRadius, cornerRadius)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.60f),
            0.4f to Color.Transparent
        ),
        cornerRadius = corner
    )
    drawRoundRect(
        color = BorderHairline,
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
    )
}

/** 玻璃材质本体：裁形 → Haze 实时模糊 + 浅色着色 → 高光/边框。 */
@Composable
fun Modifier.glassMaterial(
    hazeState: HazeState,
    cornerRadius: Dp,
    blurRadius: Dp = GlassBlurRadius,
    noise: Float = GlassNoise,
    tints: List<HazeTint>? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .hazeChild(state = hazeState) {
            this.blurRadius = blurRadius
            this.noiseFactor = noise
            this.backgroundColor = scheme.surface
            this.tints = tints ?: lightGlassTints(scheme.surface)
        }
        .drawBehind {
            drawGlassChrome(cornerRadius = cornerRadius.toPx())
        }
}
