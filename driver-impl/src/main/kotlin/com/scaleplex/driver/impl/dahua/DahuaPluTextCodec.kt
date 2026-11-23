package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem

/**
 * 大华 PLU 明文文本编解码（information/大华秤接口 官方格式，供 DhScalePluNet.dll 使用）。
 *
 * 三种版本（对应 dhpluversion.ini 的 PLU_VERSION 与 dhip.ini 每台秤右侧的编号）：
 * - 1 TMBZ_5 位单价机械秤：`plu4|code6|price5|unit1|valid3|store2|tare5|special1|A品名|B配料`，行尾 `\r\r\n`；
 * - 2 TMBZ_6 位单价机械秤：同上但单价 6 位，行尾 `\r\n`；
 * - 3 TMA_07 新版包装秤：`1P|plu4|A|code7|price6|weigh1|特殊1..3(各2)|valid3|店名2|部门2|条码13|皮重5|标签2|打折2|4×6时段|##品名1|##品名2|##品名3`，行尾 `\r\n`。
 *
 * 单价均为"分"（无小数点）；有效期 0-999；皮重单位 g；中文 GBK。
 * 样例数据锁定见 [DahuaPluTextCodecTest]（dhbz5.txt/dhbz6.txt/tma07.txt/dhBZ5.PLU 真机样例）。
 */
object DahuaPluTextCodec {

    /** PLU 文本版本（dhpluversion.ini PLU_VERSION）。 */
    enum class PluVersion(val id: Int) {
        TMBZ_5(1),
        TMBZ_6(2),
        TMA_07(3);

        companion object {
            fun fromId(id: Int): PluVersion = entries.firstOrNull { it.id == id } ?: TMA_07

            /** 按大华型号名推断版本：TMA→3，BZ6→2，BZ5→1，未知→3（新版包装秤）。 */
            fun fromModel(model: String): PluVersion {
                val m = model.trim().uppercase()
                return when {
                    m.contains("TMA") -> TMA_07
                    m.contains("BZ6") || m.contains("6位") -> TMBZ_6
                    m.contains("BZ5") || m.contains("5位") -> TMBZ_5
                    else -> TMA_07
                }
            }
        }
    }

    const val VERSION_KEY = "PLU_VERSION"
    const val PATH_KEY = "_PLU_PATH_"

    private val GBK = charset("GBK")

    /** 各版本品名/配料 GBK 字节上限（文档：BZ5/6 品名 9 汉字、配料 13 汉字；TMA07 品名 12 汉字×3 行）。 */
    private const val BZ_NAME_BYTES = 18
    private const val BZ_INGREDIENT_BYTES = 26
    private const val TMA_NAME_BYTES = 24

    // ---- 编码 ----

    /** 编码 PLU 列表为文本行（不含行尾），按版本套用字段格式。 */
    fun encodeLines(items: List<PluItem>, version: PluVersion): List<String> = when (version) {
        PluVersion.TMBZ_5 -> items.map { encodeBz(it, priceWidth = 5) }
        PluVersion.TMBZ_6 -> items.map { encodeBz(it, priceWidth = 6) }
        PluVersion.TMA_07 -> items.map { encodeTma07(it) }
    }

    /** 行尾（与官方样例文件一致：dhbz5.txt=0D0D0A，dhbz6/tma07=0D0A）。 */
    fun lineSeparator(version: PluVersion): String = if (version == PluVersion.TMBZ_5) "\r\r\n" else "\r\n"

    /** 编码为完整文件内容（含每行行尾，末行也带行尾，与官方样例一致）。 */
    fun encodeFile(items: List<PluItem>, version: PluVersion): String {
        val sep = lineSeparator(version)
        return encodeLines(items, version).joinToString(separator = sep, postfix = sep)
    }

    /** TMBZ_5 / TMBZ_6 行：定长数字段 + A品名 + B配料。 */
    private fun encodeBz(p: PluItem, priceWidth: Int): String = buildString {
        append(p.pluNo.coerceIn(0, 9999).toString().padStart(4, '0'))
        append(code6(p.eanCode))
        append(price(p.unitPrice, priceWidth))
        append(p.weightFlag.coerceIn(0, 9))
        append((p.usedByDate.coerceIn(0, 999)).toString().padStart(3, '0'))
        append(p.placeNo.coerceIn(0, 99).toString().padStart(2, '0'))
        append(p.tare.coerceIn(0, 15000).toString().padStart(5, '0'))
        append(p.specialMsgLines.size.coerceIn(0, 9))
        append('A')
        append(gbkTruncate(p.displayName, BZ_NAME_BYTES))
        append('B')
        append(gbkTruncate(p.ingredientLines.firstOrNull()?.text.orEmpty(), BZ_INGREDIENT_BYTES))
    }

    /** TMA_07 行：1P 头 + 定长段 + ##品名×3。 */
    private fun encodeTma07(p: PluItem): String = buildString {
        append("1P")
        append(p.pluNo.coerceIn(0, 9999).toString().padStart(4, '0'))
        append('A')
        append(p.eanCode.filter { it.isDigit() }.take(7).padStart(7, '0'))
        append(price(p.unitPrice, 6))
        append(p.weightFlag.coerceIn(0, 9))
        append("00") // 特殊信息1（需秤内预存信息表，暂不支持）
        append("00") // 特殊信息2
        append("00") // 特殊信息3
        append((p.usedByDate.coerceIn(0, 999)).toString().padStart(3, '0'))
        append(p.placeNo.coerceIn(0, 99).toString().padStart(2, '0')) // 店名号
        append(p.mainGroup.coerceIn(0, 99).toString().padStart(2, '0')) // 部门号
        append(p.eanCode.filter { it.isDigit() }.takeLast(13).padStart(13, '0')) // 13 位数字代号
        append(p.tare.coerceIn(0, 15000).toString().padStart(5, '0'))
        append(p.label1Format.coerceIn(0, 99).toString().padStart(2, '0')) // 标签号
        append("00") // 打折方式：0=不按时间段自动调价且手动不可调，1=自动不可手动，2=自动可手动
        append("000000".repeat(4)) // 4 个时间段（起始2+结束2+折扣2），暂不支持自动打折
        val names = p.commodityLines.take(3).map { it.text }
        repeat(3) { n -> append("##").append(gbkTruncate(names.getOrElse(n) { "" }, TMA_NAME_BYTES)) }
    }

