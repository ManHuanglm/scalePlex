package com.scaleplex.driver.impl.aclas

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.core.model.PluUnit
import com.scaleplex.core.model.ShelfLifeUnit
import com.scaleplex.core.util.Numbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶尖 Aclas PLU 文本编解码（information/共享文件/DLL69_Demo_CN220523 官方格式）：
 *
 * - 编码：UTF-16LE + BOM，Tab 分隔，首行表头（59 字段，见 Console/PLU Demo.txt）；
 * - 供 `AclasSDK.dll` 的 `Sync_ExecTaskA(…, DataType=PLU, 文件路径)` 使用，
 *   ProcType 0=Down 下发 / 1=UP 回收（回收输出同格式文件）/ 3=Del（文件名 "*"）。
 *
 * 字段映射（PluItem → Aclas）：
 * ID←pluNo、ItemCode←eanCode、DepartmentID←mainGroup、Name1←name、Name2←nameEn、
 * Price←unitPrice（元，2 位小数）、UnitID←PluUnit（kg=4/g=1/lb=6/500g=7/PCS=10）、
 * Label1ID←label1Format、ValidDate←effectiveShelfLifeDays（保质期，天）、
 * TareValue←tare（g）、OriginID←placeNo；
 * Flag1/Flag2 用官方样例常量 60/240（位标志，待真机校准）。
 * 配料/成分（Note1）、热键（HotKey）、特殊信息为独立 DataType，v1 暂不下发。
 */
object AclasTxtCodec {

    /** 官方 Demo 表头（59 列，与 Console/PLU Demo.txt 逐列一致）。 */
    val HEADER: List<String> = listOf(
        "ID", "ItemCode", "DepartmentID", "GroupID", "Name1", "Name2", "Name3",
        "Price", "UnitID", "BarcodeType1", "BarcodeType2", "Label1ID", "Label2ID",
        "ProducedDate", "FreshnessDate", "ValidDate",
        "PackageType", "PackageWeight", "PackagePrice", "PackageRange", "PackageDays", "PackageHours",
        "Text1ID", "Text2ID", "Text3ID", "Text4ID", "Text5ID", "Text6ID", "Text7ID", "Text8ID",
        "DiscountID", "DiscountRate", "HalfDiscount", "QuarterDiscount",
        "TareID", "TareValue", "IceID", "ICEValue", "OriginID", "TraceabilityID",
        "LimitPrice", "Tax1", "Tax2", "Tax3", "Tax4", "Flag1", "Flag2", "Flag3",
        "ProducedDateRule", "FreshnessDateFrom", "ValidDateFrom", "PackageDateFrom",
        "SpeedCode", "Position1", "SalesCategory",
        "DiscountBeginTime", "DiscountEndTime", "DiscountPrice", "DiscountFlag",
    )

    private const val TAB = '\t'

    /** Delphi TDate 零值（1899/12/30），官方样例中未设置的日期字段用它。 */
    const val ZERO_DATE = "1899/12/30"

    /** 官方样例常量：Flag1=60、Flag2=240（位标志语义待真机校准）。 */
    const val FLAG1_DEFAULT = 60
    const val FLAG2_DEFAULT = 240

    // ---- 编码 ----

