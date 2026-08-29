/*
 * hev-socks5-tunnel.h — هدر عمومی hev-socks5-tunnel
 * برگرفته از include/hev-socks5-tunnel.h پروژه heiher/hev-socks5-tunnel
 */

#ifndef HEV_SOCKS5_TUNNEL_H
#define HEV_SOCKS5_TUNNEL_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* شروع تانل از رشته‌ی کانفیگ YAML + توصیفگر فایل TUN؛ بلوکه‌شونده */
int hev_socks5_tunnel_main_from_str (const unsigned char *config_str,
                                     unsigned int config_len, int tun_fd);

/* شروع تانل از مسیر فایل کانفیگ + fd؛ بلوکه‌شونده */
int hev_socks5_tunnel_main_from_file (const char *config_path, int tun_fd);

/* توقف تانل */
void hev_socks5_tunnel_quit (void);

/* آمار ترافیک اینترفیس */
void hev_socks5_tunnel_stats (size_t *tx_packets, size_t *tx_bytes,
                              size_t *rx_packets, size_t *rx_bytes);

#ifdef __cplusplus
}
#endif

#endif /* HEV_SOCKS5_TUNNEL_H */
