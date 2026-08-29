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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File

/**
 * AuroraVpnService — پیاده‌سازی حالت TUN.
 *
 * جریان:
 *   1) کاربر دکمه اتصال را می‌زند → MainActivity فیلتر TUN را درخواست می‌کند
 *   2) پس از گرفتن مجوز، [onStartCommand] صدا زده می‌شود با کانفیگ در Intent
 *   3) هویت Aether باز می‌شود → تانل روی SOCKS5 محلی (127.0.0.1:<port>) اجرا می‌گردد
 *   4) اینترفیس TUN با VpnService.establish() ساخته می‌شود (fd)
 *   5) در یک ترد جدا، hev-socks5-tunnel روی همان fd اجرا می‌شود تا کل ترافیک
 *      دستگاه از طریق SOCKS5 → Aether عبور کند
 *   6) آمار ترافیک هر ثانیه خوانده و به UI برودکست می‌شود
 *
 * جلوگیری از حلقه (loop): اپ Aurora-R خودش با addDisallowedApplication از تانل
 * مستثنا شده است، پس سوکت‌هایی که Aether باز می‌کند از تانل بیرون می‌روند.
 */
class AuroraVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelThread: Thread? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var config: ConnectionConfig = ConnectionConfig()
    private var tunnelJobId: Long = 0L

    companion object {
        const val ACTION_START = "com.aurora.r.action.START"
        const val ACTION_STOP = "com.aurora.r.action.STOP"
        const val EXTRA_CONFIG = "com.aurora.r.extra.CONFIG"
        const val ACTION_STATE = "com.aurora.r.STATE"
        const val EXTRA_STATE = "com.aurora.r.extra.STATE"
        const val EXTRA_MSG = "com.aurora.r.extra.MSG"
        const val EXTRA_TX = "com.aurora.r.extra.TX"
        const val EXTRA_RX = "com.aurora.r.extra.RX"
        const val CHANNEL_ID = "aurora_vpn"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn("توقف توسط کاربر")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_CONFIG)
                if (json != null) {
                    runCatching { Json.decodeFromString<ConnectionConfig>(json) }
                        .onSuccess { config = it }
                }
                startVpn()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        startForeground(1, buildNotification("در حال اتصال…"))
        broadcast(VpnState.CONNECTING, "در حال آماده‌سازی هسته Aurora…")

        scope.launch {
            try {
                // 1) تنظیم محیط هسته
                AetherCore.setEnv("AETHER_SOCKS", "127.0.0.1:${config.socksPort}")
                AetherCore.setEnv("AETHER_NOIZE", config.noizeProfile)
                AetherCore.setEnv("AETHER_LOG_LEVEL", "info")

                // 2) باز کردن هویت
                val cfgDir = File(filesDir, "aether")
                cfgDir.mkdirs()
                val idPath = File(cfgDir, "aether.toml").absolutePath
                val openPayload = JSONObject().apply {
                    put("path", idPath)
                    put("transport", config.protocol)
                }
                val openReply = AetherCore.identityOpen(openPayload)
                if (!openReply.optBoolean("ok", false)) {
                    fail("باز کردن هویت شکست خورد: ${openReply.optString("error")}")
                    return@launch
                }
                // پاسخ: {"ok":true,"job":<id>} — job را مستقیماً از سطح بالا می‌خوانیم
                val openJob = openReply.optLong("job", 0L)
                if (openJob == 0L) {
                    fail("پاسخ باز کردن هویت job نداشت")
                    return@launch
                }
                val openResult = AetherCore.awaitJob(openJob)
                // نتیجه: {"identity":<id>,"summary":{...},"path":"...","lastconn_path":"..."}
                val identityId = openResult.optLong("identity", 0L)
                if (identityId == 0L) {
                    fail("هویت ساخته نشد")
                    return@launch
                }

                // 3) تعیین endpoint: اسکن یا دستی
                val endpoint = if (config.useManualEndpoint && config.manualEndpoint.isNotBlank()) {
                    config.manualEndpoint
                } else {
                    broadcast(VpnState.CONNECTING, "در حال اسکن endpointها…")
                    val scanPayload = JSONObject().apply {
                        put("transport", config.protocol)
                        put("mode", config.scanMode)
                        put("profile", config.noizeProfile)
                    }
                    val scanReply = AetherCore.scanStart(identityId, scanPayload)
                    if (!scanReply.optBoolean("ok", false)) {
                        fail("اسکن شکست خورد: ${scanReply.optString("error")}")
                        return@launch
                    }
                    // پاسخ: {"ok":true,"job":<id>}
                    val scanJob = scanReply.optLong("job", 0L)
                    if (scanJob == 0L) {
                        fail("پاسخ اسکن job نداشت")
                        return@launch
                    }
                    val scanResult = AetherCore.awaitJob(scanJob)
                    // نتیجه: {"endpoint":"IP:PORT"}
                    val ep = scanResult.optString("endpoint").takeIf { it.isNotBlank() }
                    if (ep == null) {
                        fail("هیچ endpoint سالمی پیدا نشد")
                        return@launch
                    }
                    ep
                }

                // 4) راه‌اندازی تانل Aether روی SOCKS5 محلی
                broadcast(VpnState.CONNECTING, "در حال برقراری تانل به $endpoint")
                val tunnelPayload = JSONObject().apply {
                    put("peer", endpoint)
                    put("transport", config.protocol)
                    put("socks", "127.0.0.1:${config.socksPort}")
                    put("profile", config.noizeProfile)
                    put("keepalive", 25)
                }
                val tunnelReply = AetherCore.tunnelStart(identityId, tunnelPayload)
                if (!tunnelReply.optBoolean("ok", false)) {
                    fail("تانل شکست خورد: ${tunnelReply.optString("error")}")
                    return@launch
                }
                // job را برای لغو در زمان قطع نگه می‌داریم
                tunnelJobId = tunnelReply.optLong("job", 0L)

                // 5) ساخت اینترفیس TUN (بدون خودِ اپ برای جلوگیری از loop)
                val fd = establishTun() ?: run {
                    AetherCore.jobCancel(tunnelJobId)
                    fail("ساخت اینترفیس TUN شکست خورد")
                    return@launch
                }
                tunFd = fd

                // 6) راه‌اندازی tun2socks روی همان fd (ترد جدا چون بلوکه‌کننده است)
                val hevConfig = TunBridge.buildConfig("127.0.0.1", config.socksPort)
                val fdInt = fd.fd
                tunnelThread = Thread({
                    val rc = TunBridge.run(hevConfig, fdInt)
                    if (isActive) {
                        android.util.Log.i("AuroraVpn", "tun thread exited rc=$rc")
                    }
                }, "aurora-tun").also { it.start() }

                // 7) وضعیت متصل + شروع برودکست آمار
                broadcast(VpnState.CONNECTED, "متصل به $endpoint")
                startStats()
            } catch (e: Exception) {
                fail("خطا: ${e.message}")
            }
        }
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return try {
            Builder().apply {
                setSession(getString(R.string.vpn_config))
                // رنج خصوصی (پراکسی) که hev روی TUN خواهد نشاند
                addAddress("198.18.0.1", 24)
                addAddress("fc00::1", 64)

                // روتینگ پیش‌فرض: کل ترافیک IPv4/IPv6 به تانل
                addRoute("0.0.0.0", 0)
                addRoute("::", 0)

                // DNSهای عمومی (با Cloudflare/Moje نظرسنجی واقعی توسط Aether انجام می‌شود)
                addDnsServer("1.1.1.1")
                addDnsServer("1.0.0.1")

                // خودِ اپ از تانل مستثنا شود تا حلقه ایجاد نشود
                try {
                    addDisallowedApplication(packageName)
                } catch (ex: Exception) {
                    android.util.Log.w("AuroraVpn", "cannot disallow self: ${ex.message}")
                }

                // سیستم‌های قدیمی
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setMetered(false)
                }
            }.establish()
        } catch (e: Exception) {
            android.util.Log.e("AuroraVpn", "establish failed", e)
            null
        }
    }

    private fun startStats() {
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val s = TunBridge.stats()
                val intent = Intent(ACTION_STATE).apply {
                    putExtra(EXTRA_STATE, VpnState.CONNECTED.name)
                    putExtra(EXTRA_TX, s[1])
                    putExtra(EXTRA_RX, s[3])
                }
                sendBroadcast(intent)
                updateNotification("متصل • ↑${(s[1] / 1024)}KB ↓${(s[3] / 1024)}KB")
            }
        }
    }

    private fun stopVpn(reason: String) {
        statsJob?.cancel()

        // ۱) tun2socks را متوقف کن
        TunBridge.stop()
        tunnelThread?.let {
            try { it.join(2000) } catch (_: Exception) {}
        }
        tunnelThread = null

        // ۲) تانل Aether را لغو و آزاد کن (وگرنه در پس‌زمینه زنده می‌ماند)
        if (tunnelJobId != 0L) {
            try {
                AetherCore.jobCancel(tunnelJobId)
                AetherCore.jobFree(tunnelJobId)
            } catch (e: Exception) {
                android.util.Log.w("AuroraVpn", "cancel tunnel job failed: ${e.message}")
            }
            tunnelJobId = 0L
        }

        // ۳) اینترفیس TUN را ببند
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null

        AetherCore.unsetEnv("AETHER_SOCKS")
        AetherCore.unsetEnv("AETHER_NOIZE")
        broadcast(VpnState.DISCONNECTED, reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** جلوگیری از حلقه‌ی بی‌نهایت fail → stopVpn → fail */
    @Volatile private var failing = false

    private fun fail(msg: String) {
        if (failing) return
        failing = true
        android.util.Log.e("AuroraVpn", msg)
        broadcast(VpnState.ERROR, msg)
        stopVpn(msg)
        failing = false
    }

    private fun broadcast(state: VpnState, msg: String) {
        val intent = Intent(ACTION_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_MSG, msg)
        }
        sendBroadcast(intent)
        updateNotification(
            when (state) {
                VpnState.CONNECTED -> msg
                VpnState.CONNECTING -> "در حال اتصال…"
                VpnState.ERROR -> "خطا: $msg"
                else -> "قطع شد"
            }
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Aurora-R VPN", NotificationManager.IMPORTANCE_LOW
            )
            ch.setDescription("وضعیت تانل Aurora-R")
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurora-R")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(1, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
