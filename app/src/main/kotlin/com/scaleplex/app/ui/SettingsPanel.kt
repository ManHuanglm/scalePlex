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
import java.io.File
import javax.swing.JFileChooser

/** 系统设置面板：语言、通信参数、报文调试、主题、数据库备份/恢复。 */
@Composable
fun SettingsPanel(state: AppState, refresh: MutableState<Int>) {
    var cfg by remember { mutableStateOf(state.config) }
    var message by remember { mutableStateOf("") }
    var restoreFile by remember { mutableStateOf<File?>(null) }

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

        // Database backup / restore
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("settings.backup"), Modifier.width(160.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                chooser.selectedFile = File("scaleplex_backup.db")
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    runCatching { state.db.backupTo(chooser.selectedFile) }
                        .onSuccess { message = tr("settings.backupDone") }
                        .onFailure { message = it.message ?: "backup failed" }
                }
            }) { Text(tr("settings.backup")) }
            Spacer(Modifier.width(12.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    restoreFile = chooser.selectedFile
                }
            }) { Text(tr("settings.restore")) }
        }

        if (message.isNotBlank()) {
            Text(message, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
        }

        // Save
        Button(onClick = { state.applyConfig(cfg); refresh.value++ }) { Text(tr("settings.save")) }
    }

    restoreFile?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreFile = null },
            title = { Text(tr("settings.restore")) },
            text = { Text(tr("settings.restoreConfirm")) },
            confirmButton = {
                Button(onClick = {
                    runCatching { state.db.restoreFrom(backup) }
                        .onSuccess { message = tr("settings.restoreDone") }
                        .onFailure { message = it.message ?: "restore failed" }
                    restoreFile = null
                }) { Text(tr("common.yes")) }
            },
            dismissButton = { TextButton(onClick = { restoreFile = null }) { Text(tr("common.no")) } },
        )
    }
}
