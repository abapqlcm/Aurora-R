package com.aurora.r

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File

/**
 * AuroraVpnService — حالت TUN (کل دستگاه).
 *
 * جریان: identity → endpoint (اسکن یا دستی) → تانل Aether روی SOCKS5 محلی →
 * VpnService.establish() → hev-socks5-tunnel روی همان fd.
 */
class AuroraVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelThread: Thread? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var config: ConnectionConfig = ConnectionConfig()
    private var tunnelJobId: Long = 0L
    private var identityId: Long = 0L
    private var lastEndpoint: String = ""

    companion object {
        const val ACTION_START = "com.aurora.r.action.START"
        const val ACTION_STOP = "com.aurora.r.action.STOP"
        const val EXTRA_CONFIG = "com.aurora.r.extra.CONFIG"
        const val ACTION_STATE = "com.aurora.r.STATE"
        const val EXTRA_STATE = "com.aurora.r.extra.STATE"
        const val EXTRA_MSG = "com.aurora.r.extra.MSG"
        const val EXTRA_TX = "com.aurora.r.extra.TX"
        const val EXTRA_RX = "com.aurora.r.extra.RX"
        const val EXTRA_ENDPOINT = "com.aurora.r.extra.ENDPOINT"
        const val CHANNEL_ID = "aurora_vpn"
        private const val TAG = "VpnService"

        /** سقف زمان هر مرحله تا اپ تا ابد روی «Connecting…» گیر نکند */
        const val IDENTITY_TIMEOUT_MS = 60_000L
        const val SCAN_TIMEOUT_MS = 180_000L
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        AppLog.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.i(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn("Stopped by user")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                intent.getStringExtra(EXTRA_CONFIG)?.let { json ->
                    runCatching { Json.decodeFromString<ConnectionConfig>(json) }
                        .onSuccess { config = it }
                        .onFailure { AppLog.w(TAG, "bad config json, using defaults: ${it.message}") }
                }
                startVpn()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    /**
     * startForeground را ایزوله می‌کنیم: روی اندروید ۱۴+ اگر نوع سرویس یا مجوز
     * درست نباشد SecurityException می‌دهد و کل اپ کرش می‌کند. اینجا فقط لاگ
     * می‌شود و پیام قابل‌فهم به UI می‌رود.
     */
    private fun safeStartForeground(text: String): Boolean = try {
        startForeground(1, buildNotification(text))
        true
    } catch (e: Throwable) {
        AppLog.e(TAG, "startForeground failed", e)
        broadcast(VpnState.ERROR, "Foreground service rejected: ${e.message}")
        false
    }

    private fun startVpn() {
        failing = false
        if (!safeStartForeground("Connecting…")) { stopSelf(); return }

        if (!AetherCore.available) {
            fail("Native core unavailable: ${AetherCore.loadError}")
            return
        }

        broadcast(VpnState.CONNECTING, "Preparing Aurora core…")

        scope.launch {
            try {
                AppLog.i(TAG, "config: protocol=${config.protocol} scan=${config.scanMode} " +
                        "noize=${config.noizeProfile} ip=${config.ipFamily} " +
                        "manual=${config.useManualEndpoint}:${config.manualEndpoint} " +
                        "socks=${config.socksPort} ech=${config.enableEch}")

                // 1) محیط هسته
                AetherCore.setEnv("AETHER_SOCKS", "127.0.0.1:${config.socksPort}")
                AetherCore.setEnv("AETHER_NOIZE", config.noizeProfile)
                AetherCore.setEnv("AETHER_LOG_LEVEL", "info")

                // 2) هویت
                val cfgDir = File(filesDir, "aether").apply { mkdirs() }
                val idPath = File(cfgDir, "aether.toml").absolutePath
                AppLog.i(TAG, "identity_open path=$idPath")

                val openReply = AetherCore.identityOpen(JSONObject().apply {
                    put("path", idPath)
                    put("transport", config.protocol)
                })
                AppLog.i(TAG, "identity_open reply=$openReply")
                if (!openReply.optBoolean("ok", false)) {
                    fail("Identity failed: ${openReply.optString("error")}")
                    return@launch
                }
                val openJob = openReply.optLong("job", 0L)
                if (openJob == 0L) { fail("identity_open returned no job"); return@launch }

                broadcast(VpnState.CONNECTING, "Provisioning identity…")
                val openResult = withTimeoutOrNull(IDENTITY_TIMEOUT_MS) {
                    AetherCore.awaitJob(openJob)
                }
                if (openResult == null) {
                    fail("Identity timed out after ${IDENTITY_TIMEOUT_MS / 1000}s — check your internet")
                    return@launch
                }
                AppLog.i(TAG, "identity result=$openResult")
                identityId = openResult.optLong("identity", 0L)
                if (identityId == 0L) { fail("Identity was not created"); return@launch }

                // 3) endpoint
                val endpoint = if (config.useManualEndpoint && config.manualEndpoint.isNotBlank()) {
                    AppLog.i(TAG, "using manual endpoint ${config.manualEndpoint}")
                    config.manualEndpoint
                } else {
                    broadcast(VpnState.CONNECTING, "Scanning for a healthy endpoint…")
                    val scanReply = AetherCore.scanStart(identityId, JSONObject().apply {
                        put("transport", config.protocol)
                        put("mode", config.scanMode)
                        put("ip", config.ipFamily)
                        put("profile", config.noizeProfile)
                        put("ech", config.enableEch)
                    })
                    AppLog.i(TAG, "scan_start reply=$scanReply")
                    if (!scanReply.optBoolean("ok", false)) {
                        fail("Scan failed: ${scanReply.optString("error")}")
                        return@launch
                    }
                    val scanJob = scanReply.optLong("job", 0L)
                    if (scanJob == 0L) { fail("scan_start returned no job"); return@launch }

                    val scanResult = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                        AetherCore.awaitJob(scanJob)
                    }
                    if (scanResult == null) {
                        AetherCore.jobCancel(scanJob)
                        fail("Scan timed out after ${SCAN_TIMEOUT_MS / 1000}s")
                        return@launch
                    }
                    AppLog.i(TAG, "scan result=$scanResult")
                    scanResult.optString("endpoint").takeIf { it.isNotBlank() }
                        ?: run { fail("No healthy endpoint found — try Thorough mode"); return@launch }
                }
                lastEndpoint = endpoint

                // 4) تانل Aether روی SOCKS5 محلی (بلوکه نمی‌شود؛ job زنده می‌ماند)
                broadcast(VpnState.CONNECTING, "Connecting to $endpoint…")
                val tunnelReply = AetherCore.tunnelStart(identityId, JSONObject().apply {
                    put("peer", endpoint)
                    put("transport", config.protocol)
                    put("socks", "127.0.0.1:${config.socksPort}")
                    put("profile", config.noizeProfile)
                    put("keepalive", config.keepaliveSec)
                    put("ech", config.enableEch)
                })
                AppLog.i(TAG, "tunnel_start reply=$tunnelReply")
                if (!tunnelReply.optBoolean("ok", false)) {
                    fail("Tunnel failed: ${tunnelReply.optString("error")}")
                    return@launch
                }
                tunnelJobId = tunnelReply.optLong("job", 0L)
                if (tunnelJobId == 0L) { fail("tunnel_start returned no job"); return@launch }

                // 4b) صبر تا SOCKS5 محلی واقعاً بالا بیاید، وگرنه hev به پورت بسته وصل می‌شود
                broadcast(VpnState.CONNECTING, "Waiting for local proxy…")
                if (!awaitSocks(config.socksPort, 20_000L)) {
                    // ممکن است job تانل با خطا مرده باشد — بخوانش تا دلیل واقعی را بدانیم
                    val poll = runCatching { AetherCore.jobPoll(tunnelJobId) }.getOrNull()
                    AppLog.e(TAG, "socks never came up; tunnel job poll=$poll")
                    AetherCore.jobCancel(tunnelJobId)
                    val why = poll?.optJSONObject("result")?.optString("error").orEmpty()
                    fail(if (why.isNotBlank()) "Tunnel error: $why"
                         else "Local proxy did not start on port ${config.socksPort}")
                    return@launch
                }
                AppLog.i(TAG, "socks5 is up on 127.0.0.1:${config.socksPort}")

                // 5) اینترفیس TUN
                val fd = establishTun() ?: run {
                    AetherCore.jobCancel(tunnelJobId)
                    fail("Failed to establish TUN interface (VPN permission?)")
                    return@launch
                }
                tunFd = fd
                AppLog.i(TAG, "tun established fd=${fd.fd}")

                // 6) hev-socks5-tunnel روی همان fd (بلوکه‌کننده → ترد جدا)
                val hevConfig = TunBridge.buildConfig(
                    "127.0.0.1", config.socksPort,
                    withIpv6 = config.ipFamily != IpFamily.V4.id
                )
                val fdInt = fd.fd
                tunnelThread = Thread({
                    val rc = try {
                        TunBridge.run(hevConfig, fdInt)
                    } catch (t: Throwable) {
                        AppLog.e(TAG, "tun2socks threw", t); -1
                    }
                    AppLog.i(TAG, "tun2socks exited rc=$rc")
                }, "aurora-tun").also { it.isDaemon = true; it.start() }

                broadcast(VpnState.CONNECTED, "Connected to $endpoint")
                AppLog.i(TAG, "CONNECTED via $endpoint")
                startStats()
            } catch (e: Throwable) {
                AppLog.e(TAG, "startVpn crashed", e)
                fail("Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** تا وقتی 127.0.0.1:port قابل اتصال شود صبر می‌کند (هسته چند ثانیه طول می‌کشد). */
    private suspend fun awaitSocks(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("127.0.0.1", port), 800)
                    true
                }
            }.getOrDefault(false)
            if (ok) return true
            delay(500)
        }
        return false
    }

    private fun establishTun(): ParcelFileDescriptor? = try {
        Builder().apply {
            setSession(getString(R.string.vpn_config))
            setMtu(8500)
            addAddress("198.18.0.1", 24)
            addRoute("0.0.0.0", 0)

            // IPv6 فقط اگر کاربر خواسته باشد؛ روی بعضی اپراتورها بدون مسیر واقعی
            // IPv6 باعث می‌شود ترافیک اصلاً بیرون نرود.
            if (config.ipFamily != IpFamily.V4.id) {
                runCatching {
                    addAddress("fc00::1", 64)
                    addRoute("::", 0)
                }.onFailure { AppLog.w(TAG, "ipv6 route skipped: ${it.message}") }
            }

            addDnsServer("1.1.1.1")
            addDnsServer("1.0.0.1")

            // خود اپ از تانل بیرون بماند تا حلقه نشود
            runCatching { addDisallowedApplication(packageName) }
                .onFailure { AppLog.w(TAG, "cannot disallow self: ${it.message}") }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
        }.establish()
    } catch (e: Throwable) {
        AppLog.e(TAG, "establish failed", e)
        null
    }

    private fun startStats() {
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val s = runCatching { TunBridge.stats() }.getOrNull() ?: continue
                sendState(Intent(ACTION_STATE).apply {
                    putExtra(EXTRA_STATE, VpnState.CONNECTED.name)
                    putExtra(EXTRA_MSG, "Connected to $lastEndpoint")
                    putExtra(EXTRA_ENDPOINT, lastEndpoint)
                    putExtra(EXTRA_TX, s[1])
                    putExtra(EXTRA_RX, s[3])
                })
                updateNotification("Connected • ↑${s[1] / 1024}KB ↓${s[3] / 1024}KB")
            }
        }
    }

    private fun stopVpn(reason: String) {
        AppLog.i(TAG, "stopVpn: $reason")
        statsJob?.cancel()

        runCatching { TunBridge.stop() }.onFailure { AppLog.w(TAG, "tun stop: ${it.message}") }
        tunnelThread?.let { runCatching { it.join(2000) } }
        tunnelThread = null

        if (tunnelJobId != 0L) {
            runCatching {
                AetherCore.jobCancel(tunnelJobId)
                AetherCore.jobFree(tunnelJobId)
            }.onFailure { AppLog.w(TAG, "cancel tunnel job: ${it.message}") }
            tunnelJobId = 0L
        }
        if (identityId != 0L) {
            runCatching { AetherCore.identityFree(identityId) }
            identityId = 0L
        }

        runCatching { tunFd?.close() }
        tunFd = null

        AetherCore.unsetEnv("AETHER_SOCKS")
        AetherCore.unsetEnv("AETHER_NOIZE")
        broadcast(VpnState.DISCONNECTED, reason)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    @Volatile private var failing = false

    private fun fail(msg: String) {
        if (failing) return
        failing = true
        AppLog.e(TAG, "FAIL: $msg")
        broadcast(VpnState.ERROR, msg)
        stopVpn(msg)
    }

    private fun broadcast(state: VpnState, msg: String) {
        sendState(Intent(ACTION_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_MSG, msg)
            putExtra(EXTRA_ENDPOINT, lastEndpoint)
        })
        updateNotification(
            when (state) {
                VpnState.CONNECTED -> msg
                VpnState.CONNECTING -> "Connecting…"
                VpnState.ERROR -> "Error: $msg"
                else -> "Disconnected"
            }
        )
    }

    /** برودکست صریح داخل خود اپ (اندروید ۱۴ برودکست ضمنی را محدود می‌کند). */
    private fun sendState(intent: Intent) {
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Aurora-R VPN", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Aurora-R tunnel status"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurora-R")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
        }
    }

    override fun onRevoke() {
        AppLog.w(TAG, "VPN revoked by system")
        stopVpn("Revoked by system")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
