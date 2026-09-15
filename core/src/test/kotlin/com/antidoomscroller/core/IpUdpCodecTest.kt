package com.antidoomscroller.core

import com.antidoomscroller.core.dns.IpUdpCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpUdpCodecTest {

    private val v4Source = byteArrayOf(10, 115, 44, 1)
    private val v4Dest = byteArrayOf(10, 115, 44, 2)
    private val v6Source = ByteArray(16).also { it[0] = 0xFD.toByte(); it[15] = 1 }
    private val v6Dest = ByteArray(16).also { it[0] = 0xFD.toByte(); it[15] = 2 }

    @Test
    fun `ipv4 round trip preserves addresses ports and payload`() {
        val payload = "dns-query-bytes".toByteArray()
        val packet = IpUdpCodec.build(4, v4Source, v4Dest, 34567, 53, payload)

        val parsed = IpUdpCodec.parse(packet)
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(4, parsed.ipVersion)
        assertArrayEquals(v4Source, parsed.sourceAddress)
        assertArrayEquals(v4Dest, parsed.destinationAddress)
        assertEquals(34567, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `ipv4 header checksum verifies to zero`() {
        val packet = IpUdpCodec.build(4, v4Source, v4Dest, 5353, 53, ByteArray(24) { it.toByte() })
        assertEquals(0, IpUdpCodec.internetChecksum(packet, 0, 20))
    }

    @Test
    fun `ipv6 round trip preserves addresses ports and payload`() {
        val payload = ByteArray(40) { (it * 7).toByte() }
        val packet = IpUdpCodec.build(6, v6Source, v6Dest, 40000, 53, payload)

        val parsed = requireNotNull(IpUdpCodec.parse(packet))
        assertEquals(6, parsed.ipVersion)
        assertArrayEquals(v6Source, parsed.sourceAddress)
        assertArrayEquals(v6Dest, parsed.destinationAddress)
        assertEquals(40000, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `response swaps source and destination`() {
        val request = requireNotNull(IpUdpCodec.parse(IpUdpCodec.build(4, v4Source, v4Dest, 33333, 53, "q".toByteArray())))
        val response = requireNotNull(IpUdpCodec.parse(IpUdpCodec.buildResponse(request, "answer".toByteArray())))

        assertArrayEquals(v4Dest, response.sourceAddress)
        assertArrayEquals(v4Source, response.destinationAddress)
        assertEquals(53, response.sourcePort)
        assertEquals(33333, response.destinationPort)
        assertArrayEquals("answer".toByteArray(), response.payload)
    }

    @Test
    fun `non udp and malformed packets are rejected`() {
        val tcp = IpUdpCodec.build(4, v4Source, v4Dest, 1, 53, ByteArray(4)).also { it[9] = 6 }
        assertNull(IpUdpCodec.parse(tcp))
        assertNull(IpUdpCodec.parse(ByteArray(0)))
        assertNull(IpUdpCodec.parse(byteArrayOf(0x45, 0, 0)))
        val unknownVersion = ByteArray(40).also { it[0] = 0x75 }
        assertNull(IpUdpCodec.parse(unknownVersion))
    }

    @Test
    fun `fragmented ipv4 packets are left alone`() {
        val packet = IpUdpCodec.build(4, v4Source, v4Dest, 1234, 53, ByteArray(8))
        packet[6] = 0x00
        packet[7] = 0x10 // non-zero fragment offset
        assertNull(IpUdpCodec.parse(packet))
    }
}
