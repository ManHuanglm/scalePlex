package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem

/**
 * 大华 TMA_07 条码秤协议帧编解码（真机抓包标定 2026-11-28 @192.168.0.150:4001，
 * 官方 TMA4.0 上位机经本地 TCP 代理抓包 + TMA07-PLU说明.doc 字段表）。
 *
 * 全部帧为 ASCII 文本，CRLF 结尾；秤端应答以 ETX(0x03) 结尾。
 *
 * PLU 73 位数字段布局（TMA07-PLU说明.doc，与 1P 文件格式同构）：
 * ```
 * [0:7]    商品代码（7 位，秤端品名字库编码）
 * [7:13]   单价（6 位，分/kg）
 * [13]     称重方式（1 位）：0 称重 / 1 计件 / 2 定重
 * [14:20]  特殊信息 1~3（2+2+2 位）
 * [20:23]  有效期（3 位，天）
 * [23:25]  店名号（2 位）
 * [25:27]  部门号（2 位）
 * [27:40]  13 位数字代号
 * [40:45]  皮重（5 位，g）
 * [45:47]  标签号（2 位）
 * [47:49]  打折（2 位）
 * [49:73]  4 × (时段起 2 + 止 2 + 折扣 2)
 * ```
 * 其后为条码段 `B{条码}C{3×4位}D{3×4位}E`（条码/关联段均可变长或空，空条码 = `B00C00D00E`）。
 */
internal object DahuaProto {

    /** PLU 号合法范围（doc：0001~4000；实测 900+ 协议层可写，仅官方软件 GUI 限制）。 */
    const val PLU_NO_MAX = 4000

    /** 回收扫描上界（官方软件全量回收即 0001~0999）。 */
    const val UPLOAD_SCAN_MAX = 999

    /** 标签格式表：官方下发 4 页 × 23 格 = 92 个格式号（0001..0092）。 */
    const val LABEL_TABLE_SIZE = 92
    private const val LABEL_PER_PAGE = 23

    // ---- 帧构造 ----

    fun pluQueryFrame(pluNo: Int): ByteArray =
        ("!0U" + plu4(pluNo) + "A\r\n").toByteArray(Charsets.US_ASCII)

    fun pluWriteFrame(pluNo: Int, digits: String, barcodeSegment: String): ByteArray =
        ("!0V" + plu4(pluNo) + "A" + digits + barcodeSegment + "\r\n").toByteArray(Charsets.US_ASCII)

    fun labelTableFrames(): List<ByteArray> =
        (0 until 4).map { page ->
            val numbers = (1 + page * LABEL_PER_PAGE..page * LABEL_PER_PAGE + LABEL_PER_PAGE)
                .joinToString("") { "%04d".format(it) }
            ("!0L" + "%02d".format(page) + "A" + numbers + "\r\n").toByteArray(Charsets.US_ASCII)
        }

    fun tradeQueryFrame(recordNo: Int): ByteArray =
        ("!0J" + "%04d".format(recordNo) + "A\r\n").toByteArray(Charsets.US_ASCII)

    /** 流水清空（回收结束确认，秤端清空已回收流水）。 */
    fun tradeClearFrame(): ByteArray = "!0HA\r\n".toByteArray(Charsets.US_ASCII)

    // ---- 响应解析 ----

    /** 回收应答：null = 空槽（noplu）；否则返回数字段与条码段。 */
    fun parsePluResponse(resp: String, expectNo: Int): ParsedPlu? {
        if (resp.contains("noplu")) return null
        val prefix = "0u" + plu4(expectNo) + "A"
        if (!resp.startsWith(prefix)) return null
        val body = resp.substringBefore('\u0003').removePrefix(prefix).trimEnd('\r', '\n')
        val bIdx = body.indexOf('B')
        if (bIdx < 0) return null
        return ParsedPlu(digits = body.take(bIdx), barcodeSegment = body.substring(bIdx))
    }

    /** 条码段解析：`B{条码}C..D..E` → 条码字符串（B 与 C 之间，"00" 视为空）。 */
    fun parseBarcode(segment: String): String {
        val b = segment.indexOf('B')
        val c = segment.indexOf('C')
        if (b < 0 || c < 0 || c < b) return ""
        val code = segment.substring(b + 1, c)
        return if (code == "00" || code.all { it == '0' }) "" else code
    }

    /**
     * 流水应答解析：`0j{号}a{数据}` → 数据（CRLF+ETX 已剥离）；
     * 结束标志 = `0j{号}ac`（c 尾缀）返回 null。
     */
    fun parseTradeResponse(resp: String, expectNo: Int): String? {
        val prefix = "0j" + "%04d".format(expectNo) + "a"
        if (!resp.startsWith(prefix)) return null
        // 结束标志 = 数据区恰为 "c"；有数据时数据区原样返回（尾标语义待实样校准）
        val body = resp.substringBefore('\u0003').removePrefix(prefix).trimEnd('\r', '\n')
        return if (body == "c") null else body
    }

    // ---- 73 位数字段 编解码 ----

    fun encodeDigits(plu: PluItem, shopNo: Int): String {
        require(plu.unitPrice > 0) { "大华秤拒收单价为 0 的商品（协议层校验不通过）" }
        val code = "%07d".format(plu.pluNo.coerceIn(0, 9_999_999))
        val price = "%06d".format(plu.unitPrice.coerceIn(0, 999_999))
        val mode = if (plu.weightFlag == 1) '1' else '0'
        val usedBy = "%03d".format(plu.usedByDate.coerceIn(0, 999))
        val dept = "%02d".format(plu.mainGroup.coerceIn(0, 99))
        val shop = "%02d".format(shopNo.coerceIn(0, 99))
        // 代码7 + 单价6 + 方式1 + 特殊6 + 有效期3 + 店号2 + 部门2 + 代号13 + 皮重5 + 标签2 + 打折2 + 时段24 = 73
        return code + price + mode + "000000" + usedBy + shop + dept +
            "0000000000000" + "00000" + "00" + "00" + "000000000000000000000000"
    }

    /** 数字段解码（回收 → PluItem 字段；品名秤端仅存字库代码，文本名称不可还原）。 */
    fun decodeDigits(digits: String): DecodedPlu {
        require(digits.length >= 73) { "PLU 数字段长度异常: ${digits.length}" }
        return DecodedPlu(
            itemCode = digits.substring(0, 7),
            unitPrice = digits.substring(7, 13).toLongOrNull() ?: 0L,
            weightFlag = if (digits[13] == '1') 1 else 0,
            usedByDate = digits.substring(20, 23).toIntOrNull() ?: 0,
            shopNo = digits.substring(23, 25).toIntOrNull() ?: 0,
            deptNo = digits.substring(25, 27).toIntOrNull() ?: 0,
        )
    }

    /** 条码段生成：有条码 → `B{code}CDE`；无 → `B00C00D00E`（均真机验证可写）。 */
    fun barcodeSegment(eanCode: String): String =
        if (eanCode.isBlank()) "B00C00D00E" else "B" + eanCode.trim() + "CDE"

    private fun plu4(no: Int): String = "%04d".format(no.coerceIn(1, PLU_NO_MAX))

    data class ParsedPlu(val digits: String, val barcodeSegment: String)

    data class DecodedPlu(
        val itemCode: String,
        val unitPrice: Long,
        val weightFlag: Int,
        val usedByDate: Int,
        val shopNo: Int,
        val deptNo: Int,
    )
}
