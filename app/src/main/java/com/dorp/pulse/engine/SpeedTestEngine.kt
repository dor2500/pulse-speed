package com.dorp.pulse.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class ServerInfo(
    val ip: String,
    val isp: String,
    /** Human-readable test server, e.g. "Cloudflare · Tel Aviv (TLV)". */
    val serverLabel: String,
    val protocol: String,
)

/**
 * The connections and raw sockets one transfer phase opened, so the phase can tear them all down at its
 * deadline. Closing the socket is what reliably interrupts a thread blocked in write().
 */
class ConnTracker {
    private val conns = Collections.synchronizedList(mutableListOf<HttpURLConnection>())
    private val sockets = Collections.synchronizedSet(mutableSetOf<Socket>())

    val factory: SSLSocketFactory = object : SSLSocketFactory() {
        private val base = HttpsURLConnection.getDefaultSSLSocketFactory()
        private fun <T : Socket> track(s: T): T { sockets += s; return s }
        override fun getDefaultCipherSuites(): Array<String> = base.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites
        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket = track(base.createSocket(s, host, port, autoClose)).also { if (s != null) sockets += s }
        override fun createSocket(host: String?, port: Int): Socket = track(base.createSocket(host, port))
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = track(base.createSocket(host, port, localHost, localPort))
        override fun createSocket(host: InetAddress?, port: Int): Socket = track(base.createSocket(host, port))
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = track(base.createSocket(address, port, localAddress, localPort))
        override fun createSocket(): Socket = track(base.createSocket())
    }

    operator fun plusAssign(c: HttpURLConnection) { conns += c }
    operator fun minusAssign(c: HttpURLConnection) { conns -= c }
    val size: Int get() = conns.size

    fun closeAll() {
        synchronized(conns) { conns.toList() }.forEach { runCatching { it.disconnect() } }
        synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } }
    }
}

/** Idle latency: median, jitter, the spread of the samples and how many probes got no answer. */
data class PingResult(
    val pingMs: Double,
    val jitterMs: Double,
    val minMs: Double = pingMs,
    val maxMs: Double = pingMs,
    val lossPercent: Double = 0.0,
)

/** Latency measured while a transfer saturates the line (bufferbloat), summarised like [PingResult]. */
data class LoadedPing(val medianMs: Double, val minMs: Double, val maxMs: Double, val jitterMs: Double)

data class TransferResult(
    val mbps: Double,
    val loadedPingMs: Double? = null,
    val loaded: LoadedPing? = null,
    /** Everything the streams moved, warm-up included: the data this phase used. */
    val bytes: Long = 0,
    val streams: Int = 0,
)

