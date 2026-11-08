package com.dorp.pulse.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.dorp.pulse.engine.TestSource
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SpeedUnit { MBPS, MBYTES }
enum class TestLength(val seconds: Int) { QUICK(6), STANDARD(10), LONG(15) }
/** In-app language override. SYSTEM follows the phone (or Android 13+ per-app language). */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), HEBREW("iw") }
enum class NetworkKind { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

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
)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<TestResult>> = _items.asStateFlow()

    fun add(result: TestResult) = save((listOf(result) + _items.value).take(MAX_ITEMS))
    fun delete(time: Long) = save(_items.value.filterNot { it.time == time })
    fun clear() = save(emptyList())

    private fun save(list: List<TestResult>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("t", r.time).put("d", r.downloadMbps).put("u", r.uploadMbps)
                    .put("p", r.pingMs).put("j", r.jitterMs).put("isp", r.isp)
                    .put("srv", r.server).put("net", r.network.name).put("src", r.source.name)
            )
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
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ITEMS = 200
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
