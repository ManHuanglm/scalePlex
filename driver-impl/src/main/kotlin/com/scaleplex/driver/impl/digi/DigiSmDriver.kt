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
 * - 清空 PLU：`TWS.exe DELF 25 <ip> <port>`（文件级删除）。
 *
 * 前置条件：设置页配置寺冈工具目录（含 TWS.exe/SMScale.dll/SM.ini/Desc），系统属性
 * `scaleplex.digi.tools`。早期猜测的 UDP 二进制帧协议已废弃（未经实体秤验证）。
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
            if (!DigiToolsBridge.isConfigured()) {
                error("寺冈工具目录未配置（系统设置：TWS.exe/SMScale.dll 所在目录）")
            }
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val online = DigiToolsBridge.checkOnline(device.ip, device.port)
            ScaleStatus(online = online, model = device.model.ifBlank { "DIGI SM" })
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        downloadPluBatch(listOf(plu)).map { }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
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

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        // TWS 记录级删除（DELR）按记录位置定位，需要先读取 PLU 文件（回收操作，超出
        // "资料只能下发不能回收" 约束），单条删除暂不提供；清空请用 deleteAllPlu。
        Result.failure(UnsupportedOperationException("寺冈单条 PLU 删除暂不支持，请使用清空 PLU"))

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DigiToolsBridge.deleteFile(DigiToolsBridge.PLU_FILE_NO, device.ip, device.port)
        }
    }

    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            // 回收：TWS RDF 25 → IP.25.DAT（每行一条 25H 记录）→ 按 PLU_FILE.TXT 布局解析
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
        // 寺冈标签格式文件号与结构定义见《标签格式1.xls》，待实体秤联调校准后接入。
        Result.failure(UnsupportedOperationException("寺冈标签格式回收待联调校准（标签格式文件定义见 标签格式1.xls）"))

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // SMScaleAPI.dll 提供 SyncDateTime(socket)，但为进程内有状态调用；
            // TWS 命令行无时间同步。时间同步暂未接入。
            error("寺冈时间同步暂未接入（SMScaleAPI.dll SyncDateTime 需进程内会话）")
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            DigiToolsBridge.readReceiptDetail(device.ip, device.port, device.id)
        }
    }

    override suspend fun clearTradeLog(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 流水清空 = 删除明细文件 0x51
            DigiToolsBridge.deleteFile(DigiReceiptParser.RECEIPT_DETAIL_FILE_NO, device.ip, device.port)
        }
    }

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        // 标签格式需按《标签格式1.xls》文件定义经 TWS 文件通道下发，结构待联调校准。
        Result.failure(UnsupportedOperationException("寺冈标签格式下发待联调校准（标签格式文件定义见 标签格式1.xls）"))
}
