package com.scaleplex.driver.impl.aclas

import com.scaleplex.communication.CommEventBus
import com.scaleplex.communication.Transport
import com.scaleplex.communication.TransportFactory
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
 * 顶尖 Aclas（DINGTOP）驱动 v1 —— 基于官方 AclasSDK.dll（DLL69_Demo_CN220523 官方 Demo）：
 *
 * - PLU 下发：[AclasTxtCodec] 生成官方文本（UTF-16LE Tab 59 字段）→
 *   `Sync_ExecTaskA(…, ProcType=Down, DataType=PLU)`；
 * - PLU 回收：`ProcType=UP` → SDK 写回同格式文本文件 → [AclasTxtCodec.parseFileText]；
 * - 清空 PLU：`ProcType=Del + 文件名"*"`（官方 Clear PLU.bat 同参数）；
 * - 在线探测：`AclasSDK_GetDevicesInfo`（同时拿到 Port/ProtocolType/设备名/固件版本）；
 * - 部门/单位/热键（DataType 1/2/3）与 Note1-3（0xC..E）为独立 DataType，v1 暂未接入。
 *
 * 前置条件：设置页配置顶尖工具目录（含 AclasSDK.dll/AclasSDK.ini），系统属性
 * `scaleplex.aclas.tools`。流水回收暂无对应 DataType（官方 Demo 未提供），诚实标注。
 */
class AclasDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.DINGTOP

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!AclasBridge.isConfigured()) {
                error("顶尖工具目录未配置（系统设置：AclasSDK.dll 所在目录）")
            }
        }
    }

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val info = AclasBridge.execTask(device.ip, AclasBridge.PROC_LIST, AclasBridge.DATA_PLU, "*", timeoutMs = 20_000)
            ScaleStatus(
                online = info.online,
                model = info.deviceName.ifBlank { device.model.ifBlank { "ACLAS" } },
                firmware = "",
            )
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val r = execPluDown(listOf(plu))
            if (!r.ok) error(AclasBridge.describeResult(r))
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (list.isEmpty()) return@runCatching BatchResult.ok(0)
            val r = execPluDown(list)
            if (r.ok) BatchResult.ok(list.size)
            else BatchResult(
                total = list.size, failed = list.size,
                errors = listOf(BatchError(device.id, null, AclasBridge.describeResult(r))),
            )
        }
    }

    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = tempFile("aclas-plu-up", ".txt")
            try {
                val r = AclasBridge.execTask(device.ip, AclasBridge.PROC_UP, AclasBridge.DATA_PLU, outFile.absolutePath)
                if (!r.online) error(AclasBridge.describeResult(r))
                if (!outFile.exists()) return@runCatching emptyList()
                AclasTxtCodec.parseFileText(outFile.readText(Charsets.UTF_16))
            } finally {
                outFile.delete()
            }
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 官方 Clear PLU.bat：-t Del -b PLU -n "*"
            val r = AclasBridge.execTask(device.ip, AclasBridge.PROC_DEL, AclasBridge.DATA_PLU, "*")
            if (!r.ok) error(AclasBridge.describeResult(r))
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 按记录删除：AclasSDK.ini Minus2Delete 提示负数量可删除；Del 仅整文件。
            // 单条删除走 Edit/负数语义待真机校准，先诚实提示。
            error("顶尖单条 PLU 删除待真机校准（Minus2Delete 语义），请使用清空 PLU 后重新下发")
        }
    }

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        // 标签格式在 AclasLabelDrawSDK（83MB 绘制 SDK）内，结构未公开且需专用编辑器。
        Result.failure(UnsupportedOperationException("顶尖标签格式回收需 AclasLabelDrawSDK 支持，暂未接入"))

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(UnsupportedOperationException("顶尖标签格式下发需 AclasLabelDrawSDK 支持，暂未接入"))

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // DataType 无时间同步项；秤端时间同步待后续接入（Options/设备参数）。
            error("顶尖时间同步暂未接入")
        }
    }

    override suspend fun readTradeLog(): Result<List<TradeRecord>> =
        // DataType 无 SaleRecord（官方 Demo 未提供流水任务）；流水回收暂未接入。
        Result.failure(UnsupportedOperationException("顶尖流水回收暂未接入（官方 Demo 无对应 DataType）"))

    override suspend fun clearTradeLog(): Result<Unit> =
        Result.failure(UnsupportedOperationException("顶尖流水清空暂未接入（官方 Demo 无对应 DataType）"))

    /** 生成 PLU 文本并下发。 */
    private fun execPluDown(items: List<PluItem>): AclasBridge.TaskResult {
        val pluFile = tempFile("aclas-plu-down", ".txt")
        try {
            // AclasTxt 官方文件格式：UTF-16LE + BOM
            pluFile.writeText(AclasTxtCodec.encodeFile(items), Charsets.UTF_16)
            return AclasBridge.execTask(device.ip, AclasBridge.PROC_DOWN, AclasBridge.DATA_PLU, pluFile.absolutePath)
        } finally {
            pluFile.delete()
        }
    }

    private fun tempFile(prefix: String, suffix: String): File =
        File(System.getProperty("java.io.tmpdir"), "$prefix-${System.currentTimeMillis()}$suffix")
}
