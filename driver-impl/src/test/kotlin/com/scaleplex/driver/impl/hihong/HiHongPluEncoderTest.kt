package com.scaleplex.driver.impl.hihong

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiHongPluEncoderTest {

    @Test
    fun `record layout follows little endian binary fields`() {
        val record = HiHongPluEncoder.encode(
            PluItem(
                pluNo = 0x01020304,
                name = "苹果",
                unitPrice = 1250,
                mainGroup = 1,
                label1Format = 3,
                tare = 30,
                eanCode = "69012345",
            ),
        )
        // pluNo 4LE
        assertEquals(0x04, record[0].toInt() and 0xFF)
        assertEquals(0x03, record[1].toInt() and 0xFF)
        assertEquals(0x02, record[2].toInt() and 0xFF)
        assertEquals(0x01, record[3].toInt() and 0xFF)
        // flags: 计重 = 0
        assertEquals(0, record[4].toInt() and 0xFF)
        // price 1250 = 0x04E2 LE
        assertEquals(0xE2, record[5].toInt() and 0xFF)
        assertEquals(0x04, record[6].toInt() and 0xFF)
        assertEquals(0x00, record[7].toInt() and 0xFF)
        assertEquals(0x00, record[8].toInt() and 0xFF)
        // mainGroup 1 (2 LE)
        assertEquals(0x01, record[9].toInt() and 0xFF)
        assertEquals(0x00, record[10].toInt() and 0xFF)
        // label1Format
        assertEquals(3, record[11].toInt() and 0xFF)
    }

    @Test
    fun `count flag sets bit0`() {
        val record = HiHongPluEncoder.encode(PluItem(pluNo = 1, weightFlag = 1))
        assertEquals(1, record[4].toInt() and 0x01)
    }

    @Test
    fun `ean field is fixed 13 bytes ascii padded`() {
        val record = HiHongPluEncoder.encode(PluItem(pluNo = 1, eanCode = "6901"))
        // 头部 14 字节（pluNo4+flag1+price4+group2+label1+tare2）后是定长 13 字节条码域
        val eanOffset = EAN_OFFSET
        val ean = record.copyOfRange(eanOffset, eanOffset + HiHongPluEncoder.EAN_FIELD_BYTES)
        assertEquals("6901\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(ean, Charsets.US_ASCII))
    }

    @Test
    fun `name gbk truncates without half character`() {
        val record = HiHongPluEncoder.encode(PluItem(pluNo = 1, name = "超长商品名称测试超长商品名称测试超长商品名称测试超长商品名称"))
        val nameLen = record[EAN_OFFSET + HiHongPluEncoder.EAN_FIELD_BYTES].toInt() and 0xFF
        assertTrue(nameLen <= HiHongPluEncoder.MAX_NAME_BYTES)
        assertEquals(0, nameLen % 2) // 每个汉字 GBK 2 字节，截断后不出现半个汉字
    }

    @Test
    fun `name falls back to english when chinese blank`() {
        val record = HiHongPluEncoder.encode(PluItem(pluNo = 1, name = "", nameEn = "Apple"))
        val nameLen = record[EAN_OFFSET + HiHongPluEncoder.EAN_FIELD_BYTES].toInt() and 0xFF
        assertEquals("Apple", String(record, EAN_OFFSET + HiHongPluEncoder.EAN_FIELD_BYTES + 1, nameLen, Charsets.US_ASCII))
    }

    /** 条码域在记录中的起始偏移：4+1+4+2+1+2 = 14。 */
    private val EAN_OFFSET = 14
}
