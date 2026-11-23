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
import com.scaleplex.core.util.Numbers
import kotlinx.coroutines.launch

/** Transaction log panel: read from scales, query, export. */
@Composable
fun TradePanel(state: AppState, refresh: MutableState<Int>) {
    var records by remember(refresh.value) {
        mutableStateOf(state.tradeLogService.query(page = 0, pageSize = 200))
    }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.trade"), Modifier.weight(1f), fontSize = 18.sp)
            Button(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev ->
                        val res = state.scaleService.driverFor(dev).readTradeLog()
                        res.getOrNull()?.let { state.tradeLogService.store(dev.id, it) }
                    }
                    records = state.tradeLogService.query(pageSize = 200)
                }
            }) { Text(tr("trade.read")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.tradeLogService.clearAll(); records = emptyList() }) { Text(tr("trade.clear")) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text(tr("trade.field.time"), Modifier.width(150.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("trade.field.plu"), Modifier.width(70.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("trade.field.name"), Modifier.width(150.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("trade.field.weight"), Modifier.width(80.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("trade.field.amount"), Modifier.width(90.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(records, key = { it.id }) { r ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(r.timestamp?.toString() ?: "-", Modifier.width(150.dp), fontSize = 11.sp)
                    Text(r.pluNo.toString(), Modifier.width(70.dp), fontSize = 11.sp)
                    Text(r.pluName, Modifier.width(150.dp), fontSize = 11.sp)
                    Text(Numbers.weightToDisplay(r.weight, state.config.weightDecimals), Modifier.width(80.dp), fontSize = 11.sp)
                    Text(Numbers.minorToDisplay(r.amount, state.config.priceDecimals), Modifier.width(90.dp), fontSize = 11.sp)
                }
            }
        }
    }
}
