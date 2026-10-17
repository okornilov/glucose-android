package ru.okornilov.glucose.profile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Кнопки профилей в постоянном уведомлении. */
class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ACTIVATE -> intent.getStringExtra(EXTRA_ID)?.let { ProfileSwitcher.activate(context, it) }
        }
    }

    companion object {
        private const val ACTION_ACTIVATE = "ru.okornilov.glucose.PROFILE_ACTIVATE"
        private const val EXTRA_ID = "id"

        /** slot — номер кнопки: у каждой свой PendingIntent, иначе они бы затирали друг друга. */
        fun activateIntent(ctx: Context, id: String, slot: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, 1000 + slot,
            Intent(ctx, ProfileReceiver::class.java).setAction(ACTION_ACTIVATE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
