package com.mntx.gitpulse.ui.activity

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.data.model.GhNotification
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhWorkflowRun
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.sync.languageColor
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.ShimmerBlock
import com.mntx.gitpulse.ui.components.StatusDot
import com.mntx.gitpulse.ui.navigation.LocalNavigator
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import com.mntx.gitpulse.ui.util.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class ActivityData(
    val notifications: List<GhNotification> = emptyList(),
    val trending: List<GhRepo> = emptyList(),
    val runs: List<Pair<GhRepo, GhWorkflowRun>> = emptyList(),
)

class ActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as GitPulseApp).services.api
    private val _data = AsyncValue<ActivityData>()
    val data = _data.state

    fun load() {
        viewModelScope.launch {
            _data.load {
                val notifications = async { runCatching { api.notifications() }.getOrDefault(emptyList()) }
                val trending = async {
                    runCatching { api.searchRepos("stars:>20000 created:>2024-01-01", sort = "stars").items }
                        .getOrDefault(emptyList())
                }
                val repos = async { runCatching { api.repositories(perPage = 12) }.getOrDefault(emptyList()) }
                val runPairs = mutableListOf<Pair<GhRepo, GhWorkflowRun>>()
                repos.await().take(6).forEach { repo ->
                    runCatching { api.workflows(repo.ownerLogin, repo.name).workflowRuns.firstOrNull() }
                        .getOrNull()?.let { runPairs += repo to it }
                }
                ActivityData(
                    notifications = notifications.await(),
                    trending = trending.await(),
                    runs = runPairs,
                )
            }
        }
    }
}

@Composable
fun ActivityScreen(vm: ActivityViewModel = viewModel()) {
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    val state by vm.data.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.42f)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 130.dp, top = 6.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pulse", style = displayStyle(30, FontWeight.Bold, letterSpacing = -1.2), color = colors.textPrimary)
                        Spacer(Modifier.width(10.dp))
                        StatusDot(colors.success, size = 5.dp)
                    }
                    Text("Notifications, CI and what's hot right now", style = bodyStyle(12), color = colors.textTertiary)
                }
            }

            when (val d = state) {
                is Async.loading, is Async.Idle -> items(6) {
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        ShimmerBlock(Modifier.fillMaxWidth().height(70.dp), RoundedCornerShape(16.dp))
                    }
                }
                is Async.Error -> item {
                    EmptyState(Icons.Rounded.Notifications, "Couldn't load your pulse", d.message)
                }
                is Async.Ready -> {
                    if (d.value.runs.isNotEmpty()) {
                        item { SectionLabel("CI activity", "Latest workflow run per repo") }
                        items(d.value.runs) { (repo, run) ->
                            Box(Modifier.padding(horizontal = 18.dp)) {
                                RunCard(repo, run) { nav.push(Route.RepoDetail.of(repo.ownerLogin, repo.name)) }
                            }
                        }
                    }
                    item { SectionLabel("Trending on GitHub", "Starred heavily this year") }
                    items(d.value.trending.take(6)) { repo ->
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            TrendingRow(repo) { nav.push(Route.RepoDetail.of(repo.ownerLogin, repo.name)) }
                        }
                    }
                    item { SectionLabel("Inbox", "${d.value.notifications.size} unread threads") }
                    if (d.value.notifications.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.CheckCircle,
                                title = "Inbox zero",
                                message = "No unread notifications — beautifully clear.",
                            )
                        }
                    } else {
                        items(d.value.notifications) { n ->
                            Box(Modifier.padding(horizontal = 18.dp)) {
                                NotificationRow(n) {
                                    n.repository?.let { nav.push(Route.RepoDetail.of(it.ownerLogin, it.name)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    val colors = PulseTheme.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, style = displayStyle(15, FontWeight.SemiBold), color = colors.textPrimary)
        Text(subtitle, style = bodyStyle(11), color = colors.textTertiary)
    }
}

@Composable
private fun RunCard(repo: GhRepo, run: GhWorkflowRun, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val success = run.conclusion == "success"
    val tint = when {
        success -> colors.success
        run.conclusion == "failure" -> colors.danger
        run.status == "in_progress" -> colors.amber
        else -> colors.textTertiary
    }
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (success) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                    null, tint = tint, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${repo.name} · ${run.headBranch ?: ""}",
                        style = displayStyle(12.5.toInt(), FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${run.displayTitle ?: run.name ?: "workflow"} · ${relativeTime(run.createdAt)}",
                        style = bodyStyle(10.5.toInt()),
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        run.conclusion ?: run.status ?: "—",
                        style = monoStyle(8.5.toInt()),
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingRow(repo: GhRepo, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.surface.copy(alpha = 0.6f))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.accentSoft)) {
                AsyncImage(
                    model = repo.owner?.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    repo.fullName,
                    style = displayStyle(12.5.toInt(), FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    repo.description ?: "No description",
                    style = bodyStyle(10.5.toInt()),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.Star, null, tint = colors.amber, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                if (repo.stargazersCount > 999) "${repo.stargazersCount / 1000}k" else repo.stargazersCount.toString(),
                style = monoStyle(10),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationRow(n: GhNotification, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val tint = when (n.reason) {
        "review_requested" -> colors.amber
        "mention" -> colors.accent
        "ci_activity" -> colors.info
        else -> colors.secondary
    }
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.surface.copy(alpha = 0.6f))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (n.subject?.type) {
                        "PullRequest" -> Icons.Rounded.PlayArrow
                        "Issue" -> Icons.Rounded.ErrorOutline
                        "Release" -> Icons.Rounded.Public
                        else -> Icons.Rounded.Description
                    },
                    null, tint = tint, modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    n.subject?.title ?: "Notification",
                    style = displayStyle(12.5.toInt(), FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${n.repository?.fullName ?: ""} · ${n.reason ?: ""} · ${relativeTime(n.updatedAt)}",
                    style = bodyStyle(10),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (n.unread) StatusDot(colors.accent, size = 5.dp)
        }
    }
}
