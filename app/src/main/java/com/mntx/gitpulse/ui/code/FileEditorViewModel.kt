package com.mntx.gitpulse.ui.code

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.ApiException
import com.mntx.gitpulse.data.model.GhContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

/**
 * Editing a repository from the phone: change a file, create one, rename/move it, delete it,
 * or save a local copy — each committed straight to the branch.
 */
data class EditorState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val original: String = "",
    val text: String = "",
    /** Commit the file was loaded at — guards the "force push" path only. */
    val blobSha: String? = null,
    val exists: Boolean = true,
    val binary: Boolean = false,
    val size: Long = 0,
    val message: String = "",
    val error: String? = null,
    val savedSha: String? = null,
    val savedMessage: String? = null,
    val loaded: Boolean = false,
) {
    val dirty: Boolean get() = loaded && text != original
}

class FileEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val services = ServiceLocator.get(app)
    private val api = services.api

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    private var owner = ""
    private var repo = ""
    private var branch = ""
    private var path = ""

    fun load(owner: String, repo: String, branch: String, path: String) {
        this.owner = owner
        this.repo = repo
        this.branch = branch
        this.path = path
        if (_state.value.loaded) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val info: GhContent = api.content(owner, repo, path, branch)
                    val ext = path.substringAfterLast('.', "").lowercase()
                    val binary = ext in BINARY_EXTS
                    val decoded = if (binary) "" else {
                        info.content?.replace("\n", "")?.let {
                            runCatching { String(Base64.getMimeDecoder().decode(it)) }.getOrNull()
                        } ?: runCatching { String(api.rawFile(owner, repo, path, branch).bytes) }.getOrDefault("")
                    }
                    Triple(info, decoded, binary)
                }
            }.onSuccess { (info, decoded, binary) ->
                _state.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        original = decoded,
                        text = decoded,
                        blobSha = info.sha,
                        exists = true,
                        binary = binary,
                        size = info.size,
                    )
                }
            }.onFailure { e ->
                // A file that doesn't exist yet is a *create*, not an error.
                val notFound = e is ApiException && e.isNotFound
                _state.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        exists = !notFound,
                        blobSha = null,
                        error = if (notFound) null else e.message,
                    )
                }
            }
        }
    }

    fun updateText(value: String) = _state.update { it.copy(text = value) }
    fun updateMessage(value: String) = _state.update { it.copy(message = value) }
    fun clearFeedback() = _state.update { it.copy(error = null, savedMessage = null, savedSha = null) }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving || snapshot.binary) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, savedMessage = null) }
            val message = snapshot.message.ifBlank {
                if (snapshot.exists) "chore: update ${path.substringAfterLast('/')}"
                else "feat: add ${path.substringAfterLast('/')}"
            }
            val bytes = snapshot.text.toByteArray()
            val existingSha = if (snapshot.exists) apiSha() else null
            runCatching {
                withContext(Dispatchers.IO) {
                    api.putFile(owner, repo, path, branch, message, bytes, existingSha)
                }
            }.onSuccess { sha ->
                _state.update {
                    it.copy(
                        saving = false,
                        original = it.text,
                        exists = true,
                        blobSha = it.blobSha ?: sha.ifBlank { it.blobSha },
                        size = bytes.size.toLong(),
                        savedSha = sha,
                        savedMessage = if (snapshot.exists) "Committed update" else "File created",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = friendly(e)) }
            }
        }
    }

    private suspend fun apiSha(): String? = runCatching { api.content(owner, repo, path, branch).sha }.getOrNull()

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val sha = apiSha() ?: error("File no longer exists")
                    api.deleteFile(
                        owner, repo, path, sha, branch,
                        _state.value.message.ifBlank { "chore: delete ${path.substringAfterLast('/')}" },
                    )
                }
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                onDeleted()
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = friendly(e)) }
            }
        }
    }

    fun rename(newPath: String, onRenamed: () -> Unit) {
        val clean = com.mntx.gitpulse.sync.SyncPath.normalise(newPath)
        if (clean.isBlank()) {
            _state.update { it.copy(error = "Enter a new path") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val head = api.branch(owner, repo, branch).commit?.sha ?: error("Branch not found")
                    val tree = api.tree(owner, repo, head, recursive = true)
                    val sha = tree.tree.firstOrNull { it.isBlob && it.path == path }?.sha
                        ?: error("File not found")
                    val blob = api.createBlobReference(owner, repo, sha)
                    val baseTree = api.commitTreeSha(owner, repo, head)
                    val newTree = api.createTree(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateTreeRequest(
                            baseTree = baseTree,
                            tree = listOf(
                                com.mntx.gitpulse.data.model.CreateTreeEntry.blob(clean, blob),
                                com.mntx.gitpulse.data.model.CreateTreeEntry(path, type = "blob", sha = null),
                            ),
                        ),
                    ) ?: error("GitHub returned no tree")
                    val commit = api.createCommit(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateCommitRequest(
                            message = _state.value.message.ifBlank { "chore: move ${path.substringAfterLast('/')} → $clean" },
                            tree = newTree,
                            parents = listOf(head),
                        ),
                    )
                    api.updateRef(owner, repo, branch, commit.sha)
                    commit.sha
                }
            }.onSuccess {
                path = clean
                _state.update { it.copy(saving = false, savedMessage = "Moved to $clean") }
                onRenamed()
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = friendly(e)) }
            }
        }
    }

    /** Writes the current buffer to the app's Downloads-like folder and returns it for sharing. */
    fun export(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val dir = File(services.appContext.getExternalFilesDir(null) ?: services.appContext.filesDir, "exports")
            dir.mkdirs()
            val out = File(dir, path.substringAfterLast('/').ifBlank { "file.txt" })
            withContext(Dispatchers.IO) {
                if (_state.value.binary) {
                    runCatching { api.rawFile(owner, repo, path, branch).bytes }.getOrNull()?.let { out.writeBytes(it) }
                } else {
                    out.writeText(_state.value.text)
                }
            }
            onReady(out)
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is ApiException -> when {
            e.isAuth -> "Your token can't write here (needs Contents: read & write)."
            e.isConflict -> "The file changed on GitHub. Reload and try again."
            else -> e.message ?: "GitHub rejected the change."
        }
        else -> e.message ?: "Could not save."
    }

    companion object {
        val BINARY_EXTS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "zip", "apk", "jar", "pdf", "mp4", "mp3",
            "ttf", "otf", "so", "aab", "woff", "woff2", "class", "dex", "webp",
        )
    }
}
