package com.dorp.pulse

import android.app.Application
import com.dorp.pulse.engine.LanDevice
import com.dorp.pulse.engine.NetworkScanner
import com.dorp.pulse.engine.Subnet
import com.dorp.pulse.engine.currentSubnet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val subnet: Subnet? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val devices: List<LanDevice> = emptyList(),
    /** True when the phone isn't on Wi-Fi/Ethernet, so there is no local network to scan. */
    val notLocal: Boolean = false,
    val finishedAt: Long? = null,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

class ScanController(private val app: Application, private val scope: CoroutineScope) {
    private val scanner = NetworkScanner(app)
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var runId = 0

    fun refresh() {
        if (_state.value.running) return
        val subnet = currentSubnet(app)
        _state.update { it.copy(subnet = subnet, notLocal = subnet == null) }
    }

    fun start() {
        stop()
        val subnet = currentSubnet(app)
        if (subnet == null) {
            _state.value = ScanUiState(notLocal = true)
            return
        }
        val id = ++runId
        _state.value = ScanUiState(subnet = subnet, running = true, total = subnet.size - 2)
        job = scope.launch {
            try {
                scanner.scan(subnet).collect { e ->
                    if (id != runId) return@collect
                    when (e) {
                        is NetworkScanner.Event.Progress -> _state.update { it.copy(done = e.done, total = e.total) }
                        is NetworkScanner.Event.Found -> _state.update { s ->
                            val list = s.devices.filterNot { it.ip == e.device.ip } + e.device
                            s.copy(devices = list.sortedBy { it.sortKey })
                        }
                        NetworkScanner.Event.Finished -> _state.update { it.copy(finishedAt = System.currentTimeMillis()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Partial results stay on screen.
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
}
