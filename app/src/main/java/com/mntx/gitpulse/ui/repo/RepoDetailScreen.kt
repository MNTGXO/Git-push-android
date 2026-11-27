package com.mntx.gitpulse.ui.repo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.automirrored.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mntx.gitpulse.data.model.GhCommitSummary
import com.mntx.gitpulse.data.model.GhContent
import com.mntx.gitpulse.data.model.GhIssue
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhWorkflowRun
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.sync.languageColor
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.ShimmerBlock
import com.mntx.gitpulse.ui.components.StatusDot
import com.mntx.gitpulse.ui.navigation.LocalNavigator
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.compactNumber
import com.mntx.gitpulse.ui.util.relativeTime
import com.mntx.gitpulse.ui.util.rememberHaptics

private enum class RepoTab(val label: String) { FILES("Files"), COMMITS("Commits"), ISSUES("Issues"), ACTIONS("Actions") }

@Composable
fun RepoDetailScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onShareZip: (owner: String, repo: String, ref: String) -> Unit,
    vm: RepoViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    val haptics = rememberHaptics(PulseTheme.settings.haptics)
    var tab by remember { mutableStateOf(RepoTab.FILES) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, name) { vm.load(owner, name) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.55f)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
            item {
                RepoTopBar(
                    title = "$owner/$name",
                    onBack = onBack,
                    onPull = {
                        haptics.tap()
                        onShareZip(owner, name, state.selectedBranch.ifBlank { "main" })
                    },
                    onStar = {
                        haptics.select()
                        vm.toggleStar(owner, name)
                    },
                    starred = state.starred,
                )
            }
            item {
                RepoHeader(
                    repo = state.repo,
                    languages = state.languages,
                )
            }
            item {
                BranchRow(
                    state = state,
                    onBranch = {
                        haptics.select()
                        vm.selectBranch(owner, name, it)
                    },
                )
            }
            item {
                TabRow(selected = tab, onSelect = {
                    haptics.tap()
                    tab = it
                })
            }
            if (actionMessage != null) {
                item {
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        PulseBanner(actionMessage!!, com.mntx.gitpulse.ui.components.BannerKind.SUCCESS)
                    }
                }
            }

            when (tab) {
                RepoTab.FILES -> filesSection(state, owner, name, vm, onFile = { content ->
                    haptics.tap()
                    nav.push(
                        Route.FileBrowser.of(
                            owner, name,
                            state.selectedBranch.ifBlank { "main" },
                            content.path,
                        )
                    )
                }, onDir = { content ->
                    haptics.tap()
                    vm.openPath(owner, name, content.path)
                })
                RepoTab.COMMITS -> commitsSection(state, owner, name, nav)
                RepoTab.ISSUES -> issuesSection(state)
                RepoTab.ACTIONS -> actionsSection(state) { id ->
                    haptics.select()
                    vm.rerun(owner, name, id)
                    actionMessage = "Workflow re-run requested"
                }
            }
        }

        // Floating sync CTA
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Pressable(
                onClick = {
                    haptics.tap()
                    nav.push(Route.DiffReview.path)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(colors.accent, colors.pink)))
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.FolderZip, null, tint = Color(0xFF0A0D14), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Sync a zip to this repo",
                        style = displayStyle(14, FontWeight.Bold),
                        color = Color(0xFF0A0D14),
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoTopBar(title: String, onBack: () -> Unit, onPull: () -> Unit, onStar: () -> Unit, starred: Boolean) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = displayStyle(15, FontWeight.SemiBold, letterSpacing = -0.3),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PulseIconButton(
            if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            "Star",
            tint = if (starred) colors.amber else colors.textPrimary,
            onClick = onStar,
        )
        Spacer(Modifier.width(8.dp))
        PulseIconButton(Icons.Rounded.ArrowDownward, "Pull", onClick = onPull)
    }
}

