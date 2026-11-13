package com.dorp.pulse.data

import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.math.roundToInt

/**
 * Turns a result into a self-contained link to Pulse's result page.
 *
 * Everything is packed into the URL fragment (after '#'): the JSON is raw-deflated and base64url-encoded.
 * Browsers never send the fragment to the server, so the page is a plain static file and no test data
 * leaves the phone except through the link itself. The client IP is never included.
 */
object ShareLink {
    const val PAGE = "https://dor2500.github.io/pulse-speed/r/"
    private const val MAX_POINTS = 30

    fun build(r: TestResult, downSamples: List<Float> = emptyList(), upSamples: List<Float> = emptyList(), wifi: ConnectionDetails? = null): String {
        val o = JSONObject()
            .put("v", 1)
            .put("t", r.time / 1000)
            .put("d", round(r.downloadMbps, 2))
            .put("u", round(r.uploadMbps, 2))
            .put("p", round(r.pingMs, 1))
            .put("j", round(r.jitterMs, 1))
            .put("src", r.source.name.lowercase())
            .put("net", r.network.name.lowercase())
        if (r.server.isNotBlank()) o.put("srv", r.server)
        if (r.isp.isNotBlank()) o.put("isp", r.isp)
        r.downLoadedPingMs?.let { o.put("dl", round(it, 1)) }
        r.upLoadedPingMs?.let { o.put("ul", round(it, 1)) }
        r.bufferbloatGrade?.let { o.put("bb", it) }
        wifi?.wifiLinkSpeedMbps?.let { o.put("wl", it) }
        wifi?.wifiFrequencyBand?.let { o.put("wb", it) }
        if (downSamples.isNotEmpty()) o.put("ds", JSONArray(downsample(downSamples)))
        if (upSamples.isNotEmpty()) o.put("us", JSONArray(downsample(upSamples)))
        return PAGE + "#" + encode(o.toString())
    }

    /** Opens the system share sheet with a one-line summary followed by the link. */
    fun share(context: Context, summary: String, link: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "$summary\n$link")
        context.startActivity(Intent.createChooser(send, title))
    }

    private fun encode(json: String): String {
        val input = json.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true) // raw deflate, matches DecompressionStream("deflate-raw")
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Averages the speed samples into at most [MAX_POINTS] buckets, one decimal each. */
    private fun downsample(values: List<Float>): List<Double> {
        if (values.size <= MAX_POINTS) return values.map { round(it.toDouble(), 1) }
        return List(MAX_POINTS) { i ->
            val from = i * values.size / MAX_POINTS
            val to = ((i + 1) * values.size / MAX_POINTS).coerceAtLeast(from + 1)
            round(values.subList(from, to).average(), 1)
        }
    }

    private fun round(v: Double, places: Int): Double {
        val f = Math.pow(10.0, places.toDouble())
        return (v * f).roundToInt() / f
    }
}
