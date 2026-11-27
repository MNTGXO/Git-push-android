package com.mntx.gitpulse.ui.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.model.GhBranch
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.sync.ArchiveExtractor
import com.mntx.gitpulse.sync.DiffSummary
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.sync.LogLevel
import com.mntx.gitpulse.sync.ManifestBuilder
import com.mntx.gitpulse.sync.PullOptions
import com.mntx.gitpulse.sync.PushOptions
import com.mntx.gitpulse.sync.RemoteBlob
import com.mntx.gitpulse.sync.SyncEvent
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.navigation.RepoRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SyncStage { IDLE, PICKING, EXTRACTING, DIFFING, REVIEW, PUSHING, PULLING, DONE, FAILED }

data class LogLine(val text: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())

data class SyncUiState(
    val stage: SyncStage = SyncStage.IDLE,
    val repos: List<GhRepo> = emptyList(),
    val branches: List<GhBranch> = emptyList(),
    val selectedRepo: GhRepo? = null,
    val selectedBranch: String = "",
    val commitMessage: String = "",
    val deleteMissing: Boolean = true,
    val dryRun: Boolean = false,
    val manifest: LocalManifest? = null,
    val remoteTree: Map<String, RemoteBlob> = emptyMap(),
    val diff: DiffSummary? = null,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val logs: List<LogLine> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val commitSha: String? = null,
    val lastArchiveName: String? = null,
    val resultDetails: String = "",
    val filterJunk: Boolean = true,
) {
    val readyToPush: Boolean get() = diff != null && !diff.isEmpty && selectedRepo != null && commitMessage.isNotBlank()
    val changeCount: Int get() = diff?.changes?.size ?: 0
}

/**
 * Drives the whole "zip → review → push" and "repo → pull" journeys.
 */
