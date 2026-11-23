package com.scaleplex.db.dao

import com.scaleplex.core.model.ScaleGroup
import com.scaleplex.db.Database
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** CRUD for `scale_group`. */
class GroupDao(private val db: Database) {

    fun insert(g: ScaleGroup): Long = db.withDb { c ->
        c.insert("INSERT INTO scale_group (name,note) VALUES (?,?)", g.name, g.note)
    }

    fun update(g: ScaleGroup): Int = db.withDb { c ->
        c.exec("UPDATE scale_group SET name=?,note=? WHERE id=?", g.name, g.note, g.id)
    }

    fun delete(id: Long): Int = db.withDb { c ->
        // Reassign devices to the default group (0) before removing.
        c.exec("UPDATE scale_device SET group_id=0 WHERE group_id=?", id)
        c.exec("DELETE FROM scale_group WHERE id=?", id)
    }

    fun list(): List<ScaleGroup> = db.withDb { c ->
        c.query("SELECT * FROM scale_group ORDER BY id") { rs ->
            ScaleGroup(rs.getLong("id"), rs.getString("name") ?: "", rs.getString("note") ?: "")
        }
    }

    fun get(id: Long): ScaleGroup? = db.withDb { c ->
        c.query("SELECT * FROM scale_group WHERE id=?", id) { rs ->
            ScaleGroup(rs.getLong("id"), rs.getString("name") ?: "", rs.getString("note") ?: "")
        }.firstOrNull()
    }
}
