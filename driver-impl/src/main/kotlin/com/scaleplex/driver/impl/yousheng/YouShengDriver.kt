package com.scaleplex.driver.impl.yousheng

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.Transport
import com.scaleplex.communication.TransportFactory
import com.scaleplex.core.model.BatchError
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.TradeRecord
import com.scaleplex.driver.api.AbstractScaleDriver
import com.scaleplex.driver.api.ScaleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 友声 YOUSHENG 条码秤驱动：TCP 与串口双模式。
 *
 * 两通道使用同一帧格式（[YouShengProtocol]），实际通道由 `device.commType` 经
 * [TransportFactory] 决定 —— TCP 走 [com.scaleplex.communication.TcpClient]，
 * 串口走 [com.scaleplex.communication.SerialClient]，驱动主体零改动。
 */
class YouShengDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.YOUSHENG

    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val ack = YouShengProtocol.parseAck(
                transport.request(YouShengProtocol.frame(YouShengProtocol.CMD_READ_STATUS)),
            )
            ScaleStatus(online = ack is YouShengProtocol.Ack.Ok, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(YouShengProtocol.CMD_WRITE_PLU, YouShengPluEncoder.encode(plu), plu.pluNo) }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    val ack = YouShengProtocol.parseAck(
                        transport.request(YouShengProtocol.frame(YouShengProtocol.CMD_WRITE_PLU, YouShengPluEncoder.encode(plu))),
                    )
                    if (ack is YouShengProtocol.Ack.Ok) success++
                    else errors += BatchError(device.id, plu.pluNo, ackMessage(ack))
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(YouShengProtocol.CMD_DELETE_PLU, int32LE(pluNo), pluNo) }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(YouShengProtocol.CMD_DELETE_ALL) }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAck(YouShengProtocol.CMD_SYNC_TIME, timeFmt.format(Date()).toByteArray(Charsets.US_ASCII))
        }
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        // 友声标签为秤端格式编号下发，模板内容首期仅下发格式 ID。
        runCatching { requireAck(YouShengProtocol.CMD_DOWNLOAD_LABEL, byteArrayOf((template.id and 0xFF).toByte())) }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // 流水记录解析待实体秤联调校准；先发送读命令确认通道，返回空列表。
            transport.request(YouShengProtocol.frame(YouShengProtocol.CMD_READ_TRADE))
            emptyList()
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(YouShengProtocol.CMD_CLEAR_TRADE) }
    }

    private suspend fun requireAck(cmd: Int, data: ByteArray = ByteArray(0), pluNo: Int? = null) {
        val ack = YouShengProtocol.parseAck(transport.request(YouShengProtocol.frame(cmd, data)))
        when (ack) {
            is YouShengProtocol.Ack.Ok -> Unit
            is YouShengProtocol.Ack.Err ->
                throw IllegalStateException(desc(cmd, pluNo) + " failed (NAK 0x" + ack.code.toString(16) + ")")
            YouShengProtocol.Ack.NoReply -> throw IllegalStateException(desc(cmd, pluNo) + " got no ACK")
        }
    }

    private fun desc(cmd: Int, pluNo: Int?): String =
        if (pluNo == null) "Command 0x" + cmd.toString(16) else "Command 0x" + cmd.toString(16) + " (PLU $pluNo)"

    private fun ackMessage(ack: YouShengProtocol.Ack): String = when (ack) {
        is YouShengProtocol.Ack.Err -> "NAK 0x" + ack.code.toString(16)
        YouShengProtocol.Ack.NoReply -> "No ACK"
        YouShengProtocol.Ack.Ok -> "ok"
    }

    private fun int32LE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
