package com.antidoomscroller.core.dns

/** DNS record types this filter cares about. */
object DnsType {
    const val A = 1
    const val AAAA = 28
    const val HTTPS = 65
}

/** The parts of a DNS query the filter needs. */
data class DnsQuery(
    val id: Int,
    val flags: Int,
    val questionCount: Int,
    val name: String,
    val type: Int,
    val klass: Int,
    /** Offset just past the question section, where an answer would be appended. */
    val questionEnd: Int,
) {
    val isStandardQuery: Boolean get() = (flags and 0x8000) == 0 && ((flags shr 11) and 0x0F) == 0
}

/**
 * Minimal DNS wire-format reader/writer.
 *
 * Only what a filtering resolver needs: read the question, and synthesise a refusal. Everything
 * else is forwarded to the upstream resolver untouched, so the app never has to understand the
 * rest of the protocol.
 */
object DnsCodec {

    private const val HEADER_SIZE = 12
    private const val MAX_NAME_LENGTH = 255
    private const val FLAG_RESPONSE = 0x8000
    private const val FLAG_RECURSION_AVAILABLE = 0x0080
    private const val RCODE_MASK = 0x000F
    private const val RCODE_NXDOMAIN = 3

    fun parseQuery(payload: ByteArray, length: Int = payload.size): DnsQuery? {
        if (length < HEADER_SIZE) return null
        val id = readUShort(payload, 0)
        val flags = readUShort(payload, 2)
        val questionCount = readUShort(payload, 4)
        if (questionCount < 1) return null

        val name = StringBuilder()
        var offset = HEADER_SIZE
        var nameLength = 0
        while (true) {
            if (offset >= length) return null
            val labelLength = payload[offset].toInt() and 0xFF
            // Compression pointers are not legal in a question we originate; refuse to guess.
            if (labelLength and 0xC0 != 0) return null
            offset++
            if (labelLength == 0) break
            if (offset + labelLength > length) return null
            nameLength += labelLength + 1
            if (nameLength > MAX_NAME_LENGTH) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in 0 until labelLength) {
                name.append((payload[offset + i].toInt() and 0xFF).toChar())
            }
            offset += labelLength
        }
        if (offset + 4 > length) return null
        val type = readUShort(payload, offset)
        val klass = readUShort(payload, offset + 2)
        return DnsQuery(
            id = id,
            flags = flags,
            questionCount = questionCount,
            name = name.toString().lowercase(),
            type = type,
            klass = klass,
            questionEnd = offset + 4,
        )
    }

    /** "That name does not exist." The fastest failure a browser can render. */
    fun buildNxDomain(query: DnsQuery, request: ByteArray): ByteArray {
        val response = request.copyOf(query.questionEnd)
        writeHeader(response, query, rcode = RCODE_NXDOMAIN, answerCount = 0)
        return response
    }

    /** NOERROR with no records: used for record types that have no meaningful null answer. */
    fun buildEmptyAnswer(query: DnsQuery, request: ByteArray): ByteArray {
        val response = request.copyOf(query.questionEnd)
        writeHeader(response, query, rcode = 0, answerCount = 0)
        return response
    }

    /**
     * NOERROR pointing at the null address (0.0.0.0 / ::), which some browsers report more
     * clearly than a missing name.
     */
    fun buildNullAddress(query: DnsQuery, request: ByteArray, ttlSeconds: Int = 60): ByteArray {
        val rdLength = when (query.type) {
            DnsType.A -> 4
            DnsType.AAAA -> 16
            else -> return buildEmptyAnswer(query, request)
        }
        val answerSize = 2 + 2 + 2 + 4 + 2 + rdLength
        val response = ByteArray(query.questionEnd + answerSize)
        request.copyInto(response, 0, 0, query.questionEnd)
        writeHeader(response, query, rcode = 0, answerCount = 1)

        var offset = query.questionEnd
        // Name: pointer back to the question at offset 12.
        response[offset] = 0xC0.toByte()
        response[offset + 1] = 0x0C
        offset += 2
        writeUShort(response, offset, query.type); offset += 2
        writeUShort(response, offset, query.klass); offset += 2
        writeUInt(response, offset, ttlSeconds.toLong()); offset += 4
        writeUShort(response, offset, rdLength)
        // RDATA is already zero-filled, which is exactly the null address.
        return response
    }

    private fun writeHeader(response: ByteArray, query: DnsQuery, rcode: Int, answerCount: Int) {
        writeUShort(response, 0, query.id)
        var flags = query.flags or FLAG_RESPONSE or FLAG_RECURSION_AVAILABLE
        flags = (flags and RCODE_MASK.inv()) or (rcode and RCODE_MASK)
        writeUShort(response, 2, flags)
        writeUShort(response, 4, query.questionCount)
        writeUShort(response, 6, answerCount)
        writeUShort(response, 8, 0) // authority
        writeUShort(response, 10, 0) // additional
    }

    fun rcodeOf(payload: ByteArray): Int =
        if (payload.size < HEADER_SIZE) -1 else readUShort(payload, 2) and RCODE_MASK

    fun idOf(payload: ByteArray): Int = if (payload.size < 2) -1 else readUShort(payload, 0)

    private fun readUShort(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun writeUShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
