package com.antidoomscroller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.messages.MessageBook
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.model.MessageRotation
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val messages = settings.messages

    fun setMessages(kind: MessageKind, list: List<String>) {
        scope.launch {
            container.settingsRepository.update { it.copy(messages = it.messages.withMessages(kind, list)) }
        }
    }

    ScreenScaffold(title = "Your messages", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            Text(
                "These are the only words the app ever shows you. Delete every built-in line and " +
                    "write your own; nothing is added back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            MessageListCard(
                title = "When a feed is blocked",
                messages = messages.feedBlockMessages,
                onChange = { setMessages(MessageKind.FEED_BLOCK, it) },
            )
            MessageListCard(
                title = "Scroll reminders",
                messages = messages.antiScrollMessages,
                onChange = { setMessages(MessageKind.ANTI_SCROLL, it) },
            )
            MessageListCard(
                title = "When a blocked site is opened",
                messages = messages.adultBlockMessages,
                onChange = { setMessages(MessageKind.ADULT_BLOCK, it) },
            )

            SectionCard(title = "How they are shown") {
                MessageRotation.entries.forEach { rotation ->
                    SwitchRow(
                        title = when (rotation) {
                            MessageRotation.RANDOM -> "Pick at random"
                            MessageRotation.SEQUENTIAL -> "In order, one after another"
                            MessageRotation.FIRST -> "Always the first one"
                        },
                        checked = messages.rotation == rotation,
                        onCheckedChange = { selected ->
                            if (!selected) return@SwitchRow
                            scope.launch {
                                container.settingsRepository.update {
                                    it.copy(messages = it.messages.copy(rotation = rotation))
                                }
                            }
                        },
                    )
                }
                SwitchRow(
                    title = "Name the app in the heading",
                    checked = messages.showAppName,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(messages = it.messages.copy(showAppName = value))
                            }
                        }
                    },
                )
                SwitchRow(
                    title = "Name which feed was blocked",
                    checked = messages.showBlockedSurface,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(messages = it.messages.copy(showBlockedSurface = value))
                            }
                        }
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Each one stays up for ${messages.holdSeconds}s",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "How long a message is held before the next is shown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            scope.launch {
                                container.settingsRepository.update {
                                    val next = (it.messages.holdSeconds - 1).coerceIn(1, 120)
                                    it.copy(messages = it.messages.copy(holdSeconds = next))
                                }
                            }
                        }) { Text("-1") }
                        TextButton(onClick = {
                            scope.launch {
                                container.settingsRepository.update {
                                    val next = (it.messages.holdSeconds + 1).coerceIn(1, 120)
                                    it.copy(messages = it.messages.copy(holdSeconds = next))
                                }
                            }
                        }) { Text("+1") }
                    }
                }
                SwitchRow(
                    title = "Also send a notification",
                    description = "Off by default: the on-screen card is usually enough.",
                    checked = messages.notifyOnBlock,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(messages = it.messages.copy(notifyOnBlock = value))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageListCard(
    title: String,
    messages: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    SectionCard(title = title, subtitle = "${messages.size} message(s)") {
        Column {
            messages.forEachIndexed { index, message ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onChange(messages.filterIndexed { i, _ -> i != index })
                    }) {
                        Text("Remove")
                    }
                }
            }
            if (messages.isEmpty()) {
                Text(
                    "No messages. The card will show a neutral line instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Add a message") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val cleaned = MessageBook.sanitise(draft)
                    if (cleaned.isNotEmpty()) {
                        onChange(messages + cleaned)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
            ) {
                Text("Add")
            }
        }
    }
}
