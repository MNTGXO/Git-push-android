package com.mntx.gitpulse

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GitPulseApp : Application() {

    /** Created lazily so headless/render environments without an Android Keystore still work. */
    val services: ServiceLocator by lazy { ServiceLocator(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashCatcher.install(this)
        appScope.launch(Dispatchers.IO) { services.pruneWorkspace() }
        appScope.launch { runCatching { restoreSession() } }
    }

    /** Restores a stored token: validates it with GitHub before showing the dashboard. */
    private suspend fun restoreSession() {
        val token = services.secure.getToken()
        if (token.isNullOrBlank()) return
        runCatching { services.api.validateToken(token) }
            .onSuccess { user -> services.signIn(token, user) }
            .onFailure { e ->
                val message = e.message.orEmpty()
                if (message.contains("401") || message.contains("rejected")) {
                    services.signOut()
                } else {
                    services.markRestoreFailed(message.ifBlank { "Couldn't reach GitHub" })
                }
            }
    }

    companion object {
        lateinit var instance: GitPulseApp
            private set
    }
}
