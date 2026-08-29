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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.r.*

enum class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Filled.Shield),
    SCAN("Scan", Icons.Filled.Radar),
    SERVERS("Servers", Icons.Filled.Dns),
    SETTINGS("Settings", Icons.Filled.Settings),
    ABOUT("About", Icons.Filled.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraApp(vm: MainViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        containerColor = AuroraBlack,
        contentWindowInsets = WindowInsets.safeDrawing,   // fixes tabs hidden behind system bars
        topBar = { AuroraTopBar() },
        bottomBar = {
            NavigationBar(containerColor = AuroraSurface, tonalElevation = 0.dp) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = s.title) },
                        label = { Text(s.title, fontSize = 10.sp) },
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
                Screen.SCAN -> ScanScreen(vm)
                Screen.SERVERS -> ServersScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.ABOUT -> AboutScreen(vm)
            }
        }
    }
}

@Composable
private fun AuroraTopBar() {
    Surface(color = AuroraBlack) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
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
                        brush = Brush.horizontalGradient(listOf(AuroraGoldBright, AuroraGold, AuroraGoldDim))
                    )
                )
                Text("by @iprez", color = AuroraTextDim, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(50), color = AuroraSurface2) {
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

/* ----------------------------------------------------------------- HOME -- */

@Composable
private fun HomeScreen(vm: MainViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val state by vm.state.collectAsState()
    val msg by vm.statusMsg.collectAsState()
    val tx by vm.txBytes.collectAsState()
    val rx by vm.rxBytes.collectAsState()
    val cfg by vm.config.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        if (vm.nativeError != null) {
            AuroraCard {
                Text("Native library error", color = AuroraRed, fontWeight = FontWeight.Bold)
                Text(vm.nativeError!!, color = AuroraTextMid, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
        }

        ConnectButton(
            state = state,
            onClick = {
                if (state == VpnState.CONNECTED || state == VpnState.CONNECTING) onDisconnect() else onConnect()
            }
        )

        Spacer(Modifier.height(10.dp))
        Text(
            msg.ifBlank { "Tap to connect" },
            color = AuroraTextMid, fontSize = 13.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        AuroraCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Download", formatBytes(rx), Icons.Filled.ArrowDownward)
                Divider(Modifier.height(46.dp).width(1.dp), color = AuroraDivider)
                StatColumn("Upload", formatBytes(tx), Icons.Filled.ArrowUpward)
            }
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("ACTIVE CONFIGURATION")
            InfoRow("Protocol", Protocol.from(cfg.protocol).label)
            InfoRow(
                "Server",
                if (cfg.useManualEndpoint && cfg.manualEndpoint.isNotBlank()) cfg.manualEndpoint
                else "Auto scan (${ScanMode.from(cfg.scanMode).label})"
            )
            InfoRow("Obfuscation", NoizeProfile.from(cfg.noizeProfile).label)
            InfoRow("Mode", "TUN — whole device")
            InfoRow("Aether core", "v${vm.version}")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatColumn(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = AuroraGold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = AuroraTextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AuroraTextDim, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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

/* ----------------------------------------------------------------- SCAN -- */

@Composable
private fun ScanScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsState()
    val scanState by vm.scanState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        AuroraCard {
            SectionTitle("ENDPOINT SCANNER")
            Text(
                "Discover a working endpoint automatically. The core probes candidates and validates a real data path before returning one.",
                color = AuroraTextDim, fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            EnumSelector("Scan mode", ScanMode.entries.map { Triple(it.id, it.label, it.hint) },
                cfg.scanMode) { vm.updateConfig { c -> c.copy(scanMode = it) } }

            Spacer(Modifier.height(8.dp))
            EnumSelector("IP family", IpFamily.entries.map { Triple(it.id, it.label, "") },
                cfg.ipFamily) { vm.updateConfig { c -> c.copy(ipFamily = it) } }

            Spacer(Modifier.height(16.dp))
            if (scanState == ScanState.RUNNING) {
                OutlineGoldButton("Scanning…", onClick = {}, enabled = false)
            } else {
                GoldButton("Start scan", onClick = { vm.runScan() })
            }
        }

        if (vm.scanLog.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AuroraCard {
                SectionTitle("LOG")
                vm.scanLog.forEach { line ->
                    Text("• $line", color = AuroraTextMid, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        if (vm.scanResults.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AuroraCard {
                SectionTitle("RESULTS")
                vm.scanResults.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(r.address, color = AuroraGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(Protocol.from(r.protocol).label, color = AuroraTextDim, fontSize = 11.sp)
                        }
                        if (r.reachable == true) {
                            Icon(Icons.Filled.CheckCircle, null, tint = AuroraGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                    Divider(color = AuroraDivider, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text("The found endpoint is saved and selected automatically. Go to Home to connect.",
                    color = AuroraTextDim, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/* -------------------------------------------------------------- SERVERS -- */

@Composable
private fun ServersScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsState()
    val endpoints by vm.endpoints.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        AuroraCard {
            SectionTitle("SERVER SOURCE")
            ModeOption("Auto scan", "Let the core find the best endpoint",
                !cfg.useManualEndpoint) { vm.updateConfig { it.copy(useManualEndpoint = false) } }
            Spacer(Modifier.height(8.dp))
            ModeOption("Manual server", "Enter your own IP:PORT",
                cfg.useManualEndpoint) { vm.updateConfig { it.copy(useManualEndpoint = true) } }
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("CURRENT SERVER")
            Text(
                if (cfg.useManualEndpoint) cfg.manualEndpoint.ifBlank { "— none selected —" }
                else "Auto scan",
                color = if (cfg.useManualEndpoint && cfg.manualEndpoint.isBlank()) AuroraTextDim else AuroraGold,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            GoldButton("Add server", onClick = { showAdd = true })
        }

        if (endpoints.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AuroraCard {
                SectionTitle("SAVED SERVERS")
                endpoints.forEach { ep ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = cfg.manualEndpoint == ep.address && cfg.useManualEndpoint,
                            onClick = { vm.updateConfig { it.copy(manualEndpoint = ep.address, protocol = ep.protocol, useManualEndpoint = true) } },
                            colors = RadioButtonDefaults.colors(selectedColor = AuroraGold, unselectedColor = AuroraTextDim)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ep.name, color = AuroraTextHigh, fontSize = 14.sp)
                            Text("${ep.address} • ${Protocol.from(ep.protocol).label}", color = AuroraTextDim, fontSize = 11.sp)
                        }
                        IconButton(onClick = { vm.removeEndpoint(ep) }) {
                            Icon(Icons.Filled.Delete, "delete", tint = AuroraRed)
                        }
                    }
                    Divider(color = AuroraDivider, thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = AuroraGold, unselectedColor = AuroraTextDim))
            Column {
                Text(title, color = if (selected) AuroraGold else AuroraTextHigh, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AuroraTextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AddEndpointDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf(Protocol.MASQUE.id) }
    val valid = addr.matches(Regex("""^\[?[0-9a-fA-F:.]+]?:\d{1,5}$"""))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraSurface,
        titleContentColor = AuroraGold,
        textContentColor = AuroraTextHigh,
        title = { Text("Add manual server", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") },
                    singleLine = true, colors = auroraFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(addr, { addr = it.trim() }, label = { Text("IP:PORT") },
                    placeholder = { Text("e.g. 162.159.192.1:2408") }, singleLine = true,
                    isError = addr.isNotBlank() && !valid, colors = auroraFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Protocol", color = AuroraTextMid, fontSize = 12.sp)
                Protocol.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = proto == p.id, onClick = { proto = p.id },
                            colors = RadioButtonDefaults.colors(selectedColor = AuroraGold))
                        Text(p.label, color = AuroraTextHigh, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { addr }, addr, proto) }, enabled = valid) {
                Text("Save", color = if (valid) AuroraGold else AuroraTextDim)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuroraTextMid) } }
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

/* ------------------------------------------------------------- SETTINGS -- */

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        AuroraCard {
            SectionTitle("PROTOCOL")
            EnumSelector("Tunnel protocol", Protocol.entries.map { Triple(it.id, it.label, it.hint) },
                cfg.protocol) { vm.updateConfig { c -> c.copy(protocol = it) } }
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("OBFUSCATION (NOIZE)")
            EnumSelector("Profile", NoizeProfile.entries.map { Triple(it.id, it.label, it.hint) },
                cfg.noizeProfile) { vm.updateConfig { c -> c.copy(noizeProfile = it) } }
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("ADVANCED")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Encrypted ClientHello (ECH)", color = AuroraTextHigh, fontSize = 14.sp)
                    Text("Hide the SNI when supported", color = AuroraTextDim, fontSize = 11.sp)
                }
                Switch(checked = cfg.enableEch, onCheckedChange = { vm.updateConfig { c -> c.copy(enableEch = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = AuroraBlack, checkedTrackColor = AuroraGold))
            }
            InfoRow("Local SOCKS5 port", cfg.socksPort.toString())
            InfoRow("Tunnel mode", "TUN (whole device)")
            Text("In TUN mode all device traffic goes through the tunnel, not just proxy-aware apps.",
                color = AuroraTextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EnumSelector(
    label: String,
    options: List<Triple<String, String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(label, color = AuroraTextMid, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        options.forEach { (id, text, hint) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected == id) AuroraGold.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == id, onClick = { onSelect(id) },
                        colors = RadioButtonDefaults.colors(selectedColor = AuroraGold, unselectedColor = AuroraTextDim))
                    Column {
                        Text(text, color = if (selected == id) AuroraGold else AuroraTextHigh, fontSize = 14.sp)
                        if (hint.isNotBlank()) Text(hint, color = AuroraTextDim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- ABOUT -- */

@Composable
private fun AboutScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(28.dp), color = AuroraSurface, modifier = Modifier.size(110.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("AR", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.verticalGradient(listOf(AuroraGoldBright, AuroraGoldDim))))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Aurora-R", color = AuroraTextHigh, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.0.0", color = AuroraTextDim, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))

        AuroraCard {
            SectionTitle("DEVELOPER")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Rez", color = AuroraTextHigh, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Developer & designer", color = AuroraTextDim, fontSize = 11.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = AuroraGold) {
                    Text("@iprez", color = AuroraBlack, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Telegram: @iprez", color = AuroraGold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(14.dp))

        AuroraCard {
            SectionTitle("CORE")
            InfoRow("Engine", "Aether v${vm.version}")
            InfoRow("TUN tunnel", "hev-socks5-tunnel")
            InfoRow("Protocols", "MASQUE / WireGuard / Gool")
            InfoRow("Core license", "AGPL-3.0")
            Spacer(Modifier.height(8.dp))
            Text("Built for personal use. Crypto core from the open-source Aether project (CluvexStudio).",
                color = AuroraTextDim, fontSize = 11.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}
