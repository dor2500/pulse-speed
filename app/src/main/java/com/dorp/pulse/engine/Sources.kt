package com.dorp.pulse.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/** The networks Pulse can measure against. */
enum class TestSource(val title: String, val selectable: Boolean) {
    CLOUDFLARE("Cloudflare", false),
    OOKLA("Ookla", true),
    FAST("FAST", true),
    LIBRESPEED("LibreSpeed", true),
    APPLE("Apple", false),
    MLAB("M-Lab", false),
}

/**
 * A server the user can pick. [raw] keeps the source's own JSON for that server,
 * so a saved choice can be turned back into endpoints later.
 */
data class ServerOption(
    val id: String,
    val name: String,
    val location: String,
    val distanceKm: Int?,
    val pingUrl: String,
    val raw: String,
)

/**
 * Turns a [TestSource] (and optionally a chosen [ServerOption]) into concrete endpoints.
 * Without a choice, sources with many servers probe a few and use the fastest.
 *
 * Ookla and FAST are used through the same public endpoints their own web apps call;
 * neither is an official API, so they may change without notice.
 */
internal object Sources {

    suspend fun prepare(source: TestSource, choice: ServerOption?, engine: SpeedTestEngine): PreparedTest =
        withContext(Dispatchers.IO) {
            when (source) {
                TestSource.CLOUDFLARE -> cloudflare(engine)
                TestSource.OOKLA -> ookla(engine, choice)
                TestSource.FAST -> fast(engine, choice)
                TestSource.LIBRESPEED -> libreSpeed(engine, choice)
                TestSource.APPLE -> apple(engine)
                TestSource.MLAB -> mlab(engine)
            }
        }

    /** Servers to show in the picker. [query] searches Ookla's directory; other sources filter locally. */
    suspend fun listServers(source: TestSource, query: String, engine: SpeedTestEngine): List<ServerOption> =
        withContext(Dispatchers.IO) {
            val all = when (source) {
                TestSource.CLOUDFLARE -> emptyList()
                TestSource.OOKLA -> ooklaServers(engine, limit = 40, query = query).map(::ooklaOption)
                TestSource.FAST -> fastOptions(engine)
                TestSource.LIBRESPEED -> libreServers(engine).map(::libreOption)
                TestSource.APPLE, TestSource.MLAB -> emptyList()
            }
            if (query.isBlank() || source == TestSource.OOKLA) all
            else all.filter { (it.name + " " + it.location).contains(query.trim(), ignoreCase = true) }
        }

    private fun nonce() = System.nanoTime().toString(36)

