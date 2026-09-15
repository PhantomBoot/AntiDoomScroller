package com.antidoomscroller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.lock.AnswerResult
import com.antidoomscroller.core.lock.CooldownProgress
import com.antidoomscroller.core.lock.LockController
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.util.Durations
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.StatusLine
import com.antidoomscroller.vpn.ContentFilterVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val lock by container.lockRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var remainingMs by remember { mutableLongStateOf(0L) }
    var penaltyMs by remember { mutableLongStateOf(0L) }
    var answer by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    // A live countdown without hammering the disk: project the persisted progress forward.
    LaunchedEffect(lock) {
        while (true) {
            val now = container.lockRepository.now()
            val projected = CooldownProgress.advance(lock.progress, now).state
            remainingMs = CooldownProgress.remainingMs(projected, lock.requiredMs, now)
            penaltyMs = (lock.penaltyUntilAccruedMs - projected.accruedMs).coerceAtLeast(0)
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            container.lockRepository.checkpoint()
            delay(30_000)
        }
    }

    val challengeSettings = settings.adultFilter.challenge
    val problems = LockController.challengeFor(lock, challengeSettings)?.problems.orEmpty()
    val problem = problems.getOrNull(lock.solvedCount)

    ScreenScaffold(title = "Turning the filter off", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            when (lock.phase) {
                LockPhase.ARMED -> SectionCard(
                    title = "The filter is on",
                    subtitle = "Turning it off takes ${settings.adultFilter.disableCooldownHours} " +
                        "hours of waiting, and then a set of problems. You can cancel at any " +
                        "point, instantly.",
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                container.lockRepository.requestDisable(settings.adultFilter.disableCooldownHours)
                            }
                        },
                    ) {
                        Text("Start the ${settings.adultFilter.disableCooldownHours} hour wait")
                    }
                }

                LockPhase.COOLING -> SectionCard(
                    title = "Waiting",
                    subtitle = "The filter stays on for the whole wait.",
                ) {
                    Text(
                        Durations.formatPrecise(remainingMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${Durations.format(remainingMs)} to go",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusLine(
                        label = "Clock changes detected",
                        value = lock.progress.tamperEvents.toString(),
                        ok = lock.progress.tamperEvents == 0,
                    )
                    if (lock.progress.tamperEvents > 0) {
                        Text(
                            "The wait is measured on the phone's uptime, not its calendar, so " +
                                "moving the clock does not shorten it. Each change adds an hour.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            container.lockRepository.cancelDisableRequest()
                            notice = "Request cancelled. The filter stays on."
                        }
                    }) {
                        Text("Cancel the request")
                    }
                }

                LockPhase.CHALLENGE -> SectionCard(
                    title = "Last step",
                    subtitle = "Solve ${challengeSettings.problemCount} problems in a row. A wrong " +
                        "answer clears them all and starts a short wait, so it is worth doing " +
                        "them properly.",
                ) {
                    if (penaltyMs > 0) {
                        Text(
                            "Wrong answer. New problems in ${Durations.formatPrecise(penaltyMs)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (problem != null) {
                        Text(
                            "Problem ${lock.solvedCount + 1} of ${problems.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(problem.prompt, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Answer") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val submitted = answer
                                    answer = ""
                                    scope.launch {
                                        notice = when (val result =
                                            container.lockRepository.submitAnswer(submitted, challengeSettings)) {
                                            is AnswerResult.Accepted ->
                                                "Correct. ${result.remainingProblems} to go."

                                            is AnswerResult.Rejected ->
                                                "Wrong. Waiting ${Durations.format(result.penaltyMs)} " +
                                                    "before a new set."

                                            is AnswerResult.Completed -> {
                                                ContentFilterVpnService.stop(context)
                                                "The filter is off."
                                            }

                                            is AnswerResult.NotReady -> "Not available yet."
                                        }
                                    }
                                },
                                enabled = answer.isNotBlank(),
                            ) {
                                Text("Submit")
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    container.lockRepository.cancelDisableRequest()
                                    notice = "Cancelled. The filter stays on."
                                }
                            }) {
                                Text("Never mind")
                            }
                        }
                    }
                }

                LockPhase.DISABLED -> SectionCard(
                    title = "The filter is off",
                    subtitle = "Turning it back on is instant, and starts the whole process over " +
                        "for next time.",
                ) {
                    Button(onClick = {
                        scope.launch {
                            container.lockRepository.rearm()
                            container.settingsRepository.update {
                                it.copy(adultFilter = it.adultFilter.copy(enabled = true))
                            }
                            notice = "Filter armed again."
                        }
                    }) {
                        Text("Turn the filter back on")
                    }
                }
            }

            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}
