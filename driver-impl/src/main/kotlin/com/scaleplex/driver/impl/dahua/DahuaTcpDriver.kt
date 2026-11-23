package com.scaleplex.driver.impl.dahua

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
 * 大华 DAHUA TM 系列 TCP 驱动（端口 4001）。
 *
 * 控制流与 DIGI 驱动一致：逐条下发并汇总 [BatchResult]；协议细节全部集中在
 * [DahuaProtocol] / [DahuaPluEncoder]，待实体秤抓包校准时只改协议层。
 */
class DahuaTcpDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DAHUA

    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val ack = DahuaProtocol.parseAck(transport.request(DahuaProtocol.frame(DahuaProtocol.CMD_STATUS)))
            ScaleStatus(online = ack is DahuaProtocol.Ack.Ok, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAck(DahuaProtocol.CMD_SET_PLU, DahuaPluEncoder.encodeFields(plu), plu.pluNo)
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    val ack = DahuaProtocol.parseAck(
                        transport.request(DahuaProtocol.frame(DahuaProtocol.CMD_SET_PLU, DahuaPluEncoder.encodeFields(plu))),
                    )
                    if (ack is DahuaProtocol.Ack.Ok) success++
                    else errors += BatchError(device.id, plu.pluNo, ackMessage(ack))
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(DahuaProtocol.CMD_DELETE_PLU, listOf(pluNo.toString()), pluNo) }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(DahuaProtocol.CMD_DELETE_ALL) }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAck(DahuaProtocol.CMD_SYNC_TIME, listOf(timeFmt.format(Date())))
        }
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(DahuaProtocol.CMD_DOWNLOAD_LABEL, DahuaPluEncoder.encodeLabel(template)) }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            DahuaProtocol.decodeTradeLog(
                transport.request(DahuaProtocol.frame(DahuaProtocol.CMD_READ_TRADE)),
                device.id,
            )
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { requireAck(DahuaProtocol.CMD_CLEAR_TRADE) }
    }

    /** 发送命令并要求 OK 应答，否则抛错携带原因。 */
    private suspend fun requireAck(command: String, fields: List<String> = emptyList(), pluNo: Int? = null) {
        val ack = DahuaProtocol.parseAck(transport.request(DahuaProtocol.frame(command, fields)))
        when (ack) {
            is DahuaProtocol.Ack.Ok -> Unit
            is DahuaProtocol.Ack.Err ->
                throw IllegalStateException(desc(command, pluNo) + " failed: ${ack.message}")
            DahuaProtocol.Ack.NoReply ->
                throw IllegalStateException(desc(command, pluNo) + " got no ACK")
        }
    }

    private fun desc(command: String, pluNo: Int?): String =
        if (pluNo == null) "Command $command" else "Command $command (PLU $pluNo)"

    private fun ackMessage(ack: DahuaProtocol.Ack): String = when (ack) {
        is DahuaProtocol.Ack.Err -> ack.message
        DahuaProtocol.Ack.NoReply -> "No ACK"
        DahuaProtocol.Ack.Ok -> "ok"
    }
}
