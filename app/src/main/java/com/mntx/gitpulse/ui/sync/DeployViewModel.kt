package com.mntx.gitpulse.ui.sync

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.ApiException
import com.mntx.gitpulse.sync.ArchiveExtractor
import com.mntx.gitpulse.sync.DiffSummary
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.sync.LogLevel
import com.mntx.gitpulse.sync.ManifestBuilder
import com.mntx.gitpulse.sync.PushOptions
import com.mntx.gitpulse.sync.SyncEvent
import com.mntx.gitpulse.sync.humanSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DeployStage { IDLE, ANALYSING, REVIEW, PUSHING, DONE, FAILED }

data class DeployState(
    val stage: DeployStage = DeployStage.IDLE,
    val target: String = "",
    val branch: String = "main",
    val subPath: String = "",
    val message: String = "",
    val deleteMissing: Boolean = false,
    val manifest: LocalManifest? = null,
    val diff: DiffSummary? = null,
    val sourceName: String = "",
    val progress: Float = 0f,
    val progressLabel: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val logs: List<LogLine> = emptyList(),
    val error: String? = null,
    val commitSha: String? = null,
    val resultMessage: String? = null,
) {
    val owner: String get() = target.substringBefore('/')
    val repo: String get() = target.substringAfter('/', "")
    val targetValid: Boolean get() = owner.isNotBlank() && repo.isNotBlank() && !repo.contains('/')
    val ready: Boolean get() = manifest != null && targetValid && branch.isNotBlank()
}

/**
 * "Deploy" flow: push a local build to a repository the user *types*, without cloning and
 * without it needing to appear in the repo picker (works for org repos, other accounts and
 * GitHub Enterprise hosts).
 *
 * Unlike the zip-mirror flow this defaults to **additive** pushes — a deploy should not delete
 * files unless the user explicitly opts into mirror mode.
 */
class DeployViewModel(app: Application) : AndroidViewModel(app) {

    private val services = ServiceLocator.get(app)
    private val api = services.api
    private val engine = services.sync
    private val prefs = services.prefs
    private val extractor = ArchiveExtractor()

    private val _state = MutableStateFlow(
        DeployState(
            target = prefs.lastDeployTarget,
            subPath = prefs.lastDeploySubPath,
            message = prefs.lastCommitPrefix,
        )
    )
    val state: StateFlow<DeployState> = _state.asStateFlow()

    private var job: Job? = null
    private var workDir: File? = null

    fun setTarget(value: String) = _state.update { it.copy(target = value.trim()) }
    fun setBranch(value: String) = _state.update { it.copy(branch = value.trim()) }
    fun setSubPath(value: String) = _state.update { it.copy(subPath = com.mntx.gitpulse.sync.SyncPath.normalise(value)) }
    fun setMessage(value: String) = _state.update { it.copy(message = value) }
    fun setDeleteMissing(value: Boolean) {
        _state.update { it.copy(deleteMissing = value) }
        if (_state.value.manifest != null) analyse()
    }

