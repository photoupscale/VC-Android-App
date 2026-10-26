package com.voicechanger.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.voicechanger.app.audio.VoiceNoteRecorder
import com.voicechanger.app.engine.VoicePresets
import com.voicechanger.app.settings.TargetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun VoiceNotesScreen(vm: MainViewModel, requireMic: (() -> Unit) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val recorder = remember { VoiceNoteRecorder() }
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(recording) {
        while (recording) {
            seconds = recorder.seconds
            delay(200)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            if (recorder.isRecording) recorder.stopAndSave(File(context.cacheDir, "notes/discarded.m4a"))
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Voice notes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Works on any phone, no root needed. Record a message in your changed voice and send it to WhatsApp or Messenger.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Voice: ${VoicePresets.byId(s.presetId)?.name ?: "Custom"}", style = MaterialTheme.typography.bodyMedium)

        if (recording) {
            Text("Recording… %.1f s".format(Locale.US, seconds), style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                recording = false
                scope.launch {
                    val out = File(context.cacheDir, "notes/voice_note_${System.currentTimeMillis()}.m4a")
                    val saved = withContext(Dispatchers.IO) { recorder.stopAndSave(out) }
                    note = if (saved) out else null
                    if (!saved) Toast.makeText(context, "Too short", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Stop") }
        } else {
            Button(onClick = {
                requireMic {
                    player?.release()
                    player = null
                    note = null
                    recorder.start(s.voice())
                    recording = true
                }
            }) { Text("Record") }
        }

        note?.let { file ->
            Text("Ready: ${file.name}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.path)
                        prepare()
                        start()
                    }
                }) { Text("Play") }
                Button(onClick = { share(context, file, TargetApp.WhatsApp.packageName) }) { Text("WhatsApp") }
                Button(onClick = { share(context, file, TargetApp.Messenger.packageName) }) { Text("Messenger") }
            }
            OutlinedButton(onClick = { share(context, file, null) }) { Text("Share to…") }
        }
    }
}

private fun share(context: Context, file: File, packageName: String?) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (packageName != null) setPackage(packageName)
    }
    try {
        context.startActivity(if (packageName == null) Intent.createChooser(intent, "Send voice note") else intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "That app isn't installed", Toast.LENGTH_SHORT).show()
    }
}
