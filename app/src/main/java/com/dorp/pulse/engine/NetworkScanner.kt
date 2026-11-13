package com.dorp.pulse.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class DeviceKind { ROUTER, THIS_DEVICE, COMPUTER, PHONE, TV, PRINTER, SPEAKER, CAMERA, SMART_HOME, UNKNOWN }

data class LanDevice(
    val ip: String,
    val name: String? = null,
    val kind: DeviceKind = DeviceKind.UNKNOWN,
    val latencyMs: Double? = null,
    val openPorts: List<Int> = emptyList(),
    /** mDNS / DNS-SD service types the device announced, e.g. "_googlecast._tcp". */
    val services: Set<String> = emptySet(),
) {
    /** Numeric sort key so 192.168.1.10 comes after 192.168.1.9. */
    val sortKey: Long get() = ip.split('.').fold(0L) { acc, p -> acc * 256 + (p.toLongOrNull() ?: 0) }
}

/** The IPv4 subnet the phone is on, clamped to at most 1024 addresses. */
data class Subnet(val self: String, val gateway: String?, val base: Long, val size: Int, val prefix: Int) {
    val label: String get() = "${ipString(base)}/$prefix"
    fun hosts(): List<String> = (1 until size - 1).map { ipString(base + it) }
}

internal fun ipString(v: Long) = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"
private fun ipLong(a: InetAddress) = a.address.fold(0L) { acc, b -> acc * 256 + (b.toInt() and 0xff) }

/** Null when the active network isn't a local one (cellular, VPN-only) or has no IPv4 address. */
fun currentSubnet(context: Context): Subnet? = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val net = cm.activeNetwork ?: return null
    val caps = cm.getNetworkCapabilities(net) ?: return null
    val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    if (!local) return null
    val lp = cm.getLinkProperties(net) ?: return null
    val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
    // A /16 would be 65k probes; scan the /22 around us instead (1024 addresses).
    val prefix = la.prefixLength.coerceIn(22, 30)
    val size = 1 shl (32 - prefix)
    val self = ipLong(la.address)
    val base = self and (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    Subnet(ipString(self), gateway, base, size, prefix)
}.getOrNull()

/**
 * Finds the devices on the local network without root:
 * 1. ICMP echo (ping socket) and TCP connects to common ports on every address in the subnet;
 *    a reply, an accepted connection or a refused one (RST) all prove the host is up.
 * 2. mDNS / DNS-SD discovery in parallel, which gives friendly names ("Living Room TV") and hints at the type.
 * 3. Reverse DNS for whatever still has no name (many home routers answer for DHCP clients).
 *
 * Android 10+ hides the ARP table from apps, so MAC addresses and vendors aren't available.
 */
class NetworkScanner(private val context: Context) {

    sealed interface Event {
        data class Progress(val done: Int, val total: Int) : Event
        data class Found(val device: LanDevice) : Event
        data object Finished : Event
    }

