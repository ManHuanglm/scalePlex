package com.scaleplex.db

import java.sql.Connection

/**
 * Version-4 schema：标签模板增加标签体系字段。
 * - `label_template.label_system`：GENERIC（通用）/ MT_VIS_EDIT / MT_B_LABEL / MT_SMART_LABEL，
 *   存量模板默认按通用体系处理。
 */
object SchemaV4 {

    private val STATEMENTS = listOf(
        "ALTER TABLE label_template ADD COLUMN label_system TEXT DEFAULT 'GENERIC'",
    )

    fun apply(c: Connection) {
        STATEMENTS.forEach { sql ->
            runCatching { c.createStatement().use { it.executeUpdate(sql) } }
        }
    }
}
