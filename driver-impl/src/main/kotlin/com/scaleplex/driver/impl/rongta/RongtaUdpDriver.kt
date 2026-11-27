package com.scaleplex.driver.impl.rongta

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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.charset.Charset

/**
 * 容大 Rongta RLS1000 条码秤驱动（真机校准 2026-11-26 @192.168.0.96）。
 *
 * 传输层结论（pktmon 抓包标定）：PLU 下载 = **UDP ip:5001**，PC→秤单向、零应答；
 * 帧为固定 257 字节 `[类型:1][编号:2 小端][数据:254]`，每帧重复发送 8 次（无 ACK 冗余）：
 * - 会话开场 `0E 01 00 34 12 00` + BCD 时间戳(年月日时分秒)；
 * - 配置帧 `0E 04 00 04 04 04 04`；
 * - PLU 记录 `01 <PLU 号:2 小端>` + GBK 品名（空格填充）；
 * - 会话结束 `0E 02 00 34 12 00`。
 *
 * 在线探测：UDP 存活检查（连发两个探测报文，Windows 下收到 ICMP 不可达时下一次
 * send 抛 ConnectionReset => 离线；静默 => 在线）。
 *
 * 时间同步（真机标定 2026-11-27）：单发会话开场帧 0E 01（含 BCD 当前时间）×8 即完成
 * 校时（秤屏/标签打印时间实测同步），秤端回 0x02 ACK；**绝不能补发会话结束帧 0E 02**
 * ——空会话提交会把秤端 PLU 表清空（真机实验证实）。
 *
 * 待标定：PLU 回收/删除、流水回收/清空、标签格式下发/回收帧类型。
 */
