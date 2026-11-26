package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.DeviceStatus
import com.scaleplex.core.model.ScaleDevice

/**
 * 内容区路由：按当前选中的标签页渲染对应面板。
 * 外层统一提供面板留白与背景，各面板只负责自身内容布局。
 */
@Composable
fun ContentTabs(state: AppState, refresh: MutableState<Int>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UiDimens.PanelPadding, vertical = 10.dp),
        ) {
            when (state.selectedTab) {
                0 -> DevicesPanel(state, refresh)
                1 -> PluPanel(state, refresh)
                2 -> LabelPanel(state, refresh)
                3 -> TradePanel(state, refresh)
                4 -> SettingsPanel(state, refresh)
                else -> AboutPanel()
            }
        }
    }
}

/**
 * 面板标题栏：左侧标题 + 可选副标题（统计信息），右侧操作按钮区。
 * 统一各业务页面的头部视觉层级。
 */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiDimens.ControlGap),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * 自适应工具条：控件在窗口变窄时自动换行，避免横向被裁切；
 * 换行后的控件仍保持统一间距。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolbarRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(UiDimens.ControlGap),
        verticalArrangement = Arrangement.spacedBy(UiDimens.ControlGap),
    ) {
        content()
    }
}

/** 设备在线状态圆点（侧边栏与设备列表共用）。 */
@Composable
fun StatusDot(status: DeviceStatus, size: Dp = 9.dp) {
    val color = when (status) {
        DeviceStatus.ONLINE -> UiColors.Online
        DeviceStatus.CONNECTING -> UiColors.Connecting
        DeviceStatus.OFFLINE -> UiColors.Offline
        DeviceStatus.UNKNOWN -> UiColors.Unknown
    }
    Box(modifier = Modifier.size(size).background(color, CircleShape))
}

/** 设备状态文案。 */
fun statusLabel(status: DeviceStatus): String = when (status) {
    DeviceStatus.ONLINE -> tr("device.status.online")
    DeviceStatus.OFFLINE -> tr("device.status.offline")
    DeviceStatus.CONNECTING -> tr("device.status.connecting")
    DeviceStatus.UNKNOWN -> tr("device.status.unknown")
}

/** 设备通信地址显示（串口 / 网络）。 */
fun deviceAddress(device: ScaleDevice): String = if (device.commType == CommType.SERIAL) {
    "${device.commType} ${device.serial.portName}@${device.serial.baudRate}"
} else {
    "${device.commType} ${device.ip}:${device.port}"
}

/** 秤号显示：网络秤取 IP 尾段（与 ScaleKey 秤号一致），串口秤取串口名。 */
fun scaleNoOf(device: ScaleDevice): String =
    if (device.commType == CommType.SERIAL) device.serial.portName
    else device.ip.substringAfterLast('.').ifBlank { "-" }