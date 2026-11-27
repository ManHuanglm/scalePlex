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
 * 在线探测 = Open + IsOpen 组合；PLU 下发 = PowerShell 桥接官方库（CasLib/Cl5k/CommManager）
 * 生成协议帧 + Kotlin 裸 TCP 20304 逐帧发送（真机验证 W02:O，2026-11-27）。
 * 手工逆向 ASCII 帧秤端一律拒绝（W02:EFE），字段集由官方 PluSchema 控制（见 buildFrameScript）；
 * 时间同步/读时间 = Cl5k.ScaleDate 帧桥接（真机验证 2026-11-27，ACK W45:O01）；
 * PLU 回收 = 请求帧 `R02F{dept:X2}{pluNo:X6},00\n`（官方 Make4Upload 生成格式，Kotlin 直接拼）
 * + `W02A` 数据帧顺序解析（真机验证 2026-11-27，见 uploadPlu）；删除待接入（Make4DeleteOne）。
 *
 * 前置条件：系统属性 `scaleplex.cas.tools` 指向含 CasComm.dll/CasLib.dll/Cl5k.dll/CommManager.dll/
 * DBManager.dll 及 config\plustruct.str 的 CL-Works 目录。
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
            if (!File(toolsDir(), "config${File.separator}plustruct.str").exists()) {
                error("凯士工具目录缺少 config\\plustruct.str（帧生成 schema 依赖）")
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
     * PLU 批量下发（真机校准 2026-11-27：官方 CL-Works 库桥接生成帧 + 裸 TCP 发送）。
     *
     * 链路 = PowerShell 桥接官方库（CasLib/Cl5k/CommManager）批量生成协议帧，
     * Kotlin 裸 TCP 20304 逐帧发送。手工逆向帧被秤端拒绝（W02:EFE），官方库
     * 生成帧（ plustruct schema 23 字段）经真机验证回 W02:O。
     */
    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        downloadPluBatch(listOf(plu)).map { }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            val frames = generateFramesViaBridge(list)
            java.net.Socket().use { sock ->
                sock.tcpNoDelay = true
                sock.connect(java.net.InetSocketAddress(device.ip, device.port), 5000)
                sock.soTimeout = RESPONSE_TIMEOUT_MS.toInt()
                val out = sock.getOutputStream()
                val inp = sock.getInputStream()
                var ok = 0
                val errors = mutableListOf<BatchError>()
                for ((index, frame) in frames.withIndex()) {
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
                        errors += BatchError(device.id, list[index].pluNo, "凯士未确认 (期望 W02:O): ${text.take(80)}")
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

    /** 通过 PowerShell 桥接官方库批量生成协议帧（一次进程生成全部帧）。 */
    private fun generateFramesViaBridge(list: List<PluItem>): List<ByteArray> {
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val jsonFile = File(tmp, "scaleplex-cas-plu.json")
        val outBin = File(tmp, "scaleplex-cas-frames.bin")
        val ps1 = File(tmp, "scaleplex-cas-frames.ps1")
        jsonFile.writeText(buildPluJson(list), Charsets.UTF_8)
        // PowerShell 5.1 对无 BOM 的 .ps1 按 ANSI(GBK) 解读，必须 UTF-8 + BOM
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        ps1.writeBytes(bom + buildFrameScript(jsonFile, outBin).toByteArray(Charsets.UTF_8))
        val configuredPs = System.getProperty(POWERSHELL_PROPERTY, "").takeIf { it.isNotBlank() }
        val ps = if (configuredPs != null) File(configuredPs) else DEFAULT_POWERSHELL
        val timeoutMs = FRAME_GEN_BASE_TIMEOUT_MS + FRAME_GEN_PER_PLU_TIMEOUT_MS * list.size
        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        )
            .apply {
                environment()[CAS_TOOLS_ENV] = toolsDir()
                System.getProperty("scaleplex.data.dir")?.let { environment()[DATA_DIR_ENV] = it }
            }
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("凯士帧生成超时 (${timeoutMs}ms)")
        }
        val output = process.inputStream.readBytes().toString(Charset.defaultCharset())
        log.info("CAS frame gen {}: {}", device.ip, output.take(300))
        if (process.exitValue() != 0 || !output.contains("FRAMES_OK")) {
            error("凯士帧生成失败 (exit=${process.exitValue()}): ${output.take(300)}")
        }
        return parseFrameStream(outBin.readBytes())
    }

    /** 解析帧流：[int32 LE 长度][帧字节] 重复。 */
    private fun parseFrameStream(bin: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val buf = java.nio.ByteBuffer.wrap(bin).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 4) {
            val len = buf.int
            if (len <= 0 || len > buf.remaining()) error("凯士帧流损坏 (len=$len)")
            val frame = ByteArray(len)
            buf.get(frame)
            frames += frame
        }
        return frames
    }

    /** 读取秤端应答直到出现指定标记（超时返回已收内容，ISO_8859_1 文本）。 */
    private fun readReplyText(inp: java.io.InputStream, marker: String): String {
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        val buf = ByteArray(4096)
        var acc = ByteArray(0)
        while (System.currentTimeMillis() < deadline) {
            val n = try {
                inp.read(buf)
            } catch (_: java.net.SocketTimeoutException) {
                return String(acc, Charsets.ISO_8859_1)
            }
            if (n <= 0) break
            acc += buf.copyOf(n)
            if (String(acc, Charsets.ISO_8859_1).contains(marker)) break
        }
        return String(acc, Charsets.ISO_8859_1)
    }

    /**
     * 解析时间回包：取首个 `(`（0x28 帧尾 BCC 分隔）前的包，提取
     * `Y/M/D/h/m/s={X2}.` 十六进制字段（Y 为年-2000）。
     */
    private fun parseClTime(text: String): java.time.LocalDateTime? {
        val packet = text.substringBefore("(")
        val field = { code: String ->
            Regex("$code=([0-9A-Fa-f]{2})\\.").find(packet)?.groupValues?.get(1)?.toInt(16)
        }
        val year = field("Y") ?: return null
        val month = field("M") ?: return null
        val day = field("D") ?: return null
        val hour = field("h") ?: return null
        val minute = field("m") ?: return null
        val second = field("s") ?: return null
        return try {
            java.time.LocalDateTime.of(2000 + year, month, day, hour, minute, second)
        } catch (_: java.time.DateTimeException) {
            null
        }
    }

    /**
     * 通过桥接脚本生成时间同步/读时间帧（真机校准 2026-11-27）：
     * Scales.Open 真握手 → m_mapClients 反射取 Client → DateInfo(nNo=1) →
     * Cl5k.ScaleDate.Make4Upload/Make4Download → [int32 长度][帧] 流文件。
     * 手工构造的 IConnect 桩会被 Make4Download 拒绝（-1003），必须用真实 Client。
     */
    private fun generateDateFramesViaBridge(includeDownload: Boolean): List<ByteArray> {
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val outBin = File(tmp, "scaleplex-cas-date-frames.bin")
        val ps1 = File(tmp, "scaleplex-cas-date.ps1")
        // PowerShell 5.1 对无 BOM 的 .ps1 按 ANSI(GBK) 解读，必须 UTF-8 + BOM
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        ps1.writeBytes(bom + buildDateFrameScript(includeDownload, outBin).toByteArray(Charsets.UTF_8))
        val configuredPs = System.getProperty(POWERSHELL_PROPERTY, "").takeIf { it.isNotBlank() }
        val ps = if (configuredPs != null) File(configuredPs) else DEFAULT_POWERSHELL
        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        )
            .apply {
                environment()[CAS_TOOLS_ENV] = toolsDir()
                System.getProperty("scaleplex.data.dir")?.let { environment()[DATA_DIR_ENV] = it }
            }
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(DATE_GEN_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("凯士时间帧生成超时 (${DATE_GEN_TIMEOUT_MS}ms)")
        }
        val output = process.inputStream.readBytes().toString(Charset.defaultCharset())
        log.info("CAS date frame gen {}: {}", device.ip, output.take(300))
        if (process.exitValue() != 0 || !output.contains("FRAMES_OK")) {
            error("凯士时间帧生成失败 (exit=${process.exitValue()}): ${output.take(300)}")
        }
        return parseFrameStream(outBin.readBytes())
    }

    /** 时间帧生成脚本：真握手 + 反射 Client + DateInfo + Make4Upload/Make4Download。 */
    private fun buildDateFrameScript(includeDownload: Boolean, outBin: File): String {
        val outPath = outBin.absolutePath.replace("'", "''")
        val tools = toolsDir().replace("'", "''")
        val ip = device.ip.trim().replace("'", "''")
        val port = device.port
        val protoType = protocolTypeOf(device.model)
        val makeCall = if (includeDownload) {
            "|${'$'}frame = [Cl5k.ScaleDate]::Make4Download(0, ${'$'}conn, ${'$'}di, [ref]${'$'}nResult)"
        } else {
            "|${'$'}frame = [Cl5k.ScaleDate]::Make4Upload(0, ${'$'}conn, ${'$'}di, [ref]${'$'}nResult)"
        }
        return """
            |${'$'}ErrorActionPreference = 'Stop'
            |${'$'}tools = ${'$'}env:SCALEPLEX_CAS_TOOLS
            |Set-Location ${'$'}tools
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\CasLib.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\CasComm.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\AppSetting.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\Cl5k.dll")
            |${'$'}onRecv = [Cas.Scale.DataReceiveDelegate]{ param(${'$'}recv, ${'$'}data) }
            |${'$'}onState = [Cas.Scale.DataReceiveDelegate]{ param(${'$'}recv, ${'$'}data) }
            |${'$'}scales = New-Object Cas.CasIOLib.Scales(0, "${'$'}tools\config", ${'$'}onRecv, ${'$'}onState)
            |${'$'}r = ${'$'}scales.Open($protoType, 'V3.02.7', '$ip', $port, 3000, 1, $protoType)
            |Start-Sleep -Seconds 4
            |if (${'$'}scales.IsOpen('$ip', $port) -ne 1) { throw "握手失败 (Open=${'$'}r)" }
            |${'$'}fMap = [Cas.CasIOLib.Scales].GetField('m_mapClients', 'NonPublic,Instance')
            |${'$'}map = ${'$'}fMap.GetValue(${'$'}scales)
            |${'$'}conn = ${'$'}null
            |foreach (${'$'}k in ${'$'}map.Keys) { ${'$'}conn = ${'$'}map[${'$'}k] }
            |if (-not ${'$'}conn) { throw "未取到连接对象" }
            |${'$'}now = Get-Date
            |${'$'}bytes = [Byte[]]@([Byte](${'$'}now.Year - 2000), [Byte]${'$'}now.Month, [Byte]${'$'}now.Day, [Byte]${'$'}now.Hour, [Byte]${'$'}now.Minute, [Byte]${'$'}now.Second)
            |${'$'}casLib = [Reflection.Assembly]::LoadFrom("${'$'}tools\CasLib.dll")
            |${'$'}diType = ${'$'}casLib.GetType('Cas.Scale.Data+Structure+DateInfo')
            |${'$'}di = [Activator]::CreateInstance(${'$'}diType)
            |${'$'}fi = 'Public,NonPublic,Instance'
            |${'$'}diType.GetField('arrSystemDate', ${'$'}fi).SetValue(${'$'}di, ${'$'}bytes)
            |${'$'}diType.GetField('arrSaleDate', ${'$'}fi).SetValue(${'$'}di, ${'$'}bytes)
            |${'$'}diType.GetField('nNo', ${'$'}fi).SetValue(${'$'}di, [UInt32]1)
            |${'$'}diType.GetField('strName', ${'$'}fi).SetValue(${'$'}di, '$ip')
            |${'$'}diType.GetField('m_nSubDataType', ${'$'}fi).SetValue(${'$'}di, [Int32]2031)
            |${'$'}nResult = 0
            $makeCall
            |if (-not ${'$'}frame) { throw "帧生成失败 (nResult=${'$'}nResult)" }
            |[void]${'$'}scales.Close('$ip', $port)
            |${'$'}bw = New-Object IO.BinaryWriter([IO.File]::Create("$outPath"))
            |${'$'}bw.Write([int]${'$'}frame.Length)
            |${'$'}bw.Write(${'$'}frame)
            |${'$'}bw.Close()
            |Write-Output 'FRAMES_OK'
        """.trimMargin()
    }

    /** PLU 列表 → JSON（品名走 Unicode，官方库按系统 ANSI(GBK) 编码回帧）。 */
    private fun buildPluJson(list: List<PluItem>): String = list.joinToString(",", "[", "]") { plu ->
        """{"pluNo":${plu.pluNo},"dept":${casDept(plu.mainGroup)},"pluType":${pluTypeOf(plu)}, """ +
            """"priceMinor":${plu.unitPrice},"sellByDate":${plu.usedByDate},"name":"${jsonEscape(plu.displayName)}"}"""
    }

    /** CL 协议 PLU 类型：Weight=1 / Count=2。 */
    private fun pluTypeOf(plu: PluItem): Int = if (plu.weightFlag == 1) 2 else 1

    private fun jsonEscape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", " ")

    /**
     * 官方库帧生成脚本：加载 CL-Works DLL → 填充 PLU schema（23 字段，对照真机抓包）→
     * 逐 PLU MakePLUData + Make4Download，输出 [int32 长度][帧] 流。
     * 价格直填 nUnitPrice（协议单位 = 分），DataRow 值仅兜底。
     */
    private fun buildFrameScript(jsonFile: File, outBin: File): String {
        val jsonPath = jsonFile.absolutePath.replace("'", "''")
        val outPath = outBin.absolutePath.replace("'", "''")
        return """
            |${'$'}ErrorActionPreference = 'Stop'
            |${'$'}jsonPath = "$jsonPath"
            |${'$'}outPath = "$outPath"
            |${'$'}tools = ${'$'}env:SCALEPLEX_CAS_TOOLS
            |Set-Location ${'$'}tools
            |${'$'}casLib = [Reflection.Assembly]::LoadFrom("${'$'}tools\CasLib.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\CasComm.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\AppSetting.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\Cl5k.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\CommManager.dll")
            |[void][Reflection.Assembly]::LoadFrom("${'$'}tools\DBManager.dll")
            |${'$'}cs = @'
            |using System;
            |public class ScaleplexConnect : Cas.IConnect
            |{
            |    private string _ip;
            |    public ScaleplexConnect(string ip) { _ip = ip; }
            |    public Int32 GetCode() { return 111; }
            |    public Int32 GetKind() { return 5200; }
            |    public String GetName() { return _ip; }
            |    public Int32 Open() { return 1; }
            |    public Int32 Close() { return 1; }
            |    public Boolean IsOpen() { return true; }
            |    public Int32 SetCallBackRecvData(Cas.Scale.SockReceiveDelegate d) { return 1; }
            |    public Int32 SetCallBackRecvState(Cas.Scale.DataReceiveDelegate d) { return 1; }
            |}
            |'@
            |Add-Type -TypeDefinition ${'$'}cs -ReferencedAssemblies @("${'$'}tools\CasLib.dll") -Language CSharp
            |${'$'}config = ${'$'}null
            |${'$'}cands = @("${'$'}tools\config")
            |if (${'$'}env:SCALEPLEX_DATA_DIR) { ${'$'}cands += (Join-Path ${'$'}env:SCALEPLEX_DATA_DIR "tools\cas\config") }
            |foreach (${'$'}cand in ${'$'}cands) {
            |    if (Test-Path (Join-Path ${'$'}cand "plustruct.str")) { ${'$'}config = ${'$'}cand; break }
            |}
            |if (-not ${'$'}config) { throw "未找到 plustruct.str（工具目录 config 或数据目录 tools\cas\config）" }
            |${'$'}acm = New-Object CommManager.AppCommManager
            |[void]${'$'}acm.InitCommLibrary(0, ${'$'}config)
            |${'$'}schemaPs = [Cl5k.Plu].GetProperties('Public,NonPublic,Static') | Where-Object { ${'$'}_.Name -eq 'PluSchema' } | Select-Object -First 1
            |${'$'}schema = ${'$'}schemaPs.GetGetMethod(${'$'}true).Invoke(${'$'}null, ${'$'}null)
            |if (${'$'}schema.Count -eq 0) {
            |    ${'$'}fiType = ${'$'}casLib.GetType('Cas.FieldInfo')
            |    ${'$'}defs = @(
            |        @(0x01,0x57,2,2,'DeptNo'), @(0x02,0x4C,6,4,'PluNo'), @(0x04,0x4D,1,1,'PluType'),
            |        @(0x06,0x4C,7,4,'UnitPrice'), @(0x0B,0x4C,8,4,'ItemCode'), @(0x0F,0x42,1,1,''),
            |        @(0x10,0x4C,6,4,'SellByDate'), @(0x13,0x42,1,1,''), @(0x03,0x53,0,0,''),
            |        @(0x4C,0x42,1,1,''), @(0x4D,0x42,1,1,''), @(0x4E,0x42,1,1,''), @(0x4F,0x42,1,1,''),
            |        @(0x27,0x42,1,1,''), @(0x28,0x42,1,1,''), @(0x29,0x42,1,1,''), @(0x2A,0x42,1,1,''), @(0x2B,0x42,1,1,''),
            |        @(0x0A,0x53,40,40,'Name1'), @(0x1E,0x53,40,40,'Name2'), @(0x1F,0x53,40,40,'Name3'),
            |        @(0x5C,0x53,40,40,''), @(0x5D,0x53,40,40,'')
            |    )
            |    foreach (${'$'}d in ${'$'}defs) {
            |        ${'$'}fi = [Activator]::CreateInstance(${'$'}fiType)
            |        ${'$'}fi.nCode = [UInt16]${'$'}d[0]
            |        ${'$'}fi.nType = [Byte]${'$'}d[1]
            |        ${'$'}fi.nLength = [Int16]${'$'}d[2]
            |        ${'$'}fi.nSize = [Int16]${'$'}d[3]
            |        ${'$'}fi.nUseCL5200 = [Int16]1
            |        ${'$'}fi.nEnable = ${'$'}true
            |        ${'$'}fi.nLevel = [Byte]1
            |        if (${'$'}d[4]) { ${'$'}fi.strDBFieldName = ${'$'}d[4] }
            |        [void]${'$'}schema.Add([int]${'$'}d[0], ${'$'}fi)
            |    }
            |}
            |${'$'}plus = Get-Content -Raw -Encoding UTF8 "${'$'}jsonPath" | ConvertFrom-Json
            |${'$'}bw = New-Object IO.BinaryWriter([IO.File]::Create("${'$'}outPath"))
            |foreach (${'$'}p in ${'$'}plus) {
            |    ${'$'}table = New-Object System.Data.DataTable
            |    foreach (${'$'}n in @('PluNo','DeptNo','PluType','GroupNo','LabelNo','UnitPrice','SellByDate','ItemCode')) {
            |        [void]${'$'}table.Columns.Add(${'$'}n, [int])
            |    }
            |    [void]${'$'}table.Columns.Add('Name1', [string])
            |    ${'$'}row = ${'$'}table.NewRow()
            |    ${'$'}row['PluNo'] = [int]${'$'}p.pluNo
            |    ${'$'}row['DeptNo'] = [int]${'$'}p.dept
            |    ${'$'}row['PluType'] = [int]${'$'}p.pluType
            |    ${'$'}row['GroupNo'] = 1
            |    ${'$'}row['UnitPrice'] = [int][math]::Round(${'$'}p.priceMinor / 100.0)
            |    ${'$'}row['SellByDate'] = [int]${'$'}p.sellByDate
            |    ${'$'}row['Name1'] = ${'$'}p.name
            |    [void]${'$'}table.Rows.Add(${'$'}row)
            |    ${'$'}pluData = [CommManager.AppCommManager].GetMethod('MakePLUData').Invoke(${'$'}acm, @(${'$'}row))
            |    ${'$'}pluData.nUnitPrice = [UInt32]${'$'}p.priceMinor
            |    ${'$'}nResult = 0
            |    ${'$'}frame = [Cl5k.Plu]::Make4Download(0, (New-Object ScaleplexConnect("${'$'}env:SCALEPLEX_CAS_IP")), ${'$'}pluData, [ref]${'$'}nResult)
            |    ${'$'}bw.Write([int]${'$'}frame.Length)
            |    ${'$'}bw.Write(${'$'}frame)
            |}
            |${'$'}bw.Close()
            |Write-Output 'FRAMES_OK'
        """.trimMargin()
    }

    /** 部门取 1..9999，超界回退 1。 */
    private fun casDept(mainGroup: Int): Int = if (mainGroup in 1..9999) mainGroup else 1

    /**
     * PLU 批量回收（真机破解 2026-11-27 @192.168.0.111）。
     *
     * 链路 = 请求帧 `R02F{dept:X2}{pluNo:X6},00\n`（官方 Cl5k.Plu.Make4Upload 生成的格式，
     * dept/pluNo 必须 ≥1；真机验证秤端实际只按 PLU 号查询，dept 恒填 01）→ 单发单收
     * （复用一条 TCP 连接）→ 回帧 `W02A{plu:X5},{dept:X2}L{len}:{头字段}F={code}.{type},{len}:`
     * + 原始字段字节 + 1B BCC。空槽/越界回 `W02AF4240,00L002B:...N=0000`。
     * 遍历 1..MAX_UPLOAD_PLU，连续 EMPTY_RUN_STOP 个空槽即提前终止。
     */
    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = mutableListOf<PluItem>()
            var emptyRun = 0
            java.net.Socket().use { sock ->
                sock.tcpNoDelay = true
                sock.connect(java.net.InetSocketAddress(device.ip, device.port), 5000)
                sock.soTimeout = RESPONSE_TIMEOUT_MS.toInt()
                val out = sock.getOutputStream()
                val inp = sock.getInputStream()
                for (pluNo in 1..MAX_UPLOAD_PLU) {
                    val req = ("R02F01" + String.format("%06X", pluNo) + ",00\n")
                        .toByteArray(Charsets.ISO_8859_1)
                    out.write(req)
                    out.flush()
                    val frame = readUploadFrame(inp) ?: break
                    val text = String(frame, Charsets.ISO_8859_1)
                    if (!text.startsWith("W02A")) break
                    if (text.startsWith("W02AF4240")) {
                        emptyRun++
                        if (emptyRun >= EMPTY_RUN_STOP) break
                    } else {
                        emptyRun = 0
                        parseCasPluFrame(frame)?.let { result += it }
                    }
                }
            }
            result
        }
    }

    /** 读取一条 `W02A` 回帧：由 `L{hex}:` 载荷长度精确读满（超时/断开返回 null）。 */
    private fun readUploadFrame(inp: java.io.InputStream): ByteArray? {
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        val buf = ByteArray(4096)
        var acc = ByteArray(0)
        while (System.currentTimeMillis() < deadline) {
            val text = String(acc, Charsets.ISO_8859_1)
            val lIdx = text.indexOf('L')
            if (lIdx > 0) {
                val colon = text.indexOf(':', lIdx)
                if (colon > lIdx) {
                    val payloadLen = text.substring(lIdx + 1, colon).toIntOrNull(16)
                    if (payloadLen != null && payloadLen > 0 && acc.size >= colon + 1 + payloadLen) {
                        return acc.copyOf(colon + 1 + payloadLen)
                    }
                }
            }
            val n = try {
                inp.read(buf)
            } catch (_: java.net.SocketTimeoutException) {
                return null
            }
            if (n <= 0) return null
            acc += buf.copyOf(n)
        }
        return null
    }

    /**
     * 顺序解析 `W02A` PLU 回帧为 [PluItem]（官方 Interprete4Upload 对裸回包抛越界，自实现）。
     * 字段格式 `F={code:X2}.{type},{len:X}:` + len 字节原始数据——**len 为十六进制**
     * （真机 PLU4 `F=0A.53,11:` = 17B "鲜带肉尾叉骨 计重" 验证；按十进制读会在 len≥0x0A
     * 时截断名称并错位后续字段，表现为价格 0 + 名称乱码/空白）。type：57=UInt16 LE、
     * 4C=UInt32 LE、4D/42=Byte、53=GBK 字符串（CL-Works 下发的称重品名称含 " 计重" 后缀，
     * 如实保留）。尾字节为 BCC，不入字段区。
     */
    internal fun parseCasPluFrame(frame: ByteArray): PluItem? {
        val text = String(frame, Charsets.ISO_8859_1)
        if (!text.startsWith("W02A") || text.startsWith("W02AF4240")) return null
        var dept = 1
        var pluNo = 0
        var pluType = 1
        var unitPrice = 0L
        var sellByDate = 0
        var name = ""
        var itemCode = 0L
        var pos = text.indexOf("F=")
        while (pos >= 0 && pos + FIELD_HEADER_MIN <= frame.size) {
            val code = hex2(text, pos + 2) ?: break
            if (text[pos + 4] != '.' ) { pos += 2; continue }
            val type = hex2(text, pos + 5) ?: break
            if (text[pos + 7] != ',') break
            var q = pos + 8
            var lenStr = ""
            while (q < text.length && text[q].isHexLenChar()) {
                lenStr += text[q]
                q++
            }
            if (lenStr.isEmpty() || q >= text.length || text[q] != ':') {
                pos += 2
                continue
            }
            val len = lenStr.toInt(16)
            val dataStart = q + 1
            val dataEnd = dataStart + len
            if (dataEnd > frame.size) break
            when (code) {
                FIELD_DEPT -> dept = le16(frame, dataStart)
                FIELD_PLU_NO -> pluNo = le32(frame, dataStart).toInt()
                FIELD_PLU_TYPE -> pluType = frame[dataStart].toInt() and 0xFF
                FIELD_UNIT_PRICE -> unitPrice = le32(frame, dataStart)
                FIELD_NAME -> name = String(frame, dataStart, len, GBK)
                FIELD_ITEM_CODE -> itemCode = le32(frame, dataStart)
                FIELD_SELL_BY_DATE -> sellByDate = le32(frame, dataStart).toInt()
            }
            pos = dataEnd
        }
        if (pluNo <= 0) return null
        return PluItem(
            pluNo = pluNo,
            name = name,
            weightFlag = if (pluType == PLU_TYPE_COUNT) 1 else 0,
            mainGroup = dept,
            unitPrice = unitPrice,
            usedByDate = sellByDate,
            eanCode = if (itemCode > 0) itemCode.toString() else "",
        )
    }

    /** 取 text[idx, idx+2) 的十六进制值（非法返回 null）。 */
    private fun hex2(text: String, idx: Int): Int? {
        if (idx + 2 > text.length) return null
        return text.substring(idx, idx + 2).toIntOrNull(16)
    }

    /** 字段长度字符（帧内 len 为十六进制：0-9/A-F/a-f）。 */
    private fun Char.isHexLenChar(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士删除 PLU 待接入（Cl5k.Plu.Make4DeleteOne）"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("凯士清空 PLU 待接入（Cl5k.Plu.Make4DeleteAll）"))

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        Result.failure(UnsupportedOperationException("凯士标签格式回收待接入（.lfm 格式已定位，见 CL-Works Label 目录）"))

    /**
     * 时间同步（真机校准 2026-11-27 @192.168.0.111）。
     *
     * 链路 = 桥接脚本 Scales.Open 真握手 → 反射 m_mapClients 取真实 Cas.Ethernet.Client
     * （Make4Download 要求 IConnect 可转 ScaleDevice，C# 桩会被拒 -1003）→
     * DateInfo(nNo>=1, arrSystemDate=[年-2000,月,日,时,分,秒] 原始字节) → Cl5k.ScaleDate.
     * Make4Download 生成 `W45F01,00L{len}:Y={X2}.M={X2}.D={X2}.h={X2}.m={X2}.s={X2}.` 帧 →
     * Kotlin 裸 TCP ×8 发送 → 秤端 ACK `W45:O01`。读时间回读验证一致。
     */
    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        bridgeMutex.withLock {
            runCatching {
                val frames = generateDateFramesViaBridge(includeDownload = true)
                val frame = frames.lastOrNull() ?: error("凯士同步帧生成失败")
                java.net.Socket().use { sock ->
                    sock.tcpNoDelay = true
                    sock.connect(java.net.InetSocketAddress(device.ip, device.port), 5000)
                    sock.soTimeout = RESPONSE_TIMEOUT_MS.toInt()
                    val out = sock.getOutputStream()
                    val inp = sock.getInputStream()
                    repeat(8) {
                        out.write(frame)
                        out.flush()
                        Thread.sleep(FRAME_GAP_MS)
                    }
                    val resp = readReplyText(inp, "W45")
                    if (!resp.contains("W45:O")) {
                        error("凯士时间同步未确认 (期望 W45:O): ${resp.take(80)}")
                    }
                }
            }
        }
    }

    /** 读取秤内时间：UL 帧 `R45F01,0001.` → 回包 `W45F01,001L042:` + `Y/M/D/h/m/s={X2}.`。 */
    override suspend fun readTime(): Result<java.time.LocalDateTime> = withContext(Dispatchers.IO) {
        bridgeMutex.withLock {
            runCatching {
                val frames = generateDateFramesViaBridge(includeDownload = false)
                val frame = frames.firstOrNull() ?: error("凯士读时间帧生成失败")
                java.net.Socket().use { sock ->
                    sock.tcpNoDelay = true
                    sock.connect(java.net.InetSocketAddress(device.ip, device.port), 5000)
                    sock.soTimeout = RESPONSE_TIMEOUT_MS.toInt()
                    val out = sock.getOutputStream()
                    val inp = sock.getInputStream()
                    repeat(8) {
                        out.write(frame)
                        out.flush()
                        Thread.sleep(FRAME_GAP_MS)
                    }
                    val resp = readReplyText(inp, "Y=")
                    parseClTime(resp) ?: error("凯士时间回包解析失败: ${resp.take(120)}")
                }
            }
        }
    }

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

        /** 帧生成进程超时：基础 + 每 PLU 追加（DLL 加载 + Add-Type 编译约 10s）。 */
        private const val FRAME_GEN_BASE_TIMEOUT_MS = 60_000L
        private const val FRAME_GEN_PER_PLU_TIMEOUT_MS = 3_000L

        /** 时间帧生成进程超时（握手等待 ~4s + DLL 加载）。 */
        private const val DATE_GEN_TIMEOUT_MS = 60_000L

        /** PLU 回收遍历上限（CL 族典型容量级）。 */
        private const val MAX_UPLOAD_PLU = 3000

        /** 连续空槽达到该数量即终止回收遍历。 */
        private const val EMPTY_RUN_STOP = 20

        /** `W02A` 回帧字段头最短长度（`F=01.57,02:`）。 */
        private const val FIELD_HEADER_MIN = 10

        /** `W02A` 回帧字段代码（与下发 PluSchema 对应）。 */
        private const val FIELD_DEPT = 0x01
        private const val FIELD_PLU_NO = 0x02
        private const val FIELD_PLU_TYPE = 0x04
        private const val FIELD_UNIT_PRICE = 0x06
        private const val FIELD_NAME = 0x0A
        private const val FIELD_ITEM_CODE = 0x0B
        private const val FIELD_SELL_BY_DATE = 0x10

        /** CL 协议 PLU 类型：1=称重、2=计数。 */
        private const val PLU_TYPE_COUNT = 2

        /** 品名 GBK 字符集。 */
        private val GBK: Charset = Charset.forName("GBK")

        /** 桥接进程传递凯士工具目录的环境变量名（路径可能含中文）。 */
        private const val CAS_TOOLS_ENV = "SCALEPLEX_CAS_TOOLS"

        /** 桥接进程传递数据目录的环境变量名（config 副本回退位置）。 */
        private const val DATA_DIR_ENV = "SCALEPLEX_DATA_DIR"

        /** Scales 为全局状态库，多设备并发探测会互相干扰，串行化。 */
        private val bridgeMutex = Mutex()
    }
}
