package com.dorp.pulse.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.dorp.pulse.engine.ServerOption
import com.dorp.pulse.engine.TestSource
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }
enum class SpeedUnit { MBPS, MBYTES }
enum class TestLength(val seconds: Int) { QUICK(6), STANDARD(10), LONG(15) }
/** In-app language override. SYSTEM follows the phone (or Android 13+ per-app language). */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), HEBREW("iw") }
enum class NetworkKind { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

data class ConnectionDetails(
    val network: NetworkKind,
    val wifiLinkSpeedMbps: Int? = null,
    val wifiFrequencyBand: String? = null,
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val unit: SpeedUnit = SpeedUnit.MBPS,
    val length: TestLength = TestLength.STANDARD,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val source: TestSource = TestSource.CLOUDFLARE,
)

private fun storedLanguage(prefs: android.content.SharedPreferences): AppLanguage =
    AppLanguage.entries.firstOrNull { it.name == prefs.getString("lang", null) } ?: AppLanguage.SYSTEM

/** Wraps [base] in the user's chosen language, if they picked one in the app. */
fun localizedContext(base: Context): Context {
    val tag = storedLanguage(base.getSharedPreferences("settings", Context.MODE_PRIVATE)).tag ?: return base
    val locale = java.util.Locale.forLanguageTag(tag)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        val s = _settings.value
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putString("unit", s.unit.name)
            .putString("length", s.length.name)
            .putString("lang", s.language.name)
            .putString("source", s.source.name)
            .commit() // committed synchronously: a language change recreates the activity right after
    }

    private fun load() = AppSettings(
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic", true),
        unit = enumOr(prefs.getString("unit", null), SpeedUnit.MBPS),
        length = enumOr(prefs.getString("length", null), TestLength.STANDARD),
        language = storedLanguage(prefs),
        source = enumOr(prefs.getString("source", null), TestSource.CLOUDFLARE),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}

data class TestResult(
    val time: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val isp: String,
    val server: String,
    val network: NetworkKind,
    val source: TestSource = TestSource.CLOUDFLARE,
    val downLoadedPingMs: Double? = null,
    val upLoadedPingMs: Double? = null,
    val bufferbloatGrade: String? = null,
    /** Full breakdown for the details screen; null for results saved before v1.9. */
    val details: TestDetails? = null,
)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<TestResult>> = _items.asStateFlow()

    fun add(result: TestResult) = save((listOf(result) + _items.value).take(MAX_ITEMS))
    fun delete(time: Long) = save(_items.value.filterNot { it.time == time })
    fun clear() = save(emptyList())
    /** Replaces the whole list, newest first (used by cloud sync). */
    fun replaceAll(list: List<TestResult>) {
        // The cloud copy never carries IP addresses; keep the ones this device recorded.
        val local = _items.value.associateBy { it.time }
        val withIps = list.map { r ->
            val mine = local[r.time]?.details ?: return@map r
            r.copy(details = (r.details ?: mine).copy(externalIp = mine.externalIp, localIp = mine.localIp))
        }
        val merged = withIps.distinctBy { it.time }.sortedByDescending { it.time }.take(MAX_ITEMS)
        if (merged != _items.value) save(merged)
    }

    fun exportCsv(): String {
        val sb = StringBuilder("Timestamp,Date,Source,Server,ISP,Network,Download_Mbps,Upload_Mbps,Ping_ms,Jitter_ms,Down_Loaded_Ping_ms,Up_Loaded_Ping_ms,Bufferbloat_Grade\n")
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        _items.value.forEach { r ->
            sb.append(r.time).append(",")
            sb.append("\"").append(df.format(java.util.Date(r.time))).append("\",")
            sb.append("\"").append(r.source.name).append("\",")
            sb.append("\"").append(r.server.replace("\"", "\"\"")).append("\",")
            sb.append("\"").append(r.isp.replace("\"", "\"\"")).append("\",")
            sb.append("\"").append(r.network.name).append("\",")
            sb.append(String.format(java.util.Locale.US, "%.2f", r.downloadMbps)).append(",")
            sb.append(String.format(java.util.Locale.US, "%.2f", r.uploadMbps)).append(",")
            sb.append(String.format(java.util.Locale.US, "%.1f", r.pingMs)).append(",")
            sb.append(String.format(java.util.Locale.US, "%.1f", r.jitterMs)).append(",")
            sb.append(r.downLoadedPingMs?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "").append(",")
            sb.append(r.upLoadedPingMs?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "").append(",")
            sb.append("\"").append(r.bufferbloatGrade ?: "").append("\"\n")
        }
        return sb.toString()
    }

