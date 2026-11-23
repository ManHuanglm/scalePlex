package com.scaleplex.driver.impl.digi

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 寺冈官方 TWS.exe（TCPDriver）命令行桥接：
 * `TWS RDFL <文件号> <IP> <端口>` 读整文件（每行一条记录，输出 IP.文件号.DAT），
 * `TWS RDR <文件号> <记录号> <IP> <端口>` 读单条记录，
 * `TWS DELF <文件号> <IP> <端口>` 删除秤上文件。
 * 结果文件 `result` 每行 `IP:文件号:结果`（0=成功，负数=错误码，见 TCPDriver.doc 错误码表）。
 *
 * 注意：命令第 4 参数"端口"实际为**秤号**（TWS 协议 TCP 目标端口内部固定；LabelNet
 * 与秤端设置页均配置秤号，通常填秤 IP 后 3 位，默认 1）。
 *
 * 工具目录（含 TWS.exe/SMScale.dll/SM.ini/Desc）通过系统属性 `scaleplex.digi.tools` 配置。
 */
object DigiToolsBridge {

    private val log = LoggerFactory.getLogger(DigiToolsBridge::class.java)

    const val TOOLS_PATH_PROPERTY = "scaleplex.digi.tools"

    /** PLU 文件号：25H（见 PLU_FILE.TXT）。 */
    const val PLU_FILE_NO = 0x25

    fun toolsPath(): String = System.getProperty(TOOLS_PATH_PROPERTY, "")

    fun isConfigured(): Boolean = toolsPath().isNotBlank() && File(toolsPath(), "TWS.exe").exists()

    /** 错误码 → 可读描述（TCPDriver.doc 错误码表，顺序对应）。 */
    fun describeResult(code: Int): String = when (code) {
        0 -> "成功"
        -1 -> "打开文件错误"
        -2 -> "打开网络错误"
        -3 -> "机器上无记录"
        -4 -> "机器上无空间"
        -5 -> "机器未定义错误"
        -6 -> "文件数据错误"
        else -> "错误码 $code"
    }

    /**
     * 执行一条 TWS 命令。命令参数与 TCPDriver.doc 一致（RDFL/RDR/DELF/DELR/WT）。
     * 返回 (结果码列表, 下载的数据文件)。
     */
    fun runTws(vararg args: String, timeoutMs: Long = 60_000): Pair<List<Int>, File?> {
        val tools = toolsPath()
        if (tools.isBlank()) error("寺冈工具目录未配置（系统设置：寺冈 DLL/TWS 路径）")
        val tws = File(tools, "TWS.exe")
        if (!tws.exists()) error("TWS.exe 不存在: ${tws.absolutePath}")

        val workDir = File(tools)
        val before = workDir.listFiles { f -> f.name.endsWith(".DAT") }?.map { it.name }?.toSet() ?: emptySet()

        val process = ProcessBuilder(listOf(tws.absolutePath) + args.toList())
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(java.nio.charset.Charset.defaultCharset())
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("TWS 命令超时: ${args.joinToString(" ")}")
        }
        log.info("TWS {} -> exit={} output={}", args.joinToString(" "), process.exitValue(), output.take(300))

        // 解析 result 文件：IP:文件号:结果
        val resultFile = File(workDir, "result")
        val codes = if (resultFile.exists()) {
            resultFile.readLines(java.nio.charset.Charset.defaultCharset())
                .filter { it.isNotBlank() }
                .mapNotNull { line -> line.substringAfterLast(':').toIntOrNull() }
        } else emptyList()

        // 找出新生成的 .DAT 文件
        val datFile = workDir.listFiles { f -> f.name.endsWith(".DAT") && f.name !in before }
            ?.maxByOrNull { it.lastModified() }

