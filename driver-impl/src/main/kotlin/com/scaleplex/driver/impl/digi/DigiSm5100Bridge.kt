package com.scaleplex.driver.impl.digi

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 寺冈 SM-5100 系（b 族：SM-110N STE57+ / SM-120 / SM-5100）官方 SMScaleAPI.dll 桥接
 * （information/寺冈开发包(接口)/SM5100DLL&Demo）：
 *
 * - `ConnectScale(char* ip, int port)`：端口 = **2000 + IP 最后一段**（SMScaleAPI.xls 权威定义，
 *   例 192.168.0.55 → 2055），返回 SOCKET 句柄，≤0 = 连接失败（兼在线探测）；
 * - `SendLPlu(s, LabelPLU*)`：逐条下发（标签式 PLU，[DigiSm5100PluCodec] 组装）；
 * - `RDPluFAsLabel(s, RecordNo, LabelPLU*)`：按记录号逐条回收；
 * - `DelPluRecord(s, RecordNo)` / `DelPluFile(s)`：单条删除 / 清空全部 PLU；
 * - `SyncDateTime(s)`：时间同步；`ReadReceiptDetail(s, RecordNo, Receipt_D*)`：流水（暂未接入）。
 * - `CloseConnect(s)`：关闭连接。
 *
 * 返回值 0=无错误，-1=错误（SMScaleAPI.xls）。DLL 为 32 位原生，64 位 JVM 无法进程内加载
 * → 32 位 PowerShell（SysWOW64）+ P/Invoke 桥接，PLU 结构字节由 Kotlin 侧组装，
 * PS 仅传递 byte[]。工具目录 = 系统属性 `scaleplex.digi.tools`（含 SM5100DLL 目录下的 SMScaleAPI.dll）。
 */
object DigiSm5100Bridge {

    private val log = LoggerFactory.getLogger(DigiSm5100Bridge::class.java)

    const val DLL_NAME = "SMScaleAPI.dll"

    /** SMScaleAPI.dll 相对工具目录的子目录（SM5100DLL&Demo 的 DLL 在其根目录）。 */
    const val DLL_SUBDIR = "SM5100DLL&Demo"

    /** 会话执行模式。 */
    enum class Mode { DOWN, UP, DEL_RECORD, DEL_ALL, TIME, PROBE }

    /** 会话结果。 */
    data class SessionResult(
        val connected: Boolean,
        val ok: Boolean,
        val detail: String,
        /** 回收的 PLU 数量（UP 模式）。 */
        val recordCount: Int = 0,
    )

    fun toolsPath(): String = System.getProperty("scaleplex.digi.tools", "").ifBlank {
        val tools = System.getProperty("scaleplex.tools.dir", "")
        if (tools.isNotBlank()) File(tools, "digi").absolutePath else ""
    }

    fun dllPath(): File? {
        val tools = toolsPath()
        if (tools.isBlank()) return null
        // SM5100DLL&Demo 目录优先（用户自备工具目录），其次工具目录根（内置释放布局）
        val sub = File(tools, DLL_SUBDIR)
        val inSub = File(sub, DLL_NAME)
        return when {
            inSub.exists() -> inSub
            File(tools, DLL_NAME).exists() -> File(tools, DLL_NAME)
            else -> null
        }
    }

    fun isConfigured(): Boolean = dllPath() != null

    /** 寺冈 b 族通讯端口：2000 + IP 最后一段（SMScaleAPI.xls）。 */
    fun portOf(ip: String): Int = ip.trim().substringAfterLast('.').toIntOrNull()?.let { 2000 + it }
        ?: error("无法解析秤 IP 最后一段: $ip")

