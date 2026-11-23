package com.scaleplex.app.ui.label

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.BatchResultDialog
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.LabelTemplateJson
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

private val PALETTE_KINDS = listOf(
    LabelElementKind.TEXT,
    LabelElementKind.PLU_NAME,
    LabelElementKind.SHOP_NAME,
    LabelElementKind.BARCODE,
    LabelElementKind.QRCODE,
    LabelElementKind.WEIGHT,
    LabelElementKind.UNIT_PRICE,
    LabelElementKind.TOTAL_PRICE,
    LabelElementKind.DATE,
)

private val ROTATION_OPTIONS = listOf(0, 90, 180, 270)

/** 元素添加时的默认几何尺寸（毫米）与占位文本。 */
private fun defaultElement(kind: LabelElementKind, index: Int): LabelElement = LabelElement(
    kind = kind,
    xMm = 4f,
    yMm = 4f + (index % 6) * 5f,
    widthMm = 24f,
    heightMm = 6f,
    text = when (kind) {
        LabelElementKind.TEXT -> ""
        LabelElementKind.PLU_NAME, LabelElementKind.SHOP_NAME -> ""
        LabelElementKind.BARCODE -> "69012345"
        else -> ""
    },
)

/**
 * 标签模板可视化编辑器：左侧元素面板、中间毫米画布、右侧属性面板。
 * 支持保存/另存、JSON 导入导出、下发到秤（全部设备并发）。
 */
@Composable
fun LabelEditorDialog(
    state: AppState,
    initial: LabelTemplate,
    onDismiss: () -> Unit,
    onSaved: (LabelTemplate) -> Unit,
) {
    var template by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var oneToOne by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, BatchResult>?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.width(920.dp).height(660.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ---- 工具栏 ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = template.name, onValueChange = { template = template.copy(name = it) },
                        label = { Text(tr("label.name")) }, modifier = Modifier.width(180.dp),
                    )
                    OutlinedTextField(
                        value = template.widthMm.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { template = template.copy(widthMm = it.coerceIn(10, 120)) } },
                        label = { Text(tr("label.width")) }, modifier = Modifier.width(90.dp),
                    )
                    OutlinedTextField(
                        value = template.heightMm.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { template = template.copy(heightMm = it.coerceIn(10, 120)) } },
                        label = { Text(tr("label.height")) }, modifier = Modifier.width(90.dp),
                    )
                    Button(onClick = { oneToOne = !oneToOne }) {
                        Text(if (oneToOne) "[1:1]" else "1:1")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val saved = template.copy(id = template.id)
                        template = saved.copy(id = state.labelService.save(saved))
                        onSaved(template)
                    }) { Text(tr("label.save")) }
                    Button(onClick = { exportJson(template) }) { Text(tr("label.exportJson")) }
                    Button(onClick = {
                        importJson()?.let { imported -> template = imported; selected = null }
                    }) { Text(tr("label.importJson")) }
                    Button(onClick = {
                        scope.launch {
                            batchResults = state.scaleService.downloadLabelToDevices(
                                state.scaleService.devices().filter { it.enabled }, template,
                            )
                        }
                    }) { Text(tr("label.download")) }
                    Button(onClick = onDismiss) { Text(tr("common.close")) }
                }
                Divider()

                // ---- 三栏主体 ----
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteColumn(
                        onAdd = { kind ->
                            template = template.copy(elements = template.elements + defaultElement(kind, template.elements.size))
                            selected = template.elements.size - 1
                        },
                    )
                    LabelCanvas(
                        template = template,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        onMove = { index, dx, dy ->
                            moveElement(template, index, dx, dy)?.let { template = it }
                        },
                        oneToOne = oneToOne,
                        modifier = Modifier.weight(1f),
                    )
                    PropertyPanel(
                        elements = template.elements,
                        selected = selected,
                        onUpdate = { index, element ->
                            template = template.copy(
                                elements = template.elements.toMutableList().apply { set(index, element) },
                            )
                        },
                        onDelete = {
                            selected?.let { i ->
                                template = template.copy(elements = template.elements.filterIndexed { idx, _ -> idx != i })
                                selected = null
                            }
                        },
                    )
                }

                Text(tr("label.selectHint"), fontSize = 11.sp, color = Color(0xFF808080))
            }
        }
    }

    batchResults?.let { results ->
        BatchResultDialog(state, results, onDismiss = { batchResults = null })
    }
}

