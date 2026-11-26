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
 * 待标定：254 字节数据区内价格/条码/单位/部门字段偏移未定位（需 RTPLU 编辑已知价格
 * PLU 后抓包 diff），PLU 下发暂不可用——避免以错误字段布局覆盖秤端数据。
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
        Result.failure(UnsupportedOperationException(FIELD_CALIBRATION_PENDING))
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException(FIELD_CALIBRATION_PENDING))
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大删除 PLU 帧类型待抓包标定"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大清空 PLU 帧类型待抓包标定"))

    override suspend fun uploadPlu(): Result<List<PluItem>> =
        Result.failure(UnsupportedOperationException("容大 PLU 回收协议待抓包标定"))

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        Result.failure(UnsupportedOperationException("容大标签格式回收待标定（RTPLU 的 .scr 标签文件格式已定位）"))

    override suspend fun syncTime(): Result<Unit> =
        Result.failure(UnsupportedOperationException("容大时间同步帧待抓包标定"))

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

    companion object {
        private const val PROBE_WAIT_MS = 300L
        private const val FIELD_CALIBRATION_PENDING =
            "容大 PLU 下发字段待标定（价格/条码字段偏移未定位），请先用原厂 RTPLU 完成标定后接入"
    }
}
