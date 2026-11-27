package com.mntx.gitpulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.ui.auth.SignInScreen
import com.mntx.gitpulse.ui.components.PulseLogo
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.displayStyle

/**
 * Top-level state machine: restoring → signed-out → signed-in shelf.
 * Each phase cross-fades so the app never flashes empty surfaces.
 */
@Composable
fun GitPulseRoot(appVm: AppViewModel = viewModel()) {
    val session by appVm.session.collectAsStateWithLifecycle()
    val signingIn by appVm.signingIn.collectAsStateWithLifecycle()
    val authError by appVm.authError.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (session) {
            is ServiceLocator.Session.Restoring -> RestorePhase()
            is ServiceLocator.Session.SignedOut, is ServiceLocator.Session.Error -> SignInScreen(
                loading = signingIn,
                error = authError ?: (session as? ServiceLocator.Session.Error)?.message,
                onSignIn = appVm::signIn,
            )
            is ServiceLocator.Session.SignedIn -> {
                val signedIn = session as ServiceLocator.Session.SignedIn
                MainShell(
                    user = signedIn.user,
                    appVm = appVm,
                )
            }
        }
    }
}

@Composable
private fun RestorePhase() {
    val colors = PulseTheme.colors
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulseLogo(size = 64.dp, animateIn = true)
            Spacer(Modifier.height(26.dp))
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(500))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "GitPulse",
                        style = displayStyle(24, FontWeight.Bold, letterSpacing = -1.0),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(14.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}
