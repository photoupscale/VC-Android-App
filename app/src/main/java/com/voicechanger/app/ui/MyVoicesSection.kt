package com.voicechanger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicechanger.app.engine.VoiceProfile
import com.voicechanger.app.settings.MyVoice
import java.util.Locale

private const val READ_ALOUD =
    "\"Hello, this is my normal speaking voice. I am reading this sentence slowly and clearly, " +
        "the same way I talk on the phone with my friends and family.\""

/** "My voices": voices made from a recording, plus the add-voice flow. */
@Composable
fun MyVoicesSection(vm: MainViewModel, requireMic: (() -> Unit) -> Unit) {
    val s by vm.settings.collectAsState()
    val add by vm.addVoice.collectAsState()

    Text("My voices (${s.myVoices.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.myVoices.forEach { v -> MyVoiceCard(v, selected = s.presetId == v.presetId, onClick = { vm.selectPreset(v.presetId) }, onDelete = { vm.deleteMyVoice(v) }) }
        if (add.open) {
            AddVoiceCard(vm, add, s.ownProfile?.let(::VoiceProfile), requireMic)
        } else {
            OutlinedButton(onClick = { vm.openAddVoice(true) }, modifier = Modifier.fillMaxWidth()) { Text("Add a voice from a sample") }
        }
    }
}

@Composable
private fun MyVoiceCard(voice: MyVoice, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(voice.name, style = MaterialTheme.typography.titleSmall)
                val p = voice.params
                Text("Pitch %+.1f, formant x%.2f".format(Locale.US, p.pitchSemitones, p.formantRatio), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun AddVoiceCard(vm: MainViewModel, add: AddVoiceState, own: VoiceProfile?, requireMic: (() -> Unit) -> Unit) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::loadTargetFile) }
    var name by remember { mutableStateOf("") }
    val idle = add.recording == null && !add.busy

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add a voice from a sample", style = MaterialTheme.typography.titleSmall)
            Text(
                "The app measures the pitch, vocal-tract size and tone of the voice in the sample and sets yours to match. " +
                    "It gets close to the person's voice, but it is not an exact copy.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Step 1: your own voice", style = MaterialTheme.typography.labelLarge)
            if (own != null) {
                Text("Saved (your pitch is about %.0f Hz). You only need to do this once.".format(Locale.US, own.medianF0), style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Tap record and read this aloud in your normal voice, for about 10 seconds:", style = MaterialTheme.typography.bodySmall)
                Text(READ_ALOUD, style = MaterialTheme.typography.bodyMedium)
            }
            RecordButton(
                label = if (own == null) "Record my voice" else "Record my voice again",
                recording = add.recording == SampleTarget.Own,
                seconds = add.recordSeconds,
                enabled = idle || add.recording == SampleTarget.Own,
                onClick = { if (add.recording != null) vm.toggleRecording(SampleTarget.Own) else requireMic { vm.toggleRecording(SampleTarget.Own) } },
            )

            Text("Step 2: the voice you want", style = MaterialTheme.typography.labelLarge)
            Text(
                "Choose a clear recording of that person talking alone, 10 to 60 seconds, with no music. " +
                    "A WhatsApp voice note, a video or any audio file works. You can also play it from another phone and record it here.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickFile.launch(arrayOf("audio/*", "video/*")) }, enabled = idle) { Text("Choose file") }
                RecordButton(
                    label = "Record it",
                    recording = add.recording == SampleTarget.Target,
                    seconds = add.recordSeconds,
                    enabled = idle || add.recording == SampleTarget.Target,
                    onClick = { if (add.recording != null) vm.toggleRecording(SampleTarget.Target) else requireMic { vm.toggleRecording(SampleTarget.Target) } },
                )
            }
            add.target?.let { t ->
                Text(
                    "${add.targetLabel}: pitch about %.0f Hz, %.0f s of speech found.".format(Locale.US, t.medianF0, t.voicedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (add.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Analysing...", style = MaterialTheme.typography.bodySmall)
                }
            }
            add.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Text("Step 3: name and save", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Voice name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveVoice(name) }, enabled = idle && own != null && add.target != null) { Text("Save voice") }
                TextButton(onClick = { vm.openAddVoice(false) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RecordButton(label: String, recording: Boolean, seconds: Float, enabled: Boolean, onClick: () -> Unit) {
    if (recording) {
        Button(onClick = onClick) { Text("Stop (%.0f s)".format(Locale.US, seconds)) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}
