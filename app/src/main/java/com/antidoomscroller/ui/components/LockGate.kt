package com.antidoomscroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.lock.AnswerResult
import com.antidoomscroller.core.lock.CooldownProgress
import com.antidoomscroller.core.lock.LockController
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.model.ChallengeSettings
import com.antidoomscroller.core.util.Durations
import com.antidoomscroller.data.LockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The words a gate uses, which differ between the filter and the master switch. */
data class LockGateCopy(
    val armedTitle: String,
    val armedSubtitle: String,
    val startLabel: String,
    val waitingSubtitle: String,
    val disabledTitle: String,
    val disabledSubtitle: String,
    val rearmLabel: String,
)

/**
 * A wait, and then a set of problems, in front of switching something off.
 *
 * One implementation serves both gates in the app. The asymmetry is the whole design: cancelling
 * is a single tap at any point and switching back on is instant, while switching off costs the
 * full wait every time. The wait is credited against monotonic uptime, so moving the phone's
 * clock forward does nothing, and the countdown is projected to the current second rather than
 * read off the last checkpoint.
 */
@Composable
fun LockGate(
    repository: LockRepository,
    challengeSettings: ChallengeSettings,
    cooldownMinutes: Int,
    copy: LockGateCopy,
    onDisabled: suspend () -> Unit,
    onRearmed: suspend () -> Unit,
) {
    val lock by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var remainingMs by remember { mutableLongStateOf(0L) }
    var penaltyMs by remember { mutableLongStateOf(0L) }
    var answer by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    // A live countdown without hammering the disk: project the persisted progress forward.
    LaunchedEffect(lock) {
        while (true) {
            val now = repository.now()
            val projected = CooldownProgress.advance(lock.progress, now).state
            remainingMs = CooldownProgress.remainingMs(projected, lock.requiredMs, now)
            penaltyMs = (lock.penaltyUntilAccruedMs - projected.accruedMs).coerceAtLeast(0)
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            repository.checkpoint()
            delay(30_000)
        }
    }

    val problems = LockController.challengeFor(lock, challengeSettings)?.problems.orEmpty()
    val problem = problems.getOrNull(lock.solvedCount)

    when (lock.phase) {
        LockPhase.ARMED -> SectionCard(
            title = copy.armedTitle,
            subtitle = copy.armedSubtitle,
        ) {
            Button(onClick = { scope.launch { repository.requestDisable(cooldownMinutes) } }) {
                Text(copy.startLabel)
            }
        }

        LockPhase.COOLING -> SectionCard(title = "Waiting", subtitle = copy.waitingSubtitle) {
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
                    "The wait is measured on the phone's uptime, not its calendar, so moving the " +
                        "clock does not shorten it. Each change adds an hour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    repository.cancelDisableRequest()
                    notice = "Cancelled. Nothing changed."
                }
            }) {
                Text("Cancel the request")
            }
        }

        LockPhase.CHALLENGE -> SectionCard(
            title = "Last step",
            subtitle = "Solve ${challengeSettings.problemCount} problems in a row. A wrong answer " +
                "clears them all and starts a short wait, so it is worth doing them properly.",
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
                                notice = when (val result = repository.submitAnswer(submitted, challengeSettings)) {
                                    is AnswerResult.Accepted ->
                                        "Correct. ${result.remainingProblems} to go."

                                    is AnswerResult.Rejected ->
                                        "Wrong. Waiting ${Durations.format(result.penaltyMs)} before a new set."

                                    is AnswerResult.Completed -> {
                                        onDisabled()
                                        copy.disabledTitle
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
                            repository.cancelDisableRequest()
                            notice = "Cancelled. Nothing changed."
                        }
                    }) {
                        Text("Never mind")
                    }
                }
            }
        }

        LockPhase.DISABLED -> SectionCard(
            title = copy.disabledTitle,
            subtitle = copy.disabledSubtitle,
        ) {
            Button(onClick = {
                scope.launch {
                    repository.rearm()
                    onRearmed()
                    notice = "Back on."
                }
            }) {
                Text(copy.rearmLabel)
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