    private fun place(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(", ")

    // ---- Cloudflare -------------------------------------------------------------------------

    private fun cloudflare(engine: SpeedTestEngine): PreparedTest {
        val base = SpeedTestEngine.CLOUDFLARE
        val meta = engine.clientMeta()
        val colo = meta?.optJSONObject("colo")?.optString("iata") ?: meta?.optString("colo").orEmpty()
        val where = COLO_NAMES[colo]?.let { "$it ($colo)" } ?: colo
        return PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = listOf("Cloudflare", where).filter { it.isNotBlank() }.joinToString(" · "),
                protocol = meta?.optString("httpProtocol").orEmpty(),
            ),
            Endpoints(
                ping = "$base/__down?bytes=0",
                download = { "$base/__down?bytes=50000000&r=${nonce()}" },
                upload = { "$base/__up?r=${nonce()}" },
                uploadChunk = 16_000_000L,
            ),
        )
    }

    // ---- Apple (the CDN behind macOS/iOS networkQuality) ---------------------------------------

    private fun apple(engine: SpeedTestEngine): PreparedTest {
        val cfg = JSONObject(engine.get("https://mensura.cdn-apple.com/api/v1/gm/config"))
        val urls = cfg.getJSONObject("urls")
        // "defra3-edge-bx-009.aaplimg.com" -> DE + FRA (UN/LOCODE country and city)
        val edge = cfg.optString("test_endpoint").substringBefore('-')
        val country = edge.take(2).uppercase()
        val city = edge.drop(2).take(3).uppercase()
        val where = APPLE_CITIES[city]?.let { "$it, $country" } ?: listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
        val meta = engine.clientMeta()
        return PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = listOf("Apple", where).filter { it.isNotBlank() }.joinToString(" · "),
                protocol = "HTTP/2",
            ),
            Endpoints(
                ping = urls.getString("small_https_download_url"),
                download = { urls.getString("large_https_download_url") },
                upload = { urls.getString("https_upload_url") },
                uploadChunk = 8_000_000L,
            ),
        )
    }

    // ---- M-Lab (Measurement Lab, NDT7) -----------------------------------------------------

    private fun mlab(engine: SpeedTestEngine): PreparedTest {
        val res = JSONObject(engine.get("https://locate.measurementlab.net/v2/nearest/ndt/ndt7")).getJSONArray("results")
        if (res.length() == 0) throw IOException("No M-Lab servers")
        val s = res.getJSONObject(0)
        val urls = s.getJSONObject("urls")
        val loc = s.optJSONObject("location")
        val where = place(loc?.optString("city"), loc?.optString("country"))
        val meta = engine.clientMeta()
        return PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = listOf("M-Lab", where).filter { it.isNotBlank() }.joinToString(" · "),
                protocol = "WebSocket (NDT7)",
            ),
            Endpoints(
                ping = "", download = { "" }, upload = { "" },
                ndt7 = Ndt7Target(
                    host = s.getString("hostname"),
                    downloadUrl = urls.getString("wss:///ndt/v7/download"),
                    uploadUrl = urls.getString("wss:///ndt/v7/upload"),
                ),
            ),
        )
    }

    // ---- Ookla (speedtest.net server network) -----------------------------------------------

    private fun ooklaServers(engine: SpeedTestEngine, limit: Int, query: String = ""): List<JSONObject> {
        var url = "https://www.speedtest.net/api/js/servers?engine=js&https_functional=true&limit=$limit"
        if (query.isNotBlank()) url += "&search=" + URLEncoder.encode(query.trim(), "UTF-8")
        val arr = JSONArray(engine.get(url))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun ooklaOption(s: JSONObject) = ServerOption(
        id = s.optString("id"),
        name = s.optString("sponsor"),
        location = place(s.optString("name"), s.optString("country")),
        distanceKm = s.optInt("distance", -1).takeIf { it >= 0 },
        pingUrl = "https://${s.getString("host")}/hello",
        raw = s.toString(),
    )

    private suspend fun ookla(engine: SpeedTestEngine, choice: ServerOption?): PreparedTest = coroutineScope {
        val metaJob = async { engine.clientMeta() }
        val server = if (choice != null) JSONObject(choice.raw) else {
            // The list is sorted by distance; probe the nearest few and keep the fastest.
            ooklaServers(engine, limit = 10).take(6)
                .map { s -> async { s to engine.probe("https://${s.getString("host")}/hello", 2500) } }
                .awaitAll()
                .filter { it.second != null }
                .minByOrNull { it.second!! }?.first
                ?: throw IOException("No Ookla server answered")
        }
        val host = server.getString("host")
        val meta = metaJob.await()
        PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = "Ookla · ${server.optString("sponsor")}, ${server.optString("name")}",
                protocol = "",
            ),
            Endpoints(
                ping = "https://$host/hello",
                download = { "https://$host/download?nocache=${nonce()}&size=25000000" },
                upload = { "https://$host/upload?nocache=${nonce()}" },
            ),
        )
    }

    // ---- FAST.com (Netflix Open Connect) ----------------------------------------------------

    /** Each target is ".../speedtest?<signed query>"; ranges go between the path and the query. */
    private fun ranged(url: String, range: String): String {
        val q = url.indexOf('?')
        return if (q < 0) "$url/range/$range" else url.substring(0, q) + "/range/$range" + url.substring(q)
    }

    private fun hostOf(url: String) = runCatching { URI(url).host }.getOrNull().orEmpty()

    private fun fastOptions(engine: SpeedTestEngine): List<ServerOption> {
        val targets = fastTargets(engine).getJSONArray("targets")
        return (0 until targets.length()).map { i ->
            val t = targets.getJSONObject(i)
            val loc = t.optJSONObject("location")
            ServerOption(
                id = hostOf(t.getString("url")),
                name = "Netflix Open Connect",
                location = place(loc?.optString("city"), loc?.optString("country")),
                distanceKm = null,
                pingUrl = ranged(t.getString("url"), "0-0"),
                raw = t.toString(),
            )
        }.distinctBy { it.id }
    }

    private fun fast(engine: SpeedTestEngine, choice: ServerOption?): PreparedTest {
        val json = fastTargets(engine)
        val targets = json.getJSONArray("targets")
        if (targets.length() == 0) throw IOException("No FAST targets")
        val all = (0 until targets.length()).map { targets.getJSONObject(it) }
        // FAST URLs are signed and expire, so a saved choice is matched by host against a fresh list.
        val picked = choice?.let { c -> all.filter { hostOf(it.getString("url")) == c.id } }?.ifEmpty { null }
            ?: choice?.let { listOf(JSONObject(it.raw)) }
            ?: all
        val urls = picked.map { it.getString("url") }
        val client = json.optJSONObject("client")
        val loc = picked.first().optJSONObject("location")
        return PreparedTest(
            ServerInfo(
                ip = client?.optString("ip").orEmpty(),
                isp = client?.optString("isp").orEmpty(),
                serverLabel = listOf("FAST · Netflix", place(loc?.optString("city"), loc?.optString("country")))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                protocol = "",
            ),
            Endpoints(
                ping = ranged(urls[0], "0-0"),
                download = { stream -> ranged(urls[stream % urls.size], "0-26214400") },
                upload = { stream -> ranged(urls[stream % urls.size], "0-0") },
                downStreams = if (urls.size == 1) 6 else maxOf(urls.size, 4).coerceAtMost(8),
            ),
        )
    }

    private fun fastTargets(engine: SpeedTestEngine): JSONObject {
        fun request(token: String) = JSONObject(
            engine.get(
                "https://api.fast.com/netflix/speedtest/v2?https=true&urlCount=5&token=" +
                    URLEncoder.encode(token, "UTF-8")
            )
        )
        return try {
            request(FAST_TOKEN)
        } catch (e: IOException) {
            // The token rarely changes; if it did, read the current one from fast.com's script.
            val page = engine.get("https://fast.com/")
            val script = Regex("app-[a-z0-9]+\\.js").find(page)?.value ?: throw e
            val js = engine.get("https://fast.com/$script")
            val token = Regex("token:\"([A-Za-z0-9]+)\"").find(js)?.groupValues?.get(1) ?: throw e
            request(token)
        }
    }

    // ---- LibreSpeed (community servers) -----------------------------------------------------

    private fun join(server: String, path: String) = server.trimEnd('/') + "/" + path.trimStart('/')

    private fun libreServers(engine: SpeedTestEngine): List<JSONObject> {
        val list = JSONArray(engine.get("https://librespeed.org/backend-servers/servers.php"))
        return (0 until list.length()).map { list.getJSONObject(it) }
    }

    private fun libreOption(s: JSONObject): ServerOption {
        val name = s.optString("name")
        // Names look like "Amsterdam, Netherlands (Clouvider)": split the sponsor off the place.
        val sponsor = s.optString("sponsorName").ifBlank { Regex("\\(([^)]+)\\)\\s*$").find(name)?.groupValues?.get(1).orEmpty() }
        val where = name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        return ServerOption(
            id = s.optString("id"),
            name = sponsor.ifBlank { where },
            location = where,
            distanceKm = null,
            pingUrl = join(s.getString("server"), s.getString("pingURL")),
            raw = s.toString(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun libreSpeed(engine: SpeedTestEngine, choice: ServerOption?): PreparedTest = coroutineScope {
        val metaJob = async { engine.clientMeta() }
        val best = if (choice != null) JSONObject(choice.raw) else {
            val servers = libreServers(engine)
            if (servers.isEmpty()) throw IOException("No LibreSpeed servers")
            // Probe them all (~100 worldwide) with a short timeout; the nearest wins.
            val io = Dispatchers.IO.limitedParallelism(24)
            servers
                .map { s -> async(io) { s to engine.probe(join(s.getString("server"), s.getString("pingURL")), 1500) } }
                .awaitAll()
                .filter { it.second != null }
                .minByOrNull { it.second!! }?.first
                ?: throw IOException("No LibreSpeed server answered")
        }
        val server = best.getString("server")
        val meta = metaJob.await()
        PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = "LibreSpeed · ${best.optString("name")}",
                protocol = "",
            ),
            Endpoints(
                ping = join(server, best.getString("pingURL")),
                download = { "${join(server, best.getString("dlURL"))}?ckSize=100&r=${nonce()}" },
                upload = { "${join(server, best.getString("ulURL"))}?r=${nonce()}" },
                uploadChunk = 4_000_000L,
            ),
        )
    }

    /** Public token that fast.com's own web app sends. */
    private const val FAST_TOKEN = "YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm"
}

private val APPLE_CITIES = mapOf(
    "FRA" to "Frankfurt", "TLV" to "Tel Aviv", "AMS" to "Amsterdam", "LON" to "London", "PAR" to "Paris",
    "MIL" to "Milan", "MRS" to "Marseille", "VIE" to "Vienna", "WAW" to "Warsaw", "MAD" to "Madrid",
    "STO" to "Stockholm", "ZRH" to "Zurich", "DUS" to "Düsseldorf", "MUC" to "Munich", "BER" to "Berlin",
    "NYC" to "New York", "LAX" to "Los Angeles", "SJC" to "San Jose", "CHI" to "Chicago", "DFW" to "Dallas",
)

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
