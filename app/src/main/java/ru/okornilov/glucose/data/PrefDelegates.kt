package ru.okornilov.glucose.data

import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class Pref<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
    private val onSet: () -> Unit,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = read()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        write(value)
        onSet()
    }
}

fun SharedPreferences.boolPref(key: String, def: Boolean, onSet: () -> Unit = {}): ReadWriteProperty<Any?, Boolean> =
    Pref({ getBoolean(key, def) }, { edit().putBoolean(key, it).apply() }, onSet)

fun SharedPreferences.intPref(key: String, def: Int, onSet: () -> Unit = {}): ReadWriteProperty<Any?, Int> =
    Pref({ getInt(key, def) }, { edit().putInt(key, it).apply() }, onSet)

fun SharedPreferences.longPref(key: String, def: Long, onSet: () -> Unit = {}): ReadWriteProperty<Any?, Long> =
    Pref({ getLong(key, def) }, { edit().putLong(key, it).apply() }, onSet)

fun SharedPreferences.stringPref(key: String, def: String, onSet: () -> Unit = {}): ReadWriteProperty<Any?, String> =
    Pref({ getString(key, def) ?: def }, { edit().putString(key, it).apply() }, onSet)

/** Double храним строкой: через Float 3.8 превратилось бы в 3.7999999, и порог «≤ 3.8» не срабатывал бы на 3.8. */
fun SharedPreferences.doublePref(key: String, def: Double, onSet: () -> Unit = {}): ReadWriteProperty<Any?, Double> =
    Pref({ getString(key, null)?.toDoubleOrNull() ?: def }, { edit().putString(key, it.toString()).apply() }, onSet)