    fun onArchivePicked(uri: Uri, name: String?) {
        val context = getApplication<Application>()
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = DeployStage.ANALYSING,
                    sourceName = name ?: "archive.zip",
                    error = null,
                    progress = 0f,
                    progressLabel = "Opening archive…",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val dest = services.workspaceDir("deploy_${System.currentTimeMillis()}")
                    workDir = dest
                    val stream = context.contentResolver.openInputStream(uri) ?: error("Couldn't open that file.")
                    extractor.extract(
                        source = stream,
                        destination = dest,
                        onProgress = { files, bytes, _ ->
                            _state.update {
                                it.copy(progressLabel = "$files files · ${humanSize(bytes)}", current = files)
                            }
                        },
                    )
                    ManifestBuilder.fromDirectory(dest)
                }
            }.onSuccess { manifest ->
                _state.update {
                    it.copy(
                        manifest = manifest,
                        sourceName = it.sourceName,
                        progressLabel = "",
                        diff = null,
                    )
                }
                persist()
                analyse()
            }.onFailure { e ->
                _state.update { it.copy(stage = DeployStage.FAILED, error = e.message ?: "Could not read the archive.") }
            }
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        val context = getApplication<Application>()
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(stage = DeployStage.ANALYSING, sourceName = "Selected folder", error = null, progressLabel = "Reading folder…")
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val dest = services.workspaceDir("deploy_tree_${System.currentTimeMillis()}")
                    workDir = dest
                    copyTree(context, treeUri, dest)
                    ManifestBuilder.fromDirectory(dest)
                }
            }.onSuccess { manifest ->
                _state.update { it.copy(manifest = manifest, progressLabel = "", diff = null) }
                persist()
                analyse()
            }.onFailure { e ->
                _state.update { it.copy(stage = DeployStage.FAILED, error = e.message ?: "Could not read the folder.") }
            }
        }
    }

    /** Reads the target branch (if it exists) and produces the plan. */
    fun analyse() {
        val snapshot = _state.value
        val manifest = snapshot.manifest ?: return
        if (!snapshot.targetValid) {
            _state.update { it.copy(stage = DeployStage.IDLE, error = "Enter the destination as owner/repo") }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(stage = DeployStage.ANALYSING, progressLabel = "Comparing with ${it.target}…", error = null)
            }
            val branch = _state.value.branch.ifBlank { "main" }
            runCatching {
                engine.plan(
                    manifest = manifest,
                    owner = _state.value.owner,
                    repo = _state.value.repo,
                    branch = branch,
                    subPath = _state.value.subPath,
                )
            }.onSuccess { (_, diff) ->
                _state.update { it.copy(diff = diff, stage = DeployStage.REVIEW, progressLabel = "") }
            }.onFailure { e ->
                val missingBranch = (e is ApiException && (e.isNotFound || e.isConflict))
                if (missingBranch) {
                    // New repo/branch: everything is an add, nothing can be pruned.
                    val diff = com.mntx.gitpulse.sync.DiffEngine.diff(manifest.reRoot(_state.value.subPath), emptyMap())
                    _state.update {
                        it.copy(
                            diff = diff,
                            stage = DeployStage.REVIEW,
                            progressLabel = "",
                            logs = it.logs + LogLine("Branch '$branch' doesn't exist yet — it will be created.", LogLevel.INFO),
                        )
                    }
                } else {
                    _state.update { it.copy(stage = DeployStage.FAILED, error = friendly(e)) }
                }
            }
        }
    }

    fun push() {
        val snapshot = _state.value
        val manifest = snapshot.manifest ?: return
        if (!snapshot.targetValid) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = DeployStage.PUSHING,
                    progress = 0f,
                    logs = emptyList(),
                    error = null,
                    resultMessage = null,
                    commitSha = null,
                )
            }
            persist()
            engine.push(
                manifest = manifest,
                options = PushOptions(
                    owner = snapshot.owner,
                    repo = snapshot.repo,
                    branch = snapshot.branch.ifBlank { "main" },
                    message = snapshot.message.ifBlank { "deploy: ${snapshot.sourceName}" },
                    deleteMissing = snapshot.deleteMissing,
                    authorName = prefs.settings.value.defaultAuthorName,
                    authorEmail = prefs.settings.value.defaultAuthorEmail,
                    subPath = snapshot.subPath,
                ),
            ).collect { event ->
                when (event) {
                    is SyncEvent.Phase -> _state.update {
                        it.copy(logs = it.logs + LogLine(event.label, LogLevel.INFO), progressLabel = event.label)
                    }
                    is SyncEvent.Progress -> _state.update {
                        it.copy(
                            current = event.current,
                            total = event.total,
                            progress = event.fraction,
                            progressLabel = event.detail.ifBlank { it.progressLabel },
                        )
                    }
                    is SyncEvent.Log -> _state.update {
                        it.copy(logs = it.logs + LogLine(event.message, event.level))
                    }
                    is SyncEvent.Success -> _state.update {
                        it.copy(
                            stage = DeployStage.DONE,
                            progress = 1f,
                            commitSha = event.commitSha,
                            resultMessage = event.message,
                            logs = it.logs + LogLine(event.message, LogLevel.SUCCESS),
                        )
                    }
                    is SyncEvent.Failure -> _state.update {
                        it.copy(stage = DeployStage.FAILED, error = event.message, logs = it.logs + LogLine(event.message, LogLevel.ERROR))
                    }
                    SyncEvent.Cancelled -> _state.update { it.copy(stage = DeployStage.REVIEW) }
                }
            }
        }
    }

    fun reset() {
        job?.cancel()
        workDir?.let { runCatching { it.deleteRecursively() } }
        workDir = null
        _state.update {
            it.copy(
                stage = DeployStage.IDLE,
                manifest = null,
                diff = null,
                progress = 0f,
                progressLabel = "",
                logs = emptyList(),
                error = null,
                commitSha = null,
                resultMessage = null,
                current = 0,
                total = 0,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun persist() {
        prefs.lastDeployTarget = _state.value.target
        prefs.lastDeploySubPath = _state.value.subPath
        prefs.lastCommitPrefix = _state.value.message.ifBlank { prefs.lastCommitPrefix }
    }

    private fun copyTree(context: android.content.Context, root: Uri, dest: File) {
        val resolver = context.contentResolver
        dest.mkdirs()
        var count = 0
        val stack = ArrayDeque<Pair<String, Uri>>()
        stack.addLast("" to root)
        while (stack.isNotEmpty()) {
            val (prefix, current) = stack.removeLast()
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                current,
                android.provider.DocumentsContract.getTreeDocumentId(current),
            )
            val children = resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                val out = mutableListOf<Triple<String, String, String>>()
                while (cursor.moveToNext()) out += Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                out
            } ?: emptyList()
            for ((docId, name, mime) in children) {
                val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(root, docId)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    File(dest, path).mkdirs()
                    stack.addLast(path to uri)
                } else {
                    val target = File(dest, path)
                    target.parentFile?.mkdirs()
                    runCatching {
                        resolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it, 256 * 1024) }
                        }
                    }
                    count++
                    if (count % 25 == 0) _state.update { it.copy(progressLabel = "$count files copied") }
                }
            }
        }
        if (count == 0) error("That folder is empty or can't be read.")
    }

    private fun friendly(e: Throwable): String = when (e) {
        is ApiException -> when {
            e.isAuth -> "The token can't access ${_state.value.target}."
            e.isNotFound -> "Repository ${_state.value.target} not found (or no access)."
            else -> e.message ?: "GitHub error"
        }
        else -> e.message ?: "Something went wrong"
    }
}
