package com.scaleplex.driver.impl.digi

import com.scaleplex.core.model.PluItem
import java.io.ByteArrayOutputStream

/**
 * Encodes a [PluItem] into the DIGI SM series binary PLU record layout.
 *
 * The status bytes reproduce the bit definitions from the SM `SM.ini` definition file
 * (PLU_STATUS1 Byte1/Byte2, PLU_STATUS2 Byte1/Byte2/Byte3). Field presence bits are set
 * when the corresponding PLU attribute is non-default, matching how TOP2000/DIGI CSV
 * import marks optional fields.
 *
 * Note: the exact byte offsets / record length vary slightly across SM firmware; this
 * encoder targets the common SM-100 / SM-5100 record used by the DIGI UDP CSV pipeline.
 */
object DigiPluEncoder {

    // ---- PLU_STATUS1 Byte1 bits ----
    private const val B1_UNIT = 0            // 0=weighing,1=non-weighing
    private const val B1_UNIT_PRICE = 1      // 0=1st price,1=2nd price
    private const val B1_SELL_BY_DATE = 2
    private const val B1_USED_BY_DATE = 3
    private const val B1_PACKED_DATE = 4
    private const val B1_SELL_BY_TIME = 5
    private const val B1_PACKED_TIME = 6
    private const val B1_RTC_PACKED_TIME = 7

    // ---- PLU_STATUS1 Byte2 bits ----
    private const val B2_RTC_SELL_BY_TIME = 0
    private const val B2_PRICE_PER_UNIT = 1  // 0=per KG,1=per 100G
    private const val B2_FORCE_SHELF_LIFE = 2
    private const val B2_NUTRITION_PRINT = 3
    private const val B2_UNIT_PRICE_OVERRIDE = 4
    private const val B2_SELL_BY_DATE_SOURCE = 5
    private const val B2_SECURITY_TAG = 6
    private const val B2_APPEND_PLU_DATA = 7

    // ---- PLU_STATUS2 Byte1 bits ----
    private const val S1_LABEL1_FORMAT = 0
    private const val S1_LABEL2_FORMAT = 1
    private const val S1_BARCODE_FORMAT = 2
    private const val S1_ITEM_CODE = 3
    private const val S1_MAIN_GROUP = 4
    private const val S1_COST = 5
    private const val S1_PLU_TARE = 6
    private const val S1_QUANTITY = 7

    // ---- PLU_STATUS2 Byte2 bits ----
    private const val S2_QUANTITY_SYMBOL = 0
    private const val S2_SPECIAL_MSG_NO = 1
    private const val S2_INGREDIENT_NO = 2
    private const val S2_DISCOUNT = 3
    private const val S2_NUTRITION = 4
    private const val S2_COMMODITY = 5
    private const val S2_SPECIAL_MSG = 6
    private const val S2_INGREDIENT = 7

    // ---- PLU_STATUS2 Byte3 bits ----
    private const val S3_SELL_BY_DATE = 0
    private const val S3_SELL_BY_TIME = 1
    private const val S3_USED_BY_DATE = 2
    private const val S3_PACKED_DATE = 3
    private const val S3_PACKED_TIME = 4
    private const val S3_PLACE_NUMBER = 5
    private const val S3_IMAGE_NUMBER = 6
    private const val S3_STATUSB = 7

    /**
     * Build the 5 status bytes (status1 = 2 bytes, status2 = 3 bytes) for a PLU.
     * Exposed for unit testing the bit layout.
     */
    fun statusBytes(plu: PluItem): ByteArray {
        val s1b1 = 0
            .setBit(B1_UNIT, plu.weightFlag == 1)
            .setBit(B1_SELL_BY_DATE, plu.sellByDate > 0)
            .setBit(B1_USED_BY_DATE, plu.usedByDate > 0)
            .setBit(B1_PACKED_DATE, plu.packedDate > 0)

        val s1b2 = 0
            .setBit(B2_UNIT_PRICE_OVERRIDE, true) // allow price override by default

        val s2b1 = 0
            .setBit(S1_LABEL1_FORMAT, true)
            .setBit(S1_LABEL2_FORMAT, plu.label2Format > 0)
            .setBit(S1_BARCODE_FORMAT, true)
            .setBit(S1_ITEM_CODE, plu.eanCode.isNotBlank())
            .setBit(S1_MAIN_GROUP, true)
            .setBit(S1_COST, plu.cost > 0)
            .setBit(S1_PLU_TARE, plu.tare > 0)
            .setBit(S1_QUANTITY, plu.qty > 0)

        val s2b2 = 0
            .setBit(S2_COMMODITY, plu.commodityLines.isNotEmpty() || plu.name.isNotBlank())
            .setBit(S2_SPECIAL_MSG, plu.specialMsgLines.isNotEmpty())
            .setBit(S2_INGREDIENT, plu.ingredientLines.isNotEmpty())

        val s2b3 = 0
            .setBit(S3_SELL_BY_DATE, plu.sellByDate > 0)
            .setBit(S3_USED_BY_DATE, plu.usedByDate > 0)
            .setBit(S3_PACKED_DATE, plu.packedDate > 0)
            .setBit(S3_PLACE_NUMBER, plu.placeNo > 0)

        return byteArrayOf(
            s1b1.toByte(), s1b2.toByte(),
            s2b1.toByte(), s2b2.toByte(), s2b3.toByte(),
        )
    }

