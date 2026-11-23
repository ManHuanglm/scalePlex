package com.scaleplex.app.ui.table

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 默认列宽下限，拖拽不得小于该值。 */
val MIN_COLUMN_WIDTH: Dp = 40.dp

/**
 * 表格列宽状态：列宽可拖拽调整，表头与数据行共享同一 [androidx.compose.foundation.ScrollState]
 * 实现横向同步滚动。外层容器负责横向滚动，本类只持有列宽。
 */
@Stable
class TableState internal constructor(initialWidths: List<Dp>) {
    /** 表头与数据行共享的横向滚动状态（外层容器挂 horizontalScroll）。 */
    val scroll = ScrollState(0)

    val widths = mutableStateListOf(*initialWidths.toTypedArray())

    val totalWidth: Dp get() = widths.reduce { acc, w -> acc + w }

    fun resize(index: Int, delta: Dp, minWidth: Dp = MIN_COLUMN_WIDTH) {
        val current = widths.getOrNull(index) ?: return
        widths[index] = (current + delta).coerceAtLeast(minWidth)
    }
}

@Composable
fun rememberTableState(initialWidths: List<Dp>): TableState =
    remember(initialWidths) { TableState(initialWidths) }

/**
 * 表头行：每列右缘为拖拽手柄，水平拖动调整列宽。
 * [cell] 按列序号渲染单元格内容；行高由内容决定（IntrinsicSize.Min）。
 */
@Composable
fun TableHeader(
    state: TableState,
    modifier: Modifier = Modifier,
    minWidth: Dp = MIN_COLUMN_WIDTH,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        state.widths.forEachIndexed { index, width ->
            Box(modifier = Modifier.width(width)) { cell(index, width) }
            ResizeHandle(state = state, index = index, minWidth = minWidth)
        }
    }
}

/** 数据行：按 [TableState] 列宽渲染，与表头对齐（无拖拽手柄）。 */
@Composable
fun TableRow(
    state: TableState,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    Row(modifier = modifier) {
        state.widths.forEachIndexed { index, width ->
            Box(modifier = Modifier.width(width)) { cell(index, width) }
        }
    }
}

@Composable
private fun ResizeHandle(state: TableState, index: Int, minWidth: Dp) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerInput(index) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    state.resize(index, with(density) { dragAmount.toDp() }, minWidth)
                }
            },
    )
}

/** 单元格通用内边距。 */
@Composable
fun Modifier.cellPadding(): Modifier = padding(horizontal = 8.dp, vertical = 6.dp)
