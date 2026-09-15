package com.antidoomscroller.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.antidoomscroller.AntiDoomScrollerApp
import com.antidoomscroller.R
import com.antidoomscroller.ui.MainActivity

/**
 * Local notifications only - the user's own words, on their own phone. Nothing is templated from
 * anywhere else and no content is sent off the device.
 */
object Notifications {

    const val FILTER_ID = 1001
    private const val NUDGE_ID = 1002
    private const val BLOCK_ID = 1003

    fun nudge(context: Context, title: String, body: String) {
        post(context, NUDGE_ID, AntiDoomScrollerApp.CHANNEL_NUDGES, title, body, autoCancel = true)
    }

    fun blocked(context: Context, title: String, body: String) {
        post(context, BLOCK_ID, AntiDoomScrollerApp.CHANNEL_BLOCKS, title, body, autoCancel = true)
    }

    /** The ongoing notification Android requires while the filter's tunnel is up. */
    fun filterRunning(context: Context): Notification =
        builder(context, AntiDoomScrollerApp.CHANNEL_FILTER)
            .setContentTitle(context.getString(R.string.filter_running))
            .setContentText(context.getString(R.string.filter_running_detail))
            .setOngoing(true)
            .build()

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        autoCancel: Boolean,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = builder(context, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(autoCancel)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    private fun builder(context: Context, channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
}
