package com.mntx.gitpulse.ui.code

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GhostButton
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.theme.Motion
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.rememberHaptics
import java.io.File

/**
 * Edit any text file in the repo — or create a brand new one — then commit it, move it,
 * delete it, copy it or save it to the device. This is the piece mobile GitHub clients
 * normally make you open a laptop for.
 */
@Composable
fun CodeEditorScreen(
    owner: String,
    repo: String,
    branch: String,
    path: String,
    onBack: () -> Unit,
    onCommitted: () -> Unit,
    vm: FileEditorViewModel = viewModel(),
) {
    val colors = PulseTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(PulseTheme.settings.haptics)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var repath by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf(path) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, branch, path) { vm.load(owner, repo, branch, path) }
    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage != null) {
            haptics.success()
            onCommitted()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.warn()
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.35f)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            EditorHeader(
                title = path.substringAfterLast('/').ifBlank { "New file" },
                subtitle = buildString {
                    append(if (state.exists) path else "create · $path")
                    append(" · ")
                    append(branch)
                    if (state.size > 0) append(" · ${humanSize(state.size)}")
                },
                dirty = state.dirty,
                onBack = {
                    // Nothing to lose: leave. Dirty buffer: ask first.
                    if (state.dirty) confirmDiscard = true else onBack()
                },
                onMenu = { menuOpen = !menuOpen },
                onSave = { haptics.select(); vm.save() },
                saving = state.saving,
                canSave = !state.binary && !state.saving && (state.dirty || !state.exists),
                exists = state.exists,
            )

            AnimatedVisibility(menuOpen, enter = expandVertically(tween(180)) + fadeIn(), exit = shrinkVertically(tween(140)) + fadeOut()) {
                ActionRail(
                    onCopy = { clipboard.setText(AnnotatedString(state.text)); haptics.tap(); menuOpen = false },
                    onExport = { vm.export { file -> share(context, file) }; menuOpen = false },
                    onRevert = { vm.updateText(state.original); menuOpen = false },
                    onRename = { repath = true; menuOpen = false },
                    onDelete = { confirmDelete = true; menuOpen = false },
                    canDelete = state.exists,
                )
            }

            AnimatedVisibility(state.error != null) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PulseBanner(state.error ?: "", com.mntx.gitpulse.ui.components.BannerKind.ERROR)
                }
            }
            AnimatedVisibility(state.savedMessage != null) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PulseBanner(state.savedMessage ?: "", com.mntx.gitpulse.ui.components.BannerKind.SUCCESS)
                }
            }

            if (state.binary) {
                Box(Modifier.fillMaxWidth().padding(18.dp)) {
                    PulseBanner(
                        "Binary file (${humanSize(state.size)}) — download it instead of editing.",
                        com.mntx.gitpulse.ui.components.BannerKind.INFO,
                    )
                }
            }

            CodeSurface(
                value = state.text,
                onValueChange = vm::updateText,
                enabled = !state.loading && !state.binary,
                modifier = Modifier.weight(1f),
            )

            CommitBar(
                message = state.message,
                onMessage = vm::updateMessage,
                dirty = state.dirty,
                exists = state.exists,
                saving = state.saving,
                onSave = { haptics.select(); vm.save() },
            )
        }

        if (confirmDelete) {
            ConfirmSheet(
                title = "Delete ${path.substringAfterLast('/')}?",
                body = "This commits the deletion to $branch. The file stays in history, so it can be restored from the commit.",
                confirmLabel = "Delete file",
                onConfirm = { confirmDelete = false; vm.delete { haptics.success(); onBack() } },
                onDismiss = { confirmDelete = false },
            )
        }

        if (confirmDiscard) {
            ConfirmSheet(
                title = "Discard changes?",
                body = "You have edits that aren't committed to $branch yet.",
                confirmLabel = "Discard and leave",
                destructive = true,
                onConfirm = { confirmDiscard = false; onBack() },
                onDismiss = { confirmDiscard = false },
            )
        }

        if (repath) {
            RepathSheet(
                value = newPath,
                onValue = { newPath = it },
                onConfirm = { repath = false; vm.rename(newPath) { haptics.success(); onCommitted() } },
                onDismiss = { repath = false },
            )
        }
    }
}

