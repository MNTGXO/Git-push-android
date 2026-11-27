package com.mntx.gitpulse.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct repository edits that don't need an upload: delete one file, rename/move a file
 * (the classic GitHub "can't rename on phone" gap), and delete a whole folder recursively.
 *
 * Every action is one atomic commit where possible: folder deletes fold all their entries
 * into a single tree + commit + ref update.
 */
data class RepoEditState(
    val busy: Boolean = false,
    val label: String = "",
    val error: String? = null,
    val doneMessage: String? = null,
    val commitSha: String? = null,
)

class RepoEditViewModel(app: Application) : AndroidViewModel(app) {

    private val services = ServiceLocator.get(app)
    private val api = services.api

    private val _state = MutableStateFlow(RepoEditState())
    val state = _state.asStateFlow()

    fun clear() = _state.update { RepoEditState() }

    fun deleteFile(owner: String, repo: String, branch: String, path: String, message: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, label = "Deleting $path", error = null, doneMessage = null) }
            runCatching {
                val head = api.branch(owner, repo, branch).commit?.sha ?: error("Branch not found")
                val treeSha = api.commitTreeSha(owner, repo, head)
                val newTree = api.createTree(
                    owner, repo,
                    com.mntx.gitpulse.data.model.CreateTreeRequest(
                        baseTree = treeSha,
                        tree = listOf(
                            com.mntx.gitpulse.data.model.CreateTreeEntry(path, type = "blob", sha = null)
                        ),
                    ),
                ) ?: error("GitHub returned no tree")
                val commit = api.createCommit(
                    owner, repo,
                    com.mntx.gitpulse.data.model.CreateCommitRequest(
                        message = message.ifBlank { "chore: delete $path" },
                        tree = newTree,
                        parents = listOf(head),
                    ),
                )
                api.updateRef(owner, repo, branch, commit.sha)
                commit.sha
            }.onSuccess { sha ->
                _state.update { it.copy(busy = false, doneMessage = "Deleted $path", commitSha = sha) }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    /** Deletes every blob under [dirPath] in one commit. */
    fun deleteFolder(owner: String, repo: String, branch: String, dirPath: String, message: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, label = "Deleting $dirPath/", error = null, doneMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val head = api.branch(owner, repo, branch).commit?.sha ?: error("Branch not found")
                    val treeSha = api.commitTreeSha(owner, repo, head)
                    val tree = api.tree(owner, repo, head, recursive = true)
                    val prefix = dirPath.trimEnd('/') + "/"
                    val victims = tree.tree.filter { it.isBlob && it.path.startsWith(prefix) }
                    if (victims.isEmpty()) error("Nothing to delete in $dirPath/")
                    val entries = victims.map {
                        com.mntx.gitpulse.data.model.CreateTreeEntry(it.path, type = "blob", sha = null)
                    }
                    val newTree = api.createTree(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateTreeRequest(baseTree = treeSha, tree = entries),
                    ) ?: error("GitHub returned no tree")
                    val commit = api.createCommit(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateCommitRequest(
                            message = message.ifBlank { "chore: delete $dirPath/" },
                            tree = newTree,
                            parents = listOf(head),
                        ),
                    )
                    api.updateRef(owner, repo, branch, commit.sha)
                    "${victims.size} files" to commit.sha
                }
            }.onSuccess { (count, sha) ->
                _state.update { it.copy(busy = false, doneMessage = "Deleted $count file(s) from $dirPath/", commitSha = sha) }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    /** Moves a file to a new path in a single commit (add new blob from old, drop old path). */
    fun renameFile(owner: String, repo: String, branch: String, from: String, to: String, message: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, label = "Moving $from", error = null, doneMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val clean = com.mntx.gitpulse.sync.SyncPath.normalise(to)
                    if (clean.isBlank()) error("Enter a new path")
                    val head = api.branch(owner, repo, branch).commit?.sha ?: error("Branch not found")
                    val treeSha = api.commitTreeSha(owner, repo, head)
                    // The repository tree gives us the blob id directly — no need to download
                    // and re-upload just to move a file.
                    val tree = api.tree(owner, repo, head, recursive = true)
                    val blobSha = tree.tree.firstOrNull { it.isBlob && it.path == from }?.sha
                        ?: error("File not found: $from")
                    val created = api.createBlobReference(owner, repo, blobSha)
                    val newTree = api.createTree(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateTreeRequest(
                            baseTree = treeSha,
                            tree = listOf(
                                com.mntx.gitpulse.data.model.CreateTreeEntry.blob(clean, created),
                                com.mntx.gitpulse.data.model.CreateTreeEntry(from, type = "blob", sha = null),
                            ),
                        ),
                    ) ?: error("GitHub returned no tree")
                    val commit = api.createCommit(
                        owner, repo,
                        com.mntx.gitpulse.data.model.CreateCommitRequest(
                            message = message.ifBlank { "chore: move $from → $clean" },
                            tree = newTree,
                            parents = listOf(head),
                        ),
                    )
                    api.updateRef(owner, repo, branch, commit.sha)
                    commit.sha
                }
            }.onSuccess { sha ->
                _state.update { it.copy(busy = false, doneMessage = "Moved to $to", commitSha = sha) }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is ApiException -> when {
            e.isAuth -> "Your token can't modify this repository."
            e.isConflict -> "The branch moved — refresh and retry."
            e.isDecodeFailure -> "GitHub sent a response GitPulse could not read."
            else -> e.message ?: "GitHub error"
        }
        else -> e.message ?: "Something went wrong"
    }
}
