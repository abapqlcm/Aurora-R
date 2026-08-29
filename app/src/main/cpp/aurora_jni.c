/*
 * aurora_jni.c — پل JNI بین Kotlin و کتابخانه‌های بومی
 *
 * دو کتابخانه پوشش داده می‌شود:
 *   1) libaether.so            → هسته Aether (C API با JSON)
 *   2) libhev-socks5-tunnel.so → مبدل TUN ⇄ SOCKS5 (حالت TUN)
 *
 * نکته مهم درباره‌ی حلقه‌ی مسیریابی:
 *   سوکت‌هایی که libaether باز می‌کند نمی‌توانند با VpnService.protect() محافظت
 *   شوند (چون Rust مستقیم سوکت می‌سازد). به‌جای آن، در سمت Kotlin خودِ اپ با
 *   addDisallowedApplication از تانل مستثنا می‌شود تا حلقه ایجاد نشود.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "aether.h"
#include "hev-socks5-tunnel.h"

#define TAG "AuroraNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ---------------------------------------------------------------- کمکی --- */

/* رشته C که هسته برگردانده را به jstring تبدیل و بعد آزاد می‌کند */
static jstring take_reply(JNIEnv *env, char *raw)
{
    if (raw == NULL) {
        return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"the core returned nothing\"}");
    }
    jstring out = (*env)->NewStringUTF(env, raw);
    aether_string_free(raw);
    return out;
}

/* ------------------------------------------------------- Aether: عمومی --- */

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeVersion(JNIEnv *env, jclass clazz)
{
    (void)clazz;
    return take_reply(env, aether_version());
}

