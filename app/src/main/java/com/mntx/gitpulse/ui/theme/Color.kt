package com.mntx.gitpulse.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * GitPulse colour language — a deep space "ink" base with an electric violet primary and
 * a mint secondary. Every screen is built from these tokens so the whole app stays coherent.
 */
@Immutable
data class PulseColors(
    val isDark: Boolean,
    val isAmoled: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceGlass: Color,
    val stroke: Color,
    val strokeStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val secondary: Color,
    val amber: Color,
    val pink: Color,
    val danger: Color,
    val success: Color,
    val info: Color,
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color,
)

/** Accent palettes the user can pick in Settings → Appearance. */
enum class AccentPreset(val label: String, val dark: Long, val light: Long) {
    VIOLET("Violet", 0xFF8B7CFF, 0xFF5B49E6),
    MINT("Mint", 0xFF3DD9B5, 0xFF0F9E80),
    SUNSET("Sunset", 0xFFFF8A65, 0xFFE2542C),
    ICE("Ice", 0xFF5AA9FF, 0xFF1668D6),
    ROSE("Rose", 0xFFFF7AB6, 0xFFD63C81),
}

fun accentColor(preset: AccentPreset, dark: Boolean): Color =
    Color(if (dark) preset.dark else preset.light)

fun pulseColors(
    dark: Boolean,
    amoled: Boolean,
    accent: Color = Color(0xFF8B7CFF),
): PulseColors {
    val secondary = Color(0xFF4FE3C1)
    return if (dark) {
        PulseColors(
            isDark = true,
            isAmoled = amoled,
            background = if (amoled) Color(0xFF000000) else Color(0xFF080B12),
            backgroundAlt = if (amoled) Color(0xFF050505) else Color(0xFF0C111B),
            surface = if (amoled) Color(0xFF0A0A0A) else Color(0xFF111726),
            surfaceElevated = if (amoled) Color(0xFF141414) else Color(0xFF182031),
            surfaceGlass = if (amoled) Color(0xE60E0E0E) else Color(0xCC141B29),
            stroke = Color(0x14FFFFFF),
            strokeStrong = Color(0x26FFFFFF),
            textPrimary = Color(0xFFF3F6FC),
            textSecondary = Color(0xFFA7B2C8),
            textTertiary = Color(0xFF6D7A93),
            accent = accent,
            accentSoft = accent.copy(alpha = 0.16f),
            secondary = secondary,
            amber = Color(0xFFFFBE6B),
            pink = Color(0xFFFF7AB6),
            danger = Color(0xFFFF6B6B),
            success = Color(0xFF45DE9B),
            info = Color(0xFF63A9FF),
            gradientStart = accent.copy(alpha = 0.55f),
            gradientMid = Color(0xFF4FE3C1).copy(alpha = 0.30f),
            gradientEnd = Color(0xFFFF7AB6).copy(alpha = 0.28f),
        )
    } else {
        PulseColors(
            isDark = false,
            isAmoled = false,
            background = Color(0xFFF6F7FB),
            backgroundAlt = Color(0xFFEDF0F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFFFFFFF),
            surfaceGlass = Color(0xE6FFFFFF),
            stroke = Color(0x14000000),
            strokeStrong = Color(0x22000000),
            textPrimary = Color(0xFF0C1220),
            textSecondary = Color(0xFF55617A),
            textTertiary = Color(0xFF8A94A8),
            accent = accent,
            accentSoft = accent.copy(alpha = 0.12f),
            secondary = Color(0xFF0F9E80),
            amber = Color(0xFFC97A12),
            pink = Color(0xFFD63C81),
            danger = Color(0xFFD93B3B),
            success = Color(0xFF139A66),
            info = Color(0xFF1668D6),
            gradientStart = accent.copy(alpha = 0.30f),
            gradientMid = Color(0xFF24B79A).copy(alpha = 0.18f),
            gradientEnd = Color(0xFFD63C81).copy(alpha = 0.14f),
        )
    }
}