@Composable
internal fun RepoHeader(repo: Async<GhRepo>, languages: Map<String, Long>) {
    val colors = PulseTheme.colors
    when (repo) {
        is Async.Loading, is Async.Idle -> Box(Modifier.padding(horizontal = 18.dp)) {
            ShimmerBlock(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(22.dp))
        }
        is Async.Error -> Box(Modifier.padding(horizontal = 18.dp)) {
            PulseBanner(repo.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
        }
        is Async.Ready -> {
            val r = repo.value
            Box(Modifier.padding(horizontal = 18.dp)) {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.accentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (r.private) Icons.Rounded.Lock else Icons.Rounded.AccountTree,
                                    null, tint = colors.accent, modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.name,
                                    style = displayStyle(20, FontWeight.Bold, letterSpacing = -0.6),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(if (r.archived) colors.amber else colors.secondary, size = 5.dp)
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        if (r.archived) "archived" else if (r.private) "private · active" else "public · active",
                                        style = monoStyle(9.5.toInt()),
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                        if (!r.description.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(r.description!!, style = bodyStyle(12.5.toInt(), lineHeight = 18), color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(Icons.Rounded.Star, compactNumber(r.stargazersCount), colors.amber)
                            MetricPill(Icons.Rounded.ArrowDownward, compactNumber(r.forksCount), colors.info)
                            MetricPill(Icons.Rounded.RemoveRedEye, compactNumber(r.watchersCount), colors.secondary)
                            MetricPill(Icons.Rounded.Warning, compactNumber(r.openIssuesCount), colors.danger)
                            Spacer(Modifier.weight(1f))
                            Text(humanSize(r.size * 1024), style = monoStyle(9.5.toInt()), color = colors.textTertiary)
                        }
                        if (languages.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            LanguageBar(languages)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, tint: Color) {
    val colors = PulseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = monoStyle(10), color = colors.textSecondary)
    }
}

@Composable
private fun LanguageBar(languages: Map<String, Long>) {
    val colors = PulseTheme.colors
    val total = languages.values.sum().coerceAtLeast(1)
    val top = languages.entries.sortedByDescending { it.value }.take(5)
    Column {
        Row(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        ) {
            top.forEachIndexed { index, (lang, bytes) ->
                val fraction = bytes.toFloat() / total
                Box(
                    Modifier
                        .weight(fraction.coerceAtLeast(0.02f))
                        .height(6.dp)
                        .padding(end = if (index < top.size - 1) 2.dp else 0.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(languageColor(lang.lowercase())))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(top) { (lang, bytes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(languageColor(lang.lowercase()))))
                    Spacer(Modifier.width(4.dp))
                    Text(lang, style = bodyStyle(10), color = colors.textSecondary)
                    Text(
                        " ${(bytes * 100 / total)}%",
                        style = monoStyle(9),
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchRow(state: RepoState, onBranch: (String) -> Unit) {
    val colors = PulseTheme.colors
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.branches.ifEmpty { emptyList() }) { branch ->
            PulseChip(
                text = branch.name,
                selected = branch.name == state.selectedBranch,
                leadingIcon = Icons.Rounded.AccountTree,
                onClick = { onBranch(branch.name) },
            )
        }
        if (state.branches.isEmpty()) {
            item { PulseChip(text = state.selectedBranch.ifBlank { "main" }, selected = true, onClick = {}) }
        }
    }
}

@Composable
private fun TabRow(selected: RepoTab, onSelect: (RepoTab) -> Unit) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.7f))
            .padding(4.dp),
    ) {
        RepoTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) colors.accentSoft else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = displayStyle(12, if (active) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.0),
                    color = if (active) colors.accent else colors.textTertiary,
                )
            }
        }
    }
}

/* ---------------------------------------------------------------- files */

private fun androidx.compose.foundation.lazy.LazyListScope.filesSection(
    state: RepoState,
    owner: String,
    name: String,
    vm: RepoViewModel,
    onFile: (GhContent) -> Unit,
    onDir: (GhContent) -> Unit,
) {
    when (val contents = state.contents) {
        is Async.Loading, is Async.Idle -> items(6) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(14.dp))
            }
        }
        is Async.Error -> item {
            Box(Modifier.padding(18.dp)) {
                PulseBanner(contents.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
            }
        }
        is Async.Ready -> {
            if (state.path.isNotBlank()) {
                item {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "‹ back to root",
                            style = bodyStyle(11.5.toInt()),
                            color = PulseTheme.colors.accent,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { vm.openPath(owner, name, "") },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("/" + state.path, style = monoStyle(10), color = PulseTheme.colors.textTertiary)
                    }
                }
            }
            items(contents.value, key = { it.sha + it.path }) { content ->
                Box(Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) {
                    FileRow(content) {
                        if (content.isDir) onDir(content) else onFile(content)
                    }
                }
            }
            if (contents.value.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Folder,
                        title = "This folder is empty",
                        message = "Push a zip to populate it.",
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileRow(content: GhContent, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface.copy(alpha = 0.5f))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (content.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
                null,
                tint = if (content.isDir) colors.amber else Color(languageColor(content.extension)),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                content.name,
                style = displayStyle(12.5.toInt(), FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (content.isDir) "dir" else humanSize(content.size),
                style = monoStyle(9.5.toInt()),
                color = colors.textTertiary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

/* ---------------------------------------------------------------- commits */

private fun androidx.compose.foundation.lazy.LazyListScope.commitsSection(
    state: RepoState,
    owner: String,
    name: String,
    nav: com.mntx.gitpulse.ui.navigation.PulseNavigator,
) {
    when (val commits = state.commits) {
        is Async.Loading, is Async.Idle -> items(5) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(14.dp))
            }
        }
        is Async.Error -> item {
            Box(Modifier.padding(18.dp)) {
                PulseBanner(commits.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
            }
        }
        is Async.Ready -> items(commits.value) { commit ->
            Box(Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) {
                CommitRow(commit) {
                    nav.push(Route.CommitDetail.of(owner, name, commit.sha))
                }
            }
        }
    }
}

@Composable
internal fun CommitRow(commit: GhCommitSummary, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = 0.5f))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape)) {
                AsyncImage(
                    model = commit.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    commit.headline.ifBlank { "(no message)" },
                    style = displayStyle(12.5.toInt(), FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(commit.authorLogin, style = monoStyle(9.5.toInt()), color = colors.accent)
                    Text(" · ", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
                    Text(relativeTime(commit.date), style = bodyStyle(9.5.toInt()), color = colors.textTertiary)
                }
            }
            Text(
                commit.sha.take(7),
                style = monoStyle(9.5.toInt()),
                color = colors.textTertiary,
            )
        }
    }
}

