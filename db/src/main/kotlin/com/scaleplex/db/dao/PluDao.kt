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

    fun insert(p: PluItem): Long = db.withDb { c ->
        c.insert(
            """INSERT INTO plu (plu_no,name,name_en,weight_flag,main_group,unit_price,cost,tare,qty,
                barcode_type,barcode_format,flag_data,bar_right_data_type,ean_code,multi_barcode,
                used_by_date,sell_by_date,packed_date,label1_format,label2_format,place_no,tax_rate_no,
                commodity_json,ingredient_json,special_msg_json,label_template_id,hot_key,enabled,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            p.pluNo, p.name, p.nameEn, p.weightFlag, p.mainGroup, p.unitPrice, p.cost, p.tare, p.qty,
            p.barcodeType, p.barcodeFormat, p.flagData, p.barRightDataType, p.eanCode, p.multiBarcode,
            p.usedByDate, p.sellByDate, p.packedDate, p.label1Format, p.label2Format, p.placeNo, p.taxRateNo,
            JsonCodec.writeTextLines(p.commodityLines), JsonCodec.writeTextLines(p.ingredientLines),
            JsonCodec.writeTextLines(p.specialMsgLines), p.labelTemplateId, p.hotKey,
            if (p.enabled) 1 else 0, p.updatedAt,
        )
    }

    fun update(p: PluItem): Int = db.withDb { c ->
        c.exec(
            """UPDATE plu SET name=?,name_en=?,weight_flag=?,main_group=?,unit_price=?,cost=?,tare=?,qty=?,
                barcode_type=?,barcode_format=?,flag_data=?,bar_right_data_type=?,ean_code=?,multi_barcode=?,
                used_by_date=?,sell_by_date=?,packed_date=?,label1_format=?,label2_format=?,place_no=?,tax_rate_no=?,
                commodity_json=?,ingredient_json=?,special_msg_json=?,label_template_id=?,hot_key=?,enabled=?,updated_at=?
                WHERE plu_no=?""",
            p.name, p.nameEn, p.weightFlag, p.mainGroup, p.unitPrice, p.cost, p.tare, p.qty,
            p.barcodeType, p.barcodeFormat, p.flagData, p.barRightDataType, p.eanCode, p.multiBarcode,
            p.usedByDate, p.sellByDate, p.packedDate, p.label1Format, p.label2Format, p.placeNo, p.taxRateNo,
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

    fun count(keyword: String = ""): Int = db.withDb { c ->
        val like = "%$keyword%"
        c.query(
            "SELECT COUNT(*) n FROM plu WHERE ?='' OR plu_no LIKE ? OR name LIKE ? OR name_en LIKE ? OR ean_code LIKE ?",
            keyword, like, like, like, like,
        ) { rs -> rs.getInt("n") }.firstOrNull() ?: 0
    }

    /** Paged, filtered listing. [keyword] matches PLU no, name (zh/en) or barcode. */
    fun list(keyword: String = "", page: Int = 0, pageSize: Int = 50): List<PluItem> = db.withDb { c ->
        val like = "%$keyword%"
        val sql = """SELECT * FROM plu
            WHERE ?='' OR CAST(plu_no AS TEXT) LIKE ? OR name LIKE ? OR name_en LIKE ? OR ean_code LIKE ?
            ORDER BY plu_no LIMIT ? OFFSET ?"""
        c.query(sql, keyword, like, like, like, like, pageSize, page * pageSize) { rs -> map(rs) }
    }

    fun all(): List<PluItem> = db.withDb { c ->
        c.query("SELECT * FROM plu ORDER BY plu_no") { rs -> map(rs) }
    }

    private fun map(rs: java.sql.ResultSet): PluItem = PluItem(
        id = rs.getLong("id"),
        pluNo = rs.getInt("plu_no"),
        name = rs.getString("name") ?: "",
        nameEn = rs.getString("name_en") ?: "",
        weightFlag = rs.getInt("weight_flag"),
        mainGroup = rs.getInt("main_group"),
        unitPrice = rs.getLong("unit_price"),
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
        sellByDate = rs.getInt("sell_by_date"),
        packedDate = rs.getInt("packed_date"),
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
