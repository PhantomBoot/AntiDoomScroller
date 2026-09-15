package com.antidoomscroller.core

import com.antidoomscroller.core.detect.ContextTrail
import com.antidoomscroller.core.detect.DefaultSignatures
import com.antidoomscroller.core.detect.ScreenSnapshot
import com.antidoomscroller.core.detect.SignaturePack
import com.antidoomscroller.core.detect.SurfaceClassifier
import com.antidoomscroller.core.model.ContextTag
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.SupportedApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionTest {

    private val classifier = SurfaceClassifier(DefaultSignatures.pack())

    private fun instagram(vararg ids: String) =
        ScreenSnapshot(SupportedApps.INSTAGRAM, viewIds = ids.toSet())

    private fun instagramScreen(ids: Set<String>, descriptions: Set<String>) =
        ScreenSnapshot(SupportedApps.INSTAGRAM, viewIds = ids, contentDescriptions = descriptions)

    private fun youtube(ids: Set<String> = emptySet(), descriptions: Set<String> = emptySet()) =
        ScreenSnapshot(SupportedApps.YOUTUBE, viewIds = ids, contentDescriptions = descriptions)

    @Test
    fun `reels tab is the short video feed`() {
        val result = classifier.classify(instagram("clips_viewer_root", "clips_viewer_video_container"), emptySet())
        assertEquals(FeedSurface.SHORT_VIDEO_FEED, result.surface)
        assertTrue(result.evidence.isNotEmpty())
    }

    @Test
    fun `the same player opened from a dm is a different surface`() {
        val result = classifier.classify(instagram("clips_viewer_root"), setOf(ContextTag.DM))
        assertEquals(FeedSurface.SHORT_VIDEO_IN_DM, result.surface)
    }

    @Test
    fun `the same player opened from explore is a different surface`() {
        val result = classifier.classify(instagram("clips_viewer_root"), setOf(ContextTag.EXPLORE))
        assertEquals(FeedSurface.SHORT_VIDEO_IN_EXPLORE, result.surface)
    }

    @Test
    fun `the reels tab is still the reels tab right after leaving a dm`() {
        // The bottom tab bar is on screen, so the stale DM context must not unlock it.
        val result = classifier.classify(
            instagram("clips_viewer_root", "clips_tab", "tab_bar"),
            setOf(ContextTag.DM),
        )
        assertEquals(FeedSurface.SHORT_VIDEO_FEED, result.surface)
    }

    @Test
    fun `explore grid and home feed are recognised separately`() {
        assertEquals(FeedSurface.EXPLORE, classifier.classify(instagram("explore_grid"), emptySet()).surface)
        assertEquals(FeedSurface.HOME_FEED, classifier.classify(instagram("feed_recycler_view"), emptySet()).surface)
    }

    @Test
    fun `reels embedded in the home feed outrank the plain home feed`() {
        val result = classifier.classify(instagram("feed_recycler_view", "clips_netego_container"), emptySet())
        assertEquals(FeedSurface.SHORT_VIDEO_IN_HOME, result.surface)
    }

    @Test
    fun `a profile full of reels is still a profile`() {
        // Every reel thumbnail is described "Reel by <name>". Matching that text meant opening
        // someone's profile counted as opening the Reels feed, and it was covered.
        val profile = instagramScreen(
            ids = setOf("profile_header", "profile_tab", "tab_bar", "profile_grid_recycler_view"),
            descriptions = setOf("reel by alice", "reel by bob", "reels tab", "posts tab"),
        )
        val result = classifier.classify(profile, emptySet())
        assertFalse(
            "a profile must never be treated as a short-video feed",
            result.surface.isShortVideo,
        )
    }

    @Test
    fun `an explore grid of reels is explore, not the reels feed`() {
        val explore = instagramScreen(
            ids = setOf("explore_grid", "tab_bar", "search_tab"),
            descriptions = setOf("reel by alice", "reel by bob"),
        )
        assertEquals(FeedSurface.EXPLORE, classifier.classify(explore, emptySet()).surface)
    }

    @Test
    fun `a reel in the timeline answers to the home feed switch, not the reels tab switch`() {
        val home = instagramScreen(
            ids = setOf("feed_recycler_view", "clips_netego_container", "tab_bar"),
            descriptions = setOf("reel by alice"),
        )
        assertEquals(FeedSurface.SHORT_VIDEO_IN_HOME, classifier.classify(home, emptySet()).surface)
    }

    @Test
    fun `a reel playing in the timeline is caught without any clips id`() {
        // The screen from the bug report: a "Suggested for you" reel autoplaying in the home
        // feed. No clips_* container is present - the unit ids this used to require were guesses
        // that do not exist - so the reel label plus a playing video surface is the evidence.
        val feedReel = instagramScreen(
            ids = setOf("feed_recycler_view", "tab_bar", "row_feed_button_like", "class:textureview"),
            descriptions = setOf("reel by annabutterz", "like", "comment", "share"),
        )
        assertEquals(FeedSurface.SHORT_VIDEO_IN_HOME, classifier.classify(feedReel, emptySet()).surface)
    }

    @Test
    fun `the same label on a profile or explore still does not count`() {
        // The guard that keeps the line above from re-breaking profiles and Explore.
        val profile = instagramScreen(
            ids = setOf("profile_header", "profile_tab", "tab_bar"),
            descriptions = setOf("reel by alice", "reel by bob"),
        )
        assertFalse(classifier.classify(profile, emptySet()).surface.isShortVideo)

        val explore = instagramScreen(
            ids = setOf("explore_grid", "tab_bar"),
            descriptions = setOf("reel by alice"),
        )
        assertEquals(FeedSurface.EXPLORE, classifier.classify(explore, emptySet()).surface)

        // Context alone is enough even if the id guards miss.
        val unknownProfile = instagramScreen(
            ids = setOf("some_renamed_grid", "tab_bar"),
            descriptions = setOf("reel by alice"),
        )
        assertFalse(
            classifier.classify(unknownProfile, setOf(ContextTag.PROFILE)).surface.isShortVideo,
        )
    }

    @Test
    fun `the reels player is still caught once it is actually open`() {
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            classifier.classify(
                instagramScreen(
                    ids = setOf("clips_viewer_root", "clips_viewer_video_container", "tab_bar"),
                    descriptions = setOf("reel by alice"),
                ),
                emptySet(),
            ).surface,
        )
    }

    @Test
    fun `stories are not mistaken for reels`() {
        val result = classifier.classify(instagram("reel_viewer_root", "reel_viewer_texture_view"), emptySet())
        assertEquals(FeedSurface.STORIES, result.surface)
    }

    @Test
    fun `youtube shorts player is detected by its player ids`() {
        listOf("reel_recycler", "reel_player_page_container", "shorts_video_container").forEach { id ->
            assertEquals(
                "$id should be the shorts player",
                FeedSurface.SHORT_VIDEO_FEED,
                classifier.classify(youtube(ids = setOf(id)), emptySet()).surface,
            )
        }
    }

    @Test
    fun `the shorts tab in youtube's navigation bar does not make the whole app a feed`() {
        // Every YouTube screen carries a navigation tab described as "Shorts". Matching on that
        // meant the home feed, search and the long-form watch page all read as a short-video
        // feed, and the guard walked the user out of the app.
        val homeFeed = youtube(
            ids = setOf("browse_fragment", "pivot_bar", "results"),
            descriptions = setOf("shorts", "home", "subscriptions", "you"),
        )
        assertEquals(FeedSurface.HOME_FEED, classifier.classify(homeFeed, emptySet()).surface)

        val watchPage = youtube(
            ids = setOf("watch_player", "player_control_play_pause_replay_button", "pivot_bar"),
            descriptions = setOf("shorts", "pause video", "next video"),
        )
        val watched = classifier.classify(watchPage, emptySet())
        assertFalse(
            "long-form video must never be classified as short video",
            watched.surface.isShortVideo,
        )

        val searchResults = youtube(
            ids = setOf("results", "pivot_bar"),
            descriptions = setOf("shorts", "search"),
        )
        assertFalse(classifier.classify(searchResults, emptySet()).surface.isShortVideo)
    }

    @Test
    fun `the shorts shelf on the home tab is its own surface`() {
        val result = classifier.classify(
            youtube(ids = setOf("browse_fragment", "shorts_shelf"), descriptions = setOf("shorts")),
            emptySet(),
        )
        assertEquals(FeedSurface.SHORT_VIDEO_IN_HOME, result.surface)
    }

    @Test
    fun `youtube shorts opened from search is its own surface`() {
        val result = classifier.classify(youtube(ids = setOf("reel_recycler")), setOf(ContextTag.SEARCH))
        assertEquals(FeedSurface.SHORT_VIDEO_IN_EXPLORE, result.surface)
    }

    @Test
    fun `unknown screens stay unknown`() {
        assertEquals(
            FeedSurface.UNKNOWN,
            classifier.classify(instagram("settings_row_title"), emptySet()).surface,
        )
        assertEquals(
            FeedSurface.UNKNOWN,
            classifier.classify(ScreenSnapshot("com.example.calculator", setOf("clips_viewer")), emptySet()).surface,
        )
    }

    @Test
    fun `context tags come from the snapshot`() {
        val tags = classifier.contextTagsFor(instagram("direct_thread_recycler_view"))
        assertTrue(ContextTag.DM in tags)
    }

    @Test
    fun `context expires so an old dm never unlocks the reels tab`() {
        val trail = ContextTrail(maxAgeMs = 5_000)
        trail.record(ContextTag.DM, 1_000)
        assertTrue(ContextTag.DM in trail.activeTags(3_000))
        assertFalse(ContextTag.DM in trail.activeTags(20_000))
    }

    @Test
    fun `context resets when the user switches apps`() {
        val trail = ContextTrail()
        trail.onPackageChanged(SupportedApps.INSTAGRAM)
        trail.record(ContextTag.DM, 0)
        trail.onPackageChanged(SupportedApps.YOUTUBE)
        assertTrue(trail.activeTags(0).isEmpty())
    }

    @Test
    fun `signature packs survive a json round trip`() {
        val json = SignaturePack.encode(DefaultSignatures.pack())
        val parsed = SignaturePack.parse(json)
        assertEquals(DefaultSignatures.pack(), parsed)
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            SurfaceClassifier(parsed).classify(instagram("clips_viewer_root"), emptySet()).surface,
        )
    }
}
