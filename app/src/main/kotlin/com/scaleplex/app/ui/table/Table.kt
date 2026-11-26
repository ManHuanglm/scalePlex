package com.scaleplex.app.ui.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 默认列宽下限，拖拽不得小于该值。 */
val MIN_COLUMN_WIDTH: Dp = 40.dp

/**
 * 表格列宽状态：列宽可拖拽调整，表头与数据行共享同一 [ScrollState]
 * 实现横向同步滚动。外层容器负责横向滚动，本类只持有列宽。
 */
@Stable
class TableState internal constructor(initialWidths: List<Dp>) {
    /** 表头与数据行共享的横向滚动状态（由 [ScrollableTable] 挂载）。 */
    val scroll = ScrollState(0)

    val widths = mutableStateListOf(*initialWidths.toTypedArray())

    val totalWidth: Dp get() = widths.fold(0.dp) { acc, w -> acc + w }

    fun resize(index: Int, delta: Dp, minWidth: Dp = MIN_COLUMN_WIDTH) {
        val current = widths.getOrNull(index) ?: return
        widths[index] = (current + delta).coerceAtLeast(minWidth)
    }
}

@Composable
fun rememberTableState(initialWidths: List<Dp>): TableState =
    remember(initialWidths) { TableState(initialWidths) }

/**
 * 表头行：每列右缘叠加拖拽手柄（不额外占宽），水平拖动调整列宽。
 * [cell] 按列序号渲染单元格内容；行高由内容决定（IntrinsicSize.Min）。
 */
@Composable
fun TableHeader(
    state: TableState,
    modifier: Modifier = Modifier,
    minWidth: Dp = MIN_COLUMN_WIDTH,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    Row(modifier = modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        state.widths.forEachIndexed { index, width ->
            Box(modifier = Modifier.width(width)) {
                cell(index, width)
                ResizeHandle(
                    state = state,
                    index = index,
                    minWidth = minWidth,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        state.widths.forEachIndexed { index, width ->
            Box(modifier = Modifier.width(width)) { cell(index, width) }
        }
    }
}

/**
 * 数据行悬停状态：供桌面端表格行高亮使用。
 * 返回交互源（挂 [hoverable]）与当前是否悬停。
 */
@Composable
fun rememberRowHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

/**
 * 可滚动表格容器：表头 + 数据行共享横向滚动，
 * 右侧纵向滚动条与底部横向滚动条保证窄窗口下内容全部可达。
 */
@Composable
fun ScrollableTable(
    table: TableState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: @Composable () -> Unit,
    rows: LazyListScope.() -> Unit,
) {
    var headerHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 表头底色铺满视口宽度（横向滚动时也不错位）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { headerHeightPx.toDp() })
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                )
                Column(modifier = Modifier.fillMaxSize().horizontalScroll(table.scroll)) {
                    Box(modifier = Modifier.onSizeChanged { headerHeightPx = it.height }) {
                        header()
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(table.totalWidth).weight(1f),
                        content = rows,
                    )
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = with(density) { headerHeightPx.toDp() })
                        .fillMaxHeight(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(table.scroll),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResizeHandle(state: TableState, index: Int, minWidth: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
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