class RongtaUdpDriver(
    private val device: ScaleDevice,
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.RONGTA

    override val transport: com.scaleplex.communication.Transport =
        com.scaleplex.communication.TransportFactory.create(device, com.scaleplex.communication.CommEventBus())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        // UDP 无连接，connect 恒成功；真实可达性由 getStatus 的 UDP 存活检查判定
        Result.success(Unit)
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val online = isUdpAlive(device.ip, device.port)
            ScaleStatus(online = online, model = device.model.ifBlank { "RLS1000" })
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        downloadPluBatch(listOf(plu)).map { }
    }

    /**
     * PLU 批量下发（真机抓包标定 2026-11-26）：
     * 会话帧(0E01+BCD 时间戳) → 配置帧(0E04) → 0B 数据帧(每帧 4 条 PLU) → 01 名称帧(每 PLU 一帧)
     * → 结束帧(0E02)，每帧重复发送 8 次（秤端每帧回 1 字节 0x02 ACK，发送侧忽略）。
     */
    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            DatagramSocket().use { sock ->
                sock.broadcast = true
                val addr = InetAddress.getByName(device.ip)
                fun send8(frame: ByteArray) {
                    repeat(8) {
                        sock.send(DatagramPacket(frame, frame.size, addr, device.port))
                        Thread.sleep(FRAME_GAP_MS)
                    }
                }
                send8(sessionFrame())
                send8(configFrame())
                list.chunked(PLUS_PER_FRAME).forEach { chunk -> send8(pluDataFrame(chunk)) }
                list.forEach { send8(pluNameFrame(it)) }
                send8(endFrame())
                BatchResult.ok(list.size)
            }
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大删除 PLU 帧类型待抓包标定"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大清空 PLU 帧类型待抓包标定"))

    override suspend fun uploadPlu(): Result<List<PluItem>> =
        Result.failure(UnsupportedOperationException("容大 PLU 回收协议待抓包标定"))

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        Result.failure(UnsupportedOperationException("容大标签格式回收待标定（RTPLU 的 .scr 标签文件格式已定位）"))

    /**
     * 时间同步（真机标定 2026-11-27）：单发会话开场帧 0E 01（内含 BCD 当前时间）×8，
     * 秤端每帧回 1 字节 0x02 ACK，收到至少一个 ACK 判成功。
     * 红线：绝不能附加会话结束帧 0E 02——空会话提交会把秤端 PLU 表清空（真机实验证实）。
     */
    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val frame = sessionFrame()
            var acked = 0
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = ACK_TIMEOUT_MS.toInt()
                val addr = InetAddress.getByName(device.ip)
                val buf = ByteArray(16)
                repeat(8) {
                    sock.send(DatagramPacket(frame, frame.size, addr, device.port))
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        if (pkt.length > 0 && buf[0] == ACK_BYTE) acked++
                    } catch (_: java.net.SocketTimeoutException) {
                        // 单帧 ACK 丢失不致命，8 连发兜底
                    }
                    Thread.sleep(FRAME_GAP_MS)
                }
            }
            check(acked > 0) { "容大秤端未确认校时帧（未收到 0x02 ACK）" }
        }
    }

    override suspend fun readTime(): Result<java.time.LocalDateTime> =
        Result.failure(UnsupportedOperationException("容大不支持读取秤内时间"))

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大标签下发待标定（RTRLSLabel.exe 的 .RLS 格式已定位）"))

    override suspend fun readTradeLog(): Result<List<TradeRecord>> =
        Result.failure(UnsupportedOperationException("容大流水回收协议待抓包标定"))

    override suspend fun clearTradeLog(): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大流水清空协议待抓包标定"))

    /**
     * UDP 存活检查：首包触发可能的 ICMP 端口不可达，短暂等待后第二包若抛
     * ConnectionReset 判离线（端口关闭/主机不可达），静默判在线。
     */
    private fun isUdpAlive(ip: String, port: Int, probeMs: Long = PROBE_WAIT_MS): Boolean =
        DatagramSocket().use { s ->
            val addr = InetAddress.getByName(ip)
            s.send(DatagramPacket(byteArrayOf(0x00), 1, addr, port))
            try {
                Thread.sleep(probeMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            try {
                s.send(DatagramPacket(byteArrayOf(0x00), 1, addr, port))
            } catch (_: SocketException) {
                return@use false
            }
            true
        }

    // ---- 帧构造（布局经真机抓包标定，见类注释） ----

    private fun blankFrame(type: Int): ByteArray = ByteArray(FRAME_SIZE).also { it[0] = type.toByte() }

    /** 会话开场帧：0E 01 00 34 12 00 + BCD 时间戳(年月日时分秒)。单发即校时（时间戳=PC 当前时间）。 */
    private fun sessionFrame(): ByteArray {
        val f = blankFrame(0x0E)
        f[1] = 0x01
        f[2] = 0x00
        f[3] = 0x34
        f[4] = 0x12
        f[5] = 0x00
        val now = java.time.LocalDateTime.now()
        val bcd = listOf(now.year % 100, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
        bcd.forEachIndexed { i, v -> f[6 + i] = bcdByte(v) }
        return f
    }

    /** 配置帧：0E 04 00 04 04 04 04（真机原样）。 */
    private fun configFrame(): ByteArray {
        val f = blankFrame(0x0E)
        f[1] = 0x04
        f[2] = 0x00
        f[3] = 0x04
        f[4] = 0x04
        f[5] = 0x04
        f[6] = 0x04
        return f
    }

    /** 结束帧：0E 02 00 34 12 00。 */
    private fun endFrame(): ByteArray {
        val f = blankFrame(0x0E)
        f[1] = 0x02
        f[2] = 0x00
        f[3] = 0x34
        f[4] = 0x12
        f[5] = 0x00
        return f
    }

    /** 01 名称帧：[01][PLU 号 int16 LE][品名 GBK 36B 空格填充][00 填充]。 */
    private fun pluNameFrame(plu: PluItem): ByteArray {
        val f = blankFrame(0x01)
        f[1] = (plu.pluNo and 0xFF).toByte()
        f[2] = ((plu.pluNo shr 8) and 0xFF).toByte()
        writeGbkName(f, 3, plu.displayName)
        return f
    }

    /**
     * 0B 数据帧：[0B] + 4 条 64B 子记录。子记录布局（slot 偏移）：
     * +0..35 品名 GBK 36B；+36..38 货号 int24 LE；+43 部门 BCD；+44 条码低字节；
     * +45..48 价格 4B BCD(×100，8 位数字)；+49 固定 0x04；+50 保质期 int8 天；
     * +57 固定 0x05；其余 00。
     */
    private fun pluDataFrame(chunk: List<PluItem>): ByteArray {
        val f = blankFrame(0x0B)
        chunk.forEachIndexed { k, plu ->
            val base = 1 + k * SLOT_SIZE
            writeGbkName(f, base + 2, plu.displayName)
            val no = plu.pluNo
            // 货号 int24 大端（真机样例 PLU1 = 00 00 01）
            f[base + 38] = ((no shr 16) and 0xFF).toByte()
            f[base + 39] = ((no shr 8) and 0xFF).toByte()
            f[base + 40] = (no and 0xFF).toByte()
            f[base + 43] = bcdByte(deptCode(plu.mainGroup))
            f[base + 44] = (barcodeByte(plu.eanCode)).toByte()
            writeBcdPrice(f, base + 45, plu.unitPrice)
            f[base + 49] = 0x04
            f[base + 50] = (plu.effectiveShelfLifeDays.coerceIn(0, 255)).toByte()
            f[base + 57] = 0x05
        }
        return f
    }

    /** 品名 GBK 写入 [off, off+36)，不足补空格，超长截断。 */
    private fun writeGbkName(f: ByteArray, off: Int, name: String) {
        val bytes = name.toByteArray(gbkCharset())
        val n = minOf(bytes.size, NAME_SIZE)
        for (i in 0 until NAME_SIZE) {
            f[off + i] = if (i < n) bytes[i] else ' '.code.toByte()
        }
    }

    /** 价格 4B BCD：分 → 8 位十进制数字，两位一字节（999999.99 元上限）。 */
    private fun writeBcdPrice(f: ByteArray, off: Int, minor: Long) {
        val digits = minor.coerceIn(0, 99_999_999L).toString().padStart(8, '0')
        for (i in 0 until 4) {
            val hi = digits[i * 2].digitToInt()
            val lo = digits[i * 2 + 1].digitToInt()
            f[off + i] = ((hi shl 4) or lo).toByte()
        }
    }

    /** 两位十进制 → BCD 字节（部门/时间用）。 */
    private fun bcdByte(v: Int): Byte = (((v / 10) shl 4) or (v % 10)).toByte()

    /** GBK 字符集（品名/文本编码）。 */
    private fun gbkCharset(): Charset = Charset.forName("GBK")

    /** 部门取 1..99（BCD 可编码范围），超界回退 1。 */
    private fun deptCode(mainGroup: Int): Int = if (mainGroup in 1..99) mainGroup else 1

    /** 条码取纯数字前缀并取低 8 位（秤端该字段仅 1 字节，真机样例 99）。 */
    private fun barcodeByte(ean: String): Int =
        ean.filter { it.isDigit() }.take(9).toIntOrNull()?.and(0xFF) ?: 0

    companion object {
        private const val PROBE_WAIT_MS = 300L
        private const val FRAME_SIZE = 257
        private const val SLOT_SIZE = 64
        private const val NAME_SIZE = 36
        private const val PLUS_PER_FRAME = 4
        private const val FRAME_GAP_MS = 5L
        private const val ACK_TIMEOUT_MS = 300L
        private const val ACK_BYTE: Byte = 0x02
    }
}
