package com.scaleplex.service

import com.scaleplex.core.model.PluItem
import com.scaleplex.core.util.PluValidator
import com.scaleplex.core.util.ValidationResult
import com.scaleplex.db.Database
import com.scaleplex.db.dao.PluDao
import org.slf4j.LoggerFactory
/**
 * PLU business service: CRUD, validation, batch import and bulk edits.
 * Wraps [PluDao]; the UI never touches the DAO directly.
 */
class PluService(db: Database) {

    private val log = LoggerFactory.getLogger(PluService::class.java)
    private val dao = PluDao(db)

    var priceDecimals: Int = 2

    fun validate(plu: PluItem): ValidationResult = PluValidator.validate(plu, priceDecimals)

    /** Save (insert or update) after validation. Returns the row id or the validation error. */
    fun save(plu: PluItem): Result<Long> {
        return when (val v = validate(plu)) {
            is ValidationResult.Invalid -> Result.failure(IllegalArgumentException(v.errors.joinToString("; ")))
            ValidationResult.Ok -> runCatching { dao.upsert(plu) }
        }
    }

    fun saveAll(items: List<PluItem>): Int {
        val valid = items.filter { validate(it) is ValidationResult.Ok }
        return valid.size.also { dao.upsertAll(valid) }
    }

    fun delete(pluNo: Int) = dao.deleteByPluNo(pluNo)
    fun deleteAll() = dao.deleteAll()
    fun get(pluNo: Int) = dao.findByPluNo(pluNo)
    fun count(keyword: String = "", field: String = PluDao.FIELD_ALL) = dao.count(keyword, field)
    fun list(keyword: String = "", page: Int = 0, pageSize: Int = 50, field: String = PluDao.FIELD_ALL) =
        dao.list(keyword, page, pageSize, field)
    fun all() = dao.all()

    /** Bulk edit: apply [transform] to the selected PLUs and persist. */
    fun bulkEdit(pluNos: Collection<Int>, transform: (PluItem) -> PluItem): Int {
        var n = 0
        for (no in pluNos) {
            val p = dao.findByPluNo(no) ?: continue
            dao.update(transform(p))
            n++
        }
        return n
    }

    /** Convenience bulk operations matching the task list (统一改税率/加价/归属模板). */
    fun setTaxRate(pluNos: Collection<Int>, taxRateNo: Int) =
        bulkEdit(pluNos) { it.copy(taxRateNo = taxRateNo) }

    fun addPrice(pluNos: Collection<Int>, deltaMinor: Long) =
        bulkEdit(pluNos) { it.copy(unitPrice = (it.unitPrice + deltaMinor).coerceAtLeast(0)) }

    fun assignLabelTemplate(pluNos: Collection<Int>, templateId: Long) =
        bulkEdit(pluNos) { it.copy(labelTemplateId = templateId) }
}
