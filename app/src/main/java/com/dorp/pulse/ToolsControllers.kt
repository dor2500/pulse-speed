package com.dorp.pulse

import android.app.Application
import android.content.Context
import com.dorp.pulse.engine.AccessPoint
import com.dorp.pulse.engine.DnsAnswer
import com.dorp.pulse.engine.DnsBenchmark
import com.dorp.pulse.engine.DnsClient
import com.dorp.pulse.engine.DnsType
import com.dorp.pulse.engine.Hop
import com.dorp.pulse.engine.NetInfo
import com.dorp.pulse.engine.NetInfoReader
import com.dorp.pulse.engine.OpenPort
import com.dorp.pulse.engine.PortScanner
import com.dorp.pulse.engine.PublicIp
import com.dorp.pulse.engine.Resolver
import com.dorp.pulse.engine.ResolverScore
import com.dorp.pulse.engine.Traceroute
import com.dorp.pulse.engine.WakeOnLan
import com.dorp.pulse.engine.Whois
import com.dorp.pulse.engine.WhoisInfo
import com.dorp.pulse.engine.WifiScanner
import com.dorp.pulse.engine.currentSubnet
import com.dorp.pulse.engine.ipString
import com.dorp.pulse.engine.localTargets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Base for the one-shot tools: one job at a time, cancelled by stop() or by starting again. */
abstract class ToolController<S>(initial: S, protected val scope: CoroutineScope) {
    protected val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()
    protected var job: Job? = null
    protected var runId = 0

    protected fun launchJob(block: suspend (id: Int) -> Unit) {
        job?.cancel()
        val id = ++runId
        job = scope.launch {
            try { block(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("PulseTools", "tool failed", e); onError(id, e) } finally { onFinished(id) }
        }
    }
    protected open fun onError(id: Int, e: Exception) {}
    protected open fun onFinished(id: Int) {}
    open fun stop() { runId++; job?.cancel(); job = null; onFinished(runId) }
}

// ---------------------------------------------------------------- Traceroute

data class TraceState(val host: String = "google.com", val running: Boolean = false, val hops: List<Hop> = emptyList(), val error: Boolean = false)

class TraceController(scope: CoroutineScope) : ToolController<TraceState>(TraceState(), scope) {
    fun setHost(h: String) = _state.update { it.copy(host = h) }
    fun start() {
        val host = _state.value.host.trim().ifEmpty { return }
        _state.update { it.copy(running = true, hops = emptyList(), error = false) }
        launchJob { id -> Traceroute.run(host).collect { hop -> if (id == runId) _state.update { it.copy(hops = it.hops + hop) } } }
    }
    override fun onError(id: Int, e: Exception) { if (id == runId) _state.update { it.copy(error = true) } }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(running = false) } }
}

// ---------------------------------------------------------------- DNS lookup

data class DnsLookupState(
    val name: String = "example.com",
    val type: DnsType = DnsType.A,
    /** "system" = the network's own resolver, otherwise an IP. */
    val server: String = "system",
    val customServer: String = "",
    val loading: Boolean = false,
    val answer: DnsAnswer? = null,
    val error: String? = null,
)

class DnsLookupController(private val app: Application, scope: CoroutineScope) : ToolController<DnsLookupState>(DnsLookupState(), scope) {
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setType(v: DnsType) = _state.update { it.copy(type = v) }
    fun setServer(v: String) = _state.update { it.copy(server = v) }
    fun setCustom(v: String) = _state.update { it.copy(customServer = v) }

    fun lookup() {
        val s = _state.value
        val server = when (s.server) {
            "system" -> localTargets(app).dns.firstOrNull { ':' !in it } ?: localTargets(app).dns.firstOrNull() ?: "1.1.1.1"
            "custom" -> s.customServer.trim().ifEmpty { return }
            else -> s.server
        }
        var name = s.name.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifEmpty { return }
        if (s.type == DnsType.PTR) name = DnsClient.reverseName(name) ?: name
        _state.update { it.copy(loading = true, error = null, answer = null) }
        launchJob { id ->
            val a = DnsClient.query(server, name, s.type)
            if (id == runId) _state.update { it.copy(answer = a) }
        }
    }
    override fun onError(id: Int, e: Exception) { if (id == runId) _state.update { it.copy(error = e.message ?: "error") } }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(loading = false) } }
}

