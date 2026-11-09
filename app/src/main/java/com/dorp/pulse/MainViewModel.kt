package com.dorp.pulse

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dorp.pulse.data.ConnectionDetails
import com.dorp.pulse.data.HistoryStore
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SettingsStore
import com.dorp.pulse.data.TestResult
import com.dorp.pulse.data.connectionDetails
import com.dorp.pulse.data.currentNetwork
import com.dorp.pulse.engine.ServerInfo
import com.dorp.pulse.engine.SpeedTestEngine
import com.dorp.pulse.engine.ServerOption
import com.dorp.pulse.engine.TestSource
import com.dorp.pulse.data.ServerChoiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.dorp.pulse.update.ReleaseInfo
import com.dorp.pulse.update.Updater
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { IDLE, CONNECTING, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

enum class TestError { OFFLINE, SERVER }

/** State of the server picker sheet; [pings] holds a value (or null on failure) once a server was probed. */
data class ServerPickerState(
    val source: TestSource? = null,
    val query: String = "",
    val loading: Boolean = false,
    val error: Boolean = false,
    val items: List<ServerOption> = emptyList(),
    val pings: Map<String, Double?> = emptyMap(),
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val apk: File) : UpdateState
    data object Failed : UpdateState
}

data class TestUiState(
    val phase: Phase = Phase.IDLE,
    /** Live speed (Mbps) of the phase that is running. */
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val downLoadedPingMs: Double? = null,
    val upLoadedPingMs: Double? = null,
    val bufferbloatGrade: String? = null,
    val downSamples: List<Float> = emptyList(),
    val upSamples: List<Float> = emptyList(),
    val server: ServerInfo? = null,
    val network: NetworkKind = NetworkKind.OTHER,
    val connection: ConnectionDetails? = null,
    val error: TestError? = null,
    val source: TestSource = TestSource.CLOUDFLARE,
) {
    val running get() = phase in setOf(Phase.CONNECTING, Phase.PING, Phase.DOWNLOAD, Phase.UPLOAD)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settingsStore = SettingsStore(app)
    val historyStore = HistoryStore(app)
    val serverChoices = ServerChoiceStore(app)
    private val engine = SpeedTestEngine()

    private val _state = MutableStateFlow(TestUiState(network = currentNetwork(app)))
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    private var job: Job? = null

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshNetwork()
        override fun onLost(network: Network) = refreshNetwork()
    }

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private var updateJob: Job? = null

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        checkForUpdates(silent = true)
    }

    /** A silent check (at launch) never shows an error or "up to date"; it only surfaces a newer version. */
    fun checkForUpdates(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            if (!silent) _update.value = UpdateState.Checking
            _update.value = try {
                val info = Updater.fetchLatest()
                when {
                    Updater.isNewer(info.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(info)
                    silent -> UpdateState.Idle
                    else -> UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed
            }
        }
    }

    fun downloadUpdate() {
        val info = when (val u = _update.value) {
            is UpdateState.Available -> u.info
            is UpdateState.Ready -> return
            else -> return
        }
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _update.value = UpdateState.Downloading(info, 0f)
            _update.value = try {
                val apk = Updater.download(getApplication(), info) { p ->
                    _update.value = UpdateState.Downloading(info, p)
                }
                UpdateState.Ready(info, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Available(info)
            }
        }
    }

    private fun refreshNetwork() {
        val details = connectionDetails(getApplication())
        _state.update { if (it.running) it else it.copy(network = details.network, connection = details) }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    private val _picker = MutableStateFlow(ServerPickerState())
    val picker: StateFlow<ServerPickerState> = _picker.asStateFlow()
    private var pickerJob: Job? = null

    fun openServerPicker(source: TestSource) {
        _picker.value = ServerPickerState(source = source)
        loadServers(source, "", debounceMs = 0)
    }

    /** Ookla searches its whole directory; the other sources just filter the list on screen. */
    fun searchServers(query: String) {
        val source = _picker.value.source ?: return
        _picker.update { it.copy(query = query) }
        if (source == TestSource.OOKLA) loadServers(source, query, debounceMs = 450)
    }

    fun retryServers() {
        val p = _picker.value
        p.source?.let { loadServers(it, if (it == TestSource.OOKLA) p.query else "", debounceMs = 0) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadServers(source: TestSource, query: String, debounceMs: Long) {
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            delay(debounceMs)
            _picker.update { it.copy(loading = true, error = false) }
            val items = try {
                engine.listServers(source, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _picker.update { it.copy(loading = false, error = true, items = emptyList()) }
                return@launch
            }
            _picker.update { it.copy(loading = false, items = items) }
            // Measure each server's latency in the background so the list fills in live.
            val io = Dispatchers.IO.limitedParallelism(8)
            items.filter { it.id !in _picker.value.pings }.forEach { option ->
                launch(io) {
                    val ms = engine.ping(option)
                    _picker.update { if (it.source == source) it.copy(pings = it.pings + (option.id to ms)) else it }
                }
            }
        }
    }

    fun closeServerPicker() {
        pickerJob?.cancel()
        _picker.value = ServerPickerState()
    }

    fun chooseServer(source: TestSource, option: ServerOption?) {
        serverChoices.set(source, option)
        closeServerPicker()
    }

    /** Bumped on every start/stop so callbacks from a stopped run can't touch the screen. */
    private var runId = 0

    private inline fun updateRun(id: Int, crossinline transform: (TestUiState) -> TestUiState) {
        _state.update { if (id == runId) transform(it) else it }
    }

    fun start() {
        if (job?.isActive == true) return
        val details = connectionDetails(getApplication())
        val network = details.network
        if (network == NetworkKind.NONE) {
            _state.value = TestUiState(phase = Phase.ERROR, network = network, connection = details, error = TestError.OFFLINE)
            return
        }
        val settings = settingsStore.settings.value
        val durationMs = settings.length.seconds * 1000L
        val source = settings.source
        val id = ++runId
        job = viewModelScope.launch {
            _state.value = TestUiState(phase = Phase.CONNECTING, network = network, connection = details, source = source)
            try {
                val test = engine.prepare(source, serverChoices.choices.value[source])
                val ep = test.endpoints
                updateRun(id) { it.copy(server = test.info, phase = Phase.PING) }

                val ping = engine.measurePing(ep) { ms, p ->
                    updateRun(id) { it.copy(pingMs = ms, progress = p) }
                }
                updateRun(id) {
                    it.copy(pingMs = ping.pingMs, jitterMs = ping.jitterMs, phase = Phase.DOWNLOAD, progress = 0f)
                }

                val down = engine.measureDownload(ep, durationMs) { mbps, p ->
                    updateRun(id) { it.copy(liveMbps = mbps, progress = p, downSamples = it.downSamples + mbps.toFloat()) }
                }
                updateRun(id) {
                    it.copy(
                        downloadMbps = down.mbps,
                        downLoadedPingMs = down.loadedPingMs,
                        liveMbps = 0.0,
                        phase = Phase.UPLOAD,
                        progress = 0f,
                    )
                }

                val up = engine.measureUpload(ep, durationMs) { mbps, p ->
                    updateRun(id) { it.copy(liveMbps = mbps, progress = p, upSamples = it.upSamples + mbps.toFloat()) }
                }
                if (id != runId) return@launch

                val grade = com.dorp.pulse.engine.calculateBufferbloatGrade(ping.pingMs, down.loadedPingMs, up.loadedPingMs)

                updateRun(id) {
                    it.copy(
                        uploadMbps = up.mbps,
                        upLoadedPingMs = up.loadedPingMs,
                        bufferbloatGrade = grade,
                        liveMbps = down.mbps,
                        phase = Phase.DONE,
                        progress = 1f,
                    )
                }

                historyStore.add(
                    TestResult(
                        time = System.currentTimeMillis(),
                        downloadMbps = down.mbps,
                        uploadMbps = up.mbps,
                        pingMs = ping.pingMs,
                        jitterMs = ping.jitterMs,
                        isp = test.info.isp,
                        server = test.info.serverLabel,
                        network = network,
                        source = source,
                        downLoadedPingMs = down.loadedPingMs,
                        upLoadedPingMs = up.loadedPingMs,
                        bufferbloatGrade = grade,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val offline = currentNetwork(getApplication()) == NetworkKind.NONE
                updateRun(id) {
                    it.copy(phase = Phase.ERROR, error = if (offline) TestError.OFFLINE else TestError.SERVER, liveMbps = 0.0)
                }
            }
        }
    }

    /** Stops a running test immediately: sockets close and nothing from it is saved. */
    fun cancel() {
        runId++
        job?.cancel()
        job = null
        _state.update { TestUiState(network = currentNetwork(getApplication()), source = it.source) }
    }
}
