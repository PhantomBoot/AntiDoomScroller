package com.antidoomscroller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.antidoomscroller.work.CooldownWorker

class AntiDoomScrollerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        CooldownWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BLOCKS, getString(R.string.channel_blocks_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.channel_blocks_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGES, getString(R.string.channel_nudges_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = getString(R.string.channel_nudges_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FILTER, getString(R.string.channel_filter_name), NotificationManager.IMPORTANCE_MIN)
                .apply { description = getString(R.string.channel_filter_description) },
        )
    }

    companion object {
        const val CHANNEL_BLOCKS = "blocks"
        const val CHANNEL_NUDGES = "nudges"
        const val CHANNEL_FILTER = "filter"
    }
}
