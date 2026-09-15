package com.antidoomscroller.core

import com.antidoomscroller.core.detect.DefaultSignatures
import com.antidoomscroller.core.detect.MediaRegionResolver
import com.antidoomscroller.core.detect.ScreenRect
import com.antidoomscroller.core.detect.ScreenSnapshot
import com.antidoomscroller.core.model.SupportedApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sized from a real phone: 1080x2400, Instagram's tab bar across the bottom and a post header at
 * the top, which is the layout in the screenshot that prompted this.
 */
class MediaRegionTest {

    private val screen = ScreenRect(0, 0, 1080, 2400)
    private val pack = DefaultSignatures.pack()
    private val instagram = pack.forPackage(SupportedApps.INSTAGRAM)
    private val youtube = pack.forPackage(SupportedApps.YOUTUBE)

    private fun snapshot(bounds: Map<String, ScreenRect>) =
        ScreenSnapshot(SupportedApps.INSTAGRAM, viewIds = bounds.keys, bounds = bounds)

    @Test
    fun `a reel in the home feed covers the video, not the whole list`() {
        val region = MediaRegionResolver.resolve(
            snapshot(
                mapOf(
                    // The list fills the screen and must not be what gets covered.
                    "feed_recycler_view" to ScreenRect(0, 0, 1080, 2250),
                    "row_feed_profile_header" to ScreenRect(0, 180, 1080, 320),
                    "clips_netego_video_container" to ScreenRect(0, 320, 1080, 1900),
                    "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                ),
            ),
            instagram,
            screen,
        )

        assertEquals(ScreenRect(0, 320, 1080, 1900), region)
    }

    @Test
    fun `the full-screen player is covered but the tab bar is left alone`() {
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                snapshot(
                    mapOf(
                        // A full-bleed player draws underneath the tab bar.
                        "clips_viewer_video_container" to ScreenRect(0, 0, 1080, 2400),
                        "clips_viewer_root" to ScreenRect(0, 0, 1080, 2400),
                        "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                    ),
                ),
                instagram,
                screen,
            ),
        )

        assertEquals(2250, region.bottom)
        assertTrue("the tab bar must stay visible", region.bottom <= 2250)
        assertEquals(0, region.top)
    }

    @Test
    fun `a top bar is left alone too`() {
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                snapshot(
                    mapOf(
                        "action_bar_container" to ScreenRect(0, 0, 1080, 220),
                        "clips_video_container" to ScreenRect(0, 0, 1080, 2400),
                        "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                    ),
                ),
                instagram,
                screen,
            ),
        )

        assertEquals(220, region.top)
        assertEquals(2250, region.bottom)
    }

    @Test
    fun `the tighter container wins over the looser one`() {
        val region = MediaRegionResolver.resolve(
            snapshot(
                mapOf(
                    "clips_viewer_root" to ScreenRect(0, 0, 1080, 2400),
                    "clips_video_container" to ScreenRect(0, 400, 1080, 1800),
                ),
            ),
            instagram,
            screen,
        )
        assertEquals(ScreenRect(0, 400, 1080, 1800), region)
    }

    @Test
    fun `the playing video surface is covered when no container id is recognised`() {
        // Instagram renames its containers freely, but the thing playing is still a video
        // surface, and that is what the box has to land on.
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                snapshot(
                    mapOf(
                        "feed_recycler_view" to ScreenRect(0, 220, 1080, 2250),
                        "row_feed_button_like" to ScreenRect(40, 1900, 200, 1980),
                        "class:textureview" to ScreenRect(0, 300, 1080, 1860),
                        "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                    ),
                ),
                instagram,
                screen,
            ),
        )

        assertEquals(ScreenRect(0, 300, 1080, 1860), region)
        assertTrue("the post header above must stay visible", region.top > 220)
        assertTrue("the tab bar must stay usable", region.bottom < 2250)
    }

    @Test
    fun `the cover goes on the reel, not the row of story circles above it`() {
        // The screen from the bug report: a stories tray across the top of the timeline with a
        // reel playing below it. Instagram calls that tray "reels_tray" - its naming predates
        // Reels - so an id-led choice put the black box on the stories and left the reel playing.
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                snapshot(
                    mapOf(
                        "reels_tray_container" to ScreenRect(0, 220, 1080, 480),
                        "media_container" to ScreenRect(0, 220, 1080, 480),
                        "feed_recycler_view" to ScreenRect(0, 220, 1080, 2250),
                        "class:textureview" to ScreenRect(0, 520, 1080, 2000),
                        "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                    ),
                ),
                instagram,
                screen,
            ),
        )

        assertEquals(ScreenRect(0, 520, 1080, 2000), region)
        assertTrue("the stories tray must stay visible", region.top > 480)
    }

    @Test
    fun `a named container still wins when the video is inside it`() {
        val region = MediaRegionResolver.resolve(
            snapshot(
                mapOf(
                    "clips_video_container" to ScreenRect(0, 300, 1080, 1900),
                    "class:textureview" to ScreenRect(20, 320, 1060, 1880),
                ),
            ),
            instagram,
            screen,
        )
        assertEquals(ScreenRect(0, 300, 1080, 1900), region)
    }

    @Test
    fun `thumbnails and buttons are never mistaken for the video`() {
        val region = MediaRegionResolver.resolve(
            snapshot(mapOf("clips_video_container" to ScreenRect(40, 40, 200, 200))),
            instagram,
            screen,
        )
        assertNull(region)
    }

    @Test
    fun `a unit scrolled off screen is ignored in favour of the visible one`() {
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                snapshot(
                    mapOf(
                        // Same priority, but this one is mostly above the viewport.
                        "clips_netego_video_container" to ScreenRect(0, -1500, 1080, -100),
                        "clips_viewer_video_container" to ScreenRect(0, 300, 1080, 1900),
                    ),
                ),
                instagram,
                screen,
            ),
        )
        assertEquals(ScreenRect(0, 300, 1080, 1900), region)
    }

    @Test
    fun `nothing that looks like video means no region at all`() {
        assertNull(
            MediaRegionResolver.resolve(
                snapshot(mapOf("settings_row_title" to ScreenRect(0, 0, 1080, 200))),
                instagram,
                screen,
            ),
        )
        assertNull(MediaRegionResolver.resolve(snapshot(emptyMap()), null, screen))
    }

    @Test
    fun `the fallback content area still spares the app's own bars`() {
        val area = MediaRegionResolver.contentArea(
            snapshot(
                mapOf(
                    "action_bar_container" to ScreenRect(0, 0, 1080, 220),
                    "tab_bar" to ScreenRect(0, 2250, 1080, 2400),
                ),
            ),
            instagram,
            screen,
        )
        assertEquals(ScreenRect(0, 220, 1080, 2250), area)
    }

    @Test
    fun `youtube shorts keeps the pivot bar visible`() {
        val region = requireNotNull(
            MediaRegionResolver.resolve(
                ScreenSnapshot(
                    SupportedApps.YOUTUBE,
                    bounds = mapOf(
                        "reel_player_page_container" to ScreenRect(0, 0, 1080, 2400),
                        "pivot_bar" to ScreenRect(0, 2240, 1080, 2400),
                    ),
                ),
                youtube,
                screen,
            ),
        )
        assertEquals(2240, region.bottom)
    }

    @Test
    fun `every shipped app knows where its video lives`() {
        pack.apps.forEach { app ->
            assertNotNull("${app.packageName} has no media ids", app.mediaViewIds)
            assertTrue("${app.packageName} has no media ids", app.mediaViewIds.isNotEmpty())
        }
    }
}