class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val services: ServiceLocator = (app as GitPulseApp).services
    private val api = services.api
    private val engine = services.sync
    private val prefs = services.prefs
    private val extractor = ArchiveExtractor()

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var workJob: Job? = null
    private var archiveDir: File? = null

    init {
        _state.update {
            it.copy(commitMessage = prefs.lastCommitPrefix)
        }
    }

    fun loadRepos() {
        viewModelScope.launch {
            runCatching { api.repositories(perPage = 100) }
                .onSuccess { repos ->
                    _state.update { s ->
                        val preselected = s.selectedRepo ?: repos.firstOrNull { r ->
                            prefs.lastRepo == r.fullName
                        } ?: repos.firstOrNull()
                        s.copy(
                            repos = repos,
                            selectedRepo = preselected,
                            selectedBranch = preselected?.defaultBranch
                                ?: prefs.settings.value.defaultBranch,
                            error = null,
                        )
                    }
                    _state.value.selectedRepo?.let { selectRepo(it, keepBranch = false) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun selectRepo(repo: GhRepo, keepBranch: Boolean = false) {
        _state.update { s ->
            s.copy(
                selectedRepo = repo,
                selectedBranch = if (keepBranch) s.selectedBranch else (repo.defaultBranch ?: "main"),
                diff = null,
                remoteTree = emptyMap(),
                successMessage = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { api.branches(repo.ownerLogin, repo.name) }
                .onSuccess { branches ->
                    _state.update { s ->
                        s.copy(
                            branches = branches,
                            selectedBranch = s.selectedBranch.takeIf { sel ->
                                branches.any { it.name == sel }
                            } ?: branches.firstOrNull { it.name == repo.defaultBranch }?.name
                                ?: branches.firstOrNull()?.name
                                ?: repo.defaultBranch ?: "main",
                        )
                    }
                    recomputeRemote()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun selectBranch(branch: String) {
        _state.update { it.copy(selectedBranch = branch, diff = null) }
        recomputeRemote()
    }

    private fun recomputeRemote() {
        val s = _state.value
        val repo = s.selectedRepo ?: return
        if (_state.value.stage == SyncStage.PUSHING) return
        viewModelScope.launch {
            runCatching { engine.remoteTree(repo.ownerLogin, repo.name, s.selectedBranch) }
                .onSuccess { tree ->
                    _state.update { it.copy(remoteTree = tree) }
                    recomputeDiff()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            remoteTree = emptyMap(),
                            error = if (it.manifest != null) e.message else it.error
                        )
                    }
                    if (_state.value.manifest != null) recomputeDiff()
                }
        }
    }

    /* ------------------------------------------------------------- picking sources */

    fun onZipPicked(uri: Uri, displayName: String?) {
        val context = getApplication<Application>()
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = SyncStage.EXTRACTING,
                    progress = 0f,
                    progressLabel = "Opening archive…",
                    error = null,
                    lastArchiveName = displayName ?: "archive.zip",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val dest = services.workspaceDir("zip_${System.currentTimeMillis()}")
                    archiveDir = dest
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Couldn't open that file.")
                    extractor.extract(
                        source = stream,
                        destination = dest,
                        onProgress = { files, bytes, current ->
                            val total = 1 + files
                            _state.update {
                                it.copy(
                                    progress = (bytes % 1000).toFloat() / 1000f,
                                    progressLabel = "$files files · ${humanSize(bytes)}",
                                    current = files,
                                )
                            }
                        },
                    )
                }
            }.onSuccess { result ->
                val manifest = withContext(Dispatchers.IO) { ManifestBuilder.fromDirectory(result.root) }
                _state.update {
                    it.copy(
                        manifest = manifest,
                        stage = SyncStage.DIFFING,
                        progressLabel = "Comparing with GitHub…",
                        progress = 0.15f,
                        successMessage = null,
                    )
                }
                computeDiff()
            }.onFailure { e ->
                _state.update {
                    it.copy(stage = SyncStage.FAILED, error = e.message ?: "Could not read the archive.")
                }
            }
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        val context = getApplication<Application>()
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = SyncStage.EXTRACTING,
                    progressLabel = "Reading the selected folder…",
                    error = null,
                    lastArchiveName = "Selected folder",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val dest = services.workspaceDir("tree_${System.currentTimeMillis()}")
                    archiveDir = dest
                    copyTree(context, treeUri, dest)
                    dest
                }
            }.onSuccess { dir ->
                val manifest = withContext(Dispatchers.IO) { ManifestBuilder.fromDirectory(dir) }
                _state.update {
                    it.copy(
                        manifest = manifest,
                        stage = SyncStage.DIFFING,
                        progressLabel = "Comparing with GitHub…",
                    )
                }
                computeDiff()
            }.onFailure { e ->
                _state.update {
                    it.copy(stage = SyncStage.FAILED, error = e.message ?: "Could not read the folder.")
                }
            }
        }
    }

    /** Recursively copies a SAF document tree into app storage (so we own stable File handles). */
    private fun copyTree(context: android.content.Context, root: Uri, dest: File) {
        val resolver = context.contentResolver
        dest.mkdirs()
        val stack = ArrayDeque<Pair<String, Uri>>()
        stack.addLast("" to root)
        var count = 0
        while (stack.isNotEmpty()) {
            val (prefix, current) = stack.removeLast()
            val children = resolver.query(current, null, null, null, null)?.use { cursor ->
                val cols = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val list = mutableListOf<Pair<String, String>>()
                while (cursor.moveToNext()) {
                    list += cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty()
                }
                list
            } ?: continue
            children.forEach { (name, mime) ->
                val childUri = Uri.parse("$current/${Uri.encode(name)}")
                val childPrefix = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    File(dest, childPrefix).mkdirs()
                    stack.addLast(childPrefix to childUri)
                } else {
                    val target = File(dest, childPrefix)
                    target.parentFile?.mkdirs()
                    runCatching {
                        resolver.openInputStream(childUri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                        }
                    }
                    count++
                    if (count % 20 == 0) {
                        _state.update { it.copy(progressLabel = "$count files copied", current = count) }
                    }
                }
            }
        }
        if (count == 0) error("That folder is empty or inaccessible.")
    }

    /* ------------------------------------------------------------- diff */

    fun computeDiff() {
        val s = _state.value
        val manifest = s.manifest ?: return
        val repo = s.selectedRepo ?: run {
            _state.update { it.copy(stage = SyncStage.REVIEW, progress = 1f) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(stage = SyncStage.DIFFING, progress = 0.1f, progressLabel = "Reading remote tree…", error = null)
            }
            runCatching {
                val remote = if (_state.value.remoteTree.isNotEmpty()) _state.value.remoteTree
                else engine.remoteTree(repo.ownerLogin, repo.name, s.selectedBranch)
                _state.update { it.copy(remoteTree = remote) }
                engine.push(
                    manifest = manifest,
                    options = PushOptions(
                        owner = repo.ownerLogin,
                        repo = repo.name,
                        branch = s.selectedBranch,
                        message = "dry run",
                        dryRun = true,
                        deleteMissing = s.deleteMissing,
                    ),
                ).let { flow ->
                    // Dry run gives us an exact plan including deletes, using the real engine.
                    var plan: DiffSummary? = null
                    flow.collect { event ->
                        when (event) {
                            is SyncEvent.Success -> plan = _state.value.diff
                            else -> Unit
                        }
                    }
                    plan
                }
                remote
            }.onSuccess { remote ->
                val diff = com.mntx.gitpulse.sync.DiffEngine.diff(manifest, remote)
                _state.update {
                    it.copy(
                        diff = diff,
                        stage = SyncStage.REVIEW,
                        progress = 1f,
                        progressLabel = "",
                        error = null,
                    )
                }
            }.onFailure { e ->
                // Offline / empty repo: still show a local plan so the user can push.
                val diff = com.mntx.gitpulse.sync.DiffEngine.diff(manifest, _state.value.remoteTree)
                _state.update {
                    it.copy(
                        diff = diff,
                        stage = SyncStage.REVIEW,
                        progress = 1f,
                        error = if (_state.value.remoteTree.isEmpty()) null else e.message,
                    )
                }
            }
        }
    }

    /* ------------------------------------------------------------- push */

    fun push() {
        val s = _state.value
        val repo = s.selectedRepo ?: return
        val manifest = s.manifest ?: return
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = SyncStage.PUSHING,
                    progress = 0f,
                    logs = emptyList(),
                    error = null,
                    successMessage = null,
                )
            }
            prefs.lastCommitPrefix = s.commitMessage
            prefs.lastRepo = repo.fullName
            val authorName = prefs.settings.value.defaultAuthorName.ifBlank { s.commitMessage }
            engine.push(
                manifest = manifest,
                options = PushOptions(
                    owner = repo.ownerLogin,
                    repo = repo.name,
                    branch = s.selectedBranch,
                    message = s.commitMessage,
                    deleteMissing = s.deleteMissing,
                    authorName = prefs.settings.value.defaultAuthorName,
                    authorEmail = prefs.settings.value.defaultAuthorEmail,
                    dryRun = s.dryRun,
                ),
            ).collect { event ->
                when (event) {
                    is SyncEvent.Phase -> _state.update { st ->
                        st.copy(logs = st.logs + LogLine(event.label, LogLevel.INFO), progressLabel = event.label)
                    }
                    is SyncEvent.Progress -> _state.update { st ->
                        st.copy(
                            current = event.current,
                            total = event.total,
                            progress = if (event.total == 0) 0f else event.current.toFloat() / event.total,
                            progressLabel = event.detail.ifBlank { st.progressLabel },
                        )
                    }
                    is SyncEvent.Log -> _state.update { st ->
                        st.copy(logs = st.logs + LogLine(event.message, event.level))
                    }
                    is SyncEvent.Success -> {
                        _state.update { st ->
                            st.copy(
                                stage = SyncStage.DONE,
                                progress = 1f,
                                successMessage = event.message,
                                commitSha = event.commitSha,
                                resultDetails = event.details,
                                logs = st.logs + LogLine(event.message, LogLevel.SUCCESS),
                            )
                        }
                        prefs.recentRepos = listOf(repo.fullName) + prefs.recentRepos
                        // refresh the remote tree after a successful push
                        runCatching { engine.remoteTree(repo.ownerLogin, repo.name, s.selectedBranch) }
                            .onSuccess { tree -> _state.update { it.copy(remoteTree = tree) } }
                    }
                    is SyncEvent.Failure -> _state.update { st ->
                        st.copy(
                            stage = SyncStage.FAILED,
                            error = event.message,
                            logs = st.logs + LogLine(event.message, LogLevel.ERROR),
                        )
                    }
                    SyncEvent.Cancelled -> _state.update {
                        it.copy(stage = SyncStage.REVIEW, progressLabel = "Cancelled")
                    }
                }
            }
        }
    }

    fun cancel() {
        workJob?.cancel()
        _state.update { it.copy(stage = SyncStage.REVIEW, progressLabel = "Cancelled") }
    }

    /* ------------------------------------------------------------- pull */

    fun reset() {
        workJob?.cancel()
        archiveDir?.let { runCatching { it.deleteRecursively() } }
        archiveDir = null
        _state.update {
            it.copy(
                stage = SyncStage.IDLE,
                manifest = null,
                diff = null,
                progress = 0f,
                progressLabel = "",
                logs = emptyList(),
                error = null,
                successMessage = null,
                commitSha = null,
                lastArchiveName = null,
            )
        }
    }

    fun setMessage(message: String) = _state.update { it.copy(commitMessage = message) }
    fun setDeleteMissing(enabled: Boolean) {
        _state.update { it.copy(deleteMissing = enabled) }
        recomputeDiff()
    }
    fun setDryRun(enabled: Boolean) = _state.update { it.copy(dryRun = enabled) }
    fun setFilterJunk(enabled: Boolean) = _state.update { it.copy(filterJunk = enabled) }
    fun clearError() = _state.update { it.copy(error = null) }
}
