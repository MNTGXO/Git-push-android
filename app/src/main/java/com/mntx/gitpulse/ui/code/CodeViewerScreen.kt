package com.mntx.gitpulse.ui.code

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.sync.humanSize
import com.mntx.gitpulse.sync.languageColor
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.EmptyState
import com.mntx.gitpulse.ui.components.ProgressRing
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import com.mntx.gitpulse.ui.util.Async
import com.mntx.gitpulse.ui.util.AsyncValue
import kotlinx.coroutines.launch
import android.util.Base64

class FileViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val api = (app as GitPulseApp).services.api
    private val _content = AsyncValue<String>()
    val content = _content.state
    private val _size = AsyncValue<Long>()
    val size = _size.state

    fun load(owner: String, repo: String, path: String, ref: String) {
        viewModelScope.launch {
            _content.load {
                val info = api.content(owner, repo, path, ref)
                _size.ready(info.size)
                val ext = path.substringAfterLast('.', "").lowercase()
                val binary = ext in BINARY_EXTS
                if (binary) {
                    "«binary file: ${humanSize(info.size)}»"
                } else {
                    val raw = info.content?.replace("\n", "")?.let {
                        runCatching { String(Base64.decode(it, Base64.DEFAULT)) }.getOrNull()
                    }
                    raw ?: runCatching {
                        String(api.rawFile(owner, repo, path, ref).bytes)
                    }.getOrDefault("")
                }
            }
        }
    }

    companion object {
        val BINARY_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "zip", "apk", "jar", "pdf", "mp4", "mp3", "ttf", "otf", "so", "aab")
    }
}

@Composable
fun CodeViewerScreen(
    owner: String,
    repo: String,
    path: String,
    ref: String,
    onBack: () -> Unit,
    vm: FileViewerViewModel = viewModel(),
) {
    val colors = PulseTheme.colors
    val content by vm.content.collectAsStateWithLifecycle()
    val size by vm.size.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val hScroll = rememberScrollState()

    LaunchedEffect(owner, repo, path, ref) { vm.load(owner, repo, path, ref) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.35f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(27.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceElevated), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Description, null,
                        tint = Color(languageColor(path.substringAfterLast('.', "").lowercase())),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        path.substringAfterLast('/'),
                        style = displayStyle(14, FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        buildString {
                            append(ref)
                            size.data?.let { append(" · ${humanSize(it)}") }
                        },
                        style = monoStyle(9.5.toInt()),
                        color = colors.textTertiary,
                    )
                }
                if (content.data != null) {
                    PulseIconButton(Icons.Rounded.ContentCopy, "Copy", size = 36.dp) {
                        clipboard.setText(AnnotatedString(content.data ?: ""))
                    }
                }
            }

            when (val c = content) {
                is Async.Loading, is Async.Idle -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressRing(progress = 0f, indeterminate = true, size = 64.dp, stroke = 3.dp)
                }
                is Async.Error -> Box(Modifier.padding(18.dp)) {
                    PulseBanner(c.message, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
                }
                is Async.Ready -> {
                    val lines = c.value.lines()
                    if (lines.size > 6000) {
                        Box(Modifier.padding(18.dp)) {
                            PulseBanner(
                                "This file has ${lines.size} lines — showing the first 6,000.",
                                com.mntx.gitpulse.ui.components.BannerKind.INFO,
                            )
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().horizontalScroll(hScroll),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
                    ) {
                        itemsIndexed(lines.take(6000)) { index, line ->
                            Row(Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    (index + 1).toString().padStart(4),
                                    style = monoStyle(10),
                                    color = colors.textTertiary.copy(alpha = 0.55f),
                                    modifier = Modifier.width(40.dp),
                                )
                                Text(
                                    highlight(line, colors.textPrimary),
                                    style = monoStyle(11.5.toInt()),
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A featherweight syntax tint — keywords, strings, comments, numbers. No heavy parsers. */
private fun highlight(line: String, base: Color): AnnotatedString {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("<!--")) {
        return AnnotatedString(line, SpanStyle(color = Color(0xFF6D7A93)))
    }
    val keywords = listOf(
        "fun ", "val ", "var ", "class ", "object ", "import ", "package ", "return ",
        "if ", "else ", "when ", "for ", "while ", "suspend ", "override ", "private ",
        "public ", "const ", "data ", "sealed ", "interface ", "fun ", "async ", "await ",
        "def ", "from ", "export ", "default ", "function ", "const ", "let ", "new ",
    )
    return buildAnnotatedString {
        var rest = line
        while (rest.isNotEmpty()) {
            val keyword = keywords.firstOrNull { rest.startsWith(it) }
            when {
                keyword != null -> {
                    withStyle(SpanStyle(color = Color(0xFF8B7CFF), fontWeight = FontWeight.Medium)) { append(keyword) }
                    rest = rest.removePrefix(keyword)
                }
                rest.startsWith("\"") || rest.startsWith("'") || rest.startsWith("`") -> {
                    val quote = rest.first()
                    val end = rest.indexOf(quote, 1).let { if (it == -1) rest.length - 1 else it }
                    withStyle(SpanStyle(color = Color(0xFF4FE3C1))) { append(rest.substring(0, end + 1)) }
                    rest = rest.substring(end + 1)
                }
                rest.first().isDigit() -> {
                    val match = Regex("^\\d[\\d._a-fA-FxX]*").find(rest)
                    val value = match?.value ?: rest.take(1)
                    withStyle(SpanStyle(color = Color(0xFFFFBE6B))) { append(value) }
                    rest = rest.removePrefix(value)
                }
                else -> {
                    append(rest.first())
                    rest = rest.substring(1)
                }
            }
        }
    }
}
