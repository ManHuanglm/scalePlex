package com.scaleplex.db

import java.sql.Connection

/** Version-1 schema: all tables required by the task list. */
object SchemaV1 {

    private val DDL = listOf(
        // 秤分组表
        """CREATE TABLE IF NOT EXISTS scale_group (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            note TEXT DEFAULT ''
        )""",
        // 秤设备表
        """CREATE TABLE IF NOT EXISTS scale_device (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            brand TEXT NOT NULL,
            model TEXT DEFAULT '',
            comm_type TEXT NOT NULL DEFAULT 'udp',
            ip TEXT DEFAULT '',
            port INTEGER DEFAULT 1111,
            serial_port TEXT DEFAULT 'COM1',
            baud INTEGER DEFAULT 9600,
            data_bits INTEGER DEFAULT 8,
            parity INTEGER DEFAULT 0,
            stop_bits INTEGER DEFAULT 1,
            connect_timeout INTEGER DEFAULT 3000,
            read_timeout INTEGER DEFAULT 2000,
            retries INTEGER DEFAULT 3,
            group_id INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            note TEXT DEFAULT ''
        )""",
        // PLU 商品表
        """CREATE TABLE IF NOT EXISTS plu (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            plu_no INTEGER NOT NULL,
            name TEXT DEFAULT '',
            name_en TEXT DEFAULT '',
            weight_flag INTEGER DEFAULT 0,
            main_group INTEGER DEFAULT 997,
            unit_price INTEGER DEFAULT 0,
            cost INTEGER DEFAULT 0,
            tare INTEGER DEFAULT 0,
            qty INTEGER DEFAULT 0,
            barcode_type INTEGER DEFAULT 0,
            barcode_format INTEGER DEFAULT 0,
            flag_data TEXT DEFAULT '',
            bar_right_data_type INTEGER DEFAULT 0,
            ean_code TEXT DEFAULT '',
            multi_barcode TEXT DEFAULT '',
            used_by_date INTEGER DEFAULT 0,
            sell_by_date INTEGER DEFAULT 0,
            packed_date INTEGER DEFAULT 0,
            label1_format INTEGER DEFAULT 0,
            label2_format INTEGER DEFAULT 0,
            place_no INTEGER DEFAULT 0,
            tax_rate_no INTEGER DEFAULT 0,
            commodity_json TEXT DEFAULT '[]',
            ingredient_json TEXT DEFAULT '[]',
            special_msg_json TEXT DEFAULT '[]',
            label_template_id INTEGER,
            hot_key INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            updated_at INTEGER DEFAULT 0,
            UNIQUE(plu_no)
        )""",
        // 标签模板表
        """CREATE TABLE IF NOT EXISTS label_template (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            width_mm INTEGER DEFAULT 40,
            height_mm INTEGER DEFAULT 30,
            elements_json TEXT DEFAULT '[]',
            note TEXT DEFAULT ''
        )""",
        // 本地缓存交易流水
        """CREATE TABLE IF NOT EXISTS trade_log_local (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id INTEGER NOT NULL,
            record_no INTEGER DEFAULT 0,
            ts TEXT,
            plu_no INTEGER DEFAULT 0,
            plu_name TEXT DEFAULT '',
            weight INTEGER DEFAULT 0,
            unit_price INTEGER DEFAULT 0,
            amount INTEGER DEFAULT 0,
            operator TEXT DEFAULT ''
        )""",
        // 软件配置 (key-value)
        """CREATE TABLE IF NOT EXISTS app_config (
            key TEXT PRIMARY KEY,
            value TEXT
        )""",
        // 操作日志
        """CREATE TABLE IF NOT EXISTS operation_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts INTEGER NOT NULL,
            actor TEXT DEFAULT '',
            action TEXT NOT NULL,
            device_id INTEGER,
            detail TEXT DEFAULT '',
            success INTEGER DEFAULT 1
        )""",
        // indexes
        "CREATE INDEX IF NOT EXISTS idx_plu_no ON plu(plu_no)",
        "CREATE INDEX IF NOT EXISTS idx_plu_name ON plu(name)",
        "CREATE INDEX IF NOT EXISTS idx_trade_device ON trade_log_local(device_id)",
        "CREATE INDEX IF NOT EXISTS idx_trade_ts ON trade_log_local(ts)",
    )

    fun apply(c: Connection) {
        c.createStatement().use { st ->
            DDL.forEach { st.executeUpdate(it) }
        }
    }

    fun seedDefaults(c: Connection) {
        // Default group + a couple of sensible config keys if empty.
        val groupCount = c.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM scale_group").use { rs -> if (rs.next()) rs.getInt(1) else 0 } }
        if (groupCount == 0) {
            c.prepareStatement("INSERT INTO scale_group (name, note) VALUES (?, ?)").apply {
                setString(1, "默认分组"); setString(2, "Default group")
                executeUpdate()
            }
        }
    }
}
