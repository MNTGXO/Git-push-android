package com.mntx.gitpulse

import android.content.Context
import com.mntx.gitpulse.data.GitHubApi
import com.mntx.gitpulse.data.Prefs
import com.mntx.gitpulse.data.SecureStore
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.sync.HashCache
import com.mntx.gitpulse.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A deliberately small, hand-rolled dependency container.
 *
 * Why not Hilt? The app has one graph, no scoping puzzles, and hand-rolled wiring keeps the
 * cold start fast and the Gradle build free of annotation processors.
 */
class ServiceLocator(context: Context) {

    val appContext: Context = context.applicationContext
    val secure: SecureStore = SecureStore(appContext)
    val prefs: Prefs = Prefs(appContext)
    val hashCache = HashCache()

    /** Hot token so the API client can rotate credentials without being rebuilt. */
    private val tokenState = MutableStateFlow(runCatching { secure.getToken() }.getOrNull())

    val api: GitHubApi = GitHubApi(tokenProvider = { tokenState.value })
    val sync: SyncEngine = SyncEngine(api, hashCache)

    private val sessionState = MutableStateFlow<Session>(Session.SignedOut)
    val session: StateFlow<Session> = sessionState.asStateFlow()

    init {
        sessionState.value = if (tokenState.value.isNullOrBlank()) Session.SignedOut
        else Session.Restoring
    }

    sealed interface Session {
        data object SignedOut : Session
        data object Restoring : Session
        data class SignedIn(val token: String, val user: GhUser, val scopes: List<String> = emptyList()) : Session
        data class Error(val message: String) : Session
    }

    fun signIn(token: String, user: GhUser, scopes: List<String> = emptyList()) {
        secure.putToken(token)
        tokenState.value = token
        sessionState.value = Session.SignedIn(token, user, scopes)
    }

    fun updateUser(user: GhUser) {
        val current = sessionState.value
        if (current is Session.SignedIn) sessionState.value = current.copy(user = user)
    }

    fun signOut() {
        secure.clearToken()
        tokenState.value = null
        sessionState.value = Session.SignedOut
    }

    fun markRestoreFailed(message: String) {
        sessionState.value = Session.Error(message)
    }

    fun finishRestore(user: GhUser) {
        val token = tokenState.value ?: return
        if (sessionState.value is Session.SignedIn) return
        sessionState.value = Session.SignedIn(token, user)
    }

    fun currentUser(): GhUser? = (sessionState.value as? Session.SignedIn)?.user
    fun currentToken(): String? = tokenState.value

    /** Scratch space for extracted archives / pulled repos. */
    val workspace: File by lazy {
        File(appContext.cacheDir, "workspace").apply { mkdirs() }
    }

    fun workspaceDir(name: String): File = File(workspace, name.sanitize()).apply { mkdirs() }

    private fun String.sanitize(): String =
        replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "workspace" }

    companion object {
        @Volatile
        private var INSTANCE: ServiceLocator? = null

        fun get(context: Context): ServiceLocator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServiceLocator(context).also { INSTANCE = it }
            }

        fun peek(): ServiceLocator = INSTANCE
            ?: error("ServiceLocator not initialised — call GitPulseApp first.")
    }
}
