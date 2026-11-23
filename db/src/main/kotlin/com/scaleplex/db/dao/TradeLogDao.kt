package com.scaleplex.db.dao

import com.scaleplex.core.model.TradeRecord
import com.scaleplex.db.Database
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** Local cache of trade records read from scales (`trade_log_local`). */
class TradeLogDao(private val db: Database) {

    fun insertBatch(deviceId: Long, records: List<TradeRecord>): Int = db.withDb { c ->
        c.autoCommit = false
        try {
            for (r in records) {
                c.insert(
                    """INSERT INTO trade_log_local (device_id,record_no,ts,plu_no,plu_name,weight,
                        unit_price,amount,operator) VALUES (?,?,?,?,?,?,?,?,?)""",
                    deviceId, r.recordNo, r.timestamp?.toString(), r.pluNo, r.pluName, r.weight,
                    r.unitPrice, r.amount, r.operator,
                )
            }
            c.commit()
        } catch (t: Throwable) {
            c.rollback(); throw t
        } finally {
            c.autoCommit = true
        }
        records.size
    }

    /** Paged query filtered by device and inclusive time range (ISO strings). */
    fun query(deviceId: Long? = null, from: String? = null, to: String? = null,
              pluNo: Int? = null, page: Int = 0, pageSize: Int = 100): List<TradeRecord> = db.withDb { c ->
        val where = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        if (deviceId != null) { where += "device_id=?"; args += deviceId }
        if (from != null) { where += "ts>=?"; args += from }
        if (to != null) { where += "ts<=?"; args += to }
        if (pluNo != null) { where += "plu_no=?"; args += pluNo }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        val sql = "SELECT * FROM trade_log_local $clause ORDER BY ts DESC LIMIT ? OFFSET ?"
        args += pageSize; args += page * pageSize
        c.query(sql, *args.toTypedArray()) { rs -> map(rs) }
    }

    fun clearDevice(deviceId: Long): Int = db.withDb { it.exec("DELETE FROM trade_log_local WHERE device_id=?", deviceId) }

    fun clearAll(): Int = db.withDb { it.exec("DELETE FROM trade_log_local") }

    private fun map(rs: java.sql.ResultSet): TradeRecord = TradeRecord(
        id = rs.getLong("id"),
        deviceId = rs.getLong("device_id"),
        recordNo = rs.getLong("record_no"),
        timestamp = rs.getString("ts")?.let { runCatching { kotlinx.datetime.LocalDateTime.parse(it) }.getOrNull() },
        pluNo = rs.getInt("plu_no"),
        pluName = rs.getString("plu_name") ?: "",
        weight = rs.getInt("weight"),
        unitPrice = rs.getLong("unit_price"),
        amount = rs.getLong("amount"),
        operator = rs.getString("operator") ?: "",
    )
}
