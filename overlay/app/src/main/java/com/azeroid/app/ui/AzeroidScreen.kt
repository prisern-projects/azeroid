package com.azeroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azeroid.app.AzeroidActivity
import com.azeroid.app.ControllerOverlay
import com.azeroid.app.Diagnostics
import com.azeroid.app.EmulationMode
import com.azeroid.app.GpuDriver
import com.azeroid.app.GraphicsPreset
import com.azeroid.app.LaunchMode
import com.azeroid.app.Step

private val Gold = Color(0xFFF2C14E)
private val Ink = Color(0xFF0E1116)
private val Panel = Color(0xFF181D26)
private val Muted = Color(0xFF9AA4B2)

private val AzeroidColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A00),
    secondary = Color(0xFF6FA8FF),
    background = Ink,
    surface = Panel,
    onSurface = Color(0xFFE8ECF2),
)

@Composable
fun AzeroidScreen(activity: AzeroidActivity) {
    MaterialTheme(colorScheme = AzeroidColors) {
        val state = activity.state
        var showAdvanced by remember { mutableStateOf(false) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF141A24), Ink)))
                .safeDrawingPadding()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header(state.version, state.controllerConnected)
                state.update?.let { UpdateBanner(it.version, it.notes, state.busy == null) { activity.installUpdate() } }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MainCard(activity, Modifier.weight(1.2f))
                    QuickSettings(activity, Modifier.weight(1f))
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { activity.checkForUpdate() }) { Text("Check for updates") }
                    OutlinedButton(onClick = { Diagnostics.shareLogs(activity) }) { Text("Share log") }
                    OutlinedButton(onClick = { Diagnostics.reportBug(activity) }) { Text("Report a bug") }
                    OutlinedButton(onClick = { showAdvanced = true }) { Text("Advanced") }
                }
            }
        }

        state.error?.let { msg ->
            AlertDialog(
                onDismissRequest = activity::dismissError,
                confirmButton = { TextButton(onClick = activity::dismissError) { Text("OK") } },
                dismissButton = {
                    TextButton(onClick = { activity.dismissError(); Diagnostics.shareLogs(activity) }) { Text("Share log") }
                },
                title = { Text("Something went wrong") },
                text = { Text(msg) },
            )
        }
        if (showAdvanced) AdvancedDialog(activity) { showAdvanced = false }
    }
}

@Composable
private fun Header(version: String, controller: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AZEROID", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Spacer(Modifier.width(12.dp))
        Text("v$version", color = Muted)
        Spacer(Modifier.weight(1f))
        Text(if (controller) "🎮 Controller connected" else "No controller — touch controls", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun UpdateBanner(version: String, notes: String, enabled: Boolean, onUpdate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3354))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update available: v$version", fontWeight = FontWeight.Bold)
                if (notes.isNotBlank()) Text(notes.lines().take(3).joinToString("\n"), color = Muted, fontSize = 13.sp)
            }
            Button(onClick = onUpdate, enabled = enabled) { Text("Update now") }
        }
    }
}

private data class StepCopy(val number: Int, val title: String, val body: String, val button: String)

