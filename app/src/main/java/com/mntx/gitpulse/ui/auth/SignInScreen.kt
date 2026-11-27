package com.mntx.gitpulse.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.CodeLines
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseLogo
import com.mntx.gitpulse.ui.components.BannerKind
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    loading: Boolean,
    error: String?,
    onSignIn: (String) -> Unit,
) {
    val colors = PulseTheme.colors
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val haptics = rememberHaptics(PulseTheme.settings.haptics)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var token by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) }

    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(500))
        logoScale.animateTo(1f, tween(800, easing = Motion.Overshoot))
        step = 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 1.1f)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(44.dp))

            // ---- hero ----
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.20f), colors.secondary.copy(alpha = 0.10f))))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                PulseLogo(size = 52.dp)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "GitPulse",
                style = displayStyle(40, FontWeight.Bold, letterSpacing = -1.6),
                color = colors.textPrimary,
            )
            Text(
                "Push zip files, mirror folders and ship to GitHub\nstraight from your pocket.",
                style = bodyStyle(14, lineHeight = 21),
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(visible = step >= 1, enter = fadeIn(tween(500)) + slideInVertically(tween(600, easing = Motion.Decelerate)) { it / 6 }) {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Key, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Personal access token",
                                style = displayStyle(15, FontWeight.SemiBold),
                                color = colors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Create a fine-grained token with Contents + Metadata read & write.",
                            style = bodyStyle(12, lineHeight = 18),
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(14.dp))

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (token.isEmpty()) {
                                    Text("ghp_…", style = monoStyle(13), color = colors.textTertiary)
                                }
                                BasicTextField(
                                    value = token,
                                    onValueChange = { token = it.trim() },
                                    singleLine = true,
                                    textStyle = monoStyle(13).copy(color = colors.textPrimary),
                                    cursorBrush = SolidColor(colors.accent),
                                    visualTransformation = if (revealed) androidx.compose.ui.text.input.VisualTransformation.None
                                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f).focusRequester(focus),
                                )
                                Pressable(onClick = {
                                    haptics.tap()
                                    revealed = !revealed
                                }) {
                                    Icon(
                                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Pressable(onClick = {
                                    haptics.tap()
                                    clipboard.getText()?.text?.let { token = it.trim() }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TokenHint("repo", colors)
                            TokenHint("workflow", colors)
                            TokenHint("gist", colors)
                        }

                        Spacer(Modifier.height(18.dp))
                        PulseButton(
                            text = if (loading) "Verifying…" else "Continue",
                            icon = Icons.Rounded.ArrowForward,
                            loading = loading,
                            enabled = token.length >= 16 && !loading,
                            onClick = {
                                haptics.success()
                                onSignIn(token)
                            },
                        )

                        if (error != null) {
                            Spacer(Modifier.height(12.dp))
                            PulseBanner(error, BannerKind.ERROR)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            AnimatedVisibility(visible = step >= 1, enter = fadeIn(tween(600, delayMillis = 120))) {
                Pressable(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptics.tap()
                        uriHandler.openUri("https://github.com/settings/tokens?type=beta")
                    },
                ) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Open GitHub token settings",
                                    style = displayStyle(13, FontWeight.SemiBold),
                                    color = colors.textPrimary,
                                )
                                Text(
                                    "Tokens are encrypted with Android Keystore",
                                    style = bodyStyle(11),
                                    color = colors.textTertiary,
                                )
                            }
                            Icon(Icons.Rounded.OpenInNew, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Everything stays on-device. No sign-up, no server, no analytics.",
                style = bodyStyle(11),
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            CodeLines(
                lines = listOf(
                    "+ diff --git a/assets b/assets",
                    "+ 12 files changed, 3 deleted",
                    "+ commit 9f2c1a · branch main",
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun TokenHint(text: String, colors: com.mntx.gitpulse.ui.theme.PulseColors) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accentSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = monoStyle(9.5.toInt()), color = colors.accent)
    }
}
