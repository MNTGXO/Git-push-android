package com.mntx.gitpulse.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GhostButton
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.ProgressRing
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.StatusDot
import com.mntx.gitpulse.ui.components.SuccessCheckmark
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.rememberHaptics

/**
 * Deploy a local build to *any* repository by typing `owner/repo` — no picking from the list,
 * no clone. Useful for org repos, other accounts and Enterprise hosts.
 *
 * Three steps, one screen: source → destination → commit. Nothing is sent until Push.
 */
@Composable
fun DeployScreen(onBack: () -> Unit, onOpenRepo: (String) -> Unit, vm: DeployViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val haptics = rememberHaptics(PulseTheme.settings.haptics)

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            haptics.tap()
            vm.onArchivePicked(uri, uri.lastPathSegment)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            haptics.tap()
            vm.onFolderPicked(uri)
        }
    }
    LaunchedEffect(state.stage) {
        if (state.stage == DeployStage.DONE) haptics.success()
        if (state.stage == DeployStage.FAILED) haptics.warn()
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.65f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Deploy", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text(
                        "Any repo · any folder · direct commits",
                        style = monoStyle(9.5.toInt()),
                        color = colors.textTertiary,
                    )
                }
                if (state.stage != DeployStage.IDLE) {
                    PulseIconButton(Icons.Rounded.RestartAlt, "Start over", onClick = { haptics.tap(); vm.reset() })
                }
            }

            AnimatedContent(
                targetState = state.stage,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(300, easing = Motion.Decelerate)) { it / 16 })
                        .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 22 })
                        .using(SizeTransform(clip = false))
                },
                modifier = Modifier.weight(1f),
                label = "deploy-stage",
            ) { stage ->
                when (stage) {
                    DeployStage.IDLE, DeployStage.ANALYSING -> ComposeTarget(
                        state = state,
                        analysing = stage == DeployStage.ANALYSING,
                        onTarget = vm::setTarget,
                        onBranch = vm::setBranch,
                        onSubPath = vm::setSubPath,
                        onZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
                        onFolder = { folderPicker.launch(null) },
                        onCreateRepo = { haptics.select(); vm.createRepo() },
                        onPrivate = vm::setPrivateRepo,
                        onDismissOffer = vm::clearCreateOffer,
                    )
                    DeployStage.REVIEW -> DeployReview(
                        state = state,
                        onMessage = vm::setMessage,
                        onDeleteMissing = vm::setDeleteMissing,
                        onPush = { haptics.select(); vm.push() },
                    )
                    DeployStage.PUSHING -> DeployProgress(state)
                    DeployStage.DONE -> DeployDone(
                        state = state,
                        onAgain = { vm.reset() },
                        onOpen = { onOpenRepo(state.target) },
                    )
                    DeployStage.FAILED -> DeployFailed(state, onRetry = { vm.analyse() }, onReset = { vm.reset() })
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ step 1 */

@Composable
private fun ComposeTarget(
    state: DeployState,
    analysing: Boolean,
    onTarget: (String) -> Unit,
    onBranch: (String) -> Unit,
    onSubPath: (String) -> Unit,
    onZip: () -> Unit,
    onFolder: () -> Unit,
    onCreateRepo: () -> Unit,
    onPrivate: (Boolean) -> Unit,
    onDismissOffer: () -> Unit,
) {
    val colors = PulseTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 40.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = colors.accent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Destination", style = displayStyle(13, FontWeight.SemiBold), color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        if (state.targetValid) StatusDot(colors.success) else StatusDot(colors.textTertiary, pulse = false)
                    }
                    Spacer(Modifier.height(12.dp))
                    Field(
                        value = state.target,
                        onValue = onTarget,
                        placeholder = "owner/repo",
                        mono = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Field(
                                value = state.branch,
                                onValue = onBranch,
                                placeholder = "branch (main)",
                                mono = true,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            Field(
                                value = state.subPath,
                                onValue = onSubPath,
                                placeholder = "folder/ (root)",
                                mono = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            !state.targetValid -> "Type the repository as owner/repo — you can deploy to repos this account can push to."
                            state.subPath.isBlank() -> "Files land at the repository root on ${state.branch.ifBlank { "main" }}."
                            else -> "Files land inside ${state.subPath}/ on ${state.branch.ifBlank { "main" }}."
                        },
                        style = bodyStyle(11.5.toInt(), lineHeight = 17),
                        color = colors.textTertiary,
                    )
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderZip, null, tint = colors.secondary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Source", style = displayStyle(13, FontWeight.SemiBold), color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.manifest?.let { "${it.files.size} files · ${humanSize(it.totalBytes)} · ${state.sourceName}" }
                            ?: "Pick the build you want online: a zip, or a folder straight from storage.",
                        style = bodyStyle(12, lineHeight = 18),
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    PulseButton(
                        text = if (analysing) "Analysing…" else "Select .zip",
                        icon = Icons.Rounded.FolderZip,
                        loading = analysing,
                        enabled = !analysing,
                        onClick = onZip,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Pick a folder", icon = Icons.Rounded.Folder, enabled = !analysing, onClick = onFolder)
                }
            }
        }

        if (state.canCreateRepo) {
            item {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${state.target} doesn't exist yet",
                            style = displayStyle(13.5.toInt(), FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create it now and deploy straight into it — no browser, no setup.",
                            style = bodyStyle(11.5.toInt(), lineHeight = 17),
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulseChip("Private", state.privateRepo) { onPrivate(true) }
                            PulseChip("Public", !state.privateRepo) { onPrivate(false) }
                        }
                        Spacer(Modifier.height(12.dp))
                        PulseButton(
                            text = "Create ${state.target}",
                            icon = Icons.Rounded.Add,
                            loading = state.creating,
                            enabled = !state.creating,
                            onClick = onCreateRepo,
                        )
                        Spacer(Modifier.height(6.dp))
                        GhostButton("Use another repo", onClick = onDismissOffer)
                    }
                }
            }
        }

        state.error?.let { message ->
            item { PulseBanner(message, com.mntx.gitpulse.ui.components.BannerKind.ERROR) }
        }

        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Bullet("No clone, no checkout", "Blobs are uploaded and committed through the git-data API.")
                Bullet("Additive by default", "Deploy never deletes remote files unless you turn mirror on.")
                Bullet("One commit", "Tree → commit → branch update happen atomically.")
            }
        }
    }
}

