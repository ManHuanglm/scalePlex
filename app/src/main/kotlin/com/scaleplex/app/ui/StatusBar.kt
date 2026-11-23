package com.scaleplex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState

/**
 * Bottom status bar. Rendered as the last child of the root Column.
 */
@Composable
fun StatusBar(state: AppState) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        color = Color(0xFF2B2B2B),
    ) {
        val online = state.deviceRuntimes.values.count { it.status == com.scaleplex.core.model.DeviceStatus.ONLINE }
        val total = state.deviceRuntimes.size
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "在线 $online / $total    PLU ${state.pluService.count()}    ScalePlex v0.1.0",
                color = Color(0xFFCCCCCC),
                fontSize = 11.sp,
            )
        }
    }
}
