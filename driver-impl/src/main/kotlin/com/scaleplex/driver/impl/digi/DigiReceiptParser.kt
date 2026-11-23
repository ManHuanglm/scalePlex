package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.TradeRecord
import kotlinx.datetime.LocalDateTime
import java.io.File

/**
 * 寺冈 SM 系列交易流水解析：按《SM100_RTRECEIPT_DETAIL.dsc》字段表逐字节解析
 * TWS.exe `RDFL 81` 下载的明细文件（每行一条记录、16 进制文本）。
 *
 * 字段表（Bytes/Type）：RECT_No(2 BCD) ITEM_ID(2 BCD) RECT_RecSize(2 HEX) D_ItemST(4 HEX)
 * D_PLUNo(4 BCD) D_MGNo(4 BCD) D_QTY(4 HEX) D_Weight(4 HEX_WEIGHT) D_UPrice(4 HEX_PRICE)
 * D_UPriceADis/D_UPDisAMT/D_NPrice/D_ACTPrice(各4 HEX_PRICE) → 其余字段跳过（含时间戳
 * D_JINRecBWeight=BCD_DATE、D_JINRecBStat=BCD_TIME）。
 * 价格/重量编码待实体秤校准（按大端整数、金额隐含 2 位小数、重量克）。
 */
object DigiReceiptParser {

    /** 流水明细文件号（TWS 命令用）：0x51。 */
    const val RECEIPT_DETAIL_FILE_NO = 0x51

    /** 明细记录字段表：字段名 to (字节数, 类型)。 */
    private val FIELDS = listOf(
        "RECT_No" to F(2, "BCD"),
        "ITEM_ID" to F(2, "BCD"),
        "RECT_RecSize" to F(2, "HEX"),
        "D_ItemST" to F(4, "HEX"),
        "D_PLUNo" to F(4, "BCD"),
        "D_MGNo" to F(4, "BCD"),
        "D_QTY" to F(4, "HEX"),
        "D_Weight" to F(4, "HEX_WEIGHT"),
        "D_UPrice" to F(4, "HEX_PRICE"),
        "D_UPriceADis" to F(4, "HEX_PRICE"),
        "D_UPDisAMT" to F(4, "HEX_PRICE"),
        "D_NPrice" to F(4, "HEX_PRICE"),
        "D_ACTPrice" to F(4, "HEX_PRICE"),
        "D_PriceAFDT" to F(4, "HEX"),
        "D_PriceAFSUBTD" to F(4, "HEX"),
        "D_PriceAFUPOV" to F(4, "HEX"),
        "D_TPriceDISCAMT" to F(4, "HEX"),
        "D_IPriceAFTRUND" to F(4, "HEX"),
        "D_TAXRNo" to F(1, "HEX"),
        "DUMMY" to F(1, "HEX"),
        "D_TAXR" to F(2, "HEX"),
        "D_TAXVAL" to F(4, "HEX"),
        "D_TAXVALAFSUBTD" to F(4, "HEX"),
        "D_SPPriceNo" to F(1, "HEX"),
        "DUMMY1" to F(1, "HEX"),
        "D_ScaleNo" to F(3, "HEX"),
        "DUMMY2" to F(1, "HEX"),
        "D_VALPoint1" to F(4, "HEX"),
        "D_PricePoint1" to F(4, "HEX"),
        "D_VALPoint2" to F(4, "HEX"),
        "D_PricePoint2" to F(4, "HEX"),
        "D_TareVAL" to F(4, "HEX"),
        "D_SPDISCStat" to F(1, "HEX"),
        "D_MDStat" to F(1, "HEX"),
        "D_JINRecBWeight" to F(4, "BCD_DATE"),
        "D_JINRecBStat" to F(2, "BCD_TIME"),
        "D_PUBFWeight" to F(4, "HEX"),
        "D_PUBBonusP" to F(4, "HEX"),
        "D_PUBFQTY" to F(4, "HEX"),
        "D_TRCBCNT" to F(2, "HEX"),
        "D_TRFATCNT" to F(2, "HEX"),
        "D_TRSLHouse" to F(2, "HEX"),
        "D_TRSLCNT" to F(2, "HEX"),
        "D_TRCutHall" to F(2, "HEX"),
        "D_TRCutCNT" to F(2, "HEX"),
    )

    private data class F(val bytes: Int, val type: String)

