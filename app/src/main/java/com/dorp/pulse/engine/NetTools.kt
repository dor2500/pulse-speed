package com.dorp.pulse.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------- Traceroute

data class Hop(val ttl: Int, val ip: String?, val name: String?, val rttMs: Double?, val reached: Boolean)

/**
 * Traceroute without root: the system `ping` is sent with an increasing TTL; the router that drops it
 * answers "Time to live exceeded" from its own address. Each hop is then pinged directly for a clean RTT.
 */
object Traceroute {
    private val from = Regex("""[Ff]rom ([0-9a-fA-F:.]+)""")
    private val bytesFrom = Regex("""bytes from ([0-9a-fA-F:.]+)""")

    fun run(host: String, maxHops: Int = 30): Flow<Hop> = flow {
        val target = InetAddress.getAllByName(host.trim()).let { all -> all.firstOrNull { it is Inet4Address } ?: all.first() }
        val v6 = target is Inet6Address
        val bin = if (v6) "/system/bin/ping6" else "/system/bin/ping"
        val ip = target.hostAddress!!
        for (ttl in 1..maxHops) {
            val out = runCatching {
                val p = ProcessBuilder(bin, "-n", "-c", "1", "-W", "1", "-t", "$ttl", ip).redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().use { it.readText() }
                if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroy()
                text
            }.getOrDefault("")
            // "from 8.8.8.8: icmp_seq=1" - the colon belongs to the sentence, not the (possibly IPv6) address.
            val reached = bytesFrom.find(out)?.groupValues?.get(1)?.trimEnd(':')
            val hopIp = reached ?: from.find(out)?.groupValues?.get(1)?.trimEnd(':')
            val rtt = hopIp?.let { runCatching { pingOnce(it) }.getOrNull() }
            val name = hopIp?.let { withTimeoutOrNull(1200) { reverse(it) } }
            emit(Hop(ttl, hopIp, name, rtt, reached != null))
            if (reached != null) break
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun reverse(ip: String): String? = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()?.takeIf { it != ip }
    }

    /** Best of three quick ICMP probes, so one slow reply doesn't misrepresent the hop. */
    private suspend fun pingOnce(ip: String): Double? {
        var best: Double? = null
        PingEngine().ping(ip, PingMode.AUTO, count = 3, intervalMs = 50, timeoutMs = 1000).collect { e ->
            if (e is PingEvent.Reply) best = minOf(best ?: e.ms, e.ms)
        }
        return best
    }
}

// ---------------------------------------------------------------- Port scanner

data class OpenPort(val port: Int, val service: String, val ms: Double)

object PortScanner {
    val COMMON = listOf(
        20, 21, 22, 23, 25, 53, 67, 80, 110, 123, 135, 137, 139, 143, 161, 389, 443, 445, 465, 515, 548, 554, 587,
        631, 993, 995, 1080, 1194, 1433, 1521, 1723, 1883, 1900, 2049, 3000, 3306, 3389, 5000, 5060, 5353, 5432,
        5900, 6379, 7000, 8000, 8008, 8009, 8080, 8443, 8888, 9000, 9100, 27017, 32400, 51820, 62078,
    )

    fun service(port: Int): String = when (port) {
        20, 21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 25, 465, 587 -> "SMTP"; 53 -> "DNS"; 67 -> "DHCP"; 80 -> "HTTP"
        110 -> "POP3"; 123 -> "NTP"; 135 -> "MS RPC"; 137, 139 -> "NetBIOS"; 143 -> "IMAP"; 161 -> "SNMP"; 389 -> "LDAP"
        443 -> "HTTPS"; 445 -> "SMB"; 515 -> "LPD"; 548 -> "AFP"; 554 -> "RTSP"; 631 -> "IPP"; 993 -> "IMAPS"; 995 -> "POP3S"
        1080 -> "SOCKS"; 1194 -> "OpenVPN"; 1433 -> "MS SQL"; 1521 -> "Oracle"; 1723 -> "PPTP"; 1883 -> "MQTT"; 1900 -> "UPnP"
        2049 -> "NFS"; 3000 -> "Dev server"; 3306 -> "MySQL"; 3389 -> "RDP"; 5000 -> "UPnP/HTTP"; 5060 -> "SIP"; 5353 -> "mDNS"
        5432 -> "PostgreSQL"; 5900 -> "VNC"; 6379 -> "Redis"; 7000 -> "AirPlay"; 8000, 8080, 8888 -> "HTTP alt"
        8008, 8009 -> "Chromecast"; 8443 -> "HTTPS alt"; 9000 -> "HTTP alt"; 9100 -> "Printer"; 27017 -> "MongoDB"
        32400 -> "Plex"; 51820 -> "WireGuard"; 62078 -> "Apple sync"
        else -> ""
    }

