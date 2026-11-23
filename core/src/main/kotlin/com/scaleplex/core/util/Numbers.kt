package com.scaleplex.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Money / weight helpers. Prices and weights are carried as integer minor units
 * (cents / grams) throughout the app; these helpers convert to and from display strings.
 */
object Numbers {

    /** minor units -> display string, e.g. 1234 with 2 decimals -> "12.34". */
    fun minorToDisplay(minor: Long, decimals: Int): String {
        val bd = BigDecimal(minor).movePointLeft(decimals).setScale(decimals, RoundingMode.HALF_UP)
        return bd.toPlainString()
    }

    /** display string -> minor units, e.g. "12.34" with 2 decimals -> 1234. */
    fun displayToMinor(text: String, decimals: Int): Long {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isBlank()) return 0
        return BigDecimal(cleaned)
            .setScale(decimals, RoundingMode.HALF_UP)
            .movePointRight(decimals)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /** Format weight (grams, integer) to a string with the configured decimals. */
    fun weightToDisplay(grams: Int, decimals: Int): String =
        minorToDisplay(grams.toLong(), decimals)

    /** Parse a locale-neutral double string safely; returns null on failure. */
    fun parseDouble(text: String): Double? =
        runCatching { text.trim().toDouble() }.getOrNull()

    fun formatDouble(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)
}
