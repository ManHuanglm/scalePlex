package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaleplex.app.AppState

/**
 * Routes the active left-tab to its panel. Kept as a thin switch so each panel
 * is independently testable and the layout stays clear.
 */
@Composable
fun ContentTabs(state: AppState, refresh: MutableState<Int>) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopStart) {
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
