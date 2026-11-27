package com.mntx.gitpulse.data.model

import kotlinx.serialization.Serializable

/* ---------------------------------------------------------------------------------------------
 * GitHub REST models — only the fields the app actually consumes. All optional fields are
 * nullable/defaulted because the REST API is generous with omissions.
 * ------------------------------------------------------------------------------------------- */

@Serializable
data class GhUser(
    val login: String,
    val id: Long = 0,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String? = null,
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    val email: String? = null,
    val twitterUsername: String? = null,
    val publicRepos: Int = 0,
    val publicGists: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val createdAt: String? = null,
    val type: String? = "User",
)

@Serializable
data class GhRepo(
    val id: Long = 0,
    val name: String,
    val fullName: String,
    val owner: GhUser? = null,
    val private: Boolean = false,
    val description: String? = null,
    val fork: Boolean = false,
    val htmlUrl: String? = null,
    val homepage: String? = null,
    val language: String? = null,
    val stargazersCount: Int = 0,
    val watchersCount: Int = 0,
    val forksCount: Int = 0,
    val openIssuesCount: Int = 0,
    val defaultBranch: String? = null,
    val size: Long = 0,
    val archived: Boolean = false,
    val disabled: Boolean = false,
    val visibility: String? = null,
    val topics: List<String> = emptyList(),
    val license: GhLicense? = null,
    val permissions: RepoPermissions? = null,
    val updatedAt: String? = null,
    val pushedAt: String? = null,
    val createdAt: String? = null,
) {
    val ownerLogin: String get() = owner?.login ?: fullName.substringBefore('/')
    val isOwner: Boolean get() = permissions?.admin == true || permissions?.push == true
}

@Serializable
data class RepoPermissions(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false,
)

@Serializable
data class GhLicense(val key: String? = null, val name: String? = null, val spdxId: String? = null)

@Serializable
data class GhBranch(
    val name: String,
    val commit: BranchCommit? = null,
    val `protected`: Boolean = false,
)

@Serializable
data class BranchCommit(
    val sha: String? = null,
    val url: String? = null,
)

@Serializable
data class GhContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String = "file",
    val downloadUrl: String? = null,
    val htmlUrl: String? = null,
    val gitUrl: String? = null,
    val content: String? = null,
    val encoding: String? = null,
) {
    val isDir: Boolean get() = type == "dir"
    val isFile: Boolean get() = type == "file"
    val isSymlink: Boolean get() = type == "symlink"
    val isSubmodule: Boolean get() = type == "submodule"
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

@Serializable
data class GhTree(
    val sha: String = "",
    val truncated: Boolean = false,
    val tree: List<GhTreeEntry> = emptyList(),
)

@Serializable
data class GhTreeEntry(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String,
    val size: Long? = null,
    val url: String? = null,
) {
    val isBlob: Boolean get() = type == "blob"
    val isTree: Boolean get() = type == "tree"
}

@Serializable
data class GhCommitSummary(
    val sha: String,
    val commit: CommitDetail? = null,
    val author: GhUser? = null,
    val committer: GhUser? = null,
    val parents: List<BranchCommit> = emptyList(),
) {
    val message: String get() = commit?.message.orEmpty()
    val headline: String get() = message.lineSequence().firstOrNull()?.trim().orEmpty()
    val authorLogin: String get() = author?.login ?: commit?.author?.name ?: "unknown"
    val avatarUrl: String? get() = author?.avatarUrl
    val date: String? get() = commit?.author?.date ?: commit?.committer?.date
}

@Serializable
data class CommitDetail(
    val message: String = "",
    val author: GitActor? = null,
    val committer: GitActor? = null,
    val commentCount: Int = 0,
    val verification: Verification? = null,
    val stats: CommitStats? = null,
    val files: List<CommitFile> = emptyList(),
)

@Serializable
data class GitActor(val name: String? = null, val email: String? = null, val date: String? = null, val login: String? = null)

@Serializable
data class Verification(val verified: Boolean = false, val reason: String? = null)

@Serializable
data class CommitStats(val total: Int = 0, val additions: Int = 0, val deletions: Int = 0)

@Serializable
data class CommitFile(
    val filename: String = "",
    val status: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
    val previousFilename: String? = null,
)

/* ------------------------------ write payloads ------------------------------ */

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    val autoInit: Boolean = true,
    val gitignoreTemplate: String? = null,
    val licenseTemplate: String? = null,
    val homepage: String? = null,
)

@Serializable
data class UpdateRepoRequest(
    val name: String? = null,
    val description: String? = null,
    val private: Boolean? = null,
    val homepage: String? = null,
    val archived: Boolean? = null,
    val hasIssues: Boolean? = null,
    val hasWiki: Boolean? = null,
    val defaultBranch: String? = null,
)

@Serializable
data class CreateBranchRequest(val ref: String, val sha: String)

@Serializable
data class CreateBlobRequest(val content: String, val encoding: String = "base64")

@Serializable
data class CreateBlobResponse(val sha: String, val url: String? = null)

