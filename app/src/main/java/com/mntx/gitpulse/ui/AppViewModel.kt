package com.mntx.gitpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.ApiException
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: session, repositories, settings and sign-in plumbing. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val services: ServiceLocator = (app as GitPulseApp).services
    val prefs get() = services.prefs

    val session: StateFlow<ServiceLocator.Session> = services.session
    val settings: StateFlow<AppSettings> = services.prefs.settings

    private val _repos = AsyncValue<List<GhRepo>>()
    val repos: StateFlow<Async<List<GhRepo>>> = _repos.state

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    val recentRepos: StateFlow<List<String>> = combine(services.prefs.settings, session) { _, _ ->
        services.prefs.recentRepos
    }.stateIn(viewModelScope, SharingStarted.Eagerly, services.prefs.recentRepos)

    init {
        viewModelScope.launch {
            session.collect { s ->
                if (s is ServiceLocator.Session.SignedIn) refreshRepos()
            }
        }
    }

    fun signIn(token: String) {
        if (_signingIn.value) return
        _signingIn.value = true
        _authError.value = null
        viewModelScope.launch {
            runCatching { services.api.validateToken(token) }
                .onSuccess { user ->
                    services.signIn(token, user)
                    refreshRepos()
                }
                .onFailure { e ->
                    _authError.value = when {
                        e is ApiException && e.isAuth -> "GitHub rejected that token. Check it has repo + workflow scopes."
                        else -> e.message ?: "Couldn't verify the token. Check your connection."
                    }
                }
            _signingIn.value = false
        }
    }

    fun signOut() {
        services.signOut()
        _repos.set(Async.Idle)
        _authError.value = null
    }

    fun clearAuthError() { _authError.value = null }

    fun refreshRepos() {
        viewModelScope.launch {
            _repos.load { services.api.repositories(perPage = 100) }
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            runCatching { services.api.currentUser() }
                .onSuccess { services.updateUser(it) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = services.prefs.update(transform)

    val currentUser: GhUser? get() = services.currentUser()
}
