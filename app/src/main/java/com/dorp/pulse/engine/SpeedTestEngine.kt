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
    val colo: String,
    val protocol: String,
) {
    /** "Tel Aviv (TLV)" when the data-center code is known, otherwise just the code. */
    val serverLabel: String
        get() = COLO_NAMES[colo]?.let { "$it ($colo)" } ?: colo
}

data class PingResult(val pingMs: Double, val jitterMs: Double)

/**
 * Measures latency, download and upload against Cloudflare's public speed test
 * endpoints. Throughput uses several parallel HTTP streams for a fixed time and
 * discards the warm-up window, so TCP slow start doesn't drag the result down.
 */
class SpeedTestEngine {

    suspend fun fetchServerInfo(): ServerInfo = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(get("$BASE/meta"))
            val colo = json.optJSONObject("colo")?.optString("iata") ?: json.optString("colo")
            ServerInfo(
                ip = json.optString("clientIp"),
                isp = json.optString("asOrganization"),
                colo = colo.orEmpty(),
                protocol = json.optString("httpProtocol"),
            )
        } catch (e: Exception) {
            // Fallback: the plain-text trace endpoint has less detail but is very stable.
            val trace = get("$BASE/cdn-cgi/trace").lines()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()
            ServerInfo(
                ip = trace["ip"].orEmpty(),
                isp = "",
                colo = trace["colo"].orEmpty(),
                protocol = trace["http"].orEmpty(),
            )
        }
    }

    suspend fun measurePing(count: Int = 12, onSample: (ms: Double, progress: Float) -> Unit): PingResult =
        withContext(Dispatchers.IO) {
            // The first request pays for DNS + TCP + TLS; keep-alive reuses that connection afterwards.
            runCatching { singlePing() }
            val samples = ArrayList<Double>(count)
            repeat(count) { i ->
                ensureActive()
                val ms = singlePing()
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
        durationMs: Long,
        streams: Int = 6,
        onSample: (mbps: Double, progress: Float) -> Unit,
    ): Double = measureTransfer(durationMs, streams, onSample) { counter, deadline, conns ->
        val c = open("$BASE/__down?bytes=$DOWN_CHUNK")
        conns += c
        try {
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
        durationMs: Long,
        streams: Int = 4,
        onSample: (mbps: Double, progress: Float) -> Unit,
    ): Double = measureTransfer(durationMs, streams, onSample) { counter, deadline, conns ->
        val c = open("$BASE/__up", "POST")
        c.doOutput = true
        c.setFixedLengthStreamingMode(UP_CHUNK)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        conns += c
        try {
            c.outputStream.use { out ->
                var sent = 0L
                while (sent < UP_CHUNK) {
                    if (System.nanoTime() >= deadline) {
                        // Abort mid-body; the IOException that follows is expected.
                        c.disconnect()
                        return@measureTransfer
                    }
                    val n = min(UPLOAD_BUFFER.size.toLong(), UP_CHUNK - sent).toInt()
                    out.write(UPLOAD_BUFFER, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                }
            }
            c.responseCode
            c.inputStream.use { it.readBytes() }
        } finally {
            conns -= c
        }
    }

    private suspend fun measureTransfer(
        durationMs: Long,
        streams: Int,
        onSample: (Double, Float) -> Unit,
        worker: (counter: AtomicLong, deadlineNs: Long, conns: MutableList<HttpURLConnection>) -> Unit,
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
                        worker(bytes, end, conns)
                    } catch (e: IOException) {
                        lastError = e
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

    private fun singlePing(): Double {
        val c = open("$BASE/__down?bytes=0")
        val t0 = System.nanoTime()
        c.responseCode
        val t1 = System.nanoTime()
        c.inputStream.use { it.readBytes() }
        // Cloudflare reports its own processing time; subtract it to get network round trip.
        val serverMs = c.getHeaderField("Server-Timing")
            ?.let { Regex("dur=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: 0.0
        return max(0.1, (t1 - t0) / 1e6 - serverMs)
    }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
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
        private const val BASE = "https://speed.cloudflare.com"
        private const val USER_AGENT = "Pulse-Android/1.0"
        private const val DOWN_CHUNK = 50_000_000L
        private const val UP_CHUNK = 16_000_000L
        private const val SAMPLE_MS = 150L
        private val UPLOAD_BUFFER = Random.nextBytes(32 * 1024)
    }
}

private val COLO_NAMES = mapOf(
    "TLV" to "Tel Aviv", "HFA" to "Haifa", "AMM" to "Amman", "LCA" to "Larnaca", "ATH" to "Athens",
    "IST" to "Istanbul", "CAI" to "Cairo", "DXB" to "Dubai", "FRA" to "Frankfurt", "AMS" to "Amsterdam",
    "LHR" to "London", "CDG" to "Paris", "MRS" to "Marseille", "MXP" to "Milan", "FCO" to "Rome",
    "VIE" to "Vienna", "WAW" to "Warsaw", "MAD" to "Madrid", "ZRH" to "Zurich", "PRG" to "Prague",
    "BUD" to "Budapest", "OTP" to "Bucharest", "SOF" to "Sofia", "ARN" to "Stockholm", "CPH" to "Copenhagen",
    "JFK" to "New York", "EWR" to "Newark", "IAD" to "Ashburn", "ORD" to "Chicago", "DFW" to "Dallas",
    "LAX" to "Los Angeles", "SJC" to "San Jose", "SEA" to "Seattle", "MIA" to "Miami", "ATL" to "Atlanta",
    "YYZ" to "Toronto", "GRU" to "São Paulo", "NRT" to "Tokyo", "HKG" to "Hong Kong", "SIN" to "Singapore",
    "BOM" to "Mumbai", "SYD" to "Sydney", "JNB" to "Johannesburg",
)
