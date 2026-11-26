package com.scaleplex.driver.impl.cas

import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.TradeRecord
import com.scaleplex.driver.api.AbstractScaleDriver
import com.scaleplex.driver.api.ScaleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

/**
 * 凯士 CAS CL 族条码秤驱动（真机校准 2026-11-26 @192.168.0.111，CL5200N V3.02.7）。
 *
 * 传输层结论：CL 族秤监听 **TCP 20304**（凯士默认端口），协议为凯士私有二进制帧
 * （公开渠道无规范文档，ASCII/STX 盲探针均无应答）。接入方式 = 原厂上位机
 * CL-Works Pro 的 .NET 通信库桥接（本机已验证 Open→IsOpen=true 协议握手成功）：
 *
 * - `CasComm.dll`（Cas.CasIOLib.Scales）+ `CasLib.dll`，PowerShell 反射调用；
 * - `Scales.Open(nModel, strVer, ip, port, timeout, retry, PROTOCOL_TYPE)` 后连接为
 *   异步建立，须等待数秒再查 `IsOpen(ip, port)`；用完 `Close(ip, port)`；
 * - PROTOCOL_TYPE：CL5000=5000/CL5000J=5010/CL5200=5200/CL5200J=5210/CL5500=5500/CL7000=7000。
 *
 * 在线探测 = Open + IsOpen 组合；PLU 下发/回收走 CommManager.AppCommManager +
 * Cl5k.Plu.Make4Download（TransType.Download=1000/Upload=2000），待接入。
 *
 * 前置条件：系统属性 `scaleplex.cas.tools` 指向含 CasComm.dll/CasLib.dll/config 的
 * CL-Works Pro 目录（默认用本机已验证的调试包路径）。
 */
