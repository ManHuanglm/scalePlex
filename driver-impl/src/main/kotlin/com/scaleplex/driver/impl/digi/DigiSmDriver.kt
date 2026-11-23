package com.scaleplex.driver.impl.digi

import com.scaleplex.communication.Transport
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DIGI 寺冈 SM 系列驱动 v2 —— 基于官方工具链（information/寺冈开发包(接口)）：
 *
 * - PLU 下发：`SMScale.dll`（`DownloadPLUtoScales(csv, ips)`，CSV 格式同 PLU_Demo1.csv，
 *   由 [DigiPluCsvCodec] 生成），32 位 PowerShell 桥接调用，`GetScaleStatus()` 轮询结果；
 * - 交易流水：`TWS.exe RDFL 81 <ip> <port>`（0x51 销售明细）→ 按
 *   `SM100_RTRECEIPT_DETAIL.dsc` 字段表解析（[DigiReceiptParser]）；
 * - 在线探测：`TWS.exe RDR 25 <ip> <port>` 读 PLU 首条记录；
 * - 单条 PLU 删除：`RDF 25` 定位记录号 → `TWS.exe DELR 25 <记录号>`；
 * - 清空 PLU：`TWS.exe DELF 25 <ip> <port>`（文件级删除）。
 *
 * 前置条件：设置页配置寺冈工具目录（含 TWS.exe/SMScale.dll/SM.ini/Desc），系统属性
 * `scaleplex.digi.tools`。早期猜测的 UDP 二进制帧协议已废弃（未经实体秤验证）。
 *
 * 标签格式资料已定位（LabelNet 官方软件：PFF0.txt 标签格式文本 + 元素类型 1-78 定义表），
 * 列序待真机校准后接入；时间同步 TWS 命令行无对应文件号，暂未接入。
 *
 * 机型族双支持（[DigiModelFamily]）：
 * - 8442 族（SM-80/SM-100/SM-110 STE47-）：TWS.exe + SMScale.dll（上述路径）；
 * - b 族（SM-110N STE57+/SM-120/SM-5100）：SMScaleAPI.dll 进程内 socket API
 *   （[DigiSm5100Bridge]，端口 = 2000 + IP 尾段），PLU 用 LabelPLU 结构
 *   （[DigiSm5100PluCodec]），支持下发/回收/单删/清空/时间同步；流水（Receipt_D）暂未接入。
 */
