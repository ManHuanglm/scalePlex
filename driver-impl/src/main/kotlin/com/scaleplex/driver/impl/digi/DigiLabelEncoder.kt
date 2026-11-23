package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import java.io.ByteArrayOutputStream

/**
 * DIGI SM 系列标签格式记录编码器：将 [LabelTemplate] 编码为 DIGI 标签格式二进制记录。
 *
 * 记录结构（待实体秤联调校准）：
 * `formatNo(1) | 宽点阵(2 LE) | 高点阵(2 LE) | 元素数(1) |
 * 每元素: kindCode(1) + x(2) + y(2) + w(2) + h(2) + font(1) + align(1) + 文本(len 1 + GBK)`
 *
 * 坐标按热敏打印标准分辨率 8 dots/mm 换算；LOGO 元素首期不支持，编码时跳过。
 */
object DigiLabelEncoder {

    /** 热敏打印头分辨率：8 dots/mm。 */
    const val DOTS_PER_MM = 8

    /** DIGI 标签格式编号范围（对应 DigiTables.LABEL1_FORMATS 1..24）。 */
    const val FORMAT_NO_MIN = 1
    const val FORMAT_NO_MAX = 24

    private val CHARSETS_GBK = charset("GBK")

    /** 元素类型 → DIGI kindCode（待联调校准映射表）。 */
    fun kindCode(kind: LabelElementKind): Int? = when (kind) {
        LabelElementKind.TEXT -> 0x01
        LabelElementKind.PLU_NAME -> 0x02
        LabelElementKind.SHOP_NAME -> 0x03
        LabelElementKind.BARCODE -> 0x04
        LabelElementKind.QRCODE -> 0x05
        LabelElementKind.WEIGHT -> 0x06
        LabelElementKind.UNIT_PRICE -> 0x07
        LabelElementKind.TOTAL_PRICE -> 0x08
        LabelElementKind.DATE -> 0x09
        LabelElementKind.LOGO -> null
    }

    /** 模板中可下发的元素（过滤 LOGO 等不支持类型）。 */
    fun supportedElements(template: LabelTemplate): List<LabelElement> =
        template.elements.filter { kindCode(it.kind) != null }

    /**
     * 编码完整标签格式记录。[formatNo] 取值 1..24（对应 DIGI 标签格式表 T1..F8），
     * 超出范围时收敛到区间内。
     */
    fun encode(template: LabelTemplate, formatNo: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(formatNo.coerceIn(FORMAT_NO_MIN, FORMAT_NO_MAX))
        writeInt16LE(out, template.widthMm * DOTS_PER_MM)
        writeInt16LE(out, template.heightMm * DOTS_PER_MM)

        val elements = supportedElements(template)
        out.write(elements.size and 0xFF)
        for (element in elements) {
            writeElement(out, element)
        }
        return out.toByteArray()
    }

    private fun writeElement(out: ByteArrayOutputStream, element: LabelElement) {
        out.write(kindCode(element.kind) ?: return)
        writeInt16LE(out, mmToDots(element.xMm))
        writeInt16LE(out, mmToDots(element.yMm))
        writeInt16LE(out, mmToDots(element.widthMm))
        writeInt16LE(out, mmToDots(element.heightMm))
        out.write(element.fontSizePt.coerceIn(0, 0xFF))
        out.write(element.align and 0xFF)
        val text = element.text.toByteArray(CHARSETS_GBK)
        out.write(text.size and 0xFF)
        out.write(text)
    }

    /** 毫米 → 点阵，负值收敛为 0。 */
    fun mmToDots(mm: Float): Int = (mm.coerceAtLeast(0f) * DOTS_PER_MM).toInt()

    private fun writeInt16LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}
