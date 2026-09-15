package com.antidoomscroller.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the guard should do when a surface is recognised. */
@Serializable
enum class RuleAction {
    @SerialName("block") BLOCK,
    @SerialName("allow") ALLOW,
    ;

    fun toggled(): RuleAction = if (this == BLOCK) ALLOW else BLOCK
}

/** How a blocked feed is taken away. */
@Serializable
enum class BlockStyle {
    /**
     * Hide it behind an opaque panel and leave the app exactly where it is. The rest of the app
     * keeps working; on a feed with a reels unit embedded in it, only that unit is covered.
     */
    @SerialName("cover") COVER,

    /** Step back out of the player, returning to whatever tab the user was on. */
    @SerialName("exit") EXIT,
}

/** Per-app configuration. Surface actions are keyed by [FeedSurface.id] so unknown keys survive upgrades. */
@Serializable
data class AppProfile(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val surfaceActions: Map<String, RuleAction> = emptyMap(),
    val blockStyle: BlockStyle = BlockStyle.COVER,
    /**
     * Whether allowing reels in DMs covers only the video that was sent.
     *
     * On: the reel a friend sent plays, and swiping to the next one is the feed again. Off: the
     * whole visit to the player stays allowed, however far it is scrolled.
     */
    val dmAllowanceEndsOnSwipe: Boolean = true,
    val antiScroll: AntiScrollSettings = AntiScrollSettings(),
) {
    fun actionFor(surface: FeedSurface): RuleAction =
        surfaceActions[surface.id] ?: DefaultProfiles.fallbackAction(packageName, surface)

    fun withAction(surface: FeedSurface, action: RuleAction): AppProfile =
        copy(surfaceActions = surfaceActions + (surface.id to action))
}

@Serializable
enum class Sensitivity {
    @SerialName("gentle") GENTLE,
    @SerialName("balanced") BALANCED,
    @SerialName("strict") STRICT,
    @SerialName("custom") CUSTOM,
}

/**
 * Anti-scroll mode: notices when scrolling has stopped being intentional and interrupts.
 *
 * [scrollThreshold] scroll events inside [windowSeconds] triggers a reminder, then the detector
 * stays quiet for [cooldownSeconds] so it never becomes wallpaper.
 */
@Serializable
data class AntiScrollSettings(
    val enabled: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val customScrollThreshold: Int = 40,
    val customWindowSeconds: Int = 60,
    val popupTimeoutSeconds: Int = 8,
    val cooldownSeconds: Int = 180,
) {
    val scrollThreshold: Int
        get() = when (sensitivity) {
            Sensitivity.GENTLE -> 80
            Sensitivity.BALANCED -> 45
            Sensitivity.STRICT -> 20
            Sensitivity.CUSTOM -> customScrollThreshold.coerceAtLeast(3)
        }

    val windowSeconds: Int
        get() = when (sensitivity) {
            Sensitivity.GENTLE -> 120
            Sensitivity.BALANCED -> 90
            Sensitivity.STRICT -> 60
            Sensitivity.CUSTOM -> customWindowSeconds.coerceAtLeast(5)
        }
}

@Serializable
enum class MessageRotation {
    @SerialName("random") RANDOM,
    @SerialName("sequential") SEQUENTIAL,
    @SerialName("first") FIRST,
}

/** Which situation a message is shown for. */
@Serializable
enum class MessageKind {
    @SerialName("feed_block") FEED_BLOCK,
    @SerialName("anti_scroll") ANTI_SCROLL,
    @SerialName("adult_block") ADULT_BLOCK,
}

/** Fully user-authored copy for every interruption the app can produce. */
@Serializable
data class MessageSettings(
    val feedBlockMessages: List<String> = DefaultMessages.FEED_BLOCK,
    val antiScrollMessages: List<String> = DefaultMessages.ANTI_SCROLL,
    val adultBlockMessages: List<String> = DefaultMessages.ADULT_BLOCK,
    val rotation: MessageRotation = MessageRotation.RANDOM,
    val showAppName: Boolean = true,
    val showBlockedSurface: Boolean = true,
    val notifyOnBlock: Boolean = false,
) {
    fun messagesFor(kind: MessageKind): List<String> = when (kind) {
        MessageKind.FEED_BLOCK -> feedBlockMessages
        MessageKind.ANTI_SCROLL -> antiScrollMessages
        MessageKind.ADULT_BLOCK -> adultBlockMessages
    }

    fun withMessages(kind: MessageKind, messages: List<String>): MessageSettings = when (kind) {
        MessageKind.FEED_BLOCK -> copy(feedBlockMessages = messages)
        MessageKind.ANTI_SCROLL -> copy(antiScrollMessages = messages)
        MessageKind.ADULT_BLOCK -> copy(adultBlockMessages = messages)
    }
}

object DefaultMessages {
    val FEED_BLOCK = listOf(
        "Not today. You closed this for a reason.",
        "This is the part you said you did not want.",
        "The feed is off. What were you actually here for?",
    )
    val ANTI_SCROLL = listOf(
        "You have been scrolling a while. Still enjoying this?",
        "Checking in: is this how you wanted to spend the last few minutes?",
        "Long scroll detected. Put it down?",
    )
    val ADULT_BLOCK = listOf(
        "Blocked. This was your decision, made earlier, on purpose.",
        "Filter is on. Turning it off takes 48 hours.",
    )
}

