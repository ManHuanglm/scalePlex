package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.table.ScrollableTable
import com.scaleplex.app.ui.table.TableHeader
import com.scaleplex.app.ui.table.TableRow
import com.scaleplex.app.ui.table.cellPadding
import com.scaleplex.app.ui.table.rememberRowHover
import com.scaleplex.app.ui.table.rememberTableState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.DeviceStatus
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.ScaleGroup
import kotlinx.coroutines.launch

/**
 * 设备管理面板：分组筛选与分组管理、设备增删改、按分组一键下发、批量下发与时间同步。
 * 设备列表使用与 PLU 一致的可滚动表格（状态点 / 名称 / 品牌 / 分组 / 通信地址 / 操作）。
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
    var batchResults by remember { mutableStateOf<Map<Long, BatchResult>?>(null) }
    val checked = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    val online = devices.count { state.deviceRuntimes[it.id]?.status == DeviceStatus.ONLINE }
    val table = rememberTableState(
        listOf(36.dp, 90.dp, 150.dp, 110.dp, 110.dp, 220.dp, 140.dp),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(
            title = tr("tab.devices"),
            subtitle = "${tr("common.total")} ${devices.size}${tr("common.count")} · " +
                "${tr("status.online")} $online · ${tr("plu.checkedCount")} ${checked.size}",
        ) {
            OutlinedButton(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev -> state.scaleService.syncTime(dev) }
                }
            }) { Text(tr("device.syncTime")) }
            Button(onClick = {
                editing = ScaleDevice(name = "", brand = ScaleBrand.DIGI, groupId = groupFilter ?: 0)
                showEditor = true
            }) { Text(tr("device.add")) }
            Button(onClick = {
                scope.launch {
                    // 批量下发（参考银豹：勾选多台秤批量下发商品资料）
                    val targets = devices.filter { checked.contains(it.id) }.ifEmpty { devices }
                    batchResults = state.scaleService.downloadToDevices(
                        targets, state.pluService.all(),
                    )
                }
            }) { Text(tr("device.downloadSelected")) }
        }

        // ---- 分组筛选与分组管理 ----
        ToolbarRow {
            var filterExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { filterExpanded = true }) {
                    Text(
                        tr("device.group") + "：" +
                            (groups.firstOrNull { it.id == groupFilter }?.name ?: tr("group.all")),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 10.sp)
                }
                DropdownMenu(filterExpanded, { filterExpanded = false }) {
                    DropdownMenuItem(onClick = { groupFilter = null; filterExpanded = false }) {
                        Text(tr("group.all"))
                    }
                    groups.forEach { g ->
                        DropdownMenuItem(onClick = { groupFilter = g.id; filterExpanded = false }) {
                            Text(g.name)
                        }
                    }
                }
            }
            OutlinedButton(onClick = {
                groupEditing = ScaleGroup(name = "")
                showGroupEditor = true
            }) { Text(tr("group.add")) }
            groupFilter?.let { gid ->
                groups.firstOrNull { it.id == gid }?.let { g ->
                    OutlinedButton(onClick = { groupEditing = g; showGroupEditor = true }) {
                        Text(tr("group.edit"))
                    }
                    OutlinedButton(onClick = {
                        state.scaleService.deleteGroup(g.id)
                        groupFilter = null
                        refresh.value++
                    }) { Text(tr("group.delete")) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            batchResults = state.scaleService.downloadToGroup(gid, state.pluService.all())
                        }
                    }) { Text(tr("group.download")) }
                }
            }
        }

        Text(
            tr("device.batchHint"),
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ---- 设备表格 ----
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ScrollableTable(
                table = table,
                modifier = Modifier.fillMaxSize(),
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
                                deviceHeaders()[index - 1],
                                Modifier.cellPadding(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
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
                    val groupName = groups.firstOrNull { it.id == d.groupId }?.name ?: tr("group.none")
                    DeviceTableRow(
                        table = table,
                        d = d,
                        status = status,
                        groupName = groupName,
                        checked = checked.contains(d.id),
                        onCheck = { c -> if (c) checked.add(d.id) else checked.remove(d.id) },
                        onEdit = { editing = d; showEditor = true },
                        onDelete = {
                            scope.launch {
                                state.scaleService.deleteDevice(d.id)
                                refresh.value++
                            }
                        },
                    )
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                }
            }
            if (devices.isEmpty()) {
                Text(
                    tr("device.emptyHint"),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
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
            if (groupEditing!!.id == 0L) {
                state.scaleService.addGroup(it.name, it.note)
            } else {
                state.scaleService.updateGroup(it)
            }
            refresh.value++
            showGroupEditor = false
        }
    }
    batchResults?.let {
        BatchResultDialog(state, it, onDismiss = { batchResults = null })
    }
}

/** 设备表格列标题。 */
private fun deviceHeaders(): List<String> = listOf(
    tr("device.field.status"),
    tr("device.field.name"),
    tr("device.field.brand"),
    tr("device.field.group"),
    tr("device.field.address"),
    tr("common.actions"),
)

