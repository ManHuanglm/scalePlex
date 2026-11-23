package com.scaleplex.driver.impl.dahua

import com.scaleplex.core.model.TradeRecord
import kotlinx.datetime.LocalDateTime
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 大华官方 DhScalePluNet.dll 桥接（information/大华秤接口，见《DhScalePluNet.dll 说明》/《大华通讯 DLL 库说明》）：
 *
 * - `int dhSendPluDefault()`：读取 DLL 同目录 3 个 ini（dhip.ini=IP+程序编号、
 *   dhplupathname.ini=`_PLU_PATH_` PLU 源文件路径、dhpluversion.ini=PLU_VERSION），
 *   向目标秤发送 PLU 源文件，产生 Return.txt（单台出错信息）与 Rec.txt（秤返回信息）；
 * - `int dhRcvRdDefault()`：读 dhip.ini 各 IP 回收交易流水，结果写 record.txt。
 *
 * DLL 为 32 位 WINAPI（__stdcall），64 位 JVM 无法进程内加载，采用 32 位 PowerShell
 * （SysWOW64）+ P/Invoke 桥接执行，工作目录 = DLL 目录（ini 为相对路径读取）。
 * 工具目录通过系统属性 `scaleplex.dahua.tools` 配置（设置页：大华工具目录）。
 */
object DahuaBridge {

    private val log = LoggerFactory.getLogger(DahuaBridge::class.java)

    const val TOOLS_PATH_PROPERTY = "scaleplex.dahua.tools"
    const val DLL_NAME = "DhScalePluNet.dll"

    /** dhSendPluDefault 返回码（《DhScalePluNet.dll 说明》）。 */
    fun describeSendCode(code: Int): String = when (code) {
        0 -> "成功"
        -1 -> "dhip.ini 为空或设置错误"
        -2 -> "dhpluversion.ini 为空或设置错误"
        -3 -> "dhplupathname.ini 为空或设置错误"
        -4 -> "PLU 文件路径不存在"
        -5 -> "PLU 文本为空或与 PLU 版本不一致"
        -6 -> "通信存在异常（详见 Return.txt）"
        -7 -> "目标 IP 为空"
        -9 -> "通讯未完全成功（部分 IP 失败，详见 Return.txt）"
        else -> "错误码 $code"
    }

    /** Return.txt 内层错误码。 */
    fun describeReturnCode(code: Int): String = when (code) {
        0 -> "成功"
        -1 -> "连接失败"
        -2 -> "未收到秤返回信息（秤型号不对或无响应）"
        -3 -> "读 SOCKET 缓冲区失败"
        -4 -> "IP 地址转换失败"
        -10 -> "未收到正确的结束标志(0D 0A 03)，已重试 3 次"
        else -> "错误码 $code"
    }

    fun toolsPath(): String = System.getProperty(TOOLS_PATH_PROPERTY, "").ifBlank {
        val tools = System.getProperty("scaleplex.tools.dir", "")
        if (tools.isNotBlank()) File(tools, "dahua").absolutePath else ""
    }

    fun isConfigured(): Boolean = toolsPath().isNotBlank() && File(toolsPath(), DLL_NAME).exists()

    /**
     * PLU 下发：生成 PLU 明文文本 → 写 3 个 ini → 桥接调用 dhSendPluDefault() → 解析 Return.txt。
     * 返回 (发送返回码, Return.txt 内层码，null=无 Return.txt)。
     */
    fun downloadPlu(items: List<com.scaleplex.core.model.PluItem>, ip: String, version: DahuaPluTextCodec.PluVersion): Pair<Int, Int?> {
        val tools = toolsPath()
        if (tools.isBlank()) error("大华工具目录未配置（系统设置：DhScalePluNet.dll 所在目录）")
        val dll = File(tools, DLL_NAME)
        if (!dll.exists()) error("DhScalePluNet.dll 不存在: ${dll.absolutePath}")

        val pluFile = File(System.getProperty("java.io.tmpdir"), "scaleplex-dahua-plu-${System.currentTimeMillis()}.txt")
        pluFile.writeBytes(DahuaPluTextCodec.encodeFile(items, version).toByteArray(charset("GBK")))
        try {
            writeIni(File(tools, "dhip.ini"), listOf("$ip\t${version.id}"))
            writeIni(File(tools, "dhpluversion.ini"), listOf("${DahuaPluTextCodec.VERSION_KEY}\t${version.id}"))
            writeIni(File(tools, "dhplupathname.ini"), listOf("${DahuaPluTextCodec.PATH_KEY}\t${pluFile.absolutePath}"))

            val sendCode = runDll("dhSendPluDefault")
            log.info("大华 PLU 下发 ip={} version={} code={} ({})", ip, version.id, sendCode, describeSendCode(sendCode))
            val returnCode = readReturnTxt(File(tools, "Return.txt"), ip)
            return sendCode to returnCode
        } finally {
            pluFile.delete()
        }
    }

