package com.scaleplex.core.model

/**
 * 标签体系：通用标签 + 托利多三种官方标签编辑器体系 + 顶尖 Aclas 标签编辑器。
 *
 * 各体系的字段面板与常用纸张尺寸对齐对应编辑器：
 * - [GENERIC]：原有通用字段（寺冈 Label Format 2005 / DIGI 驱动使用）；
 * - [MT_VIS_EDIT]：托利多 VisEdit（RL00 等），56×40mm；
 * - [MT_B_LABEL]：托利多 bLabel（bCom/bPro），56×40mm；
 * - [MT_SMART_LABEL]：托利多 SmartLabel（bPlus/FreshBase/bComS），53×44mm；
 * - [DINGTOP]：顶尖 Aclas 标签编辑器（Aclas Label Editor），56×40mm。
 *
 * 字段集合按厂商编辑器左侧面板归类整理；具体秤端字库映射待联调校准。
 */
enum class LabelSystem(
    val defaultWidthMm: Int,
    val defaultHeightMm: Int,
) {
    GENERIC(40, 30),
    MT_VIS_EDIT(56, 40),
    MT_B_LABEL(56, 40),
    MT_SMART_LABEL(53, 44),
    DINGTOP(56, 40);

    /** 是否托利多官方编辑器体系（模板下发需经厂商工具）。 */
    val isMettler: Boolean get() = this == MT_VIS_EDIT || this == MT_B_LABEL || this == MT_SMART_LABEL

    /** 模板本体是否可在软件内直接下发到秤（厂商专用标签格式需使用官方编辑器）。 */
    val canDownloadTemplate: Boolean get() = this == GENERIC

    /** 商品数据字段面板（顺序对齐厂商编辑器左侧字段列表）。 */
    val fieldKinds: List<LabelElementKind>
        get() = when (this) {
            GENERIC -> listOf(
                LabelElementKind.PLU_NO,
                LabelElementKind.TOTAL_PRICE,
                LabelElementKind.UNIT_PRICE,
                LabelElementKind.WEIGHT,
                LabelElementKind.QUANTITY,
                LabelElementKind.QUANTITY_SYMBOL,
                LabelElementKind.PLU_NAME,
                LabelElementKind.BARCODE,
                LabelElementKind.SHOP_NAME,
                LabelElementKind.MAIN_GROUP,
                LabelElementKind.INGREDIENT,
                LabelElementKind.SPECIAL_MSG,
                LabelElementKind.TAXED_PRICE,
                LabelElementKind.TAX,
            )
            MT_VIS_EDIT -> listOf(
                LabelElementKind.PLU_NAME,
                LabelElementKind.SECOND_NAME,
                LabelElementKind.INGREDIENT,
                LabelElementKind.SPECIAL_MSG,
                LabelElementKind.ADVERTISEMENT,
                LabelElementKind.DATE,
                LabelElementKind.WEIGHT,
                LabelElementKind.UNIT_PRICE,
                LabelElementKind.TOTAL_PRICE,
                LabelElementKind.FIXED_WEIGHT,
                LabelElementKind.QUANTITY_SYMBOL,
                LabelElementKind.PLU_NO,
                LabelElementKind.BARCODE,
                LabelElementKind.TARE_WEIGHT,
                LabelElementKind.QUANTITY,
                LabelElementKind.TRACE_CODE,
                LabelElementKind.SHOP_NAME,
                LabelElementKind.MAIN_GROUP,
                LabelElementKind.TAX,
                LabelElementKind.TAXED_PRICE,
            )
            MT_B_LABEL -> listOf(
                LabelElementKind.PLU_NO,
                LabelElementKind.PLU_NAME,
                LabelElementKind.SECOND_NAME,
                LabelElementKind.BARCODE,
                LabelElementKind.UNIT_PRICE,
                LabelElementKind.TOTAL_PRICE,
                LabelElementKind.WEIGHT,
                LabelElementKind.QUANTITY,
                LabelElementKind.QUANTITY_SYMBOL,
                LabelElementKind.DATE,
                LabelElementKind.INGREDIENT,
                LabelElementKind.SPECIAL_MSG,
                LabelElementKind.TRACE_CODE,
                LabelElementKind.TARE_WEIGHT,
                LabelElementKind.SHOP_NAME,
                LabelElementKind.MAIN_GROUP,
                LabelElementKind.TAX,
                LabelElementKind.TAXED_PRICE,
            )
            MT_SMART_LABEL -> listOf(
                LabelElementKind.BARCODE,
                LabelElementKind.PLU_NAME,
                LabelElementKind.SECOND_NAME,
                LabelElementKind.PLU_NO,
                LabelElementKind.UNIT_PRICE,
                LabelElementKind.TOTAL_PRICE,
                LabelElementKind.WEIGHT,
                LabelElementKind.QUANTITY,
                LabelElementKind.QUANTITY_SYMBOL,
                LabelElementKind.INGREDIENT,
                LabelElementKind.SPECIAL_MSG,
                LabelElementKind.ADVERTISEMENT,
                LabelElementKind.DATE,
                LabelElementKind.TRACE_CODE,
                LabelElementKind.SHOP_NAME,
                LabelElementKind.MAIN_GROUP,
                LabelElementKind.TAX,
                LabelElementKind.TAXED_PRICE,
            )
            // 顶尖 Aclas 标签编辑器：字段按左侧「全部字段」树整理
            DINGTOP -> listOf(
                LabelElementKind.PLU_NAME,
                LabelElementKind.SECOND_NAME,
                LabelElementKind.THIRD_NAME,
                LabelElementKind.PLU_NO,
                LabelElementKind.BARCODE,
                LabelElementKind.PRODUCTION_DATE,
                LabelElementKind.SHELF_LIFE,
                LabelElementKind.WEIGHT,
                LabelElementKind.GROSS_WEIGHT,
                LabelElementKind.NET_WEIGHT,
                LabelElementKind.TARE_WEIGHT,
                LabelElementKind.QUANTITY,
                LabelElementKind.QUANTITY_SYMBOL,
                LabelElementKind.UNIT_PRICE,
                LabelElementKind.TOTAL_PRICE,
                LabelElementKind.DATE,
                LabelElementKind.TEXT_TITLE,
                LabelElementKind.TEXT_CONTENT,
                LabelElementKind.REMARK_1,
                LabelElementKind.REMARK_2,
                LabelElementKind.REMARK_3,
                LabelElementKind.REMARK_4,
                LabelElementKind.PREPACK_DISCOUNT,
                LabelElementKind.ADVERTISEMENT,
                LabelElementKind.TEMPERATURE,
                LabelElementKind.INGREDIENT,
                LabelElementKind.SPECIAL_MSG,
            )
        }

    /** 图形元素面板（文本/日期/二维码/外框/图像等）。 */
    val shapeKinds: List<LabelElementKind>
        get() = when (this) {
            GENERIC, MT_VIS_EDIT, MT_B_LABEL, DINGTOP -> listOf(
                LabelElementKind.TEXT,
                LabelElementKind.DATE,
                LabelElementKind.QRCODE,
                LabelElementKind.FRAME,
                LabelElementKind.LOGO,
            )
            // SmartLabel 的"组件"面板把条码/图片/图形/标签分开管理
            MT_SMART_LABEL -> listOf(
                LabelElementKind.TEXT,
                LabelElementKind.QRCODE,
                LabelElementKind.FRAME,
                LabelElementKind.LOGO,
            )
        }

    /** 常用纸张尺寸预设（宽×高，mm），首个为该体系默认尺寸。 */
    val sizePresets: List<Pair<Int, Int>>
        get() = when (this) {
            GENERIC -> listOf(40 to 30, 50 to 30, 58 to 40, 60 to 40, 80 to 50)
            MT_VIS_EDIT, MT_B_LABEL, DINGTOP -> listOf(56 to 40, 50 to 30, 58 to 40, 60 to 40, 80 to 50)
            MT_SMART_LABEL -> listOf(53 to 44, 56 to 40, 60 to 40, 80 to 50)
        }

    companion object {
        fun fromId(id: String): LabelSystem =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: GENERIC
    }
}