    fun scan(subnet: Subnet): Flow<Event> = callbackFlow {
        val mdns = MdnsBrowser(context)
        mdns.start()
        val hosts = subnet.hosts()
        val found = java.util.concurrent.ConcurrentHashMap<String, LanDevice>()
        val hostSet = hosts.toHashSet()
        val gate = Semaphore(PARALLEL)
        val done = java.util.concurrent.atomic.AtomicInteger()

        fun emitDevice(d: LanDevice) {
            found[d.ip] = d
            trySend(Event.Found(d))
        }

        coroutineScope {
            hosts.map { ip ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        probe(ip, subnet)?.let { emitDevice(it) }
                    }
                    trySend(Event.Progress(done.incrementAndGet(), hosts.size))
                }
            }.awaitAll()
        }

        // Give mDNS a moment more, then merge names/services and name the rest by reverse DNS.
        kotlinx.coroutines.delay(1500)
        val announced = mdns.stop()
        val ips = (found.keys + announced.keys.filter { it in hostSet }).toSet()
        coroutineScope {
            ips.map { ip ->
                async(Dispatchers.IO) {
                    val base = found[ip] ?: LanDevice(ip)
                    val ann = announced[ip]
                    val services = base.services + (ann?.services ?: emptySet())
                    val name = base.name ?: ann?.name ?: reverseName(ip)
                    val kind = if (base.kind == DeviceKind.ROUTER || base.kind == DeviceKind.THIS_DEVICE) base.kind
                    else guessKind(base.openPorts, services, name)
                    val merged = base.copy(name = name, services = services, kind = kind)
                    if (merged != found[ip]) emitDevice(merged)
                }
            }.awaitAll()
        }
        trySend(Event.Finished)
        close()
        awaitClose { mdns.stop() }
    }

    private suspend fun probe(ip: String, subnet: Subnet): LanDevice? = withContext(Dispatchers.IO) {
        val addr = InetAddress.getByName(ip) // literal: no DNS lookup
        if (ip == subnet.self) return@withContext LanDevice(ip, kind = DeviceKind.THIS_DEVICE, latencyMs = 0.0)
        // ICMP and every port at once, so a silent address costs ~0.7 s rather than the sum of all timeouts.
        val (rtt, tcp) = coroutineScope {
            val icmpJob = async(ScanIO) { icmp(addr, ICMP_TIMEOUT) }
            val tcpJobs = PORTS.map { port -> async(ScanIO) { port to tcp(addr, port, TCP_TIMEOUT) } }
            icmpJob.await() to tcpJobs.awaitAll()
        }
        val ports = tcp.filter { it.second.open }.map { it.first }
        val tcpRtt = tcp.mapNotNull { it.second.ms }.minOrNull()
        if (rtt == null && tcp.none { it.second.answered }) return@withContext null
        val kind = if (ip == subnet.gateway) DeviceKind.ROUTER else guessKind(ports, emptySet(), null)
        LanDevice(ip, kind = kind, latencyMs = rtt ?: tcpRtt, openPorts = ports)
    }

    private class TcpResult(val answered: Boolean, val open: Boolean, val ms: Double?)

    private fun tcp(addr: InetAddress, port: Int, timeoutMs: Int): TcpResult {
        val t0 = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(addr, port), timeoutMs) }
            TcpResult(true, true, (System.nanoTime() - t0) / 1e6)
        } catch (e: ConnectException) {
            val refused = e.message?.contains("ECONNREFUSED") == true
            TcpResult(refused, false, if (refused) (System.nanoTime() - t0) / 1e6 else null)
        } catch (_: Exception) {
            TcpResult(false, false, null)
        }
    }

    private fun icmp(addr: InetAddress, timeoutMs: Int): Double? = runCatching {
        val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
        try {
            val pkt = ByteArray(16).also { it[0] = 8; it[7] = 1 }
            val t0 = System.nanoTime()
            Os.sendto(fd, pkt, 0, pkt.size, 0, addr, 0)
            val buf = ByteArray(256)
            while (true) {
                val left = timeoutMs - ((System.nanoTime() - t0) / 1_000_000).toInt()
                if (left <= 0) return@runCatching null
                val pfd = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                if (Os.poll(arrayOf(pfd), left) <= 0) return@runCatching null
                val n = Os.recvfrom(fd, buf, 0, buf.size, 0, null)
                if (n >= 8 && buf[0].toInt() == 0) return@runCatching (System.nanoTime() - t0) / 1e6
            }
            @Suppress("UNREACHABLE_CODE") null
        } finally {
            runCatching { Os.close(fd) }
        }
    }.getOrNull()

    private suspend fun reverseName(ip: String): String? = withTimeoutOrNull(1500) {
        withContext(Dispatchers.IO) {
            runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()
                ?.takeIf { it != ip && !it.endsWith(".in-addr.arpa") }
                ?.removeSuffix(".local")?.removeSuffix(".lan")?.removeSuffix(".home")
        }
    }

    companion object {
        /** Every probe blocks a thread; the default IO pool (64) would serialize 32 hosts × 15 probes. */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        private val ScanIO = Dispatchers.IO.limitedParallelism(PARALLEL_SOCKETS)
        private const val PARALLEL_SOCKETS = 512
        private const val PARALLEL = 64
        private const val ICMP_TIMEOUT = 700
        private const val TCP_TIMEOUT = 600
        /** SSH, HTTP, Windows (SMB/RDP), Apple mobile, Cast, AirPlay, printing, RTSP cameras. */
        private val PORTS = listOf(80, 443, 22, 445, 139, 3389, 62078, 8008, 8009, 7000, 9100, 631, 554, 8080)

        fun guessKind(ports: List<Int>, services: Set<String>, name: String?): DeviceKind {
            val n = name?.lowercase().orEmpty()
            fun svc(vararg s: String) = s.any { it in services }
            return when {
                svc("_ipp._tcp", "_printer._tcp", "_pdl-datastream._tcp") || 9100 in ports || 631 in ports || "printer" in n -> DeviceKind.PRINTER
                svc("_googlecast._tcp", "_androidtvremote2._tcp", "_mediaremotetv._tcp") || 8008 in ports || 8009 in ports ||
                    listOf("tv", "chromecast", "roku", "fire", "shield", "bravia", "webos").any { it in n } -> DeviceKind.TV
                svc("_spotify-connect._tcp", "_sonos._tcp") || listOf("sonos", "speaker", "echo", "homepod").any { it in n } -> DeviceKind.SPEAKER
                svc("_hap._tcp", "_matter._tcp", "_hue._tcp") -> DeviceKind.SMART_HOME
                554 in ports || "cam" in n -> DeviceKind.CAMERA
                62078 in ports || svc("_apple-mobdev2._tcp") || listOf("iphone", "ipad", "android", "galaxy", "pixel", "phone").any { it in n } -> DeviceKind.PHONE
                445 in ports || 139 in ports || 3389 in ports || 22 in ports || svc("_smb._tcp", "_ssh._tcp", "_workstation._tcp") ||
                    listOf("desktop", "laptop", "pc", "macbook", "imac").any { it in n } -> DeviceKind.COMPUTER
                svc("_airplay._tcp", "_raop._tcp") || 7000 in ports -> DeviceKind.TV
                else -> DeviceKind.UNKNOWN
            }
        }
    }
}

