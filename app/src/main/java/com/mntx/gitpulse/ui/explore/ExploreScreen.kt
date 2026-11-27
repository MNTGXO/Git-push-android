package com.mntx.gitpulse.ui.explore

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.data.model.GhUser
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.sync.languageColor
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.ShimmerBlock
import com.mntx.gitpulse.ui.navigation.LocalNavigator
import com.mntx.gitpulse.ui.navigation.Route
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import com.mntx.gitpulse.ui.util.compactNumber
import com.mntx.gitpulse.ui.util.rememberDebounced
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

data class ExploreFilters(
    val language: String? = null,
    val sort: String = "stars",
    val includeForks: Boolean = false,
)

class ExploreViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as GitPulseApp).services.api
    private val _results = AsyncValue<List<GhRepo>>(Async.Idle)
    val results = _results.state
    private val _users = MutableStateFlow<List<GhUser>>(emptyList())
    val users = _users.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun search(q: String, filters: ExploreFilters) {
        _query.value = q
        viewModelScope.launch {
            _results.load {
                val qualifiers = buildList {
                    if (!filters.includeForks) add("fork:false")
                    filters.language?.let { add("language:$it") }
                    if (q.isNotBlank()) add(q)
                    else add("stars:>500")
                }.joinToString(" ")
                api.searchRepos(qualifiers, sort = filters.sort).items
            }
            viewModelScope.launch {
                runCatching { api.searchUsers(q.ifBlank { "fullstack" }).items.take(8) }
                    .onSuccess { _users.value = it }
            }
        }
    }
}

@Composable
fun ExploreScreen(vm: ExploreViewModel = viewModel()) {
    val colors = PulseTheme.colors
    val nav = LocalNavigator.current
    val results by vm.results.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("stars") }
    var language by remember { mutableStateOf<String?>(null) }
    val debounced = rememberDebounced(text, 420)

    LaunchedEffect(debounced, sort, language) {
        vm.search(debounced, ExploreFilters(language = language, sort = sort))
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.45f)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp, top = 6.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        "Explore",
                        style = displayStyle(30, FontWeight.Bold, letterSpacing = -1.2),
                        color = colors.textPrimary,
                    )
                    Text(
                        "Search every repository on GitHub",
                        style = bodyStyle(12.5.toInt()),
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                    SearchField(text) { text = it }
                }
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf("stars" to "Top starred", "updated" to "Fresh", "forks" to "Most forked")) { (key, label) ->
                        PulseChip(text = label, selected = sort == key, onClick = { sort = key })
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { PulseChip(text = "All", selected = language == null, onClick = { language = null }) }
                    items(listOf("kotlin", "typescript", "python", "rust", "swift", "go")) { lang ->
                        PulseChip(text = lang, selected = language == lang, onClick = { language = if (language == lang) null else lang })
                    }
                }
            }
            if (users.isNotEmpty()) {
                item {
                    Text(
                        "Developers",
                        style = displayStyle(14, FontWeight.SemiBold),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(users, key = { it.id }) { user ->
                            Pressable(onClick = { nav.push(Route.Profile.of(user.login)) }) {
                                GlassCard(shape = RoundedCornerShape(18.dp)) {
                                    Column(
                                        Modifier.width(104.dp).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(Modifier.size(44.dp).clip(CircleShape)) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            user.login,
                                            style = displayStyle(11.5.toInt(), FontWeight.SemiBold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${compactNumber(user.followers)} followers",
                                            style = bodyStyle(9),
                                            color = colors.textTertiary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 18.dp).padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TrendingUp, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Repositories",
                        style = displayStyle(14, FontWeight.SemiBold),
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${results.data?.size ?: 0} results",
                        style = monoStyle(9.5.toInt()),
                        color = colors.textTertiary,
                    )
                }
            }
            when (val r = results) {
                is Async.loading, is Async.Idle -> items(5) { ExploreSkeleton() }
                is Async.Error -> item {
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        PulseBanner(r.message, com.mntx.gitpulse.ui.components.BannerKind.WARN)
                    }
                }
                is Async.Ready -> {
                    if (r.value.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Explore,
                                title = "No repositories found",
                                message = "Try a different keyword or clear the language filter.",
                            )
                        }
                    }
                    items(r.value, key = { it.id }) { repo ->
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            ExploreRepoCard(repo) {
                                nav.push(Route.RepoDetail.of(repo.ownerLogin, repo.name))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = PulseTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search repos, topics, users…", style = bodyStyle(13), color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = bodyStyle(13).copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(Icons.Rounded.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun ExploreRepoCard(repo: GhRepo, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.accentSoft)) {
                        AsyncImage(
                            model = repo.owner?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            repo.fullName,
                            style = displayStyle(13.5.toInt(), FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            repo.description ?: "No description",
                            style = bodyStyle(11),
                            color = colors.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = colors.amber, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(compactNumber(repo.stargazersCount), style = monoStyle(10), color = colors.textSecondary)
                        }
                        Text(humanSize(repo.size * 1024), style = monoStyle(9), color = colors.textTertiary)
                    }
                }
                if (!repo.topics.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repo.topics.take(3).forEach { topic ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(colors.accentSoft)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(topic, style = monoStyle(9), color = colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreSkeleton() {
    Box(Modifier.padding(horizontal = 18.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(Modifier.size(40.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(11.dp))
                Column {
                    ShimmerBlock(Modifier.size(160.dp, 13.dp))
                    Spacer(Modifier.height(7.dp))
                    ShimmerBlock(Modifier.size(240.dp, 10.dp))
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ExplorePreview(results: Async<List<GhRepo>>, users: List<GhUser>) {
    val colors = PulseTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Explore", style = displayStyle(30, FontWeight.Bold, letterSpacing = -1.2), color = colors.textPrimary)
        }
        items(users) { user ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${user.login}", style = displayStyle(13, FontWeight.SemiBold), color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("${user.followers} followers", style = bodyStyle(10), color = colors.textTertiary)
            }
        }
        items((results as? Async.Ready)?.value ?: emptyList()) { repo ->
            ExploreRepoCard(repo) {}
        }
    }
}
