package com.scaleplex.service

import com.scaleplex.core.model.TradeRecord
import com.scaleplex.db.Database
import com.scaleplex.db.dao.TradeLogDao

/** Transaction log service: read from scales, store locally, query, clear. */
class TradeLogService(private val db: Database) {
    private val dao = TradeLogDao(db)

    fun store(deviceId: Long, records: List<TradeRecord>): Int = dao.insertBatch(deviceId, records)

    fun query(deviceId: Long? = null, from: String? = null, to: String? = null,
              pluNo: Int? = null, page: Int = 0, pageSize: Int = 100): List<TradeRecord> =
        dao.query(deviceId, from, to, pluNo, page, pageSize)

    fun clearDevice(deviceId: Long) = dao.clearDevice(deviceId)
    fun clearAll() = dao.clearAll()
}
