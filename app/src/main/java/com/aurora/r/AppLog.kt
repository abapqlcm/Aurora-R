package com.aurora.r

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppLog — لاگ داخلی اپ که در صفحه Settings › Diagnostics نمایش داده می‌شود.
 *
 * دلیل وجودش: روی گوشی کاربر logcat در دسترس نیست، پس هر خطای هسته باید
 * داخل خود اپ دیده و کپی شود، وگرنه دیباگ کردن کور است.
 */
object AppLog {

    private const val MAX_LINES = 500
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val main = Handler(Looper.getMainLooper())

    val lines = mutableStateListOf<String>()

    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    fun e(tag: String, msg: String, t: Throwable) {
        add("E", tag, "$msg :: ${t.javaClass.simpleName}: ${t.message}")
        t.stackTrace.take(6).forEach { add("E", tag, "    at $it") }
    }

    private fun add(level: String, tag: String, msg: String) {
        when (level) {
            "E" -> android.util.Log.e("Aurora/$tag", msg)
            "W" -> android.util.Log.w("Aurora/$tag", msg)
            else -> android.util.Log.i("Aurora/$tag", msg)
        }
        val line = "${stamp.format(Date())} $level/$tag  $msg"
        // SnapshotStateList را روی ترد اصلی تغییر می‌دهیم تا با Compose امن باشد
        main.post {
            lines.add(line)
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
    }

    fun clear() = main.post { lines.clear() }

    fun dump(): String = lines.joinToString("\n")
}
