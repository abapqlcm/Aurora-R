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
    val txBytes = MutableStateFlow(0L)
    val rxBytes = MutableStateFlow(0L)

    // scan state
    val scanState = MutableStateFlow(ScanState.IDLE)
    val scanLog = mutableStateListOf<String>()
    val scanResults = mutableStateListOf<ScanResult>()

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                if (intent?.action == ACTION_STATE) {
                    val s = VpnState.valueOf(intent.getStringExtra(EXTRA_STATE) ?: "DISCONNECTED")
                    state.value = s
                    statusMsg.value = intent.getStringExtra(EXTRA_MSG) ?: ""
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
            val next = block(config.value)
            repo.saveConfig(next)
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

                val cfgDir = getApplication<Application>().filesDir
                val idPath = java.io.File(cfgDir, "aether/aether.toml").absolutePath
                val open = AetherCore.identityOpen(JSONObject().apply {
                    put("path", idPath)
                    put("transport", config.value.protocol)
                })
                if (!open.optBoolean("ok", false)) {
                    scanLog.add("identity open failed: ${open.optString("error")}")
                    scanState.value = ScanState.ERROR
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
                })
                if (!scan.optBoolean("ok", false)) {
                    scanLog.add("scan failed: ${scan.optString("error")}")
                    scanState.value = ScanState.ERROR
                    return@launch
                }
                val scanJob = scan.optLong("job", 0L)
                if (scanJob == 0L) { scanLog.add("scan returned no job"); scanState.value = ScanState.ERROR; return@launch }
                val result = AetherCore.awaitJob(scanJob)
                val endpoint = result.optString("endpoint").takeIf { it.isNotBlank() }
                if (endpoint != null) {
                    scanLog.add("Found endpoint: $endpoint")
                    scanResults.add(ScanResult(endpoint, config.value.protocol, reachable = true))
                    // save as a manual endpoint automatically
                    val name = "scan-${scanResults.size}"
                    addEndpoint(SavedEndpoint(name, endpoint, config.value.protocol))
                    updateConfig { it.copy(useManualEndpoint = true, manualEndpoint = endpoint) }
                    scanState.value = ScanState.DONE
                } else {
                    scanLog.add("No healthy endpoint found")
                    scanState.value = ScanState.DONE
                }
            } catch (e: Throwable) {
                scanLog.add("error: ${e.message}")
                scanState.value = ScanState.ERROR
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
            return null
        }
        if (state.value == VpnState.CONNECTED || state.value == VpnState.CONNECTING) return null
        val prepare = VpnService.prepare(getApplication())
        if (prepare != null) return prepare
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
        val intent = Intent(getApplication(), AuroraVpnService::class.java).apply { action = ACTION_STOP }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        receiver?.let { getApplication<Application>().unregisterReceiver(it) }
    }
}
