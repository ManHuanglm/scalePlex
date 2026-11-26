package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus
import kotlinx.coroutines.launch

/**
 * 左侧电子秤列表（Scale Manager 风格）：标题行（新建/删除快捷入口）+ 扁平秤列表
 * （在线状态图标 · 秤号 · IP 地址），底部显示在线数与总数。
 * 点击行选中；「新建」跳转设备管理页；「删除」直接删除选中秤。
 */
@Composable
fun ScaleListPanel(state: AppState, modifier: Modifier = Modifier, refresh: MutableState<Int>) {
    val devices = remember(refresh.value) { state.scaleService.devices() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state.startHeartbeatPolling() }

    val online = state.deviceRuntimes.values.count { it.status == DeviceStatus.ONLINE }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- 标题行：电子秤列表 + 新建 / 删除 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("sidebar.scaleList"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    state.selectedTab = 0
                    refresh.value++
                }) { Text(tr("device.add"), fontSize = 11.sp) }
                TextButton(
                    enabled = selectedId != null,
                    onClick = {
                        val id = selectedId ?: return@TextButton
                        scope.launch {
                            state.scaleService.deleteDevice(id)
                            selectedId = null
                            refresh.value++
                        }
                    },
                ) {
                    Text(tr("device.delete"), fontSize = 11.sp, color = UiColors.Error)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // ---- 秤列表（状态图标 · 秤号 · IP） ----
            val listState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(devices, key = { it.id }) { d ->
                        val status = state.deviceRuntimes[d.id]?.status ?: DeviceStatus.UNKNOWN
                        val selected = selectedId == d.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable { selectedId = d.id }
                                .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (status == DeviceStatus.ONLINE) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = statusLabel(status),
                                tint = if (status == DeviceStatus.ONLINE) UiColors.Online else UiColors.Offline,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                scaleNoOf(d),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(44.dp),
                                maxLines = 1,
                            )
                            Text(
                                d.ip,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    }
                }
                if (devices.isEmpty()) {
                    Text(
                        tr("device.emptyHint"),
                        modifier = Modifier.align(Alignment.Center).padding(12.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            // ---- 底部统计：在线数 / 总数 ----
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${tr("sidebar.onlineCount")}${tr("common.count")}：$online",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "${tr("sidebar.totalCount")}${tr("common.count")}：${devices.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}