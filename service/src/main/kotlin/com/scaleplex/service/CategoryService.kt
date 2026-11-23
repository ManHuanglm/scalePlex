package com.scaleplex.service

import com.scaleplex.core.model.Category
import com.scaleplex.db.Database
import com.scaleplex.db.dao.CategoryDao

/**
 * 类别资料服务（对齐顶尖 LiNK64「类别资料」）：编号 + 名称 CRUD。
 * PLU 的 mainGroup 存类别编号，界面显示时经 [nameOf] 映射类别名称。
 */
class CategoryService(db: Database) {

    private val dao = CategoryDao(db)

    fun all(): List<Category> = dao.all()
    fun get(no: Int): Category? = dao.findByNo(no)

    fun save(category: Category): Result<Unit> = runCatching {
        require(category.no > 0) { "类别编号必须大于 0" }
        require(category.name.isNotBlank()) { "类别名称不能为空" }
        dao.upsert(category)
    }

    fun delete(no: Int) = dao.deleteByNo(no)

    /** 类别编号 → 名称映射（未登记的编号返回 "编号" 字符串）。 */
    fun nameMap(): Map<Int, String> = dao.all().associate { it.no to it.name }

    fun nameOf(no: Int): String = get(no)?.name ?: no.toString()
}
