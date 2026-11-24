package com.scaleplex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.app.ui.label.LabelEditorDialog
import com.scaleplex.app.ui.table.ScrollableTable
import com.scaleplex.app.ui.table.TableHeader
import com.scaleplex.app.ui.table.TableRow
import com.scaleplex.app.ui.table.cellPadding
import com.scaleplex.app.ui.table.rememberRowHover
import com.scaleplex.app.ui.table.rememberTableState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.LabelSystem
import com.scaleplex.core.model.LabelTemplate
import kotlinx.coroutines.launch

/**
 * 标签模板面板：模板列表 + 可视化编辑器入口 + 删除 + 从秤回收。
 * 列表使用可滚动表格，窄窗口下内容可横向/纵向滚动查看。
 */
@Composable
fun LabelPanel(state: AppState, refresh: MutableState<Int>) {
    var templates by remember(refresh.value) { mutableStateOf(state.labelService.list()) }
    var editing by remember { mutableStateOf<LabelTemplate?>(null) }
    var recallMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val table = rememberTableState(listOf(250.dp, 150.dp, 130.dp, 90.dp, 160.dp))

    Column(modifier = Modifier.fillMaxSize()) {
        PanelHeader(
            title = tr("tab.label"),
            subtitle = "${tr("common.total")} ${templates.size}${tr("common.count")}",
        ) {
            OutlinedButton(onClick = {
                scope.launch {
                    var saved = 0
                    val errors = mutableListOf<String>()
                    state.scaleService.devices().filter { it.enabled }.forEach { dev ->
                        state.scaleService.driverFor(dev).readLabelFormats().fold(
                            onSuccess = { list ->
                                list.forEach { state.labelService.save(it) }
                                saved += list.size
                            },
                            onFailure = { errors += "${dev.name}: ${it.message}" },
                        )
                    }
                    recallMessage = tr("label.recallDone") + saved +
                        if (errors.isEmpty()) "" else " · " + errors.joinToString("; ").take(120)
                    refresh.value++
                }
            }) { Text(tr("label.recall")) }
            // 新建标签：先选标签体系（通用 / 托利多 VisEdit / bLabel / SmartLabel）
            Box {
                var newExpanded by remember { mutableStateOf(false) }
                Button(onClick = { newExpanded = true }) { Text(tr("label.new")) }
                DropdownMenu(newExpanded, { newExpanded = false }) {
                    LabelSystem.entries.forEach { system ->
                        DropdownMenuItem(onClick = {
                            val seq = templates.count { it.system == system } + 1
                            state.labelService.save(
                                LabelTemplate(
                                    name = tr("label.system." + system.name) + " " + seq,
                                    widthMm = system.defaultWidthMm,
                                    heightMm = system.defaultHeightMm,
                                    system = system,
                                ),
                            )
                            newExpanded = false
                            refresh.value++
                        }) { Text(tr("label.system." + system.name)) }
                    }
                }
            }
        }

        if (recallMessage.isNotBlank()) {
            Text(
                recallMessage,
                fontSize = 12.sp,
                color = UiColors.Error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ScrollableTable(
                table = table,
                modifier = Modifier.fillMaxSize(),
                header = {
                    TableHeader(table) { index, _ ->
                        Text(
                            labelHeaders()[index],
                            Modifier.cellPadding(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                items(templates.size, key = { templates[it].id }) { i ->
                    LabelTableRow(
                        table = table,
                        template = templates[i],
                        onEdit = { editing = templates[i] },
                        onDelete = {
                            state.labelService.delete(templates[i].id)
                            refresh.value++
                        },
                    )
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                }
            }
            if (templates.isEmpty()) {
                Text(
                    tr("label.empty"),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }

    editing?.let { current ->
        LabelEditorDialog(
            state = state,
            initial = current,
            onDismiss = { editing = null },
            onSaved = { refresh.value++ },
        )
    }
}

private fun labelHeaders(): List<String> = listOf(
    tr("label.name"),
    tr("label.system"),
    tr("label.size"),
    tr("label.elements"),
    tr("common.actions"),
)

@Composable
private fun LabelTableRow(
    table: com.scaleplex.app.ui.table.TableState,
    template: LabelTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (hoverSource, hovered) = rememberRowHover()
    TableRow(
        table,
        modifier = Modifier
            .background(
                if (hovered) MaterialTheme.colors.primary.copy(alpha = 0.06f)
                else Color.Transparent,
            )
            .hoverable(hoverSource),
    ) { index, _ ->
        when (index) {
            0 -> Text(
                template.name,
                Modifier.cellPadding(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            1 -> Text(
                tr("label.system." + template.system.name),
                Modifier.cellPadding(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            2 -> Text(
                "${template.widthMm} × ${template.heightMm} mm",
                Modifier.cellPadding(),
                fontSize = 12.sp,
            )
            3 -> Text(template.elements.size.toString(), Modifier.cellPadding(), fontSize = 12.sp)
            else -> Row(Modifier.cellPadding(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text(tr("label.edit"), fontSize = 12.sp) }
                TextButton(onClick = onDelete) {
                    Text(tr("device.delete"), fontSize = 12.sp, color = UiColors.Error)
                }
            }
        }
    }
}
