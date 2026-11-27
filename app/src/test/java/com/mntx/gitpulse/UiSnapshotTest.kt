package com.mntx.gitpulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.data.model.CommitDetail
import com.mntx.gitpulse.data.model.GhBranch
import com.mntx.gitpulse.data.model.GhCommitSummary
import com.mntx.gitpulse.data.model.GhContent
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.data.model.GitActor
import com.mntx.gitpulse.sync.DiffEngine
import com.mntx.gitpulse.sync.LocalFile
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.sync.LogLevel
import com.mntx.gitpulse.sync.RemoteBlob
import com.mntx.gitpulse.ui.sync.LogLine
import com.mntx.gitpulse.ui.sync.PushScreenProgressPreview
import com.mntx.gitpulse.ui.sync.PushScreenReviewPreview
import com.mntx.gitpulse.ui.sync.SyncStage
import com.mntx.gitpulse.ui.sync.SyncUiState
import com.mntx.gitpulse.ui.theme.GitPulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.MotionFlags
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every key product surface to PNG files under build/screenshots in CI.
 *
 * Determinism: infinite motion is frozen via [MotionFlags], and capture draws the activity's
 * content view (see [ScreenshotHarness]) instead of using Compose semantics idling, which
 * never settles under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xhdpi")
class UiSnapshotTest : ScreenshotHarness() {

    private val settings = AppSettings()

    @Before
    fun freezeMotion() {
        MotionFlags.freezeInfinite = true
        MotionFlags.instantEntrances = true
    }

    @After
    fun restoreMotion() {
        MotionFlags.freezeInfinite = false
        MotionFlags.instantEntrances = false
    }

    private fun shot(
        name: String,
        surface: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        setSurface { GitPulseTheme(settings = settings) { surface() } }
        save(name)
    }

    /* ------------------------------------------------------------------ screens */

    @Test
    fun signIn() = shot("01-signin") {
        com.mntx.gitpulse.ui.auth.SignInScreen(loading = false, error = null, onSignIn = {})
    }

    @Test
    fun signInError() = shot("02-signin-error") {
        com.mntx.gitpulse.ui.auth.SignInScreen(
            loading = false,
            error = "GitHub rejected that token. Check it has repo + workflow scopes.",
            onSignIn = {},
        )
    }

    @Test
    fun home() = shot("03-home") {
        com.mntx.gitpulse.ui.home.HomePreview(
            user = sampleUser,
            repos = sampleRepos,
            recent = listOf("mntgxo/gitpulse"),
            settings = settings,
        )
    }

    @Test
    fun syncReview() = shot("04-sync-review") {
        PushScreenReviewPreview(
            state = SyncUiState(
                stage = SyncStage.REVIEW,
                repos = sampleRepos,
                selectedRepo = sampleRepos.first(),
                selectedBranch = "main",
                commitMessage = "feat: deploy landing page",
                branches = listOf(GhBranch("main"), GhBranch("develop"), GhBranch("release")),
                manifest = LocalManifest("site", sampleFiles),
                diff = reviewDiff,
            ),
            onClose = {},
        )
    }

    @Test
    fun explore() = shot("05-explore") {
        com.mntx.gitpulse.ui.explore.ExplorePreview(results = Async.Ready(sampleRepos), users = listOf(sampleUser))
    }

    @Test
    fun settings() = shot("06-settings") {
        com.mntx.gitpulse.ui.settings.SettingsScreen(
            user = sampleUser,
            settings = settings,
            onBack = {},
            onUpdate = {},
            onSignOut = {},
        )
    }

    @Test
    fun repoDetail() = shot("07-repo-detail") {
        com.mntx.gitpulse.ui.repo.RepoDetailPreview(
            repo = sampleRepos.first(),
            files = listOf(
                GhContent(name = "app", path = "app", sha = "1", type = "dir"),
                GhContent(name = "build.gradle.kts", path = "build.gradle.kts", sha = "2", size = 1200),
                GhContent(name = "README.md", path = "README.md", sha = "3", size = 4200),
            ),
            commits = listOf(
                GhCommitSummary(sha = "9f2c1ab", commit = CommitDetail(message = "feat: mirror deletes", author = GitActor(name = "mnt", date = "2026-11-26T10:00:00Z"))),
                GhCommitSummary(sha = "4d8e0c2", commit = CommitDetail(message = "design: aurora backdrop", author = GitActor(name = "mnt", date = "2026-11-25T08:00:00Z"))),
            ),
        )
    }

    @Test
    fun pushing() = shot("08-pushing") {
        PushScreenProgressPreview(
            state = SyncUiState(
                stage = SyncStage.PUSHING,
                progress = 0.62f,
                current = 31,
                total = 50,
                progressLabel = "Uploading assets/hero.webp",
                logs = listOf(
                    LogLine("Comparing 50 local files with GitHub", LogLevel.INFO),
                    LogLine("Plan: 18 new · 9 changed · 4 deleted · 19 unchanged", LogLevel.INFO),
                    LogLine("Uploading 27 blob(s) · 4.2 MB", LogLevel.INFO),
                ),
            ),
            onCancel = {},
        )
    }

