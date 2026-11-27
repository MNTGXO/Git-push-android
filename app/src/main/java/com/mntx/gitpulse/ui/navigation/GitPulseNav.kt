package com.mntx.gitpulse.ui.navigation

import com.mntx.gitpulse.data.model.GhRepo

sealed class Route(val path: String) {
    data object SignIn : Route("signin")
    data object Home : Route("home")
    data object Explore : Route("explore")
    data object Library : Route("library")
    data object Activity : Route("activity")
    data object Settings : Route("settings")
    data object RepoDetail : Route("repo/{owner}/{name}") {
        fun of(owner: String, name: String) = "repo/$owner/$name"
    }
    data object FileBrowser : Route("repo/{owner}/{name}/files?ref={ref}&path={path}") {
        fun of(owner: String, name: String, ref: String, path: String = "") =
            "repo/$owner/$name/files?ref=${ref.urlEncoded()}&path=${path.urlEncoded()}"
    }
    data object FileEditor : Route("repo/{owner}/{name}/edit?ref={ref}&path={path}") {
        fun of(owner: String, name: String, ref: String, path: String = "") =
            "repo/$owner/$name/edit?ref=${ref.urlEncoded()}&path=${path.urlEncoded()}"
    }
    data object Commits : Route("repo/{owner}/{name}/commits?ref={ref}") {
        fun of(owner: String, name: String, ref: String) = "repo/$owner/$name/commits?ref=${ref.urlEncoded()}"
    }
    data object CommitDetail : Route("repo/{owner}/{name}/commit/{sha}") {
        fun of(owner: String, name: String, sha: String) = "repo/$owner/$name/commit/$sha"
    }
    data object DiffReview : Route("sync/review")
    data object SyncProgress : Route("sync/progress")
    data object PullDestination : Route("sync/pull?owner={owner}&repo={repo}&ref={ref}") {
        fun of(owner: String, repo: String, ref: String) =
            "sync/pull?owner=$owner&repo=$repo&ref=${ref.urlEncoded()}"
    }
    data object NewRepo : Route("new-repo")
    data object Profile : Route("profile/{login}") {
        fun of(login: String) = "profile/$login"
    }
    data object Search : Route("search")
    data object Gists : Route("gists")
    data object Notifications : Route("notifications")
    data object Extractor : Route("extractor")
}

val bottomRoutes = listOf(Route.Home, Route.Explore, Route.Library, Route.Activity)

fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")

/** Payload passed between screens via navigation saved state / a simple bus. */
data class RepoRef(val owner: String, val name: String) {
    val fullName: String get() = "$owner/$name"
    companion object {
        fun from(repo: GhRepo) = RepoRef(repo.ownerLogin, repo.name)
    }
}
