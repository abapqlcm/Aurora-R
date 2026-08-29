package com.aurora.r

import kotlinx.serialization.Serializable

/** Tunnel protocols supported by the Aether core */
enum class Protocol(val id: String, val label: String, val hint: String) {
    MASQUE("masque", "MASQUE", "HTTP/3 based — usually best on filtered networks"),
    WIREGUARD("wg", "WireGuard", "Classic WARP tunnel — fast and light"),
    GOOL("gool", "Gool", "WireGuard in WireGuard — slower but more resistant");

    companion object {
        fun from(id: String?): Protocol = entries.firstOrNull { it.id == id } ?: MASQUE
    }
}

/** Endpoint scan mode */
enum class ScanMode(val id: String, val label: String, val hint: String) {
    TURBO("turbo", "Turbo", "Fewest probes — fastest result"),
    BALANCED("balanced", "Balanced", "Good trade-off between speed and quality"),
    THOROUGH("thorough", "Thorough", "Most probes — best chance of a healthy endpoint");

    companion object {
        fun from(id: String?): ScanMode = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/** Traffic obfuscation profile (noize) */
enum class NoizeProfile(val id: String, val label: String, val hint: String) {
    FIREWALL("firewall", "Firewall", "Tuned for aggressive DPI / firewalls"),
    BALANCED("balanced", "Balanced", "Moderate obfuscation, lower overhead"),
    AGGRESSIVE("aggressive", "Aggressive", "Maximum obfuscation, more overhead");

    companion object {
        fun from(id: String?): NoizeProfile = entries.firstOrNull { it.id == id } ?: FIREWALL
    }
}

/** IP family used while scanning */
enum class IpFamily(val id: String, val label: String) {
    V4("v4", "IPv4"),
    V6("v6", "IPv6"),
    BOTH("both", "IPv4 + IPv6");

    companion object {
        fun from(id: String?): IpFamily = entries.firstOrNull { it.id == id } ?: V4
    }
}

/** A saved manual endpoint */
@Serializable
data class SavedEndpoint(
    val name: String,
    val address: String,      // "IP:PORT"
    val protocol: String = "masque"
)

/** A result row produced by the endpoint scanner */
data class ScanResult(
    val address: String,
    val protocol: String,
    val reachable: Boolean? = null,   // null = not verified yet
    val latencyMs: Long? = null
)

/** Overall connection state */
enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, STOPPING, ERROR }

/** Scanner state for the dedicated Scan screen */
enum class ScanState { IDLE, RUNNING, DONE, ERROR }

/** Active configuration the VpnService starts with */
@Serializable
data class ConnectionConfig(
    val protocol: String = "masque",
    val scanMode: String = "balanced",
    val noizeProfile: String = "firewall",
    val ipFamily: String = "v4",
    val useManualEndpoint: Boolean = false,
    val manualEndpoint: String = "",   // "IP:PORT" when useManualEndpoint = true
    val socksPort: Int = 1819,
    val enableEch: Boolean = false,
    val keepaliveSec: Int = 25
)
