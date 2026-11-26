package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.table.ScrollableTable
import com.scaleplex.app.ui.table.TableHeader
import com.scaleplex.app.ui.table.TableRow
import com.scaleplex.app.ui.table.cellPadding
import com.scaleplex.app.ui.table.rememberTableState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 时间同步对话框（设备管理同款表格布局）：勾选单台或多台秤后可
 * - 「读取」：逐台读取秤内实时时钟（协议需支持，如托利多 DeviceTime Read）；
 * - 「下发当前时间」：把软件时间批量下发到勾选的秤（未勾选默认全部）。
 * 每行的「秤时间 / 结果」列实时回填读取结果或下发结果。
 */
@Composable
fun TimeSyncDialog(state: AppState, onDismiss: () -> Unit) {
    val devices = remember { state.scaleService.devices() }
    val checked = remember { mutableStateListOf<Long>() }
    val results = remember { mutableStateMapOf<Long, Pair<Boolean, String>>() }
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    // 软件当前时间每秒刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }

    val table = rememberTableState(
        listOf(36.dp, 72.dp, 50.dp, 112.dp, 96.dp, 84.dp, 240.dp),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("timeSync.title")) },
        text = {
            Column(modifier = Modifier.width(710.dp)) {
                // 软件当前时间（实时）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("timeSync.pcTime"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(fmtTime(nowTick), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                // 秤表格（勾选 + 状态 + 秤号 + IP + 品牌 + 秤类型 + 秤时间/结果）
                ScrollableTable(
                    table = table,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    header = {
                        TableHeader(table) { index, _ ->
                            if (index == 0) {
                                Checkbox(
                                    checked = devices.isNotEmpty() && checked.containsAll(devices.map { it.id }),
                                    onCheckedChange = { all ->
                                        if (all) {
                                            checked.addAll(devices.map { it.id }.filter { !checked.contains(it) })
                                        } else {
                                            checked.removeAll(devices.map { it.id }.toSet())
                                        }
                                    },
                                    modifier = Modifier.cellPadding(),
                                )
                            } else {
                                Text(
                                    timeSyncHeaders()[index - 1],
                                    Modifier.cellPadding(),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                ) {
                    items(devices.size, key = { devices[it].id }) { i ->
                        val d = devices[i]
                        val status = state.deviceRuntimes[d.id]?.status ?: DeviceStatus.UNKNOWN
                        val result = results[d.id]
                        TableRow(table, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { index, _ ->
                            when (index) {
                                0 -> Checkbox(
                                    checked = checked.contains(d.id),
                                    onCheckedChange = { c -> if (c) checked.add(d.id) else checked.remove(d.id) },
                                    modifier = Modifier.cellPadding(),
                                )
                                1 -> Row(Modifier.cellPadding(), verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(status)
                                    Spacer(Modifier.width(6.dp))
                                    Text(statusLabel(status), fontSize = 11.sp, maxLines = 1)
                                }
                                2 -> Text(scaleNoOf(d), Modifier.cellPadding(), fontSize = 11.sp, maxLines = 1)
                                3 -> Text(d.ip, Modifier.cellPadding(), fontSize = 11.sp, maxLines = 1)
                                4 -> Text(d.brand.displayName, Modifier.cellPadding(), fontSize = 11.sp, maxLines = 1)
                                5 -> Text(d.model, Modifier.cellPadding(), fontSize = 11.sp, maxLines = 1)
                                else -> Text(
                                    result?.second ?: "-",
                                    Modifier.cellPadding(),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = when {
                                        result == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        result.first -> UiColors.Success
                                        else -> UiColors.Error
                                    },
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }

                // 操作按钮：读取 / 下发当前时间（批量）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val targets = devices.filter { checked.contains(it.id) }
                        if (targets.isEmpty()) {
                            summary = tr("timeSync.needSelect")
                            return@OutlinedButton
                        }
                        scope.launch {
                            busy = true
                            var ok = 0
                            var fail = 0
                            for (d in targets) {
                                state.scaleService.readTime(d).fold(
                                    onSuccess = {
                                        results[d.id] = true to it.format(TIME_FORMATTER)
                                        ok++
                                    },
                                    onFailure = {
                                        results[d.id] = false to (tr("timeSync.readFailed") + (it.message ?: ""))
                                        fail++
                                    },
                                )
                            }
                            summary = "${tr("timeSync.readDone")}：${tr("timeSync.successCount")} $ok · " +
                                "${tr("timeSync.failCount")} $fail"
                            busy = false
                        }
                    }) { Text(tr("timeSync.read"), fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !busy, onClick = {
                        // 批量语义与「下发选中设备」一致：未勾选默认全部秤
                        val targets = devices.filter { checked.contains(it.id) }.ifEmpty { devices }
                        scope.launch {
                            busy = true
                            var ok = 0
                            var fail = 0
                            for (d in targets) {
                                state.scaleService.syncTime(d).fold(
                                    onSuccess = {
                                        results[d.id] = true to "✓ ${tr("timeSync.sent")}"
                                        ok++
                                    },
                                    onFailure = {
                                        results[d.id] = false to (tr("timeSync.writeFailed") + (it.message ?: ""))
                                        fail++
                                    },
                                )
                            }
                            summary = "${tr("timeSync.writeDone")}：${tr("timeSync.successCount")} $ok · " +
                                "${tr("timeSync.failCount")} $fail"
                            busy = false
                        }
                    }) { Text(tr("timeSync.write"), fontSize = 12.sp) }
                    Spacer(Modifier.weight(1f))
                    summary?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, enabled = !busy) { Text(tr("common.close")) } },
    )
}

/** 对话框列标题（与设备管理网格一致）。 */
private fun timeSyncHeaders(): List<String> = listOf(
    tr("device.field.status"),
    tr("device.field.no"),
    tr("device.field.ip"),
    tr("device.field.brand"),
    tr("device.field.scaleType"),
    tr("timeSync.result"),
)

private val TIME_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val dialogTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)

private fun fmtTime(millis: Long): String = dialogTimeFormat.format(Date(millis))