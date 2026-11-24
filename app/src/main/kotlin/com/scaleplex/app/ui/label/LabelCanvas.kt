package com.scaleplex.app.ui.label

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.i18n.tr

/** 网格间距：5mm。 */
private const val GRID_STEP_MM = 5

/** 标尺主刻度间距：5mm（主刻度带数字），小刻度 1mm。 */
private const val RULER_MAJOR_MM = 5

/** 方向键微调步长（mm），按住 Shift 为 5mm。 */
private const val NUDGE_MM = 1f
private const val NUDGE_FAST_MM = 5f

private val RULER_COLOR = Color(0xFF555555)
private val RULER_TEXT_COLOR = Color(0xFF666666)

/**
 * 标签毫米坐标画布：对齐 DIGI Label Format 2005 —— 纸张带顶部/左侧 mm 标尺（1mm 小刻度、
 * 5mm 数字主刻度），编辑模式显示 5mm 网格，元素可点击选中、拖拽移动、方向键微调；
 * [preview] 模式为纯打印效果渲染（无标尺/网格/边框/交互）。
 * 拖拽增量以毫米回传给调用方，由编辑器统一更新状态（单向数据流）。
 */
@Composable
fun LabelCanvas(
    template: LabelTemplate,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onMove: (index: Int, dxMm: Float, dyMm: Float) -> Unit,
    modifier: Modifier = Modifier,
    oneToOne: Boolean = false,
    preview: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Dialog 首帧可能以 0 尺寸测量，pxPerMm 必须收敛为正数，否则 .size(负数) 触发约束崩溃
        val pxPerMm = if (oneToOne) 8f else {
            val fit = minOf(
                (maxWidth.value - 20f) / template.widthMm.coerceAtLeast(1),
                (maxHeight.value - 20f) / template.heightMm.coerceAtLeast(1),
            )
            fit.coerceAtLeast(1f)
        }
        val paperWidth = (template.widthMm * pxPerMm).dp.coerceAtLeast(1.dp)
        val paperHeight = (template.heightMm * pxPerMm).dp.coerceAtLeast(1.dp)
        Column(horizontalAlignment = Alignment.Start) {
            if (!preview) {
                TopRuler(template.widthMm, pxPerMm)
            }
            Row {
                if (!preview) {
                    LeftRuler(template.heightMm, pxPerMm)
                }
                Surface(
                    modifier = Modifier
                        .size(paperWidth, paperHeight)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            handleNudge(event, selectedIndex, onMove)
                        }
                        .clickable(enabled = !preview) { onSelect(null) },
                    color = Color.White,
                    border = BorderStroke(1.dp, if (preview) Color(0xFFB0B0B0) else Color(0xFF808080)),
                ) {
                    Box {
                        if (!preview) {
                            GridLayer(template, pxPerMm)
                        }
                        template.elements.forEachIndexed { index, element ->
                            ElementView(
                                element = element,
                                pxPerMm = pxPerMm,
                                selected = !preview && index == selectedIndex,
                                interactive = !preview,
                                onSelect = { onSelect(index); focusRequester.requestFocus() },
                                onDrag = { dx, dy -> onMove(index, dx, dy) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 方向键微调选中元素；返回 true 表示事件已消费。 */
private fun handleNudge(
    event: androidx.compose.ui.input.key.KeyEvent,
    selectedIndex: Int?,
    onMove: (Int, Float, Float) -> Unit,
): Boolean {
    if (selectedIndex == null || event.type != KeyEventType.KeyDown) return false
    val step = if (event.isShiftPressed) NUDGE_FAST_MM else NUDGE_MM
    return when (event.key) {
        Key.DirectionLeft -> { onMove(selectedIndex, -step, 0f); true }
        Key.DirectionRight -> { onMove(selectedIndex, step, 0f); true }
        Key.DirectionUp -> { onMove(selectedIndex, 0f, -step); true }
        Key.DirectionDown -> { onMove(selectedIndex, 0f, step); true }
        else -> false
    }
}

/** 顶部 mm 标尺：1mm 小刻度，5mm 主刻度带数字。 */
@Composable
private fun TopRuler(widthMm: Int, pxPerMm: Float) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.width((widthMm * pxPerMm).dp.coerceAtLeast(1.dp)).height(18.dp)) {
        var mm = 0
        while (mm <= widthMm) {
            val x = mm * pxPerMm
            val major = mm % RULER_MAJOR_MM == 0
            drawLine(
                RULER_COLOR,
                Offset(x, if (major) 0f else size.height * 0.55f),
                Offset(x, size.height),
                1f,
            )
            // 文本需在画布内完整绘制（drawText 对负剩余宽度直接抛约束异常）
            if (major && mm > 0 && x + 14f < size.width) {
                drawText(
                    measurer, mm.toString(),
                    topLeft = Offset(x + 2f, 1f),
                    style = TextStyle(fontSize = 8.sp, color = RULER_TEXT_COLOR),
                )
            }
            mm++
        }
    }
}

/** 左侧 mm 标尺。 */
@Composable
private fun LeftRuler(heightMm: Int, pxPerMm: Float) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.width(18.dp).height((heightMm * pxPerMm).dp.coerceAtLeast(1.dp))) {
        var mm = 0
        while (mm <= heightMm) {
            val y = mm * pxPerMm
            val major = mm % RULER_MAJOR_MM == 0
            drawLine(
                RULER_COLOR,
                Offset(if (major) 0f else size.width * 0.55f, y),
                Offset(size.width, y),
                1f,
            )
            if (major && mm > 0 && y + 12f < size.height) {
                drawText(
                    measurer, mm.toString(),
                    topLeft = Offset(1f, y + 2f),
                    style = TextStyle(fontSize = 8.sp, color = RULER_TEXT_COLOR),
                )
            }
            mm++
        }
    }
}

/** 5mm 浅灰网格层。 */
@Composable
private fun GridLayer(template: LabelTemplate, pxPerMm: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = GRID_STEP_MM * pxPerMm
        val gridColor = Color(0xFFE3E3E3)
        var x = step
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}

@Composable
private fun ElementView(
    element: LabelElement,
    pxPerMm: Float,
    selected: Boolean,
    interactive: Boolean,
    onSelect: () -> Unit,
    onDrag: (dxMm: Float, dyMm: Float) -> Unit,
) {
    val borderColor = if (selected) Color(0xFF1E88E5) else Color(0xFFC0C0C0)
    var base = Modifier
        .offset((element.xMm * pxPerMm).dp, (element.yMm * pxPerMm).dp)
        .size((element.widthMm * pxPerMm).dp.coerceAtLeast(4.dp), (element.heightMm * pxPerMm).dp.coerceAtLeast(4.dp))
    base = when {
        interactive && selected -> base
            .background(Color(0x1A1E88E5))
            .border(2.dp, borderColor)
            .clickable { onSelect() }
            .pointerInput(element.kind, element.text) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x / pxPerMm, dragAmount.y / pxPerMm)
                }
            }
        interactive -> base.border(1.dp, borderColor).clickable { onSelect() }
        else -> base
    }
    Box(modifier = base, contentAlignment = Alignment.Center) {
        if (element.kind == LabelElementKind.FRAME) {
            // 外框：仅框线，无文本（线宽即边框）
            Box(modifier = Modifier.fillMaxSize().border(1.dp, if (interactive) borderColor else Color.Black))
        } else {
            Text(
                text = displayText(element),
                fontSize = maxOf(element.fontSizePt * 0.9f, 8f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = if (interactive) MaterialTheme.colors.onSurface else Color.Black,
            )
        }
    }
}

/** 画布上的元素占位显示文本（非打印内容，仅辅助识别，对齐 Label Format 2005 数据域）。 */
private fun displayText(element: LabelElement): String = when (element.kind) {
    LabelElementKind.TEXT, LabelElementKind.PLU_NAME, LabelElementKind.SHOP_NAME ->
        element.text.ifBlank { tr("label.kind." + element.kind.name) }
    LabelElementKind.BARCODE -> "|||||  " + element.text
    LabelElementKind.QRCODE -> "▣ " + element.text
    LabelElementKind.WEIGHT -> "W kg"
    LabelElementKind.UNIT_PRICE -> "¥/kg"
    LabelElementKind.TOTAL_PRICE -> "¥¥"
    LabelElementKind.TAXED_PRICE -> "¥(税)"
    LabelElementKind.TAX -> "5%"
    LabelElementKind.PLU_NO -> "0001"
    LabelElementKind.QUANTITY -> "×1"
    LabelElementKind.QUANTITY_SYMBOL -> "kg"
    LabelElementKind.INGREDIENT, LabelElementKind.SPECIAL_MSG,
    LabelElementKind.SECOND_NAME, LabelElementKind.ADVERTISEMENT,
    LabelElementKind.THIRD_NAME, LabelElementKind.REMARK_1, LabelElementKind.REMARK_2,
    LabelElementKind.REMARK_3, LabelElementKind.REMARK_4,
    LabelElementKind.TEXT_TITLE, LabelElementKind.TEXT_CONTENT,
    -> element.text.ifBlank { tr("label.kind." + element.kind.name) }
    LabelElementKind.FIXED_WEIGHT -> "1.000 kg"
    LabelElementKind.TARE_WEIGHT -> "T 0.005"
    LabelElementKind.TRACE_CODE -> "||| TRACE"
    LabelElementKind.GROSS_WEIGHT -> "G 1.000 kg"
    LabelElementKind.NET_WEIGHT -> "N 0.995 kg"
    LabelElementKind.PRODUCTION_DATE -> "2026-01-01"
    LabelElementKind.SHELF_LIFE -> "7D"
    LabelElementKind.PREPACK_DISCOUNT -> "-10%"
    LabelElementKind.DISCOUNT -> "-5%"
    LabelElementKind.TEMPERATURE -> "4°C"
    LabelElementKind.SCALE_NO -> "001"
    LabelElementKind.SELLER -> "01"
    LabelElementKind.AVERAGE_PRICE -> "AVG ¥"
    LabelElementKind.AVERAGE_WEIGHT -> "AVG kg"
    LabelElementKind.BONUS_POINT -> "100 PTS"
    LabelElementKind.ORIGIN -> "CN"
    LabelElementKind.MULTI_BARCODE -> "||||| ×2"
    LabelElementKind.MAIN_GROUP -> "997"
    LabelElementKind.DEPARTMENT -> "01"
    LabelElementKind.DATE -> element.text.ifBlank { "2026-01-01" }
    LabelElementKind.LOGO -> tr("label.kind.LOGO")
    LabelElementKind.FRAME -> ""
}
