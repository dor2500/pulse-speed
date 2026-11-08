package com.dorp.pulse

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dorp.pulse.data.HistoryStore
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SettingsStore
import com.dorp.pulse.data.TestResult
import com.dorp.pulse.data.currentNetwork
import com.dorp.pulse.engine.ServerInfo
import com.dorp.pulse.engine.SpeedTestEngine
import com.dorp.pulse.engine.TestSource
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
    val downSamples: List<Float> = emptyList(),
    val upSamples: List<Float> = emptyList(),
    val server: ServerInfo? = null,
    val network: NetworkKind = NetworkKind.OTHER,
    val error: TestError? = null,
    val source: TestSource = TestSource.CLOUDFLARE,
) {
    val running get() = phase in setOf(Phase.CONNECTING, Phase.PING, Phase.DOWNLOAD, Phase.UPLOAD)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settingsStore = SettingsStore(app)
    val historyStore = HistoryStore(app)
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
        val kind = currentNetwork(getApplication())
        _state.update { if (it.running) it else it.copy(network = kind) }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    /** Bumped on every start/stop so callbacks from a stopped run can't touch the screen. */
    private var runId = 0

    private inline fun updateRun(id: Int, crossinline transform: (TestUiState) -> TestUiState) {
        _state.update { if (id == runId) transform(it) else it }
    }

    fun start() {
        if (job?.isActive == true) return
        val network = currentNetwork(getApplication())
        if (network == NetworkKind.NONE) {
            _state.value = TestUiState(phase = Phase.ERROR, network = network, error = TestError.OFFLINE)
            return
        }
        val settings = settingsStore.settings.value
        val durationMs = settings.length.seconds * 1000L
        val source = settings.source
        val id = ++runId
        job = viewModelScope.launch {
            _state.value = TestUiState(phase = Phase.CONNECTING, network = network, source = source)
            try {
                val test = engine.prepare(source)
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
                updateRun(id) { it.copy(downloadMbps = down, liveMbps = 0.0, phase = Phase.UPLOAD, progress = 0f) }

                val up = engine.measureUpload(ep, durationMs) { mbps, p ->
                    updateRun(id) { it.copy(liveMbps = mbps, progress = p, upSamples = it.upSamples + mbps.toFloat()) }
                }
                if (id != runId) return@launch
                updateRun(id) { it.copy(uploadMbps = up, liveMbps = down, phase = Phase.DONE, progress = 1f) }

                historyStore.add(
                    TestResult(
                        time = System.currentTimeMillis(),
                        downloadMbps = down,
                        uploadMbps = up,
                        pingMs = ping.pingMs,
                        jitterMs = ping.jitterMs,
                        isp = test.info.isp,
                        server = test.info.serverLabel,
                        network = network,
                        source = source,
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