/* --------------------------------------------------------- Aether: job --- */

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeJobPoll(JNIEnv *env, jclass clazz, jlong id)
{
    (void)clazz;
    return take_reply(env, aether_job_poll((uint64_t)id));
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeJobCancel(JNIEnv *env, jclass clazz, jlong id)
{
    (void)clazz;
    return take_reply(env, aether_job_cancel((uint64_t)id));
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeJobFree(JNIEnv *env, jclass clazz, jlong id)
{
    (void)clazz;
    return take_reply(env, aether_job_free((uint64_t)id));
}

/* ---------------------------------------------------- Aether: identity --- */

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeIdentityOpen(JNIEnv *env, jclass clazz, jstring payload)
{
    (void)clazz;
    const char *json = (*env)->GetStringUTFChars(env, payload, NULL);
    char *reply = aether_identity_open(json);
    (*env)->ReleaseStringUTFChars(env, payload, json);
    return take_reply(env, reply);
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeIdentitySummary(JNIEnv *env, jclass clazz, jlong id)
{
    (void)clazz;
    return take_reply(env, aether_identity_summary((uint64_t)id));
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeIdentityFree(JNIEnv *env, jclass clazz, jlong id)
{
    (void)clazz;
    return take_reply(env, aether_identity_free((uint64_t)id));
}

/* -------------------------------------------------------- Aether: scan --- */

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeScanStart(JNIEnv *env, jclass clazz,
                                             jlong identity, jstring payload)
{
    (void)clazz;
    const char *json = (*env)->GetStringUTFChars(env, payload, NULL);
    char *reply = aether_scan_start((uint64_t)identity, json);
    (*env)->ReleaseStringUTFChars(env, payload, json);
    return take_reply(env, reply);
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeVerifyStart(JNIEnv *env, jclass clazz,
                                               jlong identity, jstring payload)
{
    (void)clazz;
    const char *json = (*env)->GetStringUTFChars(env, payload, NULL);
    char *reply = aether_verify_start((uint64_t)identity, json);
    (*env)->ReleaseStringUTFChars(env, payload, json);
    return take_reply(env, reply);
}

/* ------------------------------------------------------ Aether: tunnel --- */

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeTunnelStart(JNIEnv *env, jclass clazz,
                                               jlong identity, jstring payload)
{
    (void)clazz;
    const char *json = (*env)->GetStringUTFChars(env, payload, NULL);
    char *reply = aether_tunnel_start((uint64_t)identity, json);
    (*env)->ReleaseStringUTFChars(env, payload, json);
    return take_reply(env, reply);
}

JNIEXPORT jstring JNICALL
Java_com_aurora_r_AetherCore_nativeCoreStart(JNIEnv *env, jclass clazz, jstring arguments)
{
    (void)clazz;
    const char *json = (*env)->GetStringUTFChars(env, arguments, NULL);
    char *reply = aether_core_start(json);
    (*env)->ReleaseStringUTFChars(env, arguments, json);
    return take_reply(env, reply);
}

/* -------------------------------------------- Aether: متغیرهای محیطی ----- */

/*
 * هسته Aether تنظیمات را از متغیرهای محیطی هم می‌خواند (AETHER_SOCKS,
 * AETHER_CONFIG, AETHER_NOIZE, AETHER_LOG_LEVEL, ...). چون setenv جاوا روی
 * محیط پروسه اثر ندارد، از سمت بومی ست می‌شود.
 */
JNIEXPORT jint JNICALL
Java_com_aurora_r_AetherCore_nativeSetEnv(JNIEnv *env, jclass clazz,
                                          jstring key, jstring value)
{
    (void)clazz;
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = (*env)->GetStringUTFChars(env, value, NULL);
    int rc = setenv(k, v, 1);
    LOGI("setenv %s=%s -> %d", k, v, rc);
    (*env)->ReleaseStringUTFChars(env, key, k);
    (*env)->ReleaseStringUTFChars(env, value, v);
    return (jint)rc;
}

JNIEXPORT jint JNICALL
Java_com_aurora_r_AetherCore_nativeUnsetEnv(JNIEnv *env, jclass clazz, jstring key)
{
    (void)clazz;
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    int rc = unsetenv(k);
    (*env)->ReleaseStringUTFChars(env, key, k);
    return (jint)rc;
}

/* ================================================ tun2socks (حالت TUN) === */

/*
 * این تابع بلوکه می‌شود تا زمانی که nativeTunnelStop صدا زده شود، پس باید در
 * یک ترد جدا از سمت Kotlin اجرا شود.
 */
JNIEXPORT jint JNICALL
Java_com_aurora_r_TunBridge_nativeTunnelRun(JNIEnv *env, jclass clazz,
                                            jstring config, jint tunFd)
{
    (void)clazz;
    const char *cfg = (*env)->GetStringUTFChars(env, config, NULL);
    jsize len = (*env)->GetStringUTFLength(env, config);

    LOGI("hev tunnel starting on fd=%d (config %d bytes)", (int)tunFd, (int)len);
    int rc = hev_socks5_tunnel_main_from_str((const unsigned char *)cfg,
                                            (unsigned int)len, (int)tunFd);
    LOGI("hev tunnel exited rc=%d", rc);

    (*env)->ReleaseStringUTFChars(env, config, cfg);
    return (jint)rc;
}

JNIEXPORT void JNICALL
Java_com_aurora_r_TunBridge_nativeTunnelStop(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;
    LOGI("hev tunnel stop requested");
    hev_socks5_tunnel_quit();
}

/* آمار ترافیک: [txPackets, txBytes, rxPackets, rxBytes] */
JNIEXPORT jlongArray JNICALL
Java_com_aurora_r_TunBridge_nativeTunnelStats(JNIEnv *env, jclass clazz)
{
    (void)clazz;
    size_t tx_pkt = 0, tx_bytes = 0, rx_pkt = 0, rx_bytes = 0;
    hev_socks5_tunnel_stats(&tx_pkt, &tx_bytes, &rx_pkt, &rx_bytes);

    jlong values[4];
    values[0] = (jlong)tx_pkt;
    values[1] = (jlong)tx_bytes;
    values[2] = (jlong)rx_pkt;
    values[3] = (jlong)rx_bytes;

    jlongArray out = (*env)->NewLongArray(env, 4);
    if (out != NULL) {
        (*env)->SetLongArrayRegion(env, out, 0, 4, values);
    }
    return out;
}
