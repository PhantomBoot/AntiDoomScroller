package com.antidoomscroller.core

import com.antidoomscroller.core.blocklist.BlocklistParser
import com.antidoomscroller.core.blocklist.DomainMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    private fun matcher(block: List<String> = emptyList(), allow: List<String> = emptyList()) =
        DomainMatcher.Builder().blockAll(block).allowAll(allow).build()

    @Test
    fun `blocks apex and subdomains`() {
        val m = matcher(block = listOf("example.com"))
        assertTrue(m.isBlocked("example.com"))
        assertTrue(m.isBlocked("www.example.com"))
        assertTrue(m.isBlocked("cdn.media.example.com"))
        assertFalse(m.isBlocked("example.com.evil.net"))
        assertFalse(m.isBlocked("notexample.com"))
        assertFalse(m.isBlocked("com"))
    }

    @Test
    fun `wildcard prefix spares the apex`() {
        val m = matcher(block = listOf("*.example.com"))
        assertFalse(m.isBlocked("example.com"))
        assertTrue(m.isBlocked("videos.example.com"))
    }

    @Test
    fun `exact prefix blocks only that name`() {
        val m = matcher(block = listOf("=example.com"))
        assertTrue(m.isBlocked("example.com"))
        assertFalse(m.isBlocked("www.example.com"))
    }

    @Test
    fun `more specific allow beats broader block`() {
        val m = matcher(block = listOf("example.com"), allow = listOf("docs.example.com"))
        assertTrue(m.isBlocked("example.com"))
        assertTrue(m.isBlocked("videos.example.com"))
        assertFalse(m.isBlocked("docs.example.com"))
        assertFalse(m.isBlocked("api.docs.example.com"))
    }

    @Test
    fun `more specific block beats broader allow`() {
        val m = matcher(block = listOf("ads.partner.example.com"), allow = listOf("example.com"))
        assertFalse(m.isBlocked("example.com"))
        assertFalse(m.isBlocked("partner.example.com"))
        assertTrue(m.isBlocked("ads.partner.example.com"))
    }

    @Test
    fun `matched pattern is reported`() {
        val m = matcher(block = listOf("example.com"))
        assertEquals("example.com", m.match("www.example.com").matchedPattern)
        assertNull(m.match("other.org").matchedPattern)
    }

    @Test
    fun `normalise strips scheme port path and case`() {
        assertEquals("example.com", DomainMatcher.normalise("HTTPS://Example.com:8443/path?x=1"))
        assertEquals("example.com", DomainMatcher.normalise("example.com."))
        assertEquals("example.com", DomainMatcher.normalise("user@example.com"))
        assertNull(DomainMatcher.normalise("localhost"))
        assertNull(DomainMatcher.normalise("  "))
        assertNull(DomainMatcher.normalise("[2001:db8::1]"))
        assertNull(DomainMatcher.normalise("two words.com"))
    }

    @Test
    fun `empty matcher blocks nothing`() {
        assertFalse(DomainMatcher.EMPTY.isBlocked("example.com"))
    }

    @Test
    fun `parses hosts adblock and plain formats`() {
        val text = """
            # comment line
            0.0.0.0 blocked-one.com
            127.0.0.1   blocked-two.com  # trailing note
            ||blocked-three.com^
            blocked-four.com
            192.168.1.5 printer.local
            @@||allowed.com^
            0.0.0.0 localhost
            not a domain
        """.trimIndent()

        val result = BlocklistParser.parse(text)
        assertEquals(
            listOf("blocked-one.com", "blocked-two.com", "blocked-three.com", "blocked-four.com"),
            result.domains,
        )
    }
}
