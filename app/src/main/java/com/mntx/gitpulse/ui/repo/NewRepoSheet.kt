package com.mntx.gitpulse.ui.repo

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mntx.gitpulse.GitPulseApp
import com.mntx.gitpulse.data.model.CreateRepoRequest
import com.mntx.gitpulse.data.model.GhRepo
import com.mntx.gitpulse.ui.components.AuroraBackdrop
import com.mntx.gitpulse.ui.components.GlassCard
import com.mntx.gitpulse.ui.components.Pressable
import com.mntx.gitpulse.ui.components.PulseBanner
import com.mntx.gitpulse.ui.components.PulseButton
import com.mntx.gitpulse.ui.components.PulseChip
import com.mntx.gitpulse.ui.components.PulseIconButton
import com.mntx.gitpulse.ui.theme.PulseTheme
import com.mntx.gitpulse.ui.theme.bodyStyle
import com.mntx.gitpulse.ui.theme.displayStyle
import com.mntx.gitpulse.ui.theme.monoStyle
import kotlinx.coroutines.launch

@Composable
fun NewRepoSheet(onBack: () -> Unit, onCreated: (GhRepo) -> Unit) {
    val colors = PulseTheme.colors
    val api = GitPulseApp.instance.services.api
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var autoInit by remember { mutableStateOf(true) }
    var gitignore by remember { mutableStateOf<String?>("Android") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.55f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text("New repository", style = displayStyle(20, FontWeight.SemiBold, letterSpacing = -0.4), color = colors.textPrimary)
            }
            Spacer(Modifier.height(8.dp))

            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column(Modifier.padding(18.dp)) {
                    FieldLabel("Repository name")
                    InputField(name, "my-awesome-project") {
                        name = it.replace(" ", "-").filter { c -> c.isLetterOrDigit() || c in "-_." }
                    }
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("Description (optional)")
                    InputField(description, "What does this project do?") { description = it }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VisibilityToggle(isPrivate) { isPrivate = it }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Initialise with README", style = displayStyle(13, FontWeight.Medium), color = colors.textPrimary)
                            Text("Makes the first push frictionless", style = bodyStyle(10.5.toInt()), color = colors.textTertiary)
                        }
                        Switch(
                            checked = autoInit,
                            onCheckedChange = { autoInit = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textTertiary,
                                uncheckedTrackColor = colors.surfaceElevated,
                                uncheckedBorderColor = colors.stroke,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Add a .gitignore", style = displayStyle(13, FontWeight.SemiBold), color = colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Android", "Node", "Python", null).take(4).forEach { t ->
                    PulseChip(
                        text = t ?: "None",
                        selected = gitignore == t,
                        onClick = { gitignore = t },
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                PulseBanner(error!!, com.mntx.gitpulse.ui.components.BannerKind.ERROR)
            }

            Spacer(Modifier.height(22.dp))
            PulseButton(
                text = if (loading) "Creating…" else "Create repository",
                icon = Icons.Rounded.Add,
                enabled = name.length >= 1 && !loading,
                loading = loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            api.createRepo(
                                CreateRepoRequest(
                                    name = name.trim(),
                                    description = description.ifBlank { null },
                                    private = isPrivate,
                                    autoInit = autoInit,
                                    gitignoreTemplate = gitignore,
                                )
                            )
                        }.onSuccess { repo ->
                            loading = false
                            onCreated(repo)
                        }.onFailure { e ->
                            loading = false
                            error = e.message ?: "Could not create the repository."
                        }
                    }
                },
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = monoStyle(9.5.toInt(), FontWeight.SemiBold),
        color = PulseTheme.colors.textTertiary,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun InputField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = PulseTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 13.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = bodyStyle(13), color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = bodyStyle(13).copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
    }
}

@Composable
private fun VisibilityToggle(isPrivate: Boolean, onChange: (Boolean) -> Unit) {
    val colors = PulseTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Public", true to "Private").forEach { (priv, label) ->
            val selected = isPrivate == priv
            Pressable(onClick = { onChange(priv) }) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) colors.accentSoft else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (selected) colors.accent.copy(alpha = 0.5f) else colors.stroke,
                            RoundedCornerShape(11.dp),
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (priv) Icons.Rounded.Lock else Icons.Rounded.Public,
                        null,
                        tint = if (selected) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = displayStyle(12, FontWeight.Medium),
                        color = if (selected) colors.accent else colors.textSecondary,
                    )
                }
            }
        }
    }
}
