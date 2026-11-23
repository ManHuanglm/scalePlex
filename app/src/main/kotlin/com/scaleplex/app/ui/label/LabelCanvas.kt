package com.scaleplex.app.ui.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.i18n.tr

/**
 * 标签毫米坐标画布：纸张按可用区域 fit 缩放或 1:1 显示，元素可点击选中、拖拽移动。
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
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val pxPerMm = if (oneToOne) 8f else minOf(
            maxWidth.value / template.widthMm.coerceAtLeast(1),
            maxHeight.value / template.heightMm.coerceAtLeast(1),
        )
        Surface(
            modifier = Modifier
                .size((template.widthMm * pxPerMm).dp, (template.heightMm * pxPerMm).dp)
                .clickable { onSelect(null) },
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB0B0B0)),
        ) {
            Box {
                template.elements.forEachIndexed { index, element ->
                    ElementView(
                        element = element,
                        pxPerMm = pxPerMm,
                        selected = index == selectedIndex,
                        onSelect = { onSelect(index) },
                        onDrag = { dx, dy -> onMove(index, dx, dy) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ElementView(
    element: LabelElement,
    pxPerMm: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onDrag: (dxMm: Float, dyMm: Float) -> Unit,
) {
    val borderColor = if (selected) Color(0xFF1E88E5) else Color(0xFFC0C0C0)
    Box(
        modifier = Modifier
            .offset((element.xMm * pxPerMm).dp, (element.yMm * pxPerMm).dp)
            .size((element.widthMm * pxPerMm).dp.coerceAtLeast(4.dp), (element.heightMm * pxPerMm).dp.coerceAtLeast(4.dp))
            .background(if (selected) Color(0x1A1E88E5) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, borderColor)
            .clickable { onSelect() }
            .pointerInput(element.kind, element.text) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x / pxPerMm, dragAmount.y / pxPerMm)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText(element),
            fontSize = maxOf(element.fontSizePt * 0.9f, 8f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

/** 画布上的元素占位显示文本（非打印内容，仅辅助识别）。 */
private fun displayText(element: LabelElement): String = when (element.kind) {
    LabelElementKind.TEXT, LabelElementKind.PLU_NAME, LabelElementKind.SHOP_NAME ->
        element.text.ifBlank { tr("label.kind." + element.kind.name) }
    LabelElementKind.BARCODE -> "|||||  " + element.text
    LabelElementKind.QRCODE -> "▣ " + element.text
    LabelElementKind.WEIGHT -> "W kg"
    LabelElementKind.UNIT_PRICE -> "¥/kg"
    LabelElementKind.TOTAL_PRICE -> "¥¥"
    LabelElementKind.DATE -> element.text.ifBlank { "2026-01-01" }
    LabelElementKind.LOGO -> tr("label.kind.LOGO")
}
