package com.antidoomscroller.core.model

/** Supported host apps and the out-of-the-box rules for each. */
object SupportedApps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val FACEBOOK = "com.facebook.katana"
    const val SNAPCHAT = "com.snapchat.android"
    const val REDDIT = "com.reddit.frontpage"
    const val LINKEDIN = "com.linkedin.android"
}

object DefaultProfiles {

    /**
     * Short-video feeds go; everything else in the app is untouched.
     *
     * DMs, stories, posts, search, notifications and long-form video are never blocked - the
     * policy layer cannot block them even if a setting said otherwise. The only switches are
     * about *where a reel or short is allowed to play*, and reels friends send in DMs are on by
     * default.
     */
    fun all(): List<AppProfile> = listOf(
        AppProfile(
            packageName = SupportedApps.INSTAGRAM,
            displayName = "Instagram",
            // Instagram keeps you in place: the reel is covered, the app carries on working.
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(
                FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_HOME.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_EXPLORE.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_DM.id to RuleAction.ALLOW,
            ),
        ),
        AppProfile(
            packageName = SupportedApps.YOUTUBE,
            displayName = "YouTube",
            // Shorts is a whole tab, so backing out of it lands you on the home tab.
            blockStyle = BlockStyle.EXIT,
            surfaceActions = mapOf(
                FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_HOME.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_EXPLORE.id to RuleAction.BLOCK,
            ),
        ),
        AppProfile(
            packageName = SupportedApps.TIKTOK,
            displayName = "TikTok",
            enabled = false,
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(
                FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_HOME.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_EXPLORE.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_DM.id to RuleAction.ALLOW,
            ),
        ),
        AppProfile(
            packageName = SupportedApps.FACEBOOK,
            displayName = "Facebook",
            enabled = false,
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(
                FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_HOME.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_DM.id to RuleAction.ALLOW,
            ),
        ),
        AppProfile(
            packageName = SupportedApps.SNAPCHAT,
            displayName = "Snapchat",
            enabled = false,
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(
                FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK,
                FeedSurface.SHORT_VIDEO_IN_DM.id to RuleAction.ALLOW,
            ),
        ),
        AppProfile(
            packageName = SupportedApps.REDDIT,
            displayName = "Reddit",
            enabled = false,
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK),
        ),
        AppProfile(
            packageName = SupportedApps.LINKEDIN,
            displayName = "LinkedIn",
            enabled = false,
            blockStyle = BlockStyle.COVER,
            surfaceActions = mapOf(FeedSurface.SHORT_VIDEO_FEED.id to RuleAction.BLOCK),
        ),
    )

    /**
     * Used when a profile has no explicit entry for a surface. Short video defaults to blocked;
     * anything that is not short video is always allowed.
     */
    fun fallbackAction(@Suppress("UNUSED_PARAMETER") packageName: String, surface: FeedSurface): RuleAction =
        when (surface) {
            FeedSurface.SHORT_VIDEO_FEED,
            FeedSurface.SHORT_VIDEO_IN_HOME,
            FeedSurface.SHORT_VIDEO_IN_EXPLORE,
            -> RuleAction.BLOCK

            FeedSurface.SHORT_VIDEO_IN_DM,
            FeedSurface.HOME_FEED,
            FeedSurface.EXPLORE,
            FeedSurface.STORIES,
            FeedSurface.UNKNOWN,
            -> RuleAction.ALLOW
        }

    /**
     * The switches shown for an app, in UI order.
     *
     * Every one of them is about short video. There is deliberately no switch for "block the home
     * feed" or "block DMs" - those screens are not this app's business.
     */
    fun configurableSurfaces(packageName: String): List<FeedSurface> = when (packageName) {
        SupportedApps.INSTAGRAM -> listOf(
            FeedSurface.SHORT_VIDEO_FEED,
            FeedSurface.SHORT_VIDEO_IN_HOME,
            FeedSurface.SHORT_VIDEO_IN_EXPLORE,
            FeedSurface.SHORT_VIDEO_IN_DM,
        )

        SupportedApps.YOUTUBE -> listOf(
            FeedSurface.SHORT_VIDEO_FEED,
            FeedSurface.SHORT_VIDEO_IN_HOME,
            FeedSurface.SHORT_VIDEO_IN_EXPLORE,
        )

        else -> listOf(
            FeedSurface.SHORT_VIDEO_FEED,
            FeedSurface.SHORT_VIDEO_IN_HOME,
            FeedSurface.SHORT_VIDEO_IN_DM,
        )
    }
}
