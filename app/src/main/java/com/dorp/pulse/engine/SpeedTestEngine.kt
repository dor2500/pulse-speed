package com.dorp.pulse.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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

data class PingResult(val pingMs: Double, val jitterMs: Double)

/** Where a test sends its traffic. Every source boils down to these four things. */
class Endpoints(
    val ping: String,
    val download: (stream: Int) -> String,
    val upload: (stream: Int) -> String,
    val uploadChunk: Long = 8_000_000L,
    val downStreams: Int = 6,
    val upStreams: Int = 4,
)

class PreparedTest(val info: ServerInfo, val endpoints: Endpoints)

/**
 * Measures latency, download and upload against any [Endpoints]. Throughput uses
 * several parallel HTTP streams for a fixed time and discards the warm-up window,
 * so TCP slow start doesn't drag the result down.
 */
class SpeedTestEngine {

    suspend fun prepare(source: TestSource): PreparedTest = Sources.prepare(source, this)

    suspend fun measurePing(
        ep: Endpoints,
        count: Int = 12,
        onSample: (ms: Double, progress: Float) -> Unit,
    ): PingResult = withContext(Dispatchers.IO) {
        // The first request pays for DNS + TCP + TLS; keep-alive reuses that connection afterwards.
        runCatching { singlePing(ep.ping) }
        val samples = ArrayList<Double>(count)
        repeat(count) { i ->
            ensureActive()
            val ms = singlePing(ep.ping)
            ensureActive()
            samples += ms
            onSample(ms, (i + 1f) / count)
        }
        val sorted = samples.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        // A sample that paid for a reconnect would dominate jitter, so leave clear outliers out.
        val limit = max(median * 2.5, median + 40)
        val steady = samples.filter { it <= limit }
        val jitter = steady.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0
        PingResult(median, jitter)
    }

    suspend fun measureDownload(
        ep: Endpoints,
        durationMs: Long,
        onSample: (mbps: Double, progress: Float) -> Unit,
    ): Double = measureTransfer(durationMs, ep.downStreams, onSample) { stream, counter, deadline, conns ->
        val c = open(ep.download(stream))
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
    ): Double = measureTransfer(durationMs, ep.upStreams, onSample) { stream, counter, deadline, conns ->
        val c = open(ep.upload(stream), "POST")
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
        onSample: (Double, Float) -> Unit,
        worker: (stream: Int, counter: AtomicLong, deadlineNs: Long, conns: MutableList<HttpURLConnection>) -> Unit,
    ): Double = coroutineScope {
        val bytes = AtomicLong(0)
        val conns: MutableList<HttpURLConnection> = Collections.synchronizedList(mutableListOf())
        val start = System.nanoTime()
        val end = start + durationMs * 1_000_000
        var lastError: IOException? = null

        val workers = List(streams) { index ->
            launch(Dispatchers.IO) {
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
            synchronized(conns) { conns.toList() }.forEach { runCatching { it.disconnect() } }
            workers.forEach { it.cancel() }
        }

        if (lastBytes == 0L) throw lastError ?: IOException("No data transferred")
        if (warmBytes < 0) {
            warmBytes = 0
            warmAt = start
        }
        max(0.0, mbps(lastBytes - warmBytes, lastAt - warmAt))
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

    internal fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
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