@Composable
private fun Bullet(title: String, subtitle: String) {
    val colors = PulseTheme.colors
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.TopStart) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent),
            )
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = displayStyle(12.5.toInt(), FontWeight.Medium), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(11, lineHeight = 16), color = colors.textTertiary)
        }
    }
}

@Composable
private fun Field(value: String, onValue: (String) -> Unit, placeholder: String, mono: Boolean) {
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        if (value.isBlank()) {
            Text(placeholder, style = if (mono) monoStyle(12) else bodyStyle(12.5.toInt()), color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = (if (mono) monoStyle(12) else bodyStyle(12.5.toInt())).copy(color = colors.textPrimary),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ------------------------------------------------------------------ step 2 */

@Composable
private fun DeployReview(
    state: DeployState,
    onMessage: (String) -> Unit,
    onDeleteMissing: (Boolean) -> Unit,
    onPush: () -> Unit,
) {
    val colors = PulseTheme.colors
    val diff = state.diff
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 150.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        when {
                            diff == null -> "Preparing…"
                            diff.isEmpty -> "Nothing to deploy"
                            else -> "${diff.changes.size} file${if (diff.changes.size == 1) "" else "s"} → ${state.target}"
                        },
                        style = displayStyle(19, FontWeight.Bold, letterSpacing = -0.6),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("+${diff?.added?.size ?: 0}", style = monoStyle(11, FontWeight.SemiBold), color = colors.success)
                        Text("~${diff?.modified?.size ?: 0}", style = monoStyle(11, FontWeight.SemiBold), color = colors.amber)
                        Text("−${diff?.deleted?.size ?: 0}", style = monoStyle(11, FontWeight.SemiBold), color = colors.danger)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "branch ${state.branch.ifBlank { "main" }} · ${if (state.subPath.isBlank()) "repo root" else "${state.subPath}/"}",
                        style = monoStyle(10),
                        color = colors.textTertiary,
                    )
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Commit message", style = displayStyle(12, FontWeight.SemiBold), color = colors.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Field(
                        value = state.message,
                        onValue = onMessage,
                        placeholder = "deploy: ${state.sourceName}",
                        mono = false,
                    )
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface.copy(alpha = 0.6f))
                    .border(1.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDeleteMissing(!state.deleteMissing) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline, null,
                    tint = if (state.deleteMissing) colors.danger else colors.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mirror mode", style = displayStyle(12.5.toInt(), FontWeight.Medium), color = colors.textPrimary)
                    Text(
                        if (state.deleteMissing) "Files not in this build will be deleted" else "Off — deploy only adds and updates",
                        style = bodyStyle(11),
                        color = colors.textTertiary,
                    )
                }
                PulseChip(if (state.deleteMissing) "on" else "off", state.deleteMissing) {
                    onDeleteMissing(!state.deleteMissing)
                }
            }
        }

        items((diff?.changes ?: emptyList()).take(40).size) { index ->
            DeployChangeRow(diff!!.changes[index])
        }
        if ((diff?.changes?.size ?: 0) > 40) {
            item {
                Text(
                    "+${diff!!.changes.size - 40} more files…",
                    style = bodyStyle(11), color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 30.dp, bottom = 14.dp),
        ) {
            PulseButton(
                text = if (diff?.isEmpty == true) "Nothing to deploy" else "Deploy to ${state.target}",
                icon = Icons.Rounded.CloudUpload,
                enabled = diff?.isEmpty == false,
                onClick = onPush,
            )
        }
    }
}

