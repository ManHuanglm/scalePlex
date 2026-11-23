package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.ScaleGroup
import com.scaleplex.core.model.SerialParams
import kotlinx.coroutines.launch

/**
 * 设备管理面板：分组筛选与分组管理、设备增删改、按分组一键下发 PLU、串口参数编辑。
 */
@Composable
fun DevicesPanel(state: AppState, refresh: MutableState<Int>) {
    var groupFilter by remember { mutableStateOf<Long?>(null) }
    var devices by remember(refresh.value, groupFilter) {
        mutableStateOf(state.scaleService.devices(groupFilter))
    }
    var groups by remember(refresh.value) { mutableStateOf(state.scaleService.groups()) }
    var editing by remember { mutableStateOf<ScaleDevice?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var groupEditing by remember { mutableStateOf<ScaleGroup?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, com.scaleplex.core.model.BatchResult>?>(null) }
    val checked = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 分组筛选与分组管理 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            var filterExpanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = groups.firstOrNull { it.id == groupFilter }?.name ?: tr("group.all"),
                onValueChange = {}, readOnly = true, label = { Text(tr("device.group")) },
                modifier = Modifier.width(180.dp),
                trailingIcon = { TextButton(onClick = { filterExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(filterExpanded, { filterExpanded = false }) {
                DropdownMenuItem(onClick = { groupFilter = null; filterExpanded = false }) { Text(tr("group.all")) }
                groups.forEach { g ->
                    DropdownMenuItem(onClick = { groupFilter = g.id; filterExpanded = false }) { Text(g.name) }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { groupEditing = ScaleGroup(name = ""); showGroupEditor = true }) { Text(tr("group.add")) }
            groupFilter?.let { gid ->
                groups.firstOrNull { it.id == gid }?.let { g ->
                    TextButton(onClick = { groupEditing = g; showGroupEditor = true }) { Text(tr("group.edit")) }
                    TextButton(onClick = {
                        state.scaleService.deleteGroup(g.id)
                        groupFilter = null
                        refresh.value++
                    }) { Text(tr("group.delete")) }
                    TextButton(onClick = {
                        scope.launch {
                            batchResults = state.scaleService.downloadToGroup(gid, state.pluService.all())
                        }
                    }) { Text(tr("group.download")) }
                }
            }
        }

        // ---- 设备工具栏 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.devices"), Modifier.weight(1f), fontSize = 18.sp)
            Text(
                tr("plu.checkedCount") + " ${checked.size}",
                fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF808080),
            )
            Spacer(Modifier.width(8.dp))
            // 批量下发（参考银豹：勾选多台秤批量下发商品资料）
            Button(onClick = {
                scope.launch {
                    val targets = devices.filter { checked.contains(it.id) }.ifEmpty { devices }
                    batchResults = state.scaleService.downloadToDevices(
                        targets, state.pluService.all(),
                    )
                }
            }) { Text(tr("device.downloadSelected")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                editing = ScaleDevice(name = "", brand = ScaleBrand.DIGI, groupId = groupFilter ?: 0)
                showEditor = true
            }) { Text(tr("device.add")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev -> state.scaleService.syncTime(dev) }
                }
            }) { Text(tr("device.syncTime")) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                tr("device.batchHint"),
                fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF808080),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices, key = { it.id }) { d ->
                val rt = state.deviceRuntimes[d.id]
                val status = rt?.status?.name ?: "UNKNOWN"
                val groupName = groups.firstOrNull { it.id == d.groupId }?.name ?: tr("group.none")
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked.contains(d.id),
                        onCheckedChange = { c -> if (c) checked.add(d.id) else checked.remove(d.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${d.name} · ${d.brand.displayName} · [$groupName]", fontSize = 14.sp)
                        Text("${d.commType} ${d.ip}:${d.port}  [${status}]", fontSize = 11.sp)
                    }
                    TextButton(onClick = { editing = d; showEditor = true }) { Text(tr("device.edit")) }
                    TextButton(onClick = {
                        scope.launch {
                            state.scaleService.deleteDevice(d.id)
                            refresh.value++
                        }
                    }) { Text(tr("device.delete")) }
                }
                Divider()
            }
        }
    }

    if (showEditor && editing != null) {
        DeviceEditorDialog(state, editing!!, groups, onDismiss = { showEditor = false }) {
            if (editing!!.id == 0L) state.scaleService.addDevice(it) else state.scaleService.updateDevice(it)
            refresh.value++
            showEditor = false
        }
    }
    if (showGroupEditor && groupEditing != null) {
        GroupEditorDialog(groupEditing!!, onDismiss = { showGroupEditor = false }) {
            if (groupEditing!!.id == 0L) state.scaleService.addGroup(it.name, it.note) else state.scaleService.updateGroup(it)
            refresh.value++
            showGroupEditor = false
        }
    }
    batchResults?.let {
        BatchResultDialog(state, it, onDismiss = { batchResults = null })
    }
}

