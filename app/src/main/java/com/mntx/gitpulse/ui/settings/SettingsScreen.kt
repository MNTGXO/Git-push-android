package com.mntx.gitpulse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mntx.gitpulse.BuildConfig
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.PulseLogo
import com.mntx.gitpulse.ui.theme.AccentPreset
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.accentColor
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.compactNumber

@Composable
fun SettingsScreen(
    user: GhUser,
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onSignOut: () -> Unit,
    update: com.mntx.gitpulse.update.UpdateState = com.mntx.gitpulse.update.UpdateState(),
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
) {
    val colors = PulseTheme.colors
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = 60.dp),
    ) {
        item {
            Box {
                AuroraBackdrop(Modifier.fillMaxWidth().height(220.dp), intensity = 0.6f)
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Settings",
                            style = displayStyle(20, FontWeight.SemiBold, letterSpacing = -0.4),
                            color = colors.textPrimary,
                        )
                    }
                    ProfileCard(user)
                }
            }
        }

        item {
            SectionHeader("Appearance", Icons.Rounded.ColorLens)
            GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingSwitch(
                        icon = if (settings.darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        title = "Dark theme",
                        subtitle = if (settings.darkMode) "Midnight inks" else "Paper daylight",
                        checked = settings.darkMode,
                    ) { onUpdate { s -> s.copy(darkMode = it) } }
                    SettingDivider()
                    SettingSwitch(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AMOLED black",
                        subtitle = "True black backgrounds, saves battery",
                        checked = settings.amoled,
                        enabled = settings.darkMode,
                    ) { onUpdate { s -> s.copy(amoled = it) } }
                    SettingDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Accent", style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
                        Text("Colours flow through buttons, charts and the logo", style = bodyStyle(11), color = colors.textTertiary)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccentPreset.entries.forEach { preset ->
                                val tint = accentColor(preset, settings.darkMode)
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(tint)
                                        .then(
                                            if (settings.accent == preset) Modifier.border(2.5.dp, colors.textPrimary, CircleShape)
                                            else Modifier.border(1.dp, colors.stroke, CircleShape)
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onUpdate { s -> s.copy(accent = preset) } },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Feel", Icons.Rounded.MotionPhotosOn)
            GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingSwitch(Icons.Rounded.Vibration, "Haptics", "Tactile feedback on actions", settings.haptics) {
                        onUpdate { s -> s.copy(haptics = it) }
                    }
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.MotionPhotosOn, "Reduce motion", "Calmer transitions", settings.reduceMotion) {
                        onUpdate { s -> s.copy(reduceMotion = it) }
                    }
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Fingerprint, "Biometric lock", "Ask for fingerprint on launch", settings.biometricLock) {
                        onUpdate { s -> s.copy(biometricLock = it) }
                    }
                }
            }
        }

        item {
            SectionHeader("Sync defaults", Icons.Rounded.Tune)
            GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("Default branch", style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
                        Text(settings.defaultBranch, style = monoStyle(11), color = colors.accent)
                    }
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Description, "Confirm before push", "Show the review sheet first", settings.confirmBeforePush) {
                        onUpdate { s -> s.copy(confirmBeforePush = it) }
                    }
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Description, "Diff summary in commit", "Append a markdown table to each commit", settings.showDiffOnPush) {
                        onUpdate { s -> s.copy(showDiffOnPush = it) }
                    }
                }
            }
        }

        item {
            SectionHeader("Updates", Icons.Rounded.SystemUpdate)
            Box(Modifier.padding(horizontal = 18.dp)) {
                com.mntx.gitpulse.update.UpdateCard(
                    state = update,
                    onCheck = onCheckUpdate,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                )
            }
        }

        item {
            SectionHeader("Connection", Icons.Rounded.Hub)
            GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("GitHub host", style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
                    Text(
                        if (settings.apiBaseUrl.isBlank()) "api.github.com — the public API"
                        else settings.apiBaseUrl,
                        style = monoStyle(10.5.toInt()),
                        color = colors.accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulseChip("github.com", settings.apiBaseUrl.isBlank()) {
                            onUpdate { s -> s.copy(apiBaseUrl = "") }
                        }
                        PulseChip("Enterprise", settings.apiBaseUrl.isNotBlank()) {
                            onUpdate { s ->
                                s.copy(apiBaseUrl = "https://ghe.example.com/api/v3")
                            }
                        }
                    }
                    if (settings.apiBaseUrl.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HostField(
                            value = settings.apiBaseUrl,
                            onValue = { value -> onUpdate { s -> s.copy(apiBaseUrl = value.trim()) } },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "REST endpoint, usually https://your-host/api/v3. Sign in again if the token belongs to a different host.",
                            style = bodyStyle(10.5.toInt(), lineHeight = 15),
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("About", Icons.Rounded.Info)
            GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseLogo(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GitPulse", style = displayStyle(15, FontWeight.SemiBold), color = colors.textPrimary)
                            Text("v${BuildConfig.APP_VERSION_LABEL} · build ${BuildConfig.VERSION_CODE}", style = monoStyle(10), color = colors.textTertiary)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("OPEN SOURCE", style = monoStyle(8.5.toInt(), FontWeight.SemiBold), color = colors.accent)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "GitPulse talks to GitHub directly from your device. Your token is encrypted with the Android Keystore — it never leaves the phone.",
                        style = bodyStyle(11.5.toInt(), lineHeight = 17),
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Pressable(onClick = {
                        // Local only: the app has no telemetry, so a crash report is something the
                        // user chooses to copy. Empty means nothing has crashed since install.
                        val report = com.mntx.gitpulse.CrashCatcher.lastReport(context)
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        if (report != null) {
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GitPulse crash", report))
                            android.widget.Toast.makeText(context, "Crash report copied", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "No crash recorded — nice", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Copy crash report", style = displayStyle(12, FontWeight.SemiBold), color = colors.textSecondary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ContentCopy, null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Pressable(onClick = { uri.openUri("https://github.com/MNTGXO/Git-push-android") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("View source on GitHub", style = displayStyle(12, FontWeight.SemiBold), color = colors.accent)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Pressable(
                onClick = onSignOut,
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.danger.copy(alpha = 0.10f))
                        .border(1.dp, colors.danger.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = colors.danger, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out of @${user.login}", style = displayStyle(13, FontWeight.SemiBold), color = colors.danger)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileCard(user: GhUser) {
    val colors = PulseTheme.colors
    GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), glow = true) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.accent, colors.pink))),
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name ?: user.login,
                    style = displayStyle(18, FontWeight.SemiBold, letterSpacing = -0.4),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("@${user.login}", style = monoStyle(10.5.toInt()), color = colors.accent)
                if (!user.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        user.bio!!,
                        style = bodyStyle(11, lineHeight = 16),
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Inline text field for the Enterprise API base URL. */
@Composable
private fun HostField(value: String, onValue: (String) -> Unit) {
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = monoStyle(11).copy(color = colors.textPrimary),
            singleLine = false,
            maxLines = 2,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val colors = PulseTheme.colors
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            title.uppercase(),
            style = monoStyle(9.5.toInt(), FontWeight.SemiBold),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) colors.accent else colors.textTertiary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(10.5.toInt()), color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = colors.stroke,
            ),
        )
    }
}

@Composable
private fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(PulseTheme.colors.stroke)
    )
}
