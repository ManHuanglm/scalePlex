package com.scaleplex.driver.impl.yousheng

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouShengPluEncoderTest {

    /** 记录头长度：pluNo4 + price4 + flag1 + group1 = 10。 */
    private val HEADER_BYTES = 10

    @Test
    fun `record layout follows little endian binary fields`() {
        val record = YouShengPluEncoder.encode(
            PluItem(pluNo = 0x01020304, unitPrice = 1250, mainGroup = 3, weightFlag = 1),
        )
        // pluNo 4LE
        assertEquals(0x04, record[0].toInt() and 0xFF)
        assertEquals(0x03, record[1].toInt() and 0xFF)
        assertEquals(0x02, record[2].toInt() and 0xFF)
        assertEquals(0x01, record[3].toInt() and 0xFF)
        // price 1250 = 0x04E2 LE
        assertEquals(0xE2, record[4].toInt() and 0xFF)
        assertEquals(0x04, record[5].toInt() and 0xFF)
        // weightFlag
        assertEquals(1, record[8].toInt() and 0xFF)
        // mainGroup
        assertEquals(3, record[9].toInt() and 0xFF)
    }

    @Test
    fun `ean field is fixed 13 bytes ascii padded`() {
        val record = YouShengPluEncoder.encode(PluItem(pluNo = 1, eanCode = "6901"))
        val ean = record.copyOfRange(HEADER_BYTES, HEADER_BYTES + YouShengPluEncoder.EAN_FIELD_BYTES)
        assertEquals("6901\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(ean, Charsets.US_ASCII))
    }

    @Test
    fun `name field is fixed size space padded`() {
        val record = YouShengPluEncoder.encode(PluItem(pluNo = 1, name = "苹果"))
        val nameOffset = HEADER_BYTES + YouShengPluEncoder.EAN_FIELD_BYTES
        val nameBytes = record.copyOfRange(nameOffset, nameOffset + YouShengPluEncoder.MAX_NAME_BYTES)
        // "苹果" GBK 4 字节 + 20 空格填充
        assertEquals(0x20, nameBytes[YouShengPluEncoder.MAX_NAME_BYTES - 1].toInt())
        assertTrue(nameBytes.size == YouShengPluEncoder.MAX_NAME_BYTES)
        assertEquals("苹果", String(nameBytes, 0, 4, charset("GBK")).trim())
    }

    @Test
    fun `long name truncates without half character`() {
        val name = "超长商品名称测试超长商品名称测试超长商品名称"
        val record = YouShengPluEncoder.encode(PluItem(pluNo = 1, name = name))
        val nameOffset = HEADER_BYTES + YouShengPluEncoder.EAN_FIELD_BYTES
        val nameBytes = record.copyOfRange(nameOffset, nameOffset + YouShengPluEncoder.MAX_NAME_BYTES)
        val validBytes = nameBytes.takeWhile { it != 0x20.toByte() }.size
        assertEquals(0, validBytes % 2) // 不出现半个汉字
    }

    @Test
    fun `negative price clamped to zero`() {
        val record = YouShengPluEncoder.encode(PluItem(pluNo = 1, unitPrice = -5))
        assertEquals(0, record[4].toInt() and 0xFF)
        assertEquals(0, record[5].toInt() and 0xFF)
    }

    @Test
    fun `encode is deterministic`() {
        val plu = PluItem(pluNo = 2, name = "测试", unitPrice = 500)
        assertTrue(
            YouShengPluEncoder.encode(plu).contentEquals(YouShengPluEncoder.encode(plu)),
        )
    }
}