// ---------------------------------------------------------------- DNS benchmark

data class DnsBenchState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val scores: List<ResolverScore> = emptyList())

class DnsBenchController(private val app: Application, scope: CoroutineScope) : ToolController<DnsBenchState>(DnsBenchState(), scope) {
    fun start() {
        val network = localTargets(app).dns.filter { ':' !in it }.distinct().map { Resolver("Network DNS", it) }
        val list = (network + DnsBenchmark.PUBLIC).distinctBy { it.ip }
        _state.value = DnsBenchState(running = true, total = list.size)
        launchJob { id ->
            coroutineScope { list.map { r ->
                async {
                    val s = DnsBenchmark.score(r)
                    if (id == runId) _state.update { st ->
                        st.copy(done = st.done + 1, scores = (st.scores + s).sortedWith(compareBy({ it.medianMs == null }, { it.medianMs ?: 0.0 })))
                    }
                }
            }.awaitAll() }
        }
    }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(running = false) } }
}

// ---------------------------------------------------------------- Port scanner

enum class PortPreset { COMMON, WELL_KNOWN, CUSTOM }

data class PortScanState(
    val host: String = "",
    val preset: PortPreset = PortPreset.COMMON,
    val custom: String = "1-1024",
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val open: List<OpenPort> = emptyList(),
    val error: Boolean = false,
    val finished: Boolean = false,
)

class PortScanController(private val app: Application, scope: CoroutineScope) : ToolController<PortScanState>(PortScanState(), scope) {
    init { _state.update { it.copy(host = localTargets(app).gateway ?: "") } }
    fun setHost(v: String) = _state.update { it.copy(host = v) }
    fun setPreset(v: PortPreset) = _state.update { it.copy(preset = v) }
    fun setCustom(v: String) = _state.update { it.copy(custom = v) }

    fun start() {
        val s = _state.value
        val host = s.host.trim().ifEmpty { return }
        val ports = when (s.preset) {
            PortPreset.COMMON -> PortScanner.COMMON
            PortPreset.WELL_KNOWN -> (1..1024).toList()
            PortPreset.CUSTOM -> PortScanner.parse(s.custom) ?: run { _state.update { it.copy(error = true) }; return }
        }
        _state.update { it.copy(running = true, done = 0, total = ports.size, open = emptyList(), error = false, finished = false) }
        launchJob { id ->
            PortScanner.scan(host, ports).collect { e ->
                if (id != runId) return@collect
                when (e) {
                    is PortScanner.Event.Progress -> _state.update { it.copy(done = e.done) }
                    is PortScanner.Event.Open -> _state.update { it.copy(open = (it.open + e.port).sortedBy { p -> p.port }) }
                }
            }
            if (id == runId) _state.update { it.copy(finished = true) }
        }
    }
    override fun onError(id: Int, e: Exception) { if (id == runId) _state.update { it.copy(error = true) } }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(running = false) } }
}

// ---------------------------------------------------------------- Network info

data class NetInfoState(val info: NetInfo? = null, val public: PublicIp? = null, val loadingPublic: Boolean = false)

class NetInfoController(private val app: Application, scope: CoroutineScope) : ToolController<NetInfoState>(NetInfoState(), scope) {
    fun refresh() {
        _state.update { it.copy(info = NetInfoReader.read(app), loadingPublic = true) }
        launchJob { id ->
            val p = NetInfoReader.publicIp()
            if (id == runId) _state.update { it.copy(public = p) }
        }
    }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(loadingPublic = false) } }
}

// ---------------------------------------------------------------- Wi-Fi analyzer

data class WifiState(
    val hasPermission: Boolean = false,
    val wifiOn: Boolean = true,
    val locationOn: Boolean = true,
    val scanning: Boolean = false,
    val aps: List<AccessPoint> = emptyList(),
    val band: String = "2.4 GHz",
)

class WifiController(app: Application, scope: CoroutineScope) : ToolController<WifiState>(WifiState(), scope) {
    val scanner = WifiScanner(app)
    private var unregister: (() -> Unit)? = null

