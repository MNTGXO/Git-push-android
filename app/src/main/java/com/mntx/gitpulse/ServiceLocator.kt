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

    @Volatile
    private var apiBase: String = prefs.settings.value.apiBaseUrl

    @Volatile
    private var apiRef: GitHubApi = buildApi()

    /** Always the client for the *current* host; rebuilt when Settings switches GitHub/GHES. */
    val api: GitHubApi get() = maybeRebuild().apiRef
    // Follows the token/host accessors above, never a snapshot of them.
    val sync: SyncEngine by lazy { SyncEngine(api, hashCache) }

    private fun buildApi(): GitHubApi = GitHubApi(
        tokenProvider = { tokenState.value },
        baseUrl = apiBase.ifBlank { "https://api.github.com" },
    )

    private fun maybeRebuild(): ServiceLocator {
        val wanted = prefs.settings.value.apiBaseUrl
        if (wanted != apiBase) {
            synchronized(this) {
                if (wanted != apiBase) {
                    apiBase = wanted
                    apiRef = buildApi()
                    android.util.Log.i("GitPulse", "API host → ${wanted.ifBlank { "github.com" }}")
                }
            }
        }
        return this
    }

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
        prefs.lastLogin = user.login
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
        prefs.lastLogin = user.login
        sessionState.value = Session.SignedIn(token, user)
    }

    fun currentUser(): GhUser? = (sessionState.value as? Session.SignedIn)?.user
    fun currentToken(): String? = tokenState.value

    /**
     * Scratch space for extracted archives / pulled repos.
     *
     * **Must not live in `cacheDir`.** Android reclaims cache under storage pressure and whenever
     * the app is updated — i.e. exactly *while* a multi-MB archive is being extracted — which
     * used to make the extraction job die mid-flight and look like an instant crash. A subtree of
     * `filesDir` survives both; we just have to prune it ourselves.
     */
    val workspace: File by lazy {
        File(appContext.filesDir, "workspace").apply { mkdirs() }
    }

    /** An archive handed to us by another app (share/open), consumed once by the sync flow. */
    private val incomingArchive = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val incoming: kotlinx.coroutines.flow.StateFlow<android.net.Uri?> = incomingArchive

    fun acceptIncoming(uri: android.net.Uri?) {
        if (uri != null) incomingArchive.value = uri
    }

    fun consumeIncoming(): android.net.Uri? = incomingArchive.getAndSet(null)

    /** Deletes stale workspaces (older than a day) so the new home doesn't grow forever. */
    fun pruneWorkspace(maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        runCatching {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            workspace.listFiles()?.forEach { dir ->
                if (dir.lastModified() < cutoff) dir.deleteRecursively()
            }
        }
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
