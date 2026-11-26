package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem

/**
 * 寺冈 ETP 族（SM-120）PLU 全量 CSV（`plu0uall.csv`）解析 —— 真机格式校准 2026-11-25：
 *
 * - 138 列 GBK CSV，无表头；品名在第 103 列（下标 102，引号包裹，可能含逗号）；
 * - 下标 0 = PLU 号；23 = 条码/项目代码（10 位）；27 = 部门号；19 = 标签 1 格式；18 = 单价；
 * - 空闲槽标记为 PLU 号 ≥999997 且无品名的记录（999997/999998/999999），解析时跳过。
 *
 * **单价小数位待校准**：真机 PLU1/PLU2 的单价列为 45 / 144，无法确认其小数位约定
 * （PriceDecimalPlace=1 则为 4.5 / 14.4 元），本解析暂不填单价（unitPrice=0），
 * 待与秤端实打印对照后校准。
 */
object DigiEtpCsvCodec {

    private const val COL_PLU_NO = 0
    private const val COL_UNIT_PRICE = 18
    private const val COL_LABEL1 = 19
    private const val COL_BAR_FORMAT = 21
    private const val COL_BARCODE = 23
    private const val COL_MAIN_GROUP = 27
    private const val COL_NAME = 102

    /** 解析 plu0uall.csv 全部行 → PLU 列表（跳过空闲槽/无品名记录）。 */
    fun parse(csv: String): List<PluItem> = csv.split('\n').mapNotNull { parseLine(it) }

    /** 解析单行 CSV；无品名或 PLU 号非法的行（含空闲槽标记）返回 null。 */
    fun parseLine(line: String): PluItem? {
        if (line.isBlank()) return null
        val cols = splitCsv(line.trimEnd('\r'))
        val pluNo = cols.getOrNull(COL_PLU_NO)?.trim()?.toIntOrNull() ?: return null
        val name = cols.getOrNull(COL_NAME)?.trim().orEmpty().removeSurrounding("\"").trim()
        // 空闲槽标记（无品名；真机见 999997/999998/999999）与无名记录跳过
        if (name.isBlank()) return null
        fun field(index: Int): Int = cols.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
        val barcode = cols.getOrNull(COL_BARCODE)?.trim().orEmpty()
        return PluItem(
            pluNo = pluNo,
            name = name,
            eanCode = barcode,
            mainGroup = field(COL_MAIN_GROUP),
            label1Format = field(COL_LABEL1),
            barcodeFormat = field(COL_BAR_FORMAT),
            // 单价小数位约定待与秤端对照校准，暂不下发错误价格
            unitPrice = 0,
        )
    }

    /** 引号感知的 CSV 分割（品名内可含逗号/引号转义）。 */
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
