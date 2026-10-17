package ru.okornilov.glucose.profile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ru.okornilov.glucose.R
import ru.okornilov.glucose.app

/** Плитка в быстрых настройках: показывает активный профиль, нажатие — следующий профиль. */
class ProfileTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        ProfileSwitcher.next(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val name = app.prefs.active.name
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.label = "Профиль"
            tile.subtitle = name
        } else {
            tile.label = name
        }
        tile.updateTile()
    }
}
