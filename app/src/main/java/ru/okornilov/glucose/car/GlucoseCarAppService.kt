package ru.okornilov.glucose.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Точка входа Android Auto: один экран с текущим сахаром. */
class GlucoseCarAppService : CarAppService() {

    // Приложение ставится не из Google Play и подключается к Android Auto в режиме «Неизвестные источники» —
    // проверять подпись хоста (Android Auto / эмулятора) не нужно
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = GlucoseScreen(carContext)
    }
}
