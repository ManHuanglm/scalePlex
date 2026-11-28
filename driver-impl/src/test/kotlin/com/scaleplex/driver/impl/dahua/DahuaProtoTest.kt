package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大华协议帧编解码回归（真机标定 2026-11-28，TMA07-PLU说明.doc 字段表）。
 */
class DahuaProtoTest {

    /** 真机回收样本：PLU1 = 测试一 / 20.00 元 / 计重→称重位0 / 有效期 8 / 店号 3。 */
    private val plu1Digits = "2281080002000100000000803000" + "0".repeat(45)

    /** 真机回收样本：PLU900 条码段（8 位条码，关联段空）。 */
    private val bar900 = "B18664252CDE"

    @Test
    fun `digits length is 73`() {
        assertEquals(73, plu1Digits.length)
    }

    @Test
    fun `encode digits matches layout`() {
        val plu = PluItem(
            pluNo = 9,
            name = "",
            weightFlag = 1,
            unitPrice = 9000,
            usedByDate = 1,
            eanCode = "",
            mainGroup = 1,
        )
        val digits = DahuaProto.encodeDigits(plu, shopNo = 1)
        assertEquals(73, digits.length)
        assertEquals("0000009", digits.substring(0, 7)) // 商品代码 = PLU 号补零
        assertEquals("009000", digits.substring(7, 13)) // 单价 90.00 元 = 9000 分
        assertEquals('1', digits[13])                   // 计件
        assertEquals("001", digits.substring(20, 23))   // 有效期 1 天
        assertEquals("01", digits.substring(23, 25))    // 店号
        assertEquals("01", digits.substring(25, 27))    // 部门号
    }

    @Test
    fun `encode rejects zero price`() {
        val plu = PluItem(pluNo = 1, unitPrice = 0)
        try {
            DahuaProto.encodeDigits(plu, shopNo = 1)
            throw AssertionError("单价 0 应被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("单价"))
        }
    }

    @Test
    fun `decode digits round trip`() {
        val decoded = DahuaProto.decodeDigits(plu1Digits)
        assertEquals("2281080", decoded.itemCode)
        assertEquals(2000L, decoded.unitPrice) // 002000 = 20.00 元
        assertEquals(1, decoded.weightFlag) // PLU1 = 计件（软件界面截图一致）
        assertEquals(8, decoded.usedByDate)
        assertEquals(3, decoded.shopNo)
        assertEquals(0, decoded.deptNo)
    }

    @Test
    fun `plu query frame`() {
        assertEquals("!0U0001A\r\n", String(DahuaProto.pluQueryFrame(1), Charsets.US_ASCII))
        assertEquals("!0U0999A\r\n", String(DahuaProto.pluQueryFrame(999), Charsets.US_ASCII))
    }

    @Test
    fun `plu write frame`() {
        val frame = String(
            DahuaProto.pluWriteFrame(901, plu1Digits, bar900),
            Charsets.US_ASCII,
        )
        assertEquals("!0V0901A${plu1Digits}${bar900}\r\n", frame)
    }

    @Test
    fun `parse plu response with data`() {
        val frame = "0u0001A$plu1Digits${bar900}\r\n\u0003"
        val parsed = DahuaProto.parsePluResponse(frame, 1)!!
        assertEquals(plu1Digits, parsed.digits)
        assertEquals(bar900, parsed.barcodeSegment)
        assertEquals("18664252", DahuaProto.parseBarcode(parsed.barcodeSegment))
    }

    @Test
    fun `parse plu response noplu`() {
        assertNull(DahuaProto.parsePluResponse("0u0902Anoplu\r\n\u0003", 902))
    }

    @Test
    fun `parse barcode empty variants`() {
        assertEquals("", DahuaProto.parseBarcode("B00C00D00E"))
        assertEquals("", DahuaProto.parseBarcode("B0000000000000CDE"))
        assertEquals("186642524093", DahuaProto.parseBarcode("B186642524093C037503750375D037603760376E"))
    }

    @Test
    fun `trade query and clear frames`() {
        assertEquals("!0J0001A\r\n", String(DahuaProto.tradeQueryFrame(1), Charsets.US_ASCII))
        assertEquals("!0HA\r\n", String(DahuaProto.tradeClearFrame(), Charsets.US_ASCII))
    }

    @Test
    fun `parse trade response`() {
        val data = "26263009480003000101000002000000200000020000000200553701bc"
        assertEquals(data, DahuaProto.parseTradeResponse("0j0001a$data\r\n\u0003", 1))
        // 结束标志：0j0001ac
        assertNull(DahuaProto.parseTradeResponse("0j0001ac\r\n\u0003", 1))
    }

    @Test
    fun `label table frames cover 1 to 92`() {
        val frames = DahuaProto.labelTableFrames()
        assertEquals(4, frames.size)
        val all = frames.joinToString("") { String(it, Charsets.US_ASCII) }
        assertTrue(all.contains("!0L00A000100020003"))
        assertTrue(all.contains("009000910092")) // 第 4 页末尾 = 格式号 92
        frames.forEach { assertTrue(String(it, Charsets.US_ASCII).endsWith("\r\n")) }
    }
}
