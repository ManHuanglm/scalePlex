package com.scaleplex.driver.impl.aclas

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 顶尖 Aclas 官方 AclasSDK.dll 桥接（E:\共享文件\电子秤软件\打印机\DLL69_Demo_CN220523 官方 Demo）：
 *
 * - `AclasSDK_Initialize/Finalize`：SDK 生命周期；
 * - `AclasSDK_GetDevicesInfo`：读取设备信息（ProtocolType/Port/Name/固件版本）——在线探测与协议自协商；
 * - `AclasSDK_Sync_ExecTaskA(host, port, protocol, procType, dataType, file)`：同步任务执行；
 * - `AclasSDK_GetLastTaskError`：任务错误码。
 *
 * ProcType：0=Down 下发 / 1=UP 回收 / 3=Del 删除；DataType：0=PLU / 1=Unit / 2=Department /
 * 3=HotKey / 4=Group / 0xA=Tare / 0xC..E=Note1-3 / 0x10=Options（见官方 Demo main.cs）。
 *
 * DLL 为 32 位原生（Delphi），64 位 JVM 无法进程内加载 → 32 位 PowerShell（SysWOW64）
 * + P/Invoke 桥接，进程工作目录 = 工具目录（SDK 日志 aclassdk_log 落在 DLL 同目录）。
 * 工具目录通过系统属性 `scaleplex.aclas.tools` 配置（设置页：顶尖工具目录）。
 */
object AclasBridge {

    private val log = LoggerFactory.getLogger(AclasBridge::class.java)

    const val TOOLS_PATH_PROPERTY = "scaleplex.aclas.tools"
    const val DLL_NAME = "AclasSDK.dll"

    // ProcType（官方 Demo main.cs）
    const val PROC_DOWN = 0
    const val PROC_UP = 1
    const val PROC_EDIT = 2
    const val PROC_DEL = 3
    const val PROC_LIST = 4
    const val PROC_EMPTY = 5

    // DataType（官方 Demo main.cs）
    const val DATA_PLU = 0x0000
    const val DATA_UNIT = 0x0001
    const val DATA_DEPARTMENT = 0x0002
    const val DATA_HOTKEY = 0x0003
    const val DATA_GROUP = 0x0004
    const val DATA_NOTE1 = 0x000C

    /** 任务/设备结果。 */
    data class DeviceInfo(
        val online: Boolean,
        val port: Int = 0,
        val protocolType: Int = 0,
        val name: String = "",
        val version: Int = 0,
        val firmware: String = "",
        val errorCode: Int = 0,
    )

    /** 一次任务执行结果。 */
    data class TaskResult(
        val online: Boolean,
        val ret: Int,
        val lastError: Int,
        val port: Int,
        val protocolType: Int,
        val deviceName: String,
    ) {
        val ok: Boolean get() = online && ret == 0 && lastError == 0
    }

    fun toolsPath(): String = System.getProperty(TOOLS_PATH_PROPERTY, "").ifBlank {
        val tools = System.getProperty("scaleplex.tools.dir", "")
        if (tools.isNotBlank()) File(tools, "aclas").absolutePath else ""
    }

    fun isConfigured(): Boolean = toolsPath().isNotBlank() && File(toolsPath(), DLL_NAME).exists()

