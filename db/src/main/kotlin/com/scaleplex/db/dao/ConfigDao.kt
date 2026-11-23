package com.scaleplex.db.dao

import com.scaleplex.db.Database
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** Key-value access to `app_config`. */
class ConfigDao(private val db: Database) {

    fun get(key: String): String? = db.withDb { c ->
        c.query("SELECT value FROM app_config WHERE key=?", key) { rs -> rs.getString("value") }.firstOrNull()
    }

    fun put(key: String, value: String) = db.withDb { c ->
        c.exec(
            "INSERT INTO app_config (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            key, value,
        )
        Unit
    }

    fun all(): Map<String, String> = db.withDb { c ->
        c.query("SELECT key,value FROM app_config") { rs ->
            rs.getString("key") to (rs.getString("value") ?: "")
        }.toMap()
    }
}

/** Append-only operation audit log (`operation_log`). */
class OperationLogDao(private val db: Database) {

    fun log(action: String, actor: String = "", deviceId: Long? = null, detail: String = "", success: Boolean = true) =
        db.withDb { c ->
            c.insert(
                "INSERT INTO operation_log (ts,actor,action,device_id,detail,success) VALUES (?,?,?,?,?,?)",
                System.currentTimeMillis(), actor, action, deviceId, detail, if (success) 1 else 0,
            )
        }

    fun recent(limit: Int = 200): List<OpLogEntry> = db.withDb { c ->
        c.query("SELECT * FROM operation_log ORDER BY ts DESC LIMIT ?", limit) { rs ->
            OpLogEntry(
                id = rs.getLong("id"), ts = rs.getLong("ts"), actor = rs.getString("actor") ?: "",
                action = rs.getString("action") ?: "", deviceId = rs.getLong("device_id"),
                detail = rs.getString("detail") ?: "", success = rs.getInt("success") == 1,
            )
        }
    }
}

data class OpLogEntry(
    val id: Long, val ts: Long, val actor: String, val action: String,
    val deviceId: Long, val detail: String, val success: Boolean,
)