    sealed interface Event {
        data class Progress(val done: Int, val total: Int) : Event
        data class Open(val port: OpenPort) : Event
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(256)

    fun scan(host: String, ports: List<Int>, timeoutMs: Int = 800): Flow<Event> = channelFlow {
        val addr = withContext(Dispatchers.IO) { InetAddress.getByName(host.trim()) }
        val gate = Semaphore(128)
        val done = java.util.concurrent.atomic.AtomicInteger()
        ports.map { port ->
            launch(io) {
                gate.withPermit {
                    val t0 = System.nanoTime()
                    val open = try {
                        Socket().use { it.connect(InetSocketAddress(addr, port), timeoutMs) }; true
                    } catch (_: Exception) { false }
                    if (open) send(Event.Open(OpenPort(port, service(port), (System.nanoTime() - t0) / 1e6)))
                    send(Event.Progress(done.incrementAndGet(), ports.size))
                }
            }
        }.joinAll()
    }

    /** "22, 80, 1000-1100" → ports. Ranges are capped at 65535 and the whole list at 10 000 ports. */
    fun parse(spec: String): List<Int>? = runCatching {
        spec.split(',', ' ').filter { it.isNotBlank() }.flatMap { part ->
            if ('-' in part) { val (a, b) = part.split('-').map { it.trim().toInt() }; (a..b).toList() } else listOf(part.trim().toInt())
        }.filter { it in 1..65535 }.distinct().sorted().take(10_000)
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

// ---------------------------------------------------------------- Wake-on-LAN

object WakeOnLan {
    fun normalizeMac(mac: String): String? {
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length != 12 || hex.any { it !in "0123456789ABCDEF" }) return null
        return hex.chunked(2).joinToString(":")
    }

    /** Sends the magic packet (6×FF + 16×MAC) to the broadcast address on ports 9 and 7. */
    suspend fun send(mac: String, broadcast: String) = withContext(Dispatchers.IO) {
        val m = normalizeMac(mac) ?: throw IllegalArgumentException("Bad MAC")
        val macBytes = m.split(':').map { it.toInt(16).toByte() }.toByteArray()
        val packet = ByteArray(6) { 0xff.toByte() } + (0 until 16).flatMap { macBytes.toList() }.toByteArray()
        DatagramSocket().use { s ->
            s.broadcast = true
            val addr = InetAddress.getByName(broadcast)
            for (port in intArrayOf(9, 7)) repeat(3) { s.send(DatagramPacket(packet, packet.size, addr, port)) }
        }
    }
}

// ---------------------------------------------------------------- Whois (RDAP) and IP location

data class WhoisInfo(
    val query: String,
    val kind: String, // "ip" or "domain"
    val fields: List<Pair<String, String>>,
)

/**
 * Whois over RDAP (the JSON successor to port-43 whois, served by every registry and run through rdap.org's
 * bootstrap), plus IP geolocation from ipwho.is. Both are public, keyless HTTPS APIs.
 */
object Whois {
    private val ipRegex = Regex("""^[0-9.]+$|^[0-9a-fA-F:]+$""")

    suspend fun lookup(input: String): WhoisInfo = withContext(Dispatchers.IO) {
        val q = input.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').lowercase()
        if (ipRegex.matches(q)) ip(q) else domain(q)
    }

