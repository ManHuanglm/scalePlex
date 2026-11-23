package com.scaleplex.core.util

import com.scaleplex.core.model.PluItem

/** Result of validating a PLU before persisting or downloading. */
sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

/**
 * Shared PLU validation used by [com.scaleplex.service.PluService] before save / download.
 * Brand-specific drivers may layer extra rules on top.
 */
object PluValidator {

    fun validate(plu: PluItem, priceDecimals: Int = 2): ValidationResult {
        val errors = mutableListOf<String>()

        if (plu.pluNo <= 0) errors += "PLU 号必须为正整数 / PLU number must be positive"
        if (plu.name.isBlank() && plu.nameEn.isBlank()) errors += "品名不能为空 / Name required"
        if (plu.unitPrice < 0) errors += "单价不能为负 / Price cannot be negative"

        if (plu.weightFlag !in 0..1) errors += "计价方式非法 / Invalid weight flag"
        if (plu.tare < 0) errors += "皮重不能为负 / Tare cannot be negative"
        if (plu.barcodeType !in intArrayOf(0, 9)) errors += "条码类型非法(0=EAN,9=ITF) / Invalid barcode type"

        if (plu.eanCode.isNotBlank()) {
            when {
                // 仅对 8/13 位纯数字（EAN 形态）校验校验位；TOP2000 风格的短项目代码直接放行
                plu.barcodeType == 0 && isEanCandidate(plu.eanCode) && !isValidEan(plu.eanCode) ->
                    errors += "EAN 校验位错误 / Invalid EAN check digit"
            }
        }

        return if (errors.isEmpty()) ValidationResult.Ok else ValidationResult.Invalid(errors)
    }

    /** 是否为需要校验位校验的 EAN 形态编码：8 或 13 位纯数字。 */
    fun isEanCandidate(code: String): Boolean {
        val digits = code.filter { it.isDigit() }
        return digits == code && digits.length in intArrayOf(8, 13)
    }

    /** Validate an EAN-8 / EAN-13 check digit. */
    fun isValidEan(code: String): Boolean {
        val digits = code.filter { it.isDigit() }
        if (digits.length !in intArrayOf(8, 13)) return false
        val body = digits.dropLast(1)
        val check = digits.last() - '0'
        return eanCheckDigit(body) == check
    }

    /** Compute the EAN-8/13 check digit for a numeric body. */
    fun eanCheckDigit(body: String): Int {
        val digits = body.filter { it.isDigit() }
        var sum = 0
        // Weights alternate 3,1 starting from the rightmost body digit.
        var weight = 3
        for (i in digits.length - 1 downTo 0) {
            sum += (digits[i] - '0') * weight
            weight = if (weight == 3) 1 else 3
        }
        return (10 - (sum % 10)) % 10
    }
}
