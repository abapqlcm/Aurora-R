/*
 * aether.h — هدر C برای libaether (Aether v1.8.0)
 * این هدر توسط پروژه Aether ارائه نشده و از روی امضاهای src/ffi.rs نوشته شده است.
 *
 * قرارداد کلی همه توابع:
 *   - ورودی: رشته JSON (UTF-8، null-terminated) یا شناسه عددی
 *   - خروجی: رشته JSON تازه‌تخصیص‌داده‌شده که باید با aether_string_free آزاد شود
 *   - قالب پاسخ: {"ok": true, ...}  یا  {"ok": false, "error": "..."}
 *
 * کارهای طولانی (open/scan/verify/tunnel) به‌صورت job اجرا می‌شوند:
 *   1) تابع صدا زده می‌شود → {"ok":true,"job":<id>}
 *   2) با aether_job_poll(<id>) وضعیت گرفته می‌شود:
 *        {"ok":true,"state":"running"}
 *        {"ok":true,"state":"done","result":{...}}
 *   3) در پایان aether_job_free(<id>) صدا زده می‌شود.
 */

#ifndef AURORA_AETHER_H
#define AURORA_AETHER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* --- عمومی ------------------------------------------------------------- */

/* نسخه هسته: {"ok":true,"version":"1.8.0"} */
char *aether_version(void);

/* آزادسازی هر رشته‌ای که هسته برگردانده است */
void aether_string_free(char *raw);

/* --- مدیریت job ------------------------------------------------------- */

char *aether_job_poll(uint64_t id);
char *aether_job_cancel(uint64_t id);
char *aether_job_free(uint64_t id);

/* --- هویت (Identity) -------------------------------------------------- */

/*
 * payload:
 * {
 *   "path": "/data/.../aether.toml",   (اجباری) مسیر فایل هویت
 *   "transport": "masque"|"wg"|"gool", (اختیاری، پیش‌فرض masque)
 *   "model": "PC",                     (اختیاری)
 *   "locale": "en-US"                  (اختیاری)
 * }
 * خروجی job → {"identity":<id>,"summary":{...},"path":"...","lastconn_path":"..."}
 */
char *aether_identity_open(const char *payload);
char *aether_identity_summary(uint64_t id);
char *aether_identity_free(uint64_t id);

/* --- اسکن endpoint ---------------------------------------------------- */

/*
 * payload:
 * {
 *   "transport": "masque"|"wg"|"gool",
 *   "mode": "turbo"|"balanced"|"thorough",
 *   "ip": "v4"|"v6"|"both",
 *   "profile": "firewall"|"balanced"|...,
 *   "ports": [443, 500, 1701, 2408, 4500],
 *   "excluded": ["1.2.3.4:443"],
 *   "ech": false
 * }
 * خروجی job → {"endpoint":"IP:PORT"}
 */
char *aether_scan_start(uint64_t identity, const char *payload);

/* --- بررسی endpoint (بدون بالا آوردن تانل کامل) ------------------------ */

/* همان payload تانل؛ خروجی job → {"reachable":true|false} */
char *aether_verify_start(uint64_t identity, const char *payload);

/* --- تانل ------------------------------------------------------------- */

/*
 * payload:
 * {
 *   "peer": "IP:PORT",                 (اجباری)
 *   "transport": "masque"|"wg"|"gool",
 *   "socks": "127.0.0.1:1819",         (آدرس SOCKS5 محلی)
 *   "http": "127.0.0.1:1820",          (اختیاری، پروکسی HTTP)
 *   "profile": "firewall"|...,          (پروفایل مبهم‌سازی/noize)
 *   "keepalive": 25,
 *   "ech": false
 * }
 * این job تا قطع شدن اتصال یا cancel در حالت running می‌ماند.
 * خروجی نهایی → {"state":"closed"} یا {"state":"stopped"}
 */
char *aether_tunnel_start(uint64_t identity, const char *payload);

/* --- اجرای هسته با آرگومان‌های CLI (مسیر ساده و یکجا) ------------------- */

/* arguments: آرایه JSON از رشته‌ها، مثل ["--protocol","masque","--socks","127.0.0.1:1819"] */
char *aether_core_start(const char *arguments);

/* --- Cloudflare Zero Trust (تیمی) — در Aurora-R استفاده نمی‌شود -------- */

char *aether_team_sign_in(const char *payload);
char *aether_team_code_request(const char *payload);
char *aether_team_code_resend(uint64_t session);
char *aether_team_code_submit(uint64_t session, const char *code);
char *aether_team_session_free(uint64_t id);
char *aether_team_token_set(const char *token);
char *aether_team_token_clear(void);

#ifdef __cplusplus
}
#endif

#endif /* AURORA_AETHER_H */
