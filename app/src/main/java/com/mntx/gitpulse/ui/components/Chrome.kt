package com.mntx.gitpulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.theme.PulseTheme

/**
 * Shared navigation chrome used by the editors and action menus.
 *
 * Kept in one place (rather than copied per screen) so every sheet in the app has the same
 * scrim opacity, corner radius and hairline stroke.
 */
@Composable
fun BoxScope.SheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.strokeStrong, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(20.dp),
            content = content,
        )
    }
}

/** Slim header used at the top of full-screen editors. */
@Composable
fun EditorTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    backIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable () -> Unit = {},
) {
    val colors = PulseTheme.colors
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIconButton(backIcon, "Back", onClick = onBack)
        Spacer(Modifier.height(0.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            androidx.compose.material3.Text(
                title,
                style = com.mntx.gitpulse.ui.theme.displayStyle(
                    15,
                    androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = -0.3,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Text(
                subtitle,
                style = com.mntx.gitpulse.ui.theme.monoStyle(9.5.toInt()),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
