package com.antidoomscroller.core.model

/**
 * A distinct, user-meaningful screen inside a host app.
 *
 * Surfaces are deliberately app-agnostic: Instagram's Reels tab and YouTube's Shorts player are
 * both [SHORT_VIDEO_FEED], so one rule engine covers every supported app.
 */
enum class FeedSurface(val id: String, val label: String, val description: String) {
    SHORT_VIDEO_FEED(
        id = "short_video_feed",
        label = "Reels / Shorts feed",
        description = "The full-screen, infinitely swipeable short video player.",
    ),
    SHORT_VIDEO_IN_DM(
        id = "short_video_in_dm",
        label = "Reels sent in DMs",
        description = "A short video opened from a direct message thread.",
    ),
    SHORT_VIDEO_IN_HOME(
        id = "short_video_in_home",
        label = "Reels inside the home feed",
        description = "Short video units embedded in the main timeline.",
    ),
    SHORT_VIDEO_IN_EXPLORE(
        id = "short_video_in_explore",
        label = "Reels opened from Explore",
        description = "A short video opened from the explore grid or search results.",
    ),
    HOME_FEED(
        id = "home_feed",
        label = "Home feed",
        description = "The main scrolling timeline.",
    ),
    EXPLORE(
        id = "explore",
        label = "Explore / Discover",
        description = "The algorithmic discovery grid.",
    ),
    STORIES(
        id = "stories",
        label = "Stories",
        description = "The tap-through stories viewer.",
    ),
    UNKNOWN(
        id = "unknown",
        label = "Unrecognised screen",
        description = "No signature matched this screen.",
    ),
    ;

    /**
     * Whether the app is allowed to take this screen away at all.
     *
     * The short-video feeds, plus stories, which are the one non-video surface with a switch of
     * its own. Everything else - messages, posts, search, profiles, long-form video - is
     * recognised only so the guard knows where the user is, and cannot be blocked whatever the
     * settings say.
     */
    val isBlockable: Boolean
        get() = isShortVideo || this == STORIES

    /** Whether this surface is one of the short-video feeds. */
    val isShortVideo: Boolean
        get() = this == SHORT_VIDEO_FEED ||
            this == SHORT_VIDEO_IN_DM ||
            this == SHORT_VIDEO_IN_HOME ||
            this == SHORT_VIDEO_IN_EXPLORE

    companion object {
        fun fromId(id: String): FeedSurface? = entries.firstOrNull { it.id == id }
    }
}

/** Context tags recorded as the user moves around an app; used to disambiguate where a reel came from. */
object ContextTag {
    const val DM = "dm"
    const val EXPLORE = "explore"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
}
