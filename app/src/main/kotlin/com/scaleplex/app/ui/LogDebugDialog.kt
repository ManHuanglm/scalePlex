package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.ScaleDevice
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 日志调试对话框（设置页 → 报文调试 → 查看日志）：
 * - 「运行日志」：读取应用日志文件尾部，按级别 / 设备（名称·品牌·型号·IP·秤号）/ 关键字筛选；
 * - 「报文捕获」：显示通信层捕获的原始报文（需开启「记录原始报文」），按设备 IP 与收发方向筛选。
 */
@Composable
fun LogDebugDialog(state: AppState, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val devices = remember { state.scaleService.devices() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("log.title")) },
        text = {
            Column(modifier = Modifier.width(760.dp)) {
                TabRow(selectedTabIndex = tab, backgroundColor = MaterialTheme.colors.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("log.tab.file")) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("log.tab.packet")) })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) FileLogView(devices) else PacketView(state, devices)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(tr("common.close")) }
        },
    )
}

/** 日志文件（与 logback.xml 的 FILE appender 路径保持一致）。 */
fun logFile(): File {
    val dataDir = System.getProperty("scaleplex.data.dir")
    val base = if (!dataDir.isNullOrBlank()) File(dataDir, "logs") else File(System.getProperty("user.home"), ".scaleplex")
    return File(base, "scaleplex.log")
}

/** 打开日志所在目录（资源管理器）。 */
fun openLogFolder(): Result<Unit> = runCatching {
    val dir = logFile().parentFile ?: throw IllegalStateException("日志目录不存在")
    if (!dir.exists()) dir.mkdirs()
    Desktop.getDesktop().open(dir)
}

/** 读取日志文件尾部（最多 [maxLines] 行）。 */
private fun tailLines(file: File, maxLines: Int = 5000): List<String> = try {
    if (file.exists()) file.readLines().takeLast(maxLines) else emptyList()
} catch (_: Exception) {
    emptyList()
}

/** 级别 / 设备 / 关键字 三重筛选控件（[levelTitle] 为第一个下拉的标题，如「级别」「方向」）。 */
@Composable
private fun LogFilterBar(
    devices: List<ScaleDevice>,
    levelTitle: String,
    level: String,
    onLevel: (String) -> Unit,
    device: ScaleDevice?,
    onDevice: (ScaleDevice?) -> Unit,
    keyword: String,
    onKeyword: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 级别/方向下拉
        var levelExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { levelExpanded = true }) {
                Text("$levelTitle：${levelFilterLabel(level)}", fontSize = 12.sp)
            }
            DropdownMenu(levelExpanded, { levelExpanded = false }) {
                listOf("ALL", "ERROR", "WARN", "INFO", "DEBUG").forEach { lv ->
                    DropdownMenuItem(onClick = { onLevel(lv); levelExpanded = false }) {
                        Text(levelFilterLabel(lv))
                    }
                }
            }
        }
        // 设备下拉
        var deviceExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { deviceExpanded = true }) {
                Text(
                    "${tr("log.filter.device")}：${device?.name ?: tr("log.filter.all")}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(deviceExpanded, { deviceExpanded = false }) {
                DropdownMenuItem(onClick = { onDevice(null); deviceExpanded = false }) {
                    Text(tr("log.filter.all"))
                }
                devices.forEach { d ->
                    DropdownMenuItem(onClick = { onDevice(d); deviceExpanded = false }) {
                        Text("${d.name} · ${d.brand.displayName} · ${d.model} · ${d.ip}")
                    }
                }
            }
        }
        OutlinedTextField(
            keyword, onKeyword,
            label = { Text(tr("log.filter.keyword")) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun levelFilterLabel(level: String): String = when (level) {
    "ALL" -> tr("log.filter.all")
    else -> level
}

/** 设备匹配标识（名称/品牌/型号/IP/秤号任一出现在日志行即视为匹配）。 */
private fun deviceTokens(d: ScaleDevice): List<String> = listOf(
    d.ip, d.name, d.model, d.brand.displayName, d.port.toString(),
).filter { it.isNotBlank() }

/** Tab 1：运行日志（文件尾部 + 筛选）。 */
@Composable
private fun FileLogView(devices: List<ScaleDevice>) {
    var level by remember { mutableStateOf("ALL") }
    var device by remember { mutableStateOf<ScaleDevice?>(null) }
    var keyword by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }

    val allLines = remember(refreshTick) { tailLines(logFile()) }
    val tokens = device?.let { deviceTokens(it) }.orEmpty()
    val filtered = allLines.filter { line ->
        val levelOk = when (level) {
            "ALL" -> true
            "INFO" -> line.contains(" INFO ")
            else -> line.contains(level)
        }
        val deviceOk = tokens.isEmpty() || tokens.any { line.contains(it, ignoreCase = true) }
        val kwOk = keyword.isBlank() || line.contains(keyword, ignoreCase = true)
        levelOk && deviceOk && kwOk
    }

    Column(modifier = Modifier.heightIn(max = 460.dp)) {
        LogFilterBar(
            devices, tr("log.filter.level"), level, { level = it }, device, { device = it }, keyword, { keyword = it },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${tr("log.filter.result")} ${filtered.size} / ${allLines.size}",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { refreshTick++ }) { Text(tr("log.refresh")) }
        }
        LogLinesView(filtered)
    }
}

/** 等宽日志行渲染，ERROR/WARN 着色。 */
@Composable
private fun LogLinesView(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 360.dp)
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.04f))
            .padding(6.dp),
    ) {
        if (lines.isEmpty()) {
            Text(tr("log.file.missing"), fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
        }
        lines.takeLast(400).forEach { line ->
            val color = when {
                line.contains(" ERROR ") -> UiColors.Error
                line.contains(" WARN ") -> Color(0xFFC98A00)
                else -> MaterialTheme.colors.onSurface
            }
            Text(
                line,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = color,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                maxLines = 1,
            )
        }
    }
}

/** Tab 2：报文捕获（CommEventBus 快照 + 设备 IP / 收发方向筛选）。 */
@Composable
private fun PacketView(state: AppState, devices: List<ScaleDevice>) {
    var device by remember { mutableStateOf<ScaleDevice?>(null) }
    var direction by remember { mutableStateOf("ALL") }
    var refreshTick by remember { mutableStateOf(0) }

    val packets = remember(refreshTick) { state.commBus.snapshot() }
    val tokens = device?.let { deviceTokens(it) }.orEmpty()
    val filtered = packets.filter { p ->
        val devOk = tokens.isEmpty() || tokens.any { p.remote.contains(it, ignoreCase = true) }
        val dirOk = direction == "ALL" || p.direction.name == direction
        devOk && dirOk
    }

    Column(modifier = Modifier.heightIn(max = 460.dp)) {
        LogFilterBar(
            devices, tr("log.filter.direction"), direction, { direction = it }, device, { device = it }, "", {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${tr("log.filter.result")} ${filtered.size} / ${packets.size}",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { refreshTick++ }) { Text(tr("log.refresh")) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 360.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.04f))
                .padding(6.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(tr("log.packet.none"), fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
            filtered.takeLast(200).forEach { p ->
                val time = SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(Date(p.timestampMs))
                val dirColor = if (p.direction == com.scaleplex.communication.PacketDirection.TX) UiColors.Success else UiColors.Error
                Text(
                    "$time ${p.direction} ${p.remote} ${p.hex().take(96)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    maxLines = 1,
                )
                Text(
                    "    ${p.ascii().take(96)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = dirColor.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    maxLines = 1,
                )
            }
        }
    }
}
