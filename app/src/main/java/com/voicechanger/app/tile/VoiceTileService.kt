package com.voicechanger.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.voicechanger.app.settings.SettingsRepository

/**
 * Quick Settings tile. In auto mode it is the master on/off switch; in manual
 * mode it turns conversion on for the call you are about to make.
 */
class VoiceTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val repo = SettingsRepository.get(this)
        repo.update { s ->
            if (s.autoActivate) {
                s.copy(enabled = !s.enabled)
            } else {
                val on = !s.tileOn
                s.copy(enabled = s.enabled || on, activeNow = on)
            }
        }
        refresh()
    }

    private fun refresh() {
        val s = SettingsRepository.get(this).state.value
        qsTile?.apply {
            state = if (s.tileOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Voice changer"
            updateTile()
        }
    }
}
