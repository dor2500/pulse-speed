package com.dorp.pulse.engine

import android.content.Context
import android.net.ConnectivityManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileDescriptor
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** What the user asked for. AUTO tries ICMP, then the system ping tool, then a TCP connect. */
enum class PingMode { AUTO, TCP, DNS }

/** What actually measured the round trip. */
enum class PingMethod { ICMP, SYSTEM, TCP, DNS }

enum class TargetKind { GATEWAY, DNS, CUSTOM }

data class PingTarget(val host: String, val label: String, val kind: TargetKind)

sealed interface PingEvent {
    /** The host resolved and a method was chosen; sent once before any replies. */
    data class Started(val address: String, val method: PingMethod) : PingEvent
    data class Reply(val seq: Int, val ms: Double) : PingEvent
    data class Lost(val seq: Int) : PingEvent
}

class PingException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { RESOLVE_FAILED, NO_METHOD }
}

/** Well-known public resolvers offered as one-tap targets. */
val PublicDns = listOf(
    PingTarget("1.1.1.1", "Cloudflare", TargetKind.DNS),
    PingTarget("8.8.8.8", "Google", TargetKind.DNS),
    PingTarget("9.9.9.9", "Quad9", TargetKind.DNS),
    PingTarget("208.67.222.222", "OpenDNS", TargetKind.DNS),
)

/** The router and the DNS servers the current network handed out (DHCP / carrier). */
data class LocalTargets(val gateway: String?, val dns: List<String>)

fun localTargets(context: Context): LocalTargets = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val lp = cm?.getLinkProperties(cm.activeNetwork) ?: return LocalTargets(null, emptyList())
    val gateway = lp.routes
        // RouteInfo.hasGateway() is API 29+; a default route without a gateway reports 0.0.0.0 / ::
        .filter { it.isDefaultRoute && it.gateway?.isAnyLocalAddress == false }
        .map { it.gateway }
        .sortedBy { if (it is Inet4Address) 0 else 1 }
        .firstOrNull()?.hostAddress
    val dns = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.distinct()
    LocalTargets(gateway, dns)
}.getOrDefault(LocalTargets(null, emptyList()))

/**
 * Latency probes that need no root.
 *
 * - ICMP uses Linux "ping sockets" (SOCK_DGRAM + IPPROTO_ICMP), which Android allows for every app.
 * - If a device blocks those, the system `ping` binary is used one probe at a time.
 * - TCP measures the three-way handshake; a refused port still answers, so it still counts as a reply.
 * - DNS times a real query to the target on UDP port 53.
 */
class PingEngine {

    fun ping(
        host: String,
        mode: PingMode,
        count: Int, // 0 = until cancelled
        intervalMs: Long = 1000,
        timeoutMs: Int = 2000,
    ): Flow<PingEvent> = flow {
        val address = resolve(host) ?: throw PingException(PingException.Reason.RESOLVE_FAILED)
        val probe: Probe = when (mode) {
            PingMode.DNS -> DnsProbe(address)
            PingMode.TCP -> TcpProbe(address, tcpPorts(address))
            PingMode.AUTO -> IcmpProbe.open(address)
                ?: SystemPingProbe(address).takeIf { it.works(timeoutMs) }
                ?: TcpProbe(address, tcpPorts(address))
        }
        try {
            emit(PingEvent.Started(address.hostAddress?.substringBefore('%') ?: host, probe.method))
            var seq = 0
            while (count == 0 || seq < count) {
                coroutineContext.ensureActive()
                seq++
                val started = System.nanoTime()
                val ms = probe.once(seq, timeoutMs)
                emit(if (ms != null) PingEvent.Reply(seq, ms) else PingEvent.Lost(seq))
                if (count != 0 && seq >= count) break
                val spent = (System.nanoTime() - started) / 1_000_000
                delay((intervalMs - spent).coerceAtLeast(0))
            }
        } finally {
            probe.close()
        }
    }.flowOn(Dispatchers.IO)

    /** Name lookups can hang in the OS resolver; give up after 5 s instead of waiting forever. */
    private suspend fun resolve(host: String): InetAddress? = coroutineScope {
        val lookup = async(Dispatchers.IO) {
            runCatching { InetAddress.getAllByName(host.trim()) }.getOrNull()
        }
        withTimeoutOrNull(5000) { lookup.await() }
            ?.let { all -> all.firstOrNull { it is Inet4Address } ?: all.firstOrNull() }
    }

    private fun tcpPorts(address: InetAddress): List<Int> =
        if (PublicDns.any { it.host == address.hostAddress }) listOf(53, 443) else listOf(443, 80, 53)

    private interface Probe {
        val method: PingMethod
        /** Round trip in ms, or null when nothing came back within the timeout. */
        fun once(seq: Int, timeoutMs: Int): Double?
        fun close() {}
    }

    private class IcmpProbe(private val fd: FileDescriptor, private val address: InetAddress) : Probe {
        override val method = PingMethod.ICMP
        private val v6 = address is Inet6Address
        private val payload = ByteArray(32) { (it + 0x20).toByte() }

