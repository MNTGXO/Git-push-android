package com.mntx.gitpulse.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * A small haptics vocabulary so interactions feel physical:
 * `tap` for navigation, `select` for chips, `success` after a push, `warn` on failures.
 */
class Haptics(private val view: View, private val enabled: Boolean) {

    fun tap() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.VIRTUAL_KEY)

    fun select() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun success() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)

    fun warn() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        if (!enabled) return
        runCatching { view.performHapticFeedback(constant) }
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

/** Formats relative timestamps from GitHub's ISO-8601 strings without extra dependencies. */
fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val seconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
    return when {
        seconds < 45 -> "just now"
        seconds < 90 -> "1 min ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 7200 -> "1 hour ago"
        seconds < 86400 -> "${seconds / 3600} hours ago"
        seconds < 172800 -> "yesterday"
        seconds < 604800 -> "${seconds / 86400} days ago"
        seconds < 2592000 -> "${seconds / 604800} weeks ago"
        seconds < 31536000 -> "${seconds / 2592000} months ago"
        else -> "${seconds / 31536000} years ago"
    }
}

fun compactNumber(value: Int): String = when {
    value < 1000 -> value.toString()
    value < 1_000_000 -> String.format("%.1fk", value / 1000.0).replace(".0k", "k")
    else -> String.format("%.1fM", value / 1_000_000.0)
}

fun initials(name: String?): String {
    if (name.isNullOrBlank()) return "?"
    val parts = name.trim().split(Regex("\\s+"), limit = 2)
    return if (parts.size == 1) parts[0].take(2).uppercase()
    else (parts[0].take(1) + parts[1].take(1)).uppercase()
}
