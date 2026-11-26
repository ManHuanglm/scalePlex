package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.model.PluUnit
import com.scaleplex.core.model.ShelfLifeUnit
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SM-5100（b 族）LabelPLU 结构编解码测试 —— 布局依据 Delphi `PLU.pas`（默认对齐，总 5284 字节）。
 *  整数小端（真机 SM-110N 校准，见 [DigiSm5100PluCodec] 类注释）。 */
class DigiSm5100PluCodecTest {

    private val GBK: Charset = Charset.forName("GBK")

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    @Test
    fun `encodes core fields at delphi aligned offsets`() {
        val plu = PluItem(
            pluNo = 1234,
            eanCode = "100001",
            mainGroup = 3,
            unitPrice = 2350L,
            weightFlag = 0,
            label1Format = 5,
            label2Format = 6,
            barcodeFormat = 0x21,
            barcodeType = 0,
            barRightDataType = 2,
            usedByDate = 30,
            sellByDate = 45,
            cost = 1200L,
            tare = 100,
            qty = 0,
            taxRateNo = 1,
            name = "特级苹果",
        )
        val b = DigiSm5100PluCodec.encode(plu)
        assertEquals(DigiSm5100PluCodec.STRUCT_SIZE, b.size)
        assertEquals(1234, leInt(b, 0))          // PLUNo
        assertEquals(0, leInt(b, 4))             // WeightUnit 计重
        assertEquals(1, leInt(b, 8))             // UPriceOverride 允许变价
        assertEquals(2350, leInt(b, 12))         // UPrice
        assertEquals(5, leInt(b, 16))            // LabelFormat1
        assertEquals(6, leInt(b, 20))            // LabelFormat2
        assertEquals(0x21, leInt(b, 24))         // BarFormat
        assertEquals(3, leInt(b, 52))            // MG
        assertEquals(45, leInt(b, 56))           // SBDate
        assertEquals(30, leInt(b, 60))           // UBDate（保质期，天）
        assertEquals(1200, leInt(b, 64))         // Cost
        assertEquals(100, leInt(b, 68))          // Tare
        assertEquals(1, leInt(b, 76))            // TaxNo
        // EAN_Data ItemCode char[10]（GBK 原文）
        val itemCode = String(b, 32, 10, GBK).trimEnd('\u0000')
        assertEquals("100001", itemCode)
        assertEquals(0, leInt(b, 44))            // EAN_Data.BarCodeType
        assertEquals(2, leInt(b, 48))            // EAN_Data.BarRightData
        // 品名首行（Commodity[0] @188：font int + GBK 文本）
        assertEquals(DigiSm5100PluCodec.DEFAULT_FONT, leInt(b, 188))
        val name = String(b, 192, 12, GBK)
        assertTrue(name.startsWith("特级苹果"))
    }

    @Test
    fun `pcs unit maps to weight unit 1`() {
        val b = DigiSm5100PluCodec.encode(PluItem(pluNo = 1, unit = PluUnit.PCS.id, weightFlag = 1))
        assertEquals(1, leInt(b, 4))
        val b2 = DigiSm5100PluCodec.encode(PluItem(pluNo = 1, unit = PluUnit.KG.id, weightFlag = 0))
        assertEquals(0, leInt(b2, 4))
    }

    @Test
    fun `shelf life uses effective days`() {
        val b = DigiSm5100PluCodec.encode(
            PluItem(pluNo = 1, usedByDate = 2, shelfLifeUnit = ShelfLifeUnit.MONTH.id),
        )
        assertEquals(60, leInt(b, 60)) // 2 月 → 60 天
    }

    @Test
    fun `round trip preserves core fields`() {
        val plu = PluItem(
            pluNo = 77,
            eanCode = "100002",
            mainGroup = 5,
            name = "草叶",
            nameEn = "leaf",
            unitPrice = 340L,
            unit = PluUnit.PCS.id,
            weightFlag = 1,
            label1Format = 3,
            label2Format = 4,
            barcodeFormat = 0x21,
            barcodeType = 9,
            barRightDataType = 1,
            usedByDate = 7,
            shelfLifeUnit = ShelfLifeUnit.DAY.id,
            sellByDate = 10,
            cost = 100L,
            tare = 50,
            taxRateNo = 2,
            commodityLines = listOf(PluTextLine(text = "草叶"), PluTextLine(text = "第二行品名")),
            ingredientLines = listOf(PluTextLine(text = "配料A")),
            specialMsgLines = listOf(PluTextLine(text = "促销信息")),
        )
        val parsed = DigiSm5100PluCodec.parse(DigiSm5100PluCodec.encode(plu))!!
        assertEquals(77, parsed.pluNo)
        assertEquals("100002", parsed.eanCode)
        assertEquals(5, parsed.mainGroup)
        assertEquals(340L, parsed.unitPrice)
        assertEquals(1, parsed.weightFlag)
        assertEquals(3, parsed.label1Format)
        assertEquals(4, parsed.label2Format)
        assertEquals(9, parsed.barcodeType)
        assertEquals(7, parsed.usedByDate)
        assertEquals(10, parsed.sellByDate)
        assertEquals(50, parsed.tare)
        assertEquals(2, parsed.taxRateNo)
        assertEquals("草叶", parsed.name)
        assertEquals(2, parsed.commodityLines.size)
        assertEquals("配料A", parsed.ingredientLines.first().text)
        assertEquals("促销信息", parsed.specialMsgLines.first().text)
    }

    @Test
    fun `parse rejects empty or short buffer`() {
        assertNull(DigiSm5100PluCodec.parse(ByteArray(100)))
        assertNull(DigiSm5100PluCodec.parse(ByteArray(DigiSm5100PluCodec.STRUCT_SIZE))) // PLUNo=0
    }
}


