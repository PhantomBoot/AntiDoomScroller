package com.antidoomscroller.core

import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.dns.DnsCodec
import com.antidoomscroller.core.dns.DnsFilterEngine
import com.antidoomscroller.core.dns.DnsType
import com.antidoomscroller.core.dns.DnsVerdict
import com.antidoomscroller.core.model.BlockResponseMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCodecTest {

    private fun query(name: String, type: Int = DnsType.A, id: Int = 0x1234): ByteArray {
        val labels = name.split('.')
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val buffer = ByteArray(size)
        buffer[0] = (id shr 8).toByte()
        buffer[1] = (id and 0xFF).toByte()
        buffer[2] = 0x01 // recursion desired
        buffer[5] = 1 // one question
        var offset = 12
        for (label in labels) {
            buffer[offset++] = label.length.toByte()
            for (c in label) buffer[offset++] = c.code.toByte()
        }
        buffer[offset++] = 0
        buffer[offset++] = (type shr 8).toByte()
        buffer[offset++] = (type and 0xFF).toByte()
        buffer[offset++] = 0
        buffer[offset] = 1 // class IN
        return buffer
    }

    @Test
    fun `parses a standard query`() {
        val parsed = DnsCodec.parseQuery(query("www.Example.com"))
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("www.example.com", parsed.name)
        assertEquals(DnsType.A, parsed.type)
        assertEquals(0x1234, parsed.id)
        assertTrue(parsed.isStandardQuery)
    }

    @Test
    fun `rejects truncated and compressed questions`() {
        assertNull(DnsCodec.parseQuery(ByteArray(6)))
        val raw = query("example.com")
        assertNull(DnsCodec.parseQuery(raw, length = 14))
        val compressed = raw.copyOf()
        compressed[12] = 0xC0.toByte()
        assertNull(DnsCodec.parseQuery(compressed))
    }

    @Test
    fun `nxdomain response keeps id and question and sets rcode 3`() {
        val request = query("blocked.example.com")
        val parsed = requireNotNull(DnsCodec.parseQuery(request))
        val response = DnsCodec.buildNxDomain(parsed, request)

        assertEquals(0x1234, DnsCodec.idOf(response))
        assertEquals(3, DnsCodec.rcodeOf(response))
        assertEquals(0x80, response[2].toInt() and 0x80) // QR set
        assertEquals(1, ((response[4].toInt() shl 8) or response[5].toInt())) // question kept
        assertEquals(0, ((response[6].toInt() shl 8) or response[7].toInt())) // no answers
        assertArrayEquals(request.copyOfRange(12, request.size), response.copyOfRange(12, response.size))
    }

    @Test
    fun `null address response answers with all zeroes`() {
        val request = query("blocked.example.com", DnsType.A)
        val parsed = requireNotNull(DnsCodec.parseQuery(request))
        val response = DnsCodec.buildNullAddress(parsed, request)

        assertEquals(0, DnsCodec.rcodeOf(response))
        assertEquals(1, ((response[6].toInt() shl 8) or response[7].toInt()))
        val rdata = response.copyOfRange(response.size - 4, response.size)
        assertArrayEquals(ByteArray(4), rdata)
        assertEquals(0xC0.toByte(), response[parsed.questionEnd])
        assertEquals(0x0C.toByte(), response[parsed.questionEnd + 1])
    }

    @Test
    fun `null address falls back to empty answer for unsupported types`() {
        val request = query("blocked.example.com", DnsType.HTTPS)
        val parsed = requireNotNull(DnsCodec.parseQuery(request))
        val response = DnsCodec.buildNullAddress(parsed, request)
        assertEquals(0, ((response[6].toInt() shl 8) or response[7].toInt()))
        assertEquals(0, DnsCodec.rcodeOf(response))
    }

    @Test
    fun `engine blocks listed hosts and forwards the rest`() {
        val engine = DnsFilterEngine(
            matcher = DomainMatcher.Builder().block("blocked.com").build(),
            responseMode = BlockResponseMode.NXDOMAIN,
        )

        val blocked = engine.evaluate(query("cdn.blocked.com"))
        assertTrue(blocked is DnsVerdict.Blocked)
        assertEquals("cdn.blocked.com", (blocked as DnsVerdict.Blocked).host)
        assertEquals(3, DnsCodec.rcodeOf(blocked.response))

        val forwarded = engine.evaluate(query("wikipedia.org"))
        assertTrue(forwarded is DnsVerdict.Forward)

        assertEquals(2, engine.stats.queries)
        assertEquals(1, engine.stats.blocked)
        assertEquals("cdn.blocked.com", engine.stats.lastBlockedHost)
    }

    @Test
    fun `disabled engine passes everything through`() {
        val engine = DnsFilterEngine(DomainMatcher.Builder().block("blocked.com").build(), enabled = false)
        assertTrue(engine.evaluate(query("blocked.com")) is DnsVerdict.Passthrough)
    }
}