    private fun code6(v: String): String = v.filter { it.isDigit() }.take(6).padStart(6, '0')

    private fun price(minor: Long, width: Int): String =
        (if (minor < 0) 0L else minor).coerceAtMost(pow10(width) - 1).toString().padStart(width, '0')

    /** GBK 字节截断（按字符累计字节数，保证不切半个汉字）。 */
    private fun gbkTruncate(text: String, maxBytes: Int): String {
        var used = 0
        val sb = StringBuilder()
        for (ch in text) {
            val len = ch.toString().toByteArray(GBK).size
            if (used + len > maxBytes) break
            sb.append(ch)
            used += len
        }
        return sb.toString()
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    // ---- 解析（用于测试互逆验证与将来 PLU 回收） ----

    private const val BZ_BODY_5 = 4 + 6 + 5 + 1 + 3 + 2 + 5 + 1 // 27
    private const val BZ_BODY_6 = 4 + 6 + 6 + 1 + 3 + 2 + 5 + 1 // 28
    private const val TMA_HEAD_LEN = 80

    /** 解析 PLU 文本行。版本未指定时自动识别：1P 头=TMA_07，A 前数字段 27 位=TMBZ_5，28 位=TMBZ_6。 */
    fun parseLines(lines: List<String>, version: PluVersion? = null): List<PluItem> =
        lines.asSequence()
            .map { it.trim('\r', '\n') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                when {
                    version == PluVersion.TMA_07 || (version == null && line.startsWith("1P")) -> parseTma07(line)
                    else -> {
                        val width = when (version) {
                            PluVersion.TMBZ_5 -> 5
                            PluVersion.TMBZ_6 -> 6
                            else -> {
                                val digits = line.indexOf('A').let { if (it < 0) 0 else it }
                                if (digits == BZ_BODY_5) 5 else 6
                            }
                        }
                        parseBz(line, width)
                    }
                }
            }
            .toList()

    private fun parseBz(line: String, priceWidth: Int): PluItem? {
        val bodyLen = 4 + 6 + priceWidth + 1 + 3 + 2 + 5 + 1
        if (line.length < bodyLen + 1) return null
        val body = line.substring(0, bodyLen)
        val text = line.substring(bodyLen)
        var idx = 0
        fun take(n: Int): Int {
            val v = body.substring(idx, idx + n)
            idx += n
            return v.toIntOrNull() ?: 0
        }
        val pluNo = take(4)
        val code = body.substring(idx, idx + 6).also { idx += 6 }
        val priceMinor = take(priceWidth).toLong()
        val weightFlag = take(1)
        val usedBy = take(3)
        val store = take(2)
        val tare = take(5)
        val special = take(1)
        val name = text.substringAfter('A', "").substringBefore('B')
        val ingredient = text.substringAfter('B', "")
        return PluItem(
            pluNo = pluNo,
            eanCode = code,
            weightFlag = weightFlag,
            unitPrice = priceMinor,
            usedByDate = usedBy,
            placeNo = store,
            tare = tare,
            mainGroup = 997,
            name = name,
            commodityLines = if (name.isNotEmpty()) listOf(PluTextLineOf(name)) else emptyList(),
            ingredientLines = if (ingredient.isNotEmpty()) listOf(PluTextLineOf(ingredient)) else emptyList(),
            specialMsgLines = (0 until special).map { PluTextLineOf("") },
        )
    }

    private fun parseTma07(line: String): PluItem? {
        if (!line.startsWith("1P") || line.length < TMA_HEAD_LEN + 2) return null
        val head = line.substring(0, TMA_HEAD_LEN)
        val tail = line.substring(TMA_HEAD_LEN)
        var idx = 2
        fun take(n: Int): Int {
            val v = head.substring(idx, idx + n)
            idx += n
            return v.toIntOrNull() ?: 0
        }
        val pluNo = take(4)
        idx++ // 'A' 分隔符
        take(7) // 商品代码 7 位（与条码代号同源，此处不入库）
        val priceMinor = take(6).toLong()
        val weightFlag = take(1)
        take(2); take(2); take(2) // 特殊信息 1-3
        val usedBy = take(3)
        val store = take(2)
        val dept = take(2)
        val ean13 = head.substring(idx, idx + 13).also { idx += 13 } // 13 位超 Int，按字符串取
        val tare = take(5)
        val label = take(2)
        // 打折方式（2）+ 4×6 时间段不映射
        val names = tail.split("##").filter { it.isNotEmpty() }
        return PluItem(
            pluNo = pluNo,
            eanCode = ean13,
            weightFlag = weightFlag,
            unitPrice = priceMinor,
            usedByDate = usedBy,
            placeNo = store,
            mainGroup = dept,
            tare = tare,
            label1Format = label,
            name = names.firstOrNull().orEmpty(),
            commodityLines = names.map { PluTextLineOf(it) },
        )
    }

    private fun PluTextLineOf(text: String) = com.scaleplex.core.model.PluTextLine(text = text)
}