    /** 交易流水回收：dhRcvRdDefault() → 解析 record.txt。 */
    fun readTradeLog(ip: String, deviceId: Long): List<TradeRecord> {
        val tools = toolsPath()
        if (tools.isBlank()) error("大华工具目录未配置（系统设置：DhScalePluNet.dll 所在目录）")
        if (!File(tools, DLL_NAME).exists()) error("DhScalePluNet.dll 不存在: $tools")

        writeIni(File(tools, "dhip.ini"), listOf("$ip\t3"))
        val code = runDll("dhRcvRdDefault")
        log.info("大华流水回收 ip={} code={}", ip, code)
        if (code == -5) return emptyList() // 数据内容为空
        if (code != 0 && code != -9) {
            error("流水回收失败: ${describeSendCode(code)}")
        }
        val recordFile = File(tools, "record.txt")
        if (!recordFile.exists()) return emptyList()
        return parseRecordTxt(recordFile.readBytes(), deviceId)
    }

    /** 写 ini：每行 `关键字<Tab>值`（与官方样例一致，DLL 按关键字扫描）。 */
    private fun writeIni(file: File, lines: List<String>) {
        file.writeText(lines.joinToString("\r\n") + "\r\n", charset("GBK"))
    }

    /**
     * 32 位 PowerShell 桥接调用 DLL 导出函数（WINAPI/__stdcall，无参数）。
     * 进程工作目录设为 DLL 目录（ini/Return.txt/record.txt 按相对路径读写）。
     */
    private fun runDll(functionName: String, timeoutMs: Long = 300_000): Int {
        val tools = toolsPath()
        val ps1 = File(System.getProperty("java.io.tmpdir"), "scaleplex-dahua-$functionName.ps1")
        ps1.writeText(buildBridgeScript(tools, functionName), Charsets.UTF_8)

        val ps = File("C:\\Windows\\SysWOW64\\WindowsPowerShell\\v1.0\\powershell.exe")
            .takeIf { it.exists() } ?: File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        if (!ps.exists()) error("未找到 Windows PowerShell: ${ps.absolutePath}")

        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        ).directory(File(tools)).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(java.nio.charset.Charset.defaultCharset())
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("DhScalePluNet.$functionName 调用超时 (${timeoutMs}ms)")
        }
        val code = output.lineSequence()
            .map { it.trim() }
            .mapNotNull { it.toIntOrNull() }
            .firstOrNull()
        if (code == null) {
            log.warn("大华 DLL 桥接无返回码 exit={} output={}", process.exitValue(), output.take(400))
            error("DhScalePluNet.$functionName 未返回结果码: ${output.take(200)}")
        }
        return code
    }

    private fun buildBridgeScript(toolsDir: String, functionName: String): String = """
        |${'$'}ErrorActionPreference = 'Stop'
        |Set-Location -LiteralPath '$toolsDir'
        |Add-Type -TypeDefinition @"
        |using System;
        |using System.Runtime.InteropServices;
        |public static class DahuaScaleApi {
        |    [DllImport(@"$toolsDir\$DLL_NAME", CallingConvention = CallingConvention.StdCall)]
        |    public static extern int $functionName();
        |}
        |"@
        |Write-Output [DahuaScaleApi]::$functionName()
        |exit 0
    """.trimMargin()

    /** 读取 Return.txt：每行 `IP<Tab>码`，返回目标 IP 的内层码（无文件/无行=null）。 */
    fun readReturnTxt(file: File, ip: String): Int? {
        if (!file.exists()) return null
        return file.readLines(charset("GBK"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull { it.startsWith(ip) }
            ?.substringAfterLast('\t')?.trim()?.toIntOrNull()
    }

    /**
     * 解析 record.txt（GBK，每行一条流水，Tab/多空白分隔，见官方样例 record.txt）：
     * `IP 时间(yyMMddHHmm) 收款机号 部门号 PLU 重量(kg) 单价 总金额 实收金额`
     */
    fun parseRecordTxt(bytes: ByteArray, deviceId: Long): List<TradeRecord> {
        val text = String(bytes, charset("GBK"))
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseRecordLine(it, deviceId) }
            .toList()
    }

    private fun parseRecordLine(line: String, deviceId: Long): TradeRecord? {
        val cols = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (cols.size < 9) return null
        val time = parseTime(cols[1]) ?: return null
        val pluNo = cols[4].toIntOrNull() ?: return null
        // 重量 kg（3 位小数）→ 克；价格 元（2 位小数）→ minor 分
        val weight = ((cols[5].toDoubleOrNull() ?: 0.0) * 1000).toInt()
        val unitPrice = decimalToMinor(cols[6])
        val amount = decimalToMinor(cols[7])
        return TradeRecord(
            deviceId = deviceId,
            recordNo = "20${cols[1]}".toLongOrNull() ?: 0L, // 时间串补世纪前缀作流水号（record.txt 无独立流水号列）
            timestamp = time,
            pluNo = pluNo,
            pluName = "",
            weight = weight,
            unitPrice = unitPrice,
            amount = amount,
            operator = cols[3], // 部门号暂存 operator
        )
    }

    private fun decimalToMinor(v: String): Long = Math.round((v.toDoubleOrNull() ?: 0.0) * 100)

    /** yyMMddHHmm（10 位）→ LocalDateTime。 */
    private fun parseTime(value: String): LocalDateTime? = runCatching {
        if (value.length < 10) return@runCatching null
        val yy = value.substring(0, 2).toInt()
        val year = if (yy >= 70) 1900 + yy else 2000 + yy
        LocalDateTime(
            year,
            value.substring(2, 4).toInt(),
            value.substring(4, 6).toInt(),
            value.substring(6, 8).toInt(),
            value.substring(8, 10).toInt(),
            0,
        )
    }.getOrNull()
}
