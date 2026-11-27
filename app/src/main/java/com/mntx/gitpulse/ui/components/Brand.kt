package com.mntx.gitpulse.ui.components
import com.mntx.gitpulse.ui.util.animateFloatFrozen
import com.mntx.gitpulse.ui.util.MotionFlags

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme

/**
 * The GitPulse mark: a heartbeat trace that flattens into a branch line, with two nodes.
 * Drawn procedurally so it stays razor sharp at every size and can animate its own drawing.
 */
@Composable
fun PulseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    drawProgress: Float = 1f,
    animateIn: Boolean = true,
    nodePulse: Boolean = true,
) {
    val colors = PulseTheme.colors
    val anim = remember { Animatable(if (animateIn && !MotionFlags.instantEntrances) 0f else 1f) }
    LaunchedEffect(animateIn) {
        if (animateIn && !MotionFlags.instantEntrances) {
            anim.animateTo(1f, tween(Motion.Cinematic, easing = Motion.Emphasized))
        }
    }
    val transition = rememberInfiniteTransition(label = "logo")
    val breathe by transition.animateFloatFrozen(
        0.6f, 1f,
        infiniteRepeatable(tween(1900, easing = LinearEasing), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breathe",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.075f
            val path = Path().apply {
                moveTo(w * 0.08f, h * 0.52f)
                lineTo(w * 0.24f, h * 0.52f)
                lineTo(w * 0.32f, h * 0.30f)
                lineTo(w * 0.42f, h * 0.76f)
                lineTo(w * 0.52f, h * 0.40f)
                lineTo(w * 0.60f, h * 0.52f)
                lineTo(w * 0.72f, h * 0.52f)
                cubicTo(w * 0.84f, h * 0.52f, w * 0.80f, h * 0.26f, w * 0.92f, h * 0.26f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val total = measure.length
            val progress = anim.value * drawProgress
            val partial = Path()
            measure.getSegment(0f, total * progress, partial, true)

            drawPath(
                partial,
                brush = Brush.linearGradient(
                    listOf(colors.accent, colors.secondary),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                ),
                style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            if (progress > 0.02f) {
                drawCircle(
                    colors.accent,
                    radius = stroke * 0.85f * progress,
                    center = Offset(w * 0.08f, h * 0.52f),
                )
            }
            if (progress > 0.7f) {
                val nodeAlpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
                drawCircle(
                    colors.secondary.copy(alpha = 0.22f * nodeAlpha * breathe),
                    radius = stroke * 2.4f,
                    center = Offset(w * 0.92f, h * 0.26f),
                )
                drawCircle(
                    colors.secondary.copy(alpha = nodeAlpha),
                    radius = stroke * 0.95f,
                    center = Offset(w * 0.92f, h * 0.26f),
                )
            }
        }
    }
}

/** Logo + wordmark lockup for headers and the splash. */
@Composable
fun PulseWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 34.dp,
    textSize: Int = 22,
) {
    val colors = PulseTheme.colors
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseLogo(size = logoSize)
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        androidx.compose.material3.Text(
            text = "GitPulse",
            style = com.mntx.gitpulse.ui.theme.displayStyle(textSize, androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = -0.8),
            color = colors.textPrimary,
        )
    }
}
