package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaleplex.app.AppState

/**
 * 根布局：顶部应用栏 + 标签导航，左侧设备状态侧边栏，右侧内容区，底部状态栏。
 * 窗口变窄时由各面板内部滚动条保证内容可达。
 */
@Composable
fun ScalePlexApp(state: AppState) {
    val refresh = remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        MenuBar(state, refresh)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            DeviceTree(
                state = state,
                modifier = Modifier.width(UiDimens.SidebarWidth).fillMaxHeight(),
                refresh = refresh,
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
            )
            ContentTabs(state, refresh, Modifier.weight(1f))
        }
        StatusBar(state)
    }
}