    /**
     * 执行一个连接会话。
     * - DOWN：[dataFile] 为连续 N×[DigiSm5100PluCodec.STRUCT_SIZE] 字节的 PLU 结构流，逐条下发；
     * - UP：[dataFile] 为输出文件，逐条 RDPluFAsLabel 回收直至失败；
     * - DEL_RECORD：[recordNo] 指定记录号；DEL_ALL/TIME/PROBE 无需数据。
     */
    fun execute(ip: String, mode: Mode, dataFile: File? = null, recordNo: Int = 0, timeoutMs: Long = 300_000): SessionResult {
        val dll = dllPath() ?: error("SM5100 SMScaleAPI.dll 未找到（系统设置：寺冈工具目录下 ${DLL_SUBDIR}\\$DLL_NAME）")
        val outPath = dataFile?.absolutePath ?: ""
        val ps1 = File(System.getProperty("java.io.tmpdir"), "scaleplex-sm5100-${mode.name.lowercase()}.ps1")
        ps1.writeText(buildScript(dll.absolutePath, ip, mode, outPath, recordNo), Charsets.UTF_8)

        val ps = File("C:\\Windows\\SysWOW64\\WindowsPowerShell\\v1.0\\powershell.exe")
            .takeIf { it.exists() } ?: File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        if (!ps.exists()) error("未找到 Windows PowerShell: ${ps.absolutePath}")

        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        ).directory(File(toolsPath().ifBlank { "." })).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("SMScaleAPI 会话超时 (${timeoutMs}ms)")
        }
        if (process.exitValue() != 0) {
            log.warn("SM5100 bridge exit={} output={}", process.exitValue(), output.take(400))
            error("SMScaleAPI 调用失败: ${output.take(300)}")
        }
        val json = output.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("{") }
            ?: error("SMScaleAPI 桥接无结果: ${output.take(200)}")
        return parseResult(json)
    }

    private fun parseResult(json: String): SessionResult {
        fun intOf(key: String) = Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.lastOrNull()?.toIntOrNull() ?: 0
        fun strOf(key: String) = Regex("\"$key\":\"([^\"]*)\"").find(json)?.groupValues?.lastOrNull() ?: ""
        val connected = Regex("\"connected\":(true|false)").find(json)?.groupValues?.lastOrNull() == "true"
        return SessionResult(
            connected = connected,
            ok = intOf("ret") == 0,
            detail = strOf("detail"),
            recordCount = intOf("count"),
        )
    }

    /**
     * 生成桥接脚本。socket 句柄 IntPtr；PLU 缓冲 byte[]（5284B）由 P/Invoke 原样传递。
     * DOWN：读 PLU 流文件循环 SendLPlu；UP：循环 RDPluFAsLabel 写输出文件。
     */
    private fun buildScript(dllPath: String, ip: String, mode: Mode, dataPath: String, recordNo: Int): String {
        val port = portOf(ip)
        val modeInit = when (mode) {
            Mode.DOWN -> """
|${'$'}bytes = [System.IO.File]::ReadAllBytes('$dataPath')
|${'$'}size = ${DigiSm5100PluCodec.STRUCT_SIZE}
|if ((${'$'}bytes.Length % ${'$'}size) -ne 0) { throw "PLU 流文件大小 (${'$'}(${'$'}bytes.Length)) 不是 ${'$'}size 的整数倍" }
|${'$'}count = ${'$'}bytes.Length / ${'$'}size
|${'$'}sent = 0
|for (${'$'}i = 0; ${'$'}i -lt ${'$'}count; ${'$'}i++) {
|    ${'$'}chunk = New-Object byte[] ${'$'}size
|    [Array]::Copy(${'$'}bytes, (${'$'}i * ${'$'}size), ${'$'}chunk, 0, ${'$'}size)
|    ${'$'}r = [SmApi]::SendLPlu(${'$'}s, ${'$'}chunk)
|    if (${'$'}r -ne 0) { throw "SendLPlu 第 $(${'$'}i+1) 条失败 ret=$({'$'}r)" }
|    ${'$'}sent++
|}
|Write-Output ('{{"connected":true,"ret":0,"count":' + ${'$'}sent + ',"detail":"down"}}')
"""
            Mode.UP -> """
|${'$'}fs = [System.IO.File]::Create('$dataPath')
|${'$'}bw = New-Object System.IO.BinaryWriter(${'$'}fs)
|${'$'}size = ${DigiSm5100PluCodec.STRUCT_SIZE}
|${'$'}buf = New-Object byte[] ${'$'}size
|${'$'}n = 0
|${'$'}recNo = 1
|while (${'$'}true) {
|    [Array]::Clear(${'$'}buf, 0, ${'$'}size)
|    ${'$'}r = [SmApi]::RDPluFAsLabel(${'$'}s, ${'$'}recNo, ${'$'}buf)
|    if (${'$'}r -ne 0) { break }
|    ${'$'}bw.Write(${'$'}buf)
|    ${'$'}n++
|    ${'$'}recNo++
|    if (${'$'}n -ge 4000) { break }
|}
|${'$'}bw.Flush(); ${'$'}bw.Close(); ${'$'}fs.Close()
|Write-Output ('{{"connected":true,"ret":0,"count":' + ${'$'}n + ',"detail":"up"}}')
"""
            Mode.DEL_RECORD -> """
|${'$'}r = [SmApi]::DelPluRecord(${'$'}s, $recordNo)
|if (${'$'}r -ne 0) { throw "DelPluRecord($recordNo) 失败 ret=$({'$'}r)" }
|Write-Output '{"connected":true,"ret":0,"detail":"delRecord"}'
"""
            Mode.DEL_ALL -> """
|${'$'}r = [SmApi]::DelPluFile(${'$'}s)
|if (${'$'}r -ne 0) { throw "DelPluFile 失败 ret=$({'$'}r)" }
|Write-Output '{"connected":true,"ret":0,"detail":"delAll"}'
"""
            Mode.TIME -> """
|${'$'}r = [SmApi]::SyncDateTime(${'$'}s)
|if (${'$'}r -ne 0) { throw "SyncDateTime 失败 ret=$({'$'}r)" }
|Write-Output '{"connected":true,"ret":0,"detail":"time"}'
"""
            Mode.PROBE -> """
|if (${'$'}s -gt 0) { Write-Output '{"connected":true,"ret":0,"detail":"probe"}' } else { Write-Output '{"connected":false,"ret":-1,"detail":"connect failed"}' }
"""
        }
        return """
|${'$'}ErrorActionPreference = 'Stop'
|Add-Type -TypeDefinition @"
|using System;
|using System.Runtime.InteropServices;
|public static class SmApi {
|    [DllImport(@"$dllPath", CharSet = CharSet.Ansi)]
|    public static extern IntPtr ConnectScale(string ip, int port);
|    [DllImport(@"$dllPath")]
|    public static extern int CloseConnect(IntPtr s);
|    [DllImport(@"$dllPath")]
|    public static extern int SendLPlu(IntPtr s, byte[] plu);
|    [DllImport(@"$dllPath")]
|    public static extern int RDPluFAsLabel(IntPtr s, int recordNo, byte[] plu);
|    [DllImport(@"$dllPath")]
|    public static extern int DelPluRecord(IntPtr s, int recordNo);
|    [DllImport(@"$dllPath")]
|    public static extern int DelPluFile(IntPtr s);
|    [DllImport(@"$dllPath")]
|    public static extern int SyncDateTime(IntPtr s);
|}
|"@
|${'$'}s = [SmApi]::ConnectScale('$ip', $port)
|try {
$modeInit|} finally {
|    if (${'$'}s -ne [IntPtr]::Zero -and ${'$'}s.ToInt64() -gt 0) { ${'$'}null = [SmApi]::CloseConnect(${'$'}s) }
|}
|exit 0
""".trimMargin()
    }
}
