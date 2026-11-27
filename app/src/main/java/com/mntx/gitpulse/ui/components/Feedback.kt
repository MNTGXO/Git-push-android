package com.mntx.gitpulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle

/** Circular progress ring with a gradient sweep and a big centre label. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 168.dp,
    stroke: androidx.compose.ui.unit.Dp = 10.dp,
    trackColor: Color = PulseTheme.colors.stroke,
    label: String? = null,
    sublabel: String? = null,
    indeterminate: Boolean = false,
) {
    val c = PulseTheme.colors
    val transition = rememberInfiniteTransition(label = "ring")
    val spin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), Motion.move(420), label = "ring-progress"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx),
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            val sweep = if (indeterminate) 90f else animated * 360f
            val start = if (indeterminate) spin else -90f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(c.accent, c.secondary, c.pink, c.accent)
                ),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx),
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            // travelling tip dot
            if (sweep > 0f) {
                val angle = Math.toRadians((start + sweep).toDouble())
                val radius = (this.size.width - strokePx) / 2
                val cx = center.x + radius * kotlin.math.cos(angle).toFloat()
                val cy = center.y + radius * kotlin.math.sin(angle).toFloat()
                drawCircle(Color.White, radius = strokePx * 0.36f, center = Offset(cx, cy))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label != null) {
                Text(
                    label,
                    style = displayStyle(if (size > 140.dp) 30 else 20, FontWeight.Bold, letterSpacing = -0.8),
                    color = c.textPrimary,
                )
            }
            if (sublabel != null) {
                Text(
                    sublabel,
                    style = bodyStyle(11.5.toInt(), letterSpacing = 0.3),
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Slim animated progress bar. */
@Composable
fun PulseBar(progress: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 6.dp) {
    val c = PulseTheme.colors
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), Motion.move(380), label = "bar"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.stroke)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(c.accent, c.secondary)))
        )
    }
}

enum class BannerKind { INFO, SUCCESS, WARN, ERROR }

/** Inline status banner with an icon, used for all error/duplicate feedback. */
@Composable
fun PulseBanner(
    text: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = PulseTheme.colors
    val tint = when (kind) {
        BannerKind.INFO -> c.info
        BannerKind.SUCCESS -> c.success
        BannerKind.WARN -> c.amber
        BannerKind.ERROR -> c.danger
    }
    val icon: ImageVector = when (kind) {
        BannerKind.INFO -> Icons.Rounded.Info
        BannerKind.SUCCESS -> Icons.Rounded.Check
        BannerKind.WARN -> Icons.Rounded.ErrorOutline
        BannerKind.ERROR -> Icons.Rounded.WifiOff
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = bodyStyle(12.5.toInt(), FontWeight.Medium, lineHeight = 18),
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

/** Big success moment: animated check draw + pulse rings. */
@Composable
fun SuccessCheckmark(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    tint: Color = PulseTheme.colors.success,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = Motion.Decelerate)) }
    val ring by rememberInfiniteTransition(label = "halo").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "halo",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(tint.copy(alpha = 0.18f * (1f - ring)), radius = this.size.minDimension / 2 * (0.7f + ring * 0.3f))
            drawCircle(tint.copy(alpha = 0.35f), radius = this.size.minDimension / 2 * 0.78f)
            drawCircle(tint, radius = this.size.minDimension / 2 * 0.62f)
            val w = this.size.minDimension * 0.09f
            val path = androidx.compose.ui.graphics.Path().apply {
                val cx = center.x
                val cy = center.y
                moveTo(cx - w * 2.1f, cy + w * 0.1f)
                lineTo(cx - w * 0.5f, cy + w * 1.7f)
                lineTo(cx + w * 2.3f, cy - w * 1.5f)
            }
            drawPath(
                path,
                Color(0xFF04120C),
                style = Stroke(width = w, cap = StrokeCap.Round),
                alpha = progress.value,
            )
        }
    }
}

/** Friendly empty / error state used across the app. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = PulseTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(c.surfaceElevated)
                .border(1.dp, c.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = displayStyle(18, FontWeight.SemiBold), color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = bodyStyle(13, lineHeight = 20),
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Tiny status dot with a breathing glow. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp, pulse: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "dot")
    val glow by transition.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(1600, easing = Motion.Standard), repeatMode = RepeatMode.Reverse),
        label = "glow",
    )
    Canvas(modifier.size(size * 2)) {
        if (pulse) drawCircle(color.copy(alpha = 0.28f * glow), radius = this.size.minDimension / 2)
        drawCircle(color, radius = this.size.minDimension / 2 * 0.42f)
    }
}

/** Animated shimmering line of "code" used on the sign-in hero. */
@Composable
fun CodeLines(modifier: Modifier = Modifier, lines: List<String>, tint: Color = PulseTheme.colors.secondary) {
    Column(modifier) {
        lines.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d".format(index + 1),
                    style = monoStyle(9, FontWeight.Normal),
                    color = PulseTheme.colors.textTertiary.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    line,
                    style = monoStyle(10.5.toInt()),
                    color = if (line.startsWith("+")) tint else PulseTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}