    /**
     * 设备探测 + 任务执行（一步完成：GetDevicesInfo → Sync_ExecTaskA → GetLastTaskError）。
     * [procType]/[dataType] 见常量；[file] 为数据文件路径（Del 清空时传 "*"）。
     * 返回解析后的 JSON 行（单行）。
     */
    fun execTask(ip: String, procType: Int, dataType: Int, file: String, timeoutMs: Long = 300_000): TaskResult {
        val tools = toolsPath()
        if (tools.isBlank()) error("顶尖工具目录未配置（系统设置：AclasSDK.dll 所在目录）")
        if (!File(tools, DLL_NAME).exists()) error("AclasSDK.dll 不存在: $tools")

        val ps1 = File(System.getProperty("java.io.tmpdir"), "scaleplex-aclas-task.ps1")
        ps1.writeText(buildTaskScript(tools, ip, procType, dataType, file), Charsets.UTF_8)

        val ps = File("C:\\Windows\\SysWOW64\\WindowsPowerShell\\v1.0\\powershell.exe")
            .takeIf { it.exists() } ?: File("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        if (!ps.exists()) error("未找到 Windows PowerShell: ${ps.absolutePath}")

        val process = ProcessBuilder(
            ps.absolutePath, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath,
        ).directory(File(tools)).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("AclasSDK 任务超时 (${timeoutMs}ms)")
        }
        if (process.exitValue() != 0) {
            log.warn("ACLAS bridge exit={} output={}", process.exitValue(), output.take(400))
            error("AclasSDK 调用失败: ${output.take(300)}")
        }
        val json = output.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("{") }
            ?: error("AclasSDK 桥接无结果: ${output.take(200)}")
        log.info("ACLAS execTask ip={} proc={} data={} -> {}", ip, procType, dataType, json.take(200))
        return parseResult(json)
    }

    /** 解析桥接脚本输出的 JSON（键序固定，简单正则提取）。 */
    private fun parseResult(json: String): TaskResult {
        fun intOf(key: String) = Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.lastOrNull()?.toIntOrNull() ?: 0
        fun strOf(key: String) = Regex("\"$key\":\"([^\"]*)\"").find(json)?.groupValues?.lastOrNull() ?: ""
        val online = Regex("\"online\":(true|false)").find(json)?.groupValues?.lastOrNull() == "true"
        return TaskResult(
            online = online,
            ret = intOf("ret"),
            lastError = intOf("lastError"),
            port = intOf("port"),
            protocolType = intOf("protocol"),
            deviceName = strOf("name"),
        )
    }

    /** 设备探测（仅 GetDevicesInfo，用于在线状态）。 */
    fun probeDevice(ip: String): DeviceInfo {
        val r = execTask(ip, PROC_LIST, DATA_PLU, "*", timeoutMs = 20_000)
        return DeviceInfo(online = r.online, port = r.port, protocolType = r.protocolType, name = r.deviceName)
    }

    /** 生成桥接脚本：内嵌 C#（struct + P/Invoke），输出单行 JSON。 */
    private fun buildTaskScript(toolsDir: String, ip: String, procType: Int, dataType: Int, file: String): String {
        val dll = "$toolsDir\\$DLL_NAME"
        return """
|${'$'}ErrorActionPreference = 'Stop'
|Set-Location -LiteralPath '$toolsDir'
|Add-Type -TypeDefinition @"
|using System;
|using System.Runtime.InteropServices;
|public static class AclasApi {
|    [StructLayout(LayoutKind.Sequential, Pack = 1)]
|    public struct TASSDKDeviceInfo {
|        public UInt32 ProtocolType;
|        public UInt32 Addr;
|        public UInt32 Port;
|        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 16)] public byte[] Name;
|        public UInt32 ID;
|        public UInt32 Version;
|        public Byte Country;
|        public Byte DepartmentID;
|        public Byte KeyType;
|        public UInt64 PrinterDot;
|        public Int64 PrnStartDate;
|        public UInt32 LabelPage;
|        public UInt32 PrinterNo;
|        public UInt16 PLUStorage;
|        public UInt16 HotKeyCount;
|        public UInt16 NutritionStorage;
|        public UInt16 DiscountStorage;
|        public UInt16 Note1Storage;
|        public UInt16 Note2Storage;
|        public UInt16 Note3Storage;
|        public UInt16 Note4Storage;
|        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 12)] public byte[] FirmwareVersion;
|        public Byte DefaultProtocol;
|        public Byte LFCodeLen;
|        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 4)] public byte[] DeviceId;
|        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 4)] public byte[] StockId;
|        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 155)] public byte[] Adjunct;
|    }
|    [DllImport(@"$dll", CharSet = CharSet.Ansi)]
|    public static extern bool AclasSDK_Initialize(IntPtr Adjunct);
|    [DllImport(@"$dll")]
|    public static extern void AclasSDK_Finalize();
|    [DllImport(@"$dll", CharSet = CharSet.Ansi)]
|    public static extern bool AclasSDK_GetDevicesInfo(UInt32 Addr, UInt32 Port, UInt32 ProtocolType, ref TASSDKDeviceInfo info);
|    [DllImport(@"$dll", CharSet = CharSet.Ansi)]
|    public static extern int AclasSDK_Sync_ExecTaskA(string host, UInt32 port, UInt32 protocol, UInt32 procType, UInt32 dataType, string file);
|    [DllImport(@"$dll")]
|    public static extern int AclasSDK_GetLastTaskError();
|}
|"@
|function IpToDword([string]${'$'}s) {
|    ${'$'}p = ${'$'}s.Split('.')
|    if (${'$'}p.Length -ne 4) { return [uint32]0 }
|    return ([uint32]${'$'}p[0] -shl 24) + ([uint32]${'$'}p[1] -shl 16) + ([uint32]${'$'}p[2] -shl 8) + [uint32]${'$'}p[3]
|}
|${'$'}null = [AclasApi]::AclasSDK_Initialize([IntPtr]::Zero)
|try {
|    ${'$'}info = New-Object AclasApi+TASSDKDeviceInfo
|    ${'$'}addr = IpToDword '$ip'
|    ${'$'}online = [AclasApi]::AclasSDK_GetDevicesInfo(${'$'}addr, 0, 0, [ref]${'$'}info)
|    if (-not ${'$'}online) {
|        Write-Output '{"online":false}'
|        exit 0
|    }
|    ${'$'}name = [System.Text.Encoding]::ASCII.GetString(${'$'}info.Name).Trim([char]0).Trim()
|    ${'$'}fw = [System.Text.Encoding]::ASCII.GetString(${'$'}info.FirmwareVersion).Trim([char]0).Trim()
|    ${'$'}ret = [AclasApi]::AclasSDK_Sync_ExecTaskA('$ip', ${'$'}info.Port, ${'$'}info.ProtocolType, $procType, $dataType, '$file')
|    ${'$'}err = [AclasApi]::AclasSDK_GetLastTaskError()
|    Write-Output ('{{"online":true,"ret":{0},"lastError":{1},"port":{2},"protocol":{3},"name":"{4}","firmware":"{5}"}}' -f ${'$'}ret, ${'$'}err, ${'$'}info.Port, ${'$'}info.ProtocolType, ${'$'}name, ${'$'}fw)
|} finally {
|    [AclasApi]::AclasSDK_Finalize()
|}
|exit 0
""".trimMargin()
    }

    /** 任务结果错误描述（官方回调错误码 + GetLastTaskError）。 */
    fun describeResult(r: TaskResult): String = when {
        !r.online -> "秤不在线或网络不通"
        r.ret == 0 && r.lastError == 0 -> "成功"
        else -> "任务失败 (ret=${r.ret}, lastError=${r.lastError})"
    }
}
