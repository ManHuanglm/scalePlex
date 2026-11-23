package com.scaleplex.core.model

/**
 * Visual label template. Stored as JSON in the DB and exportable/importable.
 * The editor renders elements onto a millimeter canvas; the DIGI driver maps
 * element kinds onto its label-format codes when downloading to a scale.
 */
data class LabelTemplate(
    val id: Long = 0,
    val name: String,
    /** Paper size in millimeters. */
    val widthMm: Int = 40,
    val heightMm: Int = 30,
    val elements: List<LabelElement> = emptyList(),
    val note: String = "",
)

/** Kinds of element that can appear on a label. */
enum class LabelElementKind {
    TEXT, BARCODE, QRCODE, WEIGHT, UNIT_PRICE, TOTAL_PRICE, DATE, LOGO, PLU_NAME, SHOP_NAME
}

data class LabelElement(
    val kind: LabelElementKind,
    val xMm: Float = 0f,
    val yMm: Float = 0f,
    val widthMm: Float = 20f,
    val heightMm: Float = 6f,
    val text: String = "",
    val fontName: String = "SimSun",
    val fontSizePt: Int = 10,
    /** 0 = left, 1 = center, 2 = right. */
    val align: Int = 0,
    val rotation: Int = 0,
)
