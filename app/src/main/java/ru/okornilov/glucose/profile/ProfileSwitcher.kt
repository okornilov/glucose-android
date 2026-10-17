package ru.okornilov.glucose.profile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.app
import ru.okornilov.glucose.widget.GlucoseWidget

/** Переключение активного профиля уведомлений и обновление всего, где он виден. */
object ProfileSwitcher {

    fun activate(ctx: Context, id: String) {
        val prefs = ctx.app.prefs
        if (prefs.profile(id) == null) return
        prefs.activeProfileId = id
        refresh(ctx)
    }

    fun next(ctx: Context) {
        val prefs = ctx.app.prefs
        val list = prefs.profiles()
        val i = list.indexOfFirst { it.id == prefs.active.id }
        activate(ctx, list[(i + 1) % list.size].id)
    }

    /** Виджет, постоянное уведомление и плитка в шторке показывают активный профиль. */
    fun refresh(ctx: Context) {
        val app = ctx.app
        GlucoseWidget.updateAll(ctx)
        if (app.prefs.monitoring) {
            Notifications.notify(
                ctx, Notifications.ID_ONGOING, Notifications.ongoing(ctx, app.repo.last.value, app.repo.error.value)
            )
        }
        TileService.requestListeningState(ctx, ComponentName(ctx, ProfileTileService::class.java))
    }
}