@Composable
private fun EditorHeader(
    title: String,
    subtitle: String,
    dirty: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    canSave: Boolean,
    exists: Boolean,
) {
    val colors = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.EditNote, null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = displayStyle(15, FontWeight.SemiBold, letterSpacing = -0.3),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dirty) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.amber))
                }
            }
            Text(subtitle, style = monoStyle(9.5.toInt()), color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PulseIconButton(Icons.Rounded.MoreVert, "Actions", onClick = onMenu)
        Spacer(Modifier.width(4.dp))
        PulseIconButton(
            icon = if (exists) Icons.Rounded.Save else Icons.Rounded.Check,
            contentDescription = if (exists) "Save" else "Create",
            onClick = onSave,
            tint = if (canSave) colors.accent else colors.textTertiary,
            enabled = canSave || saving,
        )
    }
}

@Composable
private fun ActionRail(
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onRevert: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    val colors = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailAction(Icons.Rounded.ContentCopy, "Copy", colors.textSecondary, onCopy)
        RailAction(Icons.Rounded.Download, "Export", colors.textSecondary, onExport)
        RailAction(Icons.Rounded.Undo, "Revert", colors.textSecondary, onRevert)
        RailAction(Icons.Rounded.RestartAlt, "Move", colors.info, onRename)
        if (canDelete) RailAction(Icons.Rounded.DeleteOutline, "Delete", colors.danger, onDelete)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = PulseTheme.colors
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.7f))
            .border(1.dp, colors.stroke, RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = bodyStyle(9.5.toInt(), FontWeight.Medium), color = colors.textTertiary)
    }
}

/**
 * The code surface: a syntax-tinted, line-numbered editor.
 *
 * Text and caret are painted as an [AnnotatedString]; a transparent [BasicTextField] sits on top
 * so IME, selection, paste and a11y all behave natively while the buffer stays highlighted.
 * The field grows with its content inside one vertical scroll, so long files stay smooth.
 */
