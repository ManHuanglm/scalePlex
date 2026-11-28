package com.scaleplex.driver.impl.dahua

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.TcpClient
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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 大华 TMA_07 条码秤 TCP 驱动（真机抓包标定 2026-11-28 @192.168.0.150:4001）。
 *
 * 协议为 ASCII 文本帧（CRLF 结尾，秤端应答以 ETX 0x03 结尾），帧编解码见 [DahuaProto]：
 * - PLU 下发 `!0V{号}A{73位数字段}{条码段}`，应答 `0v{号}a{n}`（**ACK 不区分成败**，
 *   单价/代码为 0 的记录秤端拒写但 ACK 照回，由 encodeDigits 前置校验拦截）；
 * - PLU 回收 `!0U{号}A`，应答 `0u{号}A{数据}` / `0u{号}Anoplu`（空槽）；
 * - 流水回收 `!0J{记录}A`，结束标志 `0j{号}ac`；`!0HA` = 清空已回收流水；
 * - 校时 `!0P{yyMMddHHmm}`，ACK `0p`；标签格式表 `!0L{页}A{23×4位格式号}`。
 *
 * 大华秤**无远程删除命令**（!0D/W/G/X 盲试、空 !0V 帧、全零覆盖均真机验证无效，
 * 官方软件与 DhScalePluNet.dll 亦无删除功能），deletePlu/deleteAllPlu 明确报不支持。
 *
 * 不走 transport.request：其读循环等对端 EOF 才返回，而秤保持连接会读到超时误判，
 * 全部改用驱动内裸 Socket（读至 ETX）。
 *
 * **秤端连接行为（真机实测）**：会话活跃期间拒绝一切新 TCP 连接，且外来连接会
 * 干扰秤端处理导致应答迟到（>2.5s 超时）。因此操作互斥（[busy]：操作期间心跳
 * getStatus 直接视为在线），批量下发每条独立连接避免超时残留污染后续帧。
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

    /**
     * 操作互斥标志：大华秤在下发/回收会话活跃期间**拒绝一切新 TCP 连接**
     * （真机实测：会话进行中心跳探测 connect 全部失败），且外来连接会干扰秤端
     * 处理导致应答迟到超时。操作进行中 getStatus 直接视为在线，不连秤。
     */
    private val busy = AtomicBoolean(false)

    /** 大华 PLU 版本（TMA_07 = 3），历史配置存于 device.note。 */
    private val pluVersion: Int = device.note.toIntOrNull() ?: 3

    /** 实际通讯口（历史数据里 1111 归一化为大华默认 4001）。 */
    private val targetPort: Int = if (device.port == 1111) 4001 else device.port

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transport.connect()
            Unit
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            // 下发/回收会话进行中：秤端拒绝新连接（真机实测），直接视为在线，
            // 避免心跳 connect 干扰会话且被误报离线
            if (busy.get()) return@runCatching ScaleStatus(online = true, model = device.model)
            // TCP 4001 端口探测：4001 为大华条码秤专用通讯口；无安全的查询帧可用
            //（校时帧 !0P 会改秤时间不可用于探测），连接成功即在线。
            Socket().use { s ->
                s.connect(InetSocketAddress(device.ip, targetPort), CONNECT_TIMEOUT_MS)
            }
            ScaleStatus(online = true, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withSession { ses -> writePluOnce(ses, plu) }
        }
    }

    /**
     * PLU 批量一次性下发：转换 TMA07 格式后逐条写帧（`!0V` + ACK 前缀校验），
     * **不逐条回读验证**——每条独立连接执行（超时残留不跨条传染）。
     * ACK 不区分写入成败，无效记录（单价 0 等）由 [DahuaProto.encodeDigits] 前置拒绝。
     */
    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            var success = 0
            val errors = mutableListOf<BatchError>()
            for (plu in list) {
                try {
                    withSession { ses -> writePluOnce(ses, plu) }
                    success++
                } catch (t: Throwable) {
                    errors += BatchError(device.id, plu.pluNo, t.message ?: "error")
                }
            }
            BatchResult(total = list.size, success = success, failed = list.size - success, errors = errors)
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("大华秤无远程删除命令（真机验证：官方软件/DLL 均无此功能，仅可覆盖）"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("大华秤无远程清空命令（真机验证：官方软件/DLL 均无此功能）"))

    /** 校时帧 `!0P{yyMMddHHmm}\r\n`（秒被协议丢弃，只到分）。 */
    internal fun timeFrame(date: Date): ByteArray =
        ("!0P" + SimpleDateFormat("yyMMddHHmm", Locale.ROOT).format(date) + "\r\n")
            .toByteArray(Charsets.US_ASCII)

    /**
     * 大华校时：PC→秤 `!0P{yyMMddHHmm}\r\n`，成功 ACK = `0p\r\n` + ETX。
     * 秤内时钟无读取命令（仅可写入），readTime 不支持。
     */
    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withSession { ses ->
                val resp = ses.request(timeFrame(Date()))
                    ?: error("大华校时无应答")
                check(resp.startsWith("0p")) { "大华校时应答异常: $resp" }
            }
        }
    }

    /**
     * PLU 批量回收：`!0U{号}A` 逐槽扫描 0001~0999（与官方软件行为一致），
     * `noplu` 为空槽跳过；秤端品名仅存字库代码（7 位商品代码），文本名称不可还原。
     */
    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            withSession { ses ->
                val items = mutableListOf<PluItem>()
                for (no in 1..DahuaProto.UPLOAD_SCAN_MAX) {
                    val resp = ses.request(DahuaProto.pluQueryFrame(no))
                        ?: error("大华回收无应答 (PLU $no)")
                    val parsed = DahuaProto.parsePluResponse(resp, no)
                        ?: continue
                    val decoded = DahuaProto.decodeDigits(parsed.digits)
                    items += PluItem(
                        pluNo = no,
                        name = "",
                        weightFlag = decoded.weightFlag,
                        unitPrice = decoded.unitPrice,
                        usedByDate = decoded.usedByDate,
                        eanCode = DahuaProto.parseBarcode(parsed.barcodeSegment),
                    )
                }
                items
            }
        }
    }

    /**
     * 标签格式表下发：官方 TMA4.0 行为 = 全表 4 页 × 23 格（格式号 0001..0092）照发，
     * ScalePlex 的 LabelTemplate.id 校验范围后随全表一并下发。
     */
    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(template.id in 1..DahuaProto.LABEL_TABLE_SIZE) {
                "大华标签格式号须在 1~${DahuaProto.LABEL_TABLE_SIZE}（当前 ${template.id}）"
            }
            withSession { ses ->
                for (frame in DahuaProto.labelTableFrames()) {
                    val resp = ses.request(frame)
                        ?: error("大华标签格式下发无应答")
                    check(resp.startsWith("0l")) { "大华标签格式下发表决异常: $resp" }
                }
            }
        }
    }

    /**
     * 流水回收：`!0J{记录}A` 逐条读取至 `0j{no}ac` 结束；**不发 `!0H`**（流水保留在秤上）。
     * 数据段字段表已真机标定（时间/店号/PLU/重量/单价/总价，见 [DahuaProto.parseTradeData]）。
     */
    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            withSession { ses ->
                val records = mutableListOf<TradeRecord>()
                for (no in 1..TRADE_SCAN_MAX) {
                    val resp = ses.request(DahuaProto.tradeQueryFrame(no))
                        ?: break // 无应答
                    val data = DahuaProto.parseTradeResponse(resp, no)
                        ?: break // `0j{no}ac` 结束标志
                    records += parseTradeData(no, data)
                }
                records
            }
        }
    }

    /** 流水清空：`!0HA`（回收结束确认帧，秤端清空全部流水）。 */
    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withSession { ses ->
                val resp = ses.request(DahuaProto.tradeClearFrame())
                    ?: error("大华清流水无应答")
                check(resp.startsWith("0h")) { "大华清流水应答异常: $resp" }
            }
        }
    }

    /**
     * 一次性下发单条：`!0V` 写帧 + ACK 前缀校验（不回读验证）。
     */
    private fun writePluOnce(ses: Session, plu: PluItem) {
        val digits = DahuaProto.encodeDigits(plu, shopNo = DEFAULT_SHOP_NO)
        val ack = ses.request(DahuaProto.pluWriteFrame(plu.pluNo, digits, DahuaProto.barcodeSegment(plu.eanCode)))
            ?: error("大华下发无应答 (PLU ${plu.pluNo})")
        check(ack.startsWith("0v" + "%04d".format(plu.pluNo) + "a")) {
            "大华下发烧录应答异常 (PLU ${plu.pluNo}): $ack"
        }
    }

    /**
     * 流水数据解析委托 [DahuaProto.parseTradeData]（字段表见该注释）。
     */
    private fun parseTradeData(recordNo: Int, data: String): TradeRecord =
        DahuaProto.parseTradeData(device.id, recordNo, data)

    /** 单连接串行请求会话：发一帧读至 ETX（或超时），ASCII 文本返回。 */
    private class Session(private val socket: Socket) : AutoCloseable {
        private val out = socket.getOutputStream()
        private val inp = socket.getInputStream()

        /** 发请求并读至 ETX；无应答（超时零字节）返回 null，部分数据交解析层校验前缀。 */
        fun request(frame: ByteArray): String? {
            out.apply { write(frame); flush() }
            val buf = ByteArray(MAX_RESPONSE)
            var total = 0
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                while (total < buf.size) {
                    val n = inp.read(buf, total, buf.size - total)
                    if (n <= 0) break
                    total += n
                    if (buf[total - 1] == ETX_BYTE) break
                }
            } catch (_: SocketTimeoutException) {
                if (total == 0) return null
            }
            if (total == 0) return null
            return String(buf, 0, total, Charsets.US_ASCII)
        }

        override fun close() {
            socket.close()
        }

        private companion object {
            const val MAX_RESPONSE = 4096
            const val ETX_BYTE: Byte = 0x03
        }
    }

    /**
     * 互斥会话执行：同一时刻只允许一个操作连接秤（会话期间秤端拒绝其他连接），
     * busy 期间心跳 getStatus 视为在线。
     */
    private fun <T> withSession(block: (Session) -> T): T {
        check(busy.compareAndSet(false, true)) { "大华秤操作进行中，请稍后再试" }
        try {
            session().use { return block(it) }
        } finally {
            busy.set(false)
        }
    }

    private fun session(): Session = Socket().apply {
        tcpNoDelay = true
        connect(InetSocketAddress(device.ip, targetPort), CONNECT_TIMEOUT_MS)
        soTimeout = READ_TIMEOUT_MS
    }.let { Session(it) }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val READ_TIMEOUT_MS = 2500

        /** 默认店号（秤端店名表 01 号）。 */
        const val DEFAULT_SHOP_NO = 1

        /** 流水扫描上界（逐条查询直到 `c` 结束标志；上限兜底防死循环）。 */
        const val TRADE_SCAN_MAX = 5000
    }
}
