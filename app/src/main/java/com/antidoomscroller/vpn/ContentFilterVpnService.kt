package com.antidoomscroller.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.antidoomscroller.AppContainer
import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.dns.DnsFilterEngine
import com.antidoomscroller.core.dns.FilterStats
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * The adult content filter.
 *
 * A local VPN is the only way to filter every browser on an unrooted phone - Chrome, Opera and
 * anything else - because it is the only hook that sits below the browser. The tunnel is
 * deliberately tiny: it claims two addresses of its own and routes *only* those, so the phone's
 * DNS questions pass through this app and nothing else does. No browsing traffic is carried, and
 * blocked names never leave the device.
 *
 * Turning it off is not handled here. That goes through the cooldown in
 * [com.antidoomscroller.data.LockRepository].
 */
class ContentFilterVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = DnsFilterEngine()

    private var descriptor: ParcelFileDescriptor? = null
    private var tunnel: DnsTunnel? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.from(this)

        scope.launch {
            container.blocklistRepository.ruleset.collectLatest { engine.matcher = it.matcher }
        }
        scope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                engine.responseMode = settings.adultFilter.responseMode
                if (!settings.adultFilter.enabled) stopFilter()
            }
        }
        scope.launch {
            container.lockRepository.state.collectLatest { lock ->
                // The filter runs while it is armed, and while a disable request is still waiting.
                engine.enabled = lock.phase != LockPhase.DISABLED
                if (lock.phase == LockPhase.DISABLED) stopFilter()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter()
            return Service.START_NOT_STICKY
        }
        startInForeground()
        if (tunnel == null) startTunnel()
        // Restart if the system ever kills us: the filter is supposed to outlive the app.
        return Service.START_STICKY
    }

    override fun onRevoke() {
        // The user replaced this VPN with another one, or revoked permission in system settings.
        stopFilter()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopFilter()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = Notifications.filterRunning(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.FILTER_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.FILTER_ID, notification)
        }
    }

    private fun startTunnel() {
        val container = AppContainer.from(this)
        val settings = container.settingsRepository.settings.value
        val resolvers = UpstreamResolvers.discover(this, settings.adultFilter.upstreamDns)

        val upstreamByFake = LinkedHashMap<String, InetAddress>()
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .setBlocking(true)
            .addAddress(TUN_V4, TUN_V4_PREFIX)

        resolvers.v4.take(FAKE_V4.size).forEachIndexed { index, real ->
            val fake = FAKE_V4[index]
            builder.addDnsServer(fake).addRoute(fake, 32)
            upstreamByFake[fake] = real
        }

        if (resolvers.v6.isNotEmpty()) {
            runCatching {
                builder.addAddress(TUN_V6, TUN_V6_PREFIX)
                resolvers.v6.take(FAKE_V6.size).forEachIndexed { index, real ->
                    val fake = FAKE_V6[index]
                    builder.addDnsServer(fake).addRoute(fake, 128)
                    upstreamByFake[normaliseV6(fake)] = real
                }
            }
        }

        // Never filter ourselves; the app makes no requests, but this keeps the loop impossible.
        runCatching { builder.addDisallowedApplication(packageName) }

        val established = runCatching { builder.establish() }.getOrNull()
        if (established == null || upstreamByFake.isEmpty()) {
            status.value = FilterStatus(running = false, error = "Could not establish the filter tunnel")
            stopSelf()
            return
        }

        descriptor = established
        val dnsTunnel = DnsTunnel(
            service = this,
            descriptor = established,
            engine = engine,
            upstreamByFakeAddress = upstreamByFake,
            onError = { error -> status.value = status.value.copy(error = error.message) },
        )
        tunnel = dnsTunnel
        worker = Thread(dnsTunnel, "dns-tunnel").apply {
            isDaemon = true
            start()
        }
        status.value = FilterStatus(running = true, upstreamCount = upstreamByFake.size)

        scope.launch {
            while (tunnel != null) {
                status.value = status.value.copy(stats = engine.stats)
                kotlinx.coroutines.delay(STATUS_REFRESH_MS)
            }
        }
    }

    private fun stopFilter() {
        tunnel?.stop()
        tunnel = null
        worker = null
        runCatching { descriptor?.close() }
        descriptor = null
        status.value = FilterStatus(running = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** IPv6 literals have several spellings; key the map the same way [InetAddress] reports them. */
    private fun normaliseV6(address: String): String =
        runCatching { InetAddress.getByName(address).hostAddress }.getOrNull() ?: address

    data class FilterStatus(
        val running: Boolean = false,
        val upstreamCount: Int = 0,
        val stats: FilterStats = FilterStats(),
        val error: String? = null,
    )

    companion object {
        private const val ACTION_STOP = "com.antidoomscroller.action.STOP_FILTER"
        private const val SESSION_NAME = "AntiDoomScroller filter"
        private const val MTU = 1500
        private const val STATUS_REFRESH_MS = 2_000L

        private const val TUN_V4 = "10.115.44.1"
        private const val TUN_V4_PREFIX = 24
        private const val TUN_V6 = "fd00:adb1:5c01::1"
        private const val TUN_V6_PREFIX = 64
        private val FAKE_V4 = listOf("10.115.44.2", "10.115.44.3")
        private val FAKE_V6 = listOf("fd00:adb1:5c01::2", "fd00:adb1:5c01::3")

        /** Live status for the app's own screens. Nothing here is persisted. */
        private val statusState = MutableStateFlow(FilterStatus())
        internal val status: MutableStateFlow<FilterStatus> get() = statusState
        val observableStatus: StateFlow<FilterStatus> get() = statusState

        /** Returns the consent intent to show, or null when permission is already granted. */
        fun consentIntent(context: Context): Intent? = prepare(context)

        fun start(context: Context) {
            val intent = Intent(context, ContentFilterVpnService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ContentFilterVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Rebuilds a matcher for callers that need one before the service is up. */
        fun emptyMatcher(): DomainMatcher = DomainMatcher.EMPTY
    }
}
