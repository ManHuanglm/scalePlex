package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiPluEncoderTest {

    @Test
    fun `status bytes reflect weighing flag and cost`() {
        val weigh = PluItem(pluNo = 1, name = "x", weightFlag = 0, cost = 100)
        val count = PluItem(pluNo = 2, name = "x", weightFlag = 1, cost = 0)
        val sWeigh = DigiPluEncoder.statusBytes(weigh)
        val sCount = DigiPluEncoder.statusBytes(count)
        assertEquals(5, sWeigh.size)
        // BIT0 of status1 byte1 = unit (1 => non-weighing)
        assertTrue((sCount[0].toInt() and 0x01) == 1)
        assertTrue((sWeigh[0].toInt() and 0x01) == 0)
        // status2 byte1 BIT5 = cost present
        assertTrue((sWeigh[2].toInt() and 0x20) != 0)
        assertTrue((sCount[2].toInt() and 0x20) == 0)
    }

    @Test
    fun `encode produces deterministic output with PLU number little-endian`() {
        val p = PluItem(pluNo = 1, name = "鸡蛋", unitPrice = 1234)
        val bytes = DigiPluEncoder.encode(p)
        assertTrue(bytes.size > 10)
        assertEquals(1, bytes[0].toInt() and 0xFF)      // PLU no low byte
        assertEquals(0, bytes[1].toInt() and 0xFF)      // PLU no byte2
        // unit price after 4-byte plu + 5 status bytes => offset 9
        assertEquals(1234 and 0xFF, bytes[9].toInt() and 0xFF)
        assertEquals((1234 shr 8) and 0xFF, bytes[10].toInt() and 0xFF)
    }
}
