package com.scaleplex.db.dao

import com.scaleplex.core.model.Category
import com.scaleplex.db.Database
import com.scaleplex.db.exec
import com.scaleplex.db.query

/** CRUD for the `category` table（类别资料，对齐顶尖 LiNK64）。 */
class CategoryDao(private val db: Database) {

    fun all(): List<Category> = db.withDb { c ->
        c.query("SELECT no, name FROM category ORDER BY no") { rs ->
            Category(no = rs.getInt("no"), name = rs.getString("name") ?: "")
        }
    }

    fun findByNo(no: Int): Category? = db.withDb { c ->
        c.query("SELECT no, name FROM category WHERE no=?", no) { rs ->
            Category(no = rs.getInt("no"), name = rs.getString("name") ?: "")
        }.firstOrNull()
    }

    fun upsert(category: Category): Unit = db.withDb { c ->
        c.exec(
            "INSERT INTO category (no, name) VALUES (?, ?) ON CONFLICT(no) DO UPDATE SET name=excluded.name",
            category.no, category.name,
        )
        Unit
    }

    fun deleteByNo(no: Int): Int = db.withDb { it.exec("DELETE FROM category WHERE no=?", no) }
}
