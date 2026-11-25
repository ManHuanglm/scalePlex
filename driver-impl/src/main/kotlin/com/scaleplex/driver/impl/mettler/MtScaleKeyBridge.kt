package com.scaleplex.driver.impl.mettler

import com.scaleplex.core.model.BatchError
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.ScaleDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * MTScaleKey 32 位原生 DLL 桥接器。
 *
 * MTScaleAPI.dll 为 32 位 VC++ 库（extern "C" __stdcall ExecuteTaskInFileW），64 位 JVM
 * 无法进程内加载——通过系统自带的 32 位 PowerShell（SysWOW64）以 P/Invoke 方式调用，
 * 任务以 UTF-8 XML 文件交互，执行完读取 TaskResult 文件解析结果。
 *
 * DLL 路径通过系统属性 `scaleplex.mt.dll` 配置（默认 C:\MTScaleKey\MTScaleAPI\MTScaleAPI.dll），
 * 该目录需包含 MTScaleKey 全套 DLL（无服务模式）或已安装 MTScaleKey 服务。
 */
object MtScaleKeyBridge {

    private val log = LoggerFactory.getLogger(MtScaleKeyBridge::class.java)

    const val DLL_PATH_PROPERTY = "scaleplex.mt.dll"
    const val DEFAULT_DLL_PATH = "C:\\MTScaleKey\\MTScaleAPI\\MTScaleAPI.dll"
    const val DLL_NAME = "MTScaleAPI.dll"

    /** 32 位 PowerShell（64 位 Windows 必有；32 位系统直接用系统 PowerShell）。 */
    private fun powershell32(): File {
        val wow = File("C:\\Windows\\SysWOW64\\WindowsPowerShell\\v1.0\\powershell.exe")
        return if (wow.exists()) wow else File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")
    }

    fun dllPath(): String {
        System.getProperty(DLL_PATH_PROPERTY, "").takeIf { it.isNotBlank() }?.let { return it }
        // 内置工具链兜底：<dataDir>/tools/mettler/MTScaleAPI.dll（见 Main.kt ToolsDeployer）
        val tools = System.getProperty("scaleplex.tools.dir", "")
        if (tools.isNotBlank()) {
            val deployed = File(tools, "mettler\\$DLL_NAME").takeIf { it.exists() }
            if (deployed != null) return deployed.absolutePath
        }
        return DEFAULT_DLL_PATH
    }

    /**
     * 以文件方式执行一次 MTScaleKey 任务。
     * [taskDir] 内应已生成 Task.xml（引用 DeviceList/Commands/Data），成功后返回结果文件。
     */
    suspend fun executeTask(taskDir: File, taskId: String, timeoutMs: Long = 120_000): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dll = dllPath()
                if (!File(dll).exists()) {
                    error("MTScaleAPI.dll 不存在: $dll （请在系统设置中配置 MTScaleKey DLL 路径）")
                }
                val taskFile = File(taskDir, "Task.xml")
                val resultFile = File(taskDir, "TaskResult.xml")
                if (!taskFile.exists()) error("Task.xml 不存在: ${taskFile.absolutePath}")

                val ps1 = File(taskDir, "bridge.ps1")
                ps1.writeText(buildBridgeScript(dll, taskId, taskFile.absolutePath, resultFile.absolutePath), Charsets.UTF_8)

                val ps = powershell32()
                if (!ps.exists()) error("未找到 Windows PowerShell: ${ps.absolutePath}")

                val process = ProcessBuilder(
                    ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
                )
                    // 工作目录切到 DLL 所在目录：MTScaleAPI.dll 的依赖（MTScaleKeyData/MTConfiguration 等）
                    // 与 MTScaleKey.config 都在其旁，Windows 加载器按 CWD 搜索依赖
                    .directory(File(dll).parentFile)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.readBytes().toString(java.nio.charset.Charset.defaultCharset())
                val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    error("MTScaleKey 任务执行超时 (${timeoutMs}ms)")
                }
                val exit = process.exitValue()
                if (exit != 0) {
                    log.warn("MT bridge exit={} output={}", exit, output.take(500))
                    error("MTScaleKey 桥接调用失败 (exit=$exit): ${output.take(300)}")
                }
                if (!resultFile.exists()) {
                    error("任务未产生结果文件: ${resultFile.absolutePath}；请确认 MTScaleKey 已安装并配置秤列表")
                }
                resultFile
            }
        }

    /** 将 TaskResult 解析为按设备汇总的 [BatchResult]。 */
    fun parseResult(resultFile: File, devices: List<ScaleDevice>, total: Int): Map<Long, BatchResult> {
        val xml = resultFile.readText(StandardCharsets.UTF_8)
        val scaleResults = MtScaleKeyXml.parseTaskResultXml(xml)
        return devices.associate { device ->
            val sr = scaleResults[device.id]
            val result = if (sr == null) {
                BatchResult(total = total, failed = total,
                    errors = listOf(BatchError(device.id, null, "任务结果中缺少该秤的执行记录")))
            } else if (sr.ok) {
                BatchResult.ok(total)
            } else {
                BatchResult(
                    total = total,
                    success = 0,
                    failed = total,
                    errors = buildList {
                        add(BatchError(device.id, null, "${sr.returnCode} / ${sr.status}"))
                        sr.failedCommandIds.forEach { add(BatchError(device.id, null, "命令失败: $it")) }
                    },
                )
            }
            device.id to result
        }
    }

    /** 生成的桥接脚本：P/Invoke 调用 ExecuteTaskInFileW（Unicode/stdcall）。 */
    private fun buildBridgeScript(dllPath: String, taskId: String, inputFile: String, outputFile: String): String = """
        |${'$'}ErrorActionPreference = 'Stop'
        |Add-Type -TypeDefinition @"
        |using System;
        |using System.Runtime.InteropServices;
        |public static class MTScaleKeyApi {
        |    [DllImport(@"$dllPath", CharSet = CharSet.Unicode, CallingConvention = CallingConvention.StdCall)]
        |    public static extern bool ExecuteTaskInFileW(string taskId, string inputFile, string outputFile, bool synch);
        |    [DllImport(@"$dllPath", CharSet = CharSet.Unicode, CallingConvention = CallingConvention.StdCall)]
        |    public static extern void Release();
        |}
        |"@
        |${'$'}ok = [MTScaleKeyApi]::ExecuteTaskInFileW('$taskId', '$inputFile', '$outputFile', ${'$'}true)
        |[MTScaleKeyApi]::Release()
        |Write-Output ('EXEC=' + ${'$'}ok)
        |if (-not ${'$'}ok) { exit 2 }
        |exit 0
    """.trimMargin()
}