    /** 编码为完整文件内容（UTF-16LE 由调用方写文件时指定；此处返回含表头与行尾的文本）。 */
    fun encodeFile(items: List<PluItem>, priceDecimals: Int = 2): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString("$TAB")).append("\r\n")
        for (p in items) {
            sb.append(encodeLine(p, priceDecimals)).append("\r\n")
        }
        return sb.toString()
    }

    /** 编码单行（59 列 Tab 分隔）。 */
    fun encodeLine(p: PluItem, priceDecimals: Int = 2): String {
        val c = arrayOfNulls<String>(HEADER.size)
        c[0] = p.pluNo.toString()
        c[1] = p.eanCode
        c[2] = p.mainGroup.toString()
        c[3] = "1" // GroupID：默认组（样例值，组资料下发待后续）
        c[4] = p.name
        c[5] = p.nameEn
        c[6] = "" // Name3
        c[7] = formatPrice(p.unitPrice, priceDecimals)
        c[8] = unitId(p).toString()
        c[9] = p.barcodeType.toString()
        c[10] = "0" // BarcodeType2（第二条码类型，暂不用）
        c[11] = p.label1Format.toString()
        c[12] = p.label2Format.toString()
        c[13] = "" // ProducedDate：空 = 由秤端按 ProducedDateRule 生成
        c[14] = "0" // FreshnessDate（保鲜期，天）
        c[15] = p.effectiveShelfLifeDays.toString() // ValidDate（保质期，天）
        c[16] = "0" // PackageType
        c[17] = "0" // PackageWeight
        c[18] = "0" // PackagePrice
        c[19] = "0" // PackageRange
        c[20] = "0" // PackageDays
        c[21] = "0" // PackageHours
        for (i in 0 until 8) c[22 + i] = "0" // Text1ID..Text8ID
        c[30] = "0" // DiscountID
        c[31] = "0" // DiscountRate
        c[32] = "0" // HalfDiscount
        c[33] = "0" // QuarterDiscount
        c[34] = "0" // TareID
        c[35] = p.tare.toString() // TareValue（g）
        c[36] = "0" // IceID
        c[37] = "0" // ICEValue
        c[38] = p.placeNo.toString() // OriginID（产地编号）
        c[39] = "0" // TraceabilityID
        c[40] = "0" // LimitPrice
        for (i in 0 until 4) c[41 + i] = "0" // Tax1..Tax4
        c[45] = FLAG1_DEFAULT.toString()
        c[46] = FLAG2_DEFAULT.toString()
        c[47] = "0" // Flag3
        c[48] = "0" // ProducedDateRule
        c[49] = "0" // FreshnessDateFrom
        c[50] = "0" // ValidDateFrom
        c[51] = "0" // PackageDateFrom
        c[52] = if (p.hotKey > 0) p.hotKey.toString() else "" // SpeedCode（速查码）
        c[53] = "0" // Position1
        c[54] = "0" // SalesCategory
        c[55] = ZERO_DATE // DiscountBeginTime
        c[56] = ZERO_DATE // DiscountEndTime
        c[57] = "0" // DiscountPrice
        c[58] = "0" // DiscountFlag
        return c.map { it ?: "" }.joinToString("$TAB")
    }

    /** PluItem.unit/weightFlag → Aclas UnitID（kg=4/g=1/lb=6/500g(斤)=7/PCS=10，见 UnitTbl 与 ImportConfigTbl 映射）。 */
    private fun unitId(p: PluItem): Int = when (PluUnit.fromId(p.unit)) {
        PluUnit.KG -> 4
        PluUnit.G -> 1
        PluUnit.LB -> 6
        PluUnit.JIN -> 7
        PluUnit.PCS -> 10
    }

    /** 价格文本（元）：去掉无意义的小数尾零（官方样例 "10" 而非 "10.00"）。 */
    private fun formatPrice(minor: Long, decimals: Int): String =
        Numbers.minorToDisplay(if (minor < 0) 0 else minor, decimals).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    // ---- 解析（PLU 回收：ProcType=UP 输出同格式文件） ----

    /**
     * 解析 Aclas PLU 文本（回收输出）。首行按表头列名映射，兼容 58/59/61 列
     * （Console Demo 59 列 / LiNK69 导出 61 列含 Message1、UnitPrintName）。
     */
    fun parseFileText(text: String): List<PluItem> {
        val lines = text.split("\r\n", "\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        // 表头（含 BOM 清理）
        val headerCells = lines[0].trimStart('\uFEFF').split(TAB).map { it.trim() }
        if (headerCells.firstOrNull()?.uppercase() != "ID") return emptyList()
        val colIndex = headerCells.withIndex().associate { (i, name) -> name.uppercase() to i }

        fun cell(cells: List<String>, name: String): String =
            colIndex[name.uppercase()]?.let { cells.getOrNull(it)?.trim() } ?: ""

        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(TAB)
            val id = cell(cells, "ID").toIntOrNull() ?: return@mapNotNull null
            val price = Numbers.displayToMinor(cell(cells, "Price"), 2)
            val unitAclas = cell(cells, "UnitID").toIntOrNull() ?: 4
            PluItem(
                pluNo = id,
                eanCode = cell(cells, "ItemCode"),
                mainGroup = cell(cells, "DepartmentID").toIntOrNull() ?: 997,
                name = cell(cells, "Name1"),
                nameEn = cell(cells, "Name2"),
                unitPrice = price,
                unit = aclasToUnit(unitAclas),
                weightFlag = if (unitAclas >= 9) 1 else 0,
                label1Format = cell(cells, "Label1ID").toIntOrNull() ?: 0,
                label2Format = cell(cells, "Label2ID").toIntOrNull() ?: 0,
                // 回收端 ValidDate 即天数，单位复位为天
                usedByDate = cell(cells, "ValidDate").toIntOrNull() ?: 0,
                shelfLifeUnit = ShelfLifeUnit.DAY.id,
                tare = cell(cells, "TareValue").toIntOrNull() ?: 0,
                placeNo = cell(cells, "OriginID").toIntOrNull() ?: 0,
                commodityLines = listOfNotNull(
                    cell(cells, "Name1").takeIf { it.isNotBlank() }?.let { PluTextLine(text = it) },
                ),
            )
        }
    }

    /** Aclas UnitID → PluUnit（kg=4/g=1/lb=6/oz=5/500g=7/其余 PCS 视为计件）。 */
    private fun aclasToUnit(id: Int): Int = when (id) {
        4 -> PluUnit.KG.id
        1 -> PluUnit.G.id
        6 -> PluUnit.LB.id
        7 -> PluUnit.JIN.id
        else -> if (id >= 9) PluUnit.PCS.id else PluUnit.KG.id
    }

    /** 当前时间 → Aclas 日期文本（M/d/yyyy HH:mm，样例格式）。 */
    fun formatNow(): String = SimpleDateFormat("yyyy/M/d HH:mm", Locale.ROOT).format(Date())
}
