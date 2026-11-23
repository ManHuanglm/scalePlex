package com.scaleplex.driver.impl.yousheng

import com.scaleplex.core.model.PluItem
import java.io.ByteArrayOutputStream

/**
 * 友声 PLU 二进制记录编码器（字节布局待实体秤联调校准，字段单点集中于此）。
 *
 * 记录结构：
 * `pluNo(4 LE) | unitPrice(4 LE) | weightFlag(1) | mainGroup(1) |
 * eanCode(定长13 ASCII) | 品名(定长 [MAX_NAME_BYTES] GBK，空格填充)`
 */
object YouShengPluEncoder {

    const val MAX_NAME_BYTES = 24
    const val EAN_FIELD_BYTES = 13

    private val CHARSETS_GBK = charset("GBK")

    /** 编码 PLU 为友声二进制记录。 */
    fun encode(plu: PluItem): ByteArray {
        val out = ByteArrayOutputStream()
        writeInt32LE(out, plu.pluNo)
        writeInt32LE(out, plu.unitPrice.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        out.write(plu.weightFlag and 0x01)
        out.write(plu.mainGroup and 0xFF)
        out.write(fixedAscii(plu.eanCode, EAN_FIELD_BYTES))
        out.write(fixedName(plu.name.ifBlank { plu.nameEn }))
        return out.toByteArray()
    }

    /** 定长 GBK 品名：超长截断防半汉字，不足右补空格。 */
    private fun fixedName(name: String): ByteArray {
        val truncated = name.toByteArray(CHARSETS_GBK).take(MAX_NAME_BYTES).toByteArray()
        val padded = ByteArray(MAX_NAME_BYTES) { 0x20 }
        truncated.copyInto(padded)
        return padded
    }

    private fun fixedAscii(value: String, length: Int): ByteArray =
        value.toByteArray(Charsets.US_ASCII).copyOf(length)

    private fun writeInt32LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }
}
