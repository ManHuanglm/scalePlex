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

    /** 任务类型：0 下发数据、1 回收流水、3 检查在线状态、4 自动发现设备、100 设置参数（文档 §3.1）。 */
    const val TASK_TYPE_DOWNLOAD = 0
    const val TASK_TYPE_TRADE_LOG = 1
    const val TASK_TYPE_CHECK_ONLINE = 3

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
                """      <ItemPrice Index="0" UnitOfMeasureCode="$uom" UnitDes="" PriceOverrideFlag="${plu.allowMarkdown}" """ +
                    """DiscountFlag="${plu.allowDiscount}" Quantity="0" Currency="$currency">""" +
                    "${Numbers.minorToDisplay(plu.unitPrice, priceDecimals)}</ItemPrice>",
            )
            appendLine("    </ItemPrices>")
            // 日期：包装日期（可选）+ 保质期（BestBefore）
            val dateLines = buildList {
                if (plu.packedDate > 0 || plu.packedDatePrintFlag) {
                    val unit = if (plu.packedDateUnit == 1) "Months" else "Days"
                    add(
                        """      <Date Type="PackedDate" PrintFlag="${plu.printPackedDate}" Unit="$unit">""" +
                            "${plu.packedDate}</Date>",
                    )
                }
                if (plu.effectiveShelfLifeDays > 0) {
                    add(
                        """      <Date Type="BestBefore" PrintFlag="True" Unit="Days">""" +
                            "${plu.effectiveShelfLifeDays}</Date>",
                    )
                }
            }
            if (dateLines.isNotEmpty()) {
                appendLine("    <Dates>")
                dateLines.forEach { appendLine(it) }
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
     * 解析 CheckOnline 结果数据文件内容（CommandResult.DataFile 引用的独立 XML）。
     *
     * 真机校准（2026-11-25）：秤关机时任务层仍全绿（TaskStatus=Complete、ReturnCode=OK、
     * Succeeded=1），在线与否唯一权威是数据文件内 `<CheckOnline><Status>`：-1 = 离线。
     */
    fun parseCheckOnlineStatus(dataXml: String): Int? =
        Regex("<CheckOnline>.*?<Status>(-?\\d+)</Status>", RegexOption.DOT_MATCHES_ALL)
            .find(dataXml)?.groupValues?.get(1)?.toIntOrNull()

    /** 提取 TaskResult 中首个 CommandResult 引用的数据文件名（相对任务目录）。 */
    fun firstCommandDataFile(taskResultXml: String): String? =
        Regex("<CommandResult>.*?<DataFile>(.*?)</DataFile>", RegexOption.DOT_MATCHES_ALL)
            .find(taskResultXml)?.groupValues?.get(1)

    /**
     * 解析 DeviceTime 读取数据文件（真机校准 .231 bComS：嵌套元素，ISO 本地时间）：
     * `<Data><DeviceTime AdjustTime="false"><DeviceTime>2026-11-25T19:20:28</DeviceTime></DeviceTime></Data>`
     */
    fun parseDeviceTime(dataXml: String): java.time.LocalDateTime? {
        val raw = Regex("<DeviceTime[^>]*>\\s*<DeviceTime>([^<]+)</DeviceTime>", RegexOption.DOT_MATCHES_ALL)
            .find(dataXml)?.groupValues?.get(1)?.trim() ?: return null
        return runCatching { java.time.LocalDateTime.parse(raw) }.getOrNull()
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
            // 注意：结果 XML 的状态字段名是 TaskStatus（真机校准，误读 Status 会恒为 null）
            val status = block.textValue("TaskStatus") ?: "Unknown"
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
     * 真机 RL00 回收格式（2026-11-25 校准）：
     * - `<Description Type="ItemName">名称</Description>`（**无 Language 属性**，有则按语言区分）；
     * - 价格为显示值文本（如 `120` = 120.00 元/KGM），×100 转最小单位；
     * - 日期为 `<DateOffset Type="BestBefore" UnitOfOffset="day" ...>3</DateOffset>`（非 Date 元素）；
     * - 条码 `<AlternativeItemID>` 为 13 位补零数字串，还原时去掉前导零。
     */
    fun parseItemDataXml(xml: String): List<PluItem> {
        val items = mutableListOf<PluItem>()
        Regex("<Item>(.*?)</Item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val block = m.groupValues[1]
            val pluNo = Regex("<PLU>(\\d+)</PLU>").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val barcode = Regex("<AlternativeItemID>(.*?)</AlternativeItemID>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block).mapNotNull { it.groupValues[1].trim() }
                .map { s -> if (s.matches(Regex("\\d+"))) s.trimStart('0').ifEmpty { "0" } else s }
                .firstOrNull()
            fun description(type: String, language: String): String =
                Regex("<Description\\b[^>]*>(.*?)</Description>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(block)
                    .mapNotNull { dm ->
                        val attrs = dm.value.substringBefore('>')
                        val typeName = Regex("Type=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                        // RL00 回收不带 Language 属性，视为请求的主语言
                        val lang = Regex("Language=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: language
                        if (typeName == type && lang == language) {
                            dm.groupValues[1].trim().takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }
                    }
                    .firstOrNull().orEmpty()
            val name = description("ItemName", "zho")
            val nameEn = description("ItemName", "eng")
            val priceMatch = Regex("<ItemPrice[^>]*UnitOfMeasureCode=\"(\\w+)\"[^>]*>([\\d.]+)\\s*</ItemPrice>").find(block)
            val uom = priceMatch?.groupValues?.get(1) ?: "KGM"
            val priceText = priceMatch?.groupValues?.get(2) ?: "0"
            val minor = BigDecimal(priceText).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            val labelFormat = Regex("<LabelFormatID[^>]*>(\\d+)</LabelFormatID>").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            // 日期元素名可变（RL00 为 DateOffset），按 Type 属性匹配、到下一个 '<' 取数字
            fun dateOffset(type: String): Int =
                Regex("<Date\\w+[^>]*Type=\"$type\"[^>]*>(\\d+)\\s*<").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val bestBefore = dateOffset("BestBefore")
            val packedDate = dateOffset("PackedDate")
            val priceTag = Regex("<ItemPrice[^>]*>").find(block)?.value.orEmpty()
            val allowMarkdown = priceTag.contains("PriceOverrideFlag=\"True\"", ignoreCase = true) ||
                priceTag.contains("PriceOverrideFlag=\"true\"", ignoreCase = true)
            val allowDiscount = priceTag.contains("DiscountFlag=\"True\"", ignoreCase = true) ||
                priceTag.contains("DiscountFlag=\"true\"", ignoreCase = true)
            items += PluItem(
                pluNo = pluNo,
                name = name,
                nameEn = nameEn,
                weightFlag = if (uom == UOM_PCS) 1 else 0,
                unitPrice = minor,
                eanCode = barcode ?: "",
                usedByDate = bestBefore,
                packedDate = packedDate,
                allowMarkdown = allowMarkdown,
                allowDiscount = allowDiscount,
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
    /** ReturnCode OK/Success 且状态 Complete/Output/Executing 判定成功（真机 RL00 返回 OK）。 */
    val ok: Boolean
        get() = returnCode in setOf("Success", "OK") &&
            (status == "Complete" || status == "Output" || status == "Executing")
}
