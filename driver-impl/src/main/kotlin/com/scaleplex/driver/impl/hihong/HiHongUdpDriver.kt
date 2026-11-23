package com.scaleplex.driver.impl.hihong

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
 * 辉宏 HIHONG HL 系列 UDP 驱动。
 *
 * 帧结构与寺冈同类但命令字独立（见 [HiHongProtocol]）；控制流与大华/寺冈驱动保持
 * 一致：逐条下发、汇总 [BatchResult]、超时重试由 [Transport] 统一处理。
 */
class HiHongUdpDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.HIHONG

    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val ack = HiHongProtocol.parseAck(
                transport.request(HiHongProtocol.frame(HiHongProtocol.OP_READ_STATUS)),
            )
            ScaleStatus(online = ack is HiHongProtocol.Ack.Ok, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(HiHongProtocol.OP_DOWNLOAD_PLU, HiHongPluEncoder.encode(plu), plu.pluNo) }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    val ack = HiHongProtocol.parseAck(
                        transport.request(HiHongProtocol.frame(HiHongProtocol.OP_DOWNLOAD_PLU, HiHongPluEncoder.encode(plu))),
                    )
                    if (ack is HiHongProtocol.Ack.Ok) success++
                    else errors += BatchError(device.id, plu.pluNo, ackMessage(ack))
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(HiHongProtocol.OP_DELETE_PLU, int32LE(pluNo), pluNo) }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(HiHongProtocol.OP_DELETE_ALL_PLU) }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAck(HiHongProtocol.OP_SYNC_TIME, timeFmt.format(Date()).toByteArray(Charsets.US_ASCII))
        }
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        // 辉宏标签格式为秤端固定格式编号下发，模板内容首期仅下发格式 ID。
        runCatching { requireAck(HiHongProtocol.OP_DOWNLOAD_LABEL, byteArrayOf((template.id and 0xFF).toByte())) }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // 流水记录解析待实体秤联调校准；先发送读命令确认通道，返回空列表。
            transport.request(HiHongProtocol.frame(HiHongProtocol.OP_READ_TRADE))
            emptyList()
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(HiHongProtocol.OP_CLEAR_TRADE) }
    }

    private suspend fun requireAck(op: Int, body: ByteArray = ByteArray(0), pluNo: Int? = null) {
        val ack = HiHongProtocol.parseAck(transport.request(HiHongProtocol.frame(op, body)))
        when (ack) {
            is HiHongProtocol.Ack.Ok -> Unit
            is HiHongProtocol.Ack.Err ->
                throw IllegalStateException(desc(op, pluNo) + " failed (NAK 0x" + ack.code.toString(16) + ")")
            HiHongProtocol.Ack.NoReply -> throw IllegalStateException(desc(op, pluNo) + " got no ACK")
        }
    }

    private fun desc(op: Int, pluNo: Int?): String =
        if (pluNo == null) "Command 0x" + op.toString(16) else "Command 0x" + op.toString(16) + " (PLU $pluNo)"

    private fun ackMessage(ack: HiHongProtocol.Ack): String = when (ack) {
        is HiHongProtocol.Ack.Err -> "NAK 0x" + ack.code.toString(16)
        HiHongProtocol.Ack.NoReply -> "No ACK"
        HiHongProtocol.Ack.Ok -> "ok"
    }

    private fun int32LE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
