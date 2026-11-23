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
import com.scaleplex.app.ui.label.LabelEditorDialog
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.LabelTemplate
import kotlinx.coroutines.launch

/** Label template list panel: list + open visual editor + delete + recall from scale. */
@Composable
fun LabelPanel(state: AppState, refresh: MutableState<Int>) {
    var templates by remember(refresh.value) { mutableStateOf(state.labelService.list()) }
    var editing by remember { mutableStateOf<LabelTemplate?>(null) }
    var recallMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.label"), Modifier.weight(1f), fontSize = 18.sp)
            // 回收（参考 LabelNet：接收秤上的标签格式）
            Button(onClick = {
                scope.launch {
                    var saved = 0
                    val errors = mutableListOf<String>()
                    state.scaleService.devices().filter { it.enabled }.forEach { dev ->
                        state.scaleService.driverFor(dev).readLabelFormats().fold(
                            onSuccess = { list ->
                                list.forEach { state.labelService.save(it) }
                                saved += list.size
                            },
                            onFailure = { errors += "${dev.name}: ${it.message}" },
                        )
                    }
                    recallMessage = tr("label.recallDone") + saved +
                        if (errors.isEmpty()) "" else " · " + errors.joinToString("; ").take(120)
                    refresh.value++
                }
            }) { Text(tr("label.recall")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                state.labelService.save(
                    LabelTemplate(name = "New Label ${templates.size + 1}", widthMm = 40, heightMm = 30),
                )
                refresh.value++
            }) { Text(tr("label.new")) }
        }
        if (recallMessage.isNotBlank()) {
            Text(recallMessage, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFB00020))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(templates, key = { it.id }) { t ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 14.sp)
                        Text("${t.widthMm}×${t.heightMm} mm · ${t.elements.size} elements", fontSize = 11.sp)
                    }
                    TextButton(onClick = { editing = t }) { Text(tr("label.edit")) }
                    TextButton(onClick = { state.labelService.delete(t.id); refresh.value++ }) { Text(tr("device.delete")) }
                }
                Divider()
            }
        }
    }

    editing?.let { current ->
        LabelEditorDialog(
            state = state,
            initial = current,
            onDismiss = { editing = null },
            onSaved = { refresh.value++ },
        )
    }
}
