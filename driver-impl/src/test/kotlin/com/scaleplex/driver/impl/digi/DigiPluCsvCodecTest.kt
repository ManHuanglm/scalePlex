package com.scaleplex.driver.impl.digi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiPluCsvCodecTest {

    private val sampleHeader = DigiPluCsvCodec.HEADER.joinToString(",")

    @Test
    fun `parses header and skips it`() {
        val line = "1,0,997,31,,,,,101680000,3,1,2,鸡１,1,1,,,,,,,,,,,,,,,,,,上海,,,,,,,,,,,,,1,3,土鸡蛋 重庆,,,,,,,,,,,,"
        val plus = DigiPluCsvCodec.parse(listOf(sampleHeader, line))
        assertEquals(1, plus.size)
        val p = plus[0]
        assertEquals(1, p.pluNo)
        assertEquals(0, p.weightFlag)
        assertEquals(997, p.mainGroup)
        assertEquals(3100L, p.unitPrice) // 31.00 -> 3100 minor units
        assertEquals("101680000", p.eanCode)
        assertEquals(3, p.usedByDate)
        assertTrue(p.commodityLines.any { it.text == "鸡１" })
        assertTrue(p.specialMsgLines.any { it.text == "上海" })
        assertTrue(p.ingredientLines.any { it.text == "土鸡蛋 重庆" })
    }

    @Test
    fun `roundtrip preserves core fields`() {
        val line = "2,1,997,2,,,22,,56780000,4,1,2,鸡蛋,1,1,,,,,,,,,,,,,,,,,,北京,,,,,,,,,,,,,1,3,土鸡蛋 常州,,,,,,,,,,,,"
        val parsed = DigiPluCsvCodec.parse(listOf(sampleHeader, line))[0]
        assertEquals(2, parsed.pluNo)
        assertEquals(1, parsed.weightFlag)
        assertEquals("22", parsed.flagData)
        val out = DigiPluCsvCodec.writePlus(listOf(parsed))
        val reparsed = DigiPluCsvCodec.parse(out)[0]
        assertEquals(parsed.pluNo, reparsed.pluNo)
        assertEquals(parsed.weightFlag, reparsed.weightFlag)
        assertEquals(parsed.unitPrice, reparsed.unitPrice)
        assertEquals(parsed.eanCode, reparsed.eanCode)
    }

    @Test
    fun `quotes fields containing commas`() {
        val p = com.scaleplex.core.model.PluItem(
            pluNo = 5, 
            commodityLines = listOf(com.scaleplex.core.model.PluTextLine(fontNo = 23, text = "a,b"))
        )
        val out = DigiPluCsvCodec.writePlus(listOf(p))
        val reparsed = DigiPluCsvCodec.parse(out)[0]
        assertEquals("a,b", reparsed.commodityLines.first().text)
    }
}