@Composable
private fun DeviceTableRow(
    table: com.scaleplex.app.ui.table.TableState,
    d: ScaleDevice,
    status: DeviceStatus,
    groupName: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (hoverSource, hovered) = rememberRowHover()
    TableRow(
        table,
        modifier = Modifier
            .background(
                if (hovered) MaterialTheme.colors.primary.copy(alpha = 0.06f)
                else Color.Transparent,
            )
            .hoverable(hoverSource)
            .clickable(onClick = onEdit),
    ) { index, _ ->
        when (index) {
            0 -> Checkbox(checked = checked, onCheckedChange = onCheck, modifier = Modifier.cellPadding())
            1 -> Row(Modifier.cellPadding(), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status)
                Spacer(Modifier.width(6.dp))
                Text(statusLabel(status), fontSize = 12.sp, maxLines = 1)
            }
            2 -> Text(d.name, Modifier.cellPadding(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            3 -> Text(d.brand.displayName, Modifier.cellPadding(), fontSize = 12.sp, maxLines = 1)
            4 -> Text(groupName, Modifier.cellPadding(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            5 -> Text(deviceAddress(d), Modifier.cellPadding(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else -> Row(Modifier.cellPadding(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text(tr("device.edit"), fontSize = 12.sp) }
                TextButton(onClick = onDelete) {
                    Text(tr("device.delete"), fontSize = 12.sp, color = UiColors.Error)
                }
            }
        }
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
                    label = { Text(tr("group.name")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    group.note, { group = group.copy(note = it) },
                    label = { Text(tr("group.note")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                OutlinedTextField(
                    dev.name, { dev = dev.copy(name = it) },
                    label = { Text(tr("device.field.name")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )

                var brandExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(
                    dev.brand.displayName, {}, readOnly = true,
                    label = { Text(tr("device.field.brand")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { brandExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(brandExpanded, { brandExpanded = false }) {
                    ScaleBrand.values().forEach { b ->
                        DropdownMenuItem(onClick = {
                            // 切换品牌时端口联动为该品牌默认端口（各品牌端口对照表；可手改）
                            dev = dev.copy(brand = b, port = b.defaultPort)
                            brandExpanded = false
                        }) { Text(b.displayName) }
                    }
                }

                // 型号：有预设机型的品牌（如顶尖）提供下拉选择，仍可手动输入
                val modelOptions = dev.brand.models
                if (modelOptions.isEmpty()) {
                    OutlinedTextField(
                        dev.model, { dev = dev.copy(model = it) },
                        label = { Text(tr("device.field.model")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                } else {
                    var modelExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            dev.model, { dev = dev.copy(model = it) },
                            label = { Text(tr("device.field.model")) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            trailingIcon = {
                                TextButton(onClick = { modelExpanded = true }) { Text("▾") }
                            },
                        )
                        DropdownMenu(modelExpanded, { modelExpanded = false }) {
                            modelOptions.forEach { model ->
                                DropdownMenuItem(onClick = {
                                    dev = dev.copy(model = model)
                                    modelExpanded = false
                                }) { Text(model) }
                            }
                        }
                    }
                }

                var commExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(
                    dev.commType.name, {}, readOnly = true,
                    label = { Text(tr("device.field.comm")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { commExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(commExpanded, { commExpanded = false }) {
                    CommType.entries.forEach { c ->
                        DropdownMenuItem(onClick = { dev = dev.copy(commType = c); commExpanded = false }) {
                            Text(c.name)
                        }
                    }
                }

                if (dev.commType == CommType.SERIAL) {
                    OutlinedTextField(
                        dev.serial.portName, { dev = dev.copy(serial = dev.serial.copy(portName = it)) },
                        label = { Text(tr("device.field.serialport")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    OutlinedTextField(
                        dev.serial.baudRate.toString(),
                        { s -> s.toIntOrNull()?.let { dev = dev.copy(serial = dev.serial.copy(baudRate = it)) } },
                        label = { Text(tr("device.field.baud")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    var parityExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        dev.serial.parity.toString(), {}, readOnly = true,
                        label = { Text(tr("serial.parity")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        trailingIcon = { TextButton(onClick = { parityExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(parityExpanded, { parityExpanded = false }) {
                        listOf(0, 1, 2).forEach { p ->
                            DropdownMenuItem(onClick = {
                                dev = dev.copy(serial = dev.serial.copy(parity = p))
                                parityExpanded = false
                            }) { Text("$p") }
                        }
                    }
                    OutlinedTextField(
                        dev.serial.stopBits.toString(),
                        { s -> s.toIntOrNull()?.let { dev = dev.copy(serial = dev.serial.copy(stopBits = it)) } },
                        label = { Text(tr("serial.stopBits")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                } else {
                    OutlinedTextField(
                        dev.ip, { dev = dev.copy(ip = it) },
                        label = { Text(tr("device.field.ip")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    // 寺冈 TWS 协议的"端口"参数为秤号（LabelNet/秤端设置页均填秤号，通常为秤 IP 后 3 位）
                    val portLabel = if (dev.brand == ScaleBrand.DIGI) tr("device.field.scaleNo") else tr("device.field.port")
                    OutlinedTextField(
                        dev.port.toString(),
                        { s -> s.toIntOrNull()?.let { dev = dev.copy(port = it) } },
                        label = { Text(portLabel) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }

                var groupExpanded by remember { mutableStateOf(false) }
                val currentGroup = groups.firstOrNull { it.id == dev.groupId }
                OutlinedTextField(
                    currentGroup?.name ?: tr("group.none"), {}, readOnly = true,
                    label = { Text(tr("device.field.group")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { groupExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(groupExpanded, { groupExpanded = false }) {
                    DropdownMenuItem(onClick = { dev = dev.copy(groupId = 0); groupExpanded = false }) {
                        Text(tr("group.none"))
                    }
                    groups.forEach { g ->
                        DropdownMenuItem(onClick = { dev = dev.copy(groupId = g.id); groupExpanded = false }) {
                            Text(g.name)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(dev) }) { Text(tr("common.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}
