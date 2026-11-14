package com.dorp.pulse.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class ServiceGroup { ISRAEL, SOCIAL, STREAMING, WORK }

data class Service(
    val id: String,
    val name: String,
    val group: ServiceGroup,
    /** A page that answers quickly when the service is reachable. Any HTTP reply below 500 counts as up. */
    val probe: String,
    /** Atlassian Statuspage API, for services that publish an official status. */
    val statusApi: String? = null,
    val statusPage: String? = null,
)

/** Result of checking one service from this phone's own connection. */
data class LiveCheck(val reachable: Boolean, val ms: Double?, val httpCode: Int?)

enum class OfficialLevel { OK, MINOR, MAJOR, MAINTENANCE }

data class OfficialStatus(val level: OfficialLevel, val description: String)

object Services {
    val ALL = listOf(
        Service("bezeq", "Bezeq", ServiceGroup.ISRAEL, "https://www.bezeq.co.il/"),
        Service("hot", "HOT", ServiceGroup.ISRAEL, "https://www.hot.net.il/"),
        Service("partner", "Partner", ServiceGroup.ISRAEL, "https://www.partner.co.il/"),
        Service("cellcom", "Cellcom", ServiceGroup.ISRAEL, "https://cellcom.co.il/"),
        Service("yes", "yes", ServiceGroup.ISRAEL, "https://www.yes.co.il/"),
        Service("govil", "gov.il", ServiceGroup.ISRAEL, "https://www.gov.il/"),
        Service("hapoalim", "Bank Hapoalim", ServiceGroup.ISRAEL, "https://www.bankhapoalim.co.il/"),
        Service("leumi", "Bank Leumi", ServiceGroup.ISRAEL, "https://www.leumi.co.il/"),
        Service("ynet", "ynet", ServiceGroup.ISRAEL, "https://www.ynet.co.il/"),
        Service("wolt", "Wolt", ServiceGroup.ISRAEL, "https://wolt.com/"),

        Service("whatsapp", "WhatsApp", ServiceGroup.SOCIAL, "https://web.whatsapp.com/"),
        Service("instagram", "Instagram", ServiceGroup.SOCIAL, "https://www.instagram.com/"),
        Service("facebook", "Facebook", ServiceGroup.SOCIAL, "https://www.facebook.com/"),
        Service("tiktok", "TikTok", ServiceGroup.SOCIAL, "https://www.tiktok.com/"),
        Service("telegram", "Telegram", ServiceGroup.SOCIAL, "https://web.telegram.org/"),
        Service("x", "X (Twitter)", ServiceGroup.SOCIAL, "https://x.com/"),
        Service("discord", "Discord", ServiceGroup.SOCIAL, "https://discord.com/", "https://discordstatus.com/api/v2/status.json", "https://discordstatus.com"),
        Service("reddit", "Reddit", ServiceGroup.SOCIAL, "https://www.reddit.com/", "https://www.redditstatus.com/api/v2/status.json", "https://www.redditstatus.com"),

        Service("youtube", "YouTube", ServiceGroup.STREAMING, "https://www.youtube.com/generate_204"),
        Service("netflix", "Netflix", ServiceGroup.STREAMING, "https://www.netflix.com/"),
        Service("spotify", "Spotify", ServiceGroup.STREAMING, "https://open.spotify.com/"),
        Service("steam", "Steam", ServiceGroup.STREAMING, "https://store.steampowered.com/"),
        Service("psn", "PlayStation Network", ServiceGroup.STREAMING, "https://www.playstation.com/"),
        Service("xbox", "Xbox", ServiceGroup.STREAMING, "https://www.xbox.com/"),
        Service("epic", "Epic Games / Fortnite", ServiceGroup.STREAMING, "https://store.epicgames.com/", "https://status.epicgames.com/api/v2/status.json", "https://status.epicgames.com"),

        Service("google", "Google", ServiceGroup.WORK, "https://www.google.com/generate_204"),
        Service("gmail", "Gmail", ServiceGroup.WORK, "https://mail.google.com/"),
        Service("chatgpt", "ChatGPT", ServiceGroup.WORK, "https://chatgpt.com/", "https://status.openai.com/api/v2/status.json", "https://status.openai.com"),
        Service("outlook", "Outlook / Microsoft 365", ServiceGroup.WORK, "https://outlook.office.com/"),
        Service("icloud", "iCloud", ServiceGroup.WORK, "https://www.icloud.com/"),
        Service("zoom", "Zoom", ServiceGroup.WORK, "https://zoom.us/"),
        Service("dropbox", "Dropbox", ServiceGroup.WORK, "https://www.dropbox.com/", "https://status.dropbox.com/api/v2/status.json", "https://status.dropbox.com"),
        Service("github", "GitHub", ServiceGroup.WORK, "https://github.com/", "https://www.githubstatus.com/api/v2/status.json", "https://www.githubstatus.com"),
        Service("cloudflare", "Cloudflare", ServiceGroup.WORK, "https://www.cloudflare.com/", "https://www.cloudflarestatus.com/api/v2/status.json", "https://www.cloudflarestatus.com"),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    private fun open(url: String, timeoutMs: Int) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs; readTimeout = timeoutMs
        instanceFollowRedirects = false
        useCaches = false
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Pulse")
    }

    /** Reaches the service from this phone; one retry so a single dropped packet isn't reported as an outage. */
    suspend fun check(s: Service): LiveCheck = withContext(Dispatchers.IO) {
        var last: LiveCheck? = null
        repeat(2) {
            val t0 = System.nanoTime()
            val result = runCatching {
                val c = open(s.probe, 6000)
                try { c.responseCode } finally { c.disconnect() }
            }
            val ms = (System.nanoTime() - t0) / 1e6
            val code = result.getOrNull()
            val ok = code != null && code < 500
            last = LiveCheck(ok, if (code != null) ms else null, code)
            if (ok) return@withContext last!!
        }
        // Some sites (gov.il, banks) sit behind firewalls that drop non-browser HTTP clients. If the
        // server still accepts a TCP connection on 443 it is up; only a dead port means unreachable.
        val host = URL(s.probe).host
        val t0 = System.nanoTime()
        val tcp = runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, 443), 5000) } }.isSuccess
        if (tcp) LiveCheck(true, (System.nanoTime() - t0) / 1e6, null) else last!!
    }

    suspend fun official(s: Service): OfficialStatus? = withContext(Dispatchers.IO) {
        val api = s.statusApi ?: return@withContext null
        runCatching {
            val c = open(api, 6000).apply { instanceFollowRedirects = true }
            val text = try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
            val st = JSONObject(text).getJSONObject("status")
            val level = when (st.optString("indicator")) {
                "none" -> OfficialLevel.OK
                "minor" -> OfficialLevel.MINOR
                "major", "critical" -> OfficialLevel.MAJOR
                "maintenance" -> OfficialLevel.MAINTENANCE
                else -> OfficialLevel.OK
            }
            OfficialStatus(level, st.optString("description"))
        }.getOrNull()
    }
}
