package com.antidoomscroller.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.lifecycleScope
import com.antidoomscroller.AppContainer
import com.antidoomscroller.service.ScrollGuardAccessibilityService
import com.antidoomscroller.ui.theme.AntiDoomScrollerTheme
import com.antidoomscroller.vpn.ContentFilterVpnService
import kotlinx.coroutines.launch

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            ContentFilterVpnService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.from(this)

        // Opening the app is itself a checkpoint: it keeps the cooldown honest without a service.
        lifecycleScope.launch { container.lockRepository.checkpoint() }

        setContent {
            AntiDoomScrollerTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { AppContainer.from(this@MainActivity).lockRepository.checkpoint() }
    }

    /** Asks for VPN permission if needed, then brings the filter up. */
    fun enableContentFilter() {
        val consent = ContentFilterVpnService.consentIntent(this)
        if (consent != null) vpnConsent.launch(consent) else ContentFilterVpnService.start(this)
    }

    fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openBatterySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${ScrollGuardAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
