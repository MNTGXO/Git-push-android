package com.mntx.gitpulse.ui.util

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Rendering escape hatch.
 *
 * A handful of components breathe/pulse forever on purpose. That is great on a phone and
 * fatal for headless screenshot rendering: an infinite animation never lets Compose become
 * idle, so `waitForIdle()` blocks until the test times out (this is exactly why the CI
 * screenshots job used to publish zero PNGs).
 *
 * Setting [freezeInfinite] before composing swaps every infinite transition for a static
 * value, without changing behaviour for real users.
 */
object MotionFlags {

    /** When true, infinite transitions are rendered at their initial value. */
    @Volatile
    var freezeInfinite: Boolean = false

    /** Same idea for data-driven animations started by `LaunchedEffect`. */
    @Volatile
    var instantEntrances: Boolean = false
}

/**
 * `InfiniteTransition.animateFloat`, but static when [MotionFlags.freezeInfinite] is on.
 * The flag is read once per composition; it is intended to be set before content starts.
 */
@Composable
fun InfiniteTransition.animateFloatFrozen(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String = "infinite",
): State<Float> {
    if (MotionFlags.freezeInfinite) return remember { mutableStateOf(initialValue) }
    return animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label,
    )
}
