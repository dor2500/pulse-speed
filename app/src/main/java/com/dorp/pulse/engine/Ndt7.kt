package com.dorp.pulse.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One M-Lab NDT7 server, from the locate API. The URLs carry short-lived access tokens. */
data class Ndt7Target(val host: String, val downloadUrl: String, val uploadUrl: String)

/**
 * Measurement Lab's NDT7 protocol (github.com/m-lab/ndt-server/blob/main/spec/ndt7-protocol.md):
 * a single TLS WebSocket per direction. The server streams binary messages for download and reads ours
 * for upload; along the way it sends JSON measurements with its own TCP_INFO, whose RTT gives the loaded
 * latency without any extra probes.
 */
internal object Ndt7 {
    private const val SUBPROTOCOL = "net.measurementlab.ndt.v7"
    private const val MAX_QUEUE = 4L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Idle latency: TCP handshakes to the server's TLS port, since NDT servers don't serve a ping URL. */
    fun ping(host: String, count: Int, onSample: (Double, Float) -> Unit): PingResult {
        val samples = ArrayList<Double>()
        var lost = 0
        runCatching { Socket().use { it.connect(InetSocketAddress(host, 443), 3000) } } // warm DNS
        repeat(count) { i ->
            val t0 = System.nanoTime()
            val ok = runCatching { Socket().use { it.connect(InetSocketAddress(host, 443), 3000) } }.isSuccess
            if (ok) { val ms = (System.nanoTime() - t0) / 1e6; samples += ms; onSample(ms, (i + 1f) / count) } else lost++
        }
        if (samples.isEmpty()) throw IOException("M-Lab server didn't answer")
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        // Same rule as the HTTP ping: a handshake that hit a retransmit would dominate jitter and "high".
        val steady = samples.filter { it <= max(median * 2.5, median + 40) }
        val jitter = steady.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0
        return PingResult(median, jitter, steady.min(), steady.max(), lost * 100.0 / count)
    }

    suspend fun download(t: Ndt7Target, durationMs: Long, onSample: (Double, Float) -> Unit): TransferResult =
        run(t.downloadUrl, durationMs, onSample, upload = false)

    suspend fun upload(t: Ndt7Target, durationMs: Long, onSample: (Double, Float) -> Unit): TransferResult =
        run(t.uploadUrl, durationMs, onSample, upload = true)

    private suspend fun run(url: String, durationMs: Long, onSample: (Double, Float) -> Unit, upload: Boolean): TransferResult = coroutineScope {
        val bytes = AtomicLong(0)
        val rtts = Collections.synchronizedList(mutableListOf<Double>())
        val open = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        var error: Throwable? = null

        val request = Request.Builder().url(url).header("Sec-WebSocket-Protocol", SUBPROTOCOL).header("User-Agent", "Pulse-Android").build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { open.set(true) }
            override fun onMessage(webSocket: WebSocket, bytes2: ByteString) { if (!upload) bytes.addAndGet(bytes2.size.toLong()) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!upload) bytes.addAndGet(text.length.toLong())
                // Server measurements: TCPInfo.RTT in microseconds is the smoothed RTT under load.
                runCatching { JSONObject(text).optJSONObject("TCPInfo")?.optLong("RTT", 0L)?.takeIf { it > 0 }?.let { rtts += it / 1000.0 } }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failed.set(true); error = t }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        })

        val t0 = System.nanoTime()
        val end = t0 + durationMs * 1_000_000
        val warmupNs = min(2_000_000_000L, durationMs * 1_000_000 / 5)
        var warmBytes = -1L; var warmAt = t0; var lastBytes = 0L; var lastAt = t0; var smooth = 0.0
        var size = 1 shl 13
        val sender: Thread? = if (upload) Thread {
            // Client-driven upload: grow the message while it stays a small share of what has been sent.
            var sent = 0L
            var payload = Random.nextBytes(size).toByteString()
            while (System.nanoTime() < end && !failed.get()) {
                if (!open.get()) { Thread.sleep(20); continue }
                if (ws.queueSize() > MAX_QUEUE) { Thread.sleep(5); continue }
                if (!ws.send(payload)) break
                sent += payload.size
                if (size < (1 shl 20) && sent >= 16L * size) { size *= 2; payload = Random.nextBytes(size).toByteString() }
                // What actually left the phone: everything handed over minus what still waits in the queue.
                bytes.set(max(0, sent - ws.queueSize()))
            }
        }.apply { isDaemon = true; start() } else null

        try {
            while (isActive) {
                delay(150)
                val now = System.nanoTime()
                val b = bytes.get()
                val inst = if (now > lastAt) (b - lastBytes) * 8.0 / ((now - lastAt) / 1e9) / 1e6 else 0.0
                smooth = if (smooth == 0.0) inst else smooth * 0.75 + inst * 0.25
                lastBytes = b; lastAt = now
                if (warmBytes < 0 && now - t0 >= warmupNs) { warmBytes = b; warmAt = now }
                onSample(max(0.0, smooth), ((now - t0).toFloat() / (end - t0)).coerceIn(0f, 1f))
                if (now >= end || failed.get()) break
            }
        } finally {
            ws.cancel()
            sender?.interrupt()
        }
        if (lastBytes == 0L) throw IOException("M-Lab transfer failed: ${error?.message}")
        if (warmBytes < 0) { warmBytes = 0; warmAt = t0 }
        val mbps = max(0.0, (lastBytes - warmBytes) * 8.0 / ((lastAt - warmAt) / 1e9) / 1e6)
        val samples = synchronized(rtts) { rtts.toList() }
        val loaded = if (samples.isNotEmpty()) {
            val s = samples.sorted()
            LoadedPing(s[s.size / 2], s.first(), s.last(), samples.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0)
        } else null
        TransferResult(mbps, loaded?.medianMs, loaded, lastBytes, 1)
    }
}
