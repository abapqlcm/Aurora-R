package com.aurora.r

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.aurora.r.ui.AuroraApp
import com.aurora.r.ui.AuroraTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // نتیجه‌ی درخواست مجوز VPN
    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                vm.startService()
            }
        }

    // درخواست مجوز نوتیفیکیشن (اندروید ۱۳+)
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* اختیاری */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AuroraTheme {
                AuroraApp(
                    vm = vm,
                    onConnect = { requestConnect() },
                    onDisconnect = { vm.disconnect() }
                )
            }
        }
    }

    private fun requestConnect() {
        val prepare: Intent? = vm.connect()
        if (prepare != null) {
            // نیاز به مجوز کاربر برای VPN
            vpnPermission.launch(prepare)
        }
        // اگر null بود یعنی مجوز از قبل هست و سرویس شروع شده
    }
}
