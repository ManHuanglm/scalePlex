package com.scaleplex.app.ui

import androidx.compose.foundation.clickable
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
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.util.Numbers
import kotlinx.coroutines.launch

/**
 * PLU management panel: search box, paged list, add/edit dialog, and download actions.
 */
@Composable
fun PluPanel(state: AppState, refresh: MutableState<Int>) {
    var keyword by remember { mutableStateOf("") }
    var plus by remember(keyword, refresh.value) {
        mutableStateOf(state.pluService.list(keyword, page = 0, pageSize = 200))
    }
    var editing by remember { mutableStateOf<PluItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // toolbar
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                label = { Text(tr("plu.search")) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editing = PluItem(pluNo = (state.pluService.count() + 1)); showEditor = true }) {
                Text(tr("plu.add"))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    val devices = state.scaleService.devices()
                    state.scaleService.downloadToDevices(devices, plus)
                }
            }) { Text(tr("plu.download.group")) }
        }

        // header
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text(tr("plu.field.no"), Modifier.width(70.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.name"), Modifier.width(160.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.price"), Modifier.width(90.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.barcode"), Modifier.width(150.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(plus, key = { it.pluNo }) { p ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editing = p; showEditor = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.pluNo.toString(), Modifier.width(70.dp), fontSize = 12.sp)
                    Text(p.displayName, Modifier.width(160.dp), fontSize = 12.sp)
                    Text(Numbers.minorToDisplay(p.unitPrice, state.config.priceDecimals), Modifier.width(90.dp), fontSize = 12.sp)
                    Text(p.eanCode, Modifier.width(150.dp), fontSize = 12.sp)
                }
                Divider()
            }
        }
    }

    if (showEditor && editing != null) {
        PluEditorDialog(state, editing!!, onDismiss = { showEditor = false }) {
            state.pluService.save(it)
            refresh.value++
            showEditor = false
        }
    }
}
