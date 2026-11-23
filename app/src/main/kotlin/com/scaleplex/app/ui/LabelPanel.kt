package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.LabelTemplate

/** Label template list panel. Full visual editor is a phase-1 stretch; this manages templates. */
@Composable
fun LabelPanel(state: AppState, refresh: MutableState<Int>) {
    var templates by remember(refresh.value) { mutableStateOf(state.labelService.list()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.label"), Modifier.weight(1f), fontSize = 18.sp)
            Button(onClick = {
                state.labelService.save(
                    LabelTemplate(name = "New Label ${templates.size + 1}", widthMm = 40, heightMm = 30),
                )
                refresh.value++
            }) { Text(tr("label.new")) }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(templates, key = { it.id }) { t ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 14.sp)
                        Text("${t.widthMm}×${t.heightMm} mm · ${t.elements.size} elements", fontSize = 11.sp)
                    }
                    TextButton(onClick = { state.labelService.delete(t.id); refresh.value++ }) { Text(tr("device.delete")) }
                }
                Divider()
            }
        }
    }
}
