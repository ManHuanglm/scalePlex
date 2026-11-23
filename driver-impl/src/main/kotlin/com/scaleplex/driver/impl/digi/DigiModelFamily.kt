package com.scaleplex.driver.impl.digi

/**
 * 寺冈 DIGI 机型族划分（银豹《寺冈条码秤 传秤下发》+ LabelNet 机型选择列表）：
 *
 * - [FAMILY_8442]：SM-80 / SM-100 / SM-110（开机版本 STE47 及以下）→
 *   TOP2000 / Digi2015 工具链（TWS.exe + SMScale.dll，本驱动已接入）；
 * - [FAMILY_B]：SM-110n / SM-120（STE57+）/ SM-5100 及 b 系列机种
 *   （bCom/bComS/bHigh/bMobile/bPlus/bPro/bTwin）→ LabelNet 工具链
 *   （Access mdb + TWSDataTrans.ocx，与 TWS 文件协议互不兼容），本驱动暂未接入。
 *
 * 机型号选择列表还含 PC[Apollo]/Etica/FreshBase/I15/RL00 等其他 DIGI 产品线，暂不区分。
 */
enum class DigiModelFamily(val hint: String) {
    FAMILY_8442("8442 族（SM-80/SM-100/SM-110 STE47-）：TOP2000/Digi2015 工具链，本驱动支持"),
    FAMILY_B("b 族（SM-110n STE57+/SM-120/SM-5100）：需 LabelNet 工具链，本驱动暂未接入"),
    UNKNOWN("型号未识别，默认按 8442 族处理");

    companion object {

        /** b 族机型号关键词（LabelNet 机型列表 + 银豹文档）。注意 SM-110N 须先于 SM-110 判定。 */
        private val B_FAMILY_KEYWORDS = listOf(
            "SM-110N", "SM110N", "SM-120", "SM120", "SM-5100", "SM5100", "STE57",
            "BCOM", "BPRO", "BTWIN", "BHIGH", "BMOBILE", "BPLUS",
        )

        /** 8442 族机型号关键词。 */
        private val FAMILY_8442_KEYWORDS = listOf(
            "SM-80", "SM80", "SM-100", "SM100", "SM-110", "8442", "STE47",
        )

        /** 按设备型号推断机型族：b 族关键词优先，其次 8442 族，未知默认 8442。 */
        fun fromModel(model: String): DigiModelFamily {
            val m = model.trim().uppercase().replace(" ", "")
            if (m.isEmpty()) return UNKNOWN
            if (B_FAMILY_KEYWORDS.any { m.contains(it) }) return FAMILY_B
            if (FAMILY_8442_KEYWORDS.any { m.contains(it) }) return FAMILY_8442
            return UNKNOWN
        }
    }
}
