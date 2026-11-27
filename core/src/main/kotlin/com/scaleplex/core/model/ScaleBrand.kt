package com.scaleplex.core.model

/**
 * 顶尖（Aclas）已知机型清单（来源：LINK69 机型选择列表）。
 * 型号仅用于展示与区分机型；通信协议由 AclasSDK GetDevicesInfo 自协商。
 */
val DINGTOP_MODELS: List<String> = listOf(
    "LS2X/LS3X标签秤",
    "LS2SX自助秤",
    "LH1X吊挂秤",
    "LS6X标签秤",
    "LS5SX自助秤",
    "LS5X有杆标签秤",
    "LS5X无杆标签秤",
    "LP1X标签打印机",
    "LP54标签打印机",
    "TSX触控秤",
    "BS7有线基站",
    "HT4手持终端机",
)

/**
 * 托利多（Mettler Toledo）ScaleKey 机型清单（来源：ScaleKey 机型选择列表）。
 * 型号即 XML 任务中的 ScaleType，需与秤端实际机型一致。
 */
val METTLER_MODELS: List<String> = listOf(
    "8442",
    "PC[Apollo]",
    "bCom",
    "bComS",
    "bHigh",
    "bMobile",
    "bPlus",
    "bPro",
    "bTwin",
    "Etica",
    "FreshBase",
    "I15",
    "RL00/RL00+",
)

/**
 * Supported scale brands / drivers. Each brand maps to one [com.scaleplex.driver.api.IScaleDriver]
 * implementation registered in the [com.scaleplex.driver.api.ScaleDriverFactory].
 *
 * [defaultPort] 为各品牌网络传秤的默认端口（来源：各品牌秤设置端口对照表）：
 * - 顶尖 5002（备选 5001，官方 Demo 硬编码一致）；
 * - 托利多 3001；大华 4001；
 * - 友声 33571（真机校准 2026-11-27：JH 族 TCP 通讯口，DLL 内置默认 33581）；
 * - 凯士 20304（真机校准 2026-11-26：CL 族条码秤 TCP 监听端口）；
 * - 容大 5001（真机校准 2026-11-26：RLS1000 条码秤 UDP 传秤端口，TCP 同号不开放）；
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
    YOUSHENG("yousheng", "友声 YouSheng", 33571),
    HIHONG("hihong", "辉宏 HuiHong", 1111),
    METTLER_TOLEDO("mettler_toledo", "梅特勒-托利多 Mettler Toledo", 3001),
    CAS("cas", "CAS 凯士", 20304),
    RONGTA("rongta", "容大 Rongta", 5001),
    DINGTOP("dingtop", "顶尖 DingTop", 5002),
    ISHIDA("ishida", "石田 Ishida", 1111),
    BIZERBA("bizerba", "碧彩 Bizerba", 1111),
    GENERIC("generic", "通用 / 其他", 1111);

    /** 该品牌可选机型；空列表表示无预设机型，自由输入。 */
    val models: List<String>
        get() = when (this) {
            DINGTOP -> DINGTOP_MODELS
            METTLER_TOLEDO -> METTLER_MODELS
            else -> emptyList()
        }

    companion object {
        fun fromId(id: String): ScaleBrand =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERIC
    }
}
