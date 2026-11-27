package com.scaleplex.driver.impl.yousheng

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 友声桥接 NDJSON 编解码回归（与 YouShengBridge.cs 的 ParsePlu / PluJson / ReportJson 对应）。
 */
class YouShengDriverJsonTest {

    private val driver = YouShengDriver(
        ScaleDevice(name = "test", brand = ScaleBrand.YOUSHENG),
    )

    @Test
    fun `plu json converts fen to yuan`() {
        val json = driver.pluJson(PluItem(pluNo = 1, name = "大白菜", mainGroup = 1, unitPrice = 2000))
        assertEquals(true, json.contains("\"ID\": 1"))
        assertEquals(true, json.contains("\"Price\": 20.00"))
        assertEquals(true, json.contains("\"Class\": 1"))
        assertEquals(true, json.contains("\"Text1\": \"大白菜\""))
    }

    @Test
    fun `plu json odd price keeps two decimals`() {
        val json = driver.pluJson(PluItem(pluNo = 2, unitPrice = 855))
        assertEquals(true, json.contains("\"Price\": 8.55"))
    }

    @Test
    fun `plu json count item uses count unit`() {
        val json = driver.pluJson(PluItem(pluNo = 3, weightFlag = 1))
        assertEquals(true, json.contains("\"Unit\": ${YouShengDriver.COUNT_UNIT}"))
    }

    @Test
    fun `parse plu json maps yuan to fen`() {
        val line = """{"ID": 4, "Index": "", "Unit": 1, "Price": 12.5, "Class": 7, "Text1": "鲜猪肉"}"""
        val plu = driver.parsePluJson(line)!!
        assertEquals(4, plu.pluNo)
        assertEquals("鲜猪肉", plu.name)
        assertEquals(1250L, plu.unitPrice)
        assertEquals(7, plu.mainGroup)
        assertEquals(0, plu.weightFlag)
    }

    @Test
    fun `parse plu json skips non plu lines`() {
        assertNull(driver.parsePluJson("handle=123456"))
        assertNull(driver.parsePluJson("count=3"))
        assertNull(driver.parsePluJson("total=0"))
    }

    @Test
    fun `parse report json maps fen and grams`() {
        val line = """{"FID": 12, "dt": "2026-11-27 10:20:30", "PLU": 4, "Dept": 1, """ +
            """"UnitPrice": 15.2, "Price": 30.4, "Count": 2, "SID": 1}"""
        val record = driver.parseReportJson(line)!!
        assertEquals(12L, record.recordNo)
        assertEquals(4, record.pluNo)
        assertEquals(1520L, record.unitPrice)
        assertEquals(3040L, record.amount)
        assertEquals(2000, record.weight)
        assertEquals("2026-11-27T10:20:30", record.timestamp.toString())
        assertEquals("1", record.operator)
    }
}
