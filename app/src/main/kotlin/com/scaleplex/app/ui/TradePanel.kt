package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.table.ScrollableTable
import com.scaleplex.app.ui.table.TableHeader
import com.scaleplex.app.ui.table.TableRow
import com.scaleplex.app.ui.table.cellPadding
import com.scaleplex.app.ui.table.rememberRowHover
import com.scaleplex.app.ui.table.rememberTableState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.TradeRecord
import com.scaleplex.core.util.Numbers
import com.scaleplex.service.io.ImportExportService
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.launch

/**
 * 交易流水面板：按设备 / 起止日期 / PLU 号筛选查询，读取秤内流水、清空、导出 Excel。
 * 列表使用可滚动表格，窄窗口下通过横纵滚动条查看全部内容。
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
            ),
        )
    }
    val scope = rememberCoroutineScope()
    val devices = remember(refresh.value) { state.scaleService.devices() }
    val deviceNames = remember(devices) { devices.associate { it.id to it.name } }
    val table = rememberTableState(listOf(150.dp, 120.dp, 70.dp, 200.dp, 90.dp, 100.dp))

    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(
            title = tr("tab.trade"),
            subtitle = "${tr("common.total")} ${records.size}${tr("common.count")}",
        ) {
            OutlinedButton(onClick = {
                state.tradeLogService.clearAll()
                records = emptyList()
            }) { Text(tr("trade.clear"), color = UiColors.Error) }
            OutlinedButton(onClick = {
                if (records.isEmpty()) return@OutlinedButton
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
            Button(onClick = {
                scope.launch {
                    state.scaleService.devices().forEach { dev ->
                        val res = state.scaleService.driverFor(dev).readTradeLog()
                        res.getOrNull()?.let { state.tradeLogService.store(dev.id, it) }
                    }
                    records = state.tradeLogService.query(pageSize = 500)
                }
            }) { Text(tr("trade.read")) }
        }

        // ---- 筛选区 ----
        ToolbarRow {
            var deviceExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { deviceExpanded = true }) {
                    Text(
                        devices.firstOrNull { it.id == filterDevice }?.name ?: tr("trade.filter.device"),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 10.sp)
                }
                DropdownMenu(deviceExpanded, { deviceExpanded = false }) {
                    DropdownMenuItem(onClick = { filterDevice = null; deviceExpanded = false }, text = {
                        Text(tr("trade.filter.device"))
                    })
                    devices.forEach { d ->
                        DropdownMenuItem(onClick = { filterDevice = d.id; deviceExpanded = false }, text = {
                            Text(d.name)
                        })
                    }
                }
            }
            OutlinedTextField(
                filterFrom, { filterFrom = it },
                label = { Text(tr("trade.filter.from")) },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            OutlinedTextField(
                filterTo, { filterTo = it },
                label = { Text(tr("trade.filter.to")) },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            OutlinedTextField(
                filterPlu, { filterPlu = it },
                label = { Text(tr("trade.field.plu")) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }

        // ---- 流水表格 ----
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ScrollableTable(
                table = table,
                modifier = Modifier.fillMaxSize(),
                header = {
                    TableHeader(table) { index, _ ->
                        Text(
                            tradeHeaders()[index],
                            Modifier.cellPadding(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                items(records.size, key = { records[it].id }) { i ->
                    TradeTableRow(
                        table = table,
                        record = records[i],
                        deviceName = deviceNames[records[i].deviceId] ?: "-",
                        state = state,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
            if (records.isEmpty()) {
                Text(
                    tr("trade.empty"),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun tradeHeaders(): List<String> = listOf(
    tr("trade.field.time"),
    tr("trade.field.device"),
    tr("trade.field.plu"),
    tr("trade.field.name"),
    tr("trade.field.weight"),
    tr("trade.field.amount"),
)

@Composable
private fun TradeTableRow(
    table: com.scaleplex.app.ui.table.TableState,
    record: TradeRecord,
    deviceName: String,
    state: AppState,
) {
    val (hoverSource, hovered) = rememberRowHover()
    TableRow(
        table,
        modifier = Modifier
            .background(
                if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else Color.Transparent,
            )
            .hoverable(hoverSource),
    ) { index, _ ->
        val text = when (index) {
            0 -> record.timestamp?.toString()?.replace('T', ' ') ?: "-"
            1 -> deviceName
            2 -> record.pluNo.toString()
            3 -> record.pluName
            4 -> Numbers.weightToDisplay(record.weight, state.config.weightDecimals)
            else -> Numbers.minorToDisplay(record.amount, state.config.priceDecimals)
        }
        Text(
            text,
            Modifier.cellPadding(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}