@Composable
private fun PaletteColumn(onAdd: (LabelElementKind) -> Unit) {
    Column(modifier = Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
        Text(tr("label.palette"), fontSize = 14.sp)
        PALETTE_KINDS.forEach { kind ->
            Button(onClick = { onAdd(kind) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(tr("label.kind." + kind.name))
            }
        }
    }
}

@Composable
private fun PropertyPanel(
    elements: List<LabelElement>,
    selected: Int?,
    onUpdate: (Int, LabelElement) -> Unit,
    onDelete: () -> Unit,
) {
    val element = selected?.let { elements.getOrNull(it) }
    Column(modifier = Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
        Text(tr("label.properties"), fontSize = 14.sp)
        if (element == null) {
            Text(tr("label.selectHint"), fontSize = 11.sp, color = Color(0xFF808080))
            return@Column
        }
        val index = selected!!
        OutlinedTextField(
            value = element.text, onValueChange = { onUpdate(index, element.copy(text = it)) },
            label = { Text(tr("label.text")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField(tr("label.x"), element.xMm, Modifier.weight(1f)) { v -> onUpdate(index, element.copy(xMm = v)) }
            NumField(tr("label.y"), element.yMm, Modifier.weight(1f)) { v -> onUpdate(index, element.copy(yMm = v)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumField("W", element.widthMm, Modifier.weight(1f)) { v -> onUpdate(index, element.copy(widthMm = v)) }
            NumField("H", element.heightMm, Modifier.weight(1f)) { v -> onUpdate(index, element.copy(heightMm = v)) }
        }
        NumField(tr("label.fontSize"), element.fontSizePt.toFloat(), Modifier.fillMaxWidth()) { v ->
            onUpdate(index, element.copy(fontSizePt = v.toInt()))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("label.align"), Modifier.width(60.dp))
            listOf(0, 1, 2).forEach { a ->
                TextButton(onClick = { onUpdate(index, element.copy(align = a)) }) {
                    Text(if (element.align == a) "[$a]" else a.toString())
                }
            }
        }
        var rotExpanded by remember { mutableStateOf(false) }
        OutlinedTextField(element.rotation.toString(), {}, readOnly = true, label = { Text(tr("label.rotation")) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { rotExpanded = true }) { Text("▾") } })
        DropdownMenu(rotExpanded, { rotExpanded = false }) {
            ROTATION_OPTIONS.forEach { r ->
                DropdownMenuItem(onClick = { onUpdate(index, element.copy(rotation = r)); rotExpanded = false }) {
                    Text(r.toString())
                }
            }
        }
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("label.deleteElement")) }
    }
}

@Composable
private fun NumField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    OutlinedTextField(
        value = trimNum(value),
        onValueChange = { s -> s.toFloatOrNull()?.let(onChange) },
        label = { Text(label) },
        modifier = modifier,
    )
}

private fun trimNum(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

private fun moveElement(template: LabelTemplate, index: Int, dxMm: Float, dyMm: Float): LabelTemplate? {
    val element = template.elements.getOrNull(index) ?: return null
    val moved = element.copy(
        xMm = (element.xMm + dxMm).coerceAtLeast(0f),
        yMm = (element.yMm + dyMm).coerceAtLeast(0f),
    )
    return template.copy(elements = template.elements.toMutableList().apply { set(index, moved) })
}

private fun exportJson(template: LabelTemplate) {
    val chooser = JFileChooser()
    chooser.selectedFile = File("${template.name}.label.json")
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        val path = chooser.selectedFile.path
        val target = File(if (path.endsWith(".json")) path else "$path.label.json")
        target.writeText(LabelTemplateJson.writeTemplate(template), Charsets.UTF_8)
    }
}

private fun importJson(): LabelTemplate? {
    val chooser = JFileChooser()
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        return runCatching {
            LabelTemplateJson.readTemplate(chooser.selectedFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }
    return null
}
