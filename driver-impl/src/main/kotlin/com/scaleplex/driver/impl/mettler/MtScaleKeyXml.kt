package com.scaleplex.driver.impl.mettler

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.util.Numbers
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * MTScaleKey 接口 XML 构建与结果解析（依据《MTScaleKey 接口开发文档（对外发布）》§3 传输结构说明）。
 *
 * 调用链：Task.xml(TaskType + DataFile=DeviceList.xml) → Devices/Scale(每台秤挂 Commands 文件)
 * → Commands/Command(CommandText=Item/PresetDefinition/DeviceTime..., DataFile=数据文件)。
 * 文件一律 UTF-8；时间格式 YYYY-MM-DDTHH:mm:ss。
 */
object MtScaleKeyXml {

    /** 任务类型：0 下发数据、3 回收流水、4 检查在线状态、100 设置参数。 */
    const val TASK_TYPE_DOWNLOAD = 0
    const val TASK_TYPE_TRADE_LOG = 3
    const val TASK_TYPE_CHECK_ONLINE = 4

    /** 命令字名称（文档 §3.5）。 */
    const val CMD_ITEM = "Item"
    const val CMD_PRESET = "PresetDefinition"
    const val CMD_DEVICE_TIME = "DeviceTime"

    /** 商品价格默认单位代码（KGM 元/千克；PCS 计数）。 */
    private const val UOM_KGM = "KGM"
    private const val UOM_PCS = "PCS"

    private val xmlEscapes = mapOf("&" to "&amp;", "<" to "&lt;", ">" to "&gt;", "\"" to "&quot;")

    fun escape(value: String): String {
        var s = value
        xmlEscapes.forEach { (k, v) -> s = s.replace(k, v) }
        return s
    }

