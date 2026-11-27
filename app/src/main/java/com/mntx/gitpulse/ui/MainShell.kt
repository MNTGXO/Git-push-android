package com.mntx.gitpulse.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.ui.activity.ActivityScreen
import com.mntx.gitpulse.ui.code.CodeViewerScreen
import com.mntx.gitpulse.ui.components.PulseBottomBar
import com.mntx.gitpulse.ui.explore.ExploreScreen
import com.mntx.gitpulse.ui.home.HomeScreen
import com.mntx.gitpulse.ui.library.LibraryScreen
import com.mntx.gitpulse.ui.navigation.ProvideNavigator
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.navigation.rememberPulseNavigator
import com.mntx.gitpulse.ui.profile.ProfileScreen
import com.mntx.gitpulse.ui.repo.CommitDetailScreen
import com.mntx.gitpulse.ui.repo.NewRepoSheet
import com.mntx.gitpulse.ui.repo.RepoDetailScreen
import com.mntx.gitpulse.ui.settings.SettingsScreen
import com.mntx.gitpulse.ui.sync.PullScreen
import com.mntx.gitpulse.ui.sync.PushScreen
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.util.rememberHaptics

/**
 * Signed-in shell: persistent nav dock + navigation graph.
 * The transition contract lives here so every push in the app feels identical.
 */
