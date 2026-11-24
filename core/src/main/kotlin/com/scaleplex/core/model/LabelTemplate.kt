package com.scaleplex.core.model

import java.util.UUID

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
    /** 标签体系：通用 / 托利多 VisEdit / bLabel / SmartLabel。 */
    val system: LabelSystem = LabelSystem.GENERIC,
)

/**
 * Kinds of element that can appear on a label. Aligned with the DIGI "Label Format 2005"
 * PLU data fields (商品数据：PLU编号/价格/重量/成分/特殊信息...) plus generic shapes.
 * Serialized by name into JSON; unknown names degrade to TEXT on read.
 */
enum class LabelElementKind {
    /** 自由文本。 */
    TEXT,

    /** 条码（EAN/ITF）。 */
    BARCODE,

    /** 二维码。 */
    QRCODE,

    /** 称重打印重量 (kg)。 */
    WEIGHT,

    /** 单价。 */
    UNIT_PRICE,

    /** 合计金额。 */
    TOTAL_PRICE,

    /** 税后价格。 */
    TAXED_PRICE,

    /** 税率号。 */
    TAX,

    /** 打印日期。 */
    DATE,

    /** 图像 / 店标（寺冈 Label Format 2005 的"图像"，首期编码跳过）。 */
    LOGO,

    /** 商品名称。 */
    PLU_NAME,

    /** 店名。 */
    SHOP_NAME,

    /** PLU编号。 */
    PLU_NO,

    /** 数量（计件）。 */
    QUANTITY,

    /** 数量单位 (kg/pcs)。 */
    QUANTITY_SYMBOL,

    /** 成分。 */
    INGREDIENT,

    /** 特殊信息。 */
    SPECIAL_MSG,

    /** 部门号。 */
    MAIN_GROUP,

    /** 外框（矩形框线，无文本）。 */
    FRAME,

    /** 第二品名（托利多 VisEdit/bLabel/SmartLabel）。 */
    SECOND_NAME,

    /** 广告用语。 */
    ADVERTISEMENT,

    /** 固定重量。 */
    FIXED_WEIGHT,

    /** 皮重。 */
    TARE_WEIGHT,

    /** 追溯码。 */
    TRACE_CODE,

    /** 第三品名（顶尖 Aclas：单品名称3）。 */
    THIRD_NAME,

    /** 备注1（顶尖 Aclas）。 */
    REMARK_1,

    /** 备注2。 */
    REMARK_2,

    /** 备注3。 */
    REMARK_3,

    /** 备注4。 */
    REMARK_4,

    /** 文本信息标题头（顶尖 Aclas：文本信息1(标题头)）。 */
    TEXT_TITLE,

    /** 文本信息内容（顶尖 Aclas：文本信息1(内容)）。 */
    TEXT_CONTENT,

    /** 预包装折扣。 */
    PREPACK_DISCOUNT,

    /** 温度。 */
    TEMPERATURE,

    /** 毛重。 */
    GROSS_WEIGHT,

    /** 净重。 */
    NET_WEIGHT,

    /** 生产日期。 */
    PRODUCTION_DATE,

    /** 保质期。 */
    SHELF_LIFE,

    /** 折扣值（寺冈 Label Format 2005）。 */
    DISCOUNT,

    /** 秤号。 */
    SCALE_NO,

    /** 售货员1。 */
    SELLER,

    /** 部门号（与主组号区分）。 */
    DEPARTMENT,

    /** 多条码（寺冈 SM-120：MULTI BARCODE）。 */
    MULTI_BARCODE,

    /** 平均价。 */
    AVERAGE_PRICE,

    /** 平均重量。 */
    AVERAGE_WEIGHT,

    /** 积分（BONUS POINT）。 */
    BONUS_POINT,

    /** 原产地。 */
    ORIGIN,
}

data class LabelElement(
    val id: String = UUID.randomUUID().toString(),
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