private fun median(values: List<Double>): Double {
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

fun calculateBufferbloatGrade(baselinePingMs: Double, downLoadedPingMs: Double?, upLoadedPingMs: Double?): String {
    val deltaDown = downLoadedPingMs?.let { max(0.0, it - baselinePingMs) } ?: 0.0
    val deltaUp = upLoadedPingMs?.let { max(0.0, it - baselinePingMs) } ?: 0.0
    val worstDelta = max(deltaDown, deltaUp)
    return when {
        worstDelta <= 5.0 -> "A+"
        worstDelta <= 15.0 -> "A"
        worstDelta <= 30.0 -> "B"
        worstDelta <= 60.0 -> "C"
        worstDelta <= 150.0 -> "D"
        else -> "F"
    }
}

/** Where a test sends its traffic. Every source boils down to these four things. */
class Endpoints(
    val ping: String,
    val download: (stream: Int) -> String,
    val upload: (stream: Int) -> String,
    val uploadChunk: Long = 8_000_000L,
    val downStreams: Int = 6,
    val upStreams: Int = 4,
    /** Set for M-Lab: the transfer runs over NDT7 WebSockets instead of parallel HTTP streams. */
    val ndt7: Ndt7Target? = null,
)

class PreparedTest(val info: ServerInfo, val endpoints: Endpoints)

/**
 * Measures latency, download and upload against any [Endpoints]. Throughput uses
 * several parallel HTTP streams for a fixed time and discards the warm-up window,
 * so TCP slow start doesn't drag the result down.
 */
class SpeedTestEngine {

    suspend fun prepare(source: TestSource, choice: ServerOption?): PreparedTest = Sources.prepare(source, choice, this)

    suspend fun listServers(source: TestSource, query: String): List<ServerOption> =
        Sources.listServers(source, query, this)

    /** Latency to a server for the picker, or null when it doesn't answer. */
    suspend fun ping(option: ServerOption): Double? = withContext(Dispatchers.IO) {
        runCatching {
            fun hit(): Pair<Int, Double> {
                val c = open(option.pingUrl).apply { connectTimeout = 3000; readTimeout = 3000 }
                val t0 = System.nanoTime()
                val code = c.responseCode
                val ms = (System.nanoTime() - t0) / 1e6
                runCatching { c.inputStream.use { it.readBytes() } } // drain so keep-alive reuses the socket
                return code to ms
            }
            hit() // pays for DNS + TCP + TLS
            val (code, ms) = hit()
            if (code in 200..299) ms else null
        }.getOrNull()
    }

    suspend fun measurePing(
        ep: Endpoints,
        count: Int = 12,
        onSample: (ms: Double, progress: Float) -> Unit,
    ): PingResult = withContext(Dispatchers.IO) {
        ep.ndt7?.let { return@withContext Ndt7.ping(it.host, count, onSample) }
        // The first request pays for DNS + TCP + TLS; keep-alive reuses that connection afterwards.
        runCatching { singlePing(ep.ping) }
        val samples = ArrayList<Double>(count)
        var lost = 0
        repeat(count) { i ->
            ensureActive()
            // A probe that fails or times out counts as packet loss instead of failing the whole test.
            val ms = runCatching { singlePing(ep.ping) }.getOrNull()
            ensureActive()
            if (ms != null) { samples += ms; onSample(ms, (i + 1f) / count) } else lost++
        }
        if (samples.isEmpty()) throw IOException("No ping replies")
        val median = median(samples)
        // A sample that paid for a reconnect would dominate jitter, so leave clear outliers out.
        val limit = max(median * 2.5, median + 40)
        val steady = samples.filter { it <= limit }
        val jitter = steady.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0
        PingResult(median, jitter, steady.min(), steady.max(), lost * 100.0 / count)
    }

    suspend fun measureDownload(
        ep: Endpoints,
        durationMs: Long,
        onSample: (mbps: Double, progress: Float) -> Unit,
    ): TransferResult = if (ep.ndt7 != null) withContext(Dispatchers.IO) { Ndt7.download(ep.ndt7, durationMs, onSample) }
    else measureTransfer(durationMs, ep.downStreams, ep.ping, onSample) { stream, counter, deadline, conns ->
        val c = open(ep.download(stream), tracker = conns)
        conns += c
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (System.nanoTime() >= deadline) {
                        // Drop the socket instead of draining the rest of the body.
                        c.disconnect()
                        break
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
            }
        } finally {
            conns -= c
        }
    }

    suspend fun measureUpload(
        ep: Endpoints,
        durationMs: Long,
        onSample: (mbps: Double, progress: Float) -> Unit,
    ): TransferResult = if (ep.ndt7 != null) withContext(Dispatchers.IO) { Ndt7.upload(ep.ndt7, durationMs, onSample) }
    else measureTransfer(durationMs, ep.upStreams, ep.ping, onSample) { stream, counter, deadline, conns ->
        val c = open(ep.upload(stream), "POST", conns)
        c.doOutput = true
        c.setFixedLengthStreamingMode(ep.uploadChunk)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        conns += c
        try {
            c.outputStream.use { out ->
                var sent = 0L
                while (sent < ep.uploadChunk) {
                    if (System.nanoTime() >= deadline) {
                        // Abort mid-body; the IOException that follows is expected.
                        c.disconnect()
                        return@measureTransfer
                    }
                    val n = min(UPLOAD_BUFFER.size.toLong(), ep.uploadChunk - sent).toInt()
                    out.write(UPLOAD_BUFFER, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                }
            }
            c.responseCode
            runCatching { c.inputStream.use { it.readBytes() } }
        } finally {
            conns -= c
        }
    }

    private suspend fun measureTransfer(
        durationMs: Long,
        streams: Int,
        pingUrl: String? = null,
        onSample: (Double, Float) -> Unit,
        worker: (stream: Int, counter: AtomicLong, deadlineNs: Long, conns: ConnTracker) -> Unit,
    ): TransferResult = coroutineScope {
        val bytes = AtomicLong(0)
        val conns = ConnTracker()
        // Workers live outside this scope so a stream stuck in blocking I/O can never hold the phase open.
        val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val start = System.nanoTime()
        val end = start + durationMs * 1_000_000
        var lastError: IOException? = null

        val workers = List(streams) { index ->
            workerScope.launch {
                delay(index * 150L) // stagger connection setup
                while (isActive && System.nanoTime() < end) {
                    try {
                        worker(index, bytes, end, conns)
                    } catch (e: IOException) {
                        lastError = e
                        if (System.nanoTime() < end) android.util.Log.w("Pulse", "stream $index: ${e.javaClass.simpleName}: ${e.message}")
                        if (System.nanoTime() < end) delay(150)
                    }
                }
            }
        }

        val warmupNs = min(2_000_000_000L, durationMs * 1_000_000 / 5)
        val loadedPings = Collections.synchronizedList(mutableListOf<Double>())
        val pingJob = if (pingUrl != null) {
            launch(Dispatchers.IO) {
                delay(warmupNs / 1_000_000 + 400)
                while (isActive && System.nanoTime() < end - 400_000_000) {
                    runCatching {
                        val ms = singlePing(pingUrl)
                        loadedPings += ms
                    }
                    delay(500) // ~2 probes a second: enough for low / high / jitter without adding load
                }
            }
        } else null

        var warmBytes = -1L
        var warmAt = start
        var lastBytes = 0L
        var lastAt = start
        var smooth = 0.0
        try {
            while (true) {
                delay(SAMPLE_MS)
                val now = System.nanoTime()
                val b = bytes.get()
                val instant = mbps(b - lastBytes, now - lastAt)
                smooth = if (smooth == 0.0) instant else smooth * 0.75 + instant * 0.25
                lastBytes = b
                lastAt = now
                if (warmBytes < 0 && now - start >= warmupNs) {
                    warmBytes = b
                    warmAt = now
                }
                onSample(smooth, ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f))
                if (now >= end) break
            }
        } finally {
            // Runs on cancel too, so pressing Stop closes every socket right away.
            pingJob?.cancel()
            // disconnect() alone does not wake a thread blocked in a socket write (a stalled upload),
            // so close the sockets themselves; then stop the workers without waiting for them.
            conns.closeAll()
            workers.forEach { it.cancel() }
            workerScope.cancel()
        }

        if (lastBytes == 0L) throw lastError ?: IOException("No data transferred")
        if (warmBytes < 0) {
            warmBytes = 0
            warmAt = start
        }
        val speed = max(0.0, mbps(lastBytes - warmBytes, lastAt - warmAt))
        val pings = synchronized(loadedPings) { loadedPings.toList() }
        val loaded = if (pings.isNotEmpty()) LoadedPing(
            medianMs = median(pings),
            minMs = pings.min(),
            maxMs = pings.max(),
            jitterMs = pings.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0,
        ) else null
        TransferResult(speed, loaded?.medianMs, loaded, lastBytes, streams)
    }

    private fun singlePing(url: String): Double {
        val c = open(url)
        val t0 = System.nanoTime()
        c.responseCode
        val t1 = System.nanoTime()
        runCatching { c.inputStream.use { it.readBytes() } }
        // Cloudflare reports its own processing time; subtract it to get the network round trip.
        val serverMs = c.getHeaderField("Server-Timing")
            ?.let { Regex("dur=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: 0.0
        return max(0.1, (t1 - t0) / 1e6 - serverMs)
    }

    /** One-shot latency probe used to pick the closest server; null when it doesn't answer in time. */
    internal fun probe(url: String, timeoutMs: Int): Double? = runCatching {
        val c = open(url).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }
        try {
            val t0 = System.nanoTime()
            val code = c.responseCode
            val ms = (System.nanoTime() - t0) / 1e6
            if (code in 200..299) ms else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Who the client is (IP and ISP), from Cloudflare's metadata endpoint. Best effort. */
    internal fun clientMeta(): JSONObject? = runCatching { JSONObject(get("$CLOUDFLARE/meta")) }.getOrNull()

    internal fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    internal fun open(url: String, method: String = "GET", tracker: ConnTracker? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            if (tracker != null && this is HttpsURLConnection) sslSocketFactory = tracker.factory
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            // speed.cloudflare.com/meta answers 403 {} without a Referer from its own site (since late 2026).
            if (url.startsWith(CLOUDFLARE)) setRequestProperty("Referer", "$CLOUDFLARE/")
        }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1e6

    companion object {
        internal const val CLOUDFLARE = "https://speed.cloudflare.com"
        private const val USER_AGENT = "Pulse-Android/1.4"
        private const val SAMPLE_MS = 150L
        private val UPLOAD_BUFFER = Random.nextBytes(32 * 1024)
    }
}
