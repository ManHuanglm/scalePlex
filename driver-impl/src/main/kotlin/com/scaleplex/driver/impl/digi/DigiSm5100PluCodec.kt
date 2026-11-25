package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.model.PluUnit
import java.nio.charset.Charset

/**
 * 寺冈 SM-5100 系（b 族：SM-110N STE57+ / SM-120 / SM-5100）PLU 结构编解码。
 *
 * 依据 `information/寺冈开发包(接口)/SM5100DLL&Demo`：
 * - Delphi `PLU.pas` 的 `LabelPLU` 记录（SMScaleAPI.dll `SendLPlu/RDPluFAsLabel` 的参数）；
 * - `SMScaleAPI.xls`：端口 = 2000 + IP 最后一段；返回 0=无错误 -1=错误。
 *
 * Delphi record 默认对齐布局（Integer 4 对齐、char 数组 1 对齐），总大小 5284 字节：
 * ```
 * PLUNo/WeightUnit/UPriceOverride/UPrice/LabelFormat1/LabelFormat2/BarFormat/EANFlags  (8×4=32)
 * EAN_Data { ItemCode char[10]; pad2; BarCodeType int; BarRightData int }             (20)
 * MG/SBDate/UBDate/Cost/Tare/QTY/TaxNo/Traceability/TraceabilityL                     (9×4=36)
 * MBarcode1 { int; char[49]; pad; int }                                              (60)
 * Discount { 10×int }                                                                (40)
 * Commodity[4]  { TwsFontSize int; TxtValue char[100] }  ×4                          (416)
 * Ingredient[15] ×15                                                                 (1560)
 * SPMessage[30]  ×30                                                                 (3120)
 * ```
 * 价格/日期为整数（隐含小数位由秤端设置）；文本 GBK。
 *
 * 真机校准（SM-110N STE57+，192.168.0.55）：秤端整数一律**小端**（Delphi/x86 原生布局），
 * 大端编码会导致秤端解析出非法 PLU 号而静默丢弃（SendLPlu 仍返回 0x06）。
 */
object DigiSm5100PluCodec {

    const val STRUCT_SIZE = 5284

    private val GBK: Charset = Charset.forName("GBK")

    // 字段偏移（Delphi aligned 布局）
    private const val OFF_PLU_NO = 0
    private const val OFF_WEIGHT_UNIT = 4
    private const val OFF_UPRICE_OVERRIDE = 8
    private const val OFF_UPRICE = 12
    private const val OFF_LABEL1 = 16
    private const val OFF_LABEL2 = 20
    private const val OFF_BAR_FORMAT = 24
    private const val OFF_EAN_FLAGS = 28
    private const val OFF_EAN_DATA = 32          // ItemCode[10] + pad2 + BarCodeType + BarRightData
    private const val OFF_MG = 52
    private const val OFF_SB_DATE = 56           // 销售期限（天数）
    private const val OFF_UB_DATE = 60           // 保质期（天数）
    private const val OFF_COST = 64
    private const val OFF_TARE = 68
    private const val OFF_QTY = 72
    private const val OFF_TAX_NO = 76
    private const val OFF_MBARCODE = 88          // int + char[49] + pad3 + int
    private const val OFF_DISCOUNT = 148         // 10 × int
    private const val OFF_COMMODITY = 188        // 4 × SM_ASCII_TXT(104)
    private const val OFF_INGREDIENT = 604       // 15 × 104
    private const val OFF_SP_MESSAGE = 2164      // 30 × 104

    private const val TXT_ENTRY_SIZE = 104       // int(4) + char[100]

    /** PluItem → LabelPLU 结构字节（[STRUCT_SIZE]）。 */
    fun encode(p: PluItem): ByteArray {
        val b = ByteArray(STRUCT_SIZE)
        putInt(b, OFF_PLU_NO, p.pluNo)
        // WeightUnit：0=计重 1=计数；PCS（计件）→1
        putInt(b, OFF_WEIGHT_UNIT, if (p.weightFlag == 1 || PluUnit.fromId(p.unit) == PluUnit.PCS) 1 else 0)
        putInt(b, OFF_UPRICE_OVERRIDE, 1) // 允许秤端临时变价
        putInt(b, OFF_UPRICE, p.unitPrice.coerceAtLeast(0).toInt())
        putInt(b, OFF_LABEL1, p.label1Format)
        putInt(b, OFF_LABEL2, p.label2Format)
        putInt(b, OFF_BAR_FORMAT, p.barcodeFormat)
        putInt(b, OFF_EAN_FLAGS, p.barcodeType)
        // EAN_Data：ItemCode char[10]（项目代码）+ pad2 + BarCodeType + BarRightData
        val itemCode = p.eanCode.toByteArray(GBK).copyOf(10)
        System.arraycopy(itemCode, 0, b, OFF_EAN_DATA, 10)
        putInt(b, OFF_EAN_DATA + 12, p.barcodeType)
        putInt(b, OFF_EAN_DATA + 16, p.barRightDataType)
        putInt(b, OFF_MG, p.mainGroup)
        putInt(b, OFF_SB_DATE, p.sellByDate.coerceIn(0, 999))
        putInt(b, OFF_UB_DATE, p.effectiveShelfLifeDays.coerceIn(0, 999))
        putInt(b, OFF_COST, p.cost.coerceAtLeast(0).toInt())
        putInt(b, OFF_TARE, p.tare.coerceAtLeast(0))
        putInt(b, OFF_QTY, p.qty.coerceAtLeast(0))
        putInt(b, OFF_TAX_NO, p.taxRateNo)
        // MBarcode1 / Discount 暂不填写（全 0 = 未启用）
        // 品名兜底：commodityLines 为空而 name 非空时，name 作为品名首行
        val commodity = p.commodityLines.ifEmpty {
            if (p.displayName.isNotBlank()) listOf(PluTextLine(text = p.displayName)) else emptyList()
        }
        putTextEntries(b, OFF_COMMODITY, 4, commodity)
        putTextEntries(b, OFF_INGREDIENT, 15, p.ingredientLines)
        putTextEntries(b, OFF_SP_MESSAGE, 30, p.specialMsgLines)
        return b
    }

