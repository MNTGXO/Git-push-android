package com.mntx.gitpulse.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.sync.ChangeKind
import com.mntx.gitpulse.sync.FileChange
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GhostButton
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.ProgressRing
import com.mntx.gitpulse.ui.components.PulseBar
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.StatusDot
import com.mntx.gitpulse.ui.components.SuccessCheckmark
import com.mntx.gitpulse.ui.navigation.LocalNavigator
import com.mntx.gitpulse.ui.theme.LocalPulse
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.rememberHaptics

/**
 * The hero flow: pick a zip (or a folder), watch the plan materialise, push it.
 * Every state is a distinct, animated surface.
 */
@Composable
fun PushScreen(onClose: () -> Unit, vm: SyncViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    val haptics = rememberHaptics(PulseTheme.settings.haptics)

    LaunchedEffect(Unit) { vm.loadRepos() }

    var zipName by remember { mutableStateOf<String?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            zipName = "archive.zip"
            vm.onZipPicked(uri, zipName)
            haptics.tap()
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.onFolderPicked(uri)
            haptics.tap()
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage == SyncStage.DONE) haptics.success()
        if (state.stage == SyncStage.FAILED) haptics.warn()
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.7f)

        Column(Modifier.fillMaxSize()) {
            SyncHeader(
                stage = state.stage,
                title = when (state.stage) {
                    SyncStage.IDLE, SyncStage.EXTRACTING, SyncStage.DIFFING -> "Push to GitHub"
                    SyncStage.REVIEW -> "Review changes"
                    SyncStage.PUSHING -> "Pushing…"
                    SyncStage.DONE -> "Pushed"
                    SyncStage.FAILED -> "Push failed"
                    SyncStage.PULLING -> "Pulling…"
                    SyncStage.PICKING -> "Choose source"
                },
                onBack = {
                    haptics.tap()
                    if (state.stage == SyncStage.DONE || state.stage == SyncStage.REVIEW) vm.reset() else onClose()
                },
                onClose = onClose,
            )

            AnimatedContent(
                targetState = state.stage,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(320, easing = Motion.Decelerate)) { it / 14 })
                        .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 20 })
                        .using(SizeTransform(clip = false))
                },
                label = "stage",
                modifier = Modifier.weight(1f),
            ) { stage ->
                when (stage) {
                    SyncStage.IDLE, SyncStage.PICKING -> PickSource(
                        state = state,
                        onZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
                        onFolder = { folderPicker.launch(null) },
                    )
                    SyncStage.EXTRACTING, SyncStage.DIFFING -> WorkingState(state)
                    SyncStage.REVIEW -> ReviewState(
                        state = state,
                        onToggleDelete = vm::setDeleteMissing,
                        onMessage = vm::setMessage,
                        onBranch = vm::selectBranch,
                        onPush = {
                            haptics.select()
                            vm.setDryRun(false)
                            vm.push()
                        },
                        onNewSource = { vm.reset() },
                    )
                    SyncStage.PUSHING -> PushingState(state, onCancel = vm::cancel)
                    SyncStage.DONE -> DoneState(state, onAgain = { vm.reset() }, onOpenRepo = {
                        val repo = state.selectedRepo
                        if (repo != null) {
                            onClose()
                            nav.push(com.mntx.gitpulse.ui.navigation.Route.RepoDetail.of(repo.ownerLogin, repo.name))
                        }
                    })
                    SyncStage.FAILED -> FailedState(state, onRetry = { vm.push() }, onReset = { vm.reset() })
                    else -> PickSource(state, onZip = {}, onFolder = {})
                }
            }
        }
    }
}

@Composable
private fun SyncHeader(stage: SyncStage, title: String, onBack: () -> Unit, onClose: () -> Unit) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4),
                color = colors.textPrimary,
            )
            Text(
                when (stage) {
                    SyncStage.PUSHING -> "Streaming blobs · parallel"
                    SyncStage.DONE -> "Branch updated"
                    SyncStage.REVIEW -> "Nothing is sent until you confirm"
                    else -> "zip · folder · any branch"
                },
                style = bodyStyle(11.5.toInt()),
                color = colors.textTertiary,
            )
        }
        PulseIconButton(Icons.Rounded.Close, "Close", onClick = onClose)
    }
}

/* ------------------------------------------------------------------ step 1: source */

