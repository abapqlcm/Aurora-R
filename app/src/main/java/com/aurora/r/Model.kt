package com.aurora.r

import kotlinx.serialization.Serializable

/** پروتکل‌های پشتیبانی‌شده توسط هسته Aether */
enum class Protocol(val id: String, val label: String) {
    MASQUE("masque", "MASQUE (HTTP/3)"),
    WIREGUARD("wg", "WireGuard"),
    GOOL("gool", "Gool (WARP-in-WARP)");

    companion object {
        fun from(id: String?): Protocol = entries.firstOrNull { it.id == id } ?: MASQUE
    }
}

/** حالت اسکن endpoint */
enum class ScanMode(val id: String, val label: String) {
    TURBO("turbo", "سریع"),
    BALANCED("balanced", "متعادل"),
    THOROUGH("thorough", "دقیق");

    companion object {
        fun from(id: String?): ScanMode = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/** پروفایل مبهم‌سازی (noize) */
enum class NoizeProfile(val id: String, val label: String) {
    FIREWALL("firewall", "فایروال"),
    BALANCED("balanced", "متعادل"),
    AGGRESSIVE("aggressive", "تهاجمی");

    companion object {
        fun from(id: String?): NoizeProfile = entries.firstOrNull { it.id == id } ?: FIREWALL
    }
}

/** یک endpoint شخصی ذخیره‌شده */
@Serializable
data class SavedEndpoint(
    val name: String,
    val address: String,      // "IP:PORT"
    val protocol: String = "masque"
)

/** وضعیت کلی اتصال */
enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, STOPPING, ERROR }

/** پیکربندی فعال که VpnService با آن راه می‌افتد */
@Serializable
data class ConnectionConfig(
    val protocol: String = "masque",
    val scanMode: String = "balanced",
    val noizeProfile: String = "firewall",
    val useManualEndpoint: Boolean = false,
    val manualEndpoint: String = "",   // "IP:PORT" وقتی useManualEndpoint=true
    val socksPort: Int = 1819
)
