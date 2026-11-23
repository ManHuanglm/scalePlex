package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.TradeRecord
import kotlinx.datetime.LocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 大华 TM 系列 TCP 协议层（默认端口 4001，ASCII 命令风格）。
 *
 * 帧结构：`CMD[,field1,field2...]` + CRLF，GBK 编码；应答按 `OK` / `ERR,原因` 前缀判定。
 *
 * 注意：命令字与字段顺序基于行业通用设计，**待实体秤抓包校准**。所有常量集中于此，
 * 校准协议时只需修改本文件，不影响驱动控制流。
 */
object DahuaProtocol {

    // ---- 命令字（待联调校准） ----
    const val CMD_STATUS = "STATUS"
    const val CMD_SET_PLU = "SETPLU"
    const val CMD_DELETE_PLU = "DELPLU"
    const val CMD_DELETE_ALL = "DELALLPLU"
    const val CMD_SYNC_TIME = "SYSTIME"
    const val CMD_DOWNLOAD_LABEL = "LABEL"
    const val CMD_READ_TRADE = "READTRADE"
    const val CMD_CLEAR_TRADE = "CLEARTRADE"

    const val CR: Byte = 0x0D
    const val LF: Byte = 0x0A

    /** 应答前缀约定。 */
    private const val ACK_OK = "OK"
    private const val ACK_ERR = "ERR"

    private val CHARSETS_GBK = charset("GBK")
    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    /** 应答解析结果。 */
    sealed interface Ack {
        /** object 单例：应答成功。 */
        data object Ok : Ack

        /** 应答失败，携带秤端错误描述。 */
        data class Err(val message: String) : Ack

        /** 无应答（超时/丢包）。 */
        data object NoReply : Ack
    }

    /**
     * 组帧：命令 + 逗号分隔字段 + CRLF，GBK 编码。
     * 字段由 [DahuaPluEncoder] 保证不含逗号与换行。
     */
    fun frame(command: String, fields: List<String> = emptyList()): ByteArray {
        val line = if (fields.isEmpty()) command else fields.joinToString(prefix = "$command,", separator = ",")
        val bytes = line.toByteArray(CHARSETS_GBK)
        return bytes + CR + LF
    }

    /** 解析应答：OK→[Ack.Ok]，ERR→[Ack.Err]，无数据→[Ack.NoReply]。 */
    fun parseAck(reply: ByteArray?): Ack {
        if (reply == null || reply.isEmpty()) return Ack.NoReply
        val text = reply.toString(CHARSETS_GBK).trim()
        return when {
            text.equals(ACK_OK, ignoreCase = true) -> Ack.Ok
            text.startsWith(ACK_OK) -> Ack.Ok
            text.startsWith("$ACK_ERR,") -> Ack.Err(text.substringAfter(',').trim())
            text.startsWith(ACK_ERR) -> Ack.Err(text.removePrefix(ACK_ERR).trim())
            else -> Ack.Err(text.take(64))
        }
    }

    /** 当前主机时间 → 协议时间串 yyMMddHHmmss。 */
    fun formatNow(): String = timeFmt.format(Date())

    /**
     * 解析交易流水应答：每行一条 `T,<recordNo>,<yyMMddHHmmss>,<pluNo>,<pluName>,<weightG>,<unitPrice>,<amount>[,<operator>]`。
     * 空行与不合规行跳过（待联调校准行格式）。
     */
    fun decodeTradeLog(reply: ByteArray?, deviceId: Long): List<TradeRecord> {
        if (reply == null || reply.isEmpty()) return emptyList()
        return reply.toString(CHARSETS_GBK)
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("T,") }
            .mapNotNull { parseTradeLine(it, deviceId) }
            .toList()
    }

    private fun parseTradeLine(line: String, deviceId: Long): TradeRecord? {
        val parts = line.split(',')
        if (parts.size < 8) return null
        val recordNo = parts[1].toLongOrNull() ?: return null
        val timestamp = parseTime(parts[2])
        val pluNo = parts[3].toIntOrNull() ?: return null
        val weight = parts[5].toIntOrNull() ?: 0
        val unitPrice = parts[6].toLongOrNull() ?: 0L
        val amount = parts[7].toLongOrNull() ?: 0L
        return TradeRecord(
            deviceId = deviceId,
            recordNo = recordNo,
            timestamp = timestamp,
            pluNo = pluNo,
            pluName = parts.getOrNull(4).orEmpty(),
            weight = weight,
            unitPrice = unitPrice,
            amount = amount,
            operator = parts.getOrNull(8).orEmpty(),
        )
    }

    private fun parseTime(value: String): LocalDateTime? = runCatching {
        val d = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT).parse(value) ?: return null
        val cal = java.util.Calendar.getInstance().apply { time = d }
        LocalDateTime(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
        )
    }.getOrNull()
}
