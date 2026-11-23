package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import java.io.File
import java.nio.charset.Charset

/**
 * 寺冈 SM-100 PLU 文件（25H）解析 —— 依据《PLU_FILE.TXT》二进制布局：
 *
 * ```
 * PLU Number     4B BCD
 * Record Size    2B HEX（不含 PLU 与 Size 自身）
 * Status         2B BIN（bit0 计重/计件等）
 * Status2        3B BIN
 * Unit Price     4B BCD（即最小货币单位）
 * Label1/2       1B HEX ×2
 * Barcode Format 1B HEX
 * EAN Data       7B BCD（F1F2 1B + 项目代码 5B + 末字节高位 9=ITF/0=EAN、低位右侧数据）
 * Main Group     2B BCD
 * Sell/Use/Packed Date 2B BCD ×3（3 位天数）
 * Discount       30B
 * COMMODITY / INGREDIENT / SPECIAL MESSAGE：Font(1B) + Size(1B) + GBK 文本(Size) + 0x0C
 * BCC            1B
 * ```
 *
 * 输入为 TWS `RDF 25` 下载的 `IP.25.DAT`（每行一条记录的 16 进制文本）。
 */
object DigiPluFileParser {

    const val PLU_FILE_NO = 0x25

    private val GBK: Charset = Charset.forName("GBK")

    /** GBK 可打印文本段（含中文/ASCII，排除控制/字体字节）。 */
    private val PRINTABLE_RUN = Regex("[\\u0020-\\u007e\\u4e00-\\u9fff\\u3000-\\u303f]{2,}")

    /** 解析整个 .DAT 文件（每行一条记录），不合规行跳过。 */
    fun parseFile(file: File): List<PluItem> = parseLines(file.readLines(Charsets.ISO_8859_1))

    fun parseLines(lines: List<String>): List<PluItem> =
        lines.map { it.trim() }.filter { it.length >= 20 }.mapNotNull { parseLine(it) }

    /**
     * 解析并返回 (TWS 记录号, PLU)。记录号 = 非空记录行的 1-based 行号
     * （TWS `DELR <文件号> <记录号>` 按此号删除单条记录）。
     */
    fun parseFileIndexed(file: File): List<Pair<Int, PluItem>> =
        file.readLines(Charsets.ISO_8859_1)
            .map { it.trim() }
            .filter { it.length >= 20 }
            .mapIndexedNotNull { index, line ->
                parseLine(line)?.let { (index + 1) to it }
            }

    /** 解析单条 25H 记录（16 进制文本行）。 */
    fun parseLine(line: String): PluItem? {
        val bytes = DigiReceiptParser.hexToBytes(line) ?: return null
        if (bytes.size < 35) return null
        var pos = 0
        val pluNo = DigiReceiptParser.bcd(bytes.copyOfRange(0, 4)).toInt()
        pos += 4
        val recordSize = bigEndian(bytes, pos, 2)
        pos += 2
        val bodyEnd = minOf(bytes.size, pos + recordSize)
        val body = bytes.copyOfRange(pos, bodyEnd)
        if (body.size < 33) return null

        var p = 0
        val status1 = bigEndian(body, p, 2); p += 2
        bigEndian(body, p, 3); p += 3 // Status2（含日期/文本存在位，暂不细分）
        val price = DigiReceiptParser.bcd(body.copyOfRange(p, p + 4)); p += 4
        val label1 = body[p].toInt() and 0xFF; p += 1
        val label2 = body[p].toInt() and 0xFF; p += 1
        val barFormat = body[p].toInt() and 0xFF; p += 1
        // EAN Data 7B：F1F2(1) + 项目代码(5, BCD=hex 半字节) + 末字节（高半字节 9=ITF/0=EAN，低半字节右侧数据）
        val f1f2 = "%02X".format(body[p].toInt() and 0xFF); p += 1
        val itemCode = body.copyOfRange(p, p + 5).joinToString("") { "%02X".format(it.toInt() and 0xFF) }; p += 5
        val eanLast = body[p].toInt() and 0xFF; p += 1
        val barcodeType = (eanLast shr 4) and 0x0F
        val barRight = eanLast and 0x0F
        val mainGroup = DigiReceiptParser.bcd(body.copyOfRange(p, p + 2)).toInt(); p += 2
        val sellBy = bcdDays(body, p); p += 2
        val useBy = bcdDays(body, p); p += 2
        val packedDate = bcdDays(body, p); p += 2
        p += 30 // Discount 块

        // 文本区（结构含多行/分隔符变体，待实体秤联调）：按 GBK 可打印段提取——
        // 第 1 段 = 品名、第 2 段 = 成分、第 3 段 = 特殊信息（字体字节为控制字符自然被过滤）
        val textArea = body.copyOfRange(p, body.size)
        val runs = PRINTABLE_RUN.findAll(String(textArea, GBK))
            .map { it.value.trim() }
            .filter { it.length >= 2 }
            .toList()
        val textLines = mutableListOf<PluTextLine>()
        val ingredients = mutableListOf<PluTextLine>()
        val specialMsgs = mutableListOf<PluTextLine>()
        val name = runs.getOrNull(0).orEmpty()
        if (name.isNotBlank()) textLines += PluTextLine(fontNo = 0, text = name)
        runs.getOrNull(1)?.let { ingredients += PluTextLine(fontNo = 0, text = it) }
        runs.getOrNull(2)?.let { specialMsgs += PluTextLine(fontNo = 0, text = it) }

        return PluItem(
            pluNo = pluNo,
            name = name,
            weightFlag = if (status1 and 0x01 == 0x01) 1 else 0,
            mainGroup = mainGroup,
            unitPrice = price,
            barcodeType = if (barcodeType == 9) 9 else 0,
            barcodeFormat = barFormat,
            flagData = f1f2,
            barRightDataType = barRight,
            eanCode = itemCode.trimStart('0'),
            usedByDate = useBy,
            sellByDate = sellBy,
            packedDate = packedDate,
            label1Format = label1,
            label2Format = label2,
            commodityLines = textLines,
            ingredientLines = ingredients,
            specialMsgLines = specialMsgs,
        )
    }

    private fun bigEndian(bytes: ByteArray, offset: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = (v shl 8) or (bytes[offset + i].toInt() and 0xFF)
        return v
    }

    /** 2 字节 BCD 天数（3 位有效数字）。 */
    private fun bcdDays(bytes: ByteArray, offset: Int): Int {
        val v = DigiReceiptParser.bcd(bytes.copyOfRange(offset, offset + 2))
        return if (v < 0) 0 else v.toInt().coerceAtMost(999)
    }
}
