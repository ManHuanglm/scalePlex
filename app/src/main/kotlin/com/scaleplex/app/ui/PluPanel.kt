package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluUnit
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
 * - 顶部标题栏放主操作（新增 / 下发），筛选与次要操作分区展示，窗口变窄自动换行；
 * - 列顺序对齐顶尖：类别/货号/条码起始号/名称1/单价/条码类型/主标签编号/生产日期/保质期/保质期单位/名称2/信息1/修改时间/热键/类别编号；
 * - 表格支持拖拽列宽、行悬停高亮、横纵滚动条（内容超出可滚动查看）；
 * - 勾选商品下发、从秤回收、自动生成热键、Excel 导入导出、类别资料管理。
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
                .filter { categoryFilter == null || it.mainGroup == categoryFilter },
        )
    }
    var editing by remember { mutableStateOf<PluItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, BatchResult>?>(null) }
    var recallMessage by remember { mutableStateOf("") }
    var selectedPluNo by remember { mutableStateOf<Int?>(null) }
    var inlineEdit by remember { mutableStateOf<InlineEdit?>(null) }
    var inlineMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    /** 提交单格编辑：解析失败或保存失败时保留列表原值并提示原因。 */
    fun saveInline(updated: PluItem?) {
        if (updated == null) {
            inlineMessage = tr("plu.inlineInvalid")
        } else {
            state.pluService.save(updated).fold(
                onSuccess = { inlineMessage = ""; refresh.value++ },
                onFailure = { inlineMessage = tr("plu.inlineSaveFailed") + (it.message ?: "") },
            )
        }
        inlineEdit = null
    }

    fun commitInlineEdit(plu: PluItem, column: Int, input: String) {
        saveInline(applyCellEdit(plu, column, input, state.config.priceDecimals))
    }

    /** 是/否类单元格双击直接切换并保存。 */
    fun commitCellToggle(plu: PluItem, column: Int) {
        saveInline(toggleCell(plu, column))
    }

    val table = rememberTableState(
        listOf(
            36.dp, 60.dp, 60.dp, 160.dp, 70.dp, 70.dp, 80.dp, 70.dp, 65.dp, 65.dp,
            90.dp, 110.dp, 100.dp, 50.dp, 60.dp,
            // 托利多商品维护列（对齐 Scale Manager 商品维护）
            70.dp, 75.dp, 75.dp, 75.dp, 80.dp, 90.dp, 90.dp, 90.dp, 90.dp, 95.dp,
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(
            title = tr("tab.plu"),
            subtitle = "${tr("common.total")} ${plus.size}${tr("common.count")} · " +
                "${tr("plu.checkedCount")} ${checked.size}",
        ) {
            OutlinedButton(onClick = {
                scope.launch {
                    // 回收（参考 TOP2000「回收商品」）：逐台回收 PLU 并回写本地库
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
            Button(onClick = {
                editing = PluItem(pluNo = (state.pluService.count() + 1))
                showEditor = true
            }) { Text(tr("plu.add")) }
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

        // ---- 筛选区：筛选字段 + 搜索 + 类别筛选 ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var fieldExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { fieldExpanded = true }) {
                    Text(tr("plu.filterField") + "：" + filterFieldLabel(filterField), fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 10.sp)
                }
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
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text(tr("plu.search")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            var catExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { catExpanded = true }) {
                    Text(
                        categoryFilter?.let { "$it ${categoryNames[it] ?: ""}" } ?: tr("plu.filterDept"),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 10.sp)
                }
                DropdownMenu(catExpanded, { catExpanded = false }) {
                    DropdownMenuItem(onClick = { categoryFilter = null; catExpanded = false }) {
                        Text(tr("plu.filterDept"))
                    }
                    categories.forEach { cat ->
                        DropdownMenuItem(onClick = { categoryFilter = cat.no; catExpanded = false }) {
                            Text("${cat.no} ${cat.name}")
                        }
                    }
                }
            }
        }

        // ---- 次要操作区：编辑选中 / 类别资料 / 自动热键 / 导入 / 导出 ----
        ToolbarRow {
            OutlinedButton(
                enabled = selectedPluNo != null,
                onClick = {
                    plus.firstOrNull { it.pluNo == selectedPluNo }?.let {
                        editing = it
                        showEditor = true
                    }
                },
            ) { Text(tr("plu.editSelected")) }
            OutlinedButton(onClick = { showCategory = true }) { Text(tr("category.title")) }
            OutlinedButton(onClick = {
                // 勾选优先，无勾选则按当前列表顺序依次编 1..N（参考银豹「自动生成热键」）
                val targets = plus.filter { checked.contains(it.pluNo) }.ifEmpty { plus }
                targets.forEachIndexed { i, p ->
                    state.pluService.bulkEdit(listOf(p.pluNo)) { it.copy(hotKey = i + 1) }
                }
                refresh.value++
            }) { Text(tr("plu.genHotkey")) }
            OutlinedButton(onClick = {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    state.pluService.saveAll(
                        ImportExportService.importPluXlsx(chooser.selectedFile, state.config.priceDecimals),
                    )
                    refresh.value++
                }
            }) { Text(tr("plu.import")) }
            OutlinedButton(onClick = {
                val chooser = JFileChooser()
                chooser.selectedFile = File("plu_export.xlsx")
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    ImportExportService.exportPluXlsx(
                        chooser.selectedFile,
                        state.pluService.all(),
                        state.config.priceDecimals,
                    )
                }
            }) { Text(tr("plu.export")) }
        }

        if (recallMessage.isNotBlank()) {
            Text(
                recallMessage,
                fontSize = 12.sp,
                color = UiColors.Success,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("plu.inlineHint"),
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            )
            if (inlineMessage.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(inlineMessage, fontSize = 12.sp, color = UiColors.Error)
            }
        }

        // ---- 表格（列宽拖拽 + 横纵滚动条）----
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ScrollableTable(
                table = table,
                modifier = Modifier.fillMaxSize(),
                header = {
                    TableHeader(table) { index, _ ->
                        if (index == 0) {
                            Checkbox(
                                checked = plus.isNotEmpty() && checked.containsAll(plus.map { it.pluNo }),
                                onCheckedChange = { all ->
                                    if (all) {
                                        checked.addAll(plus.map { it.pluNo }.filter { !checked.contains(it) })
                                    } else {
                                        checked.removeAll(plus.map { it.pluNo }.toSet())
                                    }
                                },
                                modifier = Modifier.cellPadding(),
                            )
                        } else {
                            HeaderCell(pluHeaders()[index - 1])
                        }
                    }
                },
            ) {
                items(plus.size, key = { plus[it].pluNo }) { i ->
                    val p = plus[i]
                    val (hoverSource, hovered) = rememberRowHover()
                    val widthsPx = table.widths.map { with(density) { it.toPx() } }
                    TableRow(
                        table,
                        modifier = Modifier
                            .background(
                                when {
                                    selectedPluNo == p.pluNo -> MaterialTheme.colors.primary.copy(alpha = 0.10f)
                                    hovered -> MaterialTheme.colors.primary.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                },
                            )
                            .hoverable(hoverSource)
                            // 单击选中；双击单元格进入单格编辑（只读列双击打开完整编辑窗口）
                            .pointerInput(p.pluNo, widthsPx) {
                                detectTapGestures(
                                    onTap = { if (inlineEdit == null) selectedPluNo = p.pluNo },
                                    onDoubleTap = doubleTap@{ offset ->
                                        if (inlineEdit != null) return@doubleTap
                                        var acc = 0f
                                        var column = -1
                                        for ((idx, width) in widthsPx.withIndex()) {
                                            if (offset.x < acc + width) {
                                                column = idx
                                                break
                                            }
                                            acc += width
                                        }
                                        val cell = column - 1
                                        if (cell >= 0) {
                                            when {
                                                cell in TOGGLE_COLUMNS -> commitCellToggle(p, cell)
                                                cell in EDITABLE_COLUMNS -> {
                                                    inlineEdit = InlineEdit(
                                                        pluNo = p.pluNo,
                                                        column = cell,
                                                        value = cellText(cell, p, state, categoryNames),
                                                    )
                                                }
                                                else -> {
                                                    editing = p
                                                    showEditor = true
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                    ) { index, _ ->
                        if (index == 0) {
                            Checkbox(
                                checked = checked.contains(p.pluNo),
                                onCheckedChange = { c ->
                                    if (c) checked.add(p.pluNo) else checked.remove(p.pluNo)
                                },
                                modifier = Modifier.cellPadding(),
                            )
                        } else {
                            val column = index - 1
                            PluCell(
                                column = column,
                                p = p,
                                state = state,
                                categoryNames = categoryNames,
                                editing = inlineEdit?.takeIf {
                                    it.pluNo == p.pluNo && it.column == column
                                },
                                onCommit = { input -> commitInlineEdit(p, column, input) },
                                onCancel = { inlineEdit = null },
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                }
            }
            if (plus.isEmpty()) {
                Text(
                    tr("plu.empty"),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
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

/** 表头单元格统一样式。 */
@Composable
private fun HeaderCell(text: String) {
    Text(
        text,
        Modifier.cellPadding(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
    // 托利多商品维护列（对齐 Scale Manager 商品维护）
    tr("plu.mt.weightMode"),
    tr("plu.mt.tarePrice"),
    tr("plu.mt.allowMarkdown"),
    tr("plu.mt.allowDiscount"),
    tr("plu.mt.extraText"),
    tr("plu.mt.packedPrint"),
    tr("plu.mt.packedUnit"),
    tr("plu.mt.printPacked"),
    tr("plu.mt.shelfPrint"),
    tr("plu.mt.printShelf"),
)

/** 可双击直接编辑的列（0 起，首列勾选框除外）。 */
private val EDITABLE_COLUMNS = setOf(2, 3, 4, 5, 6, 7, 8, 10, 13, 14, 19)

/** 是/否类列：双击直接切换并保存。 */
private val TOGGLE_COLUMNS = setOf(15, 16, 17, 18, 20, 21, 22, 23, 24)

/** 正在内联编辑的单格。 */
private data class InlineEdit(val pluNo: Int, val column: Int, val value: String)

@Composable
private fun PluCell(
    column: Int,
    p: PluItem,
    state: AppState,
    categoryNames: Map<Int, String>,
    editing: InlineEdit?,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    if (editing != null) {
        InlineCellEditor(initial = editing.value, onCommit = onCommit, onCancel = onCancel)
        return
    }
    Text(
        cellText(column, p, state, categoryNames),
        Modifier.cellPadding().fillMaxWidth(),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 单格显示值（列表与内联编辑共用）。 */
private fun cellText(
    column: Int,
    p: PluItem,
    state: AppState,
    categoryNames: Map<Int, String>,
): String = when (column) {
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
    15 -> if (p.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh")
    16 -> yesNo(p.tarePriceFlag)
    17 -> yesNo(p.allowMarkdown)
    18 -> yesNo(p.allowDiscount)
    19 -> p.extraTextNo.toString()
    20 -> yesNo(p.packedDatePrintFlag)
    21 -> if (p.packedDateUnit == 1) tr("plu.mt.unit.month") else tr("plu.mt.unit.day")
    22 -> yesNo(p.printPackedDate)
    23 -> yesNo(p.shelfLifePrintFlag)
    24 -> yesNo(p.printShelfLifeDate)
    else -> p.mainGroup.toString()
}

/** 是/否显示。 */
private fun yesNo(value: Boolean): String = tr(if (value) "common.yes" else "common.no")

/** 将单格输入写回 PLU；返回 null 表示输入格式不合法。 */
private fun applyCellEdit(p: PluItem, column: Int, input: String, priceDecimals: Int): PluItem? {
    val text = input.trim()
    return when (column) {
        2 -> p.copy(eanCode = text)
        3 -> p.copy(name = text)
        4 -> if (Numbers.parseDouble(text) != null) {
            p.copy(unitPrice = Numbers.displayToMinor(text, priceDecimals))
        } else {
            null
        }
        5 -> text.toIntOrNull()?.let { p.copy(barcodeType = it) }
        6 -> text.toIntOrNull()?.let { p.copy(label1Format = it) }
        7 -> text.toIntOrNull()?.let { p.copy(packedDate = it) }
        8 -> text.toIntOrNull()?.let { p.copy(usedByDate = it.coerceAtLeast(0)) }
        10 -> p.copy(nameEn = text)
        13 -> text.toIntOrNull()?.let { p.copy(hotKey = it.coerceIn(0, 999)) }
        14 -> text.toIntOrNull()?.let { p.copy(mainGroup = it) }
        19 -> text.toIntOrNull()?.let { p.copy(extraTextNo = it.coerceAtLeast(0)) }
        else -> null
    }
}

/** 双击切换是/否类单元格；返回 null 表示该列不支持切换。 */
private fun toggleCell(p: PluItem, column: Int): PluItem? = when (column) {
    15 -> p.copy(
        weightFlag = if (p.weightFlag == 1) 0 else 1,
        unit = if (p.weightFlag == 1) PluUnit.KG.id else PluUnit.PCS.id,
    )
    16 -> p.copy(tarePriceFlag = !p.tarePriceFlag)
    17 -> p.copy(allowMarkdown = !p.allowMarkdown)
    18 -> p.copy(allowDiscount = !p.allowDiscount)
    20 -> p.copy(packedDatePrintFlag = !p.packedDatePrintFlag)
    21 -> p.copy(packedDateUnit = if (p.packedDateUnit == 1) 0 else 1)
    22 -> p.copy(printPackedDate = !p.printPackedDate)
    23 -> p.copy(shelfLifePrintFlag = !p.shelfLifePrintFlag)
    24 -> p.copy(printShelfLifeDate = !p.printShelfLifeDate)
    else -> null
}

/** 单元格内联编辑框：Enter 提交、Esc 取消、失焦提交。 */
@Composable
private fun InlineCellEditor(initial: String, onCommit: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    var finished by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun commit() {
        if (!finished) {
            finished = true
            onCommit(text)
        }
    }

    fun cancel() {
        if (!finished) {
            finished = true
            onCancel()
        }
    }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colors.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colors.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(2.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus && !finished) {
                    commit()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        commit()
                        true
                    }
                    Key.Escape -> {
                        cancel()
                        true
                    }
                    else -> false
                }
            },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
