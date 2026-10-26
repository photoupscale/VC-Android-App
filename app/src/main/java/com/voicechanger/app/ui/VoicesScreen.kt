package com.voicechanger.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicechanger.app.engine.VoiceCategory
import com.voicechanger.app.engine.VoicePreset
import com.voicechanger.app.engine.VoicePresets
import java.util.Locale

@Composable
fun VoicesScreen(vm: MainViewModel, requireMic: (() -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsState()
    val monitoring by vm.monitoring.collectAsState()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Voices", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Pick the voice used in calls and voice notes. Put on headphones and tap Listen to hear yourself.",
                style = MaterialTheme.typography.bodySmall,
            )
            val label = if (monitoring) "Stop listening" else "Listen with headphones"
            if (monitoring) {
                OutlinedButton(onClick = vm::toggleMonitor, modifier = Modifier.padding(top = 8.dp)) { Text(label) }
            } else {
                Button(onClick = { requireMic(vm::toggleMonitor) }, modifier = Modifier.padding(top = 8.dp)) { Text(label) }
            }
        }
        VoiceCategory.entries.forEach { category ->
            item { Text(category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(VoicePresets.all.filter { it.category == category }, key = { it.id }) { preset ->
                PresetCard(preset, selected = s.presetId == preset.id) { vm.selectPreset(preset.id) }
            }
        }
        item {
            Text("Custom", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            val selected = s.presetId == VoicePresets.CUSTOM_ID
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.selectPreset(VoicePresets.CUSTOM_ID) },
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(16.dp)) {
                    val c = s.custom
                    LabeledSlider("Pitch", "%+.1f semitones".format(Locale.US, c.pitchSemitones), c.pitchSemitones, -12f..12f) {
                        vm.setCustom(c.copy(pitchSemitones = it))
                        vm.selectPreset(VoicePresets.CUSTOM_ID)
                    }
                    LabeledSlider("Formant", "x%.2f".format(Locale.US, c.formantRatio), c.formantRatio, 0.7f..1.4f) {
                        vm.setCustom(c.copy(formantRatio = it))
                        vm.selectPreset(VoicePresets.CUSTOM_ID)
                    }
                    LabeledSlider("Brightness", "%+.1f dB".format(Locale.US, c.brightnessDb), c.brightnessDb, -6f..6f) {
                        vm.setCustom(c.copy(brightnessDb = it))
                        vm.selectPreset(VoicePresets.CUSTOM_ID)
                    }
                    Text(
                        "Tip: for a natural result move formant about 3% per semitone of pitch, in the same direction.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: VoicePreset, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(preset.name, style = MaterialTheme.typography.titleSmall)
            Text(preset.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
    Slider(value = current, onValueChange = onChange, valueRange = range)
}
