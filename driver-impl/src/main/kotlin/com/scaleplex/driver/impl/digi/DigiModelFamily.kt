package com.scaleplex.driver.impl.digi

/**
 * 寺冈 DIGI 机型族划分（银豹《寺冈条码秤 传秤下发》+ LabelNet 机型列表 + 真机校准）：
 *
 * - [FAMILY_8442]：SM-80 / SM-100 / SM-110（开机版本 STE47 及以下）→
 *   TOP2000 / Digi2015 工具链（TWS.exe + SMScale.dll）；
 * - [FAMILY_ETP]：SM-120（ETP 平台）→ **FTP+CSV 接口**（端口 21，用户 admin/admin，
 *   文件 plu0uall.csv；真机校准 2026-11-25：端口 25 的 TWS 协议返回 -5，
 *   端口 2056 的 SMScaleAPI socket 亦不可用，仅 FTP 可用）；
 * - [FAMILY_B]：SM-110N（STE57+）/ SM-5100 → SMScaleAPI.dll socket API
 *   （端口 = 2000 + IP 尾段，本驱动已接入）。
 *
 * 机型号选择列表还含 PC[Apollo]/Etica/FreshBase/I15/RL00 等其他 DIGI 产品线，暂不区分。
 */
enum class DigiModelFamily(val hint: String) {
    FAMILY_8442("8442 族（SM-80/SM-100/SM-110 STE47-）：TOP2000/Digi2015 工具链（TWS.exe + SMScale.dll）"),
    FAMILY_ETP("ETP 族（SM-120）：FTP+CSV 接口（端口 21，plu0uall.csv），本驱动支持回收"),
    FAMILY_B("b 族（SM-110N STE57+/SM-5100）：SMScaleAPI.dll socket API，本驱动支持"),
    UNKNOWN("型号未识别，默认按 8442 族处理");

    companion object {

        /** b 族机型号关键词（LabelNet 机型列表 + 银豹文档）。注意 SM-110N 须先于 SM-110 判定。 */
        private val B_FAMILY_KEYWORDS = listOf(
            "SM-110N", "SM110N", "SM-5100", "SM5100", "STE57",
            "BCOM", "BPRO", "BTWIN", "BHIGH", "BMOBILE", "BPLUS",
        )

        /** ETP 族机型号关键词（真机校准：SM-120 为 ETP 平台，FTP+CSV）。 */
        private val ETP_KEYWORDS = listOf("SM-120", "SM120")

        /** 8442 族机型号关键词。 */
        private val FAMILY_8442_KEYWORDS = listOf(
            "SM-80", "SM80", "SM-100", "SM100", "SM-110", "8442", "STE47",
        )

        /** 按设备型号推断机型族：b 族关键词优先（SM-110N 先于 SM-110），其次 ETP，再次 8442。 */
        fun fromModel(model: String): DigiModelFamily {
            val m = model.trim().uppercase().replace(" ", "")
            if (m.isEmpty()) return UNKNOWN
            if (B_FAMILY_KEYWORDS.any { m.contains(it) }) return FAMILY_B
            if (ETP_KEYWORDS.any { m.contains(it) }) return FAMILY_ETP
            if (FAMILY_8442_KEYWORDS.any { m.contains(it) }) return FAMILY_8442
            return UNKNOWN
        }
    }
}
