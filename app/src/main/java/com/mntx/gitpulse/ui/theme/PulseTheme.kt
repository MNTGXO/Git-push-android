package com.mntx.gitpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mntx.gitpulse.data.AppSettings

val LocalPulse = compositionLocalOf<PulseColors> { pulseColors(true, false) }
val LocalPulseSettings = compositionLocalOf { AppSettings() }

object PulseTheme {
    val colors: PulseColors
        @Composable @ReadOnlyComposable get() = LocalPulse.current

    val settings: AppSettings
        @Composable @ReadOnlyComposable get() = LocalPulseSettings.current
}

@Composable
fun GitPulseTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = if (settings.dynamicColor) isSystemInDarkTheme() && settings.darkMode else settings.darkMode
    val accent = accentColor(settings.accent, dark)
    val colors = remember(dark, settings.amoled, accent) { pulseColors(dark, settings.amoled, accent) }

    val scheme = remember(dark, accent) {
        if (dark) darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF0A0D14),
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFF3F6FC),
            secondary = Color(0xFF4FE3C1),
            onSecondary = Color(0xFF04120F),
            tertiary = Color(0xFFFF7AB6),
            background = Color(0xFF080B12),
            onBackground = Color(0xFFF3F6FC),
            surface = Color(0xFF111726),
            onSurface = Color(0xFFF3F6FC),
            surfaceVariant = Color(0xFF182031),
            onSurfaceVariant = Color(0xFFA7B2C8),
            outline = Color(0x26FFFFFF),
            error = Color(0xFFFF6B6B),
        ) else lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF0C1220),
            secondary = Color(0xFF0F9E80),
            onSecondary = Color.White,
            tertiary = Color(0xFFD63C81),
            background = Color(0xFFF6F7FB),
            onBackground = Color(0xFF0C1220),
            surface = Color.White,
            onSurface = Color(0xFF0C1220),
            surfaceVariant = Color(0xFFEDF0F7),
            onSurfaceVariant = Color(0xFF55617A),
            outline = Color(0x22000000),
            error = Color(0xFFD93B3B),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalPulse provides colors,
        LocalPulseSettings provides settings,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PulseTypography,
            content = content,
        )
    }
}