/** A window of time where feed blocking pauses on purpose (a Sunday-evening allowance, say). */
@Serializable
data class BreakWindow(
    val id: String,
    val label: String = "Break",
    val enabled: Boolean = true,
    /** ISO day numbers, Monday = 1 through Sunday = 7. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinuteOfDay: Int = 19 * 60,
    val endMinuteOfDay: Int = 20 * 60,
    /** Empty means every guarded app. */
    val packages: Set<String> = emptySet(),
) {
    /** True when the window wraps past midnight (22:00 -> 00:30). */
    val wrapsMidnight: Boolean get() = endMinuteOfDay <= startMinuteOfDay
}

/**
 * The rationed escape hatch: one run of unblocked scrolling, then a long wait.
 *
 * Separate from [ScheduleSettings] because a scheduled break is a decision made in advance and
 * this one is made in the moment - which is exactly why it costs a day.
 */
@Serializable
data class ScrollPassSettings(
    val enabled: Boolean = true,
    val durationMinutes: Int = 15,
    val cooldownHours: Int = 24,
)

@Serializable
data class ScheduleSettings(
    val enabled: Boolean = false,
    val windows: List<BreakWindow> = emptyList(),
)

/** Strict mode: a local password gate in front of loosening any setting. */
@Serializable
data class StrictModeSettings(
    val enabled: Boolean = false,
    val passwordHash: String? = null,
    val requireForSettingsChanges: Boolean = true,
    /** Extra delay before a loosening change applies, even with the password. */
    val loosenDelayMinutes: Int = 0,
)

@Serializable
enum class BlockResponseMode {
    /** Answer "this name does not exist" - fastest failure in every browser. */
    @SerialName("nxdomain") NXDOMAIN,

    /** Answer 0.0.0.0 / :: - some browsers show a clearer error page. */
    @SerialName("null_ip") NULL_IP,
}

@Serializable
data class ChallengeSettings(
    val problemCount: Int = 3,
    val difficulty: ChallengeDifficulty = ChallengeDifficulty.HARD,
    val secondsPerProblem: Int = 120,
    val penaltyMinutesOnWrongAnswer: Int = 15,
)

@Serializable
enum class ChallengeDifficulty {
    @SerialName("medium") MEDIUM,
    @SerialName("hard") HARD,
    @SerialName("brutal") BRUTAL,
}

/**
 * Adult content filter. Runs as an on-device DNS filter (every browser, no root) plus an
 * accessibility URL guard that catches browsers using DNS-over-HTTPS.
 */
@Serializable
data class AdultFilterSettings(
    val enabled: Boolean = false,
    val useBundledList: Boolean = true,
    val customBlockedDomains: List<String> = emptyList(),
    val allowlistDomains: List<String> = emptyList(),
    val blockDnsOverHttpsBypass: Boolean = true,
    val browserUrlGuard: Boolean = true,
    val responseMode: BlockResponseMode = BlockResponseMode.NXDOMAIN,
    /** Empty means "use whatever resolver the network already handed the phone". */
    val upstreamDns: List<String> = emptyList(),
    val disableCooldownHours: Int = 48,
    val challenge: ChallengeSettings = ChallengeSettings(),
)

@Serializable
data class EnforcementSettings(
    /**
     * Back presses to try before giving up and simply keeping the video covered.
     *
     * Giving up never means closing the app. Backing out of a short-video tab should land on the
     * tab the user came from; if it does not, covering the video is the right answer, because
     * everything else in the app is theirs to use.
     */
    val backAttempts: Int = 3,

    /**
     * How long to let a back press take effect before trying another.
     *
     * Screens are re-examined several times a second, so a short delay here would fire a burst of
     * back presses at one screen and walk the user out of the app.
     */
    val backAttemptDelayMs: Long = 1_200,

    /** How long the block card stays up before it can be dismissed. */
    val overlayMinimumMs: Long = 1_200,

    /** The window over which [backAttempts] is counted. */
    val backAttemptWindowMs: Long = 15_000,
)

/** The single persisted settings object. Everything the user can configure lives here. */
@Serializable
data class GuardSettings(
    val schemaVersion: Int = 1,
    val masterEnabled: Boolean = true,
    val profiles: List<AppProfile> = DefaultProfiles.all(),
    val messages: MessageSettings = MessageSettings(),
    val schedule: ScheduleSettings = ScheduleSettings(),
    val scrollPass: ScrollPassSettings = ScrollPassSettings(),
    val strictMode: StrictModeSettings = StrictModeSettings(),
    val adultFilter: AdultFilterSettings = AdultFilterSettings(),
    val enforcement: EnforcementSettings = EnforcementSettings(),
) {
    fun profileFor(packageName: String): AppProfile? = profiles.firstOrNull { it.packageName == packageName }

    fun withProfile(profile: AppProfile): GuardSettings {
        val replaced = profiles.map { if (it.packageName == profile.packageName) profile else it }
        val exists = profiles.any { it.packageName == profile.packageName }
        return copy(profiles = if (exists) replaced else replaced + profile)
    }

    val guardedPackages: Set<String>
        get() = profiles.filter { it.enabled }.map { it.packageName }.toSet()
}