@Composable
private fun CodeSurface(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, selection = TextRange(value.length))
    }
    val lines = remember(field.text) { field.text.count { it == '\n' } + 1 }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val mono = monoStyle(11.5.toInt()).copy(color = colors.textPrimary, lineHeight = 19.sp)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.backgroundAlt.copy(alpha = 0.92f))
            .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (enabled) colors.success else colors.textTertiary))
                Spacer(Modifier.width(7.dp))
                Text("$lines lines · ${field.text.length} chars", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text("UTF-8", style = monoStyle(9), color = colors.textTertiary.copy(alpha = 0.7f), modifier = Modifier.padding(end = 12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.stroke))
        Row(
            Modifier
                .fillMaxWidth()
                .verticalScroll(vScroll),
        ) {
            // Gutter
            Column(Modifier.width(40.dp).padding(start = 10.dp, top = 12.dp, end = 4.dp)) {
                repeat(lines) { index ->
                    Text(
                        (index + 1).toString().padStart(3),
                        style = monoStyle(11).copy(lineHeight = 19.sp),
                        color = colors.textTertiary.copy(alpha = 0.5f),
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    // Wrapping keeps every character reachable on a phone; the gutter column
                    // lines up 1:1 only when each logical line occupies exactly one visual line,
                    // which is why soft wrap is off — the code scrolls with the editor instead.
                    .horizontalScroll(hScroll)
                    .padding(top = 12.dp, end = 12.dp, bottom = 60.dp),
            ) {
                if (enabled) {
                    Box {
                        // Painted, highlighted buffer.
                        Text(
                            text = field.text.ifEmpty { " " }.let { buffer ->
                                buildAnnotatedString {
                                    buffer.split('\n').forEachIndexed { index, line ->
                                        append(highlight(line, colors.textPrimary))
                                        if (index < buffer.count { it == '\n' }) append("\n")
                                    }
                                }
                            },
                            style = mono,
                            softWrap = false,
                        )
                        // Invisible input on top: native caret, selection, IME.
                        BasicTextField(
                            value = field,
                            onValueChange = {
                                field = it
                                onValueChange(it.text)
                            },
                            textStyle = mono.copy(color = Color.Transparent),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp),
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                } else {
                    Text(
                        field.text.ifBlank { "(loading…)" },
                        style = monoStyle(11.5.toInt()),
                        color = colors.textSecondary,
                        softWrap = false,
                        modifier = Modifier.defaultMinSize(minHeight = 240.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitBar(
    message: String,
    onMessage: (String) -> Unit,
    dirty: Boolean,
    exists: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
) {
    val colors = PulseTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background)))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp, top = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.EditNote, null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (message.isBlank()) {
                    Text(
                        if (exists) "chore: update file" else "feat: add file",
                        style = bodyStyle(12),
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = message,
                    onValueChange = onMessage,
                    textStyle = bodyStyle(12).copy(color = colors.textPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        PulseButton(
            text = when {
                saving -> "Committing…"
                exists && dirty -> "Commit change"
                !exists -> "Create file"
                else -> "No changes"
            },
            icon = if (exists) Icons.Rounded.Save else Icons.Rounded.Check,
            loading = saving,
            enabled = (dirty || !exists) && !saving,
            onClick = onSave,
        )
    }
}

@Composable
private fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    SheetScaffold(onDismiss) {
        Text(title, style = displayStyle(19, FontWeight.Bold), color = PulseTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, style = bodyStyle(12.5.toInt(), lineHeight = 19), color = PulseTheme.colors.textSecondary)
        Spacer(Modifier.height(18.dp))
        PulseButton(
            confirmLabel,
            icon = if (destructive) Icons.Rounded.DeleteOutline else Icons.Rounded.Check,
            colors = if (destructive) listOf(PulseTheme.colors.danger, PulseTheme.colors.danger.copy(alpha = 0.8f)) else null,
            onClick = onConfirm,
        )
        Spacer(Modifier.height(8.dp))
        GhostButton("Cancel", onClick = onDismiss)
    }
}

@Composable
private fun RepathSheet(
    value: String,
    onValue: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PulseTheme.colors
    SheetScaffold(onDismiss) {
        Text("Move or rename", style = displayStyle(19, FontWeight.Bold), color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text("New path in the repository", style = bodyStyle(12), color = colors.textTertiary)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                textStyle = monoStyle(12).copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        PulseButton("Move file", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = onConfirm)
        Spacer(Modifier.height(8.dp))
        GhostButton("Cancel", onClick = onDismiss)
    }
}

/** Minimal bottom sheet: scrim + anchored card, animated in. */
@Composable
private fun SheetScaffold(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.strokeStrong, RoundedCornerShape(24.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            content = content,
        )
    }
}

private fun share(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save ${file.name}"))
    }
}

/**
 * Design-review surface: the editor chrome with a fully loaded buffer. Deliberately does not
 * wire a ViewModel so screenshots never touch the network.
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun EditorPreview(path: String, branch: String, text: String) {
    val colors = PulseTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        EditorHeader(
            title = path.substringAfterLast('/'),
            subtitle = "$path · $branch",
            dirty = true,
            onBack = {},
            onMenu = {},
            onSave = {},
            saving = false,
            canSave = true,
            exists = true,
        )
        ActionRail(onCopy = {}, onExport = {}, onRevert = {}, onRename = {}, onDelete = {}, canDelete = true)
        Spacer(Modifier.height(6.dp))
        CodeSurface(value = text, onValueChange = {}, enabled = true, modifier = Modifier.weight(1f))
        CommitBar(
            message = "feat: ship the landing page",
            onMessage = {},
            dirty = true,
            exists = true,
            saving = false,
            onSave = {},
        )
    }
}