    @Test
    fun library() = shot("09-library") {
        com.mntx.gitpulse.ui.library.LibraryPreview(repos = sampleRepos)
    }

    @Test
    fun fileEditor() = shot("10-file-editor") {
        com.mntx.gitpulse.ui.code.EditorPreview(
            path = "site/index.html",
            branch = "main",
            text = "<!doctype html>\n<html>\n  <head>\n    <title>GitPulse</title>\n  </head>\n  <body>\n    <h1>Ship from your pocket.</h1>\n  </body>\n</html>",
        )
    }

    @Test
    fun targetFolder() = shot("11-target-folder") {
        PushScreenReviewPreview(
            state = SyncUiState(
                stage = SyncStage.REVIEW,
                repos = sampleRepos,
                selectedRepo = sampleRepos.first(),
                selectedBranch = "main",
                subPath = "site",
                commitMessage = "deploy: landing page",
                branches = listOf(GhBranch("main"), GhBranch("develop")),
                manifest = LocalManifest("site", sampleFiles),
                diff = reviewDiff,
            ),
            onClose = {},
        )
    }

    @Test
    fun pull() = shot("12-pull") {
        com.mntx.gitpulse.ui.sync.PullPreview(
            owner = "mntgxo",
            repo = "gitpulse",
            ref = "main",
            progress = 0.48f,
            current = 72,
            total = 150,
            label = "assets/hero.webp",
        )
    }

    @Test
    fun deployComposer() = shot("14-deploy") {
        com.mntx.gitpulse.ui.sync.DeployPreview(
            state = com.mntx.gitpulse.ui.sync.DeployState(
                stage = com.mntx.gitpulse.ui.sync.DeployStage.IDLE,
                target = "mntgxo/gitpulse",
                branch = "main",
                subPath = "site",
                sourceName = "gitpulse-site.zip",
                manifest = LocalManifest("site", sampleFiles),
            ),
        )
    }

    @Test
    fun deployReview() = shot("15-deploy-review") {
        com.mntx.gitpulse.ui.sync.DeployPreview(
            state = com.mntx.gitpulse.ui.sync.DeployState(
                stage = com.mntx.gitpulse.ui.sync.DeployStage.REVIEW,
                target = "mntgxo/gitpulse",
                branch = "main",
                subPath = "",
                message = "deploy: landing page",
                sourceName = "gitpulse-site.zip",
                manifest = LocalManifest("site", sampleFiles),
                diff = reviewDiff,
            ),
        )
    }

    @Test
    fun pullSetup() = shot("16-pull-setup") {
        com.mntx.gitpulse.ui.sync.PullSetupPreview(ref = "main")
    }

    /* ------------------------------------------------------------------ fixtures */

    private val sampleUser = GhUser(
        login = "mntgxo",
        name = "MNT",
        avatarUrl = null,
        followers = 128,
        following = 42,
        publicRepos = 24,
        bio = "Shipping tools that make git feel instant.",
    )

    private val sampleRepos = listOf(
        GhRepo(id = 1, name = "gitpulse", fullName = "mntgxo/gitpulse", description = "Push zips to GitHub from your phone", language = "Kotlin", stargazersCount = 312, forksCount = 48, watchersCount = 20, openIssuesCount = 3, size = 1840, defaultBranch = "main", pushedAt = "2026-11-26T10:00:00Z", private = false, owner = sampleUser),
        GhRepo(id = 2, name = "orbit-ui", fullName = "mntgxo/orbit-ui", description = "Compose design system with motion tokens", language = "Kotlin", stargazersCount = 148, forksCount = 12, size = 940, defaultBranch = "main", pushedAt = "2026-11-25T08:00:00Z", private = true, owner = sampleUser),
        GhRepo(id = 3, name = "neon-api", fullName = "mntgxo/neon-api", description = "Realtime API for the pulse dashboard", language = "TypeScript", stargazersCount = 64, forksCount = 6, size = 420, defaultBranch = "main", pushedAt = "2026-11-22T14:00:00Z", owner = sampleUser),
    )

    private val sampleFiles = listOf(
        LocalFile("index.html", 2048) { ByteArray(0).inputStream() },
        LocalFile("assets/app.js", 4096) { ByteArray(0).inputStream() },
        LocalFile("assets/logo.svg", 1024) { ByteArray(0).inputStream() },
    )

    private val reviewDiff = DiffEngine.diff(
        LocalManifest("site", sampleFiles),
        mapOf(
            "assets/app.js" to RemoteBlob("assets/app.js", "old", 3000),
            "assets/old.css" to RemoteBlob("assets/old.css", "x", 512),
        ),
    )

}
