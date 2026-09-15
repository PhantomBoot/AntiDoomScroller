package com.antidoomscroller.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.antidoomscroller.core.dns.DnsFilterEngine
import com.antidoomscroller.core.dns.DnsVerdict
import com.antidoomscroller.core.dns.IpUdpCodec
import com.antidoomscroller.core.dns.IpUdpPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The packet loop behind the adult filter.
 *
 * The tunnel routes nothing except the filter's own DNS addresses, so ordinary traffic never
 * enters this process at all - no browsing goes through the app, and there is nothing here that
 * could log it if it did. A blocked name is answered locally and never leaves the phone;
 * everything else is handed to the resolver the network already gave you.
 */
class DnsTunnel(
    private val service: VpnService,
    private val descriptor: ParcelFileDescriptor,
    private val engine: DnsFilterEngine,
    /** Fake in-tunnel DNS address (as a plain host string) to the real resolver behind it. */
    private val upstreamByFakeAddress: Map<String, InetAddress>,
    private val onError: (Throwable) -> Unit = {},
) : Runnable {

    private val executor: ExecutorService = Executors.newFixedThreadPool(WORKER_THREADS)

    @Volatile
    private var running = true

    override fun run() {
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val buffer = ByteArray(MAX_PACKET_SIZE)
                    while (running) {
                        val length = try {
                            input.read(buffer)
                        } catch (io: IOException) {
                            if (running) onError(io)
                            break
                        }
                        if (length <= 0) continue
                        dispatch(buffer, length, output)
                    }
                }
            }
        } catch (io: IOException) {
            if (running) onError(io)
        } finally {
            executor.shutdownNow()
        }
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        runCatching { descriptor.close() }
    }

    private fun dispatch(buffer: ByteArray, length: Int, output: OutputStream) {
        val packet = IpUdpCodec.parse(buffer, length) ?: return
        if (packet.destinationPort != DNS_PORT) return

        when (val verdict = engine.evaluate(packet.payload, nowMs = System.currentTimeMillis())) {
            is DnsVerdict.Blocked -> write(output, IpUdpCodec.buildResponse(packet, verdict.response))
            is DnsVerdict.Forward, DnsVerdict.Passthrough -> forward(packet, output)
        }
    }

    private fun forward(packet: IpUdpPacket, output: OutputStream) {
        val upstream = upstreamFor(packet.destinationAddress) ?: return
        try {
            executor.execute { query(packet, upstream, output) }
        } catch (_: RejectedExecutionException) {
            // Shutting down, or the phone is asking far more than the pool can carry: dropping
            // the query makes the resolver retry, which is the correct failure here.
        }
    }

    private fun query(packet: IpUdpPacket, upstream: InetAddress, output: OutputStream) {
        try {
            DatagramSocket().use { socket ->
                // Without this the query would loop straight back into our own tunnel.
                if (!service.protect(socket)) return
                socket.soTimeout = UPSTREAM_TIMEOUT_MS
                socket.send(DatagramPacket(packet.payload, packet.payload.size, upstream, DNS_PORT))

                val buffer = ByteArray(MAX_RESPONSE_SIZE)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                write(output, IpUdpCodec.buildResponse(packet, buffer.copyOf(reply.length)))
            }
        } catch (_: IOException) {
            // A dropped answer looks like ordinary packet loss to the caller, which retries.
        }
    }

    private fun upstreamFor(destination: ByteArray): InetAddress? = runCatching {
        upstreamByFakeAddress[InetAddress.getByAddress(destination).hostAddress]
    }.getOrNull()

    private fun write(output: OutputStream, packet: ByteArray) {
        synchronized(this) {
            runCatching {
                output.write(packet)
                output.flush()
            }
        }
    }

    private companion object {
        const val DNS_PORT = 53
        const val MAX_PACKET_SIZE = 32_767
        const val MAX_RESPONSE_SIZE = 4_096
        const val UPSTREAM_TIMEOUT_MS = 5_000
        const val WORKER_THREADS = 4
    }
}