@Composable
fun MainShell(user: GhUser, appVm: AppViewModel) {
    val colors = LocalPulse.current
    val navigator = rememberPulseNavigator()
    val controller = navigator.controller
    val repos by appVm.repos.collectAsStateWithLifecycle()
    val recent by appVm.recentRepos.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics(settings.haptics)

    // In-app updates: one quiet check per few hours, surfaced only when something is newer.
    val updateVm: com.mntx.gitpulse.update.UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    var dismissedUpdate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { updateVm.checkQuietly() }

    val backStackEntry by controller.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showDock = route in setOf(
        Route.Home.path, Route.Explore.path, Route.Library.path, Route.Activity.path,
    )

    ProvideNavigator(navigator) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            NavHost(
                navController = controller,
                startDestination = Route.Home.path,
                enterTransition = {
                    slideInHorizontally(Motion.move(360)) { it / 5 } + fadeIn(Motion.move(300))
                },
                exitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { -it / 12 } },
                popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(280)) { -it / 12 } },
                popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(260)) { it / 5 } },
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Route.Home.path) {
                    HomeScreen(
                        user = user,
                        repos = repos,
                        recent = recent,
                        settings = settings,
                        onRefresh = appVm::refreshRepos,
                        onOpenRepo = { navigator.push(Route.RepoDetail.of(it.ownerLogin, it.name)) },
                        onOpenProfile = { navigator.push(Route.Settings.path) },
                    )
                }
                composable(Route.Explore.path) { ExploreScreen() }
                composable(Route.Library.path) {
                    LibraryScreen(
                        repos = repos,
                        onOpenRepo = { navigator.push(Route.RepoDetail.of(it.ownerLogin, it.name)) },
                        onNewRepo = { navigator.push(Route.NewRepo.path) },
                    )
                }
                composable(Route.Activity.path) { ActivityScreen() }
                composable(Route.Settings.path) {
                    SettingsScreen(
                        user = user,
                        settings = settings,
                        onBack = navigator::back,
                        onUpdate = appVm::updateSettings,
                        onSignOut = appVm::signOut,
                        update = updateState,
                        onCheckUpdate = updateVm::check,
                        onDownloadUpdate = updateVm::download,
                        onInstallUpdate = updateVm::install,
                    )
                }
                composable(
                    route = Route.RepoDetail.path,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    RepoDetailScreen(
                        owner = entry.arguments?.getString("owner").orEmpty(),
                        name = entry.arguments?.getString("name").orEmpty(),
                        onBack = navigator::back,
                        onShareZip = { o, r, ref -> navigator.push(Route.PullDestination.of(o, r, ref)) },
                    )
                }
                composable(
                    route = Route.FileBrowser.path,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                        navArgument("ref") { type = NavType.StringType; defaultValue = "main" },
                        navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    CodeViewerScreen(
                        owner = entry.arguments?.getString("owner").orEmpty(),
                        repo = entry.arguments?.getString("name").orEmpty(),
                        path = decode(entry.arguments?.getString("path")),
                        ref = decode(entry.arguments?.getString("ref")),
                        onBack = navigator::back,
                        onEdit = {
                            navigator.push(
                                Route.FileEditor.of(
                                    entry.arguments?.getString("owner").orEmpty(),
                                    entry.arguments?.getString("name").orEmpty(),
                                    decode(entry.arguments?.getString("ref")).ifBlank { "main" },
                                    decode(entry.arguments?.getString("path")),
                                )
                            )
                        },
                    )
                }
                composable(
                    route = Route.FileEditor.path,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                        navArgument("ref") { type = NavType.StringType; defaultValue = "main" },
                        navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    com.mntx.gitpulse.ui.code.CodeEditorScreen(
                        owner = entry.arguments?.getString("owner").orEmpty(),
                        repo = entry.arguments?.getString("name").orEmpty(),
                        branch = decode(entry.arguments?.getString("ref")).ifBlank { "main" },
                        path = decode(entry.arguments?.getString("path")),
                        onBack = navigator::back,
                        onCommitted = {},
                    )
                }
                composable(
                    route = Route.CommitDetail.path,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                        navArgument("sha") { type = NavType.StringType },
                    ),
                ) { entry ->
                    CommitDetailScreen(
                        owner = entry.arguments?.getString("owner").orEmpty(),
                        repo = entry.arguments?.getString("name").orEmpty(),
                        sha = entry.arguments?.getString("sha").orEmpty(),
                        onBack = navigator::back,
                    )
                }
                composable(Route.NewRepo.path) {
                    NewRepoSheet(
                        onBack = navigator::back,
                        onCreated = { repo ->
                            appVm.refreshRepos()
                            navigator.back()
                            navigator.push(Route.RepoDetail.of(repo.ownerLogin, repo.name))
                        },
                    )
                }
                composable(
                    route = Route.PullDestination.path,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("ref") { type = NavType.StringType; defaultValue = "main" },
                    ),
                ) { entry ->
                    PullScreen(
                        owner = entry.arguments?.getString("owner").orEmpty(),
                        repo = entry.arguments?.getString("repo").orEmpty(),
                        ref = decode(entry.arguments?.getString("ref")),
                        onBack = navigator::back,
                        onShare = { file ->
                            val launched = runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share repository zip"))
                            }
                            if (launched.isFailure) {
                                // Nothing can handle the share on this device: never let an
                                // ActivityNotFoundException (or a bad FileProvider path) kill us.
                                android.util.Log.w("GitPulse", "share failed", launched.exceptionOrNull())
                                android.widget.Toast.makeText(
                                    context,
                                    "Couldn't open a share sheet. The zip is saved in app storage.",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
                composable(Route.Search.path) { ExploreScreen() }
                composable(Route.Notifications.path) { ActivityScreen() }
                composable(Route.Gists.path) { com.mntx.gitpulse.ui.gists.GistsScreen(onBack = navigator::back) }
                composable(
                    route = Route.DiffReview.path,
                    arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    PushScreen(
                        onClose = navigator::back,
                        target = entry.arguments?.getString("target")?.takeIf { it.isNotBlank() },
                    )
                }
                composable(Route.Deploy.path) {
                    com.mntx.gitpulse.ui.sync.DeployScreen(
                        onBack = navigator::back,
                        onOpenRepo = { full ->
                            val parts = full.split("/")
                            if (parts.size == 2) {
                                navigator.back()
                                navigator.push(Route.RepoDetail.of(parts[0], parts[1]))
                            }
                        },
                    )
                }
                composable(Route.SyncProgress.path) { PushScreen(onClose = navigator::back) }
                composable(
                    route = Route.Profile.path,
                    arguments = listOf(navArgument("login") { type = NavType.StringType }),
                ) { entry ->
                    ProfileScreen(
                        login = entry.arguments?.getString("login").orEmpty(),
                        onBack = navigator::back,
                        onOpenRepo = { navigator.push(Route.RepoDetail.of(it.ownerLogin, it.name)) },
                    )
                }
            }

            // A newer build sits just above the dock, on the tabs only (never over a flow).
            AnimatedVisibility(
                visible = showDock && updateState.stage == com.mntx.gitpulse.update.UpdateStage.AVAILABLE && !dismissedUpdate,
                enter = fadeIn(tween(260)) + slideInVertically(tween(320, easing = Motion.Decelerate)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .navigationBarsPadding(),
            ) {
                com.mntx.gitpulse.update.UpdateBanner(
                    state = updateState,
                    onUpdate = {
                        haptics.select()
                        navigator.push(Route.Settings.path)
                    },
                    onDismiss = { dismissedUpdate = true },
                )
            }

            AnimatedVisibility(
                visible = showDock,
                enter = slideInVertically(tween(340, easing = Motion.Decelerate)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PulseBottomBar(
                    current = route,
                    onSelect = {
                        haptics.tap()
                        navigator.switchTab(it)
                    },
                    onSync = {
                        haptics.select()
                        navigator.push(Route.DiffReview.path)
                    },
                )
            }
        }
    }
}

private fun decode(value: String?): String =
    runCatching { java.net.URLDecoder.decode(value.orEmpty(), "UTF-8") }.getOrDefault(value.orEmpty())
