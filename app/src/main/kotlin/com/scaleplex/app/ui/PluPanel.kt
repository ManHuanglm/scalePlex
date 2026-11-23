package com.scaleplex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.util.Numbers
import kotlinx.coroutines.launch

/**
 * PLU 管理面板：列表列对齐 TOP2000（部门号/PLU编号/项目代码/商品名称/单价/F1F2/保质期/计重/成分/特殊信息）。
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

        // header（列顺序对齐 TOP2000）
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text(tr("plu.field.group"), Modifier.width(60.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.no"), Modifier.width(70.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.code"), Modifier.width(120.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.name"), Modifier.weight(1.2f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.price"), Modifier.width(80.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.flag"), Modifier.width(50.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.shelf"), Modifier.width(60.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.type"), Modifier.width(60.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.ingredient"), Modifier.width(100.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(tr("plu.field.specialMsg"), Modifier.width(100.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(plus, key = { it.pluNo }) { p ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editing = p; showEditor = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.mainGroup.toString(), Modifier.width(60.dp), fontSize = 12.sp)
                    Text(p.pluNo.toString(), Modifier.width(70.dp), fontSize = 12.sp)
                    Text(p.eanCode, Modifier.width(120.dp), fontSize = 12.sp)
                    Text(p.displayName, Modifier.weight(1.2f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(Numbers.minorToDisplay(p.unitPrice, state.config.priceDecimals), Modifier.width(80.dp), fontSize = 12.sp)
                    Text(p.flagData, Modifier.width(50.dp), fontSize = 12.sp)
                    Text(p.usedByDate.toString(), Modifier.width(60.dp), fontSize = 12.sp)
                    Text(if (p.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh"), Modifier.width(60.dp), fontSize = 12.sp)
                    Text(firstLine(p.ingredientLines), Modifier.width(100.dp), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(firstLine(p.specialMsgLines), Modifier.width(100.dp), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Divider()
            }
        }
    }

    if (showEditor && editing != null) {
        PluEditorDialog(state, editing!!, onDismiss = { showEditor = false }) {
            state.pluService.save(it)
            refresh.value++
            showEditor = false
        }
    }
}

/** 多行文本取首行做列表摘要，无内容显示 "-"。 */
private fun firstLine(lines: List<com.scaleplex.core.model.PluTextLine>): String =
    lines.firstOrNull()?.text?.take(10)?.plus(if (lines.size > 1 || (lines.firstOrNull()?.text?.length ?: 0) > 10) "…" else "")
        ?: "-"
