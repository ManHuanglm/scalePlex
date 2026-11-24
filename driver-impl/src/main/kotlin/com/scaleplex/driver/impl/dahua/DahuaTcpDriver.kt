package com.scaleplex.driver.impl.dahua

import com.scaleplex.communication.TcpClient
import com.scaleplex.communication.CommEventBus
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
 * Dahua 大华 TCP driver (port 4001). Second priority domestic brand.
 *
 * Protocol: TCP text-based, each PLU sent as a line terminated by newline.
 * Based on DhScalePluNet.dll protocol (dhip.ini for IP, dhpluversion.ini for format).
 */
class DahuaTcpDriver(
    private val device: ScaleDevice,
    override val transport: TcpClient = TcpClient(
        device.ip,
        if (device.port == 1111) 4001 else device.port, // Dahua default port 4001
        device.comm,
        CommEventBus(),
    ),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DAHUA

    private val timeFmt = SimpleDateFormat("yyMMddHHmmss", Locale.ROOT)

    // Dahua PLU version from device config (default TMA_07 = 3)
    private val pluVersion: Int = device.note.toIntOrNull() ?: 3

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.connect()
            // Dahua typically doesn't need special handshake after TCP connect
            Unit
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            // Dahua scales typically respond to a simple status query
            // Send empty line or specific command to check connectivity
            val resp = transport.request("\r\n".toByteArray())
            ScaleStatus(
                online = resp != null,
                model = device.model,
            )
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val line = DahuaPluEncoder.encode(plu, DahuaPluEncoder.PluVersion.fromCode(pluVersion))
            transport.send((line + "\r\n").toByteArray(Charsets.US_ASCII))
            // Wait for ACK (Dahua typically echoes or returns OK)
            Thread.sleep(50)
            Unit
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    val line = DahuaPluEncoder.encode(plu, DahuaPluEncoder.PluVersion.fromCode(pluVersion))
                    transport.send((line + "\r\n").toByteArray(Charsets.US_ASCII))
                    Thread.sleep(30) // Small delay between PLUs
                    success++
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Dahua delete: send special delete command
            val line = "!0V${String.format("%04d", pluNo)}DELETE\r\n"
            transport.send(line.toByteArray(Charsets.US_ASCII))
            Unit
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Clear all PLUs command
            transport.send("!0VALLDELETE\r\n".toByteArray(Charsets.US_ASCII))
            Unit
        }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = timeFmt.format(Date())
            val cmd = "!0VTIME$now\r\n"
            transport.send(cmd.toByteArray(Charsets.US_ASCII))
            Unit
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // Request trade log (protocol specific)
            transport.send("!0VTRADELOG\r\n".toByteArray(Charsets.US_ASCII))
            // Parse response (format: IP timestamp PLU weight price amount)
            emptyList() // Placeholder - needs field capture for full parsing
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.send("!0VCLEARTRADE\r\n".toByteArray(Charsets.US_ASCII))
            Unit
        }
    }

    override suspend fun uploadPlu(): Result<List<PluItem>> =
        Result.failure(UnsupportedOperationException("Dahua uploadPlu pending field capture"))

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(UnsupportedOperationException("Dahua label download pending field capture"))
}