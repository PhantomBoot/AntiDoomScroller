package com.antidoomscroller.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.model.BlockResponseMode
import com.antidoomscroller.ui.LocalContainer
import com.antidoomscroller.ui.MainActivity
import com.antidoomscroller.ui.components.ScreenScaffold
import com.antidoomscroller.ui.components.ScrollingBody
import com.antidoomscroller.ui.components.SectionCard
import com.antidoomscroller.ui.components.StatusLine
import com.antidoomscroller.ui.components.SwitchRow
import com.antidoomscroller.vpn.ContentFilterVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdultFilterScreen(onBack: () -> Unit, onOpenDisableFlow: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val lock by container.lockRepository.state.collectAsStateWithLifecycle()
    val ruleset by container.blocklistRepository.ruleset.collectAsStateWithLifecycle()
    val filterStatus by ContentFilterVpnService.observableStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? MainActivity

    val filter = settings.adultFilter
    var domainDraft by remember { mutableStateOf("") }
    var allowDraft by remember { mutableStateOf("") }
    var importNotice by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                importNotice = "Could not read that file."
                return@launch
            }
            val parsed = container.blocklistRepository.parseImport(text)
            container.settingsRepository.update { current ->
                val merged = (current.adultFilter.customBlockedDomains + parsed.domains).distinct()
                current.copy(adultFilter = current.adultFilter.copy(customBlockedDomains = merged))
            }
            importNotice = "Added ${parsed.domains.size} domain(s) from that file."
        }
    }

    ScreenScaffold(title = "Adult content filter", onBack = onBack) { padding ->
        ScrollingBody(padding) {
            SectionCard(
                title = "Filter",
                subtitle = "Blocks sites in every browser on this phone, Chrome and Opera " +
                    "included, by filtering DNS on the device itself. Turning it on is instant. " +
                    "Turning it off takes 48 hours and a set of problems.",
            ) {
                SwitchRow(
                    title = "Block adult sites",
                    checked = filter.enabled && lock.phase != LockPhase.DISABLED,
                    onCheckedChange = { enable ->
                        if (enable) {
                            scope.launch {
                                container.settingsRepository.update {
                                    it.copy(adultFilter = it.adultFilter.copy(enabled = true))
                                }
                                container.lockRepository.rearm()
                                activity?.enableContentFilter()
                            }
                        } else {
                            // Never a straight toggle: this is what the cooldown exists for.
                            onOpenDisableFlow()
                        }
                    },
                )
                StatusLine(
                    label = "Tunnel",
                    value = if (filterStatus.running) "Running" else "Stopped",
                    ok = filterStatus.running,
                )
                StatusLine(
                    label = "Rules loaded",
                    value = "${ruleset.totalBlocked} blocked, ${ruleset.allowlistCount} allowed",
                    ok = ruleset.totalBlocked > 0,
                )
                filterStatus.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (lock.phase != LockPhase.ARMED) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenDisableFlow) {
                        Text(
                            when (lock.phase) {
                                LockPhase.COOLING -> "A turn-off request is waiting"
                                LockPhase.CHALLENGE -> "The wait is over: finish the challenge"
                                LockPhase.DISABLED -> "Filter is off - turn it back on"
                                LockPhase.ARMED -> "Manage"
                            },
                        )
                    }
                }
            }

            SectionCard(
                title = "Sites you add",
                subtitle = "One domain per line. Adding example.com also blocks every subdomain.",
            ) {
                DomainList(
                    domains = filter.customBlockedDomains,
                    emptyLabel = "Nothing added yet.",
                    onRemove = { domain ->
                        scope.launch {
                            container.settingsRepository.update { current ->
                                current.copy(
                                    adultFilter = current.adultFilter.copy(
                                        customBlockedDomains = current.adultFilter.customBlockedDomains - domain,
                                    ),
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = domainDraft,
                    onValueChange = { domainDraft = it },
                    label = { Text("Add a site to block") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val domain = DomainMatcher.normalise(domainDraft)
                            if (domain != null) {
                                scope.launch {
                                    container.settingsRepository.update { current ->
                                        val merged = (current.adultFilter.customBlockedDomains + domain).distinct()
                                        current.copy(
                                            adultFilter = current.adultFilter.copy(customBlockedDomains = merged),
                                        )
                                    }
                                }
                                domainDraft = ""
                            }
                        },
                        enabled = domainDraft.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("Import a list")
                    }
                }
                importNotice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionCard(
                title = "Sites to leave alone",
                subtitle = "Beats the bundled list, so a wrongly blocked site can be let through " +
                    "without editing anything else.",
            ) {
                DomainList(
                    domains = filter.allowlistDomains,
                    emptyLabel = "Nothing allowed explicitly.",
                    onRemove = { domain ->
                        scope.launch {
                            container.settingsRepository.update { current ->
                                current.copy(
                                    adultFilter = current.adultFilter.copy(
                                        allowlistDomains = current.adultFilter.allowlistDomains - domain,
                                    ),
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = allowDraft,
                    onValueChange = { allowDraft = it },
                    label = { Text("Allow a site") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val domain = DomainMatcher.normalise(allowDraft)
                        if (domain != null) {
                            scope.launch {
                                container.settingsRepository.update { current ->
                                    val merged = (current.adultFilter.allowlistDomains + domain).distinct()
                                    current.copy(adultFilter = current.adultFilter.copy(allowlistDomains = merged))
                                }
                            }
                            allowDraft = ""
                        }
                    },
                    enabled = allowDraft.isNotBlank(),
                ) {
                    Text("Allow")
                }
            }

            SectionCard(title = "How it filters") {
                SwitchRow(
                    title = "Use the bundled list",
                    description = "${container.blocklistRepository.bundledCount()} well-known sites.",
                    checked = filter.useBundledList,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(adultFilter = it.adultFilter.copy(useBundledList = value))
                            }
                        }
                    },
                )
                SwitchRow(
                    title = "Close the DNS-over-HTTPS bypass",
                    description = "Stops a browser resolving names behind the filter's back.",
                    checked = filter.blockDnsOverHttpsBypass,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(adultFilter = it.adultFilter.copy(blockDnsOverHttpsBypass = value))
                            }
                        }
                    },
                )
                SwitchRow(
                    title = "Also check the address bar",
                    description = "Catches anything that slips past DNS. Reads only the URL field.",
                    checked = filter.browserUrlGuard,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(adultFilter = it.adultFilter.copy(browserUrlGuard = value))
                            }
                        }
                    },
                )
                SwitchRow(
                    title = "Answer with 0.0.0.0 instead of \"no such name\"",
                    description = "Some browsers show a clearer error this way.",
                    checked = filter.responseMode == BlockResponseMode.NULL_IP,
                    onCheckedChange = { value ->
                        scope.launch {
                            container.settingsRepository.update {
                                it.copy(
                                    adultFilter = it.adultFilter.copy(
                                        responseMode = if (value) BlockResponseMode.NULL_IP else BlockResponseMode.NXDOMAIN,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DomainList(domains: List<String>, emptyLabel: String, onRemove: (String) -> Unit) {
    Column {
        if (domains.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        domains.forEach { domain ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(domain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onRemove(domain) }) { Text("Remove") }
            }
        }
    }
}
