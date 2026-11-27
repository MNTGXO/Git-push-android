package com.mntx.gitpulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.mntx.gitpulse.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private val tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Display face for brand moments, headers and numbers. */
fun displayStyle(
    size: Int,
    weight: FontWeight = FontWeight.SemiBold,
    letterSpacing: Double = -0.6,
    lineHeight: Int = (size * 1.14).toInt(),
) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = tight,
)

fun bodyStyle(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
    lineHeight: Int = (size * 1.45).toInt(),
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

fun monoStyle(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.5).toInt().sp,
    letterSpacing = 0.sp,
)

val PulseTypography = Typography(
    displayLarge = displayStyle(44, FontWeight.Bold, -1.4),
    displayMedium = displayStyle(36, FontWeight.Bold, -1.2),
    displaySmall = displayStyle(30, FontWeight.SemiBold, -0.9),
    headlineLarge = displayStyle(28, FontWeight.SemiBold, -0.7),
    headlineMedium = displayStyle(24, FontWeight.SemiBold, -0.5),
    headlineSmall = displayStyle(21, FontWeight.SemiBold, -0.3),
    titleLarge = displayStyle(18, FontWeight.SemiBold, -0.2),
    titleMedium = bodyStyle(15, FontWeight.SemiBold),
    titleSmall = bodyStyle(13, FontWeight.SemiBold),
    bodyLarge = bodyStyle(15),
    bodyMedium = bodyStyle(13.5.toInt(), letterSpacing = 0.05),
    bodySmall = bodyStyle(12, letterSpacing = 0.1),
    labelLarge = bodyStyle(13, FontWeight.SemiBold, letterSpacing = 0.1),
    labelMedium = bodyStyle(11.5.toInt(), FontWeight.Medium, letterSpacing = 0.25),
    labelSmall = bodyStyle(10.5.toInt(), FontWeight.Medium, letterSpacing = 0.4),
)
