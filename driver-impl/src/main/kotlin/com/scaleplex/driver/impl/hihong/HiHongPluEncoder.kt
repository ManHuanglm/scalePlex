package com.scaleplex.driver.impl.hihong

import com.scaleplex.core.model.PluItem
import java.io.ByteArrayOutputStream

/**
 * 辉宏 PLU 二进制记录编码器（字节布局待实体秤联调校准，字段单点集中于此）。
 *
 * 记录结构：
 * `pluNo(4 LE) | flags(1, bit0=计件) | unitPrice(4 LE) | mainGroup(2 LE) |
 * label1Format(1) | tare(2 LE) | eanCode(定长13 ASCII) | 品名(len 1 + GBK)`
 */
object HiHongPluEncoder {

    /** 品名 GBK 最大字节数，超出截断防半汉字。 */
    const val MAX_NAME_BYTES = 40
    const val EAN_FIELD_BYTES = 13

    private val CHARSETS_GBK = charset("GBK")

    /** 编码 PLU 为辉宏二进制记录。 */
    fun encode(plu: PluItem): ByteArray {
        val out = ByteArrayOutputStream()
        writeInt32LE(out, plu.pluNo)
        out.write(plu.weightFlag and 0x01)
        writeInt32LE(out, plu.unitPrice.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        writeInt16LE(out, plu.mainGroup)
        out.write(plu.label1Format and 0xFF)
        writeInt16LE(out, plu.tare)
        out.write(fixedAscii(plu.eanCode, EAN_FIELD_BYTES))
        writeName(out, plu.name.ifBlank { plu.nameEn })
        return out.toByteArray()
    }

    /** GBK 截断，保证不出现半个汉字。 */
    private fun writeName(out: ByteArrayOutputStream, name: String) {
        val bytes = name.toByteArray(CHARSETS_GBK).take(MAX_NAME_BYTES).toByteArray()
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    private fun fixedAscii(value: String, length: Int): ByteArray =
        value.toByteArray(Charsets.US_ASCII).copyOf(length)

    private fun writeInt16LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun writeInt32LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }
}
