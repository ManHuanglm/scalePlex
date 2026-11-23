package com.scaleplex.db

import java.sql.Connection

/**
 * Version-3 schema（对齐顶尖 LiNK64 商品资料界面）：
 * - `category` 表：类别资料（编号 + 名称），PLU 的 main_group 关联显示类别名称；
 * - `plu.shelf_life_unit`：保质期单位（0=天 1=月 2=小时），下发统一换算为天。
 * ALTER/CREATE 逐条执行（SQLite 无 ADD COLUMN IF NOT EXISTS），由 user_version 控制只跑一次。
 */
object SchemaV3 {

    private val STATEMENTS = listOf(
        """CREATE TABLE IF NOT EXISTS category (
            no INTEGER PRIMARY KEY,
            name TEXT NOT NULL DEFAULT ''
        )""",
        "ALTER TABLE plu ADD COLUMN shelf_life_unit INTEGER DEFAULT 0",
    )

    fun apply(c: Connection) {
        STATEMENTS.forEach { sql ->
            runCatching { c.createStatement().use { it.executeUpdate(sql) } }
        }
    }
}
