package com.antidoomscroller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.antidoomscroller.AppContainer
import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.blocklist.Ruleset
import com.antidoomscroller.core.detect.ContextTrail
import com.antidoomscroller.core.detect.DefaultSignatures
import com.antidoomscroller.core.detect.MediaRegionResolver
import com.antidoomscroller.core.detect.ScreenRect
import com.antidoomscroller.core.detect.ScreenSnapshot
import com.antidoomscroller.core.detect.SurfaceClassifier
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.messages.MessageBook
import com.antidoomscroller.core.messages.RenderedMessage
import com.antidoomscroller.core.model.AppProfile
import com.antidoomscroller.core.model.BlockStyle
import com.antidoomscroller.core.model.GuardSettings
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.policy.BlockLoopTracker
import com.antidoomscroller.core.policy.GuardDecision
import com.antidoomscroller.core.policy.PolicyResolver
import com.antidoomscroller.core.scroll.ScrollDetector
import com.antidoomscroller.core.scroll.ScrollVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Watches the apps you asked it to watch and removes the short-video feeds you asked it to
 * remove - and only those.
 *
 * Everything it reads stays in this process: a screen is flattened into a snapshot, scored
 * against the signature pack, turned into one allow/block decision, and dropped. Nothing is
 * written to disk, and the app has no way to send it anywhere.
 *
 * The service is bound by the system, so it keeps running in the background with the app closed
 * and is restarted after a reboot.
 */
class ScrollGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val classifier = SurfaceClassifier(DefaultSignatures.pack())
    private val trail = ContextTrail(maxAgeMs = CONTEXT_MAX_AGE_MS)
    private val resolver = PolicyResolver()
    private val scrollDetector = ScrollDetector()
    private val messageBook = MessageBook()

    private lateinit var container: AppContainer
    private lateinit var overlay: BlockOverlay

    @Volatile private var settings: GuardSettings = GuardSettings()
    @Volatile private var ruleset: Ruleset = Ruleset(DomainMatcher.EMPTY, 0, 0, 0)
    @Volatile private var filterUnlocked: Boolean = false

    private var loopTracker = BlockLoopTracker(escapeCount = 4, windowMs = 10_000)
    private var lastEvaluationMs = 0L
    private var lastBackMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = AppContainer.from(this)
        overlay = BlockOverlay(this)

        serviceScope.launch {
            container.settingsRepository.settings.collectLatest { latest ->
                settings = latest
                loopTracker = BlockLoopTracker(
                    escapeCount = latest.enforcement.loopEscapeBlockCount,
                    windowMs = latest.enforcement.loopEscapeWindowMs,
                )
                refreshWatchedPackages(latest)
            }
        }
        serviceScope.launch {
            container.settingsRepository.signatures.collectLatest { classifier.updatePack(it) }
        }
        serviceScope.launch {
            container.blocklistRepository.ruleset.collectLatest { ruleset = it }
        }
        serviceScope.launch {
            container.lockRepository.state.collectLatest { filterUnlocked = it.phase == LockPhase.DISABLED }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(packageName)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> evaluate(packageName, force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> evaluate(packageName, force = false)
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        overlay.dismiss()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    // -- evaluation -----------------------------------------------------------

    private fun evaluate(packageName: String, force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastEvaluationMs < MIN_EVALUATION_INTERVAL_MS) return
        lastEvaluationMs = now

        if (packageName in BrowserGuard.BROWSER_PACKAGES) {
            evaluateBrowser()
            return
        }

        val profile = settings.profileFor(packageName)
        if (!settings.masterEnabled || profile == null || !profile.enabled) {
            leaveGuardedApp()
            return
        }

        val root = rootInActiveWindow ?: return
        val snapshot = SnapshotCollector.collect(root, packageName, now, screenBounds())

        trail.onPackageChanged(packageName)
        val screenTags = classifier.contextTagsFor(snapshot)
        if (screenTags.isNotEmpty()) {
            // The screen says where the user is now; that replaces wherever they were before.
            trail.clear()
            trail.recordAll(screenTags, now)
        }

        val classification = classifier.classify(snapshot, trail.activeTags(now))
        when (val decision = resolver.decide(settings, packageName, classification, LocalDateTime.now())) {
            is GuardDecision.Block -> enforceBlock(decision, profile, snapshot, now)
            is GuardDecision.Allow -> overlay.dismiss()
        }
    }

    private fun enforceBlock(
        decision: GuardDecision.Block,
        profile: AppProfile,
        snapshot: ScreenSnapshot,
        nowMs: Long,
    ) {
        val rendered = messageBook.render(
            settings = settings.messages,
            kind = MessageKind.FEED_BLOCK,
            appLabel = profile.displayName,
            surface = decision.surface,
        )
        if (settings.messages.notifyOnBlock) {
            Notifications.blocked(this, rendered.title, rendered.body)
        }

        when (decision.style) {
            BlockStyle.COVER -> cover(rendered, snapshot)
            BlockStyle.EXIT -> exit(decision, rendered, nowMs)
        }
    }

    /**
     * Hide the video and leave the app alone.
     *
     * The cover is sized to the video's own container, so a reels unit inside the timeline keeps
     * the post header above it readable, and a full-bleed player keeps the app's tab bar tappable.
     * When nothing on screen looks like the video, the content area between the app's own bars is
     * used rather than blacking out the display.
     */
    private fun cover(rendered: RenderedMessage, snapshot: ScreenSnapshot) {
        val screen = screenBounds()
        val signature = classifier.signatureFor(snapshot.packageName)
        val region = MediaRegionResolver.resolve(snapshot, signature, screen)
            ?: MediaRegionResolver.contentArea(snapshot, signature, screen)

        if (region.isEmpty) {
            overlay.dismiss()
            return
        }
        overlay.showPatch(region, rendered.title, rendered.body)
    }

    /** Step back out of the player; if the app keeps re-opening it, leave the app entirely. */
    private fun exit(decision: GuardDecision.Block, rendered: RenderedMessage, nowMs: Long) {
        overlay.showPanel(
            title = rendered.title,
            body = rendered.body,
            actionLabel = "Back to home",
            minimumVisibleMs = settings.enforcement.overlayMinimumMs,
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        if (loopTracker.onBlock("${decision.packageName}/${decision.surface.id}", nowMs)) {
            loopTracker.reset()
            overlay.dismiss()
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        if (nowMs - lastBackMs >= settings.enforcement.backAttemptDelayMs) {
            lastBackMs = nowMs
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun handleScroll(packageName: String) {
        val profile = settings.profileFor(packageName) ?: return
        if (!settings.masterEnabled || !profile.enabled) return

        val verdict = scrollDetector.onScroll(packageName, profile.antiScroll, SystemClock.uptimeMillis())
        if (verdict !is ScrollVerdict.Interrupt) return

        val rendered = messageBook.render(
            settings = settings.messages,
            kind = MessageKind.ANTI_SCROLL,
            appLabel = profile.displayName,
        )
        overlay.flash(
            title = rendered.title,
            body = rendered.body,
            actionLabel = "Keep scrolling",
            timeoutMs = profile.antiScroll.popupTimeoutSeconds * 1_000L,
        )
        Notifications.nudge(this, rendered.title, rendered.body)
    }

    private fun leaveGuardedApp() {
        overlay.dismiss()
        scrollDetector.onLeaveApp()
        loopTracker.reset()
    }

    // -- adult filter, browser layer -----------------------------------------

    private fun evaluateBrowser() {
        val adultFilter = settings.adultFilter
        if (!adultFilter.enabled || !adultFilter.browserUrlGuard || filterUnlocked) {
            overlay.dismiss()
            return
        }

        val root = rootInActiveWindow ?: return
        val host = BrowserGuard.blockedHost(SnapshotCollector.findUrlBarText(root), ruleset.matcher)
        if (host == null) {
            overlay.dismiss()
            return
        }

        val rendered = messageBook.render(settings.messages, MessageKind.ADULT_BLOCK, appLabel = null)
        overlay.showPanel(
            title = rendered.title,
            body = rendered.body,
            actionLabel = "Go back",
            minimumVisibleMs = settings.enforcement.overlayMinimumMs,
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackMs >= settings.enforcement.backAttemptDelayMs) {
            lastBackMs = now
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    // -- helpers --------------------------------------------------------------

    private fun screenBounds(): ScreenRect {
        val metrics = resources.displayMetrics
        return ScreenRect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * Narrows the event stream to the apps that are actually guarded, so the service never even
     * receives events from anything else.
     */
    private fun refreshWatchedPackages(current: GuardSettings) {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        val watched = buildSet {
            addAll(current.guardedPackages)
            if (current.adultFilter.enabled && current.adultFilter.browserUrlGuard) {
                addAll(BrowserGuard.BROWSER_PACKAGES)
            }
        }
        info.packageNames = if (watched.isEmpty()) arrayOf(packageName) else watched.toTypedArray()
        runCatching { serviceInfo = info }
    }

    private companion object {
        const val MIN_EVALUATION_INTERVAL_MS = 250L
        const val CONTEXT_MAX_AGE_MS = 6_000L
    }
}
