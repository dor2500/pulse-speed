package com.dorp.pulse

import android.app.Application
import android.content.Context
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.currentNetwork
import com.dorp.pulse.engine.LocalTargets
import com.dorp.pulse.engine.PingEngine
import com.dorp.pulse.engine.PingEvent
import com.dorp.pulse.engine.PingException
import com.dorp.pulse.engine.PingMethod
import com.dorp.pulse.engine.PingMode
import com.dorp.pulse.engine.PublicDns
import com.dorp.pulse.engine.localTargets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.abs

enum class PingCount(val value: Int) { TEN(10), THIRTY(30), ENDLESS(0) }

enum class PingError { OFFLINE, RESOLVE_FAILED, NO_METHOD }

data class PingStats(
    val sent: Int = 0,
    val received: Int = 0,
    val minMs: Double? = null,
    val avgMs: Double? = null,
    val maxMs: Double? = null,
    val jitterMs: Double? = null,
) {
    val lossPercent: Double get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent
}

data class PingUiState(
    val host: String = "1.1.1.1",
    val mode: PingMode = PingMode.AUTO,
    val count: PingCount = PingCount.TEN,
    val running: Boolean = false,
    val address: String? = null,
    val method: PingMethod? = null,
    /** One entry per probe in order; null = lost. Only the most recent [MAX_SAMPLES] are kept. */
    val samples: List<Double?> = emptyList(),
    val stats: PingStats = PingStats(),
    val error: PingError? = null,
    val local: LocalTargets = LocalTargets(null, emptyList()),
    val recents: List<String> = emptyList(),
) {
    companion object { const val MAX_SAMPLES = 120 }
}

/** Runs the Ping tab. All the networking lives in [PingEngine]; this only folds its events into UI state. */
class PingController(private val app: Application, private val scope: CoroutineScope) {
    private val engine = PingEngine()
    private val prefs = app.getSharedPreferences("ping", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        PingUiState(host = prefs.getString("host", null) ?: "1.1.1.1", recents = loadRecents())
    )
    val state: StateFlow<PingUiState> = _state.asStateFlow()
    private var job: Job? = null
    /** Bumped on every start/stop so a cancelled run can't touch the state of the next one. */
    private var runId = 0

    /** Collected RTTs of the current run, for exact stats beyond the samples kept for the chart. */
    private val rtts = mutableListOf<Double>()
    private var jitterSum = 0.0
    private var jitterN = 0
    private var lastRtt: Double? = null

    fun refreshLocal() = _state.update { it.copy(local = localTargets(app)) }

    fun setHost(host: String) = _state.update { it.copy(host = host) }
    fun setMode(mode: PingMode) = _state.update { if (it.running) it else it.copy(mode = mode) }
    fun setCount(count: PingCount) = _state.update { if (it.running) it else it.copy(count = count) }

    fun start(hostOverride: String? = null) {
        val host = (hostOverride ?: _state.value.host).trim()
        if (host.isEmpty()) return
        stop()
        refreshLocal()
        if (currentNetwork(app) == NetworkKind.NONE) {
            _state.update { it.copy(host = host, error = PingError.OFFLINE, samples = emptyList(), stats = PingStats()) }
            return
        }
        rtts.clear(); jitterSum = 0.0; jitterN = 0; lastRtt = null
        val s = _state.value
        _state.update {
            it.copy(host = host, running = true, error = null, address = null, method = null,
                samples = emptyList(), stats = PingStats())
        }
        val id = ++runId
        job = scope.launch {
            try {
                engine.ping(host, s.mode, s.count.value).collect { e -> if (id == runId) onEvent(e) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PingException) {
                if (id == runId) _state.update {
                    it.copy(error = if (e.reason == PingException.Reason.RESOLVE_FAILED) PingError.RESOLVE_FAILED else PingError.NO_METHOD)
                }
            } catch (e: Exception) {
                if (id == runId) _state.update { it.copy(error = PingError.NO_METHOD) }
            } finally {
                if (id == runId) _state.update { it.copy(running = false) }
            }
        }
    }

    fun stop() {
        runId++
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    private fun onEvent(e: PingEvent) {
        when (e) {
            is PingEvent.Started -> {
                // Only hosts that actually resolved are worth keeping in Recent.
                rememberHost(_state.value.host)
                _state.update { it.copy(address = e.address, method = e.method) }
            }
            is PingEvent.Reply -> {
                rtts += e.ms
                lastRtt?.let { jitterSum += abs(e.ms - it); jitterN++ }
                lastRtt = e.ms
                push(e.ms)
            }
            is PingEvent.Lost -> push(null)
        }
    }

    private fun push(ms: Double?) = _state.update {
        val sent = it.stats.sent + 1
        it.copy(
            samples = (it.samples + ms).takeLast(PingUiState.MAX_SAMPLES),
            stats = PingStats(
                sent = sent,
                received = rtts.size,
                minMs = rtts.minOrNull(),
                avgMs = if (rtts.isEmpty()) null else rtts.average(),
                maxMs = rtts.maxOrNull(),
                jitterMs = if (jitterN == 0) null else jitterSum / jitterN,
            ),
        )
    }

    private fun rememberHost(host: String) {
        prefs.edit().putString("host", host).apply()
        // Presets are always on screen, so only custom hosts are worth remembering.
        val local = _state.value.local
        if (PublicDns.any { it.host == host } || host == local.gateway || host in local.dns) return
        val recents = (listOf(host) + _state.value.recents.filterNot { it.equals(host, ignoreCase = true) }).take(6)
        _state.update { it.copy(recents = recents) }
        prefs.edit().putString("recents", JSONArray(recents).toString()).apply()
    }

    fun forgetRecent(host: String) {
        val recents = _state.value.recents - host
        _state.update { it.copy(recents = recents) }
        prefs.edit().putString("recents", JSONArray(recents).toString()).apply()
    }

    private fun loadRecents(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("recents", "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())
}
