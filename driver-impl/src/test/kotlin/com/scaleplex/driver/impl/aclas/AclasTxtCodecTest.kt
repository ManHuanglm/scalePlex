package com.scaleplex.driver.impl.aclas

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluUnit
import com.scaleplex.core.model.ShelfLifeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 顶尖 Aclas PLU 文本编解码测试 —— 行格式用官方 Demo 锁定
 * （Console/PLU Demo.txt、LiNK69 Data/PLU.txt，UTF-16LE Tab 59 字段）。
 */
class AclasTxtCodecTest {

    // PLU Demo.txt 行2（Tab 分隔 59 列，Price=10 元，UnitID=4 即 kg，OriginID=1，Flag1=60/Flag2=240）
    private val demoLine = listOf(
        "950001", "950001", "2", "1", "apple", "", "",
        "10", "4", "0", "0", "1", "0", "2014/9/22 15:06", "0", "0",
        "0", "0", "0", "0", "0", "0",
        "0", "0", "0", "0", "0", "0", "0", "0",
        "0", "0", "0", "0", "0", "0", "0", "0", "1", "0", "0",
        "0", "0", "0", "0", "60", "240", "0", "0", "0", "0", "0",
        "", "0", "", "1899/12/30", "1899/12/30", "0", "0",
    ).joinToString("\t")

    private val header = AclasTxtCodec.HEADER.joinToString("\t")

    @Test
    fun parseDemoLine() {
        val items = AclasTxtCodec.parseFileText("$header\r\n$demoLine\r\n")
        assertEquals(1, items.size)
        items[0].let {
            assertEquals(950001, it.pluNo)
            assertEquals("950001", it.eanCode)
            assertEquals(2, it.mainGroup)
            assertEquals("apple", it.name)
            assertEquals(1000L, it.unitPrice) // 10 元 → 1000 分
            assertEquals(PluUnit.KG.id, it.unit) // UnitID=4 → kg
            assertEquals(0, it.weightFlag)
            assertEquals(1, it.label1Format)
            assertEquals(0, it.usedByDate) // ValidDate=0
            assertEquals(ShelfLifeUnit.DAY.id, it.shelfLifeUnit) // 回收端固定为天
            assertEquals(0, it.tare)
            assertEquals(1, it.placeNo) // OriginID=1
        }
    }

    @Test
    fun encodeMatchesDemoStructure() {
        val plu = PluItem(
            pluNo = 950001,
            eanCode = "950001",
            mainGroup = 2,
            name = "apple",
            unitPrice = 1000L,
            label1Format = 1,
            placeNo = 1,
        )
        val cells = AclasTxtCodec.encodeLine(plu).split("\t")
        assertEquals(AclasTxtCodec.HEADER.size, cells.size)
        assertEquals("950001", cells[0])
        assertEquals("950001", cells[1])
        assertEquals("2", cells[2])
        assertEquals("apple", cells[4])
        assertEquals("10", cells[7]) // Price
        assertEquals("4", cells[8]) // UnitID kg
        assertEquals("1", cells[11]) // Label1ID
        assertEquals("0", cells[15]) // ValidDate（保质期 0）
        assertEquals("1", cells[38]) // OriginID
        assertEquals(AclasTxtCodec.FLAG1_DEFAULT.toString(), cells[45])
        assertEquals(AclasTxtCodec.FLAG2_DEFAULT.toString(), cells[46])
        assertEquals(AclasTxtCodec.ZERO_DATE, cells[55])
    }

    @Test
    fun encodeShelfLifeConvertedToDays() {
        // 3 月 → 90 天
        val plu = PluItem(
            pluNo = 1,
            usedByDate = 3,
            shelfLifeUnit = ShelfLifeUnit.MONTH.id,
        )
        val cells = AclasTxtCodec.encodeLine(plu).split("\t")
        assertEquals("90", cells[15])
    }

    @Test
    fun encodeFileHasHeaderAndRows() {
        val text = AclasTxtCodec.encodeFile(
            listOf(PluItem(pluNo = 1, name = "测试商品", unitPrice = 2350L)),
        )
        val lines = text.trimEnd('\r', '\n').split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("ID\tItemCode\tDepartmentID"))
        assertTrue(lines[1].contains("测试商品"))
        // 23.50 元
        assertTrue(lines[1].contains("23.5") || lines[1].contains("23.50"))
    }

    @Test
    fun roundTripPreservesCoreFields() {
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
            usedByDate = 7,
            shelfLifeUnit = ShelfLifeUnit.DAY.id,
            tare = 50,
            placeNo = 2,
        )
        val parsed = AclasTxtCodec.parseFileText("$header\r\n${AclasTxtCodec.encodeLine(plu)}\r\n").single()
        assertEquals(plu.pluNo, parsed.pluNo)
        assertEquals(plu.eanCode, parsed.eanCode)
        assertEquals(plu.mainGroup, parsed.mainGroup)
        assertEquals(plu.name, parsed.name)
        assertEquals(plu.unitPrice, parsed.unitPrice)
        assertEquals(PluUnit.PCS.id, parsed.unit)
        assertEquals(1, parsed.weightFlag)
        assertEquals(plu.label1Format, parsed.label1Format)
        assertEquals(plu.usedByDate, parsed.usedByDate)
        assertEquals(plu.tare, parsed.tare)
        assertEquals(plu.placeNo, parsed.placeNo)
    }
}
