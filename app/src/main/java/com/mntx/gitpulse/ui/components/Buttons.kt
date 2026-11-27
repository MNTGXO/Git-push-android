package com.mntx.gitpulse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.displayStyle

/** Primary CTA — a violet gradient pill with a soft bloom behind it and a press-spring. */
@Composable
fun PulseButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = true,
    height: Dp = 54.dp,
    shape: Shape = RoundedCornerShape(18.dp),
    colors: List<Color>? = null,
    onClick: () -> Unit,
) {
    val c = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.springy(500f, 0.5f),
        label = "btn-scale",
    )
    val gradient = colors ?: listOf(c.accent, c.accent.copy(alpha = 0.82f), c.pink.copy(alpha = 0.85f))
    val alpha = if (enabled) 1f else 0.42f
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(gradient),
                    size = size,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.10f))
                    )
                )
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 22.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF0A0D14),
                )
            } else if (icon != null) {
                Icon(icon, null, tint = Color(0xFF0A0D14), modifier = Modifier.size(19.dp))
            }
            if (loading || icon != null) Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = displayStyle(15, FontWeight.Bold, letterSpacing = 0.1),
                color = Color(0xFF0A0D14),
                maxLines = 1,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(trailingIcon, null, tint = Color(0xFF0A0D14), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Secondary action — hairline outline, no fill. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    height: Dp = 50.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, Motion.springy(500f, 0.55f), label = "ghost")
    val tint = if (danger) c.danger else c.textPrimary
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(if (danger) c.danger.copy(alpha = 0.10f) else c.surfaceElevated.copy(alpha = 0.55f))
            .border(
                1.dp,
                if (danger) c.danger.copy(alpha = 0.35f) else c.strokeStrong,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(text, style = displayStyle(14, FontWeight.SemiBold, letterSpacing = 0.1), color = tint)
        }
    }
}

/** Compact icon button used in headers and toolbars. */
@Composable
fun PulseIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = PulseTheme.colors.textPrimary,
    background: Color = PulseTheme.colors.surfaceElevated,
    badge: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.springy(600f, 0.45f), label = "icon")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, c.stroke, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .drawBehind {
                if (badge) {
                    drawCircle(c.pink, radius = 4.dp.toPx(), center = Offset(this.size.width - 8.dp.toPx(), 8.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Small selectable chip (filters, branches, tabs). */
@Composable
fun PulseChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val c = PulseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val border by animateDpAsState(if (selected) 1.dp else 1.dp, Motion.move(), label = "chip")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accentSoft else c.surface.copy(alpha = 0.6f))
            .border(
                BorderStroke(border, if (selected) c.accent.copy(alpha = 0.55f) else c.stroke),
                RoundedCornerShape(50),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon, null,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = displayStyle(12.5.toInt(), FontWeight.SemiBold, letterSpacing = 0.1),
            color = if (selected) c.accent else c.textSecondary,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) c.accent.copy(alpha = 0.22f) else c.surfaceElevated)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    count.toString(),
                    style = displayStyle(10, FontWeight.SemiBold),
                    color = if (selected) c.accent else c.textTertiary,
                )
            }
        }
    }
}

/** Floating pill nav item with an animated indicator — used by the bottom bar. */
@Composable
fun RowScope.NavPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = PulseTheme.colors
    val alpha by animateFloatAsState(if (selected) 1f else 0.55f, tween(220), label = "nav-alpha")
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, label,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { this.alpha = alpha },
            )
            if (selected) {
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    style = displayStyle(12, FontWeight.SemiBold),
                    color = c.accent,
                    maxLines = 1,
                )
            }
        }
    }
}
