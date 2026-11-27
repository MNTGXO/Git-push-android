package com.mntx.gitpulse.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.home.RepoCard
import com.mntx.gitpulse.ui.navigation.LocalNavigator
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.rememberDebounced

private enum class RepoFilter(val label: String) { ALL("All"), PUBLIC("Public"), PRIVATE("Private"), FORKS("Forks"), ARCHIVED("Archived") }
private enum class RepoSort(val label: String) { UPDATED("Recent"), NAME("A–Z"), STARS("Stars"), SIZE("Size") }

@Composable
fun LibraryScreen(
    repos: Async<List<GhRepo>>,
    onOpenRepo: (GhRepo) -> Unit,
    onNewRepo: () -> Unit,
) {
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RepoFilter.ALL) }
    var sort by remember { mutableStateOf(RepoSort.UPDATED) }
    val debounced = rememberDebounced(query, 200)

    val all = repos.data ?: emptyList()
    val filtered = all
        .filter { repo ->
            val matchesQuery = debounced.isBlank() || repo.name.contains(debounced, true) ||
                repo.description?.contains(debounced, true) == true ||
                repo.language?.contains(debounced, true) == true
            val matchesFilter = when (filter) {
                RepoFilter.ALL -> true
                RepoFilter.PUBLIC -> !repo.private
                RepoFilter.PRIVATE -> repo.private
                RepoFilter.FORKS -> repo.fork
                RepoFilter.ARCHIVED -> repo.archived
            }
            matchesQuery && matchesFilter
        }
        .let { list ->
            when (sort) {
                RepoSort.UPDATED -> list.sortedByDescending { it.pushedAt ?: it.updatedAt ?: "" }
                RepoSort.NAME -> list.sortedBy { it.name.lowercase() }
                RepoSort.STARS -> list.sortedByDescending { it.stargazersCount }
                RepoSort.SIZE -> list.sortedByDescending { it.size }
            }
        }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.4f)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 130.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Repositories", style = displayStyle(30, FontWeight.Bold, letterSpacing = -1.2), color = colors.textPrimary)
                        Text(
                            "${all.size} total · ${all.count { it.private }} private · ${all.count { !it.private }} public",
                            style = bodyStyle(12),
                            color = colors.textTertiary,
                        )
                    }
                    PulseIconButton(Icons.Rounded.Add, "New repository", onClick = onNewRepo)
                }
            }
            item {
                Row(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.surface)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = colors.textTertiary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Filter repositories", style = bodyStyle(12.5.toInt()), color = colors.textTertiary)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = bodyStyle(12.5.toInt()).copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Icon(Icons.Rounded.SwapVert, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                }
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(RepoFilter.entries.toList()) { f ->
                        PulseChip(
                            text = f.label,
                            selected = filter == f,
                            count = if (f != RepoFilter.ALL) all.count {
                                when (f) {
                                    RepoFilter.PUBLIC -> !it.private
                                    RepoFilter.PRIVATE -> it.private
                                    RepoFilter.FORKS -> it.fork
                                    RepoFilter.ARCHIVED -> it.archived
                                    else -> true
                                }
                            } else null,
                            onClick = { filter = f },
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sort", style = monoStyle(9.5.toInt()), color = colors.textTertiary)
                    RepoSort.entries.forEach { s ->
                        Pressable(onClick = { sort = s }) {
                            Text(
                                s.label,
                                style = displayStyle(11.5.toInt(), if (sort == s) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.0),
                                color = if (sort == s) colors.accent else colors.textTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            when (repos) {
                is Async.Loading, is Async.Idle -> item {
                    EmptyState(
                        icon = Icons.Rounded.GridView,
                        title = "Loading repositories",
                        message = "Fetching everything you have access to…",
                    )
                }
                is Async.Error -> item { EmptyState(Icons.Rounded.GridView, "Couldn't load", repos.message) }
                is Async.Ready -> {
                    if (filtered.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.GridView,
                                title = "Nothing matches",
                                message = "Try clearing the filter or searching a different name.",
                                action = {
                                    Pressable(onClick = onNewRepo) {
                                        Row(
                                            Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.accentSoft)
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Create a repository", style = displayStyle(12, FontWeight.SemiBold), color = colors.accent)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { repo ->
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            RepoCard(repo) { onOpenRepo(repo) }
                        }
                    }
                }
            }
        }
    }
}
