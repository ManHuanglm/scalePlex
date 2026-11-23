package com.scaleplex.core.model

/**
 * Supported scale brands / drivers. Each brand maps to one [com.scaleplex.driver.api.IScaleDriver]
 * implementation registered in the [com.scaleplex.driver.api.ScaleDriverFactory].
 *
 * 一期（P1）：DIGI、DAHUA、YOUSHENG、HIHONG
 * 二期（P2）：METTLER_TOLEDO、CAS、DINGTOP
 * 三期（P3）：ISHIDA、BIZERBA 等
 */
enum class ScaleBrand(val id: String, val displayName: String) {
    DIGI("digi", "DIGI 寺冈"),
    DAHUA("dahua", "大华 DAHUA"),
    YOUSHENG("yousheng", "友声 YouSheng"),
    HIHONG("hihong", "辉宏 HuiHong"),
    METTLER_TOLEDO("mettler_toledo", "梅特勒-托利多 Mettler Toledo"),
    CAS("cas", "CAS"),
    DINGTOP("dingtop", "顶尖 DingTop"),
    ISHIDA("ishida", "石田 Ishida"),
    BIZERBA("bizerba", "碧彩 Bizerba"),
    GENERIC("generic", "通用 / 其他");

    companion object {
        fun fromId(id: String): ScaleBrand =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERIC
    }
}
