package com.scaleplex.core.model

import kotlinx.datetime.LocalDate

/**
 * 计价单位（托利多下发字段；寺冈仅计重/计件，单位不参与下发）。
 * [PCS] 表示计件，与 [PluItem.weightFlag] 保持联动。
 */
enum class PluUnit(val id: Int, val label: String) {
    KG(0, "kg"),
    G(1, "g"),
    LB(2, "lb"),
    JIN(3, "500g(斤)"),
    PCS(4, "PCS(计数)");

    companion object {
        fun fromId(id: Int): PluUnit = entries.firstOrNull { it.id == id } ?: KG
    }
}

/**
 * A single PLU (price look-up) record — the universal商品 record pushed to / read from scales.
 *
 * Modeled from the DIGI SM series PLU definition (see `PLU.pas`, `PLU_Demo1.csv`, `SM.ini`)
 * but kept brand-agnostic so every driver maps it onto its own wire format.
 *
 * Money values (price / cost / tare) are stored as integer "minor units" (e.g. cents)
 * to avoid floating point drift; decimal places come from [ScaleConfig.priceDecimals] /
 * [ScaleConfig.weightDecimals].
 */
data class PluItem(
    val id: Long = 0,
    val pluNo: Int,
    val name: String = "",
    val nameEn: String = "",
    /** 0 = weigh (计重), 1 = count / non-weigh (计件). */
    val weightFlag: Int = 0,
    /** 计价单位（托利多：kg/g/lb/500g/PCS），PCS 与 weightFlag=1 联动。 */
    val unit: Int = PluUnit.KG.id,
    val mainGroup: Int = 997,
    /** Unit price in minor units. */
    val unitPrice: Long = 0,
    /** 会员价 in minor units（托利多下发字段），0 = 未设置。 */
    val memberPrice: Long = 0,
    /** 特价 in minor units（托利多下发字段），0 = 未设置。 */
    val specialPrice: Long = 0,
    /** Cost in minor units. */
    val cost: Long = 0,
    /** Tare weight in grams. */
    val tare: Int = 0,
    /** Preset quantity for count items. */
    val qty: Int = 0,
    /** 0 = EAN, 9 = ITF. */
    val barcodeType: Int = 0,
    /** Barcode layout code from the brand's barcode-format table. */
    val barcodeFormat: Int = 0,
    /** F1F2 flag bytes (2 hex chars, e.g. "22"). */
    val flagData: String = "",
    /** Right-side data type of the item barcode (0=默认,1=Quantity,2=Weight,3=Price). */
    val barRightDataType: Int = 0,
    /** Raw EAN / item code (up to 10 chars). */
    val eanCode: String = "",
    /** Additional (multi) barcode payload. */
    val multiBarcode: String = "",
    /** Shelf-life / used-by days. */
    val usedByDate: Int = 0,
    val sellByDate: Int = 0,
    val packedDate: Int = 0,
    val label1Format: Int = 0,
    val label2Format: Int = 0,
    val placeNo: Int = 0,
    val taxRateNo: Int = 0,
    /** Commodity (品名) text lines: line index -> (fontNo, text). */
    val commodityLines: List<PluTextLine> = emptyList(),
    /** Ingredient (成分) text lines. */
    val ingredientLines: List<PluTextLine> = emptyList(),
    /** Special message (特殊信息) text lines. */
    val specialMsgLines: List<PluTextLine> = emptyList(),
    /** Which label template this PLU is bound to (optional). */
    val labelTemplateId: Long? = null,
    val hotKey: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long = 0,
) {
    val displayName: String get() = name.ifBlank { nameEn }
}

/** One named text line on a label, with a brand-specific font number. */
data class PluTextLine(
    val fontNo: Int = 0,
    val text: String = "",
)
