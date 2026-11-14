package com.dorp.pulse.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything beyond the headline numbers, shown on a result's details screen. Saved with the result
 * (key "x"). The two IP addresses are personal data, so they stay on this device and are never
 * written to the cloud copy; see [toMap].
 */
data class TestDetails(
    val idleMinMs: Double? = null,
    val idleMaxMs: Double? = null,
    val lossPercent: Double? = null,
    val downMinMs: Double? = null,
    val downMaxMs: Double? = null,
    val downJitterMs: Double? = null,
    val upMinMs: Double? = null,
    val upMaxMs: Double? = null,
    val upJitterMs: Double? = null,
    val downBytes: Long? = null,
    val upBytes: Long? = null,
    val downStreams: Int? = null,
    val upStreams: Int? = null,
    val durationSec: Int? = null,
    val device: String? = null,
    val android: String? = null,
    val carrier: String? = null,
    val wifiBand: String? = null,
    val wifiLinkMbps: Int? = null,
    val protocol: String? = null,
    val downSamples: List<Float> = emptyList(),
    val upSamples: List<Float> = emptyList(),
    /** Local only. */
    val externalIp: String? = null,
    /** Local only. */
    val localIp: String? = null,
) {
    fun toJson(): JSONObject = JSONObject(toMap(includeIps = true))

    /** Firestore / JSON shape. [includeIps] is false for the cloud copy. */
    fun toMap(includeIps: Boolean): Map<String, Any> = buildMap {
        fun put(k: String, v: Any?) { if (v != null) this[k] = v }
        put("imn", idleMinMs); put("imx", idleMaxMs); put("loss", lossPercent)
        put("dmn", downMinMs); put("dmx", downMaxMs); put("dj", downJitterMs)
        put("umn", upMinMs); put("umx", upMaxMs); put("uj", upJitterMs)
        put("db", downBytes); put("ub", upBytes); put("ds", downStreams); put("us", upStreams)
        put("dur", durationSec); put("dev", device); put("os", android); put("car", carrier)
        put("wb", wifiBand); put("wl", wifiLinkMbps); put("pr", protocol)
        if (downSamples.isNotEmpty()) this["dsm"] = downSamples.map { it.toDouble() }
        if (upSamples.isNotEmpty()) this["usm"] = upSamples.map { it.toDouble() }
        if (includeIps) { put("eip", externalIp); put("lip", localIp) }
    }

    companion object {
        fun fromJson(o: JSONObject?): TestDetails? = o?.let { j -> fromMap(j.keys().asSequence().associateWith { k -> j.get(k) }) }

        fun fromMap(m: Map<String, Any?>?): TestDetails? {
            if (m.isNullOrEmpty()) return null
            fun d(k: String) = (m[k] as? Number)?.toDouble()
            fun l(k: String) = (m[k] as? Number)?.toLong()
            fun i(k: String) = (m[k] as? Number)?.toInt()
            fun s(k: String) = m[k] as? String
            fun f(k: String): List<Float> = when (val v = m[k]) {
                is JSONArray -> List(v.length()) { v.getDouble(it).toFloat() }
                is List<*> -> v.mapNotNull { (it as? Number)?.toFloat() }
                else -> emptyList()
            }
            return TestDetails(
                idleMinMs = d("imn"), idleMaxMs = d("imx"), lossPercent = d("loss"),
                downMinMs = d("dmn"), downMaxMs = d("dmx"), downJitterMs = d("dj"),
                upMinMs = d("umn"), upMaxMs = d("umx"), upJitterMs = d("uj"),
                downBytes = l("db"), upBytes = l("ub"), downStreams = i("ds"), upStreams = i("us"),
                durationSec = i("dur"), device = s("dev"), android = s("os"), carrier = s("car"),
                wifiBand = s("wb"), wifiLinkMbps = i("wl"), protocol = s("pr"),
                downSamples = f("dsm"), upSamples = f("usm"),
                externalIp = s("eip"), localIp = s("lip"),
            )
        }

        /** At most [max] points, averaged, so a 15 s test doesn't store 75 samples per direction. */
        fun downsample(values: List<Float>, max: Int = 40): List<Float> {
            if (values.size <= max) return values
            return List(max) { i ->
                val a = i * values.size / max
                val b = ((i + 1) * values.size / max).coerceAtLeast(a + 1)
                values.subList(a, b).average().toFloat()
            }
        }
    }
}
