package com.scaleplex.core.model

import kotlinx.datetime.LocalDateTime

/**
 * One transaction (trade) record read back from a scale.
 * Local cached copy lives in `trade_log_local`.
 */
data class TradeRecord(
    val id: Long = 0,
    val deviceId: Long = 0,
    val recordNo: Long = 0,
    val timestamp: LocalDateTime? = null,
    val pluNo: Int = 0,
    val pluName: String = "",
    /** Weight in grams. */
    val weight: Int = 0,
    /** Unit price in minor units. */
    val unitPrice: Long = 0,
    /** Total amount in minor units. */
    val amount: Long = 0,
    val operator: String = "",
)

/** Aggregate result of a batch download / upload across one or more scales. */
data class BatchResult(
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val errors: List<BatchError> = emptyList(),
) {
    val allOk: Boolean get() = failed == 0

    companion object {
        fun ok(total: Int) = BatchResult(total = total, success = total)
        fun single(err: BatchError) = BatchResult(total = 1, failed = 1, errors = listOf(err))
    }
}

data class BatchError(
    val deviceId: Long,
    val pluNo: Int? = null,
    val message: String,
)

/** Global application configuration (persisted to app_config). */
data class ScaleConfig(
    val language: String = "zh-CN",
    val priceDecimals: Int = 2,
    val weightDecimals: Int = 3,
    val connectTimeoutMs: Int = 3000,
    val readTimeoutMs: Int = 2000,
    val retries: Int = 3,
    val heartbeatIntervalMs: Int = 5000,
    val defaultBrand: ScaleBrand = ScaleBrand.DIGI,
    val rawPacketLog: Boolean = false,
    val logLevel: String = "INFO",
    val autoBackup: Boolean = true,
    val backupPath: String = "",
    /** 登录/启动后自动向全部在线秤下发一次全量 PLU（参考银豹"自动下发"）。 */
    val autoDownload: Boolean = false,
    /** 下发条码格式编号（DIGI EAN 条码格式表，见 DigiTables.EAN_BARCODE_FORMATS）。 */
    val barcodeFormat: Int = 0,
    /** MTScaleKey 接口 DLL 路径（托利多驱动桥接调用），空 = 默认 C:\MTScaleKey\MTScaleAPI\MTScaleAPI.dll。 */
    val mtApiDllPath: String = "",
)
