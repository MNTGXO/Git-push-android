package com.mntx.gitpulse

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
}
