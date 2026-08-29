package com.aurora.r

/**
 * TunBridge — پوسته‌ی Kotlin روی hev-socks5-tunnel.
 *
 * [run] بلوکه می‌شود، پس باید در یک ترد/کوروتین IO جدا صدا زده شود.
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
     * TUN از VpnService می‌آید (پس name/ipv4 اینجا فقط برای اطلاع هستند و
     * hev از fd داده‌شده استفاده می‌کند).
     */
    fun buildConfig(socksHost: String, socksPort: Int, mtu: Int = 8500): String = """
        tunnel:
          mtu: $mtu
          multi-queue: false
          ipv4: 198.18.0.1
          ipv6: 'fc00::1'
          icmp: 'reply'

        socks5:
          address: $socksHost
          port: $socksPort
          udp: 'udp'
          pipeline: false

        misc:
          task-stack-size: 86016
          log-level: warn
          limit-nofile: 65535
    """.trimIndent()
}