    fun setBand(b: String) = _state.update { it.copy(band = b) }

    fun refresh() {
        val perm = scanner.hasPermission()
        _state.update { it.copy(hasPermission = perm, wifiOn = scanner.wifiEnabled, locationOn = scanner.locationOn, aps = scanner.results()) }
        if (!perm || !scanner.wifiEnabled || !scanner.locationOn) return
        unregister?.invoke()
        _state.update { it.copy(scanning = true) }
        unregister = scanner.startScan {
            _state.update { it.copy(aps = scanner.results(), scanning = false) }
        }
        // If Android throttled the scan, no broadcast arrives; settle on the cached results.
        launchJob { id -> delay(8000); if (id == runId) _state.update { it.copy(scanning = false, aps = scanner.results()) } }
    }

    override fun stop() { super.stop(); unregister?.invoke(); unregister = null }
}

// ---------------------------------------------------------------- Wake-on-LAN

data class WolDevice(val name: String, val mac: String, val broadcast: String)

data class WolState(
    val devices: List<WolDevice> = emptyList(),
    val name: String = "",
    val mac: String = "",
    val broadcast: String = "",
    /** Last result: true sent, false failed, null nothing yet. */
    val sent: Pair<String, Boolean>? = null,
)

class WolController(private val app: Application, scope: CoroutineScope) : ToolController<WolState>(WolState(), scope) {
    private val prefs = app.getSharedPreferences("wol", Context.MODE_PRIVATE)

    init { _state.update { it.copy(devices = load(), broadcast = defaultBroadcast()) } }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setMac(v: String) = _state.update { it.copy(mac = v) }
    fun setBroadcast(v: String) = _state.update { it.copy(broadcast = v) }

    private fun defaultBroadcast(): String = currentSubnet(app)?.let { ipString(it.base + it.size - 1) } ?: "255.255.255.255"

    fun saveAndWake() {
        val s = _state.value
        val mac = WakeOnLan.normalizeMac(s.mac) ?: run { _state.update { it.copy(sent = s.mac to false) }; return }
        val dev = WolDevice(s.name.trim().ifEmpty { mac }, mac, s.broadcast.trim().ifEmpty { defaultBroadcast() })
        val list = listOf(dev) + s.devices.filterNot { it.mac == mac }
        _state.update { it.copy(devices = list, name = "", mac = "") }
        persist(list)
        wake(dev)
    }

    fun wake(d: WolDevice) = launchJob { id ->
        val ok = runCatching { WakeOnLan.send(d.mac, d.broadcast) }.isSuccess
        if (id == runId) _state.update { it.copy(sent = d.name to ok) }
    }

    fun remove(d: WolDevice) {
        val list = _state.value.devices - d
        _state.update { it.copy(devices = list) }
        persist(list)
    }

    private fun persist(list: List<WolDevice>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("m", it.mac).put("b", it.broadcast)) }
        prefs.edit().putString("devices", arr.toString()).apply()
    }

    private fun load(): List<WolDevice> = runCatching {
        val arr = JSONArray(prefs.getString("devices", "[]"))
        List(arr.length()) { i -> arr.getJSONObject(i).let { WolDevice(it.getString("n"), it.getString("m"), it.getString("b")) } }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------- Whois

data class WhoisState(val query: String = "", val loading: Boolean = false, val info: WhoisInfo? = null, val error: Boolean = false)

class WhoisController(scope: CoroutineScope) : ToolController<WhoisState>(WhoisState(), scope) {
    fun setQuery(v: String) = _state.update { it.copy(query = v) }
    fun lookup(q: String? = null) {
        val query = (q ?: _state.value.query).trim().ifEmpty { return }
        _state.update { it.copy(query = query, loading = true, error = false, info = null) }
        launchJob { id ->
            val info = Whois.lookup(query)
            if (id == runId) _state.update { it.copy(info = info) }
        }
    }
    override fun onError(id: Int, e: Exception) { if (id == runId) _state.update { it.copy(error = true) } }
    override fun onFinished(id: Int) { if (id == runId) _state.update { it.copy(loading = false) } }
}
