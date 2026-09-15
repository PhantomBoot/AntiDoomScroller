package com.antidoomscroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.model.BreakWindow
import com.antidoomscroller.core.schedule.ScheduleEvaluator
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val schedule = settings.schedule

    fun updateWindows(transform: (List<BreakWindow>) -> List<BreakWindow>) {
        scope.launch {
            container.settingsRepository.update {
                it.copy(schedule = it.schedule.copy(windows = transform(it.schedule.windows)))
            }
        }
    }

    ScreenScaffold(title = "Scheduled breaks", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            SectionCard(
                title = "Breaks",
                subtitle = "A window where feed blocking pauses on purpose - a Sunday evening " +
                    "allowance, say. It locks itself back afterwards. The adult filter is never " +
                    "part of a schedule.",
            ) {
                SwitchRow(
                    title = "Use scheduled breaks",
                    checked = schedule.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(schedule = it.schedule.copy(enabled = enabled))
                            }
                        }
                    },
                )
            }

            SectionCard(
                title = "Scroll on purpose",
                subtitle = "A button on the home screen that opens the feeds for a set run, then " +
                    "locks them and stays out of reach for the rest of the day. Unlike a " +
                    "scheduled break it is decided in the moment, which is why it costs so much.",
            ) {
                SwitchRow(
                    title = "Offer the button",
                    checked = settings.scrollPass.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(scrollPass = it.scrollPass.copy(enabled = enabled))
                            }
                        }
                    },
                )
                StepperRow(
                    label = "Length",
                    value = "${settings.scrollPass.durationMinutes} min",
                    onChange = { step ->
                        scope.launch {
                            container.settingsRepository.update {
                                val next = (it.scrollPass.durationMinutes + step).coerceIn(5, 120)
                                it.copy(scrollPass = it.scrollPass.copy(durationMinutes = next))
                            }
                        }
                    },
                    step = 5,
                )
                StepperRow(
                    label = "Then unavailable for",
                    value = "${settings.scrollPass.cooldownHours} h",
                    onChange = { step ->
                        scope.launch {
                            container.settingsRepository.update {
                                val next = (it.scrollPass.cooldownHours + step).coerceIn(1, 168)
                                it.copy(scrollPass = it.scrollPass.copy(cooldownHours = next))
                            }
                        }
                    },
                    step = 6,
                )
            }

            schedule.windows.forEach { window ->
                SectionCard(
                    title = window.label,
                    subtitle = ScheduleEvaluator.formatWindow(window),
                ) {
                    SwitchRow(
                        title = "Active",
                        checked = window.enabled,
                        onCheckedChange = { enabled ->
                            updateWindows { list ->
                                list.map { if (it.id == window.id) it.copy(enabled = enabled) else it }
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    DayPicker(
                        selected = window.days,
                        onToggle = { day ->
                            updateWindows { list ->
                                list.map {
                                    if (it.id != window.id) {
                                        it
                                    } else {
                                        val days = if (day in it.days) it.days - day else it.days + day
                                        it.copy(days = days)
                                    }
                                }
                            }
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    TimeRow(
                        label = "Starts",
                        minuteOfDay = window.startMinuteOfDay,
                        onChange = { minute ->
                            updateWindows { list ->
                                list.map { if (it.id == window.id) it.copy(startMinuteOfDay = minute) else it }
                            }
                        },
                    )
                    TimeRow(
                        label = "Ends",
                        minuteOfDay = window.endMinuteOfDay,
                        onChange = { minute ->
                            updateWindows { list ->
                                list.map { if (it.id == window.id) it.copy(endMinuteOfDay = minute) else it }
                            }
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { updateWindows { list -> list.filter { it.id != window.id } } }) {
                        Text("Delete this window")
                    }
                }
            }

            Column(Modifier.padding(24.dp)) {
                Button(onClick = {
                    updateWindows { list ->
                        list + BreakWindow(
                            id = "window-${System.currentTimeMillis()}",
                            label = "Break ${list.size + 1}",
                            days = setOf(7),
                            startMinuteOfDay = 19 * 60,
                            endMinuteOfDay = 20 * 60,
                        )
                    }
                }) {
                    Text("Add a break window")
                }
            }
        }
    }
}

/**
 * All seven days, always reachable.
 *
 * The previous version laid seven [TextButton]s in a plain row; each carries a 58dp minimum
 * width, so the row measured wider than a phone and Saturday and Sunday were clipped off the
 * right-hand edge with no way to scroll to them. Equal weights make the row fit whatever the
 * screen is.
 */
@Composable
private fun StepperRow(label: String, value: String, step: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label $value", style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = { onChange(-step) }) { Text("-$step") }
            TextButton(onClick = { onChange(step) }) { Text("+$step") }
        }
    }
}

@Composable
private fun DayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..7).forEach { day ->
            val isSelected = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ScheduleEvaluator.dayLabel(day),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label ${ScheduleEvaluator.formatMinute(minuteOfDay)}", style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = { onChange(wrap(minuteOfDay - 30)) }) { Text("-30m") }
            TextButton(onClick = { onChange(wrap(minuteOfDay + 30)) }) { Text("+30m") }
        }
    }
}

private fun wrap(minute: Int): Int {
    val day = 24 * 60
    return ((minute % day) + day) % day
}
