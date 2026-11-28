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

    // Dahua PLU version from device config (default TMA_07 = 3)
    private val pluVersion: Int = device.note.toIntOrNull() ?: 3

    /** 实际通讯口（历史数据里 1111 归一化为大华默认 4001）。 */
    private val targetPort: Int = if (device.port == 1111) 4001 else device.port

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.connect()
            // Dahua typically doesn't need special handshake after TCP connect
            Unit
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            // TCP 4001 端口探测：4001 为大华条码秤专用通讯口；无安全的查询帧可用
            //（读协议待标定，校时帧 !0P 会改秤时间不可用于探测），连接成功即在线。
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(device.ip, targetPort), CONNECT_TIMEOUT_MS)
            }
            ScaleStatus(online = true, model = device.model)
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

    /**
     * 大华校时（真机破解 2026-11-28 @192.168.0.150:4001，官方 TMA4.0 上位机"时钟设置"
     * 经本地 TCP 代理抓包标定）：
     * - PC→秤 `!0P{yyMMddHHmm}\r\n`（ASCII 15B，时间 10 位**不带秒**）；
     * - 成功 ACK = `0p\r\n` + ETX(0x03)，共 5B（命令小写镜像）。
     * 官方 DhScalePluNet.dll 无时间接口（PE 导出表核验）；旧 `!0VTIME` 帧真机无应答已废弃。
     * 秤内时钟无读取命令（仅可写入），readTime 不支持。
     */
    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 不走 transport.request：其读循环要等对端 EOF 才返回，而大华秤回 ACK 后
            // 保持连接，读超时会误判；改用驱动内裸 Socket 精确读 ACK。
            val resp = exchange(timeFrame(Date()))
                ?: error("大华校时无应答")
            check(resp.size >= 2 && resp[0] == '0'.code.toByte() && resp[1] == 'p'.code.toByte()) {
                "大华校时应答异常: " + resp.joinToString(" ") { String.format("%02X", it) }
            }
        }
    }

    /** 校时帧 `!0P{yyMMddHHmm}\r\n`（秒被协议丢弃，只到分）。 */
    internal fun timeFrame(date: Date): ByteArray =
        ("!0P" + SimpleDateFormat("yyMMddHHmm", Locale.ROOT).format(date) + "\r\n")
            .toByteArray(Charsets.US_ASCII)

    /** 裸 Socket 单发单收（读满 ACK_SIZE 或超时）。 */
    private fun exchange(frame: ByteArray): ByteArray? = java.net.Socket().use { s ->
        s.tcpNoDelay = true
        s.connect(java.net.InetSocketAddress(device.ip, targetPort), CONNECT_TIMEOUT_MS)
        s.soTimeout = READ_TIMEOUT_MS
        s.getOutputStream().apply {
            write(frame)
            flush()
        }
        val buf = ByteArray(ACK_SIZE)
        var total = 0
        try {
            val inp = s.getInputStream()
            while (total < buf.size) {
                val n = inp.read(buf, total, buf.size - total)
                if (n <= 0) break
                total += n
            }
        } catch (_: java.net.SocketTimeoutException) {
            // 超时返回已收到的部分
        }
        if (total > 0) buf.copyOf(total) else null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val READ_TIMEOUT_MS = 2500

        /** 校时 ACK 帧长：`0p\r\n` + ETX(0x03) = 5B。 */
        const val ACK_SIZE = 5
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