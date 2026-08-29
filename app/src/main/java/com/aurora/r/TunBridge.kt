package com.aurora.r

/**
 * TunBridge — پوسته‌ی Kotlin روی hev-socks5-tunnel.
 *
 * [run] بلوکه می‌شود، پس باید در یک ترد جدا صدا زده شود.
 */
object TunBridge {

    private external fun nativeTunnelRun(config: String, tunFd: Int): Int
    private external fun nativeTunnelStop()
    private external fun nativeTunnelStats(): LongArray?

    fun run(config: String, tunFd: Int): Int = nativeTunnelRun(config, tunFd)

    fun stop() = nativeTunnelStop()

    /** [txPackets, txBytes, rxPackets, rxBytes] */
    fun stats(): LongArray = nativeTunnelStats() ?: longArrayOf(0, 0, 0, 0)

    /**
     * کانفیگ YAML برای hev-socks5-tunnel.
     *
     * TUN از VpnService می‌آید (fd)، پس name اینجا لازم نیست.
     * مهم: بلوک ipv6 فقط وقتی نوشته می‌شود که اینترفیس TUN واقعاً آدرس IPv6
     * داشته باشد؛ وگرنه hev هنگام راه‌اندازی خطا می‌دهد و تانل بالا نمی‌آید.
     */
    fun buildConfig(
        socksHost: String,
        socksPort: Int,
        mtu: Int = 8500,
        withIpv6: Boolean = false
    ): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  multi-queue: false")
        appendLine("  ipv4: 198.18.0.1")
        if (withIpv6) appendLine("  ipv6: 'fc00::1'")
        appendLine()
        appendLine("socks5:")
        appendLine("  address: $socksHost")
        appendLine("  port: $socksPort")
        appendLine("  udp: 'udp'")
        appendLine("  pipeline: false")
        appendLine()
        appendLine("misc:")
        appendLine("  task-stack-size: 86016")
        appendLine("  log-level: warn")
        appendLine("  limit-nofile: 65535")
    }
}