@Composable
private fun GroupEditorDialog(initial: ScaleGroup, onDismiss: () -> Unit, onSave: (ScaleGroup) -> Unit) {
    var group by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) tr("group.add") else tr("group.edit")) },
        text = {
            Column(modifier = Modifier.width(340.dp)) {
                OutlinedTextField(
                    group.name, { group = group.copy(name = it) },
                    label = { Text(tr("group.name")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    group.note, { group = group.copy(note = it) },
                    label = { Text(tr("group.note")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(group) }) { Text(tr("common.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}

@Composable
private fun DeviceEditorDialog(
    state: AppState,
    initial: ScaleDevice,
    groups: List<ScaleGroup>,
    onDismiss: () -> Unit,
    onSave: (ScaleDevice) -> Unit,
) {
    var dev by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) tr("device.add") else tr("device.edit")) },
        text = {
            Column(modifier = Modifier.width(400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(dev.name, { dev = dev.copy(name = it) }, label = { Text(tr("device.field.name")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(dev.model, { dev = dev.copy(model = it) }, label = { Text(tr("device.field.model")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

                var brandExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(dev.brand.displayName, {}, readOnly = true, label = { Text(tr("device.field.brand")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { brandExpanded = true }) { Text("▾") } })
                DropdownMenu(brandExpanded, { brandExpanded = false }) {
                    ScaleBrand.values().forEach { b ->
                        DropdownMenuItem(onClick = {
                            // 切换品牌时端口联动为该品牌默认端口（各品牌端口对照表；可手改）
                            dev = dev.copy(brand = b, port = b.defaultPort); brandExpanded = false
                        }) { Text(b.displayName) }
                    }
                }

                var commExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(dev.commType.name, {}, readOnly = true, label = { Text(tr("device.field.comm")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { commExpanded = true }) { Text("▾") } })
                DropdownMenu(commExpanded, { commExpanded = false }) {
                    CommType.entries.forEach { c ->
                        DropdownMenuItem(onClick = { dev = dev.copy(commType = c); commExpanded = false }) { Text(c.name) }
                    }
                }

                if (dev.commType == CommType.SERIAL) {
                    OutlinedTextField(dev.serial.portName, { dev = dev.copy(serial = dev.serial.copy(portName = it)) }, label = { Text(tr("device.field.serialport")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    OutlinedTextField(dev.serial.baudRate.toString(), { s -> s.toIntOrNull()?.let { dev = dev.copy(serial = dev.serial.copy(baudRate = it)) } }, label = { Text(tr("device.field.baud")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    var parityExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(dev.serial.parity.toString(), {}, readOnly = true, label = { Text(tr("serial.parity")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        trailingIcon = { TextButton(onClick = { parityExpanded = true }) { Text("▾") } })
                    DropdownMenu(parityExpanded, { parityExpanded = false }) {
                        listOf(0, 1, 2).forEach { p ->
                            DropdownMenuItem(onClick = { dev = dev.copy(serial = dev.serial.copy(parity = p)); parityExpanded = false }) { Text("$p") }
                        }
                    }
                    OutlinedTextField(dev.serial.stopBits.toString(), { s -> s.toIntOrNull()?.let { dev = dev.copy(serial = dev.serial.copy(stopBits = it)) } }, label = { Text(tr("serial.stopBits")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                } else {
                    OutlinedTextField(dev.ip, { dev = dev.copy(ip = it) }, label = { Text(tr("device.field.ip")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    // 寺冈 TWS 协议的"端口"参数为秤号（LabelNet/秤端设置页均填秤号，通常为秤 IP 后 3 位）
                    val portLabel = if (dev.brand == ScaleBrand.DIGI) tr("device.field.scaleNo") else tr("device.field.port")
                    OutlinedTextField(dev.port.toString(), { s -> s.toIntOrNull()?.let { dev = dev.copy(port = it) } }, label = { Text(portLabel) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }

                var groupExpanded by remember { mutableStateOf(false) }
                val currentGroup = groups.firstOrNull { it.id == dev.groupId }
                OutlinedTextField(currentGroup?.name ?: tr("group.none"), {}, readOnly = true, label = { Text(tr("device.field.group")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { groupExpanded = true }) { Text("▾") } })
                DropdownMenu(groupExpanded, { groupExpanded = false }) {
                    DropdownMenuItem(onClick = { dev = dev.copy(groupId = 0); groupExpanded = false }) { Text(tr("group.none")) }
                    groups.forEach { g ->
                        DropdownMenuItem(onClick = { dev = dev.copy(groupId = g.id); groupExpanded = false }) { Text(g.name) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(dev) }) { Text(tr("common.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}
