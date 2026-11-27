package com.mntx.gitpulse.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mntx.gitpulse.ui.theme.AccentPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val darkMode: Boolean = true,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val accent: AccentPreset = AccentPreset.VIOLET,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val biometricLock: Boolean = false,
    val defaultBranch: String = "main",
    val defaultAuthorName: String = "",
    val defaultAuthorEmail: String = "",
    val autoSyncOnResume: Boolean = false,
    val confirmBeforePush: Boolean = true,
    val showDiffOnPush: Boolean = true,
    /** "" = github.com, otherwise a GitHub Enterprise base (e.g. https://ghe.acme.com/api/v3). */
    val apiBaseUrl: String = "",
)

/** Plain (non-secret) preference store exposed as a hot StateFlow for Compose. */
class Prefs(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("gitpulse_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun read(): AppSettings = AppSettings(
        darkMode = sp.getBoolean("dark", true),
        amoled = sp.getBoolean("amoled", false),
        dynamicColor = sp.getBoolean("dynamic", false),
        accent = runCatching { AccentPreset.valueOf(sp.getString("accent", "VIOLET")!!) }
            .getOrDefault(AccentPreset.VIOLET),
        haptics = sp.getBoolean("haptics", true),
        reduceMotion = sp.getBoolean("reduce_motion", false),
        biometricLock = sp.getBoolean("bio_lock", false),
        defaultBranch = sp.getString("default_branch", "main") ?: "main",
        defaultAuthorName = sp.getString("author_name", "") ?: "",
        defaultAuthorEmail = sp.getString("author_email", "") ?: "",
        autoSyncOnResume = sp.getBoolean("auto_sync", false),
        confirmBeforePush = sp.getBoolean("confirm_push", true),
        showDiffOnPush = sp.getBoolean("show_diff", true),
        apiBaseUrl = sp.getString("api_base", "") ?: "",
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        sp.edit {
            putBoolean("dark", next.darkMode)
            putBoolean("amoled", next.amoled)
            putBoolean("dynamic", next.dynamicColor)
            putString("accent", next.accent.name)
            putBoolean("haptics", next.haptics)
            putBoolean("reduce_motion", next.reduceMotion)
            putBoolean("bio_lock", next.biometricLock)
            putString("default_branch", next.defaultBranch)
            putString("author_name", next.defaultAuthorName)
            putString("author_email", next.defaultAuthorEmail)
            putBoolean("auto_sync", next.autoSyncOnResume)
            putBoolean("confirm_push", next.confirmBeforePush)
            putBoolean("show_diff", next.showDiffOnPush)
            putString("api_base", next.apiBaseUrl)
        }
        _settings.value = next
    }

    var lastRepo: String?
        get() = sp.getString("last_repo", null)
        set(value) = sp.edit { putString("last_repo", value) }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit { putBoolean("onboarded", value) }

    /** Recently opened repos, most recent first. */
    var recentRepos: List<String>
        get() = sp.getString("recent_repos", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = sp.edit { putString("recent_repos", value.distinct().take(8).joinToString("|")) }

    /** Login of the signed-in account, so flows can tell "my repos" from org repos. */
    var lastLogin: String
        get() = sp.getString("last_login", "") ?: ""
        set(value) = sp.edit { putString("last_login", value) }

    /** Epoch millis of the last update check; 0 = never. */
    var lastUpdateCheck: Long
        get() = sp.getLong("update_check", 0L)
        set(value) = sp.edit { putLong("update_check", value) }

    /** Last repo target used by the manual deploy screen ("owner/repo"). */
    var lastDeployTarget: String
        get() = sp.getString("deploy_target", "") ?: ""
        set(value) = sp.edit { putString("deploy_target", value) }

    /** Last sub-folder used by the deploy screen ("deploy into docs/"). */
    var lastDeploySubPath: String
        get() = sp.getString("deploy_sub", "") ?: ""
        set(value) = sp.edit { putString("deploy_sub", value) }

    /** Last commit message per repo, so batches feel continuous. */
    var lastCommitPrefix: String
        get() = sp.getString("commit_prefix", "chore: sync from GitPulse") ?: "chore: sync from GitPulse"
        set(value) = sp.edit { putString("commit_prefix", value) }
}
