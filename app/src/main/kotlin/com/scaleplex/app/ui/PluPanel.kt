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
import com.scaleplex.core.util.Numbers
import kotlinx.coroutines.launch

/**
 * PLU 管理面板：列对齐 TOP2000（部门号/PLU编号/项目代码/商品名称/单价/F1F2/保质期/商品类型/成分/特殊信息），
 * 表格列宽可拖拽自由拉伸，横向整体滚动。保存失败在弹窗内提示，不再静默。
 */
@Composable
fun PluPanel(state: AppState, refresh: MutableState<Int>) {
    var keyword by remember { mutableStateOf("") }
    var plus by remember(keyword, refresh.value) {
        mutableStateOf(state.pluService.list(keyword, page = 0, pageSize = 200))
    }
    var editing by remember { mutableStateOf<PluItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val table = rememberTableState(
        listOf(60.dp, 70.dp, 110.dp, 170.dp, 80.dp, 50.dp, 60.dp, 70.dp, 110.dp, 110.dp),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // toolbar
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                label = { Text(tr("plu.search")) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editing = PluItem(pluNo = (state.pluService.count() + 1)); showEditor = true }) {
                Text(tr("plu.add"))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    val devices = state.scaleService.devices()
                    state.scaleService.downloadToDevices(devices, plus)
                }
            }) { Text(tr("plu.download.group")) }
        }

        // table（横向滚动共享：表头与数据行同步，列宽拖拽调整）
        Column(modifier = Modifier.weight(1f).horizontalScroll(table.scroll)) {
            TableHeader(table) { index, width ->
                Text(
                    pluHeaders()[index],
                    Modifier.cellPadding(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Divider()
            LazyColumn(modifier = Modifier.width(table.totalWidth).weight(1f)) {
                items(plus, key = { it.pluNo }) { p ->
                    TableRow(table, modifier = Modifier.clickable { editing = p; showEditor = true }) { index, width ->
                        PluCell(index, p, width, state)
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
}

/** 列标题（顺序对齐 TOP2000）。 */
private fun pluHeaders(): List<String> = listOf(
    tr("plu.field.group"),
    tr("plu.field.no"),
    tr("plu.field.code"),
    tr("plu.field.name"),
    tr("plu.field.price"),
    tr("plu.field.flag"),
    tr("plu.field.shelf"),
    tr("plu.field.type"),
    tr("plu.field.ingredient"),
    tr("plu.field.specialMsg"),
)

@Composable
private fun PluCell(index: Int, p: PluItem, width: androidx.compose.ui.unit.Dp, state: AppState) {
    val text = when (index) {
        0 -> p.mainGroup.toString()
        1 -> p.pluNo.toString()
        2 -> p.eanCode
        3 -> p.displayName
        4 -> Numbers.minorToDisplay(p.unitPrice, state.config.priceDecimals)
        5 -> p.flagData
        6 -> p.usedByDate.toString()
        7 -> if (p.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh")
        8 -> summarize(p.ingredientLines)
        else -> summarize(p.specialMsgLines)
    }
    Text(
        text,
        Modifier.cellPadding().width(width),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 多行文本取首行做列表摘要，无内容显示 "-"。 */
private fun summarize(lines: List<com.scaleplex.core.model.PluTextLine>): String {
    val first = lines.firstOrNull()?.text ?: return "-"
    return if (first.length > 10) first.take(10) + "…" else first
}