/** One planned change — same visual grammar as the push review, so the two flows feel related. */
@Composable
private fun DeployChangeRow(change: com.mntx.gitpulse.sync.FileChange) {
    val colors = PulseTheme.colors
    val kind = change.kind
    val tint = when (kind) {
        com.mntx.gitpulse.sync.ChangeKind.ADDED -> colors.success
        com.mntx.gitpulse.sync.ChangeKind.MODIFIED -> colors.amber
        com.mntx.gitpulse.sync.ChangeKind.DELETED -> colors.danger
        else -> colors.textTertiary
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
            Text(
                when (kind) {
                    com.mntx.gitpulse.sync.ChangeKind.ADDED -> "+"
                    com.mntx.gitpulse.sync.ChangeKind.MODIFIED -> "~"
                    com.mntx.gitpulse.sync.ChangeKind.DELETED -> "−"
                    else -> "="
                },
                style = monoStyle(11, FontWeight.SemiBold),
                color = tint,
            )
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
            humanSize(if (kind == com.mntx.gitpulse.sync.ChangeKind.DELETED) change.remoteSize else change.local?.size ?: 0),
            style = monoStyle(9.5.toInt()),
            color = colors.textTertiary,
        )
    }
}

/* ------------------------------------------------------------------ steps 3-5 */

@Composable
private fun DeployProgress(state: DeployState) {
    val colors = PulseTheme.colors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(
            progress = state.progress,
            indeterminate = state.progress <= 0.01f,
            size = 180.dp,
            label = if (state.progress > 0.01f) "${(state.progress * 100).toInt()}%" else null,
            sublabel = if (state.total > 0) "${state.current}/${state.total}" else "uploading",
        )
        Spacer(Modifier.height(24.dp))
        Text(
            state.progressLabel.ifBlank { "Pushing to ${state.target}" },
            style = displayStyle(15, FontWeight.Medium),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(state.logs.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    state.logs.takeLast(4).forEach { line ->
                        Text(
                            line.text,
                            style = monoStyle(9.5.toInt()),
                            color = when (line.level) {
                                com.mntx.gitpulse.sync.LogLevel.ERROR -> colors.danger
                                com.mntx.gitpulse.sync.LogLevel.WARN -> colors.amber
                                com.mntx.gitpulse.sync.LogLevel.SUCCESS -> colors.success
                                else -> colors.textTertiary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeployDone(state: DeployState, onAgain: () -> Unit, onOpen: () -> Unit) {
    val colors = PulseTheme.colors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SuccessCheckmark(size = 104.dp)
        Spacer(Modifier.height(20.dp))
        Text("Deployed", style = displayStyle(26, FontWeight.Bold, letterSpacing = -0.8), color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            state.resultMessage ?: "Branch updated",
            style = bodyStyle(13),
            color = colors.textSecondary,
            maxLines = 2,
        )
        Spacer(Modifier.height(18.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.target.substringAfter('/'), style = displayStyle(14, FontWeight.Bold), color = colors.accent, maxLines = 1)
                    Text("repo", style = bodyStyle(10), color = colors.textTertiary)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.branch.ifBlank { "main" }, style = displayStyle(14, FontWeight.Bold), color = colors.secondary, maxLines = 1)
                    Text("branch", style = bodyStyle(10), color = colors.textTertiary)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.commitSha?.take(7) ?: "—", style = monoStyle(12, FontWeight.SemiBold), color = colors.pink, maxLines = 1)
                    Text("commit", style = bodyStyle(10), color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PulseButton("Open repository", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = onOpen)
        Spacer(Modifier.height(10.dp))
        GhostButton("Deploy something else", icon = Icons.Rounded.RestartAlt, onClick = onAgain)
    }
}

@Composable
private fun DeployFailed(state: DeployState, onRetry: () -> Unit, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseBanner(state.error ?: "The deploy failed.", com.mntx.gitpulse.ui.components.BannerKind.ERROR)
        Spacer(Modifier.height(18.dp))
        PulseButton("Try again", icon = Icons.Rounded.Check, onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        GhostButton("Start over", onClick = onReset)
    }
}

/** Design-review surface: the composer step with a valid target, no ViewModel involved. */
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun DeployPreview(state: DeployState) {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.65f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = {})
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Deploy", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("Any repo · any folder · direct commits", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
                }
                PulseIconButton(Icons.Rounded.RestartAlt, "Start over", onClick = {})
            }
            Box(Modifier.weight(1f)) {
                when (state.stage) {
                    DeployStage.REVIEW -> DeployReview(
                        state = state,
                        onMessage = {},
                        onDeleteMissing = {},
                        onPush = {},
                    )
                    DeployStage.PUSHING -> DeployProgress(state)
                    DeployStage.DONE -> DeployDone(state, onAgain = {}, onOpen = {})
                    DeployStage.FAILED -> DeployFailed(state, onRetry = {}, onReset = {})
                    else -> ComposeTarget(
                        state = state,
                        analysing = false,
                        onTarget = {},
                        onBranch = {},
                        onSubPath = {},
                        onZip = {},
                        onFolder = {},
                        onCreateRepo = {},
                        onPrivate = {},
                        onDismissOffer = {},
                    )
                }
            }
        }
    }
}
