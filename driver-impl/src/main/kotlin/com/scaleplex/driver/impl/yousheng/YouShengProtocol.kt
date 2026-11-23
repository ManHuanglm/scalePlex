package com.scaleplex.driver.impl.yousheng

/**
 * 友声条码秤协议层：TCP 与 RS232 串口共用同一帧格式（通道由 commType 决定）。
 *
 * 帧结构：STX(0x02) + cmd + len(2 LE, 仅 data) + data + BCC(异或) + ETX(0x03)。
 * BCC = cmd ^ len 低 ^ len 高 ^ data 各字节。
 * 命令字与应答约定基于行业通用设计，**待实体秤抓包校准**。
 */
object YouShengProtocol {

    // ---- 命令字（待联调校准） ----
    const val CMD_WRITE_PLU = 0x57        // 'W' 写 PLU
    const val CMD_DELETE_PLU = 0x44       // 'D' 删 PLU
    const val CMD_DELETE_ALL = 0x43       // 'C' 清空 PLU
    const val CMD_SYNC_TIME = 0x54        // 'T' 时间同步
    const val CMD_READ_STATUS = 0x53      // 'S' 状态查询
    const val CMD_DOWNLOAD_LABEL = 0x4C   // 'L' 标签下发
    const val CMD_READ_TRADE = 0x52       // 'R' 读流水
    const val CMD_CLEAR_TRADE = 0x45      // 'E' 清流水

    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03

    /** 应答状态字节（待联调校准：0x06=ACK，0x15=NAK）。 */
    const val ACK_OK: Int = 0x06
    const val ACK_NAK: Int = 0x15

    /** 应答解析结果。 */
    sealed interface Ack {
        /** object 单例：应答成功。 */
        data object Ok : Ack

        /** 应答失败，携带 NAK 状态码。 */
        data class Err(val code: Int) : Ack

        /** 无应答（超时/丢包）。 */
        data object NoReply : Ack
    }

    /** 组帧：STX + cmd + len(2 LE) + data + BCC + ETX。 */
    fun frame(cmd: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val lenLow = data.size and 0xFF
        val lenHigh = (data.size shr 8) and 0xFF
        val out = mutableListOf(STX, cmd.toByte(), lenLow.toByte(), lenHigh.toByte())
        data.forEach { out.add(it) }
        var bcc = cmd xor lenLow xor lenHigh
        data.forEach { bcc = bcc xor (it.toInt() and 0xFF) }
        out.add((bcc and 0xFF).toByte())
        out.add(ETX)
        return out.toByteArray()
    }

    /** 解析应答：状态字节 ACK→[Ack.Ok]，NAK→[Ack.Err]，无数据→[Ack.NoReply]。 */
    fun parseAck(reply: ByteArray?): Ack {
        if (reply == null || reply.size < 2) return Ack.NoReply
        val status = reply[1].toInt() and 0xFF
        return when (status) {
            ACK_OK -> Ack.Ok
            ACK_NAK -> Ack.Err(status)
            else -> Ack.Err(status)
        }
    }
}
