package com.scaleplex.core.model

/**
 * Supported scale brands / drivers. Each brand maps to one [com.scaleplex.driver.api.IScaleDriver]
 * implementation registered in the [com.scaleplex.driver.api.ScaleDriverFactory].
 *
 * [defaultPort] 为各品牌网络传秤的默认端口（来源：各品牌秤设置端口对照表）：
 * - 顶尖 5002（备选 5001，官方 Demo 硬编码一致）；
 * - 托利多 3001；大华 4001（友声兼容大华模式同为 4001）；
 * - 寺冈 TWS 协议的"端口"参数实际为秤号（LabelNet 配置秤号、秤端 IP 设置页"端口号"栏填秤号，
 *   通常填秤 IP 最后 3 位），默认秤号 1；
 * - 其余品牌无权威数据，暂用 1111 占位（待校准）。
 *
 * 一期（P1）：DIGI、DAHUA、YOUSHENG、HIHONG
 * 二期（P2）：METTLER_TOLEDO、CAS、DINGTOP
 * 三期（P3）：ISHIDA、BIZERBA 等
 */
enum class ScaleBrand(val id: String, val displayName: String, val defaultPort: Int) {
    DIGI("digi", "DIGI 寺冈", 1),
    DAHUA("dahua", "大华 DAHUA", 4001),
    YOUSHENG("yousheng", "友声 YouSheng", 4001),
    HIHONG("hihong", "辉宏 HuiHong", 1111),
    METTLER_TOLEDO("mettler_toledo", "梅特勒-托利多 Mettler Toledo", 3001),
    CAS("cas", "CAS", 1111),
    DINGTOP("dingtop", "顶尖 DingTop", 5002),
    ISHIDA("ishida", "石田 Ishida", 1111),
    BIZERBA("bizerba", "碧彩 Bizerba", 1111),
    GENERIC("generic", "通用 / 其他", 1111);

    companion object {
        fun fromId(id: String): ScaleBrand =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERIC
    }
}
