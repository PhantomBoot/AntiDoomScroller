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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { day ->
                            val selected = day in window.days
                            TextButton(onClick = {
                                updateWindows { list ->
                                    list.map {
                                        if (it.id != window.id) {
                                            it
                                        } else {
                                            val days = if (selected) it.days - day else it.days + day
                                            it.copy(days = days)
                                        }
                                    }
                                }
                            }) {
                                Text(
                                    ScheduleEvaluator.dayLabel(day),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

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
