package com.scaleplex.app.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.scaleplex.core.model.ScaleDevice

/**
 * 左侧设备树：按分组展示秤与实时在线状态。
 * 分组可折叠；未分组设备归入「未分组」；底部提供状态图例。
 */
@Composable
fun DeviceTree(state: AppState, modifier: Modifier = Modifier, refresh: MutableState<Int>) {
    val groups by remember(refresh.value) { mutableStateOf(state.scaleService.groups()) }
    val allDevices by remember(refresh.value) { mutableStateOf(state.scaleService.devices()) }
    val collapsed = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(Unit) { state.startHeartbeatPolling() }

    val online = state.deviceRuntimes.values.count { it.status == DeviceStatus.ONLINE }
    val total = allDevices.size

    Surface(modifier = modifier, color = MaterialTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- 标题栏：设备数统计 + 手动刷新（立即协议级探测一轮） ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("menu.device"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${tr("status.online")} $online / $total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
                IconButton(
                    onClick = {
                        state.refreshDeviceStatus()
                        refresh.value++
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = tr("common.refresh"),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

            if (allDevices.isEmpty()) {
                EmptyDeviceHint(onGoDevices = {
                    state.selectedTab = 0
                    refresh.value++
                })
            }

            val listState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    groups.forEach { group ->
                        item(key = "g${group.id}") {
                            val expanded = collapsed[group.id] != true
                            val devices = allDevices.filter { it.groupId == group.id }
                            GroupHeader(
                                name = group.name,
                                count = devices.size,
                                expanded = expanded,
                                onToggle = { collapsed[group.id] = expanded },
                            )
                            if (expanded) {
                                devices.forEach { dev ->
                                    DeviceRow(dev, state.deviceRuntimes[dev.id]?.status ?: DeviceStatus.UNKNOWN)
                                }
                            }
                        }
                    }
                    // 未分组设备
                    val ungrouped = allDevices.filter { it.groupId == 0L }
                    if (ungrouped.isNotEmpty()) {
                        item(key = "g-ungrouped") {
                            val expanded = collapsed[0L] != true
                            GroupHeader(
                                name = tr("group.none"),
                                count = ungrouped.size,
                                expanded = expanded,
                                onToggle = { collapsed[0L] = expanded },
                            )
                            if (expanded) {
                                ungrouped.forEach { dev ->
                                    DeviceRow(dev, state.deviceRuntimes[dev.id]?.status ?: DeviceStatus.UNKNOWN)
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            // ---- 状态图例 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendItem(DeviceStatus.ONLINE)
                LegendItem(DeviceStatus.OFFLINE)
                LegendItem(DeviceStatus.UNKNOWN)
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) "▾" else "▸", fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.width(5.dp))
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun DeviceRow(device: ScaleDevice, status: DeviceStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface,
            )
            Text(
                "${device.brand.displayName} · ${deviceAddress(device)}",
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun LegendItem(status: DeviceStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status, size = 7.dp)
        Spacer(Modifier.width(4.dp))
        Text(statusLabel(status), fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
    }
}

/** 无设备时的空状态：引导到「设备管理」新增。 */
@Composable
private fun EmptyDeviceHint(onGoDevices: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚖", fontSize = 18.sp, color = MaterialTheme.colors.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("device.emptyHint"),
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGoDevices) { Text(tr("device.goAdd"), fontSize = 12.sp) }
        }
    }
}