    /**
     * 解析明细文件（每行一条记录的 16 进制文本），不合规行跳过。
     * [deviceId] 用于标记记录归属；pluNames 供 PLU 号→品名映射（可选）。
     */
    fun parseReceiptFile(file: File, deviceId: Long, pluNames: Map<Int, String> = emptyMap()): List<TradeRecord> =
        parseLines(file.readLines(Charsets.ISO_8859_1), deviceId, pluNames)

    fun parseLines(lines: List<String>, deviceId: Long, pluNames: Map<Int, String> = emptyMap()): List<TradeRecord> {
        val out = mutableListOf<TradeRecord>()
        lines.map { it.trim() }.filter { it.length >= 16 }.forEach { line ->
            parseLine(line, deviceId, pluNames)?.let { out += it }
        }
        return out
    }

    fun parseLine(line: String, deviceId: Long, pluNames: Map<Int, String> = emptyMap()): TradeRecord? {
        val bytes = hexToBytes(line) ?: return null
        var offset = 0
        var rectNo = 0L
        var itemId = 0L
        var pluNo = 0
        var qty = 0
        var weight = 0
        var unitPrice = 0L
        var actPrice = 0L
        var date = ""
        var time = ""
        for ((name, f) in FIELDS) {
            if (offset + f.bytes > bytes.size) return null
            val chunk = bytes.copyOfRange(offset, offset + f.bytes)
            when (name) {
                "RECT_No" -> rectNo = bcd(chunk)
                "ITEM_ID" -> itemId = bcd(chunk)
                "D_PLUNo" -> pluNo = bcd(chunk).toInt()
                "D_QTY" -> qty = bigEndian(chunk)
                "D_Weight" -> weight = bigEndian(chunk)
                "D_UPrice" -> unitPrice = price(chunk)
                "D_ACTPrice" -> actPrice = price(chunk)
                "D_JINRecBWeight" -> date = bcdDate(chunk)
                "D_JINRecBStat" -> time = bcdTime(chunk)
            }
            offset += f.bytes
        }
        val amount = actPrice.takeIf { it > 0 } ?: (unitPrice * (if (qty > 0) qty else 1))
        return TradeRecord(
            deviceId = deviceId,
            recordNo = rectNo.takeIf { it > 0 } ?: itemId,
            timestamp = parseDateTime(date, time),
            pluNo = pluNo,
            pluName = pluNames[pluNo] ?: "",
            weight = weight,
            unitPrice = unitPrice,
            amount = amount,
        )
    }

    /** 16 进制文本 → 字节数组；非法字符返回 null。 */
    fun hexToBytes(line: String): ByteArray? {
        if (line.isEmpty() || line.length % 2 != 0) return null
        return try {
            ByteArray(line.length / 2) { i ->
                line.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** BCD 大端 → 数字（每半字节一位）。 */
    fun bcd(bytes: ByteArray): Long {
        var v = 0L
        for (b in bytes) {
            val hi = ((b.toInt() shr 4) and 0x0F)
            val lo = (b.toInt() and 0x0F)
            if (hi > 9 || lo > 9) return -1
            v = v * 100 + hi * 10 + lo
        }
        return v
    }

    /** 大端无符号整数。 */
    fun bigEndian(bytes: ByteArray): Int {
        var v = 0
        for (b in bytes) v = (v shl 8) or (b.toInt() and 0xFF)
        return v
    }

    /** HEX_PRICE：大端整数，隐含 2 位小数（待实体秤校准）。 */
    fun price(bytes: ByteArray): Long = bigEndian(bytes).toLong()

    /** BCD_DATE(4字节) → YYYY-MM-DD（8位BCD）。 */
    fun bcdDate(bytes: ByteArray): String = bytes.joinToString("") {
        "%02x".format(it.toInt() and 0xFF)
    }

    /** BCD_TIME(2字节=4位数字 HHMM) → HH:mm:00（兼容 3 字节 HHMMSS）。 */
    fun bcdTime(bytes: ByteArray): String {
        val s = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return when (s.length) {
            4 -> "${s.substring(0, 2)}:${s.substring(2, 4)}:00"
            6 -> "${s.substring(0, 2)}:${s.substring(2, 4)}:${s.substring(4, 6)}"
            else -> s
        }
    }

    private fun parseDateTime(date: String, time: String): LocalDateTime? = runCatching {
        if (date.length != 8 || time.length != 8) return null
        LocalDateTime(
            date.substring(0, 4).toInt(),
            date.substring(4, 6).toInt(),
            date.substring(6, 8).toInt(),
            time.substring(0, 2).toInt(),
            time.substring(3, 5).toInt(),
            time.substring(6, 8).toInt(),
        )
    }.getOrNull()
}
