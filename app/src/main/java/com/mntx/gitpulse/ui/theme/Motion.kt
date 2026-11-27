package com.mntx.gitpulse.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FiniteAnimationSpec

/**
 * Motion language for GitPulse.
 *
 * The product idea: "a pulse". Motion should feel like a confident heartbeat — quick
 * acceleration with a soft settle, never bouncy/toy-like for data surfaces, and playful
 * overshoot only for delightful micro-interactions (like button / success checkpoint).
 */
object Motion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Standard: Easing = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f)
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    const val Fast = 180
    const val Base = 280
    const val Slow = 520
    const val Cinematic = 900

    fun <T> enter(delay: Int = 0, duration: Int = Slow): FiniteAnimationSpec<T> =
        tween(duration, delay, easing = Decelerate)

    fun <T> exit(duration: Int = Fast): FiniteAnimationSpec<T> =
        tween(duration, easing = Accelerate)

    fun <T> move(duration: Int = Base): FiniteAnimationSpec<T> =
        tween(duration, easing = Emphasized)

    fun <T> pulse(duration: Int = 1400): FiniteAnimationSpec<T> =
        tween(duration, easing = FastOutSlowInEasing)

    fun <T> smooth(duration: Int = Base): FiniteAnimationSpec<T> =
        tween(duration, easing = standardEasing)

    fun <T> pop(duration: Int = 420): FiniteAnimationSpec<T> =
        tween(duration, easing = Overshoot)

    fun <T> springy(stiffness: Float = Spring.StiffnessMediumLow, damping: Float = 0.72f) =
        spring<T>(dampingRatio = damping, stiffness = stiffness)

    private const val standardCurve = 0.35f
    private val standardEasing = CubicBezierEasing(0.35f, 0f, 0.15f, 1f)
}

/** Stagger helper: delays items in a list so entrance animation ripples down the screen. */
fun stagger(index: Int, step: Int = 45, max: Int = 420): Int = (index * step).coerceAtMost(max)
