package com.mntx.gitpulse.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.ServiceLocator
import com.mntx.gitpulse.sync.ConflictPolicy
import com.mntx.gitpulse.sync.PullOptions
import com.mntx.gitpulse.sync.SyncEvent
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GhostButton
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.ProgressRing
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.SuccessCheckmark
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PullUiState(
    val running: Boolean = false,
    val done: Boolean = false,
    val progress: Float = 0f,
    val current: Int = 0,
    val total: Int = 0,
    val label: String = "",
    val error: String? = null,
    val conflicts: ConflictPolicy = ConflictPolicy.OVERWRITE,
    val files: Int = 0,
    val bytes: Long = 0,
    val dir: File? = null,
    val zipped: File? = null,
    val archiveJob: Boolean = false,
    /** False until the user confirms the pre-flight card (policy + destination). */
    val started: Boolean = false,
    /** Last few fetched paths, so the running screen shows motion instead of a dead zone. */
    val recent: List<String> = emptyList(),
)

class PullViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val services: ServiceLocator = ServiceLocator.get(app)
    private val _state = MutableStateFlow(PullUiState())
    val state = _state.asStateFlow()

    fun setPolicy(policy: ConflictPolicy) = _state.update { it.copy(conflicts = policy) }

    fun start(owner: String, repo: String, ref: String) {
        if (_state.value.running) return
        val dest = File(services.appContext.filesDir, "pulls/${owner}_${repo}_${ref}".replace('/', '_'))
        viewModelScope.launch {
            _state.update {
                it.copy(started = true, running = true, done = false, error = null, progress = 0f, recent = emptyList())
            }
            services.sync.pull(
                PullOptions(
                    owner = owner,
                    repo = repo,
                    ref = ref,
                    destination = dest,
                    onConflict = _state.value.conflicts,
                )
            ).collect { event ->
                when (event) {
                    is SyncEvent.Phase -> _state.update { it.copy(label = event.label) }
                    is SyncEvent.Progress -> _state.update {
                        val path = event.detail.substringAfterLast('·').trim().ifBlank { event.detail }
                        it.copy(
                            progress = event.fraction,
                            current = event.current,
                            total = event.total,
                            label = event.detail,
                            recent = if (path.isNotBlank()) (listOf(path) + it.recent).take(5) else it.recent,
                        )
                    }
                    is SyncEvent.Log -> _state.update { it.copy(label = event.message) }
                    is SyncEvent.Success -> _state.update {
                        it.copy(
                            running = false,
                            done = true,
                            recent = emptyList(),
                            progress = 1f,
                            label = event.message,
                            dir = dest,
                        )
                    }
                    is SyncEvent.Failure -> _state.update {
                        it.copy(running = false, error = event.message)
                    }
                    SyncEvent.Cancelled -> _state.update { it.copy(running = false) }
                }
            }
        }
    }

    /** Packages the downloaded folder into a shareable zip. */
    fun zipUp(onReady: (File) -> Unit) {
        val dir = _state.value.dir ?: return
        viewModelScope.launch {
            _state.update { it.copy(archiveJob = true) }
            val zip = runCatching { zipDirectory(dir) }.getOrNull()
            _state.update { it.copy(archiveJob = false, zipped = zip) }
            if (zip != null) onReady(zip)
        }
    }

    private suspend fun zipDirectory(dir: File): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = File(services.appContext.cacheDir, "exports/${dir.name}.zip")
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entry = file.relativeTo(dir).invariantSeparatorsPath
                zos.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zos, 128 * 1024) }
                zos.closeEntry()
            }
        }
        out
    }
}

