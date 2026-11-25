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
import androidx.compose.foundation.layout.heightIn
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
import com.thecutestgirl.app.ui.theme.appPalette
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

/** 内容卡面使用主题实色，默认无投影和高光；仅浮动导航采用背景模糊。 */
@Composable
fun Modifier.glassPanel(
    cornerRadius: Dp = 20.dp,
    drawHairline: Boolean = false,
    elevation: Dp = 0.dp,
    surfaceAlpha: Float = 1f
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
    drawHairline: Boolean = false,
    elevation: Dp = 0.dp,
    surfaceAlpha: Float = 1f
): Modifier {
    val shadowTint = MaterialTheme.colorScheme.onSurface
    val hairline = appPalette.hairline
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
        .then(
            if (drawHairline) Modifier.border(0.5.dp, hairline, shape) else Modifier
        )
}

/** 可复用的玻璃卡片容器（规范要求的 GlassCard）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.glassPanel(cornerRadius = cornerRadius), content = content)
}

/** 兼容旧调用的实体内容卡片。 */
@Composable
fun Modifier.solidCard(
    cornerRadius: Dp = 20.dp,
    drawHairline: Boolean = false
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
    color: Color = appPalette.hairline
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
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
        .border(0.5.dp, appPalette.hairline, shape)
}

// ---------------- 顶栏 ----------------

/** 编辑页 / 设置页顶栏：页面底 scrim + 1dp 半透分隔线（无模糊、无阴影）。 */
@Composable
fun Modifier.quietTopAppBar(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val hairline = appPalette.hairline
    return this.drawBehind {
        drawRect(color = background.copy(alpha = 0.94f))
        drawLine(
            color = hairline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private val CollapsedBarCorner = 16.dp

/** 保持页头全宽，滚动折叠标题与日期时，右侧操作始终可见。 */
@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val barCorner by animateDpAsState(
        targetValue = if (collapsed) CollapsedBarCorner else 24.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = "topBarCorner"
    )
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(barCorner)).background(
            if (collapsed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
        ).padding(bottom = 4.dp), content = content)
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
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(14.dp))
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
                elevation = if (appPalette.isDark) 0.dp else 2.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = scheme.onSurface.copy(alpha = 0.06f),
                spotColor = scheme.onSurface.copy(alpha = 0.10f)
            )
            .clip(CircleShape)
            .background(scheme.surface)
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
    cornerRadius: Dp = 12.dp,
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
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                // 禁用态明显变灰（否则只禁用点击、看起来仍可点）
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
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

/** 导航玻璃边缘，浅深材质均由主题提供。 */
internal fun DrawScope.drawGlassChrome(cornerRadius: Float, highlight: Color, edge: Color) {
    val corner = CornerRadius(cornerRadius, cornerRadius)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to highlight,
            0.4f to Color.Transparent
        ),
        cornerRadius = corner
    )
    drawRoundRect(
        color = edge,
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
    val palette = appPalette
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .hazeChild(state = hazeState) {
            this.blurRadius = blurRadius
            this.noiseFactor = noise
            this.backgroundColor = scheme.surface
            this.tints = tints ?: listOf(HazeTint(palette.glassSurface))
        }
        .drawBehind {
            drawGlassChrome(cornerRadius = cornerRadius.toPx(), highlight = palette.glassHighlight, edge = palette.glassEdge)
        }
}
