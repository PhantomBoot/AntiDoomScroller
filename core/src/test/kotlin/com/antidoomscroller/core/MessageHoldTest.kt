package com.antidoomscroller.core

import com.antidoomscroller.core.messages.MessageBook
import com.antidoomscroller.core.messages.MessageRotator
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.model.MessageRotation
import com.antidoomscroller.core.model.MessageSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MessageHoldTest {

    private val lines = listOf("one", "two", "three", "four", "five", "six")
    private val settings = MessageSettings(
        feedBlockMessages = lines,
        antiScrollMessages = lines,
        rotation = MessageRotation.RANDOM,
        holdSeconds = 4,
    )

    private fun rotator() = MessageRotator(MessageBook(Random(7)))

    private fun render(r: MessageRotator, atMs: Long, key: String = "ig/short_video_feed") =
        r.render(settings, MessageKind.FEED_BLOCK, key, atMs, "Instagram", FeedSurface.SHORT_VIDEO_FEED).body

    @Test
    fun `a message stays put while the cover is re-examined`() {
        val rotator = rotator()
        val first = render(rotator, 0)

        // The screen is looked at several times a second; none of those may change the message.
        listOf(250L, 500L, 1_000L, 2_000L, 3_500L, 3_999L).forEach { at ->
            assertEquals("changed after ${at}ms", first, render(rotator, at))
        }
    }

    @Test
    fun `it moves on once the hold has elapsed`() {
        val rotator = rotator()
        render(rotator, 0)

        val held = render(rotator, 3_000)
        val next = render(rotator, 4_000)
        // Same list, so a repeat is possible - what matters is that it is free to change now and
        // then holds the new one just as long.
        assertEquals(next, render(rotator, 5_000))
        assertEquals(next, render(rotator, 7_999))
        assertTrue(next in lines)
        assertTrue(held in lines)
    }

    @Test
    fun `a different interruption gets its own message immediately`() {
        val rotator = rotator()
        val reels = render(rotator, 0, key = "ig/short_video_feed")
        val shorts = render(rotator, 100, key = "yt/short_video_feed")
        assertTrue(shorts in lines)
        // The new key must not be answered with the held message for the old one.
        assertEquals(shorts, render(rotator, 200, key = "yt/short_video_feed"))
        assertTrue(reels in lines)
    }

    @Test
    fun `sequential rotation advances one step per hold, not per look`() {
        val ordered = settings.copy(rotation = MessageRotation.SEQUENTIAL)
        val rotator = rotator()

        fun at(ms: Long) = rotator.render(ordered, MessageKind.FEED_BLOCK, "ig/feed", ms).body

        assertEquals("one", at(0))
        assertEquals("one", at(1_000))
        assertEquals("one", at(3_999))
        assertEquals("two", at(4_000))
        assertEquals("two", at(7_000))
        assertEquals("three", at(8_000))
    }

    @Test
    fun `the hold is configurable and always sane`() {
        assertEquals(4_000L, MessageSettings().holdMs)
        assertEquals(3_000L, MessageSettings(holdSeconds = 3).holdMs)
        assertEquals(1_000L, MessageSettings(holdSeconds = 0).holdMs)
        assertEquals(120_000L, MessageSettings(holdSeconds = 9_999).holdMs)
    }

    @Test
    fun `clearing means the next interruption starts fresh`() {
        val rotator = rotator()
        val first = render(rotator, 0)
        rotator.clear()
        val afterClear = render(rotator, 100)
        assertTrue(afterClear in lines)
        assertEquals(afterClear, render(rotator, 200))
        assertTrue(first in lines)
    }

    @Test
    fun `a single message list never appears to change`() {
        val one = settings.copy(feedBlockMessages = listOf("only this"))
        val rotator = rotator()
        listOf(0L, 100L, 5_000L, 60_000L).forEach {
            assertEquals("only this", rotator.render(one, MessageKind.FEED_BLOCK, "ig/feed", it).body)
        }
        assertNotEquals("", one.feedBlockMessages.first())
    }
}
