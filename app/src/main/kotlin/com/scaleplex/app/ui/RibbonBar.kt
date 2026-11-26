package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr

/**
 * 顶部大图标工具栏（Scale Manager 风格）：图标在上、文字在下的功能入口一排，
 * 替代传统 TabRow 页签导航；窗口过窄时横向滚动。
 */
@Composable
fun RibbonBar(state: AppState, refresh: MutableState<Int>) {
    val items: List<Pair<String, ImageVector>> = listOf(
        "tab.devices" to Icons.Default.Devices,
        "tab.plu" to Icons.Default.ShoppingCart,
        "tab.label" to Icons.Default.Label,
        "tab.trade" to Icons.Default.ReceiptLong,
        "tab.settings" to Icons.Default.Settings,
        "tab.about" to Icons.Default.Info,
    )

    Surface(color = MaterialTheme.colors.surface, elevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEachIndexed { index, (key, icon) ->
                    val selected = state.selectedTab == index
                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colors.primary.copy(alpha = 0.14f) else Color.Transparent,
                            )
                            .clickable {
                                state.selectedTab = index
                                refresh.value++
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            icon,
                            contentDescription = tr(key),
                            tint = if (selected) MaterialTheme.colors.primary
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tr(key),
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (selected) MaterialTheme.colors.primary
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
        }
    }
}