    private fun get(url: String): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 8000; instanceFollowRedirects = true
            setRequestProperty("Accept", "application/rdap+json, application/json")
            setRequestProperty("User-Agent", "Pulse")
        }
        try {
            if (c.responseCode == 404) throw IllegalStateException("not found")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    private fun ip(q: String): WhoisInfo {
        val f = ArrayList<Pair<String, String>>()
        runCatching {
            val g = get("https://ipwho.is/$q")
            if (g.optBoolean("success", true)) {
                listOf(g.optString("city"), g.optString("region"), g.optString("country")).filter { it.isNotBlank() }
                    .joinToString(", ").takeIf { it.isNotBlank() }?.let { f += "location" to it }
                g.optJSONObject("connection")?.let { con ->
                    con.optString("isp").takeIf { it.isNotBlank() }?.let { f += "isp" to it }
                    con.optString("org").takeIf { it.isNotBlank() }?.let { f += "org" to it }
                    con.optInt("asn").takeIf { it > 0 }?.let { f += "asn" to "AS$it" }
                }
                g.optJSONObject("timezone")?.optString("id")?.takeIf { it.isNotBlank() }?.let { f += "timezone" to it }
            }
        }
        runCatching {
            val r = get("https://rdap.org/ip/$q")
            r.optString("handle").takeIf { it.isNotBlank() }?.let { f += "network" to it }
            r.optString("name").takeIf { it.isNotBlank() }?.let { f += "netname" to it }
            val range = listOf(r.optString("startAddress"), r.optString("endAddress")).filter { it.isNotBlank() }
            if (range.size == 2) f += "range" to range.joinToString(" – ")
            r.optString("country").takeIf { it.isNotBlank() }?.let { f += "country" to it }
            entityNames(r).firstOrNull()?.let { f += "registrant" to it }
        }
        if (f.isEmpty()) throw IllegalStateException("no data")
        return WhoisInfo(q, "ip", f)
    }

    private fun domain(q: String): WhoisInfo {
        val r = get("https://rdap.org/domain/$q")
        val f = ArrayList<Pair<String, String>>()
        r.optString("ldhName").takeIf { it.isNotBlank() }?.let { f += "domain" to it.lowercase() }
        r.optJSONArray("events")?.let { ev ->
            for (i in 0 until ev.length()) {
                val e = ev.getJSONObject(i); val date = e.optString("eventDate").take(10)
                when (e.optString("eventAction")) {
                    "registration" -> f += "registered" to date
                    "expiration" -> f += "expires" to date
                    "last changed" -> f += "updated" to date
                }
            }
        }
        r.optJSONArray("entities")?.let { ents ->
            for (i in 0 until ents.length()) {
                val e = ents.getJSONObject(i)
                val roles = e.optJSONArray("roles")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val name = vcardName(e) ?: continue
                if ("registrar" in roles) f += "registrar" to name
                if ("registrant" in roles) f += "registrant" to name
            }
        }
        r.optJSONArray("nameservers")?.let { ns ->
            val names = (0 until ns.length()).mapNotNull { ns.getJSONObject(it).optString("ldhName").lowercase().takeIf { n -> n.isNotBlank() } }
            if (names.isNotEmpty()) f += "nameservers" to names.joinToString("\n")
        }
        r.optJSONArray("status")?.let { st ->
            f += "status" to (0 until st.length()).joinToString(", ") { st.getString(it) }
        }
        return WhoisInfo(q, "domain", f)
    }

    private fun entityNames(r: JSONObject): List<String> {
        val ents = r.optJSONArray("entities") ?: return emptyList()
        return (0 until ents.length()).mapNotNull { vcardName(ents.getJSONObject(it)) }
    }

    private fun vcardName(e: JSONObject): String? = runCatching {
        val props = e.getJSONArray("vcardArray").getJSONArray(1)
        (0 until props.length()).map { props.getJSONArray(it) }.firstOrNull { it.getString(0) == "fn" }?.getString(3)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

// ---------------------------------------------------------------- Network info

data class NetInfo(
    val transport: String,
    val iface: String?,
    val localIpv4: String?,
    val prefixLength: Int?,
    val localIpv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
    val mtu: Int?,
    val privateDns: String?,
    val metered: Boolean,
    val validated: Boolean,
    val downKbps: Int?,
    val upKbps: Int?,
    val wifiSsid: String?,
    val wifiRssi: Int?,
    val wifiLinkMbps: Int?,
    val wifiFreq: Int?,
    val wifiStandard: String?,
) {
    val subnetMask: String? get() = prefixLength?.let { p ->
        val m = if (p == 0) 0L else (0xffffffffL shl (32 - p)) and 0xffffffffL
        "${(m shr 24) and 255}.${(m shr 16) and 255}.${(m shr 8) and 255}.${m and 255}"
    }
}

data class PublicIp(val ipv4: String?, val ipv6: String?, val isp: String?, val asn: String?, val location: String?)

object NetInfoReader {
    @Suppress("DEPRECATION")
    fun read(context: Context): NetInfo? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net)
        val lp = cm.getLinkProperties(net)
        val transport = when {
            caps == null -> "?"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
        val v4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val v6 = lp?.linkAddresses?.filter { it.address is Inet6Address }?.map { it.address.hostAddress!!.substringBefore('%') }.orEmpty()
        val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val mtu = if (Build.VERSION.SDK_INT >= 29) lp?.mtu?.takeIf { it > 0 } else null
        val mtuFallback = mtu ?: lp?.interfaceName?.let { n -> runCatching { NetworkInterface.getByName(n)?.mtu }.getOrNull() }
        var ssid: String? = null; var rssi: Int? = null; var link: Int? = null; var freq: Int? = null; var std: String? = null
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            val info = wm?.connectionInfo
            ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            rssi = info?.rssi?.takeIf { it in -127..0 }
            link = info?.linkSpeed?.takeIf { it > 0 }
            freq = info?.frequency?.takeIf { it > 0 }
            if (Build.VERSION.SDK_INT >= 30) std = when (info?.wifiStandard) {
                4 -> "Wi‑Fi 4 (802.11n)"; 5 -> "Wi‑Fi 5 (802.11ac)"; 6 -> "Wi‑Fi 6 (802.11ax)"; 8 -> "Wi‑Fi 7 (802.11be)"; 1 -> "802.11a/b/g"
                else -> null
            }
        }
        NetInfo(
            transport = transport,
            iface = lp?.interfaceName,
            localIpv4 = v4?.address?.hostAddress,
            prefixLength = v4?.prefixLength,
            localIpv6 = v6,
            gateway = gw,
            dns = lp?.dnsServers?.mapNotNull { it.hostAddress?.substringBefore('%') }.orEmpty(),
            mtu = mtuFallback,
            privateDns = if (Build.VERSION.SDK_INT >= 28) lp?.privateDnsServerName ?: if (lp?.isPrivateDnsActive == true) "auto" else null else null,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            downKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            wifiSsid = ssid, wifiRssi = rssi, wifiLinkMbps = link, wifiFreq = freq, wifiStandard = std,
        )
    }.getOrNull()

    /** Public addresses and provider, from Cloudflare (IPv4 and IPv6 endpoints separately). */
    suspend fun publicIp(): PublicIp = withContext(Dispatchers.IO) {
        coroutineScope {
            val meta = async { runCatching { JSONObject(httpGet("https://speed.cloudflare.com/meta")) }.getOrNull() }
            val v4 = async { trace("https://1.1.1.1/cdn-cgi/trace") }
            val v6 = async { trace("https://[2606:4700:4700::1111]/cdn-cgi/trace") }
            val m = meta.await()
            val ipv4 = v4.await() ?: m?.optString("clientIp")?.takeIf { ':' !in it }
            val ipv6 = v6.await() ?: m?.optString("clientIp")?.takeIf { ':' in it }
            PublicIp(
                ipv4 = ipv4, ipv6 = ipv6,
                isp = m?.optString("asOrganization")?.takeIf { it.isNotBlank() },
                asn = m?.optInt("asn")?.takeIf { it > 0 }?.let { "AS$it" },
                location = m?.let { listOf(it.optString("city"), it.optString("country")).filter { s -> s.isNotBlank() }.joinToString(", ") }?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun trace(url: String): String? = runCatching {
        httpGet(url, 3000).lineSequence().firstOrNull { it.startsWith("ip=") }?.removePrefix("ip=")
    }.getOrNull()

    private fun httpGet(url: String, timeout: Int = 5000): String {
        // speed.cloudflare.com/meta answers 403 {} unless the request carries a Referer from its own site.
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout; readTimeout = timeout
            setRequestProperty("User-Agent", "Pulse-Android/1.4")
            if (url.contains("speed.cloudflare.com")) setRequestProperty("Referer", "https://speed.cloudflare.com/")
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}

// ---------------------------------------------------------------- DNS benchmark

data class Resolver(val name: String, val ip: String)

data class ResolverScore(val resolver: Resolver, val medianMs: Double?, val okPercent: Int, val samples: Int)

object DnsBenchmark {
    val PUBLIC = listOf(
        Resolver("Cloudflare", "1.1.1.1"), Resolver("Google", "8.8.8.8"), Resolver("Quad9", "9.9.9.9"),
        Resolver("OpenDNS", "208.67.222.222"), Resolver("AdGuard", "94.140.14.14"), Resolver("Control D", "76.76.2.0"),
        Resolver("CleanBrowsing", "185.228.168.9"), Resolver("Comodo", "8.26.56.26"),
    )
    private val DOMAINS = listOf("google.com", "youtube.com", "facebook.com", "wikipedia.org", "amazon.com", "whatsapp.com", "netflix.com", "instagram.com")

    /** Each resolver answers every domain twice (the second is usually cached); returns the median of all replies. */
    suspend fun score(r: Resolver): ResolverScore = coroutineScope {
        val times = DOMAINS.flatMap { d -> listOf(d, d) }.map { d ->
            runCatching { DnsClient.query(r.ip, d, DnsType.A, 1500).ms }.getOrNull()
        }
        val ok = times.filterNotNull().sorted()
        ResolverScore(r, ok.getOrNull(ok.size / 2), ok.size * 100 / times.size, times.size)
    }
}
