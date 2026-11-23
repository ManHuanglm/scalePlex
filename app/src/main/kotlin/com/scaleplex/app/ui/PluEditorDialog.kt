package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
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
 * 新增 / 编辑 PLU 弹窗，字段布局对齐 TOP2000「添加新品」表单：
 * 部门号、PLU编号、项目代码、单价(/Kg)、F1F2、保质期、商品名称、商品类型、成分、特殊信息，
 * 另保留品名英 / 成本 / 皮重 / 标签格式为「高级选项」折叠区。
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.pluNo > 0 && initial.id > 0) tr("plu.edit") else tr("plu.add")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(500.dp)) {
                // ---- 部门号 / PLU编号 ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("plu.field.group"), plu.mainGroup.toString(), Modifier.weight(1f)) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(mainGroup = it) }
                    }
                    Field(tr("plu.field.no"), plu.pluNo.toString(), Modifier.weight(1f)) { s ->
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
                // ---- F1F2 / 保质期 ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("plu.field.flag"), plu.flagData, Modifier.weight(1f)) { plu = plu.copy(flagData = it) }
                    Field(tr("plu.field.shelf"), plu.usedByDate.toString(), Modifier.weight(1f)) { s ->
                        s.toIntOrNull()?.let { plu = plu.copy(usedByDate = it.coerceAtLeast(0)) }
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
                        DropdownMenuItem(onClick = { plu = plu.copy(weightFlag = 0); typeExpanded = false }) { Text(tr("plu.weight.weigh")) }
                        DropdownMenuItem(onClick = { plu = plu.copy(weightFlag = 1); typeExpanded = false }) { Text(tr("plu.weight.count")) }
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
                            }) { Text(u.label) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(tr("plu.memberPrice"), Numbers.minorToDisplay(plu.memberPrice, state.config.priceDecimals), Modifier.weight(1f)) {
                            plu = plu.copy(memberPrice = Numbers.displayToMinor(it, state.config.priceDecimals))
                        }
                        Field(tr("plu.specialPrice"), Numbers.minorToDisplay(plu.specialPrice, state.config.priceDecimals), Modifier.weight(1f)) {
                            plu = plu.copy(specialPrice = Numbers.displayToMinor(it, state.config.priceDecimals))
                        }
                    }
                    var labelExpanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = (DigiTables.LABEL1_FORMATS.firstOrNull { it.first == plu.label1Format }?.second)
                            ?: plu.label1Format.toString(),
                        onValueChange = {}, readOnly = true,
                        label = { Text(tr("plu.field.label1")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        trailingIcon = { TextButton(onClick = { labelExpanded = true }) { Text("▾") } },
                    )
                    DropdownMenu(labelExpanded, { labelExpanded = false }) {
                        DigiTables.LABEL1_FORMATS.forEach { (no, name) ->
                            DropdownMenuItem(onClick = { plu = plu.copy(label1Format = no); labelExpanded = false }) {
                                Text(name)
                            }
                        }
                    }
                }

                // ---- 校验错误提示 ----
                if (errors.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        errors.forEach {
                            Text(it, fontSize = 11.sp, color = Color(0xFFB00020))
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

/** TOP2000 的成分/特殊信息为多行文本，换行即一条秤端文本行（默认字库 23）。 */
private const val DEFAULT_TEXT_FONT_NO = 23

private fun linesToText(lines: List<PluTextLine>): String = lines.joinToString("\n") { it.text }

private fun textToLines(text: String): List<PluTextLine> = text
    .split("\n")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { PluTextLine(fontNo = DEFAULT_TEXT_FONT_NO, text = it) }
