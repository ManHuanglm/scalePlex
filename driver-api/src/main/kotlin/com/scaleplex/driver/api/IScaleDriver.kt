package com.scaleplex.driver.api

import com.scaleplex.communication.Transport
import com.scaleplex.core.model.BatchResult
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.TradeRecord

/**
 * The single contract every scale brand driver implements. The UI and services
 * talk only to this interface; adding a new brand means adding a new
 * implementation and registering it with [ScaleDriverFactory] — no UI changes.
 *
 * Methods are suspending and are expected to perform their own retry / timeout
 * handling through the underlying [Transport].
 */
interface IScaleDriver {
    /** Brand identifier this driver serves. */
    val brand: com.scaleplex.core.model.ScaleBrand

    /** Underlying transport; the service layer sets this before connecting. */
    val transport: Transport

    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>

    suspend fun getStatus(): Result<ScaleStatus>

    /** Push a single PLU to the scale. */
    suspend fun downloadPlu(plu: PluItem): Result<Unit>

    /** Push a batch of PLUs. Drivers should reuse the connection and report per-record errors. */
    suspend fun downloadPluBatch(list: List<PluItem>): Result<BatchResult>

    suspend fun deletePlu(pluNo: Int): Result<Unit>
    suspend fun deleteAllPlu(): Result<Unit>

    /** Synchronize the scale's real-time clock to the host time. */
    suspend fun syncTime(): Result<Unit>

    /** Download a label template / format definition. */
    suspend fun downloadLabelTemplate(template: LabelTemplate): Result<Unit>

    /** Read trade (transaction) records from the scale. */
    suspend fun readTradeLog(): Result<List<TradeRecord>>

    /** Erase trade records stored on the scale. */
    suspend fun clearTradeLog(): Result<Unit>
}
