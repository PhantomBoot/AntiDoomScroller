package com.antidoomscroller.core.dns

/** A parsed IPv4/IPv6 UDP datagram read off the VPN tunnel. */
data class IpUdpPacket(
    val ipVersion: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpUdpPacket) return false
        return ipVersion == other.ipVersion &&
            sourcePort == other.sourcePort &&
            destinationPort == other.destinationPort &&
            sourceAddress.contentEquals(other.sourceAddress) &&
            destinationAddress.contentEquals(other.destinationAddress) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = ipVersion
        result = 31 * result + sourceAddress.contentHashCode()
        result = 31 * result + destinationAddress.contentHashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Hand-rolled IPv4/IPv6 + UDP reader and writer.
 *
 * The tunnel routes nothing but the filter's own DNS addresses, so this only ever sees DNS
 * datagrams - which keeps it to two header layouts and two checksums instead of a TCP/IP stack.
 */
object IpUdpCodec {

    const val PROTOCOL_UDP = 17
    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val UDP_HEADER = 8

    fun parse(packet: ByteArray, length: Int = packet.size): IpUdpPacket? {
        if (length < 1) return null
        return when ((packet[0].toInt() and 0xF0) ushr 4) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray, length: Int): IpUdpPacket? {
        if (length < IPV4_MIN_HEADER) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER || length < headerLength + UDP_HEADER) return null
        if ((packet[9].toInt() and 0xFF) != PROTOCOL_UDP) return null
        // Fragmented datagrams cannot be filtered on their own; let them pass untouched.
        val fragmentOffset = (((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF))
        if (fragmentOffset != 0) return null

        val totalLength = readUShort(packet, 2).coerceAtMost(length)
        val source = packet.copyOfRange(12, 16)
        val destination = packet.copyOfRange(16, 20)
        val udpOffset = headerLength
        val udpLength = readUShort(packet, udpOffset + 4)
        val payloadLength = (udpLength - UDP_HEADER).coerceIn(0, totalLength - udpOffset - UDP_HEADER)
        return IpUdpPacket(
            ipVersion = 4,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = readUShort(packet, udpOffset),
            destinationPort = readUShort(packet, udpOffset + 2),
            payload = packet.copyOfRange(udpOffset + UDP_HEADER, udpOffset + UDP_HEADER + payloadLength),
        )
    }

    private fun parseIpv6(packet: ByteArray, length: Int): IpUdpPacket? {
        if (length < IPV6_HEADER + UDP_HEADER) return null
        // Extension headers are rare for DNS; anything that is not plain UDP is passed through.
        if ((packet[6].toInt() and 0xFF) != PROTOCOL_UDP) return null
        val payloadLength = readUShort(packet, 4)
        val source = packet.copyOfRange(8, 24)
        val destination = packet.copyOfRange(24, 40)
        val udpOffset = IPV6_HEADER
        val udpLength = readUShort(packet, udpOffset + 4)
        val available = (length - udpOffset - UDP_HEADER).coerceAtLeast(0)
        val declared = (minOf(udpLength, payloadLength) - UDP_HEADER).coerceAtLeast(0)
        val size = minOf(declared, available)
        return IpUdpPacket(
            ipVersion = 6,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = readUShort(packet, udpOffset),
            destinationPort = readUShort(packet, udpOffset + 2),
            payload = packet.copyOfRange(udpOffset + UDP_HEADER, udpOffset + UDP_HEADER + size),
        )
    }

    /** Builds the datagram that answers [request], with source and destination swapped. */
    fun buildResponse(request: IpUdpPacket, payload: ByteArray): ByteArray =
        build(
            ipVersion = request.ipVersion,
            sourceAddress = request.destinationAddress,
            destinationAddress = request.sourceAddress,
            sourcePort = request.destinationPort,
            destinationPort = request.sourcePort,
            payload = payload,
        )

    fun build(
        ipVersion: Int,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        return if (ipVersion == 4) {
            val packet = ByteArray(IPV4_MIN_HEADER + udpLength)
            packet[0] = 0x45 // version 4, 5 x 32-bit words of header
            packet[1] = 0
            writeUShort(packet, 2, packet.size)
            writeUShort(packet, 4, 0) // identification
            writeUShort(packet, 6, 0x4000) // don't fragment
            packet[8] = 64 // ttl
            packet[9] = PROTOCOL_UDP.toByte()
            sourceAddress.copyInto(packet, 12, 0, 4)
            destinationAddress.copyInto(packet, 16, 0, 4)
            writeUShort(packet, 10, 0)
            writeUShort(packet, 10, internetChecksum(packet, 0, IPV4_MIN_HEADER))

            val udpOffset = IPV4_MIN_HEADER
            writeUShort(packet, udpOffset, sourcePort)
            writeUShort(packet, udpOffset + 2, destinationPort)
            writeUShort(packet, udpOffset + 4, udpLength)
            writeUShort(packet, udpOffset + 6, 0)
            payload.copyInto(packet, udpOffset + UDP_HEADER)
            writeUShort(
                packet,
                udpOffset + 6,
                udpChecksum(packet, udpOffset, udpLength, sourceAddress, destinationAddress, ipVersion = 4),
            )
            packet
        } else {
            val packet = ByteArray(IPV6_HEADER + udpLength)
            packet[0] = 0x60 // version 6
            writeUShort(packet, 4, udpLength)
            packet[6] = PROTOCOL_UDP.toByte()
            packet[7] = 64 // hop limit
            sourceAddress.copyInto(packet, 8, 0, 16)
            destinationAddress.copyInto(packet, 24, 0, 16)

            val udpOffset = IPV6_HEADER
            writeUShort(packet, udpOffset, sourcePort)
            writeUShort(packet, udpOffset + 2, destinationPort)
            writeUShort(packet, udpOffset + 4, udpLength)
            writeUShort(packet, udpOffset + 6, 0)
            payload.copyInto(packet, udpOffset + UDP_HEADER)
            var checksum = udpChecksum(packet, udpOffset, udpLength, sourceAddress, destinationAddress, ipVersion = 6)
            // RFC 768: a computed zero is transmitted as all ones. Mandatory over IPv6.
            if (checksum == 0) checksum = 0xFFFF
            writeUShort(packet, udpOffset + 6, checksum)
            packet
        }
    }

    /** RFC 1071 ones-complement sum. */
    fun internetChecksum(buffer: ByteArray, offset: Int, length: Int, initial: Long = 0): Int {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buffer[i].toInt() and 0xFF) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun udpChecksum(
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        ipVersion: Int,
    ): Int {
        var sum = 0L
        val addressLength = if (ipVersion == 4) 4 else 16
        for (address in listOf(sourceAddress, destinationAddress)) {
            var i = 0
            while (i + 1 < addressLength) {
                sum += ((address[i].toInt() and 0xFF) shl 8) or (address[i + 1].toInt() and 0xFF)
                i += 2
            }
        }
        sum += PROTOCOL_UDP.toLong()
        sum += udpLength.toLong()
        return internetChecksum(packet, udpOffset, udpLength, sum)
    }

    private fun readUShort(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun writeUShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
