package com.thecutestgirl.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thecutestgirl.app.ui.theme.DegradationLevel
import com.thecutestgirl.app.ui.theme.LocalAppPalette
import com.thecutestgirl.app.ui.theme.LocalDegradationLevel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

internal const val UiSpringDamping = 0.72f
internal const val UiSpringStiffness = 420f

val FabNavSpacing = 16.dp

internal val GlassBlurRadius = 32.dp
internal const val GlassNoise = 0.06f

// 原有底栏专用玻璃材质；其他容器继续使用纸卡。
internal fun lightGlassTints(surface: Color): List<HazeTint> = listOf(
    HazeTint(surface.copy(alpha = 0.72f)),
    HazeTint(surface.copy(alpha = 0.10f))
)

@Composable
fun Modifier.glassMaterial(
    hazeState: HazeState,
    cornerRadius: Dp,
    blurRadius: Dp = GlassBlurRadius,
    noise: Float = GlassNoise,
    tints: List<HazeTint>? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .hazeChild(state = hazeState) {
            this.blurRadius = blurRadius
            this.noiseFactor = noise
            this.backgroundColor = scheme.surface
            this.tints = tints ?: lightGlassTints(scheme.surface)
        }
        .drawBehind {
            val corner = CornerRadius(cornerRadius.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to palette.pillHighlight.copy(alpha = 0.60f),
                    0.4f to Color.Transparent
                ),
                cornerRadius = corner
            )
            drawRoundRect(
                color = palette.hairline,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
}

// ---------------- 纸张纹理与纸卡材质 ----------------

/**
 * 固定种子风格（不使用随机数）的程序化纸纤维。
 * 坐标在 [drawWithCache] 中生成一次，尺寸或主题色变化前不会逐帧重算。
 * L1 降级（含 200% 字体）下整层关闭。
 */
@Composable
fun Modifier.paperTexture(): Modifier {
    val palette = LocalAppPalette.current
    val degradation = LocalDegradationLevel.current
    if (degradation.level >= DegradationLevel.L1.level) return this
    return drawWithCache {
        val cell = 6.dp.toPx()
        val lineStep = 12.dp.toPx()
        val dotRadius = 0.3.dp.toPx()
        val lineWidth = 0.5.dp.toPx()
        onDrawBehind {
            var row = 0
            var y = 0f
            while (y <= size.height) {
                var col = 0
                var x = 0f
                while (x <= size.width) {
                    val noise = ((row * 37 + col * 53) % 11) / 10f
                    val centerX = x + cell * (0.25f + 0.5f * noise)
                    val centerY = y + cell * (0.25f + 0.5f * (1f - noise))
                    drawCircle(
                        color = palette.paperInk,
                        radius = dotRadius,
                        center = Offset(centerX, centerY),
                        alpha = palette.paperDotAlpha
                    )
                    x += cell
                    col++
                }
                if (row % 2 == 0) {
                    val lineY = y + cell * 0.5f
                    drawLine(
                        color = palette.paperInk,
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = lineWidth,
                        alpha = palette.paperLineAlpha
                    )
                }
                y += cell
                row++
            }
        }
    }
}

/** 页面底：中性米纸 / 暖黑纸基 + 同一层纸纤维。 */
@Composable
fun Modifier.paperBackground(): Modifier {
    return background(MaterialTheme.colorScheme.background).paperTexture()
}

/**
 * 纸卡材质：100% 不透明卡面 + 纸纤维 + 1dp 实色纸边 + 顶缘高光 + 左缘压痕。
 * 深色下投影透明度为 0，层次由明度差和纸边承担。
 */
@Composable
fun Modifier.glassPanel(
    shape: androidx.compose.ui.graphics.Shape,
    drawHairline: Boolean = true,
    elevation: Dp = 2.dp,
    @Suppress("UNUSED_PARAMETER") surfaceAlpha: Float = 1f,
    elevated: Boolean = false
): Modifier {
    val palette = LocalAppPalette.current
    val surface = if (elevated) palette.elevatedSurface else MaterialTheme.colorScheme.surface
    val shadowTint = palette.paperShadow
    val hasShadow = elevation > 0.dp && palette.shadowSpotAlpha > 0f
    return this
        .then(
            if (hasShadow) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowTint.copy(alpha = palette.shadowAmbientAlpha),
                    spotColor = shadowTint.copy(alpha = palette.shadowSpotAlpha)
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(surface)
        .paperTexture()
        .drawBehind {
            drawLine(
                color = palette.cardGlow,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = palette.dashedStroke,
                start = Offset(0.5f, 0f),
                end = Offset(0.5f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        .then(
            if (drawHairline) Modifier.border(1.dp, palette.hairline, shape) else Modifier
        )
}

@Composable
fun Modifier.glassPanel(
    cornerRadius: Dp = 12.dp,
    drawHairline: Boolean = true,
    elevation: Dp = 2.dp,
    surfaceAlpha: Float = 1f,
    elevated: Boolean = false
): Modifier = glassPanel(
    shape = RoundedCornerShape(cornerRadius),
    drawHairline = drawHairline,
    elevation = elevation,
    surfaceAlpha = surfaceAlpha,
    elevated = elevated
)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.glassPanel(cornerRadius = cornerRadius), content = content)
}

@Composable
fun Modifier.solidCard(
    cornerRadius: Dp = 12.dp,
    drawHairline: Boolean = true
): Modifier = glassPanel(
    cornerRadius = cornerRadius,
    drawHairline = drawHairline
)

/** 空态虚线卡：1dp 虚线纸边 + 四角十字针脚（L5 降级关闭）。 */
@Composable
fun Modifier.dashedCard(
    cornerRadius: Dp = 12.dp,
    dash: Dp = 6.dp,
    gap: Dp = 4.dp,
    color: Color = LocalAppPalette.current.dashedStroke
): Modifier {
    val palette = LocalAppPalette.current
    val degradation = LocalDegradationLevel.current
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(palette.dashedFill)
        .paperTexture()
        .drawBehind {
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = if (degradation.level >= DegradationLevel.L7.level) {
                        null
                    } else {
                        androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(dash.toPx(), gap.toPx()), 0f
                        )
                    }
                )
            )
            if (degradation.level < DegradationLevel.L5.level) {
                val inset = 5.dp.toPx()
                val arm = 4.dp.toPx()
                listOf(
                    Offset(inset, inset) to Offset(inset + arm, inset + arm),
                    Offset(size.width - inset, inset) to Offset(size.width - inset - arm, inset + arm),
                    Offset(inset, size.height - inset) to Offset(inset + arm, size.height - inset - arm),
                    Offset(size.width - inset, size.height - inset) to Offset(
                        size.width - inset - arm,
                        size.height - inset - arm
                    )
                ).forEach { (start, end) ->
                    drawLine(
                        color = palette.dashedStroke,
                        start = start,
                        end = end,
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
}

// ---------------- 照片 ----------------

@Composable
fun Modifier.photoShape(cornerRadius: Dp = 10.dp): Modifier =
    clip(RoundedCornerShape(cornerRadius))

@Composable
fun Modifier.photoFrame(cornerRadius: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .border(1.dp, LocalAppPalette.current.hairline, shape)
}

// ---------------- 顶栏 ----------------

@Composable
fun Modifier.quietTopAppBar(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val hairline = LocalAppPalette.current.hairline
    return this.drawBehind {
        drawRect(color = background)
        drawLine(
            color = hairline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private val CollapsedBarWidth = 136.dp

@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    collapsedWidth: Dp = CollapsedBarWidth,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val expandedWidth = maxWidth
        val barWidth by animateDpAsState(
            targetValue = if (collapsed) collapsedWidth.coerceAtMost(expandedWidth) else expandedWidth,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "topBarWidth"
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .glassPanel(cornerRadius = 16.dp)
        ) {
            // 内容按展开宽度测量，外框收缩时不挤压日期条或重排标题。
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
    }
}

// ---------------- 交互状态 ----------------

/** 输入类控件获得键盘焦点时的 2dp 主色内描边。 */
@Composable
fun Modifier.focusRing(focused: Boolean, cornerRadius: Dp = 10.dp): Modifier {
    val focusColor = MaterialTheme.colorScheme.primary
    return drawBehind {
        if (focused) {
            drawRoundRect(
                color = focusColor,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * 行类反馈：hovered / pressed 共用 6% 纸墨染色；focused 画 2dp 主色描边；
 * disabled 只声明语义，不再压透明度。
 * 按钮调用时传 [pressedFeedback] = false，按压反馈仍走 98% 缩放。
 */
@Composable
fun Modifier.pressTint(
    interactionSource: MutableInteractionSource,
    color: Color = LocalAppPalette.current.paperInk,
    cornerRadius: Dp = 0.dp,
    enabled: Boolean = true,
    pressedFeedback: Boolean = true,
    focusedFeedback: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val tint = color.copy(alpha = 0.06f)
    val focusColor = MaterialTheme.colorScheme.primary
    return this
        .semantics { if (!enabled) disabled() }
        .drawBehind {
            if (enabled && ((pressed && pressedFeedback) || hovered)) {
                drawRoundRect(
                    color = tint,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                )
            }
            if (enabled && focusedFeedback && focused) {
                drawRoundRect(
                    color = focusColor,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
}

/** 48dp 热区的纸质图标按钮：悬停/按压 6% 纸墨，键盘焦点 2dp 主色框。 */
@Composable
fun PaperIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .pressTint(interactionSource, cornerRadius = 24.dp, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val palette = LocalAppPalette.current
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
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) containerColor else palette.disabledContainer)
            .pressTint(
                interactionSource = interactionSource,
                cornerRadius = 10.dp,
                enabled = enabled,
                pressedFeedback = false
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SolidCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    contentDescription: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "solidFabScale"
    )
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (palette.shadowSpotAlpha > 0f) {
                        Modifier.shadow(
                            elevation = if (pressed) 4.dp else 10.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = palette.paperShadow.copy(alpha = palette.shadowAmbientAlpha),
                            spotColor = palette.paperShadow.copy(alpha = palette.shadowSpotAlpha)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(scheme.primary)
                .pressTint(
                    interactionSource = interactionSource,
                    cornerRadius = diameter / 2f,
                    pressedFeedback = false
                )
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
}

@Composable
fun SoftCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalAppPalette.current
    val shape = RoundedCornerShape(999.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = UiSpringDamping, stiffness = UiSpringStiffness),
        label = "softButtonScale"
    )
    CompositionLocalProvider(
        LocalContentColor provides if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Row(
            modifier = modifier
                .heightIn(min = 40.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .background(
                    if (enabled) palette.buttonFill else palette.disabledContainer
                )
                .pressTint(
                    interactionSource = interactionSource,
                    cornerRadius = 20.dp,
                    enabled = enabled,
                    pressedFeedback = false
                )
                .border(1.dp, palette.hairline, shape)
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
}
