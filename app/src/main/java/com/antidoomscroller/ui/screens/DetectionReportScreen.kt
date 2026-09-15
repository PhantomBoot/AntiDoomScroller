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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.antidoomscroller.service.DetectionLog
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.SwitchRow
import kotlinx.coroutines.delay

/**
 * What the guard is actually seeing, for when a feed stops being recognised.
 *
 * Apps rename their views between releases; this turns "it stopped working" into the list of
 * names that would fix it. Recording is off until switched on, kept in memory only, and cleared
 * when it is switched off again.
 */
@Composable
fun DetectionReportScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var recording by remember { mutableStateOf(DetectionLog.enabled) }
    var entries by remember { mutableStateOf(DetectionLog.snapshot()) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(recording) {
        while (true) {
            entries = DetectionLog.snapshot()
            delay(1_000)
        }
    }

    ScreenScaffold(title = "What the guard sees", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            SectionCard(
                title = "Recording",
                subtitle = "Switch this on, open the screen that is not working, then come back " +
                    "here. Held in memory only - it is never written to disk and never leaves " +
                    "this phone unless you copy it out yourself.",
            ) {
                SwitchRow(
                    title = "Record what the guard sees",
                    checked = recording,
                    onCheckedChange = {
                        recording = it
                        DetectionLog.enabled = it
                        entries = DetectionLog.snapshot()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(DetectionLog.asText()))
                            copied = true
                        },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Text("Copy report")
                    }
                    OutlinedButton(
                        onClick = {
                            DetectionLog.clear()
                            entries = emptyList()
                            copied = false
                        },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Text("Clear")
                    }
                }
                if (copied) {
                    Spacer(Modifier.height(8.dp))
                    Text("Copied.", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (entries.isEmpty()) {
                Text(
                    if (recording) {
                        "Nothing yet. Open the app and screen that is misbehaving, then come back."
                    } else {
                        "Recording is off."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }

            entries.forEachIndexed { index, entry ->
                SectionCard(
                    title = "Screen ${index + 1}: ${entry.surface}",
                    subtitle = entry.packageName,
                ) {
                    Column {
                        if (entry.evidence.isNotEmpty()) {
                            Field("Matched on", entry.evidence.joinToString(", "))
                        }
                        Field("View ids", entry.viewIds.joinToString(", ").ifBlank { "none" })
                        Field(
                            "Descriptions",
                            entry.descriptions.joinToString(", ").ifBlank { "none" },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