    fun newTaskId(): String = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.ROOT).format(Date())

    /** Task.xml：文件方式任务入口，DataFile 指向设备列表文件。 */
    fun buildTaskXml(taskId: String, taskType: Int, deviceListFile: String): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<MTTask>
        |    <TaskID>$taskId</TaskID>
        |    <TaskType>$taskType</TaskType>
        |    <DataFile>$deviceListFile</DataFile>
        |</MTTask>
    """.trimMargin()

    /**
     * Devices.xml：电子秤列表，每台秤挂自己的命令字文件。
     * ScaleType 取设备型号（bCom/bPro/bPlus/bTwin/RL00/i15/FreshBase...），缺省 bCom。
     */
    fun buildDeviceListXml(
        devices: List<ScaleDevice>,
        commandFileByDevice: Map<Long, String>,
        priceDecimals: Int = 2,
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<Devices>")
        devices.forEach { d ->
            appendLine("  <Scale>")
            appendLine("    <DeviceID>${d.id}</DeviceID>")
            appendLine("    <ScaleNo>${d.id}</ScaleNo>")
            appendLine("    <ScaleType>${escape(d.model.ifBlank { "bCom" })}</ScaleType>")
            appendLine("    <ConnectType>Network</ConnectType>")
            appendLine("    <ConnectParams>")
            appendLine("""      <NetworkParams Type="Network" Address="${escape(d.ip)}" Port="${d.port}" />""")
            appendLine("    </ConnectParams>")
            appendLine("    <DecimalDigits>${priceDecimals.coerceIn(0, 3)}</DecimalDigits>")
            commandFileByDevice[d.id]?.let { appendLine("    <DataFile>${it}</DataFile>") }
            appendLine("  </Scale>")
        }
        appendLine("</Devices>")
    }

    /** Commands.xml：每台秤的命令字列表，Control 支持 Update/Delete/DeleteAll。 */
    fun buildCommandsXml(commands: List<MtCommand>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<Commands>")
        commands.forEach { cmd ->
            appendLine("  <Command>")
            appendLine("    <CommandText>${cmd.commandText}</CommandText>")
            appendLine("    <CommandID>${cmd.commandId}</CommandID>")
            appendLine("    <Control>${cmd.control}</Control>")
            appendLine("    <ClearData>false</ClearData>")
            cmd.dataFile?.let { appendLine("    <DataFile>${it}</DataFile>") }
            appendLine("  </Command>")
        }
        appendLine("</Commands>")
    }

    /**
     * 商品数据 Data.xml（命令字 Item）：
     * PLU、条码(AlternativeItemID)、名称(Description ItemName zho + eng)、价格(ItemPrice, KGM/PCS)、
     * 保质期(Date BestBefore)、标签号(LabelFormatID)。
     */
    fun buildItemDataXml(
        plus: List<PluItem>,
        priceDecimals: Int = 2,
        language: String = "zho",
        currency: String = "CNY",
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<Data>")
        plus.forEach { plu ->
            val uom = if (plu.weightFlag == 1) UOM_PCS else UOM_KGM
            appendLine("  <Item>")
            appendLine("    <PLU>${plu.pluNo}</PLU>")
            appendLine("    <AlternativeItemIDs>")
            appendLine("      <AlternativeItemID>${plu.pluNo}</AlternativeItemID>")
            appendLine("    </AlternativeItemIDs>")
            appendLine("    <Descriptions>")
            appendLine("""      <Description ID="0" Language="$language" Type="ItemName" Index="0">${escape(plu.name)}</Description>""")
            if (plu.nameEn.isNotBlank()) {
                appendLine("""      <Description ID="1" Language="eng" Type="ItemName" Index="1">${escape(plu.nameEn)}</Description>""")
            }
            appendLine("    </Descriptions>")
            appendLine("    <ItemPrices>")
            appendLine(
                """      <ItemPrice Index="0" UnitOfMeasureCode="$uom" UnitDes="" PriceOverrideFlag="true" """ +
                    """DiscountFlag="false" Quantity="0" Currency="$currency">""" +
                    "${Numbers.minorToDisplay(plu.unitPrice, priceDecimals)}</ItemPrice>",
            )
            appendLine("    </ItemPrices>")
            if (plu.effectiveShelfLifeDays > 0) {
                appendLine("    <Dates>")
                appendLine("""      <Date Type="BestBefore" PrintFlag="True" Unit="Days">${plu.effectiveShelfLifeDays}</Date>""")
                appendLine("    </Dates>")
            }
            appendLine("    <LabelFormats>")
            appendLine("      <LabelFormatID Index=\"0\">${plu.label1Format.coerceAtLeast(1)}</LabelFormatID>")
            appendLine("    </LabelFormats>")
            appendLine("  </Item>")
        }
        appendLine("</Data>")
    }

    /** 时间同步数据（命令字 DeviceTime）。 */
    fun buildDeviceTimeDataXml(now: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<Data>
            |  <DeviceTime AdjustTime="True">
            |    <DeviceTime>${fmt.format(now)}</DeviceTime>
            |  </DeviceTime>
            |</Data>
        """.trimMargin()
    }

    /**
     * 预置键数据（命令字 PresetDefinition）：按热键序号指向 PLU。
     * 键位属性名以文档 §3.5.3 为准，个别属性待实体秤联调校准。
     */
    fun buildPresetDataXml(plus: List<PluItem>): String = buildString {
        val keyed = plus.filter { it.hotKey > 0 }.sortedBy { it.hotKey }
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<Data>")
        appendLine("""  <PresetDefinition Mode="NormalSale">""")
        appendLine("""    <PresetPage Page="1" />""")
        keyed.forEach { plu ->
            appendLine("    <PresetKey KeyNo=\"${plu.hotKey}\">")
            appendLine("      <Destination>")
            appendLine("        <PLU>${plu.pluNo}</PLU>")
            appendLine("      </Destination>")
            appendLine("""      <Title Language="zho">${escape(plu.name)}</Title>""")
            appendLine("    </PresetKey>")
        }
        appendLine("  </PresetDefinition>")
        appendLine("</Data>")
    }

    /**
     * 解析任务结果 XML（TaskResult）：按 DeviceID 汇总每台秤的执行状态。
     * ReturnCode 非 Success / Status 为 Error 记入失败。
     */
    fun parseTaskResultXml(resultXml: String): Map<Long, MtScaleResult> {
        val out = mutableMapOf<Long, MtScaleResult>()
        val scaleRegex = Regex("<ScaleResult>(.*?)</ScaleResult>", RegexOption.DOT_MATCHES_ALL)
        scaleRegex.findAll(resultXml).forEach { m ->
            val block = m.groupValues[1]
            val deviceId = block.intValue("DeviceID") ?: return@forEach
            val returnCode = block.textValue("ReturnCode") ?: "Unknown"
            val status = block.textValue("Status") ?: "Unknown"
            val percent = block.intValue("Percent") ?: 0
            val commandErrors = Regex("<CommandResult>(.*?)</CommandResult>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block)
                .map { it.groupValues[1] }
                .filter { (it.textValue("ReturnCode") ?: "Success") != "Success" }
                .mapNotNull { it.textValue("CommandID") }
                .toList()
            out[deviceId.toLong()] = MtScaleResult(
                deviceId = deviceId.toLong(),
                returnCode = returnCode,
                status = status,
                percent = percent,
                failedCommandIds = commandErrors,
            )
        }
        return out
    }

    /**
     * 解析商品数据 Data XML（Item Read/ReadAll 回收结果）→ PLU 列表。
     * 映射与 [buildItemDataXml] 互逆：PLU、AlternativeItemID、ItemName(zho/eng)、
     * ItemPrice(文本价格 ×100 → minor、KGM/PCS)、Date BestBefore、LabelFormatID。
     */
    fun parseItemDataXml(xml: String): List<PluItem> {
        val items = mutableListOf<PluItem>()
        Regex("<Item>(.*?)</Item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val block = m.groupValues[1]
            val pluNo = Regex("<PLU>(\\d+)</PLU>").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val barcode = Regex("<AlternativeItemID>(.*?)</AlternativeItemID>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block).mapNotNull { it.groupValues[1].trim().toIntOrNull() }
                .firstOrNull()
            fun description(type: String, language: String): String =
                Regex("<Description[^>]*Type=\"$type\"[^>]*Language=\"$language\"[^>]*>(.*?)</Description>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(block).mapNotNull { it.groupValues[1].trim().takeIf { s -> s.isNotEmpty() } }
                    .firstOrNull().orEmpty()
            val name = description("ItemName", "zho")
            val nameEn = description("ItemName", "eng")
            val priceMatch = Regex("<ItemPrice[^>]*UnitOfMeasureCode=\"(\\w+)\"[^>]*>([\\d.]+)</ItemPrice>").find(block)
            val uom = priceMatch?.groupValues?.get(1) ?: "KGM"
            val priceText = priceMatch?.groupValues?.get(2) ?: "0"
            val minor = BigDecimal(priceText).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            val labelFormat = Regex("<LabelFormatID[^>]*>(\\d+)</LabelFormatID>").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val bestBefore = Regex("<Date[^>]*Type=\"BestBefore\"[^>]*>(\\d+)</Date>").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            items += PluItem(
                pluNo = pluNo,
                name = name,
                nameEn = nameEn,
                weightFlag = if (uom == UOM_PCS) 1 else 0,
                unitPrice = minor,
                eanCode = barcode?.toString() ?: "",
                usedByDate = bestBefore,
                label1Format = labelFormat,
            )
        }
        return items
    }

    private fun String.intValue(tag: String): Int? =
        Regex("<$tag>(-?\\d+)</$tag>").find(this)?.groupValues?.get(1)?.toIntOrNull()

    private fun String.textValue(tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(this)?.groupValues?.get(1)?.trim()
}

/** 命令字定义。 */
data class MtCommand(
    val commandText: String,
    val control: String = "Update",
    val dataFile: String? = null,
    val commandId: String = UUID.randomUUID().toString(),
)

/** 单台秤任务执行结果。 */
data class MtScaleResult(
    val deviceId: Long,
    val returnCode: String,
    val status: String,
    val percent: Int,
    val failedCommandIds: List<String>,
) {
    /** Success 且状态 Complete 判定成功；其余失败。 */
    val ok: Boolean get() = returnCode == "Success" && (status == "Complete" || status == "Output" || status == "Executing")
}
