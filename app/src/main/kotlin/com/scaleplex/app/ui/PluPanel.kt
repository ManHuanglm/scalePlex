package com.scaleplex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.table.TableHeader
import com.scaleplex.app.ui.table.TableRow
import com.scaleplex.app.ui.table.cellPadding
import com.scaleplex.app.ui.table.rememberTableState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.util.Numbers
import com.scaleplex.service.io.ImportExportService
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

/**
 * PLU 管理面板（下发流程参考银豹传秤）：
 * 勾选商品下发、按部门筛选、热键列 + 自动生成热键、Excel 导入导出；
 * 列对齐 TOP2000（部门号/PLU编号/项目代码/商品名称/单价/F1F2/保质期/商品类型/热键/成分/特殊信息），
 * 表格列宽可拖拽自由拉伸。
 */
@Composable
fun PluPanel(state: AppState, refresh: MutableState<Int>) {
    var keyword by remember { mutableStateOf("") }
    var deptFilter by remember { mutableStateOf<Int?>(null) }
    val checked = remember { mutableStateListOf<Int>() }

    var plus by remember(keyword, refresh.value, deptFilter) {
        mutableStateOf(
            state.pluService.list(keyword, page = 0, pageSize = 500)
                .filter { deptFilter == null || it.mainGroup == deptFilter }
        )
    }
    var editing by remember { mutableStateOf<PluItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, com.scaleplex.core.model.BatchResult>?>(null) }
    var recallMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val table = rememberTableState(
        listOf(36.dp, 60.dp, 70.dp, 110.dp, 170.dp, 80.dp, 50.dp, 60.dp, 60.dp, 50.dp, 110.dp, 110.dp),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 工具栏：搜索 / 部门筛选 / 新增 / 下发选中 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                label = { Text(tr("plu.search")) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            var deptExpanded by remember { mutableStateOf(false) }
            val depts = state.pluService.all().map { it.mainGroup }.distinct().sorted()
            OutlinedTextField(
                value = deptFilter?.toString() ?: tr("plu.filterDept"),
                onValueChange = {}, readOnly = true, label = { Text(tr("plu.field.group")) },
                modifier = Modifier.width(130.dp),
                trailingIcon = { TextButton(onClick = { deptExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(deptExpanded, { deptExpanded = false }) {
                DropdownMenuItem(onClick = { deptFilter = null; deptExpanded = false }) { Text(tr("plu.filterDept")) }
                depts.forEach { d ->
                    DropdownMenuItem(onClick = { deptFilter = d; deptExpanded = false }) { Text(d.toString()) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { editing = PluItem(pluNo = (state.pluService.count() + 1)); showEditor = true }) {
                Text(tr("plu.add"))
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                scope.launch {
                    // 回收（参考 TOP2000"回收商品"）：逐台回收 PLU 并回写本地库
                    var total = 0
                    state.scaleService.devices().filter { it.enabled }.forEach { dev ->
                        state.scaleService.driverFor(dev).uploadPlu().getOrNull()?.let { items ->
                            state.pluService.saveAll(items)
                            total += items.size
                        }
                    }
                    recallMessage = tr("plu.recallDone") + total
                    refresh.value++
                }
            }) { Text(tr("plu.upload")) }
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                scope.launch {
                    // 勾选为空时下发当前筛选结果（参考银豹：勾选商品 → 下发传秤）
                    val targets = plus.filter { checked.contains(it.pluNo) }.ifEmpty { plus }
                    batchResults = state.scaleService.downloadToDevices(
                        state.scaleService.devices().filter { it.enabled }, targets,
                    )
                }
            }) { Text(tr("plu.download.selected")) }
        }
        // ---- 次工具栏：自动生成热键 / 导入 / 导出 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recallMessage.isNotBlank()) {
                Text(recallMessage, fontSize = 12.sp, color = Color(0xFF2E7D32))
            }
            Text(
                tr("plu.checkedCount") + " ${checked.size}",
                fontSize = 12.sp, color = Color(0xFF808080),
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                // 勾选优先，无勾选则按当前列表顺序依次编 1..N（参考银豹"自动生成热键"）
                val targets = plus.filter { checked.contains(it.pluNo) }.ifEmpty { plus }
                targets.forEachIndexed { i, p ->
                    state.pluService.bulkEdit(listOf(p.pluNo)) { it.copy(hotKey = i + 1) }
                }
                refresh.value++
            }) { Text(tr("plu.genHotkey")) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val n = state.pluService.saveAll(
                        ImportExportService.importPluXlsx(chooser.selectedFile, state.config.priceDecimals),
                    )
                    refresh.value++
                }
            }) { Text(tr("plu.import")) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val chooser = JFileChooser()
                chooser.selectedFile = File("plu_export.xlsx")
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    ImportExportService.exportPluXlsx(chooser.selectedFile, state.pluService.all(), state.config.priceDecimals)
                }
            }) { Text(tr("plu.export")) }
        }

        // ---- 表格（横向滚动共享：表头与数据行同步，列宽拖拽调整）----
        Column(modifier = Modifier.weight(1f).horizontalScroll(table.scroll)) {
            TableHeader(table) { index, _ ->
                if (index == 0) {
                    Checkbox(
                        checked = plus.isNotEmpty() && checked.containsAll(plus.map { it.pluNo }),
                        onCheckedChange = { all ->
                            if (all) checked.addAll(plus.map { it.pluNo }.filter { !checked.contains(it) })
                            else checked.removeAll(plus.map { it.pluNo }.toSet())
                        },
                        modifier = Modifier.cellPadding(),
                    )
                } else {
                    Text(
                        pluHeaders()[index - 1],
                        Modifier.cellPadding(),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Divider()
            LazyColumn(modifier = Modifier.width(table.totalWidth).weight(1f)) {
                items(plus, key = { it.pluNo }) { p ->
                    TableRow(table, modifier = Modifier.clickable { editing = p; showEditor = true }) { index, _ ->
                        if (index == 0) {
                            Checkbox(
                                checked = checked.contains(p.pluNo),
                                onCheckedChange = { c ->
                                    if (c) checked.add(p.pluNo) else checked.remove(p.pluNo)
                                },
                                modifier = Modifier.cellPadding(),
                            )
                        } else {
                            PluCell(index - 1, p, state)
                        }
                    }
                    Divider()
                }
            }
        }
    }

    if (showEditor && editing != null) {
        PluEditorDialog(state, editing!!, onDismiss = { showEditor = false }, onSave = { item ->
            val error = state.pluService.save(item).fold(
                onSuccess = { refresh.value++; showEditor = false; null },
                onFailure = { it.message ?: "save failed" },
            )
            error
        })
    }
    batchResults?.let {
        BatchResultDialog(state, it, onDismiss = { batchResults = null })
    }
}

/** 列标题（第一列为勾选框；其余顺序对齐 TOP2000，含热键列）。 */
private fun pluHeaders(): List<String> = listOf(
    tr("plu.field.group"),
    tr("plu.field.no"),
    tr("plu.field.code"),
    tr("plu.field.name"),
    tr("plu.field.price"),
    tr("plu.field.flag"),
    tr("plu.field.shelf"),
    tr("plu.field.type"),
    tr("plu.hotkey"),
    tr("plu.field.ingredient"),
    tr("plu.field.specialMsg"),
)

@Composable
private fun PluCell(index: Int, p: PluItem, state: AppState) {
    val text = when (index) {
        0 -> p.mainGroup.toString()
        1 -> p.pluNo.toString()
        2 -> p.eanCode
        3 -> p.displayName
        4 -> Numbers.minorToDisplay(p.unitPrice, state.config.priceDecimals)
        5 -> p.flagData
        6 -> p.usedByDate.toString()
        7 -> if (p.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh")
        8 -> p.hotKey.takeIf { it > 0 }?.toString() ?: "-"
        9 -> summarize(p.ingredientLines)
        else -> summarize(p.specialMsgLines)
    }
    Text(
        text,
        Modifier.cellPadding(),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 多行文本取首行做列表摘要，无内容显示 "-"。 */
private fun summarize(lines: List<com.scaleplex.core.model.PluTextLine>): String {
    val first = lines.firstOrNull()?.text ?: return "-"
    return if (first.length > 10) first.take(10) + "…" else first
}