        return codes to datFile
    }

    /** 在线探测：读 PLU 文件第 1 条记录；网络错误 = 离线，"无记录" 视为在线（秤上无数据）。 */
    fun checkOnline(ip: String, port: Int): Boolean {
        val (codes, _) = runTws("RDR", PLU_FILE_NO.toString(), "1", ip, port.toString(), timeoutMs = 15_000)
        if (codes.isEmpty()) return false
        // 任一 IP 对应码非网络类错误即视为在线（0=成功，-3=无记录，-1=文件错误）
        return codes.any { it == 0 || it == -1 || it == -3 }
    }

    /** 读取流水明细文件（文件号 0x51）并解析为交易记录。 */
    fun readReceiptDetail(ip: String, port: Int, deviceId: Long, pluNames: Map<Int, String> = emptyMap()) =
        runTws("RDFL", DigiReceiptParser.RECEIPT_DETAIL_FILE_NO.toString(), ip, port.toString(), timeoutMs = 120_000)
            .let { (codes, datFile) ->
                val ok = codes.any { it == 0 }
                if (!ok) {
                    // -3 = 机器上无记录 → 空流水
                    if (codes.all { it == -3 }) emptyList()
                    else error("读取流水失败: ${codes.joinToString(",") { describeResult(it) }}")
                } else {
                    if (datFile == null) emptyList()
                    else DigiReceiptParser.parseReceiptFile(datFile, deviceId, pluNames)
                }
            }

    /** 删除秤上指定文件（如 PLU 文件 25H = 清空 PLU）。 */
    fun deleteFile(fileNo: Int, ip: String, port: Int) {
        val (codes, _) = runTws("DELF", fileNo.toString(), ip, port.toString())
        if (codes.isNotEmpty() && codes.none { it == 0 }) {
            error("删除文件 $fileNo 失败: ${codes.joinToString(",") { describeResult(it) }}")
        }
    }

    /** 删除秤上指定文件的单条记录（`TWS DELR <文件号> <记录号> <IP> <端口>`）。 */
    fun deleteRecord(fileNo: Int, recordNo: Int, ip: String, port: Int) {
        val (codes, _) = runTws(
            "DELR", fileNo.toString(), recordNo.toString(), ip, port.toString(),
            timeoutMs = 30_000,
        )
        if (codes.isNotEmpty() && codes.none { it == 0 }) {
            error("删除记录 $recordNo 失败: ${codes.joinToString(",") { describeResult(it) }}")
        }
    }

    /**
     * PLU 下发：通过官方 SMScale.dll（`DownloadPLUtoScales(csvPath, ips)`，见《DLL 使用说明》），
     * 32 位 PowerShell 桥接调用；随后轮询 `GetScaleStatus()` 直至结果稳定，
     * 返回 "IP:文件号::结果码" 每行结果 → (ip → 结果码)。
     * 要求工作目录（寺冈工具目录）内含 SMScale.dll/SM.ini/Desc（见《DLL 使用说明》目录结构）。
     */
    fun downloadPluViaDll(csvFile: File, ips: List<String>, timeoutMs: Long = 180_000): Map<String, Int> {
        val tools = toolsPath()
        if (tools.isBlank()) error("寺冈工具目录未配置（系统设置：寺冈 DLL/TWS 路径）")
        val dll = File(tools, "SMScale.dll")
        if (!dll.exists()) error("SMScale.dll 不存在: ${dll.absolutePath}")
        if (!csvFile.exists()) error("PLU CSV 不存在: ${csvFile.absolutePath}")

        val ps1 = File(System.getProperty("java.io.tmpdir"), "scaleplex-digi-plu.ps1")
        ps1.writeText(buildPluBridgeScript(dll.absolutePath, csvFile.absolutePath, ips.joinToString(",")), Charsets.UTF_8)

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
            error("PLU 下发超时 (${timeoutMs}ms)")
        }
        if (process.exitValue() != 0) {
            log.warn("DIGI PLU bridge exit={} output={}", process.exitValue(), output.take(400))
            error("SMScale.dll 调用失败: ${output.take(300)}")
        }
        // 输出行格式：IP:文件号::结果码
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.contains("::") }
            .associate { line ->
                val ip = line.substringBefore(':')
                val code = line.substringAfterLast(':').toIntOrNull() ?: -99
                ip to code
            }
    }

    /** PLU 下发桥接脚本：Ansi/stdcall P/Invoke + 状态轮询（连续两次相同视为完成）。 */
    private fun buildPluBridgeScript(dllPath: String, csvPath: String, ips: String): String = """
        |${'$'}ErrorActionPreference = 'Stop'
        |Add-Type -TypeDefinition @"
        |using System;
        |using System.Runtime.InteropServices;
        |public static class DigiScaleApi {
        |    [DllImport(@"$dllPath", CharSet = CharSet.Ansi, CallingConvention = CallingConvention.StdCall)]
        |    public static extern int DownloadPLUtoScales(string csvFile, string scaleIps);
        |    [DllImport(@"$dllPath", CharSet = CharSet.Ansi, CallingConvention = CallingConvention.StdCall)]
        |    public static extern IntPtr GetScaleStatus();
        |}
        |"@
        |${'$'}null1 = [DigiScaleApi]::DownloadPLUtoScales('$csvPath', '$ips')
        |${'$'}deadline = (Get-Date).AddSeconds(120)
        |${'$'}last = ''
        |while ((Get-Date) -lt ${'$'}deadline) {
        |    Start-Sleep -Seconds 2
        |    ${'$'}ptr = [DigiScaleApi]::GetScaleStatus()
        |    ${'$'}status = [System.Runtime.InteropServices.Marshal]::PtrToStringAnsi(${'$'}ptr)
        |    if (${'$'}status) {
        |        Write-Output ${'$'}status
        |        if (${'$'}status -eq ${'$'}last) { break }
        |        ${'$'}last = ${'$'}status
        |    }
        |}
        |exit 0
    """.trimMargin()
}

