package com.antidoomscroller.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.LockGate
import com.antidoomscroller.ui.components.LockGateCopy
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody

/**
 * The gate in front of the master switch.
 *
 * Without it that switch is the single tap that undoes every other piece of friction in the app,
 * which would make all of them decorative. Ten minutes is long enough to outlast the urge and
 * short enough to sit through when the reason is real; the problems are there so it cannot be
 * done without noticing.
 */
@Composable
fun DisableGuardScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val minutes = settings.masterLock.cooldownMinutes

    ScreenScaffold(title = "Turning the guard off", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            LockGate(
                repository = container.masterLockRepository,
                challengeSettings = settings.masterLock.challenge,
                cooldownMinutes = minutes,
                copy = LockGateCopy(
                    armedTitle = "The guard is on",
                    armedSubtitle = "Turning it off takes $minutes minutes of waiting and then a " +
                        "set of problems. Cancelling takes one tap, at any point.",
                    startLabel = "Start the $minutes minute wait",
                    waitingSubtitle = "Feeds stay guarded for the whole wait.",
                    disabledTitle = "The guard is off",
                    disabledSubtitle = "Nothing is being blocked. Turning it back on is instant.",
                    rearmLabel = "Turn the guard back on",
                ),
                onDisabled = {
                    container.settingsRepository.update { it.copy(masterEnabled = false) }
                },
                onRearmed = {
                    container.settingsRepository.update { it.copy(masterEnabled = true) }
                },
            )
        }
    }
}
