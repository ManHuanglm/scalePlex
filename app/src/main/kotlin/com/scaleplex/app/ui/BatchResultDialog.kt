package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.BatchResult

/**
 * 批量下发结果弹窗：展示每台秤的成功 / 失败明细（PLU 下发、标签下发、分组下发共用）。
 */
@Composable
fun BatchResultDialog(
    state: AppState,
    results: Map<Long, BatchResult>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("batch.title")) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                if (results.isEmpty()) {
                    items(listOf(tr("batch.noDevice"))) { line ->
                        Text(line, fontSize = 12.sp)
                    }
                }
                items(results.entries.toList(), key = { it.key }) { (deviceId, result) ->
                    val device = state.scaleService.getDevice(deviceId)
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "${device?.name ?: deviceId}  ${tr("common.success")} ${result.success} / ${tr("common.failed")} ${result.failed}",
                            fontSize = 13.sp,
                        )
                        result.errors.take(5).forEach { err ->
                            Text(
                                "  PLU ${err.pluNo ?: "-"}: ${err.message}",
                                fontSize = 11.sp,
                                color = androidx.compose.ui.graphics.Color(0xFFB00020),
                            )
                        }
                        if (result.errors.size > 5) {
                            Text("  ...", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text(tr("common.ok")) }
            }
        },
    )
}
