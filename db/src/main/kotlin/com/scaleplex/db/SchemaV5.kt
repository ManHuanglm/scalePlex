package com.scaleplex.db

import java.sql.Connection

/**
 * Version-5 schema：PLU 增加托利多商品维护字段（对齐 Scale Manager「商品维护」列）。
 * - 允许折价/允许折扣、除皮价格、附加文本号；
 * - 包装日期打印标志/单位/打印开关、保质期打印标志/打印开关。
 * 存量商品默认关闭（0）。
 */
object SchemaV5 {

    private val STATEMENTS = listOf(
        "ALTER TABLE plu ADD COLUMN allow_markdown INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN allow_discount INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN tare_price_flag INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN extra_text_no INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN packed_date_print INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN packed_date_unit INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN print_packed_date INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN shelf_life_print INTEGER DEFAULT 0",
        "ALTER TABLE plu ADD COLUMN print_shelf_life_date INTEGER DEFAULT 0",
    )

    fun apply(c: Connection) {
        STATEMENTS.forEach { sql ->
            runCatching { c.createStatement().use { it.executeUpdate(sql) } }
        }
    }
}
