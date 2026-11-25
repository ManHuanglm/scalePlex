package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 时间同步对话框：选择秤后可「读取」秤内实时时钟（协议需支持，如托利多 DeviceTime Read），
 * 或把软件「当前时间」「下发」到秤（寺冈 SMScaleAPI 仅支持写入方向）。
 */
@Composable
fun TimeSyncDialog(state: AppState, onDismiss: () -> Unit) {
    val devices = remember { state.scaleService.devices() }
    var selected by remember {
        mutableStateOf(
            devices.firstOrNull { state.deviceRuntimes[it.id]?.status == DeviceStatus.ONLINE }
                ?: devices.firstOrNull(),
        )
    }
    var scaleTime by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    // 当前时间每秒刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("timeSync.title")) },
        text = {
            Column(modifier = Modifier.width(460.dp)) {
                // 秤选择
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selected?.let { "${it.name} · ${it.brand.displayName} · ${it.ip}" }
                                ?: tr("device.emptyHint"),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▾", fontSize = 10.sp)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        devices.forEach { d ->
                            DropdownMenuItem(onClick = {
                                selected = d
                                scaleTime = null
                                message = null
                                expanded = false
                            }) { Text("${d.name} · ${d.brand.displayName} · ${d.ip}", fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("timeSync.pcTime"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(fmtTime(nowTick), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("timeSync.scaleTime"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(scaleTime ?: "-", fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = selected != null && !busy, onClick = {
                        val d = selected ?: return@OutlinedButton
                        scope.launch {
                            busy = true
                            message = null
                            val r = state.scaleService.readTime(d)
                            scaleTime = r.getOrNull()?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            message = r.exceptionOrNull()?.let { tr("timeSync.readFailed") + (it.message ?: "") }
                            busy = false
                        }
                    }) { Text(tr("timeSync.read")) }
                    Button(enabled = selected != null && !busy, onClick = {
                        val d = selected ?: return@Button
                        scope.launch {
                            busy = true
                            message = null
                            val r = state.scaleService.syncTime(d)
                            message = r.fold(
                                onSuccess = { tr("timeSync.writeOk") },
                                onFailure = { tr("timeSync.writeFailed") + (it.message ?: "") },
                            )
                            busy = false
                        }
                    }) { Text(tr("timeSync.write")) }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("common.close")) } },
    )
}

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)

private fun fmtTime(millis: Long): String = timeFormat.format(Date(millis))
