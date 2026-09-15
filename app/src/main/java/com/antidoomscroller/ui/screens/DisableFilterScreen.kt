package com.antidoomscroller.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.LockGate
import com.antidoomscroller.ui.components.LockGateCopy
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.vpn.ContentFilterVpnService

/**
 * The only way out of the adult filter: a full wait, then a set of problems.
 *
 * Cancelling is one tap at any point. That asymmetry is the design - it is easy to change your
 * mind towards the thing you decided you wanted, and slow to change it the other way.
 */
@Composable
fun DisableFilterScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hours = settings.adultFilter.disableCooldownHours

    ScreenScaffold(title = "Turning the filter off", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            LockGate(
                repository = container.lockRepository,
                challengeSettings = settings.adultFilter.challenge,
                cooldownMinutes = hours * 60,
                copy = LockGateCopy(
                    armedTitle = "The filter is on",
                    armedSubtitle = "Turning it off takes $hours hours of waiting, and then a set " +
                        "of problems. You can cancel at any point, instantly.",
                    startLabel = "Start the $hours hour wait",
                    waitingSubtitle = "The filter stays on for the whole wait.",
                    disabledTitle = "The filter is off",
                    disabledSubtitle = "Turning it back on is instant, and starts the whole " +
                        "process over for next time.",
                    rearmLabel = "Turn the filter back on",
                ),
                onDisabled = { ContentFilterVpnService.stop(context) },
                onRearmed = {
                    container.settingsRepository.update {
                        it.copy(adultFilter = it.adultFilter.copy(enabled = true))
                    }
                },
            )
        }
    }
}
