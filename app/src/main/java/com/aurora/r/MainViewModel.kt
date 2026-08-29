package com.aurora.r

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.r.AuroraVpnService.Companion.ACTION_START
import com.aurora.r.AuroraVpnService.Companion.ACTION_STATE
import com.aurora.r.AuroraVpnService.Companion.ACTION_STOP
import com.aurora.r.AuroraVpnService.Companion.EXTRA_CONFIG
import com.aurora.r.AuroraVpnService.Companion.EXTRA_ENDPOINT
import com.aurora.r.AuroraVpnService.Companion.EXTRA_MSG
import com.aurora.r.AuroraVpnService.Companion.EXTRA_RX
import com.aurora.r.AuroraVpnService.Companion.EXTRA_STATE
import com.aurora.r.AuroraVpnService.Companion.EXTRA_TX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Central ViewModel: connection state, settings and endpoints. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private var receiver: android.content.BroadcastReceiver? = null

    val config: StateFlow<ConnectionConfig> = repo.config.stateIn(
        viewModelScope, SharingStarted.Eagerly, ConnectionConfig()
    )
    val endpoints: StateFlow<List<SavedEndpoint>> = repo.endpoints.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val version = if (AetherCore.available) AetherCore.version() else "—"
    val nativeError: String? = AetherCore.loadError

    // UI state
    val state = MutableStateFlow(VpnState.DISCONNECTED)
    val statusMsg = MutableStateFlow("Ready")
    val currentEndpoint = MutableStateFlow("")
    val txBytes = MutableStateFlow(0L)
    val rxBytes = MutableStateFlow(0L)

    // scan state
    val scanState = MutableStateFlow(ScanState.IDLE)
    val scanLog = mutableStateListOf<String>()
    val scanResults = mutableStateListOf<ScanResult>()

    init {
        registerReceiver()
        if (AetherCore.available) AppLog.i("UI", "native core loaded, version=${AetherCore.version()}")
        else AppLog.e("UI", "native core NOT available: ${AetherCore.loadError}")
    }

    private fun registerReceiver() {
        receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                if (intent?.action == ACTION_STATE) {
                    val s = runCatching { VpnState.valueOf(intent.getStringExtra(EXTRA_STATE) ?: "DISCONNECTED") }
                        .getOrDefault(VpnState.DISCONNECTED)
                    state.value = s
                    statusMsg.value = intent.getStringExtra(EXTRA_MSG) ?: ""
                    currentEndpoint.value = intent.getStringExtra(EXTRA_ENDPOINT) ?: ""
                    txBytes.value = intent.getLongExtra(EXTRA_TX, 0L)
                    rxBytes.value = intent.getLongExtra(EXTRA_RX, 0L)
                }
            }
        }
        val filter = IntentFilter(ACTION_STATE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(receiver, filter)
        }
    }

    fun updateConfig(block: (ConnectionConfig) -> ConnectionConfig) {
        viewModelScope.launch {
            repo.saveConfig(block(config.value))
        }
    }

    fun addEndpoint(ep: SavedEndpoint) {
        viewModelScope.launch {
            val set = endpoints.value.toMutableSet()
            set.add(ep)
            repo.saveEndpoints(set.toList())
        }
    }

    fun removeEndpoint(ep: SavedEndpoint) {
        viewModelScope.launch {
            repo.saveEndpoints(endpoints.value.filter { it != ep })
        }
    }

    /** Run a real endpoint scan against the core via JNI. */
    fun runScan() {
        if (!AetherCore.available) { statusMsg.value = nativeError ?: "native lib missing"; return }
        viewModelScope.launch {
            try {
                scanState.value = ScanState.RUNNING
                scanLog.clear()
                scanResults.clear()
                scanLog.add("Provisioning identity…")
                AppLog.i("Scan", "start mode=${config.value.scanMode} ip=${config.value.ipFamily}")

                val cfgDir = getApplication<Application>().filesDir
                val idPath = java.io.File(cfgDir, "aether/aether.toml").absolutePath
                val open = AetherCore.identityOpen(JSONObject().apply {
                    put("path", idPath)
                    put("transport", config.value.protocol)
                })
                if (!open.optBoolean("ok", false)) {
                    val m = "identity open failed: ${open.optString("error")}"
                    scanLog.add(m); AppLog.e("Scan", m); scanState.value = ScanState.ERROR
                    return@launch
                }
                val openJob = open.optLong("job", 0L)
                if (openJob == 0L) { scanLog.add("identity open returned no job"); scanState.value = ScanState.ERROR; return@launch }
                val openResult = AetherCore.awaitJob(openJob)
                val identityId = openResult.optLong("identity", 0L)
                if (identityId == 0L) { scanLog.add("identity not created"); scanState.value = ScanState.ERROR; return@launch }

                scanLog.add("Scanning (${ScanMode.from(config.value.scanMode).label}, ${config.value.ipFamily})…")
                val scan = AetherCore.scanStart(identityId, JSONObject().apply {
                    put("transport", config.value.protocol)
                    put("mode", config.value.scanMode)
                    put("ip", config.value.ipFamily)
                    put("profile", config.value.noizeProfile)
                    put("ech", config.value.enableEch)
                })
                if (!scan.optBoolean("ok", false)) {
                    val m = "scan failed: ${scan.optString("error")}"
                    scanLog.add(m); AppLog.e("Scan", m); scanState.value = ScanState.ERROR
                    return@launch
                }
                val scanJob = scan.optLong("job", 0L)
                if (scanJob == 0L) { scanLog.add("scan returned no job"); scanState.value = ScanState.ERROR; return@launch }
                val result = AetherCore.awaitJob(scanJob)
                val endpoint = result.optString("endpoint").takeIf { it.isNotBlank() }
                if (endpoint != null) {
                    val m = "Found endpoint: $endpoint"
                    scanLog.add(m); AppLog.i("Scan", m)
                    scanResults.add(ScanResult(endpoint, config.value.protocol, reachable = true))
                    val name = "scan-${scanResults.size}"
                    addEndpoint(SavedEndpoint(name, endpoint, config.value.protocol))
                    updateConfig { it.copy(useManualEndpoint = true, manualEndpoint = endpoint) }
                    scanState.value = ScanState.DONE
                } else {
                    val m = "No healthy endpoint found (try Thorough mode / different protocol)"
                    scanLog.add(m); AppLog.w("Scan", m); scanState.value = ScanState.DONE
                }
                runCatching { AetherCore.identityFree(identityId) }
            } catch (e: Throwable) {
                val m = "Error: ${e.message ?: e.javaClass.simpleName}"
                scanLog.add(m); AppLog.e("Scan", m, e); scanState.value = ScanState.ERROR
            }
        }
    }

    fun clearScan() {
        scanResults.clear()
        scanLog.clear()
        scanState.value = ScanState.IDLE
    }

    /** Request VPN permission, then start the service. */
    fun connect(): Intent? {
        if (!AetherCore.available) {
            state.value = VpnState.ERROR
            statusMsg.value = nativeError ?: "native library failed to load"
            AppLog.e("UI", "connect blocked: native lib missing")
            return null
        }
        if (state.value == VpnState.CONNECTED || state.value == VpnState.CONNECTING) return null
        val prepare = VpnService.prepare(getApplication())
        if (prepare != null) {
            AppLog.i("UI", "VPN permission prompt needed")
            return prepare
        }
        AppLog.i("UI", "VPN permission already granted, starting service")
        startService()
        return null
    }

    fun startService(extraPrepareResult: android.content.Intent? = null) {
        val cfgJson = Json.encodeToString(ConnectionConfig.serializer(), config.value)
        val intent = Intent(getApplication(), AuroraVpnService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_CONFIG, cfgJson)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun disconnect() {
        AppLog.i("UI", "disconnect requested")
        val intent = Intent(getApplication(), AuroraVpnService::class.java).apply { action = ACTION_STOP }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        receiver?.let { runCatching { getApplication<Application>().unregisterReceiver(it) } }
    }
}