    /**
     * Encode a full PLU record: PLU number, status bytes, price, cost, tare, qty,
     * group, label formats, barcode and text lines, each little-endian where multi-byte.
     */
    fun encode(plu: PluItem, priceDecimals: Int = 2): ByteArray {
        val out = ByteArrayOutputStream()
        // PLU number: 4 bytes LE
        writeInt32LE(out, plu.pluNo)
        // status1 (2 bytes) + status2 (3 bytes)
        out.write(statusBytes(plu))
        // unit price: 4 bytes LE (minor units)
        writeInt32LE(out, plu.unitPrice.toInt())
        // cost: 4 bytes LE
        writeInt32LE(out, plu.cost.toInt())
        // tare: 2 bytes LE (grams)
        writeInt16LE(out, plu.tare)
        // preset qty: 2 bytes LE
        writeInt16LE(out, plu.qty)
        // main group: 2 bytes LE
        writeInt16LE(out, plu.mainGroup)
        // label format 1, 2: 1 byte each
        out.write(plu.label1Format and 0xFF)
        out.write(plu.label2Format and 0xFF)
        // barcode type (0=EAN,9=ITF): 1 byte
        out.write(plu.barcodeType and 0xFF)
        // barcode format: 1 byte
        out.write(plu.barcodeFormat and 0xFF)
        // flag data F1F2: 2 ASCII hex chars padded to 2 bytes
        val flag = plu.flagData.padEnd(2, '0').take(2)
        out.write(flag.toByteArray(Charsets.US_ASCII))
        // bar right-side data type: 1 byte
        out.write(plu.barRightDataType and 0xFF)
        // EAN/item code: fixed 10-byte field
        writeFixed(out, plu.eanCode, 10)
        // place no / tax no: 1 byte each
        out.write(plu.placeNo and 0xFF)
        out.write(plu.taxRateNo and 0xFF)
        // used-by / sell-by / packed date: 2 bytes LE each
        writeInt16LE(out, plu.usedByDate)
        writeInt16LE(out, plu.sellByDate)
        writeInt16LE(out, plu.packedDate)

        // Commodity text lines: count byte, then (fontNo, len, chars) per line
        writeTextLines(out, plu.commodityLines.ifEmpty {
            listOf(com.scaleplex.core.model.PluTextLine(fontNo = 23, text = plu.name))
        })
        // Ingredient lines
        writeTextLines(out, plu.ingredientLines)
        // Special message lines
        writeTextLines(out, plu.specialMsgLines)

        return out.toByteArray()
    }

    private fun writeTextLines(out: ByteArrayOutputStream, lines: List<com.scaleplex.core.model.PluTextLine>) {
        out.write(lines.size and 0xFF)
        for (line in lines) {
            out.write(line.fontNo and 0xFF)
            val bytes = line.text.toByteArray(charset("GBK"))
            out.write(bytes.size and 0xFF)
            out.write(bytes)
        }
    }

    private fun writeFixed(out: ByteArrayOutputStream, value: String, length: Int) {
        val bytes = value.toByteArray(Charsets.US_ASCII).copyOf(length)
        out.write(bytes)
    }

    private fun writeInt16LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun writeInt32LE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun Int.setBit(pos: Int, value: Boolean): Int =
        if (value) this or (1 shl pos) else this and (1 shl pos).inv()
}
