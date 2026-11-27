package com.mntx.gitpulse.ui.repo

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.data.model.GhCommitSummary
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.ProgressRing
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import com.mntx.gitpulse.ui.util.relativeTime
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

class CommitViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as GitPulseApp).services.api
    private val _commit = AsyncValue<GhCommitSummary>()
    val commit = _commit.state

    fun load(owner: String, repo: String, sha: String) {
        viewModelScope.launch {
            _commit.load { api.commit(owner, repo, sha) }
        }
    }
}

@Composable
fun CommitDetailScreen(
    owner: String,
    repo: String,
    sha: String,
    onBack: () -> Unit,
    vm: CommitViewModel = viewModel(),
) {
    val colors = PulseTheme.colors
    val commit by vm.commit.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo, sha) { vm.load(owner, repo, sha) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.35f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Commit", style = displayStyle(18, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("$owner/$repo", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(sha.take(7), style = monoStyle(10), color = colors.accent)
                }
            }

            when (val c = commit) {
                is Async.Loading, is Async.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ProgressRing(progress = 0f, indeterminate = true, size = 64.dp, stroke = 3.dp)
                }
                is Async.Error -> Box(Modifier.padding(18.dp)) {
                    PulseBanner(c.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
                }
                is Async.Ready -> {
                    val detail = c.value
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                                Column(Modifier.padding(18.dp)) {
                                    Text(
                                        detail.headline.ifBlank { "(no message)" },
                                        style = displayStyle(18, FontWeight.SemiBold, letterSpacing = -0.4),
                                        color = colors.textPrimary,
                                    )
                                    val body = detail.message.substringAfter('\n').trim()
                                    if (body.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text(body, style = monoStyle(11), color = colors.textSecondary)
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(32.dp).clip(CircleShape)) {
                                            AsyncImage(
                                                model = detail.avatarUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                detail.authorLogin,
                                                style = displayStyle(12.5.toInt(), FontWeight.SemiBold),
                                                color = colors.textPrimary,
                                            )
                                            Text(
                                                relativeTime(detail.date),
                                                style = bodyStyle(10.5.toInt()),
                                                color = colors.textTertiary,
                                            )
                                        }
                                        if (detail.commit?.verification?.verified == true) {
                                            Icon(Icons.Rounded.Verified, null, tint = colors.success, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            val stats = detail.commit?.stats
                            GlassCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Stat("+${stats?.additions ?: 0}", "added", colors.success)
                                    Stat("−${stats?.deletions ?: 0}", "removed", colors.danger)
                                    Stat("${detail.commit?.files?.size ?: 0}", "files", colors.accent)
                                }
                            }
                        }
                        item {
                            Text(
                                "Changed files",
                                style = displayStyle(13, FontWeight.SemiBold),
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(detail.commit?.files ?: emptyList()) { file ->
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    when (file.status) {
                                                        "added" -> colors.success.copy(alpha = 0.16f)
                                                        "removed" -> colors.danger.copy(alpha = 0.16f)
                                                        else -> colors.amber.copy(alpha = 0.16f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                when (file.status) {
                                                    "added" -> "+"
                                                    "removed" -> "−"
                                                    else -> "~"
                                                },
                                                style = monoStyle(10, FontWeight.SemiBold),
                                                color = when (file.status) {
                                                    "added" -> colors.success
                                                    "removed" -> colors.danger
                                                    else -> colors.amber
                                                },
                                            )
                                        }
                                        Spacer(Modifier.width(9.dp))
                                        Text(
                                            file.filename,
                                            style = monoStyle(11),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "+${file.additions} −${file.deletions}",
                                            style = monoStyle(9),
                                            color = colors.textTertiary,
                                        )
                                    }
                                    file.patch?.let { patch ->
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(colors.backgroundAlt)
                                                .padding(8.dp),
                                        ) {
                                            Text(
                                                patch.lines().take(12).joinToString("\n"),
                                                style = monoStyle(9.5.toInt()),
                                                color = colors.textSecondary,
                                                maxLines = 12,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, tint: Color) {
    Column {
        Text(value, style = displayStyle(16, FontWeight.Bold), color = tint)
        Text(label, style = bodyStyle(10), color = PulseTheme.colors.textTertiary)
    }
}
