package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine

/**
 * Parses and emits the DIGI SM PLU CSV format used by the DLL pipeline
 * (see `PLU_Demo1.csv`, `DLL 使用说明.doc`, `PLU 文件定义 For SM-100.xls`).
 *
 * Column order is exactly that of PLU_Demo1.csv. Text columns are GBK-encoded
 * on the scale side; here we keep them as Kotlin Strings and let the writer
 * apply the CSV quoting rules.
 */
object DigiPluCsvCodec {

    /** Canonical header, identical to PLU_Demo1.csv line 1. */
    val HEADER: List<String> = listOf(
        "PLU_No", "Weight_Flag", "MG_Code", "Unit_Price", "Cost",
        "Barcode_Format", "Flag_data", "Right_side_data_of_item_barcode", "EAN_Code",
        "Used_By_Date", "Commodity_line1_no", "Commodity_line1_character_size", "Commodity_line1_name",
        "Commodity_line2_no", "Commodity_line2_character_size", "Commodity_line2_name",
        "PLU_Tare", "Label1_Format", "Label2_Format", "Place_No", "Tax_rate_no",
        "Quantity", "Quantity_Symbol_Type", "Sell_By_Date", "Sell_By_Time", "Used_by_time",
        "Packed_Date", "Packed_Time", "Special_Message_no", "Ingredient_no",
        "Special_Message_line1_no", "Special_Message_line1_character_size", "Special_Message_line1_name",
        "Special_Message_line2_no", "Special_Message_line2_character_size", "Special_Message_line2_name",
        "Special_Message_line3_no", "Special_Message_line3_character_size", "Special_Message_line3_name",
        "Special_Message_line4_no", "Special_Message_line4_character_size", "Special_Message_line4_name",
        "Special_Message_line5_no", "Special_Message_line5_character_size", "Special_Message_line5_name",
        "Ingedients_line1_no", "Ingedients_line1_character_size", "Ingedients_line1_name",
        "Ingedients_line2_no", "Ingedients_line2_character_size", "Ingedients_line2_name",
        "Ingedients_line3_no", "Ingedients_line3_character_size", "Ingedients_line3_name",
        "Ingedients_line4_no", "Ingedients_line4_character_size", "Ingedients_line4_name",
        "Ingedients_line5_no", "Ingedients_line5_character_size", "Ingedients_line5_name",
    )

    /** Parse PLU_Demo1-format CSV (list of physical lines, header optional). */
    fun parse(lines: List<String>, priceDecimals: Int = 2): List<PluItem> {
        val result = mutableListOf<PluItem>()
        var headerSkipped = false
        for (raw in lines) {
            val line = raw.trimEnd('\r', '\n')
            if (line.isBlank()) continue
            if (!headerSkipped) {
                headerSkipped = true
                if (line.startsWith("PLU_No", ignoreCase = true)) continue
            }
            val cols = splitCsv(line)
            if (cols.isEmpty() || cols[0].isBlank()) continue
            result += fromColumns(cols, priceDecimals)
        }
        return result
    }

    private fun fromColumns(c: List<String>, priceDecimals: Int): PluItem {
        fun s(i: Int) = c.getOrNull(i)?.trim().orEmpty()
        fun i(idx: Int) = s(idx).toIntOrNull() ?: 0
        // commodity line1/2 text
        val commodity = buildList {
            val l1 = s(12)
            if (l1.isNotEmpty()) add(PluTextLine(fontNo = i(11), text = l1))
            val l2 = s(15)
            if (l2.isNotEmpty()) add(PluTextLine(fontNo = i(14), text = l2))
        }
        // special message lines 1..5 -> cols 32,35,38,41,44
        val special = (0..4).mapNotNull { n ->
            val nameCol = 32 + n * 3
            val text = s(nameCol)
            if (text.isNotEmpty()) PluTextLine(fontNo = i(nameCol - 1), text = text) else null
        }
        // ingredient lines 1..5 -> cols 47,50,53,56,59
        val ingredients = (0..4).mapNotNull { n ->
            val nameCol = 47 + n * 3
            val text = s(nameCol)
            if (text.isNotEmpty()) PluTextLine(fontNo = i(nameCol - 1), text = text) else null
        }

        return PluItem(
            pluNo = i(0),
            weightFlag = i(1),
            mainGroup = s(2).toIntOrNull() ?: 997,
            unitPrice = com.scaleplex.core.util.Numbers.displayToMinor(s(3), priceDecimals),
            cost = com.scaleplex.core.util.Numbers.displayToMinor(s(4), priceDecimals),
            barcodeFormat = i(5),
            flagData = s(6),
            barRightDataType = i(7),
            eanCode = s(8),
            usedByDate = i(9),
            commodityLines = commodity,
            tare = i(16),
            label1Format = i(17),
            label2Format = i(18),
            placeNo = i(19),
            taxRateNo = i(20),
            qty = i(21),
            sellByDate = i(23),
            packedDate = i(26),
            specialMsgLines = special,
            ingredientLines = ingredients,
        )
    }

    /** Emit PLUs to PLU_Demo1-format CSV lines (including header). */
    fun writePlus(items: List<PluItem>, priceDecimals: Int = 2, header: Boolean = true): List<String> {
        val out = mutableListOf<String>()
        if (header) out += HEADER.joinToString(",")
        for (p in items) {
            val c = arrayOfNulls<String>(HEADER.size)
            c[0] = p.pluNo.toString()
            c[1] = p.weightFlag.toString()
            c[2] = p.mainGroup.toString()
            c[3] = com.scaleplex.core.util.Numbers.minorToDisplay(p.unitPrice, priceDecimals)
            c[4] = com.scaleplex.core.util.Numbers.minorToDisplay(p.cost, priceDecimals)
            c[5] = p.barcodeFormat.toString()
            c[6] = p.flagData
            c[7] = p.barRightDataType.toString()
            c[8] = p.eanCode
            c[9] = p.effectiveShelfLifeDays.toString()
            // commodity line1
            val cl = p.commodityLines
            c[10] = if (cl.isNotEmpty()) "1" else ""
            c[11] = if (cl.isNotEmpty()) cl[0].fontNo.toString() else ""
            c[12] = if (cl.isNotEmpty()) cl[0].text else ""
            if (cl.size > 1) {
                c[13] = "1"; c[14] = cl[1].fontNo.toString(); c[15] = cl[1].text
            }
            c[16] = p.tare.toString()
            c[17] = p.label1Format.toString()
            c[18] = p.label2Format.toString()
            c[19] = p.placeNo.toString()
            c[20] = p.taxRateNo.toString()
            c[21] = p.qty.toString()
            c[23] = p.sellByDate.toString()
            c[26] = p.packedDate.toString()
            // special message lines
            p.specialMsgLines.take(5).forEachIndexed { n, line ->
                c[31 + n * 3] = (n + 1).toString()
                c[32 + n * 3] = line.fontNo.toString()
                c[33 + n * 3] = line.text
            }
            // ingredient lines
            p.ingredientLines.take(5).forEachIndexed { n, line ->
                c[46 + n * 3] = (n + 1).toString()
                c[47 + n * 3] = line.fontNo.toString()
                c[48 + n * 3] = line.text
            }
            out += c.map { escape(it ?: "") }.joinToString(",")
        }
        return out
    }

    private fun escape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun splitCsv(line: String): List<String> {
        val cols = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { cols.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        cols.add(sb.toString())
        return cols
    }
}
