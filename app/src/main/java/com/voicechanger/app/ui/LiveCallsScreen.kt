package com.voicechanger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicechanger.app.engine.VoicePresets
import com.voicechanger.app.settings.TargetApp

@Composable
fun LiveCallsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsState()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Live calls", style = MaterialTheme.typography.headlineMedium)
        StatusCard(vm.moduleActive, vm.settingsShared)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow("Voice changer", "Change your voice in calls", s.enabled) { on ->
                    vm.update { it.copy(enabled = on) }
                }
                SwitchRow(
                    "Auto-activate in background",
                    "Start on every call automatically, without opening the app",
                    s.autoActivate,
                    enabled = s.enabled,
                ) { on -> vm.update { it.copy(autoActivate = on) } }
                if (!s.autoActivate) {
                    SwitchRow(
                        "Change voice now",
                        "Manual mode. You can also use the \"Voice changer\" Quick Settings tile during a call.",
                        s.activeNow,
                        enabled = s.enabled,
                    ) { on -> vm.update { it.copy(activeNow = on) } }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Apps", style = MaterialTheme.typography.titleMedium)
                TargetApp.entries.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = app.packageName in s.apps,
                            onCheckedChange = { on ->
                                vm.update {
                                    it.copy(apps = if (on) it.apps + app.packageName else it.apps - app.packageName)
                                }
                            },
                        )
                        Text(app.label)
                    }
                }
            }
        }

        val voiceName = VoicePresets.byId(s.presetId)?.name ?: "Custom"
        Text("Current voice: $voiceName (change it in Voices)", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusCard(moduleActive: Boolean, shared: Boolean) {
    val ok = moduleActive && shared
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (ok) "Ready for live calls" else "Live calls need root + LSPosed",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (ok) {
                    "The LSPosed module is active. Force-stop WhatsApp/Messenger once after changing apps in LSPosed."
                } else {
                    "1. Root the phone with Magisk and install LSPosed.\n" +
                        "2. In LSPosed, enable Voice Changer and tick WhatsApp, Messenger and this app.\n" +
                        "3. Force-stop those apps and reopen this one.\n" +
                        "Without root you can still use Voice notes."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
