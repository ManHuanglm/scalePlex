package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.Category

/**
 * 类别资料管理对话框（对齐顶尖 LiNK64「类别资料」）：
 * 类别列表（编号+名称）、新增/修改/删除；PLU 的类别编号（mainGroup）关联此处。
 */
@Composable
fun CategoryDialog(state: AppState, onDismiss: () -> Unit) {
    var categories by remember { mutableStateOf(state.categoryService.all()) }
    var editNo by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun reload() {
        categories = state.categoryService.all()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("category.title")) },
        text = {
            Column(modifier = Modifier.width(420.dp)) {
                // ---- 编辑行：编号 + 名称 + 保存 ----
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editNo, onValueChange = { editNo = it },
                        label = { Text(tr("category.no")) },
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text(tr("category.name")) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val no = editNo.toIntOrNull()
                        if (no == null || editName.isBlank()) {
                            message = tr("category.inputInvalid")
                        } else {
                            state.categoryService.save(Category(no, editName.trim()))
                                .onSuccess { editNo = ""; editName = ""; message = ""; reload() }
                                .onFailure { message = it.message ?: "error" }
                        }
                    }) { Text(tr("plu.save")) }
                }
                if (message.isNotBlank()) {
                    Text(message, fontSize = 11.sp, color = Color(0xFFB00020), modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Divider()
                // ---- 类别列表 ----
                LazyColumn(modifier = Modifier.weight(1f).heightIn(min = 180.dp)) {
                    items(categories, key = { it.no }) { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.no.toString(), Modifier.width(80.dp), fontSize = 13.sp)
                            Text(cat.name, Modifier.weight(1f), fontSize = 13.sp)
                            TextButton(onClick = {
                                editNo = cat.no.toString(); editName = cat.name
                            }) { Text(tr("plu.edit"), fontSize = 12.sp) }
                            TextButton(onClick = {
                                state.categoryService.delete(cat.no)
                                reload()
                            }) { Text(tr("plu.delete"), fontSize = 12.sp, color = Color(0xFFB00020)) }
                        }
                        Divider()
                    }
                }
                if (categories.isEmpty()) {
                    Text(tr("category.empty"), fontSize = 12.sp, color = Color(0xFF808080), modifier = Modifier.padding(8.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("common.ok")) } },
    )
}