private fun copyFor(step: Step): StepCopy = when (step) {
    Step.PERMISSIONS -> StepCopy(1, "Allow file access",
        "The engine keeps WoW's files on your phone storage. Turn on \"Allow access to manage all files\" for Azeroid, then come back.",
        "Open settings")
    Step.ENGINE -> StepCopy(2, "Install the game engine",
        "Unpacks Wine, the x86 translator and the Vulkan drivers. Takes about a minute and only happens once.",
        "Install engine")
    Step.CONTAINER -> StepCopy(3, "Create the WoW environment",
        "Sets up a Windows environment tuned for World of Warcraft on Snapdragon.",
        "Create")
    Step.BATTLE_NET -> StepCopy(4, "Install Battle.net",
        "Downloads Blizzard's official installer and runs it. Click through the installer, then close it to come back here.",
        "Download & install")
    Step.WOW -> StepCopy(5, "Install WoW Classic",
        "Log in to Battle.net, pick World of Warcraft → Classic version, and install it. Keep the screen on — it's a big download. Return here when it's done.",
        "Open Battle.net")
    Step.READY -> StepCopy(0, "Ready", "", "PLAY")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainCard(activity: AzeroidActivity, modifier: Modifier) {
    val state = activity.state
    val copy = copyFor(state.step)
    val focus = remember { FocusRequester() }

    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step == Step.READY) {
                Text("World of Warcraft Classic", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (state.installs.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.installs.forEach { install ->
                            FilterChip(
                                selected = install == state.selected,
                                onClick = { activity.selectInstall(install) },
                                label = { Text(install.label) },
                            )
                        }
                    }
                } else {
                    Text(state.selected?.label ?: "", color = Muted)
                }
            } else {
                Text("Step ${copy.number} of 5", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(copy.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(copy.body, color = Muted)
            }

            if (state.busy != null) {
                Text(state.busy)
                val p = state.progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else CircularProgressIndicator()
            } else {
                Button(
                    onClick = activity::primaryAction,
                    modifier = Modifier.fillMaxWidth().height(if (state.step == Step.READY) 64.dp else 52.dp).focusRequester(focus),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                ) {
                    Text(copy.button, fontSize = if (state.step == Step.READY) 22.sp else 16.sp, fontWeight = FontWeight.Black)
                }
                // Controller users land on the main button straight away.
                LaunchedEffect(state.step) { runCatching { focus.requestFocus() } }

                if (state.step == Step.READY || state.step == Step.WOW) {
                    TextButton(onClick = activity::openBattleNet) {
                        Text(if (state.step == Step.READY) "Open Battle.net (patch / switch version)" else "Already installed? Tap to re-check")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSettings(activity: AzeroidActivity, modifier: Modifier) {
    val prefs = activity.prefs
    // Reading state keeps this panel in sync after settingsChanged().
    @Suppress("UNUSED_VARIABLE") val tick = activity.state

    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick settings", fontWeight = FontWeight.Bold)

            Label("Graphics")
            Choices(GraphicsPreset.entries, prefs.graphics, { it.label }) { prefs.graphics = it; activity.settingsChanged() }

            Label("On-screen controller")
            Choices(ControllerOverlay.entries, prefs.overlay, { it.label }) { prefs.overlay = it; activity.settingsChanged() }

            Label("FPS limit")
            Choices(listOf(30, 45, 60, 90, 120), prefs.fpsCap, { "$it" }) { prefs.fpsCap = it; activity.settingsChanged() }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show FPS counter", Modifier.weight(1f))
                Switch(checked = prefs.showFps, onCheckedChange = { prefs.showFps = it; activity.settingsChanged() })
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = Muted, fontSize = 13.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onPick(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun AdvancedDialog(activity: AzeroidActivity, onClose: () -> Unit) {
    val prefs = activity.prefs
    @Suppress("UNUSED_VARIABLE") val tick = activity.state

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Advanced") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("GPU driver — try another one if the game shows a black screen or glitches")
                Choices(GpuDriver.entries, prefs.gpuDriver, { it.label }) { prefs.gpuDriver = it; activity.settingsChanged() }

                Label("x86 emulation — Fast is quicker but may crash")
                Choices(EmulationMode.entries, prefs.emulation, { it.label }) { prefs.emulation = it; activity.settingsChanged() }

                Label("Play button")
                Choices(LaunchMode.entries, prefs.launchMode, { it.label }) { prefs.launchMode = it; activity.settingsChanged() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check for updates on start", Modifier.weight(1f))
                    Switch(checked = prefs.autoUpdate, onCheckedChange = { prefs.autoUpdate = it; activity.settingsChanged() })
                }

                OutlinedButton(onClick = { onClose(); activity.openEngineTools() }) { Text("Open engine tools (experts)") }
            }
        },
    )
}