@Serializable
data class CreateTreeEntry(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String? = null,
    val content: String? = null,
) {
    companion object {
        fun blob(path: String, sha: String) = CreateTreeEntry(path = path, mode = "100644", type = "blob", sha = sha)
        fun tree(path: String) = CreateTreeEntry(path = path, mode = "040000", type = "tree", sha = null)
        fun deleted(path: String, isTree: Boolean = false) =
            if (isTree) CreateTreeEntry(path = path, mode = "040000", type = "tree", sha = null)
            else CreateTreeEntry(path = path, mode = "100644", type = "blob", sha = null)
    }
}

@Serializable
data class CreateTreeRequest(
    val baseTree: String? = null,
    val tree: List<CreateTreeEntry>,
)

@Serializable
data class CreateTreeResponse(val sha: String, val truncated: Boolean = false)

@Serializable
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList(),
    val author: CommitAuthor? = null,
)

@Serializable
data class CommitAuthor(val name: String, val email: String, val date: String? = null)

@Serializable
data class CreateRefRequest(val ref: String, val sha: String)

@Serializable
data class UpdateRefRequest(val sha: String, val force: Boolean = false)

@Serializable
data class UpdateContentRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null,
    val encoding: String = "base64",
)

@Serializable
data class GhFileResponse(val content: GhContent? = null, val commit: GhCommitSummary? = null)

/* ------------------------------ misc server data ------------------------------ */

@Serializable
data class GhRateLimit(
    val resources: Map<String, RateResource> = emptyMap(),
    val rate: RateResource? = null,
)

@Serializable
data class RateResource(val limit: Int = 60, val remaining: Int = 60, val reset: Long = 0, val used: Int = 0)

@Serializable
data class GhGist(
    val id: String,
    val description: String? = null,
    val public: Boolean = false,
    val files: Map<String, GistFile> = emptyMap(),
    val htmlUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val comments: Int = 0,
)

@Serializable
data class GistFile(val filename: String? = null, val size: Int = 0, val language: String? = null, val rawUrl: String? = null)

@Serializable
data class GhNotification(
    val id: String,
    val unread: Boolean = true,
    val reason: String? = null,
    val subject: NotificationSubject? = null,
    val repository: GhRepo? = null,
    val updatedAt: String? = null,
)

@Serializable
data class NotificationSubject(val title: String? = null, val type: String? = null, val url: String? = null)

@Serializable
data class GhWorkflow(
    val id: Long,
    val name: String = "",
    val path: String = "",
    val state: String = "active",
)

@Serializable
data class GhWorkflowRuns(
    val totalCount: Int = 0,
    val workflowRuns: List<GhWorkflowRun> = emptyList(),
)

@Serializable
data class GhWorkflowRun(
    val id: Long,
    val name: String? = null,
    val displayTitle: String? = null,
    val headBranch: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val htmlUrl: String? = null,
    val event: String? = null,
    val runNumber: Int = 0,
)

@Serializable
data class GhIssue(
    val id: Long = 0,
    val number: Int,
    val title: String,
    val state: String = "open",
    val body: String? = null,
    val user: GhUser? = null,
    val labels: List<GhLabel> = emptyList(),
    val comments: Int = 0,
    val pullRequest: PullRequestMarker? = null,
    val createdAt: String? = null,
    val htmlUrl: String? = null,
) {
    val isPullRequest: Boolean get() = pullRequest != null
}

@Serializable
data class PullRequestMarker(val url: String? = null, val htmlUrl: String? = null)

@Serializable
data class GhLabel(val id: Long = 0, val name: String? = null, val color: String? = null, val description: String? = null)

@Serializable
data class GhPullRequest(
    val id: Long = 0,
    val number: Int,
    val title: String,
    val state: String = "open",
    val body: String? = null,
    val user: GhUser? = null,
    val draft: Boolean = false,
    val mergedAt: String? = null,
    val createdAt: String? = null,
    val head: PrBranch? = null,
    val base: PrBranch? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
) {
    val isMerged: Boolean get() = mergedAt != null
}

@Serializable
data class PrBranch(val ref: String? = null, val label: String? = null, val sha: String? = null)

@Serializable
data class CreatePullRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null,
    val draft: Boolean = false,
)

@Serializable
data class GhRelease(
    val id: Long = 0,
    val tagName: String? = null,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val createdAt: String? = null,
    val publishedAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
data class ReleaseAsset(val id: Long = 0, val name: String? = null, val size: Long = 0, val downloadCount: Int = 0, val browserDownloadUrl: String? = null)

@Serializable
data class SearchReposResponse(
    val totalCount: Int = 0,
    val incompleteResults: Boolean = false,
    val items: List<GhRepo> = emptyList(),
)

@Serializable
data class SearchUsersResponse(
    val totalCount: Int = 0,
    val items: List<GhUser> = emptyList(),
)

@Serializable
data class GhErrorBody(val message: String? = null, val documentationUrl: String? = null, val errors: List<GhErrorItem>? = null)

@Serializable
data class GhErrorItem(val message: String? = null, val code: String? = null, val resource: String? = null)
