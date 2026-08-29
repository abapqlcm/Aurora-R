package com.aurora.r.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.r.*

/** صفحه‌های اپ */
enum class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("خانه", Icons.Filled.Shield),
    ENDPOINTS("سرورها", Icons.Filled.Dns),
    SETTINGS("تنظیمات", Icons.Filled.Settings),
    ABOUT("درباره", Icons.Filled.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraApp(vm: MainViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        containerColor = AuroraBlack,
        topBar = { AuroraTopBar() },
        bottomBar = {
            NavigationBar(containerColor = AuroraSurface, tonalElevation = 0.dp) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = s.title) },
                        label = { Text(s.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AuroraBlack,
                            selectedTextColor = AuroraGold,
                            indicatorColor = AuroraGold,
                            unselectedIconColor = AuroraTextDim,
                            unselectedTextColor = AuroraTextDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AuroraBlack)
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm, onConnect, onDisconnect)
                Screen.ENDPOINTS -> EndpointsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.ABOUT -> AboutScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraTopBar() {
    Surface(color = AuroraBlack) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "AURORA-R",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(AuroraGoldBright, AuroraGold, AuroraGoldDim)
                        )
                    )
                )
                Text(
                    "ساخته‌شده توسط @iprez",
                    color = AuroraTextDim,
                    fontSize = 11.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = AuroraSurface2,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    "@iprez",
                    color = AuroraGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------- خانه ------ */

@Composable
private fun HomeScreen(vm: MainViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val state by vm.state.collectAsState()
    val msg by vm.statusMsg.collectAsState()
    val tx by vm.txBytes.collectAsState()
    val rx by vm.rxBytes.collectAsState()
    val cfg by vm.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        ConnectButton(
            state = state,
            onClick = {
                if (state == VpnState.CONNECTED || state == VpnState.CONNECTING) onDisconnect()
                else onConnect()
            }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = msg.ifBlank { "برای اتصال دکمه را بزنید" },
            color = AuroraTextMid,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))

        // کارت آمار ترافیک
        AuroraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn("دانلود", formatBytes(rx), Icons.Filled.ArrowDownward)
                Divider(
                    modifier = Modifier.height(46.dp).width(1.dp),
                    color = AuroraDivider
                )
                StatColumn("آپلود", formatBytes(tx), Icons.Filled.ArrowUpward)
            }
        }

        Spacer(Modifier.height(14.dp))

        // کارت وضعیت فعلی
        AuroraCard {
            SectionTitle("پیکربندی فعال")
            InfoRow("پروتکل", Protocol.from(cfg.protocol).label)
            InfoRow(
                "سرور",
                if (cfg.useManualEndpoint && cfg.manualEndpoint.isNotBlank())
                    cfg.manualEndpoint
                else "اسکن خودکار (${ScanMode.from(cfg.scanMode).label})"
            )
            InfoRow("مبهم‌سازی", NoizeProfile.from(cfg.noizeProfile).label)
            InfoRow("حالت", "TUN — کل دستگاه")
            InfoRow("هسته Aether", "v${vm.version}")
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatColumn(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AuroraGold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = AuroraTextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AuroraTextDim, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AuroraTextMid, fontSize = 13.sp)
        Text(value, color = AuroraTextHigh, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824 -> String.format("%.2f GB", b / 1_073_741_824.0)
    b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576.0)
    b >= 1024 -> String.format("%.0f KB", b / 1024.0)
    else -> "$b B"
}

/* --------------------------------------------------------- سرورها ------- */

