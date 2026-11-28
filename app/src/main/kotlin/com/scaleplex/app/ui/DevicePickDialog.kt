package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus
import com.scaleplex.core.model.ScaleDevice

/**
 * 通用选秤弹窗：单选目标秤（全部启用设备或某一台，默认第一台在线秤），
 * 确认后回调所选目标列表，任务执行与结果展示由调用方负责。
 * 「下发选中/回收」（PluPanel）与「读取流水」（TradePanel）、「下发模板」（LabelEditorDialog）共用。
 */
@Composable
internal fun DevicePickDialog(
    state: AppState,
    title: String,
    startLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (List<ScaleDevice>) -> Unit,
) {
    val devices = remember { state.scaleService.devices().filter { it.enabled } }
    // -1 = 全部设备；否则为选中的设备 id（默认第一台在线秤）
    var selected by remember {
        mutableStateOf(
            devices.firstOrNull { state.deviceRuntimes[it.id]?.status == DeviceStatus.ONLINE }?.id
                ?: devices.firstOrNull()?.id,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.width(420.dp)) {
                if (devices.isEmpty()) {
                    Text(
                        tr("pick.empty"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    DevicePickRow(
                        label = tr("pick.allDevices"),
                        detail = "${devices.size}${tr("common.count")}",
                        selected = selected == -1L,
                        enabled = true,
                        onSelect = { selected = -1L },
                    )
                    devices.forEach { dev ->
                        DevicePickRow(
                            label = dev.name.ifBlank { dev.model },
                            detail = "${dev.brand.displayName} · ${deviceAddress(dev)}",
                            selected = selected == dev.id,
                            enabled = true,
                            onSelect = { selected = dev.id },
                            status = state.deviceRuntimes[dev.id]?.status ?: DeviceStatus.UNKNOWN,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targets = if (selected == -1L) devices else devices.filter { it.id == selected }
                    onConfirm(targets)
                },
                enabled = devices.isNotEmpty(),
            ) { Text(startLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.close")) }
        },
    )
}

/** 选秤弹窗的可选行：单选圆点 + 名称 + 说明 + 可选在线状态点。 */
@Composable
internal fun DevicePickRow(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    status: DeviceStatus? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        if (status != null) {
            StatusDot(status)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(
            detail,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
