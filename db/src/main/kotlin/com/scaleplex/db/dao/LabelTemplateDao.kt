package com.scaleplex.db.dao

import com.scaleplex.core.model.LabelSystem
import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.db.Database
import com.scaleplex.db.JsonCodec
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** CRUD for `label_template`. */
class LabelTemplateDao(private val db: Database) {

    fun insert(t: LabelTemplate): Long = db.withDb { c ->
        c.insert(
            "INSERT INTO label_template (name,width_mm,height_mm,elements_json,note,label_system) VALUES (?,?,?,?,?,?)",
            t.name, t.widthMm, t.heightMm, JsonCodec.writeElements(t.elements), t.note, t.system.name,
        )
    }

    fun update(t: LabelTemplate): Int = db.withDb { c ->
        c.exec(
            "UPDATE label_template SET name=?,width_mm=?,height_mm=?,elements_json=?,note=?,label_system=? WHERE id=?",
            t.name, t.widthMm, t.heightMm, JsonCodec.writeElements(t.elements), t.note, t.system.name, t.id,
        )
    }

    fun upsert(t: LabelTemplate): Long =
        if (t.id == 0L) insert(t)
        else { update(t); t.id }

    fun delete(id: Long): Int = db.withDb { it.exec("DELETE FROM label_template WHERE id=?", id) }

    fun list(): List<LabelTemplate> = db.withDb { c ->
        c.query("SELECT * FROM label_template ORDER BY id") { rs -> map(rs) }
    }

    fun get(id: Long): LabelTemplate? = db.withDb { c ->
        c.query("SELECT * FROM label_template WHERE id=?", id) { rs -> map(rs) }.firstOrNull()
    }

    private fun map(rs: java.sql.ResultSet): LabelTemplate = LabelTemplate(
        id = rs.getLong("id"),
        name = rs.getString("name") ?: "",
        widthMm = rs.getInt("width_mm"),
        heightMm = rs.getInt("height_mm"),
        elements = JsonCodec.readElements(rs.getString("elements_json") ?: "[]"),
        note = rs.getString("note") ?: "",
        system = LabelSystem.fromId(rs.getString("label_system") ?: "GENERIC"),
    )
}
