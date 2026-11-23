package com.scaleplex.core.util

import com.scaleplex.core.model.PluItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluValidatorTest {

    private val ok get() = PluValidator.validate(PluItem(pluNo = 1, name = "苹果", unitPrice = 500))

    @Test
    fun `basic plu passes validation`() {
        assertEquals(ValidationResult.Ok, ok)
    }

    @Test
    fun `short item code passes without ean check`() {
        // TOP2000 风格项目代码（非 8/13 位 EAN）不参与校验位校验
        val result = PluValidator.validate(PluItem(pluNo = 2, name = "苹果", unitPrice = 500, eanCode = "0001"))
        assertEquals(ValidationResult.Ok, result)
        assertEquals(ValidationResult.Ok, PluValidator.validate(PluItem(pluNo = 3, name = "a", eanCode = "A001B")))
    }

    @Test
    fun `invalid ean13 check digit is rejected`() {
        val result = PluValidator.validate(PluItem(pluNo = 4, name = "x", eanCode = "6901234500017"))
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `valid ean13 check digit passes`() {
        // 校验位由 eanCheckDigit 动态计算，保证样例合法
        val body = "690123450001"
        val check = PluValidator.eanCheckDigit(body)
        val result = PluValidator.validate(PluItem(pluNo = 5, name = "x", eanCode = body + check))
        assertEquals(ValidationResult.Ok, result)
    }

    @Test
    fun `blank name and non positive plu rejected`() {
        val result = PluValidator.validate(PluItem(pluNo = 0, name = "", unitPrice = 500))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.size >= 2)
    }
}
