package com.mntx.gitpulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.data.model.GhBranch
import com.mntx.gitpulse.data.model.GhCommitSummary
import com.mntx.gitpulse.data.model.GhContent
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.sync.LocalFile
import com.mntx.gitpulse.sync.LocalManifest
import com.mntx.gitpulse.ui.sync.SyncStage
import com.mntx.gitpulse.ui.sync.SyncUiState
import com.mntx.gitpulse.ui.theme.GitPulseTheme
import com.mntx.gitpulse.ui.theme.pulseColors
import com.mntx.gitpulse.ui.util.Async
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the key surfaces headlessly so design regressions are visible in CI artefacts.
 * Run with `./gradlew recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h880dp-xhdpi")
class UiSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = AppSettings()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(com.mntx.gitpulse.ui.theme.LocalPulse.current.background)) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        runCatching { compose.onRoot().captureRoboImage("src/test/screenshots/$name.png") }
            .onFailure {
                println("capture '$name' failed: ${it.stackTraceToString()}")
            }
    }

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

    @Test
    fun signIn() {
        capture("01-signin") {
            com.mntx.gitpulse.ui.auth.SignInScreen(loading = false, error = null, onSignIn = {})
        }
    }

    @Test
    fun signInError() {
        capture("02-signin-error") {
            com.mntx.gitpulse.ui.auth.SignInScreen(
                loading = false,
                error = "GitHub rejected that token. Check it has repo + workflow scopes.",
                onSignIn = {},
            )
        }
    }

    @Test
    fun home() {
        capture("03-home") {
            com.mntx.gitpulse.ui.home.HomeScreen(
                user = sampleUser,
                repos = Async.Ready(sampleRepos),
                recent = listOf("mntgxo/gitpulse", "mntgxo/orbit-ui"),
                settings = settings,
                onRefresh = {},
                onOpenRepo = {},
                onOpenProfile = {},
            )
        }
    }

    @Test
    fun syncReview() {
        val files = listOf(
            LocalFile("index.html", 2048) { ByteArray(0).inputStream() },
            LocalFile("assets/app.js", 4096) { ByteArray(0).inputStream() },
            LocalFile("assets/logo.svg", 1024) { ByteArray(0).inputStream() },
        )
        capture("04-sync-review") {
            com.mntx.gitpulse.ui.sync.PushScreenReviewPreview(
                state = SyncUiState(
                    stage = SyncStage.REVIEW,
                    repos = sampleRepos,
                    selectedRepo = sampleRepos.first(),
                    selectedBranch = "main",
                    commitMessage = "feat: deploy landing page",
                    branches = listOf(GhBranch("main"), GhBranch("develop"), GhBranch("release")),
                    manifest = LocalManifest("site", files),
                    diff = com.mntx.gitpulse.sync.DiffEngine.diff(
                        LocalManifest("site", files),
                        mapOf(
                            "assets/app.js" to com.mntx.gitpulse.sync.RemoteBlob("assets/app.js", "old", 3000),
                            "assets/old.css" to com.mntx.gitpulse.sync.RemoteBlob("assets/old.css", "x", 512),
                        )
                    ),
                ),
                onClose = {},
            )
        }
    }

    @Test
    fun explore() {
        capture("05-explore") {
            com.mntx.gitpulse.ui.explore.ExplorePreview(
                results = Async.Ready(sampleRepos),
                users = listOf(sampleUser),
            )
        }
    }

    @Test
    fun settings() {
        capture("06-settings") {
            com.mntx.gitpulse.ui.settings.SettingsScreen(
                user = sampleUser,
                settings = settings,
                onBack = {},
                onUpdate = {},
                onSignOut = {},
            )
        }
    }

    @Test
    fun repoDetail() {
        capture("07-repo-detail") {
            com.mntx.gitpulse.ui.repo.RepoDetailPreview(
                repo = sampleRepos.first(),
                files = listOf(
                    GhContent(name = "app", path = "app", sha = "1", type = "dir"),
                    GhContent(name = "build.gradle.kts", path = "build.gradle.kts", sha = "2", size = 1200),
                    GhContent(name = "README.md", path = "README.md", sha = "3", size = 4200),
                ),
                commits = listOf(
                    GhCommitSummary(sha = "9f2c1ab", commit = com.mntx.gitpulse.data.model.CommitDetail(message = "feat: mirror deletes", author = com.mntx.gitpulse.data.model.GitActor(name = "mnt", date = "2026-11-26T10:00:00Z"))),
                    GhCommitSummary(sha = "4d8e0c2", commit = com.mntx.gitpulse.data.model.CommitDetail(message = "design: aurora backdrop", author = com.mntx.gitpulse.data.model.GitActor(name = "mnt", date = "2026-11-25T08:00:00Z"))),
                ),
            )
        }
    }

    @Test
    fun pushing() {
        capture("08-pushing") {
            com.mntx.gitpulse.ui.sync.PushScreenProgressPreview(
                state = SyncUiState(
                    stage = SyncStage.PUSHING,
                    progress = 0.62f,
                    current = 31,
                    total = 50,
                    progressLabel = "Uploading assets/hero.webp",
                    logs = listOf(
                        com.mntx.gitpulse.ui.sync.LogLine("Comparing 50 local files with GitHub", com.mntx.gitpulse.sync.LogLevel.INFO),
                        com.mntx.gitpulse.ui.sync.LogLine("Plan: 18 new · 9 changed · 4 deleted · 19 unchanged", com.mntx.gitpulse.sync.LogLevel.INFO),
                        com.mntx.gitpulse.ui.sync.LogLine("Uploading 27 blob(s) · 4.2 MB", com.mntx.gitpulse.sync.LogLevel.INFO),
                    ),
                ),
                onCancel = {},
            )
        }
    }
}
