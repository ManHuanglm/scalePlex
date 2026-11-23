package com.scaleplex.service

import com.scaleplex.core.model.LabelTemplate
import com.scaleplex.db.Database
import com.scaleplex.db.dao.LabelTemplateDao

/** Label template management (CRUD + import/export handled by the IO service). */
class LabelService(db: Database) {
    private val dao = LabelTemplateDao(db)

    fun list(): List<LabelTemplate> = dao.list()
    fun get(id: Long) = dao.get(id)
    fun save(t: LabelTemplate): Long = dao.upsert(t)
    fun delete(id: Long) = dao.delete(id)
}
