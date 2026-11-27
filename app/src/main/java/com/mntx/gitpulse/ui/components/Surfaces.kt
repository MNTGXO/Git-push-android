package com.mntx.gitpulse.ui.components
import com.mntx.gitpulse.ui.util.animateFloatFrozen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.pulseColors

/** A crisp glassy card with a hairline gradient stroke and a soft inner top highlight. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: Dp = 0.dp,
    tint: Color = PulseTheme.colors.surface,
    tintAlpha: Float = 1f,
    borderAlpha: Float = 1f,
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PulseTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.98f * tintAlpha),
                        tint.copy(alpha = 0.90f * tintAlpha),
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        colors.strokeStrong.copy(alpha = colors.strokeStrong.alpha * borderAlpha * 2.4f),
                        colors.stroke.copy(alpha = colors.stroke.alpha * borderAlpha * 3f),
                    )
                ),
                shape = shape,
            )
            .then(
                if (glow) Modifier.drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.accent.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width * 0.85f, 0f),
                            radius = size.maxDimension * 0.8f,
                        ),
                        size = size
                    )
                } else Modifier
            )
            .then(if (padding > 0.dp) Modifier.padding(padding) else Modifier),
        content = content,
    )
}

/** Animated press-scale wrapper for tactile, springy touch feedback. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pressable(
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.965f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !MotionFlags.freezeInfinite) scaleDown else 1f,
        animationSpec = Motion.springy(400f, 0.55f),
        label = "press-scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onLongClick = onLongPress,
                    onClick = onClick,
                ) else Modifier
            )
    ) { content() }
}

/** A shimmering skeleton block used while data loads. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val colors = PulseTheme.colors
    val x by rememberInfiniteTransition(label = "shimmer").animateFloatFrozen(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = Motion.Standard), repeatMode = RepeatMode.Restart),
        label = "shimmer-x",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceElevated)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.strokeStrong.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * x, 0f),
                        end = Offset(size.width * x + size.width * 0.6f, size.height),
                    )
                )
            }
    )
}

/** Radial "aurora" backdrop used behind hero sections. */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val colors = PulseTheme.colors
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.20f * intensity), Color.Transparent),
                    center = Offset(size.width * 0.15f, -size.height * 0.05f),
                    radius = size.maxDimension * 0.9f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.secondary.copy(alpha = 0.10f * intensity), Color.Transparent),
                    center = Offset(size.width * 1.0f, size.height * 0.25f),
                    radius = size.maxDimension * 0.7f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.pink.copy(alpha = 0.08f * intensity), Color.Transparent),
                    center = Offset(size.width * 0.55f, size.height * 1.05f),
                    radius = size.maxDimension * 0.8f,
                )
            )
        }
    )
}

/** Hairline divider that fades at both ends. */
@Composable
fun FadeDivider(modifier: Modifier = Modifier, color: Color = PulseTheme.colors.stroke) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, color, Color.Transparent)
                    ),
                    size = Size(size.width, size.height)
                )
            }
    )
}
