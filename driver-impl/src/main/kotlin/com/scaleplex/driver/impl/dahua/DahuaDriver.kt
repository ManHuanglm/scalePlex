package com.scaleplex.driver.impl.dahua

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.Transport
import com.scaleplex.communication.TransportFactory
import com.scaleplex.core.model.BatchError
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.TradeRecord
import com.scaleplex.driver.api.AbstractScaleDriver
import com.scaleplex.driver.api.ScaleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 大华 DAHUA 驱动 v2 —— 基于官方 DhScalePluNet.dll（information/大华秤接口）：
 *
 * - PLU 下发：生成官方明文 PLU 文本（[DahuaPluTextCodec]，dhBZ5/dhbz6/TMA07 三种版本，
 *   按设备型号推断）→ 写 dhip.ini/dhplupathname.ini/dhpluversion.ini → 桥接调用
 *   `dhSendPluDefault()`，解析 Return.txt（[DahuaBridge]）；
 * - 交易流水回收：`dhRcvRdDefault()` → record.txt（IP/时间/部门/PLU/重量/单价/金额）；
 * - 在线探测：TCP socket 连接（官方 DLL 无独立探测函数；网络秤为 TCP 监听，
 *   connect 可判在线，待实体秤校准更精确的探测方式）。
 *
 * 未接入项（官方 DLL 无对应接口，诚实标注）：
 * - PLU 回收（dhRcvRdDefault 仅流水，无 PLU 上传函数）；
 * - 标签格式下发/回收、单条 PLU 删除、清空 PLU、时间同步、流水清空。
 */
class DahuaDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DAHUA

    private val pluVersion: DahuaPluTextCodec.PluVersion
        get() = DahuaPluTextCodec.PluVersion.fromModel(device.model)

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val online = tcpProbe(device.ip, device.port)
            ScaleStatus(online = online, model = device.model.ifBlank { "DAHUA" })
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (sendCode, returnCode) = DahuaBridge.downloadPlu(listOf(plu), device.ip, pluVersion)
            checkSendResult(sendCode, returnCode)
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            val (sendCode, returnCode) = DahuaBridge.downloadPlu(list, device.ip, pluVersion)
            if (sendCode == 0 && (returnCode == null || returnCode == 0)) {
                BatchResult.ok(list.size)
            } else {
                BatchResult(
                    total = list.size, failed = list.size,
                    errors = listOf(BatchError(device.id, null, sendErrorMessage(sendCode, returnCode))),
                )
            }
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching { DahuaBridge.readTradeLog(device.ip, device.id) }
    }

    /** TCP 连接探测（连接成功=在线）。 */
    private fun tcpProbe(ip: String, port: Int, timeoutMs: Int = 3000): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun checkSendResult(sendCode: Int, returnCode: Int?) {
        if (sendCode != 0 || (returnCode != null && returnCode != 0)) {
            error(sendErrorMessage(sendCode, returnCode))
        }
    }

    private fun sendErrorMessage(sendCode: Int, returnCode: Int?): String {
        val msg = StringBuilder(DahuaBridge.describeSendCode(sendCode))
        if (sendCode == -6 && returnCode != null) {
            msg.append("：").append(DahuaBridge.describeReturnCode(returnCode))
        }
        return msg.toString()
    }
}
