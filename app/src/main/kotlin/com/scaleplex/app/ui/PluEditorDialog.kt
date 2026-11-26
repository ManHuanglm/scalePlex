package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DigiTables
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.util.Numbers

/**
 * 新增 / 编辑 PLU 弹窗（按品牌分区，界面基线对齐 TOP2000「添加新品」表单）：
 * - 顶部「编辑模板」下拉选择品牌，不同品牌展示各自的专属字段区；
 * - 通用字段（全部品牌）：部门号、PLU编号、项目代码、单价(/Kg)、保质期、商品名称、
 *   商品类型、成分、特殊信息；
 * - 寺冈（DIGI）专属：F1F2、条码格式、标签格式1/2（DigiTables 对照表下拉）；
 * - 托利多（MT）专属：折价/折扣/除皮价、包装/保质期打印标志、会员价/特价、附加文本号；
 * - 高级选项（全部品牌）：品名英 / 成本 / 皮重 / 热键。
 * 保存前先本地校验、再交由 [onSave] 落库；[onSave] 返回错误信息（null = 成功），
 * 失败时错误就地提示，弹窗不关闭。
 */
@Composable
fun PluEditorDialog(state: AppState, initial: PluItem, onDismiss: () -> Unit, onSave: (PluItem) -> String?) {
    var plu by remember { mutableStateOf(initial) }
    var ingredientText by remember { mutableStateOf(linesToText(initial.ingredientLines)) }
    var specialMsgText by remember { mutableStateOf(linesToText(initial.specialMsgLines)) }
    var showAdvanced by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    // 编辑模板（品牌）：默认取系统设置中的默认品牌，切换仅影响本弹窗展示的字段区
    var brand by remember { mutableStateOf(state.config.defaultBrand) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.pluNo > 0 && initial.id > 0) tr("plu.edit") else tr("plu.add")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(500.dp)) {
                // ---- 编辑模板（品牌）下拉 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("plu.editor.brand"), Modifier.width(90.dp))
                    var brandExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = brand.displayName, onValueChange = {}, readOnly = true,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        trailingIcon = { TextButton(onClick = { brandExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(brandExpanded, { brandExpanded = false }) {
                        com.scaleplex.core.model.ScaleBrand.entries.forEach { b ->
                            DropdownMenuItem(onClick = { brand = b; brandExpanded = false }, text = {
                                Text(b.displayName)
                            })
                        }
                    }
                }
                // ---- 类别（下拉，对齐顶尖类别资料） / PLU编号 ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val categories = remember { state.categoryService.all() }
                    Text(tr("plu.field.group"), Modifier.width(90.dp))
                    var catExpanded by remember { mutableStateOf(false) }
                    val selectedCategory = categories.firstOrNull { it.no == plu.mainGroup }
                    OutlinedTextField(
                        value = selectedCategory?.let { "${it.no} ${it.name}" } ?: plu.mainGroup.toString(),
                        onValueChange = {}, readOnly = true, modifier = Modifier.weight(1f),
                        trailingIcon = { TextButton(onClick = { catExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(catExpanded, { catExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(onClick = { plu = plu.copy(mainGroup = cat.no); catExpanded = false }, text = {
                                Text("${cat.no} ${cat.name}")
                            })
                        }
                        if (categories.isEmpty()) {
                            DropdownMenuItem(onClick = { catExpanded = false }, text = { Text(tr("category.empty")) })
                        }
                    }
                    Field(tr("plu.field.no"), plu.pluNo.toString(), Modifier.width(110.dp)) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(pluNo = it) }
                    }
                }
                // ---- 项目代码 ----
                Field(tr("plu.field.code"), plu.eanCode, Modifier.fillMaxWidth()) { plu = plu.copy(eanCode = it.trim()) }
                // ---- 单价(/Kg) + 条码预览 ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("plu.price.perKg"), Numbers.minorToDisplay(plu.unitPrice, state.config.priceDecimals), Modifier.weight(1f)) { s ->
                        if (Numbers.parseDouble(s) != null) {
                            plu = plu.copy(unitPrice = Numbers.displayToMinor(s, state.config.priceDecimals))
                        }
                    }
                    Text(
                        plu.eanCode.ifBlank { "0000000000" },
                        fontSize = 12.sp,
                        color = Color(0xFF808080),
                        modifier = Modifier.width(120.dp),
                    )
                }
                // ---- 保质期 / 保质期单位 ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(tr("plu.field.shelf"), plu.usedByDate.toString(), Modifier.weight(1f)) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(usedByDate = it.coerceAtLeast(0)) }
                    }
                    Text(tr("plu.field.shelfUnit"), Modifier.width(70.dp))
                    var shelfUnitExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = com.scaleplex.core.model.ShelfLifeUnit.fromId(plu.shelfLifeUnit).label,
                        onValueChange = {}, readOnly = true, modifier = Modifier.width(90.dp),
                        trailingIcon = { TextButton(onClick = { shelfUnitExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(shelfUnitExpanded, { shelfUnitExpanded = false }) {
                        com.scaleplex.core.model.ShelfLifeUnit.entries.forEach { u ->
                            DropdownMenuItem(onClick = {
                                plu = plu.copy(shelfLifeUnit = u.id); shelfUnitExpanded = false
                            }, text = { Text(u.label) })
                        }
                    }
                }
                // ---- 商品名称 ----
                Field(tr("plu.field.name"), plu.name, Modifier.fillMaxWidth()) { plu = plu.copy(name = it) }
                // ---- 商品类型 / 计价单位 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("plu.field.type"), Modifier.width(90.dp))
                    var typeExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = if (plu.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh"),
                        onValueChange = {}, readOnly = true, modifier = Modifier.width(140.dp),
                        trailingIcon = { TextButton(onClick = { typeExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(typeExpanded, { typeExpanded = false }) {
                        DropdownMenuItem(onClick = { plu = plu.copy(weightFlag = 0); typeExpanded = false }, text = { Text(tr("plu.weight.weigh")) })
                        DropdownMenuItem(onClick = { plu = plu.copy(weightFlag = 1); typeExpanded = false }, text = { Text(tr("plu.weight.count")) })
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(tr("plu.unit"), Modifier.width(80.dp))
                    var unitExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = com.scaleplex.core.model.PluUnit.fromId(plu.unit).label,
                        onValueChange = {}, readOnly = true, modifier = Modifier.width(140.dp),
                        trailingIcon = { TextButton(onClick = { unitExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(unitExpanded, { unitExpanded = false }) {
                        com.scaleplex.core.model.PluUnit.entries.forEach { u ->
                            DropdownMenuItem(onClick = {
                                // 计数(PCS) 与 计件 标志联动
                                plu = plu.copy(unit = u.id, weightFlag = if (u == com.scaleplex.core.model.PluUnit.PCS) 1 else 0)
                                unitExpanded = false
                            }, text = { Text(u.label) })
                        }
                    }
                }
                // ---- 成分 / 特殊信息 ----
                Text(tr("plu.field.ingredient"), Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    ingredientText, { ingredientText = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    minLines = 3, maxLines = 5,
                )
                Text(tr("plu.field.specialMsg"))
                OutlinedTextField(
                    specialMsgText, { specialMsgText = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    minLines = 3, maxLines = 5,
                )

                // ---- 寺冈属性（DIGI 专属：F1F2 / 条码格式 / 标签格式，对照 SM.ini 定义表）----
                if (brand == com.scaleplex.core.model.ScaleBrand.DIGI) {
                    Text(tr("plu.digi.section"), Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(tr("plu.field.flag"), plu.flagData, Modifier.weight(1f)) {
                            plu = plu.copy(flagData = it.trim())
                        }
                        TableDropdownField(
                            label = tr("plu.field.barFormat"),
                            display = (DigiTables.EAN_BARCODE_FORMATS.firstOrNull { it.first == plu.barcodeFormat }?.second)
                                ?: plu.barcodeFormat.toString(),
                            options = DigiTables.EAN_BARCODE_FORMATS,
                            modifier = Modifier.weight(1.4f),
                        ) { plu = plu.copy(barcodeFormat = it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TableDropdownField(
                            label = tr("plu.field.label1"),
                            display = (DigiTables.LABEL1_FORMATS.firstOrNull { it.first == plu.label1Format }?.second)
                                ?: plu.label1Format.toString(),
                            options = DigiTables.LABEL1_FORMATS,
                            modifier = Modifier.weight(1f),
                        ) { plu = plu.copy(label1Format = it) }
                        TableDropdownField(
                            label = tr("plu.field.label2"),
                            display = (DigiTables.LABEL2_FORMATS.firstOrNull { it.first == plu.label2Format }?.second)
                                ?: plu.label2Format.toString(),
                            options = DigiTables.LABEL2_FORMATS,
                            modifier = Modifier.weight(1f),
                        ) { plu = plu.copy(label2Format = it) }
                    }
                }

                // ---- 托利多属性（MT 专属，对齐 Scale Manager 商品维护列，本地维护/下发映射待联调）----
                if (brand == com.scaleplex.core.model.ScaleBrand.METTLER_TOLEDO) {
                    Text(tr("plu.mt.section"), Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckField(tr("plu.mt.allowMarkdown"), plu.allowMarkdown) { plu = plu.copy(allowMarkdown = it) }
                    CheckField(tr("plu.mt.allowDiscount"), plu.allowDiscount) { plu = plu.copy(allowDiscount = it) }
                    CheckField(tr("plu.mt.tarePrice"), plu.tarePriceFlag) { plu = plu.copy(tarePriceFlag = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckField(tr("plu.mt.packedPrint"), plu.packedDatePrintFlag) {
                        plu = plu.copy(packedDatePrintFlag = it)
                    }
                    Text(tr("plu.mt.packedUnit"), Modifier.width(90.dp))
                    var packedUnitExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = if (plu.packedDateUnit == 1) tr("plu.mt.unit.month") else tr("plu.mt.unit.day"),
                        onValueChange = {}, readOnly = true, modifier = Modifier.width(80.dp),
                        trailingIcon = { TextButton(onClick = { packedUnitExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(packedUnitExpanded, { packedUnitExpanded = false }) {
                        DropdownMenuItem(onClick = {
                            plu = plu.copy(packedDateUnit = 0)
                            packedUnitExpanded = false
                        }, text = { Text(tr("plu.mt.unit.day")) })
                        DropdownMenuItem(onClick = {
                            plu = plu.copy(packedDateUnit = 1)
                            packedUnitExpanded = false
                        }, text = { Text(tr("plu.mt.unit.month")) })
                    }
                    CheckField(tr("plu.mt.printPacked"), plu.printPackedDate) {
                        plu = plu.copy(printPackedDate = it)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckField(tr("plu.mt.shelfPrint"), plu.shelfLifePrintFlag) {
                        plu = plu.copy(shelfLifePrintFlag = it)
                    }
                    CheckField(tr("plu.mt.printShelf"), plu.printShelfLifeDate) {
                        plu = plu.copy(printShelfLifeDate = it)
                    }
                    Field(tr("plu.mt.extraText"), plu.extraTextNo.toString(), Modifier.weight(1f)) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(extraTextNo = it.coerceAtLeast(0)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(tr("plu.memberPrice"), Numbers.minorToDisplay(plu.memberPrice, state.config.priceDecimals), Modifier.weight(1f)) {
                        plu = plu.copy(memberPrice = Numbers.displayToMinor(it, state.config.priceDecimals))
                    }
                    Field(tr("plu.specialPrice"), Numbers.minorToDisplay(plu.specialPrice, state.config.priceDecimals), Modifier.weight(1f)) {
                        plu = plu.copy(specialPrice = Numbers.displayToMinor(it, state.config.priceDecimals))
                    }
                }
                }

                // ---- 高级选项 ----
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.align(Alignment.Start)) {
                    Text(if (showAdvanced) "▾ " + tr("plu.advanced") else "▸ " + tr("plu.advanced"))
                }
                if (showAdvanced) {
                    Field(tr("plu.field.nameEn"), plu.nameEn, Modifier.fillMaxWidth()) { plu = plu.copy(nameEn = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(tr("plu.field.cost"), Numbers.minorToDisplay(plu.cost, state.config.priceDecimals), Modifier.weight(1f)) {
                            plu = plu.copy(cost = Numbers.displayToMinor(it, state.config.priceDecimals))
                        }
                        Field(tr("plu.field.tare"), plu.tare.toString(), Modifier.weight(1f)) { s ->
                            s.toIntOrNull()?.let { plu = plu.copy(tare = it.coerceAtLeast(0)) }
                        }
                    }
                    Field(tr("plu.hotkey"), if (plu.hotKey > 0) plu.hotKey.toString() else "", Modifier.fillMaxWidth()) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(hotKey = it.coerceIn(0, 999)) }
                    }
                }

                // ---- 校验错误提示 ----
                if (errors.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        errors.forEach {
                            Text(it, fontSize = 11.sp, color = UiColors.Error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val candidate = plu.copy(
                    ingredientLines = textToLines(ingredientText),
                    specialMsgLines = textToLines(specialMsgText),
                )
                when (val v = state.pluService.validate(candidate)) {
                    is com.scaleplex.core.util.ValidationResult.Ok -> {
                        val saveError = onSave(candidate)
                        if (saveError != null) errors = listOf(saveError)
                    }
                    is com.scaleplex.core.util.ValidationResult.Invalid -> errors = v.errors
                }
            }) { Text(tr("plu.save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("plu.back")) }
        },
    )
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/** 品牌专属字段通用下拉（编号+名称对照表，如 DIGI 标签/条码格式表）。 */
@Composable
private fun TableDropdownField(
    label: String,
    display: String,
    options: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = display, onValueChange = {}, readOnly = true,
        label = { Text(label) },
        modifier = modifier.padding(vertical = 4.dp),
        trailingIcon = { TextButton(onClick = { expanded = true }) { Text("▾") } },
    )
    DropdownMenu(expanded, { expanded = false }) {
        options.forEach { (no, name) ->
            DropdownMenuItem(onClick = { onSelect(no); expanded = false }, text = {
                Text("$no · $name")
            })
        }
    }
}

/** 是/否勾选项（托利多属性）。 */
@Composable
private fun CheckField(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.sp)
    }
}

/** TOP2000 的成分/特殊信息为多行文本，换行即一条秤端文本行（默认字库 23）。 */
private const val DEFAULT_TEXT_FONT_NO = 23

private fun linesToText(lines: List<PluTextLine>): String = lines.joinToString("\n") { it.text }

private fun textToLines(text: String): List<PluTextLine> = text
    .split("\n")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { PluTextLine(fontNo = DEFAULT_TEXT_FONT_NO, text = it) }