package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus
import kotlinx.coroutines.launch

/**
 * Left device tree: groups with their scales, each device showing a live online/offline dot.
 * Clicking a device selects it for single-scale operations (download PLU, sync time, etc.).
 */
@Composable
fun DeviceTree(state: AppState, modifier: Modifier = Modifier, refresh: MutableState<Int>) {
    var groups by remember(refresh.value) { mutableStateOf(state.scaleService.groups()) }
    var selectedDeviceId by remember { mutableStateOf<Long?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(refresh.value) {
        groups = state.scaleService.groups()
        // Kick off a status refresh for every device.
        state.scaleService.devices().forEach { dev ->
            scope.launch {
                val rt = state.scaleService.connect(dev)
                state.deviceRuntimes[dev.id] = rt
                refresh.value++
            }
        }
    }

    Column(modifier = modifier.background(Color(0xFF26282C))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设备 / Devices", color = Color.White, fontSize = 14.sp)
            Button(onClick = { refresh.value++ }) { Text(tr("device.status.online")) }
        }
        LazyColumn {
            groups.forEach { group ->
                item(key = "g${group.id}") {
                    Text(
                        "📁 ${group.name}",
                        color = Color(0xFF9BD1FF), fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                val devices = state.scaleService.devicesInGroup(group.id)
                items(devices, key = { "d${it.id}" }) { dev ->
                    val rt = state.deviceRuntimes[dev.id]
                    val status = rt?.status ?: DeviceStatus.UNKNOWN
                    val dot = when (status) {
                        DeviceStatus.ONLINE -> Color(0xFF3DDC84)
                        DeviceStatus.CONNECTING -> Color(0xFFFFC107)
                        else -> Color(0xFFE53935)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedDeviceId = dev.id }
                            .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(10.dp).background(dot, CircleShape)
                        )
                        Text(
                            "  ${dev.name}  (${dev.brand.displayName})",
                            color = if (selectedDeviceId == dev.id) Color.White else Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
