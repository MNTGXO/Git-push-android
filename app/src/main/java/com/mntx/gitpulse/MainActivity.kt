package com.mntx.gitpulse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.ui.AppViewModel
import com.mntx.gitpulse.ui.GitPulseRoot
import com.mntx.gitpulse.ui.theme.GitPulseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIncoming(intent)

        setContent {
            val appVm: AppViewModel = viewModel()
            val settings by appVm.settings.collectAsStateWithLifecycle()
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize()) {
                    GitPulseRoot(appVm = appVm)
                }
            }
        }
    }

    /** `launchMode=singleTask`, so a re-share arrives here instead of a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(incoming: Intent?) {
        if (incoming == null) return
        val uri = when (incoming.action) {
            Intent.ACTION_SEND ->
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
                }
            Intent.ACTION_VIEW -> incoming.data
            else -> null
        } ?: return
        // A read grant is per-intent, so take it while we still can.
        runCatching {
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ServiceLocator.get(this).acceptIncoming(uri)
    }
}
