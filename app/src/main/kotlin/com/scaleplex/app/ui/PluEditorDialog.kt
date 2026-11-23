package com.scaleplex.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaleplex.app.AppState
import com.scaleplex.core.i18n.tr
import com.scaleplex.core.model.DigiTables
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.util.Numbers

/**
 * Add / edit PLU dialog. Exposes the core DIGI PLU fields from the SM definition:
 * PLU no, names (zh/en), weight flag, price, cost, tare, group, barcode, label formats.
 */
@Composable
fun PluEditorDialog(state: AppState, initial: PluItem, onDismiss: () -> Unit, onSave: (PluItem) -> Unit) {
    var plu by remember { mutableStateOf(initial) }

    fun priceText(v: Long) = Numbers.minorToDisplay(v, state.config.priceDecimals)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("plu.add") + " / " + tr("plu.edit")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(460.dp)) {
                LabeledField(tr("plu.field.no"), plu.pluNo.toString()) { s ->
                    s.toIntOrNull()?.let { plu = plu.copy(pluNo = it) }
                }
                LabeledField(tr("plu.field.name"), plu.name) { plu = plu.copy(name = it) }
                LabeledField(tr("plu.field.nameEn"), plu.nameEn) { plu = plu.copy(nameEn = it) }
                Row {
                    Dropdown(tr("plu.field.weightFlag"), if (plu.weightFlag == 1) tr("plu.weight.count") else tr("plu.weight.weigh")) { chosen ->
                        plu = plu.copy(weightFlag = if (chosen.startsWith("计件") || chosen.startsWith("By Count")) 1 else 0)
                    }
                }
                LabeledField(tr("plu.field.price"), priceText(plu.unitPrice)) { s ->
                    Numbers.parseDouble(s)?.let { d ->
                        plu = plu.copy(unitPrice = Numbers.displayToMinor(s, state.config.priceDecimals))
                    }
                }
                LabeledField(tr("plu.field.cost"), priceText(plu.cost)) {
                    plu = plu.copy(cost = Numbers.displayToMinor(it, state.config.priceDecimals))
                }
                LabeledField(tr("plu.field.tare"), plu.tare.toString()) { s ->
                    s.toIntOrNull()?.let { plu = plu.copy(tare = it) }
                }
                LabeledField(tr("plu.field.group"), plu.mainGroup.toString()) { s ->
                    s.toIntOrNull()?.let { plu = plu.copy(mainGroup = it) }
                }
                LabeledField(tr("plu.field.barcode"), plu.eanCode) { plu = plu.copy(eanCode = it) }
                LabeledField(
                    tr("plu.field.label1"),
                    (DigiTables.LABEL1_FORMATS.firstOrNull { it.first == plu.label1Format }?.second)
                        ?: plu.label1Format.toString(),
                ) { }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(plu) }) { Text(tr("common.save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.cancel")) }
        },
    )
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun Dropdown(label: String, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Options for the weight-flag selector shown alongside the name fields.
    val options = listOf("计重 / By Weight", "计件 / By Count")
    Box {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingIcon = {
                TextButton(onClick = { expanded = true }) { Text("▾") }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(onClick = { onSelect(opt); expanded = false }) { Text(opt) }
            }
        }
    }
}
