package com.scaleplex.driver.impl.yousheng

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.Transport
import com.scaleplex.communication.TransportFactory
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 友声 YouSheng 条码秤驱动（真机校准 2026-11-27 @192.168.3.189:33571）。
 *
 * 全部功能经官方 32 位 JHScale.dll（TXT-MODE 二次开发库）桥接：内置
 * [YouShengBridge.exe](C# P/Invoke，CWD=工具目录)，命令行 + NDJSON 文件交互，退出码 0 = 成功：
 * - `probe`：快速在线探测（JH_Connect 短超时 + IsConnected）；
 * - `gettime` / `settime`：时间读写（真机验证；GetTime 返回码不可靠，返回 0 时间也对）；
 * - `setplu`：PLU 批量下发（SetFirstPLU + SetNextPLU 缓存 + SetPLU 提交，真机验证）；
 * - `getplu`：PLU 回收（GetPLU(0) + GetFirstPLU + GetNextPLU；实测本秤固件上传类命令
 *   未开放，恒返回空——保留官方路径，待秤端确认"上传商品资料"模式）；
 * - `clearplu [id]`：PLU 删除/清空（id=0 或缺省清空全部，未真机验证）；
 * - `getreport` / `clearreport`：流水回收/清空（同属上传类，未真机验证）。
 *
 * 实秤 TCP 通讯口 = 33571（全端口扫描 + JH_Connect 连通验证标定；DLL 内置默认 33581，
 * 33582 = PC 监听等秤主动连接口，33583/33584 = UDP 搜索广播口）。
 * 旧自研帧协议（YouShengProtocol）未获真机应答，已废弃删除。
 */
class YouShengDriver(
    private val device: ScaleDevice,
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.YOUSHENG

    override val transport: Transport =
        TransportFactory.create(device, CommEventBus())

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            execBridge(listOf(CMD_PROBE, target()), PROBE_TIMEOUT_MS)
            ScaleStatus(online = true, model = device.model)
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> =
        downloadPluBatch(listOf(plu)).map { }

    /**
     * PLU 批量下发：NDJSON 临时文件一次提交（SetFirstPLU → SetNextPLU×N → SetPLU）。
     * 字段映射：ID=pluNo、Text1=品名(GBK)、Price=分→元、Class=部门；Unit/Label1/PS_SD
     * 由桥接侧取默认值（1）。
     */
    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            val inFile = tempFile("yousheng-plu-in")
            inFile.writeText(list.joinToString("\n") { pluJson(it) }, Charsets.UTF_8)
            execBridge(listOf(CMD_SET_PLU, target(), inFile.absolutePath), DOWNLOAD_TIMEOUT_MS)
            BatchResult.ok(list.size)
        }
    }

    /** PLU 回收：官方 getplu 路径；固件未开放上传时返回空列表（桥接 exit=0）。 */
    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = tempFile("yousheng-plu-out")
            execBridge(listOf(CMD_GET_PLU, target(), outFile.absolutePath), UPLOAD_TIMEOUT_MS)
            outFile.readText(Charsets.UTF_8).lineSequence().mapNotNull(::parsePluJson).toList()
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            execBridge(listOf(CMD_CLEAR_PLU, target(), pluNo.toString()), PROBE_TIMEOUT_MS)
            Unit
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            execBridge(listOf(CMD_CLEAR_PLU, target()), PROBE_TIMEOUT_MS)
            Unit
        }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 实测 settime 全程 ~17s（DLL 内部等秤端 ACK 重试），进程超时单独放宽
            execBridge(listOf(CMD_SET_TIME, target()), SET_TIME_TIMEOUT_MS)
            Unit
        }
    }

    /** 读秤内时间：解析桥接输出 `time=yyyy-MM-dd HH:mm:ss` 行。 */
    override suspend fun readTime(): Result<java.time.LocalDateTime> = withContext(Dispatchers.IO) {
        runCatching {
            val output = execBridge(listOf(CMD_GET_TIME, target()), PROBE_TIMEOUT_MS)
            val m = TIME_REGEX.find(output) ?: error("友声读时间失败：输出未含时间行\n${output.take(200)}")
            val (y, mo, d, h, mi, s) = m.destructured
            java.time.LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = tempFile("yousheng-report-out")
            execBridge(listOf(CMD_GET_REPORT, target(), outFile.absolutePath), UPLOAD_TIMEOUT_MS)
            outFile.readText(Charsets.UTF_8).lineSequence().mapNotNull(::parseReportJson).toList()
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            execBridge(listOf(CMD_CLEAR_REPORT, target()), PROBE_TIMEOUT_MS)
            Unit
        }
    }

    // ---- 桥接交互 ----

    /** ip:port 目标串（port 未配置时回退品牌默认端口 33571）。 */
    private fun target(): String = device.ip + ":" + effectivePort()

    private fun effectivePort(): Int = device.port.takeIf { it > 0 } ?: ScaleBrand.YOUSHENG.defaultPort

    /**
     * 运行桥接命令（CWD = 工具目录，JHScale.dll 依赖工作目录定位自身与 ini）。
     * exit != 0 抛异常，消息取输出末行（RESULT=... / 具体错误）。
     */
    private fun execBridge(args: List<String>, timeoutMs: Long): String {
        val toolsDir = locateToolsDir() ?: error(
            "友声工具目录未找到（需 JHScale.dll + YouShengBridge.exe，" +
                "可用系统属性 $TOOLS_PATH_PROPERTY 指定）",
        )
        val proc = ProcessBuilder(listOf(File(toolsDir, BRIDGE_EXE).absolutePath) + args)
            .directory(toolsDir)
            .redirectErrorStream(true)
            .start()
        if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            proc.destroyForcibly()
            error("友声桥接超时 (${timeoutMs}ms): ${args.firstOrNull()}")
        }
        val output = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        val exit = proc.exitValue()
        log.info("YouSheng bridge {}: exit={} {}", args.take(2), exit, output.take(200))
        check(exit == 0) {
            "友声桥接执行失败 (exit=$exit): " +
                (output.lineSequence().lastOrNull { it.isNotBlank() } ?: output.take(120))
        }
        return output
    }

    /** 工具目录：系统属性 scaleplex.yousheng.tools → data/tools/yousheng（ToolsDeployer 释放）→ 原厂目录。 */
    private fun locateToolsDir(): File? {
        System.getProperty(TOOLS_PATH_PROPERTY, "").takeIf { it.isNotBlank() }
            ?.let { return File(it) }
        val dataDir = System.getProperty(DATA_DIR_PROPERTY, "").takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".scaleplex")
        val deployed = File(File(dataDir), "tools${File.separator}yousheng")
        if (File(deployed, BRIDGE_EXE).exists()) return deployed
        return DEFAULT_TOOLS_DIR.takeIf { File(it, BRIDGE_EXE).exists() }?.let(::File)
    }

    private fun tempFile(suffix: String): File = File.createTempFile("scaleplex-$suffix", ".ndjson").apply {
        deleteOnExit()
    }

    // ---- NDJSON 编解码（与 YouShengBridge.cs 的 ParsePlu / PluJson 对应） ----

    /** PluItem → 桥接 setplu 输入行；价格为分 → 元两位小数。 */
    internal fun pluJson(plu: PluItem): String {
        val price = java.math.BigDecimal(plu.unitPrice).movePointLeft(2).toPlainString()
        return buildString {
            append("{\"ID\": ").append(plu.pluNo)
            append(", \"Price\": ").append(price)
            append(", \"Class\": ").append(plu.mainGroup)
            append(", \"Unit\": ").append(if (plu.weightFlag == 1) COUNT_UNIT else WEIGH_UNIT)
            append(", \"Text1\": \"").append(escape(plu.displayName)).append("\"")
            append("}")
        }
    }

    /** 桥接 getplu 输出行 → PluItem（Price 元 → 分取整；固件不回条码/保质期，留空）。 */
    internal fun parsePluJson(line: String): PluItem? {
        if (!line.contains("\"ID\"")) return null
        val id = numField(line, "ID")?.toInt() ?: return null
        val price = numField(line, "Price")?.toDoubleOrNull() ?: 0.0
        return PluItem(
            pluNo = id,
            name = strField(line, "Text1") ?: "",
            weightFlag = if ((numField(line, "Unit")?.toInt() ?: WEIGH_UNIT) == COUNT_UNIT) 1 else 0,
            mainGroup = numField(line, "Class")?.toInt() ?: 0,
            unitPrice = (price * 100).roundToLong(),
        )
    }

    /** 桥接 getreport 输出行 → TradeRecord（Count 视为 kg 换算克；金额元 → 分）。 */
    internal fun parseReportJson(line: String): TradeRecord? {
        if (!line.contains("\"FID\"")) return null
        val dt = strField(line, "dt") ?: return null
        return TradeRecord(
            recordNo = numField(line, "FID")?.toLong() ?: 0L,
            timestamp = runCatching { kotlinx.datetime.LocalDateTime.parse(dt.replace(' ', 'T')) }.getOrNull(),
            pluNo = numField(line, "PLU")?.toInt() ?: 0,
            unitPrice = ((numField(line, "UnitPrice")?.toDoubleOrNull() ?: 0.0) * 100).roundToLong(),
            amount = ((numField(line, "Price")?.toDoubleOrNull() ?: 0.0) * 100).roundToLong(),
            weight = ((numField(line, "Count")?.toDoubleOrNull() ?: 0.0) * 1000).roundToInt(),
            operator = numField(line, "SID") ?: "",
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "")

    private fun numField(line: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*(-?[\\d.]+)").find(line)?.groupValues?.get(1)

    private fun strField(line: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(YouShengDriver::class.java)

        private const val BRIDGE_EXE = "YouShengBridge.exe"

        /** 友声工具目录系统属性（覆盖 ToolsDeployer 释放目录）。 */
        const val TOOLS_PATH_PROPERTY = "scaleplex.yousheng.tools"

        private const val DATA_DIR_PROPERTY = "scaleplex.data.dir"

        /** 开发期默认：友声原厂 TXT-MODE 工具目录（真机验证环境）。 */
        private const val DEFAULT_TOOLS_DIR =
            "F:\\电子称常用软件\\友声\\二次开发工具\\TXT-MODE(二次开发工具,仅发送,调用模式)"

        private const val CMD_PROBE = "probe"
        private const val CMD_GET_TIME = "gettime"
        private const val CMD_SET_TIME = "settime"
        private const val CMD_GET_PLU = "getplu"
        private const val CMD_SET_PLU = "setplu"
        private const val CMD_CLEAR_PLU = "clearplu"
        private const val CMD_GET_REPORT = "getreport"
        private const val CMD_CLEAR_REPORT = "clearreport"

        /** 桥接 PLU Unit 字段：1 = 计重，0 = 计数（本秤实测默认 1 正常下发）。 */
        internal const val WEIGH_UNIT = 1
        internal const val COUNT_UNIT = 0

        /** 探测/时间读/删除类命令进程超时上限（进程冷启动 + DLL 握手/收包等待；离线 ~3s、
         *  在线 probe 实测 ~9s，gettime 在连续操作后受秤端连接限流可达 ~20s，取 30s 兜底）。 */
        private const val PROBE_TIMEOUT_MS = 30_000L

        /** 校时命令进程超时（JH_SetTime 内部等秤端 ACK 重试，实测 ~17s）。 */
        private const val SET_TIME_TIMEOUT_MS = 30_000L

        /** PLU 下发超时（SetPLU 提交逐条网络往返，按数百条留量）。 */
        private const val DOWNLOAD_TIMEOUT_MS = 120_000L

        /** 回收类命令超时（上传会话逐条读取，对齐容大 180s 留量）。 */
        private const val UPLOAD_TIMEOUT_MS = 180_000L

        private val TIME_REGEX =
            Regex("time=(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})")
    }
}
