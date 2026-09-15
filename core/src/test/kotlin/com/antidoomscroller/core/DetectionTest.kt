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
    fun `stories are not mistaken for reels`() {
        val result = classifier.classify(instagram("reel_viewer_root", "reel_viewer_texture_view"), emptySet())
        assertEquals(FeedSurface.STORIES, result.surface)
    }

    @Test
    fun `youtube shorts player is detected by id and by description`() {
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            classifier.classify(youtube(ids = setOf("reel_recycler")), emptySet()).surface,
        )
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            classifier.classify(youtube(descriptions = setOf("shorts")), emptySet()).surface,
        )
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
