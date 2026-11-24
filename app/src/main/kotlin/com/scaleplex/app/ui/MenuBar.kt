package com.scaleplex.app.ui

import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus

/**
 * 顶部导航：应用栏（标题 / 在线统计 / 语言切换）+ 标签页导航。
 * 标签文案随语言切换刷新（以 [refresh] 作为重算键）。
 */
@Composable
fun MenuBar(state: AppState, refresh: MutableState<Int>) {
    val tabKeys = listOf(
        "tab.devices", "tab.plu", "tab.label", "tab.trade", "tab.settings", "tab.about",
    )
    val tabLabels = remember(refresh.value) { tabKeys.map { tr(it) } }
    val online = state.deviceRuntimes.values.count { it.status == DeviceStatus.ONLINE }
    val total = state.deviceRuntimes.size

    TopAppBar(
        title = {
            Text(
                tr("app.title"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            Text(
                "${tr("status.online")} $online / $total",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
            )
            // 语言快捷切换
            TextButton(onClick = {
                val next = if (state.config.language.startsWith("zh")) "en-US" else "zh-CN"
                state.config = state.config.copy(language = next)
                state.configService.applyLocale(next)
                refresh.value++
            }) {
                Text(
                    if (state.config.language.startsWith("zh")) "English" else "中文",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = Color.White,
        elevation = 2.dp,
    )
    TabRow(
        selectedTabIndex = state.selectedTab,
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.primary,
    ) {
        tabKeys.forEachIndexed { index, _ ->
            Tab(
                selected = state.selectedTab == index,
                onClick = {
                    state.selectedTab = index
                    refresh.value++
                },
                text = {
                    Text(
                        tabLabels[index],
                        fontSize = 13.sp,
                        fontWeight = if (state.selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
}
