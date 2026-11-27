package com.mntx.gitpulse.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.mntx.gitpulse.ui.theme.Motion

/**
 * Navigation with a deliberate motion contract:
 *  - root tabs  → cross-fade + tiny vertical drift (feels like switching lenses)
 *  - detail     → push from the right with parallax + fade
 *  - sheets     → slide up from the bottom
 */
class PulseNavigator(val controller: NavHostController) {

    val currentRoute: String? get() = controller.currentDestination?.route

    fun go(route: Route) {
        if (currentRoute == route.path) return
        controller.navigate(route.path) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun push(path: String) = controller.navigate(path)

    fun switchTab(route: Route) {
        controller.navigate(route.path) {
            popUpTo(Route.Home.path) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back() {
        if (!controller.popBackStack()) {
            controller.navigate(Route.Home.path) { launchSingleTop = true }
        }
    }
}

val LocalNavigator = compositionLocalOf<PulseNavigator> { error("No navigator") }

@Composable
fun rememberPulseNavigator(): PulseNavigator {
    val controller = rememberNavController()
    return remember(controller) { PulseNavigator(controller) }
}

@Composable
fun ProvideNavigator(navigator: PulseNavigator, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigator provides navigator, content = content)
}
