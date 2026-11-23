package com.scaleplex.db

import java.sql.Connection

/**
 * Version-2 schema: PLU 扩展字段（参考托利多传秤下发字段）：
 * `unit` 计价单位、`member_price` 会员价、`special_price` 特价。
 * ALTER TABLE 逐条执行（SQLite 无 ADD COLUMN IF NOT EXISTS），由 user_version 控制只跑一次。
 */
object SchemaV2 {

    private val ALTERATIONS = listOf(
        "ALTER TABLE plu ADD COLUMN unit INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN member_price INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN special_price INTEGER DEFAULT 0",
    )

    fun apply(c: Connection) {
        ALTERATIONS.forEach { sql ->
            runCatching { c.createStatement().use { it.executeUpdate(sql) } }
        }
    }
}
