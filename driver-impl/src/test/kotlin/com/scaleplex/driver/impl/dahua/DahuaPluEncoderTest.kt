package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DahuaPluEncoderTest {

    @Test
    fun `encode fields follows documented field order`() {
        val plu = PluItem(
            pluNo = 12,
            name = "苹果",
            nameEn = "Apple",
            weightFlag = 0,
            unitPrice = 1250,
            eanCode = "12345678",
            mainGroup = 1,
            tare = 30,
            label1Format = 3,
            usedByDate = 7,
        )
        val fields = DahuaPluEncoder.encodeFields(plu)
        assertEquals(
            listOf("12", "苹果", "Apple", "0", "12.50", "12345678", "1", "30", "3", "7"),
            fields,
        )
    }

    @Test
    fun `price formatting covers zero max and decimals`() {
        assertEquals("0.00", DahuaPluEncoder.formatMinor(0))
        assertEquals("12.50", DahuaPluEncoder.formatMinor(1250))
        assertEquals("999999.99", DahuaPluEncoder.formatMinor(99999999))
        assertEquals("1250", DahuaPluEncoder.formatMinor(1250, decimals = 0))
    }

    @Test
    fun `negative price is clamped to zero`() {
        assertEquals("0.00", DahuaPluEncoder.formatMinor(-1))
    }

    @Test
    fun `sanitize removes frame breaking characters`() {
        assertEquals("红，苹果", DahuaPluEncoder.sanitize("红,苹果"))
        assertEquals("A B", DahuaPluEncoder.sanitize("A\rB"))
        assertEquals("A B", DahuaPluEncoder.sanitize("A\nB"))
    }

    @Test
    fun `gbk length counts chinese as two bytes`() {
        assertEquals(4, DahuaPluEncoder.gbkLength("苹果"))
        assertEquals(2, DahuaPluEncoder.gbkLength("AB"))
    }

    @Test
    fun `encode is deterministic`() {
        val plu = PluItem(pluNo = 1, name = "测试", unitPrice = 999)
        assertEquals(
            DahuaPluEncoder.encodeFields(plu),
            DahuaPluEncoder.encodeFields(plu),
        )
    }

    @Test
    fun `label encoding skips unsupported logo element`() {
        val template = com.scaleplex.core.model.LabelTemplate(
            id = 5,
            name = "t",
            widthMm = 40,
            heightMm = 30,
            elements = listOf(
                com.scaleplex.core.model.LabelElement(
                    kind = com.scaleplex.core.model.LabelElementKind.TEXT,
                    xMm = 1.5f,
                    yMm = 2f,
                    text = "超市",
                ),
                com.scaleplex.core.model.LabelElement(
                    kind = com.scaleplex.core.model.LabelElementKind.LOGO,
                ),
            ),
        )
        val fields = DahuaPluEncoder.encodeLabel(template)
        assertEquals("5", fields[0])
        assertEquals("40", fields[1])
        assertEquals("30", fields[2])
        // LOGO 元素编码为空串，join 后不会产生额外内容
        assertTrue(fields[3].startsWith("T,1.5,2,20,6,超市"))
        assertTrue(!fields[3].contains(",,"))
    }
}
