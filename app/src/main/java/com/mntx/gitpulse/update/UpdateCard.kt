package com.mntx.gitpulse.update

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.components.GhostButton
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle

/**
 * The in-app update surface. One card, one primary action: "Update" → download → "Install".
 *
 * The whole point is that a user never has to find the Releases page, choose a file, or reason
 * about signature mismatches — the build is signed with a stable key, so the update simply
 * installs over the existing app.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = PulseTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            when (state.stage) {
                                UpdateStage.AVAILABLE, UpdateStage.READY -> colors.accent.copy(alpha = 0.16f)
                                UpdateStage.UP_TO_DATE -> colors.success.copy(alpha = 0.16f)
                                else -> colors.surfaceElevated
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when (state.stage) {
                            UpdateStage.UP_TO_DATE -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.SystemUpdate
                        },
                        null,
                        tint = when (state.stage) {
                            UpdateStage.UP_TO_DATE -> colors.success
                            UpdateStage.AVAILABLE, UpdateStage.READY -> colors.accent
                            else -> colors.textSecondary
                        },
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Updates",
                        style = displayStyle(13, FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        "v${state.installedName} · build ${state.installedCode}",
                        style = monoStyle(10),
                        color = colors.textTertiary,
                    )
                }
                PulseChip(
                    text = when (state.stage) {
                        UpdateStage.CHECKING -> "checking"
                        UpdateStage.AVAILABLE -> "new"
                        UpdateStage.DOWNLOADING -> "downloading"
                        UpdateStage.READY -> "ready"
                        UpdateStage.UP_TO_DATE -> "latest"
                        UpdateStage.FAILED -> "retry"
                        UpdateStage.IDLE -> "check"
                    },
                    selected = state.stage == UpdateStage.AVAILABLE || state.stage == UpdateStage.READY,
                    onClick = onCheck,
                )
            }

            when (state.stage) {
                UpdateStage.AVAILABLE, UpdateStage.DOWNLOADING, UpdateStage.READY -> {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentSoft.copy(alpha = 0.5f))
                            .padding(12.dp),
                    ) {
                        Column {
                            Text(
                                state.latestName ?: "New build available",
                                style = monoStyle(10.5.toInt(), FontWeight.Medium),
                                color = colors.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (state.stage) {
                                    UpdateStage.DOWNLOADING -> "${(state.progress * 100).toInt()}% of ${humanSize(state.sizeBytes)}"
                                    UpdateStage.READY -> "Downloaded — tap Install to finish"
                                    else -> "${humanSize(state.sizeBytes)} · installs over your current copy"
                                },
                                style = bodyStyle(11),
                                color = colors.textSecondary,
                            )
                        }
                    }
                    if (state.stage == UpdateStage.DOWNLOADING) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = colors.accent,
                            trackColor = colors.surfaceElevated,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PulseButton(
                        text = when (state.stage) {
                            UpdateStage.READY -> "Install update"
                            UpdateStage.DOWNLOADING -> "Downloading…"
                            else -> "Download & install"
                        },
                        icon = if (state.stage == UpdateStage.READY) Icons.Rounded.SystemUpdate else Icons.Rounded.ArrowDownward,
                        loading = state.stage == UpdateStage.DOWNLOADING,
                        enabled = state.stage != UpdateStage.DOWNLOADING,
                        onClick = { if (state.stage == UpdateStage.READY) onInstall() else onDownload() },
                    )
                }

                UpdateStage.UP_TO_DATE -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "You're on the newest build.",
                        style = bodyStyle(11.5.toInt()),
                        color = colors.textTertiary,
                    )
                }

                UpdateStage.FAILED -> {
                    Spacer(Modifier.height(10.dp))
                    PulseBanner(state.error ?: "Update check failed.", com.mntx.gitpulse.ui.components.BannerKind.ERROR)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Try again", onClick = onCheck)
                }

                else -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Check GitHub for a newer APK. Updates install in place — no uninstall needed.",
                        style = bodyStyle(11.5.toInt(), lineHeight = 17),
                        color = colors.textTertiary,
                    )
                    if (state.stage == UpdateStage.CHECKING) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                            color = colors.accent,
                            trackColor = colors.surfaceElevated,
                        )
                    }
                }
            }
        }
    }
}

/** Compact launch-time prompt, shown only when a newer build is actually available. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Update available", style = displayStyle(12.5.toInt(), FontWeight.SemiBold), color = colors.textPrimary)
            Text(
                state.latestName ?: "A newer build is on GitHub",
                style = monoStyle(9.5.toInt()),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("Update", style = displayStyle(11.5.toInt(), FontWeight.Bold), color = Color(0xFF0A0D14), modifier = Modifier.clickableNoRipple(onUpdate))
        }
        Text(
            "✕",
            style = bodyStyle(12),
            color = colors.textTertiary,
            modifier = Modifier.clickableNoRipple(onDismiss).padding(start = 4.dp),
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
