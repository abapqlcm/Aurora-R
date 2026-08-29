package com.aurora.r

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** ViewModel مرکزی: مدیریت وضعیت اتصال، تنظیمات و endpointها */
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

    // وضعیت UI
    val state = MutableStateFlow(VpnState.DISCONNECTED)
    val statusMsg = MutableStateFlow("آماده")
    val txBytes = MutableStateFlow(0L)
    val rxBytes = MutableStateFlow(0L)

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
            getApplication<Application>().registerReceiver(
                receiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
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

    /** درخواست مجوز VPN و سپس شروع سرویس */
    fun connect(): Intent? {
        if (!AetherCore.available) {
            state.value = VpnState.ERROR
            statusMsg.value = "کتابخانه بومی بارگذاری نشد: ${AetherCore.loadError}"
            return null
        }
        if (state.value == VpnState.CONNECTED || state.value == VpnState.CONNECTING) return null
        val prepare = VpnService.prepare(getApplication())
        if (prepare != null) {
            return prepare  // MainActivity باید با startActivityForResult صدا بزند
        }
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
        val intent = Intent(getApplication(), AuroraVpnService::class.java).apply {
            action = ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        receiver?.let { getApplication<Application>().unregisterReceiver(it) }
    }
}