/** Browses a set of common DNS-SD service types and resolves each announcement to an IP and a name. */
private class MdnsBrowser(context: Context) {
    data class Announced(val name: String?, val services: Set<String>)

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pending = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private val results = java.util.concurrent.ConcurrentHashMap<String, Announced>()
    private val stopped = AtomicBoolean(false)

    fun start() {
        if (nsd == null) return
        TYPES.forEach { type ->
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    pending += info
                    resolveNext()
                }
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l }
        }
    }

    /** Before Android 14, NsdManager resolves one service at a time, so resolutions are queued. */
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (stopped.get() || !resolving.compareAndSet(false, true)) return
        val info = pending.poll() ?: run { resolving.set(false); return }
        runCatching {
            nsd!!.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) = next()
                override fun onServiceResolved(si: NsdServiceInfo) {
                    val ip = (if (Build.VERSION.SDK_INT >= 34) si.hostAddresses.firstOrNull { it is Inet4Address } else si.host)
                        ?.takeIf { it is Inet4Address }?.hostAddress
                    if (ip != null) {
                        val type = info.serviceType.trim('.')
                        val name = friendlyName(si)
                        results.merge(ip, Announced(name, setOf(type))) { a, b -> Announced(a.name ?: b.name, a.services + b.services) }
                    }
                    next()
                }
                fun next() { resolving.set(false); resolveNext() }
            })
        }.onFailure { resolving.set(false) }
    }

    /** Cast devices put their user-facing name in the "fn" TXT record; others use the instance name. */
    private fun friendlyName(si: NsdServiceInfo): String? {
        val fn = runCatching { si.attributes["fn"]?.toString(Charsets.UTF_8) }.getOrNull()
        val raw = fn ?: si.serviceName
        // Instance names like "Google-TV-3f2a…" or "Office Printer [abcd]"; trim obvious ids.
        return raw?.replace(Regex("""\s*\[[0-9a-fA-F:]+]$"""), "")?.replace(Regex("""-[0-9a-f]{16,}$"""), "")?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun stop(): Map<String, Announced> {
        if (stopped.compareAndSet(false, true)) {
            listeners.forEach { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
            listeners.clear()
        }
        return HashMap(results)
    }

    companion object {
        val TYPES = listOf(
            "_googlecast._tcp", "_airplay._tcp", "_raop._tcp", "_ipp._tcp", "_printer._tcp",
            "_spotify-connect._tcp", "_hap._tcp", "_smb._tcp", "_workstation._tcp", "_http._tcp",
            "_androidtvremote2._tcp", "_sonos._tcp", "_companion-link._tcp",
        )
    }
}
