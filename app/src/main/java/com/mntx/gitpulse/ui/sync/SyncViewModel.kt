package com.mntx.gitpulse.ui.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    /** Push INTO this repo folder ("" = mirror the whole repo root). */
    val subPath: String = "",
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

    private val services: ServiceLocator = ServiceLocator.get(app)
    private val api = services.api
    private val engine = services.sync
    private val prefs = services.prefs
    private val extractor = ArchiveExtractor()

    private val _state = MutableStateFlow(SyncUiState())

    /** Preselected by a "recent sync" chip on Home before this screen asks for repos. */
    private var pendingTarget: String? = null

    /**
     * Deep-links the flow to a repository: the chip on Home means "sync *this* repo", so the
     * picker opens already pointing at it instead of defaulting to the most recent repo.
     */
    fun startFor(ownerWithRepo: String) {
        pendingTarget = ownerWithRepo
    }
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var workJob: Job? = null
    private var archiveDir: File? = null

    init {
        _state.update {
            it.copy(commitMessage = prefs.lastCommitPrefix)
        }
    }

    fun loadRepos(target: String? = null) {
        if (!target.isNullOrBlank()) pendingTarget = target
        viewModelScope.launch {
            runCatching { api.repositories(perPage = 100) }
                .onSuccess { repos ->
                    _state.update { s ->
                        val wanted = pendingTarget
                        pendingTarget = null
                        val preselected = s.selectedRepo
                            ?: wanted?.let { full -> repos.firstOrNull { it.fullName.equals(full, true) } }
                            ?: repos.firstOrNull { r -> prefs.lastRepo == r.fullName }
                            ?: repos.firstOrNull()
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

    fun onZipPicked(uri: Uri, displayName: String?, subPath: String = _state.value.subPath) {
        val context = getApplication<Application>()
        workJob?.cancel()
        _state.update { it.copy(subPath = com.mntx.gitpulse.sync.SyncPath.normalise(subPath)) }
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
                    archiveDir?.let { dir -> runCatching { dir.listFiles()?.forEach { it.deleteRecursively() } } }
                    var expectedFiles = 0
                    runCatching { expectedFiles = countZipEntries(context, uri) }
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("That file couldn't be opened. Pick it again from storage.")
                    // Reject a non-zip early with a real message instead of failing inside the loop.
                    java.io.BufferedInputStream(stream).use { buffered ->
                        buffered.mark(8)
                        val header = ByteArray(4)
                        val read = buffered.read(header)
                        buffered.reset()
                        if (read < 4 || !com.mntx.gitpulse.sync.ArchiveExtractor.looksLikeZip(header)) {
                            error("That doesn't look like a .zip archive. Pick the zip file itself, not a folder or an APK.")
                        }
                            extractor.extract(
                        source = buffered,
                        destination = dest,
                        onProgress = { files, bytes, _ ->
                            // Monotonic 0.02…0.98 while extracting; Diffing/Uploading own the rest.
                            val fraction = if (expectedFiles > 0) {
                                (files.toFloat() / expectedFiles).coerceIn(0f, 1f)
                            } else {
                                (files / 400f).coerceIn(0f, 1f)
                            }
                            _state.update {
                                it.copy(
                                    progress = 0.02f + fraction * 0.96f,
                                    progressLabel = if (expectedFiles > 0) {
                                        "$files / $expectedFiles files · ${humanSize(bytes)}"
                                    } else {
                                        "$files files · ${humanSize(bytes)}"
                                    },
                                    current = files,
                                )
                            }
                        },
                    )
                    }
                }
            }.mapCatching { result ->
                result to withContext(Dispatchers.IO) { ManifestBuilder.fromDirectory(result.root) }
            }.onSuccess { (result, manifest) ->
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

    /**
     * Counts entries by walking the zip's central directory. Cheap (no decompression) and safe:
     * any malformed archive simply returns 0, which falls back to a file-count estimate.
     */
    private fun countZipEntries(context: android.content.Context, uri: Uri): Int {
        // Content URIs can't seek, so the bytes are read once here (archives are typically a few
        // MB) and only the central directory is inspected.
        return runCatching {
            val all = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return 0
            if (all.size < 22) return 0
            // End-of-central-directory signature 0x06054b50, scanned backwards over the comment.
            var i = all.size - 22
            val lowest = (all.size - 22 - 65535).coerceAtLeast(0)
            while (i >= lowest) {
                if (all[i] == 0x50.toByte() && all[i + 1] == 0x4b.toByte() &&
                    all[i + 2] == 0x05.toByte() && all[i + 3] == 0x06.toByte()
                ) {
                    val total = (all[i + 10].toInt() and 0xFF) or ((all[i + 11].toInt() and 0xFF) shl 8)
                    return total
                }
                i--
            }
            0
        }.getOrDefault(0)
    }

    fun onFolderPicked(treeUri: Uri, subPath: String = _state.value.subPath) {
        val context = getApplication<Application>()
        workJob?.cancel()
        _state.update { it.copy(subPath = com.mntx.gitpulse.sync.SyncPath.normalise(subPath)) }
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

    /**
     * Recursively copies a SAF document tree into app storage so we own stable File handles.
     *
     * SAF does not support appending a name to a tree Uri — children must be listed through
     * `DocumentsContract.buildChildDocumentsUriUsingTree` and copied with their document Uri.
     * The previous implementation guessed `"$uri/$name"` and therefore copied zero files.
     */
    private fun copyTree(context: android.content.Context, root: Uri, dest: File) {
        val resolver = context.contentResolver
        dest.mkdirs()
        var count = 0
        var bytes = 0L
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
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { cursor ->
                val out = mutableListOf<Triple<String, String, String>>()
                while (cursor.moveToNext()) {
                    out += Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                }
                out
            } ?: emptyList()

            for ((docId, name, mime) in children) {
                val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(root, docId)
                val childPath = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    File(dest, childPath).mkdirs()
                    stack.addLast(childPath to childUri)
                } else {
                    val target = File(dest, childPath)
                    target.parentFile?.mkdirs()
                    runCatching {
                        resolver.openInputStream(childUri)?.use { input ->
                            target.outputStream().use { output -> bytes += input.copyTo(output, 256 * 1024) }
                        }
                    }
                    count++
                    if (count % 25 == 0) {
                        _state.update {
                            it.copy(
                                progressLabel = "$count files · ${humanSize(bytes)}",
                                current = count,
                            )
                        }
                    }
                }
            }
        }
        if (count == 0) error("That folder is empty or its contents can't be read.")
    }

    /* ------------------------------------------------------------- diff */

    private fun recomputeDiff() = computeDiff()

    /**
     * Computes the plan for review without touching the push pipeline: one tree read,
     * local hashing (cached), pure diff. Also works when the branch is empty/missing.
     */
    fun computeDiff() {
        val snapshot = _state.value
        val manifest = snapshot.manifest ?: return
        workJob?.cancel()
        workJob = viewModelScope.launch {
            _state.update {
                it.copy(stage = SyncStage.DIFFING, progress = 0.15f, progressLabel = "Comparing with GitHub…", error = null)
            }
            val repo = _state.value.selectedRepo
            if (repo == null) {
                _state.update { it.copy(stage = SyncStage.REVIEW, progress = 1f) }
                return@launch
            }
            val branch = _state.value.selectedBranch.ifBlank { repo.defaultBranch ?: "main" }
            runCatching {
                engine.plan(
                    manifest = manifest,
                    owner = repo.ownerLogin,
                    repo = repo.name,
                    branch = branch,
                    subPath = _state.value.subPath,
                )
            }.onSuccess { (remote, diff) ->
                _state.update {
                    it.copy(diff = diff, remoteTree = remote, stage = SyncStage.REVIEW, progress = 1f, progressLabel = "", error = null)
                }
            }.onFailure { e ->
                // Brand-new/empty repo: every file is an add and nothing can be pruned.
                if (e is com.mntx.gitpulse.data.ApiException && e.isNotFound) {
                    val local = com.mntx.gitpulse.sync.DiffEngine.diff(manifest.reRoot(_state.value.subPath), emptyMap())
                    _state.update {
                        it.copy(diff = local, remoteTree = emptyMap(), stage = SyncStage.REVIEW, progress = 1f, error = null)
                    }
                } else {
                    _state.update {
                        it.copy(stage = SyncStage.FAILED, error = e.message ?: "Could not compare with GitHub.")
                    }
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
                    subPath = s.subPath,
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

    /** Clear only the per-run error, keeping the plan visible. */
    fun retryDiff() = computeDiff()

    fun setMessage(message: String) = _state.update { it.copy(commitMessage = message) }
    fun setDeleteMissing(enabled: Boolean) {
        _state.update { it.copy(deleteMissing = enabled) }
        recomputeDiff()
    }
    fun setSubPath(path: String) {
        _state.update { it.copy(subPath = com.mntx.gitpulse.sync.SyncPath.normalise(path), diff = null) }
        computeDiff()
    }

    fun setDryRun(enabled: Boolean) = _state.update { it.copy(dryRun = enabled) }
    fun setFilterJunk(enabled: Boolean) = _state.update { it.copy(filterJunk = enabled) }
    fun clearError() = _state.update { it.copy(error = null) }
}
