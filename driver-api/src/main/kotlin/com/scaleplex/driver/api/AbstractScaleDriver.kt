package com.scaleplex.driver.api

import com.scaleplex.communication.Transport
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.TradeRecord

/**
 * Optional convenience base. Drivers must override [brand] and [transport]; every
 * protocol operation defaults to "unsupported" so a partially implemented driver
 * (e.g. one that only does PLU download in phase 1) still satisfies the interface.
 */
abstract class AbstractScaleDriver : IScaleDriver {
    abstract override val brand: ScaleBrand
    abstract override val transport: Transport

    override suspend fun connect(): Result<Unit> = runCatching {
        transport.connect(); Unit
    }

    override suspend fun disconnect(): Result<Unit> = runCatching {
        transport.disconnect(); Unit
    }

    override suspend fun getStatus(): Result<ScaleStatus> =
        Result.failure(UnsupportedOperationException("getStatus not implemented for $brand"))

    override suspend fun downloadPlu(plu: PluItem): Result<Unit> =
        Result.failure(UnsupportedOperationException("downloadPlu not implemented for $brand"))

    override suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult> =
        Result.failure(UnsupportedOperationException("downloadPluBatch not implemented for $brand"))

    override suspend fun deletePlu(pluNo: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("deletePlu not implemented for $brand"))

    override suspend fun deleteAllPlu(): Result<Unit> =
        Result.failure(UnsupportedOperationException("deleteAllPlu not implemented for $brand"))

    override suspend fun uploadPlu(): Result<List<PluItem>> =
        Result.failure(UnsupportedOperationException("uploadPlu not implemented for $brand"))

    override suspend fun syncTime(): Result<Unit> =
        Result.failure(UnsupportedOperationException("syncTime not implemented for $brand"))

    override suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit> =
        Result.failure(UnsupportedOperationException("downloadLabelTemplate not implemented for $brand"))

    override suspend fun readTradeLog(): Result<List<TradeRecord>> =
        Result.failure(UnsupportedOperationException("readTradeLog not implemented for $brand"))

    override suspend fun clearTradeLog(): Result<Unit> =
        Result.failure(UnsupportedOperationException("clearTradeLog not implemented for $brand"))
}
