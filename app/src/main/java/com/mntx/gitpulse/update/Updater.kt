package com.mntx.gitpulse.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.BuildConfig
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.Http
import com.mntx.gitpulse.data.model.GhRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Where the published APK lives. Release assets are public — readable without a token. */
object UpdateSource {
    const val OWNER = "MNTGXO"
    const val REPO = "Git-push-android"
    const val RELEASE_TAG = "apk-latest"
}

enum class UpdateStage { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

data class UpdateState(
    val stage: UpdateStage = UpdateStage.IDLE,
    val installedName: String = BuildConfig.APP_VERSION_LABEL,
    val installedCode: Int = BuildConfig.VERSION_CODE,
    val latestName: String? = null,
    val latestTag: String? = null,
    val downloadUrl: String? = null,
    val sizeBytes: Long = 0,
    val progress: Float = 0f,
    val note: String? = null,
    val error: String? = null,
    val apkPath: String? = null,
) {
    val busy: Boolean get() = stage == UpdateStage.CHECKING || stage == UpdateStage.DOWNLOADING
}

/**
 * In-app updater for the GitHub-published APK.
 *
 * The rolling `apk-latest` release exposes assets named `GitPulse-<yyyyMMdd>-<HHmm>-<sha>.apk`,
 * so comparing that stamp against the installed build is enough to decide "is there something
 * newer" with no backend, no Play Store and no signed manifest.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val services = ServiceLocator.get(app)
    private val api = services.api

    val state = MutableStateFlow(UpdateState())

    /** Silent check at most every few hours, so launching stays cheap. */
    fun checkQuietly() {
        val last = services.prefs.lastUpdateCheck
        if (System.currentTimeMillis() - last < TimeUnit.HOURS.toMillis(6)) return
        check()
    }

    fun check() {
        services.prefs.lastUpdateCheck = System.currentTimeMillis()
        state.update { it.copy(stage = UpdateStage.CHECKING, error = null) }
        viewModelScope.launch {
            val release = fetchRollingRelease()
            if (release == null) {
                state.update { it.copy(stage = UpdateStage.FAILED, error = "Couldn't reach the release feed.") }
                return@launch
            }
            val apk = release.assets.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
            if (apk?.browserDownloadUrl == null) {
                state.update { it.copy(stage = UpdateStage.FAILED, error = "The latest release has no APK yet.") }
                return@launch
            }
            val remoteStamp = stampOf(apk.name)
            val installedStamp = stampOf(BuildConfig.APP_VERSION_LABEL)
            val newer = if (remoteStamp != null && installedStamp != null) {
                remoteStamp > installedStamp
            } else {
                // A local build carries no date, so the published release wins.
                true
            }
            state.update {
                it.copy(
                    stage = if (newer) UpdateStage.AVAILABLE else UpdateStage.UP_TO_DATE,
                    latestName = apk.name,
                    latestTag = release.tagName,
                    downloadUrl = apk.browserDownloadUrl,
                    sizeBytes = apk.size,
                    note = release.publishedAt,
                )
            }
        }
    }

    /** Streams the APK into app storage; [install] then hands it to the system installer. */
    fun download() {
        val url = state.value.downloadUrl ?: return
        state.update { it.copy(stage = UpdateStage.DOWNLOADING, progress = 0f, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(services.appContext.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.deleteRecursively() }
                    val target = File(dir, "update.apk")
                    val request = okhttp3.Request.Builder().url(url).build()
                    Http.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("Download failed (HTTP ${response.code}).")
                        val body = response.body ?: error("Empty response from GitHub.")
                        val total = body.contentLength().takeIf { it > 0 } ?: state.value.sizeBytes
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                var copied = 0L
                                var lastEmit = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                    if (total > 0) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastEmit > 120) {
                                            lastEmit = now
                                            state.update {
                                                it.copy(progress = (copied.toFloat() / total).coerceIn(0f, 1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // An APK is a zip: refuse a truncated or HTML-error body before the
                    // installer gets a chance to fail in a way that looks like a crash.
                    if (!target.isZip()) error("The download was incomplete. Try again.")
                    target
                }
            }.onSuccess { apk ->
                state.update { it.copy(stage = UpdateStage.READY, progress = 1f, apkPath = apk.absolutePath) }
            }.onFailure { e ->
                state.update { it.copy(stage = UpdateStage.FAILED, error = e.message ?: "Download failed.") }
            }
        }
    }

    /** Opens the system installer, requesting the "install unknown apps" grant when needed. */
    fun install() {
        val path = state.value.apkPath ?: return
        val context = getApplication<Application>()
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                state.update { it.copy(error = "Allow installs from GitPulse, then tap Install again.") }
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        if (result.isFailure) {
            state.update {
                it.copy(
                    stage = UpdateStage.FAILED,
                    error = "Couldn't open the installer. Uninstall the older copy and retry, " +
                        "or download the APK from the Releases page.",
                )
            }
        }
    }

    fun dismiss() = state.update { it.copy(stage = UpdateStage.IDLE, error = null) }

    /** Public tag endpoint first (works signed-out), authenticated listing as a fallback. */
    private suspend fun fetchRollingRelease(): GhRelease? {
        val direct = runCatching {
            withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder()
                    .url(
                        "https://api.github.com/repos/${UpdateSource.OWNER}/${UpdateSource.REPO}" +
                            "/releases/tags/${UpdateSource.RELEASE_TAG}"
                    )
                    .header("Accept", "application/vnd.github+json")
                    .build()
                Http.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    Http.json.decodeFromString(GhRelease.serializer(), response.body?.string().orEmpty())
                }
            }
        }.getOrNull()
        if (direct != null) return direct
        return runCatching {
            api.releases(UpdateSource.OWNER, UpdateSource.REPO)
                .firstOrNull { it.tagName == UpdateSource.RELEASE_TAG }
        }.getOrNull()
    }

    private fun File.isZip(): Boolean = runCatching {
        inputStream().use { stream ->
            val header = ByteArray(4)
            stream.read(header) == 4 &&
                header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    companion object {
        /**
         * `yyyyMMddHHmm` from `GitPulse-20261127-0942-8ebe86c.apk` (or any name carrying a
         * date-time pair). Null when there is no stamp to compare.
         */
        fun stampOf(name: String?): String? {
            if (name == null) return null
            val match = Regex("(\\d{8})-(\\d{4})").find(name) ?: return null
            return match.groupValues[1] + match.groupValues[2]
        }
    }
}

/** True when this context may start an APK install (always true below Android 8). */
fun Context.canInstallPackages(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
