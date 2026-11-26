package com.scaleplex.driver.impl.cas

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
 * 在线探测 = Open + IsOpen 组合；PLU 下发 = ASCII 键值协议（抓包逆向，见 downloadPluBatch）；
 * 回收/删除/时间同步走 CommManager.AppCommManager + Cl5k（待接入）。
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

    /**
     * PLU 批量下发（真机抓包标定 2026-11-26：CL-Works Pro 下载报文逆向）：
     * ASCII 键值协议 `W02A<PLU:5>,01L010A:F=<字段>.<类型>,<长度>:<数据>...`，
     * 秤端应答 `...W02:O` = OK。字段：01=PLU号(int16LE) 02=部门(int32LE)
     * 04=? 06=单价(int32LE 分) 0A=品名(GBK)，其余字段按真机模板置零。
     */
    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        downloadPluBatch(listOf(plu)).map { }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            java.net.Socket().use { sock ->
                sock.tcpNoDelay = true
                sock.connect(java.net.InetSocketAddress(device.ip, device.port), 5000)
                sock.soTimeout = RESPONSE_TIMEOUT_MS.toInt()
                val out = sock.getOutputStream()
                val inp = sock.getInputStream()
                var ok = 0
                val errors = mutableListOf<BatchError>()
                for (plu in list) {
                    val frame = buildCasPluFrame(plu)
                    // 原厂模式：同帧重复发送 8 次（间隔 ~50ms），秤端去重后应答 W02:O/E
                    repeat(8) {
                        out.write(frame)
                        out.flush()
                        Thread.sleep(FRAME_GAP_MS)
                    }
                    val resp = readUntilW02(inp)
                    val text = resp?.toString(Charsets.ISO_8859_1) ?: ""
                    if (text.contains("W02:O")) {
                        ok++
                    } else {
                        errors += BatchError(device.id, plu.pluNo, "凯士未确认 (期望 W02:O): ${text.take(80)}")
                    }
                }
                BatchResult(total = list.size, success = ok, failed = list.size - ok, errors = errors)
            }
        }
    }

    /** 读取秤端应答直到出现 W02: 标记（超时返回已收内容）。 */
    private fun readUntilW02(inp: java.io.InputStream): ByteArray? {
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        val buf = ByteArray(4096)
        var acc = ByteArray(0)
        while (System.currentTimeMillis() < deadline) {
            val n = try {
                inp.read(buf)
            } catch (_: java.net.SocketTimeoutException) {
                return if (acc.isEmpty()) null else acc
            }
            if (n <= 0) return if (acc.isEmpty()) null else acc
            acc += buf.copyOf(n)
            if (String(acc, Charsets.ISO_8859_1).contains("W02:")) return acc
        }
        return if (acc.isEmpty()) null else acc
    }

    /**
     * 构造凯士 PLU 下发 ASCII 帧。字节经 ISO-8859-1 直通保持二进制安全；
     * L010A 为标签字段（真机 PLU1 样例，语义待进一步标定）。
     */
    private fun buildCasPluFrame(plu: PluItem): ByteArray {
        val sb = StringBuilder()
        sb.append("W02A%05d,01L010A:".format(plu.pluNo.coerceIn(0, 99999)))
        sb.append(field("01", "57", intLe(plu.pluNo, 2)))
        sb.append(field("02", "4C", intLe(casDept(plu.mainGroup), 4)))
        sb.append(field("04", "4D", byteArrayOf(0x01)))
        sb.append(field("06", "4C", intLe(plu.unitPrice.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), 4)))
        sb.append(field("0B", "4C", intLe(0, 4)))
        sb.append(field("0F", "42", byteArrayOf(0x00)))
        sb.append(field("10", "4C", intLe(0, 4)))
        sb.append(field("13", "42", byteArrayOf(0x00)))
        sb.append(field("03", "53", ByteArray(0)))
        sb.append(field("4C", "42", byteArrayOf(0x00)))
        sb.append(field("4D", "42", byteArrayOf(0x00)))
        sb.append(field("4E", "42", byteArrayOf(0x00)))
        sb.append(field("4F", "42", byteArrayOf(0x00)))
        sb.append(field("27", "42", byteArrayOf(0x00)))
        sb.append(field("28", "42", byteArrayOf(0x00)))
        sb.append(field("29", "42", byteArrayOf(0x00)))
        sb.append(field("2A", "42", byteArrayOf(0x00)))
        sb.append(field("2B", "42", byteArrayOf(0x00)))
        sb.append(field("0A", "53", plu.displayName.toByteArray(gbkCharset())))
        sb.append(field("1E", "53", ByteArray(0)))
        sb.append(field("1F", "53", ByteArray(0)))
        sb.append(field("5C", "53", ByteArray(0)))
        sb.append(field("5D", "53", ByteArray(0)))
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /** 单字段：`F=<字段ID>.<类型>,<长度>:<数据>`。 */
    private fun field(id: String, type: String, data: ByteArray): String =
        "F=$id.$type,${data.size}:" + String(data, Charsets.ISO_8859_1)

    private fun intLe(value: Int, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    /** 部门取 1..9999，超界回退 1。 */
    private fun casDept(mainGroup: Int): Int = if (mainGroup in 1..9999) mainGroup else 1

    /** GBK 字符集（品名/文本编码）。 */
    private fun gbkCharset(): Charset = Charset.forName("GBK")

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

        private const val RESPONSE_TIMEOUT_MS = 8_000L
        private const val FRAME_GAP_MS = 50L

        /** Scales 为全局状态库，多设备并发探测会互相干扰，串行化。 */
        private val bridgeMutex = Mutex()
    }
}
