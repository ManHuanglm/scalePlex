package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr

/** System settings panel: language, comm timing, backup, debug toggles. */
@Composable
fun SettingsPanel(state: AppState, refresh: MutableState<Int>) {
    var cfg by remember { mutableStateOf(state.config) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("tab.settings"), fontSize = 20.sp)

        // Language
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.language"), Modifier.width(160.dp))
            Button(onClick = {
                val next = if (cfg.language.startsWith("zh")) "en-US" else "zh-CN"
                cfg = cfg.copy(language = next)
                state.applyConfig(cfg)
                state.configService.applyLocale(next)
                refresh.value++
            }) { Text(if (cfg.language.startsWith("zh")) "中文 → English" else "English → 中文") }
        }

        // Comm params
        Text(tr("settings.comm"), fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.timeout"), Modifier.width(160.dp))
            OutlinedTextField(cfg.connectTimeoutMs.toString(), { s -> s.toIntOrNull()?.let { cfg = cfg.copy(connectTimeoutMs = it) } }, modifier = Modifier.width(120.dp))
            Spacer(Modifier.width(16.dp))
            Text(tr("settings.retries"), Modifier.width(100.dp))
            OutlinedTextField(cfg.retries.toString(), { s -> s.toIntOrNull()?.let { cfg = cfg.copy(retries = it) } }, modifier = Modifier.width(80.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.heartbeat"), Modifier.width(160.dp))
            OutlinedTextField(cfg.heartbeatIntervalMs.toString(), { s -> s.toIntOrNull()?.let { cfg = cfg.copy(heartbeatIntervalMs = it) } }, modifier = Modifier.width(120.dp))
        }

        // Debug
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.rawlog"), Modifier.width(160.dp))
            Switch(cfg.rawPacketLog, { cfg = cfg.copy(rawPacketLog = it); state.applyConfig(cfg) })
        }

        // Theme
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("深色模式 / Dark mode", Modifier.width(160.dp))
            Switch(state.darkMode, { state.darkMode = it })
        }

        // Save
        Button(onClick = { state.applyConfig(cfg); refresh.value++ }) { Text(tr("settings.save")) }
    }
}
