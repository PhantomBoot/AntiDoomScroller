package com.antidoomscroller.core

import com.antidoomscroller.core.messages.MessageBook
import com.antidoomscroller.core.model.AntiScrollSettings
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.model.MessageRotation
import com.antidoomscroller.core.model.MessageSettings
import com.antidoomscroller.core.model.Sensitivity
import com.antidoomscroller.core.scroll.ScrollDetector
import com.antidoomscroller.core.scroll.ScrollVerdict
import com.antidoomscroller.core.util.Durations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BehaviourTest {

    private val settings = AntiScrollSettings(
        sensitivity = Sensitivity.CUSTOM,
        customScrollThreshold = 5,
        customWindowSeconds = 10,
        cooldownSeconds = 60,
    )

    @Test
    fun `anti scroll fires once past the threshold then respects its cooldown`() {
        val detector = ScrollDetector()
        repeat(4) { i ->
            assertEquals(ScrollVerdict.Nothing, detector.onScroll("app", settings, i * 100L))
        }
        val verdict = detector.onScroll("app", settings, 500)
        assertTrue(verdict is ScrollVerdict.Interrupt)
        assertEquals(5, (verdict as ScrollVerdict.Interrupt).scrollsInWindow)

        repeat(10) { i ->
            assertEquals(ScrollVerdict.Nothing, detector.onScroll("app", settings, 600 + i * 100L))
        }
    }

    @Test
    fun `scrolls spread over a long time never trigger`() {
        val detector = ScrollDetector()
        repeat(20) { i ->
            assertEquals(ScrollVerdict.Nothing, detector.onScroll("app", settings, i * 9_000L))
        }
    }

    @Test
    fun `the counter resets when the user switches apps`() {
        val detector = ScrollDetector()
        repeat(4) { i -> detector.onScroll("app.one", settings, i * 100L) }
        assertEquals(ScrollVerdict.Nothing, detector.onScroll("app.two", settings, 500))
        assertEquals(1, detector.scrollsInWindow())
    }

    @Test
    fun `sensitivity presets map to thresholds`() {
        assertEquals(20, AntiScrollSettings(sensitivity = Sensitivity.STRICT).scrollThreshold)
        assertEquals(45, AntiScrollSettings(sensitivity = Sensitivity.BALANCED).scrollThreshold)
        assertEquals(80, AntiScrollSettings(sensitivity = Sensitivity.GENTLE).scrollThreshold)
    }

    @Test
    fun `anti scroll can be switched off entirely`() {
        val detector = ScrollDetector()
        val off = settings.copy(enabled = false)
        repeat(20) { i -> assertEquals(ScrollVerdict.Nothing, detector.onScroll("app", off, i * 10L)) }
    }

    @Test
    fun `custom messages are used and rotate in order`() {
        val book = MessageBook()
        val messages = MessageSettings(
            feedBlockMessages = listOf("one", "two", "three"),
            rotation = MessageRotation.SEQUENTIAL,
        )
        assertEquals("one", book.pick(messages, MessageKind.FEED_BLOCK))
        assertEquals("two", book.pick(messages, MessageKind.FEED_BLOCK))
        assertEquals("three", book.pick(messages, MessageKind.FEED_BLOCK))
        assertEquals("one", book.pick(messages, MessageKind.FEED_BLOCK))
    }

    @Test
    fun `random rotation stays inside the user's own list`() {
        val book = MessageBook(Random(1))
        val messages = MessageSettings(antiScrollMessages = listOf("a", "b"), rotation = MessageRotation.RANDOM)
        repeat(50) {
            assertTrue(book.pick(messages, MessageKind.ANTI_SCROLL) in listOf("a", "b"))
        }
    }

    @Test
    fun `an emptied message list still shows something`() {
        val book = MessageBook()
        val messages = MessageSettings(adultBlockMessages = listOf("  ", ""))
        assertEquals(MessageBook.FALLBACK, book.pick(messages, MessageKind.ADULT_BLOCK))
    }

    @Test
    fun `rendered titles carry the app and surface when asked`() {
        val book = MessageBook()
        val settings = MessageSettings(feedBlockMessages = listOf("nope"))
        val rendered = book.render(settings, MessageKind.FEED_BLOCK, "Instagram", FeedSurface.SHORT_VIDEO_FEED)
        assertEquals("nope", rendered.body)
        assertTrue(rendered.title.contains("Instagram"))
        assertTrue(rendered.title.contains(FeedSurface.SHORT_VIDEO_FEED.label))

        val bare = book.render(settings.copy(showAppName = false, showBlockedSurface = false), MessageKind.FEED_BLOCK, "Instagram")
        assertEquals("Blocked", bare.title)
    }

    @Test
    fun `message sanitising collapses whitespace and caps length`() {
        assertEquals("a b", MessageBook.sanitise("  a \n\t b  "))
        assertEquals(MessageBook.MAX_LENGTH, MessageBook.sanitise("x".repeat(400)).length)
    }

    @Test
    fun `durations read the way a countdown should`() {
        assertEquals("2d 0h 0m", Durations.format(48 * 60 * 60 * 1000L))
        assertEquals("1h 30m", Durations.format(90 * 60 * 1000L))
        assertEquals("5m", Durations.format(5 * 60 * 1000L))
        assertEquals("0m", Durations.format(-1))
        assertEquals("47:59:59", Durations.formatPrecise(48 * 60 * 60 * 1000L - 1000))
    }
}
