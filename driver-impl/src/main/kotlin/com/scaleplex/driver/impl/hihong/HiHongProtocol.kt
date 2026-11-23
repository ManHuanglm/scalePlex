package com.scaleplex.driver.impl.hihong

/**
 * 辉宏 HL 系列 UDP 协议层。帧结构与寺冈同类（STX 定界二进制帧），但命令字与
 * 应答约定为辉宏私有 —— 基于行业通用设计，**待实体秤抓包校准**。
 *
 * 帧结构：STX(0x02) + op + len(2 LE, op+body) + body + checksum(二补码低字节) + ETX(0x03)。
 */
object HiHongProtocol {

    // ---- 命令字（待联调校准） ----
    const val OP_DOWNLOAD_PLU = 0x11
    const val OP_DELETE_PLU = 0x12
    const val OP_DELETE_ALL_PLU = 0x13
    const val OP_SYNC_TIME = 0x14
    const val OP_READ_STATUS = 0x15
    const val OP_DOWNLOAD_LABEL = 0x16
    const val OP_READ_TRADE = 0x17
    const val OP_CLEAR_TRADE = 0x18

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

    /**
     * 组帧：STX + op + len(2 LE) + body + checksum + ETX。
     * checksum = (op + len 低 + len 高 + body 各字节) 二补码低字节。
     */
    fun frame(op: Int, body: ByteArray = ByteArray(0)): ByteArray {
        val len = body.size + 1 // op + body
        val out = mutableListOf(STX, op.toByte())
        out.add((len and 0xFF).toByte())
        out.add(((len shr 8) and 0xFF).toByte())
        body.forEach { out.add(it) }
        var sum = op + (len and 0xFF) + ((len shr 8) and 0xFF)
        body.forEach { sum += (it.toInt() and 0xFF) }
        out.add((((sum.inv()) + 1) and 0xFF).toByte())
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
