package com.scaleplex.driver.impl.mettler

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
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 托利多 METTLER_TOLEDO 驱动（基于梅特勒-托利多官方中间件 MTScaleKey 接口程序 1.00.028AB）。
 *
 * 集成方式：生成 MTScaleKey 接口 XML 任务（Task/Devices/Commands/Data，UTF-8），
 * 经 32 位 PowerShell 桥接调用 MTScaleAPI.dll 的 ExecuteTaskInFileW 执行，回读 TaskResult。
 * 支持：商品下发(Item Update)、预置键(PresetDefinition)、删除/清空(Item Delete/DeleteAll)、
 * 时间同步(DeviceTime)、在线状态检查(TaskType 4)。流水回收需按文档配置 PostgreSQL，暂未接入。
 *
 * 前置条件：安装 MTScaleKey（或"无服务调用"DLL 全套文件），并在系统设置中配置 DLL 路径
 * （系统属性 scaleplex.mt.dll）。
 */
class MettlerToledoDriver(
    private val device: ScaleDevice,
    override val transport: Transport = TransportFactory.create(device, CommEventBus()),
) : AbstractScaleDriver() {

    override val brand: ScaleBrand = ScaleBrand.METTLER_TOLEDO

    private val log = LoggerFactory.getLogger(MettlerToledoDriver::class.java)

    override suspend fun getStatus(): Result<ScaleStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val taskId = MtScaleKeyXml.newTaskId()
            val taskDir = newTaskDir(taskId)
            // 检查在线状态：任务只带设备列表，无命令字
            File(taskDir, "DeviceList.xml")
                .writeText(MtScaleKeyXml.buildDeviceListXml(listOf(device), emptyMap()), Charsets.UTF_8)
            File(taskDir, "Task.xml")
                .writeText(
                    MtScaleKeyXml.buildTaskXml(taskId, MtScaleKeyXml.TASK_TYPE_CHECK_ONLINE, "DeviceList.xml"),
                    Charsets.UTF_8,
                )
            val resultFile = MtScaleKeyBridge.executeTask(taskDir, taskId).getOrElse { throw IllegalStateException(it.message) }
            val xml = resultFile.readText(Charsets.UTF_8)
            val status = MtScaleKeyXml.parseTaskResultXml(xml)[device.id]
            // 关机真机校准：ScaleKey 服务层任务恒 Complete/OK/Succeeded=1，不能作为在线依据；
            // 唯一权威是 DataFile 内 CheckOnline/Status（-1=离线）。无数据文件时退回任务层判定。
            val online = run {
                val dataFileName = MtScaleKeyXml.firstCommandDataFile(xml)
                val checkStatus = dataFileName?.let {
                    runCatching { File(resultFile.parentFile, it).readText(Charsets.UTF_8) }.getOrNull()
                        ?.let { MtScaleKeyXml.parseCheckOnlineStatus(it) }
                }
                checkStatus?.let { it >= 0 }
                    ?: (status?.ok == true || status?.status in listOf("Complete", "Executing", "Output", "Prepare"))
            }
            ScaleStatus(online = online, model = device.model.ifBlank { "Mettler Toledo" })
        }
    }

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val results = downloadViaTask(listOf(plu), includePreset = plu.hotKey > 0)
            val r = results[device.id] ?: error("任务结果中缺少该秤的执行记录")
            if (!r.allOk) error("PLU ${plu.pluNo} 下发失败: ${r.errors.firstOrNull()?.message ?: "unknown"}")
        }
    }

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val results = downloadViaTask(list, includePreset = list.any { it.hotKey > 0 })
            results[device.id] ?: BatchResult(
                total = list.size, failed = list.size,
                errors = listOf(BatchError(device.id, null, "任务结果中缺少该秤的执行记录")),
            )
        }
    }

    override suspend fun deletePlu(pluNo: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dataFile = "Data_Delete.xml"
            val files = mutableMapOf(
                "DeviceList.xml" to MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(device.id to "Commands.xml")),
                "Commands.xml" to MtScaleKeyXml.buildCommandsXml(
                    listOf(MtCommand(MtScaleKeyXml.CMD_ITEM, control = "Delete", dataFile = dataFile)),
                ),
                dataFile to MtScaleKeyXml.buildItemDataXml(listOf(PluItem(pluNo = pluNo))),
                "Task.xml" to MtScaleKeyXml.buildTaskXml(MtScaleKeyXml.newTaskId(), MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml"),
            )
            execute(files, total = 1).fold(
                onSuccess = { results ->
                    val r = results[device.id]
                    if (r == null || !r.allOk) error("删除 PLU $pluNo 失败: ${r?.errors?.firstOrNull()?.message ?: "无结果"}")
                },
                onFailure = { throw it },
            )
        }
    }

    override suspend fun deleteAllPlu(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val files = mapOf(
                "DeviceList.xml" to MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(device.id to "Commands.xml")),
                "Commands.xml" to MtScaleKeyXml.buildCommandsXml(
                    listOf(MtCommand(MtScaleKeyXml.CMD_ITEM, control = "DeleteAll")),
                ),
                "Task.xml" to MtScaleKeyXml.buildTaskXml(MtScaleKeyXml.newTaskId(), MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml"),
            )
            execute(files, total = 1).fold(
                onSuccess = { results ->
                    val r = results[device.id]
                    if (r == null || !r.allOk) error("清空 PLU 失败: ${r?.errors?.firstOrNull()?.message ?: "无结果"}")
                },
                onFailure = { throw it },
            )
        }
    }

    override suspend fun syncTime(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val files = mapOf(
                "DeviceList.xml" to MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(device.id to "Commands.xml")),
                "Commands.xml" to MtScaleKeyXml.buildCommandsXml(
                    listOf(MtCommand(MtScaleKeyXml.CMD_DEVICE_TIME, dataFile = "Data_Time.xml")),
                ),
                "Data_Time.xml" to MtScaleKeyXml.buildDeviceTimeDataXml(),
                "Task.xml" to MtScaleKeyXml.buildTaskXml(MtScaleKeyXml.newTaskId(), MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml"),
            )
            execute(files, total = 1).fold(
                onSuccess = { results ->
                    val r = results[device.id]
                    if (r == null || !r.allOk) error("时间同步失败: ${r?.errors?.firstOrNull()?.message ?: "无结果"}")
                },
                onFailure = { throw it },
            )
        }
    }

    override suspend fun uploadPlu(): Result<List<PluItem>> = withContext(Dispatchers.IO) {
        runCatching {
            // 回收：Item ReadAll 命令（MTScaleKey 文档支持 Read/ReadAll 控制字），
            // 结果以数据文件形式落盘（CommandResult 引用），扫描含 <Item> 的 XML 解析。
            val taskId = MtScaleKeyXml.newTaskId()
            val taskDir = newTaskDir(taskId)
            val input = mapOf(
                "DeviceList.xml" to MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(device.id to "Commands.xml")),
                "Commands.xml" to MtScaleKeyXml.buildCommandsXml(
                    listOf(MtCommand(MtScaleKeyXml.CMD_ITEM, control = "ReadAll")),
                ),
                "Task.xml" to MtScaleKeyXml.buildTaskXml(taskId, MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml"),
            )
            input.forEach { (name, content) -> File(taskDir, name).writeText(content, Charsets.UTF_8) }
            val resultFile = MtScaleKeyBridge.executeTask(taskDir, taskId).getOrElse { throw IllegalStateException(it.message) }
            val result = MtScaleKeyBridge.parseResult(resultFile, listOf(device), 1)[device.id]
            if (result == null || !result.allOk) {
                error("回收 PLU 失败: ${result?.errors?.firstOrNull()?.message ?: "无结果"}")
            }
            val inputNames = input.keys
            val dataXml = taskDir.listFiles { f ->
                f.name !in inputNames && f.name.endsWith(".xml") && f.readText(Charsets.UTF_8).contains("<Item>")
            }?.firstOrNull()?.readText(Charsets.UTF_8)
                ?: error("任务结果中未找到商品数据文件")
            MtScaleKeyXml.parseItemDataXml(dataXml)
        }
    }

    override suspend fun readLabelFormats(): Result<List<LabelTemplate>> =
        Result.failure(
            UnsupportedOperationException(
                "托利多标签格式由专用编辑器维护（VisEdit/bLabel/SmartLabel），" +
                    "请在设备列表点击「标签编辑」打开对应编辑器设计下发，暂不支持回收",
            ),
        )

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "托利多标签格式由各型号专用编辑器维护（RL00=VisEdit，bCom/bPro=bLabel，" +
                    "bPlus/FreshBase/bComS=SmartLabel），请在设备列表点击「标签编辑」设计并下发；" +
                    "ScalePlex 通过 Item.LabelFormatID 引用秤内既有标签号",
            ),
        )

    override suspend fun readTradeLog(): Result<List<TradeRecord>> =
        Result.failure(
            UnsupportedOperationException(
                "托利多流水回收需按 MTScaleKey 文档配置流水数据库（PostgreSQL），暂未接入",
            ),
        )

    override suspend fun clearTradeLog(): Result<Unit> =
        Result.failure(UnsupportedOperationException("托利多流水清空需通过流水数据库配置，暂未接入"))

    // ---- 内部：任务组装与执行 ----

    /** 组装"下发数据"任务：Item Update + 预置键（有热键时），执行并解析结果。 */
    private suspend fun downloadViaTask(
        plus: List<PluItem>,
        priceDecimals: Int = 2,
        includePreset: Boolean,
    ): Map<Long, BatchResult> {
        val taskId = MtScaleKeyXml.newTaskId()
        val files = mutableMapOf<String, String>()
        files["Data_Item.xml"] = MtScaleKeyXml.buildItemDataXml(plus, priceDecimals)
        val commands = mutableListOf(
            MtCommand(MtScaleKeyXml.CMD_ITEM, control = "Update", dataFile = "Data_Item.xml"),
        )
        if (includePreset) {
            files["Data_Preset.xml"] = MtScaleKeyXml.buildPresetDataXml(plus)
            commands += MtCommand(MtScaleKeyXml.CMD_PRESET, control = "Update", dataFile = "Data_Preset.xml")
        }
        files["Commands.xml"] = MtScaleKeyXml.buildCommandsXml(commands)
        files["DeviceList.xml"] = MtScaleKeyXml.buildDeviceListXml(listOf(device), mapOf(device.id to "Commands.xml"))
        files["Task.xml"] = MtScaleKeyXml.buildTaskXml(taskId, MtScaleKeyXml.TASK_TYPE_DOWNLOAD, "DeviceList.xml")
        return execute(files, total = plus.size).getOrElse { throw IllegalStateException(it.message) }
    }

    /** 写入任务文件并执行，解析 TaskResult。 */
    private suspend fun execute(files: Map<String, String>, total: Int): Result<Map<Long, BatchResult>> {
        val taskId = Regex("<TaskID>(.*?)</TaskID>").find(files["Task.xml"] ?: "")?.groupValues?.get(1)
            ?: MtScaleKeyXml.newTaskId()
        val taskDir = newTaskDir(taskId)
        files.forEach { (name, content) ->
            File(taskDir, name).writeText(content, Charsets.UTF_8)
        }
        log.info("MT task {} -> {} device={} model={} ip={}", taskId, taskDir.absolutePath, device.name, device.model, device.ip)
        val resultFile = MtScaleKeyBridge.executeTask(taskDir, taskId).getOrElse { return Result.failure(IllegalStateException(it.message)) }
        return Result.success(MtScaleKeyBridge.parseResult(resultFile, listOf(device), total))
    }

    private fun newTaskDir(taskId: String): File =
        File(System.getProperty("java.io.tmpdir"), "scaleplex-mt$taskId").apply { mkdirs() }
}
