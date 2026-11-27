package com.mntx.gitpulse.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mntx.gitpulse.data.AppSettings
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.sync.languageColor
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.PulseLogo
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

@Composable
fun HomeScreen(
    user: GhUser,
    repos: Async<List<GhRepo>>,
    recent: List<String>,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onOpenRepo: (GhRepo) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    val haptics = rememberHaptics(settings.haptics)
    val repoList = repos.data ?: emptyList()

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item { HomeHeader(user, onOpenProfile, settings) }
        item {
            Box(Modifier.padding(horizontal = 18.dp)) {
                HeroCard(
                    user = user,
                    repoCount = repoList.size,
                    onPush = {
                        haptics.tap()
                        nav.push(Route.DiffReview.path)
                    },
                    onExplore = {
                        haptics.tap()
                        nav.switchTab(Route.Explore)
                    },
                    onNewRepo = {
                        haptics.tap()
                        nav.push(Route.NewRepo.path)
                    },
                )
            }
        }

        item { StatsRow(repoList, user) }

        if (recent.isNotEmpty()) {
            item {
                SectionTitle("Recent syncs", "Resume where you left off")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recent) { full ->
                        val parts = full.split("/")
                        if (parts.size == 2) {
                            RecentSyncChip(parts[0], parts[1]) {
                                haptics.tap()
                                nav.push(Route.RepoDetail.of(parts[0], parts[1]))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Your repositories", when (repos) {
                is Async.Error -> "Couldn't load — pull to refresh"
                else -> "${repoList.size} total · sorted by last push"
            }) {
                PulseIconButton(Icons.Rounded.Search, "Search", size = 34.dp) {
                    haptics.tap()
                    nav.push(Route.Search.path)
                }
            }
        }

        when (repos) {
            is Async.loading, is Async.Idle -> items(4) { RepoSkeleton() }
            is Async.Error -> item {
                GlassCard(Modifier.padding(horizontal = 18.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Couldn't reach GitHub", style = displayStyle(14, FontWeight.SemiBold), color = colors.textPrimary)
                        Text(repos.message, style = bodyStyle(12), color = colors.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        Pressable(onClick = onRefresh) {
                            Text("Retry", style = displayStyle(12, FontWeight.SemiBold), color = colors.accent)
                        }
                    }
                }
            }
            is Async.Ready -> items(repoList.take(12), key = { it.id }) { repo ->
                Box(Modifier.padding(horizontal = 18.dp)) {
                    RepoCard(repo) {
                        haptics.tap()
                        onOpenRepo(repo)
                    }
                }
            }
        }

        if (repoList.size > 12) {
            item {
                Box(Modifier.padding(horizontal = 18.dp)) {
                    Pressable(onClick = { nav.switchTab(Route.Library) }, modifier = Modifier.fillMaxWidth()) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "Browse all ${repoList.size} repositories",
                                    style = displayStyle(13, FontWeight.SemiBold),
                                    color = colors.accent,
                                )
                                Icon(Icons.Rounded.ChevronRight, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(user: GhUser, onOpenProfile: () -> Unit, settings: AppSettings) {
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseLogo(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                greeting(),
                style = bodyStyle(11),
                color = colors.textTertiary,
            )
            Text(
                "@${user.login}",
                style = displayStyle(16, FontWeight.SemiBold, letterSpacing = -0.3),
                color = colors.textPrimary,
            )
        }
        PulseIconButton(Icons.Rounded.Notifications, "Notifications", size = 38.dp) {
            nav.push(Route.Notifications.path)
        }
        Spacer(Modifier.width(8.dp))
        Pressable(onClick = onOpenProfile) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5 -> "Late night shipping"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
private fun HeroCard(user: GhUser, repoCount: Int, onPush: () -> Unit, onExplore: () -> Unit, onNewRepo: () -> Unit) {
    val colors = PulseTheme.colors
    GlassCard(Modifier.fillMaxWidth(), glow = true) {
        Box {
            AuroraBackdrop(Modifier.matchParentSize(), intensity = 0.8f)
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(colors.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CONNECTED",
                        style = monoStyle(9.5.toInt(), FontWeight.SemiBold),
                        color = colors.secondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${compactNumber(repoCount)} repos",
                        style = monoStyle(10),
                        color = colors.textTertiary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Push a zip.\nMirror the folder.",
                    style = displayStyle(25, FontWeight.Bold, letterSpacing = -1.0, lineHeight = 30),
                    color = colors.textPrimary,
                )
                Text(
                    "Deleted files on GitHub get removed automatically — your repo always mirrors the upload.",
                    style = bodyStyle(12, lineHeight = 18),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(colors.accent, colors.pink.copy(alpha = 0.9f))))
                        .then(Modifier)
                        .padding(0.dp),
                ) {
                    Pressable(onClick = onPush, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 15.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.FolderZip, null, tint = Color(0xFF0A0D14), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Sync a zip or folder",
                                style = displayStyle(14, FontWeight.Bold, letterSpacing = 0.1),
                                color = Color(0xFF0A0D14),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Rounded.TrendingUp, "Explore", Modifier.weight(1f), onExplore)
                    QuickAction(Icons.Rounded.Add, "New repo", Modifier.weight(1f), onNewRepo)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surfaceElevated.copy(alpha = 0.7f))
                .border(1.dp, colors.stroke, RoundedCornerShape(13.dp))
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = displayStyle(12, FontWeight.Medium), color = colors.textSecondary)
        }
    }
}

@Composable
private fun StatsRow(repos: List<GhRepo>, user: GhUser) {
    val colors = PulseTheme.colors
    val stars = repos.sumOf { it.stargazersCount }
    val privateCount = repos.count { it.private }
    val languages = repos.mapNotNull { it.language }.distinct().size
    Row(
        Modifier.padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile("Stars", compactNumber(stars), Icons.Rounded.Star, colors.amber, Modifier.weight(1f))
        StatTile("Private", privateCount.toString(), Icons.Rounded.AccountTree, colors.accent, Modifier.weight(1f))
        StatTile("Langs", languages.toString(), Icons.Rounded.AutoAwesome, colors.secondary, Modifier.weight(1f))
        StatTile("Followers", compactNumber(user.followers), Icons.Rounded.TrendingUp, colors.pink, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = displayStyle(17, FontWeight.Bold, letterSpacing = -0.4), color = colors.textPrimary)
            Text(label, style = bodyStyle(9.5.toInt()), color = colors.textTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = displayStyle(16, FontWeight.SemiBold, letterSpacing = -0.3), color = colors.textPrimary)
            Text(subtitle, style = bodyStyle(11), color = colors.textTertiary)
        }
        trailing?.invoke()
    }
}

@Composable
private fun RecentSyncChip(owner: String, name: String, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surface.copy(alpha = 0.7f))
                .border(1.dp, colors.stroke, RoundedCornerShape(50))
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, null, tint = colors.accent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(7.dp))
            Text(name, style = displayStyle(12, FontWeight.Medium), color = colors.textPrimary)
        }
    }
}

@Composable
fun RepoCard(repo: GhRepo, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (repo.private) Icons.Rounded.AccountTree else Icons.Rounded.TrendingUp,
                            null, tint = colors.accent, modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            repo.name,
                            style = displayStyle(14.5.toInt(), FontWeight.SemiBold, letterSpacing = -0.2),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            repo.description ?: "No description",
                            style = bodyStyle(11.5.toInt()),
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repo.language?.let { lang ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(languageColor(lang.lowercase())))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(lang, style = bodyStyle(10.5.toInt()), color = colors.textSecondary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Icon(Icons.Rounded.Star, null, tint = colors.amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(compactNumber(repo.stargazersCount), style = monoStyle(10), color = colors.textTertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(humanSize(repo.size * 1024), style = monoStyle(10), color = colors.textTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(relativeTime(repo.pushedAt ?: repo.updatedAt), style = bodyStyle(10), color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun RepoSkeleton() {
    GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBlock(Modifier.size(36.dp), RoundedCornerShape(11.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                ShimmerBlock(Modifier.size(width = 140.dp, height = 14.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(Modifier.size(width = 220.dp, height = 10.dp))
            }
        }
    }
}
