package com.scaleplex.app.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DeviceStatus

/**
 * 顶部应用栏：标题 / 在线统计 / 语言切换（页面导航由 RibbonBar 大图标工具栏承担）。
 */
@Composable
fun MenuBar(state: AppState, refresh: MutableState<Int>) {
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
}