        override fun once(seq: Int, timeoutMs: Int): Double? {
            val packet = ByteArray(8 + payload.size)
            packet[0] = (if (v6) 128 else 8).toByte() // echo request
            packet[6] = (seq shr 8).toByte()
            packet[7] = seq.toByte()
            payload.copyInto(packet, 8)
            // The kernel fills in the identifier and checksum for ping sockets.
            val started = System.nanoTime()
            try {
                Os.sendto(fd, packet, 0, packet.size, 0, address, 0)
            } catch (_: ErrnoException) {
                return null // e.g. ENETUNREACH / EHOSTUNREACH
            }
            val buf = ByteArray(1500)
            while (true) {
                val left = timeoutMs - ((System.nanoTime() - started) / 1_000_000).toInt()
                if (left <= 0) return null
                val pfd = StructPollfd().apply { this.fd = this@IcmpProbe.fd; events = OsConstants.POLLIN.toShort() }
                val ready = try { Os.poll(arrayOf(pfd), left) } catch (_: ErrnoException) { return null }
                if (ready <= 0) return null
                val n = try { Os.recvfrom(fd, buf, 0, buf.size, 0, null) } catch (_: ErrnoException) { return null }
                if (n < 8) continue
                val type = buf[0].toInt() and 0xff
                val replySeq = ((buf[6].toInt() and 0xff) shl 8) or (buf[7].toInt() and 0xff)
                if (type == (if (v6) 129 else 0) && replySeq == (seq and 0xffff)) {
                    return (System.nanoTime() - started) / 1_000_000.0
                }
                // Anything else is a stale reply from an earlier sequence: keep waiting.
            }
        }

        override fun close() {
            runCatching { Os.close(fd) }
        }

        companion object {
            fun open(address: InetAddress): IcmpProbe? = runCatching {
                val v6 = address is Inet6Address
                val fd = Os.socket(
                    if (v6) OsConstants.AF_INET6 else OsConstants.AF_INET,
                    OsConstants.SOCK_DGRAM,
                    if (v6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP,
                )
                IcmpProbe(fd, address)
            }.getOrNull()
        }
    }

    private class SystemPingProbe(private val address: InetAddress) : Probe {
        override val method = PingMethod.SYSTEM
        private val binary = if (address is Inet6Address) "/system/bin/ping6" else "/system/bin/ping"
        private val time = Regex("""time[=<]([\d.]+)\s*ms""")

        /** True when the binary exists and runs (one probe may still be lost on a quiet host). */
        fun works(timeoutMs: Int): Boolean = runCatching {
            val p = start(timeoutMs)
            p.waitFor(timeoutMs + 1000L, TimeUnit.MILLISECONDS).also { if (!it) p.destroy() }
            p.exitValue() in 0..1 // 1 = no reply, but the tool itself works
        }.getOrDefault(false)

        private fun start(timeoutMs: Int): Process {
            val seconds = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
            return ProcessBuilder(binary, "-n", "-c", "1", "-W", "$seconds", address.hostAddress!!)
                .redirectErrorStream(true).start()
        }

        override fun once(seq: Int, timeoutMs: Int): Double? = runCatching {
            val p = start(timeoutMs)
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutMs + 1000L, TimeUnit.MILLISECONDS)) p.destroy()
            time.find(out)?.groupValues?.get(1)?.toDouble()
        }.getOrNull()
    }

    private class TcpProbe(private val address: InetAddress, private val ports: List<Int>) : Probe {
        override val method = PingMethod.TCP
        /** The first port that answers is kept for the rest of the run. */
        private var port: Int? = null

        override fun once(seq: Int, timeoutMs: Int): Double? {
            port?.let { return connect(it, timeoutMs) }
            for (p in ports) {
                val ms = connect(p, timeoutMs)
                if (ms != null) { port = p; return ms }
            }
            return null
        }

        private fun connect(port: Int, timeoutMs: Int): Double? {
            val started = System.nanoTime()
            return try {
                Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }
                (System.nanoTime() - started) / 1_000_000.0
            } catch (e: ConnectException) {
                // A refusal is the host's own RST, so it is a real round trip; anything else is not.
                if (e.message?.contains("ECONNREFUSED") == true) (System.nanoTime() - started) / 1_000_000.0 else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private class DnsProbe(private val address: InetAddress) : Probe {
        override val method = PingMethod.DNS
        private val socket = DatagramSocket()

        override fun once(seq: Int, timeoutMs: Int): Double? = runCatching {
            val id = Random.nextInt(0x10000)
            val query = dnsQuery(id, "example.com")
            socket.soTimeout = timeoutMs
            val started = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, address, 53))
            val buf = ByteArray(512)
            while (true) {
                val left = timeoutMs - ((System.nanoTime() - started) / 1_000_000).toInt()
                if (left <= 0) return@runCatching null
                socket.soTimeout = left
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    return@runCatching null
                }
                val replyId = ((buf[0].toInt() and 0xff) shl 8) or (buf[1].toInt() and 0xff)
                val isResponse = buf[2].toInt() and 0x80 != 0
                if (packet.length >= 12 && isResponse && replyId == id) {
                    return@runCatching (System.nanoTime() - started) / 1_000_000.0
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }.getOrNull()

        override fun close() = socket.close()

        private fun dnsQuery(id: Int, name: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
            name.split('.').forEach { label ->
                out.write(label.length)
                out.write(label.toByteArray(Charsets.US_ASCII))
            }
            out.write(byteArrayOf(0, 0, 1, 0, 1)) // root, QTYPE=A, QCLASS=IN
            return out.toByteArray()
        }
    }
}
