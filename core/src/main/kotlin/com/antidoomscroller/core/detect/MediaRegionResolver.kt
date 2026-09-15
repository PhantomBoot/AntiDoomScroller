package com.antidoomscroller.core.detect

/**
 * Works out exactly which part of the screen to cover.
 *
 * The point of the cover is to take away the video and nothing else, so the app around it keeps
 * working: the post header above a reels unit stays readable, and the app's own navigation bar
 * stays tappable. That means picking the video's own container rather than whatever list it
 * happens to sit in, and then trimming anything that would land on the app's chrome.
 */
object MediaRegionResolver {

    /** Below this share of the screen a match is a thumbnail or a button, not the video. */
    const val MIN_AREA_FRACTION = 0.06

    /** A cover shorter than this is not worth showing, and probably means a bad match. */
    const val MIN_HEIGHT_FRACTION = 0.10

    /**
     * The box to cover, or null when nothing on screen looks like the video - in which case the
     * caller should fall back to [contentArea] rather than blacking out the whole display.
     */
    fun resolve(snapshot: ScreenSnapshot, app: AppSignature?, screen: ScreenRect): ScreenRect? {
        if (app == null || app.mediaViewIds.isEmpty() || screen.isEmpty) return null

        val best = snapshot.bounds.entries
            .mapNotNull { (id, rect) ->
                val priority = app.mediaViewIds.indexOfFirst { id.contains(it) }
                if (priority < 0) return@mapNotNull null
                val visible = rect.intersect(screen)
                if (visible.isEmpty) return@mapNotNull null
                if (visible.area < (screen.area * MIN_AREA_FRACTION).toLong()) return@mapNotNull null
                Candidate(priority, visible)
            }
            // Most specific id wins; between equals, the tighter box wins.
            .minWithOrNull(compareBy({ it.priority }, { it.rect.area }))
            ?: return null

        val trimmed = trimChrome(best.rect, snapshot, app, screen)
        if (trimmed.isEmpty) return null
        if (trimmed.height < screen.height * MIN_HEIGHT_FRACTION) return null
        return trimmed
    }

    /** The screen with the app's own top and bottom bars taken off - the safe fallback. */
    fun contentArea(snapshot: ScreenSnapshot, app: AppSignature?, screen: ScreenRect): ScreenRect {
        if (app == null) return screen
        return trimChrome(screen, snapshot, app, screen)
    }

    /**
     * Pulls the box back off the app's navigation bars.
     *
     * A full-bleed player draws underneath them, so covering its raw bounds would swallow the tab
     * bar - which is the difference between "the video is hidden" and "the app is gone".
     */
    private fun trimChrome(
        region: ScreenRect,
        snapshot: ScreenSnapshot,
        app: AppSignature,
        screen: ScreenRect,
    ): ScreenRect {
        var top = region.top
        var bottom = region.bottom

        boundsMatching(snapshot, app.bottomChromeViewIds, screen)
            // Only bars in the lower half are bottom chrome; ids can repeat elsewhere.
            .filter { it.top >= screen.top + screen.height / 2 }
            .minByOrNull { it.top }
            ?.let { bar -> if (bottom > bar.top) bottom = bar.top }

        boundsMatching(snapshot, app.topChromeViewIds, screen)
            .filter { it.bottom <= screen.top + screen.height / 2 }
            .maxByOrNull { it.bottom }
            ?.let { bar -> if (top < bar.bottom) top = bar.bottom }

        if (bottom <= top) return ScreenRect.EMPTY
        return ScreenRect(region.left, top, region.right, bottom)
    }

    private fun boundsMatching(
        snapshot: ScreenSnapshot,
        fragments: List<String>,
        screen: ScreenRect,
    ): List<ScreenRect> {
        if (fragments.isEmpty()) return emptyList()
        return snapshot.bounds.entries
            .filter { (id, _) -> fragments.any { id.contains(it) } }
            .map { it.value.intersect(screen) }
            .filterNot { it.isEmpty }
    }

    private data class Candidate(val priority: Int, val rect: ScreenRect)
}
