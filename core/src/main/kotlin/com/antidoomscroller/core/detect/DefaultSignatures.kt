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
            // Matched by widget class rather than id: whatever the container is called this
            // release, the thing actually playing is a video surface, and that is what should be
            // covered. See SnapshotCollector.
            "class:textureview",
            "class:surfaceview",
            "class:videoview",
            "clips_viewer",
            "reels_viewer",
        ),
        bottomChromeViewIds = listOf("tab_bar"),
        topChromeViewIds = listOf("action_bar_container", "action_bar"),
        contextViewIds = mapOf(
            ContextTag.DM to listOf("direct_thread", "direct_inbox", "message_list", "direct_fragment_container"),
            ContextTag.EXPLORE to listOf(
                "explore",
                "discover",
                "search_tab",
                "search_result",
                "grid_recycler",
            ),
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
            // Opened from the explore grid. The origin is already known from the context, so a
            // video filling the screen is enough - no dependence on a container id that may not
            // survive the next release. Stories are not reachable from Explore, so the
            // full-screen marker cannot swallow them here.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_EXPLORE.id,
                weight = 130,
                anyViewId = listOf(
                    "clips_viewer",
                    "clips_video_container",
                    "reels_viewer",
                    "video:fullscreen",
                ),
                requireContext = listOf(ContextTag.EXPLORE),
                forbidContext = listOf(ContextTag.DM),
            ),
            // The Reels tab itself.
            //
            // Matched on the player's own container ids, never on content descriptions. Every
            // reel *thumbnail* in the app is described as "Reel by <name>" - on a profile grid,
            // on Explore, in the timeline - so matching that text classified those screens as the
            // Reels feed and took them away. A grid of reels is not a reel feed.
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
                // A profile is a profile even if every post on it is a reel.
                noneViewId = listOf("profile_header"),
                forbidContext = listOf(ContextTag.DM),
            ),
            // A reel playing inside the main timeline.
            //
            // "Reel by <name>" is the one marker that survives every rename, but on its own it
            // also matches a profile grid and Explore, where the same text labels a static
            // thumbnail. So it is used here and fenced off instead: not on a profile, not on
            // Explore, and not while the full-screen player is up. What is left is a reel
            // playing in the feed, which is precisely the thing this surface is about.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 110,
                anyViewId = listOf("clips_netego", "reels_tray", "clips_unit", "netego_carousel"),
                anyContentDescription = listOf("reel by", "reel video", "audio by"),
                noneViewId = listOf(
                    "profile_header",
                    "profile_grid",
                    "explore_grid",
                    "explore_recycler",
                    "discovery_recycler",
                    "clips_viewer",
                    "clips_video_container",
                ),
                forbidContext = listOf(ContextTag.DM, ContextTag.EXPLORE, ContextTag.PROFILE),
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
            // The Shorts player: vertically paged reel_recycler is the stable marker across
            // versions.
            //
            // Deliberately no content-description matching anywhere in YouTube. The bottom
            // navigation carries a tab described as "Shorts" on *every* screen in the app,
            // including the long-form watch page, so matching that description would mean the
            // whole app reads as a short-video feed. An id that is missing costs a feed that is
            // not blocked; an id that matches everything costs the user their app.
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
            ),
            // The Shorts shelf on the home tab: the shelf container, never the player.
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 120,
                anyViewId = listOf("shorts_shelf", "reel_shelf"),
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
                noneViewId = listOf("profile_header"),
                forbidContext = listOf(ContextTag.DM),
            ),
            SurfaceSignature(
                surface = FeedSurface.SHORT_VIDEO_IN_HOME.id,
                weight = 110,
                allViewId = listOf("feed_recycler_view"),
                // A unit id, not the word "Reels": the navigation bar carries that everywhere.
                anyViewId = listOf("reels_tray", "reels_unit", "reels_carousel"),
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