@Composable
private fun PickSource(state: SyncUiState, onZip: () -> Unit, onFolder: () -> Unit) {
    val colors = PulseTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp, top = 6.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.FolderZip, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Zip → repository", style = displayStyle(17, FontWeight.SemiBold), color = colors.textPrimary)
                            Text(
                                "Extract, diff and mirror in one commit",
                                style = bodyStyle(12),
                                color = colors.textSecondary,
                            )
                        }
                        StatusDot(colors.secondary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Files that exist on GitHub but not in your zip are deleted from the branch — so the repo exactly matches your upload.",
                        style = bodyStyle(12.5.toInt(), lineHeight = 19),
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    PulseButton("Select .zip", icon = Icons.Rounded.FolderZip, onClick = onZip)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Pick a folder instead", icon = Icons.Rounded.Folder, onClick = onFolder)
                }
            }
        }
        item { RepoTargetCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniFact("Nothing to install", "git-less", Modifier.weight(1f))
                MiniFact("Any size", "chunked", Modifier.weight(1f))
                MiniFact("Atomic", "1 commit", Modifier.weight(1f))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("How the mirror works", style = displayStyle(14, FontWeight.SemiBold), color = colors.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    StepRow("1", "You pick a zip or a folder", "GitPulse extracts it safely and strips __MACOSX noise.")
                    StepRow("2", "We hash every file locally", "Git's own SHA-1 — only genuinely changed files upload.")
                    StepRow("3", "Missing paths are pruned", "Anything not in your zip is removed from the branch.")
                    StepRow("4", "One atomic commit", "A single tree + commit + ref update. No half-pushed states.")
                }
            }
        }
    }
}

@Composable
private fun MiniFact(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(title, style = displayStyle(12, FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = monoStyle(9.5.toInt()), color = colors.accent)
        }
    }
}

@Composable
private fun StepRow(step: String, title: String, subtitle: String) {
    val colors = PulseTheme.colors
    Row(Modifier.padding(vertical = 7.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(step, style = monoStyle(10, FontWeight.SemiBold), color = colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = displayStyle(12.5.toInt(), FontWeight.Medium), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(11.5.toInt(), lineHeight = 16), color = colors.textTertiary)
        }
    }
}

