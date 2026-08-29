package com.aurora.r

import org.json.JSONObject

/**
 * AetherCore — پوسته‌ی Kotlin روی C API کتابخانه‌ی libaether.
 *
 * همه‌ی توابع بومی رشته‌ی JSON برمی‌گردانند به قالب:
 *   {"ok": true, ...}   یا   {"ok": false, "error": "..."}
 *
 * کارهای طولانی (open/scan/verify/tunnel) یک job برمی‌گردانند که باید با
 * [jobPoll] تا رسیدن به state="done" نظرسنجی شود.
 */
object AetherCore {

    /** اگر بارگذاری کتابخانه‌های بومی شکست بخورد اپ کرش نمی‌کند؛ این پیام در UI نمایش داده می‌شود. */
    @Volatile var loadError: String? = null
        private set

    val available: Boolean get() = loadError == null

    init {
        loadError = try {
            // ترتیب مهم است: کتابخانه‌های وابسته قبل از پل بارگذاری شوند.
            System.loadLibrary("aether")
            System.loadLibrary("hev-socks5-tunnel")
            System.loadLibrary("aurora_jni")
            null
        } catch (e: Throwable) {
            android.util.Log.e("AetherCore", "بارگذاری کتابخانه بومی شکست خورد", e)
            e.message ?: e.toString()
        }
    }

    // --- توابع بومی ---------------------------------------------------
    private external fun nativeVersion(): String
    private external fun nativeJobPoll(id: Long): String
    private external fun nativeJobCancel(id: Long): String
    private external fun nativeJobFree(id: Long): String
    private external fun nativeIdentityOpen(payload: String): String
    private external fun nativeIdentitySummary(id: Long): String
    private external fun nativeIdentityFree(id: Long): String
    private external fun nativeScanStart(identity: Long, payload: String): String
    private external fun nativeVerifyStart(identity: Long, payload: String): String
    private external fun nativeTunnelStart(identity: Long, payload: String): String
    private external fun nativeCoreStart(arguments: String): String
    private external fun nativeSetEnv(key: String, value: String): Int
    private external fun nativeUnsetEnv(key: String): Int

    // --- API سطح بالا -------------------------------------------------

    fun version(): String =
        runCatching { JSONObject(nativeVersion()).optString("version", "?") }
            .getOrDefault("?")

    fun setEnv(key: String, value: String) { nativeSetEnv(key, value) }
    fun unsetEnv(key: String) { nativeUnsetEnv(key) }

    fun identityOpen(payload: JSONObject): JSONObject = JSONObject(nativeIdentityOpen(payload.toString()))
    fun identitySummary(id: Long): JSONObject = JSONObject(nativeIdentitySummary(id))
    fun identityFree(id: Long): JSONObject = JSONObject(nativeIdentityFree(id))

    fun scanStart(identity: Long, payload: JSONObject): JSONObject =
        JSONObject(nativeScanStart(identity, payload.toString()))

    fun verifyStart(identity: Long, payload: JSONObject): JSONObject =
        JSONObject(nativeVerifyStart(identity, payload.toString()))

    fun tunnelStart(identity: Long, payload: JSONObject): JSONObject =
        JSONObject(nativeTunnelStart(identity, payload.toString()))

    fun coreStart(arguments: List<String>): JSONObject {
        val arr = org.json.JSONArray()
        arguments.forEach { arr.put(it) }
        return JSONObject(nativeCoreStart(arr.toString()))
    }

    fun jobPoll(id: Long): JSONObject = JSONObject(nativeJobPoll(id))
    fun jobCancel(id: Long): JSONObject = JSONObject(nativeJobCancel(id))
    fun jobFree(id: Long): JSONObject = JSONObject(nativeJobFree(id))

    /**
     * یک job را تا رسیدن به done نظرسنجی می‌کند.
     * @return آبجکت result داخل پاسخ done، یا استثنا در صورت خطا.
     */
    suspend fun awaitJob(id: Long, pollMs: Long = 400): JSONObject {
        while (true) {
            val reply = jobPoll(id)
            if (!reply.optBoolean("ok", false)) {
                error(reply.optString("error", "job poll failed"))
            }
            when (reply.optString("state")) {
                "done" -> {
                    val result = reply.optJSONObject("result") ?: JSONObject()
                    jobFree(id)
                    if (!result.optBoolean("ok", true)) {
                        error(result.optString("error", "job failed"))
                    }
                    return result
                }
                else -> kotlinx.coroutines.delay(pollMs)
            }
        }
    }
}
