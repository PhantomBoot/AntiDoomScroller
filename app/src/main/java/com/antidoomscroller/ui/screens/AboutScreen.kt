package com.antidoomscroller.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.NavRow
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenDetectionReport: () -> Unit = {}) {
    val container = LocalContainer.current
    val signatures by container.settingsRepository.signatures.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "How this works", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            SectionCard(title = "What stays on this phone") {
                Paragraph(
                    "There is no account, no server and no analytics. Your settings, your " +
                        "messages, your blocklist and the cooldown state are files in this app's " +
                        "private storage, and they are excluded from cloud backup and device " +
                        "transfer.",
                )
                Paragraph(
                    "The feed guard reads the screen of the apps you switch on. Each screen is " +
                        "turned into a list of view names, scored, used for one decision, and " +
                        "dropped. Nothing about it is written down or sent anywhere.",
                )
                Paragraph(
                    "The app asks for internet permission for exactly one reason: while the " +
                        "adult filter is on, DNS questions your phone already makes are passed " +
                        "to the resolver your network gave you. Blocked names are answered on " +
                        "the device and never leave it.",
                )
            }

            SectionCard(title = "What it does not do") {
                Paragraph(
                    "It removes short-video feeds and nothing else. Messages, stories, ordinary " +
                        "posts, search, notifications and long-form video are recognised only so " +
                        "the app knows where a reel was opened from - the policy layer cannot " +
                        "block them, whatever the settings say.",
                )
            }

            SectionCard(title = "What it cannot stop") {
                Paragraph(
                    "This is friction, not a cage, and it is worth being honest about the edges. " +
                        "Someone with the phone unlocked can uninstall the app or turn off its " +
                        "accessibility permission in system settings. A browser with a " +
                        "hard-coded DNS-over-HTTPS server can route around the DNS filter, which " +
                        "is why the address-bar check exists as a second layer. A VPN app can " +
                        "replace this one, since Android allows only one at a time.",
                )
                Paragraph(
                    "The cooldown is measured on the phone's uptime rather than its calendar, so " +
                        "moving the clock forward does not shorten it. Time while the phone is " +
                        "off can only be witnessed by the calendar, so it is credited but capped " +
                        "per restart, and any disagreement between the two clocks adds an hour.",
                )
            }

            SectionCard(
                title = "Feed signatures",
                subtitle = "Version ${signatures.version}, ${signatures.apps.size} apps",
            ) {
                Paragraph(
                    "Apps rename their internal views between releases. If a feed stops being " +
                        "recognised, the signature pack can be replaced without a new build.",
                )
                Spacer(Modifier.height(8.dp))
                NavRow(
                    title = "What the guard sees",
                    description = "If a feed stops being recognised, this shows the view names " +
                        "behind the decision so they can be corrected.",
                    onClick = onOpenDetectionReport,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        container.settingsRepository.resetSignatures()
                        notice = "Signatures reset to the ones this build shipped with."
                    }
                }) {
                    Text("Reset to built-in signatures")
                }
            }

            notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
