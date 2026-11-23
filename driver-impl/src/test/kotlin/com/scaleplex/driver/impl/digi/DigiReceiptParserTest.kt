package com.scaleplex.driver.impl.digi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiReceiptParserTest {

    /** 依 SM100_RTRECEIPT_DETAIL.dsc 字段表手工构造的一行明细（字段顺序与字节长一致）。 */
    private fun buildLine(
        rectNo: Long = 1,
        pluNo: Long = 12,
        weight: Int = 250,
        unitPrice: Int = 3125,
        actPrice: Int = 3125,
        date: String = "20260101",
        time: String = "1015",
    ): String {
        val sb = StringBuilder()
        // RECT_No (2 BCD)
        sb.append("%04d".format(rectNo))
        // ITEM_ID (2 BCD)
        sb.append("%04d".format(1))
        // RECT_RecSize (2 HEX)
        sb.append("7C00")
        // D_ItemST (4 HEX)
        sb.append("00000000")
        // D_PLUNo (4 BCD)
        sb.append("%08d".format(pluNo))
        // D_MGNo (4 BCD)
        sb.append("09970000")
        // D_QTY (4 HEX)
        sb.append("00000000")
        // D_Weight (4 HEX_WEIGHT)
        sb.append("%08X".format(weight))
        // D_UPrice / D_UPriceADis / D_UPDisAMT / D_NPrice / D_ACTPrice (各4 HEX_PRICE)
        repeat(4) { sb.append("%08X".format(unitPrice)) }
        sb.append("%08X".format(actPrice))
        // D_PriceAFDT..D_IPriceAFTRUND (5×4 HEX)
        repeat(5) { sb.append("00000000") }
        // D_TAXRNo(1) DUMMY(1) D_TAXR(2) D_TAXVAL(4) D_TAXVALAFSUBTD(4)
        sb.append("00").append("00").append("0000").append("00000000").append("00000000")
        // D_SPPriceNo(1) DUMMY1(1) D_ScaleNo(3) DUMMY2(1)
        sb.append("00").append("00").append("000000").append("00")
        // D_VALPoint1(4) D_PricePoint1(4) D_VALPoint2(4) D_PricePoint2(4) D_TareVAL(4)
        repeat(5) { sb.append("00000000") }
        // D_SPDISCStat(1) D_MDStat(1)
        sb.append("00").append("00")
        // D_JINRecBWeight (4 BCD_DATE) D_JINRecBStat (2 BCD_TIME)
        sb.append(date)
        sb.append(time)
        // D_PUBFWeight(4) D_PUBBonusP(4) D_PUBFQTY(4)
        repeat(3) { sb.append("00000000") }
        // D_TRCBCNT..D_TRCutCNT (6×2 HEX)
        repeat(6) { sb.append("0000") }
        return sb.toString()
    }

    @Test
    fun `parses receipt line into trade record`() {
        val record = DigiReceiptParser.parseLines(listOf(buildLine()), deviceId = 3).single()
        assertEquals(3L, record.deviceId)
        assertEquals(1L, record.recordNo)
        assertEquals(12, record.pluNo)
        assertEquals(250, record.weight)
        assertEquals(3125L, record.unitPrice)
        assertEquals(3125L, record.amount)
        assertEquals(2026, record.timestamp?.year)
        assertEquals(10, record.timestamp?.hour)
        assertEquals(15, record.timestamp?.minute)
    }

    @Test
    fun `uses plu name mapping when provided`() {
        val record = DigiReceiptParser.parseLines(listOf(buildLine(pluNo = 12)), deviceId = 1, pluNames = mapOf(12 to "苹果")).single()
        assertEquals("苹果", record.pluName)
    }

    @Test
    fun `falls back amount to unit price when act price zero`() {
        val record = DigiReceiptParser.parseLines(listOf(buildLine(actPrice = 0)), deviceId = 1).single()
        assertEquals(3125L, record.amount)
    }

    @Test
    fun `skips non hex and short lines`() {
        val records = DigiReceiptParser.parseLines(
            listOf("", "NOTHEX", "ABC", buildLine(), buildLine(rectNo = 2)),
            deviceId = 2,
        )
        assertEquals(2, records.size)
        assertEquals(1L, records[0].recordNo)
        assertEquals(2L, records[1].recordNo)
    }

    @Test
    fun `hex decoder rejects odd length`() {
        assertNull(DigiReceiptParser.hexToBytes("ABC"))
        assertTrue(DigiReceiptParser.hexToBytes("0AFF")!!.contentEquals(byteArrayOf(0x0A, 0xFF.toByte())))
    }

    @Test
    fun `bcd decodes decimal digits`() {
        assertEquals(123456L, DigiReceiptParser.bcd(byteArrayOf(0x12, 0x34, 0x56)))
        assertEquals(-1L, DigiReceiptParser.bcd(byteArrayOf(0x1A.toByte())))
    }
}
