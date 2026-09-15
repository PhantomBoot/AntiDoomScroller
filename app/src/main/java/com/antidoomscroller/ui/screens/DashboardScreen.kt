package com.antidoomscroller.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.lock.ScrollPassController
import com.antidoomscroller.core.lock.ScrollPassPhase
import com.antidoomscroller.core.util.Durations
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.MainActivity
import com.antidoomscroller.ui.components.NavRow
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.StatusLine
import com.antidoomscroller.ui.components.SwitchRow
import com.antidoomscroller.vpn.ContentFilterVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onOpenApps: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAdultFilter: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val lock by container.lockRepository.state.collectAsStateWithLifecycle()
    val filterStatus by ContentFilterVpnService.observableStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? MainActivity

    var accessibilityOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityOn = activity?.isAccessibilityServiceEnabled() ?: false
            delay(1_500)
        }
    }

    val guardedApps = settings.profiles.count { it.enabled }
    val scrollPass by container.scrollPassRepository.state.collectAsStateWithLifecycle()

    // The phase is projected from stored progress, so it needs re-reading on a timer rather than
    // only when something is written.
    var passPhase by remember { mutableStateOf(ScrollPassPhase.AVAILABLE) }
    var passRemainingMs by remember { mutableLongStateOf(0L) }
    var confirmingPass by remember { mutableStateOf(false) }

    LaunchedEffect(scrollPass, settings.scrollPass) {
        while (true) {
            val now = container.scrollPassRepository.now()
            passPhase = ScrollPassController.phase(scrollPass, settings.scrollPass, now)
            passRemainingMs = ScrollPassController.remainingMs(scrollPass, settings.scrollPass, now)
            delay(1_000)
        }
    }

    if (confirmingPass) {
        AlertDialog(
            onDismissRequest = { confirmingPass = false },
            title = { Text("Use today's ${settings.scrollPass.durationMinutes} minutes?") },
            text = {
                Text(
                    "Feeds unblock straight away and lock themselves again after " +
                        "${settings.scrollPass.durationMinutes} minutes. You will not be able to " +
                        "do this again for ${settings.scrollPass.cooldownHours} hours, and it " +
                        "cannot be cancelled once it starts.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingPass = false
                    scope.launch { container.scrollPassRepository.start(settings.scrollPass) }
                }) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPass = false }) { Text("Not now") }
            },
        )
    }

    ScreenScaffold(title = "AntiDoomScroller") { padding ->
        ScrollingBody(padding) {
            if (!accessibilityOn) {
                SectionCard(
                    title = "Turn the guard on",
                    subtitle = "AntiDoomScroller needs the accessibility permission to see when a " +
                        "Reels or Shorts feed opens. It reads those screens on this device only, " +
                        "and stores nothing.",
                ) {
                    Button(onClick = { activity?.openAccessibilitySettings() }) {
                        Text("Open accessibility settings")
                    }
                }
            }

            SectionCard(title = "Status") {
                StatusLine(
                    label = "Feed guard",
                    value = if (accessibilityOn && settings.masterEnabled) "Running" else "Off",
                    ok = accessibilityOn && settings.masterEnabled,
                )
                StatusLine(
                    label = "Adult content filter",
                    value = when {
                        !settings.adultFilter.enabled -> "Off"
                        lock.phase == LockPhase.DISABLED -> "Unlocked"
                        filterStatus.running -> "Blocking"
                        else -> "Starting"
                    },
                    ok = settings.adultFilter.enabled && lock.phase != LockPhase.DISABLED,
                )
                if (filterStatus.running) {
                    StatusLine(
                        label = "Blocked lookups this session",
                        value = filterStatus.stats.blocked.toString(),
                        ok = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Guard short-video feeds",
                    description = "The master switch for Reels, Shorts and the rest.",
                    checked = settings.masterEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.update { it.copy(masterEnabled = enabled) }
                        }
                    },
                )
            }

            if (settings.scrollPass.enabled) {
                SectionCard(
                    title = "Scroll on purpose",
                    subtitle = when (passPhase) {
                        ScrollPassPhase.AVAILABLE ->
                            "One run of ${settings.scrollPass.durationMinutes} minutes with the " +
                                "feeds open. Using it spends the whole day's allowance."

                        ScrollPassPhase.RUNNING -> "Feeds are open. Blocking comes back on its own."
                        ScrollPassPhase.COOLING -> "Spent for today."
                    },
                ) {
                    when (passPhase) {
                        ScrollPassPhase.AVAILABLE -> Button(onClick = { confirmingPass = true }) {
                            Text("Scroll for ${settings.scrollPass.durationMinutes} minutes")
                        }

                        ScrollPassPhase.RUNNING -> {
                            Text(
                                Durations.formatPrecise(passRemainingMs),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "left before feeds lock again",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        ScrollPassPhase.COOLING -> OutlinedButton(onClick = {}, enabled = false) {
                            Text("Available again in ${Durations.format(passRemainingMs)}")
                        }
                    }
                }
            }

            SectionCard(
                title = "What gets removed",
                subtitle = "Short-video feeds, and nothing else. Messages, stories, posts, search " +
                    "and long-form video are never touched.",
            ) {
                NavRow(
                    title = "Apps and feeds",
                    description = "Per-app switches, including reels sent in DMs.",
                    trailing = "$guardedApps on",
                    onClick = onOpenApps,
                )
                NavRow(
                    title = "Your messages",
                    description = "The words shown when something is blocked.",
                    onClick = onOpenMessages,
                )
                NavRow(
                    title = "Scheduled breaks",
                    description = if (settings.schedule.enabled) {
                        "${settings.schedule.windows.size} window(s)"
                    } else {
                        "Off"
                    },
                    onClick = onOpenSchedule,
                )
            }

            SectionCard(title = "Adult content filter") {
                NavRow(
                    title = "Filter and blocklist",
                    description = "Blocks in every browser. Turning it off takes 48 hours.",
                    trailing = if (settings.adultFilter.enabled) "On" else "Off",
                    onClick = onOpenAdultFilter,
                )
            }

            SectionCard(title = "Keeping it running") {
                NavRow(
                    title = "Battery settings",
                    description = "Exclude AntiDoomScroller from battery optimisation so it is " +
                        "not stopped in the background.",
                    onClick = { activity?.openBatterySettings() },
                )
                NavRow(
                    title = "How this works and what it stores",
                    description = "No account, no analytics, no servers.",
                    onClick = onOpenAbout,
                )
            }

            Text(
                text = "Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
