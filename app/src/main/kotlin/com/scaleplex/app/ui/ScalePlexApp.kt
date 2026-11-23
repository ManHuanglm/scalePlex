package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr

/**
 * Root layout: menu bar on top, left device tree, right tabbed content, bottom status bar.
 */
@Composable
fun ScalePlexApp(state: AppState) {
    val refresh = remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        MenuBar(state, refresh)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            DeviceTree(
                state = state,
                modifier = Modifier.width(260.dp),
                refresh = refresh,
            )
            ContentTabs(state, refresh)
        }
        StatusBar(state)
    }
}
