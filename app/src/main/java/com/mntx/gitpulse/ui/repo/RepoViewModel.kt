package com.mntx.gitpulse.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.data.model.GhBranch
import com.mntx.gitpulse.data.model.GhCommitSummary
import com.mntx.gitpulse.data.model.GhContent
import com.mntx.gitpulse.data.model.GhIssue
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhWorkflowRun
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepoState(
    val repo: Async<GhRepo> = Async.Loading,
    val branches: List<GhBranch> = emptyList(),
    val selectedBranch: String = "",
    val contents: Async<List<GhContent>> = Async.Idle,
    val commits: Async<List<GhCommitSummary>> = Async.Idle,
    val issues: Async<List<GhIssue>> = Async.Idle,
    val runs: Async<List<GhWorkflowRun>> = Async.Idle,
    val languages: Map<String, Long> = emptyMap(),
    val starred: Boolean = false,
    val path: String = "",
)

class RepoViewModel(app: Application) : AndroidViewModel(app) {

    private val services: ServiceLocator = ServiceLocator.get(app)
    private val api = services.api

    private val _state = MutableStateFlow(RepoState())
    val state = _state.asStateFlow()

    private val contents = AsyncValue<List<GhContent>>()
    private val commits = AsyncValue<List<GhCommitSummary>>()
    private val issues = AsyncValue<List<GhIssue>>()
    private val runs = AsyncValue<List<GhWorkflowRun>>()

    fun load(owner: String, name: String) {
        if ((_state.value.repo as? Async.Ready)?.value?.fullName == "$owner/$name") return
        viewModelScope.launch {
            _state.update { it.copy(repo = Async.Loading) }
            runCatching { api.repo(owner, name) }
                .onSuccess { repo ->
                    val branch = repo.defaultBranch ?: "main"
                    _state.update { it.copy(repo = Async.Ready(repo), selectedBranch = branch) }
                    refreshAll(owner, name, branch)
                }
                .onFailure { e -> _state.update { it.copy(repo = Async.Error(e.message ?: "Failed to load")) } }
        }
    }

    fun refreshAll(owner: String, name: String, branch: String) {
        viewModelScope.launch {
            val b = async { runCatching { api.branches(owner, name) }.getOrDefault(emptyList()) }
            val l = async { runCatching { api.languages(owner, name) }.getOrDefault(emptyMap()) }
            _state.update { it.copy(branches = b.await(), languages = l.await()) }
            loadContents(owner, name, branch, "")
            loadCommits(owner, name, branch)
            loadIssues(owner, name)
            loadRuns(owner, name)
            runCatching { api.isStarred(owner, name) }.onSuccess { s -> _state.update { it.copy(starred = s) } }
        }
    }

    fun selectBranch(owner: String, name: String, branch: String) {
        _state.update { it.copy(selectedBranch = branch, path = "") }
        loadContents(owner, name, branch, "")
        loadCommits(owner, name, branch)
    }

    fun openPath(owner: String, name: String, path: String) {
        _state.update { it.copy(path = path) }
        loadContents(owner, name, _state.value.selectedBranch, path)
    }

    fun loadContents(owner: String, name: String, branch: String, path: String) {
        viewModelScope.launch {
            contents.load {
                if (path.isBlank()) {
                    api.contents(owner, name, "", branch)
                } else {
                    runCatching { api.contents(owner, name, path, branch) }
                        .getOrElse { listOf(api.content(owner, name, path, branch)) }
                }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
            }
            _state.update { it.copy(contents = contents.state.value) }
        }
    }

    fun loadCommits(owner: String, name: String, branch: String) {
        viewModelScope.launch {
            commits.load { api.commits(owner, name, sha = branch, perPage = 30) }
            _state.update { it.copy(commits = commits.state.value) }
        }
    }

    fun loadIssues(owner: String, name: String) {
        viewModelScope.launch {
            issues.load { api.issues(owner, name) }
            _state.update { it.copy(issues = issues.state.value) }
        }
    }

    fun loadRuns(owner: String, name: String) {
        viewModelScope.launch {
            runs.load { api.workflows(owner, name).workflowRuns }
            _state.update { it.copy(runs = runs.state.value) }
        }
    }

    fun toggleStar(owner: String, name: String) {
        val target = !_state.value.starred
        _state.update { it.copy(starred = target) }
        viewModelScope.launch {
            runCatching { api.starRepo(owner, name, target) }
                .onFailure { _state.update { it.copy(starred = !target) } }
        }
    }

    fun deleteRepo(owner: String, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { api.deleteRepo(owner, name) }
                .onSuccess { onDone(true) }
                .onFailure { onDone(false) }
        }
    }

    fun rerun(owner: String, name: String, runId: Long) {
        viewModelScope.launch {
            runCatching { api.rerunWorkflow(owner, name, runId) }
            loadRuns(owner, name)
        }
    }
}
