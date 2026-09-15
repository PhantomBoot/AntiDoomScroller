package com.antidoomscroller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antidoomscroller.AppContainer
import com.antidoomscroller.vpn.ContentFilterVpnService
import com.antidoomscroller.work.CooldownWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Brings the filter back after a reboot, and records a clock checkpoint straight away so the
 * cooldown accounts for the time the phone was off.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val container = AppContainer.from(context)
        val pending = goAsync()
        container.scope.launch(Dispatchers.IO) {
            try {
                container.lockRepository.checkpoint()
                CooldownWorker.schedule(context)

                val settings = container.settingsRepository.awaitLoaded()
                if (settings.adultFilter.enabled && container.lockRepository.isFilterActive()) {
                    ContentFilterVpnService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
