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
import com.scaleplex.core.model.ShelfLifeUnit
import com.scaleplex.core.util.Numbers
import com.scaleplex.db.dao.PluDao
import com.scaleplex.service.io.ImportExportService
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser

/**
 * PLU 管理面板（界面功能对齐顶尖 LiNK64 商品资料）：
 * - 筛选字段下拉（全部/条码/货号/名称/类别编号）+ 搜索框；
 * - 列顺序对齐顶尖：类别/货号/条码起始号/名称1/单价/条码类型/主标签编号/生产日期/保质期/保质期单位/名称2/信息1/修改时间/热键/类别编号；
 * - 类别资料入口（[CategoryDialog]，对应顶尖「类别资料」），类别列显示类别名称；
 * - 勾选商品下发、从秤回收、自动生成热键、Excel 导入导出；列宽可拖拽自由拉伸。
 */
@Composable
fun PluPanel(state: AppState, refresh: MutableState<Int>) {
    var keyword by remember { mutableStateOf("") }
    var filterField by remember { mutableStateOf(PluDao.FIELD_ALL) }
    var categoryFilter by remember { mutableStateOf<Int?>(null) }
    val checked = remember { mutableStateListOf<Int>() }

    val categoryNames = remember(refresh.value) { state.categoryService.nameMap() }
    val categories = remember(refresh.value) { state.categoryService.all() }

    var plus by remember(keyword, refresh.value, categoryFilter, filterField) {
        mutableStateOf(
            state.pluService.list(keyword, page = 0, pageSize = 500, field = filterField)
                .filter { categoryFilter == null || it.mainGroup == categoryFilter }
        )
    }
    var editing by remember { mutableStateOf<PluItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, com.scaleplex.core.model.BatchResult>?>(null) }
    var recallMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val table = rememberTableState(
        listOf(
            36.dp, 70.dp, 55.dp, 90.dp, 150.dp, 75.dp, 60.dp, 60.dp, 65.dp, 55.dp, 55.dp,
            100.dp, 90.dp, 100.dp, 45.dp, 55.dp,
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 工具栏：筛选字段 + 搜索 + 类别筛选 + 类别资料 + 新增 + 下发/回收 ----
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // 筛选字段下拉（对齐顶尖「筛选字段」）
            var fieldExpanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = filterFieldLabel(filterField),
                onValueChange = {}, readOnly = true, label = { Text(tr("plu.filterField")) },
                modifier = Modifier.width(110.dp),
                trailingIcon = { TextButton(onClick = { fieldExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(fieldExpanded, { fieldExpanded = false }) {
                listOf(
                    PluDao.FIELD_ALL, PluDao.FIELD_BARCODE, PluDao.FIELD_PLU,
                    PluDao.FIELD_NAME, PluDao.FIELD_CATEGORY,
                ).forEach { f ->
                    DropdownMenuItem(onClick = { filterField = f; fieldExpanded = false }) {
                        Text(filterFieldLabel(f))
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                label = { Text(tr("plu.search")) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            // 类别筛选下拉（显示类别名称）
            var catExpanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = categoryFilter?.let { "$it ${categoryNames[it] ?: ""}" } ?: tr("plu.filterDept"),
                onValueChange = {}, readOnly = true, label = { Text(tr("plu.field.group")) },
                modifier = Modifier.width(140.dp),
                trailingIcon = { TextButton(onClick = { catExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(catExpanded, { catExpanded = false }) {
                DropdownMenuItem(onClick = { categoryFilter = null; catExpanded = false }) { Text(tr("plu.filterDept")) }
                categories.forEach { cat ->
                    DropdownMenuItem(onClick = { categoryFilter = cat.no; catExpanded = false }) {
                        Text("${cat.no} ${cat.name}")
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            // 类别资料入口（对齐顶尖「类别资料」）
            Button(onClick = { showCategory = true }) { Text(tr("category.title")) }
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
        // ---- 次工具栏：状态 / 自动生成热键 / 导入 / 导出 ----
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
                            PluCell(index - 1, p, state, categoryNames)
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
    if (showCategory) {
        CategoryDialog(state, onDismiss = { showCategory = false; refresh.value++ })
    }
    batchResults?.let {
        BatchResultDialog(state, it, onDismiss = { batchResults = null })
    }
}

/** 筛选字段显示名。 */
private fun filterFieldLabel(field: String): String = when (field) {
    PluDao.FIELD_BARCODE -> tr("plu.filter.barcode")
    PluDao.FIELD_PLU -> tr("plu.filter.plu")
    PluDao.FIELD_NAME -> tr("plu.filter.name")
    PluDao.FIELD_CATEGORY -> tr("plu.filter.category")
    else -> tr("plu.filter.all")
}

/** 列标题（第一列为勾选框；列顺序对齐顶尖 LiNK64 商品资料）。 */
private fun pluHeaders(): List<String> = listOf(
    tr("plu.field.group"),       // 类别（名称）
    tr("plu.field.pluNo"),       // 货号
    tr("plu.field.code"),        // 条码起始号
    tr("plu.field.name"),        // 名称1
    tr("plu.field.price"),       // 单价
    tr("plu.field.barcodeType"), // 条码类型1
    tr("plu.field.label1"),      // 主标签编号
    tr("plu.field.packed"),      // 生产日期
    tr("plu.field.shelf"),       // 保质期
    tr("plu.field.shelfUnit"),   // 保质期单位
    tr("plu.field.nameEn"),      // 名称2
    tr("plu.field.specialMsg"),  // 信息1
    tr("plu.field.updated"),     // 修改时间
    tr("plu.hotkey"),            // 热键
    tr("plu.field.groupNo"),     // 类别编号
)

@Composable
private fun PluCell(index: Int, p: PluItem, state: AppState, categoryNames: Map<Int, String>) {
    val text = when (index) {
        0 -> categoryNames[p.mainGroup] ?: p.mainGroup.toString()
        1 -> p.pluNo.toString()
        2 -> p.eanCode
        3 -> p.displayName
        4 -> Numbers.minorToDisplay(p.unitPrice, state.config.priceDecimals)
        5 -> barcodeTypeLabel(p)
        6 -> p.label1Format.toString()
        7 -> p.packedDate.toString()
        8 -> p.usedByDate.toString()
        9 -> ShelfLifeUnit.fromId(p.shelfLifeUnit).label
        10 -> p.nameEn
        11 -> summarize(p.specialMsgLines)
        12 -> SimpleDateFormat("yy-MM-dd HH:mm", Locale.ROOT).format(Date(p.updatedAt))
        13 -> p.hotKey.takeIf { it > 0 }?.toString() ?: "-"
        else -> p.mainGroup.toString()
    }
    Text(
        text,
        Modifier.cellPadding(),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 条码类型显示（0=EAN，9=ITF，其余显示编号）。 */
private fun barcodeTypeLabel(p: PluItem): String = when (p.barcodeType) {
    0 -> "EAN"
    9 -> "ITF"
    else -> p.barcodeType.toString()
}

/** 多行文本取首行做列表摘要，无内容显示 "-"。 */
private fun summarize(lines: List<com.scaleplex.core.model.PluTextLine>): String {
    val first = lines.firstOrNull()?.text ?: return "-"
    return if (first.length > 10) first.take(10) + "…" else first
}
