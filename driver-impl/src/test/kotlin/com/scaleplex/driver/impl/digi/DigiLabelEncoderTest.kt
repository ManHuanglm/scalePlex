package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.LabelElement
import com.scaleplex.core.model.LabelElementKind
import com.scaleplex.core.model.LabelTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiLabelEncoderTest {

    private fun template(vararg kinds: LabelElementKind) = LabelTemplate(
        id = 7,
        name = "t",
        widthMm = 40,
        heightMm = 30,
        elements = kinds.map { LabelElement(kind = it, xMm = 1f, yMm = 2f, text = "A") },
    )

    @Test
    fun `header encodes format no and dot matrix size`() {
        val record = DigiLabelEncoder.encode(template(LabelElementKind.TEXT), formatNo = 5)
        assertEquals(5, record[0].toInt() and 0xFF)
        // 40mm * 8dots/mm = 320 = 0x0140 LE
        assertEquals(0x40, record[1].toInt() and 0xFF)
        assertEquals(0x01, record[2].toInt() and 0xFF)
        // 30mm * 8 = 240 = 0x00F0 LE
        assertEquals(0xF0, record[3].toInt() and 0xFF)
        assertEquals(0x00, record[4].toInt() and 0xFF)
        // 元素数
        assertEquals(1, record[5].toInt() and 0xFF)
    }

    @Test
    fun `format no coerced into 1 to 24`() {
        val low = DigiLabelEncoder.encode(template(), formatNo = 0)
        val high = DigiLabelEncoder.encode(template(), formatNo = 99)
        assertEquals(1, low[0].toInt() and 0xFF)
        assertEquals(24, high[0].toInt() and 0xFF)
    }

    @Test
    fun `logo elements are skipped`() {
        val t = template(LabelElementKind.TEXT, LabelElementKind.LOGO, LabelElementKind.BARCODE)
        val record = DigiLabelEncoder.encode(t, formatNo = 1)
        assertEquals(2, record[5].toInt() and 0xFF)
        // 首个元素 kindCode = TEXT(0x01)
        assertEquals(0x01, record[6].toInt() and 0xFF)
        // 每元素 13 字节（kind1+x2+y2+w2+h2+font1+align1+len1+text1），第二个元素偏移 6+13=19
        assertEquals(0x04, record[19].toInt() and 0xFF)
    }

    @Test
    fun `element coordinates convert mm to dots`() {
        assertEquals(0, DigiLabelEncoder.mmToDots(0f))
        assertEquals(80, DigiLabelEncoder.mmToDots(10f))
        assertEquals(0, DigiLabelEncoder.mmToDots(-3f))
        assertEquals(16, DigiLabelEncoder.mmToDots(2.05f)) // 截断取整
    }

    @Test
    fun `kindCode covers first phase element kinds`() {
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.TEXT) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.BARCODE) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.WEIGHT) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.UNIT_PRICE) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.TOTAL_PRICE) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.DATE) != null)
        assertTrue(DigiLabelEncoder.kindCode(LabelElementKind.LOGO) == null)
    }
}
