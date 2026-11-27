package com.mntx.gitpulse

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
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
import com.mntx.gitpulse.sync.RemoteBlob
import com.mntx.gitpulse.ui.sync.LogLine
import com.mntx.gitpulse.ui.sync.PushScreenProgressPreview
import com.mntx.gitpulse.ui.sync.PushScreenReviewPreview
import com.mntx.gitpulse.ui.sync.SyncStage
import com.mntx.gitpulse.ui.sync.SyncUiState
import com.mntx.gitpulse.ui.theme.GitPulseTheme
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.MotionFlags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the key product surfaces headlessly so every design change is reviewable as a PNG.
 *
 * Determinism notes (learned the hard way):
 *  - every infinite/breathe/shimmer animation is frozen via [MotionFlags],
 *  - the Compose clock is driven manually and advanced explicitly instead of calling
 *    `waitForIdle()` (which never returns while anything animates forever),
 *  - content is wrapped in [PhoneFrame] so status/nav insets are part of the art.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xhdpi")
class UiSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = AppSettings()
    private var failures = 0

    @Before
    fun freezeMotion() {
        MotionFlags.freezeInfinite = true
        MotionFlags.instantEntrances = true
    }

    @After
    fun restoreMotion() {
        MotionFlags.freezeInfinite = false
        MotionFlags.instantEntrances = false
        check(failures == 0) { "$failures screenshot(s) failed to render" }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        // Manual clock: never wait for "idle" (one unexpected loop would hang the job), just
        // render the frames ourselves and finish whatever is mid-flight.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GitPulseTheme(settings = settings) {
                Box(Modifier.fillMaxSize().background(LocalPulse.current.background)) {
                    PhoneFrame { content() }
                }
            }
        }
        // Kick the frame clock: layout + first draw, then start anything that is mid-flight and
        // finish it, so nothing stays on screen half-faded.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        runCatching { compose.mainClock.advanceTimeBy(1_200) }
        runCatching {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
            val file = File(dir, "$name.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("captured -> ${file.absolutePath} (${file.length()} bytes)")
            check(file.length() > 2_000) { "screenshot $name is suspiciously small" }
        }.onFailure {
            failures++
            println("capture '$name' failed: ${it.stackTraceToString()}")
        }
        // Compose rules cannot be re-set in one test, so each capture is its own @Test.
    }

    /** Draws the status-bar / nav-bar gutters so previews look like a real phone. */
    @Composable
    private fun PhoneFrame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) { content() }
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

    @Test
    fun signIn() = capture("01-signin") {
        com.mntx.gitpulse.ui.auth.SignInScreen(loading = false, error = null, onSignIn = {})
    }

    @Test
    fun signInError() = capture("02-signin-error") {
        com.mntx.gitpulse.ui.auth.SignInScreen(
            loading = false,
            error = "GitHub rejected that token. Check it has repo + workflow scopes.",
            onSignIn = {},
        )
    }

    @Test
    fun home() = capture("03-home") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    com.mntx.gitpulse.ui.components.PulseLogo(size = 30.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "Good afternoon",
                            style = bodyStyle(11),
                            color = LocalPulse.current.textTertiary,
                        )
                        Text(
                            "@mntgxo",
                            style = com.mntx.gitpulse.ui.theme.displayStyle(16, androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = -0.3),
                            color = LocalPulse.current.textPrimary,
                        )
                    }
                }
            }
            items(sampleRepos.size) { index ->
                com.mntx.gitpulse.ui.home.RepoCard(sampleRepos[index]) {}
            }
        }
    }

    @Test
    fun syncReview() = capture("04-sync-review") {
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
    fun explore() = capture("05-explore") {
        com.mntx.gitpulse.ui.explore.ExplorePreview(results = Async.Ready(sampleRepos), users = listOf(sampleUser))
    }

    @Test
    fun settings() = capture("06-settings") {
        com.mntx.gitpulse.ui.settings.SettingsScreen(
            user = sampleUser,
            settings = settings,
            onBack = {},
            onUpdate = {},
            onSignOut = {},
        )
    }

    @Test
    fun repoDetail() = capture("07-repo-detail") {
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
    fun pushing() = capture("08-pushing") {
        PushScreenProgressPreview(
            state = SyncUiState(
                stage = SyncStage.PUSHING,
                progress = 0.62f,
                current = 31,
                total = 50,
                progressLabel = "Uploading assets/hero.webp",
                logs = listOf(
                    LogLine("Comparing 50 local files with GitHub", com.mntx.gitpulse.sync.LogLevel.INFO),
                    LogLine("Plan: 18 new · 9 changed · 4 deleted · 19 unchanged", com.mntx.gitpulse.sync.LogLevel.INFO),
                    LogLine("Uploading 27 blob(s) · 4.2 MB", com.mntx.gitpulse.sync.LogLevel.INFO),
                ),
            ),
            onCancel = {},
        )
    }
}
