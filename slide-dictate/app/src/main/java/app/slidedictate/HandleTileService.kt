package app.slidedictate

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: show or hide the edge handle without opening the app. */
class HandleTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (Prefs.handleVisible(this@HandleTileService)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Dictate handle"
            icon = Icon.createWithResource(this@HandleTileService, android.R.drawable.ic_btn_speak_now)
            updateTile()
        }
    }

    override fun onClick() {
        val on = !Prefs.handleVisible(this)
        Prefs.setHandleVisible(this, on)
        sendBroadcast(Intent(DictationService.ACTION_HANDLE).apply {
            setPackage(packageName)
            putExtra(DictationService.EXTRA_VISIBLE, on)
        })
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
