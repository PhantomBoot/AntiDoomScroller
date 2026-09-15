package com.antidoomscroller

import android.content.Context
import com.antidoomscroller.data.BlocklistRepository
import com.antidoomscroller.data.LockRepository
import com.antidoomscroller.data.ScrollPassRepository
import com.antidoomscroller.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container.
 *
 * Small enough that a DI framework would only add build time, and it keeps the dependency list
 * short - which is itself part of the privacy story.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(applicationContext, scope) }
    val lockRepository: LockRepository by lazy { LockRepository(applicationContext, scope) }
    val scrollPassRepository: ScrollPassRepository by lazy { ScrollPassRepository(applicationContext, scope) }
    val blocklistRepository: BlocklistRepository by lazy {
        BlocklistRepository(applicationContext, scope, settingsRepository)
    }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as AntiDoomScrollerApp).container
    }
}