    private fun save(list: List<TestResult>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { r ->
            val obj = JSONObject()
                .put("t", r.time).put("d", r.downloadMbps).put("u", r.uploadMbps)
                .put("p", r.pingMs).put("j", r.jitterMs).put("isp", r.isp)
                .put("srv", r.server).put("net", r.network.name).put("src", r.source.name)
            r.downLoadedPingMs?.let { obj.put("dlp", it) }
            r.upLoadedPingMs?.let { obj.put("ulp", it) }
            r.bufferbloatGrade?.let { obj.put("bbg", it) }
            r.details?.let { obj.put("x", it.toJson()) }
            arr.put(obj)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<TestResult> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TestResult(
                time = o.getLong("t"),
                downloadMbps = o.getDouble("d"),
                uploadMbps = o.getDouble("u"),
                pingMs = o.getDouble("p"),
                jitterMs = o.optDouble("j", 0.0),
                isp = o.optString("isp"),
                server = o.optString("srv"),
                network = NetworkKind.entries.firstOrNull { it.name == o.optString("net") } ?: NetworkKind.OTHER,
                source = TestSource.entries.firstOrNull { it.name == o.optString("src") } ?: TestSource.CLOUDFLARE,
                downLoadedPingMs = if (o.has("dlp")) o.getDouble("dlp") else null,
                upLoadedPingMs = if (o.has("ulp")) o.getDouble("ulp") else null,
                bufferbloatGrade = if (o.has("bbg")) o.getString("bbg") else null,
                details = TestDetails.fromJson(o.optJSONObject("x")),
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ITEMS = 200
    }
}

fun connectionDetails(context: Context): ConnectionDetails {
    val net = currentNetwork(context)
    if (net != NetworkKind.WIFI) return ConnectionDetails(net)
    return try {
        val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        val info = wm?.connectionInfo
        val speed = info?.linkSpeed?.takeIf { it > 0 }
        val freq = info?.frequency?.let { f ->
            when {
                f in 2400..2500 -> "2.4 GHz"
                f in 4900..5900 -> "5 GHz"
                f in 5925..7125 -> "6 GHz (Wi-Fi 6E/7)"
                else -> "$f MHz"
            }
        }
        ConnectionDetails(net, speed, freq)
    } catch (_: Exception) {
        ConnectionDetails(net)
    }
}

fun currentNetwork(context: Context): NetworkKind {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.OTHER
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkKind.NONE
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkKind.NONE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkKind.VPN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
        else -> NetworkKind.OTHER
    }
}

/** The server the user picked for each source; a missing entry means "automatic". */
class ServerChoiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)
    private val _choices = MutableStateFlow(load())
    val choices: StateFlow<Map<TestSource, ServerOption>> = _choices.asStateFlow()

    fun set(source: TestSource, option: ServerOption?) {
        _choices.update { if (option == null) it - source else it + (source to option) }
        val edit = prefs.edit()
        if (option == null) edit.remove(source.name)
        else edit.putString(
            source.name,
            JSONObject()
                .put("id", option.id).put("name", option.name).put("location", option.location)
                .put("distance", option.distanceKm ?: -1).put("ping", option.pingUrl).put("raw", option.raw)
                .toString(),
        )
        edit.apply()
    }

    private fun load(): Map<TestSource, ServerOption> = TestSource.entries.mapNotNull { src ->
        val text = prefs.getString(src.name, null) ?: return@mapNotNull null
        runCatching {
            val o = JSONObject(text)
            src to ServerOption(
                id = o.getString("id"),
                name = o.getString("name"),
                location = o.optString("location"),
                distanceKm = o.optInt("distance", -1).takeIf { it >= 0 },
                pingUrl = o.getString("ping"),
                raw = o.getString("raw"),
            )
        }.getOrNull()
    }.toMap()
}