@Composable
private fun EndpointsScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsState()
    val endpoints by vm.endpoints.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // انتخاب حالت: اسکن خودکار یا endpoint شخصی
        AuroraCard {
            SectionTitle("روش انتخاب سرور")

            ModeOption(
                title = "اسکن خودکار",
                subtitle = "هسته خودش بهترین endpoint را پیدا می‌کند",
                selected = !cfg.useManualEndpoint,
                onClick = { vm.updateConfig { it.copy(useManualEndpoint = false) } }
            )
            Spacer(Modifier.height(8.dp))
            ModeOption(
                title = "سرور شخصی",
                subtitle = "IP و پورت دلخواه خودت را وارد کن",
                selected = cfg.useManualEndpoint,
                onClick = { vm.updateConfig { it.copy(useManualEndpoint = true) } }
            )
        }

        Spacer(Modifier.height(14.dp))

        if (!cfg.useManualEndpoint) {
            AuroraCard {
                SectionTitle("تنظیمات اسکن")
                EnumSelector(
                    label = "حالت اسکن",
                    options = ScanMode.entries.map { it.id to it.label },
                    selected = cfg.scanMode,
                    onSelect = { vm.updateConfig { c -> c.copy(scanMode = it) } }
                )
                Text(
                    "حالت «سریع» زودتر جواب می‌دهد، «دقیق» شانس پیدا کردن سرور سالم را بالا می‌برد.",
                    color = AuroraTextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            AuroraCard {
                SectionTitle("سرور فعلی")
                Text(
                    cfg.manualEndpoint.ifBlank { "— انتخاب نشده —" },
                    color = if (cfg.manualEndpoint.isBlank()) AuroraTextDim else AuroraGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                GoldButton("افزودن سرور جدید", onClick = { showAdd = true })
            }

            if (endpoints.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AuroraCard {
                    SectionTitle("سرورهای ذخیره‌شده")
                    endpoints.forEach { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = cfg.manualEndpoint == ep.address,
                                onClick = {
                                    vm.updateConfig {
                                        it.copy(manualEndpoint = ep.address, protocol = ep.protocol)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AuroraGold,
                                    unselectedColor = AuroraTextDim
                                )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(ep.name, color = AuroraTextHigh, fontSize = 14.sp)
                                Text(
                                    "${ep.address} • ${Protocol.from(ep.protocol).label}",
                                    color = AuroraTextDim,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { vm.removeEndpoint(ep) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = AuroraRed)
                            }
                        }
                        Divider(color = AuroraDivider, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showAdd) {
        AddEndpointDialog(
            onDismiss = { showAdd = false },
            onSave = { name, addr, proto ->
                vm.addEndpoint(SavedEndpoint(name, addr, proto))
                vm.updateConfig { it.copy(manualEndpoint = addr, protocol = proto, useManualEndpoint = true) }
                showAdd = false
            }
        )
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) AuroraGold.copy(alpha = 0.12f) else AuroraSurface2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AuroraGold,
                    unselectedColor = AuroraTextDim
                )
            )
            Column {
                Text(title, color = if (selected) AuroraGold else AuroraTextHigh,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AuroraTextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AddEndpointDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf(Protocol.MASQUE.id) }
    val valid = addr.matches(Regex("""^\[?[0-9a-fA-F:.]+]?:\d{1,5}$"""))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraSurface,
        titleContentColor = AuroraGold,
        textContentColor = AuroraTextHigh,
        title = { Text("افزودن سرور شخصی", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام (دلخواه)") },
                    singleLine = true,
                    colors = auroraFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = addr,
                    onValueChange = { addr = it.trim() },
                    label = { Text("IP:PORT") },
                    placeholder = { Text("مثلا 162.159.192.1:2408") },
                    singleLine = true,
                    isError = addr.isNotBlank() && !valid,
                    colors = auroraFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("پروتکل", color = AuroraTextMid, fontSize = 12.sp)
                Protocol.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = proto == p.id,
                            onClick = { proto = p.id },
                            colors = RadioButtonDefaults.colors(selectedColor = AuroraGold)
                        )
                        Text(p.label, color = AuroraTextHigh, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.ifBlank { addr }, addr, proto) },
                enabled = valid
            ) { Text("ذخیره", color = if (valid) AuroraGold else AuroraTextDim) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو", color = AuroraTextMid) }
        }
    )
}

@Composable
private fun auroraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuroraGold,
    unfocusedBorderColor = AuroraGoldDim,
    focusedLabelColor = AuroraGold,
    unfocusedLabelColor = AuroraTextDim,
    cursorColor = AuroraGold,
    focusedTextColor = AuroraTextHigh,
    unfocusedTextColor = AuroraTextHigh
)

/* ------------------------------------------------------- تنظیمات -------- */

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        AuroraCard {
            SectionTitle("پروتکل تانل")
            EnumSelector(
                label = "پروتکل",
                options = Protocol.entries.map { it.id to it.label },
                selected = cfg.protocol,
                onSelect = { vm.updateConfig { c -> c.copy(protocol = it) } }
            )
            Text(
                "MASQUE روی HTTP/3 معمولا در شبکه‌های فیلترشده بهتر جواب می‌دهد. " +
                    "Gool دو لایه WireGuard تو در تو است (کندتر ولی مقاوم‌تر).",
                color = AuroraTextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("مبهم‌سازی ترافیک (noize)")
            EnumSelector(
                label = "پروفایل",
                options = NoizeProfile.entries.map { it.id to it.label },
                selected = cfg.noizeProfile,
                onSelect = { vm.updateConfig { c -> c.copy(noizeProfile = it) } }
            )
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("شبکه")
            InfoRow("پورت SOCKS5 محلی", cfg.socksPort.toString())
            InfoRow("حالت تانل", "TUN (کل دستگاه)")
            Text(
                "در حالت TUN همه‌ی ترافیک گوشی از تانل عبور می‌کند، نه فقط اپ‌هایی که پروکسی را قبول می‌کنند.",
                color = AuroraTextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun EnumSelector(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        options.forEach { (id, text) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected == id) AuroraGold.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == id,
                        onClick = { onSelect(id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AuroraGold,
                            unselectedColor = AuroraTextDim
                        )
                    )
                    Text(
                        text,
                        color = if (selected == id) AuroraGold else AuroraTextHigh,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------- درباره ------- */

@Composable
private fun AboutScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // نشان طلایی
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AuroraSurface,
            modifier = Modifier.size(110.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "AR",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.verticalGradient(listOf(AuroraGoldBright, AuroraGoldDim))
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Aurora-R", color = AuroraTextHigh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("نسخه ۱.۰.۰", color = AuroraTextDim, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))

        // کارت سازنده — درخواست صریح کاربر
        AuroraCard {
            SectionTitle("سازنده")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rez", color = AuroraTextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("توسعه‌دهنده و طراح", color = AuroraTextDim, fontSize = 11.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = AuroraGold) {
                    Text(
                        "@iprez",
                        color = AuroraBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "تلگرام: @iprez",
                color = AuroraGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("هسته")
            InfoRow("موتور", "Aether v${vm.version}")
            InfoRow("تانل TUN", "hev-socks5-tunnel")
            InfoRow("پروتکل‌ها", "MASQUE / WireGuard / Gool")
            InfoRow("لایسنس هسته", "AGPL-3.0")
            Spacer(Modifier.height(8.dp))
            Text(
                "این اپ برای استفاده شخصی ساخته شده است. هسته‌ی رمزنگاری از پروژه‌ی " +
                    "متن‌باز Aether (CluvexStudio) استفاده می‌کند.",
                color = AuroraTextDim, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}
