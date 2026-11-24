package com.scaleplex.core.model

/**
 * 托利多（Mettler Toledo）标签编辑器族（来源：Scale Manager「标签编辑」菜单）。
 *
 * 机型与编辑器对应关系：
 * - [VIS_EDIT_RL00]：RL00 / RL00+（RL00 专用 VisEdit，安装目录 VisEdit_RL00）；
 * - [B_LABEL]：bCom / bPro；
 * - [SMART_LABEL]：bPlus / FreshBase / bComS；
 * - [VIS_EDIT]：其余机型默认使用 VisEdit（8442 / PC[Apollo] / bHigh / bMobile / Etica / I15，待实机确认）。
 *
 * [folder] 为 Scale Manager 安装目录下的子目录，[executable] 为启动文件。
 */
enum class MtLabelEditor(
    val displayName: String,
    val folder: String,
    val executable: String,
) {
    VIS_EDIT("VisEdit", "VisEdit", "VisEdit.exe"),
    VIS_EDIT_RL00("VisEdit (RL00)", "VisEdit_RL00", "VisEdit.exe"),
    B_LABEL("bLabel", "bLabel", "bLabel.exe"),
    SMART_LABEL("SmartLabel", "SmartLabel", "start.bat");

    companion object {

        /** 按设备型号匹配标签编辑器（大小写不敏感；bComS 须先于 bCom 判定）。 */
        fun fromModel(model: String): MtLabelEditor {
            val m = model.trim().uppercase().replace(" ", "")
            return when {
                m.contains("RL00") -> VIS_EDIT_RL00
                m.contains("BCOMS") -> SMART_LABEL
                m.contains("BCOM") || m.contains("BPRO") -> B_LABEL
                m.contains("BPLUS") || m.contains("FRESHBASE") -> SMART_LABEL
                else -> VIS_EDIT
            }
        }
    }
}
