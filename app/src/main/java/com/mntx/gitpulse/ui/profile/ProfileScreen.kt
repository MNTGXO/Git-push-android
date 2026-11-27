package com.mntx.gitpulse.ui.profile

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.components.ShimmerBlock
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import com.mntx.gitpulse.ui.util.compactNumber
import com.mntx.gitpulse.ui.util.relativeTime
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class ProfileData(val user: GhUser, val repos: List<GhRepo>)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as GitPulseApp).services.api
    private val _data = AsyncValue<ProfileData>()
    val data = _data.state

    fun load(login: String) {
        viewModelScope.launch {
            _data.load {
                val user = async { api.user(login) }
                val repos = async { runCatching { api.userRepos(login) }.getOrDefault(emptyList()) }
                ProfileData(user.await(), repos.await())
            }
        }
    }
}

@Composable
fun ProfileScreen(login: String, onBack: () -> Unit, onOpenRepo: (GhRepo) -> Unit, vm: ProfileViewModel = viewModel()) {
    val colors = PulseTheme.colors
    val state by vm.data.collectAsStateWithLifecycle()
    LaunchedEffect(login) { vm.load(login) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.5f)
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PulseIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("@$login", style = displayStyle(17, FontWeight.SemiBold, letterSpacing = -0.3), color = colors.textPrimary)
                }
            }
            when (val d = state) {
                is Async.loading, is Async.Idle -> items(3) {
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        ShimmerBlock(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(22.dp))
                    }
                }
                is Async.Error -> item {
                    Box(Modifier.padding(18.dp)) { PulseBanner(d.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR) }
                }
                is Async.Ready -> {
                    item {
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .size(78.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(colors.accent, colors.pink))),
                                    ) {
                                        AsyncImage(
                                            model = d.value.user.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        d.value.user.name ?: d.value.user.login,
                                        style = displayStyle(20, FontWeight.Bold, letterSpacing = -0.5),
                                        color = colors.textPrimary,
                                    )
                                    Text("@${d.value.user.login}", style = monoStyle(10.5.toInt()), color = colors.accent)
                                    d.value.user.bio?.let {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            it,
                                            style = bodyStyle(12, lineHeight = 17),
                                            color = colors.textSecondary,
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        ProfileStat(compactNumber(d.value.user.followers), "followers")
                                        ProfileStat(compactNumber(d.value.user.following), "following")
                                        ProfileStat(compactNumber(d.value.user.publicRepos), "repos")
                                    }
                                    d.value.user.location?.let {
                                        Spacer(Modifier.height(12.dp))
                                        Text(it, style = bodyStyle(10.5.toInt()), color = colors.textTertiary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Repositories",
                            style = displayStyle(14, FontWeight.SemiBold),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    if (d.value.repos.isEmpty()) {
                        item { EmptyState(Icons.Rounded.Star, "No public repositories", "This account keeps everything private.") }
                    }
                    items(d.value.repos) { repo ->
                        Box(Modifier.padding(horizontal = 18.dp)) {
                            Pressable(onClick = { onOpenRepo(repo) }, modifier = Modifier.fillMaxWidth()) {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.TrendingUp, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(11.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                repo.name,
                                                style = displayStyle(13, FontWeight.SemiBold),
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
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "${compactNumber(repo.stargazersCount)} ★",
                                                style = monoStyle(9.5.toInt()),
                                                color = colors.amber,
                                            )
                                            Text(
                                                "${humanSize(repo.size * 1024)} · ${relativeTime(repo.updatedAt)}",
                                                style = monoStyle(8.5.toInt()),
                                                color = colors.textTertiary,
                                            )
                                        }
                                    }
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
private fun ProfileStat(value: String, label: String) {
    val colors = PulseTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = displayStyle(17, FontWeight.Bold), color = colors.textPrimary)
        Text(label, style = bodyStyle(9.5.toInt()), color = colors.textTertiary)
    }
}
