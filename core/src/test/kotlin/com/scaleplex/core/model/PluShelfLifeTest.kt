package com.scaleplex.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** 保质期单位换算测试（对齐顶尖「保质期/保质期单位」列，下发统一换算为天）。 */
class PluShelfLifeTest {

    @Test
    fun effectiveShelfLifeDaysConversions() {
        // 天：原值直传
        assertEquals(89, PluItem(pluNo = 1, usedByDate = 89, shelfLifeUnit = ShelfLifeUnit.DAY.id).effectiveShelfLifeDays)
        // 月：×30
        assertEquals(90, PluItem(pluNo = 1, usedByDate = 3, shelfLifeUnit = ShelfLifeUnit.MONTH.id).effectiveShelfLifeDays)
        // 小时：÷24 向上取整
        assertEquals(1, PluItem(pluNo = 1, usedByDate = 1, shelfLifeUnit = ShelfLifeUnit.HOUR.id).effectiveShelfLifeDays)
        assertEquals(2, PluItem(pluNo = 1, usedByDate = 25, shelfLifeUnit = ShelfLifeUnit.HOUR.id).effectiveShelfLifeDays)
        assertEquals(2, PluItem(pluNo = 1, usedByDate = 48, shelfLifeUnit = ShelfLifeUnit.HOUR.id).effectiveShelfLifeDays)
        // 0 / 缺省（天）
        assertEquals(0, PluItem(pluNo = 1, usedByDate = 0).effectiveShelfLifeDays)
        assertEquals(89, PluItem(pluNo = 1, usedByDate = 89).effectiveShelfLifeDays)
    }

    @Test
    fun shelfLifeUnitFromId() {
        assertEquals(ShelfLifeUnit.DAY, ShelfLifeUnit.fromId(0))
        assertEquals(ShelfLifeUnit.MONTH, ShelfLifeUnit.fromId(1))
        assertEquals(ShelfLifeUnit.HOUR, ShelfLifeUnit.fromId(2))
        assertEquals(ShelfLifeUnit.DAY, ShelfLifeUnit.fromId(99))
    }
}
