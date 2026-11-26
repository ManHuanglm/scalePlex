package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus

/**
 * 底部状态栏：在线设备数、PLU 数量与版本号。
 * 颜色跟随主题，语言切换后文案随之刷新。
 */
@Composable
fun StatusBar(state: AppState) {
    val online = state.deviceRuntimes.values.count { it.status == DeviceStatus.ONLINE }
    val total = state.deviceRuntimes.size
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

    Surface(
        modifier = Modifier.fillMaxWidth().height(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (online > 0) DeviceStatus.ONLINE else DeviceStatus.OFFLINE, size = 7.dp)
            Spacer(Modifier.width(6.dp))
            Text("${tr("status.online")} $online / $total", fontSize = 11.sp, color = secondary)
            Spacer(Modifier.width(18.dp))
            Text("${tr("status.plu")} ${state.pluService.count()}", fontSize = 11.sp, color = secondary)
            Spacer(Modifier.weight(1f))
            Text("ScalePlex v0.1.0", fontSize = 11.sp, color = secondary)
        }
    }
}