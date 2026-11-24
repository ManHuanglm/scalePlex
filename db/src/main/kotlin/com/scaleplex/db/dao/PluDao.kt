package com.scaleplex.db.dao

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.model.PluTextLine
import com.scaleplex.db.Database
import com.scaleplex.db.JsonCodec
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** CRUD + paging/filter for the `plu` table. */
class PluDao(private val db: Database) {

    companion object {
        /** 筛选字段常量（对齐顶尖筛选字段下拉）。 */
        const val FIELD_ALL = "all"
        const val FIELD_PLU = "plu"
        const val FIELD_NAME = "name"
        const val FIELD_BARCODE = "barcode"
        const val FIELD_CATEGORY = "category"
    }

    fun insert(p: PluItem): Long = db.withDb { c ->
        c.insert(
            """INSERT INTO plu (plu_no,name,name_en,weight_flag,unit,main_group,unit_price,member_price,special_price,cost,tare,qty,
                barcode_type,barcode_format,flag_data,bar_right_data_type,ean_code,multi_barcode,
                used_by_date,shelf_life_unit,sell_by_date,packed_date,
                allow_markdown,allow_discount,tare_price_flag,extra_text_no,
                packed_date_print,packed_date_unit,print_packed_date,shelf_life_print,print_shelf_life_date,
                label1_format,label2_format,place_no,tax_rate_no,
                commodity_json,ingredient_json,special_msg_json,label_template_id,hot_key,enabled,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            p.pluNo, p.name, p.nameEn, p.weightFlag, p.unit, p.mainGroup, p.unitPrice, p.memberPrice, p.specialPrice,
            p.cost, p.tare, p.qty,
            p.barcodeType, p.barcodeFormat, p.flagData, p.barRightDataType, p.eanCode, p.multiBarcode,
            p.usedByDate, p.shelfLifeUnit, p.sellByDate, p.packedDate,
            if (p.allowMarkdown) 1 else 0, if (p.allowDiscount) 1 else 0,
            if (p.tarePriceFlag) 1 else 0, p.extraTextNo,
            if (p.packedDatePrintFlag) 1 else 0, p.packedDateUnit, if (p.printPackedDate) 1 else 0,
            if (p.shelfLifePrintFlag) 1 else 0, if (p.printShelfLifeDate) 1 else 0,
            p.label1Format, p.label2Format, p.placeNo, p.taxRateNo,
            JsonCodec.writeTextLines(p.commodityLines), JsonCodec.writeTextLines(p.ingredientLines),
            JsonCodec.writeTextLines(p.specialMsgLines), p.labelTemplateId, p.hotKey,
            if (p.enabled) 1 else 0, p.updatedAt,
        )
    }

    fun update(p: PluItem): Int = db.withDb { c ->
        c.exec(
            """UPDATE plu SET name=?,name_en=?,weight_flag=?,unit=?,main_group=?,unit_price=?,member_price=?,special_price=?,cost=?,tare=?,qty=?,
                barcode_type=?,barcode_format=?,flag_data=?,bar_right_data_type=?,ean_code=?,multi_barcode=?,
                used_by_date=?,shelf_life_unit=?,sell_by_date=?,packed_date=?,
                allow_markdown=?,allow_discount=?,tare_price_flag=?,extra_text_no=?,
                packed_date_print=?,packed_date_unit=?,print_packed_date=?,shelf_life_print=?,print_shelf_life_date=?,
                label1_format=?,label2_format=?,place_no=?,tax_rate_no=?,
                commodity_json=?,ingredient_json=?,special_msg_json=?,label_template_id=?,hot_key=?,enabled=?,updated_at=?
                WHERE plu_no=?""",
            p.name, p.nameEn, p.weightFlag, p.unit, p.mainGroup, p.unitPrice, p.memberPrice, p.specialPrice,
            p.cost, p.tare, p.qty,
            p.barcodeType, p.barcodeFormat, p.flagData, p.barRightDataType, p.eanCode, p.multiBarcode,
            p.usedByDate, p.shelfLifeUnit, p.sellByDate, p.packedDate,
            if (p.allowMarkdown) 1 else 0, if (p.allowDiscount) 1 else 0,
            if (p.tarePriceFlag) 1 else 0, p.extraTextNo,
            if (p.packedDatePrintFlag) 1 else 0, p.packedDateUnit, if (p.printPackedDate) 1 else 0,
            if (p.shelfLifePrintFlag) 1 else 0, if (p.printShelfLifeDate) 1 else 0,
            p.label1Format, p.label2Format, p.placeNo, p.taxRateNo,
            JsonCodec.writeTextLines(p.commodityLines), JsonCodec.writeTextLines(p.ingredientLines),
            JsonCodec.writeTextLines(p.specialMsgLines), p.labelTemplateId, p.hotKey,
            if (p.enabled) 1 else 0, p.updatedAt, p.pluNo,
        )
    }

    fun upsert(p: PluItem): Long {
        val existing = findByPluNo(p.pluNo)
        return if (existing == null) insert(p) else { update(p); existing.id }
    }

    fun upsertAll(items: List<PluItem>) = db.withDb { c ->
        c.autoCommit = false
        try {
            items.forEach { upsert(it) }
            c.commit()
        } catch (t: Throwable) {
            c.rollback(); throw t
        } finally {
            c.autoCommit = true
        }
        Unit
    }

    fun deleteByPluNo(pluNo: Int): Int = db.withDb { it.exec("DELETE FROM plu WHERE plu_no=?", pluNo) }

    fun deleteAll(): Int = db.withDb { it.exec("DELETE FROM plu") }

    fun findByPluNo(pluNo: Int): PluItem? = db.withDb { c ->
        c.query("SELECT * FROM plu WHERE plu_no=?", pluNo) { rs -> map(rs) }.firstOrNull()
    }

    fun count(keyword: String = "", field: String = FIELD_ALL): Int = db.withDb { c ->
        val (cond, args) = keywordCondition(keyword, field)
        c.query("SELECT COUNT(*) n FROM plu WHERE $cond", *args.toTypedArray()) { rs -> rs.getInt("n") }
            .firstOrNull() ?: 0
    }

    /** Paged, filtered listing. [keyword] matches the field selected by [field]（对齐顶尖筛选字段下拉）. */
    fun list(keyword: String = "", page: Int = 0, pageSize: Int = 50, field: String = FIELD_ALL): List<PluItem> = db.withDb { c ->
        val (cond, args) = keywordCondition(keyword, field)
        val sql = """SELECT * FROM plu WHERE $cond ORDER BY plu_no LIMIT ? OFFSET ?"""
        c.query(sql, *args.toTypedArray(), pageSize, page * pageSize) { rs -> map(rs) }
    }

    fun all(): List<PluItem> = db.withDb { c ->
        c.query("SELECT * FROM plu ORDER BY plu_no") { rs -> map(rs) }
    }

    /**
     * 筛选条件与参数（[field] 为内部常量无注入风险）：
     * 全部=货号/名称/条码任一匹配；货号/名称/条码/类别编号各按对应列 LIKE（类别同时匹配类别名称）。
     */
    private fun keywordCondition(keyword: String, field: String): Pair<String, List<String>> {
        if (keyword.isBlank()) return "1=1" to emptyList()
        val like = "%$keyword%"
        return when (field) {
            FIELD_PLU -> "CAST(plu_no AS TEXT) LIKE ?" to listOf(like)
            FIELD_NAME -> "name LIKE ? OR name_en LIKE ?" to listOf(like, like)
            FIELD_BARCODE -> "ean_code LIKE ? OR multi_barcode LIKE ?" to listOf(like, like)
            FIELD_CATEGORY ->
                "CAST(main_group AS TEXT) LIKE ? OR main_group IN (SELECT no FROM category WHERE name LIKE ?)"
                    .let { it to listOf(like, like) }
            else -> "CAST(plu_no AS TEXT) LIKE ? OR name LIKE ? OR name_en LIKE ? OR ean_code LIKE ?"
                .let { it to listOf(like, like, like, like) }
        }
    }

    private fun map(rs: java.sql.ResultSet): PluItem = PluItem(
        id = rs.getLong("id"),
        pluNo = rs.getInt("plu_no"),
        name = rs.getString("name") ?: "",
        nameEn = rs.getString("name_en") ?: "",
        weightFlag = rs.getInt("weight_flag"),
        unit = rs.getInt("unit"),
        mainGroup = rs.getInt("main_group"),
        unitPrice = rs.getLong("unit_price"),
        memberPrice = rs.getLong("member_price"),
        specialPrice = rs.getLong("special_price"),
        cost = rs.getLong("cost"),
        tare = rs.getInt("tare"),
        qty = rs.getInt("qty"),
        barcodeType = rs.getInt("barcode_type"),
        barcodeFormat = rs.getInt("barcode_format"),
        flagData = rs.getString("flag_data") ?: "",
        barRightDataType = rs.getInt("bar_right_data_type"),
        eanCode = rs.getString("ean_code") ?: "",
        multiBarcode = rs.getString("multi_barcode") ?: "",
        usedByDate = rs.getInt("used_by_date"),
        shelfLifeUnit = rs.getInt("shelf_life_unit"),
        sellByDate = rs.getInt("sell_by_date"),
        packedDate = rs.getInt("packed_date"),
        allowMarkdown = rs.getInt("allow_markdown") == 1,
        allowDiscount = rs.getInt("allow_discount") == 1,
        tarePriceFlag = rs.getInt("tare_price_flag") == 1,
        extraTextNo = rs.getInt("extra_text_no"),
        packedDatePrintFlag = rs.getInt("packed_date_print") == 1,
        packedDateUnit = rs.getInt("packed_date_unit"),
        printPackedDate = rs.getInt("print_packed_date") == 1,
        shelfLifePrintFlag = rs.getInt("shelf_life_print") == 1,
        printShelfLifeDate = rs.getInt("print_shelf_life_date") == 1,
        label1Format = rs.getInt("label1_format"),
        label2Format = rs.getInt("label2_format"),
        placeNo = rs.getInt("place_no"),
        taxRateNo = rs.getInt("tax_rate_no"),
        commodityLines = JsonCodec.readTextLines(rs.getString("commodity_json") ?: "[]"),
        ingredientLines = JsonCodec.readTextLines(rs.getString("ingredient_json") ?: "[]"),
        specialMsgLines = JsonCodec.readTextLines(rs.getString("special_msg_json") ?: "[]"),
        labelTemplateId = rs.getObject("label_template_id")?.let { (it as Number).toLong() },
        hotKey = rs.getInt("hot_key"),
        enabled = rs.getInt("enabled") == 1,
        updatedAt = rs.getLong("updated_at"),
    )
}
