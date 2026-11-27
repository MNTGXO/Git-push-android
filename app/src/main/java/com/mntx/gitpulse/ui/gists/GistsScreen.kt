package com.mntx.gitpulse.ui.gists

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
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
import com.mntx.gitpulse.data.model.GhGist
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.GlassCard
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
import com.mntx.gitpulse.ServiceLocator

class GistsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ServiceLocator.get(app).api
    private val _gists = AsyncValue<List<GhGist>>()
    val gists = _gists.state

    fun load() {
        viewModelScope.launch { _gists.load { api.gists() } }
    }
}

@Composable
fun GistsScreen(onBack: () -> Unit, vm: GistsViewModel = viewModel()) {
    val colors = PulseTheme.colors
    val state by vm.gists.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.4f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Gists", style = displayStyle(19, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
                    Text("Quick snippets you own", style = bodyStyle(11), color = colors.textTertiary)
                }
            }
            when (val s = state) {
                is Async.Loading, is Async.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading gists…", style = bodyStyle(12), color = colors.textTertiary)
                }
                is Async.Error -> Box(Modifier.padding(18.dp)) { PulseBanner(s.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR) }
                is Async.Ready -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (s.value.isEmpty()) {
                        item { EmptyState(Icons.Rounded.Code, "No gists yet", "Create one from any file in a repo.") }
                    }
                    items(s.value) { gist ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(colors.accentSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.Code, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            gist.files.keys.firstOrNull() ?: "untitled",
                                            style = displayStyle(13, FontWeight.SemiBold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            gist.description ?: "No description",
                                            style = bodyStyle(10.5.toInt()),
                                            color = colors.textTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        if (gist.public) Icons.Rounded.Public else Icons.Rounded.Lock,
                                        null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${gist.files.size} file(s) · ${relativeTime(gist.updatedAt)}",
                                    style = monoStyle(9.5.toInt()),
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