@Composable
private fun RepoTargetCard(state: SyncUiState) {
    val colors = PulseTheme.colors
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Destination", style = displayStyle(12, FontWeight.SemiBold), color = colors.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.selectedRepo?.private == true) Icons.Rounded.Lock else Icons.Rounded.AccountTree,
                        null, tint = colors.accent, modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.selectedRepo?.fullName ?: "Choose a repository",
                        style = displayStyle(14, FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${state.branches.size} branches · ${state.selectedBranch.ifBlank { "—" }}",
                        style = monoStyle(10),
                        color = colors.textTertiary,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(tween(220)) + fadeIn(tween(200)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.repos, key = { it.id }) { repo ->
                            PulseChip(
                                text = repo.name,
                                selected = state.selectedRepo?.id == repo.id,
                                onClick = { expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ working */

@Composable
private fun WorkingState(state: SyncUiState) {
    val colors = PulseTheme.colors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(
            progress = state.progress,
            indeterminate = state.progress <= 0.01f,
            size = 190.dp,
            label = if (state.progress > 0.01f) "${(state.progress * 100).toInt()}%" else null,
            sublabel = state.progressLabel.ifBlank { "Preparing…" },
        )
        Spacer(Modifier.height(28.dp))
        Text(
            if (state.stage == SyncStage.EXTRACTING) "Extracting safely" else "Diffing against GitHub",
            style = displayStyle(20, FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.lastArchiveName ?: "",
            style = monoStyle(11),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.total > 0) {
            Spacer(Modifier.height(20.dp))
            MonoLine("${state.current} / ${state.total}")
        }
    }
}

@Composable
private fun MonoLine(text: String) {
    Text(text, style = monoStyle(11), color = PulseTheme.colors.accent)
}

/* ------------------------------------------------------------------ step 2: review */

@Composable
private fun ReviewState(
    state: SyncUiState,
    onToggleDelete: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onBranch: (String) -> Unit,
    onPush: () -> Unit,
    onNewSource: () -> Unit,
) {
    val colors = PulseTheme.colors
    val diff = state.diff
    var expandedList by remember { mutableStateOf(true) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp, top = 2.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (diff?.isEmpty == true) "Already in sync" else "${state.changeCount} change${if (state.changeCount == 1) "" else "s"}",
                                style = displayStyle(26, FontWeight.Bold, letterSpacing = -0.8),
                                color = colors.textPrimary,
                            )
                            Text(
                                "${state.manifest?.files?.size ?: 0} files · ${humanSize(state.manifest?.totalBytes ?: 0)}",
                                style = bodyStyle(12),
                                color = colors.textSecondary,
                            )
                        }
                        DiffStatChips(diff)
                    }
                    Spacer(Modifier.height(16.dp))
                    val total = (diff?.added?.size ?: 0) + (diff?.modified?.size ?: 0) + (diff?.deleted?.size ?: 0)
                    if (total > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DiffBarSegment(diff?.added?.size ?: 0, total, colors.success, Modifier.weight(1f))
                            DiffBarSegment(diff?.modified?.size ?: 0, total, colors.amber, Modifier.weight(1f))
                            DiffBarSegment(diff?.deleted?.size ?: 0, total, colors.danger, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Uploading ${humanSize(diff?.uploadBytes ?: 0)} · ${diff?.unchanged ?: 0} already identical",
                            style = bodyStyle(11.5.toInt()),
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }

        item { BranchSelector(state, onBranch) }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Commit message", style = displayStyle(12, FontWeight.SemiBold), color = colors.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        if (state.commitMessage.isBlank()) {
                            Text(
                                "feat: sync from GitPulse",
                                style = bodyStyle(13.5.toInt()),
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value = state.commitMessage,
                            onValueChange = onMessage,
                            textStyle = bodyStyle(13.5.toInt()).copy(color = colors.textPrimary),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                        )
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ToggleRow(
                        title = "Mirror — delete missing files",
                        subtitle = "Remove repo files that aren't in this upload",
                        checked = state.deleteMissing,
                        onChecked = onToggleDelete,
                        icon = Icons.Rounded.DeleteOutline,
                        tint = colors.danger,
                    )
                }
            }
        }

        item { DiffListHeader(diff, expandedList) { expandedList = !expandedList } }

        if (expandedList) {
            val changes = diff?.changes ?: emptyList()
            items(changes.take(300), key = { it.path + it.kind }) { change ->
                ChangeRow(change)
            }
            if (changes.size > 300) {
                item {
                    Text(
                        "+${changes.size - 300} more files…",
                        style = bodyStyle(11.5.toInt()),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
            diff?.skipped?.takeIf { it.isNotEmpty() }?.let { skipped ->
                item {
                    SkippedNotice(skipped.size)
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp, top = 36.dp),
        ) {
            PulseButton(
                text = when {
                    state.diff?.isEmpty == true -> "Nothing new to push"
                    state.diff == null -> "Preparing…"
                    else -> "Push ${state.diff?.changes?.size ?: 0} change${if (state.changeCount == 1) "" else "s"}"
                },
                icon = Icons.AutoMirrored.Rounded.ArrowUpward,
                enabled = state.diff?.isEmpty == false && state.commitMessage.isNotBlank(),
                onClick = onPush,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Pushing to ${state.selectedRepo?.fullName ?: ""} · ${state.selectedBranch}",
                    style = monoStyle(10),
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun DiffStatChips(diff: com.mntx.gitpulse.sync.DiffSummary?) {
    val colors = PulseTheme.colors
    if (diff == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatPill("+${diff.added.size}", colors.success)
        StatPill("~${diff.modified.size}", colors.amber)
        StatPill("−${diff.deleted.size}", colors.danger)
    }
}

@Composable
private fun StatPill(text: String, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = monoStyle(10.5.toInt(), FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun DiffBarSegment(value: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        if (total == 0) 0f else value.toFloat() / total,
        tween(600, easing = Motion.Emphasized),
        label = "seg",
    )
    Box(modifier.height(5.dp).clip(RoundedCornerShape(50)).background(PulseTheme.colors.stroke).padding(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
private fun BranchSelector(state: SyncUiState, onBranch: (String) -> Unit) {
    val colors = PulseTheme.colors
    Column {
        Text(
            "Branch",
            style = displayStyle(12, FontWeight.SemiBold),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.branches.ifEmpty { emptyList() }) { branch ->
                PulseChip(
                    text = branch.name,
                    selected = branch.name == state.selectedBranch,
                    onClick = { onBranch(branch.name) },
                )
            }
            if (state.branches.isEmpty()) {
                item { PulseChip(text = state.selectedBranch.ifBlank { "main" }, selected = true, onClick = {}) }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    val colors = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(11), color = colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = tint,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = colors.stroke,
            ),
        )
    }
}

@Composable
private fun DiffListHeader(diff: com.mntx.gitpulse.sync.DiffSummary?, expanded: Boolean, onToggle: () -> Unit) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Description, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Changed files",
            style = displayStyle(13, FontWeight.SemiBold),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text("${diff?.changes?.size ?: 0}", style = monoStyle(11), color = colors.textTertiary)
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.ExpandMore, null, tint = colors.textTertiary,
            modifier = Modifier
                .size(18.dp),
        )
    }
}

@Composable
private fun ChangeRow(change: FileChange) {
    val colors = PulseTheme.colors
    val tint = when (change.kind) {
        ChangeKind.ADDED -> colors.success
        ChangeKind.MODIFIED -> colors.amber
        ChangeKind.DELETED -> colors.danger
        ChangeKind.UNCHANGED -> colors.textTertiary
    }
    val symbol = when (change.kind) {
        ChangeKind.ADDED -> "+"
        ChangeKind.MODIFIED -> "~"
        ChangeKind.DELETED -> "−"
        ChangeKind.UNCHANGED -> "="
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = monoStyle(11, FontWeight.SemiBold), color = tint)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            change.path,
            style = monoStyle(11),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when (change.kind) {
                ChangeKind.DELETED -> humanSize(change.remoteSize)
                else -> humanSize(change.local?.size ?: 0)
            },
            style = monoStyle(9.5.toInt()),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun SkippedNotice(count: Int) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Tune, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "$count file(s) skipped (junk folders, temp files or over the size limit)",
            style = bodyStyle(11),
            color = colors.textTertiary,
        )
    }
}

/* ------------------------------------------------------------------ step 3: pushing */

@Composable
private fun PushingState(state: SyncUiState, onCancel: () -> Unit) {
    val colors = PulseTheme.colors
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        ProgressRing(
            progress = state.progress,
            size = 168.dp,
            stroke = 9.dp,
            label = if (state.total > 0) "${state.current}/${state.total}" else null,
            sublabel = "blobs",
        )
        Spacer(Modifier.height(20.dp))
        Text(
            state.progressLabel.ifBlank { "Uploading…" },
            style = displayStyle(16, FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        GlassCard(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.logs.takeLast(60)) { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "›",
                            style = monoStyle(11),
                            color = when (line.level) {
                                com.mntx.gitpulse.sync.LogLevel.ERROR -> colors.danger
                                com.mntx.gitpulse.sync.LogLevel.SUCCESS -> colors.success
                                com.mntx.gitpulse.sync.LogLevel.WARN -> colors.amber
                                else -> colors.accent
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(line.text, style = monoStyle(10.5.toInt()), color = colors.textSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GhostButton("Cancel", icon = Icons.Rounded.Close, onClick = onCancel, danger = true)
    }
}

/* ------------------------------------------------------------------ step 4: done */

@Composable
private fun DoneState(state: SyncUiState, onAgain: () -> Unit, onOpenRepo: () -> Unit) {
    val colors = PulseTheme.colors
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SuccessCheckmark(size = 108.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            "Pushed to GitHub",
            style = displayStyle(24, FontWeight.Bold, letterSpacing = -0.6),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.successMessage ?: "",
            style = bodyStyle(13),
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        state.commitSha?.let { sha ->
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(sha.take(10), style = monoStyle(11.5.toInt()), color = colors.accent)
            }
        }
        Spacer(Modifier.height(26.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            val diff = state.diff
            Row(Modifier.padding(16.dp)) {
                DoneStat("added", diff?.added?.size ?: 0, colors.success, Modifier.weight(1f))
                DoneStat("changed", diff?.modified?.size ?: 0, colors.amber, Modifier.weight(1f))
                DoneStat("deleted", diff?.deleted?.size ?: 0, colors.danger, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(22.dp))
        PulseButton("Open repository", icon = Icons.Rounded.CheckCircle, onClick = onOpenRepo)
        Spacer(Modifier.height(10.dp))
        GhostButton("Push something else", icon = Icons.Rounded.RestartAlt, onClick = onAgain)
    }
}

@Composable
private fun DoneStat(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = displayStyle(22, FontWeight.Bold), color = tint)
        Text(label, style = bodyStyle(10.5.toInt()), color = colors.textTertiary)
    }
}

/* ------------------------------------------------------------------ failure */

@Composable
private fun FailedState(state: SyncUiState, onRetry: () -> Unit, onReset: () -> Unit) {
    val colors = PulseTheme.colors
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(colors.danger.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = colors.danger, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Push didn't complete", style = displayStyle(22, FontWeight.Bold), color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            state.error ?: "Something went wrong.",
            style = bodyStyle(13, lineHeight = 20),
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        PulseButton("Retry", icon = Icons.Rounded.RestartAlt, onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        GhostButton("Start over", onClick = onReset)
        if (state.logs.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(state.logs.takeLast(20)) { line ->
                        Text(line.text, style = monoStyle(10), color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ preview entry points */

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PushScreenReviewPreview(state: SyncUiState, onClose: () -> Unit = {}) {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        ReviewState(
            state = state,
            onToggleDelete = {},
            onMessage = {},
            onBranch = {},
            onPush = {},
            onNewSource = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PushScreenProgressPreview(state: SyncUiState, onCancel: () -> Unit = {}) {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        PushingState(state, onCancel)
    }
}
