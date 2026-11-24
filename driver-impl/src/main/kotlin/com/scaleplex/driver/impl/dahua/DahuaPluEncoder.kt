package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.DigiTables

/**
 * Dahua PLU encoder for TCP protocol (port 4001).
 *
 * Based on the Dahua DLL protocol documentation and sample data files:
 * - dhbz5obj.txt (TMBZ_5): 5-digit price format
 * - dhbz6obj.txt (TMBZ_6): 6-digit price format
 * - dhtma07obj.txt (TMA_07): New format with extended fields
 *
 * Protocol format (text-based, line per PLU):
 * !0V{PLU_NO}A{PLU_TYPE}{PRICE_TYPE}{UNIT}{...}{BARCODE}...
 *
 * Field mapping from sample data (dhtma07obj.txt):
 * !0V 0001 A 22 81 08 000200000000000803000000000000000100000020106100712201318301924400 B 18664252502700 C 18664252031700 D 1866425200 E
 */
object DahuaPluEncoder {

    /**
     * PLU type/version mapping from dhpluversion.ini:
     * 1 = TMBZ_5 (5-digit price)
     * 2 = TMBZ_6 (6-digit price)
     * 3 = TMA_07 (new format)
     */
    enum class PluVersion(val code: Int, val prefix: String) {
        TMBZ_5(1, "!0W"),
        TMBZ_6(2, "!0W"),
        TMA_07(3, "!0V");

        companion object {
            fun fromCode(code: Int): PluVersion = entries.firstOrNull { it.code == code } ?: TMA_07
        }
    }

    /**
     * Encode a PLU to Dahua text protocol format.
     * Returns the line(s) to send over TCP.
     */
    fun encode(plu: PluItem, version: PluVersion = PluVersion.TMA_07): String {
        return when (version) {
            PluVersion.TMBZ_5 -> encodeTmbz5(plu)
            PluVersion.TMBZ_6 -> encodeTmbz6(plu)
            PluVersion.TMA_07 -> encodeTma07(plu)
        }
    }

    /** TMBZ_5 format (5-digit price) - from dhbz5obj.txt */
    private fun encodeTmbz5(plu: PluItem): String {
        val sb = StringBuilder()
        sb.append("!0W")
        sb.append(String.format("%04d", plu.pluNo))
        sb.append("A") // Section A start

        // A28 108 0B 0200 0C0D00 8E03 F10000 (from sample)
        // These appear to be fixed fields for PLU type, unit, etc.
        sb.append("A281080B02000C0D008E03F10000")

        // G: flags (0=weighing, 1=non-weighing)
        sb.append("G${if (plu.weightFlag == 1) "1" else "0"}")

        // H: barcode (14 digits in sample)
        val barcode = plu.eanCode.padEnd(14, '0').take(14)
        sb.append("H$barcode")

        // I: price (in sample: 18664252031700 -> this looks like price with decimals)
        // The sample shows: 18664252502700 and 18664252031700
        // Format appears to be: prefix(6) + price(8) = 14 digits
        val priceStr = String.format("%08d", plu.unitPrice)
        sb.append("I18664252$priceStr")

        // J: additional data (varies)
        sb.append("J")

        return sb.toString()
    }

    /** TMBZ_6 format (6-digit price) - from dhbz6obj.txt */
    private fun encodeTmbz6(plu: PluItem): String {
        val sb = StringBuilder()
        sb.append("!0W")
        sb.append(String.format("%04d", plu.pluNo))
        sb.append("A")

        // TMBZ_6 has slightly different A section: A28 108 0B 002000...
        sb.append("A281080B002000C0D008E03F10000")

        sb.append("G${if (plu.weightFlag == 1) "1" else "0"}")
        val barcode = plu.eanCode.padEnd(14, '0').take(14)
        sb.append("H$barcode")
        val priceStr = String.format("%08d", plu.unitPrice)
        sb.append("I18664252$priceStr")
        sb.append("J")

        return sb.toString()
    }

    /** TMA_07 format (new extended format) - from dhtma07obj.txt */
    private fun encodeTma07(plu: PluItem): String {
        val sb = StringBuilder()
        sb.append("!0V")
        sb.append(String.format("%04d", plu.pluNo))
        sb.append("A")

        // A section: 22 81 08 000200000000000803000000000000000100000020106100712201318301924400
        // Breaking down the sample:
        // 22 = PLU type?
        // 81 = ?
        // 08 = ?
        // 000200000000000803000000000000000100000020106100712201318301924400 = extended data

        // Build A section with configurable fields
        val pluType = "22" // Default from sample
        val sectionA = buildTma07SectionA(plu)
        sb.append(sectionA)

        // B section: barcode 1
        val barcode1 = plu.eanCode.padEnd(14, '0').take(14)
        sb.append("B$barcode1")

        // C section: barcode 2 (often same or related)
        val barcode2 = if (plu.multiBarcode.isNotBlank()) plu.multiBarcode.padEnd(14, '0').take(14) else barcode1
        sb.append("C$barcode2")

        // D section: barcode 3 (often price-embedded)
        val priceStr = String.format("%08d", plu.unitPrice)
        val barcode3 = "18664252$priceStr"
        sb.append("D$barcode3")

        // E section: end marker
        sb.append("E")

        return sb.toString()
    }

    private fun buildTma07SectionA(plu: PluItem): String {
        val sb = StringBuilder()
        sb.append("22") // PLU type
        sb.append("81") // ?
        sb.append("08") // ?

        // Price-related fields (8 digits each for unit price, total, etc.)
        sb.append(String.format("%016d", plu.unitPrice)) // Extended price field
        sb.append(String.format("%016d", plu.cost)) // Cost field
        sb.append(String.format("%016d", plu.tare.toLong())) // Tare
        sb.append(String.format("%016d", plu.qty.toLong())) // Qty

        // Date fields (sell by, used by, packed)
        sb.append(String.format("%08d", plu.sellByDate))
        sb.append(String.format("%08d", plu.usedByDate))
        sb.append(String.format("%08d", plu.packedDate))

        // Label formats
        sb.append(String.format("%02d", plu.label1Format))
        sb.append(String.format("%02d", plu.label2Format))

        // Group/main group
        sb.append(String.format("%04d", plu.mainGroup))

        // Tax rate
        sb.append(String.format("%02d", plu.taxRateNo))

        // Place no
        sb.append(String.format("%04d", plu.placeNo))

        // Fill remaining with zeros to match expected length
        while (sb.length < 100) sb.append('0')

        return sb.toString()
    }
}