/* ---------------------------------------------------------------- issues */

private fun androidx.compose.foundation.lazy.LazyListScope.issuesSection(state: RepoState) {
    when (val issues = state.issues) {
        is Async.Loading, is Async.Idle -> items(4) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(14.dp))
            }
        }
        is Async.Error -> item {
            Box(Modifier.padding(18.dp)) {
                PulseBanner(issues.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
            }
        }
        is Async.Ready -> {
            if (issues.value.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "No open issues",
                        message = "A clean tracker is a happy tracker.",
                    )
                }
            }
            items(issues.value) { issue ->
                Box(Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) { IssueRow(issue) }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: GhIssue) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.5f))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            null,
            tint = if (issue.isPullRequest) colors.accent else colors.success,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                issue.title,
                style = displayStyle(12.5.toInt(), FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "#${issue.number} · ${issue.user?.login ?: "unknown"} · ${relativeTime(issue.createdAt)}",
                style = monoStyle(9.5.toInt()),
                color = colors.textTertiary,
            )
        }
        if (issue.comments > 0) {
            Text("${issue.comments}💬", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
        }
    }
}

/* ---------------------------------------------------------------- actions */

private fun androidx.compose.foundation.lazy.LazyListScope.actionsSection(
    state: RepoState,
    onRerun: (Long) -> Unit,
) {
    when (val runs = state.runs) {
        is Async.Loading, is Async.Idle -> items(3) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(14.dp))
            }
        }
        is Async.Error -> item {
            Box(Modifier.padding(18.dp)) {
                PulseBanner(runs.message, com.mntx.gitpulse.ui.components.BannerKind.WARN)
            }
        }
        is Async.Ready -> {
            if (runs.value.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.PlayArrow,
                        title = "No workflow runs",
                        message = "Add a workflow to build on every push.",
                    )
                }
            }
            items(runs.value) { run ->
                Box(Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) {
                    WorkflowRow(run) { onRerun(run.id) }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(run: GhWorkflowRun, onRerun: () -> Unit) {
    val colors = PulseTheme.colors
    val success = run.conclusion == "success"
    val failed = run.conclusion == "failure"
    val running = run.status == "in_progress" || run.status == "queued"
    val tint = when {
        success -> colors.success
        failed -> colors.danger
        running -> colors.amber
        else -> colors.textTertiary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.5f))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.amber)
        } else {
            Icon(
                if (success) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                null, tint = tint, modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "Workflow",
                style = displayStyle(12.5.toInt(), FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "#${run.runNumber} · ${run.headBranch ?: "—"} · ${relativeTime(run.createdAt)}",
                style = monoStyle(9.5.toInt()),
                color = colors.textTertiary,
            )
        }
        if (!running) {
            PulseIconButton(Icons.Rounded.RestartAlt, "Re-run", size = 30.dp, background = Color.Transparent) { onRerun() }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun RepoDetailPreview(repo: GhRepo, files: List<GhContent>, commits: List<GhCommitSummary>) {
    val colors = PulseTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { RepoHeader(Async.Ready(repo), mapOf("Kotlin" to 70L, "TypeScript" to 30L)) }
        items(files) { FileRow(it) {} }
        item {
            Text("Commits", style = displayStyle(13, FontWeight.SemiBold), color = colors.textSecondary)
        }
        items(commits) { CommitRow(it) {} }
    }
}
