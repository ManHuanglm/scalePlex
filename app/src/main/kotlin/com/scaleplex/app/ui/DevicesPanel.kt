package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.launch

/**
 * Device management panel: add/edit/delete scales, import/export inventory, per-device actions.
 */
@Composable
fun DevicesPanel(state: AppState, refresh: MutableState<Int>) {
    var devices by remember(refresh.value) { mutableStateOf(state.scaleService.devices()) }
    var editing by remember { mutableStateOf<ScaleDevice?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.devices"), Modifier.weight(1f), fontSize = 18.sp)
            Button(onClick = { editing = ScaleDevice(name = "", brand = ScaleBrand.DIGI); showEditor = true }) { Text(tr("device.add")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev -> state.scaleService.syncTime(dev) }
                }
            }) { Text(tr("device.syncTime")) }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices, key = { it.id }) { d ->
                val rt = state.deviceRuntimes[d.id]
                val status = rt?.status?.name ?: "UNKNOWN"
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.name} · ${d.brand.displayName}", fontSize = 14.sp)
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
        DeviceEditorDialog(state, editing!!, onDismiss = { showEditor = false }) {
            if (editing!!.id == 0L) state.scaleService.addDevice(it) else state.scaleService.updateDevice(it)
            refresh.value++
            showEditor = false
        }
    }
}

@Composable
private fun DeviceEditorDialog(state: AppState, initial: ScaleDevice, onDismiss: () -> Unit, onSave: (ScaleDevice) -> Unit) {
    var dev by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("device.add") + " / " + tr("device.edit")) },
        text = {
            Column(modifier = Modifier.width(380.dp)) {
                OutlinedTextField(dev.name, { dev = dev.copy(name = it) }, label = { Text(tr("device.field.name")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(dev.model, { dev = dev.copy(model = it) }, label = { Text(tr("device.field.model")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                var brandExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(dev.brand.displayName, {}, readOnly = true, label = { Text(tr("device.field.brand")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    trailingIcon = { TextButton(onClick = { brandExpanded = true }) { Text("▾") } })
                DropdownMenu(brandExpanded, { brandExpanded = false }) {
                    ScaleBrand.values().forEach { b ->
                        DropdownMenuItem(onClick = { dev = dev.copy(brand = b); brandExpanded = false }) { Text(b.displayName) }
                    }
                }
                OutlinedTextField(dev.ip, { dev = dev.copy(ip = it) }, label = { Text(tr("device.field.ip")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(dev.port.toString(), { s -> s.toIntOrNull()?.let { dev = dev.copy(port = it) } }, label = { Text(tr("device.field.port")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(dev) }) { Text(tr("common.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}
