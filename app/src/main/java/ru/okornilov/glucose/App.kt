package ru.okornilov.glucose

import android.app.Application
import android.content.Context
import ru.okornilov.glucose.alerts.AlertEngine
import ru.okornilov.glucose.alerts.Notifications
import ru.okornilov.glucose.data.Prefs
import ru.okornilov.glucose.data.ReadingsDb
import ru.okornilov.glucose.data.Repository
import ru.okornilov.glucose.widget.GlucoseWidget

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var db: ReadingsDb
        private set
    lateinit var alerts: AlertEngine
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        db = ReadingsDb(this)
        alerts = AlertEngine(this, prefs)
        repo = Repository(prefs, db, alerts) { GlucoseWidget.updateAll(this) }
        Notifications.syncChannels(this)
    }
}

val Context.app: App get() = applicationContext as App
