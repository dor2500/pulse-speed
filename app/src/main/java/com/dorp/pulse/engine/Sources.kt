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
import java.net.URLEncoder

/** The networks Pulse can measure against. */
enum class TestSource(val title: String) {
    CLOUDFLARE("Cloudflare"),
    OOKLA("Ookla"),
    FAST("FAST"),
    LIBRESPEED("LibreSpeed"),
}

/**
 * Turns a [TestSource] into concrete endpoints: it finds the client's details and,
 * where the source has many servers, picks the one with the lowest latency.
 *
 * Ookla and FAST are used through the same public endpoints their own web apps call;
 * neither is an official API, so they may change without notice.
 */
internal object Sources {

    suspend fun prepare(source: TestSource, engine: SpeedTestEngine): PreparedTest = withContext(Dispatchers.IO) {
        when (source) {
            TestSource.CLOUDFLARE -> cloudflare(engine)
            TestSource.OOKLA -> ookla(engine)
            TestSource.FAST -> fast(engine)
            TestSource.LIBRESPEED -> libreSpeed(engine)
        }
    }

    private fun nonce() = System.nanoTime().toString(36)

    // ---- Cloudflare -------------------------------------------------------------------------

    private fun cloudflare(engine: SpeedTestEngine): PreparedTest {
        val base = SpeedTestEngine.CLOUDFLARE
        val meta = engine.clientMeta()
        val colo = meta?.optJSONObject("colo")?.optString("iata") ?: meta?.optString("colo").orEmpty()
        val place = COLO_NAMES[colo]?.let { "$it ($colo)" } ?: colo
        return PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = listOf("Cloudflare", place).filter { it.isNotBlank() }.joinToString(" · "),
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

    // ---- Ookla (speedtest.net server network) -----------------------------------------------

    private suspend fun ookla(engine: SpeedTestEngine): PreparedTest = coroutineScope {
        val metaJob = async { engine.clientMeta() }
        val servers = JSONArray(
            engine.get("https://www.speedtest.net/api/js/servers?engine=js&limit=10&https_functional=true")
        )
        if (servers.length() == 0) throw IOException("No Ookla servers")
        // The list is already sorted by distance; probe the nearest few and keep the fastest.
        val candidates = (0 until minOf(6, servers.length())).map { servers.getJSONObject(it) }
        val best = candidates
            .map { s -> async { s to engine.probe("https://${s.getString("host")}/hello", 2500) } }
            .awaitAll()
            .filter { it.second != null }
            .minByOrNull { it.second!! }?.first
            ?: throw IOException("No Ookla server answered")
        val host = best.getString("host")
        val meta = metaJob.await()
        PreparedTest(
            ServerInfo(
                ip = meta?.optString("clientIp").orEmpty(),
                isp = meta?.optString("asOrganization").orEmpty(),
                serverLabel = "Ookla · ${best.optString("sponsor")}, ${best.optString("name")}",
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

    private fun fast(engine: SpeedTestEngine): PreparedTest {
        val json = fastTargets(engine)
        val targets = json.getJSONArray("targets")
        if (targets.length() == 0) throw IOException("No FAST targets")
        // Each target is ".../speedtest?<signed query>"; ranges go between the path and the query.
        val urls = (0 until targets.length()).map { targets.getJSONObject(it).getString("url") }
        fun ranged(url: String, range: String): String {
            val q = url.indexOf('?')
            return if (q < 0) "$url/range/$range" else url.substring(0, q) + "/range/$range" + url.substring(q)
        }
        val client = json.optJSONObject("client")
        val loc = targets.getJSONObject(0).optJSONObject("location")
        val place = listOfNotNull(loc?.optString("city")?.ifBlank { null }, loc?.optString("country")?.ifBlank { null })
            .joinToString(", ")
        return PreparedTest(
            ServerInfo(
                ip = client?.optString("ip").orEmpty(),
                isp = client?.optString("isp").orEmpty(),
                serverLabel = listOf("FAST · Netflix", place).filter { it.isNotBlank() }.joinToString(" · "),
                protocol = "",
            ),
            Endpoints(
                ping = ranged(urls[0], "0-0"),
                download = { stream -> ranged(urls[stream % urls.size], "0-26214400") },
                upload = { stream -> ranged(urls[stream % urls.size], "0-0") },
                downStreams = maxOf(urls.size, 4).coerceAtMost(8),
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun libreSpeed(engine: SpeedTestEngine): PreparedTest = coroutineScope {
        val metaJob = async { engine.clientMeta() }
        val list = JSONArray(engine.get("https://librespeed.org/backend-servers/servers.php"))
        val servers = (0 until list.length()).map { list.getJSONObject(it) }
        if (servers.isEmpty()) throw IOException("No LibreSpeed servers")
        fun join(server: String, path: String) = server.trimEnd('/') + "/" + path.trimStart('/')
        // Probe them all (the list is ~100 servers worldwide) with a short timeout; nearest wins.
        val io = Dispatchers.IO.limitedParallelism(24)
        val best = servers
            .map { s -> async(io) { s to engine.probe(join(s.getString("server"), s.getString("pingURL")), 1500) } }
            .awaitAll()
            .filter { it.second != null }
            .minByOrNull { it.second!! }?.first
            ?: throw IOException("No LibreSpeed server answered")
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
