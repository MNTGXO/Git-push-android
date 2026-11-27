package com.mntx.gitpulse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.displayStyle

/**
 * A floating nav dock with a raised centre "sync" action — the most important verb in the app
 * gets the most reachable pixel.
 */
@Composable
fun PulseBottomBar(
    current: String?,
    onSelect: (Route) -> Unit,
    onSync: () -> Unit,
) {
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Home, "Home", current == Route.Home.path, Modifier.weight(1f)) { onSelect(Route.Home) }
            NavItem(Icons.Rounded.Explore, "Explore", current == Route.Explore.path, Modifier.weight(1f)) { onSelect(Route.Explore) }
            Spacer(Modifier.size(64.dp))
            NavItem(Icons.Rounded.GridView, "Repos", current == Route.Library.path, Modifier.weight(1f)) { onSelect(Route.Library) }
            NavItem(Icons.Rounded.Notifications, "Pulse", current == Route.Activity.path, Modifier.weight(1f)) { onSelect(Route.Activity) }
        }

        // Raised centre sync button
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.accent, colors.pink)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSync,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FolderZip, "Sync", tint = Color(0xFF0A0D14), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = PulseTheme.colors
    val tint by animateColorAsState(
        if (selected) colors.accent else colors.textTertiary,
        tween(220),
        label = "nav-tint",
    )
    val lift by animateDpAsState(if (selected) 0.dp else 1.dp, Motion.move(), label = "lift")
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon, label, tint = tint,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer { translationY = lift.toPx() },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = displayStyle(9.5.toInt(), if (selected) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.1),
            color = tint,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(width = if (selected) 14.dp else 0.dp, height = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accent),
        )
    }
}
