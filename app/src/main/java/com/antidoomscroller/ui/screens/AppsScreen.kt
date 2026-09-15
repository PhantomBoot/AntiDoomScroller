package com.antidoomscroller.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.model.BlockStyle
import com.antidoomscroller.core.model.DefaultProfiles
import com.antidoomscroller.core.model.RuleAction
import com.antidoomscroller.core.model.Sensitivity
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.components.NavRow
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Apps and feeds", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            SectionCard(
                title = "Guarded apps",
                subtitle = "Turn an app on to have its short-video feed removed. Everything else " +
                    "in that app keeps working exactly as before.",
            ) {
                settings.profiles.forEach { profile ->
                    SwitchRow(
                        title = profile.displayName,
                        description = profile.surfaceActions
                            .count { it.value == RuleAction.BLOCK }
                            .let { "$it feed(s) blocked" },
                        checked = profile.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                container.settingsRepository.updateProfile(profile.packageName) {
                                    it.copy(enabled = enabled)
                                }
                            }
                        },
                    )
                    NavRow(
                        title = "Settings for ${profile.displayName}",
                        description = "Which feeds, how they are removed, scroll reminders.",
                        onClick = { onOpenApp(profile.packageName) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val profile = settings.profileFor(packageName)

    ScreenScaffold(title = profile?.displayName ?: "App", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            if (profile == null) {
                Text(
                    "This app is not configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
                return@ScrollingBody
            }

            SectionCard(
                title = "Which feeds to remove",
                subtitle = "Each switch is about short video only. Nothing here can hide your " +
                    "messages, your stories or ordinary posts.",
            ) {
                DefaultProfiles.configurableSurfaces(packageName).forEach { surface ->
                    SwitchRow(
                        title = surface.label,
                        description = surface.description,
                        checked = profile.actionFor(surface) == RuleAction.BLOCK,
                        onCheckedChange = { blocked ->
                            scope.launch {
                                container.settingsRepository.updateProfile(packageName) {
                                    it.withAction(surface, if (blocked) RuleAction.BLOCK else RuleAction.ALLOW)
                                }
                            }
                        },
                    )
                }
            }

            SectionCard(
                title = "How it is removed",
                subtitle = "Cover leaves you exactly where you are and hides the video. Back out " +
                    "returns you to the previous tab, which suits a whole tab like Shorts.",
            ) {
                SwitchRow(
                    title = "Cover it with a black box",
                    description = "Off means: back out to the previous screen instead.",
                    checked = profile.blockStyle == BlockStyle.COVER,
                    onCheckedChange = { cover ->
                        scope.launch {
                            container.settingsRepository.updateProfile(packageName) {
                                it.copy(blockStyle = if (cover) BlockStyle.COVER else BlockStyle.EXIT)
                            }
                        }
                    },
                )
            }

            SectionCard(
                title = "Scroll reminders",
                subtitle = "Notices a long unbroken scroll and shows one of your own messages.",
            ) {
                SwitchRow(
                    title = "Anti-scroll mode",
                    checked = profile.antiScroll.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.updateProfile(packageName) {
                                it.copy(antiScroll = it.antiScroll.copy(enabled = enabled))
                            }
                        }
                    },
                )
                Sensitivity.entries.filter { it != Sensitivity.CUSTOM }.forEach { level ->
                    SwitchRow(
                        title = level.name.lowercase().replaceFirstChar { it.uppercase() },
                        description = "Reminds after about ${sensitivityThreshold(level)} scrolls.",
                        checked = profile.antiScroll.sensitivity == level,
                        enabled = profile.antiScroll.enabled,
                        onCheckedChange = { selected ->
                            if (!selected) return@SwitchRow
                            scope.launch {
                                container.settingsRepository.updateProfile(packageName) {
                                    it.copy(antiScroll = it.antiScroll.copy(sensitivity = level))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun sensitivityThreshold(level: Sensitivity): Int = when (level) {
    Sensitivity.GENTLE -> 80
    Sensitivity.BALANCED -> 45
    Sensitivity.STRICT -> 20
    Sensitivity.CUSTOM -> 40
}
