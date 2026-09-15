package com.antidoomscroller.core.detect

import com.antidoomscroller.core.model.ContextTag
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.SupportedApps

/**
 * The signatures the app ships with.
 *
 * Host apps rename view ids between releases, so these are deliberately fragments (`clips_viewer`
 * matches `clips_viewer_root`, `clips_viewer_video_container`, ...) and every surface has a
 * content-description fallback. The pack can be exported, edited and re-imported from the app's
 * Detection screen without shipping a new build.
 */
object DefaultSignatures {

    fun pack(): SignaturePack = SignaturePack(
        version = 1,
        apps = listOf(instagram(), youtube(), tiktok(), facebook(), snapchat(), reddit(), linkedin()),
    )

    private fun instagram() = AppSignature(
        packageName = SupportedApps.INSTAGRAM,
        displayName = "Instagram",
        // Most specific first: the video's own container beats the list it sits in, which is
        // what keeps the cover off the post header above a reels unit.
        mediaViewIds = listOf(
            "clips_video_container",
            "clips_viewer_video_container",
            "clips_viewer_media_container",
            "clips_netego",
            "netego_carousel",
            "reels_tray",
            "media_container",
            "video_container",
            "clips_viewer",
            "reels_viewer",
        ),
        bottomChromeViewIds = listOf("tab_bar"),
        topChromeViewIds = listOf("action_bar_container", "action_bar"),
        contextViewIds = mapOf(
            ContextTag.DM to listOf("direct_thread", "direct_inbox", "message_list", "direct_fragment_container"),
            ContextTag.EXPLORE to listOf("explore_grid", "explore_recycler", "discovery_recycler", "search_tab"),
            ContextTag.HOME to listOf("feed_recycler_view", "main_feed_recycler_view", "feed_tab"),
            ContextTag.PROFILE to listOf("profile_tab", "profile_header"),
            ContextTag.SEARCH to listOf("search_edit_text", "action_bar_search_edit_text"),
        ),
        surfaces = listOf(
            // The Reels tab keeps the bottom navigation bar on screen; a reel pushed from a DM
            // thread does not. That holds even when the user came straight from the inbox, so it
            // is scored above every context-based rule.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 160,
                allViewId = listOf("clips_viewer"),
                anyViewId = listOf("clips_tab", "tab_bar", "tab_bar_shadow"),
            ),
            // A reel opened out of a DM thread: same player, no tab bar, different origin.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_DM.id,
                weight = 140,
                anyViewId = listOf("clips_viewer", "clips_video_container", "reels_viewer"),
                noneViewId = listOf("clips_tab", "tab_bar"),
                requireContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_EXPLORE.id,
                weight = 130,
                anyViewId = listOf("clips_viewer", "clips_video_container", "reels_viewer"),
                requireContext = listOf(ContextTag.EXPLORE),
                forbidContext = listOf(ContextTag.DM),
            ),
            // The Reels tab itself.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf(
                    "clips_viewer",
                    "clips_video_container",
                    "clips_swipe_refresh_container",
                    "reels_viewer",
                    "clips_tab_container",
                ),
                anyContentDescription = listOf("reels tab", "reel by", "reels video"),
                forbidContext = listOf(ContextTag.DM),
            ),
            // Reels unit embedded in the main timeline.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 110,
                allViewId = listOf("feed_recycler_view"),
                anyViewId = listOf("clips_netego", "reels_tray", "clips_unit", "netego_carousel"),
            ),
            SurfaceSignature(
                surface = FeedSurface.STORIES.id,
                weight = 100,
                anyViewId = listOf("reel_viewer_root", "reel_viewer_texture_view", "story_viewer", "reel_viewer_media"),
                forbidContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.EXPLORE.id,
                weight = 90,
                anyViewId = listOf("explore_grid", "explore_recycler", "discovery_recycler_view"),
            ),
            SurfaceSignature(
                surface = FeedSurface.HOME_FEED.id,
                weight = 80,
                anyViewId = listOf("feed_recycler_view", "main_feed_recycler_view"),
            ),
        ),
    )

    private fun youtube() = AppSignature(
        packageName = SupportedApps.YOUTUBE,
        displayName = "YouTube",
        mediaViewIds = listOf(
            "shorts_video_container",
            "reel_player_page_container",
            "reel_watch_fragment_root_view",
            "shorts_shelf",
            "rich_shelf",
            "reel_recycler",
        ),
        bottomChromeViewIds = listOf("pivot_bar"),
        topChromeViewIds = listOf("app_bar", "toolbar"),
        contextViewIds = mapOf(
            ContextTag.SEARCH to listOf("search_edit_text", "search_box", "voice_search"),
            ContextTag.HOME to listOf("browse_fragment", "results", "pivot_bar"),
        ),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_EXPLORE.id,
                weight = 140,
                anyViewId = listOf("reel_recycler", "reel_player_page_container", "reel_watch_fragment_root_view"),
                requireContext = listOf(ContextTag.SEARCH),
            ),
            // The Shorts player: vertically paged reel_recycler is the stable marker across versions.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 130,
                anyViewId = listOf(
                    "reel_recycler",
                    "reel_player_page_container",
                    "reel_watch_fragment_root_view",
                    "reel_progress_bar",
                    "shorts_video_container",
                    "shorts_player",
                ),
                anyContentDescription = listOf("shorts", "short video"),
            ),
            // The Shorts shelf on the home tab.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 120,
                anyContentDescription = listOf("shorts shelf", "shorts"),
                anyViewId = listOf("rich_shelf", "shelf_header", "shorts_shelf"),
                noneViewId = listOf("reel_recycler", "reel_player_page_container"),
            ),
            SurfaceSignature(
                surface = FeedSurface.HOME_FEED.id,
                weight = 70,
                anyViewId = listOf("browse_fragment", "results"),
                noneViewId = listOf("reel_recycler", "reel_player_page_container"),
            ),
        ),
    )

    private fun tiktok() = AppSignature(
        packageName = SupportedApps.TIKTOK,
        displayName = "TikTok",
        mediaViewIds = listOf("vs_video", "video_container", "feed_view_pager"),
        bottomChromeViewIds = listOf("main_bottom_tab"),
        contextViewIds = mapOf(
            ContextTag.DM to listOf("chat_room", "im_chat", "message_list"),
        ),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_DM.id,
                weight = 140,
                anyViewId = listOf("video_container", "feed_view_pager", "vs_video"),
                requireContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf("feed_view_pager", "video_container", "vs_video", "main_bottom_tab"),
                anyContentDescription = listOf("for you", "following feed"),
                forbidContext = listOf(ContextTag.DM),
            ),
        ),
    )

    private fun facebook() = AppSignature(
        packageName = SupportedApps.FACEBOOK,
        displayName = "Facebook",
        mediaViewIds = listOf("reels_video", "video_player_view_pager", "reels_viewer"),
        bottomChromeViewIds = listOf("tab_bar", "bottom_navigation"),
        contextViewIds = mapOf(ContextTag.DM to listOf("thread_view", "message_list")),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_DM.id,
                weight = 140,
                anyViewId = listOf("reels_viewer", "reels_video"),
                requireContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf("reels_viewer", "reels_video", "video_player_view_pager"),
                anyContentDescription = listOf("reels"),
                forbidContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 110,
                allViewId = listOf("feed_recycler_view"),
                anyContentDescription = listOf("reels"),
            ),
        ),
    )

    private fun snapchat() = AppSignature(
        packageName = SupportedApps.SNAPCHAT,
        displayName = "Snapchat",
        mediaViewIds = listOf("spotlight", "discover_feed"),
        contextViewIds = mapOf(ContextTag.DM to listOf("chat_input_bar", "conversation")),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf("spotlight", "discover_feed"),
                anyContentDescription = listOf("spotlight"),
                forbidContext = listOf(ContextTag.DM),
            ),
        ),
    )

    private fun reddit() = AppSignature(
        packageName = SupportedApps.REDDIT,
        displayName = "Reddit",
        mediaViewIds = listOf("video_feed", "reels_container"),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf("video_feed", "reels_container"),
                anyContentDescription = listOf("watch feed"),
            ),
        ),
    )

    private fun linkedin() = AppSignature(
        packageName = SupportedApps.LINKEDIN,
        displayName = "LinkedIn",
        mediaViewIds = listOf("video_viewer", "feed_video_viewpager"),
        surfaces = listOf(
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_FEED.id,
                weight = 120,
                anyViewId = listOf("video_viewer", "feed_video_viewpager"),
                anyContentDescription = listOf("video feed"),
            ),
        ),
    )
}