class CasClDriver(
    private val device: ScaleDevice,
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.CAS

    override val transport: com.scaleplex.communication.Transport =
        com.scaleplex.communication.TransportFactory.create(device, com.scaleplex.communication.CommEventBus())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!File(toolsDir(), "CasComm.dll").exists()) {
                error("凯士工具目录未配置（系统设置：CL-Works Pro 的 CasComm.dll 所在目录）")
            }
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        bridgeMutex.withLock {
            runCatching {
                val online = probeOnline()
                ScaleStatus(online = online, model = device.model.ifBlank { "CL5200" })
            }
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> =
        Result.failure(UnsupportedOperationException(CAS_DOWNLOAD_PENDING))

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> =
        Result.failure(IllegalStateException(CAS_DOWNLOAD_PENDING))

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士删除 PLU 待接入（Cl5k.Plu.Make4DeleteOne）"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士清空 PLU 待接入（Cl5k.Plu.Make4DeleteAll）"))

    override suspend fun uploadPlu(): Result<List<PluItem>> =
        Result.failure(UnsupportedOperationException("凯士 PLU 回收待接入（Cl5k.Plu.Make4Upload）"))

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        Result.failure(UnsupportedOperationException("凯士标签格式回收待接入（.lfm 格式已定位，见 CL-Works Label 目录）"))

    override suspend fun syncTime(): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士时间同步待接入（Cl5k.ScaleDate）"))

    override suspend fun readTime(): Result<java.time.LocalDateTime> =
        Result.failure(UnsupportedOperationException("凯士读取秤内时间待接入（Cl5k.DateInfo）"))

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士标签下发待接入（DATA_LABEL=1003）"))

    override suspend fun readTradeLog(): Result<List<TradeRecord>> =
        Result.failure(UnsupportedOperationException("凯士流水回收待接入（Cl5k.Report / SaleTransaction）"))

    override suspend fun clearTradeLog(): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士流水清空待接入"))

    // ---- 内部：PowerShell 桥接 ----

    /** 在线探测脚本执行：Open → 等待异步握手 → IsOpen → Close，解析 CAS_ONLINE 标记。 */
    private fun probeOnline(): Boolean {
        val ps1 = File(System.getProperty("java.io.tmpdir"), "scaleplex-cas-probe.ps1")
        // PowerShell 5.1 对无 BOM 的 .ps1 按 ANSI(GBK) 解读，中文工具目录路径会被误读，
        // 必须以 UTF-8 + BOM 写入
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        ps1.writeBytes(bom + buildProbeScript().toByteArray(Charsets.UTF_8))
        val configuredPs = System.getProperty(POWERSHELL_PROPERTY, "").takeIf { it.isNotBlank() }
        val ps = if (configuredPs != null) File(configuredPs) else DEFAULT_POWERSHELL
        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("凯士在线探测超时 (${PROBE_TIMEOUT_MS}ms)")
        }
        val output = process.inputStream.readBytes().toString(Charset.defaultCharset())
        log.info("CAS probe {}: {}", device.ip, output.take(300))
        if (process.exitValue() != 0) {
            error("凯士在线探测失败 (exit=${process.exitValue()}): ${output.take(200)}")
        }
        return output.contains("CAS_ONLINE=True") || output.contains("CAS_ONLINE=1")
    }

    private fun buildProbeScript(): String {
        val tools = toolsDir()
        val ip = device.ip.trim().replace("'", "''")
        val port = device.port
        val protoType = protocolTypeOf(device.model)
        return """
            |${'$'}ErrorActionPreference = 'Stop'
            |Set-Location '$tools'
            |[void][Reflection.Assembly]::LoadFrom('$tools\\CasLib.dll')
            |[void][Reflection.Assembly]::LoadFrom('$tools\\CasComm.dll')
            |${'$'}onRecv = [Cas.Scale.DataReceiveDelegate]{ param(${'$'}recv, ${'$'}data) }
            |${'$'}onState = [Cas.Scale.DataReceiveDelegate]{ param(${'$'}recv, ${'$'}data) }
            |${'$'}scales = New-Object Cas.CasIOLib.Scales(0, '$tools\\config', ${'$'}onRecv, ${'$'}onState)
            |${'$'}r = ${'$'}scales.Open($protoType, 'V3.02.7', '$ip', $port, 3000, 1, $protoType)
            |Start-Sleep -Seconds 4
            |${'$'}io = ${'$'}scales.IsOpen('$ip', $port)
            |[void]${'$'}scales.Close('$ip', $port)
            |Write-Output ('CAS_ONLINE=' + ${'$'}io)
        """.trimMargin()
    }

    /** 型号 → PROTOCOL_TYPE 枚举值（默认 CL5200，真机校准机型）。 */
    private fun protocolTypeOf(model: String): Int {
        val m = model.trim().uppercase().replace(" ", "")
        return when {
            m.contains("CL5000J") -> 5010
            m.contains("CL5000") -> 5000
            m.contains("CL5200J") -> 5210
            m.contains("CL5200") -> 5200
            m.contains("CL5500") -> 5500
            m.contains("CL7000") -> 7000
            else -> 5200
        }
    }

    private fun toolsDir(): String {
        val configured = System.getProperty(TOOLS_PATH_PROPERTY, "").takeIf { it.isNotBlank() }
        if (configured != null) return configured
        return DEFAULT_TOOLS_DIR
    }

    companion object {
        private val log = LoggerFactory.getLogger(CasClDriver::class.java)

        const val TOOLS_PATH_PROPERTY = "scaleplex.cas.tools"

        /** 探测脚本用的 PowerShell 路径（可用系统属性 scaleplex.cas.powershell 覆盖）。 */
        const val POWERSHELL_PROPERTY = "scaleplex.cas.powershell"
        private val DEFAULT_POWERSHELL = File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")

        /** 默认凯士工具目录（本机已验证的 CL-Works Pro 调试包，含 CasComm/CasLib/config）。 */
        const val DEFAULT_TOOLS_DIR =
            "E:\\电子秤\\电子称\\凯士\\凯士秤\\宝能凯士电子称远程调试用文件\\后台管理软件\\后台管理软件" +
                "\\CL Workas Pro\\CL-Works Pro_Bonung_190717\\CL-Works Pro_Bonung_190717"

        private const val PROBE_TIMEOUT_MS = 40_000L

        /** Scales 为全局状态库，多设备并发探测会互相干扰，串行化。 */
        private val bridgeMutex = Mutex()

        private const val CAS_DOWNLOAD_PENDING =
            "凯士 PLU 下发待接入（CommManager.SendTransData + Cl5k.Plu.Make4Download，字段映射待联调）"
    }
}
