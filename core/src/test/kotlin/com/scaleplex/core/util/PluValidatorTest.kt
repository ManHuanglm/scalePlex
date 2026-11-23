package com.scaleplex.core.util

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluValidatorTest {

    @Test
    fun `valid plu passes`() {
        val plu = PluItem(pluNo = 1, name = "鸡蛋", unitPrice = 1234)
        assertTrue(PluValidator.validate(plu) is ValidationResult.Ok)
    }

    @Test
    fun `missing name fails`() {
        val plu = PluItem(pluNo = 1, name = "", nameEn = "")
        val r = PluValidator.validate(plu)
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun `bad plu number fails`() {
        val plu = PluItem(pluNo = 0, name = "x")
        assertTrue(PluValidator.validate(plu) is ValidationResult.Invalid)
    }

    @Test
    fun `ean13 check digit`() {
        // 6901234567892 is a classic valid EAN-13 test code.
        assertTrue(PluValidator.isValidEan("6901234567892"))
        assertEquals(2, PluValidator.eanCheckDigit("690123456789"))
        assertTrue(!PluValidator.isValidEan("6901234567890"))
    }

    @Test
    fun `money roundtrip`() {
        assertEquals("12.34", Numbers.minorToDisplay(1234, 2))
        assertEquals(1234L, Numbers.displayToMinor("12.34", 2))
        assertEquals(1200L, Numbers.displayToMinor("12", 2))
    }
}
