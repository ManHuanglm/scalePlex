package com.scaleplex.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DigiTables
import java.io.File
import javax.swing.JFileChooser

/** 条码格式编号 → 描述（DIGI EAN 条码格式表）。 */
private fun formatName(no: Int): String {
    val entry = DigiTables.EAN_BARCODE_FORMATS.firstOrNull { it.first == no }
    return if (no == 0) tr("settings.formatDefault") else "$no · ${entry?.second ?: no}"
}

/** 设置项标签统一宽度，保证右侧控件对齐。 */
private val LABEL_WIDTH = 180.dp

/**
 * 系统设置面板：语言、通信参数、调试与下发、官方工具目录、数据库备份/恢复。
 * 内容超出窗口高度时纵向滚动；保存按钮固定在标题栏右上角。
 */
@Composable
fun SettingsPanel(state: AppState, refresh: MutableState<Int>) {
    var cfg by remember { mutableStateOf(state.config) }
    var message by remember { mutableStateOf("") }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    val scroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(
            title = tr("tab.settings"),
            subtitle = message.takeIf { it.isNotBlank() },
        ) {
            Button(onClick = {
                state.applyConfig(cfg)
                message = tr("settings.saved")
                refresh.value++
            }) { Text(tr("settings.save")) }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- 通用 ----
                SettingsSection(tr("settings.section.general")) {
                    SettingRow(tr("settings.language")) {
                        OutlinedButton(onClick = {
                            val next = if (cfg.language.startsWith("zh")) "en-US" else "zh-CN"
                            cfg = cfg.copy(language = next)
                            state.applyConfig(cfg)
                            state.configService.applyLocale(next)
                            message = tr("settings.saved")
                            refresh.value++
                        }) {
                            Text(if (cfg.language.startsWith("zh")) "中文 → English" else "English → 中文")
                        }
                    }
                    SettingRow(tr("settings.darkMode")) {
                        Switch(state.darkMode, { state.applyDarkMode(it) })
                    }
                }

                // ---- 通信参数 ----
                SettingsSection(tr("settings.comm")) {
                    SettingRow(tr("settings.timeout")) {
                        NumberField(cfg.connectTimeoutMs.toString(), 140.dp) { n ->
                            cfg = cfg.copy(connectTimeoutMs = n)
                        }
                    }
                    SettingRow(tr("settings.retries")) {
                        NumberField(cfg.retries.toString(), 100.dp) { n -> cfg = cfg.copy(retries = n) }
                    }
                    SettingRow(tr("settings.heartbeat")) {
                        // 在线检测间隔预设：5s ~ 10 分钟可选（寺冈 b 族过密探测会触发秤端连接锁）
                        var hbExpanded by remember { mutableStateOf(false) }
                        val hbOptions = listOf(5000, 10000, 30000, 60000, 120000, 300000, 600000)
                        fun hbLabel(ms: Int): String =
                            if (ms < 60000) "${ms / 1000} ${tr("time.seconds")}" else "${ms / 60000} ${tr("time.minutes")}"
                        Box {
                            OutlinedButton(onClick = { hbExpanded = true }) {
                                Text(hbLabel(cfg.heartbeatIntervalMs), fontSize = 12.sp)
                            }
                            DropdownMenu(hbExpanded, { hbExpanded = false }) {
                                hbOptions.forEach { ms ->
                                    DropdownMenuItem(onClick = {
                                        cfg = cfg.copy(heartbeatIntervalMs = ms)
                                        hbExpanded = false
                                    }) { Text(hbLabel(ms)) }
                                }
                            }
                        }
                    }
                }

                // ---- 调试与下发 ----
                SettingsSection(tr("settings.section.debug")) {
                    SettingRow(tr("settings.rawlog")) {
                        Switch(cfg.rawPacketLog, {
                            cfg = cfg.copy(rawPacketLog = it)
                            state.applyConfig(cfg)
                        })
                    }
                    // 日志调试：查看运行日志/报文捕获（可按级别、设备、关键字筛选）
                    var showLogDebug by remember { mutableStateOf(false) }
                    SettingRow(tr("log.debug.actions")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showLogDebug = true }) { Text(tr("log.debug.open")) }
                            OutlinedButton(onClick = {
                                openLogFolder().fold(
                                    onSuccess = { message = tr("log.folderOpened") },
                                    onFailure = { message = tr("log.folderOpenFailed") + (it.message ?: "") },
                                )
                            }) { Text(tr("log.debug.openFolder")) }
                        }
                    }
                    if (showLogDebug) {
                        LogDebugDialog(state, onDismiss = { showLogDebug = false })
                    }
                    // 自动下发（参考银豹"自动下发"：启动后自动向全部秤下发一次全量 PLU）
                    SettingRow(tr("settings.autoDownload")) {
                        Switch(cfg.autoDownload, {
                            cfg = cfg.copy(autoDownload = it)
                            state.applyConfig(cfg)
                        })
                    }
                    // 条码格式批量应用（参考银豹"条码秤格式"选择）
                    SettingRow(tr("settings.barcodeFormat")) {
                        var formatExpanded by remember { mutableStateOf(false) }
                        var applyMessage by remember { mutableStateOf("") }
                        Box {
                            OutlinedButton(onClick = { formatExpanded = true }) {
                                Text(formatName(cfg.barcodeFormat))
                            }
                            DropdownMenu(formatExpanded, { formatExpanded = false }) {
                                DigiTables.EAN_BARCODE_FORMATS.forEach { (no, name) ->
                                    DropdownMenuItem(onClick = {
                                        cfg = cfg.copy(barcodeFormat = no)
                                        state.applyConfig(cfg)
                                        val n = state.pluService.bulkEdit(
                                            state.pluService.all().map { it.pluNo },
                                        ) { it.copy(barcodeFormat = no) }
                                        applyMessage = tr("settings.applyDone") + n
                                        formatExpanded = false
                                        refresh.value++
                                    }) { Text("$no · $name") }
                                }
                            }
                        }
                        if (applyMessage.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(applyMessage, fontSize = 12.sp, color = UiColors.Success)
                        }
                    }
                }

                // ---- 官方工具目录 ----
                SettingsSection(tr("settings.section.tools")) {
                    ToolDirRow(
                        label = tr("settings.mtDll"),
                        value = cfg.mtApiDllPath,
                        hint = tr("settings.mtDllHint"),
                        onChange = { cfg = cfg.copy(mtApiDllPath = it) },
                        selectDirectory = false,
                    )
                    ToolDirRow(
                        label = tr("settings.digiTools"),
                        value = cfg.digiToolsPath,
                        hint = tr("settings.digiToolsHint"),
                        onChange = { cfg = cfg.copy(digiToolsPath = it) },
                        selectDirectory = true,
                    )
                    ToolDirRow(
                        label = tr("settings.dahuaTools"),
                        value = cfg.dahuaToolsPath,
                        hint = tr("settings.dahuaToolsHint"),
                        onChange = { cfg = cfg.copy(dahuaToolsPath = it) },
                        selectDirectory = true,
                    )
                    ToolDirRow(
                        label = tr("settings.aclasTools"),
                        value = cfg.aclasToolsPath,
                        hint = tr("settings.aclasToolsHint"),
                        onChange = { cfg = cfg.copy(aclasToolsPath = it) },
                        selectDirectory = true,
                    )
                    // 托利多三种标签编辑器（VisEdit / bLabel / SmartLabel）由 Scale Manager 安装目录提供
                    ToolDirRow(
                        label = tr("settings.mtLabelEditor"),
                        value = cfg.mtScaleManagerPath,
                        hint = tr("settings.mtLabelEditorHint"),
                        onChange = { cfg = cfg.copy(mtScaleManagerPath = it) },
                        selectDirectory = true,
                    )
                }

                // ---- 数据备份与恢复 ----
                SettingsSection(tr("settings.section.data")) {
                    SettingRow(tr("settings.backup")) {
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
                        OutlinedButton(onClick = {
                            val chooser = JFileChooser()
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                restoreFile = chooser.selectedFile
                            }
                        }) { Text(tr("settings.restore")) }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
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

/** 分组卡片：统一标题样式与间距，避免设置项平铺难以扫读。 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.primary,
            )
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            content()
        }
    }
}

/** 设置行：左侧固定宽度标签 + 右侧控件。 */
@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(LABEL_WIDTH),
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 数字输入框（仅接受整数）。 */
@Composable
private fun NumberField(value: String, width: Dp, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> s.toIntOrNull()?.let(onChange) },
        singleLine = true,
        modifier = Modifier.width(width),
    )
}

/** 工具目录行：路径输入 + 浏览按钮。 */
@Composable
private fun ToolDirRow(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    selectDirectory: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(LABEL_WIDTH),
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(hint, fontSize = 11.sp) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = if (selectDirectory) {
                JFileChooser.DIRECTORIES_ONLY
            } else {
                JFileChooser.FILES_ONLY
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onChange(chooser.selectedFile.absolutePath)
            }
        }) { Text(tr("settings.browse")) }
    }
}
