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
import androidx.compose.material.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** 常用标签纸张预设（mm）。 */
private val SIZE_PRESETS: List<Pair<String, Pair<Int, Int>>> = listOf(
    "40×30" to Pair(40, 30),
    "50×30" to Pair(50, 30),
    "58×40" to Pair(58, 40),
    "60×40" to Pair(60, 40),
    "80×50" to Pair(80, 50),
)

/** 属性面板可选字体（秤端字库映射待联调校准）。 */
private val FONT_OPTIONS = listOf("宋体", "黑体", "楷体", "微软雅黑", "Arial")

/** 元素添加时的默认几何尺寸（毫米）与占位文本。 */
private fun defaultElement(kind: LabelElementKind, index: Int): LabelElement = LabelElement(
    kind = kind,
    xMm = 4f,
    yMm = 4f + (index % 6) * 5f,
    widthMm = 24f,
    heightMm = 6f,
    text = when (kind) {
        LabelElementKind.BARCODE -> "69012345"
        else -> ""
    },
)

/**
 * 标签模板可视化编辑器：左侧元素面板、中间毫米画布（网格+方向键微调）、右侧属性面板。
 * 支持：预设纸张尺寸、保存/另存为、吸附网格、1:1 切换、效果预览、元素复制/置顶/置底/居中、
 * 字体字号对齐旋转、JSON 导入导出、下发到秤（全部设备并发）。
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
    var snap by remember { mutableStateOf(true) }
    var showPreview by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<Map<Long, BatchResult>?>(null) }
    val scope = rememberCoroutineScope()

    fun updateElements(transform: (List<LabelElement>) -> List<LabelElement>) {
        template = template.copy(elements = transform(template.elements))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.width(920.dp).height(660.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ---- 工具栏 ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = template.name, onValueChange = { template = template.copy(name = it) },
                        label = { Text(tr("label.name")) }, modifier = Modifier.width(150.dp),
                    )
                    // 预设纸张尺寸
                    var presetExpanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { presetExpanded = true }) {
                        Text("${template.widthMm}×${template.heightMm} ▾")
                    }
                    DropdownMenu(presetExpanded, { presetExpanded = false }) {
                        SIZE_PRESETS.forEach { (label, size) ->
                            DropdownMenuItem(onClick = {
                                template = template.copy(widthMm = size.first, heightMm = size.second)
                                presetExpanded = false
                            }) { Text(label) }
                        }
                    }
                    OutlinedTextField(
                        value = template.widthMm.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { template = template.copy(widthMm = it.coerceIn(10, 120)) } },
                        label = { Text(tr("label.width")) }, modifier = Modifier.width(80.dp),
                    )
                    OutlinedTextField(
                        value = template.heightMm.toString(),
                        onValueChange = { s -> s.toIntOrNull()?.let { template = template.copy(heightMm = it.coerceIn(10, 120)) } },
                        label = { Text(tr("label.height")) }, modifier = Modifier.width(80.dp),
                    )
                    OutlinedButton(onClick = { snap = !snap }) {
                        Text(if (snap) "[${tr("label.snap")}]" else tr("label.snap"))
                    }
                    Button(onClick = { oneToOne = !oneToOne }) {
                        Text(if (oneToOne) "[1:1]" else "1:1")
                    }
                    Spacer(Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        val saved = template.copy(id = template.id)
                        template = saved.copy(id = state.labelService.save(saved))
                        onSaved(template)
                    }) { Text(tr("label.save")) }
                    // 另存为：以新模板入库并切换编辑目标
                    Button(onClick = {
                        val copy = template.copy(id = 0, name = template.name + "-" + tr("label.copySuffix"))
                        template = copy.copy(id = state.labelService.save(copy))
                        onSaved(template)
                    }) { Text(tr("label.saveAs")) }
                    OutlinedButton(onClick = { showPreview = true }) { Text(tr("label.preview")) }
                    OutlinedButton(onClick = { exportJson(template) }) { Text(tr("label.exportJson")) }
                    OutlinedButton(onClick = {
                        importJson()?.let { imported -> template = imported; selected = null }
                    }) { Text(tr("label.importJson")) }
                    Button(onClick = {
                        scope.launch {
                            batchResults = state.scaleService.downloadLabelToDevices(
                                state.scaleService.devices().filter { it.enabled }, template,
                            )
                        }
                    }) { Text(tr("label.download")) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss) { Text(tr("common.close")) }
                }
                Divider()

                // ---- 三栏主体 ----
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteColumn(
                        onAdd = { kind ->
                            updateElements { it + defaultElement(kind, it.size) }
                            selected = template.elements.size - 1
                        },
                    )
                    LabelCanvas(
                        template = template,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        onMove = { index, dx, dy ->
                            moveElement(template, index, dx, dy, snap)?.let { template = it }
                        },
                        oneToOne = oneToOne,
                        modifier = Modifier.weight(1f),
                    )
                    PropertyPanel(
                        elements = template.elements,
                        selected = selected,
                        onUpdate = { index, element ->
                            updateElements { list -> list.toMutableList().apply { set(index, element) } }
                        },
                        onDelete = {
                            selected?.let { i ->
                                updateElements { list -> list.filterIndexed { idx, _ -> idx != i } }
                                selected = null
                            }
                        },
                        onDuplicate = {
                            selected?.let { i ->
                                val src = template.elements.getOrNull(i) ?: return@let
                                updateElements { list ->
                                    list + src.copy(xMm = src.xMm + 3f, yMm = src.yMm + 3f)
                                }
                                selected = template.elements.size - 1
                            }
                        },
                        onOrder = { toFront ->
                            selected?.let { i ->
                                updateElements { list ->
                                    val el = list.getOrNull(i) ?: return@updateElements list
                                    val rest = list.filterIndexed { idx, _ -> idx != i }
                                    if (toFront) rest + el else listOf(el) + rest
                                }
                                selected = if (toFront) template.elements.size - 1 else 0
                            }
                        },
                        onCenter = { horizontal ->
                            selected?.let { i ->
                                val el = template.elements.getOrNull(i) ?: return@let
                                val centered = if (horizontal) {
                                    el.copy(xMm = ((template.widthMm - el.widthMm) / 2).coerceAtLeast(0f))
                                } else {
                                    el.copy(yMm = ((template.heightMm - el.heightMm) / 2).coerceAtLeast(0f))
                                }
                                updateElements { list -> list.toMutableList().apply { set(i, centered) } }
                            }
                        },
                    )
                }

                Text(tr("label.selectHint"), fontSize = 11.sp, color = Color(0xFF808080))
            }
        }
    }

    // ---- 1:1 效果预览 ----
    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.width(640.dp).height(540.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${template.name} · ${template.widthMm}×${template.heightMm} mm", fontSize = 14.sp)
                    LabelCanvas(
                        template = template,
                        selectedIndex = null,
                        onSelect = {},
                        onMove = { _, _, _ -> },
                        preview = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { showPreview = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(tr("common.close"))
                    }
                }
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
    onDuplicate: () -> Unit,
    onOrder: (toFront: Boolean) -> Unit,
    onCenter: (horizontal: Boolean) -> Unit,
) {
    val element = selected?.let { elements.getOrNull(it) }
    Column(modifier = Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
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
        // 字体
        var fontExpanded by remember { mutableStateOf(false) }
        OutlinedTextField(element.fontName, {}, readOnly = true, label = { Text(tr("label.font")) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { fontExpanded = true }) { Text("▾") } })
        DropdownMenu(fontExpanded, { fontExpanded = false }) {
            FONT_OPTIONS.forEach { font ->
                DropdownMenuItem(onClick = { onUpdate(index, element.copy(fontName = font)); fontExpanded = false }) {
                    Text(font)
                }
            }
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

        // 排版快捷操作
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallAction(tr("label.centerH"), Modifier.weight(1f)) { onCenter(true) }
            SmallAction(tr("label.centerV"), Modifier.weight(1f)) { onCenter(false) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallAction(tr("label.dup"), Modifier.weight(1f)) { onDuplicate() }
            SmallAction(tr("label.front"), Modifier.weight(1f)) { onOrder(true) }
            SmallAction(tr("label.back"), Modifier.weight(1f)) { onOrder(false) }
        }
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(tr("label.deleteElement")) }
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        Text(label, fontSize = 11.sp)
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

/** 拖拽移动；[snap] 开启时坐标吸附到 1mm 网格。 */
private fun moveElement(template: LabelTemplate, index: Int, dxMm: Float, dyMm: Float, snap: Boolean): LabelTemplate? {
    val element = template.elements.getOrNull(index) ?: return null
    var x = (element.xMm + dxMm).coerceAtLeast(0f)
    var y = (element.yMm + dyMm).coerceAtLeast(0f)
    if (snap) {
        x = kotlin.math.round(x)
        y = kotlin.math.round(y)
    }
    val moved = element.copy(xMm = x, yMm = y)
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
