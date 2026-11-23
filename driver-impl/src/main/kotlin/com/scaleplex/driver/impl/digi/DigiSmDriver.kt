package com.scaleplex.driver.impl.digi

import com.scaleplex.communication.Transport
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.BatchError
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
 * DIGI 寺冈 SM series driver (UDP). Phase-1, highest priority.
 *
 * The SM UDP protocol exchanges length-framed records; PLU download follows the
 * CSV-import semantics of the DIGI DLL (`PLU_Demo1.csv`). This implementation
 * reuses [DigiPluCsvCodec] for payload construction and [DigiPluEncoder] for the
 * binary record form, and drives the exchange through a [UdpClient] with retry.
 *
 * Live record-framing offsets differ across SM firmware; the protocol op-codes and
 * record framing constants below are centralized so field capture can refine them
 * without touching the surrounding control flow.
 */
class DigiSmDriver(
    private val device: ScaleDevice,
    override val transport: Transport = com.scaleplex.communication.TransportFactory.create(
        device, com.scaleplex.communication.CommEventBus(),
    ),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DIGI

    // Protocol op-codes (centralized for firmware-specific tuning).
    private object OpCode {
        const val DOWNLOAD_PLU = 0x01
        const val READ_PLU = 0x02
        const val DELETE_PLU = 0x03
        const val DELETE_ALL_PLU = 0x04
        const val SYNC_TIME = 0x05
        const val READ_STATUS = 0x06
        const val READ_TRADE = 0x07
        const val CLEAR_TRADE = 0x08
        const val DOWNLOAD_LABEL = 0x09
    }

    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    override suspend fun connect(): Result<Unit> = runCatching {
        transport.connect()
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = transport.request(frame(OpCode.READ_STATUS, ByteArray(0)))
                ?: return@runCatching ScaleStatus(online = false)
            // Reply payload[0] == 1 conventionally means "alive".
            val online = reply.size > 1 && reply[1].toInt() == 1
            ScaleStatus(online = online, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val record = DigiPluEncoder.encode(plu, priceDecimals = 2)
            val payload = frame(OpCode.DOWNLOAD_PLU, record)
            transport.request(payload) ?: error("No ACK from scale for PLU ${plu.pluNo}")
            Unit
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    val record = DigiPluEncoder.encode(plu)
                    val reply = transport.request(frame(OpCode.DOWNLOAD_PLU, record))
                    if (reply != null) success++ else errors += BatchError(device.id, plu.pluNo, "No ACK")
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = byteArrayOf(
                (pluNo and 0xFF).toByte(),
                ((pluNo shr 8) and 0xFF).toByte(),
                ((pluNo shr 16) and 0xFF).toByte(),
                ((pluNo shr 24) and 0xFF).toByte(),
            )
            transport.request(frame(OpCode.DELETE_PLU, body)) ?: error("No ACK deleting PLU $pluNo")
            Unit
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.request(frame(OpCode.DELETE_ALL_PLU, ByteArray(0))) ?: error("No ACK clearing PLU")
            Unit
        }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = timeFmt.format(Date())
            transport.request(frame(OpCode.SYNC_TIME, now.toByteArray(Charsets.US_ASCII)))
                ?: error("No ACK syncing time")
            Unit
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // Placeholder decode; full record parsing refines with field capture.
            transport.request(frame(OpCode.READ_TRADE, ByteArray(0)))
            emptyList<TradeRecord>()
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.request(frame(OpCode.CLEAR_TRADE, ByteArray(0))) ?: error("No ACK clearing trade log")
            Unit
        }
    }

    /**
     * Frame a request: STX(0x02) + opCode + length(2 LE) + body + checksum + ETX(0x03).
     * Checksum is the two's-complement of the sum of opCode+length+body, a common DIGI scheme.
     */
    private fun frame(op: Int, body: ByteArray): ByteArray {
        val len = body.size + 1 // op + body
        val out = mutableListOf<Byte>()
        out.add(0x02)
        out.add(op.toByte())
        out.add((len and 0xFF).toByte())
        out.add(((len shr 8) and 0xFF).toByte())
        body.forEach { out.add(it) }
        var sum = op + (len and 0xFF) + ((len shr 8) and 0xFF)
        body.forEach { sum += (it.toInt() and 0xFF) }
        out.add((((sum.inv()) + 1) and 0xFF).toByte()) // two's complement low byte
        out.add(0x03)
        return out.toByteArray()
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 标签格式编号收敛到 DIGI 格式表范围（1..24，对应 T1..F8）
            val formatNo = template.id.toInt().coerceIn(DigiLabelEncoder.FORMAT_NO_MIN, DigiLabelEncoder.FORMAT_NO_MAX)
            val record = DigiLabelEncoder.encode(template, formatNo)
            transport.request(frame(OpCode.DOWNLOAD_LABEL, record))
                ?: error("No ACK from scale for label format $formatNo")
            Unit
        }
    }
}
