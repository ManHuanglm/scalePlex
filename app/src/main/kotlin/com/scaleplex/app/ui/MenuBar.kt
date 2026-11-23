package com.scaleplex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.I18n
import com.scaleplex.core.i18n.tr

/**
 * Top application bar acting as the menu/toolbar. Each entry is a clickable label that
 * switches the active tab. The language toggle re-renders the whole tree via [refresh].
 */
@Composable
fun MenuBar(state: AppState, refresh: MutableState<Int>) {
    val tabs = listOf(
        "tab.devices", "tab.plu", "tab.label", "tab.trade", "tab.settings", "tab.about",
    )
    TopAppBar(
        title = { Text(tr("app.title")) },
        actions = {
            androidx.compose.foundation.layout.Row {
                tabs.forEachIndexed { i, key ->
                    androidx.compose.material.TextButton(onClick = {
                        state.selectedTab = i
                        refresh.value++
                    }) {
                        Text(
                            tr(key),
                            color = if (state.selectedTab == i)
                                androidx.compose.ui.graphics.Color.White
                            else androidx.compose.ui.graphics.Color(0xFFDDDDDD),
                        )
                    }
                }
                // Language quick-toggle
                androidx.compose.material.TextButton(onClick = {
                    val next = if (state.config.language.startsWith("zh")) "en-US" else "zh-CN"
                    state.config = state.config.copy(language = next)
                    state.configService.applyLocale(next)
                    refresh.value++
                }) {
                    Text(if (state.config.language.startsWith("zh")) "EN" else "中",
                        color = androidx.compose.ui.graphics.Color.White)
                }
            }
        },
        backgroundColor = androidx.compose.ui.graphics.Color(0xFF1E6FD9),
    )
}