class DigiSmDriver(
    private val device: ScaleDevice,
    override val transport: Transport = com.scaleplex.communication.TransportFactory.create(
        device, com.scaleplex.communication.CommEventBus(),
    ),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DIGI

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // b 族（SM-110N STE57+/SM-120/SM-5100）走 SMScaleAPI.dll 桥接，8442 族走 TWS 工具链
            when (DigiModelFamily.fromModel(device.model)) {
                DigiModelFamily.FAMILY_B -> if (!DigiSm5100Bridge.isConfigured()) {
                    error("SM-5100 SMScaleAPI.dll 未找到（寺冈工具目录下 ${DigiSm5100Bridge.DLL_SUBDIR} 目录）")
                }
                else -> if (!DigiToolsBridge.isConfigured()) {
                    error("寺冈工具目录未配置（系统设置：TWS.exe/SMScale.dll 所在目录）")
                }
            }
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                // b 族：ConnectScale 探测（端口 = 2000 + IP 尾段）
                val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.PROBE, timeoutMs = 20_000)
                ScaleStatus(online = r.connected, model = device.model.ifBlank { "DIGI SM5100" })
            } else {
                val online = DigiToolsBridge.checkOnline(device.ip, device.port)
                ScaleStatus(online = online, model = device.model.ifBlank { "DIGI SM" })
            }
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        downloadPluBatch(listOf(plu)).map { }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            // b 族：SMScaleAPI.dll 逐条 SendLPlu（LabelPLU 结构，端口 = 2000 + IP 尾段）
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                return@runCatching downloadPluBatch5100(list)
            }
            val csvFile = File(
                System.getProperty("java.io.tmpdir"),
                "scaleplex-digi-plu-${System.currentTimeMillis()}.csv",
            )
            csvFile.writeText(DigiPluCsvCodec.writePlus(list).joinToString("\r\n"), Charsets.UTF_8)
            try {
                val codeByIp = DigiToolsBridge.downloadPluViaDll(csvFile, listOf(device.ip))
                val code = codeByIp[device.ip]
                if (code == null) {
                    BatchResult(total = list.size, failed = list.size,
                        errors = listOf(BatchError(device.id, null, "下发结果中缺少本秤（IP ${device.ip}）")))
                } else if (code == 0) {
                    BatchResult.ok(list.size)
                } else {
                    BatchResult(total = list.size, failed = list.size,
                        errors = listOf(BatchError(device.id, null, DigiToolsBridge.describeResult(code))))
                }
            } finally {
                csvFile.delete()
            }
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                // b 族：RDPluFAsLabel 逐条定位 PLU 号 → DelPluRecord
                val recordNo = locate5100Record(pluNo)
                val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.DEL_RECORD, recordNo = recordNo)
                if (!r.ok) error("b 族删除记录 $recordNo 失败")
                return@runCatching
            }
            // 8442 族：RDF 25 全量回收 → 按 PLU 号定位记录号 → TWS DELR 25 <记录号>
            val (codes, datFile) = DigiToolsBridge.runTws(
                "RDF", DigiToolsBridge.PLU_FILE_NO.toString(), device.ip, device.port.toString(),
                timeoutMs = 120_000,
            )
            if (codes.isEmpty() || codes.none { it == 0 || it == -3 }) {
                error("定位 PLU $pluNo 失败: ${codes.joinToString(",") { DigiToolsBridge.describeResult(it) }}")
            }
            if (codes.any { it == -3 } || datFile == null) {
                error("秤上无 PLU 记录，无法删除 PLU $pluNo")
            }
            val recordNo = DigiPluFileParser.parseFileIndexed(datFile)
                .firstOrNull { it.second.pluNo == pluNo }?.first
                ?: error("秤上未找到 PLU $pluNo")
            DigiToolsBridge.deleteRecord(DigiToolsBridge.PLU_FILE_NO, recordNo, device.ip, device.port)
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.DEL_ALL)
                if (!r.ok) error("b 族清空 PLU 失败")
                return@runCatching
            }
            DigiToolsBridge.deleteFile(DigiToolsBridge.PLU_FILE_NO, device.ip, device.port)
        }
    }

    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                // b 族：RDPluFAsLabel 逐条回收 → LabelPLU 结构解析
                val outFile = File(System.getProperty("java.io.tmpdir"), "scaleplex-sm5100-up-${System.currentTimeMillis()}.bin")
                try {
                    val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.UP, outFile)
                    if (!r.connected) error("b 族连接失败")
                    if (!outFile.exists() || r.recordCount == 0) return@runCatching emptyList()
                    val bytes = outFile.readBytes()
                    return@runCatching (0 until r.recordCount).mapNotNull { i ->
                        val off = i * DigiSm5100PluCodec.STRUCT_SIZE
                        if (off + DigiSm5100PluCodec.STRUCT_SIZE <= bytes.size) {
                            DigiSm5100PluCodec.parse(bytes.copyOfRange(off, off + DigiSm5100PluCodec.STRUCT_SIZE))
                        } else null
                    }
                } finally {
                    outFile.delete()
                }
            }
            // 8442 族：TWS RDF 25 → IP.25.DAT（每行一条 25H 记录）→ 按 PLU_FILE.TXT 布局解析
            val (codes, datFile) = DigiToolsBridge.runTws(
                "RDF", DigiToolsBridge.PLU_FILE_NO.toString(), device.ip, device.port.toString(),
                timeoutMs = 120_000,
            )
            if (codes.isEmpty()) error("回收 PLU 失败：无结果")
            if (codes.none { it == 0 || it == -3 }) {
                error("回收 PLU 失败: ${codes.joinToString(",") { DigiToolsBridge.describeResult(it) }}")
            }
            if (datFile == null || codes.any { it == -3 }) emptyList()
            else DigiPluFileParser.parseFile(datFile)
        }
    }

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        // 标签格式资料已定位：LabelNet 官方软件 PFF0.txt（文本格式）+ 元素类型 1-78 定义表
        // （ParameterPLU2.ini [LabelFormat_Item]）。TWS 文件号与列序待实体秤抓包校准后接入。
        Result.failure(UnsupportedOperationException("寺冈标签格式回收待真机校准（LabelNet PFF0.txt 格式已定位，TWS 文件号待确认）"))

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                // b 族：SMScaleAPI.dll SyncDateTime（时间同步以系统为准）
                val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.TIME)
                if (!r.ok) error("b 族时间同步失败")
                return@runCatching
            }
            // 8442 族：TWS 命令行无时间同步，暂未接入。
            error("寺冈 8442 族时间同步暂未接入（TWS 命令行无对应文件号）")
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                // b 族流水：SMScaleAPI.dll ReadReceiptDetail（Receipt_D 紧凑结构），解码器待下一轮接入
                error("寺冈 b 族流水回收暂未接入（Receipt_D 结构解码器开发中）")
            }
            DigiToolsBridge.readReceiptDetail(device.ip, device.port, device.id)
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (DigiModelFamily.fromModel(device.model) == DigiModelFamily.FAMILY_B) {
                error("寺冈 b 族流水清空暂未接入（Receipt_D 结构解码器开发中）")
            }
            // 流水清空 = 删除明细文件 0x51
            DigiToolsBridge.deleteFile(DigiReceiptParser.RECEIPT_DETAIL_FILE_NO, device.ip, device.port)
        }
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        // 标签格式需按《标签格式1.xls》文件定义经 TWS 文件通道下发，结构待联调校准。
        Result.failure(UnsupportedOperationException("寺冈标签格式下发待联调校准（标签格式文件定义见 标签格式1.xls）"))

    /** b 族 PLU 批量下发：Kotlin 组装 LabelPLU 结构流 → 桥接逐条 SendLPlu。 */
    private fun downloadPluBatch5100(list: List<PluItem>): BatchResult {
        val stream = File(System.getProperty("java.io.tmpdir"), "scaleplex-sm5100-down-${System.currentTimeMillis()}.bin")
        try {
            java.io.RandomAccessFile(stream, "rw").use { raf ->
                for (p in list) raf.write(DigiSm5100PluCodec.encode(p))
            }
            val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.DOWN, stream)
            if (!r.connected) {
                return BatchResult(total = list.size, failed = list.size,
                    errors = listOf(BatchError(device.id, null, "b 族连接失败（${device.ip}）")))
            }
            if (!r.ok) {
                return BatchResult(total = list.size, failed = list.size,
                    errors = listOf(BatchError(device.id, null, "b 族下发失败: ${r.detail}")))
            }
            return BatchResult.ok(list.size)
        } finally {
            stream.delete()
        }
    }

    /** b 族按 PLU 号定位记录号（RDPluFAsLabel 逐条，1..4000）。 */
    private fun locate5100Record(pluNo: Int): Int {
        val outFile = File(System.getProperty("java.io.tmpdir"), "scaleplex-sm5100-up-${System.currentTimeMillis()}.bin")
        try {
            val r = DigiSm5100Bridge.execute(device.ip, DigiSm5100Bridge.Mode.UP, outFile)
            if (!r.connected) error("b 族连接失败")
            if (outFile.exists() && r.recordCount > 0) {
                val bytes = outFile.readBytes()
                for (i in 0 until r.recordCount) {
                    val off = i * DigiSm5100PluCodec.STRUCT_SIZE
                    if (off + DigiSm5100PluCodec.STRUCT_SIZE > bytes.size) break
                    val plu = DigiSm5100PluCodec.parse(bytes.copyOfRange(off, off + DigiSm5100PluCodec.STRUCT_SIZE))
                    if (plu?.pluNo == pluNo) return i + 1
                }
            }
            error("秤上未找到 PLU $pluNo")
        } finally {
            outFile.delete()
        }
    }
}