@Composable
fun PullScreen(
    owner: String,
    repo: String,
    ref: String,
    onBack: () -> Unit,
    onShare: (File) -> Unit,
    vm: PullViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    // Kept in the VM's state, so a configuration change (rotation) doesn't force a re-zip.
    val readyZip = state.zipped

    // The flow now opens on a pre-flight card (destination + conflict policy) instead of
    // downloading with an invisible default; `started` also guards rotation re-entry.
    val begin = { vm.start(owner, repo, ref) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.6f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pull repository", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("$owner/$repo · $ref", style = monoStyle(10.5.toInt()), color = colors.textTertiary)
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                when {
                    !state.started -> {
                        PullSetup(state, onPolicy = vm::setPolicy)
                    }
                    state.done -> {
                        SuccessCheckmark(size = 96.dp)
                        Spacer(Modifier.height(20.dp))
                        Text("Repository downloaded", style = displayStyle(22, FontWeight.Bold), color = colors.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(state.label, style = bodyStyle(12.5.toInt()), color = colors.textSecondary)
                        Spacer(Modifier.height(18.dp))
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp)) {
                                PullStat("files", state.current.toString(), colors.accent, Modifier.weight(1f))
                                PullStat("branch", ref, colors.secondary, Modifier.weight(1f))
                                PullStat("format", "zip", colors.pink, Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            state.dir?.absolutePath ?: "",
                            style = monoStyle(9.5.toInt()),
                            color = colors.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        ProgressRing(
                            progress = state.progress,
                            indeterminate = state.progress <= 0.01f,
                            size = 176.dp,
                            label = if (state.total > 0) "${state.current}/${state.total}" else null,
                            sublabel = if (state.total > 0) "files" else "connecting",
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            state.label.ifBlank { "Fetching tree…" },
                            style = displayStyle(15, FontWeight.Medium),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.recent.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    state.recent.forEachIndexed { index, path ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "↓",
                                                style = monoStyle(10, FontWeight.Bold),
                                                color = colors.success.copy(alpha = 1f - index * 0.16f),
                                            )
                                            Spacer(Modifier.width(7.dp))
                                            Text(
                                                path,
                                                style = monoStyle(10),
                                                color = colors.textTertiary.copy(alpha = 1f - index * 0.16f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(16.dp))
                    PulseBanner(state.error!!, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
                }

                Spacer(Modifier.weight(1f))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 14.dp)) {
                if (!state.started) {
                    PulseButton(
                        text = "Download $ref",
                        icon = Icons.Rounded.ArrowDownward,
                        onClick = begin,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Cancel", onClick = onBack)
                } else if (state.done) {
                    val zip = readyZip
                    PulseButton(
                        text = when {
                            state.archiveJob -> "Zipping…"
                            zip != null -> "Share ${zip.name}"
                            else -> "Export as .zip"
                        },
                        icon = if (zip != null) Icons.Rounded.Share else Icons.Rounded.FolderZip,
                        loading = state.archiveJob,
                        onClick = {
                            val cached = readyZip
                            if (cached != null && cached.exists()) onShare(cached) else vm.zipUp(onShare)
                        },
                    )
                    if (zip != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            humanSize(zip.length()) + " · saved in app cache",
                            style = monoStyle(9.5.toInt()),
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Back to repository", onClick = onBack)
                } else {
                    if (!state.running) {
                        PulseButton("Retry download", icon = Icons.Rounded.ArrowDownward, onClick = { vm.start(owner, repo, ref) })
                        Spacer(Modifier.height(10.dp))
                    }
                    GhostButton("Cancel", onClick = onBack)
                }
            }
        }
    }
}

/** Pre-flight: what will be fetched, where it lands, and how collisions are handled. */
@Composable
private fun PullSetup(state: PullUiState, onPolicy: (ConflictPolicy) -> Unit) {
    val colors = PulseTheme.colors
    Column(Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth(), glow = true) {
            Column(Modifier.padding(18.dp)) {
                Text("Ready to pull", style = displayStyle(20, FontWeight.Bold, letterSpacing = -0.6), color = colors.textPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Everything is written to app storage first, then zipped. Nothing is pushed back.",
                    style = bodyStyle(11.5.toInt(), lineHeight = 17),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill("parallel", colors.accent)
                    InfoPill("resumable", colors.secondary)
                    InfoPill("git blobs", colors.pink)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("If a file already exists", style = displayStyle(12.5.toInt(), FontWeight.SemiBold), color = colors.textPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    when (state.conflicts) {
                        ConflictPolicy.OVERWRITE -> "Local copies are replaced with the branch version."
                        ConflictPolicy.SKIP_EXISTING -> "Existing files are kept untouched."
                        ConflictPolicy.KEEP_BOTH -> "Both are kept; the incoming copy gets a suffix."
                    },
                    style = bodyStyle(11, lineHeight = 16),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolicyRow("Overwrite", "Replace what's there", state.conflicts == ConflictPolicy.OVERWRITE) {
                        onPolicy(ConflictPolicy.OVERWRITE)
                    }
                    PolicyRow("Skip existing", "Never touch local files", state.conflicts == ConflictPolicy.SKIP_EXISTING) {
                        onPolicy(ConflictPolicy.SKIP_EXISTING)
                    }
                    PolicyRow("Keep both", "Incoming copy gets a suffix", state.conflicts == ConflictPolicy.KEEP_BOTH) {
                        onPolicy(ConflictPolicy.KEEP_BOTH)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = monoStyle(9.5.toInt(), FontWeight.Medium), color = tint)
    }
}

@Composable
private fun PolicyRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentSoft else colors.surface.copy(alpha = 0.45f))
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.45f) else colors.stroke,
                RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) colors.accent else Color.Transparent)
                .border(1.5.dp, if (selected) colors.accent else colors.stroke, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = colors.background, modifier = Modifier.size(11.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = displayStyle(12.5.toInt(), FontWeight.Medium), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(10.5.toInt()), color = colors.textTertiary)
        }
    }
}

@Composable
private fun PullStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = displayStyle(18, FontWeight.Bold, letterSpacing = -0.4), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = bodyStyle(10), color = colors.textTertiary)
    }
}

/** Design-review surface for the pull progress screen. */
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PullPreview(
    owner: String,
    repo: String,
    ref: String,
    progress: Float,
    current: Int,
    total: Int,
    label: String,
) {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.6f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = {})
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pull repository", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("$owner/$repo · $ref", style = monoStyle(10.5.toInt()), color = colors.textTertiary)
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                ProgressRing(
                    progress = progress,
                    indeterminate = false,
                    size = 176.dp,
                    label = "$current/$total",
                    sublabel = "files",
                )
                Spacer(Modifier.height(22.dp))
                Text(label, style = displayStyle(15, FontWeight.Medium), color = colors.textPrimary)
                Spacer(Modifier.height(18.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("2.4 MB", style = displayStyle(16, FontWeight.Bold), color = colors.secondary)
                            Text("received", style = bodyStyle(10), color = colors.textTertiary)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("main", style = displayStyle(16, FontWeight.Bold), color = colors.accent)
                            Text("branch", style = bodyStyle(10), color = colors.textTertiary)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 14.dp)) {
                GhostButton("Cancel", onClick = {})
            }
        }
    }
}

/** Design-review surface for the pre-flight (policy) step of a pull. */
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PullSetupPreview(ref: String = "main") {
    val colors = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.6f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = {})
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pull repository", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("mntgxo/gitpulse · $ref", style = monoStyle(10.5.toInt()), color = colors.textTertiary)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                PullSetup(PullUiState(conflicts = ConflictPolicy.KEEP_BOTH, started = false), onPolicy = {})
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 14.dp)) {
                PulseButton("Download $ref", icon = Icons.Rounded.ArrowDownward, onClick = {})
                Spacer(Modifier.height(10.dp))
                GhostButton("Cancel", onClick = {})
            }
        }
    }
}
