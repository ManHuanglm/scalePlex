package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 大华 PLU / 标签记录编码器：将 [PluItem] 编码为逗号分隔的 ASCII 字段串。
 *
 * SETPLU 字段顺序（待联调校准）：
 * `pluNo,品名,品名英,计价方式,单价,条码,主组,皮重,标签格式1,保质期`
 *
 * 品名等文本字段去除逗号与换行，避免破坏帧结构；中文按 GBK 传输。
 */
object DahuaPluEncoder {

    /** 价格小数位默认 2 位（分→元展示）。 */
    const val DEFAULT_PRICE_DECIMALS = 2

    private val CHARSETS_GBK = charset("GBK")
    private val dateFmt = SimpleDateFormat("yyMMdd", Locale.ROOT)

    /** 编码 PLU 为 SETPLU 命令的字段列表（不含命令字本身）。 */
    fun encodeFields(plu: PluItem, priceDecimals: Int = DEFAULT_PRICE_DECIMALS): List<String> = listOf(
        plu.pluNo.toString(),
        sanitize(plu.name),
        sanitize(plu.nameEn),
        plu.weightFlag.toString(),
        formatMinor(plu.unitPrice, priceDecimals),
        sanitize(plu.eanCode),
        plu.mainGroup.toString(),
        plu.tare.toString(),
        plu.label1Format.toString(),
        plu.usedByDate.toString(),
    )

    /**
     * 编码标签模板为 LABEL 命令字段：`<id>,<width>,<height>,<元素定义;元素定义...>`。
     * 元素定义：`kindCode,x,y,w,h,text`；不支持的下发元素跳过。
     */
    fun encodeLabel(template: LabelTemplate): List<String> = listOf(
        template.id.toString(),
        template.widthMm.toString(),
        template.heightMm.toString(),
        template.elements.joinToString(";") { encodeElement(it) },
    )

    /** 元素类型 → 协议 kindCode（待联调校准映射表）。 */
    private fun kindCode(kind: LabelElementKind): String? = when (kind) {
        LabelElementKind.TEXT -> "T"
        LabelElementKind.BARCODE -> "B"
        LabelElementKind.QRCODE -> "Q"
        LabelElementKind.WEIGHT -> "W"
        LabelElementKind.UNIT_PRICE -> "P"
        LabelElementKind.TOTAL_PRICE -> "A"
        LabelElementKind.DATE -> "D"
        LabelElementKind.PLU_NAME -> "N"
        LabelElementKind.SHOP_NAME -> "S"
        LabelElementKind.LOGO -> null
    }

    private fun encodeElement(e: LabelElement): String {
        val code = kindCode(e.kind) ?: return ""
        val text = if (e.text.isNotBlank()) e.text else kindDefaultText(e.kind)
        return listOf(
            code,
            trim(e.xMm),
            trim(e.yMm),
            trim(e.widthMm),
            trim(e.heightMm),
            sanitize(text),
        ).joinToString(",")
    }

    private fun kindDefaultText(kind: LabelElementKind): String = when (kind) {
        LabelElementKind.WEIGHT -> "W"
        LabelElementKind.UNIT_PRICE -> "P"
        LabelElementKind.TOTAL_PRICE -> "A"
        LabelElementKind.DATE -> dateFmt.format(Date())
        else -> ""
    }

    /** 以"元"为单位输出价格字符串，负值视为 0。 */
    fun formatMinor(minor: Long, decimals: Int = DEFAULT_PRICE_DECIMALS): String {
        val safeMinor = if (minor < 0) 0L else minor
        val scale = pow10(decimals)
        val whole = safeMinor / scale
        val frac = (safeMinor % scale).toString().padStart(decimals, '0')
        return if (decimals == 0) whole.toString() else "$whole.$frac"
    }

    /** 清理文本字段：逗号→全角、CR/LF→空格，防止破坏 ASCII 帧。 */
    fun sanitize(text: String): String = text
        .replace(',', '，')
        .replace('\r', ' ')
        .replace('\n', ' ')

    /** GBK 编码字节长度（测试断言用）。 */
    fun gbkLength(text: String): Int = text.toByteArray(CHARSETS_GBK).size

    private fun trim(v: Float): String {
        val rounded = (v * 10).toInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }

    private fun pow10(n: Int): Long {
        var result = 1L
        repeat(maxOf(0, n)) { result *= 10 }
        return result
    }
}