    /** LabelPLU 结构字节 → PluItem（PLU 回收）。 */
    fun parse(data: ByteArray): PluItem? {
        if (data.size < STRUCT_SIZE) return null
        val pluNo = getInt(data, OFF_PLU_NO)
        if (pluNo <= 0) return null
        val commodity = readTextEntries(data, OFF_COMMODITY, 4)
        val ingredients = readTextEntries(data, OFF_INGREDIENT, 15)
        val spMessages = readTextEntries(data, OFF_SP_MESSAGE, 30)
        val weightUnit = getInt(data, OFF_WEIGHT_UNIT)
        val itemCode = String(data, OFF_EAN_DATA, 10, GBK).trimEnd('\u0000', ' ')
        // 空闲槽/系统标记记录过滤（真机校准）：秤端删除 PLU 后会显露 pluNo≈999997 的
        // 空槽标记（无品名、无项目代码、价格 0），非用户数据
        if (commodity.none { it.text.isNotBlank() } && itemCode.isBlank()) return null
        return PluItem(
            pluNo = pluNo,
            weightFlag = weightFlagOf(weightUnit),
            unit = if (weightUnit == 1) PluUnit.PCS.id else PluUnit.KG.id,
            // UPriceOverride 语义为秤端允许变价，不映射
            unitPrice = getInt(data, OFF_UPRICE).toLong().coerceAtLeast(0),
            label1Format = getInt(data, OFF_LABEL1),
            label2Format = getInt(data, OFF_LABEL2),
            barcodeFormat = getInt(data, OFF_BAR_FORMAT),
            barcodeType = getInt(data, OFF_EAN_DATA + 12),
            barRightDataType = getInt(data, OFF_EAN_DATA + 16),
            eanCode = itemCode,
            mainGroup = getInt(data, OFF_MG),
            sellByDate = getInt(data, OFF_SB_DATE),
            // 回收端保质期即天数
            usedByDate = getInt(data, OFF_UB_DATE),
            cost = getInt(data, OFF_COST).toLong().coerceAtLeast(0),
            tare = getInt(data, OFF_TARE),
            qty = getInt(data, OFF_QTY),
            taxRateNo = getInt(data, OFF_TAX_NO),
            name = commodity.firstOrNull()?.text.orEmpty(),
            commodityLines = commodity.filter { it.text.isNotBlank() },
            ingredientLines = ingredients.filter { it.text.isNotBlank() },
            specialMsgLines = spMessages.filter { it.text.isNotBlank() },
        )
    }

    private fun weightFlagOf(weightUnit: Int): Int = if (weightUnit == 1) 1 else 0

    private fun putInt(b: ByteArray, off: Int, v: Int) {
        // 小端（Delphi/x86 原生；真机校准，见类注释）
        b[off] = v.toByte()
        b[off + 1] = (v shr 8).toByte()
        b[off + 2] = (v shr 16).toByte()
        b[off + 3] = (v shr 24).toByte()
    }

    private fun getInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    /** 写文本区（SM_ASCII_TXT：TwsFontSize int + GBK 文本 char[100]，每条 [TXT_ENTRY_SIZE]）。 */
    private fun putTextEntries(b: ByteArray, base: Int, count: Int, lines: List<PluTextLine>) {
        for (i in 0 until count) {
            val off = base + i * TXT_ENTRY_SIZE
            val line = lines.getOrNull(i)
            val text = line?.text.orEmpty()
            if (text.isBlank()) continue
            putInt(b, off, line?.fontNo?.takeIf { it > 0 } ?: DEFAULT_FONT)
            val bytes = text.toByteArray(GBK)
            val len = minOf(bytes.size, 100)
            System.arraycopy(bytes, 0, b, off + 4, len)
        }
    }

    private fun readTextEntries(b: ByteArray, base: Int, count: Int): List<PluTextLine> {
        val out = mutableListOf<PluTextLine>()
        for (i in 0 until count) {
            val off = base + i * TXT_ENTRY_SIZE
            val font = getInt(b, off)
            // 文本长度：GBK 解码至首个 0x00（char[100] 以 \0 结尾）
            var end = off + 4
            val stop = off + TXT_ENTRY_SIZE
            while (end < stop && b[end] != 0.toByte()) end++
            val text = String(b, off + 4, end - (off + 4), GBK)
            if (text.isNotBlank()) out += PluTextLine(fontNo = font, text = text)
        }
        return out
    }

    /** 默认字库号（SM-100 侧文档同源，待真机校准）。 */
    const val DEFAULT_FONT = 15
}
