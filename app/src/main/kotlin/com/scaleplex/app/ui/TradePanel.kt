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
import com.scaleplex.service.io.ImportExportService
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

/**
 * 交易流水面板：按设备 / 起止日期 / PLU 号筛选查询、读取秤内流水、清空、导出 Excel。
 */
@Composable
fun TradePanel(state: AppState, refresh: MutableState<Int>) {
    var filterDevice by remember { mutableStateOf<Long?>(null) }
    var filterFrom by remember { mutableStateOf("") }
    var filterTo by remember { mutableStateOf("") }
    var filterPlu by remember { mutableStateOf("") }

    var records by remember(refresh.value, filterDevice, filterFrom, filterTo, filterPlu) {
        mutableStateOf(
            state.tradeLogService.query(
                deviceId = filterDevice,
                from = filterFrom.ifBlank { null },
                to = filterTo.ifBlank { null },
                pluNo = filterPlu.toIntOrNull(),
                page = 0,
                pageSize = 500,
            )
        )
    }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 工具栏：读取 / 清空 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("tab.trade"), Modifier.weight(1f), fontSize = 18.sp)
            Button(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev ->
                        val res = state.scaleService.driverFor(dev).readTradeLog()
                        res.getOrNull()?.let { state.tradeLogService.store(dev.id, it) }
                    }
                    records = state.tradeLogService.query(pageSize = 500)
                }
            }) { Text(tr("trade.read")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.tradeLogService.clearAll(); records = emptyList() }) { Text(tr("trade.clear")) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                chooser.selectedFile = File("trade_log.xlsx")
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    ImportExportService.exportTradeLogXlsx(
                        chooser.selectedFile,
                        records,
                        state.config.priceDecimals,
                        state.config.weightDecimals,
                    )
                }
            }) { Text(tr("trade.export")) }
        }

        // ---- 筛选行 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            var deviceExpanded by remember { mutableStateOf(false) }
            val devices = state.scaleService.devices()
            OutlinedTextField(
                value = devices.firstOrNull { it.id == filterDevice }?.name ?: tr("trade.filter.device"),
                onValueChange = {}, readOnly = true, label = { Text(tr("trade.filter.device")) },
                modifier = Modifier.width(150.dp),
                trailingIcon = { TextButton(onClick = { deviceExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(deviceExpanded, { deviceExpanded = false }) {
                DropdownMenuItem(onClick = { filterDevice = null; deviceExpanded = false }) { Text(tr("trade.filter.device")) }
                devices.forEach { d ->
                    DropdownMenuItem(onClick = { filterDevice = d.id; deviceExpanded = false }) { Text(d.name) }
                }
            }
            OutlinedTextField(
                filterFrom, { filterFrom = it },
                label = { Text(tr("trade.filter.from")) }, modifier = Modifier.width(140.dp),
            )
            OutlinedTextField(
                filterTo, { filterTo = it },
                label = { Text(tr("trade.filter.to")) }, modifier = Modifier.width(140.dp),
            )
            OutlinedTextField(
                filterPlu, { filterPlu = it },
                label = { Text(tr("trade.field.plu")) }, modifier = Modifier.width(110.dp),
            )
        }

        // ---- 表头 ----
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
